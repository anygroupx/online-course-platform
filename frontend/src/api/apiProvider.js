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
