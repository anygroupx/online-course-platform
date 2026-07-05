<template>
  <div class="api-providers-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>第三方接口管理</span>
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon>
            添加接口
          </el-button>
        </div>
      </template>

      <el-table :data="tableData" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="接口名称" width="150" />
        <el-table-column prop="providerType" label="接口类型" width="120" />
        <el-table-column prop="apiUrl" label="API地址" show-overflow-tooltip />
        <el-table-column prop="username" label="账号" width="120" />
        <el-table-column prop="balance" label="余额" width="100">
          <template #default="scope">
            <el-tag type="success">¥{{ scope.row.balance || 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">
              {{ scope.row.status === 1 ? "正常" : "禁用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastSyncTime" label="上次同步" width="160">
          <template #default="scope">
            <span
              v-if="scope.row.lastSyncTime"
              style="font-size: 12px; color: var(--text-regular)"
            >
              {{ formatTime(scope.row.lastSyncTime) }}
            </span>
            <span v-else style="color: var(--text-placeholder)">-</span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          :width="isMobile ? 120 : 280"
          fixed="right"
        >
          <template #default="scope">
            <el-button size="small" @click="handleEdit(scope.row)"
              >编辑</el-button
            >
            <el-button
              size="small"
              type="success"
              @click="handleBatchSync(scope.row)"
            >
              <el-icon><Refresh /></el-icon>
              批量同步
            </el-button>
            <el-button
              size="small"
              type="danger"
              @click="handleDelete(scope.row)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next"
        class="pagination"
      />
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="700px"
      append-to-body
    >
      <el-form :model="form" label-width="120px">
        <el-form-item label="接口名称">
          <el-input v-model="form.name" placeholder="请输入接口名称" />
        </el-form-item>
        <el-form-item label="接口类型">
          <el-select v-model="form.providerType" placeholder="请选择接口类型">
            <el-option label="27平台 (Benz)" value="27" />
            <el-option label="Oligei (2022)" value="oligei" />
            <el-option label="29同系统" value="29" />
            <el-option label="暗网 (yjdj)" value="yjdj" />
            <el-option label="Ikun" value="ikun" />
            <el-option label="网课联盟Cookie" value="wklmcookie" />
            <el-option label="网课联盟Token" value="wklmtoken" />
            <el-option label="00平台" value="00" />
            <el-option label="捐赠接口" value="jz" />
            <el-option label="学习通官方" value="xxtgf" />
            <el-option label="智慧职教官方" value="zjygf" />
            <el-option label="MOOC官方" value="moocgf" />
            <el-option label="ay查课" value="ayck" />
            <el-option label="哆啦a梦" value="dlam" />
            <el-option label="欧巴接口" value="ouba" />
          </el-select>
        </el-form-item>
        <el-form-item label="API地址">
          <el-input v-model="form.apiUrl" placeholder="请输入API地址" />
        </el-form-item>
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="请输入账号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" placeholder="请输入API Key" />
        </el-form-item>
        <el-form-item label="Token">
          <el-input
            v-model="form.token"
            type="textarea"
            :rows="2"
            placeholder="请输入Token"
          />
        </el-form-item>
        <el-form-item label="Cookie">
          <el-input
            v-model="form.cookie"
            type="textarea"
            :rows="3"
            placeholder="请输入Cookie"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">正常</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { Plus, Refresh } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import axios from "@/utils/request";
import { useResponsive } from "@/composables/useResponsive";
import dayjs from "dayjs";

const { isMobile } = useResponsive();

const tableData = ref([]);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);
const dialogVisible = ref(false);
const dialogTitle = ref("添加接口");
const form = ref({
  name: "",
  providerType: "",
  apiUrl: "",
  username: "",
  password: "",
  apiKey: "",
  token: "",
  cookie: "",
  status: 1,
});

const loadData = async () => {
  try {
    const res = await axios.get("/admin/api-providers", {
      params: {
        page: currentPage.value,
        pageSize: pageSize.value,
      },
    });
    if (res.code === 1) {
      tableData.value = res.data.records;
      total.value = res.data.total;
    }
  } catch (error) {
    console.error("加载数据失败：", error);
  }
};

const handleCreate = () => {
  dialogTitle.value = "添加接口";
  form.value = {
    name: "",
    providerType: "",
    apiUrl: "",
    username: "",
    password: "",
    apiKey: "",
    token: "",
    cookie: "",
    status: 1,
  };
  dialogVisible.value = true;
};

const handleEdit = (row) => {
  dialogTitle.value = "编辑接口";
  form.value = { ...row };
  dialogVisible.value = true;
};

const handleSubmit = async () => {
  try {
    if (form.value.id) {
      await axios.put("/admin/api-providers", form.value);
      ElMessage.success("更新成功");
    } else {
      await axios.post("/admin/api-providers", form.value);
      ElMessage.success("创建成功");
    }
    dialogVisible.value = false;
    loadData();
  } catch (error) {
    console.error("提交失败：", error);
  }
};

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm("确定要删除这个接口吗？", "警告", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });

    await axios.delete(`/admin/api-providers/${row.id}`);
    ElMessage.success("删除成功");
    loadData();
  } catch (error) {
    if (error !== "cancel") {
      console.error("删除失败：", error);
    }
  }
};

const handleBatchSync = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要批量同步 ${row.name} 的订单进度吗？将使用增量同步，只获取有更新的订单。`,
      "批量同步确认",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "info",
      }
    );

    const loading = ElMessage({
      message: "正在同步中，请稍候...",
      type: "info",
      duration: 0,
    });

    const res = await axios.post("/admin/docking/batch-sync", null, {
      params: {
        apiProviderId: row.id,
        offset: 0,
      },
    });

    loading.close();

    if (res.code === 200) {
      ElMessage.success(
        `同步完成！共同步 ${res.data.syncedCount} 条，更新 ${res.data.updatedCount} 条，未找到 ${res.data.notFoundCount} 条`
      );
      loadData();
    }
  } catch (error) {
    if (error !== "cancel") {
      console.error("批量同步失败：", error);
      ElMessage.error("批量同步失败：" + (error.message || "未知错误"));
    }
  }
};

const formatTime = (timestamp) => {
  if (!timestamp) return "";
  return dayjs.unix(timestamp).format("YYYY-MM-DD HH:mm:ss");
};

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.api-providers-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
