<template>
  <section class="face-material">
    <el-alert type="warning" :closable="false"
      title="人脸资料仅用于当前订单的人工资格核验和履约，属于敏感资料；仅授权工作人员处理当前订单时访问，不用于营销或其他用途，订单处理结束后按保留策略删除。" />
    <el-checkbox v-model="authorized">我单独授权提交并处理本次人脸资格核验材料</el-checkbox>
    <input ref="picker" type="file" accept="image/png,image/jpeg" :disabled="disabled || !authorized" aria-label="人脸资格核验图片" @change="choose" />
    <img v-if="previewUrl" :src="previewUrl" alt="待提交的人脸资格核验材料预览" />
    <el-button v-if="file" type="primary" plain :loading="uploading" :disabled="disabled || !authorized" @click="upload">安全上传材料</el-button>
    <p v-if="draftId" role="status">材料已安全暂存，可继续预览订单。</p>
    <p v-if="error" role="alert">{{ error }}</p>
  </section>
</template>
<script setup>
import { ref, watch, onBeforeUnmount, onDeactivated } from 'vue';
import { createFulfillmentMaterialDraft, createOrderFulfillmentMaterialDraft } from '@/api/serviceCommerce';
import { authSessionScope } from '@/utils/authSession';
const props = defineProps({ product: Object, orderId: { type: String, default: '' }, disabled: Boolean });
const authorized = defineModel('authorized', { type: Boolean, default: false });
const draftId = defineModel('draftId', { type: String, default: '' });
const picker = ref(null), file = ref(null), previewUrl = ref(''), uploading = ref(false), error = ref('');
let generation = 0, reader = null;
function clearPreview() { if (previewUrl.value) URL.revokeObjectURL(previewUrl.value); previewUrl.value = ''; file.value = null; if (picker.value) picker.value.value = ''; }
function clear() {
  generation += 1;
  if (reader?.readyState === FileReader.LOADING) reader.abort();
  reader = null;
  clearPreview(); draftId.value = ''; error.value = ''; uploading.value = false;
}
async function validHeader(value) {
  const bytes = new Uint8Array(await value.slice(0, 12).arrayBuffer());
  const png = bytes.length >= 8 && [137, 80, 78, 71, 13, 10, 26, 10].every((byte, index) => bytes[index] === byte);
  const jpeg = bytes.length >= 3 && bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255;
  return png || jpeg;
}
async function choose(event) {
  // Capture the File before resetting the native input; clearing it also clears event.target.files.
  const value = event.target.files?.[0];
  clear();
  const current = generation;
  if (!value || !authorized.value || props.disabled) return;
  if (!['image/png', 'image/jpeg'].includes(value.type) || value.size < 16 || value.size > 2 * 1024 * 1024) { error.value = '请选择不超过 2 MiB 的 PNG 或 JPEG 图片。'; return; }
  try {
    const valid = await validHeader(value);
    if (current !== generation || !authorized.value || props.disabled) return;
    if (!valid) { error.value = '图片内容与格式不一致，请重新选择 PNG 或 JPEG 图片。'; return; }
    file.value = value; previewUrl.value = URL.createObjectURL(value);
  } catch { if (current === generation) error.value = '无法读取图片，请重新选择。'; }
}
function dataUrl(value) {
  return new Promise((resolve, reject) => {
    const input = new FileReader();
    reader = input;
    input.onload = () => { reader = null; resolve(input.result); };
    input.onerror = input.onabort = () => { reader = null; reject(new Error('image read cancelled')); };
    input.readAsDataURL(value);
  });
}
async function upload() {
  if (!file.value || !authorized.value || props.disabled || uploading.value) return;
  const current = generation;
  const productId = props.product?.id, productVersion = props.product?.version, orderId = props.orderId;
  uploading.value = true; error.value = '';
  try {
    const imageData = await dataUrl(file.value);
    if (current !== generation || !authorized.value) return;
    const result = orderId
      ? await createOrderFulfillmentMaterialDraft(orderId, { imageData, authorizedBiometric: true })
      : await createFulfillmentMaterialDraft({ productId, productVersion, imageData, authorizedBiometric: true });
    if (current !== generation || !authorized.value) return;
    draftId.value = result.id; clearPreview();
  } catch { if (current === generation) error.value = '材料上传未完成，请重新选择图片后重试。'; }
  finally { if (current === generation) uploading.value = false; }
}
watch(authorized, (value) => { if (!value) clear(); }, { flush: 'sync' });
watch(() => [props.product?.id, props.product?.version, props.orderId], clear, { flush: 'sync' });
watch(authSessionScope, () => { clear(); authorized.value = false; }, { flush: 'sync' });
onDeactivated(clear);
onBeforeUnmount(clear);
</script>
<style scoped>
.face-material { display: grid; gap: 14px; }
.face-material img { max-width: 260px; max-height: 220px; object-fit: contain; border-radius: 8px; }
.face-material :deep(.el-checkbox__label) { white-space: normal; line-height: 1.7; }
</style>
