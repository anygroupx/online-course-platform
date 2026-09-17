import { computed, onScopeDispose, ref, shallowRef, watch } from "vue";
import { latestRequest } from "../utils/pluginIntegrations.js";

export const schoolKeywordLimit = 80;
const pageSize = 20;
const controlCharacters = /[\u0000-\u001f\u007f-\u009f]/;

// Both school APIs expose a bounded page, not a trustworthy total count.
export function serviceSchoolPage(data, requestedPage, maxPage = 10000, idMode = "code") {
  if (!["code", "name"].includes(idMode) || !Number.isInteger(maxPage) || maxPage < 1 || maxPage > 10000 || !Number.isInteger(requestedPage) || requestedPage < 1 || requestedPage > maxPage ||
      !data || data.page !== requestedPage || data.pageSize !== pageSize ||
      typeof data.hasMore !== "boolean" || !Array.isArray(data.items) ||
      data.items.length > pageSize || (data.hasMore && data.items.length !== pageSize)) {
    throw new Error("INVALID_SCHOOL_PAGE");
  }
  const ids = new Set();
  const items = data.items.map((school) => {
    // Name-only catalogs must opt in and match id/name exactly; code-based catalogs stay strict.
    if (!school || typeof school.id !== "string" || ids.has(school.id) ||
        typeof school.name !== "string" || !school.name.trim() ||
        school.name.length > 160 || controlCharacters.test(school.name) ||
        (idMode === "name"
          ? school.id !== school.name || school.id.length > 100 || school.id !== school.id.trim()
          : !/^[A-Za-z0-9_-]{1,64}$/.test(school.id))) {
      throw new Error("INVALID_SCHOOL_PAGE");
    }
    ids.add(school.id);
    return { id: school.id, name: school.name };
  });
  return { items, page: requestedPage, hasMore: data.hasMore && requestedPage < maxPage };
}

/** Search owns only discovery state; selecting/clearing an order field is always explicit. */
export function useServiceSchools(load, { productId, active, maxPage, idMode = () => "code", requireKeyword = () => false }) {
  const keyword = ref("");
  const result = shallowRef(null);
  const attempt = shallowRef(null);
  const error = ref("");
  const loading = ref(false);
  const requests = latestRequest();
  const live = ref(true);
  const enabled = computed(() => live.value && active() && productId() != null && productId() !== "");
  const queryValid = computed(() => typeof keyword.value === "string" &&
    keyword.value.length <= schoolKeywordLimit && (!requireKeyword() || keyword.value.trim().length > 0) && !controlCharacters.test(keyword.value));
  const canPrevious = computed(() => enabled.value && !loading.value && result.value?.page > 1);
  const canNext = computed(() => enabled.value && !loading.value && !!result.value?.hasMore);

  function reset() {
    requests.invalidate();
    result.value = null;
    attempt.value = null;
    error.value = "";
    loading.value = false;
  }

  async function loadPage(page) {
    if (!enabled.value || loading.value || !Number.isInteger(page) || page < 1 || page > maxPage()) return false;
    if (!queryValid.value) {
      reset();
      error.value = "KEYWORD";
      return false;
    }
    const query = keyword.value.trim();
    const ticket = requests.begin();
    attempt.value = { page, keyword: query };
    loading.value = true;
    error.value = "";
    try {
      const data = await load(productId(), { page, keyword: query }, ticket.signal);
      if (!ticket.current()) return false;
      result.value = serviceSchoolPage(data, page, maxPage(), idMode());
      return true;
    } catch (failure) {
      if (ticket.current()) error.value = failure?.message === "INVALID_SCHOOL_PAGE" ? "RESPONSE" : "LOAD";
      return false;
    } finally {
      if (ticket.current()) loading.value = false;
    }
  }

  function search() {
    if (!enabled.value || loading.value) return Promise.resolve(false);
    result.value = null;
    return loadPage(1);
  }
  function previous() {
    return canPrevious.value ? loadPage(result.value.page - 1) : Promise.resolve(false);
  }
  function next() {
    return canNext.value ? loadPage(result.value.page + 1) : Promise.resolve(false);
  }
  function retry() {
    return error.value && error.value !== "KEYWORD" && attempt.value?.keyword === keyword.value.trim()
      ? loadPage(attempt.value.page) : Promise.resolve(false);
  }

  // Abort plus a generation check: transports may settle after cancellation.
  watch(keyword, reset, { flush: "sync" });
  watch([productId, maxPage, idMode, requireKeyword], () => { keyword.value = ""; reset(); }, { flush: "sync" });
  watch(active, () => { reset(); }, { flush: "sync" });
  onScopeDispose(() => { live.value = false; reset(); });

  return { keyword, result, attempt, error, loading, enabled, queryValid, canPrevious, canNext,
    search, previous, next, retry };
}
