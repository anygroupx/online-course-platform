<template>
  <div v-if="display" class="service-status-check" :aria-label="labels.region" role="status">
    <p class="check-time">
      <span>{{ labels.heading }}</span>
      <template v-if="display.time">
        <time :datetime="display.time.datetime">{{ display.time.text }}</time>
        <span class="check-zone">（北京时间）</span>
      </template>
      <span v-else>暂无记录</span>
    </p>
    <p v-if="display.time" class="check-note">{{ labels.note }}</p>
    <p v-if="display.delayed" class="check-delay">
      <span class="delay-mark" aria-hidden="true"></span>
      {{ labels.delayed }}
    </p>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { statusCheckDisplay, statusCheckLabels } from "@/utils/serviceStatusCheck";

const props = defineProps({
  check: { type: Object, default: null },
  providerType: { type: String, default: "" },
});
const labels = computed(() => statusCheckLabels(props.providerType));
const display = computed(() => statusCheckDisplay(props.check));
</script>

<style scoped>
.service-status-check {
  margin-top: 16px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-fill-color-lighter);
  font-size: 12px;
  line-height: 1.7;
}
.check-time {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  column-gap: 8px;
  margin: 0;
  color: var(--el-text-color-regular);
}
.check-time time {
  font-variant-numeric: tabular-nums;
  font-weight: 500;
  overflow-wrap: anywhere;
}
.check-zone,
.check-note {
  color: var(--el-text-color-secondary);
}
.check-note {
  margin: 4px 0 0;
}
.check-delay {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 8px 0 0;
  color: var(--el-text-color-primary);
}
.delay-mark {
  flex: 0 0 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-color-warning);
}
</style>
