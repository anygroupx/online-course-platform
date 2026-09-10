import assert from 'node:assert/strict'
import { existsSync, mkdirSync } from 'node:fs'
import { chromium } from 'playwright'
import { createTestServer } from './fixtures/test-server.mjs'

// Exercise production views and their child components; intercept every API and
// external request so this regression cannot contact a supplier or mutate funds.
const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;padding:16px"><div id="app"></div><script type="module">
import {createApp,ref,h} from 'vue';import {createPinia} from 'pinia';import ElementPlus from 'element-plus';import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';
import Admin from '/src/views/AdminProjectCenter.vue';import Clients from '/src/views/ProjectClients.vue';import Ledger from '/src/components/projectcenter/ProjectLedger.vue';import {applyAuthSession} from '/src/utils/authSession.js';
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7,isAdmin:true});
const entry=new URLSearchParams(location.search).get('entry');const component=entry==='admin'?Admin:entry==='self'?Clients:{setup(){const owner=ref(7),visible=ref(true);return()=>h('div',[
  h('nav',{style:'display:flex;gap:12px;margin-bottom:16px'},[h('button',{onClick:()=>owner.value=7},'查看经营者七'),h('button',{onClick:()=>owner.value=8},'查看经营者八'),h('button',{onClick:()=>visible.value=false},'关闭记录')]),
  visible.value?h(Ledger,{admin:true,ownerId:owner.value}):null])}};
