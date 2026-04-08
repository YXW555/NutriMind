<template>
  <view class="page-modern">
    <app-page-header
      title="饮食计划"
      fallback-url="/pages/meals/index"
      :border="false"
    />

    <view class="ai-planner-card">
      <view class="planner-header">
        <view class="header-left">
          <text class="emoji-icon">✨</text>
          <view>
            <text class="card-title">AI 智能规划</text>
            <text class="card-subtitle">输入你的饮食偏好，一键生成专属方案</text>
          </view>
        </view>
        <picker mode="date" :value="selectedDate" @change="handleDateChange">
          <view class="date-selector">
            <text class="date-text">{{ shortDate(selectedDate, true) }}</text>
            <text class="arrow-down">▾</text>
          </view>
        </picker>
      </view>

      <view class="prompt-box">
        <input
          v-model="generatePreference"
          class="prompt-input"
          placeholder="例如：减脂训练日、晚餐清淡、控糖优先..."
          placeholder-class="prompt-placeholder"
        />
      </view>

      <view class="ai-action-row">
        <button class="btn-ai-primary" :loading="generatingDaily" @click="generateDailyPlan">生成今日计划</button>
        <button class="btn-ai-secondary" :loading="generatingWeek" @click="generateWeekPlan">生成本周草案</button>
      </view>

      <view class="week-slider" v-if="weekPlans.length > 0">
        <scroll-view scroll-x class="week-scroll" :show-scrollbar="false">
          <view class="week-row">
            <view
              v-for="item in weekPlans"
              :key="item.planDate"
              class="day-bubble"
              :class="{ active: item.planDate === selectedDate }"
              @click="selectPlanDate(item.planDate)"
            >
              <text class="bubble-date">{{ shortDate(item.planDate) }}</text>
              <view class="bubble-dot" :class="{ 'has-plan': item.hasPlan }"></view>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>

    <view class="summary-dashboard">
      <view class="dashboard-header">
        <text class="dashboard-title">{{ plan.title || '今日饮食计划' }}</text>
        <view class="status-badge" :class="plan.status.toLowerCase()">
          {{ planStatusLabel(plan.status) }}
        </view>
      </view>

      <view class="macro-grid">
        <view class="macro-item highlight">
          <text class="macro-value">{{ formatNumber(plan.totalCalories) }}</text>
          <text class="macro-label">总热量(kcal)</text>
        </view>
        <view class="macro-item">
          <text class="macro-value">{{ formatNumber(plan.totalProtein, 1) }}</text>
          <text class="macro-label">蛋白质(g)</text>
        </view>
        <view class="macro-item">
          <text class="macro-value">{{ formatNumber(plan.totalCarbohydrate, 1) }}</text>
          <text class="macro-label">碳水(g)</text>
        </view>
        <view class="macro-item">
          <text class="macro-value">{{ formatNumber(plan.totalFat, 1) }}</text>
          <text class="macro-label">脂肪(g)</text>
        </view>
      </view>

      <view v-if="showTargetStrip" class="target-compare-bar">
        <view v-if="plan.targetCalories" class="compare-item">
          <text class="c-label">目标热量</text>
          <text class="c-value">{{ formatNumber(plan.targetCalories) }}</text>
        </view>
        <view v-if="plan.calorieGap !== null" class="compare-item">
          <text class="c-label">热量偏差</text>
          <text class="c-value" :class="{ 'is-over': plan.calorieGap > 0 }">{{ signedNumber(plan.calorieGap) }}</text>
        </view>
      </view>

      <view class="ai-insight-card" v-if="hasPlanInsights">
        <view class="insight-head">
          <text class="insight-icon">🤖</text>
          <text class="insight-title">AI 洞察</text>
          <text class="insight-mode">{{ generationModeLabel(plan.generationMode) }}</text>
        </view>
        <text class="insight-content">{{ plan.summary }}</text>
      </view>

      <view class="tips-container" v-if="plan.tips.length || plan.warnings.length">
        <view v-if="plan.tips.length" class="tip-group">
          <view v-for="(tip, index) in plan.tips" :key="`tip-${index}`" class="tip-row">
            <text class="tip-emoji">💡</text>
            <text class="tip-text">{{ tip }}</text>
          </view>
        </view>
        <view v-if="plan.warnings.length" class="tip-group warning">
          <view v-for="(warning, index) in plan.warnings" :key="`warn-${index}`" class="tip-row">
            <text class="tip-emoji">⚠️</text>
            <text class="tip-text">{{ warning }}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="execution-panel">
      <view class="execution-header">
        <text class="panel-main-title">执行清单</text>
        <text class="panel-sub-title">共 {{ planItems.length }} 项</text>
      </view>

      <view v-if="!planItems.length" class="empty-state">
        <text class="empty-icon">🍽️</text>
        <text class="empty-text">这天还没安排食物，让 AI 帮你生成吧</text>
      </view>

      <view class="meal-flow">
        <view v-for="section in groupedPlanItems" :key="section.value" class="meal-group">
          <view class="meal-group-title">
            <text class="meal-name">{{ section.label }}</text>
            <text class="meal-cal">{{ formatNumber(section.totalCalories) }} kcal</text>
          </view>
          
          <view class="food-list">
            <view 
              v-for="(item, index) in section.items" 
              :key="`${item.foodId}-${index}`" 
              class="food-item"
            >
              <view class="food-info">
                <text class="food-name">{{ item.foodName }}</text>
                <text class="food-desc">{{ formatNumber(item.quantity) }}g · {{ formatNumber(item.calories) }}kcal <text v-if="item.note" class="food-note">| {{ item.note }}</text></text>
              </view>
              <view class="food-remove" @click="removePlanItem(item.originalIndex)">
                <text class="remove-icon">×</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <view class="action-dock" v-if="planItems.length">
        <button class="btn-save" @click="savePlan">保存草稿</button>
        <button class="btn-apply" @click="applyPlan">应用到饮食记录</button>
      </view>
    </view>

    <view class="manual-edit-panel">
      <view class="edit-header" @click="editorExpanded = !editorExpanded">
        <view class="header-left">
          <text class="panel-main-title">手动补充 & 微调</text>
        </view>
        <text class="expand-icon" :class="{ 'is-open': editorExpanded }">›</text>
      </view>

      <view class="edit-body" :class="{ 'is-expanded': editorExpanded }">
        <input v-model="planForm.title" class="custom-input" placeholder="计划标题 (选填)" />
        <textarea v-model="planForm.notes" class="custom-textarea" placeholder="补充说明 (选填)" />

        <text class="sub-label">选择餐次</text>
        <view class="meal-tags">
          <view 
            v-for="item in mealTypeOptions" 
            :key="item.value" 
            class="meal-tag" 
            :class="{ active: mealType === item.value }"
            @click="mealType = item.value"
          >
            {{ item.label }}
          </view>
        </view>

        <view class="search-add-box">
          <view class="search-bar">
            <input v-model="foodKeyword" class="search-input" placeholder="搜索需要补充的食物" @confirm="loadFoods" />
            <button class="btn-search" @click="loadFoods">搜索</button>
          </view>

          <picker :range="foods" range-key="name" :value="selectedFoodIndex" @change="handleFoodSelect">
            <view class="picker-box">
              <text class="picker-text" :class="{'has-val': foods.length > 0}">{{ selectedFoodLabel }}</text>
              <text class="picker-arrow">▾</text>
            </view>
          </picker>

          <view class="input-grid">
            <input v-model="quantity" class="custom-input half" type="digit" placeholder="数量(g)" />
            <input v-model="itemNote" class="custom-input half" placeholder="备注(选填)" />
          </view>

          <button class="btn-add-food" @click="addPlanItem">+ 加入计划清单</button>
        </view>
      </view>
    </view>
    
    <view class="safe-area-bottom"></view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { ensureLoggedIn, formatToday } from '@/utils/auth.js'
