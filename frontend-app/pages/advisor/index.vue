<template>
  <view class="page">
    <view class="content-shell">
      <view class="sticky-topbar">
        <app-page-header
        title="营养顾问"
        subtitle="开始聊天"
        :show-back="false"
        >
          <template #right>
            <view class="header-action" @click="openAdvisorHistoryPage">
              <text class="header-action-label">历史</text>
            </view>
          </template>
        </app-page-header>
      </view>

      <view class="conversation-card">
        <view class="section-head compact">
          <view>
            <text class="section-title">当前对话</text>
            <text class="section-desc">
              {{ sending
                ? '正在生成回答...'
                : '直接提问' }}
            </text>
          </view>
          <text class="section-link" @click="openAdvisorHistoryPage">历史页</text>
        </view>

        <view v-if="userMessageCount === 0" class="conversation-tip">
          <text class="conversation-tip-title">直接输入你的问题</text>
          <text class="conversation-tip-desc">右上角可查看历史</text>
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
              <view class="message-head">
                <view class="role-chip" :class="{ user: isUserMessage(message) }">
                  <text class="role-chip-text">{{ roleLabel(message) }}</text>
                </view>
                <text class="message-time">{{ formatRelativeTime(message.createdAt) }}</text>
              </view>

              <text class="message-text">{{ message.content }}</text>

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
                    <text class="reference-excerpt">{{ reference.excerpt || '该条回答引用了相关营养知识片段。' }}</text>
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
            placeholder="例如：今天晚餐想控卡但保证蛋白质，该怎么吃？"
            confirm-type="send"
            :disabled="sending"
            @confirm="sendMessage()"
          />
          <text class="composer-hint">
            {{ sending
              ? '顾问正在结合你的记录、目标和知识片段整理回答...'
              : '支持提问热量差距、蛋白质补足、外食替换、减脂增肌、特殊需求等问题。' }}
          </text>
        </view>
        <button class="send-button" :disabled="sending" @click="sendMessage()">
          {{ sending ? '生成中' : '发送' }}
        </button>
      </view>
    </view>

    <app-tab-bar current="advisor" />
  </view>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { ensureLoggedIn, formatToday, getProfile } from '@/utils/auth.js'
import { formatNumber, formatRelativeTime, normalizeDateInput } from '@/utils/format.js'

const heroTags = ['RAG 检索', '近期记录', '目标联动', '知识引用']
const quickPrompts = [
  {
    badge: '差',
    title: '看今天差什么',
    text: '结合我今天的记录和目标，告诉我现在最需要补的营养是什么，并给出下一餐建议。'
  },
  {
    badge: '晚',
    title: '晚餐怎么安排',
    text: '如果我今晚想吃得清淡一点，但又不想蛋白质太低，晚餐可以怎么搭配？'
  },
  {
    badge: '外',
    title: '外卖替换建议',
    text: '如果我要点外卖，怎样选能更接近我的减脂或增肌目标？请给我几个替换思路。'
  },
  {
    badge: '练',
    title: '训练日加餐',
    text: '训练日前后怎么安排加餐更合适？请结合我的近期记录给建议。'
  }
]

const messages = ref([])
const draft = ref('')
const sending = ref(false)
const contextLoading = ref(false)
const overview = ref(getProfile() || {})
const report = ref(createEmptyReport())
const dailyRecord = ref(createEmptyDailyRecord())
const today = ref(formatToday())
const answerPageState = ref({})
const expandedReferenceState = ref({})

const ADVISOR_PENDING_ACTION_KEY = 'nutrimind_advisor_pending_action'
const MESSAGE_PAGE_CHAR_LIMIT = 180

