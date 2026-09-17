import { serviceNames, nativeProductSupported } from "./serviceCommerce.js";
import { canReadIntegration, canReadProvider, capabilityLabels, isReadOnlyProviderType } from "./pluginIntegrations.js";

const workflows = Object.freeze({
  P01: { name: "闪电运动服务", summary: "查询账号和跑区，安排每次任务，并管理暂停、延期和退款。", notes: ["部分项目需要本人完成短信验证。", "请核对计价单位和执行安排，规则变更后需要重新报价。"] },
  P02: { name: "Benz 课程管理", summary: "导入课程与分类，核对价格、说明、订单进度和执行编号。", kind: "course", notes: ["课程需要完成配置和导入后才能购买。", "恢复执行编号前必须核对账号与课程，不会重新下单。"] },
  P03: { name: "黑鲨运动服务", summary: "查询账号、选择计划和跑区，提交订单并核对执行状态。", notes: ["需要人脸验证的项目由本人在指定采集页面完成。", "退款需要核对实际剩余次数，不会仅凭请求失败退回余额。"] },
  P04: { name: "极光运动服务", summary: "选择学校与运动商品，按次数和公里数下单，查看记录或增加次数。", notes: ["请核对学校和学号，再确认次数、距离及总价。", "增加次数和退款均需先预览，再确认提交。"] },
  P05: { name: "公里计划", summary: "选择计费方案，按总公里数安排日期、时段和星期。", notes: ["订单只能核对提交状态，不提供完成进度或自动退款。", "提交成功不等于运动已完成，请核实方案说明后购买。"] },
  P06: { name: "实习计划", summary: "查询学校和账号，选择服务周期，管理执行安排、续期和记录。", notes: ["上架前需要核实合同单价和有效期。", "服务期结束不代表考勤完成；通知接收需要本人验证。"] },
  P07: { name: "多项目与账户", summary: "发布项目，管理项目账户、额度、资金记录和工单。", kind: "project", notes: ["项目账户与客户额度分别记录，客户额度尚不能用于购买服务商品。", "账户开户和资金操作需要核对金额并确认，不会因查看页面而执行。"] },
  P08: { name: "鲸鱼运动服务", summary: "使用 Keep、步道乐跑和校园运动，选择跑区或学校规则并安排逐次任务。", notes: ["校园运动需要从查询结果选择学校，并满足规则最低距离。", "体育项目的图形验证、规则更新和补跑功能尚未开放。", "退款须核对实际次数后入账；设备授权和推送不在此处办理。"] },
  P09: { name: "实习打卡", summary: "查询实习项目报价和账号，按天购买、续期，管理安排和执行日志。", notes: ["至少选择一种报告类型，并核对学校和账号。", "已用天数不代表签到成功，退款需核实确切结果。"] },
  P10: { name: "无心运动服务", summary: "查询授权码，按星期、时段和配速安排任务，管理增次、退款和计划调整。", notes: ["请使用本人有效授权码。", "不提供未经确认的重跑或单次改时功能。"] },
  P11: { name: "鲸鱼运动服务（共用配置）", summary: "与鲸鱼运动服务使用相同配置和商品，无需重复添加。", kind: "shared", notes: ["请从鲸鱼运动服务入口配置和上架。", "不会因选择此入口额外开放设备授权或推送功能。"] },
  P12: { name: "雷电运动服务", summary: "选择四类运动项目，核对跑区规则，管理执行安排、任务时间和成绩查询。", notes: ["使用次数不代表成绩完成。", "取消订单和退款分别核对，取消受理后不会自动退回余额。"] },
});
const serviceCapabilityNames = Object.freeze({
  LOOKUP: "账号查询", CREATE: "用户下单", SYNC: "状态核对", PAUSE: "暂停", RESUME: "恢复",
  DELAY: "整单延期", DELAY_TASK: "单次延期", CHANGE_TIME: "任务改时", REFUND: "退款申请",
  ADD_TIMES: "续期 / 增次", EDIT_PLAN: "调整计划", EDIT_SCHEDULE: "调整周期", RUN_NOW: "立即执行",
  REPORT: "补交记录", REASSIGN: "重新分配", CANCEL: "取消订单", SCORE_INFO: "成绩查询",
});
export const providerConfigurationType = (value) => typeof value === "string" &&
  (value === "27" || isReadOnlyProviderType(value)) ? value : "";
