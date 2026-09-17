<template>
  <div class="admin-services">
    <header>
      <div>
        <span class="eyebrow">SERVICE CATALOG / 商品上架</span>
        <h1>配置可购买的服务商品。</h1>
        <p>选择已验证的服务配置，设置售价并上架，用户即可在服务商城购买。</p>
      </div>
      <el-button type="primary" :disabled="saving" @click="open()">上架服务商品</el-button>
    </header>
    <el-alert v-if="publicationLoading" type="info" title="正在核对所选服务接口，请稍候" :closable="false" />
    <el-alert v-if="publicationError" type="error" :title="publicationError" :closable="false">
      <el-button v-if="router" text @click="readRequestedProvider(router.currentRoute.value.query)">重新核对配置</el-button>
    </el-alert>
    <el-alert v-if="error" type="warning" :title="error" :closable="false" />
    <el-table v-loading="loading" :data="items"
      ><el-table-column
        prop="title"
        label="商品名称"
        min-width="180"
      /><el-table-column
        prop="displayName"
        label="显示别名"
        min-width="180"
      ><template #default="{ row }">{{ row.displayName ?? row.title }}</template></el-table-column
      /><el-table-column label="服务项目" min-width="140"
        ><template #default="{ row }"
          >{{ serviceNames[row.providerType] }} · {{ row.project }}</template
        ></el-table-column
      ><el-table-column label="销售价格" min-width="150"
        ><template #default="{ row }"
          >¥{{ row.unitPrice }} {{ row.priceUnit }}</template
        ></el-table-column
      ><el-table-column label="状态" width="130"
        ><template #default="{ row }"
          ><el-tag :type="row.available ? 'success' : 'info'">{{
            row.enabled ? (row.available ? "已上架" : "暂不可用") : "已下架"
          }}</el-tag></template
        ></el-table-column
      ><el-table-column label="管理" width="100" fixed="right"
        ><template #default="{ row }"
          ><el-button text type="primary" @click="open(row)"
            >编辑</el-button
          ></template
        ></el-table-column
      ></el-table
    >
    <el-pagination
      v-if="total > 20"
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="prev, pager, next"
      @current-change="load"
    />
    <el-dialog
      v-model="dialog"
      class="service-product-dialog"
      append-to-body
      :title="editing ? '编辑服务商品' : '上架服务商品'"
      width="min(660px, 95vw)"
      :close-on-click-modal="false"
    >
      <el-form label-position="top" :disabled="saving">
        <el-alert
          title="商品和服务配置关联后不可更换。停用配置会暂停新订单；旧订单仍会保留。"
          type="info"
          :closable="false"
        />
        <el-form-item label="已保存的服务接口" :error="providersError"
          ><div class="provider-row">
            <el-select
              v-model="form.providerId"
              class="service-provider-select"
              aria-label="已保存的服务接口"
              filterable
              fit-input-width
              popper-class="service-provider-select-popper"
              :filter-method="searchProviders"
              :loading="providersLoading"
              loading-text="正在加载服务接口"
              :no-data-text="providerEmptyText"
              :disabled="!!editing"
              placeholder="选择已验证启用的服务接口"
              @change="providerChanged"
              ><el-option
                v-for="p in providers"
                :key="p.id"
                :value="p.id"
                :label="`${p.name} · ${serviceNames[p.providerType]}`"
                :disabled="p.status !== 1 || !p.verifiedAt" /></el-select
            ><el-button
              :disabled="!!editing"
              :loading="providersLoading"
              @click="loadProviders(true)"
              >刷新接口</el-button
            >
          </div>
          <el-button
            v-if="!editing && providerPage * 50 < providerTotal"
            text
            :loading="providersLoading"
            @click="loadProviders(false)"
            >加载更多接口</el-button
          >
          <div v-if="!editing && !providersLoading && !providersError && !providers.length && !providerKeyword" class="service-config-empty">
            <p>没有匹配的已启用服务接口。请先添加对应服务，完成验证并启用。</p>
            <el-button type="primary" plain @click="configureService">{{ providerTypeFilter ? '添加此服务接口' : '选择并配置服务' }}</el-button>
          </div>
          </el-form-item
        >
        <el-form-item v-if="selectedType === 'flash'" label="项目"
          ><el-select
            v-model="form.project"
            :disabled="!!editing"
            @change="projectChanged"
            ><el-option label="闪动校园" value="sdxy" /><el-option
              label="运动世界校园"
              value="ydsjxy" /><el-option
              label="校步点"
              value="xbd" /></el-select
        ></el-form-item>
        <el-form-item v-if="selectedType === 'jingyu'" label="运动项目">
          <el-select v-model="form.project" aria-label="运动项目" :disabled="!!editing"
            @change="projectChanged">
            <el-option v-for="(label, key) in jingyuProjects" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-alert v-if="selectedType === 'jingyu'" type="info" :closable="false"
          title="Keep、校园运动按每次距离计费，单次费用先保留两位小数，再计算总额；步道乐跑按次数计费。购买 1–365 次，每次 1–100 公里；校园运动还须满足所选规则的最低距离。退款须核对实际次数后入账。" />
        <el-form-item v-if="selectedType === 'leidian'" label="运动项目">
          <el-select v-model="form.project" aria-label="运动项目" :disabled="!!editing"
            @change="projectChanged">
            <el-option v-for="(label, key) in leidianProjects" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-alert v-if="selectedType === 'leidian'" type="info" :closable="false"
          title="每次距离为 1–10 公里。步道三个项目每次计费距离最多按 2 公里计算；乐健体育按实际距离计费。订单总额以确认页面为准，取消不代表退款入账。" />
        <el-form-item v-if="selectedType === 'appui'" label="实习项目">
          <el-select v-model="form.project" :disabled="!!editing"
            @change="projectChanged">
            <el-option v-for="(label, key) in appuiProjects" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="selectedType === 'sxdk_tw'" label="实习平台"
          ><el-select
            v-model="form.project"
            :disabled="!!editing"
            @change="internshipProjectChanged"
            ><el-option
              v-for="(label, key) in internshipProjects"
              :key="key"
              :label="label"
              :value="key" /></el-select
        ></el-form-item>
        <el-alert v-if="selectedType === 'ssbenz_xbd'" type="info" :closable="false"
          title="总公里计划按公里计价；方案 0 / 1 仅为编号，请根据已核实的服务说明命名。只提供提交状态，不提供完成进度或自动退款。" />
        <el-form-item v-if="selectedType !== 'sxdk_tw'" label="服务商品"
          ><div class="provider-row">
            <el-select
              v-model="form.remoteProductId"
              :disabled="!!editing"
              placeholder="先读取服务目录"
              @change="selectRemote"
              ><el-option
                v-for="p in remoteProducts"
                :key="p.id"
                :value="p.id"
                :label="`${p.name} · 成本 ¥${p.unitPrice}`" /></el-select
            ><el-button
              :loading="reading"
              :disabled="!selectedProviderReady || !!editing"
              @click="readCatalog"
              >读取目录</el-button
            >
          </div></el-form-item
        >
        <el-alert
          v-if="
            selectedType === 'heisha' &&
            ['3', '4'].includes(form.remoteProductId)
          "
          title="人脸商品须先配置已批准的 HTTPS 官方采集域名（HEISHA_FACE_ALLOWED_ORIGINS），并完成本人授权流程联调。空白名单会阻止创建采集链接；平台不存储人脸照片。"
          type="warning"
          show-icon
          :closable="false"
        />
        <el-form-item label="商品名称"
          ><el-input v-model="form.title" maxlength="100" /></el-form-item
        ><el-form-item label="商品说明"
          ><el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="1000" /></el-form-item
        ><el-form-item
          :label="`销售单价（${selectedType === 'sxdk_tw' ? '元/服务日' : selectedType === 'appui' ? '元/天' : selectedType === 'jingyu' ? (form.project === 'bdlp' ? '元/次' : '元/次·公里') : selectedType === 'leidian' ? '元/次·公里' : selectedType === 'wuxin' || (selectedType === 'flash' && form.project === 'sdxy') ? '元/次' : '元/公里'}，最多六位小数）`"
          ><el-input
            v-model="form.unitPrice"
            inputmode="decimal"
            placeholder="例如 0.25；不能低于成本价" /></el-form-item
        ><template v-if="selectedType === 'sxdk_tw'">
          <el-alert
            title="当前服务没有可验证的实时报价，不使用预设价格。"
            description="请核实服务日成本、运行方式倍率及取消退款规则，填写合同依据。核对过期或服务配置变更后会暂停新下单。"
            type="warning"
            :closable="false"
          />
          <el-form-item label="已核实的每服务日成本"
            ><el-input
              v-model="contract.unitCost"
              inputmode="decimal"
              placeholder="填写已核实的每服务日成本"
          /></el-form-item>
          <el-form-item label="本次价格核对有效期（最长 90 天）"
            ><el-date-picker
              v-model="contract.validUntil"
              type="date"
              value-format="YYYY-MM-DD"
          /></el-form-item>
          <el-form-item label="核对依据（至少 10 字，不包含凭据）"
            ><el-input
              v-model="contract.evidence"
              type="textarea"
              :rows="3"
              maxlength="1000"
          /></el-form-item>
          <el-checkbox
            v-model="contract.upstreamChecked"
            class="contract-consent"
            >我已核实成本、倍率和取消规则，认可按服务日计费与退款条款</el-checkbox
          > </template
        ><el-form-item label="销售状态"
          ><el-switch
            v-model="form.enabled"
            active-text="上架到用户商城"
            inactive-text="保持下架"
        /></el-form-item> </el-form
      ><template #footer
        ><el-button :disabled="saving" @click="dialog = false">取消</el-button
        ><el-button
          type="primary"
          :loading="saving"
          :disabled="
            !form.providerId ||
            (!editing && !selectedProviderReady) ||
            !form.remoteProductId ||
            !form.title ||
            !form.unitPrice
          "
          @click="save"
          >保存商品</el-button
        ></template
      >
    </el-dialog>
  </div>
