<template>
  <section class="project-usage" aria-label="项目用量与运营统计">
    <header class="report-heading">
      <div>
        <span class="eyebrow">{{ admin ? 'PROJECT OPERATIONS / 全局只读' : 'PROJECT USAGE / 本人用量' }}</span>
        <h2>{{ admin ? '分别核对，不混合计算。' : '用量记录与项目额度' }}</h2>
        <p>{{ admin ? '须同时具备接口管理与资金核对权限；不从角色名称推断权限。' : '仅本人及主密钥可读取经营者统计，客户密钥不能读取本页汇总。' }}</p>
      </div>
      <el-button :loading="loading" :disabled="pageLoading" @click="load">刷新只读统计</el-button>
    </header>
    <el-alert title="API 日志可能缺失，不用于计费；OK 仅表示请求处理完成，不代表服务已执行。本页不刷新服务状态，也不移动资金。" type="info" :closable="false" show-icon />
    <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon role="alert" />
    <el-skeleton v-if="loading" :rows="4" class="loading-report" />
    <template v-else-if="report">
      <p class="report-window">滚动 24 小时 · {{ reportTime(report.window.from) }} 至 {{ reportTime(report.window.through) }} · Asia/Shanghai（不是自然日“今日”）</p>
      <div class="metrics">
        <article><span>近 24 小时调用</span><strong>{{ report.calls.last24Hours }}</strong><small>累计已记录 {{ report.calls.total }}</small></article>
        <article><span>窗口内未完成</span><strong>{{ report.calls.failedLast24Hours }}</strong><small>累计未完成 {{ report.calls.failed }}</small></article>
        <article><span>活跃客户</span><strong>{{ report.clients.active }}</strong><small>暂停 {{ report.clients.suspended }} · 已关闭 {{ report.clients.closed }}</small></article>
        <article><span>待处理工单</span><strong>{{ report.tickets.open + report.tickets.inProgress }}</strong><small>其中 {{ report.tickets.pendingCompensation }} 笔补偿待审核；审核不付款</small></article>
      </div>
      <section v-if="!admin" class="report-section">
        <h3>API 动作分布</h3>
        <p class="muted">累计失败 {{ report.calls.failed }} 次；鉴权前被拒绝的请求及漏记日志不在此统计。</p>
        <el-empty v-if="!report.actions.length" description="暂无已记录的 API 调用" :image-size="70" />
        <div v-else class="action-list" role="table" aria-label="API 动作分布">
          <div class="action-row labels" role="row"><span role="columnheader">安全动作</span><span role="columnheader">累计调用</span><span role="columnheader">近24小时</span><span role="columnheader">窗口未完成</span></div>
          <div v-for="row in report.actions" :key="row.action" class="action-row" role="row">
            <span role="cell">{{ projectCallLabel(row.action) }}<small>{{ row.action }}</small></span>
            <b role="cell" data-label="累计">{{ row.total }}</b><b role="cell" data-label="近24时">{{ row.last24Hours }}</b><b role="cell" data-label="窗口未完成">{{ row.failedLast24Hours }}</b>
          </div>
        </div>
        <p v-if="report.moreActions" class="report-warning">仅展示累计调用最多的 50 类动作；上方总数仍包含全部已记录动作。</p>
      </section>
      <section class="report-section">
        <h3>平台人民币流向</h3>
        <p class="muted">仅已结算平台钱包金额（CNY）；不是项目单位、销售额或利润。{{ admin ? '项目账户兑换与客户额度不能相加作为收入。' : '客户充值只增加可用额度，不代表已购买服务。' }}</p>
        <div class="funding-grid" :class="{ dual: admin }">
          <article v-for="entry in fundingEntries" :key="entry.label" class="funding-card">
            <h4>{{ entry.label }}</h4>
            <dl><div><dt>已结算平台扣款</dt><dd>¥{{ entry.value.debited }}</dd></div><div><dt>已转回平台</dt><dd>¥{{ entry.value.returned }}</dd></div><div><dt>净扣款</dt><dd>¥{{ entry.value.netDebited }}</dd></div></dl>
            <small>{{ entry.value.settledOperations }} 笔已结算动作（含零额度开户）</small>
          </article>
        </div>
        <el-alert v-if="admin && report.upstreamFunding.unresolvedOperations" :title="`${report.upstreamFunding.unresolvedOperations} 笔操作仍在处理中或结果未知，未计入已结算金额；请使用原操作核对流程，不按余额差推测。`" type="warning" :closable="false" show-icon />
      </section>
      <section v-if="admin" class="report-section">
        <h3>项目与绑定</h3>
        <div class="binding-facts"><p>已发布项目配置 <b>{{ report.publishedProjects }}</b></p><p>可用项目账户 <b>{{ report.activeUpstreamBindings }}</b></p><p>拥有可用项目账户的用户 <b>{{ report.activeUpstreamOwners }}</b></p><p>拥有活跃客户的经营者 <b>{{ report.activeLocalOwners }}</b></p></div>
        <p class="muted">配置与账户状态计数不等于实时可用性或验收；不汇总不同项目的缓存余额。</p>
      </section>
      <section v-else class="report-section" v-loading="pageLoading">
        <h3>按项目查看客户额度</h3>
        <p class="muted">不同项目单位不可相加，也不代表账户余额。各页按读取时的当前状态展示。</p>
        <el-alert v-if="pageError" :title="pageError" type="warning" :closable="false" show-icon />
        <el-empty v-else-if="!pageLoading && !balances.length" description="暂无客户项目" :image-size="70" />
        <div class="project-grid">
          <article v-for="item in balances" :key="item.projectId" class="balance-card">
            <h4>{{ item.title }}</h4><small>项目 {{ item.projectId }} · {{ item.customers }} 位客户，{{ item.active }} 位活跃</small>
            <dl><div><dt>活跃客户额度</dt><dd>{{ item.activeUnits }}</dd></div><div><dt>暂停客户额度</dt><dd>{{ item.suspendedUnits }}</dd></div><div><dt>剩余实付可退预算</dt><dd>¥{{ item.refundBudget }}</dd></div></dl>
          </article>
        </div>
        <el-pagination v-if="total > 20" v-model:current-page="page" :page-size="20" :total="total" :disabled="pageLoading || loading" layout="prev,pager,next" @current-change="readPage" />
        <el-button v-if="pageError" :loading="pageLoading" @click="readPage">重试本页</el-button>
      </section>
    </template>
  </section>
