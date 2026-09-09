<template>
  <div class="service-orders">
    <header class="orders-header">
      <div>
        <span class="eyebrow">{{
          admin ? "OPERATIONS / 服务运营" : "MY SERVICES / 服务订单"
        }}</span>
        <h1>{{ admin ? "服务订单与对账" : "我的服务订单" }}</h1>
        <p>
          {{
            admin
              ? "只在核实上游结果后处理不确定操作；每笔处理都有账本与操作记录。"
              : "进度、计划和售后都在这里。结果不确定时请勿重复下单。"
          }}
        </p>
      </div>
      <el-button :loading="loading" @click="load">刷新列表</el-button
      ><el-button v-if="!admin" type="primary" @click="router.push('/services')"
        >购买服务</el-button
      >
    </header>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
    <div v-loading="loading" class="order-list">
      <article
        v-for="item in items"
        :key="item.id"
        class="order-card"
        :class="{ focused: route.query.focus === item.id }"
      >
        <div class="order-title">
          <div>
            <small
              >{{ serviceNames[item.providerType] }} /
              {{ item.createTime?.replace("T", " ") }}</small
            >
            <h2>{{ item.title }}</h2>
          </div>
          <el-tag :type="tagType(item.status)">{{
            stateName(item.status)
          }}</el-tag>
        </div>
        <div class="order-metrics">
          <div>
            <span>服务账号</span><strong>{{ item.accountLabel }}</strong>
          </div>
          <div>
            <span>{{
              item.providerType === "sxdk_tw"
                ? "累计已购服务日"
                : "完成 / 总次数"
            }}</span
            ><strong
              >{{
                item.providerType === "sxdk_tw"
                  ? item.quantity
                  : (item.completed ?? "—")
              }}
              <small>{{
                item.providerType === "sxdk_tw" ? "天" : `/ ${item.quantity}`
              }}</small></strong
            >
          </div>
          <div>
            <span>{{
              item.providerType === "sxdk_tw" ? "服务截止日" : "每次距离"
            }}</span
            ><strong
              >{{
                item.providerType === "sxdk_tw"
                  ? item.schedule?.endDate
                  : item.distance
              }}
              <small v-if="item.providerType !== 'sxdk_tw'">公里</small></strong
            >
          </div>
          <div>
            <span>已扣款 / 已退款</span
            ><strong
              >¥{{ moneyText(item.paidAmount) }}
              <small>/ ¥{{ moneyText(item.refundedAmount) }}</small></strong
            >
          </div>
        </div>
        <el-progress
          v-if="item.completed != null"
          :percentage="
            Math.min(100, Math.round((item.completed / item.quantity) * 100))
          "
          :show-text="false"
          :stroke-width="5"
        />
        <p
          v-if="item.completed == null && item.providerType !== 'sxdk_tw'"
          class="unknown-progress"
        >
          上游只提供订单状态，未返回完成次数；具体执行情况请查看执行记录。
        </p>
        <p v-if="item.providerType === 'sxdk_tw'" class="unknown-progress">
          {{ internshipCalendarText(item.schedule) }} ·
          执行结果见上游记录，日历不代表考勤已完成。
        </p>
        <el-alert
          v-if="item.pendingOperationId"
          title="上一笔操作仍待确认：扣款和订单已保存，请先检查结果，不要再次购买。"
          type="warning"
          :closable="false"
        />
        <el-alert
          v-else-if="item.status === 'REFUND_REVIEW'"
          title="上游报告退款状态，本平台尚未入账，请联系管理员核对。"
          type="warning"
          :closable="false"
        />
        <footer>
          <span class="order-number">{{ item.id }}</span>
          <div class="order-actions">
            <template v-if="!admin"
              ><el-button
                v-if="item.pendingOperationId"
                :loading="busyId === item.id"
                @click="checkPending(item)"
                >检查提交结果</el-button
              ><el-button
                v-else
                :disabled="['CANCELLED', 'REFUNDED'].includes(item.status)"
                :loading="busyId === item.id"
                @click="sync(item)"
                >更新进度</el-button
              ><el-button @click="showEvents(item)">操作记录</el-button
              ><el-button v-if="item.providerType === 'sxdk_tw'" @click="notificationOrder=item; notificationOpen=true">微信通知</el-button
              ><el-button
                v-if="
                  item.providerType !== 'heisha' && !item.pendingOperationId
                "
                @click="showRunLogs(item)"
                >执行记录</el-button
              ><el-button
                v-for="action in item.actions.filter(
                  (a) => !['CHANGE_TIME', 'DELAY_TASK'].includes(a),
                )"
                :key="action"
                :type="action === 'REFUND' ? 'danger' : 'primary'"
                plain
                :loading="busyId === item.id"
                @click="prepareAction(item, action)"
                >{{ actionNames[action] }}</el-button
              ></template
            >
            <template v-else>
              <el-button @click="showAudit(item)">核对资料与记录</el-button>
              <el-button
                v-if="item.pendingOperationId"
                type="warning"
                @click="openResolve(item)"
                >核对上游结果</el-button
              >
              <el-button
                v-else-if="item.status === 'REFUND_REVIEW'"
                type="warning"
                @click="openSettlement(item)"
                >核对退款入账</el-button
              >
            </template>
          </div>
        </footer>
      </article>
    </div>
    <el-empty v-if="!loading && !items.length" description="还没有服务订单" />
    <el-pagination
      v-if="total > 20"
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="prev, pager, next"
      @current-change="load"
    />
    <ServiceNotifications v-if="!admin" v-model="notificationOpen" :order="notificationOrder" />
    <ServiceQuoteConfirm
      :quote="quote"
      :administrative="quote?.action === 'SETTLE_REFUND'"
      @close="quote = null"
      @result="onQuoteResult"
    />
    <el-drawer
      v-model="eventsOpen"
      title="订单操作记录"
      size="min(520px, 100vw)"
      ><el-timeline
        ><el-timeline-item
          v-for="event in events"
          :key="event.id"
          :timestamp="event.createTime?.replace('T', ' ')"
          ><b>{{ actionNames[event.action] }} · {{ stateName(event.state) }}</b>
          <p>¥{{ event.amount }}</p>
          <small class="order-number">{{ event.id }}</small>
          <p v-if="event.errorCategory">
            结果尚需核对；此记录未自动重试。
          </p></el-timeline-item
        ></el-timeline
      ></el-drawer
    >
    <el-drawer
      v-model="runLogsOpen"
      title="上游执行记录"
      size="min(520px, 100vw)"
      ><el-alert
        v-if="runLogOrder?.providerType === 'sxdk_tw'"
        type="info"
        :closable="false"
        class="log-privacy-note"
        title="仅展示最近十条记录的时间与类别，不代表执行成功。为保护账号信息，不展示上游原始日志。" /><el-timeline
        ><el-timeline-item
          v-for="log in runLogs.items"
          :key="log.id"
          :timestamp="log.time"
          >{{ log.status
          }}<el-button
            v-if="
              runLogOrder?.providerType === 'flash' &&
              ['未开始', '需要处理'].includes(log.status)
            "
            text
            type="primary"
            @click="editTaskTime(log)"
            >修改时间</el-button
          ><el-button
            v-if="
              runLogOrder?.providerType === 'flash' &&
              runLogOrder.actions?.includes('DELAY_TASK') &&
              ['未开始', '需要处理'].includes(log.status)
            "
            text
            type="primary"
            :loading="busyId === runLogOrder?.id"
            @click="delayTask(log)"
            >延期此任务</el-button
          ></el-timeline-item
        ></el-timeline
      >
      <div class="order-actions">
        <el-button
          :disabled="runLogPage <= 1 || runLogsLoading"
          @click="changeLogPage(-1)"
          >上一页</el-button
        ><el-button
          :disabled="!runLogs.hasMore || runLogsLoading"
          @click="changeLogPage(1)"
          >下一页</el-button
        >
      </div>
      <el-empty v-if="!runLogs.items.length" description="暂无执行记录"
    /></el-drawer>
    <el-dialog
      v-model="internshipOpen"
      class="internship-editor"
      title="编辑实习周期 / 续期"
      width="min(760px,96vw)"
      :close-on-click-modal="false"
      :show-close="!internshipLoading"
      ><el-form label-position="top" :disabled="internshipLoading"
        ><InternshipPlanFields
          v-if="internshipOrder && internshipSchedule"
          v-model="internshipForm"
          v-model:schedule="internshipSchedule"
          :project="internshipOrder.project"
          editing
          authorized /></el-form
      ><template #footer
        ><el-button
          :disabled="internshipLoading"
          @click="internshipOpen = false"
          >取消</el-button
        ><el-button
          type="primary"
          :loading="internshipLoading"
          @click="previewInternship"
          >预览续期金额</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="reportOpen"
      title="补交本人已授权的记录"
      width="min(520px,94vw)"
      :close-on-click-modal="false"
      ><el-alert
        type="info"
        :closable="false"
        title="仅可补交已购买且不晚于今天的服务日；范围内不能跳过未购日期，请分段提交。"
      /><el-form label-position="top" class="resolve-form"
        ><el-form-item label="补交类型"
          ><el-select v-model="reportForm.reportType"
            ><el-option
              v-for="type in internshipReportTypes(reportOrder?.project)"
              :key="type"
              :label="type"
              :value="type" /></el-select></el-form-item
        ><el-form-item label="开始日期"
          ><el-date-picker
            v-model="reportForm.startDate"
            :disabled-date="reportDateDisabled"
            type="date"
            value-format="YYYY-MM-DD" /></el-form-item
        ><el-form-item label="结束日期"
          ><el-date-picker
            v-model="reportForm.endDate"
            :disabled-date="reportDateDisabled"
            type="date"
            value-format="YYYY-MM-DD" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="reportOpen = false">取消</el-button
        ><el-button
          type="primary"
          :loading="internshipLoading"
          @click="previewReport"
          >预览本次操作</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="planOpen"
      title="编辑执行计划"
      width="min(620px, 95vw)"
      :close-on-click-modal="false"
    >
      <el-form v-if="planOptions" label-position="top"
        ><el-form-item
          v-for="field in [...new Set(planOptions.choices.map((c) => c.field))]"
          :key="field"
          :label="fieldNames[field] || field"
          ><el-select v-model="planFields[field]"
            ><el-option
              v-for="choice in planOptions.choices.filter(
                (c) => c.field === field,
              )"
              :key="choice.value"
              :value="choice.value"
              :label="choice.label" /></el-select></el-form-item
        ><el-form-item label="每次距离（公里）"
          ><el-input
            v-model="planFields.distance"
            inputmode="decimal" /></el-form-item
        ><WuxinPlanFields v-model="planFields" :with-start-date="false"
      /></el-form>
      <template #footer
        ><el-button @click="planOpen = false">取消</el-button
        ><el-button type="primary" :loading="planLoading" @click="previewPlan"
          >预览修改</el-button
        ></template
      >
    </el-dialog>
    <el-drawer
      v-model="auditOpen"
      title="核对资料与审计记录"
      size="min(560px, 100vw)"
    >
      <template v-if="audit"
        ><el-descriptions :column="1" border
          ><el-descriptions-item label="本平台用户">{{
            audit.userId
          }}</el-descriptions-item
          ><el-descriptions-item label="接口配置编号">{{
            audit.providerId
          }}</el-descriptions-item
          ><el-descriptions-item label="上游订单号"
            ><span class="order-number">{{
              audit.externalOrderNo || "尚未确认，须到上游查询"
            }}</span></el-descriptions-item
          ></el-descriptions
        >
        <el-timeline class="resolve-form"
          ><el-timeline-item
            v-for="entry in audit.events"
            :key="entry.operation.id"
            :timestamp="entry.operation.createTime?.replace('T', ' ')"
            ><b
              >{{ actionNames[entry.operation.action] }} ·
              {{ stateName(entry.operation.state) }}</b
            >
            <p>¥{{ moneyText(entry.operation.amount) }}</p>
            <small v-if="entry.resolvedBy"
              >核对人 #{{ entry.resolvedBy }}</small
            >
            <p v-if="entry.evidence" class="audit-evidence">
              {{ entry.evidence }}
            </p></el-timeline-item
          ></el-timeline
        ></template
      >
    </el-drawer>
    <el-dialog
      v-model="settlementOpen"
      title="核对上游主动退款"
      width="min(560px, 94vw)"
      :close-on-click-modal="false"
      :show-close="!settlementLoading"
    >
      <template v-if="settlementOrder"
        ><el-alert
          type="warning"
          title="只为已经核实的上游退款入账。不再次取消上游订单，也不把错误或超时当成退款。"
          :closable="false"
        />
        <p class="order-number">
          上游订单号 {{ settlementAudit?.externalOrderNo || "未取得" }} · 接口
          #{{ settlementAudit?.providerId }}
        </p>
        <p v-if="settlementOrder.completed == null" class="unknown-progress">
          此上游未提供完成次数，不能将总次数视为剩余次数。请核实实际可退次数后填写，默认不退款。
        </p>
        <el-form
          label-position="top"
          class="resolve-form"
          :disabled="settlementLoading"
          ><el-form-item
            :label="`实际核实的退款次数（最多 ${maxRefundableUnits(settlementOrder)} 次）`"
            ><el-input-number
              v-model="settlement.refundedUnits"
              :min="0"
              :max="maxRefundableUnits(settlementOrder)"
              :precision="0" /></el-form-item
          ><el-form-item label="核对依据（至少 10 字，不要填写密码或密钥）"
            ><el-input
              v-model="settlement.evidence"
              type="textarea"
              :rows="3"
              maxlength="1000" /></el-form-item
          ><el-checkbox v-model="settlement.upstreamChecked"
            >我已核查上游退款单与资金流水</el-checkbox
          ></el-form
        ></template
      >
      <template #footer
        ><el-button
          :disabled="settlementLoading"
          @click="settlementOpen = false"
          >取消</el-button
        ><el-button
          type="primary"
          :loading="settlementLoading"
          :disabled="
            !settlement.upstreamChecked ||
            settlement.evidence.trim().length < 10
          "
          @click="previewSettlement"
          >预览退款金额</el-button
        ></template
      >
    </el-dialog>
    <el-dialog
      v-model="resolveOpen"
      title="人工核对 · 会影响订单与余额"
      width="min(560px, 94vw)"
      :close-on-click-modal="false"
    >
      <el-alert
        type="warning"
        title="必须先登录上游核对订单和资金流水。不能只凭超时或错误提示退回余额。"
        :closable="false"
      />
      <el-form v-if="resolveQuote" label-position="top" class="resolve-form"
        ><p>
          {{ actionNames[resolveQuote.action] }} · ¥{{ resolveQuote.amount }} ·
          {{ stateName(resolveQuote.state) }}
        </p>
        <el-form-item label="已核实的上游结果"
          ><el-radio-group v-model="resolution.outcome"
            ><el-radio value="ACCEPTED">已受理</el-radio
            ><el-radio value="NOT_ACCEPTED"
              >未受理，退回本次扣款</el-radio
            ></el-radio-group
          ></el-form-item
        ><el-form-item
          v-if="
            resolution.outcome === 'ACCEPTED' &&
            resolveQuote.action === 'CREATE'
          "
          label="已核实的上游订单号"
          ><el-input
            v-model="resolution.externalOrderNo"
            maxlength="64" /></el-form-item
        ><el-form-item
          v-if="
            resolution.outcome === 'ACCEPTED' &&
            resolveQuote.action === 'REFUND'
          "
          label="实际退回的剩余次数"
          ><el-input-number
            v-model="resolution.refundedUnits"
            :min="0"
            :max="9999"
            :precision="0" /></el-form-item
        ><el-form-item label="核对依据（至少 10 字，不要填写密码或密钥）"
          ><el-input
            v-model="resolution.evidence"
            type="textarea"
            :rows="3"
            maxlength="1000" /></el-form-item
        ><el-checkbox v-model="resolution.upstreamChecked"
          >我已核查上游订单和账务记录，确认上述结论</el-checkbox
        ></el-form
      >
      <template #footer
        ><el-button @click="resolveOpen = false" :disabled="resolving"
          >取消</el-button
        ><el-button
          type="danger"
          :loading="resolving"
          :disabled="
            !resolution.upstreamChecked ||
            resolution.evidence.trim().length < 10
          "
          @click="resolve"
          >确认并记入审计</el-button
        ></template
      >
    </el-dialog>
  </div>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  listServiceOrders,
  syncServiceOrder,
  getServiceOrderEvents,
  previewServiceAction,
  getServiceOperation,
  getAdminServiceOperation,
  resolveServiceOperation,
  getServiceRunLogs,
  getServiceOrderOptions,
  getServiceOrderAudit,
  previewServiceRefundSettlement,
} from "@/api/serviceCommerce";
import {
  serviceNames,
  stateName,
  actionNames,
  moneyText,
  fieldNames,
  editableWuxinPlan,
  maxRefundableUnits,
} from "@/utils/serviceCommerce";
import InternshipPlanFields from "@/components/InternshipPlanFields.vue";
import ServiceNotifications from "@/components/ServiceNotifications.vue";
const notificationOpen = ref(false), notificationOrder = ref(null);
import {
  internshipFields,
  internshipReportTypes,
  internshipCalendarText,
  eligibleInternshipDates,
  internshipReportRangeError,
  pickerCalendarDate,
} from "@/utils/internshipServices";
import WuxinPlanFields from "@/components/WuxinPlanFields.vue";
import ServiceQuoteConfirm from "@/components/ServiceQuoteConfirm.vue";
const route = useRoute(),
  router = useRouter();
