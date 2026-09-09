import test from 'node:test'
import assert from 'node:assert/strict'
import { clientTicketOpen, ticketAmountValid, ticketRequestIdValid } from '../src/utils/projectClientTickets.js'
test('downstream ticket reference amounts are bounded exact decimal strings, not exponents or floats', () => {
  for (const v of ['0', '0.01', '12.50', '99999999.99']) assert.equal(ticketAmountValid(v), true)
  for (const v of ['', '-1', '100000000', '0.001', '1e3', '.5', '01', '1.', null]) assert.equal(ticketAmountValid(v), false)
})
test('terminal downstream tickets cannot reply and receipt lookup requires a complete identifier', () => {
  assert.equal(clientTicketOpen({ status: 'OPEN' }), true)
  assert.equal(clientTicketOpen({ status: 'IN_PROGRESS' }), true)
  for (const status of ['RESOLVED', 'CLOSED', 'UNKNOWN']) assert.equal(clientTicketOpen({ status }), false)
  assert.equal(ticketRequestIdValid('b038e810-6a0d-461c-8dc6-a2aa1aa061bd'), true)
  assert.equal(ticketRequestIdValid('1-1-1-1-1'), false)
  assert.equal(ticketRequestIdValid('np' + 'c_' + 'a'.repeat(64)), false)
})
