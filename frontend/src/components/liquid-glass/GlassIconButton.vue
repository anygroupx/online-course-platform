<template>
  <GlassButton
    v-bind="$attrs"
    size="icon"
    :aria-label="ariaLabel"
    :disabled="disabled"
    :variant="variant"
    :material="material"
    @click="$emit('click', $event)"
  >
    <slot />
  </GlassButton>
</template>

<script setup>
import GlassButton from './GlassButton.vue'

defineProps({
  ariaLabel: {
    type: String,
    required: true
  },
  disabled: {
    type: Boolean,
    default: false
  },
  variant: {
    type: String,
    default: 'default'
  },
  material: {
    type: Object,
    default: () => ({})
  }
})

defineEmits(['click'])
</script>
