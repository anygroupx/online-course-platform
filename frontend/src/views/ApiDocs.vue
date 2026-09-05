<template>
  <div class="api-docs-page">
    <el-card class="header-card">
      <template #header>
        <div class="card-header">
          <h1>第三方 API 对接文档</h1>
          <el-tag type="info">在线测试工具</el-tag>
        </div>
      </template>
      <el-alert title="使用说明" type="info" :closable="false" show-icon>
        <div class="usage-info">
          <p>所有接口均需使用 <code>uid</code> 和 <code>api_key</code> 认证，兼容旧参数 <code>key</code>；不需要网页登录 Token。</p>
          <p>基础请求 URL：<code>{{ apiBaseUrl }}</code>，接口路径为 <code>/external/...</code>，不要重复拼接 <code>/api</code>。</p>
          <p>使用 POST 和 <code>application/x-www-form-urlencoded</code> 请求体。请勿把密钥或学生密码放在 URL 中，也不要发送 JSON 请求体。</p>
          <p>UID 是个人中心的 UUID。APIKey 明文仅在签发时显示一次，密钥前缀不能用于调用；旧数字 UID 需改为 UUID；已完成哈希迁移的旧密钥仍可继续使用。</p>
          <p>生产环境请使用 HTTPS。IP / 密钥限流、密钥有效期、作用域、订单归属和上游安全校验始终生效。</p>
        </div>
      </el-alert>
      <section class="api-key-section" aria-label="在线测试凭证">
        <el-form :model="credentials" label-position="top" class="credentials-form" @submit.prevent>
          <el-form-item label="用户 UUID (UID)">
            <el-input v-model.trim="credentials.uid" placeholder="请输入用户 UUID" autocomplete="off" />
          </el-form-item>
          <el-form-item label="完整 APIKey (api_key)">
            <el-input v-model="credentials.api_key" type="password" show-password placeholder="请输入完整 APIKey，非密钥前缀" autocomplete="off" />
          </el-form-item>
        </el-form>
        <p class="credential-note">凭证仅留在当前页面内存中，不保存到浏览器存储；离开或刷新页面后清除。代码示例始终使用占位符。</p>
        <div class="credential-actions">
          <el-button @click="clearCredentials">清空凭证和结果</el-button>
          <el-button type="primary" plain @click="router.push('/profile')">前往个人中心管理密钥</el-button>
        </div>
      </section>
    </el-card>

    <div class="api-list">
      <el-card v-for="endpoint in externalEndpoints" :key="endpoint.id" class="api-card" :data-api="endpoint.path">
        <template #header>
          <button class="api-card-header" type="button" :aria-expanded="expanded.includes(endpoint.id)" :aria-controls="`api-${endpoint.id}`" @click="toggle(endpoint.id)">
            <span class="endpoint-label">
              <span class="endpoint-title"><el-tag type="success">POST</el-tag><strong>{{ endpoint.title }}</strong></span>
              <code>{{ externalEndpointUrl(apiBaseUrl, endpoint.path) }}</code>
            </span>
            <el-icon :class="{ expanded: expanded.includes(endpoint.id) }"><ArrowRight /></el-icon>
          </button>
        </template>
        <div v-if="expanded.includes(endpoint.id)" :id="`api-${endpoint.id}`" class="api-content">
          <p>{{ endpoint.description }}</p>
          <p>需要作用域：<el-tag size="small" type="info">{{ endpoint.scope }}</el-tag></p>
          <h2>请求参数</h2>
          <el-table :data="[...authParams, ...endpoint.fields]" border size="small">
            <el-table-column prop="name" label="参数名" width="115" />
            <el-table-column label="必填" width="64"><template #default="{ row }">{{ row.required ? '是' : '否' }}</template></el-table-column>
            <el-table-column prop="description" label="说明（均为字符串）" min-width="220" />
          </el-table>
          <h2>在线测试</h2>
          <el-alert v-if="endpoint.confirmation" :title="endpoint.description" type="warning" :closable="false" show-icon class="write-warning" />
          <el-form :model="forms[endpoint.id]" :label-position="isMobile ? 'top' : 'left'" label-width="110px" @submit.prevent="runTest(endpoint)">
            <el-form-item v-for="field in endpoint.fields" :key="field.name" :label="field.name" :required="field.required">
              <el-input v-model="forms[endpoint.id][field.name]" :placeholder="field.description" :type="field.secret ? 'password' : 'text'" :show-password="field.secret" autocomplete="off" />
            </el-form-item>
            <el-button type="primary" :loading="loading[endpoint.id]" @click="runTest(endpoint)"><el-icon><VideoPlay /></el-icon>测试{{ endpoint.title }}</el-button>
          </el-form>
          <div v-if="results[endpoint.id]" class="api-result" aria-live="polite">
            <h2>响应结果</h2>
            <pre tabindex="0"><code>{{ formatJson(results[endpoint.id]) }}</code></pre>
          </div>
          <h2>成功响应示例</h2>
          <pre tabindex="0"><code>{{ formatJson({ code: 1, message: '操作成功', data: endpoint.example }) }}</code></pre>
          <h2>代码示例</h2>
          <el-tabs class="api-examples">
            <el-tab-pane v-for="example in examples(endpoint)" :key="example.language" :label="example.language">
              <pre tabindex="0"><code>{{ example.code }}</code></pre>
            </el-tab-pane>
          </el-tabs>
        </div>
      </el-card>
    </div>

    <el-card class="error-card">
      <template #header><h2>响应约定与错误排查</h2></template>
      <p>成功条件为 HTTP 200 且 <code>code === 1</code>；业务数据在 <code>data</code>，消息字段为 <code>message</code>。失败时请记录 <code>errorId</code>（如有），不要记录密钥和学生密码。</p>
      <el-table :data="externalErrorCodes" border size="small">
        <el-table-column prop="http" label="HTTP" width="100" />
        <el-table-column prop="code" label="code" min-width="125" />
        <el-table-column prop="message" label="说明" min-width="185" />
        <el-table-column prop="solution" label="处理建议" min-width="270" />
      </el-table>
      <p>安全迭代不会要求第三方获取 JWT。查课 / 下单依赖上游配置；连接失败应由管理员检查接口验证状态、出站域名白名单、DNS 和 TLS，不能关闭防护来恢复调用。</p>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onBeforeUnmount, onDeactivated } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ArrowRight, VideoPlay } from '@element-plus/icons-vue';
