<template>
  <div class="service-store">
    <header class="store-header">
      <div>
        <span class="eyebrow">SERVICES / 服务商城</span>
        <h1>选好服务，安排接下来的计划。</h1>
        <p>下单后可管理进度和售后。</p>
      </div>
      <el-button @click="router.push('/service-orders')"
        >我的服务订单 <el-icon><ArrowRight /></el-icon
      ></el-button>
    </header>
    <section class="store-toolbar">
      <el-radio-group v-model="filter"
        ><el-radio-button value="">全部服务</el-radio-button
        ><el-radio-button
          v-for="(name, type) in serviceNames"
          :key="type"
          :value="type"
          >{{ name }}</el-radio-button
        ></el-radio-group
      ><el-button text :loading="loading" @click="load">刷新商品</el-button>
    </section>
    <el-alert
      v-if="error"
      :title="error"
      type="warning"
      :closable="false"
      show-icon
    />
    <div v-loading="loading" class="service-grid">
      <article
        v-for="item in visibleProducts"
        :key="item.id"
        class="service-card"
      >
        <div class="card-top">
          <span class="service-brand" :class="item.providerType">{{
            serviceNames[item.providerType]
          }}</span
          ><el-tag size="small" :type="item.available ? 'success' : 'info'">{{
            item.available ? "可下单" : "暂不可用"
          }}</el-tag>
        </div>
        <h2>{{ item.displayName ?? item.title }}</h2>
        <p class="description">
          {{ item.description || "选择服务计划，确认价格后使用平台余额下单。" }}
        </p>
        <div class="service-tags">
          <span
            v-for="action in item.capabilities.filter(
              (x) => !['CREATE', 'LOOKUP'].includes(x),
            )"
            :key="action"
            >{{ isTotalDistanceService(item) && action === "SYNC" ? "核对提交状态" : serviceActionName(action, item) }}</span
          >
        </div>
        <footer>
          <div>
            <strong>¥{{ moneyText(item.unitPrice) }}</strong
            ><small
              >{{ item.priceUnit
              }}{{
                item.providerType === "flash" && item.project !== "sdxy"
                  ? " · 补跑×2"
                  : ""
              }}</small
            >
          </div>
          <el-button
            type="primary"
            :disabled="!item.available"
            @click="open(item)"
            >选择服务</el-button
          >
        </footer>
      </article>
    </div>
    <el-empty
      v-if="!loading && !visibleProducts.length"
      description="暂无可购买的服务，请联系管理员上架商品"
    />
    <el-pagination
      v-if="total > 20"
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="prev, pager, next"
      @current-change="load"
    />

    <el-drawer
      v-model="checkout"
      :class="{ 'distance-checkout': isTotalDistanceService(selected), 'appui-checkout': selected?.providerType === 'appui', 'leidian-checkout': isLeidianService(selected), 'jingyu-checkout': isJingyuService(selected) }"
      destroy-on-close
      :title="selected ? (selected.displayName ?? selected.title) : '服务下单'"
      size="min(700px, 100vw)"
      :close-on-click-modal="false"
      :before-close="beforeClose"
    >
      <template v-if="selected">
        <div class="checkout-steps">
          <span>01 选择服务</span><b>02 填写计划</b><span>03 确认下单</span>
        </div>
        <el-form label-position="top" :disabled="busy" class="checkout-form">
          <section>
            <h3>服务对象</h3>
            <el-checkbox v-model="consent"
              >我有权使用此账号及信息，并授权提交</el-checkbox
            >
            <InternshipPlanFields
              v-if="selected.providerType === 'sxdk_tw'"
              v-model="fields"
              v-model:schedule="internshipSchedule"
              :project="selected.project"
              :product-id="selected.id"
              :authorized="consent"
              :active="checkout"
            />
            <div
              v-else-if="selected.providerType === 'jiguang'"
              class="form-grid"
            >
              <el-form-item label="学校名称" class="full">
                <el-input
                  v-model="fields.schoolName"
                  aria-label="学校名称"
                  class="school-name-input"
                  placeholder="填写学校全称"
                  maxlength="120"
                />
                <p class="school-note">可直接填写学校全称，也可在下方查询并选择。</p>
                <ServiceSchoolSearch
                  :key="selected.id"
                  :product-id="selected.id"
                  :active="checkout"
                  :selected-name="fields.schoolName"
                  @select="fields.schoolName = $event.name"
                />
              </el-form-item>
              <el-form-item label="姓名"
                ><el-input
                  v-model="fields.studentName"
                  maxlength="80"
                  placeholder="填写本人姓名" /></el-form-item
              ><el-form-item label="学号"
                ><el-input
                  v-model="fields.studentAccount"
                  maxlength="100"
                  placeholder="填写学号"
              /></el-form-item>
            </div>
            <JingyuAccountFields v-else-if="isJingyuService(selected)" v-model="fields" :project="selected.project"
              :lookup="lookupResult" :authorized="consent" :disabled="busy" :loading="lookupLoading"
              @lookup="lookup" @invalidate="clearLookup" />
            <template v-else-if="isLeidianService(selected)">
              <el-form-item :label="selected.project === '4' ? '手机号' : '账号 UID'">
                <el-input v-model="fields.account" :aria-label="selected.project === '4' ? '手机号' : '账号 UID'"
                  autocomplete="off" :maxlength="selected.project === '4' ? 15 : 64"
                  :inputmode="selected.project === '4' ? 'numeric' : 'text'" @input="clearLookup" />
              </el-form-item>
              <p class="school-note">{{ selected.project === "4"
                ? "使用已授权的数字手机号，不需要查询跑区或填写密码。"
                : "填写已授权账号的 UID，查询学校、跑区和可选时间里程；查询不会下单。" }}</p>
              <template v-if="leidianNeedsRules(selected.project)">
                <el-button type="primary" plain :loading="lookupLoading"
                  :disabled="!consent || !!leidianAccountError(selected.project, fields.account)" @click="lookup">查询跑区与规则</el-button>
                <div v-if="lookupResult" class="lookup-result">
                  <p>学校：<strong>{{ lookupResult.suggested.schoolName }}</strong></p>
                  <el-form-item label="跑区">
                    <el-select v-model="fields.zoneId" aria-label="跑区" placeholder="请选择跑区，不会自动选择">
                      <el-option v-for="choice in lookupResult.choices" :key="choice.value" :label="choice.label" :value="choice.value" />
                    </el-select>
                  </el-form-item>
                </div>
              </template>
            </template>
            <template v-else>
              <FlashAccountFields
                v-if="selected.providerType === 'flash'"
                :key="selected.id"
                :product-id="selected.id"
                :project="selected.project"
                :authorized="consent"
                @verified="accountVerified"
                @working="lookupLoading = $event"
              />
              <HeishaFaceFields
                v-if="isHeishaFaceService(selected)"
                :key="selected.id"
                :product-id="selected.id"
                :authorized="consent"
                @verified="accountVerified"
                @working="lookupLoading = $event"
              />
              <el-form-item
                v-if="selected.providerType === 'wuxin'"
                label="本人授权码"
                ><el-input
                  v-model="fields.authCode"
                  type="password"
                  show-password
                  autocomplete="off"
                  maxlength="512"
                  @input="clearLookup"
              /></el-form-item>
              <div
                v-else-if="!usesServiceAccountSession(selected)"
                class="form-grid"
              >
                <el-form-item label="服务账号 / 手机号"
                  ><el-input
                    v-model="fields.account"
                    autocomplete="off"
                    maxlength="100"
                    @input="clearLookup" /></el-form-item
                ><el-form-item label="服务密码"
                  ><el-input
                    v-model="fields.password"
                    type="password"
                    show-password
                    autocomplete="new-password"
                    :maxlength="selected.providerType === 'appui' ? 128 : 200"
                    @input="clearLookup"
                /></el-form-item>
                <el-form-item
                  v-if="selected.project === 'sdxy' || isTotalDistanceService(selected)"
                  :label="isTotalDistanceService(selected) ? '学校名称（选填）' : '学校名称'"
                  class="full"
                  ><el-input
                    v-model="fields.schoolName"
                    maxlength="120"
                    @input="clearLookup"
                /></el-form-item>
                <el-form-item v-if="selected.providerType === 'appui' && appuiNeedsSchool(selected.project)" label="学校" class="full">
                  <ServiceSchoolSearch :key="selected.id" :product-id="selected.id"
                    :active="checkout && consent" :max-page="150" id-mode="name" :selected-name="fields.schoolName" @select="selectAppuiSchool" />
                </el-form-item>
              </div>
              <el-button
                v-if="!usesServiceAccountSession(selected) && selected.capabilities.includes('LOOKUP')"
                type="primary"
                plain
                :disabled="
                  !consent ||
                  (selected.providerType === 'wuxin'
                    ? !fields.authCode
                    : !fields.account || !fields.password) ||
                  (selected.providerType === 'appui' && appuiNeedsSchool(selected.project) && !fields.schoolName)
                "
                :loading="lookupLoading"
                @click="lookup"
                >{{ selected.providerType === "appui" ? "查询账号" : "查询账号与可用计划" }}</el-button
              >
              <div v-if="lookupResult" class="lookup-result">
                <p v-if="selected.providerType === 'appui'">姓名：<strong>{{ lookupResult.suggested?.studentName || "待核对" }}</strong></p>
                <p v-if="!isHeishaFaceService(selected)">
                  {{ lookupResult?.notice }}
                </p>
                <div class="form-grid">
                  <el-form-item
                    v-for="field in choiceFields"
                    :key="field"
                    :label="fieldNames[field] || field"
                    ><el-select v-model="fields[field]"
                      ><el-option
                        v-for="choice in (lookupResult?.choices || []).filter(
                          (c) => c.field === field,
                        )"
                        :key="choice.value"
                        :label="choice.label"
                        :value="choice.value" /></el-select
                  ></el-form-item>
                </div>
              </div>
            </template>
          </section>
          <section v-if="selected.providerType !== 'sxdk_tw'">
            <h3>{{ isTotalDistanceService(selected) ? "总公里计划" : "购买计划" }}</h3>
            <TotalDistancePlanFields v-if="isTotalDistanceService(selected)" v-model="fields" v-model:distance="distance" />
            <div v-else class="form-grid">
              <el-form-item :label="selected.providerType === 'appui' ? '购买天数' : '购买次数'"
                ><el-input-number
                  v-model="quantity"
                  :min="1"
                  :max="isLeidianService(selected) ? 100 : 365"
                  :aria-label="selected.providerType === 'appui' ? '购买天数' : '购买次数'"
                  :precision="0" /></el-form-item
              ><el-form-item v-if="selected.providerType !== 'appui'" label="每次距离（公里）"
                ><el-select
                  v-if="selected.providerType === 'jiguang'"
                  v-model="distance"
                  ><el-option
                    v-for="km in [
                      '1',
                      '1.2',
                      '1.5',
                      '1.6',
                      '2',
                      '3',
                      '5',
                      '10',
                    ]"
                    :key="km"
                    :value="km"
                    :label="`${km} 公里`" /></el-select
                ><el-input
                  v-else
                  v-model="distance"
                  aria-label="每次距离（公里）"
                  inputmode="decimal"
                  placeholder="按计划限制填写"
              /></el-form-item>
              <el-form-item
                v-if="selected.providerType === 'heisha'"
                label="每日时间"
                ><el-time-select
                  v-model="fields.runTime"
                  start="00:00"
                  end="23:55"
                  step="00:05"
              /></el-form-item>
            </div>
            <template v-if="isJingyuService(selected)">
              <p class="school-note">购买 1–365 次，每次 1–100 公里，最多一位小数。{{ selected.project === "keep"
                ? "每次按距离计费，单次费用先保留两位小数，再计算总额。" : "每次费用与距离无关。" }}具体金额以确认页面为准。</p>
              <JingyuTaskPlanFields :key="selected.id" v-model="jingyuTasks" :quantity="quantity" :disabled="busy" />
              <p v-if="jingyuValidation" class="school-note" role="status">{{ jingyuValidation }}</p>
              <el-alert v-if="checkoutPending" type="warning" :closable="false"
                title="这笔提交仍待核对，请前往服务订单检查原编号，不要重新下单。" />
            </template>
            <template v-if="isLeidianService(selected)">
              <p class="school-note">购买 1–100 次，每次 1–10 公里，最多一位小数。{{ selected.project === "4"
                ? "按每次实际距离计费。" : "每次计费距离最多按 2 公里计算。" }}总额以确认页面的金额为准。</p>
              <LeidianPlanFields v-model="fields" :disabled="busy" :rules="lookupResult?.runRules || []" @distance="distance = $event" />
              <p v-if="leidianValidation" class="school-note" role="status">{{ leidianValidation }}</p>
              <el-alert v-if="checkoutPending" type="warning" :closable="false"
                title="这笔提交仍待核对，请前往服务订单检查原编号，不要重新下单。" />
            </template>
            <AppuiPlanFields v-if="selected.providerType === 'appui'" v-model="fields" />
            <p v-if="selected.providerType === 'appui' && lookupResult && appuiPlanError(fields)" class="schedule-preview">{{ appuiPlanError(fields) }}</p>
            <WuxinPlanFields
              v-if="selected.providerType === 'wuxin'"
              v-model="fields"
            />
            <template v-if="selected.providerType === 'flash'"
              ><div class="form-grid">
                <el-form-item label="首次执行日期（北京时间）"
                  ><el-date-picker
                    v-model="startDate"
                    type="date"
                    value-format="YYYY-MM-DD" /></el-form-item
                ><el-form-item label="计划时间"
                  ><el-time-select
                    v-model="runTime"
                    start="00:00"
                    end="23:55"
                    step="00:05"
                /></el-form-item>
              </div>
              <el-checkbox-group v-model="weekdays"
                ><el-checkbox
                  v-for="(day, index) in [
                    '日',
                    '一',
                    '二',
                    '三',
                    '四',
                    '五',
                    '六',
                  ]"
                  :key="index"
                  :value="index"
                  >周{{ day }}</el-checkbox
                ></el-checkbox-group
              ><el-checkbox v-if="selected.project !== 'sdxy'" v-model="repair"
                >补跑（计价倍率 ×2）</el-checkbox
              >
              <p class="schedule-preview">
                {{
                  taskTimes.length
                    ? `${taskTimes.length} 次 · ${taskTimes[0]} 至 ${taskTimes.at(-1)}`
                    : "请选择有效日期、时间与星期"
                }}
              </p></template
            >
            <el-form-item
              v-if="selected.providerType === 'jiguang'"
              label="订单备注（选填）"
              ><el-input
                v-model="fields.message"
                type="textarea"
                maxlength="500"
                show-word-limit
                :rows="2"
            /></el-form-item>
          </section>
        </el-form>
      </template>
      <template #footer
        ><div class="checkout-footer">
          <span
            >单价 ¥{{ moneyText(selected?.unitPrice) }}
            <small>{{ selected?.priceUnit }}</small></span
          ><el-button
            type="primary"
            size="large"
            :loading="busy"
            :disabled="!canPreview"
            @click="preview"
            >预览金额并下单</el-button
          >
        </div></template
      >
    </el-drawer>
    <ServiceQuoteConfirm
      :quote="quote"
      :provider-type="selected?.providerType"
      @close="closeQuote"
      @result="onResult"
    />
  </div>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { ArrowRight } from "@element-plus/icons-vue";