const admin = computed(() => !!route.meta.serviceAdmin);
const internshipOpen = ref(false),
  internshipOrder = ref(null),
  internshipForm = ref({}),
  internshipSchedule = ref(null),
  internshipLoading = ref(false);
const reportOpen = ref(false),
  reportOrder = ref(null),
  reportDates = ref([]),
  reportForm = ref({ startDate: "", endDate: "", reportType: "日报" });
const items = ref([]),
  loading = ref(false),
  error = ref(""),
  page = ref(1),
  total = ref(0),
  busyId = ref(null),
  quote = ref(null);
const events = ref([]),
  eventsOpen = ref(false),
  resolveOpen = ref(false),
  resolveQuote = ref(null),
  resolving = ref(false);
const runLogsOpen = ref(false),
  runLogs = ref({ items: [], hasMore: false }),
  runLogOrder = ref(null),
  runLogPage = ref(1),
  runLogsLoading = ref(false);
const planOpen = ref(false),
  planOptions = ref(null),
  planFields = ref({}),
  planOrder = ref(null),
  planLoading = ref(false);
const audit = ref(null),
  auditOpen = ref(false),
  settlementOpen = ref(false),
  settlementOrder = ref(null),
  settlementAudit = ref(null),
  settlementLoading = ref(false);
