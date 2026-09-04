<template>
  <div class="payment-callback-container">
    <el-card class="result-card" v-loading="loading">
      <div class="result-content" v-if="!loading">
        <!-- 支付成功 -->
        <div v-if="success" class="success-result">
          <div class="icon-wrapper">
            <el-icon :size="80" color="var(--color-success)">
              <SuccessFilled />
            </el-icon>
          </div>
          <h2>支付成功！</h2>
          <div class="order-info">
            <p>订单号：{{ orderNo }}</p>
            <p class="amount">充值金额：¥{{ amount }}</p>
          </div>
          <div class="actions">
            <el-button type="primary" @click="goToOrders">查看订单</el-button>
            <el-button @click="continueRecharge">继续充值</el-button>
          </div>
        </div>

        <!-- 支付失败 -->
        <div v-else-if="failed" class="failed-result">
          <div class="icon-wrapper">
            <el-icon :size="80" color="var(--color-danger)">
              <CircleCloseFilled />
            </el-icon>
          </div>
          <h2>支付失败</h2>
          <div class="error-info">
            <p>{{ errorMessage || '支付未完成，请重试' }}</p>
          </div>
          <div class="actions">
            <el-button type="primary" @click="continueRecharge">重新充值</el-button>
            <el-button @click="goToHome">返回首页</el-button>
          </div>
        </div>

        <!-- 处理中 -->
        <div v-else class="pending-result">
          <div class="icon-wrapper">
            <el-icon :size="80" color="var(--color-warning)">
              <WarningFilled />
            </el-icon>
          </div>
          <h2>支付处理中...</h2>
          <div class="pending-info">
            <p>您的支付正在处理中，请稍候</p>
            <p class="tip">如长时间未到账，请联系客服</p>
          </div>
          <div class="actions">
            <el-button type="primary" @click="queryOrderStatus">刷新状态</el-button>
            <el-button @click="goToOrders">查看订单</el-button>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { SuccessFilled, CircleCloseFilled, WarningFilled } from '@element-plus/icons-vue'
import { syncPaymentOrder } from '@/api/payment'

export default {
  name: 'PaymentCallback',
  components: {
    SuccessFilled,
    CircleCloseFilled,
    WarningFilled
  },
  setup() {
    const route = useRoute()
    const router = useRouter()

    const loading = ref(true)
    const success = ref(false)
    const failed = ref(false)
    const orderNo = ref('')
    const amount = ref(0)
    const errorMessage = ref('')

    const queryOrderStatus = async () => {
      loading.value = true
      try {
        const outTradeNo = route.query.out_trade_no || route.query.orderNo

        if (!outTradeNo) {
          failed.value = true
          errorMessage.value = '订单号参数缺失'
          return
        }

        orderNo.value = outTradeNo

        // 改用同步查询接口，会主动查询支付宝状态
        const res = await syncPaymentOrder(outTradeNo)

        if (res.code === 1 && res.data) {
          const order = res.data
          amount.value = order.amount

          if (order.status === 'PAID') {
            success.value = true
          } else if (order.status === 'CLOSED' || order.status === 'REFUNDED') {
            failed.value = true
            errorMessage.value = order.status === 'CLOSED' ? '订单已关闭' : '订单已退款'
          } else {
            // PENDING 或其他状态
            success.value = false
            failed.value = false
          }
        } else {
          failed.value = true
          errorMessage.value = res.message || '查询订单失败'
        }
      } catch (error) {
        console.error('查询订单失败：', error)
        failed.value = true
        errorMessage.value = error.message || '查询订单异常'
      } finally {
        loading.value = false
      }
    }

    const goToOrders = () => {
      router.push('/payment/orders')
    }

    const continueRecharge = () => {
      router.push('/recharge')
    }

    const goToHome = () => {
      router.push('/')
    }

    onMounted(() => {
      queryOrderStatus()
    })

    return {
      loading,
      success,
      failed,
      orderNo,
      amount,
      errorMessage,
      queryOrderStatus,
      goToOrders,
      continueRecharge,
      goToHome
    }
  }
}
</script>

<style scoped>
.payment-callback-container {
  min-height: calc(100vh - 160px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
}

.result-card {
  max-width: 600px;
  width: 100%;
}

.result-content {
  text-align: center;
  padding: 40px 20px;
}

.icon-wrapper {
  margin-bottom: 24px;
}

h2 {
  font-size: 28px;
  margin: 0 0 24px 0;
  color: var(--text-primary);
}

.order-info,
.error-info,
.pending-info {
  margin: 24px 0;
  padding: 20px;
  background: color-mix(in srgb, var(--brand-primary) 4%, var(--surface-solid));
  border-radius: 8px;
}

.order-info p,
.error-info p,
.pending-info p {
  margin: 8px 0;
  font-size: 15px;
  color: var(--text-regular);
}

.amount {
  font-size: 24px;
  font-weight: bold;
  color: var(--color-success) !important;
  margin-top: 12px !important;
}

.tip {
  font-size: 13px;
  color: var(--text-placeholder) !important;
}

.actions {
  margin-top: 32px;
  display: flex;
  gap: 12px;
  justify-content: center;
}

.actions .el-button {
  min-width: 120px;
}

@media (max-width: 768px) {
  .actions {
    flex-direction: column;
  }

  .actions .el-button {
    width: 100%;
  }
}
</style>
