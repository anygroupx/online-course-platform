import assert from "node:assert/strict";
import { existsSync, mkdirSync } from "node:fs";
import path from "node:path";
import { chromium } from "playwright";
import { createTestServer as createServer } from './fixtures/test-server.mjs';

// Real Vue views; every business request is intercepted. No supplier or production API is contacted.
const html = `<!doctype html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;padding:24px"><div id="app"></div>
<script type="module">
import {createApp,h} from 'vue';import {createRouter,createMemoryHistory,RouterView} from 'vue-router';import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';
import Store from '/src/views/ServiceStore.vue';import Orders from '/src/views/ServiceOrders.vue';import {applyAuthSession} from '/src/utils/authSession.js';
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7});
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/services',component:Store},{path:'/service-orders',component:Orders},{path:'/admin/service-orders',component:Orders,meta:{serviceAdmin:true}}]});
await router.push(new URLSearchParams(location.search).get('page')||'/services');await router.isReady();createApp({render:()=>h(RouterView)}).use(router).use(ElementPlus).mount('#app');
</script></body></html>`;
const server = await createServer({
  logLevel: "error",
  server: { host: "127.0.0.1", port: 0 },
  plugins: [
    {
      name: "wuxin-native-fixture",
      configureServer(vite) {
        vite.middlewares.use(async (req, res, next) => {
          if (!req.url?.startsWith("/__wuxin_services")) return next();
          res.setHeader("Content-Type", "text/html;charset=utf-8");
          res.end(await vite.transformIndexHtml(req.url, html));
        });
      },
    },
  ],
});
const product = {
  id: 2,
  providerId: 10,
  providerType: "wuxin",
  project: "sdxy",
  remoteProductId: "sdxy",
  title: "无心 · 校园计划",
  description: "授权码下单，支持按周计划、增次、编辑计划与退款。",
  unitPrice: "0.25",
  priceUnit: "元/次",
  enabled: true,
  available: true,
  version: 0,
  capabilities: [
    "LOOKUP",
    "CREATE",
    "SYNC",
    "REFUND",
    "ADD_TIMES",
    "EDIT_PLAN",
    "REASSIGN",
  ],
};
const order = {
  id: "aa077bb7-abf0-4f0e-8f5e-a578c51d3d50",
  title: product.title,
  accountLabel: "授权账号",
  providerType: "wuxin",
  project: "sdxy",
  status: "ACTIVE",
  quantity: 10,
  completed: 4,
  distance: "2.00",
  paidAmount: "2.50",
  refundedAmount: "0.00",
  pendingOperationId: null,
  createTime: "2026-09-07T08:30:00",
  version: 2,
  actions: ["REFUND", "ADD_TIMES", "EDIT_PLAN", "REASSIGN"],
};
const choices = [
  { field: "runPlanCode", value: "plan-1", label: "计划一" },
  { field: "runPlanCode", value: "plan-2", label: "计划二" },
  { field: "fenceCode", value: "fence-2", label: "校内区域二" },
  { field: "runType", value: "1", label: "日常跑" },
  { field: "runType", value: "2", label: "自由跑" },
];
const quotes = new Map(),
  calls = [],
  problems = [],
  errors = [];
let sequence = 1,
  created = false,
  browser,
  settlements = 0;
