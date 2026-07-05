<template>
  <div class="users-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>代理管理</span>
          <div class="header-actions">
            <el-button type="success" @click="handleMyInviteCode">
              <el-icon><Key /></el-icon>
              我的邀请码
            </el-button>
            <el-button type="primary" @click="handleCreate">
              <el-icon><Plus /></el-icon>
              开户
            </el-button>
          </div>
        </div>
      </template>

      <!-- 筛选器 -->
      <EnterpriseFilter
        v-model="filterModel"
        :config="filterConfig"
        :loading="loading"
        storage-key="users"
        :enable-storage="true"
        @search="handleSearch"
        @reset="handleReset"
      />

      <!-- 表格 -->
      <EnterpriseTable
        ref="tableRef"
        :columns="columnsConfig"
        :data="tableData"
        :loading="loading"
        :pagination="{
          currentPage,
          pageSize,
          total,
        }"
        row-key="id"
        storage-key="users"
        :enable-storage="true"
        :enable-column-manage="true"
        card-title-key="username"
        card-badge-key="status"
        :mobile-columns="mobileColumns"
        @page-change="handlePageChange"
      >
        <!-- 余额列自定义渲染 -->
        <template #column-balance="{ row }">
          <span style="color: var(--color-success)"
            >¥{{ row.balance }}</span
          >
        </template>

        <!-- API密钥列自定义渲染 -->
        <template #column-apiKey="{ row }">
          <el-tag v-if="row.apiKey && row.apiKey !== '0'" type="success"
            >已开通</el-tag
          >
          <el-tag v-else type="info">未开通</el-tag>
        </template>

        <!-- 邀请码列自定义渲染 -->
        <template #column-inviteCode="{ row }">
          <el-tag
            v-if="row.inviteCode"
            type="primary"
            @click="copyInviteCode(row.inviteCode)"
            style="cursor: pointer"
          >
            {{ row.inviteCode }}
          </el-tag>
          <el-tag v-else type="info">未设置</el-tag>
        </template>

        <!-- 状态列自定义渲染 -->
        <template #column-status="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? "正常" : "禁用" }}
          </el-tag>
        </template>

        <!-- 移动端卡片徽章自定义渲染 -->
        <template #card-badge="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? "正常" : "禁用" }}
          </el-tag>
        </template>

        <!-- 操作列自定义渲染 -->
        <template #actions="{ row }">
          <div class="action-buttons">
            <el-button size="small" type="primary" @click="handleRecharge(row)">
              充值
            </el-button>
            <el-button size="small" @click="handleResetPassword(row)">
              重置密码
            </el-button>
            <el-button
              size="small"
              type="success"
              @click="handleInviteCode(row)"
            >
              邀请码
            </el-button>
            <el-button
              size="small"
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? "禁用" : "启用" }}
            </el-button>
          </div>
        </template>
      </EnterpriseTable>
    </el-card>

    <!-- 开户对话框 -->
    <el-dialog
      v-model="createDialogVisible"
      title="开户"
      width="500px"
      append-to-body
    >
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="用户名">
          <el-input
            v-model="createForm.username"
            placeholder="请输入用户名（QQ号）"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="createForm.password"
            type="password"
            placeholder="请输入密码"
          />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="createForm.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="费率">
          <el-input-number
            v-model="createForm.rate"
            :min="0.5"
            :max="2"
            :step="0.05"
            :precision="2"
          />
          <span
            style="
              font-size: 12px;
              color: var(--text-secondary);
              margin-left: 10px;
            "
            >费率不能低于自己的费率</span
          >
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 充值对话框 -->
    <el-dialog
      v-model="rechargeDialogVisible"
      title="充值"
      width="400px"
      append-to-body
    >
      <el-form :model="rechargeForm" label-width="100px">
        <el-form-item label="用户">
          <el-input :value="currentUser?.username" disabled />
        </el-form-item>
        <el-form-item label="充值金额">
          <el-input-number
            v-model="rechargeForm.amount"
            :min="10"
            :precision="2"
          />
          <span
            style="
              font-size: 12px;
              color: var(--text-secondary);
              margin-left: 10px;
            "
            >最低10元</span
          >
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rechargeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleRechargeSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 邀请码管理对话框 -->
    <el-dialog
      v-model="inviteCodeDialogVisible"
      title="邀请码管理"
      width="500px"
      append-to-body
    >
      <el-form :model="inviteCodeForm" label-width="100px">
        <el-form-item label="用户">
          <el-input :value="currentUser?.username" disabled />
        </el-form-item>
        <el-form-item label="当前邀请码">
          <el-input :value="currentUser?.inviteCode || '未设置'" disabled />
          <el-button
            v-if="currentUser?.inviteCode"
            size="small"
            type="primary"
            @click="copyInviteCode(currentUser.inviteCode)"
            style="margin-left: 10px"
          >
            复制
          </el-button>
        </el-form-item>
        <el-form-item label="邀请费率">
          <el-input-number
            v-model="inviteCodeForm.inviteRate"
            :min="0.5"
            :max="2"
            :step="0.05"
            :precision="2"
          />
          <span
            style="
              font-size: 12px;
              color: var(--text-secondary);
              margin-left: 10px;
            "
            >郀请费率不能低于自己的费率</span
          >
        </el-form-item>
        <el-form-item label="操作说明">
          <el-alert
            title="设置邀请费率后，系统会自动生成邀请码。用户通过此邀请码注册时，将获得您设置的费率。"
            type="info"
            :closable="false"
            show-icon
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inviteCodeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleInviteCodeSubmit"
          >确定</el-button
        >
      </template>
    </el-dialog>

    <!-- 我的邀请码对话框 -->
    <el-dialog
      v-model="myInviteCodeDialogVisible"
      title="我的邀请码"
      width="500px"
      append-to-body
    >
      <el-form :model="myInviteCodeForm" label-width="100px">
        <el-form-item label="当前邀请码">
          <el-input :value="myInviteCodeForm.inviteCode || '未设置'" disabled />
          <el-button
            v-if="myInviteCodeForm.inviteCode"
            size="small"
            type="primary"
            @click="copyInviteCode(myInviteCodeForm.inviteCode)"
            style="margin-left: 10px"
          >
            复制
          </el-button>
        </el-form-item>
        <el-form-item label="邀请费率">
          <el-input-number
            v-model="myInviteCodeForm.inviteRate"
            :min="0.5"
            :max="2"
            :step="0.05"
            :precision="2"
          />
          <span
            style="
              font-size: 12px;
              color: var(--text-secondary);
              margin-left: 10px;
            "
            >邀请费率不能低于自己的费率</span
          >
        </el-form-item>
        <el-form-item label="操作说明">
          <el-alert
            title="设置邀请费率后，系统会自动生成邀请码。用户通过此邀请码注册时，将获得您设置的费率。"
            type="info"
            :closable="false"
            show-icon
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="myInviteCodeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleMyInviteCodeSubmit"
          >确定</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { useRouter } from "vue-router";
