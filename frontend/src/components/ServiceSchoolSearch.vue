<template>
  <div class="service-school-search" role="group" aria-label="查找学校">
    <div class="school-query">
      <el-input
        v-model="keyword"
        aria-label="学校关键词"
        :placeholder="requireKeyword ? '输入学校名称后查询' : '输入学校关键词，可留空查询'"
        :maxlength="schoolKeywordLimit"
        :disabled="!enabled"
        clearable
        @keydown.enter="searchWithKeyboard"
      />
      <el-button :loading="loading" :disabled="!enabled || !queryValid" @click="search">查询学校</el-button>
    </div>
    <p v-if="!result && !loading && !error" class="school-hint">
      {{ active ? (requireKeyword ? '输入学校名称后查询；请选择完整校名，不会自动选择。' : '查询后选择学校；更换关键词不会修改已选学校。') : '请先勾选账号授权，再查询学校。' }}
    </p>
    <p v-if="loading" class="school-hint" role="status">正在查询第 {{ attempt.page }} 页学校…</p>
    <div v-if="error" class="school-error" role="alert">
      <p>{{ errorMessage }}</p>
      <el-button v-if="error !== 'KEYWORD'" :disabled="!enabled || loading" @click="retry">重试第 {{ attempt.page }} 页</el-button>
    </div>
    <div v-if="result" class="school-results" :aria-busy="loading">
      <div class="school-results-header" role="status">
        <span>第 {{ result.page }} 页 · {{ result.items.length }} 所学校</span>
        <span v-if="result.items.length && !result.hasMore">已到最后一页</span>
      </div>
      <ul v-if="result.items.length" class="school-list" aria-label="学校查询结果">
        <li v-for="school in result.items" :key="school.id">
          <button
            type="button"
            class="school-option"
            :aria-pressed="isSelected(school)"
            :disabled="!enabled || loading || school.name.length > 120"
            @click="select(school)"
          >
            <span>{{ school.name }}</span>
            <small v-if="school.name.length > 120">名称过长，请核对学校全称</small>
            <small v-else>{{ isSelected(school) ? '已选择' : '选择' }}</small>
          </button>
        </li>
      </ul>
      <p v-else class="school-empty" role="status">
        {{ result.page === 1 ? '未找到匹配学校，请更换关键词后查询。' : '本页没有更多学校，可返回上一页。' }}
      </p>
      <nav class="school-pagination" aria-label="学校分页">
        <el-button :disabled="!canPrevious" @click="previous">上一页</el-button>
        <el-button :disabled="!canNext" @click="next">下一页</el-button>
      </nav>
    </div>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { useFormDisabled } from "element-plus";
import { findServiceSchools } from "@/api/serviceCommerce";
import { schoolKeywordLimit, useServiceSchools } from "@/composables/useServiceSchools";

const props = defineProps({
  productId: { type: [String, Number], required: true },
  active: { type: Boolean, default: true },
  disabled: Boolean,
  selectedId: { type: String, default: "" },
  selectedName: { type: String, default: "" },
  maxPage: { type: Number, default: 10000 },
  requireKeyword: Boolean,
  idMode: { type: String, default: "code", validator: (value) => ["code", "name"].includes(value) },
});
const emit = defineEmits(["select"]);
const disabled = useFormDisabled();
const { keyword, result, attempt, error, loading, enabled, queryValid, canPrevious, canNext,
  search, previous, next, retry } = useServiceSchools(findServiceSchools, {
  productId: () => props.productId,
  active: () => props.active && !disabled.value,
  maxPage: () => props.maxPage,
  idMode: () => props.idMode,
  requireKeyword: () => props.requireKeyword,
});
const errorMessage = computed(() => {
  if (error.value === "KEYWORD") return props.requireKeyword
    ? "请输入 1–80 字的学校名称，请勿使用换行或控制字符。" : "关键词最多 80 字，请勿使用换行或控制字符。";
  const issue = error.value === "RESPONSE" ? "学校列表格式有误" : "学校查询失败";
  const retained = result.value ? `；仍显示第 ${result.value.page} 页结果` : "";
  return `第 ${attempt.value.page} 页${issue}${retained}。请重试或更换关键词。`;
});
function searchWithKeyboard(event) {
  if (event.isComposing || event.keyCode === 229) return;
  event.preventDefault();
  search();
}
function isSelected(school) {
  return props.selectedId ? school.id === props.selectedId : !!props.selectedName && school.name === props.selectedName;
}
function select(school) {
  if (!enabled.value || loading.value || school.name.length > 120 || !result.value?.items.includes(school)) return;
  emit("select", { id: school.id, name: school.name });
}
</script>

<style scoped>
.service-school-search { width: 100%; min-width: 0; }
.school-query { display: flex; gap: 8px; width: 100%; }
.school-query :deep(.el-input) { flex: 1; min-width: 0; }
.service-school-search :deep(.el-button),
.service-school-search :deep(.el-input__wrapper) { min-height: 44px; box-sizing: border-box; }
.service-school-search :deep(.el-button + .el-button) { margin-left: 0; }
.school-hint, .school-empty, .school-error p { margin: 8px 0; line-height: 1.7; font-size: 12px; }
.school-hint, .school-empty { color: var(--el-text-color-secondary); }
.school-error { padding: 8px 12px; margin-top: 12px; border: 1px solid var(--el-color-warning-light-5); background: var(--el-color-warning-light-9); border-radius: 8px; }
.school-results { border: 1px solid var(--el-border-color); border-radius: 8px; margin-top: 12px; overflow: hidden; }
.school-results-header { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 4px 12px; padding: 10px 12px; background: var(--el-fill-color-light); font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.7; }
.school-list { list-style: none; padding: 0; margin: 0; max-height: 300px; overflow-y: auto; overscroll-behavior: contain; }
.school-list li + li { border-top: 1px solid var(--el-border-color-lighter); }
.school-option { display: flex; align-items: center; justify-content: space-between; gap: 12px; width: 100%; min-height: 44px; padding: 12px; text-align: left; font: inherit; line-height: 1.7; color: var(--el-text-color-primary); background: var(--el-bg-color); border: 0; cursor: pointer; }
.school-option > span { min-width: 0; overflow-wrap: anywhere; }
.school-option small { flex-shrink: 0; color: var(--el-text-color-secondary); font-size: 12px; }
.school-option[aria-pressed="true"] { background: var(--el-color-primary-light-9); }
.school-option[aria-pressed="true"] small { color: var(--el-color-primary); }
.school-option:hover:not(:disabled) { background: var(--el-fill-color-light); }
.school-option:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: -3px; }
.school-option:disabled { cursor: not-allowed; color: var(--el-text-color-placeholder); }
.school-empty { padding: 16px 12px; }
.school-pagination { display: flex; justify-content: space-between; gap: 12px; padding: 8px 12px; border-top: 1px solid var(--el-border-color-lighter); }
@media (max-width: 600px) {
  .school-query { flex-wrap: wrap; }
  .school-query :deep(.el-input) { flex-basis: 180px; }
  .school-list { max-height: 240px; }
  .school-option { gap: 8px; }
  .school-option small { max-width: 6em; }
}
</style>
