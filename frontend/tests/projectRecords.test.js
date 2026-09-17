import test from 'node:test'
import assert from 'node:assert/strict'
import { validOwner, validOwnerDetail, validRecordPage, validSubject, validLedger, recordFilters, validRecordFilters, recordStatus, recordTime } from '../src/utils/projectRecords.js'
const id = '11d9f28e-2cf5-4ddc-8a4d-4263f7d763d1'
const owner = { id: 7, username: null, status: 'MISSING', activeAccounts: 0, activeClients: 0, accountDebited: '1.01', accountReturned: '0.01', clientDebited: '0.00', clientReturned: '0.00', lastActivity: null }
const row = { book: 'PROJECT_ACCOUNT', id, ownerId: 7, projectId: 1, title: '原样显示 <img src=x>', subjectId: id, action: 'TOP_UP', direction: 'DEBIT', amount: '999999999999.99', units: '12345678901234.123456', unitPrice: '0.123456', subjectBalanceAfter: null, walletBalanceAfter: null, requestedAt: '2026-09-08T10:00:00', settledAt: '2026-09-10T23:59:59' }
const ledger = () => ({ records: [{ ...row }], current: 1, size: 20, total: 1, totals: [{ book: 'PROJECT_ACCOUNT', operations: 1, debited: '999999999999.99', returned: '0.00', netDebited: '999999999999.99' }], checkedAt: '2026-09-11T00:00:00', timezone: 'Asia/Shanghai' })

test('historical missing owners remain explicit, not fabricated active accounts', () => {
  assert.equal(validOwner(owner), true)
  assert.equal(recordStatus('MISSING'), '账号已移除')
  assert.equal(validOwner({ ...owner, id: 0 }), false)
  assert.equal(validOwner({ ...owner, activeClients: -1 }), false)
  assert.equal(validOwner({ ...owner, accountDebited: 1.01 }), false)
})
test('ledger preserves exact decimal strings and explicit missing balances', () => {
  const value = ledger(); assert.equal(validLedger(value), true)
  assert.equal(value.records[0].units, '12345678901234.123456')
  assert.equal(value.records[0].walletBalanceAfter, null)
  value.records[0].amount = 999999999999.99; assert.equal(validLedger(value), false)
})
test('totals must describe all filtered rows and never silently merge account books', () => {
  const value = ledger(); value.total = 2; assert.equal(validLedger(value), false)
  value.totals.push({ book: 'CUSTOMER_CREDIT', operations: 1, debited: '1.00', returned: '2.00', netDebited: '-1.00' })
  value.records.push({ ...row, book: 'CUSTOMER_CREDIT' }); assert.equal(validLedger(value), true)
  value.totals[1].book = 'PROJECT_ACCOUNT'; assert.equal(validLedger(value), false)
})
test('same operation ID in different books is not deduplicated but repeated same-book rows fail', () => {
  const value = ledger(); value.records.push({ ...row }); value.total = 2; value.totals[0].operations = 2
  assert.equal(validLedger(value), false)
  value.records[1].book = 'CUSTOMER_CREDIT'; assert.equal(validLedger(value), false)
  value.totals[0].operations = 1
  value.totals.push({ book: 'CUSTOMER_CREDIT', operations: 1, debited: '999999999999.99', returned: '0.00', netDebited: '999999999999.99' })
  assert.equal(validLedger(value), true)
  value.records[1].direction = 'CREDIT'; assert.equal(validLedger(value), false)
})
test('scoped owner is fixed for administrator filters and never sent by self-view', () => {
  const form = { ownerId: '9', projectId: '2', book: 'ALL', direction: 'CREDIT', keyword: ' %_! ', fromDate: '', clientId: '' }
  assert.deepEqual(recordFilters(form, false), { projectId: '2', direction: 'CREDIT', keyword: '%_!' })
  assert.equal(recordFilters(form, true, 7).ownerId, '7')
  assert.equal(recordFilters(form, true).ownerId, '9')
})
test('filters reject reversed dates numeric overflow bad IDs and oversized input', () => {
  for (const value of [{ ownerId: '0' }, { projectId: '9223372036854775808' }, { clientId: '../other' }, { keyword: 'a\nb' }, { keyword: 'a'.repeat(101) }, { fromDate: '2026-09-11', throughDate: '2026-09-10' }]) assert.equal(validRecordFilters(value), false)
  assert.equal(validRecordFilters({ ownerId: '9223372036854775807', fromDate: '2026-09-10', throughDate: '2026-09-10' }), true)
})
test('account and customer projections require known type-specific fields and nullable observations', () => {
  const account = { id, projectId: 1, title: '账户', status: 'UNKNOWN', cachedBalance: null, balanceCheckedAt: null, unitPrice: '0.25', refundableUnits: '0', refundBudget: '0.00', debited: '0.00', returned: '0.00', unresolvedOperations: 1 }
  assert.equal(validSubject(account, false), true)
  assert.equal(validSubject(account, true), false)
  assert.equal(validSubject({ ...account, cachedBalance: '1e8' }, false), false)
  const customer = { ...account, status: 'SUSPENDED', label: '客户', balance: '10', createdAt: '2026-09-10T10:00:00' }
  assert.equal(validSubject(customer, true), true)
})
test('incomplete pages and owner details cannot be interpreted as empty successful records', () => {
  assert.equal(validRecordPage(null, validOwner), false)
  assert.equal(validRecordPage({ total: 1, current: 1, size: 51, records: [owner] }, validOwner), false)
  assert.equal(validRecordPage({ total: 1, current: 1, size: 20, records: [owner] }, validOwner), true)
  assert.equal(validOwnerDetail({ owner, usage: {}, accountFunding: {} }), false)
})

