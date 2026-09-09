import assert from "node:assert/strict";
import { existsSync, mkdirSync } from "node:fs";
import { chromium } from "playwright";
import { createTestServer as createServer } from './fixtures/test-server.mjs';
const id = "20c5c14d-2eba-4dd7-a023-52a49a3dcc6b",
  token = "privateShowdocKey0123456789abcdef",
  code = "445566";
const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;padding:24px"><div id="app"></div><script type="module">
import {createApp,h,ref} from 'vue';import {createRouter,createMemoryHistory,RouterView} from 'vue-router';import ElementPlus from 'element-plus';import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';
import Orders from '/src/views/ServiceOrders.vue';import Plan from '/src/components/InternshipPlanFields.vue';import {newInternshipSchedule} from '/src/utils/internshipServices.js';import {applyAuthSession} from '/src/utils/authSession.js';applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7});
const Advice={setup(){const fields=ref({account:'13800138000',password:'fixture-password'}),schedule=ref(newInternshipSchedule());return()=>h('main',[h(Plan,{modelValue:fields.value,schedule:schedule.value,project:'qzt',productId:1,authorized:true,'onUpdate:modelValue':v=>fields.value=v,'onUpdate:schedule':v=>schedule.value=v}),h('pre',{id:'schedule'},JSON.stringify(schedule.value))]);}};
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/service-orders',component:Orders},{path:'/advice',component:Advice}]});await router.push(new URLSearchParams(location.search).get('page')||'/service-orders');await router.isReady();window.__fixtureRouter=router;createApp({render:()=>h(RouterView)}).use(router).use(ElementPlus).mount('#app');
</script></body></html>`;
const server = await createServer({
  logLevel: "error",
  server: { host: "127.0.0.1", port: 0 },
  plugins: [
    {
      name: "native-notification-fixture",
      configureServer(vite) {
        vite.middlewares.use(async (req, res, next) => {
          if (!req.url?.startsWith("/__service_notifications")) return next();
          res.setHeader("Content-Type", "text/html;charset=utf-8");
          res.end(await vite.transformIndexHtml(req.url, html));
        });
      },
    },
  ],
});
let browser,
  context,
  sends = 0,
  loseChallenge = true,
  loseSave = false,
  sequence = 0,
  historyFailure = false;
let settings = {
  orderId: id,
  configured: false,
  enabled: false,
  verified: false,
  deliveryAvailable: true,
  canVerify: false,
  version: 0,
  challengeDeliveryId: null,
  challengeExpiresAt: null,
  verifiedAt: null,
  notices: [],
};
const records = [],
  writes = [],
  reads = [],
  unexpected = [],
  errors = [];
const order = {
  id,
  title: "校友帮 · 实习服务",
  accountLabel: "13***000",
  providerType: "sxdk_tw",
  project: "xyb",
  status: "ACTIVE",
  quantity: 10,
  completed: null,
  distance: null,
  paidAmount: "2.50",
  refundedAmount: "0.00",
  pendingOperationId: null,
  createTime: "2026-09-08T12:00:00",
  version: 1,
  actions: [],
  schedule: {
    endDate: "2099-01-01",
    weekdays: [1, 2, 3, 4, 5],
    checkInTime: "08:00:00",
    checkOutTime: "18:00:00",
    runMode: 1,
    dailyReport: false,
    weeklyReport: false,
    monthlyReport: false,
  },
};
const expiry = new Date(Date.now() + 10 * 86400000).toISOString().slice(0, 10);
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
    const path = url.pathname.slice(4),
      method = req.method(),
      body = () => JSON.parse(req.postData() || "{}"),
      respond = (data) =>
        route.fulfill({
          contentType: "application/json",
          body: JSON.stringify({ code: 1, data }),
        });
    if (method === "GET") reads.push(path);
    else writes.push({ path, method, body: body() });
    if (path === "/service-orders" && method === "GET")
      return respond({ records: [order], total: 1 });
    if (path === `/service-orders/${id}/notifications`) {
      if (method === "GET") return respond(settings);
      const b = body();
      assert.equal(b.version, settings.version);
      assert.equal(b.consent, true);
      if (method === "PUT") {
        assert.equal(b.token, token);
        settings = {
          ...settings,
          configured: true,
          enabled: false,
          verified: false,
          canVerify: false,
          version: settings.version + 1,
          challengeDeliveryId: null,
          challengeExpiresAt: null,
        };
        if (loseSave) {
          loseSave = false;
          return route.abort("connectionfailed");
        }
        return respond(settings);
      }
      if (method === "DELETE") {
        settings = {
          ...settings,
          configured: false,
          enabled: false,
          verified: false,
          canVerify: false,
          version: settings.version + 1,
          challengeDeliveryId: null,
          challengeExpiresAt: null,
        };
        for (const job of records)
          if (job.state === "READY") job.state = "CANCELLED";
        return respond(settings);
      }
    }
    if (
      path === `/service-orders/${id}/notifications/challenge` &&
      method === "POST"
    ) {
      assert.equal(body().version, settings.version);
      assert.equal(body().consent, true);
      assert.equal(settings.challengeDeliveryId, null);
      sends++;
      const job = {
        id: `challenge-${++sequence}`,
        orderId: id,
        kind: "VERIFY_RECEIVER",
        state: loseChallenge ? "UNKNOWN" : "ACCEPTED",
        createdAt: "2026-09-08T12:05:00",
        notice: "结果未知不自动重发；若已收到验证码，仍可验证接收。",
      };
      records.unshift(job);
      settings = {
        ...settings,
        challengeDeliveryId: job.id,
        canVerify: true,
        challengeExpiresAt: new Date(Date.now() + 600000).toISOString(),
      };
      if (loseChallenge) {
        loseChallenge = false;
        return route.abort("connectionfailed");
      }
      return respond(settings);
    }
    if (
      path === `/service-orders/${id}/notifications/verify` &&
      method === "POST"
    ) {
      assert.equal(body().code, code);
      assert.equal(body().consent, true);
      settings = {
        ...settings,
        enabled: true,
        verified: true,
        canVerify: false,
        verifiedAt: new Date().toISOString(),
      };
      return respond(settings);
    }
    if (
      path === `/service-orders/${id}/notifications/deliveries` &&
      method === "GET"
    ) {
      if (historyFailure) {
        historyFailure = false;
        return route.fulfill({
          status: 503,
          contentType: "application/json",
          body: '{"code":-1,"message":"fixture history unavailable"}',
        });
      }
      return respond({ records, total: records.length });
    }
    if (
      path.startsWith("/service-notification-deliveries/") &&
      method === "GET"
    )
      return respond(records.find((r) => r.id === path.split("/").at(-1)));
    if (path === "/services/1/lookup" && method === "POST")
      return respond({
        suggested: {
          name: "本人姓名",
          address: "本人实习地址",
          lat: "28.1",
          lng: "112.1",
        },
        choices: [],
        notice: "上游建议仅供核实，不自动修改计划。",
        advice: {
          checkInTime: "09:15:00",
          checkOutTime: "17:45:00",
          weekdays: [1, 5, 7],
          endDate: expiry,
          weeklyReport: true,
          dailyReport: false,
        },
      });
    unexpected.push(`${method} ${path}`);
    return route.fulfill({ status: 404, body: "Unexpected fixture API" });
  });
  const page = await context.newPage();
  page.setDefaultTimeout(45000);
  page.setDefaultNavigationTimeout(90000);
  await page.goto(`${base}/__service_notifications`);
  await page.getByRole("button", { name: "微信通知", exact: true }).waitFor();
  assert.equal(writes.length, 0);
  assert.equal(
    reads.some((p) => p.includes("/notifications")),
    false,
    "no automatic notification queries on orders load",
  );
  await page.getByRole("button", { name: "微信通知", exact: true }).click();
  let drawer = page.getByRole("dialog", { name: "实习订单 · 微信通知" });
  await drawer.getByText("尚未配置", { exact: true }).waitFor();
  assert.equal(sends, 0);
  async function fillKey() {
    await drawer
      .getByRole("textbox", { name: "ShowDoc 推送密钥", exact: true })
      .fill(token);
    await drawer
      .getByText("此接收方式由我控制，同意向 ShowDoc 发送上述有限订单信息", {
        exact: true,
      })
      .click();
  }
  await drawer
    .getByRole("textbox", { name: "ShowDoc 推送密钥", exact: true })
    .fill("https://evil.example/notify");
  assert.equal(
    await drawer
      .getByRole("button", { name: "保存密钥（不发送）" })
      .isDisabled(),
    true,
  );
  await fillKey();
  await drawer.getByRole("button", { name: "保存密钥（不发送）" }).click();
  await drawer.getByRole("button", { name: "发送一次验证通知" }).waitFor();
  assert.equal(sends, 0);
  assert.equal(
    await drawer
      .locator('.el-collapse-item input[type="password"]')
      .inputValue(),
    "",
    "saved key must be cleared even after the editor collapses",
  );
  await drawer
    .getByRole("button", { name: "发送一次验证通知" })
    .click({ clickCount: 2 });
  await drawer
    .getByText(
      "验证通知的请求结果尚未确认，请检查当前设置和原发送记录，不要重发。",
      { exact: true },
    )
    .waitFor();
  assert.equal(sends, 1);
  assert.equal(
    await drawer.getByRole("button", { name: "发送一次验证通知" }).count(),
    0,
  );
  const beforeCheck = writes.length;
  await drawer.getByRole("button", { name: "检查原发送结果" }).click();
  await drawer.getByRole("textbox", { name: "收到的六位验证码" }).waitFor();
  assert.equal(writes.length, beforeCheck);
  assert.equal(settings.enabled, false);
  await drawer.getByRole("textbox", { name: "收到的六位验证码" }).fill(code);
  await drawer.getByRole("button", { name: "验证并启用订单通知" }).click();
  await drawer.getByText("已验证并启用", { exact: true }).waitFor();
  assert.equal(sends, 1);
  records.unshift({
    id: "update-1",
    orderId: id,
    kind: "ORDER_UPDATE",
    state: "ACCEPTED",
    createdAt: "2026-09-08T13:00:00",
    notice: "ShowDoc 已受理，不代表本人已阅读。",
  });
  await drawer.getByRole("button", { name: "刷新记录", exact: true }).click();
  await drawer
    .getByText("ShowDoc 已受理，不代表本人已阅读。", { exact: true })
    .waitFor();
  assert.equal(sends, 1, "history queries never send messages");
  mkdirSync("../.cache/native-service-ui", { recursive: true });
  await page.waitForFunction(
    () => document.querySelectorAll(".el-message").length === 0,
  );
  await page.screenshot({
    path: "../.cache/native-service-ui/internship-notifications-desktop.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => document.documentElement.classList.add("dark"));
  await page.screenshot({
    path: "../.cache/native-service-ui/internship-notifications-mobile-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  const bounds = await drawer.boundingBox();
  assert.ok(bounds.x >= -1 && bounds.x + bounds.width <= 391);
  assert.ok(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  );
  const stored = await page.evaluate(
    () => JSON.stringify(localStorage) + JSON.stringify(sessionStorage),
  );
  assert.equal(stored.includes(token), false);
  assert.equal(stored.includes(code), false);
  assert.equal((await page.locator("body").innerText()).includes(token), false);
  historyFailure = true;
  await drawer.getByRole("button", { name: "刷新记录", exact: true }).click();
  await drawer
    .getByText("发送记录读取失败，请刷新重试，不代表没有记录。", {
      exact: true,
    })
    .waitFor();
  await drawer.getByRole("button", { name: "刷新记录", exact: true }).click();
  await drawer.getByRole("button", { name: "停止通知并清除密钥" }).click();
  const stop = page.getByRole("dialog", { name: "停止本订单通知" });
  await stop.getByRole("button", { name: "确认停止" }).click();
  await drawer.getByText("尚未配置", { exact: true }).waitFor();
  assert.equal(sends, 1);
  assert.equal(writes.filter((w) => w.method === "DELETE").length, 1);
  loseSave = true;
  await fillKey();
  await drawer.getByRole("button", { name: "保存密钥（不发送）" }).click();
  await drawer
    .getByText(
      "保存结果尚未确认；请检查当前设置，不自动重试。尚未发送验证通知。",
      { exact: true },
    )
    .waitFor();
  const savedWrites = writes.length;
  await drawer.getByRole("button", { name: "检查当前设置" }).click();
  await drawer.getByRole("button", { name: "发送一次验证通知" }).waitFor();
  assert.equal(writes.length, savedWrites);
  assert.equal(sends, 1);
  // Navigation while a stop confirmation is open must not act on an unmounted/stale order.
  const deletesBeforeNavigation = writes.filter(
    (w) => w.method === "DELETE",
  ).length;
  await drawer.getByRole("button", { name: "停止通知并清除密钥" }).click();
  const staleConfirmation = page.getByRole("dialog", {
    name: "停止本订单通知",
  });
  await staleConfirmation.waitFor();
  await page.evaluate(() => window.__fixtureRouter.push("/advice"));
  await page.getByRole("button", { name: "读取本人实习资料" }).waitFor();
  await staleConfirmation.getByRole("button", { name: "确认停止" }).click();
  await staleConfirmation.waitFor({ state: "hidden" });
  assert.equal(
    writes.filter((w) => w.method === "DELETE").length,
    deletesBeforeNavigation,
  );
  // Real account-plan form: suggestions remain inert until a separate explicit adoption.
  const advice = await context.newPage();
  advice.setDefaultTimeout(45000);
  advice.setDefaultNavigationTimeout(90000);
  await advice.goto(`${base}/__service_notifications?page=/advice`);
  await advice.getByRole("button", { name: "读取本人实习资料" }).click();
  await advice.getByRole("button", { name: "采用这些建议" }).waitFor();
  let schedule = JSON.parse(await advice.locator("#schedule").innerText());
  assert.equal(schedule.checkInTime, "08:00:00");
  assert.equal(schedule.endDate, "");
  assert.equal(schedule.weeklyReport, false);
  await advice.getByRole("button", { name: "采用这些建议" }).click();
  schedule = JSON.parse(await advice.locator("#schedule").innerText());
  assert.equal(schedule.checkInTime, "09:15:00");
  assert.equal(schedule.endDate, expiry);
  assert.equal(schedule.weeklyReport, true);
  assert.deepEqual(schedule.weekdays, [1, 5, 7]);
  assert.equal(
    await advice.getByRole("button", { name: "采用这些建议" }).count(),
    0,
  );
  await advice.getByRole("button", { name: "读取本人实习资料" }).click();
  await advice.getByRole("button", { name: "采用这些建议" }).waitFor();
  await advice
    .getByRole("textbox", { name: "实习账号", exact: true })
    .fill("13900139000");
  assert.equal(
    await advice.getByRole("button", { name: "采用这些建议" }).count(),
    0,
    "changing account invalidates old recommendations",
  );
  assert.equal(
    writes.filter((w) => /quotes|confirm/.test(w.path)).length,
    0,
    "adopting advice must not purchase a plan",
  );
  assert.deepEqual(unexpected, []);
  assert.deepEqual(errors, []);
  console.log(
    "PASS internship notifications/advice: actual order entry, opt-in key save without send, one proof with lost response GET-only recovery, enable/history/disconnect, explicit schedule advice adoption, mobile/dark; APIs simulated only.",
  );
} finally {
  await context?.close();
  await browser?.close();
  await server.close();
}
