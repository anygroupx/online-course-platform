<template>
  <main class="admin-projects">
    <header>
      <div>
        <span class="eyebrow">PROJECT OPERATIONS / 项目运营</span>
        <h1>项目、费率与资金核对</h1>
        <p>
          目录展示价不等于最终实际成本。确认合同费率后发布，用户账户保留开通时的冻结售价。
        </p>
      </div>
      <el-button type="primary" @click="edit()">发布项目</el-button>
    </header>
    <el-tabs
      v-model="tab"
      @tab-change="tab === 'review' ? loadOperations() : tab === 'projects' ? load() : undefined"
      ><el-tab-pane label="项目与费率" name="projects" /><el-tab-pane
        label="资金操作核对"
        name="review" /><el-tab-pane label="项目工单" name="tickets" /><el-tab-pane label="运营统计" name="reports" /><el-tab-pane label="经营者明细" name="owners" /><el-tab-pane label="资金流水" name="ledger"
    /></el-tabs>
    <el-alert
      v-if="error"
      :title="error"
      type="warning"
      :closable="false"
      show-icon
    />
    <template v-if="tab === 'projects'"
      ><el-table :data="rows" v-loading="loading"
        ><el-table-column
          prop="title"
          label="项目"
          min-width="170"
        /><el-table-column
          prop="remoteProjectId"
          label="项目"
          width="110"
        /><el-table-column label="新账户售价 / 已核实成本" min-width="210"
          ><template #default="{ row }"
            >¥{{ row.unitPrice }} / ¥{{ row.unitCost }}</template
          ></el-table-column
        ><el-table-column
          prop="validUntil"
          label="核实有效期"
          width="150"
        /><el-table-column label="状态" min-width="120"
          ><template #default="{ row }"
            ><el-tag :type="row.available ? 'success' : 'info'">{{
              row.enabled ? (row.available ? "已发布" : "需核实配置") : "未发布"
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="操作" width="95"
          ><template #default="{ row }"
            ><el-button text @click="edit(row)">编辑</el-button></template
          ></el-table-column
        ></el-table
      ><el-pagination
        v-if="total > 20"
        v-model:current-page="page"
        :page-size="20"
        :total="total"
        layout="prev,pager,next"
        @current-change="load"
    /></template>
    <template v-else-if="tab === 'review'"
      ><p class="review-policy">
        须同时具备接口管理与资金核对权限。先查实际交易流水，再判断是否受理；不能用余额差猜测，也不会再次提交兑换。
      </p>
      <el-button :loading="loading" @click="loadOperations"
        >刷新核对列表</el-button
      ><el-table :data="operationRows" v-loading="loading"
        ><el-table-column
          prop="userId"
          label="用户"
          width="85"
        /><el-table-column
          prop="projectTitle"
          label="项目"
          min-width="150"
        /><el-table-column label="操作" min-width="140"
          ><template #default="{ row }">{{
            projectActions[row.action]
          }}</template></el-table-column
        ><el-table-column label="平台金额" min-width="110"
          ><template #default="{ row }"
            >¥{{ row.amount }}</template
          ></el-table-column
        ><el-table-column label="状态" min-width="120"
          ><template #default="{ row }">{{
            projectState(row.state)
          }}</template></el-table-column
        ><el-table-column label="操作" min-width="110"
          ><template #default="{ row }"
            ><el-button text @click="inspect(row.id)"
              >查看与核对</el-button
            ></template
          ></el-table-column
        ></el-table
      ><el-pagination
        v-if="operationTotal > 20"
        v-model:current-page="operationPage"
        :page-size="20"
        :total="operationTotal"
        layout="prev,pager,next"
        @current-change="loadOperations"
    /></template>
    <ProjectTickets v-else-if="tab === 'tickets'" admin />
    <ProjectUsage v-else-if="tab === 'reports'" admin />
    <ProjectOwners v-else-if="tab === 'owners'" />
    <ProjectLedger v-else-if="tab === 'ledger'" admin />
    <el-drawer
      v-model="editingOpen"
      destroy-on-close
      :title="editing ? '编辑项目' : '发布项目'"
      size="min(560px,100vw)"
      :close-on-click-modal="false"
      :before-close="closeEdit"
      ><el-form label-position="top" :disabled="saving">
        <el-form-item label="已验证的 syyv5 配置"
          ><el-select
            v-if="!editing"
            v-model="form.providerId"
            filterable
            remote
            :remote-method="findProviders"
            :loading="providersLoading"
            placeholder="搜索接口名称"
            @change="clearCatalog"
            ><el-option
              v-for="provider in providerRows"
              :key="provider.id"
              :value="provider.id"
              :label="provider.name"
              :disabled="
                provider.status !== 1 || !provider.verifiedAt
              " /></el-select
          ><el-input
            v-else
            :model-value="`固定绑定接口 #${form.providerId}`"
            disabled
        /></el-form-item>
        <el-form-item label="项目"
          ><div class="catalog-line">
            <el-select
              v-model="form.remoteProjectId"
              :disabled="!!editing"
              placeholder="先读取已授权的项目目录"
              @change="selectRemote"
              ><el-option
                v-for="item in catalog"
                :key="item.id"
                :value="item.id"
                :label="`${item.name} · 目录 ¥${item.basePrice}`" /><el-option
                v-if="editing"
                :value="form.remoteProjectId"
                :label="`已绑定项目 ${form.remoteProjectId}`" /></el-select
            ><el-button
              :loading="reading"
              :disabled="!form.providerId || !!editing"
              @click="readCatalog"
              >读取目录</el-button
            >
          </div></el-form-item
        >
        <el-form-item label="项目名称"
          ><el-input v-model="form.title" maxlength="100" /></el-form-item
        ><el-form-item label="项目说明"
          ><el-input
            v-model="form.description"
            type="textarea"
            maxlength="1000"
            :rows="2"
        /></el-form-item>
        <el-alert
          title="目录价不等于实际成本，缺价不默认为 1。须核实主账号扣费，并在项目业务入口和服务交付验收前保持充值关闭。"
          type="warning"
          :closable="false"
        />
        <div class="price-grid">
          <el-form-item label="新账户售价（元 / 额度）"
            ><el-input
              v-model="form.unitPrice"
              inputmode="decimal" /></el-form-item
          ><el-form-item label="已核实实际成本（元 / 额度）"
            ><el-input v-model="form.unitCost" inputmode="decimal"
          /></el-form-item>
        </div>
        <el-form-item label="成本核实有效期"
          ><el-date-picker
            v-model="form.validUntil"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="最多九十天" /></el-form-item
        ><el-form-item label="核实依据"
          ><el-input
            v-model="form.evidence"
            type="textarea"
            :rows="3"
            maxlength="1000"
            placeholder="至少十个字，记录合同或账务核实依据；不要填写密钥" /></el-form-item
        ><el-checkbox v-model="form.upstreamChecked" class="wrapped-check"
          >已核实实际单位成本，并了解用户账户冻结售价不会跟随调价</el-checkbox
        ><el-form-item label="开放状态"
          ><el-switch
            v-model="form.enabled"
            active-text="开放新账户与充值"
            inactive-text="未发布"
        /></el-form-item> </el-form
      ><template #footer
        ><el-button :disabled="saving" @click="editingOpen = false"
          >取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >保存项目</el-button
        ></template
      ></el-drawer
    >
    <el-dialog
      v-model="reviewOpen"
      class="project-review-dialog"
      title="核对项目操作"
      width="min(540px,calc(100vw - 24px))"
      :close-on-click-modal="false"
      :show-close="!saving"
      :close-on-press-escape="!saving"
      ><template v-if="operation"
        ><p>
          {{ operation.projectTitle }} · {{ projectActions[operation.action] }}
        </p>
        <div class="review-amount">¥{{ operation.amount }}</div>
        <p>
          项目额度 {{ operation.units }} · {{ projectState(operation.state) }}
        </p>
        <p class="muted">操作编号 {{ operation.id }}</p>
        <el-alert
          v-if="reviewAttempted"
          title="已提交核对请求。请查询原操作结果，不要再次提交。"
          type="warning"
          :closable="false"
        />
        <el-alert v-if="projectOpeningFunded(operation) && operation.state === 'UNKNOWN'"
          title="必须同时核实客户归属、开户结果与初始充值；不能只凭当前余额认定成功或退款。"
          type="warning" :closable="false" class="funded-review-warning" />
        <el-form
          v-if="operation.state === 'UNKNOWN'"
          label-position="top"
          :disabled="saving || reviewAttempted"
          ><el-form-item label="实际受理结果"
            ><el-radio-group v-model="review.outcome"
              ><el-radio value="ACCEPTED">已受理</el-radio
              ><el-radio value="NOT_ACCEPTED"
                >完全未受理</el-radio
              ></el-radio-group
            ></el-form-item
          ><el-form-item
            v-if="
              operation.action === 'PROVISION' && review.outcome === 'ACCEPTED'
            "
            label="已核实的客户编号"
            ><el-input
              v-model="review.customerId"
              inputmode="numeric"
              maxlength="19" /></el-form-item
          ><el-form-item label="核对证据"
            ><el-input
              v-model="review.evidence"
              type="textarea"
              :rows="3"
              maxlength="1000"
              placeholder="原操作对应的交易流水与核实过程，至少十个字；不要填密钥" /></el-form-item
          ><el-checkbox v-model="review.upstreamChecked" class="wrapped-check"
            >已逐项核实，不凭余额变化推测受理结果</el-checkbox
          >
          <p class="review-effect">{{ effect }}</p></el-form
        ></template
      ><template #footer
        ><el-button :loading="saving" @click="checkReview"
          >检查原操作状态</el-button
        ><el-button
          v-if="operation?.state === 'UNKNOWN' && !reviewAttempted"
          type="primary"
          :loading="saving"
          :disabled="
            !review.upstreamChecked || review.evidence.trim().length < 10
          "
          @click="resolve"
          >确认核对并记账</el-button
        ></template
      ></el-dialog
    >
  </main>
