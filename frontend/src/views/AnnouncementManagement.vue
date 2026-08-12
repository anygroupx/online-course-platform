<template>
  <div class="announcement-management">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>公告管理</h2>
      <el-button type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon>
        发布公告
      </el-button>
    </div>

    <!-- 搜索筛选 -->
    <div class="search-section">
      <el-form :model="searchForm" inline>
        <el-form-item label="公告类型">
          <el-select v-model="searchForm.type" placeholder="请选择类型" clearable>
            <el-option 
              v-for="option in variableStore.getStatusOptions('announcement_type')"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
            <el-option label="草稿" :value="0" />
            <el-option label="已发布" :value="1" />
            <el-option label="已下线" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="searchForm.title" placeholder="请输入标题关键词" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 公告列表 -->
    <div class="table-section">
      <el-table :data="announcementList" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="typeName" label="类型" width="100" />
        <el-table-column prop="priorityName" label="优先级" width="100">
          <template #default="{ row }">
            <el-tag :type="getPriorityTagType(row.priority)">
              {{ row.priorityName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="statusName" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isTop" label="置顶" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isTop" type="warning">置顶</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isPopup" label="弹窗" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isPopup" type="success">弹窗</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishTime" label="发布时间" width="160" />
        <el-table-column prop="createByName" label="创建人" width="120" />
        <el-table-column 
          label="操作" 
          :width="getOperationColumnWidth()" 
          fixed="right"
          class-name="operation-column"
        >
          <template #default="{ row }">
            <div class="operation-buttons">
              <div class="primary-actions">
                <el-button size="small" @click="handleView(row)">查看</el-button>
                <el-button size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
              </div>
              <div class="secondary-actions">
                <el-dropdown @command="(command) => handleAction(command, row)">
                  <el-button size="small">
                    更多<el-icon class="el-icon--right"><arrow-down /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="publish" v-if="row.status !== 1">发布</el-dropdown-item>
                      <el-dropdown-item command="offline" v-if="row.status === 1">下线</el-dropdown-item>
                      <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-section">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="800px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="公告标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入公告标题" />
        </el-form-item>
        <el-form-item label="公告类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择公告类型">
            <el-option label="系统公告" :value="1" />
            <el-option label="日常公告" :value="2" />
            <el-option label="维护通知" :value="3" />
            <el-option label="活动公告" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-select v-model="form.priority" placeholder="请选择优先级">
            <el-option label="普通" :value="1" />
            <el-option label="重要" :value="2" />
            <el-option label="紧急" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="公告内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            placeholder="请输入公告内容"
          />
        </el-form-item>
        <el-form-item label="设置">
          <el-checkbox v-model="form.isTop">置顶显示</el-checkbox>
          <el-checkbox v-model="form.isPopup">弹窗显示</el-checkbox>
        </el-form-item>
        <el-form-item label="发布时间">
          <el-date-picker
            v-model="form.publishTime"
            type="datetime"
            placeholder="选择发布时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="form.expireTime"
            type="datetime"
            placeholder="选择过期时间（可选）"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          {{ isEdit ? '更新' : '发布' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 查看对话框 -->
    <el-dialog v-model="viewDialogVisible" title="公告详情" width="600px">
      <div v-if="currentAnnouncement" class="announcement-detail">
        <h3>{{ currentAnnouncement.title }}</h3>
        <div class="announcement-meta">
          <el-tag :type="getPriorityTagType(currentAnnouncement.priority)">
            {{ currentAnnouncement.priorityName }}
          </el-tag>
          <el-tag :type="getStatusTagType(currentAnnouncement.status)">
            {{ currentAnnouncement.statusName }}
          </el-tag>
          <span class="meta-text">{{ currentAnnouncement.typeName }}</span>
          <span class="meta-text">发布时间：{{ currentAnnouncement.publishTime }}</span>
        </div>
        <div class="announcement-content">
          {{ currentAnnouncement.content }}
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh, ArrowDown } from '@element-plus/icons-vue'
import {
  getAnnouncementPage,
  createAnnouncement,
  updateAnnouncement,
  deleteAnnouncement,
  publishAnnouncement,
  offlineAnnouncement
} from '@/api/announcement'
import { useVariableStore } from '@/stores/variableStore'
import { useResponsive } from '@/composables/useResponsive'

// 响应式数据
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const viewDialogVisible = ref(false)
const isEdit = ref(false)
const dialogTitle = ref('')

// 变量store
const variableStore = useVariableStore()
const currentAnnouncement = ref(null)

// 搜索表单
const searchForm = reactive({
  type: null,
  status: null,
  title: ''
})

// 分页数据
const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

// 公告列表
const announcementList = ref([])

// 表单数据
const form = reactive({
  id: null,
  title: '',
  content: '',
  type: 2,
  priority: 1,
  isTop: false,
  isPopup: false,
  publishTime: null,
  expireTime: null
})

// 表单验证规则
const rules = {
  title: [
    { required: true, message: '请输入公告标题', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请输入公告内容', trigger: 'blur' }
  ],
  type: [
    { required: true, message: '请选择公告类型', trigger: 'change' }
  ]
}

const formRef = ref()

// 获取优先级标签类型
const getPriorityTagType = (priority) => {
  switch (priority) {
    case 1: return 'info' // 修改：将空字符串改为 "info"
    case 2: return 'warning'
    case 3: return 'danger'
    default: return 'info' // 修改：将空字符串改为 "info"
  }
}

// 获取状态标签类型
const getStatusTagType = (status) => {
  switch (status) {
    case 0: return 'info'
    case 1: return 'success'
    case 2: return 'danger'
    default: return 'info' // 修改：将空字符串改为 "info"
  }
}

// 加载公告列表
const loadAnnouncementList = async () => {
  loading.value = true
  try {
    const params = {
      current: pagination.current,
      size: pagination.size,
      ...searchForm
    }
    const response = await getAnnouncementPage(params)
    if (response.code === 1) {
      announcementList.value = response.data.records
      pagination.total = response.data.total
    }
  } catch (error) {
    ElMessage.error('加载公告列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  pagination.current = 1
  loadAnnouncementList()
}

// 重置
const handleReset = () => {
  Object.assign(searchForm, {
    type: null,
    status: null,
    title: ''
  })
  pagination.current = 1
  loadAnnouncementList()
}

// 显示创建对话框
const showCreateDialog = () => {
  isEdit.value = false
  dialogTitle.value = '发布公告'
  resetForm()
  dialogVisible.value = true
}

// 查看公告
const handleView = (row) => {
  currentAnnouncement.value = row
  viewDialogVisible.value = true
}

// 编辑公告
const handleEdit = (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑公告'
  Object.assign(form, {
    id: row.id,
    title: row.title,
    content: row.content,
    type: row.type,
    priority: row.priority,
    isTop: row.isTop === 1,
    isPopup: row.isPopup === 1,
    publishTime: row.publishTime,
    expireTime: row.expireTime
  })
  dialogVisible.value = true
}

// 处理操作
const handleAction = async (command, row) => {
  switch (command) {
    case 'publish':
      await handlePublish(row)
      break
    case 'offline':
      await handleOffline(row)
      break
    case 'delete':
      await handleDelete(row)
      break
  }
}

// 发布公告
const handlePublish = async (row) => {
  try {
    await ElMessageBox.confirm('确定要发布此公告吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    const response = await publishAnnouncement(row.id)
    if (response.code === 1) {
      ElMessage.success('公告发布成功')
      loadAnnouncementList()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('发布公告失败')
    }
  }
}

// 下线公告
const handleOffline = async (row) => {
  try {
    await ElMessageBox.confirm('确定要下线此公告吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    const response = await offlineAnnouncement(row.id)
    if (response.code === 1) {
      ElMessage.success('公告下线成功')
      loadAnnouncementList()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('下线公告失败')
    }
  }
}

// 删除公告
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除此公告吗？删除后无法恢复！', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    const response = await deleteAnnouncement(row.id)
    if (response.code === 1) {
      ElMessage.success('公告删除成功')
      loadAnnouncementList()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除公告失败')
    }
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate()
  submitting.value = true
  
  try {
    const formData = {
      ...form,
      isTop: form.isTop ? 1 : 0,
      isPopup: form.isPopup ? 1 : 0
    }
    
    let response
    if (isEdit.value) {
      response = await updateAnnouncement(formData)
    } else {
      response = await createAnnouncement(formData)
    }
    
    if (response.code === 1) {
      ElMessage.success(isEdit.value ? '公告更新成功' : '公告发布成功')
      dialogVisible.value = false
      loadAnnouncementList()
    }
  } catch (error) {
    ElMessage.error(isEdit.value ? '更新公告失败' : '发布公告失败')
  } finally {
    submitting.value = false
  }
}

// 重置表单
const resetForm = () => {
  Object.assign(form, {
    id: null,
    title: '',
    content: '',
    type: 2,
    priority: 1,
    isTop: false,
    isPopup: false,
    publishTime: null,
    expireTime: null
  })
  if (formRef.value) {
    formRef.value.resetFields()
  }
}

// 分页处理
const handleSizeChange = (size) => {
  pagination.size = size
  pagination.current = 1
  loadAnnouncementList()
}

const handleCurrentChange = (current) => {
  pagination.current = current
  loadAnnouncementList()
}

// 使用设计系统的共享断点，操作列与 Element Plus 网格保持同步。
const { isMobile, screenWidth } = useResponsive()

// 获取操作列宽度
const getOperationColumnWidth = () => {
  if (isMobile.value) {
    return 150 // 移动端使用较小宽度
  } else if (screenWidth.value < 1200) {
    return 180 // 中等屏幕
  } else {
    return 200 // 大屏幕
  }
}

// 组件挂载时加载数据
onMounted(() => {
  loadAnnouncementList()
  // 加载系统变量
  variableStore.loadAllVariables()
})

</script>

<style scoped>
.announcement-management {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  color: var(--text-primary);
}

.search-section {
  background: var(--bg-card);
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
  box-shadow: var(--shadow-sm);
}

.table-section {
  background: var(--bg-card);
  padding: 20px;
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
}

.pagination-section {
  margin-top: 20px;
  text-align: right;
}

.announcement-detail {
  padding: 20px 0;
}

.announcement-detail h3 {
  margin: 0 0 15px 0;
  color: var(--text-primary);
  font-size: 18px;
}

.announcement-meta {
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.meta-text {
  color: var(--text-secondary);
  font-size: 14px;
}

.announcement-content {
  line-height: 1.6;
  color: var(--text-regular);
  white-space: pre-wrap;
}

/* 操作列响应式样式 */
.operation-buttons {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.operation-buttons > div {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.operation-buttons .el-button {
  margin: 0;
  padding: 4px 8px;
  font-size: 12px;
  min-width: auto;
}

/* 移动端操作列优化 */
@media (max-width: 767px) {
  .operation-buttons {
    gap: 2px;
  }

  .operation-buttons .el-button {
    padding: 2px 6px;
    font-size: 11px;
    min-width: 40px;
  }

  .operation-buttons > div {
    gap: 2px;
  }

  /* 移动端隐藏部分次要操作 */
  .secondary-actions {
    display: none;
  }
}

/* 中等屏幕优化 */
@media (min-width: 768px) and (max-width: 1199px) {
  .operation-buttons .el-button {
    padding: 3px 6px;
    font-size: 11px;
  }

  .secondary-actions .el-button {
    font-size: 10px;
    padding: 2px 4px;
  }
}

/* 表格操作列固定宽度优化 */
.operation-column {
  min-width: 150px;
}

@media (min-width: 768px) {
  .operation-column {
    min-width: 180px;
  }
}

@media (min-width: 1200px) {
  .operation-column {
    min-width: 200px;
  }
}
</style>
