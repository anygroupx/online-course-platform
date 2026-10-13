import assert from 'node:assert/strict'
import test from 'node:test'
import { validLocalDateTime, localDateOnly, displayDateOnly } from '../src/utils/localDateTime.js'

test('local API timestamps support both separators and up to nanosecond precision', () => {
  for (const separator of [' ', 'T']) {
    for (const fraction of ['', ...Array.from({ length: 9 }, (_, i) => `.${'1'.repeat(i + 1)}`)]) {
      assert.equal(validLocalDateTime(`2026-09-12${separator}18:19:25${fraction}`), true)
    }
  }
})

test('local timestamps validate leap years without Date parsing or timezone conversion', () => {
  for (const value of ['2000-02-29 00:00:00', '2024-02-29T23:59:59', '2026-12-31 23:59:59']) {
    assert.equal(validLocalDateTime(value), true, value)
  }
  for (const value of ['1900-02-29 00:00:00', '2026-02-29T00:00:00', '2026-04-31 00:00:00',
    '2026-00-01 00:00:00', '2026-13-01 00:00:00', '2026-01-00 00:00:00', '2026-01-32 00:00:00',
    '2026-01-01 24:00:00', '2026-01-01 00:60:00', '2026-01-01 00:00:60']) {
    assert.equal(validLocalDateTime(value), false, value)
  }
})

test('unsupported formats, timezone suffixes and malformed values remain invalid', () => {
  for (const value of [null, undefined, 20260912, {}, [], '', '2026-09-12', '2026-9-12 18:19:25',
    '2026-09-12 18:19', '2026-09-12  18:19:25', '2026-09-12\t18:19:25', '2026-09-12\n18:19:25',
    '2026-09-12T18:19:25Z', '2026-09-12T18:19:25+08:00', '2026-09-12 18:19:25.',
    '2026-09-12 18:19:25.1234567890', ' 2026-09-12 18:19:25', '2026-09-12 18:19:25 ',
    '2026-09-12 18:19:25\n', '2026-09-12 18:19:25\r', '2026-09-12 18:19:25<script>']) {
    assert.equal(validLocalDateTime(value), false, JSON.stringify(value))
  }
})

test('localDateOnly keeps only the calendar date, dropping hours/minutes/seconds', () => {
  assert.equal(localDateOnly('2026-10-13 16:42:15'), '2026-10-13')
  assert.equal(localDateOnly('2026-10-13T16:42:15'), '2026-10-13')
  assert.equal(localDateOnly('2026-10-13 00:00:00'), '2026-10-13')
  assert.equal(localDateOnly('2026-10-13 23:59:59'), '2026-10-13')
  assert.equal(localDateOnly('2026-10-13 16:42:15.123'), '2026-10-13')
})

test('localDateOnly returns empty for invalid or missing timestamps instead of slicing garbage', () => {
  for (const value of [null, undefined, '', '2026-10-13', '2026-10-13 16:42', 'not-a-date',
    '2026-10-13T16:42:15Z', 20261013, {}, []]) {
    assert.equal(localDateOnly(value), '', JSON.stringify(value))
  }
})

// displayDateOnly 服务于上游供应商透传的执行记录时间，格式不保证与平台 LocalDateTime 一致。
test('displayDateOnly keeps the date for both platform and upstream timestamp shapes', () => {
  for (const value of ['2026-10-13 16:42:15', '2026-10-13T16:42:15', '2026-10-13 16:42:15.123',
    '2026-10-13T16:42:15Z', '2026-10-13T16:42:15+08:00', '2026-10-13T16:42:15.123456Z', '2026-10-13',
    '2026-10-13T16:42:15+0800']) {
    assert.equal(displayDateOnly(value), '2026-10-13', value)
  }
})

test('displayDateOnly never blanks a value it cannot parse, unlike localDateOnly', () => {
  // 上游可能返回非标准形态；此时必须原样显示，而不是显示成空。
  for (const value of ['13/09/2026 08:00', 'unknown', '待定', '20260913']) {
    assert.equal(displayDateOnly(value), value)
    assert.equal(localDateOnly(value), '', `localDateOnly 应拒绝 ${value}`)
  }
})

test('displayDateOnly rejects impossible calendar dates and returns them unchanged', () => {
  for (const value of ['2026-02-30 08:00:00', '2026-13-01 08:00:00', '2026-00-10 08:00:00']) {
    assert.equal(displayDateOnly(value), value)
  }
  assert.equal(displayDateOnly('2028-02-29 08:00:00'), '2028-02-29')  // 2028 闰年 → 有效
  assert.equal(displayDateOnly('2026-02-29 08:00:00'), '2026-02-29 08:00:00')  // 2026 平年 → 原样
})

test('displayDateOnly returns empty for non-string values', () => {
  for (const value of [null, undefined, 20261013, {}, [], true]) {
    assert.equal(displayDateOnly(value), '', JSON.stringify(value))
  }
})
