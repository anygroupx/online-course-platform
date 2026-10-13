<template>
  <main class="course-wallet-page">
    <el-card><div class="wallet-heading"><div><h1>我的课程专户</h1><p>普通余额 ¥{{ generalBalance ?? '读取中' }} · 每个课程的预存款独立使用。</p></div><el-button :loading="loading" @click="load">刷新余额</el-button></div>
      <el-alert title="预存余额只能购买绑定课程；订单退款回原专户。不能自行提现或转回，需财务人员审核处理。" type="info" :closable="false" show-icon />
    </el-card>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-card v-loading="loading"><div class="wallet-heading"><h2>我的专属课程</h2><el-button @click="showRecords(null,'transfers')">划转记录</el-button></div>
      <div v-if="isMobile" class="wallet-mobile-list">
        <el-empty v-if="!rows.length" description="暂无课程专户，请联系管理员配置协议" />
        <article v-for="row in rows" :key="row.id" class="wallet-mobile-card">
          <h3>{{ row.platformName }}</h3><p>专属密价 ¥{{ row.fixedPrice }}</p><p><strong>可用专户余额 ¥{{ row.balance }}</strong></p>
          <p v-if="row.totalTransferIn != null">累计转入 ¥{{ formatRecordedCourseMoney(row.totalTransferIn) }} · 消费 ¥{{ formatRecordedCourseMoney(row.totalConsumed) }} · 退款 ¥{{ formatRecordedCourseMoney(row.totalRefunded) }}</p>
          <p>协议状态：{{ contractStatusLabel(row.contractStatus) }}</p><p v-if="!row.activatedAt">首次预存门槛 ¥{{ row.minInitialDeposit }}</p>
          <p>有效期：{{ row.validFrom || '立即生效' }} ～ {{ row.validUntil || '长期有效' }}</p>
          <el-button type="primary" @click="openTransfer(row)" :disabled="!['ACTIVE','PENDING_ACTIVATION'].includes(row.contractStatus)">转入预存</el-button><el-button @click="showRecords(row.id,'ledger')">资金流水</el-button>
        </article>
      </div>
      <el-table v-else :data="rows" empty-text="暂无课程专户，请联系管理员配置协议">
        <el-table-column prop="platformName" label="绑定课程" min-width="180" />
        <el-table-column label="专属密价" width="130"><template #default="{row}">¥{{ row.fixedPrice }}</template></el-table-column>
        <el-table-column label="专户可用余额" width="150"><template #default="{row}"><strong>¥{{ row.balance }}</strong></template></el-table-column>
        <el-table-column label="累计资金（元）" min-width="220"><template #default="{row}"><span v-if="row.totalTransferIn != null">转入 {{ formatRecordedCourseMoney(row.totalTransferIn) }}<br>消费 {{ formatRecordedCourseMoney(row.totalConsumed) }} · 退款 {{ formatRecordedCourseMoney(row.totalRefunded) }}</span><span v-else>统计暂未取得</span></template></el-table-column>
        <el-table-column label="协议状态" min-width="180"><template #default="{row}"><el-tag>{{ contractStatusLabel(row.contractStatus) }}</el-tag><small v-if="!row.activatedAt">首次预存门槛 ¥{{ row.minInitialDeposit }}</small></template></el-table-column>
        <el-table-column label="有效期" min-width="220"><template #default="{row}">{{ row.validFrom || '立即生效' }} ～ {{ row.validUntil || '长期有效' }}</template></el-table-column>
        <el-table-column label="操作" width="180" fixed="right"><template #default="{row}"><el-button size="small" type="primary" @click="openTransfer(row)" :disabled="!['ACTIVE','PENDING_ACTIVATION'].includes(row.contractStatus)">转入预存</el-button><el-button size="small" @click="showRecords(row.id,'ledger')">流水</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="prev, pager, next, total" @current-change="load" />
    </el-card>
    <el-dialog v-model="transferVisible" title="普通余额转入课程专户" width="min(540px, 96vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy">
      <p>绑定课程：{{ selected?.platformName }}</p><p>普通余额 ¥{{ generalBalance ?? '读取中' }} · 专户余额 ¥{{ selected?.balance }}</p>
      <el-form label-position="top" @submit.prevent="submitTransfer"><el-form-item label="转入金额（元）"><el-input v-model="amount" aria-label="转入金额" placeholder="例如100.00，最多两位小数" :disabled="busy" /></el-form-item><el-form-item label="备注（可选）"><el-input v-model="reason" aria-label="转入备注" maxlength="255" :disabled="busy" /></el-form-item></el-form>
      <p v-if="estimate" role="status">划转后普通余额 ¥{{ estimate.generalAfter }}；本课程专户 ¥{{ estimate.walletAfter }}</p>
      <el-alert v-if="transferError" :title="transferError" type="error" :closable="false" />
      <el-alert title="按1:1划转，不增加总充值金额。网络结果未知时请核对原请求，重开弹窗或同一标签页重载后重试均使用原键，不会重复入账。" type="warning" :closable="false" />
      <template #footer><el-button :disabled="busy" @click="transferVisible=false">取消</el-button><el-button type="primary" :loading="busy" :disabled="!estimate" @click="submitTransfer">确认转入</el-button></template>
    </el-dialog>
    <CourseWalletRecords :visible="records.visible" :id="records.id" :kind="records.kind" @close="records.visible=false" />
  </main>
