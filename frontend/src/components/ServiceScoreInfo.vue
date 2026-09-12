<template>
  <el-drawer v-model="open" title="成绩查询信息" size="min(640px, 100vw)" class="service-score-info" destroy-on-close>
    <p class="score-order">{{ order?.title }} · {{ order?.accountLabel }}</p>
    <el-alert type="info" :closable="false" title="这里展示查询返回的文字，不代表成绩已确认，也不会改变订单或余额。" />
    <div role="status" aria-live="polite" class="score-state">
      <p v-if="loading">正在读取成绩查询信息…</p>
      <p v-else-if="error">{{ error }}</p>
    </div>
    <pre v-if="text" class="score-text" aria-label="成绩查询文字">{{ text }}</pre>
    <template #footer><el-button :loading="loading" @click="load">{{ error ? '重试查询' : '重新查询' }}</el-button></template>
  </el-drawer>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount } from "vue";
import { getServiceScoreInfo } from "@/api/serviceCommerce";
import { latestRequest } from "@/utils/pluginIntegrations";
import { accessToken, authSessionScope } from "@/utils/authSession";
import { canReadLeidianRecord } from "@/utils/leidianServices";
const props = defineProps({ modelValue: Boolean, order: { type: Object, default: null } });
const emit = defineEmits(["update:modelValue"]);
const open = computed({ get: () => props.modelValue, set: (value) => emit("update:modelValue", value) });
const text = ref(""), error = ref(""), loading = ref(false), requests = latestRequest();
function clear() { requests.invalidate(); text.value = ""; error.value = ""; loading.value = false; }
async function load() {
  if (loading.value || !props.modelValue || !accessToken.value || !canReadLeidianRecord(props.order)) return;
  const request = requests.begin();
  loading.value = true; text.value = ""; error.value = "";
  try {
    const result = await getServiceScoreInfo(props.order.id, request.signal);
    if (!request.current()) return;
    if (typeof result?.text !== "string" || !result.text.trim() || result.text.length > 2000 || /[\x00-\x08\x0b-\x1f\x7f-\x9f]/.test(result.text))
      throw new Error("Invalid score text");
    text.value = result.text;
  } catch {
    if (request.current()) error.value = "暂时无法读取成绩查询信息，请稍后重试。";
  } finally { if (request.current()) loading.value = false; }
}
watch([() => props.modelValue, () => props.order?.id, () => canReadLeidianRecord(props.order)], () => {
  clear(); if (props.modelValue) load();
}, { immediate: true });
watch(authSessionScope, () => { clear(); open.value = false; }, { flush: "sync" });
onBeforeUnmount(clear);
</script>
<style scoped>
.score-order { color: var(--el-text-color-regular); overflow-wrap: anywhere; }
.score-state { margin-top: 20px; color: var(--el-text-color-regular); line-height: 1.7; }
.score-text { font: inherit; font-size: 14px; line-height: 1.9; white-space: pre-wrap; overflow-wrap: anywhere; padding: 20px; background: var(--el-fill-color-lighter); border: 1px solid var(--el-border-color-light); border-radius: 12px; }
:global(.service-score-info .el-button) { min-height: 44px; }
</style>
