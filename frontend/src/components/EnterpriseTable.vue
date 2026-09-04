<template>
  <div class="enterprise-table fluent-data-surface" data-density="enterprise">
    <!-- 桌面端：标准表格 -->
    <template v-if="!isMobile">
      <el-table
        ref="tableRef"
        :data="data"
        v-loading="loading"
        v-bind="tableProps"
        @selection-change="handleSelectionChange"
        @sort-change="handleSortChange"
        :row-key="rowKey"
        :height="height"
        :max-height="maxHeight"
        :scrollbar-always-on="enableVirtualScroll"
        class="desktop-table"
      >
        <!-- 选择列 -->
        <el-table-column
          v-if="selectable"
          type="selection"
          width="55"
          :reserve-selection="true"
        />

        <!-- 配置驱动的列 -->
        <template v-for="column in visibleColumns" :key="column.key">
          <el-table-column
            v-if="!column.hidden"
            :prop="column.key"
            :label="column.label"
            :width="column.width"
            :min-width="column.minWidth"
            :sortable="column.sortable ? 'custom' : false"
            :align="column.align || 'left'"
            :fixed="column.fixed"
          >
            <template #default="{ row, $index }">
              <!-- 自定义插槽优先 -->
              <slot
                :name="`column-${column.key}`"
                :row="row"
                :column="column"
                :index="$index"
              >
                <!-- 配置渲染器 -->
                <component
                  v-if="column.component"
                  :is="column.component"
                  v-bind="getColumnProps(column, row)"
                />
                <!-- 格式化函数 -->
                <span v-else-if="column.formatter">
                  {{ column.formatter(row, column, $index) }}
                </span>
                <!-- 默认显示 -->
                <span v-else>{{ row[column.key] }}</span>
              </slot>
            </template>
          </el-table-column>
        </template>

        <!-- 操作列 -->
        <el-table-column
          v-if="hasActions"
          label="操作"
          :width="actionsWidth"
          :fixed="actionsFixed"
          align="center"
        >
          <template #default="{ row, $index }">
            <slot name="actions" :row="row" :index="$index">
              <div class="table-actions">
                <template
                  v-for="action in getRowActions(row)"
                  :key="action.key"
                >
                  <el-button
                    v-if="!action.hidden"
                    :type="action.type || 'default'"
                    :size="action.size || 'small'"
                    :link="action.link !== false"
                    :disabled="
                      typeof action.disabled === 'function'
                        ? action.disabled(row)
                        : !!action.disabled
                    "
                    @click="handleAction(action.key, row, $index)"
                  >
                    <el-icon v-if="action.icon">
                      <component :is="action.icon" />
                    </el-icon>
                    {{ action.label }}
                  </el-button>
                </template>
              </div>
            </slot>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- 移动端：卡片列表 -->
    <template v-else>
      <div v-loading="loading" class="mobile-card-list">
        <div
          v-for="(row, index) in data"
          :key="getRowKey(row, index)"
          class="mobile-card fluent-depth-card"
          data-depth="1"
          @click="handleCardClick(row, index)"
        >
          <!-- 自定义卡片插槽 -->
          <slot name="mobile-card" :row="row" :index="index">
            <!-- 默认卡片渲染 -->
            <div class="card-header">
              <span class="card-title">{{ getCardTitle(row) }}</span>
              <slot name="card-badge" :row="row">
                <!-- 企业方案：徽章也支持配置驱动的组件渲染 -->
                <template v-if="props.cardBadgeKey">
                  <component
                    v-if="getBadgeColumn()?.component"
                    :is="getBadgeColumn().component"
                    v-bind="getCardBadgeProps(row)"
                  />
                  <el-tag v-else-if="getCardBadge(row)" size="small">
                    {{
                      getBadgeColumn()?.formatter
                        ? getBadgeColumn().formatter(
                            row,
                            getBadgeColumn(),
                            index
                          )
                        : getCardBadge(row)
                    }}
                  </el-tag>
                </template>
              </slot>
            </div>
            <div class="card-body">
              <div
                v-for="column in mobileVisibleColumns"
                :key="column.key"
                class="card-field"
              >
                <span class="field-label">{{ column.label }}：</span>
                <span class="field-value">
                  <slot
                    :name="`column-${column.key}`"
                    :row="row"
                    :column="column"
                    :index="index"
                  >
                    <!-- 企业方案：支持组件、格式化函数、原始值三种渲染方式 -->
                    <component
                      v-if="column.component"
                      :is="column.component"
                      v-bind="getColumnProps(column, row)"
                    />
                    <span v-else-if="column.formatter">
                      {{ column.formatter(row, column, index) }}
                    </span>
                    <span v-else>{{ row[column.key] }}</span>
                  </slot>
                </span>
              </div>
            </div>
            <div v-if="hasActions" class="card-actions">
              <!-- 优先使用 mobile-actions 插槽，如果未定义则尝试使用 actions 插槽作为后备 -->
              <slot name="mobile-actions" :row="row" :index="index">
                <slot name="actions" :row="row" :index="index">
                  <!-- 默认下拉菜单 -->
                  <el-dropdown
                    trigger="click"
                    @command="(cmd) => handleAction(cmd, row, index)"
                  >
                    <el-button size="small">
                      操作 <el-icon><ArrowDown /></el-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item
                          v-for="action in getRowActions(row)"
                          :key="action.key"
                          :command="action.key"
                          :disabled="
                            typeof action.disabled === 'function'
                              ? action.disabled(row)
                              : !!action.disabled
                          "
                        >
                          <el-icon v-if="action.icon">
                            <component :is="action.icon" />
                          </el-icon>
                          {{ action.label }}
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </slot>
              </slot>
            </div>
          </slot>
        </div>

        <!-- 空状态 -->
        <el-empty v-if="!loading && data.length === 0" description="暂无数据" />
      </div>
    </template>

    <!-- 分页 -->
    <div v-if="pagination && data.length > 0" class="table-pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="pagination.pageSizes || [10, 20, 50, 100]"
        :total="pagination.total ?? 0"
        :layout="paginationLayout"
        :pager-count="isMobile ? 5 : 7"
        :size="isMobile ? 'small' : 'default'"
        background
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, useSlots } from "vue";
import { ArrowDown } from "@element-plus/icons-vue";
import { useResponsive } from "@/composables/useResponsive";

