<template>
  <view class="page-modern">
    <app-page-header
      title="饮食记录"
      subtitle="按日期查看、按餐次记录"
      fallback-url="/pages/index/index"
      class="custom-header"
      :border="false"
    />

    <view class="xhs-card hero-card">
      <view class="hero-head">
        <view class="date-section">
          <text class="hero-badge">当前记录日</text>
          <picker mode="date" :value="recordDate" @change="handleDateChange">
            <view class="date-selector">
              <text class="hero-title">{{ recordDate }}</text>
              <text class="icon-arrow">▾</text>
            </view>
          </picker>
        </view>
      </view>

      <view class="main-stat">
        <text class="stat-value">{{ formatNumber(dailyRecord.totalCalories) }}</text>
        <text class="stat-unit">千卡</text>
      </view>

      <view class="hero-grid">
        <view class="stat-mini-card">
          <text class="mini-label">蛋白质(克)</text>
          <text class="mini-value">{{ formatNumber(dailyRecord.totalProtein) }}</text>
        </view>
        <view class="stat-mini-card">
          <text class="mini-label">已保存(条)</text>
          <text class="mini-value">{{ dailyRecord.details.length }}</text>
        </view>
        <view class="stat-mini-card highlight-card" v-if="pendingItems.length">
          <text class="mini-label">待保存</text>
          <text class="mini-value highlight-text">{{ pendingItems.length }}</text>
        </view>
      </view>
    </view>

    <view class="xhs-card reminder-strip" v-if="reminderDigest">
      <view class="reminder-strip-main">
        <text class="reminder-strip-title">{{ reminderDigest.title }}</text>
        <text class="reminder-strip-desc">{{ reminderDigest.description }}</text>
      </view>
      <button class="reminder-strip-btn" @click="goAdvisor">问问顾问</button>
    </view>

    <view class="xhs-card panel-card">
      <view class="panel-header">
        <text class="panel-title">添加记录</text>
        <text class="panel-link" @click="goFoods">管理食物库 〉</text>
      </view>

      <scroll-view scroll-x class="meal-scroll" :show-scrollbar="false">
        <view class="meal-type-row">
          <view
            v-for="item in mealTypeOptions"
            :key="item.value"
            class="meal-pill"
            :class="{ active: mealType === item.value }"
            @click="mealType = item.value"
          >
            {{ item.label }}
          </view>
        </view>
      </scroll-view>

      <view class="search-box">
        <input
          v-model="foodKeyword"
          class="xhs-input"
          placeholder="搜索食物名称..."
          placeholder-class="input-placeholder"
          confirm-type="search"
          @confirm="loadFoods"
        />
        <button class="search-btn" @click="loadFoods">搜索</button>
      </view>

      <picker :range="foods" range-key="name" :value="selectedFoodIndex" @change="handleFoodSelect">
        <view class="picker-box">
          <text class="picker-text" :class="{'placeholder-text': !foods.length}">
            {{ selectedFoodLabel }}
          </text>
          <text class="icon-arrow">▾</text>
        </view>
      </picker>

      <view v-if="!foods.length" class="empty-tip-card">
        <text class="empty-emoji">🥣</text>
        <view class="empty-text-wrap">
          <text class="empty-tip-title">暂无可选项</text>
          <text class="empty-tip-desc">请先搜索，或前往食物库新增</text>
        </view>
      </view>

      <view v-else-if="selectedFood" class="food-info-card">
        <view class="food-info-head">
          <view class="food-name-wrap">
            <text class="food-name">{{ selectedFood.name }}</text>
            <text class="food-tag">{{ selectedFood.category || '未分类' }}</text>
          </view>
          <text class="food-kcal-highlight">{{ formatNumber(selectedFood.calories) }} kcal/{{ selectedFood.unit || '100克' }}</text>
        </view>

        <view class="nutrition-strip">
          <view class="nut-item"><text>蛋</text> {{ formatNumber(selectedFood.protein, 1) }}g</view>
          <view class="nut-item"><text>脂</text> {{ formatNumber(selectedFood.fat, 1) }}g</view>
          <view class="nut-item"><text>碳</text> {{ formatNumber(selectedFood.carbohydrate, 1) }}g</view>
          <view class="nut-item"><text>纤</text> {{ formatNumber(selectedFood.fiber, 1) }}g</view>
        </view>
      </view>

      <view class="quantity-box" v-if="selectedFood">
        <input v-model="quantity" class="xhs-input" type="digit" placeholder="食用量 (克)" placeholder-class="input-placeholder"/>
        <view class="quick-chips">
          <view
            v-for="amount in quickQuantities"
            :key="amount"
            class="chip"
            @click="applyQuickQuantity(amount)"
          >
            {{ amount }}g
          </view>
        </view>
      </view>

      <view class="action-row" v-if="selectedFood">
        <button class="btn-secondary" @click="addPendingItem">加入待保存</button>
        <button class="btn-primary" @click="submitCurrentFood">立即记录</button>
      </view>
    </view>

    <view class="xhs-card pending-card" v-if="pendingItems.length">
      <view class="panel-header">
        <text class="panel-title">待保存清单 ({{ pendingItems.length }})</text>
        <text class="panel-link text-muted" @click="clearPendingItems">清空</text>
      </view>

      <view class="draft-list">
        <view v-for="(item, index) in pendingItems" :key="`${item.foodId}-${index}`" class="draft-item">
          <view class="draft-info">
            <view class="draft-title-row">
              <text class="draft-name">{{ item.foodName }}</text>
              <text class="draft-tag">{{ mealTypeLabel(item.mealType) }}</text>
            </view>
            <text class="draft-desc">{{ formatNumber(item.quantity) }}克 · {{ formatNumber(item.calories) }}千卡</text>
          </view>
          <view class="draft-remove-btn" @click="removePendingItem(index)">✕</view>
        </view>
      </view>

      <view class="submit-footer">
        <view class="submit-summary">
          <text>预计增加 {{ pendingSummary.calories }} kcal</text>
        </view>
        <button class="btn-primary-small" @click="submitPendingMeals">全部保存</button>
      </view>
    </view>

    <view class="history-section">
      <text class="section-main-title">今日记录</text>
      
      <view v-for="group in detailGroups" :key="group.value" class="xhs-card history-group-card">
        <view class="history-head">
          <view class="history-badge" :class="group.theme">{{ group.label }}</view>
          <text class="history-total">{{ formatNumber(group.calories) }} kcal</text>
        </view>

        <view class="history-list" v-if="group.items.length">
          <view v-for="detail in group.items" :key="detail.id" class="history-item">
            <view class="history-item-main">
              <text class="history-item-name">{{ detail.foodName }}</text>
              <text class="history-item-meta">{{ formatNumber(detail.quantity) }}g · {{ formatTime(detail.createdAt) }}</text>
            </view>
            <view class="history-item-right">
              <text class="history-item-kcal">{{ formatNumber(detail.calories) }}</text>
              <view class="delete-icon" @click="deleteDetail(detail.id)">移除</view>
            </view>
          </view>
        </view>

        <view v-else class="history-empty">
          <text>还没有记录该餐次哦</text>
        </view>
      </view>
    </view>

    <view class="fab-bar">
      <button class="fab-btn outline" @click="goHome">返回首页</button>
      <button class="fab-btn solid" @click="goCapture">📸 智能拍照</button>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { ensureLoggedIn, formatToday, getProfile } from '@/utils/auth.js'
