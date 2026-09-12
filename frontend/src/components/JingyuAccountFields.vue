<template>
  <div class="jingyu-account-fields">
    <el-form-item :label="project === 'keep' ? '手机号' : '账号 UID'">
      <el-input :model-value="modelValue.account" :aria-label="project === 'keep' ? '手机号' : '账号 UID'"
        inputmode="numeric" autocomplete="off" :maxlength="project === 'keep' ? 15 : 19"
        :disabled="disabled" @update:model-value="update('account', $event)" />
    </el-form-item>
    <el-form-item v-if="project === 'keep'" label="账号密码">
      <el-input :model-value="modelValue.password" type="password" show-password autocomplete="new-password"
        maxlength="128" aria-label="账号密码" :disabled="disabled" @update:model-value="update('password', $event)" />
    </el-form-item>
    <p class="account-note">{{ project === 'keep' ? '查询账号后选择跑区；密码中的空格会原样保留。'
      : '填写已授权账号的 UID。查询只读取账号信息，不会下单或修改授权。' }}</p>
    <el-button type="primary" plain :loading="loading" :disabled="disabled || !authorized || !!accountError"
      @click="$emit('lookup')">查询账号与跑区</el-button>
    <section v-if="lookup" class="account-result" aria-label="账号查询结果">
      <template v-if="project === 'bdlp'">
        <div class="account-state"><strong>{{ lookup.suggested.schoolName }}</strong>
          <el-tag :type="lookup.suggested.authorizationState === 'VALID' ? 'success' : 'danger'">
            {{ lookup.suggested.authorizationState === 'VALID' ? '授权有效' : '授权已失效' }}</el-tag></div>
        <dl><div><dt>授权方式</dt><dd>{{ lookup.suggested.authorizationType }}</dd></div>
          <div><dt>授权时间</dt><dd>{{ lookup.suggested.authorizedAt }}（北京时间）</dd></div>
          <div><dt>参考最低距离</dt><dd>{{ lookup.suggested.minDistance }} 公里</dd></div></dl>
        <el-alert v-if="lookup.suggested.authorizationState === 'EXPIRED'" type="warning" :closable="false"
          title="账号授权已失效，请完成授权后重新查询；现在不能下单。" />
      </template>
      <el-form-item label="跑区">
        <el-select :model-value="modelValue.zoneId" aria-label="跑区" placeholder="请选择跑区，不会自动选择"
          :disabled="disabled" @update:model-value="update('zoneId', $event)">
          <el-option v-for="choice in lookup.choices" :key="choice.value" :label="choice.label" :value="choice.value" />
        </el-select>
      </el-form-item>
      <div v-if="project === 'keep'" class="pace-grid">
        <el-form-item label="最快配速（分钟/公里）"><el-select :model-value="modelValue.minMinute" aria-label="最快配速（分钟/公里）"
          placeholder="选择最快配速" :disabled="disabled" @update:model-value="update('minMinute', $event)">
          <el-option v-for="n in [3, 4, 5, 6]" :key="n" :label="`${n} 分钟/公里`" :value="String(n)" /></el-select></el-form-item>
        <el-form-item label="最慢配速（分钟/公里）"><el-select :model-value="modelValue.maxMinute" aria-label="最慢配速（分钟/公里）"
          placeholder="选择最慢配速" :disabled="disabled" @update:model-value="update('maxMinute', $event)">
          <el-option v-for="n in [8, 9, 10, 11, 12, 13, 14, 15]" :key="n" :label="`${n} 分钟/公里`" :value="String(n)" /></el-select></el-form-item>
      </div>
      <el-form-item v-else label="跑步类型"><el-radio-group :model-value="modelValue.runType" aria-label="跑步类型"
        :disabled="disabled" @update:model-value="update('runType', $event)">
        <el-radio value="1">有效跑</el-radio><el-radio value="2">自由跑</el-radio></el-radio-group></el-form-item>
      <p class="account-note">{{ project === 'keep' ? '自由跑不保证计入有效成绩，请核对跑区与执行安排。'
        : '请按规则填写距离和执行时间，成绩以实际结果为准。账号信息只用于核对，不能在此修改。' }}</p>
    </section>
  </div>
</template>
<script setup>
import { computed } from "vue";
import { jingyuAccountError } from "@/utils/jingyuServices";
const props = defineProps({ modelValue: { type: Object, required: true }, project: { type: String, required: true },
  lookup: { type: Object, default: null }, authorized: Boolean, disabled: Boolean, loading: Boolean });
const emit = defineEmits(["update:modelValue", "lookup", "invalidate"]);
const accountError = computed(() => jingyuAccountError(props.project, props.modelValue));
function update(key, value) {
  if (props.disabled) return;
  emit("update:modelValue", { ...props.modelValue, [key]: value });
  if (["account", "password"].includes(key)) emit("invalidate");
}
</script>
<style scoped>
.account-note { margin: 10px 0 16px; font-size: 13px; line-height: 1.75; color: var(--el-text-color-secondary); }
.account-result { margin-top: 18px; padding: 18px; border: 1px solid var(--el-border-color-lighter); border-radius: 12px; background: var(--el-fill-color-extra-light); }
.account-state { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; justify-content: space-between; }
dl { margin: 16px 0; font-size: 13px; line-height: 1.75; }
dl div { display: flex; gap: 12px; margin-bottom: 6px; } dt { color: var(--el-text-color-secondary); flex: 0 0 84px; } dd { margin: 0; overflow-wrap: anywhere; }
.pace-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.el-select { width: 100%; } .el-alert { margin-bottom: 16px; }
@media (max-width: 480px) { .pace-grid { grid-template-columns: 1fr; gap: 0; } .account-result { padding: 14px; } }
</style>
