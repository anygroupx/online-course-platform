<template>
  <div class="system-variable-management">
    <el-card class="page-header">
      <div class="header-content">
        <div>
          <h2>系统变量管理</h2>
          <p>统一管理业务状态与系统主题色，配置保存后对所有客户端生效。</p>
        </div>
        <div v-if="isThemeFilter" class="theme-header-badge">
          <span class="theme-header-swatch" :style="{ background: themePreviewTokens['--primary-gradient'] }" />
          {{ THEME_MODE_LABELS[themeMode] }}
        </div>
      </div>
    </el-card>

    <el-card class="operation-card">
      <div class="operation-bar">
        <div class="left-actions">
          <template v-if="isThemeFilter">
            <el-button type="primary" :loading="savingTheme" :disabled="!changedThemeRows.length" @click="saveThemeChanges">
              <el-icon><Check /></el-icon>
              保存更改<span v-if="changedThemeRows.length">（{{ changedThemeRows.length }}）</span>
            </el-button>
            <el-button :disabled="!changedThemeRows.length" @click="discardThemeChanges">
              <el-icon><RefreshLeft /></el-icon>
              撤销
            </el-button>
            <el-button @click="resetThemeDrafts">
              <el-icon><MagicStick /></el-icon>
              恢复系统默认色
            </el-button>
          </template>
          <template v-else>
            <el-button type="primary" @click="showCreateDialog">
              <el-icon><Plus /></el-icon>
              添加变量
            </el-button>
            <el-button @click="loadVariables">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </template>
        </div>

        <div class="right-filters">
          <el-select
            v-model="filters.variableType"
            class="type-filter"
            placeholder="全部变量类型"
            clearable
            @change="handleTypeChange"
          >
            <el-option v-for="type in variableTypes" :key="type" :label="getTypeLabel(type)" :value="type" />
          </el-select>
          <el-input
            v-if="!isThemeFilter"
            v-model="filters.keyword"
            class="keyword-filter"
            placeholder="搜索名称、键名、值或描述"
            clearable
            @input="handleSearch"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </div>
      </div>
    </el-card>

    <template v-if="isThemeFilter">
      <el-card class="theme-preview-card" v-loading="loading">
        <div class="theme-preview" :class="`is-${themeMode}`" :style="themePreviewTokens">
          <div class="preview-copy">
            <span class="preview-eyebrow">实时预览 · {{ THEME_MODE_LABELS[themeMode] }}</span>
            <h3>用语义色构建一致的系统体验</h3>
            <p>颜色会在当前编辑器内实时预览；点击“保存更改”后才会发布到所有页面。</p>
            <div class="preview-actions">
              <button class="preview-primary">主要操作</button>
              <button class="preview-secondary">次要操作</button>
            </div>
          </div>
          <div class="preview-status-list">
            <span class="preview-status success">成功</span>
            <span class="preview-status warning">警告</span>
            <span class="preview-status danger">危险</span>
            <span class="preview-status info">信息</span>
          </div>
        </div>
      </el-card>

      <el-alert
        v-if="missingThemeVariables.length"
        class="theme-warning"
        type="warning"
        :closable="false"
        show-icon
        title="部分主题变量尚未写入数据库"
        :description="`缺少 ${missingThemeVariables.length} 项。重启新版后端或执行 010_theme_color_variables.sql 后即可管理；当前预览使用内置默认值。`"
      />

      <section v-for="group in THEME_GROUPS" :key="group.key" class="theme-group-section">
        <div class="theme-group-heading">
          <div>
            <h3>{{ group.label }}</h3>
            <p>{{ group.description }}</p>
          </div>
          <span>{{ themeItemsForGroup(group.key).length }} 项</span>
        </div>

        <div class="theme-token-grid">
          <article v-for="item in themeItemsForGroup(group.key)" :key="item.key" class="theme-token-card">
            <div class="token-heading">
              <div class="token-swatch" :style="{ backgroundColor: getThemeDraft(item)?.value || item.defaults[themeMode] }" />
              <div class="token-copy">
                <strong>{{ item.label }}</strong>
                <code>{{ item.cssVariable }}</code>
              </div>
              <el-switch
                v-if="item.variable"
                :model-value="getThemeDraft(item)?.enabled"
                inline-prompt
                active-text="启"
                inactive-text="停"
                @change="(value) => updateThemeDraftEnabled(item, value)"
              />
            </div>
            <p>{{ item.description }}</p>
            <div v-if="item.variable" class="token-editor">
              <el-color-picker
                :model-value="getThemeDraft(item)?.value"
                show-alpha
                color-format="rgb"
                @change="(value) => updateThemeDraftValue(item, value)"
              />
              <el-input
                :model-value="getThemeDraft(item)?.value"
                :class="{ 'is-invalid-color': !isCssColor(getThemeDraft(item)?.value) }"
                @input="(value) => updateThemeDraftValue(item, value)"
              />
              <el-button text title="恢复此项默认色" @click="resetThemeItem(item)">
                <el-icon><RefreshLeft /></el-icon>
              </el-button>
            </div>
            <div v-else class="token-missing">等待系统变量初始化</div>
          </article>
        </div>
      </section>
    </template>

    <el-card v-else class="table-card">
      <el-table :data="variables" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="variableKey" label="变量键名" min-width="150" />
        <el-table-column prop="variableName" label="显示名称" min-width="140" />
        <el-table-column prop="variableType" label="变量类型" width="130">
          <template #default="{ row }">
            <el-tag :type="getTypeTagType(row.variableType)">{{ getTypeLabel(row.variableType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="variableValue" label="变量值" min-width="110" />
        <el-table-column prop="variableLabel" label="描述" min-width="190" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="72" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled ? 'success' : 'danger'">{{ row.isEnabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="默认" width="70">
          <template #default="{ row }"><el-tag v-if="row.isDefault" type="warning">默认</el-tag></template>
        </el-table-column>
        <el-table-column prop="color" label="颜色" width="72">
          <template #default="{ row }">
            <div v-if="row.color" class="color-preview" :style="{ backgroundColor: row.color }" />
          </template>
        </el-table-column>
        <el-table-column label="操作" :width="isMobile ? 96 : 260" fixed="right">
          <template #default="{ row }">
            <el-dropdown v-if="isMobile" trigger="click" @command="(command) => handleVariableAction(command, row)">
              <el-button size="small">操作 <el-icon><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="edit">编辑</el-dropdown-item>
                  <el-dropdown-item command="toggle">{{ row.isEnabled ? '禁用' : '启用' }}</el-dropdown-item>
                  <el-dropdown-item v-if="!row.isDefault" command="default">设为默认</el-dropdown-item>
                  <el-dropdown-item v-if="!row.isDefault" command="delete" divided>删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <div v-else class="variable-row-actions">
              <el-button size="small" @click="showEditDialog(row)">编辑</el-button>
              <el-button size="small" :type="row.isEnabled ? 'warning' : 'success'" @click="toggleStatus(row)">
                {{ row.isEnabled ? '禁用' : '启用' }}
              </el-button>
              <el-button v-if="!row.isDefault" size="small" type="info" @click="setDefault(row)">设为默认</el-button>
              <el-button v-if="!row.isDefault" size="small" type="danger" @click="deleteVariable(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          :layout="isMobile ? 'sizes, prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
          :pager-count="isMobile ? 5 : 7"
          :size="isMobile ? 'small' : 'default'"
          @size-change="loadVariables"
          @current-change="loadVariables"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑变量' : '添加变量'"
      width="600px"
      append-to-body
      align-center
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" :label-position="isMobile ? 'top' : 'right'" :label-width="isMobile ? 'auto' : '120px'">
        <el-form-item label="变量键名" prop="variableKey"><el-input v-model="form.variableKey" placeholder="请输入变量键名" /></el-form-item>
        <el-form-item label="显示名称" prop="variableName"><el-input v-model="form.variableName" placeholder="请输入显示名称" /></el-form-item>
        <el-form-item label="变量类型" prop="variableType">
          <el-select v-model="form.variableType" placeholder="请选择变量类型">
            <el-option v-for="type in creatableVariableTypes" :key="type" :label="getTypeLabel(type)" :value="type" />
          </el-select>
        </el-form-item>
        <el-form-item label="变量值" prop="variableValue"><el-input v-model="form.variableValue" placeholder="请输入变量值" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.variableLabel" type="textarea" placeholder="请输入描述" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="是否默认"><el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="是否启用"><el-switch v-model="form.isEnabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="显示颜色"><el-color-picker v-model="form.color" show-alpha /></el-form-item>
        <el-form-item label="图标"><el-input v-model="form.icon" placeholder="请输入图标名称" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Check, MagicStick, Plus, Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
import { useResponsive } from '@/composables/useResponsive'
import { useThemeStore } from '@/stores/theme'
import { themes } from '@/styles/themes'
import {
  THEME_COLOR_TOKENS,
  THEME_GROUPS,
  THEME_MODE_LABELS,
  THEME_VARIABLE_TYPES,
  buildPrimaryGradient,
  getThemeModeByType,
  isCssColor,
  isThemeVariableType
} from '@/config/themeVariableConfig'
import {
  createVariable,
  deleteVariable as deleteVariableApi,
  getVariableTypes,
  queryVariables,
  setDefaultVariable,
  toggleVariableStatus,
  updateThemeVariables,
  updateVariable
} from '@/api/variable'

const route = useRoute()
const router = useRouter()
const themeStore = useThemeStore()
const { isMobile } = useResponsive()

const loading = ref(false)
const savingTheme = ref(false)
const variables = ref([])
const variableTypes = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const themeDrafts = reactive({})
let searchTimer

const filters = reactive({
  variableType: typeof route.query.type === 'string' ? route.query.type : '',
  keyword: ''
})
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })
const form = reactive({
  id: null,
  variableKey: '',
  variableName: '',
  variableType: '',
  variableValue: '',
  variableLabel: '',
  sortOrder: 0,
  isDefault: 0,
  isEnabled: 1,
  color: '',
  icon: ''
})

const rules = {
  variableKey: [{ required: true, message: '请输入变量键名', trigger: 'blur' }],
  variableName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  variableType: [{ required: true, message: '请选择变量类型', trigger: 'change' }],
  variableValue: [{ required: true, message: '请输入变量值', trigger: 'blur' }]
}

const typeLabels = {
  order_status: '订单状态',
  dock_status: '对接状态',
  user_status: '用户状态',
  platform_status: '平台状态',
  card_status: '充值卡状态',
  announcement_type: '公告类型',
  session_status: '会话状态',
  message_type: '消息类型',
  theme_color_light: '主题色 · 浅色',
  theme_color_dark: '主题色 · 深色'
}
const typeTagMap = {
  order_status: 'primary', dock_status: 'success', user_status: 'warning', platform_status: 'info',
  card_status: 'danger', announcement_type: 'primary', session_status: 'success', message_type: 'info',
  theme_color_light: 'primary', theme_color_dark: 'info'
}

const isThemeFilter = computed(() => isThemeVariableType(filters.variableType))
const themeMode = computed(() => getThemeModeByType(filters.variableType))
const creatableVariableTypes = computed(() => variableTypes.value.filter((type) => !isThemeVariableType(type)))
const variableByThemeKey = computed(() => Object.fromEntries(variables.value.map((item) => [item.variableKey, item])))
const missingThemeVariables = computed(() => THEME_COLOR_TOKENS.filter((item) => !variableByThemeKey.value[item.key]))
const changedThemeRows = computed(() => THEME_COLOR_TOKENS.filter((item) => {
  const variable = variableByThemeKey.value[item.key]
  const draft = themeDrafts[item.key]
  return variable && draft && (draft.value !== variable.variableValue || draft.enabled !== Boolean(variable.isEnabled))
}))
const themePreviewTokens = computed(() => {
  const tokens = { ...themes[themeMode.value] }
  for (const item of THEME_COLOR_TOKENS) {
    const draft = themeDrafts[item.key]
    if (draft?.enabled && isCssColor(draft.value)) tokens[item.cssVariable] = draft.value.trim()
  }
  tokens['--primary-gradient'] = buildPrimaryGradient(tokens)
  return tokens
})

const getTypeLabel = (type) => typeLabels[type] || type
const getTypeTagType = (type) => typeTagMap[type] || 'default'
const getThemeDraft = (item) => themeDrafts[item.key]
const themeItemsForGroup = (group) => THEME_COLOR_TOKENS
  .filter((item) => item.group === group)
  .map((item) => ({ ...item, variable: variableByThemeKey.value[item.key] }))

const syncThemeDrafts = () => {
  for (const item of THEME_COLOR_TOKENS) {
    const variable = variableByThemeKey.value[item.key]
    themeDrafts[item.key] = {
      value: variable?.variableValue || item.defaults[themeMode.value],
      enabled: variable ? Boolean(variable.isEnabled) : true
    }
  }
}

const loadVariables = async () => {
  loading.value = true
  try {
    const response = await queryVariables({
      page: isThemeFilter.value ? 1 : pagination.page,
      pageSize: isThemeFilter.value ? 100 : pagination.pageSize,
      variableType: filters.variableType || undefined,
      keyword: filters.keyword.trim() || undefined
    })
    variables.value = response.data.records || []
    pagination.total = response.data.total || 0
    if (isThemeFilter.value) syncThemeDrafts()
  } catch (error) {
    ElMessage.error('加载变量列表失败')
  } finally {
    loading.value = false
  }
}

const loadVariableTypes = async () => {
  try {
    const response = await getVariableTypes()
    variableTypes.value = [...new Set([...(response.data || []), ...Object.values(THEME_VARIABLE_TYPES)])]
  } catch (error) {
    variableTypes.value = [...Object.keys(typeLabels)]
    ElMessage.warning('变量类型加载失败，已使用内置类型列表')
  }
}

const handleTypeChange = async () => {
  pagination.page = 1
  filters.keyword = ''
  await router.replace({ query: filters.variableType ? { ...route.query, type: filters.variableType } : {} })
  loadVariables()
}

const handleSearch = () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    pagination.page = 1
    loadVariables()
  }, 280)
}

