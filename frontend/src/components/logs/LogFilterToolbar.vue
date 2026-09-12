<template>
  <div class="log-filter-toolbar">
    <el-date-picker
      :model-value="dateRange"
      type="datetimerange"
      range-separator="至"
      start-placeholder="开始时间"
      end-placeholder="结束时间"
      class="date-range-control"
      value-format="YYYY-MM-DD HH:mm:ss"
      clearable
      @update:model-value="$emit('update:dateRange', $event)"
      @change="$emit('search')"
    />
    <el-input
      :model-value="keyword"
      placeholder="搜索日志内容"
      class="keyword-control"
      clearable
      @update:model-value="$emit('update:keyword', $event)"
      @keyup.enter="$emit('search')"
      @clear="$emit('search')"
    >
      <template #prefix>
        <el-icon><Search /></el-icon>
      </template>
    </el-input>
    <el-select
      :model-value="operationType"
      placeholder="操作类型"
      class="type-control"
      clearable
      @update:model-value="$emit('update:operationType', $event)"
      @change="$emit('search')"
    >
      <el-option
        v-for="option in operationOptions"
        :key="option.value"
        :label="option.label"
        :value="option.value"
      />
    </el-select>
    <el-button
      type="primary"
      :icon="Search"
      :loading="loading"
      @click="$emit('search')"
    >
      搜索
    </el-button>
  </div>
</template>

<script setup>
import { Search } from "@element-plus/icons-vue";

defineProps({
  dateRange: {
    type: Array,
    default: null,
  },
  keyword: {
    type: String,
    default: "",
  },
  operationType: {
    type: String,
    default: "",
  },
  loading: {
    type: Boolean,
    default: false,
  },
});

defineEmits([
  "search",
  "update:dateRange",
  "update:keyword",
  "update:operationType",
]);

const operationOptions = [
  { label: "全部", value: "" },
  { label: "登录", value: "登录" },
  { label: "登出", value: "登出" },
  { label: "开户", value: "开户" },
  { label: "充值", value: "充值" },
  { label: "创建订单", value: "创建订单" },
  { label: "补单", value: "补单" },
  { label: "查课", value: "查课" },
];
</script>

<style scoped>
.log-filter-toolbar {
  min-width: 0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

/* DatePicker forwards its class to a nested input without this component's scope id. */
.log-filter-toolbar :deep(.date-range-control.el-date-editor) {
  --el-date-editor-width: 100%;
  box-sizing: border-box;
  width: min(340px, 100%);
  max-width: 100%;
  min-width: 0;
}

.keyword-control {
  width: 180px;
}

.type-control {
  width: 130px;
}

@media (max-width: 767px) {
  .log-filter-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .log-filter-toolbar :deep(.date-range-control.el-date-editor),
  .keyword-control,
  .type-control,
  .log-filter-toolbar .el-button {
    width: 100%;
    max-width: 100%;
    min-width: 0;
    margin: 0;
  }
}
</style>
