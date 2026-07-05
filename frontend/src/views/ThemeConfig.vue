<template>
  <div class="theme-config-container">
    <el-card class="theme-config-card">
      <template #header>
        <div class="card-header">
          <span>主题配置</span>
          <el-button text @click="resetTheme">重置为默认设置</el-button>
        </div>
      </template>

      <el-form label-position="top">
        <!-- Theme Mode Selection -->
        <el-form-item label="主题模式">
          <el-radio-group
            v-model="currentThemeName"
            @change="handleThemeChange"
          >
            <el-radio-button label="light">浅色</el-radio-button>
            <el-radio-button label="dark">深色</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-divider />

        <!-- Brand Colors -->
        <div class="section-title">品牌颜色</div>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="主色渐变起点">
              <el-color-picker
                v-model="colors.primaryStart"
                @change="updateColor('--primary-gradient-start', $event)"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="主色渐变终点">
              <el-color-picker
                v-model="colors.primaryEnd"
                @change="updateColor('--primary-gradient-end', $event)"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="preview-box">
          <div class="preview-label">按钮预览</div>
          <el-button type="primary">主按钮</el-button>
          <el-button>默认按钮</el-button>
        </div>

        <el-divider />

        <!-- 功能性色彩 -->
        <div class="section-title">功能性色彩</div>

        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="成功">
              <el-color-picker
                v-model="colors.success"
                @change="updateColor('--color-success', $event)"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="警告">
              <el-color-picker
                v-model="colors.warning"
                @change="updateColor('--color-warning', $event)"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="紧急">
              <el-color-picker
                v-model="colors.danger"
                @change="updateColor('--color-danger', $event)"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="信息">
              <el-color-picker
                v-model="colors.info"
                @change="updateColor('--color-info', $event)"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { useThemeStore } from "@/stores/theme";
import { themes } from "@/styles/themes";

const themeStore = useThemeStore();

const currentThemeName = computed({
  get: () => themeStore.currentThemeName,
  set: (val) => themeStore.applyTheme(val),
});

const colors = ref({
  primaryStart: "",
  primaryEnd: "",
  success: "",
  warning: "",
  danger: "",
  info: "",
});

// Helper to get current computed value of a CSS variable
const getCssVar = (name) => {
  return getComputedStyle(document.documentElement)
    .getPropertyValue(name)
    .trim();
};

const syncColorsFromStore = () => {
  // We try to get from overrides first, then from current theme definition
  // Note: getComputedStyle is the most accurate source of truth for what's currently applied
  colors.value.primaryStart = getCssVar("--primary-gradient-start");
  colors.value.primaryEnd = getCssVar("--primary-gradient-end");
  colors.value.success = getCssVar("--color-success");
  colors.value.warning = getCssVar("--color-warning");
  colors.value.danger = getCssVar("--color-danger");
  colors.value.info = getCssVar("--color-info");
};

const updateColor = (key, value) => {
  if (!value) return;
  themeStore.updateVariable(key, value);

  // If updating gradient parts, we might need to update the composite gradient variable too
  // But our CSS variable definition uses var(), so it should auto-update if browser supports it.
  // However, our JS theme definition hardcodes the gradient string.
  // Let's manually update the gradient string if start/end changes.
  if (key === "--primary-gradient-start" || key === "--primary-gradient-end") {
    const start = colors.value.primaryStart;
    const end = colors.value.primaryEnd;
    const gradient = `linear-gradient(135deg, ${start} 0%, ${end} 100%)`;
    themeStore.updateVariable("--primary-gradient", gradient);
  }
};

const handleThemeChange = (val) => {
  themeStore.applyTheme(val);
  // Re-sync local state after theme change
  setTimeout(syncColorsFromStore, 50);
};

const resetTheme = () => {
  themeStore.customOverrides = {};
  themeStore.applyTheme(themeStore.currentThemeName);
  setTimeout(syncColorsFromStore, 50);
};

onMounted(() => {
  syncColorsFromStore();
});
</script>

<style scoped>
.theme-config-container {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 20px;
  color: var(--text-primary);
}

.preview-box {
  margin: 20px 0;
  padding: 20px;
  border: 1px dashed var(--border-color);
  border-radius: var(--radius-md);
  text-align: center;
}

.preview-label {
  margin-bottom: 10px;
  color: var(--text-secondary);
  font-size: 12px;
}
</style>
