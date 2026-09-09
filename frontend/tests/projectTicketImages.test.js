import test from 'node:test'
import assert from 'node:assert/strict'
import { ticketImageFileError, TICKET_IMAGE_LIMIT } from '../src/utils/projectTicketImages.js'
test('ticket image chooser rejects links/vectors, empty or oversized originals and forged MIME', () => {
  const png = new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10])
  assert.equal(ticketImageFileError({ type: 'image/png', size: 100 }, png), '')
  assert.equal(ticketImageFileError({ type: 'image/jpeg', size: 100 }, new Uint8Array([255, 216, 255])), '')
  for (const type of ['image/svg+xml', 'image/gif', 'text/html', 'image/webp', '']) assert.ok(ticketImageFileError({ type, size: 100 }))
  for (const size of [0, TICKET_IMAGE_LIMIT + 1]) assert.ok(ticketImageFileError({ type: 'image/png', size }))
  assert.ok(ticketImageFileError({ type: 'image/jpeg', size: 100 }, png))
  assert.ok(ticketImageFileError({ type: 'image/png', size: 100 }, new Uint8Array([1, 2])))
})
