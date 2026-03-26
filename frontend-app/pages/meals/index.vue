<template>
  <view class="page">
    <app-page-header
      title="饮食记录"
      subtitle="按日期查看、按餐次记录，保存后会立即写入后端"
      fallback-url="/pages/index/index"
    >
      <template #right>
        <button class="capture-button" @click="goCapture">智能识别</button>
      </template>
    </app-page-header>

    <view v-if="false" class="top-bar">
      <view>
        <text class="page-title">饮食记录</text>
        <text class="page-subtitle">按日期查看、按餐次记录，保存后会立即写入后端。</text>
      </view>
      <button class="capture-button" @click="goCapture">智能识别</button>
    </view>

    <view class="hero-card">
      <view class="hero-head">
        <view>
          <text class="hero-badge">当前记录日</text>
          <text class="hero-title">{{ recordDate }}</text>
          <text class="hero-desc">切换日期即可查看这一天的饮食与营养汇总。</text>
        </view>

        <picker mode="date" :value="recordDate" @change="handleDateChange">
          <view class="date-pill">切换日期</view>
        </picker>
      </view>

      <view class="hero-grid">
        <view class="hero-stat-card">
          <text class="hero-stat-label">总热量</text>
          <text class="hero-stat-value">{{ formatNumber(dailyRecord.totalCalories) }}</text>
          <text class="hero-stat-unit">千卡</text>
        </view>
        <view class="hero-stat-card">
          <text class="hero-stat-label">蛋白质</text>
          <text class="hero-stat-value">{{ formatNumber(dailyRecord.totalProtein) }}</text>
          <text class="hero-stat-unit">克</text>
        </view>
        <view class="hero-stat-card">
          <text class="hero-stat-label">已保存</text>
          <text class="hero-stat-value">{{ dailyRecord.details.length }}</text>
          <text class="hero-stat-unit">条</text>
        </view>
        <view class="hero-stat-card">
          <text class="hero-stat-label">待保存</text>
          <text class="hero-stat-value">{{ pendingItems.length }}</text>
          <text class="hero-stat-unit">条</text>
        </view>
      </view>

      <view class="meal-count-row">
        <view
          v-for="item in mealTypeStats"
          :key="item.value"
          class="meal-count-chip"
          :class="item.theme"
        >
          <text class="meal-count-name">{{ item.label }}</text>
          <text class="meal-count-value">{{ item.count }}</text>
        </view>
      </view>
    </view>

    <view class="panel">
      <view class="section-head">
        <view>
          <text class="section-title">添加一条记录</text>
          <text class="section-desc">先选餐次，再从食物库里挑选食物并填写食用量。</text>
        </view>
        <text class="section-link" @click="goFoods">去食物库</text>
      </view>

      <view class="meal-type-row">
        <view
          v-for="item in mealTypeOptions"
          :key="item.value"
          class="meal-type-chip"
          :class="[item.theme, { active: mealType === item.value }]"
          @click="mealType = item.value"
        >
          <text class="meal-type-text">{{ item.label }}</text>
        </view>
      </view>

      <view class="search-row">
        <input
          v-model="foodKeyword"
          class="field search-field"
          placeholder="搜索食物名称"
          confirm-type="search"
          @confirm="loadFoods"
        />
        <button class="search-button" @click="loadFoods">搜索</button>
      </view>

      <picker :range="foods" range-key="name" :value="selectedFoodIndex" @change="handleFoodSelect">
        <view class="picker-field">{{ selectedFoodLabel }}</view>
      </picker>

      <view v-if="!foods.length" class="empty-inline-card">
        <text class="empty-inline-title">当前还没有可选食物</text>
        <text class="empty-inline-desc">先去食物库新增食物，或者重启食物服务让示例食物自动注入。</text>
      </view>

      <view v-else-if="selectedFood" class="food-preview-card">
        <view class="food-preview-head">
          <view>
            <text class="food-preview-title">{{ selectedFood.name }}</text>
            <text class="food-preview-meta">{{ selectedFood.category || '未分类' }} · {{ selectedFood.unit || '100克' }}</text>
          </view>
          <text class="food-preview-kcal">{{ formatNumber(selectedFood.calories) }} 千卡</text>
        </view>

        <view class="food-preview-grid">
          <view class="food-preview-item">
            <text class="food-preview-label">蛋白质</text>
            <text class="food-preview-value">{{ formatNumber(selectedFood.protein, 1) }} 克</text>
          </view>
          <view class="food-preview-item">
            <text class="food-preview-label">脂肪</text>
            <text class="food-preview-value">{{ formatNumber(selectedFood.fat, 1) }} 克</text>
          </view>
          <view class="food-preview-item">
            <text class="food-preview-label">碳水</text>
            <text class="food-preview-value">{{ formatNumber(selectedFood.carbohydrate, 1) }} 克</text>
          </view>
          <view class="food-preview-item">
            <text class="food-preview-label">纤维</text>
            <text class="food-preview-value">{{ formatNumber(selectedFood.fiber, 1) }} 克</text>
          </view>
        </view>
      </view>

      <input v-model="quantity" class="field" type="digit" placeholder="请输入食用量，单位 克" />

      <view class="quick-quantity-row">
        <view
          v-for="amount in quickQuantities"
          :key="amount"
          class="quick-quantity-chip"
          @click="applyQuickQuantity(amount)"
        >
          <text class="quick-quantity-text">{{ amount }}克</text>
        </view>
      </view>

      <view class="button-row">
        <button class="secondary-button" @click="addPendingItem">加入待保存</button>
        <button class="primary-button" @click="submitCurrentFood">立即保存</button>
      </view>
    </view>

    <view class="panel">
      <view class="section-head">
        <view>
          <text class="section-title">待保存列表</text>
          <text class="section-desc">支持先选多条再一次性提交。</text>
        </view>
        <text v-if="pendingItems.length" class="section-link" @click="clearPendingItems">清空</text>
      </view>

      <view v-if="!pendingItems.length" class="empty-inline-card">
        <text class="empty-inline-title">还没有待保存内容</text>
        <text class="empty-inline-desc">上面选好食物和餐次后，可以先加入待保存列表。</text>
      </view>

      <view v-for="(item, index) in pendingItems" :key="`${item.foodId}-${index}`" class="draft-card">
        <view class="draft-main">
          <view class="draft-top">
            <text class="draft-name">{{ item.foodName }}</text>
            <view class="draft-badge">
              <text class="draft-badge-text">{{ mealTypeLabel(item.mealType) }}</text>
            </view>
          </view>
          <text class="draft-meta">{{ formatNumber(item.quantity) }} 克 · {{ formatNumber(item.calories) }} 千卡</text>
        </view>
        <button class="draft-remove" @click="removePendingItem(index)">移除</button>
      </view>

      <view v-if="pendingItems.length" class="submit-panel">
        <view>
          <text class="submit-title">预计新增 {{ pendingItems.length }} 条记录</text>
          <text class="submit-desc">热量 {{ pendingSummary.calories }} 千卡 · 蛋白质 {{ pendingSummary.protein }} 克</text>
        </view>
        <button class="submit-button" @click="submitPendingMeals">全部保存</button>
      </view>
    </view>

    <view class="section-head history-head">
      <view>
        <text class="section-title">已保存记录</text>
        <text class="section-desc">按餐次查看这一天已经写入后端的数据。</text>
      </view>
    </view>

    <view class="timeline-card">
      <view v-for="group in detailGroups" :key="group.value" class="group-card">
        <view class="group-head">
          <view class="group-badge" :class="group.theme">
            <text class="group-badge-text">{{ group.label }}</text>
          </view>
          <text class="group-meta">{{ group.count }} 条 · {{ formatNumber(group.calories) }} 千卡</text>
        </view>

        <view v-if="group.items.length">
          <view v-for="detail in group.items" :key="detail.id" class="detail-card">
            <view class="detail-main">
              <text class="detail-name">{{ detail.foodName }}</text>
              <text class="detail-meta">{{ formatNumber(detail.quantity) }} 克 · {{ formatNumber(detail.calories) }} 千卡 · {{ formatTime(detail.createdAt) }}</text>
            </view>
            <button class="detail-delete" @click="deleteDetail(detail.id)">删除</button>
          </view>
        </view>

        <view v-else class="group-empty">
          <text class="group-empty-text">这一餐次还没有记录</text>
        </view>
      </view>
    </view>

    <view class="footer-actions">
      <button class="ghost-button" @click="goHome">返回首页</button>
      <button class="primary-button" @click="goCapture">去拍照识别</button>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { ensureLoggedIn, formatToday } from '@/utils/auth.js'