// 获取插槽
const slots = useSlots();

// Props
const props = defineProps({
  // 列配置
  columns: {
    type: Array,
    required: true,
    validator: (value) => {
      return value.every((col) => col.key && col.label);
    },
  },
  // 表格数据
  data: {
    type: Array,
    default: () => [],
  },
  // 加载状态
  loading: {
    type: Boolean,
    default: false,
  },
  // 行操作配置
  rowActions: {
    type: Array,
    default: () => [],
  },
  // 分页配置
  pagination: {
    type: Object,
    default: null,
  },
  // 是否可选择
  selectable: {
    type: Boolean,
    default: false,
  },
  // 行唯一标识
  rowKey: {
    type: [String, Function],
    default: "id",
  },
  // 表格高度
  height: {
    type: [String, Number],
    default: undefined,
  },
  // 最大高度
  maxHeight: {
    type: [String, Number],
    default: undefined,
  },
  // 操作列宽度
  actionsWidth: {
    type: [String, Number],
    default: 200,
  },
  // 操作列固定
  actionsFixed: {
    type: [String, Boolean],
    default: "right",
  },
  // 移动端卡片标题字段
  cardTitleKey: {
    type: String,
    default: "",
  },
  // 移动端卡片徽章字段
  cardBadgeKey: {
    type: String,
    default: "",
  },
  // 移动端显示的列（key 数组）
  mobileColumns: {
    type: Array,
    default: () => [],
  },
  // 额外的 el-table props
  tableProps: {
    type: Object,
    default: () => ({}),
  },
  // 配置持久化存储键
  storageKey: {
    type: String,
    default: "",
  },
  // 是否启用配置持久化
  enableStorage: {
    type: Boolean,
    default: false,
  },
  // 是否启用列管理
  enableColumnManage: {
    type: Boolean,
    default: false,
  },
  // 初始列可见性配置
  initialColumnVisible: {
    type: Object,
    default: () => ({}),
  },
  // 初始列顺序
  initialColumnOrder: {
    type: Array,
    default: () => [],
  },
  // 是否启用虚拟滚动（仅在指定height时生效）
  enableVirtualScroll: {
    type: Boolean,
    default: false,
  },
});