import { formatNumber, formatTime, mealTypeLabel } from '@/utils/format.js'
import { buildReminderDigest } from '@/utils/reminders.js'

const quickQuantities = [50, 100, 150, 200, 300]
const mealTypeOptions = [
  { label: '早餐', value: 'BREAKFAST', theme: 'theme-morning' },
  { label: '午餐', value: 'LUNCH', theme: 'theme-noon' },
  { label: '晚餐', value: 'DINNER', theme: 'theme-night' },
  { label: '加餐', value: 'SNACK', theme: 'theme-snack' }
]

const recordDate = ref(formatToday())
const mealType = ref('BREAKFAST')
const foodKeyword = ref('')
const foods = ref([])
const selectedFoodIndex = ref(0)
const quantity = ref('100')
const pendingItems = ref([])
const dailyRecord = ref(createEmptyDailyRecord(recordDate.value))
const profile = ref(getProfile() || {})

const selectedFood = computed(() => foods.value[selectedFoodIndex.value] || null)

const selectedFoodLabel = computed(() => {
  if (!foods.value.length) {
    return '暂无可选项，请搜索'
  }
  const food = foods.value[selectedFoodIndex.value]
  return food ? `${food.name} (${food.unit || '100g'})` : '请选择食物'
})

const pendingSummary = computed(() => {
  const totals = pendingItems.value.reduce((sum, item) => {
    sum.calories += Number(item.calories || 0)
    sum.protein += Number(item.protein || 0)
    return sum
  }, { calories: 0, protein: 0 })

  return {
    calories: formatNumber(totals.calories),
    protein: formatNumber(totals.protein, 1)
  }
})

