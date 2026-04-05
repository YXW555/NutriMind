<template>
  <view class="page">
    
    <view class="sidebar-overlay" :class="{ 'is-active': showSidebar }" @click="closeSidebar"></view>
    
    <view class="sidebar" :class="{ 'is-open': showSidebar }">
      <view class="sidebar-header">
        <view class="new-chat-btn" @click="startNewChat">
          <text class="new-chat-icon">+</text>
          <text class="new-chat-text">新建对话</text>
        </view>
      </view>
      
      <scroll-view scroll-y class="sidebar-list">
        <view v-if="sessions.length === 0" class="sidebar-empty">
          <text>暂无历史对话</text>
        </view>
        
        <view
          v-for="sess in sessions"
          :key="sess.id"
          class="session-item"
          :class="{ active: activeSessionId === sess.id }"
          @click="switchSession(sess.id)"
        >
          <view class="session-icon">💬</view>
          <view class="session-info">
            <text class="session-title">{{ sess.title }}</text>
            <text class="session-time">{{ formatLocalTime(sess.updatedAt) }}</text>
          </view>
        </view>
      </scroll-view>
    </view>

    <view class="content-shell">
      <view class="sticky-topbar">
        <view class="custom-nav-bar">
          <view class="nav-left">
            <view class="menu-icon-btn" @click="openSidebar">
              <text class="menu-icon">☰</text>
            </view>
            <view class="nav-title-box">
              <text class="nav-title">营养顾问</text>
              <text class="nav-subtitle">AI 随时解答</text>
            </view>
          </view>
        </view>
      </view>

      <view class="conversation-container">
        
        <view v-if="activeMessages.length === 0" class="empty-chat-tip">
          <text class="tip-text">开启了一个新对话，请直接向我提问</text>
        </view>

        <view
          v-for="message in messageTimeline"
          :key="message.id"
        >
          <view v-if="message.showDivider" class="timeline-divider">
            <text class="timeline-divider-text">{{ message.dividerLabel }}</text>
          </view>

          <view
            class="message-row"
            :class="{ user: isUserMessage(message) }"
            :id="messageAnchorId(message)"
          >
            <view v-if="!isUserMessage(message)" class="message-avatar assistant">
              <text class="message-avatar-text">{{ assistantAvatarText }}</text>
            </view>

            <view class="message-card" :class="{ user: isUserMessage(message) }">
              <view class="message-head" v-if="!isUserMessage(message)">
                <text class="message-time">{{ formatLocalTime(message.createdAt) }}</text>
              </view>

              <text class="message-text" :class="{ 'text-white': isUserMessage(message) }">{{ message.content }}</text>

              <view
                v-if="!isUserMessage(message) && message.references.length"
                class="reference-shell"
              >
                <view class="reference-toggle" @click="toggleReferences(message)">
                  <text class="reference-toggle-text">
                    {{ isReferenceExpanded(message) ? '收起引用依据' : `查看引用依据 ${message.references.length}` }}
                  </text>
                  <text class="reference-toggle-icon">{{ isReferenceExpanded(message) ? '-' : '+' }}</text>
                </view>

                <view v-if="isReferenceExpanded(message)" class="reference-list">
                  <text class="reference-title">引用依据</text>
                  <view
                    v-for="(reference, index) in message.references"
                    :key="`${message.id}-${index}`"
                    class="reference-card"
                  >
                    <view class="reference-head">
                      <text class="reference-tag">{{ reference.title || '知识片段' }}</text>
                      <text v-if="reference.section" class="reference-section">{{ reference.section }}</text>
                    </view>
                    <text
                      v-if="reference.authority || reference.sourceName"
                      class="reference-source"
                    >
                      {{ [reference.authority, reference.sourceName].filter(Boolean).join(' · ') }}
                    </text>
                    <text class="reference-excerpt">{{ reference.excerpt || '该条回答引用了相关营养知识片段。' }}</text>
                    <text v-if="reference.sourceUrl" class="reference-url">{{ reference.sourceUrl }}</text>
                  </view>
                </view>
              </view>
            </view>

            <view v-if="isUserMessage(message)" class="message-avatar user">
              <text class="message-avatar-text">{{ userAvatarText }}</text>
            </view>
          </view>
        </view>

        <view id="message-bottom" class="scroll-anchor"></view>
      </view>
    </view>

    <view class="composer-shell">
      <view class="composer">
        <view class="composer-main">
          <input
            v-model="draft"
            class="composer-input"
            placeholder="输入你的问题..."
            confirm-type="send"
            :disabled="sending"
            @confirm="sendMessage()"
          />
        </view>
        <button class="send-button" :class="{ 'is-active': draft.trim().length > 0 }" :disabled="sending || draft.trim().length === 0" @click="sendMessage()">
          {{ sending ? '...' : '发送' }}
        </button>
      </view>
      <view class="composer-safe-area">
        <text class="safe-area-text">{{ sending ? '正在思考中...' : '支持提问热量差距、外食替换等问题' }}</text>
      </view>
    </view>

    <app-tab-bar current="advisor" />
  </view>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { ensureLoggedIn, getProfile } from '@/utils/auth.js'

