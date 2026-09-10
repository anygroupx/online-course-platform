<template>
  <main class="project-center">
    <header class="project-header">
      <div>
        <span class="eyebrow">PROJECTS / 项目中心</span>
        <h1>每个项目，独立一份额度。</h1>
        <p>开通专属账户，可选择同时充值初始额度。先预览、后确认，费用和记录清晰可查。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新项目</el-button>
    </header>
    <el-alert
      class="project-scope-note"
      title="当前管理项目额度与工单，不代表已购买项目服务；安全项目业务入口尚未开放。"
      type="info"
      show-icon
      :closable="false"
    />
    <el-alert
      v-if="error"
      :title="error"
      type="warning"
      show-icon
      :closable="false"
    />
    <div class="project-grid" v-loading="loading">
      <article
        v-for="project in projects"
        :key="project.id"
        class="project-card"
      >
        <div class="card-heading">
          <span class="project-marker">SYYV5</span
          ><el-tag
            :type="project.account?.state === 'ACTIVE' ? 'success' : 'info'"
            >{{
              project.account
                ? projectState(project.account.state)
                : project.available
                  ? "可开通"
                  : "暂不可用"
            }}</el-tag
          >
        </div>
        <h2>{{ project.title }}</h2>
        <p class="description">
          {{
            project.description || "此项目的额度独立计算，不与其他项目共用。"
          }}
        </p>
        <div class="balance-band">
          <span>最近确认余额</span
          ><strong
            >{{ projectAmount(project.account?.remoteBalance) }}
            <small>额度</small></strong
          >
          <p>
            {{
              project.account?.balanceCheckedAt
                ? `${project.account.balanceCheckedAt.replace("T", " ")} 更新`
                : "尚未读取余额；只有确认充值后才扣款"
            }}
          </p>
        </div>
        <dl class="rate-lines">
          <div>
            <dt>{{ project.account ? "本账户冻结费率" : "开通时费率" }}</dt>
            <dd>
              ¥{{
                projectAmount(project.account?.unitPrice || project.unitPrice)
              }}
              / 额度
            </dd>
          </div>
          <div>
            <dt>未退充值额度</dt>
            <dd>{{ projectAmount(project.account?.refundableUnits) }}</dd>
          </div>
        </dl>
        <el-alert
          v-if="project.account?.pendingOperationId"
          title="原操作尚未核实，请检查结果。不要重复充值或转回。"
          type="warning"
          :closable="false"
        />
        <div class="project-actions">
          <el-button
            v-if="!project.account || project.account.state === 'NEW'"
            type="primary"
            :disabled="!projectActionAllowed(project, 'PROVISION')"
            @click="begin(project, 'PROVISION')"
            >开通项目账户</el-button
          >
          <template v-else
            ><el-button
              type="primary"
              :disabled="!projectActionAllowed(project, 'TOP_UP')"
              @click="begin(project, 'TOP_UP')"
              >充值额度</el-button
            ><el-button
              :disabled="!projectActionAllowed(project, 'WITHDRAW')"
              @click="begin(project, 'WITHDRAW')"
              >转回余额</el-button
            ><el-button
              text
              :loading="refreshing === project.account.id"
              :disabled="
                !['ACTIVE', 'DISABLED'].includes(project.account.state)
              "
              @click="refreshBalance(project)"
              >更新账户余额</el-button
            ></template
          >
          <el-button
            v-if="project.account?.ticketsAvailable"
            text
            @click="ticketPanel?.openDraft(project.account.id)"
            >工单与支持</el-button
          >
          <el-button
            v-if="project.account?.pendingOperationId"
            type="warning"
            plain
            @click="inspect(project.account.pendingOperationId)"
            >检查原操作</el-button
          >
        </div>
      </article>
    </div>
    <el-empty
      v-if="!loading && !projects.length"
      description="暂无已开放项目，请联系管理员发布"
    />
    <el-pagination
      v-if="total > 20"
      v-model:current-page="page"
      :total="total"
      :page-size="20"
      layout="prev,pager,next"
      @current-change="load"
    />
    <section class="project-history">
      <div class="history-heading">
        <h2>兑换与开户记录</h2>
        <el-button text @click="loadHistory">刷新记录</el-button>
      </div>
      <p class="muted">
        这里记录实际操作与状态。结果未知不等于失败，也不会自动退回预扣金额。
      </p>
      <el-table
        class="history-table"
        :data="records"
        empty-text="还没有项目操作记录"
        ><el-table-column
          prop="projectTitle"
          label="项目"
          min-width="150"
        /><el-table-column label="操作" min-width="130"
          ><template #default="{ row }">{{
            projectActions[row.action]
          }}</template></el-table-column
        ><el-table-column label="项目额度" min-width="100"
          ><template #default="{ row }">{{
            projectAmount(row.units)
          }}</template></el-table-column
        ><el-table-column label="平台金额" min-width="110"
          ><template #default="{ row }"
            >¥{{ row.amount }}</template
          ></el-table-column
        ><el-table-column label="状态" min-width="150"
          ><template #default="{ row }"
            ><el-button text @click="inspect(row.id)"
              >{{ projectState(row.state) }} · 查看</el-button
            ></template
          ></el-table-column
        ></el-table
      >
      <div class="history-mobile">
        <article
          v-for="record in records"
          :key="record.id"
          class="history-record"
        >
          <div>
            <strong>{{ projectActions[record.action] }}</strong
            ><b>¥{{ record.amount }}</b>
          </div>
          <p>
            {{ record.projectTitle }} ·
            {{ projectAmount(record.units) }} 项目额度
          </p>
          <el-button text type="primary" @click="inspect(record.id)"
            >{{ projectState(record.state) }} · 查看</el-button
          >
        </article>
        <p v-if="!records.length" class="muted">还没有项目操作记录</p>
      </div>
      <el-pagination
        v-if="recordTotal > 20"
        v-model:current-page="recordPage"
        :total="recordTotal"
        :page-size="20"
        layout="prev,pager,next"
        @current-change="loadHistory"
      />
    </section>
    <ProjectTickets ref="ticketPanel" :accounts="ticketAccounts" />
    <el-drawer
      class="project-action-drawer"
      v-model="formOpen"
      destroy-on-close
      size="min(500px,100vw)"
      :title="projectActions[action]"
      :close-on-click-modal="false"
      :before-close="closeForm"
    >
      <template v-if="selected"
        ><div class="operation-project">
          {{ selected.title }}<span>独立子钱包 · 不自动执行其他项目任务</span>
        </div>
        <el-form label-position="top" :disabled="busy">
          <el-form-item v-if="action === 'PROVISION'" label="开户方式">
            <el-radio-group v-model="initialFunding" class="opening-options">
              <el-radio :value="false" border>零余额开通</el-radio>
              <el-radio :value="true" border>开通并充值</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-alert v-if="formError" :title="formError" type="warning" :closable="false" class="opening-error" />
          <el-form-item v-if="action !== 'PROVISION' || initialFunding" :label="action === 'PROVISION' ? '初始项目额度' : '项目额度'"
            ><el-input
              v-model="units"
              inputmode="decimal"
              maxlength="20"
              placeholder="最多六位小数"
          /></el-form-item>
          <div class="quote-estimate">
            <span>{{
              action === "TOP_UP"
                ? "预计扣除平台余额"
                : action === "WITHDRAW"
                  ? "预计到账平台余额"
                  : initialFunding ? "开户并充值，预计扣除账户余额" : "零余额开户费用"
            }}</span
            ><strong>¥{{ estimate }}</strong>
            <p>最终以服务器校验的有效报价为准。</p>
          </div>
          <div class="policy-note">
            <p v-if="action === 'PROVISION' && initialFunding">开户费率 ¥{{ selected.unitPrice }} / 额度，充值费用向上取整到分。修改开户方式或额度后须重新确认规则。</p>
            <p v-if="action === 'PROVISION'">
              默认零余额开通，不扣款；也可选择初始额度，核对费用后同时开通和充值。
            </p>
            <p v-else>
              本账户费率冻结为 ¥{{ selected.account.unitPrice }} /
              额度。充值向上取整到分，转回向下取整到分。
            </p>
            <p>
              转回只覆盖已充值且未退的额度与金额；赠送额度不自动兑付。项目余额和账户余额分别记账。
            </p>
          </div>
          <el-checkbox v-model="consent" class="policy-check"
            >我已了解项目账户、费率和余额兑换规则，确认使用本人账户</el-checkbox
          >
        </el-form></template
      ><template #footer
        ><el-button
          type="primary"
          :loading="busy"
          :disabled="!consent || estimate === '—' || busy"
          @click="preview"
          >预览本次操作</el-button
        ></template
      >
    </el-drawer>
    <el-dialog
      class="project-action-dialog"
      v-model="quoteOpen"
      title="确认项目操作"
      width="min(480px,calc(100vw - 24px))"
      :close-on-click-modal="false"
      :show-close="!busy"
      :close-on-press-escape="!busy"
    >
      <template v-if="quote"
        ><p class="operation-name">
          {{ quote.projectTitle }} · {{ projectActions[quote.action] }}
        </p>
        <dl v-if="quote.action === 'PROVISION'" class="opening-summary">
          <div><dt>初始项目额度</dt><dd>{{ projectAmount(quote.units) }}</dd></div>
          <div><dt>本次冻结费率</dt><dd>¥{{ projectAmount(quote.unitPrice) }} / 额度</dd></div>
        </dl>
        <div class="confirm-amount">¥{{ quote.amount }}</div>
        <p class="amount-caption">
          {{
            quote.action === "TOP_UP"
              ? "将扣除平台余额"
              : quote.action === "WITHDRAW"
                ? "确认扣除成功后才转入账户余额"
                : projectOpeningFunded(quote) ? "开户并充值，将扣除账户余额" : "零余额开户，不扣款"
          }}
        </p>
        <el-tag
          :type="
            quote.state === 'SUCCEEDED'
              ? 'success'
              : quote.state === 'UNKNOWN'
                ? 'warning'
                : 'info'
          "
          >{{ projectState(quote.state) }}</el-tag
        >
        <ul class="quote-warnings">
          <li v-for="warning in quote.warnings" :key="warning">
            {{ warning }}
          </li>
        </ul>
        <p class="operation-id">操作编号 {{ quote.id }}</p></template
      >
      <template #footer
        ><el-button :disabled="busy" @click="quoteOpen = false">{{
          attempted ? "关闭" : "暂不执行"
        }}</el-button
        ><el-button
          v-if="!attempted && projectQuoteReady(quote, clock)"
          type="primary"
          :loading="busy"
          @click="confirm"
          >确认{{ quote?.action === "PROVISION" ? (projectOpeningFunded(quote) ? "开户并充值" : "开户") : "兑换" }}</el-button
        ><el-button
          v-else-if="
            quote &&
            !['SUCCEEDED', 'NOT_ACCEPTED', 'EXPIRED'].includes(quote.state)
          "
          type="primary"
          :loading="busy"
          @click="check"
          >检查提交结果</el-button
        ></template
      >
    </el-dialog>
  </main>
