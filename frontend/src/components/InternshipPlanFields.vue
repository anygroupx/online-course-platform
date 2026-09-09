<template>
  <div class="internship-fields">
    <div class="internship-heading">
      <h3>{{ internshipProjects[project] }} · 实习计划</h3>
      <el-tag type="info">按服务日计费</el-tag>
    </div>
    <p class="plan-explanation">
      填入本人已获授权的实习信息。上游规则和提交授权不由本平台替代；不要填写他人的账号或伪造资料。
    </p>
    <div class="internship-grid">
      <el-form-item v-if="!editing" label="实习账号"
        ><el-input
          :model-value="modelValue.account"
          placeholder="填写本人实习账号"
          autocomplete="off"
          maxlength="100"
          @update:model-value="set('account', $event)"
      /></el-form-item>
      <el-form-item
        :label="editing ? '更新密码（留空保留原凭据）' : '实习账号密码'"
        ><el-input
          :model-value="modelValue.password"
          type="password"
          show-password
          autocomplete="off"
          maxlength="512"
          @update:model-value="set('password', $event)"
      /></el-form-item>
      <el-form-item v-if="hasSchools && !editing" label="所属学校" class="full"
        ><div class="school-search">
          <el-input
            v-model="schoolKeyword"
            placeholder="输入学校名称后查询"
            maxlength="80"
            @keyup.enter="searchSchools"
          /><el-button
            :disabled="!authorized"
            :loading="schoolLoading"
            @click="searchSchools"
            >查询学校</el-button
          >
        </div>
        <el-select
          :model-value="modelValue.schoolId"
          :placeholder="
            project === 'xxt' ? '手机号登录可不选学校' : '请查询并选择学校'
          "
          clearable
          @update:model-value="selectSchool"
          ><el-option
            v-for="school in schoolItems"
            :key="school.id"
            :value="school.id"
            :label="school.name"
        /></el-select>
        <div v-if="schoolPage > 1 || schoolHasMore" class="school-page">
          <el-button
            text
            :disabled="schoolLoading || schoolPage <= 1"
            @click="searchSchools(-1)"
            >上一页</el-button
          ><el-button
            text
            :disabled="schoolLoading || !schoolHasMore"
            @click="searchSchools(1)"
            >下一页</el-button
          >
        </div>
      </el-form-item>
      <el-form-item v-if="!editing" class="full"
        ><el-button
          :disabled="!authorized || !modelValue.account || !modelValue.password"
          :loading="lookupLoading"
          @click="lookup"
          >读取本人实习资料</el-button
        ><span v-if="lookupNotice" class="lookup-note">{{
          lookupNotice
        }}</span></el-form-item
      >
      <section v-if="adviceItems.length" class="lookup-advice full" aria-label="上游计划建议">
        <h4>上游计划建议 · 尚未修改当前表单</h4>
        <dl><div v-for="item in adviceItems" :key="item.key"><dt>{{ item.label }}</dt><dd>{{ item.value }}</dd></div></dl>
        <p>是否采用由你确认。报告选项、服务日期可能影响报价；超出可购买范围的截止日期不会填入。</p>
        <el-button :disabled="!authorized" @click="applyAdvice">采用这些建议</el-button>
      </section>
      <el-form-item label="姓名"
        ><el-input
          :model-value="modelValue.name"
          maxlength="100"
          @update:model-value="set('name', $event)"
      /></el-form-item>
      <el-form-item label="岗位名称"
        ><el-input
          :model-value="
            modelValue[project === 'zxjy' ? 'customizedGwName' : 'gwName']
          "
          maxlength="100"
          @update:model-value="
            set(project === 'zxjy' ? 'customizedGwName' : 'gwName', $event)
          "
      /></el-form-item>
      <el-form-item
        v-if="['xyb', 'gxy', 'xxt', 'gxzy', 'jxzhjy'].includes(project)"
        label="项目 / 实习计划关键词"
        class="full"
        ><el-input
          :model-value="modelValue.projectName"
          maxlength="120"
          @update:model-value="set('projectName', $event)"
      /></el-form-item>
      <el-form-item label="公司地址" class="full"
        ><el-input
          :model-value="modelValue[companyAddressField]"
          maxlength="512"
          @update:model-value="set(companyAddressField, $event)"
      /></el-form-item>
      <el-form-item label="已获授权的执行地址" class="full"
        ><el-input
          :model-value="modelValue.address"
          maxlength="512"
          placeholder="请核实并填写本人实习地址"
          @update:model-value="set('address', $event)"
      /></el-form-item>
      <el-form-item label="纬度"
        ><el-input
          :model-value="modelValue.lat"
          inputmode="decimal"
          placeholder="-90 至 90"
          maxlength="16"
          @update:model-value="set('lat', $event)"
      /></el-form-item>
      <el-form-item label="经度"
        ><el-input
          :model-value="modelValue.lng"
          inputmode="decimal"
          placeholder="-180 至 180"
          maxlength="16"
          @update:model-value="set('lng', $event)"
      /></el-form-item>
    </div>
    <el-collapse class="location-details"
      ><el-collapse-item title="地区与其他资料（上游要求时填写）" name="details"
        ><div class="internship-grid">
          <el-form-item
            v-for="(label, key) in extraFields"
            :key="key"
            :label="label"
            ><el-input
              :model-value="modelValue[key]"
              maxlength="120"
              @update:model-value="set(key, $event)"
          /></el-form-item></div></el-collapse-item
    ></el-collapse>
    <h3 class="schedule-title">服务周期</h3>
    <div class="internship-grid">
      <el-form-item label="服务截止日（包含当天，北京时间）"
        ><el-date-picker
          :model-value="schedule.endDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="请选择截止日期"
          :disabled-date="internshipEndDateDisabled"
          @update:model-value="updateSchedule('endDate', $event)"
      /></el-form-item>
      <el-form-item v-if="project === 'xyb'" label="运行方式"
        ><el-select
          :model-value="schedule.runMode"
          :disabled="editing"
          @update:model-value="updateSchedule('runMode', $event)"
          ><el-option label="上游授权会话方式" :value="1" /><el-option
            label="账号密码方式"
            :value="2" /><el-option
            label="已绑定微信方式（服务日价 ×5）"
            :value="3" /></el-select
      ></el-form-item>
      <el-form-item :label="project === 'zxjy' ? '执行时间' : '上班时间'"
        ><el-time-picker
          :model-value="schedule.checkInTime"
          value-format="HH:mm:ss"
          format="HH:mm"
          @update:model-value="updateSchedule('checkInTime', $event)"
      /></el-form-item>
      <el-form-item
        v-if="project !== 'zxjy'"
        label="下班时间（无需下班时可清空）"
        ><el-time-picker
          :model-value="schedule.checkOutTime"
          value-format="HH:mm:ss"
          format="HH:mm"
          @update:model-value="updateSchedule('checkOutTime', $event || '')"
      /></el-form-item>
      <el-form-item label="服务星期" class="full"
        ><el-checkbox-group
          :model-value="schedule.weekdays"
          @update:model-value="updateSchedule('weekdays', $event)"
          ><el-checkbox
            v-for="(label, index) in ['一', '二', '三', '四', '五', '六', '日']"
            :key="index"
            :value="index + 1"
            >周{{ label }}</el-checkbox
          ></el-checkbox-group
        ></el-form-item
      >
    </div>
    <div class="report-settings">
      <el-checkbox
        v-if="project !== 'qzt'"
        :model-value="schedule.dailyReport"
        @update:model-value="updateSchedule('dailyReport', $event)"
        >日报</el-checkbox
      ><el-checkbox
        :model-value="schedule.weeklyReport"
        @update:model-value="updateSchedule('weeklyReport', $event)"
        >周报</el-checkbox
      ><el-checkbox
        :model-value="schedule.monthlyReport"
        @update:model-value="updateSchedule('monthlyReport', $event)"
        >月报</el-checkbox
      ><el-checkbox
        :model-value="schedule.skipHolidays"
        @update:model-value="updateSchedule('skipHolidays', $event)"
        >按上游节假日规则执行</el-checkbox
      >
    </div>
    <el-collapse
      ><el-collapse-item title="报告提交日和字数设置" name="reports"
        ><div class="internship-grid">
          <el-form-item label="周报提交日"
            ><el-select
              :model-value="schedule.weeklyReportDay"
              @update:model-value="updateSchedule('weeklyReportDay', $event)"
              ><el-option
                v-for="(label, index) in [
                  '一',
                  '二',
                  '三',
                  '四',
                  '五',
                  '六',
                  '日',
                ]"
                :key="index"
                :label="`周${label}`"
                :value="index + 1" /></el-select></el-form-item
          ><el-form-item label="月报提交日（0 遵循上游默认）"
            ><el-input-number
              :model-value="schedule.monthlyReportDay"
              :min="0"
              :max="31"
              :precision="0"
              @update:model-value="updateSchedule('monthlyReportDay', $event)"
          /></el-form-item>
          <el-form-item
            v-for="(label, type) in {
              day: '日报',
              week: '周报',
              month: '月报',
              summary: '总结',
            }"
            :key="type"
            :label="`${label}字数范围（0 使用默认）`"
            ><div class="word-range">
              <el-input-number
                :model-value="schedule.reportLengths?.[type]?.minSize || 0"
                :min="0"
                :max="10000"
                :precision="0"
                @update:model-value="wordLimit(type, 'minSize', $event)"
              /><span>至</span
              ><el-input-number
                :model-value="schedule.reportLengths?.[type]?.maxSize || 0"
                :min="0"
                :max="10000"
                :precision="0"
                @update:model-value="wordLimit(type, 'maxSize', $event)"
              /></div
          ></el-form-item></div></el-collapse-item
    ></el-collapse>
    <el-checkbox
      :model-value="schedule.randomLocation"
      @update:model-value="updateSchedule('randomLocation', $event)"
      >使用上游允许范围内的定位偏移（须符合账号授权）</el-checkbox
    >
    <el-alert
      class="billing-note"
      type="info"
      :closable="false"
      :title="
        editing
          ? '续期 / 改周期只收新增且未购买日期的费用；删减日期不自动退款。'
          : '服务日按北京时间今天至截止日、所选星期计算，包含今天和截止日。'
      "
      description="这是本平台服务日零售计费规则，并非上游实时价。节假日执行规则不会改变计费天数。取消成功后退回尚未使用且仍在当前计划中的已购服务日费用；新增周期、立即执行均先预览金额。"
    />
  </div>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount } from "vue";
