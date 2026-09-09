import assert from "node:assert/strict";
import { existsSync, mkdirSync } from "node:fs";
import { chromium } from "playwright";
import { createTestServer as createServer } from './fixtures/test-server.mjs';
const batchId = "fa78622c-9ef0-4d6c-b335-102720a6af04";
const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="app"></div><script type="module">
import {createApp} from 'vue';import ElementPlus from 'element-plus';import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';import Page from '/src/views/AdminPlatforms.vue';import {applyAuthSession} from '/src/utils/authSession.js';applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7});createApp(Page).use(ElementPlus).mount('#app');</script></body></html>`;
const server = await createServer({
  logLevel: "error",
  server: { host: "127.0.0.1", port: 0 },
  plugins: [
    {
      name: "catalogue-price-test",
      configureServer(vite) {
        vite.middlewares.use(async (req, res, next) => {
          if (req.url !== "/__catalog_refresh") return next();
          res.setHeader("Content-Type", "text/html;charset=utf-8");
          res.end(await vite.transformIndexHtml(req.url, html));
        });
      },
    },
  ],
});
let browser;
const writes = [],
  reads = [],
  unexpected = [],
  errors = [];
let batch,
  loseConfirm = true,
  stale = false,
  previewCount = 0,
  confirmedCount = 0;