</template>
<script setup>
import { computed, onBeforeUnmount, ref, watch } from "vue";
import ProjectTickets from "@/components/projectcenter/ProjectTickets.vue";
import {
  listProjects,
  refreshProjectAccount,
  quoteProject,
  confirmProjectOperation,
  getProjectOperation,
  listProjectOperations,
} from "@/api/projectCenter";
import {
  projectActions,
  projectState,
  projectAmount,
  estimateProjectTransfer,
  projectActionAllowed,
  projectQuoteReady,
  projectOpeningFunded,
  projectOpeningUnits,
  validProjectOpeningOperation,
} from "@/utils/projectCenter";
const projects = ref([]),
  total = ref(0),
  page = ref(1),
  records = ref([]),
  recordPage = ref(1),
  recordTotal = ref(0),
  loading = ref(false),
  error = ref(""),
  refreshing = ref(null);
const ticketPanel = ref(null);
const ticketAccounts = computed(() =>
  projects.value
    .filter((p) => p.account?.ticketsAvailable)
    .map((p) => ({ accountId: p.account.id, title: p.title })),
);
const selected = ref(null),
  action = ref("PROVISION"),
  units = ref("10"),
  initialFunding = ref(false),
  formError = ref(""),
  consent = ref(false),
  formOpen = ref(false),
  quote = ref(null),
  quoteOpen = ref(false),
  busy = ref(false),
  attempted = ref(false),
  clock = ref(Date.now());
