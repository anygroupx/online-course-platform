<template>
  <div class="customer-service">
    <!-- 客服入口按钮 -->
    <div v-if="!chatVisible" class="service-button" @click="openChat">
      <el-icon><ChatDotRound /></el-icon>
      <span>在线客服</span>
      <el-badge
        v-if="unreadCount > 0"
        :value="unreadCount"
        class="unread-badge"
      />
    </div>

    <!-- 聊天窗口 -->
    <div v-if="chatVisible" class="chat-window">
      <!-- 聊天头部 -->
      <div class="chat-header">
        <div class="header-info">
          <el-icon><ChatDotRound /></el-icon>
          <span>在线客服</span>
          <el-tag v-if="sessionStatus === 1" type="warning" size="small"
            >等待中</el-tag
          >
          <el-tag v-else-if="sessionStatus === 2" type="success" size="small"
            >进行中</el-tag
          >
        </div>
        <div class="header-actions">
          <el-button size="small" @click="minimizeChat">
            <el-icon><Minus /></el-icon>
          </el-button>
          <el-button size="small" @click="closeChat">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 消息列表 -->
      <div class="message-list" ref="messageListRef">
        <div v-if="messages.length === 0" class="empty-message">
          <el-icon><ChatDotRound /></el-icon>
          <p>欢迎使用在线客服，有什么可以帮助您的吗？</p>
        </div>
        <div
          v-for="message in messages"
          :key="message.id"
          class="message-item"
          :class="{ 'message-right': message.senderType === 1 }"
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
              <span class="message-time">{{
                formatTime(message.createTime)
              }}</span>
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
          placeholder="请输入您的问题..."
          @keydown.enter.prevent="handleSendMessage"
          :disabled="sessionStatus === 3"
        />
        <div class="input-actions">
          <el-button
            type="primary"
            @click="handleSendMessage"
            :disabled="!inputMessage.trim() || sessionStatus === 3"
            :loading="sending"
          >
            发送
          </el-button>
          <el-button v-if="sessionStatus === 2" @click="handleEndSession">
            结束对话
          </el-button>
        </div>
      </div>
    </div>

    <!-- 最小化状态 -->
    <div v-if="minimized" class="minimized-chat" @click="restoreChat">
      <el-icon><ChatDotRound /></el-icon>
      <el-badge
        v-if="unreadCount > 0"
        :value="unreadCount"
        class="unread-badge"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from "vue";
import { ElMessage } from "element-plus";
import {
  ChatDotRound,
  Minus,
  Close,
  User,
  Service,
} from "@element-plus/icons-vue";
import {
  createOrGetSession,
  sendMessage,
  getSessionMessages,
  markMessagesAsRead,
  getUnreadCount,
  endSession,
} from "@/api/customerService";
import { useUserStore } from "@/stores/user";

// Source: AURA-X-KYS 用户状态管理集成
const userStore = useUserStore();

// 响应式数据
const chatVisible = ref(false);
const minimized = ref(false);
const sending = ref(false);
const sessionId = ref("");
const sessionStatus = ref(0); // 0-无会话 1-等待中 2-进行中 3-已结束
const messages = ref([]);
const inputMessage = ref("");
const unreadCount = ref(0);
const messageListRef = ref();

// 定时器
// Source: AURA-X-KYS 消息轮询机制
let unreadTimer = null;
let messagePollingTimer = null;

// 格式化时间
const formatTime = (time) => {
  if (!time) return "";
  const date = new Date(time);
  return date.toLocaleTimeString("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
  });
};

// 打开聊天窗口
const openChat = async () => {
  chatVisible.value = true;
  minimized.value = false;

  try {
    // 创建或获取会话
    const response = await createOrGetSession();
    if (response.code === 1) {
      sessionId.value = response.data.sessionId;
      sessionStatus.value = response.data.status;

      // 加载消息列表
      await loadMessages();

      // 标记消息为已读并重置未读数量
      if (sessionId.value) {
        await markMessagesAsRead(sessionId.value);
        unreadCount.value = 0;
      }

      // 启动消息轮询
      startMessagePolling();
    } else {
      ElMessage.error(response.msg || "创建会话失败");
    }
  } catch (error) {
    console.error("打开客服失败：", error);
    ElMessage.error("打开客服失败，请稍后重试");
  }
};

