<template>
  <section v-if="imageId" class="ticket-image-viewer">
    <div class="image-actions">
      <el-button v-if="!url" :loading="loading" @click="view">查看私有图片附件</el-button>
      <el-button v-else @click="clear">收起并清除图片</el-button>
      <small>仅按需读取，不加载网络图片</small>
    </div>
    <p v-if="error" role="alert">{{ error }}</p>
    <img v-if="url" :src="url" alt="已清除元数据的工单附件" referrerpolicy="no-referrer" />
  </section>
</template>
<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { clientTicketImage } from '@/api/projectClientTickets'
const props = defineProps({ imageId: { type: String, default: null }, fetcher: { type: Function, default: clientTicketImage } })
const url = ref(''), loading = ref(false), error = ref('')
let alive = true, generation = 0
function clear() { generation++; if (url.value) URL.revokeObjectURL(url.value); url.value = ''; loading.value = false; error.value = '' }
async function view() {
  if (loading.value || !props.imageId) return
  const g = generation, id = props.imageId
  loading.value = true; error.value = ''
  try {
    const blob = await props.fetcher(id)
    if (!alive || g !== generation || id !== props.imageId) return
    if (!(blob instanceof Blob) || blob.type !== 'image/png' || blob.size < 1 || blob.size > 4 * 1024 * 1024) throw new Error('invalid image')
    url.value = URL.createObjectURL(blob)
  } catch { if (alive && g === generation) error.value = '附件读取失败或权限已失效，请重新核实；不会加载网络内容。' }
  finally { if (alive && g === generation) loading.value = false }
}
watch(() => props.imageId, clear)
onBeforeUnmount(() => { alive = false; clear() })
</script>
<style scoped>
.ticket-image-viewer{margin:16px 0}.image-actions{display:flex;gap:12px;align-items:center;flex-wrap:wrap}.el-button{min-height:44px;margin:0}small{font-size:11px;color:var(--el-text-color-secondary)}img{display:block;margin-top:14px;max-width:100%;max-height:420px;object-fit:contain;background:var(--el-fill-color-light);border:1px solid var(--el-border-color);border-radius:8px}p{font-size:12px;line-height:1.8;color:var(--el-color-warning)}
</style>
