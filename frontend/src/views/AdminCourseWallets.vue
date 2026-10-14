<template>
  <main class="admin-course-wallets">
    <el-card><div class="heading"><div><h1>{{ activeTab==='contracts'?'课程密价协议':'课程专户资金' }}</h1><p>独立固定价、独立资金账户；改价只影响新订单，历史退款仍回原账户。</p></div><el-button v-if="canManage" type="primary" @click="openContract()">创建密价协议</el-button></div>
      <div class="filters"><el-input v-model="filters.userUid" aria-label="筛选代理UUID" placeholder="精确代理UUID" clearable /><el-select v-model="filters.platformId" aria-label="筛选绑定课程" placeholder="绑定课程" clearable filterable><el-option v-for="p in platforms" :key="p.id" :value="p.id" :label="p.displayName || p.name" /></el-select><el-button :loading="loading" @click="page=1;load()">查询</el-button><el-button v-if="canWallet" @click="showRecords(null,'transfers')">划转审计</el-button></div>
    </el-card>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-card><el-tabs v-model="activeTab" @tab-change="page=1;load()"><el-tab-pane v-if="canContracts" label="密价协议" name="contracts" /><el-tab-pane v-if="canWallet" label="资金账户" name="wallets" /></el-tabs>
      <div v-if="isMobile" v-loading="loading"><el-empty v-if="!rows.length" description="暂无匹配的协议或专户" /><article v-for="row in rows" :key="row.id" class="mobile-wallet-card">
        <h3>{{ row.platformName }} · {{ row.username }}</h3><small>{{ row.userUid }}</small><p>固定密价 ¥{{ row.fixedPrice }} · 首次门槛 ¥{{ row.minInitialDeposit }}</p><p><strong>专户余额 ¥{{ row.balance }}</strong></p>
        <p v-if="row.totalTransferIn != null">累计转入 ¥{{ formatRecordedCourseMoney(row.totalTransferIn) }} · 转回 ¥{{ formatRecordedCourseMoney(row.totalTransferredOut) }} · 消费 ¥{{ formatRecordedCourseMoney(row.totalConsumed) }} · 退款 ¥{{ formatRecordedCourseMoney(row.totalRefunded) }}</p>
        <p>协议状态：{{ contractStatusLabel(row.contractStatus || row.status) }}</p><p>协议版本 {{ row.contractVersion ?? row.version }} · {{ row.validUntil || '长期有效' }}</p>
        <p>最后修改：{{ row.updateTime || '—' }} · 操作人 #{{ row.updatedBy ?? '—' }}</p>
        <div class="mobile-actions"><el-button v-if="canManage" size="small" @click="openContract(row)">修改协议</el-button><el-button v-if="canContracts || canAudit" size="small" @click="showRecords(row.contractId || row.id,'history')">变更历史</el-button><el-button v-if="canWallet" size="small" @click="showRecords(row.walletId || row.id,'ledger')">资金流水</el-button><el-button v-if="canRelease" size="small" type="warning" @click="openRelease(row)">财务转回</el-button></div>
      </article></div>
      <el-table v-else v-loading="loading" :data="rows" empty-text="暂无匹配的协议或专户">
        <el-table-column label="代理" min-width="175"><template #default="{row}"><span>{{ row.username || row.userUid }}</span><small>{{ row.userUid }}</small></template></el-table-column>
        <el-table-column prop="platformName" label="绑定课程" min-width="170" />
        <el-table-column prop="fixedPrice" label="固定密价（元）" width="145" />
        <el-table-column prop="minInitialDeposit" label="首次门槛（元）" width="145" />
        <el-table-column prop="balance" label="专户余额（元）" width="145" />
        <el-table-column label="累计资金（元）" min-width="245"><template #default="{row}"><span v-if="row.totalTransferIn != null">转入 {{ formatRecordedCourseMoney(row.totalTransferIn) }} · 转回 {{ formatRecordedCourseMoney(row.totalTransferredOut) }}<br>消费 {{ formatRecordedCourseMoney(row.totalConsumed) }} · 退款 {{ formatRecordedCourseMoney(row.totalRefunded) }}</span><span v-else>统计暂未取得</span></template></el-table-column>
        <el-table-column label="状态/版本" width="160"><template #default="{row}"><el-tag>{{ contractStatusLabel(row.contractStatus || row.status) }}</el-tag><small>协议版本 {{ row.contractVersion ?? row.version }}</small></template></el-table-column>
        <el-table-column label="有效期" min-width="220"><template #default="{row}">{{ row.validFrom || '立即生效' }} ～ {{ row.validUntil || '长期有效' }}</template></el-table-column>
        <!-- 展示服务端已审计的修改身份和时间，便于管理员追溯协议配置。 -->
        <el-table-column label="最后修改" min-width="190"><template #default="{row}"><span>{{ row.updateTime || '—' }}</span><small>操作人 #{{ row.updatedBy ?? '—' }}</small></template></el-table-column>
        <el-table-column label="操作" width="260" fixed="right"><template #default="{row}"><el-button v-if="canManage" size="small" @click="openContract(row)">修改协议</el-button><el-button v-if="canContracts || canAudit" size="small" @click="showRecords(row.contractId || row.id,'history')">变更历史</el-button><el-button v-if="canWallet" size="small" @click="showRecords(row.walletId || row.id,'ledger')">资金流水</el-button><el-button v-if="canRelease" size="small" type="warning" @click="openRelease(row)">财务转回</el-button></template></el-table-column>
      </el-table><el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="prev, pager, next, total" @current-change="load" />
    </el-card>
    <el-dialog v-model="contractVisible" :title="editingId?'修改课程密价协议':'创建课程密价协议'" width="min(640px, 96vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" append-to-body>
      <el-form label-position="top">
        <el-form-item label="代理账号（精确绑定公开UUID）"><el-select v-if="canSearchUsers && !editingId" v-model="form.userUid" remote filterable :remote-method="searchUsers" :loading="searchLoading" placeholder="搜索代理账号或UUID" style="width:100%" aria-label="选择协议代理"><el-option v-for="user in userOptions" :key="user.uid" :value="user.uid" :label="`${user.username} · ${user.uid}`" /></el-select><el-input v-else v-model="form.userUid" aria-label="协议代理UUID" placeholder="代理公开UUID" :disabled="!!editingId || busy" /></el-form-item>
        <el-form-item label="绑定课程商品"><el-select v-model="form.platformId" aria-label="协议绑定课程" filterable :disabled="!!editingId || busy" style="width:100%"><el-option v-for="p in platforms" :key="p.id" :value="p.id" :label="p.displayName || p.name" /></el-select></el-form-item>
        <p v-if="selectedProduct">课程展示价 ¥{{ priceReference?.basePrice ?? selectedProduct.basePrice }}<span v-if="ordinaryReference"> · 当前代理普通成交参考价 ¥{{ ordinaryReference }}</span></p>
        <p v-if="referenceLoading" role="status">正在获取服务端普通参考价…</p>
        <el-alert v-if="referenceError" :title="referenceError" type="warning" :closable="false" />
        <div class="two-cols"><el-form-item label="固定密价（元）"><el-input v-model="form.fixedPrice" aria-label="固定密价" :disabled="busy" /></el-form-item><el-form-item label="首次最低预存（元，0免门槛）"><el-input v-model="form.minInitialDeposit" aria-label="首次最低预存" :disabled="busy" /></el-form-item></div>
        <el-form-item label="协议状态"><el-select v-model="form.status" aria-label="协议状态" :disabled="busy" style="width:100%"><el-option v-for="status in statuses" :key="status" :value="status" :label="contractStatusLabel(status)" :disabled="existingTerminated && status!=='TERMINATED'" /></el-select></el-form-item>
        <div class="two-cols"><el-form-item label="生效时间（可选）"><el-date-picker v-model="form.validFrom" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="立即生效" style="width:100%" :disabled="busy" /></el-form-item><el-form-item label="到期时间（可选）"><el-date-picker v-model="form.validUntil" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="长期有效" style="width:100%" :disabled="busy" /></el-form-item></div>
        <el-form-item label="操作原因（必填）"><el-input v-model="form.reason" aria-label="协议操作原因" type="textarea" maxlength="255" show-word-limit :disabled="busy" /></el-form-item>
      </el-form>
      <el-alert title="修改只影响新订单；预存门槛大于0时新协议自动待激活。暂停、到期、终止均保留余额与审计，已终止协议不能恢复。" type="warning" :closable="false" />
      <el-alert v-if="formError" :title="formError" type="error" :closable="false" />
      <template #footer><el-button :disabled="busy" @click="contractVisible=false">取消</el-button><el-button type="primary" :loading="busy || referenceLoading" :disabled="referenceLoading" @click="saveContract">保存协议</el-button></template>
    </el-dialog>
    <el-dialog v-model="releaseVisible" title="财务批准专户转回" width="min(520px, 96vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" append-to-body>
      <p>目标代理：{{ selected?.username || selected?.userUid }}</p><p>绑定课程 {{ selected?.platformName }} · 专户余额 ¥{{ selected?.balance }}</p>
      <el-alert title="此操作代表财务审批；只能将该代理预存款退回同一代理的普通余额。请先核查资金流水和未结算事项。" type="warning" :closable="false" />
      <el-form label-position="top"><el-form-item label="批准转回金额（元）"><el-input v-model="releaseAmount" aria-label="批准转回金额" :disabled="busy" /></el-form-item><el-form-item label="审批原因（必填）"><el-input v-model="releaseReason" aria-label="审批原因" type="textarea" maxlength="255" :disabled="busy" /></el-form-item></el-form>
      <el-alert v-if="formError" :title="formError" type="error" :closable="false" />
      <template #footer><el-button :disabled="busy" @click="releaseVisible=false">取消</el-button><el-button type="warning" :loading="busy" @click="approveRelease">批准并转回</el-button></template>
    </el-dialog>
    <CourseWalletRecords :visible="records.visible" :id="records.id" :kind="records.kind" :filters="filters" admin @close="records.visible=false" />
  </main>