const detailGroups = computed(() => {
  return mealTypeOptions.map(item => {
    const items = dailyRecord.value.details.filter(detail => (detail.mealType || 'SNACK') === item.value)
    const calories = items.reduce((sum, detail) => sum + Number(detail.calories || 0), 0)
    return {
      ...item,
      items,
      count: items.length,
      calories
    }
  })
})

const reminderDigest = computed(() => buildReminderDigest({
  profile: profile.value,
  dailyRecord: dailyRecord.value,
  weeklyReport: {}
}))

function createEmptyDailyRecord(date) {
  return {
    recordDate: date,
    totalCalories: 0,
    totalProtein: 0,
    totalFat: 0,
    totalCarbohydrate: 0,
    details: []
  }
}

function normalizeDailyRecord(date, payload) {
  const fallback = createEmptyDailyRecord(date)
  if (!payload) return fallback
  return {
    ...fallback,
    ...payload,
    details: Array.isArray(payload.details) ? payload.details : []
  }
}

function handleDateChange(event) {
  recordDate.value = event.detail.value
  dailyRecord.value = createEmptyDailyRecord(recordDate.value)
  pendingItems.value = []
  loadDailyRecord()
}

function handleFoodSelect(event) {
  selectedFoodIndex.value = Number(event.detail.value || 0)
}

function applyQuickQuantity(amount) {
  quantity.value = `${amount}`
}

function clearPendingItems() {
  pendingItems.value = []
}

async function loadFoods() {
  if (!ensureLoggedIn()) return
  try {
    const response = await request.get('/foods', {
      keyword: foodKeyword.value,
      current: 1,
      size: 50
    })
    foods.value = response && Array.isArray(response.records) ? response.records : []
    selectedFoodIndex.value = 0
  } catch (error) {
    console.log('load foods for meal failed', error)
  }
}

async function loadDailyRecord() {
  if (!ensureLoggedIn()) return
  try {
    const response = await request.get('/meals/daily', { recordDate: recordDate.value })
    dailyRecord.value = normalizeDailyRecord(recordDate.value, response)
  } catch (error) {
    console.log('load daily meal failed', error)
  }
}

function buildDraftItem(showToast = true) {
  if (!foods.value.length) {
    if (showToast) uni.showToast({ title: '请先搜索或新增食物', icon: 'none' })
    return null
  }
  const food = selectedFood.value
  const numericQuantity = Number(quantity.value)

  if (!food || Number.isNaN(numericQuantity) || numericQuantity <= 0) {
    if (showToast) uni.showToast({ title: '请输入正确的食用量', icon: 'none' })
    return null
  }

  const ratio = numericQuantity / 100
  return {
    foodId: food.id,
    foodName: food.name,
    mealType: mealType.value,
    quantity: numericQuantity,
    calories: Number(food.calories || 0) * ratio,
    protein: Number(food.protein || 0) * ratio,
    fat: Number(food.fat || 0) * ratio,
    carbohydrate: Number(food.carbohydrate || 0) * ratio
  }
}

function addPendingItem() {
  const item = buildDraftItem()
  if (item) pendingItems.value.unshift(item) // 新增的排在前面
}

