import assert from "node:assert/strict";
import fs from "node:fs/promises";
import { existsSync } from "node:fs";
import path from "node:path";
import { chromium } from "playwright";
import { createServer } from "vite";

// Mount the real management page against an in-memory, intercepted HTTP contract. No live
// provider, backend credentials or external network is used by this browser regression test.
const html = `<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;background:#f5f7fa"><div id="app"></div><script type="module">
import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import Page from '/src/views/AdminApiProviders.vue';
import { applyAuthSession } from '/src/utils/authSession.js';
applyAuthSession({ token: 'test.' + btoa(JSON.stringify({exp: Date.now()/1000 + 3600})) + '.signature', userId: 7 });
createApp(Page).use(ElementPlus).mount('#app');
</script></body></html>`;
const server = await createServer({
  logLevel: "error",
  server: { host: "127.0.0.1", port: 0, strictPort: false },
  plugins: [{
    name: "provider-test-page",
    configureServer(vite) {
      vite.middlewares.use(async (request, response, next) => {
        if (request.url !== "/__provider_workflow") return next();
        response.setHeader("Content-Type", "text/html; charset=utf-8");
        response.end(await vite.transformIndexHtml(request.url, html));
      });
    },
  }],
});
let browser;
try {
  await server.listen();
  const address = server.httpServer.address();
  const baseURL = `http://127.0.0.1:${address.port}`;
  const executablePath = process.env.BROWSER_PATH
    || (existsSync("/snap/bin/chromium") ? "/snap/bin/chromium" : undefined);
  browser = await chromium.launch({ executablePath, headless: true, args: ["--no-sandbox", "--disable-dev-shm-usage"] });
  const page = await browser.newPage({ viewport: { width: 1600, height: 980 } });
  const consoleErrors = [];
  page.on("pageerror", (error) => consoleErrors.push(error.message));
  const unexpectedRequests = [];
  const providers = [];
  const writes = [];
  let failNextTest = false;
  const failureId = "12345678-1234-1234-1234-123456789012";
  const safeProvider = (provider) => ({
    id: provider.id, name: provider.name, providerType: provider.providerType,
    apiUrl: provider.apiUrl, usernameMasked: "us***er", hasApiKey: true,
    status: provider.status, balance: provider.balance,
    verifiedAt: provider.verifiedAt, verifiedBy: provider.verifiedBy,
    lastCheckReason: provider.lastCheckReason, lastCheckErrorId: provider.lastCheckErrorId,
  });
  await page.route("**/*", async (route) => {
    const url = new URL(route.request().url());
    if (url.origin !== baseURL) {
      unexpectedRequests.push(url.origin);
      return route.abort();
    }
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const method = route.request().method();
    const payload = route.request().postDataJSON();
    const pathname = url.pathname.slice(4);
    const respond = (data, message = "操作成功", status = 200, extra = {}) => route.fulfill({
      status, contentType: "application/json",
      body: JSON.stringify({ code: status === 200 ? 1 : -1, message, data, ...extra }),
    });
    if (pathname === "/admin/api-providers" && method === "GET") {
      return respond({ records: providers.map(safeProvider), total: providers.length, current: 1, size: 10 });
    }
    if (pathname === "/admin/api-providers" && ["POST", "PUT"].includes(method)) {
      writes.push(payload);
      for (const key of ["verifiedAt", "verifiedBy", "lastCheckReason", "configVersion", "balance"]) {
        assert.equal(Object.hasOwn(payload, key), false, `client must not submit ${key}`);
      }
      const normalized = new URL(payload.apiUrl.trim());
      normalized.pathname = normalized.pathname.replace(/\/api\.php\/?$/i, "").replace(/\/+$/, "");
      const apiUrl = normalized.href.replace(/\/$/, "");
      if (method === "POST") {
        providers.push({ ...payload, id: 9, apiUrl, balance: 0, status: payload.status === 0 ? 0 : 2 });
        return respond(9, "API接口已保存，请测试连接后启用");
      }
      const provider = providers.find((item) => item.id === payload.id);
      const changed = provider.apiUrl !== apiUrl || provider.providerType !== payload.providerType || Boolean(payload.apiKey);
      Object.assign(provider, payload, { apiUrl });
      if (changed) Object.assign(provider, { status: 2, verifiedAt: null, verifiedBy: null, lastCheckReason: null });
      return respond(null);
    }
    if (pathname === "/admin/api-providers/9/test-connection") {
      const provider = providers[0];
      if (failNextTest) {
        failNextTest = false;
        provider.lastCheckReason = "PRIVATE_ADDRESS";
        provider.lastCheckErrorId = failureId;
        return respond({ reason: "PRIVATE_ADDRESS" }, "第三方域名解析到了非公网地址，已阻止请求", 502, { errorId: failureId });
      }
      Object.assign(provider, { verifiedAt: "2026-09-06T02:00:00", verifiedBy: 7, lastCheckReason: "SUCCESS", lastCheckErrorId: null });
      return respond({ apiUrl: provider.apiUrl, normalizedHost: new URL(provider.apiUrl).hostname,
        verifiedAt: provider.verifiedAt, verifiedBy: 7, durationMs: 24, status: provider.status }, "连接测试通过，可启用接口");
    }
    if (pathname === "/admin/api-providers/9/status") {
      assert.ok([0, 1].includes(payload.status));
      if (payload.status === 1) assert.ok(providers[0].verifiedAt && providers[0].lastCheckReason === "SUCCESS");
      providers[0].status = payload.status;
      return respond(null);
    }
    if (pathname === "/admin/api-providers/9/balance") {
      assert.equal(providers[0].status, 1);
      providers[0].balance = 88.5;
      return respond(88.5);
    }
    unexpectedRequests.push(`${method} ${pathname}`);
    return respond(null, "Unexpected mocked endpoint", 500);
  });
  page.setDefaultTimeout(15000);
  await page.goto(`${baseURL}/__provider_workflow`);
  await page.getByRole("button", { name: "添加接口", exact: true }).click();
  await page.getByPlaceholder("请输入接口名称", { exact: true }).fill("Daytime 浏览器测试");
  await page.getByPlaceholder("https://provider.example.com 或 /openapi 基础目录").fill("HTTPS://New-Provider.Example:443/api.php/");
  await page.getByPlaceholder("请输入API Key", { exact: true }).fill("browser-test-secret");
  await page.getByRole("button", { name: "保存", exact: true }).click();
  const row = page.locator(".el-table__body-wrapper tbody tr").filter({ hasText: "Daytime 浏览器测试" }).first();
  await row.waitFor();
  await row.getByText("待验证", { exact: true }).waitFor();
  assert.equal(await row.getByRole("button", { name: "启用", exact: true }).isDisabled(), true);
  assert.equal(await row.getByRole("button", { name: "查询余额", exact: true }).isDisabled(), true);
  assert.equal(await page.locator("body").innerText().then((text) => text.includes("browser-test-secret")), false);

  await row.getByRole("button", { name: "测试连接", exact: true }).click();
  await page.getByRole("dialog", { name: "连接测试通过" }).waitFor();
  await page.getByRole("dialog", { name: "连接测试通过" }).getByText("https://new-provider.example", { exact: true }).waitFor();
  await page.getByRole("dialog", { name: "连接测试通过" }).getByRole("button", { name: "关闭", exact: true }).click();
  await row.getByText("待启用", { exact: true }).waitFor();
  assert.equal(providers[0].status, 2, "testing never auto-enables");
  await row.getByRole("button", { name: "启用", exact: true }).click();
  await row.getByText("已启用", { exact: true }).waitFor();
  await row.getByRole("button", { name: "查询余额", exact: true }).click();
  await row.getByText("¥88.50", { exact: true }).waitFor();

  const screenshotDir = process.env.PROVIDER_SCREENSHOT_DIR;
  if (screenshotDir) {
    await fs.mkdir(screenshotDir, { recursive: true });
    await page.screenshot({ path: path.join(screenshotDir, "provider-desktop.png"), fullPage: true });
  }
  await row.getByRole("button", { name: "编辑", exact: true }).click();
  await page.getByPlaceholder("https://provider.example.com 或 /openapi 基础目录").fill("https://changed-provider.example/openapi");
  failNextTest = true;
  await page.getByRole("button", { name: "保存并测试", exact: true }).click();
  const failure = page.getByRole("dialog", { name: "连接测试失败" });
  await failure.waitFor();
  await failure.getByText("PRIVATE_ADDRESS", { exact: true }).waitFor();
  await failure.getByText(failureId, { exact: true }).waitFor();
  assert.equal(providers[0].status, 2, "changing URL deactivates the provider");
  assert.equal(providers[0].verifiedAt, null);
  if (screenshotDir) await page.screenshot({ path: path.join(screenshotDir, "provider-failure.png"), fullPage: true });
  await failure.getByRole("button", { name: "关闭", exact: true }).click();
  assert.equal(await row.getByRole("button", { name: "启用", exact: true }).isDisabled(), true);

  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("button", { name: "添加接口", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "添加接口" });
  await dialog.waitFor();
  const bounds = await dialog.boundingBox();
  assert.ok(bounds.width <= 390 && bounds.x >= 0, "mobile dialog stays in the viewport");
  if (screenshotDir) await page.screenshot({ path: path.join(screenshotDir, "provider-mobile.png"), fullPage: true });
  await dialog.getByRole("button", { name: "取消", exact: true }).click();
  assert.equal(writes.length, 2);
  assert.deepEqual(unexpectedRequests, []);
  assert.deepEqual(consoleErrors, []);
  console.log("Provider browser workflow passed: save → pending → test → enable → balance; URL edit → reverify → classified failure; mobile layout.");
} finally {
  if (browser) await browser.close();
  await server.close();
}
