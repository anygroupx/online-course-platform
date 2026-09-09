import assert from "node:assert/strict";
import { existsSync, mkdirSync } from "node:fs";
import { chromium } from "playwright";
import { createServer } from "vite";
const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;padding:24px"><div id="app"></div><script type="module">
import {createApp,h} from 'vue';import {createRouter,createMemoryHistory,RouterView} from 'vue-router';import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';
import Store from '/src/views/ServiceStore.vue';import Orders from '/src/views/ServiceOrders.vue';import {applyAuthSession} from '/src/utils/authSession.js';
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7});
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/services',component:Store},{path:'/service-orders',component:Orders}]});await router.push('/services');await router.isReady();createApp({render:()=>h(RouterView)}).use(router).use(ElementPlus).mount('#app');
</script></body></html>`;
const server = await createServer({
  logLevel: "error",
  server: { host: "127.0.0.1", port: 0 },
  plugins: [
    {
      name: "flash-auth-fixture",
      configureServer(vite) {
        vite.middlewares.use(async (req, res, next) => {
          if (!req.url?.startsWith("/__flashauth")) return next();
          res.setHeader("Content-Type", "text/html;charset=utf-8");
          res.end(await vite.transformIndexHtml(req.url, html));
        });
      },
    },
  ],
});
const product = {
  id: 11,
  providerId: 9,
  providerType: "flash",
  project: "sdxy",
  remoteProductId: "sdxy",
  title: "闪电 · 闪动校园",
  description: "本人授权的运动服务",
  unitPrice: "0.25",
  priceUnit: "元/次",
  enabled: true,
  available: true,
  version: 1,
  capabilities: ["LOOKUP", "CREATE", "SYNC", "REFUND", "DELAY_TASK"],
};
const choices = [
  { field: "zoneId", value: "z1", label: "授权区域" },
  { field: "runRuleId", value: "r1", label: "原有计划" },
  { field: "runType", value: "SUN", label: "日常跑" },
];
const sessions = new Map(),
  unexpected = [],
  errors = [];
let browser,
  seq = 1,
  sms = 0,
  verifications = 0,
  refreshes = 0,
  confirms = 0,
  quoteSeq = 0,
  loseNextVerify = false;
const tomorrow = new Date(Date.now() + 86400000).toISOString().slice(0, 10);
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
  await page.clock.install();
  page.setDefaultTimeout(30000);
  page.setDefaultNavigationTimeout(60000);
  page.on("pageerror", (error) => errors.push(error.message));
  await page.route("**/*", async (route) => {
    const req = route.request(),
      url = new URL(req.url());
    if (url.origin !== base) {
      unexpected.push(url.origin);
      return route.abort();
    }
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const ep = url.pathname.slice(4),
      method = req.method(),
      body = () => JSON.parse(req.postData() || "{}");
    const reply = (data) =>
      route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({ code: 1, data, message: "操作成功" }),
      });
    assert.ok(
      !url.search.includes("123456") && !url.search.includes("mock-password"),
    );
    if (ep === "/services") return reply({ records: [product], total: 1 });
    if (ep === "/services/11/account-sessions") {
      assert.equal(body().authorizedAccount, true);
      assert.equal(body().account, "13800138000");
      assert.equal(body().secret, undefined);
      const id = `421bb209-c7df-4f05-9c18-${String(seq++).padStart(12, "0")}`,
        session = {
          id,
          productId: 11,
          mode: body().mode,
          state: "CREATED",
          accountLabel: "13***00",
          expiresAt: new Date(Date.now() + 600000).toISOString(),
          lookup: null,
          canRefreshRules: false,
        };
      sessions.set(id, session);
      return reply(session);
    }
    if (ep.startsWith("/service-account-sessions/")) {
      const id = ep.split("/")[2],
        action = ep.split("/")[3],
        s = sessions.get(id);
      assert.ok(s);
      if (method === "DELETE") {
        s.state = "REVOKED";
        s.lookup = null;
        return reply(null);
      }
      if (method === "GET") return reply(s);
      if (action === "send-code") {
        sms++;
        s.state = "SMS_SENT";
        await new Promise((r) => setTimeout(r, 150));
        return reply(s);
      }
      if (action === "verify") {
        verifications++;
        assert.equal(
          body().secret,
          s.mode === "SMS" ? "123456" : "mock-password",
        );
        await new Promise((r) => setTimeout(r, 150));
        if (loseNextVerify) {
          loseNextVerify = false;
          s.state = "UNKNOWN";
          return route.abort("failed");
        }
        s.state = "READY";
        s.canRefreshRules = true;
        s.lookup = {
          suggested: { zoneId: "z1", runRuleId: "r1", runType: "SUN" },
          choices,
          notice: "账号已授权",
        };
        return reply(s);
      }
      if (action === "refresh-rules") {
        refreshes++;
        s.state = "READY";
        s.canRefreshRules = false;
        s.lookup = {
          ...s.lookup,
          suggested: { ...s.lookup.suggested, runRuleId: "r2" },
          choices: choices.map((c) =>
            c.field === "runRuleId"
              ? { ...c, value: "r2", label: "刷新后的计划" }
              : c,
          ),
        };
        return reply(s);
      }
    }
    if (ep === "/services/11/quotes") {
      const data = body();
      assert.ok(sessions.has(data.accountSessionId));
      assert.equal(data.fields.password, undefined);
      assert.equal(data.fields.account, undefined);
      assert.equal(data.fields.studentId, undefined);
      assert.equal(data.fields.runRuleId, "r2");
      assert.equal(data.quantity, 1);
      assert.equal(data.taskTimes.length, 1);
      return reply({
        id: `90f47bdf-67fb-4e34-b431-${String(++quoteSeq).padStart(12, "0")}`,
        orderId: null,
        action: "CREATE",
        state: "READY",
        title: product.title,
        quantity: 1,
        quantityUnit: "次",
        amount: "0.25",
        amountLabel: "本次余额扣款",
        expiresAt: "2099-01-01T12:00:00",
      });
    }
    if (ep.endsWith("/confirm")) {
      confirms++;
      return reply({
        id: ep.split("/")[2],
        orderId: "a990205d-963e-4536-8b63-f64c007c880a",
        action: "CREATE",
        state: "SUCCEEDED",
        amount: "0.25",
      });
    }
    if (ep === "/service-orders") return reply({ records: [], total: 0 });
    unexpected.push(`${method} ${ep}`);
    return route.abort();
  });
  async function open() {
    await page.goto(`${base}/__flashauth`);
    await page.getByRole("button", { name: "选择服务", exact: true }).click();
    await page
      .getByText("我有权使用此账号及信息，并授权本平台向所选上游提交", {
        exact: true,
      })
      .click();
    await page
      .getByLabel("服务账号 / 手机号", { exact: true })
      .fill("13800138000");
    await page.getByLabel("学校名称", { exact: true }).fill("测试大学");
  }
  await open();
  await page.getByText("短信验证码", { exact: true }).click();
  await page
    .getByRole("button", { name: "发送本人验证码", exact: true })
    .dblclick();
  await page.getByLabel("收到的短信验证码", { exact: true }).fill("123456");
  assert.equal(sms, 1);
  await page
    .getByRole("button", { name: "查询账号与可用计划", exact: true })
    .dblclick();
  await page
    .getByText("账号已核实，请选择服务计划并预览金额。", { exact: true })
    .waitFor();
  assert.equal(verifications, 1);
  assert.equal(
    await page.getByLabel("收到的短信验证码", { exact: true }).count(),
    0,
  );
  await page.getByRole("button", { name: "刷新上游规则", exact: true }).click();
  await page
    .getByRole("dialog", { name: product.title, exact: true })
    .getByText("刷新后的计划", { exact: true })
    .waitFor();
  assert.equal(refreshes, 1);
  assert.equal(verifications, 1);
  await page.getByLabel("购买次数", { exact: true }).fill("1");
  await page
    .getByLabel("首次执行日期（北京时间）", { exact: true })
    .fill(tomorrow);
  await page
    .getByLabel("首次执行日期（北京时间）", { exact: true })
    .press("Tab");
  await page
    .getByRole("button", { name: "预览金额并下单", exact: true })
    .click();
  assert.equal(confirms, 0);
  await page
    .getByRole("dialog", { name: "确认本次操作", exact: true })
    .getByText("¥0.25", { exact: true })
    .waitFor();
  mkdirSync("../.cache/native-service-ui", { recursive: true });
  await page.screenshot({
    path: "../.cache/native-service-ui/flash-sms-confirmation.png",
    fullPage: true,
    animations: "disabled",
  });
  await page
    .getByRole("button", { name: "确认并下单", exact: true })
    .dblclick();
  await page
    .getByRole("heading", { name: "我的服务订单", exact: true })
    .waitFor();
  assert.equal(confirms, 1);
  await open();
  await page.getByLabel("服务密码", { exact: true }).fill("mock-password");
  loseNextVerify = true;
  await page
    .getByRole("button", { name: "查询账号与可用计划", exact: true })
    .click();
  await page
    .getByText(
      "请求结果尚未确认。请检查原授权状态，不要重复发送验证码或提交凭据。",
      { exact: true },
    )
    .waitFor();
  const count = verifications;
  await page.getByRole("button", { name: "检查授权状态", exact: true }).click();
  await page
    .getByText("上游结果无法确认，本会话不会再次提交。请核对后重新授权。", {
      exact: true,
    })
    .waitFor();
  assert.equal(verifications, count);
  assert.equal(
    await page
      .getByRole("button", { name: "查询账号与可用计划", exact: true })
      .count(),
    0,
  );
  assert.equal(
    await page.getByLabel("服务密码", { exact: true }).inputValue(),
    "",
  );
  assert.equal(
    await page
      .evaluate(
        () => JSON.stringify(localStorage) + JSON.stringify(sessionStorage),
      )
      .then((v) => v.includes("mock-password") || v.includes("123456")),
    false,
  );
  await page.getByRole("button", { name: "重新授权", exact: true }).click();
  await page.getByLabel("服务密码", { exact: true }).fill("mock-password");
  await page
    .getByRole("button", { name: "查询账号与可用计划", exact: true })
    .click();
  await page
    .getByText("账号已核实，请选择服务计划并预览金额。", { exact: true })
    .waitFor();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => document.documentElement.classList.add("dark"));
  assert.ok(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  );
  await page.waitForFunction(() => document.querySelectorAll(".el-message").length === 0);
  await page.screenshot({
    path: "../.cache/native-service-ui/flash-authorization-mobile-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  await page
    .getByText("我有权使用此账号及信息，并授权本平台向所选上游提交", {
      exact: true,
    })
    .click();
  await page
    .getByRole("button", { name: "预览金额并下单", exact: true })
    .isDisabled()
    .then((v) => assert.equal(v, true));
  await page
    .getByText("我有权使用此账号及信息，并授权本平台向所选上游提交", {
      exact: true,
    })
    .click();
  await page.getByLabel("服务密码", { exact: true }).fill("mock-password");
  await page
    .getByRole("button", { name: "查询账号与可用计划", exact: true })
    .click();
  await page
    .getByText("账号已核实，请选择服务计划并预览金额。", { exact: true })
    .waitFor();
  await page.clock.fastForward(11 * 60 * 1000);
  await page.getByText("授权已过期，请重新授权。", { exact: true }).waitFor();
  assert.equal(
    await page
      .getByRole("button", { name: "预览金额并下单", exact: true })
      .isDisabled(),
    true,
  );
  assert.deepEqual(unexpected, []);
  assert.deepEqual(errors, []);
  console.log(
    "PASS Flash authorization: one SMS/code → server-bound checkout → fresh rule choices → one order; lost verification response → query-only recovery; password reset/consent revocation/mobile dark; simulated only",
  );
} finally {
  await browser?.close();
  await server.close();
}
