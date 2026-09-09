<template>
  <div class="flash-account">
    <el-form-item label="账号授权方式">
      <el-radio-group v-model="mode" :disabled="working" @change="reset">
        <el-radio-button value="PASSWORD">账号密码</el-radio-button>
        <el-radio-button v-if="project === 'sdxy'" value="SMS"
          >短信验证码</el-radio-button
        >
      </el-radio-group>
    </el-form-item>
    <div class="account-grid">
      <el-form-item label="服务账号 / 手机号"
        ><el-input
          v-model="account"
          :disabled="working || !!session"
          autocomplete="off"
          maxlength="100"
          @input="invalidate"
      /></el-form-item>
      <el-form-item v-if="project === 'sdxy'" label="学校名称"
        ><el-input
          v-model="schoolName"
          :disabled="working || !!session"
          autocomplete="off"
          maxlength="120"
          placeholder="密码授权必填；短信授权可选"
          @input="invalidate"
      /></el-form-item>
      <el-form-item v-if="mode === 'PASSWORD' && !verified" label="服务密码"
        ><el-input
          v-model="secret"
          :disabled="working"
          type="password"
          show-password
          autocomplete="new-password"
          maxlength="200"
      /></el-form-item>
      <el-form-item
        v-if="mode === 'SMS' && session?.state === 'SMS_SENT'"
        label="收到的短信验证码"
        ><el-input
          v-model="secret"
          :disabled="working"
          autocomplete="one-time-code"
          inputmode="numeric"
          maxlength="8"
          placeholder="只提交本次收到的验证码"
      /></el-form-item>
    </div>
    <p class="auth-note">
      授权仅用于此商品，有效期最多十分钟。短信、验证码校验和规则刷新不自动重试；账号凭据不回传、不存入浏览器持久化存储。
    </p>
    <div class="account-actions">
      <el-button
        v-if="mode === 'SMS' && (!session || session.state === 'CREATED')"
        type="primary"
        plain
        :loading="working"
        :disabled="!authorized || !account"
        @click="send"
        >发送本人验证码</el-button
      >
      <el-button
        v-if="
          !verified &&
          ((mode === 'PASSWORD' && (!session || session.state === 'CREATED')) ||
            session?.state === 'SMS_SENT')
        "
        type="primary"
        :loading="working"
        :disabled="
          !authorized ||
          !account ||
          !secret ||
          (mode === 'PASSWORD' && project === 'sdxy' && !schoolName)
        "
        @click="verify"
        >查询账号与可用计划</el-button
      >
      <el-button
        v-if="verified && session.canRefreshRules"
        :loading="working"
        @click="refresh"
        >刷新可用规则</el-button
      >
      <el-button v-if="session && !verified" :loading="working" @click="check"
        >检查授权状态</el-button
      >
      <el-button v-if="session" :disabled="working" @click="reset"
        >重新授权</el-button
      >
    </div>
    <el-alert
      v-if="session || uncertain"
      :type="verified ? 'success' : 'info'"
      :closable="false"
      :title="statusText"
      show-icon
    />
    <p v-if="verified" class="auth-note">
      授权账号 {{ session.accountLabel }} ·
      {{ session.expiresAt?.replace("T", " ") }}
      到期。刷新规则会使旧报价失效，请重新预览。
    </p>
  </div>
</template>
<script setup>
import { ref, computed, watch, onBeforeUnmount } from "vue";
import { serviceAccountExpired } from "@/utils/serviceCommerce";
import {
  createServiceAccountSession,
  sendServiceAccountCode,
  verifyServiceAccount,
  getServiceAccountSession,
  refreshServiceAccountRules,
  revokeServiceAccountSession,
} from "@/api/serviceCommerce";
const props = defineProps({
  productId: [String, Number],
  project: String,
  authorized: Boolean,
});
const emit = defineEmits(["verified", "working"]);
const mode = ref("PASSWORD"),
  account = ref(""),
  schoolName = ref(""),
  secret = ref(""),
  session = ref(null),
  working = ref(false),
  uncertain = ref(false);
