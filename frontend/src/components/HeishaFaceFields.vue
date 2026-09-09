<template>
  <div class="heisha-face">
    <ol class="face-steps" aria-label="官方人脸授权流程">
      <li :class="{ active: !session?.lookup }"><b>01</b> 账号预检</li>
      <li :class="{ active: session?.lookup && !ready }"><b>02</b> 官方采集</li>
      <li :class="{ active: ready }"><b>03</b> 确认计划</li>
    </ol>
    <div class="face-account-grid">
      <el-form-item label="本人手机号">
        <el-input
          v-model="account"
          :disabled="working || !!session"
          autocomplete="off"
          inputmode="tel"
          maxlength="11"
          placeholder="账号绑定的手机号"
        />
      </el-form-item>
      <el-form-item
        v-if="!session || session.state === 'CREATED'"
        label="服务密码"
      >
        <el-input
          v-model="secret"
          :disabled="working"
          type="password"
          show-password
          autocomplete="new-password"
          maxlength="200"
        />
      </el-form-item>
    </div>
    <div class="face-actions">
      <el-button
        v-if="!session || session.state === 'CREATED'"
        type="primary"
        :loading="working"
        :disabled="
          !authorized || !/^1\d{10}$/.test(account) || !secret || uncertain
        "
        @click="verify"
        >预检本人账号</el-button
      >
      <el-button v-if="session" :loading="working" @click="checkSession"
        >检查授权状态</el-button
      >
      <el-button v-if="session" :disabled="working" @click="reset"
        >重新授权</el-button
      >
    </div>
    <el-alert
      v-if="session || uncertain"
      :type="ready ? 'success' : 'info'"
      :title="statusText"
      :closable="false"
      show-icon
    />
    <section
      v-if="session?.face"
      class="collection-card"
      aria-label="官方人脸采集"
    >
      <div class="collection-heading">
        <h4>照片只交给官方采集端</h4>
        <el-tag :type="ready ? 'success' : 'warning'" size="small">{{
          ready ? "采集已核实" : "待完成采集"
        }}</el-tag>
      </div>
      <p>
        系统不上传、保存或展示人脸照片。采集将在打开的认证页面完成；请核对下方域名，只提交本人信息，不转发采集页面。
      </p>
      <div v-if="session.face.collectionOrigin" class="collection-origin">
        <span>已批准的采集站点</span
        ><strong>{{ session.face.collectionOrigin }}</strong>
      </div>
      <el-checkbox
        v-if="!ready"
        v-model="faceConsent"
        :disabled="working"
        class="face-consent"
        >我同意在官方采集站点提交本人人脸信息；取消勾选会撤销本次授权</el-checkbox
      >
      <div v-if="session.face.status" class="collection-progress">
        <span
          >已接收 <strong>{{ session.face.status.fileCount }}</strong> 张</span
        >
        <span
          >需要 {{ session.face.status.minFileCount }}–{{
            session.face.status.maxFileCount
          }}
          张</span
        >
      </div>
      <div class="face-actions">
        <el-button
          v-if="session.face.canCollect"
          type="primary"
          :loading="working"
          :disabled="!faceConsent || uncertain"
          @click="collect"
          >获取官方采集入口</el-button
        >
        <el-button
          v-if="session.face.canLaunch"
          type="primary"
          :loading="working"
          :disabled="!faceConsent || uncertain"
          @click="launch"
          >打开官方采集页</el-button
        >
        <el-button
          v-if="session.face.canCheck"
          :loading="working"
          :disabled="!faceConsent || uncertain"
          @click="checkFace"
          >检查采集状态</el-button
        >
      </div>
      <p v-if="!ready" class="collection-hint">
        在新页面完成后，返回这里检查状态。打开页面不会扣款或下单。撤销仅停止当前授权，已提交的资料不会自动删除。
      </p>
    </section>
    <p class="face-deadline">
      {{
        session
          ? `当前授权账号 ${session.accountLabel}，最多十分钟有效。`
          : "账号预检、采集与下单绑定到同一用户和商品。"
      }}密码、采集令牌及照片不会存入浏览器持久化存储。
    </p>
  </div>
