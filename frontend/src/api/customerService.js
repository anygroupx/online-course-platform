import request from '@/utils/request'

/**
 * 客服相关API
 * Source: 基于现有系统架构设计
 */

// 创建或获取用户会话
export function createOrGetSession() {
  return request({
    url: '/customer-service/session',
    method: 'post'
  })
}

// 发送消息
export function sendMessage(data) {
  return request({
    url: '/customer-service/message',
    method: 'post',
    data
  })
}

// 获取会话消息列表
export function getSessionMessages(sessionId) {
  return request({
    url: `/customer-service/session/${sessionId}/messages`,
    method: 'get'
  })
}

// 标记消息为已读
export function markMessagesAsRead(sessionId) {
  return request({
    url: `/customer-service/session/${sessionId}/read`,
    method: 'post'
  })
}

// 获取用户未读消息数量
export function getUnreadCount() {
  return request({
    url: '/customer-service/unread-count',
    method: 'get'
  })
}

// 结束会话
export function endSession(sessionId) {
  return request({
    url: `/customer-service/session/${sessionId}/end`,
    method: 'post'
  })
}

// 分配客服（管理员接口）
export function assignCustomerService(sessionId, customerServiceId) {
  return request({
    url: `/customer-service/session/${sessionId}/assign`,
    method: 'post',
    params: { customerServiceId }
  })
}

// 获取所有会话列表（管理端）
export function getAllSessions(status) {
  return request({
    url: '/customer-service/admin/sessions',
    method: 'get',
    params: { status }
  })
}

// 客服接入会话（管理端）
export function takeSession(sessionId) {
  return request({
    url: `/customer-service/admin/session/${sessionId}/take`,
    method: 'post'
  })
}