const rawMessages = ref([])
const draft = ref('')
const sending = ref(false)
const overview = ref(getProfile() || {})
const expandedReferenceState = ref({})

// 侧边栏和多会话状态管理
const showSidebar = ref(false)
const sessions = ref([]) 
const activeSessionId = ref(null)

// 核心修复点 1：本地持久化存储强制拆分的“消息断点 ID”
const splitMessageIds = ref(uni.getStorageSync('chat_split_ids') || [])

const userAvatarText = computed(() => {
  const source = overview.value?.nickname || overview.value?.username || '我'
  return String(source).slice(0, 1).toUpperCase()
})
const assistantAvatarText = computed(() => 'AI')

// 当前激活的会话消息列表
const activeMessages = computed(() => {
  if (activeSessionId.value === 'new_temp') return []
  const session = sessions.value.find(s => s.id === activeSessionId.value)
  return session ? session.messages : []
})

// 时间线聚合（按日期分割线）
const messageTimeline = computed(() => {
  let previousDateKey = ''
  return activeMessages.value.map(item => {
    const d = new Date(safeParseDate(item.createdAt))
    const currentDateKey = `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`
    const showDivider = Boolean(currentDateKey) && currentDateKey !== previousDateKey
    previousDateKey = currentDateKey
    return {
      ...item,
      showDivider,
      dividerLabel: showDivider ? (currentDateKey === `${new Date().getFullYear()}-${new Date().getMonth() + 1}-${new Date().getDate()}` ? '今天' : currentDateKey) : ''
    }
  })
})

function isUserMessage(message) {
  return String(message?.role || '').toUpperCase() === 'USER'
}

function trimText(value, maxLength) {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  if (text.length <= maxLength) {
    return text
  }
  return `${text.slice(0, Math.max(0, maxLength - 3))}...`
}

function openSidebar() {
  showSidebar.value = true
}

function closeSidebar() {
  showSidebar.value = false
}

// 彻底修复8小时时差问题的本地时间格式化
function safeParseDate(isoString) {
  if (!isoString) return new Date().getTime()
  let safeStr = String(isoString).replace(/-/g, '/').replace('T', ' ')
  let d = new Date(isoString)
  if (isNaN(d.getTime())) {
    d = new Date(safeStr)
  }
  return d.getTime()
}

