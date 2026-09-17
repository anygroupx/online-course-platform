<template>
  <LiquidGlass
    ref="containerRef"
    :radius="material.radius ?? 13"
    :bevel="material.bevel ?? 7"
    :refraction="material.refraction ?? 14"
    :blur="material.blur ?? 0.4"
    :dispersion="material.dispersion ?? 0.25"
    :tint="material.tint ?? 'rgba(255,255,255,.16)'"
    :mode="material.mode ?? 'auto'"
    role="radiogroup"
    :aria-label="ariaLabel"
    :class="['lg-segmented', wrapperClass]"
    @keydown="onKeyDown"
  >
    <button
      v-for="opt in options"
      :key="opt.value"
      type="button"
      role="radio"
      :aria-checked="modelValue === opt.value"
      :disabled="opt.disabled"
      :tabindex="opt.value === focusValue ? 0 : -1"
      @click="selectOption(opt.value)"
    >
      <slot name="label" :option="opt">
        {{ opt.label }}
      </slot>
    </button>
  </LiquidGlass>
</template>

<script setup>
import { ref, computed } from 'vue'
import LiquidGlass from './LiquidGlass.vue'

const props = defineProps({
  modelValue: {
    type: [String, Number],
    required: true
  },
  options: {
    type: Array,
    required: true
    // Array of { value, label, disabled? }
  },
  ariaLabel: {
    type: String,
    default: '选项组'
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

const containerRef = ref(null)

const enabledOptions = computed(() => props.options.filter(o => !o.disabled))

const focusValue = computed(() => {
  if (enabledOptions.value.some(o => o.value === props.modelValue)) {
    return props.modelValue
  }
  return enabledOptions.value[0]?.value
})

const selectOption = (val) => {
  emit('update:modelValue', val)
  emit('change', val)
}

function nextKeyIndex(e, index, length) {
  if (!length) return null
  const backwards = e.key === 'ArrowLeft' || e.key === 'ArrowUp'
  const forwards = e.key === 'ArrowRight' || e.key === 'ArrowDown'
  if (backwards) return (index - 1 + length) % length
  if (forwards) return (index + 1) % length
  if (e.key === 'Home') return 0
  if (e.key === 'End') return length - 1
  return null
}

const onKeyDown = (e) => {
  const host = containerRef.value?.hostRef
  if (!host) return
  const buttons = Array.from(host.querySelectorAll('button[role="radio"]:not(:disabled)'))
  const i = buttons.indexOf(document.activeElement)
  if (i < 0) return
  const next = nextKeyIndex(e, i, buttons.length)
  if (next === null) return
  e.preventDefault()
  buttons[next].focus()
  const targetOption = enabledOptions.value[next]
  if (targetOption) {
    selectOption(targetOption.value)
  }
}

defineExpose({
  containerRef
})
</script>
