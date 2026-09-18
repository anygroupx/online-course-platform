import assert from 'node:assert/strict'
import test from 'node:test'
import { mkdirSync } from 'node:fs'
import { chromium } from 'playwright'
import { createTestServer } from './fixtures/test-server.mjs'

const materialValues = {
  glass_renderer_mode: 'auto',
  glass_refraction: '47',
  glass_bevel: '19',
  glass_blur: '0.4',
  glass_dispersion: '0.9',
  glass_radius: '22',
  glass_tint: 'rgba(255,255,255,0.024)',
  glass_surface_opacity: '0.62',
  glass_backdrop_blur: '14',
  glass_saturation: '112'
}

const colorValues = {
  brand_primary: '#3463ce',
  brand_primary_hover: '#2952b3',
  brand_primary_pressed: '#1f3f8c',
  brand_cyan: '#22b8cf',
  brand_violet: '#6366f1',
  primary_gradient_start: '#3463ce',
  primary_gradient_end: '#22b8cf',
  color_success: '#37a06f',
  color_warning: '#d97706',
  color_danger: '#dc2626',
  color_info: '#3463ce',
  bg_body: '#f5f6f8',
  bg_card: 'rgba(255, 255, 255, 0.65)',
  bg_card_hover: 'rgba(255, 255, 255, 0.82)',
  bg_overlay: 'rgba(245, 246, 248, 0.75)',
  surface_solid: '#ffffff',
  surface_mica: 'rgba(245, 247, 250, 0.80)',
  surface_acrylic: 'rgba(255, 255, 255, 0.52)',
  text_primary: '#252b35',
  text_regular: '#3b4453',
  text_secondary: '#717a88',
  text_placeholder: '#9aa2af',
  text_on_brand: '#ffffff',
  border_color: 'rgba(205, 210, 219, 0.72)',
  border_color_light: 'rgba(223, 226, 231, 0.60)',
  stroke_highlight: 'rgba(255, 255, 255, 0.95)',
  focus_ring: 'rgba(52, 99, 206, 0.35)'
}

const labels = {
  ...Object.fromEntries(Object.keys(colorValues).map((key) => [key, key])),
  glass_renderer_mode: '渲染模式',
  glass_refraction: '折射强度',
  glass_bevel: '斜面宽度',
  glass_blur: '光学模糊',
  glass_dispersion: '色散强度',
  glass_radius: '表面圆角',
  glass_tint: '材质着色',
  glass_surface_opacity: '表面透明度',
  glass_backdrop_blur: '兼容模糊',
  glass_saturation: '背景饱和度'
}

const themeRows = Object.entries({ ...colorValues, ...materialValues }).map(([key, value], index) => ({
  id: index + 1,
  variableKey: key,
  variableName: labels[key],
  variableType: 'theme_color_liquid_glass',
  variableValue: value,
  variableLabel: labels[key],
  sortOrder: index + 1,
  isDefault: 0,
  isEnabled: 1,
  color: null,
  icon: null
}))

const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="app"></div><script type="module">
import {createApp,h} from 'vue';import {createPinia} from 'pinia';import {RouterView} from 'vue-router';import ElementPlus from 'element-plus';import * as icons from '@element-plus/icons-vue';
import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/element-overrides.scss';import '/src/styles/global.css';import '/src/styles/fluent-spatial.scss';import '/src/styles/responsive.scss';import '/src/styles/liquid-glass-theme.scss';
import router from '/src/router/index.js';import {applyAuthSession} from '/src/utils/authSession.js';import {useThemeStore} from '/src/stores/theme.js';
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',uid:'20000000-0000-4000-8000-000000000007',role:'ADMIN',isAdmin:true,nickname:'测试管理员'});
localStorage.setItem('app-theme-preference','liquid-glass');
const pinia=createPinia();const app=createApp({render:()=>h(RouterView)}).use(pinia).use(ElementPlus);for(const [name,icon] of Object.entries(icons))app.component(name,icon);
await router.push('/admin/variables?type=theme_color_liquid_glass');app.use(router);await router.isReady();app.mount('#app');await useThemeStore(pinia).initTheme();
</script></body></html>`

const server = await createTestServer({
  logLevel: 'error',
  plugins: [{
    name: 'liquid-glass-global-fixture',
    configureServer(vite) {
      vite.middlewares.use(async (req, res, next) => {
        if (!req.url?.startsWith('/__liquid_glass_global')) return next()
        res.setHeader('Content-Type', 'text/html;charset=utf-8')
        res.end(await vite.transformIndexHtml(req.url, html))
      })
    }
  }]
})

const output = new URL('../../.cache/liquid-glass-global/', import.meta.url).pathname
mkdirSync(output, { recursive: true })

let browser
let base

test.before(async () => {
  await server.listen()
  base = `http://127.0.0.1:${server.config.server.port}`
  browser = await chromium.launch({ headless: true })
})

test.after(async () => {
  await browser?.close()
  await server.close()
})

