/**
 * Design Tokens & Theme Definitions
 * 
 * Defines the core color palette and semantic tokens for the application.
 * These are applied as CSS variables at runtime.
 */

export const commonTokens = {
    // Spacing & Radius
    '--radius-sm': '4px',
    '--radius-md': '8px',
    '--radius-lg': '12px',
    '--radius-xl': '16px',

    '--header-height': '60px',
    '--sidebar-width': '240px',

    // Shadows (Base definitions, can be overridden per theme if needed)
    '--shadow-sm': '0 2px 4px rgba(0, 0, 0, 0.05)',
    '--shadow-md': '0 4px 12px rgba(0, 0, 0, 0.08)',
    '--shadow-lg': '0 8px 24px rgba(0, 0, 0, 0.12)',
}

export const lightTheme = {
    ...commonTokens,

    // Brand Colors
    '--primary-gradient-start': '#667eea',
    '--primary-gradient-end': '#764ba2',
    '--primary-gradient': 'linear-gradient(135deg, var(--primary-gradient-start) 0%, var(--primary-gradient-end) 100%)',

    // Functional Colors
    '--color-success': '#67c23a',
    '--color-warning': '#e6a23c',
    '--color-danger': '#f56c6c',
    '--color-info': '#909399',

    // Backgrounds
    '--bg-body': '#f5f7fa',
    '--bg-card': 'rgba(255, 255, 255, 0.9)',
    '--bg-card-hover': 'rgba(255, 255, 255, 1)',
    '--bg-overlay': 'rgba(255, 255, 255, 0.8)',

    // Text
    '--text-primary': '#303133',
    '--text-regular': '#606266',
    '--text-secondary': '#909399',
    '--text-placeholder': '#a8abb2',

    // Borders
    '--border-color': '#dcdfe6',
    '--border-color-light': '#e4e7ed',
}

export const darkTheme = {
    ...commonTokens,

    // Brand Colors (Can be adjusted for dark mode if needed)
    '--primary-gradient-start': '#667eea',
    '--primary-gradient-end': '#764ba2',
    '--primary-gradient': 'linear-gradient(135deg, var(--primary-gradient-start) 0%, var(--primary-gradient-end) 100%)',

    // Functional Colors
    '--color-success': '#67c23a',
    '--color-warning': '#e6a23c',
    '--color-danger': '#f56c6c',
    '--color-info': '#909399',

    // Backgrounds
    '--bg-body': '#0f172a',
    '--bg-card': 'rgba(30, 41, 59, 0.8)',
    '--bg-card-hover': 'rgba(40, 50, 70, 0.9)',
    '--bg-overlay': 'rgba(0, 0, 0, 0.8)',

    // Text
    '--text-primary': '#FFFFFF',
    '--text-regular': '#E5EAF3',
    '--text-secondary': '#A3A6AD',
    '--text-placeholder': '#8D9095',

    // Borders
    '--border-color': '#4C4D4F',
    '--border-color-light': '#363637',

    // Shadow Overrides for Dark Mode
    '--shadow-sm': '0 2px 4px rgba(0, 0, 0, 0.3)',
    '--shadow-md': '0 4px 12px rgba(0, 0, 0, 0.4)',
    '--shadow-lg': '0 8px 24px rgba(0, 0, 0, 0.5)',
}

export const themes = {
    light: lightTheme,
    dark: darkTheme,
}
