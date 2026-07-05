/**
 * 系统设置相关接口
 * @module api/setting
 */
import request from "@/utils/request";

/**
 * 获取系统设置
 * @return {Promise} 系统设置对象
 */
export function getSettings() {
  return request({
    url: "/system/config",
    method: "get",
  });
}
