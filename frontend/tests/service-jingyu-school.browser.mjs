import assert from "node:assert/strict";
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";
import { chromium } from "playwright";
import { createTestServer } from "./fixtures/test-server.mjs";

const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;padding:20px"><div id="app"></div><script type="module">
import {createApp,h} from 'vue';import {createRouter,createMemoryHistory,RouterView} from 'vue-router';import ElementPlus from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/element-overrides.scss';import '/src/styles/global.css';import '/src/styles/fluent-spatial.scss';import '/src/styles/responsive.scss';
import Store from '/src/views/ServiceStore.vue';import Orders from '/src/views/ServiceOrders.vue';import Admin from '/src/views/AdminServiceProducts.vue';
import {applyAuthSession,accessToken} from '/src/utils/authSession.js';
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600,sub:7}))+'.signature',userId:7});
window.switchIdentity=userId=>applyAuthSession({token:accessToken.value,userId});
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/services',component:Store},{path:'/service-orders',component:Orders},{path:'/admin/service-products',component:Admin}]});
window.navigate=path=>router.push(path);await router.push('/services');await router.isReady();createApp({render:()=>h(RouterView,null,{default:({Component,route})=>Component?h(Component,{key:route.path}):null})}).use(router).use(ElementPlus,{locale:zhCn}).mount('#app');
</script></body></html>`;
const server = await createTestServer({ logLevel: "error", server: { host: "127.0.0.1", port: 0 }, plugins: [{
  name: "jingyu-school-regression", configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
    if (!req.url?.startsWith("/__jingyu_school")) return next();
    res.setHeader("Content-Type", "text/html;charset=utf-8"); res.end(await vite.transformIndexHtml(req.url, html));
  }); },
}] });
const output = path.resolve(process.env.JINGYU_SCHOOL_TEST_OUTPUT || "../.cache/native-service-ui/jingyu-school");
mkdirSync(output, { recursive: true });
const schoolId = "9007199254740993", ruleId = "9007199254740995", password = "  exact +&= password  ", account = "001_test-25";
const schoolName = "示例运动学院", otherSchool = "另一所运动学院";
const product = { id: 83, providerId: 8, providerType: "jingyu", project: "yyd", remoteProductId: "yyd", title: "校园运动计划",
  description: "选择学校和跑步规则，安排每次任务。", unitPrice: "0.010000", priceUnit: "元/次·公里", enabled: true, available: true, version: 0,
  capabilities: ["LOOKUP", "CREATE", "SYNC", "PAUSE", "RESUME", "DELAY", "DELAY_TASK", "CHANGE_TIME", "REFUND"] };
const choices = [{ field: "runRuleId", value: ruleId, label: "东校区" }, { field: "runRuleId", value: "22", label: "西校区" }];
const rules = [{ id: ruleId, zoneId: "7", zoneName: "东校区", minDistance: "1.5" }, { id: "22", zoneId: "8", zoneName: "西校区", minDistance: "2" }];
const lookup = (id = schoolId, name = schoolName) => ({ suggested: { schoolId: id, schoolName: name }, choices, schoolRules: rules });
const calls = [], errors = [], unexpected = [], consoleErrors = [], cases = [], records = [], quotes = new Map(), saves = [];
let browser, page, base, sequence = 1, lookupMode = "ok", confirmMode = "ok", confirmations = 0, lookupGate;
const check = name => { cases.push(name); console.log(`PASS scenario: ${name}`); };
const drawer = () => page.locator(".jingyu-checkout");
const preview = () => drawer().getByRole("button", { name: "预览金额并下单", exact: true });
const lookupButton = () => drawer().getByRole("button", { name: "查询账号与规则", exact: true });
const navigate = url => page.evaluate(url => window.navigate(url), url);
const shot = name => page.screenshot({ path: path.join(output, name), animations: "disabled", fullPage: false });
function gate() { let release, start; const value = { promise: new Promise(r => { release = r; }), started: new Promise(r => { start = r; }), release: () => release(), start: () => start() }; return value; }
function quote(action = "CREATE", orderId = null) {
  const q = { id: `bfa54304-5104-4f5a-8888-${String(sequence++).padStart(12, "0")}`, orderId, action, state: "READY", title: product.title,
    quantity: action === "CREATE" ? 2 : 0, quantityUnit: "次", amount: action === "CREATE" ? "0.04" : "0.00", amountLabel: "本次余额扣款",
    unitCharge: action === "CREATE" ? "0.02000000" : null, expiresAt: "2026-09-12T09:05:00", errorCategory: null, distancePlan: null };
  quotes.set(q.id, q); return q;
}
async function open() {
  await page.locator(".service-card").filter({ has: page.getByRole("heading", { name: product.title, exact: true }) })
    .getByRole("button", { name: "选择服务", exact: true }).click();
  const consent = drawer().getByRole("checkbox", { name: "我有权使用此账号及信息，并授权提交", exact: true });
  if (!await consent.isChecked()) await consent.locator("xpath=ancestor::label[1]").click();
}
async function selectSchool(name = schoolName) {
  const change = drawer().getByRole("button", { name: "更换学校", exact: true });
  if (await change.count()) await change.click();
  await drawer().getByRole("textbox", { name: "学校关键词", exact: true }).fill("运动学院");
  await drawer().getByRole("button", { name: "查询学校", exact: true }).click();
  await drawer().locator(".school-option").filter({ hasText: name }).click();
  await drawer().getByText(`已选学校：${name}`, { exact: true }).waitFor();
}
async function fillAccount() {
  await drawer().getByLabel("学号", { exact: true }).fill(account);
  await drawer().getByLabel("账号密码", { exact: true }).fill(password);
}
async function fillPlan() {
  await selectSchool(); await fillAccount(); await lookupButton().click();
  await drawer().locator(".school-rules").waitFor();
  await drawer().locator(".rule-option").filter({ hasText: "西校区" }).click();
  await drawer().getByRole("button", { name: "采用最低距离", exact: true }).click();
  const quantity = drawer().getByLabel("购买次数", { exact: true }); await quantity.fill("2"); await quantity.press("Tab");
  await drawer().getByLabel("首个执行日期（北京时间）", { exact: true }).fill("2026-09-13");
  await drawer().getByRole("button", { name: "生成任务安排", exact: true }).click();
  await drawer().getByLabel("第 1 条任务时间", { exact: true }).fill("08:01:02");
  assert.equal(await preview().isEnabled(), true);
}
try {
  await server.listen(); base = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({ executablePath: process.env.BROWSER_PATH || (existsSync("/snap/bin/chromium") ? "/snap/bin/chromium" : undefined), headless: true,
    args: ["--no-sandbox", "--disable-dev-shm-usage"] });
  page = await browser.newPage({ viewport: { width: 1440, height: 1080 }, timezoneId: "America/Los_Angeles" });
  page.setDefaultTimeout(15000); page.setDefaultNavigationTimeout(60000);
  await page.clock.setFixedTime(new Date("2026-09-12T01:00:00.400Z"));
  page.on("pageerror", error => errors.push(error.message));
  page.on("console", message => { if (message.type() === "error") consoleErrors.push(message.text()); });
  await page.route("**/*", async route => {
    const req = route.request(), url = new URL(req.url());
    if (url.origin !== base) { unexpected.push(`external ${url.origin}`); return route.abort(); }
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const endpoint = url.pathname.slice(4), method = req.method(), body = JSON.parse(req.postData() || "{}");
    calls.push({ endpoint, method, body, query: Object.fromEntries(url.searchParams) });
    const respond = (data, code = 1) => route.fulfill({ contentType: "application/json", body: JSON.stringify({ code, data, message: code === 1 ? "操作成功" : "测试查询暂不可用" }) });
    try {
      if (endpoint === "/services" && method === "GET") return respond({ records: [product], total: 1 });
      if (endpoint === "/services/83/schools" && method === "GET") {
        assert.equal(url.searchParams.get("page"), "1"); assert.ok(url.searchParams.get("keyword").trim());
        return respond({ page: 1, pageSize: 20, hasMore: false, items: [{ id: schoolId, name: schoolName }, { id: "52", name: otherSchool }] });
      }
      if (endpoint === "/services/83/lookup" && method === "POST") {
        assert.deepEqual(Object.keys(body).sort(), ["account", "password", "schoolId", "schoolName"]);
        assert.equal(body.account, account); assert.equal(body.password, password); assert.equal(typeof body.schoolId, "string");
        const mode = lookupMode;
        if (mode === "late") { const pending = lookupGate; pending.start(); await pending.promise; }
        if (mode === "mismatch") return respond(lookup("999", "不匹配的学院"));
        if (mode === "malformed") return respond({ ...lookup(), schoolRules: [{ ...rules[0], id: 91 }] });
        if (mode === "error") return respond(null, 0);
        return respond(lookup(body.schoolId, body.schoolName));
      }
      if (endpoint === "/services/83/quotes" && method === "POST") {
        assert.deepEqual(body.fields, { schoolId, schoolName, account, password, runRuleId: "22" });
        assert.equal(body.quantity, 2); assert.equal(body.distance, "2"); assert.equal(body.authorizedAccount, true);
        assert.equal(body.accountSessionId, null); assert.equal(body.schedule, null);
        assert.deepEqual(body.taskTimes, ["2026-09-13 08:01:02", "2026-09-14 08:00:00"]);
        return respond(quote());
      }
      const operation = /^\/service-order-operations\/([^/]+)(\/confirm)?$/.exec(endpoint);
      if (operation) {
        const q = quotes.get(operation[1]); assert.ok(q);
        if (method === "GET") return respond(q);
        assert.equal(method, "POST"); assert.equal(operation[2], "/confirm"); assert.deepEqual(body, {}); confirmations++;
        if (q.action === "CREATE") {
          assert.equal(q.state, "READY");
          const record = { id: `7d43a924-55ab-4c42-812a-${String(records.length + 1).padStart(12, "0")}`, providerType: "jingyu", project: "yyd",
            title: product.title, accountLabel: "00***25", status: confirmMode === "ok" ? "ACTIVE" : "UNKNOWN", quantity: 2, completed: 0, distance: "2",
            quantityUnit: "次", paidAmount: "0.04", refundedAmount: "0.00", createTime: "2026-09-12T09:00:00", version: 1,
            pendingOperationId: confirmMode === "ok" ? null : q.id, actions: confirmMode === "ok" ? ["SYNC", "PAUSE", "CHANGE_TIME", "DELAY_TASK", "REFUND"] : [],
            statusCheck: { checkedAt: "2026-09-12T09:00:00", delayed: false } };
          records.push(record); q.orderId = record.id; q.state = confirmMode === "ok" ? "SUCCEEDED" : "UNKNOWN";
        } else { q.state = "SUCCEEDED"; }
        return respond(q);
      }
      if (endpoint === "/service-orders" && method === "GET") {
        const filtered = records.filter(record => !url.searchParams.get("orderId") || record.id === url.searchParams.get("orderId"));
        return respond({ records: filtered, total: filtered.length, current: Number(url.searchParams.get("page")), size: 20 });
      }
      if (/^\/service-orders\/[^/]+\/events$/.test(endpoint) && method === "GET") return respond([]);
      if (/^\/service-orders\/[^/]+\/logs$/.test(endpoint) && method === "GET") return respond({ page: 1, hasMore: false,
        items: [{ id: "school-task-1", time: "2026-09-13 08:01:02", status: "待执行", editable: true, endTime: null }] });
      if (/^\/service-orders\/[^/]+\/quotes$/.test(endpoint) && method === "POST") {
        assert.equal(body.action, "CHANGE_TIME"); assert.deepEqual(body.fields, { taskId: "school-task-1", page: "1", time: "2026-09-15 16:18:29" });
        return respond(quote("CHANGE_TIME", records[0].id));
      }
      if (endpoint === "/admin/api-providers" && method === "GET") return respond({ records: [{ id: 8, name: "测试运动服务", providerType: "jingyu", status: 1, verified: true, verifiedAt: "2026-09-12T08:00:00" }], total: 1 });
      if (endpoint === "/admin/service-products" && method === "GET") return respond({ records: saves.length ? [product] : [], total: saves.length });
      if (endpoint === "/admin/plugin-integrations/P08/providers/8/catalog" && method === "GET") {
        assert.equal(url.searchParams.get("project"), "yyd"); return respond([{ id: "yyd", name: "校园运动", unitPrice: "0.01", priceUnit: "元/次·公里" }]);
      }
      if (endpoint === "/admin/service-products" && method === "POST") {
        assert.equal(body.project, "yyd"); assert.equal(body.remoteProductId, "yyd"); assert.equal(body.unitPrice, "0.02");
        assert.equal(body.providerId, 8); assert.equal(body.enabled, true); saves.push(body); return respond(product);
      }
      unexpected.push(`${method} ${endpoint}`); return respond(null, 0);
    } catch (error) { errors.push(error.message); return respond(null, 0); }
  });

  await page.goto(`${base}/__jingyu_school`); await page.getByRole("heading", { name: product.title, exact: true }).waitFor();
  assert.equal(calls.length, 1); await open();
  assert.equal(await drawer().getByRole("button", { name: "查询学校", exact: true }).isEnabled(), false);
  assert.equal(await lookupButton().isEnabled(), false); assert.equal(calls.filter(c => c.method === "POST").length, 0);
  await selectSchool(); assert.equal(await drawer().locator(".service-school-search").count(), 0);
  await fillAccount(); assert.equal(await drawer().getByLabel("账号密码", { exact: true }).inputValue(), password);
  check("school search is explicit, requires a keyword, and collapses to the chosen full name");

  for (const mode of ["mismatch", "malformed"]) {
    lookupMode = mode; await lookupButton().click(); await page.getByText("学校或规则信息不完整，请重新查询。", { exact: true }).last().waitFor();
    assert.equal(await drawer().locator(".school-rules").count(), 0); assert.equal(await preview().isEnabled(), false);
  }
  lookupMode = "error"; await lookupButton().click(); await page.getByText("测试查询暂不可用", { exact: true }).waitFor();
  check("mismatched schools, malformed rule identities, and failed queries cannot enable checkout");

  lookupMode = "late"; lookupGate = gate(); await lookupButton().click(); await lookupGate.started;
  await selectSchool(otherSchool); lookupGate.release(); lookupMode = "ok";
  await page.waitForResponse(r => r.url().endsWith("/services/83/lookup"));
  assert.equal(await drawer().locator(".school-rules").count(), 0); assert.equal(await preview().isEnabled(), false);
  await selectSchool(); await lookupButton().click(); await drawer().locator(".school-rules").waitFor();
  assert.equal(await drawer().locator('.school-rules input:checked').count(), 0);
  assert.equal(await drawer().getByLabel("跑步类型", { exact: true }).count(), 0);
  assert.equal(await drawer().getByLabel("最快配速（分钟/公里）", { exact: true }).count(), 0);
  check("changing school invalidates a late account response and rules are never silently selected");

  await drawer().locator(".rule-option").filter({ hasText: "西校区" }).click();
  await drawer().getByLabel("每次距离（公里）", { exact: true }).fill("1.5");
  assert.equal(await preview().isEnabled(), false);
  await drawer().getByRole("button", { name: "采用最低距离", exact: true }).click();
  assert.equal(await drawer().getByLabel("每次距离（公里）", { exact: true }).inputValue(), "2");
  const quantity = drawer().getByLabel("购买次数", { exact: true }); await quantity.fill("2"); await quantity.press("Tab");
  await drawer().getByLabel("首个执行日期（北京时间）", { exact: true }).fill("2026-09-13");
  await drawer().getByRole("button", { name: "生成任务安排", exact: true }).click();
  await drawer().getByLabel("第 1 条任务时间", { exact: true }).fill("08:01:02");
  assert.equal(await preview().isEnabled(), true);
  await drawer().locator(".school-rules").scrollIntoViewIfNeeded(); await shot("school-rules-desktop.png");
  check("chosen rules enforce minimum distance, which changes only on an explicit adoption action");

  await preview().click(); await page.locator(".quote-money").getByText("¥0.04", { exact: true }).waitFor();
  assert.match(await page.locator(".quote-charge-details").innerText(), /0\.02000000/); assert.equal(confirmations, 0);
  await page.getByRole("button", { name: "确认并下单", exact: true }).evaluate(button => { button.click(); button.click(); });
  await page.locator(".order-card").waitFor(); assert.equal(confirmations, 1);
  assert.equal(records[0].project, "yyd"); assert.equal(records[0].paidAmount, "0.04");
  check("school checkout preserves long IDs and password spaces, freezes cent-rounded pricing, and confirms once");

  await navigate("/admin/service-products"); await page.getByRole("button", { name: "上架服务商品", exact: true }).click();
  const admin = page.locator(".service-product-dialog"); await admin.locator(".el-select").first().click();
  await page.getByRole("option", { name: "测试运动服务 · 鲸鱼", exact: true }).click();
  await admin.getByLabel("运动项目", { exact: true }).locator("xpath=ancestor::div[contains(@class,'el-select__wrapper')][1]").click();
  await page.getByRole("option", { name: "校园运动", exact: true }).click();
  await admin.getByRole("button", { name: "读取目录", exact: true }).click(); await admin.locator(".el-select").last().click();
  await page.getByRole("option", { name: /校园运动.*0\.01/ }).click();
  await admin.locator(".el-form-item").filter({ hasText: "销售单价（元/次·公里，" }).locator("input").fill("0.02");
  await admin.locator(".el-switch").click(); await admin.getByRole("button", { name: "保存商品", exact: true }).click();
  await admin.waitFor({ state: "hidden" }); assert.equal(saves.length, 1);
  check("administrators can publish the school project with its own catalogue and mileage price");

  await navigate("/services"); await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => document.documentElement.classList.add("dark")); await open(); await fillPlan();
  await drawer().locator(".school-rules").scrollIntoViewIfNeeded();
  const layout = await drawer().evaluate(el => ({ width: el.clientWidth, scroll: el.scrollWidth,
    rules: [...el.querySelectorAll(".rule-option")].map(row => row.getBoundingClientRect().height),
    controls: [...el.querySelectorAll(".jingyu-account-fields .el-button")].map(button => button.getBoundingClientRect().height) }));
  assert.ok(layout.scroll <= layout.width + 1, JSON.stringify(layout)); assert.ok(layout.rules.every(h => h >= 44), JSON.stringify(layout));
  assert.ok(layout.controls.every(h => h >= 44), JSON.stringify(layout)); await shot("school-rules-mobile-dark.png");
  await drawer().locator(".jingyu-task-plan").scrollIntoViewIfNeeded(); await shot("school-task-plan-mobile-dark.png");
  check("390px dark mode has no horizontal overflow and retains 44px rule and action targets");

  confirmMode = "unknown"; await preview().click(); await page.getByRole("button", { name: "确认并下单", exact: true }).click();
  await page.getByRole("button", { name: "检查提交结果", exact: true }).waitFor();
  assert.equal(confirmations, 2); await page.getByRole("button", { name: "检查提交结果", exact: true }).click();
  assert.equal(confirmations, 2); assert.equal(records[1].status, "UNKNOWN");
  await page.locator(".service-quote-dialog .el-dialog__headerbtn").click();
  assert.equal(await preview().isEnabled(), false);
  check("uncertain school orders only reread the original operation and block a second checkout");

  const oldPassword = await drawer().getByLabel("账号密码", { exact: true }).inputValue(); assert.equal(oldPassword, password);
  await page.evaluate(() => window.switchIdentity(9)); await drawer().waitFor({ state: "hidden" });
  const storage = await page.evaluate(() => JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]));
  for (const secret of [password, account, schoolId, ruleId]) assert.equal(storage.includes(secret), false);
  assert.deepEqual(errors, []); assert.deepEqual(unexpected, []);
  assert.deepEqual(consoleErrors.filter(text => !/ERR_ABORTED/.test(text)), []);
  check("same-token identity changes close the form and no school credentials are persisted");
  writeFileSync(path.join(output, "summary.json"), JSON.stringify({ cases, requests: calls.length, confirmations, realBusinessCalls: 0, layout, errors, unexpected }, null, 2));
  console.log(`PASS: Jingyu school workflow; ${cases.length} scenarios; ${calls.length} intercepted requests.`);
} catch (error) {
  await shot("failure.png").catch(() => {});
  writeFileSync(path.join(output, "failure.json"), JSON.stringify({ error: String(error.stack), cases, errors, unexpected, consoleErrors, calls }, null, 2));
  throw error;
} finally {
  lookupGate?.release(); await browser?.close(); await server.close();
}
