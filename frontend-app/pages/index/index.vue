<template>
  <view class="page">
    <view class="top-bar">
      <view class="brand-wrap">
        <image 
          class="brand-logo" 
          src="/static/logo.png" 
          mode="aspectFit"
        ></image>
        <view class="greeting-wrap">
          <text class="brand-title">{{ greeting }}</text>
          <text class="brand-subtitle">{{ today }} · 今日营养总览</text>
        </view>
      </view>

      <view class="avatar" @click="goProfile">
        <text class="avatar-text">{{ avatarText }}</text>
      </view>
    </view>

    <view class="module-card hero-card">
      <view class="hero-header">
        <view class="hero-copy">
          <text class="hero-kicker">今日摄入对照</text>
          <text class="hero-title">{{ calorieHeadline }}</text>
        </view>
        <view class="hero-pill">
          <text class="hero-pill-title">本周记录</text>
          <text class="hero-pill-value">{{ weeklyReport.recordedDays || 0 }} 天</text>
        </view>
      </view>

      <text class="hero-desc">{{ calorieSummary }}</text>

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
          <view class="hero-stat-card highlight-card">
            <text class="hero-stat-label">{{ remainingLabel }}</text>
            <text class="hero-stat-value" :style="{ color: calorieAccentColor }">{{ remainingValue }}</text>
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

    <view class="module-card macro-panel">
      <view class="panel-head">
        <view>
          <text class="panel-title">三大营养素</text>
          <text class="panel-desc">浅色底纹为建议摄入比例范围</text>
        </view>
      </view>

      <view v-for="item in macroCards" :key="item.key" class="macro-row">
        <view class="macro-row-head">
          <view class="macro-main">
            <view class="macro-name-wrap">
              <view class="macro-dot" :class="item.key"></view>
              <text class="macro-name">{{ item.label }}</text>
            </view>
            <text class="macro-meta">{{ item.value }} 克 · 占 {{ item.percent }}%</text>
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

    <view class="module-card insight-card">
      <view class="insight-head">
        <view class="insight-title-wrap">
          <text class="insight-kicker">💡 智能周报</text>
          <text class="insight-title">{{ weeklyHighlightTitle }}</text>
        </view>
        <view class="insight-rate-wrap">
          <text class="insight-rate-label">完成度</text>
          <text class="insight-rate">{{ completionRateText }}%</text>
        </view>
      </view>

      <text class="insight-desc">{{ weeklyHighlightDesc }}</text>

      <view class="insight-grid">
        <view class="insight-pill">
          <text class="insight-pill-label">周均热量</text>
          <text class="insight-pill-value">{{ formatNumber(weeklyReport.averageCalories) }} <text class="insight-pill-unit">kcal</text></text>
        </view>
        <view class="insight-pill">
          <text class="insight-pill-label">周均蛋白质</text>
          <text class="insight-pill-value">{{ formatNumber(weeklyReport.averageProtein, 1) }} <text class="insight-pill-unit">g</text></text>
        </view>
        <view class="insight-pill">
          <text class="insight-pill-label">记录天数</text>
          <text class="insight-pill-value">{{ weeklyReport.recordedDays || 0 }} <text class="insight-pill-unit">天</text></text>
        </view>
      </view>
    </view>

    <view class="section-head">
      <view>
        <text class="section-title">今日记录</text>
      </view>
      <text class="section-link" @click="goMeals">查看全部</text>
    </view>

    <view v-if="!dailyRecord.details.length" class="module-card empty-card">
      <view class="empty-icon">🍽️</view>
      <text class="empty-title">今天还没有饮食记录</text>
      <text class="empty-desc">可以先拍照识别一餐，或者手动记录今天的第一餐。</text>
      <button class="btn-primary empty-btn" @click="goCapture">一键记录</button>
    </view>

    <view class="timeline-wrap" v-else>
      <view v-for="(detail, index) in displayDetails" :key="detail.id" class="timeline-item">
        <view class="timeline-line" v-if="index !== displayDetails.length - 1"></view>
        <view class="timeline-dot" :class="mealTypeClass(detail.mealType)"></view>
        <view class="module-card record-card">
          <view class="record-cover" :class="mealTypeClass(detail.mealType)">
            <text class="record-cover-text">{{ mealTypeText(detail.mealType).slice(0,1) }}</text>
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
      </view>
    </view>

    <view class="guide-modal-overlay" :class="{ 'is-visible': showGuideModal }">
      <view class="guide-modal-card">
        <view class="guide-header">
          <text class="guide-title">营养基础小科普 🥗</text>
          <text class="guide-subtitle">只需1分钟，让你对健康饮食更有底气。</text>
        </view>
        
        <scroll-view scroll-y class="guide-body">
          <view class="guide-section">
            <text class="guide-section-title">🔥 什么是热量？</text>
            <text class="guide-section-desc">想要改变体重，最根本的法则是：</text>
            <view class="guide-list">
              <text class="guide-list-item">• 摄入 < 消耗 = 变瘦（创造热量缺口）</text>
              <text class="guide-list-item">• 摄入 > 消耗 = 变重（热量盈余）</text>
            </view>
          </view>

          <view class="guide-section">
            <text class="guide-section-title">🥩 什么是三大营养素？</text>
            <text class="guide-section-desc">它们构成了食物的热量来源：</text>
            <view class="guide-list">
              <text class="guide-list-item"><text class="highlight green">碳水：</text>身体首选能量。（如米面、薯类）</text>
              <text class="guide-list-item"><text class="highlight red">蛋白质：</text>肌肉建筑材料，饱腹感强。（如肉蛋奶）</text>
              <text class="guide-list-item"><text class="highlight yellow">脂肪：</text>维持激素健康。（如坚果、好油）</text>
            </view>
          </view>
        </scroll-view>

        <view class="guide-footer">
          <button class="btn-primary" @click="closeGuideAndSetGoal">我了解了，去设置目标</button>
          <button class="btn-text" @click="closeGuideOnly">稍后再说</button>
        </view>
      </view>
    </view>

    <view class="fab-mask" :class="{ 'is-open': isFabOpen }" @click="toggleFab"></view>
    
    <view class="fab-container">
      <view
        v-for="(item, index) in actionCards"
        :key="item.key"
        class="fab-sub-item"
        :class="{ 'is-open': isFabOpen }"
        :style="{
          transform: isFabOpen ? `translate(${item.tx}rpx, ${item.ty}rpx) scale(1)` : 'translate(0, 0) scale(0.5)',
          opacity: isFabOpen ? 1 : 0,
          transitionDelay: isFabOpen ? `${index * 0.04}s` : '0s'
        }"
        @click="handleFabItemClick(item)"
      >
        <view class="fab-sub-btn">
          <text class="fab-sub-badge">{{ item.badge }}</text>
        </view>
        <text class="fab-sub-title">{{ item.title }}</text>
      </view>

      <view class="fab-main-btn" :class="{ 'is-open': isFabOpen }" @click="toggleFab">
        <image class="fab-main-logo" src="/static/logo.png" mode="aspectFit"></image>
        <text class="fab-main-icon">+</text>
      </view>
    </view>
    <app-tab-bar current="home" />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { formatToday, getProfile, getToken, isLoggedIn, openAuthPage, saveSession, getFirstLoginFlag, clearFirstLoginFlag } from '@/utils/auth.js'