</template>
<script setup>
import { computed, onBeforeUnmount, ref } from "vue";
import ProjectTickets from "@/components/projectcenter/ProjectTickets.vue";
import ProjectUsage from "@/components/projectcenter/ProjectUsage.vue";
import ProjectOwners from "@/components/projectcenter/ProjectOwners.vue";
import ProjectLedger from "@/components/projectcenter/ProjectLedger.vue";
import { ElMessage } from "element-plus";
import request from "@/utils/request";
import {
  listProjects,
  projectCatalog,
  saveProject,
  listProjectOperations,
  getProjectOperation,
  resolveProjectOperation,
} from "@/api/projectCenter";
import { projectActions, projectState, projectOpeningFunded, projectResolutionEffect, validProjectOpeningOperation } from "@/utils/projectCenter";
const tab = ref("projects"),
  rows = ref([]),
  total = ref(0),
  page = ref(1),
  loading = ref(false),
  error = ref(""),
  operationRows = ref([]),
  operationTotal = ref(0),
  operationPage = ref(1),
  editingOpen = ref(false),
  editing = ref(null),
  saving = ref(false),
  reading = ref(false),
  providersLoading = ref(false),
  providerRows = ref([]),
  catalog = ref([]),
  form = ref({}),
  reviewOpen = ref(false),
  operation = ref(null),
  reviewAttempted = ref(false),
  review = ref({
    outcome: "NOT_ACCEPTED",
    customerId: "",
    evidence: "",
    upstreamChecked: false,
  });