import {
  listServiceProducts,
  lookupServiceAccount,
  previewServiceOrder,
} from "@/api/serviceCommerce";
import {
  serviceNames,
  serviceActionName,
  fieldNames,
  moneyText,
  buildTaskTimes,
  serviceFormFields,
  isHeishaFaceService,
  usesServiceAccountSession,
  knownOutcome,
} from "@/utils/serviceCommerce";
import ServiceSchoolSearch from "@/components/ServiceSchoolSearch.vue";
import InternshipPlanFields from "@/components/InternshipPlanFields.vue";
import FlashAccountFields from "@/components/FlashAccountFields.vue";
import HeishaFaceFields from "@/components/HeishaFaceFields.vue";
import { newInternshipSchedule } from "@/utils/internshipServices";
import WuxinPlanFields from "@/components/WuxinPlanFields.vue";
import AppuiPlanFields from "@/components/AppuiPlanFields.vue";
import LeidianPlanFields from "@/components/LeidianPlanFields.vue";
import JingyuAccountFields from "@/components/JingyuAccountFields.vue";
import JingyuTaskPlanFields from "@/components/JingyuTaskPlanFields.vue";
import { isJingyuService, jingyuAccountError, jingyuLookupFields, jingyuLookupValid, jingyuOrderError } from "@/utils/jingyuServices";
import { isLeidianService, leidianNeedsRules, leidianAccountError, leidianLookupValid, leidianOrderError } from "@/utils/leidianServices";
import { authSessionScope } from "@/utils/authSession";
import { appuiNeedsSchool, appuiPlanError } from "@/utils/appuiServices";
import ServiceQuoteConfirm from "@/components/ServiceQuoteConfirm.vue";
import TotalDistancePlanFields from "@/components/TotalDistancePlanFields.vue";
import { isTotalDistanceService, distanceOrderValid } from "@/utils/totalDistanceServices";
import { latestRequest } from "@/utils/pluginIntegrations";
const router = useRouter();
const internshipSchedule = ref(newInternshipSchedule());
const products = ref([]),
  total = ref(0),
  page = ref(1),
  loading = ref(false),
  error = ref(""),
  filter = ref("");
