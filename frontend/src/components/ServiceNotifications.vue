<template>
  <el-drawer
    v-model="open"
    title="实习订单 · 微信通知"
    size="min(570px,100vw)"
    class="service-notifications"
    :close-on-click-modal="false"
    :close-on-press-escape="!busy"
    :before-close="beforeClose"
    destroy-on-close
  >
    <div v-loading="loading" class="notification-panel">
      <span class="eyebrow">NOTIFICATIONS / 本人通知接收</span>
      <h2>状态有变化，再通知你。</h2>
      <p class="intro">
        仅发送已同步的订单状态。不是校友帮微信授权或账号绑定，也不会收取“绑定费”。
      </p>
      <el-alert
        v-if="error"
        :title="error"
        type="warning"
        :closable="false"
        show-icon
      />
      <template v-if="settings">
        <el-alert
          v-if="!settings.deliveryAvailable"
          title="通知发送尚未启用；仍可查看或停止已有设置。"
          type="info"
          :closable="false"
        />
        <div class="notification-status">
          <el-tag :type="settings.enabled ? 'success' : 'info'">{{
            settings.enabled
              ? "已验证并启用"
              : settings.configured
                ? "已保存 · 尚未启用"
                : "尚未配置"
          }}</el-tag
          ><el-button text :loading="loading" :disabled="busy" @click="load"
            >检查当前设置</el-button
          >
        </div>
        <ol class="privacy-rules">
          <li>
            只包含订单编号、已同步状态与计划服务日，不包含账号密码、地点或报告内容。
          </li>
          <li>
            保存密钥不发送。你主动发送验证通知并填写接收到的验证码后，才会启用自动状态通知。
          </li>
          <li>
            ShowDoc
            受理不等于已阅读；发送结果未知不自动重发，通知不会再次执行任务或改变余额。
          </li>
        </ol>
        <el-collapse v-model="editing"
          ><el-collapse-item
            :title="
              settings.configured
                ? '更换接收密钥（需重新验证）'
                : '1 · 保存本人的 ShowDoc 推送密钥'
            "
            name="key"
          >
            <p class="help">
              先在
              <a
                href="https://push.showdoc.com.cn/"
                target="_blank"
                rel="noopener noreferrer"
                >ShowDoc 官方服务</a
              >
              完成本人微信接收设置。只粘贴推送地址最后一段密钥，不要粘贴整个网址。
            </p>
            <el-form
              label-position="top"
              :disabled="busy || !settings.deliveryAvailable || saveUnknown"
              ><el-form-item label="ShowDoc 推送密钥"
                ><el-input
                  v-model="token"
                  type="password"
                  autocomplete="off"
                  maxlength="128"
                  placeholder="密钥只用于固定的 ShowDoc 官方通道" /></el-form-item
              ><el-checkbox v-model="consent" class="wrapped-check"
                >此接收方式由我控制，同意向 ShowDoc
                发送上述有限订单信息</el-checkbox
              ><el-button
                type="primary"
                :loading="busy"
                :disabled="!consent || !notificationTokenValid(token)"
                @click="save"
                >保存密钥（不发送）</el-button
              ></el-form
            >
          </el-collapse-item></el-collapse
        >
        <section
          v-if="settings.configured && !settings.verified"
          class="verification-step"
        >
          <h3>2 · 验证你能收到通知</h3>
          <p class="help">
            点击后会向当前接收方式发送一次六位验证码。通知回执丢失时先检查原记录，不要重复发送。
          </p>
          <el-button
            v-if="notificationCanChallenge(settings) && !challengeAttempted"
            type="primary"
            :loading="busy"
            @click="challenge"
            >发送一次验证通知</el-button
          ><el-button v-else :loading="busy" @click="checkDelivery"
            >检查原发送结果</el-button
          ><el-alert
            v-if="challengeAttempted && !settings.challengeDeliveryId"
            title="请求结果尚未确认，请查询当前设置，不要重复发送。"
            type="warning"
            :closable="false"
          />
          <div v-if="testDelivery" class="test-result">
            <strong>{{ notificationState(testDelivery.state) }}</strong>
            <p>{{ testDelivery.notice }}</p>
          </div>
          <el-form
            v-if="settings.canVerify"
            label-position="top"
            :disabled="busy || !settings.deliveryAvailable"
            ><el-form-item label="收到的六位验证码"
              ><el-input
                v-model="code"
                inputmode="numeric"
                maxlength="6"
                autocomplete="off"
                placeholder="从本人接收的验证通知中获取"
            /></el-form-item>
            <p class="help">
              有效至
              {{
                settings.challengeExpiresAt?.replace("T", " ")
              }}；最多五次尝试。
            </p>
            <el-button
              type="primary"
              :disabled="!/^\d{6}$/.test(code)"
              :loading="busy"
              @click="verify"
              >验证并启用订单通知</el-button
            ></el-form
          >
          <p v-else-if="settings.challengeDeliveryId" class="help">
            如验证码已过期或次数用尽，请重新保存密钥并明确发送新的验证通知。
          </p>
        </section>
        <section v-if="settings.enabled" class="enabled-card">
          <h3>本人接收方式已验证</h3>
          <p>
            后续订单状态变化会进入发送队列。不会额外刷新订单；离线期间的多次变化可能合并为最近状态。
          </p>
          <p>服务周期结束不代表考勤已成功。请返回订单查看执行记录。</p>
        </section>
        <section class="delivery-history">
          <div class="history-heading">
            <h3>发送记录</h3>
            <el-button
              text
              :loading="loading"
              :disabled="busy"
              @click="loadHistory"
              >刷新记录</el-button
            >
          </div>
          <p class="help">仅查询已有记录，不会重发任何通知。</p>
          <article
            v-for="record in records"
            :key="record.id"
            class="delivery-row"
          >
            <div>
              <strong>{{ notificationKind(record.kind) }}</strong
              ><el-tag
                :type="
                  record.state === 'ACCEPTED'
                    ? 'success'
                    : record.state === 'UNKNOWN'
                      ? 'warning'
                      : 'info'
                "
                >{{ notificationState(record.state) }}</el-tag
              >
            </div>
            <p>{{ record.notice }}</p>
            <time>{{ record.createdAt?.replace("T", " ") }}</time>
          </article>
          <el-empty
            v-if="!records.length && !historyError"
            description="还没有发送记录"
            :image-size="64"
          /><el-alert
            v-if="historyError"
            title="发送记录读取失败，请刷新重试，不代表没有记录。"
            type="warning"
            :closable="false"
          /><el-pagination
            v-if="total > 10"
            v-model:current-page="page"
            :total="total"
            :page-size="10"
            layout="prev,pager,next"
            @current-change="loadHistory"
          />
        </section>
      </template>
    </div>
    <template #footer
      ><el-button :disabled="busy" @click="open = false">关闭</el-button
      ><el-button
        v-if="settings?.configured"
        type="danger"
        plain
        :disabled="busy || stopAttempted"
        @click="stop"
        >停止通知并清除密钥</el-button
      ></template
    >
  </el-drawer>
