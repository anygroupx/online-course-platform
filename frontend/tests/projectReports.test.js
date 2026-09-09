import test from 'node:test'
import assert from 'node:assert/strict'
import { validProjectReport, validProjectBalancePage, projectCallLabel, reportTime } from '../src/utils/projectReports.js'

const calls = { total: 10, failed: 2, last24Hours: 6, failedLast24Hours: 1, actionKinds: 1 }
const funding = { settledOperations: 2, debited: '9007199254740993.01', returned: '0.01', netDebited: '9007199254740993.00', unresolvedOperations: 0 }
const owner = () => ({
  window: { from: '2026-09-08T22:00:00.123456789', through: '2026-09-09T22:00:00.123456789', timezone: 'Asia/Shanghai' },
  calls: { ...calls }, actions: [{ action: 'USAGE', ...calls }], moreActions: false,
  clients: { total: 3, active: 2, suspended: 1, closed: 0, projects: 2 },
  tickets: { total: 3, open: 1, inProgress: 1, resolved: 1, closed: 0, pendingCompensation: 1 }, localFunding: { ...funding },
})
const page = () => ({ current: 1, total: 1, records: [{ projectId: 1, title: '本地项目', customers: 3, active: 2, activeUnits: '12345678901234.123456', suspendedUnits: '0', refundBudget: '10.01' }] })

test('reports preserve decimal strings and exact Beijing window text without money coercion', () => {
  const report = owner()
  assert.equal(validProjectReport(report), true)
  assert.equal(report.localFunding.debited, '9007199254740993.01')
  assert.equal(reportTime(report.window.through), '2026-09-09 22:00:00')
  assert.equal(validProjectBalancePage(page()), true)
})
test('partial malformed or unsafe counters never become a fake zero report', () => {
  for (const value of [null, {}, { ...owner(), calls: null }, { ...owner(), clients: {} }, { ...owner(), tickets: {} }]) assert.equal(validProjectReport(value), false)
  for (const value of [-1, '6', NaN, 1.5, Number.MAX_SAFE_INTEGER + 1]) {
    const report = owner(); report.calls.last24Hours = value; assert.equal(validProjectReport(report), false)
  }
  const report = owner(); report.calls.failedLast24Hours = 8; assert.equal(validProjectReport(report), false)
})
test('owner actions are bounded and untrusted text is not accepted as an action key', () => {
  const report = owner(); report.actions = Array(51).fill(report.actions[0]); assert.equal(validProjectReport(report), false)
  report.actions = [{ ...calls, action: '<img onerror=1>' }]; assert.equal(validProjectReport(report), false)
  assert.equal(projectCallLabel('USAGE'), '查询用量统计')
  assert.equal(projectCallLabel('FUTURE_SAFE_ACTION'), 'FUTURE_SAFE_ACTION')
})
test('global reports require both separate funding groups and never fill missing supplier data', () => {
  const global = { ...owner(), publishedProjects: 2, activeUpstreamBindings: 3, activeUpstreamOwners: 2, activeLocalOwners: 1 }
  assert.equal(validProjectReport(global, true), false)
  global.upstreamFunding = { ...funding, unresolvedOperations: 2 }; assert.equal(validProjectReport(global, true), true)
  global.upstreamFunding.debited = 10.01; assert.equal(validProjectReport(global, true), false)
})
test('project pages keep separate exact units and reject incomplete or unbounded pages', () => {
  assert.equal(validProjectBalancePage(null), false)
  const value = page(); value.records.push({ ...value.records[0], projectId: 2, activeUnits: '5' }); value.total = 2
  assert.equal(validProjectBalancePage(value), true)
  value.records[0].activeUnits = '1e9'; assert.equal(validProjectBalancePage(value), false)
  value.records[0].activeUnits = '1'; value.records = Array(21).fill(value.records[0]); assert.equal(validProjectBalancePage(value), false)
})
test('inconsistent ticket/client totals and negative paid budgets fail rather than imply money', () => {
  const report = owner(); report.clients.total = 2; assert.equal(validProjectReport(report), false)
  const ticket = owner(); ticket.tickets.pendingCompensation = 3; assert.equal(validProjectReport(ticket), false)
  const balance = page(); balance.records[0].refundBudget = '-1.00'; assert.equal(validProjectBalancePage(balance), false)
  const money = owner(); money.localFunding.returned = '-0.01'; assert.equal(validProjectReport(money), false)
})
