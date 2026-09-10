import test from "node:test";
import assert from "node:assert/strict";
import { isTotalDistanceService, totalDistanceValid, distancePlanError, distanceOrderValid, distanceWeekdaysText } from "../src/utils/totalDistanceServices.js";
import { serviceFormFields, nativeProductSupported, maxRefundableUnits, stateName } from "../src/utils/serviceCommerce.js";
import { canReadIntegration, isReadOnlyProviderType } from "../src/utils/pluginIntegrations.js";
const fields = () => ({ account: "13800138000", password: " demo-password ", schoolName: "", startTime: "09:00", endTime: "21:00", weekdays: "1,3,5" });

test("total-distance service is scoped to the plaintext xbd family and two neutral type codes", () => {
  assert.equal(isTotalDistanceService({ providerType: "ssbenz_xbd" }), true);
  assert.equal(isTotalDistanceService("ssbenz_xbd"), true);
  for (const value of [null, "27", "yongye", "ssbenz", "ssbenz_ydsj", "flash"]) assert.equal(isTotalDistanceService(value), false);
  for (const id of ["0", "1"]) assert.equal(nativeProductSupported("ssbenz_xbd", "xbd", id), true);
  for (const [project, id] of [["ydsj", "0"], ["default", "1"], ["xbd", "2"], ["xbd", "01"], ["xbd", 1]])
    assert.equal(nativeProductSupported("ssbenz_xbd", project, id), false);
});
test("total kilometres preserve exact hundredths beyond fifty and reject ambiguous input", () => {
  for (const value of ["0.01", "50.01", "120.50", "999999.99", "1"]) assert.equal(totalDistanceValid(value), true, value);
  for (const value of [null, "", "0", "-1", "1000000", "120.501", "1e2", " 2", ".50", "1.", "NaN", "1,20", "0x10", "999999.999"])
    assert.equal(totalDistanceValid(value), false, String(value));
});
test("time windows are explicit and never repaired to default hours", () => {
  const values = fields();
  assert.equal(distancePlanError("120.50", values), "");
  for (const startTime of ["05:59", "23:00", "9:00", "09:00:00", "21:00", "21:01"])
    assert.ok(distancePlanError("120.50", { ...values, startTime }));
  assert.equal(distancePlanError("120.50", { ...values, startTime: "06:00", endTime: "22:59" }), "");
});
test("week selection is nonempty distinct bounded and only formats verified numeric days", () => {
  for (const weekdays of ["", "0", "8", "1,1", "1,2,3,4,5,6,7,1", "135", "1, 3", "1.0"])
    assert.ok(distancePlanError("120.50", { ...fields(), weekdays }));
  const days = [5, 1, 3];
  assert.equal(distanceWeekdaysText(days), "周一、周三、周五");
  assert.deepEqual(days, [5, 1, 3]);
  for (const list of [null, [], [1, 1], [0], [8], ["1"], [1.1]]) assert.equal(distanceWeekdaysText(list), "星期待核对");
});
test("quotes require account and plan inputs but never fabricate an account preflight", () => {
  assert.equal(distanceOrderValid("120.50", fields()), true);
  for (const update of [{ account: "" }, { password: " " }, { account: "x\n" }, { password: "x\u0085" }, { schoolName: "学".repeat(121) }, { account: "1".repeat(101) }])
    assert.equal(distanceOrderValid("120.50", { ...fields(), ...update }), false);
  assert.equal(distanceOrderValid("120.501", fields()), false);
});
test("form allowlist excludes credentials tokens quantities and fields from other services", () => {
  const input = { ...fields(), token: "injected", type: "1", quantity: "100", authCode: "private", accountSessionId: "other", schedule: "other", zkm: "999" };
  assert.deepEqual(serviceFormFields("ssbenz_xbd", input), fields());
  assert.equal(serviceFormFields("ssbenz_xbd", input).password, " demo-password ");
});
test("submission labels never claim execution completion or integer refundable units", () => {
  assert.equal(stateName("SUBMITTED"), "已提交");
  assert.equal(stateName("SUBMISSION_REVIEW"), "提交待核对");
  assert.equal(maxRefundableUnits({ providerType: "ssbenz_xbd", quantity: 1, completed: null }), 0);
  assert.equal(maxRefundableUnits({ providerType: "jiguang", quantity: 10, completed: 2 }), 8);
});
test("P05 is separately configured as a service catalog not a course or full opaque migration", () => {
  assert.equal(isReadOnlyProviderType("ssbenz_xbd"), true);
  assert.equal(canReadIntegration({ providerType: "ssbenz_xbd", integrationStatus: "NATIVE_PARTIAL", availableCapabilities: ["CATALOG"] }), true);
  assert.equal(Boolean(canReadIntegration({ providerType: "ssbenz_xbd", integrationStatus: "NEEDS_PROTOCOL", availableCapabilities: ["CATALOG"] })), false);
});
