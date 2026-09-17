// API local timestamps may use a space (Jackson) or T (ISO LocalDateTime).
// Validate the wall-clock fields directly, without browser-dependent parsing
// or a conversion from the server's declared timezone to the device timezone.
export function validLocalDateTime(value) {
  if (typeof value !== 'string') return false
  const match = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2}):(\d{2})(?:\.\d{1,9})?$/.exec(value)
  if (!match || match[0] !== value) return false
  const [year, month, day, hour, minute, second] = match.slice(1).map(Number)
  if (month < 1 || month > 12 || day < 1 || hour > 23 || minute > 59 || second > 59) return false
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
  return day <= [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1]
}
