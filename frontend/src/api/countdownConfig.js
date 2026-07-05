import request from "@/utils/request";

/**
 * 获取倒计时配置
 */
export function getCountdownConfigs() {
  return request({
    url: "/admin/countdown-config/all",
    method: "get",
  });
}

/**
 * 批量更新倒计时配置
 */
export function updateCountdownConfigs(configs) {
  return request({
    url: "/admin/countdown-config/batch-update",
    method: "post",
    data: configs,
  });
}

/**
 * 获取列配置
 */
export function getColumnConfig() {
  return request({
    url: "/admin/countdown-config/column-config",
    method: "get",
  });
}

/**
 * 保存列配置
 */
export function saveColumnConfig(config) {
  return request({
    url: "/admin/countdown-config/column-config",
    method: "post",
    data: config,
  });
}

/**
 * 获取单个配置项
 */
export function getConfigByKey(key) {
  return request({
    url: `/admin/countdown-config/${key}`,
    method: "get",
  });
}

/**
 * 更新单个配置项
 */
export function updateConfigByKey(key, value) {
  return request({
    url: `/admin/countdown-config/${key}`,
    method: "put",
    data: { value },
  });
}

// ========== 考试倒计时配置相关接口 ==========

/**
 * 获取考试倒计时配置
 */
export function getExamCountdownConfigs() {
  return request({
    url: "/admin/countdown-config/exam-configs",
    method: "get",
  });
}

/**
 * 更新考试倒计时配置
 */
export function updateExamCountdownConfigs(configs) {
  return request({
    url: "/admin/countdown-config/exam-configs",
    method: "post",
    data: configs,
  });
}