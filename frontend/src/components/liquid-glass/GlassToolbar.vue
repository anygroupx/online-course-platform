<template>
  <LiquidGlass
    ref="containerRef"
    :radius="material.radius ?? 14"
    :bevel="material.bevel ?? 8"
    :refraction="material.refraction ?? 16"
    :blur="material.blur ?? 0.4"
    :dispersion="material.dispersion ?? 0.3"
    :tint="material.tint ?? 'rgba(255,255,255,.16)'"
    :mode="material.mode ?? 'auto'"
    role="toolbar"
    :aria-label="ariaLabel"
    :class="['lg-toolbar', wrapperClass]"
    @focusin="onFocusIn"
    @keydown="onKeyDown"
  >
    <slot />
  </LiquidGlass>
</template>

<script setup>
import { ref } from 'vue'
import LiquidGlass from './LiquidGlass.vue'

const props = defineProps({
  ariaLabel: {
    type: String,
    default: '工具栏'
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

const containerRef = ref(null)

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

const onFocusIn = (e) => {
  const host = containerRef.value?.hostRef
  if (!host) return
  const active = e.target
  if (active.tagName !== 'BUTTON') return
  const buttons = Array.from(host.querySelectorAll('button:not(:disabled)'))
  buttons.forEach(b => {
    b.tabIndex = b === active ? 0 : -1
  })
}

const onKeyDown = (e) => {
  const host = containerRef.value?.hostRef
  if (!host) return
  const buttons = Array.from(host.querySelectorAll('button:not(:disabled)'))
  const i = buttons.indexOf(document.activeElement)
  if (i < 0) return
  const next = nextKeyIndex(e, i, buttons.length)
  if (next === null) return
  e.preventDefault()
  buttons[next].focus()
}

defineExpose({
  containerRef
})
</script>