import { formatNumber } from '@/utils/format.js'

const mealTypeOptions = [
  { label: '早餐', value: 'BREAKFAST', theme: 'warm' },
  { label: '午餐', value: 'LUNCH', theme: 'green' },
  { label: '晚餐', value: 'DINNER', theme: 'blue' },
  { label: '加餐', value: 'SNACK', theme: 'gold' }
]

const selectedDate = ref(formatToday())
const weekPlans = ref([])
const plan = ref(createEmptyPlan())
const planForm = ref({ title: '', notes: '' })
const planItems = ref([])
const generatePreference = ref('')
const generatingDaily = ref(false)
const generatingWeek = ref(false)
const editorExpanded = ref(false)

const foods = ref([])
const selectedFoodIndex = ref(0)
const foodKeyword = ref('')
const mealType = ref('BREAKFAST')
const quantity = ref('100')
const itemNote = ref('')

const selectedFood = computed(() => foods.value[selectedFoodIndex.value] || null)
const hasPlanInsights = computed(() => Boolean(plan.value.summary || plan.value.generationMode))
const showTargetStrip = computed(() =>
  Boolean(
    plan.value.targetCalories ||
      plan.value.targetProtein ||
      plan.value.calorieGap !== null ||
      plan.value.proteinGap !== null
  )
)