async function openFixture(viewport, contextOptions = {}) {
  const context = await browser.newContext({ viewport, ...contextOptions })
  const page = await context.newPage()
  const errors = []
  const unexpected = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.route('**/*', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (url.origin !== base) {
      unexpected.push(request.url())
      return route.abort()
    }
    if (!url.pathname.startsWith('/api/')) return route.continue()
    const endpoint = url.pathname.slice(4)
    const respond = (data) => route.fulfill({
      json: { code: 1, success: true, data },
      headers: { 'Cache-Control': 'no-store' }
    })
    if (endpoint === '/client/bootstrap') return respond({})
    if (endpoint === '/theme/variables') {
      return respond({ light: {}, dark: {}, 'liquid-glass': { ...colorValues, ...materialValues } })
    }
    if (endpoint === '/announcement/system') return respond(null)
    if (endpoint === '/customer-service/unread-count') return respond(0)
    if (endpoint === '/admin/variables/types') {
      return respond(['theme_color_light', 'theme_color_dark', 'theme_color_liquid_glass'])
    }
    if (endpoint === '/admin/variables') {
      return respond({ records: themeRows, total: themeRows.length, current: 1, size: 100 })
    }
    unexpected.push(`${request.method()} ${endpoint}`)
    return route.abort()
  })
  await page.goto(`${base}/__liquid_glass_global`)
  await page.locator('.system-variable-management').waitFor()
  return { context, page, errors, unexpected }
}

test('global liquid-glass theme mounts optics and exposes material controls', async () => {
  const fixture = await openFixture({ width: 1440, height: 900 })
  const { context, page, errors, unexpected } = fixture
  try {
    assert.equal(await page.locator('html').getAttribute('data-theme'), 'liquid-glass')
    assert.equal(await page.locator('html').evaluate((node) => node.classList.contains('theme-liquid-glass')), true)
    assert.equal(await page.locator('html').evaluate((node) => getComputedStyle(node).getPropertyValue('--glass-backdrop-blur').trim()), '14px')

    for (const selector of ['.sidebar-material', '.header-material', '.theme-material-preview']) {
      const host = page.locator(selector)
      await host.waitFor()
      assert.equal(await host.getAttribute('data-glass-active'), 'true')
      assert.equal(await host.locator(':scope > .lg-surface').count(), 1)
      assert.equal(await host.locator(':scope > .lg-light').count(), 1)
      assert.equal(await host.locator(':scope > .lg-sheen').count(), 1)
      assert.equal(await host.locator(':scope > .lg-content').count(), 1)
    }

    await page.waitForFunction(() => document.querySelector('.header-material')?.dataset.glassRenderer === 'svg')
    assert.equal(await page.locator('.header-material').getAttribute('data-glass-renderer'), 'svg')

    assert.equal(await page.locator('.material-token-card').count(), 10)
    await page.getByText('折射 47 · 斜面 19 · 色散 0.9', { exact: true }).waitFor()

    await page.locator('.el-radio-button', { hasText: '兼容毛玻璃' }).click()
    await page.waitForFunction(() => document.querySelector('.theme-material-preview')?.dataset.glassRenderer === 'css')
    assert.equal(await page.locator('.theme-material-preview').getAttribute('data-glass-renderer'), 'css')
    await page.screenshot({ path: `${output}desktop.png`, fullPage: true, animations: 'disabled' })

    assert.deepEqual(errors, [])
    assert.deepEqual(unexpected, [])
  } finally {
    await context.close()
  }
})

test('global liquid-glass theme remains within responsive viewports', async () => {
  for (const width of [320, 390, 768, 1024, 1440]) {
    const fixture = await openFixture({ width, height: width <= 390 ? 844 : 900 })
    const { context, page, errors, unexpected } = fixture
    try {
      const geometry = await page.evaluate(() => ({
        viewport: document.documentElement.clientWidth,
        document: document.documentElement.scrollWidth,
        body: document.body.scrollWidth
      }))
      assert.ok(geometry.document <= geometry.viewport + 2, `${width}px document overflow: ${JSON.stringify(geometry)}`)
      assert.ok(geometry.body <= geometry.viewport + 2, `${width}px body overflow: ${JSON.stringify(geometry)}`)
      if (width === 390) {
        await page.screenshot({ path: `${output}mobile-390.png`, fullPage: true, animations: 'disabled' })
      }
      assert.deepEqual(errors, [], `${width}px has no page errors`)
      assert.deepEqual(unexpected, [], `${width}px makes no unexpected requests`)
    } finally {
      await context.close()
    }
  }
})

test('forced colors routes global optics to the solid accessibility renderer', async () => {
  const fixture = await openFixture({ width: 1024, height: 768 }, { forcedColors: 'active' })
  const { context, page, errors, unexpected } = fixture
  try {
    await page.waitForFunction(() => document.querySelector('.header-material')?.dataset.glassRenderer === 'solid')
    assert.equal(await page.locator('.header-material').getAttribute('data-glass-renderer'), 'solid')
    assert.equal(await page.locator('.theme-material-preview').getAttribute('data-glass-renderer'), 'solid')
    assert.deepEqual(errors, [])
    assert.deepEqual(unexpected, [])
  } finally {
    await context.close()
  }
})
