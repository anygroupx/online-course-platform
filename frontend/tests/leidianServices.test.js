import test from "node:test";
import assert from "node:assert/strict";
import { leidianProjects, leidianNeedsRules, leidianDateRange, leidianClock, leidianPlanError,
  editableLeidianPlan, leidianAccountError, leidianLookupValid, leidianOrderError, leidianTaskTimeError,
  canReadLeidianRecord, leidianLogsValid, leidianResolutionError, leidianResolutionPayload } from "../src/utils/leidianServices.js";
import { nativeProductSupported, serviceNames, serviceFormFields, serviceActionName, quoteChargeDetails } from "../src/utils/serviceCommerce.js";
import { isReadOnlyProviderType } from "../src/utils/pluginIntegrations.js";
import { statusCheckLabels } from "../src/utils/serviceStatusCheck.js";
import { serviceOrderSearchParams } from "../src/composables/useServiceOrderSearch.js";
const now = new Date("2026-09-12T01:00:00.400Z");
const plan = () => ({ startDate: "2026-09-13", startTime: "08:00:01", endTime: "09:30:59", weekdays: "1,3,5" });
const product = (project = "1") => ({ providerType: "leidian", project, remoteProductId: project });
const fields = () => ({ ...plan(), account: "authorized_uid", zoneId: "9007199254740993" });
const lookup = () => ({ suggested: { schoolName: "示例学校" }, choices: [{ field: "zoneId", value: "9007199254740993", label: "田径场" }],
  runRules: [{ distance: "2.0", startTime: "08:00:01", endTime: "09:30:59" }] });
const order = () => ({ providerType: "leidian", status: "ACTIVE", actions: ["SCORE_INFO", "CHANGE_TIME"] });
const resolution = () => ({ outcome: "ACCEPTED", evidence: "已独立核实该订单及对应记录的处理结果", upstreamChecked: true,
  externalOrderNo: "order_A-01", externalSubOrderNo: "9007199254740993" });

