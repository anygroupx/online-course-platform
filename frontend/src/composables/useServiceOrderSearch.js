import { computed, onScopeDispose, ref, shallowRef, watch } from "vue";
import { latestRequest } from "../utils/pluginIntegrations.js";

export const orderSearchTypes = Object.freeze(["flash", "heisha", "jiguang", "wuxin", "sxdk_tw", "ssbenz_xbd", "appui", "leidian", "jingyu"]);
export const orderSearchStates = Object.freeze(["ACTIVE", "PAUSED", "COMPLETED", "CONFIRMING", "ATTENTION", "REFUND_REVIEW", "SUBMITTING", "SUBMITTED", "SUBMISSION_REVIEW", "REFUNDED", "CANCELLED"]);
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const controls = /[\u0000-\u001f\u007f-\u009f]/;
export const emptyOrderSearch = () => ({ keyword: "", providerType: "", status: "", ownerId: "", createdFrom: "", createdTo: "" });

function date(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value < "1000-01-01" || value > "9998-12-31") return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}

export function serviceOrderSearchParams(draft, admin = false, focus = "") {
  const values = {};
  for (const key of Object.keys(emptyOrderSearch())) {
    const value = draft?.[key] ?? "";
    if (typeof value !== "string" || controls.test(value)) throw new Error("FILTER");
    values[key] = value;
  }
  if (values.keyword.length > 100) throw new Error("KEYWORD");
  values.keyword = values.keyword.trim();
  if (values.providerType && !orderSearchTypes.includes(values.providerType)) throw new Error("FILTER");
  if (values.status && !orderSearchStates.includes(values.status)) throw new Error("FILTER");
  if (values.ownerId && (!admin || !/^[1-9]\d{0,18}$/.test(values.ownerId) || BigInt(values.ownerId) > 9223372036854775807n)) throw new Error("OWNER");
  for (const key of ["createdFrom", "createdTo"]) if (values[key] && !date(values[key])) throw new Error("DATE");
  if (values.createdFrom && values.createdTo && values.createdFrom > values.createdTo) throw new Error("DATE_RANGE");
  if (typeof focus !== "string" || (focus && !uuid.test(focus))) throw new Error("FOCUS");
  if (focus) values.orderId = focus;
  return Object.fromEntries(Object.entries(values).filter(([, value]) => value !== ""));
}

export function serviceOrderSearchPage(data, params) {
  if (!data || data.current !== params.page || data.size !== 20 || !Number.isSafeInteger(data.total) || data.total < 0 ||
      !Array.isArray(data.records) || data.records.length > 20 || data.records.length > data.total) throw new Error("RESPONSE");
  const seen = new Set();
  for (const row of data.records) {
    if (!row || typeof row.id !== "string" || !uuid.test(row.id) || seen.has(row.id) ||
        typeof row.title !== "string" || typeof row.accountLabel !== "string" ||
        !orderSearchTypes.includes(row.providerType) || !orderSearchStates.includes(row.status) ||
        !Array.isArray(row.actions) || row.actions.some((a) => typeof a !== "string") ||
        (params.orderId && row.id !== params.orderId) ||
        (params.providerType && row.providerType !== params.providerType) ||
        (params.status && row.status !== params.status)) throw new Error("RESPONSE");
    seen.add(row.id);
  }
  return { records: data.records, total: data.total, current: data.current, size: data.size };
}

const messages = Object.freeze({
  FILTER: "筛选条件不正确，请检查后重新查询。",
  KEYWORD: "搜索内容不能超过 100 个字符。",
  OWNER: "请输入有效的所属用户编号。",
  DATE: "请输入有效的创建日期。",
  DATE_RANGE: "开始日期不能晚于结束日期。",
  FOCUS: "订单定位信息不正确，请返回全部订单。",
  RESPONSE: "返回的订单列表不完整，请重试查询。",
  LOAD: "暂时无法读取服务订单，请重试查询。",
  FORBIDDEN: "暂时无法查看这些订单，请确认登录状态和查看权限。",
});

/** Applied filters are immutable until explicit submission; reads never trigger business actions. */
export function useServiceOrderSearch(read, { scope, admin, active, focus = () => "", visible = () => true, preset = () => "" }) {
  const providerPreset = computed(() => typeof preset() === "string" && orderSearchTypes.includes(preset()) ? preset() : "");
  const blankDraft = () => ({ ...emptyOrderSearch(), providerType: providerPreset.value });
  const draft = ref(blankDraft());
  const result = shallowRef(null), attempt = shallowRef(null);
  const loading = ref(false), error = ref(""), validation = ref("");
  const requests = latestRequest();
  let live = true;
  const applied = computed(() => attempt.value?.filters || {});
  const dirty = computed(() => {
    try { return JSON.stringify(serviceOrderSearchParams(draft.value, admin(), focus())) !== JSON.stringify(applied.value); }
    catch { return true; }
  });
  const items = computed(() => result.value?.records || []);
  const total = computed(() => result.value?.total || 0);
  const page = computed(() => result.value?.current || attempt.value?.page || 1);
  const pageCount = computed(() => Math.min(10000, Math.ceil(total.value / 20)));

  function pause() {
    requests.invalidate(); loading.value = false;
  }
  function clear() {
    requests.invalidate(); result.value = null; attempt.value = null;
    error.value = ""; validation.value = ""; loading.value = false;
  }
  async function execute(filters, currentPage) {
    if (!live || !active() || !visible() || !Number.isInteger(currentPage) || currentPage < 1 || currentPage > 10000) return false;
    const ticket = requests.begin();
    const query = { ...filters, page: currentPage, pageSize: 20 };
    attempt.value = { filters: { ...filters }, page: currentPage };
    result.value = null; loading.value = true; error.value = ""; validation.value = "";
    try {
      const response = await read(query, admin(), ticket.signal);
      if (!ticket.current()) return false;
      result.value = serviceOrderSearchPage(response, query);
      return true;
    } catch (failure) {
      if (ticket.current()) error.value = (Object.hasOwn(messages, failure?.message) ? messages[failure.message] : null) ||
        ([401, 403].includes(failure?.response?.status) ? messages.FORBIDDEN : messages.LOAD);
      return false;
    } finally {
      if (ticket.current()) loading.value = false;
    }
  }
  function submit() {
    let filters;
    try { filters = serviceOrderSearchParams(draft.value, admin(), focus()); }
    catch (failure) { validation.value = messages[failure.message] || messages.FILTER; return Promise.resolve(false); }
    return execute(filters, 1);
  }
  function refresh() {
    return attempt.value ? execute(attempt.value.filters, attempt.value.page) : submit();
  }
  function goToPage(next) {
    return result.value && !loading.value && Number.isInteger(next) && next >= 1 && next <= pageCount.value
      ? execute(applied.value, next) : Promise.resolve(false);
  }
  function retry() { return error.value && !dirty.value ? refresh() : Promise.resolve(false); }
  function reset() { draft.value = blankDraft(); return submit(); }

  watch([scope, active, focus, providerPreset], () => {
    clear(); draft.value = blankDraft();
    if (active() && visible()) void submit();
  }, { immediate: true, flush: "sync" });
  onScopeDispose(() => { live = false; clear(); });
  return { draft, applied, dirty, result, items, total, page, pageCount, loading, error, validation,
    submit, refresh, goToPage, retry, reset, pause };
}
