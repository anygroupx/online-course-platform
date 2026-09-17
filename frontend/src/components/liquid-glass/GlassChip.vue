<template>
  <LiquidGlass
    :radius="material.radius ?? 99"
    :bevel="material.bevel ?? 6"
    :refraction="material.refraction ?? 11"
    :blur="material.blur ?? 0.3"
    :dispersion="material.dispersion ?? 0.2"
    :tint="material.tint ?? 'rgba(255,255,255,.17)'"
    :mode="material.mode ?? 'auto'"
    :class="['lg-chip', wrapperClass]"
  >
    <button
      ref="buttonRef"
      type="button"
      :aria-pressed="selected"
      :disabled="disabled"
      @click="toggle"
    >
      <svg
        class="chip-check"
        viewBox="0 0 24 24"
        aria-hidden="true"
      >
        <path d="m5 12 4 4L19 6" />
      </svg>
      <slot />
    </button>
  </LiquidGlass>
</template>

<script setup>
import { ref } from 'vue'
import LiquidGlass from './LiquidGlass.vue'

const props = defineProps({
  selected: {
    type: Boolean,
    default: false
  },
  disabled: {
    type: Boolean,
    default: false
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

const emit = defineEmits(['update:selected', 'change'])

const buttonRef = ref(null)

const toggle = () => {
  if (props.disabled) return
  const next = !props.selected
  emit('update:selected', next)
  emit('change', next)
}

defineExpose({
  buttonRef
})
</script>