const groupedPlanItems = computed(() =>
  mealTypeOptions
    .map((option) => {
      const items = planItems.value
        .map((item, index) => ({ ...item, originalIndex: index }))
        .filter((item) => item.mealType === option.value)
      return {
        ...option,
        items,
        totalCalories: items.reduce((sum, item) => sum + Number(item.calories || 0), 0)
      }
    })
    .filter((section) => section.items.length)
)

const selectedFoodLabel = computed(() => {
  if (!foods.value.length) return '搜出食物后在此选择'
  const food = foods.value[selectedFoodIndex.value]
  return food ? `${food.name} · ${food.unit || '100g'}` : '请选择食物'
})

function createEmptyPlan() {
  return {
    title: '', notes: '', status: 'DRAFT',
    totalCalories: 0, totalProtein: 0, totalFat: 0, totalCarbohydrate: 0,
    targetCalories: null, targetProtein: null, calorieGap: null, proteinGap: null,
    generationMode: '', summary: '', tips: [], warnings: [], references: [], items: []
  }
}

function normalizePlan(payload) {
  const next = payload || createEmptyPlan()
  const items = Array.isArray(next.items) ? next.items : []
  plan.value = {
    ...createEmptyPlan(), ...next,
    tips: Array.isArray(next.tips) ? next.tips : [],
    warnings: Array.isArray(next.warnings) ? next.warnings : [],
    references: Array.isArray(next.references) ? next.references : [],
    items
  }
  planForm.value = { title: next.title || '', notes: next.notes || '' }
  planItems.value = items.map((item, index) => ({ ...item, sortOrder: item.sortOrder ?? index }))
  // 如果没有内容，自动展开编辑区
  editorExpanded.value = !items.length
}

function applyGeneratedPlan(payload) {
  normalizePlan({ status: 'GENERATED', ...payload })
}

async function loadFoods() {
  if (!ensureLoggedIn() || !foodKeyword.value.trim()) return
  try {
    const response = await request.get('/foods', { keyword: foodKeyword.value, current: 1, size: 20 })
    foods.value = Array.isArray(response?.records) ? response.records : []
    selectedFoodIndex.value = 0
  } catch (error) {}
}

async function loadPlan() {
  if (!ensureLoggedIn()) return
  try {
    const [dailyPlan, weekly] = await Promise.all([
      request.get('/meals/plans/daily', { planDate: selectedDate.value }),
      request.get('/meals/plans/week', { anchorDate: selectedDate.value })
    ])
    normalizePlan(dailyPlan)
    weekPlans.value = Array.isArray(weekly) ? weekly : []
  } catch (error) {}
}

async function generateDailyPlan() {
  if (!ensureLoggedIn()) return
  generatingDaily.value = true
  try {
    const response = await request.post('/meals/plans/generate/daily', {
      planDate: selectedDate.value,
      preference: generatePreference.value || null,
      saveDraft: false
    })
    applyGeneratedPlan(response)
    uni.showToast({ title: '已生成', icon: 'success' })
  } catch (error) {
  } finally { generatingDaily.value = false }
}

