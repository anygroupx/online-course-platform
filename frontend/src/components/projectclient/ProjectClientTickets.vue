<template>
  <el-drawer v-model="open" title="下游客户售后" size="min(880px,100vw)" class="client-tickets"
    :close-on-click-modal="false" :close-on-press-escape="!busy" :before-close="close">
    <header class="ticket-header">
      <span class="eyebrow">LOCAL AFTER-SALES / 本平台处理</span>
      <h2>{{ context?.label || '我的下游工单' }}</h2>
      <p class="muted">{{ context?.projectTitle || '仅展示你名下的客户，客户密钥只能访问自己的工单。' }}</p>
    </header>
    <el-alert title="这是下游客户的本地售后，不会向上游发单。补偿申请通过仅记录结论，不会自动充值、退款或改变任何余额。"
      type="info" show-icon :closable="false" />
    <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon class="spaced" />
    <section v-if="pending" class="recovery" role="status">
      <h3>{{ retryReady ? '未查到本次提交回执' : '先核实这一次请求' }}</h3>
      <p>请求编号 <code>{{ pending.payload.requestId }}</code></p>
      <p class="muted">不会自动重发。离开本页前请保存编号；同页重开会保留待核对请求。</p>
      <div class="actions">
        <el-button :loading="busy" @click="recover">查询原请求结果</el-button>
        <el-button v-if="retryReady" :disabled="busy" @click="retry">使用原编号重试一次</el-button>
        <el-button v-if="retryReady" :disabled="busy" @click="abandon">结束本次尝试并刷新</el-button>
      </div>
    </section>
    <div class="toolbar">
      <el-button v-if="mode !== 'list'" :disabled="locked" @click="back">返回工单列表</el-button>
      <el-button v-if="mode === 'list' && context" type="primary" :disabled="locked || context.status !== 'ACTIVE'" @click="beginCreate">新建本地工单</el-button>
      <el-button :disabled="locked" :loading="loading" @click="mode === 'detail' ? loadDetail() : loadList()">刷新{{ mode === 'detail' ? '工单' : '列表' }}</el-button>
    </div>

    <section v-if="mode === 'list'" v-loading="loading">
      <div class="filters">
        <el-select v-model="status" aria-label="筛选工单状态" placeholder="全部状态" clearable :disabled="locked" @change="filterChanged">
          <el-option v-for="(label, key) in clientTicketStates" :key="key" :label="label" :value="key" />
        </el-select>
        <el-select v-model="kind" aria-label="筛选工单类型" placeholder="全部类型" clearable :disabled="locked" @change="filterChanged">
          <el-option v-for="(label, key) in clientTicketKinds" :key="key" :label="label" :value="key" />
        </el-select>
      </div>
      <article v-for="item in items" :key="item.id" class="ticket-row">
        <div class="ticket-copy"><span class="muted">{{ clientTicketKinds[item.kind] }} · {{ item.projectTitle }}</span><h3>{{ item.title }}</h3>
          <span class="muted">{{ item.updatedAt }} · 版本 {{ item.version }}</span><p v-if="!context" class="identity">客户 {{ item.clientId }}</p></div>
        <div class="row-end"><el-tag :type="clientTicketOpen(item) ? 'warning' : 'info'">{{ clientTicketStates[item.status] }}</el-tag>
          <el-button :disabled="locked" @click="inspect(item.id)">查看工单</el-button></div>
      </article>
      <el-empty v-if="!loading && !items.length && !error" description="暂无本地售后工单" />
      <el-pagination v-if="total > 20" v-model:current-page="page" :total="total" :page-size="20" layout="prev,pager,next" :disabled="locked" @current-change="loadList" />
      <section class="lookup">
        <h3>按原请求编号找回结果</h3><p class="muted">只查询，不重新提交。请勿在这里输入 API 密钥或账号密码。</p>
        <el-input v-model="lookupId" maxlength="36" placeholder="提交时的 UUID 请求编号" aria-label="原请求编号" :disabled="locked" />
        <el-button :disabled="locked || !ticketRequestIdValid(lookupId)" @click="lookup">查询提交回执</el-button>
      </section>
    </section>

    <section v-else-if="mode === 'create'" class="compose">
      <h3>为此客户提交问题</h3>
      <p class="muted">只接受纯文本；请勿填写密码、密钥或人脸资料。附件暂未开放。</p>
      <el-form label-position="top" :disabled="locked">
        <el-form-item label="工单类型"><el-select v-model="draft.kind" aria-label="工单类型">
          <el-option v-for="(label, key) in clientTicketKinds" :key="key" :value="key" :label="label" /></el-select></el-form-item>
        <el-form-item label="工单标题"><el-input v-model="draft.title" maxlength="120" show-word-limit /></el-form-item>
        <el-form-item label="问题描述"><el-input v-model="draft.description" type="textarea" :rows="5" maxlength="5000" show-word-limit /></el-form-item>
        <el-form-item v-if="draft.kind === 'COMPENSATION'" label="申请参考金额（元，不是支付指令）">
          <el-input v-model="draft.amount" inputmode="decimal" maxlength="11" placeholder="0.00" /></el-form-item>
        <el-checkbox v-model="consent" class="consent">我已确认客户和内容，知晓仅在本平台处理且不会自动入账</el-checkbox>
        <div class="actions"><el-button type="primary" :disabled="!canCreate" @click="submit">提交本地工单</el-button></div>
      </el-form>
    </section>

    <section v-else v-loading="loading" class="detail">
      <template v-if="ticket">
        <div class="detail-heading"><el-tag :type="clientTicketOpen(ticket) ? 'warning' : 'info'">{{ clientTicketStates[ticket.status] }}</el-tag>
          <span class="muted">{{ clientTicketKinds[ticket.kind] }} · 版本 {{ ticket.version }}</span></div>
        <h2>{{ ticket.title }}</h2><p class="identity">客户 {{ ticket.clientId }}</p>
        <p class="plaintext">{{ ticket.description }}</p>
        <section v-if="ticket.kind === 'COMPENSATION'" class="review-card">
          <div class="detail-heading"><strong>{{ clientTicketReviews[ticket.reviewResult] }}</strong><b>申请参考 ¥{{ ticket.requestedAmount }}</b></div>
          <p v-if="ticket.reviewNote" class="plaintext">{{ ticket.reviewNote }}</p>
          <p class="muted">{{ ticket.reviewedAt || '尚未审核' }} · 审核结论不代表款项已支付</p>
        </section>
        <section class="conversation"><h3>沟通记录 <span class="muted">{{ replyTotal }} 条</span></h3>
          <article v-for="message in messages" :key="message.id" class="message" :class="message.author === 'OWNER' ? 'owner' : ''">
            <header><b>{{ message.author === 'OWNER' ? '经营者回复' : '客户回复' }}</b><time>{{ message.createdAt }}</time></header>
            <p class="plaintext">{{ message.content }}</p>
          </article>
          <p v-if="!messages.length" class="muted">尚无回复</p>
          <el-pagination v-if="replyTotal > 20" v-model:current-page="replyPage" :total="replyTotal" :page-size="20" layout="prev,pager,next" :disabled="locked" @current-change="loadDetail" />
        </section>
        <template v-if="clientTicketOpen(ticket)">
          <el-form label-position="top" :disabled="locked || loading" class="reply-form">
            <el-form-item label="补充回复"><el-input v-model="replyText" type="textarea" :rows="3" maxlength="5000" show-word-limit /></el-form-item>
            <el-checkbox v-model="replyConsent" class="consent">确认以经营者身份向此客户回复</el-checkbox>
            <div class="actions"><el-button :disabled="!replyText.trim() || !replyConsent" @click="sendReply">发送本地回复</el-button></div>
          </el-form>
          <el-form label-position="top" :disabled="locked || loading" class="decision-form">
            <h3>{{ ticket.kind === 'COMPENSATION' ? '审核此补偿申请' : '记录处理结果' }}</h3>
            <el-form-item label="处理结论"><el-select v-model="decision" aria-label="处理结论">
              <template v-if="ticket.kind === 'COMPENSATION'"><el-option label="通过申请（仅记录，不入账）" value="APPROVE" /><el-option label="拒绝申请" value="REJECT" /></template>
              <template v-else><el-option label="已解决" value="RESOLVE" /><el-option label="关闭工单" value="CLOSE" /></template>
            </el-select></el-form-item>
            <el-form-item label="结论说明（客户可见）"><el-input v-model="decisionNote" type="textarea" :rows="3" maxlength="2000" show-word-limit /></el-form-item>
            <el-checkbox v-model="decisionConsent" class="consent">我已阅读当前版本，确认结束此工单；本操作不执行补偿付款</el-checkbox>
            <div class="actions"><el-button type="primary" :disabled="!decision || !decisionNote.trim() || !decisionConsent" @click="sendDecision">确认处理结论</el-button></div>
          </el-form>
        </template>
        <el-alert v-else title="工单已结束，回复和处理结论已锁定；补偿通过不代表款项入账。" type="success" :closable="false" class="spaced" />
      </template>
      <el-empty v-else-if="!loading" description="详情尚未读取，请刷新；不会重复提交工单" />
    </section>
    <template #footer><el-button :disabled="busy" @click="open = false">{{ pending ? '关闭，保留待核对请求' : '关闭售后窗口' }}</el-button></template>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref, watch, onBeforeUnmount } from 'vue'