// Emits
const emit = defineEmits([
  "selection-change",
  "sort-change",
  "action",
  "page-change",
  "size-change",
  "card-click",
]);

// 响应式检测
const { isMobile } = useResponsive();

// Refs
const tableRef = ref(null);

// 分页状态
const currentPage = ref(props.pagination?.currentPage || 1);
const pageSize = ref(props.pagination?.pageSize || 10);

// 列配置状态
const columnVisible = ref({});
const columnOrder = ref([]);

// 初始化列配置
const initializeColumnConfig = () => {
  // 从 props 或默认值初始化
  columnVisible.value = props.initialColumnVisible || {};
  columnOrder.value =
    props.initialColumnOrder.length > 0
      ? props.initialColumnOrder
      : props.columns.map((col) => col.key);

  // 确保所有列都有可见性配置
  props.columns.forEach((col) => {
    if (columnVisible.value[col.key] === undefined) {
      columnVisible.value[col.key] = col.visible !== false;
    }
  });

  // 分页由父级统一管理；这里仅加载列配置。
  if (props.enableStorage && props.storageKey) {
    loadTableConfig();
  }
};

// 配置持久化
const loadTableConfig = () => {
  if (!props.enableStorage || !props.storageKey) return;

  try {
    const saved = localStorage.getItem(`table_${props.storageKey}`);
    if (saved) {
      const config = JSON.parse(saved);
      if (config.columnVisible) {
        columnVisible.value = {
          ...columnVisible.value,
          ...config.columnVisible,
        };
      }
      if (config.columnOrder) {
        columnOrder.value = config.columnOrder;
      }
    }
  } catch (error) {
    console.error("加载表格配置失败:", error);
  }
};

const saveTableConfig = () => {
  if (!props.enableStorage || !props.storageKey) return;

  try {
    const config = {
      columnVisible: columnVisible.value,
      columnOrder: columnOrder.value,
      timestamp: Date.now(),
    };
    localStorage.setItem(`table_${props.storageKey}`, JSON.stringify(config));
  } catch (error) {
    console.error("保存表格配置失败:", error);
  }
};

const clearTableConfig = () => {
  if (!props.storageKey) return;
  localStorage.removeItem(`table_${props.storageKey}`);
  initializeColumnConfig();
};

// 初始化
initializeColumnConfig();

// 监听分页配置变化（企业方案：深度同步父组件状态）
watch(
  () => props.pagination?.currentPage,
  (val) => {
    if (val !== undefined && val !== currentPage.value) {
      currentPage.value = val;
    }
  },
  { immediate: false }
);

watch(
  () => props.pagination?.pageSize,
  (val) => {
    if (val !== undefined && val !== pageSize.value) {
      pageSize.value = val;
    }
  },
  { immediate: false }
);

// 可见列（根据配置排序和筛选）
const visibleColumns = computed(() => {
  if (props.enableStorage || props.enableColumnManage) {
    // 启用列管理时，按配置顺序和可见性返回
    return columnOrder.value
      .map((key) => props.columns.find((col) => col.key === key))
      .filter((col) => col && columnVisible.value[col.key] && !col.hideInTable);
  }
  // 默认模式
  return props.columns.filter((col) => !col.hideInTable);
});

// 移动端可见列
const mobileVisibleColumns = computed(() => {
  if (props.mobileColumns.length > 0) {
    return props.columns.filter((col) => props.mobileColumns.includes(col.key));
  }
  // 默认显示前 4 个非操作列
  return visibleColumns.value.slice(0, 4);
});

// 是否有操作列
const hasActions = computed(() => {
  return props.rowActions.length > 0 || !!slots.actions;
});

