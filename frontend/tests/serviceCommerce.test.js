import {
  isHeishaFaceService,
  usesServiceAccountSession,
  validFaceLaunchTicket,
  serviceAccountExpired,
} from "../src/utils/serviceCommerce.js";
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  buildTaskTimes,
  moneyText,
  knownOutcome,
  stateName,
  serviceFormFields,
  editableWuxinPlan,
  nativeProductSupported,
  maxRefundableUnits,
  quoteChargeDetails,
} from "../src/utils/serviceCommerce.js";
test("schedule matches chosen weekdays without timezone shifts", () => {
  assert.deepEqual(buildTaskTimes("2026-09-07", "08:00", 3, [1, 3, 5]), [
    "2026-09-07 08:00:00",
    "2026-09-09 08:00:00",
    "2026-09-11 08:00:00",
  ]);
});
test("invalid or unsatisfiable schedules fail instead of silently changing quantity", () => {
  for (const args of [
    ["2026-02-30", "08:00", 1],
    ["2026-09-07", "24:00", 1],
    ["2026-09-07", "08:00", 0],
    ["2026-09-07", "08:00", 1, []],
    ["2026-09-07", "08:00", 365, [1]],
  ])
    assert.deepEqual(buildTaskTimes(...args), []);
});
test("decimal prices remain exact strings", () => {
  assert.equal(moneyText("0.123456"), "0.123456");
  assert.equal(moneyText("9999999.99"), "9999999.99");
  for (const v of [null, "1e9", "NaN", "<script>", "-0.10"])
    assert.equal(moneyText(v), "—");
});
test("ambiguous upstream results are never shown as a final failure or success", () => {
  assert.equal(knownOutcome("UNKNOWN"), false);
  assert.equal(knownOutcome("DISPATCHING"), false);
  assert.equal(knownOutcome("SUCCEEDED"), true);
  assert.equal(stateName("unknown-secret"), "待核对");
});

test("switching suppliers cannot forward stale passwords or arbitrary parameters", () => {
  assert.deepEqual(
    serviceFormFields("wuxin", {
      authCode: "authorized",
      password: "hidden",
      login_key: "forged",
      pace: "7",
    }),
    { authCode: "authorized", pace: "7" },
  );
  assert.deepEqual(
    serviceFormFields("jiguang", {
      studentName: "Name",
      authCode: "private",
      password: "private",
    }),
    { studentName: "Name" },
  );
  assert.deepEqual(serviceFormFields("unknown", { password: "private" }), {});
});

test("editing Wuxin plans never silently replaces the existing schedule with checkout defaults", () => {
  const source = {
    runPlanCode: "plan-2",
    fenceCode: "fence-2",
    runType: "2",
    runTime: "07:30",
    endTime: "09:00",
    pace: "6.5",
    weekdays: "1,3,5",
    distance: "2.4",
    authCode: "private",
    password: "private",
  };
  const fields = editableWuxinPlan(source, "2");
  assert.equal(fields.runTime, "07:30");
  assert.equal(fields.pace, "6.5");
  assert.equal(fields.distance, "2.4");
  assert.equal(fields.authCode, undefined);
  assert.equal(fields.password, undefined);
  const missing = editableWuxinPlan({}, "2");
  assert.equal(missing.runTime, "");
  assert.equal(missing.weekdays, "");
  assert.equal(missing.pace, "");
  assert.equal(missing.distance, "2");
});

test("native product selection follows the actual PHP product allowlist, not arbitrary catalog rows", () => {
  assert.equal(nativeProductSupported("jiguang", "default", "1"), true);
  assert.equal(nativeProductSupported("jiguang", "default", "3"), false);
  assert.equal(nativeProductSupported("heisha", "default", "3"), true);
  assert.equal(nativeProductSupported("wuxin", "sdxy", "sdxy"), true);
  assert.equal(nativeProductSupported("flash", "ydsjxy", "ydsjxy"), true);
  assert.equal(nativeProductSupported("flash", "sdxy", "xbd"), false);
});
test("a missing completion count is only a refund ceiling, never a claimed remaining count", () => {
  assert.equal(maxRefundableUnits({ quantity: 10, completed: 4 }), 6);
  assert.equal(maxRefundableUnits({ quantity: 10, completed: null }), 10);
});

test("account authorization expiry is interpreted in Beijing time and respects precise deadlines", () => {
  assert.equal(
    serviceAccountExpired(
      { expiresAt: "2026-09-08T08:10:00" },
      Date.parse("2026-09-08T00:09:59Z"),
    ),
    false,
  );
  assert.equal(
    serviceAccountExpired(
      { expiresAt: "2026-09-08T08:10:00" },
      Date.parse("2026-09-08T00:10:00Z"),
    ),
    true,
  );
  assert.equal(
    serviceAccountExpired(
      { expiresAt: "2026-09-08T08:10:00.123456" },
      Date.parse("2026-09-08T00:10:00.122Z"),
    ),
    false,
  );
});
test("missing malformed or expired authorization deadlines disable checkout", () => {
  for (const value of [
    undefined,
    "",
    "not-a-date",
    "https://untrusted.example",
    "2026-09-08T25:00:00",
  ])
    assert.equal(serviceAccountExpired({ expiresAt: value }), true);
  assert.equal(
    serviceAccountExpired(
      { expiresAt: "2026-09-08T00:10:00Z" },
      Date.parse("2026-09-08T00:10:01Z"),
    ),
    true,
  );
});

