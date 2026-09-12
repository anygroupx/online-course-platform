import { test } from "node:test";
import assert from "node:assert/strict";
import { effectScope, ref } from "vue";
import { serviceSchoolPage, useServiceSchools } from "../src/composables/useServiceSchools.js";

const pageData = (page = 1, hasMore = false) => ({
  page, pageSize: 20, hasMore,
  items: Array.from({ length: hasMore ? 20 : 1 }, (_, i) => ({ id: `S-${page}-${i}`, name: `测试大学 ${page}-${i}` })),
});
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
function setup(t, load) {
  const productId = ref(1), active = ref(true), maxPage = ref(10000), idMode = ref("code");
  const scope = effectScope();
  const state = scope.run(() => useServiceSchools(load, {
    productId: () => productId.value, active: () => active.value, maxPage: () => maxPage.value, idMode: () => idMode.value,
  }));
  t.after(() => scope.stop());
  return { state, productId, active, maxPage, idMode, stop: () => scope.stop() };
}

test("school pages expose only proven fields, preserve names and do not invent totals", () => {
  const raw = pageData();
  raw.total = 99999;
  raw.items[0] = { id: "S-1", name: "  联合大学  ", url: "https://not-for-browsers.invalid", apiKey: "secret" };
  assert.deepEqual(serviceSchoolPage(raw, 1), { items: [{ id: "S-1", name: "  联合大学  " }], page: 1, hasMore: false });
  assert.equal(serviceSchoolPage({ page: 1, pageSize: 20, hasMore: false, items: [] }, 1).items.length, 0);
  assert.equal(serviceSchoolPage(pageData(10000, true), 10000).hasMore, false);
});

test("malformed pagination and unsafe or duplicate options fail closed", () => {
  const invalid = [null, {}, { ...pageData(), page: 2 }, { ...pageData(), page: "1" },
    { ...pageData(), pageSize: 500 }, { ...pageData(), hasMore: "true" },
    { ...pageData(), hasMore: true }, { ...pageData(), items: Array(21).fill({ id: "S", name: "大学" }) },
    { ...pageData(), items: [{ id: "S", name: "大学" }, { id: "S", name: "另一大学" }] }];
  for (const school of [null, { id: 1, name: "大学" }, { id: "url?key=private", name: "大学" },
    { id: "S", name: " " }, { id: "S", name: "a".repeat(161) }, { id: "S", name: "大学\n" },
    { id: "S", name: "大学\u0085" }]) invalid.push({ ...pageData(), items: [school] });
  for (const data of invalid) assert.throws(() => serviceSchoolPage(data, 1), /INVALID_SCHOOL_PAGE/);
  for (const maxPage of [0, -1, 10001, 1.5, NaN]) assert.throws(() => serviceSchoolPage(pageData(), 1, maxPage));
});

test("school discovery is explicit, supports an empty keyword, and sends no order fields", async (t) => {
  const calls = [];
  const { state } = setup(t, async (...args) => { calls.push(args); return pageData(); });
  assert.equal(calls.length, 0);
  assert.equal(await state.next(), false);
  assert.equal(await state.previous(), false);
  assert.equal(await state.retry(), false);
  assert.equal(await state.search(), true);
  assert.equal(calls.length, 1);
  assert.deepEqual(calls[0].slice(0, 2), [1, { page: 1, keyword: "" }]);
  assert.equal(calls[0][2] instanceof AbortSignal, true);
  assert.equal(state.canNext.value, false);
  assert.equal(state.canPrevious.value, false);
});

test("pagination stays on the searched keyword, uses hasMore and caps provider page bounds", async (t) => {
  const calls = [];
  const { state, maxPage } = setup(t, async (_, params) => { calls.push(params); return pageData(params.page, true); });
  maxPage.value = 2;
  state.keyword.value = "  大学  ";
  await state.search();
  await state.next();
  assert.equal(state.result.value.page, 2);
  assert.equal(state.canPrevious.value, true);
  assert.equal(await state.next(), false);
  await state.previous();
  assert.deepEqual(calls, [{ page: 1, keyword: "大学" }, { page: 2, keyword: "大学" }, { page: 1, keyword: "大学" }]);
  state.keyword.value = "学院";
  assert.equal(state.result.value, null);
  assert.equal(state.canNext.value, false);
  await state.search();
  assert.deepEqual(calls.at(-1), { page: 1, keyword: "学院" });
});

