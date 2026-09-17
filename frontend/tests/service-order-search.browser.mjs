import assert from "node:assert/strict";
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";
import { chromium } from "playwright";
import { createTestServer } from "./fixtures/test-server.mjs";

const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;padding:20px"><div id="app"></div><script type="module">
import {createApp,h} from 'vue';import {createRouter,createMemoryHistory,RouterView} from 'vue-router';import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';
import Orders from '/src/views/ServiceOrders.vue';import {applyAuthSession,clearAuthSession,sessionUserInfo} from '/src/utils/authSession.js';
// Match LoginResponse: the public account identifier is uid, not the internal numeric userId.
const session=(userId,permissions=[])=>applyAuthSession({token:'test.'+btoa(JSON.stringify({userId,permissions,exp:Date.now()/1000+3600}))+'.signature',uid:'20000000-0000-4000-8000-'+String(userId).padStart(12,'0'),role:'USER',isAdmin:false,permissions});session(7);
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/service-orders',component:Orders},{path:'/admin/service-orders',component:Orders,meta:{serviceAdmin:true}}]});
await router.push('/service-orders');await router.isReady();window.__ordersFixture={go:(p)=>router.push(p),query:()=>router.currentRoute.value.query,session,logout:clearAuthSession,rotate:()=>applyAuthSession({...sessionUserInfo.value,balance:'8.00',nickname:'更新的昵称',token:'test.'+btoa(JSON.stringify({userId:7,exp:Date.now()/1000+7200}))+'.rotated'})};
createApp({render:()=>h(RouterView,null,{default:({Component,route})=>Component?h(Component,{key:route.path}):null})}).use(router).use(ElementPlus).mount('#app');
</script></body></html>`;
const server = await createTestServer({ logLevel: "error", plugins: [{ name: "order-search-fixture", configureServer(vite) {
  vite.middlewares.use(async (req, res, next) => {
    if (!req.url?.startsWith("/__order_search")) return next();
    res.setHeader("Content-Type", "text/html;charset=utf-8"); res.end(await vite.transformIndexHtml(req.url, html));
  });
} }] });
const orderId = (n) => `10000000-0000-0000-0000-${String(n).padStart(12, "0")}`;
const rows = Array.from({ length: 45 }, (_, i) => ({
  ownerId: 7, id: orderId(i + 1), title: i === 29 ? "字符%_!服务" : `晨跑服务 ${i + 1}`, accountLabel: `13***${String(i + 1).padStart(2, "0")}`,
  providerType: i % 2 ? "flash" : "jiguang", project: "default", status: i % 4 === 3 ? "PAUSED" : "ACTIVE",
  quantity: 5, completed: i % 2 ? null : 1, distance: "2.00", paidAmount: "2.50", refundedAmount: "0.00", pendingOperationId: null,
  version: 1, createTime: `2026-09-${i % 3 ? "12" : "11"}T10:00:00`, actions: [], quantityUnit: "次",
}));
rows.push({ ...rows[0], id: orderId(46), ownerId: 8, title: "用户八的服务", accountLabel: "20***08" });
const reads = [], writes = [], unexpected = [], errors = [], cases = [], releases = [];
let browser, page, base, failSecond = false, malformedNext = false, heldKeyword = "", releaseHeld;
const output = path.resolve(process.env.ORDER_SEARCH_TEST_OUTPUT || "../.cache/native-service-ui/order-search");
mkdirSync(output, { recursive: true });
const until = async (condition) => { const end = Date.now() + 30000; while (!condition()) { if (Date.now() > end) throw new Error("Fixture request did not arrive"); await new Promise((r) => setTimeout(r, 25)); } };
function bodyFor(query, user) {
  const q = query.keyword || "";
  let found = rows.filter((r) => (query.administrative || r.ownerId === user.userId)
    && (!query.ownerId || String(r.ownerId) === query.ownerId)
    && (!query.orderId || r.id === query.orderId)
    && (!query.providerType || r.providerType === query.providerType)
    && (!query.status || r.status === query.status)
    && (!query.createdFrom || r.createTime.slice(0, 10) >= query.createdFrom)
    && (!query.createdTo || r.createTime.slice(0, 10) <= query.createdTo)
    && (!q || [r.id, r.title, r.accountLabel].some((v) => v.includes(q))));
  found = found.sort((a, b) => b.id.localeCompare(a.id));
  return { records: found.slice((Number(query.page) - 1) * 20, Number(query.page) * 20).map(({ ownerId: _owner, ...row }) => row),
    total: found.length, current: Number(query.page), size: 20 };
}
try {
  await server.listen(); base = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({ executablePath: process.env.BROWSER_PATH || (existsSync("/snap/bin/chromium") ? "/snap/bin/chromium" : undefined),
    headless: true, args: ["--no-sandbox", "--disable-dev-shm-usage"] });
  page = await browser.newPage({ viewport: { width: 1440, height: 1080 }, timezoneId: "America/Los_Angeles" });
  page.setDefaultTimeout(30000); page.on("pageerror", (e) => errors.push(e.message));
  await page.route("**/*", async (route) => {
    const request = route.request(), url = new URL(request.url());
    if (url.origin !== base) { unexpected.push(url.origin); return route.abort(); }
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const endpoint = url.pathname.slice(4), method = request.method();
    const reply = (data, status = 200) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify({ code: status === 200 ? 1 : -1, message: status === 200 ? "ok" : "测试请求失败", data }) });
    if (method !== "GET") { writes.push({ endpoint, method }); return reply(null, 503); }
    if (!["/service-orders", "/admin/service-orders"].includes(endpoint)) { unexpected.push(endpoint); return reply(null, 404); }
    const user = JSON.parse(Buffer.from(request.headers().authorization.split(".")[1], "base64").toString());
    const params = Object.fromEntries(url.searchParams);
    for (const key of Object.keys(params)) assert.ok(["page", "pageSize", "keyword", "providerType", "status", "createdFrom", "createdTo", "ownerId", "orderId"].includes(key));
    assert.equal(params.pageSize, "20");
    const query = { ...params, administrative: endpoint.startsWith("/admin/") };
    reads.push({ endpoint, params, userId: user.userId });
    if (query.administrative && !user.permissions.includes("api-provider:update")) return reply(null, 403);
    if (heldKeyword && params.keyword === heldKeyword) {
      await new Promise((resolve) => { releaseHeld = resolve; releases.push(resolve); });
      try { return await reply(bodyFor(query, user)); } catch { return; } // An explicitly aborted read has no UI consumer.
    }
    if (failSecond && params.page === "2") return reply(null, 503);
    if (malformedNext) { malformedNext = false; return reply({ ...bodyFor(query, user), current: 999 }); }
    return reply(bodyFor(query, user));
  });
  const settled = async () => page.waitForFunction(() => !document.querySelector(".search-status")?.textContent.includes("正在查询"));
  const requestBy = async (action) => {
    const response = page.waitForResponse((r) => r.url().startsWith(`${base}/api/`) && r.request().method() === "GET");
    await action(); await response; await settled();
  };
  await page.goto(`${base}/__order_search`);
  await page.waitForFunction(() => document.querySelectorAll(".order-card").length === 20);
  assert.equal(reads.length, 1); assert.deepEqual(reads[0].params, { page: "1", pageSize: "20" });
  const search = page.getByRole("textbox", { name: "搜索订单", exact: true });
  const submit = page.getByRole("button", { name: "查询订单", exact: true });
  const reset = page.getByRole("button", { name: "重置筛选", exact: true });
  const cards = page.locator(".order-card");
  await search.fill("字符%_!"); await page.waitForTimeout(150);
  assert.equal(reads.length, 1); assert.match(await page.locator(".filter-dirty").innerText(), /修改/);
  await requestBy(() => submit.click());
  assert.equal(await cards.count(), 1); assert.match(await cards.first().innerText(), /字符%_!服务/);
  assert.equal(reads.at(-1).params.keyword, "字符%_!");
  assert.match(await page.locator(".applied-filters").innerText(), /字符%_!/);
  assert.deepEqual(await page.evaluate(() => window.__ordersFixture.query()), {});
  cases.push("literal keyword and explicit draft submission");
  const readsBeforeRotation = reads.length;
  await page.evaluate(() => window.__ordersFixture.rotate());
  await page.waitForTimeout(150);
  assert.equal(reads.length, readsBeforeRotation, "same-account token renewal must not restart the list query");
  assert.equal(await search.inputValue(), "字符%_!");
  assert.match(await page.locator(".applied-filters").innerText(), /字符%_!/);
  assert.equal(await cards.count(), 1);
  cases.push("real public uid login contract and filters retained across same-family renewal");

  await requestBy(() => reset.click());
  await page.locator(".service-order-filters label").filter({ has: page.getByRole("combobox", { name: "服务类型", exact: true }) }).locator(".el-select__wrapper").click();
  await page.getByRole("option", { name: "闪电", exact: true }).click();
  await page.locator(".service-order-filters label").filter({ has: page.getByRole("combobox", { name: "订单状态", exact: true }) }).locator(".el-select__wrapper").click();
  await page.getByRole("option", { name: "已暂停", exact: true }).click();
  await page.getByLabel("创建日期起始", { exact: true }).fill("2026-09-12");
  await page.getByLabel("创建日期截止", { exact: true }).fill("2026-09-12");
  await requestBy(() => submit.click());
  assert.deepEqual(reads.at(-1).params, { providerType: "flash", status: "PAUSED", createdFrom: "2026-09-12", createdTo: "2026-09-12", page: "1", pageSize: "20" });
  assert.ok(await cards.count() > 0);
  for (const card of await cards.all()) { assert.match(await card.innerText(), /闪电/); assert.match(await card.innerText(), /已暂停/); assert.match(await card.innerText(), /2026-09-12/); }
  await page.screenshot({ path: `${output}/orders-filtered-desktop.png`, fullPage: false, animations: "disabled" });
  cases.push("combined service, displayed status and Beijing date filters");

  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => document.documentElement.classList.add("dark"));
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
  for (const control of await page.locator(".service-order-filters .el-input__wrapper, .service-order-filters .el-select__wrapper, .service-order-filters input[type=date], .filter-buttons button").all())
    assert.ok((await control.boundingBox()).height >= 44);
  await page.locator(".service-order-filters").screenshot({ path: `${output}/orders-filters-mobile-dark.png`, animations: "disabled" });
  await page.screenshot({ path: `${output}/orders-page-mobile-dark.png`, animations: "disabled" });
  cases.push("390px dark theme, overflow and 44px controls");
  await page.setViewportSize({ width: 1440, height: 1080 }); await page.evaluate(() => document.documentElement.classList.remove("dark"));

  await page.getByLabel("创建日期起始", { exact: true }).fill("2026-09-13");
  let count = reads.length; await submit.click();
  assert.match(await page.locator(".filter-validation").innerText(), /开始日期/); assert.equal(reads.length, count);
  await requestBy(() => reset.click());
  await search.dispatchEvent("compositionstart"); await search.fill("输入法确认");
  count = reads.length; await search.press("Enter"); await page.waitForTimeout(100); assert.equal(reads.length, count);
  await search.dispatchEvent("compositionend"); await requestBy(() => search.press("Enter"));
  assert.equal(await cards.count(), 0); assert.match(await page.locator(".el-empty").innerText(), /没有符合筛选条件/);
  assert.equal(await page.locator(".el-alert").count(), 0);
  cases.push("invalid date and IME Enter validation, distinct empty result");

  await requestBy(() => reset.click()); failSecond = true;
  await requestBy(() => page.locator(".el-pagination .btn-next").click());
  assert.equal(await cards.count(), 0); assert.equal(await page.locator(".el-empty").count(), 0);
  assert.match(await page.locator(".el-alert").innerText(), /重试查询/); assert.equal(reads.at(-1).params.page, "2");
  count = reads.length; await page.waitForTimeout(150); assert.equal(reads.length, count);
  await search.fill("未提交草稿"); assert.equal(await page.getByRole("button", { name: "重试查询", exact: true }).isDisabled(), true);
  await requestBy(() => page.getByRole("button", { name: "刷新列表", exact: true }).click());
  assert.deepEqual(reads.at(-1).params, { page: "2", pageSize: "20" });
  await search.fill(""); failSecond = false;
  await requestBy(() => page.getByRole("button", { name: "重试查询", exact: true }).click());
  assert.equal(await cards.count(), 20); assert.match(await page.locator(".search-status").innerText(), /第 2 页/);
  cases.push("failed pagination clears stale rows and retries applied page without submitting drafts");

  heldKeyword = "较早请求"; await search.fill(heldKeyword); await submit.click();
  await until(() => !!releaseHeld); assert.equal(await cards.count(), 0);
  await search.fill("字符%_!"); await requestBy(() => submit.click());
  releaseHeld(); heldKeyword = ""; releaseHeld = null; await page.waitForTimeout(150);
  assert.equal(await cards.count(), 1); assert.match(await cards.first().innerText(), /字符%_!服务/);
  malformedNext = true; await requestBy(() => page.getByRole("button", { name: "刷新列表", exact: true }).click());
  assert.equal(await cards.count(), 0); assert.match(await page.locator(".el-alert").innerText(), /列表不完整/);
  await requestBy(() => page.getByRole("button", { name: "重试查询", exact: true }).click());
  cases.push("aborted stale requests and malformed-page rejection");

  await requestBy(() => page.evaluate((id) => window.__ordersFixture.go(`/service-orders?focus=${id}`), orderId(1)));
  assert.equal(await cards.count(), 1); assert.equal(await page.locator(".order-card.focused").count(), 1);
  assert.deepEqual(reads.at(-1).params, { orderId: orderId(1), page: "1", pageSize: "20" });
  assert.equal(await search.inputValue(), "");
  await requestBy(() => page.evaluate((id) => window.__ordersFixture.go(`/service-orders?focus=${id}`), orderId(46)));
  assert.match(await page.locator(".el-empty").innerText(), /未找到可查看的指定订单/);
  count = reads.length; await page.evaluate(() => window.__ordersFixture.go("/service-orders?focus"));
  await page.waitForFunction(() => document.querySelector(".filter-validation")?.textContent.includes("定位信息"));
  assert.equal(reads.length, count); assert.equal(await cards.count(), 0);
  await requestBy(() => page.getByRole("button", { name: "返回全部订单", exact: true }).click());
  assert.equal(reads.length, count + 1); assert.equal(await cards.count(), 20);
  cases.push("exact purchased-order focus, foreign order privacy and invalid-focus recovery");

  heldKeyword = "旧账号请求"; await search.fill(heldKeyword); await submit.click(); await until(() => !!releaseHeld);
  await requestBy(() => page.evaluate(() => window.__ordersFixture.session(8)));
  releaseHeld(); releaseHeld = null; heldKeyword = ""; await page.waitForTimeout(150);
  assert.equal(await cards.count(), 1); assert.match(await cards.first().innerText(), /用户八的服务/); assert.equal(await search.inputValue(), "");
  await requestBy(() => page.evaluate(() => window.__ordersFixture.session(8, ["api-provider:update"])));
  await requestBy(() => page.evaluate(() => window.__ordersFixture.go("/admin/service-orders")));
  const owner = page.getByRole("textbox", { name: "所属用户编号", exact: true });
  await owner.fill("7"); await requestBy(() => submit.click()); assert.equal(reads.at(-1).params.ownerId, "7");
  assert.ok(!(await page.locator(".order-list").innerText()).includes("用户八的服务"));
  await owner.fill("9223372036854775807"); await requestBy(() => submit.click());
  assert.equal(reads.at(-1).params.ownerId, "9223372036854775807"); assert.equal(await cards.count(), 0);
  await owner.fill("9223372036854775808"); count = reads.length; await submit.click();
  assert.match(await page.locator(".filter-validation").innerText(), /有效的所属用户编号/); assert.equal(reads.length, count);
  await requestBy(() => reset.click());
  await requestBy(() => page.evaluate(() => window.__ordersFixture.session(8)));
  assert.equal(await cards.count(), 0); assert.match(await page.locator(".el-alert").innerText(), /查看权限/);
  cases.push("account switch, administrative owner query, Long precision and revoked permissions");

  assert.deepEqual(await page.evaluate(() => Object.keys(localStorage)), ["userInfo"]);
  assert.deepEqual(await page.evaluate(() => Object.keys(sessionStorage)), []);
  assert.deepEqual(await page.evaluate(() => window.__ordersFixture.query()), {});
  const content = await page.locator(".service-orders").innerText();
  assert.ok(!/上游|本平台|当前平台|对接|套娃/.test(content));
  count = reads.length; await page.evaluate(() => window.__ordersFixture.logout());
  await page.waitForFunction(() => document.querySelector(".search-status")?.textContent.includes("请登录"));
  assert.equal(await cards.count(), 0); assert.equal(reads.length, count); assert.equal(await page.locator(".service-order-filters").count(), 0);
  assert.deepEqual(await page.evaluate(() => Object.keys(localStorage)), []);
  assert.deepEqual(writes, []); assert.deepEqual(unexpected, []); assert.deepEqual(errors, []);
  cases.push("no search persistence, business writes, external calls, stale logout data or internal UI terminology");
  const result = { status: "passed", cases, readRequests: reads.length, businessWrites: writes.length, externalCalls: unexpected.length,
    screenshots: ["orders-filtered-desktop.png", "orders-filters-mobile-dark.png", "orders-page-mobile-dark.png"] };
  writeFileSync(`${output}/browser-evidence.json`, JSON.stringify(result, null, 2));
  console.log("Service order search browser regression passed", JSON.stringify(result));
} catch (error) {
  if (page && !page.isClosed()) await page.screenshot({ path: `${output}/failure.png`, fullPage: true }).catch(() => {});
  writeFileSync(`${output}/failure.json`, JSON.stringify({ message: error.message, completedCases: cases, reads, writes, unexpected, errors }, null, 2));
  throw error;
} finally {
  for (const release of releases) release();
  await browser?.close(); await server.close();
}