</template>
<script setup>
import { ref,reactive,computed,onMounted,watch } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage,ElMessageBox } from 'element-plus';
import { sessionUserInfo } from '@/utils/authSession';
import { hasPermission,hasAnyPermission } from '@/utils/permissions';
import { getCoursePlatforms } from '@/api/course';
import { queryUsers } from '@/api/user';
import { getCourseContracts,getCourseContractPriceReference,getAdminCourseWallets,createCourseContract,updateCourseContract,releaseCourseWallet } from '@/api/courseWallet';
import { courseMoney,transferSummary,formatRecordedCourseMoney,contractStatusLabel,createWalletTransferRequestKeys } from '@/utils/courseWallet';
import CourseWalletRecords from '@/components/CourseWalletRecords.vue';
import { useResponsive } from '@/composables/useResponsive';
const { isMobile } = useResponsive();
const route=useRoute();
const canManage=computed(()=>hasPermission(sessionUserInfo.value,'course-private-price:manage'));
const canContracts=computed(()=>hasAnyPermission(sessionUserInfo.value,['course-private-price:read','course-private-price:manage']));
const canWallet=computed(()=>hasAnyPermission(sessionUserInfo.value,['course-wallet:read:any','course-wallet:audit','course-wallet:release']));
const canAudit=computed(()=>hasPermission(sessionUserInfo.value,'course-wallet:audit'));
const canRelease=computed(()=>hasPermission(sessionUserInfo.value,'course-wallet:release'));
const canSearchUsers=computed(()=>hasPermission(sessionUserInfo.value,'user:read'));
const activeTab=ref(route.path.includes('private-contracts')?'contracts':'wallets'),rows=ref([]),platforms=ref([]),page=ref(1),total=ref(0),loading=ref(false),error=ref('');
const filters=reactive({userUid:route.query.userUid || '',platformId:null});
const records=reactive({visible:false,id:null,kind:'ledger'});
const contractVisible=ref(false),editingId=ref(null),form=reactive({}),formError=ref(''),busy=ref(false),userOptions=ref([]),searchLoading=ref(false),existingTerminated=ref(false);
const selected=ref(null),releaseVisible=ref(false),releaseAmount=ref(''),releaseReason=ref('');
// 财务转回的原请求身份在关闭弹窗后保留，收到明确成功才释放。
const releaseKeys=createWalletTransferRequestKeys({userUid:()=>sessionUserInfo.value?.uid});
const selectedProduct=computed(()=>platforms.value.find(p=>p.id===form.platformId));
// 管理配置的普通参考价也来自同一服务端定价引擎，不在浏览器重复算价。
const priceReference=ref(null),referenceLoading=ref(false),referenceError=ref('');let referenceSequence=0;
const ordinaryReference=computed(()=>priceReference.value?.ordinaryPrice);
async function loadPriceReference(){
  const sequence=++referenceSequence;priceReference.value=null;referenceError.value='';referenceLoading.value=false;
  if(!contractVisible.value||!form.platformId||!String(form.userUid||'').match(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i))return;
  referenceLoading.value=true;
  try{const res=await getCourseContractPriceReference({userUid:form.userUid,platformId:form.platformId});if(sequence===referenceSequence)priceReference.value={basePrice:courseMoney(res.data.basePrice),ordinaryPrice:courseMoney(res.data.ordinaryPrice),rateType:res.data.rateType};}
  catch(e){if(sequence===referenceSequence)referenceError.value=e.message||'普通参考价暂不可用，请重新选择代理与课程';}
  finally{if(sequence===referenceSequence)referenceLoading.value=false;}
}
watch(()=>[contractVisible.value,form.userUid,form.platformId],loadPriceReference);
const statuses=computed(()=>editingId.value?['ACTIVE','PENDING_ACTIVATION','SUSPENDED','EXPIRED','TERMINATED']:['ACTIVE','PENDING_ACTIVATION']);
async function load(){if(!(activeTab.value==='contracts'?canContracts.value:canWallet.value)){error.value='当前账号无权读取此页面';rows.value=[];return;}loading.value=true;error.value='';try{const fn=activeTab.value==='contracts'?getCourseContracts:getAdminCourseWallets;const res=await fn({page:page.value,pageSize:20,userUid:filters.userUid||undefined,platformId:filters.platformId||undefined});rows.value=res.data.records||[];total.value=res.data.total||0;}catch(e){error.value=e.message||'加载失败';}finally{loading.value=false;}}
async function searchUsers(keyword){if(!keyword.trim())return;searchLoading.value=true;try{const res=await queryUsers({keyword,page:1,pageSize:20,status:1});userOptions.value=res.data.records||[];}catch(e){formError.value=e.message;}finally{searchLoading.value=false;}}
function openContract(row){if(busy.value)return;editingId.value=row?(row.contractId || row.id):null;existingTerminated.value=(row?.contractStatus||row?.status)==='TERMINATED';formError.value='';Object.assign(form,{userUid:row?.userUid||filters.userUid||'',platformId:row?.platformId||null,fixedPrice:String(row?.fixedPrice??''),minInitialDeposit:String(row?.minInitialDeposit??'0'),status:row?.contractStatus||row?.status||'ACTIVE',validFrom:row?.validFrom||null,validUntil:row?.validUntil||null,version:row?.contractVersion??row?.version??null,reason:''});contractVisible.value=true;}
function showRecords(id,kind){Object.assign(records,{visible:true,id,kind});}
async function saveContract(){if(busy.value)return;busy.value=true;formError.value='';try{
  const price=courseMoney(form.fixedPrice),minimum=courseMoney(form.minInitialDeposit);if(price==='0.00')throw new Error('固定密价必须大于0');if(!form.userUid||!form.platformId)throw new Error('必须指定代理UUID和课程商品');if(!form.reason.trim())throw new Error('请填写操作原因');if(form.validFrom&&form.validUntil&&form.validUntil<=form.validFrom)throw new Error('到期时间必须晚于生效时间');
  if(!editingId.value&&!priceReference.value)throw new Error('请先取得选定代理的服务端普通参考价');
  const low=selectedProduct.value&&BigInt(price.replace('.',''))<BigInt(courseMoney(priceReference.value?.basePrice??selectedProduct.value.basePrice).replace('.',''))?'该密价低于课程展示价，请确认报价已获授权。':'';
  await ElMessageBox.confirm(`${low}当前代理普通参考价 ¥${ordinaryReference.value || '暂不可用'}。为选定代理和课程设置固定密价 ¥${price}、首次门槛 ¥${minimum}。仅新订单采用新协议，历史成交价和退款来源保持不变。`,'确认协议配置',{type:'warning',confirmButtonText:'确认保存',cancelButtonText:'取消'});
  const payload={...form,fixedPrice:price,minInitialDeposit:minimum,reason:form.reason.trim()};if(editingId.value)await updateCourseContract(editingId.value,payload);else await createCourseContract(payload);contractVisible.value=false;await load();ElMessage.success('协议已保存');
}catch(e){if(e!=='cancel'&&e!=='close')formError.value=e.message||'协议保存失败';}finally{busy.value=false;}}
function openRelease(row){if(busy.value)return;selected.value=row;releaseAmount.value='';releaseReason.value='';formError.value='';releaseVisible.value=true;try{releaseKeys.assertRecoverable();}catch(e){formError.value=e.message;}}
async function approveRelease(){if(busy.value)return;busy.value=true;formError.value='';try{
  if(!releaseReason.value.trim())throw new Error('必须填写审批原因');const walletId=selected.value.walletId||selected.value.id,prior=releaseKeys.pending(walletId,'OUT',releaseAmount.value);const preview=prior?.preview || transferSummary('0',selected.value.balance,releaseAmount.value,'OUT');
  await ElMessageBox.confirm(prior?.preview?'核对原财务转回结果：服务端将按原请求键确认，不会重复入账。请以核对后刷新取得的真实余额为准。':`批准向代理 ${selected.value.userUid} 的普通余额转回 ¥${preview.amount}。课程专户余额：¥${preview.walletBefore} → ¥${preview.walletAfter}；该代理普通余额增加 ¥${preview.amount}。`,'确认财务审批',{type:'warning',confirmButtonText:'批准转回',cancelButtonText:'取消'});
  const requestKey=releaseKeys.forTransfer(walletId,'OUT',preview.amount,preview);
  await releaseCourseWallet(walletId,{amount:preview.amount,reason:releaseReason.value.trim(),requestKey});releaseKeys.confirmed(walletId,'OUT',preview.amount,requestKey);releaseVisible.value=false;await load();ElMessage.success('财务转回已完成');
}catch(e){if(e!=='cancel'&&e!=='close')formError.value=e.message||'转回失败，请核查流水后使用原请求重试';}finally{busy.value=false;}}
watch(()=>route.path,()=>{activeTab.value=route.path.includes('private-contracts')?'contracts':'wallets';page.value=1;load();});
onMounted(async()=>{try{platforms.value=(await getCoursePlatforms()).data||[];}catch(e){error.value=e.message;}await load();});
</script>
<style scoped>.admin-course-wallets{padding:20px;display:grid;gap:16px}.heading,.filters{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.heading{justify-content:space-between}.filters{margin-top:16px}.filters .el-input{max-width:330px}.filters .el-select{width:240px}h1{font-size:24px;margin:0}p,small{color:var(--el-text-color-secondary)}small{display:block;font-size:11px;word-break:break-all}.two-cols{display:grid;grid-template-columns:1fr 1fr;gap:16px}.el-pagination{margin-top:16px;overflow:auto}.mobile-wallet-card{padding:16px 0;border-top:1px solid var(--el-border-color)}.mobile-actions{display:flex;gap:8px;flex-wrap:wrap}.mobile-actions .el-button{margin-left:0}@media(max-width:600px){.admin-course-wallets{padding:10px}.two-cols{grid-template-columns:1fr;gap:0}.filters .el-input,.filters .el-select{width:100%;max-width:none}}</style>