function removePendingItem(index) {
  pendingItems.value.splice(index, 1)
}

function showRewardFeedback(rewardFeedback, fallbackTitle) {
  const message = rewardFeedback?.messages?.[0]
  const pointsEarned = Number(rewardFeedback?.pointsEarned || 0)
  if (message) {
    uni.showToast({ title: message, icon: 'none' })
    return
  }
  if (pointsEarned > 0) {
    uni.showToast({ title: `记录成功 +${pointsEarned}积分`, icon: 'none' })
    return
  }
  uni.showToast({ title: fallbackTitle, icon: 'success' })
}

async function submitCurrentFood() {
  if (!ensureLoggedIn()) return
  const item = buildDraftItem()
  if (!item) return

  uni.showLoading({ title: '保存中' })
  try {
    const response = await request.post('/meals', {
      recordDate: recordDate.value,
      details: [{ foodId: item.foodId, quantity: item.quantity, mealType: item.mealType }]
    })
    dailyRecord.value = normalizeDailyRecord(recordDate.value, response)
    quantity.value = '100'
    uni.hideLoading()
    showRewardFeedback(response?.rewardFeedback, '已记录')
  } catch (error) {
    uni.hideLoading()
    uni.showToast({ title: '保存失败', icon: 'none' })
  }
}

async function submitPendingMeals() {
  if (!ensureLoggedIn()) return
  if (!pendingItems.value.length) return uni.showToast({ title: '暂无待保存内容', icon: 'none' })

  uni.showLoading({ title: '保存中' })
  try {
    const response = await request.post('/meals', {
      recordDate: recordDate.value,
      details: pendingItems.value.map(item => ({
        foodId: item.foodId,
        quantity: item.quantity,
        mealType: item.mealType
      }))
    })
    dailyRecord.value = normalizeDailyRecord(recordDate.value, response)
    pendingItems.value = []
    uni.hideLoading()
    showRewardFeedback(response?.rewardFeedback, '全部保存成功')
  } catch (error) {
    uni.hideLoading()
    uni.showToast({ title: '保存失败', icon: 'none' })
  }
}

function deleteDetail(detailId) {
  uni.showModal({
    title: '提示',
    content: '确定要删除这条记录吗？',
    confirmColor: '#059669', // 确认按钮用现代绿
    success: async (result) => {
      if (result.confirm) {
        try {
          const response = await request.delete(`/meals/details/${detailId}`)
          dailyRecord.value = normalizeDailyRecord(recordDate.value, response)
          uni.showToast({ title: '已移除', icon: 'success' })
        } catch (error) {
          console.log('delete meal detail failed', error)
        }
      }
    }
  })
}

function goFoods() { uni.navigateTo({ url: '/pages/foods/index' }) }
function goCapture() { uni.reLaunch({ url: '/pages/capture/index' }) }
function goHome() { uni.reLaunch({ url: '/pages/index/index' }) }
function goAdvisor() { uni.reLaunch({ url: '/pages/advisor/index' }) }

onShow(() => {
  if (!ensureLoggedIn()) return
  profile.value = getProfile() || {}
  loadFoods()
  loadDailyRecord()
})
</script>

<style scoped>
/* ========== 全局变量与底色 (纯白+现代绿) ========== */
.page-modern {
  --app-bg: #FFFFFF; /* 大背景纯白 */
  --card-bg: #FFFFFF; /* 卡片纯白 */
  --primary: #059669; /* 鲜明现代的核心绿色 */
  --primary-dark: #047857; /* 深绿色 */
  --primary-light: #d1fae5; /* 极浅绿，用于辅助背景 */
  --text-main: #111827; /* 深灰/黑文本 */
  --text-sub: #6b7280; /* 中灰色辅助文本 */
  --border-light: #e5e7eb; /* 浅色分割线和边框 */
  --warn-color: #d97706; /* 橙色替代红色警告 */
  --warn-bg: #fef3c7; /* 浅橙色背景 */
  
  min-height: 100vh;
  background-color: var(--app-bg);
  padding: 24rpx 24rpx calc(200rpx + env(safe-area-inset-bottom));
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
}

