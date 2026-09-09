<template>
  <main class="plugin-integrations-page" data-screen-label="插件集成">
    <header class="integration-heading">
      <div>
        <div class="eyebrow">扩展能力 / 静态调研与原生接入</div>
        <h1>插件集成</h1>
        <p>把可验证的能力带入应用，而不是把旧脚本装进系统。</p>
      </div>
      <el-button @click="configureProvider()">接口配置 <el-icon><ArrowRight /></el-icon></el-button>
    </header>

    <section class="integration-summary" aria-label="集成概况">
      <div><span>已审阅压缩包</span><strong>{{ integrations.length }}</strong><small>11 ZIP + 1 tar.gz</small></div>
      <div><span>只读连接器</span><strong>{{ readOnlyCount }}</strong><small>报价 / 商品 / 学校</small></div>
      <div><span>已有能力</span><strong>{{ countStatus('EXISTING') }}</strong><small>复用当前课程同步</small></div>
      <div><span>待补协议</span><strong>{{ countStatus('NEEDS_PROTOCOL') }}</strong><small>不猜测不透明核心</small></div>
    </section>

    <el-alert class="integration-notice" type="info" :closable="false" show-icon
      title="此页仅用于接口检查。用户下单请在“服务商品”上架，通过“服务商城 / 服务订单”使用原生业务功能。"
      description="连接测试成功只代表模板入口可读，不代表真实上游全功能已验收。观察到的按钮不等于已实现能力。" />

    <section class="research-panel" aria-labelledby="research-heading">
      <div class="research-heading">
        <div><h2 id="research-heading">来源与接入评估</h2><span class="muted">2026-09-06 静态审阅 · 192 个文件 · 鲸鱼两包 24/25 文件一致</span></div>
        <el-button :loading="loading" @click="loadIntegrations"><el-icon><Refresh /></el-icon>刷新清单</el-button>
      </div>
      <div class="integration-filters">
        <el-input v-model="keyword" placeholder="搜索插件、文件名或能力" clearable aria-label="搜索插件" :prefix-icon="Search" />
        <el-select v-model="status" placeholder="全部状态" clearable aria-label="筛选接入状态">
          <el-option v-for="(item, key) in integrationStatuses" :key="key" :label="item.label" :value="key" />
        </el-select>
        <span class="result-count">{{ filteredIntegrations.length }} 个来源</span>
      </div>
      <el-alert v-if="loadError" type="error" :closable="false" title="研究清单读取失败，请刷新重试" />
      <el-skeleton v-else-if="loading" :rows="8" animated class="list-skeleton" />
      <el-empty v-else-if="!filteredIntegrations.length" description="没有符合筛选条件的插件" />
      <div v-else class="plugin-list">
        <article v-for="plugin in filteredIntegrations" :key="plugin.id" class="plugin-row" :data-testid="`plugin-${plugin.id}`">
          <div class="plugin-identity">
            <span class="source-id">{{ plugin.id }}</span>
            <div><h3>{{ plugin.name }}</h3><p class="archive-name">{{ plugin.archive }}</p><span class="muted">{{ plugin.category }} · {{ evidenceLabels[plugin.evidenceLevel] || '待审阅' }}</span></div>
          </div>
          <div class="plugin-features">
            <p><span class="field-label">已观察</span>{{ plugin.observedFeatures.join(' / ') }}</p>
            <div class="available-capabilities"><span class="field-label">已实现</span>
              <el-tag v-for="capability in plugin.availableCapabilities" :key="capability" size="small" effect="plain">{{ capabilityLabels[capability] || '未知能力' }}</el-tag>
              <span v-if="!plugin.availableCapabilities.length" class="muted">尚未接入{{ plugin.duplicateOf ? ` · 同 ${plugin.duplicateOf} 家族` : '' }}</span>
            </div>
          </div>
          <div class="plugin-decision">
            <el-tag :type="statusInfo(plugin.integrationStatus).tone" effect="light">{{ statusInfo(plugin.integrationStatus).label }}</el-tag>
            <div class="plugin-row-actions">
              <el-button link type="primary" @click="openPlugin(plugin, 'research')">查看证据</el-button>
              <el-button v-if="canReadIntegration(plugin)" type="primary" plain size="small" @click="openPlugin(plugin, 'catalog')">只读接入</el-button>
              <el-button v-else-if="plugin.availableCapabilities.includes('PROJECT_CENTER')" size="small" @click="router.push('/admin/service-projects')">项目中心</el-button>
              <el-button v-else-if="plugin.integrationStatus === 'EXISTING'" size="small" @click="configureProvider()">已有接口</el-button>
            </div>
          </div>
        </article>
      </div>
    </section>

    <footer class="integration-footer">原包仅作为静态参考。服务下单与售后使用独立的原生商城；本页只检查连接与目录。未完成的多项目钱包、工单和不透明协议不会因检查通过而自动开放。</footer>

    <el-drawer v-model="drawerVisible" :title="selectedPlugin ? `${selectedPlugin.name} · 集成详情` : '集成详情'"
      size="min(740px, 100vw)" class="plugin-integration-drawer" destroy-on-close>
      <template v-if="selectedPlugin">
        <div class="drawer-source"><span class="source-id">{{ selectedPlugin.id }}</span><span>{{ selectedPlugin.archive }}</span></div>
        <el-tabs v-model="activeTab" class="integration-tabs">
          <el-tab-pane label="调研证据" name="research">
            <section class="evidence-section"><h3>观察到的功能</h3><ul><li v-for="feature in selectedPlugin.observedFeatures" :key="feature">{{ feature }}</li></ul></section>
            <section class="evidence-section"><h3>限制与待补资料</h3><ul><li v-for="blocker in selectedPlugin.blockers" :key="blocker">{{ blocker }}</li></ul></section>
            <section class="evidence-section"><h3>来源定位（解压后行号）</h3><ul class="evidence-files"><li v-for="evidence in selectedPlugin.evidence" :key="evidence">{{ evidence }}</li></ul></section>
            <el-alert type="warning" :closable="false" title="静态证据，不是实网可用性证明"
              description="不透明代码没有被执行或破解；依赖库许可证不代表插件再分发授权。本期没有访问包内硬编码域名。" />
            <el-button v-if="canReadIntegration(selectedPlugin)" type="primary" class="evidence-read-button" @click="activeTab = 'catalog'">查看已实现的只读能力</el-button>
          </el-tab-pane>
          <el-tab-pane v-if="canReadIntegration(selectedPlugin)" label="商品 / 报价" name="catalog" />
          <el-tab-pane v-if="selectedPlugin.availableCapabilities.includes('SCHOOLS')" label="学校检索" name="schools" />
        </el-tabs>

        <section v-if="activeTab !== 'research' && canReadIntegration(selectedPlugin)" class="read-workspace" aria-label="只读查询工作区">
          <el-alert type="info" :closable="false" title="使用已保存、验证并启用的同类型配置"
            description="本页不填写密钥，不登录学生账号。每次点击只发起一次只读请求，切换选择不会自动查询上游。" />
          <div class="provider-selector">
            <div class="selector-heading"><label for="plugin-provider-filter">接口配置</label><el-button link type="primary" @click="configureProvider(selectedPlugin.providerType)">新建 / 管理配置</el-button></div>
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
            <p v-else-if="!providerLoading && !providerOptions.length" class="empty-provider-note">没有找到对应配置。请先保存 {{ selectedPlugin.providerType }} 接口，测试通过后显式启用。</p>
            <p v-else-if="!providerLoading && !providerOptions.some(canReadProvider)" class="empty-provider-note">当前页配置均未验证启用，请先在接口配置页完成测试与启用。</p>
          </div>

          <section v-if="activeTab === 'catalog'" class="catalog-workspace">
            <div class="query-controls">
              <el-select v-if="selectedPlugin.projects.length" v-model="project" aria-label="选择闪电项目" placeholder="选择项目">
                <el-option v-for="option in selectedPlugin.projects" :key="option.id" :label="option.name" :value="option.id" />
              </el-select>
              <el-button type="primary" :disabled="!providerReady" :loading="catalogLoading" @click="loadCatalog">{{ selectedPlugin.providerType === 'flash' ? '读取项目报价' : '读取商品目录' }}</el-button>
            </div>
            <p class="query-disclaimer">只展示上游报价，不代表本平台零售价或可下单商品；不同项目的计价单位可能不同。</p>
            <el-alert v-if="catalogError" type="error" :closable="false" :title="catalogError.message" :description="catalogError.errorId ? `错误 ID：${catalogError.errorId}` : ''" />
            <el-skeleton v-else-if="catalogLoading" :rows="3" animated />
            <el-empty v-else-if="products === null" description="选择接口后，手动读取商品或项目报价" :image-size="76" />
            <el-empty v-else-if="!products.length" description="上游返回了空商品目录" :image-size="76" />
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
import { integrationStatuses, evidenceLabels, capabilityLabels, statusInfo, canReadIntegration, canReadProvider,
  providerOptionLabel, filterIntegrations, formatPluginPrice, integrationError, latestRequest, isReadOnlyProviderType } from "@/utils/pluginIntegrations";

