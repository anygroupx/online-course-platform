<template>
  <section class="record-report record-subjects">
    <header><h3>{{ client ? '客户账户' : '项目账户' }}</h3><el-button :loading="loading" @click="read">刷新{{ client ? '客户' : '账户' }}</el-button></header>
    <p class="record-muted">{{ client ? '客户额度与项目账户分别记录，充值额度不代表已购买服务。' : '余额为上次记录值，不代表当前可用余额。本页不会刷新账户或发起资金操作。' }}</p>
    <el-form v-if="client" label-position="top" class="record-inline-filter" @submit.prevent="apply">
      <el-form-item label="筛选项目编号"><el-input v-model="draft.projectId" inputmode="numeric" maxlength="19" clearable /></el-form-item>
      <el-form-item label="客户状态"><el-select v-model="draft.status" style="width:150px"><el-option label="全部状态" value="ALL" /><el-option label="可用" value="ACTIVE" /><el-option label="已暂停" value="SUSPENDED" /><el-option label="已关闭" value="CLOSED" /></el-select></el-form-item>
      <el-button type="primary" native-type="submit" :disabled="!validRecordFilters(draft)" :loading="loading">筛选客户</el-button>
    </el-form>
    <el-alert v-if="error" :title="error" type="warning" show-icon :closable="false" />
    <el-skeleton v-if="loading" :rows="4" class="record-skeleton" />
    <template v-else-if="loaded">
      <el-empty v-if="!rows.length" description="没有符合条件的账户" :image-size="70" />
      <div class="record-grid"><article v-for="row in rows" :key="row.id" class="record-card"><header><div><h4>{{ client ? row.label : row.title }}</h4><small>{{ client ? row.title + ' · ' : '' }}项目 {{ row.projectId }}</small></div><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ recordStatus(row.status) }}</el-tag></header>
        <dl><div><dt>{{ client ? '客户额度' : '上次记录余额' }}</dt><dd>{{ (client ? row.balance : row.cachedBalance) ?? '未记录' }}</dd></div><div><dt>冻结单价</dt><dd>¥{{ row.unitPrice }} / 单位</dd></div><div><dt>可转回额度</dt><dd>{{ row.refundableUnits }}</dd></div><div><dt>剩余实付可退预算</dt><dd>¥{{ row.refundBudget }}</dd></div></dl>
        <p class="record-funding-line">已结算扣款 ¥{{ row.debited }} · 已转回 ¥{{ row.returned }}<br /><template v-if="!client">余额记录时间 {{ recordTime(row.balanceCheckedAt) }}<br />仍待核实 {{ row.unresolvedOperations }} 笔操作，未计入结算金额</template><template v-else>创建时间 {{ recordTime(row.createdAt) }}</template></p>
        <p class="record-id">{{ row.id }}</p><el-button @click="$emit('ledger', { projectId: row.projectId, clientId: client ? row.id : undefined, book: client ? 'CUSTOMER_CREDIT' : 'PROJECT_ACCOUNT' })">查看资金记录</el-button></article></div>
      <el-pagination v-if="total > 20" v-model:current-page="page" :page-size="20" :total="total" :disabled="loading" layout="prev,pager,next" @current-change="read" />
    </template>
  </section>
</template>
<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { projectOwnerAccounts, projectOwnerClients } from '@/api/projectRecords'
import { validRecordFilters, validRecordPage, validSubject, recordStatus, recordTime } from '@/utils/projectRecords'
import '@/styles/project-records.css'
const props = defineProps({ ownerId: { type: Number, required: true }, client: Boolean })
defineEmits(['ledger'])
const rows = ref([]), total = ref(0), page = ref(1), loading = ref(false), loaded = ref(false), error = ref(''), draft = ref({ projectId: '', status: 'ALL' }), filters = ref({})
let generation = 0, controller
function apply() {
  if (!validRecordFilters(draft.value)) return
  filters.value = { projectId: draft.value.projectId || undefined, status: draft.value.status === 'ALL' ? undefined : draft.value.status }; page.value = 1; read()
}
async function read() {
  const current = ++generation, owner = props.ownerId, expected = page.value, client = props.client
  controller?.abort(); controller = new AbortController(); const signal = controller.signal
  rows.value = []; total.value = 0; loaded.value = false; error.value = ''; loading.value = true
  try {
    const data = await (client ? projectOwnerClients : projectOwnerAccounts)(owner, { ...filters.value, page: expected, pageSize: 20 }, signal)
    if (current !== generation || signal.aborted) return
    if (!validRecordPage(data, (r) => validSubject(r, client)) || data.current !== expected) throw new Error('invalid subjects')
    rows.value = data.records; total.value = data.total; loaded.value = true
  } catch { if (current === generation && !signal.aborted) error.value = '账户记录读取失败，请重试；不会将失败显示为无记录。' }
  finally { if (current === generation) loading.value = false }
}
watch(() => [props.ownerId, props.client], () => { page.value = 1; draft.value = { projectId: '', status: 'ALL' }; filters.value = {}; read() }, { immediate: true })
onBeforeUnmount(() => { generation++; controller?.abort() })
</script>
