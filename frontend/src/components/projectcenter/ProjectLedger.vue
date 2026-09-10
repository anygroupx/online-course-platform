<template>
  <section class="record-report" aria-label="项目资金流水">
    <header class="record-heading"><div><span class="record-eyebrow">TRANSACTIONS / 资金记录</span><h2>每笔结算，有据可查。</h2><p class="record-muted">只展示已结算记录；处理中和待核实操作不计入金额。本页不发起充值、退款或重复提交。</p></div><el-button :loading="loading" @click="refresh">刷新记录</el-button></header>
    <el-alert title="项目账户兑换和客户额度分别核对，不能相加当作收入。不同项目的额度单位不汇总。" type="info" :closable="false" show-icon />
    <el-form label-position="top" class="record-filters" @submit.prevent="apply">
      <el-form-item v-if="admin && !ownerId" label="经营者编号"><el-input v-model="draft.ownerId" inputmode="numeric" maxlength="19" clearable /></el-form-item>
      <el-form-item label="项目编号"><el-input v-model="draft.projectId" inputmode="numeric" maxlength="19" clearable /></el-form-item>
      <el-form-item label="记录类型"><el-select v-model="draft.book"><el-option label="全部类型" value="ALL" /><el-option v-for="(label,key) in recordBooks" :key="key" :label="label" :value="key" /></el-select></el-form-item>
      <el-form-item label="资金方向"><el-select v-model="draft.direction"><el-option label="全部方向" value="ALL" /><el-option label="余额扣款" value="DEBIT" /><el-option label="转回余额" value="CREDIT" /></el-select></el-form-item>
      <el-form-item label="结算开始日期"><el-input v-model="draft.fromDate" type="date" /></el-form-item>
      <el-form-item label="结算结束日期"><el-input v-model="draft.throughDate" type="date" /></el-form-item>
      <el-form-item label="项目名 / 操作编号"><el-input v-model="draft.keyword" maxlength="100" clearable /></el-form-item>
      <el-form-item v-if="draft.clientId" label="当前客户编号"><el-input :model-value="draft.clientId" readonly /></el-form-item>
      <div class="record-actions record-filter-actions"><el-button type="primary" :disabled="!validRecordFilters(draft)" :loading="loading" native-type="submit">查询流水</el-button><el-button :disabled="loading" @click="reset">重置筛选</el-button><small>日期按北京时间，结束日期包含当天；总额对应全部筛选结果，不只当前页。</small></div>
    </el-form>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon role="alert" />
    <el-skeleton v-if="loading" :rows="4" class="record-skeleton" />
    <template v-else-if="result">
      <p class="record-time">查询时间 {{ recordTime(result.checkedAt) }} · 共 {{ result.total }} 笔已结算动作（含零额度开户）</p>
      <div class="record-totals"><article v-for="total in result.totals" :key="total.book" class="record-card"><h3>{{ recordBooks[total.book] }}</h3><dl><div><dt>余额扣款</dt><dd>¥{{ total.debited }}</dd></div><div><dt>转回余额</dt><dd>¥{{ total.returned }}</dd></div><div><dt>净扣款</dt><dd>¥{{ total.netDebited }}</dd></div></dl><small>{{ total.operations }} 笔已结算动作；净扣款不是利润。</small></article></div>
      <el-empty v-if="!result.records.length" description="没有符合条件的已结算记录" :image-size="80" />
      <div class="record-list"><article v-for="row in result.records" :key="row.book + ':' + row.id" class="record-ledger-row"><header><div><span class="record-tag">{{ recordBooks[row.book] }} · {{ recordActions[row.action] }}</span><h4>{{ row.title }}</h4><small>项目 {{ row.projectId }}<template v-if="admin"> · 经营者 {{ row.ownerId }}</template></small></div><div class="record-money" :class="{ credit: row.direction === 'CREDIT' }">{{ row.direction === 'CREDIT' ? '+' : '−' }}¥{{ row.amount }}</div></header>
        <dl><div><dt>项目额度数量</dt><dd>{{ row.units }}</dd></div><div><dt>冻结单价</dt><dd>¥{{ row.unitPrice }} / 单位</dd></div><div><dt>结算后额度</dt><dd>{{ row.subjectBalanceAfter ?? '未记录' }}</dd></div><div><dt>结算后钱包余额</dt><dd>{{ row.walletBalanceAfter === null ? '未记录' : '¥' + row.walletBalanceAfter }}</dd></div><div><dt>申请时间</dt><dd>{{ recordTime(row.requestedAt) }}</dd></div><div><dt>结算时间</dt><dd>{{ recordTime(row.settledAt) }}</dd></div></dl>
        <p class="record-id">操作编号 {{ row.id }}<br />{{ row.book === 'CUSTOMER_CREDIT' ? '客户' : '账户' }}编号 {{ row.subjectId }}</p></article></div>
      <el-pagination v-if="result.total > 20" v-model:current-page="page" :page-size="20" :total="result.total" :disabled="loading" layout="prev,pager,next" @current-change="read" />
    </template>
  </section>
</template>
<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { projectLedger } from '@/api/projectRecords'
import { recordBooks, recordActions, recordTime, recordFilters, validRecordFilters, validLedger } from '@/utils/projectRecords'
import '@/styles/project-records.css'
const props = defineProps({ admin: Boolean, ownerId: Number, projectId: Number, clientId: String, book: String })
const draft = ref({}), applied = ref({}), page = ref(1), result = ref(null), loading = ref(false), error = ref('')
let generation = 0, controller
function initial() { return { ownerId: '', projectId: props.projectId ?? '', clientId: props.clientId ?? '', book: props.book || (props.clientId ? 'CUSTOMER_CREDIT' : 'ALL'), direction: 'ALL', fromDate: '', throughDate: '', keyword: '' } }
function apply() {
  if (!validRecordFilters(draft.value)) return
  applied.value = recordFilters(draft.value, props.admin, props.ownerId); page.value = 1; read()
}
function refresh() { read() }
function reset() { draft.value = initial(); apply() }
async function read() {
  const current = ++generation, expectedPage = page.value
  controller?.abort(); controller = new AbortController(); const signal = controller.signal
  loading.value = true; error.value = ''; result.value = null
  try {
    const data = await projectLedger({ ...applied.value, page: expectedPage, pageSize: 20 }, props.admin, signal)
    if (current !== generation || signal.aborted) return
    if (!validLedger(data) || data.current !== expectedPage) throw new Error('invalid ledger')
    result.value = data
  } catch (e) {
    if (current === generation && !signal.aborted) error.value = e.response?.status === 403 ? '没有读取这些资金记录的权限。' : '资金记录读取失败或数据不完整；请重试，未显示为零金额。'
  } finally { if (current === generation) loading.value = false }
}
watch(() => [props.admin, props.ownerId, props.projectId, props.clientId, props.book], reset, { immediate: true })
onBeforeUnmount(() => { generation++; controller?.abort() })
</script>
