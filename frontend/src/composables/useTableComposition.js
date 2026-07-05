// Source: AURA-X-KYS 企业级表格组合式函数
// 统一管理表格状态：分页、筛选、排序、选择、配置持久化
import { ref, computed, watch, nextTick } from "vue";

/**
 * 企业级表格状态管理 Composable
 * @param {Object} options 配置选项
 * @param {String} options.storageKey 本地存储键名（用于配置持久化）
 * @param {Object} options.initialFilters 初始筛选条件
 * @param {Object} options.initialSort 初始排序配置
 * @param {Number} options.pageSize 初始每页条数
 * @param {Array} options.columns 列配置
 */
export function useTableComposition(options = {}) {
  const {
    storageKey = "table_config",
    initialFilters = {},
    initialSort = { prop: null, order: null },
    pageSize: defaultPageSize = 10,
    columns = [],
  } = options;

  // ========== 分页状态 ==========
  const currentPage = ref(1);
  const pageSize = ref(defaultPageSize);
  const total = ref(0);

  // ========== 筛选状态 ==========
  const filters = ref({ ...initialFilters });
  const dateRange = ref([]);

  // ========== 排序状态 ==========
  const sortConfig = ref({ ...initialSort });

  // ========== 选择状态 ==========
  const selectedRows = ref([]);
  const selectedIds = computed(() => selectedRows.value.map((row) => row.id));

  // ========== 加载状态 ==========
  const loading = ref(false);
  const tableData = ref([]);

  // ========== 列配置状态 ==========
  const columnOrder = ref(columns.map((col) => col.key));
  const columnVisible = ref(
    columns.reduce((acc, col) => {
      acc[col.key] = col.visible !== false;
      return acc;
    }, {})
  );
  const columnWidths = ref(
    columns.reduce((acc, col) => {
      if (col.width) acc[col.key] = col.width;
      return acc;
    }, {})
  );

  // ========== 配置持久化 ==========
  const loadConfig = () => {
    try {
      const saved = localStorage.getItem(storageKey);
      if (saved) {
        const config = JSON.parse(saved);
        if (config.columnOrder) columnOrder.value = config.columnOrder;
        if (config.columnVisible) columnVisible.value = config.columnVisible;
        if (config.columnWidths) columnWidths.value = config.columnWidths;
        if (config.sortConfig) sortConfig.value = config.sortConfig;
        if (config.pageSize) pageSize.value = config.pageSize;
      }
    } catch (error) {
      console.error("加载表格配置失败:", error);
    }
  };

  const saveConfig = () => {
    try {
      const config = {
        columnOrder: columnOrder.value,
        columnVisible: columnVisible.value,
        columnWidths: columnWidths.value,
        sortConfig: sortConfig.value,
        pageSize: pageSize.value,
      };
      localStorage.setItem(storageKey, JSON.stringify(config));
    } catch (error) {
      console.error("保存表格配置失败:", error);
    }
  };

  const clearConfig = () => {
    localStorage.removeItem(storageKey);
    columnOrder.value = columns.map((col) => col.key);
    columnVisible.value = columns.reduce((acc, col) => {
      acc[col.key] = col.visible !== false;
      return acc;
    }, {});
    columnWidths.value = columns.reduce((acc, col) => {
      if (col.width) acc[col.key] = col.width;
      return acc;
    }, {});
    sortConfig.value = { ...initialSort };
    pageSize.value = defaultPageSize;
  };

  // ========== 计算属性 ==========
  // 可见列配置
  const visibleColumns = computed(() => {
    return columnOrder.value
      .filter((key) => columnVisible.value[key])
      .map((key) => {
        const col = columns.find((c) => c.key === key);
        return col
          ? {
              ...col,
              width: columnWidths.value[key] || col.width,
            }
          : null;
      })
      .filter(Boolean);
  });

  // 查询参数（合并筛选、分页、排序）
  // Source: AURA-X-KYS 修复0值筛选问题 - 保留所有有效值包括0
  const queryParams = computed(() => {
    const params = {
      page: currentPage.value,
      pageSize: pageSize.value,
    };

    // 添加筛选参数，保留0值（重要：0是有效的状态值）
    Object.keys(filters.value).forEach((key) => {
      const value = filters.value[key];
      // 只过滤 null、undefined 和空字符串，保留 0 和 false
      if (value !== null && value !== undefined && value !== "") {
        params[key] = value;
      }
    });

    // 添加排序参数
    if (sortConfig.value.prop) {
      params.sortBy = sortConfig.value.prop;
      params.sortOrder =
        sortConfig.value.order === "ascending" ? "asc" : "desc";
    }

    // 添加日期范围
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0];
      params.endDate = dateRange.value[1];
    }

    return params;
  });

  // ========== 事件处理 ==========
  const handleSelectionChange = (selection) => {
    selectedRows.value = selection;
  };

  const handleSortChange = ({ prop, order }) => {
    sortConfig.value = { prop, order };
    saveConfig();
  };

  const handlePageChange = ({ page, size }) => {
    currentPage.value = page;
    pageSize.value = size;
    saveConfig();
  };

  const handleSizeChange = (size) => {
    pageSize.value = size;
    currentPage.value = 1;
    saveConfig();
  };

  const handleCurrentChange = (page) => {
    currentPage.value = page;
  };

  const handleFilterChange = (newFilters) => {
    filters.value = { ...filters.value, ...newFilters };
    currentPage.value = 1; // 筛选时重置到第一页
  };

  const handleDateRangeChange = (range) => {
    dateRange.value = range;
    currentPage.value = 1;
  };

  const handleResetFilters = () => {
    filters.value = { ...initialFilters };
    dateRange.value = [];
    currentPage.value = 1;
  };

  // ========== 列管理 ==========
  const toggleColumnVisibility = (key, visible) => {
    columnVisible.value[key] = visible;
    saveConfig();
  };

  const updateColumnOrder = (newOrder) => {
    columnOrder.value = newOrder;
    saveConfig();
  };

  const updateColumnWidth = (key, width) => {
    columnWidths.value[key] = width;
    saveConfig();
  };

  // ========== 数据加载 ==========
  const loadData = async (fetchFn) => {
    loading.value = true;
    try {
      const result = await fetchFn(queryParams.value);
      if (result && result.data) {
        tableData.value = result.data;
        total.value = result.total || 0;
      }
      return result;
    } catch (error) {
      console.error("数据加载失败:", error);
      throw error;
    } finally {
      loading.value = false;
    }
  };

  // ========== 刷新数据 ==========
  const refresh = async (fetchFn) => {
    await loadData(fetchFn);
  };

  // ========== 清空选择 ==========
  const clearSelection = () => {
    selectedRows.value = [];
  };

  // ========== 批量操作辅助 ==========
  const hasSelection = computed(() => selectedRows.value.length > 0);
  const selectionCount = computed(() => selectedRows.value.length);

  // 初始化时加载配置
  loadConfig();

  return {
    // 分页
    currentPage,
    pageSize,
    total,

    // 筛选
    filters,
    dateRange,

    // 排序
    sortConfig,

    // 选择
    selectedRows,
    selectedIds,
    hasSelection,
    selectionCount,

    // 数据
    loading,
    tableData,

    // 列配置
    columnOrder,
    columnVisible,
    columnWidths,
    visibleColumns,

    // 查询参数
    queryParams,

    // 事件处理
    handleSelectionChange,
    handleSortChange,
    handlePageChange,
    handleSizeChange,
    handleCurrentChange,
    handleFilterChange,
    handleDateRangeChange,
    handleResetFilters,

    // 列管理
    toggleColumnVisibility,
    updateColumnOrder,
    updateColumnWidth,

    // 配置管理
    saveConfig,
    clearConfig,
    loadConfig,

    // 数据操作
    loadData,
    refresh,
    clearSelection,
  };
}
