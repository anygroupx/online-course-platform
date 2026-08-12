<template>
  <div class="dashboard fluent-spatial-stage" data-spatial-page="dashboard">
    <section class="hero-panel fluent-depth-card" data-depth="hero">
      <div class="hero-copy">
        <div class="hero-kicker">
          <span class="hero-kicker-dot"></span>
          LEARNING OPERATIONS · LIVE
        </div>
        <h1>学习业务，尽在掌控</h1>
        <p>把课程、订单与账户状态汇聚到同一座 Fluent 空间控制台。</p>
        <div class="hero-actions">
          <button type="button" class="hero-action hero-action--primary" @click="router.push('/orders')">
            创建新订单
            <el-icon><ArrowRight /></el-icon>
          </button>
          <button type="button" class="hero-action hero-action--secondary" @click="router.push('/courses')">
            浏览课程库
          </button>
        </div>
      </div>

      <!-- 仪表装置使用真实模板语义连接 CSS 3D，而不是仅声明未使用的透视样式。 -->
      <div
        v-if="!isMobile"
        class="hero-visual"
        data-tilt-axis="xy"
        aria-hidden="true"
      >
        <div class="hero-orb hero-orb--left">
          <el-icon><Bell /></el-icon>
        </div>
        <div class="hero-orb hero-orb--top">
          <el-icon><Wallet /></el-icon>
        </div>
        <div class="hero-device" data-depth="device">
          <div class="hero-device-side-card"></div>
          <div class="hero-device-screen">
            <div class="hero-device-header">
              <span></span><span></span><span></span>
            </div>
            <div class="hero-bars">
              <span class="hero-bar hero-bar--tall"></span>
              <span class="hero-bar hero-bar--medium"></span>
              <span class="hero-bar hero-bar--short"></span>
            </div>
          </div>
        </div>
        <span class="hero-pillar hero-pillar--a"></span>
        <span class="hero-pillar hero-pillar--b"></span>
        <span class="hero-pillar hero-pillar--c"></span>
      </div>
    </section>

    <section class="dashboard-grid dashboard-grid--top">
      <article class="content-panel announcements-panel fluent-depth-card" data-depth="2">
        <header class="panel-header">
          <div class="panel-title">
            <span class="panel-icon panel-icon--blue">
              <el-icon><Bell /></el-icon>
            </span>
            <span>最新公告</span>
          </div>
          <button
            type="button"
            class="panel-more"
            @click="handleViewMoreAnnouncements"
          >
            更多
            <el-icon><ArrowRight /></el-icon>
          </button>
        </header>

        <div v-if="displayAnnouncements.length" class="announcement-list">
          <button
            v-for="announcement in displayAnnouncements"
            :key="announcement.id"
            type="button"
            class="announcement-row"
            @click="handleAnnouncementClick(announcement)"
          >
            <span class="announcement-dot"></span>
            <span class="announcement-text">{{ announcement.title }}</span>
            <span class="announcement-date">
              {{ formatAnnouncementDate(announcement.publishTime) }}
            </span>
          </button>
        </div>

        <div v-else class="panel-empty">暂无最新公告</div>
      </article>

      <article class="content-panel metric-panel metric-panel--balance fluent-depth-card" data-depth="2">
        <div class="panel-title">
          <span class="panel-icon panel-icon--blue">
            <el-icon><Wallet /></el-icon>
          </span>
          <span>账户余额</span>
        </div>
        <div class="metric-value metric-value--blue">
          ¥ {{ formatCurrency(userInfo?.balance) }}
        </div>
        <div class="metric-subtext">可用于充值与订单支付</div>
        <div class="panel-watermark">
          <el-icon><Wallet /></el-icon>
        </div>
      </article>

      <button
        type="button"
        class="content-panel action-panel action-panel--blue fluent-depth-card"
        data-depth="3"
        @click="router.push('/orders')"
      >
        <div class="action-panel-main">
          <span class="panel-icon panel-icon--blue panel-icon--large">
            <el-icon><Document /></el-icon>
          </span>
          <span class="action-panel-text">点击查看订单</span>
        </div>
        <span class="action-arrow">
          <el-icon><ArrowRight /></el-icon>
        </span>
      </button>
    </section>

    <section class="dashboard-grid dashboard-grid--stats">
      <article class="content-panel stat-panel fluent-depth-card" data-depth="2">
        <div class="panel-title">
          <span class="panel-icon panel-icon--blue">
            <el-icon><Document /></el-icon>
          </span>
          <span>我的订单</span>
        </div>
        <div class="stat-inline">
          <span class="stat-label">今日：</span>
          <span class="metric-value metric-value--blue">
            {{ formatInteger(statistics?.todayOrders) }}
          </span>
        </div>
        <div class="metric-subtext">
          累计 {{ formatInteger(userInfo?.totalOrders) }} 单
        </div>
        <div class="panel-watermark">
          <el-icon><Document /></el-icon>
        </div>
      </article>

      <article class="content-panel stat-panel fluent-depth-card" data-depth="2">
        <div class="panel-title">
          <span class="panel-icon panel-icon--green">
            <el-icon><DataAnalysis /></el-icon>
          </span>
          <span>我的费率</span>
        </div>
        <div class="metric-value metric-value--green">
          {{ formatRate(userInfo?.rate) }}倍
        </div>
        <div class="metric-subtext">当前结算倍率</div>
        <div class="panel-watermark">
          <span class="watermark-symbol">%</span>
        </div>
      </article>

      <button
        type="button"
        class="content-panel action-panel action-panel--green fluent-depth-card"
        data-depth="3"
        @click="router.push('/price-list')"
      >
        <div class="action-panel-main">
          <span class="panel-icon panel-icon--green panel-icon--large">
            <el-icon><PriceTag /></el-icon>
          </span>
          <span class="action-panel-text">点击查看价格</span>
        </div>
        <span class="action-arrow action-arrow--green">
          <el-icon><ArrowRight /></el-icon>
        </span>
      </button>

      <article class="content-panel stat-panel fluent-depth-card" data-depth="2">
        <div class="panel-title">
          <span class="panel-icon panel-icon--orange">
            <el-icon><Money /></el-icon>
          </span>
          <span>累计消费</span>
        </div>
        <div class="stat-inline">
          <span class="stat-label">今日：</span>
          <span class="metric-value metric-value--orange">
            ¥ {{ formatCurrency(statistics?.todayAmount) }}
          </span>
        </div>
        <div class="metric-subtext">
          累计 ¥ {{ formatCurrency(userInfo?.totalRecharge) }}
        </div>
        <div class="panel-watermark">
          <el-icon><Money /></el-icon>
        </div>
      </article>
    </section>

    <section class="quick-panel fluent-depth-card" data-depth="1">
      <div class="quick-panel-title">快速操作：常用入口</div>

      <div class="quick-actions">
        <button
          v-for="action in quickActions"
          :key="action.label"
          type="button"
          class="quick-action"
          @click="action.action"
        >
          <span class="quick-action-icon" :class="`quick-action-icon--${action.tone}`">
            <el-icon><component :is="action.icon" /></el-icon>
          </span>
          <span class="quick-action-label">{{ action.label }}</span>
        </button>
      </div>

      <div v-if="agentOverviewItems.length" class="agent-overview">
        <div
          v-for="item in agentOverviewItems"
          :key="item.label"
          class="agent-overview-item"
        >
          <span class="agent-overview-label">{{ item.label }}</span>
          <span class="agent-overview-value" :class="item.tone">
            {{ item.value }}
          </span>
        </div>
      </div>
    </section>

    <el-dialog
      v-model="announcementDialogVisible"
      :title="selectedAnnouncement?.title || '公告详情'"
      width="560px"
      :close-on-click-modal="true"
    >
      <div v-if="selectedAnnouncement" class="announcement-dialog">
        <div class="announcement-dialog-meta">
          <el-tag :type="getPriorityTagType(selectedAnnouncement.priority)">
            {{ selectedAnnouncement.priorityName || "系统公告" }}
          </el-tag>
          <span>{{ formatTime(selectedAnnouncement.publishTime) }}</span>
        </div>
        <p class="announcement-dialog-content">
          {{ selectedAnnouncement.content }}
        </p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/stores/user";