import { listClientTickets, clientTicket, clientTicketReplies, clientTicketRequest, createClientTicket, replyClientTicket, decideClientTicket } from '@/api/projectClientTickets'
import { clientTicketKinds, clientTicketStates, clientTicketReviews, clientTicketOpen, ticketAmountValid, ticketRequestIdValid } from '@/utils/projectClientTickets'
const props = defineProps({ modelValue: Boolean, client: { type: Object, default: null } })
const emit = defineEmits(['update:modelValue'])
const open = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const context = ref(null), mode = ref('list'), items = ref([]), ticket = ref(null), selectedId = ref(null)
const messages = ref([]), replyTotal = ref(0), replyPage = ref(1), total = ref(0), page = ref(1)
const status = ref(''), kind = ref(''), busy = ref(false), loading = ref(false), error = ref('')
const pending = ref(null), retryReady = ref(false), lookupId = ref('')
const draft = reactive({ kind: 'BUG', title: '', description: '', amount: '0.00' })
const consent = ref(false), replyText = ref(''), replyConsent = ref(false)
const decision = ref(''), decisionNote = ref(''), decisionConsent = ref(false)
let alive = true, generation = 0, sequence = 0
const current = g => alive && props.modelValue && generation === g
const locked = computed(() => busy.value || !!pending.value)
const canCreate = computed(() => consent.value && draft.title.trim() && draft.description.trim() && (draft.kind !== 'COMPENSATION' || ticketAmountValid(draft.amount)))
function close(done) { if (!busy.value) done() }
function clearReply() { replyText.value = ''; replyConsent.value = false; decision.value = ''; decisionNote.value = ''; decisionConsent.value = false }
async function loadList() {
  const g = generation, seq = ++sequence
  loading.value = true; error.value = ''
  try {
    const result = await listClientTickets({ clientId: context.value?.id, status: status.value || undefined, kind: kind.value || undefined, page: page.value, pageSize: 20 })
    if (current(g) && seq === sequence) { items.value = result.records || []; total.value = result.total || 0 }
  } catch { if (current(g) && seq === sequence) error.value = '工单列表读取失败，不代表没有工单。请重试查询。' }
  finally { if (current(g) && seq === sequence) loading.value = false }
}
async function loadDetail() {
  if (!selectedId.value) return
  const g = generation, seq = ++sequence, id = selectedId.value
  loading.value = true; error.value = ''
  // Reading a new snapshot invalidates earlier acknowledgement, even when the draft is retained.
  replyConsent.value = false; decisionConsent.value = false
  // Do not allow a stale visible version to become a new write after a failed refresh.
  ticket.value = null
  try {
    const [detail, replies] = await Promise.all([clientTicket(id), clientTicketReplies(id, { page: replyPage.value, pageSize: 20 })])
    if (current(g) && seq === sequence) { ticket.value = detail; messages.value = replies.records || []; replyTotal.value = replies.total || 0 }
  } catch { if (current(g) && seq === sequence) error.value = '工单详情读取失败，请重新查询，不需要重新提交。' }
  finally { if (current(g) && seq === sequence) loading.value = false }
}
function inspect(id) { selectedId.value = id; mode.value = 'detail'; replyPage.value = 1; clearReply(); loadDetail() }
function back() { mode.value = 'list'; clearReply(); loadList() }
function filterChanged() { page.value = 1; loadList() }
function beginCreate() { mode.value = 'create'; error.value = ''; consent.value = false; Object.assign(draft, { kind: 'BUG', title: '', description: '', amount: '0.00' }) }
function submit() {
  if (locked.value || !canCreate.value || !context.value) return
  execute({ type: 'CREATE', payload: { requestId: crypto.randomUUID(), clientId: context.value.id, kind: draft.kind,
    title: draft.title.trim(), description: draft.description.trim(), requestedAmount: draft.kind === 'COMPENSATION' ? draft.amount : '0', consent: true } })
}
function sendReply() {
  if (locked.value || loading.value || !clientTicketOpen(ticket.value) || !replyText.value.trim() || !replyConsent.value) return
  execute({ type: 'REPLY', id: ticket.value.id, payload: { requestId: crypto.randomUUID(), version: ticket.value.version, content: replyText.value.trim(), consent: true } })
}
function sendDecision() {
  if (locked.value || loading.value || !clientTicketOpen(ticket.value) || !decision.value || !decisionNote.value.trim() || !decisionConsent.value) return
  execute({ type: 'DECISION', id: ticket.value.id, payload: { requestId: crypto.randomUUID(), version: ticket.value.version, action: decision.value, note: decisionNote.value.trim(), consent: true } })
}
async function execute(attempt) {
  if (busy.value) return
  const g = generation
  pending.value = attempt; retryReady.value = false; busy.value = true; error.value = ''
  let receipt
  try {
    if (attempt.type === 'CREATE') receipt = await createClientTicket(attempt.payload)
    else if (attempt.type === 'REPLY') receipt = await replyClientTicket(attempt.id, attempt.payload)
    else receipt = await decideClientTicket(attempt.id, attempt.payload)
  } catch { if (current(g)) error.value = '本次提交结果尚未核实（可能已保存，也可能被拒绝）。请先查询原请求，不能直接重复提交。' }
  finally { if (current(g)) busy.value = false }
  if (current(g) && receipt) await applied(receipt)
}
async function applied(receipt) {
  pending.value = null; retryReady.value = false; error.value = ''; clearReply(); consent.value = false
  selectedId.value = receipt.ticketId; mode.value = 'detail'; replyPage.value = 1
  await loadDetail()
}
async function recover() {
  if (!pending.value || busy.value) return
  const g = generation, requestId = pending.value.payload.requestId
  busy.value = true; error.value = ''; retryReady.value = false
  let receipt
  try {
    receipt = await clientTicketRequest(requestId)
    if (current(g) && !receipt) { retryReady.value = true; error.value = '尚无已提交回执。可再次查询、手动按原编号重试，或结束本次尝试后重新阅读最新工单。' }
  } catch { if (current(g)) error.value = '原请求查询失败，不能据此判断提交失败。请稍后继续查询，不自动重发。' }
  finally { if (current(g)) busy.value = false }
  if (current(g) && receipt) await applied(receipt)
}
function retry() { if (pending.value && retryReady.value && !busy.value) execute(pending.value) }
function abandon() {
  if (!retryReady.value || busy.value) return
  const was = pending.value
  pending.value = null; retryReady.value = false; error.value = ''; clearReply(); consent.value = false
  if (was?.id) { selectedId.value = was.id; mode.value = 'detail'; loadDetail() }
}
async function lookup() {
  if (locked.value || !ticketRequestIdValid(lookupId.value)) return
  const g = generation; busy.value = true; error.value = ''
  let receipt
  try { receipt = await clientTicketRequest(lookupId.value); if (current(g) && !receipt) error.value = '没有找到该身份的提交回执；这次仅查询，没有重新提交。' }
  catch { if (current(g)) error.value = '查询失败，请稍后重试。' }
  finally { if (current(g)) busy.value = false }
  if (current(g) && receipt) await applied(receipt)
}
watch(() => props.modelValue, value => {
  generation++; sequence++; loading.value = false; busy.value = false
  if (!value) return
  if (pending.value) { error.value = '恢复上次待核对请求；未切换客户、未自动重发。'; return }
  context.value = props.client ? { ...props.client } : null
  mode.value = 'list'; items.value = []; ticket.value = null; page.value = 1; status.value = ''; kind.value = ''; lookupId.value = ''
  clearReply(); loadList()
})
onBeforeUnmount(() => { alive = false; generation++; pending.value = null; clearReply() })
</script>

