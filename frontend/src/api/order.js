/**
 * 订单相关API
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
import request from "@/utils/request";

/**
 * 创建订单
 */
export function createOrder(data) {
  return request({
    url: "/orders",
    method: "post",
    data,
  });
}

/**
 * 查询订单列表
 */
export function queryOrders(data) {
  return request({
    url: "/orders/query",
    method: "post",
    data,
  });
}

/**
 * 获取订单详情
 */
export function getOrderDetail(orderNo) {
  return request({
    url: `/orders/${orderNo}`,
    method: "get",
  });
}

/**
 * 取消订单
 */
export function cancelOrder(orderNo) {
  return request({
    url: `/orders/${orderNo}/cancel`,
    method: "post",
  });
}

/**
 * 补单
 */
export function retryOrder(orderNo) {
  return request({
    url: `/orders/${orderNo}/retry`,
    method: "post",
  });
}

/**
 * 刷新订单进度
 */
export function refreshOrder(orderNo) {
  return request({
    url: `/orders/${orderNo}/refresh`,
    method: "post",
  });
}

// ========== 管理员订单管理接口 ==========

/**
 * 管理员查询所有订单
 */
export function queryAllOrders(data) {
  return request({
    url: "/admin/orders/query-all",
    method: "post",
    data,
  });
}

/**
 * 管理员删除订单
 */
export function deleteOrder(id, reason) {
  return request({
    url: `/admin/orders/${id}`,
    method: "delete",
    params: { reason },
  });
}

/**
 * 管理员批量删除订单
 */
export function batchDeleteOrders(orderIds, reason) {
  return request({
    url: "/admin/orders/batch-delete",
    method: "delete",
    data: orderIds,
    params: { reason },
  });
}

/**
 * 管理员切换自营订单状态
 */
export function toggleSelfOperatedOrderStatus(id, params) {
  return request({
    url: `/admin/orders/${id}/toggle-status`,
    method: "post",
    params,
  });
}

/**
 * 管理员调整倒计时
 */
export function adjustCountdown(id, data) {
  return request({
    url: `/admin/orders/${id}/adjust-countdown`,
    method: "post",
    data,
  });
}

/**
 * 获取正在倒计时的订单列表
 */
export function getActiveCountdownOrders() {
  return request({
    url: "/admin/orders/countdown",
    method: "get",
  });
}

/**
 * 获取订单剩余倒计时时间
 */
export function getRemainingCountdown(id) {
  return request({
    url: `/admin/orders/${id}/countdown-remaining`,
    method: "get",
  });
}

/**
 * 管理员手动完成订单
 */
export function completeOrder(id, data) {
  return request({
    url: `/admin/orders/${id}/complete`,
    method: "post",
    data,
  });
}

/**
 * 批量倒计时操作
 */
export function batchCountdownOperation(data) {
  return request({
    url: "/admin/orders/batch-countdown-operation",
    method: "post",
    data,
  });
}

/**
 * 获取订单倒计时历史记录
 */
export function getCountdownHistory(orderId) {
  return request({
    url: `/admin/orders/${orderId}/countdown-history`,
    method: "get",
  });
}

/**
 * 获取所有倒计时历史记录
 */
export function getAllCountdownHistory(params) {
  return request({
    url: "/admin/orders/countdown-history",
    method: "get",
    params,
  });
}

/**
 * 获取倒计时历史记录（包含账号和订单状态）
 */
export function getAllCountdownHistoryWithDetails(params) {
  return request({
    url: "/admin/orders/countdown-history-with-details",
    method: "get",
    params,
  });
}

/**
 * 开始下一步任务倒计时
 */
export function startNextTaskCountdown(orderId, data) {
  return request({
    url: `/admin/orders/${orderId}/start-next-task-countdown`,
    method: "post",
    data,
  });
}

// ========== 考试倒计时相关接口 ==========

/**
 * 获取正在考试倒计时的订单列表
 */
export function getActiveExamCountdownOrders() {
  return request({
    url: "/admin/orders/exam-countdown",
    method: "get",
  });
}

/**
 * 获取订单剩余考试倒计时时间
 */
export function getRemainingExamCountdown(id) {
  return request({
    url: `/admin/orders/${id}/exam-countdown-remaining`,
    method: "get",
  });
}

/**
 * 手动完成考试
 */
export function completeExam(id, data) {
  return request({
    url: `/admin/orders/${id}/complete-exam`,
    method: "post",
    data,
  });
}

/**
 * 开始考试倒计时
 */
export function startExamCountdown(orderId, data) {
  return request({
    url: `/admin/orders/${orderId}/start-exam-countdown`,
    method: "post",
    data,
  });
}

/**
 * 调整考试倒计时
 */
export function adjustExamCountdown(orderId, data) {
  return request({
    url: `/admin/orders/${orderId}/adjust-exam-countdown`,
    method: "post",
    data,
  });
}

/**
 * 导出订单
 */
export function exportOrders(data) {
  return request({
    url: "/admin/orders/export",
    method: "post",
    data,
    responseType:
      data && typeof data.fileType === "string" && data.fileType === "xlsx"
        ? "blob"
        : "json",
  });
}
