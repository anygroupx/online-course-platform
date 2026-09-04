<!-- AdminOrders.vue 完整配置驱动使用示例 -->

<template>
  <div class="admin-orders-page">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="statistics-cards">
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="stat-content">
            <div class="stat-value">{{ statistics.total || 0 }}</div>
            <div class="stat-label">总订单</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="stat-content">
            <div class="stat-value">{{ statistics.processing || 0 }}</div>
            <div class="stat-label">处理中</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="stat-content">
            <div class="stat-value">{{ statistics.completed || 0 }}</div>
            <div class="stat-label">已完成</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="stat-content">
            <div class="stat-value">{{ statistics.failed || 0 }}</div>
            <div class="stat-label">失败</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 企业级筛选组件 - 配置驱动 -->
    <EnterpriseFilter
      v-model="queryForm"
      :config="filterConfig"
      :options="filterOptions"
      :loading="searchLoading"
      @search="handleSearch"
      @reset="handleResetQuery"
    >
      <!-- 自定义复杂字段：代理账号下拉（带格式化 label） -->
      <template #filter-userUid="{ filter, isMobile }">
        <el-select
          v-model="queryForm.userUid"
          placeholder="选择代理账号"
          clearable
          :style="{ width: isMobile ? '100%' : '150px' }"
        >
          <el-option
            v-for="agent in agentList"
            :key="agent.uid"
            :label="
              agent.username + (agent.nickname ? ` (${agent.nickname})` : '')
            "
            :value="agent.uid"
          />
        </el-select>
      </template>
    </EnterpriseFilter>

    <!-- 企业级表格组件 - 配置驱动 -->
    <EnterpriseTable
      :columns="tableColumns"
      :data="tableData"
      :loading="tableLoading"
      :row-actions="rowActions"
      :pagination="pagination"
      :selectable="true"
      :table-props="tableProps"
      :card-title-key="'orderNo'"
      :mobile-columns="mobileColumns"
      @selection-change="handleSelectionChange"
      @sort-change="handleSortChange"
      @action="handleAction"
      @page-change="handlePageChange"
    >
      <!-- 自定义状态列（如需特殊样式） -->
      <template #column-orderStatus="{ row }">
        <StatusDisplay :status="row.orderStatus" type="order_status" />
      </template>

      <!-- 自定义操作列（如需完全自定义） -->
      <template #actions="{ row }">
        <el-button type="primary" link size="small" @click="handleView(row)">
          查看
        </el-button>
        <el-button
          v-if="row.orderStatus !== 2"
          type="warning"
          link
          size="small"
          @click="handleUpdateStatus(row)"
        >
          状态
        </el-button>
        <el-button type="danger" link size="small" @click="handleDelete(row)">
          删除
        </el-button>
      </template>

      <!-- 移动端卡片自定义 -->
      <template #mobile-card="{ row, index }">
        <div class="custom-mobile-card">
          <div class="card-header">
            <span class="order-no">{{ row.orderNo }}</span>
            <StatusDisplay :status="row.orderStatus" type="order_status" />
          </div>
          <div class="card-info">
            <div class="info-item">
              <span class="label">平台：</span>
              <span class="value">{{ row.platform?.name }}</span>
            </div>
            <div class="info-item">
              <span class="label">课程：</span>
              <span class="value">{{ row.courseName }}</span>
            </div>
            <div class="info-item">
              <span class="label">价格：</span>
              <span class="value price">¥{{ row.price }}</span>
            </div>
          </div>
          <div class="card-actions">
            <el-button size="small" @click="handleView(row)"
              >查看详情</el-button
            >
          </div>
        </div>
      </template>
    </EnterpriseTable>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import EnterpriseFilter from "@/components/EnterpriseFilter.vue";
import EnterpriseTable from "@/components/EnterpriseTable.vue";
import StatusDisplay from "@/components/StatusDisplay.vue";
import {
  adminOrdersFilterConfig,
  defaultQueryForm,
} from "@/config/adminOrdersFilterConfig";
import {
  adminOrdersColumns,
  adminOrdersActions,
  defaultPagination,
  mobileVisibleColumns,
  tableConfig as tableProps,
} from "@/config/adminOrdersTableConfig";
import { useVariableStore } from "@/stores/variableStore";
import { getOrderDetail } from "@/api/order";

// 筛选配置
const filterConfig = adminOrdersFilterConfig;
const tableColumns = adminOrdersColumns;
const rowActions = adminOrdersActions;
const mobileColumns = mobileVisibleColumns;

// 查询表单
const queryForm = ref({ ...defaultQueryForm });

// 数据源（Mock 数据）
const platformList = ref([
  { id: 1, name: "学习通" },
  { id: 2, name: "智慧树" },
  { id: 3, name: "中国大学MOOC" },
  { id: 4, name: "雨课堂" },
  { id: 5, name: "U校园" },
]);

const agentList = ref([
  { id: 1, username: "agent001", nickname: "张三" },
  { id: 2, username: "agent002", nickname: "李四" },
  { id: 3, username: "agent003", nickname: "王五" },
]);

