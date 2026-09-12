import test from "node:test";
import assert from "node:assert/strict";
import { jingyuProjects, jingyuClock, jingyuDateRange, jingyuTimestampValid, jingyuTaskTimeError, jingyuTaskTimesError,
  jingyuAccountError, jingyuLookupFields, jingyuFormFields, jingyuLookupValid, jingyuOrderError,
  jingyuLogsValid, jingyuTaskEditable, jingyuResolutionError, jingyuResolutionPayload } from "../src/utils/jingyuServices.js";
import { nativeProductSupported, serviceNames, serviceFormFields, serviceActionName } from "../src/utils/serviceCommerce.js";
import { isReadOnlyProviderType } from "../src/utils/pluginIntegrations.js";
import { serviceOrderSearchParams } from "../src/composables/useServiceOrderSearch.js";
const now = new Date("2026-09-12T01:00:00.400Z");
const uid = "9007199254740993";
const product = (project = "keep") => ({ providerType: "jingyu", project, remoteProductId: project });
const fields = (project = "keep") => project === "keep"
  ? { account: "13800138000", password: "  keep password  ", zoneId: uid, minMinute: "3", maxMinute: "15" }
  : { account: uid, zoneId: uid, runType: "1" };
const lookup = (project = "keep") => ({ suggested: project === "keep" ? {} : { schoolName: "示例学校", minDistance: "2.0",
  authorizationState: "VALID", authorizationType: "扫码授权", authorizedAt: "2026-09-11 08:09:10" },
  choices: [{ field: "zoneId", value: uid, label: "田径场" }] });
const times = ["2026-09-13 08:09:10", "2026-09-14 08:09:10"];
const order = () => ({ providerType: "jingyu", status: "ACTIVE", actions: ["DELAY_TASK", "CHANGE_TIME"] });
const row = (id = "task-1") => ({ id, time: times[0], status: "待执行", editable: true, endTime: null });
const logs = (page = 1) => ({ page, hasMore: false, items: [row()] });
const resolution = () => ({ outcome: "ACCEPTED", evidence: "已独立核实订单编号、记录编号及资金处理结果", upstreamChecked: true,
  externalOrderNo: "record_A-01", externalSubOrderNo: uid });