import { formatNumber, formatTime } from '@/utils/format.js'

const DEFAULT_REFERENCE_CALORIES = 2000
const macroDefinitions = [
  {
    key: 'carbohydrate',
    label: '碳水化合物',
    field: 'totalCarbohydrate',
    caloriesPerGram: 4,
    recommendedMin: 45,
    recommendedMax: 55
  },
  {
    key: 'protein',
    label: '蛋白质',
    field: 'totalProtein',
    caloriesPerGram: 4,
    recommendedMin: 15,
    recommendedMax: 20
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

// 控制弹窗的状态变量
const showGuideModal = ref(false)

// 悬浮菜单开启状态
const isFabOpen = ref(false)

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

// 悬浮菜单的配置：换成了极简的文字，并且大幅增加了展开半径 (R=340) 以防止文字重叠
const actionCards = [
  { key: 'capture', badge: '拍', title: '智能记录', tx: -340, ty: 0, handler: goCapture },
  { key: 'meals', badge: '记', title: '手动补记', tx: -314, ty: -130, handler: goMeals },
  { key: 'advisor', badge: '问', title: '问问顾问', tx: -240, ty: -240, handler: goAdvisor },
  { key: 'report', badge: '报', title: '查看报告', tx: -130, ty: -314, handler: goReport },
  { key: 'plan', badge: '划', title: '饮食计划', tx: 0, ty: -340, handler: goMealPlan }
]

function toggleFab() {
  isFabOpen.value = !isFabOpen.value
}

function handleFabItemClick(item) {
  isFabOpen.value = false // 点击后自动收起
  item.handler()
}

// 动态问候语
const greeting = computed(() => {
  const hour = new Date().getHours();
  const name = profile.value.nickname || profile.value.username || '';
  let timeStr = '你好';
  if (hour >= 5 && hour < 12) timeStr = '早上好';
  else if (hour >= 12 && hour < 18) timeStr = '下午好';
  else if (hour >= 18 || hour < 5) timeStr = '晚上好';
  return `${timeStr}${name ? '，' + name : ''}`;
})

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
  if (!referenceCalories.value) return 0
  return Math.max(0, Math.round((totalCaloriesNumber.value / referenceCalories.value) * 100))
})
const calorieRingPercent = computed(() => Math.min(calorieCompletionPercent.value, 100))

