<template>
  <el-container
    class="main-container fluent-spatial-stage"
    data-spatial-shell
  >
    <!-- 空间背景只提供环境光与透视参照，不参与交互。 -->
    <div class="spatial-backdrop" aria-hidden="true">
      <span class="spatial-glow spatial-glow--blue"></span>
      <span class="spatial-glow spatial-glow--cyan"></span>
      <span class="spatial-grid"></span>
    </div>
    <el-aside
      :width="isCollapse ? '64px' : '252px'"
      class="sidebar fluent-acrylic"
      :class="{
        collapsed: isCollapse,
        'mobile-visible': isMobileMenuVisible && isMobile,
      }"
    >
      <div class="logo" data-depth="brand">
        <span class="logo-device" aria-hidden="true">
          <i></i><i></i><i></i><i></i>
        </span>
        <div v-if="!isCollapse" class="logo-copy">
          <span class="logo-kicker">LEARNING OS</span>
          <h2>{{ settings.site_name || "二开台" }}</h2>
        </div>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="menu"
        :router="true"
        :collapse="isCollapse"
        background-color="transparent"
        @select="handleMenuSelect"
      >
        <el-menu-item index="/dashboard">
          <el-icon><HomeFilled /></el-icon>
          <template #title>首页</template>
        </el-menu-item>

        <el-menu-item index="/orders">
          <el-icon><Document /></el-icon>
          <template #title>订单管理</template>
        </el-menu-item>

        <el-menu-item index="/courses">
          <el-icon><Reading /></el-icon>
          <template #title>课程列表</template>
        </el-menu-item>

        <el-menu-item index="/users">
          <el-icon><User /></el-icon>
          <template #title>代理管理</template>
        </el-menu-item>

        <el-menu-item index="/price-list">
          <el-icon><Tickets /></el-icon>
          <template #title>价格列表</template>
        </el-menu-item>

        <el-menu-item index="/recharge">
          <el-icon><Money /></el-icon>
          <template #title>账户充值</template>
        </el-menu-item>

        <el-menu-item index="/api-guide">
          <el-icon><Money /></el-icon>
          <template #title>对接文档</template>
        </el-menu-item>

        <el-menu-item index="/logs">
          <el-icon><List /></el-icon>
          <template #title>操作日志</template>
        </el-menu-item>

        <el-sub-menu v-if="userStore.isAdmin" index="admin">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/admin/platforms">课程平台</el-menu-item>
          <el-menu-item index="/admin/api-providers">接口配置</el-menu-item>
          <el-menu-item index="/admin/orders">订单管理</el-menu-item>
          <el-menu-item index="/admin/cards">充值卡密</el-menu-item>
          <el-menu-item index="/admin/announcements">公告管理</el-menu-item>
          <el-menu-item index="/admin/customer-service">客服管理</el-menu-item>
          <el-menu-item index="/admin/variables">系统变量</el-menu-item>
          <el-menu-item index="/admin/countdown">倒计时管理</el-menu-item>
          <el-menu-item index="/admin/aqks">AQKS刷课管理</el-menu-item>
          <el-menu-item index="/settings">系统设置</el-menu-item>
        </el-sub-menu>
      </el-menu>

      <div class="sidebar-footer" v-if="!isCollapse">
        <div class="footer-links">
          <router-link to="/service-agreement" class="footer-link"
            >服务协议</router-link
          >
          <span class="divider">|</span>
          <router-link to="/privacy-policy" class="footer-link"
            >隐私政策</router-link
          >
        </div>
        <div class="copyright">Copyright © 2025 二开台</div>
      </div>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <!-- 移动端菜单按钮 -->
          <el-button
            v-if="isMobile"
            type="text"
            @click="toggleMobileMenu"
            class="mobile-menu-btn"
            :icon="Menu"
          />
          <!-- 桌面端折叠按钮 -->
          <el-button
            v-if="!isMobile"
            type="text"
            @click="toggleCollapse"
            class="collapse-btn"
            :icon="isCollapse ? Expand : Fold"
          />
          <el-breadcrumb separator="/" class="breadcrumb">
            <el-breadcrumb-item
              v-for="item in breadcrumbList"
              :key="item.path"
              :to="item.path"
              @click="handleBreadcrumbClick(item.path)"
              style="cursor: pointer"
            >
              {{ item.name }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <div v-if="!isMobile" class="system-status" aria-label="系统运行正常">
            <span class="system-status-dot"></span>
            <span>服务在线</span>
          </div>
          <ThemeToggle />
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-icon><UserFilled /></el-icon>
              {{ userStore.userInfo?.nickname || "用户" }}
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled
                  >余额: ¥{{
                    userStore.userInfo?.balance || 0
                  }}</el-dropdown-item
                >
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>
                  个人中心
                </el-dropdown-item>
                <el-dropdown-item command="changePassword"
                  >修改密码</el-dropdown-item
                >
                <el-dropdown-item divided command="logout"
                  >退出登录</el-dropdown-item
                >
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 标签页导航 -->
      <TagsView />

      <el-main class="main-content" data-depth="workspace">
        <router-view v-slot="{ Component, route }">
          <transition name="fade-transform" mode="out-in">
            <keep-alive :include="cachedViews">
              <component :is="Component" :key="route.path" />
            </keep-alive>
          </transition>
        </router-view>
      </el-main>
    </el-container>

    <!-- 修改密码对话框 -->
    <el-dialog
      v-model="passwordDialogVisible"
      title="修改密码"
      width="400px"
      :close-on-click-modal="false"
      append-to-body
    >
      <el-form :model="passwordForm" label-width="100px">
        <el-form-item label="当前密码">
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            placeholder="请输入当前密码"
            show-password
          />
        </el-form-item>

        <el-form-item label="新密码">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            placeholder="请输入新密码"
            show-password
          />
        </el-form-item>

        <el-form-item label="确认新密码">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="handleChangePassword"
          :loading="changingPassword"
        >
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 系统公告弹窗 -->
    <el-dialog
      v-model="announcementDialogVisible"
      title="系统公告"
      width="500px"
      :close-on-click-modal="false"
      append-to-body
      center
    >
      <div v-if="systemAnnouncement" class="announcement-popup">
        <div class="announcement-header">
          <el-icon><Bell /></el-icon>
          <h3>{{ systemAnnouncement.title }}</h3>
        </div>
        <div class="announcement-content">
          {{ systemAnnouncement.content }}
        </div>
        <div class="announcement-meta">
          <el-tag :type="getPriorityTagType(systemAnnouncement.priority)">
            {{ systemAnnouncement.priorityName }}
          </el-tag>
          <span class="publish-time">{{ systemAnnouncement.publishTime }}</span>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="announcementDialogVisible = false">
          我知道了
        </el-button>
      </template>
    </el-dialog>

    <!-- 移动端遮罩层 -->
    <div
      v-if="isMobile && isMobileMenuVisible"
      class="mobile-overlay"
      @click="closeMobileMenu"
    ></div>

    <!-- 在线客服组件 -->
    <CustomerService />
  </el-container>
</template>

<script setup>
import { computed, ref, watch, nextTick, onMounted, onUnmounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useUserStore } from "@/stores/user";
import { useTagsViewStore } from "@/stores/tagsView";
import { ElMessage } from "element-plus";
import { changePassword } from "@/api/user";
import { getSystemAnnouncement } from "@/api/announcement";
import { getSettings } from "@/api/setting";
import TagsView from "@/components/TagsView.vue";
import CustomerService from "@/components/CustomerService.vue";
import ThemeToggle from "@/components/ThemeToggle.vue";
import {
  HomeFilled,
  Document,
  Reading,
  User,
  List,
  Setting,
  Tickets,
  UserFilled,
  ArrowDown,
  Expand,
  Fold,
  Bell,
  Menu,
} from "@element-plus/icons-vue";

const settings = ref({
  site_name: "",
  site_keywords: "",
  site_description: "",
  system_notice: "",
  user_register_enabled: "1",
  user_register_fee: 5,
  min_recharge_amount: 10,
  api_enable_threshold: 300,
});

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const tagsViewStore = useTagsViewStore();

// 缓存的视图组件名称
const cachedViews = computed(() => tagsViewStore.cachedViewsList);

// 菜单折叠状态
const isCollapse = ref(false);

// 移动端菜单显示状态
const isMobileMenuVisible = ref(false);

// 移动端检测
const isMobile = ref(false);

// 检测屏幕尺寸
const checkScreenSize = () => {
  isMobile.value = window.innerWidth <= 768;
};

// 监听窗口大小变化
const handleResize = () => {
  checkScreenSize();
  // 如果切换到桌面端，关闭移动端菜单
  if (!isMobile.value) {
    isMobileMenuVisible.value = false;
  }
};

// 修改密码相关状态
const passwordDialogVisible = ref(false);
const changingPassword = ref(false);
const passwordForm = ref({
  oldPassword: "",
  newPassword: "",
  confirmPassword: "",
});

// 公告相关状态
const announcementDialogVisible = ref(false);
const systemAnnouncement = ref(null);

// 切换菜单折叠状态
const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value;
};