</template>
<script setup>
import { computed, inject, ref, watch, onBeforeUnmount } from "vue";
import { routerKey } from "vue-router";
import { ElMessage } from "element-plus";
import request from "@/utils/request";
import { listServiceProducts, saveServiceProduct } from "@/api/serviceCommerce";
import { fetchPluginCatalog } from "@/api/pluginIntegration";
import { internshipProjects } from "@/utils/internshipServices";
import { appuiProjects } from "@/utils/appuiServices";
import { leidianProjects } from "@/utils/leidianServices";
import { jingyuProjects } from "@/utils/jingyuServices";
import { serviceNames, nativeProductSupported } from "@/utils/serviceCommerce";
import { serviceTypeFromQuery, servicePublishRequest, validPublicationProvider } from "@/utils/pluginWorkflows";
import { latestRequest } from "@/utils/pluginIntegrations";
const router = inject(routerKey, null);
const viewPath = router?.currentRoute.value.path;
const providerTypeFilter = ref(serviceTypeFromQuery(router?.currentRoute.value.query.providerType));
const publicationLoading = ref(false), publicationError = ref("");
const publicationRequests = latestRequest(), productListRequests = latestRequest(), catalogRequests = latestRequest();
const items = ref([]),
  page = ref(1),
  total = ref(0),
  loading = ref(false),
  error = ref(""),
  dialog = ref(false),
  editing = ref(null),
  providers = ref([]),
  remoteProducts = ref([]),
  reading = ref(false),
  saving = ref(false);
