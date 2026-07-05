import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { themes } from '@/styles/themes'

export const useThemeStore = defineStore('theme', () => {
    // State
    const currentThemeName = ref('light')
    const customOverrides = ref({}) // For runtime user customization

    // Actions
    const applyTheme = (themeName) => {
        const theme = themes[themeName] || themes['light']
        const overrides = customOverrides.value

        // Merge base theme with overrides
        const finalTheme = { ...theme, ...overrides }

        // Apply to document root
        const root = document.documentElement

        Object.entries(finalTheme).forEach(([key, value]) => {
            root.style.setProperty(key, value)
        })

        // Handle Element Plus Dark Mode Class
        if (themeName === 'dark') {
            root.classList.add('dark')
        } else {
            root.classList.remove('dark')
        }

        currentThemeName.value = themeName
        localStorage.setItem('app-theme-preference', themeName)
    }

    const toggleTheme = () => {
        const nextTheme = currentThemeName.value === 'dark' ? 'light' : 'dark'
        applyTheme(nextTheme)
    }

    const updateVariable = (key, value) => {
        customOverrides.value[key] = value
        // Re-apply current theme to effect changes
        applyTheme(currentThemeName.value)
    }

    const initTheme = () => {
        const stored = localStorage.getItem('app-theme-preference')
        if (stored && themes[stored]) {
            applyTheme(stored)
        } else {
            // Check system preference
            const systemPrefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches
            applyTheme(systemPrefersDark ? 'dark' : 'light')
        }
    }

    return {
        currentThemeName,
        customOverrides,
        applyTheme,
        toggleTheme,
        updateVariable,
        initTheme
    }
})