</template>
<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage,ElMessageBox } from 'element-plus';
import { getCourseWallets,transferIntoCourseWallet } from '@/api/courseWallet';
import { getUserInfo } from '@/api/user';
import { sessionUserInfo } from '@/utils/authSession';
import { transferSummary,courseMoney,formatRecordedCourseMoney,contractStatusLabel,createWalletTransferRequestKeys } from '@/utils/courseWallet';
import CourseWalletRecords from '@/components/CourseWalletRecords.vue';
import { useResponsive } from '@/composables/useResponsive';
const { isMobile } = useResponsive();
const rows=ref([]),page=ref(1),total=ref(0),loading=ref(false),error=ref(''),generalBalance=ref(null);
const transferVisible=ref(false),selected=ref(null),amount=ref(''),reason=ref(''),busy=ref(false),transferError=ref('');
// 请求身份独立于弹窗开关，网络未知结果重试不能重复划转。
const transferKeys=createWalletTransferRequestKeys({userUid:()=>sessionUserInfo.value?.uid});
const records=reactive({visible:false,id:null,kind:'ledger'});
const estimate=computed(()=>{try{return transferKeys.pending(selected.value?.id,'IN',amount.value)?.preview || transferSummary(generalBalance.value,selected.value?.balance,amount.value,'IN');}catch{return null;}});
async function load(){loading.value=true;error.value='';try{const [wallets,user]=await Promise.all([getCourseWallets({page:page.value,pageSize:20}),getUserInfo()]);rows.value=wallets.data.records||[];total.value=wallets.data.total||0;generalBalance.value=courseMoney(user.data.balance);sessionUserInfo.value={...sessionUserInfo.value,...user.data};}catch(e){error.value=e.message||'专户加载失败，请刷新重试';}finally{loading.value=false;}}
function openTransfer(row){if(busy.value)return;selected.value=row;amount.value='';reason.value='';transferError.value='';transferVisible.value=true;try{transferKeys.assertRecoverable();}catch(e){transferError.value=e.message;}}
function showRecords(id,kind){Object.assign(records,{visible:true,id,kind});}
async function submitTransfer(){if(busy.value)return;busy.value=true;transferError.value='';try{const prior=transferKeys.pending(selected.value.id,'IN',amount.value);const preview=prior?.preview || transferSummary(generalBalance.value,selected.value.balance,amount.value,'IN');
  await ElMessageBox.confirm(prior?.preview?'核对原划转请求结果：服务端将按原请求键确认，不会重复入账。页面预估来自原尝试，实际余额请以核对后刷新为准。':`向“${selected.value.platformName}”专户转入 ¥${preview.amount}。普通余额：¥${preview.generalBefore} → ¥${preview.generalAfter}；课程专户：¥${preview.walletBefore} → ¥${preview.walletAfter}。预存款仅用于本课程。`,'确认资金划转',{confirmButtonText:'确认划转',cancelButtonText:'取消',type:'warning'});
  const walletId=selected.value.id,requestKey=transferKeys.forTransfer(walletId,'IN',preview.amount,preview);
  await transferIntoCourseWallet(walletId,{amount:preview.amount,requestKey,reason:reason.value});transferKeys.confirmed(walletId,'IN',preview.amount,requestKey);transferVisible.value=false;await load();ElMessage.success(error.value?'已划转，余额刷新失败，请重试刷新':'转入成功，余额已刷新');
}catch(e){if(e!=='cancel'&&e!=='close')transferError.value=e.message||'划转失败，请检查流水后用原请求重试';}finally{busy.value=false;}}
onMounted(load);
</script>
<style scoped>.course-wallet-page{padding:20px;display:grid;gap:16px}.wallet-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}h1{font-size:24px;margin:0}h2{font-size:18px}p,small{color:var(--el-text-color-secondary)}small{display:block;margin-top:6px}.el-pagination{margin-top:16px;overflow:auto}.wallet-mobile-card{padding:16px 0;border-top:1px solid var(--el-border-color)}.wallet-mobile-card h3{margin:0}.wallet-mobile-card strong{color:var(--el-color-primary)}@media(max-width:600px){.course-wallet-page{padding:10px}.wallet-heading{align-items:flex-start}.el-table{font-size:13px}}</style>
