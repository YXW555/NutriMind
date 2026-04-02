<template>
  <view class="page">
    <view class="content-shell">
      <app-page-header
        title="历史对话"
        subtitle="过往提问记录"
        fallback-url="/pages/advisor/index"
      />

      <view class="history-container">
        <view v-if="loading" class="empty-state">
          <text class="empty-title">正在读取</text>
          <text class="empty-desc">请稍候...</text>
        </view>

        <view v-else-if="historyItems.length" class="history-list">
          <view
            v-for="item in historyItems"
            :key="item.id"
            class="history-item"
            @click="resumeHistory(item)"
          >
            <view class="history-icon">
              <text class="icon-text">💬</text>
            </view>
            
            <view class="history-content">
              <view class="history-top">
                <text class="history-title">{{ item.title }}</text>
                <text class="history-time">{{ item.timeText }}</text>
              </view>
              <text class="history-desc">{{ item.desc }}</text>
            </view>
          </view>
        </view>

        <view v-else class="empty-state">
          <text class="empty-title">还没有历史提问</text>
          <text class="empty-desc">返回聊天页问一个问题吧</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { ensureLoggedIn } from '@/utils/auth.js'
import { formatRelativeTime } from '@/utils/format.js'

const ADVISOR_PENDING_ACTION_KEY = 'nutrimind_advisor_pending_action'

const messages = ref([])
const loading = ref(false)

// 纯粹的历史记录数据处理
const historyItems = computed(() => {
  return messages.value
    .filter(item => isUserMessage(item))
    .slice()
    .reverse()
    .slice(0, 30) // 既然只有历史列表了，可以稍微多展示几条，这里改成 30 条
    .map((item, index) => ({
      id: item.id,
      title: trimText(item.content, 12),
      desc: trimText(item.content, 30),
      fullContent: item.content,
      timeText: formatRelativeTime(item.createdAt)
    }))
})

function isUserMessage(message) {
  return String(message?.role || '').toUpperCase() === 'USER'
}

function normalizeMessageItem(message) {
  return {
    id: message?.id || `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    role: String(message?.role || 'ASSISTANT').toUpperCase(),
    content: String(message?.content || ''),
    createdAt: message?.createdAt || new Date().toISOString()
  }
}

function trimText(value, maxLength) {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  if (text.length <= maxLength) {
    return text
  }
  return `${text.slice(0, Math.max(0, maxLength - 3))}...`
}

async function loadMessages() {
  const response = await request.get('/advisor/messages')
  const items = Array.isArray(response) ? response : []
  messages.value = items.map(normalizeMessageItem)
}

function savePendingAdvisorAction(action) {
  uni.setStorageSync(ADVISOR_PENDING_ACTION_KEY, JSON.stringify(action))
}

function backToChat() {
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  if (Array.isArray(pages) && pages.length > 1) {
    uni.navigateBack({
      delta: 1
    })
    return
  }

  uni.reLaunch({
    url: '/pages/advisor/index'
  })
}

// 点击历史记录，带着问题返回聊天页
function resumeHistory(item) {
  if (!item) {
    return
  }

  savePendingAdvisorAction({
    type: 'focus',
    messageId: item.id,
    draft: item.fullContent
  })
  backToChat()
}

onShow(async () => {
  if (!ensureLoggedIn()) {
    return
  }

  loading.value = true
  try {
    await loadMessages()
  } catch (error) {
    console.log('load advisor history failed', error)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page {
  position: relative;
  min-height: 100vh;
  padding: 32rpx 0 60rpx;
  background: #f4f5f7; /* 统一的浅灰背景色，显得干净 */
}

.content-shell {
  position: relative;
  z-index: 1;
  padding: 0 28rpx;
}

.history-container {
  margin-top: 32rpx;
  background: #ffffff;
  border-radius: 24rpx;
  padding: 10rpx 0;
  box-shadow: 0 4rpx 16rpx rgba(0,0,0,0.03); /* 轻微的阴影提升质感 */
}

.history-list {
  display: flex;
  flex-direction: column;
}

.history-item {
  display: flex;
  align-items: center;
  padding: 28rpx 32rpx; /* 稍微加大了上下间距，更透气 */
  transition: background-color 0.2s;
}

/* 列表分割线，最后一条不显示 */
.history-item:not(:last-child) {
  border-bottom: 1rpx solid rgba(0, 0, 0, 0.04);
}

.history-item:active {
  background-color: #f8faf9;
}

/* 气泡图标的样式 */
.history-icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: 20rpx;
  background: rgba(47, 125, 107, 0.08); /* 使用品牌色的透明背景 */
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 24rpx;
  flex-shrink: 0;
}

.icon-text {
  font-size: 36rpx;
}

.history-content {
  flex: 1;
  min-width: 0; /* 防止文本过长挤破布局 */
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.history-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8rpx;
}

.history-title {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--nm-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.history-time {
  font-size: 22rpx;
  color: #94a3b8;
  flex-shrink: 0;
  margin-left: 16rpx;
}

.history-desc {
  display: block;
  font-size: 26rpx;
  color: #64748b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.empty-state {
  padding: 100rpx 24rpx;
  text-align: center;
}

.empty-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: var(--nm-text);
}

.empty-desc {
  display: block;
  margin-top: 16rpx;
  font-size: 26rpx;
  color: #94a3b8;
}
</style>