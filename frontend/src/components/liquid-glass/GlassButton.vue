<template>
  <LiquidGlass
    :radius="computedRadius"
    :bevel="material.bevel ?? 7"
    :refraction="material.refraction ?? 15"
    :blur="material.blur ?? 0.35"
    :dispersion="material.dispersion ?? 0.25"
    :tint="material.tint ?? 'rgba(255,255,255,.21)'"
    :mode="material.mode ?? 'auto'"
    :class="['lg-button', wrapperClass]"
    :data-size="size"
    :data-variant="variant"
    as="div"
  >
    <button
      ref="buttonRef"
      :type="type"
      :disabled="disabled"
      :aria-pressed="pressed"
      :aria-label="ariaLabel"
      @click="$emit('click', $event)"
    >
      <slot />
    </button>
  </LiquidGlass>
</template>

<script setup>
import { ref, computed } from 'vue'
import LiquidGlass from './LiquidGlass.vue'

const props = defineProps({
  size: {
    type: String,
    default: 'md',
    validator: (v) => ['sm', 'md', 'lg', 'icon'].includes(v)
  },
  variant: {
    type: String,
    default: 'default',
    validator: (v) => ['default', 'accent'].includes(v)
  },
  type: {
    type: String,
    default: 'button'
  },
  disabled: {
    type: Boolean,
    default: false
  },
  pressed: {
    type: Boolean,
    default: undefined
  },
  ariaLabel: {
    type: String,
    default: undefined
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

defineEmits(['click'])

const buttonRef = ref(null)

const computedRadius = computed(() => {
  if (props.material.radius !== undefined) return props.material.radius
  return props.size === 'icon' ? 99 : 12
})

defineExpose({
  buttonRef
})
</script>
