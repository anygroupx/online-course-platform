/**
 * 用户状态管理
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { login as loginApi, logout as logoutApi, verifyMfa as verifyMfaApi } from "@/api/auth";
import router from "@/router";
import { ElMessage } from "element-plus";

export const useUserStore = defineStore("user", () => {
  // 状态
  const token = ref(localStorage.getItem("token") || "");
  const userInfo = ref(JSON.parse(localStorage.getItem("userInfo") || "null"));

  // 计算属性
  const isLoggedIn = computed(() => !!token.value);
  const isAdmin = computed(() => userInfo.value?.isAdmin || false);

  // 登录
  // Source: AURA-X-KYS 安全加固 - Token时间戳管理 + 配置加载
  const persistSession = async (data) => {
    token.value = data.token;
    userInfo.value = data;

    const currentTime = Date.now().toString();
    localStorage.setItem("token", data.token);
    localStorage.setItem("tokenTime", currentTime);
    localStorage.setItem("userInfo", JSON.stringify(data));

    if (data.refreshToken) {
      localStorage.setItem("refreshToken", data.refreshToken);
      localStorage.setItem("refreshTokenTime", currentTime);
    }

    // 强制改密期间后端只允许改密相关接口，暂不加载业务配置。
    if (data.mustChangePassword) {
      return;
    }

    try {
      const settingsRes = await import("@/api/setting").then((m) =>
        m.getSettings()
      );
      if (settingsRes.code === 1 && settingsRes.data) {
        settingsRes.data.forEach((item) => {
          if (
            [
              "token_expire_minutes",
              "refresh_token_expire_days",
              "auto_refresh_token_enabled",
            ].includes(item.configKey)
          ) {
            localStorage.setItem(item.configKey, item.configValue);
          }
        });
      }
    } catch (error) {
      console.log("加载系统配置失败，使用默认值");
    }
  };

  // 同步服务端强制改密状态，供布局控制业务页面是否挂载。
  const setMustChangePassword = (required) => {
    if (!userInfo.value) {
      return;
    }
    userInfo.value = {
      ...userInfo.value,
      mustChangePassword: required,
    };
    localStorage.setItem("userInfo", JSON.stringify(userInfo.value));
  };

  // 登录（支持管理员 MFA 二次验证）
  const login = async (loginForm) => {
    try {
      const res = await loginApi(loginForm);
      if (res.code === 1) {
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
      }
      return res;
    } catch (error) {
      console.error("登录失败：", error);
      throw error;
    }
  };

  // MFA 二次验证完成登录
  const verifyMfaLogin = async (payload) => {
    try {
      const res = await verifyMfaApi(payload);
      if (res.code === 1) {
        await persistSession(res.data);
        ElMessage.success("MFA 验证成功");
        router.push("/");
      }
      return res;
    } catch (error) {
      console.error("MFA 验证失败：", error);
      throw error;
    }
  };

  // 登出
  // Source: AURA-X-KYS 安全加固 - 清理所有Token相关数据
  const logout = async () => {
    try {
      await logoutApi();

      // 清空状态
      token.value = "";
      userInfo.value = null;

      // 清空localStorage（包含所有token相关）
      localStorage.removeItem("token");
      localStorage.removeItem("tokenTime");
      localStorage.removeItem("refreshToken");
      localStorage.removeItem("refreshTokenTime");
      localStorage.removeItem("userInfo");

      ElMessage.success("登出成功");
      router.push("/login");
    } catch (error) {
      console.error("登出失败：", error);

      // 即使登出失败，也清空本地状态
      token.value = "";
      userInfo.value = null;
      localStorage.removeItem("token");
      localStorage.removeItem("tokenTime");
      localStorage.removeItem("refreshToken");
      localStorage.removeItem("refreshTokenTime");
      localStorage.removeItem("userInfo");
      router.push("/login");
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
    logout,
  };
});