import { formatNumber, formatTime, mealTypeLabel } from '@/utils/format.js'

const quickQuantities = [50, 100, 150, 200, 300]
const mealTypeOptions = [
  { label: '早餐', value: 'BREAKFAST', theme: 'warm' },
  { label: '午餐', value: 'LUNCH', theme: 'green' },
  { label: '晚餐', value: 'DINNER', theme: 'blue' },
  { label: '加餐', value: 'SNACK', theme: 'gold' }
]

const recordDate = ref(formatToday())
const mealType = ref('BREAKFAST')
const foodKeyword = ref('')
const foods = ref([])
const selectedFoodIndex = ref(0)
const quantity = ref('100')
const pendingItems = ref([])
const dailyRecord = ref(createEmptyDailyRecord(recordDate.value))

const selectedFood = computed(() => foods.value[selectedFoodIndex.value] || null)

const selectedFoodLabel = computed(() => {
  if (!foods.value.length) {
    return '先搜索食物名称，或者去食物库新增'
  }
  const food = foods.value[selectedFoodIndex.value]
  return food ? `${food.name} · ${food.unit || '100克'}` : '请选择食物'
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

const mealTypeStats = computed(() => {
  return mealTypeOptions.map(item => ({
    ...item,
    count: dailyRecord.value.details.filter(detail => (detail.mealType || 'SNACK') === item.value).length
  }))
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
  if (!payload) {
    return fallback
  }

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
  if (!ensureLoggedIn()) {
    return
  }

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
  if (!ensureLoggedIn()) {
    return
  }

  try {
    const response = await request.get('/meals/daily', {
      recordDate: recordDate.value
    })
    dailyRecord.value = normalizeDailyRecord(recordDate.value, response)
  } catch (error) {
    console.log('load daily meal failed', error)
  }
}

function buildDraftItem(showToast = true) {
  if (!foods.value.length) {
    if (showToast) {
      uni.showToast({
        title: '请先搜索或新增食物',
        icon: 'none'
      })
    }
    return null
  }

  const food = selectedFood.value
  const numericQuantity = Number(quantity.value)

  if (!food || Number.isNaN(numericQuantity) || numericQuantity <= 0) {
    if (showToast) {
      uni.showToast({
        title: '请输入正确的食用量',
        icon: 'none'
      })
    }
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
  if (!item) {
    return
  }
  pendingItems.value.push(item)
}

function removePendingItem(index) {
  pendingItems.value.splice(index, 1)
}

async function submitCurrentFood() {
  if (!ensureLoggedIn()) {
    return
  }

  const item = buildDraftItem()
  if (!item) {
    return
  }

  try {
    const response = await request.post('/meals', {
      recordDate: recordDate.value,
      details: [
        {
          foodId: item.foodId,
          quantity: item.quantity,
          mealType: item.mealType
        }
      ]
    })
    dailyRecord.value = normalizeDailyRecord(recordDate.value, response)
    quantity.value = '100'
    uni.showToast({
      title: '保存成功',
      icon: 'success'
    })
  } catch (error) {
    console.log('submit current food failed', error)
  }
}

async function submitPendingMeals() {
  if (!ensureLoggedIn()) {
    return
  }

  if (!pendingItems.value.length) {
    uni.showToast({
      title: '请先加入待保存内容',
      icon: 'none'
    })
    return
  }

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
    uni.showToast({
      title: '全部保存成功',
      icon: 'success'
    })
  } catch (error) {
    console.log('submit pending meals failed', error)
  }
}

function deleteDetail(detailId) {
  uni.showModal({
    title: '删除记录',
    content: '确认删除这条饮食明细吗？',
    success: async (result) => {
      if (!result.confirm) {
        return
      }

      try {
        const response = await request.delete(`/meals/details/${detailId}`)
        dailyRecord.value = normalizeDailyRecord(recordDate.value, response)
        uni.showToast({
          title: '删除成功',
          icon: 'success'
        })
      } catch (error) {
        console.log('delete meal detail failed', error)
      }
    }
  })
}

function goFoods() {
  uni.navigateTo({
    url: '/pages/foods/index'
  })
}

function goCapture() {
  uni.reLaunch({
    url: '/pages/capture/index'
  })
}

function goHome() {
  uni.reLaunch({
    url: '/pages/index/index'
  })
}

onShow(() => {
  if (!ensureLoggedIn()) {
    return
  }

  loadFoods()
  loadDailyRecord()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 32rpx 28rpx calc(96rpx + env(safe-area-inset-bottom));
}

.top-bar,
.hero-head,
.section-head,
.search-row,
.button-row,
.food-preview-head,
.draft-card,
.draft-top,
.submit-panel,
.group-head,
.detail-card,
.footer-actions {
  display: flex;
  align-items: center;
}

.top-bar,
.hero-head,
.section-head,
.food-preview-head,
.draft-card,
.submit-panel,
.group-head,
.detail-card,
.footer-actions {
  justify-content: space-between;
}

.page-title {
  display: block;
  font-size: 62rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.page-subtitle,
.hero-desc,
.section-desc,
.food-preview-meta,
.food-preview-label,
.empty-inline-desc,
.draft-meta,
.submit-desc,
.group-meta,
.detail-meta,
.hero-stat-label,
.hero-stat-unit {
  font-size: 26rpx;
  color: var(--nm-muted);
}

.page-subtitle {
  display: block;
  margin-top: 10rpx;
}

.capture-button,
.primary-button,
.secondary-button,
.search-button,
.submit-button,
.ghost-button {
  height: 84rpx;
  font-size: 28rpx;
  font-weight: 700;
}

.capture-button,
.search-button,
.submit-button,
.primary-button {
  background: var(--nm-primary-dark);
  color: #ffffff;
}

.secondary-button {
  background: #e7eef9;
  color: #1e3a5f;
}

.ghost-button {
  background: rgba(255, 255, 255, 0.86);
  color: var(--nm-text);
}

.hero-card,
.panel,
.timeline-card,
.group-card {
  margin-top: 26rpx;
  border-radius: 34rpx;
  background: var(--nm-card);
  box-shadow: var(--nm-shadow);
}

.hero-card,
.panel,
.timeline-card {
  padding: 28rpx;
}

.hero-card {
  background: linear-gradient(155deg, #eff6ff 0%, #ffffff 100%);
}

.hero-badge {
  display: inline-flex;
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.84);
  font-size: 24rpx;
  color: #1d4ed8;
}

.hero-title {
  display: block;
  margin-top: 14rpx;
  font-size: 48rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.date-pill {
  padding: 18rpx 24rpx;
  border-radius: 999rpx;
  background: #172033;
  font-size: 26rpx;
  font-weight: 700;
  color: #ffffff;
}

.hero-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
  margin-top: 24rpx;
}

.hero-stat-card {
  padding: 22rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.92);
}

.hero-stat-value {
  display: block;
  margin-top: 12rpx;
  font-size: 42rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.meal-count-row,
.meal-type-row,
.quick-quantity-row {
  display: flex;
  gap: 14rpx;
  flex-wrap: wrap;
}

.meal-count-row {
  margin-top: 22rpx;
}

.meal-count-chip,
.meal-type-chip {
  padding: 14rpx 20rpx;
  border-radius: 999rpx;
}

.meal-count-chip.warm,
.meal-type-chip.warm {
  background: #fff1d6;
}

.meal-count-chip.green,
.meal-type-chip.green {
  background: #e8f8ef;
}

.meal-count-chip.blue,
.meal-type-chip.blue {
  background: #ebf2ff;
}

.meal-count-chip.gold,
.meal-type-chip.gold {
  background: #f8efd4;
}

.meal-count-name,
.meal-type-text {
  font-size: 24rpx;
  font-weight: 700;
  color: #5f594f;
}

.meal-count-value {
  margin-left: 10rpx;
  font-size: 24rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.meal-type-row {
  margin-top: 18rpx;
}

.meal-type-chip.active {
  background: var(--nm-primary);
}

.meal-type-chip.active .meal-type-text {
  color: #ffffff;
}

.section-title {
  display: block;
  font-size: 34rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.section-link {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--nm-primary);
}

.search-row {
  gap: 14rpx;
  margin-top: 18rpx;
}

.field,
.picker-field {
  width: 100%;
  margin-top: 16rpx;
  padding: 22rpx 24rpx;
  border-radius: 24rpx;
  background: #f5f4ef;
  font-size: 28rpx;
  color: var(--nm-text);
}

.search-field {
  flex: 1;
  margin-top: 0;
}

.search-button {
  width: 150rpx;
}

.empty-inline-card {
  margin-top: 18rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  background: #f7f5ef;
}

.empty-inline-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: var(--nm-text);
}

.food-preview-card {
  margin-top: 18rpx;
  padding: 22rpx;
  border-radius: 28rpx;
  background: #f8fafc;
}

.food-preview-title {
  display: block;
  font-size: 32rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.food-preview-kcal {
  font-size: 30rpx;
  font-weight: 800;
  color: var(--nm-primary);
}

.food-preview-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14rpx;
  margin-top: 18rpx;
}

.food-preview-item {
  padding: 18rpx;
  border-radius: 22rpx;
  background: rgba(255, 255, 255, 0.94);
}

.food-preview-value {
  display: block;
  margin-top: 10rpx;
  font-size: 28rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.quick-quantity-row {
  margin-top: 18rpx;
}

.quick-quantity-chip {
  padding: 14rpx 22rpx;
  border-radius: 999rpx;
  background: #edf6f1;
}

.quick-quantity-text {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--nm-primary);
}

.button-row,
.footer-actions {
  gap: 16rpx;
  margin-top: 22rpx;
}

.button-row button,
.footer-actions button {
  flex: 1;
}

.draft-card {
  gap: 18rpx;
  margin-top: 18rpx;
  padding: 22rpx;
  border-radius: 24rpx;
  background: #f8fafc;
}

.draft-main,
.detail-main {
  flex: 1;
  min-width: 0;
}

.draft-name,
.submit-title,
.detail-name {
  display: block;
  font-size: 30rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.draft-badge,
.group-badge {
  padding: 10rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(14, 165, 109, 0.12);
}

.draft-badge-text,
.group-badge-text {
  font-size: 22rpx;
  font-weight: 700;
  color: var(--nm-primary);
}

.draft-remove,
.detail-delete {
  min-width: 120rpx;
  height: 72rpx;
  background: #fee2e2;
  color: #991b1b;
  font-size: 24rpx;
  font-weight: 700;
}

.submit-panel {
  margin-top: 20rpx;
  padding: 22rpx;
  border-radius: 28rpx;
  background: linear-gradient(145deg, #fff5de 0%, #ffffff 100%);
}

.submit-button {
  min-width: 176rpx;
}

.history-head {
  margin-top: 34rpx;
}

.group-card {
  padding: 22rpx;
  background: #ffffff;
}

.group-card + .group-card {
  margin-top: 18rpx;
}

.group-badge.warm {
  background: #fff1d6;
}

.group-badge.green {
  background: #e8f8ef;
}

.group-badge.blue {
  background: #ebf2ff;
}

.group-badge.gold {
  background: #f8efd4;
}

.group-meta {
  font-size: 24rpx;
}

.detail-card {
  gap: 18rpx;
  margin-top: 18rpx;
  padding: 20rpx;
  border-radius: 22rpx;
  background: #f8fafc;
}

.group-empty {
  margin-top: 18rpx;
  padding: 20rpx;
  border-radius: 22rpx;
  background: #f8fafc;
}

.group-empty-text {
  font-size: 26rpx;
  color: var(--nm-muted);
}
</style>
