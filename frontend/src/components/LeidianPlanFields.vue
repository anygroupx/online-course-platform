<template>
  <div class="leidian-plan">
    <fieldset v-if="rules.length" class="run-rule-options">
      <legend>可选时间与里程</legend>
      <p>点击采用一项建议，也可在下方自行调整。不会自动购买或增加次数。</p>
      <el-button v-for="(rule, index) in rules" :key="index" class="run-rule"
        :disabled="disabled" @click="useRule(rule)">
        <strong>{{ rule.distance }} 公里</strong><span>{{ rule.startTime }} – {{ rule.endTime }}</span>
      </el-button>
    </fieldset>
    <el-form-item label="开始日期（北京时间）">
      <el-input :disabled="disabled" :model-value="modelValue.startDate" type="date" aria-label="开始日期（北京时间）"
        :min="range.min" :max="range.max" @update:model-value="set('startDate', $event)" />
    </el-form-item>
    <div class="leidian-time-grid">
      <el-form-item label="开始时间">
        <el-input :disabled="disabled" :model-value="modelValue.startTime" type="time" step="1" aria-label="开始时间"
          @update:model-value="set('startTime', $event)" />
      </el-form-item>
      <el-form-item label="结束时间">
        <el-input :disabled="disabled" :model-value="modelValue.endTime" type="time" step="1" aria-label="结束时间"
          @update:model-value="set('endTime', $event)" />
      </el-form-item>
    </div>
    <el-form-item label="执行星期（至少选择一天）">
      <el-checkbox-group :disabled="disabled" :model-value="days" aria-label="执行星期"
        @update:model-value="set('weekdays', [...$event].sort().join(','))">
        <el-checkbox v-for="(day, index) in ['一', '二', '三', '四', '五', '六', '日']"
          :key="day" :value="String(index + 1)">周{{ day }}</el-checkbox>
      </el-checkbox-group>
    </el-form-item>
    <p class="leidian-plan-note">时间均为北京时间，只支持同一天内的时间段。执行星期决定安排日期，不代表已完成次数。</p>
  </div>
</template>
<script setup>
import { computed } from "vue";
import { leidianDateRange } from "@/utils/leidianServices";
const props = defineProps({
  modelValue: { type: Object, required: true },
  rules: { type: Array, default: () => [] },
  disabled: Boolean,
});
const emit = defineEmits(["update:modelValue", "distance"]);
const range = computed(() => { props.modelValue; return leidianDateRange(); });
const days = computed(() => String(props.modelValue.weekdays || "").split(",").filter(Boolean));
const set = (key, value) => { if (!props.disabled) emit("update:modelValue", { ...props.modelValue, [key]: value ?? "" }); };
function useRule(rule) {
  if (props.disabled) return;
  emit("update:modelValue", { ...props.modelValue, startTime: rule.startTime, endTime: rule.endTime });
  emit("distance", rule.distance);
}
</script>
<style scoped>
.leidian-time-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.leidian-plan :deep(.el-input__wrapper), .leidian-plan :deep(.el-checkbox) { min-height: 44px; box-sizing: border-box; }
.leidian-plan :deep(.el-input__inner) { min-width: 0; }
.leidian-plan :deep(.el-checkbox-group) { display: flex; flex-wrap: wrap; gap: 4px 12px; }
.leidian-plan :deep(.el-checkbox) { margin-right: 0; padding-right: 8px; }
.leidian-plan-note, .run-rule-options p { color: var(--el-text-color-regular); font-size: 13px; line-height: 1.7; }
.run-rule-options { min-width: 0; padding: 14px; margin: 0 0 22px; border: 1px solid var(--el-border-color-light); border-radius: 12px; background: var(--el-fill-color-lighter); }
.run-rule-options legend { padding: 0 6px; font-weight: 600; }
.run-rule-options p { margin-top: 0; }
.run-rule { min-height: 52px; height: auto; max-width: 100%; margin: 4px 8px 4px 0; padding: 10px 14px; white-space: normal; text-align: left; }
.run-rule :deep(span) { display: flex; flex-wrap: wrap; gap: 4px 12px; }
@media (max-width: 380px) { .leidian-time-grid { grid-template-columns: 1fr; gap: 0; } }
</style>