let alive = true,
  loadVersion = 0,
  historyVersion = 0,
  operationVersion = 0;
const timer = setInterval(() => (clock.value = Date.now()), 1000);
const estimate = computed(() =>
  action.value === "PROVISION" && !initialFunding.value
    ? "0.00"
    : estimateProjectTransfer(
        units.value,
        action.value === "PROVISION" ? selected.value?.unitPrice : selected.value?.account?.unitPrice,
        action.value === "WITHDRAW",
      ),
);
async function load() {
  const v = ++loadVersion;
  loading.value = true;
  error.value = "";
  try {
    const data = await listProjects({ page: page.value, pageSize: 20 });
    if (alive && v === loadVersion) {
      projects.value = data.records;
      total.value = data.total;
    }
  } catch {
    if (alive && v === loadVersion)
      error.value =
        "项目中心尚未启用或暂时不可用，请联系管理员检查迁移与配置。";
  } finally {
    if (alive && v === loadVersion) loading.value = false;
  }
}
async function loadHistory() {
  const v = ++historyVersion;
  try {
    const data = await listProjectOperations({
      page: recordPage.value,
      pageSize: 20,
    });
    if (alive && v === historyVersion) {
      records.value = data.records;
      recordTotal.value = data.total;
    }
  } catch {}
}
async function refreshBalance(project) {
  if (refreshing.value) return;
  refreshing.value = project.account.id;
  try {
    const data = await refreshProjectAccount(project.account.id);
    if (alive) project.account = data;
  } catch {
  } finally {
    refreshing.value = null;
  }
}
watch([initialFunding, units], () => { consent.value = false; formError.value = ""; });
function begin(project, next) {
  if (busy.value || !projectActionAllowed(project, next)) return;
  operationVersion++;
  initialFunding.value = false;
  formError.value = "";
  selected.value = project;
  action.value = next;
  units.value = "10";
  consent.value = false;
  formOpen.value = true;
}
function closeForm(done) {
  if (!busy.value) done();
}
async function preview() {
  if (busy.value || !consent.value || estimate.value === "—") return;
  const version = ++operationVersion, projectId = selected.value.id, actionValue = action.value;
  const amount = actionValue === "PROVISION" ? projectOpeningUnits(initialFunding.value, units.value) : units.value;
  if (amount === undefined) return;
  busy.value = true; formError.value = "";
  try {
    const data = await quoteProject(projectId, { action: actionValue, units: amount, confirmedPolicy: true });
    if (!alive || version !== operationVersion) return;
    if (actionValue === "PROVISION" && (data?.state !== "READY" || !validProjectOpeningOperation(data, { projectId, units: amount })))
      throw new Error("incomplete opening quote");
    quote.value = data;
    attempted.value = false;
    quoteOpen.value = true;
    formOpen.value = false;
  } catch {
    if (alive && version === operationVersion) formError.value = "预览未完成或费用信息不完整，未开户、未扣款。请重新核对。";
  } finally { if (alive && version === operationVersion) busy.value = false; }
}
function acceptOperation(value, previous) {
  if (previous.action === "PROVISION" && !validProjectOpeningOperation(value, previous))
    throw new Error("incomplete opening receipt");
  quote.value = value;
}
async function confirm() {
  if (busy.value || attempted.value || !projectQuoteReady(quote.value)) return;
  const previous = quote.value, version = ++operationVersion;
  busy.value = true;
  attempted.value = true;
  try {
    const data = await confirmProjectOperation(previous.id);
    if (!alive || version !== operationVersion) return;
    acceptOperation(data, previous);
    await Promise.all([load(), loadHistory()]);
  } catch {
    if (alive && version === operationVersion) quote.value = {
      ...previous, state: "NETWORK_UNKNOWN",
      warnings: ["请求结果尚未确认，请检查同一操作，不要重新提交或自动退款。"],
    };
  } finally { if (alive && version === operationVersion) busy.value = false; }
}
async function check() {
  if (!quote.value || busy.value) return;
  const previous = quote.value, version = ++operationVersion;
  busy.value = true;
  try {
    const data = await getProjectOperation(previous.id);
    if (!alive || version !== operationVersion) return;
    acceptOperation(data, previous);
    await Promise.all([load(), loadHistory()]);
  } catch {
    if (alive && version === operationVersion) quote.value = { ...previous, warnings: ["原操作结果尚未确认或数据不完整；没有重复开户、充值或退款。"] };
  } finally { if (alive && version === operationVersion) busy.value = false; }
}
async function inspect(id) {
  if (busy.value) return;
  const version = ++operationVersion;
  busy.value = true;
  try {
    const data = await getProjectOperation(id);
    if (!alive || version !== operationVersion) return;
    if (data?.id !== id || data?.action === "PROVISION" && !validProjectOpeningOperation(data)) throw new Error("incomplete operation");
    quote.value = data;
    attempted.value = true;
    quoteOpen.value = true;
  } catch {
    if (alive && version === operationVersion) error.value = "操作记录读取失败或信息不完整，请重试；未再次提交操作。";
  } finally { if (alive && version === operationVersion) busy.value = false; }
}
load();
loadHistory();
onBeforeUnmount(() => {
  alive = false;
  loadVersion++;
  historyVersion++;
  operationVersion++;
  clearInterval(timer);
});
</script>
<style scoped>
.project-center {
  max-width: 1400px;
  margin: 0 auto;
}
.project-header,
.history-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 26px;
}
.eyebrow {
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 1.5px;
  color: var(--el-color-primary);
}
h1 {
  font-size: 28px;
  line-height: 1.4;
  margin: 9px 0 12px;
}
h2 {
  font-size: 18px;
  margin: 0;
}
.project-header p,
.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.8;
}
.project-scope-note {
  margin-bottom: 20px;
}
.project-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}
.project-card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 14px;
  padding: 23px;
  background: var(--el-bg-color);
}
.card-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}
.project-marker {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--el-text-color-secondary);
}
.description {
  font-size: 13px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
  min-height: 46px;
}
.balance-band {
  background: var(--el-fill-color-extra-light);
  border-radius: 10px;
  padding: 17px;
  margin: 20px 0;
  display: grid;
  gap: 7px;
}
.balance-band > span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.balance-band strong {
  font-size: 30px;
  font-variant-numeric: tabular-nums;
}
.balance-band small {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}
.balance-band p {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin: 0;
}
.rate-lines {
  font-size: 12px;
}
.rate-lines div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin: 13px 0;
}
.rate-lines dt {
  color: var(--el-text-color-secondary);
}
.rate-lines dd {
  margin: 0;
}
.project-actions {
  display: flex;
  gap: 9px;
  flex-wrap: wrap;
  margin-top: 20px;
}
.project-actions .el-button {
  min-height: 44px;
  margin-left: 0;
}
.project-history {
  margin-top: 36px;
  padding: 25px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 14px;
  background: var(--el-bg-color);
  overflow: hidden;
}
.history-heading {
  margin-bottom: 10px;
}
.operation-project {
  font-size: 18px;
  font-weight: 650;
  margin-bottom: 28px;
}
.operation-project span {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-weight: 400;
  margin-top: 8px;
}
:global(.project-action-drawer .el-button), :global(.project-action-dialog .el-button),
:global(.project-action-drawer .el-input__wrapper) { min-height: 44px; box-sizing: border-box; }
:global(.project-action-drawer .el-drawer__close-btn), :global(.project-action-dialog .el-dialog__headerbtn) { min-width: 44px; min-height: 44px; }
.opening-options { display: grid; gap: 12px; width: 100%; }
.opening-options :deep(.el-radio) { margin: 0; min-height: 48px; height: auto; padding: 14px; }
.opening-error { margin-bottom: 18px; }
.opening-summary { display: grid; gap: 12px; padding: 16px; background: var(--el-fill-color-extra-light); border-radius: 8px; }
.opening-summary > div { display: flex; justify-content: space-between; gap: 16px; font-size: 13px; line-height: 1.8; }
.opening-summary dt { color: var(--el-text-color-secondary); }
.opening-summary dd { margin: 0; overflow-wrap: anywhere; text-align: right; }
.quote-estimate {
  display: grid;
  gap: 12px;
  background: var(--el-fill-color-extra-light);
  border-radius: 12px;
  padding: 23px;
  margin: 18px 0;
}
.quote-estimate span,
.quote-estimate p {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 0;
}
.quote-estimate strong,
.confirm-amount {
  font-size: 36px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}
