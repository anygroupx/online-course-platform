import assert from "node:assert/strict";
import { existsSync, mkdirSync } from "node:fs";
import { chromium } from "playwright";
import { createTestServer as createServer } from './fixtures/test-server.mjs';

// Real Vue views, simulated API only. No supplier calls, money, or real customer keys.
const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;padding:24px"><div id="app"></div><script type="module">
import {createApp,h} from 'vue';import {createRouter,createMemoryHistory,RouterView} from 'vue-router';import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';
import Projects from '/src/views/ProjectCenter.vue';import Admin from '/src/views/AdminProjectCenter.vue';import {applyAuthSession} from '/src/utils/authSession.js';
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7});
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/service-projects',component:Projects},{path:'/admin/service-projects',component:Admin}]});await router.push(new URLSearchParams(location.search).get('page')||'/service-projects');await router.isReady();createApp({render:()=>h(RouterView)}).use(router).use(ElementPlus).mount('#app');
</script></body></html>`;
const server = await createServer({
  logLevel: "error",
  server: { host: "127.0.0.1", port: 0 },
  plugins: [
    {
      name: "project-center-fixture",
      configureServer(vite) {
        vite.middlewares.use(async (req, res, next) => {
          if (!req.url?.startsWith("/__project_center")) return next();
          res.setHeader("Content-Type", "text/html;charset=utf-8");
          res.end(await vite.transformIndexHtml(req.url, html));
        });
      },
    },
  ],
});
const project = {
  id: 1,
  providerId: 9,
  remoteProjectId: "5",
  title: "项目甲 · 独立服务额度",
  description: "开通专属账户，按明确费率充值；每次兑换都有独立操作记录。",
  basePrice: "1",
  unitPrice: "0.333333",
  unitCost: "0.10",
  validUntil: "2099-12-31",
  enabled: true,
  available: true,
  version: 0,
  account: null,
};
const consent = "我已了解项目账户、费率和余额兑换规则，确认使用本人账户";
const accountId = "27c5c14d-2eba-4dd7-a023-52a49a3dcc6b";
const operations = new Map(),
  mutations = [],
  reads = [],
  saves = [],
  resolutions = [],
  unexpected = [],
  errors = [];
let seq = 0,
  loseConfirm = false,
  unknownConfirm = false,
  loseResolution = false,
  browser,
  context;
const operationId = () =>
  `5aa431d0-cfe1-4e06-8a68-${String(++seq).padStart(12, "0")}`;
const pageData = (records) => ({
  records,
  total: records.length,
  current: 1,
  size: 20,
});
try {
  await server.listen();
  const base = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({
    executablePath:
      process.env.BROWSER_PATH ||
      (existsSync("/snap/bin/chromium") ? "/snap/bin/chromium" : undefined),
    headless: true,
    args: ["--no-sandbox", "--disable-dev-shm-usage"],
  });
  context = await browser.newContext({
    viewport: { width: 1440, height: 1080 },
  });
  context.on("page", (p) => p.on("pageerror", (e) => errors.push(e.message)));
  await context.route("**/*", async (route) => {
    const req = route.request(),
      url = new URL(req.url());
    if (url.origin !== base) {
      unexpected.push(url.origin);
      return route.abort();
    }
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const endpoint = url.pathname.slice(4),
      method = req.method(),
      body = () => JSON.parse(req.postData() || "{}");
    const respond = (data) =>
      route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({ code: 1, data }),
      });
    if (method === "GET") reads.push(endpoint);
    else mutations.push({ endpoint, body: body() });
    if (
      method === "GET" &&
      ["/project-tickets", "/admin/project-tickets"].includes(endpoint)
    )
      return respond(pageData([]));
    if (
      method === "GET" &&
      ["/service-projects", "/admin/service-projects"].includes(endpoint)
    )
      return respond(pageData([project]));
    if (
      method === "GET" &&
      ["/project-operations", "/admin/project-operations"].includes(endpoint)
    )
      return respond(pageData([...operations.values()].reverse()));
    if (
      method === "GET" &&
      /^\/(admin\/)?project-operations\/[^/]+$/.test(endpoint)
    )
      return respond(operations.get(endpoint.split("/").at(-1)));
    if (
      method === "POST" &&
      endpoint === `/project-accounts/${accountId}/refresh`
    )
      return respond(project.account);
    if (method === "POST" && endpoint === "/service-projects/1/quotes") {
      const b = body();
      assert.equal(b.confirmedPolicy, true);
      assert.ok(["PROVISION", "TOP_UP", "WITHDRAW"].includes(b.action));
      const q = {
        id: operationId(),
        accountId,
        projectId: 1,
        projectTitle: project.title,
        userId: 7,
        action: b.action,
        state: "READY",
        units: b.units || "0",
        unitPrice: project.unitPrice,
        amount:
          b.action === "PROVISION"
            ? "0.00"
            : b.action === "WITHDRAW"
              ? "0.66"
              : "3.34",
        balanceAfter: null,
        expiresAt: new Date(Date.now() + 240000).toISOString(),
        createdAt: new Date().toISOString(),
        warnings: [
          "预览不扣款，确认后仅派发一次。",
          "外部赠额不自动兑付；未知结果不得自动退款。",
        ],
      };
      operations.set(q.id, q);
      return respond(q);
    }
    if (
      method === "POST" &&
      /^\/project-operations\/[^/]+\/confirm$/.test(endpoint)
    ) {
      const q = operations.get(endpoint.split("/")[2]);
      assert.equal(q.state, "READY");
      await new Promise((r) => setTimeout(r, 150));
      q.state = unknownConfirm ? "UNKNOWN" : "SUCCEEDED";
      if (q.action === "PROVISION")
        project.account = {
          id: accountId,
          projectId: 1,
          state: "ACTIVE",
          unitPrice: "0.333333",
          remoteBalance: "0",
          refundableUnits: "0",
          refundBudget: "0",
          balanceCheckedAt: "2026-09-08T12:00:00",
          pendingOperationId: null,
          ticketsAvailable: true,
        };
      else if (unknownConfirm) {
        project.account.state = "UNKNOWN";
        project.account.pendingOperationId = q.id;
      } else {
        project.account.remoteBalance =
          q.action === "WITHDRAW" ? "8.000001" : "10.000001";
        project.account.refundableUnits = project.account.remoteBalance;
        project.account.refundBudget =
          q.action === "WITHDRAW" ? "2.68" : "3.34";
      }
      q.balanceAfter = project.account.remoteBalance;
      if (loseConfirm) {
        loseConfirm = false;
        return route.abort("connectionfailed");
      }
      return respond(q);
    }
    if (method === "GET" && endpoint === "/admin/api-providers") {
      assert.equal(url.searchParams.get("providerType"), "syyv5");
      return respond(
        pageData([
          {
            id: 9,
            providerType: "syyv5",
            name: "已验证项目接口",
            status: 1,
            verifiedAt: "2026-09-08T12:00:00",
          },
        ]),
      );
    }
    if (method === "GET" && endpoint === "/admin/service-project-catalog")
      return respond([{ id: "6", name: "项目乙", basePrice: "1.75" }]);
    if (method === "POST" && endpoint === "/admin/service-projects") {
      saves.push(body());
      return respond({ ...project, ...body(), id: 2 });
    }
    if (
      method === "POST" &&
      /^\/admin\/project-operations\/[^/]+\/resolve$/.test(endpoint)
    ) {
      const b = body(),
        q = operations.get(endpoint.split("/")[3]);
      resolutions.push(b);
      assert.equal(q.state, "UNKNOWN");
      assert.equal(b.upstreamChecked, true);
      assert.ok(b.evidence.length >= 10);
      q.state = b.outcome === "ACCEPTED" ? "SUCCEEDED" : "NOT_ACCEPTED";
      project.account.state = "ACTIVE";
      project.account.pendingOperationId = null;
      if (loseResolution) {
        loseResolution = false;
        return route.abort("connectionfailed");
      }
      return respond(q);
    }
    unexpected.push(`${method} ${endpoint}`);
    return route.fulfill({ status: 404, body: "Unexpected fixture API" });
  });
  const page = await context.newPage();
  page.setDefaultTimeout(45000);
  page.setDefaultNavigationTimeout(90000);
  await page.goto(`${base}/__project_center`);
  await page.getByRole("heading", { name: project.title }).waitFor();
  assert.equal(
    mutations.length,
    0,
    "loading must not create accounts, quotes or refresh upstream balances",
  );
  async function preview(action, units) {
    await page
      .getByRole("button", { name: action, exact: true })
      .first()
      .click();
    const drawer = page.getByRole("dialog");
    if (units)
      await drawer
        .getByRole("textbox", { name: "项目额度", exact: true })
        .fill(units);
    await drawer.getByText(consent, { exact: true }).click();
    await drawer.getByRole("button", { name: "预览本次操作" }).click();
    const dialog = page.getByRole("dialog", { name: "确认项目操作" });
    await dialog.waitFor();
    return dialog;
  }
  async function closeQuote(dialog) {
    await dialog.getByRole("button", { name: "关闭", exact: true }).click();
    await dialog.waitFor({ state: "hidden" });
  }
  let dialog = await preview("开通项目账户");
  assert.equal(
    mutations.filter((m) => m.endpoint.endsWith("/confirm")).length,
    0,
  );
  assert.match(await dialog.innerText(), /¥0.00/);
  await dialog
    .getByRole("button", { name: "确认开户" })
    .click({ clickCount: 2 });
  await dialog.getByText("已完成", { exact: true }).waitFor();
  assert.equal(
    mutations.filter((m) => m.endpoint.endsWith("/confirm")).length,
    1,
    "double click must dispatch once",
  );
  await closeQuote(dialog);
  const beforeSupport = mutations.length;
  await page.getByRole("button", { name: "工单与支持", exact: true }).click();
  const supportDraft = page.getByRole("dialog", { name: "新建项目工单", exact: true });
  await supportDraft.waitFor();
  assert.equal(mutations.length, beforeSupport, "opening support from the owned account must not auto-submit");
  await supportDraft.getByRole("button", { name: "取消", exact: true }).click();
  await supportDraft.waitFor({ state: "hidden" });
  await page.getByRole("button", { name: "更新账户余额" }).click();
  assert.equal(
    mutations.filter((m) => m.endpoint.endsWith("/refresh")).length,
    1,
  );
  dialog = await preview("充值额度", "10.000001");
  assert.match(await dialog.innerText(), /¥3.34/);
  await dialog.getByRole("button", { name: "确认兑换" }).click();
  await dialog.getByText("已完成", { exact: true }).waitFor();
  await closeQuote(dialog);
  dialog = await preview("转回余额", "2");
  assert.match(await dialog.innerText(), /¥0.66/);
  loseConfirm = true;
  await dialog.getByRole("button", { name: "确认兑换" }).click();
  await dialog.getByText("提交结果待确认", { exact: true }).waitFor();
  assert.equal(
    await dialog.getByRole("button", { name: "确认兑换" }).count(),
    0,
  );
  const beforeCheck = mutations.length;
  await dialog.getByRole("button", { name: "检查提交结果" }).click();
  await dialog.getByText("已完成", { exact: true }).waitFor();
  assert.equal(
    mutations.length,
    beforeCheck,
    "recovery is GET-only, never replays",
  );
  await closeQuote(dialog);
  unknownConfirm = true;
  dialog = await preview("充值额度", "10.000001");
  await dialog.getByRole("button", { name: "确认兑换" }).click();
  await dialog.getByText("待人工核对", { exact: true }).waitFor();
  const pending = [...operations.values()].at(-1).id;
  await closeQuote(dialog);
  assert.equal(
    await page
      .getByRole("button", { name: "充值额度", exact: true })
      .isDisabled(),
    true,
  );
  assert.equal(
    await page
      .getByRole("button", { name: "转回余额", exact: true })
      .isDisabled(),
    true,
  );
  mkdirSync("../.cache/native-service-ui", { recursive: true });
  await page.waitForFunction(
    () => document.querySelectorAll(".el-message").length === 0,
  );
  await page.screenshot({
    path: "../.cache/native-service-ui/project-center-desktop.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => document.documentElement.classList.add("dark"));
  assert.ok(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
    "mobile has no horizontal overflow",
  );
  await page.screenshot({
    path: "../.cache/native-service-ui/project-center-mobile-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  const browserState = await page.evaluate(() =>
    JSON.stringify({
      local: { ...localStorage },
      session: { ...sessionStorage },
      html: document.body.innerHTML,
    }),
  );
  assert.doesNotMatch(
    browserState,
    /customer[_-]?key|api[_-]?key|private-customer/i,
    "customer credentials never enter browser state or DOM",
  );

  const admin = await context.newPage();
  admin.setDefaultTimeout(45000);
  admin.setDefaultNavigationTimeout(90000);
  await admin.goto(`${base}/__project_center?page=/admin/service-projects`);
  await admin.getByRole("button", { name: "发布项目", exact: true }).click();
  let drawer = admin.getByRole("dialog", { name: "发布项目", exact: true });
  await drawer.getByRole("combobox", { name: "已验证的 syyv5 配置" }).click();
  await admin.getByRole("option", { name: "已验证项目接口" }).click();
  await drawer.getByRole("button", { name: "读取目录" }).click();
  await drawer.getByText("先读取已授权的项目目录", { exact: true }).click();
  await admin.getByRole("option", { name: "项目乙 · 目录 ¥1.75" }).click();
  await drawer
    .getByRole("textbox", { name: "新账户售价（元 / 额度）" })
    .fill("0.25");
  await drawer
    .getByRole("textbox", { name: "已核实实际成本（元 / 额度）" })
    .fill("0.10");
  const expiry = new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 10);
  await drawer.getByRole("combobox", { name: "成本核实有效期" }).fill(expiry);
  await drawer.getByRole("combobox", { name: "成本核实有效期" }).press("Tab");
  await drawer
    .getByRole("textbox", { name: "核实依据" })
    .fill("已查阅合同并逐笔核实上游的实际单位扣款成本。");
  await drawer
    .getByText("已核实实际单位成本，并了解用户账户冻结售价不会跟随调价", {
      exact: true,
    })
    .click();
  await drawer.locator(".el-switch__core").click();
  await drawer.getByRole("button", { name: "保存项目" }).click();
  await drawer.waitFor({ state: "hidden" });
  assert.equal(saves.length, 1);
  assert.equal(saves[0].unitCost, "0.10");
  assert.equal(saves[0].remoteProjectId, "6");
  assert.equal(saves[0].enabled, true);
  await admin.getByRole("tab", { name: "资金操作核对" }).click();
  await admin.getByRole("button", { name: "查看与核对" }).first().click();
  dialog = admin.getByRole("dialog", { name: "核对项目操作" });
  await dialog.waitFor();
  assert.match(await dialog.innerText(), new RegExp(pending));
  await dialog
    .getByRole("textbox", { name: "核对证据" })
    .fill("逐项核对原流水，上游确认完全未受理该笔充值。");
  await dialog
    .getByText("已逐项核实，不凭余额变化推测受理结果", { exact: true })
    .click();
  loseResolution = true;
  await dialog.getByRole("button", { name: "确认核对并记账" }).click();
  await dialog
    .getByText("已提交核对请求。请查询原操作结果，不要再次提交。")
    .waitFor();
  assert.equal(
    await dialog.getByRole("button", { name: "确认核对并记账" }).count(),
    0,
  );
  const beforeResolveCheck = mutations.length;
  await dialog.getByRole("button", { name: "检查原操作状态" }).click();
  await dialog.getByText(/已确认未受理/).waitFor();
  assert.equal(mutations.length, beforeResolveCheck);
  assert.equal(resolutions.length, 1);
  await admin.setViewportSize({ width: 390, height: 844 });
  await admin.evaluate(() => document.documentElement.classList.add("dark"));
  assert.ok(
    await admin.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
    "admin mobile no horizontal overflow",
  );
  await admin.screenshot({
    path: "../.cache/native-service-ui/project-center-admin-mobile-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  assert.deepEqual(unexpected, []);
  assert.deepEqual(errors, []);
  console.log(
    "Project center browser workflow passed: zero opening, exact quotes, single dispatch, GET-only recovery, publication, reconciliation, mobile/dark; simulated APIs only.",
  );
} finally {
  await context?.close();
  await browser?.close();
  await server.close();
}
