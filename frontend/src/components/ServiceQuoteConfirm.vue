<template>
  <el-dialog
    :model-value="!!quote"
    title="确认本次操作"
    class="service-quote-dialog"
    :class="{ 'distance-quote': !!quote?.distancePlan }"
    width="min(480px, 94vw)"
    :close-on-click-modal="false"
    :close-on-press-escape="!sending"
    :show-close="!sending"
    @close="close"
  >
    <template v-if="quote">
      <p class="quote-context">
        {{ serviceActionName(quote.action, { ...quote, providerType }) }} · {{ quote.title }}
      </p>
      <div class="quote-money">
        <span>{{ quote.amountLabel }}</span
        ><strong>¥{{ moneyText(quote.amount) }}</strong>
      </div>
      <template v-if="chargeDetails">
        <dl class="quote-charge-details" aria-label="费用明细">
          <div>
            <dt>订单计费单价</dt>
            <dd>¥{{ chargeDetails.unitCharge }} / {{ chargeDetails.unit }}</dd>
          </div>
          <div>
            <dt>本次数量</dt>
            <dd>{{ chargeDetails.quantity }} {{ chargeDetails.unit }}</dd>
          </div>
        </dl>
        <p class="quote-rounding">{{ chargeDetails.refund
          ? "退款受可退余额限制，以本次预览金额为准。"
          : "按订单单价计算，总额保留两位小数。" }}</p>
      </template>
      <TotalDistancePlanSummary v-if="quote.distancePlan" :plan="quote.distancePlan" />
      <p class="quote-note">
        {{
          quote.quantity
            ? `${quote.action === "REFUND" ? "最多" : "本次"} ${quote.quantity} ${quote.quantityUnit || "次"} · `
            : ""
        }}金额由服务器计算。{{
          administrative
            ? "确认后退回订单所属用户的余额，不会再次提交退款申请。"
            : Number(quote.amount) === 0
              ? "本次无需扣款。确认编号只提交一次，执行结果仍需后续确认。"
              : ["CREATE", "ADD_TIMES", "EDIT_SCHEDULE", "RUN_NOW"].includes(
                    quote.action,
                  )
                ? "确认后扣除账户余额；相同确认编号不会重复扣款。"
                : quote.action === "REFUND"
                  ? providerType === "jingyu" ? "实际退款次数核实后再入账，不以预估上限直接退款。" : "按已核实的未用服务量和订单单价退回账户余额。"
                  : "此操作不额外扣款。"
        }}
      </p>
      <el-alert
        v-if="started"
        :title="
          result ? stateName(result.state) : '正在检查这笔提交，请勿重新下单'
        "
        type="warning"
        :closable="false"
        show-icon
      />
      <el-alert
        v-else
        :title="
          administrative
            ? '请确认已查验退款流水。本操作只记录退款入账，提交后不可在此撤回。'
            : providerType === 'jingyu' && quote.action === 'REFUND'
              ? '只提交取消申请，不会按预估次数自动退款。结果待核对时，请检查原编号并联系管理员核实确切退款次数。'
            : quote.action === 'CANCEL'
              ? '这里只取消订单，不会退回余额；取消成功后仍需单独核对退款。结果不确定时只检查原编号，不要重复提交。'
            : quote.distancePlan
              ? '这里只确认提交，不代表执行完成；暂不支持在线取消或自动退款。结果不确定时只检查原编号，不要重新下单。'
              : '取消与退款按服务条款及已核实的未用次数 / 服务日结算；结果超时将转入核对，不自动重复提交。'
        "
        type="info"
        :closable="false"
      />
      <p v-if="quote.action === 'DELAY_TASK'" class="quote-note">
        仅延期所选任务，不会延期整笔订单。新的执行时间以确认结果及刷新后的记录为准。
      </p>
      <p class="quote-id">确认编号 {{ quote.id }}</p>
    </template>
    <template #footer>
      <el-button :disabled="sending" @click="close">{{
        started ? "稍后查看订单" : "返回修改"
      }}</el-button>
      <el-button
        v-if="!started"
        type="primary"
        :loading="sending"
        @click="confirm"
        >确认{{
          administrative
            ? "退款入账"
            : quote?.action === "REFUND"
              ? providerType === "jingyu" ? "取消申请" : "退款"
              : quote?.action === "CREATE"
                ? "并下单"
                : "操作"
        }}</el-button
      >
      <el-button v-else type="primary" :loading="sending" @click="check"
        >检查提交结果</el-button
      >
    </template>
  </el-dialog>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount } from "vue";