/* 去除 button 默认边框 */
button::after {
  border: none;
}

/* ========== 卡片统一采用白底 + 浅边框 ========== */
.xhs-card {
  background: var(--card-bg);
  border: 1rpx solid var(--border-light);
  border-radius: 32rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.03);
}

/* ========== 头部 ========== */
.header-action-btn {
  background: var(--primary-light);
  color: var(--primary);
  font-size: 26rpx;
  font-weight: 700;
  border-radius: 100rpx;
  padding: 0 32rpx;
  height: 64rpx;
  line-height: 64rpx;
  margin: 0;
  transition: all 0.2s;
}
.header-action-btn:active {
  transform: scale(0.95);
}

/* ========== Hero 总览数据卡片 ========== */
.hero-card {
  background: #FFFFFF;
  position: relative;
  overflow: hidden;
}

.date-section {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.hero-badge {
  font-size: 24rpx;
  color: var(--text-sub);
  margin-bottom: 12rpx;
}

.date-selector {
  display: flex;
  align-items: center;
  background: var(--primary-light);
  padding: 8rpx 24rpx;
  border-radius: 100rpx;
  border: none;
}

.hero-title {
  font-size: 36rpx;
  font-weight: 800;
  color: var(--primary-dark);
}

.icon-arrow {
  font-size: 24rpx;
  color: var(--primary);
  margin-left: 8rpx;
}

.main-stat {
  margin-top: 40rpx;
  display: flex;
  align-items: baseline;
}

.stat-value {
  font-size: 88rpx;
  font-weight: 900;
  color: var(--text-main);
  line-height: 1;
  letter-spacing: -2rpx;
}

.stat-unit {
  font-size: 28rpx;
  color: var(--text-sub);
  margin-left: 12rpx;
  font-weight: 600;
}

.hero-grid {
  display: flex;
  gap: 20rpx;
  margin-top: 48rpx;
}

.stat-mini-card {
  flex: 1;
  background: #FFFFFF;
  border: 1rpx solid var(--border-light);
  border-radius: 20rpx;
  padding: 20rpx;
  display: flex;
  flex-direction: column;
}

.highlight-card {
  background: #fffbeb;
  border-color: #fde68a;
}

.mini-label {
  font-size: 22rpx;
  color: var(--text-sub);
}

.mini-value {
  font-size: 36rpx;
  font-weight: 800;
  color: var(--text-main);
  margin-top: 8rpx;
}

.highlight-text {
  color: var(--warn-color);
}

/* ========== 面板通用头部 ========== */
.reminder-strip {
  display: flex;
  align-items: center;
  gap: 20rpx;
  background: #F5FBF8;
  border-color: #D4F2E1;
}
.reminder-strip-main {
  flex: 1;
  min-width: 0;
}
.reminder-strip-title {
  display: block;
  font-size: 28rpx;
  font-weight: 800;
  color: var(--text-main);
}
.reminder-strip-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: var(--text-sub);
}
.reminder-strip-btn {
  flex-shrink: 0;
  min-width: 144rpx;
  height: 72rpx;
  line-height: 72rpx;
  margin: 0;
  padding: 0 20rpx;
  border-radius: 999rpx;
  background: var(--primary);
  color: #FFFFFF;
  font-size: 24rpx;
  font-weight: 700;
}
.reminder-strip-btn::after { border: none; }

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 32rpx;
}

.panel-title {
  font-size: 32rpx;
  font-weight: 800;
  color: var(--text-main);
}

.panel-link {
  font-size: 26rpx;
  color: var(--primary);
  font-weight: 600;
}
.text-muted {
  color: var(--text-sub);
}

/* ========== 餐次胶囊 ========== */
.meal-scroll {
  width: 100%;
  white-space: nowrap;
  margin-bottom: 32rpx;
}

.meal-type-row {
  display: inline-flex;
  gap: 16rpx;
}

.meal-pill {
  padding: 16rpx 40rpx;
  border-radius: 100rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text-main);
  background: #FFFFFF;
  border: 1rpx solid var(--border-light);
  transition: all 0.2s ease;
}

