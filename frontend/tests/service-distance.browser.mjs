import assert from "node:assert/strict";
import { existsSync, mkdirSync } from "node:fs";
import path from "node:path";
import { chromium } from "playwright";
import { createTestServer as createServer } from "./fixtures/test-server.mjs";

// Actual Vue views. Every business request is intercepted, including admin configuration.
const html = `<!doctype html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;padding:20px"><div id="app"></div>
<script type="module">
import {createApp,h} from 'vue';import {createRouter,createMemoryHistory,RouterView} from 'vue-router';import {createPinia} from 'pinia';import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';
import Store from '/src/views/ServiceStore.vue';import Orders from '/src/views/ServiceOrders.vue';import Products from '/src/views/AdminServiceProducts.vue';import Providers from '/src/views/AdminApiProviders.vue';import {applyAuthSession} from '/src/utils/authSession.js';
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7,isAdmin:true});
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/services',component:Store},{path:'/service-orders',component:Orders},{path:'/admin/service-orders',component:Orders,meta:{serviceAdmin:true}},{path:'/admin/service-products',component:Products},{path:'/admin/api-providers',component:Providers}]});
await router.push(new URLSearchParams(location.search).get('page')||'/services');await router.isReady();createApp({render:()=>h(RouterView)}).use(createPinia()).use(router).use(ElementPlus).mount('#app');
</script></body></html>`;
const server = await createServer({ logLevel: "error", plugins: [{ name: "distance-workflow", configureServer(vite) {
  vite.middlewares.use(async (req, res, next) => {
    if (!req.url?.startsWith("/__distance")) return next();
    res.setHeader("Content-Type", "text/html;charset=utf-8");
    res.end(await vite.transformIndexHtml(req.url, html));
  });
} }] });
const product = { id: 5, providerId: 15, providerType: "ssbenz_xbd", project: "xbd", remoteProductId: "0", title: "总公里计划 · 方案 0", description: "填写总公里数和每周时段，提交后可核对状态。", unitPrice: "0.25", priceUnit: "元/公里", enabled: true, available: true, version: 0, capabilities: ["CREATE", "SYNC"] };
const provider = { id: 15, name: "总公里测试配置", providerType: "ssbenz_xbd", status: 1, verified: true, verifiedAt: "2026-09-10T09:00:00", apiUrl: "https://authorized.example/xbd/ydapi", configVersion: 1 };
const order = { id: "3a360631-df83-407e-9c59-0a18da8a04e5", title: product.title, accountLabel: "13***00", providerType: "ssbenz_xbd", project: "xbd", status: "SUBMITTED", quantity: 1, quantityUnit: "单", completed: null, distance: "120.50", paidAmount: "30.13", refundedAmount: "0.00", actions: [], pendingOperationId: null, createTime: "2026-09-10T10:00:00", version: 1, distancePlan: null };
const quotes = new Map(), calls = [], unexpected = [], errors = [], productSaves = [], providerSaves = [];
let created = false, confirms = 0, reads = 0, syncs = 0, resolutions = 0, failSync = false, browser;
const out = path.resolve("../.cache/native-service-ui"); mkdirSync(out, { recursive: true });
function frozenPlan(body) {
  assert.equal(body.quantity, 1); assert.equal(typeof body.distance, "string");
  assert.equal(body.authorizedAccount, true); assert.equal(body.accountSessionId, null);
  assert.equal(body.schedule, null); assert.deepEqual(body.taskTimes, []);
  assert.deepEqual(Object.keys(body.fields).sort(), ["account", "password", "schoolName", "startTime", "endTime", "weekdays"].sort());
  assert.equal(body.fields.account, "13800138000"); assert.equal(body.fields.password, "demo-password");
  assert.equal(body.fields.startTime, "09:05"); assert.equal(body.fields.endTime, "21:10");
  assert.equal(body.fields.weekdays, "1,3,5");
  const [whole, fraction = ""] = body.distance.split(".");
  return { typeCode: "0", totalDistance: `${whole}.${fraction.padEnd(2,"0")}`, schoolName: body.fields.schoolName || "自动识别", startTime: body.fields.startTime, endTime: body.fields.endTime, weekdays: body.fields.weekdays.split(",").map(Number) };
}
try {
  await server.listen();
  const base = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({ executablePath: process.env.BROWSER_PATH || (existsSync("/snap/bin/chromium") ? "/snap/bin/chromium" : undefined), headless: true, args: ["--no-sandbox", "--disable-dev-shm-usage"] });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1080 } });
  await context.route("**/*", async (route) => {
    const req = route.request(), url = new URL(req.url()), method = req.method();
    if (url.origin !== base) { unexpected.push(`external ${url.origin}`); return route.abort(); }
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const endpoint = url.pathname.slice(4), body = () => JSON.parse(req.postData() || "{}");
    calls.push(`${method} ${endpoint}`);
    const respond = (data, status = 200) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify({ code: status === 200 ? 1 : 400, data, message: status === 200 ? "操作成功" : "暂时无法核对提交状态" }) });
    if (endpoint === "/services" || endpoint === "/admin/service-products" && method === "GET") return respond({ records: [product], total: 1 });
    if (endpoint === "/services/5/quotes" && method === "POST") {
      const distancePlan = frozenPlan(body());
      const quote = { id: `e66cb44f-4a73-44a4-98db-${String(quotes.size + 1).padStart(12,"0")}`, orderId: null, action: "CREATE", state: "READY", title: product.title, quantity: 1, quantityUnit: "单", amount: "30.13", amountLabel: "本次余额扣款", expiresAt: "2099-01-01T00:00:00", distancePlan };
      quotes.set(quote.id, quote); return respond(quote);
    }
    const match = endpoint.match(/^\/service-order-operations\/([^/]+)(\/confirm)?$/);
    if (match) {
      const quote = quotes.get(match[1]); assert.ok(quote);
      if (match[2]) {
        assert.equal(method, "POST"); assert.deepEqual(body(), {}); confirms++;
        assert.equal(confirms, 1, "lost responses must not replay confirmation");
        created = true; quote.orderId = order.id; quote.state = "UNKNOWN";
        order.pendingOperationId = quote.id; order.status = "CONFIRMING"; order.distancePlan = quote.distancePlan;
        return route.abort("failed"); // The simulated operation exists; only the response is lost.
      }
      assert.equal(method, "GET"); reads++;
      if (reads > 1) { quote.state = "SUCCEEDED"; order.status = "SUBMITTED"; order.pendingOperationId = null; }
      return respond(quote);
    }
    if ((endpoint === "/service-orders" || endpoint === "/admin/service-orders") && method === "GET") return respond({ records: created ? [order] : [], total: created ? 1 : 0, current: 1, size: 20 });
    if (endpoint === `/service-orders/${order.id}/sync`) {
      assert.equal(method, "POST"); assert.deepEqual(body(), {}); syncs++;
      if (failSync) return respond(null, 502);
      order.status = "SUBMISSION_REVIEW"; order.version++; return respond(order);
    }
    if (endpoint === `/service-orders/${order.id}/events`) return respond([{ id: [...quotes.keys()].at(-1), action: "CREATE", state: "SUCCEEDED", amount: "30.13", createTime: order.createTime }]);
    if (endpoint.startsWith("/admin/service-order-operations/")) {
      const id = endpoint.split("/")[3], quote = quotes.get(id); assert.ok(quote);
      if (endpoint.endsWith("/resolve")) {
        const submitted = body(); assert.equal(submitted.refundedUnits, null);
        assert.equal(submitted.outcome, "NOT_ACCEPTED"); assert.equal(submitted.upstreamChecked, true);
        assert.ok(submitted.evidence.length >= 10); resolutions++;
        quote.state = "NOT_ACCEPTED"; order.status = "CANCELLED"; order.refundedAmount = "30.13"; order.pendingOperationId = null;
      }
      return respond(quote);
    }
    if (endpoint === "/admin/api-providers" && method === "GET") return respond({ records: [provider], total: 1 });
    if (endpoint === "/admin/plugin-integrations/P05/providers/15/catalog") {
      assert.equal(method, "GET"); assert.equal(url.searchParams.get("project"), "xbd");
      return respond([{ id: "0", name: "总公里计划 · 方案 0", unitPrice: "0.10", priceUnit: "元/公里" }, { id: "1", name: "总公里计划 · 方案 1", unitPrice: "0.20", priceUnit: "元/公里" }]);
    }
    if (endpoint === "/admin/service-products" && method === "POST") { productSaves.push(body()); return respond(product); }
    if (endpoint === "/admin/api-providers" && method === "POST") { providerSaves.push(body()); return respond({ ...provider, id: 16, status: 2, verified: false }); }
    unexpected.push(`${method} ${endpoint}`); return respond(null, 404);
  });
  const page = await context.newPage(); page.setDefaultTimeout(20000); page.setDefaultNavigationTimeout(60000);
  page.on("pageerror", (error) => errors.push(error.message));
  const go = (route) => page.goto(`${base}/__distance?page=${encodeURIComponent(route)}`);
  const formInput = (label) => page.locator(".el-form-item").filter({ has: page.locator("label", { hasText: label }) }).locator("input").first();
  const screenshot = async (name) => { await page.getByRole("heading").first().waitFor(); await page.waitForFunction(() => !document.querySelector(".el-message")); await page.waitForTimeout(350); await page.screenshot({ path: path.join(out, name), fullPage: true }); };
  await go("/services");
  await page.getByRole("button", { name: "选择服务", exact: true }).click();
  const drawer = page.locator(".distance-checkout"); await drawer.waitFor({ state: "visible" });
  assert.equal(await page.getByRole("button", { name: "查询账号与可用计划", exact: true }).count(), 0);
  assert.equal(await page.getByText("购买次数", { exact: true }).count(), 0);
  assert.equal(await page.getByText("每次距离（公里）", { exact: true }).count(), 0);
  await formInput("服务账号 / 手机号").fill("13800138000");
  await formInput("服务密码").fill("demo-password");
  await page.getByText("我有权使用此账号及信息，并授权提交", { exact: true }).click();
  assert.equal(await page.getByRole("checkbox", { name: "我有权使用此账号及信息，并授权提交" }).isChecked(), true);
  await page.getByRole("textbox", { name: "总公里数", exact: true }).fill("120.501");
  const preview = page.getByRole("button", { name: "预览金额并下单", exact: true });
  assert.equal(await preview.isDisabled(), true);
  await page.getByRole("textbox", { name: "总公里数", exact: true }).fill("120.50");
  await page.getByLabel("开始时间", { exact: true }).fill("21:10");
  await page.getByLabel("结束时间", { exact: true }).fill("21:10");
  assert.equal(await preview.isDisabled(), true);
  await page.getByLabel("开始时间", { exact: true }).fill("09:05");
  await page.getByLabel("结束时间", { exact: true }).fill("21:10");
  assert.equal(await preview.isEnabled(), true);
  await page.getByText("周二", { exact: true }).click();
  await page.getByText("周四", { exact: true }).click();
  assert.equal(confirms, 0); assert.equal(quotes.size, 0);
  await screenshot("p05-store-desktop.png");
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => document.documentElement.classList.add("dark"));
  await screenshot("p05-store-mobile-dark.png");
  await page.locator(".total-distance-fields").scrollIntoViewIfNeeded();
  await screenshot("p05-plan-mobile-dark.png");
  const dimensions = await page.evaluate(() => ({ width: innerWidth, body: document.body.scrollWidth,
    inputs: [...document.querySelectorAll(".distance-checkout .el-input__wrapper")].map((el) => el.getBoundingClientRect().height),
    days: [...document.querySelectorAll(".distance-weekdays .el-checkbox")].map((el) => el.getBoundingClientRect().height) }));
  assert.ok(dimensions.body <= dimensions.width, JSON.stringify(dimensions));
  assert.ok(dimensions.inputs.every((height) => height >= 44)); assert.ok(dimensions.days.every((height) => height >= 44));
  await preview.click();
  let dialog = page.getByRole("dialog", { name: "确认本次操作" });
  await dialog.waitFor({ state: "visible" });
  assert.match(await dialog.innerText(), /120\.50 公里/); assert.match(await dialog.innerText(), /¥30\.13/);
  assert.match(await dialog.innerText(), /不代表执行完成/); assert.doesNotMatch(await dialog.innerText(), /demo-password|13800138000|每次距离/);
  await dialog.getByRole("button", { name: "返回修改", exact: true }).click();
  assert.equal(confirms, 0);
  await preview.click(); dialog = page.getByRole("dialog", { name: "确认本次操作" });
  await dialog.getByRole("button", { name: "确认并下单", exact: true }).click();
  await dialog.getByRole("button", { name: "检查提交结果", exact: true }).waitFor();
  await screenshot("p05-unknown-mobile-dark.png");
  assert.equal(await dialog.getByRole("button", { name: "确认并下单", exact: true }).count(), 0);
  await dialog.getByRole("button", { name: "检查提交结果", exact: true }).click();
  await page.getByText("待人工核对", { exact: true }).first().waitFor();
  assert.equal(confirms, 1);
  await dialog.getByRole("button", { name: "检查提交结果", exact: true }).click();
  await page.getByRole("heading", { name: "我的服务订单", exact: true }).waitFor();
  const card = page.locator(".order-card");
  assert.match(await card.innerText(), /已提交/); assert.match(await card.innerText(), /1\s*单/);
  assert.equal(await card.locator(".el-progress").count(), 0);
  for (const label of ["执行记录", "取消并退款", "增加次数", "编辑计划"]) assert.equal(await card.getByRole("button", { name: label, exact: true }).count(), 0);
  await page.getByRole("button", { name: "核对提交状态", exact: true }).click();
  await page.locator(".order-card .order-title").getByText("提交待核对", { exact: true }).waitFor();
  assert.equal(order.refundedAmount, "0.00"); assert.equal(order.completed, null);
  failSync = true;
  await page.getByRole("button", { name: "核对提交状态", exact: true }).click();
  await page.getByText("暂时无法核对提交状态", { exact: true }).first().waitFor();
  assert.match(await card.innerText(), /提交待核对/);
  assert.ok((await card.locator(".order-actions .el-button").evaluateAll((nodes) => nodes.map((node) => node.getBoundingClientRect().height))).every((height) => height >= 44));
  await screenshot("p05-order-mobile-dark.png");
  await page.setViewportSize({ width: 1440, height: 1080 });
  await page.evaluate(() => document.documentElement.classList.remove("dark"));
  await screenshot("p05-order-desktop.png");
  const storage = await page.evaluate(() => JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]));
  assert.doesNotMatch(storage, /demo-password|13800138000|startTime|120\.50/);

  // A separately seeded unknown operation can be resolved only through the audited UI.
  const pending = [...quotes.values()].at(-1); pending.state = "UNKNOWN";
  order.pendingOperationId = pending.id; order.status = "CONFIRMING";
  await go("/admin/service-orders");
  await page.getByRole("button", { name: "核对处理结果", exact: true }).click();
  const resolution = page.getByRole("dialog", { name: "人工核对 · 会影响订单与余额" });
  await resolution.waitFor({ state: "visible" });
  assert.match(await resolution.innerText(), /仅|不确认执行完成/);
  assert.equal(await resolution.getByRole("spinbutton").count(), 0);
  await resolution.locator("textarea").fill("已独立核查此笔提交和完整资金记录，确认没有受理");
  await resolution.getByText("我已核查订单和账务记录，确认上述结论", { exact: true }).click();
  await resolution.getByRole("button", { name: "确认并记入审计", exact: true }).click();
  await page.locator(".order-card .order-title").getByText("已取消", { exact: true }).waitFor(); assert.equal(resolutions, 1);
  order.status = "REFUND_REVIEW"; await page.getByRole("button", { name: "刷新列表", exact: true }).click();
  await page.locator(".order-card .order-title").getByText("退款待核对", { exact: true }).waitFor();
  assert.equal(await page.getByRole("button", { name: "核对退款入账", exact: true }).count(), 0);

  await go("/admin/service-products");
  await page.getByRole("button", { name: "上架服务商品", exact: true }).click();
  const productDialog = page.getByRole("dialog");
  await productDialog.locator(".el-select").first().click();
  await page.getByRole("option", { name: /总公里测试配置/ }).click();
  await productDialog.getByRole("button", { name: "读取目录", exact: true }).click();
  await productDialog.locator(".el-select").nth(1).click();
  await page.getByRole("option", { name: /总公里计划 · 方案 1/ }).click();
  await productDialog.locator(".el-form-item").filter({ has: page.locator("label", { hasText: "销售单价" }) }).locator("input").fill("0.35");
  await productDialog.getByRole("button", { name: "保存商品", exact: true }).click();
  await productDialog.waitFor({ state: "hidden" });
  assert.equal(productSaves.length, 1); assert.equal(productSaves[0].project, "xbd");
  assert.equal(productSaves[0].remoteProductId, "1"); assert.equal(productSaves[0].unitPrice, "0.35");
  assert.equal(productSaves[0].providerId, 15);

  await go("/admin/api-providers");
  await page.getByRole("button", { name: "添加接口", exact: true }).click();
  const providerDialog = page.getByRole("dialog", { name: "添加接口" });
  await providerDialog.getByPlaceholder("请输入接口名称").fill("总公里配置演示");
  await providerDialog.locator(".el-select").click();
  await page.getByRole("option", { name: "总公里计划（P05 明文接口）", exact: true }).click();
  assert.match(await providerDialog.innerText(), /无需 UID/);
  assert.equal(await providerDialog.getByPlaceholder("请输入账号").count(), 0);
  await providerDialog.getByPlaceholder("https://provider.example.com 或 /openapi 基础目录").fill("https://authorized.example/xbd/ydapi");
  await providerDialog.getByPlaceholder("请输入API Key").fill("demo-source-key-not-real");
  await providerDialog.getByRole("button", { name: "保存", exact: true }).click();
  await providerDialog.waitFor({ state: "hidden" });
  assert.equal(providerSaves.length, 1); assert.equal(providerSaves[0].providerType, "ssbenz_xbd");
  assert.equal(providerSaves[0].username, ""); assert.equal(providerSaves[0].apiKey, "demo-source-key-not-real");
  assert.equal(confirms, 1); assert.equal(reads, 2); assert.equal(syncs, 2);
  assert.equal(calls.some((call) => /lookup|\/schools|\/logs|refund-quotes|settle-refund|test-connection|refresh-balance/.test(call)), false);
  assert.deepEqual(unexpected, []); assert.deepEqual(errors, []);
  console.log("P05 actual Vue: total-distance form, exact payload, frozen quote, single lost confirmation, read-only recovery, submission-only states, admin null refund units/catalog/token and mobile dark verified");
} finally { await browser?.close(); await server.close(); }