test("only Heisha face products require hosted authorization; other services stay unchanged", () => {
  for (const id of ["3", "4"]) {
    const product = {
      providerType: "heisha",
      project: "default",
      remoteProductId: id,
    };
    assert.equal(isHeishaFaceService(product), true);
    assert.equal(usesServiceAccountSession(product), true);
    assert.equal(nativeProductSupported("heisha", "default", id), true);
  }
  for (const product of [
    null,
    { providerType: "heisha", project: "other", remoteProductId: "3" },
    { providerType: "heisha", project: "default", remoteProductId: "1" },
    { providerType: "jiguang", project: "default", remoteProductId: "3" },
  ])
    assert.equal(isHeishaFaceService(product), false);
  assert.equal(usesServiceAccountSession({ providerType: "flash" }), true);
  assert.equal(nativeProductSupported("jiguang", "default", "3"), false);
  assert.equal(nativeProductSupported("heisha", "default", "5"), false);
});
test("face navigation only accepts an unexpired one-use local ticket bound to the session", () => {
  const now = Date.parse("2026-09-08T01:00:00+08:00");
  const ticket = {
    sessionId: "bound-session",
    ticket: "a".repeat(64),
    expiresAt: "2026-09-08T01:01:00",
  };
  assert.equal(validFaceLaunchTicket(ticket, "bound-session", now), true);
  for (const changed of [
    { ticket: "https://untrusted.example" },
    { ticket: "a".repeat(65) },
    { ticket: "" },
    { sessionId: "other" },
    { expiresAt: "2026-09-08T01:00:00" },
  ])
    assert.equal(
      validFaceLaunchTicket({ ...ticket, ...changed }, "bound-session", now),
      false,
    );
});

const exactQuote = Object.freeze({ action: "CREATE", quantity: 3, quantityUnit: "次", unitCharge: "0.05499989", amount: "0.16" });
test("quoted unit charge preserves eight decimals without recomputing the final cents", () => {
  assert.deepEqual(quoteChargeDetails(exactQuote), { unitCharge: "0.05499989", quantity: 3, unit: "次", refund: false });
  assert.equal(exactQuote.amount, "0.16");
  assert.equal(quoteChargeDetails({ ...exactQuote, unitCharge: "9999999999.99999999" }).unitCharge, "9999999999.99999999");
});
test("legacy quotes keep their recorded price and service days have a distinct unit", () => {
  assert.equal(quoteChargeDetails({ ...exactQuote, unitCharge: "0.055000", amount: "0.17" }).unitCharge, "0.055000");
  assert.equal(quoteChargeDetails({ ...exactQuote, action: "EDIT_SCHEDULE", quantityUnit: "天" }).unit, "天");
});
test("refund details describe a cap instead of inventing an uncapped total", () => {
  for (const action of ["REFUND", "SETTLE_REFUND"]) {
    const value = quoteChargeDetails({ ...exactQuote, action, quantity: 6, amount: "0.32" });
    assert.equal(value.refund, true);
    assert.equal(value.quantity, 6);
    assert.equal(value.amount, undefined);
  }
});
test("charge details do not accept floating numbers, exponent notation or oversized values", () => {
  for (const unitCharge of [0.05499989, undefined, null, "1e-8", "NaN", "Infinity", "-0.1", "0", "0.00000000", "0.054999891", "10000000000", "<script>", " 0.01"]) {
    assert.equal(quoteChargeDetails({ ...exactQuote, unitCharge }), null, String(unitCharge));
  }
});
test("nonbilling and malformed quotes cannot reuse an irrelevant stored price", () => {
  for (const action of ["PAUSE", "RESUME", "DELAY", "CHANGE_TIME", "REPORT", "EDIT_PLAN"]) {
    assert.equal(quoteChargeDetails({ ...exactQuote, action }), null);
  }
  for (const quantity of [0, -1, "3", 3.5, 10000, NaN]) assert.equal(quoteChargeDetails({ ...exactQuote, quantity }), null);
  assert.equal(quoteChargeDetails({ ...exactQuote, quantityUnit: "unknown" }), null);
  assert.equal(quoteChargeDetails(null), null);
  assert.equal(quoteChargeDetails({ ...exactQuote, unitCharge: undefined }), null);
});
test("total distance keeps its existing plan summary instead of a misleading per-run breakdown", () => {
  assert.equal(quoteChargeDetails({ ...exactQuote, quantity: 1, quantityUnit: "单", distancePlan: { totalDistance: "120.50" } }), null);
});
