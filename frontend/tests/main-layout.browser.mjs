import assert from 'node:assert/strict'
import test from 'node:test'
import { existsSync, mkdirSync } from 'node:fs'
import { chromium } from 'playwright'
import { createTestServer } from './fixtures/test-server.mjs'

// Use the real router, cached layout and production styles. All network traffic
// is intercepted; even product saves below only change this fixture's memory.
const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="app"></div><script type="module">
import {createApp,h,nextTick} from 'vue';import {createPinia} from 'pinia';import {RouterView} from 'vue-router';import ElementPlus from 'element-plus';import * as icons from '@element-plus/icons-vue';
import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/element-overrides.scss';import '/src/styles/global.css';import '/src/styles/fluent-spatial.scss';import '/src/styles/responsive.scss';
import router from '/src/router/index.js';import {applyAuthSession} from '/src/utils/authSession.js';
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',uid:'20000000-0000-4000-8000-000000000007',role:'ADMIN',isAdmin:true,nickname:'测试管理员'});
const app=createApp({render:()=>h(RouterView)}).use(createPinia()).use(ElementPlus);for(const [name,icon] of Object.entries(icons))app.component(name,icon);
await router.push(new URLSearchParams(location.search).get('entry')||'/service-orders');app.use(router);await router.isReady();
window.__layoutFixture={go:async(path)=>{await router.push(path);await nextTick()}};app.mount('#app');
</script></body></html>`
const server = await createTestServer({ logLevel: 'error', plugins: [{ name: 'main-layout-fixture', configureServer(vite) {
  vite.middlewares.use(async (req, res, next) => {
    if (!req.url?.startsWith('/__main_layout')) return next()
    res.setHeader('Content-Type', 'text/html;charset=utf-8')
    res.end(await vite.transformIndexHtml(req.url, html))
  })
} }] })
const output = new URL('../../.cache/native-service-ui/main-layout/', import.meta.url).pathname
mkdirSync(output, { recursive: true })
const emptyPage = { records: [], total: 0, current: 1, size: 20 }
const provider = { id: 9, name: '测试服务接口', providerType: 'jiguang', status: 1, verifiedAt: '2026-09-12 18:19:25' }
let browser, base

async function fixture(t, entry, viewport = { width: 1440, height: 900 }, providerRows = [provider]) {
  const context = await browser.newContext({ viewport })
  const reads = [], saves = [], unexpected = [], errors = []
  const page = await context.newPage()
  page.setDefaultTimeout(30000)
  page.setDefaultNavigationTimeout(60000)
  page.on('pageerror', (error) => errors.push(error.message))
  await context.route('**/*', async (route) => {
    const request = route.request(), url = new URL(request.url()), method = request.method()
    if (url.origin !== base) { unexpected.push(request.url()); return route.abort() }
    if (!url.pathname.startsWith('/api/')) return route.continue()
    const endpoint = url.pathname.slice(4)
    const respond = (data) => route.fulfill({ json: { code: 1, success: true, data }, headers: { 'Cache-Control': 'no-store' } })
    if (endpoint === '/admin/service-products' && method === 'POST') {
      saves.push(request.postDataJSON())
      return respond({ id: saves.length, ...saves.at(-1) })
    }
    if (method !== 'GET') { unexpected.push(`${method} ${endpoint}`); return route.abort() }
    reads.push({ endpoint, query: Object.fromEntries(url.searchParams) })
    if (endpoint === '/client/bootstrap') return respond({})
    if (endpoint === '/announcement/system') return respond(null)
    if (endpoint === '/customer-service/unread-count') return respond(0)
    if (['/service-orders', '/admin/service-orders', '/admin/service-products', '/service-projects',
      '/project-operations', '/project-tickets', '/project-clients', '/project-client-operations', '/admin/service-projects'].includes(endpoint)) return respond(emptyPage)
    if (endpoint === '/project-clients/stats') return respond({ customers: 0, active: 0, appliedOperations: 0, totalDebited: '0.00', totalReturned: '0.00' })
    if (endpoint === '/admin/api-providers') {
      const keyword = url.searchParams.get('keyword') || ''
      const records = providerRows.filter((row) => row.name.includes(keyword))
      return respond({ ...emptyPage, total: records.length, records })
    }
    if (endpoint === '/admin/plugin-integrations/P04/providers/9/catalog') return respond([{ id: '1', name: '测试晨间商品', unitPrice: '0.10', priceUnit: '元/公里' }])
    unexpected.push(`${method} ${endpoint}`)
    return route.abort()
  })
  t.after(async () => {
    await context.close()
    assert.deepEqual(unexpected, [], 'no unexpected or live requests')
    assert.deepEqual(errors, [], 'no browser errors')
  })
  await page.goto(`${base}/__main_layout?entry=${encodeURIComponent(entry)}`)
  await page.locator('.main-container').waitFor()
  const go = async (path) => {
    await page.evaluate((target) => window.__layoutFixture.go(target), path)
    await page.waitForFunction(() => !document.querySelector('.main-content .fade-transform-enter-active, .main-content .fade-transform-leave-active'))
  }
  const count = (endpoint) => reads.filter((row) => row.endpoint === endpoint).length
  return { page, go, reads, saves, count }
}

await test('main layout route and dialog regressions', async (t) => {
  try {
    await server.listen()
    base = `http://127.0.0.1:${server.httpServer.address().port}`
    browser = await chromium.launch({ executablePath: process.env.BROWSER_PATH || (existsSync('/snap/bin/chromium') ? '/snap/bin/chromium' : undefined),
      args: ['--no-sandbox', '--disable-dev-shm-usage'] })

    await t.test('only the visible order view loads, including cached user/admin transitions', async (t) => {
      const { page, go, count, reads } = await fixture(t, '/service-orders')
      await page.getByText('找到 0 笔订单', { exact: true }).waitFor()
      await page.waitForLoadState('networkidle')
      assert.equal(count('/service-orders'), 1)
      await go('/admin/service-orders')
      await page.getByRole('heading', { name: '服务订单与对账', exact: true }).waitFor()
      await page.getByText('找到 0 笔订单', { exact: true }).waitFor()
      assert.equal(count('/admin/service-orders'), 1, 'entering admin orders must issue exactly one GET')
      assert.equal(count('/service-orders'), 1)
      await go('/admin/service-products?focus=10000000-0000-0000-0000-000000000001')
      assert.equal(count('/admin/service-orders'), 1, 'cached order views must not load on other pages')
      assert.equal(count('/service-orders'), 1)
      await go('/service-orders')
      await page.getByText('找到 0 笔订单', { exact: true }).waitFor()
      assert.equal(count('/service-orders'), 1, 'returning to cached results does not issue a redundant GET')
      await page.getByRole('button', { name: '刷新列表', exact: true }).click()
      await page.waitForLoadState('networkidle')
      assert.equal(count('/service-orders'), 2, 'manual refresh remains available')
      await go('/admin/service-orders')
      await page.getByText('找到 0 笔订单', { exact: true }).waitFor()
      assert.equal(count('/admin/service-orders'), 1, 'cached admin results remain separate')
      for (const read of reads.filter((row) => row.endpoint.endsWith('/service-orders'))) {
        assert.deepEqual(read.query, { page: '1', pageSize: '20' })
      }
      await page.goto(`${base}/__main_layout?entry=/admin/service-orders`)
      await page.getByText('找到 0 笔订单', { exact: true }).waitFor()
      await page.waitForLoadState('networkidle')
      assert.equal(count('/admin/service-orders'), 2, 'direct admin entry loads once')
    })

    await t.test('cached order filters and drafts survive unrelated menu and focus changes', async (t) => {
      const { page, go, count, reads } = await fixture(t, '/service-orders')
      await page.getByText('找到 0 笔订单', { exact: true }).waitFor()
      await page.getByRole('textbox', { name: '搜索订单', exact: true }).fill('已查询')
      await page.getByRole('button', { name: '查询订单', exact: true }).click()
      await page.getByText('找到 0 笔订单', { exact: true }).waitFor()
      assert.equal(count('/service-orders'), 2)
      await page.getByRole('textbox', { name: '搜索订单', exact: true }).fill('未提交')
      await go('/admin/service-orders')
      await page.getByText('找到 0 笔订单', { exact: true }).waitFor()
      assert.equal(await page.getByRole('textbox', { name: '搜索订单', exact: true }).inputValue(), '')
      await go('/admin/service-products?focus=10000000-0000-0000-0000-000000000001')
      await go('/service-orders')
      assert.equal(await page.getByRole('textbox', { name: '搜索订单', exact: true }).inputValue(), '未提交')
      await page.getByText('搜索：已查询', { exact: true }).waitFor()
      assert.equal(count('/service-orders'), 2, 'hidden and cached views issue no new queries')
      assert.equal(count('/admin/service-orders'), 1)
      await page.getByRole('button', { name: '刷新列表', exact: true }).click()
      await page.getByText('找到 0 笔订单', { exact: true }).waitFor()
      assert.deepEqual(reads.filter((row) => row.endpoint === '/service-orders').at(-1).query,
        { keyword: '已查询', page: '1', pageSize: '20' })
    })

    await t.test('breadcrumbs use localized route titles and the actual menu groups', async (t) => {
      const { page, go } = await fixture(t, '/service-projects')
      for (const [path, expected] of [
        ['/service-projects', ['首页', '服务中心', '项目中心']],
        ['/project-clients', ['首页', '服务中心', '客户与 API']],
        ['/admin/service-projects', ['首页', '服务管理', '项目与子钱包']],
        ['/admin/service-products', ['首页', '服务管理', '服务商品']],
        ['/admin/service-orders', ['首页', '服务管理', '服务订单与对账']],
      ]) {
        await go(path)
        const crumbs = page.locator('.breadcrumb .el-breadcrumb__inner')
        assert.deepEqual((await crumbs.allTextContents()).map((text) => text.trim()), expected, path)
        assert.equal(await crumbs.nth(1).evaluate((el) => el.classList.contains('is-link')), false, 'menu groups are not nonexistent URLs')
        assert.equal(await crumbs.last().evaluate((el) => el.classList.contains('is-link')), false, 'current page is not a navigation link')
      }
      await page.screenshot({ path: `${output}service-admin-breadcrumb.png`, animations: 'disabled' })
    })

    await t.test('service interface dropdown keeps long names readable and stays inside the viewport', async (t) => {
      const longProvider = { ...provider, id: 10, providerType: 'jingyu',
        name: '晨间校园运动服务配置（适用于晨间、午间、晚间跑步，支持多校区和不同学校规则的长名称测试）' }
      const label = `${longProvider.name} · 鲸鱼`
      const providers = [provider, longProvider,
        { ...provider, id: 11, name: '尚未验证的服务配置', verifiedAt: null },
        { ...provider, id: 12, name: '已经停用的服务配置', status: 0 },
        ...Array.from({ length: 10 }, (_, i) => ({ ...provider, id: 20 + i, name: `其他运动配置-${i + 1}` })),
        { ...provider, id: 99, name: '不支持的配置', providerType: 'unsupported' },
      ]
      const { page } = await fixture(t, '/admin/service-products', undefined, providers)
      for (const [name, viewport] of [
        ['desktop', { width: 1440, height: 900 }],
        ['mobile', { width: 390, height: 844 }],
        ['narrow', { width: 320, height: 568 }],
        ['landscape', { width: 844, height: 390 }],
      ]) {
        await page.setViewportSize(viewport)
        await page.getByRole('button', { name: '上架服务商品', exact: true }).click()
        const dialog = page.getByRole('dialog', { name: '上架服务商品', exact: true })
        await page.waitForFunction(() => !document.querySelector('.dialog-fade-enter-active'))
        const input = dialog.getByRole('combobox').first()
        await input.click()
        const option = page.getByRole('option', { name: label, exact: true })
        await option.waitFor()
        await page.waitForFunction(() => !document.querySelector('.el-zoom-in-top-enter-active, .el-zoom-in-bottom-enter-active'))
        const popup = page.locator('.el-select__popper:visible')
        await page.screenshot({ path: `${output}service-provider-${name}.png`, animations: 'disabled' })
        const trigger = await dialog.locator('.provider-row .el-select').first().boundingBox()
        const bounds = await popup.boundingBox()
        assert.ok(Math.abs(bounds.width - trigger.width) <= 1, `${name}: dropdown follows the input width ${JSON.stringify({ trigger, bounds })}`)
        assert.ok(Math.abs(bounds.x - trigger.x) <= 1, `${name}: dropdown stays aligned with the input`)
        assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= viewport.width, `${name}: dropdown fits horizontally`)
        assert.ok(bounds.y >= 0 && bounds.y + bounds.height <= viewport.height, `${name}: dropdown fits vertically ${JSON.stringify(bounds)}`)
        assert.equal(await option.evaluate((el) => el.scrollWidth <= el.clientWidth), true, `${name}: long option text is not clipped`)
        assert.equal(await input.evaluate((el) => getComputedStyle(el).outlineStyle), 'none', `${name}: the wrapper, not the inner search input, draws focus`)
        assert.equal(await dialog.locator('.service-provider-select .el-select__wrapper').evaluate((el) =>
          el.classList.contains('is-focused') && getComputedStyle(el).boxShadow !== 'none'), true, `${name}: the select keeps a visible focus indicator`)
        assert.equal(await page.getByRole('option', { name: '尚未验证的服务配置 · 极光', exact: true }).isEnabled(), false)
        assert.equal(await page.getByRole('option', { name: '已经停用的服务配置 · 极光', exact: true }).isEnabled(), false)
        assert.equal(await popup.getByText('不支持的配置', { exact: false }).count(), 0)
        const scroll = popup.locator('.el-select-dropdown__wrap')
        assert.equal(await scroll.evaluate((el) => el.scrollHeight > el.clientHeight), true, `${name}: long lists remain scrollable`)
        await scroll.evaluate((el) => { el.scrollTop = el.scrollHeight })
        assert.ok(await scroll.evaluate((el) => el.scrollTop) > 0)

        const search = async (keyword) => {
          const response = page.waitForResponse((res) => {
            const url = new URL(res.url())
            return url.pathname === '/api/admin/api-providers' && (url.searchParams.get('keyword') || '') === keyword
          })
          await input.fill(keyword)
          await response
        }
        await search('没有这个服务')
        await popup.locator('.el-select-dropdown__empty').waitFor()
        assert.equal(await popup.getByRole('option').count(), 0)
        await search('晨间')
        await option.waitFor()
        assert.equal(await popup.getByRole('option').count(), 1)
        await option.click()
        await dialog.getByRole('combobox', { name: '运动项目', exact: true }).waitFor()
        const selected = dialog.locator('.provider-row .el-select__placeholder').first()
        assert.equal(await selected.textContent(), label)
        const refreshed = page.waitForResponse((res) => new URL(res.url()).pathname === '/api/admin/api-providers')
        await dialog.getByRole('button', { name: '刷新接口', exact: true }).click()
        await refreshed
        await page.waitForFunction(() => !document.querySelector('.provider-row .el-button.is-loading'))
        assert.equal(await selected.textContent(), label, `${name}: refresh preserves the selection`)
        await dialog.getByRole('button', { name: '取消', exact: true }).click()
        await dialog.waitFor({ state: 'hidden' })
      }
    })

    await t.test('product forms scroll and save in the real animated shell at desktop and mobile sizes', async (t) => {
      const { page, saves } = await fixture(t, '/admin/service-products')
      for (const [name, viewport] of [
        ['desktop', { width: 1440, height: 900 }],
        ['compact', { width: 1280, height: 720 }],
        ['mobile', { width: 390, height: 844 }],
        ['landscape', { width: 844, height: 390 }],
      ]) {
        await page.setViewportSize(viewport)
        await page.getByRole('button', { name: '上架服务商品', exact: true }).click()
        const dialog = page.getByRole('dialog', { name: '上架服务商品', exact: true })
        await dialog.waitFor()
        await page.waitForFunction(() => !document.querySelector('.dialog-fade-enter-active, .dialog-fade-leave-active'))
        assert.equal(await dialog.evaluate((el) => !!el.closest('.main-content')), false, 'dialog must escape the transformed page container')
        const bounds = await dialog.locator('.service-product-dialog').boundingBox()
        assert.ok(bounds.y >= 0 && bounds.y + bounds.height <= viewport.height + 1, `${name}: dialog fits viewport ${JSON.stringify(bounds)}`)
        const overlay = await dialog.boundingBox()
        assert.ok(overlay.height >= viewport.height - 1, `${name}: overlay covers viewport`)
        await dialog.getByRole('combobox').first().click()
        await page.getByRole('option', { name: '测试服务接口 · 极光', exact: true }).click()
        await dialog.getByRole('button', { name: '读取目录', exact: true }).click()
        await dialog.getByText('先读取服务目录', { exact: true }).click()
        await page.getByRole('option', { name: '测试晨间商品 · 成本 ¥0.10', exact: true }).click()
        await dialog.getByLabel('商品名称', { exact: true }).fill(`测试商品-${name}`)
        await dialog.getByLabel('商品说明', { exact: true }).fill('完整表单回归验证')
        await dialog.getByPlaceholder('例如 0.25；不能低于成本价').fill('0.25')
        const body = dialog.locator('.el-dialog__body')
        const scrollable = await body.evaluate((el) => el.scrollHeight > el.clientHeight)
        if (viewport.height <= 720 || viewport.width < 600) assert.equal(scrollable, true, `${name}: long form has a scroll container`)
        if (scrollable) {
          await body.evaluate((el) => { el.scrollTop = 0 })
          await body.hover()
          await page.mouse.wheel(0, 10000)
          await page.waitForFunction(() => document.querySelector('.service-product-dialog .el-dialog__body').scrollTop > 0)
        }
        await dialog.locator('.el-switch__core').click()
        const save = dialog.getByRole('button', { name: '保存商品', exact: true })
        const saveBounds = await save.boundingBox()
        assert.ok(saveBounds.y >= 0 && saveBounds.y + saveBounds.height <= viewport.height, `${name}: save button remains visible`)
        assert.equal(await save.isEnabled(), true)
        await page.screenshot({ path: `${output}service-product-${name}.png`, animations: 'disabled' })
        const before = saves.length
        await save.click()
        await dialog.waitFor({ state: 'hidden' })
        assert.equal(saves.length, before + 1, `${name}: save can be clicked`)
        assert.equal(saves.at(-1).title, `测试商品-${name}`)
        assert.equal(saves.at(-1).description, '完整表单回归验证')
        assert.equal(saves.at(-1).unitPrice, '0.25')
        assert.equal(saves.at(-1).enabled, true)
      }
    })
  } finally {
    await browser?.close()
    await server.close()
  }
})
