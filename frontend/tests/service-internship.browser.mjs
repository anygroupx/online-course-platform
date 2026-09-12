import assert from "node:assert/strict";
import { existsSync, mkdirSync } from "node:fs";
import path from "node:path";
import { chromium } from "playwright";
import { createTestServer as createServer } from './fixtures/test-server.mjs';
const html = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;padding:24px"><div id="app"></div><script type="module">
import {createApp,h} from 'vue';import {createRouter,createMemoryHistory,RouterView} from 'vue-router';import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';
import Store from '/src/views/ServiceStore.vue';import Orders from '/src/views/ServiceOrders.vue';import Admin from '/src/views/AdminServiceProducts.vue';import {applyAuthSession} from '/src/utils/authSession.js';
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7});
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/services',component:Store},{path:'/service-orders',component:Orders},{path:'/admin/service-products',component:Admin}]});await router.push(new URLSearchParams(location.search).get('page')||'/services');await router.isReady();createApp({render:()=>h(RouterView)}).use(router).use(ElementPlus).mount('#app');
</script></body></html>`;
const server = await createServer({
  logLevel: "error",
  server: { host: "127.0.0.1", port: 0 },
  plugins: [
    {
      name: "internship-fixture",
      configureServer(vite) {
        vite.middlewares.use(async (req, res, next) => {
          if (!req.url?.startsWith("/__internship")) return next();
          res.setHeader("Content-Type", "text/html;charset=utf-8");
          res.end(await vite.transformIndexHtml(req.url, html));
        });
      },
    },
  ],
});
const date = (offset) => {
  const today = new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Asia/Shanghai",
  }).format(new Date());
  return new Date(Date.parse(today + "T00:00:00Z") + offset * 86400000)
    .toISOString()
    .slice(0, 10);
};
const product = {
  id: 3,
  providerId: 13,
  providerType: "sxdk_tw",
  project: "gxy",
  remoteProductId: "gxy",
  title: "实习 · 工学云",
  description: "本人授权的实习周期服务，按服务日计费。",
  unitPrice: "0.25",
  priceUnit: "元/服务日",
  enabled: true,
  available: true,
  version: 1,
  capabilities: [
    "LOOKUP",
    "CREATE",
    "SYNC",
    "PAUSE",
    "RESUME",
    "EDIT_SCHEDULE",
    "RUN_NOW",
    "REPORT",
    "REFUND",
  ],
};
const order = {
  id: "a277c5ef-b520-40c8-9a5c-a598e5639f81",
  providerType: "sxdk_tw",
  project: "gxy",
  title: product.title,
  accountLabel: "st***26",
  quantity: 3,
  quantityUnit: "天",
  completed: null,
  distance: null,
  paidAmount: "0.75",
  refundedAmount: "0.00",
  status: "ACTIVE",
  pendingOperationId: null,
  version: 1,
  createTime: "2026-09-07T10:00:00",
  actions: ["PAUSE", "EDIT_SCHEDULE", "RUN_NOW", "REPORT", "REFUND"],
};
const publicFields = {
  name: "测试本人",
  gwName: "软件实习",
  address: "已授权实习地址",
  jobAddress: "实习单位地址",
  lat: "28.1",
  lng: "112.1",
  province: "示例省",
  city: "示例市",
};
let browser,
  created = false,
  seq = 1,
  lookups = 0,
  saves = 0;
const quotes = new Map(),
  actions = [],
  unexpected = [],
  errors = [];
function quote(action, quantity, amount) {
  const q = {
    id: `b513380c-df31-4924-bcf1-${String(seq++).padStart(12, "0")}`,
    orderId: action === "CREATE" ? null : order.id,
    title: product.title,
    action,
    state: "READY",
    quantity,
    quantityUnit: action === "RUN_NOW" || action === "REPORT" ? "次" : "天",
    amount,
    amountLabel: action === "REFUND" ? "预计未用服务日退款" : "本次余额扣款",
    expiresAt: "2099-01-01T00:00:00",
  };
  quotes.set(q.id, q);
  return q;
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
  const page = await browser.newPage({
    viewport: { width: 1440, height: 1080 },
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
      method = req.method(),
      body = () => JSON.parse(req.postData() || "{}");
    const respond = (data, status = 200) =>
      route.fulfill({
        status,
        contentType: "application/json",
        body: JSON.stringify({ code: 1, data, message: "操作成功" }),
      });
    if (endpoint === "/services")
      return respond({ records: [product], total: 1 });
    if (endpoint === "/services/3/lookup") {
      lookups++;
      assert.equal(body().password, "not-real-secret");
      assert.equal(body().act, undefined);
      return respond({
        suggested: publicFields,
        choices: [],
        notice: "资料已读取，请核对后填写服务周期。",
      });
    }
    if (endpoint === "/services/3/quotes") {
      const data = body();
      assert.equal(data.quantity, 0);
      assert.equal(data.distance, null);
      assert.equal(data.fields.account, "student-2026");
      assert.equal(data.fields.password, "not-real-secret");
      assert.equal(data.fields.name, "测试本人");
      assert.equal(data.fields.authCode, undefined);
      assert.equal(data.fields.runTime, undefined);
      assert.equal(data.schedule.endDate, date(2));
      order.schedule = data.schedule;
      return respond(quote("CREATE", 3, "0.75"));
    }
    if (
      endpoint.startsWith("/service-order-operations/") &&
      endpoint.endsWith("/confirm")
    ) {
      const q = quotes.get(endpoint.split("/")[2]);
      assert.ok(q);
      actions.push(q.action);
      await new Promise((resolve) => setTimeout(resolve, 200));
      q.state = "SUCCEEDED";
      q.orderId = order.id;
      if (q.action === "CREATE") created = true;
      if (q.action === "EDIT_SCHEDULE") {
        order.quantity = 5;
        order.schedule = { ...order.schedule, endDate: date(4) };
        order.paidAmount = "1.25";
      }
      if (q.action === "PAUSE") {
        order.status = "PAUSED";
        order.actions = [
          "RESUME",
          "EDIT_SCHEDULE",
          "RUN_NOW",
          "REPORT",
          "REFUND",
        ];
      }
      if (q.action === "RESUME") {
        order.status = "ACTIVE";
        order.actions = [
          "PAUSE",
          "EDIT_SCHEDULE",
          "RUN_NOW",
          "REPORT",
          "REFUND",
        ];
      }
      if (q.action === "REFUND") {
        order.status = "REFUNDED";
        order.refundedAmount = "1.25";
        order.actions = [];
      }
      return respond(q);
    }
    if (endpoint === "/service-orders")
      return respond({
        records: created ? [order] : [],
        total: created ? 1 : 0,
        current: 1, size: 20,
      });
    if (endpoint === `/service-orders/${order.id}/options`)
      return respond({
        suggested: publicFields,
        schedule: order.schedule,
        paidDates: Array.from({ length: order.quantity }, (_, i) => date(i)),
        choices: [],
        notice: "当前周期",
      });
    if (endpoint === `/service-orders/${order.id}/quotes`) {
      const data = body();
      if (data.action === "EDIT_SCHEDULE") {
        assert.equal(data.fields.password, "");
        assert.equal(data.fields.account, undefined);
        assert.equal(data.schedule.endDate, date(4));
        return respond(quote(data.action, 2, "0.50"));
      }
      if (data.action === "REPORT") {
        assert.equal(data.fields.reportType, "日报");
        assert.equal(data.fields.startDate, date(0));
        return respond(quote(data.action, 0, "0.00"));
      }
      if (data.action === "REFUND") return respond(quote("REFUND", 5, "1.25"));
      assert.ok(["PAUSE", "RESUME", "RUN_NOW"].includes(data.action));
      return respond(
        quote(
          data.action,
          data.action === "RUN_NOW" ? 1 : 0,
          data.action === "RUN_NOW" ? "0.25" : "0.00",
        ),
      );
    }
    if (endpoint === `/service-orders/${order.id}/logs`)
      return respond({
        items: [
          { id: "1", time: "2026-09-07 08:00:00", status: "考勤执行记录" },
        ],
        page: 1,
        hasMore: false,
      });
    if (endpoint === "/admin/service-products" && method === "GET")
      return respond({ records: [], total: 0 });
    if (endpoint === "/admin/api-providers")
      return respond({
        records: [
          {
            id: 13,
            name: "已授权实习直连接口",
            providerType: "sxdk_tw",
            status: 1,
            verifiedAt: "2026-09-07T00:00:00",
          },
        ],
        total: 1,
      });
    if (endpoint === "/admin/service-products" && method === "POST") {
      saves++;
      const data = body();
      assert.equal(data.project, "zxjy");
      assert.equal(data.remoteProductId, "zxjy");
      assert.equal(data.contractPrice.unitCost, "0.10");
      assert.equal(data.contractPrice.upstreamChecked, true);
      assert.equal(data.contractPrice.validUntil, date(30));
      assert.ok(data.contractPrice.evidence.length >= 10);
      assert.equal(data.unitPrice, "0.25");
      return respond(product);
    }
    unexpected.push(`${method} ${endpoint}`);
    return respond(null, 404);
  });
  await page.goto(`${base}/__internship`);
  await page.getByRole("button", { name: "选择服务", exact: true }).click();
  await page
    .getByText("我有权使用此账号及信息，并授权提交", {
      exact: true,
    })
    .click();
  await page.getByPlaceholder("填写本人实习账号").fill("student-2026");
  await page
    .getByLabel("实习账号密码", { exact: true })
    .fill("not-real-secret");
  await page
    .getByRole("button", { name: "读取本人实习资料", exact: true })
    .click();
  await page
    .getByText("资料已读取，请核对后填写服务周期。", { exact: true })
    .waitFor();
  assert.equal(lookups, 1);
  await page
    .getByRole("group", { name: "服务星期" })
    .getByText("周六", { exact: true })
    .click();
  await page
    .getByRole("group", { name: "服务星期" })
    .getByText("周日", { exact: true })
    .click();
  const end = page.getByPlaceholder("请选择截止日期");
  await end.fill(date(2));
  await end.press("Tab");
  await page.getByText("服务周期", { exact: true }).click();
  assert.equal(await page.getByLabel("购买次数", { exact: true }).count(), 0);
  assert.equal(
    await page.getByLabel("每次距离（公里）", { exact: true }).count(),
    0,
  );
  const output = path.resolve("../.cache/native-service-ui");
  mkdirSync(output, { recursive: true });
  await page
    .getByLabel("实习账号密码", { exact: true })
    .evaluate((input) => input.blur());
  await page.screenshot({
    path: `${output}/internship-checkout.png`,
    fullPage: true,
    animations: "disabled",
  });
  await page
    .getByRole("button", { name: "预览金额并下单", exact: true })
    .click();
  await page.getByText("¥0.75", { exact: true }).waitFor();
  await page
    .getByRole("dialog", { name: "确认本次操作" })
    .getByText(/本次 3 天/)
    .waitFor();
  assert.deepEqual(actions, []);
  await page
    .getByRole("button", { name: "确认并下单", exact: true })
    .dblclick();
  await page
    .getByRole("heading", { name: "我的服务订单", exact: true })
    .waitFor();
  assert.deepEqual(actions, ["CREATE"]);
  assert.equal(
    await page.evaluate(() =>
      JSON.stringify(localStorage).includes("not-real-secret"),
    ),
    false,
  );
  await page.getByText("累计已购服务日", { exact: true }).waitFor();
  assert.equal(await page.getByText("每次距离", { exact: true }).count(), 0);
  await page
    .getByRole("button", { name: "编辑周期 / 续期", exact: true })
    .click();
  const editor = page.getByRole("dialog", { name: "编辑实习周期 / 续期" });
  await editor.waitFor();
  assert.equal(
    await editor
      .getByLabel("更新密码（留空保留原凭据）", { exact: true })
      .inputValue(),
    "",
  );
  await editor.getByPlaceholder("请选择截止日期").fill(date(4));
  await editor.getByPlaceholder("请选择截止日期").press("Tab");
  await page.getByRole("button", { name: "预览续期金额", exact: true }).click();
  await page.getByText("¥0.50", { exact: true }).waitFor();
  await page.getByRole("button", { name: "确认操作", exact: true }).dblclick();
  await page
    .getByRole("dialog", { name: "确认本次操作" })
    .waitFor({ state: "hidden" });
  assert.deepEqual(actions, ["CREATE", "EDIT_SCHEDULE"]);
  for (const action of ["暂停", "恢复"]) {
    await page.getByRole("button", { name: action, exact: true }).click();
    await page.getByRole("button", { name: "确认操作", exact: true }).click();
    await page
      .getByRole("dialog", { name: "确认本次操作" })
      .waitFor({ state: "hidden" });
  }
  await page.getByRole("button", { name: "补交记录", exact: true }).click();
  const report = page.getByRole("dialog", { name: "补交本人已授权的记录" });
  await report.getByLabel("开始日期", { exact: true }).fill(date(0));
  await report.getByLabel("开始日期", { exact: true }).press("Tab");
  await report.getByLabel("结束日期", { exact: true }).fill(date(0));
  await report.getByLabel("结束日期", { exact: true }).press("Tab");
  await page.getByRole("button", { name: "预览本次操作", exact: true }).click();
  await page.getByRole("button", { name: "确认操作", exact: true }).click();
  await page
    .getByRole("dialog", { name: "确认本次操作" })
    .waitFor({ state: "hidden" });
  await page.waitForFunction(() => document.querySelectorAll(".el-message").length === 0);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => document.documentElement.classList.add("dark"));
  await page.screenshot({
    path: `${output}/internship-orders-mobile-dark.png`,
    fullPage: true,
    animations: "disabled",
  });
  assert.ok(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  );
  await page.getByRole("button", { name: "取消并退款", exact: true }).click();
  await page.getByRole("button", { name: "确认退款", exact: true }).click();
  await page.locator(".order-card .order-title").getByText("已退款", { exact: true }).waitFor();
  await page.goto(`${base}/__internship?page=/admin/service-products`);
  await page.setViewportSize({ width: 1440, height: 1080 });
  await page.getByRole("button", { name: "上架服务商品", exact: true }).click();
  const publish = page.getByRole("dialog", {
    name: "上架服务商品",
    exact: true,
  });
  await publish.getByRole("combobox").first().click();
  await page
    .getByRole("option", { name: "已授权实习直连接口 · 实习服务", exact: true })
    .click();
  await publish.getByPlaceholder("例如 0.25；不能低于成本价").fill("0.25");
  await publish
    .getByPlaceholder("填写合同成本，不是原插件的默认值")
    .fill("0.10");
  const until = publish.getByLabel("本次价格核对有效期（最长 90 天）", {
    exact: true,
  });
  await until.fill(date(30));
  await until.press("Tab");
  await publish
    .getByLabel("核对依据（至少 10 字，不包含凭据）", { exact: true })
    .fill("已核实上游合同单价与服务日取消退款规则");
  await publish
    .getByText(
      "我已核实成本、倍率和取消规则，认可按服务日计费与退款条款",
      { exact: true },
    )
    .click();
  await publish.locator(".el-switch__core").click();
  await publish
    .locator(".el-dialog__body")
    .evaluate((el) => (el.scrollTop = 0));
  await page.screenshot({
    path: `${output}/internship-contract-publishing.png`,
    fullPage: true,
    animations: "disabled",
  });
  await publish.getByRole("button", { name: "保存商品", exact: true }).click();
  await page.getByText("服务商品已保存", { exact: true }).waitFor();
  assert.equal(saves, 1);
  assert.deepEqual(unexpected, []);
  assert.deepEqual(errors, []);
  assert.deepEqual(actions, [
    "CREATE",
    "EDIT_SCHEDULE",
    "PAUSE",
    "RESUME",
    "REPORT",
    "REFUND",
  ]);
  console.log(
    "PASS internship native workflow: account lookup → day quote → one confirmation → renewal → pause/resume → report → cancellation; contract-price publishing without fake price endpoint; mobile dark; simulated APIs only",
  );
} finally {
  await browser?.close();
  await server.close();
}
