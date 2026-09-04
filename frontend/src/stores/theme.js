import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getThemeVariables } from '@/api/variable'
import { themes } from '@/styles/themes'
import {
  THEME_COLOR_TOKENS,
  buildPrimaryGradient,
  isCssColor
} from '@/config/themeVariableConfig'

const EMPTY_THEME_OVERRIDES = () => ({ light: {}, dark: {} })

export const useThemeStore = defineStore('theme', () => {
  const currentThemeName = ref('light')
  const serverOverrides = ref(EMPTY_THEME_OVERRIDES())
  const loadingThemeVariables = ref(false)
  const themeVariablesLoaded = ref(false)

  const composeTheme = (themeName) => {
    const normalizedTheme = themes[themeName] ? themeName : 'light'
    const finalTheme = {
      ...themes[normalizedTheme],
      ...serverOverrides.value[normalizedTheme]
    }
    finalTheme['--primary-gradient'] = buildPrimaryGradient(finalTheme)
    return finalTheme
  }

  const applyTheme = (themeName, persistPreference = true) => {
    const normalizedTheme = themes[themeName] ? themeName : 'light'
    const root = document.documentElement

    Object.entries(composeTheme(normalizedTheme)).forEach(([key, value]) => {
      root.style.setProperty(key, value)
    })

    root.classList.toggle('dark', normalizedTheme === 'dark')
    currentThemeName.value = normalizedTheme
    if (persistPreference) {
      localStorage.setItem('app-theme-preference', normalizedTheme)
    }
  }

  const normalizeServerTheme = (values = {}) => {
    const result = {}
    for (const definition of THEME_COLOR_TOKENS) {
      const value = values[definition.key]
      if (isCssColor(value)) {
        result[definition.cssVariable] = value.trim()
      }
    }
    return result
  }

  const refreshThemeVariables = async () => {
    loadingThemeVariables.value = true
    try {
      const response = await getThemeVariables()
      serverOverrides.value = {
        light: normalizeServerTheme(response.data?.light),
        dark: normalizeServerTheme(response.data?.dark)
      }
      themeVariablesLoaded.value = true
      applyTheme(currentThemeName.value, false)
      return true
    } catch (error) {
      // 网络或后端不可用时继续使用内置主题，避免主题加载阻断应用启动。
      console.warn('加载系统主题变量失败，已使用内置主题。', error)
      return false
    } finally {
      loadingThemeVariables.value = false
    }
  }

  const toggleTheme = () => {
    applyTheme(currentThemeName.value === 'dark' ? 'light' : 'dark')
  }

  const initTheme = async () => {
    const stored = localStorage.getItem('app-theme-preference')
    const initialTheme = stored && themes[stored]
      ? stored
      : (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')

    // 先同步应用本地默认值，避免等待接口期间出现无主题闪烁。
    applyTheme(initialTheme, false)
    await refreshThemeVariables()
  }

  return {
    currentThemeName,
    serverOverrides,
    loadingThemeVariables,
    themeVariablesLoaded,
    composeTheme,
    applyTheme,
    toggleTheme,
    refreshThemeVariables,
    initTheme
  }
})