.meal-pill.active {
  background: var(--primary);
  color: #FFFFFF;
  border-color: var(--primary);
  box-shadow: 0 6rpx 16rpx rgba(5, 150, 105, 0.2);
}

/* ========== 表单输入 ========== */
.search-box {
  display: flex;
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.xhs-input {
  flex: 1;
  height: 88rpx;
  background: #FFFFFF;
  border: 1rpx solid var(--border-light);
  border-radius: 24rpx;
  padding: 0 32rpx;
  font-size: 28rpx;
  color: var(--text-main);
}

.input-placeholder {
  color: #9ca3af;
}

.search-btn {
  width: 140rpx;
  height: 88rpx;
  line-height: 88rpx;
  background: var(--primary);
  color: #FFFFFF;
  border-radius: 24rpx;
  font-size: 28rpx;
  font-weight: 700;
  margin: 0;
}

.picker-box {
  height: 88rpx;
  background: #FFFFFF;
  border: 1rpx solid var(--border-light);
  border-radius: 24rpx;
  padding: 0 32rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
}

.picker-text {
  font-size: 28rpx;
  color: var(--text-main);
  font-weight: 500;
}
.placeholder-text {
  color: #9ca3af;
}

/* ========== 空状态提示 ========== */
.empty-tip-card {
  display: flex;
  align-items: center;
  background: #FFFFFF;
  border: 1rpx dashed var(--border-light);
  padding: 32rpx;
  border-radius: 24rpx;
  margin-bottom: 24rpx;
}

.empty-emoji {
  font-size: 64rpx;
  margin-right: 24rpx;
}

.empty-text-wrap {
  display: flex;
  flex-direction: column;
}

.empty-tip-title {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--text-main);
}

.empty-tip-desc {
  font-size: 24rpx;
  color: var(--text-sub);
  margin-top: 8rpx;
}

/* ========== 食物营养预览卡片 ========== */
.food-info-card {
  background: #FFFFFF;
  border: 1rpx solid var(--border-light);
  border-radius: 24rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
}

.food-info-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24rpx;
}

.food-name-wrap {
  display: flex;
  flex-direction: column;
}

.food-name {
  font-size: 32rpx;
  font-weight: 800;
  color: var(--text-main);
}

.food-tag {
  display: inline-block;
  font-size: 20rpx;
  color: var(--primary-dark);
  background: var(--primary-light);
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  margin-top: 8rpx;
  align-self: flex-start;
}

.food-kcal-highlight {
  font-size: 32rpx;
  font-weight: 800;
  color: var(--primary-dark);
}

.nutrition-strip {
  display: flex;
  justify-content: space-between;
  background: #f9fafb;
  border: 1rpx solid var(--border-light);
  border-radius: 16rpx;
  padding: 16rpx 24rpx;
}

.nut-item {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--text-main);
}

.nut-item text {
  color: var(--text-sub);
  font-weight: 400;
  margin-right: 4rpx;
}

/* ========== 食用量与快捷按钮 ========== */
.quantity-box {
  margin-bottom: 32rpx;
}

.quick-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 16rpx;
}

.chip {
  padding: 12rpx 28rpx;
  background: #FFFFFF;
  border: 1rpx solid var(--border-light);
  border-radius: 100rpx;
  font-size: 24rpx;
  color: var(--text-main);
  font-weight: 500;
  transition: background 0.2s;
}
.chip:active {
  background: var(--primary-light);
  border-color: var(--primary-light);
  color: var(--primary-dark);
}

/* ========== 按钮 ========== */
.action-row {
  display: flex;
  gap: 20rpx;
}

.btn-primary, .btn-secondary {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 100rpx;
  font-size: 30rpx;
  font-weight: 700;
  margin: 0;
}

.btn-primary {
  background: var(--primary);
  color: #FFFFFF;
  box-shadow: 0 8rpx 24rpx rgba(5, 150, 105, 0.2);
}

.btn-secondary {
  background: #FFFFFF;
  border: 1rpx solid var(--border-light);
  color: var(--text-main);
}

