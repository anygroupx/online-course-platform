import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const layoutSource = readFileSync(
  new URL("../src/layouts/MainLayout.vue", import.meta.url),
  "utf8"
);

const submenuSource = (index) => {
  const pattern = new RegExp(
    `<el-sub-menu[^>]*index="${index}"[^>]*>([\\s\\S]*?)<\\/el-sub-menu>`
  );
  const match = layoutSource.match(pattern);
  assert.ok(match, `应存在 ${index} 下拉菜单`);
  return match[0];
};

test("服务功能统一收纳到服务中心下拉菜单", () => {
  const serviceMenu = submenuSource("service-center");

  assert.match(serviceMenu, /<span>服务中心<\/span>/);
  for (const path of [
    "/services",
    "/service-projects",
    "/project-clients",
    "/service-orders",
  ]) {
    assert.match(serviceMenu, new RegExp(`index="${path}"`));
  }
});

test("服务管理员功能使用独立下拉菜单", () => {
  const serviceAdminMenu = submenuSource("service-admin");
  const systemAdminMenu = submenuSource("system-admin");

  assert.match(serviceAdminMenu, /v-if="userStore\.isAdmin"/);
  assert.match(serviceAdminMenu, /<span>服务管理<\/span>/);
  for (const path of [
    "/admin/service-products",
    "/admin/service-projects",
    "/admin/service-orders",
    "/admin/plugin-integrations",
  ]) {
    assert.match(serviceAdminMenu, new RegExp(`index="${path}"`));
    assert.doesNotMatch(systemAdminMenu, new RegExp(`index="${path}"`));
  }
});