// 切换移动端菜单显示状态
const toggleMobileMenu = () => {
  isMobileMenuVisible.value = !isMobileMenuVisible.value;
};

// 关闭移动端菜单
const closeMobileMenu = () => {
  isMobileMenuVisible.value = false;
};

// 处理菜单选择
const handleMenuSelect = (index) => {
  // 移动端选择菜单项后自动关闭菜单
  if (isMobile.value) {
    closeMobileMenu();
  }
};

// 当前激活的菜单
const activeMenu = computed(() => route.path);

// 激活状态移动动画
const updateActiveIndicator = () => {
  const menu = document.querySelector(".menu");
  if (!menu) return;

  const activeItem = menu.querySelector(".el-menu-item.is-active");
  if (!activeItem) {
    // 隐藏指示器
    menu.style.setProperty("--active-top", "0px");
    menu.style.setProperty("--active-height", "0px");
    menu.style.setProperty("--active-opacity", "0");
    return;
  }

  const menuRect = menu.getBoundingClientRect();
  const itemRect = activeItem.getBoundingClientRect();
  // 计算相对于菜单容器内容的顶部距离，需要加上 scrollTop
  const top = itemRect.top - menuRect.top + menu.scrollTop;
  const height = itemRect.height;

  // 更新CSS变量
  menu.style.setProperty("--active-top", `${top}px`);
  menu.style.setProperty("--active-height", `${height}px`);
  menu.style.setProperty("--active-opacity", "1");
};

