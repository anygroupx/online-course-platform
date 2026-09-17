import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { mountLiquidGlass, setLiquidGlassPosition } from './glass-engine.js'

/**
 * Vue composable to attach Liquid Glass optical engine to any target element.
 * @param {import('vue').Ref<HTMLElement | null>} targetRef
 * @param {import('./glass-engine.js').GlassOptions} initialOptions
 */
export function useLiquidGlass(targetRef, initialOptions = {}) {
  let controller = null
  const renderer = ref('css')

  onMounted(() => {
    if (!targetRef.value) return
    controller = mountLiquidGlass(targetRef.value, initialOptions)
    if (controller) {
      renderer.value = controller.renderer
    }
  })

  const update = (options) => {
    controller?.update(options)
    if (controller) {
      renderer.value = controller.renderer
    }
  }

  const refresh = () => {
    controller?.refresh()
    if (controller) {
      renderer.value = controller.renderer
    }
  }

  const setPosition = (x, y) => {
    if (targetRef.value) {
      setLiquidGlassPosition(targetRef.value, x, y)
    }
  }

  onBeforeUnmount(() => {
    if (controller) {
      controller.destroy()
      controller = null
    }
  })

  return {
    renderer,
    update,
    refresh,
    setPosition,
    get controller() {
      return controller
    }
  }
}
