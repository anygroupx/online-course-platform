import test from 'node:test';
import assert from 'node:assert/strict';
import { formatAmountChange, formatBalanceAfter, formatLogDateTime, getAmountChangeClass } from '../src/utils/logDisplay.js';
test('zero balances stay visible and absent/invalid amounts do not become NaN', () => {
  assert.equal(formatBalanceAfter(0), '¥0.00');
  assert.equal(formatAmountChange('12.5'), '+12.50');
  assert.equal(formatAmountChange(-3), '-3.00');
  for (const value of [null, undefined, '', 'invalid']) {
    assert.equal(formatBalanceAfter(value), '-');
    assert.equal(formatAmountChange(value), '-');
    assert.equal(getAmountChangeClass(value), 'is-neutral');
  }
});
test('date parsing handles DB timestamps and missing/invalid data', () => {
  assert.equal(formatLogDateTime(null), '-');
  assert.equal(formatLogDateTime('not-a-date'), '-');
  assert.match(formatLogDateTime('2026-09-06 12:00:00'), /2026/);
});
