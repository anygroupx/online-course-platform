<template>
  <main class="project-clients">
    <header class="page-heading">
      <div>
        <span class="eyebrow">SYYV5 / 客户管理</span>
        <h1>一个项目，服务多位客户。</h1>
        <p>
          为你管理的客户建立独立额度账户。开户、充值和转回都有完整操作记录。
        </p>
      </div>
      <div class="actions">
        <el-button :disabled="busy" @click="usageVisible = true">用量与统计</el-button>
        <el-button @click="openTickets(null)">客户售后工单</el-button>
        <el-button @click="openKeys('OWNER')">项目 OpenAPI 密钥</el-button
        ><el-button type="primary" :disabled="busy" @click="beginOpen"
          >新建客户</el-button
        >
      </div>
    </header>
    <el-alert
      title="这是独立的客户额度账户；充值只增加客户可用额度，不代表已购买或执行项目服务。"
      type="info"
      :closable="false"
      show-icon
    />
    <el-alert
      v-if="error"
      :title="error"
      type="warning"
      :closable="false"
      show-icon
    />
    <div v-if="stats" class="stats">
      <article>
        <span>客户</span><b>{{ stats.customers }}</b
        ><small>{{ stats.active }} 位可用</small>
      </article>
      <article>
        <span>累计充值</span><b>¥{{ stats.totalDebited }}</b
        ><small>仅已结算操作</small>
      </article>
      <article>
        <span>已转回平台</span><b>¥{{ stats.totalReturned }}</b
        ><small>不计入充值奖励</small>
      </article>
    </div>
    <section v-loading="loading">
      <div class="section-heading">
        <h2>我的客户</h2>
        <el-button :disabled="busy" :loading="loading" @click="loadClients"
          >刷新客户</el-button
        >
      </div>
      <el-empty
        v-if="!loading && !clients.length && !error"
        description="还没有客户。新建时可以选择零额度开户。"
      />
      <div class="client-grid">
        <article v-for="client in clients" :key="client.id" class="client-card">
          <header>
            <strong>{{ client.label }}</strong
            ><el-tag :type="client.status === 'ACTIVE' ? 'success' : 'info'">{{
              clientState(client.status)
            }}</el-tag>
          </header>
          <p class="muted">{{ client.projectTitle }}</p>
          <div class="balance">
            <b>{{ client.balance }}</b
            ><span>客户额度</span>
          </div>
          <dl>
            <div>
              <dt>冻结单价</dt>
              <dd>¥{{ client.unitPrice }} / 单位</dd>
            </div>
            <div>
              <dt>剩余实付可退预算</dt>
              <dd>¥{{ client.refundBudget }}</dd>
            </div>
          </dl>
          <p class="client-id">客户 {{ client.id }}</p>
          <div class="actions">
            <el-button :disabled="busy" @click="openTickets(client)">客户售后</el-button>
            <el-button
              :disabled="busy || !clientActionAllowed(client, 'TOP_UP')"
              @click="begin(client, 'TOP_UP')"
              >充值额度</el-button
            ><el-button
              :disabled="busy || !clientActionAllowed(client, 'WITHDRAW')"
              @click="begin(client, 'WITHDRAW')"
              >转回平台</el-button
            ><el-button
              :disabled="busy || client.status === 'CLOSED'"
              @click="openKeys(client.id)"
              >客户 API 密钥</el-button
            ><el-button
              :disabled="busy || client.status === 'CLOSED'"
              @click="
                changeStatus(
                  client,
                  client.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE',
                )
              "
              >{{
                client.status === "ACTIVE" ? "暂停客户" : "恢复客户"
              }}</el-button
            ><el-button
              :disabled="busy || client.status === 'CLOSED'"
              @click="changeStatus(client, 'CLOSED')"
              >关闭客户</el-button
            >
          </div>
        </article>
      </div>
      <el-pagination
        v-if="total > 20"
        v-model:current-page="page"
        :total="total"
        :page-size="20"
        layout="prev,pager,next"
        @current-change="loadClients"
      />
    </section>
    <section class="history">
      <div class="section-heading">
        <h2>资金操作</h2>
        <div class="actions">
          <el-button :disabled="busy" @click="loadHistory"
            >刷新操作记录</el-button
          ><el-button @click="readCalls">查看 OpenAPI 调用记录</el-button>
        </div>
      </div>
      <el-alert
        v-if="historyError"
        title="操作记录读取失败，不代表没有记录。请稍后重试。"
        type="warning"
        :closable="false"
      />
      <article v-for="op in records" :key="op.id" class="history-row">
        <div>
          <strong>{{ clientActions[op.action] }} · {{ op.label }}</strong
          ><span class="muted"
            >{{ op.projectTitle }} · {{ op.units }} 额度</span
          >
        </div>
        <b>¥{{ op.amount }}</b
        ><el-button text @click="inspect(op.id)"
          >{{ clientState(op.state) }} · 查看</el-button
        >
      </article>
      <p v-if="!records.length && !historyError" class="muted">
        还没有资金操作
      </p>
      <el-pagination
        v-if="recordTotal > 20"
        v-model:current-page="recordPage"
        :total="recordTotal"
        :page-size="20"
        layout="prev,pager,next"
        @current-change="loadHistory"
      />
    </section>
    <el-dialog
      v-model="dialog"
      :title="clientActions[action] || '资金操作'"
      width="min(620px,calc(100vw - 24px))"
      :close-on-click-modal="false"
      :close-on-press-escape="!busy"
      :before-close="beforeClose"
      destroy-on-close
    >
      <el-alert
        v-if="dialogError"
        :title="dialogError"
        type="warning"
        :closable="false"
        show-icon
      />
      <template v-if="!operation">
        <el-form label-position="top" :disabled="busy || previewUnknown"
          ><el-form-item v-if="action === 'OPEN'" label="服务项目"
            ><el-select
              v-model="projectId"
              aria-label="服务项目"
              placeholder="请选择已开放项目"
              ><el-option
                v-for="p in catalog"
                :key="p.id"
                :label="p.title"
                :value="p.id"
                :disabled="!p.available" /></el-select
            ><el-button
              v-if="catalogTotal > catalog.length"
              text
              :loading="catalogLoading"
              @click="loadCatalog(true)"
              >读取更多项目</el-button
            ></el-form-item
          ><el-form-item v-else label="客户"
            ><strong
              >{{ selected?.label }} · {{ selected?.projectTitle }}</strong
            ></el-form-item
          ><el-form-item v-if="action === 'OPEN'" label="客户别名"
            ><el-input
              v-model="label"
              maxlength="100"
              placeholder="填写不含密码等敏感信息的客户别名" /></el-form-item
          ><el-form-item
            :label="
              action === 'OPEN' ? '初始额度（可为 0）' : '客户额度数量'
            "
            ><el-input
              v-model="units"
              inputmode="decimal"
              maxlength="14"
              placeholder="最多6位小数"
            /><el-button
              v-if="action === 'WITHDRAW'"
              text
              @click="units = selected.balance"
              >使用全部可转回额度</el-button
            ></el-form-item
          ><el-checkbox v-model="consent" class="consent"
            >我已确认客户和用途，知晓此操作只处理客户额度</el-checkbox
          ></el-form
        >
        <p class="muted">
          零额度开户不扣款。初始充值与客户创建在同一事务内完成，不会先扣费后留下一条失败客户。
        </p>
        <p v-if="requestId" class="muted">本次请求编号 {{ requestId }}</p>
      </template>
      <template v-else
        ><div class="quote-title">
          <strong>{{ operation.label }}</strong
          ><el-tag :type="operation.state === 'APPLIED' ? 'success' : 'info'">{{
            clientState(operation.state)
          }}</el-tag>
        </div>
        <dl class="quote-details">
          <div>
            <dt>服务项目</dt>
            <dd>{{ operation.projectTitle }}</dd>
          </div>
          <div>
            <dt>操作</dt>
            <dd>{{ clientActions[operation.action] }}</dd>
          </div>
          <div>
            <dt>客户额度</dt>
            <dd>{{ operation.units }}</dd>
          </div>
          <div>
            <dt>冻结单价</dt>
            <dd>¥{{ operation.unitPrice }}</dd>
          </div>
          <div>
            <dt>
              {{
                operation.action === "WITHDRAW"
                  ? "转回平台钱包"
                  : "从平台钱包扣除"
              }}
            </dt>
            <dd class="amount">¥{{ operation.amount }}</dd>
          </div>
          <div v-if="operation.clientBalanceAfter !== null">
            <dt>结算后的客户额度</dt>
            <dd>{{ operation.clientBalanceAfter }}</dd>
          </div>
        </dl>
        <el-alert
          :title="operation.notice"
          :type="operation.state === 'APPLIED' ? 'success' : 'info'"
          :closable="false"
          show-icon
        />
        <p class="muted">
          操作号 {{ operation.id }}<br />预览截止 {{ operation.expiresAt }}
        </p>
        <el-checkbox
          v-if="operation.state === 'READY' && !confirmAttempted"
          v-model="confirmConsent"
          class="consent"
          >我已核对客户、数量及平台金额，确认这一次结算</el-checkbox
        ></template
      >
      <template #footer
        ><el-button :disabled="busy" @click="dialog = false">关闭</el-button
        ><el-button
          v-if="!operation && !previewUnknown"
          type="primary"
          :disabled="!canPreview"
          :loading="busy"
          @click="preview"
          >预览金额（不扣款）</el-button
        ><el-button
          v-if="previewUnknown || operation"
          :loading="busy"
          @click="recover"
          >检查原操作结果</el-button
        ><el-button
          v-if="operation?.state === 'READY' && !confirmAttempted"
          type="primary"
          :disabled="!confirmConsent"
          :loading="busy"
          @click="confirm"
          >确认本次结算</el-button
        ></template
      >
    </el-dialog>
    <el-dialog v-model="usageVisible" title="项目用量与统计" width="min(1000px,calc(100vw - 24px))" destroy-on-close>
      <ProjectUsage v-if="usageVisible" />
    </el-dialog>
    <ProjectKeys v-model="keysVisible" :subject="keySubject" />
    <ProjectClientTickets v-model="ticketsVisible" :client="ticketClient" />
    <el-dialog
      v-model="callsVisible"
      title="项目 OpenAPI 调用记录"
      width="min(640px,calc(100vw - 24px))"
      ><p class="muted">
        只保存安全动作和结果，不记录密钥或请求正文；统计记录失败不会改变原资金操作结果。
      </p>
      <el-alert
        v-if="callsError"
        title="调用记录读取失败，请重试。"
        type="warning"
        :closable="false"
      />
      <div v-for="(call, i) in calls" :key="i" class="history-row">
        <strong>{{ call.action }}</strong
        ><span>{{ call.outcome === "OK" ? "成功" : "未完成" }}</span
        ><time>{{ call.createdAt }}</time>
      </div>
      <el-pagination
        v-if="callsTotal > 20"
        v-model:current-page="callsPage"
        :total="callsTotal"
        :page-size="20"
        layout="prev,pager,next"
        @current-change="readCalls"
      /><template #footer
        ><el-button @click="readCalls">刷新调用记录</el-button></template
      ></el-dialog
    >
  </main>