import { useResponsive } from "@/composables/useResponsive";
import { getUserInfo } from "@/api/user";
import { getStatistics } from "@/api/statistics";
import { getLatestAnnouncements } from "@/api/announcement";
import { ElMessage } from "element-plus";
import {
  Bell,
  Wallet,
  Document,
  Money,
  PriceTag,
  ArrowRight,
  DataAnalysis,
  Plus,
  CreditCard,
  Download,
  QuestionFilled,
  User,
} from "@element-plus/icons-vue";

const router = useRouter();
const userStore = useUserStore();
const { isMobile } = useResponsive();

const userInfo = ref(null);
const statistics = ref(null);
const announcements = ref([]);
const selectedAnnouncement = ref(null);
const announcementDialogVisible = ref(false);

const displayAnnouncements = computed(() => announcements.value.slice(0, 3));

const quickActions = computed(() => [
  {
    label: "创建订单",
    icon: Plus,
    tone: "blue",
    action: () => router.push("/courses"),
  },
  {
    label: "账户充值",
    icon: CreditCard,
    tone: "green",
    action: () => router.push("/recharge"),
  },
  {
    label: "价格查询",
    icon: PriceTag,
    tone: "orange",
    action: () => router.push("/price-list"),
  },
  {
    label: "代理管理",
    icon: User,
    tone: "purple",
    action: () => router.push("/users"),
  },
  {
    label: "订单导出",
    icon: Download,
    tone: "blue-soft",
    action: () => router.push("/orders"),
  },
  {
    label: "帮助中心",
    icon: QuestionFilled,
    tone: "teal",
    action: openHelpCenter,
  },
]);

