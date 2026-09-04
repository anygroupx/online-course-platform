<template>
  <div class="aqks-study-management">
    <!-- 页面标题 -->
    <el-card class="page-header">
      <div class="header-content">
        <div>
          <h2>AQKS刷课管理</h2>
          <p>管理实验室安全自营订单的自动刷课任务</p>
        </div>
        <div class="header-actions">
          <el-button type="warning" @click="handleSyncExamStatus" :loading="syncLoading">
            <el-icon><Check /></el-icon>
            同步考试状态
          </el-button>
          <el-button type="primary" @click="loadOrders">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="statistics-cards">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-value">{{ statistics.runningCount || 0 }}</div>
            <div class="stat-label">运行中任务</div>
          </div>
          <el-icon class="stat-icon primary"><Loading /></el-icon>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-value">{{ statistics.pendingExam || 0 }}</div>
            <div class="stat-label">待考试</div>
          </div>
          <el-icon class="stat-icon warning"><Clock /></el-icon>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-value">{{ statistics.completed || 0 }}</div>
            <div class="stat-label">已完成</div>
          </div>
          <el-icon class="stat-icon success"><Check /></el-icon>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-value">{{ statistics.total || 0 }}</div>
            <div class="stat-label">自营订单总数</div>
          </div>
          <el-icon class="stat-icon info"><Document /></el-icon>
        </el-card>
      </el-col>
    </el-row>

    <!-- 批量操作工具栏 -->
    <el-card v-if="selectedOrders.length > 0" class="batch-actions-card">
      <div class="batch-actions">
        <span class="batch-info"
          >已选择 {{ selectedOrders.length }} 个订单</span
        >
        <div class="batch-buttons">
          <el-button
            type="success"
            size="small"
            :loading="batchLoading"
            @click="handleBatchStart"
            :disabled="!canBatchStart"
          >
            <el-icon><VideoPlay /></el-icon>
            批量启动刷课
          </el-button>
          <el-button
            type="danger"
            size="small"
            :loading="batchLoading"
            @click="handleBatchStop"
            :disabled="!canBatchStop"
          >
            <el-icon><VideoPause /></el-icon>
            批量停止
          </el-button>
          <el-button
            type="info"
            size="small"
            :loading="batchLoading"
            @click="handleBatchRefresh"
          >
            <el-icon><Refresh /></el-icon>
            批量刷新
          </el-button>
          <el-button size="small" @click="clearSelection"> 取消选择 </el-button>
        </div>
      </div>
    </el-card>

    <!-- 订单列表 -->
    <el-card class="order-list-card">
      <template #header>
        <div class="card-header">
          <span>自营订单列表</span>
          <div class="filter-actions">
            <el-select
              v-model="filters.status"
              placeholder="订单状态"
              clearable
              @change="loadOrders"
              style="width: 120px; margin-right: 10px"
            >
              <el-option label="待处理" :value="0" />
              <el-option label="进行中" :value="1" />
              <el-option label="已完成" :value="2" />
              <el-option label="待考试" :value="5" />
              <el-option label="考试中" :value="6" />
              <el-option label="考试完成" :value="7" />
            </el-select>
            <el-input
              v-model="filters.studentAccount"
              placeholder="学生账号"
              clearable
              style="width: 150px"
              @keyup.enter="loadOrders"
            />
          </div>
        </div>
      </template>

      <el-table
        :data="orders"
        v-loading="loading"
        stripe
        border
        @selection-change="handleSelectionChange"
        class="responsive-table"
      >
        <el-table-column type="selection" width="55" class-name="hide-on-mobile" />
        <el-table-column prop="orderNo" label="订单号" width="180" class-name="hide-on-mobile" />
        <el-table-column prop="studentAccount" label="学生账号" width="150" class-name="always-show" />
        <el-table-column prop="platformName" label="平台" width="120" class-name="hide-on-mobile" />
        <el-table-column prop="progress" label="学习进度" width="150" class-name="always-show">
          <template #default="{ row }">
            <el-progress
              :percentage="parseProgress(row.progress)"
              :status="parseProgress(row.progress) >= 100 ? 'success' : ''"
              :stroke-width="10"
            />
            <span class="progress-text">{{ row.progress || "0%" }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="orderStatus" label="状态" width="100" class-name="always-show">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.orderStatus)">
              {{ getStatusText(row.orderStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="刷课状态" width="100" class-name="always-show">
          <template #default="{ row }">
            <el-tag :type="row.isStudyRunning ? 'success' : 'info'">
              {{ row.isStudyRunning ? "运行中" : "已停止" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" class-name="hide-on-mobile" />
        <el-table-column label="操作" fixed="right" width="380" class-name="action-column always-show">
          <template #default="{ row }">
            <!-- 主要操作按钮（始终显示） -->
            <div class="action-buttons">
              <el-button
                v-if="!row.isStudyRunning && row.orderStatus < 5"
                type="success"
                size="small"
                @click="handleStartStudy(row)"
                class="action-btn primary-action"
              >
                <el-icon><VideoPlay /></el-icon>
                <span class="btn-text">启动刷课</span>
              </el-button>
              <el-button
                v-if="row.isStudyRunning"
                type="danger"
                size="small"
                @click="handleStopStudy(row)"
                class="action-btn primary-action"
              >
                <el-icon><VideoPause /></el-icon>
                <span class="btn-text">停止</span>
              </el-button>

              <!-- 次要操作按钮（大屏显示） -->
              <el-button
                type="primary"
                size="small"
                @click="handleManualAdd(row)"
                class="action-btn secondary-action"
              >
                <el-icon><Plus /></el-icon>
                <span class="btn-text">10秒</span>
              </el-button>
              <el-button
                type="info"
                size="small"
                @click="handleRefreshStatus(row)"
                class="action-btn secondary-action"
              >
                <el-icon><Refresh /></el-icon>
              </el-button>
              <el-dropdown
                trigger="click"
                @command="(command) => handleDetailAction(command, row)"
                class="secondary-action"
              >
                <el-button type="text" size="small">
                  详情<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="detail">订单详情</el-dropdown-item>
                    <el-dropdown-item command="log">学习日志</el-dropdown-item>
                    <el-dropdown-item command="checkExam" divided v-if="row.orderStatus >= 5 && row.orderStatus <= 6">
                      <el-icon><Check /></el-icon>
                      检查考试
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>

              <!-- 小屏幕折叠菜单 -->
              <el-dropdown
                trigger="click"
                @command="(command) => handleMobileAction(command, row)"
                class="mobile-more-action"
              >
                <el-button size="small" type="primary" plain>
                  <el-icon><ArrowDown /></el-icon>
                  <span class="btn-text">更多</span>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="addTime">
                      <el-icon><Plus /></el-icon>
                      增加10秒
                    </el-dropdown-item>
                    <el-dropdown-item command="refresh">
                      <el-icon><Refresh /></el-icon>
                      刷新状态
                    </el-dropdown-item>
                    <el-dropdown-item divided command="detail">
                      <el-icon><Document /></el-icon>
                      订单详情
                    </el-dropdown-item>
                    <el-dropdown-item command="log">
                      <el-icon><Clock /></el-icon>
                      学习日志
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadOrders"
          @current-change="loadOrders"
        />
      </div>
    </el-card>

    <!-- 学习状态详情对话框 -->
    <el-dialog
      v-model="statusDialogVisible"
      title="学习状态详情"
      width="500px"
      append-to-body
    >
      <el-descriptions :column="1" border v-if="currentStatus">
        <el-descriptions-item label="学生姓名">{{
          currentStatus.name
        }}</el-descriptions-item>
        <el-descriptions-item label="学院">{{
          currentStatus.departmentName
        }}</el-descriptions-item>
        <el-descriptions-item label="专业">{{
          currentStatus.specialtyName
        }}</el-descriptions-item>
        <el-descriptions-item label="年级">{{
          currentStatus.grade
        }}</el-descriptions-item>
        <el-descriptions-item label="已学习"
          >{{ currentStatus.studyTimes }} 分钟</el-descriptions-item
        >
        <el-descriptions-item label="要求时长"
          >{{ currentStatus.minTimeMinute }} 分钟</el-descriptions-item
        >
        <el-descriptions-item label="进度">
          <el-progress
            :percentage="currentStatus.progressPercent || 0"
            :stroke-width="15"
          />
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 订单详情对话框 -->
    <el-dialog
      v-model="orderDetailVisible"
      title="订单详情"
      width="700px"
      append-to-body
    >
      <el-descriptions :column="2" border v-if="currentOrderDetail">
        <el-descriptions-item label="订单号" :span="2">{{
          currentOrderDetail.orderNo
        }}</el-descriptions-item>
        <el-descriptions-item label="学生账号">{{
          currentOrderDetail.studentAccount
        }}</el-descriptions-item>
        <el-descriptions-item label="学生姓名">{{
          currentOrderDetail.studentName
        }}</el-descriptions-item>
        <el-descriptions-item label="平台">{{
          currentOrderDetail.platformName
        }}</el-descriptions-item>
        <el-descriptions-item label="订单状态">
          <el-tag :type="getStatusType(currentOrderDetail.orderStatus)">
            {{ getStatusText(currentOrderDetail.orderStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="学习进度">{{
          currentOrderDetail.progress || "0%"
        }}</el-descriptions-item>
        <el-descriptions-item label="刷课状态">
          <el-tag
            :type="currentOrderDetail.isStudyRunning ? 'success' : 'info'"
          >
            {{ currentOrderDetail.isStudyRunning ? "运行中" : "已停止" }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{
          currentOrderDetail.createTime
        }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{
          currentOrderDetail.updateTime
        }}</el-descriptions-item>
        <el-descriptions-item label="订单金额" v-if="currentOrderDetail.amount">
          <span style="color: var(--color-danger); font-weight: bold"
            >￥{{ currentOrderDetail.amount }}</span
          >
        </el-descriptions-item>
        <el-descriptions-item
          label="支付状态"
          v-if="currentOrderDetail.payStatus !== undefined"
        >
          <el-tag
            :type="currentOrderDetail.payStatus === 1 ? 'success' : 'warning'"
          >
            {{ currentOrderDetail.payStatus === 1 ? "已支付" : "未支付" }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item
          label="备注"
          :span="2"
          v-if="currentOrderDetail.remark"
          >{{ currentOrderDetail.remark }}</el-descriptions-item
        >
      </el-descriptions>
    </el-dialog>

    <!-- 学习日志对话框 -->
    <el-dialog
      v-model="studyLogVisible"
      title="学习日志"
      width="800px"
      append-to-body
    >
      <el-table :data="studyLogs" max-height="400">
        <el-table-column prop="createTime" label="时间" width="160" />
        <el-table-column prop="action" label="操作" width="120" />
        <el-table-column prop="message" label="详情" />
        <el-table-column prop="progress" label="进度" width="100" />
      </el-table>
      <div v-if="studyLogs.length === 0" class="no-data">
        <el-empty description="暂无学习日志" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Refresh,
  Loading,
  Clock,
  Check,
  Document,
  VideoPlay,
  VideoPause,
  Plus,
  ArrowDown,
} from "@element-plus/icons-vue";
import { queryAllOrders } from "@/api/order";
import {
  startAutoStudy,
  stopAutoStudy,
  addStudyTime,
  getStudyStatus,
  isTaskRunning,
  batchCheckRunningStatus,
  getAqksStatistics,
  checkExamStatus,
  syncExamStatus,
} from "@/api/aqks";

// 数据
const loading = ref(false);
const orders = ref([]);
const statistics = ref({
  runningCount: 0,
  pendingExam: 0,
  completed: 0,
  total: 0,
});
const filters = ref({
  status: null,
  studentAccount: "",
});
const pagination = ref({
  page: 1,
  size: 20,
  total: 0,
});
const statusDialogVisible = ref(false);
const currentStatus = ref(null);

// 多选功能
const selectedOrders = ref([]);
const batchLoading = ref(false);
const syncLoading = ref(false);  // 同步考试状态加载状态

// 订单详情对话框
const orderDetailVisible = ref(false);
const currentOrderDetail = ref(null);

// 学习日志对话框
const studyLogVisible = ref(false);
const studyLogs = ref([]);

// 刷新定时器
let refreshTimer = null;

// 加载订单列表
const loadOrders = async () => {
  loading.value = true;
  try {
    const params = {
      page: pagination.value.page,
      pageSize: pagination.value.size, // 后端使用pageSize
      isSelfOperated: 1, // 只查询自营订单
      orderStatus: filters.value.status,
      studentAccount: filters.value.studentAccount || undefined,
    };

    const res = await queryAllOrders(params);
    if (res.code === 1) {
      orders.value = res.data.records || [];
      console.log("加载订单:", orders.value);
      pagination.value.total = res.data.total || 0;

      // 计算统计数据
      statistics.value.total = pagination.value.total;

      // 检查每个订单的刷课状态
      await checkStudyStatus();
    }
  } catch (error) {
    console.error("加载订单失败:", error);
    ElMessage.error("加载订单失败");
  } finally {
    loading.value = false;
  }
};

// 检查刷课状态（批量优化版）
// Source: AURA-X-KYS - 批量查询优化，减少网络请求
const checkStudyStatus = async () => {
  try {
    // 获取统计数据（后端统计）
    const statsRes = await getAqksStatistics();
    if (statsRes.code === 1 && statsRes.data) {
      statistics.value.runningCount = statsRes.data.runningCount || 0;
      statistics.value.pendingExam = statsRes.data.pendingExam || 0;
      statistics.value.completed = statsRes.data.completed || 0;
      statistics.value.total = statsRes.data.total || 0;
    }

    // 批量检查所有订单的刷课状态
    if (orders.value.length > 0) {
      const orderIds = orders.value.map(order => order.id);
      const batchRes = await batchCheckRunningStatus(orderIds);

      if (batchRes.code === 1 && batchRes.data) {
        // 更新每个订单的运行状态
        orders.value.forEach(order => {
          order.isStudyRunning = batchRes.data[order.id] === true;
        });
      }
    }
  } catch (error) {
    console.error("检查刷课状态失败:", error);
  }
};

// 启动刷课（优化版 - 智能状态处理）
// Source: AURA-X-KYS - 智能状态同步
const handleStartStudy = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要为订单 ${row.orderNo} 启动自动刷课吗？`,
      "启动刷课",
      { type: "info" }
    );

    const res = await startAutoStudy(row.id);
    if (res.code === 1) {
      ElMessage.success("自动刷课已启动");
      row.isStudyRunning = true;
      statistics.value.runningCount++;

      // 如果订单状态是待处理(0)，更新为进行中(1)
      if (row.orderStatus === 0) {
        row.orderStatus = 1;
      }

      // 刷新一次列表以同步最新状态
      await checkStudyStatus();
    } else {
      // 特殊处理：进度已达100%的情况
      if (res.msg && res.msg.includes('100%')) {
        ElMessage.warning({
          message: '学习进度已达100%，订单已自动切换为待考试状态',
          duration: 5000
        });
        // 刷新列表以显示最新状态
        await loadOrders();
      } else {
        ElMessage.error(res.msg || "启动失败");
      }
    }
  } catch (error) {
    if (error !== "cancel") {
      // 检查是否是后端返回的业务异常
      const errorMsg = error.response?.data?.msg || error.message || "启动刷课失败";
      if (errorMsg.includes('100%')) {
        ElMessage.warning({
          message: errorMsg,
          duration: 5000
        });
        // 刷新列表
        await loadOrders();
      } else {
        ElMessage.error(errorMsg);
      }
    }
  }
};

// 停止刷课
const handleStopStudy = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要停止订单 ${row.orderNo} 的自动刷课吗？`,
      "停止刷课",
      { type: "warning" }
    );

    const res = await stopAutoStudy(row.id);
    if (res.code === 1) {
      ElMessage.success("已停止刷课");
      row.isStudyRunning = false;
      statistics.value.runningCount = Math.max(
        0,
        statistics.value.runningCount - 1
      );
    } else {
      ElMessage.error(res.msg || "停止失败");
    }
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error("停止刷课失败");
    }
  }
};

// 手动刷时长
const handleManualAdd = async (row) => {
  try {
    const res = await addStudyTime(row.id, 10);
    if (res.code === 1) {
      ElMessage.success(res.data || "时长增加成功");
      // 刷新状态
      handleRefreshStatus(row);
    } else {
      ElMessage.error(res.msg || "刷时长失败");
    }
  } catch (error) {
    ElMessage.error("刷时长失败");
  }
};

// 刷新状态
const handleRefreshStatus = async (row) => {
  try {
    const res = await getStudyStatus(row.id);
    if (res.code === 1 && res.data) {
      // 计算进度百分比
      let progressPercent = 0;
      if (res.data.studyTimes && res.data.minTimeMinute) {
        const studied = parseInt(res.data.studyTimes);
        const required = parseInt(res.data.minTimeMinute);
        progressPercent =
          required > 0
            ? Math.min(100, Math.round((studied * 100) / required))
            : 0;
        row.progress = `${progressPercent}% (${studied}/${required}分钟)`;
      }

      // 保存状态并添加进度百分比
      currentStatus.value = {
        ...res.data,
        progressPercent, // 添加计算好的进度百分比
      };

      statusDialogVisible.value = true;
    } else {
      ElMessage.error(res.msg || "获取状态失败");
    }
  } catch (error) {
    ElMessage.error("获取状态失败");
  }
};

// 解析进度百分比
const parseProgress = (progress) => {
  if (!progress) return 0;
  const match = progress.match(/(\d+)%/);
  return match ? parseInt(match[1]) : 0;
};

// 多选处理
const handleSelectionChange = (selection) => {
  selectedOrders.value = selection;
};

// 清空选择
const clearSelection = () => {
  selectedOrders.value = [];
};

// 批量操作条件判断
const canBatchStart = computed(() => {
  return selectedOrders.value.some(
    (order) => !order.isStudyRunning && order.orderStatus < 5
  );
});

const canBatchStop = computed(() => {
  return selectedOrders.value.some((order) => order.isStudyRunning);
});

// 批量启动刷课
const handleBatchStart = async () => {
  const eligibleOrders = selectedOrders.value.filter(
    (order) => !order.isStudyRunning && order.orderStatus < 5
  );

  if (eligibleOrders.length === 0) {
    ElMessage.warning("没有可启动的订单");
    return;
  }

  try {
    await ElMessageBox.confirm(
      `确定要批量启动 ${eligibleOrders.length} 个订单的自动刷课吗？`,
      "批量启动刷课",
      { type: "info" }
    );

    batchLoading.value = true;
    let successCount = 0;
    let failCount = 0;

    for (const order of eligibleOrders) {
      try {
        const res = await startAutoStudy(order.id);
        if (res.code === 1) {
          order.isStudyRunning = true;
          successCount++;
        } else {
          failCount++;
        }
      } catch {
        failCount++;
      }
    }

    if (successCount > 0) {
      statistics.value.runningCount += successCount;
    }

    ElMessage.success(
      `批量启动完成：成功 ${successCount} 个${
        failCount > 0 ? `，失败 ${failCount} 个` : ""
      }`
    );
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error("批量启动失败");
    }
  } finally {
    batchLoading.value = false;
  }
};

// 批量停止刷课
const handleBatchStop = async () => {
  const eligibleOrders = selectedOrders.value.filter(
    (order) => order.isStudyRunning
  );

  if (eligibleOrders.length === 0) {
    ElMessage.warning("没有正在运行的订单");
    return;
  }

  try {
    await ElMessageBox.confirm(
      `确定要批量停止 ${eligibleOrders.length} 个订单的自动刷课吗？`,
      "批量停止刷课",
      { type: "warning" }
    );

    batchLoading.value = true;
    let successCount = 0;
    let failCount = 0;

    for (const order of eligibleOrders) {
      try {
        const res = await stopAutoStudy(order.id);
        if (res.code === 1) {
          order.isStudyRunning = false;
          successCount++;
        } else {
          failCount++;
        }
      } catch {
        failCount++;
      }
    }

    if (successCount > 0) {
      statistics.value.runningCount = Math.max(
        0,
        statistics.value.runningCount - successCount
      );
    }

    ElMessage.success(
      `批量停止完成：成功 ${successCount} 个${
        failCount > 0 ? `，失败 ${failCount} 个` : ""
      }`
    );
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error("批量停止失败");
    }
  } finally {
    batchLoading.value = false;
  }
};

// 批量刷新状态
const handleBatchRefresh = async () => {
  if (selectedOrders.value.length === 0) {
    ElMessage.warning("请先选择订单");
    return;
  }

  batchLoading.value = true;
  try {
    for (const order of selectedOrders.value) {
      try {
        const res = await getStudyStatus(order.id);
        if (res.code === 1 && res.data) {
          // 更新进度
          if (res.data.studyTimes && res.data.minTimeMinute) {
            const studied = parseInt(res.data.studyTimes);
            const required = parseInt(res.data.minTimeMinute);
            const progressPercent =
              required > 0
                ? Math.min(100, Math.round((studied * 100) / required))
                : 0;
            order.progress = `${progressPercent}% (${studied}/${required}分钟)`;
          }
        }
      } catch {
        // 忽略单个订单的错误
      }
    }
    ElMessage.success("批量刷新完成");
  } catch (error) {
    ElMessage.error("批量刷新失败");
  } finally {
    batchLoading.value = false;
  }
};

// 详情操作处理
const handleDetailAction = async (command, row) => {
  if (command === "detail") {
    // 订单详情
    currentOrderDetail.value = { ...row };
    orderDetailVisible.value = true;
  } else if (command === "log") {
    // 学习日志
    try {
      // 模拟获取学习日志（实际需要调用API）
      studyLogs.value = [
        {
          createTime: "2025-12-21 10:30:00",
          action: "启动刷课",
          message: "自动刷课任务已启动",
          progress: "0%",
        },
        {
          createTime: "2025-12-21 11:00:00",
          action: "进度更新",
          message: "学习进度更新",
          progress: "25%",
        },
        {
          createTime: "2025-12-21 11:30:00",
          action: "进度更新",
          message: "学习进度更新",
          progress: "50%",
        },
      ];
      studyLogVisible.value = true;
    } catch (error) {
      ElMessage.error("获取学习日志失败");
    }
  } else if (command === "checkExam") {
    // 检查考试状态
    await handleCheckExam(row);
  }
};

// 移动端操作处理
// Source: AURA-X-KYS - 响应式操作栏优化
const handleMobileAction = async (command, row) => {
  switch (command) {
    case "addTime":
      await handleManualAdd(row);
      break;
    case "refresh":
      await handleRefreshStatus(row);
      break;
    case "detail":
      currentOrderDetail.value = { ...row };
      orderDetailVisible.value = true;
      break;
    case "log":
      await handleDetailAction("log", row);
      break;
    case "checkExam":
      await handleCheckExam(row);
      break;
  }
};

// 检查单个订单的考试状态
const handleCheckExam = async (row) => {
  try {
    ElMessage.info(`正在检查订单 ${row.orderNo} 的考试状态...`);

    const res = await checkExamStatus(row.id);
    if (res.code === 1 && res.data) {
      const examInfo = res.data;
      const passed = examInfo.isPassed ? "通过" : "未通过";
      ElMessage.success({
        message: `考试检查完成：分数 ${examInfo.score}, ${passed}`,
        duration: 5000
      });

      // 刷新订单列表
      await loadOrders();
    } else {
      ElMessage.warning(res.msg || "未获取到考试信息");
    }
  } catch (error) {
    ElMessage.error("检查考试状态失败: " + (error.message || error));
  }
};

// 批量同步考试状态
const handleSyncExamStatus = async () => {
  try {
    await ElMessageBox.confirm(
      "确定要同步所有待考试/考试中订单的考试状态吗？",
      "同步考试状态",
      { type: "info" }
    );

    syncLoading.value = true;
    const res = await syncExamStatus();

    if (res.code === 1 && res.data) {
      const { total, success, failed } = res.data;
      ElMessage.success(
        `同步完成：总计 ${total} 个，成功 ${success} 个，失败 ${failed} 个`
      );

      // 刷新订单列表和统计
      await loadOrders();
    } else {
      ElMessage.error(res.msg || "同步失败");
    }
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error("同步考试状态失败");
    }
  } finally {
    syncLoading.value = false;
  }
};

// 状态文字
const getStatusText = (status) => {
  const map = {
    0: "待处理",
    1: "进行中",
    2: "已完成",
    3: "已取消",
    4: "失败",
    5: "待考试",
    6: "考试中",
    7: "考试完成",
  };
  return map[status] || "未知";
};

// 状态样式
const getStatusType = (status) => {
  const map = {
    0: "info",
    1: "primary",
    2: "success",
    3: "warning",
    4: "danger",
    5: "warning",
    6: "primary",
    7: "success",
  };
  return map[status] || "info";
};

// 生命周期
onMounted(() => {
  loadOrders();
  // 每30秒自动刷新
  refreshTimer = setInterval(() => {
    checkStudyStatus();
  }, 30000);
});

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer);
  }
});
</script>