function formatLocalTime(isoString) {
  if (!isoString) return ''
  const timestamp = safeParseDate(isoString)
  const now = Date.now()
  let diffMins = Math.floor((now - timestamp) / 60000)
  
  if (diffMins < 0) diffMins = 0

  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins} 分钟前`
  const diffHours = Math.floor(diffMins / 60)
  if (diffHours < 24) return `${diffHours} 小时前`
  const diffDays = Math.floor(diffHours / 24)
  if (diffDays < 30) return `${diffDays} 天前`
  
  const d = new Date(timestamp)
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

// 分组逻辑
function groupMessagesIntoSessions(items) {
  let tempSessions = []
  let currentSess = null

  items.forEach(msg => {
    if (!currentSess) {
      currentSess = { 
        id: `sess-${msg.id}`, 
        title: isUserMessage(msg) ? trimText(msg.content, 15) : '新对话', 
        messages: [msg], 
        updatedAt: msg.createdAt 
      }
      tempSessions.push(currentSess)
    } else {
      const timeDiffMins = (safeParseDate(msg.createdAt) - safeParseDate(currentSess.updatedAt)) / 60000
      
      // 核心修复点 2：判断这条消息是不是我们标记过的“新对话第一条消息”
      const isManualSplit = splitMessageIds.value.includes(msg.id)

      // 如果时间超过120分钟，或者它是一条被强制标记的新对话开端，就新建组！
      if (isUserMessage(msg) && (timeDiffMins > 120 || isManualSplit)) {
        currentSess = { 
          id: `sess-${msg.id}`, 
          title: trimText(msg.content, 15), 
          messages: [msg], 
          updatedAt: msg.createdAt 
        }
        tempSessions.push(currentSess)
      } else {
        currentSess.messages.push(msg)
        currentSess.updatedAt = msg.createdAt
        if (currentSess.title === '新对话' && isUserMessage(msg)) {
          currentSess.title = trimText(msg.content, 15)
        }
      }
    }
  })

  return tempSessions.reverse()
}

function normalizeMessageItem(message) {
  const content = String(message?.content || '')
  return {
    id: message?.id || `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    role: String(message?.role || 'ASSISTANT').toUpperCase(),
    content,
    references: Array.isArray(message?.references) ? message.references : [],
    createdAt: message?.createdAt || new Date().toISOString()
  }
}

function messageAnchorId(message) {
  const rawId = String(message?.id || '')
  return `message-${rawId.replace(/[^A-Za-z0-9_-]/g, '-')}`
}

function isReferenceExpanded(message) {
  return Boolean(expandedReferenceState.value[String(message?.id)])
}

function toggleReferences(message) {
  const messageId = String(message?.id || '')
  if (!messageId) {
    return
  }
  expandedReferenceState.value[messageId] = !expandedReferenceState.value[messageId]
}

// 侧边栏：新建对话
function startNewChat() {
  activeSessionId.value = 'new_temp'
  closeSidebar()
}

// 侧边栏：切换对话
function switchSession(sessionId) {
  activeSessionId.value = sessionId
  closeSidebar()
  nextTick(() => {
    scrollToBottom()
  })
}

// 核心修复点 3：让 loadMessages 接收一个参数，用来判断要不要标记强制截断
async function loadMessages(options = { markLastAsSplit: false }) {
  try {
    const response = await request.get('/advisor/messages')
    const items = Array.isArray(response) ? response : []
    rawMessages.value = items.map(normalizeMessageItem)
    
    // 如果这是新对话的第一条消息刚发完，我们需要找到后端真正生成的该条消息的 ID 并记录下来
    if (options.markLastAsSplit) {
      // 从所有消息里找到最后一条用户发送的消息（也就是刚刚发出去的那条）
      const lastUserMsg = [...rawMessages.value].reverse().find(m => isUserMessage(m))
      if (lastUserMsg && !splitMessageIds.value.includes(lastUserMsg.id)) {
        splitMessageIds.value.push(lastUserMsg.id)
        uni.setStorageSync('chat_split_ids', splitMessageIds.value)
      }
    }

    sessions.value = groupMessagesIntoSessions(rawMessages.value)
    
    if (activeSessionId.value !== 'new_temp') {
      const sessionExists = sessions.value.some(s => s.id === activeSessionId.value)
      if (!sessionExists && sessions.value.length > 0) {
        activeSessionId.value = sessions.value[0].id
      }
    }
    
    await nextTick()
    scrollToBottom()
  } catch (error) {
    console.log('load advisor messages failed', error)
  }
}

async function sendMessage() {
  const content = String(draft.value).trim()
  if (!content || sending.value) {
    return
  }

  const tempMessage = normalizeMessageItem({
    id: `local-${Date.now()}`,
    role: 'USER',
    content,
    references: [],
    createdAt: new Date().toISOString()
  })

  // 记录一下：发消息之前是不是在一个强制的新对话里
  const isForcedNewSession = !activeSessionId.value || activeSessionId.value === 'new_temp'

  if (isForcedNewSession) {
    const newSess = {
      id: `temp-sess-${Date.now()}`,
      title: trimText(content, 15),
      messages: [tempMessage],
      updatedAt: tempMessage.createdAt
    }
    sessions.value.unshift(newSess)
    activeSessionId.value = newSess.id
  } else {
    const currentSession = sessions.value.find(s => s.id === activeSessionId.value)
    if (currentSession) {
      currentSession.messages.push(tempMessage)
      currentSession.updatedAt = tempMessage.createdAt
    }
  }

  draft.value = ''
  sending.value = true
  await nextTick()
  scrollToBottom()

  try {
    await request.post('/advisor/messages', { content })
    // 核心修复点 4：把 isForcedNewSession 传给 loadMessages
    await loadMessages({ markLastAsSplit: isForcedNewSession }) 
  } catch (error) {
    console.log('send advisor message failed', error)
  } finally {
    sending.value = false
    await nextTick()
    scrollToBottom()
  }
}

