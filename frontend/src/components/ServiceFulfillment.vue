<template>
  <div v-if="allowed && isSelfOperatedHeisha(order)" class="fulfillment-controls">
    <el-button v-for="action in fulfillmentActions(order)" :key="action" :disabled="busy" @click="openAction(action)">{{ fulfillmentActionNames[action] }}</el-button>
    <el-button :disabled="busy" @click="showDetails">查看履约资料</el-button>
    <el-dialog v-model="actionOpen" :title="fulfillmentActionNames[selectedAction]" width="460px" :close-on-click-modal="false" @closed="clearAction">
      <el-form label-position="top" @submit.prevent="save">
        <el-form-item v-if="selectedAction === 'PROGRESS'" label="已完成数量">
          <el-input-number v-model="completed" :min="order.completed + 1" :max="order.quantity" :precision="0" aria-label="已完成数量" />
        </el-form-item>
        <el-form-item label="处理备注"><el-input v-model="note" type="textarea" maxlength="1000" autocomplete="off" placeholder="仅填写处理情况，请勿填写账号、密码或授权信息" /></el-form-item>
        <p v-if="message" role="alert">{{ message }}</p>
        <el-button type="primary" native-type="submit" :loading="busy">确认处理</el-button>
      </el-form>
    </el-dialog>
    <el-drawer v-model="detailsOpen" title="履约资料" :before-close="closeDrawer" destroy-on-close>
      <p>仅用于处理此订单，请勿复制到处理备注。退款需联系管理员进行财务核对。</p>
      <p v-if="busy" role="status">正在读取资料…</p>
      <p v-if="message" role="alert">{{ message }}</p>
      <dl v-if="details"><template v-for="(value, key) in details.fields" :key="key"><dt>{{ fulfillmentFieldNames[key] || fieldNames[key] || key }}</dt><dd>{{ value }}</dd></template></dl>
    </el-drawer>
  </div>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount, onDeactivated } from 'vue';
import { sessionUserInfo, authSessionScope, hasAuthenticatedSession } from '@/utils/authSession';
import { latestRequest } from '@/utils/pluginIntegrations';
import { fieldNames } from '@/utils/serviceCommerce';
import { isSelfOperatedHeisha, fulfillmentActions, fulfillmentActionNames, fulfillmentCommand } from '@/utils/serviceFulfillment';
import { getServiceFulfillment, manageServiceFulfillment } from '@/api/serviceCommerce';
const fulfillmentFieldNames = Object.freeze({
  phone: '服务账号', password: '密码', plan_option_id: '计划编号', fence_option_id: '区域编号',
  run_time: '执行时间', school_name: '学校', plan_name: '计划', fence_name: '区域',
  single_min_distance_km: '单次最少公里数', single_max_distance_km: '单次最多公里数',
  time_fragments: '可用时段', product_id: '服务编号', times: '次数', km_per_day: '每次公里数',
});
const props = defineProps({ order: { type: Object, required: true } });
const emit = defineEmits(['changed']);
const allowed = computed(() => hasAuthenticatedSession.value && sessionUserInfo.value?.permissions?.includes('service-order:fulfill'));
const requests = latestRequest();
const busy = ref(false), actionOpen = ref(false), detailsOpen = ref(false), details = ref(null), message = ref('');
const selectedAction = ref(''), completed = ref(0), note = ref('');
function clearAction() { note.value = ''; selectedAction.value = ''; message.value = ''; }
function clear() { requests.invalidate(); details.value = null; detailsOpen.value = false; actionOpen.value = false; busy.value = false; clearAction(); }
function closeDrawer(done) { clear(); done(); }
function openAction(action) { clear(); selectedAction.value = action; completed.value = props.order.completed + 1; actionOpen.value = true; }
async function showDetails() {
  clear(); detailsOpen.value = true; busy.value = true;
  const ticket = requests.begin();
  try {
    const value = await getServiceFulfillment(props.order.id, ticket.signal);
    if (ticket.current() && allowed.value && detailsOpen.value) details.value = value;
  } catch { if (ticket.current()) message.value = '无法读取履约资料，请确认权限后重试。'; }
  finally { if (ticket.current()) busy.value = false; }
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
watch(authSessionScope, clear);
watch(() => props.order.id, clear);
onDeactivated(clear);
onBeforeUnmount(clear);
</script>
