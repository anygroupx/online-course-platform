<template>
  <el-table :data="logs" v-loading="loading" class="logs-table" stripe>
    <el-table-column prop="id" label="ID" width="80" />
    <el-table-column prop="operationType" label="操作类型" width="120">
      <template #default="{ row }">
        <el-tag :type="getOperationTypeTagType(row.operationType)">
          {{ row.operationType || "-" }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column
      prop="operationDesc"
      label="操作描述"
      show-overflow-tooltip
    />
    <el-table-column prop="amountChange" label="金额变动" width="128">
      <template #default="{ row }">
        <span
          class="amount-change"
          :class="getAmountChangeClass(row.amountChange)"
        >
          {{ formatAmountChange(row.amountChange) }}
        </span>
      </template>
    </el-table-column>
    <el-table-column prop="balanceAfter" label="操作后余额" width="128">
      <template #default="{ row }">
        {{ formatBalanceAfter(row.balanceAfter) }}
      </template>
    </el-table-column>
    <el-table-column prop="ipAddress" label="IP地址" width="140" />
    <el-table-column prop="createTime" label="操作时间" width="176">
      <template #default="{ row }">
        {{ formatLogDateTime(row.createTime) }}
      </template>
    </el-table-column>
  </el-table>
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
.logs-table {
  width: 100%;
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
</style>