const variableStore = useVariableStore();

// 动态选项（传递给筛选组件）
const filterOptions = computed(() => ({
  platformList: platformList.value,
  agentList: agentList.value,
  statusOptions: {
    order_status: variableStore.getStatusOptions("order_status"),
    dock_status: variableStore.getStatusOptions("dock_status"),
  },
}));

// 统计数据
const statistics = ref({
  total: 0,
  processing: 0,
  completed: 0,
  failed: 0,
});

// 表格数据
const tableData = ref([]);
const tableLoading = ref(false);
const searchLoading = ref(false);
const selectedOrders = ref([]);

// 分页
const pagination = ref({ ...defaultPagination });

// 查询处理
const handleSearch = () => {
  pagination.value.currentPage = 1;
  loadOrders();
};

// 重置处理
const handleResetQuery = () => {
  queryForm.value = { ...defaultQueryForm };
  handleSearch();
};

// Mock 数据生成函数
const generateMockOrders = (count = 100) => {
  const platforms = [
    { id: 1, name: "学习通" },
    { id: 2, name: "智慧树" },
    { id: 3, name: "中国大学MOOC" },
    { id: 4, name: "雨课堂" },
    { id: 5, name: "U校园" },
  ];

  const courses = [
    "大学英语",
    "高等数学",
    "线性代数",
    "概率论与数理统计",
    "大学物理",
    "程序设计基础",
    "数据结构",
    "计算机网络",
    "操作系统",
    "数据库原理",
  ];

  const agents = [
    { id: 1, username: "agent001", nickname: "张三" },
    { id: 2, username: "agent002", nickname: "李四" },
    { id: 3, username: "agent003", nickname: "王五" },
  ];

  const orders = [];
  const now = Date.now();

  for (let i = 1; i <= count; i++) {
    const platform = platforms[Math.floor(Math.random() * platforms.length)];
    const agent = agents[Math.floor(Math.random() * agents.length)];
    const orderStatus = Math.floor(Math.random() * 5); // 0-4
    const dockStatus = Math.floor(Math.random() * 4); // 0-3
    const price = (Math.random() * 490 + 10).toFixed(2);
    const originalPrice = (
      parseFloat(price) *
      (1 + Math.random() * 0.5)
    ).toFixed(2);

    orders.push({
      id: i,
      orderNo: `ORD${new Date().getFullYear()}${String(i).padStart(6, "0")}`,
      platform,
      platformName: platform.name,
      courseName: courses[Math.floor(Math.random() * courses.length)],
      studentAccount: `student${String(i).padStart(4, "0")}`,
      studentPassword: `pwd${Math.random().toString(36).substring(2, 8)}`,
      orderStatus,
      dockStatus,
      price: parseFloat(price),
      originalPrice: parseFloat(originalPrice),
      user: agent,
      agentName: `${agent.username} (${agent.nickname})`,
      remark: Math.random() > 0.7 ? "这是一条测试备注" : "",
      retryCount: orderStatus === 4 ? Math.floor(Math.random() * 3) : 0,
      createdAt: new Date(now - Math.random() * 30 * 24 * 3600 * 1000)
        .toISOString()
        .slice(0, 19)
        .replace("T", " "),
      updatedAt: new Date(now - Math.random() * 7 * 24 * 3600 * 1000)
        .toISOString()
        .slice(0, 19)
        .replace("T", " "),
    });
  }

  return orders;
};

// 全部 Mock 数据
const allMockOrders = generateMockOrders(100);

// 筛选 Mock 数据
const filterMockOrders = (orders, filters) => {
  return orders.filter((order) => {
    // 订单编号筛选
    if (filters.orderNo && !order.orderNo.includes(filters.orderNo)) {
      return false;
    }

    // 平台筛选
    if (filters.platformId && order.platform.id !== filters.platformId) {
      return false;
    }

    // 学生账号筛选
    if (
      filters.studentAccount &&
      !order.studentAccount.includes(filters.studentAccount)
    ) {
      return false;
    }

    // 订单状态筛选
    if (
      filters.orderStatus !== null &&
      filters.orderStatus !== undefined &&
      order.orderStatus !== filters.orderStatus
    ) {
      return false;
    }

    // 对接状态筛选
    if (
      filters.dockStatus !== null &&
      filters.dockStatus !== undefined &&
      order.dockStatus !== filters.dockStatus
    ) {
      return false;
    }

    // 代理账号筛选
    if (filters.userUid && order.user.uid !== filters.userUid) {
      return false;
    }

    return true;
  });
};

