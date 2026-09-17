<template>
  <div class="lgw-shell">
    <header class="lgw-top">
      <div class="lgw-wordmark">
        <span aria-hidden="true"></span>
        GLASS / MATERIALS
      </div>
      <div class="lgw-top-actions">
        <el-button
          size="small"
          round
          :type="isCurrentThemeLiquidGlass ? 'success' : 'primary'"
          @click="toggleGlobalLiquidGlass"
        >
          {{ isCurrentThemeLiquidGlass ? '当前已应用液态玻璃' : '应用为系统主题' }}
        </el-button>
        <code>REFERENCE / 17188B0</code>
      </div>
    </header>

    <div class="lgw-heading">
      <div>
        <h1>Liquid Glass</h1>
        <p>真实背景、克制折射、清晰前景。</p>
      </div>
      <span class="lgw-status">
        <i :class="statusClass"></i>
        <span>{{ statusText }}</span>
      </span>
    </div>

    <main>
      <div class="lgw-workspace">
        <section class="lgw-viewport">
          <div class="lgw-toolbar">
            <span>LIVE BACKDROP</span>
            <div class="lgw-segments" role="radiogroup" aria-label="背景模式">
              <button
                v-for="b in backdropOptions"
                :key="b.key"
                type="button"
                role="radio"
                :aria-checked="backdrop === b.key"
                :aria-pressed="backdrop === b.key"
                @click="backdrop = b.key"
              >
                {{ b.label }}
              </button>
            </div>
          </div>

          <div
            ref="stageRef"
            class="lgw-stage"
            :data-backdrop="backdrop"
          >
            <div class="lgw-backdrop" aria-hidden="true">
              <div class="lgw-grid"></div>
              <div class="lgw-back-title">Clarity.</div>
              <div class="lgw-band a"></div>
              <div class="lgw-band b"></div>
              <div class="lgw-band c"></div>
              <div class="lgw-text">
                <strong>See through.</strong>
                <p>
                  这不是玻璃里的一张图片。<br>
                  文字与色带都是背后的真实 DOM。
                </p>
                <small>LIGHT / SURFACE / REFRACTION</small>
              </div>
            </div>

            <LiquidGlass
              ref="lensRef"
              class="lgw-lens"
              :data-shape="shape"
              :radius="shapeRadius"
              :refraction="params.refraction"
              :bevel="params.bevel"
              :blur="params.blur"
              :dispersion="params.dispersion"
              :mode="mode"
              focusRing="none"
              tabindex="0"
              role="group"
              aria-label="可移动玻璃镜片"
              @pointerdown="onPointerDown"
              @pointermove="onPointerMove"
              @pointerup="onPointerUp"
              @keydown="onKeyDown"
            >
              <div class="lgw-lens-top">
                <span>LIQUID / 01</span>
                <span>✥</span>
              </div>
              <div class="lgw-lens-word">Transparent.</div>
              <div class="lgw-lens-bottom">
                <span>拖动镜片，看边缘</span>
                <span>→</span>
              </div>
            </LiquidGlass>
          </div>
        </section>

        <aside class="lgw-inspector">
          <div class="lgw-inspector-title">
            <b>材质参数</b>
            <button type="button" @click="resetParams">重置</button>
          </div>

          <div class="lgw-segments" role="radiogroup" aria-label="渲染方式">
            <button
              type="button"
              role="radio"
              :aria-checked="mode === 'auto'"
              :aria-pressed="mode === 'auto'"
              @click="mode = 'auto'"
            >
              折射玻璃 (Auto)
            </button>
            <button
              type="button"
              role="radio"
              :aria-checked="mode === 'css'"
              :aria-pressed="mode === 'css'"
              @click="mode = 'css'"
            >
              仅毛玻璃 (CSS)
            </button>
          </div>

          <label class="lgw-field">
            <span>组件形状</span>
            <div class="lgw-segments" role="radiogroup" aria-label="形状">
              <button
                v-for="s in shapeOptions"
                :key="s.key"
                type="button"
                role="radio"
                :aria-checked="shape === s.key"
                :aria-pressed="shape === s.key"
                @click="shape = s.key"
              >
                {{ s.label }}
              </button>
            </div>
          </label>

          <label class="lgw-field">
            <span>
              折射强度
              <output>{{ params.refraction }}</output>
            </span>
            <input
              type="range"
              :min="0"
              :max="90"
              :step="1"
              :value="params.refraction"
              :style="{ '--pct': `${(params.refraction / 90) * 100}%` }"
              @input="params.refraction = $event.target.valueAsNumber"
            />
          </label>

          <label class="lgw-field">
            <span>
              曲面边缘
              <output>{{ params.bevel }} px</output>
            </span>
            <input
              type="range"
              :min="5"
              :max="40"
              :step="1"
              :value="params.bevel"
              :style="{ '--pct': `${((params.bevel - 5) / 35) * 100}%` }"
              @input="params.bevel = $event.target.valueAsNumber"
            />
          </label>

          <label class="lgw-field">
            <span>
              模糊程度
              <output>{{ params.blur.toFixed(2) }}</output>
            </span>
            <input
              type="range"
              :min="0"
              :max="12"
              :step="0.05"
              :value="params.blur"
              :style="{ '--pct': `${(params.blur / 12) * 100}%` }"
              @input="params.blur = $event.target.valueAsNumber"
            />
          </label>

          <label class="lgw-field">
            <span>
              色散
              <output>{{ params.dispersion.toFixed(2) }}</output>
            </span>
            <input
              type="range"
              :min="0"
              :max="4"
              :step="0.1"
              :value="params.dispersion"
              :style="{ '--pct': `${(params.dispersion / 4) * 100}%` }"
              @input="params.dispersion = $event.target.valueAsNumber"
            />
          </label>

          <button
            type="button"
            class="lgw-apply-theme-btn"
            @click="applyAsSystemTheme"
          >
            应用液态玻璃为全局主题
          </button>

          <p class="lgw-note">
            增强折射在桌面 Chromium 启用；其他环境自动降级。
          </p>
        </aside>
      </div>

      <section class="lgw-samples">
        <div class="lgw-samples-title">
          <b>实际控件</b>
          <span>更轻、更小 · 同一材质体系</span>
        </div>
        <div class="lgw-sample-grid">
          <!-- 01 / 按钮 -->
          <article>
            <header>01 / 按钮</header>
            <div class="lgw-row">
              <GlassButton size="sm">次要</GlassButton>
              <GlassButton>继续 →</GlassButton>
              <GlassButton variant="accent">主要操作</GlassButton>
            </div>
          </article>

          <!-- 02 / 开关 -->
          <article>
            <header>02 / 开关</header>
            <div class="lgw-row">
              <span>接收通知</span>
              <GlassSwitch
                v-model="notify"
                aria-label="接收通知"
              />
            </div>
          </article>

          <!-- 03 / 滑块 -->
          <article>
            <header>03 / 滑块 ({{ brightness }}%)</header>
            <div style="width: 100%; padding: 0 10px;">
              <GlassSlider
                v-model="brightness"
                aria-label="亮度"
                :min="0"
                :max="100"
              />
            </div>
          </article>

          <!-- 04 / 分段 -->
          <article>
            <header>04 / 分段</header>
            <div>
              <GlassSegmented
                v-model="segment"
                aria-label="内容范围"
                :options="segmentOptions"
              />
            </div>
          </article>

          <!-- 05 / 标签 -->
          <article>
            <header>05 / 标签</header>
            <div class="lgw-row">
              <GlassChip
                v-for="tag in availableTags"
                :key="tag"
                :selected="selectedTags.includes(tag)"
                @update:selected="toggleTag(tag, $event)"
              >
                {{ tag }}
              </GlassChip>
            </div>
          </article>

          <!-- 06 / 工具栏 -->
          <article>
            <header>06 / 工具栏</header>
            <div>
              <GlassToolbar aria-label="编辑工具">
                <button
                  v-for="t in toolItems"
                  :key="t.id"
                  type="button"
                  :aria-label="t.label"
                  :aria-pressed="currentTool === t.id"
                  @click="currentTool = t.id"
                >
                  {{ t.icon }}
                </button>
                <GlassToolbarSeparator />
                <button
                  type="button"
                  aria-label="重置工具"
                  @click="currentTool = 'cursor'"
                >
                  ↶
                </button>
              </GlassToolbar>
            </div>
          </article>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import {
  LiquidGlass,
  GlassButton,
  GlassSwitch,
  GlassSlider,
  GlassSegmented,
  GlassChip,
  GlassToolbar,
  GlassToolbarSeparator,
  setLiquidGlassPosition
} from '@/components/liquid-glass'
import { useThemeStore } from '@/stores/theme'
import '@/styles/reference-workbench.css'

