import assert from "node:assert/strict";
import { existsSync, mkdirSync } from "node:fs";
import path from "node:path";
import { chromium } from "playwright";
import { createTestServer as createServer } from './fixtures/test-server.mjs';

const html = `<!doctype html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;padding:24px"><div id="app"></div>
<script type="module">
import {createApp,h} from 'vue';import {createRouter,createMemoryHistory,RouterView} from 'vue-router';import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';
import Store from '/src/views/ServiceStore.vue';import Orders from '/src/views/ServiceOrders.vue';import Admin from '/src/views/AdminServiceProducts.vue';import {applyAuthSession} from '/src/utils/authSession.js';
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7});
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/services',component:Store},{path:'/service-orders',component:Orders},{path:'/admin/service-products',component:Admin},{path:'/admin/service-orders',component:Orders,meta:{serviceAdmin:true}}]});
await router.push(new URLSearchParams(location.search).get('page')||'/services');await router.isReady();createApp({render:()=>h(RouterView)}).use(router).use(ElementPlus).mount('#app');
</script></body></html>`;
const server = await createServer({
  logLevel: "error",
  server: { host: "127.0.0.1", port: 0, strictPort: false },
  plugins: [
    {
      name: "native-service-fixture",
      configureServer(vite) {
        vite.middlewares.use(async (req, res, next) => {
          if (!req.url?.startsWith("/__native_services")) return next();
          res.setHeader("Content-Type", "text/html;charset=utf-8");
          res.end(await vite.transformIndexHtml(req.url, html));
        });
      },
    },
  ],
});
const uuid = "70fe9178-8f36-434e-8200-f4d1c9ed82d0";
const qid = "85ea431d-cfe1-4e06-8a68-1f343f50f34f";
const product = {
  id: 1,
  providerId: 9,
  providerType: "jiguang",
  project: "default",
  remoteProductId: "1",
  title: "极光 · 晨间计划",
  description: "自选次数，按公里计价；订单支持增次与剩余次数退款。",
  unitPrice: "0.25",
  priceUnit: "元/公里",
  enabled: true,
  available: true,
  version: 0,
  capabilities: ["CREATE", "SYNC", "REFUND", "ADD_TIMES"],
};
const order = {
  id: uuid,
  title: product.title,
  accountLabel: "20***01",
  providerType: "jiguang",
  project: "default",
  status: "ACTIVE",
  quantity: 10,
  completed: 2,
  distance: "2.00",
  paidAmount: "5.00",
  refundedAmount: "0.00",
  pendingOperationId: null,
  createTime: "2026-09-06T12:00:00",
  actions: ["REFUND", "ADD_TIMES"],
};
let created = false,
  confirmations = 0,
  previews = [],
  mutations = [],
  adminSaves = [],
  quotes = new Map();
