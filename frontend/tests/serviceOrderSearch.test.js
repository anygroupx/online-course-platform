import test from "node:test";
import assert from "node:assert/strict";
import { effectScope, ref } from "vue";
import { emptyOrderSearch, orderSearchTypes, orderSearchStates, serviceOrderSearchParams,
  serviceOrderSearchPage, useServiceOrderSearch } from "../src/composables/useServiceOrderSearch.js";

const id = "00000000-0000-0000-0000-000000000001";
const secondId = "00000000-0000-0000-0000-000000000002";
const row = (props = {}) => ({ id, title: "测试服务", accountLabel: "13***07", providerType: "jiguang", status: "ACTIVE", actions: [], ...props });
const data = (current = 1, props = {}) => ({ records: [row()], current, size: 20, total: 1, ...props });
const flush = () => new Promise((resolve) => setImmediate(resolve));
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; }
function setup(t, read, options = {}) {
  const context = ref("user:7"), admin = ref(false), active = ref(options.active ?? true), focus = ref(options.focus ?? ""), visible = ref(options.visible ?? true);
  const preset = ref(options.preset ?? "");
  const scope = effectScope();
  const state = scope.run(() => useServiceOrderSearch(read, { scope: () => context.value, admin: () => admin.value,
    active: () => active.value, focus: () => focus.value, visible: () => visible.value, preset: () => preset.value }));
  t.after(() => scope.stop());
  return { state, context, admin, active, focus, visible, preset, stop: () => scope.stop() };
}

test("order filters whitelist read-only fields and preserve literal text and 64-bit user IDs", () => {
  assert.deepEqual(serviceOrderSearchParams({ ...emptyOrderSearch(), keyword: "  名称!%_  ", password: "never-send" }), { keyword: "名称!%_" });
  const draft = { keyword: id, providerType: "sxdk_tw", status: "COMPLETED", ownerId: "9223372036854775807", createdFrom: "2024-02-29", createdTo: "9998-12-31" };
  assert.deepEqual(serviceOrderSearchParams(draft, true, secondId), { ...draft, orderId: secondId });
  for (const providerType of orderSearchTypes) assert.equal(serviceOrderSearchParams({ providerType }).providerType, providerType);
  for (const status of orderSearchStates) assert.equal(serviceOrderSearchParams({ status }).status, status);
  assert.deepEqual(serviceOrderSearchParams(emptyOrderSearch()), {});
});

test("invalid filters and foreign owner scope fail before issuing requests", () => {
  for (const keyword of ["x".repeat(101), "a\nb", "\u0000", "\u0085", 123, []]) assert.throws(() => serviceOrderSearchParams({ keyword }));
  for (const ownerId of ["0", "-1", "01", "1.5", "1e2", "9223372036854775808", "7 ", 7]) assert.throws(() => serviceOrderSearchParams({ ownerId }, true));
  assert.throws(() => serviceOrderSearchParams({ ownerId: "7" }, false), /OWNER/);
  for (const createdFrom of ["2026-02-29", "2026-09-31", "2026-9-01", "0999-12-31", "9999-01-01", "2026-09-12T00:00:00"]) assert.throws(() => serviceOrderSearchParams({ createdFrom }), /DATE/);
  assert.throws(() => serviceOrderSearchParams({ createdFrom: "2026-09-13", createdTo: "2026-09-12" }), /DATE_RANGE/);
  for (const focus of [null, [], [id], "bad-id", 1]) assert.throws(() => serviceOrderSearchParams({}, false, focus), /FOCUS/);
  assert.throws(() => serviceOrderSearchParams({ providerType: "unknown" }), /FILTER/);
  assert.throws(() => serviceOrderSearchParams({ status: "active" }), /FILTER/);
});

test("malformed pages cannot replace a previously valid order list", () => {
  const params = { page: 1, pageSize: 20 };
  assert.deepEqual(serviceOrderSearchPage(data(), params), data());
  assert.equal(serviceOrderSearchPage(data(1, { records: [], total: 0 }), params).total, 0);
  for (const bad of [null, {}, data(2), data(1, { size: 10 }), data("1"), data(1, { total: -1 }), data(1, { total: "1" }), data(1, { total: Number.MAX_SAFE_INTEGER + 1 }),
    data(1, { records: null }), data(1, { records: [row(), row()], total: 2 }), data(1, { records: Array(21).fill(row()), total: 21 }), data(1, { total: 0 })])
    assert.throws(() => serviceOrderSearchPage(bad, params), /RESPONSE/);
  for (const badRow of [null, row({ id: "bad" }), row({ status: "UNKNOWN" }), row({ providerType: "unknown" }), row({ title: null }), row({ accountLabel: null }), row({ actions: [1] })])
    assert.throws(() => serviceOrderSearchPage(data(1, { records: [badRow] }), params), /RESPONSE/);
  for (const extra of [{ orderId: secondId }, { providerType: "flash" }, { status: "COMPLETED" }])
    assert.throws(() => serviceOrderSearchPage(data(), { ...params, ...extra }), /RESPONSE/);
});

