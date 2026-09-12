const beijing = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Asia/Shanghai",
  year: "numeric", month: "2-digit", day: "2-digit",
  hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23",
});

// Service timestamps without an offset are Beijing wall time, never the browser's local time.
export function statusCheckTime(value) {
  if (typeof value !== "string") return null;
  const match = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.\d{1,9})?(Z|[+-]\d{2}:\d{2})?$/.exec(value);
  if (!match) return null;
  const wall = new Date(`${match[1]}Z`);
  if (!Number.isFinite(wall.getTime()) || wall.toISOString().slice(0, 19) !== match[1]) return null;
  const offset = match[2] || "+08:00";
  if (offset !== "Z") {
    const hours = Number(offset.slice(1, 3)), minutes = Number(offset.slice(4, 6));
    if (hours > 14 || minutes > 59 || (hours === 14 && minutes !== 0)) return null;
  }
  const instant = new Date(`${match[1]}${offset}`);
  if (!Number.isFinite(instant.getTime())) return null;
  const parts = Object.fromEntries(beijing.formatToParts(instant).map(({ type, value }) => [type, value]));
  if (!/^\d{4}$/.test(parts.year)) return null;
  const text = `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`;
  return { text, datetime: `${text.replace(" ", "T")}+08:00` };
}

export function statusCheckDisplay(check) {
  if (!check || typeof check !== "object" || Array.isArray(check) || typeof check.delayed !== "boolean") return null;
  return { time: statusCheckTime(check.checkedAt), delayed: check.delayed };
}

const progressLabels = Object.freeze({
  region: "进度核对情况",
  heading: "最近核对",
  note: "核对时间不代表执行时间。",
  delayed: "暂未取得最新进度，已保留此前结果。",
});
const planLabels = Object.freeze({
  region: "计划状态核对情况",
  heading: "最近核对计划状态",
  note: "仅核对计划状态，考勤结果请查看执行记录。",
  delayed: "暂未取得最新计划状态，已保留此前结果。",
});
const dayLabels = Object.freeze({
  region: "天数与状态核对情况",
  heading: "最近核对天数与状态",
  note: "已使用天数不代表签到成功，具体结果请查看执行记录。",
  delayed: "暂未取得最新天数与状态，已保留此前结果。",
});
const runLabels = Object.freeze({
  region: "次数与状态核对情况",
  heading: "最近核对次数与状态",
  note: "已使用次数不代表跑步或成绩完成，具体结果请查看执行记录和成绩查询信息。",
  delayed: "暂未取得最新次数与状态，已保留此前结果。",
});
const taskLabels = Object.freeze({
  region: "任务完成情况", heading: "最近核对任务",
  note: "完成次数按成功任务核对；任务显示退款不代表余额已退回，请查看退款记录。",
  delayed: "暂未取得最新任务状态，已保留此前结果。",
});
export function statusCheckLabels(providerType) {
  if (providerType === "jingyu") return taskLabels;
  if (providerType === "leidian") return runLabels;
  return providerType === "appui" ? dayLabels : providerType === "sxdk_tw" ? planLabels : progressLabels;
}
