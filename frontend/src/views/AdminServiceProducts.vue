<template>
  <div class="admin-services">
    <header>
      <div>
        <span class="eyebrow">SERVICE CATALOG / 商品上架</span>
        <h1>配置可购买的服务商品。</h1>
        <p>选择已验证的服务配置，设置售价并上架，用户即可在服务商城购买。</p>
      </div>
      <el-button type="primary" @click="open()">上架服务商品</el-button>
    </header>
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
        <el-form-item label="已保存的服务接口"
          ><div class="provider-row">
            <el-select
              v-model="form.providerId"
              filterable
              remote
              :remote-method="searchProviders"
              :loading="providersLoading"
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
          ></el-form-item
        >
        <el-form-item v-if="selectedType === 'flash'" label="项目"
          ><el-select
            v-model="form.project"
            :disabled="!!editing"
            @change="
              remoteProducts = [];
              form.remoteProductId = '';
            "
            ><el-option label="闪动校园" value="sdxy" /><el-option
              label="运动世界校园"
              value="ydsjxy" /><el-option
              label="校步点"
              value="xbd" /></el-select
        ></el-form-item>
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
              :disabled="!form.providerId || !!editing"
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
          :label="`销售单价（${selectedType === 'sxdk_tw' ? '元/服务日' : selectedType === 'wuxin' || (selectedType === 'flash' && form.project === 'sdxy') ? '元/次' : '元/公里'}，最多六位小数）`"
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
              placeholder="填写合同成本，不是原插件的默认值"
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
import { computed, ref, onBeforeUnmount } from "vue";
import { ElMessage } from "element-plus";
import request from "@/utils/request";
import { listServiceProducts, saveServiceProduct } from "@/api/serviceCommerce";
import { fetchPluginCatalog } from "@/api/pluginIntegration";
import { internshipProjects } from "@/utils/internshipServices";
import { serviceNames, nativeProductSupported } from "@/utils/serviceCommerce";
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
  providerPage = ref(1),
  providerTotal = ref(0),
  providerKeyword = ref("");
let providerRequest = 0,
  searchTimer;
const selectedType = computed(
  () =>
    providers.value.find((p) => p.id === form.value.providerId)?.providerType ||
    editing.value?.providerType ||
    "",
);
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const r = await listServiceProducts(
      { page: page.value, pageSize: 20 },
      true,
    );
    items.value = r.records;
    total.value = r.total;
  } catch {
    error.value =
      "服务商城未启用或读取失败，请联系系统管理员完成必要配置。";
  } finally {
    loading.value = false;
  }
}
async function loadProviders(reset = true) {
  const token = ++providerRequest,
    next = reset ? 1 : providerPage.value + 1;
  providersLoading.value = true;
  const selected = providers.value.find((p) => p.id === form.value.providerId);
  try {
    const r = await request.get("/admin/api-providers", {
      params: {
        page: next,
        pageSize: 50,
        status: 1,
        keyword: providerKeyword.value || undefined,
      },
    });
    if (token !== providerRequest) return;
    const rows = r.data.records.filter((p) => serviceNames[p.providerType]);
    providers.value = [
      ...new Map(
        [
          ...(reset ? [] : providers.value),
          ...rows,
          ...(selected ? [selected] : []),
        ].map((p) => [p.id, p]),
      ).values(),
    ];
    providerPage.value = next;
    providerTotal.value = r.data.total;
  } catch {
  } finally {
    if (token === providerRequest) providersLoading.value = false;
  }
}
function searchProviders(keyword) {
  providerKeyword.value = keyword;
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => loadProviders(true), 250);
}
function open(row) {
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
  dialog.value = true;
  loadProviders();
}
function providerChanged() {
  if (selectedType.value === "sxdk_tw") {
    form.value.project = "zxjy";
    internshipProjectChanged();
    return;
  }
  form.value.project = ["flash", "wuxin"].includes(selectedType.value)
    ? "sdxy"
    : selectedType.value === "ssbenz_xbd" ? "xbd" : "default";
  form.value.remoteProductId = "";
  remoteProducts.value = [];
}
function internshipProjectChanged() {
  form.value.remoteProductId = form.value.project;
  form.value.title = `实习 · ${internshipProjects[form.value.project]}`;
  remoteProducts.value = [];
}
async function readCatalog() {
  reading.value = true;
  const providerId = form.value.providerId,
    project = form.value.project,
    type = selectedType.value;
  try {
    const r = await fetchPluginCatalog(
      { flash: "P01", heisha: "P03", jiguang: "P04", wuxin: "P10", ssbenz_xbd: "P05" }[type],
      providerId,
      ["flash", "ssbenz_xbd"].includes(type) ? project : null,
    );
    if (form.value.providerId !== providerId || form.value.project !== project)
      return;
    remoteProducts.value = r.data.filter((p) =>
      nativeProductSupported(type, project, p.id),
    );
    if (!remoteProducts.value.length)
      ElMessage.warning("没有可下单的商品");
  } catch {
  } finally {
    reading.value = false;
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
onBeforeUnmount(() => {
  providerRequest++;
  clearTimeout(searchTimer);
});
load();
</script>
<style scoped>
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
