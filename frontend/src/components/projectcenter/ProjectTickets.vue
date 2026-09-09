<template>
  <section class="project-support" aria-label="项目工单">
    <header class="support-heading">
      <div>
        <span class="eyebrow">SUPPORT / 项目支持</span>
        <h2>{{ admin ? "用户项目工单" : "项目工单" }}</h2>
        <p>
          只展示本平台绑定的工单。补偿审核不等于付款，工单不会自动改变余额。
        </p>
      </div>
      <div class="support-toolbar">
        <el-button :loading="loading" @click="load">刷新工单</el-button
        ><el-button
          v-if="!admin"
          type="primary"
          :disabled="!accounts.length"
          @click="openDraft()"
          >新建工单</el-button
        >
      </div>
    </header>
    <el-alert
      v-if="error"
      :title="error"
      type="warning"
      :closable="false"
      show-icon
    />
    <div class="ticket-grid" v-loading="loading">
      <article v-for="item in rows" :key="item.id" class="support-card">
        <div class="ticket-meta">
          <el-tag size="small" effect="plain">{{
            ticketTypes[item.type]
          }}</el-tag
          ><span>{{ ticketState(item) }}</span>
        </div>
        <button class="ticket-title" @click="inspect(item.id)">
          {{ item.title }}
        </button>
        <p class="ticket-project">
          {{ item.projectTitle
          }}<span v-if="admin"> · 用户 #{{ item.userId }}</span>
        </p>
        <p v-if="item.type === 'compensation'" class="compensation-note">
          申请 {{ item.compensationAmount }} 项目额度 · 不自动兑付
        </p>
        <div class="ticket-footer">
          <span>{{ (item.createdAt || "").replace("T", " ") }}</span
          ><el-button text type="primary" @click="inspect(item.id)"
            >查看工单</el-button
          >
        </div>
      </article>
    </div>
    <el-empty
      v-if="!loading && !rows.length"
      description="暂无本平台工单；不会导入上游其他客户的记录"
    />
    <el-pagination
      v-if="total > 20"
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="prev,pager,next"
      @current-change="load"
    />
    <el-drawer
      v-model="draftOpen"
      title="新建项目工单"
      size="min(550px,100vw)"
      destroy-on-close
      :close-on-click-modal="false"
      :close-on-press-escape="!busy"
      :before-close="closeDraft"
    >
      <el-alert
        title="先保存草稿并预览，再确认发送；本平台不要求在工单中提供密码、验证码或密钥。"
        type="info"
        :closable="false"
      />
      <el-form label-position="top" :disabled="busy || draftAttempted">
        <el-form-item label="本人已开通的项目"
          ><el-select v-model="draft.accountId" placeholder="选择本人项目账户"
            ><el-option
              v-for="item in accounts"
              :key="item.accountId"
              :value="item.accountId"
              :label="item.title" /></el-select
        ></el-form-item>
        <el-form-item label="工单类型"
          ><el-radio-group v-model="draft.type"
            ><el-radio-button
              v-for="(label, value) in ticketTypes"
              :key="value"
              :value="value"
              >{{ label }}</el-radio-button
            ></el-radio-group
          ></el-form-item
        >
        <el-form-item label="工单标题"
          ><el-input
            v-model="draft.title"
            maxlength="120"
            placeholder="用一句话描述需要协助的事项"
        /></el-form-item>
        <el-form-item label="情况说明"
          ><el-input
            v-model="draft.description"
            type="textarea"
            :rows="5"
            maxlength="4000"
            show-word-limit
            placeholder="描述发生时间、现象和已尝试的处理；不要填写任何凭据"
        /></el-form-item>
        <el-form-item
          v-if="draft.type === 'compensation'"
          label="申请补偿的项目额度"
          ><el-input
            v-model="draft.compensationAmount"
            inputmode="decimal"
            maxlength="20"
            placeholder="申请额度，不是人民币到账金额"
        /></el-form-item>
        <p class="support-notice">
          当前仅支持文字工单，暂不上传或打开上游附件。补偿申请不保证获批，获批也不代表已向项目或平台余额入账。
        </p>
        <el-checkbox v-model="draft.confirmedPolicy" class="wrapped-check"
          >确认发送本人项目的问题说明，未包含密码、验证码或密钥</el-checkbox
        >
      </el-form>
      <el-alert
        v-if="draftAttempted"
        title="草稿保存结果尚未确认。请关闭并刷新工单列表检查记录，不要重复保存；尚未自动发往上游。"
        type="warning"
        :closable="false"
      />
      <template #footer
        ><el-button :disabled="busy" @click="draftOpen = false">取消</el-button
        ><el-button
          type="primary"
          :loading="busy"
          :disabled="!draftValid || draftAttempted"
          @click="previewSubmit"
          >预览工单</el-button
        ></template
      >
    </el-drawer>
    <el-drawer
      v-model="detailOpen"
      title="项目工单详情"
      size="min(610px,100vw)"
      destroy-on-close
      :close-on-click-modal="false"
      :close-on-press-escape="!busy"
      :before-close="closeDetail"
    >
      <template v-if="detail"
        ><div class="detail-topline">
          <el-tag
            >{{ ticketTypes[detail.type] }} · {{ ticketState(detail) }}</el-tag
          ><el-button
            :loading="busy"
            :disabled="
              !detail.checkedAt ||
              (!!detail.pendingOperationId && detail.state !== 'UNKNOWN')
            "
            @click="refreshDetail"
            >读取上游最新回复</el-button
          >
        </div>
        <h3>{{ detail.title }}</h3>
        <p class="ticket-project">{{ detail.projectTitle }}</p>
        <p class="ticket-text">{{ detail.description }}</p>
        <div v-if="detail.type === 'compensation'" class="compensation-box">
          <strong>申请 {{ detail.compensationAmount }} 项目额度</strong>
          <p>
            {{
              detail.reviewResult === "approved"
                ? "审核通过"
                : detail.reviewResult === "rejected"
                  ? "审核未通过"
                  : "尚未审核"
            }}
            · 不代表余额已到账
          </p>
          <p v-if="detail.reviewNote" class="ticket-text">
            {{ detail.reviewNote }}
          </p>
        </div>
        <el-alert
          v-if="
            detail.hasAttachment ||
            detail.replies?.some((reply) => reply.hasAttachment)
          "
          title="上游包含附件，当前不会加载外部图片或链接，请通过已验证的支持渠道核实。"
          type="info"
          :closable="false"
        />
        <h4>回复记录</h4>
        <div v-if="!detail.replies?.length" class="empty-replies">
          暂无回复。点击“读取上游最新回复”才会请求上游。
        </div>
        <article
          v-for="reply in detail.replies"
          :key="reply.id"
          class="reply-bubble"
          :class="{ 'reply-customer': reply.sender === 'customer' }"
        >
          <div>
            <strong>{{
              reply.sender === "customer" ? "本人项目账户" : "上游支持"
            }}</strong
            ><time>{{ reply.createdAt }}</time>
          </div>
          <p class="ticket-text">{{ reply.content || "仅附件，当前不显示" }}</p>
        </article>
        <div v-if="detail.pendingOperationId" class="pending-ticket">
          <p>原操作尚未结束，请检查原操作，不要重复发送。</p>
          <el-button
            type="warning"
            plain
            :loading="busy"
            @click="inspectOperation(detail.pendingOperationId)"
            >检查原操作</el-button
          >
        </div>
        <el-form
          v-else-if="!admin && canReplyToTicket(detail)"
          label-position="top"
          :disabled="busy"
          ><el-form-item label="补充回复"
            ><el-input
              v-model="replyText"
              type="textarea"
              :rows="4"
              maxlength="4000"
              placeholder="仅填写需要补充的情况，不填写凭据" /></el-form-item
          ><el-checkbox v-model="replyConsent" class="wrapped-check"
            >确认发送本人项目工单的回复，未包含凭据</el-checkbox
          ><el-button
            type="primary"
            :loading="busy"
            :disabled="!replyConsent || !replyText.trim()"
            @click="previewReply"
            >预览回复</el-button
          ></el-form
        >
        <el-form
          v-else-if="admin && canReviewTicket(detail)"
          label-position="top"
          :disabled="busy"
          ><h4>补偿审核 · 不自动付款</h4>
          <el-radio-group v-model="review.result"
            ><el-radio value="approved">同意申请</el-radio
            ><el-radio value="rejected">拒绝申请</el-radio></el-radio-group
          ><el-form-item label="审核说明"
            ><el-input
              v-model="review.note"
              type="textarea"
              :rows="3"
              maxlength="1000"
              placeholder="至少十个字，说明核实依据与审核结论" /></el-form-item
          ><el-checkbox v-model="review.upstreamChecked" class="wrapped-check"
            >已核实原工单，了解审核通过不会自动向任何账户入账</el-checkbox
          >
          <p class="support-notice">
            须同时具备接口管理与资金核对权限；不能覆盖已经完成的审核。
          </p>
          <el-button
            type="primary"
            :loading="busy"
            :disabled="
              !review.upstreamChecked || review.note.trim().length < 10
            "
            @click="previewReview"
            >预览补偿审核</el-button
          ></el-form
        >
        <p class="ticket-id">本平台工单编号 {{ detail.id }}</p>
      </template>
    </el-drawer>
    <el-dialog
      v-model="operationOpen"
      :title="ticketActions[operation?.action] || '检查工单操作'"
      width="min(540px,calc(100vw - 24px))"
      :close-on-click-modal="false"
      :close-on-press-escape="!busy"
      :show-close="!busy"
    >
      <template v-if="operation"
        ><el-tag
          :type="operation.state === 'SUCCEEDED' ? 'success' : 'warning'"
          >{{ projectState(operation.state) }}</el-tag
        >
        <p v-if="operation.reviewResult" class="support-notice">
          审核结论：{{
            operation.reviewResult === "approved" ? "同意申请" : "拒绝申请"
          }}。不自动付款。
        </p>
        <p class="ticket-text operation-content">{{ operation.content }}</p>
        <ul class="operation-warnings">
          <li v-for="warning in operation.warnings" :key="warning">
            {{ warning }}
          </li>
        </ul>
        <el-form
          v-if="admin && operation.state === 'UNKNOWN'"
          label-position="top"
          :disabled="busy || resolutionAttempts.has(operation.id)"
          ><h4>人工核对原操作</h4>
          <el-radio-group v-model="resolution.outcome"
            ><el-radio value="NOT_ACCEPTED">完全未受理</el-radio
            ><el-radio
              value="ACCEPTED"
              :disabled="!canResolveTicketAccepted(operation)"
              >已受理</el-radio
            ></el-radio-group
          >
          <p v-if="operation.action === 'SUBMIT'" class="support-notice">
            协议缺少可独立验证的客户归属，未知的新工单不能凭编号认领。
          </p>
          <el-form-item label="核对证据"
            ><el-input
              v-model="resolution.evidence"
              type="textarea"
              :rows="3"
              maxlength="1000"
              placeholder="逐项核实原请求的上游受理结果，不凭相似内容猜测" /></el-form-item
          ><el-checkbox
            v-model="resolution.upstreamChecked"
            class="wrapped-check"
            >已逐项核对原请求的真实受理结果</el-checkbox
          ><el-button
            :loading="busy"
            :disabled="
              !resolution.upstreamChecked ||
              resolution.evidence.trim().length < 10
            "
            @click="resolve"
            >保存核对结论</el-button
          ></el-form
        >
        <el-alert
          v-if="resolutionAttempts.has(operation.id)"
          title="核对请求已提交，请只查询原操作结果，不要重复提交。"
          type="warning"
          :closable="false"
        />
        <p class="ticket-id">操作编号 {{ operation.id }}</p></template
      >
      <template #footer
        ><el-button :disabled="busy" @click="operationOpen = false"
          >关闭</el-button
        ><el-button
          v-if="
            operation &&
            !confirmAttempts.has(operation.id) &&
            ticketOperationReady(operation, admin, clock)
          "
          type="primary"
          :loading="busy"
          @click="confirm"
          >{{
            operation.action === "REVIEW"
              ? "确认审核（不自动付款）"
              : "确认发送"
          }}</el-button
        ><el-button
          v-else-if="
            operation &&
            !['SUCCEEDED', 'NOT_ACCEPTED', 'EXPIRED'].includes(operation.state)
          "
          type="primary"
          :loading="busy"
          @click="check"
          >检查提交结果</el-button
        ></template
      >
    </el-dialog>
  </section>