const settlement = ref({
  refundedUnits: 0,
  evidence: "",
  upstreamChecked: false,
});
const resolution = ref({
  outcome: "NOT_ACCEPTED",
  externalOrderNo: "",
  refundedUnits: 0,
  evidence: "",
  upstreamChecked: false,
});
function tagType(state) {
  return ["COMPLETED", "REFUNDED"].includes(state)
    ? "success"
    : ["CONFIRMING", "ATTENTION", "REFUND_REVIEW"].includes(state)
      ? "warning"
      : "info";
}
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const r = await listServiceOrders(
      { page: page.value, pageSize: 20 },
      admin.value,
    );
    items.value = r.records;
    total.value = r.total;
  } catch {
    error.value = "暂时无法读取服务订单，请确认服务已启用并重试。";
  } finally {
    loading.value = false;
  }
}
function onQuoteResult(result) {
  if (result.state === "SUCCEEDED") {
    // A definitive receipt supersedes a previous lost-response error toast.
    ElMessage.closeAll();
    ElMessage.success(
      result.action === "SETTLE_REFUND"
        ? "退款已入账，可在审计记录中查看。"
        : `${actionNames[result.action] || "操作"}已受理。`,
    );
  }
  load();
}
async function sync(item) {
  if (busyId.value) return;
  busyId.value = item.id;
  try {
    await syncServiceOrder(item.id);
    await load();
  } catch {
  } finally {
    busyId.value = null;
  }
}
async function checkPending(item) {
  busyId.value = item.id;
  try {
    const op = await getServiceOperation(item.pendingOperationId);
    ElMessage({
      type: op.state === "SUCCEEDED" ? "success" : "warning",
      message: stateName(op.state),
    });
    await load();
  } catch {
  } finally {
    busyId.value = null;
  }
}
async function showEvents(item) {
  try {
    events.value = await getServiceOrderEvents(item.id);
    eventsOpen.value = true;
  } catch {}
}
async function prepareAction(item, action) {
  if (busyId.value) return;
  if (action === "EDIT_SCHEDULE") {
    await editInternship(item);
    return;
  }
  if (action === "REPORT") {
    busyId.value = item.id;
    try {
      const options = await getServiceOrderOptions(item.id);
      reportOrder.value = item;
      reportDates.value = eligibleInternshipDates(options);
      if (!reportDates.value.length) {
        ElMessage.info("当前没有可补交的已购服务日");
        return;
      }
      reportForm.value = {
        startDate: "",
        endDate: "",
        reportType: internshipReportTypes(item.project)[0],
      };
      reportOpen.value = true;
    } catch {
    } finally {
      busyId.value = null;
    }
    return;
  }
  if (action === "EDIT_PLAN") {
    await editPlan(item);
    return;
  }
  let quantity = 0;
  if (action === "ADD_TIMES") {
    try {
      const value = await ElMessageBox.prompt(
        "输入增加的次数（1–365）",
        "增加次数",
        {
          inputValue: "1",
          inputPattern: /^(?:[1-9]\d?|[12]\d{2}|3[0-5]\d|36[0-5])$/,
          inputErrorMessage: "请输入 1–365 的整数",
        },
      );
      quantity = Number(value.value);
    } catch {
      return;
    }
  }
  busyId.value = item.id;
  try {
    quote.value = await previewServiceAction(item.id, { action, quantity });
  } catch {
  } finally {
    busyId.value = null;
  }
}
async function showRunLogs(item) {
  if (runLogsLoading.value) return;
  runLogOrder.value = item;
  runLogPage.value = 1;
  await changeLogPage(0);
}
async function changeLogPage(delta) {
  if (runLogsLoading.value) return;
  runLogsLoading.value = true;
  try {
    const result = await getServiceRunLogs(
      runLogOrder.value.id,
      runLogPage.value + delta,
    );
    runLogPage.value += delta;
    runLogs.value = result;
    runLogsOpen.value = true;
  } catch {
  } finally {
    runLogsLoading.value = false;
  }
}
async function delayTask(log) {
  if (busyId.value || !runLogOrder.value) return;
  busyId.value = runLogOrder.value.id;
  try {
    quote.value = await previewServiceAction(runLogOrder.value.id, {
      action: "DELAY_TASK",
      quantity: 0,
      fields: { taskId: log.id, page: String(runLogPage.value) },
    });
    runLogsOpen.value = false;
  } catch {
  } finally {
    busyId.value = null;
  }
}
async function editTaskTime(log) {
  try {
    const result = await ElMessageBox.prompt(
      "填写未来的北京时间，格式 YYYY-MM-DD HH:mm:ss",
      "修改任务时间",
      {
        inputValue: log.time,
        inputPattern: /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/,
        inputErrorMessage: "请按要求填写日期和时间",
      },
    );
    quote.value = await previewServiceAction(runLogOrder.value.id, {
      action: "CHANGE_TIME",
      quantity: 0,
      fields: {
        taskId: log.id,
        time: result.value,
        page: String(runLogPage.value),
      },
    });
  } catch {}
}
async function editInternship(item) {
  if (internshipLoading.value) return;
  internshipLoading.value = true;
  try {
    const options = await getServiceOrderOptions(item.id);
    internshipOrder.value = item;
    internshipForm.value = {
      ...internshipFields(options.suggested, true),
      password: "",
    };
    internshipSchedule.value = options.schedule;
    internshipOpen.value = true;
  } catch {
  } finally {
    internshipLoading.value = false;
  }
}
async function previewInternship() {
  if (internshipLoading.value) return;
  internshipLoading.value = true;
  try {
    quote.value = await previewServiceAction(internshipOrder.value.id, {
      action: "EDIT_SCHEDULE",
      quantity: 0,
      fields: internshipFields(internshipForm.value, true),
      schedule: internshipSchedule.value,
    });
    internshipOpen.value = false;
    internshipForm.value.password = "";
  } catch {
  } finally {
    internshipLoading.value = false;
  }
}
function reportDateDisabled(date) {
  return !reportDates.value.includes(pickerCalendarDate(date));
}
async function previewReport() {
  if (internshipLoading.value) return;
  const message = internshipReportRangeError(
    reportForm.value,
    reportDates.value,
  );
  if (message) {
    ElMessage.warning(message);
    return;
  }
  internshipLoading.value = true;
  try {
    quote.value = await previewServiceAction(reportOrder.value.id, {
      action: "REPORT",
      quantity: 0,
      fields: reportForm.value,
    });
    reportOpen.value = false;
  } catch {
  } finally {
    internshipLoading.value = false;
  }
}
async function editPlan(item) {
  try {
    planOptions.value = await getServiceOrderOptions(item.id);
    planOrder.value = item;
    planFields.value = editableWuxinPlan(
      planOptions.value.suggested,
      item.distance,
    );
    planOpen.value = true;
  } catch {}
}
async function previewPlan() {
  if (planLoading.value) return;
  planLoading.value = true;
  try {
    quote.value = await previewServiceAction(planOrder.value.id, {
      action: "EDIT_PLAN",
      quantity: 0,
      fields: planFields.value,
    });
    planOpen.value = false;
  } catch {
  } finally {
    planLoading.value = false;
  }
}
async function showAudit(item) {
  if (busyId.value) return;
  busyId.value = item.id;
  try {
    audit.value = await getServiceOrderAudit(item.id);
    auditOpen.value = true;
  } catch {
  } finally {
    busyId.value = null;
  }
}
async function openSettlement(item) {
  if (settlementLoading.value || busyId.value) return;
  settlementLoading.value = true;
  busyId.value = item.id;
  try {
    const fresh = await getServiceOrderAudit(item.id);
    if (
      fresh.order.status !== "REFUND_REVIEW" ||
      fresh.order.pendingOperationId
    ) {
      await load();
      return;
    }
    settlementAudit.value = fresh;
    settlementOrder.value = fresh.order;
    settlement.value = {
      refundedUnits:
        fresh.order.completed == null ? 0 : maxRefundableUnits(fresh.order),
      evidence: "",
      upstreamChecked: false,
    };
    settlementOpen.value = true;
  } catch {
  } finally {
    settlementLoading.value = false;
    busyId.value = null;
  }
}
async function previewSettlement() {
  if (settlementLoading.value || !settlementOrder.value) return;
  settlementLoading.value = true;
  try {
    quote.value = await previewServiceRefundSettlement(
      settlementOrder.value.id,
      { ...settlement.value, orderVersion: settlementOrder.value.version },
    );
    settlementOpen.value = false;
  } catch {
  } finally {
    settlementLoading.value = false;
  }
}
async function openResolve(item) {
  if (busyId.value) return;
  busyId.value = item.id;
  try {
    resolveQuote.value = await getAdminServiceOperation(
      item.pendingOperationId,
    );
    resolution.value = {
      outcome: "NOT_ACCEPTED",
      externalOrderNo: "",
      refundedUnits: 0,
      evidence: "",
      upstreamChecked: false,
    };
    resolveOpen.value = true;
  } catch {
  } finally {
    busyId.value = null;
  }
}
async function resolve() {
  if (resolving.value) return;
  resolving.value = true;
  try {
    await resolveServiceOperation(resolveQuote.value.id, resolution.value);
    resolveOpen.value = false;
    ElMessage.success("核对结果已记录");
    await load();
  } catch {
  } finally {
    resolving.value = false;
  }
}
watch(internshipOpen, (open) => {
  if (!open) internshipForm.value.password = "";
});
onBeforeUnmount(() => {
  internshipForm.value.password = "";
});
load();
</script>
<style scoped>
:global(.internship-editor) {
  display: flex;
  flex-direction: column;
  max-height: calc(100dvh - 48px);
  margin: 24px auto !important;
}
:global(.internship-editor .el-dialog__body) {
  overflow: auto;
  min-height: 0;
  flex: 1;
}