// 【色彩优化】使用更鲜亮的薄荷绿和醒目的红色
const calorieAccentColor = computed(() => (calorieCompletionPercent.value > 100 ? '#FF4D4F' : '#20C997'))
const calorieRingStyle = computed(() => ({
  background: `conic-gradient(from -90deg, ${calorieAccentColor.value} 0 ${calorieRingPercent.value}%, #F3F4F6 ${calorieRingPercent.value}% 100%)`
}))

const calorieStatusText = computed(() => {
  if (totalCaloriesNumber.value <= 0) return '待开始'
  if (calorieCompletionPercent.value > 110) return '偏高'
  if (calorieCompletionPercent.value < 90) return '偏低'
  return '较接近'
})
const calorieHeadline = computed(() => {
  if (!totalCaloriesNumber.value) return '今天还没开始记录'
  return `已摄入 ${totalCaloriesText.value} kcal`
})
const calorieSummary = computed(() => {
  if (!totalCaloriesNumber.value) {
    return '先记录一餐，AI 会自动为你计算剩余热量额度。'
  }
  if (overCalories.value > 0) {
    return `当前比参考高 ${formatNumber(overCalories.value)} 千卡，下一餐可适当清淡些。`
  }
  if (remainingCalories.value <= 120) {
    return '当前和参考值比较接近，保持现在的节奏就可以。'
  }
  return `还可摄入 ${formatNumber(remainingCalories.value)} 千卡，可灵活安排下一餐。`
})
const remainingLabel = computed(() => (overCalories.value > 0 ? '超标热量' : '剩余额度'))
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
  return String(source).slice(0, 1).toUpperCase()
})
const weeklyHighlightTitle = computed(() => weeklyReport.value.highlightTitle || '记录频率待提升')
const weeklyHighlightDesc = computed(() => {
  return weeklyReport.value.highlightDesc || '持续记录几天后，AI 会在这里为你提供准确的营养搭配变化分析。'
})

function createEmptyDailyRecord() {
  return { totalCalories: 0, totalProtein: 0, totalFat: 0, totalCarbohydrate: 0, details: [] }
}

function resolveMacroStatusLabel(percent, min, max, totalMacroCalories) {
  if (!totalMacroCalories) return '待开始'
  if (percent < min) return '偏低'
  if (percent > max) return '偏高'
  return '达标'
}

function resolveMacroStatusClass(percent, min, max, totalMacroCalories) {
  if (!totalMacroCalories) return 'idle'
  if (percent < min) return 'low'
  if (percent > max) return 'high'
  return 'good'
}

function mealTypeClass(type) {
  const map = { BREAKFAST: 'breakfast', LUNCH: 'lunch', DINNER: 'dinner', SNACK: 'snack' }
  return map[type] || 'snack'
}

