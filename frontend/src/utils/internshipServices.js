export const internshipProjects = Object.freeze({
  zxjy: "职校家园",
  qzt: "黔职通",
  gxy: "工学云",
  xyb: "校友帮",
  xxy: "习迅云 / 宁夏",
  xxt: "学习通",
  hzj: "慧职教",
  gxzy: "广西职业",
  jxzhjy: "江西智慧教育",
});
export const internshipFieldKeys = Object.freeze([
  "account",
  "password",
  "name",
  "gwName",
  "customizedGwName",
  "schoolId",
  "school",
  "schoolName",
  "projectName",
  "address",
  "addressOld",
  "officialAddress",
  "jobAddress",
  "lat",
  "lng",
  "country",
  "province",
  "city",
  "area",
  "adcode",
  "phone_name",
  "reason",
  "desctext",
  "up_remark",
  "down_remark",
]);
export function newInternshipSchedule() {
  return {
    endDate: "",
    weekdays: [1, 2, 3, 4, 5],
    checkInTime: "08:00:00",
    checkOutTime: "18:00:00",
    runMode: 1,
    dailyReport: false,
    weeklyReport: false,
    monthlyReport: false,
    skipHolidays: false,
    randomLocation: false,
    weeklyReportDay: 7,
    monthlyReportDay: 0,
    reportLengths: {
      day: { minSize: 0, maxSize: 0 },
      week: { minSize: 0, maxSize: 0 },
      month: { minSize: 0, maxSize: 0 },
      summary: { minSize: 0, maxSize: 0 },
    },
  };
}
export function internshipFields(fields, editing = false) {
  return Object.fromEntries(
    internshipFieldKeys
      .filter(
        (key) => (!editing || key !== "account") && fields[key] !== undefined,
      )
      .map((key) => [key, String(fields[key] ?? "")]),
  );
}
export function internshipReportTypes(project) {
  return [
    ...(project === "qzt" ? [] : ["日报"]),
    "周报",
    "月报",
    ...(project === "gxy" ? ["上班打卡", "下班打卡", "上下班打卡"] : []),
  ];
}
export const internshipCalendarText = (schedule) =>
  schedule
    ? `周${(schedule.weekdays || []).map((day) => ["一", "二", "三", "四", "五", "六", "日"][day - 1]).join(" / ")} · ${schedule.checkInTime || ""}${schedule.checkOutTime ? ` – ${schedule.checkOutTime}` : ""}`
    : "";

export function beijingToday(now = new Date()) {
  return new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Shanghai" }).format(
    now,
  );
}
export function pickerCalendarDate(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
export function internshipEndDateDisabled(date, now = new Date()) {
  const today = beijingToday(now);
  const last = new Date(Date.parse(`${today}T00:00:00Z`) + 364 * 86400000)
    .toISOString()
    .slice(0, 10);
  const selected = pickerCalendarDate(date);
  return selected < today || selected > last;
}
export function eligibleInternshipDates(options, now = new Date()) {
  const today = beijingToday(now);
  return [...new Set(options?.paidDates || [])]
    .filter(
      (day) =>
        /^\d{4}-\d{2}-\d{2}$/.test(day) &&
        day <= today &&
        day <= (options?.schedule?.endDate || ""),
    )
    .sort();
}
export function internshipReportRangeError(form, paidDates) {
  const { startDate: first, endDate: last } = form;
  const validDate = (value) =>
    /^\d{4}-\d{2}-\d{2}$/.test(value || "") &&
    Number.isFinite(Date.parse(`${value}T00:00:00Z`)) &&
    new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value;
  if (!validDate(first) || !validDate(last) || first > last)
    return "请选择有效的开始和结束日期";
  const start = Date.parse(`${first}T00:00:00Z`),
    end = Date.parse(`${last}T00:00:00Z`);
  if (end - start > 364 * 86400000) return "单次补交范围最多 365 天";
  const paid = new Set(paidDates);
  for (let day = start; day <= end; day += 86400000) {
    if (!paid.has(new Date(day).toISOString().slice(0, 10)))
      return "补交范围包含未购买或未到期的日期，请按连续已购服务日分段提交";
  }
  return "";
}

// Lookup advice is not an entitlement and never changes a plan until the user applies it.
export function internshipAdviceItems(advice) {
  if (!advice) return [];
  const labels = { checkInTime: '建议上班时间', checkOutTime: '建议下班时间', endDate: '上游截止日期', weekdays: '建议执行周期', dailyReport: '日报', weeklyReport: '周报', monthlyReport: '月报' };
  return Object.entries(labels).flatMap(([key,label]) => {
    const value=advice[key];
    if(value===null||value===undefined)return [];
    const text=key==='weekdays' ? (Array.isArray(value)?value.map(d=>['一','二','三','四','五','六','日'][d-1]).join(' / '):'格式待核实') : typeof value==='boolean' ? (value?'需要':'不需要') : String(value);
    return [{key,label,value:text}];
  });
}
export function applyInternshipAdvice(current, advice, now=new Date()) {
  const next={...current};
  if(!advice)return next;
  for(const key of ['checkInTime','checkOutTime'])if(/^(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$/.test(advice[key]||''))next[key]=advice[key];
  for(const key of ['dailyReport','weeklyReport','monthlyReport'])if(typeof advice[key]==='boolean')next[key]=advice[key];
  if(Array.isArray(advice.weekdays)&&advice.weekdays.length&&advice.weekdays.length<=7&&advice.weekdays.every(d=>Number.isInteger(d)&&d>=1&&d<=7)&&new Set(advice.weekdays).size===advice.weekdays.length)next.weekdays=[...advice.weekdays];
  const date=advice.endDate;
  if(/^\d{4}-\d{2}-\d{2}$/.test(date||'')){
    const timestamp=Date.parse(`${date}T00:00:00Z`), today=beijingToday(now);
    const last=new Date(Date.parse(`${today}T00:00:00Z`)+364*86400000).toISOString().slice(0,10);
    if(Number.isFinite(timestamp)&&new Date(timestamp).toISOString().slice(0,10)===date&&date>=today&&date<=last)next.endDate=date;
  }
  return next;
}
