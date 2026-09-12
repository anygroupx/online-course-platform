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
import {applyAuthSession,clearAuthSession,accessToken} from '/src/utils/authSession.js';
window.testLogin=(userId=7)=>applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600,sub:userId}))+'.signature',userId});window.testLogin();
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/services',component:Store},{path:'/service-orders',component:Orders},{path:'/admin/service-products',component:Admin},{path:'/admin/service-orders',component:Orders,meta:{serviceAdmin:true}},{path:'/providers',component:Providers}]});
window.navigate=(path)=>router.push(path);window.logout=clearAuthSession;window.switchIdentity=(userId)=>applyAuthSession({token:accessToken.value,userId});
await router.push('/services');await router.isReady();createApp({render:()=>h(RouterView)}).use(router).use(ElementPlus,{locale:zhCn}).mount('#app');
</script></body></html>`;
const server = await createTestServer({ logLevel: "error", server: { host: "127.0.0.1", port: 0 }, plugins: [{
  name: "jingyu-regression-fixture", configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
    if (!req.url?.startsWith("/__jingyu")) return next();
    res.setHeader("Content-Type", "text/html;charset=utf-8"); res.end(await vite.transformIndexHtml(req.url, html));
  }); },
}] });
const output = path.resolve(process.env.JINGYU_TEST_OUTPUT || "../.cache/native-service-ui/jingyu");
mkdirSync(output, { recursive: true });
const uid = "9007199254740993", password = "  keep password  ";
const projects = ["keep", "bdlp"], names = ["Keep 自由跑", "步道乐跑"];
const capabilities = ["LOOKUP", "CREATE", "SYNC", "PAUSE", "RESUME", "DELAY", "DELAY_TASK", "CHANGE_TIME", "REFUND"];
const products = projects.map((project, i) => ({ id: 81 + i, providerId: 8, providerType: "jingyu", project, remoteProductId: project,
  title: `鲸鱼 · ${names[i]}`, description: "查询账号，安排任务，查看执行记录。", unitPrice: i ? "0.130000" : "0.010000",
  priceUnit: i ? "元/次" : "元/次·公里", enabled: true, available: true, version: 0, capabilities }));
const fieldsFor = project => project === "keep" ? { account: "13800138000", password, zoneId: uid, minMinute: "3", maxMinute: "15" }
  : { account: uid, zoneId: uid, runType: "2" };
const lookupFor = (project, state = "VALID", schoolName = "示例运动大学") => ({ suggested: project === "keep" ? {} : {
  schoolName, minDistance: "2.0", authorizationState: state, authorizationType: "扫码授权", authorizedAt: "2026-09-11 08:09:10" },
  choices: [{ field: "zoneId", value: uid, label: "田径场" }], notice: "模拟账号查询，不会下单" });
const records = [], quotes = new Map(), calls = [], confirmations = [], dispatches = [], saves = [], resolutions = [], cases = [];
const unexpected = [], errors = [], consoleErrors = [], failedRequests = [], gates = new Map();
let browser, page, sequence = 1, confirmMode = "ok", lookupMode = "ok", logMode = "ok", denyResolution = false;
const note = "已独立核实订单编号、对应记录与资金处理结果";
const check = name => { cases.push(name); console.log(`PASS scenario: ${name}`); };
function gate(name) {
  let release, markStarted; const promise = new Promise(r => { release = r; }), started = new Promise(r => { markStarted = r; });
  const value = { promise, started, release, markStarted, used: false }; gates.set(name, value); return value;
}
async function hold(name) { const value = gates.get(name); if (value && !value.used) { value.used = true; value.markStarted(); await value.promise; } }
async function waitForGate(value) {
  let timer; try { await Promise.race([value.started, new Promise((_, reject) => { timer = setTimeout(() => reject(new Error("Mock request did not start")), 15000); })]); }
  finally { clearTimeout(timer); }
}
async function afterResponse(pending) {
  const response = await pending; await response.finished();
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
}
function refreshActions(record) {
  record.actions = record.pendingOperationId || ["REFUND_REVIEW", "REFUNDED", "COMPLETED", "CANCELLED"].includes(record.status) ? []
    : [record.status === "PAUSED" ? "RESUME" : "PAUSE", "DELAY", "DELAY_TASK", "CHANGE_TIME", "REFUND"];
}
function createRecord(p, data = {}, extra = {}) {
  const record = { id: `7d43a924-55ab-4c42-812a-${String(records.length + 1).padStart(12, "0")}`, providerType: "jingyu", project: p.project,
    title: p.title, accountLabel: p.project === "keep" ? "138****8000" : "9007***993", status: "ACTIVE", quantity: data.quantity ?? 2, completed: 0,
    distance: data.distance ?? "1.5", quantityUnit: "次", paidAmount: p.project === "keep" ? "0.06" : "0.26", refundedAmount: "0.00", version: 1,
    createTime: "2026-09-12T09:00:00", pendingOperationId: null, statusCheck: { checkedAt: "2026-09-12T09:00:00", delayed: false }, ...extra };
  refreshActions(record); records.push(record); return record;
}
function quote(action, record, data = {}) {
  const p = data.product || products.find(p => p.project === record?.project), id = `bfa54304-5104-4f5a-8888-${String(sequence++).padStart(12, "0")}`;
  const quantity = action === "REFUND" ? record.quantity - record.completed : data.quantity ?? 0;
  const q = { id, orderId: record?.id || null, action, state: "READY", title: record?.title || p.title, quantity, quantityUnit: "次",
    amount: action === "CREATE" ? p.project === "keep" ? "0.06" : "0.26" : action === "REFUND" ? "0.14" : action === "SETTLE_REFUND" ? "0.26" : "0.00",
    unitCharge: quantity ? p.project === "keep" ? "0.02000000" : "0.13000000" : null,
    amountLabel: ["REFUND", "SETTLE_REFUND"].includes(action) ? "预计退款上限（按实际核实数量结算）" : action === "CREATE" ? "本次余额扣款" : "无需额外扣款",
    expiresAt: "2026-09-12T09:05:00", errorCategory: null, distancePlan: null };
  quotes.set(id, { q, record, data }); return q;
}
function audit(record) { return { order: record, userId: 7, providerId: 8, externalOrderNo: `business_${record.project}`, externalSubOrderNo: uid, events: [] }; }
const drawer = () => page.locator(".jingyu-checkout");
const card = record => page.locator(".order-card").filter({ has: page.locator(".order-number", { hasText: record.id }) });
const previewButton = () => drawer().getByRole("button", { name: "预览金额并下单", exact: true });
const consent = () => drawer().getByRole("checkbox", { name: "我有权使用此账号及信息，并授权提交" });
const lookupButton = () => drawer().getByRole("button", { name: "查询账号与跑区", exact: true });
async function navigate(url) { await page.evaluate(url => window.navigate(url), url); }
async function shot(name) { await page.screenshot({ path: path.join(output, name), animations: "disabled", fullPage: false }); }
async function noOverflow() { assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), "viewport must not overflow horizontally"); }
async function setChecked(input, checked = true) {
  if (await input.isChecked() !== checked) await input.locator("xpath=ancestor::label[1]").click();
  assert.equal(await input.isChecked(), checked);
}
async function choose(select, label) {
  await select.locator("xpath=ancestor::div[contains(@class,'el-select__wrapper')][1]").click();
  await page.getByRole("option", { name: label, exact: true }).click();
}
async function closeDrawer(locator) { await locator.locator(".el-drawer__close-btn").click(); await locator.waitFor({ state: "hidden" }); }
async function confirm(name = "确认操作") {
  await page.getByRole("button", { name, exact: true }).evaluate(el => { el.click(); el.click(); });
  await page.locator(".service-quote-dialog").waitFor({ state: "hidden" });
}
async function openProduct(i, account = i ? uid : "13800138000") {
  await page.locator(".service-card").filter({ has: page.getByRole("heading", { name: products[i].title, exact: true }) }).getByRole("button", { name: "选择服务" }).click();
  await setChecked(consent()); await drawer().getByLabel(i ? "账号 UID" : "手机号", { exact: true }).fill(account);
  if (!i) await drawer().getByLabel("账号密码", { exact: true }).fill(password);
}
async function quantity(value) { const input = drawer().getByLabel("购买次数", { exact: true }); await input.fill(String(value)); await input.press("Tab"); }
async function fillPlan(i) {
  await lookupButton().click(); await drawer().getByLabel("跑区", { exact: true }).waitFor();
  assert.equal(await drawer().getByLabel("跑区", { exact: true }).inputValue(), "", "zone must not be silently selected");
  await choose(drawer().getByLabel("跑区", { exact: true }), "田径场");
  if (!i) {
    await choose(drawer().getByLabel("最快配速（分钟/公里）", { exact: true }), "3 分钟/公里");
    await choose(drawer().getByLabel("最慢配速（分钟/公里）", { exact: true }), "15 分钟/公里");
  } else await setChecked(drawer().getByRole("radio", { name: "自由跑", exact: true }));
  await quantity(i ? 2 : 3); await drawer().getByLabel("每次距离（公里）", { exact: true }).fill(i ? "8.5" : "1.5");
  await drawer().getByLabel("首个执行日期（北京时间）", { exact: true }).fill("2026-09-13");
  await drawer().getByRole("button", { name: "生成任务安排", exact: true }).click();
  await drawer().getByLabel("第 1 条任务时间", { exact: true }).fill("08:00:01");
}
try {
  await server.listen(); const base = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({ executablePath: process.env.BROWSER_PATH || (existsSync("/snap/bin/chromium") ? "/snap/bin/chromium" : undefined), headless: true, args: ["--no-sandbox", "--disable-dev-shm-usage"] });
  page = await browser.newPage({ viewport: { width: 1440, height: 1080 }, locale: "zh-CN", timezoneId: "America/Los_Angeles", reducedMotion: "reduce" });
  await page.clock.setFixedTime(new Date("2026-09-12T01:00:00.400Z")); page.setDefaultTimeout(15000); page.setDefaultNavigationTimeout(60000);
  page.on("pageerror", error => errors.push(error.message));
  page.on("console", message => { if (message.type() === "error") consoleErrors.push({ text: message.text(), url: message.location().url }); });
  page.on("requestfailed", request => failedRequests.push({ url: request.url(), error: request.failure()?.errorText }));
  await page.route("**/*", async route => {
    const req = route.request(), url = new URL(req.url());
    if (url.origin !== base) { unexpected.push(`external ${url.origin}`); return route.abort(); }
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const endpoint = url.pathname.slice(4), method = req.method(), body = JSON.parse(req.postData() || "{}");
    calls.push({ endpoint, method, body, query: Object.fromEntries(url.searchParams) });
    const respond = (data, code = 1, status = 200) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify({ code, data,
      message: status === 403 ? "缺少核对退款所需权限" : code === 1 ? "操作成功" : "模拟响应暂时不可用" }) });
    try {
      if (endpoint === "/services" && method === "GET") return respond({ records: products, total: 2 });
      if (endpoint === "/admin/api-providers" && method === "GET") return respond({ records: [{ id: 8, name: "演示运动服务", providerType: "jingyu", status: 1, verifiedAt: "2026-09-12T08:00:00", verified: true }], total: 1 });
      if (endpoint === "/admin/service-products" && method === "GET") return respond({ records: saves.length ? [products[1]] : [], total: saves.length });
      if (endpoint === "/admin/plugin-integrations/P08/providers/8/catalog" && method === "GET") {
        assert.equal(url.searchParams.get("project"), "bdlp");
        return respond([{ id: "bdlp", name: products[1].title, unitPrice: "0.1", priceUnit: "元/次" },
          { id: "keep", name: "错误项目不应出现", unitPrice: "0.1" }, { id: "yyd", name: "未支持项目不应出现", unitPrice: "0.1" }]);
      }
      if (endpoint === "/admin/service-products" && method === "POST") {
        assert.equal(body.providerId, 8); assert.equal(body.project, "bdlp"); assert.equal(body.remoteProductId, "bdlp");
        assert.equal(body.unitPrice, "0.13"); assert.equal(body.enabled, true); assert.equal(body.contractPrice, null);
        saves.push(body); return respond(products[1]);
      }
      const lookupMatch = /^\/services\/(8[12])\/lookup$/.exec(endpoint);
      if (lookupMatch && method === "POST") {
        const project = lookupMatch[1] === "81" ? "keep" : "bdlp";
        assert.deepEqual(Object.keys(body).sort(), project === "keep" ? ["account", "password"] : ["account"]);
        if (project === "keep") assert.equal(body.password, password);
        else assert.equal(typeof body.account, "string");
        const mode = lookupMode;
        if (mode === "late") { await hold("lookup"); return respond(lookupFor(project, "VALID", "迟到的学校不得出现")); }
        if (mode === "invalid") return respond({ ...lookupFor(project), choices: [{ field: "zoneId", value: Number(uid), label: "数字编号不安全" }] });
        if (mode === "error") return respond(null, 0);
        return respond(lookupFor(project, mode === "expired" ? "EXPIRED" : "VALID"));
      }
      const createMatch = /^\/services\/(8[12])\/quotes$/.exec(endpoint);
      if (createMatch && method === "POST") {
        const p = products.find(p => p.id === Number(createMatch[1])), i = projects.indexOf(p.project);
        assert.deepEqual(Object.keys(body).sort(), ["accountSessionId", "authorizedAccount", "distance", "fields", "quantity", "schedule", "taskTimes"]);
        assert.equal(body.authorizedAccount, true); assert.equal(body.accountSessionId, null); assert.equal(body.schedule, null);
        assert.equal(body.quantity, i ? 2 : 3); assert.equal(body.distance, i ? "8.5" : "1.5"); assert.deepEqual(body.fields, fieldsFor(p.project));
        assert.deepEqual(body.taskTimes, Array.from({ length: body.quantity }, (_, n) => `2026-09-${13 + n} 08:00:0${n ? 0 : 1}`));
        await hold("quote:CREATE"); return respond(quote("CREATE", null, { product: p, ...body }));
      }
      if (["/service-orders", "/admin/service-orders"].includes(endpoint) && method === "GET") {
        const id = url.searchParams.get("orderId"), type = url.searchParams.get("providerType");
        const filtered = records.filter(r => (!id || r.id === id) && (!type || type === r.providerType));
        return respond({ records: filtered, total: filtered.length, current: Number(url.searchParams.get("page") || 1), size: 20 });
      }
      const operation = /^\/(?:admin\/)?service-order-operations\/([^/]+)(?:\/(confirm|resolve|settle-refund))?$/.exec(endpoint);
      if (operation) {
        const saved = quotes.get(operation[1]); assert.ok(saved, `known operation ${endpoint}`); const { q, data } = saved;
        if (method === "GET" && !operation[2]) { if (endpoint.startsWith("/admin/")) await hold("adminOperation"); return respond(q); }
        if (method === "POST" && operation[2] === "resolve") {
          if (denyResolution) return respond(null, -1, 403);
          assert.equal(body.outcome, "ACCEPTED"); assert.equal(body.upstreamChecked, true); assert.ok(body.evidence.length >= 10);
          const extras = q.action === "CREATE" ? ["externalOrderNo", "externalSubOrderNo"] : q.action === "REFUND" ? ["refundedUnits"] : [];
          assert.deepEqual(Object.keys(body).sort(), ["outcome", "evidence", "upstreamChecked", ...extras].sort());
          if (q.action === "CREATE") { assert.equal(body.externalOrderNo, "verified_business_id"); assert.equal(body.externalSubOrderNo, uid); }
          if (q.action === "REFUND") { assert.equal(body.refundedUnits, 2); assert.ok(body.refundedUnits <= q.quantity); }
          await hold(`resolve:${q.action}`);
          const record = records.find(r => r.id === q.orderId); record.pendingOperationId = null;
          record.status = q.action === "REFUND" ? "REFUNDED" : q.action === "PAUSE" ? "PAUSED" : "ACTIVE";
          if (q.action === "REFUND") record.refundedAmount = "0.04";
          refreshActions(record); q.state = "SUCCEEDED"; resolutions.push(body); return respond(q);
        }
        if (method === "POST" && ["confirm", "settle-refund"].includes(operation[2])) {
          assert.deepEqual(body, {}); confirmations.push(q.id); assert.equal(confirmations.filter(id => id === q.id).length, 1, "one confirmation per operation");
          await hold(`confirm:${q.action}`);
          if (q.action === "CREATE" && confirmMode === "expired") { q.state = "EXPIRED"; return respond(q); }
          dispatches.push(q.id);
          if (q.action === "CREATE") { saved.record = createRecord(data.product, data); q.orderId = saved.record.id; }
          const record = records.find(r => r.id === q.orderId); assert.ok(record);
          if ((q.action === "CREATE" && confirmMode === "unknown") || q.action === "REFUND") {
            q.state = "UNKNOWN"; record.status = "CONFIRMING"; record.pendingOperationId = q.id; refreshActions(record); return respond(null, 0);
          }
          if (q.action === "PAUSE") record.status = "PAUSED";
          if (q.action === "RESUME") record.status = "ACTIVE";
          if (q.action === "SETTLE_REFUND") { assert.equal(operation[2], "settle-refund"); record.status = "REFUNDED"; record.refundedAmount = "0.26"; }
          record.version++; refreshActions(record); q.state = "SUCCEEDED"; return respond(q);
        }
      }
      const orderMatch = /^\/(admin\/)?service-orders\/([^/]+)\/(logs|quotes|sync|audit|refund-quotes)$/.exec(endpoint);
      if (orderMatch) {
        const record = records.find(r => r.id === orderMatch[2]); assert.ok(record); const action = orderMatch[3];
        if (action === "audit" && method === "GET") return respond(audit(record));
        if (action === "sync" && method === "POST") { assert.deepEqual(body, {}); return respond(record); }
        if (action === "logs" && method === "GET") {
          const n = Number(url.searchParams.get("page")); assert.ok(n >= 1 && n <= 19);
          const hasMore = record.quantity === 365 ? n < 19 : n < 2;
          const items = Array.from({ length: hasMore ? 20 : 5 }, (_, i) => ({ id: `task_${n}_${i}`, time: `2026-09-${13 + (n === 2 ? 1 : 0)} 12:34:56`,
            status: n === 1 && i === 1 ? "已退款" : n === 1 && i > 1 ? "已完成" : "待执行", editable: !(n === 1 && i > 0),
            endTime: n === 1 && i === 2 ? "2026-09-13 12:44:56" : null }));
          if (n === 2 && logMode === "error") { logMode = "ok"; return respond(null, 0); }
          if (n === 2 && logMode === "invalid") { logMode = "ok"; return respond({ page: 1, hasMore: false, items }); }
          return respond({ page: n, hasMore, items });
        }
        if (action === "quotes" && method === "POST") {
          assert.equal(body.quantity, 0); assert.ok(capabilities.includes(body.action));
          if (body.action === "CHANGE_TIME") assert.deepEqual(body.fields, { taskId: "task_2_0", page: "2", time: "2026-09-15 16:18:29" });
          else if (body.action === "DELAY_TASK") assert.deepEqual(body.fields, { taskId: "task_2_0", page: "2" });
          else assert.equal(body.fields, undefined);
          assert.ok(!record.pendingOperationId, "never preview another action for a pending order");
          await hold(`quote:${body.action}`); return respond(quote(body.action, record));
        }
        if (action === "refund-quotes" && method === "POST") {
          assert.equal(record.status, "REFUND_REVIEW"); assert.equal(body.refundedUnits, 2); assert.equal(body.orderVersion, record.version);
          assert.equal(body.upstreamChecked, true); assert.ok(body.evidence.length >= 10);
          assert.deepEqual(Object.keys(body).sort(), ["evidence", "orderVersion", "refundedUnits", "upstreamChecked"]);
          return respond(quote("SETTLE_REFUND", record, { quantity: 2 }));
        }
      }
      unexpected.push(`${method} ${endpoint}`); return respond(null, 0);
    } catch (error) { unexpected.push(`${method} ${endpoint}: ${error.stack}`); return respond(null, 0); }
  });
  await page.goto(`${base}/__jingyu`); await page.getByRole("heading", { name: products[0].title, exact: true }).waitFor();
  assert.equal(await page.locator(".service-card").count(), 2); assert.equal(calls.filter(c => c.method === "POST").length, 0);
  check("two-project inventory; no business requests on page load");

  await openProduct(1); await setChecked(consent(), false);
  assert.equal(await lookupButton().isEnabled(), false); assert.equal(await previewButton().isEnabled(), false);
  await setChecked(consent()); lookupMode = "invalid"; await lookupButton().click();
  await page.getByText("账号或跑区信息不完整，请重新查询。", { exact: true }).waitFor();
  assert.equal(await drawer().getByLabel("跑区", { exact: true }).count(), 0);
  lookupMode = "error"; await lookupButton().click(); await page.getByText("模拟响应暂时不可用", { exact: true }).waitFor();
  check("consent and malformed/error lookup responses block checkout");

  lookupMode = "late"; const staleLookup = gate("lookup"); await lookupButton().click(); await waitForGate(staleLookup);
  await drawer().getByLabel("账号 UID", { exact: true }).fill("9007199254740994");
  lookupMode = "expired"; await lookupButton().click(); await drawer().getByText("授权已失效", { exact: true }).waitFor();
  const staleLookupResponse = page.waitForResponse(r => r.url().endsWith("/services/82/lookup")); staleLookup.release(); await afterResponse(staleLookupResponse);
  assert.equal(await page.getByText("迟到的学校不得出现", { exact: true }).count(), 0); assert.equal(await previewButton().isEnabled(), false);
  check("late account results cannot overwrite a changed account or expired authorization");

  lookupMode = "late"; const revoked = gate("lookup"); await lookupButton().click(); await waitForGate(revoked); await setChecked(consent(), false);
  const revokedResponse = page.waitForResponse(r => r.url().endsWith("/services/82/lookup")); revoked.release(); await afterResponse(revokedResponse);
  assert.equal(await drawer().getByLabel("跑区", { exact: true }).count(), 0); assert.equal(await previewButton().isEnabled(), false);
  await closeDrawer(drawer()); lookupMode = "ok";
  check("revoking consent invalidates an in-flight lookup");

  await openProduct(0); await fillPlan(0);
  assert.equal(await drawer().getByLabel("账号密码", { exact: true }).inputValue(), password);
  await drawer().getByLabel("第 2 条任务日期", { exact: true }).fill("2026-09-13");
  await drawer().getByLabel("第 2 条任务时间", { exact: true }).fill("08:00:01");
  assert.equal(await previewButton().isEnabled(), false);
  await drawer().getByLabel("第 2 条任务日期", { exact: true }).fill("2026-09-14");
  await drawer().getByLabel("第 2 条任务时间", { exact: true }).fill("08:00:00");
  await drawer().getByLabel("第 1 条任务时间", { exact: true }).fill(""); assert.equal(await previewButton().isEnabled(), false);
  await drawer().getByLabel("第 1 条任务时间", { exact: true }).fill("08:00:01");
  await quantity(4); assert.equal(await drawer().locator(".task-row").count(), 3); assert.equal(await previewButton().isEnabled(), false);
  await quantity(3); assert.equal(await previewButton().isEnabled(), true);
  await noOverflow(); await shot("keep-checkout-desktop.png");
  check("explicit per-task dates preserve seconds, reject duplicates/empty times and never silently resize");

  await previewButton().click(); await page.locator(".quote-money").getByText("¥0.06", { exact: true }).waitFor();
  assert.match(await page.locator(".quote-charge-details").innerText(), /0\.02000000/); assert.equal(dispatches.length, 0);
  await confirm("确认并下单"); await card(records[0]).waitFor(); assert.equal(confirmations.length, 1);
  check("Keep uses the frozen per-unit-rounded quote and dispatches once on double click");

  await navigate("/services"); await openProduct(1); await fillPlan(1);
  assert.equal(await drawer().getByLabel("账号 UID", { exact: true }).inputValue(), uid);
  assert.equal(await drawer().locator('input[type="password"]').count(), 0);
  assert.equal(await drawer().getByLabel("最快配速（分钟/公里）", { exact: true }).count(), 0);
  await previewButton().click(); await page.locator(".quote-money").getByText("¥0.26", { exact: true }).waitFor();
  await confirm("确认并下单"); await card(records[1]).waitFor();
  check("Bdlp preserves string UID, excludes passwords/account facts and charges by count, not distance");

  await navigate("/services"); await openProduct(0); await fillPlan(0);
  const previewsBefore = calls.filter(c => c.endpoint === "/services/81/quotes").length;
  await page.clock.setFixedTime(new Date("2026-09-13T00:00:02Z")); await previewButton().click();
  await page.getByText(/第 1 条任务：请选择未来一年内的任务时间/).first().waitFor();
  assert.equal(calls.filter(c => c.endpoint === "/services/81/quotes").length, previewsBefore);
  await page.clock.setFixedTime(new Date("2026-09-12T01:00:00.400Z"));
  await previewButton().click(); await page.locator(".service-quote-dialog").waitFor();
  const writesBeforeExpiry = dispatches.length, ordersBeforeExpiry = records.length;
  confirmMode = "expired"; await page.clock.setFixedTime(new Date("2026-09-12T01:06:00Z")); await confirm("确认并下单");
  assert.equal(dispatches.length, writesBeforeExpiry); assert.equal(records.length, ordersBeforeExpiry);
  assert.equal(await previewButton().isEnabled(), true); await closeDrawer(drawer());
  confirmMode = "ok"; await page.clock.setFixedTime(new Date("2026-09-12T01:00:00.400Z"));
  check("fresh-now task validation and expired confirmation produce no order or debit");

  await openProduct(0); await fillPlan(0); confirmMode = "unknown"; await previewButton().click();
  await page.getByRole("button", { name: "确认并下单", exact: true }).evaluate(el => { el.click(); el.click(); });
  await page.getByRole("button", { name: "检查提交结果", exact: true }).waitFor();
  const pending = records.at(-1), pendingId = pending.pendingOperationId;
  await page.locator(".service-quote-dialog").getByRole("button", { name: "检查提交结果", exact: true }).click(); await page.locator(".service-quote-dialog").getByText("待人工核对", { exact: true }).waitFor();
  await page.getByRole("button", { name: "稍后查看订单", exact: true }).click(); assert.equal(await previewButton().isEnabled(), false);
  assert.equal(confirmations.filter(id => id === pendingId).length, 1);
  assert.equal(calls.filter(c => c.endpoint.endsWith(pendingId) && c.method === "GET").length, 1);
  confirmMode = "ok"; await navigate("/service-orders");
  check("UNKNOWN retains the original operation ID, only reads recovery and blocks repeat checkout");

  const primary = createRecord(products[0], { quantity: 25 }, { title: "鲸鱼 · Keep 分页订单", paidAmount: "0.50", completed: 18 });
  const lastPageOrder = createRecord(products[0], { quantity: 365 }, { title: "鲸鱼 · 年度任务订单", paidAmount: "7.30", completed: 18 });
  await page.getByRole("button", { name: "刷新列表", exact: true }).click(); await card(primary).waitFor();
  await card(primary).getByText("完成 / 总次数", { exact: true }).waitFor(); assert.equal(await card(primary).locator(".el-progress").count(), 1);
  assert.equal(await card(pending).getByRole("button", { name: "暂停", exact: true }).count(), 0);
  await card(primary).getByRole("button", { name: "暂停", exact: true }).click(); await confirm();
  await card(primary).getByRole("button", { name: "恢复", exact: true }).click(); await confirm();
  await card(primary).getByRole("button", { name: "延期未完成任务", exact: true }).click(); await confirm();
  assert.equal(primary.status, "ACTIVE"); assert.equal(primary.refundedAmount, "0.00");
  check("order actions use advertised permissions; pause/resume/delay do not refund money");

  const logs = page.locator(".service-run-logs"), task = page.locator(".jingyu-task-editor");
  await card(primary).getByRole("button", { name: "执行记录", exact: true }).click();
  await logs.getByText(/结束时间：2026-09-13 12:44:56/).waitFor();
  assert.equal(await logs.getByRole("button", { name: "修改时间", exact: true }).count(), 1);
  await logs.getByText(/任务显示“已退款”不代表余额已到账/).waitFor();
  logMode = "error"; await logs.getByRole("button", { name: "下一页", exact: true }).click();
  await logs.getByText("暂时无法读取这一页记录，请重试。", { exact: true }).waitFor();
  assert.equal(await logs.getByRole("button", { name: "修改时间", exact: true }).count(), 0);
  logMode = "invalid"; await logs.getByRole("button", { name: "重试读取", exact: true }).click();
  await logs.getByText("暂时无法读取这一页记录，请重试。", { exact: true }).waitFor();
  assert.equal(await logs.getByRole("button", { name: "修改时间", exact: true }).count(), 0);
  await logs.getByRole("button", { name: "重试读取", exact: true }).click();
  await logs.getByRole("button", { name: "修改时间", exact: true }).first().waitFor();
  check("task pages reject failed or mismatched responses without showing stale editable rows");

  await logs.getByRole("button", { name: "修改时间", exact: true }).first().click();
  assert.equal(await task.getByLabel("新执行时间", { exact: true }).inputValue(), "12:34:56");
  await task.getByLabel("新执行日期（北京时间）", { exact: true }).fill("2026-09-11");
  assert.equal(await task.getByRole("button", { name: "预览时间修改", exact: true }).isEnabled(), false);
  await task.getByLabel("新执行日期（北京时间）", { exact: true }).fill("2027-09-13");
  assert.equal(await task.getByRole("button", { name: "预览时间修改", exact: true }).isEnabled(), false);
  await task.getByLabel("新执行日期（北京时间）", { exact: true }).fill("2026-09-15");
  await task.getByLabel("新执行时间", { exact: true }).fill("16:18:29"); await shot("task-time-desktop.png");
  const taskPreviews = calls.filter(c => c.body.action === "CHANGE_TIME").length;
  await task.getByRole("button", { name: "预览时间修改", exact: true }).evaluate(el => { el.click(); el.click(); });
  await page.locator(".service-quote-dialog").waitFor(); assert.equal(calls.filter(c => c.body.action === "CHANGE_TIME").length, taskPreviews + 1);
  await confirm();
  check("task time editor preserves the exact second-page task ID, validates Beijing time and previews once");

  await card(primary).getByRole("button", { name: "执行记录", exact: true }).click(); await logs.getByRole("button", { name: "下一页", exact: true }).click();
  await logs.getByRole("button", { name: "延期此任务", exact: true }).first().click();
  await page.getByText(/仅延期所选任务，不会延期整笔订单/).waitFor(); await confirm();
  check("single-task delay carries only the chosen task ID and its original page");

  await card(primary).getByRole("button", { name: "执行记录", exact: true }).click(); await logs.getByRole("button", { name: "下一页", exact: true }).click();
  const delayedQuote = gate("quote:DELAY_TASK"); await logs.getByRole("button", { name: "延期此任务", exact: true }).first().click(); await waitForGate(delayedQuote);
  await closeDrawer(logs); await card(lastPageOrder).getByRole("button", { name: "执行记录", exact: true }).click();
  const delayedResponse = page.waitForResponse(r => r.url().endsWith(`/service-orders/${primary.id}/quotes`)); delayedQuote.release(); await afterResponse(delayedResponse);
  assert.equal(await page.locator(".service-quote-dialog:visible").count(), 0);
  for (let n = 2; n <= 19; n++) {
    await logs.getByRole("button", { name: "下一页", exact: true }).click(); await logs.getByText(new RegExp(`第 ${n} 页 ·`)).waitFor();
    await logs.getByRole("status").waitFor({ state: "hidden" });
  }
  assert.equal(await logs.locator(".el-timeline-item").count(), 5);
  assert.equal(await logs.getByRole("button", { name: "下一页", exact: true }).isEnabled(), false);
  assert.equal(calls.filter(c => c.endpoint.endsWith("/logs") && c.query.page === "20").length, 0); await closeDrawer(logs);
  check("closed/switched log views discard late quotes and pagination stops at page 19 (five rows)");

  await card(primary).getByRole("button", { name: "取消并申请退款", exact: true }).click();
  await page.getByText(/不会按预估次数自动退款/).waitFor(); await page.locator(".quote-money").getByText("¥0.14", { exact: true }).waitFor();
  await page.getByRole("button", { name: "确认取消申请", exact: true }).click();
  await page.locator(".service-quote-dialog").getByRole("button", { name: "检查提交结果", exact: true }).waitFor(); const refundId = primary.pendingOperationId;
  await page.locator(".service-quote-dialog").getByRole("button", { name: "检查提交结果", exact: true }).click(); await page.locator(".service-quote-dialog").getByText("待人工核对", { exact: true }).waitFor();
  await page.getByRole("button", { name: "稍后查看订单", exact: true }).click();
  assert.equal(primary.refundedAmount, "0.00"); assert.equal(confirmations.filter(id => id === refundId).length, 1);
  check("refund confirmation is an application, not estimated credit; UNKNOWN is read-only");

  await navigate("/admin/service-orders"); await card(pending).getByRole("button", { name: "核对处理结果", exact: true }).click();
  const resolve = page.locator(".service-resolution"); await setChecked(resolve.getByRole("radio", { name: "已受理", exact: true }));
  await resolve.getByLabel("已核实的服务订单号", { exact: true }).fill("verified_business_id");
  await resolve.locator("textarea").fill(note); await setChecked(resolve.getByRole("checkbox"));
  assert.equal(await resolve.getByRole("button", { name: "确认并记入审计", exact: true }).isEnabled(), false);
  await resolve.getByLabel("已核实的订单记录编号", { exact: true }).fill(uid); await shot("double-id-recovery-desktop.png");
  denyResolution = true; const beforeDenied = resolutions.length;
  const deniedResponse = page.waitForResponse(r => r.url().endsWith(`/admin/service-order-operations/${pendingId}/resolve`));
  await resolve.getByRole("button", { name: "确认并记入审计", exact: true }).click(); await afterResponse(deniedResponse);
  await page.getByText("缺少核对退款所需权限", { exact: true }).waitFor();
  assert.equal(resolutions.length, beforeDenied); assert.equal(pending.pendingOperationId, pendingId); assert.equal(await resolve.isVisible(), true);
  denyResolution = false;
  await resolve.getByRole("button", { name: "确认并记入审计", exact: true }).evaluate(el => { el.click(); el.click(); }); await resolve.waitFor({ state: "hidden" });
  assert.equal(resolutions.length, beforeDenied + 1); assert.equal(pending.status, "ACTIVE");
  check("creation reconciliation requires both IDs; 403 neither mutates the order nor shows success");

  await card(primary).getByRole("button", { name: "核对处理结果", exact: true }).click();
  await setChecked(resolve.getByRole("radio", { name: "已受理", exact: true }));
  assert.equal(await resolve.getByLabel("已核实的服务订单号", { exact: true }).count(), 0);
  assert.equal(await resolve.getByLabel("已核实的订单记录编号", { exact: true }).count(), 0);
  assert.equal(await resolve.locator(".el-input-number input").inputValue(), "", "refund amount must not be inferred or prefilled");
  await resolve.locator("textarea").fill(note); await setChecked(resolve.getByRole("checkbox"));
  assert.equal(await resolve.getByRole("button", { name: "确认并记入审计", exact: true }).isEnabled(), false);
  await resolve.locator(".el-input-number input").fill("2"); await resolve.locator(".el-input-number input").press("Tab");
  await shot("refund-recovery-desktop.png");
  await resolve.getByRole("button", { name: "确认并记入审计", exact: true }).evaluate(el => { el.click(); el.click(); }); await resolve.waitFor({ state: "hidden" });
  assert.equal(primary.refundedAmount, "0.04"); assert.equal(primary.status, "REFUNDED");
  check("refund reconciliation requires an explicit exact count and never forwards replacement IDs");

  const pausedUnknown = quote("PAUSE", pending); pausedUnknown.state = "UNKNOWN"; pending.pendingOperationId = pausedUnknown.id;
  pending.status = "CONFIRMING"; refreshActions(pending); await page.getByRole("button", { name: "刷新列表", exact: true }).click();
  await card(pending).getByRole("button", { name: "核对处理结果", exact: true }).click();
  await setChecked(resolve.getByRole("radio", { name: "已受理", exact: true }));
  assert.equal(await resolve.locator(".el-input-number input").count(), 0);
  assert.equal(await resolve.getByLabel("已核实的订单记录编号", { exact: true }).count(), 0);
  await resolve.locator("textarea").fill(note); await setChecked(resolve.getByRole("checkbox"));
  await resolve.getByRole("button", { name: "确认并记入审计", exact: true }).click(); await resolve.waitFor({ state: "hidden" });
  assert.equal(pending.status, "PAUSED"); assert.equal(pending.refundedAmount, "0.00");
  check("noncreation reconciliation uses saved identity and carries no refund count");

  records[1].status = "REFUND_REVIEW"; refreshActions(records[1]); await page.getByRole("button", { name: "刷新列表", exact: true }).click();
  await card(records[1]).getByRole("button", { name: "核对退款入账", exact: true }).click();
  const settlement = page.locator(".service-settlement"); assert.equal(await settlement.locator(".el-input-number input").inputValue(), "");
  await settlement.locator("textarea").fill(note); await setChecked(settlement.getByRole("checkbox"));
  assert.equal(await settlement.getByRole("button", { name: "预览退款金额", exact: true }).isEnabled(), false);
  await settlement.locator(".el-input-number input").fill("2"); await settlement.locator(".el-input-number input").press("Tab");
  await settlement.getByRole("button", { name: "预览退款金额", exact: true }).click(); assert.equal(records[1].refundedAmount, "0.00");
  await confirm("确认退款入账"); assert.equal(records[1].refundedAmount, "0.26");
  check("separate REFUND_REVIEW settlement requires explicit count and finance confirmation");

  await navigate("/admin/service-products"); await page.getByRole("button", { name: "上架服务商品", exact: true }).click();
  const admin = page.locator(".service-product-dialog"); await admin.locator(".el-select").first().click();
  await page.getByRole("option", { name: "演示运动服务 · 鲸鱼", exact: true }).click();
  await admin.getByText(/单次费用先保留两位小数/).waitFor();
  await choose(admin.getByLabel("运动项目", { exact: true }), "步道乐跑"); await admin.getByRole("button", { name: "读取目录", exact: true }).click();
  await admin.locator(".el-select").last().click(); await page.getByRole("option", { name: new RegExp(products[1].title) }).click();
  assert.equal(await page.getByRole("option", { name: /不应出现/ }).count(), 0);
  await admin.locator(".el-form-item").filter({ hasText: "销售单价（元/次，" }).locator("input").fill("0.13");
  await admin.locator(".el-switch").click(); await admin.getByRole("button", { name: "保存商品", exact: true }).click();
  await admin.waitFor({ state: "hidden" }); assert.equal(saves.length, 1);
  await navigate("/providers"); await page.getByRole("button", { name: "添加接口", exact: true }).click();
  const provider = page.locator(".el-dialog:visible"); await provider.locator(".el-select").first().click();
  await page.getByRole("option", { name: "鲸鱼（Keep / 步道乐跑）", exact: true }).click();
  await provider.getByText(/不要添加 jingyu 或 api.php/).waitFor(); await provider.locator(".el-dialog__headerbtn").click();
  check("admin configuration and publishing only expose verified projects with correct price units");

  await page.setViewportSize({ width: 390, height: 844 }); await page.evaluate(() => document.documentElement.classList.add("dark"));
  await navigate("/services"); await openProduct(0); await fillPlan(0); await noOverflow();
  for (const selector of [".el-input__wrapper", ".el-select__wrapper", ".el-checkbox", ".el-button"]) {
    const heights = await drawer().locator(selector).evaluateAll(elements => elements.filter(el => el.getBoundingClientRect().width > 0).map(el => el.getBoundingClientRect().height));
    assert.ok(heights.every(height => height >= 43.5), `${selector} has short touch targets: ${heights}`);
  }
  await drawer().locator(".el-drawer__body").evaluate(el => { el.scrollTop = 0; }); await shot("keep-account-mobile-dark.png");
  await drawer().getByRole("heading", { name: "逐次任务安排", exact: true }).scrollIntoViewIfNeeded(); await shot("task-plan-mobile-dark.png");
  await closeDrawer(drawer()); await openProduct(1); await fillPlan(1); await noOverflow();
  await drawer().locator(".el-drawer__body").evaluate(el => { el.scrollTop = 0; }); await shot("bdlp-account-mobile-dark.png");
  await closeDrawer(drawer());
  check("390px dark-mode account and task screens fit the viewport with 44px touch targets");

  await openProduct(1); lookupMode = "late"; const logoutLookup = gate("lookup"); await lookupButton().click(); await waitForGate(logoutLookup);
  await page.evaluate(() => window.switchIdentity(8)); await drawer().waitFor({ state: "hidden" });
  const logoutResponse = page.waitForResponse(r => r.url().endsWith("/services/82/lookup")); logoutLookup.release(); await afterResponse(logoutResponse);
  assert.equal(await page.getByText("迟到的学校不得出现", { exact: true }).count(), 0); lookupMode = "ok";
  await page.evaluate(() => window.testLogin()); await openProduct(0);
  assert.equal(await drawer().getByLabel("手机号", { exact: true }).inputValue(), "13800138000");
  // A normal close followed by a fresh opening must start with blank credentials.
  await closeDrawer(drawer());
  await page.locator(".service-card").first().getByRole("button", { name: "选择服务" }).click();
  assert.equal(await drawer().getByLabel("手机号", { exact: true }).inputValue(), "");
  assert.equal(await drawer().getByLabel("账号密码", { exact: true }).inputValue(), ""); await closeDrawer(drawer());
  check("same-token identity changes invalidate private lookup state; closing checkout clears credentials");

  await page.setViewportSize({ width: 1440, height: 1080 }); await navigate("/service-orders");
  await card(lastPageOrder).getByRole("button", { name: "执行记录", exact: true }).click(); await logs.getByRole("button", { name: "下一页", exact: true }).click();
  await logs.getByRole("button", { name: "修改时间", exact: true }).first().click();
  await task.getByLabel("新执行日期（北京时间）", { exact: true }).fill("2026-09-15"); await task.getByLabel("新执行时间", { exact: true }).fill("16:18:29");
  const heldTask = gate("quote:CHANGE_TIME"); await task.getByRole("button", { name: "预览时间修改", exact: true }).click(); await waitForGate(heldTask);
  await page.evaluate(() => window.switchIdentity(8)); await task.waitFor({ state: "hidden" });
  const taskResponse = page.waitForResponse(r => r.url().endsWith(`/service-orders/${lastPageOrder.id}/quotes`)); heldTask.release(); await afterResponse(taskResponse);
  assert.equal(await page.locator(".service-quote-dialog:visible, .jingyu-task-editor:visible").count(), 0);
  check("late task previews cannot reopen after identity changes");

  const recoveryRace = quote("CREATE", records[0], { quantity: 3 }); recoveryRace.state = "UNKNOWN";
  records[0].pendingOperationId = recoveryRace.id; records[0].status = "CONFIRMING"; refreshActions(records[0]);
  await page.evaluate(() => window.testLogin()); await navigate("/admin/service-orders");
  const heldRecovery = gate("adminOperation"); await card(records[0]).getByRole("button", { name: "核对处理结果", exact: true }).click(); await waitForGate(heldRecovery);
  await page.evaluate(() => window.switchIdentity(8));
  const recoveryResponse = page.waitForResponse(r => r.url().endsWith(`/admin/service-order-operations/${recoveryRace.id}`)); heldRecovery.release(); await afterResponse(recoveryResponse);
  assert.equal(await resolve.isVisible(), false);
  await page.evaluate(() => window.logout());
  const storage = await page.evaluate(() => JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]));
  for (const secret of [uid, password, "13800138000", "迟到的学校"]) assert.equal(storage.includes(secret), false, "account data must not persist");
  check("late admin reads cannot reopen a prior identity's dialog; private account data is not persisted");

  assert.deepEqual(errors, []); assert.deepEqual(unexpected, []);
  assert.equal(failedRequests.filter(r => !/ERR_ABORTED/.test(r.error || "")).length, 0);
  assert.deepEqual(consoleErrors.filter(e => !/ERR_ABORTED/.test(e.text) && !(e.url.endsWith(`/admin/service-order-operations/${pendingId}/resolve`) && /403/.test(e.text))), []);
  const report = { cases, caseCount: cases.length, mockedRequests: calls.length, confirmationAttempts: confirmations.length,
    syntheticDispatches: dispatches.length, resolutions: resolutions.length, unexpected, errors, realBusinessCalls: 0 };
  writeFileSync(path.join(output, "summary.json"), JSON.stringify(report, null, 2));
  console.log(`PASS: Jingyu two-project user flows; ${cases.length} scenarios; ${calls.length} mocked requests; screenshots ${output}`);
} catch (error) {
  if (page) await shot("failure.png").catch(() => {});
  writeFileSync(path.join(output, "failure.json"), JSON.stringify({ error: String(error.stack), cases, unexpected, errors, consoleErrors, failedRequests, calls }, null, 2));
  throw error;
} finally {
  for (const value of gates.values()) value.release();
  await browser?.close(); await server.close();
}
