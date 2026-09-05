/**
 * 课程相关API
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
import request from '@/utils/request'

/**
 * 获取课程平台列表
 */
export function getCoursePlatforms() {
  return request({
    url: '/courses',
    method: 'get'
  })
}

/**
 * 查询课程
 */
export function queryCourses(data) {
  return request({
    url: '/courses/query',
    method: 'post',
    data
  })
}

/**
 * 获取统计数据
 */
export function getStatistics() {
  return request({
    url: '/statistics',
    method: 'get'
  })
}

/**
 * 查询课程平台（管理员）
 */
export function queryPlatforms(params) {
  return request({
    url: '/admin/platforms',
    method: 'get',
    params
  })
}

/**
 * 创建课程平台（管理员）
 */
export function createPlatform(data) {
  return request({
    url: '/admin/platforms',
    method: 'post',
    data
  })
}

/**
 * 更新课程平台（管理员）
 */
export function updatePlatform(data) {
  return request({
    url: '/admin/platforms',
    method: 'put',
    data
  })
}

/**
 * 删除课程平台（管理员）
 */
export function deletePlatform(id) {
  return request({
    url: `/admin/platforms/${id}`,
    method: 'delete'
  })
}


/**
 * 查询第三方接口商品列表（管理员）
 */
export function fetchProviderProducts(params) {
  return request({
    url: '/admin/docking/products',
    method: 'get',
    params
  })
}

/**
 * 导入选中的第三方商品（管理员）
 */
export function importSelectedProducts(data) {
  return request({
    url: '/admin/docking/import-products',
    method: 'post',
    data,
    timeout: 120000
  })
}
