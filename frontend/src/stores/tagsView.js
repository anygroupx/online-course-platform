/**
 * 标签页视图状态管理
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * @source Vue 3 + Pinia 最佳实践
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useTagsViewStore = defineStore('tagsView', () => {
  // 标签页列表
  const visitedViews = ref([])
  // 缓存的视图组件名称
  const cachedViews = ref([])

  // 添加标签页
  const addView = (view) => {
    addVisitedView(view)
    addCachedView(view)
  }

  // 添加访问过的标签页
  const addVisitedView = (view) => {
    // 检查是否已存在
    if (visitedViews.value.some(v => v.path === view.path)) return
    
    // 设置标签页信息
    const tagView = {
      name: view.name,
      path: view.path,
      title: view.meta?.title || view.name,
      icon: view.meta?.icon,
      fixed: view.meta?.fixed || false,
      closable: view.meta?.closable !== false, // 默认可关闭
      query: view.query,
      params: view.params
    }
    
    visitedViews.value.push(tagView)
  }

  // 添加缓存视图
  const addCachedView = (view) => {
    if (cachedViews.value.includes(view.name)) return
    if (view.meta?.keepAlive !== false) {
      cachedViews.value.push(view.name)
    }
  }

  // 删除标签页
  const delView = (view) => {
    return new Promise(resolve => {
      delVisitedView(view)
      delCachedView(view)
      resolve({
        visitedViews: [...visitedViews.value],
        cachedViews: [...cachedViews.value]
      })
    })
  }

  // 删除访问过的标签页
  const delVisitedView = (view) => {
    const index = visitedViews.value.findIndex(v => v.path === view.path)
    if (index > -1) {
      visitedViews.value.splice(index, 1)
    }
  }

  // 删除缓存视图
  const delCachedView = (view) => {
    const index = cachedViews.value.indexOf(view.name)
    if (index > -1) {
      cachedViews.value.splice(index, 1)
    }
  }

  // 删除其他标签页
  const delOthersViews = (view) => {
    return new Promise(resolve => {
      delOthersVisitedViews(view)
      delOthersCachedViews(view)
      resolve({
        visitedViews: [...visitedViews.value],
        cachedViews: [...cachedViews.value]
      })
    })
  }

  // 删除其他访问过的标签页
  const delOthersVisitedViews = (view) => {
    visitedViews.value = visitedViews.value.filter(v => {
      return v.fixed || v.path === view.path
    })
  }

  // 删除其他缓存视图
  const delOthersCachedViews = (view) => {
    const index = cachedViews.value.indexOf(view.name)
    if (index > -1) {
      cachedViews.value = cachedViews.value.slice(index, index + 1)
    } else {
      cachedViews.value = []
    }
  }

  // 删除所有标签页
  const delAllViews = () => {
    return new Promise(resolve => {
      delAllVisitedViews()
      delAllCachedViews()
      resolve({
        visitedViews: [...visitedViews.value],
        cachedViews: [...cachedViews.value]
      })
    })
  }

  // 删除所有访问过的标签页
  const delAllVisitedViews = () => {
    visitedViews.value = visitedViews.value.filter(tag => tag.fixed)
  }

  // 删除所有缓存视图
  const delAllCachedViews = () => {
    cachedViews.value = []
  }

  // 更新标签页
  const updateVisitedView = (view) => {
    const index = visitedViews.value.findIndex(v => v.path === view.path)
    if (index > -1) {
      visitedViews.value[index] = Object.assign(visitedViews.value[index], view)
    }
  }

  // 移动标签页位置
  const moveView = (fromIndex, toIndex) => {
    const item = visitedViews.value.splice(fromIndex, 1)[0]
    visitedViews.value.splice(toIndex, 0, item)
  }

  // 切换标签页固定状态
  const toggleFixed = (view) => {
    const index = visitedViews.value.findIndex(v => v.path === view.path)
    if (index > -1) {
      visitedViews.value[index].fixed = !visitedViews.value[index].fixed
    }
  }

  // 计算属性
  const visitedViewsList = computed(() => visitedViews.value)
  const cachedViewsList = computed(() => cachedViews.value)

  return {
    // 状态
    visitedViews,
    cachedViews,
    
    // 计算属性
    visitedViewsList,
    cachedViewsList,
    
    // 方法
    addView,
    addVisitedView,
    addCachedView,
    delView,
    delVisitedView,
    delCachedView,
    delOthersViews,
    delOthersVisitedViews,
    delOthersCachedViews,
    delAllViews,
    delAllVisitedViews,
    delAllCachedViews,
    updateVisitedView,
    moveView,
    toggleFixed
  }
})