test("initial GET has bounded pagination and editing drafts does not submit them", async (t) => {
  const calls = [];
  const { state } = setup(t, async (...args) => { calls.push(args); return data(); });
  await flush();
  assert.deepEqual(calls[0].slice(0, 2), [{ page: 1, pageSize: 20 }, false]);
  assert.ok(calls[0][2] instanceof AbortSignal);
  state.draft.value.keyword = "服务%";
  await flush();
  assert.equal(calls.length, 1); assert.equal(state.dirty.value, true);
  assert.equal(state.items.value.length, 1); assert.deepEqual(state.applied.value, {});
  await state.submit();
  assert.deepEqual(calls.at(-1)[0], { keyword: "服务%", page: 1, pageSize: 20 });
  assert.equal(state.dirty.value, false);
});

test("refresh and pagination use applied filters, never unsubmitted edits", async (t) => {
  const calls = [];
  const { state } = setup(t, async (params) => { calls.push(params); return data(params.page, { total: 41 }); });
  await flush(); state.draft.value.keyword = "已查询"; await state.submit();
  await state.goToPage(2);
  state.draft.value.keyword = "未查询";
  await state.refresh(); await state.goToPage(3);
  assert.deepEqual(calls.slice(1).map((p) => [p.keyword, p.page]), [["已查询", 1], ["已查询", 2], ["已查询", 2], ["已查询", 3]]);
  await state.submit(); assert.equal(calls.at(-1).page, 1); assert.equal(calls.at(-1).keyword, "未查询");
  await state.reset(); assert.deepEqual(state.applied.value, {}); assert.equal(state.page.value, 1);
});

test("a failed page clears stale records and retries the exact page only on explicit request", async (t) => {
  const calls = []; let failing = true;
  const { state } = setup(t, async (params) => { calls.push(params); if (params.page === 2 && failing) throw new Error("private-provider-data"); return data(params.page, { total: 21 }); });
  await flush();
  assert.equal(await state.goToPage(2), false);
  assert.equal(state.items.value.length, 0); assert.equal(state.result.value, null); assert.equal(state.page.value, 2);
  assert.match(state.error.value, /重试查询/); assert.ok(!state.error.value.includes("private"));
  await flush(); assert.equal(calls.length, 2);
  state.draft.value.keyword = "新条件";
  assert.equal(await state.retry(), false); assert.equal(calls.length, 2);
  state.draft.value.keyword = ""; failing = false;
  assert.equal(await state.retry(), true); assert.equal(state.page.value, 2);
  assert.deepEqual(calls.map((p) => p.page), [1, 2, 2]);
  assert.equal(await state.retry(), false);
});

test("new searches abort and isolate older completions and errors", async (t) => {
  const pending = [];
  const { state } = setup(t, (params, admin, signal) => { const d = deferred(); pending.push({ ...d, params, signal }); return d.promise; });
  state.draft.value.keyword = "第二次"; const second = state.submit();
  assert.equal(pending[0].signal.aborted, true);
  pending[1].resolve(data(1, { records: [row({ title: "第二次" })] }));
  await second;
  pending[0].reject(new Error("expired request")); await flush();
  assert.equal(state.items.value[0].title, "第二次"); assert.equal(state.error.value, ""); assert.equal(state.loading.value, false);
  const third = state.refresh();
  assert.equal(state.items.value.length, 0, "old rows are not presented as the new in-flight page");
  pending[2].resolve(data(2)); await third;
  assert.match(state.error.value, /不完整/); assert.equal(state.result.value, null);
});

