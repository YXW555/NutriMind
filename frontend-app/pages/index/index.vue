<template>
  <view class="page">
    <view class="top-bar">
      <view class="brand-wrap">
        <view class="brand-logo">
          <text class="brand-logo-text">知</text>
        </view>
        <view>
          <text class="brand-title">知食分子</text>
          <text class="brand-subtitle">今日营养总览</text>
        </view>
      </view>

      <view class="avatar" @click="goProfile">
        <text class="avatar-text">{{ avatarText }}</text>
      </view>
    </view>

    <view class="hero-card">
      <view class="hero-header">
        <view class="hero-copy">
          <text class="hero-kicker">今日摄入对照</text>
          <text class="hero-title">{{ calorieHeadline }}</text>
          <text class="hero-desc">{{ calorieSummary }}</text>
        </view>

        <view class="hero-pill">
          <text class="hero-pill-title">本周记录</text>
          <text class="hero-pill-value">{{ weeklyReport.recordedDays || 0 }} 天</text>
        </view>
      </view>

      <view class="hero-body">
        <view class="ring-column">
          <view class="ring-shell" :style="calorieRingStyle">
            <view class="ring-inner">
              <text class="ring-percent" :style="{ color: calorieAccentColor }">{{ calorieCompletionPercent }}%</text>
              <text class="ring-label">参考占比</text>
            </view>
          </view>
          <text class="ring-status" :style="{ color: calorieAccentColor }">{{ calorieStatusText }}</text>
        </view>

        <view class="hero-stats">
          <view class="hero-stat-card">
            <text class="hero-stat-label">已摄入</text>
            <text class="hero-stat-value">{{ totalCaloriesText }}</text>
            <text class="hero-stat-unit">千卡</text>
          </view>

          <view class="hero-stat-card">
            <text class="hero-stat-label">参考值</text>
            <text class="hero-stat-value">{{ referenceCaloriesText }}</text>
            <text class="hero-stat-unit">千卡</text>
          </view>

          <view class="hero-stat-card">
            <text class="hero-stat-label">{{ remainingLabel }}</text>
            <text class="hero-stat-value">{{ remainingValue }}</text>
            <text class="hero-stat-unit">千卡</text>
          </view>

          <view class="hero-stat-card">
            <text class="hero-stat-label">今日记录</text>
            <text class="hero-stat-value">{{ dailyRecord.details.length }}</text>
            <text class="hero-stat-unit">条</text>
          </view>
        </view>
      </view>
    </view>

    <view class="macro-panel">
      <view class="panel-head">
        <view>
          <text class="panel-title">三大营养素占比</text>
          <text class="panel-desc">按热量占比对照推荐范围，浅色区间代表建议摄入比例。</text>
        </view>
      </view>

      <view v-for="item in macroCards" :key="item.key" class="macro-row">
        <view class="macro-row-head">
          <view class="macro-main">
            <text class="macro-name">{{ item.label }}</text>
            <text class="macro-meta">{{ item.value }} 克 · {{ item.percent }}%</text>
          </view>

          <view class="macro-status" :class="item.statusClass">
            <text class="macro-status-text">{{ item.statusLabel }}</text>
          </view>
        </view>

        <view class="macro-track">
          <view
            class="macro-range"
            :style="{
              left: `${item.recommendedMin}%`,
              width: `${item.recommendedWidth}%`
            }"
          ></view>
          <view class="macro-fill" :class="item.key" :style="{ width: `${item.progressPercent}%` }"></view>
        </view>

        <view class="macro-foot">
          <text class="macro-foot-text">推荐 {{ item.rangeText }}</text>
          <text class="macro-foot-text">当前 {{ item.percent }}%</text>
        </view>
      </view>
    </view>

    <view class="action-grid">
      <view
        v-for="item in actionCards"
        :key="item.key"
        class="action-card"
        :class="item.theme"
        @click="item.handler()"
      >
        <view class="action-badge" :class="item.theme">
          <text class="action-badge-text">{{ item.badge }}</text>
        </view>

        <view class="action-main">
          <text class="action-title">{{ item.title }}</text>
          <text class="action-desc">{{ item.desc }}</text>
        </view>
      </view>
    </view>

    <view class="insight-card">
      <view class="insight-head">
        <view>
          <text class="insight-kicker">本周提示</text>
          <text class="insight-title">{{ weeklyHighlightTitle }}</text>
        </view>
        <text class="insight-rate">{{ completionRateText }}%</text>
      </view>

      <text class="insight-desc">{{ weeklyHighlightDesc }}</text>

      <view class="insight-grid">
        <view class="insight-pill">
          <text class="insight-pill-label">周均热量</text>
          <text class="insight-pill-value">{{ formatNumber(weeklyReport.averageCalories) }} 千卡</text>
        </view>
        <view class="insight-pill">
          <text class="insight-pill-label">周均蛋白质</text>
          <text class="insight-pill-value">{{ formatNumber(weeklyReport.averageProtein, 1) }} 克</text>
        </view>
        <view class="insight-pill">
          <text class="insight-pill-label">记录天数</text>
          <text class="insight-pill-value">{{ weeklyReport.recordedDays || 0 }} 天</text>
        </view>
      </view>
    </view>

    <view class="section-head">
      <view>
        <text class="section-title">今日记录</text>
        <text class="section-subtitle">{{ today }}</text>
      </view>
      <text class="section-link" @click="goMeals">全部</text>
    </view>

    <view v-if="!dailyRecord.details.length" class="empty-card">
      <text class="empty-title">今天还没有饮食记录</text>
      <text class="empty-desc">可以先拍照识别一餐，或者去记录页手动补记今天的第一餐。</text>
    </view>

    <view v-for="detail in displayDetails" :key="detail.id" class="record-card">
      <view class="record-cover" :class="mealTypeClass(detail.mealType)">
        <text class="record-cover-text">{{ mealTypeText(detail.mealType) }}</text>
      </view>

      <view class="record-main">
        <text class="record-name">{{ detail.foodName }}</text>
        <text class="record-meta">{{ mealTypeText(detail.mealType) }} · {{ formatTime(detail.createdAt) }}</text>
      </view>

      <view class="record-side">
        <text class="record-kcal">{{ formatNumber(detail.calories) }}</text>
        <text class="record-unit">千卡</text>
      </view>
    </view>

    <app-tab-bar current="home" />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { formatToday, getProfile, getToken, isLoggedIn, openAuthPage, saveSession } from '@/utils/auth.js'
