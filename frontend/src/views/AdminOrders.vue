<template>
  <div class="admin-orders-page">
    <!-- 统计卡片 -->
    <el-row :gutter="isMobile ? 12 : 20" class="statistics-cards">
      <el-col :xs="12" :sm="12" :md="6" :lg="6" :xl="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-value">{{ statistics.totalOrders || 0 }}</div>
            <div class="stat-label">总订单数</div>
          </div>
          <el-icon class="stat-icon"><Document /></el-icon>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6" :xl="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-value">{{ statistics.completedOrders || 0 }}</div>
            <div class="stat-label">已完成</div>
          </div>
          <el-icon class="stat-icon success"><Check /></el-icon>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6" :xl="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-value">¥{{ statistics.totalRevenue || 0 }}</div>
            <div class="stat-label">总营收</div>
          </div>
          <el-icon class="stat-icon warning"><Money /></el-icon>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6" :xl="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-value">{{ statistics.todayOrders || 0 }}</div>
            <div class="stat-label">今日订单</div>
          </div>
          <el-icon class="stat-icon info"><Calendar /></el-icon>
        </el-card>
      </el-col>
    </el-row>

    <!-- 订单管理主卡片 -->
    <el-card class="main-card">
      <template #header>
        <div class="card-header">
          <span>订单管理</span>
          <div class="header-actions">
            <el-button type="success" @click="loadStatistics">
              <el-icon><Refresh /></el-icon>
              刷新统计
            </el-button>
            <el-button
              type="warning"
              @click="handleBatchOperation"
              v-if="selectedOrders.length > 0"
            >
              <el-icon><Operation /></el-icon>
              批量操作（{{ selectedOrders.length }}）
            </el-button>
            <el-button
              type="success"
              @click="handleQuickComplete"
              v-if="selectedOrders.length > 0"
            >
              <el-icon><Check /></el-icon>
              批量完成
            </el-button>
            <el-button
              type="danger"
              @click="handleQuickCancel"
              v-if="selectedOrders.length > 0"
            >
              <el-icon><Close /></el-icon>
              批量取消
            </el-button>
            <el-button
              type="primary"
              @click="handleExport"
              v-if="selectedOrders.length > 0"
            >
              <el-icon><Download /></el-icon>
              导出订单（{{ selectedOrders.length }}）
            </el-button>
            <el-button type="info" @click="handleColumnManage">
              <el-icon><Setting /></el-icon>
              列管理
            </el-button>
            <el-button
              :type="showPassword ? 'success' : 'info'"
              @click="togglePasswordDisplay"
            >
              <el-icon><View /></el-icon>
              {{ showPassword ? "隐藏密码" : "显示密码" }}
            </el-button>
            <el-button type="primary" @click="handleCreate">
              <el-icon><Plus /></el-icon>
              新建订单
            </el-button>
          </div>
        </div>
      </template>

      <!-- 企业级筛选方案：移动端抽屉 + 桌面端折叠面板 -->
      <template v-if="isMobile">
        <!-- 移动端：筛选按钮 + 抽屉 -->
        <div class="mobile-filter-trigger">
          <el-button type="primary" @click="filterDrawerVisible = true">
            <el-icon><Filter /></el-icon>
            筛选条件
          </el-button>
          <el-button @click="handleResetQuery">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </div>

        <el-drawer
          v-model="filterDrawerVisible"
          title="筛选条件"
          direction="rtl"
          size="80%"
        >
          <el-form :model="queryForm" label-position="top" ref="queryFormRef">
            <el-form-item label="订单编号">
              <el-input
                v-model="queryForm.orderNo"
                placeholder="订单编号"
                clearable
              />
            </el-form-item>
            <el-form-item label="平台">
              <el-select
                v-model="queryForm.platformId"
                placeholder="选择平台"
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="platform in platformList"
                  :key="platform.id"
                  :label="platform.name"
                  :value="platform.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="订单状态">
              <el-select
                v-model="queryForm.orderStatus"
                placeholder="订单状态"
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="option in variableStore.getStatusOptions(
                    'order_status'
                  )"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="学生账号">
              <el-input
                v-model="queryForm.studentAccount"
                placeholder="学生账号"
                clearable
              />
            </el-form-item>
            <el-form-item label="对接状态">
              <el-select
                v-model="queryForm.dockStatus"
                placeholder="对接状态"
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="option in variableStore.getStatusOptions(
                    'dock_status'
                  )"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="代理账号">
              <el-select
                v-model="queryForm.userId"
                placeholder="选择代理账号"
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="agent in agentList"
                  :key="agent.id"
                  :label="
                    agent.username +
                    (agent.nickname ? ` (${agent.nickname})` : '')
                  "
                  :value="agent.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="创建时间">
              <el-date-picker
                v-model="dateRange"
                type="datetimerange"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                format="YYYY-MM-DD HH:mm:ss"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-form>
          <template #footer>
            <div style="display: flex; gap: 10px">
              <el-button
                type="primary"
                @click="handleDrawerSearch"
                :loading="searchLoading"
              >
                <el-icon><Search /></el-icon>
                查询
              </el-button>
              <el-button @click="filterDrawerVisible = false">取消</el-button>
            </div>
          </template>
        </el-drawer>
      </template>

      <template v-else>
        <!-- 桌面端：常用筛选 + 高级筛选折叠面板 -->
        <div class="desktop-filter-wrapper">
          <!-- 常用筛选（始终显示） -->
          <el-form
            :inline="true"
            :model="queryForm"
            label-width="100px"
            class="common-filter-form"
            ref="queryFormRef"
          >
            <el-form-item label="订单编号">
              <el-input
                v-model="queryForm.orderNo"
                placeholder="订单编号"
                clearable
                style="width: 200px"
                @input="handleRealTimeSearch"
              />
            </el-form-item>
            <el-form-item label="平台">
              <el-select
                v-model="queryForm.platformId"
                placeholder="选择平台"
                clearable
                style="width: 150px"
                @change="handleRealTimeSearch"
              >
                <el-option
                  v-for="platform in platformList"
                  :key="platform.id"
                  :label="platform.name"
                  :value="platform.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="订单状态">
              <el-select
                v-model="queryForm.orderStatus"
                placeholder="订单状态"
                clearable
                style="width: 120px"
                @change="handleRealTimeSearch"
              >
                <el-option
                  v-for="option in variableStore.getStatusOptions(
                    'order_status'
                  )"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                @click="handleSearch"
                :loading="searchLoading"
              >
                <el-icon><Search /></el-icon>
                查询
              </el-button>
              <el-button @click="handleResetQuery">
                <el-icon><Refresh /></el-icon>
                重置
              </el-button>
            </el-form-item>
          </el-form>

          <!-- 高级筛选（折叠面板） -->
          <el-collapse
            v-model="advancedSearchVisible"
            class="advanced-filter-collapse"
          >
            <el-collapse-item title="高级筛选" name="1">
              <el-form :inline="true" :model="queryForm" label-width="100px">
                <el-form-item label="学生账号">
                  <el-input
                    v-model="queryForm.studentAccount"
                    placeholder="学生账号"
                    clearable
                    style="width: 150px"
                    @input="handleRealTimeSearch"
                  />
                </el-form-item>
                <el-form-item label="对接状态">
                  <el-select
                    v-model="queryForm.dockStatus"
                    placeholder="对接状态"
                    clearable
                    style="width: 120px"
                    @change="handleRealTimeSearch"
                  >
                    <el-option
                      v-for="option in variableStore.getStatusOptions(
                        'dock_status'
                      )"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="代理账号">
                  <el-select
                    v-model="queryForm.userId"
                    placeholder="选择代理账号"
                    clearable
                    style="width: 150px"
                    @change="handleRealTimeSearch"
                  >
                    <el-option
                      v-for="agent in agentList"
                      :key="agent.id"
                      :label="
                        agent.username +
                        (agent.nickname ? ` (${agent.nickname})` : '')
                      "
                      :value="agent.id"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="创建时间">
                  <el-date-picker
                    v-model="dateRange"
                    type="datetimerange"
                    range-separator="至"
                    start-placeholder="开始时间"
                    end-placeholder="结束时间"
                    format="YYYY-MM-DD HH:mm:ss"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    style="width: 350px"
                    @change="handleRealTimeSearch"
                  />
                </el-form-item>
              </el-form>
            </el-collapse-item>
          </el-collapse>
        </div>
      </template>

      <!-- 订单列表 -->
      <el-table
        :data="tableData"
        style="width: 100%"
        v-loading="loading"
        @selection-change="handleSelectionChange"
        stripe
        border
        :default-sort="tableConfig.sortConfig"
        @sort-change="handleSortChange"
        ref="tableRef"
        :row-key="getRowKey"
        :lazy="true"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        :height="tableHeight"
        :max-height="tableMaxHeight"
        :row-class-name="getRowClassName"
        class="animated-table"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column
          v-for="(column, index) in visibleColumns"
          :key="column.prop"
          :prop="column.prop"
          :label="column.label"
          :width="column.width"
          :show-overflow-tooltip="column.showOverflowTooltip"
          :sortable="column.sortable"
          :header-cell-style="{ cursor: 'move' }"
        >
          <template
            #default="scope"
            v-if="column.prop === 'studentAccountPassword'"
          >
            <div style="display: flex; align-items: center; gap: 8px">
              <span style="font-weight: 500">{{
                scope.row.studentAccount
              }}</span>
              <span style="color: var(--color-text-secondary)">|</span>
              <span style="color: var(--color-amount-success)">{{
                showPassword ? scope.row.studentPassword || "-" : "****"
              }}</span>
            </div>
          </template>
          <template #default="scope" v-else-if="column.prop === 'amount'">
            <span style="color: var(--color-amount-danger)"
              >¥{{ scope.row.amount }}</span
            >
          </template>
          <template #default="scope" v-else-if="column.prop === 'progress'">
            <div style="display: flex; flex-direction: column; gap: 4px">
              <!-- 进度条 -->
              <div style="display: flex; align-items: center; gap: 8px">
                <el-progress
                  :percentage="parseInt(scope.row.progress || '0')"
                  :stroke-width="6"
                  :show-text="false"
                  style="flex: 1"
                />
                <span style="font-size: 12px; min-width: 35px">{{
                  scope.row.progress || "0%"
                }}</span>
              </div>

              <!-- 倒计时信息（仅进行中状态且是自营订单时显示） -->
              <div
                v-if="
                  scope.row.orderStatus === 1 &&
                  scope.row.isSelfOperated &&
                  scope.row.countdownEndTime
                "
                style="
                  font-size: 11px;
                  color: var(--color-warning);
                  display: flex;
                  align-items: center;
                  gap: 4px;
                "
              >
                <el-icon><Timer /></el-icon>
                <span>{{ getCountdownText(scope.row) }}</span>
              </div>
            </div>
          </template>
          <template #default="scope" v-else-if="column.prop === 'orderStatus'">
            <el-select
              v-model="scope.row.orderStatus"
              @change="handleStatusChange(scope.row)"
              size="small"
              style="width: 120px"
              :class="getStatusClass(scope.row.orderStatus)"
            >
              <el-option
                v-for="option in variableStore.getStatusOptions('order_status')"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <div
              class="status-indicator"
              :class="getStatusIndicatorClass(scope.row.orderStatus)"
            ></div>
          </template>
          <template #default="scope" v-else-if="column.prop === 'dockStatus'">
            <el-select
              v-model="scope.row.dockStatus"
              @change="handleDockStatusChange(scope.row)"
              size="small"
              style="width: 120px"
            >
              <el-option
                v-for="option in variableStore.getStatusOptions('dock_status')"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          :width="getOperationColumnWidth()"
          :fixed="isMobile ? false : 'right'"
          class-name="operation-column"
        >
          <template #default="scope">
            <!-- 移动端：下拉菜单 -->
            <div v-if="isMobile" class="mobile-operations">
              <el-dropdown
                @command="(cmd) => handleOperationCommand(cmd, scope.row)"
              >
                <el-button size="small" type="primary">
                  操作 <el-icon class="el-icon--right"><Operation /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="view">
                      <el-icon><View /></el-icon> 详情
                    </el-dropdown-item>
                    <el-dropdown-item command="status">
                      <el-icon><Setting /></el-icon> 修改状态
                    </el-dropdown-item>
                    <el-dropdown-item command="dock">
                      <el-icon><Setting /></el-icon> 修改对接
                    </el-dropdown-item>
                    <el-dropdown-item command="remark">
                      <el-icon><Document /></el-icon> 添加备注
                    </el-dropdown-item>
                    <el-dropdown-item
                      command="retry"
                      :disabled="scope.row.retryCount >= 5"
                    >
                      <el-icon><Refresh /></el-icon> 补单
                    </el-dropdown-item>
                    <el-dropdown-item
                      v-if="scope.row.isSelfOperated"
                      command="toggle"
                    >
                      <el-icon><Check /></el-icon> 状态切换
                    </el-dropdown-item>
                    <el-dropdown-item
                      v-if="
                        scope.row.isSelfOperated && scope.row.orderStatus === 1
                      "
                      command="countdown"
                    >
                      <el-icon><Timer /></el-icon> 调整倒计时
                    </el-dropdown-item>
                    <el-dropdown-item
                      command="delete"
                      :disabled="scope.row.orderStatus === 1"
                      divided
                    >
                      <el-icon><Close /></el-icon> 删除
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>

            <!-- 桌面端：原有按钮布局 -->
            <div v-else class="operation-buttons">
              <!-- 第一行：主要操作 -->
              <div class="primary-actions">
                <el-button size="small" @click="handleView(scope.row)"
                  >详情</el-button
                >
                <el-button
                  size="small"
                  type="warning"
                  @click="handleForceUpdateStatus(scope.row)"
                >
                  修改状态
                </el-button>
                <el-button
                  size="small"
                  type="info"
                  @click="handleForceUpdateDockStatus(scope.row)"
                >
                  修改对接
                </el-button>
                <el-button
                  size="small"
                  type="primary"
                  @click="handleAddRemark(scope.row)"
                >
                  添加备注
                </el-button>
              </div>

              <!-- 第二行：次要操作和自营订单功能 -->
              <div class="secondary-actions">
                <el-button
                  size="small"
                  type="danger"
                  @click="handleRetry(scope.row)"
                  :disabled="scope.row.retryCount >= 5"
                >
                  补单
                </el-button>

                <!-- 自营订单倒计时功能 -->
                <template v-if="scope.row.isSelfOperated">
                  <el-button
                    size="small"
                    type="success"
                    @click="handleToggleStatus(scope.row)"
                  >
                    状态切换
                  </el-button>
                  <el-button
                    v-if="scope.row.orderStatus === 1"
                    size="small"
                    type="info"
                    @click="handleAdjustCountdown(scope.row)"
                  >
                    调整倒计时
                  </el-button>
                </template>

                <el-button
                  size="small"
                  type="danger"
                  @click="handleDelete(scope.row)"
                  :disabled="scope.row.orderStatus === 1"
                >
                  删除
                </el-button>
              </div>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next"
        class="pagination"
        @current-change="loadOrders"
        @size-change="loadOrders"
      />
    </el-card>

    <!-- 强制修改订单状态对话框 -->
    <el-dialog
      v-model="statusDialogVisible"
      title="修改订单状态"
      width="500px"
      append-to-body
    >
      <el-form :model="statusForm" label-width="120px">
        <el-form-item label="订单编号">
          <el-input v-model="statusForm.orderNo" disabled />
        </el-form-item>
        <el-form-item label="当前状态">
          <StatusDisplay
            type="order_status"
            :value="statusForm.currentStatus"
            in-form
          />
        </el-form-item>
        <el-form-item label="新状态">
          <el-select
            v-model="statusForm.newStatus"
            placeholder="选择新状态"
            style="width: 100%"
          >
            <el-option
              v-for="option in variableStore.getStatusOptions('order_status')"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="修改原因">
          <el-input
            v-model="statusForm.reason"
            type="textarea"
            placeholder="请输入修改原因"
            :rows="3"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleStatusSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 强制修改对接状态对话框 -->
    <el-dialog
      v-model="dockStatusDialogVisible"
      title="修改对接状态"
      width="500px"
      append-to-body
    >
      <el-form :model="dockStatusForm" label-width="120px">
        <el-form-item label="订单编号">
          <el-input v-model="dockStatusForm.orderNo" disabled />
        </el-form-item>
        <el-form-item label="当前状态">
          <StatusDisplay
            type="dock_status"
            :value="dockStatusForm.currentStatus"
            in-form
          />
        </el-form-item>
        <el-form-item label="新状态">
          <el-select
            v-model="dockStatusForm.newStatus"
            placeholder="选择新状态"
            style="width: 100%"
          >
            <el-option
              v-for="option in variableStore.getStatusOptions('dock_status')"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="修改原因">
          <el-input
            v-model="dockStatusForm.reason"
            type="textarea"
            placeholder="请输入修改原因"
            :rows="3"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dockStatusDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleDockStatusSubmit"
          >确定</el-button
        >
      </template>
    </el-dialog>

    <!-- 自营订单状态切换对话框 -->
    <el-dialog
      v-model="toggleStatusDialogVisible"
      title="自营订单状态切换"
      width="500px"
      append-to-body
    >
      <el-form :model="toggleStatusForm" label-width="120px">
        <el-form-item label="订单编号">
          <el-input v-model="toggleStatusForm.orderNo" disabled />
        </el-form-item>
        <el-form-item label="当前状态">
          <StatusDisplay
            type="order_status"
            :value="toggleStatusForm.currentStatus"
            in-form
          />
        </el-form-item>
        <el-form-item label="新状态" required>
          <el-select
            v-model="toggleStatusForm.newStatus"
            placeholder="选择新状态"
            style="width: 100%"
          >
            <el-option
              v-for="option in variableStore.getStatusOptions('order_status')"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="toggleStatusForm.newStatus === 1"
          label="倒计时时长(分钟)"
        >
          <el-input-number
            v-model="toggleStatusForm.countdownDuration"
            :min="1"
            :max="1440"
            placeholder="请输入倒计时时长"
          />
        </el-form-item>
        <el-form-item v-if="toggleStatusForm.newStatus === 1" label="自动完成">
          <el-switch v-model="toggleStatusForm.autoComplete" />
        </el-form-item>
        <el-form-item label="切换原因">
          <el-input
            v-model="toggleStatusForm.reason"
            type="textarea"
            placeholder="请输入切换原因"
            :rows="3"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="toggleStatusDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleToggleStatusSubmit"
          >确定</el-button
        >
      </template>
    </el-dialog>

    <!-- 倒计时调整对话框 -->
    <el-dialog
      v-model="adjustCountdownDialogVisible"
      title="调整倒计时"
      width="400px"
      append-to-body
    >
      <el-form :model="adjustCountdownForm" label-width="120px">
        <el-form-item label="订单编号">
          <el-input v-model="adjustCountdownForm.orderNo" disabled />
        </el-form-item>
        <el-form-item label="当前时长">
          <el-input v-model="adjustCountdownForm.currentDuration" disabled />
        </el-form-item>
        <el-form-item label="新时长(分钟)" required>
          <el-input-number
            v-model="adjustCountdownForm.newDuration"
            :min="1"
            :max="1440"
            placeholder="请输入新的倒计时时长"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="调整原因">
          <el-input
            v-model="adjustCountdownForm.reason"
            type="textarea"
            placeholder="请输入调整原因"
            :rows="3"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustCountdownDialogVisible = false"
          >取消</el-button
        >
        <el-button type="primary" @click="handleAdjustCountdownSubmit"
          >确定</el-button
        >
      </template>
    </el-dialog>

    <!-- 添加备注对话框 -->
    <el-dialog
      v-model="remarkDialogVisible"
      title="添加订单备注"
      width="500px"
      append-to-body
    >
      <el-form :model="remarkForm" label-width="120px">
        <el-form-item label="订单编号">
          <el-input v-model="remarkForm.orderNo" disabled />
        </el-form-item>
        <el-form-item label="备注内容">
          <el-input
            v-model="remarkForm.remark"
            type="textarea"
            placeholder="请输入备注内容"
            :rows="4"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="remarkDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleRemarkSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 列管理对话框 -->
    <el-dialog
      v-model="columnManageDialogVisible"
      title="列管理"
      width="500px"
      append-to-body
    >
      <div class="column-manage">
        <div class="column-list">
          <div
            v-for="(prop, index) in tableConfig.columnOrder"
            :key="prop"
            class="column-item"
            draggable="true"
            @dragstart="handleColumnDragStart($event, index)"
            @dragend="handleColumnDragEnd"
            @dragover="handleColumnDragOver($event)"
            @drop="handleColumnDrop($event, index)"
          >
            <el-icon class="drag-handle"><Rank /></el-icon>
            <span>{{ columnDefinitions[prop]?.label || prop }}</span>
            <el-switch
              v-model="columnVisible[prop]"
              @change="handleColumnVisibilityChange"
            />
          </div>
        </div>
        <div class="column-actions">
          <el-button @click="resetColumnOrder">重置顺序</el-button>
          <el-button @click="clearAllConfig" type="warning">清除配置</el-button>
          <el-button type="primary" @click="saveColumnConfig"
            >保存配置</el-button
          >
        </div>
      </div>
    </el-dialog>

    <!-- 批量操作对话框 -->
    <el-dialog
      v-model="batchDialogVisible"
      title="批量操作订单"
      width="600px"
      append-to-body
    >
      <el-form :model="batchForm" label-width="120px">
        <el-form-item label="操作类型">
          <el-select
            v-model="batchForm.operation"
            placeholder="选择操作类型"
            style="width: 100%"
          >
            <el-option label="修改订单状态" value="updateStatus" />
            <el-option label="状态切换（支持倒计时）" value="toggleStatus" />
            <el-option label="修改对接状态" value="updateDockStatus" />
            <el-option label="添加备注" value="addRemark" />
            <el-option label="批量补单" value="retryOrders" />
            <el-option label="批量取消" value="cancelOrders" />
          </el-select>
        </el-form-item>

        <!-- 修改订单状态 -->
        <el-form-item
          label="新订单状态"
          v-if="batchForm.operation === 'updateStatus'"
        >
          <el-select
            v-model="batchForm.value"
            placeholder="选择新状态"
            style="width: 100%"
          >
            <el-option
              v-for="option in variableStore.getStatusOptions('order_status')"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>

        <!-- 状态切换（支持倒计时） -->
        <el-form-item
          label="新订单状态"
          v-if="batchForm.operation === 'toggleStatus'"
        >
          <el-select
            v-model="batchForm.value"
            placeholder="选择新状态"
            style="width: 100%"
          >
            <el-option
              v-for="option in variableStore.getStatusOptions('order_status')"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>

        <!-- 倒计时配置（仅状态切换且选择进行中时显示） -->
        <el-form-item
          label="倒计时时长(分钟)"
          v-if="batchForm.operation === 'toggleStatus' && batchForm.value === 1"
        >
          <el-input-number
            v-model="batchForm.countdownDuration"
            :min="1"
            :max="1440"
            placeholder="请输入倒计时时长"
            style="width: 100%"
          />
          <span class="form-tip"
            >默认值：{{ defaultCountdownDuration }}分钟</span
          >
        </el-form-item>

        <!-- 自动完成配置（仅状态切换且选择进行中时显示） -->
        <el-form-item
          label="自动完成"
          v-if="batchForm.operation === 'toggleStatus' && batchForm.value === 1"
        >
          <el-switch v-model="batchForm.autoComplete" />
          <span class="form-tip">倒计时结束后是否自动完成订单</span>
        </el-form-item>

        <!-- 修改对接状态 -->
        <el-form-item
          label="新对接状态"
          v-if="batchForm.operation === 'updateDockStatus'"
        >
          <el-select
            v-model="batchForm.value"
            placeholder="选择新状态"
            style="width: 100%"
          >
            <el-option
              v-for="option in variableStore.getStatusOptions('dock_status')"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>

        <!-- 添加备注 -->
        <el-form-item
          label="备注内容"
          v-if="batchForm.operation === 'addRemark'"
        >
          <el-input
            v-model="batchForm.value"
            type="textarea"
            placeholder="请输入备注内容"
            :rows="3"
          />
        </el-form-item>

        <!-- 批量补单 -->
        <el-form-item
          label="补单说明"
          v-if="batchForm.operation === 'retryOrders'"
        >
          <el-input
            v-model="batchForm.value"
            type="textarea"
            placeholder="请输入补单说明（可选）"
            :rows="2"
          />
        </el-form-item>

        <!-- 批量取消 -->
        <el-form-item
          label="取消原因"
          v-if="batchForm.operation === 'cancelOrders'"
        >
          <el-input
            v-model="batchForm.value"
            type="textarea"
            placeholder="请输入取消原因"
            :rows="2"
          />
        </el-form-item>

        <el-form-item label="操作原因">
          <el-input
            v-model="batchForm.reason"
            type="textarea"
            placeholder="请输入操作原因"
            :rows="2"
          />
        </el-form-item>

        <el-form-item label="已选择">
          <el-tag type="success">{{ selectedOrders.length }} 个订单</el-tag>
        </el-form-item>

        <!-- 显示选中的订单列表 -->
        <el-form-item label="选中订单" v-if="selectedOrders.length > 0">
          <div
            style="
              max-height: 200px;
              overflow-y: auto;
              border: 1px solid #dcdfe6;
              padding: 10px;
              border-radius: 4px;
            "
          >
            <div
              v-for="order in selectedOrders.slice(0, 10)"
              :key="order.id"
              style="margin-bottom: 5px"
            >
              <el-tag size="small">{{ order.orderNo }}</el-tag>
              <span
                style="
                  margin-left: 10px;
                  font-size: 12px;
                  color: var(--color-text-regular);
                "
              >
                {{ getStatusText(order.orderStatus) }} -
                {{ order.studentAccount }}
              </span>
            </div>
            <div
              v-if="selectedOrders.length > 10"
              style="color: var(--color-text-secondary); font-size: 12px"
            >
              还有 {{ selectedOrders.length - 10 }} 个订单...
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="handleBatchSubmit"
          :loading="batchLoading"
        >
          确定执行
        </el-button>
      </template>
    </el-dialog>

    <!-- 订单详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="订单详情"
      width="800px"
      append-to-body
    >
      <el-descriptions :column="2" border v-if="currentOrder">
        <el-descriptions-item label="订单编号">{{
          currentOrder.orderNo
        }}</el-descriptions-item>
        <el-descriptions-item label="平台名称">{{
          currentOrder.platformName
        }}</el-descriptions-item>
        <el-descriptions-item label="学校名称">{{
          currentOrder.schoolName
        }}</el-descriptions-item>
        <el-descriptions-item label="学生姓名">{{
          currentOrder.studentName
        }}</el-descriptions-item>
        <el-descriptions-item label="学生账号">{{
          currentOrder.studentAccount
        }}</el-descriptions-item>
        <el-descriptions-item label="课程名称" :span="2">{{
          currentOrder.courseName
        }}</el-descriptions-item>
        <el-descriptions-item label="订单金额"
          >¥{{ currentOrder.amount }}</el-descriptions-item
        >
        <el-descriptions-item label="完成进度">{{
          currentOrder.progress || "0%"
        }}</el-descriptions-item>
        <el-descriptions-item label="订单状态">
          <el-tag :type="getStatusType(currentOrder.orderStatus)">
            {{ getStatusText(currentOrder.orderStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="对接状态">
          <el-tag :type="getDockStatusType(currentOrder.dockStatus)">
            {{ getDockStatusText(currentOrder.dockStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="补单次数">{{
          currentOrder.retryCount
        }}</el-descriptions-item>
        <el-descriptions-item label="是否秒刷">
          <el-tag :type="currentOrder.isFastMode ? 'success' : 'info'">
            {{ currentOrder.isFastMode ? "是" : "否" }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{
          currentOrder.createTime
        }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{
          currentOrder.updateTime
        }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">
          <div style="white-space: pre-wrap">
            {{ currentOrder.remarks || "无" }}
          </div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 订单导出对话框 -->
    <el-dialog
      v-model="exportDialogVisible"
      title="导出订单"
      width="500px"
      append-to-body
    >
      <el-form :model="exportForm" label-width="120px">
        <el-form-item label="导出格式">
          <el-select
            v-model="exportForm.format"
            placeholder="选择导出格式"
            style="width: 100%"
          >
            <el-option label="格式1：学校+账号+密码+课程名字" :value="1" />
            <el-option label="格式2：账号+密码+课程名字" :value="2" />
            <el-option label="格式3：学校+账号+密码" :value="3" />
            <el-option label="格式4：账号+密码" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件格式">
          <el-radio-group v-model="exportForm.fileType">
            <el-radio label="txt">TXT格式</el-radio>
            <el-radio label="xlsx">XLSX格式(Excel)</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="导出原因">
          <el-input
            v-model="exportForm.reason"
            placeholder="请输入导出原因（可选）"
          />
        </el-form-item>
        <el-form-item label="选中订单">
          <el-tag type="info">已选择 {{ selectedOrders.length }} 个订单</el-tag>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exportDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="confirmExport"
          :loading="exportLoading"
          >确认导出</el-button
        >
      </template>
    </el-dialog>

    <!-- 导出结果对话框 -->
    <el-dialog
      v-model="exportResultVisible"
      title="导出结果"
      width="600px"
      append-to-body
    >
      <div class="export-result">
        <el-alert
          title="导出成功"
          type="success"
          :closable="false"
          style="margin-bottom: 20px"
        >
          <template #default>
            <p>导出格式：{{ getFormatText(exportResult.format) }}</p>
            <p>导出数量：{{ exportResult.count }} 个订单</p>
            <p>导出时间：{{ exportResult.exportTime }}</p>
          </template>
        </el-alert>
        <el-input
          v-model="exportResult.content"
          type="textarea"
          :rows="15"
          readonly
          placeholder="导出内容"
        />
        <div style="margin-top: 15px; text-align: right">
          <el-button @click="copyToClipboard">复制到剪贴板</el-button>
          <el-button type="primary" @click="downloadAsFile">下载文件</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, nextTick } from "vue";
import {
  Document,
  Check,
  Money,
  Calendar,
  Refresh,
  Operation,
  Plus,
  Download,
  Close,
  Setting,
  Rank,
  Timer,
  View,
  Filter,
  Search,
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox, ElLoading } from "element-plus";
import { useResponsive } from "@/composables/useResponsive";
import {
  queryOrders,
  createOrder,
  retryOrder,
  deleteOrder,
  batchDeleteOrders,
  toggleSelfOperatedOrderStatus,
  adjustCountdown,
  getRemainingCountdown,
  completeOrder,
  exportOrders,
} from "@/api/order";
import { getCoursePlatforms } from "@/api/course";
import { useVariableStore } from "@/stores/variableStore";
import StatusDisplay from "@/components/StatusDisplay.vue";
import { getCountdownConfigs } from "@/api/countdownConfig";

// 使用响应式 composable
const { isMobile } = useResponsive();

// 变量store
const variableStore = useVariableStore();

// 响应式数据
const tableData = ref([]);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);
const loading = ref(false);
const selectedOrders = ref([]);
const platformList = ref([]);
const agentList = ref([]);
const dateRange = ref([]);

// 倒计时配置
const defaultCountdownDuration = ref(60);

// 密码显示控制
const showPassword = ref(false);

// 个性化配置
const tableConfig = ref({
  columnOrder: [
    "id",
    "orderNo",
    "platformName",
    "studentAccountPassword",
    "courseName",
    "amount",
    "progress",
    "orderStatus",
    "dockStatus",
    "retryCount",
    "createTime",
  ],
  columnWidths: {
    id: 80,
    orderNo: 200,
    platformName: 120,
    studentAccountPassword: 200,
    courseName: 200,
    amount: 100,
    progress: 100,
    orderStatus: 150,
    dockStatus: 150,
    retryCount: 100,
    createTime: 160,
  },
  sortConfig: {
    prop: "createTime",
    order: "descending",
  },
});

// 列配置定义
const columnDefinitions = ref({
  id: { label: "ID", width: 80, sortable: true },
  orderNo: {
    label: "订单编号",
    width: 200,
    showOverflowTooltip: true,
    sortable: true,
  },
  platformName: { label: "平台", width: 120 },
  studentAccountPassword: { label: "学生账号密码", width: 200 },
  courseName: { label: "课程名称", showOverflowTooltip: true },
  amount: { label: "金额", width: 100, sortable: true },
  progress: { label: "进度", width: 100 },
  orderStatus: { label: "订单状态", width: 150 },
  dockStatus: { label: "对接状态", width: 150 },
  retryCount: { label: "补单次数", width: 100 },
  createTime: { label: "创建时间", width: 160, sortable: true },
});

// 计算可见列
const visibleColumns = computed(() => {
  return tableConfig.value.columnOrder
    .filter((prop) => columnVisible.value[prop])
    .map((prop) => ({
      prop,
      ...columnDefinitions.value[prop],
    }));
});

// 统计信息
const statistics = ref({});

// 表格性能优化
const tableHeight = ref("auto");
const tableMaxHeight = ref(600);
const tableRef = ref(null);

// 搜索筛选优化 - 企业级混合方案
const searchLoading = ref(false);
const searchTimeout = ref(null);
const queryFormRef = ref(null);

// 常用筛选字段（默认展示）
const commonFields = ["orderNo", "platformId", "orderStatus"];
// 高级筛选字段
const advancedFields = ["studentAccount", "dockStatus", "userId", "dateRange"];

// 桌面端：折叠面板状态
const advancedSearchVisible = ref([]);
// 移动端：抽屉状态
const filterDrawerVisible = ref(false);

const showQueryFields = ref({
  orderNo: true,
  platformId: true,
  studentAccount: true,
  orderStatus: true,
  dockStatus: true,
  userId: true,
  dateRange: true,
});

// 表格行动画
const rowAnimations = ref(new Map());
const previousTableData = ref([]);

// 获取操作列宽度
const getOperationColumnWidth = () => {
  if (isMobile.value) {
    return 100; // 移动端只显示下拉菜单，宽度更小
  } else if (window.innerWidth <= 1200) {
    return 320; // 中等屏幕
  } else if (window.innerWidth <= 1600) {
    return 400; // 大屏幕
  } else {
    return 450; // 超大屏幕
  }
};

// 表格性能优化方法
const getRowKey = (row) => {
  return row?.id || row?.orderNo || Math.random().toString(36);
};

// 计算表格高度
const calculateTableHeight = () => {
  const windowHeight = window.innerHeight;
  const headerHeight = 200; // 页面头部高度
  const paginationHeight = 60; // 分页高度
  const padding = 40; // 内边距

  const availableHeight =
    windowHeight - headerHeight - paginationHeight - padding;
  tableMaxHeight.value = Math.max(300, availableHeight);

  if (tableData.value.length > 10) {
    tableHeight.value = tableMaxHeight.value;
  } else {
    tableHeight.value = "auto";
  }
};

// 实时搜索处理
const handleRealTimeSearch = () => {
  if (searchTimeout.value) {
    clearTimeout(searchTimeout.value);
  }

  searchTimeout.value = setTimeout(() => {
    handleSearch();
  }, 500); // 500ms防抖
};

// 切换查询字段显示
const toggleQueryFields = () => {
  ElMessageBox.prompt("请选择要显示的查询字段（用逗号分隔）", "字段设置", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    inputValue: Object.keys(showQueryFields.value)
      .filter((key) => showQueryFields.value[key])
      .join(","),
    inputPlaceholder:
      "orderNo,platformId,studentAccount,orderStatus,dockStatus,userId,dateRange",
  })
    .then(({ value }) => {
      const fields = value.split(",").map((field) => field.trim());
      const newShowFields = {};

      // 重置所有字段为false
      Object.keys(showQueryFields.value).forEach((key) => {
        newShowFields[key] = false;
      });

      // 设置选中的字段为true
      fields.forEach((field) => {
        if (showQueryFields.value.hasOwnProperty(field)) {
          newShowFields[field] = true;
        }
      });

      showQueryFields.value = newShowFields;
      ElMessage.success("字段设置已更新");
    })
    .catch(() => {
      // 用户取消
    });
};

// 获取表格行类名
const getRowClassName = ({ row, rowIndex }) => {
  const rowKey = getRowKey(row);
  const animationClass = rowAnimations.value.get(rowKey);
  return animationClass || "";
};

// 处理表格数据变化，添加动画效果
const handleTableDataChange = (newData) => {
  const currentData = newData || [];
  const previousData = previousTableData.value || [];

  // 检查哪些行发生了变化
  currentData.forEach((newRow, index) => {
    const rowKey = getRowKey(newRow);
    const oldRow = previousData.find((row) => getRowKey(row) === rowKey);

    if (oldRow) {
      // 检查状态是否发生变化
      if (oldRow.orderStatus !== newRow.orderStatus) {
        // 添加状态变化动画
        rowAnimations.value.set(rowKey, "status-changed");

        // 强制更新表格
        nextTick(() => {
          // 3秒后移除动画类
          setTimeout(() => {
            rowAnimations.value.delete(rowKey);
            // 强制重新渲染表格
            if (tableRef.value) {
              tableRef.value.doLayout();
            }
          }, 3000);
        });
      }
    }
  });

  // 更新之前的数据
  previousTableData.value = [...currentData];
};

// 查询表单
const queryForm = ref({
  orderNo: "",
  platformId: null,
  studentAccount: "",
  orderStatus: null,
  dockStatus: null,
  userId: null,
  page: 1,
  pageSize: 10,
});

// 对话框状态
const statusDialogVisible = ref(false);
const dockStatusDialogVisible = ref(false);
const remarkDialogVisible = ref(false);
const batchDialogVisible = ref(false);
const detailDialogVisible = ref(false);
const exportDialogVisible = ref(false);
const exportResultVisible = ref(false);
const columnManageDialogVisible = ref(false);

// 列可见性配置
const columnVisible = ref({
  id: true,
  orderNo: true,
  platformName: true,
  studentAccountPassword: true,
  courseName: true,
  amount: true,
  progress: true,
  orderStatus: true,
  dockStatus: true,
  retryCount: true,
  createTime: true,
});

// 表单数据
const statusForm = ref({
  orderId: null,
  orderNo: "",
  currentStatus: null,
  newStatus: null,
  reason: "",
});

const dockStatusForm = ref({
  orderId: null,
  orderNo: "",
  currentStatus: null,
  newStatus: null,
  reason: "",
});

const remarkForm = ref({
  orderId: null,
  orderNo: "",
  remark: "",
});

// 状态切换表单
const toggleStatusDialogVisible = ref(false);
const toggleStatusForm = ref({
  orderId: null,
  orderNo: "",
  currentStatus: null,
  newStatus: null,
  countdownDuration: 60,
  autoComplete: false,
  reason: "",
});

// 倒计时调整表单
const adjustCountdownDialogVisible = ref(false);
const adjustCountdownForm = ref({
  orderId: null,
  orderNo: "",
  currentDuration: 0,
  newDuration: 60,
  reason: "",
});

const batchForm = ref({
  operation: "",
  value: "",
  reason: "",
  countdownDuration: 60,
  autoComplete: false,
});

// 导出表单
const exportForm = ref({
  format: 1,
  exportFileType: "txt",
  reason: "",
});

// 导出结果
const exportResult = ref({
  content: "",
  format: 1,
  count: 0,
  exportTime: "",
});

const batchLoading = ref(false);
const exportLoading = ref(false);
const currentOrder = ref(null);

// 个性化配置相关方法
const loadTableConfig = () => {
  const savedConfig = localStorage.getItem("adminOrdersTableConfig");
  if (savedConfig) {
    try {
      const config = JSON.parse(savedConfig);
      // 加载列顺序配置
      if (config.columnOrder) {
        tableConfig.value.columnOrder = config.columnOrder;
      }
      // 加载排序配置
      if (config.sortConfig) {
        tableConfig.value.sortConfig = config.sortConfig;
      }
      // 加载列宽度配置
      if (config.columnWidths) {
        tableConfig.value.columnWidths = {
          ...tableConfig.value.columnWidths,
          ...config.columnWidths,
        };
      }
      // 加载列可见性配置
      if (config.columnVisible) {
        columnVisible.value = {
          ...columnVisible.value,
          ...config.columnVisible,
        };
      }
    } catch (error) {
      console.error("加载表格配置失败：", error);
    }
  }
};

const saveTableConfig = () => {
  const configToSave = {
    columnOrder: tableConfig.value.columnOrder,
    columnWidths: tableConfig.value.columnWidths,
    sortConfig: tableConfig.value.sortConfig,
    columnVisible: columnVisible.value,
  };
  localStorage.setItem("adminOrdersTableConfig", JSON.stringify(configToSave));
};

// 状态颜色相关方法
const getStatusClass = (status) => {
  const classMap = {
    0: "status-pending",
    1: "status-processing",
    2: "status-completed",
    3: "status-cancelled",
    4: "status-failed",
  };
  return classMap[status] || "";
};

const getStatusIndicatorClass = (status) => {
  const classMap = {
    0: "indicator-pending",
    1: "indicator-processing",
    2: "indicator-completed",
    3: "indicator-cancelled",
    4: "indicator-failed",
  };
  return classMap[status] || "";
};

// 密码更新方法
const handlePasswordUpdate = async (row) => {
  try {
    // 这里可以调用API更新密码
    ElMessage.success("密码更新成功");
  } catch (error) {
    ElMessage.error("密码更新失败");
  }
};

// 切换密码显示/隐藏
const togglePasswordDisplay = () => {
  showPassword.value = !showPassword.value;
};

// 排序变化处理
const handleSortChange = ({ prop, order }) => {
  tableConfig.value.sortConfig = { prop, order };
  saveTableConfig();
};

// 列拖拽处理
const handleColumnDrag = (event) => {
  event.preventDefault();
};

const handleColumnDragStart = (event, index) => {
  event.dataTransfer.setData("text/plain", index);
  event.dataTransfer.effectAllowed = "move";
  event.target.classList.add("dragging");
};

const handleColumnDragEnd = (event) => {
  event.target.classList.remove("dragging");
};

const handleColumnDragOver = (event) => {
  event.preventDefault();
  event.dataTransfer.dropEffect = "move";
};

const handleColumnDrop = (event, targetIndex) => {
  event.preventDefault();
  const fromIndex = parseInt(event.dataTransfer.getData("text/plain"));

  if (fromIndex !== targetIndex) {
    const newOrder = [...tableConfig.value.columnOrder];
    const [removed] = newOrder.splice(fromIndex, 1);
    newOrder.splice(targetIndex, 0, removed);
    tableConfig.value.columnOrder = newOrder;
  }
};

// 列管理相关方法
const handleColumnManage = () => {
  columnManageDialogVisible.value = true;
};

const handleColumnVisibilityChange = () => {
  // 列可见性变化时自动保存配置
  saveTableConfig();
};

const resetColumnOrder = () => {
  tableConfig.value.columnOrder = [
    "id",
    "orderNo",
    "platformName",
    "studentAccountPassword",
    "courseName",
    "amount",
    "progress",
    "orderStatus",
    "dockStatus",
    "retryCount",
    "createTime",
  ];
  // 重置列可见性
  columnVisible.value = {
    id: true,
    orderNo: true,
    platformName: true,
    studentAccountPassword: true,
    courseName: true,
    amount: true,
    progress: true,
    orderStatus: true,
    dockStatus: true,
    retryCount: true,
    createTime: true,
  };
};

const saveColumnConfig = () => {
  saveTableConfig();
  columnManageDialogVisible.value = false;
  ElMessage.success("列配置已保存");
};

const clearAllConfig = () => {
  localStorage.removeItem("adminOrdersTableConfig");
  resetColumnOrder();
  ElMessage.success("配置已清除，页面将刷新");
  setTimeout(() => {
    window.location.reload();
  }, 1000);
};

// 生命周期 - 统一初始化
onMounted(async () => {
  // 加载配置和变量
  loadTableConfig();
  await variableStore.loadAllVariables();
  await loadCountdownConfig();

  // 计算表格高度
  calculateTableHeight();

  // 加载数据
  loadOrders();
  loadPlatforms();
  loadAgentList();
  loadStatistics();
});

// 方法
const loadOrders = async () => {
  loading.value = true;
  try {
    const params = { ...queryForm.value };
    params.page = currentPage.value;
    params.pageSize = pageSize.value;

    // 添加时间范围查询
    if (dateRange.value && dateRange.value.length === 2) {
      params.startTime = dateRange.value[0];
      params.endTime = dateRange.value[1];
    }

    // 使用管理员专用接口查询所有订单
    const res = await fetch("/api/admin/orders/query-all", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${localStorage.getItem("token")}`,
      },
      body: JSON.stringify(params),
    });
    const data = await res.json();
    console.log("订单查询响应:", data);

    if (data.code === 1) {
      // 确保数据安全，添加默认值
      tableData.value = (data.data?.records || []).map((item) => ({
        id: item?.id || "",
        orderNo: item?.orderNo || "",
        platformName: item?.platformName || "",
        studentAccount: item?.studentAccount || "",
        studentPassword: item?.studentPassword || "",
        courseName: item?.courseName || "",
        amount: item?.amount || 0,
        progress: item?.progress || "0%",
        orderStatus: item?.orderStatus ?? 0,
        dockStatus: item?.dockStatus ?? 0,
        retryCount: item?.retryCount || 0,
        createTime: item?.createTime || "",
        isSelfOperated: item?.isSelfOperated || false,
        countdownEndTime: item?.countdownEndTime || null,
        countdownDuration: item?.countdownDuration || 0,
        ...item, // 保留其他属性
      }));
      total.value = data.data?.total || 0;

      // 处理表格数据变化，添加动画效果
      handleTableDataChange(tableData.value);

      // 调试：检查第一个订单的数据
      if (tableData.value.length > 0) {
        console.log("第一个订单数据:", tableData.value[0]);
        console.log(
          "第一个订单ID:",
          tableData.value[0].id,
          "类型:",
          typeof tableData.value[0].id
        );
        console.log("第一个订单编号:", tableData.value[0].orderNo);
      }
    } else {
      ElMessage.error(data.message || "查询失败");
      tableData.value = [];
      total.value = 0;
    }
  } catch (error) {
    console.error("加载订单失败：", error);
    ElMessage.error("加载订单失败");
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
    // 在数据加载完成后重新计算表格高度
    nextTick(() => {
      calculateTableHeight();
    });
  }
};

const loadPlatforms = async () => {
  try {
    const res = await getCoursePlatforms();
    if (res.code === 1) {
      platformList.value = res.data;
    }
  } catch (error) {
    console.error("加载平台列表失败：", error);
  }
};

// 加载代理账号列表
const loadAgentList = async () => {
  try {
    const res = await fetch("/api/admin/orders/agent-accounts", {
      method: "GET",
      headers: {
        Authorization: `Bearer ${localStorage.getItem("token")}`,
      },
    });
    const data = await res.json();
    if (data.code === 1) {
      agentList.value = data.data;
    }
  } catch (error) {
    console.error("加载代理账号列表失败：", error);
  }
};

// 加载倒计时配置
const loadCountdownConfig = async () => {
  try {
    const response = await getCountdownConfigs();
    if (response.code === 1) {
      const configs = response.data;
      defaultCountdownDuration.value = parseInt(
        configs.default_countdown_duration || "60"
      );
    }
  } catch (error) {
    console.error("加载倒计时配置失败:", error);
    // 如果加载失败，使用默认值60分钟
    defaultCountdownDuration.value = 60;
  }
};

const loadStatistics = async () => {
  try {
    const res = await fetch("/api/admin/orders/statistics", {
      method: "GET",
      headers: {
        Authorization: `Bearer ${localStorage.getItem("token")}`,
      },
    });
    const data = await res.json();
    if (data.code === 1) {
      statistics.value = data.data;
    }
  } catch (error) {
    console.error("加载统计信息失败：", error);
  }
};

const handleSearch = () => {
  currentPage.value = 1;
  loadOrders();
};

// 移动端抽屉内的查询按钮
const handleDrawerSearch = () => {
  filterDrawerVisible.value = false;
  handleSearch();
};

const handleResetQuery = () => {
  queryForm.value = {
    orderNo: "",
    platformId: null,
    studentAccount: "",
    orderStatus: null,
    dockStatus: null,
    userId: null,
    page: 1,
    pageSize: 10,
  };
  dateRange.value = [];
  currentPage.value = 1;
  loadOrders();
};

const handleSelectionChange = (selection) => {
  selectedOrders.value = selection;
};

// 移动端操作命令分发
const handleOperationCommand = (command, row) => {
  switch (command) {
    case "view":
      handleView(row);
      break;
    case "status":
      handleForceUpdateStatus(row);
      break;
    case "dock":
      handleForceUpdateDockStatus(row);
      break;
    case "remark":
      handleAddRemark(row);
      break;
    case "retry":
      handleRetry(row);
      break;
    case "toggle":
      handleToggleStatus(row);
      break;
    case "countdown":
      handleAdjustCountdown(row);
      break;
    case "delete":
      handleDelete(row);
      break;
  }
};

const handleView = (row) => {
  currentOrder.value = row;
  detailDialogVisible.value = true;
};

const handleForceUpdateStatus = (row) => {
  statusForm.value = {
    orderId: row.id,
    orderNo: row.orderNo,
    currentStatus: row.orderStatus,
    newStatus: null,
    reason: "",
  };
  statusDialogVisible.value = true;
};

const handleForceUpdateDockStatus = (row) => {
  dockStatusForm.value = {
    orderId: row.id,
    orderNo: row.orderNo,
    currentStatus: row.dockStatus,
    newStatus: null,
    reason: "",
  };
  dockStatusDialogVisible.value = true;
};

const handleAddRemark = (row) => {
  remarkForm.value = {
    orderId: row.id,
    orderNo: row.orderNo,
    remark: "",
  };
  remarkDialogVisible.value = true;
};

const handleBatchOperation = async () => {
  // 从倒计时配置中读取自动完成设置
  let autoCompleteEnabled = false;
  try {
    const countdownConfigs = await getCountdownConfigs();
    autoCompleteEnabled =
      countdownConfigs.code === 1 &&
      countdownConfigs.data.auto_complete_enabled === "1";
  } catch (error) {
    console.warn("读取倒计时配置失败，使用默认值:", error);
  }

  batchForm.value = {
    operation: "",
    value: "",
    reason: "",
    countdownDuration: defaultCountdownDuration.value,
    autoComplete: autoCompleteEnabled, // 使用配置中的设置
  };
  batchDialogVisible.value = true;
};

const handleStatusSubmit = async () => {
  try {
    const res = await fetch(
      `/api/admin/orders/${statusForm.value.orderId}/force-update-status`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          Authorization: `Bearer ${localStorage.getItem("token")}`,
        },
        body: `newStatus=${
          statusForm.value.newStatus
        }&reason=${encodeURIComponent(statusForm.value.reason)}`,
      }
    );
    const data = await res.json();
    if (data.code === 1) {
      ElMessage.success("状态修改成功");
      statusDialogVisible.value = false;
      loadOrders();
      loadStatistics();
    } else {
      ElMessage.error(data.message || "修改失败");
    }
  } catch (error) {
    console.error("修改状态失败：", error);
    ElMessage.error("修改状态失败");
  }
};

const handleDockStatusSubmit = async () => {
  try {
    const res = await fetch(
      `/api/admin/orders/${dockStatusForm.value.orderId}/force-update-dock-status`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          Authorization: `Bearer ${localStorage.getItem("token")}`,
        },
        body: `newStatus=${
          dockStatusForm.value.newStatus
        }&reason=${encodeURIComponent(dockStatusForm.value.reason)}`,
      }
    );
    const data = await res.json();
    if (data.code === 1) {
      ElMessage.success("对接状态修改成功");
      dockStatusDialogVisible.value = false;
      loadOrders();
    } else {
      ElMessage.error(data.message || "修改失败");
    }
  } catch (error) {
    console.error("修改对接状态失败：", error);
    ElMessage.error("修改对接状态失败");
  }
};

const handleRemarkSubmit = async () => {
  try {
    const res = await fetch(
      `/api/admin/orders/${remarkForm.value.orderId}/add-remark`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          Authorization: `Bearer ${localStorage.getItem("token")}`,
        },
        body: `remark=${encodeURIComponent(remarkForm.value.remark)}`,
      }
    );
    const data = await res.json();
    if (data.code === 1) {
      ElMessage.success("备注添加成功");
      remarkDialogVisible.value = false;
      loadOrders();
    } else {
      ElMessage.error(data.message || "添加失败");
    }
  } catch (error) {
    console.error("添加备注失败：", error);
    ElMessage.error("添加备注失败");
  }
};

const handleBatchSubmit = async () => {
  if (!batchForm.value.operation) {
    ElMessage.warning("请选择操作类型");
    return;
  }

  if (selectedOrders.value.length === 0) {
    ElMessage.warning("请选择要操作的订单");
    return;
  }

  batchLoading.value = true;
  try {
    const orderIds = selectedOrders.value.map((order) => order.id);

    let url = "";
    let requestBody = {};

    // 根据操作类型选择不同的API接口
    switch (batchForm.value.operation) {
      case "updateStatus":
        url = "/api/admin/orders/batch/update-order-status";
        requestBody = {
          orderIds: orderIds,
          status: batchForm.value.value,
          reason: batchForm.value.reason,
        };
        break;
      case "toggleStatus":
        // 批量状态切换（支持倒计时）
        url = "/api/admin/orders/batch/toggle-status";

        // 如果切换到进行中状态，从配置中读取自动完成设置
        let autoCompleteValue = batchForm.value.autoComplete;
        if (batchForm.value.value === 1) {
          try {
            const countdownConfigs = await getCountdownConfigs();
            autoCompleteValue =
              countdownConfigs.code === 1 &&
              countdownConfigs.data.auto_complete_enabled === "1";
          } catch (error) {
            console.warn("读取倒计时配置失败，使用表单设置:", error);
          }
        }

        requestBody = {
          orderIds: orderIds,
          newStatus: batchForm.value.value,
          countdownDuration: batchForm.value.countdownDuration,
          autoComplete: autoCompleteValue, // 使用配置中的设置
          reason: batchForm.value.reason,
        };
        break;
      case "updateDockStatus":
        url = "/api/admin/orders/batch/update-dock-status";
        requestBody = {
          orderIds: orderIds,
          dockStatus: batchForm.value.value,
          reason: batchForm.value.reason,
        };
        break;
      case "addRemark":
        url = "/api/admin/orders/batch/add-remarks";
        requestBody = {
          orderIds: orderIds,
          remark: batchForm.value.value,
        };
        break;
      case "retryOrders":
        url = "/api/admin/orders/batch/retry-orders";
        requestBody = {
          orderIds: orderIds,
          reason: batchForm.value.value || batchForm.value.reason,
        };
        break;
      case "cancelOrders":
        // 批量取消订单
        url = "/api/admin/orders/batch/update-order-status";
        requestBody = {
          orderIds: orderIds,
          status: 3,
          reason: batchForm.value.value || batchForm.value.reason,
        };
        break;
      default:
        ElMessage.error("不支持的操作类型");
        return;
    }

    const res = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${localStorage.getItem("token")}`,
      },
      body: JSON.stringify(requestBody),
    });
    const data = await res.json();
    if (data.code === 1) {
      ElMessage.success(data.message || "批量操作成功");
      batchDialogVisible.value = false;
      loadOrders();
      loadStatistics();
    } else {
      ElMessage.error(data.message || "批量操作失败");
    }
  } catch (error) {
    console.error("批量操作失败：", error);
    ElMessage.error("批量操作失败");
  } finally {
    batchLoading.value = false;
  }
};

const handleRetry = async (row) => {
  try {
    await retryOrder(row.id);
    ElMessage.success("补单成功");
    loadOrders();
  } catch (error) {
    console.error("补单失败：", error);
    ElMessage.error("补单失败");
  }
};

// 处理订单状态变更
const handleStatusChange = async (row) => {
  try {
    // 记录原始状态
    const originalStatus = row.orderStatus;

    // 如果切换到进行中状态且是自营订单，使用状态切换API启动倒计时
    if (row.orderStatus === 1 && row.isSelfOperated) {
      // 从倒计时配置中读取自动完成设置
      const countdownConfigs = await getCountdownConfigs();
      const autoCompleteEnabled =
        countdownConfigs.code === 1 &&
        countdownConfigs.data.auto_complete_enabled === "1";

      const params = {
        newStatus: row.orderStatus,
        countdownDuration: defaultCountdownDuration.value,
        autoComplete: autoCompleteEnabled, // 使用配置中的设置
        reason: "管理员直接修改状态",
      };

      const result = await toggleSelfOperatedOrderStatus(row.id, params);
      ElMessage.success("订单状态修改成功，已启动倒计时");
    } else {
      // 其他情况使用原有的强制修改状态API
      const res = await fetch(
        `/api/admin/orders/${row.id}/force-update-status`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/x-www-form-urlencoded",
            Authorization: `Bearer ${localStorage.getItem("token")}`,
          },
          body: `newStatus=${row.orderStatus}&reason=管理员直接修改`,
        }
      );
      const data = await res.json();
      if (data.code === 1) {
        ElMessage.success("订单状态修改成功");
      } else {
        ElMessage.error(data.message || "修改失败");
        // 恢复原状态
        loadOrders();
        return;
      }
    }

    // 触发状态变化动画
    const rowKey = getRowKey(row);
    rowAnimations.value.set(rowKey, "status-changed");

    // 3秒后移除动画类
    setTimeout(() => {
      rowAnimations.value.delete(rowKey);
    }, 3000);

    loadStatistics();
  } catch (error) {
    console.error("修改订单状态失败：", error);
    ElMessage.error("修改订单状态失败");
    // 恢复原状态
    loadOrders();
  }
};

