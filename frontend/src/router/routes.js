// Route titles and breadcrumb groups live with the route records, not URL fragments.
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
        meta: { title: "项目管理", requiresAuth: true },
      },
      {
        path: "settings",
        name: "Settings",
        component: () => import("@/views/Settings.vue"),
        meta: { title: "系统设置", requiresAuth: true, adminOnly: true, breadcrumbGroup: "系统管理" },
      },
      {
        path: "admin/platforms",
        name: "AdminPlatforms",
        component: () => import("@/views/AdminPlatforms.vue"),
        meta: { title: "平台管理", requiresAuth: true, adminOnly: true, breadcrumbTitle: "课程平台", breadcrumbGroup: "系统管理" },
      },
      {
        path: "admin/categories",
        name: "AdminCategories",
        component: () => import("@/views/AdminPlatformCategories.vue"),
        meta: { title: "分类管理", requiresAuth: true, adminOnly: true, breadcrumbGroup: "系统管理" },
      },
      {
        path: "admin/api-providers",
        name: "AdminApiProviders",
        component: () => import("@/views/AdminApiProviders.vue"),
        meta: { title: "接口管理", requiresAuth: true, adminOnly: true, breadcrumbTitle: "接口配置", breadcrumbGroup: "系统管理" },
      },
      {
        path: "services", name: "ServiceStore", component: () => import("@/views/ServiceStore.vue"),
        meta: { title: "服务商城", requiresAuth: true, breadcrumbGroup: "服务中心" },
      },
      {
        path: "service-orders", name: "ServiceOrders", component: () => import("@/views/ServiceOrders.vue"),
        meta: { title: "服务订单", requiresAuth: true, breadcrumbGroup: "服务中心" },
      },
      {
        path: "service-projects", name: "ProjectCenter", component: () => import("@/views/ProjectCenter.vue"),
        meta: { title: "项目中心", requiresAuth: true, breadcrumbGroup: "服务中心" },
      },
      {
        path: "project-clients", name: "ProjectClients", component: () => import("@/views/ProjectClients.vue"),
        meta: { title: "客户与项目 API", requiresAuth: true, breadcrumbTitle: "客户与 API", breadcrumbGroup: "服务中心" },
      },
      {
        path: "admin/service-projects", name: "AdminProjectCenter", component: () => import("@/views/AdminProjectCenter.vue"),
        meta: { title: "项目与子钱包", requiresAuth: true, adminOnly: true, breadcrumbGroup: "服务管理" },
      },
      {
        path: "admin/service-products", name: "AdminServiceProducts", component: () => import("@/views/AdminServiceProducts.vue"),
        meta: { title: "服务商品", requiresAuth: true, adminOnly: true, breadcrumbGroup: "服务管理" },
      },
      {
        path: "admin/service-orders", name: "AdminServiceOrders", component: () => import("@/views/ServiceOrders.vue"),
        meta: { title: "服务订单与对账", requiresAuth: true, adminOnly: true, serviceAdmin: true, breadcrumbGroup: "服务管理" },
      },
      {
        path: "admin/plugin-integrations",
        name: "AdminPluginIntegrations",
        component: () => import("@/views/AdminPluginIntegrations.vue"),
        meta: { title: "插件集成", requiresAuth: true, adminOnly: true, breadcrumbTitle: "接口接入检查", breadcrumbGroup: "服务管理" },
      },
      {
        path: "admin/orders",
        name: "AdminOrders",
        component: () => import("@/views/AdminOrders.vue"),
        meta: { title: "管理员订单管理", requiresAuth: true, adminOnly: true, breadcrumbTitle: "订单管理", breadcrumbGroup: "系统管理" },
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
        meta: { title: "充值卡密管理", requiresAuth: true, adminOnly: true, breadcrumbTitle: "充值卡密", breadcrumbGroup: "系统管理" },
      },
      {
        path: "admin/announcements",
        name: "AnnouncementManagement",
        component: () => import("@/views/AnnouncementManagement.vue"),
        meta: { title: "公告管理", requiresAuth: true, adminOnly: true, breadcrumbGroup: "系统管理" },
      },
      {
        path: "admin/variables",
        name: "SystemVariableManagement",
        component: () => import("@/views/SystemVariableManagement.vue"),
        meta: { title: "系统变量管理", requiresAuth: true, adminOnly: true, breadcrumbTitle: "系统变量", breadcrumbGroup: "系统管理" },
      },
      {
        path: "admin/countdown",
        name: "CountdownManagement",
        component: () => import("@/views/CountdownManagement.vue"),
        meta: { title: "倒计时管理", requiresAuth: true, adminOnly: true, breadcrumbGroup: "系统管理" },
      },
      {
        path: "admin/aqks",
        name: "AqksStudyManagement",
        component: () => import("@/views/AqksStudyManagement.vue"),
        meta: { title: "AQKS刷课管理", requiresAuth: true, adminOnly: true, breadcrumbGroup: "系统管理" },
      },
      {
        path: "api-guide",
        name: "ApiGuide",
        component: () => import("@/views/ApiDocs.vue"),
        meta: { title: "API文档", requiresAuth: true, breadcrumbTitle: "API 文档" },
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
        meta: { title: "客服管理", requiresAuth: true, adminOnly: true, breadcrumbGroup: "系统管理" },
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
        redirect: { path: "/admin/variables", query: { type: "theme_color_light" } },
        meta: { title: "主题配置", requiresAuth: true, adminOnly: true, breadcrumbGroup: "系统管理" },
      },
      {
        path: "liquid-glass",
        name: "LiquidGlassWorkbench",
        component: () => import("@/views/LiquidGlassWorkbench.vue"),
        meta: { title: "液态玻璃工作台", requiresAuth: false },
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
  {
    path: "/standalone/liquid-glass",
    name: "LiquidGlassStandalone",
    component: () => import("@/views/LiquidGlassWorkbench.vue"),
    meta: { title: "液态玻璃工作台" },
  },
];

export default routes;