</template>
<script setup>
import { computed, onBeforeUnmount, reactive, ref } from "vue";
import {
  listProjectTickets,
  getProjectTicket,
  refreshProjectTicket,
  prepareProjectTicket,
  prepareProjectReply,
  prepareTicketReview,
  getTicketOperation,
  confirmTicketOperation,
  resolveTicketOperation,
} from "@/api/projectTickets";
import {
  ticketTypes,
  ticketActions,
  ticketState,
  canReplyToTicket,
  canReviewTicket,
  ticketOperationReady,
  canResolveTicketAccepted,
} from "@/utils/projectTickets";
import { projectState } from "@/utils/projectCenter";
const props = defineProps({
  admin: Boolean,
  accounts: { type: Array, default: () => [] },
});
const rows = ref([]),
  total = ref(0),
  page = ref(1),
  loading = ref(false),
  error = ref(""),
  busy = ref(false),
  draftOpen = ref(false),
  detailOpen = ref(false),
  operationOpen = ref(false),
  detail = ref(null),
  operation = ref(null),
  draft = ref({}),
  draftAttempted = ref(false),
  replyText = ref(""),
  replyConsent = ref(false),
  review = ref({ result: "rejected", note: "", upstreamChecked: false }),
  resolution = ref({
    outcome: "NOT_ACCEPTED",
    evidence: "",
    upstreamChecked: false,
  }),
  clock = ref(Date.now());
