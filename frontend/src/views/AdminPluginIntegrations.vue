<template>
  <main class="plugin-integrations-page" data-screen-label="插件集成">
    <header class="integration-heading">
      <div>
        <div class="eyebrow">服务与业务管理</div>
        <h1>插件集成</h1>
        <p>选择要使用的功能，完成配置和商品上架，再管理用户订单。</p>
      </div>
      <el-button @click="configureProvider()">接口配置 <el-icon><ArrowRight /></el-icon></el-button>
    </header>

    <section class="integration-summary" aria-label="功能概况">
      <div><span>功能总览</span><strong>{{ integrations.length }}</strong><small>按业务查看使用方式</small></div>
      <div><span>服务下单</span><strong>{{ serviceCount }}</strong><small>配置、上架与订单管理</small></div>
      <div><span>课程管理</span><strong>{{ countCapability('COURSE_CATALOG') }}</strong><small>课程导入与进度核对</small></div>
      <div><span>项目管理</span><strong>{{ countCapability('PROJECT_CENTER') }}</strong><small>项目、账户与工单</small></div>
    </section>

    <el-alert class="integration-notice" type="info" :closable="false" show-icon title="从配置到用户下单"
      description="保存并验证接口、核对商品价格并上架；只有已上架且可用的商品才会展示给用户。" />

    <section class="research-panel" aria-labelledby="research-heading">
      <div class="research-heading">
        <div><h2 id="research-heading">选择功能</h2><span class="muted">按业务配置与使用</span></div>
        <el-button :loading="loading" @click="loadIntegrations"><el-icon><Refresh /></el-icon>刷新清单</el-button>
      </div>
      <div class="integration-filters">
        <el-input v-model="keyword" placeholder="搜索插件或功能" clearable aria-label="搜索插件" :prefix-icon="Search" />
        <el-select v-model="status" placeholder="全部状态" clearable aria-label="筛选功能状态">
          <el-option v-for="(item, key) in integrationStatuses" :key="key" :label="item.label" :value="key" />
        </el-select>
        <span class="result-count">{{ filteredIntegrations.length }} 项功能</span>
      </div>
      <el-alert v-if="loadError" type="error" :closable="false" title="功能清单读取失败，请刷新重试" />
      <el-skeleton v-else-if="loading" :rows="8" animated class="list-skeleton" />
      <el-empty v-else-if="!filteredIntegrations.length" description="没有符合筛选条件的插件" />
      <div v-else class="plugin-list">
        <article v-for="plugin in filteredIntegrations" :key="plugin.id" class="plugin-row" :data-testid="`plugin-${plugin.id}`">
          <div class="plugin-identity">
            <span class="source-id">{{ plugin.id }}</span>
            <div><h3>{{ pluginWorkflow(plugin).name }}</h3><span class="muted">{{ plugin.category }}</span></div>
          </div>
          <div class="plugin-features">
            <p>{{ pluginWorkflow(plugin).summary }}</p>
            <div class="available-capabilities"><span class="field-label">已实现</span>
              <el-tag v-for="capability in pluginCapabilityNames(plugin).slice(0, 5)" :key="capability" size="small" effect="plain">{{ capability }}</el-tag>
              <span v-if="pluginCapabilityNames(plugin).length > 5" class="muted">另 {{ pluginCapabilityNames(plugin).length - 5 }} 项</span>
              <span v-if="!pluginCapabilityNames(plugin).length" class="muted">{{ plugin.duplicateOf ? '与鲸鱼运动服务共用配置' : '暂无可用操作' }}</span>
            </div>
          </div>
          <div class="plugin-decision">
            <el-tag :type="statusInfo(plugin.integrationStatus).tone" effect="light">{{ statusInfo(plugin.integrationStatus).label }}</el-tag>
            <div class="plugin-row-actions">
              <el-button link type="primary" @click="openPlugin(plugin, 'research')">功能说明</el-button>
              <el-button v-if="canConfigurePlugin(plugin)" type="primary" plain size="small" @click="openPlugin(plugin, 'setup')">配置与使用</el-button>
              <el-button v-else-if="pluginWorkflow(plugin).kind === 'project'" size="small" @click="openPlugin(plugin, 'setup')">项目管理</el-button>
              <el-button v-else-if="pluginWorkflow(plugin).kind === 'course'" size="small" @click="openPlugin(plugin, 'setup')">课程管理</el-button>
              <el-button v-else-if="pluginWorkflow(plugin).kind === 'shared'" size="small" @click="openShared(plugin)">前往鲸鱼服务</el-button>
            </div>
          </div>
        </article>
      </div>
    </section>

    <footer class="integration-footer">接口验证不会自动上架商品。请核对计费和服务说明后再向用户开放。</footer>

    <el-drawer v-model="drawerVisible" :title="selectedPlugin ? `${selectedWorkflow.name} · 使用与配置` : '使用与配置'"
      size="min(740px, 100vw)" class="plugin-integration-drawer" destroy-on-close>
      <template v-if="selectedPlugin">
        <div class="drawer-source"><span class="source-id">{{ selectedPlugin.id }}</span><span>{{ selectedWorkflow.summary }}</span></div>
        <el-tabs v-model="activeTab" class="integration-tabs">
          <el-tab-pane label="功能说明" name="research">
            <section class="evidence-section"><h3>已实现的操作</h3>
              <div class="available-capabilities"><el-tag v-for="name in pluginCapabilityNames(selectedPlugin)" :key="name" effect="plain">{{ name }}</el-tag></div>
              <p v-if="!pluginCapabilityNames(selectedPlugin).length" class="muted">{{ selectedPlugin.duplicateOf ? '请使用鲸鱼运动服务的配置和商品。' : '暂无可用操作。' }}</p>
            </section>
            <section class="evidence-section"><h3>使用前须知</h3><ul><li v-for="note in selectedWorkflow.notes" :key="note">{{ note }}</li></ul></section>
            <el-button v-if="selectedWorkflow.kind === 'shared'" type="primary" @click="openShared(selectedPlugin)">前往鲸鱼服务</el-button>
            <el-button v-else-if="selectedWorkflow.kind !== 'unavailable'" type="primary" @click="activeTab = 'setup'">配置与使用</el-button>
          </el-tab-pane>
          <el-tab-pane v-if="selectedWorkflow.kind !== 'unavailable' && selectedWorkflow.kind !== 'shared'" label="配置与使用" name="setup" />
          <el-tab-pane v-if="canReadIntegration(selectedPlugin)" label="商品 / 报价" name="catalog" />
          <el-tab-pane v-if="selectedPlugin.availableCapabilities.includes('SCHOOLS')" label="学校检索" name="schools" />
        </el-tabs>

        <section v-if="activeTab === 'setup' && selectedWorkflow.kind === 'course'" class="workflow-steps" aria-label="课程管理流程">
          <article class="workflow-step"><span class="step-number">1</span><div><h3>配置与导入课程</h3><p>保存课程接口，核对分类、课程价格和说明后导入。</p><div class="workflow-actions"><el-button @click="configureProvider('27')">配置课程接口</el-button><el-button type="primary" @click="navigate('/admin/platforms')">导入与管理课程</el-button></div></div></article>
          <article class="workflow-step"><span class="step-number">2</span><div><h3>购买与订单管理</h3><p>用户选择可购买的课程下单；管理员可核对执行进度和恢复执行编号。</p><div class="workflow-actions"><el-button @click="navigate('/courses')">查看课程</el-button><el-button @click="navigate('/admin/orders')">管理课程订单</el-button></div></div></article>
        </section>
        <section v-if="activeTab === 'setup' && selectedWorkflow.kind === 'project'" class="workflow-steps" aria-label="项目管理流程">
          <article class="workflow-step"><span class="step-number">1</span><div><h3>配置与发布项目</h3><p>保存并验证项目接口，核对项目价格后发布。</p><div class="workflow-actions"><el-button @click="configureProvider('syyv5')">配置项目接口</el-button><el-button type="primary" @click="navigate('/admin/service-projects')">发布与管理项目</el-button></div></div></article>
          <article class="workflow-step"><span class="step-number">2</span><div><h3>项目账户与客户额度</h3><p>查看已发布项目，管理账户、资金记录和工单。客户额度尚不能用于购买服务商品。</p><div class="workflow-actions"><el-button @click="navigate('/service-projects')">查看项目与账户</el-button><el-button @click="navigate('/project-clients')">管理客户额度</el-button></div></div></article>
        </section>

        <section v-if="activeTab !== 'research' && canConfigurePlugin(selectedPlugin)" class="read-workspace" aria-label="服务配置工作区">
          <el-alert type="info" :closable="false" title="使用已保存、验证并启用的同类型配置"
            description="选择配置不会自动查询商品、设置价格或提交订单。请核对各步骤后再操作。" />
          <div class="provider-selector">
            <div class="selector-heading"><label for="plugin-provider-filter">{{ activeTab === 'setup' ? '1. 选择服务配置' : '接口配置' }}</label><el-button link type="primary" @click="configureProvider(selectedPlugin.providerType)">添加 / 管理配置</el-button></div>
            <el-input id="plugin-provider-filter" v-model="providerKeyword" maxlength="80" placeholder="按配置名称筛选，回车查询" clearable @keyup.enter="loadProviders(1)" @clear="loadProviders(1)">
              <template #append><el-button :icon="Search" aria-label="筛选接口配置" :loading="providerLoading" @click="loadProviders(1)" /></template>
            </el-input>
            <el-select v-model="providerId" placeholder="选择已验证启用的接口" aria-label="选择接口配置" :loading="providerLoading" :disabled="providerLoading">
              <el-option v-for="provider in providerOptions" :key="provider.id" :label="providerOptionLabel(provider)" :value="provider.id" :disabled="!canReadProvider(provider)" />
            </el-select>
            <div class="provider-pagination"><el-button link :loading="providerLoading" @click="loadProviders(providerPage)">刷新配置</el-button>
              <el-pagination v-if="providerTotal > 20" :current-page="providerPage" :page-size="20" :total="providerTotal" :pager-count="5" layout="prev, pager, next" small @current-change="loadProviders" />
            </div>
            <el-alert v-if="providerError" type="error" :closable="false" :title="providerError.message" />
            <div v-else-if="!providerLoading && !providerOptions.length" class="empty-provider-state">
              <p>{{ providerKeyword ? '没有找到匹配的配置，请调整名称后重试。' : '尚未配置此服务。添加对应接口并完成验证、启用后，才能选择它上架商品。' }}</p>
              <el-button v-if="!providerKeyword" type="primary" plain @click="configureProvider(selectedPlugin.providerType)">添加此服务接口</el-button>
            </div>
            <p v-else-if="!providerLoading && !providerOptions.some(canReadProvider)" class="empty-provider-note">当前页配置均未验证启用，请先在接口配置页完成测试与启用。</p>
          </div>

          <section v-if="activeTab === 'setup' && canPublishIntegration(selectedPlugin)" class="workflow-steps" aria-label="服务使用流程">
            <article class="workflow-step"><span class="step-number">2</span><div><h3>核对并上架商品</h3><p>进入上架表单，选择商品并核对售价、计价单位与服务说明。不会自动保存或开放销售。</p>
              <el-select v-if="publicationProjects.length" v-model="project" aria-label="上架项目" placeholder="选择项目">
                <el-option v-for="option in publicationProjects" :key="option.id" :label="option.name" :value="option.id" />
              </el-select>
              <div class="workflow-actions"><el-button type="primary" :disabled="!publicationTarget" @click="navigate(publicationTarget)">上架此服务商品</el-button><el-button v-if="canReadIntegration(selectedPlugin)" @click="activeTab = 'catalog'">先查看商品报价</el-button></div>
            </div></article>
            <article class="workflow-step"><span class="step-number">3</span><div><h3>用户下单与订单管理</h3><p>服务商城只显示可购买的商品。下单后，可在订单中使用该服务支持的操作。</p><div class="workflow-actions"><el-button @click="navigate(serviceDestination(selectedPlugin.providerType))">查看服务商城</el-button><el-button @click="navigate(serviceDestination(selectedPlugin.providerType, true))">管理服务订单</el-button></div></div></article>
          </section>
          <el-alert v-else-if="activeTab === 'setup'" type="info" :closable="false" title="此功能仅支持目录查询，暂不能上架下单。" />

          <section v-if="activeTab === 'catalog'" class="catalog-workspace">
            <div class="query-controls">
              <el-select v-if="selectedPlugin.projects.length" v-model="project" aria-label="选择报价项目" placeholder="选择项目">
                <el-option v-for="option in selectedPlugin.projects" :key="option.id" :label="option.name" :value="option.id" />
              </el-select>
              <el-button type="primary" :disabled="!providerReady" :loading="catalogLoading" @click="loadCatalog">{{ selectedPlugin.providerType === 'flash' ? '读取项目报价' : '读取商品目录' }}</el-button>
            </div>
            <p class="query-disclaimer">只展示查询价格，不代表销售价格或可下单商品；不同项目的计价单位可能不同。</p>
            <el-alert v-if="catalogError" type="error" :closable="false" :title="catalogError.message" :description="catalogError.errorId ? `错误 ID：${catalogError.errorId}` : ''" />
            <el-skeleton v-else-if="catalogLoading" :rows="3" animated />
            <el-empty v-else-if="products === null" description="选择接口后，手动读取商品或项目报价" :image-size="76" />
            <el-empty v-else-if="!products.length" description="未返回商品目录" :image-size="76" />
            <div v-else class="quote-list" aria-live="polite">
              <div v-for="product in products" :key="product.id" class="quote-row">
                <div><strong>{{ product.name }}</strong><small>商品 ID · {{ product.id }}</small></div>
                <div class="quote-price"><strong>¥{{ formatPluginPrice(product.unitPrice) }}</strong><small>{{ product.priceUnit }}</small></div>
              </div>
              <p class="muted">本次只读结果 · {{ products.length }} 项 · 未创建任何订单</p>
            </div>
          </section>

          <section v-else-if="activeTab === 'schools'" class="school-workspace">
            <div class="query-controls school-controls">
              <el-input v-model="schoolKeyword" maxlength="80" placeholder="学校名称关键词，可留空" aria-label="学校关键词" clearable @keyup.enter="loadSchools(1, true)" />
              <el-button type="primary" :disabled="!providerReady" :loading="schoolLoading" @click="loadSchools(1, true)">查询学校</el-button>
            </div>
            <p class="query-disclaimer">每页最多 20 项，不自动翻页，不查询或提交学生信息。</p>
            <el-alert v-if="schoolError" type="error" :closable="false" :title="schoolError.message" :description="schoolError.errorId ? `错误 ID：${schoolError.errorId}` : ''" />
            <el-skeleton v-else-if="schoolLoading" :rows="4" animated />
            <el-empty v-else-if="schoolResult === null" description="输入关键词后手动查询学校" :image-size="76" />
            <template v-else>
              <div class="school-result-label">{{ appliedSchoolKeyword ? `检索：${appliedSchoolKeyword}` : '全部学校' }} · 第 {{ schoolResult.page }} 页</div>
              <el-empty v-if="!schoolResult.items.length" description="本页没有匹配学校" :image-size="76" />
              <ul v-else class="school-list" aria-live="polite"><li v-for="school in schoolResult.items" :key="school.id"><span>{{ school.name }}</span><small>{{ school.id }}</small></li></ul>
              <div class="school-pagination">
                <el-button :disabled="schoolResult.page <= 1 || schoolLoading" @click="loadSchools(schoolResult.page - 1)">上一页</el-button>
                <span class="muted">本页 {{ schoolResult.items.length }} 项</span>
                <el-button :disabled="!schoolResult.hasMore || schoolLoading" @click="loadSchools(schoolResult.page + 1)">下一页</el-button>
              </div>
            </template>
          </section>
        </section>
      </template>
    </el-drawer>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { ArrowRight, Refresh, Search } from "@element-plus/icons-vue";
