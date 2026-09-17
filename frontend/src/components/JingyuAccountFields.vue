<template>
  <div class="jingyu-account-fields">
    <section v-if="project === 'yyd'" class="school-section" aria-label="选择就读学校">
      <header class="section-heading"><span class="step-number">01</span><div><h3>选择就读学校</h3><p>先选学校，再查询学号对应的跑步规则。</p></div></header>
      <ServiceSchoolSearch v-if="!modelValue.schoolId || changingSchool" :product-id="productId" :active="authorized && !disabled" :selected-id="modelValue.schoolId || ''"
        :max-page="10" :require-keyword="true" @select="selectSchool" />
      <div v-if="modelValue.schoolName" class="selected-school"><span role="status">已选学校：<strong>{{ modelValue.schoolName }}</strong></span>
        <el-button v-if="!changingSchool" :disabled="disabled || !authorized" @click="changingSchool = true">更换学校</el-button></div>
    </section>
    <header v-if="project === 'yyd'" class="section-heading"><span class="step-number">02</span><div><h3>核对运动账号</h3><p>查询不会下单，账号密码不会保存在浏览器中。</p></div></header>
    <el-form-item :label="accountLabel">
      <el-input :model-value="modelValue.account" :aria-label="accountLabel"
        :inputmode="project === 'yyd' ? 'text' : 'numeric'" autocomplete="off" :maxlength="project === 'yyd' ? 64 : project === 'keep' ? 15 : 19"
        :disabled="disabled" @update:model-value="update('account', $event)" />
    </el-form-item>
    <el-form-item v-if="project !== 'bdlp'" label="账号密码">
      <el-input :model-value="modelValue.password" type="password" show-password autocomplete="new-password"
        maxlength="128" aria-label="账号密码" :disabled="disabled" @update:model-value="update('password', $event)" />
    </el-form-item>
    <p class="account-note">{{ project === 'yyd' ? '使用所选学校的学号和运动账号密码；更换学校后需要重新查询。' : project === 'keep' ? '查询账号后选择跑区；密码中的空格会原样保留。'
      : '填写已授权账号的 UID。查询只读取账号信息，不会下单或修改授权。' }}</p>
    <el-button type="primary" plain :loading="loading" :disabled="disabled || !authorized || !!accountError"
      @click="$emit('lookup')">{{ project === 'yyd' ? '查询账号与规则' : '查询账号与跑区' }}</el-button>
    <section v-if="lookup" class="account-result" aria-label="账号查询结果">
      <template v-if="project === 'yyd'">
        <header class="section-heading"><span class="step-number">03</span><div><h3>选择跑步规则</h3><p>{{ lookup.suggested.schoolName }} · {{ lookup.schoolRules.length }} 项可选规则，不会自动选择。</p></div></header>
        <div class="school-rules" role="radiogroup" aria-label="学校跑步规则">
          <label v-for="rule in lookup.schoolRules" :key="rule.id" class="rule-option" :class="{ selected: modelValue.runRuleId === rule.id }">
            <input type="radio" name="school-run-rule" :value="rule.id" :checked="modelValue.runRuleId === rule.id" :disabled="disabled"
              @change="update('runRuleId', rule.id)" />
            <span><strong>{{ rule.zoneName }}</strong><small>每次至少 {{ rule.minDistance }} 公里</small></span>
          </label>
        </div>
        <div v-if="selectedRule" class="rule-guidance" role="status"><span>已选规则：每次至少 <strong>{{ selectedRule.minDistance }} 公里</strong></span>
          <el-button plain :disabled="disabled" @click="$emit('distance', selectedRule.minDistance)">采用最低距离</el-button></div>
        <p class="account-note">请继续填写购买次数与每次任务时间；完成任务不等同于取得有效成绩。</p>
      </template>
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
      <el-form-item v-if="project !== 'yyd'" label="跑区">
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
      <el-form-item v-else-if="project === 'bdlp'" label="跑步类型"><el-radio-group :model-value="modelValue.runType" aria-label="跑步类型"
        :disabled="disabled" @update:model-value="update('runType', $event)">
        <el-radio value="1">有效跑</el-radio><el-radio value="2">自由跑</el-radio></el-radio-group></el-form-item>
      <p v-if="project !== 'yyd'" class="account-note">{{ project === 'keep' ? '自由跑不保证计入有效成绩，请核对跑区与执行安排。'
        : '请按规则填写距离和执行时间，成绩以实际结果为准。账号信息只用于核对，不能在此修改。' }}</p>
    </section>
  </div>
</template>
<script setup>
import { computed, ref } from "vue";
import { jingyuAccountError } from "@/utils/jingyuServices";
import ServiceSchoolSearch from "@/components/ServiceSchoolSearch.vue";
const props = defineProps({ modelValue: { type: Object, required: true }, project: { type: String, required: true },
  productId: { type: [String, Number], required: true },
  lookup: { type: Object, default: null }, authorized: Boolean, disabled: Boolean, loading: Boolean });
const emit = defineEmits(["update:modelValue", "lookup", "invalidate", "distance"]);
const accountError = computed(() => jingyuAccountError(props.project, props.modelValue));
const changingSchool = ref(false);
const accountLabel = computed(() => props.project === "yyd" ? "学号" : props.project === "keep" ? "手机号" : "账号 UID");
const selectedRule = computed(() => props.lookup?.schoolRules?.find((rule) => rule.id === props.modelValue.runRuleId));
function selectSchool(school) {
  if (props.disabled || !props.authorized) return;
  emit("update:modelValue", { ...props.modelValue, schoolId: school.id, schoolName: school.name, runRuleId: "" });
  emit("invalidate");
  changingSchool.value = false;
}
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
.school-section { margin-bottom: 24px; }
.section-heading { display: flex; align-items: flex-start; gap: 12px; margin: 0 0 16px; }
.section-heading h3 { margin: 0; font-size: 15px; color: var(--el-text-color-primary); }
.section-heading p { margin: 6px 0 0; font-size: 13px; line-height: 1.6; color: var(--el-text-color-secondary); }
.step-number { flex-shrink: 0; display: grid; place-items: center; width: 30px; height: 30px; border-radius: 9px; font-size: 12px; font-weight: 700; font-variant-numeric: tabular-nums; background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.selected-school { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; padding: 12px; margin-bottom: 0; border-radius: 8px; background: var(--el-fill-color-light); font-size: 13px; overflow-wrap: anywhere; }
.school-rules { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.rule-option { display: flex; align-items: flex-start; gap: 10px; padding: 14px; min-height: 44px; border: 1px solid var(--el-border-color); border-radius: 10px; background: var(--el-bg-color); cursor: pointer; }
.rule-option.selected { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.rule-option:focus-within { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.rule-option input { margin: 3px 0 0; accent-color: var(--el-color-primary); flex-shrink: 0; }
.rule-option span { min-width: 0; overflow-wrap: anywhere; }
.rule-option strong { font-size: 14px; line-height: 1.5; }.rule-option small { display: block; margin-top: 6px; font-size: 12px; color: var(--el-text-color-secondary); }
.rule-guidance { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; justify-content: space-between; margin-top: 16px; font-size: 13px; line-height: 1.7; }
.rule-guidance .el-button { min-height: 44px; }
@media (max-width: 560px) { .school-rules { grid-template-columns: 1fr; } }
@media (max-width: 480px) { .pace-grid { grid-template-columns: 1fr; gap: 0; } .account-result { padding: 14px; } }
</style>