const themeStore = useThemeStore()
const isCurrentThemeLiquidGlass = computed(() => themeStore.currentThemeName === 'liquid-glass')

const stageRef = ref(null)
const lensRef = ref(null)

const backdropOptions = [
  { key: 'spectrum', label: '色带' },
  { key: 'grid', label: '网格' },
  { key: 'text', label: '文字' }
]
const backdrop = ref('spectrum')

const shapeOptions = [
  { key: 'card', label: '卡片' },
  { key: 'pill', label: '胶囊' },
  { key: 'circle', label: '圆形' }
]
const shape = ref('card')

const shapeRadius = computed(() => (shape.value === 'card' ? 40 : 999))

const mode = ref('auto')

const DEFAULT_PARAMS = {
  refraction: 56,
  bevel: 22,
  blur: 0.35,
  dispersion: 1.2
}

const params = reactive({ ...DEFAULT_PARAMS })

const resetParams = () => {
  Object.assign(params, DEFAULT_PARAMS)
  mode.value = 'auto'
  shape.value = 'card'
  nextTick(centerLens)
}

// Controls state
const notify = ref(true)
const brightness = ref(48)
const segment = ref('recent')
const segmentOptions = [
  { value: 'recent', label: '最近' },
  { value: 'saved', label: '收藏' },
  { value: 'all', label: '全部' }
]