test("account, permissions, admin context and logout invalidate visible rows and pending reads", async (t) => {
  const pending = [];
  const { state, context, admin, active } = setup(t, (params, administrative, signal) => {
    const d = deferred(); pending.push({ ...d, params, administrative, signal }); return d.promise;
  });
  pending[0].resolve(data()); await flush(); state.draft.value.keyword = "私有搜索";
  context.value = "user:8";
  assert.equal(state.items.value.length, 0); assert.equal(state.draft.value.keyword, "");
  assert.equal(pending[0].signal.aborted, true);
  admin.value = true; context.value = "admin:8:permissions-changed";
  assert.equal(pending.at(-1).administrative, true);
  active.value = false;
  assert.equal(state.loading.value, false); assert.equal(state.result.value, null); assert.equal(pending.at(-1).signal.aborted, true);
  for (const p of pending.slice(1)) p.resolve(data()); await flush();
  assert.equal(state.items.value.length, 0);
  assert.equal(await state.refresh(), false);
  const count = pending.length; active.value = true; assert.equal(pending.length, count + 1);
});

test("inactive sessions do not read and disposal suppresses late completions", async (t) => {
  let count = 0; const d = deferred();
  const { state, active, stop } = setup(t, () => { count++; return d.promise; }, { active: false });
  assert.equal(count, 0); assert.equal(await state.submit(), false);
  active.value = true; assert.equal(count, 1); stop();
  d.resolve(data()); await flush();
  assert.equal(state.items.value.length, 0); assert.equal(state.loading.value, false);
  assert.equal(await state.submit(), false); assert.equal(count, 1);
});

test("focus resets pagination and invalid focus never falls back to all orders", async (t) => {
  const calls = [];
  const { state, focus } = setup(t, async (params) => { calls.push(params); return data(params.page, { records: [row({ id: params.orderId || id })], total: params.orderId ? 1 : 21 }); });
  await flush(); await state.goToPage(2); state.draft.value.keyword = "旧筛选";
  focus.value = secondId; await flush();
  assert.deepEqual(calls.at(-1), { orderId: secondId, page: 1, pageSize: 20 });
  assert.equal(state.items.value[0].id, secondId); assert.equal(state.draft.value.keyword, "");
  await state.reset(); assert.equal(calls.at(-1).orderId, secondId);
  const count = calls.length; focus.value = [id]; await flush();
  assert.equal(calls.length, count); assert.equal(state.items.value.length, 0); assert.match(state.validation.value, /定位信息/);
  focus.value = null; await flush(); assert.equal(calls.length, count);
  focus.value = ""; await flush(); assert.deepEqual(calls.at(-1), { page: 1, pageSize: 20 });
});

test("invalid drafts make no network request and preserve the labeled applied result", async (t) => {
  let count = 0;
  const { state } = setup(t, async () => { count++; return data(); });
  await flush(); state.draft.value.createdFrom = "2026-09-13"; state.draft.value.createdTo = "2026-09-12";
  assert.equal(await state.submit(), false); assert.equal(count, 1); assert.equal(state.items.value.length, 1);
  assert.match(state.validation.value, /开始日期/); assert.deepEqual(state.applied.value, {});
  await state.reset(); assert.equal(state.validation.value, ""); assert.equal(count, 2);
});

test("empty results remain distinct from malformed or forbidden responses", async (t) => {
  let response = data(1, { records: [], total: 0 }); let failure;
  const { state } = setup(t, async () => { if (failure) throw failure; return response; });
  await flush(); assert.ok(state.result.value); assert.equal(state.error.value, ""); assert.equal(state.total.value, 0);
  response = {}; await state.refresh(); assert.equal(state.result.value, null); assert.match(state.error.value, /不完整/);
  failure = { response: { status: 403 }, message: "sensitive explanation" }; await state.refresh();
  assert.match(state.error.value, /查看权限/); assert.equal(state.items.value.length, 0);
});

test("unknown error names are never treated as inherited message-map properties", async (t) => {
  let failure = new Error("constructor");
  const { state } = setup(t, async () => { throw failure; });
  await flush(); assert.equal(typeof state.error.value, "string"); assert.match(state.error.value, /重试查询/);
  failure = new Error("__proto__"); await state.refresh(); assert.equal(typeof state.error.value, "string");
});

test("page bounds reject fractional or excessive navigation and cap the UI range", async (t) => {
  let count = 0;
  const { state } = setup(t, async (params) => { count++; return data(params.page, { total: 300000 }); });
  await flush(); assert.equal(state.pageCount.value, 10000);
  for (const page of [0, -1, 1.5, 10001, NaN, "2"]) assert.equal(await state.goToPage(page), false);
  assert.equal(count, 1); assert.equal(await state.goToPage(10000), true); assert.equal(state.page.value, 10000);
});


