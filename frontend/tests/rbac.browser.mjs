import assert from 'node:assert/strict';
import { existsSync, mkdirSync } from 'node:fs';
import { chromium } from 'playwright';
import { createTestServer } from './fixtures/test-server.mjs';

// Started by RbacAuthContractTest: real SQL role policy, authentication and HTTP security.
// Never replace login/refresh responses or inject fixture permission arrays into successful sessions.
assert.ok(process.env.RBAC_AUTH_URL, 'Run via Maven -Dtest=RbacAuthContractTest -Drbac.browser=true');
const password = process.env.RBAC_TEST_PASSWORD;
const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"></head><body><div id="app"></div><script type="module">
import {createApp,h} from 'vue';import {createPinia} from 'pinia';import {RouterView} from 'vue-router';import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';
import router from '/src/router/index.js';
import {applyAuthSession,sessionUserInfo,getAccessToken,refreshAccessSession} from '/src/utils/authSession.js';
import request from '/src/utils/request.js';
window.revokedUrls=[];const revoke=URL.revokeObjectURL.bind(URL);URL.revokeObjectURL=url=>{window.revokedUrls.push(url);revoke(url)};
window.rbac={
 async login(username,password){const response=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password})});const body=await response.json();if(body.code!==1)throw Error(body.message);applyAuthSession(body.data);return body.data},
 async go(path){await router.push(path);return router.currentRoute.value.path},
 profile:()=>sessionUserInfo.value,
 token:getAccessToken,
 refresh:refreshAccessSession,
 async deniedAsset(path){try{await request.get(path,{responseType:'blob',suppressGlobalError:true});return 200}catch(e){return e.response?.status}},
 forge(){sessionUserInfo.value={...sessionUserInfo.value,permissions:['service-order:read','service-order:fulfill','service-order:biometric']};localStorage.setItem('userInfo',JSON.stringify(sessionUserInfo.value))}
};
createApp({render:()=>h(RouterView)}).use(createPinia()).use(router).use(ElementPlus).mount('#app');
</script></body></html>`;
const server = await createTestServer({ root: new URL('../', import.meta.url).pathname, logLevel: 'error',
  server: { proxy: { '/api': { target: process.env.RBAC_AUTH_URL, changeOrigin: false } } },
  plugins: [{ name: 'real-rbac-contract', configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
    if (!req.url?.startsWith('/__rbac')) return next();
    res.setHeader('Content-Type', 'text/html'); res.end(await vite.transformIndexHtml(req.url, html));
  }); } }],
});
const output = new URL('../../.cache/rbac-verification/browser/', import.meta.url).pathname;
mkdirSync(output, { recursive: true });
let browser;
try {
  await server.listen();
  const base = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({ executablePath: process.env.BROWSER_PATH || (existsSync('/snap/bin/chromium') ? '/snap/bin/chromium' : undefined), args: ['--no-sandbox'] });
  const page = await browser.newPage({ viewport: { width: 1440, height: 960 } });
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  // Stub only unrelated business data; every protected service/auth/RBAC request reaches Spring.
  await page.route('**/api/**', route => {
    const pathname = new URL(route.request().url()).pathname;
    if (!pathname.startsWith('/api/')) return route.continue();
    const path = pathname.slice(4);
    if (/^\/(auth|admin\/rbac|admin\/service-orders|admin\/service-products)/.test(path)) return route.continue();
    const data = path === '/customer-service/unread-count' ? 0 : path === '/announcement/system' ? null : path.startsWith('/announcement/') ? []
      : path === '/client/bootstrap' ? {} : /theme|config/.test(path) ? {} : /statistics|stats/.test(path) ? {}
      : /customer-service/.test(path) ? [] : { records: [], total: 0, current: 1, size: 20 };
    return route.fulfill({ json: { code: 1, data } });
  });
  await page.goto(`${base}/__rbac`);
  await page.waitForFunction(() => !!window.rbac);
  const login = username => page.evaluate(([u, p]) => window.rbac.login(u, p), [username, password]);
  const go = path => page.evaluate(p => window.rbac.go(p), path);
  const visible = text => page.getByRole('button', { name: text, exact: true }).isVisible();
  for (const [role, routes] of Object.entries({
    operator: ['/admin/service-products', '/admin/service-orders', '/admin/service-projects', '/admin/api-providers'],
    finance: ['/admin/service-orders'], support: ['/admin/customer-service'],
    auditor: ['/admin/service-products', '/admin/service-orders', '/admin/service-projects', '/admin/api-providers'],
    combined: ['/admin/service-products', '/admin/service-orders'], admin: ['/admin/rbac', '/admin/service-orders'],
  })) {
    const profile = await login(role);
    assert.ok(Array.isArray(profile.roles) && Array.isArray(profile.permissions));
    for (const route of routes) assert.equal(await go(route), route, `${role}: ${route}`);
    const menuNames = await page.locator('.sidebar .el-menu-item').allTextContents();
    const menuHas = name => menuNames.some(text => text.trim() === name);
    assert.equal(menuHas('服务商品'), ['operator', 'auditor', 'combined', 'admin'].includes(role));
    assert.equal(menuHas('服务订单与对账'), role !== 'support');
    assert.equal(menuHas('客服管理'), ['support', 'admin'].includes(role));
    if (role === 'finance') {
      await page.getByRole('button', { name: '核对资料与记录', exact: true }).waitFor();
      assert.equal(await visible('开始处理'), false);
      assert.equal(await visible('查看履约资料'), false);
      assert.equal(await visible('取消并全额退款'), true);
      await page.screenshot({ path: `${output}/finance-order-actions.png` });
      assert.equal(await go('/admin/service-products'), '/dashboard');
    }
    if (role === 'operator' || role === 'combined') {
      await go('/admin/service-orders');
      await page.getByRole('button', { name: '开始处理', exact: true }).waitFor();
      assert.equal(await visible('取消并全额退款'), role === 'combined');
      assert.equal(await visible('核对资料与记录'), role === 'combined');
    }
    if (role === 'support') assert.equal(await go('/admin/service-orders'), '/dashboard');
    if (role === 'auditor') {
      await go('/admin/service-orders');
      await page.getByText('权限测试订单', { exact: true }).waitFor();
      for (const action of ['开始处理', '取消并全额退款', '核对资料与记录', '查看履约资料']) assert.equal(await visible(action), false);
      await page.screenshot({ path: `${output}/auditor-read-only.png` });
      await go('/admin/api-providers?type=heisha');
      assert.equal(await visible('添加接口'), false);
      assert.equal(await page.getByRole('dialog').count(), 0, 'Read-only deep links never open provider edit forms');
      await go('/admin/service-products?providerId=1&providerType=heisha&project=default');
      assert.equal(await visible('上架服务商品'), false);
      assert.equal(await page.getByRole('dialog').count(), 0, 'Read-only deep links never open product edit forms');
    }
  }
  await login('ordinary');
  for (const path of ['/admin/service-products', '/admin/service-orders', '/admin/service-projects', '/admin/customer-service', '/admin/rbac']) assert.equal(await go(path), '/dashboard');

  // A real operator opens a private image. Revoke its role through the real administrative API.
  const admin = await login('admin');
  const operator = await login('operator');
  await go('/admin/service-orders');
  await page.getByRole('button', { name: '查看履约资料', exact: true }).click();
  await page.getByRole('button', { name: '查看人脸材料', exact: true }).click();
  await page.locator('img[src^="blob:"]').waitFor();
  await page.screenshot({ path: `${output}/operator-biometric.png` });
  assert.equal(await page.evaluate(async ({ token, uid }) => (await fetch(`/api/admin/rbac/users/${uid}/roles`, {
    method: 'PUT', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ roles: ['USER'] }),
  })).status, { token: admin.token, uid: operator.uid }), 200);
  const assetPath = '/admin/service-orders/10000000-0000-4000-8000-000000000001/fulfillment/assets/10000000-0000-4000-8000-000000000002';
  let deniedRequests = 0;
  page.on('request', req => { if (new URL(req.url()).pathname === `/api${assetPath}`) deniedRequests++; });
  assert.equal(await page.evaluate(path => window.rbac.deniedAsset(path), assetPath), 403);
  assert.equal(deniedRequests, 1, 'Denied business request is never replayed');
  await page.waitForFunction(() => window.rbac.profile().roles.includes('USER') && window.revokedUrls.length > 0);
  assert.equal(await page.locator('img[src^="blob:"]').count(), 0);
  assert.notEqual(await page.evaluate(() => window.rbac.token()), operator.token, 'Refresh rotated the access session');
  await page.evaluate(() => window.rbac.forge());
  assert.equal(await go('/admin/service-orders'), '/admin/service-orders', 'Forged data can affect UX only');
  assert.equal(await page.evaluate(path => window.rbac.deniedAsset(path), assetPath), 403);
  assert.deepEqual(await page.evaluate(() => window.rbac.profile().permissions), []);
  await page.screenshot({ path: `${output}/revoked-permissions.png` });
  assert.deepEqual(errors, []);
  console.log('PASS real auth RBAC browser: six roles + union, routes/menus/buttons, read-only deep links, revoked old JWT, forged UI rejected, one-shot 403 refresh, private Blob revoked');
} finally { await browser?.close(); await server.close(); }
