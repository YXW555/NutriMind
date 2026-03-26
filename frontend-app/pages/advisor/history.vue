<template>
  <view class="page">
    <view class="bg-orb orb-left"></view>
    <view class="bg-orb orb-right"></view>
    <view class="bg-grid"></view>

    <view class="content-shell">
      <app-page-header
        title="历史对话"
        subtitle="历史与快捷提问"
        fallback-url="/pages/advisor/index"
      />

      <view class="panel">
        <view class="section-head">
          <view>
            <text class="section-title">快捷提问</text>
            <text class="section-desc">点一下直接发问</text>
          </view>
          <text class="section-link" @click="backToChat">聊天页</text>
        </view>

        <view class="prompt-grid">
          <view
            v-for="item in quickPrompts"
            :key="item.title"
            class="prompt-item"
            @click="openChatWithPrompt(item.text)"
          >
            <text class="prompt-badge">{{ item.badge }}</text>
            <text class="prompt-title">{{ item.title }}</text>
            <text class="prompt-desc">{{ item.text }}</text>
          </view>
        </view>
      </view>

      <view class="panel">
        <view class="section-head compact">
          <view>
            <text class="section-title">最近提问</text>
            <text class="section-desc">{{ historySummary }}</text>
          </view>
          <text v-if="historyItems.length" class="section-link" @click="resumeHistory(historyItems[0])">继续最近一条</text>
        </view>

        <view v-if="loading" class="empty-state">
          <text class="empty-title">正在读取</text>
          <text class="empty-desc">请稍候</text>
        </view>

        <view v-else-if="historyItems.length" class="history-list">
          <view
            v-for="item in historyItems"
            :key="item.id"
            class="history-item"
            @click="resumeHistory(item)"
          >
            <view class="history-item-top">
              <text class="history-rank">{{ item.order }}</text>
              <text class="history-time">{{ item.timeText }}</text>
            </view>
            <text class="history-title">{{ item.title }}</text>
            <text class="history-desc">{{ item.desc }}</text>
          </view>
        </view>

        <view v-else class="empty-state">
          <text class="empty-title">还没有历史提问</text>
          <text class="empty-desc">先问一个问题吧</text>
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

const quickPrompts = [
  {
    badge: '今日',
    title: '看今天差什么',
    text: '结合我今天的记录和目标，告诉我现在最需要补的营养是什么，并给出下一餐建议。'
  },
  {
    badge: '晚餐',
    title: '晚餐怎么安排',
    text: '如果我今晚想吃得清淡一点，但又不想蛋白质太低，晚餐可以怎么搭配？'
  },
  {
    badge: '外卖',
    title: '外卖替换建议',
    text: '如果我要点外卖，怎样选能更接近我的减脂或增肌目标？请给我几个替换思路。'
  },
  {
    badge: '训练',
    title: '训练日加餐',
    text: '训练日前后怎么安排加餐更合适？请结合我的近期记录给建议。'
  }
]

const messages = ref([])
const loading = ref(false)

const historyItems = computed(() => {
  return messages.value
    .filter(item => isUserMessage(item))
    .slice()
    .reverse()
    .slice(0, 12)
    .map((item, index) => ({
      id: item.id,
      order: `0${index + 1}`.slice(-2),
      title: trimText(item.content, 20),
      desc: trimText(item.content, 44),
      fullContent: item.content,
      timeText: formatRelativeTime(item.createdAt)
    }))
})

const historySummary = computed(() => {
  if (!historyItems.value.length) {
    return '暂无历史'
  }
  return `最近 ${historyItems.value.length} 条`
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

function openChatWithPrompt(content) {
  savePendingAdvisorAction({
    type: 'send',
    content
  })
  backToChat()
}

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
  overflow-x: hidden;
  background:
    radial-gradient(circle at top left, rgba(14, 165, 109, 0.15) 0%, rgba(14, 165, 109, 0) 34%),
    radial-gradient(circle at top right, rgba(245, 158, 11, 0.1) 0%, rgba(245, 158, 11, 0) 28%),
    linear-gradient(180deg, #f5f2ea 0%, #eef8f2 100%);
}

.bg-orb {
  position: absolute;
  border-radius: 999rpx;
  filter: blur(30rpx);
  opacity: 0.75;
}

.orb-left {
  top: 220rpx;
  left: -90rpx;
  width: 260rpx;
  height: 260rpx;
  background: rgba(14, 165, 109, 0.14);
}

.orb-right {
  top: 520rpx;
  right: -70rpx;
  width: 240rpx;
  height: 240rpx;
  background: rgba(59, 130, 246, 0.12);
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(15, 23, 42, 0.03) 1rpx, transparent 1rpx),
    linear-gradient(90deg, rgba(15, 23, 42, 0.03) 1rpx, transparent 1rpx);
  background-size: 40rpx 40rpx;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.32), transparent 78%);
}