const agentOverviewItems = computed(() => {
  if (!userInfo.value?.agentStats || userStore.isAdmin) {
    return [];
  }

  return [
    {
      label: "代理总数",
      value: formatInteger(userInfo.value.agentStats.totalAgents),
      tone: "tone-default",
    },
    {
      label: "今日注册",
      value: formatInteger(userInfo.value.agentStats.todayRegistered),
      tone: "tone-success",
    },
    {
      label: "今日登录",
      value: formatInteger(userInfo.value.agentStats.todayLogin),
      tone: "tone-primary",
    },
    {
      label: "今日下单",
      value: formatInteger(userInfo.value.agentStats.todayOrders),
      tone: "tone-warning",
    },
  ];
});

const formatInteger = (value) => {
  const count = Number(value || 0);
  return Number.isFinite(count) ? count.toLocaleString("zh-CN") : "0";
};

const formatCurrency = (value) => {
  const amount = Number(value || 0);
  return Number.isFinite(amount)
    ? amount.toLocaleString("zh-CN", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      })
    : "0.00";
};

const formatRate = (value) => {
  const rate = Number(value || 1);
  if (!Number.isFinite(rate)) {
    return "1";
  }

  return Number.isInteger(rate)
    ? String(rate)
    : rate.toFixed(2).replace(/\.?0+$/, "");
};

const getPriorityTagType = (priority) => {
  switch (priority) {
    case 2:
      return "warning";
    case 3:
      return "danger";
    default:
      return "info";
  }
};

const formatTime = (time) => {
  if (!time) return "";

  return new Date(time).toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
};

const formatAnnouncementDate = (time) => {
  if (!time) return "--";

  const date = new Date(time);
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");

  return `${month}-${day}`;
};

const handleAnnouncementClick = (announcement) => {
  selectedAnnouncement.value = announcement;
  announcementDialogVisible.value = true;
};

const handleViewMoreAnnouncements = () => {
  if (userStore.isAdmin) {
    router.push("/admin/announcements");
    return;
  }

  if (displayAnnouncements.value.length > 0) {
    handleAnnouncementClick(displayAnnouncements.value[0]);
    return;
  }

  ElMessage.info("暂无更多公告");
};

const openHelpCenter = () => {
  const trigger = document.querySelector(".service-button, .minimized-chat");
  if (trigger instanceof HTMLElement) {
    trigger.click();
    return;
  }

  router.push("/api-guide");
};

const loadAnnouncements = async () => {
  try {
    const response = await getLatestAnnouncements(5);
    if (response.code === 1) {
      announcements.value = response.data || [];
    }
  } catch (error) {
    console.log("加载公告失败:", error);
  }
};

const loadUserInfo = async () => {
  try {
    const res = await getUserInfo();
    if (res.code === 1) {
      userInfo.value = res.data;
    }
  } catch (error) {
    console.error("获取用户信息失败：", error);
  }
};

const loadStatistics = async () => {
  try {
    const res = await getStatistics();
    if (res.code === 1) {
      statistics.value = res.data;
    }
  } catch (error) {
    console.error("获取统计数据失败：", error);
  }
};

onMounted(() => {
  loadUserInfo();
  loadStatistics();
  loadAnnouncements();
});
</script>

<style scoped>
.dashboard {
  --dashboard-blue: #4e8cff;
  --dashboard-blue-soft: #eef4ff;
  --dashboard-green: #63c56e;
  --dashboard-green-soft: #effaf0;
  --dashboard-orange: #f7a62f;
  --dashboard-orange-soft: #fff6e8;
  --dashboard-purple: var(--brand-violet);
  --dashboard-purple-soft: color-mix(in srgb, var(--brand-violet) 10%, transparent);
  --dashboard-teal: #39c9c1;
  --dashboard-teal-soft: #eafbf8;
  --dashboard-surface: rgba(255, 255, 255, 0.92);
  --dashboard-surface-strong: rgba(255, 255, 255, 0.98);
  --dashboard-border: rgba(110, 151, 255, 0.14);
  --dashboard-shadow: 0 8px 30px rgba(139, 168, 218, 0.16);
  --dashboard-shadow-hover: 0 12px 32px rgba(126, 164, 223, 0.22);

  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1440px;
  margin: 0 auto;
}

.hero-panel,
.content-panel,
.quick-panel {
  position: relative;
  overflow: hidden;
  background: var(--dashboard-surface);
  border: 1px solid var(--dashboard-border);
  border-radius: 8px;
  box-shadow: var(--dashboard-shadow);
}

.hero-panel {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(280px, 0.8fr);
  align-items: center;
  min-height: 208px;
  padding: 32px 40px;
  background:
    radial-gradient(circle at 78% 24%, rgba(114, 159, 255, 0.12), transparent 26%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(234, 242, 255, 0.96));
}

.hero-panel::after {
  content: "";
  position: absolute;
  inset: 0;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.08), transparent 36%),
    radial-gradient(circle at 65% 0%, rgba(160, 193, 255, 0.18), transparent 28%);
  pointer-events: none;
}

