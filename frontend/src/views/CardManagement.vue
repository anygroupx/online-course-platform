<template>
  <div class="card-management">
    <h1>充值卡密管理</h1>

    <!-- 操作栏 -->
    <el-card class="operation-card">
      <div class="operation-bar">
        <el-button type="primary" @click="handleGenerate">
          <el-icon><Plus /></el-icon>
          生成卡密
        </el-button>
        <div class="search-bar">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索卡号"
            style="width: 200px"
            @keyup.enter="loadCards"
          >
            <template #append>
              <el-button @click="loadCards">
                <el-icon><Search /></el-icon>
              </el-button>
            </template>
          </el-input>
          <el-select
            v-model="statusFilter"
            placeholder="状态筛选"
            style="width: 120px; margin-left: 10px"
            @change="loadCards"
          >
            <el-option label="全部" value="" />
            <el-option label="未使用" :value="0" />
            <el-option label="已使用" :value="1" />
            <el-option label="已禁用" :value="2" />
          </el-select>
        </div>
      </div>
    </el-card>

    <!-- 卡密列表 -->
    <el-card class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="cardNo" label="卡号" width="200" />
        <el-table-column prop="cardPassword" label="卡密" width="120" />
        <el-table-column prop="amount" label="面额" width="100">
          <template #default="{ row }">
            <span class="amount">¥{{ row.amount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="usedBy" label="使用者" width="120">
          <template #default="{ row }">
            <span v-if="row.usedBy">{{ row.usedBy }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="usedTime" label="使用时间" width="180">
          <template #default="{ row }">
            <span v-if="row.usedTime">{{ formatDateTime(row.usedTime) }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              type="danger"
              size="small"
              @click="handleDisable(row)"
            >
              禁用
            </el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadCards"
        @current-change="loadCards"
      />
    </el-card>

    <!-- 生成卡密对话框 -->
    <el-dialog
      v-model="generateDialogVisible"
      title="生成充值卡密"
      width="400px"
      append-to-body
    >
      <el-form :model="generateForm" label-width="100px">
        <el-form-item label="卡密数量">
          <el-input-number v-model="generateForm.count" :min="1" :max="100" />
          <span
            style="
              font-size: 12px;
              color: var(--text-secondary);
              margin-left: 10px;
            "
            >最多100张</span
          >
        </el-form-item>
        <el-form-item label="面额">
          <el-input-number
            v-model="generateForm.amount"
            :min="1"
            :precision="2"
          />
          <span
            style="
              font-size: 12px;
              color: var(--text-secondary);
              margin-left: 10px;
            "
            >单位：元</span
          >
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="handleGenerateSubmit"
          :loading="generating"
          >确定</el-button
        >
      </template>
    </el-dialog>

    <!-- 生成结果对话框 -->
    <el-dialog
      v-model="resultDialogVisible"
      title="生成结果"
      width="600px"
      append-to-body
    >
      <div class="result-content">
        <el-alert
          title="卡密生成成功"
          type="success"
          :closable="false"
          style="margin-bottom: 20px"
        />
        <div class="card-list">
          <div v-for="card in generatedCards" :key="card.id" class="card-item">
            <div class="card-info">
              <div class="card-no">卡号：{{ card.cardNo }}</div>
              <div class="card-password">卡密：{{ card.cardPassword }}</div>
              <div class="card-amount">面额：¥{{ card.amount }}</div>
            </div>
            <el-button size="small" @click="copyCard(card)">复制</el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="resultDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="exportCards">导出</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Search } from "@element-plus/icons-vue";
import { generateCards, queryCards, disableCard } from "@/api/card";

const loading = ref(false);
const generating = ref(false);
const tableData = ref([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(10);
const searchKeyword = ref("");
const statusFilter = ref("");

const generateDialogVisible = ref(false);
const resultDialogVisible = ref(false);
const generatedCards = ref([]);

const generateForm = ref({
  count: 1,
  amount: 10,
});

const loadCards = async () => {
  loading.value = true;
  try {
    const res = await queryCards({
      cardNo: searchKeyword.value,
      status: statusFilter.value,
      page: currentPage.value,
      pageSize: pageSize.value,
    });
    if (res.code === 1) {
      tableData.value = res.data.records;
      total.value = res.data.total;
    }
  } catch (error) {
    console.error("加载卡密列表失败：", error);
  } finally {
    loading.value = false;
  }
};

const handleGenerate = () => {
  generateForm.value = {
    count: 1,
    amount: 10,
  };
  generateDialogVisible.value = true;
};

const handleGenerateSubmit = async () => {
  generating.value = true;
  try {
    const res = await generateCards(generateForm.value);
    if (res.code === 1) {
      generatedCards.value = res.data;
      generateDialogVisible.value = false;
      resultDialogVisible.value = true;
      loadCards();
    }
  } catch (error) {
    console.error("生成卡密失败：", error);
  } finally {
    generating.value = false;
  }
};

const handleDisable = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要禁用卡号 ${row.cardNo} 吗？`,
      "确认禁用",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }
    );

    await disableCard(row.id);
    ElMessage.success("禁用成功");
    loadCards();
  } catch (error) {
    if (error !== "cancel") {
      console.error("禁用卡密失败：", error);
    }
  }
};

const getStatusType = (status) => {
  switch (status) {
    case 0:
      return "success";
    case 1:
      return "info";
    case 2:
      return "danger";
    default:
      return "";
  }
};

const getStatusText = (status) => {
  switch (status) {
    case 0:
      return "未使用";
    case 1:
      return "已使用";
    case 2:
      return "已禁用";
    default:
      return "未知";
  }
};

const formatDateTime = (dateTime) => {
  if (!dateTime) return "-";
  return new Date(dateTime).toLocaleString("zh-CN");
};

const copyCard = (card) => {
  const text = `卡号：${card.cardNo}\n卡密：${card.cardPassword}\n面额：¥${card.amount}`;
  navigator.clipboard
    .writeText(text)
    .then(() => {
      ElMessage.success("复制成功");
    })
    .catch(() => {
      ElMessage.error("复制失败");
    });
};

const exportCards = () => {
  let content = "卡号,卡密,面额\n";
  generatedCards.value.forEach((card) => {
    content += `${card.cardNo},${card.cardPassword},${card.amount}\n`;
  });

  const blob = new Blob([content], { type: "text/csv;charset=utf-8;" });
  const link = document.createElement("a");
  const url = URL.createObjectURL(blob);
  link.setAttribute("href", url);
  link.setAttribute(
    "download",
    `充值卡密_${new Date().toISOString().slice(0, 10)}.csv`
  );
  link.style.visibility = "hidden";
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);

  ElMessage.success("导出成功");
};

onMounted(() => {
  loadCards();
});
</script>

<style scoped>
.card-management {
  padding: 20px;
}

.operation-card {
  margin-bottom: 20px;
}

.operation-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-bar {
  display: flex;
  align-items: center;
}

.table-card {
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.amount {
  color: var(--color-success);
  font-weight: bold;
}

.result-content {
  max-height: 400px;
  overflow-y: auto;
}

.card-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 15px;
}

.card-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background-color: var(--bg-body);
}

.card-info {
  flex: 1;
}

.card-no,
.card-password,
.card-amount {
  margin-bottom: 5px;
  font-size: 14px;
}

.card-no {
  font-weight: bold;
  color: var(--text-primary);
}

.card-password {
  color: var(--text-regular);
}

.card-amount {
  color: var(--color-success);
  font-weight: bold;
}

/* Dark Mode Overrides */
html.dark .card-item {
  background-color: rgba(255, 255, 255, 0.05);
}
</style>
