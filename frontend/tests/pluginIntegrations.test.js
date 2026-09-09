import test from "node:test";
import assert from "node:assert/strict";
import { canReadIntegration, canReadProvider, filterIntegrations, formatPluginPrice, integrationError,
  isReadOnlyProviderType, latestRequest, providerOptionLabel } from "../src/utils/pluginIntegrations.js";

const ready = { id: "P04", name: "极光", archive: "jiguang_user.zip", category: "运动计划",
  providerType: "jiguang", integrationStatus: "READ_ONLY", availableCapabilities: ["CATALOG", "SCHOOLS"], observedFeatures: ["学校查询"] };
test("observed features or a type alone never imply a callable capability", () => {
  assert.equal(canReadIntegration(ready), true);
  for (const change of [{ integrationStatus: "NEEDS_PROTOCOL" }, { availableCapabilities: [] }, { providerType: "unregistered" }]) {
    assert.equal(Boolean(canReadIntegration({ ...ready, ...change })), false);
  }
  assert.equal(isReadOnlyProviderType("jiguang&act=add"), false);
});
test("enabled and verified are both required", () => {
  assert.equal(canReadProvider({ status: 1, verified: true }), true);
  for (const provider of [{ status: 1 }, { status: 2, verified: true }, { status: 0, verified: true }, { status: 99, verified: true }]) {
    assert.equal(canReadProvider(provider), false);
  }
  assert.match(providerOptionLabel({ name: "测试配置", status: 2, verified: true }), /待启用/);
});
test("filter matches filenames, names, capabilities and status without mutating data", () => {
  assert.deepEqual(filterIntegrations([ready], "JIGUANG", "READ_ONLY"), [ready]);
  assert.deepEqual(filterIntegrations([ready], "学校", ""), [ready]);
  assert.deepEqual(filterIntegrations([ready], "极光", "DUPLICATE"), []);
});
test("decimal formatting never rounds through binary floating point", () => {
  assert.equal(formatPluginPrice("0.1"), "0.10");
  assert.equal(formatPluginPrice("0.123456"), "0.123456");
  assert.equal(formatPluginPrice("12.345000"), "12.345");
  assert.equal(formatPluginPrice("0.000001"), "0.000001");
  for (const value of ["-1", "NaN", "Infinity", "1e9", null, "1000000000", "0.1234567"]) assert.equal(formatPluginPrice(value), "—");
});
test("unknown server messages and malformed error IDs are never displayed", () => {
  const error = integrationError({ response: { data: { message: "key=unsafe-secret", errorId: "unsafe-secret", data: { reason: "unexpected-secret" } } } });
  assert.equal(error.errorId, "");
  assert.equal(error.message.includes("secret"), false);
});
test("latest request prevents stale provider, module and price responses", () => {
  const guard = latestRequest();
  const first = guard.begin(); assert.equal(first.current(), true);
  const second = guard.begin(); assert.equal(first.current(), false); assert.equal(first.signal.aborted, true);
  assert.equal(second.current(), true);
  guard.invalidate(); assert.equal(second.current(), false); assert.equal(second.signal.aborted, true);
});

test("error IDs must have UUID structure, not just 36 hexadecimal or hyphen characters", () => {
  for (const errorId of ["a".repeat(36), "-".repeat(36)]) {
    assert.equal(integrationError({ response: { data: { errorId } } }).errorId, "");
  }
  const errorId = "12345678-1234-1234-1234-123456789012";
  assert.equal(integrationError({ response: { data: { errorId } } }).errorId, errorId);
});

test("native business status preserves actual read-only catalog capabilities without inventing a catalog", () => {
  assert.equal(canReadIntegration({...ready, providerType:"flash", integrationStatus:"NATIVE_PARTIAL"}), true);
  assert.equal(Boolean(canReadIntegration({...ready, providerType:"sxdk_tw", integrationStatus:"NATIVE_PARTIAL", availableCapabilities:[]})), false);
});