import { Plus, Key } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { useTableComposition } from "@/composables/useTableComposition";
import EnterpriseFilter from "@/components/EnterpriseFilter.vue";
import EnterpriseTable from "@/components/EnterpriseTable.vue";
import {
  filterConfig,
  columnsConfig,
  rowActionsConfig,
  mobileColumns,
} from "@/config/usersConfig";
import {
  queryUsers,
  createUser,
  recharge,
  resetPassword,
  changeUserStatus,
  setupInviteCode,
  getUserInfo,
} from "@/api/user";

const router = useRouter();

// 使用表格组合式函数统一管理状态
const {
  currentPage,
  pageSize,
  total,
  filters,
  loading,
  tableData,
  handlePageChange,
  handleFilterChange,
  handleResetFilters,
  loadData,
} = useTableComposition({
  storageKey: "users",
  initialFilters: {
    keyword: "",
  },
  pageSize: 10,
  columns: columnsConfig,
});

// Refs
const tableRef = ref(null);

// 对话框状态
const createDialogVisible = ref(false);
const rechargeDialogVisible = ref(false);
const inviteCodeDialogVisible = ref(false);
const myInviteCodeDialogVisible = ref(false);
const currentUser = ref(null);

const createForm = ref({
  username: "",
  password: "",
  nickname: "",
  rate: 1.0,
});

const rechargeForm = ref({
  amount: 10,
});

const inviteCodeForm = ref({
  inviteRate: 1.0,
});

const myInviteCodeForm = ref({
  inviteCode: "",
  inviteRate: 1.0,
});

// 筛选模型（使用组合式函数的 filters）
const filterModel = computed({
  get: () => filters.value,
  set: (val) => handleFilterChange(val),
});

// 数据加载
const loadUsers = async () => {
  try {
    await loadData(async (params) => {
      const res = await queryUsers({
        keyword: params.keyword,
        page: params.page,
        pageSize: params.pageSize,
      });
      if (res.code === 1) {
        return {
          data: res.data.records || [],
          total: res.data.total || 0,
        };
      }
      throw new Error(res.msg || "加载失败");
    });
  } catch (error) {
    console.error("加载用户列表失败:", error);
    ElMessage.error("加载用户列表失败");
  }
};

// 事件处理
const handleSearch = () => {
  currentPage.value = 1;
  loadUsers();
};

const handleReset = () => {
  handleResetFilters();
  loadUsers();
};