test("a failed next page retains the last good page and retries the exact failed page only on request", async (t) => {
  const calls = [];
  let fail = true;
  const { state } = setup(t, async (_, query) => {
    calls.push(query);
    if (query.page === 2 && fail) throw new Error("private provider response must not become UI text");
    return pageData(query.page, query.page === 1);
  });
  await state.search();
  const firstPage = state.result.value;
  assert.equal(await state.next(), false);
  assert.equal(state.result.value, firstPage);
  assert.equal(state.error.value, "LOAD");
  assert.equal(state.attempt.value.page, 2);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(calls.length, 2);
  fail = false;
  assert.equal(await state.retry(), true);
  assert.equal(state.result.value.page, 2);
  assert.deepEqual(calls.map((c) => c.page), [1, 2, 2]);
  assert.equal(state.error.value, "");
  assert.equal(await state.retry(), false);
});

test("changing a failed keyword drops its retry target and invalidates pagination immediately", async (t) => {
  const calls = [];
  const { state } = setup(t, async (_, query) => { calls.push(query); throw new Error("offline"); });
  state.keyword.value = "旧学校";
  await state.search();
  state.keyword.value = "新学校";
  assert.equal(state.error.value, "");
  assert.equal(state.attempt.value, null);
  assert.equal(await state.retry(), false);
  assert.equal(calls.length, 1);
});

test("malformed replies surface a retryable format error without treating them as an empty result", async (t) => {
  let good = false;
  const { state } = setup(t, async () => good ? pageData() : { ...pageData(), page: 3 });
  assert.equal(await state.search(), false);
  assert.equal(state.error.value, "RESPONSE");
  assert.equal(state.result.value, null);
  good = true;
  assert.equal(await state.retry(), true);
  assert.equal(state.result.value.items.length, 1);
});

test("double-clicks cannot overlap requests; editing a keyword aborts and rejects a late success", async (t) => {
  const old = deferred(), current = deferred(), calls = [];
  const { state } = setup(t, (_, query, signal) => {
    calls.push({ query, signal });
    return calls.length === 1 ? old.promise : current.promise;
  });
  state.keyword.value = "旧学校";
  const first = state.search();
  assert.equal(await state.search(), false);
  assert.equal(await state.next(), false);
  state.keyword.value = "新学校";
  assert.equal(calls[0].signal.aborted, true);
  assert.equal(state.loading.value, false);
  const second = state.search();
  old.resolve({ ...pageData(), items: [{ id: "OLD", name: "旧大学" }] });
  assert.equal(await first, false);
  assert.equal(state.result.value, null);
  assert.equal(state.loading.value, true);
  current.resolve({ ...pageData(), items: [{ id: "NEW", name: "新大学" }] });
  assert.equal(await second, true);
  assert.equal(state.result.value.items[0].id, "NEW");
  assert.equal(state.loading.value, false);
  assert.equal(calls.length, 2);
});

test("a stale failure cannot erase a newer successful query or flash a retry prompt", async (t) => {
  const old = deferred();
  let count = 0;
  const { state } = setup(t, () => ++count === 1 ? old.promise : Promise.resolve(pageData()));
  const first = state.search();
  state.keyword.value = "大学";
  await state.search();
  old.reject(new Error("late failure"));
  assert.equal(await first, false);
  assert.equal(state.error.value, "");
  assert.equal(state.result.value.items.length, 1);
});

test("switching products clears discovery and cancels results even when transport ignores abort", async (t) => {
  const pending = deferred(), calls = [];
  const { state, productId } = setup(t, (id, query, signal) => { calls.push({ id, query, signal }); return pending.promise; });
  state.keyword.value = "大学";
  const first = state.search();
  productId.value = 2;
  assert.equal(state.keyword.value, "");
  assert.equal(calls[0].signal.aborted, true);
  pending.resolve(pageData());
  assert.equal(await first, false);
  assert.equal(state.result.value, null);
});

