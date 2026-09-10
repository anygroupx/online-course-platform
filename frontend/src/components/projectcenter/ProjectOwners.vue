<template>
  <section class="record-report" aria-label="经营者项目明细">
    <header class="record-heading"><div><span class="record-eyebrow">OPERATORS / 经营者明细</span><h2>从账户到记录，逐项核对。</h2><p class="record-muted">需同时具备接口管理与资金核对权限。已停用账号和历史记录保留可查，不显示访问密钥或工单内容。</p></div><el-button :loading="loading" @click="read">刷新经营者</el-button></header>
    <form class="record-owner-search" @submit.prevent="search"><el-input v-model="keyword" maxlength="100" aria-label="经营者编号或用户名" placeholder="经营者编号或用户名，符号按原文查找" clearable /><el-button type="primary" native-type="submit" :loading="loading">查询经营者</el-button></form>
    <el-alert v-if="error" :title="error" type="warning" show-icon :closable="false" role="alert" />
    <el-skeleton v-if="loading" :rows="5" class="record-skeleton" />
    <template v-else-if="loaded"><el-empty v-if="!rows.length" description="没有符合条件的经营者记录" :image-size="80" /><div class="record-grid">
      <article v-for="row in rows" :key="row.id" class="record-card"><header><div><h3>{{ row.username || '账号已移除' }}</h3><small>经营者 {{ row.id }}</small></div><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ recordStatus(row.status) }}</el-tag></header>
        <dl><div><dt>可用项目账户 / 客户</dt><dd>{{ row.activeAccounts }} / {{ row.activeClients }}</dd></div><div><dt>项目账户已扣 / 已转回</dt><dd>¥{{ row.accountDebited }} / ¥{{ row.accountReturned }}</dd></div><div><dt>客户额度已扣 / 已转回</dt><dd>¥{{ row.clientDebited }} / ¥{{ row.clientReturned }}</dd></div></dl><p class="record-time">最近记录 {{ recordTime(row.lastActivity) }}</p><el-button @click="openOwner(row.id)">查看经营者明细</el-button></article></div>
      <el-pagination v-if="total > 20" v-model:current-page="page" :page-size="20" :total="total" :disabled="loading" layout="prev,pager,next" @current-change="read" />
    </template>
    <el-drawer v-model="visible" title="经营者明细" size="min(1000px,100vw)" destroy-on-close @closed="clearDetail">
      <section class="record-report"><div class="record-actions"><span class="record-id">经营者 {{ selectedId }}</span><el-button :loading="detailLoading" @click="loadDetail">刷新明细</el-button></div>
        <el-alert v-if="detailError" :title="detailError" type="warning" :closable="false" show-icon />
        <el-skeleton v-if="detailLoading" :rows="6" class="record-skeleton" />
        <template v-else-if="detail"><header class="record-identity record-detail-title"><h2>{{ detail.owner.username || '账号已移除' }}</h2><el-tag :type="detail.owner.status === 'ACTIVE' ? 'success' : 'info'">{{ recordStatus(detail.owner.status) }}</el-tag></header>
          <el-tabs v-model="tab" class="record-detail-tabs"><el-tab-pane label="经营概况" name="overview" /><el-tab-pane label="项目账户" name="accounts" /><el-tab-pane label="客户账户" name="clients" /><el-tab-pane label="资金流水" name="ledger" /></el-tabs>
          <template v-if="tab === 'overview'"><p class="record-time">滚动24小时：{{ recordTime(detail.usage.window.from) }} 至 {{ recordTime(detail.usage.window.through) }} · 北京时间</p><div class="record-grid"><article class="record-card"><h3>用量与售后</h3><dl><div><dt>近24小时调用</dt><dd>{{ detail.usage.calls.last24Hours }}</dd></div><div><dt>窗口未完成</dt><dd>{{ detail.usage.calls.failedLast24Hours }}</dd></div><div><dt>活跃客户</dt><dd>{{ detail.usage.clients.active }}</dd></div><div><dt>待处理工单</dt><dd>{{ detail.usage.tickets.open + detail.usage.tickets.inProgress }}</dd></div><div><dt>补偿待审核</dt><dd>{{ detail.usage.tickets.pendingCompensation }}</dd></div></dl><small>调用记录可能缺失，不作为计费凭证；补偿审核不自动付款。</small></article>
            <article class="record-card"><h3>项目账户兑换</h3><dl><div><dt>已结算扣款</dt><dd>¥{{ detail.accountFunding.debited }}</dd></div><div><dt>已转回余额</dt><dd>¥{{ detail.accountFunding.returned }}</dd></div><div><dt>待核实操作</dt><dd>{{ detail.accountFunding.unresolvedOperations }}</dd></div></dl><p class="record-muted">待核实操作不纳入已结算金额，余额变化不能代替逐笔核对。</p><el-button @click="showLedger({ book: 'PROJECT_ACCOUNT' })">查看项目兑换记录</el-button></article></div>
            <article class="record-card record-section"><h3>客户额度</h3><p class="record-funding-line">已结算扣款 ¥{{ detail.usage.localFunding.debited }} · 已转回 ¥{{ detail.usage.localFunding.returned }}</p><p class="record-muted">额度充值不代表已购买或执行服务；不同项目的额度不相加。</p><el-button @click="showLedger({ book: 'CUSTOMER_CREDIT' })">查看客户额度记录</el-button></article>
          </template>
          <ProjectSubjects v-else-if="tab === 'accounts'" :owner-id="selectedId" @ledger="showLedger" />
          <ProjectSubjects v-else-if="tab === 'clients'" :owner-id="selectedId" client @ledger="showLedger" />
          <ProjectLedger v-else-if="tab === 'ledger'" admin :owner-id="selectedId" :project-id="scope.projectId" :client-id="scope.clientId" :book="scope.book" />
        </template>
      </section>
    </el-drawer>
  </section>
