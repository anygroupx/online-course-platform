<template>
  <component
    :is="as"
    ref="hostRef"
    class="lg"
    :data-focus-ring="focusRing"
    :data-glass-active="enabled ? 'true' : 'false'"
    :style="computedStyle"
  >
    <span class="lg-surface" aria-hidden="true"></span>
    <span class="lg-light" aria-hidden="true"></span>
    <span class="lg-sheen" aria-hidden="true"></span>
    <div
      class="lg-content"
      :class="contentClass"
      :style="contentStyle"
    >
      <slot />
    </div>
  </component>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { mountLiquidGlass, setLiquidGlassPosition } from './glass-engine.js'
import './liquid-glass.css'

const props = defineProps({
  enabled: {
    type: Boolean,
    default: true
  },
  radius: {
    type: Number,
    default: 40
  },
  refraction: {
    type: Number,
    default: 56
  },
  bevel: {
    type: Number,
    default: 22
  },
  blur: {
    type: Number,
    default: 0.35
  },
  dispersion: {
    type: Number,
    default: 1.2
  },
  tint: {
    type: String,
    default: 'rgba(255,255,255,.018)'
  },
  fallbackBlur: {
    type: Number,
    default: 16
  },
  saturation: {
    type: Number,
    default: 1.08
  },
  mode: {
    type: String,
    default: 'auto',
    validator: (v) => ['auto', 'svg', 'css'].includes(v)
  },
  focusRing: {
    type: String,
    default: 'auto',
    validator: (v) => ['auto', 'none'].includes(v)
  },
  contentClass: {
    type: [String, Object, Array],
    default: ''
  },
  contentStyle: {
    type: [Object, String],
    default: () => ({})
  },
  as: {
    type: String,
    default: 'div'
  }
})

const hostRef = ref(null)
let controller = null
const currentRenderer = ref('css')

const computedStyle = computed(() => {
  return {
    '--lg-radius': `${props.radius}px`,
    '--lg-tint': props.tint
  }
})

const mountController = () => {
  if (!props.enabled || controller) return
  if (!hostRef.value) return
  controller = mountLiquidGlass(hostRef.value, {
    radius: props.radius,
    refraction: props.refraction,
    bevel: props.bevel,
    blur: props.blur,
    dispersion: props.dispersion,
    tint: props.tint,
    fallbackBlur: props.fallbackBlur,
    saturation: props.saturation,
    mode: props.mode
  })
  if (controller) {
    currentRenderer.value = controller.renderer
  }
}

const destroyController = () => {
  if (!controller) return
  controller.destroy()
  controller = null
  currentRenderer.value = 'css'
}

onMounted(mountController)

watch(
  () => props.enabled,
  (enabled) => {
    if (enabled) mountController()
    else destroyController()
  }
)

watch(
  () => [
    props.radius,
    props.refraction,
    props.bevel,
    props.blur,
    props.dispersion,
    props.tint,
    props.fallbackBlur,
    props.saturation,
    props.mode
  ],
  () => {
    if (!controller) return
    controller.update({
      radius: props.radius,
      refraction: props.refraction,
      bevel: props.bevel,
      blur: props.blur,
      dispersion: props.dispersion,
      tint: props.tint,
      fallbackBlur: props.fallbackBlur,
      saturation: props.saturation,
      mode: props.mode
    })
    currentRenderer.value = controller.renderer
  }
)

onBeforeUnmount(() => {
  destroyController()
})

const refresh = () => {
  controller?.refresh()
  if (controller) {
    currentRenderer.value = controller.renderer
  }
}

const setPosition = (x, y) => {
  if (hostRef.value) {
    setLiquidGlassPosition(hostRef.value, x, y)
  }
}

defineExpose({
  hostRef,
  get controller() {
    return controller
  },
  get renderer() {
    return controller ? controller.renderer : currentRenderer.value
  },
  refresh,
  setPosition
})
</script>
