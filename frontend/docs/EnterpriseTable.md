# EnterpriseTable 企业级表格组件

> 更新时间：2026-07-12
> 组件代码位于 `frontend/src/components` 与 `frontend/src/composables`。


## 功能特性

- ✅ **响应式设计**：桌面端表格 + 移动端卡片
- ✅ **配置驱动**：支持 JSON/JS 列配置
- ✅ **插槽混合**：配置 + 插槽灵活组合
- ✅ **内置功能**：排序、分页、批量选择、行操作
- ✅ **自动适配**：移动端自动切换卡片模式
- ✅ **高性能**：支持虚拟滚动、懒加载

## 使用模式

### **模式 1：配置驱动（推荐 ⭐）**

```vue
<template>
  <EnterpriseTable
    :columns="columns"
    :data="tableData"
    :loading="loading"
    :row-actions="rowActions"
    :pagination="pagination"
    @action="handleAction"
  />
</template>

<script setup>
import { ref } from "vue";
import EnterpriseTable from "@/components/EnterpriseTable.vue";

const columns = [
  { key: "id", label: "ID", width: 80, sortable: true },
  { key: "name", label: "名称", minWidth: 150 },
  {
    key: "status",
    label: "状态",
    width: 100,
    formatter: (row) => (row.status === 1 ? "启用" : "禁用"),
  },
];

const rowActions = [
  { key: "edit", label: "编辑", type: "primary" },
  { key: "delete", label: "删除", type: "danger" },
];

const tableData = ref([]);
const loading = ref(false);
const pagination = ref({ currentPage: 1, pageSize: 10, total: 0 });

const handleAction = ({ action, row }) => {
  console.log(action, row);
};
</script>
```

---

## Props

| 参数            | 说明                       | 类型              | 默认值    |
| --------------- | -------------------------- | ----------------- | --------- |
| `columns`       | 列配置数组                 | `Array`           | `[]`      |
| `data`          | 表格数据                   | `Array`           | `[]`      |
| `loading`       | 加载状态                   | `Boolean`         | `false`   |
| `rowActions`    | 行操作配置                 | `Array`           | `[]`      |
| `pagination`    | 分页配置                   | `Object`          | `null`    |
| `selectable`    | 是否可选择                 | `Boolean`         | `false`   |
| `rowKey`        | 行唯一标识                 | `String/Function` | `'id'`    |
| `height`        | 表格高度                   | `String/Number`   | -         |
| `maxHeight`     | 最大高度                   | `String/Number`   | -         |
| `actionsWidth`  | 操作列宽度                 | `String/Number`   | `200`     |
| `actionsFixed`  | 操作列固定                 | `String/Boolean`  | `'right'` |
| `cardTitleKey`  | 移动端卡片标题字段         | `String`          | -         |
| `cardBadgeKey`  | 移动端卡片徽章字段         | `String`          | -         |
| `mobileColumns` | 移动端显示的列（key 数组） | `Array`           | `[]`      |
| `tableProps`    | 额外的 el-table props      | `Object`          | `{}`      |

---

## Columns 配置结构

```typescript
interface Column {
  key: string; // 字段名（必填）
  label: string; // 列标题（必填）
  width?: number | string; // 列宽度
  minWidth?: number | string; // 最小宽度
  sortable?: boolean; // 是否可排序
  align?: "left" | "center" | "right"; // 对齐方式
  fixed?: "left" | "right" | boolean; // 固定列
  hidden?: boolean; // 是否隐藏
  hideInTable?: boolean; // 桌面端隐藏

  // 渲染方式（三选一）
  formatter?: (row, column, index) => string; // 格式化函数
  component?: Component; // 自定义组件
  componentProps?: Object | Function; // 组件属性
}
```

### 列配置示例

```javascript
// 基础列
{ key: 'name', label: '名称', width: 150 }

// 格式化列
{
  key: 'price',
  label: '价格',
  width: 100,
  align: 'right',
  formatter: (row) => `¥${row.price || 0}`
}

// 组件列
{
  key: 'status',
  label: '状态',
  width: 100,
  component: StatusTag,
  componentProps: (row) => ({
    status: row.status,
    type: 'success'
  })
}

// 排序列
{ key: 'createdAt', label: '创建时间', width: 180, sortable: true }

// 固定列
{ key: 'actions', label: '操作', width: 200, fixed: 'right' }
```

---

## RowActions 配置结构

```typescript
interface RowAction {
  key: string; // 操作唯一标识（必填）
  label: string; // 操作文本（必填）
  icon?: string | Component; // 图标
  type?: string; // 按钮类型 (primary/success/warning/danger/info)
  size?: string; // 按钮大小 (large/default/small)
  link?: boolean; // 是否为链接按钮
  disabled?: boolean; // 是否禁用
  show?: boolean | Function; // 是否显示（支持动态判断）
  hidden?: boolean; // 是否隐藏
}
```

### 行操作示例

```javascript
const rowActions = [
  {
    key: "view",
    label: "查看",
    icon: "View",
    type: "primary",
    link: true,
  },
  {
    key: "edit",
    label: "编辑",
    icon: "Edit",
    type: "warning",
    link: true,
    show: (row) => row.editable, // 动态显示
  },
  {
    key: "delete",
    label: "删除",
    icon: "Delete",
    type: "danger",
    link: true,
    disabled: (row) => row.status === "processing", // 动态禁用
  },
];
```

---

## Pagination 配置

