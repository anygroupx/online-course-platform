/**
 * 响应式布局 Composable
 * 统一管理屏幕尺寸检测和断点判断
 * 遵循 KISS 原则：简单实用，避免过度设计
 */
import { ref, onMounted, onUnmounted, computed } from "vue";

// 标准断点定义（与 Element Plus 对齐）
export const BREAKPOINTS = {
  xs: 480, // 小屏手机
  sm: 768, // 平板/大屏手机
  md: 992, // 小屏笔记本
  lg: 1200, // 桌面显示器
  xl: 1600, // 大屏显示器
};

/**
 * 响应式屏幕检测
 * @returns {Object} 响应式状态和工具方法
 */
export function useResponsive() {
  const screenWidth = ref(
    typeof window !== "undefined" ? window.innerWidth : 1920
  );

  // 基础断点判断
  const isMobile = computed(() => screenWidth.value <= BREAKPOINTS.sm);
  const isTablet = computed(
    () =>
      screenWidth.value > BREAKPOINTS.sm && screenWidth.value <= BREAKPOINTS.md
  );
  const isDesktop = computed(() => screenWidth.value > BREAKPOINTS.md);
  const isLargeScreen = computed(() => screenWidth.value >= BREAKPOINTS.xl);

  // 具体断点判断
  const isXs = computed(() => screenWidth.value <= BREAKPOINTS.xs);
  const isSm = computed(
    () =>
      screenWidth.value > BREAKPOINTS.xs && screenWidth.value <= BREAKPOINTS.sm
  );
  const isMd = computed(
    () =>
      screenWidth.value > BREAKPOINTS.sm && screenWidth.value <= BREAKPOINTS.md
  );
  const isLg = computed(
    () =>
      screenWidth.value > BREAKPOINTS.md && screenWidth.value <= BREAKPOINTS.lg
  );
  const isXl = computed(() => screenWidth.value > BREAKPOINTS.lg);

  // 屏幕类型（用于日志或分析）
  const screenType = computed(() => {
    if (isXs.value) return "xs";
    if (isSm.value) return "sm";
    if (isMd.value) return "md";
    if (isLg.value) return "lg";
    return "xl";
  });

  // 更新屏幕尺寸
  const updateScreenSize = () => {
    if (typeof window !== "undefined") {
      screenWidth.value = window.innerWidth;
    }
  };

  // 防抖处理（避免频繁触发，提升性能）
  let resizeTimer = null;
  const handleResize = () => {
    if (resizeTimer) {
      clearTimeout(resizeTimer);
    }
    resizeTimer = setTimeout(() => {
      updateScreenSize();
    }, 100);
  };

  // 生命周期挂载
  onMounted(() => {
    if (typeof window !== "undefined") {
      updateScreenSize();
      window.addEventListener("resize", handleResize, { passive: true });
    }
  });

  // 生命周期卸载 - 关键：确保清理所有资源
  onUnmounted(() => {
    if (typeof window !== "undefined") {
      window.removeEventListener("resize", handleResize);
    }
    // 清理定时器，防止内存泄漏
    if (resizeTimer) {
      clearTimeout(resizeTimer);
      resizeTimer = null;
    }
  });

  return {
    // 屏幕宽度
    screenWidth,

    // 常用断点（推荐使用）
    isMobile,
    isTablet,
    isDesktop,
    isLargeScreen,

    // 精确断点（按需使用）
    isXs,
    isSm,
    isMd,
    isLg,
    isXl,

    // 工具
    screenType,
    BREAKPOINTS,
  };
}

/**
 * 简化版：仅移动端检测（兼容现有代码）
 * @returns {Object}
 */
export function useMobile() {
  const { isMobile } = useResponsive();
  return { isMobile };
}
