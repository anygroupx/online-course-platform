// API local timestamps may use a space (Jackson) or T (ISO LocalDateTime).
// Validate the wall-clock fields directly, without browser-dependent parsing
// or a conversion from the server's declared timezone to the device timezone.
function validCalendarDate(year, month, day) {
  if (month < 1 || month > 12 || day < 1) return false
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
  return day <= [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1]
}

export function validLocalDateTime(value) {
  if (typeof value !== 'string') return false
  const match = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2}):(\d{2})(?:\.\d{1,9})?$/.exec(value)
  if (!match || match[0] !== value) return false
  const [year, month, day, hour, minute, second] = match.slice(1).map(Number)
  if (hour > 23 || minute > 59 || second > 59) return false
  return validCalendarDate(year, month, day)
}

// 只取日期部分（yyyy-MM-dd），用于执行记录这类只需精确到天的展示。
// 直接切字符串，不做 Date 解析或时区换算，避免跨时区把日期显示错一天。
// 输入必须是平台统一的 LocalDateTime（Jackson 格式），否则返回空串。
export function localDateOnly(value) {
  if (!validLocalDateTime(value)) return ''
  return value.slice(0, 10)
}

// 上游供应商的执行记录时间不是平台统一的 LocalDateTime，而是各网关透传的字符串
// （"yyyy-MM-dd HH:mm:ss"、"yyyy-MM-ddTHH:mm:ss"、带 Z 或 +08:00 后缀等）。
// 这里能识别出日期前缀就只显示日期；识别不了就原样返回，绝不把有内容的值显示成空。
export function displayDateOnly(value) {
  if (typeof value !== 'string') return ''
  const match = /^(\d{4})-(\d{2})-(\d{2})(?=[ T]|$)/.exec(value)
  if (!match) return value
  const [year, month, day] = match.slice(1).map(Number)
  return validCalendarDate(year, month, day) ? match[0] : value
}