// 分页布局
const paginationLayout = computed(() => {
  return isMobile.value
    ? "sizes, prev, pager, next"
    : "total, sizes, prev, pager, next, jumper";
});

// 获取行唯一标识
const getRowKey = (row, index) => {
  if (typeof props.rowKey === "function") {
    return props.rowKey(row);
  }
  return row[props.rowKey] || index;
};

// 获取列组件属性
const getColumnProps = (column, row) => {
  if (typeof column.componentProps === "function") {
    return column.componentProps(row);
  }
  return column.componentProps || {};
};

// 获取行操作（支持动态显示）
const getRowActions = (row) => {
  return props.rowActions.filter((action) => {
    if (typeof action.show === "function") {
      return action.show(row);
    }
    return action.show !== false;
  });
};

// 获取卡片标题
const getCardTitle = (row) => {
  if (props.cardTitleKey) {
    return row[props.cardTitleKey];
  }
  // 默认取第一列
  const firstColumn = visibleColumns.value[0];
  return firstColumn ? row[firstColumn.key] : "";
};

// 获取卡片徽章
const getCardBadge = (row) => {
  if (props.cardBadgeKey) {
    return row[props.cardBadgeKey];
  }
  return "";
};

// 获取徽章对应的列配置（企业方案：支持配置驱动渲染）
const getBadgeColumn = () => {
  if (!props.cardBadgeKey) return null;
  return props.columns.find((col) => col.key === props.cardBadgeKey);
};

// 卡片头部徽章需要与标题保持同一视觉层级，不沿用表格内的小号状态样式。
const getCardBadgeProps = (row) => ({
  ...getColumnProps(getBadgeColumn(), row),
  size: "default",
});

// 事件处理
const handleSelectionChange = (selection) => {
  emit("selection-change", selection);
};

const handleSortChange = ({ prop, order }) => {
  emit("sort-change", { prop, order });
};

const handleAction = (actionKey, row, index) => {
  emit("action", { action: actionKey, row, index });
};

const handleSizeChange = (size) => {
  // 每页条数由父级统一持久化；组件只负责重置页码并派发一次变更。
  pageSize.value = size;
  currentPage.value = 1;
  emit("size-change", size);
  emit("page-change", { page: 1, size });
};

const handleCurrentChange = (page) => {
  currentPage.value = page;
  emit("page-change", { page, size: pageSize.value });
};

const handleCardClick = (row, index) => {
  emit("card-click", { row, index });
};

// 列管理方法
const toggleColumnVisibility = (key, visible) => {
  columnVisible.value[key] = visible;
  saveTableConfig();
};

const updateColumnOrder = (newOrder) => {
  columnOrder.value = newOrder;
  saveTableConfig();
};

const resetColumns = () => {
  columnVisible.value = {};
  props.columns.forEach((col) => {
    columnVisible.value[col.key] = col.visible !== false;
  });
  columnOrder.value = props.columns.map((col) => col.key);
  saveTableConfig();
};

// 暴露方法
defineExpose({
  tableRef,
  clearSelection: () => tableRef.value?.clearSelection(),
  toggleRowSelection: (row, selected) =>
    tableRef.value?.toggleRowSelection(row, selected),
  setCurrentRow: (row) => tableRef.value?.setCurrentRow(row),
  // 列管理方法
  toggleColumnVisibility,
  updateColumnOrder,
  resetColumns,
  // 配置管理
  saveTableConfig,
  clearTableConfig,
  // 状态访问
  getColumnVisible: () => columnVisible.value,
  getColumnOrder: () => columnOrder.value,
});
</script>

<style scoped>
.enterprise-table {
  width: 100%;
  /* 激活下方组件级容器查询，使卡片布局由可用宽度而非设备宽度决定。 */
  container-type: inline-size;
}

.desktop-table {
  width: 100%;
}

.table-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
}

/* 企业方案：增强操作按钮对比度（适配link和普通按钮） */
.table-actions :deep(.el-button--small) {
  font-weight: 500;
}