import { formatNumber, formatTime } from '@/utils/format.js'

const DEFAULT_REFERENCE_CALORIES = 2000
const macroDefinitions = [
  {
    key: 'protein',
    label: '蛋白质',
    field: 'totalProtein',
    caloriesPerGram: 4,
    recommendedMin: 15,
    recommendedMax: 20
  },
  {
    key: 'carbohydrate',
    label: '碳水',
    field: 'totalCarbohydrate',
    caloriesPerGram: 4,
    recommendedMin: 45,
    recommendedMax: 55
  },
  {
    key: 'fat',
    label: '脂肪',
    field: 'totalFat',
    caloriesPerGram: 9,
    recommendedMin: 20,
    recommendedMax: 30
  }
]

const today = ref(formatToday())
const profile = ref(getProfile() || {})
const dailyRecord = ref(createEmptyDailyRecord())
const weeklyReport = ref({
  recordedDays: 0,
  averageCalories: 0,
  averageProtein: 0,
  averageFat: 0,
  averageCarbohydrate: 0,
  completionRate: 0,
  highlightTitle: '',
  highlightDesc: ''
})

const actionCards = [
  {
    key: 'capture',
    badge: 'AI',
    title: '智能识别记一餐',
    desc: '拍照识别后直接写入记录',
    theme: 'capture',
    handler: goCapture
  },
  {
    key: 'meals',
    badge: '记',
    title: '手动补记 / 看记录',
    desc: '进入记录页补记一餐或按日期回看',
    theme: 'meals',
    handler: goMeals
  },
  {
    key: 'report',
    badge: '报',
    title: '查看报告',
    desc: '查看周趋势和摄入总结',
    theme: 'report',
    handler: goReport
  },
  {
    key: 'plan',
    badge: '计',
    title: '安排饮食计划',
    desc: '提前排好当天餐次，保存后还能一键应用',
    theme: 'plan',
    handler: goMealPlan
  },
  {
    key: 'advisor',
    badge: '问',
    title: '问问顾问',
    desc: '结合记录获取饮食建议',
    theme: 'advisor',
    handler: goAdvisor
  }
]