test('date and book filters match supported calendar and subject constraints', () => {
  for (const form of [{ fromDate: '2026-02-29' }, { throughDate: '2026-04-31' }, { fromDate: '0999-12-31' }, { throughDate: '9999-01-01' }, { fromDate: '2026-1-01' }, { fromDate: 'no-date' }, { book: 'OTHER' }, { direction: 'TOP_UP' }, { status: 'DELETED' }, { book: 'PROJECT_ACCOUNT', clientId: id }]) assert.equal(validRecordFilters(form), false)
  assert.equal(validRecordFilters({ fromDate: '2024-02-29', throughDate: '2024-03-01' }), true)
  assert.equal(validRecordFilters({ clientId: id, book: 'CUSTOMER_CREDIT' }), true)
})
test('pages and settled actions cannot contradict their declared book and totals', () => {
  const value = ledger()
  value.total = 0; assert.equal(validLedger(value), false)
  value.total = 1; value.current = 2; assert.equal(validLedger(value), false)
  value.current = 1; value.records[0].action = 'OPEN'; assert.equal(validLedger(value), false)
  value.records[0].action = 'PROVISION'; assert.equal(validLedger(value), true)
})

for (const separator of [' ', 'T']) {
  test(`ledger and subject timestamps accept ${JSON.stringify(separator)} without converting timezones`, () => {
    for (const fraction of ['', '.123', '.123456789']) {
      const timestamp = `2026-09-12${separator}18:19:25${fraction}`
      const value = ledger()
      value.checkedAt = value.records[0].requestedAt = value.records[0].settledAt = timestamp
      assert.equal(validLedger(value), true)
      assert.equal(validOwner({ ...owner, lastActivity: timestamp }), true)
      const account = { id, projectId: 1, title: '账户', status: 'ACTIVE', cachedBalance: '10',
        balanceCheckedAt: timestamp, unitPrice: '0.25', refundableUnits: '10', refundBudget: '2.50',
        debited: '2.50', returned: '0.00', unresolvedOperations: 0 }
      assert.equal(validSubject(account, false), true)
      assert.equal(validSubject({ ...account, label: '客户', balance: '10', createdAt: timestamp }, true), true)
      assert.equal(recordTime(timestamp), '2026-09-12 18:19:25')
    }
  })
}

test('every required ledger timestamp still rejects malformed or missing values', () => {
  for (const timestamp of [null, undefined, '', '2026-09-12', '2026-09-12 18:19',
    '2026-09-12 18:19:25Z', '2026-09-12T18:19:25+08:00', '2026-02-29 18:19:25',
    '2026-09-12 24:00:00']) {
    for (const field of ['requestedAt', 'settledAt', 'checkedAt']) {
      const value = ledger()
      if (field === 'checkedAt') value[field] = timestamp
      else value.records[0][field] = timestamp
      assert.equal(validLedger(value), false, `${field}: ${JSON.stringify(timestamp)}`)
    }
  }
})