const userMessageCount = computed(() => messages.value.filter(item => isUserMessage(item)).length)
const userAvatarText = computed(() => {
  const source = overview.value?.nickname || overview.value?.username || '我'
  return String(source).slice(0, 1).toUpperCase()
})
const assistantAvatarText = computed(() => '问')
const referenceCount = computed(() => messages.value.reduce((sum, item) => {
  if (isUserMessage(item)) {
    return sum
  }
  return sum + item.references.length
}, 0))
const messageTimeline = computed(() => {
  let previousDateKey = ''
  return messages.value.map(item => {
    const currentDateKey = formatDateKey(item.createdAt)
    const showDivider = Boolean(currentDateKey) && currentDateKey !== previousDateKey
    previousDateKey = currentDateKey
    return {
      ...item,
      showDivider,
      dividerLabel: showDivider ? formatDateLabel(item.createdAt) : ''
    }
  })
})
const historyItems = computed(() => {
  return messages.value
    .filter(item => isUserMessage(item))
    .slice()
    .reverse()
    .slice(0, 6)
    .map((item, index) => ({
      id: item.id,
      order: `0${index + 1}`.slice(-2),
      title: trimText(item.content, 20),
      desc: trimText(item.content, 42),
      timeText: formatRelativeTime(item.createdAt)
    }))
})
const goalTypeText = computed(() => formatGoalType(overview.value?.healthGoal?.goalType))
const targetCaloriesText = computed(() => {
  const target = Number(overview.value?.healthGoal?.targetCalories || 0)
  return target > 0 ? `${formatNumber(target)} 千卡` : '未设置'
})
const heroTitle = computed(() => {
  if (sending.value) {
    return '顾问正在整理你的新问题'
  }
  if ((report.value.recordedDays || 0) > 0) {
    return `最近 ${report.value.recordedDays || 0} 天已有记录，可做更贴近实际的建议`
  }
  return '先从一个问题开始，顾问会逐步理解你的饮食节奏'
})
const heroDesc = computed(() => {
  if (sending.value) {
    return '当前会优先结合你的近期记录、健康目标和检索到的营养知识片段生成回答。'
  }
  return report.value.highlightDesc
    || '这里不是纯聊天窗口，而是会结合目标、饮食记录和知识依据来回答的营养顾问工作台。'
})
const heroStatusLabel = computed(() => {
  if (contextLoading.value) {
    return '上下文同步'
  }
  if (sending.value) {
    return '回答生成'
  }
  return '当前状态'
})
const heroStatusValue = computed(() => {
  if (contextLoading.value) {
    return '正在读取今日记录和周趋势'
  }
  if (sending.value) {
    return '顾问正在组织答案'
  }
  if (referenceCount.value > 0) {
    return `已关联 ${referenceCount.value} 条知识依据`
  }
  return '可以直接开始提问'
})
const contextCards = computed(() => ([
  {
    key: 'today-records',
    label: '今日记录',
    value: `${dailyRecord.value.details.length} 条`,
    note: `${formatNumber(dailyRecord.value.totalCalories)} 千卡`,
    theme: 'warm'
  },
  {
    key: 'today-protein',
    label: '今日蛋白质',
    value: `${formatNumber(dailyRecord.value.totalProtein, 1)} 克`,
    note: '顾问会关注是否接近目标',
    theme: 'green'
  },
  {
    key: 'weekly-trend',
    label: '近 7 天记录',
    value: `${report.value.recordedDays || 0} 天`,
    note: `${formatNumber(report.value.averageCalories)} 千卡 / 天`,
    theme: 'blue'
  },
  {
    key: 'goal',
    label: '当前目标',
    value: goalTypeText.value,
    note: `参考热量 ${targetCaloriesText.value}`,
    theme: 'ink'
  }
]))
const conversationSummary = computed(() => {
  const total = messages.value.length
  if (!total) {
    return '还没有历史对话，先从快捷提问里选一个也可以。'
  }
  return `共 ${total} 条消息，已提问 ${userMessageCount.value} 次，顾问引用依据 ${referenceCount.value} 条。`
})
const historySummary = computed(() => {
  if (!historyItems.value.length) {
    return '先问一次，历史问题就会出现在这里。'
  }
  return `最近保留 ${historyItems.value.length} 条用户提问，点一下可以回到原消息或继续追问。`
})
const lastActivityText = computed(() => {
  const latest = messages.value[messages.value.length - 1]
  if (!latest?.createdAt) {
    return '刚同步'
  }
  return `最近 ${formatRelativeTime(latest.createdAt)}`
})

