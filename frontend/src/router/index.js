/**
 * Vue Router配置
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
import { createRouter, createWebHistory } from "vue-router";
import routes from "./routes.js";
import {
  getAccessToken,
  isAccessTokenExpired,
  refreshAccessSession,
  clearAuthSession,
  sessionUserInfo,
} from "@/utils/authSession";
import { isClientAutoRefreshEnabled } from "@/utils/clientConfigState";

const router = createRouter({
  history: createWebHistory(),
  routes,
});

// Route bootstrap: after a reload, recover a short-lived access JWT using the
// HttpOnly refresh cookie. The access token itself is never persisted.
router.beforeEach(async (to, _from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 二开台` : "在线网课平台";
  if (!to.meta.requiresAuth) {
    next();
    return;
  }

  const { useAppConfigStore } = await import("@/stores/appConfig");
  await useAppConfigStore().ensureLoaded();

  let token = getAccessToken();
  if (!token || isAccessTokenExpired()) {
    if (!isClientAutoRefreshEnabled()) {
      clearAuthSession();
      next("/login");
      return;
    }
    try {
      token = await refreshAccessSession();
    } catch {
      clearAuthSession();
      next("/login");
      return;
    }
  }

  if (to.meta.adminOnly && !sessionUserInfo.value?.isAdmin) {
    next("/dashboard");
    return;
  }
  next();
});

export default router;
