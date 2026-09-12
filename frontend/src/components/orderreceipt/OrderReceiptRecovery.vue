<template>
  <section class="receipt-recovery" aria-label="订单执行编号恢复">
    <header class="receipt-heading">
      <div><span class="receipt-eyebrow">RECOVERY / 订单核对</span><h2>找回编号，不重复下单。</h2></div>
      <el-button :disabled="busy" @click="loadRecent">查看核对记录</el-button>
    </header>
    <el-alert type="info" :closable="false" show-icon title="仅适用于已有执行回执、但编号未记录的订单。核对不会补单、取消、退款或修改余额。" />
    <p class="receipt-note">需要订单管理与接口管理双权限。核对账号、凭据、课程和商品的完整身份，不按课程名称猜测归属。</p>

    <section v-if="!requestId" class="receipt-discovery" aria-label="查找执行编号" :aria-busy="candidateLoading">
      <header>
        <div><h3>不知道执行编号？</h3><p>按这笔订单的完整身份查找，也可以直接填写已有回执编号。</p></div>
        <el-button :loading="candidateLoading" :disabled="busy || candidateLoading" @click="findCandidates">查找执行编号</el-button>
      </header>
      <p class="receipt-scope">仅核对本次查询返回的记录，不代表全部订单；没有结果不表示订单不存在。</p>
      <el-alert v-if="candidateError" :title="candidateError" type="warning" :closable="false" show-icon role="alert" />
      <el-skeleton v-if="candidateLoading" :rows="2" animated />
      <div v-else-if="candidates" class="receipt-candidate-result" aria-live="polite">
        <p class="receipt-candidate-summary">{{ candidates.receiptIds.length ? `本次找到 ${candidates.receiptIds.length} 个可核对编号` : '本次未找到可核实编号' }}</p>
        <p class="receipt-scope">查询时间：{{ receiptTime(candidates.checkedAt) }} · 北京时间</p>
        <template v-if="candidates.receiptIds.length">
          <p class="receipt-note">请按原始回执选择编号。填入后仍需填写核对依据，并单独确认关联。</p>
          <ul class="receipt-candidates" aria-label="可核对的执行编号">
            <li v-for="id in candidates.receiptIds" :key="id">
              <code>{{ id }}</code>
              <el-button :disabled="busy || form.receiptId === id" :aria-label="`填入执行编号 ${id}`" @click="chooseCandidate(id)">{{ form.receiptId === id ? '已填入表单' : '填入此编号' }}</el-button>
            </li>
          </ul>
        </template>
        <p v-else class="receipt-note">若已持有原始回执，可在下方手动填写编号继续核对；不要因此重复下单。</p>
      </div>
    </section>

    <el-form v-if="!requestId" label-position="top" class="receipt-form" :disabled="busy || candidateLoading" @submit.prevent="preview">
      <el-form-item label="执行编号"><el-input v-model="form.receiptId" maxlength="50" placeholder="填写已核实的订单回执编号" autocomplete="off" /></el-form-item>
      <el-form-item label="核对依据"><el-input v-model="form.evidence" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="至少10个字，说明回执及订单归属依据；不要填写密码或访问密钥。" autocomplete="off" /></el-form-item>
      <el-checkbox v-model="form.ownershipConfirmed">我已核实原始回执及执行账户，确认与此订单属于同一笔业务</el-checkbox>
      <el-button type="primary" native-type="submit" :disabled="!validReceiptForm(form) || busy || candidateLoading">核对执行编号</el-button>
    </el-form>

    <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon role="alert" />
    <el-skeleton v-if="busy" :rows="3" animated class="receipt-skeleton" />
    <template v-if="requestId">
      <div class="receipt-request"><span>本次请求编号</span><code>{{ requestId }}</code><small>结果未确认时，请检查这个请求；关闭页面后也可从核对记录继续。</small></div>
      <article v-if="view && !busy" class="receipt-preview">
        <header><h3>{{ receiptStates[view.state][0] }}</h3><el-tag :type="view.state === 'APPLIED' ? 'success' : 'info'">{{ view.state === 'APPLIED' ? '编号已关联' : '未改变订单' }}</el-tag></header>
        <dl><div><dt>订单编号</dt><dd>{{ view.orderNo }}</dd></div><div><dt>课程</dt><dd>{{ view.courseName }}</dd></div><div><dt>执行编号</dt><dd>{{ view.receiptId }}</dd></div><div><dt>预览有效至</dt><dd>{{ receiptTime(view.expiresAt) }} · 北京时间</dd></div></dl>
        <p>{{ receiptStates[view.state][1] }}</p>
        <el-alert v-if="view.state === 'READY' && !receiptCanConfirm(view, clock)" title="预览已过期，请重新核对。" type="warning" :closable="false" />
        <template v-if="view.state === 'READY'">
          <el-checkbox v-model="consent" :disabled="!receiptCanConfirm(view, clock)">确认只关联上述编号，不重新提交订单或产生费用</el-checkbox>
          <el-button type="primary" :disabled="!consent || !receiptCanConfirm(view, clock) || busy || confirmSent" @click="confirm">确认恢复编号</el-button>
        </template>
      </article>
      <div class="receipt-actions"><el-button :loading="busy" @click="check">检查原请求</el-button><el-button v-if="!view || view.state !== 'READING'" :disabled="busy" @click="reset">开始新的核对</el-button></div>
    </template>

    <section v-if="showRecent" class="receipt-history" aria-label="最近核对记录">
      <header><h3>最近20次核对</h3><el-button text :disabled="busy" @click="showRecent = false">收起记录</el-button></header>
      <el-alert v-if="recentError" :title="recentError" type="warning" :closable="false" />
      <el-skeleton v-if="recentLoading" :rows="3" />
      <el-empty v-else-if="recentLoaded && !recent.length" description="还没有核对记录" :image-size="60" />
      <article v-for="item in recent" :key="item.id"><div><strong>{{ item.receiptId }}</strong><span>{{ receiptStates[item.state][0] }}</span><code>{{ item.id }}</code></div><el-button :disabled="busy" @click="select(item)">查看原请求</el-button></article>
    </section>
  </section>