function createEmptyReport() {
  return {
    recordedDays: 0,
    averageCalories: 0,
    averageProtein: 0,
    completionRate: 0,
    highlightTitle: '',
    highlightDesc: ''
  }
}

function createEmptyDailyRecord() {
  return {
    totalCalories: 0,
    totalProtein: 0,
    details: []
  }
}

function isUserMessage(message) {
  return String(message?.role || '').toUpperCase() === 'USER'
}

function roleLabel(message) {
  return isUserMessage(message) ? '我' : '顾问'
}

function formatGoalType(goalType) {
  const map = {
    FAT_LOSS: '减脂',
    MUSCLE_GAIN: '增肌',
    MAINTAIN: '维持',
    BALANCE: '均衡'
  }
  return map[goalType] || '待完善'
}

function trimLabelText(value, maxLength) {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  if (text.length <= maxLength) {
    return text
  }
  return `${text.slice(0, Math.max(0, maxLength - 3))}...`
}

function splitLongText(text, maxLength) {
  const chunks = []
  let cursor = 0
  while (cursor < text.length) {
    chunks.push(text.slice(cursor, cursor + maxLength).trim())
    cursor += maxLength
  }
  return chunks.filter(Boolean)
}

function chunkMessageContent(content, maxLength = MESSAGE_PAGE_CHAR_LIMIT) {
  const text = String(content || '').trim()
  if (!text) {
    return []
  }
  if (text.length <= maxLength) {
    return [text]
  }

  const sentences = []
  let sentence = ''
  for (const character of text) {
    sentence += character
    if ('。！？；;!?'.includes(character) || character === '\n') {
      const normalizedSentence = sentence.trim()
      if (normalizedSentence) {
        sentences.push(normalizedSentence)
      }
      sentence = ''
    }
  }

  if (sentence.trim()) {
    sentences.push(sentence.trim())
  }

  if (!sentences.length) {
    return splitLongText(text, maxLength)
  }

  const pages = []
  let currentPage = ''
  for (const currentSentence of sentences) {
    const nextPage = currentPage ? `${currentPage}${currentSentence}` : currentSentence
    if (nextPage.length > maxLength && currentPage) {
      pages.push(currentPage.trim())
      currentPage = currentSentence
      continue
    }
    currentPage = nextPage
  }

  if (currentPage.trim()) {
    pages.push(currentPage.trim())
  }

  return pages.flatMap(page => (page.length > maxLength ? splitLongText(page, maxLength) : [page]))
}

function resolveSectionTitle(sectionTitle, _pageContent, _pageIndex, isContinuation) {
  if (sectionTitle) {
    const normalizedTitle = trimLabelText(sectionTitle, 14)
    return isContinuation ? `${normalizedTitle}（续）` : normalizedTitle
  }

  return ''
}

function parseMessageSection(section) {
  const lines = String(section || '')
    .split('\n')
    .map(line => line.trim())
    .filter(Boolean)

  if (!lines.length) {
    return {
      title: '',
      content: ''
    }
  }

  const firstLine = lines[0]
  const looksLikeTitle = (
    lines.length > 1
    && firstLine.length <= 18
    && (/^[一二三四五六七八九十0-9]+[、.．)]/.test(firstLine) || /[:：]$/.test(firstLine))
  )

  if (looksLikeTitle) {
    return {
      title: firstLine.replace(/[:：]$/, ''),
      content: lines.slice(1).join('\n')
    }
  }

  return {
    title: '',
    content: lines.join('\n')
  }
}

