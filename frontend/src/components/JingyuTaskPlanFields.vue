<template>
  <section class="jingyu-task-plan" aria-label="逐次任务安排">
    <div class="plan-heading"><div><h4>逐次任务安排</h4><p>每次对应一条任务，可生成后逐条调整。所有时间均为北京时间。</p></div>
      <el-tag :type="validation ? 'warning' : 'success'">已安排 {{ modelValue.length }} / {{ quantity || 0 }} 次</el-tag></div>
    <div class="builder-grid">
      <el-form-item label="首个执行日期（北京时间）"><el-input v-model="date" type="date" :min="range.min" :max="range.max"
        aria-label="首个执行日期（北京时间）" :disabled="disabled" /></el-form-item>
      <el-form-item label="默认执行时间"><el-input v-model="time" type="time" aria-label="默认执行时间" :disabled="disabled" /></el-form-item>
    </div>
    <el-form-item label="执行星期"><el-checkbox-group v-model="weekdays" aria-label="执行星期" :disabled="disabled">
      <el-checkbox v-for="day in days" :key="day.value" :value="day.value">{{ day.label }}</el-checkbox>
    </el-checkbox-group></el-form-item>
    <div class="generate-actions"><el-button type="primary" plain :disabled="disabled || !date || !time || !weekdays.length" @click="generate">
      {{ modelValue.length ? '重新生成全部任务' : '生成任务安排' }}</el-button>
      <span>修改购买次数不会自动增删任务。{{ modelValue.length ? '重新生成会替换下面的安排。' : '请先生成，再核对每次时间。' }}</span></div>
    <p v-if="generationError" class="task-validation" role="alert">{{ generationError }}</p>
    <ol v-if="modelValue.length" class="task-list" :start="(page - 1) * pageSize + 1">
      <li v-for="(_, offset) in pageItems" :key="(page - 1) * pageSize + offset" class="task-row">
        <span class="task-index">{{ (page - 1) * pageSize + offset + 1 }}</span>
        <el-form-item label="执行日期"><el-input :model-value="part(offset, 'date')" type="date" :min="range.min" :max="range.max"
          :aria-label="`第 ${(page - 1) * pageSize + offset + 1} 条任务日期`" :disabled="disabled"
          @update:model-value="update(offset, 'date', $event)" /></el-form-item>
        <el-form-item label="执行时间"><el-input :model-value="part(offset, 'time')" type="time" step="1"
          :aria-label="`第 ${(page - 1) * pageSize + offset + 1} 条任务时间`" :disabled="disabled"
          @update:model-value="update(offset, 'time', $event)" /></el-form-item>
      </li>
    </ol>
    <div v-if="pageCount > 1" class="task-pagination" aria-label="任务安排分页">
      <el-button :disabled="disabled || page === 1" @click="page--">上一组任务</el-button><span>{{ page }} / {{ pageCount }}</span>
      <el-button :disabled="disabled || page >= pageCount" @click="page++">下一组任务</el-button>
    </div>
    <p class="task-validation" role="status">{{ validation || '每条任务的时间均已填写，可继续预览金额。' }}</p>
  </section>
</template>
<script setup>
import { computed, ref, watch } from "vue";
import { buildTaskTimes } from "@/utils/serviceCommerce";
import { jingyuClock, jingyuDateRange, jingyuTaskTimesError } from "@/utils/jingyuServices";
const props = defineProps({ modelValue: { type: Array, required: true }, quantity: Number, disabled: Boolean });
const emit = defineEmits(["update:modelValue"]);
const date = ref(""), time = ref("08:00"), weekdays = ref([1, 2, 3, 4, 5, 6, 0]);
const page = ref(1), generationError = ref(""), pageSize = 10;
const days = [1, 2, 3, 4, 5, 6, 0].map((value, index) => ({ value, label: `周${["一", "二", "三", "四", "五", "六", "日"][index]}` }));
const range = computed(() => { props.modelValue; return jingyuDateRange(); });
const validation = computed(() => jingyuTaskTimesError(props.modelValue, props.quantity));
const pageCount = computed(() => Math.max(1, Math.ceil(props.modelValue.length / pageSize)));
const pageItems = computed(() => props.modelValue.slice((page.value - 1) * pageSize, page.value * pageSize));
watch(pageCount, (count) => { page.value = Math.min(page.value, count); });
function generate() {
  if (props.disabled) return;
  const times = buildTaskTimes(date.value, time.value, props.quantity, weekdays.value);
  generationError.value = jingyuTaskTimesError(times, props.quantity);
  if (generationError.value) return;
  emit("update:modelValue", times); page.value = 1;
}
function part(offset, field) {
  const stamp = pageItems.value[offset] || "";
  return field === "date" ? stamp.slice(0, 10).trim() : stamp.slice(11);
}
function update(offset, field, value) {
  if (props.disabled) return;
  const index = (page.value - 1) * pageSize + offset;
  if (index >= props.modelValue.length) return;
  const dateValue = field === "date" ? value : part(offset, "date");
  const timeValue = field === "time" ? value : part(offset, "time");
  const times = [...props.modelValue];
  times[index] = `${dateValue.padEnd(10, ' ')} ${jingyuClock(timeValue)}`;
  generationError.value = ""; emit("update:modelValue", times);
}
</script>
<style scoped>
.jingyu-task-plan { margin-top: 22px; padding-top: 18px; border-top: 1px solid var(--el-border-color-lighter); }
.plan-heading { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 12px; align-items: baseline; } h4 { margin: 0; font-size: 16px; }
.plan-heading p, .generate-actions span, .task-validation { font-size: 13px; line-height: 1.75; color: var(--el-text-color-secondary); }
.builder-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-top: 16px; }
.generate-actions { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; } .generate-actions span { flex: 1 1 210px; }
.task-list { list-style: none; padding: 0; margin: 18px 0; }
.task-row { display: grid; grid-template-columns: 26px minmax(0, 1.2fr) minmax(0, 1fr); gap: 10px; align-items: center; padding: 14px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.task-row :deep(.el-form-item) { margin-bottom: 0; min-width: 0; } .task-index { color: var(--el-text-color-secondary); font-size: 12px; }
.task-pagination { display: flex; align-items: center; justify-content: space-between; gap: 10px; } .task-pagination span { font-variant-numeric: tabular-nums; }
:deep(.el-checkbox-group) { display: flex; flex-wrap: wrap; gap: 0 16px; } :deep(.el-checkbox) { margin-right: 0; }
@media (max-width: 480px) { .builder-grid { grid-template-columns: 1fr; gap: 0; } .task-row { grid-template-columns: 20px minmax(0, 1fr); gap: 10px; } .task-row :deep(.el-form-item:last-child) { grid-column: 2; } }
</style>
