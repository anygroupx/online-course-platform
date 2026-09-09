<template>
  <el-dialog
    :model-value="!!quote"
    title="确认本次操作"
    width="min(480px, 94vw)"
    :close-on-click-modal="false"
    :close-on-press-escape="!sending"
    :show-close="!sending"
    @close="close"
  >
    <template v-if="quote">
      <p class="quote-context">
        {{ actionNames[quote.action] }} · {{ quote.title }}
      </p>
      <div class="quote-money">
        <span>{{ quote.amountLabel }}</span
        ><strong>¥{{ moneyText(quote.amount) }}</strong>
      </div>
      <p class="quote-note">
        {{
          quote.quantity
            ? `${quote.action === "REFUND" ? "最多" : "本次"} ${quote.quantity} ${quote.quantityUnit || "次"} · `
            : ""
        }}金额由服务器计算。{{
          administrative
            ? "确认后退回订单所属用户的余额，不会再次向上游发起退款。"
            : Number(quote.amount) === 0
              ? "本次无需扣款。确认编号只提交一次，执行结果仍需上游确认。"
              : ["CREATE", "ADD_TIMES", "EDIT_SCHEDULE", "RUN_NOW"].includes(
                    quote.action,
                  )
                ? "确认后扣除账户余额；相同确认编号不会重复扣款。"
                : quote.action === "REFUND"
                  ? "按已核实的未用服务量和订单单价退回账户余额。"
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
            ? '请确认已查验上游退款流水。本操作只进行本平台账本入账，提交后不可在此撤回。'
            : '取消与退款按服务条款及已核实的未用次数 / 服务日结算；上游超时转入核对，不自动重复提交。'
        "
        type="info"
        :closable="false"
      />
      <p v-if="quote.action === 'DELAY_TASK'" class="quote-note">
        仅延期所选任务，不会延期整笔订单。新的执行时间以上游回执及刷新后的记录为准。
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
              ? "退款"
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
import { ref, watch } from "vue";
import {
  confirmServiceOperation,
  getServiceOperation,
  confirmServiceRefundSettlement,
  getAdminServiceOperation,
} from "@/api/serviceCommerce";
import {
  actionNames,
  stateName,
  moneyText,
  knownOutcome,
} from "@/utils/serviceCommerce";
const props = defineProps({ quote: Object, administrative: Boolean });
const emit = defineEmits(["close", "result"]);
const sending = ref(false),
  started = ref(false),
  result = ref(null);
watch(
  () => props.quote?.id,
  () => {
    started.value = false;
    result.value = null;
  },
);
function receive(value) {
  result.value = value;
  emit("result", value);
  if (knownOutcome(value.state)) emit("close");
}
async function confirm() {
  if (started.value || sending.value || !props.quote) return;
  started.value = true;
  sending.value = true;
  try {
    receive(
      await (
        props.administrative
          ? confirmServiceRefundSettlement
          : confirmServiceOperation
      )(props.quote.id),
    );
  } catch {
    /* Keep the ID; only query the result, never resubmit automatically. */
  } finally {
    sending.value = false;
  }
}
async function check() {
  if (sending.value || !props.quote) return;
  sending.value = true;
  try {
    receive(
      await (
        props.administrative ? getAdminServiceOperation : getServiceOperation
      )(props.quote.id),
    );
  } catch {
    /* The durable confirmation remains available in the order list. */
  } finally {
    sending.value = false;
  }
}
function close() {
  if (!sending.value) emit("close");
}
</script>
<style scoped>
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