// 监听路由变化
watch(
  () => route.path,
  () => {
    nextTick(() => {
      updateActiveIndicator();
    });
  },
  { immediate: true }
);

// 面包屑导航
const breadcrumbList = computed(() => {
  const breadcrumbs = [];
  const pathSegments = route.path.split("/").filter((segment) => segment);

  // 添加首页
  breadcrumbs.push({ name: "首页", path: "/dashboard" });

  // 根据路径生成面包屑
  let currentPath = "";
  pathSegments.forEach((segment, index) => {
    currentPath += `/${segment}`;

    // 根据路径生成名称
    let name = "";
    switch (segment) {
      case "dashboard":
        name = "首页";
        break;
      case "orders":
        name = "订单管理";
        break;
      case "courses":
        name = "课程列表";
        break;
      case "users":
        name = "代理管理";
        break;
      case "price-list":
        name = "价格列表";
        break;
      case "logs":
        name = "操作日志";
        break;
      case "admin":
        name = "系统管理";
        break;
      case "platforms":
        name = "课程平台";
        break;
      case "api-providers":
        name = "接口配置";
        break;
      case "settings":
        name = "系统设置";
        break;
      case "announcements":
        name = "公告管理";
      case "cards":
        name = "充值卡密";
      case "variables":
        name = "系统变量";
      case "countdown":
        name = "倒计时管理";
      case "customer-service":
        name = "客服管理";
        break;
      default:
        name = segment;
    }

    // 如果是最后一个路径，不添加链接
    if (index === pathSegments.length - 1) {
      breadcrumbs.push({ name, path: "" });
    } else {
      breadcrumbs.push({ name, path: currentPath });
    }
  });

  return breadcrumbs;
});

// 处理面包屑点击
const handleBreadcrumbClick = (path) => {
  if (path) {
    router.push(path);
  }
};

const handleCommand = (command) => {
  if (command === "logout") {
    userStore.logout();
  } else if (command === "changePassword") {
    passwordDialogVisible.value = true;
    resetPasswordForm();
  } else if (command === "profile") {
    router.push("/profile");
  }
};

const handleChangePassword = async () => {
  // 验证表单
  if (!passwordForm.value.oldPassword) {
    ElMessage.error("请输入当前密码");
    return;
  }
  if (!passwordForm.value.newPassword) {
    ElMessage.error("请输入新密码");
    return;
  }
  if (passwordForm.value.newPassword.length < 6) {
    ElMessage.error("新密码长度不能少于6位");
    return;
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.error("两次输入的新密码不一致");
    return;
  }

  changingPassword.value = true;
  try {
    await changePassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword,
    });
    ElMessage.success("密码修改成功");
    passwordDialogVisible.value = false;
    resetPasswordForm();
  } catch (error) {
    console.error("修改密码失败：", error);
    ElMessage.error(error.message || "修改密码失败");
  } finally {
    changingPassword.value = false;
  }
};

const resetPasswordForm = () => {
  passwordForm.value = {
    oldPassword: "",
    newPassword: "",
    confirmPassword: "",
  };
};

// 获取优先级标签类型
const getPriorityTagType = (priority) => {
  switch (priority) {
    case 1:
      return "";
    case 2:
      return "warning";
    case 3:
      return "danger";
    default:
      return "";
  }
};

// 加载系统公告
const loadSystemAnnouncement = async () => {
  try {
    const response = await getSystemAnnouncement();
    if (response.code === 1 && response.data) {
      systemAnnouncement.value = response.data;
      // 检查是否已经显示过此公告（使用localStorage记录）
      const shownAnnouncements = JSON.parse(
        localStorage.getItem("shownAnnouncements") || "[]"
      );
      if (!shownAnnouncements.includes(response.data.id)) {
        announcementDialogVisible.value = true;
        // 记录已显示的公告
        shownAnnouncements.push(response.data.id);
        localStorage.setItem(
          "shownAnnouncements",
          JSON.stringify(shownAnnouncements)
        );
      }
    }
  } catch (error) {
    // 静默处理错误，不影响正常使用
    console.log("加载系统公告失败:", error);
  }
};

// 获取系统设置
const loadSettings = async () => {
  try {
    const response = await getSettings();
    if (response.code === 1) {
      response.data.forEach((item) => {
        settings.value[item.configKey] = item.configValue;
      });
      console.log("系统设置加载完成", settings.value);
    }
  } catch (error) {
    console.log("加载系统设置失败:", error);
  }
};

// 动态更新 SEO 信息
const updateSEO = () => {
  // 更新网站标题
  if (settings.value.site_name) {
    const routeTitle = route.meta.title;
    document.title = routeTitle
      ? `${routeTitle} - ${settings.value.site_name}`
      : settings.value.site_name;
  }

  // 更新 keywords meta 标签
  if (settings.value.site_keywords) {
    const keywordsMeta = document.getElementById("meta-keywords");
    if (keywordsMeta) {
      keywordsMeta.setAttribute("content", settings.value.site_keywords);
    }
  }

  // 更新 description meta 标签
  if (settings.value.site_description) {
    const descriptionMeta = document.getElementById("meta-description");
    if (descriptionMeta) {
      descriptionMeta.setAttribute("content", settings.value.site_description);
    }
  }
};