function scrollToBottom() {
  const query = uni.createSelectorQuery()
  query.select('#message-bottom').boundingClientRect()
  query.selectViewport().scrollOffset()
  query.exec((result) => {
    const anchor = result && result[0]
    const viewport = result && result[1]
    if (!anchor || !viewport) {
      return
    }

    uni.pageScrollTo({
      scrollTop: (anchor.top || 0) + (viewport.scrollTop || 0) - 180,
      duration: 220
    })
  })
}

onShow(async () => {
  if (!ensureLoggedIn()) return
  overview.value = getProfile() || {}
  await loadMessages()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx 0 calc(280rpx + env(safe-area-inset-bottom));
  background: #f4f5f7; 
}

/* 根据新头部微调内边距 */
.content-shell {
  padding: calc(env(safe-area-inset-top) + 120rpx) 24rpx 0;
}

.sticky-topbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 70;
  padding: env(safe-area-inset-top) 24rpx 12rpx; /* 增加顶部安全区高度兼容 */
  box-sizing: border-box;
  background: #f4f5f7;
  box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.02);
}

/* === 新增：自定义顶部导航栏样式 === */
.custom-nav-bar {
  display: flex;
  align-items: center;
  height: 88rpx;
  width: 100%;
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.menu-icon-btn {
  width: 64rpx;
  height: 64rpx;
  background: rgba(47, 125, 107, 0.1);
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: opacity 0.2s;
}

.menu-icon-btn:active {
  opacity: 0.7;
}

.menu-icon {
  font-size: 32rpx;
  color: var(--nm-primary);
  line-height: 1;
}

.nav-title-box {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.nav-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #333333;
  line-height: 1.2;
}

.nav-subtitle {
  font-size: 20rpx;
  color: #94a3b8;
  margin-top: 4rpx;
  line-height: 1.2;
}

/* === 侧边栏抽屉样式 === */
.sidebar-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 998;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.3s ease;
}

.sidebar-overlay.is-active {
  opacity: 1;
  pointer-events: auto;
}

.sidebar {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  width: 580rpx;
  background: #ffffff;
  z-index: 999;
  transform: translateX(-100%);
  transition: transform 0.3s cubic-bezier(0.2, 0.8, 0.2, 1);
  display: flex;
  flex-direction: column;
  padding-top: env(safe-area-inset-top);
  box-shadow: 10rpx 0 30rpx rgba(0,0,0,0.1);
}

.sidebar.is-open {
  transform: translateX(0);
}

.sidebar-header {
  padding: 32rpx 24rpx;
  border-bottom: 1rpx solid #f1f5f9;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx 32rpx;
  background: var(--nm-primary);
  border-radius: 16rpx;
  color: #ffffff;
  box-shadow: 0 8rpx 24rpx rgba(47, 125, 107, 0.25);
  transition: opacity 0.2s;
}

.new-chat-btn:active {
  opacity: 0.8;
}

.new-chat-icon {
  font-size: 36rpx;
  font-weight: 400;
}

.new-chat-text {
  font-size: 30rpx;
  font-weight: 700;
}

.sidebar-list {
  flex: 1;
  height: 0;
  padding: 16rpx 24rpx;
}

.sidebar-empty {
  padding: 60rpx 0;
  text-align: center;
  color: #94a3b8;
  font-size: 26rpx;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx;
  border-radius: 16rpx;
  margin-bottom: 12rpx;
  transition: background-color 0.2s;
}

.session-item:active {
  background: #f8faf9;
}

.session-item.active {
  background: rgba(47, 125, 107, 0.08);
}

.session-icon {
  font-size: 32rpx;
}