async function generateWeekPlan() {
  if (!ensureLoggedIn()) return
  generatingWeek.value = true
  try {
    const response = await request.post('/meals/plans/generate/week', {
      anchorDate: selectedDate.value,
      preference: generatePreference.value || null,
      saveDraft: true
    })
    const currentDay = Array.isArray(response?.days)
      ? response.days.find((item) => item.planDate === selectedDate.value)
      : null
    await loadPlan()
    if (currentDay) { applyGeneratedPlan({ ...currentDay, status: 'READY' }) }
    uni.showToast({ title: '本周草案已生成', icon: 'success' })
  } catch (error) {
  } finally { generatingWeek.value = false }
}

function handleDateChange(event) {
  selectPlanDate(event.detail.value)
}

async function selectPlanDate(value) {
  selectedDate.value = value
  await loadPlan()
}

function handleFoodSelect(event) {
  selectedFoodIndex.value = Number(event.detail.value || 0)
}

function resetEditor() {
  quantity.value = '100'
  itemNote.value = ''
}

function addPlanItem() {
  if (!selectedFood.value) {
    uni.showToast({ title: '请先搜索并选择食物', icon: 'none' })
    return
  }
  const numericQuantity = Number(quantity.value)
  if (Number.isNaN(numericQuantity) || numericQuantity <= 0) {
    uni.showToast({ title: '输入正确数量', icon: 'none' })
    return
  }

  const ratio = numericQuantity / 100
  planItems.value.push({
    foodId: selectedFood.value.id,
    foodName: selectedFood.value.name,
    mealType: mealType.value,
    quantity: numericQuantity,
    note: itemNote.value || '',
    sortOrder: planItems.value.length,
    calories: Number(selectedFood.value.calories || 0) * ratio,
    protein: Number(selectedFood.value.protein || 0) * ratio,
    fat: Number(selectedFood.value.fat || 0) * ratio,
    carbohydrate: Number(selectedFood.value.carbohydrate || 0) * ratio
  })
  resetEditor()
  uni.showToast({ title: '已加入', icon: 'none' })
}

function removePlanItem(index) {
  planItems.value.splice(index, 1)
}

async function savePlan() {
  if (!ensureLoggedIn()) return
  try {
    const response = await persistPlan()
    normalizePlan(response)
    await loadPlan()
    uni.showToast({ title: '保存成功', icon: 'success' })
  } catch (error) {}
}

async function applyPlan() {
  if (!ensureLoggedIn()) return
  if (!planItems.value.length) {
    uni.showToast({ title: '计划是空的', icon: 'none' })
    return
  }
  try {
    await persistPlan()
    await request.post('/meals/plans/daily/apply', { planDate: selectedDate.value })
    uni.showModal({
      title: '应用成功',
      content: '已写入今日饮食记录，去查看吗？',
      success: (result) => {
        if (result.confirm) uni.navigateTo({ url: '/pages/meals/index' })
      }
    })
    await loadPlan()
  } catch (error) {}
}

function persistPlan() {
  return request.put('/meals/plans/daily', {
    planDate: selectedDate.value,
    title: planForm.value.title || null,
    notes: planForm.value.notes || null,
    items: planItems.value.map((item, index) => ({
      foodId: item.foodId, mealType: item.mealType, quantity: item.quantity,
      note: item.note || null, sortOrder: index
    }))
  })
}

function shortDate(value, withText = false) {
  if (!value) return '--'
  const [year, month, day] = String(value).split('-')
  if (value === formatToday() && withText) return '今天'
  return `${month}/${day}`
}

function generationModeLabel(value) {
  const map = { 'AI_AGENT': 'AI 智能生成', 'RULE_BASED': '基础生成', 'MIXED': '混合生成' }
  return map[value] || '智能生成'
}

function planStatusLabel(value) {
  const map = { DRAFT: '草稿阶段', GENERATED: 'AI 已生成', READY: '待执行', APPLIED: '已应用' }
  return map[value] || '规划中'
}

function signedNumber(value) {
  const num = Number(value || 0)
  return num > 0 ? `+${num}` : `${num}`
}

