<template>
  <view class="page">
    <app-page-header
      title="饮食计划"
      subtitle="提前安排一天怎么吃，保存后还能一键写入实际饮食记录"
      fallback-url="/pages/meals/index"
    >
      <template #right>
        <picker mode="date" :value="selectedDate" @change="handleDateChange">
          <view class="date-pill">{{ selectedDate }}</view>
        </picker>
      </template>
    </app-page-header>

    <view class="week-card">
      <text class="section-title">本周安排</text>
      <scroll-view scroll-x class="week-scroll" show-scrollbar="false">
        <view class="week-row">
          <view
            v-for="item in weekPlans"
            :key="item.planDate"
            class="day-chip"
            :class="{ active: item.planDate === selectedDate }"
            @click="selectPlanDate(item.planDate)"
          >
            <text class="day-chip-date">{{ shortDate(item.planDate) }}</text>
            <text class="day-chip-meta">{{ item.hasPlan ? `${item.itemCount} 项` : '未安排' }}</text>
          </view>
        </view>
      </scroll-view>
    </view>

    <view class="summary-card">
      <view class="summary-top">
        <view>
          <text class="summary-title">{{ plan.title || '今天的饮食计划' }}</text>
          <text class="summary-desc">{{ plan.notes || '先把每餐吃什么定下来，执行会轻松很多。' }}</text>
        </view>
        <view class="summary-badge">{{ plan.status || 'DRAFT' }}</view>
      </view>

      <view class="summary-grid">
        <view class="summary-item">
          <text class="summary-label">总热量</text>
          <text class="summary-value">{{ formatNumber(plan.totalCalories) }}</text>
          <text class="summary-unit">kcal</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">蛋白质</text>
          <text class="summary-value">{{ formatNumber(plan.totalProtein, 1) }}</text>
          <text class="summary-unit">g</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">脂肪</text>
          <text class="summary-value">{{ formatNumber(plan.totalFat, 1) }}</text>
          <text class="summary-unit">g</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">碳水</text>
          <text class="summary-value">{{ formatNumber(plan.totalCarbohydrate, 1) }}</text>
          <text class="summary-unit">g</text>
        </view>
      </view>
    </view>

    <view class="panel">
      <view class="section-head">
        <view>
          <text class="section-title">编辑当天计划</text>
          <text class="section-desc">先选日期，再补齐每餐食物和数量。</text>
        </view>
      </view>

      <input v-model="planForm.title" class="field" placeholder="计划标题，例如训练日高蛋白" />
      <textarea v-model="planForm.notes" class="textarea-field" maxlength="200" placeholder="补充说明，例如早餐少油、晚餐主食减量"></textarea>

      <view class="meal-type-row">
        <view
          v-for="item in mealTypeOptions"
          :key="item.value"
          class="meal-type-chip"
          :class="{ active: mealType === item.value }"
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
        <view class="field">{{ selectedFoodLabel }}</view>
      </picker>

      <view v-if="selectedFood" class="food-preview-card">
        <text class="food-preview-title">{{ selectedFood.name }}</text>
        <text class="food-preview-meta">
          {{ selectedFood.category || '未分类' }} · {{ selectedFood.unit || '100g' }} · {{ formatNumber(selectedFood.calories) }} kcal
        </text>
      </view>

      <view class="form-grid">
        <input v-model="quantity" class="field" type="digit" placeholder="数量(g)" />
        <input v-model="itemNote" class="field" placeholder="备注，例如加餐、训练后" />
      </view>

      <view class="button-row">
        <button class="secondary-button" @click="addPlanItem">加入计划</button>
        <button class="ghost-button" @click="resetEditor">清空输入</button>
      </view>
    </view>

    <view class="panel">
      <view class="section-head">
        <view>
          <text class="section-title">计划条目</text>
          <text class="section-desc">这里展示当前日期会被保存的完整内容。</text>
        </view>
      </view>

      <view v-if="!planItems.length" class="empty-card">
        <text class="empty-title">这一天还没有安排食物</text>
        <text class="empty-desc">上面选择食物后点击“加入计划”，就会出现在这里。</text>
      </view>

      <view v-for="(item, index) in planItems" :key="`${item.foodId}-${index}`" class="plan-item-card">
        <view class="plan-item-main">
          <view class="plan-item-top">
            <text class="plan-item-name">{{ item.foodName }}</text>
            <view class="plan-item-badge">{{ mealTypeLabel(item.mealType) }}</view>
          </view>
          <text class="plan-item-meta">
            {{ formatNumber(item.quantity) }} g · {{ formatNumber(item.calories) }} kcal{{ item.note ? ` · ${item.note}` : '' }}
          </text>
        </view>
        <button class="danger-button small" @click="removePlanItem(index)">移除</button>
      </view>

      <view class="button-row">
        <button class="primary-button" @click="savePlan">保存计划</button>
        <button class="secondary-button" @click="applyPlan">一键应用到记录</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { ensureLoggedIn, formatToday } from '@/utils/auth.js'
