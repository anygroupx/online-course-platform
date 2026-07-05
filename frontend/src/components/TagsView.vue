<template>
  <div class="tags-view-container">
    <!-- 标签页滚动容器 -->
    <div 
      ref="scrollContainer" 
      class="tags-view-wrapper"
      @scroll="handleScroll"
    >
      <div 
        ref="tagsContainer" 
        class="tags-container"
        :style="{ transform: `translateX(${scrollLeft}px)` }"
      >
        <TagItem
          v-for="(tag, index) in visitedViews"
          :key="tag.path"
          :tag="tag"
          :index="index"
          :is-active="isActive(tag)"
          @click="handleTagClick(tag)"
          @close="handleTagClose(tag)"
          @contextmenu="handleTagContextMenu($event, tag)"
          @dragstart="handleDragStart($event, index)"
          @dragend="handleDragEnd"
          @dragover="handleDragOver($event, index)"
          @drop="handleDrop($event, index)"
        />
      </div>
    </div>

    <!-- 滚动按钮 -->
    <div class="tags-view-actions">
      <el-button
        v-if="showScrollLeft"
        type="text"
        size="small"
        :icon="ArrowLeft"
        @click="scrollLeftAction"
        class="scroll-btn"
      />
      <el-button
        v-if="showScrollRight"
        type="text"
        size="small"
        :icon="ArrowRight"
        @click="scrollRightAction"
        class="scroll-btn"
      />
      
      <!-- 更多操作按钮 -->
      <el-dropdown 
        trigger="click" 
        @command="handleDropdownCommand"
        class="more-actions"
      >
        <el-button type="text" size="small" :icon="More" class="more-btn" />
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="refresh">
              <el-icon><Refresh /></el-icon>
              刷新当前页
            </el-dropdown-item>
            <el-dropdown-item command="closeOthers" :disabled="visitedViews.length <= 1">
              <el-icon><Close /></el-icon>
              关闭其他
            </el-dropdown-item>
            <el-dropdown-item command="closeAll" :disabled="visitedViews.length <= 1">
              <el-icon><CircleClose /></el-icon>
              关闭所有
            </el-dropdown-item>
            <el-dropdown-item divided command="closeLeft" :disabled="!canCloseLeft">
              <el-icon><ArrowLeft /></el-icon>
              关闭左侧
            </el-dropdown-item>
            <el-dropdown-item command="closeRight" :disabled="!canCloseRight">
              <el-icon><ArrowRight /></el-icon>
              关闭右侧
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 右键菜单 -->
    <el-dropdown
      ref="contextMenuRef"
      trigger="manual"
      :visible="contextMenuVisible"
      @command="handleContextMenuCommand"
      @hide="contextMenuVisible = false"
    >
      <div></div>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="refresh">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-dropdown-item>
          <el-dropdown-item command="close" :disabled="!contextMenuTag?.closable">
            <el-icon><Close /></el-icon>
            关闭
          </el-dropdown-item>
          <el-dropdown-item command="closeOthers" :disabled="visitedViews.length <= 1">
            <el-icon><Close /></el-icon>
            关闭其他
          </el-dropdown-item>
          <el-dropdown-item command="closeAll" :disabled="visitedViews.length <= 1">
            <el-icon><CircleClose /></el-icon>
            关闭所有
          </el-dropdown-item>
          <el-dropdown-item command="closeLeft" :disabled="!canCloseLeft">
            <el-icon><ArrowLeft /></el-icon>
            关闭左侧
          </el-dropdown-item>
          <el-dropdown-item command="closeRight" :disabled="!canCloseRight">
            <el-icon><ArrowRight /></el-icon>
            关闭右侧
          </el-dropdown-item>
          <el-dropdown-item divided command="toggleFixed">
            <el-icon><Lock v-if="!contextMenuTag?.fixed" /><Unlock v-else /></el-icon>
            {{ contextMenuTag?.fixed ? '取消固定' : '固定标签' }}
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useTagsViewStore } from '@/stores/tagsView'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  ArrowRight,
  More,
  Refresh,
  Close,
  CircleClose,
  Lock,
  Unlock
} from '@element-plus/icons-vue'
import TagItem from './TagItem.vue'

const router = useRouter()
const route = useRoute()
const tagsViewStore = useTagsViewStore()

// 引用
const scrollContainer = ref(null)
const tagsContainer = ref(null)
const contextMenuRef = ref(null)

