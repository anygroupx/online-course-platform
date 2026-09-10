import request from '@/utils/request'
const root = (orderId) => `/admin/orders/${encodeURIComponent(orderId)}/receipt-recoveries`
export const previewOrderReceipt = async (orderId, data) => (await request.post(root(orderId), data, { timeout: 60000, __sessionRetry: true })).data
export const getOrderReceipt = async (orderId, requestId, signal) => (await request.get(`${root(orderId)}/${encodeURIComponent(requestId)}`, { signal })).data
export const confirmOrderReceipt = async (orderId, requestId) => (await request.post(`${root(orderId)}/${encodeURIComponent(requestId)}/confirm`, { consent: true }, { __sessionRetry: true })).data
export const recentOrderReceipts = async (orderId, signal) => (await request.get(root(orderId), { signal })).data