const totalCaloriesNumber = computed(() => Number(dailyRecord.value.totalCalories || 0))
const totalCaloriesText = computed(() => formatNumber(totalCaloriesNumber.value))
const referenceCalories = computed(() => {
  const configured = Number(profile.value?.healthGoal?.targetCalories || 0)
  return configured > 0 ? configured : DEFAULT_REFERENCE_CALORIES
})
const referenceCaloriesText = computed(() => formatNumber(referenceCalories.value))
const remainingCalories = computed(() => Math.max(referenceCalories.value - totalCaloriesNumber.value, 0))
const overCalories = computed(() => Math.max(totalCaloriesNumber.value - referenceCalories.value, 0))
const calorieCompletionPercent = computed(() => {
  if (!referenceCalories.value) {
    return 0
  }
  return Math.max(0, Math.round((totalCaloriesNumber.value / referenceCalories.value) * 100))
})
const calorieRingPercent = computed(() => Math.min(calorieCompletionPercent.value, 100))
const calorieAccentColor = computed(() => (calorieCompletionPercent.value > 100 ? '#ff8d7e' : '#bbffd8'))
const calorieRingStyle = computed(() => ({
  background: `conic-gradient(from -90deg, ${calorieAccentColor.value} 0 ${calorieRingPercent.value}%, rgba(255, 255, 255, 0.16) ${calorieRingPercent.value}% 100%)`
}))
const calorieStatusText = computed(() => {
  if (totalCaloriesNumber.value <= 0) {
    return '待开始'
  }
  if (calorieCompletionPercent.value > 110) {
    return '偏高'
  }
  if (calorieCompletionPercent.value < 90) {
    return '偏低'
  }
  return '较接近'
})
const calorieHeadline = computed(() => {
  if (!totalCaloriesNumber.value) {
    return '今天还没开始记录'
  }
  return `已记录 ${totalCaloriesText.value} 千卡`
})
const calorieSummary = computed(() => {
  if (!totalCaloriesNumber.value) {
    return '先记录一餐，后面会结合你的参考摄入、活动量和实际吃饭情况做更自然的对照。'
  }
  if (overCalories.value > 0) {
    return `当前比参考值高 ${formatNumber(overCalories.value)} 千卡，下一餐可以适当清淡一些。`
  }
  if (remainingCalories.value <= 120) {
    return '当前和参考值已经比较接近，保持现在的节奏就可以。'
  }
  return `当前比参考值低 ${formatNumber(remainingCalories.value)} 千卡，可结合饥饿感和用餐计划灵活安排。`
})
const remainingLabel = computed(() => (overCalories.value > 0 ? '高于参考' : '低于参考'))
const remainingValue = computed(() => formatNumber(overCalories.value > 0 ? overCalories.value : remainingCalories.value))
const completionRateText = computed(() => formatNumber(weeklyReport.value.completionRate))

const macroCards = computed(() => {
  const totalMacroCalories = macroDefinitions.reduce((sum, item) => {
    return sum + Number(dailyRecord.value[item.field] || 0) * item.caloriesPerGram
  }, 0)

  return macroDefinitions.map(item => {
    const grams = Number(dailyRecord.value[item.field] || 0)
    const percent = totalMacroCalories > 0
      ? Math.round((grams * item.caloriesPerGram / totalMacroCalories) * 100)
      : 0

    return {
      ...item,
      value: formatNumber(grams, 1),
      percent,
      progressPercent: Math.min(percent, 100),
      recommendedWidth: item.recommendedMax - item.recommendedMin,
      rangeText: `${item.recommendedMin}-${item.recommendedMax}%`,
      statusLabel: resolveMacroStatusLabel(percent, item.recommendedMin, item.recommendedMax, totalMacroCalories),
      statusClass: resolveMacroStatusClass(percent, item.recommendedMin, item.recommendedMax, totalMacroCalories)
    }
  })
})

const displayDetails = computed(() => dailyRecord.value.details.slice(0, 4))
const avatarText = computed(() => {
  const source = profile.value.nickname || profile.value.username || '我'
  return String(source).slice(0, 1).toLowerCase()
})
const weeklyHighlightTitle = computed(() => weeklyReport.value.highlightTitle || '记录频率再稳定一点，趋势会更清楚')
const weeklyHighlightDesc = computed(() => {
  return weeklyReport.value.highlightDesc || '持续记录几天后，首页会更准确地提醒你热量和营养搭配的变化。'
})

function createEmptyDailyRecord() {
  return {
    totalCalories: 0,
    totalProtein: 0,
    totalFat: 0,
    totalCarbohydrate: 0,
    details: []
  }
}

