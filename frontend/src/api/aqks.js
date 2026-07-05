/**
 * AQKS刷课管理API
 * 
 * 封装实验室安全平台自动刷课相关接口
 * 
 * @author AI Assistant
 * @since 2025-12-20
 */
import request from '@/utils/request'

/**
 * 启动自动刷课任务
 * @param {number} orderId 订单ID
 */
export function startAutoStudy(orderId) {
    return request({
        url: `/api/admin/aqks/start/${orderId}`,
        method: 'post'
    })
}

/**
 * 停止自动刷课任务
 * @param {number} orderId 订单ID
 */
export function stopAutoStudy(orderId) {
    return request({
        url: `/api/admin/aqks/stop/${orderId}`,
        method: 'post'
    })
}

/**
 * 手动刷时长
 * @param {number} orderId 订单ID
 * @param {number} seconds 时长（秒），默认10秒
 */
export function addStudyTime(orderId, seconds = 10) {
    return request({
        url: `/api/admin/aqks/add-time/${orderId}`,
        method: 'post',
        params: { seconds }
    })
}

/**
 * 获取学习状态
 * @param {number} orderId 订单ID
 */
export function getStudyStatus(orderId) {
    return request({
        url: `/api/admin/aqks/status/${orderId}`,
        method: 'get'
    })
}

/**
 * 检查任务是否在运行
 * @param {number} orderId 订单ID
 */
export function isTaskRunning(orderId) {
    return request({
        url: `/api/admin/aqks/running/${orderId}`,
        method: 'get'
    })
}

/**
 * 批量检查任务运行状态（优化版）
 * Source: AURA-X-KYS - 批量查询优化，减少网络请求
 * @param {Array<number>} orderIds 订单ID数组
 * @returns {Promise<Object>} 返回Map<订单ID, 是否运行>
 */
export function batchCheckRunningStatus(orderIds) {
    return request({
        url: '/api/admin/aqks/running/batch',
        method: 'post',
        data: orderIds
    })
}

/**
 * 获取运行中的任务数量
 */
export function getRunningTaskCount() {
    return request({
        url: '/api/admin/aqks/running-count',
        method: 'get'
    })
}

/**
 * 获取AQKS统计数据
 * 返回: {runningCount, pendingExam, completed, total}
 */
export function getAqksStatistics() {
    return request({
        url: '/api/admin/aqks/statistics',
        method: 'get'
    })
}

/**
 * 检查单个订单的考试状态
 * 
 * 手动触发检查指定订单的考试成绩，
 * 并根据结果自动更新订单状态和备注
 * 
 * @param {number} orderId 订单ID
 * @returns {Promise<Object>} 考试信息
 */
export function checkExamStatus(orderId) {
    return request({
        url: `/api/admin/aqks/check-exam/${orderId}`,
        method: 'post'
    })
}

/**
 * 批量同步考试状态
 * 
 * 手动触发批量同步所有待考试/考试中订单的考试状态
 * 
 * @returns {Promise<Object>} 同步结果 {total, success, failed, errors}
 */
export function syncExamStatus() {
    return request({
        url: '/api/admin/aqks/sync-exam-status',
        method: 'post'
    })
}
