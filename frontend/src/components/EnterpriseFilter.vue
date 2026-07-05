<template>
  <div class="enterprise-filter">
    <!-- 移动端：筛选按钮 + 抽屉 -->
    <template v-if="isMobile">
      <div class="mobile-filter-trigger">
        <el-button type="primary" @click="drawerVisible = true">
          <el-icon><Filter /></el-icon>
          筛选条件
        </el-button>
        <el-button @click="handleReset">
          <el-icon><Refresh /></el-icon>
          重置
        </el-button>
      </div>

      <el-drawer
        v-model="drawerVisible"
        title="筛选条件"
        direction="rtl"
        size="80%"
      >
        <el-form :model="localModel" label-position="top" ref="mobileFormRef">
          <!-- 配置驱动的筛选项（移动端全部显示） -->
          <template v-if="config">
            <template v-for="filter in allConfigFilters" :key="filter.key">
              <el-form-item :label="filter.label">
                <!-- 自定义插槽优先 -->
                <slot
                  :name="`filter-${filter.key}`"
                  :filter="filter"
                  :is-mobile="true"
                >
                  <component
                    :is="getFilterComponent(filter.type)"
                    v-model="localModel[filter.key]"
                    v-bind="getFilterProps(filter, true)"
                    @change="handleFilterChange"
                  >
                    <!-- 下拉选项 -->
                    <template v-if="filter.type === 'select'">
                      <el-option
                        v-for="option in getOptions(filter)"
                        :key="getOptionValue(option, filter)"
                        :label="getOptionLabel(option, filter)"
                        :value="getOptionValue(option, filter)"
                      />
                    </template>
                  </component>
                </slot>
              </el-form-item>
            </template>
          </template>

          <!-- 纯插槽模式（向后兼容） -->
          <slot v-else name="filters" :is-mobile="true"></slot>
        </el-form>
        <template #footer>
          <div style="display: flex; gap: 10px">
            <el-button
              type="primary"
              @click="handleDrawerSearch"
              :loading="loading"
            >
              <el-icon><Search /></el-icon>
              查询
            </el-button>
            <el-button @click="drawerVisible = false">取消</el-button>
          </div>
        </template>
      </el-drawer>
    </template>

    <!-- 桌面端：常用筛选 + 高级筛选折叠面板 -->
    <template v-else>
      <div class="desktop-filter-wrapper">
        <!-- 常用筛选（始终显示） -->
        <el-form
          :inline="true"
          :model="localModel"
          :label-width="labelWidth"
          class="common-filter-form"
          ref="commonFormRef"
        >
          <!-- 配置驱动的常用筛选 -->
          <template v-if="config && config.common">
            <template v-for="filter in config.common" :key="filter.key">
              <el-form-item :label="filter.label">
                <!-- 自定义插槽优先 -->
                <slot
                  :name="`filter-${filter.key}`"
                  :filter="filter"
                  :is-mobile="false"
                >
                  <component
                    :is="getFilterComponent(filter.type)"
                    v-model="localModel[filter.key]"
                    v-bind="getFilterProps(filter, false)"
                    @change="handleFilterChange"
                  >
                    <!-- 下拉选项 -->
                    <template v-if="filter.type === 'select'">
                      <el-option
                        v-for="option in getOptions(filter)"
                        :key="getOptionValue(option, filter)"
                        :label="getOptionLabel(option, filter)"
                        :value="getOptionValue(option, filter)"
                      />
                    </template>
                  </component>
                </slot>
              </el-form-item>
            </template>
          </template>

          <!-- 纯插槽模式（向后兼容） -->
          <slot v-else name="common-filters"></slot>

          <el-form-item>
            <el-button type="primary" @click="handleSearch" :loading="loading">
              <el-icon><Search /></el-icon>
              查询
            </el-button>
            <el-button @click="handleReset">
              <el-icon><Refresh /></el-icon>
              重置
            </el-button>
          </el-form-item>
        </el-form>

        <!-- 高级筛选（折叠面板） -->
        <el-collapse
          v-if="hasAdvancedConfig || hasAdvancedFilters"
          v-model="collapseVisible"
          class="advanced-filter-collapse"
        >
          <el-collapse-item title="高级筛选" name="1">
            <el-form
              :inline="true"
              :model="localModel"
              :label-width="labelWidth"
            >
              <!-- 配置驱动的高级筛选 -->
              <template v-if="config && config.advanced">
                <template v-for="filter in config.advanced" :key="filter.key">
                  <el-form-item :label="filter.label">
                    <!-- 自定义插槽优先 -->
                    <slot
                      :name="`filter-${filter.key}`"
                      :filter="filter"
                      :is-mobile="false"
                    >
                      <component
                        :is="getFilterComponent(filter.type)"
                        v-model="localModel[filter.key]"
                        v-bind="getFilterProps(filter, false)"
                        @change="handleFilterChange"
                      >
                        <!-- 下拉选项 -->
                        <template v-if="filter.type === 'select'">
                          <el-option
                            v-for="option in getOptions(filter)"
                            :key="getOptionValue(option, filter)"
                            :label="getOptionLabel(option, filter)"
                            :value="getOptionValue(option, filter)"
                          />
                        </template>
                      </component>
                    </slot>
                  </el-form-item>
                </template>
              </template>

              <!-- 纯插槽模式（向后兼容） -->
              <slot v-else name="advanced-filters"></slot>
            </el-form>
          </el-collapse-item>
        </el-collapse>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch } from "vue";
