import assert from 'node:assert/strict'
import { existsSync, mkdirSync } from 'node:fs'
import { chromium } from 'playwright'
import { createTestServer } from './fixtures/test-server.mjs'
const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;padding:16px"><div id="app"></div><script type="module">
import {createApp,ref,h} from 'vue';import {createPinia} from 'pinia';import ElementPlus from 'element-plus';import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';
import Admin from '/src/views/AdminOrders.vue';import Recovery from '/src/components/orderreceipt/OrderReceiptRecovery.vue';import {applyAuthSession} from '/src/utils/authSession.js';
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7,isAdmin:true});
const component=new URLSearchParams(location.search).get('entry')==='admin'?Admin:{setup(){const order=ref(1),shown=ref(true);return()=>h('main',[h('nav',{style:'display:flex;gap:12px;margin-bottom:18px'},[h('button',{onClick:()=>order.value=2},'切换订单二'),h('button',{onClick:()=>shown.value=false},'关闭核对')]),shown.value?h(Recovery,{orderId:order.value}):null])}};
createApp(component).use(createPinia()).use(ElementPlus).mount('#app');</script></body></html>`
const server = await createTestServer({ logLevel: 'error', plugins: [{ name: 'order-receipts-fixture', configureServer(vite) {
  vite.middlewares.use(async (req, res, next) => {
    if (!req.url?.startsWith('/__order_receipts')) return next()
    res.setHeader('Content-Type', 'text/html;charset=utf-8'); res.end(await vite.transformIndexHtml(req.url, html))
  })
} }] })
const note = '已核实原始回执和原执行账户，确认完整订单归属'
const receipt = 'receipt-9'
const order = { id: 1, orderNo: 'ORD-RECEIPT-1', platformName: '示例课程服务', studentAccount: 'student', courseName: '示例课程 <img src=x>', amount: '2.50', progress: '20%', orderStatus: 4, dockStatus: 2, retryCount: 0, isSelfOperated: 0, createTime: '2026-09-10T10:00:00' }
const records = new Map(), reads = [], candidateReads = [], writes = [], unexpected = [], errors = []
let browser, mode = '', historyMode = '', held = false, entered, release
let candidateMode = '', candidateHeld = false, candidateEntered, candidateRelease, candidateFinished
const deferred = () => { let resolve; const promise = new Promise((r) => { resolve = r }); return { promise, resolve } }
const basePath = '/admin/orders/1/receipt-recoveries'
const output = new URL('../../.cache/native-service-ui/', import.meta.url).pathname
try {
  await server.listen(); const base = server.resolvedUrls.local[0].replace(/\/$/, '')
  browser = await chromium.launch({ executablePath: existsSync('/snap/bin/chromium') ? '/snap/bin/chromium' : undefined, args: ['--no-sandbox', '--disable-dev-shm-usage'] })
  const context = await browser.newContext({ viewport: { width: 1440, height: 1100 } })
  await context.route('**/*', async (route) => {
    const req = route.request(), url = new URL(req.url())
    if (url.origin !== base) { unexpected.push(url.origin); return route.abort() }
    if (!url.pathname.startsWith('/api/')) return route.continue()
    const path = url.pathname.slice(4), method = req.method()
    const ok = async (data) => { try { await route.fulfill({ json: { code: 1, data }, headers: { 'Cache-Control': 'no-store' } }) } catch {} }
    // Actual AdminOrders initialization is explicitly allowlisted; query-all POST is read-only.
    if (path === '/admin/orders/query-all' && method === 'POST') return ok({ records: [order], total: 1, current: 1, size: 10 })
    if (method === 'GET' && path.startsWith('/admin/variables/type/')) return ok([])
    if (method === 'GET' && ['/admin/orders/agent-accounts', '/courses', '/orders/ORD-RECEIPT-1/progress-logs'].includes(path)) return ok([])
    if (method === 'GET' && path === '/admin/countdown-config/all') return ok({})
    if (method === 'GET' && path === '/admin/orders/statistics') return ok({ totalOrders: 1, completedOrders: 0, totalRevenue: '2.50', todayOrders: 1 })
    const candidateMatch = path.match(/^\/admin\/orders\/(\d+)\/receipt-recoveries\/candidates$/)
    if (candidateMatch && method === 'POST') {
      assert.match(req.headers().authorization || '', /^Bearer /)
      assert.ok([null, '', 'null'].includes(req.postData()))
      candidateReads.push(path)
      if (candidateHeld) { candidateEntered.resolve(); await candidateRelease.promise }
      try {
        if (['failure', 'deny', 'conflict', 'unauthorized'].includes(candidateMode)) return await route.fulfill({ status: candidateMode === 'deny' ? 403 : candidateMode === 'conflict' ? 400 : candidateMode === 'unauthorized' ? 401 : 502, json: { code: candidateMode === 'conflict' ? -110 : -1, message: '模拟查询失败' } })
        const result = { orderId: Number(candidateMatch[1]), receiptIds: [receipt, 'receipt-10'], checkedAt: '2026-09-11T12:00:00', scope: 'CURRENT_RESPONSE' }
        if (candidateMode === 'empty') result.receiptIds = []
        if (candidateMode === 'partial') delete result.checkedAt
        if (candidateMode === 'duplicate') result.receiptIds = [receipt, receipt]
        if (candidateMode === 'wrong-order') result.orderId = 999
        if (candidateMode === 'unbounded') result.receiptIds = Array.from({ length: 21 }, (_, i) => `receipt-${i}`)
        if (candidateMode === 'wrong-scope') result.scope = 'ALL'
        if (candidateMode === 'invalid-id') result.receiptIds = ['<img src=x>']
        if (candidateMode === 'extra-secret') result.password = 'must-not-render-private-proof'
        return await ok(result)
      } finally { candidateFinished?.resolve() }
    }
    const match = path.match(/^\/admin\/orders\/(\d+)\/receipt-recoveries(?:\/([0-9a-f-]+)(\/confirm)?)?$/)
    if (!match) { unexpected.push(method + ' ' + path); return route.abort() }
    assert.match(req.headers().authorization || '', /^Bearer /)
    const ownerOrder = Number(match[1]), id = match[2]
    if (method === 'GET') {
      reads.push(path)
      if (!id) {
        if (historyMode === 'failure') return route.fulfill({ status: 500, json: { code: -1, message: '模拟读取失败' } })
        if (historyMode === 'partial') return ok([{ id: 'bad' }])
        return ok(historyMode === 'empty' ? [] : [...records.values()].filter((v) => v.orderId === ownerOrder).slice(-20).reverse())
      }
      if (mode === 'partial-get') return ok({ ...records.get(id), expiresAt: undefined })
      return ok(records.get(id))
    }
    const body = req.postDataJSON(); writes.push({ method, path, body })
    if (method === 'POST' && !id) {
      assert.deepEqual(Object.keys(body).sort(), ['evidence', 'ownershipConfirmed', 'receiptId', 'requestId'])
      assert.equal(body.ownershipConfirmed, true); assert.equal(body.evidence, note)
      assert.match(body.requestId, /^[0-9a-f-]{36}$/)
      if (mode === 'deny') return route.fulfill({ status: 403, json: { code: 403, message: '权限不足' } })
      const view = { id: body.requestId, orderId: ownerOrder, orderNo: ownerOrder === 1 ? order.orderNo : 'ORD-SECOND', courseName: ownerOrder === 1 ? order.courseName : '订单二课程', receiptId: body.receiptId, state: mode === 'no-proof' ? 'READ_FAILED' : 'READY', expiresAt: '2099-01-01T10:00:00', appliedAt: null, notice: 'not-rendered' }
      records.set(view.id, view)
      if (held) { entered.resolve(); await release.promise }
      if (mode === 'lose-preview') return route.abort()
      if (mode === 'wrong-receipt') return ok({ ...view, receiptId: 'unexpected-id' })
      return ok(view)
    }
    if (method === 'POST' && match[3] === '/confirm') {
      assert.deepEqual(body, { consent: true })
      const view = records.get(id); assert.equal(view.orderId, ownerOrder)
      if (mode === 'conflict') { view.state = 'CONFLICT'; return ok(view) }
      view.state = 'APPLIED'; view.appliedAt = '2026-09-10T11:00:00'
      if (mode === 'lose-confirm') return route.abort()
      return ok(view)
    }
    unexpected.push(method + ' ' + path); return route.abort()
  })
  const page = await context.newPage(); page.setDefaultTimeout(45000); page.setDefaultNavigationTimeout(90000)
  page.on('pageerror', (e) => errors.push(e.message))
  const fill = async (scope) => {
    await scope.getByRole('textbox', { name: '执行编号', exact: true }).fill(receipt)
    await scope.getByRole('textbox', { name: '核对依据', exact: true }).fill(note)
    assert.equal(await scope.getByRole('button', { name: '核对执行编号', exact: true }).isDisabled(), true)
    await scope.getByText('我已核实原始回执及执行账户，确认与此订单属于同一笔业务', { exact: true }).click()
  }
  const approve = async (scope) => {
    assert.equal(await scope.getByRole('button', { name: '确认恢复编号', exact: true }).isDisabled(), true)
    await scope.getByText('确认只关联上述编号，不重新提交订单或产生费用', { exact: true }).click()
    await scope.getByRole('button', { name: '确认恢复编号', exact: true }).click()
  }
  const screenshot = async (name) => { mkdirSync(output, { recursive: true }); await page.screenshot({ path: output + name + '.png', fullPage: true }) }
  await page.goto(base + '/__order_receipts?entry=admin')
  await page.getByText(order.orderNo, { exact: true }).waitFor()
  const row = page.locator('.el-table__body tr').filter({ hasText: order.orderNo })
  await row.getByRole('button', { name: '详情', exact: true }).click()
  const detail = page.getByRole('dialog', { name: '订单详情', exact: true })
  await detail.getByRole('button', { name: '恢复执行编号', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '恢复订单执行编号', exact: true })
  await dialog.getByRole('heading', { name: '找回编号，不重复下单。', exact: true }).waitFor()
  assert.equal(reads.length, 0); assert.equal(writes.length, 0)
  assert.equal(candidateReads.length, 0)
  await dialog.getByRole('button', { name: '查找执行编号', exact: true }).click()
  await dialog.getByText('本次找到 2 个可核对编号', { exact: true }).waitFor()
  assert.equal(candidateReads.length, 1); assert.equal(writes.length, 0)
  assert.equal(await dialog.getByRole('textbox', { name: '执行编号', exact: true }).inputValue(), '')
  assert.equal(await dialog.getByRole('button', { name: '核对执行编号', exact: true }).isDisabled(), true)
  await dialog.getByRole('region', { name: '查找执行编号', exact: true }).scrollIntoViewIfNeeded()
  await screenshot('benz-receipt-candidates-desktop')
  await page.setViewportSize({ width: 390, height: 844 })
  await page.evaluate(() => { document.documentElement.classList.add('dark'); document.documentElement.setAttribute('data-theme', 'dark') })
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false)
  await dialog.getByRole('region', { name: '查找执行编号', exact: true }).scrollIntoViewIfNeeded()
  await screenshot('benz-receipt-candidates-mobile-dark')
  await page.setViewportSize({ width: 1440, height: 1100 })
  await page.evaluate(() => { document.documentElement.classList.remove('dark'); document.documentElement.removeAttribute('data-theme') })
  for (const state of ['empty', 'failure', 'deny', 'conflict', 'partial', 'duplicate', 'wrong-order', 'unbounded', 'wrong-scope', 'invalid-id', 'extra-secret']) {
    candidateMode = state
    const before = candidateReads.length
    await dialog.getByRole('button', { name: '查找执行编号', exact: true }).click()
    const message = state === 'empty' ? '本次未找到可核实编号' : state === 'deny' ? '需要订单管理与接口管理双权限，未获得查找权限。'
      : state === 'conflict' ? '订单或编号记录已变化，请重新查找；未关联编号。' : '编号查找未完成或返回数据不完整，请重试；未关联编号，也未重新下单。'
    await dialog.getByText(message, { exact: true }).waitFor()
    assert.equal(candidateReads.length, before + 1); assert.equal(writes.length, 0)
    assert.equal(await dialog.getByRole('list', { name: '可核对的执行编号', exact: true }).count(), 0)
    if (state !== 'empty') assert.equal(await dialog.getByText('本次未找到可核实编号', { exact: true }).count(), 0)
    assert.equal(await dialog.getByText('must-not-render-private-proof', { exact: true }).count(), 0)
  }
  candidateMode = ''
  await dialog.getByRole('button', { name: '查找执行编号', exact: true }).click()
  await dialog.getByRole('button', { name: '填入执行编号 receipt-9', exact: true }).click()
  assert.equal(await dialog.getByRole('textbox', { name: '执行编号', exact: true }).inputValue(), receipt)
  assert.equal(await dialog.getByRole('textbox', { name: '核对依据', exact: true }).inputValue(), '')
  await dialog.getByRole('textbox', { name: '核对依据', exact: true }).fill(note)
  await dialog.getByText('我已核实原始回执及执行账户，确认与此订单属于同一笔业务', { exact: true }).click()
  await dialog.getByRole('button', { name: '填入执行编号 receipt-10', exact: true }).click()
  assert.equal(await dialog.getByRole('textbox', { name: '核对依据', exact: true }).inputValue(), '')
  assert.equal(await dialog.getByRole('button', { name: '核对执行编号', exact: true }).isDisabled(), true)
  await dialog.getByRole('button', { name: '填入执行编号 receipt-9', exact: true }).click()
  assert.equal(writes.length, 0)
  await fill(dialog); mode = 'lose-preview'
  await dialog.getByRole('button', { name: '核对执行编号', exact: true }).click()
  await dialog.getByText('核对结果未确认，请检查原请求；不要重复提交。', { exact: true }).waitFor()
  const originalId = writes[0].body.requestId
  assert.equal(await dialog.getByRole('button', { name: '确认恢复编号', exact: true }).count(), 0)
  mode = ''; await dialog.getByRole('button', { name: '检查原请求', exact: true }).click()
  await dialog.getByRole('heading', { name: '待确认', exact: true }).waitFor()
  assert.equal(reads.at(-1), basePath + '/' + originalId); assert.equal(writes.length, 1)
  assert.equal(await dialog.locator('img').count(), 0)
  await screenshot('benz-receipt-preview-desktop')
  mode = 'lose-confirm'; await approve(dialog)
  await dialog.getByText('恢复结果未确认，请检查原请求，不要再次提交。', { exact: true }).waitFor()
  assert.equal(writes.length, 2); assert.equal(writes[1].path, basePath + '/' + originalId + '/confirm')
  mode = ''; await dialog.getByRole('button', { name: '检查原请求', exact: true }).click()
  await dialog.getByRole('heading', { name: '已恢复', exact: true }).waitFor(); assert.equal(writes.length, 2)
  await dialog.getByText('执行编号已恢复；金额、状态和进度未改变，可另行刷新执行记录。', { exact: true }).waitFor()
  await dialog.getByRole('button', { name: 'Close this dialog' }).click(); await dialog.waitFor({ state: 'hidden' })
  await detail.getByRole('button', { name: '恢复执行编号', exact: true }).click()
  await dialog.getByRole('button', { name: '查看核对记录', exact: true }).click()
  await dialog.getByRole('button', { name: '查看原请求', exact: true }).click()
  await dialog.getByRole('heading', { name: '已恢复', exact: true }).waitFor(); assert.equal(writes.length, 2)
  await dialog.getByRole('button', { name: '开始新的核对', exact: true }).click()
  for (const state of ['no-proof', 'deny', 'wrong-receipt']) {
    mode = state; await fill(dialog)
    await dialog.getByRole('button', { name: '核对执行编号', exact: true }).click()
    await dialog.getByText(state === 'no-proof' ? '无法核实完整订单身份，未关联编号；查不到不表示订单不存在。' : state === 'deny' ? '需要订单管理与接口管理双权限，未获得核对权限。' : '核对结果未确认，请检查原请求；不要重复提交。', { exact: true }).waitFor()
    assert.equal(await dialog.getByRole('button', { name: '确认恢复编号', exact: true }).count(), 0)
    await dialog.getByRole('button', { name: '开始新的核对', exact: true }).click()
  }
  mode = ''; await fill(dialog); await dialog.getByRole('button', { name: '核对执行编号', exact: true }).click()
  await dialog.getByRole('heading', { name: '待确认', exact: true }).waitFor()
  mode = 'conflict'; await approve(dialog)
  await dialog.getByRole('heading', { name: '记录已变化', exact: true }).waitFor()
  const confirmCount = writes.filter((r) => r.path.endsWith('/confirm')).length
  mode = ''; await dialog.getByRole('button', { name: '开始新的核对', exact: true }).click()
  for (const state of ['failure', 'partial', 'empty']) {
    historyMode = state; await dialog.getByRole('button', { name: '查看核对记录', exact: true }).click()
    await dialog.getByText(state === 'empty' ? '还没有核对记录' : '核对记录读取失败或数据不完整，请重试；未显示为空记录。', { exact: true }).waitFor()
    if (state !== 'empty') assert.equal(await dialog.getByText('还没有核对记录', { exact: true }).count(), 0)
  }
  historyMode = ''; await dialog.getByRole('button', { name: '收起记录', exact: true }).click()
  await fill(dialog); await dialog.getByRole('button', { name: '核对执行编号', exact: true }).click()
  await dialog.getByRole('heading', { name: '待确认', exact: true }).waitFor()
  await page.setViewportSize({ width: 390, height: 844 })
  await page.evaluate(() => { document.documentElement.classList.add('dark'); document.documentElement.setAttribute('data-theme', 'dark') })
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false)
  await screenshot('benz-receipt-mobile-dark')
  await dialog.locator('.receipt-preview').scrollIntoViewIfNeeded()
  await page.screenshot({ path: output + 'benz-receipt-mobile-confirm.png' })
  mode = 'partial-get'; await dialog.getByRole('button', { name: '检查原请求', exact: true }).click()
  await dialog.getByText('原请求尚未确认或数据不完整；未重新查询订单，也未关联编号。', { exact: true }).waitFor()
  assert.equal(await dialog.getByRole('button', { name: '确认恢复编号', exact: true }).count(), 0)
  assert.equal(writes.filter((r) => r.path.endsWith('/confirm')).length, confirmCount)

  // Candidate reads are explicit, disabled while pending, and discarded after an order change or unmount.
  await page.goto(base + '/__order_receipts?entry=scope')
  const candidateScope = page.getByRole('region', { name: '订单执行编号恢复', exact: true })
  candidateHeld = true; candidateEntered = deferred(); candidateRelease = deferred(); candidateFinished = deferred()
  const beforeLookup = candidateReads.length, beforeWrites = writes.length
  await candidateScope.getByRole('button', { name: '查找执行编号', exact: true }).click(); await candidateEntered.promise
  assert.equal(await candidateScope.getByRole('button', { name: '查找执行编号', exact: true }).isDisabled(), true)
  assert.equal(await candidateScope.getByRole('button', { name: '核对执行编号', exact: true }).isDisabled(), true)
  await page.getByRole('button', { name: '切换订单二', exact: true }).click()
  candidateHeld = false; candidateRelease.resolve(); await candidateFinished.promise
  await page.waitForTimeout(120)
  assert.equal(await candidateScope.getByRole('list', { name: '可核对的执行编号', exact: true }).count(), 0)
  assert.equal(await candidateScope.getByRole('textbox', { name: '执行编号', exact: true }).inputValue(), '')
  assert.equal(candidateReads.length, beforeLookup + 1); assert.equal(writes.length, beforeWrites)
  await candidateScope.getByRole('button', { name: '查找执行编号', exact: true }).click()
  await candidateScope.getByText('本次找到 2 个可核对编号', { exact: true }).waitFor()
  assert.equal(candidateReads.at(-1), '/admin/orders/2/receipt-recoveries/candidates')
  await candidateScope.getByRole('button', { name: '填入执行编号 receipt-10', exact: true }).click()
  assert.equal(writes.length, beforeWrites)
  candidateHeld = true; candidateEntered = deferred(); candidateRelease = deferred(); candidateFinished = deferred()
  await candidateScope.getByRole('button', { name: '查找执行编号', exact: true }).click(); await candidateEntered.promise
  await page.getByRole('button', { name: '关闭核对', exact: true }).click()
  candidateHeld = false; candidateRelease.resolve(); await candidateFinished.promise
  assert.equal(await candidateScope.count(), 0); assert.equal(writes.length, beforeWrites)

  // A read or preview resolving after order change cannot populate the next order.
  mode = ''; await page.goto(base + '/__order_receipts?entry=scope')
  const scope = page.getByRole('region', { name: '订单执行编号恢复', exact: true })
  await fill(scope); held = true; entered = deferred(); release = deferred()
  await scope.getByRole('button', { name: '核对执行编号', exact: true }).click(); await entered.promise
  await page.getByRole('button', { name: '切换订单二', exact: true }).click()
  held = false; release.resolve(); await page.waitForTimeout(120)
  assert.equal(await scope.getByText(order.courseName, { exact: true }).count(), 0)
  await fill(scope); await scope.getByRole('button', { name: '核对执行编号', exact: true }).click()
  await scope.getByText('ORD-SECOND', { exact: true }).waitFor()
  await page.getByRole('button', { name: '关闭核对', exact: true }).click()
  assert.equal(await scope.count(), 0)
  const storage = await page.evaluate(() => JSON.stringify({ local: { ...localStorage }, session: { ...sessionStorage } }))
  for (const sensitive of [note, receipt, 'receipt-10', originalId, order.orderNo]) assert.equal(storage.includes(sensitive), false)
  // Response-time authentication failure cannot replay a deliberately triggered discovery request.
  candidateMode = 'unauthorized'; await page.goto(base + '/__order_receipts?entry=scope')
  const beforeAuthFailure = candidateReads.length
  await page.getByRole('button', { name: '查找执行编号', exact: true }).click()
  await page.getByText('编号查找未完成或返回数据不完整，请重试；未关联编号，也未重新下单。', { exact: true }).waitFor()
  await page.waitForTimeout(150)
  assert.equal(candidateReads.length, beforeAuthFailure + 1)
  assert.deepEqual(unexpected, []); assert.deepEqual(errors, [])
  assert.ok(writes.every((r) => r.method === 'POST' && (r.path.endsWith('/receipt-recoveries') || r.path.endsWith('/confirm'))))
  console.log('PASS Benz receipt recovery: bounded explicit candidate discovery/selection, no auto-read/first-choice, empty/error/malformed/permission/claim-change states and pending/unmount isolation; actual admin order entry, explicit full-identity preview/consent, lost preview/confirm GET-only recovery and persisted history, no-proof/403/conflict/malformed states, stale order isolation, mobile dark and no stored credentials; all supplier requests simulated')
} finally { candidateRelease?.resolve(); release?.resolve(); await browser?.close(); await server.close() }
