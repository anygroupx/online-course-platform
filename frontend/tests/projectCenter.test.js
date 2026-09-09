import { test } from "node:test";
import assert from "node:assert/strict";
import {
  projectAmount,
  projectActionAllowed,
  projectQuoteReady,
  estimateProjectTransfer,
} from "../src/utils/projectCenter.js";
test("project estimates use exact decimals and asymmetric cent rounding, never binary floats", () => {
  assert.equal(estimateProjectTransfer("8", "0.25"), "2.00");
  assert.equal(estimateProjectTransfer("0.05", "0.25"), "0.02");
  assert.equal(estimateProjectTransfer("0.05", "0.25", true), "0.01");
  assert.equal(estimateProjectTransfer("0.000001", "0.123456"), "0.01");
  assert.equal(estimateProjectTransfer("0.000001", "0.123456", true), "0.00");
  assert.equal(estimateProjectTransfer("100000", "9999"), "999900000.00");
});
test("invalid project amounts never become zero prices", () => {
  for (const amount of ["", "NaN", "-1", "1e2", "0.0000001", "100001", null])
    assert.equal(estimateProjectTransfer(amount, "1"), "—");
  for (const amount of [null, "NaN", "1e2", "<img>", "-1"])
    assert.equal(projectAmount(amount), "—");
  assert.equal(projectAmount("0.000001"), "0.000001");
});
test("funding requires active account and availability; withdrawal cannot use unfunded gifts", () => {
  const p = { available: true, account: null };
  assert.equal(projectActionAllowed(p, "PROVISION"), true);
  assert.equal(projectActionAllowed(p, "TOP_UP"), false);
  p.account = { state: "ACTIVE", refundableUnits: "4", refundBudget: "1" };
  assert.equal(projectActionAllowed(p, "TOP_UP"), true);
  assert.equal(projectActionAllowed(p, "WITHDRAW"), true);
  p.available = false;
  assert.equal(projectActionAllowed(p, "TOP_UP"), false);
  assert.equal(projectActionAllowed(p, "WITHDRAW"), true);
  p.account.refundableUnits = "0";
  assert.equal(projectActionAllowed(p, "WITHDRAW"), false);
  for (const state of ["UNKNOWN", "BUSY", "DISABLED"]) {
    p.available = true;
    p.account = { state, refundableUnits: "10", refundBudget: "10" };
    assert.equal(projectActionAllowed(p, "TOP_UP"), false);
    assert.equal(projectActionAllowed(p, "WITHDRAW"), false);
  }
  assert.equal(projectActionAllowed(p, "UNRECOGNIZED"), false);
});
test("only a current READY quote can be confirmed and naive timestamps mean Beijing", () => {
  const now = Date.parse("2026-09-08T10:00:00+08:00");
  const quote = {
    action: "TOP_UP",
    state: "READY",
    expiresAt: "2026-09-08T10:05:00",
  };
  assert.equal(projectQuoteReady(quote, now), true);
  for (const change of [
    { state: "UNKNOWN" },
    { state: "DISPATCHING" },
    { state: "NETWORK_UNKNOWN" },
    { expiresAt: "2026-09-08T09:59:00" },
    { action: "other" },
  ])
    assert.equal(projectQuoteReady({ ...quote, ...change }, now), false);
});