import { Filter, Search, Refresh } from "@element-plus/icons-vue";
import { useResponsive } from "@/composables/useResponsive";

// Props
const props = defineProps({
  modelValue: {
    type: Object,
    required: true,
  },
  // 筛选配置（支持 JSON/JS 配置）
  config: {
    type: Object,
    default: null,
    validator: (value) => {
      if (!value) return true;
      // 验证配置结构
      return (
        (!value.common || Array.isArray(value.common)) &&
        (!value.advanced || Array.isArray(value.advanced))
      );
    },
  },
  // 动态选项数据源
  options: {
    type: Object,
    default: () => ({}),
  },
  loading: {
    type: Boolean,
    default: false,
  },
  labelWidth: {
    type: String,
    default: "100px",
  },
  hasAdvancedFilters: {
    type: Boolean,
    default: true,
  },
  // 自动搜索防抖时间（毫秒）
  autoSearchDelay: {
    type: Number,
    default: 500,
  },
  // 是否启用自动搜索
  enableAutoSearch: {
    type: Boolean,
    default: true,
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
});

// Emits
const emit = defineEmits(["update:modelValue", "search", "reset"]);

// 响应式检测
const { isMobile } = useResponsive();

// 本地模型（双向绑定）
const localModel = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});

// UI 状态
const drawerVisible = ref(false);
const collapseVisible = ref([]);
const mobileFormRef = ref(null);
const commonFormRef = ref(null);

// 配置持久化功能
const loadFilterConfig = () => {
  if (!props.enableStorage || !props.storageKey) return;

  try {
    const saved = localStorage.getItem(`filter_${props.storageKey}`);
    if (saved) {
      const config = JSON.parse(saved);
      if (config.collapseVisible !== undefined) {
        collapseVisible.value = config.collapseVisible;
      }
      if (config.filters) {
        // 恢复筛选条件（可选功能）
        Object.keys(config.filters).forEach((key) => {
          if (localModel.value[key] === undefined) {
            localModel.value[key] = config.filters[key];
          }
        });
      }
    }
  } catch (error) {
    console.error("加载筛选配置失败:", error);
  }
};

const saveFilterConfig = () => {
  if (!props.enableStorage || !props.storageKey) return;

  try {
    const config = {
      collapseVisible: collapseVisible.value,
      filters: { ...localModel.value },
      timestamp: Date.now(),
    };
    localStorage.setItem(`filter_${props.storageKey}`, JSON.stringify(config));
  } catch (error) {
    console.error("保存筛选配置失败:", error);
  }
};

// 监听折叠面板变化并保存
watch(collapseVisible, () => {
  saveFilterConfig();
});

// 组件挂载时加载配置
if (props.enableStorage) {
  loadFilterConfig();
}

// 计算属性：是否有高级筛选配置
const hasAdvancedConfig = computed(() => {
  return (
    props.config && props.config.advanced && props.config.advanced.length > 0
  );
});

// 计算属性：移动端所有筛选项
const allConfigFilters = computed(() => {
  if (!props.config) return [];
  return [...(props.config.common || []), ...(props.config.advanced || [])];
});

// 组件映射表
const COMPONENT_MAP = {
  input: "el-input",
  select: "el-select",
  date: "el-date-picker",
  daterange: "el-date-picker",
  datetime: "el-date-picker",
  datetimerange: "el-date-picker",
  number: "el-input-number",
  switch: "el-switch",
  checkbox: "el-checkbox-group",
  radio: "el-radio-group",
};

// 获取筛选组件类型
const getFilterComponent = (type) => {
  return COMPONENT_MAP[type] || "el-input";
};