const confirmAttempts = reactive(new Set()),
  resolutionAttempts = reactive(new Set());
let alive = true,
  generation = 0;
const timer = setInterval(() => (clock.value = Date.now()), 1000);
const draftValid = computed(
  () =>
    draft.value.accountId &&
    draft.value.title?.trim() &&
    draft.value.description?.trim() &&
    draft.value.confirmedPolicy &&
    (draft.value.type !== "compensation" ||
      /^\d{1,6}(?:\.\d{1,6})?$/.test(draft.value.compensationAmount)),
);
async function load() {
  const v = ++generation;
  loading.value = true;
  error.value = "";
  try {
    const data = await listProjectTickets(
      { page: page.value, pageSize: 20 },
      props.admin,
    );
    if (alive && v === generation) {
      rows.value = data.records;
      total.value = data.total;
    }
  } catch {
    if (alive && v === generation)
      error.value =
        "工单读取失败，请检查功能开关、迁移与权限。不会把读取失败显示为没有工单。";
  } finally {
    if (alive && v === generation) loading.value = false;
  }
}
function openDraft(accountId) {
  if (busy.value || props.admin) return;
  draft.value = {
    accountId: accountId || props.accounts[0]?.accountId,
    type: "bug",
    title: "",
    description: "",
    compensationAmount: "",
    confirmedPolicy: false,
  };
  draftAttempted.value = false;
  draftOpen.value = true;
}
function closeDraft(done) {
  if (!busy.value) {
    draft.value = {};
    done();
  }
}
function closeDetail(done) {
  if (!busy.value) {
    replyText.value = "";
    review.value = { result: "rejected", note: "", upstreamChecked: false };
    done();
  }
}
function showOperation(data) {
  operation.value = data;
  resolution.value = {
    outcome: "NOT_ACCEPTED",
    evidence: "",
    upstreamChecked: false,
  };
  detailOpen.value = false;
  draftOpen.value = false;
  operationOpen.value = true;
}
async function previewSubmit() {
  if (busy.value || draftAttempted.value || !draftValid.value) return;
  busy.value = true;
  draftAttempted.value = true;
  try {
    const data = await prepareProjectTicket(draft.value.accountId, {
      type: draft.value.type,
      title: draft.value.title,
      description: draft.value.description,
      compensationAmount:
        draft.value.type === "compensation"
          ? draft.value.compensationAmount
          : "0",
      confirmedPolicy: true,
    });
    if (alive) {
      showOperation(data);
      draft.value = {};
      await load();
    }
  } catch {
  } finally {
    busy.value = false;
  }
}
async function inspect(id) {
  if (busy.value) return;
  busy.value = true;
  try {
    const data = await getProjectTicket(id, props.admin);
    if (alive) {
      detail.value = data;
      replyText.value = "";
      replyConsent.value = false;
      review.value = { result: "rejected", note: "", upstreamChecked: false };
      detailOpen.value = true;
    }
  } catch {
  } finally {
    busy.value = false;
  }
}
async function refreshDetail() {
  if (busy.value || !detail.value) return;
  busy.value = true;
  try {
    const data = await refreshProjectTicket(detail.value.id, props.admin);
    if (alive) {
      detail.value = data;
      await load();
    }
  } catch {
  } finally {
    busy.value = false;
  }
}
async function previewReply() {
  if (
    busy.value ||
    !replyConsent.value ||
    !replyText.value.trim() ||
    !canReplyToTicket(detail.value)
  )
    return;
  busy.value = true;
  try {
    const data = await prepareProjectReply(detail.value.id, {
      content: replyText.value,
      version: detail.value.version,
      confirmedPolicy: true,
    });
    if (alive) {
      showOperation(data);
      replyText.value = "";
      await load();
    }
  } catch {
  } finally {
    busy.value = false;
  }
}
async function previewReview() {
  if (
    busy.value ||
    !review.value.upstreamChecked ||
    !canReviewTicket(detail.value)
  )
    return;
  busy.value = true;
  try {
    const data = await prepareTicketReview(detail.value.id, {
      ...review.value,
      version: detail.value.version,
    });
    if (alive) {
      showOperation(data);
      await load();
    }
  } catch {
  } finally {
    busy.value = false;
  }
}
async function inspectOperation(id) {
  if (busy.value) return;
  busy.value = true;
  try {
    const data = await getTicketOperation(id, props.admin);
    if (alive) showOperation(data);
  } catch {
  } finally {
    busy.value = false;
  }
}
async function confirm() {
  if (
    busy.value ||
    !ticketOperationReady(operation.value, props.admin) ||
    confirmAttempts.has(operation.value.id)
  )
    return;
  busy.value = true;
  confirmAttempts.add(operation.value.id);
  try {
    const data = await confirmTicketOperation(operation.value.id, props.admin);
    if (alive) {
      operation.value = data;
      await load();
    }
  } catch {
    if (alive)
      operation.value = {
        ...operation.value,
        state: "NETWORK_UNKNOWN",
        warnings: [
          "发送结果尚未确认。请查询同一操作，不要重发或创建相同工单。",
        ],
      };
  } finally {
    busy.value = false;
  }
}
async function check() {
  if (busy.value || !operation.value) return;
  busy.value = true;
  try {
    const data = await getTicketOperation(operation.value.id, props.admin);
    if (alive) {
      operation.value = data;
      await load();
    }
  } catch {
  } finally {
    busy.value = false;
  }
}
async function resolve() {
  if (
    busy.value ||
    !operation.value ||
    resolutionAttempts.has(operation.value.id) ||
    !resolution.value.upstreamChecked
  )
    return;
  busy.value = true;
  resolutionAttempts.add(operation.value.id);
  try {
    const data = await resolveTicketOperation(
      operation.value.id,
      resolution.value,
    );
    if (alive) {
      operation.value = data;
      await load();
    }
  } catch {
  } finally {
    busy.value = false;
  }
}
load();
defineExpose({ openDraft });
onBeforeUnmount(() => {
  alive = false;
  generation++;
  clearInterval(timer);
  draft.value = {};
  replyText.value = "";
});
</script>
<style scoped>
.project-support {
  margin-top: 32px;
  border-top: 1px solid var(--el-border-color-light);
  padding-top: 28px;
  min-width: 0;
}
.support-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin-bottom: 20px;
}
.eyebrow {
  font-size: 11px;
  letter-spacing: 1.5px;
  color: var(--el-color-primary);
  font-weight: 650;
}
h2 {
  font-size: 22px;
  margin: 8px 0;
}
h3 {
  font-size: 21px;
  overflow-wrap: anywhere;
}
.support-heading p,
.ticket-project,
.support-notice,
.compensation-note {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.8;
}
.support-toolbar,
.detail-topline {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.ticket-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 300px), 1fr));
  gap: 18px;
}
.support-card {
  padding: 20px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  background: var(--el-bg-color);
  min-width: 0;
}
.ticket-meta,
.ticket-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--el-text-color-secondary);
  font-size: 11px;
}
.ticket-title {
  font: inherit;
  text-align: left;
  color: var(--el-text-color-primary);
  background: none;
  border: 0;
  padding: 14px 0 0;
  font-size: 17px;
  font-weight: 650;
  cursor: pointer;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.ticket-title:hover {
  color: var(--el-color-primary);
}
.ticket-footer {
  margin-top: 18px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.ticket-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 14px;
  line-height: 1.85;
  color: var(--el-text-color-primary);
}
.compensation-box {
  background: var(--el-fill-color-light);
  padding: 16px;
  border-radius: 10px;
  font-size: 13px;
  margin: 20px 0;
}
.compensation-box p {
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
.reply-bubble {
  padding: 14px 16px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  margin: 12px 0;
}
.reply-customer {
  border-left: 3px solid var(--el-color-primary);
}
.reply-bubble > div {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: 11px;
  flex-wrap: wrap;
}
.reply-bubble time,
.empty-replies,
.ticket-id {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.pending-ticket {
  margin: 20px 0;
  padding: 14px;
  border: 1px solid var(--el-color-warning-light-7);
  background: var(--el-color-warning-light-9);
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.6;
}
.operation-warnings {
  font-size: 12px;
  line-height: 1.9;
  padding-left: 20px;
  color: var(--el-text-color-secondary);
}
.operation-content {
  max-height: 220px;
  overflow: auto;
}
.wrapped-check {
  height: auto;
  min-height: 44px;
  align-items: flex-start;
  margin: 14px 0;
  width: 100%;
}
.wrapped-check :deep(.el-checkbox__label) {
  white-space: normal;
  line-height: 1.7;
}
.wrapped-check :deep(.el-checkbox__input) {
  margin-top: 5px;
}
.project-support :deep(.el-form) {
  margin-top: 20px;
}
.project-support :deep(.el-select) {
  width: 100%;
}
.project-support :deep(.el-button) {
  min-height: 44px;
}
.project-support :deep(.el-button + .el-button) {
  margin-left: 0;
}
.project-support :deep(.el-radio-group) {
  flex-wrap: wrap;
  gap: 6px;
}
.project-support :deep(.el-radio) {
  margin-right: 10px;
  white-space: normal;
  height: auto;
  min-height: 36px;
}
.project-support :deep(.el-alert__title) {
  overflow-wrap: anywhere;
}
.ticket-id {
  margin-top: 24px;
}
.project-support :deep(.el-pagination) {
  justify-content: center;
  margin: 20px 0;
}
@media (max-width: 600px) {
  .support-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }
  .support-toolbar {
    width: 100%;
  }
  .support-toolbar > .el-button {
    flex: 1;
  }
  .support-card {
    padding: 16px;
  }
  .ticket-footer {
    flex-wrap: wrap;
  }
  .project-support :deep(.el-drawer__body) {
    padding: 16px;
  }
  .detail-topline {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
