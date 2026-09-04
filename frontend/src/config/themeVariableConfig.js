import { darkTheme, lightTheme } from '@/styles/themes'

export const THEME_VARIABLE_TYPES = {
  light: 'theme_color_light',
  dark: 'theme_color_dark'
}

export const THEME_TYPE_TO_MODE = Object.fromEntries(
  Object.entries(THEME_VARIABLE_TYPES).map(([mode, type]) => [type, mode])
)

export const THEME_MODE_LABELS = {
  light: '浅色主题',
  dark: '深色主题'
}

export const THEME_GROUPS = [
  { key: 'brand', label: '品牌色', description: '主操作、链接、强调信息与品牌渐变' },
  { key: 'semantic', label: '功能色', description: '成功、警告、危险与信息反馈' },
  { key: 'surface', label: '背景与表面', description: '页面背景、卡片、浮层与半透明材质' },
  { key: 'content', label: '文字与边界', description: '文字层级、描边、高光与键盘焦点' }
]

const token = (key, cssVariable, label, group, description) => ({
  key,
  cssVariable,
  label,
  group,
  description,
  defaults: {
    light: lightTheme[cssVariable],
    dark: darkTheme[cssVariable]
  }
})

export const THEME_COLOR_TOKENS = [
  token('brand_primary', '--brand-primary', '品牌主色', 'brand', '主按钮、选中态和主要链接'),
  token('brand_primary_hover', '--brand-primary-hover', '主色悬停', 'brand', '主要操作的悬停状态'),
  token('brand_primary_pressed', '--brand-primary-pressed', '主色按下', 'brand', '主要操作的按下状态'),
  token('brand_cyan', '--brand-cyan', '品牌青色', 'brand', '辅助品牌色和渐变终点'),
  token('brand_violet', '--brand-violet', '品牌紫色', 'brand', '强调装饰和数据视觉辅助色'),
  token('primary_gradient_start', '--primary-gradient-start', '渐变起点', 'brand', '主品牌渐变的起始颜色'),
  token('primary_gradient_end', '--primary-gradient-end', '渐变终点', 'brand', '主品牌渐变的结束颜色'),

  token('color_success', '--color-success', '成功色', 'semantic', '成功、完成和正常状态'),
  token('color_warning', '--color-warning', '警告色', 'semantic', '提醒、等待和风险状态'),
  token('color_danger', '--color-danger', '危险色', 'semantic', '失败、删除和高风险状态'),
  token('color_info', '--color-info', '信息色', 'semantic', '一般信息和辅助提示'),

  token('bg_body', '--bg-body', '页面背景', 'surface', '应用主内容区的底色'),
  token('bg_card', '--bg-card', '卡片背景', 'surface', '常规卡片和容器背景'),
  token('bg_card_hover', '--bg-card-hover', '卡片悬停', 'surface', '可交互卡片的悬停背景'),
  token('bg_overlay', '--bg-overlay', '遮罩背景', 'surface', '浮层后方的半透明遮罩'),
  token('surface_solid', '--surface-solid', '实色表面', 'surface', '输入框、弹层等不透明表面'),
  token('surface_mica', '--surface-mica', '云母表面', 'surface', '页面级柔和半透明材质'),
  token('surface_acrylic', '--surface-acrylic', '亚克力表面', 'surface', '浮动卡片和导航半透明材质'),

  token('text_primary', '--text-primary', '主要文字', 'content', '标题和高强调正文'),
  token('text_regular', '--text-regular', '常规文字', 'content', '正文和表单内容'),
  token('text_secondary', '--text-secondary', '次要文字', 'content', '说明、辅助信息和元数据'),
  token('text_placeholder', '--text-placeholder', '占位文字', 'content', '输入提示和弱化内容'),
  token('text_on_brand', '--text-on-brand', '品牌色上文字', 'content', '主色按钮与品牌色背景上的文字'),
  token('border_color', '--border-color', '主要边框', 'content', '控件和卡片的常规描边'),
  token('border_color_light', '--border-color-light', '弱边框', 'content', '分隔线和低强调描边'),
  token('stroke_highlight', '--stroke-highlight', '表面高光', 'content', '半透明表面的顶部高光'),
  token('focus_ring', '--focus-ring', '焦点光环', 'content', '键盘操作时的可访问性焦点提示')
]

export const THEME_TOKEN_BY_KEY = Object.fromEntries(
  THEME_COLOR_TOKENS.map((item) => [item.key, item])
)

export const isThemeVariableType = (type) => Boolean(THEME_TYPE_TO_MODE[type])

export const getThemeModeByType = (type) => THEME_TYPE_TO_MODE[type] || 'light'

export const getThemeTypeByMode = (mode) => THEME_VARIABLE_TYPES[mode] || THEME_VARIABLE_TYPES.light

export const getThemeDefaults = (mode) => Object.fromEntries(
  THEME_COLOR_TOKENS.map((item) => [item.cssVariable, item.defaults[mode]])
)

export const buildThemeOverrides = (variables = []) => {
  return variables.reduce((result, variable) => {
    const definition = THEME_TOKEN_BY_KEY[variable.variableKey]
    if (definition && variable.isEnabled !== 0 && isCssColor(variable.variableValue)) {
      result[definition.cssVariable] = variable.variableValue.trim()
    }
    return result
  }, {})
}

export const isCssColor = (value) => {
  if (typeof value !== 'string' || !value.trim()) return false
  if (typeof CSS === 'undefined' || typeof CSS.supports !== 'function') {
    return /^(#[0-9a-f]{3,8}|rgba?\([\d\s.,%+-]+\)|hsla?\([\d\s.,%+-]+\)|transparent)$/i.test(value.trim())
  }
  return CSS.supports('color', value.trim())
}

export const buildPrimaryGradient = (tokens) => {
  const start = tokens['--primary-gradient-start'] || tokens['--brand-primary']
  const end = tokens['--primary-gradient-end'] || tokens['--brand-cyan']
  return `linear-gradient(135deg, ${start} 0%, ${end} 100%)`
}
