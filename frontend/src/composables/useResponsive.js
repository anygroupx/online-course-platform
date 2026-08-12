/**
 * 响应式布局 Composable
 * 统一管理屏幕尺寸检测和断点判断
 * 遵循 KISS 原则：简单实用，避免过度设计
 */
import { ref, onMounted, onUnmounted, computed, readonly } from "vue";

// 断点与 Element Plus 2.x 保持一致，避免组件网格与业务判断在边界处错位。
export const BREAKPOINTS = {
  xs: 0,
  sm: 768,
  md: 992,
  lg: 1200,
  xl: 1920,
};

// 所有调用方共享同一个视口状态与监听器，避免页面内重复注册 resize 事件。
const screenWidth = ref(
  typeof window !== "undefined" ? window.innerWidth : BREAKPOINTS.lg
);
let consumerCount = 0;
let resizeTimer = null;

const updateScreenSize = () => {
  if (typeof window !== "undefined") {
    screenWidth.value = window.innerWidth;
  }
};

const handleResize = () => {
  if (resizeTimer) clearTimeout(resizeTimer);
  resizeTimer = setTimeout(updateScreenSize, 100);
};

const startListening = () => {
  if (typeof window === "undefined" || consumerCount > 0) return;
  updateScreenSize();
  window.addEventListener("resize", handleResize, { passive: true });
};

const stopListening = () => {
  if (typeof window === "undefined" || consumerCount > 0) return;
  window.removeEventListener("resize", handleResize);
  if (resizeTimer) {
    clearTimeout(resizeTimer);
    resizeTimer = null;
  }
};

/**
 * 响应式屏幕检测
 * @returns {Object} 响应式状态和工具方法
 */
export function useResponsive() {
  // 基础断点判断：移动端严格对应 Element Plus 的 xs 区间。
  const isMobile = computed(() => screenWidth.value < BREAKPOINTS.sm);
  const isTablet = computed(
    () =>
      screenWidth.value >= BREAKPOINTS.sm && screenWidth.value < BREAKPOINTS.md
  );
  const isDesktop = computed(() => screenWidth.value >= BREAKPOINTS.md);
  const isLargeScreen = computed(() => screenWidth.value >= BREAKPOINTS.xl);

  // 精确断点判断采用互斥区间，便于组件按当前档位渲染。
  const isXs = computed(() => screenWidth.value < BREAKPOINTS.sm);
  const isSm = computed(
    () =>
      screenWidth.value >= BREAKPOINTS.sm && screenWidth.value < BREAKPOINTS.md
  );
  const isMd = computed(
    () =>
      screenWidth.value >= BREAKPOINTS.md && screenWidth.value < BREAKPOINTS.lg
  );
  const isLg = computed(
    () =>
      screenWidth.value >= BREAKPOINTS.lg && screenWidth.value < BREAKPOINTS.xl
  );
  const isXl = computed(() => screenWidth.value >= BREAKPOINTS.xl);

  // 屏幕类型（用于日志或分析）
  const screenType = computed(() => {
    if (isXs.value) return "xs";
    if (isSm.value) return "sm";
    if (isMd.value) return "md";
    if (isLg.value) return "lg";
    return "xl";
  });

  onMounted(() => {
    startListening();
    consumerCount += 1;
    updateScreenSize();
  });

  onUnmounted(() => {
    consumerCount = Math.max(0, consumerCount - 1);
    stopListening();
  });

  return {
    // 屏幕宽度
    screenWidth: readonly(screenWidth),

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
