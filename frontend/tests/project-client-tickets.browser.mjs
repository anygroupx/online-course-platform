import assert from 'node:assert/strict'
import { existsSync, mkdirSync } from 'node:fs'
import { chromium } from 'playwright'
import { createTestServer as createServer } from './fixtures/test-server.mjs';import { pngFixture } from './fixtures/ticket-image.mjs'
const png = pngFixture(), imageId = 'b038e810-6a0d-461c-8dc6-a2aa1aa061bf'
const clientA = 'b038e810-6a0d-461c-8dc6-a2aa1aa061bd', clientB = 'b038e810-6a0d-461c-8dc6-a2aa1aa061bc'
const ticketId = 'b038e810-6a0d-461c-8dc6-a2aa1aa061be', secret = 'npc_' + 'c'.repeat(64), password = 'fixture-only-password'
const customers = [clientA, clientB].map((id, i) => ({ id, label: i ? '同项目另一客户' : '示例下游客户', projectId: 1, projectTitle: '本地项目 A', status: 'ACTIVE', version: 1, balance: '10.000000', unitPrice: '0.250000', refundableUnits: '10', refundBudget: '2.50' }))
const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0"><div id="app"></div><script type="module">
import {createApp} from 'vue';import ElementPlus from 'element-plus';import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';import Page from '/src/views/ProjectClients.vue';import {applyAuthSession} from '/src/utils/authSession.js';applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7});createApp(Page).use(ElementPlus).mount('#app');</script></body></html>`
const server = await createServer({ logLevel: 'error', server: { host: '127.0.0.1', port: 0 }, plugins: [{ name: 'client-ticket-fixture', configureServer(vite) {
  vite.middlewares.use(async (req, res, next) => { if (req.url !== '/__client_tickets') return next(); res.setHeader('Content-Type', 'text/html;charset=utf-8'); res.end(await vite.transformIndexHtml(req.url, html)) })
} }] })
let browser, ticket, loseCreate = true, loseReply = true, failReply = false, loseDecision = true
const receipts = new Map(), replies = [], writes = [], reads = [], errors = [], unexpected = []
let keyState = { configured: false, prefix: null, access: 'READ_ONLY', version: 0, subject: clientA, expiresAt: null }
try {
  await server.listen()
  const base = `http://127.0.0.1:${server.httpServer.address().port}`
  browser = await chromium.launch({ executablePath: process.env.BROWSER_PATH || (existsSync('/snap/bin/chromium') ? '/snap/bin/chromium' : undefined), args: ['--no-sandbox', '--disable-dev-shm-usage'] })
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } })
  page.setDefaultTimeout(45000); page.setDefaultNavigationTimeout(90000)
  page.on('pageerror', e => errors.push(e.message))
  await page.addInitScript(() => { const create=URL.createObjectURL.bind(URL),revoke=URL.revokeObjectURL.bind(URL);window.activeTicketBlobs=new Set();URL.createObjectURL=b=>{const u=create(b);window.activeTicketBlobs.add(u);return u};URL.revokeObjectURL=u=>{window.activeTicketBlobs.delete(u);revoke(u)} })
  await page.route('**/*', async route => {
    const req = route.request(), url = new URL(req.url()), method = req.method()
    if (url.origin !== base) { unexpected.push(url.origin); return route.abort() }
    if (!url.pathname.startsWith('/api/')) return route.continue()
    const path = url.pathname.slice(4), data = JSON.parse(req.postData() || '{}')
    const ok = data => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 1, data }) })
    const rejected = () => route.fulfill({ status: 422, contentType: 'application/json', body: JSON.stringify({ code: 422, message: '请核实原请求及工单版本' }) })
    if (method === 'GET') reads.push({ path, query: url.searchParams.toString() }); else writes.push({ path, data })
    if (path === '/project-clients' && method === 'GET') return ok({ records: customers, total: 2 })
    if (path === '/project-clients/stats') return ok({ customers: 2, active: 2, totalDebited: '5.00', totalReturned: '0.00' })
    if (path === '/project-client-operations') return ok({ records: [], total: 0 })
    if (path === '/project-client-tickets' && method === 'GET') {
      const show = ticket && (!url.searchParams.get('clientId') || url.searchParams.get('clientId') === ticket.clientId) && (!url.searchParams.get('status') || url.searchParams.get('status') === ticket.status)
      return ok({ records: show ? [ticket] : [], total: show ? 1 : 0 })
    }
    if (path.startsWith('/project-client-tickets/images/')) {
      assert.equal(method, 'GET'); assert.match(req.headers().authorization, /^Bearer /); assert.equal(url.search, '');
      return route.fulfill({ contentType: 'image/png', body: png })
    }
    if (path.startsWith('/project-client-tickets/by-request/')) return ok(receipts.get(path.split('/').at(-1)) || null)
    if (path === `/project-client-tickets/${ticketId}` && method === 'GET') return ok(ticket)
    if (path === `/project-client-tickets/${ticketId}/replies` && method === 'GET') return ok({ records: replies, total: replies.length })
    function receipt(action) {
      const value = { ticketId, requestId: data.requestId, version: ticket.version, action, notice: '仅记录，不入账' }
      receipts.set(data.requestId, value); return value
    }
    if (path === '/project-client-tickets' && method === 'POST') {
      ticket = { ...data, id: ticketId, projectId: 1, projectTitle: '本地项目 A', status: 'OPEN', reviewResult: 'PENDING', reviewNote: null, reviewedAt: null, version: 0, createdAt: '2026-09-09 10:00:00', updatedAt: '2026-09-09 10:00:00' }
      ticket.imageId = data.imageData ? imageId : null; delete ticket.imageData
      const value = receipt('CREATE')
      if (loseCreate) { loseCreate = false; return route.abort('failed') }
      return ok(value)
    }
    if (path === `/project-client-tickets/${ticketId}/replies` && method === 'POST') {
      if (failReply) { failReply = false; return rejected() }
      if (data.version !== ticket.version) return rejected()
      ticket.version++; ticket.status = 'IN_PROGRESS'
      replies.push({ id: 'reply-' + ticket.version, version: ticket.version, author: 'OWNER', content: data.content, imageId: data.imageData ? imageId : null, createdAt: '2026-09-09 10:01:00' })
      const value = receipt('REPLY')
      if (loseReply) { loseReply = false; return route.abort('failed') }
      return ok(value)
    }
    if (path === `/project-client-tickets/${ticketId}/decision` && method === 'POST') {
      if (data.version !== ticket.version) return rejected()
      ticket.version++; ticket.status = data.action === 'APPROVE' ? 'RESOLVED' : 'CLOSED'; ticket.reviewResult = data.action === 'APPROVE' ? 'APPROVED' : 'REJECTED'; ticket.reviewNote = data.note; ticket.reviewedAt = '2026-09-09 10:03:00'
      const value = receipt(data.action)
      if (loseDecision) { loseDecision = false; return route.abort('failed') }
      return ok(value)
    }
    if (path === `/project-api-keys/${clientA}`) {
      if (method === 'GET') return ok(keyState)
      assert.equal(data.password, password); assert.equal(data.access, 'SUPPORT')
      keyState = { ...keyState, configured: true, access: data.access, version: 1, prefix: secret.slice(0, 12), expiresAt: '2026-10-09 10:00:00' }
      return ok({ secret, settings: keyState })
    }
    unexpected.push(method + ' ' + path); return rejected()
  })
  await page.goto(base + '/__client_tickets')
  await page.getByText('示例下游客户', { exact: true }).waitFor()
  assert.equal(writes.length, 0)
  const card = page.locator('.client-card').filter({ hasText: '示例下游客户' })
  await card.getByRole('button', { name: '客户售后', exact: true }).click()
  const drawer = page.getByRole('dialog', { name: '客户售后', exact: true })
  await drawer.getByText('暂无客户售后工单').waitFor()
  assert.ok(reads.some(r => r.path === '/project-client-tickets' && r.query.includes(clientA)))
  await drawer.getByRole('button', { name: '新建客户工单' }).click()
  await drawer.locator('.compose .el-select__wrapper').click()
  await page.getByRole('option', { name: '补偿申请', exact: true }).click()
  await drawer.getByLabel('工单标题', { exact: true }).fill('本地额度问题待核实')
  const literal = '<img src=x onerror="window.ticketUnsafe=true"> 仅作为文字证据'
  await drawer.getByLabel('问题描述', { exact: true }).fill(literal)
  await drawer.getByLabel('申请参考金额（元，不是支付指令）').fill('1.25')
  await drawer.locator('label.el-checkbox').filter({ hasText: /我已确认客户和内容/ }).click()
  const picker = drawer.locator('.compose input[type=file]')
  await picker.setInputFiles({ name: 'not-a-raster.svg', mimeType: 'image/svg+xml', buffer: Buffer.from('<svg/>') })
  await drawer.getByRole('alert').filter({ hasText: '仅支持 PNG 或 JPEG 图片' }).waitFor()
  await picker.setInputFiles({ name: 'fixture.png', mimeType: 'image/png', buffer: png })
  await drawer.getByAltText('待提交附件预览').waitFor()
  assert.equal(writes.length, 0)
  assert.ok(await drawer.getByAltText('待提交附件预览').evaluate(img => img.complete && img.naturalWidth > 0))
  assert.equal(await drawer.getByRole('button', { name: '提交客户工单' }).isDisabled(), true)
  await drawer.locator('label.el-checkbox').filter({ hasText: /我已确认客户和内容/ }).click()
  await drawer.getByRole('button', { name: '提交客户工单' }).click()
  await drawer.getByText('先核实这一次请求').waitFor()
  assert.equal(writes.length, 1)
  assert.match(writes[0].data.imageData, /^data:image\/png;base64,/ )
  const originalRequest = writes[0].data.requestId
  // Closing retains the original identity and request; opening another customer must not rebind it.
  await drawer.getByRole('button', { name: '关闭，保留待核对请求' }).click()
  await page.locator('.client-card').filter({ hasText: '同项目另一客户' }).getByRole('button', { name: '客户售后', exact: true }).click()
  await drawer.getByText(originalRequest, { exact: true }).waitFor()
  await drawer.getByRole('heading', { name: '示例下游客户', exact: true }).waitFor()
  await drawer.getByRole('button', { name: '查询原请求结果' }).click()
  await drawer.getByRole('heading', { name: '本地额度问题待核实', exact: true }).waitFor()
  assert.equal(writes.length, 1)
  assert.equal(await page.evaluate(() => window.ticketUnsafe), undefined)
  await drawer.getByText(literal, { exact: true }).waitFor()
  assert.equal(reads.filter(r => r.path.includes('/images/')).length, 0)
  await drawer.getByRole('button', { name: '查看私有图片附件', exact: true }).click()
  const attachment = drawer.getByAltText('已清除元数据的工单附件')
  await attachment.waitFor()
  assert.ok((await attachment.getAttribute('src')).startsWith('blob:'))
  await page.waitForFunction(() => document.querySelector('img[alt="已清除元数据的工单附件"]')?.naturalWidth === 120)
  assert.equal(reads.filter(r => r.path.includes('/images/')).length, 1)
  await drawer.getByRole('button', { name: '收起并清除图片', exact: true }).click()
  assert.equal(await page.evaluate(() => activeTicketBlobs.size), 0)
  await drawer.getByLabel('补充回复').fill('经营者核对：本次不是上游服务履约成功。')
  await drawer.locator('.reply-form input[type=file]').setInputFiles({ name: 'reply.png', mimeType: 'image/png', buffer: png })
  await drawer.getByAltText('待提交附件预览').waitFor()
  await drawer.locator('label.el-checkbox').filter({ hasText: '确认以经营者身份向此客户回复' }).click()
  await drawer.getByRole('button', { name: '发送回复' }).click()
  await drawer.getByText('先核实这一次请求').waitFor()
  await drawer.getByRole('button', { name: '查询原请求结果' }).click()
  await drawer.locator('.message').getByText('经营者核对：本次不是上游服务履约成功。').waitFor()
  assert.equal(writes.length, 2)
  await drawer.getByLabel('补充回复').fill('保留草稿，但刷新后必须重新确认')
  await drawer.locator('label.el-checkbox').filter({ hasText: '确认以经营者身份向此客户回复' }).click()
  await drawer.getByRole('button', { name: '刷新工单', exact: true }).click()
  await drawer.getByRole('heading', { name: '本地额度问题待核实', exact: true }).waitFor()
  assert.equal(await drawer.getByRole('checkbox', { name: '确认以经营者身份向此客户回复' }).isChecked(), false)
  assert.equal(await drawer.getByLabel('补充回复').inputValue(), '保留草稿，但刷新后必须重新确认')
  // A concurrent customer reply makes the visible version stale: do not overwrite, rebase or repeat automatically.
  ticket.version++
  replies.push({ id: 'external-reply', version: ticket.version, author: 'CUSTOMER', content: '客户补充的最新信息', createdAt: '2026-09-09 10:02:00' })
  await drawer.getByLabel('补充回复').fill('这条使用了旧版本，不应直接写入')
  await drawer.locator('label.el-checkbox').filter({ hasText: '确认以经营者身份向此客户回复' }).click()
  await drawer.getByRole('button', { name: '发送回复' }).click()
  await drawer.getByText('先核实这一次请求').waitFor()
  await drawer.getByRole('button', { name: '查询原请求结果' }).click()
  await drawer.getByText('未查到本次提交回执').waitFor()
  assert.equal(writes.length, 3)
  await drawer.getByRole('button', { name: '结束本次尝试并刷新' }).click()
  await drawer.locator('.message').getByText('客户补充的最新信息').waitFor()
  assert.equal(await drawer.getByLabel('补充回复').inputValue(), '')
  // Failure before commit: GET finds no receipt, then a deliberate retry must retain the exact original payload and ID.
  failReply = true
  await drawer.getByLabel('补充回复').fill('已阅读客户最新信息，准备继续处理')
  await drawer.locator('label.el-checkbox').filter({ hasText: '确认以经营者身份向此客户回复' }).click()
  await drawer.getByRole('button', { name: '发送回复' }).click()
  await drawer.getByText('先核实这一次请求').waitFor()
  await drawer.getByRole('button', { name: '查询原请求结果' }).click()
  await drawer.getByRole('button', { name: '使用原编号重试一次' }).click()
  await drawer.locator('.message').getByText('已阅读客户最新信息，准备继续处理').waitFor()
  assert.equal(writes.length, 5); assert.deepEqual(writes[3], writes[4])
  await drawer.locator('.reply-form input[type=file]').setInputFiles({ name: 'image-only.png', mimeType: 'image/png', buffer: png })
  await drawer.getByAltText('待提交附件预览').waitFor()
  await drawer.locator('label.el-checkbox').filter({ hasText: '确认以经营者身份向此客户回复' }).click()
  await drawer.getByRole('button', { name: '发送回复' }).click()
  await page.waitForFunction(() => document.querySelectorAll('.message').length === 4)
  assert.equal(writes.at(-1).data.content, '')
  assert.match(writes.at(-1).data.imageData, /^data:image\/png;base64,/)
  assert.equal(reads.filter(r => r.path.includes('/images/')).length, 1)
  await drawer.locator('.decision-form .el-select__wrapper').click()
  await page.getByRole('option', { name: '通过申请（仅记录，不入账）', exact: true }).click()
  await drawer.getByLabel('结论说明（客户可见）').fill('已核实申请事实，后续如需支付必须另走合法资金流程。')
  await drawer.locator('label.el-checkbox').filter({ hasText: /我已阅读当前版本/ }).click()
  await drawer.getByRole('button', { name: '确认处理结论' }).click()
  await drawer.getByText('先核实这一次请求').waitFor()
  await drawer.getByRole('button', { name: '查询原请求结果' }).click()
  await drawer.getByText('申请已通过', { exact: true }).waitFor()
  assert.equal(writes.length, 7)
  assert.equal(await drawer.getByRole('button', { name: '发送回复' }).count(), 0)
  assert.equal(await drawer.getByRole('button', { name: '确认处理结论' }).count(), 0)
  assert.equal(customers[0].balance, '10.000000')
  assert.ok(writes.every(w => w.path.startsWith('/project-client-tickets')))
  mkdirSync('../.cache/native-service-ui', { recursive: true })
  await drawer.getByRole('heading', { name: '示例下游客户', exact: true }).scrollIntoViewIfNeeded()
  await page.waitForFunction(() => document.querySelectorAll('.el-message').length === 0)
  await page.screenshot({ path: '../.cache/native-service-ui/project-client-tickets-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 960 })
  await page.evaluate(() => document.documentElement.classList.add('dark'))
  await drawer.getByRole('heading', { name: '示例下游客户', exact: true }).scrollIntoViewIfNeeded()
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
  await page.screenshot({ path: '../.cache/native-service-ui/project-client-tickets-mobile-dark.png', fullPage: false })
  await drawer.getByRole('button', { name: '查看私有图片附件', exact: true }).first().click()
  await drawer.getByAltText('已清除元数据的工单附件').waitFor()
  await drawer.getByRole('button', { name: '关闭售后窗口' }).click()
  await page.waitForFunction(() => activeTicketBlobs.size === 0)
  await card.getByRole('button', { name: '客户 API 密钥', exact: true }).click()
  const keyDrawer = page.getByRole('dialog', { name: '客户 API 密钥', exact: true })
  await keyDrawer.getByRole('radio', { name: '只读查询', exact: true }).waitFor()
  assert.equal(await keyDrawer.getByRole('radio', { name: '只读查询', exact: true }).isChecked(), true)
  await keyDrawer.locator('label.el-radio').filter({ hasText: '本人提单与回复（不含资金）' }).click()
  await keyDrawer.getByLabel('当前登录密码').fill(password)
  await keyDrawer.locator('label.el-checkbox').filter({ hasText: /我确认接收方和权限范围/ }).click()
  await keyDrawer.getByRole('button', { name: '签发密钥（仅显示一次）' }).click()
  await keyDrawer.getByText('请立即安全保存这一次的密钥').waitFor()
  assert.equal(writes.at(-1).data.access, 'SUPPORT')
  const storage = await page.evaluate(() => JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]))
  assert.ok(!storage.includes(secret)); assert.ok(!storage.includes(password))
  await keyDrawer.getByRole('button', { name: '关闭并清除敏感输入' }).click()
  assert.deepEqual(errors, []); assert.deepEqual(unexpected, [])
  console.log('PASS downstream after-sales: real Vue customer entry, pure text, exact same-customer recovery, lost create/reply/review GET-only, stale version, explicit same-ID retry, final no-payment result, opt-in SUPPORT credential, image MIME guard, atomic image upload and pure-image reply, authenticated on-demand Blob view/revoke, desktop/mobile dark; APIs simulated only.')
} catch (e) { await browser?.contexts()[0]?.pages()[0]?.screenshot({ path: '../.cache/native-service-ui/client-ticket-browser-failure.png', fullPage: true }).catch(() => {}); throw e } finally { await browser?.close(); await server.close() }
