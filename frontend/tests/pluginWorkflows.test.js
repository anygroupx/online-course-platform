import test from "node:test";
import assert from "node:assert/strict";
import { canReadIntegration } from "../src/utils/pluginIntegrations.js";
import { serviceNames } from "../src/utils/serviceCommerce.js";
import { canPublishIntegration, pluginWorkflow, pluginCapabilityNames, serviceTypeFromQuery, validServiceProject,
  servicePublishTarget, servicePublishRequest, validPublicationProvider, serviceDestination, providerConfigurationType } from "../src/utils/pluginWorkflows.js";

const plugin = (props = {}) => ({ id: "P06", providerType: "sxdk_tw", integrationStatus: "NATIVE_PARTIAL",
  availableCapabilities: [], serviceCapabilities: ["LOOKUP", "CREATE", "SYNC", "EDIT_SCHEDULE"], projects: [], ...props });
const provider = (props = {}) => ({ id: 91, name: "实习配置", providerType: "sxdk_tw", status: 1, verified: true,
  verifiedAt: "2026-09-14T00:00:00", ...props });

test("native ordering does not depend on a catalog connector and cannot be guessed from a directory", () => {
  assert.equal(canReadIntegration(plugin()), false);
  assert.equal(canPublishIntegration(plugin()), true);
  assert.equal(pluginWorkflow(plugin()).kind, "service");
  assert.deepEqual(pluginCapabilityNames(plugin()), ["账号查询", "用户下单", "状态核对", "调整周期"]);
  for (const serviceCapabilities of [undefined, [], ["CATALOG"], "CREATE", null])
    assert.equal(canPublishIntegration(plugin({ availableCapabilities: ["CATALOG"], serviceCapabilities })), false);
  assert.equal(pluginWorkflow(plugin({ serviceCapabilities: [] })).kind, "unavailable");
  assert.equal(pluginWorkflow(plugin({ serviceCapabilities: [], availableCapabilities: ["CATALOG"] })).kind, "catalog");
  for (const integrationStatus of ["PLANNED", "NEEDS_PROTOCOL", "READ_ONLY", "DUPLICATE", null])
    assert.equal(canPublishIntegration(plugin({ integrationStatus })), false);
  assert.equal(canPublishIntegration(plugin({ duplicateOf: "P08" })), false);
  assert.deepEqual(pluginCapabilityNames(plugin({ availableCapabilities: ["CATALOG", "constructor"], serviceCapabilities: ["CREATE", "constructor", "CREATE"] })), ["商品 / 报价", "用户下单"]);
});

test("all twelve entries have separate friendly workflows; special entries never become service order types", () => {
  const types = ["flash", "27", "heisha", "jiguang", "ssbenz_xbd", "sxdk_tw", "syyv5", "jingyu", "appui", "wuxin", null, "leidian"];
  for (const [index, type] of types.entries()) {
    const descriptor = plugin({ id: `P${String(index + 1).padStart(2, "0")}`, providerType: type });
    const flow = pluginWorkflow(descriptor);
    assert.ok(flow.name && flow.summary && flow.notes.length, descriptor.id);
    assert.doesNotMatch([flow.name, flow.summary, ...flow.notes].join(" "), /上游|本平台|当前平台|对接|套娃|第三方|来源|渠道|货源/);
    assert.equal(canPublishIntegration(descriptor), Object.hasOwn(serviceNames, type));
  }
  assert.equal(pluginWorkflow(plugin({ id: "P02", providerType: "27", availableCapabilities: ["COURSE_CATALOG"] })).kind, "course");
  assert.equal(pluginWorkflow(plugin({ id: "P07", providerType: "syyv5", availableCapabilities: ["PROJECT_CENTER"] })).kind, "project");
  const shared = plugin({ id: "P11", providerType: null, integrationStatus: "DUPLICATE", duplicateOf: "P08" });
  assert.equal(pluginWorkflow(shared).kind, "shared");
  assert.equal(servicePublishTarget(shared, provider()), null);
  for (const id of ["unknown", "constructor", "__proto__", null]) assert.equal(pluginWorkflow({ id }).kind, "unavailable");
});

