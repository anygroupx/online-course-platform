import request from "@/utils/request";
const key = (value) => encodeURIComponent(value);
const root = (id) => `/service-orders/${key(id)}/notifications`;
const read = async (url, params) => (await request.get(url, { params })).data;
const mutate = async (url, method, data) =>
  (await request({ url, method, data, timeout: 30000, __sessionRetry: true }))
    .data;
export const notificationSettings = (id) => read(root(id));
export const configureNotifications = (id, data) =>
  mutate(root(id), "put", data);
export const challengeNotifications = (id, data) =>
  mutate(`${root(id)}/challenge`, "post", data);
export const verifyNotifications = (id, data) =>
  mutate(`${root(id)}/verify`, "post", data);
export const disconnectNotifications = (id, data) =>
  mutate(root(id), "delete", data);
export const notificationDeliveries = (id, params) =>
  read(`${root(id)}/deliveries`, params);
export const notificationDelivery = (id) =>
  read(`/service-notification-deliveries/${key(id)}`);