test("lost authorization or a closed drawer cancels pending work and cannot launch new queries", async (t) => {
  const pending = deferred();
  let signal, calls = 0;
  const { state, active } = setup(t, (_, __, s) => { signal = s; calls++; return pending.promise; });
  const first = state.search();
  active.value = false;
  assert.equal(signal.aborted, true);
  assert.equal(state.loading.value, false);
  assert.equal(await state.search(), false);
  pending.resolve(pageData());
  assert.equal(await first, false);
  assert.equal(state.result.value, null);
  active.value = true;
  assert.equal(calls, 1, "restoring authorization must not auto-query");
});

test("unmount cancels work and makes even a retained search callback inert", async (t) => {
  const pending = deferred();
  let signal, count = 0;
  const { state, stop } = setup(t, (_, __, s) => { signal = s; count++; return pending.promise; });
  const first = state.search();
  stop();
  assert.equal(signal.aborted, true);
  assert.equal(await state.search(), false);
  pending.resolve(pageData());
  assert.equal(await first, false);
  assert.equal(count, 1);
});

test("overlong or control-character keywords are rejected locally; valid CJK and blanks are allowed", async (t) => {
  const calls = [];
  const { state } = setup(t, async (_, query) => { calls.push(query); return pageData(); });
  for (const term of ["a".repeat(81), "大学\n", "\u0000", "大学\u0085"]) {
    state.keyword.value = term;
    assert.equal(state.queryValid.value, false);
    assert.equal(await state.search(), false);
    assert.equal(state.error.value, "KEYWORD");
    assert.equal(await state.retry(), false);
  }
  assert.equal(calls.length, 0);
  for (const term of ["学".repeat(80), "  中文 English 大学  ", "   "]) {
    state.keyword.value = term;
    assert.equal(await state.search(), true);
    assert.equal(calls.at(-1).keyword, term.trim());
  }
});

test("name-identified AppUI school pages preserve exact Unicode names only in explicit name mode", () => {
  const name = "示例职业学院（东校区）";
  const data = { page: 1, pageSize: 20, hasMore: false, items: [{ id: name, name, extra: "discard" }] };
  assert.throws(() => serviceSchoolPage(data, 1, 150), /INVALID_SCHOOL_PAGE/);
  assert.deepEqual(serviceSchoolPage(data, 1, 150, "name"), { page: 1, hasMore: false, items: [{ id: name, name }] });
  assert.throws(() => serviceSchoolPage({ ...data, page: 151 }, 151, 150, "name"), /INVALID_SCHOOL_PAGE/);
  const last = { page: 150, pageSize: 20, hasMore: true, items: Array.from({ length: 20 }, (_, i) => ({ id: `学院${i}`, name: `学院${i}` })) };
  assert.equal(serviceSchoolPage(last, 150, 150, "name").hasMore, false);
});

test("name identifiers cannot broaden the school response trust boundary", () => {
  const data = { page: 1, pageSize: 20, hasMore: false, items: [] };
  for (const items of [
    [{ id: "school-code", name: "示例大学" }], [{ id: 1, name: "示例大学" }],
    [{ id: "示例大学", name: "另一大学" }], [{ id: "学院".repeat(51), name: "学院".repeat(51) }],
    [{ id: "示例\n大学", name: "示例\n大学" }], [{ id: " 示例大学 ", name: " 示例大学 " }],
    [{ id: "示例大学", name: "示例大学" }, { id: "示例大学", name: "示例大学" }],
  ]) assert.throws(() => serviceSchoolPage({ ...data, items }, 1, 150, "name"), /INVALID_SCHOOL_PAGE/);
  for (const mode of [null, "unknown", {}, true]) assert.throws(() => serviceSchoolPage(data, 1, 150, mode), /INVALID_SCHOOL_PAGE/);
});

test("switching identifier mode invalidates pending discovery and keeps the new page bounded", async (t) => {
  const pending = deferred(), calls = [];
  const { state, idMode, maxPage } = setup(t, (_, params, signal) => {
    calls.push({ params, signal });
    return calls.length === 1 ? pending.promise : { page: 1, pageSize: 20, hasMore: false, items: [{ id: "示例大学", name: "示例大学" }] };
  });
  const old = state.search();
  idMode.value = "name";
  assert.equal(calls[0].signal.aborted, true);
  assert.equal(state.result.value, null);
  maxPage.value = 150;
  assert.equal(await state.search(), true);
  pending.resolve(pageData());
  assert.equal(await old, false);
  assert.equal(state.result.value.items[0].id, "示例大学");
  assert.equal(state.canNext.value, false);
});
