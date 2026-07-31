/**
 * Axios请求封装
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
import axios from "axios";
import { ElMessage } from "element-plus";
import router from "@/router";

// Source: AURA-X-KYS 安全加固 - Token管理
// Token过期时间配置（从系统设置读取，fallback到默认值）
const getTokenExpireTime = () => {
  const minutes = parseInt(
    localStorage.getItem("token_expire_minutes") || "15"
  );
  return minutes * 60 * 1000;
};

const getRefreshTokenExpireTime = () => {
  const days = parseInt(
    localStorage.getItem("refresh_token_expire_days") || "7"
  );
  return days * 24 * 60 * 60 * 1000;
};

const isAutoRefreshEnabled = () => {
  return localStorage.getItem("auto_refresh_token_enabled") !== "0";
};

// 是否正在刷新token的标志
let isRefreshing = false;
// 重试队列
let requestQueue = [];

// 创建axios实例
// Source: Docker部署修复 - 使用环境变量配置API地址
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
  timeout: 30000,
  headers: {
    "Content-Type": "application/json;charset=utf-8",
  },
});

// 不需要token的接口白名单
const whiteList = [
  "/auth/login",
  "/register",
  "/register/validate-invite-code",
  "/auth/refresh", // 刷新token接口
  "/auth/mfa/verify",
];

/**
 * 检查token是否过期
 * Source: AURA-X-KYS 安全加固 - Token过期检查（支持动态配置）
 */
const isTokenExpired = () => {
  const tokenTime = localStorage.getItem("tokenTime");
  if (!tokenTime) return true;

  const elapsed = Date.now() - parseInt(tokenTime);
  return elapsed > getTokenExpireTime();
};

/**
 * 检查refreshToken是否过期
 */
const isRefreshTokenExpired = () => {
  const refreshTokenTime = localStorage.getItem("refreshTokenTime");
  if (!refreshTokenTime) return true;

  const elapsed = Date.now() - parseInt(refreshTokenTime);
  return elapsed > getRefreshTokenExpireTime();
};

/**
 * 刷新token
 * Source: AURA-X-KYS 安全加固 - 改进错误处理
 */
const refreshTokenRequest = async () => {
  const refreshToken = localStorage.getItem("refreshToken");
  if (!refreshToken || isRefreshTokenExpired()) {
    throw new Error("Refresh token expired");
  }

  try {
    // Source: Docker部署修复 - 使用配置的baseURL
    const apiBase = import.meta.env.VITE_API_BASE_URL || "/api";
    const response = await axios.post(`${apiBase}/auth/refresh`, {
      refreshToken,
    });

    if (response.data.code === 1) {
      const { token, refreshToken: newRefreshToken } = response.data.data;

      // 更新token和时间戳
      localStorage.setItem("token", token);
      localStorage.setItem("tokenTime", Date.now().toString());

      if (newRefreshToken) {
        localStorage.setItem("refreshToken", newRefreshToken);
        localStorage.setItem("refreshTokenTime", Date.now().toString());
      }

      return token;
    }

    throw new Error(response.data.message || "Token refresh failed");
  } catch (error) {
    console.error("Token刷新失败:", error);
    // 抛出错误让调用方处理，不在这里直接清理和跳转
    throw error;
  }
};

/**
 * 清理Token并跳转登录
 */
const clearTokenAndRedirect = () => {
  localStorage.removeItem("token");
  localStorage.removeItem("tokenTime");
  localStorage.removeItem("refreshToken");
  localStorage.removeItem("refreshTokenTime");
  localStorage.removeItem("userInfo");

  // 避免在登录页重复跳转
  if (router.currentRoute.value.path !== "/login") {
    router.push("/login");
  }
};

// 通知当前布局进入强制改密状态，同时持久化标记以覆盖刷新场景。
const notifyMustChangePassword = () => {
  const storedUserInfo = localStorage.getItem("userInfo");
  if (storedUserInfo) {
    try {
      const userInfo = JSON.parse(storedUserInfo);
      localStorage.setItem(
        "userInfo",
        JSON.stringify({ ...userInfo, mustChangePassword: true })
      );
    } catch (error) {
      console.warn("同步强制改密状态失败：", error);
    }
  }
  window.dispatchEvent(new CustomEvent("must-change-password"));
};

