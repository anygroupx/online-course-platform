<template>
  <div class="project-management-page">
    <el-card class="overview-card">
      <div class="overview-content">
        <div>
          <div class="overview-title">项目管理</div>
          <p>查看当前账户可用的项目、实际价格和项目说明。</p>
        </div>
        <el-tag :type="userLevelType" size="large" effect="dark">
          当前等级：{{ userLevelLabel }}
        </el-tag>
      </div>
    </el-card>

    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">可用项目</div>
            <div class="result-count">共 {{ filteredTableData.length }} 个项目</div>
          </div>
          <el-input
            v-model="searchKeyword"
            class="project-search"
            clearable
            :prefix-icon="Search"
            placeholder="搜索项目名称、分类或说明"
            aria-label="搜索项目"
          />
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="filteredTableData"
        style="width: 100%"
        stripe
      >
        <el-table-column label="项目名称" min-width="190">
          <template #default="scope">
            <div class="project-name">
              <el-icon><Reading /></el-icon>
              <span>{{ projectName(scope.row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分类" min-width="130">
          <template #default="scope">
            {{ scope.row.categoryName || "未分类" }}
          </template>
        </el-table-column>
        <el-table-column label="我的价格" width="150" align="center">
          <template #default="scope">
            <span class="my-price">¥{{ calculateUserPrice(scope.row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="项目说明" min-width="280" show-overflow-tooltip>
          <template #default="scope">
            <span class="description-text">{{ scope.row.description || "暂无说明" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
              {{ scope.row.status === 1 ? "可用" : "停用" }}
            </el-tag>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty
            :description="searchKeyword.trim() ? '没有找到匹配的项目' : '暂无可用项目'"
          />
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { Reading, Search } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { getCoursePlatforms } from "@/api/course";
import { getUserInfo } from "@/api/user";
import {
  getUserLevelLabel,
  getUserLevelTagType,
} from "@/utils/userLevel";
import {
  formatUserProjectPrice,
  getProjectDisplayName,
  matchesProjectSearch,
} from "@/utils/projectPricing";

const tableData = ref([]);
const userRate = ref(1);
const searchKeyword = ref("");
const loading = ref(false);

const userLevelLabel = computed(() => getUserLevelLabel(userRate.value));
const userLevelType = computed(() => getUserLevelTagType(userRate.value));

const projectName = getProjectDisplayName;

const filteredTableData = computed(() =>
  tableData.value.filter((platform) =>
    matchesProjectSearch(platform, searchKeyword.value)
  )
);

const calculateUserPrice = (platform) =>
  formatUserProjectPrice(platform, userRate.value);

const loadData = async () => {
  loading.value = true;
  try {
    const [userRes, platformRes] = await Promise.all([
      getUserInfo(),
      getCoursePlatforms(),
    ]);

    if (userRes.code === 1) {
      userRate.value = Number(userRes.data?.rate || 1);
    }
    if (platformRes.code === 1) {
      tableData.value = Array.isArray(platformRes.data) ? platformRes.data : [];
    }
  } catch (error) {
    console.error("加载项目失败：", error);
    ElMessage.error("加载项目失败，请稍后重试");
  } finally {
    loading.value = false;
  }
};

onMounted(loadData);
</script>

<style scoped>
.project-management-page {
  padding: 20px;
}

.overview-card {
  margin-bottom: 20px;
  border: none;
  color: var(--text-on-brand);
  background: var(--primary-gradient) !important;
}

.overview-card :deep(.el-card__body) {
  color: inherit;
}

.overview-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 8px;
}

.overview-title {
  margin-bottom: 8px;
  font-size: 22px;
  font-weight: 700;
}

.overview-content p {
  margin: 0;
  font-size: 14px;
  opacity: 0.9;
}

.table-card {
  box-shadow: var(--shadow-sm);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.card-title {
  font-size: 17px;
  font-weight: 700;
}

.result-count {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 13px;
}

.project-search {
  width: min(380px, 100%);
}

.project-name {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-primary);
  font-weight: 600;
}

.project-name .el-icon {
  color: var(--brand-primary);
}

.my-price {
  color: var(--color-danger);
  font-size: 16px;
  font-weight: 700;
}

.description-text {
  color: var(--text-secondary);
}

@media (max-width: 768px) {
  .project-management-page {
    padding: 12px;
  }

  .overview-content,
  .card-header {
    align-items: stretch;
    flex-direction: column;
  }

  .overview-content .el-tag {
    align-self: flex-start;
  }

  .project-search {
    width: 100%;
  }
}
</style>