import router from "@/router";
import { listPluginIntegrations, listPluginProviders, fetchPluginCatalog, searchPluginSchools } from "@/api/pluginIntegration";
import { integrationStatuses, statusInfo, canReadIntegration, canReadProvider,
  providerOptionLabel, filterIntegrations, formatPluginPrice, integrationError, latestRequest } from "@/utils/pluginIntegrations";

import { canPublishIntegration, pluginWorkflow, pluginCapabilityNames, validServiceProject,
  servicePublishTarget, serviceDestination, providerConfigurationType } from "@/utils/pluginWorkflows";

const canConfigurePlugin = (plugin) => canReadIntegration(plugin) || canPublishIntegration(plugin);
const integrations = ref([]), loading = ref(false), loadError = ref(false);
const keyword = ref(""), status = ref("");
const filteredIntegrations = computed(() => filterIntegrations(integrations.value.map((plugin) => ({
  ...plugin, name: pluginWorkflow(plugin).name, archive: "",
  observedFeatures: [pluginWorkflow(plugin).summary, ...pluginCapabilityNames(plugin)],
})), keyword.value, status.value));
const countCapability = (value) => integrations.value.filter((item) => item.availableCapabilities.includes(value)).length;
const serviceCount = computed(() => integrations.value.filter(canPublishIntegration).length);
const drawerVisible = ref(false), selectedPlugin = ref(null), activeTab = ref("research");
const providerOptions = ref([]), providerId = ref(null), providerLoading = ref(false), providerError = ref(null);
const providerKeyword = ref(""), providerPage = ref(1), providerTotal = ref(0), providersInitialized = ref(false);
const project = ref("");
const selectedProvider = computed(() => providerOptions.value.find((item) => item.id === providerId.value));
const providerReady = computed(() => canReadProvider(selectedProvider.value));
const selectedWorkflow = computed(() => pluginWorkflow(selectedPlugin.value));
const publicationProjects = computed(() => (selectedPlugin.value?.projects || []).filter((option) => validServiceProject(selectedPlugin.value.providerType, option.id)));
const publicationTarget = computed(() => servicePublishTarget(selectedPlugin.value, selectedProvider.value, project.value));
const products = ref(null), catalogLoading = ref(false), catalogError = ref(null);
const schoolKeyword = ref(""), appliedSchoolKeyword = ref(""), schoolResult = ref(null), schoolLoading = ref(false), schoolError = ref(null);
const listRequests = latestRequest(), providerRequests = latestRequest(), catalogRequests = latestRequest(), schoolRequests = latestRequest();