// 请求拦截器
// Source: AURA-X-KYS 安全加固 - 自动token刷新
request.interceptors.request.use(
  async (config) => {
    // 检查是否在白名单中
    const isInWhiteList = whiteList.some((url) => {
      // 精确匹配或通配符匹配
      if (url.endsWith("**")) {
        const prefix = url.slice(0, -2);
        return config.url.startsWith(prefix);
      } else {
        return config.url === url;
      }
    });

    // 如果不在白名单中，则添加token
    if (!isInWhiteList) {
      const token = localStorage.getItem("token");

      if (!token) {
        // 没有token，直接拒绝请求
        return Promise.reject(new Error("未登录"));
      }

      // 检查是否启用自动刷新和token是否过期
      if (isAutoRefreshEnabled() && isTokenExpired()) {
        if (!isRefreshing) {
          isRefreshing = true;

          try {
            const newToken = await refreshTokenRequest();
            isRefreshing = false;

            // 执行队列中的请求
            requestQueue.forEach((cb) => cb(newToken));
            requestQueue = [];

            config.headers["Authorization"] = `Bearer ${newToken}`;
            return config;
          } catch (error) {
            isRefreshing = false;
            // 刷新失败，清空队列并拒绝所有请求
            requestQueue.forEach((cb) => cb(null));
            requestQueue = [];

            // 清理Token并跳转登录
            clearTokenAndRedirect();
            ElMessage.error("登录已过期，请重新登录");
            return Promise.reject(new Error("Token刷新失败"));
          }
        } else {
          // 正在刷新token，将请求加入队列等待
          return new Promise((resolve, reject) => {
            requestQueue.push((newToken) => {
              if (newToken) {
                config.headers["Authorization"] = `Bearer ${newToken}`;
                resolve(config);
              } else {
                reject(new Error("Token刷新失败"));
              }
            });
          });
        }
      } else {
        // Token未过期或未启用自动刷新，直接使用
        config.headers["Authorization"] = `Bearer ${token}`;
      }
    }

    return config;
  },
  (error) => {
    console.error("请求错误：", error);
    return Promise.reject(error);
  }
);

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    // 特殊处理：如果是blob类型响应（文件下载），需要检查是否为错误的JSON响应
    if (response.config.responseType === "blob") {
      const contentType = response.headers["content-type"];
      // 如果响应类型为JSON，尝试解析错误信息
      if (contentType && contentType.includes("application/json")) {
        return new Promise((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = function () {
            try {
              const json = JSON.parse(reader.result);
              ElMessage.error(json.message || "请求失败");
              // -100: 未登录或token过期
              if (json.code === -100) {
                localStorage.removeItem("token");
                localStorage.removeItem("userInfo");
                router.push("/login");
              }
              reject(new Error(json.message || "请求失败"));
            } catch (e) {
              // 解析失败，返回原始blob
              resolve(response.data);
            }
          };
          reader.onerror = function () {
            resolve(response.data);
          };
          reader.readAsText(response.data);
        });
      }
      // 如果不是JSON，直接返回原始数据
      return response.data;
    }

    const res = response.data;

    // 如果返回的状态码不是1，说明接口请求失败
    if (res.code !== 1) {
      ElMessage.error(res.message || "请求失败");

      // -100: 未登录或token过期
      if (res.code === -100) {
        localStorage.removeItem("token");
        localStorage.removeItem("userInfo");
        router.push("/login");
      }

      return Promise.reject(new Error(res.message || "请求失败"));
    } else {
      return res;
    }
  },
  async (error) => {
    console.error("响应错误：", error);

    if (error.response) {
      switch (error.response.status) {
        case 401:
          // Source: AURA-X-KYS 安全加固 - 401自动重试（改进版）
          // 如果不在刷新中且refresh token未过期，尝试刷新
          if (
            !isRefreshing &&
            !isRefreshTokenExpired() &&
            isAutoRefreshEnabled()
          ) {
            try {
              isRefreshing = true;
              const newToken = await refreshTokenRequest();
              isRefreshing = false;

              // 重试原请求
              error.config.headers["Authorization"] = `Bearer ${newToken}`;
              return request(error.config);
            } catch (refreshError) {
              isRefreshing = false;
              clearTokenAndRedirect();
              ElMessage.error("登录已过期，请重新登录");
              return Promise.reject(refreshError);
            }
          } else {
            // 正在刷新、refresh token过期或未启用自动刷新
            if (!isRefreshing) {
              clearTokenAndRedirect();
              ElMessage.error("登录已过期，请重新登录");
            }
            // 如果正在刷新，不显示错误消息（会由刷新失败统一处理）
          }
          break;
        case 403:
          if (error.response.data?.code === -107) {
            notifyMustChangePassword();
            ElMessage.warning(
              error.response.data.message || "首次登录必须修改密码"
            );
          } else {
            ElMessage.error(error.response.data?.message || "拒绝访问");
          }
          break;
        case 404:
          ElMessage.error("请求资源不存在");
          break;
        case 500:
          ElMessage.error("服务器内部错误");
          break;
        default:
          ElMessage.error(error.response.data.message || "请求失败");
      }
    } else if (error.request) {
      // 请求已发送但没有收到响应
      ElMessage.error("网络错误，请检查网络连接");
    } else {
      // 请求配置出错
      console.error("请求配置错误:", error.message);
      // 只在非Token相关错误时显示提示
      if (
        !error.message?.includes("Token") &&
        !error.message?.includes("未登录")
      ) {
        ElMessage.error("请求配置错误: " + (error.message || "未知错误"));
      }
    }

    return Promise.reject(error);
  }
);

export default request;