const updateThemeDraftValue = (item, value) => {
  if (!themeDrafts[item.key] || value === null) return
  themeDrafts[item.key].value = value
}
const updateThemeDraftEnabled = (item, enabled) => {
  if (themeDrafts[item.key]) themeDrafts[item.key].enabled = enabled
}
const resetThemeItem = (item) => {
  if (!themeDrafts[item.key]) return
  themeDrafts[item.key].value = item.defaults[themeMode.value]
  themeDrafts[item.key].enabled = true
}
const resetThemeDrafts = async () => {
  try {
    await ElMessageBox.confirm(`确定将${THEME_MODE_LABELS[themeMode.value]}恢复为系统默认色吗？保存前仍可撤销。`, '恢复默认主题色', { type: 'warning' })
    THEME_COLOR_TOKENS.forEach(resetThemeItem)
  } catch (error) {
    // 用户取消，无需提示。
  }
}
const discardThemeChanges = () => syncThemeDrafts()

const saveThemeChanges = async () => {
  const invalid = changedThemeRows.value.find((item) => !isCssColor(themeDrafts[item.key]?.value))
  if (invalid) {
    ElMessage.error(`${invalid.label}不是有效的 CSS 颜色`)
    return
  }
  savingTheme.value = true
  try {
    const updates = changedThemeRows.value.map((item) => {
      const variable = variableByThemeKey.value[item.key]
      const draft = themeDrafts[item.key]
      return {
        ...variable,
        variableValue: draft.value.trim(),
        isEnabled: draft.enabled ? 1 : 0,
        isDefault: 0,
        color: null,
        icon: null
      }
    })
    await updateThemeVariables(updates)
    ElMessage.success('主题颜色已发布')
    await loadVariables()
    await themeStore.refreshThemeVariables()
  } catch (error) {
    ElMessage.error(error?.message || '主题颜色保存失败')
  } finally {
    savingTheme.value = false
  }
}

