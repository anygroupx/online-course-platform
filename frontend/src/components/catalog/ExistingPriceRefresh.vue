<template>
  <el-drawer
    v-model="open"
    title="Benz · 更新现有价格与说明"
    size="min(1000px,100vw)"
    :close-on-click-modal="false"
    :close-on-press-escape="!busy"
    :before-close="beforeClose"
    destroy-on-close
    class="existing-price-refresh"
  >
    <span class="eyebrow">EXISTING ONLY / 不新增课程</span>
    <h2>保留你的课程编排，只更新价格与说明。</h2>
    <p class="intro">
      对应 Benz
      的仅更新模式。不会重命名、切换分类、改变上下架、修改接口绑定，也不影响已下单价格或用户余额。
    </p>
    <el-alert
      v-if="error"
      :title="error"
      type="warning"
      :closable="false"
      show-icon
    />
    <el-form
      v-if="!batch"
      label-position="top"
      :disabled="busy"
      class="query-fields"
    >
      <el-form-item label="Benz 课程接口"
        ><el-select
          v-model="providerId"
          placeholder="选择已启用的 Benz 接口"
          aria-label="Benz 课程接口"
          ><el-option
            v-for="p in benzProviders"
            :key="p.id"
            :value="p.id"
            :label="p.name" /></el-select
      ></el-form-item>
      <el-form-item label="价格倍率"
        ><el-input
          v-model="multiplier"
          inputmode="decimal"
          placeholder="例如 1.2"
          maxlength="10"
        /><small>十进制计算，最终四舍五入保留两位小数。</small></el-form-item
      >
      <el-form-item label="限定远程分类（可选）"
        ><el-input
          v-model="category"
          maxlength="50"
          placeholder="留空表示所有远程分类"
      /></el-form-item>
      <el-form-item label="排除远程分类（可选）"
        ><el-input
          v-model="skip"
          maxlength="5100"
          placeholder="用英文逗号分隔分类编号"
      /></el-form-item>
      <el-form-item label="更新范围" class="full"
        ><el-radio-group v-model="scope"
          ><el-radio-button value="ALL_EXISTING" label="ALL_EXISTING"
            >全部已关联课程</el-radio-button
          ><el-radio-button value="SELECTED" label="SELECTED"
            >指定商品编号</el-radio-button
          ></el-radio-group
        ></el-form-item
      >
      <el-form-item
        v-if="scope === 'SELECTED'"
        label="远程商品编号"
        class="full"
        ><el-input
          v-model="productIds"
          type="textarea"
          :rows="3"
          placeholder="用英文逗号或换行分隔；最多500个"
          maxlength="25500"
      /></el-form-item>
      <div class="full">
        <el-button
          type="primary"
          :loading="busy"
          :disabled="!canPreview"
          @click="preview"
          >读取并预览变更（不更新）</el-button
        >
        <p class="intro">
          仅查询上游目录，不发起上游写操作。未导入商品不会创建；一次最多500个本地课程。
        </p>
      </div>
    </el-form>
    <template v-else>
      <div class="summary">
        <el-tag
          :type="
            batch.state === 'APPLIED'
              ? 'success'
              : batch.state === 'READY'
                ? 'info'
                : 'warning'
          "
          >{{ labels[batch.state] || "待核对" }}</el-tag
        ><span
          >{{ changedRows.length }} 项变更 ·
          {{ batch.plan.rows.length - changedRows.length }} 项不变</span
        ><span
          >倍率 {{ batch.plan.multiplier }} · 有效至 {{ batch.expiresAt }}</span
        >
      </div>
      <el-alert
        :title="batch.notice"
        :type="batch.state === 'APPLIED' ? 'success' : 'info'"
        :closable="false"
        show-icon
      />
      <p class="intro">
        未导入 {{ batch.plan.notImported }} 项 / 分类排除
        {{ batch.plan.excluded }} 项 / 上游已不存在
        {{ batch.plan.selectedMissing }} 项，均不会创建或删除课程。
      </p>
      <el-empty
        v-if="!batch.plan.rows.length"
        description="当前范围没有可更新的已关联课程"
      />
      <article
        v-for="row in batch.plan.rows"
        :key="row.localId"
        class="price-change"
      >
        <header>
          <div>
            <strong>{{ row.title }}</strong
            ><small>本地 #{{ row.localId }} · 远程 {{ row.remoteId }}</small>
          </div>
          <el-tag :type="row.changed ? 'warning' : 'info'">{{
            row.changed ? "将更新" : "保持不变"
          }}</el-tag>
        </header>
        <div class="price-comparison">
          <span>当前 ¥{{ row.oldPrice }}</span
          ><b>→</b><strong>预览 ¥{{ row.newPrice }}</strong>
        </div>
        <details v-if="row.oldDescription !== row.newDescription">
          <summary>查看说明变化（纯文本）</summary>
          <div class="description-pair">
            <div>
              <small>当前说明</small>
              <pre>{{ row.oldDescription || "（空）" }}</pre>
            </div>
            <div>
              <small>更新后说明</small>
              <pre>{{ row.newDescription || "（清空）" }}</pre>
            </div>
          </div>
        </details>
        <p v-if="!row.descriptionProvided" class="intro">
          上游未提供说明，保留本地内容。
        </p>
      </article>
      <p class="intro">
        批次
        {{
          batch.id
        }}。若本地价格、说明或绑定发生变化，整批停止，不覆盖他人修改。已上架状态、名称与分类变化不会被回写。
      </p>
      <el-checkbox
        v-if="batch.state === 'READY' && !attempted"
        v-model="consent"
        class="consent"
        >我已核对上面所有变更，同意仅更新现有价格与说明</el-checkbox
      >
    </template>
    <template #footer>
      <el-button :disabled="busy" @click="open = false">关闭</el-button>
      <el-button v-if="batch" :loading="busy" @click="recover"
        >检查原批次结果</el-button
      >
      <el-button
        v-if="batch && !attempted && batch.state === 'READY'"
        type="primary"
        :disabled="!consent || !changedRows.length"
        :loading="busy"
        @click="confirm"
        >确认更新 {{ changedRows.length }} 项</el-button
      >
      <el-button
        v-if="batch && batch.state !== 'READY'"
        :disabled="busy"
        @click="reset"
        >新建预览</el-button
      >
    </template>
  </el-drawer>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount } from "vue";
