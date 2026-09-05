<template>
  <div class="profile-page">
    <el-row :gutter="20">
      <!-- 左侧：用户基本信息 -->
      <el-col :xs="24" :sm="24" :md="12" :lg="8">
        <el-card class="user-card">
          <template #header>
            <div class="card-header">
              <span>👤 基本信息</span>
            </div>
          </template>

          <div class="user-info">
            <div class="avatar-section">
              <el-avatar :size="80" class="avatar">
                <el-icon><UserFilled /></el-icon>
              </el-avatar>
              <h2>{{ userInfo.nickname || "用户" }}</h2>
              <el-tag
                :type="userInfo.isAdmin ? 'danger' : 'primary'"
                size="large"
              >
                {{ userInfo.isAdmin ? "管理员" : "普通用户" }}
              </el-tag>
            </div>

            <el-descriptions :column="1" border class="info-descriptions">
              <el-descriptions-item label="用户名">
                {{ userInfo.username }}
              </el-descriptions-item>
              <el-descriptions-item label="账户余额">
                <span class="balance">¥{{ userInfo.balance || 0 }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="费率">
                <el-tag type="success"
                  >{{ (userInfo.rate * 100).toFixed(0) }}%</el-tag
                >
              </el-descriptions-item>
              <el-descriptions-item label="注册时间" v-if="userInfo.createTime">
                {{ formatDate(userInfo.createTime) }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
          <el-card class="api-key-card" style="margin-top: 20px">
            <template #header>
              <div class="card-header">
                <span>🔑 API密钥</span>
              </div>
            </template>

            <div class="api-key-section">
              <template v-if="userInfo.apiEnabled">
                <el-alert
                  title="API 密钥已开通；出于安全原因，密钥明文仅在签发时显示一次"
                  type="success"
                  :closable="false"
                  show-icon
                  style="margin-bottom: 15px"
                />

                <div class="api-key-display">
                  <div class="key-item">
                    <label>用户ID (UID):</label>
                    <div class="key-value">
                      <code>{{ userInfo.uid }}</code>
                      <el-button size="small" text aria-label="复制完整UID" @click="copyToClipboard(userInfo.uid, 'UID')">
                        <el-icon><DocumentCopy /></el-icon>
                      </el-button>
                    </div>
                  </div>
                  <div class="key-item">
                    <label>密钥前缀（不是完整 APIKey）:</label>
                    <div class="key-value">
                      <code>{{ userInfo.apiKeyPrefix || '已配置' }}••••</code>
                    </div>
                  </div>
                  <div class="key-item" v-if="userInfo.apiKeyExpiresAt">
                    <label>到期时间:</label>
                    <div class="key-value">{{ formatDate(userInfo.apiKeyExpiresAt) }}</div>
                  </div>
                </div>

                <p class="key-help">此处仅展示用于识别的前缀，不能用于 API 认证。完整密钥只在签发时显示一次；如果未保存或已过期，可验证登录密码后免费轮换；旧密钥将立即失效。</p>
                <div class="api-docs-link">
                  <el-button type="primary" @click="goToApiDocs" plain>
                    <el-icon><Document /></el-icon>
                    查看API文档
                  </el-button>
                  <el-button type="warning" plain :loading="enablingApiKey" @click="handleRotateApiKey">轮换 APIKey</el-button>
                </div>
              </template>

              <template v-else>
                <el-alert
                  title="API密钥未开通"
                  type="warning"
                  :closable="false"
                  show-icon
                  style="margin-bottom: 15px"
                >
                  <template #default>
                    <div style="margin-top: 10px">
                      <p style="margin: 5px 0">💡 开通后可调用系统API接口</p>
                      <p style="margin: 5px 0">💰 开通费用：</p>
                      <ul style="margin: 5px 0; padding-left: 20px">
                        <li>余额 ≥ ¥300：免费开通</li>
                        <li>余额 < ¥300：收费 ¥10</li>
                      </ul>
                    </div>
                  </template>
                </el-alert>

                <el-button
                  type="primary"
                  @click="handleEnableApiKey"
                  :loading="enablingApiKey"
                  style="width: 100%"
                  size="large"
                >
                  <el-icon><Key /></el-icon>
                  开通API密钥
                </el-button>
              </template>
            </div>
          </el-card>
        </el-card>

        <!-- API密钥卡片 -->
      </el-col>

      <!-- 右侧：统计数据 -->
      <el-col :xs="24" :sm="24" :md="12" :lg="16">
        <!-- 统计卡片 -->
        <el-row :gutter="20">
          <el-col :xs="12" :sm="12" :md="12">
            <el-card class="stat-card">
              <el-statistic title="总订单数" :value="stats.totalOrders">
                <template #prefix>
                  <el-icon color="var(--brand-primary)"><Document /></el-icon>
                </template>
              </el-statistic>
            </el-card>
          </el-col>
          <el-col :xs="12" :sm="12" :md="12">
            <el-card class="stat-card">
              <el-statistic
                title="总充值金额"
                :value="stats.totalRecharge"
                :precision="2"
                prefix="¥"
              >
                <template #prefix>
                  <el-icon color="var(--color-success)"><Money /></el-icon>
                </template>
              </el-statistic>
            </el-card>
          </el-col>
        </el-row>

        <!-- 代理统计 -->
        <el-card
          class="stats-card"
          style="margin-top: 20px"
          v-if="stats.agentStats"
        >
          <template #header>
            <div class="card-header">
              <span>📊 代理统计</span>
            </div>
          </template>

          <el-row :gutter="20">
            <el-col :xs="12" :sm="6" :md="6">
              <div class="stat-item">
                <div class="stat-value">{{ stats.agentStats.totalAgents }}</div>
                <div class="stat-label">总代理数</div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6" :md="6">
              <div class="stat-item">
                <div class="stat-value">
                  {{ stats.agentStats.todayRegistered }}
                </div>
                <div class="stat-label">今日注册</div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6" :md="6">
              <div class="stat-item">
                <div class="stat-value">{{ stats.agentStats.todayLogin }}</div>
                <div class="stat-label">今日登录</div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6" :md="6">
              <div class="stat-item">
                <div class="stat-value">{{ stats.agentStats.todayOrders }}</div>
                <div class="stat-label">今日订单</div>
              </div>
            </el-col>
          </el-row>
        </el-card>

        <!-- 邀请码信息 -->
        <el-card class="invite-card" style="margin-top: 20px">
          <template #header>
            <div class="card-header">
              <span>📋 邀请码</span>
            </div>
          </template>

          <div class="invite-section">
            <template v-if="userInfo.inviteCode && userInfo.inviteCode !== '0'">
              <div class="invite-info">
                <div class="invite-item">
                  <label>我的邀请码:</label>
                  <div class="invite-value">
                    <el-tag size="large" type="success">{{
                      userInfo.inviteCode
                    }}</el-tag>
                    <el-button
                      size="small"
                      text
                      @click="copyToClipboard(userInfo.inviteCode, '邀请码')"
                    >
                      <el-icon><DocumentCopy /></el-icon>
                      复制
                    </el-button>
                  </div>
                </div>

                <div class="invite-item" v-if="userInfo.inviteRate">
                  <label>下级费率:</label>
                  <div class="invite-value">
                    <el-tag type="info"
                      >{{ (userInfo.inviteRate * 100).toFixed(0) }}%</el-tag
                    >
                  </div>
                </div>
              </div>

              <el-divider />

              <div class="invite-link-section">
                <label>邀请链接:</label>
                <el-input :value="inviteLink" readonly style="margin-top: 10px">
                  <template #append>
                    <el-button @click="copyToClipboard(inviteLink, '邀请链接')">
                      <el-icon><DocumentCopy /></el-icon>
                      复制
                    </el-button>
                  </template>
                </el-input>
              </div>
            </template>

            <template v-else>
              <el-alert
                title="邀请码未设置"
                type="info"
                :closable="false"
                show-icon
                style="margin-bottom: 15px"
              />
              <el-button
                type="primary"
                @click="showSetupInviteDialog"
                plain
                style="width: 100%"
              >
                设置邀请码
              </el-button>
            </template>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 设置邀请码对话框 -->
    <el-dialog
      v-model="inviteDialogVisible"
      title="设置邀请码"
      width="400px"
      :close-on-click-modal="false"
     append-to-body>
      <el-form :model="inviteForm" label-width="120px">
        <el-form-item label="邀请费率(%)">
          <el-input-number
            v-model="inviteForm.inviteRate"
            :min="0"
            :max="100"
            :step="1"
          />
          <div class="form-tip">设置下级用户的费率百分比</div>
        </el-form-item>
        <el-form-item label="自定义邀请码">
          <el-input
            v-model="inviteForm.customInviteCode"
            placeholder="留空则自动生成"
          />
          <div class="form-tip">留空系统将自动生成</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inviteDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="handleSetupInviteCode"
          :loading="settingInvite"
        >
          确定
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="issuedKeyVisible"
      title="APIKey 仅显示一次"
      width="600px"
      append-to-body
      destroy-on-close
      :show-close="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      class="issued-key-dialog"
      @closed="issuedKey = ''"
    >
      <el-alert title="请立即复制并安全保存完整密钥，关闭后无法再次查看。" type="warning" :closable="false" show-icon />
      <p>完整 APIKey（可换行显示，复制时不会包含换行）：</p>
      <pre class="issued-key-value" tabindex="0" aria-label="完整 APIKey">{{ issuedKey }}</pre>
      <template #footer>
        <el-button type="primary" @click="copyToClipboard(issuedKey, '完整 APIKey')">复制完整 APIKey</el-button>
        <el-button @click="issuedKeyVisible = false">我已安全保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, onDeactivated } from "vue";
import { copyText } from "@/utils/clipboard";
import { useRouter } from "vue-router";
import { useUserStore } from "@/stores/user";
import { ElMessage, ElMessageBox } from "element-plus";
import { getUserInfo, enableApiKey, rotateApiKey, setupInviteCode } from "@/api/user";
import {
  UserFilled,
  DocumentCopy,
  Document,
  Key,
  Money,
} from "@element-plus/icons-vue";
import dayjs from "dayjs";

const router = useRouter();
const userStore = useUserStore();

// 用户信息
const userInfo = ref({});
const stats = ref({
  totalOrders: 0,
  totalRecharge: 0,
  agentStats: null,
});

// API密钥相关状态
const enablingApiKey = ref(false);
const issuedKey = ref('');
const issuedKeyVisible = ref(false);
const clearIssuedKey = () => { issuedKeyVisible.value = false; issuedKey.value = ''; };
onBeforeUnmount(clearIssuedKey);
onDeactivated(clearIssuedKey);

// 邀请码相关状态
const inviteDialogVisible = ref(false);
const settingInvite = ref(false);
const inviteForm = ref({
  inviteRate: 60,
  customInviteCode: "",
});

// 邀请链接
const inviteLink = computed(() => {
  const baseUrl = window.location.origin;
  return `${baseUrl}/register?inviteCode=${userInfo.value.inviteCode}`;
});

// 加载用户信息
const loadUserInfo = async () => {
  try {
    const res = await getUserInfo();
    if (res.code === 1) {
      userInfo.value = { ...res.data, isAdmin: userStore.isAdmin };
      if (userStore.userInfo) userStore.userInfo = { ...userStore.userInfo, balance: res.data.balance };
      stats.value.totalOrders = res.data.totalOrders || 0;
      stats.value.totalRecharge = res.data.totalRecharge || 0;
      stats.value.agentStats = res.data.agentStats;
    }
  } catch (error) {
    console.error("加载用户信息失败:", error);
    ElMessage.error("加载用户信息失败");
  }
};

// 格式化日期
const formatDate = (date) => {
  return dayjs(date).format("YYYY-MM-DD HH:mm:ss");
};

// 复制到剪贴板
const copyToClipboard = async (text, label) => {
  try {
    await copyText(text);
    ElMessage.success(`${label}已复制到剪贴板`);
  } catch (error) {
    console.error("复制失败:", error);
    ElMessage.error("复制失败");
  }
};

// 开通API密钥
const handleEnableApiKey = async () => {
  try {
    const balance = userInfo.value.balance || 0;
    const fee = balance >= 300 ? 0 : 10;

    await ElMessageBox.confirm(
      `开通API密钥${fee > 0 ? `需要支付 ¥${fee}` : "免费"}，确定要开通吗？`,
      "开通确认",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "info",
      }
    );

    enablingApiKey.value = true;
    const res = await enableApiKey(1, null); // type=1 表示为自己开通

    // 检查响应码
    if (res.code === 1) {
      issuedKey.value = res.data;
      issuedKeyVisible.value = true;
      await loadUserInfo();
    } else {
      // 后端返回的业务错误
      ElMessage.error(res.message || "开通失败");
    }
  } catch (error) {
    if (error !== "cancel" && error !== "close") {
      console.error("开通API密钥失败:", error);
      // 显示错误信息
      const errorMessage = error.response?.data?.message || error.message || "开通失败";
      ElMessage.error(errorMessage);
    }
  } finally {
    enablingApiKey.value = false;
  }
};