function mealTypeText(type) {
  const map = { BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐', SNACK: '加餐' }
  return map[type] || '加餐'
}

function goProfile() { uni.reLaunch({ url: '/pages/profile/index' }) }
function goCapture() { uni.reLaunch({ url: '/pages/capture/index' }) }
function goMeals() { uni.navigateTo({ url: '/pages/meals/index' }) }
function goReport() { uni.navigateTo({ url: '/pages/report/index' }) }
function goMealPlan() { uni.navigateTo({ url: '/pages/meals/plan' }) }
function goAdvisor() { uni.reLaunch({ url: '/pages/advisor/index' }) }

function closeGuideAndSetGoal() {
  showGuideModal.value = false
  clearFirstLoginFlag()
  goProfile()
}

function closeGuideOnly() {
  showGuideModal.value = false
  clearFirstLoginFlag()
}

async function loadDashboard() {
  if (!isLoggedIn()) {
    openAuthPage()
    return
  }

  if (getFirstLoginFlag()) {
    showGuideModal.value = true
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
    weeklyReport.value = { ...weeklyReport.value, ...(report || {}) }
  } catch (error) {
    console.log('load dashboard failed', error)
  }
}

onShow(() => {
  loadDashboard()
})
</script>

<style scoped>
/* ====================================================
   全局基础样式
==================================================== */
.page {
  min-height: 100vh;
  background-color: #F4F6F8; 
  padding: 40rpx 24rpx 176rpx;
  box-sizing: border-box;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  position: relative;
}

.module-card {
  background: #ffffff;
  border-radius: 40rpx;
  padding: 36rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.03);
}

.top-bar, .brand-wrap, .hero-header, .section-head, .record-card, 
.macro-row-head, .macro-foot, .panel-head, .insight-head {
  display: flex;
}

.top-bar, .hero-header, .section-head, .record-card, 
.macro-row-head, .macro-foot, .panel-head, .insight-head {
  justify-content: space-between;
}

.top-bar, .macro-row-head, .macro-foot, .panel-head {
  align-items: center;
}