onLoad((query) => { if (query?.date) selectedDate.value = query.date })
onShow(() => {
  if (ensureLoggedIn()) { loadPlan() }
})
</script>

<style scoped>
/* 白绿清爽主题变量 */
.page-modern {
  --app-bg: #ffffff; /* 纯白大背景 */
  --card-bg: #ffffff;
  --primary: #059669; /* 鲜明现代的核心绿色 */
  --primary-dark: #047857; /* 深绿色，用于悬浮或强调 */
  --primary-light: #d1fae5; /* 极浅绿，用于辅助背景 */
  --text-main: #111827; /* 深灰/黑文本 */
  --text-sub: #6b7280; /* 中灰色辅助文本 */
  --border-light: #e5e7eb; /* 浅色分割线和边框 */
  --warn-color: #d97706; /* 橙色用于警告替代 */
  --warn-bg: #fef3c7; /* 浅橙色背景 */
  
  min-height: 100vh;
  background-color: var(--app-bg);
  padding: 24rpx;
}

/* --- 1. AI 智能规划区 --- */
.ai-planner-card {
  background: var(--card-bg);
  border: 1rpx solid var(--border-light);
  border-radius: 32rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.03);
}

.planner-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
}

.header-left {
  display: flex;
  align-items: center;
}

.emoji-icon {
  font-size: 40rpx;
  margin-right: 16rpx;
}

.card-title {
  display: block;
  font-size: 32rpx;
  font-weight: 800;
  color: var(--text-main);
}

.card-subtitle {
  display: block;
  font-size: 24rpx;
  color: var(--text-sub);
  margin-top: 4rpx;
}

.date-selector {
  background: var(--primary-light);
  padding: 10rpx 24rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
}

.date-text {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--primary);
  margin-right: 8rpx;
}

.arrow-down {
  font-size: 24rpx;
  color: var(--primary);
}

/* 对话框式输入 */
.prompt-box {
  background: #ffffff;
  border: 2rpx solid var(--border-light);
  border-radius: 20rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
  transition: all 0.2s;
}

.prompt-box:focus-within {
  border-color: var(--primary);
  box-shadow: 0 4rpx 16rpx rgba(5, 150, 105, 0.08);
}

.prompt-input {
  width: 100%;
  font-size: 28rpx;
  color: var(--text-main);
}

.prompt-placeholder {
  color: #9ca3af;
}

.ai-action-row {
  display: flex;
  gap: 16rpx;
}

.btn-ai-primary, .btn-ai-secondary {
  flex: 1;
  height: 80rpx;
  border-radius: 20rpx;
  font-size: 28rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
}

.btn-ai-primary {
  background: var(--primary);
  color: #ffffff;
  box-shadow: 0 6rpx 16rpx rgba(5, 150, 105, 0.2);
}

.btn-ai-secondary {
  background: #ffffff;
  border: 1rpx solid var(--border-light);
  color: var(--text-main);
}

.week-slider {
  margin-top: 24rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid var(--border-light);
}

.week-scroll {
  width: 100%;
  white-space: nowrap;
}

.week-row {
  display: inline-flex;
  gap: 20rpx;
}

.day-bubble {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16rpx 20rpx;
  border-radius: 20rpx;
  background: #ffffff;
  border: 1rpx solid var(--border-light);
  transition: all 0.2s;
}

.day-bubble.active {
  background: var(--primary);
  border-color: var(--primary);
}

.bubble-date {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--text-sub);
  margin-bottom: 8rpx;
}

.day-bubble.active .bubble-date {
  color: #ffffff;
}

.bubble-dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: transparent;
}

.bubble-dot.has-plan {
  background: #9ca3af; /* 灰色圆点表示有计划 */
}
.day-bubble.active .bubble-dot.has-plan {
  background: #ffffff; /* 激活时反白 */
}

/* --- 2. 计划摘要仪表盘 --- */
.summary-dashboard {
  background: var(--card-bg);
  border: 1rpx solid var(--border-light);
  border-radius: 32rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.03);
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32rpx;
}

.dashboard-title {
  font-size: 32rpx;
  font-weight: 800;
  color: var(--text-main);
}

