<template>
  <el-drawer
    v-model="open"
    :title="subject === 'OWNER' ? '项目 OpenAPI 密钥' : '客户 API 密钥'"
    size="min(600px,100vw)"
    :close-on-click-modal="false"
    :close-on-press-escape="!busy"
    :before-close="close"
    destroy-on-close
    class="project-keys"
  >
    <span class="eyebrow">LOCAL OPENAPI / 独立访问密钥</span>
    <h2>
      {{
        subject === "OWNER"
          ? "为你的业务系统授权。"
          : "为这个客户开放查询与售后。"
      }}
    </h2>
    <p class="muted">
      凭据仅供项目接口使用，不是账号登录密码。不会自动生成或收费；签发、轮换、撤销须验证当前登录密码。新密钥只显示一次。
    </p>
    <el-alert
      v-if="error"
      :title="error"
      type="warning"
      show-icon
      :closable="false"
    />
    <div v-loading="loading">
      <template v-if="settings">
        <div class="key-status">
          <el-tag :type="settings.configured ? 'success' : 'info'">{{
            settings.configured ? "已配置" : "未配置"
          }}</el-tag
          ><span>版本 {{ settings.version }} · 当前{{ ({ READ_ONLY: "只读", MANAGE: "管理", SUPPORT: "本人售后" })[settings.access] || "未授权" }}</span
          ><el-button :disabled="busy" @click="load">检查密钥设置</el-button>
        </div>
        <p v-if="settings.prefix" class="muted">
          展示前缀 {{ settings.prefix }} · 不是可用凭据。有效至
          {{ settings.expiresAt }}
        </p>
        <section v-if="secret" class="issued">
          <h3>请立即安全保存这一次的密钥</h3>
          <el-input
            :model-value="secret"
            type="password"
            readonly
            show-password
            aria-label="本次签发的项目密钥"
            autocomplete="off"
          />
          <p class="muted">
            离开此窗口即清除，查询设置无法恢复。如丢失，核对当前版本后重新轮换。
          </p>
          <el-button @click="secret = ''">我已保存，清除本次显示</el-button>
        </section>
        <el-form
          v-else
          label-position="top"
          :disabled="busy || loading || unknown"
        >
          <el-form-item label="当前登录密码"
            ><el-input
              v-model="password"
              type="password"
              autocomplete="off"
              maxlength="200"
          /></el-form-item>
          <el-form-item label="权限范围"
            ><el-radio-group v-model="access"
              ><el-radio label="READ_ONLY" value="READ_ONLY">只读查询</el-radio
              ><el-radio v-if="subject === 'OWNER'" label="MANAGE" value="MANAGE"
                >客户管理与资金确认</el-radio
              ><el-radio v-if="subject !== 'OWNER'" label="SUPPORT" value="SUPPORT">本人提单与回复（不含资金）</el-radio></el-radio-group
            ></el-form-item
          >
          <el-alert
            v-if="access === 'MANAGE' && subject === 'OWNER'"
            title="管理密钥可为你的客户开户、充值及转回，会影响你的平台钱包。请只交给你控制的业务系统。"
            type="warning"
            :closable="false"
          />
          <el-alert v-if="subject !== 'OWNER'" title="默认只读。选择售后权限后，客户只能提单/回复自己的工单，不能审核、操作资金或查看同项目其他客户。" type="info" :closable="false" />
          <el-form-item label="有效期（天）"
            ><el-input-number v-model="days" :min="1" :max="365" :precision="0"
          /></el-form-item>
          <el-checkbox v-model="consent" class="consent"
            >我确认接收方和权限范围，并知晓轮换后旧密钥立即失效</el-checkbox
          >
          <div class="key-actions">
            <el-button
              type="primary"
              :disabled="!password || !consent"
              @click="issue"
              >{{
                settings.configured
                  ? "轮换密钥（仅显示一次）"
                  : "签发密钥（仅显示一次）"
              }}</el-button
            ><el-button
              v-if="settings.configured"
              type="danger"
              plain
              :disabled="!password || !consent"
              @click="revoke"
              >撤销此密钥</el-button
            >
          </div>
        </el-form>
      </template>
    </div>
    <section class="api-note">
      <h3>仅通过请求头使用</h3>
      <code>X-Project-Key: &lt;完整密钥&gt;</code>
      <p class="muted">
        禁止放进
        URL、浏览器存储或日志。只读凭据不能转移资金，客户凭据不能查看其他客户。
      </p>
      <code>{{
        subject === "OWNER"
          ? "GET /api/external/projects/v1/clients"
          : "GET /api/external/projects/v1/self"
      }}</code>
    </section>
    <template #footer
      ><el-button :disabled="busy" @click="open = false"
        >关闭并清除敏感输入</el-button
      ></template
    >
  </el-drawer>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount } from "vue";