// 监听 settings 变化，自动更新 SEO
watch(
  () => [
    settings.value.site_name,
    settings.value.site_keywords,
    settings.value.site_description,
  ],
  () => {
    updateSEO();
  },
  { deep: true }
);

// 监听路由变化，更新页面标题
watch(
  () => route.path,
  () => {
    if (settings.value.site_name) {
      const routeTitle = route.meta.title;
      document.title = routeTitle
        ? `${routeTitle} - ${settings.value.site_name}`
        : settings.value.site_name;
    }
  }
);

// 组件挂载时加载系统公告
onMounted(() => {
  loadSystemAnnouncement();
  loadSettings();
  // 初始化屏幕尺寸检测
  checkScreenSize();
  // 添加窗口大小变化监听
  window.addEventListener("resize", handleResize);
});

// 组件卸载时清理事件监听
onUnmounted(() => {
  window.removeEventListener("resize", handleResize);
});
</script>

<style scoped>
.main-container {
  height: 100vh;
}

.sidebar {
  /* 现代玻璃拟态效果 - 白色半透明背景 */
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-right: 1px solid rgba(255, 255, 255, 0.2);
  overflow-x: hidden;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
  position: relative;
  /* 添加宽度变化的过渡动画 */
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
}

.sidebar-footer {
  padding: 20px 0;
  text-align: center;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  background: var(--sidebar-footer-bg, transparent);
}

.footer-links {
  margin-bottom: 8px;
  font-size: 12px;
}

.footer-link {
  color: rgba(255, 255, 255, 0.7);
  text-decoration: none;
  transition: color 0.3s;
  color: var(--text-primary);
}

.footer-link:hover {
  color: #fff;
}

.divider {
  margin: 0 8px;
  color: rgba(255, 255, 255, 0.3);
}

.copyright {
  font-size: 12px;
  font-family: var(--font-family);
  color: var(--text-secondary);
  transform: scale(0.9);
}

/* 添加微妙的渐变叠加层 */
.sidebar::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(
    135deg,
    rgba(255, 255, 255, 0.1) 0%,
    rgba(255, 255, 255, 0.05) 50%,
    rgba(255, 255, 255, 0.1) 100%
  );
  pointer-events: none;
  z-index: 1;
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 现代化渐变背景 */
  background: var(--primary-gradient);
  position: relative;
  overflow: hidden;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  z-index: 2;
}

.logo::before {
  content: "";
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: linear-gradient(
    45deg,
    transparent,
    rgba(255, 255, 255, 0.15),
    transparent
  );
  transform: rotate(45deg);
  animation: shine 4s infinite;
}

@keyframes shine {
  0% {
    transform: translateX(-100%) rotate(45deg);
  }
  100% {
    transform: translateX(100%) rotate(45deg);
  }
}

.logo h2 {
  color: #fff;
  font-size: 22px;
  margin: 0;
  font-weight: 600;
  letter-spacing: 2px;
  position: relative;
  z-index: 1;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
  /* 添加文字过渡动画 */
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  transform-origin: center;
}

/* 折叠状态下的Logo文字动画 */
.sidebar.collapsed .logo h2 {
  font-size: 18px;
  letter-spacing: 1px;
  transform: scale(0.9);
}

.sidebar:not(.collapsed) .logo h2 {
  font-size: 22px;
  letter-spacing: 2px;
  transform: scale(1);
}