</template>
<script setup>
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { ElMessageBox } from "element-plus";
import {
  notificationSettings,
  configureNotifications,
  challengeNotifications,
  verifyNotifications,
  disconnectNotifications,
  notificationDeliveries,
  notificationDelivery,
} from "@/api/serviceNotifications";
import {
  notificationState,
  notificationKind,
  notificationTokenValid,
  notificationCanChallenge,
} from "@/utils/serviceNotifications";
const props = defineProps({ modelValue: Boolean, order: Object });
const emit = defineEmits(["update:modelValue"]);
const open = computed({
  get: () => props.modelValue,
  set: (v) => {
    if (!busy.value) {
      if (!v) clearSecrets();
      emit("update:modelValue", v);
    }
  },
});
const settings = ref(null),
  records = ref([]),
  total = ref(0),
  page = ref(1),
  loading = ref(false),
  busy = ref(false),
  error = ref(""),
  historyError = ref(false),
  token = ref(""),
  consent = ref(false),
  code = ref(""),
  editing = ref(["key"]),
  testDelivery = ref(null),
  challengeAttempted = ref(false),
  saveUnknown = ref(false),
  stopAttempted = ref(false);
let generation = 0,
  alive = true,
  historySequence = 0;
function current(v) {
  return alive && v === generation && props.modelValue;
}
function clearSecrets() {
  token.value = "";
  code.value = "";
  consent.value = false;
}
function beforeClose(done) {
  if (!busy.value) {
    clearSecrets();
    done();
  }
}
async function load() {
  if (!props.order?.id || loading.value) return;
  const v = generation;
  loading.value = true;
  error.value = "";
  try {
    const data = await notificationSettings(props.order.id);
    if (!current(v)) return;
    settings.value = data;
    saveUnknown.value = false;
    stopAttempted.value = false;
    if (!data.configured) editing.value = ["key"];
    await loadHistory();
    if (data.challengeDeliveryId) {
      const delivery = await notificationDelivery(data.challengeDeliveryId);
      if (current(v)) testDelivery.value = delivery;
    }
  } catch {
    if (current(v))
      error.value = "通知设置读取失败，请检查功能开关、迁移与权限。";
  } finally {
    if (current(v)) loading.value = false;
  }
}
async function loadHistory() {
  if (!props.order?.id) return;
  const v = generation,
    sequence = ++historySequence;
  historyError.value = false;
  try {
    const result = await notificationDeliveries(props.order.id, {
      page: page.value,
      pageSize: 10,
    });
    if (current(v) && sequence === historySequence) {
      records.value = result.records;
      total.value = result.total;
    }
  } catch {
    if (current(v) && sequence === historySequence) historyError.value = true;
  }
}
async function save() {
  if (
    busy.value ||
    loading.value ||
    !settings.value ||
    !consent.value ||
    !notificationTokenValid(token.value) ||
    saveUnknown.value
  )
    return;
  const v = generation,
    payload = {
      token: token.value,
      version: settings.value.version,
      consent: true,
    };
  busy.value = true;
  clearSecrets();
  saveUnknown.value = true;
  try {
    const data = await configureNotifications(props.order.id, payload);
    if (current(v)) {
      settings.value = data;
      editing.value = [];
      challengeAttempted.value = false;
      testDelivery.value = null;
      saveUnknown.value = false;
      await loadHistory();
    }
  } catch {
    if (current(v))
      error.value =
        "保存结果尚未确认；请检查当前设置，不自动重试。尚未发送验证通知。";
  } finally {
    payload.token = "";
    if (current(v)) busy.value = false;
  }
}
async function challenge() {
  if (
    busy.value ||
    loading.value ||
    challengeAttempted.value ||
    !notificationCanChallenge(settings.value)
  )
    return;
  const v = generation;
  busy.value = true;
  challengeAttempted.value = true;
  try {
    const data = await challengeNotifications(props.order.id, {
      version: settings.value.version,
      consent: true,
    });
    if (current(v)) {
      settings.value = data;
      if (data.challengeDeliveryId) {
        const result = await notificationDelivery(data.challengeDeliveryId);
        if (current(v)) testDelivery.value = result;
      }
      await loadHistory();
    }
  } catch {
    if (current(v))
      error.value =
        "验证通知的请求结果尚未确认，请检查当前设置和原发送记录，不要重发。";
  } finally {
    if (current(v)) busy.value = false;
  }
}
async function checkDelivery() {
  if (busy.value) return;
  await load();
}
async function verify() {
  if (
    busy.value ||
    loading.value ||
    !/^\d{6}$/.test(code.value) ||
    !settings.value?.canVerify
  )
    return;
  const v = generation,
    payload = {
      code: code.value,
      version: settings.value.version,
      consent: true,
    };
  busy.value = true;
  code.value = "";
  try {
    const data = await verifyNotifications(props.order.id, payload);
    if (current(v)) {
      settings.value = data;
      error.value = "";
    }
  } catch {
    if (current(v)) await load();
  } finally {
    payload.code = "";
    if (current(v)) busy.value = false;
  }
}
async function stop() {
  if (
    busy.value ||
    loading.value ||
    !settings.value?.configured ||
    stopAttempted.value
  )
    return;
  // Consent belongs to the order/version shown when confirmation opened, not a later selection.
  const v = generation,
    orderId = props.order.id,
    version = settings.value.version;
  busy.value = true;
  try {
    await ElMessageBox.confirm(
      "停止后取消尚未派发的消息并清除密钥；已派发的通知无法撤回。",
      "停止本订单通知",
      {
        confirmButtonText: "确认停止",
        cancelButtonText: "暂不停止",
        type: "warning",
      },
    );
  } catch {
    if (current(v)) busy.value = false;
    return;
  }
  if (!current(v)) return;
  stopAttempted.value = true;
  clearSecrets();
  try {
    const data = await disconnectNotifications(orderId, {
      version,
      consent: true,
    });
    if (current(v)) {
      settings.value = data;
      testDelivery.value = null;
      challengeAttempted.value = false;
      editing.value = ["key"];
      stopAttempted.value = false;
      await loadHistory();
    }
  } catch {
    if (current(v))
      error.value = "停用请求结果尚未确认，请查询当前设置，不要重复提交。";
  } finally {
    if (current(v)) busy.value = false;
  }
}
watch(
  () => [props.modelValue, props.order?.id],
  () => {
    generation++;
    historySequence++;
    loading.value = false;
    historyError.value = false;
    clearSecrets();
    busy.value = false;
    settings.value = null;
    records.value = [];
    testDelivery.value = null;
    error.value = "";
    page.value = 1;
    challengeAttempted.value = false;
    saveUnknown.value = false;
    stopAttempted.value = false;
    if (props.modelValue) load();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  alive = false;
  generation++;
  clearSecrets();
});
</script>
<style scoped>
:global(.service-notifications .el-drawer__footer .el-button) {
  min-height: 44px;
  margin-left: 0;
}
:global(.service-notifications .el-drawer__footer) {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}
.notification-panel {
  min-width: 0;
}
.eyebrow {
  font-size: 11px;
  letter-spacing: 1.4px;
  font-weight: 650;
  color: var(--el-color-primary);
}
h2 {
  font-size: 23px;
  margin: 10px 0;
}
h3 {
  font-size: 16px;
  margin: 12px 0;
}
.intro,
.help,
.privacy-rules,
.enabled-card p {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.9;
}
.notification-status,
.history-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin: 18px 0;
}
.privacy-rules {
  padding-left: 19px;
  margin: 18px 0 22px;
}
.privacy-rules li + li {
  margin-top: 9px;
}
.help a {
  color: var(--el-color-primary);
}
.verification-step,
.enabled-card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  padding: 18px;
  margin: 22px 0;
}
.enabled-card {
  background: var(--el-color-success-light-9);
  border-color: var(--el-color-success-light-7);
}
.wrapped-check {
  height: auto;
  min-height: 44px;
  align-items: flex-start;
  margin: 12px 0;
  width: 100%;
}
.wrapped-check :deep(.el-checkbox__label) {
  white-space: normal;
  line-height: 1.8;
}
.wrapped-check :deep(.el-checkbox__input) {
  margin-top: 5px;
}
.test-result {
  margin: 14px 0;
  padding: 13px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  font-size: 13px;
}
.test-result p {
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}
.delivery-history {
  margin-top: 28px;
}
.delivery-row {
  border-top: 1px solid var(--el-border-color-lighter);
  padding: 15px 0;
}
.delivery-row > div {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  font-size: 13px;
}
.delivery-row p,
.delivery-row time {
  font-size: 11px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}
.notification-panel :deep(.el-button) {
  min-height: 44px;
}
.notification-panel :deep(.el-pagination) {
  margin-top: 15px;
  justify-content: center;
}
.notification-panel :deep(.el-collapse-item__header) {
  min-height: 48px;
  height: auto;
  line-height: 1.7;
}
.notification-panel :deep(.el-alert) {
  margin: 12px 0;
}
.notification-panel :deep(.el-alert__title) {
  overflow-wrap: anywhere;
}
@media (max-width: 600px) {
  h2 {
    font-size: 21px;
  }
  .verification-step,
  .enabled-card {
    padding: 14px;
  }
  .notification-status {
    align-items: flex-start;
    flex-wrap: wrap;
  }
}
</style>
