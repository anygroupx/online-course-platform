<template>
  <div class="orders-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>订单管理</span>
          <div class="header-actions">
            <el-button type="primary" @click="handleCreate">
              <el-icon><Plus /></el-icon>
              新建订单
            </el-button>
          </div>
        </div>
      </template>

      <!-- 筛选器 -->
      <EnterpriseFilter
        v-model="filterModel"
        :config="filterConfig"
        :options="{ platformList, orderStatusOptions }"
        :loading="loading"
        storage-key="orders"
        :enable-storage="true"
        @search="handleSearch"
        @reset="handleReset"
      />

      <!-- 批量操作 -->
      <TableBatchActions
        :selected-rows="selectedRows"
        :selection-count="selectedRows.length"
        :actions="batchActionsConfig"
        @action="handleBatchAction"
        @clear-selection="clearSelection"
      />

      <!-- 表格 -->
      <EnterpriseTable
        ref="tableRef"
        :columns="columnsConfig"
        :data="tableData"
        :loading="loading"
        :row-actions="rowActionsConfig"
        :pagination="{
          currentPage,
          pageSize,
          total,
        }"
        :default-sort="defaultSort"
        :selectable="true"
        row-key="id"
        storage-key="orders"
        :enable-storage="true"
        :enable-column-manage="true"
        card-title-key="orderNo"
        card-badge-key="orderStatus"
        :mobile-columns="mobileColumns"
        @selection-change="handleSelectionChange"
        @action="handleRowAction"
        @page-change="handlePageChange"
      >
        <!-- 自定义金额列显示 -->
        <template #column-amount="{ row }">
          <span style="color: var(--color-danger)">¥{{ row.amount }}</span>
        </template>
      </EnterpriseTable>
    </el-card>

    <!-- 创建订单对话框 -->
    <el-dialog
      v-model="createDialogVisible"
      title="创建订单"
      width="600px"
      append-to-body
    >
      <el-form :model="createForm" label-width="120px">
        <el-form-item label="课程平台">
          <el-select
            v-model="createForm.platformId"
            placeholder="请选择课程平台"
            style="width: 100%"
            @change="handlePlatformChange"
          >
            <el-option
              v-for="platform in platformList"
              :key="platform.id"
              :label="`${platform.name}（基础价格：${platform.basePrice}元）`"
              :value="platform.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="学校名称">
          <el-input
            v-model="createForm.schoolName"
            placeholder="请输入学校名称"
          />
        </el-form-item>
        <el-form-item label="学生姓名">
          <el-input
            v-model="createForm.studentName"
            placeholder="请输入学生姓名"
          />
        </el-form-item>
        <el-form-item label="学生账号">
          <el-input
            v-model="createForm.studentAccount"
            placeholder="请输入学生账号"
            @input="handleAccountInput"
          />
        </el-form-item>
        <el-form-item label="学生密码">
          <div style="display: flex; align-items: center; gap: 10px">
            <el-input
              v-model="createForm.studentPassword"
              type="password"
              placeholder="请输入学生密码"
              style="flex: 1"
            />
            <el-button
              v-if="
                currentPlatform &&
                currentPlatform.passwordEnabled &&
                currentPlatform.passwordRule
              "
              size="small"
              type="primary"
              @click="generatePassword"
            >
              自动生成
            </el-button>
          </div>
          <div
            v-if="
              currentPlatform &&
              currentPlatform.passwordEnabled &&
              currentPlatform.passwordRule
            "
            style="
              font-size: 12px;
              color: var(--text-secondary);
              margin-top: 5px;
            "
          >
            规则：{{
              currentPlatform.passwordRule.replace("{account}", "账号")
            }}
          </div>
        </el-form-item>
        <el-form-item label="课程ID">
          <el-input
            v-model="createForm.courseId"
            placeholder="请输入课程ID（可选）"
          />
        </el-form-item>
        <el-form-item label="课程名称">
          <el-input
            v-model="createForm.courseName"
            placeholder="请输入课程名称"
          />
        </el-form-item>
        <el-form-item label="秒刷">
          <el-switch v-model="createForm.isFastMode" />
          <span
            style="
              font-size: 12px;
              color: var(--text-secondary);
              margin-left: 10px;
            "
            >开启秒刷可能额外收费</span
          >
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导出对话框 -->
    <el-dialog
      v-model="exportDialogVisible"
      title="导出订单"
      width="500px"
      append-to-body
    >
      <el-form :model="exportForm" label-width="120px">
        <el-form-item label="导出格式">
          <el-select
            v-model="exportForm.format"
            placeholder="选择导出格式"
            style="width: 100%"
          >
            <el-option label="格式1：学校+账号+密码+课程名字" :value="1" />
            <el-option label="格式2：账号+密码+课程名字" :value="2" />
            <el-option label="格式3：学校+账号+密码" :value="3" />
            <el-option label="格式4：账号+密码" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件格式">
          <el-radio-group v-model="exportForm.fileType">
            <el-radio label="txt">TXT格式</el-radio>
            <el-radio label="xlsx">XLSX格式(Excel)</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="已选择">
          <el-tag type="success">{{ selectedRows.length }} 个订单</el-tag>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exportDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="handleExportConfirm"
          :loading="exportLoading"
        >
          <el-icon><Download /></el-icon>
          导出
        </el-button>
      </template>
    </el-dialog>

    <!-- 订单详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="订单详情"
      width="700px"
      append-to-body
    >
      <el-descriptions :column="isMobile ? 1 : 2" border v-if="currentOrder">
        <el-descriptions-item label="订单编号">{{
          currentOrder.orderNo
        }}</el-descriptions-item>
        <el-descriptions-item label="平台名称">{{
          currentOrder.platformName
        }}</el-descriptions-item>
        <el-descriptions-item label="学校名称">{{
          currentOrder.schoolName
        }}</el-descriptions-item>
        <el-descriptions-item label="学生姓名">{{
          currentOrder.studentName
        }}</el-descriptions-item>
        <el-descriptions-item label="学生账号">{{
          currentOrder.studentAccount
        }}</el-descriptions-item>
        <el-descriptions-item label="课程名称" :span="2">{{
          currentOrder.courseName
        }}</el-descriptions-item>
        <el-descriptions-item label="订单金额">
          <span style="color: var(--color-danger)"
            >¥{{ currentOrder.amount }}</span
          >
        </el-descriptions-item>
        <el-descriptions-item label="订单状态">
          <el-tag :type="getStatusType(currentOrder.orderStatus)">
            {{ getStatusText(currentOrder.orderStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="对接状态">
          <el-tag :type="getDockStatusType(currentOrder.dockStatus)">
            {{ getDockStatusText(currentOrder.dockStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="完成进度">{{
          currentOrder.progress || "0%"
        }}</el-descriptions-item>
        <el-descriptions-item label="补单次数"
          >{{ currentOrder.retryCount }}/5</el-descriptions-item
        >
        <el-descriptions-item label="是否秒刷">
          <el-tag :type="currentOrder.isFastMode === 1 ? 'success' : 'info'">
            {{ currentOrder.isFastMode === 1 ? "是" : "否" }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="课程开始">{{
          currentOrder.courseStartTime || "-"
        }}</el-descriptions-item>
        <el-descriptions-item label="课程结束">{{
          currentOrder.courseEndTime || "-"
        }}</el-descriptions-item>
        <el-descriptions-item label="考试开始">{{
          currentOrder.examStartTime || "-"
        }}</el-descriptions-item>
        <el-descriptions-item label="考试结束">{{
          currentOrder.examEndTime || "-"
        }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{
          currentOrder.createTime
        }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{
          currentOrder.remarks || "-"
        }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { Plus, Download } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { useTableComposition } from "@/composables/useTableComposition";
import { useResponsive } from "@/composables/useResponsive";
import EnterpriseFilter from "@/components/EnterpriseFilter.vue";
import TableBatchActions from "@/components/TableBatchActions.vue";
import EnterpriseTable from "@/components/EnterpriseTable.vue";
import StatusDisplay from "@/components/StatusDisplay.vue";
import {
  filterConfig,
  columnsConfig,
  rowActionsConfig,
  batchActionsConfig,
  mobileColumns,
  defaultSort,
} from "@/config/ordersConfig";
import {
  queryOrders,
  createOrder,
  cancelOrder,
  retryOrder,
  refreshOrder,
  exportOrders,
} from "@/api/order";
import { getCoursePlatforms } from "@/api/course";
import { useVariableStore } from "@/stores/variableStore";

// 变量store
const variableStore = useVariableStore();
const { isMobile } = useResponsive();

// 使用表格组合式函数统一管理状态
const {
  currentPage,
  pageSize,
  total,
  filters,
  selectedRows,
  loading,
  tableData,
  handleSelectionChange,
  handlePageChange,
  handleFilterChange,
  handleResetFilters,
  loadData,
  clearSelection,
} = useTableComposition({
  storageKey: "orders",
  initialFilters: {
    orderNo: "",
    platformId: null,
    studentAccount: "",
    orderStatus: null,
  },
  pageSize: 10,
  columns: columnsConfig,
});

// Refs
const tableRef = ref(null);

// 对话框状态
const createDialogVisible = ref(false);
const detailDialogVisible = ref(false);
const exportDialogVisible = ref(false);

// 数据源
const platformList = ref([]);
const currentOrder = ref(null);
const exportLoading = ref(false);
const exportForm = ref({
  format: 1,
  exportFileType: "txt",
});
const currentPlatform = ref(null);

// 筛选模型（使用组合式函数的 filters）
const filterModel = computed({
  get: () => filters.value,
  set: (val) => handleFilterChange(val),
});

// 订单状态选项（从 variableStore 获取）
const orderStatusOptions = computed(() => {
  return variableStore.getStatusOptions("order_status");
});

// 创建订单表单
const createForm = ref({
  platformId: null,
  schoolName: "",
  studentName: "",
  studentAccount: "",
  studentPassword: "",
  courseId: "",
  courseName: "",
  isFastMode: false,
});

// 数据加载
const loadOrders = async () => {
  try {
    await loadData(async (params) => {
      const res = await queryOrders(params);
      if (res.code === 1) {
        return {
          data: res.data.records || [],
          total: res.data.total || 0,
        };
      }
      throw new Error(res.msg || "加载失败");
    });
  } catch (error) {
    console.error("加载订单失败:", error);
    ElMessage.error("加载订单失败");
  }
};

const loadPlatforms = async () => {
  try {
    const res = await getCoursePlatforms();
    if (res.code === 1) {
      platformList.value = res.data;
    }
  } catch (error) {
    console.error("加载平台列表失败:", error);
  }
};

// 事件处理
const handleSearch = () => {
  currentPage.value = 1;
  loadOrders();
};

const handleReset = () => {
  handleResetFilters();
  loadOrders();
};

const handleCreate = () => {
  createForm.value = {
    platformId: null,
    schoolName: "",
    studentName: "",
    studentAccount: "",
    studentPassword: "",
    courseId: "",
    courseName: "",
    isFastMode: false,
  };
  currentPlatform.value = null; // 重置当前平台
  createDialogVisible.value = true;
};

// 处理平台选择变化
const handlePlatformChange = (platformId) => {
  currentPlatform.value = platformList.value.find((p) => p.id === platformId);
  // 如果平台支持自动生成密码且有账号，自动生成密码
  if (
    currentPlatform.value &&
    currentPlatform.value.passwordEnabled &&
    currentPlatform.value.passwordRule &&
    createForm.value.studentAccount
  ) {
    generatePassword();
  }
};

// 处理账号输入
const handleAccountInput = () => {
  // 如果平台支持自动生成密码，自动生成密码
  if (
    currentPlatform.value &&
    currentPlatform.value.passwordEnabled &&
    currentPlatform.value.passwordRule &&
    createForm.value.studentAccount
  ) {
    generatePassword();
  }
};

// 生成密码
const generatePassword = () => {
  if (
    !currentPlatform.value ||
    !currentPlatform.value.passwordRule ||
    !createForm.value.studentAccount
  ) {
    return;
  }

  // 使用密码规则生成密码，{account}替换为实际账号
  const password = currentPlatform.value.passwordRule.replace(
    "{account}",
    createForm.value.studentAccount
  );
  createForm.value.studentPassword = password;
};

const handleCreateSubmit = async () => {
  if (!createForm.value.platformId) {
    ElMessage.warning("请选择课程平台");
    return;
  }
  if (!createForm.value.studentAccount || !createForm.value.studentPassword) {
    ElMessage.warning("请输入学生账号和密码");
    return;
  }
  if (!createForm.value.courseName) {
    ElMessage.warning("请输入课程名称");
    return;
  }

  try {
    await createOrder(createForm.value);
    ElMessage.success("订单创建成功");
    createDialogVisible.value = false;
    loadOrders();
  } catch (error) {
    console.error("创建订单失败：", error);
  }
};

// 行操作处理
const handleRowAction = async ({ action, row }) => {
  switch (action) {
    case "view":
      handleView(row);
      break;
    case "retry":
      await handleRetry(row);
      break;
    case "refresh":
      await handleRefresh(row);
      break;
    case "cancel":
      await handleCancel(row);
      break;
  }
};

const handleView = (row) => {
  currentOrder.value = row;
  detailDialogVisible.value = true;
};

const handleRetry = async (row) => {
  if (row.retryCount >= 5) {
    ElMessage.warning("该订单补刷已超过5次");
    return;
  }

  try {
    await ElMessageBox.confirm("确定要补单吗？", "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });

    await retryOrder(row.orderNo);
    ElMessage.success("补单成功");
    loadOrders();
  } catch (error) {
    if (error !== "cancel") {
      console.error("补单失败:", error);
    }
  }
};

const handleRefresh = async (row) => {
  try {
    await refreshOrder(row.orderNo);
    ElMessage.success("进度刷新成功");
    loadOrders();
  } catch (error) {
    console.error("刷新失败:", error);
  }
};

const handleCancel = async (row) => {
  if (row.orderStatus !== 0) {
    ElMessage.warning("只能取消待处理的订单");
    return;
  }

  try {
    await ElMessageBox.confirm("取消订单将退回余额，确定要取消吗？", "警告", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });

    await cancelOrder(row.orderNo);
    ElMessage.success("订单取消成功");
    loadOrders();
  } catch (error) {
    if (error !== "cancel") {
      console.error("取消订单失败:", error);
    }
  }
};

// 批量操作处理
const handleBatchAction = async ({ action }) => {
  if (action === "export") {
    exportDialogVisible.value = true;
  }
};

const handleExportConfirm = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning("请先选择要导出的订单");
    return;
  }

  const orderIds = selectedRows.value.map((order) => order.id);

  exportLoading.value = true;
  try {
    const response = await exportOrders({
      orderIds,
      format: exportForm.value.format,
      fileType: exportForm.value.fileType,
    });

    if (exportForm.value.fileType === "xlsx") {
      // XLSX格式 - 直接下载文件
      // 检查 response 是否为错误 JSON
      let isError = false;
      let errorMsg = "";
      // response 可能是 Blob, ArrayBuffer, 或字符串
      if (response instanceof Blob) {
        // 尝试读取 Blob 内容为文本并解析为 JSON
        const text = await response.text();
        try {
          const json = JSON.parse(text);
          if (
            json &&
            typeof json === "object" &&
            (json.message || json.error)
          ) {
            isError = true;
            errorMsg = json.message || json.error || "导出失败";
          }
        } catch (e) {
          // 不是 JSON，说明是正常的 XLSX 文件
        }
      } else if (typeof response === "string") {
        try {
          const json = JSON.parse(response);
          if (
            json &&
            typeof json === "object" &&
            (json.message || json.error)
          ) {
            isError = true;
            errorMsg = json.message || json.error || "导出失败";
          }
        } catch (e) {
          // 不是 JSON，说明是正常的 XLSX 文件
        }
      }
      if (isError) {
        ElMessage.error(errorMsg);
        exportDialogVisible.value = false;
        exportLoading.value = false;
        return;
      }
      // 如果不是错误，正常下载
      const blob =
        response instanceof Blob
          ? response
          : new Blob([response], {
              type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            });
      const link = document.createElement("a");
      link.href = window.URL.createObjectURL(blob);
      const timestamp = new Date()
        .toISOString()
        .replace(/[-:]/g, "")
        .replace(/T/, "_")
        .split(".")[0];
      link.download = `订单导出_${timestamp}.xlsx`;
      link.click();
      window.URL.revokeObjectURL(link.href);

      ElMessage.success({
        message: `成功导出 ${orderIds.length} 个订单（XLSX格式）`,
        duration: 3000,
      });
      exportDialogVisible.value = false;
    } else {
      // TXT格式 - 复制到剪贴板
      if (response.code === 1 && response.data && response.data.content) {
        navigator.clipboard
          .writeText(response.data.content)
          .then(() => {
            ElMessage.success(`已导出${orderIds.length}个订单到剪贴板`);
            exportDialogVisible.value = false;
          })
          .catch(() => {
            // 如果复制失败，显示在对话框中
            ElMessageBox.alert(response.data.content, "导出内容", {
              confirmButtonText: "关闭",
              dangerouslyUseHTMLString: false,
            });
            exportDialogVisible.value = false;
          });
      } else {
        ElMessage.error(response.message || "导出失败");
      }
    }
  } catch (error) {
    console.error("导出失败:", error);
    ElMessage.error("导出失败: " + (error.message || "未知错误"));
  } finally {
    exportLoading.value = false;
  }
};

// 工具方法
const getStatusType = (status) => {
  return variableStore.getVariableTagType("order_status", status);
};

const getStatusText = (status) => {
  return variableStore.getVariableName("order_status", status);
};

const getDockStatusType = (status) => {
  return variableStore.getVariableTagType("dock_status", status);
};

const getDockStatusText = (status) => {
  return variableStore.getVariableName("dock_status", status);
};

// 企业方案：监听分页变化自动加载数据
// Source: AURA-X-KYS 响应式数据加载模式
watch(
  [currentPage, pageSize],
  () => {
    loadOrders();
  },
  { immediate: false } // 避免初始化时重复加载
);

// 企业方案：监听分页变化自动加载数据
watch([currentPage, pageSize], () => {
  loadOrders();
});

// 生命周期
onMounted(async () => {
  await variableStore.loadAllVariables();
  await loadPlatforms();
  await loadOrders();
});
</script>

<style scoped>
.orders-page {
  padding: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-actions {
  display: flex;
  gap: 10px;
}

@media (max-width: 768px) {
  .orders-page {
    padding: 12px;
  }

  .header-actions {
    flex-direction: column;
    width: 100%;
  }

  .header-actions .el-button {
    width: 100%;
  }
}
</style>
