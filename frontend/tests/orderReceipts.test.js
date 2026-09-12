import test from 'node:test'
import assert from 'node:assert/strict'
import { validReceiptForm, validReceiptView, receiptCanConfirm, receiptRequestId, executionReceiptId, receiptTime, validReceiptCandidates } from '../src/utils/orderReceipts.js'
const id = '11111111-1111-4111-8111-111111111111'
const form = { receiptId: 'receipt-9', evidence: '已核实原始回执及原执行账户的完整订单归属', ownershipConfirmed: true }
const ready = { id, orderId: 1, orderNo: 'ORD-1', courseName: '<img src=x>', receiptId: 'receipt-9', state: 'READY', expiresAt: '2026-09-10T10:00:00', appliedAt: null }
test('receipt input needs a bounded execution ID, evidence and separate ownership consent', () => {
  assert.equal(validReceiptForm(form), true)
  for (const change of [{ receiptId: '../1' }, { receiptId: 'a'.repeat(51) }, { evidence: 'too short' }, { evidence: ' '.repeat(1001) }, { evidence: form.evidence + '\n' }, { ownershipConfirmed: false }, { ownershipConfirmed: 1 }]) assert.equal(validReceiptForm({ ...form, ...change }), false)
})
test('receipt IDs cannot be mistaken for URL credentials or arbitrary request identifiers', () => {
  assert.equal(receiptRequestId(id), true)
  for (const invalid of ['..', 'f'.repeat(36), id.replace(/-/g, ''), null]) assert.equal(receiptRequestId(invalid), false)
  assert.equal(executionReceiptId('receipt-9_abc'), true)
  assert.equal(executionReceiptId('https://receipt.invalid/id'), false)
})
test('receipt views bind exact order and request while never inferring incomplete success', () => {
  assert.equal(validReceiptView(ready, 1, id), true)
  assert.equal(validReceiptView(ready, 2, id), false)
  assert.equal(validReceiptView(ready, 1, 'different'), false)
  for (const patch of [{ state: 'SUCCESS' }, { expiresAt: null }, { orderId: '1' }, { courseName: null }, { state: 'APPLIED' }]) assert.equal(validReceiptView({ ...ready, ...patch }, 1, id), false)
  assert.equal(validReceiptView({ ...ready, state: 'APPLIED', appliedAt: '2026-09-10T09:59:00' }, 1), true)
})
test('receipt confirmation uses Beijing expiry and cannot confirm uncertain or final states', () => {
  const now = Date.parse('2026-09-10T01:59:59Z')
  assert.equal(receiptCanConfirm(ready, now), true)
  assert.equal(receiptCanConfirm(ready, now + 1000), false)
  for (const state of ['READING', 'READ_FAILED', 'APPLIED', 'EXPIRED', 'CONFLICT', 'INTERRUPTED']) assert.equal(receiptCanConfirm({ ...ready, state }, now), false)
  assert.equal(receiptCanConfirm(null, now), false)
})
test('malformed calendar dates cannot masquerade as a future preview', () => {
  for (const expiresAt of ['2026-02-30T10:00:00', '2026-13-01T10:00:00', '2026-01-01T25:00:00', '0000-01-01T10:00:00']) assert.equal(validReceiptView({ ...ready, expiresAt }, 1), false)
  assert.equal(receiptTime('2026-09-10T10:00:00.123456'), '2026-09-10 10:00:00')
})

const candidates = { orderId: 1, receiptIds: ['receipt-9', 'receipt-10'], checkedAt: '2026-09-11T12:00:00', scope: 'CURRENT_RESPONSE' }
test('receipt candidates are bounded, order-scoped and do not imply complete discovery', () => {
  assert.equal(validReceiptCandidates(candidates, 1), true)
  assert.equal(validReceiptCandidates({ ...candidates, receiptIds: [] }, 1), true)
  assert.equal(validReceiptCandidates({ ...candidates, receiptIds: Array.from({ length: 20 }, (_, i) => `receipt-${i}`) }, 1), true)
  assert.equal(validReceiptCandidates(candidates, 2), false)
  for (const change of [{ orderId: '1' }, { orderId: 0 }, { scope: 'ALL' }, { scope: null }, { receiptIds: null },
    { receiptIds: ['receipt-9', 'receipt-9'] }, { receiptIds: ['../id'] }, { receiptIds: [1] }, { receiptIds: ['a'.repeat(51)] },
    { receiptIds: Array.from({ length: 21 }, (_, i) => `receipt-${i}`) }, { checkedAt: null }, { checkedAt: '2026-02-30T12:00:00' }, { checkedAt: '2026-09-11T24:00:00' }, { checkedAt: '2026-09-11T12:60:00' }]) {
    assert.equal(validReceiptCandidates({ ...candidates, ...change }, 1), false, JSON.stringify(change))
  }
})
test('incomplete or expanded candidate DTOs never render as verified discovery', () => {
  for (const key of Object.keys(candidates)) {
    const missing = { ...candidates }; delete missing[key]
    assert.equal(validReceiptCandidates(missing, 1), false, key)
  }
  for (const value of [null, [], {}, { ...candidates, studentPassword: 'must-not-display' }, { ...candidates, complete: true }]) {
    assert.equal(validReceiptCandidates(value, 1), false)
  }
})
