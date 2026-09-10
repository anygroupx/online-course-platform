import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import { chromium } from "playwright";
import { createTestServer as createServer } from "./fixtures/test-server.mjs";

const html = `<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body><div id="app"></div><script type="module">
import { createApp } from "vue";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import Page from "/src/views/Recharge.vue";
import { applyAuthSession } from "/src/utils/authSession.js";
applyAuthSession({ token: "test." + btoa(JSON.stringify({ exp: Date.now() / 1000 + 3600 })) + ".signature", userId: 7 });
createApp(Page).use(ElementPlus).mount("#app");
</script></body></html>`;

const server = await createServer({
  logLevel: "error",
  server: { host: "127.0.0.1", port: 0, strictPort: false },
  plugins: [{
    name: "recharge-card-test-page",
    configureServer(vite) {
      vite.middlewares.use(async (request, response, next) => {
        if (request.url !== "/__recharge_card") return next();
        response.setHeader("Content-Type", "text/html; charset=utf-8");
        response.end(await vite.transformIndexHtml(request.url, html));
      });
    },
  }],
});

let browser;
try {
  await server.listen();
  const baseURL = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({
    executablePath: process.env.BROWSER_PATH
      || (existsSync("/snap/bin/chromium") ? "/snap/bin/chromium" : undefined),
    headless: true,
    args: ["--no-sandbox", "--disable-dev-shm-usage"],
  });
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  const errors = [];
  const writes = [];
  page.on("pageerror", (error) => errors.push(error.message));

  await page.route("**/*", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.origin !== baseURL) return route.abort();
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const path = url.pathname.slice(4);
    const respond = (data, message = "操作成功") => route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({ code: 1, message, data }),
    });
    if (path === "/user/info" && request.method() === "GET") {
      return respond({ balance: "0.00", totalRecharge: "0.00" });
    }
    if (path.startsWith("/payment/orders") && request.method() === "GET") {
      return respond({ records: [] });
    }
    if (path === "/cards/recharge" && request.method() === "POST") {
      writes.push(request.postDataJSON());
      return respond("10.00", "充值成功");
    }
    return route.fulfill({ status: 404, body: `Unexpected mock API: ${request.method()} ${path}` });
  });

  await page.goto(`${baseURL}/__recharge_card`);
  await page.getByRole("tab", { name: "卡密充值" }).click();
  await page.getByPlaceholder("请输入16位卡号").fill("1234567890123456");
  await page.getByPlaceholder("请输入卡密（8到64位）").fill("0123456789abcdef0123456789abcdef");
  await page.locator(".card-recharge").getByRole("button", { name: "立即充值" }).click();

  await page.getByText("充值成功！到账金额：¥10.00").waitFor();
  assert.deepEqual(writes, [{
    cardNo: "1234567890123456",
    cardPassword: "0123456789abcdef0123456789abcdef",
  }]);
  assert.deepEqual(errors, []);
  assert.equal(await page.getByPlaceholder("请输入16位卡号").inputValue(), "");
  assert.equal(await page.getByPlaceholder("请输入卡密（8到64位）").inputValue(), "");
} finally {
  await browser?.close();
  await server.close();
}