/* ========== 待保存列表 ========== */
.draft-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.draft-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #FFFFFF;
  border: 1rpx solid var(--border-light);
  border-radius: 20rpx;
  padding: 24rpx;
}

.draft-title-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 8rpx;
}

.draft-name {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--text-main);
}

.draft-tag {
  font-size: 20rpx;
  background: var(--primary-light);
  color: var(--primary-dark);
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}

.draft-desc {
  font-size: 24rpx;
  color: var(--text-sub);
}

.draft-remove-btn {
  width: 60rpx;
  height: 60rpx;
  line-height: 60rpx;
  text-align: center;
  background: #fef2f2;
  color: #ef4444;
  border-radius: 50%;
  font-size: 28rpx;
}

.submit-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 32rpx;
  padding-top: 24rpx;
  border-top: 2rpx dashed var(--border-light);
}

.submit-summary {
  font-size: 26rpx;
  color: var(--text-sub);
  font-weight: 500;
}

.btn-primary-small {
  background: var(--primary);
  color: #FFFFFF;
  font-size: 26rpx;
  font-weight: 700;
  padding: 0 40rpx;
  height: 72rpx;
  line-height: 72rpx;
  border-radius: 100rpx;
  margin: 0;
}

/* ========== 历史记录时间轴 ========== */
.history-section {
  margin-top: 48rpx;
}

.section-main-title {
  font-size: 32rpx;
  font-weight: 800;
  color: var(--text-main);
  margin-bottom: 24rpx;
  display: block;
  padding-left: 12rpx;
}

.history-group-card {
  padding: 0;
  overflow: hidden;
}

.history-head {
  background: #FFFFFF;
  padding: 24rpx 32rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1rpx solid var(--border-light);
}

.history-badge {
  font-size: 24rpx;
  font-weight: 700;
  padding: 6rpx 20rpx;
  border-radius: 100rpx;
}

/* 清爽白底标签 */
.theme-morning { background: #fffbeb; color: #d97706; border: 1rpx solid #fde68a; }
.theme-noon { background: var(--primary-light); color: var(--primary-dark); border: 1rpx solid #a7f3d0; }
.theme-night { background: #f0f9ff; color: #0369a1; border: 1rpx solid #bae6fd; }
.theme-snack { background: #fff7ed; color: #c2410c; border: 1rpx solid #ffedd5; }

.history-total {
  font-size: 28rpx;
  font-weight: 800;
  color: var(--text-main);
}

.history-list {
  padding: 16rpx 32rpx;
}

.history-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid var(--border-light);
}

.history-item:last-child {
  border-bottom: none;
}

.history-item-main {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.history-item-name {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--text-main);
}

.history-item-meta {
  font-size: 22rpx;
  color: var(--text-sub);
}

.history-item-right {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.history-item-kcal {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--text-main);
}

.delete-icon {
  font-size: 22rpx;
  color: #ef4444;
  background: #ffffff;
  border: 1rpx solid #fca5a5;
  padding: 8rpx 16rpx;
  border-radius: 8rpx;
  font-weight: 600;
}

.history-empty {
  padding: 48rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: #9ca3af;
}

/* ========== 底部悬浮操作栏 ========== */
.fab-bar {
  position: fixed;
  bottom: 40rpx;
  left: 32rpx;
  right: 32rpx;
  display: flex;
  gap: 24rpx;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  padding: 20rpx;
  border-radius: 100rpx;
  border: 1rpx solid var(--border-light);
  box-shadow: 0 16rpx 48rpx rgba(0, 0, 0, 0.05);
  z-index: 100;
}

.fab-btn {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 100rpx;
  font-size: 30rpx;
  font-weight: 700;
  margin: 0;
  transition: transform 0.2s;
}

.fab-btn:active {
  transform: scale(0.95);
}

.fab-btn.outline {
  background: #FFFFFF;
  color: var(--text-main);
  border: 1rpx solid var(--border-light);
  box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.02);
}

.fab-btn.solid {
  background: var(--primary);
  color: #FFFFFF;
  box-shadow: 0 8rpx 24rpx rgba(5, 150, 105, 0.2);
}
</style>