function resolveMacroStatusLabel(percent, min, max, totalMacroCalories) {
  if (!totalMacroCalories) {
    return '待开始'
  }
  if (percent < min) {
    return '偏低'
  }
  if (percent > max) {
    return '偏高'
  }
  return '达标'
}

function resolveMacroStatusClass(percent, min, max, totalMacroCalories) {
  if (!totalMacroCalories) {
    return 'idle'
  }
  if (percent < min) {
    return 'low'
  }
  if (percent > max) {
    return 'high'
  }
  return 'good'
}

function mealTypeClass(type) {
  const map = {
    BREAKFAST: 'breakfast',
    LUNCH: 'lunch',
    DINNER: 'dinner',
    SNACK: 'snack'
  }
  return map[type] || 'snack'
}

function mealTypeText(type) {
  const map = {
    BREAKFAST: '早餐',
    LUNCH: '午餐',
    DINNER: '晚餐',
    SNACK: '加餐'
  }
  return map[type] || '加餐'
}

function goProfile() {
  uni.reLaunch({
    url: '/pages/profile/index'
  })
}

function goCapture() {
  uni.reLaunch({
    url: '/pages/capture/index'
  })
}

function goMeals() {
  uni.navigateTo({
    url: '/pages/meals/index'
  })
}

function goReport() {
  uni.navigateTo({
    url: '/pages/report/index'
  })
}

function goMealPlan() {
  uni.navigateTo({
    url: '/pages/meals/plan'
  })
}

function goAdvisor() {
  uni.reLaunch({
    url: '/pages/advisor/index'
  })
}

async function loadDashboard() {
  if (!isLoggedIn()) {
    openAuthPage()
    return
  }

  today.value = formatToday()

  try {
    const [currentProfile, todayRecord, report] = await Promise.all([
      request.get('/profile/overview'),
      request.get('/meals/daily', { recordDate: today.value }),
      request.get('/reports/overview', { rangeType: 'week' })
    ])

    saveSession(getToken(), {
      userId: currentProfile?.userId,
      username: currentProfile?.username,
      nickname: currentProfile?.nickname,
      email: currentProfile?.email,
      phone: currentProfile?.phone,
      role: currentProfile?.role
    })
    profile.value = currentProfile || {}
    dailyRecord.value = {
      ...createEmptyDailyRecord(),
      ...(todayRecord || {}),
      details: Array.isArray(todayRecord?.details) ? todayRecord.details : []
    }
    weeklyReport.value = {
      ...weeklyReport.value,
      ...(report || {})
    }
  } catch (error) {
    console.log('load dashboard failed', error)
  }
}

onShow(() => {
  loadDashboard()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 48rpx 28rpx 220rpx;
}

.top-bar,
.brand-wrap,
.hero-header,
.hero-body,
.section-head,
.record-card,
.macro-row-head,
.macro-foot,
.panel-head,
.insight-head {
  display: flex;
}

.top-bar,
.hero-header,
.section-head,
.record-card,
.macro-row-head,
.macro-foot,
.panel-head,
.insight-head {
  justify-content: space-between;
}

.top-bar,
.hero-body,
.macro-row-head,
.macro-foot,
.panel-head,
.insight-head {
  align-items: center;
}

.brand-wrap {
  gap: 18rpx;
  align-items: center;
}

.brand-logo {
  width: 84rpx;
  height: 84rpx;
  border-radius: 24rpx;
  background: var(--nm-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 14rpx 24rpx rgba(14, 165, 109, 0.18);
}

.brand-logo-text {
  font-size: 40rpx;
  font-weight: 800;
  color: #ffffff;
}

.brand-title {
  display: block;
  font-size: 50rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.brand-subtitle,
.panel-desc,
.macro-meta,
.macro-foot-text,
.action-desc,
.insight-desc,
.insight-pill-label,
.section-subtitle,
.record-meta,
.empty-desc,
.ring-label,
.hero-desc,
.hero-stat-label,
.hero-stat-unit,
.hero-pill-title {
  font-size: 25rpx;
  color: var(--nm-muted);
}

.brand-subtitle {
  margin-top: 6rpx;
}

.avatar {
  width: 86rpx;
  height: 86rpx;
  border-radius: 50%;
  background: var(--nm-card);
  border: 2rpx solid rgba(14, 165, 109, 0.14);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--nm-shadow);
}

.avatar-text {
  font-size: 34rpx;
  font-weight: 700;
  color: var(--nm-primary);
}

.hero-card,
.macro-panel,
.action-card,
.insight-card,
.empty-card,
.record-card {
  border-radius: 36rpx;
  box-shadow: var(--nm-shadow);
}

.hero-card {
  margin-top: 30rpx;
  padding: 30rpx;
  background: linear-gradient(140deg, #0ea56d 0%, #1bbf83 100%);
}

.hero-copy {
  flex: 1;
  min-width: 0;
}

.hero-kicker,
.hero-title,
.hero-desc,
.hero-pill-title,
.hero-pill-value,
.hero-stat-label,
.hero-stat-value,
.hero-stat-unit {
  color: rgba(255, 255, 255, 0.96);
}

.hero-kicker {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
}

.hero-title {
  display: block;
  margin-top: 10rpx;
  font-size: 48rpx;
  line-height: 1.2;
  font-weight: 800;
}

.hero-desc {
  display: block;
  margin-top: 10rpx;
  line-height: 1.65;
  color: rgba(255, 255, 255, 0.74);
}

.hero-pill {
  flex-shrink: 0;
  margin-left: 18rpx;
  padding: 18rpx 22rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.16);
  text-align: center;
}

.hero-pill-value {
  display: block;
  margin-top: 6rpx;
  font-size: 34rpx;
  font-weight: 800;
}

.hero-body {
  flex-wrap: wrap;
  gap: 24rpx;
  margin-top: 28rpx;
}

.ring-column {
  width: 230rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
}

.ring-shell {
  width: 220rpx;
  height: 220rpx;
  padding: 16rpx;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.08);
}

.ring-inner {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.94);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.ring-percent {
  font-size: 54rpx;
  font-weight: 800;
  line-height: 1;
}

.ring-label {
  margin-top: 10rpx;
  color: #627061;
}

.ring-status {
  margin-top: 16rpx;
  font-size: 28rpx;
  font-weight: 700;
}

.hero-stats {
  flex: 1;
  min-width: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14rpx;
}

.hero-stat-card {
  min-height: 120rpx;
  padding: 20rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.14);
  backdrop-filter: blur(10rpx);
}