</template>
<script setup>
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import {
  serviceAccountExpired,
  validFaceLaunchTicket,
} from "@/utils/serviceCommerce";
import {
  createServiceAccountSession,
  verifyServiceAccount,
  getServiceAccountSession,
  revokeServiceAccountSession,
  collectServiceAccountFace,
  checkServiceAccountFace,
  issueServiceFaceLaunch,
} from "@/api/serviceCommerce";
const props = defineProps({ productId: [String, Number], authorized: Boolean });
const emit = defineEmits(["verified", "working"]);
const account = ref(""),
  secret = ref(""),
  session = ref(null),
  faceConsent = ref(false),
  working = ref(false),
  uncertain = ref(false);
let generation = 0;
const ready = computed(
  () => session.value?.state === "READY" && !uncertain.value,
);
const statusText = computed(() =>
  uncertain.value
    ? "请求结果尚未确认，请检查原授权状态，不要重复创建采集链接。"
    : {
        CREATED: "授权会话已创建，等待账号预检。",
        VERIFYING: "账号预检进行中，请检查原授权状态。",
        FACE_REQUIRED:
          "账号预检通过。请阅读采集说明并明确授权，再获取官方入口。",
        COLLECTING: "正在获取官方采集入口，请检查状态，不要重复创建。",
        FACE_PENDING: "采集尚未核实。完成官方采集后，请返回检查状态。",
        FACE_CHECKING: "正在检查官方采集状态；此步骤不会下单。",
        FACE_RETRY:
          "本次采集状态检查未能确认，可手动再次检查；不会重新创建采集或下单。",
        READY: "官方采集已核实，可选择计划并预览金额。",
        UNKNOWN: "采集请求结果未知，本会话不会再次派发。请核实后重新授权。",
        EXPIRED: "授权已过期，请重新授权。",
        USED: "此授权已用于一笔订单，请到我的服务订单查看。",
        REVOKED: "授权已撤销。",
      }[session.value?.state] || "",
);
function publish() {
  emit(
    "verified",
    session.value?.lookup && !uncertain.value
      ? {
          id: ready.value ? session.value.id : null,
          lookup: session.value.lookup,
        }
      : null,
  );
}
async function discard(id) {
  try {
    await revokeServiceAccountSession(id);
  } catch {
    /* Server expiry and ownership checks remain in force. */
  }
}
function reset() {
  const old = session.value?.id;
  generation++;
  session.value = null;
  secret.value = "";
  faceConsent.value = false;
  uncertain.value = false;
  publish();
  if (old) void discard(old);
}
async function run(
  action,
  { create = false, credential = false, query = false } = {},
) {
  if (working.value || !props.authorized || (uncertain.value && !query)) return;
  const version = generation;
  working.value = true;
  emit("working", true);
  try {
    if (create && !session.value) {
      const created = await createServiceAccountSession(props.productId, {
        mode: "PASSWORD",
        account: account.value,
        authorizedAccount: props.authorized,
      });
      if (version !== generation) {
        void discard(created.id);
        return;
      }
      session.value = created;
    }
    if (!session.value?.id || version !== generation) return;
    const value = await action(session.value.id);
    if (version === generation) {
      session.value = value;
      uncertain.value = false;
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
function verify() {
  const value = secret.value;
  return run((id) => verifyServiceAccount(id, { secret: value }), {
    create: true,
    credential: true,
  });
}
const checkSession = () => run(getServiceAccountSession, { query: true });
const collect = () =>
  faceConsent.value &&
  run((id) => collectServiceAccountFace(id, { authorizedFace: true }));
const checkFace = () => faceConsent.value && run(checkServiceAccountFace);
async function launch() {
  if (
    working.value ||
    uncertain.value ||
    !props.authorized ||
    !faceConsent.value ||
    !session.value?.face?.canLaunch
  )
    return;
  const popup = window.open("about:blank", "_blank");
  if (!popup) {
    ElMessage.warning("请允许打开新页面后重试。");
    return;
  }
  // Detach the opener before any external navigation. No supplier URL or token is sent to this component.
  popup.opener = null;
  popup.document.title = "正在打开官方采集页";
  popup.document.body.textContent = "正在核实一次性跳转授权，请勿转发此页面。";
  const version = generation,
    id = session.value.id;
  working.value = true;
  emit("working", true);
  try {
    const value = await issueServiceFaceLaunch(id);
    if (
      version !== generation ||
      !props.authorized ||
      !faceConsent.value ||
      !validFaceLaunchTicket(value, id)
    ) {
      popup.close();
      return;
    }
    const form = popup.document.createElement("form");
    form.method = "post";
    form.action = `${window.location.origin}/api/service-face-collection/launch`;
    for (const [name, content] of Object.entries({
      sessionId: id,
      ticket: value.ticket,
    })) {
      const input = popup.document.createElement("input");
      input.type = "hidden";
      input.name = name;
      input.value = content;
      form.appendChild(input);
    }
    popup.document.body.appendChild(form);
    form.submit();
    form.remove();
  } catch {
    popup.close();
    ElMessage.warning(
      "采集页面未能打开，请检查授权状态后再试；不会重复创建采集链接。",
    );
  } finally {
    working.value = false;
    emit("working", false);
  }
}
watch(
  () => props.productId,
  () => {
    reset();
    account.value = "";
  },
);
watch(
  () => props.authorized,
  (value) => {
    if (!value) reset();
  },
);
watch(faceConsent, (value) => {
  if (!value && session.value?.face) reset();
});
const expiryTimer = setInterval(() => {
  if (
    session.value &&
    !["EXPIRED", "USED", "REVOKED"].includes(session.value.state) &&
    serviceAccountExpired(session.value)
  ) {
    generation++;
    session.value = {
      ...session.value,
      state: "EXPIRED",
      lookup: null,
      face: null,
    };
    secret.value = "";
    uncertain.value = false;
    faceConsent.value = false;
    publish();
  }
}, 1000);
onBeforeUnmount(() => {
  clearInterval(expiryTimer);
  generation++;
  secret.value = "";
  if (session.value?.id) void discard(session.value.id);
});
</script>
<style scoped>
.heisha-face {
  margin: 20px 0;
}
.face-steps {
  padding: 0;
  margin: 0 0 22px;
  display: flex;
  gap: 10px;
  list-style: none;
  flex-wrap: wrap;
}
.face-steps li {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.face-steps b {
  font-weight: 600;
  font-size: 11px;
  border: 1px solid var(--el-border-color);
  border-radius: 50%;
  width: 25px;
  height: 25px;
  display: grid;
  place-items: center;
}
.face-steps .active {
  color: var(--el-color-primary);
}
.face-steps .active b {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}
.face-account-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}
.face-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 16px 0;
}
.face-actions .el-button {
  min-height: 44px;
  margin-left: 0;
}
.collection-card {
  margin-top: 18px;
  padding: 20px;
  background: var(--el-fill-color-extra-light);
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
}
.collection-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}
.collection-heading h4 {
  margin: 0;
  font-size: 15px;
}
.collection-card p,
.face-deadline {
  line-height: 1.8;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.collection-origin {
  display: grid;
  gap: 5px;
  padding: 12px 14px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  margin: 16px 0;
  overflow-wrap: anywhere;
}
.collection-origin span {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.collection-origin strong {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.face-consent {
  min-height: 44px;
  height: auto;
  white-space: normal;
  align-items: flex-start;
}
.face-consent :deep(.el-checkbox__input) {
  margin-top: 5px;
}
.face-consent :deep(.el-checkbox__label) {
  line-height: 1.7;
  white-space: normal;
}
.collection-progress {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 10px;
  margin-top: 18px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.collection-progress strong {
  font-size: 18px;
  margin: 0 3px;
  color: var(--el-text-color-primary);
}
.collection-hint {
  margin-bottom: 0;
}
.face-deadline {
  margin: 14px 0 0;
}
@media (max-width: 580px) {
  .face-account-grid {
    grid-template-columns: 1fr;
  }
  .collection-card {
    padding: 16px;
  }
}
</style>