import {
  previewCatalogRefresh,
  getCatalogRefresh,
  confirmCatalogRefresh,
} from "@/api/catalogRefresh";
const props = defineProps({
    modelValue: Boolean,
    providers: { type: Array, default: () => [] },
  }),
  emit = defineEmits(["update:modelValue", "applied"]);
const open = computed({
  get: () => props.modelValue,
  set: (v) => {
    if (!busy.value) emit("update:modelValue", v);
  },
});
const providerId = ref(null),
  multiplier = ref("1"),
  category = ref(""),
  skip = ref(""),
  productIds = ref(""),
  scope = ref("ALL_EXISTING"),
  batch = ref(null),
  busy = ref(false),
  error = ref(""),
  consent = ref(false),
  attempted = ref(false);
const labels = {
  READY: "待核对 · 未更新",
  APPLIED: "已更新",
  STALE: "已变化 · 整批未更新",
  EXPIRED: "已过期 · 未更新",
};
const benzProviders = computed(() =>
  props.providers.filter((p) => p.providerType === "27" && p.status === 1),
);
const list = (value) => [
  ...new Set(
    value
      .split(/[,\n]/)
      .map((v) => v.trim())
      .filter(Boolean),
  ),
];
const canPreview = computed(
  () =>
    providerId.value &&
    /^\d{1,4}(?:\.\d{1,4})?$/.test(multiplier.value) &&
    Number(multiplier.value) > 0 &&
    Number(multiplier.value) <= 1000 &&
    (scope.value !== "SELECTED" || list(productIds.value).length > 0),
);
const changedRows = computed(
  () => batch.value?.plan.rows.filter((row) => row.changed) || [],
);
let generation = 0,
  alive = true,
  announced = null;
