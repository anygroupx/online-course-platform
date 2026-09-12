<template>
  <form class="service-order-filters" aria-label="订单筛选" @submit.prevent="submit"
    @compositionstart="composing = true" @compositionend="composing = false" @keydown.enter.capture="guardComposition">
    <div class="filter-heading"><h2>查找订单</h2><span>按已保存的订单查询，不会执行订单操作</span></div>
    <div class="filter-grid">
      <label class="keyword-field"><span>搜索订单</span>
        <el-input :model-value="modelValue.keyword" placeholder="订单号、服务名称或账号标识" maxlength="100" clearable
          aria-label="搜索订单" autocomplete="off" @update:model-value="update('keyword', $event)" />
      </label>
      <label><span>服务类型</span>
        <el-select :model-value="modelValue.providerType" aria-label="服务类型" placeholder="全部服务" @update:model-value="update('providerType', $event)">
          <el-option label="全部服务" value="" /><el-option v-for="type in orderSearchTypes" :key="type" :label="serviceNames[type]" :value="type" />
        </el-select>
      </label>
      <label><span>订单状态</span>
        <el-select :model-value="modelValue.status" aria-label="订单状态" placeholder="全部状态" @update:model-value="update('status', $event)">
          <el-option label="全部状态" value="" /><el-option v-for="state in orderSearchStates" :key="state" :label="stateLabel(state)" :value="state" />
        </el-select>
      </label>
      <label><span>创建日期 · 起始</span><input type="date" aria-label="创建日期起始" min="1000-01-01" max="9998-12-31"
        :value="modelValue.createdFrom" @input="update('createdFrom', $event.target.value)" /></label>
      <label><span>创建日期 · 截止</span><input type="date" aria-label="创建日期截止" min="1000-01-01" max="9998-12-31"
        :value="modelValue.createdTo" @input="update('createdTo', $event.target.value)" /></label>
      <label v-if="admin"><span>所属用户编号</span><el-input :model-value="modelValue.ownerId" inputmode="numeric" maxlength="19"
        aria-label="所属用户编号" placeholder="全部用户" clearable autocomplete="off" @update:model-value="update('ownerId', $event)" /></label>
    </div>
    <div class="filter-bottom">
      <p>日期按北京时间计算。账号按列表显示的标识查找，无需填写密码。</p>
      <div class="filter-buttons"><el-button native-type="button" @click="$emit('reset')">重置筛选</el-button>
        <el-button type="primary" native-type="submit" :loading="loading && !dirty">查询订单</el-button></div>
    </div>
    <p v-if="validation" class="filter-validation" role="alert">{{ validation }}</p>
    <p v-else-if="dirty" class="filter-dirty" role="status">筛选条件已修改，点击“查询订单”后生效。</p>
  </form>
</template>

<script setup>
import { ref } from "vue";
import { serviceNames, stateName, orderStateName } from "@/utils/serviceCommerce";
import { orderSearchStates, orderSearchTypes } from "@/composables/useServiceOrderSearch";
const props = defineProps({ modelValue: { type: Object, required: true }, admin: Boolean, loading: Boolean, dirty: Boolean, validation: String });
const emit = defineEmits(["update:modelValue", "submit", "reset"]);
const composing = ref(false);
const update = (key, value) => emit("update:modelValue", { ...props.modelValue, [key]: value ?? "" });
function stateLabel(status) {
  if (["sxdk_tw", "appui"].includes(props.modelValue.providerType)) return orderStateName({ providerType: props.modelValue.providerType, status });
  return status === "COMPLETED" ? "已结束 / 已完成" : stateName(status);
}
function guardComposition(event) { if (event.isComposing || composing.value || event.keyCode === 229) event.preventDefault(); }
function submit() { if (!composing.value) emit("submit"); }
</script>

<style scoped>
.service-order-filters { padding: 22px; border: 1px solid var(--el-border-color-light); border-radius: 18px; background: var(--el-bg-color); margin-bottom: 20px; }
.filter-heading { display: flex; align-items: baseline; gap: 16px; flex-wrap: wrap; margin-bottom: 18px; }
.filter-heading h2 { margin: 0; font-size: 16px; }
.filter-heading > span, .filter-bottom p { font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.7; }
.filter-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
.keyword-field { grid-column: span 2; }
.filter-grid label { display: flex; flex-direction: column; gap: 8px; min-width: 0; font-size: 13px; }
.filter-grid :deep(.el-select) { width: 100%; }
.filter-grid :deep(.el-input__wrapper), .filter-grid :deep(.el-select__wrapper) { min-height: 44px; box-sizing: border-box; }
.filter-grid input[type=date] { width: 100%; min-width: 0; min-height: 44px; box-sizing: border-box; padding: 8px 12px; border: 1px solid var(--el-border-color); border-radius: var(--el-border-radius-base); background: var(--el-fill-color-blank); color: var(--el-text-color-primary); font: inherit; }
.filter-grid input[type=date]:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.filter-bottom { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 18px; }
.filter-bottom p { margin: 0; }
.filter-buttons { display: flex; gap: 10px; flex-shrink: 0; }
.filter-buttons .el-button { min-height: 44px; margin-left: 0; }
.filter-validation, .filter-dirty { margin: 12px 0 0; font-size: 13px; line-height: 1.7; overflow-wrap: anywhere; }
.filter-validation { color: var(--el-color-danger); }
.filter-dirty { color: var(--el-text-color-secondary); }
:global(html.dark) .filter-grid input[type=date] { color-scheme: dark; }
@media (max-width: 720px) {
  .service-order-filters { padding: 18px; }
  .filter-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px 10px; }
  .filter-bottom { flex-direction: column; align-items: stretch; gap: 12px; }
  .filter-buttons .el-button { flex: 1; }
}
@media (max-width: 360px) { .filter-grid { grid-template-columns: minmax(0, 1fr); } .keyword-field { grid-column: auto; } }
</style>
