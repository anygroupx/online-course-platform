import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

// keep-alive 的 include 按「组件自身的 name」匹配。Vue 在 <script setup> 下取
// 文件名推断出的 __name，而路由的 name 是另一套命名；两者不一致时页面看起来正常，
// 但缓存永远不生效——来回切换标签会重新加载、丢失滚动位置和表单状态。
// 因此凡是 name 与文件名不同的路由，都必须用 meta.keepAliveName 声明真实组件名。
const SRC = fileURLToPath(new URL("../src", import.meta.url));
const routesSource = readFileSync(join(SRC, "router/routes.js"), "utf8");

// 从 routes.js 里逐条取出布局子路由的 name / component / meta.keepAliveName。
function parseRoutes(source) {
  const childrenStart = source.indexOf("children: [");
  assert.ok(childrenStart >= 0, "routes.js 应包含布局 children 数组");
  const body = source.slice(childrenStart);

  const routes = [];
  let depth = 0;
  let start = null;
  for (let i = 0; i < body.length; i += 1) {
    const char = body[i];
    if (char === "{") {
      if (depth === 0) start = i;
      depth += 1;
    } else if (char === "}") {
      depth -= 1;
      if (depth === 0 && start !== null) {
        const block = body.slice(start, i + 1);
        const name = block.match(/name:\s*["']([^"']+)["']/);
        const comp = block.match(/import\(\s*["']([^"']+)["']\s*\)/);
        const keepAliveName = block.match(/keepAliveName:\s*["']([^"']+)["']/);
        if (name && comp) {
          routes.push({
            name: name[1],
            component: comp[1].split("/").pop().replace(/\.vue$/, ""),
            keepAliveName: keepAliveName ? keepAliveName[1] : null,
          });
        }
        start = null;
      }
    }
  }
  return routes;
}

test("name 与组件文件名不同的路由必须声明 meta.keepAliveName", () => {
  const offenders = parseRoutes(routesSource)
    .filter((route) => route.name !== route.component)
    .filter((route) => route.keepAliveName !== route.component)
    .map((route) => `${route.name} → ${route.component}（keepAliveName=${route.keepAliveName}）`);

  assert.deepEqual(
    offenders,
    [],
    `以下路由的 keepAliveName 与组件文件名不一致，keep-alive 缓存不会生效：\n${offenders.join("\n")}`
  );
});

test("keepAliveName 只用于确实需要缓存改名的路由", () => {
  // name 与组件名一致的路由不该冗余声明 keepAliveName。
  const redundant = parseRoutes(routesSource)
    .filter((route) => route.keepAliveName && route.keepAliveName === route.name)
    .map((route) => route.name);

  assert.deepEqual(redundant, [], `以下路由的 keepAliveName 与 name 相同，属冗余声明：\n${redundant.join("\n")}`);
});

test("每个缓存名都对应真实存在的组件文件", () => {
  const files = new Set();
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (entry.name.endsWith(".vue")) files.add(entry.name.replace(/\.vue$/, ""));
    }
  };
  walk(SRC);

  const missing = parseRoutes(routesSource)
    .filter((route) => !files.has(route.keepAliveName || route.component))
    .map((route) => route.keepAliveName || route.component);

  assert.deepEqual(missing, [], `以下 keep-alive 名称找不到对应组件文件：\n${missing.join("\n")}`);
});