// Never try to retrieve an existing plaintext key from the server.
const handleRotateApiKey = async () => {
  try {
    const { value: password } = await ElMessageBox.prompt(
      '轮换免费，旧密钥将立即失效，请及时更新第三方配置。请输入当前登录密码确认。',
      '确认轮换 APIKey',
      { inputType: 'password', inputPlaceholder: '当前登录密码', inputValidator: (value) => !!value || '请输入当前登录密码', confirmButtonText: '验证并轮换', cancelButtonText: '取消', closeOnClickModal: false }
    );
    enablingApiKey.value = true;
    const res = await rotateApiKey(password);
    issuedKey.value = res.data;
    issuedKeyVisible.value = true;
    await loadUserInfo();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.response?.data?.message || error.message || '轮换失败');
  } finally {
    enablingApiKey.value = false;
  }
};

// 跳转到API文档
const goToApiDocs = () => {
  router.push("/api-guide");
};

// 显示设置邀请码对话框
const showSetupInviteDialog = () => {
  inviteForm.value = {
    inviteRate: 60,
    customInviteCode: "",
  };
  inviteDialogVisible.value = true;
};

// 设置邀请码
const handleSetupInviteCode = async () => {
  try {
    settingInvite.value = true;
    const res = await setupInviteCode({
      inviteRate: inviteForm.value.inviteRate / 100,
      customInviteCode: inviteForm.value.customInviteCode || undefined,
    });
    if (res.code === 1) {
      ElMessage.success("邀请码设置成功");
      inviteDialogVisible.value = false;
      await loadUserInfo();
    }
  } catch (error) {
    console.error("设置邀请码失败:", error);
    ElMessage.error(error.message || "设置失败");
  } finally {
    settingInvite.value = false;
  }
};

