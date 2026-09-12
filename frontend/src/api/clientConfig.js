/**
 * 客户端公开启动配置。
 */
import request from "@/utils/request";

export function getClientBootstrap() {
  return request({
    url: "/client/bootstrap",
    method: "get",
    suppressGlobalError: true,
  });
}
