export const clientStates = Object.freeze({
  ACTIVE: "可用",
  SUSPENDED: "已暂停",
  CLOSED: "已关闭",
  READY: "待确认",
  APPLIED: "已结算",
  STALE: "已变化 · 未扣款",
  EXPIRED: "已过期 · 未扣款",
});
export const clientActions = Object.freeze({
  OPEN: "开通客户",
  TOP_UP: "充值本地额度",
  WITHDRAW: "转回平台余额",
});
export const clientState = (value) => clientStates[value] || "待核对";
export function clientUnitsValid(value, opening = false) {
  if (!/^\d{1,6}(?:\.\d{1,6})?$/.test(String(value))) return false;
  const [whole, fraction = ""] = String(value).split(".");
  const n = BigInt(whole) * 1000000n + BigInt(fraction.padEnd(6, "0"));
  return n <= 100000n * 1000000n && (opening ? n >= 0n : n > 0n);
}
export const clientActionAllowed = (row, action) =>
  action === "TOP_UP"
    ? row?.status === "ACTIVE"
    : action === "WITHDRAW"
      ? ["ACTIVE", "SUSPENDED"].includes(row?.status) &&
        /^\d{1,14}(?:\.\d{1,6})?$/.test(String(row?.balance)) &&
        /[1-9]/.test(String(row.balance))
      : false;