.hero-copy {
  position: relative;
  z-index: 1;
}

.hero-copy h1 {
  margin: 0 0 14px;
  font-size: clamp(32px, 4vw, 52px);
  line-height: 1.08;
  color: #1f2937;
  letter-spacing: 0;
}

.hero-copy p {
  margin: 0;
  font-size: 17px;
  line-height: 1.75;
  color: #6b7280;
}

.hero-visual {
  position: relative;
  height: 100%;
  min-height: 180px;
}

.hero-device {
  position: absolute;
  right: 30px;
  bottom: 4px;
  width: 248px;
  height: 154px;
}

.hero-device-screen,
.hero-device-side-card {
  position: absolute;
  border: 1px solid rgba(117, 157, 255, 0.25);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 20px 36px rgba(125, 156, 224, 0.2);
  backdrop-filter: blur(8px);
}

.hero-device-screen {
  right: 0;
  bottom: 0;
  width: 170px;
  height: 134px;
  padding: 20px 18px;
}

.hero-device-side-card {
  top: 18px;
  left: 0;
  width: 104px;
  height: 96px;
  transform: rotate(-8deg);
}

.hero-device-header {
  width: 72px;
  height: 10px;
  border-radius: 999px;
  background: rgba(92, 139, 255, 0.18);
}

.hero-bars {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  height: 78px;
  margin-top: 18px;
}

.hero-bar {
  width: 26px;
  border-radius: 8px 8px 0 0;
  background: linear-gradient(180deg, rgba(95, 145, 255, 0.92), rgba(95, 145, 255, 0.38));
}

.hero-bar--tall {
  height: 68px;
}

.hero-bar--medium {
  height: 54px;
}

.hero-bar--short {
  height: 40px;
}

.hero-orb {
  position: absolute;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.88);
  color: var(--dashboard-blue);
  box-shadow: 0 14px 30px rgba(126, 164, 223, 0.18);
}

.hero-orb .el-icon {
  font-size: 22px;
}

.hero-orb--left {
  left: 58px;
  bottom: 44px;
  width: 44px;
  height: 44px;
}

.hero-orb--top {
  right: 116px;
  top: 0;
  width: 48px;
  height: 48px;
}

.hero-pillar {
  position: absolute;
  bottom: 8px;
  right: 22px;
  width: 22px;
  border-radius: 8px 8px 0 0;
  background: linear-gradient(180deg, rgba(94, 152, 255, 0.92), rgba(94, 152, 255, 0.36));
}

.hero-pillar--a {
  right: 20px;
  height: 56px;
}

.hero-pillar--b {
  right: 52px;
  height: 38px;
}

.hero-pillar--c {
  right: 84px;
  height: 24px;
}

.dashboard-grid {
  display: grid;
  gap: 20px;
}

.dashboard-grid--top {
  grid-template-columns: minmax(320px, 1.45fr) minmax(240px, 1fr) minmax(280px, 1.1fr);
}

.dashboard-grid--stats {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.content-panel {
  min-height: 172px;
  padding: 24px 26px;
  transition:
    transform 0.24s ease,
    box-shadow 0.24s ease,
    border-color 0.24s ease;
}

button.content-panel,
.announcement-row,
.panel-more,
.quick-action {
  border: none;
  cursor: pointer;
  text-align: left;
}

button.content-panel:hover,
.announcement-row:hover,
.panel-more:hover,
.quick-action:hover {
  transform: translateY(-2px);
}

.content-panel:hover,
.quick-panel:hover {
  box-shadow: var(--dashboard-shadow-hover);
}

.panel-header,
.panel-title {
  display: flex;
  align-items: center;
}

.panel-header {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.panel-title {
  gap: 12px;
  min-width: 0;
  font-size: 18px;
  font-weight: 700;
  color: #111827;
}

.panel-icon {
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.panel-icon .el-icon {
  font-size: 20px;
}

.panel-icon--large {
  width: 54px;
  height: 54px;
}

.panel-icon--large .el-icon {
  font-size: 26px;
}

.panel-icon--blue {
  background: var(--dashboard-blue-soft);
  color: var(--dashboard-blue);
}

.panel-icon--green {
  background: var(--dashboard-green-soft);
  color: var(--dashboard-green);
}

.panel-icon--orange {
  background: var(--dashboard-orange-soft);
  color: var(--dashboard-orange);
}

.panel-more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  background: transparent;
  color: #6b7280;
  font-size: 15px;
  font-weight: 600;
}

.announcement-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.announcement-row {
  width: 100%;
  display: grid;
  grid-template-columns: 12px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  background: transparent;
  border-bottom: 1px solid rgba(135, 153, 184, 0.12);
}

.announcement-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.announcement-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--dashboard-blue);
}

.announcement-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 16px;
  line-height: 1.4;
  color: #374151;
}

.announcement-date {
  font-size: 15px;
  color: #9ca3af;
}