// 加载订单（使用 Mock 数据）
const loadOrders = async () => {
  tableLoading.value = true;
  searchLoading.value = true;

  // 模拟网络延迟
  await new Promise((resolve) => setTimeout(resolve, 300));

  try {
    // 筛选数据
    const filteredOrders = filterMockOrders(allMockOrders, queryForm.value);

    // 分页
    const start =
      (pagination.value.currentPage - 1) * pagination.value.pageSize;
    const end = start + pagination.value.pageSize;
    tableData.value = filteredOrders.slice(start, end);
    pagination.value.total = filteredOrders.length;

    // 更新统计数据
    const stats = {
      total: allMockOrders.length,
      processing: allMockOrders.filter((o) => o.orderStatus === 1).length,
      completed: allMockOrders.filter((o) => o.orderStatus === 2).length,
      failed: allMockOrders.filter((o) => o.orderStatus === 4).length,
    };

    statistics.value = stats;
  } catch (error) {
    console.error("加载订单失败：", error);
  } finally {
    tableLoading.value = false;
    searchLoading.value = false;
  }
};

// 表格事件处理
const handleSelectionChange = (selection) => {
  selectedOrders.value = selection;
};

const handleSortChange = ({ prop, order }) => {
  queryForm.value.sortField = prop;
  queryForm.value.sortOrder = order === "ascending" ? "asc" : "desc";
  loadOrders();
};

const handleAction = ({ action, row, index }) => {
  switch (action) {
    case "view":
      handleView(row);
      break;
    case "status":
      handleUpdateStatus(row);
      break;
    case "dock":
      handleUpdateDock(row);
      break;
    case "remark":
      handleRemark(row);
      break;
    case "retry":
      handleRetry(row);
      break;
    case "delete":
      handleDelete(row);
      break;
  }
};

const handlePageChange = ({ page, size }) => {
  pagination.value.currentPage = page;
  pagination.value.pageSize = size;
  loadOrders();
};

// 具体操作方法（Mock 实现）
const handleView = (row) => {
  console.log("查看订单：", row);
  ElMessage.success(`查看订单：${row.orderNo}`);
};

const handleUpdateStatus = (row) => {
  console.log("更新状态：", row);
  ElMessageBox.prompt("请输入新状态 (0-4)", "更新订单状态", {
    inputPattern: /^[0-4]$/,
    inputErrorMessage: "请输入 0-4 之间的数字",
  })
    .then(({ value }) => {
      // Mock 更新
      const index = allMockOrders.findIndex((o) => o.id === row.id);
      if (index !== -1) {
        allMockOrders[index].orderStatus = parseInt(value);
        loadOrders();
        ElMessage.success("状态更新成功");
      }
    })
    .catch(() => {});
};

const handleUpdateDock = (row) => {
  console.log("更新对接：", row);
  ElMessageBox.prompt("请输入新对接状态 (0-3)", "更新对接状态", {
    inputPattern: /^[0-3]$/,
    inputErrorMessage: "请输入 0-3 之间的数字",
  })
    .then(({ value }) => {
      // Mock 更新
      const index = allMockOrders.findIndex((o) => o.id === row.id);
      if (index !== -1) {
        allMockOrders[index].dockStatus = parseInt(value);
        loadOrders();
        ElMessage.success("对接状态更新成功");
      }
    })
    .catch(() => {});
};

const handleRemark = (row) => {
  console.log("添加备注：", row);
  ElMessageBox.prompt("请输入备注内容", "添加备注", {
    inputValue: row.remark || "",
  })
    .then(({ value }) => {
      // Mock 更新
      const index = allMockOrders.findIndex((o) => o.id === row.id);
      if (index !== -1) {
        allMockOrders[index].remark = value;
        loadOrders();
        ElMessage.success("备注添加成功");
      }
    })
    .catch(() => {});
};

const handleRetry = (row) => {
  console.log("重试订单：", row);
  ElMessageBox.confirm("确认重试该订单？", "重试确认", {
    type: "warning",
  })
    .then(() => {
      // Mock 重试
      const index = allMockOrders.findIndex((o) => o.id === row.id);
      if (index !== -1) {
        allMockOrders[index].retryCount += 1;
        allMockOrders[index].orderStatus = 1; // 改为处理中
        loadOrders();
        ElMessage.success("订单重试成功");
      }
    })
    .catch(() => {});
};

const handleDelete = (row) => {
  console.log("删除订单：", row);
  ElMessageBox.confirm("确认删除该订单？此操作不可恢复！", "删除确认", {
    type: "error",
  })
    .then(() => {
      // Mock 删除
      const index = allMockOrders.findIndex((o) => o.id === row.id);
      if (index !== -1) {
        allMockOrders.splice(index, 1);
        loadOrders();
        ElMessage.success("订单删除成功");
      }
    })
    .catch(() => {});
};

// 初始化
onMounted(() => {
  loadOrders();
});
</script>

<style scoped>
.admin-orders-page {
  padding: 20px;
}

.statistics-cards {
  margin-bottom: 20px;
}

.stat-content {
  text-align: center;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: var(--text-secondary);
}

/* 自定义移动端卡片样式 */
.custom-mobile-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.custom-mobile-card .card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color-light);
}

.order-no {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.card-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-item {
  display: flex;
  font-size: 14px;
}

.info-item .label {
  color: var(--text-secondary);
  min-width: 60px;
}

.info-item .value {
  color: var(--text-regular);
}

.info-item .price {
  color: var(--color-danger);
  font-weight: 600;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px solid var(--border-color-light);
}
</style>