.top-bar { margin-bottom: 36rpx; padding: 0 8rpx; }
.brand-wrap { gap: 20rpx; align-items: center; }
.brand-logo { width: 84rpx; height: 84rpx; border-radius: 24rpx; }
.greeting-wrap { display: flex; flex-direction: column; }
.brand-title { font-size: 44rpx; font-weight: 900; color: #111827; line-height: 1.2; }
.brand-subtitle { font-size: 26rpx; color: #6B7280; margin-top: 8rpx; }
.avatar {
  width: 84rpx; height: 84rpx; border-radius: 50%;
  background: #ffffff; border: 2rpx solid #E5E7EB;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.04);
}
.avatar-text { font-size: 34rpx; font-weight: 800; color: #111827; }

.hero-card { padding: 40rpx 36rpx; }
.hero-header { align-items: flex-start; margin-bottom: 16rpx; }
.hero-copy { flex: 1; }
.hero-kicker { font-size: 26rpx; font-weight: 800; color: #20C997; }
.hero-title { display: block; margin-top: 10rpx; font-size: 48rpx; font-weight: 900; color: #111827; }
.hero-desc { display: block; font-size: 28rpx; line-height: 1.6; color: #4B5563; margin-bottom: 40rpx; }

.hero-pill {
  padding: 14rpx 28rpx; border-radius: 100rpx;
  background: #F3F4F6; border: 1rpx solid #E5E7EB;
  text-align: center; display: flex; align-items: center; gap: 12rpx;
}
.hero-pill-title { font-size: 24rpx; color: #6B7280; }
.hero-pill-value { font-size: 30rpx; font-weight: 900; color: #111827; }

.hero-body { display: flex; align-items: center; gap: 36rpx; }
.ring-column { width: 220rpx; display: flex; flex-direction: column; align-items: center; flex-shrink: 0; }
.ring-shell { width: 220rpx; height: 220rpx; padding: 18rpx; border-radius: 50%; background: #F9FAFB; }
.ring-inner {
  width: 100%; height: 100%; border-radius: 50%; background: #ffffff;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  box-shadow: inset 0 2rpx 8rpx rgba(0,0,0,0.02);
}
.ring-percent { font-size: 56rpx; font-weight: 900; line-height: 1; }
.ring-label { margin-top: 10rpx; font-size: 24rpx; color: #6B7280; }
.ring-status { margin-top: 20rpx; font-size: 30rpx; font-weight: 800; }

.hero-stats { flex: 1; display: grid; grid-template-columns: repeat(2, 1fr); gap: 20rpx; }
.hero-stat-card {
  padding: 24rpx; border-radius: 28rpx; background: #F9FAFB; border: 1rpx solid #F3F4F6;
  display: flex; flex-direction: column; justify-content: center;
}
.highlight-card { background: #ffffff; box-shadow: 0 4rpx 20rpx rgba(0,0,0,0.05); border: 1rpx solid #E5E7EB; }
.hero-stat-label { font-size: 24rpx; color: #6B7280; }
.hero-stat-value { margin-top: 10rpx; font-size: 40rpx; font-weight: 900; color: #111827; }
.hero-stat-unit { margin-top: 6rpx; font-size: 22rpx; color: #9CA3AF; }

.panel-title { font-size: 40rpx; font-weight: 900; color: #111827; }
.panel-desc { margin-top: 10rpx; font-size: 26rpx; color: #6B7280; }
.macro-row { margin-top: 40rpx; }
.macro-name-wrap { display: flex; align-items: center; gap: 12rpx; }
.macro-dot { width: 16rpx; height: 16rpx; border-radius: 50%; }
.macro-dot.carbohydrate { background: #20C997; }
.macro-dot.protein { background: #FF4D4F; }
.macro-dot.fat { background: #FFC53D; }
.macro-name { font-size: 34rpx; font-weight: 900; color: #111827; }
.macro-meta { margin-top: 8rpx; font-size: 26rpx; color: #4B5563; }
.macro-status { padding: 10rpx 24rpx; border-radius: 100rpx; }
.macro-status.idle { background: #F3F4F6; }
.macro-status.low, .macro-status.high { background: #FEF2F2; }
.macro-status.good { background: #E6FFFA; }
.macro-status-text { font-size: 24rpx; font-weight: 800; color: #6B7280; }
.macro-status.low .macro-status-text, .macro-status.high .macro-status-text { color: #DC2626; }
.macro-status.good .macro-status-text { color: #059669; }

.macro-track {
  position: relative; height: 24rpx; margin-top: 20rpx; border-radius: 100rpx; background: #F3F4F6; overflow: hidden;
}
.macro-range, .macro-fill { position: absolute; top: 0; bottom: 0; border-radius: 100rpx; }
.macro-range { background: rgba(0,0,0,0.03); border-left: 2rpx solid rgba(0,0,0,0.06); border-right: 2rpx solid rgba(0,0,0,0.06); box-sizing: border-box; }
.macro-fill { left: 0; transition: width 0.6s ease; }
.macro-fill.carbohydrate { background: linear-gradient(90deg, #38D07D, #20C997); }
.macro-fill.protein { background: linear-gradient(90deg, #FF7875, #FF4D4F); }
.macro-fill.fat { background: linear-gradient(90deg, #FFD666, #FFC53D); }
.macro-foot { margin-top: 16rpx; }
.macro-foot-text { font-size: 24rpx; color: #6B7280; }

.insight-card { border: 1rpx solid #F3F4F6; }
.insight-head { align-items: flex-start; }
.insight-title-wrap { flex: 1; }
.insight-kicker { font-size: 26rpx; font-weight: 800; color: #F59E0B; }
.insight-title { display: block; margin-top: 10rpx; font-size: 40rpx; font-weight: 900; color: #111827; }
.insight-rate-wrap { display: flex; flex-direction: column; align-items: flex-end; background: #F3F4F6; padding: 16rpx 24rpx; border-radius: 24rpx; }
.insight-rate-label { font-size: 22rpx; color: #4B5563; font-weight: 800; }
.insight-rate { font-size: 36rpx; font-weight: 900; color: #111827; }
.insight-desc { display: block; margin-top: 24rpx; line-height: 1.6; font-size: 30rpx; color: #4B5563; }
.insight-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 20rpx; margin-top: 28rpx; }
.insight-pill { padding: 24rpx; border-radius: 28rpx; background: #F9FAFB; text-align: center; }
.insight-pill-label { font-size: 24rpx; color: #6B7280; }
.insight-pill-value { display: block; margin-top: 12rpx; font-size: 36rpx; font-weight: 900; color: #111827; }
.insight-pill-unit { font-size: 22rpx; color: #9CA3AF; font-weight: normal; }

.section-head { margin: 48rpx 0 24rpx; padding: 0 8rpx; align-items: center;}
.section-title { font-size: 42rpx; font-weight: 900; color: #111827; }
.section-link { font-size: 30rpx; font-weight: 800; color: #6B7280; }

.btn-primary {
  width: 100%; height: 100rpx; border-radius: 100rpx;
  background: #20C997; color: #ffffff; font-size: 34rpx; font-weight: 900;
  display: flex; align-items: center; justify-content: center; 
  box-shadow: 0 8rpx 24rpx rgba(32, 201, 151, 0.3);
}
.btn-primary::after { border: none; }

.empty-card { text-align: center; padding: 80rpx 40rpx; }
.empty-icon { font-size: 90rpx; margin-bottom: 24rpx; }
.empty-title { display: block; font-size: 36rpx; font-weight: 900; color: #111827; margin-bottom: 16rpx;}
.empty-desc { font-size: 28rpx; color: #6B7280; }
.empty-btn { margin-top: 40rpx; width: 320rpx; }

.timeline-wrap { padding-left: 20rpx; }
.timeline-item { position: relative; padding-left: 48rpx; margin-bottom: 28rpx; }
.timeline-line { position: absolute; left: 10rpx; top: 48rpx; bottom: -48rpx; width: 4rpx; background: #E5E7EB; border-radius: 2rpx;}
.timeline-dot {
  position: absolute; left: 0; top: 38rpx; width: 24rpx; height: 24rpx;
  border-radius: 50%; border: 6rpx solid #ffffff; box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.1);
}
.timeline-dot.breakfast { background: #FCD34D; }
.timeline-dot.lunch { background: #34D399; }
.timeline-dot.dinner { background: #60A5FA; }
.timeline-dot.snack { background: #A78BFA; }

.record-card { margin-bottom: 0; padding: 28rpx; display: flex; align-items: center; gap: 28rpx; }
.record-cover {
  width: 100rpx; height: 100rpx; border-radius: 28rpx; background: #F3F4F6;
  display: flex; align-items: center; justify-content: center;
}
.record-cover-text { font-size: 32rpx; font-weight: 900; color: #6B7280; }
.record-main { flex: 1; }
.record-name { display: block; font-size: 34rpx; font-weight: 900; color: #111827; margin-bottom: 8rpx;}
.record-meta { font-size: 26rpx; color: #6B7280; }
.record-side { text-align: right; }
.record-kcal { display: block; font-size: 48rpx; font-weight: 900; color: #111827; }
.record-unit { font-size: 24rpx; color: #9CA3AF; margin-top: 4rpx;}

/* 科普弹窗样式 */
.guide-modal-overlay {
  position: fixed; inset: 0; background: rgba(0, 0, 0, 0.5); backdrop-filter: blur(8px);
  z-index: 9999; display: flex; align-items: center; justify-content: center;
  opacity: 0; pointer-events: none; transition: opacity 0.3s ease;
}
.guide-modal-overlay.is-visible { opacity: 1; pointer-events: auto; }
.guide-modal-card {
  width: 680rpx; max-height: 85vh; background: #ffffff; border-radius: 48rpx;
  display: flex; flex-direction: column; overflow: hidden; box-shadow: 0 24rpx 80rpx rgba(0, 0, 0, 0.2);
  transform: scale(0.95) translateY(20rpx); transition: all 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}
.guide-modal-overlay.is-visible .guide-modal-card { transform: scale(1) translateY(0); }
.guide-header { padding: 60rpx 48rpx 30rpx; text-align: center; background: #F9FAFB; border-bottom: 1rpx solid #F3F4F6; }
.guide-title { display: block; font-size: 46rpx; font-weight: 900; color: #111827; margin-bottom: 16rpx; }
.guide-subtitle { display: block; font-size: 28rpx; color: #6B7280; line-height: 1.6; }
.guide-body { flex: 1; padding: 0 48rpx; min-height: 400rpx; }
.guide-section { margin-top: 40rpx; padding-bottom: 40rpx; border-bottom: 1rpx dashed #E5E7EB; }
.guide-section:last-child { border-bottom: none; }
.guide-section-title { display: block; font-size: 34rpx; font-weight: 900; color: #111827; margin-bottom: 16rpx; }
.guide-section-desc { display: block; font-size: 30rpx; color: #4B5563; line-height: 1.6; }
.guide-list { margin-top: 20rpx; background: #F9FAFB; border-radius: 24rpx; padding: 28rpx; }
.guide-list-item { display: block; font-size: 28rpx; color: #4B5563; line-height: 1.6; margin-bottom: 16rpx; }
.guide-list-item:last-child { margin-bottom: 0; }
.highlight { font-weight: 900; }
.highlight.green { color: #20C997; }
.highlight.red { color: #FF4D4F; }
.highlight.yellow { color: #F59E0B; }
.guide-footer { padding: 40rpx 48rpx 50rpx; display: flex; flex-direction: column; gap: 24rpx; }
.btn-text { width: 100%; height: 80rpx; background: transparent; color: #6B7280; font-size: 32rpx; font-weight: 800; display: flex; align-items: center; justify-content: center; }
.btn-text::after { border: none; }


/* ====================================================
   全新展开式悬浮按钮 (Radial Floating Action Button)
==================================================== */
.fab-mask {
  position: fixed;
  inset: 0;
  background: rgba(255, 255, 255, 0.85); /* 浅色半透明遮罩 */
  backdrop-filter: blur(8px);
  z-index: 990;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.3s ease;
}
.fab-mask.is-open {
  opacity: 1;
  pointer-events: auto;
}

.fab-container {
  position: fixed;
  right: 40rpx;
  bottom: 180rpx; /* 在TabBar之上 */
  width: 120rpx;
  height: 120rpx;
  z-index: 995;
  display: flex;
  align-items: center;
  justify-content: center;
}

.fab-main-btn {
  position: absolute;
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%; /* 强制圆形 */
  overflow: hidden;    /* 裁切超出圆形的图片内容 */
  background: #ffffff; /* 纯白底色衬托Logo */
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12rpx 32rpx rgba(32, 201, 151, 0.3);
  z-index: 1000;
  transition: background-color 0.3s, box-shadow 0.3s;
}

/* 展开后主按钮变成红色 */
.fab-main-btn.is-open {
  background: #FF4D4F;
  box-shadow: 0 12rpx 32rpx rgba(255, 77, 79, 0.4);
}

/* 默认状态：显示 Logo，尺寸充满容器 */
.fab-main-logo {
  width: 120rpx; /* 与容器同大 */
  height: 120rpx;
  position: absolute;
  transition: all 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  opacity: 1;
  transform: scale(1) rotate(0deg);
}

/* 展开状态：Logo 缩小淡出并向左旋转 */
.fab-main-btn.is-open .fab-main-logo {
  opacity: 0;
  transform: scale(0.5) rotate(-90deg);
}

/* 默认状态：隐藏 X 号 */
.fab-main-icon {
  font-size: 72rpx;
  color: #ffffff;
  font-weight: 300;
  line-height: 1;
  margin-top: -8rpx;
  position: absolute;
  transition: all 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  opacity: 0;
  transform: scale(0.5) rotate(0deg);
}

/* 展开状态：X 号放大淡入，并顺时针旋转到 135 度 */
.fab-main-btn.is-open .fab-main-icon {
  opacity: 1;
  transform: scale(1) rotate(135deg);
}

.fab-sub-item {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 995;
  transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

/* 缩小按钮尺寸，使得间距更开阔 */
.fab-sub-btn {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.06);
  margin-bottom: 12rpx;
}

/* 极简排版文字，配合主题色 */
.fab-sub-badge {
  font-size: 32rpx;
  font-weight: 900;
  color: #20C997;
}

.fab-sub-title {
  font-size: 24rpx;
  font-weight: 900;
  color: #111827;
  text-shadow: 0 2rpx 4rpx rgba(255, 255, 255, 0.9);
  white-space: nowrap;
  position: absolute;
  bottom: -46rpx; /* 下沉更多，拉开与按钮的距离 */
}
</style>