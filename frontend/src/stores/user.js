/** Authentication state. Access JWT is memory-only; refresh credential is an HttpOnly cookie. */
import { defineStore } from "pinia";
import { computed } from "vue";
import { login as loginApi, logout as logoutApi, verifyMfa as verifyMfaApi } from "@/api/auth";
import router from "@/router";
import { ElMessage } from "element-plus";
import {
  accessToken,
  sessionUserInfo,
  applyAuthSession,
  clearAuthSession,
} from "@/utils/authSession";

export const useUserStore = defineStore("user", () => {
  const token = accessToken;
  const userInfo = sessionUserInfo;
  const isLoggedIn = computed(() => !!token.value);
  const isAdmin = computed(() => userInfo.value?.isAdmin || false);

  const persistSession = async (data) => {
    applyAuthSession(data);
    if (data.mustChangePassword) return;
    try {
      const settingsRes = await import("@/api/setting").then((m) => m.getSettings());
      if (settingsRes.code === 1 && settingsRes.data) {
        settingsRes.data.forEach((item) => {
          if (["token_expire_minutes", "refresh_token_expire_days", "auto_refresh_token_enabled"].includes(item.configKey)) {
            localStorage.setItem(item.configKey, item.configValue);
          }
        });
      }
    } catch {
      console.info("加载系统配置失败，使用安全默认值");
    }
  };

  const setMustChangePassword = (required) => {
    if (!userInfo.value) return;
    userInfo.value = { ...userInfo.value, mustChangePassword: required };
    localStorage.setItem("userInfo", JSON.stringify(userInfo.value));
  };

  const login = async (loginForm) => {
    const res = await loginApi(loginForm);
    if (res.code !== 1) return res;
    if (res.data?.mfaRequired) {
      return {
        mfaRequired: true,
        mfaChallengeId: res.data.mfaChallengeId,
        username: res.data.username,
      };
    }
    await persistSession(res.data);
    ElMessage.success("登录成功");
    router.push("/");
    return { mfaRequired: false };
  };

  const verifyMfaLogin = async (payload) => {
    const res = await verifyMfaApi(payload);
    if (res.code === 1) {
      await persistSession(res.data);
      ElMessage.success("MFA 验证成功");
      router.push("/");
    }
    return res;
  };

  const clearSession = () => {
    clearAuthSession();
    router.push("/login");
  };

  const logout = async () => {
    try {
      await logoutApi();
      ElMessage.success("登出成功");
    } catch (error) {
      console.warn("服务端登出请求失败，本地凭证仍将清理", error);
    } finally {
      clearSession();
    }
  };

  return {
    token,
    userInfo,
    isLoggedIn,
    isAdmin,
    login,
    verifyMfaLogin,
    setMustChangePassword,
    clearSession,
    logout,
  };
});
