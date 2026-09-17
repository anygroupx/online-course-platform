export function buildBreadcrumbs(route) {
  const breadcrumbs = [{ key: 'home', name: '首页', to: '/dashboard' }]
  const groups = new Set()
  for (const record of route.matched || []) {
    const meta = record.meta || {}
    const name = meta.breadcrumbTitle || meta.title
    if (!name || meta.breadcrumb === false || record.name === 'Dashboard') continue
    if (meta.breadcrumbGroup && !groups.has(meta.breadcrumbGroup)) {
      groups.add(meta.breadcrumbGroup)
      breadcrumbs.push({ key: `group:${meta.breadcrumbGroup}`, name: meta.breadcrumbGroup })
    }
    const parameterNames = new Set([...record.path.matchAll(/:([A-Za-z0-9_]+)/g)].map((match) => match[1]))
    const params = Object.fromEntries(Object.entries(route.params || {}).filter(([key]) => parameterNames.has(key)))
    breadcrumbs.push({
      key: `route:${String(record.name || record.path)}`,
      name,
      to: record.name ? { name: record.name, params } : record.path,
    })
  }
  // Menu groups and the current page are text, not links to synthetic URLs.
  delete breadcrumbs[breadcrumbs.length - 1].to
  return breadcrumbs
}