function buildMessagePages(content) {
  const normalized = String(content || '').replace(/\r\n/g, '\n').replace(/\r/g, '\n').trim()
  if (!normalized) {
    return []
  }

  const sections = normalized
    .split(/\n{2,}/)
    .map(section => section.trim())
    .filter(Boolean)

  const pages = []
  for (const section of sections.length ? sections : [normalized]) {
    const parsedSection = parseMessageSection(section)
    const contentChunks = chunkMessageContent(parsedSection.content || section)
    contentChunks.forEach((chunk, chunkIndex) => {
      pages.push({
        title: resolveSectionTitle(parsedSection.title, chunk, pages.length, chunkIndex > 0),
        content: chunk
      })
    })
  }

  const mergedPages = []
  for (const page of pages) {
    const lastPage = mergedPages[mergedPages.length - 1]
    const canMerge = (
      lastPage
      && !lastPage.title
      && !page.title
      && (lastPage.content.length + page.content.length + 2) <= MESSAGE_PAGE_CHAR_LIMIT
    )

    if (canMerge) {
      lastPage.content = `${lastPage.content}\n\n${page.content}`
      continue
    }

    mergedPages.push(page)
  }

  return mergedPages.length
    ? mergedPages
    : [{
        title: '',
        content: normalized
      }]
}