</template>
<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from "vue";
import { ElMessageBox } from "element-plus";
import ProjectClientTickets from "@/components/projectclient/ProjectClientTickets.vue";
import ProjectKeys from "@/components/projectclient/ProjectKeys.vue";
import ProjectUsage from "@/components/projectcenter/ProjectUsage.vue";
import {
  clientCatalog,
  listProjectClients,
  clientStats,
  quoteProjectClient,
  clientOperation,
  clientOperationByRequest,
  clientOperations,
  confirmProjectClient,
  changeClientStatus,
  projectApiCalls,
} from "@/api/projectClients";
import {
  clientState,
  clientActions,
  clientUnitsValid,
  clientActionAllowed,
} from "@/utils/projectClients";
const usageVisible = ref(false);
const clients = ref([]),
  page = ref(1),
  total = ref(0),
  stats = ref(null),
  loading = ref(false),
  error = ref(""),
  busy = ref(false),
  records = ref([]),
  recordPage = ref(1),
  recordTotal = ref(0),
  historyError = ref(false);
const dialog = ref(false),
  dialogError = ref(""),
  action = ref("OPEN"),
  selected = ref(null),
  projectId = ref(null),
  label = ref(""),
  units = ref("0"),
  consent = ref(false),
  confirmConsent = ref(false),
  operation = ref(null),
  requestId = ref(null),
  previewUnknown = ref(false),
  confirmAttempted = ref(false);
