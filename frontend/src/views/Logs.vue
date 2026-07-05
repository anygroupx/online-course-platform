<template>
  <div class="logs-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>操作日志</span>
          <div class="header-actions">
            <!-- 时间范围选择器 -->
            <el-date-picker
              v-model="dateRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 340px; margin-right: 10px"
              value-format="YYYY-MM-DD HH:mm:ss"
              clearable
              @change="loadLogs"
            />
            <el-input
              v-model="keyword"
              placeholder="搜索日志内容"
              style="width: 180px; margin-right: 10px"
              clearable
              @keyup.enter="loadLogs"
              @clear="loadLogs"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-select
              v-model="operationType"
              placeholder="操作类型"
              style="width: 130px; margin-right: 10px"
              clearable
              @change="loadLogs"
            >
              <el-option label="全部" value="" />
              <el-option label="登录" value="登录" />
              <el-option label="登出" value="登出" />
              <el-option label="开户" value="开户" />
              <el-option label="充值" value="充值" />
              <el-option label="创建订单" value="创建订单" />
              <el-option label="补单" value="补单" />
              <el-option label="查课" value="查课" />
            </el-select>
            <el-button type="primary" @click="loadLogs" :icon="Search">搜索</el-button>
            <el-button 
              v-if="isAdmin" 
              type="warning" 
              @click="syncToES" 
              :loading="syncing"
            >
              同步历史数据
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="tableData" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="operationType" label="操作类型" width="120">
          <template #default="scope">
            <el-tag>{{ scope.row.operationType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="operationDesc"
          label="操作描述"
          show-overflow-tooltip
        />
        <el-table-column prop="amountChange" label="金额变动" width="120">
          <template #default="scope">
            <span
              :style="{
                color:
                  scope.row.amountChange >= 0
                    ? 'var(--color-success)'
                    : 'var(--color-danger)',
              }"
            >
              {{ scope.row.amountChange >= 0 ? "+" : ""
              }}{{ scope.row.amountChange }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="balanceAfter" label="操作后余额" width="120">
          <template #default="scope">
            <span v-if="scope.row.balanceAfter"
              >¥{{ scope.row.balanceAfter }}</span
            >
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="IP地址" width="140" />
        <el-table-column prop="createTime" label="操作时间" width="160" />
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next"
        class="pagination"
        @current-change="loadLogs"
        @size-change="loadLogs"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from "vue";
import { Search } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { queryLogs, syncLogsToES } from "@/api/log";
import { useUserStore } from "@/stores/user";

const tableData = ref([]);
const currentPage = ref(1);
const pageSize = ref(20);
const total = ref(0);
const operationType = ref("");
const keyword = ref("");
const dateRange = ref(null);
const syncing = ref(false);

// 检查是否为管理员
const userStore = useUserStore();
const isAdmin = computed(() => userStore.isAdmin);

const loadLogs = async () => {
  try {
    const params = {
      operationType: operationType.value || undefined,
      keyword: keyword.value || undefined,
      page: currentPage.value,
      pageSize: pageSize.value,
    };
    
    // 添加时间范围参数
    if (dateRange.value && dateRange.value.length === 2) {
      params.startTime = dateRange.value[0];
      params.endTime = dateRange.value[1];
    }
    
    const res = await queryLogs(params);
    if (res.code === 1) {
      if (res.data && res.data.records) {
        tableData.value = res.data.records;
        total.value = res.data.total || 0;
      } else {
        tableData.value = [];
        total.value = 0;
      }
    }
  } catch (error) {
    console.error("加载日志失败：", error);
    tableData.value = [];
    total.value = 0;
  }
};

// 同步历史数据到ES
const syncToES = async () => {
  syncing.value = true;
  try {
    const res = await syncLogsToES();
    if (res.code === 1) {
      ElMessage.success(res.msg || "同步成功");
    } else {
      ElMessage.error(res.msg || "同步失败");
    }
  } catch (error) {
    ElMessage.error("同步失败：" + error.message);
  } finally {
    syncing.value = false;
  }
};

onMounted(() => {
  loadLogs();
});
</script>

<style scoped>
.logs-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