const availableTags = ['设计', '开发', '灵感']
const selectedTags = ref(['设计'])

const toggleTag = (tag, on) => {
  if (on) {
    if (!selectedTags.value.includes(tag)) selectedTags.value.push(tag)
  } else {
    selectedTags.value = selectedTags.value.filter(t => t !== tag)
  }
}

const toolItems = [
  { id: 'cursor', label: '指针', icon: '↖' },
  { id: 'pen', label: '画笔', icon: '✎' },
  { id: 'crop', label: '裁剪', icon: '⌗' }
]
const currentTool = ref('cursor')

// Dragging & Positioning
const pos = ref({ x: 0, y: 0 })
let dragInfo = null
let resizeObserver = null

const centerLens = () => {
  const stage = stageRef.value
  const lensHost = lensRef.value?.hostRef
  if (!stage || !lensHost) return
  const x = (stage.clientWidth - lensHost.offsetWidth) * 0.57
  const y = (stage.clientHeight - lensHost.offsetHeight) * 0.57
  pos.value = { x, y }
  setLiquidGlassPosition(lensHost, x, y)
}

const moveLens = (x, y) => {
  const stage = stageRef.value
  const lensHost = lensRef.value?.hostRef
  if (!stage || !lensHost) return
  const maxX = stage.clientWidth - lensHost.offsetWidth - 12
  const maxY = stage.clientHeight - lensHost.offsetHeight - 12
  const nextX = Math.max(12, Math.min(x, maxX))
  const nextY = Math.max(12, Math.min(y, maxY))
  pos.value = { x: nextX, y: nextY }
  setLiquidGlassPosition(lensHost, nextX, nextY)
}

const onPointerDown = (e) => {
  if (e.button !== 0 || !e.isPrimary) return
  if (e.target.closest('button, input, a, textarea, select')) return
  e.preventDefault()
  dragInfo = {
    id: e.pointerId,
    sx: e.clientX,
    sy: e.clientY,
    ox: pos.value.x,
    oy: pos.value.y
  }
  e.currentTarget.setPointerCapture(e.pointerId)
  e.currentTarget.focus({ preventScroll: true })
}

const onPointerMove = (e) => {
  if (dragInfo && dragInfo.id === e.pointerId) {
    moveLens(
      dragInfo.ox + e.clientX - dragInfo.sx,
      dragInfo.oy + e.clientY - dragInfo.sy
    )
  }
}

const onPointerUp = (e) => {
  if (dragInfo && dragInfo.id === e.pointerId) {
    dragInfo = null
    if (e.currentTarget.hasPointerCapture(e.pointerId)) {
      e.currentTarget.releasePointerCapture(e.pointerId)
    }
  }
}

const onKeyDown = (e) => {
  if (e.target !== e.currentTarget) return
  const step = e.shiftKey ? 30 : 10
  const deltas = {
    ArrowLeft: [-step, 0],
    ArrowRight: [step, 0],
    ArrowUp: [0, -step],
    ArrowDown: [0, step]
  }
  if (deltas[e.key]) {
    e.preventDefault()
    moveLens(pos.value.x + deltas[e.key][0], pos.value.y + deltas[e.key][1])
  } else if (e.key === 'Home') {
    e.preventDefault()
    centerLens()
  }
}

const statusText = computed(() => {
  const r = lensRef.value?.renderer || 'css'
  if (r === 'svg') return 'SVG 增强硬件折射'
  if (r === 'solid') return 'Solid 无障碍高对比模式'
  return 'CSS 柔和毛玻璃模式'
})

const statusClass = computed(() => {
  const r = lensRef.value?.renderer || 'css'
  if (r === 'svg') return 'status-svg'
  if (r === 'solid') return 'status-solid'
  return 'status-css'
})

const applyAsSystemTheme = () => {
  themeStore.setTheme('liquid-glass')
}

const toggleGlobalLiquidGlass = () => {
  if (isCurrentThemeLiquidGlass.value) {
    themeStore.setTheme('light')
  } else {
    themeStore.setTheme('liquid-glass')
  }
}

onMounted(() => {
  nextTick(() => {
    centerLens()
    resizeObserver = new ResizeObserver(centerLens)
    if (stageRef.value) resizeObserver.observe(stageRef.value)
    if (lensRef.value?.hostRef) resizeObserver.observe(lensRef.value.hostRef)
  })
})

onBeforeUnmount(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
})
</script>
