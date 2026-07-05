import request from '../utils/request'

/**
 * 创建支付订单
 */
export function createPayment(data) {
    return request({
        url: '/payment/create',
        method: 'post',
        data
    })
}

/**
 * 查询订单状态
 */
export function queryPaymentOrder(orderNo) {
    return request({
        url: `/payment/query/${orderNo}`,
        method: 'get'
    })
}

/**
 * 同步订单状态（主动查询支付宝）
 */
export function syncPaymentOrder(orderNo) {
    return request({
        url: `/payment/sync/${orderNo}`,
        method: 'post'
    })
}

/**
 * 获取支付订单列表
 */
export function getPaymentOrders(params) {
    return request({
        url: '/payment/orders',
        method: 'get',
        params
    })
}

/**
 * 申请退款
 */
export function refundPayment(orderNo, refundReason) {
    return request({
        url: `/payment/refund/${orderNo}`,
        method: 'post',
        params: { refundReason }
    })
}

/**
 * 获取支付配置列表(管理员)
 */
export function getPaymentConfigs() {
    return request({
        url: '/payment/config',
        method: 'get'
    })
}

/**
 * 获取配置详情(管理员)
 */
export function getPaymentConfig(id) {
    return request({
        url: `/payment/config/${id}`,
        method: 'get'
    })
}

/**
 * 创建支付配置(管理员)
 */
export function createPaymentConfig(data) {
    return request({
        url: '/payment/config',
        method: 'post',
        data
    })
}

/**
 * 更新支付配置(管理员)
 */
export function updatePaymentConfig(id, data) {
    return request({
        url: `/payment/config/${id}`,
        method: 'put',
        data
    })
}

/**
 * 激活支付配置(管理员)
 */
export function activatePaymentConfig(id) {
    return request({
        url: `/payment/config/${id}/activate`,
        method: 'put'
    })
}

/**
 * 删除支付配置(管理员)
 */
export function deletePaymentConfig(id) {
    return request({
        url: `/payment/config/${id}`,
        method: 'delete'
    })
}