import { formatNumber, mealTypeLabel } from '@/utils/format.js'

const mealTypeOptions = [
  { label: '早餐', value: 'BREAKFAST' },
  { label: '午餐', value: 'LUNCH' },
  { label: '晚餐', value: 'DINNER' },
  { label: '加餐', value: 'SNACK' }
]

const selectedDate = ref(formatToday())
const weekPlans = ref([])
const plan = ref(createEmptyPlan())
const planForm = ref({
  title: '',
  notes: ''
})
const planItems = ref([])

const foods = ref([])
const selectedFoodIndex = ref(0)
const foodKeyword = ref('')
const mealType = ref('BREAKFAST')
const quantity = ref('100')
const itemNote = ref('')

const selectedFood = computed(() => foods.value[selectedFoodIndex.value] || null)

const selectedFoodLabel = computed(() => {
  if (!foods.value.length) {
    return '先搜索食物，或去食物库新增'
  }
  const food = foods.value[selectedFoodIndex.value]
  return food ? `${food.name} · ${food.unit || '100g'}` : '请选择食物'
})

function createEmptyPlan() {
  return {
    title: '',
    notes: '',
    status: 'DRAFT',
    totalCalories: 0,
    totalProtein: 0,
    totalFat: 0,
    totalCarbohydrate: 0,
    items: []
  }
}

function normalizePlan(payload) {
  const next = payload || createEmptyPlan()
  const items = Array.isArray(next.items) ? next.items : []
  plan.value = {
    ...createEmptyPlan(),
    ...next,
    items
  }
  planForm.value = {
    title: next.title || '',
    notes: next.notes || ''
  }
  planItems.value = items.map((item, index) => ({
    ...item,
    sortOrder: item.sortOrder ?? index
  }))
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
    foods.value = Array.isArray(response?.records) ? response.records : []
    selectedFoodIndex.value = 0
  } catch (error) {
    console.log('load foods for plan failed', error)
  }
}

async function loadPlan() {
  if (!ensureLoggedIn()) {
    return
  }

  try {
    const [dailyPlan, weekly] = await Promise.all([
      request.get('/meals/plans/daily', { planDate: selectedDate.value }),
      request.get('/meals/plans/week', { anchorDate: selectedDate.value })
    ])
    normalizePlan(dailyPlan)
    weekPlans.value = Array.isArray(weekly) ? weekly : []
  } catch (error) {
    console.log('load meal plan failed', error)
  }
}

function handleDateChange(event) {
  selectPlanDate(event.detail.value)
}

function selectPlanDate(value) {
  selectedDate.value = value
  loadPlan()
}

function handleFoodSelect(event) {
  selectedFoodIndex.value = Number(event.detail.value || 0)
}

function resetEditor() {
  quantity.value = '100'
  itemNote.value = ''
  mealType.value = 'BREAKFAST'
}