async function loadIntegrations() {
  const task = listRequests.begin(); loading.value = true; loadError.value = false;
  try {
    const response = await listPluginIntegrations(task.signal);
    if (task.current()) { integrations.value = response.data; openRequestedPlugin(); }
  } catch {
    if (task.current()) { loadError.value = true; integrations.value = []; }
  } finally { if (task.current()) loading.value = false; }
}
function clearReadResults() {
  catalogRequests.invalidate(); schoolRequests.invalidate();
  products.value = null; catalogError.value = null; catalogLoading.value = false;
  schoolResult.value = null; schoolError.value = null; schoolLoading.value = false;
  appliedSchoolKeyword.value = "";
}
function openPlugin(plugin, tab) {
  providerRequests.invalidate(); clearReadResults();
  selectedPlugin.value = plugin; providerOptions.value = []; providerId.value = null;
  providerKeyword.value = ""; providerPage.value = 1; providerTotal.value = 0; providerError.value = null;
  providersInitialized.value = false; providerLoading.value = false; project.value = plugin.projects[0]?.id || ""; schoolKeyword.value = "";
  activeTab.value = tab; drawerVisible.value = true;
  if (tab !== "research") loadProviders(1);
}
let openedRoutePlugin;
function openRequestedPlugin() {
  if (router.currentRoute.value.path && router.currentRoute.value.path !== "/admin/plugin-integrations") return;
  const id = router.currentRoute.value.query.plugin;
  const plugin = integrations.value.find((item) => item.id === id);
  if (plugin && id !== openedRoutePlugin) {
    openedRoutePlugin = id;
    openPlugin(plugin, pluginWorkflow(plugin).kind === "shared" ? "research" : "setup");
  }
}
watch(() => router.currentRoute.value.query.plugin, () => { openedRoutePlugin = undefined; openRequestedPlugin(); });
function navigate(target) {
  if (!target) return;
  drawerVisible.value = false;
  router.push(target);
}
function openShared(plugin) {
  const canonical = integrations.value.find((item) => item.id === plugin.duplicateOf);
  if (canonical && canConfigurePlugin(canonical)) openPlugin(canonical, "setup");
}
function configureProvider(type) {
  const preset = providerConfigurationType(type);
  navigate({ path: "/admin/api-providers", query: preset ? { type: preset } : {} });
}
async function loadProviders(page = 1) {
  const plugin = selectedPlugin.value;
  if (!drawerVisible.value || !canConfigurePlugin(plugin)) return;
  providersInitialized.value = true;
  const task = providerRequests.begin(); providerLoading.value = true; providerError.value = null;
  providerId.value = null; providerOptions.value = []; providerTotal.value = 0; providerPage.value = page;
  clearReadResults();
  try {
    const response = await listPluginProviders(plugin.id, { page, pageSize: 20, keyword: providerKeyword.value }, task.signal);
    if (task.current()) { providerOptions.value = response.data.records; providerTotal.value = response.data.total; }
  } catch (error) { if (task.current()) providerError.value = integrationError(error); }
  finally { if (task.current()) providerLoading.value = false; }
}
async function loadCatalog() {
  if (!providerReady.value || !canReadIntegration(selectedPlugin.value) || catalogLoading.value) return;
  const task = catalogRequests.begin(); catalogLoading.value = true; products.value = null; catalogError.value = null;
  try {
    const response = await fetchPluginCatalog(selectedPlugin.value.id, providerId.value, project.value, task.signal);
    if (task.current()) products.value = response.data;
  } catch (error) { if (task.current()) catalogError.value = integrationError(error); }
  finally { if (task.current()) catalogLoading.value = false; }
}
async function loadSchools(page = 1, newSearch = false) {
  if (!providerReady.value || !selectedPlugin.value?.availableCapabilities.includes("SCHOOLS") || schoolLoading.value) return;
  if (newSearch) appliedSchoolKeyword.value = schoolKeyword.value.trim();
  const task = schoolRequests.begin(); schoolLoading.value = true; schoolResult.value = null; schoolError.value = null;
  try {
    const response = await searchPluginSchools(selectedPlugin.value.id, providerId.value,
      { page, pageSize: 20, keyword: appliedSchoolKeyword.value }, task.signal);
    if (task.current()) schoolResult.value = response.data;
  } catch (error) { if (task.current()) schoolError.value = integrationError(error); }
  finally { if (task.current()) schoolLoading.value = false; }
}
watch([providerId, project], clearReadResults);
watch(activeTab, (tab) => { if (tab !== "research" && !providersInitialized.value) loadProviders(1); });
watch(drawerVisible, (visible) => { if (!visible) { providerRequests.invalidate(); providerLoading.value = false; clearReadResults(); } });
onMounted(loadIntegrations);
onBeforeUnmount(() => { listRequests.invalidate(); providerRequests.invalidate(); clearReadResults(); });
</script>