</template>
<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { projectOwners, projectOwnerDetail } from '@/api/projectRecords'
import { validOwner, validOwnerDetail, validRecordPage, recordStatus, recordTime } from '@/utils/projectRecords'
import ProjectLedger from './ProjectLedger.vue'
import ProjectSubjects from './ProjectSubjects.vue'
import '@/styles/project-records.css'
const keyword = ref(''), applied = ref(''), page = ref(1), rows = ref([]), total = ref(0), loading = ref(false), loaded = ref(false), error = ref('')
const visible = ref(false), selectedId = ref(null), detail = ref(null), detailLoading = ref(false), detailError = ref(''), tab = ref('overview'), scope = ref({})
let generation = 0, detailGeneration = 0, controller, detailController
function search() { applied.value = keyword.value.trim(); page.value = 1; read() }
async function read() {
  const current = ++generation, expected = page.value; controller?.abort(); controller = new AbortController(); const signal = controller.signal
  rows.value = []; total.value = 0; loading.value = true; loaded.value = false; error.value = ''
  try {
    const data = await projectOwners({ keyword: applied.value || undefined, page: expected, pageSize: 20 }, signal)
    if (current !== generation || signal.aborted) return
    if (!validRecordPage(data, validOwner) || data.current !== expected) throw new Error('invalid owners')
    rows.value = data.records; total.value = data.total; loaded.value = true
  } catch (e) { if (current === generation && !signal.aborted) error.value = e.response?.status === 403 ? '需要接口管理与资金核对双权限，未读取经营者资料。' : '经营者记录读取失败，请重试；不会将失败显示为没有经营者。' }
  finally { if (current === generation) loading.value = false }
}
function openOwner(id) { selectedId.value = id; tab.value = 'overview'; scope.value = {}; visible.value = true; loadDetail() }
async function loadDetail() {
  if (!visible.value || selectedId.value === null) return
  const current = ++detailGeneration, owner = selectedId.value
  detailController?.abort(); detailController = new AbortController(); const signal = detailController.signal
  detail.value = null; detailError.value = ''; detailLoading.value = true
  try {
    const data = await projectOwnerDetail(owner, signal)
    if (current !== detailGeneration || signal.aborted || !visible.value || owner !== selectedId.value) return
    if (!validOwnerDetail(data) || data.owner.id !== owner) throw new Error('invalid detail')
    detail.value = data
  } catch { if (current === detailGeneration && !signal.aborted) detailError.value = '经营者明细读取失败或记录已变化，请重试。' }
  finally { if (current === detailGeneration) detailLoading.value = false }
}
function showLedger(value) { scope.value = value; tab.value = 'ledger' }
function clearDetail() { detailGeneration++; detailController?.abort(); detail.value = null; selectedId.value = null; scope.value = {} }
onMounted(read)
onBeforeUnmount(() => { generation++; controller?.abort(); clearDetail() })
</script>