<style scoped>
.eyebrow{font-size:11px;letter-spacing:1.3px;color:var(--el-color-primary);font-weight:650}
.ticket-header h2{font-size:24px;line-height:1.5;margin:10px 0 4px}.muted{color:var(--el-text-color-secondary);font-size:12px;line-height:1.8}
h3{font-size:15px;line-height:1.6;margin:0 0 8px}.spaced{margin-top:16px}.toolbar,.actions,.detail-heading,.filters{display:flex;gap:12px;align-items:center;flex-wrap:wrap}
.toolbar{margin:22px 0}.filters .el-select{width:180px}.ticket-row{padding:20px 0;border-bottom:1px solid var(--el-border-color-lighter);display:flex;align-items:center;gap:16px;justify-content:space-between}
.ticket-copy{min-width:0}.ticket-copy h3{margin:5px 0;overflow-wrap:anywhere}.row-end{display:flex;gap:14px;align-items:center;flex:none}
.detail h2{font-size:22px;line-height:1.6;overflow-wrap:anywhere}.identity,code{font-size:11px;color:var(--el-text-color-secondary);overflow-wrap:anywhere}
.plaintext{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.9;font-size:14px}.review-card,.recovery{border:1px solid var(--el-border-color);border-radius:10px;padding:18px;margin:20px 0;background:var(--el-fill-color-light)}
.recovery{border-color:var(--el-color-warning-light-5);background:var(--el-color-warning-light-9)}.recovery p{font-size:12px;overflow-wrap:anywhere}
.review-card .detail-heading{justify-content:space-between}.review-card b{font-variant-numeric:tabular-nums}.conversation,.reply-form,.decision-form,.lookup{border-top:1px solid var(--el-border-color-lighter);padding-top:24px;margin-top:28px}
.message{padding:16px;margin-top:12px;border-radius:8px;background:var(--el-fill-color-light)}.message.owner{border-left:3px solid var(--el-color-primary-light-5)}
.message header{display:flex;gap:12px;justify-content:space-between;font-size:12px}.message time{color:var(--el-text-color-secondary);font-size:11px}.message p{margin:10px 0 0}
.actions{margin-top:16px}.compose{margin-top:26px}.lookup>.el-button{margin-top:12px}.el-button{min-height:44px;margin-left:0}.el-pagination{margin-top:22px}.el-form-item{margin-bottom:20px}
.consent{height:auto;min-height:44px;align-items:flex-start;white-space:normal}.consent :deep(.el-checkbox__label){white-space:normal;line-height:1.8}.consent :deep(.el-checkbox__input){margin-top:5px}
:deep(.el-select__wrapper),:deep(.el-input__wrapper){min-height:44px}:deep(.el-alert__title){line-height:1.8}
@media(max-width:600px){.ticket-header h2{font-size:21px}.ticket-row{align-items:flex-start;flex-direction:column;gap:10px}.row-end{width:100%;justify-content:space-between}.filters{display:grid;grid-template-columns:1fr 1fr}.filters .el-select{width:100%}.toolbar .el-button,.actions .el-button{flex:1}.message header{flex-direction:column;gap:4px}.review-card,.recovery{padding:14px}.detail h2{font-size:20px}}
</style>
