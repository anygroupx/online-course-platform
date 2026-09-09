import request from "@/utils/request";
const id = (value) => encodeURIComponent(value);
const read = async (url, params) =>
  (await request.get(url, { params, timeout: 125000 })).data;
const write = async (url, data = {}, method = "post") =>
  (await request({ url, data, method, timeout: 125000, __sessionRetry: true }))
    .data;
export const listProjects = (params, admin = false) =>
  read(admin ? "/admin/service-projects" : "/service-projects", params);
export const projectCatalog = (providerId) =>
  read("/admin/service-project-catalog", { providerId });
export const saveProject = (projectId, data) =>
  write(
    `/admin/service-projects${projectId ? `/${id(projectId)}` : ""}`,
    data,
    projectId ? "put" : "post",
  );
export const refreshProjectAccount = (accountId) =>
  write(`/project-accounts/${id(accountId)}/refresh`);
export const quoteProject = (projectId, data) =>
  write(`/service-projects/${id(projectId)}/quotes`, data);
export const confirmProjectOperation = (operationId) =>
  write(`/project-operations/${id(operationId)}/confirm`);
export const getProjectOperation = (operationId, admin = false) =>
  read(`${admin ? "/admin" : ""}/project-operations/${id(operationId)}`);
export const listProjectOperations = (params, admin = false) =>
  read(admin ? "/admin/project-operations" : "/project-operations", params);
export const resolveProjectOperation = (operationId, data) =>
  write(`/admin/project-operations/${id(operationId)}/resolve`, data);
