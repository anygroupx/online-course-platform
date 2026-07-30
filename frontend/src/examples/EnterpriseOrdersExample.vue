<template>
  <div class="enterprise-orders-example">
    <!-- 统计卡片 -->
    <TableStatistics
      :statistics="statisticsData"
      :format-options="{ decimals: 0, thousandsSeparator: true }"
    />

    <!-- 主卡片 -->
    <el-card class="main-card">
      <!-- 筛选器 -->
      <EnterpriseFilter
        v-model="filterModel"
        :config="filterConfig"
        :options="{ platformList, agentList }"
        :loading="loading"
        storage-key="admin_orders"
        :enable-storage="true"
        @search="handleSearch"
        @reset="handleReset"
      />

      <!-- 批量操作工具栏 -->
      <TableBatchActions
        :selected-rows="selectedRows"
        :selection-count="selectedRows.length"
        :actions="batchActionsConfig"
        :mobile-visible-count="2"
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
        :selectable="true"
        row-key="id"
        storage-key="admin_orders"
        :enable-storage="true"
        :enable-column-manage="true"
        card-title-key="orderNo"
        card-badge-key="orderStatus"
        @selection-change="handleSelectionChange"
        @action="handleRowAction"
        @page-change="handlePageChange"
      >
        <!-- 自定义列插槽示例 -->
        <template #column-studentAccount="{ row }">
          <el-input
            v-model="row.studentAccount"
            size="small"
            @blur="handleAccountUpdate(row)"
          />
        </template>

        <!-- 移动端卡片自定义 -->
        <template #mobile-card="{ row }">
          <div class="custom-mobile-card">
            <div class="card-header">
              <span class="order-no">{{ row.orderNo }}</span>
              <StatusDisplay :status="row.orderStatus" type="order_status" />
            </div>
            <div class="card-body">
              <div class="info-item">
                <span class="label">平台：</span>
                <span class="value">{{ row.platformName }}</span>
              </div>
              <div class="info-item">
                <span class="label">课程：</span>
                <span class="value">{{ row.courseName }}</span>
              </div>
              <div class="info-item">
                <span class="label">金额：</span>
                <span class="value price">¥{{ row.amount }}</span>
              </div>
            </div>
          </div>
        </template>
      </EnterpriseTable>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { useTableComposition } from "@/composables/useTableComposition";
import TableStatistics from "@/components/TableStatistics.vue";
import EnterpriseFilter from "@/components/EnterpriseFilter.vue";
import TableBatchActions from "@/components/TableBatchActions.vue";
import EnterpriseTable from "@/components/EnterpriseTable.vue";
import StatusDisplay from "@/components/StatusDisplay.vue";
import {
  statisticsConfig,
  filterConfig,
  columnsConfig,
  rowActionsConfig,
  batchActionsConfig,
} from "@/config/adminOrdersEnterpriseConfig";

// 使用表格组合式函数统一管理状态
const {
  // 分页状态
  currentPage,
  pageSize,
  total,

  // 筛选状态
  filters,

  // 选择状态
  selectedRows,
  hasSelection,
  selectionCount,

  // 数据状态
  loading,
  tableData,

  // 查询参数
  queryParams,

  // 事件处理
  handleSelectionChange,
  handlePageChange,
  handleFilterChange,
  handleResetFilters,

  // 数据操作
  loadData,
  refresh,
  clearSelection,
} = useTableComposition({
  storageKey: "admin_orders",
  initialFilters: {
    orderNo: "",
    platformId: null,
    studentAccount: "",
    orderStatus: null,
    dockStatus: null,
    userId: null,
  },
  pageSize: 10,
  columns: columnsConfig,
});

// Refs
const tableRef = ref(null);

// 筛选模型（使用组合式函数的 filters）
const filterModel = computed({
  get: () => filters.value,
  set: (val) => handleFilterChange(val),
});

// 选项数据
const platformList = ref([]);
const agentList = ref([]);

// 统计数据
const statisticsData = ref(statisticsConfig);

