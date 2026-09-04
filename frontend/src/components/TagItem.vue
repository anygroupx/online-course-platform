<template>
  <div
    class="tag-item"
    :class="{
      'active': isActive,
      'fixed': tag.fixed,
      'dragging': isDragging
    }"
    :draggable="!tag.fixed"
    @click="$emit('click')"
    @dragstart="$emit('dragstart', $event)"
    @dragend="$emit('dragend')"
    @dragover="$emit('dragover', $event)"
    @drop="$emit('drop', $event)"
    @contextmenu="$emit('contextmenu', $event)"
  >
    <!-- 标签图标 -->
    <el-icon v-if="tag.icon" class="tag-icon">
      <component :is="tag.icon" />
    </el-icon>

    <!-- 标签标题 -->
    <span class="tag-title">{{ tag.title }}</span>

    <!-- 关闭按钮 -->
    <el-icon
      v-if="tag.closable && !tag.fixed"
      class="tag-close"
      @click.stop="$emit('close')"
    >
      <Close />
    </el-icon>

    <!-- 固定图标 -->
    <el-icon
      v-if="tag.fixed"
      class="tag-fixed"
    >
      <Lock />
    </el-icon>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Close, Lock } from '@element-plus/icons-vue'

// Props
const props = defineProps({
  tag: {
    type: Object,
    required: true
  },
  index: {
    type: Number,
    required: true
  },
  isActive: {
    type: Boolean,
    default: false
  },
  isDragging: {
    type: Boolean,
    default: false
  }
})

// Emits
const emit = defineEmits([
  'click',
  'close',
  'contextmenu',
  'dragstart',
  'dragend',
  'dragover',
  'drop'
])
</script>

<style scoped>
.tag-item {
  display: inline-flex;
  align-items: center;
  height: 36px; /* 调整高度与容器协调 */
  padding: 0 12px;
  margin-right: 8px;
  background: var(--surface-acrylic);
  border: 1px solid var(--border-color-light);
  border-radius: 8px; /* 增加圆角 */
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
  backdrop-filter: blur(10px); /* 增强模糊效果 */
  -webkit-backdrop-filter: blur(10px);
  user-select: none;
  min-width: 80px;
  max-width: 200px;
  /* 添加微妙的阴影 */
  box-shadow: var(--shadow-sm);
}

.tag-item:hover {
  background: var(--bg-card-hover);
  border-color: color-mix(in srgb, var(--brand-primary) 30%, var(--border-color));
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}

.tag-item.active {
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--brand-primary) 15%, var(--surface-solid)) 0%,
    color-mix(in srgb, var(--brand-primary) 10%, var(--surface-solid)) 100%
  );
  border-color: var(--brand-primary);
  color: var(--brand-primary);
  font-weight: 500;
}

.tag-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: linear-gradient(180deg, var(--brand-primary) 0%, var(--color-success) 100%);
  border-radius: 0 2px 2px 0;
}

.tag-item.fixed {
  background: color-mix(in srgb, var(--color-success) 10%, var(--surface-solid));
  border-color: color-mix(in srgb, var(--color-success) 30%, var(--border-color));
}

.tag-item.fixed:hover {
  background: color-mix(in srgb, var(--color-success) 15%, var(--surface-solid));
  border-color: color-mix(in srgb, var(--color-success) 50%, var(--border-color));
}

.tag-item.dragging {
  opacity: 0.6;
  transform: rotate(5deg) scale(1.05);
  z-index: 1000;
  box-shadow: var(--shadow-lg);
}

.tag-icon {
  margin-right: 6px;
  font-size: 14px;
  color: inherit;
}

.tag-title {
  flex: 1;
  font-size: 13px;
  color: inherit;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1;
}

.tag-close {
  margin-left: 6px;
  font-size: 12px;
  color: var(--text-secondary);
  transition: all 0.3s;
  padding: 2px;
  border-radius: 2px;
}

.tag-close:hover {
  color: var(--color-danger);
  background: color-mix(in srgb, var(--color-danger) 10%, transparent);
  transform: scale(1.1);
}

.tag-fixed {
  margin-left: 6px;
  font-size: 12px;
  color: var(--color-success);
}

/* 标签项动画效果 */
.tag-item {
  animation: slideIn 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(-20px) scale(0.9);
  }
  to {
    opacity: 1;
    transform: translateX(0) scale(1);
  }
}

/* 关闭动画 */
.tag-item.closing {
  animation: slideOut 0.3s cubic-bezier(0.4, 0, 0.2, 1) forwards;
}

@keyframes slideOut {
  to {
    opacity: 0;
    transform: translateX(20px) scale(0.9);
    max-width: 0;
    margin-right: 0;
    padding: 0;
  }
}

/* 响应式设计 */
@media (max-width: 767px) {
  .tag-item {
    height: 32px; /* 移动端保持合适高度 */
    padding: 0 8px;
    margin-right: 4px;
    min-width: 60px;
    max-width: 120px;
  }

  .tag-title {
    font-size: 12px;
  }

  .tag-icon {
    margin-right: 4px;
    font-size: 12px;
  }

  .tag-close {
    margin-left: 4px;
    font-size: 10px;
  }

  .tag-fixed {
    margin-left: 4px;
    font-size: 10px;
  }
}

@media (max-width: 480px) {
  .tag-item {
    height: 28px; /* 小屏幕保持合适高度 */
    padding: 0 6px;
    margin-right: 2px;
    min-width: 50px;
    max-width: 100px;
  }

  .tag-title {
    font-size: 11px;
  }
}
</style>