try {
  await server.listen();
  const base = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({
    executablePath:
      process.env.BROWSER_PATH ||
      (existsSync("/snap/bin/chromium") ? "/snap/bin/chromium" : undefined),
    args: ["--no-sandbox", "--disable-dev-shm-usage"],
  });
  const page = await browser.newPage({
    viewport: { width: 1440, height: 1080 },
  });
  page.setDefaultTimeout(45000);
  page.setDefaultNavigationTimeout(90000);
  page.on("pageerror", (e) => errors.push(e.message));
  await page.route("**/*", async (route) => {
    const req = route.request(),
      url = new URL(req.url());
    if (url.origin !== base) {
      unexpected.push(url.origin);
      return route.abort();
    }
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const path = url.pathname.slice(4),
      method = req.method(),
      respond = (data) =>
        route.fulfill({
          contentType: "application/json",
          body: JSON.stringify({ code: 1, data }),
        });
    if (method === "GET") reads.push(path);
    else
      writes.push({ path, method, data: JSON.parse(req.postData() || "{}") });
    if (path === "/admin/platforms" && method === "GET")
      return respond({ records: [], total: 0 });
    if (path === "/admin/platform-categories" && method === "GET")
      return respond([]);
    if (path === "/admin/api-providers" && method === "GET")
      return respond({
        records: [
          { id: 9, name: "Benz · 已启用", providerType: "27", status: 1 },
          { id: 10, name: "其他接口", providerType: "29", status: 1 },
          { id: 11, name: "Benz · 未启用", providerType: "27", status: 2 },
        ],
        total: 3,
      });
    if (path === "/admin/platforms/price-refreshes" && method === "POST") {
      const form = JSON.parse(req.postData());
      assert.equal(form.providerId, 9);
      assert.equal(form.multiplier, "1.2");
      assert.equal(form.categoryId, "1");
      assert.deepEqual(form.skipCategoryIds, ["2", "3"]);
      assert.equal(form.scope, "ALL_EXISTING");
      assert.deepEqual(form.productIds, []);
      previewCount++;
      batch = {
        id: batchId,
        providerId: 9,
        state: "READY",
        expiresAt: "2099-01-01T12:05:00",
        appliedAt: null,
        notice: "仅预览现有价格与说明，尚未更新。",
        plan: {
          multiplier: "1.2",
          scope: "ALL_EXISTING",
          categoryId: "1",
          skipCategoryIds: ["2", "3"],
          notImported: 3,
          excluded: 2,
          selectedMissing: 0,
          rows: [
            {
              localId: 1,
              remoteId: "A",
              title: "本地课程名称保持不变",
              oldPrice: "2.50",
              newPrice: "1.48",
              oldDescription: "本地说明",
              newDescription:
                '<img src="https://evil.example/track">纯文本上游说明',
              descriptionProvided: true,
              changed: true,
            },
            {
              localId: 2,
              remoteId: "B",
              title: "保持原价课程",
              oldPrice: "3.00",
              newPrice: "3.00",
              oldDescription: "自己的说明",
              newDescription: "自己的说明",
              descriptionProvided: false,
              changed: false,
            },
          ],
        },
      };
      return respond(batch);
    }
    if (
      path === `/admin/platforms/price-refreshes/${batchId}/confirm` &&
      method === "POST"
    ) {
      assert.deepEqual(JSON.parse(req.postData()), { consent: true });
      confirmedCount++;
      batch = {
        ...batch,
        state: stale ? "STALE" : "APPLIED",
        notice: stale
          ? "本地价格已经变化，整批未更新。"
          : "已更新，课程名称、分类与上架状态保持不变。",
      };
      if (loseConfirm) {
        loseConfirm = false;
        return route.abort("connectionfailed");
      }
      return respond(batch);
    }
    if (
      path === `/admin/platforms/price-refreshes/${batchId}` &&
      method === "GET"
    )
      return respond(batch);
    unexpected.push(method + " " + path);
    return route.fulfill({ status: 404, body: "Unexpected mock API" });
  });
  await page.goto(`${base}/__catalog_refresh`);
  await page
    .getByRole("button", { name: "仅更新价格/说明", exact: true })
    .click();
  const drawer = page.getByRole("dialog", {
    name: "Benz · 更新现有价格与说明",
  });
  await drawer
    .getByRole("button", { name: "读取并预览变更（不更新）" })
    .waitFor();
  assert.equal(writes.length, 0);
  assert.equal(
    reads.some((x) => x.includes("price-refreshes")),
    false,
  );
  await drawer.locator(".el-select__wrapper").click();
  await page
    .getByRole("option", { name: "Benz · 已启用", exact: true })
    .click();
  assert.equal(
    await page
      .getByRole("option", { name: "Benz · 未启用", exact: true })
      .count(),
    0,
  );
  assert.equal(
    await page.getByRole("option", { name: "其他接口", exact: true }).count(),
    0,
  );
  await drawer
    .getByRole("textbox", { name: "价格倍率", exact: true })
    .fill("1.2");
  await drawer.getByRole("textbox", { name: "限定目录分类（可选）" }).fill("1");
  await drawer
    .getByRole("textbox", { name: "排除目录分类（可选）" })
    .fill("2,3");
  await drawer
    .getByRole("button", { name: "读取并预览变更（不更新）" })
    .click();
  await drawer.getByText("预览 ¥1.48", { exact: true }).waitFor();
  assert.equal(confirmedCount, 0);
  assert.equal(previewCount, 1);
  assert.equal(
    await drawer.getByRole("button", { name: "确认更新 1 项" }).isDisabled(),
    true,
  );
  await drawer.getByText("查看说明变化（纯文本）", { exact: true }).click();
  assert.equal(await drawer.locator("img").count(), 0);
  await drawer
    .getByText('<img src="https://evil.example/track">纯文本上游说明', {
      exact: true,
    })
    .waitFor();
  mkdirSync("../.cache/native-service-ui", { recursive: true });
  await page.screenshot({
    path: "../.cache/native-service-ui/catalog-refresh-desktop.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => document.documentElement.classList.add("dark"));
  await page.screenshot({
    path: "../.cache/native-service-ui/catalog-refresh-mobile-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  assert.ok(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  );
  await drawer
    .getByText("我已核对上面所有变更，同意仅更新现有价格与说明", {
      exact: true,
    })
    .click();
  await drawer
    .getByRole("button", { name: "确认更新 1 项" })
    .click({ clickCount: 2 });
  await drawer
    .getByText("更新请求结果尚未确认。只检查原批次结果，不自动重新提交。", {
      exact: true,
    })
    .waitFor();
  assert.equal(confirmedCount, 1);
  assert.equal(
    await drawer.getByRole("button", { name: "确认更新 1 项" }).count(),
    0,
  );
  const before = writes.length;
  await drawer.getByRole("button", { name: "检查原批次结果" }).click();
  await drawer
    .getByText("已更新，课程名称、分类与上架状态保持不变。", { exact: true })
    .waitFor();
  assert.equal(writes.length, before);
  assert.equal(confirmedCount, 1);
  await drawer.getByRole("button", { name: "新建预览" }).click();
  stale = true;
  await drawer
    .getByRole("button", { name: "读取并预览变更（不更新）" })
    .click();
  await drawer.getByText("预览 ¥1.48", { exact: true }).waitFor();
  await drawer
    .getByText("我已核对上面所有变更，同意仅更新现有价格与说明", {
      exact: true,
    })
    .click();
  await drawer.getByRole("button", { name: "确认更新 1 项" }).click();
  await drawer
    .getByText("本地价格已经变化，整批未更新。", { exact: true })
    .waitFor();
  assert.equal(
    await drawer.getByRole("button", { name: "确认更新 1 项" }).count(),
    0,
  );
  assert.equal(
    writes.some(
      (w) =>
        w.path.includes("import-products") ||
        w.path.includes("batch-sync") ||
        w.path.includes("service-orders"),
    ),
    false,
  );
  assert.deepEqual(unexpected, []);
  assert.deepEqual(errors, []);
  assert.equal(
    await page.evaluate(
      () =>
        JSON.stringify(localStorage).includes("price-refresh") ||
        JSON.stringify(sessionStorage).includes("price-refresh"),
    ),
    false,
  );
  console.log(
    "PASS Benz existing-only refresh: actual admin entry, no automatic query/write, decimal preview, no HTML execution, single local confirmation, GET-only recovery, stale batch, mobile/dark; simulated APIs only.",
  );
} finally {
  await browser?.close();
  await server.close();
}
