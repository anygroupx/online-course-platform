<template>
  <section class="image-picker">
    <div class="picker-actions">
      <el-button :disabled="disabled || reading" :loading="reading" @click="input?.click()">{{ modelValue ? '更换图片' : '选择图片附件' }}</el-button>
      <el-button v-if="modelValue" :disabled="disabled || reading" @click="clear">移除图片</el-button>
    </div>
    <input ref="input" type="file" accept="image/png,image/jpeg" aria-label="选择工单图片" class="file-input" :disabled="disabled || reading" @change="select" />
    <p class="image-help">可选一张 PNG/JPEG，最多 2MiB、400 万像素。请先遮盖密码、人脸和其他隐私。提交前仅供预览，提交时会自动清除图片元数据。</p>
    <p v-if="error" role="alert" class="image-error">{{ error }}</p>
    <figure v-if="preview"><img :src="preview" alt="待提交附件预览" /><figcaption>尚未上传 · 将与本次工单/回复一起提交</figcaption></figure>
  </section>
</template>
<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { ticketImageFileError } from '@/utils/projectTicketImages'
const props = defineProps({ modelValue: { type: String, default: '' }, disabled: Boolean })
const emit = defineEmits(['update:modelValue', 'busy'])
const input = ref(null), reading = ref(false), preview = ref(''), error = ref('')
let sequence = 0, alive = true, reader
function release() { if (preview.value) URL.revokeObjectURL(preview.value); preview.value = '' }
function clear() { sequence++; reader?.abort(); release(); emit('update:modelValue', ''); if (input.value) input.value.value = ''; error.value = ''; reading.value = false; emit('busy', false) }
async function select(event) {
  const file = event.target.files?.[0]
  if (!file || props.disabled || reading.value) return
  const seq = ++sequence
  release(); emit('update:modelValue', ''); error.value = ''; reading.value = true; emit('busy', true)
  try {
    const problem = ticketImageFileError(file)
    if (problem) throw new Error(problem)
    const header = new Uint8Array(await file.slice(0, 12).arrayBuffer())
    const invalid = ticketImageFileError(file, header)
    if (invalid) throw new Error(invalid)
    if (!alive || seq !== sequence) return
    const data = await new Promise((resolve, reject) => {
      reader = new FileReader()
      reader.onload = () => resolve(reader.result)
      reader.onerror = () => reject(new Error('图片读取失败，请重新选择。'))
      reader.onabort = () => reject(new Error('已取消图片读取。'))
      reader.readAsDataURL(file)
    })
    if (!alive || seq !== sequence) return
    preview.value = URL.createObjectURL(file)
    emit('update:modelValue', data)
  } catch (e) { if (alive && seq === sequence) error.value = e.message || '图片读取失败' }
  finally { if (alive && seq === sequence) { reading.value = false; emit('busy', false); if (input.value) input.value.value = '' } }
}
watch(() => props.modelValue, value => { if (!value && !reading.value) release() })
onBeforeUnmount(() => { alive = false; sequence++; reader?.abort(); release(); emit('busy', false) })
</script>
<style scoped>
.image-picker{margin:16px 0}.picker-actions{display:flex;gap:12px;flex-wrap:wrap}.el-button{min-height:44px;margin:0}.file-input{display:none}.image-help,figcaption{font-size:12px;color:var(--el-text-color-secondary);line-height:1.8}.image-error{font-size:12px;color:var(--el-color-danger)}figure{margin:12px 0}img{display:block;max-width:100%;max-height:220px;object-fit:contain;border-radius:8px;border:1px solid var(--el-border-color);background:var(--el-fill-color-light)}figcaption{margin-top:8px}
</style>
