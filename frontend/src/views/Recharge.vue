<template>
  <div class="recharge-page">
    <h1>账户充值</h1>

    <!-- 余额信息 -->
    <el-card class="balance-card">
      <div class="balance-info">
        <div class="balance-item">
          <div class="balance-label">当前余额</div>
          <div class="balance-value">¥{{ userInfo?.balance || "0.00" }}</div>
        </div>
        <div class="balance-item">
          <div class="balance-label">总充值金额</div>
          <div class="balance-value">
            ¥{{ userInfo?.totalRecharge || "0.00" }}
          </div>
        </div>
      </div>
    </el-card>

    <!-- 充值方式选择 -->
    <el-card class="recharge-methods">
      <template #header>
        <div class="card-header">
          <span>选择充值方式</span>
        </div>
      </template>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- 支付宝支付 -->
        <el-tab-pane label="支付宝支付" name="alipay">
          <div class="alipay-recharge">
            <el-form :model="alipayForm" label-width="120px">
              <!-- 快捷金额选择 -->
              <el-form-item label="充值金额">
                <div class="amount-options">
                  <div
                    v-for="preset in presetAmounts"
                    :key="preset"
                    class="amount-option"
                    :class="{ active: alipayForm.amount === preset }"
                    @click="selectAmount(preset)"
                  >
                    <span class="amount-value">{{ preset }}</span>
                    <span class="amount-unit">元</span>
                  </div>
                </div>
              </el-form-item>

              <!-- 自定义金额 -->
              <el-form-item label="自定义金额">
                <el-input
                  v-model.number="alipayForm.customAmount"
                  type="number"
                  :min="minAmount"
                  placeholder="请输入充值金额"
                  style="width: 300px"
                  @input="selectCustomAmount"
                >
                  <template #append>元</template>
                </el-input>
                <div class="tip">最低充值金额：{{ minAmount }}元</div>
              </el-form-item>

              <!-- 支付方式 -->
              <el-form-item label="支付方式">
                <el-radio-group v-model="alipayForm.paymentType">
                  <el-radio label="PC" v-if="!isMobile">电脑网站支付</el-radio>
                  <el-radio label="WAP" v-if="isMobile">手机网站支付</el-radio>
                </el-radio-group>
              </el-form-item>

              <!-- 充值总额 -->
              <el-form-item label="充值总额">
                <span class="total-amount">¥{{ alipayForm.amount || 0 }}</span>
              </el-form-item>

              <el-form-item>
                <el-button
                  type="primary"
                  @click="handleAlipayRecharge"
                  :loading="recharging"
                  :disabled="
                    !alipayForm.amount || alipayForm.amount < minAmount
                  "
                >
                  立即充值
                </el-button>
              </el-form-item>
            </el-form>

            <el-alert
              title="支付说明"
              type="info"
              :closable="false"
              style="margin-top: 20px"
            >
              <template #default>
                <ul>
                  <li>支付成功后，余额将自动到账</li>
                  <li>支付过程中请勿关闭页面</li>
                  <li>如有问题请联系客服</li>
                </ul>
              </template>
            </el-alert>
          </div>
        </el-tab-pane>

        <!-- 卡密充值 -->
        <el-tab-pane label="卡密充值" name="card">
          <div class="card-recharge">
            <el-form
              :model="cardForm"
              label-width="100px"
              @submit.prevent="handleCardRecharge"
            >
              <el-form-item label="卡号">
                <el-input
                  v-model="cardForm.cardNo"
                  placeholder="请输入16位卡号"
                  maxlength="16"
                  clearable
                />
              </el-form-item>
              <el-form-item label="卡密">
                <el-input
                  v-model="cardForm.cardPassword"
                  placeholder="请输入8位卡密"
                  maxlength="8"
                  clearable
                />
              </el-form-item>
              <el-form-item>
                <el-button
                  type="primary"
                  @click="handleCardRecharge"
                  :loading="recharging"
                  :disabled="!cardForm.cardNo || !cardForm.cardPassword"
                >
                  立即充值
                </el-button>
              </el-form-item>
            </el-form>

            <el-alert
              title="充值说明"
              type="info"
              :closable="false"
              style="margin-top: 20px"
            >
              <template #default>
                <ul>
                  <li>卡号：16位数字</li>
                  <li>卡密：8位字符</li>
                  <li>充值成功后余额立即到账</li>
                  <li>如有问题请联系客服</li>
                </ul>
              </template>
            </el-alert>
          </div>
        </el-tab-pane>

        <!-- 代理充值 -->
        <el-tab-pane label="代理充值" name="agent">
          <div class="agent-recharge">
            <el-alert title="代理充值说明" type="warning" :closable="false">
              <template #default>
                <p>代理充值功能需要联系您的上级代理进行操作。</p>
                <p>请提供您的用户账号给上级代理，由代理为您充值。</p>
              </template>
            </el-alert>

            <div class="contact-info">
              <h3>联系方式</h3>
              <p>如需代理充值，请联系您的上级代理或客服人员。</p>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 充值记录 -->
    <el-card class="recharge-history">
      <template #header>
        <div class="card-header">
          <span>充值记录</span>
          <el-button @click="loadRechargeHistory">刷新</el-button>
        </div>
      </template>

      <el-table :data="rechargeHistory" v-loading="historyLoading" stripe>
        <el-table-column prop="amount" label="充值金额" width="120">
          <template #default="{ row }">
            <span class="amount">+¥{{ row.amount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="paymentMethod" label="充值方式" width="120">
          <template #default="{ row }">
            {{ row.paymentMethod || "卡密充值" }}
          </template>
        </el-table-column>
        <el-table-column prop="tradeNo" label="交易号" width="200">
          <template #default="{ row }">
            {{ row.tradeNo || "-" }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag type="success">已完成</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="充值时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { getUserInfo } from "../api/user";
import { createPayment, getPaymentOrders } from "../api/payment";

const activeTab = ref("alipay");
const userInfo = ref(null);
const recharging = ref(false);
const historyLoading = ref(false);

const rechargeHistory = ref([]);

const cardForm = ref({
  cardNo: "",
  cardPassword: "",
});

const alipayForm = ref({
  amount: null,
  customAmount: null,
  paymentType: "PC",
});

const presetAmounts = ref([10, 50, 100, 500, 1000]);
const minAmount = ref(10);
const isMobile = ref(false);

const loadUserInfo = async () => {
  try {
    const res = await getUserInfo();
    if (res.code === 1) {
      userInfo.value = res.data;
    }
  } catch (error) {
    console.error("获取用户信息失败：", error);
  }
};

const handleCardRecharge = async () => {
  if (!cardForm.value.cardNo || !cardForm.value.cardPassword) {
    ElMessage.warning("请输入卡号和卡密");
    return;
  }

  if (cardForm.value.cardNo.length !== 16) {
    ElMessage.warning("卡号必须为16位数字");
    return;
  }

  if (cardForm.value.cardPassword.length < 8 || cardForm.value.cardPassword.length > 64) {
    ElMessage.warning("卡密长度必须为8到64位字符");
    return;
  }

  recharging.value = true;
  try {
    const res = await rechargeByCard(cardForm.value);
    if (res.code === 1) {
      ElMessage.success(`充值成功！到账金额：¥${res.data}`);
      cardForm.value = {
        cardNo: "",
        cardPassword: "",
      };
      loadUserInfo();
      loadRechargeHistory();
    }
  } catch (error) {
    console.error("充值失败：", error);
  } finally {
    recharging.value = false;
  }
};

const loadRechargeHistory = async () => {
  historyLoading.value = true;
  try {
    // 调用支付订单接口获取充值记录
    const res = await getPaymentOrders({
      status: 'PAID', // 只显示已支付的充值记录
      pageNum: 1,
      pageSize: 10
    });

    if (res.code === 1 && res.data && res.data.records) {
      // 将payment_order数据转换为充值记录格式
      rechargeHistory.value = res.data.records.map(order => ({
        amount: order.amount,
        paymentMethod: order.paymentType === 'PC' ? 'PC网站支付' : '手机网站支付',
        tradeNo: order.alipayTradeNo || order.orderNo,
        status: order.status === 'PAID' ? 1 : 0,
        createTime: order.paidTime || order.createTime
      }));
    }
  } catch (error) {
    console.error("加载充值记录失败：", error);
  } finally {
    historyLoading.value = false;
  }
};

const handleTabChange = (tab) => {
  console.log("切换到充值方式：", tab);
};

const formatDateTime = (dateTime) => {
  if (!dateTime) return "-";
  return new Date(dateTime).toLocaleString("zh-CN");
};

const detectDevice = () => {
  const userAgent = navigator.userAgent.toLowerCase();
  isMobile.value = /mobile|android|iphone|ipad|phone/i.test(userAgent);
  alipayForm.value.paymentType = isMobile.value ? "WAP" : "PC";
};

const selectAmount = (preset) => {
  alipayForm.value.amount = preset;
  alipayForm.value.customAmount = null;
};

const selectCustomAmount = () => {
  alipayForm.value.amount = alipayForm.value.customAmount;
};

const handleAlipayRecharge = async () => {
  if (!alipayForm.value.amount || alipayForm.value.amount < minAmount.value) {
    ElMessage.warning(`充值金额不能小于${minAmount.value}元`);
    return;
  }

  recharging.value = true;

  try {
    const res = await createPayment({
      amount: alipayForm.value.amount,
      paymentType: alipayForm.value.paymentType,
      subject: "账户充值",
      body: `在线网课平台账户充值${alipayForm.value.amount}元`,
    });

    if (res.code === 1 && res.data.paymentForm) {
      // 创建隐藏的div并插入表单
      const div = document.createElement("div");
      div.innerHTML = res.data.paymentForm;
      document.body.appendChild(div);

      // 自动提交表单
      const form = div.querySelector("form");
      if (form) {
        form.submit();
      }
    } else {
      ElMessage.error(res.message || "创建支付订单失败");
    }
  } catch (error) {
    console.error("创建支付失败：", error);
    ElMessage.error(error.message || "创建支付失败");
  } finally {
    recharging.value = false;
  }
};

onMounted(() => {
  detectDevice();
  loadUserInfo();
  loadRechargeHistory();
});
</script>

<style scoped>
.recharge-page {
  padding: 20px;
}

.balance-card {
  margin-bottom: 20px;
  background: var(--primary-gradient) !important;
  color: var(--text-on-brand);
}

.balance-card :deep(.el-card__body) {
  color: inherit;
}

.balance-info {
  display: flex;
  justify-content: space-around;
}

.balance-item {
  text-align: center;
}

.balance-label {
  font-size: 14px;
  opacity: 0.8;
  margin-bottom: 8px;
}

.balance-value {
  font-size: 24px;
  font-weight: bold;
}

.recharge-methods {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-recharge {
  padding: 20px;
}

.agent-recharge {
  padding: 20px;
}

.contact-info {
  margin-top: 20px;
  padding: 15px;
  background-color: var(--bg-body);
  border-radius: 8px;
}

.contact-info h3 {
  margin-top: 0;
  color: var(--text-primary);
}

.recharge-history {
  box-shadow: var(--shadow-sm);
}

.amount {
  color: var(--color-success);
  font-weight: bold;
}

:deep(.el-tabs__content) {
  padding: 0;
}

:deep(.el-form-item) {
  margin-bottom: 20px;
}

:deep(.el-input) {
  width: 300px;
}

.alipay-recharge {
  padding: 20px;
}

.amount-options {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 10px;
}

.amount-option {
  padding: 15px;
  border: 2px solid var(--border-color);
  border-radius: 8px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  background: var(--bg-card);
}

.amount-option:hover {
  border-color: var(--brand-primary);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px color-mix(in srgb, var(--brand-primary) 15%, transparent);
}

.amount-option.active {
  border-color: var(--brand-primary);
  background: color-mix(in srgb, var(--brand-primary) 12%, var(--surface-solid));
}

.amount-value {
  display: block;
  font-size: 24px;
  font-weight: bold;
  color: var(--brand-primary);
}

.amount-unit {
  font-size: 12px;
  color: var(--text-secondary);
}

.tip {
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.total-amount {
  font-size: 24px;
  font-weight: bold;
  color: var(--color-danger);
}

/* Dark Mode Overrides */
html.dark .contact-info {
  background-color: color-mix(in srgb, var(--text-primary) 5%, transparent);
}

html.dark .amount-option {
  background-color: color-mix(in srgb, var(--text-primary) 5%, transparent);
  border-color: var(--border-color-light);
}

html.dark .amount-option.active {
  background-color: color-mix(in srgb, var(--brand-primary) 10%, transparent);
}

@media (max-width: 768px) {
  .amount-options {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