const showCreateDialog = () => {
  isEdit.value = false
  resetForm()
  form.variableType = filters.variableType || ''
  dialogVisible.value = true
}
const showEditDialog = (row) => {
  isEdit.value = true
  Object.assign(form, row)
  dialogVisible.value = true
}
const resetForm = () => {
  Object.assign(form, {
    id: null, variableKey: '', variableName: '', variableType: '', variableValue: '', variableLabel: '',
    sortOrder: 0, isDefault: 0, isEnabled: 1, color: '', icon: ''
  })
  formRef.value?.resetFields()
}
const submitForm = async () => {
  try {
    await formRef.value.validate()
    if (isEdit.value) await updateVariable(form)
    else await createVariable(form)
    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    dialogVisible.value = false
    loadVariables()
  } catch (error) {
    if (error instanceof Error) ElMessage.error(error.message || '操作失败')
  }
}
const toggleStatus = async (row) => {
  try {
    await toggleVariableStatus(row.id, !row.isEnabled)
    ElMessage.success('状态切换成功')
    loadVariables()
  } catch (error) {
    ElMessage.error('状态切换失败')
  }
}
const setDefault = async (row) => {
  try {
    await setDefaultVariable(row.id)
    ElMessage.success('设置默认成功')
    loadVariables()
  } catch (error) {
    ElMessage.error('设置默认失败')
  }
}
const handleVariableAction = (command, row) => ({
  edit: () => showEditDialog(row),
  toggle: () => toggleStatus(row),
  default: () => setDefault(row),
  delete: () => deleteVariable(row)
}[command]?.())
const deleteVariable = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除这个变量吗？', '确认删除', { type: 'warning' })
    await deleteVariableApi(row.id)
    ElMessage.success('删除成功')
    loadVariables()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('删除失败')
  }
}

