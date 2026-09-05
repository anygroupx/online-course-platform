<template>
  <div class="api-providers-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>第三方接口管理</span>
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon>
            添加接口
          </el-button>
        </div>
      </template>

      <el-alert
        class="provider-notice"
        title="新增 HTTPS 接口无需配置域名白名单。保存后请测试连接，再启用；地址、类型或凭据变更后需要重新验证。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-table v-loading="tableLoading" :data="tableData" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="接口名称" width="150" />
        <el-table-column prop="providerType" label="接口类型" width="120" />
        <el-table-column prop="apiUrl" label="API地址" min-width="180" show-overflow-tooltip />
        <el-table-column prop="usernameMasked" label="账号" width="120" />
        <el-table-column prop="balance" label="余额" width="170">
          <template #default="scope">
            <div class="balance-cell">
              <el-tag type="success">¥{{ formatBalance(scope.row.balance) }}</el-tag>
              <el-button
                link
                type="primary"
                :loading="balanceLoadingIds.has(scope.row.id)"
                :disabled="scope.row.status !== 1"
                @click="handleRefreshBalance(scope.row)"
              >
                查询余额
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : scope.row.status === 0 ? 'info' : 'warning'">
              {{ providerStatusLabel(scope.row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="连接验证 / 健康" width="180">
          <template #default="scope">
            <el-tooltip :content="verificationDetails(scope.row)" placement="top">
              <div class="verification-cell">
                <span :class="{ 'check-failed': scope.row.lastCheckReason && scope.row.lastCheckReason !== 'SUCCESS' }">
                  {{ providerCheckLabel(scope.row.lastCheckReason) }}
                </span>
                <small>{{ scope.row.verifiedAt ? formatDateTime(scope.row.verifiedAt) : '尚未人工验证' }}</small>
              </div>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="lastSyncTime" label="上次同步" width="160">
          <template #default="scope">
            <span
              v-if="scope.row.lastSyncTime"
              style="font-size: 12px; color: var(--text-regular)"
            >
              {{ formatTime(scope.row.lastSyncTime) }}
            </span>
            <span v-else style="color: var(--text-placeholder)">-</span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          :width="isMobile ? 145 : 400"
          fixed="right"
        >
          <template #default="scope">
            <div class="provider-actions">
            <el-button size="small" @click="handleEdit(scope.row)" :disabled="statusLoadingIds.has(scope.row.id)"
              >编辑</el-button
            >
            <el-button size="small" type="primary" plain
              :loading="testLoadingIds.has(scope.row.id)"
              :disabled="statusLoadingIds.has(scope.row.id)"
              @click="handleConnectionTest(scope.row)">
              测试连接
            </el-button>
            <el-button size="small" :type="scope.row.status === 1 ? 'warning' : 'success'"
              :loading="statusLoadingIds.has(scope.row.id)"
              :disabled="scope.row.status !== 1 && (!canEnableProvider(scope.row) || testLoadingIds.has(scope.row.id))"
              @click="handleToggleStatus(scope.row)">
              {{ scope.row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button
              size="small"
              type="success"
              :disabled="scope.row.status !== 1"
              @click="handleBatchSync(scope.row)"
            >
              <el-icon><Refresh /></el-icon>
              批量同步
            </el-button>
            <el-button
              size="small"
              type="danger"
              @click="handleDelete(scope.row)"
              >删除</el-button
            >
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next"
        class="pagination"
      />
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      :width="isMobile ? '94%' : '700px'"
      append-to-body
      :close-on-click-modal="!submitting"
      :close-on-press-escape="!submitting"
      :show-close="!submitting"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
        <el-form-item label="接口名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入接口名称" />
        </el-form-item>
        <el-form-item label="接口类型" prop="providerType">
          <el-select v-model="form.providerType" placeholder="请选择接口类型">
            <el-option label="27平台 (Benz)" value="27" />
            <el-option label="Oligei (2022)" value="oligei" />
            <el-option label="Daytime（推荐）" value="Daytime" />
            <el-option label="29同系统（兼容）" value="29" />
            <el-option label="暗网 (yjdj)" value="yjdj" />
            <el-option label="Ikun" value="ikun" />
          </el-select>
        </el-form-item>
        <el-form-item label="API地址" prop="apiUrl">
          <el-input v-model="form.apiUrl" placeholder="https://provider.example.com 或 /openapi 基础目录" maxlength="2048" />
          <div class="field-help">默认仅允许 HTTPS 公网域名，不接受 IP、查询参数或片段。Daytime / 29 兼容以 /api.php 结尾的地址。</div>
          <div class="field-help">HTTP 和非默认端口仍需运维显式放行；修改地址、类型或重新填写凭据后会清除旧验证，不能直接保持启用。</div>
        </el-form-item>
        <el-form-item label="账号">
          <el-input
            v-model="form.username"
            :placeholder="form.id && form.hasUsername ? '留空则保持原账号' : '请输入账号'"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="form.id && form.hasPassword ? '已配置，留空则保持不变' : '请输入密码'"
            show-password
          />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="form.id && form.hasApiKey ? '已配置，留空则保持不变' : '请输入API Key'"
          />
        </el-form-item>
        <el-form-item label="Token">
          <el-input
            v-model="form.token"
            type="textarea"
            :rows="2"
            :placeholder="form.id && form.hasToken ? '已配置，留空则保持不变' : '请输入Token'"
          />
        </el-form-item>
        <el-form-item label="Cookie">
          <el-input
            v-model="form.cookie"
            type="textarea"
            :rows="3"
            :placeholder="form.id && form.hasCookie ? '已配置，留空则保持不变' : '请输入Cookie'"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio v-if="form.id && originalStatus === 1" :label="1">保持启用（配置变更后需重验）</el-radio>
            <el-radio :label="2">待验证 / 待启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false" :disabled="submitting">取消</el-button>
        <el-button :loading="submitting" @click="handleSubmit(false)">保存</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit(true)">保存并测试</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="testResultVisible" :title="connectionResult?.success ? '连接测试通过' : '连接测试失败'"
      :width="isMobile ? '94%' : '560px'" append-to-body>
      <template v-if="connectionResult">
        <el-alert :type="connectionResult.success ? 'success' : 'error'" :title="connectionResult.message"
          :closable="false" show-icon />
        <dl class="test-details">
          <dt>接口</dt><dd>{{ connectionResult.name }}</dd>
          <template v-if="connectionResult.success">
            <dt>规范化地址</dt><dd>{{ connectionResult.apiUrl }}</dd>
            <dt>测试耗时</dt><dd>{{ connectionResult.durationMs }} ms</dd>
            <dt>验证人 ID</dt><dd>{{ connectionResult.verifiedBy }}</dd>
          </template>
          <template v-else>
            <dt>错误分类</dt><dd>{{ connectionResult.reason || 'REQUEST_FAILED' }}</dd>
            <dt v-if="connectionResult.errorId">追踪 ID</dt><dd v-if="connectionResult.errorId">{{ connectionResult.errorId }}</dd>
          </template>
        </dl>
        <p class="field-help">连接测试仅查询余额或商品目录，不下单或补单，也不会自动启用接口。测试通过后可在列表中点击“启用”。</p>
      </template>
      <template #footer><el-button @click="testResultVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from "vue";
import { Plus, Refresh } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import axios from "@/utils/request";
import { useResponsive } from "@/composables/useResponsive";
import dayjs from "dayjs";
import { refreshApiProviderBalance, testApiProviderConnection, updateApiProviderStatus } from "@/api/apiProvider";
import { providerStatusLabel, canEnableProvider, providerCheckLabel } from "@/utils/providerStatus";

const { isMobile } = useResponsive();

const tableData = ref([]);
const tableLoading = ref(false);
const balanceLoadingIds = ref(new Set());
const testLoadingIds = ref(new Set());
const statusLoadingIds = ref(new Set());
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);
const dialogVisible = ref(false);
const dialogTitle = ref("添加接口");
const formRef = ref();
const submitting = ref(false);
const originalStatus = ref(2);
const testResultVisible = ref(false);
const connectionResult = ref(null);
const emptyForm = () => ({
  name: "", providerType: "Daytime", apiUrl: "", username: "", password: "",
  apiKey: "", token: "", cookie: "", status: 2,
});
const form = ref(emptyForm());
const formRules = {
  name: [{ required: true, whitespace: true, message: "请输入接口名称", trigger: "blur" }],
  providerType: [{ required: true, message: "请选择接口类型", trigger: "change" }],
  apiUrl: [{ required: true, whitespace: true, message: "请输入 API 基础地址", trigger: "blur" }],
};

const setBusy = (state, id, busy) => {
  const next = new Set(state.value);
  if (busy) next.add(id); else next.delete(id);
  state.value = next;
};

let loadSequence = 0;
const loadData = async () => {
  const sequence = ++loadSequence;
  tableLoading.value = true;
  try {
    const res = await axios.get("/admin/api-providers", {
      params: {
        page: currentPage.value,
        pageSize: pageSize.value,
      },
    });
    if (res.code === 1 && sequence === loadSequence) {
      tableData.value = res.data.records;
      total.value = res.data.total;
    }
  } catch {
    // The shared request interceptor displays a safe error. Never log Axios configs with credentials.
  } finally {
    if (sequence === loadSequence) tableLoading.value = false;
  }
};

const handleCreate = () => {
  dialogTitle.value = "添加接口";
  form.value = emptyForm();
  originalStatus.value = 2;
  formRef.value?.clearValidate();
  dialogVisible.value = true;
};

const handleEdit = (row) => {
  dialogTitle.value = "编辑接口";
  originalStatus.value = row.status;
  formRef.value?.clearValidate();
  form.value = {
    id: row.id,
    name: row.name,
    providerType: row.providerType,
    apiUrl: row.apiUrl,
    username: "",
    password: "",
    apiKey: "",
    token: "",
    cookie: "",
    status: row.status,
    hasUsername: Boolean(row.usernameMasked),
    hasPassword: row.hasPassword,
    hasApiKey: row.hasApiKey,
    hasToken: row.hasToken,
    hasCookie: row.hasCookie,
  };
  dialogVisible.value = true;
};

const handleSubmit = async (testAfterSave = false) => {
  if (submitting.value || !(await formRef.value?.validate().catch(() => false))) return;
  submitting.value = true;
  try {
    const payload = {
      id: form.value.id, name: form.value.name, providerType: form.value.providerType,
      apiUrl: form.value.apiUrl, username: form.value.username, password: form.value.password,
      apiKey: form.value.apiKey, token: form.value.token, cookie: form.value.cookie, status: form.value.status,
    };
    const res = payload.id
      ? await axios.put("/admin/api-providers", payload)
      : await axios.post("/admin/api-providers", payload);
    const id = payload.id || res.data;
    const name = payload.name;
    ElMessage.success(testAfterSave ? "配置已保存，正在测试连接" : "配置已保存；目标或凭据有变更时，请重新测试后启用");
    dialogVisible.value = false;
    form.value = emptyForm();
    await loadData();
    if (testAfterSave) await handleConnectionTest({ id, name });
  } catch {
    // Request errors are already displayed without dumping submitted secrets to the console.
  } finally {
    submitting.value = false;
  }
};

const handleConnectionTest = async (row) => {
  if (testLoadingIds.value.has(row.id)) return;
  setBusy(testLoadingIds, row.id, true);
  try {
    const res = await testApiProviderConnection(row.id);
    connectionResult.value = { ...res.data, name: row.name, success: true, message: res.message };
  } catch (error) {
    const body = error.response?.data;
    connectionResult.value = {
      name: row.name, success: false,
      message: body?.message || "连接测试未完成，请稍后重试",
      reason: body?.data?.reason, errorId: body?.errorId,
    };
  } finally {
    setBusy(testLoadingIds, row.id, false);
    testResultVisible.value = true;
    await loadData();
  }
};

const handleToggleStatus = async (row) => {
  const status = row.status === 1 ? 0 : 1;
  setBusy(statusLoadingIds, row.id, true);
  try {
    await updateApiProviderStatus(row.id, status);
    ElMessage.success(status === 1 ? "接口已启用" : "接口已停用");
    await loadData();
  } catch {
    // The backend rechecks verification and configuration version; UI state is not authorization.
  } finally {
    setBusy(statusLoadingIds, row.id, false);
  }
};

const formatDateTime = (value) => value ? dayjs(value).format("YYYY-MM-DD HH:mm:ss") : "未验证";
const verificationDetails = (row) => [
  `最近人工验证：${formatDateTime(row.verifiedAt)}`,
  row.verifiedBy ? `验证人 ID：${row.verifiedBy}` : null,
  row.checkedAt ? `最近检查：${formatDateTime(row.checkedAt)}` : null,
  row.lastCheckErrorId ? `追踪 ID：${row.lastCheckErrorId}` : null,
].filter(Boolean).join("；");

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm("确定要删除这个接口吗？", "警告", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });

    await axios.delete(`/admin/api-providers/${row.id}`);
    ElMessage.success("删除成功");
    loadData();
  } catch (error) {
    // Cancellation and safe request errors need no console output.
  }
};

const formatBalance = (value) => {
  const balance = Number(value);
  return Number.isFinite(balance) ? balance.toFixed(2) : "0.00";
};

const handleRefreshBalance = async (row) => {
  balanceLoadingIds.value = new Set([...balanceLoadingIds.value, row.id]);
  try {
    const res = await refreshApiProviderBalance(row.id);
    row.balance = res.data;
    ElMessage.success(`${row.name} 余额已更新`);
  } catch (error) {
    // The shared request interceptor displays the safe failure classification.
  } finally {
    const next = new Set(balanceLoadingIds.value);
    next.delete(row.id);
    balanceLoadingIds.value = next;
  }
};

const handleBatchSync = async (row) => {
  let loading;
  try {
    await ElMessageBox.confirm(
      `确定要批量同步 ${row.name} 的订单进度吗？将使用增量同步，只获取有更新的订单。`,
      "批量同步确认",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "info",
      }
    );

    loading = ElMessage({
      message: "正在同步中，请稍候...",
      type: "info",
      duration: 0,
    });

    const res = await axios.post("/admin/docking/batch-sync", null, {
      params: {
        apiProviderId: row.id,
        offset: 0,
      },
    });

    loading.close();

    if (res.code === 1) {
      ElMessage.success(
        `同步完成！共同步 ${res.data.syncedCount} 条，更新 ${res.data.updatedCount} 条，未找到 ${res.data.notFoundCount} 条`
      );
      loadData();
    }
  } catch (error) {
    if (error !== "cancel") {
      // The shared request interceptor already reports errors.
    }
  } finally {
    loading?.close();
  }
};

const formatTime = (timestamp) => {
  if (!timestamp) return "";
  return dayjs.unix(timestamp).format("YYYY-MM-DD HH:mm:ss");
};

watch([currentPage, pageSize], loadData);

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.api-providers-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}

.balance-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.provider-notice { margin-bottom: 16px; }
.provider-actions { display: flex; flex-wrap: wrap; gap: 6px; }
.provider-actions .el-button { margin-left: 0; }
.verification-cell { display: flex; flex-direction: column; font-size: 12px; }
.verification-cell small, .field-help { color: var(--text-secondary, #606266); font-size: 12px; line-height: 1.7; }
.field-help { margin-top: 6px; }
.check-failed { color: var(--el-color-danger); }
.test-details { display: grid; grid-template-columns: 100px 1fr; gap: 12px; }
.test-details dt { color: var(--text-secondary, #606266); }
.test-details dd { margin: 0; overflow-wrap: anywhere; }
</style>
