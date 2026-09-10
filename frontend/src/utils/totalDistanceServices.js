export const totalDistanceType = "ssbenz_xbd";
export const isTotalDistanceService = (value) =>
  (typeof value === "string" ? value : value?.providerType) === totalDistanceType;

// Validate exact hundredths without converting business quantities to binary floating point.
export function totalDistanceValid(value) {
  const raw = String(value ?? "");
  if (!/^\d{1,6}(?:\.\d{1,2})?$/.test(raw)) return false;
  const [whole, fraction = ""] = raw.split(".");
  const hundredths = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
  return hundredths >= 1n && hundredths <= 99999999n;
}
export function distancePlanError(distance, fields = {}) {
  if (!totalDistanceValid(distance)) return "总公里数须为 0.01–999999.99，最多两位小数";
  const clock = /^(?:0[6-9]|1\d|2[0-2]):[0-5]\d$/;
  if (!clock.test(fields.startTime || "") || !clock.test(fields.endTime || ""))
    return "请选择 06:00–22:59 内的开始与结束时间";
  if (fields.startTime >= fields.endTime) return "结束时间须晚于开始时间，不能跨天";
  const weeks = String(fields.weekdays ?? "");
  if (!/^[1-7](?:,[1-7]){0,6}$/.test(weeks) || new Set(weeks.split(",")).size !== weeks.split(",").length)
    return "请至少选择一个星期，不可重复";
  return "";
}
export function distanceOrderValid(distance, fields = {}) {
  return !distancePlanError(distance, fields) &&
    typeof fields.account === "string" && !!fields.account.trim() && fields.account.length <= 100 &&
    typeof fields.password === "string" && !!fields.password.trim() && fields.password.length <= 200 &&
    String(fields.schoolName ?? "").length <= 120 &&
    [fields.account, fields.password, fields.schoolName || ""].every((value) => !/[\u0000-\u001f\u007f-\u009f]/.test(value));
}
export function distanceWeekdaysText(weekdays) {
  if (!Array.isArray(weekdays) || !weekdays.length || weekdays.length > 7 ||
      new Set(weekdays).size !== weekdays.length || weekdays.some((day) => !Number.isInteger(day) || day < 1 || day > 7))
    return "星期待核对";
  const labels = ["", "一", "二", "三", "四", "五", "六", "日"];
  return [...weekdays].sort((a, b) => a - b).map((day) => `周${labels[day]}`).join("、");
}
