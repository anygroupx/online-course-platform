<template>
  <div v-if="allowed && isSelfOperatedHeisha(order)" class="fulfillment-controls">
    <el-tag :type="verificationTag[order.verificationStatus] || 'info'">
      {{ verificationNames[order.verificationStatus] || "待资格核验" }}
    </el-tag>
    <el-button
      v-for="action in fulfillmentActions(order)"
      :key="action"
      :type="action === 'CANCEL_REFUND' ? 'danger' : 'default'"
      :disabled="busy"
      @click="openAction(action)"
    >{{ fulfillmentActionNames[action] }}</el-button>
    <el-button :disabled="busy" @click="showDetails">查看履约资料</el-button>

    <el-dialog
      v-model="actionOpen"
      :title="fulfillmentActionNames[selectedAction]"
      width="460px"
      :close-on-click-modal="false"
      @closed="clearAction"
    >
      <el-alert
        v-if="selectedAction === 'CANCEL_REFUND'"
        type="warning"
        :closable="false"
        :title="`确认后将取消未开始的订单，并退回 ¥${refundableAmount}。金额由服务器按订单账本计算。`"
      />
      <el-form label-position="top" @submit.prevent="save">
        <el-form-item v-if="selectedAction === 'PROGRESS'" label="已完成数量">
          <el-input-number
            v-model="completed"
            :min="order.completed + 1"
            :max="order.quantity"
            :precision="0"
            aria-label="已完成数量"
          />
        </el-form-item>
        <el-form-item :label="selectedAction === 'CANCEL_REFUND' ? '取消原因' : '处理备注'">
          <el-input
            v-model="note"
            type="textarea"
            maxlength="1000"
            autocomplete="off"
            placeholder="仅填写处理情况，请勿填写账号、密码或授权信息"
          />
        </el-form-item>
        <p v-if="message" role="alert">{{ message }}</p>
        <el-button
          :type="selectedAction === 'CANCEL_REFUND' ? 'danger' : 'primary'"
          native-type="submit"
          :loading="busy"
        >确认处理</el-button>
      </el-form>
    </el-dialog>

    <el-dialog
      v-model="verificationOpen"
      :title="verificationActionNames[verificationAction]"
      width="520px"
      :close-on-click-modal="false"
      @closed="clearVerification"
    >
      <el-form label-position="top" @submit.prevent="saveVerification">
        <template v-if="verificationAction === 'VERIFY'">
          <el-form-item label="已核实的计划编号"><el-input v-model="verification.resolvedPlanId" maxlength="120" /></el-form-item>
          <el-form-item label="已核实的计划名称"><el-input v-model="verification.resolvedPlanName" maxlength="200" /></el-form-item>
          <el-form-item label="已核实的区域编号"><el-input v-model="verification.resolvedFenceId" maxlength="120" /></el-form-item>
          <el-form-item label="已核实的区域名称"><el-input v-model="verification.resolvedFenceName" maxlength="200" /></el-form-item>
          <el-form-item label="已核实的单次最少公里"><el-input-number v-model="verification.resolvedMinDistance" :min="0.1" :max="50" :precision="2" /></el-form-item>
          <el-form-item label="已核实的单次最多公里"><el-input-number v-model="verification.resolvedMaxDistance" :min="0.1" :max="50" :precision="2" /></el-form-item>
        </template>
        <el-form-item :label="verificationAction === 'VERIFY' ? '核验备注' : '原因（必填）'">
          <el-input
            v-model="verification.note"
            type="textarea"
            maxlength="1000"
            autocomplete="off"
            placeholder="请勿填写账号、密码或人脸资料"
          />
        </el-form-item>
        <p v-if="message" role="alert">{{ message }}</p>
        <el-button
          :type="verificationAction === 'REJECT' ? 'danger' : 'primary'"
          native-type="submit"
          :loading="busy"
        >确认提交</el-button>
      </el-form>
    </el-dialog>

    <el-drawer
      v-model="detailsOpen"
      title="履约资料与资格核验"
      :before-close="closeDrawer"
      destroy-on-close
      size="min(620px, 94vw)"
    >
      <p>资料仅用于处理此订单，请勿复制到处理备注或其他系统。</p>
      <p v-if="busy" role="status">正在读取资料…</p>
      <p v-if="message" role="alert">{{ message }}</p>
      <template v-if="details">
        <div class="verification-summary">
          <el-tag :type="verificationTag[details.verificationStatus] || 'info'">
            {{ verificationNames[details.verificationStatus] || details.verificationStatus }}
          </el-tag>
          <span>资料版本 {{ details.materialVersion }} · 核验版本 {{ details.version }}</span>
        </div>
        <p v-if="details.verificationNote"><strong>最近核验说明：</strong>{{ details.verificationNote }}</p>
        <dl>
          <template v-for="(value, key) in details.fields" :key="key">
            <dt>{{ fulfillmentFieldNames[key] || fieldNames[key] || key }}</dt><dd>{{ value }}</dd>
          </template>
        </dl>

        <section v-if="details.assets?.length" class="asset-list">
          <h3>人脸资格核验材料</h3>
          <article v-for="asset in details.assets" :key="asset.id" class="asset-card">
            <p>{{ asset.width }} × {{ asset.height }} · {{ asset.byteSize }} 字节</p>
            <p v-if="asset.purgedAt">材料已按保留期限清除。</p>
            <template v-else-if="biometricAllowed">
              <el-button :loading="assetBusy === asset.id" @click="loadAsset(asset)">查看人脸材料</el-button>
              <img v-if="assetUrls[asset.id]" :src="assetUrls[asset.id]" alt="当前订单的人脸资格核验材料" />
            </template>
            <p v-else>当前账号没有查看人脸材料的权限。</p>
          </article>
        </section>

        <div v-if="verificationActions.length" class="verification-actions">
          <el-button
            v-for="action in verificationActions"
            :key="action"
            :type="action === 'REJECT' ? 'danger' : action === 'VERIFY' ? 'primary' : 'warning'"
            :disabled="busy"
            @click="openVerification(action)"
          >{{ verificationActionNames[action] }}</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, ref, watch, onBeforeUnmount, onDeactivated } from 'vue';
