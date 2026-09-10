import { serviceAccountExpired } from './serviceCommerce.js'
export const receiptRequestId = (v) => typeof v === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v)
export const executionReceiptId = (v) => typeof v === 'string' && /^[A-Za-z0-9_-]{1,50}$/.test(v)
const time = (v) => {
  if (typeof v !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?$/.test(v)) return false
  const [year, month, day] = v.slice(0, 10).split('-').map(Number)
  return year >= 1000 && year <= 9998 && month >= 1 && month <= 12 && day >= 1 && day <= new Date(Date.UTC(year, month, 0)).getUTCDate() && Number.isFinite(Date.parse(v.replace(/(\.\d{3})\d+/, '$1') + '+08:00'))
}
export const receiptStates = {
  READING: ['核对中', '正在核对；请检查原请求，不重复发起查询。'],
  READY: ['待确认', '执行编号与完整订单身份一致；确认后只关联编号，不重新下单或扣费。'],
  APPLIED: ['已恢复', '执行编号已恢复；金额、状态和进度未改变，可另行刷新执行记录。'],
  READ_FAILED: ['未能核实', '无法核实完整订单身份，未关联编号；查不到不表示订单不存在。'],
  CONFLICT: ['记录已变化', '订单、配置或编号归属已变化，未关联编号。请重新核对。'],
  EXPIRED: ['预览已过期', '核对预览已过期，未关联编号。请重新核对。'],
  INTERRUPTED: ['查询未完成', '原查询未完成，未关联编号。重新核对需要使用新的请求编号。'],
}
export function validReceiptForm(form) {
  const note = String(form.evidence ?? '')
  return executionReceiptId(form.receiptId) && note.trim().length >= 10 && note.length <= 1000 && !/[\x00-\x1f\x7f]/.test(note) && form.ownershipConfirmed === true
}
export function validReceiptView(value, orderId, requestId) {
  return !!value && receiptRequestId(value.id) && (requestId === undefined || value.id === requestId) &&
    Number.isSafeInteger(value.orderId) && value.orderId > 0 && value.orderId === orderId &&
    typeof value.orderNo === 'string' && value.orderNo.length > 0 && value.orderNo.length <= 50 &&
    typeof value.courseName === 'string' && value.courseName.length > 0 && value.courseName.length <= 255 &&
    executionReceiptId(value.receiptId) && Object.hasOwn(receiptStates, value.state) && time(value.expiresAt) &&
    (value.state === 'APPLIED' ? time(value.appliedAt) : value.appliedAt === null)
}
export const receiptCanConfirm = (view, now) => view?.state === 'READY' && !serviceAccountExpired(view, now)
export const receiptTime = (value) => value?.replace('T', ' ').replace(/\.\d+$/, '') || '未记录'
