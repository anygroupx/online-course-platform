/**
 * Vue Router配置
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
import { createRouter, createWebHistory } from "vue-router";

const routes = [
  {
    path: "/login",
    name: "Login",
    component: () => import("@/views/Login.vue"),
    meta: { title: "登录" },
  },
  {
    path: "/register",
    name: "Register",
    component: () => import("@/views/Register.vue"),
    meta: { title: "注册" },
  },
  {
    path: "/guest-order",
    name: "GuestOrder",
    component: () => import("@/views/GuestOrder.vue"),
    meta: { title: "游客下单" },
  },
  {
    path: "/service-agreement",
    name: "ServiceAgreement",
    component: () => import("@/views/ServiceAgreement.vue"),
    meta: { title: "服务协议" },
  },
  {
    path: "/privacy-policy",
    name: "PrivacyPolicy",
    component: () => import("@/views/PrivacyPolicy.vue"),
    meta: { title: "隐私政策" },
  },
  {
    path: "/",
    name: "Layout",
    component: () => import("@/layouts/MainLayout.vue"),
    redirect: "/dashboard",
    children: [
      {
        path: "dashboard",
        name: "Dashboard",
        component: () => import("@/views/Dashboard.vue"),
        meta: { title: "首页", requiresAuth: true },
      },
      {
        path: "orders",
        name: "Orders",
        component: () => import("@/views/Orders.vue"),
        meta: { title: "订单管理", requiresAuth: true },
      },
      {
        path: "courses",
        name: "Courses",
        component: () => import("@/views/Courses.vue"),
        meta: { title: "课程列表", requiresAuth: true },
      },
      {
        path: "users",
        name: "Users",
        component: () => import("@/views/Users.vue"),
        meta: { title: "代理管理", requiresAuth: true },
      },
      {
        path: "logs",
        name: "Logs",
        component: () => import("@/views/Logs.vue"),
        meta: { title: "操作日志", requiresAuth: true },
      },
      {
        path: "price-list",
        name: "PriceList",
        component: () => import("@/views/PriceList.vue"),
        meta: { title: "价格列表", requiresAuth: true },
      },
      {
        path: "settings",
        name: "Settings",
        component: () => import("@/views/Settings.vue"),
        meta: { title: "系统设置", requiresAuth: true, adminOnly: true },
      },
      {
        path: "admin/platforms",
        name: "AdminPlatforms",
        component: () => import("@/views/AdminPlatforms.vue"),
        meta: { title: "平台管理", requiresAuth: true, adminOnly: true },
      },
      {
        path: "admin/categories",
        name: "AdminCategories",
        component: () => import("@/views/AdminPlatformCategories.vue"),
        meta: { title: "分类管理", requiresAuth: true, adminOnly: true },
      },
      {
        path: "admin/api-providers",
        name: "AdminApiProviders",
        component: () => import("@/views/AdminApiProviders.vue"),
        meta: { title: "接口管理", requiresAuth: true, adminOnly: true },
      },
      {
        path: "admin/orders",
        name: "AdminOrders",
        component: () => import("@/views/AdminOrders.vue"),
        meta: { title: "管理员订单管理", requiresAuth: true, adminOnly: true },
      },
      {
        path: "recharge",
        name: "Recharge",
        component: () => import("@/views/Recharge.vue"),
        meta: { title: "账户充值", requiresAuth: true },
      },
      {
        path: "admin/cards",
        name: "CardManagement",
        component: () => import("@/views/CardManagement.vue"),
        meta: { title: "充值卡密管理", requiresAuth: true, adminOnly: true },
      },
      {
        path: "admin/announcements",
        name: "AnnouncementManagement",
        component: () => import("@/views/AnnouncementManagement.vue"),
        meta: { title: "公告管理", requiresAuth: true, adminOnly: true },
      },
      {
        path: "admin/variables",
        name: "SystemVariableManagement",
        component: () => import("@/views/SystemVariableManagement.vue"),
        meta: { title: "系统变量管理", requiresAuth: true, adminOnly: true },
      },
      {
        path: "admin/countdown",
        name: "CountdownManagement",
        component: () => import("@/views/CountdownManagement.vue"),
        meta: { title: "倒计时管理", requiresAuth: true, adminOnly: true },
      },
      {
        path: "admin/aqks",
        name: "AqksStudyManagement",
        component: () => import("@/views/AqksStudyManagement.vue"),
        meta: { title: "AQKS刷课管理", requiresAuth: true, adminOnly: true },
      },
      {
        path: "api-guide",
        name: "ApiGuide",
        component: () => import("@/views/ApiDocs.vue"),
        meta: { title: "API文档", requiresAuth: true },
      },
      {
        path: "profile",
        name: "Profile",
        component: () => import("@/views/Profile.vue"),
        meta: { title: "个人中心", requiresAuth: true },
      },
      {
        path: "admin/customer-service",
        name: "CustomerServiceManagement",
        component: () => import("@/views/CustomerServiceManagement.vue"),
        meta: { title: "客服管理", requiresAuth: true, adminOnly: true },
      },
      {
        path: "examples",
        name: "Examples",
        component: () => import("@/examples/EnterpriseOrdersExample.vue"),
        meta: { title: "示例页面", requiresAuth: true },
      },
      {
        path: "theme-config",
        name: "ThemeConfig",
        component: () => import("@/views/ThemeConfig.vue"),
        meta: { title: "主题配置", requiresAuth: true },
      },
      {
        path: "payment/callback",
        name: "PaymentCallback",
        component: () => import("@/views/PaymentCallback.vue"),
        meta: { title: "支付结果", requiresAuth: true },
      },
      {
        path: "payment/orders",
        name: "PaymentOrders",
        component: () => import("@/views/PaymentOrders.vue"),
        meta: { title: "支付订单", requiresAuth: true },
      },
    ],
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

// 检查Token是否过期的辅助函数
// Source: AURA-X-KYS 安全加固 - 路由守卫Token过期检查
const isTokenExpiredInRouter = () => {
  const tokenTime = localStorage.getItem("tokenTime");
  if (!tokenTime) return true;

  const minutes = parseInt(
    localStorage.getItem("token_expire_minutes") || "15"
  );
  const expireTime = minutes * 60 * 1000;
  const elapsed = Date.now() - parseInt(tokenTime);

  return elapsed > expireTime;
};

// 路由守卫
router.beforeEach((to, from, next) => {
  // 设置页面标题
  document.title = to.meta.title ? `${to.meta.title} - 二开台` : "在线网课平台";

  // 检查是否需要登录
  if (to.meta.requiresAuth) {
    const token = localStorage.getItem("token");
    const autoRefreshEnabled =
      localStorage.getItem("auto_refresh_token_enabled") !== "0";

    if (!token) {
      next("/login");
      return;
    }

    // 检查Token是否过期（如果启用了自动刷新，则允许过期Token通过，由请求拦截器处理）
    // 如果未启用自动刷新且Token过期，直接跳转登录
    if (!autoRefreshEnabled && isTokenExpiredInRouter()) {
      localStorage.removeItem("token");
      localStorage.removeItem("tokenTime");
      localStorage.removeItem("userInfo");
      next("/login");
      return;
    }

    // 检查是否需要管理员权限
    if (to.meta.adminOnly) {
      const userInfo = JSON.parse(localStorage.getItem("userInfo") || "null");
      if (!userInfo || !userInfo.isAdmin) {
        next("/dashboard");
        return;
      }
    }

    next();
  } else {
    next();
  }
});

export default router;
