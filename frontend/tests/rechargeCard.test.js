import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const rechargeView = await readFile(
  new URL("../src/views/Recharge.vue", import.meta.url),
  "utf8",
);

test("card recharge input accepts the full server-supported secret length", () => {
  assert.match(
    rechargeView,
    /v-model="cardForm\.cardPassword"[\s\S]*?maxlength="64"/,
  );
  assert.match(rechargeView, /请输入卡密（8到64位）/);
  assert.match(rechargeView, /卡密：8到64位字符/);
  assert.doesNotMatch(rechargeView, /请输入8位卡密|卡密：8位字符/);
});

test("card recharge validation remains aligned with the 8-to-64 character API contract", () => {
  assert.match(
    rechargeView,
    /cardPassword\.length < 8 \|\| cardForm\.value\.cardPassword\.length > 64/,
  );
  assert.match(rechargeView, /卡密长度必须为8到64位字符/);
});

test("card recharge action imports its API and submits through the form", () => {
  assert.match(
    rechargeView,
    /import \{ rechargeByCard \} from ["']\.\.\/api\/card["'];/,
  );
  assert.match(
    rechargeView,
    /<el-form[\s\S]*?@submit\.prevent="handleCardRecharge"[\s\S]*?<el-button[\s\S]*?native-type="submit"/,
  );
  assert.match(rechargeView, /await rechargeByCard\(cardForm\.value\)/);
  assert.doesNotMatch(
    rechargeView,
    /@click="handleCardRecharge"/,
  );
});

test("unexpected client-side submission failures are visible to the user", () => {
  assert.match(rechargeView, /if \(!error\?\.isAxiosError\)/);
  assert.match(rechargeView, /充值请求未能提交，请刷新页面后重试/);
});