const blank = () => ({
  providerId: null,
  project: "default",
  remoteProductId: "",
  title: "",
  description: "",
  unitPrice: "",
  enabled: false,
  version: null,
});
const form = ref(blank());
const contract = ref({
  unitCost: "",
  validUntil: "",
  evidence: "",
  upstreamChecked: false,
});
const providersLoading = ref(false),
  providersError = ref(""),
  providerPage = ref(1),
  providerTotal = ref(0),
  providerKeyword = ref("");
let providerRequest = 0,
  searchTimer;
const providerEmptyText = computed(() =>
  providersError.value || (providerKeyword.value
    ? "未找到匹配的服务接口"
    : "暂无已启用的服务接口，请先完成验证并启用"),
);
const selectedProvider = computed(() => providers.value.find((p) => p.id === form.value.providerId));
const selectedProviderReady = computed(() => selectedProvider.value?.status === 1 && !!selectedProvider.value.verifiedAt);
const selectedType = computed(
  () =>
    selectedProvider.value?.providerType ||
    editing.value?.providerType ||
    "",
);
async function load() {
  const task = productListRequests.begin();
  loading.value = true;
  error.value = "";
  try {
    const r = await listServiceProducts(
      { page: page.value, pageSize: 20, providerType: providerTypeFilter.value || undefined },
      true,
    );
    if (!task.current()) return;
    items.value = r.records;
    total.value = r.total;
  } catch {
    if (!task.current()) return;
    error.value =
      "服务商城未启用或读取失败，请联系系统管理员完成必要配置。";
  } finally {
    if (task.current()) loading.value = false;
  }
}
async function loadProviders(reset = true) {
  clearTimeout(searchTimer);
  const token = ++providerRequest,
    next = reset ? 1 : providerPage.value + 1;
  providersLoading.value = true;
  providersError.value = "";
  const selected = providers.value.find((p) => p.id === form.value.providerId);
  if (reset) {
    providers.value = selected ? [selected] : [];
    providerPage.value = 0;
    providerTotal.value = 0;
  }
  try {
    const r = await request.get("/admin/api-providers", {
      params: {
        page: next,
        pageSize: 50,
        status: 1,
        providerTypes: providerTypeFilter.value || Object.keys(serviceNames).join(","),
        keyword: providerKeyword.value || undefined,
      },
    });
    if (token !== providerRequest) return;
    const rows = r.data.records.filter((p) => serviceTypeFromQuery(p?.providerType) &&
      (!providerTypeFilter.value || p.providerType === providerTypeFilter.value));
    providers.value = [
      ...new Map(
        [
          ...(selected ? [selected] : []),
          ...(reset ? [] : providers.value),
          ...rows,
        ].map((p) => [p.id, p]),
      ).values(),
    ];
    providerPage.value = next;
    providerTotal.value = r.data.total;
  } catch {
    if (token === providerRequest)
      providersError.value = "服务接口加载失败，请点击“刷新接口”重试";
  } finally {
    if (token === providerRequest) providersLoading.value = false;
  }
}
function searchProviders(keyword) {
  if (keyword === providerKeyword.value) return;
  providerRequest++;
  providerKeyword.value = keyword;
  providersLoading.value = true;
  providersError.value = "";
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => loadProviders(true), 250);
}
function open(row, preset = null, requested = null) {
  resetCatalog();
  publicationRequests.invalidate(); publicationLoading.value = false;
  if (!preset) publicationError.value = "";
  contract.value = {
    unitCost: row?.contractPrice?.unitCost || "",
    validUntil: row?.contractPrice?.validUntil || "",
    evidence: row?.contractPrice?.evidence || "",
    upstreamChecked: false,
  };
  clearTimeout(searchTimer);
  providerKeyword.value = "";
  editing.value = row || null;
  form.value = row
    ? {
        providerId: row.providerId,
        project: row.project,
        remoteProductId: row.remoteProductId,
        title: row.title,
        description: row.description || "",
        unitPrice: row.unitPrice,
        enabled: row.enabled,
        version: row.version,
      }
    : blank();
  remoteProducts.value = row
    ? [{ id: row.remoteProductId, name: row.title, unitPrice: row.unitPrice }]
    : [];
  if (preset) {
    providers.value = [preset];
    form.value.providerId = preset.id;
    providerChanged();
    if (requested?.project) {
      form.value.project = requested.project;
      if (selectedType.value === "sxdk_tw") internshipProjectChanged();
    }
  }
  dialog.value = true;
  loadProviders();
}
function configureService() {
  dialog.value = false;
  router?.push(providerTypeFilter.value
    ? { path: "/admin/api-providers", query: { type: providerTypeFilter.value } }
    : { path: "/admin/plugin-integrations" });
}
async function readRequestedProvider(query) {
  publicationRequests.invalidate(); publicationLoading.value = false; publicationError.value = "";
  if (!query) { if (router) dialog.value = false; return; }
  providerTypeFilter.value = serviceTypeFromQuery(query.providerType);
  dialog.value = false;
  if (!Object.hasOwn(query, "providerId") && !Object.hasOwn(query, "project")) return;
  const requested = servicePublishRequest(query);
  if (!requested) {
    publicationError.value = "上架信息不完整或不正确，请从功能页重新选择服务配置。";
    return;
  }
  const task = publicationRequests.begin(); publicationLoading.value = true;
  try {
    const response = await request.get(`/admin/api-providers/${requested.providerId}`, { signal: task.signal });
    if (!task.current()) return;
    if (!validPublicationProvider(response.data, requested)) {
      publicationError.value = "所选接口不存在、类型不匹配或尚未验证启用，请检查配置后重试。";
      return;
    }
    const { id, name, providerType, status, verifiedAt } = response.data;
    open(null, { id, name, providerType, status, verifiedAt }, requested);
  } catch {
    if (task.current()) publicationError.value = "无法核对所选服务接口，请检查查看权限和接口配置后重试。";
  } finally { if (task.current()) publicationLoading.value = false; }
}
function resetCatalog() {
  catalogRequests.invalidate();
  reading.value = false;
  remoteProducts.value = [];
}
function projectChanged() {
  resetCatalog();
  form.value.remoteProductId = "";
}
function providerChanged() {
  projectChanged();
  if (selectedType.value === "sxdk_tw") {
    form.value.project = "zxjy";
    internshipProjectChanged();
    return;
  }
  form.value.project = ["flash", "wuxin"].includes(selectedType.value)
    ? "sdxy"
    : selectedType.value === "jingyu" ? "keep" : ["appui", "leidian"].includes(selectedType.value) ? "1" : selectedType.value === "ssbenz_xbd" ? "xbd" : "default";
  form.value.remoteProductId = "";
  remoteProducts.value = [];
}
function internshipProjectChanged() {
  resetCatalog();
  form.value.remoteProductId = form.value.project;
  form.value.title = `实习 · ${internshipProjects[form.value.project]}`;
  remoteProducts.value = [];
}
async function readCatalog() {
  if (reading.value || !selectedProviderReady.value || editing.value || !dialog.value) return;
  const task = catalogRequests.begin();
  reading.value = true;
  const providerId = form.value.providerId,
    project = form.value.project,
    type = selectedType.value;
  try {
    const r = await fetchPluginCatalog(
      { flash: "P01", heisha: "P03", jiguang: "P04", wuxin: "P10", ssbenz_xbd: "P05", appui: "P09", leidian: "P12", jingyu: "P08" }[type],
      providerId,
      ["flash", "ssbenz_xbd", "appui", "leidian", "jingyu"].includes(type) ? project : null,
      task.signal,
    );
    if (!task.current() || !dialog.value || form.value.providerId !== providerId || form.value.project !== project)
      return;
    remoteProducts.value = r.data.filter((p) =>
      nativeProductSupported(type, project, p.id),
    );
    if (!remoteProducts.value.length)
      ElMessage.warning("没有可下单的商品");
  } catch {
  } finally {
    if (task.current()) reading.value = false;
  }
}
function selectRemote() {
  const p = remoteProducts.value.find(
    (p) => p.id === form.value.remoteProductId,
  );
  if (p) {
    form.value.title = p.name;
    form.value.unitPrice = p.unitPrice;
  }
}
async function save() {
  saving.value = true;
  try {
    await saveServiceProduct(editing.value?.id, {
      ...form.value,
      contractPrice: selectedType.value === "sxdk_tw" ? contract.value : null,
    });
    dialog.value = false;
    ElMessage.success("服务商品已保存");
    await load();
  } catch {
  } finally {
    saving.value = false;
  }
}
watch(providerTypeFilter, () => { page.value = 1; load(); });
watch(() => router && router.currentRoute.value.path === viewPath ? router.currentRoute.value.query : null,
  readRequestedProvider, { immediate: true, flush: "sync" });
