<template>
  <div class="system-variable-management">
    <!-- 页面标题 -->
    <el-card class="page-header">
      <div class="header-content">
        <h2>系统变量管理</h2>
        <p>管理系统中的各种状态变量和配置项</p>
      </div>
    </el-card>

    <!-- 操作栏 -->
    <el-card class="operation-card">
      <div class="operation-bar">
        <div class="left-actions">
          <el-button type="primary" @click="showCreateDialog">
            <el-icon><Plus /></el-icon>
            添加变量
          </el-button>
          <el-button @click="loadVariables">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
        <div class="right-filters">
          <el-select v-model="filters.variableType" placeholder="选择变量类型" clearable @change="loadVariables">
            <el-option
              v-for="type in variableTypes"
              :key="type"
              :label="getTypeLabel(type)"
              :value="type"
            />
          </el-select>
          <el-input
            v-model="filters.keyword"
            placeholder="搜索变量名称或键名"
            clearable
            @input="handleSearch"
            style="width: 200px; margin-left: 10px;"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>
      </div>
    </el-card>

    <!-- 变量列表 -->
    <el-card class="table-card">
      <el-table
        :data="variables"
        v-loading="loading"
        stripe
        style="width: 100%"
      >
        <el-table-column prop="variableKey" label="变量键名" width="150" />
        <el-table-column prop="variableName" label="显示名称" width="150" />
        <el-table-column prop="variableType" label="变量类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getTypeTagType(row.variableType)">
              {{ getTypeLabel(row.variableType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="variableValue" label="变量值" width="100" />
        <el-table-column prop="variableLabel" label="描述" min-width="200" />
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled ? 'success' : 'danger'">
              {{ row.isEnabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="默认" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" type="warning">默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="color" label="颜色" width="80">
          <template #default="{ row }">
            <div v-if="row.color" class="color-preview" :style="{ backgroundColor: row.color }"></div>
          </template>
        </el-table-column>
        <el-table-column prop="icon" label="图标" width="80">
          <template #default="{ row }">
            <el-icon v-if="row.icon">
              <component :is="row.icon" />
            </el-icon>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showEditDialog(row)">编辑</el-button>
            <el-button 
              size="small" 
              :type="row.isEnabled ? 'warning' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.isEnabled ? '禁用' : '启用' }}
            </el-button>
            <el-button 
              v-if="!row.isDefault"
              size="small" 
              type="info"
              @click="setDefault(row)"
            >
              设为默认
            </el-button>
            <el-button 
              v-if="!row.isDefault"
              size="small" 
              type="danger"
              @click="deleteVariable(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadVariables"
          @current-change="loadVariables"
        />
      </div>
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑变量' : '添加变量'"
      width="600px"
      @close="resetForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
      >
        <el-form-item label="变量键名" prop="variableKey">
          <el-input v-model="form.variableKey" placeholder="请输入变量键名" />
        </el-form-item>
        <el-form-item label="显示名称" prop="variableName">
          <el-input v-model="form.variableName" placeholder="请输入显示名称" />
        </el-form-item>
        <el-form-item label="变量类型" prop="variableType">
          <el-select v-model="form.variableType" placeholder="请选择变量类型">
            <el-option
              v-for="type in variableTypes"
              :key="type"
              :label="getTypeLabel(type)"
              :value="type"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="变量值" prop="variableValue">
          <el-input v-model="form.variableValue" placeholder="请输入变量值" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.variableLabel" type="textarea" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="是否默认">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="form.isEnabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="显示颜色">
          <el-color-picker v-model="form.color" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="请输入图标名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createVariable,
  updateVariable,
  deleteVariable as deleteVariableApi,
  queryVariables,
  toggleVariableStatus,
  setDefaultVariable,
  getVariableTypes
} from '@/api/variable'

// 响应式数据
const loading = ref(false)
const variables = ref([])
const variableTypes = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()

// 筛选条件
const filters = reactive({
  variableType: '',
  keyword: ''
})

// 分页信息
const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
})

// 表单数据
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

// 表单验证规则
const rules = {
  variableKey: [
    { required: true, message: '请输入变量键名', trigger: 'blur' }
  ],
  variableName: [
    { required: true, message: '请输入显示名称', trigger: 'blur' }
  ],
  variableType: [
    { required: true, message: '请选择变量类型', trigger: 'change' }
  ],
  variableValue: [
    { required: true, message: '请输入变量值', trigger: 'blur' }
  ]
}

// 变量类型标签映射
const typeLabels = {
  'order_status': '订单状态',
  'dock_status': '对接状态',
  'user_status': '用户状态',
  'platform_status': '平台状态',
  'card_status': '充值卡状态',
  'announcement_type': '公告类型',
  'session_status': '会话状态',
  'message_type': '消息类型'
}

// 获取类型标签
const getTypeLabel = (type) => {
  return typeLabels[type] || type
}

// 获取类型标签类型
const getTypeTagType = (type) => {
  const typeMap = {
    'order_status': 'primary',
    'dock_status': 'success',
    'user_status': 'warning',
    'platform_status': 'info',
    'card_status': 'danger',
    'announcement_type': 'primary',
    'session_status': 'success',
    'message_type': 'info'
  }
  return typeMap[type] || 'default'
}

// 加载变量列表
const loadVariables = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      pageSize: pagination.pageSize,
      variableType: filters.variableType || undefined
    }
    const response = await queryVariables(params)
    variables.value = response.data.records
    pagination.total = response.data.total
  } catch (error) {
    ElMessage.error('加载变量列表失败')
  } finally {
    loading.value = false
  }
}