export const serviceTypeFromQuery = (value) => typeof value === "string" && Object.hasOwn(serviceNames, value) ? value : "";
const providerIdText = (value) => ["string", "number"].includes(typeof value) && /^[1-9]\d*$/.test(String(value)) &&
  Number.isSafeInteger(Number(value)) ? String(value) : "";
export function canPublishIntegration(plugin) {
  return Boolean(plugin && !plugin.duplicateOf && plugin.integrationStatus === "NATIVE_PARTIAL" &&
    serviceTypeFromQuery(plugin.providerType) && Array.isArray(plugin.serviceCapabilities) && plugin.serviceCapabilities.includes("CREATE"));
}
export function pluginWorkflow(plugin) {
  const known = Object.hasOwn(workflows, plugin?.id) ? workflows[plugin.id] : null;
  if (!known) return { name: "待开放功能", kind: "unavailable", summary: "此功能尚无可用的操作入口。", notes: ["请等待功能开放后再配置。"] };
  let kind = canPublishIntegration(plugin) ? "service" : canReadIntegration(plugin) ? "catalog" : "unavailable";
  if (known.kind === "course" && plugin.availableCapabilities?.includes("COURSE_CATALOG")) kind = "course";
  if (known.kind === "project" && plugin.availableCapabilities?.includes("PROJECT_CENTER")) kind = "project";
  if (known.kind === "shared" && plugin.integrationStatus === "DUPLICATE" && plugin.duplicateOf === "P08") kind = "shared";
  return { ...known, kind };
}
export function pluginCapabilityNames(plugin) {
  const native = canPublishIntegration(plugin) ? plugin.serviceCapabilities : [];
  return [...new Set([
    ...(Array.isArray(plugin?.availableCapabilities) ? plugin.availableCapabilities : []).map((name) => Object.hasOwn(capabilityLabels, name) ? capabilityLabels[name] : null),
    ...native.map((name) => Object.hasOwn(serviceCapabilityNames, name) ? serviceCapabilityNames[name] : null),
  ].filter(Boolean))];
}
export function validServiceProject(type, project) {
  if (!serviceTypeFromQuery(type) || typeof project !== "string" || !project) return false;
  const productId = type === "ssbenz_xbd" ? "0" : ["heisha", "jiguang"].includes(type) ? "1" : project;
  return nativeProductSupported(type, project, productId);
}
export function servicePublishTarget(plugin, provider, project = "") {
  if (!canPublishIntegration(plugin) || !canReadProvider(provider) || !providerIdText(provider.id) || typeof project !== "string") return null;
  if (provider.providerType !== plugin.providerType) return null;
  const query = { providerType: plugin.providerType, providerId: providerIdText(provider.id) };
  if (project) {
    if (!validServiceProject(plugin.providerType, project)) return null;
    query.project = project;
  }
  return { path: "/admin/service-products", query };
}
export function servicePublishRequest(query = {}) {
  if (!query || typeof query !== "object" || Array.isArray(query)) return null;
  const providerType = serviceTypeFromQuery(query.providerType), providerId = providerIdText(query.providerId);
  if (!providerType || !providerId) return null;
  if (query.project !== undefined && (typeof query.project !== "string" ||
      !validServiceProject(providerType, query.project))) return null;
  return { providerType, providerId, project: query.project || "" };
}
export function validPublicationProvider(provider, requested) {
  return Boolean(requested && provider && providerIdText(provider.id) &&
    providerIdText(provider.id) === requested.providerId && serviceTypeFromQuery(requested.providerType) &&
    provider.providerType === requested.providerType && provider.status === 1 &&
    typeof provider.verifiedAt === "string" && /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})?$/.test(provider.verifiedAt) &&
    Number.isFinite(Date.parse(provider.verifiedAt.replace(" ", "T"))));
}
export function serviceDestination(providerType, adminOrders = false) {
  const type = serviceTypeFromQuery(providerType);
  return type ? { path: adminOrders ? "/admin/service-orders" : "/services", query: { providerType: type } } : null;
}
