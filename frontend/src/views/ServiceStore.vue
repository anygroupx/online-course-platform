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
        <h2>{{ item.title }}</h2>
        <p class="description">
          {{ item.description || "选择服务计划，确认价格后使用平台余额下单。" }}
        </p>
        <div class="service-tags">
          <span
            v-for="action in item.capabilities.filter(
              (x) => !['CREATE', 'LOOKUP'].includes(x),
            )"
            :key="action"
            >{{ actionNames[action] }}</span
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
      destroy-on-close
      :title="selected?.title || '服务下单'"
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
            />
            <div
              v-else-if="selected.providerType === 'jiguang'"
              class="form-grid"
            >
              <el-form-item label="学校名称" class="full"
                ><el-input
                  v-model="fields.schoolName"
                  placeholder="填写学校全称"
                  maxlength="120"
                /><el-button
                  text
                  @click="searchSchools"
                  :disabled="!fields.schoolName"
                  >查询学校</el-button
                >
                <div v-if="schoolResults.length" class="school-options">
                  <el-button
                    v-for="school in schoolResults"
                    :key="school.id"
                    text
                    @click="
                      fields.schoolName = school.name;
                      schoolResults = [];
                    "
                    >{{ school.name }}</el-button
                  >
                </div>
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
                    maxlength="200"
                    @input="clearLookup"
                /></el-form-item>
                <el-form-item
                  v-if="selected.project === 'sdxy'"
                  label="学校名称"
                  class="full"
                  ><el-input
                    v-model="fields.schoolName"
                    maxlength="120"
                    @input="clearLookup"
                /></el-form-item>
              </div>
              <el-button
                v-if="!usesServiceAccountSession(selected)"
                type="primary"
                plain
                :disabled="
                  !consent ||
                  (selected.providerType === 'wuxin'
                    ? !fields.authCode
                    : !fields.account || !fields.password)
                "
                :loading="lookupLoading"
                @click="lookup"
                >查询账号与可用计划</el-button
              >
              <div v-if="lookupResult" class="lookup-result">
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
            <h3>购买计划</h3>
            <div class="form-grid">
              <el-form-item label="购买次数"
                ><el-input-number
                  v-model="quantity"
                  :min="1"
                  :max="365"
                  :precision="0" /></el-form-item
              ><el-form-item label="每次距离（公里）"
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
      @close="quote = null"
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
  findServiceSchools,
  previewServiceOrder,
} from "@/api/serviceCommerce";
import {
  serviceNames,
  actionNames,
  fieldNames,
  moneyText,
  buildTaskTimes,
  serviceFormFields,
  isHeishaFaceService,
  usesServiceAccountSession,
} from "@/utils/serviceCommerce";
import InternshipPlanFields from "@/components/InternshipPlanFields.vue";
import FlashAccountFields from "@/components/FlashAccountFields.vue";
import HeishaFaceFields from "@/components/HeishaFaceFields.vue";
import { newInternshipSchedule } from "@/utils/internshipServices";
import WuxinPlanFields from "@/components/WuxinPlanFields.vue";
import ServiceQuoteConfirm from "@/components/ServiceQuoteConfirm.vue";
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
const accountSessionId = ref(null);
const lookupResult = ref(null),
  lookupLoading = ref(false),
  busy = ref(false),
  quote = ref(null),
  schoolResults = ref([]);
const startDate = ref(""),
  runTime = ref("08:00"),
  weekdays = ref([0, 1, 2, 3, 4, 5, 6]),
  repair = ref(false);
let selectionVersion = 0;
const schoolRequest = latestRequest();
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
    !lookupLoading.value &&
    selected.value &&
    (selected.value.providerType === "sxdk_tw"
      ? fields.value.account &&
        fields.value.password &&
        fields.value.name &&
        fields.value.address &&
        internshipSchedule.value.endDate &&
        internshipSchedule.value.weekdays.length
      : selected.value.providerType === "jiguang"
        ? fields.value.schoolName &&
          fields.value.studentName &&
          fields.value.studentAccount
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
  internshipSchedule.value = newInternshipSchedule();
  selectionVersion++;
  schoolRequest.invalidate();
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
  quantity.value = 10;
  distance.value = "2";
  quote.value = null;
  schoolResults.value = [];
  repair.value = false;
  checkout.value = true;
}
function clearLookup() {
  selectionVersion++;
  lookupLoading.value = false;
  lookupResult.value = null;
}
function beforeClose(done) {
  if (busy.value || lookupLoading.value || quote.value) return;
  selectionVersion++;
  fields.value.password = "";
  fields.value.authCode = "";
  accountSessionId.value = null;
  lookupResult.value = null;
  done();
}
async function lookup() {
  if (lookupLoading.value || !selected.value) return;
  const version = ++selectionVersion;
  lookupLoading.value = true;
  try {
    const r = await lookupServiceAccount(
      selected.value.id,
      serviceFormFields(selected.value.providerType, fields.value),
    );
    if (version !== selectionVersion) return;
    lookupResult.value = r;
    Object.assign(fields.value, r.suggested);
  } catch {
    if (version === selectionVersion) lookupResult.value = null;
  } finally {
    if (version === selectionVersion) lookupLoading.value = false;
  }
}
async function searchSchools() {
  const version = selectionVersion;
  const request = schoolRequest.begin();
  try {
    const r = await findServiceSchools(
      selected.value.id,
      {
        page: 1,
        keyword: fields.value.schoolName,
      },
      request.signal,
    );
    if (version === selectionVersion && request.current())
      schoolResults.value = r.items;
  } catch {
    if (request.current()) schoolResults.value = [];
  }
}
async function preview() {
  if (!canPreview.value || busy.value) return;
  busy.value = true;
  try {
    quote.value = await previewServiceOrder(selected.value.id, {
      quantity: selected.value.providerType === "sxdk_tw" ? 0 : quantity.value,
      distance:
        selected.value.providerType === "sxdk_tw" ? null : distance.value,
      schedule:
        selected.value.providerType === "sxdk_tw"
          ? internshipSchedule.value
          : null,
      fields: serviceFormFields(selected.value.providerType, {
        ...fields.value,
        repair: String(repair.value),
      }),
      taskTimes: selected.value.providerType === "flash" ? taskTimes.value : [],
      authorizedAccount: consent.value,
      accountSessionId: usesServiceAccountSession(selected.value)
        ? accountSessionId.value
        : null,
    });
  } catch {
    /* Request layer displays safe server validation errors. */
  } finally {
    busy.value = false;
  }
}
function onResult(result) {
  if (result.state === "SUCCEEDED") {
    fields.value.password = "";
    fields.value.authCode = "";
    ElMessage.success("订单已提交，可在服务订单中查看进度");
    router.push({ path: "/service-orders", query: { focus: result.orderId } });
  } else if (result.orderId)
    ElMessage.warning(
      "订单已保存，结果待核对。请勿重复下单，可在服务订单中继续查看。",
    );
}
watch(filter, () => {
  page.value = 1;
  load();
});
onBeforeUnmount(() => {
  selectionVersion++;
  schoolRequest.invalidate();
  catalogRequest.invalidate();
  fields.value.password = "";
  fields.value.authCode = "";
});
load();
</script>
<style scoped>
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
.school-options {
  display: grid;
  width: 100%;
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
