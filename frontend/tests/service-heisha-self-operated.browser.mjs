import assert from 'node:assert/strict';
import { existsSync, mkdirSync } from 'node:fs';
import { chromium } from 'playwright';
import { fileURLToPath } from 'node:url';
import { createTestServer } from './fixtures/test-server.mjs';

const html = `<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8"></head><body><div id="app"></div><script type="module">
import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';
import {createApp,h} from 'vue';import {createRouter,createMemoryHistory,RouterView} from 'vue-router';import ElementPlus from 'element-plus';import 'element-plus/dist/index.css';
import Store from '/src/views/ServiceStore.vue';import Orders from '/src/views/ServiceOrders.vue';import Admin from '/src/views/AdminServiceProducts.vue';import {applyAuthSession} from '/src/utils/authSession.js';
const selected=new URLSearchParams(location.search).get('page')||'/services';
const permissions=selected.startsWith('/admin')?['api-provider:update','service-order:fulfill','service-order:biometric']:[];
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:selected.startsWith('/admin')?8:7,permissions});
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/services',component:Store},{path:'/service-orders',component:Orders},{path:'/admin/service-products',component:Admin},{path:'/admin/service-orders',component:Orders,meta:{serviceAdmin:true}}]});
await router.push(selected);await router.isReady();createApp({render:()=>h(RouterView)}).use(router).use(ElementPlus).mount('#app');</script></body></html>`;

const server = await createTestServer({
  root: fileURLToPath(new URL('../', import.meta.url)),
  logLevel: 'error',
  plugins: [{ name: 'self-operated-fixture', configureServer(vite) {
    vite.middlewares.use(async (req, res, next) => {
      if (!req.url?.startsWith('/__self')) return next();
      res.setHeader('Content-Type', 'text/html;charset=utf-8');
      res.end(await vite.transformIndexHtml(req.url, html));
    });
  } }],
});

const ids = [
  '70fe9178-8f36-434e-8200-f4d1c9ed82d0',
  '0fe4a11f-779a-49f4-8d46-b583459940b1',
];
const quoteIds = [
  '85ea431d-cfe1-4e06-8a68-1f343f50f34f',
  '1a950315-34a2-4e9b-aea7-91edbed1b227',
];
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADUlEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC', 'base64');
const imageData = `data:image/png;base64,${png.toString('base64')}`;
let product = {
  id: 1, providerId: null, providerType: 'heisha', project: 'default', remoteProductId: '3',
  title: '黑鲨资格服务', description: '核验资料并安排服务', unitPrice: '0.25', priceUnit: '元/公里',
  enabled: true, available: true, version: 0, fulfillmentMode: 'SELF_OPERATED', capabilities: [],
};
const orders = ids.map((id, index) => ({
  id, title: product.title, accountLabel: '13***00', providerType: 'heisha', project: 'default',
  remoteProductId: '3', fulfillmentMode: 'SELF_OPERATED', status: 'PENDING', quantity: 10,
  completed: 0, distance: '2', paidAmount: '5.00', refundedAmount: '0.00', version: 0,
  fulfillmentVersion: 0, verificationStatus: 'PENDING', verificationReason: null,
  faceMaterialPresent: true, faceMaterialRequired: true, actions: [], pendingOperationId: null,
  quantityUnit: '次', createTime: '2026-09-25T12:00:00',
}));
const details = orders.map((order, index) => ({
  orderId: order.id,
  fields: { phone: '13800138000', password: 'browser-password-secret', run_time: '08:00', school_name: '测试大学' },
  verificationStatus: 'PENDING', version: 0, materialVersion: 0, verifiedBy: null, verifiedAt: null,
  verificationNote: null,
  assets: [{ id: `asset-${index + 1}`, assetType: 'FACE_QUALIFICATION', mimeType: 'image/png',
    width: 1, height: 1, byteSize: png.length, version: 0, createTime: '2026-09-25T12:00:00', purgedAt: null }],
}));
const materialDraftIds = [
  '66e4a682-fab8-48ef-8245-d4c4af4a0172',
  'a9edfc0a-a34c-4a6c-aa8a-648ec6a2fa8f',
  'd39e29d4-fcd1-45da-afdd-8e650087c36c',
];
let activeIndex = -1, quoteCount = 0, draftCount = 0, confirmations = 0, savedProduct = null;
const verifyActions = [], fulfillmentActions = [], supplementRequests = [], supplierAttempts = [], unexpected = [], errors = [];
let browser;

