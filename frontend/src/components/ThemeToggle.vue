<template>
  <button
    class="theme-toggle"
    @click="toggleTheme"
    :title="iconState ? '切换到浅色模式' : '切换到深色模式'"
    :aria-label="iconState ? '切换到浅色模式' : '切换到深色模式'"
  >
    <div class="icon-container" :class="{ 'is-dark': iconState }">
      <!-- Sun Icon -->
      <svg
        class="icon sun"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <circle cx="12" cy="12" r="5"></circle>
        <line x1="12" y1="1" x2="12" y2="3"></line>
        <line x1="12" y1="21" x2="12" y2="23"></line>
        <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line>
        <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line>
        <line x1="1" y1="12" x2="3" y2="12"></line>
        <line x1="21" y1="12" x2="23" y2="12"></line>
        <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line>
        <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line>
      </svg>

      <!-- Moon Icon -->
      <svg
        class="icon moon"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path>
      </svg>
    </div>
  </button>
</template>

<script setup>
import { ref, watch, computed } from "vue";
import { useThemeStore } from "../stores/theme";

const themeStore = useThemeStore();
const isDark = computed(() => themeStore.currentThemeName === 'dark');

// 独立的图标状态，延迟更新以避免动画闪烁
const iconState = ref(isDark.value);

// 监听 isDark 变化，但不在过渡动画中立即更新图标
watch(isDark, (newVal) => {
  // 如果不在过渡动画中，立即同步
  if (!document.documentElement.classList.contains("view-transition-active")) {
    iconState.value = newVal;
  }
});

const toggleTheme = async (event) => {
  const isAppearanceTransition =
    document.startViewTransition &&
    !window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  if (!isAppearanceTransition) {
    themeStore.toggleTheme();
    iconState.value = isDark.value;
    return;
  }

  // 保存切换前的状态
  const wasDark = isDark.value;

  const x = event.clientX;
  const y = event.clientY;
  const endRadius = Math.hypot(
    Math.max(x, innerWidth - x),
    Math.max(y, innerHeight - y)
  );

  // 添加标记类，表示正在过渡
  document.documentElement.classList.add("view-transition-active");

  // startViewTransition 的回调是同步执行的，会捕获新状态
  const transition = document.startViewTransition(() => {
    themeStore.toggleTheme();
  });

  // 等待过渡准备好后，应用自定义动画
  transition.ready.then(() => {
    const clipPath = [
      `circle(0px at ${x}px ${y}px)`,
      `circle(${endRadius}px at ${x}px ${y}px)`,
    ];
    document.documentElement.animate(
      {
        clipPath: clipPath,
      },
      {
        duration: 400,
        easing: "ease-out",
        pseudoElement: "::view-transition-new(root)",
      }
    );
  });

  // 动画完成后再更新图标状态
  transition.finished
    .then(() => {
      iconState.value = isDark.value;
      document.documentElement.classList.remove("view-transition-active");
    })
    .catch(() => {
      // 如果动画被取消，也要清理状态
      iconState.value = isDark.value;
      document.documentElement.classList.remove("view-transition-active");
    });
};
</script>

<style>
/* View Transition 优化 - 防止闪烁 */
::view-transition-old(root),
::view-transition-new(root) {
  animation: none;
  mix-blend-mode: normal;
  overflow: hidden;
}

/* 始终让新视图在上层，通过 clip-path 扩展展示 */
::view-transition-old(root) {
  z-index: 1;
}

::view-transition-new(root) {
  z-index: 9999;
}
</style>

<style scoped>
/* 主题按钮模拟 Windows 11 的轻量实体控件，按下时回到基准面。 */
.theme-toggle {
  width: 38px;
  height: 38px;
  padding: 8px;
  border: 1px solid var(--border-color-light);
  border-radius: 12px;
  color: var(--text-regular);
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.14), transparent),
    color-mix(in srgb, var(--surface-solid) 58%, transparent);
  box-shadow:
    inset 0 1px 0 var(--stroke-highlight),
    0 3px 8px rgba(33, 51, 78, 0.10);
  transition:
    transform var(--motion-fast) cubic-bezier(0.16, 1, 0.3, 1),
    color var(--motion-fast) ease,
    background-color var(--motion-fast) ease;
}

.theme-toggle:hover {
  color: var(--brand-primary);
  background: color-mix(in srgb, var(--brand-primary) 9%, var(--surface-solid));
  transform: translateY(-1px);
}

.theme-toggle:active {
  transform: translateY(1px);
}

.icon {
  transition:
    opacity var(--motion-base) ease,
    transform var(--motion-base) cubic-bezier(0.16, 1, 0.3, 1);
}
</style>

<style scoped>
.theme-toggle {
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 8px;
  border-radius: 50%;
  color: var(--text-primary);
  transition: background-color 0.3s;
  overflow: hidden;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.theme-toggle:hover {
  background-color: rgba(0, 0, 0, 0.05);
}

html.dark .theme-toggle:hover {
  background-color: rgba(255, 255, 255, 0.1);
}

.icon-container {
  position: relative;
  width: 24px;
  height: 24px;
}

.icon {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  transition: all 0.5s cubic-bezier(0.4, 0, 0.2, 1);
}

.sun {
  opacity: 1;
  transform: rotate(0deg) scale(1);
}

.moon {
  opacity: 0;
  transform: rotate(90deg) scale(0);
}

.is-dark .sun {
  opacity: 0;
  transform: rotate(-90deg) scale(0);
}

.is-dark .moon {
  opacity: 1;
  transform: rotate(0deg) scale(1);
}
</style>
