import request from "@/utils/request";
const root = "/admin/platforms/price-refreshes";
export const previewCatalogRefresh = async (data) =>
  (
    await request({
      url: root,
      method: "post",
      data,
      timeout: 120000,
      __sessionRetry: true,
    })
  ).data;
export const getCatalogRefresh = async (id) =>
  (await request.get(`${root}/${encodeURIComponent(id)}`)).data;
export const confirmCatalogRefresh = async (id) =>
  (
    await request({
      url: `${root}/${encodeURIComponent(id)}/confirm`,
      method: "post",
      data: { consent: true },
      __sessionRetry: true,
    })
  ).data;
