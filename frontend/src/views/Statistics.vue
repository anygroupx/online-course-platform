<template>
  <div class="statistics-page">
    <el-row :gutter="20">
      <!-- 订单统计 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card purple-card">
          <div class="stat-icon">
            <el-icon><Document /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">总订单数</div>
            <div class="stat-value">{{ stats?.totalOrders || 0 }}</div>
            <div class="stat-trend">今日: +{{ stats?.todayOrders || 0 }}</div>
          </div>
        </el-card>
      </el-col>

      <!-- 用户统计 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card blue-card">
          <div class="stat-icon">
            <el-icon><User /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">总用户数</div>
            <div class="stat-value">{{ stats?.totalUsers || 0 }}</div>
            <div class="stat-trend">今日: +{{ stats?.todayNewUsers || 0 }}</div>
          </div>
        </el-card>
      </el-col>

      <!-- 交易额统计 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card green-card">
          <div class="stat-icon">
            <el-icon><Money /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">累计销售</div>
            <div class="stat-value">¥{{ stats?.totalAmount || 0 }}</div>
            <div class="stat-trend">今日: ¥{{ stats?.todayAmount || 0 }}</div>
          </div>
        </el-card>
      </el-col>

      <!-- 待处理订单 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card orange-card">
          <div class="stat-icon">
            <el-icon><Clock /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">待处理</div>
            <div class="stat-value">{{ stats?.pendingOrders || 0 }}</div>
            <div class="stat-trend">进行中: {{ stats?.processingOrders || 0 }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Document, User, Money, Clock } from '@element-plus/icons-vue'
import { getStatistics } from '@/api/statistics'

const stats = ref(null)

const loadStats = async () => {
  try {
    const res = await getStatistics()
    if (res.code === 1) {
      stats.value = res.data
    }
  } catch (error) {
    console.error('加载统计数据失败：', error)
  }
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped>
.statistics-page {
  padding: 20px;
}

.stat-card {
  position: relative;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s;
  border: none;
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 100px;
  height: 100px;
  border-radius: 50%;
  opacity: 0.1;
  transition: all 0.3s;
}

.stat-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.15);
}

.stat-card:hover::before {
  transform: scale(2);
}

.purple-card::before {
  background: var(--color-primary);
}

.blue-card::before {
  background: #4facfe;
}

.green-card::before {
  background: #43e97b;
}

.orange-card::before {
  background: #f093fb;
}

:deep(.el-card__body) {
  padding: 24px;
}

.stat-icon {
  font-size: 48px;
  margin-bottom: 16px;
  display: inline-block;
}

.purple-card .stat-icon {
  color: var(--color-primary);
}

.blue-card .stat-icon {
  color: #4facfe;
}

.green-card .stat-icon {
  color: #43e97b;
}

.orange-card .stat-icon {
  color: #f093fb;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 8px;
}

.stat-trend {
  font-size: 13px;
  color: var(--color-success);
}
</style>
