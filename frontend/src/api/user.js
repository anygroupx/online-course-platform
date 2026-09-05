/**
 * 用户相关API
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
import request from '@/utils/request'

/**
 * 获取用户完整信息
 */
export function getUserInfo() {
  return request({
    url: '/user/info',
    method: 'get'
  })
}

/**
 * 查询用户列表
 */
export function queryUsers(params) {
  return request({
    url: '/users',
    method: 'get',
    params
  })
}

/**
 * 创建用户（开户）
 */
export function createUser(data) {
  return request({
    url: '/users',
    method: 'post',
    data
  })
}

/**
 * 更新用户信息
 */
export function updateUser(data) {
  return request({
    url: '/users',
    method: 'put',
    data
  })
}

/**
 * 充值
 */
export function recharge(data) {
  return request({
    url: '/users/recharge',
    method: 'post',
    data
  })
}

/**
 * 修改密码
 */
export function changePassword(data) {
  return request({
    url: '/users/change-password',
    method: 'post',
    data: new URLSearchParams(data).toString(),
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    }
  })
}

/**
 * 重置密码
 */
export function resetPassword(uid) {
  return request({
    url: `/users/${uid}/reset-password`,
    method: 'post'
  })
}

/**
 * 禁用/启用用户
 */
export function changeUserStatus(uid, status) {
  return request({
    url: `/users/${uid}/status`,
    method: 'post',
    params: { status }
  })
}

/**
 * 开通API密钥
 */
export function enableApiKey(type, targetUserUid) {
  return request({
    url: '/api-keys/enable',
    method: 'post',
    params: { type, targetUserUid }
  })
}

/**
 * 用户注册
 */
export function register(data) {
  return request({
    url: '/register',
    method: 'post',
    data
  })
}

/**
 * 验证邀请码
 */
export function validateInviteCode(inviteCode) {
  return request({
    url: '/register/validate-invite-code',
    method: 'get',
    params: { inviteCode }
  })
}

/**
 * 设置邀请码
 */
export function setupInviteCode(data) {
  return request({
    url: '/register/invite-code',
    method: 'post',
    data
  })
}


/** Password-confirmed self-service rotation. The old key becomes invalid immediately. */
export function rotateApiKey(currentPassword) {
  return request({ url: '/api-keys/rotate', method: 'post', data: { currentPassword } })
}