onMounted(async () => {
  await loadVariableTypes()
  await loadVariables()
})
onBeforeUnmount(() => clearTimeout(searchTimer))
</script>

<style scoped>
.system-variable-management { padding: 20px; }
.page-header, .operation-card, .table-card, .theme-preview-card { margin-bottom: 20px; }
.header-content, .operation-bar, .theme-group-heading, .token-heading { display: flex; align-items: center; }
.header-content, .operation-bar, .theme-group-heading { justify-content: space-between; gap: 20px; }
.header-content h2 { margin: 0 0 8px; color: var(--text-primary); }
.header-content p, .theme-group-heading p, .theme-token-card p { margin: 0; color: var(--text-secondary); font-size: 14px; }
.theme-header-badge { display: inline-flex; align-items: center; gap: 10px; padding: 8px 12px; border: 1px solid var(--border-color); border-radius: 999px; color: var(--text-regular); font-weight: 600; background: var(--surface-acrylic); }
.theme-header-swatch { width: 34px; height: 18px; border-radius: 999px; box-shadow: inset 0 0 0 1px var(--border-color-light); }
.left-actions, .right-filters, .variable-row-actions { display: flex; align-items: center; gap: 10px; }
.operation-bar, .left-actions, .right-filters { flex-wrap: wrap; min-width: 0; max-width: 100%; }
.right-filters > * { max-width: 100%; }
.left-actions :deep(.el-button), .variable-row-actions :deep(.el-button) { margin-inline-start: 0; }
.type-filter { width: 190px; }
.keyword-filter { width: 240px; }
.color-preview { width: 22px; height: 22px; border-radius: 6px; border: 1px solid var(--border-color); }
.pagination-container { display: flex; justify-content: center; margin-top: 20px; }