const checkout = ref(false),
  selected = ref(null),
  fields = ref({}),
  quantity = ref(10),
  distance = ref("2"),
  consent = ref(false);
const accountSessionId = ref(null), checkoutPending = ref(false);
const jingyuTasks = ref([]);
const jingyuValidation = computed(() => isJingyuService(selected.value)
  ? jingyuOrderError(selected.value, fields.value, quantity.value, distance.value, lookupResult.value, jingyuTasks.value) : "");
const leidianValidation = computed(() => isLeidianService(selected.value)
  ? leidianOrderError(selected.value, fields.value, quantity.value, distance.value, lookupResult.value) : "");
const lookupResult = ref(null),
  lookupLoading = ref(false),
  busy = ref(false),
  quote = ref(null);
const startDate = ref(""),
  runTime = ref("08:00"),
  weekdays = ref([0, 1, 2, 3, 4, 5, 6]),
  repair = ref(false);
let selectionVersion = 0;
const catalogRequest = latestRequest();
const visibleProducts = computed(() =>
  products.value.filter(
    (p) => !filter.value || p.providerType === filter.value,
  ),
);
const choiceFields = computed(() => [
  ...new Set(lookupResult.value?.choices?.map((c) => c.field) || []),
]);
const taskTimes = computed(() =>
  buildTaskTimes(
    startDate.value,
    runTime.value,
    quantity.value,
    weekdays.value,
  ),
);
const canPreview = computed(
  () =>
    consent.value &&
    !checkoutPending.value &&
    !lookupLoading.value &&
    selected.value &&
    (isJingyuService(selected.value)
      ? !jingyuValidation.value
      : isLeidianService(selected.value)
      ? !leidianValidation.value
      : isTotalDistanceService(selected.value)
      ? distanceOrderValid(distance.value, fields.value)
      : selected.value.providerType === "appui"
      ? !!lookupResult.value && !appuiPlanError(fields.value) &&
        Number.isInteger(quantity.value) && quantity.value >= 1 && quantity.value <= 365
      : selected.value.providerType === "sxdk_tw"
      ? fields.value.account &&
        fields.value.password &&
        fields.value.name &&
        fields.value.address &&
        (!["xxy", "hzj"].includes(selected.value.project) || fields.value.schoolId) &&
        internshipSchedule.value.endDate &&
        internshipSchedule.value.weekdays.length
      : selected.value.providerType === "jiguang"
        ? fields.value.schoolName?.trim() &&
          fields.value.studentName?.trim() &&
          fields.value.studentAccount?.trim()
        : !!lookupResult.value) &&
    (!usesServiceAccountSession(selected.value) || !!accountSessionId.value) &&
    (selected.value.providerType !== "flash" ||
      (!!accountSessionId.value && taskTimes.value.length === quantity.value)),
);
async function load() {
  const request = catalogRequest.begin();
  loading.value = true;
  error.value = "";
  try {
    const r = await listServiceProducts({
      page: page.value,
      pageSize: 20,
      providerType: filter.value || undefined,
    });
    if (!request.current()) return;
    products.value = r.records;
    total.value = r.total;
  } catch {
    if (!request.current()) return;
    error.value =
      "服务商城尚未启用或加载失败，请联系管理员检查商品与接口配置。";
  } finally {
    if (request.current()) loading.value = false;
  }
}
function accountVerified(value) {
  accountSessionId.value = value?.id || null;
  lookupResult.value = value?.lookup || null;
  const previous = fields.value;
  fields.value = value?.lookup ? { ...value.lookup.suggested } : {};
  if (value?.lookup && isHeishaFaceService(selected.value)) {
    fields.value.runTime = previous.runTime || "08:00";
    for (const key of ["planOptionId", "fenceOptionId"]) {
      if (
        value.lookup.choices.some(
          (choice) => choice.field === key && choice.value === previous[key],
        )
      )
        fields.value[key] = previous[key];
    }
  }
}
function open(item) {
  accountSessionId.value = null;
  checkoutPending.value = false;
  jingyuTasks.value = [];
  internshipSchedule.value = newInternshipSchedule();
  selectionVersion++;
  lookupLoading.value = false;
  startDate.value = "";
  runTime.value = "08:00";
  weekdays.value = [0, 1, 2, 3, 4, 5, 6];
  selected.value = item;
  fields.value = {
    account: "",
    password: "",
    schoolName: "",
    studentName: "",
    studentAccount: "",
    message: "",
    runTime: "08:00",
    authCode: "",
    startDate: "",
    endTime: "10:00",
    weekdays: "1,2,3,4,5",
    pace: "7",
  };
  lookupResult.value = null;
  consent.value = false;
  quantity.value = isTotalDistanceService(item) ? 1 : 10;
  distance.value = isTotalDistanceService(item) ? "" : "2";
  if (isJingyuService(item)) {
    fields.value = item.project === "keep" ? { account: "", password: "", zoneId: "", minMinute: "", maxMinute: "" }
      : { account: "", zoneId: "", runType: "" };
  }
  if (isLeidianService(item)) {
    fields.value = { account: "", startDate: "", startTime: "", endTime: "", weekdays: "1,2,3,4,5" };
  }
  if (item.providerType === "appui") {
    Object.assign(fields.value, { address: "", startTime: "07:30", endTime: "18:10", reports: "1" });
    distance.value = "";
  }
  if (isTotalDistanceService(item)) {
    fields.value.startTime = "09:00";
    fields.value.endTime = "21:00";
  }
  quote.value = null;
  repair.value = false;
  checkout.value = true;
}
function selectAppuiSchool(school) {
  fields.value.schoolName = school.name;
  clearLookup();
}
function clearLookup() {
  selectionVersion++;
  lookupLoading.value = false;
  lookupResult.value = null;
  if (isLeidianService(selected.value) || isJingyuService(selected.value)) delete fields.value.zoneId;
}
function beforeClose(done) {
  if (busy.value || lookupLoading.value || quote.value) return;
  selectionVersion++;
  fields.value.password = "";
  fields.value.authCode = "";
  if (isLeidianService(selected.value) || isJingyuService(selected.value)) fields.value = {};
  jingyuTasks.value = [];
  accountSessionId.value = null;
  lookupResult.value = null;
  done();
}
async function lookup() {
  if (lookupLoading.value || busy.value || !checkout.value || !consent.value || !selected.value) return;
  const item = selected.value, leidian = isLeidianService(item), jingyu = isJingyuService(item);
  if (jingyu && jingyuAccountError(item.project, fields.value)) return;
  if (leidian && (!leidianNeedsRules(item.project) || leidianAccountError(item.project, fields.value.account))) return;
  clearLookup();
  const version = selectionVersion;
  lookupLoading.value = true;
  try {
    const r = await lookupServiceAccount(item.id, jingyu ? jingyuLookupFields(item.project, fields.value) : leidian
      ? { account: fields.value.account } : serviceFormFields(item.providerType, fields.value));
    if (version !== selectionVersion || !checkout.value || !consent.value) return;
    if (leidian && !leidianLookupValid(r)) {
      ElMessage.warning("跑区与规则信息不完整，请重新查询。");
      return;
    }
    if (jingyu && !jingyuLookupValid(item.project, r)) {
      ElMessage.warning("账号或跑区信息不完整，请重新查询。");
      return;
    }
    lookupResult.value = r;
    if (!leidian && !jingyu) Object.assign(fields.value, r.suggested);
  } catch {
    if (version === selectionVersion) lookupResult.value = null;
  } finally {
    if (version === selectionVersion) lookupLoading.value = false;
  }
}
async function preview() {
  if (!canPreview.value || busy.value || quote.value) return;
  if (isJingyuService(selected.value)) {
    const error = jingyuOrderError(selected.value, fields.value, quantity.value, distance.value, lookupResult.value, jingyuTasks.value);
    if (error) { ElMessage.warning(error); return; }
  }
  if (isLeidianService(selected.value)) {
    const error = leidianOrderError(selected.value, fields.value, quantity.value, distance.value, lookupResult.value);
    if (error) { ElMessage.warning(error); return; }
  }
  const version = selectionVersion;
  busy.value = true;
  try {
    const result = await previewServiceOrder(selected.value.id, {
      quantity: isTotalDistanceService(selected.value) ? 1 : selected.value.providerType === "sxdk_tw" ? 0 : quantity.value,
      distance:
        ["sxdk_tw", "appui"].includes(selected.value.providerType) ? null : distance.value,
      schedule:
        selected.value.providerType === "sxdk_tw"
          ? internshipSchedule.value
          : null,
      fields: serviceFormFields(selected.value.providerType, {
        ...fields.value,
        repair: String(repair.value),
      }, selected.value.project),
      taskTimes: isJingyuService(selected.value) ? [...jingyuTasks.value] : selected.value.providerType === "flash" ? taskTimes.value : [],
      authorizedAccount: consent.value,
      accountSessionId: usesServiceAccountSession(selected.value)
        ? accountSessionId.value
        : null,
    });
    if (version === selectionVersion && checkout.value && consent.value) quote.value = result;
  } catch {
    /* Request layer displays safe server validation errors. */
  } finally {
    busy.value = false;
  }
}
function closeQuote(context) {
  if ((isLeidianService(selected.value) || isJingyuService(selected.value)) && context?.pending) checkoutPending.value = true;
  quote.value = null;
}
function onResult(result) {
  if (isLeidianService(selected.value) || isJingyuService(selected.value)) checkoutPending.value = !knownOutcome(result.state);
  if (result.state === "SUCCEEDED") {
    fields.value.password = "";
    fields.value.authCode = "";
    ElMessage.success(isTotalDistanceService(selected.value)
      ? "已记录提交，可在服务订单中核对状态；执行结果尚未确认"
      : "订单已提交，可在服务订单中查看进度");
    router.push({ path: "/service-orders", query: { focus: result.orderId } });
  } else if (result.orderId)
    ElMessage.warning(
      "订单已保存，结果待核对。请勿重复下单，可在服务订单中继续查看。",
    );
}
watch(consent, (value) => { if (!value && (isLeidianService(selected.value) || isJingyuService(selected.value))) clearLookup(); }, { flush: "sync" });
watch(authSessionScope, () => {
  selectionVersion++; checkout.value = false; selected.value = null; jingyuTasks.value = [];
  fields.value = {}; lookupResult.value = null; accountSessionId.value = null; quote.value = null;
  lookupLoading.value = false; busy.value = false;
}, { flush: "sync" });
watch(filter, () => {
  page.value = 1;
  load();
});
onBeforeUnmount(() => {
  selectionVersion++;
  catalogRequest.invalidate();
  jingyuTasks.value = [];
  fields.value = {};
  lookupResult.value = null;
  accountSessionId.value = null;
});
load();
</script>
<style scoped>
:global(.jingyu-checkout .el-input__wrapper), :global(.jingyu-checkout .el-select__wrapper),
:global(.jingyu-checkout .el-button), :global(.jingyu-checkout .el-checkbox), :global(.jingyu-checkout .el-radio) { min-height: 44px; box-sizing: border-box; }
:global(.jingyu-checkout .el-checkbox__label) { white-space: normal; line-height: 1.7; }
:global(.jingyu-checkout .el-input-number) { min-height: 44px; width: 100%; }
:global(.jingyu-checkout .el-input-number__decrease), :global(.jingyu-checkout .el-input-number__increase) { min-width: 44px; }
:global(.jingyu-checkout .el-input__inner) { min-width: 0; }
:global(.leidian-checkout .el-input__wrapper), :global(.leidian-checkout .el-select__wrapper),
:global(.leidian-checkout .el-button), :global(.leidian-checkout .el-checkbox) { min-height: 44px; box-sizing: border-box; }
:global(.leidian-checkout .el-checkbox__label) { white-space: normal; line-height: 1.6; }
:global(.leidian-checkout .el-input-number) { min-height: 44px; width: 100%; }
:global(.leidian-checkout .el-input-number__decrease), :global(.leidian-checkout .el-input-number__increase) { min-width: 44px; }
:global(.appui-checkout .el-input__wrapper),
:global(.appui-checkout .el-button),
:global(.appui-checkout .el-checkbox) { min-height: 44px; box-sizing: border-box; }
:global(.appui-checkout .el-checkbox__label) { white-space: normal; line-height: 1.6; }
:global(.distance-checkout .el-input__wrapper),
:global(.distance-checkout .el-button),
:global(.distance-checkout .el-checkbox) { min-height: 44px; box-sizing: border-box; }
:global(.distance-checkout .el-input__inner) { min-width: 0; }
:global(.distance-checkout .el-checkbox__label) { white-space: normal; line-height: 1.6; }

