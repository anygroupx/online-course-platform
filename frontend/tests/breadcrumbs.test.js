import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { createMemoryHistory, createRouter } from 'vue-router'
import routes from '../src/router/routes.js'
import { buildBreadcrumbs } from '../src/utils/breadcrumbs.js'

const router = createRouter({ history: createMemoryHistory(), routes })
const crumbs = (target) => buildBreadcrumbs(router.resolve(target))
const names = (target) => crumbs(target).map((item) => item.name)
const layout = routes.find((record) => record.name === 'Layout')

test('every layout page uses route metadata, never raw URL segments', () => {
  for (const record of layout.children.filter((record) => !record.redirect)) {
    const list = crumbs({ name: record.name })
    assert.equal(list[0].name, '首页', record.name)
    assert.equal(list.at(-1).name, record.meta.breadcrumbTitle || record.meta.title, record.name)
    assert.equal(list.at(-1).to, undefined, 'current page must not link to itself')
    assert.equal(new Set(list.map((item) => item.key)).size, list.length, 'stable unique render keys')
    assert.ok(list.every((item) => /[\u4e00-\u9fff]/.test(item.name)), record.name)
    if (record.meta.adminOnly) assert.ok(record.meta.breadcrumbGroup, `${record.name}: admin pages need an explicit group`)
    if (record.meta.breadcrumbGroup) {
      assert.equal(list[1].name, record.meta.breadcrumbGroup, record.name)
      assert.equal(list[1].to, undefined, 'menu group must not link to a nonexistent route')
    }
  }
})

test('home is shown once, including trailing slashes and query strings', () => {
  for (const path of ['/dashboard', '/dashboard/', '/dashboard?tab=stats#summary']) {
    assert.deepEqual(crumbs(path), [{ key: 'home', name: '首页' }])
  }
})

test('service and system routes belong to their own menu groups regardless of URL prefix', () => {
  for (const [path, expected] of [
    ['/service-projects', ['首页', '服务中心', '项目中心']],
    ['/project-clients?tab=usage', ['首页', '服务中心', '客户与 API']],
    ['/admin/service-projects', ['首页', '服务管理', '项目与子钱包']],
    ['/admin/service-products', ['首页', '服务管理', '服务商品']],
    ['/admin/service-orders', ['首页', '服务管理', '服务订单与对账']],
    ['/admin/plugin-integrations', ['首页', '服务管理', '接口接入检查']],
    ['/settings', ['首页', '系统管理', '系统设置']],
    ['/admin/announcements', ['首页', '系统管理', '公告管理']],
    ['/admin/cards', ['首页', '系统管理', '充值卡密']],
    ['/admin/variables', ['首页', '系统管理', '系统变量']],
    ['/admin/countdown', ['首页', '系统管理', '倒计时管理']],
    ['/admin/categories', ['首页', '系统管理', '分类管理']],
    ['/payment/orders', ['首页', '支付订单']],
    ['/payment/callback', ['首页', '支付结果']],
    ['/profile', ['首页', '个人中心']],
  ]) assert.deepEqual(names(path), expected)
})

test('submenu entries and breadcrumb titles stay consistent', () => {
  const source = readFileSync(new URL('../src/layouts/MainLayout.vue', import.meta.url), 'utf8')
  for (const [, path, title] of source.matchAll(/<el-menu-item index="([^"]+)">([^<]+)<\/el-menu-item>/g)) {
    assert.equal(crumbs(path).at(-1).name, title, path)
  }
  assert.doesNotMatch(source, /pathSegments|handleBreadcrumbClick/)
})

test('nested named routes preserve parent links and never expose parameter placeholders', () => {
  const nested = createRouter({ history: createMemoryHistory(), routes: [{
    path: '/projects/:projectId', name: 'Project', meta: { title: '项目详情', breadcrumbGroup: '服务中心' },
    children: [{ path: 'orders/:orderId', name: 'ProjectOrder', meta: { title: '订单详情', breadcrumbGroup: '服务中心' } }],
  }] })
  const route = nested.resolve('/projects/7/orders/8?tab=progress')
  const list = buildBreadcrumbs(route)
  assert.deepEqual(list.map((item) => item.name), ['首页', '服务中心', '项目详情', '订单详情'])
  assert.equal(list.at(-1).to, undefined)
  assert.equal(nested.resolve(list[2].to).path, '/projects/7')
})

test('untitled and explicitly hidden records do not leak internal paths', () => {
  const list = buildBreadcrumbs({ matched: [
    { name: 'Layout', path: '/', meta: {} },
    { name: 'Internal', path: '/internal', meta: { title: '隐藏分组', breadcrumb: false } },
    { name: 'Detail', path: '/internal/detail', meta: { title: '记录详情' } },
  ], params: {} })
  assert.deepEqual(list.map((item) => item.name), ['首页', '记录详情'])
  assert.equal(list[0].to, '/dashboard')
})