// 状态
const scrollLeft = ref(0)
const contextMenuVisible = ref(false)
const contextMenuTag = ref(null)
const draggingIndex = ref(-1)

// 计算属性
const visitedViews = computed(() => tagsViewStore.visitedViewsList)
const cachedViews = computed(() => tagsViewStore.cachedViewsList)

// 滚动相关
const showScrollLeft = computed(() => scrollLeft.value < 0)
const showScrollRight = computed(() => {
  if (!scrollContainer.value || !tagsContainer.value) return false
  const containerWidth = scrollContainer.value.clientWidth
  const tagsWidth = tagsContainer.value.scrollWidth
  return Math.abs(scrollLeft.value) < tagsWidth - containerWidth
})

// 关闭操作相关
const canCloseLeft = computed(() => {
  if (!contextMenuTag.value) return false
  const currentIndex = visitedViews.value.findIndex(v => v.path === contextMenuTag.value.path)
  return currentIndex > 0
})

const canCloseRight = computed(() => {
  if (!contextMenuTag.value) return false
  const currentIndex = visitedViews.value.findIndex(v => v.path === contextMenuTag.value.path)
  return currentIndex < visitedViews.value.length - 1
})

// 判断标签是否激活
const isActive = (tag) => {
  return tag.path === route.path
}

// 标签点击
const handleTagClick = (tag) => {
  if (tag.path !== route.path) {
    router.push({
      path: tag.path,
      query: tag.query,
      params: tag.params
    })
  }
}

// 标签关闭
const handleTagClose = async (tag) => {
  if (!tag.closable) return
  
  await tagsViewStore.delView(tag)
  
  // 如果关闭的是当前页面，跳转到上一个标签页
  if (tag.path === route.path) {
    const currentIndex = visitedViews.value.findIndex(v => v.path === tag.path)
    if (currentIndex > 0) {
      const prevTag = visitedViews.value[currentIndex - 1]
      router.push(prevTag.path)
    } else if (visitedViews.value.length > 0) {
      const nextTag = visitedViews.value[0]
      router.push(nextTag.path)
    } else {
      router.push('/dashboard')
    }
  }
}

// 标签右键菜单
const handleTagContextMenu = (event, tag) => {
  event.preventDefault()
  contextMenuTag.value = tag
  contextMenuVisible.value = true
  
  nextTick(() => {
    if (contextMenuRef.value) {
      contextMenuRef.value.$el.style.left = `${event.clientX}px`
      contextMenuRef.value.$el.style.top = `${event.clientY}px`
    }
  })
}

// 右键菜单命令处理
const handleContextMenuCommand = async (command) => {
  const tag = contextMenuTag.value
  if (!tag) return

  switch (command) {
    case 'refresh':
      // 刷新当前页面
      window.location.reload()
      break
    case 'close':
      await handleTagClose(tag)
      break
    case 'closeOthers':
      await tagsViewStore.delOthersViews(tag)
      break
    case 'closeAll':
      await tagsViewStore.delAllViews()
      router.push('/dashboard')
      break
    case 'closeLeft':
      await closeLeftTags(tag)
      break
    case 'closeRight':
      await closeRightTags(tag)
      break
    case 'toggleFixed':
      tagsViewStore.toggleFixed(tag)
      ElMessage.success(tag.fixed ? '已取消固定' : '已固定标签')
      break
  }
  
  contextMenuVisible.value = false
}

// 下拉菜单命令处理
const handleDropdownCommand = async (command) => {
  const currentTag = visitedViews.value.find(v => v.path === route.path)
  if (!currentTag) return

  switch (command) {
    case 'refresh':
      window.location.reload()
      break
    case 'closeOthers':
      await tagsViewStore.delOthersViews(currentTag)
      break
    case 'closeAll':
      await tagsViewStore.delAllViews()
      router.push('/dashboard')
      break
    case 'closeLeft':
      await closeLeftTags(currentTag)
      break
    case 'closeRight':
      await closeRightTags(currentTag)
      break
  }
}

// 关闭左侧标签
const closeLeftTags = async (tag) => {
  const currentIndex = visitedViews.value.findIndex(v => v.path === tag.path)
  const leftTags = visitedViews.value.slice(0, currentIndex).filter(t => !t.fixed)
  
  for (const leftTag of leftTags) {
    await tagsViewStore.delView(leftTag)
  }
}

