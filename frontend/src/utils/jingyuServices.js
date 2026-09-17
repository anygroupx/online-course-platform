import { beijingToday } from "./internshipServices.js";

export const jingyuProjects = Object.freeze({ keep: "Keep 自由跑", bdlp: "步道乐跑", yyd: "校园运动" });
export const isJingyuService = (value) => value?.providerType === "jingyu";
const matches = (value, pattern) => typeof value === "string" && pattern.test(value) && !/\p{Cc}/u.test(value);
const text = (value, max) => typeof value === "string" && value.trim().length > 0 && value.length <= max && !/\p{Cc}/u.test(value);
const object = (value) => value !== null && typeof value === "object" && !Array.isArray(value);
const identifier = (value) => matches(value, /^[1-9][0-9]{0,18}$/);
const distanceValid = (value) => matches(value, /^(?:[1-9][0-9]?(?:\.\d)?|100(?:\.0)?)$/);
const validDate = (value) => matches(value, /^\d{4}-\d{2}-\d{2}$/) &&
  Number.isFinite(Date.parse(`${value}T00:00:00Z`)) && new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value;
export function jingyuClock(value) {
  return matches(value, /^(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?$/) ? value.length === 5 ? `${value}:00` : value : "";
}
export function jingyuDateRange(now = new Date()) {
  const instant = new Date(now);
  if (!Number.isFinite(instant.getTime())) return { min: "", max: "" };
  const min = beijingToday(instant), [year, month, day] = min.split("-").map(Number);
  const last = new Date(Date.UTC(year + 1, month, 0)).getUTCDate();
  return { min, max: `${year + 1}-${String(month).padStart(2, "0")}-${String(Math.min(day, last)).padStart(2, "0")}` };
}
export const jingyuTimestampValid = (value) => typeof value === "string" && value.length === 19 && value[10] === " " &&
  validDate(value.slice(0, 10)) && jingyuClock(value.slice(11)) === value.slice(11);
export function jingyuTaskTimeError(date, time, now = new Date()) {
  const clock = jingyuClock(time), instant = new Date(now);
  if (!validDate(date) || !clock || !Number.isFinite(instant.getTime())) return "请填写有效的任务日期和时间";
  const upperClock = new Date(instant.getTime() + 8 * 3600000).toISOString().slice(11, 23);
  const timestamp = Date.parse(`${date}T${clock}+08:00`);
  if (timestamp <= instant.getTime() || timestamp > Date.parse(`${jingyuDateRange(instant).max}T${upperClock}+08:00`))
    return "请选择未来一年内的任务时间（北京时间）";
  return "";
}
export function jingyuTaskTimesError(times, quantity, now = new Date()) {
  if (!Number.isInteger(quantity) || quantity < 1 || quantity > 365) return "购买次数须为 1–365 的整数";
  if (!Array.isArray(times) || times.length !== quantity) return `请安排 ${quantity} 条任务，每次购买对应一条任务`;
  if (new Set(times).size !== times.length) return "任务时间不能重复，请调整后再预览";
  for (let i = 0; i < times.length; i++) {
    if (!jingyuTimestampValid(times[i])) return `第 ${i + 1} 条任务的日期或时间不完整`;
    const error = jingyuTaskTimeError(times[i].slice(0, 10), times[i].slice(11), now);
    if (error) return `第 ${i + 1} 条任务：${error}`;
  }
  return "";
}
export function jingyuAccountError(project, fields = {}) {
  if (!Object.hasOwn(jingyuProjects, project)) return "请选择有效的运动项目";
  if (project === "bdlp") return identifier(fields?.account) ? "" : "请填写已授权的账号 UID（1–19 位正整数）";
  if (project === "yyd") {
    if (!identifier(fields?.schoolId) || !text(fields?.schoolName, 80) || fields.schoolName !== fields.schoolName.trim())
      return "请先查询并选择学校";
    if (!matches(fields?.account, /^[A-Za-z0-9_-]{1,64}$/)) return "请填写有效学号，支持字母、数字、短横线和下划线";
  } else if (!matches(fields?.account, /^[0-9]{7,15}$/)) return "请填写 7–15 位数字手机号";
  return text(fields?.password, 128) ? "" : "请填写 1–128 字的账号密码，不支持控制字符";
}
const formKeys = Object.freeze({ keep: ["account", "password", "zoneId", "minMinute", "maxMinute"], bdlp: ["account", "zoneId", "runType"],
  yyd: ["schoolId", "schoolName", "account", "password", "runRuleId"] });
// UID and passwords stay strings; never round long IDs or trim meaningful password spaces.
export function jingyuLookupFields(project, fields = {}) {
  if (!object(fields)) return {};
  return Object.fromEntries((project === "yyd" ? ["schoolId", "schoolName", "account", "password"]
    : project === "keep" ? ["account", "password"] : project === "bdlp" ? ["account"] : [])
    .filter((key) => Object.hasOwn(fields, key) && typeof fields[key] === "string").map((key) => [key, fields[key]]));
}
export function jingyuFormFields(project, fields = {}) {
  if (!object(fields) || !Object.hasOwn(formKeys, project)) return {};
  return Object.fromEntries(formKeys[project].filter((key) => Object.hasOwn(fields, key) && typeof fields[key] === "string").map((key) => [key, fields[key]]));
}
export function jingyuLookupValid(project, lookup, fields) {
  if (!Object.hasOwn(jingyuProjects, project) || !object(lookup?.suggested) || !Array.isArray(lookup.choices) ||
      lookup.choices.length < 1 || lookup.choices.length > 200 ||
      !lookup.choices.every((choice) => choice?.field === (project === "yyd" ? "runRuleId" : "zoneId") && identifier(choice.value) && text(choice.label, 100)) ||
      new Set(lookup.choices.map((choice) => choice.value)).size !== lookup.choices.length) return false;
  const facts = lookup.suggested;
  if (project === "keep") return Object.keys(facts).length === 0;
  if (project === "yyd") return Object.keys(facts).length === 2 && identifier(facts.schoolId) && text(facts.schoolName, 80) &&
    facts.schoolName === facts.schoolName.trim() && (fields === undefined || fields?.schoolId === facts.schoolId && fields?.schoolName === facts.schoolName) &&
    Array.isArray(lookup.schoolRules) && lookup.schoolRules.length === lookup.choices.length &&
    new Set(lookup.schoolRules.map((rule) => rule?.id)).size === lookup.schoolRules.length &&
    lookup.schoolRules.every((rule) => identifier(rule?.id) && identifier(rule.zoneId) && text(rule.zoneName, 100) && distanceValid(rule.minDistance) &&
      lookup.choices.some((choice) => choice.value === rule.id && choice.label === rule.zoneName));
  return Object.keys(facts).length === 5 && text(facts.schoolName, 100) && distanceValid(facts.minDistance) &&
    ["VALID", "EXPIRED"].includes(facts.authorizationState) && text(facts.authorizationType, 100) && jingyuTimestampValid(facts.authorizedAt);
}
export function jingyuOrderError(product, fields, quantity, distance, lookup, times, now = new Date()) {
  if (!isJingyuService(product) || !Object.hasOwn(jingyuProjects, product.project) || product.project !== product.remoteProductId)
    return "此商品暂不能购买，请重新选择运动项目";
  const accountError = jingyuAccountError(product.project, fields);
  if (accountError) return accountError;
  if (!jingyuLookupValid(product.project, lookup, fields)) return product.project === "yyd" ? "请先查询账号与规则" : "请先查询账号与跑区";
  if (product.project === "bdlp" && lookup.suggested.authorizationState !== "VALID") return "账号授权已失效，请完成授权后重新查询";
  if (product.project === "yyd") {
    const rule = lookup.schoolRules.find((item) => item.id === fields.runRuleId);
    if (!rule) return "请选择学校对应的跑步规则";
    if (distanceValid(distance) && Number(distance) < Number(rule.minDistance)) return `所选规则要求每次至少 ${rule.minDistance} 公里`;
  } else if (!lookup.choices.some((choice) => choice.value === fields.zoneId)) return "请选择查询结果中的跑区";
  if (product.project === "bdlp" && !["1", "2"].includes(fields.runType)) return "请选择有效跑或自由跑";
  if (product.project === "keep" && (!matches(fields.minMinute, /^[3-6]$/) || !matches(fields.maxMinute, /^(?:[89]|1[0-5])$/)))
    return "请设置配速：最快 3–6 分钟/公里，最慢 8–15 分钟/公里";
  if (!distanceValid(distance)) return "每次距离须为 1–100 公里，最多一位小数";
  return jingyuTaskTimesError(times, quantity, now);
}
export function jingyuLogsValid(value, page) {
  return Number.isInteger(page) && page >= 1 && page <= 19 && value?.page === page && typeof value.hasMore === "boolean" &&
    (page < 19 || !value.hasMore) && Array.isArray(value.items) && value.items.length <= (page === 19 ? 5 : 20) &&
    (!value.hasMore || value.items.length === 20) && value.items.every((row) => matches(row?.id, /^[A-Za-z0-9_-]{1,64}$/) &&
      jingyuTimestampValid(row.time) && ["待执行", "已完成", "已退款", "需要关注"].includes(row.status) &&
      row.editable === (row.status === "待执行") && (row.endTime == null || jingyuTimestampValid(row.endTime))) &&
    new Set(value.items.map((row) => row.id)).size === value.items.length;
}
export function jingyuTaskEditable(order, logs, row, action) {
  return isJingyuService(order) && !order.pendingOperationId && ["ACTIVE", "PAUSED", "ATTENTION"].includes(order.status) &&
    ["CHANGE_TIME", "DELAY_TASK"].includes(action) && Array.isArray(order.actions) && order.actions.includes(action) && jingyuLogsValid(logs, logs?.page) &&
    logs.items.includes(row) && row.editable === true;
}
export function jingyuResolutionError(action, value = {}, quantity) {
  if (!["CREATE", "PAUSE", "RESUME", "DELAY", "DELAY_TASK", "CHANGE_TIME", "REFUND"].includes(action) || !object(value) ||
      !["ACCEPTED", "NOT_ACCEPTED"].includes(value.outcome)) return "请选择已核实的处理结果";
  if (value.upstreamChecked !== true || !text(value.evidence, 1000) || value.evidence.trim().length < 10)
    return "请核实处理结果并填写至少 10 字的核对依据";
  if (value.outcome === "ACCEPTED") {
    if (action === "CREATE") {
      if (!matches(value.externalOrderNo, /^[A-Za-z0-9_-]{1,64}$/)) return "请填写已核实的服务订单号";
      if (!identifier(value.externalSubOrderNo)) return "请填写已核实的订单记录编号（正整数）";
    }
    if (action === "REFUND" && (!Number.isInteger(quantity) || quantity < 0 || quantity > 365 ||
        !Number.isInteger(value.refundedUnits) || value.refundedUnits < 0 || value.refundedUnits > quantity))
      return "请填写已核实的退款次数，不得使用预估次数";
  }
  return "";
}
export function jingyuResolutionPayload(action, value) {
  if (!object(value) || !["CREATE", "PAUSE", "RESUME", "DELAY", "DELAY_TASK", "CHANGE_TIME", "REFUND"].includes(action)) return {};
  const body = { outcome: value.outcome, evidence: value.evidence, upstreamChecked: value.upstreamChecked };
  if (value.outcome === "ACCEPTED") {
    if (action === "CREATE") { body.externalOrderNo = value.externalOrderNo; body.externalSubOrderNo = value.externalSubOrderNo; }
    if (action === "REFUND") body.refundedUnits = value.refundedUnits;
  }
  return body;
}
