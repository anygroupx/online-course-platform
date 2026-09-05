<template>
  <div class="logs-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <h1>操作日志</h1>
          <el-button v-if="isAdmin" type="warning" @click="syncToES" :loading="syncing">同步历史数据</el-button>
        </div>
      </template>
      <LogFilterToolbar v-model:date-range="dateRange" v-model:keyword="keyword" v-model:operation-type="operationType" :loading="loading" @search="handleSearch" />
      <LogMobileList v-if="isMobile" :logs="tableData" :loading="loading" />
      <LogDesktopTable v-else :logs="tableData" :loading="loading" />
      <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :total="total" :pager-count="isMobile ? 5 : 7" layout="total, sizes, prev, pager, next" class="pagination" @current-change="loadLogs" @size-change="handleSearch" />
    </el-card>
  </div>
</template>
<script setup>
import { ref, onMounted, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { queryLogs, syncLogsToES } from '@/api/log';
import { useUserStore } from '@/stores/user';
import { useResponsive } from '@/composables/useResponsive';
import LogFilterToolbar from '@/components/logs/LogFilterToolbar.vue';
import LogMobileList from '@/components/logs/LogMobileList.vue';
import LogDesktopTable from '@/components/logs/LogDesktopTable.vue';
const { isMobile } = useResponsive();
const tableData = ref([]), currentPage = ref(1), pageSize = ref(20), total = ref(0);
const operationType = ref(''), keyword = ref(''), dateRange = ref(null), syncing = ref(false), loading = ref(false);
const userStore = useUserStore();
const isAdmin = computed(() => userStore.isAdmin);
let sequence = 0;
const loadLogs = async () => {
  const request = ++sequence;
  loading.value = true;
  try {
    const params = { operationType: operationType.value || undefined, keyword: keyword.value || undefined, page: currentPage.value, pageSize: pageSize.value };
    if (dateRange.value?.length === 2) [params.startTime, params.endTime] = dateRange.value;
    const res = await queryLogs(params);
    if (request !== sequence) return;
    tableData.value = res.data?.records || [];
    total.value = res.data?.total || 0;
  } catch {
    if (request === sequence) { tableData.value = []; total.value = 0; }
  } finally { if (request === sequence) loading.value = false; }
};
const handleSearch = () => { currentPage.value = 1; loadLogs(); };
const syncToES = async () => {
  syncing.value = true;
  try { const res = await syncLogsToES(); ElMessage.success(res.message || '同步成功'); }
  catch (error) { ElMessage.error(error.message || '同步失败'); }
  finally { syncing.value = false; }
};
onMounted(loadLogs);
</script>
<style scoped>
.logs-page { padding: 20px; }
h1 { font-size: 18px; }
.card-header { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 12px; }
.log-filter-toolbar { margin-bottom: 18px; }
.pagination { margin-top: 20px; justify-content: flex-end; }
@media (max-width: 767px) { .logs-page { padding: 0; } .pagination { justify-content: center; } }
</style>