.theme-preview-card :deep(.el-card__body) { padding: 0; }
.theme-preview { min-height: 260px; display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(220px, .6fr); gap: 28px; align-items: center; padding: clamp(24px, 4vw, 48px); border-radius: var(--radius-lg); color: var(--text-regular); background: radial-gradient(circle at 90% 10%, color-mix(in srgb, var(--brand-violet) 24%, transparent), transparent 38%), var(--bg-body); }
.preview-copy { max-width: 680px; }
.preview-eyebrow { display: inline-block; margin-bottom: 10px; color: var(--brand-primary); font-size: 13px; font-weight: 700; letter-spacing: .04em; }
.preview-copy h3 { margin: 0 0 10px; color: var(--text-primary); font-size: clamp(24px, 3vw, 36px); line-height: 1.18; }
.preview-copy p { margin: 0; color: var(--text-secondary); line-height: 1.7; }
.preview-actions { display: flex; gap: 10px; margin-top: 24px; }
.preview-actions button { min-height: 38px; padding: 0 18px; border-radius: var(--radius-sm); font: inherit; font-weight: 700; cursor: default; }
.preview-primary { border: 1px solid var(--brand-primary); color: var(--text-on-brand); background: var(--primary-gradient); }
.preview-secondary { border: 1px solid var(--border-color); color: var(--text-regular); background: var(--surface-solid); }
.preview-status-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.preview-status { display: grid; place-items: center; min-height: 72px; border-radius: var(--radius-md); color: var(--text-on-brand); font-weight: 700; box-shadow: var(--shadow-sm); }
.preview-status.success { background: var(--color-success); }
.preview-status.warning { background: var(--color-warning); }
.preview-status.danger { background: var(--color-danger); }
.preview-status.info { background: var(--color-info); }
.theme-warning { margin-bottom: 20px; }
.theme-group-section { margin-bottom: 22px; }
.theme-group-heading { margin-bottom: 12px; padding: 0 4px; }
.theme-group-heading h3 { margin: 0 0 4px; color: var(--text-primary); font-size: 18px; }
.theme-group-heading > span { color: var(--text-placeholder); font-size: 13px; }
.theme-token-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.theme-token-card { min-width: 0; padding: 16px; border: 1px solid var(--border-color-light); border-radius: var(--radius-md); background: var(--bg-card); box-shadow: inset 0 1px 0 var(--stroke-highlight), var(--shadow-sm); }
.token-heading { gap: 12px; }
.token-swatch { flex: 0 0 auto; width: 38px; height: 38px; border: 1px solid var(--border-color); border-radius: 10px; box-shadow: inset 0 0 0 2px color-mix(in srgb, var(--surface-solid) 54%, transparent); }
.token-copy { min-width: 0; flex: 1; }
.token-copy strong, .token-copy code { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.token-copy strong { color: var(--text-primary); font-size: 14px; }
.token-copy code { margin-top: 3px; color: var(--text-placeholder); font-size: 11px; }
.theme-token-card > p { min-height: 40px; margin: 12px 0; line-height: 1.5; }
.token-editor { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 8px; align-items: center; }
.token-editor :deep(.el-input__inner) { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 12px; }
.token-editor .is-invalid-color :deep(.el-input__wrapper) { box-shadow: inset 0 0 0 1px var(--color-danger) !important; }
.token-missing { padding: 9px 10px; border-radius: var(--radius-sm); color: var(--text-placeholder); font-size: 12px; background: color-mix(in srgb, var(--color-warning) 8%, transparent); }

@media (max-width: 1100px) { .theme-token-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 767px) {
  .system-variable-management { padding: 12px; }
  .page-header, .operation-card, .table-card, .theme-preview-card { margin-bottom: 12px; }
  .header-content, .operation-bar, .right-filters { flex-direction: column; align-items: stretch; }
  .theme-header-badge { align-self: flex-start; }
  .operation-bar { gap: 12px; }
  .left-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .left-actions :deep(.el-button) { width: 100%; }
  .left-actions :deep(.el-button:nth-child(3)) { grid-column: 1 / -1; }
  .type-filter, .keyword-filter, .right-filters :deep(.el-select) { width: 100%; }
  .theme-preview { grid-template-columns: 1fr; padding: 24px 18px; }
  .preview-status-list { grid-template-columns: repeat(4, minmax(0, 1fr)); }
  .preview-status { min-height: 54px; font-size: 12px; }
  .theme-token-grid { grid-template-columns: 1fr; }
  .pagination-container :deep(.el-pagination) { max-width: 100%; flex-wrap: wrap; justify-content: center; row-gap: 8px; }
}
@media (max-width: 480px) {
  .left-actions { grid-template-columns: 1fr; }
  .left-actions :deep(.el-button:nth-child(3)) { grid-column: auto; }
  .preview-status-list { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .preview-actions { flex-direction: column; }
}
</style>