import { useResponsive } from '@/composables/useResponsive';
import { useUserStore } from '@/stores/user';
import * as externalApi from '@/api/external';
import { authParams, externalEndpoints, externalErrorCodes, externalEndpointUrl, externalExamples } from '@/utils/externalApiGuide';

const router = useRouter();
const { isMobile } = useResponsive();
const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/+$/, '');
const credentials = reactive({ uid: useUserStore().userInfo?.uid || '', api_key: '' });
// Remove secrets left by the old test tool; never import them into component state.
for (const storage of [localStorage, sessionStorage]) {
  storage.removeItem('api_test_key');
  storage.removeItem('api_test_uid');
}
const expanded = ref([]);
const forms = reactive(Object.fromEntries(externalEndpoints.map((endpoint) => [endpoint.id, Object.fromEntries(endpoint.fields.map((field) => [field.name, '']))])));
const loading = reactive({});
const results = reactive({});
let generation = 0;
const clearCredentials = () => {
  generation += 1;
  credentials.uid = '';
  credentials.api_key = '';
  for (const endpoint of externalEndpoints) {
    for (const field of endpoint.fields) forms[endpoint.id][field.name] = '';
    delete results[endpoint.id];
  }
};
onBeforeUnmount(clearCredentials);
onDeactivated(clearCredentials);
const toggle = (id) => { expanded.value = expanded.value.includes(id) ? expanded.value.filter((value) => value !== id) : [...expanded.value, id]; };
const formatJson = (data) => JSON.stringify(data, null, 2);
const examples = (endpoint) => externalExamples(endpoint, new URL(externalEndpointUrl(apiBaseUrl, endpoint.path), window.location.origin).href);