import { sessionUserInfo, authSessionScope, hasAuthenticatedSession } from '@/utils/authSession';
import { latestRequest } from '@/utils/pluginIntegrations';
import { fieldNames } from '@/utils/serviceCommerce';
import { isSelfOperatedHeisha, fulfillmentActions, fulfillmentActionNames, fulfillmentCommand, fulfillmentVerificationActions } from '@/utils/serviceFulfillment';
import { getServiceFulfillment, getServiceFulfillmentAsset, manageServiceFulfillment, verifyServiceFulfillment } from '@/api/serviceCommerce';

const fulfillmentFieldNames = Object.freeze({
  phone: '服务账号', password: '密码', plan_option_id: '计划编号', fence_option_id: '区域编号',
  run_time: '执行时间', school_name: '学校', plan_name: '计划', fence_name: '区域',
  single_min_distance_km: '单次最少公里数', single_max_distance_km: '单次最多公里数',
  time_fragments: '可用时段', product_id: '服务编号', times: '次数', km_per_day: '每次公里数',
  note: '用户说明', supplemental_note: '用户补充说明',
});
const verificationNames = Object.freeze({ PENDING: '待资格核验', VERIFIED: '资格已通过', NEEDS_INFO: '需要补充资料', REJECTED: '资格未通过' });
const verificationTag = Object.freeze({ PENDING: 'warning', VERIFIED: 'success', NEEDS_INFO: 'warning', REJECTED: 'danger' });
const verificationActionNames = Object.freeze({ VERIFY: '核验通过', NEEDS_INFO: '要求补充资料', REJECT: '核验不通过' });
const props = defineProps({ order: { type: Object, required: true } });
const emit = defineEmits(['changed']);
const permissions = computed(() => sessionUserInfo.value?.permissions || []);
const allowed = computed(() => hasAuthenticatedSession.value && permissions.value.includes('service-order:fulfill'));
const biometricAllowed = computed(() => allowed.value && permissions.value.includes('service-order:biometric'));
const refundableAmount = computed(() => Math.max(0, Number(props.order.paidAmount || 0) - Number(props.order.refundedAmount || 0)).toFixed(2));
const verificationActions = computed(() => details.value
  ? fulfillmentVerificationActions(props.order, details.value.verificationStatus) : []);
const requests = latestRequest(), assetRequests = latestRequest();
const busy = ref(false), actionOpen = ref(false), detailsOpen = ref(false), details = ref(null), message = ref('');
const selectedAction = ref(''), completed = ref(0), note = ref('');
const verificationOpen = ref(false), verificationAction = ref('');
const verification = ref({ note: '', resolvedPlanId: '', resolvedPlanName: '', resolvedFenceId: '', resolvedFenceName: '', resolvedMinDistance: undefined, resolvedMaxDistance: undefined });
const assetUrls = ref({}), assetBusy = ref('');

