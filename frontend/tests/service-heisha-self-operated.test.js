import test from 'node:test';
import assert from 'node:assert/strict';
import { fulfillmentActions, fulfillmentCommand, isSelfOperatedHeisha } from '../src/utils/serviceFulfillment.js';
import { orderStateName, serviceActionName } from '../src/utils/serviceCommerce.js';
import { emptyOrderSearch, serviceOrderSearchParams } from '../src/composables/useServiceOrderSearch.js';
const order = { providerType: 'heisha', fulfillmentMode: 'SELF_OPERATED', status: 'PENDING', version: 2, completed: 2, quantity: 10 };
test('only self-operated heisha exposes legal lifecycle actions', () => {
  assert.deepEqual(fulfillmentActions(order), ['START', 'ATTENTION']);
  assert.deepEqual(fulfillmentActions({ ...order, status: 'ACTIVE' }), ['PROGRESS', 'COMPLETE', 'ATTENTION']);
  assert.deepEqual(fulfillmentActions({ ...order, status: 'ATTENTION' }), ['RESUME']);
  assert.deepEqual(fulfillmentActions({ ...order, status: 'COMPLETED' }), []);
  assert.deepEqual(fulfillmentActions({ ...order, fulfillmentMode: 'UPSTREAM' }), []);
  assert.equal(isSelfOperatedHeisha({ ...order, providerType: 'flash' }), false);
});
test('commands freeze order version and validate progress and notes', () => {
  assert.deepEqual(fulfillmentCommand(order, 'START'), { action: 'START', orderVersion: 2, note: null });
  assert.throws(() => fulfillmentCommand(order, 'COMPLETE'));
  assert.throws(() => fulfillmentCommand(order, 'ATTENTION', null, ' '));
  const active = { ...order, status: 'ACTIVE' };
  for (const n of [-1, 1, 2, 11, 2.5]) assert.throws(() => fulfillmentCommand(active, 'PROGRESS', n));
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
