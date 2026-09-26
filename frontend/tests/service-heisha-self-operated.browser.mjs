import assert from 'node:assert/strict';
import { existsSync, mkdirSync } from 'node:fs';
import { chromium } from 'playwright';
import { createTestServer } from './fixtures/test-server.mjs';
const html = `<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8"></head><body><div id="app"></div><script type="module">
import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';
import {createApp,h} from 'vue';import {createRouter,createMemoryHistory,RouterView} from 'vue-router';import ElementPlus from 'element-plus';import 'element-plus/dist/index.css';
import Store from '/src/views/ServiceStore.vue';import Orders from '/src/views/ServiceOrders.vue';import Admin from '/src/views/AdminServiceProducts.vue';import {applyAuthSession} from '/src/utils/authSession.js';
const selected=new URLSearchParams(location.search).get('page')||'/services';
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7,permissions:selected.startsWith('/admin')?['api-provider:update','service-order:fulfill']:[]});
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/services',component:Store},{path:'/service-orders',component:Orders},{path:'/admin/service-products',component:Admin},{path:'/admin/service-orders',component:Orders,meta:{serviceAdmin:true}}]});
await router.push(selected);await router.isReady();createApp({render:()=>h(RouterView)}).use(router).use(ElementPlus).mount('#app');</script></body></html>`;
const server = await createTestServer({ logLevel: 'error', plugins: [{ name: 'self-operated-fixture', configureServer(vite) {
  vite.middlewares.use(async (req, res, next) => { if (!req.url?.startsWith('/__self')) return next(); res.setHeader('Content-Type', 'text/html;charset=utf-8'); res.end(await vite.transformIndexHtml(req.url, html)); });
} }] });
const id = '70fe9178-8f36-434e-8200-f4d1c9ed82d0', qid = '85ea431d-cfe1-4e06-8a68-1f343f50f34f';
const product = { id: 1, providerId: 9, providerType: 'heisha', project: 'default', remoteProductId: '1', title: '黑鲨日常跑', description: '按计划完成服务', unitPrice: '0.25', priceUnit: '元/公里', enabled: true, available: true, version: 0, fulfillmentMode: 'SELF_OPERATED', capabilities: ['LOOKUP', 'CREATE', 'SYNC'] };
const order = { id, title: product.title, accountLabel: '13***00', providerType: 'heisha', project: 'default', fulfillmentMode: 'SELF_OPERATED', status: 'PENDING', quantity: 10, completed: 0, distance: '2', paidAmount: '5.00', refundedAmount: '0.00', version: 0, actions: [], pendingOperationId: null, quantityUnit: '次', createTime: '2026-09-25T12:00:00' };
let saved, created = false, confirmations = 0, quote, syncs = 0;
const commands = [], unexpected = [], errors = [];
let browser;
try {
  await server.listen(); const base = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({ executablePath: process.env.BROWSER_PATH || (existsSync('/snap/bin/chromium') ? '/snap/bin/chromium' : undefined), headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } }); page.setDefaultTimeout(15000);
  page.on('pageerror', e => errors.push(e.message));
  await page.route('**/*', async route => {
    const req = route.request(), url = new URL(req.url());
    if (url.origin !== base) { unexpected.push(url.origin); return route.abort(); }
    if (!url.pathname.startsWith('/api/')) return route.continue();
    const endpoint = url.pathname.slice(4), body = () => JSON.parse(req.postData() || '{}');
    const respond = data => route.fulfill({ contentType: 'application/json', headers: { 'Cache-Control': 'no-store' }, body: JSON.stringify({ code: 1, data }) });
    if (endpoint === '/admin/api-providers') return respond({ records: [{ id: 9, name: '黑鲨配置', providerType: 'heisha', status: 1, verifiedAt: '2026-09-25T00:00:00' }], total: 1 });
    if (endpoint.endsWith('/providers/9/catalog')) return respond([{ id: '1', name: product.title, unitPrice: '0.10', priceUnit: '元/公里' }]);
    if (endpoint === '/admin/service-products') {
      if (req.method() === 'POST') { saved = body(); return respond(product); }
      return respond({ records: saved ? [product] : [], total: saved ? 1 : 0 });
    }
    if (endpoint === '/services') return respond({ records: [product], total: 1 });
    if (endpoint === '/services/1/lookup') return respond({ suggested: { planOptionId: 'p1', fenceOptionId: 'f1' }, choices: [{ field: 'planOptionId', value: 'p1', label: '日常跑' }, { field: 'fenceOptionId', value: 'f1', label: '校园区域' }], notice: '请选择计划和区域' });
    if (endpoint === '/services/1/quotes') {
      const input = body(); assert.equal(input.fields.password, 'browser-password-secret'); assert.equal(input.fulfillmentMode, undefined); assert.equal(input.selfOperated, undefined); assert.equal(input.authorizedAccount, true);
      quote = { id: qid, orderId: null, action: 'CREATE', state: 'READY', title: product.title, quantity: 10, amount: '5.00', amountLabel: '本次余额扣款', expiresAt: '2099-01-01T00:00:00' }; return respond(quote);
    }
    if (endpoint === `/service-order-operations/${qid}/confirm`) { confirmations++; created = true; quote = { ...quote, state: 'SUCCEEDED', orderId: id }; return respond(quote); }
    if (endpoint === `/service-order-operations/${qid}`) return respond(quote);
    if (['/service-orders', '/admin/service-orders'].includes(endpoint)) return respond({ records: created ? [order] : [], total: created ? 1 : 0, current: Number(url.searchParams.get('page') || 1), size: 20 });
    if (endpoint === `/service-orders/${id}/sync`) { syncs++; return respond(order); }
    if (endpoint === `/admin/service-orders/${id}/fulfillment`) {
      if (req.method() === 'GET') return respond({ orderId: id, fields: { phone: '13800138000', password: 'browser-password-secret', run_time: '08:00' } });
      const command = body(); commands.push(command); assert.equal(command.orderVersion, order.version);
      if (command.action === 'START') order.status = 'ACTIVE';
      else if (command.action === 'PROGRESS') order.completed = command.completed;
      else if (command.action === 'COMPLETE') { order.status = 'COMPLETED'; order.completed = order.quantity; }
      else assert.fail('Unexpected lifecycle action');
      order.version++; return respond(order);
    }
    unexpected.push(`${req.method()} ${endpoint}`); return route.fulfill({ status: 404, body: '{}' });
  });
  const goto = path => page.goto(`${base}/__self?page=${encodeURIComponent(path)}`);
  await goto('/admin/service-products');
  await page.getByRole('button', { name: '上架服务商品', exact: true }).click();
  await page.locator('.service-provider-select .el-select__wrapper').click();
  await page.getByRole('option', { name: /黑鲨配置/ }).click();
  await page.getByRole('dialog').locator('.el-form-item').filter({ hasText: '履约方式' }).locator('.el-select__wrapper').click();
  await page.getByRole('option', { name: '平台自营', exact: true }).click();
  await page.getByRole('button', { name: '读取目录', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.locator('.el-form-item').filter({ hasText: '服务商品' }).locator('.el-select').click();
  await page.getByRole('option', { name: /黑鲨日常跑/ }).click();
  await page.getByRole('button', { name: '保存商品', exact: true }).click();
  await page.getByText('服务商品已保存', { exact: true }).waitFor();
  assert.equal(saved.fulfillmentMode, 'SELF_OPERATED');
  await goto('/services'); await page.getByText('平台自营', { exact: true }).waitFor();
  await page.getByRole('button', { name: '选择服务', exact: true }).click();
  await page.getByText('我有权使用此账号及信息，并授权提交', { exact: true }).click();
  const checkout = page.getByRole('dialog');
  await checkout.locator('.el-form-item').filter({ hasText: '服务账号 / 手机号' }).locator('input').fill('13800138000');
  await checkout.locator('.el-form-item').filter({ hasText: '服务密码' }).locator('input').fill('browser-password-secret');
  await page.getByRole('button', { name: '查询账号与可用计划' }).click();
  await page.getByText('请选择计划和区域', { exact: true }).waitFor();
  await checkout.locator('.el-form-item').filter({ hasText: '每日时间' }).locator('input').fill('08:00');
  await page.getByRole('button', { name: '预览金额并下单' }).click();
  await page.getByRole('button', { name: '确认并下单', exact: true }).click();
  await page.getByRole('heading', { name: '我的服务订单' }).waitFor();
  await page.locator('.order-card').getByText('待处理', { exact: true }).waitFor(); assert.equal(confirmations, 1);
  assert.equal(await page.getByRole('button', { name: '开始处理', exact: true }).count(), 0);
  await page.getByRole('button', { name: '刷新状态', exact: true }).click();
  await goto('/admin/service-orders');
  await page.getByRole('button', { name: '查看履约资料', exact: true }).click();
  await page.getByText('browser-password-secret', { exact: true }).waitFor();
  assert.equal(await page.evaluate(() => JSON.stringify([localStorage, sessionStorage]).includes('browser-password-secret')), false);
  await page.locator('.el-drawer__close-btn:visible').click();
  await page.getByText('browser-password-secret', { exact: true }).waitFor({ state: 'hidden' });
  await page.getByRole('button', { name: '查看履约资料', exact: true }).click();
  await page.getByText('browser-password-secret', { exact: true }).waitFor();
  await page.evaluate(async () => {
    const { applyAuthSession, sessionUserInfo, getAccessToken } = await import('/src/utils/authSession.js');
    applyAuthSession({ ...sessionUserInfo.value, token: getAccessToken(), permissions: ['api-provider:update'] });
  });
  await page.getByText('browser-password-secret', { exact: true }).waitFor({ state: 'hidden' });
  assert.equal(await page.getByRole('button', { name: '查看履约资料', exact: true }).count(), 0);
  await goto('/admin/service-orders');
  for (const label of ['开始处理', '更新进度', '标记完成']) {
    await page.getByRole('button', { name: label, exact: true }).click();
    if (label === '更新进度') await page.getByRole('spinbutton', { name: '已完成数量' }).fill('3');
    await page.getByRole('button', { name: '确认处理', exact: true }).click();
    await page.getByRole('dialog').waitFor({ state: 'hidden' });
  }
  assert.deepEqual(commands.map(c => c.action), ['START', 'PROGRESS', 'COMPLETE']);
  assert.equal(order.completed, 10);
  await goto('/service-orders'); await page.locator('.order-card').getByText('已完成', { exact: true }).waitFor();
  await page.getByRole('button', { name: '刷新状态', exact: true }).click();
  mkdirSync('../.cache/heisha-self-operated-ui', { recursive: true });
  await page.screenshot({ path: '../.cache/heisha-self-operated-ui/completed.png', fullPage: true, animations: 'disabled' });
  assert.ok(syncs >= 2); assert.deepEqual(unexpected, []); assert.deepEqual(errors, []);
  console.log('PASS heisha self-operated product, checkout, local lifecycle, sensitive drawer and user refresh');
} finally { await browser?.close(); await server.close(); }
