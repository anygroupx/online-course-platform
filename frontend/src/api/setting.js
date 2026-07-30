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

/**
 * 批量更新系统设置
 * @param {Object} configs 配置键值
 * @return {Promise}
 */
export function updateSettings(configs) {
  return request({
    url: "/system/config",
    method: "put",
    data: configs,
  });
}

/**
 * 重置单个配置为默认值
 * @param {string} configKey 配置键
 * @return {Promise}
 */
export function resetSetting(configKey) {
  return request({
    url: `/system/config/reset/${encodeURIComponent(configKey)}`,
    method: "post",
  });
}

/**
 * 重置全部系统配置为默认值
 * @return {Promise}
 */
export function resetAllSettings() {
  return request({
    url: "/system/config/reset-all",
    method: "post",
  });
}
