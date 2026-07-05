/**
 * 统计相关API
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
import request from '@/utils/request'

/**
 * 获取统计数据
 */
export function getStatistics() {
  return request({
    url: '/statistics',
    method: 'get'
  })
}