.panel-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 102px;
  color: #9ca3af;
  font-size: 15px;
}

.metric-panel,
.stat-panel {
  position: relative;
}

.metric-value {
  position: relative;
  z-index: 1;
  font-size: clamp(26px, 3vw, 38px);
  line-height: 1.08;
  font-weight: 700;
  letter-spacing: 0;
  margin-top: 26px;
}

.metric-value--blue {
  color: var(--dashboard-blue);
}

.metric-value--green {
  color: var(--dashboard-green);
}

.metric-value--orange {
  color: var(--dashboard-orange);
}

.metric-subtext {
  position: relative;
  z-index: 1;
  margin-top: 14px;
  font-size: 14px;
  line-height: 1.6;
  color: #94a3b8;
}

.panel-watermark {
  position: absolute;
  right: 20px;
  bottom: 14px;
  color: rgba(126, 164, 223, 0.12);
  pointer-events: none;
}

.panel-watermark .el-icon,
.watermark-symbol {
  font-size: 102px;
  line-height: 1;
}

.stat-inline {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 28px;
}

.stat-label {
  font-size: 16px;
  color: #6b7280;
}

.action-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(236, 244, 255, 0.92));
}

.action-panel--green {
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(239, 250, 240, 0.92));
}

.action-panel-main {
  display: flex;
  align-items: center;
  gap: 18px;
}

.action-panel-text {
  font-size: clamp(22px, 2vw, 34px);
  line-height: 1.2;
  font-weight: 700;
  color: #111827;
}

.action-arrow {
  flex: 0 0 auto;
  width: 52px;
  height: 52px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(78, 140, 255, 0.1);
  color: var(--dashboard-blue);
}

.action-arrow .el-icon {
  font-size: 22px;
}

.action-arrow--green {
  background: rgba(99, 197, 110, 0.1);
  color: var(--dashboard-green);
}

.quick-panel {
  padding: 24px 28px;
}