// 加载变量类型
const loadVariableTypes = async () => {
  try {
    const response = await getVariableTypes()
    variableTypes.value = response.data
  } catch (error) {
    ElMessage.error('加载变量类型失败')
  }
}

// 搜索处理
const handleSearch = () => {
  pagination.page = 1
  loadVariables()
}

// 显示创建对话框
const showCreateDialog = () => {
  isEdit.value = false
  dialogVisible.value = true
  resetForm()
}

// 显示编辑对话框
const showEditDialog = (row) => {
  isEdit.value = true
  dialogVisible.value = true
  Object.assign(form, row)
}

// 重置表单
const resetForm = () => {
  Object.assign(form, {
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
  formRef.value?.resetFields()
}

// 提交表单
const submitForm = async () => {
  try {
    await formRef.value.validate()
    
    if (isEdit.value) {
      await updateVariable(form)
      ElMessage.success('更新成功')
    } else {
      await createVariable(form)
      ElMessage.success('创建成功')
    }
    
    dialogVisible.value = false
    loadVariables()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

// 切换状态
const toggleStatus = async (row) => {
  try {
    await toggleVariableStatus(row.id, !row.isEnabled)
    ElMessage.success('状态切换成功')
    loadVariables()
  } catch (error) {
    ElMessage.error('状态切换失败')
  }
}

// 设为默认
const setDefault = async (row) => {
  try {
    await setDefaultVariable(row.id)
    ElMessage.success('设置默认成功')
    loadVariables()
  } catch (error) {
    ElMessage.error('设置默认失败')
  }
}

// 删除变量
const deleteVariable = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除这个变量吗？', '确认删除', {
      type: 'warning'
    })
    
    await deleteVariableApi(row.id)
    ElMessage.success('删除成功')
    loadVariables()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 页面初始化
onMounted(() => {
  loadVariableTypes()
  loadVariables()
})
</script>

<style scoped>
.system-variable-management {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.header-content h2 {
  margin: 0 0 8px 0;
  color: var(--text-primary);
}

.header-content p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 14px;
}

.operation-card {
  margin-bottom: 20px;
}

.operation-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.left-actions {
  display: flex;
  gap: 10px;
}

.right-filters {
  display: flex;
  align-items: center;
}

.table-card {
  margin-bottom: 20px;
}

.color-preview {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  border: 1px solid var(--border-color);
}

.pagination-container {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