function revokeAssets() {
  Object.values(assetUrls.value).forEach((url) => URL.revokeObjectURL(url));
  assetUrls.value = {};
  assetBusy.value = '';
  assetRequests.invalidate();
}
function clearAction() { note.value = ''; selectedAction.value = ''; message.value = ''; }
function clearVerification() {
  verificationAction.value = '';
  verification.value = { note: '', resolvedPlanId: '', resolvedPlanName: '', resolvedFenceId: '', resolvedFenceName: '', resolvedMinDistance: undefined, resolvedMaxDistance: undefined };
  message.value = '';
}
function clear() {
  requests.invalidate(); revokeAssets(); details.value = null; detailsOpen.value = false;
  verificationOpen.value = false; actionOpen.value = false; busy.value = false;
  clearAction(); clearVerification();
}
function closeDrawer(done) { clear(); done(); }
function openAction(action) { clear(); selectedAction.value = action; completed.value = (props.order.completed || 0) + 1; actionOpen.value = true; }
function openVerification(action) {
  verificationAction.value = action;
  verification.value = {
    note: '',
    resolvedPlanId: String(details.value?.fields?.plan_option_id || ''),
    resolvedPlanName: String(details.value?.fields?.plan_name || ''),
    resolvedFenceId: String(details.value?.fields?.fence_option_id || ''),
    resolvedFenceName: String(details.value?.fields?.fence_name || ''),
    resolvedMinDistance: details.value?.fields?.single_min_distance_km == null ? undefined : Number(details.value.fields.single_min_distance_km),
    resolvedMaxDistance: details.value?.fields?.single_max_distance_km == null ? undefined : Number(details.value.fields.single_max_distance_km),
  };
  message.value = '';
  verificationOpen.value = true;
}
async function showDetails() {
  clear(); detailsOpen.value = true; busy.value = true;
  const ticket = requests.begin();
  try {
    const value = await getServiceFulfillment(props.order.id, ticket.signal);
    if (ticket.current() && allowed.value && detailsOpen.value) details.value = value;
  } catch { if (ticket.current()) message.value = '无法读取履约资料，请确认权限后重试。'; }
  finally { if (ticket.current()) busy.value = false; }
}
async function loadAsset(asset) {
  if (!biometricAllowed.value || assetBusy.value) return;
  const ticket = assetRequests.begin(); assetBusy.value = asset.id; message.value = '';
  try {
    const blob = await getServiceFulfillmentAsset(props.order.id, asset.id, ticket.signal);
    const url = URL.createObjectURL(blob);
    if (!ticket.current() || !biometricAllowed.value || !detailsOpen.value) { URL.revokeObjectURL(url); return; }
    if (assetUrls.value[asset.id]) URL.revokeObjectURL(assetUrls.value[asset.id]);
    assetUrls.value = { ...assetUrls.value, [asset.id]: url };
  } catch { if (ticket.current()) message.value = '无法读取人脸材料，请确认权限和材料状态后重试。'; }
  finally { if (ticket.current()) assetBusy.value = ''; }
}
async function save() {
  if (busy.value || !allowed.value) return;
  let command;
  try { command = fulfillmentCommand(props.order, selectedAction.value, completed.value, note.value); }
  catch (error) { message.value = error.message; return; }
  const ticket = requests.begin(); busy.value = true;
  try {
    await manageServiceFulfillment(props.order.id, command);
    if (ticket.current()) { clear(); emit('changed'); }
  } catch { if (ticket.current()) { message.value = '处理未成功，请刷新订单后核对状态和权限。'; emit('changed'); } }
  finally { if (ticket.current()) busy.value = false; }
}
async function saveVerification() {
  if (busy.value || !allowed.value || !details.value) return;
  const value = verification.value;
  if (['NEEDS_INFO', 'REJECT'].includes(verificationAction.value) && !value.note.trim()) {
    message.value = '请填写原因。'; return;
  }
  const clean = (text) => text.trim() || null;
  const payload = {
    action: verificationAction.value,
    orderVersion: props.order.version,
    fulfillmentVersion: details.value.version,
    note: clean(value.note),
    resolvedPlanId: clean(value.resolvedPlanId), resolvedPlanName: clean(value.resolvedPlanName),
    resolvedFenceId: clean(value.resolvedFenceId), resolvedFenceName: clean(value.resolvedFenceName),
    resolvedMinDistance: value.resolvedMinDistance ?? null,
    resolvedMaxDistance: value.resolvedMaxDistance ?? null,
  };
  const ticket = requests.begin(); busy.value = true;
  try {
    await verifyServiceFulfillment(props.order.id, payload);
    if (ticket.current()) { clear(); emit('changed'); }
  } catch { if (ticket.current()) { message.value = '资格核验未保存，请刷新订单后重试。'; emit('changed'); } }
  finally { if (ticket.current()) busy.value = false; }
}

watch(authSessionScope, clear, { flush: 'sync' });
watch(allowed, (value) => { if (!value) clear(); }, { flush: 'sync' });
watch(() => props.order.id, clear, { flush: 'sync' });
watch(biometricAllowed, (value) => { if (!value) revokeAssets(); }, { flush: 'sync' });
onDeactivated(clear);
onBeforeUnmount(clear);
</script>

<style scoped>
.fulfillment-controls, .verification-actions { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.verification-summary { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-bottom: 14px; }
dl { display: grid; grid-template-columns: minmax(110px, auto) 1fr; gap: 8px 16px; word-break: break-word; }
dt { color: var(--el-text-color-secondary); }
dd { margin: 0; }
.asset-list { margin-top: 24px; }
.asset-card { padding: 12px; border: 1px solid var(--el-border-color); border-radius: 8px; margin-bottom: 12px; }
.asset-card img { display: block; max-width: 100%; max-height: 420px; object-fit: contain; margin-top: 12px; border-radius: 8px; }
.verification-actions { margin-top: 24px; }
</style>