.hero-stat-value {
  display: block;
  margin-top: 10rpx;
  font-size: 40rpx;
  font-weight: 800;
  line-height: 1.05;
}

.hero-stat-unit {
  display: block;
  margin-top: 8rpx;
  color: rgba(255, 255, 255, 0.72);
}

.macro-panel {
  margin-top: 28rpx;
  padding: 30rpx;
  background: rgba(255, 255, 255, 0.94);
}

.panel-title {
  display: block;
  font-size: 38rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.panel-desc {
  display: block;
  margin-top: 8rpx;
  line-height: 1.6;
}

.macro-row {
  margin-top: 30rpx;
}

.macro-main {
  flex: 1;
  min-width: 0;
}

.macro-name {
  display: block;
  font-size: 30rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.macro-meta {
  display: block;
  margin-top: 8rpx;
}

.macro-status {
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
}

.macro-status.idle {
  background: rgba(143, 137, 123, 0.12);
}

.macro-status.low,
.macro-status.high {
  background: rgba(244, 165, 22, 0.16);
}

.macro-status.good {
  background: rgba(14, 165, 109, 0.14);
}

.macro-status-text {
  font-size: 24rpx;
  font-weight: 700;
  color: #6f685c;
}

.macro-status.low .macro-status-text,
.macro-status.high .macro-status-text {
  color: #b87412;
}

.macro-status.good .macro-status-text {
  color: var(--nm-primary);
}

.macro-track {
  position: relative;
  overflow: hidden;
  height: 18rpx;
  margin-top: 18rpx;
  border-radius: 999rpx;
  background: #ece9e2;
}

.macro-range,
.macro-fill {
  position: absolute;
  top: 0;
  bottom: 0;
  border-radius: 999rpx;
}

.macro-range {
  top: 2rpx;
  bottom: 2rpx;
  background: rgba(14, 165, 109, 0.1);
  border: 2rpx solid rgba(14, 165, 109, 0.18);
}

.macro-fill {
  left: 0;
}

.macro-fill.protein {
  background: linear-gradient(90deg, #18bc84 0%, #1da56d 100%);
}

.macro-fill.carbohydrate {
  background: linear-gradient(90deg, #4a82f2 0%, #3f72e3 100%);
}

.macro-fill.fat {
  background: linear-gradient(90deg, #f7b216 0%, #ef9c12 100%);
}

.macro-foot {
  margin-top: 12rpx;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18rpx;
  margin-top: 28rpx;
}

.action-card {
  min-height: 170rpx;
  padding: 24rpx;
  background: var(--nm-card);
  display: flex;
  gap: 18rpx;
  align-items: flex-start;
  border: 1rpx solid rgba(22, 20, 16, 0.04);
}

.action-card.capture {
  background: linear-gradient(160deg, #ecfff5 0%, #ffffff 100%);
}

.action-card.meals {
  background: linear-gradient(160deg, #fff6e7 0%, #ffffff 100%);
}

.action-card.report {
  background: linear-gradient(160deg, #edf4ff 0%, #ffffff 100%);
}

.action-card.plan {
  background: linear-gradient(160deg, #eefbf4 0%, #ffffff 100%);
}

.action-card.advisor {
  background: linear-gradient(160deg, #f4fbf8 0%, #ffffff 100%);
}

.action-badge {
  width: 70rpx;
  height: 70rpx;
  border-radius: 22rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.action-badge.capture {
  background: rgba(14, 165, 109, 0.14);
}

.action-badge.meals {
  background: rgba(246, 163, 19, 0.14);
}

.action-badge.report {
  background: rgba(66, 132, 243, 0.14);
}

.action-badge.plan {
  background: rgba(14, 165, 109, 0.14);
}

.action-badge.advisor {
  background: rgba(27, 165, 131, 0.12);
}

.action-badge-text {
  font-size: 30rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.action-main {
  flex: 1;
  min-width: 0;
}

.action-title {
  display: block;
  font-size: 30rpx;
  font-weight: 800;
  color: var(--nm-text);
  line-height: 1.35;
}

.action-desc {
  display: block;
  margin-top: 10rpx;
  line-height: 1.65;
}

.insight-card {
  margin-top: 28rpx;
  padding: 28rpx;
  background: linear-gradient(155deg, #fff8e7 0%, #ffffff 100%);
}

.insight-kicker {
  display: block;
  font-size: 24rpx;
  font-weight: 700;
  color: #bb7f16;
}

.insight-title {
  display: block;
  margin-top: 10rpx;
  font-size: 40rpx;
  line-height: 1.3;
  font-weight: 800;
  color: var(--nm-text);
}

.insight-rate {
  padding: 12rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(14, 165, 109, 0.12);
  font-size: 28rpx;
  font-weight: 800;
  color: var(--nm-primary);
}

.insight-desc {
  display: block;
  margin-top: 18rpx;
  line-height: 1.72;
}

.insight-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14rpx;
  margin-top: 22rpx;
}

.insight-pill {
  padding: 18rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.9);
}

.insight-pill-value {
  display: block;
  margin-top: 10rpx;
  font-size: 29rpx;
  font-weight: 800;
  color: var(--nm-text);
  line-height: 1.35;
}

.section-head {
  margin-top: 40rpx;
  margin-bottom: 18rpx;
}

.section-title {
  display: block;
  font-size: 54rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.section-link {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--nm-primary);
}

.empty-card {
  padding: 28rpx;
  background: var(--nm-card);
}

.empty-title {
  display: block;
  margin-bottom: 12rpx;
  font-size: 34rpx;
  font-weight: 700;
  color: var(--nm-text);
}

.empty-desc {
  display: block;
  line-height: 1.72;
}

.record-card {
  gap: 20rpx;
  margin-top: 18rpx;
  padding: 22rpx;
  background: var(--nm-card);
}

.record-cover {
  width: 116rpx;
  height: 116rpx;
  border-radius: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.record-cover.breakfast {
  background: linear-gradient(135deg, #ffe6bb 0%, #ffd088 100%);
}

.record-cover.lunch {
  background: linear-gradient(135deg, #d7f4e5 0%, #b7e9cf 100%);
}

.record-cover.dinner {
  background: linear-gradient(135deg, #dce9ff 0%, #bfd4ff 100%);
}

.record-cover.snack {
  background: linear-gradient(135deg, #f1dfd0 0%, #ead0b5 100%);
}

.record-cover-text {
  font-size: 24rpx;
  font-weight: 700;
  color: #544a3a;
}

.record-main {
  flex: 1;
  min-width: 0;
}

.record-name {
  display: block;
  font-size: 34rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.record-meta {
  display: block;
  margin-top: 10rpx;
}

.record-side {
  text-align: right;
}

.record-kcal {
  display: block;
  font-size: 54rpx;
  font-weight: 800;
  color: var(--nm-text);
  line-height: 1;
}

.record-unit {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #b0a99b;
}
</style>
