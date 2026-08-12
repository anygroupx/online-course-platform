<template>
  <div v-if="show" class="table-batch-actions">
    <el-card shadow="never" class="batch-card">
      <div class="batch-content">
        <!-- 选择信息 -->
        <div class="batch-info">
          <el-icon class="info-icon"><InfoFilled /></el-icon>
          <span class="info-text">
            已选择 <strong>{{ selectionCount }}</strong> 项
          </span>
          <el-button
            link
            type="primary"
            size="small"
            @click="handleClearSelection"
          >
            清空
          </el-button>
        </div>

        <!-- 批量操作按钮组 -->
        <div class="batch-actions">
          <template v-for="action in visibleActions" :key="action.key">
            <el-button
              v-if="!action.hidden"
              :type="action.type || 'default'"
              :size="action.size || 'default'"
              :icon="action.icon"
              :disabled="action.disabled"
              :loading="action.loading"
              @click="handleAction(action.key)"
            >
              {{ action.label }}
            </el-button>
          </template>

          <!-- 移动端：更多操作下拉菜单 -->
          <el-dropdown
            v-if="isMobile && hasMoreActions"
            trigger="click"
            @command="handleAction"
          >
            <el-button :size="isMobile ? 'default' : 'default'">
              更多操作 <el-icon><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="action in moreActions"
                  :key="action.key"
                  :command="action.key"
                  :disabled="action.disabled"
                  :icon="action.icon"
                >
                  {{ action.label }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { InfoFilled, ArrowDown } from "@element-plus/icons-vue";
import { useResponsive } from "@/composables/useResponsive";

// Props
const props = defineProps({
  // 选中的行
  selectedRows: {
    type: Array,
    default: () => [],
  },
  // 选中数量
  selectionCount: {
    type: Number,
    default: 0,
  },
  // 批量操作配置
  actions: {
    type: Array,
    required: true,
    default: () => [],
  },
  // 是否显示（根据是否有选择自动控制）
  autoShow: {
    type: Boolean,
    default: true,
  },
  // 手动控制显示
  visible: {
    type: Boolean,
    default: undefined,
  },
  // 移动端显示的主要操作数量
  mobileVisibleCount: {
    type: Number,
    default: 2,
  },
});

// Emits
const emit = defineEmits(["action", "clear-selection"]);

// 响应式
const { isMobile } = useResponsive();

// 是否显示
const show = computed(() => {
  if (props.visible !== undefined) {
    return props.visible;
  }
  return props.autoShow ? props.selectionCount > 0 : true;
});

// 可见的操作（基于 show 属性和权限）
const visibleActions = computed(() => {
  const actions = props.actions.filter((action) => {
    if (action.hidden) return false;
    if (typeof action.show === "function") {
      return action.show(props.selectedRows);
    }
    return action.show !== false;
  });

  // 移动端只显示前 N 个
  if (isMobile.value) {
    return actions.slice(0, props.mobileVisibleCount);
  }

  return actions;
});

// 移动端更多操作
const moreActions = computed(() => {
  if (!isMobile.value) return [];
  return props.actions
    .filter((action) => {
      if (action.hidden) return false;
      if (typeof action.show === "function") {
        return action.show(props.selectedRows);
      }
      return action.show !== false;
    })
    .slice(props.mobileVisibleCount);
});

// 是否有更多操作
const hasMoreActions = computed(() => {
  return moreActions.value.length > 0;
});

// 处理操作
const handleAction = (actionKey) => {
  emit("action", {
    action: actionKey,
    selectedRows: props.selectedRows,
    count: props.selectionCount,
  });
};

// 清空选择
const handleClearSelection = () => {
  emit("clear-selection");
};
</script>

<style scoped>
.table-batch-actions {
  margin-bottom: 16px;
  animation: slideDown 0.3s ease-out;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.batch-card {
  border: 1px solid var(--color-primary);
  /* 企业方案：使用更高对比度的背景，确保文字清晰可读 */
  background: linear-gradient(135deg, #ffffff 0%, #f5f9ff 100%);
}

.batch-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.batch-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.info-icon {
  color: var(--color-info-primary);
  font-size: 18px;
}

.info-text {
  font-size: 14px;
  /* 企业方案：使用CSS变量确保文本对比度达标（5.7:1） */
  color: var(--color-text-regular);
}

.info-text strong {
  color: var(--color-info-primary);
  font-size: 16px;
  margin: 0 4px;
}

.batch-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  flex: 1;
  justify-content: flex-end;
}

.batch-actions .el-button {
  margin: 0;
}

/* 移动端优化 */
@media (max-width: 767px) {
  .batch-content {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .batch-info {
    justify-content: center;
  }

  .batch-actions {
    flex-direction: column;
    gap: 8px;
  }

  .batch-actions .el-button {
    width: 100%;
  }

  .info-text {
    font-size: 13px;
  }

  .info-text strong {
    font-size: 15px;
  }
}

@container (max-width: 520px) {
  .batch-content {
    flex-direction: column;
    align-items: stretch;
  }

  .batch-actions {
    display: grid;
    grid-template-columns: 1fr;
  }

  .batch-actions .el-button {
    width: 100%;
  }
}
</style>