.content-shell {
  position: relative;
  z-index: 1;
  padding: 0 28rpx;
}

.entry-card,
.panel {
  margin-top: 24rpx;
  padding: 28rpx;
  border-radius: 34rpx;
  background: rgba(255, 255, 255, 0.92);
  border: 1rpx solid rgba(255, 255, 255, 0.76);
  box-shadow: var(--nm-shadow);
}

.entry-card {
  background: linear-gradient(150deg, #172033 0%, #21344b 56%, #31546e 100%);
}

.entry-head,
.section-head,
.history-item-top,
.entry-actions {
  display: flex;
}

.entry-head,
.section-head,
.history-item-top {
  justify-content: space-between;
}

.section-head {
  align-items: flex-start;
  gap: 18rpx;
}

.section-head.compact {
  align-items: center;
}

.entry-copy {
  flex: 1;
  min-width: 0;
}

.entry-title,
.entry-desc {
  display: block;
  color: #ffffff;
}

.entry-title {
  font-size: 36rpx;
  line-height: 1.4;
  font-weight: 800;
}

.entry-desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.75;
  color: rgba(255, 255, 255, 0.74);
}

.entry-badge {
  width: 112rpx;
  height: 112rpx;
  margin-left: 18rpx;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.12);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.entry-badge-top,
.entry-badge-bottom {
  color: #ffffff;
  font-weight: 800;
}

.entry-badge-top {
  font-size: 20rpx;
  opacity: 0.7;
  letter-spacing: 2rpx;
}

.entry-badge-bottom {
  margin-top: 6rpx;
  font-size: 30rpx;
}

.entry-actions {
  margin-top: 22rpx;
}

.section-title {
  display: block;
  font-size: 34rpx;
  font-weight: 800;
  color: #111827;
}

.section-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #6b7280;
}

.section-link {
  flex-shrink: 0;
  padding: 12rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(14, 165, 109, 0.12);
  font-size: 24rpx;
  font-weight: 700;
  color: var(--nm-primary-dark);
}

.prompt-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18rpx;
  margin-top: 22rpx;
}

.prompt-item,
.history-item,
.empty-state {
  border-radius: 26rpx;
  background: #f8faf8;
  border: 1rpx solid rgba(14, 165, 109, 0.08);
}

.prompt-item {
  padding: 22rpx;
}

.prompt-badge {
  display: inline-flex;
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(14, 165, 109, 0.12);
  font-size: 22rpx;
  font-weight: 800;
  color: var(--nm-primary-dark);
}

.prompt-title {
  display: block;
  margin-top: 14rpx;
  font-size: 28rpx;
  font-weight: 800;
  color: #111827;
}

.prompt-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #6b7280;
}

.history-list {
  margin-top: 22rpx;
}

.history-item {
  padding: 22rpx;
}

.history-item + .history-item {
  margin-top: 14rpx;
}

.history-item-top {
  align-items: center;
  gap: 16rpx;
}

.history-rank {
  min-width: 60rpx;
  height: 44rpx;
  border-radius: 999rpx;
  background: rgba(23, 32, 51, 0.08);
  font-size: 22rpx;
  font-weight: 800;
  color: #334155;
  display: flex;
  align-items: center;
  justify-content: center;
}

.history-time {
  font-size: 22rpx;
  color: #64748b;
}

.history-title {
  display: block;
  margin-top: 14rpx;
  font-size: 28rpx;
  font-weight: 800;
  line-height: 1.5;
  color: #111827;
}

.history-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #6b7280;
}

.empty-state {
  margin-top: 22rpx;
  padding: 28rpx 24rpx;
}

.empty-title {
  display: block;
  font-size: 28rpx;
  font-weight: 800;
  color: #111827;
}

.empty-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #6b7280;
}

.secondary-button {
  width: 100%;
  height: 88rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.14);
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 800;
}

.secondary-button::after {
  border: none;
}

@media screen and (max-width: 720px) {
  .prompt-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
