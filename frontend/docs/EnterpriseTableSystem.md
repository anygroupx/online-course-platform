# 企业级通用表格组件使用指南

> 更新时间：2026-07-12
> 组件代码位于 `frontend/src/components` 与 `frontend/src/composables`。


## 🎯 概述

基于 **AURA-X-KYS** 协议，采用**渐进式增强**方案重构的企业级通用表格系统。

### 核心设计理念

- **KISS (Keep It Simple)**: 在现有基础上增量增强，而非推倒重来
- **YAGNI (You Aren't Gonna Need It)**: 只添加当前明确需要的功能
- **SOLID**: 通过组合和插槽扩展，而非修改核心代码

---

## 📦 组件架构

### 1. 核心组件

| 组件                    | 路径                                 | 职责           |
| ----------------------- | ------------------------------------ | -------------- |
| **useTableComposition** | `composables/useTableComposition.js` | 统一状态管理   |
| **TableStatistics**     | `components/TableStatistics.vue`     | 统计卡片       |
| **TableBatchActions**   | `components/TableBatchActions.vue`   | 批量操作工具栏 |
| **EnterpriseFilter**    | `components/EnterpriseFilter.vue`    | 企业级筛选器   |
| **EnterpriseTable**     | `components/EnterpriseTable.vue`     | 企业级表格     |

### 2. 配置示例

| 文件                                    | 说明         |
| --------------------------------------- | ------------ |
| `config/adminOrdersEnterpriseConfig.js` | 完整配置示例 |
| `examples/EnterpriseOrdersExample.vue`  | 完整使用示例 |

---

## 🚀 快速开始

### 步骤 1: 创建配置文件

```javascript
// config/myPageConfig.js
export const statisticsConfig = [
  {
    key: "total",
    label: "总数",
    value: 0,
    icon: Document,
    iconClass: "primary",
    color: "#409eff",
  },
];

export const filterConfig = {
  common: [
    {
      key: "keyword",
      label: "关键字",
      type: "input",
      placeholder: "请输入",
      width: "200px",
    },
  ],
  advanced: [
    {
      key: "dateRange",
      label: "日期范围",
      type: "daterange",
      width: "240px",
    },
  ],
};

export const columnsConfig = [
  { key: "id", label: "ID", width: 80, sortable: true },
  { key: "name", label: "名称", minWidth: 150 },
];

export const rowActionsConfig = [
  { key: "edit", label: "编辑", type: "primary", size: "small" },
  { key: "delete", label: "删除", type: "danger", size: "small" },
];

export const batchActionsConfig = [
  { key: "export", label: "导出", type: "primary" },
  { key: "delete", label: "批量删除", type: "danger" },
];
```

### 步骤 2: 在页面中使用

```vue
<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <TableStatistics :statistics="statisticsData" />

    <!-- 主卡片 -->
    <el-card>
      <!-- 筛选器 -->
      <EnterpriseFilter
        v-model="filterModel"
        :config="filterConfig"
        :options="{ platformList }"
        storage-key="my_page"
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
        :columns="columnsConfig"
        :data="tableData"
        :loading="loading"
        :row-actions="rowActionsConfig"
        :pagination="{ currentPage, pageSize, total }"
        :selectable="true"
        storage-key="my_page"
        :enable-storage="true"
        :enable-column-manage="true"
        @selection-change="handleSelectionChange"
        @action="handleRowAction"
        @page-change="handlePageChange"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useTableComposition } from "@/composables/useTableComposition";
import TableStatistics from "@/components/TableStatistics.vue";
import EnterpriseFilter from "@/components/EnterpriseFilter.vue";
import TableBatchActions from "@/components/TableBatchActions.vue";
import EnterpriseTable from "@/components/EnterpriseTable.vue";
import {
  statisticsConfig,
  filterConfig,
  columnsConfig,
  rowActionsConfig,
  batchActionsConfig,
} from "@/config/myPageConfig";

// 使用组合式函数统一管理状态
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
  storageKey: "my_page",
  initialFilters: { keyword: "" },
  pageSize: 10,
  columns: columnsConfig,
});

const filterModel = computed({
  get: () => filters.value,
  set: (val) => handleFilterChange(val),
});

const statisticsData = ref(statisticsConfig);
const platformList = ref([]);

onMounted(async () => {
  await loadData(fetchDataFunction);
});

const handleSearch = () => {
  currentPage.value = 1;
  loadData(fetchDataFunction);
};

const handleReset = () => {
  handleResetFilters();
  loadData(fetchDataFunction);
};

const handleRowAction = ({ action, row }) => {
  console.log("行操作:", action, row);
};

const handleBatchAction = ({ action, selectedRows }) => {
  console.log("批量操作:", action, selectedRows);
};

// 数据获取函数
const fetchDataFunction = async (params) => {
  const res = await fetch("/api/data", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(params),
  });
  const data = await res.json();
  return {
    data: data.data.records || [],
    total: data.data.total || 0,
  };
};
</script>
```

---

## 📖 详细说明

### useTableComposition

**统一状态管理 Composable**

```javascript
const {
  // 分页状态
  currentPage,
  pageSize,
  total,

  // 筛选状态
  filters,
  dateRange,

  // 排序状态
  sortConfig,

  // 选择状态
  selectedRows,
  selectedIds,
  hasSelection,
  selectionCount,

  // 数据状态
  loading,
  tableData,

  // 列配置
  visibleColumns,

  // 查询参数（自动合并所有状态）
  queryParams,

  // 事件处理
  handleSelectionChange,
  handleSortChange,
  handlePageChange,
  handleFilterChange,
  handleResetFilters,

  // 列管理
  toggleColumnVisibility,
  updateColumnOrder,

  // 配置管理
  saveConfig,
  clearConfig,

  // 数据操作
  loadData,
  refresh,
  clearSelection,
} = useTableComposition(options);
```

**Options 参数:**

```javascript
{
  storageKey: 'table_config',        // 本地存储键名
  initialFilters: {},                // 初始筛选条件
  initialSort: { prop: null },       // 初始排序
  pageSize: 10,                      // 每页条数
  columns: [],                       // 列配置
}
```

---

### TableStatistics

**统计卡片组件**

```vue
<TableStatistics
  :statistics="[
    {
      key: 'total',
      label: '总数',
      value: 100,
      icon: Document,
      iconClass: 'primary',
      color: '#409eff',
      extra: '额外信息',
    },
  ]"
  :format-options="{
    decimals: 0,
    thousandsSeparator: true,
    prefix: '¥',
    suffix: '',
  }"
/>
```

---

### TableBatchActions

**批量操作工具栏**

```vue
<TableBatchActions
  :selected-rows="selectedRows"
  :selection-count="selectedRows.length"
  :actions="[
    {
      key: 'export',
      label: '导出',
      type: 'primary',
      icon: Download,
      show: true, // 或动态函数: (rows) => rows.length > 0
    },
  ]"
  :mobile-visible-count="2"
  @action="handleBatchAction"
  @clear-selection="clearSelection"
/>
```

---

### EnterpriseFilter

**企业级筛选器**

```vue
<EnterpriseFilter
  v-model="filters"
  :config="{
    common: [
      {
        key: 'keyword',
        label: '关键字',
        type: 'input',  // input | select | date | daterange | number
        placeholder: '请输入',
        width: '200px',
        clearable: true,
      }
    ],
    advanced: [...]
  }"
  :options="{
    platformList: [], // 动态选项数据
  }"
  storage-key="filter_config"
  :enable-storage="true"
  :enable-auto-search="true"
  :auto-search-delay="500"
  @search="handleSearch"
  @reset="handleReset"
>
  <!-- 自定义筛选项插槽 -->
  <template #filter-customField="{ filter, isMobile }">
    <el-input v-model="filters.customField" />
  </template>
</EnterpriseFilter>
```

---

### EnterpriseTable

**企业级表格**

```vue
<EnterpriseTable
  :columns="[
    {
      key: 'id',
      label: 'ID',
      width: 80,
      sortable: true,
      visible: true,
      formatter: (row) => row.id, // 格式化函数
      component: CustomComponent, // 自定义组件
      componentProps: (row) => ({ data: row }), // 组件属性
    },
  ]"
  :data="tableData"
  :loading="loading"
  :row-actions="[
    {
      key: 'edit',
      label: '编辑',
      type: 'primary',
      show: (row) => row.canEdit, // 动态显示
    },
  ]"
  :pagination="{ currentPage, pageSize, total }"
  :selectable="true"
  row-key="id"
  storage-key="table_config"
  :enable-storage="true"
  :enable-column-manage="true"
  card-title-key="name"
  card-badge-key="status"
  @selection-change="handleSelectionChange"
  @action="handleRowAction"
  @page-change="handlePageChange"
>
  <!-- 自定义列插槽 -->
  <template #column-name="{ row, column }">
    <span class="custom">{{ row.name }}</span>
  </template>

  <!-- 移动端卡片自定义 -->
  <template #mobile-card="{ row }">
    <div>自定义卡片内容</div>
  </template>
</EnterpriseTable>
```

---

## 🎨 高级特性

### 1. 配置持久化

所有组件都支持配置持久化到 `localStorage`:

```vue
<EnterpriseFilter storage-key="my_filter" :enable-storage="true" />

<EnterpriseTable storage-key="my_table" :enable-storage="true" />
```

保存的内容包括:

- 筛选条件
- 列顺序和可见性
- 分页大小
- 排序配置

### 2. 响应式设计

所有组件都内置响应式支持:

- **桌面端**: 标准表格布局
- **移动端**: 卡片式布局
- **自动适配**: 使用 `useResponsive` composable

### 3. 动态权限控制

```javascript
// 行操作动态显示
rowActions: [
  {
    key: "delete",
    label: "删除",
    show: (row) => row.canDelete && row.status !== "locked",
  },
];

// 批量操作动态显示
batchActions: [
  {
    key: "approve",
    label: "批量审批",
    show: (selectedRows) =>
      selectedRows.every((row) => row.status === "pending"),
  },
];
```

### 4. 自定义渲染

```vue
<!-- 使用 formatter 函数 -->
<script>
const columns = [
  {
    key: "amount",
    label: "金额",
    formatter: (row) => `¥${row.amount.toFixed(2)}`,
  },
];
</script>

<!-- 使用自定义组件 -->
<script>
import StatusDisplay from "@/components/StatusDisplay.vue";

const columns = [
  {
    key: "status",
    label: "状态",
    component: StatusDisplay,
    componentProps: (row) => ({ status: row.status, type: "order_status" }),
  },
];
</script>

<!-- 使用插槽 -->
<template>
  <EnterpriseTable>
    <template #column-actions="{ row }">
      <el-button @click="handleCustomAction(row)">自定义操作</el-button>
    </template>
  </EnterpriseTable>
</template>
```

---

## 💡 最佳实践

### 1. 统一配置管理

将所有配置放在 `config/` 目录下:

```
config/
  ├── adminOrdersEnterpriseConfig.js
  ├── userManagementConfig.js
  └── productListConfig.js
```

### 2. 使用 Composable 统一状态

```javascript
// 推荐：使用 useTableComposition
const table = useTableComposition({
  storageKey: "orders",
  columns: columnsConfig,
});

// 不推荐：手动管理所有状态
const currentPage = ref(1);
const pageSize = ref(10);
const filters = ref({});
// ...
```

### 3. 合理使用插槽

```vue
<!-- 简单场景：使用 formatter -->
<script>
const columns = [{ key: "price", formatter: (row) => `¥${row.price}` }];
</script>

<!-- 复杂场景：使用插槽 -->
<template>
  <EnterpriseTable>
    <template #column-price="{ row }">
      <el-input-number v-model="row.price" @change="handlePriceChange(row)" />
    </template>
  </EnterpriseTable>
</template>
```

### 4. 性能优化

```javascript
// 启用虚拟滚动（大数据量）
<EnterpriseTable
  :height="600"
  :data="largeDataset"
/>

// 合理设置分页大小
const paginationConfig = {
  pageSizes: [10, 20, 50, 100],  // 不要设置过大的值
};
```

---

## 📚 参考示例

完整示例请查看:

- **配置示例**: `src/config/adminOrdersEnterpriseConfig.js`
- **使用示例**: `src/examples/EnterpriseOrdersExample.vue`
- **原始实现**: `src/views/AdminOrders.vue`

---

## 🔄 迁移指南

### 从旧版本迁移

**步骤 1**: 安装组合式函数

```javascript
import { useTableComposition } from "@/composables/useTableComposition";
```

**步骤 2**: 替换状态管理

```javascript
// 旧代码
const currentPage = ref(1);
const pageSize = ref(10);
const selectedRows = ref([]);
// ...

// 新代码
const table = useTableComposition({
  storageKey: "my_table",
  columns: columnsConfig,
});
```

**步骤 3**: 更新模板

```vue
<!-- 旧代码 -->
<el-table :data="tableData" @selection-change="handleSelectionChange">
  <el-table-column prop="id" label="ID" />
</el-table>

<!-- 新代码 -->
<EnterpriseTable
  :columns="columnsConfig"
  :data="table.tableData.value"
  @selection-change="table.handleSelectionChange"
/>
```

---

## ❓ 常见问题

**Q: 如何禁用配置持久化？**

A: 不传 `storage-key` 或设置 `:enable-storage="false"`

**Q: 如何自定义移动端显示？**

A: 使用 `#mobile-card` 插槽或配置 `mobile-columns` 属性

**Q: 如何实现服务端排序？**

A: 监听 `@sort-change` 事件，将排序参数传给后端

**Q: 如何添加自定义筛选项？**

A: 使用 `#filter-{key}` 具名插槽

---

## 📞 技术支持

如有问题，请查看:

- 完整示例代码
- 组件源码注释
- AURA-X-KYS 协议文档
