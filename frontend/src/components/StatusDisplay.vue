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

/**
 * 计算颜色亮度（感知亮度公式）
 * @param {string} color - 十六进制颜色值
 * @returns {number} 0-255 的亮度值
 */
const getBrightness = (color) => {
  if (!color) return 255;

  // 移除 # 符号
  color = color.replace("#", "");

  // 转换为 RGB
  const r = parseInt(color.substr(0, 2), 16);
  const g = parseInt(color.substr(2, 2), 16);
  const b = parseInt(color.substr(4, 2), 16);

  // 使用感知亮度公式 (ITU-R BT.709)
  return r * 0.2126 + g * 0.7152 + b * 0.0722;
};

/**
 * 根据背景色自动选择文字颜色
 * @param {string} bgColor - 背景色
 * @returns {string} 文字颜色
 */
const getTextColor = (bgColor) => {
  const brightness = getBrightness(bgColor);
  // 亮度 > 128 使用深色文字，否则使用浅色文字
  return brightness > 128 ? "#303133" : "#ffffff";
};

// 计算自定义样式
const customStyle = computed(() => {
  if (!useCustomColor.value) {
    return {};
  }

  const bgColor = customColor.value;
  const textColor = getTextColor(bgColor);

  // light 效果：浅色背景 + 自适应文字
  if (actualEffect.value === "light") {
    return {
      "--el-tag-bg-color": `${bgColor}20`, // 背景透明度 12.5%
      "--el-tag-border-color": bgColor,
      "--el-tag-text-color": bgColor,
      "border-color": bgColor,
    };
  }

  // dark 效果：深色背景 + 自适应文字
  if (actualEffect.value === "dark") {
    return {
      "--el-tag-bg-color": bgColor,
      "--el-tag-border-color": bgColor,
      "--el-tag-text-color": textColor,
      color: textColor,
    };
  }

  // plain 效果：白色背景 + 彩色边框
  return {
    "--el-tag-bg-color": "#ffffff",
    "--el-tag-border-color": bgColor,
    "--el-tag-text-color": bgColor,
    "border-color": bgColor,
    color: bgColor,
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
