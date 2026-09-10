<template>
  <dl class="total-distance-summary" aria-label="总公里计划快照">
    <div><dt>总公里数</dt><dd>{{ plan.totalDistance }} 公里</dd></div>
    <div><dt>执行时段</dt><dd>{{ plan.startTime }}–{{ plan.endTime }}</dd></div>
    <div class="distance-full"><dt>每周安排</dt><dd>{{ distanceWeekdaysText(plan.weekdays) }}</dd></div>
    <div class="distance-full"><dt>学校 / 方案</dt><dd>{{ plan.schoolName }} · 方案 {{ plan.typeCode }}</dd></div>
  </dl>
</template>
<script setup>
import { distanceWeekdaysText } from "@/utils/totalDistanceServices";
defineProps({ plan: { type: Object, required: true } });
</script>
<style scoped>
.total-distance-summary { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px 20px; padding: 18px; margin: 18px 0; border: 1px solid var(--el-border-color-lighter); border-radius: 14px; background: var(--el-fill-color-extra-light); }
.total-distance-summary dt { color: var(--el-text-color-secondary); font-size: 12px; margin-bottom: 6px; }
.total-distance-summary dd { margin: 0; color: var(--el-text-color-primary); font-size: 14px; overflow-wrap: anywhere; line-height: 1.6; }
.distance-full { grid-column: 1 / -1; }
</style>
