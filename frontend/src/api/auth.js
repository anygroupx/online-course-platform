/**
 * 认证相关API
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
import request from "@/utils/request";

/**
 * 用户登录
 */
export function login(data) {
  return request({
    url: "/auth/login",
    method: "post",
    data,
  });
}

/**
 * 用户登出
 */
export function logout() {
  return request({
    url: "/auth/logout",
    method: "post",
  });
}

/**
 * 获取当前用户信息
 */
export function getCurrentUser() {
  return request({
    url: "/auth/current",
    method: "get",
  });
}

/**
 * 刷新token
 * Source: AURA-X-KYS 安全加固 - Token刷新机制
 */
export function refreshToken() {
  return request({
    url: "/auth/refresh",
    method: "post",
    data: {
      refreshToken: localStorage.getItem("refreshToken"),
    },
  });
}

/**
 * MFA 登录二次验证
 * @param {Object} data { challengeId, code }
 */
export function verifyMfa(data) {
  return request({
    url: "/auth/mfa/verify",
    method: "post",
    data,
  });
}

/**
 * 查询 MFA 状态（管理员）
 */
export function getMfaStatus() {
  return request({
    url: "/auth/mfa/status",
    method: "get",
  });
}

/**
 * 开始绑定 MFA
 */
export function beginMfaSetup() {
  return request({
    url: "/auth/mfa/setup",
    method: "post",
  });
}

/**
 * 确认绑定 MFA
 * @param {Object} data { setupToken, code }
 */
export function confirmMfaSetup(data) {
  return request({
    url: "/auth/mfa/setup/confirm",
    method: "post",
    data,
  });
}

/**
 * 关闭 MFA
 * @param {Object} data { code }
 */
export function disableMfa(data) {
  return request({
    url: "/auth/mfa/disable",
    method: "post",
    data,
  });
}
