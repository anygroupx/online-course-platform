import assert from "node:assert/strict";
import { existsSync, mkdirSync } from "node:fs";
import path from "node:path";
import { chromium } from "playwright";
import { createTestServer as createServer } from './fixtures/test-server.mjs';

const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;padding:24px"><div id="app"></div><script type="module">
import { createApp,h } from 'vue'; import {createRouter,createMemoryHistory,RouterView} from 'vue-router'; import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css'; import 'element-plus/theme-chalk/dark/css-vars.css'; import '/src/styles/variables.scss'; import '/src/styles/global.css'; import '/src/styles/element-overrides.scss';
import Orders from '/src/views/ServiceOrders.vue'; import {applyAuthSession} from '/src/utils/authSession.js';
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7});
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/service-orders',component:Orders}]}); await router.push('/service-orders');await router.isReady();createApp({render:()=>h(RouterView)}).use(router).use(ElementPlus).mount('#app');
</script></body></html>`;
const server = await createServer({
  logLevel: "error",
  server: { host: "127.0.0.1", port: 0 },
  plugins: [
    {
      name: "flash-task-fixture",
      configureServer(vite) {
        vite.middlewares.use(async (req, res, next) => {
          if (!req.url?.startsWith("/__flash")) return next();
          res.setHeader("Content-Type", "text/html;charset=utf-8");
          res.end(await vite.transformIndexHtml(req.url, html));
        });
      },
    },
  ],
});
const order = {
  id: "d99a05ee-0e2e-4e07-ae5f-36b4b7af3130",
  providerType: "flash",
  project: "sdxy",
  title: "闪电 · 闪动校园",
  accountLabel: "13***26",
  quantity: 3,
  completed: null,
  distance: "2",
  paidAmount: "0.75",
  refundedAmount: "0.00",
  status: "PAUSED",
  pendingOperationId: null,
  version: 1,
  createTime: "2026-09-07T10:00:00",
  actions: ["RESUME", "DELAY", "DELAY_TASK", "CHANGE_TIME", "REFUND"],
};
const quote = {
  id: "4029ce1c-9a12-45e4-b80e-7f00b8f937e8",
  orderId: order.id,
  action: "DELAY_TASK",
  state: "READY",
  title: order.title,
  quantity: 0,
  quantityUnit: "次",
  amount: "0.00",
  amountLabel: "无需额外扣款",
  expiresAt: "2099-01-01T00:00:00",
};
let browser,
  confirms = 0,
  previews = 0,
  checks = 0;
const unexpected = [],
  errors = [];
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
    viewport: { width: 1280, height: 900 },
  });
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
    const endpoint = url.pathname.slice(4),
      body = () => JSON.parse(req.postData() || "{}");
    const respond = (data) =>
      route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({ code: 1, message: "操作成功", data }),
      });
    if (endpoint === "/service-orders")
      return respond({ records: [order], total: 1 });
    if (endpoint === `/service-orders/${order.id}/logs`)
      return respond({
        items: [
          { id: "TASK-1", time: "2026-09-10 08:00:00", status: "未开始" },
          { id: "TASK-2", time: "2026-09-09 08:00:00", status: "已完成" },
          { id: "TASK-3", time: "2026-09-08 08:00:00", status: "待确认" },
        ],
        page: 1,
        hasMore: false,
      });
    if (endpoint === `/service-orders/${order.id}/quotes`) {
      previews++;
      assert.deepEqual(body(), {
        action: "DELAY_TASK",
        quantity: 0,
        fields: { taskId: "TASK-1", page: "1" },
      });
      return respond(quote);
    }
    if (endpoint === `/service-order-operations/${quote.id}/confirm`) {
      confirms++;
      await new Promise((resolve) => setTimeout(resolve, 200));
      quote.state = "UNKNOWN";
      quote.errorCategory = "TIMEOUT";
      order.pendingOperationId = quote.id;
      order.status = "CONFIRMING";
      order.actions = [];
      return respond(quote);
    }
    if (endpoint === `/service-order-operations/${quote.id}`) {
      checks++;
      return respond(quote);
    }
    unexpected.push(`${req.method()} ${endpoint}`);
    return route.abort();
  });
  await page.goto(`${base}/__flash`);
  await page
    .getByRole("heading", { name: "我的服务订单", exact: true })
    .waitFor();
  assert.equal(
    await page.getByRole("button", { name: "延期此任务", exact: true }).count(),
    0,
  );
  await page.getByRole("button", { name: "执行记录", exact: true }).click();
  const logs = page.getByRole("dialog", { name: "执行记录", exact: true });
  // Opening the drawer does not mean its asynchronous task request has rendered yet.
  await logs.getByRole("button", { name: "延期此任务", exact: true }).waitFor();
  assert.equal(
    await logs.getByRole("button", { name: "延期此任务", exact: true }).count(),
    1,
  );
  await logs.getByRole("button", { name: "延期此任务", exact: true }).click();
  const confirm = page.getByRole("dialog", {
    name: "确认本次操作",
    exact: true,
  });
  await confirm.waitFor();
  await confirm
    .getByText(
      "仅延期所选任务，不会延期整笔订单。新的执行时间以确认结果及刷新后的记录为准。",
      { exact: true },
    )
    .waitFor();
  assert.ok((await confirm.innerText()).includes("本次无需扣款"));
  assert.equal(confirms, 0);
  assert.equal(previews, 1);
  const output = path.resolve("../.cache/native-service-ui");
  mkdirSync(output, { recursive: true });
  await page.screenshot({
    path: `${output}/flash-single-task-delay.png`,
    fullPage: true,
    animations: "disabled",
  });
  await confirm
    .getByRole("button", { name: "确认操作", exact: true })
    .dblclick();
  await confirm.getByText("待人工核对", { exact: true }).waitFor();
  assert.equal(confirms, 1);
  await confirm
    .getByRole("button", { name: "检查提交结果", exact: true })
    .click();
  assert.equal(checks, 1);
  assert.equal(confirms, 1);
  await confirm
    .getByRole("button", { name: "稍后查看订单", exact: true })
    .click();
  await page
    .getByRole("button", { name: "检查提交结果", exact: true })
    .waitFor();
  assert.equal(
    await page.getByRole("button", { name: "延期此任务", exact: true }).count(),
    0,
  );
  assert.deepEqual(unexpected, []);
  assert.deepEqual(errors, []);
  console.log(
    "PASS Flash per-task delay: eligible task only → exact owned task preview → explicit no-charge confirmation → UNKNOWN without replay; all APIs simulated",
  );
} finally {
  await browser?.close();
  await server.close();
}
