import assert from "node:assert/strict";
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";
import { chromium } from "playwright";
import { createTestServer } from "./fixtures/test-server.mjs";

const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;padding:24px"><div id="app"></div><script type="module">
import {createApp,h} from 'vue';import {createRouter,createMemoryHistory,RouterView} from 'vue-router';import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';
import Store from '/src/views/ServiceStore.vue';import {applyAuthSession} from '/src/utils/authSession.js';
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7});
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/services',component:Store},{path:'/service-orders',component:{render:()=>h('h1','模拟订单回执')}}]});
await router.push('/services');await router.isReady();createApp({render:()=>h(RouterView)}).use(router).use(ElementPlus).mount('#app');
</script></body></html>`;
const server = await createTestServer({ logLevel: "error", server: { port: 0 }, plugins: [{
  name: "school-selection-fixture", configureServer(vite) {
    vite.middlewares.use(async (req, res, next) => {
      if (!req.url?.startsWith("/__service_schools")) return next();
      res.setHeader("Content-Type", "text/html;charset=utf-8");
      res.end(await vite.transformIndexHtml(req.url, html));
    });
  },
}] });
const output = path.resolve(process.env.SCHOOL_TEST_OUTPUT || "../.cache/service-school-ui");
mkdirSync(output, { recursive: true });
const product = (id, project) => ({
  id, providerId: id + 10, providerType: project ? "sxdk_tw" : "jiguang", project: project || "default",
  remoteProductId: project || "1", title: project ? `实习 · ${project}` : `极光 · 计划 ${id}`,
  description: "本人授权的服务计划。", unitPrice: "0.25", priceUnit: project ? "元/服务日" : "元/公里",
  enabled: true, available: true, version: 1, capabilities: ["CREATE", "SYNC", "LOOKUP"],
});
const schoolPage = (page, prefix = "示例") => ({ page, pageSize: 20, hasMore: page === 1,
  items: page === 1 ? Array.from({ length: 20 }, (_, i) => ({ id: `S-${i}`, name: `${prefix}大学 ${i + 1}` }))
    : [{ id: "S-20", name: `${prefix}分页大学` }, { id: "S-21", name: `${prefix}第二学院` }],
});
const quoteId = "cdba6f4c-a002-40d4-b44e-468338917de1", orderId = "aa53e0c1-1f3b-4cc5-86bb-b6b4af004f9c";
const quote = { id: quoteId, orderId: null, state: "READY", action: "CREATE", title: "所选服务", quantity: 10,
  amount: "7.31", amountLabel: "本次余额扣款", expiresAt: "2099-01-01T00:00:00" };
const pending = [], contexts = [], evidence = [];
async function boundedWait(promise, label) {
  let timer;
  try {
    return await Promise.race([promise, new Promise((_, reject) => {
      timer = setTimeout(() => reject(new Error(`Timed out waiting for ${label}`)), 30000);
    })]);
  } finally {
    clearTimeout(timer);
  }
}
function hold() {
  let release, seen, done;
  const item = { gate: new Promise((r) => { release = r; }), release: () => release(),
    started: new Promise((r) => { seen = r; }), seen: () => seen(),
    completed: new Promise((r) => { done = r; }), done: () => done() };
  pending.push(item); return item;
}
let browser, base;
async function fixture(products, handler, options = {}) {
  const context = await browser.newContext({ viewport: options.viewport || { width: 1440, height: 1100 } });
  contexts.push(context);
  const page = await context.newPage(), calls = [], errors = [], unexpected = [];
  page.setDefaultTimeout(30000); page.setDefaultNavigationTimeout(60000);
  page.on("pageerror", (error) => errors.push(error.message));
  await context.route("**/*", async (route) => {
    const req = route.request(), url = new URL(req.url());
    if (url.origin !== base) { unexpected.push(url.href); return route.abort(); }
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const call = { endpoint: url.pathname.slice(4), method: req.method(), params: Object.fromEntries(url.searchParams), body: req.postDataJSON() };
    calls.push(call);
    const respond = (data, status = 200) => route.fulfill({ status, contentType: "application/json",
      body: JSON.stringify({ code: status === 200 ? 1 : -1, data, message: status === 200 ? "成功" : "暂时不可用" }) });
    try {
      if (call.endpoint === "/services" && call.method === "GET") return respond({ records: products, total: products.length });
      if (/^\/services\/\d+\/schools$/.test(call.endpoint) && call.method === "GET") {
        assert.deepEqual(Object.keys(call.params).sort(), ["keyword", "page"]);
        return await handler(call, respond);
      }
      if (call.endpoint.endsWith("/lookup") && call.method === "POST" && options.lookup) return await options.lookup(call, respond);
      if (call.endpoint.endsWith("/quotes") && call.method === "POST") {
        assert.equal(call.body.authorizedAccount, true); assert.equal(call.body.amount, undefined);
        return options.quote ? await options.quote(call, respond) : respond(quote);
      }
      if (call.endpoint === `/service-order-operations/${quoteId}/confirm` && call.method === "POST") {
        assert.deepEqual(call.body, {}); return respond({ ...quote, orderId, state: "SUCCEEDED" });
      }
      unexpected.push(`${call.method} ${call.endpoint}`); return respond(null, 404);
    } catch (error) {
      errors.push(error.message); await respond(null, 500).catch(() => {});
    }
  });
  await page.goto(`${base}/__service_schools`);
  return { page, calls, errors, unexpected, context };
}
const picker = (page) => page.getByRole("group", { name: "查找学校", exact: true });
const button = (page, name) => picker(page).getByRole("button", { name, exact: true });
const keywordInput = (page) => picker(page).getByRole("textbox", { name: "学校关键词" });
const schools = (calls) => calls.filter((c) => c.endpoint.endsWith("/schools"));
const posts = (calls) => calls.filter((c) => c.method === "POST");
const formSchool = (page) => page.getByPlaceholder("填写学校全称");
const preview = (page) => page.getByRole("button", { name: "预览金额并下单", exact: true });
const consent = (page) => page.getByText("我有权使用此账号及信息，并授权提交", { exact: true });
const selectedSchool = (page) => page.getByRole("status", { name: "已选学校", exact: true });
async function open(page, title) {
  await page.locator(".service-card").filter({ has: page.getByRole("heading", { name: title, exact: true }) })
    .getByRole("button", { name: "选择服务", exact: true }).click();
  await picker(page).waitFor();
}
async function search(page, keyword) { await keywordInput(page).fill(keyword); await button(page, "查询学校").click(); }
const shownPage = (page, number, count) => picker(page).getByText(`第 ${number} 页 · ${count} 所学校`, { exact: true }).waitFor();
async function healthy(f, name) {
  assert.deepEqual(f.errors, []); assert.deepEqual(f.unexpected, []);
  evidence.push({ name, schools: schools(f.calls).length, quotes: f.calls.filter((c) => c.endpoint.endsWith("/quotes")).length,
    confirmations: f.calls.filter((c) => c.endpoint.endsWith("/confirm")).length });
  await f.context.close();
}
try {
  await server.listen(); base = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({ executablePath: process.env.BROWSER_PATH || (existsSync("/snap/bin/chromium") ? "/snap/bin/chromium" : undefined),
    headless: true, args: ["--no-sandbox", "--disable-dev-shm-usage"] });
  let failPage = true, malformed = true;
  const old = hold(), closing = hold(), quoting = hold();
  const f = await fixture([product(1), product(2)], async (call, respond) => {
    const page = Number(call.params.page), keyword = call.params.keyword;
    if (keyword === "旧查询" || keyword === "关闭查询") {
      const gate = keyword === "旧查询" ? old : closing;
      gate.seen(); await gate.gate; await respond(schoolPage(page, "过期")).catch(() => {}); gate.done(); return;
    }
    if (keyword === "失败" && page === 2 && failPage) { failPage = false; return respond(null, 503); }
    if (keyword === "空") return respond({ page, pageSize: 20, hasMore: false, items: [] });
    if (keyword === "格式" && malformed) { malformed = false; return respond({ ...schoolPage(1), page: 2 }); }
    return respond(schoolPage(page, keyword === "新查询" ? "最新" : "示例"));
  }, { quote: async (call, respond) => {
    assert.deepEqual(call.body.fields, { schoolName: "手工完整大学", studentName: "测试姓名", studentAccount: "2026123", message: "" });
    assert.equal(call.body.quantity, 10); assert.equal(call.body.distance, "2");
    quoting.seen(); await quoting.gate; await respond(quote); quoting.done();
  } });
  const p = f.page;
  await open(p, product(1).title);
  assert.equal(schools(f.calls).length, 0, "opening checkout never auto-queries");
  await formSchool(p).fill("手工大学"); await search(p, ""); await shownPage(p, 1, 20);
  assert.equal(await button(p, "上一页").isDisabled(), true);
  await button(p, "下一页").click(); await shownPage(p, 2, 2);
  assert.equal(await formSchool(p).inputValue(), "手工大学");
  await button(p, "示例分页大学 选择").focus(); await button(p, "示例分页大学 选择").press("Enter");
  assert.equal(await formSchool(p).inputValue(), "示例分页大学");
  assert.equal(await button(p, "示例分页大学 已选择").getAttribute("aria-pressed"), "true");
  assert.equal(posts(f.calls).length, 0, "discovery and selection cannot quote or order");
  await p.screenshot({ path: path.join(output, "schools-desktop.png"), fullPage: true });
  await button(p, "上一页").click(); await shownPage(p, 1, 20);
  assert.equal(await formSchool(p).inputValue(), "示例分页大学");
  await search(p, "失败"); await shownPage(p, 1, 20); await button(p, "下一页").click();
  await picker(p).getByRole("alert").getByText(/仍显示第 1 页结果/).waitFor();
  const failedCount = schools(f.calls).length;
  await p.screenshot({ path: path.join(output, "schools-retry.png"), fullPage: true });
  assert.equal(schools(f.calls).length, failedCount, "failure must not trigger automatic retries");
  await button(p, "重试第 2 页").click(); await shownPage(p, 2, 2);
  assert.deepEqual(schools(f.calls).slice(-2).map((c) => c.params), [{ page: "2", keyword: "失败" }, { page: "2", keyword: "失败" }]);
  await search(p, "空"); await picker(p).getByText("未找到匹配学校，请更换关键词后查询。", { exact: true }).waitFor();
  assert.equal(await button(p, "下一页").isDisabled(), true);
  assert.equal(await formSchool(p).inputValue(), "示例分页大学");
  await search(p, "格式"); await picker(p).getByRole("alert").getByText(/学校列表格式有误/).waitFor();
  assert.equal(await picker(p).locator(".school-empty").count(), 0);
  await button(p, "重试第 1 页").click(); await shownPage(p, 1, 20);
  await search(p, "旧查询"); await boundedWait(old.started, "obsolete school query start");
  await search(p, "新查询"); await button(p, "最新大学 1 选择").waitFor();
  old.release(); await boundedWait(old.completed, "obsolete school query completion");
  assert.equal(await picker(p).getByText(/过期/).count(), 0);
  assert.equal(await formSchool(p).inputValue(), "示例分页大学");
  await keywordInput(p).fill("输入法查询");
  assert.equal(await picker(p).locator(".school-results").count(), 0);
  const beforeIme = schools(f.calls).length;
  await keywordInput(p).dispatchEvent("keydown", { key: "Enter", code: "Enter", isComposing: true });
  await p.evaluate(() => new Promise(requestAnimationFrame));
  assert.equal(schools(f.calls).length, beforeIme, "IME confirmation cannot submit an unfinished keyword");
  await keywordInput(p).press("Enter"); await shownPage(p, 1, 20);
  assert.deepEqual(schools(f.calls).at(-1).params, { page: "1", keyword: "输入法查询" });
  await search(p, "关闭查询"); await boundedWait(closing.started, "closed checkout query start");
  await p.locator(".el-drawer__close-btn:visible").click(); await picker(p).waitFor({ state: "hidden" });
  await open(p, product(2).title); closing.release(); await boundedWait(closing.completed, "closed checkout query completion");
  assert.equal(await keywordInput(p).inputValue(), ""); assert.equal(await formSchool(p).inputValue(), "");
  assert.equal(await picker(p).locator(".school-results").count(), 0);
  await p.getByPlaceholder("填写本人姓名").fill("测试姓名"); await p.getByPlaceholder("填写学号").fill("2026123");
  await consent(p).click(); await formSchool(p).fill("   "); assert.equal(await preview(p).isDisabled(), true);
  await formSchool(p).fill("手工完整大学"); const beforeManual = schools(f.calls).length;
  await preview(p).click(); await boundedWait(quoting.started, "price quote start");
  assert.equal(await button(p, "查询学校").isDisabled(), true); assert.equal(await keywordInput(p).isDisabled(), true);
  quoting.release(); await boundedWait(quoting.completed, "price quote completion"); await p.getByText("¥7.31", { exact: true }).waitFor();
  assert.equal(schools(f.calls).length, beforeManual);
  assert.equal(f.calls.filter((c) => c.endpoint.endsWith("/confirm")).length, 0);
  await p.getByRole("button", { name: "确认并下单", exact: true }).dblclick();
  await p.getByRole("heading", { name: "模拟订单回执", exact: true }).waitFor();
  assert.equal(f.calls.filter((c) => c.endpoint.endsWith("/confirm")).length, 1);
  await healthy(f, "jiguang-pagination-retry-races-manual-quote-confirmation");

  for (const project of ["xxy", "hzj", "xxt"]) {
    const delayed = hold(); let firstLookup = project === "xxy", cleared = false;
    const f = await fixture([product(3, project)], async (call, respond) => respond(schoolPage(Number(call.params.page), "实习")), {
      lookup: async (call, respond) => {
        assert.equal(call.body.schoolId, firstLookup ? "S-20" : "S-21");
        assert.equal(call.body.school, firstLookup ? "实习分页大学" : "实习第二学院");
        assert.equal(call.body.schoolName, call.body.school);
        assert.equal(call.body.keyword, undefined); assert.equal(call.body.url, undefined);
        const suggested = { ...call.body, name: "测试实习生", address: "示例实习地址", lat: "30.1", lng: "110.2" };
        if (firstLookup) { firstLookup = false; delayed.seen(); await delayed.gate; }
        await respond({ suggested, choices: [], notice: "资料已读取" }); delayed.done();
      }, quote: async (call, respond) => {
        assert.equal(call.body.fields.schoolId, cleared ? "" : "S-21");
        assert.equal(call.body.fields.school, cleared ? "" : "实习第二学院");
        assert.equal(call.body.fields.schoolName, call.body.fields.school);
        assert.equal(call.body.fields.keyword, undefined); assert.equal(call.body.fields.url, undefined);
        assert.equal(call.body.quantity, 0); assert.equal(call.body.distance, null);
        return respond({ ...quote, quantity: 1, quantityUnit: "天", amount: "0.25" });
      },
    });
    const page = f.page; await open(page, product(3, project).title);
    assert.equal(await button(page, "查询学校").isDisabled(), true);
    await page.getByLabel("实习账号", { exact: true }).fill("test-student");
    await page.getByLabel("实习账号密码", { exact: true }).fill("offline-fixture-password"); await consent(page).click();
    const lookup = page.getByRole("button", { name: "读取本人实习资料", exact: true });
    assert.equal(await lookup.isDisabled(), project !== "xxt");
    await search(page, "实习"); await shownPage(page, 1, 20); await button(page, "下一页").click(); await shownPage(page, 2, 2);
    await button(page, project === "xxy" ? "实习分页大学 选择" : "实习第二学院 选择").click();
    assert.equal(posts(f.calls).length, 0);
    if (project === "xxy") {
      await lookup.click(); await boundedWait(delayed.started, "previous school lookup start");
      await page.getByRole("button", { name: "清除学校", exact: true }).click(); assert.equal(await lookup.isDisabled(), true);
      await button(page, "实习第二学院 选择").click(); delayed.release(); await boundedWait(delayed.completed, "previous school lookup completion");
      await selectedSchool(page).getByText("实习第二学院", { exact: true }).waitFor();
      assert.equal(await page.getByLabel("姓名", { exact: true }).inputValue(), "", "stale lookup cannot overwrite a new selection");
    }
    await keywordInput(page).fill("另一关键词"); await selectedSchool(page).getByText("实习第二学院", { exact: true }).waitFor();
    await lookup.click(); await page.getByText("资料已读取", { exact: true }).waitFor();
    const today = new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Shanghai" }).format(new Date());
    const future = new Date(Date.parse(`${today}T00:00:00Z`) + 7 * 86400000).toISOString().slice(0, 10);
    await page.getByPlaceholder("请选择截止日期").fill(future); await page.getByPlaceholder("请选择截止日期").press("Enter");
    await preview(page).click(); await page.getByRole("dialog", { name: "确认本次操作", exact: true }).getByText("¥0.25", { exact: true }).waitFor();
    await page.getByRole("button", { name: "返回修改", exact: true }).click();
    await page.getByRole("button", { name: "清除学校", exact: true }).click(); cleared = true;
    assert.equal(await lookup.isDisabled(), project !== "xxt"); assert.equal(await preview(page).isDisabled(), project !== "xxt");
    if (project === "xxt") {
      await preview(page).click(); await page.getByRole("dialog", { name: "确认本次操作", exact: true }).getByText("¥0.25", { exact: true }).waitFor();
      await page.getByRole("button", { name: "返回修改", exact: true }).click();
    }
    if (project === "xxy") await page.screenshot({ path: path.join(output, "internship-school-required.png"), fullPage: true });
    await healthy(f, `internship-${project}-identity-lookup-quote-clear`);
  }

  const revoke = hold();
  const r = await fixture([product(7, "xxy")], async (call, respond) => {
    revoke.seen(); await revoke.gate; await respond(schoolPage(Number(call.params.page), "过期授权")).catch(() => {}); revoke.done();
  }, { viewport: { width: 390, height: 844 } });
  await open(r.page, product(7, "xxy").title); await consent(r.page).click(); await search(r.page, "实习"); await boundedWait(revoke.started, "revoked consent query start");
  await consent(r.page).click(); revoke.release(); await boundedWait(revoke.completed, "revoked consent query completion");
  assert.equal(await picker(r.page).locator(".school-results").count(), 0);
  assert.equal(await button(r.page, "查询学校").isDisabled(), true);
  await healthy(r, "internship-consent-revoked-in-flight");

  const longName = "示例联合大学（东南校区）".repeat(8), tooLong = "学".repeat(130);
  const xssName = '<img src="https://invalid.example/test">示例大学';
  const m = await fixture([product(8)], async (_, respond) => respond({ page: 1, pageSize: 20, hasMore: false,
    items: [{ id: "LONG", name: longName }, { id: "TOO-LONG", name: tooLong }, { id: "ESCAPED", name: xssName }] }),
  { viewport: { width: 390, height: 844 } });
  await m.page.evaluate(() => document.documentElement.classList.add("dark"));
  await open(m.page, product(8).title); await search(m.page, "示例");
  const row = button(m.page, `${longName} 选择`); await row.waitFor();
  assert.equal(await button(m.page, `${tooLong} 名称过长，请核对学校全称`).isDisabled(), true);
  assert.equal(await picker(m.page).locator("img, script").count(), 0); await button(m.page, `${xssName} 选择`).waitFor();
  await row.click(); assert.equal(await formSchool(m.page).inputValue(), longName);
  const layout = await m.page.evaluate(() => {
    const drawer = document.querySelector(".el-drawer"), search = document.querySelector(".service-school-search");
    return { drawerWidth: drawer.clientWidth, drawerScroll: drawer.scrollWidth, searchWidth: search.clientWidth, searchScroll: search.scrollWidth,
      targets: [...search.querySelectorAll("button.school-option, .school-query .el-button, .el-input__wrapper")]
        .filter((e) => e.getBoundingClientRect().height).map((e) => e.getBoundingClientRect().height) };
  });
  assert.ok(layout.drawerScroll <= layout.drawerWidth + 1, JSON.stringify(layout));
  assert.ok(layout.searchScroll <= layout.searchWidth + 1, JSON.stringify(layout));
  assert.ok(layout.targets.every((height) => height >= 44), JSON.stringify(layout));
  await m.page.screenshot({ path: path.join(output, "schools-mobile-dark.png"), fullPage: true });
  assert.equal(posts(m.calls).length, 0); await healthy(m, "mobile-dark-long-name-keyboard-safe-text");
  writeFileSync(path.join(output, "browser-evidence.json"), `${JSON.stringify({ groups: evidence, mobileLayout: layout }, null, 2)}\n`);
  console.log(`Service-school browser regression passed: ${evidence.length} scenario groups; no external traffic.`);
} finally {
  for (const request of pending) request.release();
  for (const context of contexts) await context.close().catch(() => {});
  await browser?.close(); await server.close();
}
