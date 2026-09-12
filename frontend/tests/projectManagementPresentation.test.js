import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import {
  calculateUserProjectPrice,
  matchesProjectSearch,
} from "../src/utils/projectPricing.js";
import {
  getAssignableUserLevels,
  getUserLevelLabel,
} from "../src/utils/userLevel.js";

const readSource = (path) =>
  readFileSync(new URL(path, import.meta.url), "utf8");

const priceListSource = readSource("../src/views/PriceList.vue");
const layoutSource = readSource("../src/layouts/MainLayout.vue");
const routerSource = readSource("../src/router/index.js");
const dashboardSource = readSource("../src/views/Dashboard.vue");
const profileSource = readSource("../src/views/Profile.vue");
const usersSource = readSource("../src/views/Users.vue");
const usersConfigSource = readSource("../src/config/usersConfig.js");

const displayedRateTerms = /我的费率|当前费率|下级费率|邀请费率|结算倍率/;

test("/price-list 使用项目管理标题与导航名称", () => {
  assert.match(priceListSource, /项目管理/);
  assert.match(layoutSource, /index="\/price-list"[\s\S]*?<template #title>项目管理<\/template>/);
  assert.match(routerSource, /path: "price-list"[\s\S]*?title: "项目管理"/);
});

test("项目管理支持按名称、分类和说明搜索", () => {
  const project = {
    name: "study",
    displayName: "学习项目",
    categoryName: "继续教育",
    description: "支持在线学习和考试",
  };

  assert.equal(matchesProjectSearch(project, "学习"), true);
  assert.equal(matchesProjectSearch(project, "继续教育"), true);
  assert.equal(matchesProjectSearch(project, "考试"), true);
  assert.equal(matchesProjectSearch(project, "不存在"), false);
  assert.match(priceListSource, /placeholder="搜索项目名称、分类或说明"/);
});

test("项目管理只展示用户实际价格并兼容两种定价方式", () => {
  assert.equal(
    calculateUserProjectPrice({ basePrice: 10, rateType: "MULTIPLY" }, 0.8),
    8
  );
  assert.equal(
    calculateUserProjectPrice({ basePrice: 10, rateType: "ADD" }, 0.8),
    10.8
  );
  assert.match(priceListSource, /label="我的价格"/);
  assert.doesNotMatch(priceListSource, /基础价格|不同费率价格对比|价格计算说明/);
});

test("用户数值费率展示已替换为等级", () => {
  for (const source of [priceListSource, dashboardSource, profileSource, usersSource]) {
    assert.doesNotMatch(source, displayedRateTerms);
  }
  assert.match(dashboardSource, /账户等级/);
  assert.match(profileSource, /账户等级/);
  assert.match(usersConfigSource, /key: "level"[\s\S]*?label: "账户等级"/);
  assert.match(usersConfigSource, /key: "inviteLevel"[\s\S]*?label: "邀请等级"/);
});

test("等级映射稳定且可分配等级不会越过当前账户", () => {
  assert.equal(getUserLevelLabel(0.5), "钻石级");
  assert.equal(getUserLevelLabel(0.9), "白银级");
  assert.equal(getUserLevelLabel(1.8), "基础级");

  const options = getAssignableUserLevels(0.8);
  assert.ok(options.length > 0);
  assert.ok(options.every((option) => option.rate >= 0.8));
});

test("等级表单继续提交后端现有 rate 字段", () => {
  assert.match(usersSource, /createForm\.rate/);
  assert.match(usersSource, /inviteRate: inviteCodeForm\.value\.inviteRate/);
  assert.match(profileSource, /inviteRate: inviteForm\.value\.inviteRate/);
});
