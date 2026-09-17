import test from "node:test";
import assert from "node:assert/strict";
import { jingyuAccountError, jingyuLookupFields, jingyuFormFields, jingyuLookupValid, jingyuOrderError } from "../src/utils/jingyuServices.js";
import { serviceFormFields } from "../src/utils/serviceCommerce.js";

const schoolId = "9007199254740993", ruleId = "9007199254740995";
const fields = () => ({ schoolId, schoolName: "测试学院", account: "001_test-25", password: "  exact password  ", runRuleId: ruleId });
const product = { providerType: "jingyu", project: "yyd", remoteProductId: "yyd" };
const lookup = () => ({ suggested: { schoolId, schoolName: "测试学院" }, choices: [{ field: "runRuleId", value: ruleId, label: "东校区" }],
  schoolRules: [{ id: ruleId, zoneId: "11", zoneName: "东校区", minDistance: "1.5" }] });
const times = ["2026-09-13 08:01:02"], now = new Date("2026-09-12T01:00:00Z");
const error = (f = fields(), l = lookup(), distance = "1.5") => jingyuOrderError(product, f, 1, distance, l, times, now);

test("school lookup and checkout carry only the school-scoped fields and preserve exact credentials", () => {
  const input = { ...fields(), zoneId: "spoofed", studentId: "spoofed", runType: "1", minMinute: "3", repair: "1" };
  const { runRuleId, ...accountFields } = fields();
  assert.deepEqual(jingyuLookupFields("yyd", input), accountFields);
  assert.deepEqual(jingyuFormFields("yyd", input), fields());
  assert.deepEqual(serviceFormFields("jingyu", input, "yyd"), fields());
  assert.equal(jingyuAccountError("yyd", input), "");
});

test("school ID, canonical name, and alphanumeric school account are required", () => {
  for (const schoolId of [null, 9007199254740993, "0", "01", "9".repeat(20), "1\n"])
    assert.notEqual(jingyuAccountError("yyd", { ...fields(), schoolId }), "");
  for (const schoolName of ["", " ", " 学院", "学院\n", "学".repeat(81), null])
    assert.notEqual(jingyuAccountError("yyd", { ...fields(), schoolName }), "");
  for (const account of ["", " student", "student\n", "a".repeat(65), "学号", 123])
    assert.notEqual(jingyuAccountError("yyd", { ...fields(), account }), "");
  for (const password of [null, "  ", "pass\n", "a".repeat(129)])
    assert.notEqual(jingyuAccountError("yyd", { ...fields(), password }), "");
});

test("school rule responses bind the selected school and match every choice to a distinct rule", () => {
  assert.equal(jingyuLookupValid("yyd", lookup(), fields()), true);
  assert.equal(error(), "");
  assert.equal(jingyuLookupValid("yyd", lookup(), { ...fields(), schoolId: "2" }), false);
  assert.equal(jingyuLookupValid("yyd", lookup(), { ...fields(), schoolName: "另一所学院" }), false);
  const mutate = (fn) => { const value = lookup(); fn(value); return value; };
  for (const value of [null, {}, mutate(v => { delete v.schoolRules; }), mutate(v => { v.schoolRules = null; }),
    mutate(v => { v.schoolRules.push(v.schoolRules[0]); }), mutate(v => { v.choices[0].field = "zoneId"; }),
    mutate(v => { v.schoolRules[0].id = "12"; }), mutate(v => { v.schoolRules[0].zoneId = 11; }),
    mutate(v => { v.schoolRules[0].zoneName = "不同跑区"; }), mutate(v => { v.schoolRules[0].minDistance = "100.1"; }),
    mutate(v => { v.suggested.password = "must-not-return"; }), mutate(v => { v.schoolRules = [null]; })]) {
    assert.equal(jingyuLookupValid("yyd", value, fields()), false);
  }
});

test("rule selection is explicit and its minimum distance is enforced without changing task times", () => {
  assert.notEqual(error({ ...fields(), runRuleId: undefined }), "");
  assert.notEqual(error({ ...fields(), runRuleId: "unknown" }), "");
  assert.match(error(fields(), lookup(), "1.4"), /至少 1.5 公里/);
  for (const distance of ["1.5", "2", "100"]) assert.equal(error(fields(), lookup(), distance), "");
  for (const distance of ["0.1", "1.51", "100.1", "NaN", 2]) assert.notEqual(error(fields(), lookup(), distance), "");
  assert.notEqual(jingyuOrderError(product, fields(), 1, "1.5", lookup(), [], now), "");
  assert.notEqual(jingyuOrderError(product, fields(), 1, "1.5", lookup(), ["2026-09-11 08:00:00"], now), "");
});

test("multiple rules may share a zone but cannot share the rule identity", () => {
  const value = lookup();
  value.choices.push({ field: "runRuleId", value: "22", label: "东校区" });
  value.schoolRules.push({ id: "22", zoneId: "11", zoneName: "东校区", minDistance: "2" });
  assert.equal(jingyuLookupValid("yyd", value, fields()), true);
  assert.match(error({ ...fields(), runRuleId: "22" }, value), /至少 2 公里/);
  assert.equal(error({ ...fields(), runRuleId: "22" }, value, "2"), "");
  value.schoolRules[1].id = ruleId;
  assert.equal(jingyuLookupValid("yyd", value), false);
});