const runTest = async (endpoint) => {
  if (loading[endpoint.id]) return;
  if (!credentials.uid || !credentials.api_key) return ElMessage.warning('请填写UID和完整APIKey');
  if (endpoint.fields.some((field) => field.required && !forms[endpoint.id][field.name].trim())) return ElMessage.warning('请填写所有必填参数');
  loading[endpoint.id] = true;
  const current = generation;
  try {
    if (endpoint.confirmation) await ElMessageBox.confirm(endpoint.confirmation, '真实业务操作确认', { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '取消' });
    if (current !== generation) return;
    const result = await externalApi[endpoint.id]({ ...credentials, ...forms[endpoint.id] });
    if (current !== generation) return;
    results[endpoint.id] = { httpStatus: 200, ...result };
    ElMessage.success('请求成功');
  } catch (error) {
    if (error === 'cancel' || error === 'close' || current !== generation) return;
    results[endpoint.id] = {
      httpStatus: error.response?.status || null,
      ...(error.response?.data || { message: error.name === 'AbortError' ? '请求超时；写请求请先查单确认，勿重复提交' : error.message || '网络请求失败' }),
      ...(error.response?.retryAfter ? { retryAfter: error.response.retryAfter } : {}),
    };
    ElMessage.error(results[endpoint.id].message || '请求失败');
  } finally {
    loading[endpoint.id] = false;
  }
};
</script>

<style scoped>
.api-docs-page { box-sizing: border-box; padding: 12px; max-width: 1400px; margin: 0 auto; }
h1 { margin: 0; font-size: 20px; color: var(--text-primary); }
h2 { font-size: 16px; margin: 18px 0 12px; color: var(--text-primary); }
p { line-height: 1.8; overflow-wrap: anywhere; }
code { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; overflow-wrap: anywhere; }
.card-header { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; }
.header-card, .api-list { margin-bottom: 18px; }
.api-key-section { padding: 18px; margin-top: 18px; border-radius: var(--radius-md); background: var(--bg-body); }
.credentials-form { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 16px; }
.credential-note { color: var(--text-secondary); font-size: 13px; }
.credential-actions { display: flex; flex-wrap: wrap; gap: 10px; }
.credential-actions .el-button { margin: 0; white-space: normal; height: auto; }
.api-list { display: grid; gap: 14px; }
.api-card, .api-content, .api-examples, .endpoint-label { min-width: 0; }
.api-card-header { width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 4px 0; border: 0; background: transparent; color: var(--text-primary); text-align: left; cursor: pointer; }
.endpoint-label { display: grid; gap: 8px; }
.endpoint-title { display: flex; align-items: center; gap: 12px; font-size: 16px; }
.endpoint-label code { color: var(--text-secondary); }
.api-card-header > .el-icon { flex-shrink: 0; transition: transform .2s; }
.api-card-header > .expanded { transform: rotate(90deg); }
.write-warning { margin-bottom: 16px; }
pre { box-sizing: border-box; max-width: 100%; overflow: auto; padding: 16px; border: 1px solid var(--border-color-light); border-radius: var(--radius-md); background: var(--bg-body); color: var(--text-primary); font-size: 13px; line-height: 1.7; overscroll-behavior-inline: contain; }
pre code { overflow-wrap: normal; }
.api-result { border-left: 3px solid var(--brand-primary); padding-left: 12px; }
@media (max-width: 767px) {
  .api-docs-page { padding: 0; }
  .api-key-section { padding: 12px; }
  .credentials-form { grid-template-columns: minmax(0, 1fr); gap: 0; }
  .credential-actions .el-button { flex: 1 1 100%; padding: 10px; }
  :deep(.el-card__header), :deep(.el-card__body) { padding: 16px 12px; }
  :deep(.el-alert__content) { min-width: 0; }
  :deep(.el-form-item__content) { min-width: 0; }
  .endpoint-label code { font-size: 12px; }
}
</style>
