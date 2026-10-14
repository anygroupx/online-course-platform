<template>
  <main class="analytics-page">
    <header><div><h1>密价经营分析</h1><p>按原订单事实核验成交，将预存本金与成本估算分别展示。</p></div><el-button :loading="loading" @click="load">刷新分析</el-button></header>
    <el-form inline @submit.prevent="load"><el-form-item label="商品 ID"><el-input v-model="platformId" aria-label="分析商品 ID" inputmode="numeric" clearable /></el-form-item><el-form-item label="套餐 ID"><el-input v-model="planId" aria-label="分析套餐 ID" inputmode="numeric" clearable /></el-form-item><el-button type="primary" :loading="loading" @click="load">筛选</el-button></el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon /><template v-if="data">
      <p>统计时刻 {{ privateDate(data.asOf) }} · 原事实存在疑点的周期 {{ data.reviewRequiredPeriodCount }} · 待履约订单 {{ data.pendingFulfillmentOrderCount }}</p>
      <section class="metrics"><article v-for="metric in metrics" :key="metric.key"><p>{{ metric.label }}</p><strong>{{ metric.money?'¥'+privateMoney(data[metric.key]):data[metric.key]??'未知' }}</strong><small>{{ metric.note }}</small></article></section>
      <el-alert title="预存本金不是营收；预存负债和待转回金额不能计入利润。" type="warning" :closable="false" show-icon />
      <section class="quality"><h2>资金对账核查</h2><p>核查结果：{{ {BALANCED:'已核对账本与余额',REVIEW_REQUIRED:'存在明确账本或余额异常',INSUFFICIENT_EVIDENCE:'存在未核验来源或历史期初'}[data.walletReconciliationStatus]||'待核对' }}</p><p>账本/余额异常专户 {{ data.walletLedgerMismatchCount??0 }} · 期初证据不足 {{ data.walletOpeningUnverifiedCount??0 }} · 划转对边或原支付来源未核验 {{ data.walletSourceUnverifiedCount??0 }}</p><p>这里只报告原证据，余额不会被自动修复或冻结；请在原专户流水和只读对账报告中核查。</p></section><section class="quality"><h2>成本证据与资金账龄</h2><p>成本证据：{{ {VERIFIED_ESTIMATE:'已核验成本估算',PARTIAL_COVERAGE:'部分成本覆盖',INSUFFICIENT_EVIDENCE:'证据不足'}[data.costStatus] }}</p><p>已覆盖 {{ data.costCoveredOrderCount }} / {{ data.effectiveOrderCount }} 笔有效成交；成本估算 {{ data.verifiedCostEstimate===null?'未知':'¥'+privateMoney(data.verifiedCostEstimate) }}，估算贡献毛利 {{ data.estimatedContributionMargin===null?'未知':'¥'+privateMoney(data.estimatedContributionMargin) }}。</p><p>实际利润：{{ data.actualProfit===null?'未知，尚无逐单实际供应商结算成本':privateMoney(data.actualProfit) }}</p><p>平均资金账龄：{{ data.averageCashAgeDays===null?'未知':data.averageCashAgeDays+' 天' }} · 完整账龄证据覆盖本金 ¥{{ privateMoney(data.cashAgeCoveredAmount) }}</p><p>账龄采用完整流水的先进先出模型；退款和未知期初本金不猜测历史资金年龄。</p></section>
      <section v-for="group in groups" :key="group.key" class="quality"><h2>{{ group.label }}</h2><el-table :data="data[group.key]||[]"><el-table-column label="分组"><template #default="scope">{{ group.key==='byChannel'?channel(scope.row.key):scope.row.key }}</template></el-table-column><el-table-column prop="periodCount" label="权益周期" /><el-table-column prop="effectiveOrderCount" label="有效成交单数" /><el-table-column label="有效成交额"><template #default="scope">¥{{ privateMoney(scope.row.effectiveRevenue) }}</template></el-table-column></el-table></section>
      <section class="quality"><h2>统计口径</h2><p v-for="note in data.limitations" :key="note">{{ note }}</p></section>
    </template>
  </main>
</template>
<script setup>
import {ref,onMounted} from 'vue';
import {getPrivateAnalytics} from '@/api/coursePrivateOperations';
import {privateMoney,privateDate} from '@/utils/coursePrivateOperations';
const data=ref(null),loading=ref(false),error=ref(''),platformId=ref(''),planId=ref('');
const metrics=[{key:'publishedPlanCount',label:'已发布套餐'},{key:'enrolledAgentCount',label:'已签约代理'},{key:'pendingActivationPeriodCount',label:'待激活周期'},{key:'activationAgent7Days',label:'近 7 天激活代理'},{key:'activationAgent30Days',label:'近 30 天激活代理'},{key:'firstOrderConversionRate',label:'首次有效成交转化率 %',note:'以已激活首期协议为分母'},{key:'automaticRenewalSuccessRate',label:'自动续期成功率 %',note:'以已到购买边界的自动续期周期为分母'},{key:'upgradeCount',label:'已生效升级'},{key:'upgradeContractRate',label:'协议升级率 %',note:'筛选内唯一已升级协议 / 已签约协议'},{key:'effectiveRevenue',label:'有效成交额',money:true,note:'已核验履约，扣除退款'},{key:'walletReserve',label:'专户预存负债',money:true,note:'本金，不计为营收'},{key:'pendingReleaseAmount',label:'待转回申请金额',money:true,note:'尚未转回，也未冻结'}];
const groups=[{key:'byPlan',label:'按套餐'},{key:'byProduct',label:'按商品'},{key:'byChannel',label:'按申请来源'}];
const channel=value=>({SELF:'代理自行申请',ADMIN:'运营指定',SYSTEM:'系统',UNKNOWN:'来源未知'}[value]||value);
// 服务端再次验证权限与筛选；读取失败时清空旧筛选结果，避免误读旧统计。
async function load(){loading.value=true;error.value='';data.value=null;try{const params={};for(const [key,value] of [['platformId',platformId.value],['planId',planId.value]]){if(!value.trim())continue;if(!/^[1-9]\d*$/.test(value)||!Number.isSafeInteger(Number(value)))throw new Error('商品和套餐 ID 必须为正整数');params[key]=Number(value);}data.value=(await getPrivateAnalytics(params)).data;}catch(e){error.value=e.message||'经营分析读取失败';}finally{loading.value=false;}}
onMounted(load);
</script>
<style scoped>
.analytics-page{padding:22px;display:grid;grid-template-columns:minmax(0,1fr);gap:18px;max-width:1400px;margin:auto;width:100%;box-sizing:border-box}.analytics-page>*{min-width:0}header{display:flex;justify-content:space-between;gap:16px;align-items:center;flex-wrap:wrap}h1{font-size:27px;margin:0 0 10px}p{line-height:1.6;color:var(--el-text-color-secondary)}.metrics{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:14px}.metrics article,.quality{padding:20px;border:1px solid var(--el-border-color-light);border-radius:12px;background:var(--el-bg-color)}.metrics p{margin:0 0 10px}.metrics strong{font-size:26px;overflow-wrap:anywhere}.metrics small{display:block;margin-top:10px;color:var(--el-text-color-secondary)}h2{font-size:18px}.el-form{display:flex;flex-wrap:wrap;gap:8px}.el-form-item{margin:0}@media(max-width:600px){.analytics-page{padding:12px}.metrics{grid-template-columns:1fr}.el-form-item{width:100%}}
</style>
