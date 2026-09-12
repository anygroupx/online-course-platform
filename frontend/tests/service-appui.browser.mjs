import assert from "node:assert/strict";
import { existsSync, mkdirSync } from "node:fs";
import path from "node:path";
import { chromium } from "playwright";
import { createTestServer } from "./fixtures/test-server.mjs";

// Real Vue views with synthetic data only. Every API request is intercepted before Vite's proxy.
const html = `<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;padding:24px"><div id="app"></div>
<script type="module">
import {createApp,h} from 'vue';import {createRouter,createMemoryHistory,RouterView} from 'vue-router';import ElementPlus from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/element-overrides.scss';import '/src/styles/global.css';import '/src/styles/fluent-spatial.scss';import '/src/styles/responsive.scss';
import Store from '/src/views/ServiceStore.vue';import Orders from '/src/views/ServiceOrders.vue';import Admin from '/src/views/AdminServiceProducts.vue';import {applyAuthSession} from '/src/utils/authSession.js';
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7});
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/services',component:Store},{path:'/service-orders',component:Orders},{path:'/admin/service-products',component:Admin},{path:'/admin/service-orders',component:Orders,meta:{serviceAdmin:true}}]});
await router.push(new URLSearchParams(location.search).get('page')||'/services');await router.isReady();createApp({render:()=>h(RouterView)}).use(router).use(ElementPlus,{locale:zhCn}).mount('#app');
</script></body></html>`;
const server = await createTestServer({
  logLevel: "error", server: { host: "127.0.0.1", port: 0 },
  plugins: [{
    name: "appui-regression-fixture",
    configureServer(vite) {
      vite.middlewares.use(async (req, res, next) => {
        if (!req.url?.startsWith("/__appui_services")) return next();
        res.setHeader("Content-Type", "text/html;charset=utf-8");
        res.end(await vite.transformIndexHtml(req.url, html));
      });
    },
  }],
});
const output = path.resolve(process.env.APPUI_TEST_OUTPUT || "../.cache/native-service-ui/appui");
mkdirSync(output, { recursive: true });
const password = " synthetic+密码&= only ";
const account = "synthetic-student-2026";
const school = "示例职业学院二";
const product = {
  id: 9, providerId: 9, providerType: "appui", project: "3", remoteProductId: "3",
  title: "实习打卡 · 慧职教", description: "按天购买，可编辑执行安排、增加天数和申请退款。",
  unitPrice: "0.25", priceUnit: "元/天", enabled: true, available: true, version: 0,
  capabilities: ["LOOKUP", "CREATE", "SYNC", "EDIT_PLAN", "ADD_TIMES", "REFUND"],
};
const order = {
  id: "ac077bb7-abf0-4f0e-8f5e-a578c51d3d59", title: product.title, accountLabel: "sy***26",
  providerType: "appui", project: "3", status: "ACTIVE", quantity: 10, completed: 4,
  quantityUnit: "天", distance: null, schedule: null, paidAmount: "2.50", refundedAmount: "0.00",
  pendingOperationId: null, createTime: "2026-09-12T08:30:00", version: 1,
  actions: ["EDIT_PLAN", "ADD_TIMES", "REFUND"],
  statusCheck: { checkedAt: "2026-09-12T09:10:00", delayed: false },
};
let plan = { address: "示例市实训路18号（当前安排）", startTime: "08:17", endTime: "17:43", weekdays: "2,4,6", reports: "2,3" };
const quotes = new Map(), requests = [], confirmations = [], unexpected = [], errors = [], consoleErrors = [], failedRequests = [], saves = [];
let browser, page, created = false, sequence = 1, refundId, releaseOldLookup;
const oldLookup = new Promise((resolve) => { releaseOldLookup = resolve; });
function quote(action, quantity, amount) {
  const value = {
    id: `d4a6d316-5c87-4cf7-a5a8-${String(sequence++).padStart(12, "0")}`,
    orderId: action === "CREATE" ? null : order.id, action, state: "READY", title: product.title,
    quantity, quantityUnit: "天", amount, unitCharge: "0.25000000", distancePlan: null,
    amountLabel: action === "REFUND" ? "预计退款上限" : amount === "0.00" ? "无需额外扣款" : "本次余额扣款",
    expiresAt: "2099-01-01T00:00:00", errorCategory: null,
  };
  quotes.set(value.id, value);
  return value;
}
function events() {
  // EventView has no providerType or quantityUnit; the view must retain its order context.
  return confirmations.map((id) => {
    const q = quotes.get(id);
    return { id, action: q.action, state: q.state, amount: q.amount, errorCategory: q.errorCategory, createTime: "2026-09-12T09:20:00" };
  });
}
async function screenshot(name) {
  await page.screenshot({ path: path.join(output, name), fullPage: true, animations: "disabled" });
}
async function noOverflow() {
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), "page must fit the viewport");
}
try {
  await server.listen();
  const base = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({
    executablePath: process.env.BROWSER_PATH || (existsSync("/snap/bin/chromium") ? "/snap/bin/chromium" : undefined),
    headless: true, args: ["--no-sandbox", "--disable-dev-shm-usage"],
  });
  page = await browser.newPage({ viewport: { width: 1440, height: 1080 }, locale: "zh-CN", timezoneId: "America/Los_Angeles" });
  page.setDefaultTimeout(15000);
  page.setDefaultNavigationTimeout(60000);
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => { if (message.type() === "error") consoleErrors.push({ text: message.text(), url: message.location().url }); });
  page.on("requestfailed", (request) => failedRequests.push({ url: request.url(), error: request.failure()?.errorText }));
  await page.route("**/*", async (route) => {
    const req = route.request(), url = new URL(req.url());
    if (url.origin !== base) { unexpected.push(`origin ${url.origin}`); return route.abort(); }
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const endpoint = url.pathname.slice(4), method = req.method();
    const respond = (data, code = 1) => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ code, data, message: code === 1 ? "操作成功" : "模拟请求不匹配" }) });
    try {
      const body = JSON.parse(req.postData() || "{}");
      requests.push({ endpoint, method, body, query: Object.fromEntries(url.searchParams) });
      if (endpoint === "/admin/api-providers" && method === "GET") return respond({ records: [{ id: 9, name: "已验证实习服务", providerType: "appui", status: 1, verifiedAt: "2026-09-12T08:00:00" }], total: 1 });
      if (endpoint === "/admin/service-products" && method === "GET") return respond({ records: saves.length ? [product] : [], total: saves.length });
      if (endpoint === "/admin/plugin-integrations/P09/providers/9/catalog" && method === "GET") {
        assert.equal(url.searchParams.get("project"), "3");
        return respond([{ id: "3", name: product.title, unitPrice: "0.10", priceUnit: "元/天" }, { id: "9", name: "不属于所选项目的商品", unitPrice: "0.10", priceUnit: "元/天" }]);
      }
      if (endpoint === "/admin/service-products" && method === "POST") {
        assert.equal(body.providerId, 9); assert.equal(body.project, "3"); assert.equal(body.remoteProductId, "3");
        assert.equal(body.unitPrice, "0.25"); assert.equal(body.enabled, true); assert.equal(body.contractPrice, null);
        saves.push(body); return respond(product);
      }
      if (endpoint === "/services" && method === "GET") return respond({ records: [product], total: 1 });
      if (endpoint === "/services/9/schools" && method === "GET") {
        assert.deepEqual([...url.searchParams.keys()].sort(), ["keyword", "page"]);
        assert.equal(url.searchParams.get("keyword"), "示例");
        const current = Number(url.searchParams.get("page"));
        assert.ok([1, 2].includes(current));
        const names = current === 1 ? Array.from({ length: 20 }, (_, i) => `示例大学${i + 1}`) : ["示例职业学院", school];
        // P09's real read model uses each exact school name as both id and name, not a numeric code.
        return respond({ items: names.map((name) => ({ id: name, name })), page: current, pageSize: 20, hasMore: current === 1 });
      }
      if (endpoint === "/services/9/lookup" && method === "POST") {
        assert.equal(body.password, password); assert.equal(body.studentName, undefined);
        assert.equal(body.distance, undefined); assert.equal(body.quantity, undefined);
        if (body.account === "obsolete-account") {
          assert.equal(body.schoolName, "示例职业学院");
          await oldLookup;
          return respond({ suggested: { studentName: "过期姓名", address: "过期地址" }, choices: [], notice: "过期查询不得覆盖当前账号" });
        }
        assert.equal(body.account, account); assert.equal(body.schoolName, school);
        return respond({ suggested: { studentName: "测试同学", address: "示例市实训路18号" }, choices: [], notice: "账号已查询，请核对姓名和地址，再选择服务天数与执行安排。" });
      }
      if (endpoint === "/services/9/quotes" && method === "POST") {
        assert.equal(body.quantity, 10); assert.equal(body.distance, null); assert.equal(body.schedule, null);
        assert.deepEqual(body.taskTimes, []); assert.equal(body.accountSessionId, null); assert.equal(body.authorizedAccount, true);
        assert.deepEqual(body.fields, { account, password, schoolName: school, address: "示例市实训路18号", startTime: "07:31", endTime: "18:10", weekdays: "1,2,3,4,5", reports: "1" });
        return respond(quote("CREATE", 10, "2.50"));
      }
      if ((endpoint === "/service-orders" || endpoint === "/admin/service-orders") && method === "GET") return respond({ records: created ? [order] : [], total: created ? 1 : 0, current: 1, size: 20 });
      if (endpoint === `/service-orders/${order.id}/sync` && method === "POST") {
        order.completed = 5; order.version++; order.statusCheck.checkedAt = "2026-09-12T09:30:00";
        return respond(order);
      }
      if (endpoint === `/service-orders/${order.id}/options` && method === "GET") return respond({
        suggested: { ...plan, password: "never-render-this-value", account: "private-account", distance: "999", studentName: "not-editable" },
        choices: [], notice: "已读取当前执行安排；留空密码会保留原密码。",
      });
      if (endpoint === `/service-orders/${order.id}/quotes` && method === "POST") {
        if (body.action === "EDIT_PLAN") {
          assert.equal(body.quantity, 0);
          assert.deepEqual(body.fields, { ...plan, startTime: "09:07", password: "" });
          return respond(quote("EDIT_PLAN", 0, "0.00"));
        }
        assert.deepEqual(Object.keys(body).sort(), ["action", "quantity"]);
        if (body.action === "ADD_TIMES") { assert.equal(body.quantity, 3); return respond(quote("ADD_TIMES", 3, "0.75")); }
        assert.equal(body.action, "REFUND"); assert.equal(body.quantity, 0);
        const q = quote("REFUND", order.quantity - order.completed, "2.00"); refundId = q.id;
        return respond(q);
      }
      if (endpoint.startsWith("/service-order-operations/") && endpoint.endsWith("/confirm") && method === "POST") {
        const q = quotes.get(endpoint.split("/")[2]); assert.ok(q); assert.equal(q.state, "READY", "a confirmation may only be posted once");
        confirmations.push(q.id);
        await new Promise((resolve) => setTimeout(resolve, 180));
        q.orderId = order.id;
        if (q.action === "REFUND") {
          q.state = "UNKNOWN"; q.errorCategory = "TIMEOUT"; order.pendingOperationId = q.id; order.actions = [];
          // A lost transport response is ambiguous. Only a GET for this exact operation may follow.
          return route.abort("failed");
        }
        q.state = "SUCCEEDED";
        if (q.action === "CREATE") created = true;
        if (q.action === "EDIT_PLAN") { plan.startTime = "09:07"; order.version++; }
        if (q.action === "ADD_TIMES") { order.quantity += q.quantity; order.paidAmount = "3.25"; order.version++; }
        return respond(q);
      }
      if (endpoint.startsWith("/service-order-operations/") && method === "GET") {
        const q = quotes.get(endpoint.split("/")[2]); assert.ok(q); return respond(q);
      }
      if (endpoint === `/service-orders/${order.id}/events` && method === "GET") return respond(events());
      if (endpoint === `/admin/service-orders/${order.id}/audit` && method === "GET") return respond({ order, userId: 7, providerId: 9, externalOrderNo: "9001", events: events().map((operation) => ({ operation, resolvedBy: null, evidence: null })) });
      if (endpoint === `/service-orders/${order.id}/logs` && method === "GET") return respond({ items: [{ id: "5001", time: "2026-09-12 08:17:00", status: "签到：已签 · 签退：待执行" }], page: 1, hasMore: false });
      unexpected.push(`${method} ${endpoint}`); return respond(null, 0);
    } catch (error) {
      unexpected.push(`${method} ${endpoint}: ${error.stack}`);
      return respond(null, 0);
    }
  });

  // Configure the product through the real administrator UI; saving is simulated, never deployed.
  await page.goto(`${base}/__appui_services?page=/admin/service-products`);
  await page.getByRole("button", { name: "上架服务商品", exact: true }).click();
  const adminDialog = page.getByRole("dialog", { name: "上架服务商品", exact: true });
  await adminDialog.getByRole("combobox").first().click();
  await page.getByRole("option", { name: "已验证实习服务 · 实习打卡", exact: true }).click();
  await adminDialog.locator(".el-form-item").filter({ hasText: "实习项目" }).locator(".el-select").click();
  await page.getByRole("option", { name: "慧职教", exact: true }).click();
  await adminDialog.getByRole("button", { name: "读取目录", exact: true }).click();
  await adminDialog.getByText("先读取服务目录", { exact: true }).click();
  await page.getByRole("option", { name: `${product.title} · 成本 ¥0.10`, exact: true }).click();
  assert.equal(await page.getByRole("option", { name: /不属于所选项目/ }).count(), 0);
  await adminDialog.getByLabel("销售单价（元/天，最多六位小数）", { exact: true }).fill("0.25");
  await adminDialog.locator(".el-switch__core").click();
  await screenshot("appui-product-desktop.png");
  await adminDialog.getByRole("button", { name: "保存商品", exact: true }).click();
  await page.getByText("服务商品已保存", { exact: true }).waitFor();
  assert.equal(saves.length, 1);

  await page.goto(`${base}/__appui_services`);
  await page.getByRole("button", { name: "选择服务", exact: true }).click();
  const drawer = page.getByRole("dialog", { name: product.title, exact: true });
  const lookup = drawer.getByRole("button", { name: "查询账号", exact: true });
  const preview = page.getByRole("button", { name: "预览金额并下单", exact: true });
  assert.equal(await lookup.isDisabled(), true);
  assert.equal(await drawer.getByRole("button", { name: "查询学校", exact: true }).isDisabled(), true);
  assert.equal(await preview.isDisabled(), true);
  await drawer.getByLabel("服务账号 / 手机号", { exact: true }).fill("obsolete-account");
  await drawer.getByLabel("服务密码", { exact: true }).fill(password);
  await drawer.getByText("我有权使用此账号及信息，并授权提交", { exact: true }).click();
  assert.equal(await lookup.isDisabled(), true, "school selection is mandatory for project 3");
  await drawer.getByLabel("学校关键词", { exact: true }).fill("示例");
  await drawer.getByRole("button", { name: "查询学校", exact: true }).click();
  await drawer.getByText("第 1 页 · 20 所学校", { exact: true }).waitFor();
  await drawer.getByRole("navigation", { name: "学校分页" }).getByRole("button", { name: "下一页", exact: true }).click();
  await drawer.getByText("第 2 页 · 2 所学校", { exact: true }).waitFor();
  await drawer.locator(".school-option").filter({ hasText: "示例职业学院" }).first().click();

  // Neither a changed account nor a changed school may accept an obsolete account lookup.
  const pendingLookup = page.waitForRequest((request) => request.url().endsWith("/services/9/lookup"));
  await lookup.click(); await pendingLookup;
  await drawer.getByLabel("服务账号 / 手机号", { exact: true }).fill(account);
  await drawer.locator(".school-option").filter({ hasText: school }).click();
  await lookup.click();
  await drawer.getByText("测试同学", { exact: true }).waitFor();
  const staleReply = page.waitForResponse((response) => response.url().endsWith("/services/9/lookup") && response.request().postDataJSON().account === "obsolete-account");
  releaseOldLookup();
  await (await staleReply).finished();
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  assert.equal(await drawer.getByText("过期姓名", { exact: true }).count(), 0);
  assert.equal(await drawer.getByLabel("打卡地址", { exact: true }).inputValue(), "示例市实训路18号");
  assert.equal(await drawer.getByLabel("姓名", { exact: true }).count(), 0, "queried name is read-only");
  assert.equal(await drawer.getByText(/每次距离|公里|首次执行日期/).count(), 0);
  assert.equal(await drawer.locator(".el-date-editor").count(), 0);
  assert.equal(await drawer.getByLabel("购买天数", { exact: true }).inputValue(), "10");
  await drawer.getByText("日报", { exact: true }).click();
  assert.equal(await preview.isDisabled(), true);
  await drawer.getByText("日报", { exact: true }).click();
  await drawer.getByLabel("上班时间", { exact: true }).fill("19:00");
  assert.equal(await preview.isDisabled(), true);
  await drawer.getByLabel("上班时间", { exact: true }).fill("07:31");
  assert.equal(await preview.isDisabled(), false);
  assert.equal(confirmations.length, 0);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => document.documentElement.classList.add("dark"));
  await drawer.locator(".el-drawer__body").evaluate((node) => { node.scrollTop = 0; });
  await screenshot("appui-checkout-mobile-dark.png"); await noOverflow();
  await drawer.getByLabel("上班时间", { exact: true }).scrollIntoViewIfNeeded();
  await screenshot("appui-plan-mobile-dark.png"); await noOverflow();
  assert.ok(await drawer.locator(".appui-time-grid").evaluate((node) => node.scrollWidth <= node.clientWidth + 1));
  await page.setViewportSize({ width: 1440, height: 1080 });
  await page.evaluate(() => document.documentElement.classList.remove("dark"));
  await preview.click();
  const confirmation = page.getByRole("dialog", { name: "确认本次操作", exact: true });
  await confirmation.getByText("¥2.50", { exact: true }).waitFor();
  await confirmation.getByText("¥0.25000000 / 天", { exact: true }).waitFor();
  assert.equal(confirmations.length, 0);
  await screenshot("appui-create-quote-desktop.png");
  await confirmation.getByRole("button", { name: "确认并下单", exact: true }).dblclick();
  await page.getByRole("heading", { name: "我的服务订单", exact: true }).waitFor();
  assert.equal(confirmations.length, 1);
  assert.equal(await page.evaluate((value) => JSON.stringify(localStorage).includes(value) || JSON.stringify(sessionStorage).includes(value), password), false);
  await page.getByText("已使用 / 已购天数", { exact: true }).waitFor();
  await page.getByText("6 天", { exact: true }).waitFor();
  assert.equal(await page.locator(".el-progress").count(), 0);
  await page.getByText("已使用天数不代表签到成功，具体结果请查看执行记录。", { exact: true }).waitFor();
  await page.getByRole("button", { name: "核对天数与状态", exact: true }).click();
  await page.getByText("5 天", { exact: true }).waitFor();
  await page.getByRole("status", { name: "天数与状态核对情况", exact: true }).getByText("2026-09-12 09:30:00", { exact: true }).waitFor();
  assert.equal(confirmations.length, 1, "status checking does not charge");

  // Current plan values, including non-default minutes, must survive editing without credentials.
  await page.getByRole("button", { name: "编辑安排", exact: true }).click();
  const editor = page.getByRole("dialog", { name: "编辑执行安排", exact: true });
  await editor.waitFor();
  assert.equal(await editor.getByLabel("打卡地址", { exact: true }).inputValue(), plan.address);
  assert.equal(await editor.getByLabel("上班时间", { exact: true }).inputValue(), "08:17");
  assert.equal(await editor.getByLabel("下班时间", { exact: true }).inputValue(), "17:43");
  for (const day of ["周二", "周四", "周六", "周报", "月报"]) assert.equal(await editor.getByRole("checkbox", { name: day, exact: true }).isChecked(), true);
  for (const day of ["周一", "周三", "周五", "周日", "日报"]) assert.equal(await editor.getByRole("checkbox", { name: day, exact: true }).isChecked(), false);
  assert.equal(await editor.getByLabel("新密码（选填）", { exact: true }).inputValue(), "");
  assert.equal(await editor.getByText(/never-render-this-value|private-account|not-editable|每次距离/).count(), 0);
  await editor.getByLabel("上班时间", { exact: true }).fill("09:07");
  await screenshot("appui-edit-plan-desktop.png");
  await editor.getByRole("button", { name: "预览修改", exact: true }).click();
  await confirmation.getByText(`编辑安排 · ${product.title}`, { exact: true }).waitFor();
  await confirmation.getByRole("button", { name: "确认操作", exact: true }).dblclick();
  await confirmation.waitFor({ state: "hidden" });
  await page.getByText("编辑安排已受理。", { exact: true }).waitFor();
  assert.equal(confirmations.length, 2);
  product.unitPrice = "0.40"; // A later product price must not replace the order's frozen quoted unit charge.
  await page.getByRole("button", { name: "增加天数", exact: true }).click();
  const renewal = page.getByRole("dialog", { name: "增加天数", exact: true });
  await renewal.getByRole("textbox").fill("3");
  await renewal.getByRole("button", { name: /^(确定|确认|OK)$/ }).click();
  await confirmation.getByText("¥0.75", { exact: true }).waitFor();
  await confirmation.getByText("¥0.25000000 / 天", { exact: true }).waitFor();
  await confirmation.getByText("3 天", { exact: true }).waitFor();
  await confirmation.getByRole("button", { name: "确认操作", exact: true }).dblclick();
  await confirmation.waitFor({ state: "hidden" });
  await page.getByText("增加天数已受理。", { exact: true }).waitFor();
  await page.getByText("8 天", { exact: true }).waitFor();
  assert.equal(confirmations.length, 3);
  await page.getByRole("button", { name: "操作记录", exact: true }).click();
  const history = page.getByRole("dialog", { name: "订单操作记录", exact: true });
  await history.getByText(/增加天数 ·/).waitFor();
  await history.getByText(/编辑安排 ·/).waitFor();
  assert.equal(await history.getByText(/增加次数|编辑计划/).count(), 0);
  await history.getByRole("button", { name: /close|关闭/i }).click();
  await page.getByRole("button", { name: "执行记录", exact: true }).click();
  const logs = page.getByRole("dialog", { name: "执行记录", exact: true });
  await logs.getByText("签到：已签 · 签退：待执行", { exact: true }).waitFor();
  await logs.getByRole("button", { name: /close|关闭/i }).click();

  // Administration also renders EventView in the containing order's day-based context.
  await page.goto(`${base}/__appui_services?page=/admin/service-orders`);
  await page.getByRole("button", { name: "核对资料与记录", exact: true }).click();
  const audit = page.getByRole("dialog", { name: "核对资料与审计记录", exact: true });
  await audit.getByText(/增加天数 ·/).waitFor();
  await audit.getByText(/编辑安排 ·/).waitFor();
  assert.equal(await audit.getByText(/增加次数|编辑计划/).count(), 0);

  // COMPLETED is not proof that all purchased days were used; refunds remain subject to a receipt.
  order.status = "COMPLETED";
  await page.goto(`${base}/__appui_services?page=/service-orders`);
  await page.locator(".order-title").getByText("服务已结束", { exact: true }).waitFor();
  await page.getByText("8 天", { exact: true }).waitFor();
  await page.getByRole("button", { name: "取消并退款", exact: true }).click();
  await confirmation.getByText("¥2.00", { exact: true }).waitFor();
  await confirmation.getByRole("button", { name: "确认退款", exact: true }).dblclick();
  await confirmation.getByRole("button", { name: "检查提交结果", exact: true }).waitFor();
  assert.equal(confirmations.length, 4);
  const checksBefore = requests.filter((r) => r.endpoint === `/service-order-operations/${refundId}` && r.method === "GET").length;
  await confirmation.getByRole("button", { name: "检查提交结果", exact: true }).click();
  await confirmation.getByText("待人工核对", { exact: true }).waitFor();
  assert.equal(requests.filter((r) => r.endpoint === `/service-order-operations/${refundId}` && r.method === "GET").length, checksBefore + 1);
  assert.equal(confirmations.length, 4, "an uncertain refund cannot be replayed");
  assert.equal(order.refundedAmount, "0.00");
  await confirmation.getByRole("button", { name: "稍后查看订单", exact: true }).click();
  await confirmation.waitFor({ state: "hidden" });
  await page.locator(".order-card").getByText("待核对", { exact: true }).waitFor();
  assert.equal(await page.getByRole("button", { name: "取消并退款", exact: true }).count(), 0);
  assert.equal(await page.getByText(/退款已入账|退款成功/).count(), 0);
  await page.waitForFunction(() => !document.querySelector(".el-message"));
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => document.documentElement.classList.add("dark"));
  await screenshot("appui-unknown-refund-mobile-dark.png"); await noOverflow();
  assert.deepEqual(failedRequests, [{ url: `${base}/api/service-order-operations/${refundId}/confirm`, error: "net::ERR_FAILED" }]);
  assert.ok(consoleErrors.every((error) => error.text === "Failed to load resource: net::ERR_FAILED" && error.url === `${base}/api/service-order-operations/${refundId}/confirm`), JSON.stringify(consoleErrors));
  assert.deepEqual(unexpected, []); assert.deepEqual(errors, []);
  assert.equal(saves.length, 1);
  assert.deepEqual(confirmations.map((id) => quotes.get(id).action), ["CREATE", "EDIT_PLAN", "ADD_TIMES", "REFUND"]);
  console.log("PASS AppUI: project catalog → school-name pagination → consent/account lookup → stale-reply rejection → day checkout → single confirmation → status/plan/renewal → contextual audit/logs → uncertain refund GET-only recovery; mobile dark; no live API calls");
} catch (error) {
  console.error("AppUI isolated regression diagnostics", { unexpected, errors, consoleErrors, failedRequests });
  if (page && !page.isClosed()) await screenshot("appui-failure.png").catch(() => {});
  throw error;
} finally {
  releaseOldLookup();
  await browser?.close();
  await server.close();
}