function normalizeMessageItem(message) {
  const content = String(message?.content || '')
  return {
    id: message?.id || `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    role: String(message?.role || 'ASSISTANT').toUpperCase(),
    content,
    contentPages: buildMessagePages(content),
    references: Array.isArray(message?.references) ? message.references : [],
    createdAt: message?.createdAt || new Date().toISOString()
  }
}

function normalizeDailyRecord(record) {
  return {
    ...createEmptyDailyRecord(),
    ...(record || {}),
    details: Array.isArray(record?.details) ? record.details : []
  }
}

function messageAnchorId(message) {
  const rawId = String(message?.id || '')
  return `message-${rawId.replace(/[^A-Za-z0-9_-]/g, '-')}`
}

function trimText(value, maxLength) {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  if (text.length <= maxLength) {
    return text
  }
  return `${text.slice(0, Math.max(0, maxLength - 1))}…`
}

function hasPaginatedContent(message) {
  return !isUserMessage(message) && Array.isArray(message?.contentPages) && message.contentPages.length > 1
}

function getCurrentMessagePage(message) {
  const totalPages = Array.isArray(message?.contentPages) ? message.contentPages.length : 0
  if (!totalPages) {
    return 0
  }

  const savedIndex = Number(answerPageState.value[String(message?.id)] || 0)
  if (!Number.isFinite(savedIndex) || savedIndex < 0) {
    return 0
  }
  if (savedIndex >= totalPages) {
    return totalPages - 1
  }
  return savedIndex
}

function setMessagePage(message, nextIndex) {
  if (!hasPaginatedContent(message)) {
    return
  }

  const totalPages = message.contentPages.length
  const normalizedIndex = Math.min(Math.max(0, Number(nextIndex) || 0), totalPages - 1)
  answerPageState.value[String(message.id)] = normalizedIndex
}

function handleMessagePageChange(message, event) {
  setMessagePage(message, event?.detail?.current)
}

function goToPreviousMessagePage(message) {
  if (!hasPaginatedContent(message)) {
    return
  }
  setMessagePage(message, getCurrentMessagePage(message) - 1)
}

function goToNextMessagePage(message) {
  if (!hasPaginatedContent(message)) {
    return
  }
  setMessagePage(message, getCurrentMessagePage(message) + 1)
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

function openAdvisorHistoryPage() {
  uni.navigateTo({
    url: '/pages/advisor/history'
  })
}

function consumePendingAdvisorAction() {
  const rawValue = uni.getStorageSync(ADVISOR_PENDING_ACTION_KEY)
  if (!rawValue) {
    return null
  }

  uni.removeStorageSync(ADVISOR_PENDING_ACTION_KEY)

  if (typeof rawValue === 'object') {
    return rawValue
  }

  try {
    return JSON.parse(rawValue)
  } catch (error) {
    return null
  }
}

async function applyPendingAdvisorAction() {
  const action = consumePendingAdvisorAction()
  if (!action || typeof action !== 'object') {
    return
  }

  if (action.type === 'send' && action.content) {
    await sendMessage(action.content)
    return
  }

  if (action.type === 'focus' && action.messageId) {
    if (action.draft) {
      draft.value = String(action.draft)
    }
    await nextTick()
    scrollToMessage(action.messageId)
  }
}

function formatDateKey(value) {
  if (!value) {
    return ''
  }
  const date = new Date(normalizeDateInput(value))
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatDateLabel(value) {
  const key = formatDateKey(value)
  if (!key) {
    return '更早的记录'
  }
  const todayKey = formatDateKey(new Date())
  if (key === todayKey) {
    return '今天'
  }
  return key
}

async function loadMessages() {
  try {
    const response = await request.get('/advisor/messages')
    const items = Array.isArray(response) ? response : []
    messages.value = items.map(normalizeMessageItem)
    await nextTick()
    scrollToBottom()
  } catch (error) {
    console.log('load advisor messages failed', error)
  }
}

async function loadContext() {
  try {
    today.value = formatToday()
    const [profileResponse, reportResponse, todayRecordResponse] = await Promise.all([
      request.get('/profile/overview'),
      request.get('/reports/overview', { rangeType: 'week' }),
      request.get('/meals/daily', { recordDate: today.value })
    ])

    overview.value = profileResponse || getProfile() || {}
    report.value = {
      ...createEmptyReport(),
      ...(reportResponse || {})
    }
    dailyRecord.value = normalizeDailyRecord(todayRecordResponse)
  } catch (error) {
    console.log('load advisor context failed', error)
  }
}

async function loadAdvisorWorkspace() {
  if (!ensureLoggedIn()) {
    return
  }

  overview.value = getProfile() || overview.value || {}
  contextLoading.value = true
  try {
    await loadMessages()
  } finally {
    contextLoading.value = false
  }
}

async function sendMessage(presetContent = '') {
  const content = String(presetContent || draft.value).trim()
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

  messages.value = [...messages.value, tempMessage]
  draft.value = ''
  sending.value = true
  await nextTick()
  scrollToBottom()

  try {
    const response = await request.post('/advisor/messages', {
      content
    })
    messages.value = [...messages.value, normalizeMessageItem(response)]
  } catch (error) {
    messages.value = messages.value.filter(item => item.id !== tempMessage.id)
    draft.value = content
    console.log('send advisor message failed', error)
  } finally {
    sending.value = false
    await nextTick()
    scrollToBottom()
  }
}

function sendPrompt(content) {
  sendMessage(content)
}

function reuseHistory(item) {
  if (!item) {
    return
  }
  const source = messages.value.find(message => String(message.id) === String(item.id))
  draft.value = source?.content || item.desc || ''
  scrollToMessage(item.id)
}

function openHistoryMessage(item) {
  if (!item) {
    return
  }
  reuseHistory(item)
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

function scrollToMessage(messageId) {
  const query = uni.createSelectorQuery()
  query.select(`#${messageAnchorId({ id: messageId })}`).boundingClientRect()
  query.selectViewport().scrollOffset()
  query.exec((result) => {
    const anchor = result && result[0]
    const viewport = result && result[1]
    if (!anchor || !viewport) {
      return
    }

    uni.pageScrollTo({
      scrollTop: Math.max(0, (anchor.top || 0) + (viewport.scrollTop || 0) - 220),
      duration: 220
    })
  })
}

onShow(async () => {
  await loadAdvisorWorkspace()
  await applyPendingAdvisorAction()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx 0 calc(332rpx + env(safe-area-inset-bottom));
  background: linear-gradient(180deg, #fbfefc 0%, #edf7f0 100%);
}

.content-shell {
  padding: calc(env(safe-area-inset-top) + 104rpx) 24rpx 0;
}

.sticky-topbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 70;
  padding: 0 24rpx 12rpx;
  box-sizing: border-box;
  background: rgba(248, 252, 249, 0.96);
  border-bottom: 1rpx solid rgba(31, 41, 55, 0.05);
}

.hero-head,
.section-head,
.message-head,
.reference-head,
.composer,
.context-grid,
.hero-tag-row,
.prompt-grid {
  display: flex;
}

.section-head,
.message-head,
.reference-head,
.hero-head,
.composer {
  justify-content: space-between;
}

.header-action {
  min-width: 92rpx;
  height: 64rpx;
  padding: 0 20rpx;
  border-radius: 16rpx;
  background: var(--nm-card);
  border: 1rpx solid var(--nm-line);
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-action-label {
  font-size: 22rpx;
  font-weight: 700;
  color: var(--nm-primary);
}

.section-head {
  align-items: flex-start;
  gap: 16rpx;
}

.section-head.compact {
  margin-bottom: 14rpx;
}

.section-title {
  display: block;
  font-size: 30rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.section-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: var(--nm-muted);
}

.section-link {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--nm-primary);
  white-space: nowrap;
}

.conversation-card,
.composer {
  border-radius: 22rpx;
  box-shadow: var(--nm-shadow);
}

.conversation-tip {
  margin-bottom: 18rpx;
  padding: 22rpx;
  border-radius: 18rpx;
  background: var(--nm-card-soft);
  border: 1rpx solid var(--nm-line);
}

.conversation-tip-title {
  display: block;
  font-size: 28rpx;
  font-weight: 800;
  line-height: 1.5;
  color: var(--nm-text);
}

.conversation-tip-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: var(--nm-muted);
}

.conversation-card {
  margin-top: 18rpx;
  padding: 24rpx;
  background: rgba(255, 255, 255, 0.96);
  border: 1rpx solid var(--nm-line);
}

.timeline-divider {
  display: flex;
  justify-content: center;
  margin: 10rpx 0 18rpx;
}

.timeline-divider-text {
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(31, 41, 55, 0.08);
  font-size: 22rpx;
  font-weight: 700;
  color: var(--nm-muted);
}

.message-row {
  display: flex;
  align-items: flex-end;
  gap: 16rpx;
  margin-bottom: 18rpx;
}

.message-row.user {
  justify-content: flex-end;
}

.message-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.message-avatar.assistant {
  background: var(--nm-primary-dark);
}

.message-avatar.user {
  background: var(--nm-card-soft);
  border: 1rpx solid var(--nm-line);
}

.message-avatar-text {
  font-size: 28rpx;
  font-weight: 800;
  color: #ffffff;
}

.message-avatar.user .message-avatar-text {
  color: var(--nm-primary-dark);
}

.message-card {
  width: 100%;
  max-width: calc(100% - 88rpx);
  padding: 24rpx 26rpx;
  border-radius: 18rpx;
  background: #f2f8f4;
  border: 1rpx solid var(--nm-line);
}

.message-card.user {
  background: #e8f3ec;
  border-color: #d2e3d8;
}

.message-head {
  align-items: center;
  gap: 16rpx;
}

.role-chip {
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(47, 125, 107, 0.12);
}

.role-chip.user {
  background: rgba(24, 34, 47, 0.08);
}

.role-chip-text {
  font-size: 22rpx;
  font-weight: 800;
  color: var(--nm-primary);
}

.role-chip.user .role-chip-text {
  color: var(--nm-text);
}

.message-time {
  font-size: 22rpx;
  color: var(--nm-muted);
}

.message-card.user .message-time {
  color: var(--nm-muted);
}

.message-text {
  display: block;
  margin-top: 14rpx;
  font-size: 28rpx;
  line-height: 1.8;
  color: var(--nm-text);
}

.message-pager {
  margin-top: 14rpx;
}

.message-pager-head,
.message-pager-footer,
.reference-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.message-pager-head {
  margin-bottom: 14rpx;
}

.message-pager-kicker,
.message-pager-index {
  font-size: 22rpx;
  font-weight: 800;
}

.message-pager-kicker {
  color: var(--nm-primary-dark);
}

.message-pager-index {
  color: var(--nm-muted);
}

.message-swiper {
  height: 400rpx;
}

.message-page {
  height: 100%;
  padding: 22rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.78);
  border: 1rpx solid rgba(14, 165, 109, 0.08);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}