let generation = 0;
const verified = computed(() => session.value?.state === "READY");
const statusText = computed(() => {
  if (uncertain.value)
    return "请求结果尚未确认。请检查原授权状态，不要重复发送验证码或提交凭据。";
  return (
    {
      CREATED: "授权会话已创建，等待下一步。",
      SMS_SENDING: "短信发送结果待确认，请检查状态，不要再次发送。",
      SMS_SENT:
        "验证码已发送，请输入收到的验证码。未收到时至少等待一分钟后重新授权。",
      VERIFYING: "正在核实账号，请检查原会话状态，不要重复提交验证码。",
      READY: "账号已核实，请选择服务计划并预览金额。",
      REFRESHING: "可用规则正在更新，旧计划暂不可用于下单。",
      REAUTHORIZE:
        "规则更新已受理，但未获得可复用的授权信息；请重新验证后读取新规则。",
      UNKNOWN: "处理结果无法确认，本会话不会再次提交。请核对后重新授权。",
      EXPIRED: "授权已过期，请重新授权。",
      USED: "此授权已用于一笔订单。请到我的服务订单查看结果。",
      REVOKED: "授权已撤销。",
    }[session.value?.state] || ""
  );
});
function publish() {
  emit(
    "verified",
    verified.value
      ? { id: session.value.id, lookup: session.value.lookup }
      : null,
  );
}
function invalidate() {
  generation++;
  uncertain.value = false;
  emit("verified", null);
}
async function discard(id) {
  try {
    await revokeServiceAccountSession(id);
  } catch {
    /* Expiry and server ownership checks remain the fallback. */
  }
}
function reset() {
  const old = session.value?.id;
  generation++;
  session.value = null;
  secret.value = "";
  uncertain.value = false;
  publish();
  if (old) void discard(old);
}
async function begin(version) {
  if (session.value) return session.value.id;
  const value = await createServiceAccountSession(props.productId, {
    mode: mode.value,
    account: account.value,
    schoolName: schoolName.value,
    authorizedAccount: props.authorized,
  });
  if (version !== generation) {
    void discard(value.id);
    return null;
  }
  session.value = value;
  return value.id;
}
async function run(action, { create = false, credential = false } = {}) {
  if (working.value || !props.authorized) return;
  const version = generation;
  working.value = true;
  emit("working", true);
  uncertain.value = false;
  try {
    const id = create ? await begin(version) : session.value?.id;
    if (!id || version !== generation) return;
    const value = await action(id);
    if (version === generation) {
      session.value = value;
      publish();
    }
  } catch {
    if (version === generation) {
      uncertain.value = !!session.value;
      emit("verified", null);
    }
  } finally {
    if (credential) secret.value = "";
    working.value = false;
    emit("working", false);
  }
}
const send = () => run(sendServiceAccountCode, { create: true });
const verify = () => {
  const value = secret.value;
  return run((id) => verifyServiceAccount(id, { secret: value }), {
    create: true,
    credential: true,
  });
};
const refresh = () => {
  emit("verified", null);
  return run(refreshServiceAccountRules);
};
const check = () => run(getServiceAccountSession);
watch(
  () => [props.productId, props.project],
  () => {
    reset();
    account.value = "";
    schoolName.value = "";
    mode.value = "PASSWORD";
  },
);
watch(
  () => props.authorized,
  (value) => {
    if (!value) reset();
  },
);
const expiryTimer = setInterval(() => {
  if (
    !session.value ||
    ["EXPIRED", "USED", "REVOKED"].includes(session.value.state)
  )
    return;
  if (serviceAccountExpired(session.value)) {
    generation++;
    session.value = {
      ...session.value,
      state: "EXPIRED",
      lookup: null,
      canRefreshRules: false,
    };
    secret.value = "";
    uncertain.value = false;
    publish();
  }
}, 1000);
onBeforeUnmount(() => {
  clearInterval(expiryTimer);
  const old = session.value?.id;
  generation++;
  secret.value = "";
  if (old) void discard(old);
});
</script>
<style scoped>
.flash-account {
  margin: 20px 0;
}
.account-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}
.account-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 16px 0;
}
.account-actions .el-button {
  margin-left: 0;
  min-height: 44px;
}
.auth-note {
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
  margin: 12px 0;
}
@media (max-width: 580px) {
  .account-grid {
    grid-template-columns: 1fr;
  }
}
</style>