function pageData(records) { return { current: 1, size: 20, total: records.length, records }; }
function respond(route, data) {
  return route.fulfill({ contentType: 'application/json', headers: { 'Cache-Control': 'no-store' },
    body: JSON.stringify({ code: 1, data }) });
}
function activeOrder() { assert.ok(activeIndex >= 0, 'an order should exist before order APIs are called'); return orders[activeIndex]; }
function activeDetails() { assert.ok(activeIndex >= 0); return details[activeIndex]; }

try {
  await server.listen();
  const base = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({
    executablePath: process.env.BROWSER_PATH || (existsSync('/snap/bin/chromium') ? '/snap/bin/chromium' : undefined),
    headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'],
  });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  page.setDefaultTimeout(15000);
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/*', async route => {
    const req = route.request(), url = new URL(req.url());
    if (url.origin !== base) { unexpected.push(url.origin); return route.abort(); }
    if (!url.pathname.startsWith('/api/')) return route.continue();
    const endpoint = url.pathname.slice(4), method = req.method();
    const body = () => JSON.parse(req.postData() || '{}');

    if (endpoint === '/admin/api-providers' || /\/providers\/[^/]+\/catalog$/.test(endpoint) ||
        /\/services\/[^/]+\/lookup$/.test(endpoint) || endpoint.startsWith('/service-account-sessions') ||
        endpoint.startsWith('/service-face-collection')) {
      supplierAttempts.push(`${method} ${endpoint}`);
      await route.abort();
      assert.fail(`providerless workflow attempted ${method} ${endpoint}`);
    }
    if (endpoint === '/admin/service-products' && method === 'GET')
      return respond(route, pageData(savedProduct ? [savedProduct] : []));
    if (endpoint === '/admin/service-products' && method === 'POST') {
      savedProduct = body();
      assert.equal(savedProduct.providerId, null);
      assert.equal(savedProduct.providerType, 'heisha');
      assert.equal(savedProduct.project, 'default');
      assert.equal(savedProduct.remoteProductId, '3');
      assert.equal(savedProduct.fulfillmentMode, 'SELF_OPERATED');
      product = { ...product, ...savedProduct, id: 1, available: true, version: 0 };
      return respond(route, product);
    }
    if (endpoint === '/services' && method === 'GET') return respond(route, pageData([product]));

    if (endpoint === '/service-fulfillment-material-drafts' && method === 'POST') {
      const input = body();
      assert.equal(input.productId, product.id);
      assert.equal(input.productVersion, product.version);
      assert.equal(input.authorizedBiometric, true);
      assert.match(input.imageData, /^data:image\/png;base64,/);
      const draftId = materialDraftIds[draftCount++];
      return respond(route, { id: draftId, expiresAt: '2026-09-25T12:15:00' });
    }
    if (endpoint === '/services/1/quotes' && method === 'POST') {
      const input = body();
      assert.equal(input.authorizedAccount, true);
      assert.equal(input.authorizedBiometric, true);
      assert.match(input.materialDraftId, /^[0-9a-f-]{36}$/);
      assert.equal(input.fields.phone, '13800138000');
      assert.equal(input.fields.password, 'browser-password-secret');
      assert.equal(input.accountSessionId, null);
      const index = quoteCount++;
      assert.ok(index < quoteIds.length, 'unexpected extra quote');
      return respond(route, { id: quoteIds[index], orderId: null, action: 'CREATE', state: 'READY',
        title: product.title, quantity: input.quantity, amount: '5.00', amountLabel: '订单金额',
        expiresAt: '2026-09-25T12:05:00', quantityUnit: '次', unitCharge: '0.50000000' });
    }
    const confirmation = endpoint.match(/^\/service-order-operations\/([^/]+)\/confirm$/);
    if (confirmation && method === 'POST') {
      const index = quoteIds.indexOf(confirmation[1]);
      assert.notEqual(index, -1);
      assert.equal(index, confirmations);
      activeIndex = index;
      confirmations++;
      return respond(route, { id: confirmation[1], orderId: orders[index].id, action: 'CREATE', state: 'SUCCEEDED' });
    }
    const orderDraft = endpoint.match(/^\/service-orders\/([^/]+)\/fulfillment\/material-drafts$/);
    if (orderDraft && method === 'POST') {
      assert.equal(orderDraft[1], activeOrder().id);
      const input = body();
      assert.equal(input.authorizedBiometric, true);
      assert.match(input.imageData, /^data:image\/png;base64,/);
      const draftId = materialDraftIds[draftCount++];
      return respond(route, { id: draftId, expiresAt: '2026-09-25T12:15:00' });
    }
    const updateMaterials = endpoint.match(/^\/service-orders\/([^/]+)\/fulfillment\/materials$/);
    if (updateMaterials && method === 'POST') {
      assert.equal(updateMaterials[1], activeOrder().id);
      const input = body();
      assert.equal(input.password, 'supplemented-password');
      assert.equal(input.authorizedBiometric, true);
      assert.match(input.materialDraftId, /^[0-9a-f-]{36}$/);
      assert.equal(input.note, '已补充资格核验图片');
      supplementRequests.push(input);
      activeOrder().verificationStatus = 'PENDING';
      activeOrder().verificationReason = null;
      activeOrder().fulfillmentVersion++;
      activeDetails().verificationStatus = 'PENDING';
      activeDetails().version++;
      activeDetails().materialVersion++;
      return respond(route, activeOrder());
    }
    if (endpoint === '/service-orders' || endpoint === '/admin/service-orders') {
      assert.equal(method, 'GET');
      const selectedOrder = activeIndex < 0 ? [] : [activeOrder()];
      return respond(route, pageData(selectedOrder));
    }
    const sync = endpoint.match(/^\/service-orders\/([^/]+)\/sync$/);
    if (sync && method === 'POST') {
      assert.equal(sync[1], activeOrder().id);
      return respond(route, activeOrder());
    }
    const detailsRequest = endpoint.match(/^\/admin\/service-orders\/([^/]+)\/fulfillment$/);
    if (detailsRequest && method === 'GET') {
      assert.equal(detailsRequest[1], activeOrder().id);
      return respond(route, activeDetails());
    }
    const assetRequest = endpoint.match(/^\/admin\/service-orders\/([^/]+)\/fulfillment\/assets\/([^/]+)$/);
    if (assetRequest && method === 'GET') {
      assert.equal(assetRequest[1], activeOrder().id);
      assert.equal(assetRequest[2], `asset-${activeIndex + 1}`);
      return route.fulfill({ status: 200, contentType: 'image/png', headers: { 'Cache-Control': 'no-store' }, body: png });
    }
    const verification = endpoint.match(/^\/admin\/service-orders\/([^/]+)\/fulfillment\/verification$/);
    if (verification && method === 'POST') {
      assert.equal(verification[1], activeOrder().id);
      const input = body();
      verifyActions.push(input.action);
      if (input.action === 'NEEDS_INFO') {
        assert.equal(input.note, '请补交清晰的人脸资格材料');
        activeOrder().verificationStatus = 'NEEDS_INFO';
        activeOrder().verificationReason = input.note;
      } else {
        assert.equal(input.action, 'VERIFY');
        activeOrder().verificationStatus = 'VERIFIED';
        activeOrder().verificationReason = null;
      }
      activeDetails().verificationStatus = activeOrder().verificationStatus;
      activeDetails().verificationNote = input.note;
      activeDetails().version++;
      activeOrder().fulfillmentVersion = activeDetails().version;
      return respond(route, activeOrder());
    }
    const fulfillment = endpoint.match(/^\/admin\/service-orders\/([^/]+)\/fulfillment$/);
    if (fulfillment && method === 'POST') {
      assert.equal(fulfillment[1], activeOrder().id);
      const input = body();
      fulfillmentActions.push(input.action);
      if (input.action === 'START') {
        assert.equal(activeOrder().verificationStatus, 'VERIFIED');
        activeOrder().status = 'ACTIVE';
      } else if (input.action === 'PROGRESS') {
        assert.equal(activeOrder().status, 'ACTIVE');
        activeOrder().completed = input.completed;
      } else if (input.action === 'COMPLETE') {
        activeOrder().status = 'COMPLETED';
        activeOrder().completed = activeOrder().quantity;
      } else assert.fail(`unexpected local lifecycle action ${input.action}`);
      activeOrder().version++;
      return respond(route, activeOrder());
    }
    unexpected.push(`${method} ${endpoint}`);
    return route.fulfill({ status: 404, body: '{}' });
  });

  const goto = path => page.goto(`${base}/__self?page=${encodeURIComponent(path)}`);
  const uploadMaterial = async (dialog, orderSpecific = false) => {
    const consentLabel = '我单独授权提交并处理本次人脸资格核验材料';
    await dialog.getByText(consentLabel, { exact: true }).click();
    await dialog.locator('input[type="file"]').setInputFiles({ name: 'qualification.png', mimeType: 'image/png', buffer: png });
    await dialog.getByRole('button', { name: '安全上传材料', exact: true }).click();
    await dialog.getByText('材料已安全暂存，可继续预览订单。', { exact: true }).waitFor();
    if (orderSpecific) assert.match(page.url(), /service-orders/);
  };
  const createOrder = async () => {
    await goto('/services');
    await page.getByRole('button', { name: '选择服务', exact: true }).click();
    await page.getByText('我有权使用此账号及信息，并授权提交', { exact: true }).click();
    const checkout = page.getByRole('dialog').last();
    await uploadMaterial(checkout);
    await checkout.locator('.el-form-item').filter({ hasText: '服务账号 / 手机号' }).locator('input').fill('13800138000');
    await checkout.locator('.el-form-item').filter({ hasText: '服务密码' }).locator('input').fill('browser-password-secret');
    await checkout.locator('.el-form-item').filter({ hasText: '期望执行时间' }).locator('input').fill('08:00');
    await page.getByRole('button', { name: '预览金额并下单', exact: true }).click();
    await page.getByRole('button', { name: '确认并下单', exact: true }).click();
    await page.getByRole('heading', { name: '我的服务订单' }).waitFor();
    await page.locator('.order-card').getByText('待资格核验', { exact: true }).waitFor();
    assert.equal(await page.evaluate(() => JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]).includes('browser-password-secret')), false);
    assert.equal(await page.evaluate((secret) => JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]).includes(secret), imageData), false);
  };
  const openAdminDetails = async () => {
    await page.getByRole('button', { name: '查看履约资料', exact: true }).click();
    await page.getByText(activeDetails().fields.password, { exact: true }).waitFor();
  };
  const verify = async () => {
    await page.getByRole('button', { name: '核验通过', exact: true }).click();
    await page.getByRole('button', { name: '确认提交', exact: true }).click();
    await page.getByRole('button', { name: '查看履约资料', exact: true }).waitFor();
  };

  await goto('/admin/service-products');
  await page.getByRole('button', { name: '上架服务商品', exact: true }).click();
  let dialog = page.getByRole('dialog').last();
  await dialog.locator('.el-form-item').filter({ hasText: '服务类型' }).locator('.el-select__wrapper').click();
  await page.getByRole('option', { name: '黑鲨', exact: true }).click();
  await dialog.locator('.el-form-item').filter({ hasText: '履约方式' }).locator('.el-select__wrapper').click();
  await page.getByRole('option', { name: '平台自营', exact: true }).click();
  assert.equal(await dialog.locator('.service-provider-select').count(), 0);
  assert.equal(await page.getByRole('button', { name: '读取目录', exact: true }).count(), 0);
  await dialog.locator('.el-form-item').filter({ hasText: '服务商品' }).locator('.el-select__wrapper').click();
  await page.getByRole('option', { name: '黑鲨商品 3', exact: true }).click();
  await dialog.locator('.el-form-item').filter({ hasText: '商品名称' }).locator('input').fill('黑鲨资格服务');
  await dialog.locator('.el-form-item').filter({ hasText: '销售单价' }).locator('input').fill('0.25');
  await dialog.locator('.el-form-item').filter({ hasText: '销售状态' }).locator('.el-switch__core').click();
  await dialog.getByRole('button', { name: '保存商品', exact: true }).click();
  await page.getByText('服务商品已保存', { exact: true }).waitFor();
  assert.equal(savedProduct.providerId, null);
  assert.equal(savedProduct.providerType, 'heisha');
  assert.equal(savedProduct.remoteProductId, '3');
  assert.equal(savedProduct.fulfillmentMode, 'SELF_OPERATED');

  await createOrder();
  assert.equal(confirmations, 1);
  await page.getByRole('button', { name: '刷新状态', exact: true }).click();
  await page.locator('.order-card').getByText('待处理', { exact: true }).waitFor();
  assert.equal(await page.locator('.order-card').getByRole('button', { name: '开始处理', exact: true }).count(), 0);

  await goto('/admin/service-orders');
  await page.getByRole('button', { name: '查看履约资料', exact: true }).waitFor();
  await openAdminDetails();
  assert.equal(await page.getByRole('button', { name: '查看人脸材料', exact: true }).count(), 1);
  await page.evaluate(async () => {
    const { applyAuthSession, sessionUserInfo, getAccessToken } = await import('/src/utils/authSession.js');
    applyAuthSession({ ...sessionUserInfo.value, token: getAccessToken(), permissions: ['api-provider:update', 'service-order:fulfill'] });
  });
  await page.getByRole('button', { name: '查看人脸材料', exact: true }).waitFor({ state: 'detached' });
  await page.evaluate(async () => {
    const { applyAuthSession, sessionUserInfo, getAccessToken } = await import('/src/utils/authSession.js');
    applyAuthSession({ ...sessionUserInfo.value, token: getAccessToken(), permissions: ['api-provider:update', 'service-order:fulfill', 'service-order:biometric'] });
  });
  await openAdminDetails();
  await page.getByRole('button', { name: '查看人脸材料', exact: true }).click();
  await page.locator('.asset-card img').waitFor();
  await page.locator('.el-drawer__close-btn:visible').click();
  await page.getByText('browser-password-secret', { exact: true }).waitFor({ state: 'hidden' });

  await openAdminDetails();
  await verify();
  await page.getByRole('button', { name: '开始处理', exact: true }).click();
  await page.getByRole('button', { name: '确认处理', exact: true }).click();
  await page.getByRole('button', { name: '更新进度', exact: true }).click();
  await page.getByRole('spinbutton', { name: '已完成数量' }).fill('3');
  await page.getByRole('button', { name: '确认处理', exact: true }).click();
  await page.getByRole('button', { name: '标记完成', exact: true }).click();
  await page.getByRole('button', { name: '确认处理', exact: true }).click();
  assert.deepEqual(fulfillmentActions, ['START', 'PROGRESS', 'COMPLETE']);
  assert.equal(orders[0].completed, 10);
  await goto('/service-orders');
  await page.locator('.order-card').getByText('已完成', { exact: true }).waitFor();

  await createOrder();
  assert.equal(confirmations, 2);
  await goto('/admin/service-orders');
  await openAdminDetails();
  await page.getByRole('button', { name: '要求补充资料', exact: true }).click();
  const verificationDialog = page.getByRole('dialog', { name: '要求补充资料', exact: true });
  await verificationDialog.locator('textarea').fill('请补交清晰的人脸资格材料');
  await verificationDialog.getByRole('button', { name: '确认提交', exact: true }).click();
  await page.getByRole('button', { name: '查看履约资料', exact: true }).waitFor();
  assert.equal(orders[1].verificationStatus, 'NEEDS_INFO');

  await goto('/service-orders');
  await page.getByRole('button', { name: '补充资料', exact: true }).click();
  dialog = page.getByRole('dialog', { name: '补充资格核验资料', exact: true });
  await dialog.locator('.el-form-item').filter({ hasText: '新密码' }).locator('input').fill('supplemented-password');
  await dialog.locator('.el-form-item').filter({ hasText: '补充说明' }).locator('textarea').fill('已补充资格核验图片');
  await uploadMaterial(dialog, true);
  await dialog.getByRole('button', { name: '重新提交核验', exact: true }).click();
  await page.locator('.order-card').getByText('待资格核验', { exact: true }).waitFor();
  assert.equal(supplementRequests.length, 1);
  assert.equal(orders[1].verificationStatus, 'PENDING');

  await goto('/admin/service-orders');
  await openAdminDetails();
  await verify();
  await page.getByRole('button', { name: '开始处理', exact: true }).click();
  await page.getByRole('button', { name: '确认处理', exact: true }).click();
  assert.equal(orders[1].status, 'ACTIVE');
  assert.deepEqual(verifyActions, ['VERIFY', 'NEEDS_INFO', 'VERIFY']);

  assert.equal(quoteCount, 2);
  assert.equal(draftCount, 3);
  assert.deepEqual(supplierAttempts, []);
  assert.deepEqual(unexpected, []);
  assert.deepEqual(errors, []);
  await page.locator('.el-loading-mask:visible').waitFor({ state: 'hidden' });
  await page.locator('.order-card').getByText('资格已通过', { exact: true }).first().waitFor();
  mkdirSync('../.cache/heisha-self-operated-ui', { recursive: true });
  await page.screenshot({ path: '../.cache/heisha-self-operated-ui/supplement-verified.png', fullPage: true, animations: 'disabled' });
  console.log('PASS providerless Black Shark SKU 3 publication, checkout, audited biometric access, local fulfillment, NEEDS_INFO and resubmission');
} finally {
  await browser?.close();
  await server.close();
}