</template>
<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { previewOrderReceipt, getOrderReceipt, confirmOrderReceipt, recentOrderReceipts, findOrderReceiptCandidates } from '@/api/orderReceipts'
import { receiptStates, validReceiptForm, validReceiptView, receiptCanConfirm, receiptTime, validReceiptCandidates } from '@/utils/orderReceipts'
const props = defineProps({ orderId: { type: Number, required: true } })
const emit = defineEmits(['applied'])
const form = ref({ receiptId: '', evidence: '', ownershipConfirmed: false })
const expectedReceipt = ref('')
const requestId = ref(''), view = ref(null), busy = ref(false), consent = ref(false), confirmSent = ref(false), error = ref(''), clock = ref(Date.now())
const recent = ref([]), showRecent = ref(false), recentLoading = ref(false), recentLoaded = ref(false), recentError = ref('')
const candidates = ref(null), candidateLoading = ref(false), candidateError = ref('')
let generation = 0, controller, recentController, recentGeneration = 0, candidateController, candidateGeneration = 0
const timer = setInterval(() => { clock.value = Date.now() }, 1000)
function reset() {
  clearCandidates()
  generation++; controller?.abort(); form.value = { receiptId: '', evidence: '', ownershipConfirmed: false }
  requestId.value = ''; expectedReceipt.value = ''; view.value = null; busy.value = false; consent.value = false; confirmSent.value = false; error.value = ''
}
function clearCandidates() {
  candidateGeneration++; candidateController?.abort()
  candidates.value = null; candidateLoading.value = false; candidateError.value = ''
}
async function findCandidates() {
  if (busy.value || candidateLoading.value || requestId.value) return
  clearCandidates(); candidateController = new AbortController()
  const signal = candidateController.signal, order = props.orderId, version = candidateGeneration
  candidateLoading.value = true; form.value.ownershipConfirmed = false
  try {
    const value = await findOrderReceiptCandidates(order, signal)
    if (version !== candidateGeneration || signal.aborted || order !== props.orderId || requestId.value) return
    if (!validReceiptCandidates(value, order)) throw new Error('incomplete candidate response')
    candidates.value = value
  } catch (e) {
    if (version !== candidateGeneration || signal.aborted) return
    candidateError.value = e.response?.status === 403 ? '需要订单管理与接口管理双权限，未获得查找权限。'
      : e.response?.status === 409 || e.response?.data?.code === -110 ? '订单或编号记录已变化，请重新查找；未关联编号。'
        : '编号查找未完成或返回数据不完整，请重试；未关联编号，也未重新下单。'
  } finally { if (version === candidateGeneration) candidateLoading.value = false }
}
function chooseCandidate(id) {
  if (busy.value || candidateLoading.value || requestId.value || !candidates.value?.receiptIds.includes(id)) return
  form.value = { receiptId: id, evidence: '', ownershipConfirmed: false }
  consent.value = false
}
function accept(value, order, id) {
  if (!validReceiptView(value, order, id) || expectedReceipt.value && value.receiptId !== expectedReceipt.value) throw new Error('incomplete receipt response')
  view.value = value; consent.value = false
  if (value.state === 'APPLIED') emit('applied', value)
}
async function preview() {
  if (busy.value || candidateLoading.value || requestId.value || !validReceiptForm(form.value)) return
  clearCandidates()
  const order = props.orderId, id = crypto.randomUUID(), version = ++generation
  requestId.value = id; expectedReceipt.value = form.value.receiptId; busy.value = true; error.value = ''
  const body = { ...form.value, evidence: form.value.evidence.trim(), requestId: id }
  try {
    const value = await previewOrderReceipt(order, body)
    if (version !== generation || order !== props.orderId) return
    accept(value, order, id)
  } catch (e) {
    if (version === generation) error.value = e.response?.status === 403 ? '需要订单管理与接口管理双权限，未获得核对权限。' : '核对结果未确认，请检查原请求；不要重复提交。'
  } finally { if (version === generation) { busy.value = false; form.value.evidence = ''; form.value.ownershipConfirmed = false } }
}
async function check() {
  if (busy.value || !requestId.value) return
  const order = props.orderId, id = requestId.value, version = ++generation
  controller?.abort(); controller = new AbortController(); const signal = controller.signal
  busy.value = true; error.value = ''; consent.value = false
  try {
    const value = await getOrderReceipt(order, id, signal)
    if (version !== generation || signal.aborted || order !== props.orderId) return
    accept(value, order, id); confirmSent.value = false
  } catch (e) {
    if (version === generation && !signal.aborted) { view.value = null; error.value = e.response?.status === 403 ? '没有读取此核对请求的权限。' : '原请求尚未确认或数据不完整；未重新查询订单，也未关联编号。' }
  } finally { if (version === generation) busy.value = false }
}
async function confirm() {
  if (busy.value || confirmSent.value || !consent.value || !receiptCanConfirm(view.value, clock.value)) return
  const order = props.orderId, id = requestId.value, version = ++generation
  confirmSent.value = true; busy.value = true; consent.value = false; error.value = ''
  try {
    const value = await confirmOrderReceipt(order, id)
    if (version !== generation || order !== props.orderId) return
    accept(value, order, id)
  } catch { if (version === generation) { view.value = null; error.value = '恢复结果未确认，请检查原请求，不要再次提交。' } }
  finally { if (version === generation) busy.value = false }
}
async function loadRecent() {
  showRecent.value = true; recent.value = []; recentLoaded.value = false; recentError.value = ''; recentLoading.value = true
  recentController?.abort(); recentController = new AbortController(); const signal = recentController.signal
  const order = props.orderId, version = ++recentGeneration
  try {
    const data = await recentOrderReceipts(order, signal)
    if (version !== recentGeneration || order !== props.orderId || signal.aborted) return
    if (!Array.isArray(data) || data.length > 20 || !data.every((v) => validReceiptView(v, order)) || new Set(data.map((v) => v.id)).size !== data.length) throw new Error('invalid history')
    recent.value = data; recentLoaded.value = true
  } catch { if (version === recentGeneration && !signal.aborted) recentError.value = '核对记录读取失败或数据不完整，请重试；未显示为空记录。' }
  finally { if (version === recentGeneration) recentLoading.value = false }
}
function select(item) { if (busy.value) return; reset(); requestId.value = item.id; expectedReceipt.value = item.receiptId; check() }
watch(() => form.value.receiptId, () => { form.value.ownershipConfirmed = false }, { flush: 'sync' })
watch(() => props.orderId, () => { reset(); recentGeneration++; recentController?.abort(); recent.value = []; recentLoaded.value = false; showRecent.value = false; recentLoading.value = false })
onBeforeUnmount(() => { reset(); recentGeneration++; recentController?.abort(); clearInterval(timer) })
</script>
<style scoped>
.receipt-recovery { color: var(--text-primary); min-width: 0; }
.receipt-heading, .receipt-preview header, .receipt-history header { display: flex; justify-content: space-between; align-items: flex-start; gap: 14px; flex-wrap: wrap; }
.receipt-heading h2 { margin: 4px 0 20px; font-size: 23px; line-height: 1.6; }
.receipt-eyebrow { color: var(--brand-primary); font-weight: 700; font-size: 11px; letter-spacing: 1px; }
.receipt-note, .receipt-preview p, .receipt-request small { color: var(--text-secondary); font-size: 12px; line-height: 1.8; }
.receipt-form { display: grid; gap: 16px; margin: 24px 0; }
.receipt-form .el-form-item { margin: 0; }
.receipt-form > .el-button, .receipt-preview > .el-button { justify-self: start; min-height: 44px; margin: 12px 0; }
.receipt-recovery :deep(.el-checkbox) { height: auto; align-items: flex-start; min-height: 44px; }
.receipt-recovery :deep(.el-checkbox__label) { white-space: normal; line-height: 1.7; }
.receipt-recovery :deep(.el-checkbox__input) { margin-top: 5px; }
.receipt-request { display: grid; gap: 8px; padding: 16px 0; }
.receipt-request span { font-size: 12px; color: var(--text-secondary); }
.receipt-request code, .receipt-history code { overflow-wrap: anywhere; font-size: 12px; }
.receipt-preview { padding: 20px; border: 1px solid var(--border-color-light); background: var(--surface-mica); border-radius: 10px; margin: 12px 0; }
.receipt-preview h3, .receipt-history h3 { margin: 0 0 12px; font-size: 17px; }
.receipt-preview dl { margin: 16px 0; display: grid; gap: 12px; }
.receipt-preview dl div { display: grid; grid-template-columns: 105px minmax(0,1fr); gap: 14px; font-size: 13px; line-height: 1.8; }
.receipt-preview dt { color: var(--text-secondary); }
.receipt-preview dd { margin: 0; overflow-wrap: anywhere; }
.receipt-actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 18px; }
.receipt-actions .el-button + .el-button { margin-left: 0; }
.receipt-skeleton { margin: 24px 0; }
.receipt-history { border-top: 1px solid var(--border-color-light); margin-top: 24px; padding-top: 20px; }
.receipt-history article { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 14px; padding: 16px 0; border-bottom: 1px solid var(--border-color-light); }
.receipt-history article > div { display: grid; gap: 6px; min-width: 0; }
.receipt-history span, .receipt-history code { color: var(--text-secondary); font-size: 12px; }
.receipt-discovery { margin: 24px 0; padding: 20px; border: 1px solid var(--border-color-light); border-radius: 10px; background: var(--surface-mica); }
.receipt-discovery header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; flex-wrap: wrap; }
.receipt-discovery h3 { margin: 0 0 6px; font-size: 16px; }
.receipt-discovery p { margin: 6px 0; font-size: 12px; line-height: 1.8; color: var(--text-secondary); }
.receipt-discovery .receipt-scope { font-size: 12px; }
.receipt-discovery > .receipt-scope { margin: 12px 0 0; }
.receipt-discovery :deep(.el-alert), .receipt-discovery :deep(.el-skeleton) { margin-top: 16px; }
.receipt-candidate-result { margin-top: 16px; border-top: 1px solid var(--border-color-light); padding-top: 12px; }
.receipt-discovery .receipt-candidate-summary { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.receipt-candidates { list-style: none; margin: 12px 0 0; padding: 0; max-height: 280px; overflow-y: auto; }
.receipt-candidates li { display: flex; gap: 12px; align-items: center; justify-content: space-between; padding: 10px 0; border-top: 1px solid var(--border-color-light); }
.receipt-candidates code { min-width: 0; overflow-wrap: anywhere; font-size: 13px; line-height: 1.6; }
.receipt-discovery .el-button { min-height: 44px; flex-shrink: 0; }
@media(max-width:720px) {
  .receipt-discovery { padding: 16px; }
  .receipt-heading h2 { font-size: 21px; }
  .receipt-preview { padding: 16px; }
  .receipt-preview dl div { grid-template-columns: 80px minmax(0,1fr); }
  .receipt-recovery .el-button, .receipt-recovery :deep(.el-input__wrapper) { min-height: 44px; box-sizing: border-box; }
}
</style>
