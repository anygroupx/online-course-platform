import { darkTheme, lightTheme, liquidGlassTheme } from '../styles/themes/index.js'

export const THEME_VARIABLE_TYPES = {
  light: 'theme_color_light',
  dark: 'theme_color_dark',
  'liquid-glass': 'theme_color_liquid_glass'
}

export const LIQUID_GLASS_THEME_TYPE = THEME_VARIABLE_TYPES['liquid-glass']

export const THEME_TYPE_TO_MODE = Object.fromEntries(
  Object.entries(THEME_VARIABLE_TYPES).map(([mode, type]) => [type, mode])
)

export const THEME_MODE_LABELS = {
  light: '浅色主题',
  dark: '深色主题',
  'liquid-glass': '液态玻璃'
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
    dark: darkTheme[cssVariable],
    'liquid-glass': liquidGlassTheme[cssVariable]
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

const materialToken = (
  key,
  label,
  description,
  control,
  defaultValue,
  options = {}
) => ({
  key,
  label,
  description,
  control,
  defaultValue,
  ...options
})

/**
 * 液态玻璃材质参数与光学引擎保持一一对应。
 * 这些参数独立于颜色 Token，避免颜色编辑器承担材质生命周期职责。
 */
export const LIQUID_GLASS_MATERIAL_TOKENS = [
  materialToken('glass_renderer_mode', '渲染模式', '自动选择增强折射或兼容毛玻璃', 'mode', 'auto', {
    options: [
      { label: '自动', value: 'auto' },
      { label: '增强折射', value: 'svg' },
      { label: '兼容毛玻璃', value: 'css' }
    ]
  }),
  materialToken('glass_refraction', '折射强度', '控制边缘背景位移强度', 'number', '56', {
    min: 0, max: 100, step: 1, unit: ''
  }),
  materialToken('glass_bevel', '斜面宽度', '控制产生折射与高光的边缘范围', 'number', '22', {
    min: 2, max: 48, step: 1, unit: 'px'
  }),
  materialToken('glass_blur', '光学模糊', '增强折射路径中的轻微模糊', 'number', '0.35', {
    min: 0, max: 8, step: 0.05, unit: 'px'
  }),
  materialToken('glass_dispersion', '色散强度', '控制边缘 RGB 通道的微弱分离', 'number', '1.2', {
    min: 0, max: 5, step: 0.05, unit: ''
  }),
  materialToken('glass_radius', '表面圆角', '控制主壳层和玻璃卡片的圆角', 'number', '24', {
    min: 8, max: 48, step: 1, unit: 'px'
  }),
  materialToken('glass_tint', '材质着色', '叠加在实时背景上的低透明度颜色', 'color', 'rgba(255,255,255,0.018)'),
  materialToken('glass_surface_opacity', '表面透明度', '控制普通业务卡片的透明表面强度', 'number', '0.65', {
    min: 0.12, max: 0.92, step: 0.01, unit: ''
  }),
  materialToken('glass_backdrop_blur', '兼容模糊', 'CSS 降级路径与普通业务表面的背景模糊', 'number', '16', {
    min: 4, max: 32, step: 1, unit: 'px'
  }),
  materialToken('glass_saturation', '背景饱和度', '控制玻璃后方内容的饱和度', 'number', '108', {
    min: 80, max: 160, step: 1, unit: '%'
  })
]

export const LIQUID_GLASS_MATERIAL_BY_KEY = Object.fromEntries(
  LIQUID_GLASS_MATERIAL_TOKENS.map((item) => [item.key, item])
)

export const DEFAULT_LIQUID_GLASS_MATERIAL = Object.fromEntries(
  LIQUID_GLASS_MATERIAL_TOKENS.map((item) => [item.key, item.defaultValue])
)

export const isValidMaterialValue = (definition, value) => {
  if (!definition || value === null || value === undefined) return false
  const normalized = String(value).trim()
  if (definition.control === 'mode') {
    return definition.options.some((option) => option.value === normalized)
  }
  if (definition.control === 'color') return isCssColor(normalized)
  const numericValue = Number(normalized)
  return Number.isFinite(numericValue)
    && numericValue >= definition.min
    && numericValue <= definition.max
}

export const normalizeLiquidGlassMaterial = (values = {}) => Object.fromEntries(
  LIQUID_GLASS_MATERIAL_TOKENS.map((definition) => {
    const value = values[definition.key]
    return [
      definition.key,
      isValidMaterialValue(definition, value) ? String(value).trim() : definition.defaultValue
    ]
  })
)

export const materialToEngineOptions = (values = {}) => {
  const material = normalizeLiquidGlassMaterial(values)
  return {
    mode: material.glass_renderer_mode,
    refraction: Number(material.glass_refraction),
    bevel: Number(material.glass_bevel),
    blur: Number(material.glass_blur),
    dispersion: Number(material.glass_dispersion),
    radius: Number(material.glass_radius),
    tint: material.glass_tint,
    fallbackBlur: Number(material.glass_backdrop_blur),
    saturation: Number(material.glass_saturation) / 100
  }
}

export const materialToCssVariables = (values = {}) => {
  const material = normalizeLiquidGlassMaterial(values)
  return {
    '--glass-refraction': material.glass_refraction,
    '--glass-bevel': `${material.glass_bevel}px`,
    '--glass-optical-blur': `${material.glass_blur}px`,
    '--glass-dispersion': material.glass_dispersion,
    '--glass-radius': `${material.glass_radius}px`,
    '--glass-tint': material.glass_tint,
    '--glass-surface-opacity': material.glass_surface_opacity,
    '--glass-backdrop-blur': `${material.glass_backdrop_blur}px`,
    '--glass-saturation': `${material.glass_saturation}%`
  }
}

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
