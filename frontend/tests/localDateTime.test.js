import assert from 'node:assert/strict'
import test from 'node:test'
import { validLocalDateTime, localDateOnly } from '../src/utils/localDateTime.js'

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
