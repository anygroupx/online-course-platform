export const clientTicketKinds = { SUGGESTION: '建议反馈', BUG: '故障反馈', COMPENSATION: '补偿申请' }
export const clientTicketStates = { OPEN: '待处理', IN_PROGRESS: '处理中', RESOLVED: '已解决', CLOSED: '已关闭' }
export const clientTicketReviews = { NONE: '无需审核', PENDING: '待审核', APPROVED: '申请已通过', REJECTED: '申请已拒绝' }
export const clientTicketOpen = (ticket) => ['OPEN', 'IN_PROGRESS'].includes(ticket?.status)
export const ticketAmountValid = (value) => /^(?:0|[1-9]\d{0,7})(?:\.\d{1,2})?$/.test(String(value ?? ''))
export const ticketRequestIdValid = (value) => /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(String(value ?? ''))