const attemptedReviews = new Set();
let alive = true,
  providerGeneration = 0,
  editorGeneration = 0;
const effect = computed(() => projectResolutionEffect(operation.value, review.value.outcome));

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const r = await listProjects({ page: page.value, pageSize: 20 }, true);
    if (alive) {
      rows.value = r.records;
      total.value = r.total;
    }
  } catch {
    error.value = "项目中心读取失败，请检查功能开关、迁移与接口管理权限。";
  } finally {
    loading.value = false;
  }
}
async function loadOperations() {
  loading.value = true;
  error.value = "";
  try {
    const r = await listProjectOperations(
      { page: operationPage.value, pageSize: 20 },
      true,
    );
    if (alive) {
      operationRows.value = r.records;
      operationTotal.value = r.total;
    }
  } catch {
    error.value = "无法读取项目资金记录，须同时具备接口管理与资金核对权限。";
  } finally {
    loading.value = false;
  }
}
async function findProviders(keyword = "") {
  const v = ++providerGeneration;
  providersLoading.value = true;
  try {
    const r = await request.get("/admin/api-providers", {
      params: { page: 1, pageSize: 50, name: keyword, providerType: "syyv5" },
    });
    if (alive && v === providerGeneration)
      providerRows.value = r.data.records.filter(
        (p) => p.providerType === "syyv5",
      );
  } catch {
  } finally {
    if (v === providerGeneration) providersLoading.value = false;
  }
}
function edit(row) {
  editorGeneration++;
  editing.value = row || null;
  form.value = {
    providerId: row?.providerId || null,
    remoteProjectId: row?.remoteProjectId || "",
    title: row?.title || "",
    description: row?.description || "",
    unitPrice: row?.unitPrice || "",
    unitCost: row?.unitCost || "",
    validUntil: row?.validUntil || "",
    evidence: "",
    upstreamChecked: false,
    enabled: row?.enabled || false,
    version: row?.version ?? null,
  };
  catalog.value = [];
  editingOpen.value = true;
  if (!row) findProviders();
}
function clearCatalog() {
  editorGeneration++;
  catalog.value = [];
  form.value.remoteProjectId = "";
}
function selectRemote() {
  const item = catalog.value.find((p) => p.id === form.value.remoteProjectId);
  if (item && !form.value.title) form.value.title = item.name;
}
async function readCatalog() {
  if (reading.value || !form.value.providerId) return;
  const version = editorGeneration;
  reading.value = true;
  try {
    const data = await projectCatalog(form.value.providerId);
    if (alive && version === editorGeneration) catalog.value = data;
  } catch {
  } finally {
    reading.value = false;
  }
}
function closeEdit(done) {
  if (!saving.value && !reading.value) {
    editorGeneration++;
    done();
  }
}
async function save() {
  if (saving.value) return;
  if (
    !form.value.title ||
    !form.value.remoteProjectId ||
    !form.value.validUntil ||
    form.value.evidence.trim().length < 10 ||
    !form.value.upstreamChecked
  ) {
    ElMessage.warning("请填写项目、价格有效期和核实依据，并确认已核对成本。");
    return;
  }
  saving.value = true;
  try {
    await saveProject(editing.value?.id, form.value);
    editingOpen.value = false;
    await load();
  } catch {
  } finally {
    saving.value = false;
  }
}
function acceptOperation(value, id, previous) {
  if (value?.id !== id || (value?.action === "PROVISION" || previous?.action === "PROVISION") && !validProjectOpeningOperation(value, previous || {}))
    throw new Error("incomplete project opening result");
  operation.value = value;
}
async function inspect(id) {
  if (saving.value) return;
  try {
    const value = await getProjectOperation(id, true);
    if (!alive) return;
    acceptOperation(value, id);
    review.value = {
      outcome: "NOT_ACCEPTED",
      customerId: "",
      evidence: "",
      upstreamChecked: false,
    };
    reviewAttempted.value = attemptedReviews.has(id);
    reviewOpen.value = true;
  } catch {}
}
async function checkReview() {
  if (saving.value || !operation.value) return;
  saving.value = true;
  try {
    const previous = operation.value;
    const value = await getProjectOperation(previous.id, true);
    if (!alive) return;
    acceptOperation(value, previous.id, previous);
    await loadOperations();
  } catch {
  } finally {
    saving.value = false;
  }
}
async function resolve() {
  if (saving.value || reviewAttempted.value || !review.value.upstreamChecked)
    return;
  saving.value = true;
  reviewAttempted.value = true;
  attemptedReviews.add(operation.value.id);
  try {
    const previous = operation.value;
    const value = await resolveProjectOperation(previous.id, {
      ...review.value,
      customerId:
        operation.value.action === "PROVISION" &&
        review.value.outcome === "ACCEPTED"
          ? review.value.customerId
          : null,
    });
    if (!alive) return;
    acceptOperation(value, previous.id, previous);
    await loadOperations();
  } catch {
  } finally {
    saving.value = false;
  }
}
load();
onBeforeUnmount(() => {
  alive = false;
  providerGeneration++;
  editorGeneration++;
});
</script>
<style scoped>
.funded-review-warning { margin: 16px 0; }
:global(.project-review-dialog .el-button), :global(.project-review-dialog .el-input__wrapper) { min-height: 44px; box-sizing: border-box; }
:global(.project-review-dialog .el-dialog__headerbtn) { min-height: 44px; min-width: 44px; }
.admin-projects {
  max-width: 1400px;
  margin: auto;
}
.admin-projects header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 25px;
}
.eyebrow {
  font-size: 11px;
  letter-spacing: 1.5px;
  color: var(--el-color-primary);
  font-weight: 650;
}
h1 {
  font-size: 28px;
  margin: 10px 0;
}
.admin-projects header p,
.muted,
.review-policy {
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}
.catalog-line {
  display: flex;
  gap: 10px;
  width: 100%;
}
.catalog-line > .el-select {
  flex: 1;
  min-width: 0;
}
.price-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
  margin-top: 22px;
}
.wrapped-check {
  white-space: normal;
  height: auto;
  align-items: flex-start;
  margin: 8px 0 20px;
}
.wrapped-check :deep(.el-checkbox__label) {
  white-space: normal;
  line-height: 1.8;
}
.wrapped-check :deep(.el-checkbox__input) {
  margin-top: 5px;
}
.review-amount {
  font-size: 34px;
  font-weight: 650;
}
.muted {
  overflow-wrap: anywhere;
}
.review-effect {
  padding: 15px;
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-7);
  border-radius: 8px;
  line-height: 1.8;
  font-size: 13px;
}
.el-pagination {
  margin-top: 20px;
}
.admin-projects :deep(.el-dialog__footer .el-button) {
  min-height: 44px;
}
.admin-projects :deep(.el-drawer__footer .el-button) {
  min-height: 44px;
}
@media (max-width: 600px) {
  .price-grid {
    grid-template-columns: 1fr;
  }
  h1 {
    font-size: 23px;
  }
  .admin-projects header {
    align-items: flex-start;
  }
  .admin-projects header > .el-button {
    margin-top: 20px;
  }
}
</style>
