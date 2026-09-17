/** Real Vue/Element Plus UI against an offline API fixture; never contacts a supplied plugin host. */
import assert from "node:assert/strict";
import { existsSync, mkdirSync } from "node:fs";
import { resolve } from "node:path";
import { createTestServer as createServer } from './fixtures/test-server.mjs';
import { chromium } from "playwright";

const sources = [
  ["P01", "0510闪电三件套.zip", "闪电三件套", "NATIVE_PARTIAL", "flash"],
  ["P02", "benzs.zip", "Benz 同步桥", "EXISTING", "27"],
  ["P03", "heisha_sdxy_user.zip", "黑鲨闪动校园", "NATIVE_PARTIAL", "heisha"],
  ["P04", "jiguang_user.zip", "极光", "NATIVE_PARTIAL", "jiguang"],
  ["P05", "ssbenz.zip", "永夜 / ssbenz", "NATIVE_PARTIAL", "ssbenz_xbd"],
  ["P06", "sxdk_tw.zip", "实习计划套娃", "NATIVE_PARTIAL", "sxdk_tw"],
  ["P07", "syyv5 (1).tar.gz", "syyv5 多项目中心", "NATIVE_PARTIAL", "syyv5"],
  ["P08", "toc模板专属对接鲸鱼文件.zip", "鲸鱼 · TOC 版", "NATIVE_PARTIAL", "jingyu"],
  ["P09", "二开appui对接.zip", "AppUI 实习打卡", "NATIVE_PARTIAL", "appui"],
  ["P10", "无心闪动0312.zip", "无心闪动", "NATIVE_PARTIAL", "wuxin"],
  ["P11", "通用鲸鱼套娃对接.zip", "鲸鱼 · 通用版", "DUPLICATE", null],
  ["P12", "雷电_v1.2_二开对接.zip", "雷电 v1.2", "NATIVE_PARTIAL", "leidian"],
];
const jingyuPendingNotice = "体育项目的图形验证、规则更新和补跑功能尚未开放。";
const descriptors = sources.map(([id, archive, name, integrationStatus, providerType]) => ({
  id, archive, name, integrationStatus, providerType,
  category: id === "P07" ? "项目与账务" : ["P06", "P09"].includes(id) ? "实习计划" : id === "P02" ? "课程同步" : "运动计划",
  evidenceLevel: ["P05", "P08", "P09", "P12"].includes(id) ? "MIXED" : integrationStatus === "NEEDS_PROTOCOL" ? "OPAQUE" : id === "P11" ? "DUPLICATE" : "PLAINTEXT",
  duplicateOf: id === "P11" ? "P08" : null,
  observedFeatures: id === "P07" ? ["项目密钥与余额兑换", "流水与工单", "OpenAPI"]
    : id === "P02" ? ["分类与商品同步", "增量批量进度"]
    : id === "P08" ? ["Keep 与步道乐跑报价、账号查询与下单", "逐次任务安排、延期与暂停恢复", "双编号核对与人工退款入账"]
    : ["商品或项目目录", "计划与日志", "只读之外的动作需独立验证"],
  blockers: id === "P11" ? ["25 个文件中 24 个与 P08 字节一致", "唯一差异为 bdlp 页面，不重复注册连接器"]
    : id === "P08" ? [jingyuPendingNotice, "运动与体育项目的学校归属、图形验证、规则更新、补跑计费仍待补齐；设备授权和推送没有独立可核实接口", "退款没有原子数量回执，必须核对实际数量；不执行 PHP 或定时脚本，不重发结果不确定的订单"]
    : ["静态证据不代表上游业务全功能已验收", "财务、订单、学生授权均未开放"],
  evidence: id === "P08" ? ["jingyu/api.php（静态核对的固定表单协议）", "index/keep.php", "index/bdlp.php", "jingyu/jingyu_tables.sql"]
    : [`${providerType || "plugin"}/api.php:1–127`, "来源行号由本地解压报告记录"],
  serviceCapabilities: ["flash", "heisha", "jiguang", "wuxin", "sxdk_tw", "ssbenz_xbd", "appui", "leidian", "jingyu"].includes(providerType) ? ["CREATE", "SYNC"] : [],
  availableCapabilities: ["flash", "heisha", "jiguang", "wuxin", "ssbenz_xbd", "appui", "leidian", "jingyu"].includes(providerType) ? ["CATALOG", ...(id === "P04" ? ["SCHOOLS"] : [])]
    : id === "P02" ? ["COURSE_CATALOG", "BATCH_PROGRESS"] : id === "P07" ? ["PROJECT_CENTER"] : [],
  projects: id === "P01" ? [{ id: "sdxy", name: "闪动校园" }, { id: "ydsjxy", name: "运动世界校园" }, { id: "xbd", name: "校步点" }]
    : id === "P08" ? [{ id: "keep", name: "Keep 自由跑" }, { id: "bdlp", name: "步道乐跑" }, { id: "yyd", name: "校园运动" }]
    : id === "P12" ? ["步道乐跑", "步道人脸跑", "步道自由跑", "乐健体育"].map((name, i) => ({ id: String(i + 1), name })) : [],
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
  const screenshots = resolve("../.cache/plugin-integration-ui"); mkdirSync(screenshots, { recursive: true });
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
      const type = descriptors.find(item => item.id === pluginId)?.providerType;
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
      if (pluginId === "P08") {
        const project = url.searchParams.get("project"); assert.ok(["keep", "bdlp", "yyd"].includes(project));
        return respond([{ id: project, name: project === "keep" ? "Keep 自由跑" : project === "yyd" ? "校园运动" : "步道乐跑", unitPrice: "0.01", priceUnit: project === "bdlp" ? "元/次" : "元/次·公里" }]);
      }
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
  for (const id of ["P05", "P08", "P09", "P12"]) {
    await page.getByTestId(`plugin-${id}`).getByRole("button", { name: "配置与使用", exact: true }).waitFor();

  }
  const jingyuRow = page.getByTestId("plugin-P08");
  await jingyuRow.getByText("已实现部分功能", { exact: true }).waitFor();
  assert.equal(await page.locator(".archive-name").count(), 0, "archive metadata is not user-facing copy");
  await jingyuRow.getByText("商品 / 报价", { exact: true }).waitFor();
  assert.equal(await jingyuRow.getByText("待补协议", { exact: true }).count(), 0);
  for (const id of ["P11"])
    assert.equal(await page.getByTestId(`plugin-${id}`).getByRole("button", { name: "配置与使用", exact: true }).count(), 0);
  assert.equal(calls.filter(c => /\/(catalog|schools)$/.test(c.path)).length, 0);
  await page.getByLabel("搜索插件", { exact: true }).fill("鲸鱼");
  assert.equal(await page.locator(".plugin-row").count(), 2);
  await jingyuRow.getByRole("button", { name: "功能说明", exact: true }).click();
  await page.getByText(jingyuPendingNotice, { exact: true }).waitFor();
  assert.equal(await page.getByRole("tab", { name: "商品 / 报价", exact: true }).count(), 1);
  assert.equal(await page.getByRole("button", { name: /读取商品目录|读取项目报价/ }).count(), 0);
  assert.equal(calls.filter(call => call.path.includes("/P08/")).length, 0, "opening evidence alone must not query configured services");
  await page.screenshot({ animations: "disabled", path: `${screenshots}/jingyu-partial-evidence-desktop.png` });
  await page.locator(".plugin-integration-drawer .el-drawer__close-btn").click(); await page.locator(".plugin-integration-drawer").waitFor({ state: "hidden" });
  await page.getByTestId("plugin-P11").getByRole("button", { name: "功能说明" }).click();
  await page.getByText("请从鲸鱼运动服务入口配置和上架。", { exact: true }).waitFor();
  assert.equal(await page.getByRole("tab", { name: "商品 / 报价" }).count(), 0);
  await page.locator(".plugin-integration-drawer .el-drawer__close-btn").click(); await page.locator(".plugin-integration-drawer").waitFor({ state: "hidden" });
  await page.getByLabel("搜索插件", { exact: true }).fill("");

  const open = async (id) => {
    await page.getByTestId(`plugin-${id}`).getByRole("button", { name: "配置与使用", exact: true }).click();
    await page.locator(".provider-selector .el-select").waitFor();
    await page.getByText("选择配置不会自动查询商品", { exact: false }).waitFor();
    await page.getByRole("tab", { name: "商品 / 报价", exact: true }).click();
  };
  const chooseProvider = async (label = "演示只读接口 A") => {
    await page.locator(".provider-selector .el-select").click();
    await page.getByRole("option", { name: new RegExp(label) }).click();
  };
  await page.getByTestId("plugin-P06").getByRole("button", { name: "配置与使用", exact: true }).click();
  await page.locator(".provider-selector .el-select").waitFor();
  assert.equal(await page.getByRole("tab", { name: "商品 / 报价", exact: true }).count(), 0, "native-only internship does not invent a catalog");
  assert.equal(await page.getByRole("button", { name: "上架此服务商品", exact: true }).isEnabled(), false);
  await chooseProvider();
  assert.equal(await page.getByRole("button", { name: "上架此服务商品", exact: true }).isEnabled(), true);
  assert.equal(calls.filter(call => /P06.*(catalog|schools)/.test(call.path)).length, 0);
  await page.locator(".plugin-integration-drawer .el-drawer__close-btn").click(); await page.locator(".plugin-integration-drawer").waitFor({ state: "hidden" });
  await page.getByTestId("plugin-P11").getByRole("button", { name: "前往鲸鱼服务", exact: true }).click();
  await page.getByRole("dialog", { name: "鲸鱼运动服务 · 使用与配置", exact: true }).waitFor();
  assert.equal(calls.filter(call => call.path.includes("/P11/")).length, 0);
  await page.locator(".plugin-integration-drawer .el-drawer__close-btn").click(); await page.locator(".plugin-integration-drawer").waitFor({ state: "hidden" });
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
  await page.waitForFunction(() => document.querySelectorAll(".el-message").length === 0);
  await page.screenshot({ animations: "disabled", path: `${screenshots}/desktop-workspace.png` });
  await page.locator(".plugin-integration-drawer .el-drawer__close-btn").click(); await page.locator(".plugin-integration-drawer").waitFor({ state: "hidden" });

  await open("P01"); await chooseProvider();
  await page.locator(".catalog-workspace .el-select").click();
  await page.getByRole("option", { name: "运动世界校园", exact: true }).click();
  await page.getByRole("button", { name: "读取项目报价", exact: true }).click();
  await page.getByText("¥0.123456", { exact: true }).waitFor();
  await page.getByText("元/公里（倍率另计）", { exact: true }).waitFor();
  assert.equal(calls.filter(c => c.path.includes("P01") && c.path.endsWith("/catalog")).at(-1).params.project, "ydsjxy");
  await page.locator(".plugin-integration-drawer .el-drawer__close-btn").click(); await page.locator(".plugin-integration-drawer").waitFor({ state: "hidden" });

  // Directory reads stay manual and P11 reuses P08 rather than duplicating service calls.
  await open("P08"); await chooseProvider();
  for (const [project, label, unit] of [["keep", "Keep 自由跑", "元/次·公里"], ["bdlp", "步道乐跑", "元/次"], ["yyd", "校园运动", "元/次·公里"]]) {
    await page.locator(".catalog-workspace .el-select").click();
    await page.getByRole("option", { name: label, exact: true }).click();
    await page.getByRole("button", { name: "读取商品目录", exact: true }).click();
    await page.locator(".catalog-workspace .quote-list").getByText(label, { exact: true }).waitFor();
    await page.locator(".catalog-workspace .quote-list").getByText(unit, { exact: true }).waitFor();
    assert.equal(calls.filter(c => c.path.includes("/P08/") && c.path.endsWith("/catalog")).at(-1).params.project, project);
  }
  await page.screenshot({ animations: "disabled", path: `${screenshots}/jingyu-project-catalog-desktop.png` });
  assert.equal(calls.filter(c => c.path.includes("/P11/")).length, 0);
  await page.locator(".plugin-integration-drawer .el-drawer__close-btn").click(); await page.locator(".plugin-integration-drawer").waitFor({ state: "hidden" });

  await open("P03");
  await page.getByText("尚未配置此服务。", { exact: false }).waitFor();
  assert.equal(await page.getByRole("button", { name: "读取商品目录", exact: true }).isEnabled(), false);
  heishaConfigured = true;
  await page.getByRole("button", { name: "刷新配置", exact: true }).click();
  await chooseProvider();
  await page.getByRole("button", { name: "读取商品目录", exact: true }).click();
  await page.getByText("未返回商品目录", { exact: true }).waitFor();
  await page.locator(".plugin-integration-drawer .el-drawer__close-btn").click(); await page.locator(".plugin-integration-drawer").waitFor({ state: "hidden" });
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
  console.log(`PASS: 12-source inventory, current Jingyu partial capability boundary, permissions-oriented UI, no auto outbound, query contracts, stale response cancellation, error/empty states, mobile; ${calls.length} mocked GET requests; screenshots ${screenshots}`);
} finally { await browser?.close(); await server.close(); }