/* link类型按钮增强 */
.table-actions :deep(.el-button--primary.is-link) {
  color: var(--el-color-primary);
}

.table-actions :deep(.el-button--success.is-link) {
  color: var(--el-color-success);
}

.table-actions :deep(.el-button--warning.is-link) {
  color: var(--el-color-warning);
}

.table-actions :deep(.el-button--danger.is-link) {
  color: var(--el-color-danger);
}

.table-actions :deep(.el-button--info.is-link) {
  color: var(--el-color-info);
}

/* 普通按钮增强：功能色背景使用可配置的反色文字，确保主题切换后仍清晰。 */
.table-actions :deep(.el-button--small:not(.is-link)) {
  padding: 5px 12px;
  border-width: 1px;
}

.table-actions :deep(.el-button--primary:not(.is-link)) {
  background: var(--primary-gradient) !important;
  border: none !important;
  color: var(--text-on-brand) !important;
  box-shadow: var(--shadow-sm);
}

.table-actions :deep(.el-button--primary:not(.is-link):hover),
.table-actions :deep(.el-button--primary:not(.is-link):focus) {
  opacity: 0.94;
  box-shadow: var(--shadow-md);
  color: var(--text-on-brand) !important;
}

.table-actions :deep(.el-button--success:not(.is-link)) {
  --el-button-bg-color: var(--el-color-success);
  --el-button-border-color: var(--el-color-success);
  --el-button-text-color: var(--text-on-brand);
  --el-button-hover-bg-color: var(--el-color-success-light-3);
  --el-button-hover-border-color: var(--el-color-success-light-3);
  --el-button-hover-text-color: var(--text-on-brand);
}

.table-actions :deep(.el-button--warning:not(.is-link)) {
  --el-button-bg-color: var(--el-color-warning);
  --el-button-border-color: var(--el-color-warning);
  --el-button-text-color: var(--text-on-brand);
  --el-button-hover-bg-color: var(--el-color-warning-light-3);
  --el-button-hover-border-color: var(--el-color-warning-light-3);
  --el-button-hover-text-color: var(--text-on-brand);
}

.table-actions :deep(.el-button--danger:not(.is-link)) {
  --el-button-bg-color: var(--el-color-danger);
  --el-button-border-color: var(--el-color-danger);
  --el-button-text-color: var(--text-on-brand);
  --el-button-hover-bg-color: var(--el-color-danger-light-3);
  --el-button-hover-border-color: var(--el-color-danger-light-3);
  --el-button-hover-text-color: var(--text-on-brand);
}

.table-actions :deep(.el-button--info:not(.is-link)) {
  --el-button-bg-color: var(--el-color-info);
  --el-button-border-color: var(--el-color-info);
  --el-button-text-color: var(--text-on-brand);
  --el-button-hover-bg-color: var(--el-color-info-dark-2);
  --el-button-hover-border-color: var(--el-color-info-dark-2);
  --el-button-hover-text-color: var(--text-on-brand);
}

.table-actions :deep(.el-button--default:not(.is-link)) {
  --el-button-bg-color: var(--bg-card);
  --el-button-border-color: var(--border-color);
  --el-button-text-color: var(--text-primary);
  --el-button-hover-bg-color: var(--el-color-primary-light-9);
  --el-button-hover-border-color: var(--el-color-primary-light-7);
  --el-button-hover-text-color: var(--el-color-primary);
}

/* 移动端卡片列表 */
.mobile-card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 200px;
}

.mobile-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color-light);
  border-radius: 8px;
  padding: 16px;
  box-shadow: var(--shadow-sm);
  transition: box-shadow 0.3s;
}

.mobile-card:active {
  box-shadow: var(--shadow-md);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  min-width: 0;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color-light);
}

.card-title {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  overflow-wrap: anywhere;
}

