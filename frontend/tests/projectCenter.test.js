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

test("initial account funding is opt-in and rejects malformed or unbounded units", async () => {
  const { projectOpeningUnits } = await import("../src/utils/projectCenter.js");
  assert.equal(projectOpeningUnits(false, "garbage"), null);
  assert.equal(projectOpeningUnits(true, "8.123456"), "8.123456");
  for (const value of ["", "0", "-1", "100001", "1e2", "0.0000001", null])
    assert.equal(projectOpeningUnits(true, value), undefined);
});

test("opening quote requires exact arithmetic, identity, deadline and complete results", async () => {
  const { validProjectOpeningOperation, projectOpeningFunded } = await import("../src/utils/projectCenter.js");
  const q = { id: "7620966f-df58-4b6f-a035-000000000001", accountId: "7620966f-df58-4b6f-a035-000000000002",
    projectId: 7, projectTitle: "项目甲", action: "PROVISION", state: "READY", units: "0.05", unitPrice: "0.25",
    amount: "0.02", balanceAfter: null, expiresAt: "2099-01-01T10:00:00", warnings: [] };
  assert.equal(validProjectOpeningOperation(q, { projectId: 7, units: "0.050000" }), true);
  assert.equal(projectOpeningFunded(q), true);
  for (const changed of [{ amount: "0.01" }, { units: "8" }, { unitPrice: "1e3" }, { id: "oops" },
    { projectId: 8 }, { expiresAt: undefined }, { warnings: null }, { balanceAfter: "1" }, { state: "SUCCEEDED" }])
    assert.equal(validProjectOpeningOperation({ ...q, ...changed }, q), false);
  const done = { ...q, state: "SUCCEEDED", balanceAfter: "0.05" };
  assert.equal(validProjectOpeningOperation(done, q), true);
  assert.equal(validProjectOpeningOperation({ ...done, id: q.accountId }, q), false);
  const free = { ...q, units: "0", amount: "0.00" };
  assert.equal(validProjectOpeningOperation(free, { projectId: 7, units: null }), true);
  assert.equal(projectOpeningFunded(free), false);
});

test("funded opening reconciliation states refund and acceptance effects without another charge", async () => {
  const { projectResolutionEffect } = await import("../src/utils/projectCenter.js");
  const paid = { action: "PROVISION", units: "8", amount: "2.00" };
  assert.match(projectResolutionEffect(paid, "NOT_ACCEPTED"), /返还原充值预扣 ¥2.00/);
  assert.match(projectResolutionEffect(paid, "ACCEPTED"), /8 额度和 ¥2.00/);
  assert.match(projectResolutionEffect(paid, "ACCEPTED"), /不会再次扣款或开户/);
  assert.doesNotMatch(projectResolutionEffect({ ...paid, units: "0", amount: "0.00" }, "NOT_ACCEPTED"), /返还/);
  assert.match(projectResolutionEffect({ ...paid, action: "TOP_UP" }, "NOT_ACCEPTED"), /返还/);
});