// 处理对接状态变更
const handleDockStatusChange = async (row) => {
  try {
    const res = await fetch(
      `/api/admin/orders/${row.id}/force-update-dock-status`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          Authorization: `Bearer ${localStorage.getItem("token")}`,
        },
        body: `newStatus=${row.dockStatus}&reason=管理员直接修改`,
      }
    );
    const data = await res.json();
    if (data.code === 1) {
      ElMessage.success("对接状态修改成功");
    } else {
      ElMessage.error(data.message || "修改失败");
      // 恢复原状态
      loadOrders();
    }
  } catch (error) {
    console.error("修改对接状态失败：", error);
    ElMessage.error("修改对接状态失败");
    // 恢复原状态
    loadOrders();
  }
};

const handleCreate = () => {
  // 跳转到订单创建页面或打开创建对话框
  ElMessage.info("订单创建功能");
};

// 快捷批量完成
const handleQuickComplete = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要将选中的 ${selectedOrders.value.length} 个订单标记为已完成吗？`,
      "批量完成确认",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }
    );

    const orderIds = selectedOrders.value.map((order) => order.id);
    const res = await fetch("/api/admin/orders/batch/update-order-status", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${localStorage.getItem("token")}`,
      },
      body: JSON.stringify({
        orderIds: orderIds,
        status: 2,
        reason: "管理员批量完成",
      }),
    });
    const data = await res.json();
    if (data.code === 1) {
      ElMessage.success("批量完成成功");
      loadOrders();
      loadStatistics();
    } else {
      ElMessage.error(data.message || "批量完成失败");
    }
  } catch (error) {
    if (error !== "cancel") {
      console.error("批量完成失败：", error);
      ElMessage.error("批量完成失败");
    }
  }
};

