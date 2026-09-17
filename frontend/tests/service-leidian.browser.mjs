import assert from "node:assert/strict";
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";
import { chromium } from "playwright";
import { createTestServer } from "./fixtures/test-server.mjs";

// Actual Vue screens; all business traffic is synthetic and intercepted on loopback.
const html = `<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;padding:20px"><div id="app"></div>
<script type="module">
import {createApp,h} from 'vue';import {createRouter,createMemoryHistory,RouterView} from 'vue-router';import ElementPlus from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/element-overrides.scss';import '/src/styles/global.css';import '/src/styles/fluent-spatial.scss';import '/src/styles/responsive.scss';
import Store from '/src/views/ServiceStore.vue';import Orders from '/src/views/ServiceOrders.vue';import Admin from '/src/views/AdminServiceProducts.vue';import Providers from '/src/views/AdminApiProviders.vue';
import {applyAuthSession,clearAuthSession} from '/src/utils/authSession.js';
window.testLogin=(userId=7)=>applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600,sub:userId}))+'.signature',userId});window.testLogin();
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/services',component:Store},{path:'/service-orders',component:Orders},{path:'/admin/service-products',component:Admin},{path:'/admin/service-orders',component:Orders,meta:{serviceAdmin:true}},{path:'/providers',component:Providers}]});
window.navigate=(path)=>router.push(path);window.logout=clearAuthSession;
await router.push('/services');await router.isReady();createApp({render:()=>h(RouterView,null,{default:({Component,route})=>Component?h(Component,{key:route.path}):null})}).use(router).use(ElementPlus,{locale:zhCn}).mount('#app');
</script></body></html>`;
const server = await createTestServer({ logLevel: "error", server: { host: "127.0.0.1", port: 0 }, plugins: [{
  name: "leidian-regression-fixture", configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
    if (!req.url?.startsWith("/__leidian")) return next();
    res.setHeader("Content-Type", "text/html;charset=utf-8"); res.end(await vite.transformIndexHtml(req.url, html));
  }); },
}] });
const output = path.resolve(process.env.LEIDIAN_TEST_OUTPUT || "../.cache/native-service-ui/leidian");
mkdirSync(output, { recursive: true });
const names = ["步道乐跑", "步道人脸跑", "步道自由跑", "乐健体育"];
const products = names.map((name, i) => ({ id: 121 + i, providerId: 12, providerType: "leidian", project: String(i + 1), remoteProductId: String(i + 1),
  title: `雷电 · ${name}`, description: "安排运动次数，查看执行记录和成绩查询信息。", unitPrice: "0.350000", priceUnit: "元/次·公里", enabled: true, available: true, version: 0,
  capabilities: ["LOOKUP", "CREATE", "SYNC", "CANCEL", "EDIT_PLAN", "CHANGE_TIME", "SCORE_INFO"] }));
const basePlan = { startDate: "2026-09-13", startTime: "06:07:08", endTime: "07:08:09", weekdays: "7,2" };
const rules = { suggested: { schoolName: "示例运动大学", account: "never-merge-account", password: "never-merge-password" },
  choices: [{ field: "zoneId", value: "9007199254740993", label: "田径场" }],
  runRules: [{ distance: "3.5", startTime: "08:09:10", endTime: "09:10:11" }], notice: "仅模拟规则" };
