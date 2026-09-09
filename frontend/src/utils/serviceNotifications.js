export const notificationStates = Object.freeze({
  READY: "等待安全派发",
  DISPATCHING: "发送结果待确认",
  ACCEPTED: "ShowDoc 已受理",
  REJECTED: "ShowDoc 拒绝",
  UNKNOWN: "发送结果未知",
  CANCELLED: "发送前已取消",
  EXPIRED: "发送窗口已过期",
});
export const notificationState = (value) =>
  notificationStates[value] || "待核实";
export const notificationTokenValid = (value) =>
  /^[A-Za-z0-9_-]{16,128}$/.test(String(value || ""));
export const notificationCanChallenge = (value) =>
  !!value?.configured &&
  !!value.deliveryAvailable &&
  !value.verified &&
  !value.challengeDeliveryId;
export const notificationKind = (value) =>
  value === "VERIFY_RECEIVER" ? "接收方式验证" : "订单状态更新";