.policy-note,
.quote-warnings {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.9;
}
.policy-check {
  height: auto;
  white-space: normal;
  align-items: flex-start;
  margin-top: 18px;
}
.policy-check :deep(.el-checkbox__label) {
  white-space: normal;
  line-height: 1.8;
}
.policy-check :deep(.el-checkbox__input) {
  margin-top: 5px;
}
.operation-name {
  font-weight: 600;
}
.amount-caption {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.quote-warnings {
  padding-left: 20px;
}
.operation-id {
  overflow-wrap: anywhere;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.el-pagination {
  margin-top: 18px;
}
.project-center :deep(.el-dialog__footer .el-button) {
  min-height: 44px;
}
.history-mobile {
  display: none;
}
.history-record {
  padding: 12px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}
.history-record > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}
.history-record p {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.7;
}
.history-record .el-button {
  min-height: 44px;
}
@media (max-width: 1100px) {
  .project-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 640px) {
  .project-grid {
    grid-template-columns: 1fr;
  }
  .project-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }
  .project-header > .el-button {
    margin-top: 0;
    min-height: 44px;
  }
  .history-table {
    display: none;
  }
  .history-mobile {
    display: grid;
    gap: 12px;
  }
  h1 {
    font-size: 22px;
  }
  .project-card,
  .project-history {
    padding: 18px;
  }
}
</style>
