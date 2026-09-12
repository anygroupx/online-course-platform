import test from "node:test";
import assert from "node:assert/strict";
import { appuiProjects, appuiNeedsSchool, appuiPlanError, editableAppuiPlan, appuiRemainingText } from "../src/utils/appuiServices.js";
import { nativeProductSupported, serviceNames, serviceActionName, serviceFormFields, orderStateName, quoteChargeDetails } from "../src/utils/serviceCommerce.js";
import { isReadOnlyProviderType } from "../src/utils/pluginIntegrations.js";
import { statusCheckLabels } from "../src/utils/serviceStatusCheck.js";
import { serviceOrderSearchParams } from "../src/composables/useServiceOrderSearch.js";

const fields = () => ({ address: "教学楼 A&B", startTime: "07:31", endTime: "18:11", weekdays: "1,2,3,4,5", reports: "1" });
test("AppUI registers nine exact project IDs and only three need school discovery", () => {
  assert.equal(Object.keys(appuiProjects).length, 9);
  for (let n = 1; n <= 9; n++) {
    const id = String(n);
    assert.ok(nativeProductSupported("appui", id, id));
    assert.equal(appuiNeedsSchool(id), [3, 6, 8].includes(n));
  }
  for (const [project, id] of [["0", "0"], ["10", "10"], ["01", "01"], ["1", "2"], [1, 1], ["default", "1"]])
    assert.equal(nativeProductSupported("appui", project, id), false);
  assert.equal(serviceNames.appui, "实习打卡");
  assert.ok(isReadOnlyProviderType("appui"));
  assert.equal(serviceOrderSearchParams({ providerType: "appui" }).providerType, "appui");
});
test("AppUI forms exclude names returned by lookup and credentials for other services", () => {
  const safe = { ...fields(), account: "student-001", password: " p&+=密码 ", schoolName: "示例学校" };
  assert.deepEqual(serviceFormFields("appui", { ...safe, studentName: "伪造姓名", authCode: "secret", pass: "secret",
    quantity: 5000, days1: 5000, distance: "5", schedule: {}, accountSessionId: "foreign", token: "secret" }), safe);
});
test("valid day arrangements preserve minute precision without inventing a date calendar", () => {
  assert.equal(appuiPlanError(fields()), "");
  assert.equal(appuiPlanError({ ...fields(), startTime: "00:00", endTime: "23:59", weekdays: "7,1", reports: "3,2,1" }), "");
});
test("AppUI address is required, bounded and rejects control characters", () => {
  for (const address of ["", "   ", null, 123, "字".repeat(501), "地址\n楼层", "地址\u0085楼层"])
    assert.notEqual(appuiPlanError({ ...fields(), address }), "");
  assert.notEqual(appuiPlanError(null), "");
  assert.equal(appuiPlanError({ ...fields(), address: "字".repeat(500) }), "");
});
test("time windows cannot be missing, malformed, equal or overnight", () => {
  for (const [startTime, endTime] of [["7:30", "18:10"], ["07:30:00", "18:10"], ["24:00", "23:59"], ["07:60", "18:10"],
    ["18:10", "07:30"], ["07:30", "07:30"], ["", "18:10"], [["07:30"], "18:10"]])
    assert.notEqual(appuiPlanError({ ...fields(), startTime, endTime }), "");
});
test("weekdays and reports are bounded nonempty distinct selections", () => {
  for (const weekdays of ["", "0", "8", "1,1", "1,2,", "01", "1, 2", [1, 2], "1\n"])
    assert.notEqual(appuiPlanError({ ...fields(), weekdays }), "");
  for (const reports of ["", "0", "4", "1,1", "01", "1,2,3,1", [1], "1\n"])
    assert.notEqual(appuiPlanError({ ...fields(), reports }), "");
});
test("editing copies only current arrangements and never defaults or returns a password", () => {
  const original = { ...fields(), reports: "2,3", password: "private", pass: "private", account: "private", distance: "2" };
  const edit = editableAppuiPlan(original);
  assert.deepEqual(edit, { ...fields(), reports: "2,3", password: "" });
  edit.address = "新地址";
  assert.equal(original.address, "教学楼 A&B");
  for (const source of [undefined, {}, null, { startTime: false, reports: [1] }]) {
    const incomplete = editableAppuiPlan(source);
    assert.equal(incomplete.password, ""); assert.equal(incomplete.startTime, "");
    assert.notEqual(appuiPlanError(incomplete), "");
  }
});
test("remaining days are only a valid checked quota, never a guessed refund or attendance result", () => {
  const order = { providerType: "appui", status: "ACTIVE", quantity: 10, completed: 3 };
  assert.equal(appuiRemainingText(order), "7 天");
  assert.equal(appuiRemainingText({ ...order, status: "COMPLETED" }), "7 天");
  assert.equal(appuiRemainingText({ ...order, completed: 10 }), "0 天");
  for (const status of ["ATTENTION", "REFUND_REVIEW", "CONFIRMING", "UNKNOWN"]) assert.equal(appuiRemainingText({ ...order, status }), "待核对");
  for (const status of ["REFUNDED", "CANCELLED"]) assert.equal(appuiRemainingText({ ...order, status }), "已结束");
  for (const change of [{ quantity: "10" }, { quantity: 10000 }, { completed: null }, { completed: -1 }, { completed: 11 }, { pendingOperationId: "pending" }])
    assert.equal(appuiRemainingText({ ...order, ...change }), "待核对");
});
test("day action and status labels avoid reporting quota usage as successful attendance", () => {
  assert.equal(serviceActionName("ADD_TIMES", { providerType: "appui" }), "增加天数");
  assert.equal(serviceActionName("ADD_TIMES", { quantityUnit: "天" }), "增加天数");
  assert.equal(serviceActionName("ADD_TIMES", { providerType: "wuxin" }), "增加次数");
  assert.equal(orderStateName({ providerType: "appui", status: "COMPLETED" }), "服务已结束");
  assert.match(statusCheckLabels("appui").note, /不代表签到成功/);
  assert.match(statusCheckLabels("appui").heading, /天数与状态/);
});
test("day confirmation displays the frozen eight-decimal server price without recomputing it", () => {
  const quote = { action: "ADD_TIMES", quantity: 4, quantityUnit: "天", unitCharge: "0.25000000", amount: "1.00" };
  assert.deepEqual(quoteChargeDetails(quote), { quantity: 4, unit: "天", unitCharge: "0.25000000", refund: false });
  assert.equal(quoteChargeDetails({ ...quote, unitCharge: 0.25 }), null);
});
