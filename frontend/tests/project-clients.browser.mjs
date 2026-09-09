import assert from "node:assert/strict";
import { existsSync, mkdirSync } from "node:fs";
import { chromium } from "playwright";
import { createServer } from "vite";
const customerId = "bab323d2-ccbd-45fe-a88f-08b4ce19c4ba",
  operationId = "bbb323d2-ccbd-45fe-a88f-08b4ce19c4ba";
const fullKey = "npo_" + "a".repeat(64),
  password = "fixture-current-password";
const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0"><div id="app"></div><script type="module">
import {createApp} from 'vue';import ElementPlus from 'element-plus';import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';import Page from '/src/views/ProjectClients.vue';import {applyAuthSession} from '/src/utils/authSession.js';applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7});createApp(Page).use(ElementPlus).mount('#app');</script></body></html>`;
const server = await createServer({
  logLevel: "error",
  server: { host: "127.0.0.1", port: 0 },
  plugins: [
    {
      name: "project-client-fixture",
      configureServer(vite) {
        vite.middlewares.use(async (req, res, next) => {
          if (req.url !== "/__project_clients") return next();
          res.setHeader("Content-Type", "text/html;charset=utf-8");
          res.end(await vite.transformIndexHtml(req.url, html));
        });
      },
    },
  ],
});
let browser,
  op,
  losePreview = true,
  loseConfirm = true,
  loseKey = false,
  client = null,
  keyState = {
    configured: false,
    prefix: null,
    access: "READ_ONLY",
    version: 0,
    expiresAt: null,
    subject: "OWNER",
  };
const writes = [],
  reads = [],
  unexpected = [],
  errors = [],
  records = [];
