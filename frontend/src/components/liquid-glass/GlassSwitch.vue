<template>
  <button
    ref="switchRef"
    type="button"
    role="switch"
    :aria-checked="modelValue"
    :disabled="disabled"
    :aria-label="ariaLabel"
    class="lg-switch"
    @click="toggle"
  >
    <span class="lg-switch-track" aria-hidden="true" />
    <span ref="thumbRef" class="lg lg-switch-thumb" aria-hidden="true">
      <span class="lg-surface" aria-hidden="true"></span>
      <span class="lg-light" aria-hidden="true"></span>
      <span class="lg-sheen" aria-hidden="true"></span>
    </span>
  </button>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { mountLiquidGlass } from './glass-engine.js'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  disabled: {
    type: Boolean,
    default: false
  },
  ariaLabel: {
    type: String,
    default: '开关'
  },
  material: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const switchRef = ref(null)
const thumbRef = ref(null)
let controller = null

const toggle = () => {
  if (props.disabled) return
  const next = !props.modelValue
  emit('update:modelValue', next)
  emit('change', next)
}

onMounted(() => {
  if (!thumbRef.value) return
  controller = mountLiquidGlass(thumbRef.value, {
    radius: props.material.radius ?? 99,
    bevel: props.material.bevel ?? 5,
    refraction: props.material.refraction ?? 8,
    blur: props.material.blur ?? 0.2,
    dispersion: props.material.dispersion ?? 0.15,
    tint: props.material.tint ?? 'rgba(255,255,255,.82)',
    mode: props.material.mode ?? 'auto'
  })
})

watch(
  () => props.material,
  (mat) => {
    controller?.update({
      radius: mat.radius ?? 99,
      bevel: mat.bevel ?? 5,
      refraction: mat.refraction ?? 8,
      blur: mat.blur ?? 0.2,
      dispersion: mat.dispersion ?? 0.15,
      tint: mat.tint ?? 'rgba(255,255,255,.82)',
      mode: mat.mode ?? 'auto'
    })
  },
  { deep: true }
)

onBeforeUnmount(() => {
  if (controller) {
    controller.destroy()
    controller = null
  }
})

defineExpose({
  switchRef,
  thumbRef
})
</script>
