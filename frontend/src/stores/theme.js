import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getThemeVariables } from '@/api/variable'
import { themes } from '@/styles/themes'
import {
  THEME_COLOR_TOKENS,
  buildPrimaryGradient,
  isCssColor,
  materialToCssVariables,
  materialToEngineOptions,
  normalizeLiquidGlassMaterial
} from '@/config/themeVariableConfig'

const EMPTY_THEME_OVERRIDES = () => ({ light: {}, dark: {}, 'liquid-glass': {} })

export const useThemeStore = defineStore('theme', () => {
  const currentThemeName = ref('light')
  const serverOverrides = ref(EMPTY_THEME_OVERRIDES())
  const materialOverrides = ref({})
  const loadingThemeVariables = ref(false)
  const themeVariablesLoaded = ref(false)

  const liquidGlassMaterial = computed(() => normalizeLiquidGlassMaterial(materialOverrides.value))
  const liquidGlassOptions = computed(() => materialToEngineOptions(liquidGlassMaterial.value))

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

    const cssVariables = {
      ...composeTheme(normalizedTheme),
      ...materialToCssVariables(liquidGlassMaterial.value)
    }

    Object.entries(cssVariables).forEach(([key, value]) => {
      root.style.setProperty(key, value)
    })

    root.dataset.theme = normalizedTheme
    root.classList.toggle('dark', normalizedTheme === 'dark')
    root.classList.toggle('theme-liquid-glass', normalizedTheme === 'liquid-glass')
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
      const liquidGlassValues = response.data?.['liquid-glass'] || response.data?.liquid_glass || {}
      serverOverrides.value = {
        light: normalizeServerTheme(response.data?.light),
        dark: normalizeServerTheme(response.data?.dark),
        'liquid-glass': normalizeServerTheme(liquidGlassValues)
      }
      materialOverrides.value = normalizeLiquidGlassMaterial(liquidGlassValues)
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

  const cycleTheme = () => {
    const order = ['light', 'dark', 'liquid-glass']
    const currentIndex = order.indexOf(currentThemeName.value)
    const nextIndex = (currentIndex === -1 ? 0 : currentIndex + 1) % order.length
    applyTheme(order[nextIndex])
  }

  const toggleTheme = () => {
    cycleTheme()
  }

  const setTheme = (themeName) => {
    applyTheme(themeName)
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
    materialOverrides,
    liquidGlassMaterial,
    liquidGlassOptions,
    loadingThemeVariables,
    themeVariablesLoaded,
    composeTheme,
    applyTheme,
    toggleTheme,
    cycleTheme,
    setTheme,
    refreshThemeVariables,
    initTheme
  }
})
