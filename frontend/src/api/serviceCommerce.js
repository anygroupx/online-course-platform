import request from "@/utils/request";
const id = (value) => encodeURIComponent(value);
const read = async (url, params, signal) =>
  (await request.get(url, { params, signal, timeout: 125000 })).data;
// Do not replay mutations after an HTTP 401; confirmation uses a durable server-issued operation ID.
const write = async (url, data, method = "post") =>
  (await request({ url, data, method, timeout: 125000, __sessionRetry: true }))
    .data;
export const listServiceProducts = (params, admin = false) =>
  read(admin ? "/admin/service-products" : "/services", params);
export const saveServiceProduct = (productId, data) =>
  write(
    `/admin/service-products${productId ? `/${id(productId)}` : ""}`,
    data,
    productId ? "put" : "post",
  );
export const lookupServiceAccount = (productId, fields) =>
  write(`/services/${id(productId)}/lookup`, fields);
export const findServiceSchools = (productId, params, signal) =>
  read(`/services/${id(productId)}/schools`, params, signal);
export const previewServiceOrder = (productId, data) =>
  write(`/services/${id(productId)}/quotes`, data);
export const previewServiceAction = (orderId, data) =>
  write(`/service-orders/${id(orderId)}/quotes`, data);
export const confirmServiceOperation = (operationId) =>
  write(`/service-order-operations/${id(operationId)}/confirm`, {});
export const getServiceOperation = (operationId) =>
  read(`/service-order-operations/${id(operationId)}`);
export const listServiceOrders = (params, admin = false, signal) =>
  read(admin ? "/admin/service-orders" : "/service-orders", params, signal);
export const syncServiceOrder = (orderId) =>
  write(`/service-orders/${id(orderId)}/sync`, {});
export const getServiceOrderEvents = (orderId) =>
  read(`/service-orders/${id(orderId)}/events`);
export const resolveServiceOperation = (operationId, data) =>
  write(`/admin/service-order-operations/${id(operationId)}/resolve`, data);

export const getAdminServiceOperation = (operationId) =>
  read(`/admin/service-order-operations/${id(operationId)}`);

export const getServiceRunLogs = (orderId, page, signal) =>
  read(`/service-orders/${id(orderId)}/logs`, { page }, signal);

export const getServiceScoreInfo = (orderId, signal) =>
  read(`/service-orders/${id(orderId)}/score-info`, undefined, signal);

export const getServiceOrderOptions = (orderId) =>
  read(`/service-orders/${id(orderId)}/options`);

export const getServiceOrderAudit = (orderId) =>
  read(`/admin/service-orders/${id(orderId)}/audit`);
export const previewServiceRefundSettlement = (orderId, data) =>
  write(`/admin/service-orders/${id(orderId)}/refund-quotes`, data);
export const confirmServiceRefundSettlement = (operationId) =>
  write(`/admin/service-order-operations/${id(operationId)}/settle-refund`, {});

export const createServiceAccountSession = (productId, data) =>
  write(`/services/${id(productId)}/account-sessions`, data);
export const sendServiceAccountCode = (sessionId) =>
  write(`/service-account-sessions/${id(sessionId)}/send-code`, {});
export const verifyServiceAccount = (sessionId, data) =>
  write(`/service-account-sessions/${id(sessionId)}/verify`, data);
export const getServiceAccountSession = (sessionId) =>
  read(`/service-account-sessions/${id(sessionId)}`);
export const refreshServiceAccountRules = (sessionId) =>
  write(`/service-account-sessions/${id(sessionId)}/refresh-rules`, {});
export const revokeServiceAccountSession = (sessionId) =>
  write(`/service-account-sessions/${id(sessionId)}`, {}, "delete");

export const collectServiceAccountFace = (sessionId, data) =>
  write(`/service-account-sessions/${id(sessionId)}/face-collection`, data);
export const checkServiceAccountFace = (sessionId) =>
  write(`/service-account-sessions/${id(sessionId)}/face-check`, {});
export const issueServiceFaceLaunch = (sessionId) =>
  write(`/service-account-sessions/${id(sessionId)}/face-launch`, {});