let browser;
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
  page.setDefaultTimeout(30000);
  page.setDefaultNavigationTimeout(60000);
  const unexpected = [],
    errors = [];
  page.on("pageerror", (e) => {
    errors.push(e.message);
    console.error("BROWSER ERROR:", e.message);
  });
  await page.route("**/*", async (route) => {
    const req = route.request(),
      url = new URL(req.url());
    if (url.origin !== base) {
      unexpected.push(url.origin);
      return route.abort();
    }
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const endpoint = url.pathname.slice(4),
      method = req.method();
    const body = () => JSON.parse(req.postData() || "{}");
    const respond = (data, status = 200) =>
      route.fulfill({
        status,
        contentType: "application/json",
        body: JSON.stringify({ code: 1, data, message: "操作成功" }),
      });
    if (endpoint === "/services")
      return respond({ records: [product], total: 1 });
    if (endpoint === "/services/1/quotes") {
      const data = body();
      previews.push(data);
      assert.equal(data.authorizedAccount, true);
      assert.equal(data.quantity, 10);
      assert.equal(data.distance, "2");
      assert.equal(data.fields.studentAccount, "2026001");
      assert.equal(data.amount, undefined);
      const quote = {
        id: qid,
        orderId: null,
        action: "CREATE",
        state: "READY",
        title: product.title,
        quantity: 10,
        amount: "5.00",
        amountLabel: "本次余额扣款",
        expiresAt: "2099-01-01T00:00:00",
      };
      quotes.set(qid, quote);
      return respond(quote);
    }
    if (
      endpoint.startsWith("/service-order-operations/") &&
      endpoint.endsWith("/confirm")
    ) {
      confirmations++;
      mutations.push(endpoint);
      const id = endpoint.split("/")[2];
      const quote = quotes.get(id);
      assert.ok(quote);
      await new Promise((r) => setTimeout(r, 250));
      if (quote.action === "CREATE") {
        created = true;
        quote.state = "SUCCEEDED";
        quote.orderId = uuid;
      } else {
        quote.state = "UNKNOWN";
        quote.orderId = uuid;
        order.pendingOperationId = id;
        order.status = "CONFIRMING";
        order.actions = [];
      }
      return respond(quote);
    }
    if (endpoint.startsWith("/service-order-operations/") && method === "GET")
      return respond(quotes.get(endpoint.split("/")[2]));
    if (endpoint === "/service-orders")
      return respond({
        records: created ? [order] : [],
        total: created ? 1 : 0,
      });
    if (endpoint === `/service-orders/${uuid}/quotes`) {
      const data = body();
      assert.equal(data.action, "REFUND");
      const quote = {
        id: "2d9d2c47-fec8-4f87-b8ad-cbde9e2ba036",
        orderId: uuid,
        action: "REFUND",
        state: "READY",
        title: product.title,
        quantity: 6,
        amount: "3.00",
        amountLabel: "预计退款上限",
        expiresAt: "2099-01-01T00:00:00",
      };
      quotes.set(quote.id, quote);
      return respond(quote);
    }
    if (endpoint === `/service-orders/${uuid}/events`) return respond([]);
    if (endpoint === "/admin/service-products" && method === "GET")
      return respond({
        records: adminSaves.length ? [product] : [],
        total: adminSaves.length,
      });
    if (endpoint === "/admin/api-providers")
      return respond({
        records: [
          {
            id: 9,
            name: "已授权极光接口",
            providerType: "jiguang",
            status: 1,
            verifiedAt: "2026-09-06T00:00:00",
          },
        ],
        total: 1,
      });
    if (endpoint === "/admin/plugin-integrations/P04/providers/9/catalog")
      return respond([
        {
          id: "1",
          name: "上游晨间商品",
          unitPrice: "0.10",
          priceUnit: "元/公里",
        },
      ]);
    if (endpoint === "/admin/service-products" && method === "POST") {
      adminSaves.push(body());
      return respond(product);
    }
    unexpected.push(`${method} ${endpoint}`);
    return respond(null, 404);
  });
  await page.goto(`${base}/__native_services`);
  try {
    await page.getByRole("button", { name: "选择服务", exact: true }).click();
  } catch (error) {
    console.error("Simulated workflow initialization:", { errors, unexpected, body: await page.locator("body").innerText() });
    throw error;
  }
  await page
    .getByText("我有权使用此账号及信息，并授权提交", {
      exact: true,
    })
    .click();
  await page.getByPlaceholder("填写学校全称").fill("示例大学");
  await page.getByPlaceholder("填写本人姓名").fill("测试用户");
  await page.getByPlaceholder("填写学号").fill("2026001");
  assert.equal(previews.length, 0);
  await page.getByRole("button", { name: "预览金额并下单" }).click();
  await page.getByText("¥5.00", { exact: true }).waitFor();
  assert.equal(confirmations, 0);
  const output = path.resolve("../.cache/native-service-ui");
  mkdirSync(output, { recursive: true });
  await page.screenshot({
    path: `${output}/checkout-desktop.png`,
    fullPage: true,
  });
  await page
    .getByRole("button", { name: "确认并下单", exact: true })
    .dblclick();
  await page.getByRole("heading", { name: "我的服务订单" }).waitFor();
  assert.equal(confirmations, 1);
  assert.equal(previews.length, 1);
  await page.getByRole("button", { name: "取消并退款", exact: true }).click();
  await page.getByRole("button", { name: "确认退款", exact: true }).click();
  await page
    .getByRole("button", { name: "检查提交结果", exact: true })
    .last()
    .waitFor();
  assert.equal(confirmations, 2);
  await page
    .getByRole("button", { name: "检查提交结果", exact: true })
    .last()
    .click();
  assert.equal(confirmations, 2, "an unknown refund must never be replayed");
  await page.getByRole("button", { name: "稍后查看订单", exact: true }).click();
  await page.getByRole("dialog").waitFor({ state: "hidden" });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => document.documentElement.classList.add("dark"));
  await page.screenshot({
    path: `${output}/orders-mobile-dark.png`,
    fullPage: true,
  });
  assert.ok(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  );
  await page.goto(`${base}/__native_services?page=/admin/service-products`);
  await page.setViewportSize({ width: 1440, height: 1080 });
  await page.getByRole("button", { name: "上架服务商品", exact: true }).click();
  await page
    .getByRole("dialog", { name: "上架服务商品", exact: true })
    .getByRole("combobox")
    .first()
    .click();
  await page
    .getByRole("option", { name: "已授权极光接口 · 极光", exact: true })
    .click();
  await page.getByRole("button", { name: "读取目录", exact: true }).click();
  await page.getByText("先读取服务目录", { exact: true }).click();
  await page
    .getByRole("option", { name: "上游晨间商品 · 成本 ¥0.10", exact: true })
    .click();
  await page.getByPlaceholder("例如 0.25；不能低于成本价").fill("0.25");
  await page.locator(".el-switch__core").click();
  await page.getByRole("button", { name: "保存商品", exact: true }).click();
  await page.getByText("服务商品已保存", { exact: true }).waitFor();
  assert.equal(adminSaves.length, 1);
  assert.equal(adminSaves[0].enabled, true);
  assert.equal(adminSaves[0].unitPrice, "0.25");
  assert.equal(adminSaves[0].providerId, 9);
  assert.deepEqual(unexpected, []);
  assert.deepEqual(errors, []);
  console.log(
    `PASS native service workflow: storefront → parameters → preview → single confirmation → order → uncertain refund without replay; admin publishing; dark mobile; ${mutations.length} simulated confirmations; screenshots ${output}`,
  );
} finally {
  await browser?.close();
  await server.close();
}