test("Jingyu exposes only the two verified string projects and searchable orders", () => {
  assert.deepEqual(Object.keys(jingyuProjects), ["keep", "bdlp"]);
  for (const project of Object.keys(jingyuProjects)) assert.equal(nativeProductSupported("jingyu", project, project), true);
  for (const [project, id] of [["yyd", "yyd"], ["ymty", "ymty"], ["default", "keep"], ["keep", "bdlp"], [1, 1], [null, "keep"]])
    assert.equal(nativeProductSupported("jingyu", project, id), false);
  assert.equal(serviceNames.jingyu, "鲸鱼"); assert.equal(isReadOnlyProviderType("jingyu"), true);
  assert.equal(serviceOrderSearchParams({ providerType: "jingyu" }).providerType, "jingyu");
  assert.equal(serviceActionName("REFUND", product()), "取消并申请退款");
  assert.equal(serviceActionName("DELAY", product()), "延期未完成任务");
});
test("lookup and checkout preserve long UID strings and meaningful password spaces without leaking project fields", () => {
  for (const project of ["keep", "bdlp"]) {
    const input = { ...fields(project), schoolName: "只展示", authorizationState: "VALID", password: "  keep password  ",
      minMinute: "3", maxMinute: "15", runType: "1", taskTimes: times, token: "never-send", repair: "true" };
    assert.deepEqual(jingyuLookupFields(project, input), project === "keep" ? { account: input.account, password: input.password } : { account: uid });
    assert.deepEqual(jingyuFormFields(project, input), fields(project));
    assert.deepEqual(serviceFormFields("jingyu", input, project), fields(project));
  }
});
test("field whitelists fail closed on unknown projects and malformed inputs", () => {
  for (const value of [null, undefined, [], "text", 42]) for (const project of ["keep", "bdlp", "__proto__", "constructor", "invalid"])
    for (const fn of [jingyuLookupFields, jingyuFormFields]) assert.deepEqual(fn(project, value), {});
  assert.deepEqual(jingyuFormFields("keep", Object.create(fields())), {});
  assert.deepEqual(jingyuLookupFields("keep", { account: 13800138000, password: 123 }), {});
});
test("Keep requires a numeric phone and a nonblank control-free password without trimming", () => {
  assert.equal(jingyuAccountError("keep", fields()), "");
  for (const account of ["123456", "1".repeat(16), "+8613800138000", "13800138000\n", 13800138000, null])
    assert.notEqual(jingyuAccountError("keep", { ...fields(), account }), "");
  for (const password of ["", "  ", "a\n", "a\u0085", "x".repeat(129), 123, null])
    assert.notEqual(jingyuAccountError("keep", { ...fields(), password }), "");
});
test("Bdlp UID is never coerced to a lossy number or accepted with a leading zero", () => {
  for (const account of ["1", uid, "9".repeat(19)]) assert.equal(jingyuAccountError("bdlp", { account }), "");
  for (const account of ["0", "01", "9".repeat(20), "1.0", "1e3", " 1", "1\n", Number(uid), null])
    assert.notEqual(jingyuAccountError("bdlp", { account }), "");
  for (const project of [null, "yyd", "__proto__"]) assert.notEqual(jingyuAccountError(project, fields()), "");
});
test("Beijing ranges clamp leap day and do not depend on the browser timezone", () => {
  assert.deepEqual(jingyuDateRange("2026-09-11T16:00:00Z"), { min: "2026-09-12", max: "2027-09-12" });
  assert.deepEqual(jingyuDateRange("2028-02-28T16:00:00Z"), { min: "2028-02-29", max: "2029-02-28" });
  assert.deepEqual(jingyuDateRange("invalid"), { min: "", max: "" });
  assert.equal(jingyuTaskTimeError("2029-02-28", "08:00:00", "2028-02-29T00:00:00Z"), "");
  assert.notEqual(jingyuTaskTimeError("2029-03-01", "08:00:00", "2028-02-29T00:00:00Z"), "");
});
test("clock normalization preserves seconds and strictly rejects invalid calendar stamps", () => {
  assert.equal(jingyuClock("08:09"), "08:09:00"); assert.equal(jingyuClock("08:09:10"), "08:09:10");
  for (const time of ["8:09", "08:9", "24:00", "23:59:60", "08:00\n", null, 800]) assert.equal(jingyuClock(time), "");
  assert.equal(jingyuTimestampValid(times[0]), true);
  for (const stamp of ["2026-02-30 08:00:00", "2026-09-13T08:00:00", "2026-09-13 08:00", "2026-09-13 24:00:00", null, 123])
    assert.equal(jingyuTimestampValid(stamp), false);
});
test("task times are strictly future and at most one Beijing calendar year away", () => {
  assert.equal(jingyuTaskTimeError("2026-09-12", "09:00:01", now), "");
  assert.equal(jingyuTaskTimeError("2027-09-12", "09:00:00", now), "");
  for (const [date, time] of [["2026-09-12", "09:00:00"], ["2027-09-12", "09:00:01"], ["2026-09-11", "12:00"], ["2026-02-30", "10:00"]])
    assert.notEqual(jingyuTaskTimeError(date, time, now), "");
});
test("there is exactly one unique explicit timestamp per purchased unit, including the 365-unit limit", () => {
  assert.equal(jingyuTaskTimesError(times, 2, now), "");
  const yearly = Array.from({ length: 365 }, (_, i) => `${new Date(Date.UTC(2026, 8, 13 + i)).toISOString().slice(0, 10)} 08:00:00`);
  assert.equal(jingyuTaskTimesError(yearly, 365, now), "");
  for (const [value, quantity] of [[null, 2], [times, "2"], [times, 1], [times, 0], [yearly, 366], [[times[0], times[0]], 2], [[times[0], ""], 2]])
    assert.notEqual(jingyuTaskTimesError(value, quantity, now), "");
});
test("lookup accepts only complete project-specific facts and unique explicit zone choices", () => {
  assert.equal(jingyuLookupValid("keep", lookup()), true); assert.equal(jingyuLookupValid("bdlp", lookup("bdlp")), true);
  for (const project of ["keep", "bdlp"]) for (const value of [null, {}, [], { ...lookup(project), choices: [] },
    { ...lookup(project), choices: [...lookup(project).choices, ...lookup(project).choices] },
    { ...lookup(project), choices: [{ field: "zoneId", value: Number(uid), label: "场地" }] }]) assert.equal(jingyuLookupValid(project, value), false);
  assert.equal(jingyuLookupValid("keep", { ...lookup(), suggested: { account: "never-merge" } }), false);
  for (const key of Object.keys(lookup("bdlp").suggested)) {
    const value = lookup("bdlp"); delete value.suggested[key]; assert.equal(jingyuLookupValid("bdlp", value), false);
  }
});
test("zone result sizes are bounded and malformed authorization facts cannot enable checkout", () => {
  const value = lookup("bdlp"); value.choices = Array.from({ length: 200 }, (_, i) => ({ field: "zoneId", value: String(i + 1), label: "场地" }));
  assert.equal(jingyuLookupValid("bdlp", value), true); value.choices.push({ field: "zoneId", value: "201", label: "场地" });
  assert.equal(jingyuLookupValid("bdlp", value), false);
  for (const [key, invalid] of [["minDistance", "0"], ["schoolName", ""], ["authorizationState", "OK"], ["authorizedAt", "2026-02-30 08:00:00"]])
    assert.equal(jingyuLookupValid("bdlp", { ...lookup("bdlp"), suggested: { ...lookup("bdlp").suggested, [key]: invalid } }), false);
});
test("checkout requires valid lookup, explicit zone and current authorization", () => {
  for (const project of ["keep", "bdlp"]) {
    assert.equal(jingyuOrderError(product(project), fields(project), 2, "2.0", lookup(project), times, now), "");
    for (const input of [{ ...fields(project), zoneId: "" }, { ...fields(project), zoneId: "2" }, null])
      assert.notEqual(jingyuOrderError(product(project), input, 2, "2.0", lookup(project), times, now), "");
    assert.notEqual(jingyuOrderError(product(project), fields(project), 2, "2.0", null, times, now), "");
  }
  const expired = lookup("bdlp"); expired.suggested.authorizationState = "EXPIRED";
  assert.equal(jingyuLookupValid("bdlp", expired), true);
  assert.match(jingyuOrderError(product("bdlp"), fields("bdlp"), 2, "2.0", expired, times, now), /授权已失效/);
});
test("checkout enforces distance and project-only pace/type bounds without estimating a charge", () => {
  for (const distance of ["1", "1.5", "99.9", "100", "100.0"])
    assert.equal(jingyuOrderError(product(), fields(), 2, distance, lookup(), times, now), "");
  for (const distance of ["0", "0.9", "1.00", "100.1", "101", "01", " 2", 2, null])
    assert.notEqual(jingyuOrderError(product(), fields(), 2, distance, lookup(), times, now), "");
  for (const extra of [{ minMinute: "2" }, { minMinute: "7" }, { maxMinute: "7" }, { maxMinute: "16" }])
    assert.notEqual(jingyuOrderError(product(), { ...fields(), ...extra }, 2, "2", lookup(), times, now), "");
  assert.equal(jingyuOrderError(product("bdlp"), { ...fields("bdlp"), runType: "2" }, 2, "2", lookup("bdlp"), times, now), "");
  assert.notEqual(jingyuOrderError(product("bdlp"), { ...fields("bdlp"), runType: 1 }, 2, "2", lookup("bdlp"), times, now), "");
});
test("mismatched product identities and an expired explicit task plan fail before preview", () => {
  assert.notEqual(jingyuOrderError({ ...product(), remoteProductId: "bdlp" }, fields(), 2, "2", lookup(), times, now), "");
  assert.notEqual(jingyuOrderError(product(), fields(), 2, "2", lookup(), times, "2026-09-13T00:09:10Z"), "");
});
test("task log pagination permits 20 rows per page and only five on the final nineteenth page", () => {
  assert.equal(jingyuLogsValid(logs(), 1), true);
  const full = { page: 2, hasMore: true, items: Array.from({ length: 20 }, (_, i) => row(`task-${i}`)) };
  assert.equal(jingyuLogsValid(full, 2), true);
  assert.equal(jingyuLogsValid({ ...full, items: full.items.slice(0, 19) }, 2), false);
  const last = { page: 19, hasMore: false, items: full.items.slice(0, 5) };
  assert.equal(jingyuLogsValid(last, 19), true);
  for (const value of [{ ...last, hasMore: true }, { ...last, items: full.items.slice(0, 6) }, { ...last, page: 20 }]) assert.equal(jingyuLogsValid(value, value.page), false);
  for (const page of [0, 20, "1", null]) assert.equal(jingyuLogsValid(logs(), page), false);
});
test("task logs reject duplicate identifiers, malformed dates and falsely editable completed rows", () => {
  for (const change of [{ id: "" }, { id: "task\n" }, { status: "已完成" }, { time: "2026-02-30 08:00:00" }, { endTime: "tomorrow" }, { editable: "true" }])
    assert.equal(jingyuLogsValid({ ...logs(), items: [{ ...row(), ...change }] }, 1), false);
  assert.equal(jingyuLogsValid({ ...logs(), items: [row(), row()] }, 1), false);
  assert.equal(jingyuLogsValid({ ...logs(), items: [{ ...row(), status: "已退款", editable: false }] }, 1), true);
});
test("task actions require the current validated page, advertised permission and no pending operation", () => {
  const page = logs(); assert.equal(jingyuTaskEditable(order(), page, page.items[0], "CHANGE_TIME"), true);
  for (const change of [{ pendingOperationId: "pending" }, { status: "CONFIRMING" }, { status: "REFUND_REVIEW" }, { actions: [] }, { actions: "CHANGE_TIME" }])
    assert.equal(jingyuTaskEditable({ ...order(), ...change }, page, page.items[0], "CHANGE_TIME"), false);
  assert.equal(jingyuTaskEditable(order(), page, { ...page.items[0] }, "CHANGE_TIME"), false);
  assert.equal(jingyuTaskEditable(order(), page, page.items[0], "REFUND"), false);
  assert.equal(jingyuTaskEditable(order(), { ...page, page: 20 }, page.items[0], "DELAY_TASK"), false);
});
test("accepted creation needs two independent verified identifiers and audit evidence", () => {
  assert.equal(jingyuResolutionError("CREATE", resolution()), "");
  for (const extra of [{ externalOrderNo: "" }, { externalSubOrderNo: "" }, { externalSubOrderNo: Number(uid) },
    { externalOrderNo: "A\n" }, { externalSubOrderNo: "01" }, { evidence: "太短" }, { upstreamChecked: false }])
    assert.notEqual(jingyuResolutionError("CREATE", { ...resolution(), ...extra }), "");
});
test("accepted refunds require an explicit integer count, including exact zero and a zero preview ceiling", () => {
  for (const units of [0, 1, 365]) assert.equal(jingyuResolutionError("REFUND", { ...resolution(), refundedUnits: units }, 365), "");
  assert.equal(jingyuResolutionError("REFUND", { ...resolution(), refundedUnits: 0 }, 0), "");
  for (const refundedUnits of [null, undefined, "0", -1, 1.1, 3])
    assert.notEqual(jingyuResolutionError("REFUND", { ...resolution(), refundedUnits }, 2), "");
  for (const ceiling of [null, "2", -1, 366]) assert.notEqual(jingyuResolutionError("REFUND", { ...resolution(), refundedUnits: 0 }, ceiling), "");
});
test("noncreation reconciliation never forwards supplied identifiers, credentials or estimated refund counts", () => {
  const value = { ...resolution(), refundedUnits: 2, password: "private", account: uid };
  const base = { outcome: value.outcome, evidence: value.evidence, upstreamChecked: true };
  for (const action of ["PAUSE", "RESUME", "DELAY", "DELAY_TASK", "CHANGE_TIME"]) {
    assert.equal(jingyuResolutionError(action, value), ""); assert.deepEqual(jingyuResolutionPayload(action, value), base);
  }
  assert.deepEqual(jingyuResolutionPayload("CREATE", value), { ...base, externalOrderNo: value.externalOrderNo, externalSubOrderNo: uid });
  assert.deepEqual(jingyuResolutionPayload("REFUND", value), { ...base, refundedUnits: 2 });
  assert.deepEqual(jingyuResolutionPayload("CREATE", { ...value, outcome: "NOT_ACCEPTED" }), { ...base, outcome: "NOT_ACCEPTED" });
  for (const value of [null, [], "value"]) assert.deepEqual(jingyuResolutionPayload("CREATE", value), {});
});
test("unknown reconciliation actions or malformed evidence cannot reach the audit endpoint", () => {
  for (const action of ["ADD_TIMES", "EDIT_PLAN", "CANCEL", null]) assert.notEqual(jingyuResolutionError(action, resolution()), "");
  for (const value of [null, [], {}, { ...resolution(), outcome: "UNKNOWN" }, { ...resolution(), evidence: "x".repeat(1001) }])
    assert.notEqual(jingyuResolutionError("CREATE", value), "");
});
