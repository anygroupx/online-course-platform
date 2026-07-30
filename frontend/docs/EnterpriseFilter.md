# EnterpriseFilter 企业级筛选组件

> 更新时间：2026-07-12
> 组件代码位于 `frontend/src/components` 与 `frontend/src/composables`。


## 功能特性

- ✅ **响应式设计**：移动端抽屉 + 桌面端折叠面板
- ✅ **双向绑定**：v-model 支持
- ✅ **配置驱动**：支持 JSON/JS 配置（推荐）
- ✅ **插槽混合**：配置 + 插槽灵活组合
- ✅ **自动搜索**：可配置防抖时间
- ✅ **暴露方法**：支持外部控制

## 两种使用模式

### **模式 1：配置驱动（推荐 ⭐）**

适用场景：标准表单、大型筛选（10+ 字段）

**优点**：

- 配置可复用、可持久化
- 代码简洁，维护方便
- 支持动态选项注入

### **模式 2：纯插槽**

适用场景：高度自定义、复杂逻辑

**优点**：

- 最大灵活性
- IDE 提示完整

---

## 使用示例

### 模式 1：配置驱动模式

#### 步骤 1：创建配置文件

```javascript
// config/orderFilterConfig.js
export const orderFilterConfig = {
  common: [
    {
      key: "orderNo",
      label: "订单编号",
      type: "input",
      placeholder: "输入订单编号",
      width: "200px",
    },
    {
      key: "platformId",
      label: "平台",
      type: "select",
      placeholder: "选择平台",
      width: "150px",
      optionsKey: "platformList", // 从 options prop 获取
      labelKey: "name",
      valueKey: "id",
    },
  ],
  advanced: [
    {
      key: "status",
      label: "状态",
      type: "select",
      width: "120px",
      // 方式1：直接配置选项
      options: [
        { label: "待处理", value: 0 },
        { label: "已完成", value: 1 },
      ],
    },
    {
      key: "dateRange",
      label: "创建时间",
      type: "datetimerange",
      width: "350px",
    },
  ],
};
```

#### 步骤 2：在页面中使用

```vue
<template>
  <EnterpriseFilter
    v-model="queryForm"
    :config="filterConfig"
    :options="filterOptions"
    :loading="searchLoading"
    @search="handleSearch"
    @reset="handleReset"
  >
    <!-- 可选：自定义复杂字段 -->
    <template #filter-customField="{ filter, isMobile }">
      <CustomComponent v-model="queryForm.customField" />
    </template>
  </EnterpriseFilter>
</template>

<script setup>
import { ref, computed } from "vue";
import EnterpriseFilter from "@/components/EnterpriseFilter.vue";
import { orderFilterConfig } from "@/config/orderFilterConfig";

// 配置
const filterConfig = orderFilterConfig;

// 表单数据
const queryForm = ref({
  orderNo: "",
  platformId: null,
  status: null,
  dateRange: [],
});

// 动态选项数据源
const platformList = ref([
  { id: 1, name: "平台A" },
  { id: 2, name: "平台B" },
]);

// 传递给组件的选项
const filterOptions = computed(() => ({
  platformList: platformList.value,
}));

// 查询逻辑
const searchLoading = ref(false);
const handleSearch = () => {
  searchLoading.value = true;
  // API 调用
  setTimeout(() => {
    searchLoading.value = false;
  }, 1000);
};

const handleReset = () => {
  queryForm.value = {
    orderNo: "",
    platformId: null,
    status: null,
    dateRange: [],
  };
};
</script>
```

---

### 模式 2：纯插槽模式（向后兼容）

```vue
<template>
  <EnterpriseFilter
    v-model="queryForm"
    :loading="searchLoading"
    @search="handleSearch"
    @reset="handleReset"
  >
    <!-- 桌面端常用筛选 -->
    <template #common-filters>
      <el-form-item label="订单编号">
        <el-input v-model="queryForm.orderNo" style="width: 200px" />
      </el-form-item>
    </template>

    <!-- 桌面端高级筛选 -->
    <template #advanced-filters>
      <el-form-item label="状态">
        <el-select v-model="queryForm.status" style="width: 120px">
          <el-option label="待处理" value="0" />
        </el-select>
      </el-form-item>
    </template>

    <!-- 移动端抽屉内所有筛选 -->
    <template #filters="{ isMobile }">
      <el-form-item label="订单编号">
        <el-input v-model="queryForm.orderNo" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryForm.status" style="width: 100%">
          <el-option label="待处理" value="0" />
        </el-select>
      </el-form-item>
    </template>
  </EnterpriseFilter>
</template>
```