const integrations = ref([]), loading = ref(false), loadError = ref(false);
const keyword = ref(""), status = ref("");
const filteredIntegrations = computed(() => filterIntegrations(integrations.value, keyword.value, status.value));
const countStatus = (value) => integrations.value.filter((item) => item.integrationStatus === value).length;
const readOnlyCount = computed(() => integrations.value.filter(canReadIntegration).length);
const drawerVisible = ref(false), selectedPlugin = ref(null), activeTab = ref("research");
const providerOptions = ref([]), providerId = ref(null), providerLoading = ref(false), providerError = ref(null);
const providerKeyword = ref(""), providerPage = ref(1), providerTotal = ref(0), providersInitialized = ref(false);
const project = ref("");
const providerReady = computed(() => canReadProvider(providerOptions.value.find((item) => item.id === providerId.value)));
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
  providersInitialized.value = false; project.value = plugin.projects[0]?.id || ""; schoolKeyword.value = "";
  activeTab.value = tab; drawerVisible.value = true;
  if (tab !== "research") loadProviders(1);
}
let openedRoutePlugin;
function openRequestedPlugin() {
  const id = router.currentRoute.value.query.plugin;
  const plugin = integrations.value.find((item) => item.id === id);
  if (plugin && id !== openedRoutePlugin && canReadIntegration(plugin)) {
    openedRoutePlugin = id;
    openPlugin(plugin, "catalog");
  }
}
watch(() => router.currentRoute.value.query.plugin, () => { openedRoutePlugin = undefined; openRequestedPlugin(); });
function configureProvider(type) {
  router.push({ path: "/admin/api-providers", query: isReadOnlyProviderType(type) ? { type } : {} });
}
async function loadProviders(page = 1) {
  const plugin = selectedPlugin.value;
  if (!drawerVisible.value || !canReadIntegration(plugin)) return;
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
  if (!providerReady.value || catalogLoading.value) return;
  const task = catalogRequests.begin(); catalogLoading.value = true; products.value = null; catalogError.value = null;
  try {
    const response = await fetchPluginCatalog(selectedPlugin.value.id, providerId.value, project.value, task.signal);
    if (task.current()) products.value = response.data;
  } catch (error) { if (task.current()) catalogError.value = integrationError(error); }
  finally { if (task.current()) catalogLoading.value = false; }
}
async function loadSchools(page = 1, newSearch = false) {
  if (!providerReady.value || schoolLoading.value) return;
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
watch(drawerVisible, (visible) => { if (!visible) { providerRequests.invalidate(); clearReadResults(); } });
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
.evidence-files { font-family: ui-monospace, monospace; }
.evidence-read-button { margin-top: 20px; }
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
  .plugin-decision { grid-column: auto; }
  .quote-row { gap: 10px; }
  .quote-price strong { font-size: 18px; }
  .query-controls .el-select { width: 100%; flex-basis: 100%; }
}
</style>
