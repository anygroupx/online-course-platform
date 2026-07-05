<template>
  <div class="platform-categories-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>平台分类管理</span>
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon>
            添加分类
          </el-button>
        </div>
      </template>

      <el-table :data="tableData" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="分类名称" width="200" />
        <el-table-column prop="sortOrder" label="排序" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">
              {{ scope.row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" show-overflow-tooltip />
        <el-table-column label="操作" width="350" fixed="right">
          <template #default="scope">
            <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button size="small" type="warning" @click="handleDeletePlatforms(scope.row)">批量删除课程</el-button>
            <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
            <el-button size="small" type="danger" plain @click="handleCascadeDelete(scope.row)">级联删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next"
        class="pagination"
        @current-change="loadData"
        @size-change="loadData"
      />
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" append-to-body>
      <el-form :model="form" label-width="100px">
        <el-form-item label="分类名称">
          <el-input v-model="form.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from '@/utils/request'

const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('添加分类')
const form = ref({
  name: '',
  sortOrder: 0,
  status: 1
})

const loadData = async () => {
  try {
    const res = await axios.get('/admin/platform-categories', {
      params: {
        page: currentPage.value,
        pageSize: pageSize.value
      }
    })
    if (res.code === 1) {
      tableData.value = res.data.records
      total.value = res.data.total
    }
  } catch (error) {
    console.error('加载数据失败：', error)
  }
}

const handleCreate = () => {
  dialogTitle.value = '添加分类'
  form.value = {
    name: '',
    sortOrder: 0,
    status: 1
  }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑分类'
  form.value = { ...row }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  try {
    if (form.value.id) {
      await axios.put('/admin/platform-categories', form.value)
      ElMessage.success('更新成功')
    } else {
      await axios.post('/admin/platform-categories', form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (error) {
    console.error('提交失败：', error)
    ElMessage.error('提交失败')
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除这个分类吗？', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await axios.delete(`/admin/platform-categories/${row.id}`)
    ElMessage.success('删除成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败：', error)
      ElMessage.error('删除失败')
    }
  }
}

// 方案A：批量删除某分类下的所有课程平台
const handleDeletePlatforms = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除分类"${row.name}"下的所有课程平台吗？此操作不会删除分类本身。`,
      '批量删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    const res = await axios.delete(`/admin/platform-categories/${row.id}/platforms`)
    if (res.code === 1) {
      ElMessage.success(`成功删除 ${res.data.deletedCount} 个课程平台`)
      loadData()
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('批量删除课程平台失败：', error)
      ElMessage.error('批量删除失败')
    }
  }
}

// 方案B：级联删除分类及其下所有课程平台
const handleCascadeDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要级联删除分类"${row.name}"及其下的所有课程平台吗？此操作将同时删除分类和所有相关课程！`,
      '级联删除警告',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'error',
        distinguishCancelAndClose: true
      }
    )
    
    const res = await axios.delete(`/admin/platform-categories/${row.id}/cascade`)
    if (res.code === 1) {
      ElMessage.success(`成功删除分类及其 ${res.data.deletedPlatformsCount} 个课程平台`)
      loadData()
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('级联删除失败：', error)
      ElMessage.error('级联删除失败')
    }
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.platform-categories-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
