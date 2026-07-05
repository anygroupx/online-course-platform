/**
 * 日志相关API
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
import request from '@/utils/request'

/**
 * 查询操作日志
 */
export function queryLogs(params) {
  return request({
    url: '/logs',
    method: 'get',
    params
  })
}

/**
 * ES全文搜索日志
 */
export function searchLogs(params) {
  return request({
    url: '/logs/search',
    method: 'get',
    params
  })
}

/**
 * 同步历史日志到ES（仅管理员）
 */
export function syncLogsToES() {
  return request({
    url: '/logs/sync-to-es',
    method: 'post'
  })
}