.menu {
  border-right: none;
  background: transparent;
  z-index: 2;
  position: relative;
  /* 添加激活状态移动动画的容器 */
  overflow: visible;
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

/* 激活状态背景移动动画 */
.menu::before {
  content: "";
  position: absolute;
  left: 8px;
  right: 8px;
  top: var(--active-top, 0px);
  height: var(--active-height, 0px);
  background: color-mix(in srgb, var(--brand-primary) 15%, transparent);
  border-radius: 8px;
  box-shadow: 0 4px 12px color-mix(in srgb, var(--brand-primary) 20%, transparent),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  opacity: var(--active-opacity, 0);
  pointer-events: none;
  z-index: 1;
}

/* 激活状态指示器 */
.menu::after {
  content: "";
  position: absolute;
  left: 0;
  top: var(--active-top, 0px);
  height: var(--active-height, 0px);
  width: 3px;
  background: var(--primary-gradient);
  border-radius: 0 2px 2px 0;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  opacity: var(--active-opacity, 0);
  pointer-events: none;
  z-index: 2;
}

/* 现代化菜单项样式 */
.menu .el-menu-item,
.menu :deep(.el-sub-menu__title) {
  color: var(--text-primary) !important;
  font-weight: 500;
  margin: 4px 8px;
  border-radius: 8px;
}

.menu :deep(.el-sub-menu__title:hover),
.menu .el-menu-item:hover {
  background-color: rgba(255, 255, 255, 0.2) !important;
  color: var(--primary-gradient-start) !important;
}

/* Dark Mode Sidebar */
html.dark .sidebar {
  background: rgba(30, 41, 59, 0.8);
  border-right: 1px solid rgba(255, 255, 255, 0.05);
}

html.dark .menu .el-menu-item,
html.dark .menu :deep(.el-sub-menu__title) {
  color: var(--text-regular) !important;
}

html.dark .menu .el-menu-item:hover,
html.dark .menu :deep(.el-sub-menu__title:hover) {
  background-color: rgba(255, 255, 255, 0.05) !important;
  color: #fff !important;
}

html.dark .menu .el-menu-item.is-active {
  color: #fff !important;
  background-color: rgba(255, 255, 255, 0.1) !important;
}

/* 菜单项通用样式 */
.menu .el-menu-item,
.menu :deep(.el-sub-menu__title) {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
  transform-origin: left center;
}

.menu .el-menu-item:hover {
  background: rgba(0, 0, 0, 0.08) !important;
  color: rgba(0, 0, 0, 0.95) !important;
  transform: translateX(4px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.menu .el-menu-item.is-active {
  background: transparent !important;
  color: rgba(0, 0, 0, 0.95) !important;
  position: relative;
  z-index: 3;
}

.menu .el-menu-item.is-active::before {
  display: none;
}

/* 折叠状态下的菜单项样式 */
.sidebar.collapsed .menu .el-menu-item {
  margin: 4px 4px;
  padding-left: 20px !important;
  justify-content: center;
}

.sidebar.collapsed .menu .el-menu-item .el-menu-item__title {
  opacity: 0;
  transform: translateX(-10px);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.sidebar:not(.collapsed) .menu .el-menu-item .el-menu-item__title {
  opacity: 1;
  transform: translateX(0);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 折叠状态下图标居中 */
.sidebar.collapsed .menu .el-menu-item .el-icon {
  margin-right: 0 !important;
  transform: translateX(0);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.sidebar:not(.collapsed) .menu .el-menu-item .el-icon {
  margin-right: 8px;
  transform: translateX(0);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 子菜单样式优化 */
.menu .el-sub-menu .el-sub-menu__title {
  color: rgba(0, 0, 0, 0.8) !important;
  font-weight: 500;
  margin: 4px 8px;
  border-radius: 8px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  transform-origin: left center;
}

.menu .el-sub-menu .el-sub-menu__title:hover {
  background: rgba(0, 0, 0, 0.08) !important;
  color: rgba(0, 0, 0, 0.95) !important;
  transform: translateX(4px);
}

/* 折叠状态下的子菜单样式 */
.sidebar.collapsed .menu .el-sub-menu .el-sub-menu__title {
  margin: 4px 4px;
  padding-left: 20px !important;
  justify-content: center;
}

.sidebar.collapsed .menu .el-sub-menu .el-sub-menu__title span {
  opacity: 0;
  transform: translateX(-10px);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.sidebar:not(.collapsed) .menu .el-sub-menu .el-sub-menu__title span {
  opacity: 1;
  transform: translateX(0);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.sidebar.collapsed .menu .el-sub-menu .el-sub-menu__title .el-icon {
  margin-right: 0 !important;
  transform: translateX(0);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.sidebar:not(.collapsed) .menu .el-sub-menu .el-sub-menu__title .el-icon {
  margin-right: 8px;
  transform: translateX(0);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.menu .el-sub-menu .el-menu-item {
  margin: 2px 16px;
  padding-left: 20px !important;
  /* 子菜单项动画 */
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  transform-origin: left center;
  opacity: 1;
  transform: translateX(0);
}

/* 子菜单项悬停效果 */
.menu .el-sub-menu .el-menu-item:hover {
  background: rgba(0, 0, 0, 0.08) !important;
  color: rgba(0, 0, 0, 0.95) !important;
  transform: translateX(4px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

/* 子菜单展开/收起动画 */
.menu .el-sub-menu .el-menu-item {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 子菜单收起时的动画 */
.menu .el-sub-menu.is-opened .el-menu-item {
  opacity: 1;
  transform: translateX(0);
  max-height: 40px;
  margin: 2px 16px;
  padding: 8px 20px;
}

.menu .el-sub-menu:not(.is-opened) .el-menu-item {
  opacity: 0;
  transform: translateX(-20px);
  max-height: 0;
  margin: 0 16px;
  padding: 0 20px;
  overflow: hidden;
}

/* 子菜单项逐个出现动画 */
.menu .el-sub-menu.is-opened .el-menu-item:nth-child(1) {
  transition-delay: 0.05s;
}

.menu .el-sub-menu.is-opened .el-menu-item:nth-child(2) {
  transition-delay: 0.1s;
}

.menu .el-sub-menu.is-opened .el-menu-item:nth-child(3) {
  transition-delay: 0.15s;
}

.menu .el-sub-menu.is-opened .el-menu-item:nth-child(4) {
  transition-delay: 0.2s;
}

.menu .el-sub-menu.is-opened .el-menu-item:nth-child(5) {
  transition-delay: 0.25s;
}

/* 收起时的反向延迟 */
.menu .el-sub-menu:not(.is-opened) .el-menu-item:nth-child(1) {
  transition-delay: 0.25s;
}

.menu .el-sub-menu:not(.is-opened) .el-menu-item:nth-child(2) {
  transition-delay: 0.2s;
}

.menu .el-sub-menu:not(.is-opened) .el-menu-item:nth-child(3) {
  transition-delay: 0.15s;
}

.menu .el-sub-menu:not(.is-opened) .el-menu-item:nth-child(4) {
  transition-delay: 0.1s;
}

.menu .el-sub-menu:not(.is-opened) .el-menu-item:nth-child(5) {
  transition-delay: 0.05s;
}

/* 子菜单标题的展开/收起动画 */
.menu .el-sub-menu .el-sub-menu__title .el-sub-menu__icon-arrow {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.menu .el-sub-menu.is-opened .el-sub-menu__title .el-sub-menu__icon-arrow {
  transform: rotate(180deg);
}

.menu
  .el-sub-menu:not(.is-opened)
  .el-sub-menu__title
  .el-sub-menu__icon-arrow {
  transform: rotate(0deg);
}

.header {
  background-color: #fff;
  box-shadow: 0 2px 8px rgba(0, 21, 41, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  backdrop-filter: blur(10px);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  font-size: 18px;
  color: #606266;
  padding: 8px;
  border-radius: 4px;
  transition: all 0.3s;
}

.collapse-btn:hover {
  background-color: #f5f7fa;
  color: #409eff;
}

/* 移动端菜单按钮样式 */
.mobile-menu-btn {
  font-size: 18px;
  color: rgb(229, 237, 253);
  padding: 8px;
  border-radius: 4px;
  transition: all 0.3s;
  margin-right: 8px;
}

.mobile-menu-btn:hover {
  background-color: #f5f7fa;
  color: #409eff;
}

.breadcrumb {
  font-size: 14px;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 0 16px;
  height: 60px;
  line-height: 60px;
  border-radius: 8px;
  transition: all 0.3s;
}

.user-info:hover {
  background-color: #f5f7fa;
  transform: translateY(-2px);
}

.main-content {
  /* 现代化背景渐变，与玻璃拟态侧边栏协调 */
  background: linear-gradient(
    135deg,
    rgba(248, 250, 252, 0.8) 0%,
    rgba(241, 245, 249, 0.9) 50%,
    rgba(236, 242, 248, 0.8) 100%
  );
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  padding: 24px;
  overflow-y: auto;
  min-height: calc(
    100vh - 144px
  ); /* 调整高度：header(60px) + tags(44px) + 额外空间(40px) */
  position: relative;
}

/* 添加微妙的纹理效果 */
.main-content::before {
  content: "";
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image: radial-gradient(
      circle at 20% 80%,
      color-mix(in srgb, var(--brand-primary) 3%, transparent) 0%,
      transparent 50%
    ),
    radial-gradient(
      circle at 80% 20%,
      color-mix(in srgb, var(--brand-violet) 3%, transparent) 0%,
      transparent 50%
    );
  pointer-events: none;
  z-index: -1;
}

/* 移动端遮罩层 */
.mobile-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 999;
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    height: 100vh;
    z-index: 1000;
    /* 移动端默认隐藏在左侧 - 使用 translate3d 同时支持硬件加速 */
    transform: translate3d(-100%, 0, 0);
    transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    /* 移动端优化的玻璃拟态效果 - 提升亮度和层次感 */
    background: rgba(255, 255, 255, 0.2);
    backdrop-filter: blur(18px);
    -webkit-backdrop-filter: blur(18px);
    width: 280px !important; /* 移动端固定宽度 */
    /* 添加内部高光，提升亮度 */
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.4),
      0 8px 32px rgba(0, 0, 0, 0.12), 0 2px 8px rgba(0, 0, 0, 0.08);
    /* 添加边框增强层次感 */
    border-right: 1px solid rgba(255, 255, 255, 0.3);
    /* 硬件加速优化 */
    will-change: transform;
  }

  /* 移动端侧边栏添加亮度提升层 */
  .sidebar::before {
    /* 移动端使用更明亮的渐变叠加 */
    background: linear-gradient(
      135deg,
      rgba(255, 255, 255, 0.15) 0%,
      rgba(255, 255, 255, 0.08) 50%,
      rgba(255, 255, 255, 0.15) 100%
    );
  }

  .sidebar.mobile-visible {
    /* 显示时移动到视口内 - 使用 translate3d 保持硬件加速 */
    transform: translate3d(0, 0, 0);
  }

  .main-container {
    margin-left: 0;
  }

  .header {
    padding: 0 16px;
    /* 移动端头部也添加玻璃拟态效果 */
    background: rgba(255, 255, 255, 0.9);
    backdrop-filter: blur(15px);
    -webkit-backdrop-filter: blur(15px);
  }

  .main-content {
    padding: 16px;
    /* 移动端优化背景 */
    background: linear-gradient(
      135deg,
      rgba(248, 250, 252, 0.95) 0%,
      rgba(241, 245, 249, 0.98) 100%
    );
    /* 移动端调整高度计算 */
    min-height: calc(
      100vh - 140px
    ); /* header(60px) + tags(40px) + 额外空间(40px) */
  }

  .breadcrumb {
    display: none;
  }

  /* 移动端菜单项优化 */
  .menu .el-menu-item {
    margin: 2px 8px;
    font-size: 14px;
    padding: 12px 16px !important;
  }

  .menu .el-sub-menu .el-menu-item {
    margin: 1px 16px;
    padding: 8px 20px !important;
  }

  .menu .el-sub-menu .el-sub-menu__title {
    margin: 2px 8px;
    padding: 12px 16px !important;
  }

  /* 移动端Logo优化 */
  .logo h2 {
    font-size: 20px;
    letter-spacing: 1px;
  }
}

@media (max-width: 480px) {
  .header {
    padding: 0 12px;
  }

  .main-content {
    padding: 12px;
  }

  .logo h2 {
    font-size: 18px;
  }
}

/* 页面切换过渡动画 */
.fade-transform-leave-active,
.fade-transform-enter-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(30px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(-30px);
}

/* 公告弹窗样式 */
.announcement-popup {
  padding: 20px 0;
}

.announcement-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.announcement-header .el-icon {
  font-size: 20px;
  color: #409eff;
}

.announcement-header h3 {
  margin: 0;
  color: #303133;
  font-size: 18px;
  font-weight: 600;
}

.announcement-content {
  line-height: 1.6;
  color: #606266;
  margin-bottom: 16px;
  white-space: pre-wrap;
  min-height: 60px;
}

.announcement-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.publish-time {
  color: #909399;
  font-size: 12px;
}

/* Dark Mode 样式优化 */
html.dark .sidebar {
  background: rgba(30, 30, 30, 0.6);
  border-right: 1px solid rgba(255, 255, 255, 0.05);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.05);
}

html.dark .sidebar::before {
  background: linear-gradient(
    135deg,
    rgba(0, 0, 0, 0.2) 0%,
    rgba(0, 0, 0, 0.1) 50%,
    rgba(0, 0, 0, 0.2) 100%
  );
}

html.dark .logo {
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--brand-primary) 20%, transparent),
    color-mix(in srgb, var(--brand-violet) 20%, transparent)
  );
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

html.dark .header {
  background: rgba(30, 30, 30, 0.8);
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

html.dark .main-content {
  background: linear-gradient(
    135deg,
    rgba(15, 23, 42, 0.8) 0%,
    rgba(30, 41, 59, 0.9) 50%,
    rgba(15, 23, 42, 0.8) 100%
  );
}

/* Dark Mode 公告弹窗 */
html.dark .announcement-header h3 {
  color: #e5e7eb;
}

html.dark .announcement-content {
  color: #d1d5db;
}

html.dark .user-info {
  color: #e5e7eb;
}

html.dark .user-info:hover {
  background-color: rgba(255, 255, 255, 0.05);
}
</style>

<style scoped>
/* Fluent 空间壳层：侧栏与命令栏保持低 elevation，内容区拥有独立透视舞台。 */
.main-container {
  position: relative;
  isolation: isolate;
  min-height: 100vh;
  overflow: hidden;
  color: var(--text-regular);
  background:
    radial-gradient(circle at 82% -10%, rgba(0, 183, 195, 0.16), transparent 31%),
    radial-gradient(circle at 18% 16%, rgba(15, 108, 189, 0.16), transparent 28%),
    linear-gradient(145deg, var(--bg-body), color-mix(in srgb, var(--bg-body) 90%, #d8edff));
}

.spatial-backdrop {
  position: fixed;
  z-index: -1;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.spatial-grid {
  position: absolute;
  inset: 0;
  opacity: 0.42;
  background-image:
    linear-gradient(rgba(15, 108, 189, 0.055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15, 108, 189, 0.055) 1px, transparent 1px);
  background-size: 42px 42px;
  mask-image: linear-gradient(to bottom, black, transparent 88%);
  transform: perspective(900px) rotateX(58deg) scale(1.5) translateY(22%);
  transform-origin: center bottom;
}

.spatial-glow {
  position: absolute;
  width: 38vw;
  aspect-ratio: 1;
  border-radius: 50%;
  opacity: 0.22;
  filter: blur(80px);
}

.spatial-glow--blue {
  top: -18vw;
  right: 10vw;
  background: #1b7fd1;
}

.spatial-glow--cyan {
  right: -14vw;
  bottom: -18vw;
  background: #00b7c3;
}

.sidebar {
  position: relative;
  z-index: 12;
  display: flex;
  flex-direction: column;
  flex: 0 0 auto;
  height: calc(100vh - 20px);
  margin: 10px 0 10px 10px;
  overflow: hidden;
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-xl);
  background: color-mix(in srgb, var(--surface-mica) 88%, transparent) !important;
  box-shadow:
    inset 0 1px 0 var(--stroke-highlight),
    var(--shadow-lg);
  backdrop-filter: blur(28px) saturate(1.2);
  transition:
    width var(--motion-base) cubic-bezier(0.16, 1, 0.3, 1),
    transform var(--motion-base) cubic-bezier(0.16, 1, 0.3, 1);
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 76px;
  padding: 0 17px;
  border-bottom: 1px solid var(--border-color-light);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.08), transparent);
}

.logo-device {
  position: relative;
  display: grid;
  flex: 0 0 34px;
  grid-template-columns: repeat(2, 7px);
  place-content: center;
  gap: 4px;
  width: 34px;
  height: 34px;
  border: 1px solid color-mix(in srgb, var(--brand-primary) 38%, var(--border-color));
  border-radius: 10px;
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.32), transparent),
    var(--primary-gradient);
  box-shadow:
    inset 0 1px 1px rgba(255, 255, 255, 0.42),
    0 8px 18px color-mix(in srgb, var(--brand-primary) 26%, transparent);
  transform: translateZ(18px) rotateY(-8deg);
}

.logo-device i {
  width: 7px;
  height: 7px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.12);
}

.logo-copy {
  min-width: 0;
}

.logo-kicker {
  display: block;
  margin-bottom: 2px;
  color: var(--brand-primary);
  font-size: 9px;
  font-weight: 750;
  letter-spacing: 0.16em;
}

.logo h2 {
  overflow: hidden;
  margin: 0;
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 680;
  letter-spacing: -0.02em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu {
  flex: 1;
  padding: 12px 8px;
  overflow: hidden auto;
  border-right: 0;
  background: transparent;
}

.menu :deep(.el-menu-item),
.menu :deep(.el-sub-menu__title) {
  height: 42px;
  margin: 3px 0;
  border-radius: 10px;
  color: var(--text-secondary);
  font-weight: 560;
  transition:
    color var(--motion-fast) ease,
    background-color var(--motion-fast) ease,
    transform var(--motion-fast) ease;
}

.menu :deep(.el-menu-item:hover),
.menu :deep(.el-sub-menu__title:hover) {
  color: var(--text-primary);
  background: color-mix(in srgb, var(--brand-primary) 8%, transparent);
  transform: translateX(2px);
}

.menu :deep(.el-menu-item.is-active) {
  color: var(--brand-primary);
  background:
    linear-gradient(90deg, color-mix(in srgb, var(--brand-primary) 15%, transparent), transparent 92%);
  box-shadow: inset 3px 0 0 var(--brand-primary);
}

.menu :deep(.el-menu-item.is-active .el-icon) {
  filter: drop-shadow(0 2px 5px color-mix(in srgb, var(--brand-primary) 34%, transparent));
}

.sidebar-footer {
  padding: 14px 16px 18px;
  border-top: 1px solid var(--border-color-light);
  color: var(--text-placeholder);
}

.header {
  position: relative;
  z-index: 10;
  height: var(--header-height);
  margin: 10px 10px 0;
  padding: 0 16px;
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--surface-acrylic) 88%, transparent) !important;
  box-shadow:
    inset 0 1px 0 var(--stroke-highlight),
    var(--shadow-sm);
  backdrop-filter: blur(24px) saturate(1.18);
}

.collapse-btn,
.mobile-menu-btn {
  width: 36px;
  height: 36px;
  border: 1px solid var(--border-color-light) !important;
  border-radius: 10px;
  color: var(--text-regular);
  background: color-mix(in srgb, var(--surface-solid) 62%, transparent) !important;
}

.breadcrumb :deep(.el-breadcrumb__inner) {
  color: var(--text-secondary);
  font-weight: 520;
}

.breadcrumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--text-primary);
  font-weight: 650;
}

.system-status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 32px;
  padding: 0 11px;
  border: 1px solid color-mix(in srgb, var(--color-success) 24%, var(--border-color-light));
  border-radius: 999px;
  color: var(--text-secondary);
  background: color-mix(in srgb, var(--color-success) 7%, transparent);
  font-size: 12px;
  font-weight: 600;
}

.system-status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-success);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--color-success) 14%, transparent);
}