</template>

<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { ownerProjectUsage, projectUsageBalances, systemProjectOverview } from '@/api/projectReports'
import { projectCallLabel, reportTime, validProjectReport, validProjectBalancePage } from '@/utils/projectReports'

const props = defineProps({ admin: Boolean })
const report = ref(null), error = ref(''), loading = ref(false)
const balances = ref([]), total = ref(0), page = ref(1), pageLoading = ref(false), pageError = ref('')
let generation = 0, controller, pageController
const fundingEntries = computed(() => !report.value ? [] : [
  ...(props.admin ? [{ label: '项目账户兑换', value: report.value.upstreamFunding }] : []),
  { label: '客户额度', value: report.value.localFunding },
])

async function load() {
  const current = ++generation
  controller?.abort(); pageController?.abort()
  controller = new AbortController()
  const signal = controller.signal, admin = props.admin
  report.value = null; error.value = ''; balances.value = []; pageError.value = ''; page.value = 1; total.value = 0
  loading.value = true; pageLoading.value = false
  try {
    const [summary, projects] = await Promise.all([
      admin ? systemProjectOverview(signal) : ownerProjectUsage(signal),
      admin ? Promise.resolve(null) : projectUsageBalances(1, signal),
    ])
    if (current !== generation || signal.aborted) return
    if (!validProjectReport(summary, admin) || (!admin && (!validProjectBalancePage(projects) || projects.current !== 1))) throw new Error('invalid report')
    report.value = summary
    if (!admin) { balances.value = projects.records; total.value = projects.total }
  } catch (err) {
    if (current === generation && !signal.aborted) error.value = admin && err.response?.status === 403
      ? '需要接口管理与资金核对双权限；未读取任何全局统计。'
      : '统计读取失败或数据不完整；不会将失败显示为零。请重试只读请求。'
  } finally { if (current === generation) loading.value = false }
}
async function readPage() {
  const current = generation, expectedPage = page.value
  pageController?.abort(); pageController = new AbortController()
  const signal = pageController.signal
  pageLoading.value = true; pageError.value = ''; balances.value = []
  try {
    const result = await projectUsageBalances(expectedPage, signal)
    if (current !== generation || signal.aborted || page.value !== expectedPage) return
    if (!validProjectBalancePage(result) || result.current !== expectedPage) throw new Error('invalid page')
    balances.value = result.records; total.value = result.total
  } catch {
    if (current === generation && !signal.aborted) pageError.value = '项目额度读取失败；请重试本页，未显示伪零余额。'
  } finally { if (current === generation && !signal.aborted) pageLoading.value = false }
}
watch(() => props.admin, load, { immediate: true })
onBeforeUnmount(() => { generation++; controller?.abort(); pageController?.abort() })
</script>

