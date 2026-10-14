import { mockAuthority } from './fixtures/rbac-policy.mjs'
import assert from 'node:assert/strict'
import test from 'node:test'
import { existsSync } from 'node:fs'
import { chromium } from 'playwright'
import { createTestServer } from './fixtures/test-server.mjs'

// 高亮指示器是 JS 定位的浮动块：updateActiveIndicator() 把激活项的几何写成
// --active-top/--active-height 两个 CSS 变量，.menu::before/::after 再消费它们。
// 因此它必须随菜单重排（折叠、滚动、窗口缩放）重新计算，否则会停在旧位置。
// 本测试断言「CSS 变量 == 现场几何」，而不是断言某个固定像素值。
const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="app"></div><script type="module">
import {createApp,h,nextTick} from 'vue';import {createPinia} from 'pinia';import {RouterView} from 'vue-router';import ElementPlus from 'element-plus';import * as icons from '@element-plus/icons-vue';
import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/element-overrides.scss';import '/src/styles/global.css';import '/src/styles/fluent-spatial.scss';import '/src/styles/responsive.scss';
import router from '/src/router/index.js';import {applyAuthSession} from '/src/utils/authSession.js';
applyAuthSession({ ...${JSON.stringify(mockAuthority('SUPER_ADMIN'))},token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',uid:'20000000-0000-4000-8000-000000000007',role:'SUPER_ADMIN',isAdmin:true,nickname:'测试管理员'});
const app=createApp({render:()=>h(RouterView)}).use(createPinia()).use(ElementPlus);for(const [name,icon] of Object.entries(icons))app.component(name,icon);
await router.push(new URLSearchParams(location.search).get('entry')||'/dashboard');app.use(router);await router.isReady();
app.mount('#app');
</script></body></html>`

const server = await createTestServer({
  logLevel: 'error',
  plugins: [{
    name: 'indicator-fixture',
    configureServer(vite) {
      vite.middlewares.use(async (req, res, next) => {
        if (!req.url?.startsWith('/__indicator')) return next()
        res.setHeader('Content-Type', 'text/html;charset=utf-8')
        res.end(await vite.transformIndexHtml(req.url, html))
      })
    },
  }],
})
const emptyPage = { records: [], total: 0, current: 1, size: 20 }
let browser, base

/** 读取「指示器实际落点」与「激活项现场几何」的差。 */
const indicatorDelta = (page) => page.evaluate(() => {
  const menu = document.querySelector('.menu')
  const item = menu?.querySelector('.el-menu-item.is-active')
  if (!menu || !item) return null
  const style = getComputedStyle(menu)
  const menuRect = menu.getBoundingClientRect()
  const itemRect = item.getBoundingClientRect()
  return {
    applied: parseFloat(style.getPropertyValue('--active-top')),
    appliedHeight: parseFloat(style.getPropertyValue('--active-height')),
    expected: itemRect.top - menuRect.top + menu.scrollTop,
    expectedHeight: itemRect.height,
    opacity: style.getPropertyValue('--active-opacity').trim(),
    scrollTop: menu.scrollTop,
    scrollHeight: menu.scrollHeight,
    clientHeight: menu.clientHeight,
    sidebarWidth: document.querySelector('.sidebar').getBoundingClientRect().width,
  }
})

await test('menu active indicator follows layout changes', async (t) => {
  try {
    await server.listen()
    base = `http://127.0.0.1:${server.httpServer.address().port}`
    browser = await chromium.launch({
      executablePath: process.env.BROWSER_PATH || (existsSync('/snap/bin/chromium') ? '/snap/bin/chromium' : undefined),
      args: ['--no-sandbox', '--disable-dev-shm-usage'],
    })

    await t.test('展开折叠栏后指示器跟随激活项移动', async (t) => {
      const context = await browser.newContext({ viewport: { width: 1440, height: 700 } })
      const page = await context.newPage()
      page.setDefaultTimeout(30000)
      const errors = []
      page.on('pageerror', (error) => errors.push(error.message))
      await context.route('**/*', async (route) => {
        const request = route.request()
        const url = new URL(request.url())
        if (url.origin !== base) return route.abort()
        if (!url.pathname.startsWith('/api/')) return route.continue()
        if (request.method() !== 'GET') return route.abort()
        const endpoint = url.pathname.slice(4)
        if (endpoint === '/announcement/system') return route.fulfill({ json: { code: 1, success: true, data: null }, headers: { 'Cache-Control': 'no-store' } })
        if (endpoint === '/announcement/latest' || endpoint === '/announcement/top') return route.fulfill({ json: { code: 1, success: true, data: [] }, headers: { 'Cache-Control': 'no-store' } })
        return route.fulfill({
          json: { code: 1, success: true, data: emptyPage },
          headers: { 'Cache-Control': 'no-store' },
        })
      })
      t.after(async () => { await context.close(); assert.deepEqual(errors, [], 'no browser errors') })

      // 激活项选在"服务中心"下拉之下，展开该下拉会把激活项整体下推。
      await page.goto(`${base}/__indicator?entry=${encodeURIComponent('/logs')}`)
      await page.locator('.main-container').waitFor()
      await page.locator('.menu .el-menu-item.is-active').waitFor()

      const before = await indicatorDelta(page)
      assert.ok(before, '应能找到激活菜单项')
      assert.ok(
        Math.abs(before.applied - before.expected) < 1.5,
        `初始就应对齐：applied=${before.applied} expected=${before.expected}`
      )

      await page.locator('.el-sub-menu__title', { hasText: '服务中心' }).first().evaluate((el) => el.click())
      await page.waitForTimeout(800)

      const expanded = await indicatorDelta(page)
      assert.ok(
        Math.abs(expanded.expected - before.expected) > 50,
        `展开折叠栏应把激活项明显下推：before=${before.expected} after=${expanded.expected}`
      )
      assert.equal(expanded.opacity, '1', '激活项仍在，指示器不应被隐藏')
      assert.ok(
        Math.abs(expanded.applied - expanded.expected) < 1.5,
        `展开折叠栏后指示器错位：applied=${expanded.applied} 实际应在 ${expanded.expected}（偏差 ${(expanded.applied - expanded.expected).toFixed(1)}px）`
      )

      // 折叠侧栏会重建菜单 DOM，行内定位变量随之丢失，必须重新写入。
      await page.locator('.collapse-btn').evaluate((el) => el.click())
      await page.waitForFunction(() => document.querySelector('.sidebar').getBoundingClientRect().width < 100)
      await page.waitForTimeout(800)

      const collapsed = await indicatorDelta(page)
      assert.ok(collapsed.sidebarWidth < 100, `侧栏应已折叠（宽度=${collapsed.sidebarWidth}）`)
      assert.equal(collapsed.opacity, '1', '折叠后激活项仍在，指示器不应丢失')
      assert.ok(
        Math.abs(collapsed.applied - collapsed.expected) < 1.5,
        `折叠后指示器错位：applied=${collapsed.applied} 实际应在 ${collapsed.expected}（偏差 ${(collapsed.applied - collapsed.expected).toFixed(1)}px）`
      )
    })

    await t.test('指示器跟随菜单滚动', async (t) => {
      const context = await browser.newContext({ viewport: { width: 1440, height: 620 } })
      const page = await context.newPage()
      page.setDefaultTimeout(30000)
      const errors = []
      page.on('pageerror', (error) => errors.push(error.message))
      await context.route('**/*', async (route) => {
        const url = new URL(route.request().url())
        if (url.origin !== base) return route.abort()
        if (!url.pathname.startsWith('/api/') || route.request().method() !== 'GET') return route.continue()
        const endpoint = url.pathname.slice(4)
        if (endpoint === '/announcement/system') return route.fulfill({ json: { code: 1, success: true, data: null }, headers: { 'Cache-Control': 'no-store' } })
        if (endpoint === '/announcement/latest' || endpoint === '/announcement/top') return route.fulfill({ json: { code: 1, success: true, data: [] }, headers: { 'Cache-Control': 'no-store' } })
        return route.fulfill({ json: { code: 1, success: true, data: emptyPage }, headers: { 'Cache-Control': 'no-store' } })
      })
      t.after(async () => { await context.close(); assert.deepEqual(errors, [], 'no browser errors') })

      await page.goto(`${base}/__indicator`)
      await page.locator('.menu .el-menu-item.is-active').waitFor()

      await page.locator('.menu').evaluate((el) => { el.scrollTop = 60 })
      await page.waitForTimeout(600)

      const scrolled = await indicatorDelta(page)
      assert.ok(scrolled.scrollTop > 0, '菜单应已滚动')
      assert.ok(
        Math.abs(scrolled.applied - scrolled.expected) < 1.5,
        `滚动后指示器未跟随：applied=${scrolled.applied} expected=${scrolled.expected}`
      )
    })
  } finally {
    await browser?.close()
    await server.close()
  }
})