// 关闭右侧标签
const closeRightTags = async (tag) => {
  const currentIndex = visitedViews.value.findIndex(v => v.path === tag.path)
  const rightTags = visitedViews.value.slice(currentIndex + 1).filter(t => !t.fixed)
  
  for (const rightTag of rightTags) {
    await tagsViewStore.delView(rightTag)
  }
}

// 拖拽相关
const handleDragStart = (event, index) => {
  draggingIndex.value = index
  event.dataTransfer.effectAllowed = 'move'
}

const handleDragEnd = () => {
  draggingIndex.value = -1
}

const handleDragOver = (event, index) => {
  event.preventDefault()
  event.dataTransfer.dropEffect = 'move'
}

const handleDrop = (event, dropIndex) => {
  event.preventDefault()
  
  if (draggingIndex.value !== -1 && draggingIndex.value !== dropIndex) {
    tagsViewStore.moveView(draggingIndex.value, dropIndex)
  }
}

// 滚动相关
const handleScroll = () => {
  // 可以在这里添加滚动处理逻辑
}

const scrollLeftAction = () => {
  const scrollAmount = 200
  scrollLeft.value = Math.min(0, scrollLeft.value + scrollAmount)
}

const scrollRightAction = () => {
  const scrollAmount = 200
  scrollLeft.value = Math.max(
    scrollLeft.value - scrollAmount,
    -(tagsContainer.value?.scrollWidth || 0) + (scrollContainer.value?.clientWidth || 0)
  )
}

// 监听路由变化，自动添加标签页
watch(
  () => route.path,
  (newPath) => {
    if (newPath) {
      tagsViewStore.addView(route)
    }
  },
  { immediate: true }
)

// 监听标签页变化，调整滚动位置
watch(
  () => visitedViews.value.length,
  () => {
    nextTick(() => {
      adjustScrollPosition()
    })
  }
)

// 调整滚动位置
const adjustScrollPosition = () => {
  if (!scrollContainer.value || !tagsContainer.value) return
  
  const containerWidth = scrollContainer.value.clientWidth
  const tagsWidth = tagsContainer.value.scrollWidth
  
  if (tagsWidth <= containerWidth) {
    scrollLeft.value = 0
  } else {
    // 确保当前激活的标签页可见
    const activeTag = document.querySelector('.tag-item.active')
    if (activeTag) {
      const tagRect = activeTag.getBoundingClientRect()
      const containerRect = scrollContainer.value.getBoundingClientRect()
      
      if (tagRect.left < containerRect.left) {
        scrollLeft.value = Math.min(0, scrollLeft.value + (containerRect.left - tagRect.left))
      } else if (tagRect.right > containerRect.right) {
        scrollLeft.value = scrollLeft.value - (tagRect.right - containerRect.right)
      }
    }
  }
}

// 生命周期
onMounted(() => {
  // 初始化时添加当前路由
  tagsViewStore.addView(route)
  
  // 监听窗口大小变化
  window.addEventListener('resize', adjustScrollPosition)
})

onUnmounted(() => {
  window.removeEventListener('resize', adjustScrollPosition)
})
</script>

<style scoped>
.tags-view-container {
  display: flex;
  align-items: center;
  height: 44px;
  background: var(--bg-card);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border-color-light);
  padding: 0 16px;
  position: relative;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
  transition: background-color 0.3s, border-color 0.3s;
}

html.dark .tags-view-container {
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-color-light);
  box-shadow: var(--shadow-sm);
}

.tags-view-wrapper {
  flex: 1;
  overflow: hidden;
  position: relative;
}

.tags-container {
  display: flex;
  align-items: center;
  height: 100%;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  white-space: nowrap;
  padding: 4px 0;
}

.tags-view-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 12px;
}

.scroll-btn,
.more-btn {
  width: 28px;
  height: 28px;
  padding: 0;
  border-radius: 4px;
  color: var(--text-regular);
  transition: all 0.3s;
}

.scroll-btn:hover,
.more-btn:hover {
  background: rgba(0, 0, 0, 0.08);
  color: var(--primary-gradient-start);
}

html.dark .scroll-btn:hover,
html.dark .more-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}

.more-actions {
  margin-left: 4px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .tags-view-container {
    height: 40px;
    padding: 0 8px;
  }
  
  .tags-view-actions {
    margin-left: 8px;
    gap: 4px;
  }
  
  .scroll-btn,
  .more-btn {
    width: 24px;
    height: 24px;
  }
}

@media (max-width: 480px) {
  .tags-view-container {
    height: 36px;
    padding: 0 6px;
  }
}
</style>