<style scoped>
.plugin-integrations-page { color: var(--text-primary, #17202b); display: grid; gap: 20px; min-width: 0; }
.integration-heading, .research-heading, .selector-heading { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.eyebrow { color: var(--brand-primary, #0f6cbd); font-size: 12px; font-weight: 600; letter-spacing: .05em; }
h1 { font-size: 28px; margin: 6px 0 8px; letter-spacing: -.02em; }
.integration-heading p, .integration-footer { margin: 0; color: var(--text-secondary, #5c6675); font-size: 13px; line-height: 1.7; }
.integration-summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.integration-summary > div { background: var(--bg-card, #fff); border: 1px solid var(--border-color-light, #e1e6ed); border-radius: 12px; padding: 18px 20px; display: grid; gap: 4px; }
.integration-summary span, .integration-summary small { color: var(--text-secondary, #5c6675); font-size: 12px; }
.integration-summary strong { font-size: 30px; line-height: 1.3; font-variant-numeric: tabular-nums; }
.research-panel { min-width: 0; background: var(--bg-card, #fff); border: 1px solid var(--border-color-light, #e1e6ed); border-radius: 16px; overflow: hidden; }
.research-heading { padding: 20px 24px 12px; }
.research-heading h2 { margin: 0 0 5px; font-size: 17px; }
.muted { color: var(--text-secondary, #5c6675); font-size: 12px; line-height: 1.6; }
.integration-filters { display: flex; gap: 12px; align-items: center; padding: 8px 24px 20px; }
.integration-filters .el-input { max-width: 360px; }
.integration-filters .el-select { width: 170px; flex-shrink: 0; }
.result-count { margin-left: auto; color: var(--text-secondary, #5c6675); font-size: 12px; white-space: nowrap; }
.list-skeleton { padding: 24px; }
.plugin-row { padding: 20px 24px; display: grid; grid-template-columns: minmax(200px, 1fr) minmax(250px, 1.35fr) 156px; gap: 24px; border-top: 1px solid var(--border-color-light, #e1e6ed); align-items: center; }
.plugin-row:hover { background: var(--bg-card-hover, #f7faff); }
.plugin-identity { display: flex; align-items: flex-start; gap: 12px; min-width: 0; }
.plugin-identity > div { min-width: 0; }
.source-id { display: inline-flex; justify-content: center; align-items: center; background: var(--surface-mica, #edf4fb); color: var(--brand-primary, #0f6cbd); border: 1px solid var(--border-color-light, #e1e6ed); border-radius: 8px; font: 600 12px ui-monospace, monospace; width: 38px; height: 38px; flex-shrink: 0; }
.plugin-identity h3 { font-size: 15px; margin: 1px 0 5px; }
.archive-name { margin: 0 0 5px; font-size: 12px; color: var(--text-secondary, #5c6675); overflow-wrap: anywhere; }
.plugin-features { min-width: 0; font-size: 12px; }
.plugin-features p { color: var(--text-regular, #354052); margin: 0 0 8px; line-height: 1.75; }
.field-label { color: var(--text-secondary, #5c6675); margin-right: 8px; white-space: nowrap; }
.available-capabilities { display: flex; flex-wrap: wrap; align-items: center; gap: 5px; }
.plugin-decision { display: grid; justify-items: end; gap: 12px; }
.plugin-row-actions { display: flex; gap: 10px; align-items: center; }
.plugin-row-actions .el-button + .el-button { margin-left: 0; }
.integration-footer { text-align: center; padding: 0 12px 8px; }
.drawer-source { display: flex; align-items: center; gap: 10px; font-size: 13px; color: var(--text-secondary, #5c6675); overflow-wrap: anywhere; margin-bottom: 12px; }
.drawer-source .source-id { width: 32px; height: 32px; }
.evidence-section { margin: 12px 0 24px; }
.evidence-section h3 { font-size: 15px; margin: 0 0 12px; }
.evidence-section ul { padding-left: 20px; margin: 0; }
.evidence-section li { line-height: 1.8; margin: 6px 0; color: var(--text-regular, #354052); font-size: 13px; overflow-wrap: anywhere; }

.read-workspace { display: grid; gap: 20px; }
.provider-selector { display: grid; gap: 10px; }
.selector-heading label { font-weight: 600; font-size: 14px; }
.provider-selector .el-select { width: 100%; }
.provider-pagination { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-wrap: wrap; }
.empty-provider-note { margin: 0; color: var(--color-warning, #a85400); font-size: 13px; line-height: 1.6; }
.query-controls { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.query-controls .el-select { flex: 1; min-width: 180px; }
.school-controls { flex-wrap: nowrap; }
.school-controls .el-input { flex: 1; min-width: 0; }
.query-disclaimer { color: var(--text-secondary, #5c6675); font-size: 12px; line-height: 1.7; margin: 12px 0 16px; }
.quote-list { border-top: 1px solid var(--border-color-light, #e1e6ed); }
.quote-row { display: grid; grid-template-columns: minmax(0, 1fr) minmax(120px, auto); gap: 16px; align-items: center; padding: 18px 0; border-bottom: 1px solid var(--border-color-light, #e1e6ed); }
.quote-row strong { font-size: 14px; overflow-wrap: anywhere; }
.quote-row small { display: block; color: var(--text-secondary, #5c6675); font-size: 12px; margin-top: 5px; }
.quote-price { text-align: right; }
.quote-price strong { color: var(--brand-primary, #0f6cbd); font-size: 20px; font-variant-numeric: tabular-nums; }
.school-result-label { margin: 8px 0; font-size: 13px; overflow-wrap: anywhere; }
.school-list { padding: 0; list-style: none; }
.school-list li { display: flex; justify-content: space-between; gap: 16px; padding: 12px 0; border-bottom: 1px solid var(--border-color-light, #e1e6ed); font-size: 14px; overflow-wrap: anywhere; }
.school-list small { color: var(--text-secondary, #5c6675); flex-shrink: 0; }
.school-pagination { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: 16px; }
@media (max-width: 1100px) { .plugin-row { grid-template-columns: minmax(190px, 1fr) minmax(200px, 1.3fr); gap: 14px; } .plugin-decision { grid-column: 1 / -1; display: flex; justify-content: space-between; } }
@media (max-width: 640px) {
  .plugin-integrations-page { gap: 14px; }
  .integration-heading { align-items: flex-start; flex-wrap: wrap; gap: 12px; }
  h1 { font-size: 24px; }
  .integration-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
  .integration-summary > div { padding: 14px; }
  .integration-summary strong { font-size: 27px; }
  .research-heading { padding: 16px; align-items: flex-start; }
  .research-heading > div { min-width: 0; }
  .research-heading > .el-button { flex-shrink: 0; }
  .integration-filters { padding: 0 16px 16px; gap: 8px; flex-wrap: wrap; }
  .integration-filters .el-input { max-width: none; width: 100%; }
  .integration-filters .el-select { width: 170px; }
  .plugin-row { grid-template-columns: minmax(0, 1fr); padding: 16px; gap: 12px; }
  .plugin-decision { grid-column: auto; flex-wrap: wrap; gap: 12px; }
  .quote-row { gap: 10px; }
  .quote-price strong { font-size: 18px; }
  .query-controls .el-select { width: 100%; flex-basis: 100%; }
}
.workflow-steps { display: grid; gap: 16px; }
.workflow-step { display: flex; align-items: flex-start; gap: 12px; border: 1px solid var(--border-color-light, #e1e6ed); border-radius: 12px; padding: 18px; }
.workflow-step > div { flex: 1; min-width: 0; }
.step-number { display: grid; place-items: center; width: 28px; height: 28px; flex-shrink: 0; border-radius: 50%; color: var(--brand-primary, #0f6cbd); background: var(--surface-mica, #edf4fb); font-weight: 600; }
.workflow-step h3 { margin: 3px 0 8px; font-size: 15px; }
.workflow-step p, .empty-provider-state p { margin: 0 0 12px; color: var(--text-secondary, #5c6675); font-size: 13px; line-height: 1.8; }
.workflow-actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 12px; }
.workflow-actions .el-button + .el-button { margin-left: 0; }
.empty-provider-state { border: 1px dashed var(--border-color-light, #e1e6ed); border-radius: 10px; padding: 16px; }
@media (max-width: 640px) { .workflow-step { padding: 14px; gap: 10px; } .selector-heading { flex-wrap: wrap; gap: 8px; } .workflow-actions > .el-button { max-width: 100%; } }
</style>
