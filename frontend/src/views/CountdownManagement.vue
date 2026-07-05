<template>
  <div class="countdown-management">
    <!-- 页面标题 -->
    <el-card class="page-header">
      <div class="header-content">
        <div>
          <h2>倒计时管理</h2>
          <p>管理自营订单的倒计时、考试倒计时和自动完成功能</p>
        </div>
        <div class="header-actions">
          <el-button
            type="primary"
            size="small"
            @click="showColumnConfigDialog"
          >
            <el-icon><Setting /></el-icon>
            列设置
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 统计信息 -->
    <el-row :gutter="20" class="statistics-cards">
      <el-col :span="4">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-value">{{ statistics.activeCountdown || 0 }}</div>
            <div class="stat-label">进行中倒计时</div>
          </div>
          <el-icon class="stat-icon"><Clock /></el-icon>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-value">
              {{ statistics.activeExamCountdown || 0 }}
            </div>
            <div class="stat-label">考试中倒计时</div>
          </div>
          <el-icon class="stat-icon exam"><School /></el-icon>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-value">{{ statistics.expiredCount || 0 }}</div>
            <div class="stat-label">已过期订单</div>
          </div>
          <el-icon class="stat-icon warning"><Warning /></el-icon>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-value">
              {{ statistics.autoCompleteCount || 0 }}
            </div>
            <div class="stat-label">自动完成</div>
          </div>
          <el-icon class="stat-icon success"><Check /></el-icon>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-value">
              {{ statistics.manualCompleteCount || 0 }}
            </div>
            <div class="stat-label">手动完成</div>
          </div>
          <el-icon class="stat-icon info"><User /></el-icon>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-value">
              {{ statistics.examCompletedCount || 0 }}
            </div>
            <div class="stat-label">考试完成</div>
          </div>
          <el-icon class="stat-icon exam-success"><CircleCheck /></el-icon>
        </el-card>
      </el-col>
    </el-row>

    <!-- 操作栏 -->
    <el-card class="operation-card">
      <div class="operation-bar">
        <!-- 移动端使用折叠面板 -->
        <div v-if="isMobile" class="mobile-operation">
          <el-collapse v-model="activeCollapseItems">
            <el-collapse-item title="操作按钮" name="actions">
              <div class="mobile-actions">
                <el-button
                  type="primary"
                  size="small"
                  @click="loadCountdownOrders"
                >
                  <el-icon><Refresh /></el-icon>
                  刷新
                </el-button>
                <el-button size="small" @click="startAutoRefresh">
                  <el-icon><Timer /></el-icon>
                  {{ autoRefresh ? "停止" : "自动刷新" }}
                </el-button>
                <el-button
                  type="success"
                  size="small"
                  @click="showConfigDialog"
                >
                  <el-icon><Setting /></el-icon>
                  配置
                </el-button>
                <el-button
                  type="warning"
                  size="small"
                  @click="showExamConfigDialog"
                >
                  <el-icon><School /></el-icon>
                  考试
                </el-button>
                <el-button
                  type="warning"
                  size="small"
                  @click="showBatchOperationDialog"
                  :disabled="selectedOrders.length === 0"
                >
                  <el-icon><Operation /></el-icon>
                  批量({{ selectedOrders.length }})
                </el-button>
                <el-button type="info" size="small" @click="showHistoryDialog">
                  <el-icon><Clock /></el-icon>
                  历史
                </el-button>
              </div>
            </el-collapse-item>
            <el-collapse-item title="筛选条件" name="filters">
              <div class="mobile-filters">
                <el-select
                  v-model="filters.status"
                  placeholder="状态"
                  clearable
                  size="small"
                  @change="loadCountdownOrders"
                >
                  <el-option label="全部" value="" />
                  <el-option label="进行中" value="processing" />
                  <el-option label="考试中" value="exam_processing" />
                  <el-option label="待考试" value="exam_pending" />
                  <el-option label="即将过期" value="expiring" />
                  <el-option label="已过期" value="expired" />
                  <el-option label="考试过期" value="exam_expired" />
                </el-select>
                <el-select
                  v-model="filters.countdownType"
                  placeholder="类型"
                  clearable
                  size="small"
                  @change="loadCountdownOrders"
                >
                  <el-option label="全部" value="" />
                  <el-option label="普通倒计时" value="normal" />
                  <el-option label="考试倒计时" value="exam" />
                </el-select>
                <el-input
                  v-model="filters.studentAccount"
                  placeholder="账户"
                  clearable
                  size="small"
                  @change="handleStudentAccountFilter"
                />
                <el-button
                  type="primary"
                  size="small"
                  @click="showJumpToRowDialog"
                >
                  <el-icon><ArrowDown /></el-icon>
                  跳转
                </el-button>
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>

        <!-- 桌面端布局 -->
        <template v-else>
          <div class="left-actions">
            <el-button type="primary" @click="loadCountdownOrders">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
            <el-button @click="startAutoRefresh">
              <el-icon><Timer /></el-icon>
              {{ autoRefresh ? "停止自动刷新" : "开始自动刷新" }}
            </el-button>
            <el-button type="success" @click="showConfigDialog">
              <el-icon><Setting /></el-icon>
              倒计时配置
            </el-button>
            <el-button type="warning" @click="showExamConfigDialog">
              <el-icon><School /></el-icon>
              考试配置
            </el-button>
            <el-button
              type="warning"
              @click="showBatchOperationDialog"
              :disabled="selectedOrders.length === 0"
            >
              <el-icon><Operation /></el-icon>
              批量操作({{ selectedOrders.length }})
            </el-button>
            <el-button type="info" @click="showHistoryDialog">
              <el-icon><Clock /></el-icon>
              历史记录
            </el-button>
          </div>
          <div class="right-filters">
            <el-select
              v-model="filters.status"
              placeholder="筛选状态"
              clearable
              @change="loadCountdownOrders"
              style="margin-right: 10px"
            >
              <el-option label="全部" value="" />
              <el-option label="进行中" value="processing" />
              <el-option label="考试中" value="exam_processing" />
              <el-option label="待考试" value="exam_pending" />
              <el-option label="即将过期" value="expiring" />
              <el-option label="已过期" value="expired" />
              <el-option label="考试过期" value="exam_expired" />
            </el-select>
            <el-select
              v-model="filters.countdownType"
              placeholder="倒计时类型"
              clearable
              @change="loadCountdownOrders"
              style="margin-right: 10px"
            >
              <el-option label="全部" value="" />
              <el-option label="普通倒计时" value="normal" />
              <el-option label="考试倒计时" value="exam" />
            </el-select>
            <el-input
              v-model="filters.studentAccount"
              placeholder="账户"
              clearable
              @change="handleStudentAccountFilter"
              style="width: 180px"
            />
            <el-button
              type="primary"
              size="small"
              @click="showJumpToRowDialog"
              style="margin-left: 10px"
            >
              <el-icon><ArrowDown /></el-icon>
              跳转到行
            </el-button>
          </div>
        </template>
      </div>
    </el-card>

    <!-- 倒计时订单列表 -->
    <el-card class="table-card">
      <el-table
        ref="tableRef"
        :data="sortedOrders"
        v-loading="loading"
        stripe
        style="width: 100%"
        @selection-change="handleSelectionChange"
        :default-sort="{ prop: tableSort.prop, order: tableSort.order }"
        @sort-change="handleSortChange"
      >
        <el-table-column type="selection" width="55" fixed="left" />
        <el-table-column
          v-for="column in visibleColumns"
          :key="column.prop"
          :prop="column.prop"
          :label="column.label"
          :width="column.width"
          :min-width="column.minWidth"
          :sortable="column.sortable ? 'custom' : false"
          :fixed="column.fixed"
        >
          <template #default="{ row }" v-if="column.prop === 'countdownStatus'">
            <el-tag :type="getCountdownStatusType(row)">
              {{ getCountdownStatusText(row) }}
            </el-tag>
          </template>
          <template
            #default="{ row }"
            v-else-if="column.prop === 'remainingTime'"
          >
            <div class="countdown-display">
              <span
                v-if="row.remainingMinutes > 0"
                class="countdown-time"
                :class="getCountdownTimeClass(row)"
              >
                {{ formatTime(row.remainingMinutes) }}
              </span>
              <span v-else class="expired-text">已过期</span>
            </div>
          </template>
          <template
            #default="{ row }"
            v-else-if="column.prop === 'autoCompleteEnabled'"
          >
            <el-tag :type="row.autoCompleteEnabled ? 'success' : 'info'">
              {{ row.autoCompleteEnabled ? "是" : "否" }}
            </el-tag>
          </template>
          <template
            #default="{ row }"
            v-else-if="column.prop === 'countdownStartTime'"
          >
            {{ formatDateTime(row.countdownStartTime) }}
          </template>
          <template
            #default="{ row }"
            v-else-if="column.prop === 'countdownEndTime'"
          >
            {{ formatDateTime(row.countdownEndTime) }}
          </template>
          <template #default="{ row }" v-else-if="column.prop === 'operations'">
            <div class="operation-buttons">
              <!-- 进行中的订单操作 -->
              <div v-if="row.remainingMinutes > 0" class="primary-actions">
                <el-button size="small" @click="showAdjustDialog(row)"
                  >调整倒计时</el-button
                >
                <el-button
                  size="small"
                  type="success"
                  @click="completeOrder(row)"
                  >完成订单</el-button
                >
              </div>
              <!-- 已过期的订单操作 -->
              <div v-else class="expired-actions">
                <el-button
                  size="small"
                  type="primary"
                  @click="showStartExamDialog(row)"
                  >开始考试</el-button
                >
                <el-button
                  size="small"
                  type="success"
                  @click="completeOrder(row)"
                  >完成订单</el-button
                >
                <el-button
                  size="small"
                  type="warning"
                  @click="showStatusSwitchDialog(row)"
                  >切换状态</el-button
                >
              </div>
              <div class="secondary-actions">
                <el-button
                  size="small"
                  type="warning"
                  @click="showOrderDetail(row)"
                  >查看详情</el-button
                >
              </div>
            </div>
          </template>
          <template #default="{ row }" v-else>
            {{ row[column.prop] }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 调整倒计时对话框 -->
    <el-dialog
      v-model="adjustDialogVisible"
      title="调整倒计时"
      width="400px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form :model="adjustForm" label-width="100px">
        <el-form-item label="订单号">
          <el-input v-model="adjustForm.orderNo" disabled />
        </el-form-item>
        <el-form-item label="当前时长">
          <el-input v-model="adjustForm.currentDuration" disabled />
        </el-form-item>
        <el-form-item label="新时长(分钟)" required>
          <el-input-number
            v-model="adjustForm.newDuration"
            :min="1"
            :max="1440"
            placeholder="请输入新的倒计时时长"
          />
        </el-form-item>
        <el-form-item label="调整原因">
          <el-input
            v-model="adjustForm.reason"
            type="textarea"
            placeholder="请输入调整原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAdjust">确定</el-button>
      </template>
    </el-dialog>

    <!-- 完成订单对话框 -->
    <el-dialog
      v-model="completeDialogVisible"
      title="完成订单"
      width="400px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form :model="completeForm" label-width="100px">
        <el-form-item label="订单号">
          <el-input v-model="completeForm.orderNo" disabled />
        </el-form-item>
        <el-form-item label="课程名称">
          <el-input v-model="completeForm.courseName" disabled />
        </el-form-item>
        <el-form-item label="完成原因">
          <el-input
            v-model="completeForm.reason"
            type="textarea"
            placeholder="请输入完成原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitComplete">确定完成</el-button>
      </template>
    </el-dialog>

    <!-- 重新开始倒计时对话框 -->
    <el-dialog
      v-model="restartDialogVisible"
      title="重新开始倒计时"
      width="500px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form :model="restartForm" label-width="100px">
        <el-form-item label="订单号">
          <el-input v-model="restartForm.orderNo" disabled />
        </el-form-item>
        <el-form-item label="课程名称">
          <el-input v-model="restartForm.courseName" disabled />
        </el-form-item>
        <el-form-item label="倒计时时长(分钟)" required>
          <el-input-number
            v-model="restartForm.duration"
            :min="1"
            :max="1440"
            placeholder="请输入倒计时时长"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="操作原因">
          <el-input
            v-model="restartForm.reason"
            type="textarea"
            placeholder="请输入操作原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="restartDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="submitRestartCountdown"
          :loading="restartLoading"
          >确定</el-button
        >
      </template>
    </el-dialog>

    <!-- 切换状态对话框 -->
    <el-dialog
      v-model="statusSwitchDialogVisible"
      title="切换订单状态"
      width="500px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form :model="statusSwitchForm" label-width="100px">
        <el-form-item label="订单号">
          <el-input v-model="statusSwitchForm.orderNo" disabled />
        </el-form-item>
        <el-form-item label="课程名称">
          <el-input v-model="statusSwitchForm.courseName" disabled />
        </el-form-item>
        <el-form-item label="新状态" required>
          <el-select
            v-model="statusSwitchForm.newStatus"
            placeholder="请选择新状态"
            style="width: 100%"
          >
            <el-option label="待处理" :value="0" />
            <el-option label="进行中" :value="1" />
            <el-option label="已完成" :value="2" />
            <el-option label="已取消" :value="3" />
            <el-option label="失败" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作原因">
          <el-input
            v-model="statusSwitchForm.reason"
            type="textarea"
            placeholder="请输入操作原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusSwitchDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="submitStatusSwitch"
          :loading="statusSwitchLoading"
          >确定</el-button
        >
      </template>
    </el-dialog>

    <!-- 开始考试倒计时对话框 -->
    <el-dialog
      v-model="startExamDialogVisible"
      title="开始考试倒计时"
      width="500px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form :model="startExamForm" label-width="100px">
        <el-form-item label="订单号">
          <el-input v-model="startExamForm.orderNo" disabled />
        </el-form-item>
        <el-form-item label="课程名称">
          <el-input v-model="startExamForm.courseName" disabled />
        </el-form-item>
        <el-form-item label="考试倒计时时长(分钟)" required>
          <el-input-number
            v-model="startExamForm.duration"
            :min="1"
            :max="1440"
            placeholder="请输入考试倒计时时长"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="启用自动完成">
          <el-switch
            v-model="startExamForm.autoCompleteEnabled"
            :active-value="true"
            :inactive-value="false"
          />
          <span class="form-tip">考试倒计时结束后是否自动完成</span>
        </el-form-item>
        <el-form-item label="操作原因">
          <el-input
            v-model="startExamForm.reason"
            type="textarea"
            placeholder="请输入操作原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startExamDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="submitStartExam"
          :loading="startExamLoading"
          >确定</el-button
        >
      </template>
    </el-dialog>

    <!-- 下一步任务倒计时对话框 -->
    <el-dialog
      v-model="nextTaskDialogVisible"
      title="开始下一步任务倒计时"
      width="500px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form :model="nextTaskForm" label-width="100px">
        <el-form-item label="订单号">
          <el-input v-model="nextTaskForm.orderNo" disabled />
        </el-form-item>
        <el-form-item label="课程名称">
          <el-input v-model="nextTaskForm.courseName" disabled />
        </el-form-item>
        <el-form-item label="下一步任务倒计时时长(分钟)" required>
          <el-input-number
            v-model="nextTaskForm.duration"
            :min="1"
            :max="1440"
            placeholder="请输入倒计时时长"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="启用自动完成">
          <el-switch
            v-model="nextTaskForm.autoCompleteEnabled"
            :active-value="true"
            :inactive-value="false"
          />
          <span class="form-tip">倒计时结束后是否自动完成订单</span>
        </el-form-item>
        <el-form-item
          label="自动完成状态"
          v-if="nextTaskForm.autoCompleteEnabled"
        >
          <el-select
            v-model="nextTaskForm.autoCompleteStatus"
            placeholder="请选择自动完成状态"
            style="width: 100%"
          >
            <el-option label="待处理" :value="0" />
            <el-option label="进行中" :value="1" />
            <el-option label="已完成" :value="2" />
            <el-option label="已取消" :value="3" />
            <el-option label="失败" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务描述">
          <el-input
            v-model="nextTaskForm.taskDescription"
            type="textarea"
            placeholder="请描述下一步任务内容"
          />
        </el-form-item>
        <el-form-item label="操作原因">
          <el-input
            v-model="nextTaskForm.reason"
            type="textarea"
            placeholder="请输入操作原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nextTaskDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="submitNextTaskCountdown"
          :loading="nextTaskLoading"
          >确定</el-button
        >
      </template>
    </el-dialog>

    <!-- 倒计时配置对话框 -->
    <el-dialog
      v-model="configDialogVisible"
      title="倒计时配置"
      width="700px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form :model="configForm" label-width="200px" class="config-form">
        <el-form-item label="配置ID" v-if="configForm.id">
          <el-input v-model="configForm.id" disabled />
        </el-form-item>

        <el-form-item label="默认倒计时时长（分钟）">
          <el-input-number
            v-model="configForm.default_countdown_duration"
            :min="1"
            :max="1440"
            placeholder="请输入默认倒计时时长"
            style="width: 200px"
          />
          <span class="form-tip">设置状态切换时的默认倒计时时长</span>
        </el-form-item>

        <el-form-item label="倒计时结束后自动跳转状态">
          <el-select
            v-model="configForm.auto_complete_status"
            placeholder="请选择自动完成状态"
            style="width: 200px"
          >
            <el-option
              v-for="option in orderStatusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <span class="form-tip">倒计时结束后订单自动跳转到的状态</span>
        </el-form-item>

        <el-form-item label="启用自动完成功能">
          <el-switch
            v-model="configForm.auto_complete_enabled"
            :active-value="1"
            :inactive-value="0"
          />
          <span class="form-tip">是否启用倒计时结束后自动完成功能</span>
        </el-form-item>

        <el-form-item label="倒计时警告时间（分钟）">
          <el-input-number
            v-model="configForm.countdown_warning_time"
            :min="1"
            :max="60"
            placeholder="请输入警告时间"
            style="width: 200px"
          />
          <span class="form-tip">剩余时间少于此时长时显示警告</span>
        </el-form-item>

        <el-divider />

        <el-form-item label="配置创建时间" v-if="configForm.createTime">
          <el-input v-model="configForm.createTime" disabled />
        </el-form-item>

        <el-form-item label="最后更新时间" v-if="configForm.updateTime">
          <el-input v-model="configForm.updateTime" disabled />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="configDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveConfig" :loading="saving"
          >保存配置</el-button
        >
      </template>
    </el-dialog>

    <!-- 考试倒计时配置对话框 -->
    <el-dialog
      v-model="examConfigDialogVisible"
      title="考试倒计时配置"
      width="700px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form :model="examConfigForm" label-width="200px" class="config-form">
        <el-form-item label="配置ID" v-if="examConfigForm.id">
          <el-input v-model="examConfigForm.id" disabled />
        </el-form-item>

        <el-form-item label="默认考试倒计时时长（分钟）">
          <el-input-number
            v-model="examConfigForm.default_exam_countdown_duration"
            :min="1"
            :max="1440"
            placeholder="请输入默认考试倒计时时长"
            style="width: 200px"
          />
          <span class="form-tip">设置考试倒计时时的默认时长</span>
        </el-form-item>

        <el-form-item label="考试倒计时结束后自动跳转状态">
          <el-select
            v-model="examConfigForm.exam_auto_complete_status"
            placeholder="请选择自动完成状态"
            style="width: 200px"
          >
            <el-option
              v-for="option in orderStatusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <span class="form-tip">考试倒计时结束后订单自动跳转到的状态</span>
        </el-form-item>

        <el-form-item label="启用考试自动完成功能">
          <el-switch
            v-model="examConfigForm.exam_auto_complete_enabled"
            :active-value="1"
            :inactive-value="0"
          />
          <span class="form-tip">是否启用考试倒计时结束后自动完成功能</span>
        </el-form-item>

        <el-form-item label="考试倒计时警告时间（分钟）">
          <el-input-number
            v-model="examConfigForm.exam_countdown_warning_time"
            :min="1"
            :max="60"
            placeholder="请输入警告时间"
            style="width: 200px"
          />
          <span class="form-tip">剩余时间少于此时长时显示警告</span>
        </el-form-item>

        <el-divider />

        <el-form-item label="配置创建时间" v-if="examConfigForm.createTime">
          <el-input v-model="examConfigForm.createTime" disabled />
        </el-form-item>

        <el-form-item label="最后更新时间" v-if="examConfigForm.updateTime">
          <el-input v-model="examConfigForm.updateTime" disabled />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="examConfigDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveExamConfig" :loading="examSaving"
          >保存配置</el-button
        >
      </template>
    </el-dialog>

    <!-- 列配置对话框 -->
    <el-dialog
      v-model="columnConfigDialogVisible"
      title="列配置"
      width="600px"
      append-to-body
      :close-on-click-modal="false"
    >
      <div class="column-config-content">
        <el-alert
          title="提示"
          description="拖动列项可以调整列的显示顺序，勾选复选框控制列的显示/隐藏"
          type="info"
          :closable="false"
          style="margin-bottom: 15px"
        />

        <div ref="columnListRef" class="column-list">
          <div
            v-for="column in allColumns"
            :key="column.prop"
            class="column-item"
            :data-prop="column.prop"
          >
            <div class="column-item-left">
              <el-icon class="drag-handle"><ArrowDown /></el-icon>
              <el-checkbox
                v-model="column.visible"
                :disabled="column.required"
                @change="handleColumnVisibilityChange"
              >
                {{ column.label }}
              </el-checkbox>
            </div>
            <div class="column-item-right">
              <el-tag v-if="column.required" size="small" type="info"
                >必需</el-tag
              >
              <el-tag v-if="column.sortable" size="small" type="success"
                >可排序</el-tag
              >
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="resetColumnConfig">重置</el-button>
        <el-button @click="columnConfigDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="saveColumnConfigData"
          :loading="columnSaving"
          >保存配置</el-button
        >
      </template>
    </el-dialog>

    <!-- 批量操作对话框 -->
    <el-dialog
      v-model="batchDialogVisible"
      title="批量操作"
      width="500px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form :model="batchForm" label-width="100px">
        <el-form-item label="操作类型" required>
          <el-select
            v-model="batchForm.operationType"
            placeholder="请选择操作类型"
            style="width: 100%"
          >
            <el-option label="完成订单" value="complete" />
            <el-option label="调整倒计时" value="adjust" />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="batchForm.operationType === 'adjust'"
          label="新时长(分钟)"
          required
        >
          <el-input-number
            v-model="batchForm.newDuration"
            :min="1"
            :max="1440"
            placeholder="请输入新的倒计时时长"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="操作原因">
          <el-input
            v-model="batchForm.reason"
            type="textarea"
            placeholder="请输入操作原因"
          />
        </el-form-item>
        <el-form-item label="选中订单">
          <div class="selected-orders">
            <el-tag
              v-for="order in selectedOrders"
              :key="order.id"
              class="order-tag"
            >
              {{ order.orderNo }}
            </el-tag>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="submitBatchOperation"
          :loading="batchLoading"
          >确定</el-button
        >
      </template>
    </el-dialog>

    <!-- 订单详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="订单详情"
      width="800px"
      append-to-body
      :close-on-click-modal="false"
    >
      <div v-if="selectedOrder" class="order-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{
            selectedOrder.orderNo
          }}</el-descriptions-item>
          <el-descriptions-item label="平台">{{
            selectedOrder.platformName
          }}</el-descriptions-item>
          <el-descriptions-item label="课程名称">{{
            selectedOrder.courseName
          }}</el-descriptions-item>
          <el-descriptions-item label="学生账号">{{
            selectedOrder.studentAccount
          }}</el-descriptions-item>
          <el-descriptions-item label="订单状态">
            <el-tag :type="getOrderStatusType(selectedOrder.orderStatus)">
              {{ getOrderStatusText(selectedOrder.orderStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="倒计时状态">
            <el-tag :type="getCountdownStatusType(selectedOrder)">
              {{ getCountdownStatusText(selectedOrder) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="剩余时间">
            <span
              v-if="selectedOrder.remainingMinutes > 0"
              class="countdown-time"
            >
              {{ formatTime(selectedOrder.remainingMinutes) }}
            </span>
            <span v-else class="expired-text">已过期</span>
          </el-descriptions-item>
          <el-descriptions-item label="总时长"
            >{{ selectedOrder.countdownDuration }}分钟</el-descriptions-item
          >
          <el-descriptions-item label="开始时间">{{
            formatDateTime(selectedOrder.countdownStartTime)
          }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{
            formatDateTime(selectedOrder.countdownEndTime)
          }}</el-descriptions-item>
          <el-descriptions-item label="自动完成">
            <el-tag
              :type="selectedOrder.autoCompleteEnabled ? 'success' : 'info'"
            >
              {{ selectedOrder.autoCompleteEnabled ? "是" : "否" }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{
            formatDateTime(selectedOrder.createTime)
          }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{
            selectedOrder.remarks || "无"
          }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 历史记录对话框 -->
    <el-dialog
      v-model="historyDialogVisible"
      title="倒计时历史记录"
      width="1200px"
      append-to-body
      :close-on-click-modal="false"
    >
      <div class="history-content">
        <!-- 高级筛选 -->
        <el-card class="filter-card" style="margin-bottom: 20px">
          <template #header>
            <div class="card-header">
              <span>高级筛选</span>
              <el-button type="primary" size="small" @click="applyHistoryFilter"
                >应用筛选</el-button
              >
            </div>
          </template>
          <el-row :gutter="20">
            <el-col :span="6">
              <el-form-item label="账号">
                <el-input
                  v-model="historyFilter.studentAccount"
                  placeholder="请输入账号"
                  clearable
                />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="操作类型">
                <el-select
                  v-model="historyFilter.operationType"
                  placeholder="请选择操作类型"
                  clearable
                >
                  <el-option label="全部" value="" />
                  <el-option label="开始倒计时" value="start" />
                  <el-option label="调整倒计时" value="adjust" />
                  <el-option label="完成订单" value="complete" />
                  <el-option label="自动完成" value="auto_complete" />
                  <el-option label="过期" value="expired" />
                  <el-option label="重新开始" value="restart" />
                  <el-option label="状态切换" value="status_switch" />
                  <el-option label="下一步任务" value="next_task_start" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="操作人">
                <el-input
                  v-model="historyFilter.operatorName"
                  placeholder="请输入操作人"
                  clearable
                />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="时间范围">
                <el-date-picker
                  v-model="historyFilter.dateRange"
                  type="datetimerange"
                  range-separator="至"
                  start-placeholder="开始时间"
                  end-placeholder="结束时间"
                  format="YYYY-MM-DD HH:mm:ss"
                  value-format="YYYY-MM-DD HH:mm:ss"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </el-card>

        <el-tabs
          v-model="activeHistoryTab"
          @tab-change="handleHistoryTabChange"
        >
          <el-tab-pane label="订单历史" name="order">
            <div v-if="selectedOrder" class="order-history-section">
              <h4>订单：{{ selectedOrder.orderNo }}</h4>
              <el-table :data="orderHistory" v-loading="historyLoading" stripe>
                <el-table-column
                  prop="operationType"
                  label="操作类型"
                  width="120"
                >
                  <template #default="{ row }">
                    <el-tag :type="getOperationTypeTag(row.operationType)">
                      {{ getOperationTypeText(row.operationType) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column
                  prop="oldDuration"
                  label="操作前时长"
                  width="120"
                >
                  <template #default="{ row }">
                    {{ row.oldDuration ? row.oldDuration + "分钟" : "-" }}
                  </template>
                </el-table-column>
                <el-table-column
                  prop="newDuration"
                  label="操作后时长"
                  width="120"
                >
                  <template #default="{ row }">
                    {{ row.newDuration ? row.newDuration + "分钟" : "-" }}
                  </template>
                </el-table-column>
                <el-table-column
                  prop="reason"
                  label="操作原因"
                  min-width="200"
                />
                <el-table-column
                  prop="operatorName"
                  label="操作人"
                  width="120"
                />
                <el-table-column prop="createTime" label="操作时间" width="160">
                  <template #default="{ row }">
                    {{ formatDateTime(row.createTime) }}
                  </template>
                </el-table-column>
              </el-table>
            </div>
            <div v-else class="no-selection">
              <el-empty description="请先选择一个订单查看历史记录" />
            </div>
          </el-tab-pane>
          <el-tab-pane label="全部历史" name="all">
            <el-table :data="allHistory" v-loading="historyLoading" stripe>
              <el-table-column prop="orderNo" label="订单号" width="150" />
              <el-table-column prop="username" label="账号" width="120" />
              <el-table-column
                prop="orderStatusText"
                label="订单状态"
                width="100"
              >
                <template #default="{ row }">
                  <el-tag :type="getOrderStatusTag(row.orderStatus)">
                    {{ row.orderStatusText }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column
                prop="operationType"
                label="操作类型"
                width="120"
              >
                <template #default="{ row }">
                  <el-tag :type="getOperationTypeTag(row.operationType)">
                    {{ getOperationTypeText(row.operationType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column
                prop="oldDuration"
                label="操作前时长"
                width="120"
              >
                <template #default="{ row }">
                  {{ row.oldDuration ? row.oldDuration + "分钟" : "-" }}
                </template>
              </el-table-column>
              <el-table-column
                prop="newDuration"
                label="操作后时长"
                width="120"
              >
                <template #default="{ row }">
                  {{ row.newDuration ? row.newDuration + "分钟" : "-" }}
                </template>
              </el-table-column>
              <el-table-column prop="reason" label="操作原因" min-width="200" />
              <el-table-column prop="operatorName" label="操作人" width="120" />
              <el-table-column prop="createTime" label="操作时间" width="160">
                <template #default="{ row }">
                  {{ formatDateTime(row.createTime) }}
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
      <template #footer>
        <el-button @click="historyDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 跳转到行对话框 -->
    <el-dialog
      v-model="jumpToRowDialogVisible"
      title="跳转到指定行"
      width="400px"
      :close-on-click-modal="false"
    >
      <el-form :model="jumpToRowForm" label-width="100px">
        <el-form-item label="账号">
          <el-input
            v-model="jumpToRowForm.studentAccount"
            placeholder="请输入账号"
            clearable
            @keyup.enter="handleJumpToRow"
          />
        </el-form-item>
        <el-form-item label="或选择行号">
          <el-input-number
            v-model="jumpToRowForm.rowIndex"
            :min="1"
            :max="sortedOrders.length"
            placeholder="请输入行号"
            style="width: 100%"
            @keyup.enter="handleJumpToRowByIndex"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="jumpToRowDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleJumpToRow">跳转</el-button>
        <el-button type="success" @click="handleJumpToRowByIndex"
          >按行号跳转</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed, nextTick } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Clock,
  Warning,
  Check,
  User,
  Refresh,
  Timer,
  Setting,
  Operation,
  ArrowDown,
  School,
  CircleCheck,
} from "@element-plus/icons-vue";
import Sortable from "sortablejs";
import {
  getActiveCountdownOrders,
  getRemainingCountdown,
  completeOrder as completeOrderApi,
  adjustCountdown,
  batchCountdownOperation,
  getCountdownHistory,
  getAllCountdownHistory,
  getAllCountdownHistoryWithDetails,
  startNextTaskCountdown,
  getActiveExamCountdownOrders,
  getRemainingExamCountdown,
  completeExam,
  startExamCountdown,
  adjustExamCountdown,
} from "@/api/order";
import {
  getCountdownConfigs,
  updateCountdownConfigs,
  getColumnConfig,
  saveColumnConfig,
  getExamCountdownConfigs,
  updateExamCountdownConfigs,
} from "@/api/countdownConfig";
import { useVariableStore } from "@/stores/variableStore";

// 响应式数据
const loading = ref(false);
const countdownOrders = ref([]);
const autoRefresh = ref(false);
const refreshTimer = ref(null);
const countdownTimer = ref(null); // 实时倒计时定时器
const tableRef = ref(null);
const activeCollapseItems = ref(["actions", "filters"]); // 默认展开操作和筛选面板

// 表格排序
const tableSort = reactive({
  prop: "",
  order: "",
});

// 计算排序后的订单列表
const sortedOrders = computed(() => {
  if (!tableSort.prop || !tableSort.order) {
    return countdownOrders.value;
  }

  const orders = [...countdownOrders.value];
  const direction = tableSort.order === "ascending" ? 1 : -1;

  return orders.sort((a, b) => {
    let aVal = a[tableSort.prop];
    let bVal = b[tableSort.prop];

    // 处理特殊字段
    if (tableSort.prop === "remainingTime") {
      aVal = a.remainingMinutes || 0;
      bVal = b.remainingMinutes || 0;
    }

    if (typeof aVal === "number" && typeof bVal === "number") {
      return (aVal - bVal) * direction;
    }

    if (typeof aVal === "string" && typeof bVal === "string") {
      return aVal.localeCompare(bVal) * direction;
    }

    return 0;
  });
});

// 处理排序变化
const handleSortChange = ({ prop, order }) => {
  tableSort.prop = prop;
  tableSort.order = order;
};

// 列配置
const columnConfigDialogVisible = ref(false);
const columnSaving = ref(false);
const columnListRef = ref(null);
let sortableInstance = null;

// 所有可用的列定义
const allColumns = ref([
  {
    id: "col_order_no",
    prop: "orderNo",
    label: "订单号",
    width: 150,
    visible: true,
    required: true,
    sortable: true,
    order: 1,
  },
  {
    id: "col_platform",
    prop: "platformName",
    label: "平台",
    width: 120,
    visible: true,
    sortable: true,
    order: 2,
  },
  {
    id: "col_course",
    prop: "courseName",
    label: "课程名称",
    minWidth: 200,
    visible: true,
    sortable: true,
    order: 3,
  },
  {
    id: "col_student",
    prop: "studentAccount",
    label: "学生账号",
    width: 120,
    visible: true,
    sortable: true,
    order: 4,
  },
  {
    id: "col_status",
    prop: "countdownStatus",
    label: "倒计时状态",
    width: 120,
    visible: true,
    sortable: false,
    order: 5,
  },
  {
    id: "col_remaining",
    prop: "remainingTime",
    label: "剩余时间",
    width: 150,
    visible: true,
    sortable: true,
    order: 6,
  },
  {
    id: "col_duration",
    prop: "countdownDuration",
    label: "总时长(分钟)",
    width: 120,
    visible: true,
    sortable: true,
    order: 7,
  },
  {
    id: "col_auto_complete",
    prop: "autoCompleteEnabled",
    label: "自动完成",
    width: 100,
    visible: true,
    sortable: true,
    order: 8,
  },
  {
    id: "col_start_time",
    prop: "countdownStartTime",
    label: "开始时间",
    width: 160,
    visible: true,
    sortable: true,
    order: 9,
  },
  {
    id: "col_end_time",
    prop: "countdownEndTime",
    label: "结束时间",
    width: 160,
    visible: true,
    sortable: true,
    order: 10,
  },
  {
    id: "col_operations",
    prop: "operations",
    label: "操作",
    width: 250,
    visible: true,
    required: true,
    fixed: "right",
    sortable: false,
    order: 11,
  },
]);

// 计算可见的列
const visibleColumns = computed(() => {
  return allColumns.value
    .filter((col) => col.visible)
    .sort((a, b) => a.order - b.order);
});

// 显示列配置对话框
const showColumnConfigDialog = () => {
  columnConfigDialogVisible.value = true;
  nextTick(() => {
    initSortable();
  });
};

// 初始化拖动排序
const initSortable = () => {
  if (!columnListRef.value) return;

  if (sortableInstance) {
    sortableInstance.destroy();
  }

  sortableInstance = Sortable.create(columnListRef.value, {
    animation: 150,
    handle: ".drag-handle",
    ghostClass: "sortable-ghost",
    onEnd: (evt) => {
      const { oldIndex, newIndex } = evt;
      if (oldIndex !== newIndex) {
        const movedItem = allColumns.value.splice(oldIndex, 1)[0];
        allColumns.value.splice(newIndex, 0, movedItem);
        // 更新order
        allColumns.value.forEach((col, index) => {
          col.order = index + 1;
        });
      }
    },
  });
};

// 处理列可见性变化
const handleColumnVisibilityChange = () => {
  // 可以在这里添加额外的逻辑
};

// 重置列配置
const resetColumnConfig = () => {
  ElMessageBox.confirm("确定要重置列配置吗？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      // 恢复默认配置
      allColumns.value = [
        {
          id: "col_order_no",
          prop: "orderNo",
          label: "订单号",
          width: 150,
          visible: true,
          required: true,
          sortable: true,
          order: 1,
        },
        {
          id: "col_platform",
          prop: "platformName",
          label: "平台",
          width: 120,
          visible: true,
          sortable: true,
          order: 2,
        },
        {
          id: "col_course",
          prop: "courseName",
          label: "课程名称",
          minWidth: 200,
          visible: true,
          sortable: true,
          order: 3,
        },
        {
          id: "col_student",
          prop: "studentAccount",
          label: "学生账号",
          width: 120,
          visible: true,
          sortable: true,
          order: 4,
        },
        {
          id: "col_status",
          prop: "countdownStatus",
          label: "倒计时状态",
          width: 120,
          visible: true,
          sortable: false,
          order: 5,
        },
        {
          id: "col_remaining",
          prop: "remainingTime",
          label: "剩余时间",
          width: 150,
          visible: true,
          sortable: true,
          order: 6,
        },
        {
          id: "col_duration",
          prop: "countdownDuration",
          label: "总时长(分钟)",
          width: 120,
          visible: true,
          sortable: true,
          order: 7,
        },
        {
          id: "col_auto_complete",
          prop: "autoCompleteEnabled",
          label: "自动完成",
          width: 100,
          visible: true,
          sortable: true,
          order: 8,
        },
        {
          id: "col_start_time",
          prop: "countdownStartTime",
          label: "开始时间",
          width: 160,
          visible: true,
          sortable: true,
          order: 9,
        },
        {
          id: "col_end_time",
          prop: "countdownEndTime",
          label: "结束时间",
          width: 160,
          visible: true,
          sortable: true,
          order: 10,
        },
        {
          id: "col_operations",
          prop: "operations",
          label: "操作",
          width: 250,
          visible: true,
          required: true,
          fixed: "right",
          sortable: false,
          order: 11,
        },
      ];
      ElMessage.success("列配置已重置");
    })
    .catch(() => {
      // 取消重置
    });
};

// 保存列配置
const saveColumnConfigData = async () => {
  columnSaving.value = true;
  try {
    const config = {
      columns: allColumns.value.map((col) => ({
        id: col.id,
        prop: col.prop,
        visible: col.visible,
        order: col.order,
      })),
    };

    // 使用 localStorage 保存，如果后端支持可以改为 API 调用
    localStorage.setItem("countdown_column_config", JSON.stringify(config));

    // 如果后端支持，可以调用 API
    // await saveColumnConfig(config)

    ElMessage.success("列配置保存成功");
    columnConfigDialogVisible.value = false;
  } catch (error) {
    console.error("保存列配置失败:", error);
    ElMessage.error("保存列配置失败");
  } finally {
    columnSaving.value = false;
  }
};

// 加载列配置
const loadColumnConfigData = () => {
  try {
    const savedConfig = localStorage.getItem("countdown_column_config");
    if (savedConfig) {
      const config = JSON.parse(savedConfig);
      if (config.columns && Array.isArray(config.columns)) {
        // 合并保存的配置
        config.columns.forEach((savedCol) => {
          const column = allColumns.value.find((col) => col.id === savedCol.id);
          if (column) {
            column.visible = savedCol.visible;
            column.order = savedCol.order;
          }
        });
        // 按 order 排序
        allColumns.value.sort((a, b) => a.order - b.order);
      }
    }
  } catch (error) {
    console.error("加载列配置失败:", error);
  }
};

// 选择相关
const selectedOrders = ref([]);

// 批量操作相关
const batchDialogVisible = ref(false);
const batchLoading = ref(false);
const batchForm = reactive({
  operationType: "",
  newDuration: 60,
  reason: "",
});

// 订单详情相关
const detailDialogVisible = ref(false);
const selectedOrder = ref(null);

// 历史记录相关
const historyDialogVisible = ref(false);
const historyLoading = ref(false);
const activeHistoryTab = ref("order");
const orderHistory = ref([]);
const allHistory = ref([]);

// 历史记录筛选
const historyFilter = reactive({
  orderNo: "",
  operationType: "",
  operatorName: "",
  dateRange: null,
});

// 变量存储
const variableStore = useVariableStore();

// 配置相关
const configDialogVisible = ref(false);
const saving = ref(false);
const configForm = reactive({
  id: null,
  default_countdown_duration: 60,
  auto_complete_status: 2,
  auto_complete_enabled: 1,
  countdown_warning_time: 10,
  createTime: null,
  updateTime: null,
});

// 订单状态选项
const orderStatusOptions = ref([]);

// 统计信息
const statistics = reactive({
  activeCountdown: 0,
  activeExamCountdown: 0,
  expiredCount: 0,
  autoCompleteCount: 0,
  manualCompleteCount: 0,
  examCompletedCount: 0,
});

// 筛选条件
const filters = reactive({
  status: "",
  countdownType: "",
  studentAccount: "",
});

// 滚动到指定行的功能
const scrollToTargetRow = (targetOrderNo) => {
  if (!targetOrderNo) return;

  // 查找目标订单在排序后列表中的索引
  const targetIndex = sortedOrders.value.findIndex(
    (order) => order.orderNo === targetOrderNo
  );

  if (targetIndex !== -1 && tableRef.value) {
    // 使用Element Plus的scrollToRow方法滚动到指定行
    nextTick(() => {
      try {
        tableRef.value.scrollToRow(targetIndex);
        // 高亮显示目标行
        highlightTargetRow(targetIndex);
      } catch (error) {
        console.warn("滚动到指定行失败，使用备用方案:", error);
        // 备用方案：使用DOM滚动
        scrollToRowFallback(targetIndex);
      }
    });
  }
};

// 高亮显示目标行
const highlightTargetRow = (index) => {
  const tableBody = tableRef.value?.$el?.querySelector(
    ".el-table__body-wrapper tbody"
  );
  if (tableBody) {
    const rows = tableBody.querySelectorAll("tr");
    if (rows[index]) {
      // 添加高亮样式
      rows[index].classList.add("target-row-highlight");
      // 3秒后移除高亮
      setTimeout(() => {
        rows[index].classList.remove("target-row-highlight");
      }, 3000);
    }
  }
};

// 备用滚动方案
const scrollToRowFallback = (index) => {
  const tableBody = tableRef.value?.$el?.querySelector(
    ".el-table__body-wrapper"
  );
  if (tableBody) {
    const rows = tableBody.querySelectorAll("tbody tr");
    if (rows[index]) {
      rows[index].scrollIntoView({
        behavior: "smooth",
        block: "center",
      });
      highlightTargetRow(index);
    }
  }
};

// 处理学生账户筛选
const handleStudentAccountFilter = () => {
  loadCountdownOrders();
};

// 跳转到行对话框
const jumpToRowDialogVisible = ref(false);
const jumpToRowForm = reactive({
  studentAccount: "",
  rowIndex: 1,
});

// 显示跳转到行对话框
const showJumpToRowDialog = () => {
  jumpToRowForm.studentAccount = "";
  jumpToRowForm.rowIndex = 1;
  jumpToRowDialogVisible.value = true;
};

// 根据账号跳转到行
const handleJumpToRow = () => {
  if (!jumpToRowForm.studentAccount.trim()) {
    ElMessage.warning("请输入账号");
    return;
  }

  const targetOrder = sortedOrders.value.find((order) =>
    order.studentAccount
      .toLowerCase()
      .includes(jumpToRowForm.studentAccount.toLowerCase())
  );

  if (targetOrder) {
    scrollToTargetRow(targetOrder.studentAccount);
    jumpToRowDialogVisible.value = false;
    ElMessage.success(`已跳转到账号 ${targetOrder.studentAccount}`);
  } else {
    ElMessage.error("未找到匹配的账号");
  }
};

// 根据行号跳转
const handleJumpToRowByIndex = () => {
  if (!jumpToRowForm.rowIndex || jumpToRowForm.rowIndex < 1) {
    ElMessage.warning("请输入有效的行号");
    return;
  }

  const targetIndex = jumpToRowForm.rowIndex - 1; // 转换为0基索引
  if (targetIndex >= sortedOrders.value.length) {
    ElMessage.error(`行号超出范围，当前共有 ${sortedOrders.value.length} 行`);
    return;
  }

  const targetOrder = sortedOrders.value[targetIndex];
  if (targetOrder) {
    scrollToTargetRow(targetOrder.studentAccount);
    jumpToRowDialogVisible.value = false;
    ElMessage.success(
      `已跳转到第 ${jumpToRowForm.rowIndex} 行，账号 ${targetOrder.studentAccount}`
    );
  }
};

// 调整倒计时对话框
const adjustDialogVisible = ref(false);
const adjustForm = reactive({
  orderId: null,
  orderNo: "",
  currentDuration: 0,
  newDuration: 60,
  reason: "",
});

// 完成订单对话框
const completeDialogVisible = ref(false);
const completeForm = reactive({
  orderId: null,
  orderNo: "",
  courseName: "",
  reason: "",
});

// 重新开始倒计时对话框
const restartDialogVisible = ref(false);
const restartLoading = ref(false);
const restartForm = reactive({
  orderId: null,
  orderNo: "",
  courseName: "",
  duration: 60,
  reason: "",
});

// 切换状态对话框
const statusSwitchDialogVisible = ref(false);
const statusSwitchLoading = ref(false);
const statusSwitchForm = reactive({
  orderId: null,
  orderNo: "",
  courseName: "",
  newStatus: null,
  reason: "",
});

// 下一步任务倒计时对话框
const nextTaskDialogVisible = ref(false);
const nextTaskLoading = ref(false);
const nextTaskForm = reactive({
  orderId: null,
  orderNo: "",
  courseName: "",
  duration: 60,
  autoCompleteEnabled: true,
  autoCompleteStatus: 2, // 默认已完成
  taskDescription: "",
  reason: "",
});

// 开始考试倒计时对话框
const startExamDialogVisible = ref(false);
const startExamLoading = ref(false);
const startExamForm = reactive({
  orderId: null,
  orderNo: "",
  courseName: "",
  duration: 120,
  autoCompleteEnabled: true,
  reason: "",
});

// 考试倒计时配置对话框
const examConfigDialogVisible = ref(false);
const examSaving = ref(false);
const examConfigForm = reactive({
  id: null,
  default_exam_countdown_duration: 120,
  exam_auto_complete_status: 7,
  exam_auto_complete_enabled: 1,
  exam_countdown_warning_time: 15,
  createTime: null,
  updateTime: null,
});

// 加载倒计时订单
const loadCountdownOrders = async () => {
  loading.value = true;
  try {
    // 根据筛选条件决定加载哪种类型的订单
    let orders = [];
    if (filters.countdownType === "exam") {
      // 只加载考试倒计时订单
      const response = await getActiveExamCountdownOrders();
      orders = response.data || [];
    } else if (filters.countdownType === "normal") {
      // 只加载普通倒计时订单
      const response = await getActiveCountdownOrders();
      orders = response.data || [];
    } else {
      // 加载所有倒计时订单
      const [normalResponse, examResponse] = await Promise.all([
        getActiveCountdownOrders(),
        getActiveExamCountdownOrders(),
      ]);
      orders = [...(normalResponse.data || []), ...(examResponse.data || [])];
    }

    // 应用筛选条件
    if (filters.studentAccount) {
      orders = orders.filter((order) =>
        order.studentAccount
          .toLowerCase()
          .includes(filters.studentAccount.toLowerCase())
      );
    }

    // 应用状态筛选
    if (filters.status) {
      orders = orders.filter((order) => {
        switch (filters.status) {
          case "processing":
            return order.orderStatus === 1 && order.remainingMinutes > 0;
          case "exam_processing":
            return order.orderStatus === 6 && order.examRemainingMinutes > 0;
          case "exam_pending":
            return order.orderStatus === 5;
          case "expiring":
            return (
              (order.remainingMinutes > 0 && order.remainingMinutes <= 10) ||
              (order.examRemainingMinutes > 0 &&
                order.examRemainingMinutes <= 10)
            );
          case "expired":
            return order.remainingMinutes <= 0 && order.orderStatus === 0;
          case "exam_expired":
            return order.examRemainingMinutes <= 0 && order.orderStatus === 5;
          default:
            return true;
        }
      });
    }

    countdownOrders.value = orders;

    // 优化：批量计算剩余时间，避免多次API调用
    countdownOrders.value.forEach((order) => {
      // 计算普通倒计时剩余时间
      if (order.countdownEndTime) {
        const now = new Date();
        const endTime = new Date(order.countdownEndTime);
        const diffMinutes = Math.max(
          0,
          Math.floor((endTime - now) / (1000 * 60))
        );
        order.remainingMinutes = diffMinutes;
      } else {
        order.remainingMinutes = 0;
      }

      // 计算考试倒计时剩余时间
      if (order.examCountdownEndTime) {
        const now = new Date();
        const endTime = new Date(order.examCountdownEndTime);
        const diffMinutes = Math.max(
          0,
          Math.floor((endTime - now) / (1000 * 60))
        );
        order.examRemainingMinutes = diffMinutes;
      } else {
        order.examRemainingMinutes = 0;
      }
    });

    // 更新统计信息
    updateStatistics();

    // 如果有筛选条件，自动滚动到第一个匹配项
    if (filters.studentAccount && orders.length > 0) {
      nextTick(() => {
        scrollToTargetRow(orders[0].orderNo);
      });
    }
  } catch (error) {
    console.error("加载倒计时订单失败:", error);
    ElMessage.error("加载倒计时订单失败");
  } finally {
    loading.value = false;
  }
};

// 更新统计信息
const updateStatistics = () => {
  statistics.activeCountdown = countdownOrders.value.filter(
    (order) => order.orderStatus === 1 && order.remainingMinutes > 0
  ).length;

  statistics.activeExamCountdown = countdownOrders.value.filter(
    (order) => order.orderStatus === 6 && order.examRemainingMinutes > 0
  ).length;

  statistics.expiredCount = countdownOrders.value.filter(
    (order) => order.remainingMinutes <= 0 || order.examRemainingMinutes <= 0
  ).length;

  statistics.autoCompleteCount = countdownOrders.value.filter(
    (order) => order.autoCompleteEnabled || order.examAutoCompleteEnabled
  ).length;

  statistics.manualCompleteCount = countdownOrders.value.filter(
    (order) => !order.autoCompleteEnabled && !order.examAutoCompleteEnabled
  ).length;

  statistics.examCompletedCount = countdownOrders.value.filter(
    (order) => order.orderStatus === 7
  ).length;
};

// 获取倒计时状态类型
const getCountdownStatusType = (row) => {
  if (row.remainingMinutes <= 0) {
    if (row.orderStatus === 0) return "warning"; // 待考试状态
    return "danger"; // 已过期
  }
  if (row.remainingMinutes <= 30) return "warning";
  return "success";
};

// 获取倒计时时间样式类
const getCountdownTimeClass = (row) => {
  if (row.remainingMinutes <= 0) return "expired";
  if (row.remainingMinutes <= 10) return "critical";
  if (row.remainingMinutes <= 30) return "warning";
  return "normal";
};

// 获取订单状态类型
const getOrderStatusType = (status) => {
  const statusMap = {
    0: "info", // 待处理
    1: "warning", // 进行中
    2: "success", // 已完成
    3: "danger", // 已取消
    4: "danger", // 失败
  };
  return statusMap[status] || "info";
};

// 获取订单状态文本
const getOrderStatusText = (status) => {
  const statusMap = {
    0: "待处理",
    1: "进行中",
    2: "已完成",
    3: "已取消",
    4: "失败",
  };
  return statusMap[status] || "未知";
};

// 获取倒计时状态文本
const getCountdownStatusText = (row) => {
  if (row.remainingMinutes <= 0) {
    // 根据订单状态判断
    if (row.orderStatus === 0) return "已过期待考试";
    if (row.orderStatus === 1) return "进行中";
    return "已过期";
  }
  if (row.remainingMinutes <= 30) return "即将过期";
  return "进行中";
};

// 格式化时间
const formatTime = (minutes) => {
  if (minutes <= 0) return "00:00";
  const hours = Math.floor(minutes / 60);
  const mins = minutes % 60;
  return `${hours.toString().padStart(2, "0")}:${mins
    .toString()
    .padStart(2, "0")}`;
};

// 格式化日期时间
const formatDateTime = (dateTime) => {
  if (!dateTime) return "-";
  return new Date(dateTime).toLocaleString("zh-CN");
};

// 显示调整对话框
const showAdjustDialog = (row) => {
  adjustForm.orderId = row.id;
  adjustForm.orderNo = row.orderNo;
  adjustForm.currentDuration = row.countdownDuration;
  adjustForm.newDuration = row.countdownDuration;
  adjustForm.reason = "";
  adjustDialogVisible.value = true;
};

// 提交调整
const submitAdjust = async () => {
  try {
    await adjustCountdown(adjustForm.orderId, {
      newDuration: adjustForm.newDuration,
      reason: adjustForm.reason,
    });
    ElMessage.success("倒计时调整成功");
    adjustDialogVisible.value = false;
    loadCountdownOrders();
  } catch (error) {
    ElMessage.error("倒计时调整失败");
  }
};

// 完成订单
const completeOrder = (row) => {
  completeForm.orderId = row.id;
  completeForm.orderNo = row.orderNo;
  completeForm.courseName = row.courseName;
  completeForm.reason = "";
  completeDialogVisible.value = true;
};

// 提交完成
const submitComplete = async () => {
  try {
    await completeOrderApi(completeForm.orderId, {
      reason: completeForm.reason,
    });
    ElMessage.success("订单完成成功");
    completeDialogVisible.value = false;
    loadCountdownOrders();
  } catch (error) {
    ElMessage.error("订单完成失败");
  }
};

// 显示重新开始倒计时对话框
const showRestartDialog = (row) => {
  restartForm.orderId = row.id;
  restartForm.orderNo = row.orderNo;
  restartForm.courseName = row.courseName;
  // 使用配置的默认时长，如果没有配置则使用60分钟
  restartForm.duration = configForm.default_countdown_duration || 60;
  restartForm.reason = "";
  restartDialogVisible.value = true;
};

// 提交重新开始倒计时
const submitRestartCountdown = async () => {
  if (!restartForm.duration || restartForm.duration < 1) {
    ElMessage.warning("请输入有效的倒计时时长");
    return;
  }

  restartLoading.value = true;
  try {
    const response = await fetch(
      `/api/admin/orders/${restartForm.orderId}/restart-countdown`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${localStorage.getItem("token")}`,
        },
        body: JSON.stringify({
          duration: restartForm.duration,
          reason: restartForm.reason || "重新开始倒计时",
        }),
      }
    );

    const data = await response.json();
    if (data.code === 1) {
      ElMessage.success("倒计时重新开始成功");
      restartDialogVisible.value = false;
      loadCountdownOrders();
    } else {
      ElMessage.error(data.message || "重新开始倒计时失败");
    }
  } catch (error) {
    ElMessage.error("重新开始倒计时失败");
  } finally {
    restartLoading.value = false;
  }
};

// 显示下一步任务对话框
const showNextTaskDialog = (row) => {
  nextTaskForm.orderId = row.id;
  nextTaskForm.orderNo = row.orderNo;
  nextTaskForm.courseName = row.courseName;
  // 使用配置的默认时长，如果没有配置则使用60分钟
  nextTaskForm.duration = configForm.default_countdown_duration || 60;
  // 使用配置的自动完成设置
  nextTaskForm.autoCompleteEnabled = configForm.auto_complete_enabled === 1;
  nextTaskForm.autoCompleteStatus = configForm.auto_complete_status || 2;
  nextTaskForm.taskDescription = "";
  nextTaskForm.reason = "";
  nextTaskDialogVisible.value = true;
};

// 显示开始考试对话框
const showStartExamDialog = (row) => {
  startExamForm.orderId = row.id;
  startExamForm.orderNo = row.orderNo;
  startExamForm.courseName = row.courseName;
  // 使用配置的默认考试倒计时时长
  startExamForm.duration =
    examConfigForm.default_exam_countdown_duration || 120;
  // 使用配置的自动完成设置
  startExamForm.autoCompleteEnabled =
    examConfigForm.exam_auto_complete_enabled === 1;
  startExamForm.reason = "";
  startExamDialogVisible.value = true;
};

// 提交开始考试倒计时
const submitStartExam = async () => {
  if (!startExamForm.duration || startExamForm.duration < 1) {
    ElMessage.warning("请输入有效的考试倒计时时长");
    return;
  }

  startExamLoading.value = true;
  try {
    const response = await startExamCountdown(startExamForm.orderId, {
      duration: startExamForm.duration,
      reason: startExamForm.reason || "开始考试倒计时",
    });

    if (response.code === 1) {
      ElMessage.success("考试倒计时开始成功");
      startExamDialogVisible.value = false;
      loadCountdownOrders();
    } else {
      ElMessage.error(response.message || "开始考试倒计时失败");
    }
  } catch (error) {
    ElMessage.error("开始考试倒计时失败");
  } finally {
    startExamLoading.value = false;
  }
};

// 显示考试配置对话框
const showExamConfigDialog = () => {
  examConfigDialogVisible.value = true;
  loadExamConfigs();
};

// 加载考试配置
const loadExamConfigs = async () => {
  try {
    const response = await getExamCountdownConfigs();
    if (response.code === 1) {
      const configs = response.data;
      examConfigForm.id = configs.id || null;
      examConfigForm.default_exam_countdown_duration = parseInt(
        configs.default_exam_countdown_duration || "120"
      );
      examConfigForm.exam_auto_complete_status = parseInt(
        configs.exam_auto_complete_status || "7"
      );
      examConfigForm.exam_auto_complete_enabled = parseInt(
        configs.exam_auto_complete_enabled || "1"
      );
      examConfigForm.exam_countdown_warning_time = parseInt(
        configs.exam_countdown_warning_time || "15"
      );
      examConfigForm.createTime = configs.createTime
        ? formatDateTime(configs.createTime)
        : null;
      examConfigForm.updateTime = configs.updateTime
        ? formatDateTime(configs.updateTime)
        : null;
    }
  } catch (error) {
    console.error("加载考试配置失败:", error);
    ElMessage.error("加载考试配置失败");
  }
};

// 保存考试配置
const saveExamConfig = async () => {
  examSaving.value = true;
  try {
    const configs = {
      default_exam_countdown_duration:
        examConfigForm.default_exam_countdown_duration.toString(),
      exam_auto_complete_status:
        examConfigForm.exam_auto_complete_status.toString(),
      exam_auto_complete_enabled:
        examConfigForm.exam_auto_complete_enabled.toString(),
      exam_countdown_warning_time:
        examConfigForm.exam_countdown_warning_time.toString(),
    };
    const response = await updateExamCountdownConfigs(configs);

    if (response.code === 1) {
      ElMessage.success("考试配置保存成功");
      examConfigDialogVisible.value = false;
      // 重新加载配置以获取最新的时间戳
      await loadExamConfigs();
    } else {
      ElMessage.error(response.message || "考试配置保存失败");
    }
  } catch (error) {
    console.error("保存考试配置失败:", error);
    ElMessage.error("保存考试配置失败");
  } finally {
    examSaving.value = false;
  }
};

// 提交下一步任务倒计时
const submitNextTaskCountdown = async () => {
  if (!nextTaskForm.duration || nextTaskForm.duration < 1) {
    ElMessage.warning("请输入有效的倒计时时长");
    return;
  }

  nextTaskLoading.value = true;
  try {
    const response = await startNextTaskCountdown(nextTaskForm.orderId, {
      duration: nextTaskForm.duration,
      autoCompleteEnabled: nextTaskForm.autoCompleteEnabled,
      autoCompleteStatus: nextTaskForm.autoCompleteEnabled
        ? nextTaskForm.autoCompleteStatus
        : null,
      reason:
        nextTaskForm.reason ||
        `开始下一步任务倒计时：${nextTaskForm.taskDescription || "无描述"}`,
    });

    if (response.code === 1) {
      ElMessage.success("下一步任务倒计时开始成功");
      nextTaskDialogVisible.value = false;
      loadCountdownOrders();
    } else {
      ElMessage.error(response.message || "开始下一步任务倒计时失败");
    }
  } catch (error) {
    ElMessage.error("开始下一步任务倒计时失败");
  } finally {
    nextTaskLoading.value = false;
  }
};

// 提交状态切换
const submitStatusSwitch = async () => {
  if (statusSwitchForm.newStatus === null) {
    ElMessage.warning("请选择新状态");
    return;
  }

  statusSwitchLoading.value = true;
  try {
    const response = await fetch(
      `/api/admin/orders/${statusSwitchForm.orderId}/switch-status`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${localStorage.getItem("token")}`,
        },
        body: JSON.stringify({
          newStatus: statusSwitchForm.newStatus,
          reason: statusSwitchForm.reason || "切换订单状态",
        }),
      }
    );

    const data = await response.json();
    if (data.code === 1) {
      ElMessage.success("订单状态切换成功");
      statusSwitchDialogVisible.value = false;
      loadCountdownOrders();
    } else {
      ElMessage.error(data.message || "状态切换失败");
    }
  } catch (error) {
    ElMessage.error("状态切换失败");
  } finally {
    statusSwitchLoading.value = false;
  }
};

// 显示订单详情
const showOrderDetail = (row) => {
  selectedOrder.value = row;
  detailDialogVisible.value = true;
};

// 处理表格选择变化
const handleSelectionChange = (selection) => {
  selectedOrders.value = selection;
};

// 显示批量操作对话框
const showBatchOperationDialog = () => {
  if (selectedOrders.value.length === 0) {
    ElMessage.warning("请先选择要操作的订单");
    return;
  }
  batchForm.operationType = "";
  batchForm.newDuration = 60;
  batchForm.reason = "";
  batchDialogVisible.value = true;
};

// 提交批量操作
const submitBatchOperation = async () => {
  if (!batchForm.operationType) {
    ElMessage.warning("请选择操作类型");
    return;
  }

  if (batchForm.operationType === "adjust" && !batchForm.newDuration) {
    ElMessage.warning("请输入新的倒计时时长");
    return;
  }

  try {
    await ElMessageBox.confirm(
      `确定要对选中的${selectedOrders.value.length}个订单执行${
        batchForm.operationType === "complete" ? "完成" : "调整倒计时"
      }操作吗？`,
      "确认批量操作",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }
    );

    batchLoading.value = true;

    const requestData = {
      orderIds: selectedOrders.value.map((order) => order.id),
      operationType: batchForm.operationType,
      newDuration:
        batchForm.operationType === "adjust" ? batchForm.newDuration : null,
      reason: batchForm.reason,
    };

    const response = await batchCountdownOperation(requestData);

    if (response.code === 1) {
      ElMessage.success(response.message || "批量操作成功");
      batchDialogVisible.value = false;
      selectedOrders.value = [];
      loadCountdownOrders();
    } else {
      ElMessage.error(response.message || "批量操作失败");
    }
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error("批量操作失败");
    }
  } finally {
    batchLoading.value = false;
  }
};

// 启动实时倒计时更新
const startCountdownTimer = () => {
  if (countdownTimer.value) {
    clearInterval(countdownTimer.value);
  }

  countdownTimer.value = setInterval(() => {
    // 更新每个订单的剩余时间
    countdownOrders.value.forEach((order) => {
      // 更新普通倒计时剩余时间
      if (order.countdownEndTime) {
        const now = new Date();
        const endTime = new Date(order.countdownEndTime);
        const diffMinutes = Math.max(
          0,
          Math.floor((endTime - now) / (1000 * 60))
        );
        order.remainingMinutes = diffMinutes;

        // 如果普通倒计时过期且订单仍为进行中状态，标记为待处理
        if (diffMinutes <= 0 && order.orderStatus === 1) {
          order.orderStatus = 0; // 标记为待处理状态
        }
      }

      // 更新考试倒计时剩余时间
      if (order.examCountdownEndTime) {
        const now = new Date();
        const endTime = new Date(order.examCountdownEndTime);
        const diffMinutes = Math.max(
          0,
          Math.floor((endTime - now) / (1000 * 60))
        );
        order.examRemainingMinutes = diffMinutes;

        // 如果考试倒计时过期且订单仍为考试中状态，标记为待考试
        if (diffMinutes <= 0 && order.orderStatus === 6) {
          order.orderStatus = 5; // 标记为待考试状态
        }
      }
    });

    // 更新统计信息
    updateStatistics();
  }, 1000); // 每秒更新一次
};

// 停止实时倒计时更新
const stopCountdownTimer = () => {
  if (countdownTimer.value) {
    clearInterval(countdownTimer.value);
    countdownTimer.value = null;
  }
};

// 显示历史记录对话框
const showHistoryDialog = () => {
  historyDialogVisible.value = true;
  activeHistoryTab.value = "order";
  if (selectedOrder.value) {
    loadOrderHistory();
  }
};

// 处理历史记录标签页切换
const handleHistoryTabChange = (tabName) => {
  if (tabName === "order" && selectedOrder.value) {
    loadOrderHistory();
  } else if (tabName === "all") {
    loadAllHistory();
  }
};

// 加载订单历史记录
const loadOrderHistory = async () => {
  if (!selectedOrder.value) return;

  historyLoading.value = true;
  try {
    const response = await getCountdownHistory(selectedOrder.value.id);
    if (response.code === 1) {
      orderHistory.value = response.data;
    } else {
      ElMessage.error("加载订单历史记录失败");
    }
  } catch (error) {
    ElMessage.error("加载订单历史记录失败");
  } finally {
    historyLoading.value = false;
  }
};

// 加载所有历史记录
const loadAllHistory = async () => {
  historyLoading.value = true;
  try {
    const response = await getAllCountdownHistoryWithDetails({
      pageNum: 1,
      pageSize: 50,
    });
    if (response.code === 1) {
      allHistory.value = response.data;
    } else {
      ElMessage.error("加载历史记录失败");
    }
  } catch (error) {
    ElMessage.error("加载历史记录失败");
  } finally {
    historyLoading.value = false;
  }
};

// 获取操作类型标签类型
const getOperationTypeTag = (operationType) => {
  const tagMap = {
    start: "success",
    adjust: "warning",
    complete: "success",
    manual_complete: "success",
    auto_complete: "success",
    expired: "danger",
    restart: "primary",
    status_switch: "info",
    next_task_start: "primary",
  };
  return tagMap[operationType] || "info";
};

// 获取操作类型文本
const getOperationTypeText = (operationType) => {
  const textMap = {
    start: "开始倒计时",
    adjust: "调整倒计时",
    complete: "完成订单",
    manual_complete: "手动完成",
    auto_complete: "自动完成",
    expired: "过期",
    restart: "重新开始",
    status_switch: "状态切换",
    next_task_start: "下一步任务",
  };
  return textMap[operationType] || "未知操作";
};

// 获取订单状态标签类型
const getOrderStatusTag = (orderStatus) => {
  const tagMap = {
    0: "warning", // 待考试
    1: "primary", // 进行中
    2: "success", // 已完成
    3: "info", // 已取消
    4: "danger", // 失败
  };
  return tagMap[orderStatus] || "info";
};

// 应用历史记录筛选
const applyHistoryFilter = () => {
  if (activeHistoryTab.value === "order" && selectedOrder.value) {
    loadOrderHistory();
  } else if (activeHistoryTab.value === "all") {
    loadAllHistory();
  }
};

// 开始自动刷新
const startAutoRefresh = () => {
  if (autoRefresh.value) {
    if (refreshTimer.value) {
      clearInterval(refreshTimer.value);
      refreshTimer.value = null;
    }
    autoRefresh.value = false;
  } else {
    refreshTimer.value = setInterval(() => {
      loadCountdownOrders();
    }, 30000); // 30秒刷新一次
    autoRefresh.value = true;
  }
};

// 显示配置对话框
const showConfigDialog = () => {
  configDialogVisible.value = true;
  loadConfigs();
};

// 加载配置
const loadConfigs = async () => {
  try {
    const response = await getCountdownConfigs();
    if (response.code === 1) {
      const configs = response.data;
      // 假设后端返回的是对象或者键值对
      configForm.id = configs.id || null;
      configForm.default_countdown_duration = parseInt(
        configs.default_countdown_duration || "60"
      );
      configForm.auto_complete_status = parseInt(
        configs.auto_complete_status || "2"
      );
      configForm.auto_complete_enabled = parseInt(
        configs.auto_complete_enabled || "1"
      );
      configForm.countdown_warning_time = parseInt(
        configs.countdown_warning_time || "10"
      );
      configForm.createTime = configs.createTime
        ? formatDateTime(configs.createTime)
        : null;
      configForm.updateTime = configs.updateTime
        ? formatDateTime(configs.updateTime)
        : null;
    }
  } catch (error) {
    console.error("加载配置失败:", error);
    ElMessage.error("加载配置失败");
  }
};

// 保存配置
const saveConfig = async () => {
  saving.value = true;
  try {
    const configs = {
      default_countdown_duration:
        configForm.default_countdown_duration.toString(),
      auto_complete_status: configForm.auto_complete_status.toString(),
      auto_complete_enabled: configForm.auto_complete_enabled.toString(),
      countdown_warning_time: configForm.countdown_warning_time.toString(),
    };
    const response = await updateCountdownConfigs(configs);

    if (response.code === 1) {
      ElMessage.success("配置保存成功");
      configDialogVisible.value = false;
      // 重新加载配置以获取最新的时间戳
      await loadConfigs();
    } else {
      ElMessage.error(response.message || "配置保存失败");
    }
  } catch (error) {
    console.error("保存配置失败:", error);
    ElMessage.error("保存配置失败");
  } finally {
    saving.value = false;
  }
};

// 加载订单状态选项
const loadOrderStatusOptions = () => {
  orderStatusOptions.value = variableStore.getStatusOptions("order_status");
};

// 移动端检测
const isMobile = ref(false);

// 检测屏幕尺寸
const checkScreenSize = () => {
  isMobile.value = window.innerWidth <= 768;
};

// 监听窗口大小变化
const handleResize = () => {
  checkScreenSize();
};

// 获取操作列宽度
const getOperationColumnWidth = () => {
  if (isMobile.value) {
    return 150; // 移动端使用较小宽度
  } else if (window.innerWidth <= 1200) {
    return 200; // 中等屏幕
  } else {
    return 250; // 大屏幕
  }
};

// 页面初始化
onMounted(async () => {
  // 初始化屏幕尺寸检测
  checkScreenSize();
  // 添加窗口大小变化监听
  window.addEventListener("resize", handleResize);
  await variableStore.loadAllVariables();
  loadOrderStatusOptions();
  loadColumnConfigData(); // 加载列配置
  loadCountdownOrders();
  // 启动实时倒计时更新
  startCountdownTimer();
});

// 页面销毁
onUnmounted(() => {
  // 清理事件监听
  window.removeEventListener("resize", handleResize);
  if (refreshTimer.value) {
    clearInterval(refreshTimer.value);
  }
  // 停止实时倒计时更新
  stopCountdownTimer();
  // 销毁拖动实例
  if (sortableInstance) {
    sortableInstance.destroy();
  }
});
</script>

<style scoped>
.countdown-management {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-content > div:first-child h2 {
  margin: 0 0 8px 0;
  color: -var(--color-primary);
}

.header-content > div:first-child p {
  margin: 0;
  color: -var(--color-secondary);
  font-size: 14px;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.statistics-cards {
  margin-bottom: 20px;
}

.stat-card {
  text-align: center;
}

.stat-content {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: -var(--text-secondary);
}

.stat-icon {
  font-size: 32px;
  color: #409eff;
  margin-top: 10px;
}

.stat-icon.success {
  color: #67c23a;
}

.stat-icon.warning {
  color: #e6a23c;
}

.stat-icon.info {
  color: #909399;
}

.operation-card {
  margin-bottom: 20px;
}

.operation-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.left-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.right-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.mobile-operation {
  width: 100%;
}

.mobile-actions {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.mobile-filters {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.mobile-filters .el-select,
.mobile-filters .el-input {
  width: 100%;
}

.mobile-filters .el-button {
  width: 100%;
}

/* 平板端样式 */
@media (min-width: 769px) and (max-width: 1200px) {
  .operation-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .left-actions {
    justify-content: flex-start;
    margin-bottom: 10px;
  }

  .right-filters {
    justify-content: flex-start;
  }

  .right-filters > * {
    flex: 1;
    min-width: 150px;
  }
}

/* 移动端样式 */
@media (max-width: 768px) {
  .operation-card {
    margin-bottom: 15px;
  }

  .operation-bar {
    padding: 0;
  }

  /* 隐藏桌面端元素 */
  .left-actions,
  .right-filters {
    display: none;
  }

  /* 折叠面板优化 */
  :deep(.el-collapse) {
    border: none;
  }

  :deep(.el-collapse-item__header) {
    background-color: #f5f7fa;
    padding: 12px 15px;
    font-weight: 600;
    color: #303133;
  }

  :deep(.el-collapse-item__content) {
    padding: 15px;
    background-color: var(--bg-card);
  }

  :deep(.el-collapse-item__wrap) {
    border-bottom: none;
    background-color: var(--bg-card);
  }

  /* 移动端按钮大小 */
  .mobile-actions .el-button {
    padding: 8px 12px;
    font-size: 13px;
  }

  .mobile-actions .el-button .el-icon {
    font-size: 14px;
  }
}

/* 超小屏幕优化 */
@media (max-width: 375px) {
  .mobile-actions {
    grid-template-columns: 1fr;
  }

  .mobile-actions .el-button {
    width: 100%;
  }
}

/* 大屏幕优化 */
@media (min-width: 1600px) {
  .operation-bar {
    padding: 10px 0;
  }

  .left-actions {
    gap: 15px;
  }

  .right-filters > * {
    margin-right: 15px;
  }

  .right-filters > *:last-child {
    margin-right: 0;
  }
}

.table-card {
  margin-bottom: 20px;
}

.countdown-display {
  font-family: "Courier New", monospace;
}

.countdown-time {
  color: var(--color-success);
  font-weight: bold;
}

.countdown-time.critical {
  color: var(--color-danger);
  animation: blink 1s infinite;
}

.countdown-time.warning {
  color: var(--color-warning);
}

.countdown-time.normal {
  color: var(--color-success);
}

.countdown-time.expired {
  color: var(--color-danger);
}

.expired-text {
  color: var(--color-danger);
  font-weight: bold;
}

@keyframes blink {
  0%,
  50% {
    opacity: 1;
  }
  51%,
  100% {
    opacity: 0.3;
  }
}

.selected-orders {
  max-height: 120px;
  overflow-y: auto;
}

.order-tag {
  margin: 2px;
}

.order-detail {
  padding: 10px 0;
}

.history-content {
  max-height: 600px;
  overflow-y: auto;
}

.order-history-section h4 {
  margin: 0 0 15px 0;
  color: var(--text-primary);
}

.no-selection {
  text-align: center;
  padding: 40px 0;
}

.config-form {
  max-width: 100%;
}

.form-tip {
  margin-left: 10px;
  color: var(--text-secondary);
  font-size: 12px;
}

/* 操作列响应式样式 */
.operation-buttons {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.operation-buttons > div {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.operation-buttons .el-button {
  margin: 0;
  padding: 4px 8px;
  font-size: 12px;
  min-width: auto;
}

/* 移动端操作列优化 */
@media (max-width: 768px) {
  .operation-buttons {
    gap: 2px;
  }

  .operation-buttons .el-button {
    padding: 2px 6px;
    font-size: 11px;
    min-width: 40px;
  }

  .operation-buttons > div {
    gap: 2px;
  }

  /* 移动端隐藏部分次要操作 */
  .secondary-actions {
    display: none;
  }
}

/* 中等屏幕优化 */
@media (max-width: 1200px) and (min-width: 769px) {
  .operation-buttons .el-button {
    padding: 3px 6px;
    font-size: 11px;
  }

  .secondary-actions .el-button {
    font-size: 10px;
    padding: 2px 4px;
  }
}

/* 表格操作列固定宽度优化 */
.operation-column {
  min-width: 150px;
}

@media (min-width: 769px) {
  .operation-column {
    min-width: 200px;
  }
}

@media (min-width: 1201px) {
  .operation-column {
    min-width: 250px;
  }
}

/* 列配置对话框样式 */
.column-config-content {
  max-height: 500px;
  overflow-y: auto;
}

.column-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.column-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background: var(--bg-body);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  cursor: move;
  transition: all 0.3s;
}

.column-item:hover {
  background: var(--bg-body-darker);
  box-shadow: var(--shadow-sm);
}

.column-item-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.column-item-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.drag-handle {
  cursor: grab;
  color: var(--text-secondary);
  font-size: 16px;
}

.drag-handle:active {
  cursor: grabbing;
}

.sortable-ghost {
  opacity: 0.4;
  background: var(--color-primary);
}

/* 配置表单优化 */
.config-form .el-divider {
  margin: 20px 0;
}

/* 历史记录筛选卡片样式 */
.filter-card {
  background: var(--bg-body);
  border: 1px solid var(--border-color);
}

/* 目标行高亮样式 */
.target-row-highlight {
  background-color: var(--color-warning-light-9) !important;
  border: 2px solid var(--color-warning) !important;
  animation: highlight-pulse 2s ease-in-out;
}

@keyframes highlight-pulse {
  0% {
    background-color: var(--color-warning-light-9);
    transform: scale(1);
  }
  50% {
    background-color: var(--color-warning-light-7);
    transform: scale(1.02);
  }
  100% {
    background-color: var(--color-warning-light-9);
    transform: scale(1);
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header span {
  font-weight: 600;
  color: var(--text-primary);
}

/* 倒计时状态优化 */
.countdown-status-pending {
  background: var(--color-warning-light-9);
  border-color: var(--color-warning);
}

.countdown-status-expired {
  background: var(--color-danger-light-9);
  border-color: var(--color-danger);
}

/* 操作按钮组优化 */
.operation-buttons {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.operation-buttons > div {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: flex-start;
}

.operation-buttons .el-button {
  margin: 0;
  padding: 6px 12px;
  font-size: 12px;
  min-width: auto;
  border-radius: 4px;
  transition: all 0.3s ease;
}

.operation-buttons .el-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15);
}

/* 过期订单特殊样式 */
.expired-actions .el-button {
  font-weight: 500;
}

.expired-actions .el-button--primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
}

.expired-actions .el-button--success {
  background: linear-gradient(135deg, #56ab2f 0%, #a8e6cf 100%);
  border: none;
}

.expired-actions .el-button--warning {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  border: none;
  color: white;
}

/* 统计卡片优化 */
.stat-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.stat-value {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* Dark Mode Overrides */
html.dark .stat-card {
  background: #1e1e1e;
  border-color: #404040;
}

html.dark .column-item {
  background: #1e1e1e;
  border-color: #404040;
}

html.dark .column-item:hover {
  background: #2a2a2a;
}

html.dark .filter-card {
  background: #1e1e1e;
  border-color: #404040;
}

html.dark .target-row-highlight {
  background-color: rgba(230, 162, 60, 0.2) !important;
  border-color: #e6a23c !important;
}

html.dark .countdown-status-pending {
  background: rgba(230, 162, 60, 0.2);
  border-color: #e6a23c;
}

html.dark .countdown-status-expired {
  background: rgba(245, 108, 108, 0.2);
  border-color: #f56c6c;
}
</style>