// 快捷批量取消
const handleQuickCancel = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要取消选中的 ${selectedOrders.value.length} 个订单吗？`,
      "批量取消确认",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }
    );

    const orderIds = selectedOrders.value.map((order) => order.id);
    const res = await fetch("/api/admin/orders/batch/update-order-status", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${localStorage.getItem("token")}`,
      },
      body: JSON.stringify({
        orderIds: orderIds,
        status: 3,
        reason: "管理员批量取消",
      }),
    });
    const data = await res.json();
    if (data.code === 1) {
      ElMessage.success("批量取消成功");
      loadOrders();
      loadStatistics();
    } else {
      ElMessage.error(data.message || "批量取消失败");
    }
  } catch (error) {
    if (error !== "cancel") {
      console.error("批量取消失败：", error);
      ElMessage.error("批量取消失败");
    }
  }
};

// 复制到剪贴板
const copyToClipboard = async () => {
  try {
    await navigator.clipboard.writeText(exportResult.value.content);
    ElMessage.success("已复制到剪贴板");
  } catch (error) {
    console.error("复制失败:", error);
    ElMessage.error("复制失败，请手动复制");
  }
};

// 工具方法
// 使用系统变量获取订单状态名称
const getStatusText = (status) => {
  // Source: 系统变量管理（前端 variableStore + 后端 SystemVariable）
  return variableStore.getVariableName("order_status", status);
};

