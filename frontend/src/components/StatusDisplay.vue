<template>
  <el-tag
    :type="useCustomColor ? '' : tagType"
    :style="customStyle"
    :effect="actualEffect"
    :size="actualSize"
    :class="{ 'custom-color-tag': useCustomColor }"
  >
    <el-icon v-if="icon" class="status-icon">
      <component :is="icon" />
    </el-icon>
    {{ displayName }}
  </el-tag>
</template>

<script setup>
import { computed } from "vue";
import { useVariableStore } from "@/stores/variableStore";

const props = defineProps({
  // 变量类型
  type: {
    type: String,
    required: true,
  },
  // 变量值
  value: {
    type: [String, Number],
    required: true,
  },
  // 标签效果 (light: 浅色背景, dark: 深色背景, plain: 边框样式)
  effect: {
    type: String,
    default: "light",
  },
  // 是否在表单中使用（使用更清晰的样式）
  inForm: {
    type: Boolean,
    default: false,
  },
  // 标签大小
  size: {
    type: String,
    default: "small",
  },
  // 是否显示图标
  showIcon: {
    type: Boolean,
    default: true,
  },
});

const variableStore = useVariableStore();

// 计算显示名称
const displayName = computed(() => {
  return variableStore.getVariableName(props.type, props.value);
});

// 计算标签类型
const tagType = computed(() => {
  return variableStore.getVariableTagType(props.type, props.value);
});

// 计算自定义颜色
const customColor = computed(() => {
  return variableStore.getVariableColor(props.type, props.value);
});

// 是否使用自定义颜色
const useCustomColor = computed(() => {
  return !!customColor.value && customColor.value !== "";
});

// 计算实际使用的效果
const actualEffect = computed(() => {
  // 使用自定义颜色时，始终使用 light 效果避免文字被掩盖
  if (useCustomColor.value) {
    return "light";
  }
  if (props.inForm) {
    return "dark";
  }
  return props.effect;
});

// 计算实际使用的大小
const actualSize = computed(() => {
  if (props.inForm) {
    return "default";
  }
  return props.size;
});

// 计算图标
const icon = computed(() => {
  if (!props.showIcon) return null;
  return variableStore.getVariableIcon(props.type, props.value);
});

// 计算自定义样式。color-mix 同时支持十六进制色和 CSS 变量，避免拼接透明度导致无效颜色。
const customStyle = computed(() => {
  if (!useCustomColor.value) {
    return {};
  }

  const accentColor = customColor.value;
  const backgroundColor = `color-mix(in srgb, ${accentColor} 12%, var(--surface-solid))`;
  const borderColor = `color-mix(in srgb, ${accentColor} 52%, var(--border-color-light))`;
  const textColor = `color-mix(in srgb, ${accentColor} 72%, var(--text-primary))`;

  return {
    "--el-tag-bg-color": backgroundColor,
    "--el-tag-border-color": borderColor,
    "--el-tag-text-color": textColor,
    "border-color": borderColor,
    color: textColor,
  };
});

</script>

<style scoped>
.status-icon {
  margin-right: 4px;
}

/* 自定义颜色标签样式 */
.custom-color-tag {
  border-width: 1px;
  border-style: solid;
}

/* 确保图标颜色与文字一致 */
.custom-color-tag .status-icon {
  color: inherit;
}
</style>
