import request from '@/utils/request'

/**
 * 外部API接口（供第三方系统对接）
 */

/**
 * 查询余额
 */
export function getMoney(data) {
    return request({
        url: '/external/getmoney',
        method: 'post',
        params: data
    })
}

/**
 * 查单
 */
export function queryOrders(data) {
    return request({
        url: '/external/chadan',
        method: 'post',
        params: data
    })
}

/**
 * 单下单
 */
export function createOrder(data) {
    return request({
        url: '/external/add',
        method: 'post',
        params: data
    })
}

/**
 * 补单
 */
export function retryOrder(data) {
    return request({
        url: '/external/budan',
        method: 'post',
        params: data
    })
}

/**
 * 查课
 */
export function queryCourses(data) {
    return request({
        url: '/external/query-courses',
        method: 'post',
        params: data
    })
}

/**
 * 查询订单进度
 */
export function queryProgress(data) {
    return request({
        url: '/external/query-progress',
        method: 'post',
        params: data
    })
}

/**
 * 获取平台列表
 */
export function getPlatforms(data) {
    return request({
        url: '/external/get-platforms',
        method: 'post',
        params: data
    })
}