// 获取倒计时文本
const getCountdownText = (row) => {
  if (!row.countdownEndTime) return "";

  try {
    const endTime = new Date(row.countdownEndTime);
    const now = new Date();
    const diff = endTime.getTime() - now.getTime();

    if (diff <= 0) {
      return "已过期";
    }

    const hours = Math.floor(diff / (1000 * 60 * 60));
    const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
    const seconds = Math.floor((diff % (1000 * 60)) / 1000);

    if (hours > 0) {
      return `${hours}时${minutes}分`;
    } else if (minutes > 0) {
      return `${minutes}分${seconds}秒`;
    } else {
      return `${seconds}秒`;
    }
  } catch (error) {
    console.error("计算倒计时失败:", error);
    return "计算错误";
  }
};

// 使用系统变量获取订单状态对应的标签类型
const getStatusType = (status) => {
  // Source: 系统变量管理（前端 variableStore + 后端 SystemVariable）
  return variableStore.getVariableTagType("order_status", status);
};

// 使用系统变量获取对接状态名称
const getDockStatusText = (status) => {
  // Source: 系统变量管理（前端 variableStore + 后端 SystemVariable）
  return variableStore.getVariableName("dock_status", status);
};

// 使用系统变量获取对接状态对应的标签类型
const getDockStatusType = (status) => {
  // Source: 系统变量管理（前端 variableStore + 后端 SystemVariable）
  return variableStore.getVariableTagType("dock_status", status);
};

