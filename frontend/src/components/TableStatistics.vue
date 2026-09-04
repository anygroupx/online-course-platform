<template>
  <el-row :gutter="isMobile ? 12 : 20" class="table-statistics">
    <el-col
      v-for="(stat, index) in statistics"
      :key="stat.key || index"
      :xs="12"
      :sm="12"
      :md="6"
      :lg="6"
    >
      <el-card class="stat-card" shadow="hover">
        <div class="stat-content">
          <div class="stat-value" :style="{ color: stat.color }">
            {{ formatValue(stat.value) }}
          </div>
          <div class="stat-label">{{ stat.label }}</div>
          <div v-if="stat.extra" class="stat-extra">
            {{ stat.extra }}
          </div>
        </div>
        <div class="stat-icon" :class="stat.iconClass">
          <el-icon :size="40">
            <component :is="stat.icon || Document" />
          </el-icon>
        </div>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup>
import { computed } from "vue";
import { Document } from "@element-plus/icons-vue";
import { useResponsive } from "@/composables/useResponsive";

// Props
const props = defineProps({
  // 统计数据配置
  statistics: {
    type: Array,
    required: true,
    default: () => [],
    validator: (value) => {
      return value.every(
        (stat) => stat.label !== undefined && stat.value !== undefined
      );
    },
  },
  // 数值格式化选项
  formatOptions: {
    type: Object,
    default: () => ({
      decimals: 0,
      thousandsSeparator: true,
      prefix: "",
      suffix: "",
    }),
  },
});

// 响应式
const { isMobile } = useResponsive();

// 格式化数值
const formatValue = (value) => {
  const { decimals, thousandsSeparator, prefix, suffix } = props.formatOptions;

  if (value === null || value === undefined) return "-";

  let formatted = Number(value).toFixed(decimals);

  if (thousandsSeparator) {
    formatted = formatted.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  }

  return `${prefix}${formatted}${suffix}`;
};
</script>

<style scoped>
.table-statistics {
  margin-bottom: 20px;
}

.stat-card {
  position: relative;
  overflow: hidden;
  min-height: 100px;
  cursor: default;
  transition: all 0.3s;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.1);
}

.stat-content {
  position: relative;
  z-index: 2;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: var(--text-primary);
  margin-bottom: 8px;
  line-height: 1.2;
}

.stat-label {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.stat-extra {
  font-size: 12px;
  color: var(--text-placeholder);
  margin-top: 4px;
}

.stat-icon {
  position: absolute;
  right: 20px;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0.15;
  transition: all 0.3s;
  z-index: 1;
}

.stat-card:hover .stat-icon {
  opacity: 0.25;
  transform: translateY(-50%) scale(1.1);
}

.stat-icon.primary {
  color: var(--brand-primary);
}

.stat-icon.success {
  color: var(--color-success);
}

.stat-icon.warning {
  color: var(--color-warning);
}

.stat-icon.danger {
  color: var(--color-danger);
}

.stat-icon.info {
  color: var(--color-info);
}

/* 移动端优化 */
@media (max-width: 767px) {
  .stat-card {
    min-height: 90px;
  }

  .stat-value {
    font-size: 24px;
  }

  .stat-label {
    font-size: 12px;
  }

  .stat-icon {
    right: 15px;
  }

  .stat-icon :deep(.el-icon) {
    font-size: 32px;
  }
}

@container (max-width: 420px) {
  .table-statistics :deep(.el-col) {
    flex: 0 0 100%;
    max-width: 100%;
  }

  .stat-card {
    min-height: 84px;
  }
}
</style>
