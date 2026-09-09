import request from "@/utils/request";

const root = "/admin/plugin-integrations";
const pluginPath = (id) => `${root}/${encodeURIComponent(id)}`;
const providerPath = (pluginId, providerId) => `${pluginPath(pluginId)}/providers/${encodeURIComponent(providerId)}`;

export const listPluginIntegrations = (signal) => request.get(root, { signal });
export const listPluginProviders = (pluginId, params, signal) => request.get(`${pluginPath(pluginId)}/providers`, { params, signal });
export const fetchPluginCatalog = (pluginId, providerId, project, signal) => request.get(
  `${providerPath(pluginId, providerId)}/catalog`, { params: project ? { project } : {}, signal, timeout: 65000 });
export const searchPluginSchools = (pluginId, providerId, params, signal) => request.get(
  `${providerPath(pluginId, providerId)}/schools`, { params, signal, timeout: 65000 });
