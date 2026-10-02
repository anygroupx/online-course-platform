<template>
  <main class="rbac-page">
    <h1>角色与权限</h1>
    <p>查看各角色可以执行的操作，为用户分配一个或多个角色。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-button :loading="loading" :disabled="busy" @click="load">刷新权限矩阵</el-button>
    <el-table :data="matrix" v-loading="loading">
      <el-table-column prop="code" label="角色" width="180" />
      <el-table-column label="权限">
        <template #default="{ row }">
          <el-tag v-for="permission in row.permissions" :key="permission">{{ permission }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
    <el-form label-position="top" @submit.prevent="readRoles">
      <el-form-item label="用户编号">
        <el-input v-model="uid" :disabled="busy" autocomplete="off" />
      </el-form-item>
      <el-button :disabled="busy || !uid.trim()" @click="readRoles">查询用户角色</el-button>
      <template v-if="loadedUid === uid.trim() && loadedUid">
        <el-form-item label="分配角色">
          <el-checkbox-group v-model="selected" :disabled="busy">
            <el-checkbox v-for="row in matrix" :key="row.code" :value="row.code">{{ row.code }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-button type="primary" :loading="busy" :disabled="!selected.length" @click="save">保存角色</el-button>
      </template>
    </el-form>
  </main>
</template>

<script setup>
import { ref, watch, onMounted, onActivated, onDeactivated, onBeforeUnmount } from 'vue';
import { ElMessageBox, ElMessage } from 'element-plus';
import request from '@/utils/request';
import { authSessionScope, sessionUserInfo } from '@/utils/authSession';
import { hasPermission } from '@/utils/permissions';

const matrix = ref([]);
const selected = ref([]);
const uid = ref('');
const loadedUid = ref('');
const loading = ref(false);
const busy = ref(false);
const error = ref('');
let generation = 0;

function clear() {
  generation++;
  matrix.value = [];
  selected.value = [];
  loadedUid.value = '';
  uid.value = '';
  busy.value = false;
  loading.value = false;
  error.value = '';
}

async function load() {
  if (loading.value || busy.value || !hasPermission(sessionUserInfo.value, 'rbac:manage')) return;
  const scope = generation;
  loading.value = true;
  error.value = '';
  try {
    const response = await request.get('/admin/rbac/matrix', { suppressGlobalError: true });
    if (scope === generation) matrix.value = response.data;
  } catch {
    if (scope === generation) error.value = '无法读取权限矩阵，请确认权限后重试。';
  } finally {
    if (scope === generation) loading.value = false;
  }
}

async function readRoles() {
  const target = uid.value.trim();
  if (busy.value || !target || !hasPermission(sessionUserInfo.value, 'rbac:manage')) return;
  const scope = generation;
  busy.value = true;
  loadedUid.value = '';
  selected.value = [];
  error.value = '';
  try {
    const response = await request.get(`/admin/rbac/users/${encodeURIComponent(target)}/roles`, { suppressGlobalError: true });
    if (scope === generation && uid.value.trim() === target) {
      selected.value = response.data;
      loadedUid.value = target;
    }
  } catch {
    if (scope === generation) error.value = '无法查询用户角色，请检查用户编号后重试。';
  } finally {
    if (scope === generation) busy.value = false;
  }
}

async function save() {
  const target = loadedUid.value;
  if (!target || target !== uid.value.trim() || busy.value || !selected.value.length
      || !hasPermission(sessionUserInfo.value, 'rbac:manage')) return;
  const scope = generation;
  const roles = [...selected.value];
  busy.value = true;
  error.value = '';
  try {
    try {
      await ElMessageBox.confirm('保存后，用户的访问权限将在后续请求中生效。是否继续？', '确认角色变更', { type: 'warning' });
    } catch {
      return;
    }
    if (scope !== generation || target !== uid.value.trim()) return;
    // Never automatically replay a privileged mutation following token refresh.
    const response = await request.put(`/admin/rbac/users/${encodeURIComponent(target)}/roles`, { roles }, {
      __sessionRetry: true,
      suppressGlobalError: true,
    });
    if (scope === generation) {
      selected.value = response.data;
      ElMessage.success('角色已更新');
    }
  } catch {
    if (scope === generation) error.value = '角色未更新，请确认可分配的角色后重试。';
  } finally {
    if (scope === generation) busy.value = false;
  }
}

watch(uid, () => {
  selected.value = [];
  loadedUid.value = '';
});
watch(authSessionScope, clear, { flush: 'sync' });
onMounted(load);
onActivated(load);
onDeactivated(clear);
onBeforeUnmount(clear);
</script>

<style scoped>
.rbac-page { display: grid; gap: 18px; max-width: 1100px; }
.el-tag { margin: 4px; }
.el-form { max-width: 620px; }
p { color: var(--el-text-color-secondary); }
</style>
