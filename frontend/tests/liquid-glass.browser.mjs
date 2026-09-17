import assert from 'node:assert/strict'
import test from 'node:test'
import { chromium } from 'playwright'
import { createTestServer } from './fixtures/test-server.mjs'

const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="app"></div><script type="module">
import { createApp, h } from 'vue';
import { createPinia } from 'pinia';
import { RouterView } from 'vue-router';
import ElementPlus from 'element-plus';
import * as icons from '@element-plus/icons-vue';
import 'element-plus/dist/index.css';
import 'element-plus/theme-chalk/dark/css-vars.css';
import '/src/styles/variables.scss';
import '/src/styles/element-overrides.scss';
import '/src/styles/global.css';
import '/src/styles/fluent-spatial.scss';
import '/src/styles/responsive.scss';
import '/src/styles/liquid-glass-theme.scss';
import router from '/src/router/index.js';

const app = createApp({ render: () => h(RouterView) }).use(createPinia()).use(ElementPlus);
for (const [name, icon] of Object.entries(icons)) app.component(name, icon);
await router.push('/standalone/liquid-glass');
app.use(router);
await router.isReady();
app.mount('#app');
</script></body></html>`

const server = await createTestServer({
  logLevel: 'error',
  plugins: [{
    name: 'liquid-glass-fixture',
    configureServer(vite) {
      vite.middlewares.use(async (req, res, next) => {
        if (!req.url?.startsWith('/__liquid_glass_test')) return next()
        res.setHeader('Content-Type', 'text/html;charset=utf-8')
        res.end(await vite.transformIndexHtml(req.url, html))
      })
    }
  }]
})

let browser, base

test.before(async () => {
  await server.listen()
  base = `http://127.0.0.1:${server.config.server.port}`
  browser = await chromium.launch({ headless: true })
})

test.after(async () => {
  if (browser) await browser.close()
  if (server) await server.close()
})

test('liquid glass workbench renders with 4-layer topology and live optics', async (t) => {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await context.newPage()
  page.setDefaultTimeout(30000)

  const errors = []
  page.on('pageerror', (err) => errors.push(err.message))

  await page.goto(`${base}/__liquid_glass_test`)
  await page.waitForSelector('.lgw-shell')

  // 1. Verify 4-layer topology
  const lens = page.locator('.lgw-lens')
  await lens.waitFor({ state: 'visible' })
  assert.ok(await lens.locator('.lg-surface').count() > 0, 'lg-surface is present')
  assert.ok(await lens.locator('.lg-light').count() > 0, 'lg-light is present')
  assert.ok(await lens.locator('.lg-sheen').count() > 0, 'lg-sheen is present')
  assert.ok(await lens.locator('.lg-content').count() > 0, 'lg-content is present')

  // Foreground content inside lens is sharp and readable
  const word = await lens.locator('.lgw-lens-word').textContent()
  assert.equal(word?.trim(), 'Transparent.')

  // Verify status badge
  const status = page.locator('.lgw-status')
  await status.waitFor({ state: 'visible' })
  const statusText = await status.textContent()
  assert.ok(statusText.includes('折射') || statusText.includes('毛玻璃') || statusText.includes('模式'))

  // Verify SVG defs exist for displacement when in Chromium
  const svgDefs = await page.locator('svg[data-lg-defs]').count()
  assert.ok(svgDefs > 0, 'SVG defs created in DOM')

  // 2. Test backdrop switching
  const gridBtn = page.locator('.lgw-toolbar button:has-text("网格")')
  await gridBtn.click()
  const stage = page.locator('.lgw-stage')
  assert.equal(await stage.getAttribute('data-backdrop'), 'grid')

  const textBtn = page.locator('.lgw-toolbar button:has-text("文字")')
  await textBtn.click()
  assert.equal(await stage.getAttribute('data-backdrop'), 'text')

  // 3. Test lens shape changes
  const pillBtn = page.locator('.lgw-inspector button:has-text("胶囊")')
  await pillBtn.click()
  assert.equal(await lens.getAttribute('data-shape'), 'pill')

  const circleBtn = page.locator('.lgw-inspector button:has-text("圆形")')
  await circleBtn.click()
  assert.equal(await lens.getAttribute('data-shape'), 'circle')

  const cardBtn = page.locator('.lgw-inspector button:has-text("卡片")')
  await cardBtn.click()
  assert.equal(await lens.getAttribute('data-shape'), 'card')

  // 4. Test keyboard movement (Arrow keys & Home)
  await lens.focus()
  const initialTransform = await lens.evaluate((el) => el.style.transform)
  await page.keyboard.press('ArrowRight')
  await page.waitForTimeout(50)
  const movedTransform = await lens.evaluate((el) => el.style.transform)
  assert.notEqual(initialTransform, movedTransform, 'Lens moves on arrow key navigation')

  await page.keyboard.press('Home')
  await page.waitForTimeout(50)

  // 5. Test semantic controls in the gallery
  // Button
  const smBtn = page.locator('.lg-button[data-size="sm"] button')
  await smBtn.click()

  // Switch
  const glassSwitch = page.locator('.lg-switch')
  const initialChecked = await glassSwitch.getAttribute('aria-checked')
  await glassSwitch.click()
  const newChecked = await glassSwitch.getAttribute('aria-checked')
  assert.notEqual(initialChecked, newChecked, 'GlassSwitch toggles aria-checked state')

  // Segmented
  const savedSegment = page.locator('.lg-segmented button:has-text("收藏")')
  await savedSegment.click()
  assert.equal(await savedSegment.getAttribute('aria-checked'), 'true')

  // Chips
  const devChip = page.locator('.lg-chip button:has-text("开发")')
  const chipPressedBefore = await devChip.getAttribute('aria-pressed')
  await devChip.click()
  const chipPressedAfter = await devChip.getAttribute('aria-pressed')
  assert.notEqual(chipPressedBefore, chipPressedAfter, 'GlassChip toggles aria-pressed')

  // 6. Test applying liquid glass theme
  const applyThemeBtn = page.locator('.lgw-apply-theme-btn')
  await applyThemeBtn.click()
  const themeDataset = await page.evaluate(() => document.documentElement.dataset.theme)
  assert.equal(themeDataset, 'liquid-glass', 'Applies liquid-glass theme to html root')
  const hasThemeClass = await page.evaluate(() => document.documentElement.classList.contains('theme-liquid-glass'))
  assert.ok(hasThemeClass, 'Sets theme-liquid-glass class on html root')

  assert.equal(errors.length, 0, `No unexpected errors: ${errors.join(', ')}`)
  await context.close()
})

test('liquid glass workbench is responsive across viewport widths without overflow', async (t) => {
  const widths = [1440, 1024, 768, 390, 320]
  for (const w of widths) {
    const context = await browser.newContext({ viewport: { width: w, height: 800 } })
    const page = await context.newPage()
    await page.goto(`${base}/__liquid_glass_test`)
    await page.waitForSelector('.lgw-shell')

    const hasOverflow = await page.evaluate(() => {
      return document.documentElement.scrollWidth > document.documentElement.clientWidth + 2
    })
    assert.ok(!hasOverflow, `Viewport width ${w}px does not horizontally overflow`)
    await context.close()
  }
})
