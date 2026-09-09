import { internshipFields, internshipProjects } from "./internshipServices.js";
export const serviceNames = Object.freeze({
  flash: "闪电",
  heisha: "黑鲨",
  jiguang: "极光",
  wuxin: "无心",
  sxdk_tw: "实习服务",
});
export const actionNames = Object.freeze({
  CREATE: "下单",
  SYNC: "更新进度",
  ADD_TIMES: "增加次数",
  REFUND: "取消并退款",
  SETTLE_REFUND: "退款入账",
  PAUSE: "暂停",
  RESUME: "恢复",
  DELAY: "延期",
  DELAY_TASK: "延期此任务",
  CHANGE_TIME: "修改任务时间",
  EDIT_PLAN: "编辑计划",
  REASSIGN: "重新分配",
  EDIT_SCHEDULE: "编辑周期 / 续期",
  RUN_NOW: "立即执行",
  REPORT: "补交记录",
});
export const stateNames = Object.freeze({
  READY: "待确认",
  DISPATCHING: "正在确认",
  UNKNOWN: "待人工核对",
  SUCCEEDED: "已受理",
  NOT_ACCEPTED: "未受理",
  EXPIRED: "预览已过期",
  ACTIVE: "进行中",
  PAUSED: "已暂停",
  COMPLETED: "已完成",
  REFUNDED: "已退款",
  CANCELLED: "已取消",
  CONFIRMING: "操作待确认",
  REFUND_REVIEW: "退款待核对",
  ATTENTION: "需要处理",
  SUBMITTING: "提交中",
});
export const stateName = (state) => stateNames[state] || "待核对";
export const fieldNames = Object.freeze({
  runType: "服务类型",
  runRuleId: "执行计划",
  zoneId: "服务区域",
  fenceId: "服务区域",
  planOptionId: "执行计划",
  fenceOptionId: "服务区域",
  runPlanCode: "执行计划",
  fenceCode: "服务区域",
});
export const knownOutcome = (state) =>
  ["SUCCEEDED", "NOT_ACCEPTED", "EXPIRED"].includes(state);
export const moneyText = (value) =>
  /^\d{1,10}(?:\.\d{1,6})?$/.test(String(value ?? "")) ? String(value) : "—";
export function buildTaskTimes(
  date,
  time,
  quantity,
  weekdays = [0, 1, 2, 3, 4, 5, 6],
) {
  if (
    !/^\d{4}-\d{2}-\d{2}$/.test(date || "") ||
    !/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(time || "") ||
    !Number.isInteger(quantity) ||
    quantity < 1 ||
    quantity > 365 ||
    !weekdays.length
  )
    return [];
  const first = new Date(`${date}T00:00:00Z`);
  if (
    Number.isNaN(first.getTime()) ||
    first.toISOString().slice(0, 10) !== date
  )
    return [];
  const result = [];
  for (let day = 0; day < 366 && result.length < quantity; day++) {
    const current = new Date(first.getTime() + day * 86400000);
    if (weekdays.includes(current.getUTCDay()))
      result.push(`${current.toISOString().slice(0, 10)} ${time}:00`);
  }
  return result.length === quantity ? result : [];
}

const formKeys = Object.freeze({
  jiguang: ["schoolName", "studentName", "studentAccount", "message"],
  heisha: ["account", "password", "planOptionId", "fenceOptionId", "runTime"],
  flash: [
    "account",
    "password",
    "schoolName",
    "runType",
    "runRuleId",
    "zoneId",
    "fenceId",
    "repair",
  ],
  wuxin: [
    "authCode",
    "runPlanCode",
    "fenceCode",
    "runType",
    "startDate",
    "runTime",
    "endTime",
    "weekdays",
    "pace",
    "message",
  ],
});
export function serviceFormFields(type, fields) {
  if (type === "sxdk_tw") return internshipFields(fields);
  return Object.fromEntries(
    (formKeys[type] || [])
      .filter((key) => fields[key] !== undefined)
      .map((key) => [key, String(fields[key] ?? "")]),
  );
}

// Never populate an existing plan with checkout defaults or reuse credentials from another form.
export function editableWuxinPlan(suggested = {}, distance = "") {
  const keys = [
    "runPlanCode",
    "fenceCode",
    "runType",
    "runTime",
    "endTime",
    "weekdays",
    "pace",
    "message",
    "distance",
  ];
  const fields = Object.fromEntries(
    keys.map((key) => [key, String(suggested[key] ?? "")]),
  );
  if (!fields.distance) fields.distance = String(distance);
  return fields;
}

// Catalog rows are not an authorization to sell unknown product IDs.
export function nativeProductSupported(type, project, id) {
  if (type === "sxdk_tw")
    return Object.hasOwn(internshipProjects, project) && project === id;
  if (type === "wuxin") return project === "sdxy" && id === "sdxy";
  if (type === "flash")
    return ["sdxy", "ydsjxy", "xbd"].includes(project) && id === project;
  if (type === "heisha")
    return project === "default" && ["1", "2", "3", "4"].includes(id);
  return type === "jiguang" && project === "default" && ["1", "2"].includes(id);
}
export function maxRefundableUnits(order) {
  return Math.max(0, order.quantity - (order.completed ?? 0));
}

export function serviceAccountExpired(session, now = Date.now()) {
  const value = session?.expiresAt;
  if (
    typeof value !== "string" ||
    !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})?$/.test(
      value,
    )
  )
    return true;
  const timestamp = /(?:Z|[+-]\d{2}:\d{2})$/.test(value)
    ? value
    : `${value}+08:00`;
  const deadline = Date.parse(timestamp.replace(/(\.\d{3})\d+/, "$1"));
  return !Number.isFinite(deadline) || deadline <= now;
}

export function isHeishaFaceService(product) {
  return (
    product?.providerType === "heisha" &&
    product.project === "default" &&
    ["3", "4"].includes(String(product.remoteProductId))
  );
}
export function usesServiceAccountSession(product) {
  return product?.providerType === "flash" || isHeishaFaceService(product);
}
export function validFaceLaunchTicket(value, sessionId, now = Date.now()) {
  return (
    value?.sessionId === sessionId &&
    /^[0-9a-f]{64}$/.test(value?.ticket || "") &&
    !serviceAccountExpired(value, now)
  );
}
