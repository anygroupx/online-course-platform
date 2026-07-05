<template>
  <div class="price-list-page">
    <el-card class="info-card">
      <div class="price-info">
        <el-icon class="info-icon"><InfoFilled /></el-icon>
        <div class="info-text">
          <h3>价格计算说明</h3>
          <p>我的实际价格 = 平台基础价格 × 我的费率（{{ userRate }}）</p>
        </div>
      </div>
    </el-card>

    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>价格列表</span>
          <el-tag type="success">我的费率：{{ userRate }}</el-tag>
        </div>
      </template>

      <el-table :data="tableData" style="width: 100%" stripe>
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="name" label="平台名称" width="150">
          <template #default="scope">
            <div class="platform-name">
              <el-icon><Reading /></el-icon>
              <span>{{ scope.row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="basePrice" label="基础价格" width="100" align="center">
          <template #default="scope">
            <el-tag type="warning">{{ scope.row.basePrice }}元</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="不同费率价格对比" min-width="400">
          <template #default="scope">
            <div class="price-compare">
              <div class="price-item" v-for="rate in [0.2, 0.3, 0.4, 0.5, 0.6]" :key="rate">
                <span class="rate-label">{{ rate }}倍</span>
                <span class="rate-price">{{ calculatePrice(scope.row.basePrice, rate) }}元</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="我的价格" width="120" align="center" fixed="right">
          <template #default="scope">
            <div class="my-price">
              <el-tag type="danger" size="large" effect="dark">
                {{ calculatePrice(scope.row.basePrice, userRate) }}元
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
              {{ scope.row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { InfoFilled, Reading } from '@element-plus/icons-vue'
import { getCoursePlatforms } from '@/api/course'
import { getUserInfo } from '@/api/user'

const tableData = ref([])
const userRate = ref(1.0)

const loadData = async () => {
  try {
    // 获取用户费率
    const userRes = await getUserInfo()
    if (userRes.code === 1) {
      userRate.value = userRes.data.rate
    }

    // 获取课程平台列表
    const platformRes = await getCoursePlatforms()
    if (platformRes.code === 1) {
      tableData.value = platformRes.data
    }
  } catch (error) {
    console.error('加载数据失败：', error)
  }
}

const calculatePrice = (basePrice, rate) => {
  return (basePrice * rate).toFixed(2)
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.price-list-page {
  padding: 20px;
}

.info-card {
  margin-bottom: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
}

.price-info {
  display: flex;
  align-items: center;
  padding: 10px;
}

.info-icon {
  font-size: 40px;
  margin-right: 20px;
}

.info-text h3 {
  margin: 0 0 8px 0;
  font-size: 18px;
}

.info-text p {
  margin: 0;
  font-size: 14px;
  opacity: 0.9;
}

.table-card {
  box-shadow: var(--shadow-sm);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 16px;
  font-weight: bold;
}

.platform-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.price-compare {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.price-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 12px;
  background: var(--bg-body);
  border-radius: 6px;
  transition: all 0.3s;
}

.price-item:hover {
  background: var(--bg-card-hover);
  transform: translateY(-2px);
}

.rate-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.rate-price {
  font-size: 14px;
  font-weight: bold;
  color: var(--color-primary);
}

.my-price {
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.05);
  }
}

/* Dark Mode Overrides */
html.dark .price-item {
  background: rgba(255, 255, 255, 0.05);
}

html.dark .price-item:hover {
  background: rgba(255, 255, 255, 0.1);
}
</style>