## Props

| 参数                 | 说明                         | 类型      | 默认值    |
| -------------------- | ---------------------------- | --------- | --------- |
| `modelValue`         | 筛选表单数据（v-model）      | `Object`  | -         |
| `config`             | 筛选配置对象（配置驱动模式） | `Object`  | `null`    |
| `options`            | 动态选项数据源               | `Object`  | `{}`      |
| `loading`            | 查询加载状态                 | `Boolean` | `false`   |
| `labelWidth`         | 表单 label 宽度              | `String`  | `'100px'` |
| `hasAdvancedFilters` | 是否显示高级筛选折叠面板     | `Boolean` | `true`    |
| `autoSearchDelay`    | 自动搜索防抖时间（毫秒）     | `Number`  | `500`     |
| `enableAutoSearch`   | 是否启用自动搜索             | `Boolean` | `true`    |

### config 配置结构

```typescript
interface FilterConfig {
  common?: FilterField[]; // 常用筛选字段
  advanced?: FilterField[]; // 高级筛选字段
}

interface FilterField {
  key: string; // 字段名（对应 v-model 的 key）
  label: string; // 字段标签
  type: FilterType; // 字段类型
  placeholder?: string; // 占位符
  width?: string; // 桌面端宽度
  clearable?: boolean; // 是否可清空（默认 true）

  // 下拉选项配置（三选一）
  options?: Array<any>; // 方式1：直接配置选项数组
  optionsKey?: string; // 方式2：从 options prop 获取
  optionsGetter?: Function; // 方式3：动态函数

  // 选项映射
  labelKey?: string; // 选项 label 字段（默认 'label'）
  valueKey?: string; // 选项 value 字段（默认 'value'）

  // 日期类型特有
  format?: string;
  valueFormat?: string;
  startPlaceholder?: string;
  endPlaceholder?: string;

  // 数字类型特有
  min?: number;
  max?: number;
  step?: number;
}

type FilterType =
  | "input" // 文本输入框
  | "select" // 下拉选择器
  | "date" // 日期选择器
  | "datetime" // 日期时间选择器
  | "daterange" // 日期范围
  | "datetimerange" // 日期时间范围
  | "number" // 数字输入框
  | "switch" // 开关
  | "checkbox" // 多选框组
  | "radio"; // 单选框组
```

## Events

| 事件名              | 说明         | 回调参数          |
| ------------------- | ------------ | ----------------- |
| `update:modelValue` | 表单数据更新 | `(value: Object)` |
| `search`            | 点击查询按钮 | -                 |
| `reset`             | 点击重置按钮 | -                 |

## Slots

| 插槽名             | 说明                               | 作用域参数                                   |
| ------------------ | ---------------------------------- | -------------------------------------------- |
| `common-filters`   | 桌面端常用筛选（纯插槽模式）       | -                                            |
| `advanced-filters` | 桌面端高级筛选（纯插槽模式）       | -                                            |
| `filters`          | 移动端抽屉内所有筛选（纯插槽模式） | `{ isMobile: Boolean }`                      |
| `filter-{key}`     | 自定义单个筛选字段（配置模式）     | `{ filter: FilterField, isMobile: Boolean }` |

### 插槽优先级

在配置驱动模式下，**具名插槽 `filter-{key}` 优先于配置**。这允许你对特定字段进行深度自定义：

```vue
<EnterpriseFilter :config="filterConfig">
  <!-- 自定义 userId 字段 -->
  <template #filter-userId="{ filter, isMobile }">
    <el-select
      v-model="queryForm.userId"
      :style="{ width: isMobile ? '100%' : '150px' }"
    >
      <el-option
        v-for="user in userList"
        :key="user.id"
        :label="formatUserLabel(user)"
        :value="user.id"
      />
    </el-select>
  </template>
</EnterpriseFilter>
```

## 暴露方法

