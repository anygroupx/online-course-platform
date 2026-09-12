<template>
  <el-dialog v-model="open" title="修改任务时间" class="jingyu-task-editor" width="min(560px, 95vw)"
    :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" destroy-on-close>
    <el-alert type="info" :closable="false" title="只修改这一条任务的执行时间，不增加次数，也不额外扣款。" />
    <p class="task-original">原定时间：{{ task?.time }}（北京时间）</p>
    <el-form label-position="top" :disabled="busy">
      <el-form-item label="新执行日期（北京时间）"><el-input v-model="date" type="date" :min="range.min" :max="range.max" aria-label="新执行日期（北京时间）" /></el-form-item>
      <el-form-item label="新执行时间"><el-input v-model="time" type="time" step="1" aria-label="新执行时间" /></el-form-item>
    </el-form>
    <p v-if="validation" class="task-validation" role="status">{{ validation }}</p>
    <template #footer><el-button :disabled="busy" @click="open = false">取消</el-button>
      <el-button type="primary" :loading="busy" :disabled="!!validation" @click="preview">预览时间修改</el-button></template>
  </el-dialog>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount } from "vue";
import { accessToken, authSessionScope } from "@/utils/authSession";
import { previewServiceAction } from "@/api/serviceCommerce";
import { jingyuClock, jingyuDateRange, jingyuTaskTimeError } from "@/utils/jingyuServices";
const props = defineProps({ modelValue: Boolean, task: { type: Object, default: null } });
const emit = defineEmits(["update:modelValue", "quote"]);
const open = computed({ get: () => props.modelValue, set: (value) => emit("update:modelValue", value) });
const date = ref(""), time = ref(""), busy = ref(false);
let generation = 0;
const range = computed(() => { props.modelValue; return jingyuDateRange(); });
function taskValid() {
  return typeof props.task?.id === "string" && /^[A-Za-z0-9_-]{1,64}$/.test(props.task.id) && !/\p{Cc}/u.test(props.task.id) &&
    Number.isInteger(props.task.page) && props.task.page >= 1 && props.task.page <= 19 && typeof props.task.orderId === "string" && /^[A-Za-z0-9_-]{1,64}$/.test(props.task.orderId);
}
const validation = computed(() => !taskValid() ? "请选择有效的任务记录" : jingyuTaskTimeError(date.value, time.value));
watch([() => props.modelValue, () => props.task], () => {
  generation++; busy.value = false;
  date.value = props.modelValue ? props.task?.time?.slice(0, 10) || "" : "";
  time.value = props.modelValue ? props.task?.time?.slice(11, 19) || "" : "";
}, { immediate: true, flush: "sync" });
watch(authSessionScope, () => { generation++; busy.value = false; date.value = time.value = ""; open.value = false; }, { flush: "sync" });
async function preview() {
  if (busy.value || !props.modelValue || !taskValid() || !accessToken.value || jingyuTaskTimeError(date.value, time.value)) return;
  const current = ++generation, task = props.task;
  busy.value = true;
  try {
    const quote = await previewServiceAction(task.orderId, { action: "CHANGE_TIME", quantity: 0,
      fields: { taskId: task.id, page: String(task.page), time: `${date.value} ${jingyuClock(time.value)}` } });
    if (current !== generation || !props.modelValue || props.task !== task) return;
    emit("quote", quote); emit("update:modelValue", false);
  } catch { /* The request layer shows safe validation failures. */ }
  finally { if (current === generation) busy.value = false; }
}
onBeforeUnmount(() => { generation++; });
</script>
<style scoped>
.task-original, .task-validation { color: var(--el-text-color-regular); font-size: 13px; line-height: 1.7; overflow-wrap: anywhere; }
:global(.jingyu-task-editor) { display: flex; flex-direction: column; max-height: calc(100dvh - 48px); margin: 24px auto !important; }
:global(.jingyu-task-editor .el-dialog__body) { overflow: auto; }
:global(.jingyu-task-editor .el-input__wrapper), :global(.jingyu-task-editor .el-button) { min-height: 44px; box-sizing: border-box; }
</style>