function quote(action, amount, quantity = 0) {
  const q = {
    id: `d4a6d316-5c87-4cf7-a5a8-${String(sequence++).padStart(12, "0")}`,
    orderId: action === "CREATE" ? null : order.id,
    action,
    state: "READY",
    title: product.title,
    quantity,
    amount,
    amountLabel:
      action === "CREATE"
        ? "本次余额扣款"
        : action === "SETTLE_REFUND"
          ? "预计退款上限（按实际核实次数结算）"
          : "无需额外扣款",
    expiresAt: "2099-01-01T00:00:00",
  };
  quotes.set(q.id, q);
  return q;
}
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
  const page = await browser.newPage({
    viewport: { width: 1440, height: 1080 },
  });
  page.setDefaultTimeout(30000);
  page.setDefaultNavigationTimeout(60000);
  page.on("pageerror", (error) => errors.push(error.message));
  await page.route("**/*", async (route) => {
    const req = route.request(),
      url = new URL(req.url());
    if (url.origin !== base) {
      problems.push(`unexpected origin ${url.origin}`);
      return route.abort();
    }
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const endpoint = url.pathname.slice(4),
      method = req.method(),
      body = () => JSON.parse(req.postData() || "{}");
    const respond = (data, status = 200) =>
      route.fulfill({
        status,
        contentType: "application/json",
        body: JSON.stringify({ code: 1, data, message: "操作成功" }),
      });
    if (endpoint === "/services")
      return respond({ records: [product], total: 1 });
    if (endpoint === "/services/2/lookup") {
      assert.equal(body().authCode, "demo-authorized-code-not-real");
      assert.equal(body().password, undefined);
      return respond({
        suggested: {
          runPlanCode: "plan-2",
          fenceCode: "fence-2",
          runType: "2",
        },
        choices,
        notice: "模拟授权账号配置",
      });
    }
    if (endpoint === "/services/2/quotes") {
      const data = body();
      assert.equal(data.authorizedAccount, true);
      assert.equal(data.quantity, 10);
      assert.equal(data.fields.authCode, "demo-authorized-code-not-real");
      assert.equal(data.fields.runPlanCode, "plan-2");
      assert.equal(data.fields.password, undefined);
      assert.ok(data.fields.startDate);
      return respond(quote("CREATE", "2.50", 10));
    }
    if (
      endpoint.startsWith("/service-order-operations/") &&
      endpoint.endsWith("/confirm")
    ) {
      const q = quotes.get(endpoint.split("/")[2]);
      assert.ok(q);
      calls.push(q.action);
      await new Promise((resolve) => setTimeout(resolve, 200));
      q.state = "SUCCEEDED";
      q.orderId = order.id;
      if (q.action === "CREATE") created = true;
      if (q.action === "EDIT_PLAN") {
        order.distance = "2.40";
        order.version++;
      }
      return respond(q);
    }
    if (endpoint === "/service-orders")
      return respond({
        records: created ? [order] : [],
        total: created ? 1 : 0,
        current: 1, size: 20,
      });
    if (endpoint === `/service-orders/${order.id}/options`)
      return respond({
        choices,
        suggested: {
          runPlanCode: "plan-2",
          fenceCode: "fence-2",
          runType: "2",
          runTime: "07:30",
          endTime: "09:00",
          distance: "2.4",
          weekdays: "1,3,5",
          pace: "6.5",
          message: "当前订单备注",
        },
        notice: "已读取当前计划",
      });
    if (endpoint === `/service-orders/${order.id}/quotes`) {
      const data = body();
      if (data.action === "EDIT_PLAN") {
        assert.equal(data.fields.runTime, "07:30");
        assert.equal(data.fields.endTime, "09:00");
        assert.equal(data.fields.pace, "6.5");
        assert.equal(data.fields.distance, "2.4");
        assert.equal(data.fields.weekdays, "1,3,5");
        assert.equal(data.fields.authCode, undefined);
        assert.equal(data.fields.startDate, undefined);
      } else assert.equal(data.action, "REASSIGN");
      return respond(quote(data.action, "0.00"));
    }
    if (endpoint === "/admin/service-orders")
      return respond({ records: [order], total: 1, current: 1, size: 20 });
    if (endpoint === `/admin/service-orders/${order.id}/audit`)
      return respond({
        order,
        userId: 7,
        providerId: 10,
        externalOrderNo: "UP-10",
        externalSubOrderNo: null,
        events: [],
      });
    if (endpoint === `/admin/service-orders/${order.id}/refund-quotes`) {
      const data = body();
      assert.equal(data.orderVersion, order.version);
      assert.equal(data.refundedUnits, 6);
      assert.equal(data.upstreamChecked, true);
      assert.ok(data.evidence.length >= 10);
      assert.equal(data.amount, undefined);
      return respond(quote("SETTLE_REFUND", "1.50", 6));
    }
    if (
      endpoint.startsWith("/admin/service-order-operations/") &&
      endpoint.endsWith("/settle-refund")
    ) {
      const q = quotes.get(endpoint.split("/")[3]);
      assert.equal(q?.action, "SETTLE_REFUND");
      settlements++;
      q.state = "SUCCEEDED";
      q.amountLabel = "已退回账户余额";
      order.status = "REFUNDED";
      order.refundedAmount = "1.50";
      order.actions = [];
      order.version++;
      // Simulate a lost HTTP response after a committed local credit; the UI must query, never replay.
      return route.abort("failed");
    }
    if (
      endpoint.startsWith("/admin/service-order-operations/") &&
      method === "GET"
    )
      return respond(quotes.get(endpoint.split("/")[3]));
    problems.push(`${method} ${endpoint}`);
    return respond(null, 404);
  });
  await page.goto(`${base}/__wuxin_services`);
  await page.getByRole("button", { name: "选择服务", exact: true }).click();
  await page
    .getByText("我有权使用此账号及信息，并授权提交", {
      exact: true,
    })
    .click();
  await page
    .getByLabel("本人授权码", { exact: true })
    .fill("demo-authorized-code-not-real");
  await page
    .getByRole("button", { name: "查询账号与可用计划", exact: true })
    .click();
  await page.getByText("模拟授权账号配置", { exact: true }).waitFor();
  const date = page.locator(".wuxin-plan .el-date-editor input");
  await date.fill(
    new Date(Date.now() + 86400000 * 2).toISOString().slice(0, 10),
  );
  await date.press("Tab");
  await page
    .getByRole("button", { name: "预览金额并下单", exact: true })
    .click();
  await page.getByText("¥2.50", { exact: true }).waitFor();
  assert.deepEqual(calls, []);
  await page
    .getByRole("button", { name: "确认并下单", exact: true })
    .dblclick();
  await page
    .getByRole("heading", { name: "我的服务订单", exact: true })
    .waitFor();
  assert.deepEqual(calls, ["CREATE"]);
  assert.equal(
    await page.evaluate(() =>
      JSON.stringify(localStorage).includes("demo-authorized-code-not-real"),
    ),
    false,
  );
  await page.getByRole("button", { name: "编辑计划", exact: true }).click();
  await page.getByRole("dialog", { name: "编辑执行计划" }).waitFor();
  const dialog = page.getByRole("dialog", { name: "编辑执行计划" });
  await dialog
    .locator(".wuxin-plan .el-select__selected-item")
    .filter({ hasText: "07:30" })
    .first()
    .waitFor();
  assert.equal(
    await dialog.locator(".el-input-number input").inputValue(),
    "6.5",
  );
  const output = path.resolve("../.cache/native-service-ui");
  mkdirSync(output, { recursive: true });
  await page.screenshot({
    path: `${output}/wuxin-edit-plan.png`,
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "预览修改", exact: true }).click();
  await page.getByRole("button", { name: "确认操作", exact: true }).click();
  await page
    .getByRole("dialog", { name: "确认本次操作" })
    .waitFor({ state: "hidden" });
  await page.getByText("2.40", { exact: false }).first().waitFor();
  await page.getByRole("button", { name: "重新分配", exact: true }).click();
  await page.getByRole("button", { name: "确认操作", exact: true }).click();
  await page
    .getByRole("dialog", { name: "确认本次操作" })
    .waitFor({ state: "hidden" });
  assert.deepEqual(calls, ["CREATE", "EDIT_PLAN", "REASSIGN"]);
  order.status = "REFUND_REVIEW";
  order.actions = [];
  await page.goto(`${base}/__wuxin_services?page=/admin/service-orders`);
  await page.getByRole("button", { name: "核对退款入账", exact: true }).click();
  const settlement = page.getByRole("dialog", { name: "核对主动退款" });
  await settlement.waitFor();
  await settlement.getByText(/服务订单号 UP-10/).waitFor();
  assert.equal(
    await page
      .getByRole("button", { name: "预览退款金额", exact: true })
      .isDisabled(),
    true,
  );
  await settlement
    .locator("textarea")
    .fill("已核查上游退款单和资金流水，确认退回六次");
  await page.getByText("我已核查退款单与资金流水", { exact: true }).click();
  await page.getByRole("button", { name: "预览退款金额", exact: true }).click();
  await page.getByText("¥1.50", { exact: true }).waitFor();
  assert.equal(settlements, 0);
  await page
    .getByRole("dialog", { name: "核对主动退款" })
    .waitFor({ state: "hidden" });
  await page.screenshot({
    path: `${output}/refund-settlement-preview.png`,
    fullPage: true,
    animations: "disabled",
  });
  await page
    .getByRole("button", { name: "确认退款入账", exact: true })
    .dblclick();
  await page
    .getByRole("button", { name: "检查提交结果", exact: true })
    .waitFor();
  assert.equal(settlements, 1);
  await page.getByRole("button", { name: "检查提交结果", exact: true }).click();
  await page
    .getByRole("dialog", { name: "确认本次操作" })
    .waitFor({ state: "hidden" });
  assert.equal(
    settlements,
    1,
    "lost local settlement responses must not cause another POST",
  );
  await page.locator(".order-card .order-title").getByText("已退款", { exact: true }).waitFor();
  await page.waitForFunction(
    () => document.querySelectorAll(".el-message").length === 0,
  );
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => document.documentElement.classList.add("dark"));
  await page.screenshot({
    path: `${output}/wuxin-refund-mobile-dark.png`,
    fullPage: true,
    animations: "disabled",
  });
  assert.ok(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  );
  // Flash exposes a status but not a completed count: the UI must not invent 0% or prefill a full refund.
  Object.assign(order, {
    providerType: "flash",
    status: "REFUND_REVIEW",
    completed: null,
    title: "闪电 · 进度待确认",
    version: 9,
    actions: [],
  });
  await page.goto(`${base}/__wuxin_services?page=/admin/service-orders`);
  await page
    .getByText(
      "当前订单只返回状态，未返回完成次数；具体执行情况请查看执行记录。",
      { exact: true },
    )
    .waitFor();
  assert.equal(await page.locator(".el-progress").count(), 0);
  await page.getByRole("button", { name: "核对退款入账", exact: true }).click();
  const unknownRefund = page.getByRole("dialog", { name: "核对主动退款" });
  await unknownRefund.getByText(/不能将总次数视为剩余次数/).waitFor();
  assert.equal(
    await unknownRefund.locator(".el-input-number input").inputValue(),
    "0",
  );
  assert.equal(settlements, 1);
  assert.deepEqual(problems, []);
  assert.deepEqual(errors, []);
  console.log(
    "PASS Wuxin authorization → checkout → existing plan preservation → edit/reassign; privileged refund preview → lost reply → query-only confirmation; mobile dark; no real supplier traffic",
  );
} finally {
  await browser?.close();
  await server.close();
}