| 方法名           | 说明                 | 参数 |
| ---------------- | -------------------- | ---- |
| `closeDrawer`    | 关闭移动端抽屉       | -    |
| `openDrawer`     | 打开移动端抽屉       | -    |
| `toggleAdvanced` | 切换高级筛选折叠状态 | -    |

### 使用暴露方法

```vue
<template>
  <EnterpriseFilter ref="filterRef" />
  <el-button @click="openFilter">打开筛选</el-button>
</template>

<script setup>
import { ref } from "vue";

const filterRef = ref(null);

const openFilter = () => {
  filterRef.value.openDrawer();
};
</script>
```

## 配置示例

### 基础配置

```javascript
export const basicFilterConfig = {
  common: [
    {
      key: "keyword",
      label: "关键词",
      type: "input",
      placeholder: "输入关键词搜索",
      width: "200px",
    },
    {
      key: "status",
      label: "状态",
      type: "select",
      width: "120px",
      options: [
        { label: "全部", value: null },
        { label: "启用", value: 1 },
        { label: "禁用", value: 0 },
      ],
    },
  ],
};
```

### 动态选项配置

```javascript
export const dynamicFilterConfig = {
  common: [
    {
      key: "categoryId",
      label: "分类",
      type: "select",
      width: "150px",
      // 方式1：从 options prop 获取
      optionsKey: "categoryList",
      labelKey: "name",
      valueKey: "id",
    },
    {
      key: "status",
      label: "状态",
      type: "select",
      width: "120px",
      // 方式2：动态函数
      optionsGetter: (options) => {
        return options.variableStore?.getStatusOptions("order_status") || [];
      },
    },
  ],
};
```

### 日期范围配置

```javascript
export const dateFilterConfig = {
  advanced: [
    {
      key: "dateRange",
      label: "创建时间",
      type: "datetimerange",
      width: "350px",
      format: "YYYY-MM-DD HH:mm:ss",
      valueFormat: "YYYY-MM-DD HH:mm:ss",
      startPlaceholder: "开始时间",
      endPlaceholder: "结束时间",
    },
  ],
};
```

---

## 最佳实践

### 1. 配置文件组织

```
src/
├── config/
│   ├── filters/
│   │   ├── orderFilter.js      # 订单筛选配置
│   │   ├── userFilter.js       # 用户筛选配置
│   │   └── index.js            # 统一导出
```

### 2. 合理划分常用/高级筛选

- **常用筛选**：高频使用（如：订单号、状态、日期）
- **高级筛选**：低频使用（如：备注、标签、自定义字段）

建议常用筛选 ≤ 3 个字段，避免界面拥挤。

### 3. 选项数据源管理

```javascript
// 推荐：集中管理选项数据
const filterOptions = computed(() => ({
  platformList: platformStore.list,
  statusOptions: {
    order_status: variableStore.getStatusOptions("order_status"),
    dock_status: variableStore.getStatusOptions("dock_status"),
  },
}));
```

### 4. 复杂字段使用插槽

对于需要特殊渲染的字段（如：带头像的用户选择、树形分类选择），使用插槽自定义：

```vue
<EnterpriseFilter :config="config">
  <template #filter-userId="{ filter, isMobile }">
    <UserSelect v-model="queryForm.userId" :is-mobile="isMobile" />
  </template>
</EnterpriseFilter>
```

### 5. 性能优化

- 禁用自动搜索：`:enable-auto-search="false"`
- 增加防抖时间：`:auto-search-delay="800"`
- 大数据集使用虚拟滚动下拉

### 6. 配置持久化

```javascript
// 保存用户筛选配置到 localStorage
const saveFilterConfig = (config) => {
  localStorage.setItem("userFilterConfig", JSON.stringify(config));
};

// 加载用户配置
const loadUserConfig = () => {
  const saved = localStorage.getItem("userFilterConfig");
  return saved ? JSON.parse(saved) : defaultConfig;
};
```

## 设计理念

本组件遵循 **AURA-X-KYS** 协议中的核心设计原则：

- **KISS（Keep It Simple）**：单一组件，API 简洁
- **YAGNI（You Aren't Gonna Need It）**：按需加载高级筛选
- **SOLID**：单一职责，只负责筛选 UI 交互

参考：

- Element Plus Admin 筛选模式
- Ant Design Pro ProTable 组件
- 移动端抽屉交互标准