.service-orders {
  max-width: 1160px;
  margin: auto;
}
.orders-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 28px;
}
.orders-header > div {
  flex: 1;
}
.orders-header h1 {
  font-size: 28px;
  margin: 8px 0;
}
.orders-header p {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.8;
}
.eyebrow {
  font-size: 11px;
  letter-spacing: 1.6px;
  color: var(--el-color-primary);
  font-weight: 700;
}
.order-list {
  display: grid;
  gap: 18px;
  min-height: 100px;
}
.order-card {
  padding: 24px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  border-radius: 18px;
}
.order-card.focused {
  border-color: var(--el-color-primary);
}
.order-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.order-title small {
  color: var(--el-text-color-secondary);
  font-size: 11px;
}
.order-title h2 {
  font-size: 18px;
  margin: 8px 0 0;
}
.order-metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin: 24px 0;
}
.order-metrics span {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}
.order-metrics strong {
  font-size: 19px;
}
.order-metrics small {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-weight: 400;
}
.order-card footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 22px;
}
.order-number {
  font: 11px monospace;
  overflow-wrap: anywhere;
  color: var(--el-text-color-secondary);
}
.order-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.order-actions .el-button + .el-button {
  margin-left: 0;
}
.order-card .el-alert {
  margin-top: 18px;
}
.unknown-progress {
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
  margin-top: 16px;
}
.resolve-form {
  margin-top: 18px;
}
.audit-evidence {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.7;
}
.resolve-form :deep(.el-checkbox__label) {
  white-space: normal;
  line-height: 1.7;
}
@media (max-width: 600px) {
  .orders-header {
    flex-wrap: wrap;
  }
  .orders-header > div {
    flex-basis: 100%;
  }
  .orders-header h1 {
    font-size: 24px;
  }
  .order-card {
    padding: 18px;
  }
  .order-metrics {
    grid-template-columns: 1fr 1fr;
    gap: 18px;
  }
  .order-actions {
    width: 100%;
  }
  .order-actions .el-button {
    min-height: 44px;
  }
}
</style>