import {
  projectKeySettings,
  issueProjectKey,
  revokeProjectKey,
} from "@/api/projectClients";
const props = defineProps({
    modelValue: Boolean,
    subject: { type: String, default: "OWNER" },
  }),
  emit = defineEmits(["update:modelValue"]);
const open = computed({
  get: () => props.modelValue,
  set: (v) => {
    if (!busy.value) {
      if (!v) clear();
      emit("update:modelValue", v);
    }
  },
});
const settings = ref(null),
  password = ref(""),
  secret = ref(""),
  consent = ref(false),
  access = ref("READ_ONLY"),
  days = ref(30),
  busy = ref(false),
  loading = ref(false),
  error = ref(""),
  unknown = ref(false);
let generation = 0,
  alive = true;
const current = (v) => alive && v === generation && props.modelValue;
function clear() {
  password.value = "";
  secret.value = "";
  consent.value = false;
}
function close(done) {
  if (!busy.value) {
    clear();
    done();
  }
}
async function load() {
  if (loading.value) return;
  const v = generation;
  loading.value = true;
  try {
    const data = await projectKeySettings(props.subject);
    if (current(v)) {
      settings.value = data;
      unknown.value = false;
      error.value = "";
    }
  } catch {
    if (current(v))
      error.value =
        "设置读取失败，不代表当前没有密钥。请重试查询，不自动签发。";
  } finally {
    if (current(v)) loading.value = false;
  }
}
async function issue() {
  if (
    busy.value ||
    loading.value ||
    unknown.value ||
    !password.value ||
    !consent.value
  )
    return;
  const v = generation,
    subject = props.subject,
    payload = {
      version: settings.value.version,
      password: password.value,
      access: access.value,
      days: days.value,
      consent: true,
    };
  busy.value = true;
  clear();
  unknown.value = true;
  try {
    const data = await issueProjectKey(subject, payload);
    if (current(v)) {
      settings.value = data.settings;
      secret.value = data.secret;
      unknown.value = false;
      error.value = "";
    }
  } catch {
    if (current(v))
      error.value =
        "签发结果尚未确认；请检查当前密钥版本，不自动轮换或重试。密钥明文无法从查询中恢复。";
  } finally {
    payload.password = "";
    if (current(v)) busy.value = false;
  }
}
async function revoke() {
  if (
    busy.value ||
    loading.value ||
    unknown.value ||
    !password.value ||
    !consent.value
  )
    return;
  const v = generation,
    subject = props.subject,
    payload = {
      version: settings.value.version,
      password: password.value,
      consent: true,
    };
  busy.value = true;
  clear();
  unknown.value = true;
  try {
    const data = await revokeProjectKey(subject, payload);
    if (current(v)) {
      settings.value = data;
      unknown.value = false;
      error.value = "";
    }
  } catch {
    if (current(v))
      error.value = "撤销结果尚未确认，请检查原密钥设置，不自动重新提交。";
  } finally {
    payload.password = "";
    if (current(v)) busy.value = false;
  }
}
watch(
  () => [props.modelValue, props.subject],
  () => {
    generation++;
    clear();
    loading.value = false;
    busy.value = false;
    settings.value = null;
    error.value = "";
    unknown.value = false;
    access.value = "READ_ONLY";
    if (props.modelValue) load();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  alive = false;
  generation++;
  clear();
});
</script>
<style scoped>
.eyebrow {
  font-size: 11px;
  color: var(--el-color-primary);
  letter-spacing: 1.3px;
}
.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.9;
}
h2 {
  font-size: 22px;
  line-height: 1.5;
}
h3 {
  font-size: 15px;
}
.key-status,
.key-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin: 20px 0;
}
.key-status {
  font-size: 12px;
}
.consent {
  height: auto;
  min-height: 44px;
}
.consent :deep(.el-checkbox__label) {
  white-space: normal;
  line-height: 1.8;
}
.issued {
  border: 1px solid var(--el-color-success-light-7);
  background: var(--el-color-success-light-9);
  border-radius: 12px;
  padding: 18px;
  margin: 20px 0;
}
.api-note {
  border-top: 1px solid var(--el-border-color-light);
  margin-top: 28px;
  padding-top: 20px;
}
code {
  display: block;
  font-size: 12px;
  overflow-wrap: anywhere;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}
:deep(.el-button) {
  min-height: 44px;
}
:global(.project-keys .el-drawer__footer .el-button) {
  min-height: 44px;
}
</style>