function addPlanItem() {
  if (!selectedFood.value) {
    uni.showToast({
      title: '请先选择食物',
      icon: 'none'
    })
    return
  }

  const numericQuantity = Number(quantity.value)
  if (Number.isNaN(numericQuantity) || numericQuantity <= 0) {
    uni.showToast({
      title: '请输入正确数量',
      icon: 'none'
    })
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
}

function removePlanItem(index) {
  planItems.value.splice(index, 1)
}

async function savePlan() {
  if (!ensureLoggedIn()) {
    return
  }

  try {
    const response = await persistPlan()
    normalizePlan(response)
    loadPlan()
  } catch (error) {
    console.log('save meal plan failed', error)
  }
}

async function applyPlan() {
  if (!ensureLoggedIn()) {
    return
  }

  try {
    await persistPlan()
    await request.post('/meals/plans/daily/apply', {
      planDate: selectedDate.value
    })
    uni.showModal({
      title: '应用成功',
      content: '计划已经写入饮食记录，是否现在去查看？',
      success: (result) => {
        if (result.confirm) {
          uni.navigateTo({
            url: '/pages/meals/index'
          })
        }
      }
    })
    loadPlan()
  } catch (error) {
    console.log('apply meal plan failed', error)
  }
}

function persistPlan() {
  return request.put('/meals/plans/daily', {
    planDate: selectedDate.value,
    title: planForm.value.title || null,
    notes: planForm.value.notes || null,
    items: planItems.value.map((item, index) => ({
      foodId: item.foodId,
      mealType: item.mealType,
      quantity: item.quantity,
      note: item.note || null,
      sortOrder: index
    }))
  })
}

function shortDate(value) {
  if (!value) {
    return '--'
  }
  const [, month, day] = String(value).split('-')
  return `${month}/${day}`
}

onLoad((query) => {
  if (query?.date) {
    selectedDate.value = query.date
  }
})

onShow(() => {
  if (!ensureLoggedIn()) {
    return
  }
  loadFoods()
  loadPlan()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 32rpx 28rpx 80rpx;
}

.week-card,
.summary-card,
.panel {
  margin-top: 24rpx;
  padding: 28rpx;
  border-radius: 34rpx;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: var(--nm-shadow);
}

.date-pill,
.summary-badge,
.plan-item-badge,
.meal-type-chip {
  padding: 14rpx 20rpx;
  border-radius: 999rpx;
}

.date-pill {
  background: #172033;
  font-size: 24rpx;
  color: #ffffff;
}

.section-head,
.summary-top,
.search-row,
.button-row,
.plan-item-card,
.plan-item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.section-title,
.summary-title {
  display: block;
  font-size: 36rpx;
  font-weight: 800;
  color: #111827;
}

.section-desc,
.summary-desc,
.summary-label,
.summary-unit,
.food-preview-meta,
.empty-desc,
.plan-item-meta {
  font-size: 24rpx;
  color: #64748b;
}

.summary-desc {
  display: block;
  margin-top: 10rpx;
  line-height: 1.7;
}

.summary-badge {
  background: rgba(14, 165, 109, 0.12);
  color: var(--nm-primary);
  font-size: 24rpx;
  font-weight: 700;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
  margin-top: 22rpx;
}

.summary-item {
  padding: 22rpx;
  border-radius: 24rpx;
  background: #f8fafc;
}

.summary-value {
  display: block;
  margin-top: 10rpx;
  font-size: 38rpx;
  font-weight: 800;
  color: #111827;
}

.week-scroll {
  margin-top: 18rpx;
  white-space: nowrap;
}

.week-row {
  display: inline-flex;
  gap: 14rpx;
}

.day-chip {
  min-width: 150rpx;
  padding: 20rpx;
  border-radius: 24rpx;
  background: #f5f4ef;
}

.day-chip.active {
  background: var(--nm-primary);
}

.day-chip-date,
.day-chip-meta,
.meal-type-text,
.plan-item-name,
.food-preview-title,
.empty-title {
  display: block;
}

.day-chip-date {
  font-size: 28rpx;
  font-weight: 800;
  color: #111827;
}

.day-chip-meta {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #64748b;
}

.day-chip.active .day-chip-date,
.day-chip.active .day-chip-meta {
  color: #ffffff;
}

.field,
.textarea-field {
  width: 100%;
  box-sizing: border-box;
  margin-top: 16rpx;
  padding: 22rpx 24rpx;
  border-radius: 24rpx;
  background: #f5f4ef;
  font-size: 28rpx;
  color: #111827;
}

.textarea-field {
  min-height: 150rpx;
}

.meal-type-row,
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14rpx;
  margin-top: 18rpx;
}

.meal-type-chip {
  background: #edf6f1;
  text-align: center;
}

.meal-type-chip.active {
  background: var(--nm-primary);
}

.meal-type-text {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--nm-primary);
}

.meal-type-chip.active .meal-type-text {
  color: #ffffff;
}

.search-field {
  flex: 1;
  margin-top: 0;
}

.search-button,
.primary-button,
.secondary-button,
.ghost-button,
.danger-button {
  height: 84rpx;
  border-radius: 24rpx;
  font-size: 26rpx;
  font-weight: 700;
}

.search-button,
.primary-button {
  background: var(--nm-primary-dark);
  color: #ffffff;
}

.secondary-button {
  background: #dbeafe;
  color: #1d4ed8;
}

.ghost-button {
  background: #f3f4f6;
  color: #111827;
}

.danger-button {
  background: #fee2e2;
  color: #991b1b;
}

.danger-button.small {
  min-width: 120rpx;
  height: 72rpx;
}

.button-row {
  margin-top: 20rpx;
}

.button-row button {
  flex: 1;
}

.food-preview-card,
.empty-card,
.plan-item-card {
  margin-top: 18rpx;
  padding: 22rpx;
  border-radius: 24rpx;
  background: #f8fafc;
}

.food-preview-title,
.plan-item-name,
.empty-title {
  font-size: 30rpx;
  font-weight: 800;
  color: #111827;
}

.plan-item-main {
  flex: 1;
  min-width: 0;
}

.plan-item-badge {
  background: rgba(14, 165, 109, 0.12);
  font-size: 22rpx;
  color: var(--nm-primary);
}
</style>