.status-badge {
  padding: 8rpx 20rpx;
  border: 1rpx solid var(--border-light);
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 700;
  background: #ffffff;
  color: var(--text-sub);
}
.status-badge.generated { background: var(--primary-light); color: var(--primary); border: none; }
.status-badge.ready { background: #f0fdf4; color: #166534; border: 1rpx solid #bbf7d0; }
.status-badge.applied { background: #ecfdf5; color: var(--primary-dark); border: 1rpx solid #a7f3d0; }

/* 网格仪表 */
.macro-grid {
  display: flex;
  justify-content: space-between;
  background: #ffffff;
  border: 1rpx solid var(--border-light);
  border-radius: 24rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
}

.macro-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.macro-item.highlight .macro-value {
  color: var(--primary);
  font-size: 40rpx;
}

.macro-value {
  font-size: 36rpx;
  font-weight: 800;
  color: var(--text-main);
  margin-bottom: 8rpx;
}

.macro-label {
  font-size: 22rpx;
  color: var(--text-sub);
}

.target-compare-bar {
  display: flex;
  gap: 24rpx;
  margin-bottom: 24rpx;
}

.compare-item {
  flex: 1;
  background: #ffffff;
  border: 1rpx solid var(--border-light);
  padding: 16rpx 24rpx;
  border-radius: 16rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.c-label { font-size: 24rpx; color: var(--text-sub); }
.c-value { font-size: 28rpx; font-weight: 700; color: var(--text-main); }
.c-value.is-over { color: var(--warn-color); } 

/* AI 洞察 */
.ai-insight-card {
  background: #ffffff;
  border: 1rpx solid var(--primary-light);
  padding: 24rpx;
  border-radius: 24rpx;
  margin-bottom: 24rpx;
}

.insight-head {
  display: flex;
  align-items: center;
  margin-bottom: 12rpx;
}

.insight-icon { font-size: 32rpx; margin-right: 12rpx; }
.insight-title { font-size: 28rpx; font-weight: 700; color: var(--text-main); flex: 1; }
.insight-mode { font-size: 22rpx; color: var(--primary); font-weight: 600; background: var(--primary-light); padding: 4rpx 12rpx; border-radius: 8rpx;}

.insight-content {
  font-size: 26rpx;
  color: #374151;
  line-height: 1.6;
}

/* Tips */
.tips-container {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.tip-group {
  background: #ffffff;
  border: 1rpx solid var(--border-light);
  border-radius: 20rpx;
  padding: 20rpx;
}

.tip-group.warning { background: var(--warn-bg); border-color: #fde68a; }

.tip-row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 12rpx;
}
.tip-row:last-child { margin-bottom: 0; }

.tip-emoji { margin-right: 12rpx; font-size: 28rpx; line-height: 1.4; }
.tip-text { font-size: 26rpx; color: #374151; line-height: 1.5; flex: 1; }
.warning .tip-text { color: #92400e; } 

/* --- 3. 执行清单区 --- */
.execution-panel {
  background: var(--card-bg);
  border: 1rpx solid var(--border-light);
  border-radius: 32rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.03);
}

.execution-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 32rpx;
}

.panel-main-title { font-size: 32rpx; font-weight: 800; color: var(--text-main); }
.panel-sub-title { font-size: 26rpx; color: var(--text-sub); }

.empty-state {
  padding: 60rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #ffffff;
}
.empty-icon { font-size: 80rpx; margin-bottom: 16rpx; }
.empty-text { font-size: 26rpx; color: #9ca3af; }

.meal-group {
  margin-bottom: 32rpx;
}

.meal-group-title {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 16rpx;
}

.meal-name { font-size: 28rpx; font-weight: 800; color: var(--text-main); }
.meal-cal { font-size: 24rpx; font-weight: 600; color: var(--text-sub); }

.food-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.food-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 24rpx;
  background: #ffffff;
  border: 1rpx solid var(--border-light);
  border-radius: 20rpx;
}

.food-info { flex: 1; min-width: 0; }
.food-name { display: block; font-size: 28rpx; font-weight: 700; color: var(--text-main); margin-bottom: 6rpx; }
.food-desc { display: block; font-size: 24rpx; color: var(--text-sub); }
.food-note { color: var(--primary); }

.food-remove {
  width: 50rpx;
  height: 50rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.remove-icon { font-size: 36rpx; color: #d1d5db; }

.action-dock {
  display: flex;
  gap: 16rpx;
  margin-top: 32rpx;
  padding-top: 32rpx;
  border-top: 1rpx solid var(--border-light);
}

.btn-save, .btn-apply {
  flex: 1;
  height: 88rpx;
  border-radius: 24rpx;
  font-size: 28rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.btn-save { background: #ffffff; border: 1rpx solid var(--border-light); color: var(--text-main); }
.btn-apply { background: var(--primary); color: #ffffff; box-shadow: 0 4rpx 12rpx rgba(5, 150, 105, 0.2); }

/* --- 4. 手动微调区 --- */
.manual-edit-panel {
  background: var(--card-bg);
  border: 1rpx solid var(--border-light);
  border-radius: 32rpx;
  padding: 32rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.03);
}

.edit-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.expand-icon {
  font-size: 40rpx;
  color: var(--text-sub);
  transition: transform 0.3s;
  transform: rotate(90deg);
}
.expand-icon.is-open { transform: rotate(-90deg); }

.edit-body {
  max-height: 0;
  overflow: hidden;
  opacity: 0;
  transition: all 0.3s ease;
}

.edit-body.is-expanded {
  max-height: 2000rpx;
  opacity: 1;
  margin-top: 32rpx;
  padding-top: 32rpx;
  border-top: 1rpx solid var(--border-light);
}

.custom-input, .custom-textarea, .picker-box {
  width: 100%;
  box-sizing: border-box;
  background: #ffffff;
  border: 1rpx solid var(--border-light);
  border-radius: 20rpx;
  padding: 24rpx;
  font-size: 28rpx;
  margin-bottom: 20rpx;
  color: var(--text-main);
}

.custom-textarea { min-height: 140rpx; }

.sub-label {
  display: block;
  font-size: 26rpx;
  font-weight: 700;
  color: var(--text-main);
  margin: 10rpx 0 20rpx;
}

.meal-tags {
  display: flex;
  gap: 16rpx;
  margin-bottom: 32rpx;
}

.meal-tag {
  padding: 14rpx 32rpx;
  border-radius: 999rpx;
  background: #ffffff;
  border: 1rpx solid var(--border-light);
  font-size: 26rpx;
  color: var(--text-sub);
  font-weight: 600;
  transition: all 0.2s;
}

.meal-tag.active {
  background: var(--primary);
  border-color: var(--primary);
  color: #ffffff;
}

.search-add-box {
  background: #ffffff;
  border: 1rpx dashed var(--border-light);
  border-radius: 24rpx;
  padding: 24rpx;
}

.search-bar {
  display: flex;
  gap: 12rpx;
  margin-bottom: 20rpx;
}

.search-input {
  flex: 1;
  background: #ffffff;
  border: 1rpx solid var(--border-light);
  border-radius: 16rpx;
  padding: 0 20rpx;
  font-size: 26rpx;
  color: var(--text-main);
}

.btn-search {
  background: var(--primary);
  color: #ffffff;
  font-size: 26rpx;
  margin: 0;
  border-radius: 16rpx;
}

.picker-box {
  background: #ffffff;
  border: 1rpx solid var(--border-light);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.picker-text { color: var(--text-sub); }
.picker-text.has-val { color: var(--text-main); font-weight: 600; }
.picker-arrow { color: #9ca3af; }

.input-grid {
  display: flex;
  gap: 16rpx;
}
.custom-input.half { flex: 1; background: #ffffff; border: 1rpx solid var(--border-light); }

.btn-add-food {
  width: 100%;
  height: 80rpx;
  line-height: 80rpx;
  background: var(--primary-light);
  color: var(--primary);
  font-size: 28rpx;
  font-weight: 700;
  border-radius: 20rpx;
  margin-top: 10rpx;
}
.btn-add-food::after { display: none; }

.safe-area-bottom {
  height: calc(40rpx + env(safe-area-inset-bottom));
}
</style>