const catalog = ref([]),
  catalogPage = ref(1),
  catalogTotal = ref(0),
  catalogLoading = ref(false),
  keysVisible = ref(false),
  ticketsVisible = ref(false),
  ticketClient = ref(null),
  keySubject = ref("OWNER"),
  callsVisible = ref(false),
  calls = ref([]),
  callsPage = ref(1),
  callsTotal = ref(0),
  callsError = ref(false);
let alive = true,
  generation = 0,
  listSequence = 0,
  historySequence = 0,
  announced = null;
const current = (v) => alive && generation === v;
const canPreview = computed(
  () =>
    consent.value &&
    clientUnitsValid(units.value, action.value === "OPEN") &&
    (action.value !== "OPEN" || (projectId.value && label.value.trim())),
);
async function loadClients() {
  const seq = ++listSequence;
  loading.value = true;
  error.value = "";
  try {
    const data = await listProjectClients({ page: page.value, pageSize: 20 });
    if (alive && seq === listSequence) {
      clients.value = data.records;
      total.value = data.total;
    }
    const summary = await clientStats();
    if (alive && seq === listSequence) stats.value = summary;
  } catch {
    if (alive && seq === listSequence)
      error.value =
        "客户资料读取失败，请检查功能、权限与迁移状态，不代表客户不存在。";
  } finally {
    if (alive && seq === listSequence) loading.value = false;
  }
}
async function loadHistory() {
  const seq = ++historySequence;
  historyError.value = false;
  try {
    const data = await clientOperations({
      page: recordPage.value,
      pageSize: 20,
    });
    if (alive && seq === historySequence) {
      records.value = data.records;
      recordTotal.value = data.total;
    }
  } catch {
    if (alive && seq === historySequence) historyError.value = true;
  }
}
async function loadCatalog(more = false) {
  if (catalogLoading.value) return;
  const v = generation;
  catalogLoading.value = true;
  try {
    const next = more ? catalogPage.value + 1 : 1;
    const data = await clientCatalog({ page: next, pageSize: 50 });
    if (current(v)) {
      catalog.value = more ? [...catalog.value, ...data.records] : data.records;
      catalogPage.value = next;
      catalogTotal.value = data.total;
    }
  } catch {
    if (current(v))
      dialogError.value =
        "项目目录读取失败，请关闭后重试，不使用过期项目快照。";
  } finally {
    if (current(v)) catalogLoading.value = false;
  }
}
function reset(act, row = null) {
  if (busy.value) return false;
  generation++;
  action.value = act;
  selected.value = row;
  projectId.value = row?.projectId || null;
  label.value = "";
  units.value = act === "OPEN" ? "0" : "";
  consent.value = false;
  confirmConsent.value = false;
  operation.value = null;
  requestId.value = null;
  previewUnknown.value = false;
  confirmAttempted.value = false;
  dialogError.value = "";
  catalogLoading.value = false;
  announced = null;
  dialog.value = true;
  return true;
}
function beginOpen() {
  if (reset("OPEN")) {
    catalog.value = [];
    catalogTotal.value = 0;
    loadCatalog();
  }
}
function begin(row, act) {
  reset(act, row);
}
async function preview() {
  if (busy.value || previewUnknown.value || !canPreview.value) return;
  const v = generation;
  requestId.value = crypto.randomUUID();
  const data = {
    requestId: requestId.value,
    action: action.value,
    projectId: projectId.value,
    clientId: selected.value?.id || null,
    label: action.value === "OPEN" ? label.value.trim() : null,
    units: units.value,
    consent: true,
  };
  busy.value = true;
  previewUnknown.value = true;
  try {
    const result = await quoteProjectClient(data);
    if (current(v)) {
      show(result);
      previewUnknown.value = false;
      dialogError.value = "";
    }
  } catch {
    if (current(v))
      dialogError.value =
        "预览请求结果尚未确认，请用原请求编号检查结果。没有自动确认或重建客户。";
  } finally {
    if (current(v)) busy.value = false;
  }
}
function show(data) {
  operation.value = data;
  action.value = data.action;
  if (data.state === "APPLIED" && announced !== data.id) {
    announced = data.id;
    loadClients();
    loadHistory();
  }
}
async function confirm() {
  if (
    busy.value ||
    confirmAttempted.value ||
    !confirmConsent.value ||
    operation.value?.state !== "READY"
  )
    return;
  const v = generation,
    id = operation.value.id;
  busy.value = true;
  confirmAttempted.value = true;
  dialogError.value = "";
  try {
    const result = await confirmProjectClient(id);
    if (current(v)) show(result);
  } catch {
    if (current(v))
      dialogError.value =
        "结算请求结果尚未确认，请查询原操作；不自动重复扣款或开户。";
  } finally {
    if (current(v)) busy.value = false;
  }
}
async function recover() {
  if (busy.value || (!operation.value && !requestId.value)) return;
  const v = generation;
  busy.value = true;
  try {
    const result = operation.value
      ? await clientOperation(operation.value.id)
      : await clientOperationByRequest(requestId.value);
    if (current(v)) {
      show(result);
      previewUnknown.value = false;
      confirmAttempted.value = false;
      confirmConsent.value = false;
      dialogError.value =
        result.state === "READY"
          ? "原操作仍待确认。重新核对后只能显式确认这个原操作，不新建请求。"
          : "";
    }
  } catch {
    if (current(v))
      dialogError.value = "原操作结果尚未读到，请稍后继续查询，不自动重提。";
  } finally {
    if (current(v)) busy.value = false;
  }
}
async function inspect(id) {
  if (!reset("OPEN")) return;
  const v = generation;
  busy.value = true;
  try {
    const result = await clientOperation(id);
    if (current(v)) show(result);
  } catch {
    if (current(v)) dialogError.value = "操作读取失败，请从记录中重新查看。";
  } finally {
    if (current(v)) busy.value = false;
  }
}
function beforeClose(done) {
  if (!busy.value) done();
}
function openTickets(client) {
  ticketClient.value = client;
  ticketsVisible.value = true;
}
function openKeys(subject) {
  keySubject.value = subject;
  keysVisible.value = true;
}
async function changeStatus(row, next) {
  if (busy.value) return;
  const v = generation,
    id = row.id,
    version = row.version;
  busy.value = true;
  try {
    await ElMessageBox.confirm(
      next === "CLOSED"
        ? "仅在客户额度和可退预算都为零时关闭；客户密钥失效，不能恢复。"
        : next === "ACTIVE"
          ? "恢复客户后，可重新充值并使用其未过期的只读密钥；不会自动转移资金。"
          : "暂停将阻止此客户充值及密钥访问，不改变余额；已有客户额度仍可转回。",
      next === "CLOSED"
        ? "关闭客户"
        : next === "ACTIVE"
          ? "恢复客户"
          : "暂停客户",
      {
        confirmButtonText: "确认变更",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
    if (!current(v)) return;
    await changeClientStatus(id, { version, status: next, consent: true });
    if (current(v)) await loadClients();
  } catch (e) {
    if (current(v) && e !== "cancel" && e !== "close") {
      await loadClients();
      error.value =
        "客户状态变更结果未确认，请核对已刷新的客户状态，不自动重试。";
    }
  } finally {
    if (current(v)) busy.value = false;
  }
}
async function readCalls() {
  callsVisible.value = true;
  callsError.value = false;
  try {
    const data = await projectApiCalls({ page: callsPage.value, pageSize: 20 });
    if (alive) {
      calls.value = data.records;
      callsTotal.value = data.total;
    }
  } catch {
    if (alive) callsError.value = true;
  }
}
onMounted(() => {
  loadClients();
  loadHistory();
});
onBeforeUnmount(() => {
  alive = false;
  generation++;
});
</script>
<style scoped>
.project-clients {
  max-width: 1250px;
  margin: auto;
  padding: 24px;
}
.eyebrow {
  font-size: 11px;
  letter-spacing: 1.5px;
  color: var(--el-color-primary);
  font-weight: 650;
}
.page-heading,
.section-heading,
.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.actions {
  justify-content: flex-start;
  flex-wrap: wrap;
}
.page-heading {
  align-items: flex-start;
  margin-bottom: 22px;
}
h1 {
  font-size: 27px;
  margin: 8px 0;
}
h2 {
  font-size: 18px;
}
p,
.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.9;
}
.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
  margin: 24px 0;
}
.stats article,
.client-card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  background: var(--el-bg-color);
  padding: 22px;
}
.stats article > * {
  display: block;
}
.stats span,
.stats small {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.stats b {
  font-size: 25px;
  margin: 8px 0;
}
.client-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  margin: 16px 0;
}
.client-card header,
.quote-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}
.balance {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 20px 0;
}
.balance b {
  font-size: 25px;
}
.balance span {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
dl {
  margin: 16px 0;
}
dl > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  margin: 12px 0;
}
dt {
  color: var(--el-text-color-secondary);
}
dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.client-id {
  overflow-wrap: anywhere;
  font-size: 11px;
}
.history {
  margin-top: 34px;
  border-top: 1px solid var(--el-border-color-light);
  padding-top: 22px;
}
.history-row {
  display: flex;
  gap: 12px;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 12px;
}
.history-row > div > span {
  display: block;
}
.quote-title {
  margin: 12px 0 18px;
}
.quote-details > div {
  font-size: 14px;
}
.quote-details .amount {
  font-size: 24px;
  color: var(--el-color-primary);
  font-weight: 650;
}
.consent {
  height: auto;
  min-height: 44px;
  margin: 16px 0;
}
.consent :deep(.el-checkbox__label) {
  white-space: normal;
  line-height: 1.8;
}
:deep(.el-button) {
  min-height: 44px;
  margin-left: 0;
}
:deep(.el-alert) {
  margin: 12px 0;
}
:deep(.el-dialog__footer) {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
:deep(.el-pagination) {
  margin-top: 16px;
  justify-content: center;
}
@media (max-width: 700px) {
  .project-clients {
    padding: 16px 12px;
  }
  .page-heading,
  .section-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .stats {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .stats article {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 14px;
  }
  .stats b {
    font-size: 21px;
    margin: 0;
  }
  .stats small {
    margin-left: auto;
  }
  .client-grid {
    grid-template-columns: 1fr;
  }
  .client-card {
    padding: 18px;
  }
  .history-row {
    flex-wrap: wrap;
  }
  .history-row > div {
    width: 100%;
  }
  h1 {
    font-size: 24px;
  }
  .actions {
    gap: 8px;
  }
  .actions .el-button {
    flex: 1;
  }
}
</style>
