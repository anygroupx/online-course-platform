/** Real Vue/Element Plus UI against an offline API fixture; never contacts a supplied plugin host. */
import assert from "node:assert/strict";
import { existsSync, mkdirSync } from "node:fs";
import { resolve } from "node:path";
import { createServer } from "vite";
import { chromium } from "playwright";

const sources = [
  ["P01", "0510闪电三件套.zip", "闪电三件套", "NATIVE_PARTIAL", "flash"],
  ["P02", "benzs.zip", "Benz 同步桥", "EXISTING", "27"],
  ["P03", "heisha_sdxy_user.zip", "黑鲨闪动校园", "NATIVE_PARTIAL", "heisha"],
  ["P04", "jiguang_user.zip", "极光", "NATIVE_PARTIAL", "jiguang"],
  ["P05", "ssbenz.zip", "永夜 / ssbenz", "NEEDS_PROTOCOL", null],
  ["P06", "sxdk_tw.zip", "实习计划套娃", "NATIVE_PARTIAL", "sxdk_tw"],
  ["P07", "syyv5 (1).tar.gz", "syyv5 多项目中心", "NATIVE_PARTIAL", "syyv5"],
  ["P08", "toc模板专属对接鲸鱼文件.zip", "鲸鱼 · TOC 版", "NEEDS_PROTOCOL", null],
  ["P09", "二开appui对接.zip", "appui 实习平台", "NEEDS_PROTOCOL", null],
  ["P10", "无心闪动0312.zip", "无心闪动", "NATIVE_PARTIAL", "wuxin"],
  ["P11", "通用鲸鱼套娃对接.zip", "鲸鱼 · 通用版", "DUPLICATE", null],
  ["P12", "雷电_v1.2_二开对接.zip", "雷电 v1.2", "NEEDS_PROTOCOL", null],
];
const descriptors = sources.map(([id, archive, name, integrationStatus, providerType]) => ({
  id, archive, name, integrationStatus, providerType,
  category: id === "P07" ? "项目与账务" : ["P06", "P09"].includes(id) ? "实习计划" : id === "P02" ? "课程同步" : "运动计划",
  evidenceLevel: integrationStatus === "NEEDS_PROTOCOL" ? "OPAQUE" : id === "P11" ? "DUPLICATE" : "PLAINTEXT",
  duplicateOf: id === "P11" ? "P08" : null,
  observedFeatures: id === "P07" ? ["项目密钥与余额兑换", "流水与工单", "OpenAPI"]
    : id === "P02" ? ["分类与商品同步", "增量批量进度"] : ["商品或项目目录", "计划与日志", "只读之外的动作需独立验证"],
  blockers: id === "P11" ? ["25 个文件中 24 个与 P08 字节一致", "唯一差异为 bdlp 页面，不重复注册连接器"]
    : ["静态证据不代表上游业务全功能已验收", "财务、订单、学生授权均未开放"],
  evidence: [`${providerType || "plugin"}/api.php:1–127`, "来源行号由本地解压报告记录"],
  availableCapabilities: ["flash", "heisha", "jiguang", "wuxin"].includes(providerType) ? ["CATALOG", ...(id === "P04" ? ["SCHOOLS"] : [])]
    : id === "P02" ? ["COURSE_CATALOG", "BATCH_PROGRESS"] : id === "P07" ? ["PROJECT_CENTER"] : [],
  projects: id === "P01" ? [{ id: "sdxy", name: "闪动校园" }, { id: "ydsjxy", name: "运动世界校园" }, { id: "xbd", name: "校步点" }] : [],
}));
const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;background:var(--bg-body);font-family:'Segoe UI','Noto Sans CJK SC',sans-serif"><div id="app" style="padding:20px"></div><script type="module">
import { createApp } from 'vue'; import ElementPlus from 'element-plus'; import 'element-plus/dist/index.css';
import 'element-plus/theme-chalk/dark/css-vars.css'; import '/src/styles/variables.scss'; import '/src/styles/element-overrides.scss'; import '/src/styles/global.css'; import '/src/styles/fluent-spatial.scss'; import '/src/styles/responsive.scss'; import Page from '/src/views/AdminPluginIntegrations.vue';
import { applyAuthSession } from '/src/utils/authSession.js';
applyAuthSession({ token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7 });
createApp(Page).use(ElementPlus).mount('#app');</script></body></html>`;
const server = await createServer({
  logLevel: "error", server: { host: "127.0.0.1", port: 0, strictPort: false },
  plugins: [{ name: "plugin-test-page", configureServer(vite) {
    vite.middlewares.use(async (req, res, next) => {
      if (req.url !== "/__plugin_integrations") return next();
      res.setHeader("Content-Type", "text/html; charset=utf-8");
      res.end(await vite.transformIndexHtml(req.url, html));
    });
  } }],
});
let browser;
try {
  await server.listen();
  const baseURL = `http://127.0.0.1:${server.httpServer.address().port}`;
  const executablePath = process.env.BROWSER_PATH || (existsSync("/snap/bin/chromium") ? "/snap/bin/chromium" : undefined);
  browser = await chromium.launch({ executablePath, headless: true, args: ["--no-sandbox", "--disable-dev-shm-usage"] });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1050 } });
  const pageErrors = [], unexpectedRequests = [], calls = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));
  let failNextCatalog = false, delayNextCatalog = false, releaseCatalog, heishaConfigured = false;
  const failureId = "12345678-1234-1234-1234-123456789012";
  await page.route("**/*", async (route) => {
    const url = new URL(route.request().url());
    if (url.origin !== baseURL) { unexpectedRequests.push(url.href); return route.abort(); }
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const path = url.pathname.slice(4), method = route.request().method();
    calls.push({ path, method, params: Object.fromEntries(url.searchParams) });
    assert.equal(method, "GET", "new integration UI must never mutate an API");
    const respond = (data, status = 200, extra = {}) => route.fulfill({ status, contentType: "application/json",
      headers: { "Cache-Control": "no-store" }, body: JSON.stringify({ code: status === 200 ? 1 : -1, message: status === 200 ? "成功" : "第三方服务响应超时", data, ...extra }) });
    if (path === "/admin/plugin-integrations") return respond(descriptors);
    const match = path.match(/^\/admin\/plugin-integrations\/(P\d{2})\/providers(?:\/(\d+)\/(catalog|schools))?$/);
    if (!match) { unexpectedRequests.push(path); return respond(null, 404); }
    const [, pluginId, providerId, action] = match;
    if (!action) {
      const type = { P01: "flash", P03: "heisha", P04: "jiguang" }[pluginId];
      const records = pluginId === "P03" && !heishaConfigured ? [] : [
        { id: 7, name: "演示禁用配置", providerType: type, status: 0, verified: true },
        { id: 8, name: "演示待启用配置", providerType: type, status: 2, verified: true },
        { id: 9, name: "演示只读接口 A", providerType: type, status: 1, verified: true },
        { id: 10, name: "演示只读接口 B", providerType: type, status: 1, verified: true },
      ];
      return respond({ records, total: records.length, current: 1, size: 20 });
    }
    if (action === "catalog") {
      if (failNextCatalog) { failNextCatalog = false; return respond({ reason: "TIMEOUT" }, 502, { errorId: failureId }); }
      if (delayNextCatalog) { delayNextCatalog = false; await new Promise((resolve) => { releaseCatalog = resolve; }); }
      if (pluginId === "P03") return respond([]);
      if (pluginId === "P01") return respond([{ id: url.searchParams.get("project") || "sdxy", name: "项目报价（演示）", unitPrice: "0.123456", priceUnit: url.searchParams.get("project") === "sdxy" ? "元/次" : "元/公里（倍率另计）" }]);
      return respond([{ id: "1", name: providerId === "10" ? "二号接口项目（演示）" : "晨跑项目（演示）", unitPrice: "0.15", priceUnit: "元/公里" },
        { id: "2", name: "日常项目（演示）", unitPrice: "0.125000", priceUnit: "元/公里" }]);
    }
    const number = Number(url.searchParams.get("page"));
    return respond({ items: Array.from({ length: number === 1 ? 20 : 1 }, (_, i) => ({ id: `school-${number}-${i}`, name: `测试大学 ${number}-${i + 1}` })), page: number, pageSize: 20, hasMore: number === 1 });
  });
  await page.goto(`${baseURL}/__plugin_integrations`);
  await page.getByTestId("plugin-P12").waitFor();
  assert.equal(await page.locator(".plugin-row").count(), 12);
  assert.equal(calls.filter(c => /\/(catalog|schools)$/.test(c.path)).length, 0);
  await page.getByLabel("搜索插件", { exact: true }).fill("鲸鱼");
  assert.equal(await page.locator(".plugin-row").count(), 2);
  await page.getByTestId("plugin-P11").getByRole("button", { name: "查看证据" }).click();
  await page.getByText("25 个文件中 24 个与 P08 字节一致", { exact: true }).waitFor();
  assert.equal(await page.getByRole("tab", { name: "商品 / 报价" }).count(), 0);
  await page.keyboard.press("Escape"); await page.locator(".plugin-integration-drawer").waitFor({ state: "hidden" });
  await page.getByLabel("搜索插件", { exact: true }).fill("");

  const open = async (id) => {
    await page.getByTestId(`plugin-${id}`).getByRole("button", { name: "只读接入", exact: true }).click();
    await page.locator(".provider-selector .el-select").waitFor();
    await page.getByText("本页不填写密钥", { exact: false }).waitFor();
  };
  const chooseProvider = async (label = "演示只读接口 A") => {
    await page.locator(".provider-selector .el-select").click();
    await page.getByRole("option", { name: new RegExp(label) }).click();
  };
  await open("P04");
  assert.equal(await page.getByRole("button", { name: "读取商品目录", exact: true }).isEnabled(), false);
  await page.locator(".provider-selector .el-select").click();
  assert.equal(await page.getByRole("option", { name: /演示禁用配置/ }).getAttribute("aria-disabled"), "true");
  assert.equal(await page.getByRole("option", { name: /演示待启用配置/ }).getAttribute("aria-disabled"), "true");
  await page.getByRole("option", { name: /演示只读接口 A/ }).click();
  await page.getByRole("button", { name: "读取商品目录", exact: true }).click();
  await page.getByText("晨跑项目（演示）", { exact: true }).waitFor();
  await page.getByText("¥0.125", { exact: true }).waitFor();
  assert.equal(calls.filter(c => c.path.endsWith("/catalog")).length, 1);
  await page.getByRole("tab", { name: "学校检索", exact: true }).click();
  assert.equal(calls.filter(c => c.path.endsWith("/schools")).length, 0);
  await page.getByLabel("学校关键词", { exact: true }).fill("大学");
  await page.getByRole("button", { name: "查询学校", exact: true }).click();
  await page.getByText("测试大学 1-1", { exact: true }).waitFor();
  await page.getByRole("button", { name: "下一页", exact: true }).click();
  await page.getByText("测试大学 2-1", { exact: true }).waitFor();
  assert.equal(await page.getByRole("button", { name: "下一页", exact: true }).isEnabled(), false);
  assert.deepEqual(calls.filter(c => c.path.endsWith("/schools")).map(c => c.params), [
    { page: "1", pageSize: "20", keyword: "大学" }, { page: "2", pageSize: "20", keyword: "大学" },
  ]);
  await page.getByRole("tab", { name: "商品 / 报价", exact: true }).click();
  failNextCatalog = true;
  await page.getByRole("button", { name: "读取商品目录", exact: true }).click();
  await page.getByText(`错误 ID：${failureId}`, { exact: true }).waitFor();
  assert.equal(await page.getByText("晨跑项目（演示）", { exact: true }).count(), 0);

  delayNextCatalog = true;
  await page.getByRole("button", { name: "读取商品目录", exact: true }).click();
  await page.waitForFunction(() => document.querySelector(".catalog-workspace button.is-loading"));
  await chooseProvider("演示只读接口 B");
  while (!releaseCatalog) await new Promise(resolve => setTimeout(resolve, 10));
  releaseCatalog();
  await page.getByRole("button", { name: "读取商品目录", exact: true }).click();
  await page.getByText("二号接口项目（演示）", { exact: true }).waitFor();
  assert.equal(await page.getByText("晨跑项目（演示）", { exact: true }).count(), 0);
  const screenshots = resolve("../.cache/plugin-integration-ui"); mkdirSync(screenshots, { recursive: true });
  await page.waitForFunction(() => document.querySelectorAll(".el-message").length === 0);
  await page.screenshot({ animations: "disabled", path: `${screenshots}/desktop-workspace.png` });
  await page.keyboard.press("Escape"); await page.locator(".plugin-integration-drawer").waitFor({ state: "hidden" });

  await open("P01"); await chooseProvider();
  await page.locator(".catalog-workspace .el-select").click();
  await page.getByRole("option", { name: "运动世界校园", exact: true }).click();
  await page.getByRole("button", { name: "读取项目报价", exact: true }).click();
  await page.getByText("¥0.123456", { exact: true }).waitFor();
  await page.getByText("元/公里（倍率另计）", { exact: true }).waitFor();
  assert.equal(calls.filter(c => c.path.includes("P01") && c.path.endsWith("/catalog")).at(-1).params.project, "ydsjxy");
  await page.keyboard.press("Escape"); await page.locator(".plugin-integration-drawer").waitFor({ state: "hidden" });

  await open("P03");
  await page.getByText("没有找到对应配置。", { exact: false }).waitFor();
  assert.equal(await page.getByRole("button", { name: "读取商品目录", exact: true }).isEnabled(), false);
  heishaConfigured = true;
  await page.getByRole("button", { name: "刷新配置", exact: true }).click();
  await chooseProvider();
  await page.getByRole("button", { name: "读取商品目录", exact: true }).click();
  await page.getByText("上游返回了空商品目录", { exact: true }).waitFor();
  await page.keyboard.press("Escape"); await page.locator(".plugin-integration-drawer").waitFor({ state: "hidden" });
  await page.getByLabel("搜索插件", { exact: true }).fill("极光");
  await page.screenshot({ animations: "disabled", path: `${screenshots}/desktop-overview.png`, fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByTestId("plugin-P04").waitFor();
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, "mobile overview must not overflow");
  await open("P04"); await chooseProvider();
  await page.getByRole("button", { name: "读取商品目录", exact: true }).click();
  await page.getByText("晨跑项目（演示）", { exact: true }).waitFor();
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, "mobile drawer must not overflow");
  await page.screenshot({ animations: "disabled", path: `${screenshots}/mobile-workspace.png` });
  await page.evaluate(() => document.documentElement.classList.add("dark"));
  await page.screenshot({ animations: "disabled", path: `${screenshots}/dark-mobile-workspace.png` });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  const darkBackground = await page.locator(".plugin-integration-drawer").evaluate(el => getComputedStyle(el).backgroundColor);
  assert.notEqual(darkBackground, "rgb(255, 255, 255)", "dark drawer must not have a white background");
  assert.deepEqual(unexpectedRequests, []); assert.deepEqual(pageErrors, []);
  console.log(`PASS: 12-source inventory, permissions-oriented UI, no auto outbound, query contracts, stale response cancellation, error/empty states, mobile; ${calls.length} mocked GET requests; screenshots ${screenshots}`);
} finally { await browser?.close(); await server.close(); }