test("Leidian registers exactly four string projects, catalog and order filtering", () => {
  assert.equal(Object.keys(leidianProjects).length, 4);
  for (const id of ["1", "2", "3", "4"]) {
    assert.equal(nativeProductSupported("leidian", id, id), true);
    assert.equal(leidianNeedsRules(id), id !== "4");
  }
  for (const [project, id] of [[1, 1], [4, 4], ["01", "01"], ["5", "5"], ["default", "1"], ["1", "2"]])
    assert.equal(nativeProductSupported("leidian", project, id), false);
  assert.equal(serviceNames.leidian, "雷电");
  assert.equal(isReadOnlyProviderType("leidian"), true);
  assert.equal(serviceOrderSearchParams({ providerType: "leidian" }).providerType, "leidian");
});
test("Leidian checkout whitelists inputs and excludes passwords, sessions and returned school names", () => {
  const input = fields();
  assert.deepEqual(serviceFormFields("leidian", { ...input, password: "private", authCode: "private", accountSessionId: "private",
    schoolName: "不应提交", runRules: [], taskTimes: [], quantity: 50, distance: "2", token: "private" }), input);
});
test("projects 1–3 use UID, project 4 requires a numeric phone without coercion", () => {
  for (const project of ["1", "2", "3"]) assert.equal(leidianAccountError(project, "UID_a-9"), "");
  assert.equal(leidianAccountError("4", "13800138000"), "");
  for (const account of ["", " ", null, 123, ["uid"], "bad.uid", "uid\n", "uid\u0085", "a".repeat(65)])
    assert.notEqual(leidianAccountError("1", account), "");
  for (const account of ["123456", "1".repeat(16), "+8613800138000", "UID", 13800138000])
    assert.notEqual(leidianAccountError("4", account), "");
  for (const project of [1, 4, null, "5", "01"]) assert.notEqual(leidianAccountError(project, "1234567"), "");
});
test("Beijing date boundaries are browser-timezone independent and clamp leap day", () => {
  assert.deepEqual(leidianDateRange("2026-09-11T16:00:00Z"), { min: "2026-09-12", max: "2027-09-12" });
  assert.deepEqual(leidianDateRange("2028-02-28T16:00:00Z"), { min: "2028-02-29", max: "2029-02-28" });
  for (const startDate of ["2026-09-12", "2027-09-12"]) assert.equal(leidianPlanError({ ...plan(), startDate }, now), "");
  for (const startDate of ["2026-09-11", "2027-09-13", "2026-02-30", "2026-9-12", "2026-09-12\n", 20260912])
    assert.notEqual(leidianPlanError({ ...plan(), startDate }, now), "");
});
test("plan times retain seconds, normalize minutes and reject equal or overnight windows", () => {
  assert.equal(leidianClock("08:03"), "08:03:00"); assert.equal(leidianClock("08:03:59"), "08:03:59");
  for (const value of ["8:03", "08:3", "24:00", "23:59:60", "08:00\n", "08:00:00.1", null, 800]) assert.equal(leidianClock(value), "");
  assert.equal(leidianPlanError(plan(), now), "");
  for (const [startTime, endTime] of [["08:00", "08:00:00"], ["23:00", "01:00"], ["08:00:59", "08:00:58"], [null, "09:00"]])
    assert.notEqual(leidianPlanError({ ...plan(), startTime, endTime }, now), "");
});
test("weekdays are one to seven distinct string selections", () => {
  assert.equal(leidianPlanError({ ...plan(), weekdays: "7,1" }, now), "");
  for (const weekdays of ["", "0", "8", "1,1", "1, 2", "01", "1,2,", [1, 2], "1\n"])
    assert.notEqual(leidianPlanError({ ...plan(), weekdays }, now), "");
});
test("malformed plans and clocks fail closed", () => {
  for (const value of [undefined, null, {}, [], "text"]) assert.notEqual(leidianPlanError(value, now), "");
  assert.notEqual(leidianPlanError(plan(), new Date("invalid")), "");
  assert.notEqual(leidianTaskTimeError("2026-09-13", "08:00", "invalid"), "");
});
test("lookup validates strict string run-zone IDs, bounds and usable rules", () => {
  assert.equal(leidianLookupValid(lookup()), true);
  for (const value of [null, {}, { ...lookup(), choices: [] }, { ...lookup(), runRules: [] },
    { ...lookup(), suggested: { schoolName: "\n" } }, { ...lookup(), choices: [{ field: "zoneId", value: 123, label: "田径场" }] },
    { ...lookup(), choices: [{ field: "zoneId", value: "01", label: "田径场" }] },
    { ...lookup(), choices: [{ field: "account", value: "123", label: "田径场" }] },
    { ...lookup(), runRules: [{ distance: 2, startTime: "08:00:00", endTime: "09:00:00" }] },
    { ...lookup(), runRules: [{ distance: "10.1", startTime: "08:00:00", endTime: "09:00:00" }] },
    { ...lookup(), runRules: [{ distance: "2", startTime: "23:00:00", endTime: "01:00:00" }] }])
    assert.equal(leidianLookupValid(value), false);
  assert.equal(leidianLookupValid({ ...lookup(), choices: Array(201).fill(lookup().choices[0]) }), false);
  assert.equal(leidianLookupValid({ ...lookup(), runRules: Array(65).fill(lookup().runRules[0]) }), false);
  assert.equal(leidianLookupValid({ ...lookup(), runRules: Array(2).fill(lookup().runRules[0]) }), false);
});
test("lookup recommendations below one km never bypass the purchase distance minimum", () => {
  const value = { ...lookup(), runRules: [{ distance: "0.5", startTime: "08:00", endTime: "09:00" }] };
  assert.equal(leidianLookupValid(value), true);
  assert.notEqual(leidianOrderError(product(), fields(), 10, "0.5", value, now), "");
});
test("all four projects validate checkout and project 4 requires neither lookup nor run zone", () => {
  for (const project of ["1", "2", "3"]) assert.equal(leidianOrderError(product(project), fields(), 10, "2.0", lookup(), now), "");
  const phoneFields = { ...plan(), account: "13800138000" };
  assert.equal(leidianOrderError(product("4"), phoneFields, 100, "10", null, now), "");
  assert.notEqual(leidianOrderError(product("4"), { ...phoneFields, zoneId: "123" }, 10, "2", null, now), "");
  for (const selected of [product(1), product(4), product("5"), { ...product(), remoteProductId: "2" }, { ...product(), providerType: "flash" }])
    assert.notEqual(leidianOrderError(selected, fields(), 10, "2", lookup(), now), "");
});
test("checkout requires fresh usable rules and an explicit run zone from the result", () => {
  assert.notEqual(leidianOrderError(product(), fields(), 10, "2", null, now), "");
  for (const zoneId of ["", "123", 9007199254740993, null])
    assert.notEqual(leidianOrderError(product(), { ...fields(), zoneId }, 10, "2", lookup(), now), "");
});
test("quantity is 1–100 integer and distance is 1–10 with at most one decimal", () => {
  for (const quantity of [1, 100]) for (const distance of ["1", "1.1", "9.9", "10.0"])
    assert.equal(leidianOrderError(product(), fields(), quantity, distance, lookup(), now), "");
  for (const quantity of [0, 101, 1.5, "10", null, NaN]) assert.notEqual(leidianOrderError(product(), fields(), quantity, "2", lookup(), now), "");
  for (const distance of [0, 2, "", ".5", "0.9", "10.1", "2.01", "02", "2e0", "2\n"])
    assert.notEqual(leidianOrderError(product(), fields(), 10, distance, lookup(), now), "");
});
test("plan editing copies only saved plan fields, without credentials or checkout defaults", () => {
  const original = { ...plan(), account: "private", password: "private", zoneId: "123", distance: "2", weekdays: "7,2" };
  assert.deepEqual(editableLeidianPlan(original), { ...plan(), weekdays: "7,2" });
  for (const value of [undefined, null, {}, { startTime: 800 }]) assert.deepEqual(editableLeidianPlan(value), { startDate: "", startTime: "", endTime: "", weekdays: "" });
});
test("task time uses Beijing and is strictly future to second precision", () => {
  assert.notEqual(leidianTaskTimeError("2026-09-12", "09:00:00", now), "");
  assert.equal(leidianTaskTimeError("2026-09-12", "09:00:01", now), "");
  assert.equal(leidianTaskTimeError("2027-09-12", "09:00:00", now), "");
  assert.notEqual(leidianTaskTimeError("2027-09-12", "09:00:01", now), "");
  assert.notEqual(leidianTaskTimeError("2026-02-30", "09:00:01", now), "");
  assert.equal(leidianTaskTimeError("2029-02-28", "08:00:00", "2028-02-29T00:00:00Z"), "");
  assert.notEqual(leidianTaskTimeError("2029-03-01", "08:00:00", "2028-02-29T00:00:00Z"), "");
});
test("cancelled and refund-review orders never read deleted records or score text", () => {
  assert.equal(canReadLeidianRecord(order()), true);
  for (const status of ["CANCELLED", "REFUND_REVIEW", "REFUNDED"]) assert.equal(canReadLeidianRecord({ ...order(), status }), false);
  for (const value of [null, { ...order(), pendingOperationId: "pending" }, { ...order(), providerType: "flash" },
    { ...order(), actions: [] }, { ...order(), actions: "SCORE_INFO" }]) assert.equal(canReadLeidianRecord(value), false);
});
test("recovery requires evidence, a literal confirmation and strict double IDs", () => {
  assert.equal(leidianResolutionError("CREATE", resolution()), "");
  for (const change of [{ upstreamChecked: false }, { upstreamChecked: "true" }, { evidence: "短证据" }, { evidence: "字".repeat(1001) },
    { externalOrderNo: "bad.id" }, { externalOrderNo: 123 }, { externalSubOrderNo: 9007199254740992 },
    { externalSubOrderNo: "01" }, { externalSubOrderNo: "0" }, { externalSubOrderNo: "1\n" }])
    assert.notEqual(leidianResolutionError("CREATE", { ...resolution(), ...change }), "");
  assert.notEqual(leidianResolutionError("CREATE", null), "");
});
test("recovery never submits refunded units and double IDs stay exact strings", () => {
  assert.deepEqual(leidianResolutionPayload("CREATE", { ...resolution(), refundedUnits: 9, password: "private" }), resolution());
  for (const action of ["CANCEL", "EDIT_PLAN", "CHANGE_TIME"]) {
    assert.equal(leidianResolutionError(action, { ...resolution(), externalOrderNo: "", externalSubOrderNo: "" }), "");
    assert.deepEqual(leidianResolutionPayload(action, { ...resolution(), refundedUnits: 9 }), {
      outcome: "ACCEPTED", evidence: resolution().evidence, upstreamChecked: true,
    });
  }
  assert.deepEqual(leidianResolutionPayload("CREATE", { ...resolution(), outcome: "NOT_ACCEPTED" }), {
    outcome: "NOT_ACCEPTED", evidence: resolution().evidence, upstreamChecked: true,
  });
});
test("labels separate consumed quota, score text, cancellation and refund settlement", () => {
  assert.equal(serviceActionName("CANCEL", product()), "取消订单");
  assert.equal(serviceActionName("SCORE_INFO", product()), "成绩查询信息");
  assert.equal(serviceActionName("SYNC", product()), "核对次数与状态");
  assert.equal(serviceActionName("EDIT_PLAN", product()), "编辑执行安排");
  assert.match(statusCheckLabels("leidian").note, /不代表/);
  assert.match(statusCheckLabels("leidian").region, /次数/);
  assert.equal(quoteChargeDetails({ action: "CANCEL", amount: "0.00", quantity: 10, unitCharge: "1.00", quantityUnit: "次" }), null);
});

test("task pages keep exact IDs and editable flags and cannot retain a mismatched page", () => {
  const row = { id: "task_9007199254740993", time: "2026-09-13 08:00:01", status: "需要关注", editable: true, endTime: null };
  const page = { page: 2, hasMore: true, items: [row] };
  assert.equal(leidianLogsValid(page, 2), true);
  assert.equal(leidianLogsValid(page, 1), false);
  assert.equal(leidianLogsValid({ ...page, page: 100 }, 100), false);
  for (const patch of [{ id: 123 }, { id: "task\n" }, { editable: "true" }, { time: "2026-02-30 08:00:01" },
    { status: "<script>" }, { endTime: "not a date" }]) assert.equal(leidianLogsValid({ ...page, items: [{ ...row, ...patch }] }, 2), false);
  assert.equal(leidianLogsValid({ ...page, items: [row, row] }, 2), false);
  assert.equal(leidianLogsValid(null, 1), false);
});
