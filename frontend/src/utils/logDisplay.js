const number = (value) => value == null || value === '' || !Number.isFinite(Number(value)) ? null : Number(value);
export function formatAmountChange(value) {
  const amount = number(value);
  return amount == null ? '-' : `${amount > 0 ? '+' : ''}${amount.toFixed(2)}`;
}
export function formatBalanceAfter(value) {
  const amount = number(value);
  return amount == null ? '-' : `¥${amount.toFixed(2)}`;
}
export function getAmountChangeClass(value) {
  const amount = number(value);
  return amount > 0 ? 'is-positive' : amount < 0 ? 'is-negative' : 'is-neutral';
}
export function getOperationTypeTagType(type) {
  if (['充值', '登录'].includes(type)) return 'success';
  if (['补单', '创建订单', '下单'].includes(type)) return 'warning';
  if (['删除', '禁用'].includes(type)) return 'danger';
  return 'info';
}
export function formatLogDateTime(value) {
  if (!value) return '-';
  const date = new Date(typeof value === 'string' ? value.replace(' ', 'T') : value);
  if (Number.isNaN(date.getTime())) return '-';
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(date);
}
