import assert from 'node:assert/strict';
import { existsSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright';
import { createTestServer } from './fixtures/test-server.mjs';

const html = `<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;padding:20px"><div id="app"></div>
<script type="module">
import {createApp,h} from 'vue';import {createRouter,createMemoryHistory,RouterView} from 'vue-router';import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';
import Orders from '/src/views/ServiceOrders.vue';import {applyAuthSession} from '/src/utils/authSession.js';
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7});
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/service-orders',component:Orders},{path:'/admin/service-orders',component:Orders,meta:{serviceAdmin:true}}]});
await router.push(new URLSearchParams(location.search).get('page')||'/service-orders');await router.isReady();createApp({render:()=>h(RouterView)}).use(router).use(ElementPlus).mount('#app');
</script></body></html>`;
const server = await createTestServer({ logLevel: 'error', plugins: [{ name: 'status-refresh-fixture', configureServer(vite) {
  vite.middlewares.use(async (req, res, next) => {
    if (!req.url?.startsWith('/__status_refresh')) return next();
    res.setHeader('Content-Type', 'text/html;charset=utf-8');
    res.end(await vite.transformIndexHtml(req.url, html));
  });
} }] });
const order = (n, title, statusCheck, extra = {}) => ({
  id: `70fe9178-8f36-434e-8200-f4d1c9ed82d${n.toString(16)}`, title, accountLabel: '20***18', providerType: 'jiguang', project: 'default',
  status: 'ACTIVE', quantity: 10, completed: 3, distance: '2.00', paidAmount: '5.00', refundedAmount: '0.00',
  pendingOperationId: null, createTime: '2026-09-10T08:30:00', updateTime: '2026-09-11T23:59:59', version: 1,
  actions: [], quantityUnit: '次', statusCheck, ...extra,
});
const internshipOrder = (n, title, statusCheck, extra = {}) => order(n, title, statusCheck, {
  providerType: 'sxdk_tw', project: 'zxjy', quantity: 3, completed: null, distance: null,
  paidAmount: '0.75', quantityUnit: '天', schedule: { endDate: '2026-09-12', weekdays: [1,2,3,4,5,6,7],
    checkInTime: '08:00:00', checkOutTime: '18:00:00', runMode: 1 }, ...extra,
});
const rows = [
  order(1, '晨间计划 · 已有核对记录', { checkedAt: '2026-09-11T10:20:30.123456789', delayed: false }),
  order(2, '校园计划 · 暂未更新', { checkedAt: '2026-09-11T09:18:26', delayed: true }),
  order(3, '新建计划 · 暂无记录', { checkedAt: null, delayed: false }),
  order(4, '历史计划 · 兼容旧响应', undefined),
  order(5, '等待确认的计划', { checkedAt: '2026-09-11T08:10:00', delayed: false }, { status: 'CONFIRMING', pendingOperationId: '70fe9178-8f36-434e-8200-f4d1c9ed8299' }),
  order(6, '字段异常的历史计划', { checkedAt: '<script>private-response</script>', delayed: true }),
  internshipOrder(7, '实习日常计划', { checkedAt: '2026-09-11T02:20:30Z', delayed: false }),
  internshipOrder(8, '实习计划 · 暂停与延迟', { checkedAt: '2026-09-11T09:18:26', delayed: true }, { status: 'PAUSED' }),
  // A legacy/malformed attendance count must still never become a completion bar for a daily plan.
  internshipOrder(9, '实习计划 · 服务期结束', { checkedAt: '2026-09-11T10:20:30', delayed: false }, {
    status: 'COMPLETED', completed: 3, createTime: '2026-09-08T08:30:00', schedule: { endDate: '2026-09-10', weekdays: [1,2,3,4,5,6,7], checkInTime: '08:00:00', checkOutTime: '18:00:00' },
  }),
  internshipOrder(10, '实习计划 · 尚未核对', { checkedAt: null, delayed: false }),
  internshipOrder(11, '实习计划 · 历史响应', undefined, {
    schedule: { endDate: '2026-09-01', weekdays: [1,2,3,4,5], checkInTime: '08:00:00', checkOutTime: '18:00:00' },
  }),
  internshipOrder(12, '实习计划 · 操作待确认', { checkedAt: null, delayed: false }, {
    status: 'CONFIRMING', pendingOperationId: '70fe9178-8f36-434e-8200-f4d1c9ed8298',
  }),
];
const reads = [], writes = [], unexpected = [], errors = [];
let browser, releaseSync, failSync = false;
try {
  await server.listen();
  const base = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({ executablePath: process.env.BROWSER_PATH || (existsSync('/snap/bin/chromium') ? '/snap/bin/chromium' : undefined), headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1120 }, timezoneId: 'America/Los_Angeles' });
  page.setDefaultTimeout(30000);
  page.on('pageerror', e => errors.push(e.message));
  await page.route('**/*', async route => {
    const req = route.request(), url = new URL(req.url());
    if (url.origin !== base) { unexpected.push(url.origin); return route.abort(); }
    if (!url.pathname.startsWith('/api/')) return route.continue();
    const endpoint = url.pathname.slice(4), method = req.method();
    const respond = data => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 1, data, message: '操作成功' }) });
    if (method === 'GET' && ['/service-orders', '/admin/service-orders'].includes(endpoint)) {
      reads.push(endpoint); return respond({ records: rows, total: rows.length, current: 1, size: 20 });
    }
    const target = [rows[0], rows[6]].find(row => endpoint === `/service-orders/${row.id}/sync`);
    if (method === 'POST' && target) {
      writes.push(endpoint);
      assert.deepEqual(JSON.parse(req.postData() || '{}'), {}, 'a manual read never sends money, status or credentials');
      if (releaseSync) await releaseSync;
      if (failSync) {
        target.statusCheck.delayed = true;
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 400, message: '暂时无法更新进度，请稍后重试' }) });
      }
      target.statusCheck = { checkedAt: '2026-09-11T10:25:30', delayed: false };
      if (target.providerType === 'sxdk_tw') target.status = 'PAUSED';
      return respond(target);
    }
    unexpected.push(`${method} ${endpoint}`); return route.abort();
  });
  await page.goto(`${base}/__status_refresh`);
  const cards = page.locator('.order-card');
  await cards.nth(11).waitFor();
  const first = cards.nth(0), second = cards.nth(1);
  assert.equal(await first.locator('time').innerText(), '2026-09-11 10:20:30');
  assert.equal(await first.locator('time').getAttribute('datetime'), '2026-09-11T10:20:30+08:00');
  assert.match(await first.innerText(), /北京时间/);
  assert.match(await first.innerText(), /核对时间不代表执行时间/);
  assert.match(await second.innerText(), /暂未取得最新进度，已保留此前结果/);
  assert.match(await second.innerText(), /2026-09-11 09:18:26/);
  assert.match(await cards.nth(2).innerText(), /暂无记录/);
  assert.equal(await cards.nth(2).locator('time').count(), 0);
  assert.equal(await cards.nth(3).locator('.service-status-check').count(), 0, 'old responses do not fabricate check times');
  assert.equal(await cards.nth(4).getByRole('button', { name: '更新进度', exact: true }).count(), 0, 'unknown write remains a manual reconciliation case');
  assert.equal(await cards.nth(4).getByRole('button', { name: '检查提交结果', exact: true }).count(), 1);
  assert.equal(await cards.nth(5).locator('time').count(), 0);
  assert.ok(!(await page.locator('body').innerText()).includes('private-response'));
  const internship = cards.nth(6), pausedPlan = cards.nth(7), endedPlan = cards.nth(8);
  assert.equal(await internship.locator('.order-title .el-tag').innerText(), '计划运行中');
  assert.equal(await pausedPlan.locator('.order-title .el-tag').innerText(), '计划已暂停');
  assert.equal(await endedPlan.locator('.order-title .el-tag').innerText(), '服务期结束');
  assert.equal(await endedPlan.locator('.order-title .el-tag--success').count(), 0, 'period end is not a successful attendance badge');
  assert.match(await endedPlan.innerText(), /服务期结束不代表考勤已完成/);
  assert.equal(await internship.locator('time').innerText(), '2026-09-11 10:20:30');
  assert.match(await internship.getByRole('status', { name: '计划状态核对情况' }).innerText(), /最近核对计划状态/);
  assert.match(await internship.innerText(), /仅核对计划状态，考勤结果请查看执行记录/);
  assert.match(await pausedPlan.innerText(), /暂未取得最新计划状态，已保留此前结果/);
  for (let i = 6; i < rows.length; i++) {
    assert.equal(await cards.nth(i).locator('.el-progress').count(), 0);
    assert.ok(!/完成 \/ 总次数|已完成 3|每次距离/.test(await cards.nth(i).innerText()));
    assert.match(await cards.nth(i).locator('.order-metrics').innerText(), /累计已购服务日/);
  }
  assert.match(await cards.nth(9).innerText(), /暂无记录/);
  assert.equal(await cards.nth(9).locator('time').count(), 0);
  assert.equal(await cards.nth(10).locator('.service-status-check').count(), 0);
  assert.equal(await cards.nth(10).locator('.order-title .el-tag').innerText(), '计划运行中', 'the browser never infers a state from an old calendar date');
  assert.equal(await cards.nth(11).getByRole('button', { name: '核对计划状态', exact: true }).count(), 0);
  assert.equal(await cards.nth(11).getByRole('button', { name: '检查提交结果', exact: true }).count(), 1);
  const planMetrics = await internship.locator('.order-metrics').innerText();
  assert.equal(reads.length, 1); assert.equal(writes.length, 0);
  const out = path.resolve('../.cache/native-service-ui'); mkdirSync(out, { recursive: true });
  await page.screenshot({ path: `${out}/internship-status-orders-desktop.png`, fullPage: true, animations: 'disabled' });
  await internship.screenshot({ path: `${out}/internship-status-card-desktop.png`, animations: 'disabled' });
  await endedPlan.screenshot({ path: `${out}/internship-status-ended-desktop.png`, animations: 'disabled' });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => document.documentElement.classList.add('dark'));
  for (const button of await first.locator('.order-actions .el-button').all()) assert.ok((await button.boundingBox()).height >= 44);
  for (const button of await internship.locator('.order-actions .el-button').all()) assert.ok((await button.boundingBox()).height >= 44);
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
  await page.screenshot({ path: `${out}/internship-status-orders-mobile-dark.png`, fullPage: true, animations: 'disabled' });
  // No browser timer may turn the new display into unsolicited remote reads.
  await page.clock.install(); await page.clock.fastForward('06:00');
  assert.equal(reads.length, 1); assert.equal(writes.length, 0);
  await page.clock.resume();
  let unblock;
  releaseSync = new Promise(resolve => { unblock = resolve; });
  const button = first.getByRole('button', { name: '更新进度', exact: true });
  await button.click();
  await page.waitForFunction(() => document.querySelector('.order-actions .el-button.is-loading'));
  await button.click({ force: true });
  assert.equal(writes.length, 1, 'busy guard prevents another manual read');
  unblock(); releaseSync = null;
  await page.waitForFunction(() => document.querySelector('.order-card time')?.textContent === '2026-09-11 10:25:30');
  assert.equal(reads.length, 2); assert.equal(writes.length, 1);
  assert.match(await first.locator('.order-metrics').innerText(), /3/);
  assert.match(await first.locator('.order-metrics').innerText(), /5\.00/);
  failSync = true;
  await first.getByRole('button', { name: '更新进度', exact: true }).click();
  await first.getByText('暂未取得最新进度，已保留此前结果。', { exact: true }).waitFor();
  assert.equal(writes.length, 2); assert.equal(reads.length, 3, 'failed read fetches only saved local metadata');
  assert.equal(await first.locator('time').innerText(), '2026-09-11 10:25:30');
  assert.match(await first.locator('.order-metrics').innerText(), /5\.00/);
  await page.getByRole('button', { name: '刷新列表', exact: true }).click();
  await page.waitForFunction(() => !document.querySelector('.order-list .el-loading-mask'));
  assert.equal(writes.length, 2, 'refreshing the list cannot call sync');
  failSync = false;
  const priorReads = reads.length;
  await internship.getByRole('button', { name: '核对计划状态', exact: true }).click();
  await internship.getByText('计划已暂停', { exact: true }).waitFor();
  await page.waitForFunction(() => !document.querySelector('.order-actions .el-button.is-loading'));
  assert.equal(writes.length, 3); assert.equal(reads.length, priorReads + 1);
  assert.equal(await internship.locator('time').innerText(), '2026-09-11 10:25:30');
  assert.equal(await internship.locator('.order-metrics').innerText(), planMetrics, 'status checks cannot extend the purchased calendar or change balance');
  failSync = true;
  await internship.getByRole('button', { name: '核对计划状态', exact: true }).click();
  await internship.getByText('暂未取得最新计划状态，已保留此前结果。', { exact: true }).waitFor();
  await page.waitForFunction(() => !document.querySelector('.order-actions .el-button.is-loading'));
  assert.equal(writes.length, 4); assert.equal(reads.length, priorReads + 2);
  assert.equal(await internship.locator('.order-title .el-tag').innerText(), '计划已暂停');
  assert.equal(await internship.locator('time').innerText(), '2026-09-11 10:25:30');
  assert.equal(await internship.locator('.order-metrics').innerText(), planMetrics);
  await internship.screenshot({ path: `${out}/internship-status-card-mobile-dark.png`, animations: 'disabled' });
  await endedPlan.screenshot({ path: `${out}/internship-status-ended-mobile-dark.png`, animations: 'disabled' });
  const storage = await page.evaluate(() => JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]));
  for (const marker of [rows[0].id, '10:25:30', 'private-response']) assert.ok(!storage.includes(marker));
  await page.goto(`${base}/__status_refresh?page=/admin/service-orders`);
  await page.getByRole('heading', { name: '服务订单与对账' }).waitFor();
  await page.locator('.order-card').nth(11).waitFor();
  assert.equal(await page.getByRole('button', { name: '更新进度', exact: true }).count(), 0);
  assert.equal(await page.locator('.order-card').first().locator('time').innerText(), '2026-09-11 10:25:30');
  assert.equal(await page.getByRole('button', { name: '核对计划状态', exact: true }).count(), 0);
  assert.equal(await page.locator('.order-card').nth(6).locator('.order-title .el-tag').innerText(), '计划已暂停');
  assert.equal(writes.length, 4);
  assert.ok(!/上游|上游平台|上游源|本平台|当前平台|对接|套娃|第三方|渠道|货源/.test(await page.locator('body').innerText()));
  assert.deepEqual(errors, []); assert.deepEqual(unexpected, []);
  console.log('PASS internship + service status refresh: real bound daily-plan states and end-of-period semantics, no attendance graph, immutable calendar/money, legacy dates not inferred;  real Vue order page, Beijing checks in Los Angeles, delayed/empty/legacy/invalid/unknown cases, 44px mobile dark, no timer polling, one manual read + local GET, no money/storage effects, admin read-only; mocked APIs only');
} finally {
  if (browser) await browser.close();
  await server.close();
}