// 加载消息列表
const loadMessages = async () => {
  if (!sessionId.value) return;

  try {
    const response = await getSessionMessages(sessionId.value);
    if (response.code === 1) {
      // 确保消息按时间排序
      messages.value = response.data.sort((a, b) => {
        return new Date(a.createTime) - new Date(b.createTime);
      });
      // 滚动到底部
      await nextTick();
      scrollToBottom();
    } else {
      console.error("加载消息失败：", response.msg);
    }
  } catch (error) {
    console.error("加载消息异常：", error);
    // 静默处理，避免频繁提示
  }
};

// 发送消息
// Source: AURA-X-KYS 使用真实用户ID发送消息
const handleSendMessage = async () => {
  if (!inputMessage.value.trim() || !sessionId.value) return;

  // 检查用户是否登录
  // if (!userStore.userInfo || !userStore.userInfo.id) {
  //   ElMessage.warning("请先登录");
  //   return;
  // }

  sending.value = true;

  try {
    const response = await sendMessage({
      sessionId: sessionId.value,
      content: inputMessage.value.trim(),
      senderType: 1, // 用户
      messageType: 1, // 文本
      senderId: userStore.userInfo.id, // 使用真实用户ID
    });

    if (response.code === 1) {
      inputMessage.value = "";
      // 重新加载消息列表
      await loadMessages();
    }
  } catch (error) {
    ElMessage.error("发送消息失败");
  } finally {
    sending.value = false;
  }
};

// 结束会话
const handleEndSession = async () => {
  if (!sessionId.value) return;

  try {
    const response = await endSession(sessionId.value);
    if (response.code === 1) {
      sessionStatus.value = 3;
      stopMessagePolling(); // 会话结束后停止轮询
      ElMessage.success("会话已结束");
    } else {
      ElMessage.error(response.msg || "结束会话失败");
    }
  } catch (error) {
    console.error("结束会话失败：", error);
    ElMessage.error("结束会话失败，请稍后重试");
  }
};

// 关闭聊天窗口
const closeChat = () => {
  chatVisible.value = false;
  minimized.value = false;
  // 关闭时不停止轮询，继续后台更新未读消息数量
  // 轮询会在组件卸载时才停止
};

// 最小化聊天窗口
const minimizeChat = () => {
  chatVisible.value = false;
  minimized.value = true;
  // 最小化时继续轮询，以便显示未读消息数
};

// 恢复聊天窗口
const restoreChat = async () => {
  chatVisible.value = true;
  minimized.value = false;

  // 恢复时重新加载消息并标记已读
  if (sessionId.value) {
    await loadMessages();
    await markMessagesAsRead(sessionId.value);
    unreadCount.value = 0;
  }

  // 确保消息轮询正在运行
  if (!messagePollingTimer) {
    startMessagePolling();
  }
};

// 滚动到底部
const scrollToBottom = () => {
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight;
  }
};

// 获取未读消息数量
const loadUnreadCount = async () => {
  try {
    const response = await getUnreadCount();
    if (response.code === 1) {
      unreadCount.value = response.data;
    }
  } catch (error) {
    // 静默处理错误
  }
};

// 轮询获取新消息（当聊天窗口打开或最小化时）
// Source: AURA-X-KYS 消息轮询机制
const startMessagePolling = () => {
  if (messagePollingTimer) return;

  messagePollingTimer = setInterval(async () => {
    if (sessionId.value && sessionStatus.value !== 3) {
      const currentMessageCount = messages.value.length;
      await loadMessages();

      // 如果聊天窗口打开，有新消息时标记为已读
      if (chatVisible.value && messages.value.length > currentMessageCount) {
        await markMessagesAsRead(sessionId.value);
        unreadCount.value = 0;
      }

      // 如果窗口最小化或关闭，更新未读消息数量
      if (!chatVisible.value || minimized.value) {
        await loadUnreadCount();
      }
    }
  }, 3000); // 每3秒轮询一次
};

// 停止消息轮询
const stopMessagePolling = () => {
  if (messagePollingTimer) {
    clearInterval(messagePollingTimer);
    messagePollingTimer = null;
  }
};

// 组件挂载
onMounted(() => {
  // 加载未读消息数量
  loadUnreadCount();

  // 定时获取未读消息数量
  unreadTimer = setInterval(loadUnreadCount, 30000); // 30秒检查一次
});