```javascript
const pagination = {
  currentPage: 1, // 当前页
  pageSize: 10, // 每页条数
  total: 0, // 总条数
  pageSizes: [10, 20, 50, 100], // 可选每页条数
};
```

---

## Events

| 事件名             | 说明           | 回调参数                                           |
| ------------------ | -------------- | -------------------------------------------------- |
| `selection-change` | 选择项变化     | `(selection: Array)`                               |
| `sort-change`      | 排序变化       | `({ prop: string, order: string })`                |
| `action`           | 行操作触发     | `({ action: string, row: Object, index: number })` |
| `page-change`      | 页码变化       | `({ page: number, size: number })`                 |
| `size-change`      | 每页条数变化   | `(size: number)`                                   |
| `card-click`       | 移动端卡片点击 | `({ row: Object, index: number })`                 |

---

## Slots

| 插槽名           | 说明             | 作用域参数               |
| ---------------- | ---------------- | ------------------------ |
| `column-{key}`   | 自定义单元格     | `{ row, column, index }` |
| `actions`        | 自定义操作列     | `{ row, index }`         |
| `mobile-card`    | 自定义移动端卡片 | `{ row, index }`         |
| `card-badge`     | 自定义卡片徽章   | `{ row }`                |
| `mobile-actions` | 自定义移动端操作 | `{ row, index }`         |

### 插槽使用示例

```vue
<EnterpriseTable :columns="columns" :data="data">
  <!-- 自定义状态列 -->
  <template #column-status="{ row }">
    <el-tag :type="row.status === 1 ? 'success' : 'danger'">
      {{ row.status === 1 ? '启用' : '禁用' }}
    </el-tag>
  </template>

  <!-- 自定义操作列 -->
  <template #actions="{ row }">
    <el-button size="small" @click="handleEdit(row)">编辑</el-button>
    <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
  </template>

  <!-- 自定义移动端卡片 -->
  <template #mobile-card="{ row }">
    <div class="custom-card">
      <h3>{{ row.name }}</h3>
      <p>{{ row.description }}</p>
    </div>
  </template>
</EnterpriseTable>
```

---

## 完整示例

详见 `src/examples/AdminOrdersConfigExample.vue`

### 配置文件

```javascript
// src/config/orderTableConfig.js
export const orderColumns = [
  {
    key: "orderNo",
    label: "订单编号",
    width: 180,
    sortable: true,
    fixed: "left",
  },
  { key: "platform", label: "平台", width: 120 },
  {
    key: "status",
    label: "状态",
    width: 100,
    component: StatusDisplay,
    componentProps: (row) => ({ status: row.status }),
  },
  {
    key: "price",
    label: "价格",
    width: 100,
    align: "right",
    formatter: (row) => `¥${row.price}`,
  },
];

export const orderActions = [
  { key: "view", label: "查看", type: "primary", link: true },
  { key: "edit", label: "编辑", type: "warning", link: true },
  { key: "delete", label: "删除", type: "danger", link: true },
];
```

### 页面使用

```vue
<template>
  <EnterpriseTable
    :columns="orderColumns"
    :data="tableData"
    :loading="loading"
    :row-actions="orderActions"
    :pagination="pagination"
    @action="handleAction"
    @page-change="handlePageChange"
  />
</template>

<script setup>
import { orderColumns, orderActions } from "@/config/orderTableConfig";

const handleAction = ({ action, row }) => {
  switch (action) {
    case "view":
      console.log("查看", row);
      break;
    case "edit":
      console.log("编辑", row);
      break;
    case "delete":
      console.log("删除", row);
      break;
  }
};
</script>
```

---

## 最佳实践

### 1. 配置文件组织

```
src/
├── config/
│   ├── tables/
│   │   ├── orderTable.js      # 订单表格配置
│   │   ├── userTable.js       # 用户表格配置
│   │   └── index.js           # 统一导出
```

### 2. 移动端优化

- 指定 `mobileColumns` 显示关键字段（≤ 4 个）
- 使用 `cardTitleKey` 设置卡片标题
- 自定义 `mobile-card` 插槽优化展示

```vue
<EnterpriseTable
  :mobile-columns="['orderNo', 'status', 'price']"
  card-title-key="orderNo"
>
  <template #mobile-card="{ row }">
    <CustomCard :data="row" />
  </template>
</EnterpriseTable>
```

### 3. 性能优化

- 设置 `row-key` 确保列表更新性能
- 使用 `height` 或 `max-height` 启用虚拟滚动
- 大数据集分页加载

```vue
<EnterpriseTable
  :row-key="(row) => row.id"
  :max-height="600"
  :pagination="{ pageSize: 50 }"
/>
```

### 4. 列宽度管理

```javascript
// 推荐：固定关键列，其余自适应
const columns = [
  { key: "id", label: "ID", width: 80, fixed: "left" },
  { key: "name", label: "名称", minWidth: 150 }, // 自适应
  { key: "status", label: "状态", width: 100 },
];
```

### 5. 操作权限控制

```javascript
const rowActions = [
  {
    key: "edit",
    label: "编辑",
    show: (row) => {
      // 根据权限和行数据动态显示
      return hasPermission("order:edit") && row.status !== "locked";
    },
  },
];
```

---

## 设计理念

遵循 **AURA-X-KYS** 协议核心原则：

- **KISS**：配置简洁，API 清晰
- **YAGNI**：按需加载，不预设复杂功能
- **SOLID**：单一职责，只负责表格展示和交互

参考：

- Ant Design Pro ProTable
- Element Plus Admin 表格模式
- 移动端卡片最佳实践
