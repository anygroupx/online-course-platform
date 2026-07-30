<template>
  <div class="customer-service-management">
    <!-- 头部 -->
    <div class="header">
      <h2>客服管理工作台</h2>
      <div class="header-actions">
        <el-button @click="loadData" :loading="loading">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- 状态筛选标签 -->
    <div class="status-tabs">
      <el-radio-group v-model="selectedStatus" @change="loadSessions">
        <el-radio-button :label="null">全部</el-radio-button>
        <el-radio-button :label="1">等待中</el-radio-button>
        <el-radio-button :label="2">进行中</el-radio-button>
        <el-radio-button :label="3">已结束</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 主体内容 -->
    <div class="main-content">
      <!-- 左侧会话列表 -->
      <div class="session-list">
        <div class="list-header">
          <span>会话列表 ({{ sessions.length }})</span>
        </div>
        <div class="list-body">
          <div
            v-if="sessions.length === 0"
            class="empty-placeholder"
          >
            <el-empty description="暂无会话" />
          </div>
          <div
            v-for="session in sessions"
            :key="session.sessionId"
            class="session-item"
            :class="{ active: currentSessionId === session.sessionId }"
            @click="selectSession(session)"
          >
            <div class="session-header">
              <div class="user-info">
                <el-avatar :size="40">
                  <el-icon><User /></el-icon>
                </el-avatar>
                <div class="user-details">
                  <div class="user-name">{{ session.userName || '未知用户' }}</div>
                  <div class="user-account">{{ session.userAccount }}</div>
                </div>
              </div>
              <el-tag
                :type="getStatusType(session.status)"
                size="small"
              >
                {{ getStatusText(session.status) }}
              </el-tag>
            </div>
            <div class="session-preview">
              <div class="last-message">
                {{ session.lastMessageContent || '暂无消息' }}
              </div>
              <div class="time">
                {{ formatTime(session.lastMessageTime) }}
              </div>
            </div>
            <div v-if="session.customerServiceId" class="cs-info">
              客服：{{ session.customerServiceName || '未知' }}
            </div>
            <div v-if="!session.customerServiceId && session.status === 1" class="take-action">
              <el-button
                type="primary"
                size="small"
                @click.stop="handleTakeSession(session.sessionId)"
              >
                接入会话
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧消息区域 -->
      <div class="message-area">
        <div v-if="!currentSessionId" class="empty-chat">
          <el-icon :size="80"><ChatDotRound /></el-icon>
          <p>请从左侧选择一个会话</p>
        </div>
        <template v-else>
          <!-- 聊天头部 -->
          <div class="chat-header">
            <div class="chat-user-info">
              <el-avatar :size="32">
                <el-icon><User /></el-icon>
              </el-avatar>
              <div class="chat-user-details">
                <div class="name">{{ currentSession?.userName || '未知用户' }}</div>
                <div class="account">{{ currentSession?.userAccount }}</div>
              </div>
            </div>
            <div class="chat-actions">
              <el-button
                v-if="currentSession?.status === 2"
                @click="handleEndSession"
                :disabled="endingSession"
              >
                结束会话
              </el-button>
            </div>
          </div>

          <!-- 消息列表 -->
          <div class="message-list" ref="messageListRef">
            <div
              v-for="message in messages"
              :key="message.id"
              class="message-item"
              :class="{ 'message-right': message.senderType === 2 }"
            >
              <div class="message-avatar">
                <el-avatar :size="32">
                  <el-icon v-if="message.senderType === 1"><User /></el-icon>
                  <el-icon v-else><Service /></el-icon>
                </el-avatar>
              </div>
              <div class="message-content">
                <div class="message-header">
                  <span class="sender-name">{{ message.senderName }}</span>
                  <span class="message-time">{{ formatTime(message.createTime) }}</span>
                </div>
                <div class="message-text">{{ message.content }}</div>
              </div>
            </div>
          </div>

          <!-- 输入区域 -->
          <div class="input-area">
            <el-input
              v-model="inputMessage"
              type="textarea"
              :rows="3"
              placeholder="输入回复消息..."
              @keydown.enter.prevent="handleSendMessage"
              :disabled="currentSession?.status === 3"
            />
            <div class="input-actions">
              <el-button
                type="primary"
                @click="handleSendMessage"
                :disabled="!inputMessage.trim() || currentSession?.status === 3"
                :loading="sending"
              >
                发送
              </el-button>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, ChatDotRound, User, Service } from '@element-plus/icons-vue'
