import assert from "node:assert/strict";
import { existsSync, mkdirSync } from "node:fs";
import { chromium } from "playwright";
import { createTestServer as createServer } from './fixtures/test-server.mjs';
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
      name: "heisha-face-fixture",
      configureServer(vite) {
        vite.middlewares.use(async (req, res, next) => {
          if (!req.url?.startsWith("/__heishaface")) return next();
          res.setHeader("Content-Type", "text/html;charset=utf-8");
          res.end(await vite.transformIndexHtml(req.url, html));
        });
      },
    },
  ],
});
const product = {
  id: 13,
  providerId: 9,
  providerType: "heisha",
  project: "default",
  remoteProductId: "3",
  title: "黑鲨 · 人脸日常跑",
  description: "账号预检与官方采集完成后，确认计划下单。",
  unitPrice: "0.25",
  priceUnit: "元/公里",
  enabled: true,
  available: true,
  version: 1,
  capabilities: ["LOOKUP", "CREATE", "SYNC"],
};
const lookup = {
  suggested: { planOptionId: "p1", fenceOptionId: "f1" },
  choices: [
    { field: "planOptionId", value: "p1", label: "本人日常跑计划" },
    { field: "fenceOptionId", value: "f1", label: "本人校园区域" },
  ],
  notice: "账号与计划已绑定；人脸凭据仅在服务器保管，照片由官方采集站点处理。",
};
const consentLabel = "我有权使用此账号及信息，并授权提交";
const faceConsentLabel =
  "我同意在官方采集站点提交本人人脸信息；取消勾选会撤销本次授权";
let browser,
  context,
  seq = 1,
  preflights = 0,
  collections = 0,
  checks = 0,
  launches = 0,
  confirmations = 0,
  quoteSeq = 0,
  collected = false,
  loseCollect = false,
  loseCheck = false;
const sessions = new Map(),
  tickets = new Map(),
  unexpected = [],
  errors = [];