.card-header :deep(.el-tag) {
  flex: 0 0 auto;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.card-field {
  display: flex;
  font-size: 14px;
  line-height: 1.6;
}

.field-label {
  color: var(--text-secondary);
  min-width: 80px;
  flex-shrink: 0;
}

.field-value {
  color: var(--text-regular);
  flex: 1;
  word-break: break-all;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px solid var(--border-color-light);
  gap: 8px;
  flex-wrap: wrap;
}

/* 移动端操作按钮样式优化 */
.card-actions :deep(.action-buttons) {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
  width: 100%;
}

.card-actions :deep(.action-buttons .el-button) {
  flex-shrink: 0;
  padding: 5px 10px;
  font-size: 13px;
}

/* 分页 */
.table-pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

@media (max-width: 767px) {
  .table-pagination {
    margin-top: 16px;
  }

  .table-pagination :deep(.el-pagination) {
    max-width: 100%;
    flex-wrap: wrap;
    justify-content: center;
    row-gap: 8px;
  }

  .table-pagination :deep(.el-pagination__total),
  .table-pagination :deep(.el-pagination__jump) {
    display: none;
  }
}

@container (max-width: 420px) {
  .mobile-card {
    padding: 14px 12px;
  }

  .card-header {
    align-items: flex-start;
    gap: 8px;
  }

  .card-field {
    display: grid;
    grid-template-columns: minmax(72px, 0.36fr) minmax(0, 1fr);
    gap: 8px;
  }

  .field-label {
    min-width: 0;
  }

  .card-actions,
  .card-actions :deep(.action-buttons) {
    inline-size: 100%;
    justify-content: flex-end;
  }

  .card-actions :deep(.el-button) {
    flex: 1 1 auto;
  }

  /* 默认下拉操作在窄卡片中占满整行，保持触控目标与自定义操作区一致。 */
  .card-actions :deep(.el-dropdown),
  .card-actions :deep(.el-dropdown .el-button) {
    inline-size: 100%;
  }

  .card-actions :deep(.el-dropdown .el-button) {
    justify-content: center;
  }
}
</style>

<style scoped>
/* 数据表保持 solid 材质与低 elevation，保证高密度场景的扫描效率。 */
.enterprise-table {
  position: relative;
  width: 100%;
  padding: 8px;
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--surface-solid) 92%, transparent);
  box-shadow:
    inset 0 1px 0 var(--stroke-highlight),
    var(--shadow-sm);
}

.desktop-table {
  overflow: hidden;
  border-radius: var(--radius-md);
}

.desktop-table :deep(.el-table__header-wrapper) {
  border-radius: var(--radius-md) var(--radius-md) 0 0;
}

.desktop-table :deep(.el-table__row) {
  transition:
    background-color var(--motion-fast) ease,
    box-shadow var(--motion-fast) ease;
}

.desktop-table :deep(.el-table__row:hover > td.el-table__cell) {
  background: color-mix(in srgb, var(--brand-primary) 7%, var(--surface-solid));
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--brand-primary) 8%, transparent);
}

.table-actions {
  gap: 6px;
}

.table-actions :deep(.el-button.is-link) {
  min-height: 28px;
  padding: 4px 7px;
  border-radius: 7px;
}

.table-actions :deep(.el-button.is-link:hover) {
  background: color-mix(in srgb, currentColor 8%, transparent);
}

.mobile-card-list {
  gap: 12px;
}

.mobile-card {
  position: relative;
  overflow: hidden;
  border-color: var(--border-color-light);
  border-radius: var(--radius-md);
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.08), transparent),
    var(--bg-card);
  box-shadow:
    inset 0 1px 0 var(--stroke-highlight),
    var(--shadow-sm);
}

.mobile-card::before {
  content: "";
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: var(--primary-gradient);
}

.card-header,
.card-actions {
  border-color: var(--border-color-light);
}

.card-title {
  color: var(--text-primary);
  font-weight: 650;
}

.field-label {
  color: var(--text-secondary);
}

.field-value {
  color: var(--text-regular);
}

.table-pagination {
  margin: 14px 0 4px;
  padding-top: 12px;
  border-top: 1px solid var(--border-color-light);
}

@media (max-width: 767px) {
  .enterprise-table {
    padding: 0;
    border: 0;
    background: transparent;
    box-shadow: none;
  }
}
</style>