// ========== 新增功能处理函数 ==========

// 删除订单
const handleDelete = async (row) => {
  try {
    // 检查row是否存在和orderNo属性
    if (!row || !row.orderNo) {
      ElMessage.error("订单数据异常，无法删除");
      return;
    }

    await ElMessageBox.confirm(
      `确定要删除订单 ${row.orderNo} 吗？\n\n 此操作不可撤销，请谨慎操作！`,
      "删除确认",
      {
        confirmButtonText: "确定删除",
        cancelButtonText: "取消",
        type: "warning",
        dangerouslyUseHTMLString: false,
        customClass: "delete-confirm-dialog",
      }
    );

    // 显示删除进度
    const loadingInstance = ElLoading.service({
      lock: true,
      text: "正在删除订单...",
      background: "rgba(0, 0, 0, 0.7)",
    });

    try {
      await deleteOrder(row.id, "管理员删除");
      ElMessage.success({
        message: `订单 ${row.orderNo} 删除成功`,
        duration: 3000,
        showClose: true,
      });
      loadOrders();
    } finally {
      loadingInstance.close();
    }
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error({
        message: "删除失败：" + (error.message || "未知错误"),
        duration: 5000,
        showClose: true,
      });
    }
  }
};

// 自营订单状态切换
const handleToggleStatus = async (row) => {
  console.log("状态切换 - 完整订单数据:", JSON.stringify(row, null, 2));
  console.log("状态切换 - 订单ID:", row.id, "类型:", typeof row.id);
  console.log("状态切换 - 订单编号:", row.orderNo);

  if (!row.id) {
    ElMessage.error("订单ID不能为空");
    return;
  }

  // 从倒计时配置中读取自动完成设置
  let autoCompleteEnabled = false;
  try {
    const countdownConfigs = await getCountdownConfigs();
    autoCompleteEnabled =
      countdownConfigs.code === 1 &&
      countdownConfigs.data.auto_complete_enabled === "1";
  } catch (error) {
    console.warn("读取倒计时配置失败，使用默认值:", error);
  }

  toggleStatusForm.value.orderId = row.id;
  toggleStatusForm.value.orderNo = row.orderNo;
  toggleStatusForm.value.currentStatus = row.orderStatus;
  toggleStatusForm.value.newStatus = null;
  // 使用配置中的默认倒计时时长
  toggleStatusForm.value.countdownDuration = defaultCountdownDuration.value;
  toggleStatusForm.value.autoComplete = autoCompleteEnabled; // 使用配置中的设置
  toggleStatusForm.value.reason = "";
  toggleStatusDialogVisible.value = true;
};