try {
  await server.listen();
  const base = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({
    executablePath:
      process.env.BROWSER_PATH ||
      (existsSync("/snap/bin/chromium") ? "/snap/bin/chromium" : undefined),
    args: ["--no-sandbox", "--disable-dev-shm-usage"],
  });
  const page = await browser.newPage({
    viewport: { width: 1440, height: 1080 },
  });
  page.setDefaultTimeout(45000);
  page.setDefaultNavigationTimeout(90000);
  page.on("pageerror", (e) => errors.push(e.message));
  await page.route("**/*", async (route) => {
    const req = route.request(),
      url = new URL(req.url());
    if (url.origin !== base) {
      unexpected.push(url.origin);
      return route.abort();
    }
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const path = url.pathname.slice(4),
      method = req.method(),
      respond = (data) =>
        route.fulfill({
          contentType: "application/json",
          body: JSON.stringify({ code: 1, data }),
        }),
      body = () => JSON.parse(req.postData() || "{}");
    if (method === "GET") reads.push(path);
    else writes.push({ path, method, body: body() });
    if (path === "/project-clients" && method === "GET")
      return respond({
        records: client ? [client] : [],
        total: client ? 1 : 0,
      });
    if (path === "/project-clients/stats")
      return respond({
        customers: client ? 1 : 0,
        active: client?.status === "ACTIVE" ? 1 : 0,
        appliedOperations: records.filter((r) => r.state === "APPLIED").length,
        totalDebited: client ? "2.50" : "0.00",
        totalReturned: "0.00",
      });
    if (path === "/project-client-operations" && method === "GET")
      return respond({ records: [...records], total: records.length });
    if (path === "/project-clients/catalog")
      return respond({
        records: [
          {
            id: 1,
            title: "本地项目 · 演示",
            unitPrice: "0.25",
            available: true,
            version: 1,
          },
        ],
        total: 1,
      });
    if (path === "/project-clients/quotes") {
      const f = body();
      assert.equal(f.consent, true);
      assert.equal(f.projectId, 1);
      assert.match(f.requestId, /^[a-f0-9-]{36}$/);
      op = {
        id: operationId,
        requestId: f.requestId,
        clientId: customerId,
        projectId: 1,
        projectTitle: "本地项目 · 演示",
        label: client?.label || f.label,
        action: f.action,
        state: "READY",
        units: f.units,
        unitPrice: "0.25",
        amount: f.action === "OPEN" ? "2.50" : "1.00",
        clientBalanceAfter: null,
        walletBalanceAfter: null,
        expiresAt: "2099-01-01T12:00:00",
        notice: "尚未结算；仅本地客户子账，不是上游项目余额。",
      };
      records.unshift(op);
      if (losePreview) {
        losePreview = false;
        return route.abort("connectionfailed");
      }
      return respond(op);
    }
    if (path === `/project-client-operations/${operationId}/confirm`) {
      assert.deepEqual(body(), { consent: true });
      op = {
        ...op,
        state: "APPLIED",
        clientBalanceAfter: "10.000000",
        walletBalanceAfter: "97.50",
        notice: "本地客户与平台钱包已经同事务结算，未调用供应商。",
      };
      records[0] = op;
      client = {
        id: customerId,
        projectId: 1,
        projectTitle: "本地项目 · 演示",
        label: op.label,
        status: "ACTIVE",
        version: 1,
        balance: "10.000000",
        unitPrice: "0.250000",
        refundableUnits: "10.000000",
        refundBudget: "2.50",
      };
      if (loseConfirm) {
        loseConfirm = false;
        return route.abort("connectionfailed");
      }
      return respond(op);
    }
    if (
      path.startsWith("/project-client-operations/by-request/") ||
      path === `/project-client-operations/${operationId}`
    )
      return respond(op);
    if (path === `/project-clients/${customerId}/status`) {
      const f = body();
      assert.equal(f.version, client.version);
      client = { ...client, status: f.status, version: client.version + 1 };
      return respond(client);
    }
    if (path.startsWith("/project-api-keys/")) {
      const subject = decodeURIComponent(path.split("/").at(-1));
      if (method === "GET") return respond({ ...keyState, subject });
      const f = body();
      assert.equal(f.password, password);
      assert.equal(f.consent, true);
      assert.equal(f.version, keyState.version);
      if (method === "POST") {
        keyState = {
          ...keyState,
          configured: true,
          prefix: fullKey.slice(0, 12),
          access: f.access,
          version: keyState.version + 1,
          expiresAt: "2099-01-01T12:00:00",
          subject,
        };
        if (loseKey) {
          loseKey = false;
          return route.abort("connectionfailed");
        }
        return respond({ secret: fullKey, settings: keyState });
      }
      if (method === "DELETE") {
        keyState = {
          ...keyState,
          configured: false,
          prefix: null,
          version: keyState.version + 1,
        };
        return respond(keyState);
      }
    }
    if (path === "/project-api-calls")
      return respond({
        records: [
          {
            action: "CLIENTS",
            outcome: "OK",
            createdAt: "2026-09-09T10:00:00",
          },
        ],
        total: 1,
      });
    unexpected.push(method + " " + path);
    return route.fulfill({ status: 404, body: "Unexpected fixture route" });
  });
  await page.goto(`${base}/__project_clients`);
  await page.getByRole("button", { name: "新建客户", exact: true }).waitFor();
  assert.equal(writes.length, 0);
  assert.equal(
    reads.some((p) => p.includes("project-api-keys")),
    false,
  );
  await page.getByRole("button", { name: "新建客户", exact: true }).click();
  let dialog = page.getByRole("dialog", { name: "开通客户", exact: true });
  await dialog.locator(".el-select__wrapper").click();
  await page.getByRole("option", { name: "本地项目 · 演示" }).click();
  await dialog
    .getByRole("textbox", { name: "客户别名", exact: true })
    .fill("示例客户甲");
  await dialog
    .getByRole("textbox", { name: "初始本地额度（可为0）" })
    .fill("10");
  await dialog
    .getByText("我已确认客户和用途，知晓此操作只处理本平台本地额度", {
      exact: true,
    })
    .click();
  await dialog.getByRole("button", { name: "预览金额（不扣款）" }).click();
  await dialog
    .getByText(
      "预览请求结果尚未确认，请用原请求编号检查结果。没有自动确认或重建客户。",
      { exact: true },
    )
    .waitFor();
  assert.equal(writes.length, 1);
  assert.equal(client, null);
  await dialog.getByRole("button", { name: "检查原操作结果" }).click();
  await dialog.getByText("¥2.50", { exact: true }).waitFor();
  assert.equal(writes.length, 1);
  assert.equal(
    await dialog.getByRole("button", { name: "确认本次结算" }).isDisabled(),
    true,
  );
  await dialog
    .getByText("我已核对客户、数量及平台金额，确认这一次本地结算", {
      exact: true,
    })
    .click();
  await dialog
    .getByRole("button", { name: "确认本次结算" })
    .click({ clickCount: 2 });
  await dialog
    .getByText("结算请求结果尚未确认，请查询原操作；不自动重复扣款或开户。", {
      exact: true,
    })
    .waitFor();
  assert.equal(writes.filter((w) => w.path.endsWith("/confirm")).length, 1);
  assert.equal(
    await dialog.getByRole("button", { name: "确认本次结算" }).count(),
    0,
  );
  const before = writes.length;
  await dialog.getByRole("button", { name: "检查原操作结果" }).click();
  await dialog
    .getByText("本地客户与平台钱包已经同事务结算，未调用供应商。", {
      exact: true,
    })
    .waitFor();
  assert.equal(writes.length, before);
  await dialog.getByRole("button", { name: "关闭", exact: true }).click();
  await page.getByRole("button", { name: "充值额度", exact: true }).waitFor();
  mkdirSync("../.cache/native-service-ui", { recursive: true });
  await page.waitForFunction(
    () => document.querySelectorAll(".el-message").length === 0,
  );
  await page.screenshot({
    path: "../.cache/native-service-ui/project-clients-desktop.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "暂停客户", exact: true }).click();
  await page
    .getByRole("dialog", { name: "暂停本地客户" })
    .getByRole("button", { name: "确认变更" })
    .click();
  await page.getByRole("button", { name: "恢复客户", exact: true }).waitFor();
  assert.equal(
    await page
      .getByRole("button", { name: "充值额度", exact: true })
      .isDisabled(),
    true,
  );
  assert.equal(
    await page
      .getByRole("button", { name: "转回平台", exact: true })
      .isEnabled(),
    true,
  );
  await page
    .getByRole("button", { name: "项目 OpenAPI 密钥", exact: true })
    .click();
  const keys = page.getByRole("dialog", {
    name: "本平台项目 OpenAPI 密钥",
    exact: true,
  });
  await keys.getByText("未配置", { exact: true }).waitFor();
  const keyWrites = writes.length;
  assert.equal(keyState.configured, false);
  async function keyInput() {
    await keys
      .getByRole("textbox", { name: "当前登录密码", exact: true })
      .fill(password);
    await keys
      .getByText("我确认接收方和权限范围，并知晓轮换后旧密钥立即失效", {
        exact: true,
      })
      .click();
  }
  await keyInput();
  await keys.getByRole("button", { name: "签发密钥（仅显示一次）" }).click();
  await keys.getByRole("textbox", { name: "本次签发的项目密钥" }).waitFor();
  assert.equal(writes.length, keyWrites + 1);
  assert.equal(
    await keys
      .getByRole("textbox", { name: "本次签发的项目密钥" })
      .inputValue(),
    fullKey,
  );
  await keys.getByRole("button", { name: "我已保存，清除本次显示" }).click();
  assert.equal(
    await keys
      .getByRole("textbox", { name: "当前登录密码", exact: true })
      .inputValue(),
    "",
  );
  assert.equal(
    (await page.locator("body").innerText()).includes(fullKey),
    false,
  );
  loseKey = true;
  await keyInput();
  await keys.getByRole("button", { name: "轮换密钥（仅显示一次）" }).click();
  await keys
    .getByText(
      "签发结果尚未确认；请检查当前密钥版本，不自动轮换或重试。密钥明文无法从查询中恢复。",
      { exact: true },
    )
    .waitFor();
  const afterLost = writes.length;
  await keys.getByRole("button", { name: "检查密钥设置" }).click();
  await keys.getByText(`版本 ${keyState.version}`, { exact: false }).waitFor();
  assert.equal(writes.length, afterLost);
  assert.equal(
    await keys.getByRole("textbox", { name: "本次签发的项目密钥" }).count(),
    0,
  );
  await keyInput();
  await keys.getByRole("button", { name: "撤销此密钥" }).click();
  await keys.getByText("未配置", { exact: true }).waitFor();
  await keys.getByRole("button", { name: "关闭并清除敏感输入" }).click();
  await keys.waitFor({ state: "hidden" });
  await page.waitForFunction(
    () =>
      document.querySelectorAll(".el-message").length === 0 &&
      [...document.querySelectorAll(".el-overlay")].every(
        (node) =>
          getComputedStyle(node).display === "none" ||
          node.getBoundingClientRect().height === 0,
      ),
  );
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => document.documentElement.classList.add("dark"));
  await page.screenshot({
    path: "../.cache/native-service-ui/project-clients-mobile-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  assert.ok(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  );
  const storage = await page.evaluate(
    () => JSON.stringify(localStorage) + JSON.stringify(sessionStorage),
  );
  assert.equal(storage.includes(fullKey), false);
  assert.equal(storage.includes(password), false);
  await page.getByRole("button", { name: "查看 OpenAPI 调用记录" }).click();
  await page
    .getByRole("dialog", { name: "项目 OpenAPI 调用记录" })
    .getByText("CLIENTS", { exact: true })
    .waitFor();
  assert.equal(
    writes.some((w) =>
      /service-projects|project-accounts|external/.test(w.path),
    ),
    false,
  );
  assert.deepEqual(unexpected, []);
  assert.deepEqual(errors, []);
  console.log(
    "PASS native downstream clients: real Vue multiple-customer screen, explicit initial funding quote/confirm, lost preview and commit GET-only recovery, suspension, one-time hashed-key UI issuance/rotation/revocation, local-only stats/history, mobile/dark; simulated APIs only.",
  );
} finally {
  await browser?.close();
  await server.close();
}