// 组件卸载
onUnmounted(() => {
  if (unreadTimer) {
    clearInterval(unreadTimer);
  }
  stopMessagePolling();
});
</script>

<style scoped>
.customer-service {
  position: relative;
}

/* 客服入口按钮 */
.service-button {
  position: fixed;
  bottom: 20px;
  right: 20px;
  width: 60px;
  height: 60px;
  background: var(--primary-gradient);
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px color-mix(in srgb, var(--brand-primary) 40%, transparent);
  transition: all 0.3s ease;
  z-index: 1000;
}

.service-button:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 16px color-mix(in srgb, var(--brand-primary) 52%, transparent);
}

.service-button .el-icon {
  color: white;
  font-size: 20px;
  margin-bottom: 2px;
}

.service-button span {
  color: white;
  font-size: 10px;
  font-weight: 500;
}

.unread-badge {
  position: absolute;
  top: -5px;
  right: -5px;
}

/* 聊天窗口 */
.chat-window {
  position: fixed;
  bottom: 20px;
  right: 20px;
  width: 350px;
  height: 500px;
  background: var(--bg-card);
  border-radius: 12px;
  box-shadow: var(--shadow-lg);
  display: flex;
  flex-direction: column;
  z-index: 1001;
  overflow: hidden;
}

/* 聊天头部 */
.chat-header {
  background: var(--primary-gradient);
  color: white;
  padding: 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-info .el-icon {
  font-size: 16px;
}

.header-info span {
  font-weight: 500;
}

.header-actions {
  display: flex;
  gap: 4px;
}

.header-actions .el-button {
  padding: 4px;
  min-height: auto;
  background: rgba(255, 255, 255, 0.2);
  border: none;
  color: white;
}

.header-actions .el-button:hover {
  background: rgba(255, 255, 255, 0.3);
}

/* 消息列表 */
.message-list {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  background: var(--bg-body);
}

.empty-message {
  text-align: center;
  color: var(--text-secondary);
  padding: 40px 20px;
}

.empty-message .el-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-message p {
  margin: 0;
  font-size: 14px;
}

.message-item {
  display: flex;
  margin-bottom: 16px;
  align-items: flex-start;
}

.message-item.message-right {
  flex-direction: row-reverse;
}

.message-avatar {
  margin: 0 8px;
}

.message-content {
  max-width: 70%;
  background: var(--bg-card);
  border-radius: 12px;
  padding: 8px 12px;
  box-shadow: var(--shadow-sm);
  color: var(--text-primary);
}

.message-right .message-content {
  background: var(--brand-primary);
  color: white;
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.sender-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
}

.message-right .sender-name {
  color: rgba(255, 255, 255, 0.8);
}

.message-time {
  font-size: 11px;
  color: var(--text-placeholder);
}

.message-right .message-time {
  color: rgba(255, 255, 255, 0.6);
}

.message-text {
  font-size: 14px;
  line-height: 1.4;
  word-wrap: break-word;
}

/* 输入区域 */
.input-area {
  padding: 16px;
  background: var(--bg-card);
  border-top: 1px solid var(--border-color-light);
}

.input-actions {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* 最小化状态 */
.minimized-chat {
  position: fixed;
  bottom: 20px;
  right: 20px;
  width: 50px;
  height: 50px;
  background: var(--primary-gradient);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px color-mix(in srgb, var(--brand-primary) 40%, transparent);
  transition: all 0.3s ease;
  z-index: 1000;
}

.minimized-chat:hover {
  transform: scale(1.1);
}

.minimized-chat .el-icon {
  color: white;
  font-size: 18px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .chat-window {
    width: calc(100vw - 40px);
    height: calc(100vh - 40px);
    bottom: 20px;
    right: 20px;
  }

  .service-button {
    bottom: 15px;
    right: 15px;
    width: 50px;
    height: 50px;
  }

  .minimized-chat {
    bottom: 15px;
    right: 15px;
    width: 45px;
    height: 45px;
  }
}

/* Dark Mode Overrides */
html.dark .chat-window {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
}

html.dark .message-list {
  background: #121212;
}

html.dark .message-content {
  background: #2d2d2d;
  color: var(--text-primary);
}

html.dark .message-right .message-content {
  background: var(--brand-primary);
  color: white;
}

html.dark .input-area {
  background: var(--bg-card);
  border-top: 1px solid var(--border-color);
}
</style>
