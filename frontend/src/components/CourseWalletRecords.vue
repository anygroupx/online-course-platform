<template>
  <el-dialog :model-value="visible" :title="title" width="min(920px, 96vw)" @close="$emit('close')" append-to-body>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-table v-loading="loading" :data="rows" style="width:100%" empty-text="暂无记录">
      <el-table-column v-if="kind==='history'" type="expand"><template #default="{row}"><div class="audit-json"><p>修改前</p><pre>{{ row.beforeJson || '新建协议' }}</pre><p>修改后</p><pre>{{ row.afterJson }}</pre></div></template></el-table-column>
      <el-table-column prop="id" label="编号" width="90" />
      <!-- 全局划转审计显示资金归属和审批证据，便于按钱包核查。 -->
      <el-table-column v-if="kind==='transfers'" prop="walletId" label="钱包编号" width="110" />
      <el-table-column v-if="kind!=='history'" label="业务类型" min-width="140"><template #default="{row}">{{ labels[row.bizType || row.type] || row.bizType || row.type }}</template></el-table-column>
      <el-table-column v-if="kind!=='history'" prop="amount" label="金额（元）" width="120" />
      <el-table-column v-if="kind==='ledger'" label="方向" width="80"><template #default="{row}">{{ row.direction===1?'入账':'出账' }}</template></el-table-column>
      <el-table-column v-if="kind==='ledger'" prop="balanceBefore" label="此前余额" width="120" />
      <el-table-column v-if="kind==='ledger'" prop="balanceAfter" label="此后余额" width="120" />
      <el-table-column v-if="kind==='transfers'" label="状态" width="100"><template #default="{row}">{{ row.status==='SUCCEEDED'?'已成功':row.status }}</template></el-table-column>
      <el-table-column v-if="kind!=='history'" prop="bizNo" label="业务单号" min-width="200" show-overflow-tooltip />
      <el-table-column prop="operatorId" label="操作人" width="100" />
      <el-table-column v-if="kind==='transfers'" prop="approvedBy" label="审批人" width="100" />
      <el-table-column label="原因/备注" min-width="160"><template #default="{row}">{{ row.reason || row.remark || '—' }}</template></el-table-column>
      <el-table-column prop="createTime" label="时间" min-width="180" />
      <el-table-column v-if="kind==='transfers'" prop="completedAt" label="完成时间" min-width="180" />
    </el-table>
    <el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="prev, pager, next, total" @current-change="load" />
  </el-dialog>
</template>
<script setup>
import { ref, watch, computed } from 'vue';
import { getCourseWalletLedger,getAdminCourseWalletLedger,getCourseContractHistory,getCourseTransfers,getAdminCourseTransfers } from '@/api/courseWallet';
const props=defineProps({visible:Boolean,id:[Number,String],kind:{type:String,default:'ledger'},admin:Boolean,filters:Object});
defineEmits(['close']);
const page=ref(1),total=ref(0),rows=ref([]),loading=ref(false),error=ref('');
const title=computed(()=>({ledger:'课程专户资金流水',history:'协议变更审计',transfers:'资金划转记录'}[props.kind]));
const labels={TRANSFER_IN:'普通余额转入',TRANSFER_OUT:'财务批准转回',ORDER:'订单消费',CONSUME:'订单消费',REFUND:'原订单退款'};
async function load(){loading.value=true;error.value='';try{const params={page:page.value,pageSize:20,...props.filters};let response;
  if(props.kind==='history')response=await getCourseContractHistory(props.id,params);
  else if(props.kind==='transfers')response=await(props.admin?getAdminCourseTransfers:getCourseTransfers)(params);
  else response=await(props.admin?getAdminCourseWalletLedger:getCourseWalletLedger)(props.id,params);
  rows.value=response.data.records||[];total.value=response.data.total||0;
}catch(e){error.value=e.message||'记录加载失败';}finally{loading.value=false;}}
watch(()=>[props.visible,props.id,props.kind],()=>{if(props.visible){page.value=1;rows.value=[];load();}});
</script>
<style scoped>.el-pagination{margin-top:16px;max-width:100%;overflow:auto}.audit-json{padding:12px}.audit-json pre{white-space:pre-wrap;word-break:break-all}</style>
