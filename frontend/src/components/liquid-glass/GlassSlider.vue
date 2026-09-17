<template>
  <div
    class="lg-range"
    :class="wrapperClass"
    :style="{ '--p': progress }"
  >
    <div class="lg-range-track" aria-hidden="true">
      <div class="lg-range-fill" />
    </div>
    <LiquidGlass
      class="lg-range-thumb"
      :radius="material.radius ?? 99"
      :bevel="material.bevel ?? 5"
      :refraction="material.refraction ?? 9"
      :blur="material.blur ?? 0.25"
      :dispersion="material.dispersion ?? 0.18"
      :tint="material.tint ?? 'rgba(255,255,255,.44)'"
      :mode="material.mode ?? 'auto'"
      aria-hidden="true"
    />
    <input
      ref="inputRef"
      type="range"
      :min="min"
      :max="max"
      :step="step"
      :value="safeValue"
      :disabled="disabled"
      :aria-label="ariaLabel"
      @input="onInput"
    />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import LiquidGlass from './LiquidGlass.vue'

const props = defineProps({
  modelValue: {
    type: Number,
    required: true
  },
  min: {
    type: Number,
    default: 0
  },
  max: {
    type: Number,
    default: 100
  },
  step: {
    type: Number,
    default: 1
  },
  disabled: {
    type: Boolean,
    default: false
  },
  ariaLabel: {
    type: String,
    default: '滑块'
  },
  wrapperClass: {
    type: [String, Object, Array],
    default: ''
  },
  material: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const inputRef = ref(null)

if (!Number.isFinite(props.min) || !Number.isFinite(props.max) || props.max <= props.min) {
  throw new Error('GlassSlider requires finite min < max.')
}
if (!Number.isFinite(props.step) || props.step <= 0) {
  throw new Error('GlassSlider step must be positive.')
}

const safeValue = computed(() => {
  if (!Number.isFinite(props.modelValue)) return props.min
  return Math.max(props.min, Math.min(props.max, props.modelValue))
})

const progress = computed(() => {
  const range = props.max - props.min
  if (range <= 0) return 0
  return (safeValue.value - props.min) / range
})

const onInput = (e) => {
  const val = e.target.valueAsNumber
  emit('update:modelValue', val)
  emit('change', val)
}

defineExpose({
  inputRef
})
</script>
