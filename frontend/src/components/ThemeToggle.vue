<template>
  <button
    class="theme-toggle"
    @click="toggleTheme"
    :title="buttonTitle"
    :aria-label="buttonTitle"
  >
    <div class="icon-container" :class="`theme-${themeState}`">
      <!-- Sun Icon -->
      <svg
        class="icon sun"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
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
        aria-hidden="true"
      >
        <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path>
      </svg>

      <!-- Liquid Glass Lens Icon -->
      <svg
        class="icon glass"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <rect x="3" y="3" width="18" height="18" rx="5" ry="5"></rect>
        <circle cx="8.5" cy="8.5" r="1.5"></circle>
        <path d="M21 15l-5-5L5 21"></path>
      </svg>
    </div>
  </button>
</template>

<script setup>
import { ref, watch, computed } from "vue";
import { useThemeStore } from "../stores/theme";

const themeStore = useThemeStore();
const currentTheme = computed(() => themeStore.currentThemeName);

// 独立的图标状态，延迟更新以避免动画闪烁
const themeState = ref(currentTheme.value);

const buttonTitle = computed(() => {
  if (themeState.value === 'light') return '当前：浅色模式（点击切换深色模式）';
  if (themeState.value === 'dark') return '当前：深色模式（点击切换液态玻璃主题）';
  return '当前：液态玻璃主题（点击切换浅色模式）';
});

// 监听 currentTheme 变化，但不在过渡动画中立即更新图标
watch(currentTheme, (newVal) => {
  if (!document.documentElement.classList.contains("view-transition-active")) {
    themeState.value = newVal;
  }
});

const toggleTheme = async (event) => {
  const isAppearanceTransition =
    document.startViewTransition &&
    !window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  if (!isAppearanceTransition) {
    themeStore.toggleTheme();
    themeState.value = currentTheme.value;
    return;
  }

  const x = event.clientX;
  const y = event.clientY;
  const endRadius = Math.hypot(
    Math.max(x, innerWidth - x),
    Math.max(y, innerHeight - y)
  );

  document.documentElement.classList.add("view-transition-active");

  const transition = document.startViewTransition(() => {
    themeStore.toggleTheme();
  });

  transition.ready.then(() => {
    const clipPath = [
      `circle(0px at ${x}px ${y}px)`,
      `circle(${endRadius}px at ${x}px ${y}px)`,
    ];
    document.documentElement.animate(
      { clipPath },
      {
        duration: 400,
        easing: "ease-out",
        pseudoElement: "::view-transition-new(root)",
      }
    );
  });

  transition.finished
    .then(() => {
      themeState.value = currentTheme.value;
      document.documentElement.classList.remove("view-transition-active");
    })
    .catch(() => {
      themeState.value = currentTheme.value;
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

::view-transition-old(root) {
  z-index: 1;
}

::view-transition-new(root) {
  z-index: 9999;
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
  transition: background-color 0.3s, transform 0.15s;
  overflow: hidden;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.theme-toggle:hover {
  background-color: rgba(0, 0, 0, 0.05);
  transform: translateY(-1px);
}

.theme-toggle:active {
  transform: translateY(1px);
}

html.dark .theme-toggle:hover,
html.theme-liquid-glass .theme-toggle:hover {
  background-color: rgba(255, 255, 255, 0.15);
}

.icon-container {
  position: relative;
  width: 22px;
  height: 22px;
}

.icon {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  opacity: 0;
  transform: rotate(90deg) scale(0.5);
  pointer-events: none;
}

/* Light active: show Sun */
.theme-light .sun {
  opacity: 1;
  transform: rotate(0deg) scale(1);
}

/* Dark active: show Moon */
.theme-dark .moon {
  opacity: 1;
  transform: rotate(0deg) scale(1);
}

/* Liquid Glass active: show Lens */
.theme-liquid-glass .glass {
  opacity: 1;
  transform: rotate(0deg) scale(1);
  color: var(--brand-primary, #3463ce);
}
</style>