<style scoped>
.project-usage { color: var(--text-primary); min-width: 0; }
.report-heading { display: flex; justify-content: space-between; gap: 16px; flex-wrap: wrap; align-items: flex-start; margin-bottom: 18px; }
h2 { font-size: 24px; margin: 6px 0; line-height: 1.5; }
h3 { margin: 0 0 12px; font-size: 17px; }
h4 { margin: 0 0 8px; font-size: 15px; overflow-wrap: anywhere; }
.report-heading p, .muted, small, .report-window { color: var(--text-secondary); font-size: 12px; line-height: 1.8; }
.eyebrow { color: var(--brand-primary); font-size: 11px; letter-spacing: 1.2px; font-weight: 700; }
.report-window { overflow-wrap: anywhere; margin: 18px 0; font-variant-numeric: tabular-nums; }
.project-usage :deep(.el-alert) { margin: 12px 0; }
.project-usage :deep(.el-button) { min-height: 44px; }
.loading-report { margin: 24px 0; }
.metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin: 20px 0; }
.metrics article { border: 1px solid var(--border-color-light); border-radius: 10px; padding: 18px; background: var(--surface-solid); }
.metrics span, .metrics small, .metrics strong { display: block; }
.metrics span { font-size: 13px; }.metrics strong { font-size: 27px; margin: 12px 0; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.report-section { border: 1px solid var(--border-color-light); border-radius: 10px; padding: 22px; margin: 18px 0; background: var(--surface-solid); }
.action-row { display: grid; grid-template-columns: minmax(0, 1fr) repeat(3, minmax(65px, .35fr)); gap: 12px; padding: 14px 0; border-bottom: 1px solid var(--border-color-light); font-size: 13px; }
.action-row:last-child { border: 0; }.action-row small { display: block; overflow-wrap: anywhere; }.action-row b { text-align: right; font-variant-numeric: tabular-nums; }.action-row.labels { font-size: 12px; color: var(--text-secondary); }.action-row.labels span:not(:first-child) { text-align: right; }
.report-warning { color: var(--color-warning); font-size: 12px; }
.project-grid, .funding-grid.dual { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.balance-card, .funding-card { border: 1px solid var(--border-color-light); border-radius: 8px; background: var(--surface-mica); padding: 18px; min-width: 0; }
dl { margin: 16px 0; }dl div { display: flex; justify-content: space-between; gap: 14px; padding: 8px 0; }dt { color: var(--text-secondary); font-size: 12px; }dd { margin: 0; font-weight: 650; font-size: 15px; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; text-align: right; }
.binding-facts { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }.binding-facts p { display: flex; justify-content: space-between; font-size: 13px; gap: 12px; }
.project-usage :deep(.el-pagination) { margin-top: 18px; justify-content: flex-end; }
@media(max-width: 720px) {
  h2 { font-size: 21px; }.metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }.metrics article { padding: 14px; }.metrics strong { font-size: 24px; }
  .project-grid, .funding-grid.dual, .binding-facts { grid-template-columns: 1fr; }.report-section { padding: 16px; }.balance-card, .funding-card { padding: 14px; }
  .action-row { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }.action-row.labels { display: none; }.action-row>span { grid-column: 1 / -1; }.action-row b { text-align: left; }.action-row b::before { content: attr(data-label); display: block; font-size: 11px; color: var(--text-secondary); font-weight: 400; margin-bottom: 4px; }
}
</style>