.quick-panel-title {
  margin-bottom: 24px;
  font-size: 18px;
  font-weight: 700;
  color: #111827;
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

.quick-action {
  position: relative;
  min-width: 0;
  padding: 6px 16px 2px;
  background: transparent;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

.quick-action:not(:last-child)::after {
  content: "";
  position: absolute;
  top: 18px;
  right: 0;
  width: 1px;
  height: 72px;
  background: rgba(148, 163, 184, 0.18);
}

.quick-action-icon {
  width: 62px;
  height: 62px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: 0 10px 22px rgba(148, 163, 184, 0.2);
}

.quick-action-icon .el-icon {
  font-size: 28px;
}

.quick-action-icon--blue {
  background: linear-gradient(135deg, #4e8cff, #3e73ef);
}

.quick-action-icon--green {
  background: linear-gradient(135deg, #63c56e, #48b858);
}

.quick-action-icon--orange {
  background: linear-gradient(135deg, #f7a62f, #f18a23);
}

.quick-action-icon--purple {
  background: linear-gradient(135deg, #7a72f8, #6559f0);
}

.quick-action-icon--blue-soft {
  background: linear-gradient(135deg, #68a4ff, #4e8cff);
}

.quick-action-icon--teal {
  background: linear-gradient(135deg, #39c9c1, #21b8ae);
}

.quick-action-label {
  font-size: 16px;
  line-height: 1.4;
  color: #374151;
  text-align: center;
}

.agent-overview {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid rgba(135, 153, 184, 0.12);
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.agent-overview-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 6px 8px;
  text-align: center;
}

.agent-overview-label {
  font-size: 13px;
  color: #94a3b8;
}

.agent-overview-value {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
}

.tone-default {
  color: #1f2937;
}

.tone-success {
  color: var(--dashboard-green);
}

.tone-primary {
  color: var(--dashboard-blue);
}

.tone-warning {
  color: var(--dashboard-orange);
}

.announcement-dialog {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.announcement-dialog-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #94a3b8;
  font-size: 14px;
}

.announcement-dialog-content {
  margin: 0;
  font-size: 15px;
  line-height: 1.85;
  color: var(--text-regular);
  white-space: pre-wrap;
}

@media (max-width: 1280px) {
  .dashboard-grid--top {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-grid--top .announcements-panel {
    grid-column: 1 / -1;
  }

  .dashboard-grid--stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .quick-actions {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    row-gap: 24px;
  }

  .quick-action:nth-child(3)::after {
    display: none;
  }

  .quick-action:nth-child(-n + 3) {
    padding-bottom: 18px;
  }
}

@media (max-width: 960px) {
  .hero-panel {
    grid-template-columns: 1fr;
    min-height: auto;
    padding: 28px 24px;
  }

  .hero-copy h1 {
    font-size: 34px;
  }

  .hero-copy p {
    font-size: 15px;
  }

  .dashboard-grid--top,
  .dashboard-grid--stats {
    grid-template-columns: 1fr;
  }

  .content-panel {
    min-height: 0;
  }

  .action-panel-text {
    font-size: 24px;
  }
}

@media (max-width: 767px) {
  .dashboard {
    gap: 16px;
  }

  .hero-panel,
  .content-panel,
  .quick-panel {
    border-radius: 8px;
  }

  .hero-panel {
    padding: 24px 18px;
  }

  .hero-copy h1 {
    margin-bottom: 10px;
    font-size: 28px;
  }

  .hero-copy p {
    line-height: 1.7;
  }

  .content-panel,
  .quick-panel {
    padding: 18px;
  }

  .panel-title {
    font-size: 16px;
  }

  .panel-icon {
    width: 36px;
    height: 36px;
  }

  .panel-icon .el-icon {
    font-size: 18px;
  }

  .metric-value {
    margin-top: 18px;
    font-size: 28px;
  }

  .stat-inline {
    margin-top: 18px;
  }

  .announcement-row {
    grid-template-columns: 12px minmax(0, 1fr);
    gap: 10px;
  }

  .announcement-date {
    grid-column: 2;
    padding-left: 0;
    font-size: 13px;
  }

  .action-panel {
    align-items: flex-start;
  }

  .action-panel-main {
    gap: 14px;
  }

  .action-panel-text {
    font-size: 20px;
  }

  .action-arrow {
    width: 44px;
    height: 44px;
  }

  .quick-panel-title {
    margin-bottom: 18px;
    font-size: 16px;
  }

  .quick-actions {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 20px 0;
  }

  .quick-action {
    gap: 12px;
    padding: 4px 12px 0;
  }

  .quick-action:nth-child(2n)::after {
    display: none;
  }

  .quick-action-icon {
    width: 56px;
    height: 56px;
  }

  .quick-action-icon .el-icon {
    font-size: 24px;
  }

  .quick-action-label {
    font-size: 14px;
  }

  .agent-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .agent-overview-value {
    font-size: 18px;
  }
}

@media (max-width: 480px) {
  .hero-panel {
    padding: 20px 16px;
  }

  .hero-copy h1 {
    font-size: 24px;
  }

  .content-panel,
  .quick-panel {
    padding: 16px;
  }

  .metric-value {
    font-size: 24px;
  }

  .panel-watermark .el-icon,
  .watermark-symbol {
    font-size: 78px;
  }

  .action-panel-text {
    font-size: 18px;
  }
}

:global(html.dark) .dashboard {
  --dashboard-surface: rgba(17, 24, 39, 0.88);
  --dashboard-surface-strong: rgba(15, 23, 42, 0.96);
  --dashboard-border: rgba(96, 165, 250, 0.14);
  --dashboard-shadow: 0 12px 32px rgba(2, 6, 23, 0.42);
  --dashboard-shadow-hover: 0 18px 40px rgba(2, 6, 23, 0.5);
}

:global(html.dark) .hero-panel {
  background:
    radial-gradient(circle at 78% 24%, rgba(59, 130, 246, 0.18), transparent 28%),
    linear-gradient(135deg, rgba(15, 23, 42, 0.98), rgba(17, 24, 39, 0.94));
}

:global(html.dark) .hero-copy h1,
:global(html.dark) .panel-title,
:global(html.dark) .action-panel-text,
:global(html.dark) .quick-panel-title {
  color: #f8fafc;
}

:global(html.dark) .hero-copy p,
:global(html.dark) .panel-more,
:global(html.dark) .announcement-date,
:global(html.dark) .metric-subtext,
:global(html.dark) .agent-overview-label,
:global(html.dark) .announcement-dialog-meta {
  color: #94a3b8;
}

:global(html.dark) .announcement-text,
:global(html.dark) .quick-action-label,
:global(html.dark) .stat-label {
  color: #e2e8f0;
}

:global(html.dark) .announcement-row,
:global(html.dark) .agent-overview {
  border-color: rgba(148, 163, 184, 0.12);
}

:global(html.dark) .hero-device-screen,
:global(html.dark) .hero-device-side-card,
:global(html.dark) .hero-orb {
  background: rgba(30, 41, 59, 0.78);
  border-color: rgba(96, 165, 250, 0.2);
}

:global(html.dark) .hero-device-header {
  background: rgba(96, 165, 250, 0.22);
}

:global(html.dark) .panel-watermark {
  color: rgba(148, 163, 184, 0.08);
}

:global(html.dark) .action-panel {
  background:
    linear-gradient(135deg, rgba(15, 23, 42, 0.98), rgba(20, 32, 58, 0.9));
}

:global(html.dark) .action-panel--green {
  background:
    linear-gradient(135deg, rgba(15, 23, 42, 0.98), rgba(19, 46, 33, 0.88));
}

:global(html.dark) .panel-empty,
:global(html.dark) .announcement-dialog-content {
  color: #cbd5e1;
}
</style>

<style scoped>
/* 首页是品牌空间的主舞台，3D 强度高于其他业务页但不改变信息顺序。 */
.dashboard {
  --dashboard-blue: var(--brand-primary);
  --dashboard-blue-soft: color-mix(in srgb, var(--brand-primary) 11%, transparent);
  --dashboard-green: var(--color-success);
  --dashboard-green-soft: color-mix(in srgb, var(--color-success) 10%, transparent);
  --dashboard-orange: var(--color-warning);
  --dashboard-orange-soft: color-mix(in srgb, var(--color-warning) 11%, transparent);
  --dashboard-purple: var(--brand-violet);
  --dashboard-purple-soft: color-mix(in srgb, var(--brand-violet) 10%, transparent);
  --dashboard-teal: var(--brand-cyan);
  --dashboard-teal-soft: color-mix(in srgb, var(--brand-cyan) 10%, transparent);
  --dashboard-surface: var(--bg-card);
  --dashboard-surface-strong: var(--surface-solid);
  --dashboard-border: var(--border-color-light);
  --dashboard-shadow: var(--shadow-sm);
  --dashboard-shadow-hover: var(--shadow-lg);
  gap: 18px;
  max-width: 1540px;
  transform-style: preserve-3d;
}

.hero-panel,
.content-panel,
.quick-panel {
  border-color: var(--border-color-light);
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow:
    inset 0 1px 0 var(--stroke-highlight),
    var(--shadow-sm);
  backdrop-filter: blur(22px) saturate(1.18);
}

.hero-panel {
  min-height: 268px;
  padding: 38px 44px;
  overflow: visible;
  border-radius: var(--radius-xl);
  background:
    radial-gradient(circle at 82% 24%, color-mix(in srgb, var(--brand-cyan) 18%, transparent), transparent 24%),
    radial-gradient(circle at 62% -8%, color-mix(in srgb, var(--brand-violet) 12%, transparent), transparent 36%),
    linear-gradient(135deg, color-mix(in srgb, var(--surface-solid) 88%, transparent), var(--surface-acrylic));
  box-shadow:
    inset 0 1px 0 var(--stroke-highlight),
    var(--shadow-lg);
  transform: translateZ(22px) rotateX(0.35deg);
}

.hero-panel::before {
  content: "";
  position: absolute;
  z-index: -1;
  right: 7%;
  bottom: -14px;
  left: 7%;
  height: 30px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--brand-primary) 22%, transparent);
  filter: blur(22px);
  transform: translateZ(-28px);
}

.hero-panel::after {
  border-radius: inherit;
  background:
    linear-gradient(112deg, transparent 16%, rgba(255, 255, 255, 0.20) 42%, transparent 58%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.10), transparent 36%);
  background-size: 210% 100%, auto;
  animation: hero-light 9s ease-in-out infinite;
}

@keyframes hero-light {
  0%,
  72%,
  100% {
    background-position: -140% 0, 0 0;
  }
  88% {
    background-position: 160% 0, 0 0;
  }
}

.hero-copy {
  max-width: 650px;
  transform: translateZ(42px);
}

.hero-kicker {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 16px;
  color: var(--brand-primary);
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 0.15em;
}

.hero-kicker-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--brand-cyan);
  box-shadow:
    0 0 0 4px color-mix(in srgb, var(--brand-cyan) 13%, transparent),
    0 0 18px color-mix(in srgb, var(--brand-cyan) 64%, transparent);
}

.hero-copy h1 {
  margin-bottom: 12px;
  color: var(--text-primary);
  font-size: clamp(34px, 4.4vw, 58px);
  font-weight: 720;
  letter-spacing: -0.045em;
}

.hero-copy p {
  max-width: 560px;
  color: var(--text-secondary);
  font-size: 16px;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 26px;
}

.hero-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 42px;
  padding: 0 18px;
  border: 1px solid transparent;
  border-radius: 11px;
  font-weight: 650;
  cursor: pointer;
  transition:
    transform var(--motion-fast) cubic-bezier(0.16, 1, 0.3, 1),
    box-shadow var(--motion-fast) ease,
    background-color var(--motion-fast) ease;
}

.hero-action:hover {
  transform: translate3d(0, -2px, 8px);
}

.hero-action:active {
  transform: translate3d(0, 1px, 0);
}

.hero-action--primary {
  color: var(--text-on-brand);
  background: var(--primary-gradient);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.32),
    0 10px 22px color-mix(in srgb, var(--brand-primary) 28%, transparent);
}

.hero-action--secondary {
  color: var(--text-primary);
  border-color: var(--border-color);
  background: color-mix(in srgb, var(--surface-solid) 62%, transparent);
}

.hero-visual {
  min-height: 218px;
  perspective: 920px;
  transform-style: preserve-3d;
  transform: translateZ(34px) rotateX(2deg) rotateY(-3deg);
}

.hero-device {
  right: 34px;
  bottom: 4px;
  width: 278px;
  height: 176px;
  transform-style: preserve-3d;
  animation: device-float 5.6s ease-in-out infinite;
}

@keyframes device-float {
  0%,
  100% {
    transform: translate3d(0, 0, 22px) rotateY(-8deg) rotateX(2deg);
  }
  50% {
    transform: translate3d(0, -8px, 34px) rotateY(-4deg) rotateX(0deg);
  }
}

.hero-device-screen,
.hero-device-side-card {
  border-color: color-mix(in srgb, var(--brand-primary) 30%, var(--border-color));
  border-radius: 16px;
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.28), transparent),
    color-mix(in srgb, var(--surface-acrylic) 92%, transparent);
  box-shadow:
    inset 0 1px 0 var(--stroke-highlight),
    0 24px 48px rgba(22, 54, 88, 0.22);
  backdrop-filter: blur(18px) saturate(1.28);
}