.service-store {
  max-width: 1240px;
  margin: auto;
}
.store-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 18px 0 30px;
}
.eyebrow {
  font-size: 11px;
  letter-spacing: 1.6px;
  color: var(--el-color-primary);
  font-weight: 700;
}
.store-header h1 {
  font-size: 28px;
  letter-spacing: -0.8px;
  margin: 10px 0;
}
.store-header p {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.store-toolbar :deep(.el-radio-group) {
  display: flex;
  flex-wrap: wrap;
  row-gap: 8px;
}
.store-toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 22px;
  gap: 12px;
}
.service-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
  min-height: 160px;
}
.service-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 20px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  min-height: 292px;
}
.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.service-brand {
  padding: 7px 12px;
  border-radius: 10px;
  font-weight: 700;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.service-brand.heisha {
  color: var(--el-text-color-primary);
  background: var(--el-fill-color);
}
.service-brand.flash {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}
.service-card h2 {
  font-size: 20px;
  margin: 22px 0 8px;
}
.description {
  color: var(--el-text-color-secondary);
  line-height: 1.8;
  flex: 1;
  font-size: 13px;
}
.service-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin: 10px 0 24px;
}
.service-tags span {
  font-size: 11px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 5px;
  padding: 3px 7px;
  color: var(--el-text-color-secondary);
}
.service-card footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.service-card footer strong {
  font-size: 24px;
  color: var(--el-color-primary);
}
.service-card small {
  display: block;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
.checkout-steps {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  padding-bottom: 24px;
  color: var(--el-text-color-secondary);
}
.checkout-steps b {
  color: var(--el-color-primary);
}
.checkout-form section {
  padding: 8px 0 24px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin-bottom: 18px;
}
.checkout-form h3 {
  font-size: 16px;
  margin: 8px 0 18px;
}
.checkout-form :deep(.el-checkbox) {
  white-space: normal;
  height: auto;
  line-height: 1.7;
  margin-bottom: 18px;
}
.checkout-form :deep(.el-checkbox__label) {
  white-space: normal;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 18px;
}
.full {
  grid-column: 1/-1;
}
.form-grid :deep(.el-select),
.form-grid :deep(.el-date-editor),
.form-grid :deep(.el-input-number) {
  width: 100%;
}
.lookup-result {
  padding: 12px 16px;
  margin-top: 16px;
  background: var(--el-fill-color-light);
  border-radius: 12px;
}
.lookup-result p,
.schedule-preview {
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}
.school-name-input :deep(.el-input__wrapper) { min-height: 44px; box-sizing: border-box; }
.school-note {
  margin: 8px 0 12px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}
.checkout-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.checkout-footer small {
  color: var(--el-text-color-secondary);
}
@media (max-width: 1050px) {
  .service-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .store-header {
    align-items: flex-start;
    flex-direction: column;
    padding-top: 4px;
  }
  .store-header h1 {
    font-size: 23px;
  }
  .store-toolbar :deep(.el-radio-group) {
    display: flex;
    flex-wrap: wrap;
    row-gap: 8px;
  }
  .store-toolbar {
    flex-wrap: wrap;
  }
  .service-grid {
    grid-template-columns: 1fr;
  }
  .form-grid {
    grid-template-columns: 1fr;
  }
  .full {
    grid-column: auto;
  }
  .service-card {
    padding: 20px;
  }
  .checkout-footer {
    flex-wrap: wrap;
  }
  .checkout-footer .el-button {
    width: 100%;
  }
}
</style>
