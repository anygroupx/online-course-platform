/** Native Vue workflows against local fixtures only. Every API is mocked, including all writes. */
import assert from 'node:assert/strict'
import test from 'node:test'
import { existsSync, mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import vue from '@vitejs/plugin-vue'
import { chromium } from 'playwright'
import { createTestServer } from './fixtures/test-server.mjs'

const routerModule = '/__plugin_workflow_router.mjs'
const routerSource = `import {createRouter,createMemoryHistory} from 'vue-router';
const marker={render:()=>null};
export default createRouter({history:createMemoryHistory(),routes:[
{path:'/admin/plugin-integrations',component:()=>import('/src/views/AdminPluginIntegrations.vue')},
{path:'/admin/api-providers',component:()=>import('/src/views/AdminApiProviders.vue')},
{path:'/admin/service-products',component:()=>import('/src/views/AdminServiceProducts.vue')},
{path:'/services',component:()=>import('/src/views/ServiceStore.vue')},
{path:'/service-orders',component:()=>import('/src/views/ServiceOrders.vue')},
{path:'/admin/service-orders',component:()=>import('/src/views/ServiceOrders.vue'),meta:{serviceAdmin:true}},
...['/admin/docking','/admin/products','/admin/orders','/project-center','/project-clients','/admin/projects','/admin/platforms','/courses','/admin/service-projects','/service-projects'].map(path=>({path,component:marker}))]});`
const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;background:var(--bg-body)"><div id="app"></div><script type="module">
import {createApp,h,KeepAlive} from 'vue';import {RouterView,RouterLink} from 'vue-router';
import ElementPlus from 'element-plus';import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';
import '/src/styles/variables.scss';import '/src/styles/element-overrides.scss';import '/src/styles/global.css';import '/src/styles/fluent-spatial.scss';import '/src/styles/responsive.scss';
import router from '${routerModule}';globalThis.__workflowRouter=router;import {applyAuthSession} from '/src/utils/authSession.js';
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7});
await router.push(new URLSearchParams(location.search).get('page')||'/admin/plugin-integrations');await router.isReady();
createApp({render:()=>h('main',{style:'padding:16px'},[
h('nav',{style:'display:flex;flex-wrap:wrap;gap:20px;margin-bottom:20px'},[
h(RouterLink,{to:'/admin/plugin-integrations','data-testid':'nav-plugins'},()=> '功能配置'),
h(RouterLink,{to:'/admin/service-products','data-testid':'nav-products'},()=> '商品管理')]),
h(RouterView,null,{default:({Component})=>h(KeepAlive,null,{default:()=>Component})})])}).use(router).use(ElementPlus).mount('#app');
</script></body></html>`
const server = await createTestServer({ configFile: false, logLevel: 'error',
  resolve: { alias: [{ find: /^@\/router$/, replacement: routerModule }, { find: '@', replacement: resolve('src') }] },
  plugins: [vue(), { name: 'shared-native-plugin-router',
    resolveId(id) { if (id === routerModule) return '\0plugin-workflow-router' },
    load(id) { if (id === '\0plugin-workflow-router') return routerSource },
    configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
      if (!req.url?.startsWith('/__plugin_workflow?') && req.url !== '/__plugin_workflow') return next()
      res.setHeader('Content-Type', 'text/html;charset=utf-8')
      res.end(await vite.transformIndexHtml(req.url, html))
    }) },
  }],
})
const entries = [
  ['P02', '27', 'EXISTING', ['COURSE_CATALOG', 'BATCH_PROGRESS']],
  ['P04', 'jiguang', 'NATIVE_PARTIAL', ['CATALOG', 'SCHOOLS']],
  ['P06', 'sxdk_tw', 'NATIVE_PARTIAL', []],
  ['P07', 'syyv5', 'NATIVE_PARTIAL', ['PROJECT_CENTER']],
  ['P08', 'jingyu', 'NATIVE_PARTIAL', ['CATALOG']],
  ['P11', null, 'DUPLICATE', []],
].map(([id, providerType, integrationStatus, availableCapabilities]) => ({
  id, providerType, integrationStatus, availableCapabilities, name: id, category: '服务功能',
  duplicateOf: id === 'P11' ? 'P08' : null, observedFeatures: [], blockers: [], evidence: [],
  projects: id === 'P08' ? [{ id: 'keep', name: 'Keep 自由跑' }] : [],
  serviceCapabilities: ['jiguang', 'sxdk_tw', 'jingyu'].includes(providerType) ? ['CREATE', 'SYNC'] : [],
}))
const verified = '2026-09-13T10:00:00'
const provider = { id: 509, name: '校园运动配置', providerType: 'jiguang', status: 1,
  verifiedAt: verified, verifiedBy: 7, lastCheckReason: 'SUCCESS', apiUrl: 'https://service.example',
  usernameMasked: '10***01', hasApiKey: true }
const remote = { id: '1', name: '晨间运动', unitPrice: '0.10', priceUnit: '元/公里' }
const product = { id: 21, providerId: 509, providerType: 'jiguang', project: 'default', remoteProductId: '1',
  title: '晨间运动', description: '核对次数和距离后确认下单。', unitPrice: '0.25', priceUnit: '元/公里',
  available: true, enabled: true, version: 0, capabilities: ['CREATE', 'SYNC'] }
const operationId = '19e3e7b1-a2f6-417b-ab52-f8811b3ad2e1'
const orderId = '08e3e7b1-a2f6-417b-ab52-f8811b3ad2e2'
const order = { id: orderId, title: product.title, providerType: 'jiguang', project: 'default',
  accountLabel: '20***01', status: 'ACTIVE', quantity: 10, completed: 0, distance: '2.00',
  paidAmount: '5.00', refundedAmount: '0.00', actions: [], createTime: verified }
const screenshots = resolve('../.cache/plugin-workflow-ui')
mkdirSync(screenshots, { recursive: true })
let browser, base

function deferred() {
  let resolve
  const promise = new Promise((done) => { resolve = done })
  return { promise, resolve }
}
async function fixture(t, options = {}) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1050 } })
  const page = await context.newPage(), calls = [], errors = [], unexpected = []
  const state = { providers: structuredClone(options.providers || []), products: [], created: false }
  page.setDefaultTimeout(15000)
  page.setDefaultNavigationTimeout(60000)
  page.on('pageerror', error => errors.push(error.message))
  await context.route('**/*', async (route) => {
    const req = route.request(), url = new URL(req.url()), method = req.method()
    if (url.origin !== base) { unexpected.push(req.url()); return route.abort() }
    if (!url.pathname.startsWith('/api/')) return route.continue()
    const endpoint = url.pathname.slice(4), params = Object.fromEntries(url.searchParams)
    const body = req.postDataJSON() || null
    calls.push({ method, endpoint, params, body })
    const respond = (data, status = 200) => route.fulfill({ status, json: {
      code: status === 200 ? 1 : -1, data, message: status === 200 ? '操作成功' : '无权查看配置',
    } })
    const paged = (rows) => {
      const current = Number(params.page || 1), size = Number(params.pageSize || 20)
      return { records: rows.slice((current - 1) * size, current * size), total: rows.length, current, size }
    }
    if (endpoint === '/admin/plugin-integrations' && method === 'GET') return respond(entries)
    const plugin = endpoint.match(/^\/admin\/plugin-integrations\/(P\d+)\/providers(?:\/(\d+)\/(catalog|schools))?$/)
    if (plugin && method === 'GET') {
      const [, pluginId, id, action] = plugin
      assert.notEqual(pluginId, 'P11', 'shared entry must never call a duplicate API')
      if (action === 'catalog') {
        assert.notEqual(pluginId, 'P06', 'internship has no catalog connector')
        const rows = options.catalog ? await options.catalog(id, params, calls) : [remote]
        return respond(rows)
      }
      assert.equal(action, undefined, 'school reads must remain explicit')
      const type = entries.find(entry => entry.id === pluginId)?.providerType
      return respond(paged(state.providers.filter(row => row.providerType === type).map(row => ({
        id: row.id, name: row.name, providerType: row.providerType, status: row.status, verified: !!row.verifiedAt,
      }))))
    }
    if (endpoint === '/admin/api-providers' && method === 'GET') {
      const rows = options.listRows || state.providers
      return respond(paged(rows.filter(row => (!params.providerTypes || params.providerTypes.split(',').includes(row.providerType)) &&
        (!params.status || row.status === Number(params.status)) && (!params.keyword || row.name.includes(params.keyword)))))
    }
    const meta = endpoint.match(/^\/admin\/api-providers\/(\d+)$/)
    if (meta && method === 'GET') {
      const result = options.metadata ? await options.metadata(meta[1], calls) : state.providers.find(row => row.id === Number(meta[1]))
      if (result?.httpStatus) return respond(null, result.httpStatus)
      return respond(result || null)
    }
    if (options.writes && endpoint === '/admin/api-providers' && method === 'POST') {
      assert.equal(body.status, 2)
      assert.equal(body.providerType, 'jiguang')
      assert.equal(body.username, '10001')
      assert.equal(body.apiKey, 'fixture-only-key')
      state.providers.push({ ...provider, name: body.name, status: 2, verifiedAt: null, lastCheckReason: null })
      return respond(provider.id)
    }
    if (options.writes && endpoint === `/admin/api-providers/${provider.id}/test-connection` && method === 'POST') {
      Object.assign(state.providers[0], { verifiedAt: verified, lastCheckReason: 'SUCCESS' })
      return respond({ apiUrl: provider.apiUrl, normalizedHost: 'service.example', verifiedAt: verified,
        verifiedBy: 7, durationMs: 25, status: 2 })
    }
    if (options.writes && endpoint === `/admin/api-providers/${provider.id}/status` && method === 'PATCH') {
      assert.equal(body.status, 1)
      assert.equal(state.providers[0].lastCheckReason, 'SUCCESS')
      state.providers[0].status = 1
      return respond(null)
    }
    if (endpoint === '/admin/service-products' && method === 'GET') return respond(paged(state.products))
    if (options.writes && endpoint === '/admin/service-products' && method === 'POST') {
      assert.equal(body.enabled, true)
      assert.equal(body.providerId, provider.id)
      assert.equal(body.unitPrice, '0.25')
      assert.equal(body.project, 'default')
      assert.equal(body.remoteProductId, '1')
      assert.equal(body.contractPrice, null)
      assert.equal(body.apiKey, undefined)
      assert.equal(body.password, undefined)
      state.products = [{ ...product, ...body }]
      return respond(state.products[0])
    }
    if (endpoint === '/services' && method === 'GET') {
      assert.equal(params.providerType, 'jiguang')
      return respond(paged(state.products))
    }
    if (options.writes && endpoint === `/services/${product.id}/quotes` && method === 'POST') {
      assert.equal(body.authorizedAccount, true)
      assert.equal(body.fields.studentAccount, '2026001')
      assert.equal(body.quantity, 10)
      assert.equal(body.distance, '2')
      assert.equal(body.amount, undefined)
      return respond({ id: operationId, orderId: null, action: 'CREATE', state: 'READY',
        title: product.title, quantity: 10, amount: '5.00', amountLabel: '本次余额扣款', expiresAt: '2099-01-01T00:00:00' })
    }
    if (options.writes && endpoint === `/service-order-operations/${operationId}/confirm` && method === 'POST') {
      assert.equal(state.created, false, 'confirmation must not replay')
      state.created = true
      return respond({ id: operationId, orderId, action: 'CREATE', state: 'SUCCEEDED' })
    }
    if (['/service-orders', '/admin/service-orders'].includes(endpoint) && method === 'GET') {
      return respond(paged(state.created ? [order] : []))
    }
    unexpected.push(`${method} ${endpoint}`)
    return route.abort()
  })
  t.after(async () => {
    if (t.passed === false) await page.screenshot({ path: `${screenshots}/failure-${t.name.replace(/[^a-z0-9]+/gi, '-').slice(0, 90)}.png`, fullPage: true }).catch(() => {})
    await context.close()
    assert.deepEqual(unexpected, [], 'no unmocked network or implicit mutations')
    assert.deepEqual(errors, [], 'no browser runtime errors')
  })
  await page.goto(`${base}/__plugin_workflow?page=${encodeURIComponent(options.path || '/admin/plugin-integrations')}`)
  return { page, calls, state,
    metadataCalls: () => calls.filter(c => /^\/admin\/api-providers\/\d+$/.test(c.endpoint)),
    catalogCalls: () => calls.filter(c => c.endpoint.endsWith('/catalog')),
    writeCalls: () => calls.filter(c => c.method !== 'GET'),
  }
}
const navigate = (page, target) => page.evaluate(async target => {
  await globalThis.__workflowRouter.push(target)
}, target)
const currentRoute = (page) => page.evaluate(() => {
  const { path, query } = globalThis.__workflowRouter.currentRoute.value
  return { path, query }
})
async function arrivedWithin(promise) {
  let timer
  try { return await Promise.race([promise, new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error('expected mocked request was not received')), 15000)
  })]) } finally { clearTimeout(timer) }
}
async function setupPlugin(page, id) {
  await page.getByTestId(`plugin-${id}`).getByRole('button', { name: '配置与使用', exact: true }).click()
  await page.getByRole('button', { name: '刷新配置', exact: true }).waitFor()
}
async function selectPluginProvider(page, name = provider.name) {
  await page.locator('.provider-selector .el-select').click()
  await page.getByRole('option', { name: `${name} · 已验证启用`, exact: true }).click()
}
const productDialog = (page) => page.getByRole('dialog', { name: '上架服务商品', exact: true })
const publishPath = (id = provider.id, providerType = 'jiguang', extra = '') =>
  `/admin/service-products?providerType=${providerType}&providerId=${id}${extra}`

await test('plugin configuration, publishing and native purchase workflow', async (t) => {
  try {
    await server.listen()
    base = `http://127.0.0.1:${server.httpServer.address().port}`
    browser = await chromium.launch({ headless: true,
      executablePath: process.env.BROWSER_PATH || (existsSync('/snap/bin/chromium') ? '/snap/bin/chromium' : undefined),
      args: ['--no-sandbox', '--disable-dev-shm-usage'] })

    await t.test('configure, verify, enable, publish and confirm once through real pages with KeepAlive', async (t) => {
      const { page, calls, state, writeCalls, catalogCalls, metadataCalls } = await fixture(t, { writes: true })
      await setupPlugin(page, 'P04')
      await page.getByText('尚未配置此服务。', { exact: false }).waitFor()
      assert.equal(await page.getByRole('button', { name: '上架此服务商品', exact: true }).isEnabled(), false)
      assert.equal(writeCalls().length, 0)
      await page.getByRole('button', { name: '添加此服务接口', exact: true }).click()
      const config = page.getByRole('dialog', { name: '添加接口', exact: true })
      await config.waitFor()
      assert.deepEqual(await currentRoute(page), { path: '/admin/api-providers', query: { type: 'jiguang' } })
      await config.getByText('极光（服务接口）', { exact: true }).waitFor()
      await config.getByPlaceholder('请输入接口名称', { exact: true }).fill(provider.name)
      await config.getByPlaceholder('https://provider.example.com 或 /openapi 基础目录').fill(provider.apiUrl)
      await config.getByPlaceholder('请输入账号', { exact: true }).fill('10001')
      await config.getByPlaceholder('请输入API Key', { exact: true }).fill('fixture-only-key')
      await config.getByRole('button', { name: '保存', exact: true }).click()
      const row = page.locator('.el-table__body-wrapper tbody tr').filter({ hasText: provider.name })
      await row.getByText('待验证', { exact: true }).waitFor()
      assert.equal(await row.getByRole('button', { name: '启用', exact: true }).isEnabled(), false)
      assert.equal(writeCalls().length, 1)
      await row.getByRole('button', { name: '测试连接', exact: true }).click()
      await page.getByRole('dialog', { name: '连接测试通过', exact: true }).getByRole('button', { name: '关闭', exact: true }).click()
      await row.getByText('待启用', { exact: true }).waitFor()
      assert.equal(state.providers[0].status, 2, 'connection testing does not enable sales')
      await row.getByRole('button', { name: '启用', exact: true }).click()
      await row.getByText('已启用', { exact: true }).waitFor()
      assert.equal(await page.getByText('fixture-only-key', { exact: false }).count(), 0)
      await page.getByTestId('nav-plugins').click()
      await setupPlugin(page, 'P04')
      await selectPluginProvider(page)
      assert.equal(catalogCalls().length, 0)
      await page.getByRole('button', { name: '上架此服务商品', exact: true }).click()
      const dialog = productDialog(page)
      await dialog.waitFor()
      assert.equal(metadataCalls().length, 1, 'publishing freshly revalidates saved metadata')
      assert.equal(catalogCalls().length, 0)
      assert.equal(await dialog.getByPlaceholder('例如 0.25；不能低于成本价').inputValue(), '')
      assert.equal(await dialog.getByRole('switch').getAttribute('aria-checked'), 'false')
      assert.equal(await dialog.getByRole('button', { name: '保存商品', exact: true }).isEnabled(), false)
      await dialog.getByRole('button', { name: '读取目录', exact: true }).click()
      await dialog.getByText('先读取服务目录', { exact: true }).click()
      await page.getByRole('option', { name: '晨间运动 · 成本 ¥0.10', exact: true }).click()
      await dialog.getByPlaceholder('例如 0.25；不能低于成本价').fill('0.25')
      await dialog.locator('.el-switch__core').click()
      await page.screenshot({ path: `${screenshots}/publication-desktop.png`, animations: 'disabled' })
      await dialog.getByRole('button', { name: '保存商品', exact: true }).click()
      await dialog.waitFor({ state: 'hidden' })
      assert.equal(state.products.length, 1)
      await page.getByTestId('nav-plugins').click()
      await setupPlugin(page, 'P04')
      await page.getByRole('button', { name: '查看服务商城', exact: true }).click()
      await page.getByRole('button', { name: '选择服务', exact: true }).click()
      await page.getByText('我有权使用此账号及信息，并授权提交', { exact: true }).click()
      await page.getByPlaceholder('填写学校全称').fill('示例大学')
      await page.getByPlaceholder('填写本人姓名').fill('测试用户')
      await page.getByPlaceholder('填写学号').fill('2026001')
      assert.equal(calls.filter(c => c.endpoint.endsWith('/quotes')).length, 0)
      await page.getByRole('button', { name: '预览金额并下单', exact: true }).click()
      const quote = page.getByRole('dialog', { name: '确认本次操作', exact: true })
      await quote.getByText('¥5.00', { exact: true }).waitFor()
      assert.equal(state.created, false)
      await quote.getByRole('button', { name: '确认并下单', exact: true }).click()
      await page.locator('.order-card.focused').waitFor()
      await quote.waitFor({ state: 'hidden' })
      assert.deepEqual(await currentRoute(page), { path: '/service-orders', query: { focus: orderId, providerType: 'jiguang' } })
      assert.equal(await page.getByRole('dialog').count(), 0, 'cached checkout must not cover the order page')
      const search = calls.filter(c => c.endpoint === '/service-orders').at(-1)
      assert.equal(search.params.providerType, 'jiguang')
      assert.equal(search.params.orderId, orderId)
      assert.equal(search.params.userId, undefined)
      assert.equal(calls.filter(c => c.endpoint.endsWith('/confirm')).length, 1)
      await page.setViewportSize({ width: 390, height: 844 })
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
      await page.screenshot({ path: `${screenshots}/orders-mobile.png`, animations: 'disabled', fullPage: true })
      await page.evaluate(() => document.documentElement.classList.add('dark'))
      await page.screenshot({ path: `${screenshots}/orders-mobile-dark.png`, animations: 'disabled', fullPage: true })
      await page.getByRole('button', { name: '购买服务', exact: true }).click()
      await page.getByRole('button', { name: '选择服务', exact: true }).click()
      assert.equal(await page.getByPlaceholder('填写学号').inputValue(), '', 'completed account context is not reused')
      assert.equal(await page.getByRole('button', { name: '预览金额并下单', exact: true }).isEnabled(), false)
      assert.equal(calls.filter(c => c.endpoint.endsWith('/confirm')).length, 1)
    })

    await t.test('valid selection outside the first 50 rows stays selected without copying prices or secrets', async (t) => {
      const rows = Array.from({ length: 51 }, (_, i) => ({ ...provider, id: i + 1, name: `较早配置-${i + 1}` }))
      const { page, calls, catalogCalls, writeCalls } = await fixture(t, { providers: [provider], listRows: rows, path: publishPath(),
        metadata: () => ({ ...provider, apiKey: 'must-not-copy-fixture', unitPrice: '999.99' }) })
      const dialog = productDialog(page)
      await dialog.getByText(`${provider.name} · 极光`, { exact: true }).waitFor()
      await page.waitForFunction(() => !document.querySelector('.provider-row .el-button.is-loading'))
      await dialog.getByRole('combobox', { name: '已保存的服务接口', exact: true }).click()
      assert.equal(await page.getByRole('option', { name: `${provider.name} · 极光`, exact: true }).getAttribute('aria-selected'), 'true')
      assert.equal(await dialog.getByPlaceholder('例如 0.25；不能低于成本价').inputValue(), '')
      assert.equal(await page.getByText('must-not-copy-fixture', { exact: false }).count(), 0)
      assert.equal(calls.find(c => c.endpoint === '/admin/api-providers').params.providerTypes, 'jiguang')
      assert.equal(catalogCalls().length, 0)
      assert.equal(writeCalls().length, 0)
    })

    await t.test('revalidation rejects disabled, unverified, mismatched, missing and forbidden configurations', async (t) => {
      for (const [name, value] of [ ['disabled', { ...provider, status: 0 }], ['pending', { ...provider, status: 2 }],
        ['unverified', { ...provider, verifiedAt: null }], ['wrong-type', { ...provider, providerType: 'Daytime' }],
        ['wrong-id', { ...provider, id: 1 }], ['missing', null], ['forbidden', { httpStatus: 403 }] ]) {
        await t.test(name, async (t) => {
          const { page, metadataCalls, catalogCalls, writeCalls } = await fixture(t, { path: publishPath(), metadata: () => value })
          await page.getByText(name === 'forbidden' ? '无法核对所选服务接口，请检查查看权限和接口配置后重试。'
            : '所选接口不存在、类型不匹配或尚未验证启用，请检查配置后重试。', { exact: true }).waitFor()
          assert.equal(await productDialog(page).count(), 0)
          assert.equal(metadataCalls().length, 1)
          assert.equal(catalogCalls().length, 0)
          assert.equal(writeCalls().length, 0)
        })
      }
    })

    await t.test('malformed route values do not trigger metadata reads or publication', async (t) => {
      const { page, metadataCalls, catalogCalls, writeCalls } = await fixture(t)
      for (const path of [publishPath('0'), publishPath('1e3'), publishPath('509', 'Daytime'),
        publishPath('509', 'jiguang', '&providerId=1'), publishPath('509', 'jiguang', '&project=unknown'),
        publishPath('509', 'jiguang', '&project=default&project=default'), '/admin/service-products?project=default']) {
        await navigate(page, path)
        await page.getByText('上架信息不完整或不正确，请从功能页重新选择服务配置。', { exact: true }).waitFor()
        assert.equal(await productDialog(page).count(), 0)
      }
      assert.equal(metadataCalls().length, 0)
      assert.equal(catalogCalls().length, 0)
      assert.equal(writeCalls().length, 0)
    })

    await t.test('stale metadata cannot replace newer routes or a manually opened form', async (t) => {
      const gate = deferred(), arrived = deferred()
      const other = { ...provider, id: 510, name: '新选择配置' }
      const { page, catalogCalls, writeCalls } = await fixture(t, { providers: [provider, other],
        metadata: async id => { if (id === '509') { arrived.resolve(); await gate.promise; return provider }; return other } })
      await navigate(page, publishPath())
      await arrivedWithin(arrived.promise)
      await navigate(page, publishPath(510))
      await productDialog(page).getByText(`${other.name} · 极光`, { exact: true }).waitFor()
      gate.resolve()
      await page.waitForTimeout(100)
      assert.equal(await productDialog(page).getByText(`${other.name} · 极光`, { exact: true }).count(), 1)
      await productDialog(page).getByRole('button', { name: '取消', exact: true }).click()
      const manualGate = deferred(), manualArrived = deferred()
      const second = await fixture(t, { providers: [provider], metadata: async () => { manualArrived.resolve(); await manualGate.promise; return provider } })
      await navigate(second.page, publishPath())
      await arrivedWithin(manualArrived.promise)
      await second.page.getByRole('button', { name: '上架服务商品', exact: true }).click()
      await productDialog(second.page).waitFor()
      manualGate.resolve()
      await second.page.waitForTimeout(100)
      assert.equal(await productDialog(second.page).getByRole('combobox', { name: '已保存的服务接口', exact: true }).inputValue(), '')
      assert.equal(catalogCalls().length, 0)
      assert.equal(writeCalls().length, 0)
    })

    await t.test('internship uses manual contract verification, never a fabricated catalog', async (t) => {
      const internship = { ...provider, providerType: 'sxdk_tw', name: '实习计划配置' }
      const { page, catalogCalls, writeCalls } = await fixture(t, { providers: [internship] })
      await setupPlugin(page, 'P06')
      assert.equal(await page.getByRole('tab', { name: '商品 / 报价', exact: true }).count(), 0)
      await selectPluginProvider(page, internship.name)
      await page.getByRole('button', { name: '上架此服务商品', exact: true }).click()
      const dialog = productDialog(page)
      await dialog.getByText('当前服务没有可验证的实时报价，不使用预设价格。', { exact: true }).waitFor()
      await dialog.getByText('已核实的每服务日成本', { exact: true }).waitFor()
      assert.equal(await dialog.getByPlaceholder('例如 0.25；不能低于成本价').inputValue(), '')
      assert.equal(await dialog.getByRole('button', { name: '读取目录', exact: true }).count(), 0)
      await page.setViewportSize({ width: 390, height: 844 })
      await page.screenshot({ path: `${screenshots}/internship-contract-mobile.png`, animations: 'disabled' })
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
      await page.evaluate(() => document.documentElement.classList.add('dark'))
      await page.screenshot({ path: `${screenshots}/internship-contract-mobile-dark.png`, animations: 'disabled' })
      assert.notEqual(await dialog.evaluate(el => getComputedStyle(el).backgroundColor), 'rgb(255, 255, 255)')
      assert.equal(catalogCalls().length, 0)
      assert.equal(writeCalls().length, 0)
    })
    await t.test('missing configuration explains the next step and opens the matching type without writes', async (t) => {
      const { page, metadataCalls, writeCalls } = await fixture(t, { path: '/admin/service-products?providerType=jiguang' })
      await page.getByRole('button', { name: '上架服务商品', exact: true }).click()
      await productDialog(page).getByText('没有匹配的已启用服务接口。请先添加对应服务，完成验证并启用。', { exact: true }).waitFor()
      await page.getByRole('button', { name: '添加此服务接口', exact: true }).click()
      await page.getByRole('dialog', { name: '添加接口', exact: true }).getByText('极光（服务接口）', { exact: true }).waitFor()
      assert.deepEqual(await currentRoute(page), { path: '/admin/api-providers', query: { type: 'jiguang' } })
      assert.equal(metadataCalls().length, 0)
      assert.equal(writeCalls().length, 0)
    })

    await t.test('a permission failure can be retried; clearing the publication route closes the old selection', async (t) => {
      let forbidden = true
      const { page, metadataCalls, writeCalls } = await fixture(t, { providers: [provider], path: publishPath(),
        metadata: () => forbidden ? { httpStatus: 403 } : provider })
      await page.getByText('无法核对所选服务接口，请检查查看权限和接口配置后重试。', { exact: true }).waitFor()
      forbidden = false
      await page.getByRole('button', { name: '重新核对配置', exact: true }).click()
      await productDialog(page).waitFor()
      assert.equal(metadataCalls().length, 2)
      await navigate(page, '/admin/service-products?providerType=sxdk_tw')
      await productDialog(page).waitFor({ state: 'hidden' })
      assert.equal(writeCalls().length, 0)
    })

    await t.test('a newly unverified list row overrides pinned verified metadata and cannot be read or saved', async (t) => {
      const { page, writeCalls, catalogCalls } = await fixture(t, { path: publishPath(), providers: [provider],
        // Exercise a configuration changed while its saved metadata was being revalidated.
        listRows: [{ ...provider, verifiedAt: null }] })
      const dialog = productDialog(page)
      await dialog.getByText(`${provider.name} · 极光`, { exact: true }).waitFor()
      await page.waitForFunction(() => !document.querySelector('.provider-row .el-button.is-loading'))
      assert.equal(await dialog.getByRole('button', { name: '读取目录', exact: true }).isEnabled(), false)
      assert.equal(await dialog.getByRole('button', { name: '保存商品', exact: true }).isEnabled(), false)
      assert.equal(writeCalls().length, 0)
      assert.equal(catalogCalls().length, 0)
    })

    await t.test('a closed and reopened form ignores the old catalog even for the same provider and project', async (t) => {
      const gate = deferred(), arrived = deferred()
      let reads = 0
      const { page, catalogCalls, writeCalls } = await fixture(t, { providers: [provider], path: publishPath(),
        catalog: async () => {
          if (++reads === 1) { arrived.resolve(); await gate.promise; return [{ ...remote, name: '已过期目录' }] }
          return [{ ...remote, name: '新核对目录' }]
        } })
      const dialog = productDialog(page)
      await dialog.getByRole('button', { name: '读取目录', exact: true }).click()
      await arrivedWithin(arrived.promise)
      await dialog.getByRole('button', { name: '取消', exact: true }).click()
      await dialog.waitFor({ state: 'hidden' })
      await page.getByRole('button', { name: '上架服务商品', exact: true }).click()
      await dialog.getByRole('combobox', { name: '已保存的服务接口', exact: true }).click()
      await page.getByRole('option', { name: `${provider.name} · 极光`, exact: true }).click()
      await dialog.getByRole('button', { name: '读取目录', exact: true }).click()
      await dialog.getByText('先读取服务目录', { exact: true }).click()
      await page.getByRole('option', { name: '新核对目录 · 成本 ¥0.10', exact: true }).waitFor()
      gate.resolve()
      await page.waitForTimeout(100)
      assert.equal(await page.getByRole('option', { name: '已过期目录 · 成本 ¥0.10', exact: true }).count(), 0)
      assert.equal(catalogCalls().length, 2)
      assert.equal(writeCalls().length, 0)
    })

    await t.test('course, project and shared entries preserve their own configuration and management routes', async (t) => {
      const { page, calls, writeCalls } = await fixture(t)
      await page.getByTestId('plugin-P02').getByRole('button', { name: '课程管理', exact: true }).click()
      await page.getByRole('button', { name: '配置课程接口', exact: true }).click()
      let config = page.getByRole('dialog', { name: '添加接口', exact: true })
      await config.getByText('27平台 (Benz)', { exact: true }).waitFor()
      assert.deepEqual(await currentRoute(page), { path: '/admin/api-providers', query: { type: '27' } })
      await config.getByRole('button', { name: '取消', exact: true }).click()
      await page.getByTestId('nav-plugins').click()
      await page.getByTestId('plugin-P07').getByRole('button', { name: '项目管理', exact: true }).click()
      await page.getByRole('button', { name: '配置项目接口', exact: true }).click()
      config = page.getByRole('dialog', { name: '添加接口', exact: true })
      await config.getByText('多项目与账户', { exact: true }).waitFor()
      assert.deepEqual(await currentRoute(page), { path: '/admin/api-providers', query: { type: 'syyv5' } })
      await config.getByRole('button', { name: '取消', exact: true }).click()
      await page.getByTestId('nav-plugins').click()
      await page.getByTestId('plugin-P07').getByRole('button', { name: '项目管理', exact: true }).click()
      await page.getByRole('button', { name: '发布与管理项目', exact: true }).click()
      assert.equal((await currentRoute(page)).path, '/admin/service-projects')
      await page.getByTestId('nav-plugins').click()
      await page.getByTestId('plugin-P11').getByRole('button', { name: '前往鲸鱼服务', exact: true }).click()
      await page.getByRole('dialog', { name: '鲸鱼运动服务 · 使用与配置', exact: true }).waitFor()
      assert.equal(calls.filter(c => /\/(P02|P07|P11)\/providers/.test(c.endpoint)).length, 0)
      await page.getByRole('button', { name: '管理服务订单', exact: true }).click()
      await page.getByRole('heading', { name: '服务订单与对账', exact: true }).waitFor()
      assert.deepEqual(await currentRoute(page), { path: '/admin/service-orders', query: { providerType: 'jingyu' } })
      assert.equal(calls.filter(c => c.endpoint === '/admin/service-orders').at(-1).params.providerType, 'jingyu')
      assert.equal(writeCalls().length, 0)
    })
    console.log(`Offline workflow screenshots: ${screenshots}`)
  } finally { await browser?.close(); await server.close() }
})