watch(dialog, (visible) => {
  if (!visible) { resetCatalog(); providerRequest++; clearTimeout(searchTimer); providersLoading.value = false; }
});
onBeforeUnmount(() => {
  publicationRequests.invalidate(); productListRequests.invalidate(); resetCatalog();
  providerRequest++;
  clearTimeout(searchTimer);
});
load();
</script>
<style scoped>
.service-config-empty { margin-top: 10px; width: 100%; }
.service-config-empty p { margin: 0 0 8px; line-height: 1.7; color: var(--text-secondary); }

:global(.service-product-dialog) {
  display: flex;
  flex-direction: column;
  max-height: calc(100dvh - 48px);
  margin: 24px auto !important;
}
:global(.service-product-dialog .el-dialog__body) {
  min-height: 0;
  overflow: auto;
  flex: 1;
}
.service-provider-select :deep(.el-select__input:focus-visible) {
  outline: none;
  box-shadow: none;
}
:global(.service-provider-select-popper .el-select-dropdown__wrap) {
  max-height: min(274px, 40dvh);
}
:global(.service-provider-select-popper .el-select-dropdown__empty) {
  padding-inline: 12px;
  white-space: normal;
  overflow-wrap: anywhere;
}
:global(.service-provider-select-popper .el-select-dropdown__item) {
  height: auto;
  min-height: 34px;
  padding-block: 7px;
  line-height: 20px;
  white-space: normal;
  overflow-wrap: anywhere;
}
.contract-consent {
  height: auto;
  line-height: 1.8;
}
.contract-consent :deep(.el-checkbox__label) {
  white-space: normal;
}

.admin-services {
  max-width: 1200px;
  margin: auto;
}
.admin-services header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 26px;
}
.admin-services h1 {
  font-size: 25px;
  margin: 10px 0;
}
.admin-services header p {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.8;
}
.eyebrow {
  font-size: 11px;
  letter-spacing: 1.5px;
  color: var(--el-color-primary);
  font-weight: 700;
}
.provider-row {
  display: flex;
  gap: 10px;
  width: 100%;
}
.provider-row .el-select {
  flex: 1;
  min-width: 0;
}
.admin-services :deep(.el-alert) {
  margin-bottom: 20px;
}
.admin-services :deep(.el-table) {
  border-radius: 14px;
}
.admin-services :deep(.el-pagination) {
  margin-top: 20px;
}
@media (max-width: 600px) {
  .admin-services header {
    align-items: flex-start;
    flex-direction: column;
  }
  .admin-services h1 {
    font-size: 22px;
  }
  .provider-row {
    flex-wrap: wrap;
  }
}
</style>
