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