// 生命周期
onMounted(async () => {
  await loadPlatforms();
  await loadAgents();
  await loadOrders();
  await loadStatistics();
});

// 数据加载
const loadOrders = async () => {
  try {
    await loadData(async (params) => {
      const res = await fetch("/api/admin/orders/query-all", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(params),
      });
      const data = await res.json();
      if (data.code === 1) {
        return {
          data: data.data.records || [],
          total: data.data.total || 0,
        };
      }
      throw new Error(data.msg || "加载失败");
    });
  } catch (error) {
    console.error("加载订单失败:", error);
    ElMessage.error("加载订单失败");
  }
};

const loadPlatforms = async () => {
  try {
    const res = await fetch("/api/courses/platforms");
    const data = await res.json();
    if (data.code === 1) {
      platformList.value = data.data || [];
    }
  } catch (error) {
    console.error("加载平台列表失败:", error);
  }
};

const loadAgents = async () => {
  try {
    const res = await fetch("/api/admin/orders/agent-accounts");
    const data = await res.json();
    if (data.code === 1) {
      agentList.value = data.data || [];
    }
  } catch (error) {
    console.error("加载代理列表失败:", error);
  }
};

const loadStatistics = async () => {
  try {
    const res = await fetch("/api/admin/orders/statistics");
    const data = await res.json();
    if (data.code === 1) {
      const stats = data.data;
      statisticsData.value = [
        { ...statisticsConfig[0], value: stats.totalOrders || 0 },
        { ...statisticsConfig[1], value: stats.completedOrders || 0 },
        { ...statisticsConfig[2], value: stats.totalAmount || 0 },
        { ...statisticsConfig[3], value: stats.todayOrders || 0 },
      ];
    }
  } catch (error) {
    console.error("加载统计数据失败:", error);
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

const handleRowAction = async ({ action, row }) => {
  console.log("行操作:", action, row);
  switch (action) {
    case "view":
      ElMessage.info(`查看订单: ${row.orderNo}`);
      break;
    case "edit":
      ElMessage.info(`编辑订单: ${row.orderNo}`);
      break;
    case "retry":
      await handleRetry(row);
      break;
    case "delete":
      await handleDelete(row);
      break;
  }
};

const handleBatchAction = async ({ action, selectedRows }) => {
  console.log("批量操作:", action, selectedRows);
  switch (action) {
    case "complete":
      ElMessage.success(`批量完成 ${selectedRows.length} 个订单`);
      break;
    case "cancel":
      ElMessage.warning(`批量取消 ${selectedRows.length} 个订单`);
      break;
    case "delete":
      ElMessage.error(`批量删除 ${selectedRows.length} 个订单`);
      break;
    case "export":
      ElMessage.info(`导出 ${selectedRows.length} 个订单`);
      break;
  }
};

const handleRetry = async (row) => {
  try {
    ElMessage.success(`补单成功: ${row.orderNo}`);
    await loadOrders();
  } catch (error) {
    ElMessage.error("补单失败");
  }
};

const handleDelete = async (row) => {
  try {
    ElMessage.success(`删除成功: ${row.orderNo}`);
    await loadOrders();
  } catch (error) {
    ElMessage.error("删除失败");
  }
};

const handleAccountUpdate = async (row) => {
  console.log("更新账号:", row.studentAccount);
  ElMessage.success("账号已更新");
};
</script>

<style scoped>
.enterprise-orders-example {
  padding: 20px;
}

.main-card {
  margin-bottom: 20px;
}

/* 移动端卡片自定义样式 */
.custom-mobile-card {
  width: 100%;
}

.custom-mobile-card .card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.order-no {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.custom-mobile-card .card-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-item {
  display: flex;
  font-size: 14px;
}

.info-item .label {
  color: #909399;
  min-width: 60px;
}

.info-item .value {
  color: #606266;
  flex: 1;
}

.info-item .price {
  color: var(--color-warning);
  font-weight: 600;
}

@media (max-width: 768px) {
  .enterprise-orders-example {
    padding: 12px;
  }
}
</style>