test("hidden cached views preserve draft and applied filters without issuing background reads", async (t) => {
  const calls = [];
  const { state, visible } = setup(t, async (params) => { calls.push(params); return data(params.page, { total: 41 }); });
  await flush(); state.draft.value.keyword = "已查询"; await state.submit(); await state.goToPage(2);
  state.draft.value.keyword = "未提交";
  visible.value = false; state.pause();
  assert.equal(await state.refresh(), false); assert.equal(calls.length, 3);
  assert.equal(state.page.value, 2); assert.equal(state.applied.value.keyword, "已查询");
  assert.equal(state.draft.value.keyword, "未提交"); assert.equal(state.items.value.length, 1);
  visible.value = true; await flush();
  assert.equal(calls.length, 3, "reactivating a cached result does not issue a redundant request");
  await state.refresh();
  assert.deepEqual(calls.at(-1), { keyword: "已查询", page: 2, pageSize: 20 });
  assert.equal(state.draft.value.keyword, "未提交");
});

test("pausing an in-flight cached view aborts its read and ignores late completions", async (t) => {
  const pending = [];
  const { state, visible } = setup(t, (params, admin, signal) => {
    const d = deferred(); pending.push({ ...d, params, signal }); return d.promise;
  });
  visible.value = false; state.pause();
  assert.equal(pending[0].signal.aborted, true); assert.equal(state.loading.value, false);
  pending[0].resolve(data()); await flush();
  assert.equal(state.result.value, null);
  visible.value = true;
  const resumed = state.refresh();
  assert.equal(pending.length, 2);
  pending[1].resolve(data()); assert.equal(await resumed, true);
});

test("session changes clear hidden private data but do not fetch until the view is visible", async (t) => {
  const calls = [];
  const { state, context, visible } = setup(t, async (params) => { calls.push(params); return data(); });
  await flush(); state.draft.value.keyword = "私有搜索";
  visible.value = false; state.pause(); context.value = "user:8";
  assert.equal(state.items.value.length, 0); assert.equal(state.draft.value.keyword, "");
  assert.equal(await state.submit(), false); assert.equal(calls.length, 1);
  visible.value = true; await state.refresh();
  assert.equal(calls.length, 2); assert.deepEqual(state.applied.value, {});
});


test("workflow type presets survive reset and focus changes without widening owner permissions", async (t) => {
  const calls = [];
  const { state, preset, focus } = setup(t, async (params) => {
    calls.push(params); return data(1, { records: [row({ providerType: params.providerType || "jiguang", id: params.orderId || id })] });
  }, { preset: "sxdk_tw", focus: id });
  await flush(); assert.deepEqual(calls[0], { providerType: "sxdk_tw", orderId: id, page: 1, pageSize: 20 });
  state.draft.value.providerType = ""; await state.submit(); assert.equal(calls.at(-1).providerType, undefined);
  await state.reset(); assert.equal(calls.at(-1).providerType, "sxdk_tw");
  state.draft.value.ownerId = "8"; assert.equal(await state.submit(), false);
  assert.equal(calls.some(call => call.ownerId), false);
  preset.value = "appui"; await flush(); assert.equal(calls.at(-1).providerType, "appui");
  assert.equal(state.draft.value.ownerId, ""); assert.equal(calls.at(-1).orderId, id);
  focus.value = ""; await flush(); assert.equal(calls.at(-1).providerType, "appui"); assert.equal(calls.at(-1).orderId, undefined);
  preset.value = ["flash"]; await flush(); assert.equal(state.draft.value.providerType, "");
  assert.equal(calls.at(-1).providerType, undefined);
});

test("changing the workflow type aborts and discards the previous result", async (t) => {
  const pending = [];
  const { state, preset } = setup(t, (params, admin, signal) => {
    const d = deferred(); pending.push({ ...d, params, signal }); return d.promise;
  }, { preset: "sxdk_tw" });
  preset.value = "jiguang";
  assert.equal(pending.length, 2); assert.equal(pending[0].signal.aborted, true);
  pending[1].resolve(data()); await flush();
  pending[0].resolve(data(1, { records: [row({ providerType: "sxdk_tw" })] })); await flush();
  assert.equal(state.items.value[0].providerType, "jiguang");
  assert.deepEqual(state.applied.value, { providerType: "jiguang" });
});
