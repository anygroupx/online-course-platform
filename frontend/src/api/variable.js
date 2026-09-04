/**
 * 系统变量管理API
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
import request from '@/utils/request'

/**
 * 创建系统变量
 */
export function createVariable(data) {
  return request({
    url: '/admin/variables',
    method: 'post',
    data
  })
}

/**
 * 更新系统变量
 */
export function updateVariable(data) {
  return request({
    url: '/admin/variables',
    method: 'put',
    data
  })
}

/**
 * 原子批量更新主题颜色变量
 */
export function updateThemeVariables(data) {
  return request({
    url: '/admin/variables/theme',
    method: 'put',
    data
  })
}

/**
 * 删除系统变量
 */
export function deleteVariable(id) {
  return request({
    url: `/admin/variables/${id}`,
    method: 'delete'
  })
}

/**
 * 根据类型查询变量列表
 */
export function getVariablesByType(type) {
  return request({
    url: `/admin/variables/type/${type}`,
    method: 'get'
  })
}

/**
 * 分页查询变量
 */
export function queryVariables(params) {
  return request({
    url: '/admin/variables',
    method: 'get',
    params
  })
}

/**
 * 获取变量详情
 */
export function getVariableById(id) {
  return request({
    url: `/admin/variables/${id}`,
    method: 'get'
  })
}

/**
 * 启用/禁用变量
 */
export function toggleVariableStatus(id, enabled) {
  return request({
    url: `/admin/variables/${id}/toggle`,
    method: 'post',
    params: { enabled }
  })
}

/**
 * 设置默认变量
 */
export function setDefaultVariable(id) {
  return request({
    url: `/admin/variables/${id}/set-default`,
    method: 'post'
  })
}

/**
 * 获取所有变量类型
 */
export function getVariableTypes() {
  return request({
    url: '/admin/variables/types',
    method: 'get'
  })
}

/**
 * 获取客户端可用的主题颜色变量（公开只读）
 */
export function getThemeVariables() {
  return request({
    url: '/theme/variables',
    method: 'get'
  })
}
