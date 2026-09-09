<template>
  <div class="wuxin-plan">
    <div class="plan-grid">
      <el-form-item v-if="withStartDate" label="开始日期（北京时间）"
        ><el-date-picker
          :model-value="modelValue.startDate"
          type="date"
          value-format="YYYY-MM-DD"
          @update:model-value="set('startDate', $event)"
      /></el-form-item>
      <el-form-item label="开始时间"
        ><el-time-select
          :model-value="modelValue.runTime"
          start="00:00"
          end="23:55"
          step="00:05"
          @update:model-value="set('runTime', $event)"
      /></el-form-item>
      <el-form-item label="结束时间"
        ><el-time-select
          :model-value="modelValue.endTime"
          start="00:00"
          end="23:55"
          step="00:05"
          @update:model-value="set('endTime', $event)"
      /></el-form-item>
      <el-form-item label="配速（分钟 / 公里）"
        ><el-input-number
          :model-value="
            modelValue.pace === '' || modelValue.pace == null
              ? undefined
              : Number(modelValue.pace)
          "
          :min="3"
          :max="15"
          :step="0.5"
          :precision="1"
          @update:model-value="
            set('pace', $event == null ? '' : String($event))
          "
      /></el-form-item>
    </div>
    <el-form-item label="执行星期"
      ><el-checkbox-group
        :model-value="weekdays"
        @update:model-value="set('weekdays', $event.sort().join(','))"
        ><el-checkbox
          v-for="(day, index) in ['一', '二', '三', '四', '五', '六', '日']"
          :key="index"
          :value="String(index + 1)"
          >周{{ day }}</el-checkbox
        ></el-checkbox-group
      ></el-form-item
    >
    <el-form-item label="客户备注（选填）"
      ><el-input
        :model-value="modelValue.message"
        type="textarea"
        maxlength="500"
        :rows="2"
        @update:model-value="set('message', $event)"
    /></el-form-item>
  </div>
</template>
<script setup>
import { computed } from "vue";
const props = defineProps({
  modelValue: { type: Object, required: true },
  withStartDate: { type: Boolean, default: true },
});
const emit = defineEmits(["update:modelValue"]);
const weekdays = computed(() =>
  (props.modelValue.weekdays || "").split(",").filter(Boolean),
);
function set(key, value) {
  emit("update:modelValue", { ...props.modelValue, [key]: value });
}
</script>
<style scoped>
.plan-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 18px;
}
.plan-grid :deep(.el-date-editor),
.plan-grid :deep(.el-select),
.plan-grid :deep(.el-input-number) {
  width: 100%;
}
@media (max-width: 600px) {
  .plan-grid {
    grid-template-columns: 1fr;
  }
}
</style>
