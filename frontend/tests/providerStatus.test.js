import test from "node:test";
import assert from "node:assert/strict";
import { providerStatusLabel, canEnableProvider, providerCheckLabel } from "../src/utils/providerStatus.js";

const verified = { verifiedAt: "2026-09-06T01:00:00", lastCheckReason: "SUCCESS" };

test("a new configuration is pending and cannot be enabled", () => {
  assert.equal(providerStatusLabel({ status: 2 }), "待验证");
  assert.equal(canEnableProvider({ status: 2 }), false);
});
test("successful testing does not imply activation", () => {
  assert.equal(providerStatusLabel({ status: 2, ...verified }), "待启用");
  assert.equal(canEnableProvider({ status: 2, ...verified }), true);
});
test("a disabled provider stays disabled even after a successful test", () => {
  assert.equal(providerStatusLabel({ status: 0, ...verified }), "已禁用");
  assert.equal(canEnableProvider({ status: 0, ...verified }), true);
});
test("an active provider offers disable, not another enable", () => {
  assert.equal(providerStatusLabel({ status: 1, ...verified }), "已启用");
  assert.equal(canEnableProvider({ status: 1, ...verified }), false);
});
test("a later failed check prevents re-enabling", () => {
  for (const lastCheckReason of ["TIMEOUT", "DNS_FAILURE", null, ""]) {
    assert.equal(canEnableProvider({ status: 2, ...verified, lastCheckReason }), false);
  }
});
test("health checks alone cannot substitute for manual verification", () => {
  assert.equal(canEnableProvider({ status: 2, lastCheckReason: "SUCCESS" }), false);
});
test("unknown states are not implicitly activatable", () => {
  assert.equal(canEnableProvider({ status: 99, ...verified }), false);
  assert.equal(canEnableProvider({ ...verified }), false);
});
test("only allowlisted failure labels are rendered", () => {
  assert.equal(providerCheckLabel("PRIVATE_ADDRESS"), "域名解析到非公网地址");
  for (const reason of ["password=secret", "__proto__", "constructor", "toString", null]) {
    assert.equal(providerCheckLabel(reason), "尚未检查");
  }
});