import {
  findServiceSchools,
  lookupServiceAccount,
} from "@/api/serviceCommerce";
import {
  internshipProjects,
  internshipAdviceItems,
  applyInternshipAdvice,
  internshipFields,
} from "@/utils/internshipServices";
import { latestRequest } from "@/utils/pluginIntegrations";
import { internshipEndDateDisabled } from "@/utils/internshipServices";
const props = defineProps({
  modelValue: { type: Object, required: true },
  schedule: { type: Object, required: true },
  project: { type: String, required: true },
  productId: [Number, String],
  authorized: Boolean,
  editing: Boolean,
});
const emit = defineEmits(["update:modelValue", "update:schedule"]);
const hasSchools = computed(() =>
  ["xxy", "xxt", "hzj"].includes(props.project),
);
const companyAddressField = computed(() =>
  props.project === "qzt"
    ? "officialAddress"
    : props.project === "gxy"
      ? "jobAddress"
      : "addressOld",
);
const extraFields = {
  country: "国家",
  province: "省份",
  city: "城市",
  area: "区县",
  adcode: "地区编码",
  phone_name: "已授权设备标识（上游要求时）",
  reason: "申请原因",
  desctext: "说明",
};
const schoolKeyword = ref(""),
  schoolItems = ref([]),
  schoolLoading = ref(false),
  schoolPage = ref(1),
  schoolHasMore = ref(false),
  lookupLoading = ref(false),
  lookupNotice = ref("");
