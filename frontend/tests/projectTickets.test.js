import test from "node:test";
import assert from "node:assert/strict";
import {
  ticketState,
  canReplyToTicket,
  canReviewTicket,
  ticketOperationReady,
  canResolveTicketAccepted,
} from "../src/utils/projectTickets.js";
test("ticket states never confuse unknown writes with successful remote replies", () => {
  assert.equal(
    ticketState({ state: "UNKNOWN", status: "resolved" }),
    "操作结果待核实",
  );
  assert.equal(ticketState({ state: "DRAFT", status: null }), "草稿待确认");
  assert.equal(
    ticketState({ state: "ACTIVE", status: "processing" }),
    "处理中",
  );
  assert.equal(
    canReplyToTicket({ state: "UNKNOWN", pendingOperationId: "op" }),
    false,
  );
});
test("only unblocked owned active tickets can receive a new reply or first compensation review", () => {
  const ticket = {
    state: "ACTIVE",
    type: "compensation",
    pendingOperationId: null,
    reviewResult: "",
  };
  assert.equal(canReplyToTicket(ticket), true);
  assert.equal(canReplyToTicket({ ...ticket, status: "resolved" }), false);
  assert.equal(canReviewTicket({ ...ticket, status: "closed" }), false);
  assert.equal(canReviewTicket(ticket), true);
  assert.equal(canReviewTicket({ ...ticket, reviewResult: "approved" }), false);
  assert.equal(canReviewTicket({ ...ticket, type: "bug" }), false);
  assert.equal(canReviewTicket({ ...ticket, pendingOperationId: "op" }), false);
});
test("customer cannot confirm admin reviews and expired or unknown requests cannot be replayed", () => {
  const op = {
    action: "SUBMIT",
    state: "READY",
    expiresAt: "2099-01-01T00:00:00+08:00",
  };
  assert.equal(ticketOperationReady(op, false), true);
  assert.equal(ticketOperationReady(op, true), false);
  assert.equal(ticketOperationReady({ ...op, action: "REVIEW" }, false), false);
  assert.equal(ticketOperationReady({ ...op, action: "REVIEW" }, true), true);
  assert.equal(ticketOperationReady({ ...op, state: "UNKNOWN" }, false), false);
  assert.equal(
    ticketOperationReady({ ...op, expiresAt: "2020-01-01T00:00:00Z" }, false),
    false,
  );
});
test("unverifiable unknown submits cannot be manually claimed by an arbitrary upstream ticket id", () => {
  assert.equal(
    canResolveTicketAccepted({ action: "SUBMIT", state: "UNKNOWN" }),
    false,
  );
  assert.equal(
    canResolveTicketAccepted({ action: "REPLY", state: "UNKNOWN" }),
    true,
  );
  assert.equal(
    canResolveTicketAccepted({ action: "REVIEW", state: "UNKNOWN" }),
    true,
  );
  assert.equal(
    canResolveTicketAccepted({ action: "REPLY", state: "READY" }),
    false,
  );
});
