<template>
  <div v-loading="loading" class="log-mobile-list">
    <article
      v-for="log in logs"
      :key="log.id || `${log.createTime}-${log.operationDesc}`"
      class="log-mobile-item"
    >
      <header class="log-mobile-header">
        <div>
          <span>{{ formatLogDateTime(log.createTime) }}</span>
          <strong>{{ log.operationDesc || "-" }}</strong>
        </div>
        <el-tag :type="getOperationTypeTagType(log.operationType)">
          {{ log.operationType || "操作" }}
        </el-tag>
      </header>

      <dl class="log-mobile-fields">
        <div>
          <dt>金额变动</dt>
          <dd
            class="amount-change"
            :class="getAmountChangeClass(log.amountChange)"
          >
            {{ formatAmountChange(log.amountChange) }}
          </dd>
        </div>
        <div>
          <dt>操作后余额</dt>
          <dd>{{ formatBalanceAfter(log.balanceAfter) }}</dd>
        </div>
        <div>
          <dt>IP地址</dt>
          <dd>{{ log.ipAddress || "-" }}</dd>
        </div>
      </dl>
    </article>

    <el-empty v-if="!loading && logs.length === 0" description="暂无操作日志" />
  </div>
</template>

<script setup>
import {
  formatAmountChange,
  formatBalanceAfter,
  formatLogDateTime,
  getAmountChangeClass,
  getOperationTypeTagType,
} from "@/utils/logDisplay";

defineProps({
  logs: {
    type: Array,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
});
</script>

<style scoped>
.log-mobile-list {
  display: grid;
  gap: 12px;
  min-height: 180px;
}

.log-mobile-item {
  display: grid;
  gap: 12px;
  padding: 16px;
  background: var(--surface-solid);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.log-mobile-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.log-mobile-header > div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.log-mobile-header span,
.log-mobile-fields dt {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.log-mobile-header strong {
  overflow-wrap: anywhere;
  color: var(--text-primary);
  font-size: 14px;
  line-height: 1.5;
}

.log-mobile-fields {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
}

.log-mobile-fields div {
  min-width: 0;
}

.log-mobile-fields dd {
  margin: 4px 0 0;
  overflow-wrap: anywhere;
  color: var(--text-regular);
  font-size: 13px;
  line-height: 1.55;
}

.amount-change {
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.amount-change.is-positive {
  color: var(--color-success);
}

.amount-change.is-negative {
  color: var(--color-danger);
}

.amount-change.is-neutral {
  color: var(--text-secondary);
}

@media (max-width: 560px) {
  .log-mobile-header {
    display: grid;
  }

  .log-mobile-fields {
    grid-template-columns: 1fr;
  }
}
</style>
