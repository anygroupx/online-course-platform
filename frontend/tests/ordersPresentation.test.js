import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const ordersConfigSource = readFileSync(
  new URL("../src/config/ordersConfig.js", import.meta.url),
  "utf8"
);
const ordersViewSource = readFileSync(
  new URL("../src/views/Orders.vue", import.meta.url),
  "utf8"
);
const enterpriseTableSource = readFileSync(
  new URL("../src/components/EnterpriseTable.vue", import.meta.url),
  "utf8"
);

test("用户订单列表展示备注列", () => {
  assert.match(
    ordersConfigSource,
    /key: "remarks",\s+label: "备注",\s+minWidth: 180,\s+visible: true,\s+formatter: \(row\) => row\.remarks \|\| "-"/
  );
  assert.match(
    ordersConfigSource,
    /export const mobileColumns = \[[\s\S]*?"remarks"[\s\S]*?\];/
  );
});

test("用户订单详情的执行记录不展示更新来源", () => {
  assert.doesNotMatch(ordersViewSource, /progress-log-source/);
  assert.doesNotMatch(ordersViewSource, /getProgressLogSourceText/);
  assert.doesNotMatch(ordersViewSource, />手动刷新</);
  assert.match(ordersViewSource, /<el-timeline-item[\s\S]*?完成进度：/);
});

test("旧的表格列配置会自动补入新增备注列", () => {
  assert.match(
    enterpriseTableSource,
    /const newColumnKeys = currentColumnKeys\.filter\([\s\S]*?columnOrder\.value = \[\.\.\.savedColumnOrder, \.\.\.newColumnKeys\];/
  );
});
