<template>
  <div class="form-grid self-operated-heisha-fields">
    <el-form-item label="服务账号 / 手机号">
      <el-input v-model="model.account" autocomplete="off" maxlength="100" />
    </el-form-item>
    <el-form-item label="服务密码">
      <el-input v-model="model.password" type="password" show-password autocomplete="new-password" maxlength="200" />
    </el-form-item>
    <el-form-item label="学校名称（选填）">
      <el-input v-model="model.schoolName" maxlength="120" />
    </el-form-item>
    <el-form-item label="期望执行时间">
      <el-time-select v-model="model.runTime" start="00:00" end="23:55" step="00:05" />
    </el-form-item>
    <el-form-item label="补充说明（选填）" class="full">
      <el-input v-model="model.note" type="textarea" maxlength="1000" :rows="2" />
    </el-form-item>
  </div>
</template>
<script setup>
const model = defineModel({ type: Object, required: true });
</script>
