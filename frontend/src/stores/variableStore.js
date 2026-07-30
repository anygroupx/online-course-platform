/**
 * 系统变量状态管理
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getVariablesByType } from '@/api/variable'

export const useVariableStore = defineStore('variable', () => {
  // 状态数据
  const variables = ref({})
  const loading = ref(false)

  // 计算属性 - 订单状态
  const orderStatusOptions = computed(() => {
    return variables.value.order_status || []
  })

  // 计算属性 - 对接状态
  const dockStatusOptions = computed(() => {
    return variables.value.dock_status || []
  })

  // 计算属性 - 用户状态
  const userStatusOptions = computed(() => {
    return variables.value.user_status || []
  })

  // 计算属性 - 平台状态
  const platformStatusOptions = computed(() => {
    return variables.value.platform_status || []
  })

  // 计算属性 - 充值卡状态
  const cardStatusOptions = computed(() => {
    return variables.value.card_status || []
  })

  // 计算属性 - 公告类型
  const announcementTypeOptions = computed(() => {
    return variables.value.announcement_type || []
  })

  // 计算属性 - 会话状态
  const sessionStatusOptions = computed(() => {
    return variables.value.session_status || []
  })

  // 计算属性 - 消息类型
  const messageTypeOptions = computed(() => {
    return variables.value.message_type || []
  })

  // 加载指定类型的变量
  const loadVariablesByType = async (type) => {
    try {
      loading.value = true
      const response = await getVariablesByType(type)
      variables.value[type] = response.data
    } catch (error) {
      console.error(`加载${type}变量失败:`, error)
    } finally {
      loading.value = false
    }
  }

  // 加载所有变量
  const loadAllVariables = async () => {
    const types = [
      'order_status',
      'dock_status',
      'user_status',
      'platform_status',
      'card_status',
      'announcement_type',
      'session_status',
      'message_type'
    ]

    await Promise.all(types.map(type => loadVariablesByType(type)))
  }

  // 根据类型和值获取变量信息
  const getVariableByTypeAndValue = (type, value) => {
    const typeVariables = variables.value[type] || []
    return typeVariables.find(v => v.variableValue === String(value))
  }

  // 根据类型和值获取变量名称
  const getVariableName = (type, value) => {
    const variable = getVariableByTypeAndValue(type, value)
    return variable ? variable.variableName : '未知'
  }

  // 根据类型和值获取变量标签类型
  const getVariableTagType = (type, value) => {
    const variable = getVariableByTypeAndValue(type, value)
    if (!variable) return 'info'

    // 根据颜色或状态判断标签类型
    if (variable.color) {
      const color = (variable.color || '').toLowerCase()
      if (
        color.includes('#63c56e') ||
        color.includes('#67c23a') ||
        color.includes('#5cb85c') ||
        color.includes('var(--color-success)')
      ) return 'success'
      if (
        color.includes('#f7a62f') ||
        color.includes('#e6a23c') ||
        color.includes('#f0ad4e') ||
        color.includes('var(--color-warning)')
      ) return 'warning'
      if (
        color.includes('#f06565') ||
        color.includes('#f56c6c') ||
        color.includes('#d9534f') ||
        color.includes('var(--color-danger)')
      ) return 'danger'
      if (
        color.includes('#4e8cff') ||
        color.includes('#409eff') ||
        color.includes('#667eea') ||
        color.includes('#5bc0de') ||
        color.includes('var(--color-primary)')
      ) return 'primary'
    }

    return 'info'
  }

  // 根据类型和值获取变量颜色
  const getVariableColor = (type, value) => {
    const variable = getVariableByTypeAndValue(type, value)
    return variable ? variable.color : '#909399'
  }

  // 根据类型和值获取变量图标
  const getVariableIcon = (type, value) => {
    const variable = getVariableByTypeAndValue(type, value)
    return variable ? variable.icon : null
  }

  // 获取状态选项（用于下拉框）
  const getStatusOptions = (type) => {
    const typeVariables = variables.value[type] || []
    return typeVariables
      .filter(v => v.isEnabled)
      .map(v => ({
        label: v.variableName,
        value: parseInt(v.variableValue),
        color: v.color,
        icon: v.icon
      }))
  }

  return {
    // 状态
    variables,
    loading,

    // 计算属性
    orderStatusOptions,
    dockStatusOptions,
    userStatusOptions,
    platformStatusOptions,
    cardStatusOptions,
    announcementTypeOptions,
    sessionStatusOptions,
    messageTypeOptions,

    // 方法
    loadVariablesByType,
    loadAllVariables,
    getVariableByTypeAndValue,
    getVariableName,
    getVariableTagType,
    getVariableColor,
    getVariableIcon,
    getStatusOptions
  }
})