<style scoped>
/* Source: AURA-X-KYS - 响应式布局与主题集成优化 */
.aqks-study-management {
  padding: 20px;
  min-height: calc(100vh - var(--header-height));
}

/* ========== 页面头部 ========== */
.page-header {
  margin-bottom: 20px;
  background: var(--bg-card);
  border: 1px solid var(--border-color-light);
  box-shadow: var(--shadow-sm);
  transition: all 0.3s ease;
}

.page-header:hover {
  background: var(--bg-card-hover);
  box-shadow: var(--shadow-md);
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 15px;
}

.header-content h2 {
  margin: 0 0 5px 0;
  font-size: 20px;
  color: var(--text-primary);
  background: var(--primary-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.header-content p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 14px;
}

/* ========== 统计卡片 ========== */
.statistics-cards {
  margin-bottom: 20px;
}

.stat-card {
  position: relative;
  overflow: hidden;
  background: var(--bg-card);
  border: 1px solid var(--border-color-light);
  transition: all 0.3s ease;
  cursor: pointer;
}

.stat-card:hover {
  background: var(--bg-card-hover);
  box-shadow: var(--shadow-lg);
  transform: translateY(-4px);
}

.stat-content {
  position: relative;
  z-index: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: var(--text-primary);
  transition: color 0.3s ease;
}

.stat-label {
  font-size: 14px;
  color: var(--text-secondary);
  margin-top: 5px;
}

.stat-icon {
  position: absolute;
  right: 20px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 48px;
  opacity: 0.15;
  transition: all 0.3s ease;
}

.stat-card:hover .stat-icon {
  opacity: 0.25;
  transform: translateY(-50%) scale(1.1);
}

.stat-icon.primary {
  color: var(--primary-gradient-start);
}
.stat-icon.success {
  color: var(--color-success);
}
.stat-icon.warning {
  color: var(--color-warning);
}
.stat-icon.info {
  color: var(--color-info);
}

/* ========== 批量操作卡片 ========== */
.batch-actions-card {
  margin-bottom: 20px;
  border: 2px dashed var(--primary-gradient-start);
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
}

.batch-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  flex-wrap: wrap;
  gap: 15px;
}

