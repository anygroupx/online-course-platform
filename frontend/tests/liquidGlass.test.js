import assert from 'node:assert/strict'
import test from 'node:test'
import { GLASS_DEFAULTS } from '../src/components/liquid-glass/glass-engine.js'
import { liquidGlassTheme, themes } from '../src/styles/themes/index.js'
import {
  THEME_VARIABLE_TYPES,
  THEME_MODE_LABELS,
  THEME_COLOR_TOKENS,
  LIQUID_GLASS_MATERIAL_TOKENS,
  isValidMaterialValue,
  materialToCssVariables,
  materialToEngineOptions,
  isCssColor,
  buildPrimaryGradient
} from '../src/config/themeVariableConfig.js'

test('Liquid Glass engine defaults match reference baseline', () => {
  assert.equal(GLASS_DEFAULTS.radius, 40)
  assert.equal(GLASS_DEFAULTS.refraction, 56)
  assert.equal(GLASS_DEFAULTS.bevel, 22)
  assert.equal(GLASS_DEFAULTS.blur, 0.35)
  assert.equal(GLASS_DEFAULTS.dispersion, 1.2)
  assert.equal(GLASS_DEFAULTS.tint, 'rgba(255,255,255,.018)')
  assert.equal(GLASS_DEFAULTS.mode, 'auto')
})

test('liquid-glass theme contains cool-neutral visual language tokens', () => {
  assert.equal(liquidGlassTheme['--bg-body'], '#f5f6f8')
  assert.equal(liquidGlassTheme['--text-primary'], '#252b35')
  assert.equal(liquidGlassTheme['--text-secondary'], '#717a88')
  assert.equal(liquidGlassTheme['--border-color'], 'rgba(205, 210, 219, 0.72)')
  assert.equal(liquidGlassTheme['--brand-primary'], '#3463ce')
  assert.equal(liquidGlassTheme['--color-success'], '#37a06f')
  assert.ok(themes['liquid-glass'], 'liquid-glass is registered in themes dictionary')
})

test('liquid-glass theme variable configuration is complete and valid', () => {
  assert.equal(THEME_VARIABLE_TYPES['liquid-glass'], 'theme_color_liquid_glass')
  assert.equal(THEME_MODE_LABELS['liquid-glass'], '液态玻璃')

  // Check every token has a valid liquid-glass default
  for (const token of THEME_COLOR_TOKENS) {
    const defaultVal = token.defaults['liquid-glass']
    assert.ok(defaultVal, `Token ${token.key} has liquid-glass default`)
    assert.ok(isCssColor(defaultVal), `Token ${token.key} has valid CSS color: ${defaultVal}`)
  }

  const gradient = buildPrimaryGradient(liquidGlassTheme)
  assert.ok(gradient.includes('linear-gradient'), 'builds valid primary gradient')
})

test('liquid-glass material parameters drive engine and CSS fallback values', () => {
  assert.equal(LIQUID_GLASS_MATERIAL_TOKENS.length, 10)
  for (const definition of LIQUID_GLASS_MATERIAL_TOKENS) {
    assert.equal(isValidMaterialValue(definition, definition.defaultValue), true, definition.key)
  }

  const values = {
    glass_renderer_mode: 'css',
    glass_refraction: '48',
    glass_bevel: '18',
    glass_blur: '0.5',
    glass_dispersion: '0.8',
    glass_radius: '20',
    glass_tint: 'rgba(255,255,255,0.02)',
    glass_surface_opacity: '0.6',
    glass_backdrop_blur: '14',
    glass_saturation: '115'
  }
  const engine = materialToEngineOptions(values)
  const css = materialToCssVariables(values)

  assert.deepEqual(engine, {
    mode: 'css',
    refraction: 48,
    bevel: 18,
    blur: 0.5,
    dispersion: 0.8,
    radius: 20,
    tint: 'rgba(255,255,255,0.02)',
    fallbackBlur: 14,
    saturation: 1.15
  })
  assert.equal(css['--glass-radius'], '20px')
  assert.equal(css['--glass-backdrop-blur'], '14px')
  assert.equal(css['--glass-saturation'], '115%')
})

test('liquid-glass semantic control presets match audited reference', () => {
  // Audited presets from references/components.md:
  // Large material: radius 40, bevel 22, refraction 56, blur .35, dispersion 1.2
  // Button: radius 12, bevel 7, refraction 15, blur .35, dispersion .25
  // Switch thumb: radius 99, bevel 5, refraction 8, blur .2, dispersion .15
  // Slider thumb: radius 99, bevel 5, refraction 9, blur .25, dispersion .18
  // Segmented: radius 13, bevel 7, refraction 14, blur .4, dispersion .25
  // Chip: radius 99, bevel 6, refraction 11, blur .3, dispersion .2
  // Toolbar: radius 14, bevel 8, refraction 16, blur .4, dispersion .3
  const presets = {
    large: { radius: 40, bevel: 22, refraction: 56, blur: 0.35, dispersion: 1.2 },
    button: { radius: 12, bevel: 7, refraction: 15, blur: 0.35, dispersion: 0.25 },
    switchThumb: { radius: 99, bevel: 5, refraction: 8, blur: 0.2, dispersion: 0.15 },
    sliderThumb: { radius: 99, bevel: 5, refraction: 9, blur: 0.25, dispersion: 0.18 },
    segmented: { radius: 13, bevel: 7, refraction: 14, blur: 0.4, dispersion: 0.25 },
    chip: { radius: 99, bevel: 6, refraction: 11, blur: 0.3, dispersion: 0.2 },
    toolbar: { radius: 14, bevel: 8, refraction: 16, blur: 0.4, dispersion: 0.3 }
  }

  assert.equal(presets.large.refraction, 56)
  assert.equal(presets.button.bevel, 7)
  assert.equal(presets.switchThumb.radius, 99)
  assert.equal(presets.sliderThumb.dispersion, 0.18)
  assert.equal(presets.segmented.blur, 0.4)
  assert.equal(presets.chip.refraction, 11)
  assert.equal(presets.toolbar.dispersion, 0.3)
})
