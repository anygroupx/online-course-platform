import { validLocalDateTime } from './localDateTime.js'

const count = (n) => Number.isSafeInteger(n) && n >= 0
const counts = (value, fields) => !!value && fields.every((field) => count(value[field]))
const money = (n, signed = false) => typeof n === 'string' && (signed ? /^-?(0|[1-9]\d*)\.\d{2}$/ : /^(0|[1-9]\d*)\.\d{2}$/).test(n)
const units = (n) => typeof n === 'string' && /^(0|[1-9]\d*)(\.\d{1,6})?$/.test(n)
const funding = (value) => counts(value, ['settledOperations', 'unresolvedOperations']) && money(value.debited) && money(value.returned) && money(value.netDebited, true)
const callFields = ['total', 'failed', 'last24Hours', 'failedLast24Hours']
const validCalls = (value) => counts(value, callFields) && value.failed <= value.total && value.last24Hours <= value.total && value.failedLast24Hours <= Math.min(value.failed, value.last24Hours)

export function validProjectReport(value, admin = false) {
  if (!value?.window || value.window.timezone !== 'Asia/Shanghai' ||
      !['from', 'through'].every((key) => validLocalDateTime(value.window[key])) ||
      !validCalls(value.calls) || !count(value.calls.actionKinds) || value.calls.actionKinds > value.calls.total ||
      !counts(value.clients, ['total', 'active', 'suspended', 'closed', 'projects']) ||
      !counts(value.tickets, ['total', 'open', 'inProgress', 'resolved', 'closed', 'pendingCompensation']) ||
      !funding(value.localFunding)) return false
  if (value.clients.active + value.clients.suspended + value.clients.closed !== value.clients.total || value.clients.projects > value.clients.total ||
      value.tickets.open + value.tickets.inProgress + value.tickets.resolved + value.tickets.closed !== value.tickets.total ||
      value.tickets.pendingCompensation > value.tickets.open + value.tickets.inProgress) return false
  if (admin) return funding(value.upstreamFunding) && counts(value, ['publishedProjects', 'activeUpstreamBindings', 'activeUpstreamOwners', 'activeLocalOwners'])
  return typeof value.moreActions === 'boolean' && Array.isArray(value.actions) && value.actions.length <= 50 &&
    value.actions.every((row) => typeof row.action === 'string' && /^[A-Z_]{1,40}$/.test(row.action) && validCalls(row))
}

export function validProjectBalancePage(value) {
  return !!value && count(value.total) && count(value.current) && value.current > 0 && Array.isArray(value.records) && value.records.length <= 20 &&
    value.records.every((row) => Number.isSafeInteger(row.projectId) && row.projectId > 0 && typeof row.title === 'string' && row.title.length <= 100 &&
      counts(row, ['customers', 'active']) && row.active <= row.customers && units(row.activeUnits) && units(row.suspendedUnits) && money(row.refundBudget))
}

const actions = {
  CATALOG: '查询项目目录', CLIENTS: '查询客户列表', CLIENT: '查询客户', SELF: '查询本人客户',
  STATS: '查询子账统计', USAGE: '查询用量统计', USAGE_PROJECTS: '查询项目额度',
  QUOTE: '生成报价', CONFIRM: '确认结算', OPERATION: '查询原操作', BY_REQUEST: '查询原请求',
  OPERATIONS: '查询操作记录', STATUS: '更新客户状态', TICKETS: '查询工单', TICKET: '查询工单详情',
  TICKET_SUBMIT: '提交工单', TICKET_REPLY: '回复工单', TICKET_IMAGE: '读取私有工单图片',
}
export const projectCallLabel = (action) => actions[action] || action
export const reportTime = (value) => value.replace('T', ' ').replace(/\.\d+$/, '')
