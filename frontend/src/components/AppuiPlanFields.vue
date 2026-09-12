<template>
  <div class="appui-plan">
    <el-form-item label="打卡地址">
      <el-input :model-value="modelValue.address" type="textarea" :rows="2"
        aria-label="打卡地址" maxlength="500" show-word-limit placeholder="填写完整地址，并确认符合账号要求"
        @update:model-value="set('address', $event)" />
    </el-form-item>
    <div class="appui-time-grid">
      <el-form-item label="上班时间">
        <el-input :model-value="modelValue.startTime" type="time" aria-label="上班时间"
          @update:model-value="set('startTime', $event)" />
      </el-form-item>
      <el-form-item label="下班时间">
        <el-input :model-value="modelValue.endTime" type="time" aria-label="下班时间"
          @update:model-value="set('endTime', $event)" />
      </el-form-item>
    </div>
    <el-form-item label="执行星期（至少选择一天）">
      <el-checkbox-group :model-value="selection('weekdays')" aria-label="执行星期"
        @update:model-value="setSelection('weekdays', $event)">
        <el-checkbox v-for="(day, index) in ['一', '二', '三', '四', '五', '六', '日']"
          :key="day" :value="String(index + 1)">周{{ day }}</el-checkbox>
      </el-checkbox-group>
    </el-form-item>
    <el-form-item label="报告类型（至少选择一种）">
      <el-checkbox-group :model-value="selection('reports')" aria-label="报告类型"
        @update:model-value="setSelection('reports', $event)">
        <el-checkbox v-for="(label, index) in ['日报', '周报', '月报']"
          :key="label" :value="String(index + 1)">{{ label }}</el-checkbox>
      </el-checkbox-group>
    </el-form-item>
    <p class="appui-plan-note">时间按北京时间填写。执行星期与服务天数分别设置，修改安排不会增加天数。</p>
  </div>
</template>
<script setup>
const props = defineProps({ modelValue: { type: Object, required: true } });
const emit = defineEmits(["update:modelValue"]);
const selection = (key) => String(props.modelValue[key] || "").split(",").filter(Boolean);
const set = (key, value) => emit("update:modelValue", { ...props.modelValue, [key]: value ?? "" });
const setSelection = (key, values) => set(key, [...values].sort().join(","));
</script>
<style scoped>
.appui-time-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.appui-plan :deep(.el-checkbox-group) { display: flex; flex-wrap: wrap; gap: 4px 12px; }
.appui-plan :deep(.el-checkbox) { min-height: 44px; margin-right: 0; padding-right: 8px; }
.appui-plan :deep(.el-input__wrapper) { min-height: 44px; box-sizing: border-box; }
.appui-plan :deep(.el-input__inner) { min-width: 0; }
.appui-plan-note { margin: 0; color: var(--el-text-color-regular); font-size: 13px; line-height: 1.7; }
@media (max-width: 380px) { .appui-time-grid { grid-template-columns: 1fr; gap: 0; } }
</style>