// 调整倒计时
const handleAdjustCountdown = (row) => {
  if (!row.id) {
    ElMessage.error("订单ID不能为空");
    return;
  }

  console.log("倒计时调整 - 订单数据:", row);
  console.log("倒计时调整 - 订单编号:", row.orderNo);

  adjustCountdownForm.value.orderId = row.id;
  adjustCountdownForm.value.orderNo = row.orderNo;
  adjustCountdownForm.value.currentDuration = row.countdownDuration || 0;
  adjustCountdownForm.value.newDuration =
    row.countdownDuration || defaultCountdownDuration.value;
  adjustCountdownForm.value.reason = "";
  adjustCountdownDialogVisible.value = true;
};

// 状态切换提交
const handleToggleStatusSubmit = async () => {
  if (!toggleStatusForm.value.orderId) {
    ElMessage.error("订单ID不能为空");
    return;
  }

  console.log("准备发送状态切换请求");
  console.log("订单ID:", toggleStatusForm.value.orderId);
  console.log("表单数据:", toggleStatusForm.value);

  try {
    const params = {
      newStatus: toggleStatusForm.value.newStatus,
      countdownDuration: toggleStatusForm.value.countdownDuration,
      autoComplete: toggleStatusForm.value.autoComplete,
      reason: toggleStatusForm.value.reason,
    };

    console.log("请求参数:", params);
    console.log("开始调用API...");

    const result = await toggleSelfOperatedOrderStatus(
      toggleStatusForm.value.orderId,
      params
    );
    console.log("API调用成功:", result);

    ElMessage.success("状态切换成功");
    toggleStatusDialogVisible.value = false;
    loadOrders();
  } catch (error) {
    console.error("API调用失败:", error);
    ElMessage.error("状态切换失败: " + (error.message || "未知错误"));
  }
};