.user-info {
  min-height: 38px;
  padding: 0 12px;
  border: 1px solid var(--border-color-light);
  border-radius: 12px;
  color: var(--text-primary);
  background: color-mix(in srgb, var(--surface-solid) 58%, transparent);
}

.main-content {
  position: relative;
  z-index: 2;
  padding: 18px 20px 26px;
  overflow-x: hidden;
  perspective: 1300px;
  transform-style: preserve-3d;
}

:global(html.dark) .main-container {
  background:
    radial-gradient(circle at 82% -10%, rgba(56, 213, 222, 0.12), transparent 30%),
    radial-gradient(circle at 18% 14%, rgba(71, 158, 245, 0.12), transparent 28%),
    linear-gradient(145deg, #07111f, #091729 60%, #06101c);
}

:global(html.dark) .spatial-grid {
  opacity: 0.52;
  background-image:
    linear-gradient(rgba(71, 158, 245, 0.075) 1px, transparent 1px),
    linear-gradient(90deg, rgba(71, 158, 245, 0.075) 1px, transparent 1px);
}

@media (max-width: 768px) {
  .sidebar {
    height: 100vh;
    margin: 0;
    border-radius: 0 var(--radius-xl) var(--radius-xl) 0;
  }

  .header {
    margin: 8px 8px 0;
  }

  .main-content {
    padding: 14px 12px 22px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .logo-device,
  .spatial-grid {
    transform: none;
  }
}
</style>
