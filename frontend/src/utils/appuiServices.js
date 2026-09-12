export const appuiProjects = Object.freeze({
  1: "校友邦", 2: "职校家园", 3: "慧职教", 4: "黔职通", 5: "学习通",
  6: "习行学生版", 7: "工学云", 8: "习讯云", 9: "广西职业院校公众号",
});
export const appuiNeedsSchool = (project) => ["3", "6", "8"].includes(String(project));
const planKeys = ["address", "startTime", "endTime", "weekdays", "reports"];
const controls = /\p{Cc}/u;
function validSelection(value, max) {
  if (typeof value !== "string" || controls.test(value) || !new RegExp(`^[1-${max}](?:,[1-${max}]){0,${max - 1}}$`).test(value)) return false;
  const items = value.split(",");
  return new Set(items).size === items.length;
}
export function appuiPlanError(fields = {}) {
  if (!fields || typeof fields.address !== "string" || !fields.address.trim() || fields.address.trim().length > 500 || controls.test(fields.address))
    return "请填写不超过 500 字的打卡地址";
  const clock = /^(?:[01]\d|2[0-3]):[0-5]\d$/;
  if (typeof fields.startTime !== "string" || typeof fields.endTime !== "string" || fields.startTime.length !== 5 || fields.endTime.length !== 5 || !clock.test(fields.startTime) || !clock.test(fields.endTime)) return "请选择上班和下班时间";
  if (fields.startTime >= fields.endTime) return "下班时间须晚于上班时间";
  if (!validSelection(fields.weekdays, 7)) return "请至少选择一个执行星期，不要重复选择";
  if (!validSelection(fields.reports, 3)) return "请至少选择一种报告";
  return "";
}
// Existing arrangements are read back as-is; never import credentials or use checkout defaults.
export function editableAppuiPlan(suggested = {}) {
  return { ...Object.fromEntries(planKeys.map((key) => [key,
    typeof suggested?.[key] === "string" ? suggested[key] : ""])), password: "" };
}
export function appuiRemainingText(order) {
  if (["REFUNDED", "CANCELLED"].includes(order?.status)) return "已结束";
  if (order?.pendingOperationId || !["ACTIVE", "COMPLETED"].includes(order?.status) ||
      !Number.isInteger(order.quantity) || order.quantity < 1 || order.quantity > 9999 ||
      !Number.isInteger(order.completed) || order.completed < 0 || order.completed > order.quantity) return "待核对";
  return `${order.quantity - order.completed} 天`;
}
