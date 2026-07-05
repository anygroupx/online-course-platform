<template>
  <div class="settings-page">
    <el-card class="settings-card">
      <template #header>
        <div class="card-header">
          <span>系统设置</span>
          <el-button type="primary" @click="handleSave" :loading="saving">
            <el-icon><Select /></el-icon>
            保存设置
          </el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab" class="settings-tabs">
        <!-- 基础设置 -->
        <el-tab-pane label="基础设置" name="basic">
          <el-form :model="configs" label-width="150px" class="config-form">
            <el-form-item label="网站名称">
              <el-input
                v-model="configs.site_name"
                placeholder="请输入网站名称"
              />
            </el-form-item>

            <el-form-item label="SEO关键词">
              <el-input
                v-model="configs.site_keywords"
                placeholder="请输入关键词，用逗号分隔"
              />
            </el-form-item>

            <el-form-item label="SEO描述">
              <el-input
                v-model="configs.site_description"
                type="textarea"
                :rows="3"
                placeholder="请输入网站描述"
              />
            </el-form-item>

            <el-form-item label="系统公告">
              <el-input
                v-model="configs.system_notice"
                type="textarea"
                :rows="5"
                placeholder="请输入系统公告"
              />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 用户设置 -->
        <el-tab-pane label="用户设置" name="user">
          <el-form :model="configs" label-width="180px" class="config-form">
            <el-form-item label="允许用户注册">
              <el-switch
                v-model="configs.user_register_enabled"
                active-text="开启"
                inactive-text="关闭"
                :active-value="'1'"
                :inactive-value="'0'"
              />
            </el-form-item>

            <el-form-item label="用户开户费用">
              <el-input-number
                v-model="configs.user_register_fee"
                :min="0"
                :precision="2"
              />
              <span style="margin-left: 10px; color: var(--text-secondary)"
                >元</span
              >
            </el-form-item>

            <el-form-item label="最低充值金额">
              <el-input-number
                v-model="configs.min_recharge_amount"
                :min="1"
                :precision="2"
              />
              <span style="margin-left: 10px; color: var(--text-secondary)"
                >元</span
              >
            </el-form-item>

            <el-form-item label="API开通免费门槛">
              <el-input-number
                v-model="configs.api_enable_threshold"
                :min="0"
                :precision="2"
              />
              <span style="margin-left: 10px; color: var(--text-secondary)"
                >元（余额大于此值可免费开通）</span
              >
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 高级设置 -->
        <el-tab-pane label="高级设置" name="advanced">
          <el-form :model="configs" label-width="180px" class="config-form">
            <!-- Token安全配置 -->
            <el-divider content-position="left">
              <el-text type="primary">Token安全配置</el-text>
            </el-divider>

            <el-form-item label="Access Token过期时间">
              <el-input-number
                v-model="configs.token_expire_minutes"
                :min="5"
                :max="120"
                :step="5"
              />
              <span style="margin-left: 10px; color: var(--text-secondary)">
                分钟（建议15-30分钟）
              </span>
              <el-tooltip placement="top" effect="dark">
                <template #content>
                  <div style="max-width: 300px">
                    Access Token的有效时间，越短越安全但需要更频繁刷新<br />
                    默认：15分钟
                  </div>
                </template>
                <el-icon style="margin-left: 5px; cursor: help">
                  <InfoFilled />
                </el-icon>
              </el-tooltip>
            </el-form-item>

            <el-form-item label="Refresh Token过期时间">
              <el-input-number
                v-model="configs.refresh_token_expire_days"
                :min="1"
                :max="30"
                :step="1"
              />
              <span style="margin-left: 10px; color: var(--text-secondary)">
                天（建议7-14天）
              </span>
              <el-tooltip placement="top" effect="dark">
                <template #content>
                  <div style="max-width: 300px">
                    Refresh Token的有效时间，用于刷新Access Token<br />
                    超过此时间用户需重新登录<br />
                    默认：7天
                  </div>
                </template>
                <el-icon style="margin-left: 5px; cursor: help">
                  <InfoFilled />
                </el-icon>
              </el-tooltip>
            </el-form-item>

            <el-form-item label="启用自动刷新">
              <el-switch
                v-model="configs.auto_refresh_token_enabled"
                active-text="开启"
                inactive-text="关闭"
                :active-value="'1'"
                :inactive-value="'0'"
              />
              <span style="margin-left: 10px; color: var(--text-secondary)">
                Token过期前自动刷新，提升用户体验
              </span>
            </el-form-item>

            <el-divider content-position="left">
              <el-text type="primary">系统功能</el-text>
            </el-divider>

            <el-form-item label="主题设置">
              <el-button
                type="primary"
                plain
                @click="$router.push('/theme-config')"
              >
                <el-icon><Brush /></el-icon>
                自定义主题颜色
              </el-button>
            </el-form-item>

            <el-form-item label="第三方登录配置">
              <el-alert
                title="第三方登录功能待开发"
                type="info"
                :closable="false"
              />
            </el-form-item>

            <el-form-item label="支付接口配置">
              <el-alert
                title="支付接口功能待开发"
                type="info"
                :closable="false"
              />
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { InfoFilled, Select, Brush } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import axios from "@/utils/request";

const activeTab = ref("basic");
const saving = ref(false);
// Source: AURA-X-KYS 安全加固 - Token配置管理
const configs = ref({
  site_name: "",
  site_keywords: "",
  site_description: "",
  system_notice: "",
  user_register_enabled: "1",
  user_register_fee: 5,
  min_recharge_amount: 10,
  api_enable_threshold: 300,
  // Token安全配置
  token_expire_minutes: 15,
  refresh_token_expire_days: 7,
  auto_refresh_token_enabled: "1",
});

// Source: AURA-X-KYS 安全加固 - 同步Token配置到localStorage
const loadConfigs = async () => {
  try {
    const res = await axios.get("/system/config");
    if (res.code === 1 && res.data) {
      // 将配置数组转换为对象
      res.data.forEach((item) => {
        configs.value[item.configKey] = item.configValue;

        // Token相关配置同步到localStorage，供request.js使用
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
    console.error("加载配置失败：", error);
  }
};

// Source: AURA-X-KYS 安全加固 - 保存时同步Token配置
const handleSave = async () => {
  saving.value = true;
  try {
    const res = await axios.put("/system/config", configs.value);
    if (res.code === 1) {
      // Token配置同步到localStorage
      localStorage.setItem(
        "token_expire_minutes",
        configs.value.token_expire_minutes
      );
      localStorage.setItem(
        "refresh_token_expire_days",
        configs.value.refresh_token_expire_days
      );
      localStorage.setItem(
        "auto_refresh_token_enabled",
        configs.value.auto_refresh_token_enabled
      );

      ElMessage.success("保存成功，Token配置已更新");
    }
  } catch (error) {
    console.error("保存失败：", error);
  } finally {
    saving.value = false;
  }
};

const userRate = ref(1.0);

onMounted(() => {
  loadConfigs();
});
</script>

<style scoped>
.settings-page {
  padding: 20px;
}

.settings-card {
  box-shadow: var(--shadow-sm);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.settings-tabs {
  margin-top: 20px;
}

.config-form {
  max-width: 800px;
  padding: 20px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
}

:deep(.el-input-number) {
  width: 200px;
}

/* Dark Mode Overrides */
html.dark .config-form {
  background-color: transparent;
}
</style>