// 倒计时调整提交
const handleAdjustCountdownSubmit = async () => {
  if (!adjustCountdownForm.value.orderId) {
    ElMessage.error("订单ID不能为空");
    return;
  }

  try {
    const data = {
      newDuration: adjustCountdownForm.value.newDuration,
      reason: adjustCountdownForm.value.reason,
    };

    await adjustCountdown(adjustCountdownForm.value.orderId, data);
    ElMessage.success("倒计时调整成功");
    adjustCountdownDialogVisible.value = false;
    loadOrders();
  } catch (error) {
    ElMessage.error("倒计时调整失败");
  }
};

// ========== 订单导出相关函数 ==========

// 打开导出对话框
const handleExport = () => {
  if (!selectedOrders.value || selectedOrders.value.length === 0) {
    ElMessage.warning("请先选择要导出的订单");
    return;
  }

  // 重置导出表单
  exportForm.value = {
    format: 1,
    fileType: "txt",
    reason: "",
  };

  exportDialogVisible.value = true;
};

// 确认导出
const confirmExport = async () => {
  if (!selectedOrders.value || selectedOrders.value.length === 0) {
    ElMessage.warning("没有选中的订单");
    return;
  }

  const orderIds = selectedOrders.value.map((order) => order.id);

  exportLoading.value = true;
  try {
    const response = await exportOrders({
      orderIds,
      format: exportForm.value.format,
      fileType: exportForm.value.fileType,
      reason: exportForm.value.reason,
    });

    if (exportForm.value.fileType === "xlsx") {
      // XLSX格式 - 直接下载文件
      let blob;
      // If response is already a Blob, use it directly; otherwise, create a Blob
      if (response instanceof Blob) {
        blob = response;
      } else {
        blob = new Blob([response], {
          type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        });
      }
      // Check if the blob is actually a JSON error
      if (blob.type === "application/json") {
        // Read the blob as text and parse the error message
        const reader = new FileReader();
        reader.onload = function () {
          try {
            const errorJson = JSON.parse(reader.result);
            ElMessage.error(errorJson.message || "导出失败");
          } catch (e) {
            ElMessage.error("导出失败: 返回了无效的响应");
          }
        };
        reader.readAsText(blob);
      } else {
        const link = document.createElement("a");
        link.href = window.URL.createObjectURL(blob);
        const timestamp = new Date()
          .toISOString()
          .replace(/[-:]/g, "")
          .replace(/T/, "_")
          .split(".")[0];
        link.download = `订单导出_${timestamp}.xlsx`;
        link.click();
        window.URL.revokeObjectURL(link.href);

        ElMessage.success({
          message: `成功导出 ${orderIds.length} 个订单（XLSX格式）`,
          duration: 3000,
        });
        exportDialogVisible.value = false;
      }
    } else {
      // TXT格式 - 显示结果对话框
      if (response.code === 1 && response.data) {
        exportResult.value = response.data;
        exportDialogVisible.value = false;
        exportResultVisible.value = true;
      } else {
        ElMessage.error(response.message || "导出失败");
      }
    }
  } catch (error) {
    console.error("导出失败:", error);
    ElMessage.error("导出失败: " + (error.message || "未知错误"));
  } finally {
    exportLoading.value = false;
  }
};

// 下载为文件
const downloadAsFile = () => {
  if (!exportResult.value.content) {
    ElMessage.warning("没有可下载的内容");
    return;
  }

  const blob = new Blob([exportResult.value.content], { type: "text/plain" });
  const link = document.createElement("a");
  link.href = window.URL.createObjectURL(blob);
  const timestamp = new Date()
    .toISOString()
    .replace(/[-:]/g, "")
    .replace(/T/, "_")
    .split(".")[0];
  link.download = `订单导出_${timestamp}.txt`;
  link.click();
  window.URL.revokeObjectURL(link.href);

  ElMessage.success("文件下载成功");
};

// 获取格式文本
const getFormatText = (format) => {
  const formatMap = {
    1: "格式1：学校+账号+密码+课程名字",
    2: "格式2：账号+密码+课程名字",
    3: "格式3：学校+账号+密码",
    4: "格式4：账号+密码",
  };
  return formatMap[format] || "未知格式";
};
</script>

<style scoped>
.admin-orders-page {
  padding: 20px;
}

.statistics-cards {
  margin-bottom: 20px;
}

.stat-card {
  position: relative;
  overflow: hidden;
}

.stat-card .stat-content {
  position: relative;
  z-index: 2;
}

.stat-card .stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 8px;
}

.stat-card .stat-label {
  font-size: 14px;
  color: var(--color-info);
}

.stat-card .stat-icon {
  position: absolute;
  right: 20px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 40px;
  opacity: 0.3;
  color: var(--color-primary);
}

.stat-card .stat-icon.success {
  color: var(--color-success);
}