// 获取筛选组件属性
const getFilterProps = (filter, isMobile) => {
  const baseProps = {
    placeholder: filter.placeholder || filter.label,
    clearable: filter.clearable !== false,
    style: {
      width: isMobile ? "100%" : filter.width || "200px",
    },
  };

  // 日期类型特殊处理
  if (filter.type === "daterange" || filter.type === "datetimerange") {
    return {
      ...baseProps,
      type: filter.type,
      rangeSeparator: "至",
      startPlaceholder: filter.startPlaceholder || "开始时间",
      endPlaceholder: filter.endPlaceholder || "结束时间",
      format: filter.format || "YYYY-MM-DD HH:mm:ss",
      valueFormat: filter.valueFormat || "YYYY-MM-DD HH:mm:ss",
    };
  }

  // 日期选择器
  if (filter.type === "date" || filter.type === "datetime") {
    return {
      ...baseProps,
      type: filter.type,
      format: filter.format || "YYYY-MM-DD",
      valueFormat: filter.valueFormat || "YYYY-MM-DD",
    };
  }

  // 数字输入框
  if (filter.type === "number") {
    return {
      ...baseProps,
      min: filter.min,
      max: filter.max,
      step: filter.step || 1,
    };
  }

  return baseProps;
};

// 获取选项列表
const getOptions = (filter) => {
  // 支持三种方式：
  // 1. 直接配置 options: [{label, value}]
  if (filter.options && Array.isArray(filter.options)) {
    return filter.options;
  }

  // 2. 从 props.options 中获取 optionsKey: 'platformList'
  if (filter.optionsKey && props.options[filter.optionsKey]) {
    return props.options[filter.optionsKey];
  }

  // 3. 动态函数 optionsGetter: (options) => options.xxx
  if (filter.optionsGetter && typeof filter.optionsGetter === "function") {
    return filter.optionsGetter(props.options);
  }

  return [];
};

// 获取选项的 label
const getOptionLabel = (option, filter) => {
  if (typeof option === "object") {
    return option[filter.labelKey || "label"] || option.name || option.text;
  }
  return option;
};

// 获取选项的 value
const getOptionValue = (option, filter) => {
  if (typeof option === "object") {
    return option[filter.valueKey || "value"] || option.id;
  }
  return option;
};

// 筛选项变化处理
const handleFilterChange = () => {
  if (props.enableAutoSearch) {
    triggerAutoSearch();
  }
};

// 自动搜索防抖
let searchTimeout = null;
const triggerAutoSearch = () => {
  if (!props.enableAutoSearch) return;

  if (searchTimeout) {
    clearTimeout(searchTimeout);
  }

  searchTimeout = setTimeout(() => {
    emit("search");
  }, props.autoSearchDelay);
};

// 监听模型变化（用于自动搜索）
watch(
  () => props.modelValue,
  () => {
    if (props.enableAutoSearch && !props.config) {
      // 纯插槽模式才需要监听
      triggerAutoSearch();
    }
  },
  { deep: true }
);

// 查询按钮
const handleSearch = () => {
  emit("search");
};

// 移动端抽屉内的查询按钮
const handleDrawerSearch = () => {
  drawerVisible.value = false;
  emit("search");
};

// 重置按钮
const handleReset = () => {
  emit("reset");
};

// 暴露方法给父组件
defineExpose({
  closeDrawer: () => {
    drawerVisible.value = false;
  },
  openDrawer: () => {
    drawerVisible.value = true;
  },
  toggleAdvanced: () => {
    collapseVisible.value = collapseVisible.value.length ? [] : ["1"];
  },
});
</script>

<style scoped>
.enterprise-filter {
  margin-bottom: 20px;
}

/* 移动端筛选触发器 */
.mobile-filter-trigger {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.mobile-filter-trigger .el-button {
  flex: 1;
}

/* 桌面端筛选包装器 */
.desktop-filter-wrapper {
  margin-bottom: 0;
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
  color: var(--text-regular);
  background-color: transparent;
  border-bottom: none;
}

.advanced-filter-collapse :deep(.el-collapse-item__wrap) {
  background-color: transparent;
  border-bottom: none;
}

.advanced-filter-collapse :deep(.el-collapse-item__content) {
  padding-bottom: 0;
  color: var(--text-regular);
}

/* 移动端抽屉内表单优化 */
:deep(.el-drawer__body .el-form) {
  padding: 0 12px;
}

:deep(.el-drawer__body .el-form-item) {
  margin-bottom: 20px;
}

:deep(.el-drawer__footer) {
  padding: 12px;
  border-top: 1px solid var(--border-color-light);
}
</style>