createApp(component).use(createPinia()).use(ElementPlus).mount('#app');</script></body></html>`
const server = await createTestServer({ logLevel: 'error', plugins: [{ name: 'project-records-fixture', configureServer(vite) {
  vite.middlewares.use(async (req, res, next) => {
    if (!req.url?.startsWith('/__project_records')) return next()
    res.setHeader('Content-Type', 'text/html;charset=utf-8')
    res.end(await vite.transformIndexHtml(req.url, html))
  })
} }] })
const uuid = (n) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`
const time = '2026-09-10T10:00:00'
const funding = { settledOperations: 3, unresolvedOperations: 1, debited: '100.11', returned: '1.01', netDebited: '99.10' }
const usage = {
  window: { from: '2026-09-09T10:00:00', through: time, timezone: 'Asia/Shanghai' },
  calls: { total: 3, failed: 1, last24Hours: 2, failedLast24Hours: 1, actionKinds: 1 },
  actions: [{ action: 'USAGE', total: 3, failed: 1, last24Hours: 2, failedLast24Hours: 1 }], moreActions: false,
  clients: { total: 2, active: 1, suspended: 1, closed: 0, projects: 1 },
  tickets: { total: 1, open: 1, inProgress: 0, resolved: 0, closed: 0, pendingCompensation: 0 }, localFunding: funding,
}
const owners = Array.from({ length: 21 }, (_, i) => ({
  id: i + 7, username: i === 2 ? null : i === 0 ? '经营者%_!' : `经营者${i + 7}`,
  status: i === 2 ? 'MISSING' : i === 1 ? 'DISABLED' : 'ACTIVE', activeAccounts: 1, activeClients: 1,
  accountDebited: '100.11', accountReturned: '1.01', clientDebited: '10.11', clientReturned: '0.01', lastActivity: i === 2 ? null : time,
}))
const account = { id: uuid(100), projectId: 1, title: '项目%_! <img src=x>', status: 'UNKNOWN', cachedBalance: null, balanceCheckedAt: null, unitPrice: '0.123456', refundableUnits: '12.123456', refundBudget: '3.01', debited: '10.11', returned: '1.01', unresolvedOperations: 1 }
const client = { id: uuid(200), projectId: 1, title: account.title, label: '客户明细甲', status: 'SUSPENDED', balance: '12345678901234.123456', unitPrice: '0.123456', refundableUnits: '12.123456', refundBudget: '3.01', debited: '10.11', returned: '1.01', createdAt: time }
const records = Array.from({ length: 22 }, (_, i) => ({
  book: i === 1 || i === 2 ? 'CUSTOMER_CREDIT' : 'PROJECT_ACCOUNT', id: uuid(i === 1 ? 1 : i + 1), ownerId: 7, projectId: 1,
  title: account.title, subjectId: i === 1 || i === 2 ? client.id : account.id,
  action: i === 2 ? 'WITHDRAW' : 'TOP_UP', direction: i === 2 ? 'CREDIT' : 'DEBIT',
  amount: i === 0 ? '999999999999.99' : i === 1 ? '10.11' : i === 2 ? '1.01' : '0.01',
  units: '12.123456', unitPrice: '0.123456', subjectBalanceAfter: i === 0 ? null : '23.123456', walletBalanceAfter: i === 0 ? null : '45.11',
  requestedAt: '2026-09-08T22:00:00', settledAt: time,
}))
records.push({ ...records[0], id: uuid(90), ownerId: 8, title: '经营者八独立记录', amount: '8.00' })
const listing = (rows, current = 1) => ({ records: rows.slice((current - 1) * 20, current * 20), total: rows.length, current, size: 20 })
const cents = (value) => BigInt(value.replace('.', ''))
const decimal = (value) => `${value < 0n ? '-' : ''}${(value < 0n ? -value : value) / 100n}.${String((value < 0n ? -value : value) % 100n).padStart(2, '0')}`
const ledger = (query, self = false) => {
  const rows = records.filter((row) => (!self || row.ownerId === 7) && (!query.ownerId || String(row.ownerId) === query.ownerId) &&
    (!query.projectId || String(row.projectId) === query.projectId) && (!query.clientId || row.book === 'CUSTOMER_CREDIT' && row.subjectId === query.clientId) &&
    (!query.book || row.book === query.book) && (!query.direction || row.direction === query.direction) &&
    (!query.keyword || row.title.includes(query.keyword) || row.id.includes(query.keyword)) &&
    (!query.fromDate || row.settledAt.slice(0, 10) >= query.fromDate) && (!query.throughDate || row.settledAt.slice(0, 10) <= query.throughDate))
  const totals = ['PROJECT_ACCOUNT', 'CUSTOMER_CREDIT'].flatMap((book) => {
    const group = rows.filter((row) => row.book === book)
    if (!group.length) return []
    const debit = group.filter((row) => row.direction === 'DEBIT').reduce((sum, row) => sum + cents(row.amount), 0n)
    const credit = group.filter((row) => row.direction === 'CREDIT').reduce((sum, row) => sum + cents(row.amount), 0n)
    return [{ book, operations: group.length, debited: decimal(debit), returned: decimal(credit), netDebited: decimal(debit - credit) }]
  })
  return { ...listing(rows, Number(query.page)), totals, checkedAt: time, timezone: 'Asia/Shanghai' }
}
const deferred = () => { let resolve; const promise = new Promise((r) => { resolve = r }); return { promise, resolve } }
let browser, ownerMode = '', detailMode = '', subjectMode = '', ledgerMode = '', heldOwner, holdEntered, holdRelease
const reads = [], writes = [], unexpected = [], errors = []
const last = (path) => reads.filter((row) => row.path === path).at(-1)?.query
const ledgerPath = '/admin/project-reports/ledger'
const output = new URL('../../.cache/native-service-ui/', import.meta.url).pathname
try {
  await server.listen()
  const base = server.resolvedUrls.local[0].replace(/\/$/, '')
  browser = await chromium.launch({ executablePath: existsSync('/snap/bin/chromium') ? '/snap/bin/chromium' : undefined, args: ['--no-sandbox', '--disable-dev-shm-usage'] })
  const context = await browser.newContext({ viewport: { width: 1440, height: 1100 } })
  await context.route('**/*', async (route) => {
    const request = route.request(), url = new URL(request.url())
    if (url.origin !== base) { unexpected.push(url.origin); return route.abort() }
    if (!url.pathname.startsWith('/api/')) return route.continue()
    const path = url.pathname.slice(4), query = Object.fromEntries(url.searchParams)
    if (request.method() !== 'GET') { writes.push(path); return route.abort() }
    reads.push({ path, query })
    assert.match(request.headers().authorization || '', /^Bearer /)
    const ok = async (data) => { try { await route.fulfill({ json: { code: 1, data }, headers: { 'Cache-Control': 'no-store' } }) } catch {} }
    const failure = async (mode) => { try { await route.fulfill({ status: mode === 'deny' ? 403 : 500, json: { code: -1, message: '模拟读取失败' } }) } catch {} }
    if (path === '/admin/project-reports/owners') {
      if (ownerMode === 'deny' || ownerMode === 'error') return failure(ownerMode)
      if (ownerMode === 'partial') return ok({ records: [], total: 0 })
      const filtered = ownerMode === 'empty' ? [] : owners.filter((row) => !query.keyword || row.username?.includes(query.keyword) || String(row.id) === query.keyword)
      return ok(listing(filtered, Number(query.page)))
    }
    const subject = path.match(/^\/admin\/project-reports\/owners\/(\d+)\/(accounts|clients)$/)
    if (subject) {
      if (subjectMode === 'error') return failure('error')
      if (subjectMode === 'partial') return ok(listing([{ id: client.id }]))
      let rows = subjectMode === 'empty' ? [] : subject[2] === 'accounts' ? [account] : [client]
      if (query.projectId) rows = rows.filter((row) => String(row.projectId) === query.projectId)
      if (query.status) rows = rows.filter((row) => row.status === query.status)
      return ok(listing(rows, Number(query.page)))
    }
    const detail = path.match(/^\/admin\/project-reports\/owners\/(\d+)$/)
    if (detail) {
      if (detailMode === 'partial') return ok({ owner: owners[0] })
      if (detailMode === 'wrong-owner') return ok({ owner: owners[1], usage, accountFunding: funding })
      return ok({ owner: owners.find((row) => row.id === Number(detail[1])), usage, accountFunding: funding })
    }
    if (path === ledgerPath || path === '/project-ledger') {
      assert.equal(query.pageSize, '20')
      if (path === '/project-ledger') assert.equal(query.ownerId, undefined)
      if (heldOwner !== undefined && heldOwner === query.ownerId) { holdEntered.resolve(); await holdRelease.promise }
      if (ledgerMode === 'deny' || ledgerMode === 'error') return failure(ledgerMode)
      if (ledgerMode === 'partial') return ok({ ...ledger(query), totals: undefined })
      if (ledgerMode === 'empty') return ok({ ...listing([], Number(query.page)), totals: [], checkedAt: time, timezone: 'Asia/Shanghai' })
      return ok(ledger(query, path === '/project-ledger'))
    }
    if (['/admin/service-projects', '/project-clients', '/project-clients/catalog', '/project-client-operations'].includes(path)) return ok(listing([]))
    if (path === '/project-clients/stats') return ok({ customers: 0, active: 0, appliedOperations: 0, totalDebited: '0.00', totalReturned: '0.00' })
    unexpected.push(path); return route.abort()
  })
  const page = await context.newPage()
  page.setDefaultTimeout(45000); page.setDefaultNavigationTimeout(90000)
  page.on('pageerror', (error) => errors.push(error.message))
  const screenshot = async (name) => { mkdirSync(output, { recursive: true }); await page.screenshot({ path: output + name + '.png', fullPage: true }) }
  const formItem = (scope, label) => scope.locator('.el-form-item').filter({ has: page.locator('label', { hasText: label }) })
  const select = async (scope, label, option) => {
    await formItem(scope, label).locator('.el-select').click()
    await page.getByRole('option', { name: option, exact: true }).click()
  }
  const noOverflow = async () => assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false)

  await page.goto(base + '/__project_records?entry=admin')
  await page.getByRole('tab', { name: '经营者明细', exact: true }).waitFor()
  assert.equal(reads.some((row) => row.path.startsWith('/admin/project-reports')), false)
  await page.getByRole('tab', { name: '经营者明细', exact: true }).click()
  await page.getByRole('heading', { name: '经营者%_!', exact: true }).waitFor()
  assert.equal(await page.getByText('已停用', { exact: true }).count(), 1)
  assert.equal(await page.getByText('账号已移除', { exact: true }).count(), 2)
  await page.getByRole('button', { name: 'Go to next page' }).click()
  await page.getByRole('heading', { name: '经营者27', exact: true }).waitFor()
  assert.equal(last('/admin/project-reports/owners').page, '2')
  const search = page.getByRole('textbox', { name: '经营者编号或用户名' })
  const before = reads.length
  await search.fill('%_!')
  assert.equal(reads.length, before, 'editing a filter must not issue requests')
  await page.getByRole('button', { name: '查询经营者', exact: true }).click()
  await page.getByRole('heading', { name: '经营者%_!', exact: true }).waitFor()
  assert.equal(last('/admin/project-reports/owners').keyword, '%_!')
  assert.equal(last('/admin/project-reports/owners').page, '1')
  await screenshot('project-records-owners-desktop')
  await page.getByRole('button', { name: '查看经营者明细', exact: true }).click()
  const drawer = page.getByRole('dialog', { name: '经营者明细', exact: true })
  await drawer.getByRole('heading', { name: '用量与售后', exact: true }).waitFor()
  assert.equal(reads.some((row) => row.path.endsWith('/7/accounts') || row.path.endsWith('/7/clients')), false)
  await drawer.getByRole('tab', { name: '项目账户', exact: true }).click()
  await drawer.getByText('未记录', { exact: true }).waitFor()
  assert.equal(await drawer.locator('img').count(), 0, 'project titles must be escaped text')
  await drawer.getByText('仍待核实 1 笔操作，未计入结算金额', { exact: false }).waitFor()
  await drawer.getByRole('button', { name: '查看资金记录', exact: true }).click()
  await drawer.getByText('−¥999999999999.99', { exact: true }).waitFor()
  assert.deepEqual({ ownerId: last(ledgerPath).ownerId, projectId: last(ledgerPath).projectId, book: last(ledgerPath).book }, { ownerId: '7', projectId: '1', book: 'PROJECT_ACCOUNT' })
  assert.equal(await drawer.getByText('未记录', { exact: true }).count(), 2)
  assert.equal(await drawer.getByText('¥1000000000000.18', { exact: true }).count(), 2)
  await drawer.getByRole('tab', { name: '客户账户', exact: true }).click()
  await drawer.getByText('12345678901234.123456', { exact: true }).waitFor()
  await formItem(drawer, '筛选项目编号').locator('input').fill('1')
  await select(drawer, '客户状态', '已暂停')
  await drawer.getByRole('button', { name: '筛选客户', exact: true }).click()
  await drawer.getByRole('heading', { name: '客户明细甲', exact: true }).waitFor()
  assert.equal(last('/admin/project-reports/owners/7/clients').projectId, '1')
  assert.equal(last('/admin/project-reports/owners/7/clients').status, 'SUSPENDED')
  await drawer.getByRole('button', { name: '查看资金记录', exact: true }).click()
  await drawer.getByText('+¥1.01', { exact: true }).waitFor()
  assert.equal(last(ledgerPath).clientId, client.id)
  assert.equal(last(ledgerPath).book, 'CUSTOMER_CREDIT')
  assert.equal(last(ledgerPath).ownerId, '7')
  assert.equal(await drawer.getByText('经营者编号', { exact: true }).count(), 0)
  await select(drawer, '记录类型', '项目账户兑换')
  assert.equal(await drawer.getByRole('button', { name: '查询流水', exact: true }).isDisabled(), true)
  await select(drawer, '记录类型', '客户额度')
  await screenshot('project-records-client-desktop')
  await drawer.getByRole('button', { name: 'Close this dialog' }).click()
  await drawer.waitFor({ state: 'hidden' })

  // Historical identities can be inspected, but bad details cannot masquerade as
  // another owner. Failures and malformed pages have no synthetic empty state.
  await search.fill('8'); await page.getByRole('button', { name: '查询经营者', exact: true }).click()
  await page.getByRole('heading', { name: '经营者8', exact: true }).waitFor()
  await page.locator('.record-card').filter({ has: page.getByRole('heading', { name: '经营者8', exact: true }) }).getByRole('button', { name: '查看经营者明细', exact: true }).click()
  await drawer.getByRole('heading', { name: '经营者8', exact: true }).waitFor()
  await drawer.getByText('已停用', { exact: true }).waitFor()
  await drawer.getByRole('button', { name: 'Close this dialog' }).click(); await drawer.waitFor({ state: 'hidden' })
  await search.fill('9'); await page.getByRole('button', { name: '查询经营者', exact: true }).click()
  await page.getByRole('heading', { name: '账号已移除', exact: true }).waitFor()
  await page.locator('.record-card').filter({ has: page.getByRole('heading', { name: '账号已移除', exact: true }) }).getByRole('button', { name: '查看经营者明细', exact: true }).click()
  await drawer.getByRole('heading', { name: '账号已移除', exact: true }).waitFor()
  detailMode = 'wrong-owner'; await drawer.getByRole('button', { name: '刷新明细', exact: true }).click()
  await drawer.getByText('经营者明细读取失败或记录已变化，请重试。', { exact: true }).waitFor()
  assert.equal(await drawer.getByRole('heading', { name: '经营者8', exact: true }).count(), 0)
  detailMode = ''; await drawer.getByRole('button', { name: '刷新明细', exact: true }).click()
  await drawer.getByRole('heading', { name: '账号已移除', exact: true }).waitFor()
  subjectMode = 'partial'; await drawer.getByRole('tab', { name: '客户账户', exact: true }).click()
  await drawer.getByText('账户记录读取失败，请重试；不会将失败显示为无记录。', { exact: true }).waitFor()
  assert.equal(await drawer.getByText('没有符合条件的账户', { exact: true }).count(), 0)
  subjectMode = ''; await drawer.getByRole('button', { name: 'Close this dialog' }).click(); await drawer.waitFor({ state: 'hidden' })
  for (const mode of ['deny', 'partial', 'error', 'empty']) {
    ownerMode = mode; await page.getByRole('button', { name: '刷新经营者', exact: true }).click()
    await page.getByText(mode === 'deny' ? '需要接口管理与资金核对双权限，未读取经营者资料。' : mode === 'empty' ? '没有符合条件的经营者记录' : '经营者记录读取失败，请重试；不会将失败显示为没有经营者。', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: '查看经营者明细', exact: true }).count(), 0)
  }
  ownerMode = ''
  await page.getByRole('tab', { name: '资金流水', exact: true }).click()
  const report = page.getByRole('region', { name: '项目资金流水', exact: true })
  await report.getByText('−¥999999999999.99', { exact: true }).waitFor()
  assert.equal(await report.locator('.record-ledger-row').count(), 20)
  assert.equal(await report.getByText(`操作编号 ${uuid(1)}`, { exact: false }).count(), 2, 'same IDs across books are distinct rows')
  const allTotals = await report.locator('.record-totals').innerText()
  // Unapplied edits must not silently alter a pagination request.
  await formItem(report, '项目名 / 操作编号').locator('input').fill('not-applied')
  await report.getByRole('button', { name: 'Go to next page' }).click()
  await report.getByText('经营者八独立记录', { exact: true }).waitFor()
  assert.equal(last(ledgerPath).page, '2'); assert.equal(last(ledgerPath).keyword, undefined)
  assert.equal(await report.locator('.record-totals').innerText(), allTotals)
  await formItem(report, '项目名 / 操作编号').locator('input').fill('%_!')
  await formItem(report, '经营者编号').locator('input').fill('7')
  await formItem(report, '结算开始日期').locator('input').fill('2026-09-10')
  await formItem(report, '结算结束日期').locator('input').fill('2026-09-10')
  await select(report, '资金方向', '转回余额')
  const queryCount = reads.filter((row) => row.path === ledgerPath).length
  await formItem(report, '项目名 / 操作编号').locator('input').press('Enter')
  await report.getByText('+¥1.01', { exact: true }).waitFor()
  assert.equal(reads.filter((row) => row.path === ledgerPath).length, queryCount + 1, 'Enter submits exactly one GET')
  assert.deepEqual(last(ledgerPath), { ownerId: '7', direction: 'CREDIT', keyword: '%_!', fromDate: '2026-09-10', throughDate: '2026-09-10', page: '1', pageSize: '20' })
  assert.equal(await report.getByText('¥-1.01', { exact: true }).count(), 1)
  await formItem(report, '结算开始日期').locator('input').fill('2026-09-11')
  assert.equal(await report.getByRole('button', { name: '查询流水', exact: true }).isDisabled(), true)
  await report.getByRole('button', { name: '重置筛选', exact: true }).click()
  await report.getByText('−¥999999999999.99', { exact: true }).waitFor()
  await screenshot('project-records-ledger-desktop')
  for (const mode of ['deny', 'partial', 'error', 'empty']) {
    ledgerMode = mode; await report.getByRole('button', { name: '刷新记录', exact: true }).click()
    await report.getByText(mode === 'deny' ? '没有读取这些资金记录的权限。' : mode === 'empty' ? '没有符合条件的已结算记录' : '资金记录读取失败或数据不完整；请重试，未显示为零金额。', { exact: true }).waitFor()
    assert.equal(await report.locator('.record-ledger-row').count(), 0)
    assert.equal(await report.locator('.record-totals .record-card').count(), 0)
  }
  ledgerMode = ''

  // The owner-facing entry uses a JWT-derived scope and never reads admin data.
  const marker = reads.length
  await page.goto(base + '/__project_records?entry=self')
  await page.getByRole('button', { name: '资金流水', exact: true }).waitFor()
  assert.equal(reads.slice(marker).some((row) => row.path === '/project-ledger'), false)
  await page.getByRole('button', { name: '资金流水', exact: true }).click()
  const self = page.getByRole('dialog', { name: '我的资金流水', exact: true })
  await self.getByText('−¥999999999999.99', { exact: true }).waitFor()
  assert.equal(reads.slice(marker).some((row) => row.path.startsWith('/admin/')), false)
  assert.equal(await self.getByText('经营者编号', { exact: true }).count(), 0)
  assert.equal(last('/project-ledger').ownerId, undefined)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.evaluate(() => { document.documentElement.classList.add('dark'); document.documentElement.setAttribute('data-theme', 'dark') })
  await noOverflow(); await screenshot('project-records-self-mobile-dark')
  await self.locator('.record-totals').scrollIntoViewIfNeeded()
  await page.screenshot({ path: output + 'project-records-self-ledger-mobile-dark.png' })
  assert.equal(await self.locator('.record-ledger-row').evaluateAll((rows) => rows.some((row) => row.scrollWidth > row.clientWidth + 1)), false)
  assert.equal(await self.locator('.record-filters .el-input__wrapper').evaluateAll((inputs) => inputs.every((input) => input.getBoundingClientRect().height >= 44)), true)
  assert.equal(await self.getByText('经营者八独立记录', { exact: true }).count(), 0)

  // Abort + generation checks prevent a delayed request repainting a new scope.
  heldOwner = '7'; holdEntered = deferred(); holdRelease = deferred()
  await page.goto(base + '/__project_records?entry=scope')
  await holdEntered.promise
  await page.getByRole('button', { name: '查看经营者八', exact: true }).click()
  await page.getByText('经营者八独立记录', { exact: true }).waitFor()
  heldOwner = undefined; holdRelease.resolve()
  await page.waitForTimeout(150)
  assert.equal(await page.getByText('−¥999999999999.99', { exact: true }).count(), 0)
  await noOverflow()
  heldOwner = '7'; holdEntered = deferred(); holdRelease = deferred()
  await page.getByRole('button', { name: '查看经营者七', exact: true }).click(); await holdEntered.promise
  await page.getByRole('button', { name: '关闭记录', exact: true }).click()
  heldOwner = undefined; holdRelease.resolve(); await page.waitForTimeout(150)
  assert.equal(await page.getByRole('region', { name: '项目资金流水', exact: true }).count(), 0)
  const storage = await page.evaluate(() => JSON.stringify({ local: { ...localStorage }, session: { ...sessionStorage } }))
  for (const privateValue of [uuid(1), uuid(100), uuid(200), account.title, client.label, '999999999999.99']) assert.equal(storage.includes(privateValue), false)
  assert.deepEqual(writes, []); assert.deepEqual(unexpected, []); assert.deepEqual(errors, [])
  console.log('PASS project records workflow: actual entry points, owner search/paging/history, account/client drill-down, exact separate-book totals, applied filters/date boundaries, GET-only self/admin isolation, malformed/error states, stale cancellation, mobile dark mode and no persisted records')
} finally {
  holdRelease?.resolve()
  await browser?.close(); await server.close()
}