.message-page-title {
  display: block;
  font-size: 24rpx;
  font-weight: 800;
  color: #166534;
}

.message-page-scroll {
  flex: 1;
  height: 0;
  margin-top: 10rpx;
}

.message-text.paged {
  margin-top: 0;
  white-space: pre-wrap;
}

.message-pager-footer {
  margin-top: 14rpx;
}

.message-pager-action {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--nm-primary-dark);
}

.message-pager-action.disabled {
  opacity: 0.32;
}

.message-pager-dots {
  flex: 1;
  display: flex;
  justify-content: center;
  gap: 10rpx;
}

.message-page-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 999rpx;
  background: rgba(15, 23, 42, 0.12);
}

.message-page-dot.active {
  width: 28rpx;
  background: var(--nm-primary-dark);
}

.message-card.user .message-text {
  color: var(--nm-text);
}

.reference-shell {
  margin-top: 22rpx;
  padding-top: 18rpx;
  border-top: 1rpx solid rgba(31, 41, 55, 0.08);
}

.reference-toggle {
  padding: 16rpx 18rpx;
  border-radius: 16rpx;
  background: rgba(31, 41, 55, 0.04);
}

.reference-toggle-text,
.reference-toggle-icon {
  color: var(--nm-primary-dark);
}

.reference-toggle-text {
  font-size: 24rpx;
  font-weight: 700;
}

