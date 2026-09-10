<template>
  <div class="total-distance-fields">
    <el-alert title="一次购买总公里数，不按次数或每次距离计费。" type="info" :closable="false" />
    <div class="distance-inputs">
      <el-form-item label="总公里数" class="distance-full">
        <el-input :model-value="distance" @update:model-value="emit('update:distance', $event)"
          inputmode="decimal" maxlength="9" placeholder="例如 120.50" aria-label="总公里数">
          <template #append>公里</template>
        </el-input>
      </el-form-item>
      <el-form-item label="开始时间">
        <el-input :model-value="modelValue.startTime" @update:model-value="set('startTime', $event)"
          type="time" min="06:00" max="22:59" step="60" aria-label="开始时间" />
      </el-form-item>
      <el-form-item label="结束时间">
        <el-input :model-value="modelValue.endTime" @update:model-value="set('endTime', $event)"
          type="time" min="06:00" max="22:59" step="60" aria-label="结束时间" />
      </el-form-item>
    </div>
    <p class="distance-help">时段可选 06:00–22:59；结束须晚于开始，不会自动更改填写的时间。</p>
    <el-form-item label="每周执行星期">
      <el-checkbox-group v-model="weekdays" class="distance-weekdays" aria-label="每周执行星期">
        <el-checkbox v-for="(name, index) in ['一', '二', '三', '四', '五', '六', '日']"
          :key="index + 1" :value="index + 1" border>周{{ name }}</el-checkbox>
      </el-checkbox-group>
    </el-form-item>
    <p class="distance-help" :class="{ invalid: distance && error }" aria-live="polite">
      {{ error || "计划填写完整。预览只计算金额，确认后才提交并扣款。" }}
    </p>
    <p class="distance-help">提交后可核对提交状态，暂不提供完成进度、在线修改或自动退款。</p>
  </div>
</template>
<script setup>
import { computed } from "vue";
import { distancePlanError } from "@/utils/totalDistanceServices";
const props = defineProps({ modelValue: { type: Object, required: true }, distance: String });
const emit = defineEmits(["update:modelValue", "update:distance"]);
function set(key, value) { emit("update:modelValue", { ...props.modelValue, [key]: value }); }
const weekdays = computed({
  get: () => String(props.modelValue.weekdays || "").split(",").filter(Boolean).map(Number),
  set: (days) => set("weekdays", [...days].sort((a, b) => a - b).join(",")),
});
const error = computed(() => distancePlanError(props.distance, props.modelValue));
</script>
<style scoped>
.distance-inputs { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 16px; margin-top: 20px; }
.distance-full { grid-column: 1 / -1; }
.distance-help { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.8; }
.distance-help.invalid { color: var(--el-color-danger); }
.distance-weekdays { display: flex; flex-wrap: wrap; gap: 8px; }
.distance-weekdays :deep(.el-checkbox) { margin: 0; height: 44px; padding: 0 12px; }
.total-distance-fields :deep(.el-input__wrapper) { min-height: 44px; box-sizing: border-box; }
.total-distance-fields :deep(.el-input__inner) { min-width: 0; }
@media (max-width: 420px) { .distance-inputs { gap: 0 10px; } }
</style>