.batch-info {
  font-size: 14px;
  color: var(--primary-gradient-start);
  font-weight: 500;
}

.batch-buttons {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.batch-buttons .el-button {
  font-size: 12px;
}

/* ========== 订单列表卡片 ========== */
.order-list-card {
  margin-bottom: 20px;
  background: var(--bg-card);
  border: 1px solid var(--border-color-light);
  box-shadow: var(--shadow-sm);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.card-header span {
  color: var(--text-primary);
  font-weight: 500;
}

.filter-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.progress-text {
  font-size: 12px;
  color: var(--text-secondary);
  margin-left: 10px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

/* ========== 空数据样式 ========== */
.no-data {
  text-align: center;
  padding: 20px 0;
  color: var(--text-secondary);
}

/* ========== 操作栏样式 ========== */
.action-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.action-btn {
  white-space: nowrap;
}

/* 默认隐藏移动端折叠菜单 */
.mobile-more-action {
  display: none;
}

/* ========== 表格响应式 ========== */
.responsive-table {
  width: 100%;
}

/* 确保表格可以横向滚动 */
:deep(.el-table__body-wrapper) {
  overflow-x: auto;
}

/* ========== 响应式设计 ========== */

/* 平板及以下 (≤ 1024px) */
@media screen and (max-width: 1024px) {
  .aqks-study-management {
    padding: 15px;
  }

  /* 统计卡片：2列布局 */
  :deep(.statistics-cards .el-col) {
    max-width: 50% !important;
    flex: 0 0 50% !important;
  }

  .stat-value {
    font-size: 24px;
  }

  .stat-icon {
    font-size: 40px;
    right: 15px;
  }

  /* 表格操作列调整 */
  :deep(.el-table .el-button) {
    padding: 5px 10px;
    font-size: 12px;
  }

  /* 操作栏：隐藏按钮文字，只显示图标 */
  .action-btn .btn-text {
    display: none;
  }

  /* 平板下隐藏学生账号列 */
  :deep(.hide-on-tablet) {
    display: none !important;
  }
}

/* 手机横屏/小平板 (≤ 768px) */
@media screen and (max-width: 768px) {
  .aqks-study-management {
    padding: 10px;
  }

  .header-content {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-content h2 {
    font-size: 18px;
  }

  /* 统计卡片：单列布局 */
  :deep(.statistics-cards .el-col) {
    max-width: 100% !important;
    flex: 0 0 100% !important;
    margin-bottom: 10px;
  }

  /* 批量操作按钮堆叠 */
  .batch-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .batch-buttons {
    justify-content: center;
  }

  /* 筛选栏堆叠 */
  .card-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .filter-actions {
    width: 100%;
  }

  .filter-actions .el-select,
  .filter-actions .el-input {
    flex: 1;
    min-width: 0;
  }

  /* 表格滚动优化 */
  :deep(.el-table) {
    font-size: 12px;
  }

  :deep(.el-table__header th) {
    padding: 8px 0;
  }

  :deep(.el-table__body td) {
    padding: 8px 0;
  }

  /* 分页器简化 */
  :deep(.el-pagination) {
    flex-wrap: wrap;
    justify-content: center;
  }

  :deep(.el-pagination .el-pagination__sizes) {
    margin: 0 5px;
  }

  /* 操作栏响应式：隐藏次要操作，显示折叠菜单 */
  .secondary-action {
    display: none !important;
  }

  .mobile-more-action {
    display: inline-block;
  }

  /* 主要操作按钮仅显示图标 */
  .primary-action .btn-text {
    display: none;
  }

  /* 调整操作列宽度 */
  :deep(.action-column) {
    min-width: 120px !important;
  }

  /* 手机屏幕下隐藏次要列 */
  :deep(.hide-on-mobile) {
    display: none !important;
  }

  /* 确保核心列显示 */
  :deep(.always-show) {
    display: table-cell !important;
  }

  /* 调整表格字体和间距 */
  :deep(.el-table) {
    font-size: 12px;
  }

  :deep(.el-table .cell) {
    padding: 4px 8px;
    line-height: 1.5;
  }

  /* 订单号列在小屏幕下缩短 */
  :deep(.el-table th:first-of-type),
  :deep(.el-table td:first-of-type) {
    min-width: 140px;
  }
}

/* 手机竖屏 (≤ 480px) */
@media screen and (max-width: 480px) {
  .aqks-study-management {
    padding: 8px;
  }

  .page-header {
    margin-bottom: 10px;
  }

  .header-content h2 {
    font-size: 16px;
  }

  .header-content p {
    font-size: 12px;
  }

  .statistics-cards {
    margin-bottom: 10px;
  }

  .stat-value {
    font-size: 20px;
  }

  .stat-label {
    font-size: 12px;
  }

  .stat-icon {
    font-size: 32px;
    right: 10px;
  }

  /* 批量操作按钮全宽 */
  .batch-buttons .el-button {
    flex: 1;
    min-width: 0;
  }

  /* 表格操作列按钮进一步缩小 */
  :deep(.el-table .el-button) {
    padding: 4px 8px;
    font-size: 12px;
  }

  :deep(.el-table .el-dropdown) {
    font-size: 12px;
  }

  /* 操作按钮仅显示图标 */
  .action-buttons {
    gap: 4px;
  }

  .action-btn {
    min-width: 32px;
    padding: 4px 8px;
  }

  .mobile-more-action .el-button {
    min-width: auto;
    padding: 4px 8px;
  }

  /* 进一步减小操作列宽度 */
  :deep(.action-column) {
    min-width: 100px !important;
  }

  /* 分页器超小屏优化 */
  :deep(.el-pagination) {
    font-size: 12px;
  }

  :deep(.el-pagination .btn-prev),
  :deep(.el-pagination .btn-next),
  :deep(.el-pagination .el-pager li) {
    min-width: 28px;
    height: 28px;
    line-height: 28px;
  }
}

/* ========== 暗色模式优化 ========== */
html.dark {
  /* 批量操作卡片在暗色模式下的渐变 */
  .batch-actions-card {
    border-color: var(--primary-gradient-end);
    background: color-mix(in srgb, var(--brand-primary) 5%, transparent);
  }

  /* 表格在暗色模式下的边框 */
  :deep(.el-table) {
    --el-table-border-color: var(--border-color);
  }

  /* 悬停效果增强 */
  .stat-card:hover {
    box-shadow: 0 8px 24px color-mix(in srgb, var(--brand-primary) 30%, transparent);
  }
}

/* ========== 打印样式 ========== */
@media print {
  .aqks-study-management {
    padding: 0;
  }

  .header-actions,
  .batch-actions-card,
  .filter-actions,
  .pagination-wrapper,
  .el-table__column--selection,
  .el-table__column:last-child {
    display: none !important;
  }

  .stat-card,
  .order-list-card {
    box-shadow: none;
    border: 1px solid #ddd;
  }
}
</style>