.hero-device-screen {
  width: 190px;
  height: 150px;
  transform: translateZ(46px);
}

.hero-device-side-card {
  width: 116px;
  height: 108px;
  transform: translateZ(12px) rotateY(13deg) rotateZ(-7deg);
}

.hero-device-header {
  display: flex;
  gap: 5px;
  width: auto;
  background: transparent;
}

.hero-device-header span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--brand-primary) 28%, transparent);
}

.hero-device-header span:first-child {
  background: var(--brand-primary);
}

.hero-bar {
  background: linear-gradient(180deg, var(--brand-cyan), var(--brand-primary));
  box-shadow: 0 7px 16px color-mix(in srgb, var(--brand-primary) 18%, transparent);
}

.hero-orb {
  border: 1px solid var(--border-color-light);
  border-radius: 14px;
  color: var(--brand-primary);
  background: var(--surface-acrylic);
  box-shadow:
    inset 0 1px 0 var(--stroke-highlight),
    var(--shadow-md);
  backdrop-filter: blur(16px);
  animation: spatial-drift 4.8s ease-in-out infinite;
}

.hero-orb--top {
  animation-delay: -2.2s;
}

.hero-pillar {
  border-radius: 8px 8px 3px 3px;
  background: linear-gradient(180deg, var(--brand-cyan), var(--brand-primary));
  box-shadow: 0 10px 22px color-mix(in srgb, var(--brand-primary) 20%, transparent);
}

