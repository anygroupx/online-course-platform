import { beijingToday } from "./internshipServices.js";

export const leidianProjects = Object.freeze({
  1: "步道乐跑", 2: "步道人脸跑", 3: "步道自由跑", 4: "乐健体育",
});
export const isLeidianService = (value) => value?.providerType === "leidian";
export const leidianNeedsRules = (project) => ["1", "2", "3"].includes(project);
const planKeys = ["startDate", "startTime", "endTime", "weekdays"];
const matches = (value, pattern) => typeof value === "string" && pattern.test(value) && !/\p{Cc}/u.test(value);
const validDate = (value) => matches(value, /^\d{4}-\d{2}-\d{2}$/) &&
  Number.isFinite(Date.parse(`${value}T00:00:00Z`)) && new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value;
const text = (value, max) => typeof value === "string" && value.trim().length > 0 && value.length <= max && !/\p{Cc}/u.test(value);

export function leidianDateRange(now = new Date()) {
  const instant = new Date(now);
  if (!Number.isFinite(instant.getTime())) return { min: "", max: "" };
  const min = beijingToday(instant);
  const [year, month, day] = min.split("-").map(Number);
  const last = new Date(Date.UTC(year + 1, month, 0)).getUTCDate();
  return { min, max: `${year + 1}-${String(month).padStart(2, "0")}-${String(Math.min(day, last)).padStart(2, "0")}` };
}
export function leidianClock(value) {
  return matches(value, /^(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?$/)
    ? value.length === 5 ? `${value}:00` : value : "";
}
export function leidianPlanError(fields = {}, now = new Date()) {
  const range = leidianDateRange(now);
  if (!range.min || !validDate(fields?.startDate) || fields.startDate < range.min || fields.startDate > range.max)
    return "开始日期须在今天至未来一年内（北京时间）";
  const start = leidianClock(fields.startTime), end = leidianClock(fields.endTime);
  if (!start || !end) return "请选择开始时间和结束时间";
  if (start >= end) return "结束时间须晚于开始时间，不支持跨天时间段";
  if (!matches(fields.weekdays, /^[1-7](?:,[1-7]){0,6}$/) ||
      new Set(fields.weekdays.split(",")).size !== fields.weekdays.split(",").length)
    return "请至少选择一个执行星期，不要重复选择";
  return "";
}
// Read saved values as-is. Checkout defaults and credentials never enter plan edits.
export function editableLeidianPlan(suggested = {}) {
  return Object.fromEntries(planKeys.map((key) => [key, typeof suggested?.[key] === "string" ? suggested[key] : ""]));
}
export function leidianAccountError(project, account) {
  if (typeof project !== "string" || !Object.hasOwn(leidianProjects, project)) return "请选择有效的运动项目";
  if (!matches(account, project === "4" ? /^[0-9]{7,15}$/ : /^[A-Za-z0-9_-]{1,64}$/))
    return project === "4" ? "请填写 7–15 位数字手机号" : "请填写已授权账号的 UID，仅支持字母、数字、下划线和短横线";
  return "";
}
export function leidianLookupValid(value) {
  if (!text(value?.suggested?.schoolName, 100) || !Array.isArray(value.choices) || !value.choices.length || value.choices.length > 200 ||
      !Array.isArray(value.runRules) || !value.runRules.length || value.runRules.length > 64) return false;
  if (!value.choices.every((c) => c?.field === "zoneId" && matches(c.value, /^[1-9][0-9]{0,18}$/) && text(c.label, 100)) ||
      new Set(value.choices.map((c) => c.value)).size !== value.choices.length) return false;
  return value.runRules.every((r) => matches(r?.distance, /^(?:0\.[1-9]|[1-9](?:\.\d)?|10(?:\.0)?)$/) &&
    leidianClock(r.startTime) && leidianClock(r.endTime) && leidianClock(r.startTime) < leidianClock(r.endTime)) &&
    new Set(value.runRules.map((r) => `${r.distance}|${leidianClock(r.startTime)}|${leidianClock(r.endTime)}`)).size === value.runRules.length;
}
export function leidianOrderError(product, fields, quantity, distance, lookup, now = new Date()) {
  if (!isLeidianService(product) || typeof product.project !== "string" || !Object.hasOwn(leidianProjects, product.project) || product.project !== product.remoteProductId)
    return "此商品暂不能购买，请重新选择运动项目";
  const accountError = leidianAccountError(product.project, fields?.account);
  if (accountError) return accountError;
  if (!Number.isInteger(quantity) || quantity < 1 || quantity > 100) return "购买次数须为 1–100 的整数";
  if (!matches(distance, /^(?:[1-9](?:\.\d)?|10(?:\.0)?)$/)) return "每次距离须为 1–10 公里，最多一位小数";
  if (leidianNeedsRules(product.project)) {
    if (!leidianLookupValid(lookup)) return "请先查询跑区与规则";
    if (!lookup.choices.some((choice) => choice.value === fields.zoneId)) return "请选择查询结果中的跑区";
  } else if (fields.zoneId) return "此项目不需要选择跑区，请重新打开商品";
  return leidianPlanError(fields, now);
}
export function leidianTaskTimeError(date, time, now = new Date()) {
  const clock = leidianClock(time);
  if (!validDate(date) || !clock) return "请填写有效的任务日期和时间";
  const instant = new Date(now), stamp = `${date}T${clock}+08:00`;
  if (!Number.isFinite(instant.getTime())) return "请填写有效的任务日期和时间";
  const beijingClock = new Date(instant.getTime() + 8 * 3600000).toISOString().slice(11, 23);
  const upper = `${leidianDateRange(instant).max}T${beijingClock}+08:00`;
  if (Date.parse(stamp) <= instant.getTime() || Date.parse(stamp) > Date.parse(upper))
    return "请选择未来一年内的任务时间（北京时间）";
  return "";
}
const logTimeValid = (value) => typeof value === "string" && value.length === 19 && value[10] === " " &&
  validDate(value.slice(0, 10)) && leidianClock(value.slice(11)) === value.slice(11);
export function leidianLogsValid(value, page) {
  return Number.isInteger(page) && page >= 1 && page <= 100 && value?.page === page &&
    typeof value.hasMore === "boolean" && (page < 100 || !value.hasMore) && Array.isArray(value.items) && value.items.length <= 20 &&
    value.items.every((row) => matches(row?.id, /^[A-Za-z0-9_-]{1,64}$/) && logTimeValid(row.time) &&
      ["待执行", "跑步结束", "需要关注", "待核对"].includes(row.status) && typeof row.editable === "boolean" &&
      (row.endTime == null || logTimeValid(row.endTime))) && new Set(value.items.map((row) => row.id)).size === value.items.length;
}
export const canReadLeidianRecord = (order) => isLeidianService(order) && !order.pendingOperationId &&
  !["REFUND_REVIEW", "REFUNDED", "CANCELLED"].includes(order.status) && Array.isArray(order.actions) && order.actions.includes("SCORE_INFO");
export function leidianResolutionError(action, value = {}) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return "请填写已核实的处理结果";
  if (!["ACCEPTED", "NOT_ACCEPTED"].includes(value.outcome)) return "请选择已核实的处理结果";
  if (value.upstreamChecked !== true || !text(value.evidence, 1000) || value.evidence.trim().length < 10) return "请核实处理结果并填写至少 10 字的核对依据";
  if (action === "CREATE" && value.outcome === "ACCEPTED") {
    if (!matches(value.externalOrderNo, /^[A-Za-z0-9_-]{1,64}$/)) return "请填写已核实的服务订单号";
    if (!matches(value.externalSubOrderNo, /^[1-9][0-9]{0,18}$/)) return "请填写已核实的订单记录编号（正整数）";
  }
  return "";
}
export function leidianResolutionPayload(action, value) {
  const body = { outcome: value.outcome, evidence: value.evidence, upstreamChecked: value.upstreamChecked };
  if (action === "CREATE" && value.outcome === "ACCEPTED") {
    body.externalOrderNo = value.externalOrderNo;
    body.externalSubOrderNo = value.externalSubOrderNo;
  }
  return body;
}
