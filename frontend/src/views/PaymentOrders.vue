<template>
  <div class="payment-orders-page">
    <h1>支付订单</h1>

    <!-- 搜索区域 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="订单状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 150px">
            <el-option label="全部" value=""></el-option>
            <el-option label="待支付" value="PENDING"></el-option>
            <el-option label="已支付" value="PAID"></el-option>
            <el-option label="已关闭" value="CLOSED"></el-option>
            <el-option label="已退款" value="REFUNDED"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 订单列表 -->
    <el-card class="table-card">
      <el-table :data="orderList" v-loading="loading" stripe>
        <el-table-column prop="orderNo" label="订单号" width="200" />
        <el-table-column prop="amount" label="金额" width="120">
          <template #default="{ row }">
            <span class="amount">¥{{ row.amount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="paymentType" label="支付方式" width="120">
          <template #default="{ row }">
            {{ row.paymentType === 'PC' ? 'PC网站支付' : '手机网站支付' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="alipayTradeNo" label="支付宝交易号" min-width="180">
          <template #default="{ row }">
            {{ row.alipayTradeNo || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="paidTime" label="支付时间" width="180">
          <template #default="{ row }">
            {{ row.paidTime ? formatDateTime(row.paidTime) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PAID'"
              type="danger"
              size="small"
              @click="handleRefund(row)"
            >
              申请退款
            </el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="handleSearch"
        />
      </div>
    </el-card>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPaymentOrders, refundPayment } from '@/api/payment'

export default {
  name: 'PaymentOrders',
  setup() {
    const loading = ref(false)
    const orderList = ref([])
    const total = ref(0)

    const queryParams = ref({
      status: '',
      pageNum: 1,
      pageSize: 10
    })

    const loadOrders = async () => {
      loading.value = true
      try {
        const res = await getPaymentOrders(queryParams.value)
        
        if (res.code === 1 && res.data) {
          orderList.value = res.data.records
          total.value = res.data.total
        }
      } catch (error) {
        console.error('加载订单失败：', error)
        ElMessage.error('加载订单失败')
      } finally {
        loading.value = false
      }
    }

    const handleSearch = () => {
      queryParams.value.pageNum = 1
      loadOrders()
    }

    const handleReset = () => {
      queryParams.value = {
        status: '',
        pageNum: 1,
        pageSize: 10
      }
      loadOrders()
    }

    const handleRefund = async (row) => {
      try {
        await ElMessageBox.prompt('请输入退款原因', '申请退款', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          inputPattern: /.+/,
          inputErrorMessage: '请输入退款原因'
        }).then(async ({ value }) => {
          const res = await refundPayment(row.orderNo, value)
          
          if (res.code === 1) {
            ElMessage.success('退款申请提交成功')
            loadOrders()
          } else {
            ElMessage.error(res.message || '退款申请失败')
          }
        })
      } catch (error) {
        if (error !== 'cancel') {
          console.error('退款失败：', error)
        }
      }
    }

    const getStatusType = (status) => {
      const types = {
        'PENDING': 'warning',
        'PAID': 'success',
        'CLOSED': 'info',
        'REFUNDED': 'danger'
      }
      return types[status] || ''
    }

    const getStatusLabel = (status) => {
      const labels = {
        'PENDING': '待支付',
        'PAID': '已支付',
        'CLOSED': '已关闭',
        'REFUNDING': '退款中',
        'REFUNDED': '已退款'
      }
      return labels[status] || status
    }

    const formatDateTime = (dateTime) => {
      if (!dateTime) return '-'
      return new Date(dateTime).toLocaleString('zh-CN')
    }

    onMounted(() => {
      loadOrders()
    })

    return {
      loading,
      orderList,
      total,
      queryParams,
      loadOrders,
      handleSearch,
      handleReset,
      handleRefund,
      getStatusType,
      getStatusLabel,
      formatDateTime
    }
  }
}
</script>

<style scoped>
.payment-orders-page {
  padding: 20px;
}

.search-card {
  margin-bottom: 20px;
}

.table-card {
  box-shadow: var(--shadow-sm);
}

.amount {
  color: var(--color-success);
  font-weight: bold;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