.reference-toggle-icon {
  font-size: 30rpx;
  font-weight: 800;
}

.reference-list {
  margin-top: 14rpx;
}

.reference-title {
  display: block;
  margin-bottom: 12rpx;
  font-size: 23rpx;
  font-weight: 800;
  color: var(--nm-primary);
}

.reference-card {
  padding: 18rpx 20rpx;
  border-radius: 16rpx;
  background: #f4f9f5;
}

.reference-card + .reference-card {
  margin-top: 12rpx;
}

.reference-head {
  flex-wrap: wrap;
  align-items: center;
  gap: 10rpx;
}

.reference-tag {
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(47, 125, 107, 0.12);
  font-size: 22rpx;
  font-weight: 700;
  color: var(--nm-primary);
}

.reference-section {
  font-size: 22rpx;
  color: var(--nm-muted);
}

.reference-excerpt {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: var(--nm-muted);
}

.scroll-anchor {
  height: 4rpx;
}

.composer-shell {
  position: fixed;
  left: 0;
  right: 0;
  bottom: calc(118rpx + env(safe-area-inset-bottom));
  z-index: 50;
  padding: 0 24rpx;
}

.composer {
  gap: 16rpx;
  align-items: flex-end;
  padding: 16rpx;
  background: rgba(255, 255, 255, 0.98);
  border: 1rpx solid var(--nm-line);
}

.composer-main {
  flex: 1;
  min-width: 0;
}

.composer-input {
  width: 100%;
  height: 92rpx;
  padding: 0 22rpx;
  border-radius: 16rpx;
  background: #f1f8f3;
  font-size: 28rpx;
  color: var(--nm-text);
  box-sizing: border-box;
}

.composer-hint {
  display: block;
  margin-top: 12rpx;
  font-size: 22rpx;
  line-height: 1.6;
  color: var(--nm-muted);
}

.send-button {
  width: 164rpx;
  height: 92rpx;
  border-radius: 16rpx;
  background: var(--nm-primary);
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 800;
}

.send-button::after {
  border: none;
}

.send-button[disabled] {
  opacity: 0.78;
}

@media screen and (max-width: 720px) {
  .hero-head {
    flex-direction: column;
  }

  .hero-status {
    width: 100%;
  }

  .context-grid,
  .prompt-grid,
  .history-list {
    grid-template-columns: minmax(0, 1fr);
  }

  .message-card {
    max-width: calc(100% - 88rpx);
  }
}
</style>