const advice = ref(null);
const adviceItems = computed(() => internshipAdviceItems(advice.value));
function applyAdvice() {
  if (!props.authorized || !advice.value) return;
  emit("update:schedule", applyInternshipAdvice(props.schedule, advice.value));
  advice.value = null;
  lookupNotice.value = "已采用有效建议；请检查计划并重新预览费用，不代表已下单。";
}
const schoolRequest = latestRequest();
let lookupVersion = 0;
function set(key, value) {
  lookupVersion++;
  lookupNotice.value = "";
  advice.value = null;
  lookupLoading.value = false;
  emit("update:modelValue", { ...props.modelValue, [key]: value ?? "" });
}
function updateSchedule(key, value) {
  emit("update:schedule", { ...props.schedule, [key]: value });
}
function wordLimit(type, key, value) {
  updateSchedule("reportLengths", {
    ...props.schedule.reportLengths,
    [type]: {
      minSize: 0,
      maxSize: 0,
      ...props.schedule.reportLengths?.[type],
      [key]: value ?? 0,
    },
  });
}
async function searchSchools(delta = 0) {
  if (!props.authorized || !props.productId) return;
  const ticket = schoolRequest.begin(),
    page =
      typeof delta === "number" && delta !== 0 ? schoolPage.value + delta : 1;
  schoolLoading.value = true;
  try {
    const r = await findServiceSchools(
      props.productId,
      { page, keyword: schoolKeyword.value },
      ticket.signal,
    );
    if (!ticket.current()) return;
    schoolItems.value = r.items;
    schoolPage.value = r.page;
    schoolHasMore.value = r.hasMore;
  } catch {
    if (ticket.current()) schoolItems.value = [];
  } finally {
    if (ticket.current()) schoolLoading.value = false;
  }
}
function selectSchool(id) {
  lookupLoading.value = false;
  const school = schoolItems.value.find((s) => s.id === id);
  lookupVersion++;
  advice.value = null;
  emit("update:modelValue", {
    ...props.modelValue,
    schoolId: school?.id || "",
    school: school?.name || "",
    schoolName: school?.name || "",
  });
}
async function lookup() {
  if (lookupLoading.value || !props.authorized) return;
  const version = ++lookupVersion;
  lookupLoading.value = true;
  try {
    const result = await lookupServiceAccount(props.productId, {
      ...internshipFields(props.modelValue),
      runMode: String(props.schedule.runMode),
    });
    if (version !== lookupVersion) return;
    emit("update:modelValue", { ...props.modelValue, ...result.suggested });
    advice.value = result.advice || null;
    lookupNotice.value = result.notice || "资料已读取，请核对后填写服务周期。";
  } catch {
  } finally {
    if (version === lookupVersion) lookupLoading.value = false;
  }
}
watch(
  () => [props.project, props.productId],
  () => {
    lookupVersion++;
    schoolRequest.invalidate();
    schoolItems.value = [];
    schoolKeyword.value = "";
    lookupNotice.value = "";
  advice.value = null;
  },
);
watch(
  () => [props.schedule.runMode, props.authorized],
  () => {
    lookupVersion++;
    lookupLoading.value = false;
    lookupNotice.value = "";
  advice.value = null;
  },
);
onBeforeUnmount(() => {
  lookupVersion++;
  schoolRequest.invalidate();
});
</script>
<style scoped>
.lookup-advice { border: 1px solid var(--el-color-primary-light-7); background: var(--el-color-primary-light-9); border-radius: 10px; padding: 16px; }
.lookup-advice h4 { margin: 0 0 12px; }
.lookup-advice dl { margin: 0; display: grid; grid-template-columns: repeat(auto-fit,minmax(150px,1fr)); gap: 10px; }
.lookup-advice dt, .lookup-advice p { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.7; }
.lookup-advice dd { margin: 4px 0 0; font-size: 13px; }
.lookup-advice .el-button { min-height: 44px; }
.internship-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 16px 0;
}
.internship-heading h3 {
  margin: 0;
}
.plan-explanation,
.lookup-note {
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}
.internship-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 18px;
}
.full {
  grid-column: 1/-1;
}
.internship-grid :deep(.el-select),
.internship-grid :deep(.el-date-editor) {
  width: 100%;
}
.school-search {
  display: flex;
  width: 100%;
  gap: 8px;
  margin-bottom: 10px;
}
.lookup-note {
  margin-left: 10px;
}
.schedule-title {
  margin: 24px 0 18px;
}
.word-range {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}
.word-range :deep(.el-input-number) {
  min-width: 0;
  width: calc(50% - 12px);
}
.billing-note {
  margin-top: 20px;
}
.report-settings {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 8px 0 16px;
}
.report-settings :deep(.el-checkbox) {
  margin-right: 12px;
}
.location-details {
  margin-bottom: 20px;
}
.internship-fields :deep(.el-checkbox__label) {
  white-space: normal;
}
@media (max-width: 600px) {
  .internship-grid {
    grid-template-columns: 1fr;
  }
  .full {
    grid-column: auto;
  }
  .internship-heading {
    flex-wrap: wrap;
  }
  .school-search {
    flex-wrap: wrap;
  }
  .school-search .el-input {
    min-width: 0;
    flex: 1;
  }
}
</style>