.session-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.session-title {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--nm-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-time {
  font-size: 22rpx;
  color: #94a3b8;
}

/* === 聊天主区域样式 === */
.conversation-container {
  margin-top: 18rpx;
}

.empty-chat-tip {
  display: flex;
  justify-content: center;
  margin: 60rpx 0;
}

.tip-text {
  padding: 12rpx 24rpx;
  background: rgba(0,0,0,0.06);
  border-radius: 999rpx;
  font-size: 24rpx;
  color: var(--nm-muted);
}

.timeline-divider {
  display: flex;
  justify-content: center;
  margin: 24rpx 0;
}

.timeline-divider-text {
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(0, 0, 0, 0.06);
  font-size: 22rpx;
  font-weight: 500;
  color: var(--nm-muted);
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  margin-bottom: 32rpx;
}

.message-row.user {
  justify-content: flex-end;
}

.message-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%; 
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 4rpx;
}

.message-avatar.assistant {
  background: linear-gradient(135deg, var(--nm-primary), var(--nm-primary-dark));
}

.message-avatar.user {
  background: #e2e8f0;
}

.message-avatar-text {
  font-size: 28rpx;
  font-weight: 800;
  color: #ffffff;
}

.message-avatar.user .message-avatar-text {
  color: #475569;
}

.message-card {
  width: auto;
  max-width: calc(100% - 120rpx);
  padding: 20rpx 28rpx;
  border-radius: 4rpx 32rpx 32rpx 32rpx; 
  background: #ffffff;
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.02);
}

.message-card.user {
  border-radius: 32rpx 4rpx 32rpx 32rpx;
  background: var(--nm-primary);
}

.message-head {
  display: flex;
  align-items: center;
  margin-bottom: 8rpx;
}

.message-time {
  font-size: 22rpx;
  color: var(--nm-muted);
}

.message-text {
  display: block;
  font-size: 30rpx;
  line-height: 1.6;
  color: var(--nm-text);
  word-break: break-all;
}

.text-white {
  color: #ffffff !important;
}

.reference-shell {
  margin-top: 20rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid rgba(0, 0, 0, 0.05);
}

.reference-toggle {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12rpx 0;
}

.reference-toggle-text,
.reference-toggle-icon {
  color: var(--nm-muted);
  font-size: 24rpx;
}

.reference-list {
  margin-top: 12rpx;
}

.reference-title {
  display: block;
  margin-bottom: 12rpx;
  font-size: 22rpx;
  color: var(--nm-muted);
}

.reference-card {
  padding: 16rpx;
  border-radius: 12rpx;
  background: #f8faf9;
  margin-bottom: 12rpx;
}

.reference-head {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-bottom: 6rpx;
}

.reference-tag {
  font-size: 22rpx;
  font-weight: 600;
  color: var(--nm-primary);
}

.reference-excerpt {
  font-size: 24rpx;
  color: #64748b;
  line-height: 1.5;
}

.reference-source {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  font-weight: 700;
  color: var(--nm-primary-dark);
}

.reference-url {
  display: block;
  margin-top: 8rpx;
  font-size: 21rpx;
  line-height: 1.5;
  color: #64748b;
  word-break: break-all;
}

.scroll-anchor {
  height: 10rpx;
}

.composer-shell {
  position: fixed;
  left: 0;
  right: 0;
  bottom: calc(100rpx + env(safe-area-inset-bottom));
  z-index: 50;
  background: #f4f5f7;
  padding: 20rpx 24rpx 10rpx;
}

.composer {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.composer-main {
  flex: 1;
}

.composer-input {
  width: 100%;
  height: 80rpx;
  padding: 0 32rpx;
  border-radius: 999rpx; 
  background: #ffffff;
  font-size: 28rpx;
  color: var(--nm-text);
  box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.03);
}

.send-button {
  width: 120rpx;
  height: 80rpx;
  border-radius: 999rpx;
  background: #e2e8f0;
  color: #94a3b8;
  font-size: 28rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  padding: 0;
}

.send-button::after {
  border: none;
}

.send-button.is-active {
  background: var(--nm-primary);
  color: #ffffff;
}

.composer-safe-area {
  text-align: center;
  padding-top: 16rpx;
}

.safe-area-text {
  font-size: 20rpx;
  color: #94a3b8;
}
</style>