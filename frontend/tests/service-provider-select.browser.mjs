import assert from 'node:assert/strict'
import test from 'node:test'
import { existsSync } from 'node:fs'
import { chromium } from 'playwright'
import { createTestServer } from './fixtures/test-server.mjs'
import { serviceNames } from '../src/utils/serviceCommerce.js'

const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="app"></div><script type="module">
import {createApp} from 'vue';import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';import '/src/styles/variables.scss';import '/src/styles/element-overrides.scss';import '/src/styles/global.css';import '/src/styles/fluent-spatial.scss';import '/src/styles/responsive.scss';
import Admin from '/src/views/AdminServiceProducts.vue';import {applyAuthSession} from '/src/utils/authSession.js';
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7});
createApp(Admin).use(ElementPlus).mount('#app');
</script></body></html>`
const server = await createTestServer({ logLevel: 'error', plugins: [{ name: 'service-provider-select-fixture', configureServer(vite) {
  vite.middlewares.use(async (req, res, next) => {
    if (!req.url?.startsWith('/__service_provider_select')) return next()
    res.setHeader('Content-Type', 'text/html;charset=utf-8')
    res.end(await vite.transformIndexHtml(req.url, html))
  })
} }] })
const provider = { id: 9, name: '晨间运动服务', providerType: 'jiguang', status: 1, verifiedAt: '2026-09-13 10:00:00' }
const emptyPage = { records: [], total: 0, current: 1, size: 50 }
let browser, base

async function fixture(t, rows = [provider], respondProviders) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } })
  const page = await context.newPage()
  const reads = [], errors = [], unexpected = []
  page.setDefaultTimeout(15000)
  page.setDefaultNavigationTimeout(60000)
  page.on('pageerror', (error) => errors.push(error.message))
  await context.route('**/*', async (route) => {
    const request = route.request(), url = new URL(request.url())
    if (url.origin !== base) { unexpected.push(request.url()); return route.abort() }
    if (!url.pathname.startsWith('/api/')) return route.continue()
    const respond = (data) => route.fulfill({ json: { code: 1, data } })
    if (request.method() !== 'GET') { unexpected.push(`${request.method()} ${url.pathname}`); return route.abort() }
    if (url.pathname === '/api/admin/service-products') return respond(emptyPage)
    if (url.pathname === '/api/admin/api-providers') {
      const query = Object.fromEntries(url.searchParams)
      reads.push(query)
      if (respondProviders) {
        const response = await respondProviders(query, reads.length)
        if (response) return route.fulfill(response)
      }
      const types = query.providerTypes?.split(',')
      const keyword = query.keyword || ''
      const matching = rows.filter((row) => (!types || types.includes(row.providerType))
        && (row.name.includes(keyword) || row.providerType.includes(keyword)))
      const current = Number(query.page || 1), size = Number(query.pageSize || 10)
      return respond({ records: matching.slice((current - 1) * size, current * size), total: matching.length, current, size })
    }
    unexpected.push(`${request.method()} ${url.pathname}`)
    return route.abort()
  })
  t.after(async () => {
    await context.close()
    assert.deepEqual(unexpected, [], 'only mocked read requests are allowed')
    assert.deepEqual(errors, [], 'no browser runtime errors')
  })
  await page.goto(`${base}/__service_provider_select`)
  await page.getByRole('button', { name: '上架服务商品', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '上架服务商品', exact: true })
  const input = dialog.getByRole('combobox', { name: '已保存的服务接口', exact: true })
  const loaded = () => page.waitForFunction(() => !document.querySelector('.provider-row .el-button.is-loading'))
  await loaded()
  await input.click()
  return { page, dialog, input, reads, loaded, popup: page.locator('.el-select__popper.service-provider-select-popper:visible') }
}

await test('service interface dropdown data and empty states', async (t) => {
  try {
    await server.listen()
    base = `http://127.0.0.1:${server.httpServer.address().port}`
    browser = await chromium.launch({ headless: true,
      executablePath: process.env.BROWSER_PATH || (existsSync('/snap/bin/chromium') ? '/snap/bin/chromium' : undefined),
      args: ['--no-sandbox', '--disable-dev-shm-usage'] })

    await t.test('service types are filtered before pagination, not after an unrelated first page', async (t) => {
      const rows = [...Array.from({ length: 60 }, (_, index) => ({ ...provider, id: 100 + index, name: `课程配置-${index}`, providerType: 'Daytime' })), provider]
      const { page, dialog, reads } = await fixture(t, rows)
      await page.getByRole('option', { name: '晨间运动服务 · 极光', exact: true }).waitFor({ timeout: 4000 })
      assert.equal(reads[0].status, '1')
      assert.deepEqual(reads[0].providerTypes.split(',').sort(), Object.keys(serviceNames).sort())
      assert.equal(await dialog.getByRole('button', { name: '加载更多接口', exact: true }).count(), 0, 'the total counts only relevant interfaces')
      await page.getByRole('option', { name: '晨间运动服务 · 极光', exact: true }).click()
      assert.equal(await dialog.getByRole('button', { name: '读取目录', exact: true }).isEnabled(), true)
    })

    await t.test('an empty list still opens the dropdown and explains how to make interfaces available', async (t) => {
      const { popup } = await fixture(t, [])
      await popup.getByText('暂无已启用的服务接口，请先完成验证并启用', { exact: true }).waitFor({ timeout: 4000 })
      assert.equal(await popup.getByRole('option').count(), 0)
    })

    await t.test('search, clearing and reopening restore selectable interfaces', async (t) => {
      const { page, dialog, input, popup, loaded } = await fixture(t, [provider,
        { ...provider, id: 10, name: '晚间运动服务', providerType: 'jingyu' }])
      const search = async (keyword) => {
        const response = page.waitForResponse((res) => {
          const url = new URL(res.url())
          return url.pathname === '/api/admin/api-providers' && (url.searchParams.get('keyword') || '') === keyword
        })
        await input.fill(keyword)
        await response
        await loaded()
      }
      await search('没有这个服务')
      await popup.getByText('未找到匹配的服务接口', { exact: true }).waitFor()
      assert.equal(await popup.getByRole('option').count(), 0)
      await search('晚间')
      await page.getByRole('option', { name: '晚间运动服务 · 鲸鱼', exact: true }).waitFor()
      assert.equal(await popup.getByRole('option').count(), 1)
      await search('')
      assert.equal(await popup.getByRole('option').count(), 2)
      await page.getByRole('option', { name: '晨间运动服务 · 极光', exact: true }).click()
      const label = dialog.locator('.service-provider-select .el-select__placeholder')
      assert.equal(await label.textContent(), '晨间运动服务 · 极光')
      await dialog.getByRole('button', { name: '刷新接口', exact: true }).click()
      await loaded()
      assert.equal(await label.textContent(), '晨间运动服务 · 极光', 'refresh preserves the current selection')
      await dialog.getByRole('button', { name: '取消', exact: true }).click()
      await dialog.waitFor({ state: 'hidden' })
      await page.getByRole('button', { name: '上架服务商品', exact: true }).click()
      await loaded()
      await input.click()
      await page.getByRole('option', { name: '晨间运动服务 · 极光', exact: true }).waitFor()
      assert.equal(await popup.getByRole('option').count(), 2)
    })

    await t.test('read failures explain the problem and refresh retries without leaving a blank list', async (t) => {
      let fail = true
      const { page, dialog, input, popup, loaded } = await fixture(t, [provider], () =>
        fail ? { status: 503, json: { code: -1, message: '服务暂不可用' } } : null)
      const message = '服务接口加载失败，请点击“刷新接口”重试'
      await popup.getByText(message, { exact: true }).waitFor()
      await dialog.locator('.el-form-item__error').getByText(message, { exact: true }).waitFor()
      fail = false
      await dialog.getByRole('button', { name: '刷新接口', exact: true }).click()
      await loaded()
      await input.click()
      await page.getByRole('option', { name: '晨间运动服务 · 极光', exact: true }).waitFor()
      await dialog.locator('.el-form-item__error').waitFor({ state: 'detached' })
    })

    await t.test('additional pages load without dropping earlier options or selection restrictions', async (t) => {
      const rows = Array.from({ length: 51 }, (_, index) => ({ ...provider, id: index + 1, name: `运动配置-${index + 1}` }))
      rows[0].verifiedAt = null
      rows[1].status = 0
      const { page, dialog, input, popup, loaded, reads } = await fixture(t, rows)
      await page.getByRole('option', { name: '运动配置-3 · 极光', exact: true }).waitFor()
      assert.equal(await popup.getByRole('option').count(), 50)
      assert.equal(await page.getByRole('option', { name: '运动配置-1 · 极光', exact: true }).isEnabled(), false)
      assert.equal(await page.getByRole('option', { name: '运动配置-2 · 极光', exact: true }).isEnabled(), false)
      const more = dialog.getByRole('button', { name: '加载更多接口', exact: true })
      await input.press('Escape')
      await more.click()
      await loaded()
      assert.equal(reads.at(-1).page, '2')
      assert.deepEqual(reads.at(-1).providerTypes, reads[0].providerTypes)
      assert.equal(await more.count(), 0)
      await input.click()
      await popup.waitFor()
      assert.equal(await popup.getByRole('option').count(), 51)
      await page.getByRole('option', { name: '运动配置-51 · 极光', exact: true }).click()
      assert.equal(await dialog.getByRole('button', { name: '读取目录', exact: true }).isEnabled(), true)
    })
  } finally {
    await browser?.close()
    await server.close()
  }
})
