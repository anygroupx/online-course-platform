import { ref, watch, onMounted } from "vue";

const THEME_KEY = "app-theme-preference";

export function useTheme() {
  const isDark = ref(false);

  // Check system preference
  const systemPrefersDark = window.matchMedia("(prefers-color-scheme: dark)");

  const applyTheme = (dark) => {
    // 同步更新 DOM 类名，确保 View Transition 能捕获正确的状态
    if (dark) {
      document.documentElement.classList.add("dark");
    } else {
      document.documentElement.classList.remove("dark");
    }
    // 状态更新放在最后
    isDark.value = dark;
  };

  const toggleTheme = () => {
    const newDark = !isDark.value;
    localStorage.setItem(THEME_KEY, newDark ? "dark" : "light");
    applyTheme(newDark);
  };

  const initTheme = () => {
    const stored = localStorage.getItem(THEME_KEY);
    if (stored) {
      applyTheme(stored === "dark");
    } else {
      applyTheme(systemPrefersDark.matches);
    }
  };

  // Listen for system changes if no manual override
  systemPrefersDark.addEventListener("change", (e) => {
    if (!localStorage.getItem(THEME_KEY)) {
      applyTheme(e.matches);
    }
  });

  onMounted(() => {
    initTheme();
  });

  return {
    isDark,
    toggleTheme,
  };
}