onMounted(() => {
  loadUserInfo();
});
</script>

<style scoped>
.profile-page {
  box-sizing: border-box;
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

/* 用户信息卡片 */
.user-card {
  height: 100%;
}

.user-info {
  text-align: center;
}

.avatar-section {
  margin-bottom: 20px;
}

.avatar {
  margin-bottom: 15px;
  background: var(--primary-gradient);
  font-size: 36px;
}

.avatar-section h2 {
  margin: 10px 0;
  font-size: 24px;
  color: var(--text-primary);
}

.info-descriptions {
  margin-top: 20px;
}

.balance {
  color: var(--color-success);
  font-size: 18px;
  font-weight: 600;
}

/* API密钥卡片 */
.api-key-section {
  padding: 10px 0;
}

.api-key-display {
  margin: 15px 0;
}

.key-item {
  margin-bottom: 15px;
}

.key-item label {
  display: block;
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.key-value {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  background: var(--bg-body);
  border-radius: 6px;
}

html.dark .key-value {
  background: rgba(255, 255, 255, 0.05);
}

.key-value code {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
  word-break: break-all;
  font-family: "Courier New", monospace;
  font-size: 14px;
  color: var(--text-regular);
}

.api-docs-link { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 20px; }
.api-docs-link .el-button { margin: 0; }

/* 统计卡片 */
.stat-card {
  margin-bottom: 20px;
}

.stats-card .stat-item {
  text-align: center;
  padding: 20px 0;
}

.stat-item .stat-value {
  font-size: 32px;
  font-weight: 600;
  color: var(--brand-primary);
  margin-bottom: 8px;
}

.stat-item .stat-label {
  font-size: 14px;
  color: var(--text-secondary);
}

/* 邀请码卡片 */
.invite-section {
  padding: 10px 0;
}

.invite-info .invite-item {
  margin-bottom: 15px;
}

.invite-item label {
  display: block;
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.invite-value {
  display: flex;
  align-items: center;
  gap: 10px;
}

.invite-link-section label {
  display: block;
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 5px;
}

.form-tip {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 5px;
}

.key-value { min-width: 0; }
.key-value .el-button { flex-shrink: 0; min-width: 44px; min-height: 44px; }
.key-help { color: var(--text-secondary); font-size: 13px; line-height: 1.7; overflow-wrap: anywhere; }
.issued-key-value { padding: 16px; border-radius: 8px; background: var(--bg-body); font: 16px/1.8 ui-monospace, Consolas, monospace; white-space: pre-wrap; overflow-wrap: anywhere; word-break: break-all; user-select: all; }
.avatar-section h2 { overflow-wrap: anywhere; }
/* 响应式优化 */
@media (max-width: 768px) {
  .profile-page { padding: 0; }
  .user-card { height: auto; margin-bottom: 16px; }
  :deep(.el-card__body), :deep(.el-card__header) { padding: 16px 12px; }
  :deep(.el-descriptions__table) { table-layout: fixed; }
  :deep(.el-descriptions__label) { width: 90px; }
  :deep(.el-descriptions__content) { overflow-wrap: anywhere; }
  :deep(.el-alert__content) { min-width: 0; }
  .stat-item .stat-value { font-size: 26px; }
  .invite-value { flex-wrap: wrap; }

  .avatar {
    width: 60px;
    height: 60px;
  }

  .avatar-section h2 {
    font-size: 20px;
  }
}
</style>
