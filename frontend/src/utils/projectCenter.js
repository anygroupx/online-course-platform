import { serviceAccountExpired } from "./serviceCommerce.js";
export const projectActions = Object.freeze({
  PROVISION: "开通项目账户",
  TOP_UP: "充值项目额度",
  WITHDRAW: "转回平台余额",
});
export const projectStates = Object.freeze({
  NEW: "未开通",
  ACTIVE: "已开通",
  DISABLED: "服务已停用",
  BUSY: "操作待确认",
  READY: "待确认",
  DISPATCHING: "正在核实",
  UNKNOWN: "待人工核对",
  NETWORK_UNKNOWN: "提交结果待确认",
  SUCCEEDED: "已完成",
  NOT_ACCEPTED: "已确认未受理",
  EXPIRED: "预览已过期",
});
export const projectState = (value) => projectStates[value] || "待核对";
export const projectAmount = (value) =>
  /^\d{1,12}(?:\.\d{1,6})?$/.test(String(value ?? "")) ? String(value) : "—";
function decimal6(value) {
  if (!/^\d{1,12}(?:\.\d{1,6})?$/.test(String(value ?? ""))) return null;
  const [integer, fraction = ""] = String(value).split(".");
  return BigInt(integer) * 1000000n + BigInt(fraction.padEnd(6, "0"));
}
export function estimateProjectTransfer(units, rate, withdraw = false) {
  const quantity = decimal6(units),
    price = decimal6(rate);
  if (
    quantity === null ||
    price === null ||
    quantity <= 0n ||
    quantity > 100000000000n ||
    price <= 0n
  )
    return "—";
  const product = quantity * price,
    divisor = 10000000000n;
  const cents = withdraw
    ? product / divisor
    : (product + divisor - 1n) / divisor;
  return `${cents / 100n}.${String(cents % 100n).padStart(2, "0")}`;
}
export function projectActionAllowed(project, action) {
  if (!project || !projectActions[action]) return false;
  const account = project.account;
  if (action === "PROVISION")
    return project.available && (!account || account.state === "NEW");
  if (account?.state !== "ACTIVE" || account.pendingOperationId) return false;
  if (action === "TOP_UP") return project.available;
  return (
    (decimal6(account.refundableUnits) ?? 0n) > 0n &&
    (decimal6(account.refundBudget) ?? 0n) > 0n
  );
}
export function projectQuoteReady(quote, now = Date.now()) {
  return (
    quote?.state === "READY" &&
    !!projectActions[quote.action] &&
    !serviceAccountExpired(quote, now)
  );
}
