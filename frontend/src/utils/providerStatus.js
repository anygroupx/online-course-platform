export const PROVIDER_STATUS = Object.freeze({ DISABLED: 0, ACTIVE: 1, PENDING: 2 });

export function providerStatusLabel(provider) {
  if (provider.status === PROVIDER_STATUS.ACTIVE) return "已启用";
  if (provider.status === PROVIDER_STATUS.DISABLED) return "已禁用";
  return provider.verifiedAt && provider.lastCheckReason === "SUCCESS" ? "待启用" : "待验证";
}

export function canEnableProvider(provider) {
  return [PROVIDER_STATUS.DISABLED, PROVIDER_STATUS.PENDING].includes(provider.status)
    && Boolean(provider.verifiedAt)
    && provider.lastCheckReason === "SUCCESS";
}

export const providerCheckMessages = Object.freeze({
  SUCCESS: "连接正常",
  BLOCKED_DESTINATION: "地址未通过安全检查",
  PRIVATE_ADDRESS: "域名解析到非公网地址",
  PROVIDER_NOT_ACTIVE: "尚未启用或需要重新验证",
  DNS_FAILURE: "域名解析失败",
  REDIRECT_BLOCKED: "第三方重定向被阻止",
  TIMEOUT: "连接或响应超时",
  RESPONSE_TOO_LARGE: "响应超过大小限制",
  NETWORK_FAILURE: "网络连接失败",
  TLS_FAILURE: "TLS 安全连接校验失败",
  HTTP_ERROR: "上游 HTTP 状态异常",
  INVALID_RESPONSE: "响应格式错误",
  UPSTREAM_REJECTED: "请求被拒绝，请检查凭据和授权",
  UNSUPPORTED_OPERATION: "暂不支持只读连接测试",
});

export function providerCheckLabel(reason) {
  return Object.hasOwn(providerCheckMessages, reason) ? providerCheckMessages[reason] : "尚未检查";
}