.stat-card .stat-icon.warning {
  color: var(--color-warning);
}

.stat-card .stat-icon.info {
  color: var(--color-info);
}

.main-card {
  margin-bottom: 20px;
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

.query-form {
  margin-bottom: 20px;
}

.query-items {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
}

.query-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-left: auto;
}

/* 查询字段动画效果 */
.query-item-enter-active,
.query-item-leave-active {
  transition: all 0.3s ease;
}

.query-item-enter-from {
  opacity: 0;
  transform: translateX(-20px);
}

.query-item-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

.query-item-move {
  transition: transform 0.3s ease;
}

/* 表格行动画效果 */
.animated-table {
  position: relative;
}

/* 表格行状态变化动画 */
.animated-table .status-changed {
  animation: statusChange 2s ease-in-out !important;
}

@keyframes statusChange {
  0% {
    background-color: #fff3cd !important;
    transform: scale(1);
  }
  25% {
    background-color: #ffeaa7 !important;
    transform: scale(1.02);
  }
  50% {
    background-color: #fdcb6e !important;
    transform: scale(1.01);
  }
  75% {
    background-color: #ffeaa7 !important;
    transform: scale(1.02);
  }
  100% {
    background-color: #fff3cd !important;
    transform: scale(1);
  }
}

/* 表格行进入动画 */
.animated-table .el-table__row {
  transition: all 0.3s ease;
}

.animated-table .el-table__row:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
}

.pagination {
  margin-top: 20px;
  text-align: right;
}

/* 状态颜色样式 */
.status-pending .el-input__wrapper {
  border-color: var(--color-primary);
  background-color: rgba(78, 140, 255, 0.1);
}

.status-processing .el-input__wrapper {
  border-color: var(--color-warning);
  background-color: rgba(247, 166, 47, 0.1);
}

.status-completed .el-input__wrapper {
  border-color: var(--color-success);
  background-color: rgba(99, 197, 110, 0.1);
}

.status-cancelled .el-input__wrapper {
  border-color: var(--color-info);
  background-color: rgba(144, 147, 153, 0.1);
}

.status-failed .el-input__wrapper {
  border-color: var(--color-danger);
  background-color: rgba(240, 101, 101, 0.1);
}

/* 状态指示器 */
.status-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  margin-left: 8px;
  vertical-align: middle;
}

.indicator-pending {
  background-color: var(--color-primary);
  box-shadow: 0 0 6px rgba(78, 140, 255, 0.6);
}

.indicator-processing {
  background-color: var(--color-warning);
  box-shadow: 0 0 6px rgba(247, 166, 47, 0.6);
}

.indicator-completed {
  background-color: var(--color-success);
  box-shadow: 0 0 6px rgba(99, 197, 110, 0.6);
}

.indicator-cancelled {
  background-color: var(--color-info);
  box-shadow: 0 0 6px rgba(144, 147, 153, 0.6);
}

.indicator-failed {
  background-color: var(--color-danger);
  box-shadow: 0 0 6px rgba(240, 101, 101, 0.6);
}

/* 密码输入框样式 */
.el-table .el-input--small .el-input__wrapper {
  border-radius: 4px;
  transition: all 0.3s;
}

.el-table .el-input--small .el-input__wrapper:hover {
  border-color: var(--color-primary);
}

/* 表格拖拽样式 */
.el-table .el-table__header-wrapper th {
  cursor: move;
  user-select: none;
}

.el-table .el-table__header-wrapper th:hover {
  background-color: #f5f7fa;
}

/* 列管理对话框样式 */
.column-manage {
  padding: 20px 0;
}

.column-list {
  max-height: 400px;
  overflow-y: auto;
  margin-bottom: 20px;
}

.column-item {
  display: flex;
  align-items: center;
  padding: 12px;
  margin-bottom: 8px;
  background-color: #f8f9fa;
  border-radius: 6px;
  cursor: move;
  transition: all 0.3s;
}

.column-item:hover {
  background-color: #e9ecef;
  transform: translateY(-1px);
}

.column-item.dragging {
  opacity: 0.5;
  transform: rotate(2deg);
}

.drag-handle {
  margin-right: 12px;
  color: var(--color-info);
  cursor: grab;
}

.drag-handle:active {
  cursor: grabbing;
}

.column-item span {
  flex: 1;
  font-weight: 500;
}

.column-actions {
  display: flex;
  justify-content: space-between;
  padding-top: 20px;
  border-top: 1px solid #e4e7ed;
}

/* 表单提示样式 */
.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: var(--color-info);
}

/* 操作列响应式样式 */
.operation-buttons {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.operation-buttons > div {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.operation-buttons .el-button {
  margin: 0;
  padding: 4px 8px;
  font-size: 12px;
  min-width: auto;
  flex-shrink: 0;
}

/* 移动端操作列优化 */
.mobile-operations {
  display: flex;
  justify-content: center;
  align-items: center;
}

.mobile-operations .el-button {
  width: 100%;
  padding: 8px 12px;
}

/* 企业级筛选样式 */
.mobile-filter-trigger {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.mobile-filter-trigger .el-button {
  flex: 1;
}

.desktop-filter-wrapper {
  margin-bottom: 20px;
}

.common-filter-form {
  margin-bottom: 12px;
}

.advanced-filter-collapse {
  border-top: 1px solid var(--border-color-light);
  padding-top: 12px;
}

.advanced-filter-collapse :deep(.el-collapse-item__header) {
  font-weight: 500;
  color: #606266;
}

@media (max-width: 767px) {
  .admin-orders-page {
    padding: 12px;
  }

  /* 统计卡片移动端优化 */
  .statistics-cards {
    margin-bottom: 16px;
  }

  .stat-card .stat-value {
    font-size: 20px;
    margin-bottom: 4px;
  }

  .stat-card .stat-label {
    font-size: 12px;
  }

  .stat-card .stat-icon {
    font-size: 32px;
    opacity: 0.3;
  }

  /* 移动端筛选抽屉内表单优化 */
  .el-drawer__body .el-form {
    padding: 0 12px;
  }

  .el-drawer__body .el-form-item {
    margin-bottom: 20px;
  }

  /* 头部操作按钮移动端优化 */
  .header-actions {
    flex-wrap: wrap;
    gap: 8px;
  }

  .header-actions .el-button {
    font-size: 12px;
    padding: 8px 12px;
  }

  /* 操作按钮优化 */
  .operation-buttons {
    gap: 3px;
  }

  .operation-buttons .el-button {
    padding: 2px 6px;
    font-size: 11px;
    min-width: 40px;
  }

  .operation-buttons > div {
    gap: 2px;
  }

  /* 下拉菜单项优化 */
  :deep(.el-dropdown-menu__item) {
    padding: 10px 16px;
    font-size: 14px;
  }

  :deep(.el-dropdown-menu__item .el-icon) {
    margin-right: 8px;
    font-size: 16px;
  }

  /* 表格移动端优化 */
  :deep(.el-table) {
    font-size: 12px;
  }

  :deep(.el-table th.el-table__cell) {
    padding: 8px 0;
    font-size: 12px;
  }

  :deep(.el-table td.el-table__cell) {
    padding: 8px 0;
  }

  /* 分页器移动端优化 */
  .pagination {
    justify-content: center;
  }

  :deep(.el-pagination) {
    flex-wrap: wrap;
    justify-content: center;
  }
}

/* 中等屏幕优化 */
@media (min-width: 768px) and (max-width: 1199px) {
  .operation-buttons .el-button {
    padding: 3px 6px;
    font-size: 11px;
  }

  .secondary-actions .el-button {
    font-size: 10px;
    padding: 2px 4px;
  }
}

/* 大屏幕优化 */
@media (min-width: 1200px) and (max-width: 1600px) {
  .operation-buttons .el-button {
    padding: 4px 8px;
    font-size: 12px;
  }
}

/* 表格操作列固定宽度优化 */
.operation-column {
  min-width: 200px;
}

@media (min-width: 768px) {
  .operation-column {
    min-width: 320px;
  }
}

@media (min-width: 1200px) {
  .operation-column {
    min-width: 400px;
  }
}

@media (min-width: 1601px) {
  .operation-column {
    min-width: 450px;
  }
}

/* ========== Dark Mode 适配 ========== */
/* 统计卡片 */
html.dark .stat-card {
  background: var(--bg-card);
  border-color: var(--border-color);
}

html.dark .stat-value {
  color: var(--text-primary);
}

html.dark .stat-label {
  color: var(--text-secondary);
}

/* 高级筛选折叠面板 */
html.dark .advanced-filter-collapse {
  border-color: var(--border-color);
  background-color: transparent;
}

html.dark .advanced-filter-collapse :deep(.el-collapse-item__header) {
  background-color: transparent;
  color: var(--text-primary);
  border-bottom-color: var(--border-color);
}

html.dark .advanced-filter-collapse :deep(.el-collapse-item__wrap) {
  background-color: transparent;
  border-bottom-color: var(--border-color);
}

html.dark .advanced-filter-collapse :deep(.el-collapse-item__content) {
  background-color: transparent;
  color: var(--text-regular);
}

/* 移动端筛选触发器 */
html.dark .mobile-filter-trigger {
  background-color: transparent;
}

/* 表单项标签 */
html.dark .el-form-item__label {
  color: var(--text-regular);
}

/* 列管理 */
html.dark .column-item {
  background-color: rgba(255, 255, 255, 0.05);
  color: var(--text-primary);
}

html.dark .column-item:hover {
  background-color: rgba(255, 255, 255, 0.08);
}

html.dark .drag-handle {
  color: var(--text-secondary);
}

/* 操作按钮 */
html.dark .operation-buttons .el-button {
  border-color: var(--border-color);
}

/* 状态指示器在 Dark Mode 下保持原有颜色，增强对比度 */
html.dark .indicator-pending {
  box-shadow: 0 0 8px rgba(78, 140, 255, 0.8);
}

html.dark .indicator-processing {
  box-shadow: 0 0 8px rgba(247, 166, 47, 0.8);
}

html.dark .indicator-completed {
  box-shadow: 0 0 8px rgba(99, 197, 110, 0.8);
}

html.dark .indicator-cancelled {
  box-shadow: 0 0 8px rgba(144, 147, 153, 0.8);
}

html.dark .indicator-failed {
  box-shadow: 0 0 8px rgba(240, 101, 101, 0.8);
}
</style>