test("publishing carries only a validated saved ID, type and optional supported project", () => {
  const target = servicePublishTarget(plugin(), provider({ apiKey: "private-key", username: "private-user" }), "zxjy");
  assert.deepEqual(target, { path: "/admin/service-products", query: { providerType: "sxdk_tw", providerId: "91", project: "zxjy" } });
  assert.deepEqual(servicePublishRequest(target.query), { providerType: "sxdk_tw", providerId: "91", project: "zxjy" });
  assert.equal(JSON.stringify(target).includes("private"), false);
  for (const props of [{ status: 0 }, { status: 2 }, { status: "1" }, { verified: false }, { verified: "true" }, { providerType: "jiguang" }, { providerType: undefined }, { id: -1 }, { id: "01" }])
    assert.equal(servicePublishTarget(plugin(), provider(props)), null);
  for (const project of [[], {}, 0, false, "unknown", "__proto__"])
    assert.equal(servicePublishTarget(plugin(), provider(), project), null);
});

test("every service type accepts its own project, including default and total-distance products", () => {
  const pairs = { flash: "sdxy", heisha: "default", jiguang: "default", wuxin: "sdxy", sxdk_tw: "zxjy", appui: "9", leidian: "4", jingyu: "yyd", ssbenz_xbd: "xbd" };
  for (const [providerType, project] of Object.entries(pairs)) {
    assert.equal(validServiceProject(providerType, project), true);
    assert.ok(servicePublishRequest({ providerType, providerId: "91", project }));
    assert.equal(validServiceProject(providerType, "../../refund"), false);
  }
  assert.equal(validServiceProject("jingyu", "ymty"), false);
  assert.equal(validServiceProject("appui", "10"), false);
  assert.equal(validServiceProject("unknown", "sdxy"), false);
});

test("untrusted routes fail closed and never contain credentials, forged IDs or arbitrary destinations", () => {
  const valid = { providerType: "sxdk_tw", providerId: "91" };
  for (const providerId of ["0", "01", "-1", "1e2", "1.5", "91 ", "9007199254740992", 1.5, Infinity, null, {}, ["91"], "../9"])
    assert.equal(servicePublishRequest({ ...valid, providerId }), null);
  for (const providerType of ["27", "syyv5", "__proto__", "constructor", "FLASH", ["flash"], undefined, null]) {
    assert.equal(serviceTypeFromQuery(providerType), "");
    assert.equal(servicePublishRequest({ ...valid, providerType }), null);
    assert.equal(serviceDestination(providerType), null);
  }
  for (const query of [null, [], {}, "query", { ...valid, project: [] }, { ...valid, project: "" }]) assert.equal(servicePublishRequest(query), null);
  assert.deepEqual(servicePublishRequest({ ...valid, apiKey: "secret", unitPrice: "0.01", enabled: true }), { ...valid, project: "" });
  assert.deepEqual(serviceDestination("jiguang"), { path: "/services", query: { providerType: "jiguang" } });
  assert.deepEqual(serviceDestination("jiguang", true), { path: "/admin/service-orders", query: { providerType: "jiguang" } });
});

test("publication requires fresh metadata matching ID, type, explicit activation and verification", () => {
  const requested = servicePublishRequest({ providerType: "sxdk_tw", providerId: "91" });
  assert.equal(validPublicationProvider(provider(), requested), true);
  assert.equal(validPublicationProvider(provider({ verifiedAt: "2026-09-14 00:00:00" }), requested), true);
  for (const props of [{ id: 92 }, { id: "01" }, { providerType: "jiguang" }, { status: 0 }, { status: 2 }, { status: "1" },
    { verifiedAt: null }, { verifiedAt: "" }, { verifiedAt: true }, { verifiedAt: "verified" }, { verifiedAt: "2026-99-99T99:00:00" }])
    assert.equal(validPublicationProvider(provider(props), requested), false);
  assert.equal(validPublicationProvider(null, requested), false);
  assert.equal(validPublicationProvider(provider(), null), false);
});

test("setup routes preserve course and project types without making them service order types", () => {
  for (const type of ["27", "syyv5", ...Object.keys(serviceNames)]) assert.equal(providerConfigurationType(type), type);
  for (const value of [undefined, null, 27, ["27"], "unknown", "constructor", "__proto__", " 27"])
    assert.equal(providerConfigurationType(value), "");
  assert.equal(serviceTypeFromQuery("27"), "");
  assert.equal(serviceTypeFromQuery("syyv5"), "");
});
