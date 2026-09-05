import request from "@/utils/request";

/**
 * 查询第三方接口实时余额，并将结果保存到本地。
 */
export function refreshApiProviderBalance(id) {
  return request({
    url: `/admin/api-providers/${id}/balance`,
    method: "post",
  });
}

/** Test saved credentials with a read-only balance/catalog operation; never auto-enables. */
export function testApiProviderConnection(id) {
  return request({
    url: `/admin/api-providers/${id}/test-connection`,
    method: "post",
    // DNS and the bounded HTTP operation both run before the response.
    timeout: 65000,
  });
}

export function updateApiProviderStatus(id, status) {
  return request({
    url: `/admin/api-providers/${id}/status`,
    method: "patch",
    data: { status },
  });
}
