import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

// 菜单分组写在 MainLayout.vue 模板里，面包屑分组写在 routes.js 的 meta.breadcrumbGroup，
// 两套数据没有单一来源。任何一侧改了、另一侧忘了改，就会出现「面包屑显示一个侧边栏里
// 不存在的层级」这类矛盾，而且页面不会报错。这条门禁把两者钉在一起。
const layoutSource = readFileSync(new URL("../src/layouts/MainLayout.vue", import.meta.url), "utf8");
const routesSource = readFileSync(new URL("../src/router/routes.js", import.meta.url), "utf8");

/** 从菜单模板解析出每个菜单项所属的下拉分组（顶层项为 null）。 */
function parseMenuGroups(source) {
  const start = source.indexOf("<el-menu");
  const end = source.indexOf("</el-menu>");
  assert.ok(start >= 0 && end > start, "MainLayout.vue 应包含 <el-menu> 区块");
  const menu = source.slice(start, end);

  const token = /<el-sub-menu\s+([^>]*)>|<\/el-sub-menu>|<el-menu-item\s+([^>]*)>|<span>([^<]*)<\/span>/g;
  const stack = [];
  const groups = new Map();
  let pendingTitle = null;

  for (const match of menu.matchAll(token)) {
    const subAttrs = match[1];
    const itemAttrs = match[2];
    const spanText = match[3];

    if (subAttrs !== undefined) {
      stack.push({ items: [], title: null });
    } else if (match[0] === "</el-sub-menu>") {
      const closed = stack.pop();
      assert.ok(closed, "菜单模板的 <el-sub-menu> 标签不配对");
      closed.title = pendingTitle;
      pendingTitle = null;
      for (const path of closed.items) groups.set(path, closed.title);
    } else if (itemAttrs !== undefined) {
      const index = itemAttrs.match(/index="([^"]+)"/);
      assert.ok(index, `菜单项缺少 index：${itemAttrs}`);
      if (stack.length) stack.at(-1).items.push(index[1]);
      else groups.set(index[1], null);
    } else if (spanText !== undefined && stack.length && !stack.at(-1).title && !stack.at(-1).items.length) {
      pendingTitle = spanText.trim();
    }
  }
  assert.equal(stack.length, 0, "菜单模板的 <el-sub-menu> 标签未闭合");
  return groups;
}

/** 从 routes.js 解析出每个布局子路由的 breadcrumbGroup（引号无关）。 */
function parseBreadcrumbGroups(source) {
  const start = source.indexOf("children: [");
  assert.ok(start >= 0, "routes.js 应包含布局 children 数组");
  const body = source.slice(start);

  const groups = new Map();
  let depth = 0;
  let blockStart = null;
  for (let i = 0; i < body.length; i += 1) {
    if (body[i] === "{") {
      if (depth === 0) blockStart = i;
      depth += 1;
    } else if (body[i] === "}") {
      depth -= 1;
      if (depth === 0 && blockStart !== null) {
        const block = body.slice(blockStart, i + 1);
        const path = block.match(/path:\s*["']([^"']+)["']/);
        const group = block.match(/breadcrumbGroup:\s*["']([^"']+)["']/);
        if (path) groups.set(`/${path[1].replace(/^\/+/, "")}`, group ? group[1] : null);
        blockStart = null;
      }
    }
  }
  return groups;
}

test("菜单分组必须与路由的 breadcrumbGroup 一致", () => {
  const menuGroups = parseMenuGroups(layoutSource);
  const crumbGroups = parseBreadcrumbGroups(routesSource);

  assert.ok(menuGroups.size > 0, "应解析出菜单项");

  const drift = [];
  for (const [path, menuGroup] of menuGroups) {
    if (!crumbGroups.has(path)) continue; // 非布局子路由（或另有归属）不在此门禁范围
    const crumbGroup = crumbGroups.get(path);
    const expected = menuGroup ?? null;
    if (crumbGroup !== expected) {
      drift.push(`${path}：菜单=「${expected ?? "未分组"}」 面包屑=「${crumbGroup ?? "未分组"}」`);
    }
  }

  assert.deepEqual(drift, [], `菜单分组与面包屑分组漂移：\n${drift.join("\n")}`);
});

test("菜单项互不重复且都有 index", () => {
  const menuGroups = parseMenuGroups(layoutSource);
  const start = layoutSource.indexOf("<el-menu");
  const end = layoutSource.indexOf("</el-menu>");
  const paths = [...layoutSource.slice(start, end).matchAll(/<el-menu-item[^>]*index="([^"]+)"/g)].map((m) => m[1]);

  assert.equal(paths.length, menuGroups.size, "每个菜单项都应归属一个分组");

  const seen = new Set();
  const duplicates = [];
  for (const path of paths) {
    if (seen.has(path)) duplicates.push(path);
    seen.add(path);
  }
  assert.deepEqual(duplicates, [], `菜单项重复（移动分组时容易留下副本）：\n${duplicates.join("\n")}`);
});

/** 取菜单项的展示标题：优先 <template #title>，否则 el-icon 之后的剩余文本。 */
function titleOf(itemBody) {
  const templated = itemBody.match(/<template\s+#title>([\s\S]*?)<\/template>/);
  const source = templated ? templated[1] : itemBody.replace(/<el-icon>[\s\S]*?<\/el-icon>/g, "");
  return source.replace(/<[^>]+>/g, "").trim();
}

test("菜单项标题互不相同", () => {
  // 侧边栏里两个同名条目用户无法区分（例如「我的订单」与「全站订单」都叫「订单管理」）。
  // 标题本身不要求等于路由 title 或 breadcrumbTitle——那两者按上下文可以不同——但菜单内必须唯一。
  const start = layoutSource.indexOf("<el-menu");
  const end = layoutSource.indexOf("</el-menu>");
  const items = [...layoutSource.slice(start, end).matchAll(/<el-menu-item\s+([^>]*)>([\s\S]*?)<\/el-menu-item>/g)]
    .map((m) => ({ path: m[1].match(/index="([^"]+)"/)?.[1], title: titleOf(m[2]) }))
    .filter((item) => item.path && item.title);

  const byTitle = new Map();
  for (const item of items) {
    if (!byTitle.has(item.title)) byTitle.set(item.title, []);
    byTitle.get(item.title).push(item.path);
  }

  const collisions = [...byTitle.entries()]
    .filter(([, paths]) => paths.length > 1)
    .map(([title, paths]) => `「${title}」被 ${paths.join(" 与 ")} 共用`);

  assert.deepEqual(collisions, [], `菜单项标题重复，用户无法区分：\n${collisions.join("\n")}`);
});
