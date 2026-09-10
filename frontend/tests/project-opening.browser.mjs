import assert from 'node:assert/strict'
import { existsSync, mkdirSync } from 'node:fs'
import { chromium } from 'playwright'
import { createTestServer } from './fixtures/test-server.mjs'
import { estimateProjectTransfer } from '../src/utils/projectCenter.js'

// Actual project view, closed mock API allowlist; never contacts suppliers or moves real money.
const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;padding:20px"><div id="app"></div><script type="module">
import {createApp,h} from 'vue';import {createRouter,createMemoryHistory,RouterView,RouterLink} from 'vue-router';import ElementPlus from 'element-plus';import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';
import ProjectCenter from '/src/views/ProjectCenter.vue';import Admin from '/src/views/AdminProjectCenter.vue';import {applyAuthSession} from '/src/utils/authSession.js';
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7});
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:ProjectCenter},{path:'/admin',component:Admin},{path:'/away',component:{render:()=>h('h2','已离开项目页')}}]});await router.push(new URLSearchParams(location.search).get('entry')==='admin'?'/admin':'/');await router.isReady();createApp({render:()=>h('main',[h(RouterLink,{to:'/away'},()=> '离开页面'),h(RouterView)])}).use(router).use(ElementPlus).mount('#app');</script></body></html>`
const server = await createTestServer({ logLevel: 'error', plugins: [{ name: 'funded-opening-fixture', configureServer(vite) {
  vite.middlewares.use(async (req, res, next) => {
    if (!req.url?.startsWith('/__funded_opening')) return next()
    res.setHeader('Content-Type', 'text/html;charset=utf-8'); res.end(await vite.transformIndexHtml(req.url, html))
  })
} }] })
const project = { id: 1, title: '项目甲 · 独立服务额度', description: '选择初始额度，核对费用后开通', remoteProjectId: '5', unitPrice: '0.25', validUntil: '2099-12-31', enabled: true, available: true, version: 0, account: null }
const accountId = '19c226ff-a3da-4e82-812e-000000000001'
const operations = new Map(), writes = [], reads = [], unexpected = [], errors = []
const consent = '我已了解项目账户、费率和余额兑换规则，确认使用本人账户'
let browser, mode = '', sequence = 0, creations = 0, debits = 0, resolutions = 0, releases = [], held, reached, unresolved
const output = new URL('../../.cache/native-service-ui/', import.meta.url).pathname
const pageData = (records) => ({ records, total: records.length, current: 1, size: 20 })
const deferred = () => { let resolve; const promise = new Promise((r) => { resolve = r }); return { promise, resolve } }
try {
  await server.listen(); const base = server.resolvedUrls.local[0].replace(/\/$/, '')
  browser = await chromium.launch({ executablePath: existsSync('/snap/bin/chromium') ? '/snap/bin/chromium' : undefined, args: ['--no-sandbox', '--disable-dev-shm-usage'] })
  const context = await browser.newContext({ viewport: { width: 1440, height: 1050 } })
  await context.route('**/*', async (route) => {
    const request = route.request(), url = new URL(request.url())
    if (url.origin !== base) { unexpected.push(url.origin); return route.abort() }
    if (!url.pathname.startsWith('/api/')) return route.continue()
    const path = url.pathname.slice(4), method = request.method()
    const ok = async (data) => { try { await route.fulfill({ json: { code: 1, data }, headers: { 'Cache-Control': 'no-store' } }) } catch {} }
    assert.match(request.headers().authorization || '', /^Bearer /)
    if (method === 'GET') {
      reads.push(path)
      if (path === '/service-projects') return ok(pageData([project]))
      if (path === '/project-tickets') return ok(pageData([]))
      if (path === '/admin/service-projects') return ok(pageData([]))
      if (path === '/admin/project-operations') return ok(pageData([...operations.values()]))
      if (/^\/admin\/project-operations\/[0-9a-f-]+$/.test(path)) return ok(operations.get(path.split('/')[3]))
      if (path === '/project-operations') return ok(pageData([...operations.values()].reverse()))
      if (/^\/project-operations\/[0-9a-f-]+$/.test(path)) {
        const value = operations.get(path.split('/')[2])
        return ok(mode === 'partial-result' ? { ...value, amount: undefined } : value)
      }
    }
    if (method === 'POST' && path === '/service-projects/1/quotes') {
      const form = request.postDataJSON(); writes.push({ path, form })
      assert.equal(form.action, 'PROVISION'); assert.equal(form.confirmedPolicy, true)
      const units = form.units ?? '0', amount = form.units === null ? '0.00' : estimateProjectTransfer(units, project.unitPrice)
      assert.notEqual(amount, '—')
      const id = `19c226ff-a3da-4e82-812e-${String(++sequence + 1).padStart(12, '0')}`
      const value = { id, accountId, projectId: 1, projectTitle: project.title, action: 'PROVISION', units, unitPrice: project.unitPrice, amount, balanceAfter: null,
        state: 'READY', expiresAt: '2099-01-01T10:00:00', createdAt: '2026-09-10T10:00:00', warnings: ['确认后只开通一次；响应不确定时保留预扣并检查原操作，不自动退款。'] }
      operations.set(id, value)
      if (mode === 'held-quote') { reached.resolve(); await held.promise }
      if (mode === 'partial-quote') return ok({ ...value, unitPrice: undefined })
      if (mode === 'wrong-quantity') return ok({ ...value, units: '16', amount: '4.00' })
      return ok(value)
    }
    if (method === 'POST' && /^\/project-operations\/[0-9a-f-]+\/confirm$/.test(path)) {
      writes.push({ path }); const value = operations.get(path.split('/')[2]); assert.equal(value.state, 'READY')
      creations++; if (value.amount !== '0.00') debits++
      value.state = mode === 'unknown' ? 'UNKNOWN' : 'SUCCEEDED'
      value.balanceAfter = value.state === 'SUCCEEDED' ? value.units : null
      project.account = { id: accountId, projectId: 1, state: value.state === 'SUCCEEDED' ? 'ACTIVE' : 'UNKNOWN', unitPrice: project.unitPrice,
        remoteBalance: value.balanceAfter, refundableUnits: value.units, refundBudget: value.amount,
        pendingOperationId: value.state === 'UNKNOWN' ? value.id : null, ticketsAvailable: false }
      if (mode === 'lose-confirm') return route.abort()
      if (mode === 'wrong-confirm') return ok({ ...value, units: '12', amount: '3.00' })
      return ok(value)
    }
    if (method === 'POST' && /^\/admin\/project-operations\/[0-9a-f-]+\/resolve$/.test(path)) {
      const form = request.postDataJSON(); writes.push({ path, form }); resolutions++
      assert.equal(form.outcome, 'NOT_ACCEPTED'); assert.equal(form.customerId, null); assert.equal(form.upstreamChecked, true)
      const value = operations.get(path.split('/')[3]); value.state = 'NOT_ACCEPTED'; value.balanceAfter = null
      if (mode === 'lose-admin') return route.abort()
      return ok(value)
    }
    unexpected.push(method + ' ' + path); return route.abort()
  })
  const page = await context.newPage(); page.setDefaultTimeout(45000); page.setDefaultNavigationTimeout(90000)
  page.on('pageerror', (e) => errors.push(e.message))
  const load = async () => { await page.goto(base + '/__funded_opening'); await page.getByRole('button', { name: '开通项目账户', exact: true }).waitFor() }
  const drawer = page.getByRole('dialog', { name: '开通项目账户', exact: true })
  const dialog = page.getByRole('dialog', { name: '确认项目操作', exact: true })
  const begin = async () => { await page.getByRole('button', { name: '开通项目账户', exact: true }).click(); await drawer.waitFor() }
  const funded = async (amount = '8') => {
    await drawer.getByText('开通并充值', { exact: true }).click()
    await drawer.getByRole('textbox', { name: '初始项目额度', exact: true }).fill(amount)
    assert.equal(await drawer.getByRole('button', { name: '预览本次操作', exact: true }).isDisabled(), true)
    await drawer.getByText(consent, { exact: true }).click()
  }
  const preview = async () => { await drawer.getByRole('button', { name: '预览本次操作', exact: true }).click(); await dialog.waitFor(); await drawer.waitFor({ state: 'hidden' }) }
  const screenshot = async (name) => { mkdirSync(output, { recursive: true }); await page.screenshot({ path: output + name + '.png', fullPage: true, animations: 'disabled' }) }

  await load(); await begin(); assert.equal(writes.length, 0)
  assert.equal(await drawer.getByRole('radio', { name: '零余额开通', exact: true }).isChecked(), true)
  assert.equal(await drawer.getByRole('textbox', { name: '初始项目额度' }).count(), 0)
  await drawer.getByText(consent, { exact: true }).click(); await preview()
  assert.equal(writes.at(-1).form.units, null); assert.equal(creations, 0); assert.equal(debits, 0)
  await dialog.getByText('零余额开户，不扣款', { exact: true }).waitFor()
  await dialog.getByRole('button', { name: '暂不执行', exact: true }).click(); await begin()
  await funded('0.05')
  await drawer.getByRole('textbox', { name: '初始项目额度', exact: true }).fill('8')
  assert.equal(await drawer.getByRole('checkbox').isChecked(), false)
  await drawer.getByText(consent, { exact: true }).click(); await preview()
  assert.equal(writes.at(-1).form.units, '8'); assert.equal(creations, 0); assert.equal(debits, 0)
  await dialog.getByText('¥2.00', { exact: true }).waitFor()
  await dialog.getByText('开户并充值，将扣除账户余额', { exact: true }).waitFor()
  await screenshot('project-opening-desktop')
  mode = 'lose-confirm'; const id = [...operations.keys()].at(-1)
  await dialog.getByRole('button', { name: '确认开户并充值', exact: true }).click()
  await dialog.getByText('请求结果尚未确认，请检查同一操作，不要重新提交或自动退款。', { exact: true }).waitFor()
  assert.equal(creations, 1); assert.equal(debits, 1)
  mode = 'partial-result'; await dialog.getByRole('button', { name: '检查提交结果', exact: true }).click()
  await dialog.getByText('原操作结果尚未确认或数据不完整；没有重复开户、充值或退款。', { exact: true }).waitFor()
  assert.equal(await dialog.getByText('已完成', { exact: true }).count(), 0)
  mode = ''; await dialog.getByRole('button', { name: '检查提交结果', exact: true }).click()
  await dialog.getByText('已完成', { exact: true }).waitFor()
  assert.ok(reads.includes('/project-operations/' + id)); assert.equal(creations, 1); assert.equal(debits, 1)

  for (const bad of ['partial-quote', 'wrong-quantity']) {
    project.account = null; operations.clear(); mode = bad; await load(); await begin(); await funded()
    await drawer.getByRole('button', { name: '预览本次操作', exact: true }).click()
    await drawer.getByText('预览未完成或费用信息不完整，未开户、未扣款。请重新核对。', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: '确认开户并充值', exact: true }).count(), 0)
    assert.equal(creations, 1)
  }
  project.account = null; operations.clear(); mode = ''; await load(); await begin(); await funded()
  await drawer.getByRole('textbox', { name: '初始项目额度' }).fill('100001')
  await drawer.getByText(consent, { exact: true }).click()
  assert.equal(await drawer.getByRole('button', { name: '预览本次操作', exact: true }).isDisabled(), true)
  await drawer.getByRole('textbox', { name: '初始项目额度' }).fill('8'); await drawer.getByText(consent, { exact: true }).click(); await preview()
  mode = 'wrong-confirm'; await dialog.getByRole('button', { name: '确认开户并充值', exact: true }).click()
  await dialog.getByText('提交结果待确认', { exact: true }).waitFor(); assert.equal(creations, 2)
  mode = ''; await dialog.getByRole('button', { name: '检查提交结果', exact: true }).click()
  await dialog.getByText('已完成', { exact: true }).waitFor(); assert.equal(creations, 2)

  project.account = null; operations.clear(); mode = ''; await load(); await page.setViewportSize({ width: 390, height: 844 })
  await page.evaluate(() => { document.documentElement.classList.add('dark') }); await begin(); await funded('0.05')
  assert.ok((await drawer.getByRole('button', { name: '预览本次操作', exact: true }).boundingBox()).height >= 44)
  await screenshot('project-opening-mobile-form'); await preview()
  await dialog.getByText('¥0.02', { exact: true }).waitFor(); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false)
  assert.ok((await dialog.getByRole('button', { name: '确认开户并充值', exact: true }).boundingBox()).height >= 44)
  await screenshot('project-opening-mobile-dark')
  mode = 'unknown'; await dialog.getByRole('button', { name: '确认开户并充值', exact: true }).click()
  await dialog.getByText('待人工核对', { exact: true }).waitFor()
  await dialog.getByRole('button', { name: '检查提交结果', exact: true }).click()
  assert.equal(creations, 3); assert.equal(debits, 3)
  unresolved = structuredClone([...operations.values()].at(-1))
  await dialog.getByRole('button', { name: '关闭', exact: true }).click()
  assert.equal(await page.getByRole('button', { name: '开通项目账户', exact: true }).count(), 0)

  project.account = null; operations.clear(); mode = 'held-quote'; held = deferred(); reached = deferred(); releases.push(held.resolve)
  await page.setViewportSize({ width: 1440, height: 1050 }); await load(); await begin(); await funded()
  await drawer.getByRole('button', { name: '预览本次操作', exact: true }).click(); await reached.promise
  await page.getByRole('link', { name: '离开页面', exact: true }).evaluate((element) => element.click())
  await page.getByRole('heading', { name: '已离开项目页', exact: true }).waitFor()
  held.resolve(); await page.waitForTimeout(150)
  assert.equal(await page.getByRole('dialog', { name: '确认项目操作', exact: true }).count(), 0)
  assert.equal(creations, 3)
  operations.clear(); operations.set(unresolved.id, unresolved); mode = ''
  await page.goto(base + '/__funded_opening?entry=admin')
  await page.getByRole('tab', { name: '资金操作核对', exact: true }).click()
  await page.getByRole('button', { name: '查看与核对', exact: true }).click()
  const review = page.getByRole('dialog', { name: '核对项目操作', exact: true })
  await review.getByText('必须同时核实客户归属、开户结果与初始充值；不能只凭当前余额认定成功或退款。', { exact: true }).waitFor()
  await review.getByText('返还原充值预扣 ¥0.02；不会再次提交退款。', { exact: true }).waitFor()
  assert.equal(await review.getByRole('button', { name: '确认核对并记账', exact: true }).isDisabled(), true)
  await review.getByText('已受理', { exact: true }).click()
  await review.getByRole('textbox', { name: '已核实的客户编号', exact: true }).fill('11')
  await review.getByText('确认开户及初始充值已受理，记录 0.05 额度和 ¥0.02 的已付费用；不会再次扣款或开户。', { exact: true }).waitFor()
  await review.getByText('完全未受理', { exact: true }).click()
  await review.getByRole('textbox', { name: '核对证据', exact: true }).fill('已核实原始开户回执和充值流水，确认没有受理本次操作')
  await review.getByText('已逐项核实，不凭余额变化推测受理结果', { exact: true }).click()
  mode = 'lose-admin'; await review.getByRole('button', { name: '确认核对并记账', exact: true }).click()
  await review.getByText('已提交核对请求。请查询原操作结果，不要再次提交。', { exact: true }).waitFor()
  mode = ''; await review.getByRole('button', { name: '检查原操作状态', exact: true }).click()
  await review.getByText('项目额度 0.05 · 已确认未受理', { exact: true }).waitFor()
  assert.equal(resolutions, 1); assert.equal(creations, 3); assert.equal(debits, 3)
  const storage = await page.evaluate(() => JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]))
  for (const value of [...operations.keys(), 'private-customer-key', 'initialFunding']) assert.equal(storage.includes(value), false)
  assert.deepEqual(unexpected, []); assert.deepEqual(errors, [])
  console.log('PASS funded project opening: actual Vue entry, free default, optional exact initial credit, renewed consent, preview without money, one provision/debit, lost and incomplete result GET-only recovery, UNKNOWN no retry, explicit admin paid-refund effect and one reconciliation, obsolete response isolation, mobile dark; mock APIs only')
} finally { for (const release of releases) release(); await browser?.close(); await server.close() }
