/**
 * 用户状态管理
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { login as loginApi, logout as logoutApi } from "@/api/auth";
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
  const login = async (loginForm) => {
    try {
      const res = await loginApi(loginForm);
      if (res.code === 1) {
        token.value = res.data.token;
        userInfo.value = res.data;

        // 保存到localStorage，包含时间戳
        const currentTime = Date.now().toString();
        localStorage.setItem("token", res.data.token);
        localStorage.setItem("tokenTime", currentTime);
        localStorage.setItem("userInfo", JSON.stringify(res.data));

        // 如果后端返回了refreshToken，也保存
        if (res.data.refreshToken) {
          localStorage.setItem("refreshToken", res.data.refreshToken);
          localStorage.setItem("refreshTokenTime", currentTime);
        }

        // 加载系统配置（包含Token过期时间配置）
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

        ElMessage.success("登录成功");
        router.push("/");
      }
    } catch (error) {
      console.error("登录失败：", error);
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
    logout,
  };
});