function faceState(s, state) {
  s.state = state;
  if (["UNKNOWN", "REVOKED", "USED"].includes(state)) {
    s.face = null;
    s.lookup = null;
    return;
  }
  s.lookup = lookup;
  s.face = {
    collectionOrigin:
      state === "FACE_REQUIRED" ? null : "https://collect.example",
    status:
      state === "FACE_REQUIRED"
        ? null
        : {
            completed: state === "READY",
            fileCount: collected ? 2 : 0,
            minFileCount: 2,
            maxFileCount: 3,
          },
    canCollect: state === "FACE_REQUIRED",
    canCheck: ["FACE_PENDING", "FACE_RETRY"].includes(state),
    canLaunch: ["FACE_PENDING", "FACE_RETRY"].includes(state),
  };
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
  context = await browser.newContext({
    viewport: { width: 1440, height: 1080 },
  });
  context.on("page", (p) => p.on("pageerror", (e) => errors.push(e.message)));
  await context.route("**/*", async (route) => {
    const req = route.request(),
      url = new URL(req.url());
    if (url.origin === "https://collect.example") {
      // Intercepted hosted site: these tests never upload real photos or call a supplier.
      if (url.pathname === "/__done") {
        collected = true;
        return route.fulfill({
          contentType: "application/json",
          body: '{"simulated":true}',
        });
      }
      return route.fulfill({
        contentType: "text/html;charset=utf-8",
        body: `<html lang="zh-CN"><head><meta charset="utf-8"><title>模拟官方采集站点</title></head><body><h1>模拟官方采集站点</h1><p>测试数据，不上传任何照片。</p><button onclick="fetch('/__done',{method:'POST'}).then(()=>document.getElementById('done').textContent='模拟采集完成')">模拟完成本人采集</button><p id="done"></p></body></html>`,
      });
    }
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
        headers: { "Cache-Control": "no-store" },
        body: JSON.stringify({ code: 1, data, message: "操作成功" }),
      });
    assert.ok(
      !url.search.includes("mock-password") &&
        !url.search.includes("ticket=") &&
        !url.search.includes("face_token"),
    );
    if (ep === "/services") return reply({ records: [product], total: 1 });
    if (ep === "/services/13/account-sessions") {
      assert.deepEqual(body(), {
        mode: "PASSWORD",
        account: "13800138000",
        authorizedAccount: true,
      });
      const id = `1f80fbc1-999b-4f0e-a7cb-${String(seq++).padStart(12, "0")}`;
      const s = {
        id,
        productId: 13,
        mode: "PASSWORD",
        state: "CREATED",
        accountLabel: "13***00",
        expiresAt: new Date(Date.now() + 600000).toISOString(),
        lookup: null,
        canRefreshRules: false,
        face: null,
      };
      sessions.set(id, s);
      return reply(s);
    }
    const match = ep.match(/^\/service-account-sessions\/([^/]+)(?:\/(.*))?$/);
    if (match) {
      const s = sessions.get(match[1]);
      assert.ok(s);
      const action = match[2];
      if (method === "DELETE") {
        faceState(s, "REVOKED");
        return reply(null);
      }
      if (method === "GET") {
        if (s.state === "FACE_CHECKING") faceState(s, "FACE_RETRY");
        return reply(s);
      }
      if (action === "verify") {
        preflights++;
        assert.deepEqual(body(), { secret: "mock-password" });
        faceState(s, "FACE_REQUIRED");
        return reply(s);
      }
      if (action === "face-collection") {
        collections++;
        assert.deepEqual(body(), { authorizedFace: true });
        assert.equal(s.state, "FACE_REQUIRED");
        if (loseCollect) {
          loseCollect = false;
          faceState(s, "UNKNOWN");
          return route.abort("failed");
        }
        faceState(s, "FACE_PENDING");
        return reply(s);
      }
      if (action === "face-check") {
        checks++;
        assert.deepEqual(body(), {});
        if (loseCheck) {
          loseCheck = false;
          faceState(s, "FACE_CHECKING");
          return route.abort("failed");
        }
        faceState(s, collected ? "READY" : "FACE_PENDING");
        return reply(s);
      }
      if (action === "face-launch") {
        launches++;
        assert.deepEqual(body(), {});
        assert.equal(s.state, "FACE_PENDING");
        const ticket = String(launches).padStart(64, "a");
        tickets.set(ticket, s.id);
        return reply({
          sessionId: s.id,
          ticket,
          expiresAt: new Date(Date.now() + 60000).toISOString(),
        });
      }
    }
    if (ep === "/service-face-collection/launch") {
      assert.equal(method, "POST");
      assert.equal(url.search, "");
      assert.equal(req.headers().authorization, undefined);
      assert.match(
        req.headers()["content-type"],
        /application\/x-www-form-urlencoded/,
      );
      const form = new URLSearchParams(req.postData());
      assert.deepEqual([...form.keys()], ["sessionId", "ticket"]);
      assert.equal(tickets.get(form.get("ticket")), form.get("sessionId"));
      tickets.delete(form.get("ticket"));
      return route.fulfill({
        status: 303,
        headers: {
          Location: "https://collect.example/official?c=simulated-link",
          "Cache-Control": "no-store",
          "Referrer-Policy": "no-referrer",
        },
        body: "",
      });
    }
    if (ep === "/services/13/quotes") {
      const data = body();
      assert.ok(data.accountSessionId);
      assert.equal(sessions.get(data.accountSessionId)?.state, "READY");
      assert.deepEqual(data.fields, {
        planOptionId: "p1",
        fenceOptionId: "f1",
        runTime: "08:00",
      });
      assert.equal(data.quantity, 1);
      assert.equal(data.distance, "2");
      assert.equal(data.authorizedAccount, true);
      assert.deepEqual(data.taskTimes, []);
      assert.ok(!JSON.stringify(data).includes("mock-password"));
      quoteSeq++;
      return reply({
        id: `8f80fbc1-999b-4f0e-a7cb-${String(quoteSeq).padStart(12, "0")}`,
        action: "CREATE",
        state: "READY",
        amount: "0.50",
        quantity: 1,
        accountLabel: "13***00",
        expiresAt: new Date(Date.now() + 240000).toISOString(),
        warnings: ["模拟官方采集检查完成；只有确认后才下单扣费。"],
      });
    }
    if (/^\/service-order-operations\/[^/]+\/confirm$/.test(ep)) {
      confirmations++;
      return reply({
        id: ep.split("/")[2],
        orderId: "8f80fbc1-999b-4f0e-a7cb-900000000001",
        action: "CREATE",
        state: "SUCCEEDED",
        amount: "0.50",
      });
    }
    if (ep === "/service-orders") return reply({ records: [], total: 0, current: 1, size: 20 });
    unexpected.push(`${method} ${ep}`);
    return route.abort();
  });
  const page = await context.newPage();
  page.setDefaultTimeout(30000);
  page.setDefaultNavigationTimeout(60000);
  await page.clock.install();
  const button = (name) => page.getByRole("button", { name, exact: true });
  async function open() {
    collected = false;
    await page.goto(`${base}/__heishaface`);
    await button("选择服务").click();
    await page.getByText(consentLabel, { exact: true }).click();
    await page.getByLabel("本人手机号", { exact: true }).fill("13800138000");
    await page.getByLabel("服务密码", { exact: true }).fill("mock-password");
    await button("预检本人账号").dblclick();
    await page
      .getByText("账号预检通过。请阅读采集说明并明确授权，再获取官方入口。", {
        exact: true,
      })
      .waitFor();
    assert.equal(await page.getByLabel("服务密码", { exact: true }).count(), 0);
    assert.equal(await button("预览金额并下单").isDisabled(), true);
    assert.equal(await button("获取官方采集入口").isDisabled(), true);
    await page.getByText(faceConsentLabel, { exact: true }).click();
  }
  await open();
  await button("获取官方采集入口").dblclick();
  await page.getByText("https://collect.example", { exact: true }).waitFor();
  assert.equal(collections, 1);
  assert.equal(preflights, 1);
  assert.equal(await button("预览金额并下单").isDisabled(), true);
  await button("检查采集状态").click();
  assert.equal(await button("预览金额并下单").isDisabled(), true);
  assert.equal(checks, 1);
  const [popup] = await Promise.all([
    context.waitForEvent("page"),
    button("打开官方采集页").click(),
  ]);
  await popup
    .getByRole("heading", { name: "模拟官方采集站点", exact: true })
    .waitFor();
  assert.equal(await popup.evaluate(() => window.opener), null);
  assert.equal(await popup.evaluate(() => document.referrer), "");
  assert.equal(launches, 1);
  assert.equal(collections, 1);
  assert.equal(tickets.size, 0);
  await popup
    .getByRole("button", { name: "模拟完成本人采集", exact: true })
    .click();
  await popup.getByText("模拟采集完成", { exact: true }).waitFor();
  await popup.close();
  await button("检查采集状态").click();
  await page
    .getByText("官方采集已核实，可选择计划并预览金额。", { exact: true })
    .waitFor();
  await page.getByLabel("购买次数", { exact: true }).fill("1");
  assert.equal(await button("预览金额并下单").isEnabled(), true);
  mkdirSync("../.cache/native-service-ui", { recursive: true });
  await page.screenshot({
    path: "../.cache/native-service-ui/heisha-face-ready-desktop.png",
    fullPage: true,
    animations: "disabled",
  });
  await button("预览金额并下单").click();
  await page
    .getByRole("dialog", { name: "确认本次操作", exact: true })
    .getByText("¥0.50", { exact: true })
    .waitFor();
  assert.equal(confirmations, 0);
  await button("确认并下单").dblclick();
  await page
    .getByRole("heading", { name: "我的服务订单", exact: true })
    .waitFor();
  assert.equal(confirmations, 1);
  await open();
  loseCollect = true;
  const before = collections;
  await button("获取官方采集入口").click();
  await page
    .getByText("请求结果尚未确认，请检查原授权状态，不要重复创建采集链接。", {
      exact: true,
    })
    .waitFor();
  assert.equal(await button("获取官方采集入口").isDisabled(), true);
  await button("检查授权状态").click();
  await page
    .getByText("采集请求结果未知，本会话不会再次派发。请核实后重新授权。", {
      exact: true,
    })
    .waitFor();
  assert.equal(collections, before + 1);
  assert.equal(await button("获取官方采集入口").count(), 0);
  assert.equal(await button("预览金额并下单").isDisabled(), true);
  await open();
  await button("获取官方采集入口").click();
  loseCheck = true;
  await button("检查采集状态").click();
  await page
    .getByText("请求结果尚未确认，请检查原授权状态，不要重复创建采集链接。", {
      exact: true,
    })
    .waitFor();
  const checkCount = checks;
  await button("检查授权状态").click();
  await page
    .getByText(
      "本次采集状态检查未能确认，可手动再次检查；不会重新创建采集或下单。",
      { exact: true },
    )
    .waitFor();
  assert.equal(checks, checkCount);
  assert.equal(await button("预览金额并下单").isDisabled(), true);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => document.documentElement.classList.add("dark"));
  assert.ok(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  );
  await page.clock.fastForward(5000);
  await button("打开官方采集页").scrollIntoViewIfNeeded();
  await page.screenshot({
    path: "../.cache/native-service-ui/heisha-face-pending-mobile-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  for (const name of ["打开官方采集页", "检查采集状态"])
    assert.ok((await button(name).boundingBox()).height >= 44);
  assert.equal(
    await page
      .evaluate(
        () => JSON.stringify(localStorage) + JSON.stringify(sessionStorage),
      )
      .then((v) => /mock-password|ticket|faceToken/.test(v)),
    false,
  );
  await page.getByText(faceConsentLabel, { exact: true }).click();
  await button("预检本人账号").waitFor();
  assert.equal(
    await page.getByLabel("服务密码", { exact: true }).inputValue(),
    "",
  );
  assert.equal(await button("预览金额并下单").isDisabled(), true);
  await open();
  await button("获取官方采集入口").click();
  await page.clock.fastForward(11 * 60000);
  await page.getByText("授权已过期，请重新授权。", { exact: true }).waitFor();
  assert.equal(await button("打开官方采集页").count(), 0);
  assert.equal(await button("预览金额并下单").isDisabled(), true);
  assert.deepEqual(unexpected, []);
  assert.deepEqual(errors, []);
  console.log(
    "PASS Heisha hosted face: explicit consent → one collection → approved-origin one-use POST handoff → authoritative check → session-bound paid order; lost responses/revoke/expiry/mobile dark verified; all supplier/hosted APIs simulated",
  );
} finally {
  await context?.close();
  await browser?.close();
  await server.close();
}
