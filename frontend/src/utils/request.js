/** Axios wrapper with memory-only access JWT and cookie-based refresh rotation. */
import axios from "axios";
import { ElMessage } from "element-plus";
import router from "@/router";
import {
  getAccessToken,
  isAccessTokenExpired,
  refreshAccessSession,
  clearAuthSession,
} from "@/utils/authSession";

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
  timeout: 30000,
  withCredentials: true,
  headers: { "Content-Type": "application/json;charset=utf-8" },
});

const publicPaths = new Set([
  "/auth/login",
  "/auth/refresh",
  "/auth/mfa/verify",
  "/register",
  "/register/validate-invite-code",
  "/theme/variables",
]);

const autoRefreshEnabled = () => localStorage.getItem("auto_refresh_token_enabled") !== "0";

function clearAndRedirect() {
  clearAuthSession();
  if (router.currentRoute.value.path !== "/login") router.push("/login");
}

function notifyMustChangePassword() {
  const value = localStorage.getItem("userInfo");
  if (value) {
    try {
      const info = JSON.parse(value);
      localStorage.setItem("userInfo", JSON.stringify({ ...info, mustChangePassword: true }));
    } catch {
      localStorage.removeItem("userInfo");
    }
  }
  window.dispatchEvent(new CustomEvent("must-change-password"));
}

request.interceptors.request.use(async (config) => {
  if (publicPaths.has(config.url)) return config;

  let token = getAccessToken();
  if ((!token || isAccessTokenExpired()) && autoRefreshEnabled()) {
    try {
      token = await refreshAccessSession();
    } catch {
      clearAndRedirect();
      throw new Error("未登录");
    }
  }
  if (!token) throw new Error("未登录");
  config.headers.Authorization = `Bearer ${token}`;
  return config;
});

request.interceptors.response.use(
  (response) => {
    if (response.config.responseType === "blob") return response.data;
    const result = response.data;
    if (result?.code === 1) return result;
    if (result?.code === -100) clearAndRedirect();
    ElMessage.error(result?.message || "请求失败");
    return Promise.reject(new Error(result?.message || "请求失败"));
  },
  async (error) => {
    const status = error.response?.status;
    const original = error.config || {};
    const canRetry = status === 401
      && !original.__sessionRetry
      && !String(original.url || "").includes("/auth/refresh")
      && autoRefreshEnabled();

    if (canRetry) {
      try {
        original.__sessionRetry = true;
        const token = await refreshAccessSession();
        original.headers = original.headers || {};
        original.headers.Authorization = `Bearer ${token}`;
        return request(original);
      } catch {
        clearAndRedirect();
        ElMessage.error("登录已过期，请重新登录");
      }
    } else if (status === 401) {
      clearAndRedirect();
    } else if (status === 403 && error.response?.data?.code === -107) {
      notifyMustChangePassword();
      ElMessage.warning(error.response.data.message || "首次登录必须修改密码");
    } else if (status === 403) {
      ElMessage.error(error.response?.data?.message || "拒绝访问");
    } else if (status === 429) {
      ElMessage.error(error.response?.data?.message || "请求过于频繁，请稍后再试");
    } else if (error.response) {
      ElMessage.error(error.response.data?.message || "请求失败");
    } else if (error.request) {
      ElMessage.error("网络错误，请检查网络连接");
    }
    return Promise.reject(error);
  }
);

export default request;
