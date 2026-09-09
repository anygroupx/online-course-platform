import { serviceAccountExpired } from "./serviceCommerce.js";
export const ticketTypes = Object.freeze({
  suggestion: "功能建议",
  bug: "问题反馈",
  compensation: "补偿申请",
});
export const ticketStatuses = Object.freeze({
  pending: "待处理",
  processing: "处理中",
  resolved: "已解决",
  closed: "已关闭",
});
export const ticketActions = Object.freeze({
  SUBMIT: "发送新工单",
  REPLY: "发送回复",
  REVIEW: "提交补偿审核",
});
export const ticketState = (ticket) =>
  ({
    DRAFT: "草稿待确认",
    UNKNOWN: "操作结果待核实",
    CANCELLED: "已确认未受理",
    EXPIRED: "草稿已过期",
  })[ticket?.state] ||
  ticketStatuses[ticket?.status] ||
  "等待回执";
export const canReplyToTicket = (ticket) =>
  ticket?.state === "ACTIVE" && !ticket.pendingOperationId && !["resolved", "closed"].includes(ticket.status);
export const canReviewTicket = (ticket) =>
  canReplyToTicket(ticket) &&
  ticket.type === "compensation" &&
  !ticket.reviewResult;
export const ticketOperationReady = (
  operation,
  admin = false,
  now = Date.now(),
) =>
  operation?.state === "READY" &&
  (admin
    ? operation.action === "REVIEW"
    : ["SUBMIT", "REPLY"].includes(operation.action)) &&
  !serviceAccountExpired(operation, now);
export const canResolveTicketAccepted = (operation) =>
  operation?.state === "UNKNOWN" &&
  ["REPLY", "REVIEW"].includes(operation.action);