.content-panel {
  min-height: 168px;
  padding: 22px 24px;
  overflow: hidden;
}

.content-panel::before,
.quick-panel::before {
  content: "";
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: linear-gradient(125deg, rgba(255, 255, 255, 0.08), transparent 42%);
  pointer-events: none;
}

.panel-title,
.action-panel-text,
.quick-panel-title {
  color: var(--text-primary);
}

.panel-more,
.metric-subtext,
.agent-overview-label {
  color: var(--text-secondary);
}

.announcement-text,
.quick-action-label,
.stat-label {
  color: var(--text-regular);
}

.metric-value,
.agent-overview-value {
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.03em;
}

.action-panel {
  background:
    radial-gradient(circle at 100% 0, color-mix(in srgb, var(--brand-primary) 14%, transparent), transparent 52%),
    var(--bg-card);
}

.action-panel--green {
  background:
    radial-gradient(circle at 100% 0, color-mix(in srgb, var(--color-success) 14%, transparent), transparent 52%),
    var(--bg-card);
}

.quick-panel {
  padding: 22px 26px;
}

.quick-action-icon {
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 18px;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.32),
    0 12px 24px rgba(25, 62, 102, 0.16);
  transform: translateZ(14px) rotateX(4deg);
}

.quick-action:hover .quick-action-icon {
  transform: translate3d(0, -3px, 22px) rotateX(0deg);
}

:global(html.dark) .hero-panel {
  background:
    radial-gradient(circle at 82% 24%, rgba(56, 213, 222, 0.14), transparent 24%),
    radial-gradient(circle at 62% -8%, rgba(156, 137, 255, 0.12), transparent 36%),
    linear-gradient(135deg, rgba(18, 37, 61, 0.94), rgba(10, 24, 42, 0.82));
}

:global(html.dark) .hero-device-screen,
:global(html.dark) .hero-device-side-card,
:global(html.dark) .hero-orb {
  background:
    linear-gradient(145deg, rgba(209, 231, 255, 0.10), transparent),
    rgba(18, 38, 62, 0.76);
}

@media (max-width: 960px) {
  .hero-panel {
    min-height: 232px;
    padding: 30px 28px;
    transform: none;
  }

  .hero-copy {
    transform: none;
  }
}

@media (max-width: 767px) {
  .hero-panel {
    padding: 26px 20px;
    border-radius: var(--radius-lg);
  }

  .hero-copy h1 {
    font-size: 32px;
  }

  .hero-actions {
    margin-top: 22px;
  }

  .hero-action {
    flex: 1 1 148px;
  }

  .content-panel,
  .quick-panel {
    border-radius: var(--radius-md);
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero-panel,
  .hero-copy,
  .hero-visual,
  .hero-device,
  .hero-orb,
  .quick-action-icon {
    animation: none;
    transform: none;
  }
}
</style>
