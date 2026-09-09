import { providerCheckLabel } from "./providerStatus.js";

export const readOnlyProviderTypes = Object.freeze(["flash", "heisha", "jiguang", "wuxin", "sxdk_tw", "syyv5"]);
export const isReadOnlyProviderType = (type) => readOnlyProviderTypes.includes(type);
export const integrationStatuses = Object.freeze({
  NATIVE_PARTIAL: { label: "服务能力（部分）", tone: "warning" },
  READ_ONLY: { label: "只读接入", tone: "success" },
  EXISTING: { label: "已有能力", tone: "primary" },
  PLANNED: { label: "待独立重写", tone: "warning" },
  NEEDS_PROTOCOL: { label: "待补协议", tone: "danger" },
  DUPLICATE: { label: "重复家族", tone: "info" },
});
export const evidenceLabels = Object.freeze({
  PLAINTEXT: "明文可审阅", MIXED: "部分核心不透明", OPAQUE: "核心不透明", DUPLICATE: "重复包变体",
});
export const capabilityLabels = Object.freeze({
  PROJECT_CENTER: "项目账户与子钱包",
  CATALOG: "商品 / 报价", SCHOOLS: "学校检索", COURSE_CATALOG: "课程与分类", BATCH_PROGRESS: "批量进度",
});
export const statusInfo = (status) => integrationStatuses[status] || { label: "未评估", tone: "info" };
export const canReadIntegration = (item) => ["READ_ONLY", "NATIVE_PARTIAL"].includes(item?.integrationStatus)
  && isReadOnlyProviderType(item.providerType) && item.availableCapabilities?.includes("CATALOG");
export const canReadProvider = (provider) => provider?.status === 1 && provider.verified === true;
export function providerOptionLabel(provider) {
  const suffix = provider.status === 0 ? "已禁用" : provider.status === 2 ? "待验证 / 待启用"
    : !provider.verified ? "未验证" : provider.status === 1 ? "已验证启用" : "不可用";
  return `${provider.name} · ${suffix}`;
}
export function filterIntegrations(items, keyword, status) {
  const term = String(keyword || "").trim().toLocaleLowerCase();
  return items.filter((item) => (!status || item.integrationStatus === status)
    && (!term || [item.id, item.name, item.archive, item.category, ...(item.observedFeatures || [])]
      .some((value) => String(value || "").toLocaleLowerCase().includes(term))));
}

/** Format without binary floating point or interpreting a quotation as a payable amount. */
export function formatPluginPrice(value) {
  const raw = String(value ?? "");
  if (!/^[0-9]{1,9}(?:\.[0-9]{1,6})?$/.test(raw)) return "—";
  const [integer, fraction = ""] = raw.split(".");
  const decimals = fraction.replace(/0+$/, "").padEnd(2, "0");
  return `${integer}.${decimals}`;
}

/** Unknown remote text is never rendered as an operational error explanation. */
export function integrationError(error) {
  const body = error?.response?.data;
  const reason = body?.data?.reason;
  const knownReasons = new Set(["BLOCKED_DESTINATION", "PRIVATE_ADDRESS", "PROVIDER_NOT_ACTIVE", "DNS_FAILURE",
    "REDIRECT_BLOCKED", "TIMEOUT", "RESPONSE_TOO_LARGE", "NETWORK_FAILURE", "TLS_FAILURE", "HTTP_ERROR",
    "INVALID_RESPONSE", "UPSTREAM_REJECTED", "UNSUPPORTED_OPERATION"]);
  return {
    message: knownReasons.has(reason) ? providerCheckLabel(reason) : "读取失败，请重试或检查接口配置",
    errorId: /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(body?.errorId || "") ? body.errorId : "",
  };
}

/** Cancellation stops browser waiting, not necessarily a server request already dispatched. */
export function latestRequest() {
  let sequence = 0;
  let controller;
  return {
    begin() {
      controller?.abort();
      controller = new AbortController();
      const currentController = controller;
      const token = ++sequence;
      return { signal: controller.signal, current: () => token === sequence && !currentController.signal.aborted };
    },
    invalidate() { ++sequence; controller?.abort(); },
  };
}