const current = (v) => alive && v === generation && props.modelValue;
function beforeClose(done) {
  if (!busy.value) done();
}
function reset() {
  if (busy.value) return;
  generation++;
  batch.value = null;
  error.value = "";
  consent.value = false;
  attempted.value = false;
  announced = null;
}
function show(value) {
  batch.value = value;
  if (value.state === "APPLIED" && announced !== value.id) {
    announced = value.id;
    emit("applied");
  }
}
async function preview() {
  if (busy.value || !canPreview.value) return;
  const v = generation;
  busy.value = true;
  error.value = "";
  try {
    const data = await previewCatalogRefresh({
      providerId: providerId.value,
      scope: scope.value,
      multiplier: multiplier.value,
      categoryId: category.value.trim() || null,
      skipCategoryIds: list(skip.value),
      productIds: scope.value === "SELECTED" ? list(productIds.value) : [],
    });
    if (current(v)) show(data);
  } catch {
    if (current(v))
      error.value =
        "预览未完成，未更新任何课程；请检查接口、范围和原始数据后再预览。";
  } finally {
    if (current(v)) busy.value = false;
  }
}
async function confirm() {
  if (
    busy.value ||
    attempted.value ||
    !consent.value ||
    !changedRows.value.length ||
    batch.value?.state !== "READY"
  )
    return;
  const v = generation,
    id = batch.value.id;
  busy.value = true;
  attempted.value = true;
  error.value = "";
  try {
    const data = await confirmCatalogRefresh(id);
    if (current(v)) show(data);
  } catch {
    if (current(v))
      error.value = "更新请求结果尚未确认。只检查原批次结果，不自动重新提交。";
  } finally {
    if (current(v)) busy.value = false;
  }
}
async function recover() {
  if (busy.value || !batch.value) return;
  const v = generation,
    id = batch.value.id;
  busy.value = true;
  try {
    const data = await getCatalogRefresh(id);
    if (current(v)) {
      show(data);
      if (data.state === "READY" && attempted.value) {
        attempted.value = false;
        consent.value = false;
        error.value =
          "原批次仍可确认；重新核对后仅沿用同一批次编号，不重查上游或新建批次。";
      } else error.value = "";
    }
  } catch {
    if (current(v))
      error.value = "原批次结果读取失败，请稍后再查询；不自动重提更新。";
  } finally {
    if (current(v)) busy.value = false;
  }
}
watch(
  () => props.modelValue,
  () => {
    generation++;
    busy.value = false;
    if (props.modelValue && batch.value) recover();
  },
);
onBeforeUnmount(() => {
  alive = false;
  generation++;
});
</script>
<style scoped>
.eyebrow {
  color: var(--el-color-primary);
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 1.4px;
}
h2 {
  font-size: 22px;
  margin: 10px 0;
}
.intro,
small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.8;
}
.query-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 20px;
  margin-top: 24px;
}
.full {
  grid-column: 1/-1;
}
.summary {
  display: flex;
  align-items: center;
  gap: 15px;
  flex-wrap: wrap;
  font-size: 12px;
  margin: 20px 0;
}
.price-change {
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  padding: 18px;
  margin: 14px 0;
}
.price-change header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.price-change small {
  display: block;
  overflow-wrap: anywhere;
}
.price-comparison {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 14px;
  margin: 14px 0;
}
.price-comparison span,
.price-comparison b {
  color: var(--el-text-color-secondary);
}
summary {
  min-height: 44px;
  cursor: pointer;
  font-size: 12px;
  line-height: 44px;
}
.description-pair {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.description-pair > div {
  background: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 12px;
  min-width: 0;
}
pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-family: inherit;
  font-size: 12px;
}
.consent {
  min-height: 44px;
  height: auto;
}
.consent :deep(.el-checkbox__label) {
  white-space: normal;
  line-height: 1.8;
}
:deep(.el-button) {
  min-height: 44px;
}
:global(.existing-price-refresh .el-drawer__footer) {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
:global(.existing-price-refresh .el-drawer__footer .el-button) {
  min-height: 44px;
  margin-left: 0;
}
@media (max-width: 600px) {
  .query-fields,
  .description-pair {
    grid-template-columns: 1fr;
  }
  .full {
    grid-column: auto;
  }
  .price-change {
    padding: 14px;
  }
  h2 {
    font-size: 20px;
  }
  .price-change header {
    flex-wrap: wrap;
  }
  .price-comparison {
    font-size: 13px;
    gap: 10px;
  }
}
</style>
