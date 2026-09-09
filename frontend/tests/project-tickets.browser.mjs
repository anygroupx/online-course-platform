import assert from "node:assert/strict";
import { existsSync, mkdirSync } from "node:fs";
import { chromium } from "playwright";
import { createTestServer as createServer } from './fixtures/test-server.mjs';
import { pngFixture } from "./fixtures/ticket-image.mjs";
const png = pngFixture();
// A visible drawer can still be translating into view; capture only after its finite CSS transitions settle.
async function settleDrawer(locator) {
  await locator.evaluate(async (element) => {
    await new Promise(requestAnimationFrame);
    const animations = new Set();
    for (let node = element; node; node = node.parentElement)
      for (const animation of node.getAnimations())
        if (animation.effect?.getTiming().iterations !== Infinity) animations.add(animation);
    await Promise.all([...animations].map((animation) => animation.finished.catch(() => {})));
  });
}
const accountId = "27c5c14d-2eba-4dd7-a023-52a49a3dcc6b";
const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;padding:24px"><div id="app"></div><script type="module">
import {createApp} from 'vue';import ElementPlus from 'element-plus';import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';
import Tickets from '/src/components/projectcenter/ProjectTickets.vue';import {applyAuthSession} from '/src/utils/authSession.js';applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7});createApp(Tickets,{admin:new URLSearchParams(location.search).has('admin'),accounts:[{accountId:'${accountId}',title:'项目甲 · 独立服务额度'}]}).use(ElementPlus).mount('#app');
</script></body></html>`;
const server = await createServer({
  logLevel: "error",
  server: { host: "127.0.0.1", port: 0 },
  plugins: [
    {
      name: "project-tickets-fixture",
      configureServer(vite) {
        vite.middlewares.use(async (req, res, next) => {
          if (!req.url?.startsWith("/__project_tickets")) return next();
          res.setHeader("Content-Type", "text/html;charset=utf-8");
          res.end(await vite.transformIndexHtml(req.url, html));
        });
      },
    },
  ],
});
let browser,
  context,
  seq = 0,
  loseConfirm = false,
  loseResolution = false;
const tickets = new Map(),
  operations = new Map(),
  writes = [],
  reads = [],
  unexpected = [],
  errors = [];
const id = () => `87c5c14d-2eba-4dd7-a023-${String(++seq).padStart(12, "0")}`;
const listing = (rows) => ({
  records: rows,
  total: rows.length,
  current: 1,
  size: 20,
});
function operation(ticket, action, content, result = null, hasAttachment = false) {
  const op = {
    id: id(),
    ticketId: ticket.id,
    action,
    state: "READY",
    content,
    reviewResult: result,
    hasAttachment,
    expiresAt: new Date(Date.now() + 300000).toISOString(),
    createdAt: "2026-09-08T14:00:00",
    warnings: [
      "预览只保存本地草稿，确认后发送一次。",
      "补偿申请和审核不会自动增加任何账户余额；审核通过不等于已到账。",
    ],
  };
  operations.set(op.id, op);
  ticket.pendingOperationId = op.id;
  ticket.version++;
  return op;
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
  await context.addInitScript(() => {
    window.ticketImageUrls = new Set();
    const create = URL.createObjectURL.bind(URL), revoke = URL.revokeObjectURL.bind(URL);
    URL.createObjectURL = blob => { const url = create(blob); ticketImageUrls.add(url); return url; };
    URL.revokeObjectURL = url => { ticketImageUrls.delete(url); revoke(url); };
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
    else writes.push({ path, body: body() });
    if (method === "GET" && /^\/(admin\/)?(project-tickets|project-ticket-operations)\/[^/]+\/image$/.test(path)) {
      assert.match(req.headers().authorization, /^Bearer /);
      assert.equal(url.searchParams.has('api_key'), false);
      assert.equal(url.searchParams.has('token'), false);
      return route.fulfill({ contentType: 'image/png', body: png });
    }
    if (method === "GET" && /^\/(admin\/)?project-tickets$/.test(path))
      return respond(listing([...tickets.values()].reverse()));
    if (method === "GET" && /^\/(admin\/)?project-tickets\/[^/]+$/.test(path))
      return respond(tickets.get(path.split("/").at(-1)));
    if (
      method === "GET" &&
      /^\/(admin\/)?project-ticket-operations\/[^/]+$/.test(path)
    ) {
      const op = operations.get(path.split("/").at(-1));
      return respond(
        op.action === "REVIEW" && !path.startsWith("/admin/")
          ? { ...op, content: "", reviewResult: null }
          : op,
      );
    }
    if (
      method === "POST" &&
      path === `/project-accounts/${accountId}/tickets`
    ) {
      const b = body();
      assert.equal(b.confirmedPolicy, true);
      assert.equal(b.compensationAmount, "2.25");
      const ticket = {
        id: id(),
        accountId,
        projectId: 1,
        projectTitle: "项目甲 · 独立服务额度",
        userId: 7,
        state: "DRAFT",
        type: b.type,
        title: b.title,
        description: b.description,
        compensationAmount: b.compensationAmount,
        status: null,
        reviewResult: "",
        reviewNote: "",
        hasAttachment: !!b.imageData,
        attachmentAvailable: !!b.imageData,
        replies: [],
        version: 0,
        pendingOperationId: null,
        createdAt: "2026-09-08T14:00:00",
        checkedAt: null,
      };
      tickets.set(ticket.id, ticket);
      return respond(operation(ticket, "SUBMIT", b.description, null, !!b.imageData));
    }
    if (
      method === "POST" &&
      /^\/project-tickets\/[^/]+\/reply-quotes$/.test(path)
    ) {
      const t = tickets.get(path.split("/")[2]),
        b = body();
      assert.equal(b.version, t.version);
      assert.equal(b.confirmedPolicy, true);
      assert.equal(t.pendingOperationId, null);
      return respond(operation(t, "REPLY", b.content, null, !!b.imageData));
    }
    if (
      method === "POST" &&
      /^\/admin\/project-tickets\/[^/]+\/review-quotes$/.test(path)
    ) {
      const t = tickets.get(path.split("/")[3]),
        b = body();
      assert.equal(b.version, t.version);
      assert.equal(b.upstreamChecked, true);
      assert.equal(t.reviewResult, "");
      return respond(operation(t, "REVIEW", b.note, b.result));
    }
    if (
      method === "POST" &&
      /^\/(admin\/)?project-ticket-operations\/[^/]+\/confirm$/.test(path)
    ) {
      const op = operations.get(path.split("/").at(-2)),
        t = tickets.get(op.ticketId);
      assert.equal(op.state, "READY");
      await new Promise((r) => setTimeout(r, 150));
      if (op.action === "REPLY")
        t.replies.push({
          id: String(100 + seq),
          sender: "customer",
          content: op.content,
          createdAt: "2026-09-08 14:10:00",
          hasAttachment: op.hasAttachment,
          attachmentAvailable: op.hasAttachment,
        });
      if (op.action === "REVIEW") {
        t.reviewResult = op.reviewResult;
        t.reviewNote = op.content;
        t.status = op.reviewResult === "approved" ? "resolved" : "closed";
      } else t.status = op.action === "REPLY" ? "processing" : "pending";
      t.version++;
      if (loseConfirm) {
        loseConfirm = false;
        op.state = "UNKNOWN";
        t.state = "UNKNOWN";
        return route.abort("connectionfailed");
      }
      op.state = "SUCCEEDED";
      t.state = "ACTIVE";
      t.pendingOperationId = null;
      t.checkedAt = "2026-09-08T14:10:00";
      return respond(op);
    }
    if (
      method === "POST" &&
      /^\/(admin\/)?project-tickets\/[^/]+\/refresh$/.test(path)
    ) {
      const t = tickets.get(path.split("/").at(-2));
      t.version++;
      t.checkedAt = "2026-09-08T14:12:00";
      if (!t.replies.some((r) => r.id === "support-reply"))
        t.replies.push({
          id: "support-reply",
          sender: "admin",
          content: "已收到反馈 <script>window.__xss=1</script>",
          createdAt: "2026-09-08 14:12:00",
          hasAttachment: true,
        });
      return respond(t);
    }
    if (
      method === "POST" &&
      /^\/admin\/project-ticket-operations\/[^/]+\/resolve$/.test(path)
    ) {
      const op = operations.get(path.split("/").at(-2)),
        t = tickets.get(op.ticketId),
        b = body();
      assert.equal(op.state, "UNKNOWN");
      assert.equal(b.upstreamChecked, true);
      assert.ok(b.evidence.length >= 10);
      assert.equal(op.action, "REPLY");
      op.state = b.outcome === "ACCEPTED" ? "SUCCEEDED" : "NOT_ACCEPTED";
      t.state = "ACTIVE";
      t.pendingOperationId = null;
      t.version++;
      if (loseResolution) {
        loseResolution = false;
        return route.abort("connectionfailed");
      }
      return respond(op);
    }
    unexpected.push(`${method} ${path}`);
    return route.fulfill({ status: 404, body: "Unexpected fixture request" });
  });
  const page = await context.newPage();
  page.setDefaultTimeout(45000);
  page.setDefaultNavigationTimeout(90000);
  await page.goto(`${base}/__project_tickets`);
  await page.getByRole("button", { name: "新建工单" }).waitFor();
  assert.equal(
    writes.length,
    0,
    "initial load never fetches supplier or submits",
  );
  async function draft(title, image = false) {
    await page.getByRole("button", { name: "新建工单" }).click();
    const d = page.getByRole("dialog", { name: "新建项目工单" });
    await d.getByText("补偿申请", { exact: true }).click();
    await d.getByRole("textbox", { name: "工单标题" }).fill(title);
    await d
      .getByRole("textbox", { name: "情况说明" })
      .fill("本人项目未完成服务，请核实已提供的执行记录。");
    await d.getByRole("textbox", { name: "申请补偿的项目额度" }).fill("2.25");
    if (image) {
      const noNewWrite = writes.length;
      await d.locator('input[type=file]').setInputFiles({ name: 'ticket.png', mimeType: 'image/png', buffer: png });
      await d.getByAltText('待提交附件预览').waitFor();
      assert.equal(writes.length, noNewWrite, 'local file selection does not upload');
    }
    await d
      .getByText("确认发送本人项目的问题说明，未包含密码、验证码或密钥", {
        exact: true,
      })
      .click();
    await d.getByRole("button", { name: "预览工单" }).click();
    const confirm = page.getByRole("dialog", { name: "发送新工单" });
    await confirm.waitFor();
    return confirm;
  }
  let dialog = await draft("执行异常 <img src=x onerror=window.__xss=1>", true);
  assert.match(writes.at(-1).body.imageData, /^data:image\/png;base64,/);
  assert.equal(reads.filter(p=>p.endsWith("/image")).length, 0);
  await dialog.getByRole("button", { name: "查看私有图片附件", exact: true }).click();
  await dialog.getByAltText("已清除元数据的工单附件").waitFor();
  assert.equal(reads.filter(p=>p.endsWith("/image")).length, 1);
  assert.equal(
    writes.filter((w) => w.path.endsWith("/confirm")).length,
    0,
    "drafts do not dispatch",
  );
  await dialog
    .getByRole("button", { name: "确认发送", exact: true })
    .click({ clickCount: 2 });
  await dialog.getByText("已完成", { exact: true }).waitFor();
  assert.equal(writes.filter((w) => w.path.endsWith("/confirm")).length, 1);
  await dialog.getByRole("button", { name: "关闭", exact: true }).click();
  await page.waitForFunction(() => ticketImageUrls.size === 0);
  assert.equal(await page.locator(".ticket-title img").count(), 0);
  await page.getByRole("button", { name: "查看工单", exact: true }).click();
  dialog = page.getByRole("dialog", { name: "项目工单详情" });
  await dialog.waitFor();
  const before = writes.length;
  assert.equal(writes.filter((w) => w.path.endsWith("/refresh")).length, 0);
  await dialog.getByRole("button", { name: "查看私有图片附件", exact: true }).click();
  await dialog.getByAltText("已清除元数据的工单附件").waitFor();
  assert.equal(writes.length, before, 'reading image cache never refreshes supplier');
  await dialog.getByRole("button", { name: "收起并清除图片", exact: true }).click();
  await dialog.getByRole("button", { name: "读取最新回复" }).click();
  await dialog
    .getByText("已收到反馈 <script>window.__xss=1</script>", { exact: true })
    .waitFor();
  assert.equal(await dialog.locator("script,img").count(), 0);
  assert.equal(await page.evaluate(() => window.__xss), undefined);
  await dialog
    .getByRole("textbox", { name: "补充回复" })
    .fill("补充本人情况，请核实原操作。");
  await dialog.locator('input[type=file]').setInputFiles({ name: 'reply.png', mimeType: 'image/png', buffer: png });
  await dialog.getByAltText('待提交附件预览').waitFor();
  await dialog
    .getByText("确认发送本人项目工单的回复，未包含凭据", { exact: true })
    .click();
  await dialog.getByRole("button", { name: "预览回复" }).click();
  dialog = page.getByRole("dialog", { name: "发送回复" });
  await dialog.waitFor();
  assert.match(writes.at(-1).body.imageData, /^data:image\/png;base64,/);
  await dialog.getByRole("button", { name: "查看私有图片附件", exact: true }).click();
  await dialog.getByAltText("已清除元数据的工单附件").waitFor();
  loseConfirm = true;
  await dialog.getByRole("button", { name: "确认发送", exact: true }).click();
  await dialog.getByText("提交结果待确认", { exact: true }).waitFor();
  assert.equal(
    await dialog.getByRole("button", { name: "确认发送", exact: true }).count(),
    0,
  );
  const writesBeforeRead = writes.length;
  await dialog.getByRole("button", { name: "检查提交结果" }).click();
  await dialog.getByText("待人工核对", { exact: true }).waitFor();
  assert.equal(writes.length, writesBeforeRead);
  await dialog.getByRole("button", { name: "关闭", exact: true }).click();
  await page.getByRole("button", { name: "查看工单", exact: true }).click();
  dialog = page.getByRole("dialog", { name: "项目工单详情" });
  await dialog.getByRole("button", { name: "读取最新回复" }).click();
  assert.equal(
    await dialog.getByRole("button", { name: "预览回复" }).count(),
    0,
    "remote text presence does not unlock an uncertain send",
  );
  assert.equal([...operations.values()].at(-1).state, "UNKNOWN");
  const replyBubble = dialog.locator('.reply-customer');
  const noRepeat = writes.length;
  await replyBubble.getByRole('button', { name: '查看私有图片附件', exact: true }).click();
  await replyBubble.getByAltText('已清除元数据的工单附件').waitFor();
  assert.equal(writes.length, noRepeat);
  mkdirSync("../.cache/native-service-ui", { recursive: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => document.documentElement.classList.add("dark"));
  assert.ok(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  );
  await page.screenshot({
    path: "../.cache/native-service-ui/project-tickets-mobile-dark.png",
    fullPage: false,
    animations: "disabled",
  });
  const bounds = await dialog.boundingBox();
  assert.ok(
    bounds.x >= -1 && bounds.x + bounds.width <= 391,
    JSON.stringify(bounds),
  );
  const admin = await context.newPage();
  admin.setDefaultTimeout(45000);
  admin.setDefaultNavigationTimeout(90000);
  await admin.goto(`${base}/__project_tickets?admin`);
  await admin.getByRole("button", { name: "查看工单" }).click();
  let detail = admin.getByRole("dialog", { name: "项目工单详情" });
  await detail.getByRole('button', { name: '查看私有图片附件', exact: true }).first().click();
  await detail.getByAltText('已清除元数据的工单附件').waitFor();
  assert.ok(reads.some(p => p.startsWith('/admin/project-tickets/') && p.endsWith('/image')));
  await detail.getByRole("button", { name: "检查原操作" }).click();
  dialog = admin.getByRole("dialog", { name: "发送回复" });
  await dialog.getByText("已受理", { exact: true }).click();
  await dialog
    .getByRole("textbox", { name: "核对证据" })
    .fill("逐项核对原请求流水，上游确认该回复已经提交成功。");
  await dialog
    .getByText("已逐项核对原请求的真实受理结果", { exact: true })
    .click();
  loseResolution = true;
  await dialog.getByRole("button", { name: "保存核对结论" }).click();
  await dialog
    .getByText("核对请求已提交，请只查询原操作结果，不要重复提交。", {
      exact: true,
    })
    .waitFor();
  assert.equal(
    await dialog.getByRole("button", { name: "保存核对结论" }).isDisabled(),
    true,
  );
  const resolutionCount = writes.filter((w) =>
    w.path.endsWith("/resolve"),
  ).length;
  const checkCount = writes.length;
  await dialog.getByRole("button", { name: "检查提交结果" }).click();
  await dialog.getByText("已完成", { exact: true }).waitFor();
  assert.equal(writes.length, checkCount);
  assert.equal(resolutionCount, 1);
  await dialog.getByRole("button", { name: "关闭", exact: true }).click();
  await page.getByRole('dialog', { name: '项目工单详情' }).getByRole('button', { name: 'Close this dialog' }).click();
  await page.getByRole('button', { name: '查看工单', exact: true }).click();
  let userDetail = page.getByRole('dialog', { name: '项目工单详情' });
  await userDetail.locator('input[type=file]').setInputFiles({ name: 'only.png', mimeType: 'image/png', buffer: png });
  await userDetail.getByAltText('待提交附件预览').waitFor();
  await userDetail.getByText('确认发送本人项目工单的回复，未包含凭据', { exact: true }).click();
  await userDetail.getByRole('button', { name: '预览回复' }).click();
  assert.equal(writes.at(-1).body.content, '');
  assert.match(writes.at(-1).body.imageData, /^data:image\/png;base64,/);
  const pureImage = page.getByRole('dialog', { name: '发送回复' });
  await pureImage.getByRole('button', { name: '确认发送', exact: true }).click();
  await pureImage.getByText('已完成', { exact: true }).waitFor();
  await pureImage.getByRole('button', { name: '关闭', exact: true }).click();
  await page.getByRole('button', { name: '查看工单', exact: true }).click();
  await admin.getByRole("button", { name: "查看工单" }).click();
  detail = admin.getByRole("dialog", { name: "项目工单详情" });
  await detail.getByText("同意申请", { exact: true }).click();
  await detail
    .getByRole("textbox", { name: "审核说明" })
    .fill("已核实服务异常，同意申请，款项另行核对，不自动入账。");
  await detail
    .getByText("已核实原工单，了解审核通过不会自动向任何账户入账", {
      exact: true,
    })
    .click();
  await detail.getByRole("button", { name: "预览补偿审核" }).click();
  dialog = admin.getByRole("dialog", { name: "提交补偿审核" });
  await dialog.getByRole("button", { name: "确认审核（不自动付款）" }).click();
  await dialog.getByText("已完成", { exact: true }).waitFor();
  await dialog.getByRole("button", { name: "关闭", exact: true }).click();
  await admin.getByRole("button", { name: "查看工单" }).click();
  detail = admin.getByRole("dialog", { name: "项目工单详情" });
  await detail
    .getByText("审核通过 · 不代表余额已到账", { exact: true })
    .waitFor();
  assert.equal(
    await detail.getByRole("button", { name: "预览补偿审核" }).count(),
    0,
  );
  await settleDrawer(detail);
  await admin.screenshot({
    path: "../.cache/native-service-ui/project-tickets-review-desktop.png",
    fullPage: true,
    animations: "disabled",
  });
  const desktopBounds = await detail.boundingBox();
  assert.ok(
    desktopBounds.x >= 0 && desktopBounds.x + desktopBounds.width <= 1441,
    JSON.stringify(desktopBounds),
  );
  await page
    .getByRole("dialog", { name: "项目工单详情" })
    .getByRole("button", { name: "Close this dialog" })
    .click();
  dialog = await draft("第二张待核实的新工单");
  loseConfirm = true;
  await dialog.getByRole("button", { name: "确认发送", exact: true }).click();
  await dialog.getByRole("button", { name: "检查提交结果" }).click();
  await dialog.getByText("待人工核对", { exact: true }).waitFor();
  await admin
    .getByRole("dialog", { name: "项目工单详情" })
    .getByRole("button", { name: "Close this dialog" })
    .click();
  await admin.getByRole("button", { name: "刷新工单", exact: true }).click();
  await admin
    .locator(".support-card")
    .filter({ hasText: "第二张待核实的新工单" })
    .getByRole("button", { name: "查看工单" })
    .click();
  await admin
    .getByRole("dialog", { name: "项目工单详情" })
    .getByRole("button", { name: "检查原操作" })
    .click();
  dialog = admin.getByRole("dialog", { name: "发送新工单" });
  assert.equal(
    await dialog
      .getByRole("radio", { name: "已受理", exact: true })
      .isDisabled(),
    true,
  );
  assert.ok(reads.filter(p => p.endsWith('/image')).every(p => !p.includes('key=')));
  assert.deepEqual(unexpected, []);
  assert.deepEqual(errors, []);
  const storage = await page.evaluate(() =>
    JSON.stringify({
      local: { ...localStorage },
      session: { ...sessionStorage },
    }),
  );
  assert.doesNotMatch(storage, /customer[_-]?key|api[_-]?key|补充本人情况/);
  console.log(
    "Project ticket browser workflow passed: drafts, single sends, exact ownership fixture, private inline images in draft/submission/reply, image-only replies, GET-only image cache and uncertainty, admin resolution/review without money, mobile/dark; simulated APIs only.",
  );
} finally {
  await context?.close();
  await browser?.close();
  await server.close();
}