const records = [], quotes = new Map(), calls = [], confirmations = [], saves = [], resolutions = [], unexpected = [], errors = [], consoleErrors = [], failedRequests = [];
let browser, page, sequence = 1, scoreMode = "ok", confirmMode = "ok", failPage2 = false;
const gates = new Map();
function gate(name) {
  let release, markStarted;
  const promise = new Promise(r => { release = r; }), started = new Promise(r => { markStarted = r; });
  const value = { name, promise, release, started, markStarted, used: false }; gates.set(name, value); return value;
}
async function waitForGate(value) {
  let timeout;
  try {
    await Promise.race([value.started, new Promise((_, reject) => {
      timeout = setTimeout(() => reject(new Error(`Timed out waiting for mocked request: ${value.name}`)), 15000);
    })]);
  } finally { clearTimeout(timeout); }
}
async function hold(name) {
  const value = gates.get(name); if (!value || value.used) return;
  value.used = true; value.markStarted(); await value.promise;
}
async function afterResponse(pending) {
  const response = await pending; await response.finished();
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
}
function quote(action, record, data = {}) {
  const id = `bfa54304-5104-4f5a-8888-${String(sequence++).padStart(12, "0")}`;
  const q = { id, orderId: record?.id || null, action, state: "READY", title: record?.title || data.product.title, quantity: data.quantity ?? 0,
    quantityUnit: "次", amount: action === "CREATE" ? "18.47" : action === "SETTLE_REFUND" ? "4.62" : "0.00", unitCharge: "1.84700000",
    amountLabel: action === "CREATE" ? "本次余额扣款" : action === "SETTLE_REFUND" ? "本次退款入账" : "无需额外扣款",
    expiresAt: "2026-09-12T09:05:00", errorCategory: null, distancePlan: null };
  quotes.set(id, { q, record, data }); return q;
}
function createRecord(p, pending) {
  const record = { id: `7d43a924-55ab-4c42-812a-${String(records.length + 1).padStart(12, "0")}`, providerType: "leidian", project: p.project,
    title: p.title, accountLabel: pending ? "sy***pending" : `sy***${p.project}`, status: pending ? "CONFIRMING" : "ACTIVE", quantity: 10, completed: 3,
    distance: "3.5", quantityUnit: "次", paidAmount: "18.47", refundedAmount: "0.00", version: 1, createTime: "2026-09-12T09:00:00",
    actions: pending ? [] : ["EDIT_PLAN", "CHANGE_TIME", "SCORE_INFO", "CANCEL"], pendingOperationId: null,
    statusCheck: { checkedAt: "2026-09-12T09:00:00", delayed: false } };
  records.push(record); return record;
}
function audit(record) { return { order: record, userId: 7, providerId: 12, externalOrderNo: `business_${record.project}`, externalSubOrderNo: "9007199254740993", events: [] }; }
const exactFields = (actual, expected) => assert.deepEqual(actual, expected);
async function navigate(url) { await page.evaluate(url => window.navigate(url), url); }
async function shot(name) { await page.screenshot({ path: path.join(output, name), fullPage: false, animations: "disabled" }); }
async function noOverflow() { assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), "screen must fit the viewport"); }
function card(record) { return page.locator(".order-card").filter({ has: page.locator(".order-number", { hasText: record.id }) }); }
function drawer() { return page.locator(".leidian-checkout"); }
async function setChecked(input, checked = true) {
  // Element Plus hides its native input; exercise the visible label, not a forced click.
  if (await input.isChecked() !== checked) await input.locator("xpath=ancestor::label[1]").click();
  assert.equal(await input.isChecked(), checked);
}
async function closeDrawer(locator) { await locator.locator(".el-drawer__close-btn").click(); await locator.waitFor({ state: "hidden" }); }
async function confirm(name = "确认操作") {
  const button = page.getByRole("button", { name, exact: true });
  await button.evaluate(el => { el.click(); el.click(); });
  await page.locator(".service-quote-dialog").waitFor({ state: "hidden" });
}
async function chooseZone() {
  await drawer().getByLabel("跑区", { exact: true }).locator("xpath=ancestor::div[contains(@class, 'el-select__wrapper')][1]").click();
  await page.getByRole("option", { name: "田径场", exact: true }).click();
}
async function openProduct(i, account = i === 3 ? "13800138000" : `synthetic_uid_${i + 1}`) {
  await page.locator(".service-card").filter({ has: page.getByRole("heading", { name: products[i].title, exact: true }) }).getByRole("button", { name: "选择服务" }).click();
  await setChecked(drawer().getByRole("checkbox", { name: "我有权使用此账号及信息，并授权提交" }));
  await drawer().getByLabel(i === 3 ? "手机号" : "账号 UID", { exact: true }).fill(account);
  assert.equal(await drawer().locator('input[type="password"]').count(), 0);
}
async function fillPlan(i) {
  if (i < 3) {
    await drawer().getByRole("button", { name: "查询跑区与规则" }).click();
    await drawer().getByText("示例运动大学", { exact: true }).waitFor();
    assert.equal(await drawer().getByLabel("开始时间", { exact: true }).inputValue(), "", "recommendations must be explicitly adopted");
    await chooseZone(); await drawer().getByRole("button", { name: /3\.5 公里/ }).click();
  } else {
    assert.equal(await drawer().getByRole("button", { name: "查询跑区与规则" }).count(), 0);
    assert.equal(await drawer().getByLabel("跑区", { exact: true }).count(), 0);
    await drawer().getByLabel("开始时间", { exact: true }).fill("08:09:10");
    await drawer().getByLabel("结束时间", { exact: true }).fill("09:10:11");
    await drawer().getByLabel("每次距离（公里）", { exact: true }).fill("3.5");
  }
  await drawer().getByLabel("开始日期（北京时间）", { exact: true }).fill("2026-09-13");
}
try {
  await server.listen(); const base = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({ executablePath: process.env.BROWSER_PATH || (existsSync("/snap/bin/chromium") ? "/snap/bin/chromium" : undefined), headless: true, args: ["--no-sandbox", "--disable-dev-shm-usage"] });
  page = await browser.newPage({ viewport: { width: 1440, height: 1080 }, locale: "zh-CN", timezoneId: "America/Los_Angeles", reducedMotion: "reduce" });
  await page.clock.setFixedTime(new Date("2026-09-12T01:00:00.400Z"));
  page.setDefaultTimeout(15000); page.setDefaultNavigationTimeout(60000);
  page.on("pageerror", error => errors.push(error.message));
  page.on("console", message => { if (message.type() === "error") consoleErrors.push({ text: message.text(), url: message.location().url }); });
  page.on("requestfailed", req => failedRequests.push({ url: req.url(), error: req.failure()?.errorText }));
  await page.route("**/*", async route => {
    const req = route.request(), url = new URL(req.url());
    if (url.origin !== base) { unexpected.push(`external ${url.origin}`); return route.abort(); }
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const endpoint = url.pathname.slice(4), method = req.method(), body = JSON.parse(req.postData() || "{}");
    calls.push({ endpoint, method, body, query: Object.fromEntries(url.searchParams) });
    const respond = (data, code = 1) => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ code, data, message: code === 1 ? "操作成功" : "模拟响应暂时不可用" }) });
    try {
      if (endpoint === "/services" && method === "GET") return respond({ records: products, total: 4 });
      if (endpoint === "/admin/api-providers" && method === "GET") return respond({ records: [{ id: 12, name: "演示运动服务", providerType: "leidian", status: 1, verifiedAt: "2026-09-12T08:00:00", verified: true }], total: 1 });
      if (endpoint === "/admin/service-products" && method === "GET") return respond({ records: saves.length ? [products[3]] : [], total: saves.length });
      if (endpoint === "/admin/plugin-integrations/P12/providers/12/catalog" && method === "GET") {
        assert.equal(url.searchParams.get("project"), "4");
        return respond([{ id: "4", name: products[3].title, unitPrice: "0.2" }, { id: "1", name: "错误项目不应出现", unitPrice: "0.1" }, { id: 4, name: "数字编号不应出现", unitPrice: "0.1" }]);
      }
      if (endpoint === "/admin/service-products" && method === "POST") {
        assert.equal(body.providerId, 12); assert.equal(body.project, "4"); assert.equal(body.remoteProductId, "4");
        assert.equal(body.unitPrice, "0.35"); assert.equal(body.enabled, true); assert.equal(body.contractPrice, null);
        saves.push(body); return respond(products[3]);
      }
      const lookupMatch = /^\/services\/(12[1-3])\/lookup$/.exec(endpoint);
      if (lookupMatch && method === "POST") {
        assert.deepEqual(Object.keys(body), ["account"]);
        if (body.account === "slow_uid") { await hold("lookup"); return respond({ ...rules, suggested: { schoolName: "过期学校不得出现" } }); }
        if (body.account === "invalid_uid") return respond({ ...rules, choices: [{ field: "zoneId", value: 123, label: "不安全编号" }] });
        if (body.account === "error_uid") return respond(null, 0);
        return respond(rules);
      }
      const createMatch = /^\/services\/(12[1-4])\/quotes$/.exec(endpoint);
      if (createMatch && method === "POST") {
        const p = products.find(p => p.id === Number(createMatch[1]));
        assert.deepEqual(Object.keys(body).sort(), ["accountSessionId", "authorizedAccount", "distance", "fields", "quantity", "schedule", "taskTimes"]);
        assert.equal(body.authorizedAccount, true); assert.equal(body.accountSessionId, null); assert.equal(body.schedule, null); assert.deepEqual(body.taskTimes, []);
        assert.equal(body.quantity, 10); assert.equal(body.distance, "3.5");
        exactFields(body.fields, { account: confirmMode === "unreadable" ? "synthetic_pending" : p.project === "4" ? "13800138000" : `synthetic_uid_${p.project}`,
          startDate: "2026-09-13", startTime: "08:09:10", endTime: "09:10:11", weekdays: "1,2,3,4,5", ...(p.project === "4" ? {} : { zoneId: "9007199254740993" }) });
        return respond(quote("CREATE", null, { product: p, ...body }));
      }
      if (["/service-orders", "/admin/service-orders"].includes(endpoint) && method === "GET") {
        const orderId = url.searchParams.get("orderId"), type = url.searchParams.get("providerType");
        const rows = records.filter(o => (!orderId || o.id === orderId) && (!type || type === o.providerType));
        return respond({ records: rows, total: rows.length, current: Number(url.searchParams.get("page") || 1), size: 20 });
      }
      const opMatch = /^\/(?:admin\/)?service-order-operations\/([^/]+)(?:\/(confirm|resolve|settle-refund))?$/.exec(endpoint);
      if (opMatch) {
        const { q, data } = quotes.get(opMatch[1]) || {}; assert.ok(q, `known operation ${endpoint}`);
        if (method === "GET" && !opMatch[2]) { if (endpoint.startsWith("/admin/")) await hold("adminOperation"); return respond(q); }
        if (method === "POST" && opMatch[2] === "resolve") {
          assert.equal(body.upstreamChecked, true); assert.ok(body.evidence.length >= 10); assert.equal(Object.hasOwn(body, "refundedUnits"), false);
          assert.equal(body.outcome, "ACCEPTED");
          if (q.action === "CREATE") {
            assert.equal(body.externalOrderNo, "verified_business_id"); assert.equal(body.externalSubOrderNo, "9007199254740993");
            assert.deepEqual(Object.keys(body).sort(), ["evidence", "externalOrderNo", "externalSubOrderNo", "outcome", "upstreamChecked"]);
          } else assert.deepEqual(Object.keys(body).sort(), ["evidence", "outcome", "upstreamChecked"]);
          const record = records.find(o => o.id === q.orderId); record.pendingOperationId = null;
          record.status = q.action === "CANCEL" ? "REFUND_REVIEW" : "ACTIVE";
          record.actions = q.action === "CANCEL" ? [] : ["EDIT_PLAN", "CHANGE_TIME", "SCORE_INFO", "CANCEL"];
          q.state = "SUCCEEDED"; resolutions.push(body); return respond(q);
        }
        if (method === "POST" && ["confirm", "settle-refund"].includes(opMatch[2])) {
          assert.deepEqual(body, {}); confirmations.push(q.id);
          assert.equal(confirmations.filter(id => id === q.id).length, 1, "each confirmation must be sent once");
          await hold(`confirm:${q.action}`);
          if (q.action === "CREATE") {
            const record = createRecord(data.product, confirmMode === "unreadable"); q.orderId = record.id;
            if (confirmMode === "unreadable") { q.state = "UNKNOWN"; record.pendingOperationId = q.id; return respond(null, 0); }
          }
          const record = records.find(o => o.id === q.orderId);
          if (q.action === "CANCEL") { record.status = "REFUND_REVIEW"; record.actions = []; assert.equal(record.refundedAmount, "0.00"); }
          if (q.action === "SETTLE_REFUND") { assert.equal(opMatch[2], "settle-refund"); record.status = "REFUNDED"; record.refundedAmount = "4.62"; }
          q.state = "SUCCEEDED"; return respond(q);
        }
      }
      const orderMatch = /^\/(admin\/)?service-orders\/([^/]+)\/(logs|score-info|options|quotes|sync|audit|refund-quotes)$/.exec(endpoint);
      if (orderMatch) {
        const record = records.find(o => o.id === orderMatch[2]); assert.ok(record); const action = orderMatch[3];
        if (["logs", "score-info", "options", "sync"].includes(action)) assert.ok(!["REFUND_REVIEW", "REFUNDED", "CANCELLED"].includes(record.status), "never query a removed order");
        if (action === "audit" && method === "GET") return respond(audit(record));
        if (action === "options" && method === "GET") return respond({ suggested: { ...basePlan, account: "do-not-copy", password: "do-not-copy", distance: "9" }, choices: [], notice: "下单时安排" });
        if (action === "sync" && method === "POST") { assert.deepEqual(body, {}); return respond(record); }
        if (action === "score-info" && method === "GET") {
          assert.equal(url.search, "");
          if (scoreMode === "late") { await gates.get("score").promise; return respond({ text: "过期成绩文字不得出现" }); }
          if (scoreMode === "error") return respond(null, 0);
          return respond({ text: `<img src="https://must-not-load.invalid/image" onerror="window.scoreExecuted=true">\n成绩查询说明 ${record.accountLabel}` });
        }
        if (action === "logs" && method === "GET") {
          const n = Number(url.searchParams.get("page")); assert.ok([1, 2].includes(n));
          if (n === 2 && failPage2) { failPage2 = false; return respond(null, 0); }
          const items = n === 2 ? [{ id: "task_page2_9007199254740993", time: "2026-09-14 12:34:56", status: "待执行", editable: true, endTime: null }] : Array.from({ length: 20 }, (_, i) => ({
            id: `task_${i}`, time: "2026-09-13 08:09:10", status: i === 0 ? "待执行" : i === 2 ? "需要关注" : "跑步结束", editable: i === 0 || i === 2,
            endTime: i === 1 ? "2026-09-13 08:20:30" : null,
          }));
          return respond({ items, page: n, hasMore: n === 1 });
        }
        if (action === "quotes" && method === "POST") {
          assert.equal(body.quantity, 0); assert.notEqual(body.action, "SCORE_INFO", "scores are read-only GETs, never quotes");
          if (body.action === "EDIT_PLAN") exactFields(body.fields, { ...basePlan, startTime: "06:08:09" });
          else if (body.action === "CHANGE_TIME") exactFields(body.fields, { taskId: "task_page2_9007199254740993", page: "2", time: "2026-09-15 16:18:29" });
          else { assert.equal(body.action, "CANCEL"); assert.equal(body.fields, undefined); }
          await hold(`quote:${body.action}`); return respond(quote(body.action, record));
        }
        if (action === "refund-quotes" && method === "POST") {
          assert.equal(record.status, "REFUND_REVIEW"); assert.equal(body.refundedUnits, 2); assert.equal(body.orderVersion, record.version); assert.equal(body.upstreamChecked, true);
          assert.ok(body.evidence.length >= 10); return respond(quote("SETTLE_REFUND", record, { quantity: 2 }));
        }
      }
      unexpected.push(`${method} ${endpoint}`); return respond(null, 0);
    } catch (error) { unexpected.push(`${method} ${endpoint}: ${error.stack}`); return respond(null, 0); }
  });
  await page.goto(`${base}/__leidian`);
  await page.getByRole("heading", { name: products[0].title, exact: true }).waitFor();
  assert.equal(calls.filter(c => c.method === "POST").length, 0, "loading the storefront never submits business calls");

  // Stale rules, malformed rules, consent revocation and explicit adoption.
  await openProduct(0, "slow_uid"); const oldLookup = gate("lookup");
  await drawer().getByRole("button", { name: "查询跑区与规则" }).click();
  await waitForGate(oldLookup); await drawer().getByLabel("账号 UID", { exact: true }).fill("invalid_uid");
  await drawer().getByRole("button", { name: "查询跑区与规则" }).click();
  await page.getByText("跑区与规则信息不完整，请重新查询。", { exact: true }).waitFor();
  oldLookup.release(); await page.waitForTimeout(80);
  assert.equal(await page.getByText("过期学校不得出现", { exact: true }).count(), 0);
  assert.equal(await drawer().getByLabel("跑区", { exact: true }).count(), 0);
  await drawer().getByLabel("账号 UID", { exact: true }).fill("error_uid");
  await drawer().getByRole("button", { name: "查询跑区与规则" }).click();
  await page.getByText("模拟响应暂时不可用", { exact: true }).waitFor();
  await drawer().getByLabel("账号 UID", { exact: true }).fill("slow_uid");
  const revokedLookup = gate("lookup"); await drawer().getByRole("button", { name: "查询跑区与规则" }).click(); await waitForGate(revokedLookup);
  await setChecked(drawer().getByRole("checkbox", { name: "我有权使用此账号及信息，并授权提交" }), false);
  const revokedResponse = page.waitForResponse(r => r.url().endsWith("/services/121/lookup"));
  revokedLookup.release(); await afterResponse(revokedResponse);
  assert.equal(await page.getByText("过期学校不得出现", { exact: true }).count(), 0);
  assert.equal(await drawer().getByLabel("跑区", { exact: true }).count(), 0);
  await setChecked(drawer().getByRole("checkbox", { name: "我有权使用此账号及信息，并授权提交" }));
  await drawer().getByLabel("账号 UID", { exact: true }).fill("synthetic_uid_1"); await fillPlan(0);
  await setChecked(drawer().getByRole("checkbox", { name: "我有权使用此账号及信息，并授权提交" }), false);
  assert.equal(await drawer().getByLabel("跑区", { exact: true }).count(), 0);
  assert.equal(await drawer().getByRole("button", { name: "预览金额并下单" }).isEnabled(), false);
  await closeDrawer(drawer());
  for (let i = 0; i < 4; i++) {
    await openProduct(i); await fillPlan(i);
    assert.equal(await drawer().getByLabel(i === 3 ? "手机号" : "账号 UID", { exact: true }).inputValue(), i === 3 ? "13800138000" : `synthetic_uid_${i + 1}`);
    if (i === 0) { await noOverflow(); await shot("checkout-desktop.png"); }
    await drawer().getByRole("button", { name: "预览金额并下单" }).click();
    await page.locator(".quote-money").getByText("¥18.47", { exact: true }).waitFor();
    assert.match(await page.locator(".quote-charge-details").innerText(), /1\.84700000/);
    assert.equal(confirmations.length, i, "preview cannot confirm or debit");
    await confirm("确认并下单"); await card(records[i]).waitFor();
    assert.equal(records[i].refundedAmount, "0.00");
    if (i < 3) await navigate("/services");
  }
  assert.equal(calls.filter(c => c.endpoint === "/services/124/lookup").length, 0);
  await page.getByRole("button", { name: "返回全部订单" }).click(); await card(records[0]).waitFor();
  const primary = records[0];
  assert.equal(await card(primary).locator(".el-progress").count(), 0);
  await card(primary).getByText("已使用 / 已购次数", { exact: true }).waitFor();
  await card(primary).getByRole("button", { name: "核对次数与状态" }).click();
  await card(primary).getByRole("button", { name: "编辑执行安排" }).click();
  const planDialog = page.locator(".service-plan-editor");
  await planDialog.getByText(/下单时保存的安排/).waitFor();
  assert.equal(await planDialog.getByLabel("开始时间", { exact: true }).inputValue(), basePlan.startTime);
  assert.equal(await planDialog.getByLabel("结束时间", { exact: true }).inputValue(), basePlan.endTime);
  assert.equal(await planDialog.locator('input[type="password"]').count(), 0);
  await planDialog.getByLabel("开始时间", { exact: true }).fill("06:08:09");
  await planDialog.getByRole("button", { name: "预览修改" }).click(); await confirm();

  await card(primary).getByRole("button", { name: "执行记录", exact: true }).click();
  const logs = page.locator(".service-run-logs");
  await logs.getByText(/结束时间：2026-09-13 08:20:30/).waitFor();
  assert.equal(await logs.getByRole("button", { name: "修改时间", exact: true }).count(), 2);
  failPage2 = true; await logs.getByRole("button", { name: "下一页" }).click();
  await logs.getByText("暂时无法读取这一页记录，请重试。", { exact: true }).waitFor();
  assert.equal(await logs.getByRole("button", { name: "修改时间", exact: true }).count(), 0, "failed new page cannot display or edit old tasks");
  await logs.getByRole("button", { name: "重试读取" }).click();
  await logs.getByRole("button", { name: "修改时间", exact: true }).click();
  const task = page.locator(".leidian-task-editor");
  assert.equal(await task.getByLabel("新执行时间", { exact: true }).inputValue(), "12:34:56");
  await task.getByLabel("新执行日期（北京时间）", { exact: true }).fill("2026-09-15");
  await task.getByLabel("新执行时间", { exact: true }).fill("16:18:29");
  await shot("task-time-desktop.png"); await task.getByRole("button", { name: "预览时间修改" }).click(); await confirm();

  await card(primary).getByRole("button", { name: "成绩查询信息", exact: true }).click();
  const score = page.locator(".service-score-info"); await score.locator(".score-text").waitFor();
  assert.match(await score.locator(".score-text").textContent(), /<img/);
  assert.equal(await score.locator(".score-text img, .score-text a").count(), 0);
  assert.equal(await page.evaluate(() => window.scoreExecuted), undefined);
  scoreMode = "error"; await score.getByRole("button", { name: "重新查询" }).click();
  await score.getByText("暂时无法读取成绩查询信息，请稍后重试。", { exact: true }).waitFor();
  assert.equal(await score.locator(".score-text").count(), 0);
  scoreMode = "ok"; await score.getByRole("button", { name: "重试查询" }).click(); await score.locator(".score-text").waitFor();
  const oldScore = gate("score"); scoreMode = "late"; await score.getByRole("button", { name: "重新查询" }).click();
  await closeDrawer(score); scoreMode = "ok";
  await card(records[1]).getByRole("button", { name: "成绩查询信息", exact: true }).click(); await score.locator(".score-text").waitFor();
  oldScore.release(); await page.waitForTimeout(80);
  assert.match(await score.locator(".score-text").textContent(), /sy\*\*\*2/);
  assert.equal(await page.getByText("过期成绩文字不得出现", { exact: true }).count(), 0); await closeDrawer(score);

  // An unreadable confirmation is recovered only through GET of its durable ID.
  await navigate("/services"); confirmMode = "unreadable";
  await openProduct(1, "synthetic_pending"); await fillPlan(1);
  await drawer().getByRole("button", { name: "预览金额并下单" }).click();
  await page.getByRole("button", { name: "确认并下单", exact: true }).click();
  await page.getByRole("button", { name: "检查提交结果", exact: true }).waitFor();
  const pending = records.at(-1), pendingId = pending.pendingOperationId;
  await page.getByRole("button", { name: "检查提交结果", exact: true }).click();
  await page.getByText("待人工核对", { exact: true }).waitFor();
  await page.getByRole("button", { name: "稍后查看订单", exact: true }).click();
  assert.equal(await drawer().getByRole("button", { name: "预览金额并下单" }).isEnabled(), false);
  assert.equal(confirmations.filter(id => id === pendingId).length, 1);
  assert.equal(calls.filter(c => c.endpoint.endsWith(pendingId) && c.method === "GET").length, 1);
  confirmMode = "ok"; await navigate("/service-orders");

  // Cancellation never credits money, and removed records expose no read actions.
  await card(primary).getByRole("button", { name: "取消订单", exact: true }).click();
  await page.getByText(/这里只取消订单，不会退回余额/).waitFor();
  await page.locator(".quote-money").getByText("¥0.00", { exact: true }).waitFor(); await confirm();
  await card(primary).getByText("退款待核对", { exact: true }).waitFor();
  assert.equal(primary.refundedAmount, "0.00");
  for (const name of ["核对次数与状态", "执行记录", "成绩查询信息", "取消订单"])
    assert.equal(await card(primary).getByRole("button", { name, exact: true }).count(), 0);

  await navigate("/admin/service-orders");
  await card(primary).getByRole("button", { name: "核对资料与记录" }).click();
  const auditDrawer = page.locator(".el-drawer").filter({ hasText: "核对资料与审计记录" });
  await auditDrawer.getByText("9007199254740993", { exact: true }).waitFor(); await closeDrawer(auditDrawer);
  await card(pending).getByRole("button", { name: "核对处理结果" }).click();
  const resolve = page.locator(".service-resolution"); await setChecked(resolve.getByRole("radio", { name: "已受理", exact: true }));
  await resolve.getByLabel("已核实的服务订单号", { exact: true }).fill("verified_business_id");
  await resolve.locator("textarea").fill("已独立核实账号项目和两个独立订单编号");
  await setChecked(resolve.getByRole("checkbox"));
  assert.equal(await resolve.getByRole("button", { name: "确认并记入审计" }).isEnabled(), false);
  await resolve.getByLabel("已核实的订单记录编号", { exact: true }).fill("9007199254740993");
  await shot("double-id-recovery-desktop.png"); await resolve.getByRole("button", { name: "确认并记入审计" }).click(); await resolve.waitFor({ state: "hidden" });
  assert.equal(resolutions.length, 1);
  const cancelUnknown = quote("CANCEL", pending); cancelUnknown.state = "UNKNOWN"; pending.status = "CONFIRMING"; pending.pendingOperationId = cancelUnknown.id; pending.actions = [];
  await page.getByRole("button", { name: "刷新列表", exact: true }).click();
  await card(pending).getByRole("button", { name: "核对处理结果" }).click();
  await resolve.getByRole("radio", { name: "未受理，保留原订单", exact: true }).waitFor();
  await setChecked(resolve.getByRole("radio", { name: "已受理", exact: true }));
  await resolve.getByText(/取消受理后仍需单独核对退款/).waitFor();
  await resolve.locator("textarea").fill("已独立核实取消受理但尚未确认任何退款"); await setChecked(resolve.getByRole("checkbox"));
  await resolve.getByRole("button", { name: "确认并记入审计" }).click(); await resolve.waitFor({ state: "hidden" });
  assert.equal(pending.refundedAmount, "0.00"); assert.equal(resolutions.length, 2);
  await card(primary).getByRole("button", { name: "核对退款入账" }).click();
  const settlement = page.getByRole("dialog", { name: "核对主动退款" });
  await settlement.locator(".el-input-number input").fill("2"); await settlement.locator("textarea").fill("已核实剩余次数及原付款流水同意退回两次"); await setChecked(settlement.getByRole("checkbox"));
  await settlement.getByRole("button", { name: "预览退款金额" }).click(); assert.equal(primary.refundedAmount, "0.00");
  await confirm("确认退款入账"); assert.equal(primary.refundedAmount, "4.62");

  // Admin configuration offers the actual four projects, filtering mismatched IDs.
  await navigate("/admin/service-products"); await page.getByRole("button", { name: "上架服务商品", exact: true }).click();
  const admin = page.locator(".service-product-dialog"); await admin.locator(".el-select").first().click();
  await page.getByRole("option", { name: "演示运动服务 · 雷电", exact: true }).click();
  await admin.getByLabel("运动项目", { exact: true }).locator("xpath=ancestor::div[contains(@class, 'el-select__wrapper')][1]").click(); await page.getByRole("option", { name: "乐健体育", exact: true }).click();
  await admin.getByRole("button", { name: "读取目录", exact: true }).click();
  await admin.locator(".el-select").last().click();
  await page.getByRole("option", { name: new RegExp(products[3].title) }).click();
  assert.equal(await page.getByRole("option", { name: /不应出现/ }).count(), 0);
  const priceField = admin.locator(".el-form-item").filter({ hasText: "销售单价（元/次·公里" });
  await priceField.locator("input").fill("0.35"); await admin.locator(".el-switch").click(); assert.equal(await admin.getByRole("switch").getAttribute("aria-checked"), "true");
  await admin.getByRole("button", { name: "保存商品", exact: true }).click(); await admin.waitFor({ state: "hidden" }); assert.equal(saves.length, 1);
  await navigate("/providers"); await page.getByRole("button", { name: "添加接口", exact: true }).click();
  const provider = page.locator(".el-dialog:visible"); await provider.locator(".el-select").first().click();
  await page.getByRole("option", { name: "雷电（四种运动项目）", exact: true }).click();
  await provider.getByText(/不要添加 ldrun 或 api.php/).waitFor();
  assert.equal(await provider.locator(".el-form-item").filter({ has: page.locator(".el-form-item__label", { hasText: /^密码$/ }) }).count(), 0);
  await provider.locator(".el-dialog__headerbtn").click();

  // Mobile dark mode, 44px controls, no account/score persistence and logout race.
  await page.setViewportSize({ width: 375, height: 812 }); await page.evaluate(() => document.documentElement.classList.add("dark"));
  await navigate("/services"); await openProduct(2); await fillPlan(2); await noOverflow();
  for (const selector of [".el-input__wrapper", ".el-select__wrapper", ".el-checkbox", ".el-button"]) {
    const heights = await drawer().locator(selector).evaluateAll(elements => elements.filter(e => e.getBoundingClientRect().width > 0).map(e => e.getBoundingClientRect().height));
    assert.ok(heights.every(h => h >= 43.5), `${selector} has a short touch target: ${heights}`);
  }
  await shot("checkout-mobile-dark.png");
  const mobileLayout = await drawer().evaluate(el => {
    const rect = node => { const r = node.getBoundingClientRect(); return { top: r.top, bottom: r.bottom, left: r.left, right: r.right, height: r.height }; };
    return { viewport: { width: innerWidth, height: innerHeight, scrollY }, drawer: rect(el), overlay: rect(el.closest(".el-overlay")),
      footer: rect(el.querySelector(".el-drawer__footer")), action: rect(el.querySelector(".checkout-footer .el-button")),
      ancestorStyles: [el, el.parentElement, el.parentElement.parentElement].map(node => ({ tag: node.tagName, className: node.className, scrollTop: node.scrollTop, position: getComputedStyle(node).position, top: getComputedStyle(node).top, marginTop: getComputedStyle(node).marginTop, transform: getComputedStyle(node).transform })) };
  });
  writeFileSync(path.join(output, "mobile-layout.json"), JSON.stringify(mobileLayout, null, 2));
  assert.ok(mobileLayout.action.top >= 0 && mobileLayout.action.bottom <= mobileLayout.viewport.height + 1, "mobile checkout action must be fully inside the viewport");
  await drawer().locator(".el-drawer__body").evaluate(node => { node.scrollTop = 0; });
  await shot("checkout-account-mobile-dark.png");
  await closeDrawer(drawer());
  await navigate("/service-orders"); await card(records[2]).getByRole("button", { name: "成绩查询信息", exact: true }).click();
  await score.locator(".score-text").waitFor(); await noOverflow(); await shot("score-mobile-dark.png");
  const logoutScore = gate("score"); scoreMode = "late"; await score.getByRole("button", { name: "重新查询" }).click();
  await page.evaluate(() => window.logout()); await score.waitFor({ state: "hidden" }); logoutScore.release(); await page.waitForTimeout(80);
  assert.equal(await page.getByText("过期成绩文字不得出现", { exact: true }).count(), 0);
  const storage = await page.evaluate(() => JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]));
  for (const privateText of ["synthetic_uid", "synthetic_pending", "13800138000", "must-not-load.invalid", "never-merge-password", "过期成绩"])
    assert.equal(storage.includes(privateText), false, `${privateText} must not be persisted`);
  // Responses from an old identity cannot reopen a task quote or an admin recovery dialog.
  await page.setViewportSize({ width: 1440, height: 1080 });
  await page.evaluate(() => window.testLogin()); await navigate("/service-orders");
  await card(records[2]).getByRole("button", { name: "执行记录", exact: true }).click();
  await logs.getByRole("button", { name: "下一页" }).click();
  await logs.getByRole("button", { name: "修改时间", exact: true }).click();
  await task.getByLabel("新执行日期（北京时间）", { exact: true }).fill("2026-09-15");
  await task.getByLabel("新执行时间", { exact: true }).fill("16:18:29");
  const heldTask = gate("quote:CHANGE_TIME"); await task.getByRole("button", { name: "预览时间修改" }).click(); await waitForGate(heldTask);
  await page.evaluate(() => { window.logout(); window.testLogin(8); });
  await task.waitFor({ state: "hidden" });
  const taskResponse = page.waitForResponse(r => r.url().endsWith(`/service-orders/${records[2].id}/quotes`));
  heldTask.release(); await afterResponse(taskResponse);
  assert.equal(await page.locator(".service-quote-dialog:visible, .leidian-task-editor:visible").count(), 0);

  const recoveryRace = quote("CREATE", records[3], { quantity: 10 }); recoveryRace.state = "UNKNOWN";
  records[3].pendingOperationId = recoveryRace.id; records[3].status = "CONFIRMING"; records[3].actions = [];
  await page.evaluate(() => window.testLogin()); await navigate("/admin/service-orders");
  const heldRecovery = gate("adminOperation"); await card(records[3]).getByRole("button", { name: "核对处理结果" }).click(); await waitForGate(heldRecovery);
  await page.evaluate(() => window.testLogin(8));
  const recoveryResponse = page.waitForResponse(r => r.url().endsWith(`/admin/service-order-operations/${recoveryRace.id}`));
  heldRecovery.release(); await afterResponse(recoveryResponse);
  assert.equal(await page.locator(".service-resolution:visible").count(), 0); assert.equal(resolutions.length, 2);

  // A late cancellation preview is also private to the identity that requested it.
  await page.evaluate(() => window.testLogin()); await navigate("/service-orders");
  const heldCancel = gate("quote:CANCEL"), confirmationsBeforeRace = confirmations.length;
  await card(records[2]).getByRole("button", { name: "取消订单", exact: true }).click(); await waitForGate(heldCancel);
  await page.evaluate(() => { window.logout(); window.testLogin(8); });
  const cancelResponse = page.waitForResponse(r => r.url().endsWith(`/service-orders/${records[2].id}/quotes`));
  heldCancel.release(); await afterResponse(cancelResponse);
  assert.equal(await page.locator(".service-quote-dialog:visible").count(), 0, "an old identity's cancellation preview cannot reopen");
  assert.equal(confirmations.length, confirmationsBeforeRace, "discarding late previews must not dispatch an operation");
  assert.equal(records[2].status, "ACTIVE"); assert.equal(records[2].refundedAmount, "0.00");
  // An already dispatched receipt must not close or overwrite a new identity's quote.
  await page.evaluate(() => window.testLogin());
  await card(records[2]).getByRole("button", { name: "取消订单", exact: true }).click();
  const heldReceipt = gate("confirm:CANCEL");
  const oldReceiptId = (await page.locator(".quote-id").textContent()).replace("确认编号", "").trim();
  await page.getByRole("button", { name: "确认操作", exact: true }).click(); await waitForGate(heldReceipt);
  await page.evaluate(() => window.testLogin(8)); await page.locator(".service-quote-dialog").waitFor({ state: "hidden" });
  await card(records[1]).getByRole("button", { name: "取消订单", exact: true }).click();
  await page.locator(".quote-context").getByText(new RegExp(products[1].title)).waitFor();
  const newQuoteLabel = await page.locator(".quote-id").textContent();
  const receiptResponse = page.waitForResponse(r => r.url().endsWith(`/service-order-operations/${oldReceiptId}/confirm`));
  heldReceipt.release(); await afterResponse(receiptResponse);
  assert.equal(await page.locator(".quote-id").count(), 1, "a stale receipt cannot close a new quote");
  assert.equal(await page.locator(".quote-id").textContent(), newQuoteLabel);
  assert.equal(await page.getByRole("button", { name: "确认操作", exact: true }).isEnabled(), true);
  assert.equal(confirmations.filter(id => id === oldReceiptId).length, 1);
  assert.equal(records[1].status, "ACTIVE"); assert.equal(records[2].refundedAmount, "0.00");
  await page.getByRole("button", { name: "返回修改", exact: true }).click();
  await page.evaluate(() => window.logout());
  assert.deepEqual(errors, []); assert.deepEqual(unexpected, []);
  assert.equal(failedRequests.filter(r => !/ERR_ABORTED/.test(r.error || "")).length, 0);
  assert.deepEqual(consoleErrors.filter(e => !/ERR_ABORTED/.test(e.text)), []);
  const report = { assertions: "four-project checkout, strict lookup, immutable quotes, one dispatch, unknown GET recovery, plans, paged tasks, text-only score, cancellation/refunds, double IDs, configuration, mobile, privacy, consent and identity races", calls: calls.length, confirmations: confirmations.length, resolutionCount: resolutions.length, externalRequests: unexpected.length, errors };
  writeFileSync(path.join(output, "summary.json"), JSON.stringify(report, null, 2));
  console.log(`PASS: Leidian four-project commerce and safe recovery; ${calls.length} mocked requests; screenshots ${output}`);
} catch (error) {
  if (page) await shot("failure.png").catch(() => {});
  writeFileSync(path.join(output, "failure.json"), JSON.stringify({ error: String(error.stack), unexpected, errors, consoleErrors, failedRequests, calls }, null, 2));
  throw error;
} finally {
  for (const value of gates.values()) value.release();
  await browser?.close(); await server.close();
}
