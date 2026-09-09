import request from "@/utils/request";
const id = (value) => encodeURIComponent(value);
const get = async (url, params) => (await request.get(url, { params })).data;
const write = async (url, method, data) =>
  (await request({ url, method, data, __sessionRetry: true })).data;
export const clientCatalog = (params) =>
  get("/project-clients/catalog", params);
export const listProjectClients = (params) => get("/project-clients", params);
export const projectClient = (key) => get(`/project-clients/${id(key)}`);
export const clientStats = () => get("/project-clients/stats");
export const quoteProjectClient = (data) =>
  write("/project-clients/quotes", "post", data);
export const clientOperation = (key) =>
  get(`/project-client-operations/${id(key)}`);
export const clientOperationByRequest = (key) =>
  get(`/project-client-operations/by-request/${id(key)}`);
export const clientOperations = (params) =>
  get("/project-client-operations", params);
export const confirmProjectClient = (key) =>
  write(`/project-client-operations/${id(key)}/confirm`, "post", {
    consent: true,
  });
export const changeClientStatus = (key, data) =>
  write(`/project-clients/${id(key)}/status`, "post", data);
export const projectKeySettings = (subject) =>
  get(`/project-api-keys/${id(subject)}`);
export const issueProjectKey = (subject, data) =>
  write(`/project-api-keys/${id(subject)}`, "post", data);
export const revokeProjectKey = (subject, data) =>
  write(`/project-api-keys/${id(subject)}`, "delete", data);
export const projectApiCalls = (params) => get("/project-api-calls", params);