const handleCreate = () => {
  createForm.value = {
    username: "",
    password: "",
    nickname: "",
    rate: 1.0,
  };
  createDialogVisible.value = true;
};

const handleCreateSubmit = async () => {
  try {
    await createUser(createForm.value);
    ElMessage.success("开户成功");
    createDialogVisible.value = false;
    loadUsers();
  } catch (error) {
    console.error("开户失败：", error);
  }
};

const handleRecharge = (row) => {
  currentUser.value = row;
  rechargeForm.value.amount = 10;
  rechargeDialogVisible.value = true;
};

const handleRechargeSubmit = async () => {
  try {
    await recharge({
      targetUserId: currentUser.value.id,
      amount: rechargeForm.value.amount,
    });
    ElMessage.success("充值成功");
    rechargeDialogVisible.value = false;
    loadUsers();
  } catch (error) {
    console.error("充值失败：", error);
  }
};

const handleResetPassword = async (row) => {
  try {
    await ElMessageBox.confirm("确定要重置该用户的密码吗？", "警告", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });

    const res = await resetPassword(row.id);
    ElMessage.success(`密码重置成功，新密码：${res.data}`);
  } catch (error) {
    if (error !== "cancel") {
      console.error("重置密码失败：", error);
    }
  }
};

const handleToggleStatus = async (row) => {
  const action = row.status === 1 ? "禁用" : "启用";
  try {
    await ElMessageBox.confirm(`确定要${action}该用户吗？`, "警告", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });

    const newStatus = row.status === 1 ? 0 : 1;
    await changeUserStatus(row.id, newStatus);
    ElMessage.success(`${action}成功`);
    loadUsers();
  } catch (error) {
    if (error !== "cancel") {
      console.error(`${action}失败：`, error);
    }
  }
};

// 复制邀请码
const copyInviteCode = (inviteCode) => {
  navigator.clipboard
    .writeText(inviteCode)
    .then(() => {
      ElMessage.success("邀请码已复制到剪贴板");
    })
    .catch(() => {
      ElMessage.error("复制失败，请手动复制");
    });
};

// 处理邀请码管理
const handleInviteCode = (row) => {
  currentUser.value = row;
  inviteCodeForm.value.inviteRate = row.inviteRate || 1.0;
  inviteCodeDialogVisible.value = true;
};

// 处理邀请码设置提交
const handleInviteCodeSubmit = async () => {
  try {
    const res = await setupInviteCode({
      inviteRate: inviteCodeForm.value.inviteRate,
    });
    ElMessage.success("邀请码设置成功");
    inviteCodeDialogVisible.value = false;
    loadUsers();
  } catch (error) {
    console.error("设置邀请码失败：", error);
  }
};

// 处理我的邀请码
const handleMyInviteCode = async () => {
  // 检查用户是否已登录
  const token = localStorage.getItem("token");
  if (!token) {
    ElMessage.error("请先登录");
    router.push("/login");
    return;
  }

  try {
    // 获取当前用户信息
    const res = await getUserInfo();
    if (res.code === 1) {
      myInviteCodeForm.value.inviteCode = res.data.inviteCode || "";
      myInviteCodeForm.value.inviteRate = res.data.inviteRate || 1.0;
      myInviteCodeDialogVisible.value = true;
    }
  } catch (error) {
    console.error("获取用户信息失败：", error);
    ElMessage.error("获取用户信息失败，请重新登录");
    router.push("/login");
  }
};

// 处理我的邀请码设置提交
const handleMyInviteCodeSubmit = async () => {
  try {
    const res = await setupInviteCode({
      inviteRate: myInviteCodeForm.value.inviteRate,
    });
    ElMessage.success("邀请码设置成功");
    myInviteCodeDialogVisible.value = false;
    // 重新获取用户信息
    const userRes = await getUserInfo();
    if (userRes.code === 1) {
      myInviteCodeForm.value.inviteCode = userRes.data.inviteCode || "";
    }
  } catch (error) {
    console.error("设置邀请码失败：", error);
  }
};

// 企业方案：监听分页变化自动加载数据
watch([currentPage, pageSize], () => {
  loadUsers();
});

onMounted(() => {
  loadUsers();
});
</script>

<style scoped>
.users-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 10px;
}

/* 操作列按钮组 */
.action-buttons {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.action-buttons .el-button {
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .users-page {
    padding: 12px;
  }

  .header-actions {
    flex-direction: column;
    width: 100%;
  }

  .header-actions .el-button {
    width: 100%;
  }

  /* 移动端操作按钮保持横排，自动换行 */
  .action-buttons {
    gap: 6px;
    justify-content: flex-end;
  }

  .action-buttons .el-button {
    flex: 0 0 auto;
    padding: 4px 8px;
    font-size: 12px;
  }
}
</style>
