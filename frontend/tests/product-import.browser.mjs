import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { chromium } from 'playwright';
import { createServer } from 'vite';

// Real Vue dialog, intercepted API only: never imports or modifies production products.
const html = `<!doctype html><html><head><meta charset="UTF-8"></head><body><div id="app"></div>
<script type="module">
import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import Page from '/src/views/AdminPlatforms.vue';
import { applyAuthSession } from '/src/utils/authSession.js';
applyAuthSession({ token: 'test.' + btoa(JSON.stringify({exp: Date.now()/1000 + 3600})) + '.signature', userId: 7 });
createApp(Page).use(ElementPlus).mount('#app');
</script></body></html>`;
const server = await createServer({
  logLevel: 'error', server: { host: '127.0.0.1', port: 0, strictPort: false },
  plugins: [{ name: 'product-import-test', configureServer(vite) {
    vite.middlewares.use(async (request, response, next) => {
      if (request.url !== '/__product_import') return next();
      response.setHeader('Content-Type', 'text/html; charset=utf-8');
      response.end(await vite.transformIndexHtml(request.url, html));
    });
  } }],
});
let browser;
try {
  await server.listen();
  const baseURL = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({
    executablePath: process.env.BROWSER_PATH || (existsSync('/snap/bin/chromium') ? '/snap/bin/chromium' : undefined),
    headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'],
  });
  const page = await browser.newPage({ viewport: { width: 1500, height: 1000 } });
  page.setDefaultTimeout(15000);
  const unexpected = [], consoleErrors = [], imports = [], queries = [];
  page.on('pageerror', error => consoleErrors.push(error.message));
  await page.route('**/*', async route => {
    const url = new URL(route.request().url());
    if (url.origin !== baseURL) { unexpected.push(url.origin); return route.abort(); }
    if (!url.pathname.startsWith('/api/')) return route.continue();
    const respond = data => route.fulfill({ status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 1, message: '操作成功', success: true, data }) });
    if (url.pathname === '/api/admin/api-providers') {
      return respond({ records: [{ id: 6, name: '分类导入测试接口', status: 1 },
        { id: 7, name: '其他测试接口', status: 1 }], total: 2 });
    }
    if (url.pathname === '/api/admin/platforms' || url.pathname === '/api/admin/platform-categories') {
      return respond({ records: [], total: 0 });
    }
    if (url.pathname === '/api/admin/docking/products') {
      queries.push(Object.fromEntries(url.searchParams));
      return respond([{ id: 'selected', name: '待导入的测试商品', categoryId: url.searchParams.get('categoryId') || '90',
        categoryName: '测试分类', price: 5, imported: false }]);
    }
    if (url.pathname === '/api/admin/docking/import-products' && route.request().method() === 'POST') {
      imports.push(route.request().postDataJSON());
      return respond({ requested: 1, success: 1, created: 1, updated: 0, missing: 0, fail: 0 });
    }
    unexpected.push(url.pathname);
    return route.abort();
  });
  await page.goto(`${baseURL}/__product_import`, { timeout: 60000 });
  const dialog = page.getByRole('dialog', { name: '查询并导入第三方商品' });
  const providerSelect = dialog.locator('.el-select').first();
  const categoryInput = dialog.getByPlaceholder('可选，不传查询全部');
  const submit = dialog.getByRole('button', { name: /导入选中商品/ });
  async function openImport() {
    await page.getByRole('button', { name: '一键导入', exact: true }).click();
    await dialog.waitFor();
    await providerSelect.click();
    await page.getByRole('option', { name: '分类导入测试接口', exact: true }).click();
  }
  async function queryAndSelect() {
    await dialog.getByRole('button', { name: '查询商品', exact: true }).click();
    const row = dialog.locator('.el-table__body-wrapper tbody tr').filter({ hasText: '待导入的测试商品' });
    await row.waitFor();
    await row.locator('.el-checkbox').click();
    assert.equal(await row.getByRole('checkbox').isChecked(), true);
  }
  // Submit the category of the displayed list, not a subsequently edited input value.
  await openImport();
  await categoryInput.fill(' 60 ');
  await queryAndSelect();
  await categoryInput.fill('61');
  await submit.click();
  await dialog.waitFor({ state: 'hidden' });
  assert.equal(queries[0].categoryId, '60');
  assert.equal(imports[0].categoryId, '60');
  assert.deepEqual(imports[0].productIds, ['selected']);
  assert.equal(imports[0].priceMultiplier, 1);
  assert.equal(imports[0].syncCategories, true);
  assert.equal(Object.hasOwn(imports[0], 'products'), false, 'Do not submit untrusted product snapshots');

  // A new unfiltered query must clear the previous category snapshot.
  await openImport();
  await queryAndSelect();
  await categoryInput.fill('60');
  await submit.click();
  await dialog.waitFor({ state: 'hidden' });
  assert.equal(queries[1].categoryId, undefined);
  assert.equal(imports[1].categoryId, null);

  // A provider change invalidates both selected products and their source category.
  await openImport();
  await categoryInput.fill('60');
  await queryAndSelect();
  await providerSelect.click();
  await page.getByRole('option', { name: '其他测试接口', exact: true }).click();
  assert.equal(await submit.isDisabled(), true);
  await categoryInput.fill('');
  await queryAndSelect();
  await submit.click();
  await dialog.waitFor({ state: 'hidden' });
  assert.equal(imports[2].apiProviderId, 7);
  assert.equal(imports[2].categoryId, null);
  assert.equal(imports.length, 3, 'Never retry imports implicitly');
  assert.deepEqual(unexpected, []);
  assert.deepEqual(consoleErrors, []);
  console.log('Product import browser regression passed: category query → selection → import; query snapshot; reset/provider change; no implicit retry.');
} finally {
  await browser?.close();
  await server.close();
}
