import test from 'node:test';
import assert from 'node:assert/strict';
import { fulfillmentActions, fulfillmentCommand, isSelfOperatedHeisha, fulfillmentVerificationActions, canSupplementFulfillment } from '../src/utils/serviceFulfillment.js';
import { orderStateName, serviceActionName, requiresSelfOperatedFaceMaterial, usesServiceAccountSession } from '../src/utils/serviceCommerce.js';
import { emptyOrderSearch, serviceOrderSearchParams } from '../src/composables/useServiceOrderSearch.js';
const order = { providerType: 'heisha', fulfillmentMode: 'SELF_OPERATED', status: 'PENDING', verificationStatus: 'PENDING', version: 2, completed: 0, quantity: 10 };
test('only self-operated heisha exposes legal lifecycle actions', () => {
  assert.deepEqual(fulfillmentActions(order), ['ATTENTION', 'CANCEL_REFUND']);
  assert.deepEqual(fulfillmentActions({ ...order, verificationStatus: 'VERIFIED' }), ['START', 'ATTENTION', 'CANCEL_REFUND']);
  assert.deepEqual(fulfillmentActions({ ...order, verificationStatus: 'NEEDS_INFO' }), ['ATTENTION', 'CANCEL_REFUND']);
  assert.deepEqual(fulfillmentActions({ ...order, completed: 1 }), ['ATTENTION']);
  assert.deepEqual(fulfillmentActions({ ...order, status: 'ACTIVE' }), ['PROGRESS', 'COMPLETE', 'ATTENTION']);
  assert.deepEqual(fulfillmentActions({ ...order, status: 'ATTENTION' }), ['RESUME']);
  assert.deepEqual(fulfillmentActions({ ...order, status: 'COMPLETED' }), []);
  assert.deepEqual(fulfillmentActions({ ...order, fulfillmentMode: 'UPSTREAM' }), []);
  assert.equal(isSelfOperatedHeisha({ ...order, providerType: 'flash' }), false);
});
test('commands freeze order version and validate progress and notes', () => {
  const verified = { ...order, verificationStatus: 'VERIFIED' };
  assert.deepEqual(fulfillmentCommand(verified, 'START'), { action: 'START', orderVersion: 2, note: null });
  assert.throws(() => fulfillmentCommand(order, 'COMPLETE'));
  assert.throws(() => fulfillmentCommand(order, 'ATTENTION', null, ' '));
  assert.throws(() => fulfillmentCommand(order, 'CANCEL_REFUND', null, ' '));
  assert.equal(fulfillmentCommand(order, 'CANCEL_REFUND', null, '用户申请取消').note, '用户申请取消');
  const active = { ...order, status: 'ACTIVE' };
  for (const n of [-1, 0, 11, 2.5]) assert.throws(() => fulfillmentCommand(active, 'PROGRESS', n));
  assert.equal(fulfillmentCommand(active, 'PROGRESS', 3).completed, 3);
});
test('local states and refresh labels preserve upstream behavior', () => {
  assert.equal(orderStateName(order), '待处理');
  assert.equal(serviceActionName('SYNC', order), '刷新状态');
  assert.notEqual(serviceActionName('SYNC', { ...order, fulfillmentMode: 'UPSTREAM' }), '刷新状态');
});
test('fulfillment filter is administrator-only and validates mode', () => {
  const draft = { ...emptyOrderSearch(), fulfillmentMode: 'SELF_OPERATED' };
  assert.deepEqual(serviceOrderSearchParams(draft, true), { fulfillmentMode: 'SELF_OPERATED' });
  assert.throws(() => serviceOrderSearchParams(draft, false));
  assert.throws(() => serviceOrderSearchParams({ ...draft, fulfillmentMode: 'other' }, true));
});

test('all local SKUs bypass official sessions and only 3/4 require separate face material', () => {
  for (const sku of ['1', '2', '3', '4']) {
    const product = { ...order, project: 'default', remoteProductId: sku };
    assert.equal(requiresSelfOperatedFaceMaterial(product), ['3', '4'].includes(sku));
    assert.equal(usesServiceAccountSession(product), false);
    assert.equal(usesServiceAccountSession({ ...product, fulfillmentMode: 'UPSTREAM' }), ['3', '4'].includes(sku));
  }
});
test('qualification transitions and supplementation depend on the immutable local order state', () => {
  assert.deepEqual(fulfillmentVerificationActions(order), ['VERIFY', 'NEEDS_INFO', 'REJECT']);
  assert.deepEqual(fulfillmentVerificationActions({ ...order, verificationStatus: 'NEEDS_INFO' }), ['VERIFY', 'REJECT']);
  for (const status of ['VERIFIED', 'REJECTED']) assert.deepEqual(fulfillmentVerificationActions({ ...order, verificationStatus: status }), []);
  const supplementable = { ...order, verificationStatus: 'NEEDS_INFO' };
  assert.equal(canSupplementFulfillment(supplementable), true);
  for (const override of [{ status: 'ACTIVE' }, { fulfillmentMode: 'UPSTREAM' }, { pendingOperationId: 'pending' }]) {
    assert.equal(canSupplementFulfillment({ ...supplementable, ...override }), false);
    assert.deepEqual(fulfillmentVerificationActions({ ...order, ...override }), []);
  }
  assert.equal(serviceActionName('LOCAL_MATERIAL_UPDATED', order), '补充资料已更新');
  assert.equal(serviceActionName('LOCAL_REFUND', order), '取消并退款');
});