import { authSessionScope } from "@/utils/authSession";
import TotalDistancePlanSummary from "@/components/TotalDistancePlanSummary.vue";
import {
  confirmServiceOperation,
  getServiceOperation,
  confirmServiceRefundSettlement,
  getAdminServiceOperation,
} from "@/api/serviceCommerce";
import {
  serviceActionName,
  stateName,
  moneyText,
  knownOutcome,
  quoteChargeDetails,
} from "@/utils/serviceCommerce";
const props = defineProps({ quote: Object, administrative: Boolean, providerType: { type: String, default: "" } });
const emit = defineEmits(["close", "result"]);
const chargeDetails = computed(() => quoteChargeDetails(props.quote));
const sending = ref(false),
  started = ref(false),
  result = ref(null);
let generation = 0;
function clearContext() {
  generation++;
  sending.value = false;
  started.value = false;
  result.value = null;
}
watch(() => props.quote?.id, clearContext, { flush: "sync" });
watch(authSessionScope, () => { clearContext(); emit("close"); }, { flush: "sync" });
onBeforeUnmount(() => { generation++; });
function receive(value) {
  result.value = value;
  emit("result", value);
  if (knownOutcome(value.state)) emit("close");
}
async function confirm() {
  if (started.value || sending.value || !props.quote) return;
  const current = generation;
  started.value = true;
  sending.value = true;
  try {
    const value = await (
      props.administrative
        ? confirmServiceRefundSettlement
        : confirmServiceOperation
    )(props.quote.id);
    // Dispatch is durable; a stale receipt must never update a different quote or identity.
    if (current === generation) receive(value);
  } catch {
    /* Keep the ID; only query the result, never resubmit automatically. */
  } finally {
    if (current === generation) sending.value = false;
  }
}
async function check() {
  if (sending.value || !props.quote) return;
  const current = generation;
  sending.value = true;
  try {
    const value = await (
      props.administrative ? getAdminServiceOperation : getServiceOperation
    )(props.quote.id);
    if (current === generation) receive(value);
  } catch {
    /* The durable confirmation remains available in the order list. */
  } finally {
    if (current === generation) sending.value = false;
  }
}
function close() {
  if (!sending.value) emit("close", started.value && !knownOutcome(result.value?.state) ? { pending: true } : null);
}
</script>
<style scoped>
:global(.service-quote-dialog .el-button) { min-height: 44px; }
.quote-charge-details {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  margin: 20px 0 8px;
}
.quote-charge-details dt, .quote-rounding {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.8;
}
.quote-charge-details dd {
  margin: 6px 0 0;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.quote-rounding { margin: 0 0 14px; }
.quote-context {
  color: var(--el-text-color-secondary);
}
.quote-money {
  padding: 24px;
  background: var(--el-fill-color-light);
  border-radius: 16px;
  display: grid;
  gap: 8px;
}
.quote-money strong {
  font-size: 36px;
  letter-spacing: -1px;
  color: var(--el-color-primary);
}
.quote-note {
  line-height: 1.8;
}
.quote-id {
  font: 11px monospace;
  overflow-wrap: anywhere;
  color: var(--el-text-color-secondary);
  margin-top: 20px;
}
</style>
