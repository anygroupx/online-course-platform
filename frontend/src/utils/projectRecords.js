import { validProjectReport } from './projectReports.js'

const count = (v) => Number.isSafeInteger(v) && v >= 0
const identifier = (v) => count(v) && v > 0
const money = (v, signed = false) => typeof v === 'string' && (signed ? /^-?(0|[1-9]\d*)\.\d{2}$/ : /^(0|[1-9]\d*)\.\d{2}$/).test(v)
const units = (v) => typeof v === 'string' && /^(0|[1-9]\d*)(\.\d{1,6})?$/.test(v)
const nullable = (v, check) => v === null || check(v)
const text = (v, max) => typeof v === 'string' && v.length <= max
const uuid = (v) => typeof v === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v)
const time = (v) => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?$/.test(v)
const books = ['PROJECT_ACCOUNT', 'CUSTOMER_CREDIT']
const date = (value) => {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const [year, month, day] = value.split('-').map(Number)
  if (year < 1000 || year > 9998 || month < 1 || month > 12 || day < 1) return false
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
  return day <= [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1]
}

export function validOwner(row) {
  return !!row && identifier(row.id) && nullable(row.username, (v) => text(v, 50)) && ['ACTIVE', 'DISABLED', 'MISSING'].includes(row.status) &&
    count(row.activeAccounts) && count(row.activeClients) && ['accountDebited', 'accountReturned', 'clientDebited', 'clientReturned'].every((key) => money(row[key])) && nullable(row.lastActivity, time)
}
export const validRecordPage = (value, check) => !!value && count(value.total) && identifier(value.current) && value.current <= 10000 && identifier(value.size) && value.size <= 50 && Array.isArray(value.records) && value.records.length <= value.size && value.records.length <= Math.max(0, value.total - (value.current - 1) * value.size) && value.records.every(check)
export function validOwnerDetail(value) {
  const funding = value?.accountFunding
  return !!value && validOwner(value.owner) && validProjectReport(value.usage) && !!funding &&
    count(funding.settledOperations) && count(funding.unresolvedOperations) && money(funding.debited) && money(funding.returned) && money(funding.netDebited, true)
}
export function validSubject(row, client) {
  return !!row && uuid(row.id) && identifier(row.projectId) && text(row.title, 100) &&
    (client ? ['ACTIVE', 'SUSPENDED', 'CLOSED'].includes(row.status) && text(row.label, 100) && units(row.balance) && time(row.createdAt)
      : ['NEW', 'ACTIVE', 'DISABLED', 'BUSY', 'UNKNOWN'].includes(row.status) && nullable(row.cachedBalance, units) && nullable(row.balanceCheckedAt, time) && count(row.unresolvedOperations)) &&
    units(row.unitPrice) && units(row.refundableUnits) && money(row.refundBudget) && money(row.debited) && money(row.returned)
}
export function validLedger(value) {
  if (!validRecordPage(value, (row) => !!row && books.includes(row.book) && uuid(row.id) && identifier(row.ownerId) && identifier(row.projectId) && text(row.title, 100) && uuid(row.subjectId) &&
      ['OPEN', 'PROVISION', 'TOP_UP', 'WITHDRAW'].includes(row.action) && ['DEBIT', 'CREDIT'].includes(row.direction) &&
      money(row.amount) && units(row.units) && units(row.unitPrice) && nullable(row.subjectBalanceAfter, units) && nullable(row.walletBalanceAfter, money) && time(row.requestedAt) && time(row.settledAt)) ||
      !time(value.checkedAt) || value.timezone !== 'Asia/Shanghai' || !Array.isArray(value.totals) || value.totals.length > 2) return false
  const seen = new Set()
  for (const row of value.records) {
    const key = `${row.book}:${row.id}`
    if (seen.has(key) || (row.action === 'WITHDRAW') !== (row.direction === 'CREDIT')) return false
    if (!(row.book === 'PROJECT_ACCOUNT' ? ['PROVISION', 'TOP_UP', 'WITHDRAW'] : ['OPEN', 'TOP_UP', 'WITHDRAW']).includes(row.action)) return false
    seen.add(key)
  }
  return new Set(value.totals.map((r) => r.book)).size === value.totals.length && value.totals.every((r) => books.includes(r.book) && count(r.operations) && money(r.debited) && money(r.returned) && money(r.netDebited, true)) &&
    value.totals.reduce((sum, r) => sum + r.operations, 0) === value.total &&
    books.every((book) => value.records.filter((r) => r.book === book).length <= (value.totals.find((r) => r.book === book)?.operations ?? 0))
}
export const recordBooks = { PROJECT_ACCOUNT: '项目账户兑换', CUSTOMER_CREDIT: '客户额度' }
export const recordStatus = (status) => ({ ACTIVE: '可用', DISABLED: '已停用', MISSING: '账号已移除', SUSPENDED: '已暂停', CLOSED: '已关闭', NEW: '待开通', BUSY: '处理中', UNKNOWN: '待核实' }[status] || '未知状态')
export const recordActions = { PROVISION: '开户', OPEN: '开户', TOP_UP: '充值', WITHDRAW: '转回' }
export const recordTime = (value) => value ? value.replace('T', ' ').replace(/\.\d+$/, '') : '暂无记录'
export function recordFilters(form, admin, ownerId) {
  const params = {}
  for (const key of ['book', 'direction', 'keyword', 'fromDate', 'throughDate', 'projectId', 'clientId']) {
    const value = String(form[key] ?? '').trim()
    if (value && value !== 'ALL') params[key] = value
  }
  if (admin) {
    const owner = ownerId ?? form.ownerId
    if (owner !== null && owner !== undefined && String(owner).trim()) params.ownerId = String(owner).trim()
  }
  return params
}
export function validRecordFilters(form) {
  for (const key of ['ownerId', 'projectId']) {
    const text = String(form[key] ?? '').trim()
    if (text && (!/^[1-9]\d{0,18}$/.test(text) || BigInt(text) > 9223372036854775807n)) return false
  }
  if (form.clientId && (!uuid(form.clientId) || form.book === 'PROJECT_ACCOUNT')) return false
  if (form.book && form.book !== 'ALL' && !books.includes(form.book)) return false
  if (form.direction && !['ALL', 'DEBIT', 'CREDIT'].includes(form.direction)) return false
  if (form.status && !['ALL', 'ACTIVE', 'SUSPENDED', 'CLOSED'].includes(form.status)) return false
  for (const key of ['fromDate', 'throughDate']) if (form[key] && !date(form[key])) return false
  if (String(form.keyword ?? '').length > 100 || /[\x00-\x1f\x7f]/.test(form.keyword || '')) return false
  if (form.fromDate && form.throughDate && form.fromDate > form.throughDate) return false
  return true
}