import {
  getAllSessions,
  takeSession,
  sendMessage,
  getSessionMessages,
  endSession
} from '@/api/customerService'

// 响应式数据
const sessions = ref([])
const currentSessionId = ref('')
const messages = ref([])
const inputMessage = ref('')
const selectedStatus = ref(null)
const loading = ref(false)
const sending = ref(false)
const endingSession = ref(false)
const messageListRef = ref()

// 轮询定时器
let sessionPollingTimer = null
let messagePollingTimer = null

// 当前选中的会话
const currentSession = computed(() => {
  return sessions.value.find(s => s.sessionId === currentSessionId.value)
})

// 格式化时间
const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diffMs = now - date
  const diffMins = Math.floor(diffMs / 60000)

  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins}分钟前`
  if (diffMins < 1440) return `${Math.floor(diffMins / 60)}小时前`

  return date.toLocaleDateString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 获取状态类型
const getStatusType = (status) => {
  const types = {
    1: 'warning',
    2: 'success',
    3: 'info'
  }
  return types[status] || ''
}

// 获取状态文本
const getStatusText = (status) => {
  const texts = {
    1: '等待中',
    2: '进行中',
    3: '已结束'
  }
  return texts[status] || '未知'
}

// 加载会话列表
const loadSessions = async () => {
  loading.value = true
  try {
    const response = await getAllSessions(selectedStatus.value)
    if (response.code === 1) {
      sessions.value = response.data || []
    } else {
      ElMessage.error(response.msg || '加载会话列表失败')
    }
  } catch (error) {
    console.error('加载会话列表失败：', error)
    ElMessage.error('加载会话列表失败')
  } finally {
    loading.value = false
  }
}

// 选择会话
const selectSession = async (session) => {
  currentSessionId.value = session.sessionId
  await loadMessages()
  startMessagePolling()
}

// 加载消息列表
const loadMessages = async () => {
  if (!currentSessionId.value) return

  try {
    const response = await getSessionMessages(currentSessionId.value)
    if (response.code === 1) {
      messages.value = response.data.sort((a, b) => {
        return new Date(a.createTime) - new Date(b.createTime)
      })
      await nextTick()
      scrollToBottom()
    }
  } catch (error) {
    console.error('加载消息失败：', error)
  }
}

// 接入会话
const handleTakeSession = async (sessionId) => {
  try {
    const response = await takeSession(sessionId)
    if (response.code === 1) {
      ElMessage.success('接入会话成功')
      await loadSessions()
      // 如果接入的是当前选中的会话，重新加载消息
      if (sessionId === currentSessionId.value) {
        await loadMessages()
      }
    } else {
      ElMessage.error(response.msg || '接入会话失败')
    }
  } catch (error) {
    console.error('接入会话失败：', error)
    ElMessage.error('接入会话失败')
  }
}

// 发送消息
const handleSendMessage = async () => {
  if (!inputMessage.value.trim() || !currentSessionId.value) return

  sending.value = true

  try {
    const response = await sendMessage({
      sessionId: currentSessionId.value,
      content: inputMessage.value.trim(),
      senderType: 2, // 客服
      messageType: 1  // 文本
    })

    if (response.code === 1) {
      inputMessage.value = ''
      await loadMessages()
      // 更新会话列表中的最后消息时间
      await loadSessions()
    } else {
      ElMessage.error(response.msg || '发送消息失败')
    }
  } catch (error) {
    console.error('发送消息失败：', error)
    ElMessage.error('发送消息失败')
  } finally {
    sending.value = false
  }
}

// 结束会话
const handleEndSession = async () => {
  if (!currentSessionId.value) return

  endingSession.value = true

  try {
    const response = await endSession(currentSessionId.value)
    if (response.code === 1) {
      ElMessage.success('会话已结束')
      await loadSessions()
      stopMessagePolling()
    } else {
      ElMessage.error(response.msg || '结束会话失败')
    }
  } catch (error) {
    console.error('结束会话失败：', error)
    ElMessage.error('结束会话失败')
  } finally {
    endingSession.value = false
  }
}

// 滚动到底部
const scrollToBottom = () => {
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}

// 开始会话列表轮询
const startSessionPolling = () => {
  if (sessionPollingTimer) return
  sessionPollingTimer = setInterval(() => {
    loadSessions()
  }, 5000) // 每5秒刷新一次会话列表
}

// 停止会话列表轮询
const stopSessionPolling = () => {
  if (sessionPollingTimer) {
    clearInterval(sessionPollingTimer)
    sessionPollingTimer = null
  }
}

// 开始消息轮询
const startMessagePolling = () => {
  if (messagePollingTimer) return
  messagePollingTimer = setInterval(() => {
    if (currentSessionId.value) {
      loadMessages()
    }
  }, 3000) // 每3秒刷新一次消息
}

// 停止消息轮询
const stopMessagePolling = () => {
  if (messagePollingTimer) {
    clearInterval(messagePollingTimer)
    messagePollingTimer = null
  }
}

// 加载所有数据
const loadData = async () => {
  await loadSessions()
  if (currentSessionId.value) {
    await loadMessages()
  }
}

// 组件挂载
onMounted(() => {
  loadSessions()
  startSessionPolling()
})

// 组件卸载
onUnmounted(() => {
  stopSessionPolling()
  stopMessagePolling()
})
</script>

<style scoped>
.customer-service-management {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-body);
}

/* 头部 */
.header {
  padding: 20px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header h2 {
  margin: 0;
  font-size: 20px;
  color: var(--text-primary);
}

/* 状态标签 */
.status-tabs {
  padding: 16px 20px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-color);
}

/* 主体内容 */
.main-content {
  flex: 1;
  display: flex;
  min-height: 0;
}

/* 左侧会话列表 */
.session-list {
  width: 350px;
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
}

.list-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color);
  font-weight: 500;
  color: var(--text-primary);
}

.list-body {
  flex: 1;
  overflow-y: auto;
}

.empty-placeholder {
  padding: 60px 20px;
  text-align: center;
}

.session-item {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color-light);
  cursor: pointer;
  transition: background 0.2s;
}

.session-item:hover {
  background: var(--bg-hover);
}

.session-item.active {
  background: var(--color-primary-light-9);
  border-left: 3px solid var(--color-primary);
}

.session-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.user-details {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-weight: 500;
  color: var(--text-primary);
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-account {
  font-size: 12px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-preview {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.last-message {
  flex: 1;
  font-size: 13px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time {
  font-size: 12px;
  color: var(--text-placeholder);
  white-space: nowrap;
}

.cs-info {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

.take-action {
  margin-top: 8px;
}

/* 右侧消息区域 */
.message-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--bg-body);
}

.empty-chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-placeholder);
}

.empty-chat p {
  margin-top: 16px;
  font-size: 14px;
}

/* 聊天头部 */
.chat-header {
  padding: 16px 20px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chat-user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chat-user-details .name {
  font-weight: 500;
  color: var(--text-primary);
  font-size: 14px;
}

.chat-user-details .account {
  font-size: 12px;
  color: var(--text-secondary);
}

/* 消息列表 */
.message-list {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background: var(--bg-body);
}

.message-item {
  display: flex;
  margin-bottom: 20px;
  align-items: flex-start;
}

.message-item.message-right {
  flex-direction: row-reverse;
}

.message-avatar {
  margin: 0 12px;
}

.message-content {
  max-width: 60%;
  background: var(--bg-card);
  border-radius: 12px;
  padding: 10px 14px;
  box-shadow: var(--shadow-sm);
}

.message-right .message-content {
  background: var(--color-primary);
  color: white;
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  gap: 12px;
}

.sender-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
}

.message-right .sender-name {
  color: rgba(255, 255, 255, 0.9);
}

.message-time {
  font-size: 11px;
  color: var(--text-placeholder);
  white-space: nowrap;
}

.message-right .message-time {
  color: rgba(255, 255, 255, 0.7);
}

.message-text {
  font-size: 14px;
  line-height: 1.5;
  word-wrap: break-word;
  color: var(--text-primary);
}

.message-right .message-text {
  color: white;
}

/* 输入区域 */
.input-area {
  padding: 20px;
  background: var(--bg-card);
  border-top: 1px solid var(--border-color);
}

.input-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

/* 暗黑模式 */
html.dark .message-content {
  background: #2d2d2d;
}

html.dark .message-right .message-content {
  background: var(--color-primary);
  color: white;
}

/* 响应式 */
@media (max-width: 1024px) {
  .session-list {
    width: 300px;
  }
}

@media (max-width: 768px) {
  .main-content {
    flex-direction: column;
  }

  .session-list {
    width: 100%;
    max-height: 300px;
    border-right: none;
    border-bottom: 1px solid var(--border-color);
  }
}
</style>
