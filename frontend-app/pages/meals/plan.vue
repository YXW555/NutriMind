<template>
  <view class="page">
    <app-page-header
      title="饮食计划"
      subtitle="先生成建议，再查看当天执行内容，最后按需要微调。"
      fallback-url="/pages/meals/index"
    />

    <view class="planner-card">
      <view class="section-head planner-head">
        <view>
          <text class="section-title">智能规划</text>
          <text class="section-desc">选择日期和偏好后，让智能体先帮你生成当天或一周饮食方案。</text>
        </view>
        <picker mode="date" :value="selectedDate" @change="handleDateChange">
          <view class="date-pill">{{ selectedDate }}</view>
        </picker>
      </view>

      <view class="planner-intro">
        <text class="planner-intro-badge">AI 饮食规划</text>
        <text class="planner-intro-text">更适合比赛演示的流程是“先生成方案，再展示你如何落地执行”。</text>
      </view>

      <input
        v-model="generatePreference"
        class="field"
        placeholder="输入本次偏好，例如：减脂训练日、晚餐清淡、控糖优先"
      />

      <view class="button-row planner-actions">
        <button class="primary-button" :loading="generatingDaily" @click="generateDailyPlan">AI 生成当天计划</button>
        <button class="secondary-button" :loading="generatingWeek" @click="generateWeekPlan">AI 生成本周草案</button>
      </view>

      <view class="week-strip">
        <text class="section-mini-title">本周安排</text>
        <text class="section-mini-desc">点击任意日期，快速切换查看当天计划。</text>
      </view>

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
            <text class="day-chip-meta">{{ weekPlanMeta(item) }}</text>
          </view>
        </view>
      </scroll-view>
    </view>

    <view class="summary-card">
      <view class="summary-top">
        <view>
          <text class="summary-title">{{ plan.title || '今天的饮食计划' }}</text>
          <text class="summary-desc">{{ plan.notes || '先把每一餐安排清楚，再决定是否手动微调。' }}</text>
        </view>
        <view class="summary-badge">{{ planStatusLabel(plan.status) }}</view>
      </view>

      <view class="hero-note-card" :class="{ empty: !hasPlanInsights }">
        <view class="hero-note-top">
          <text class="agent-note-title">智能规划摘要</text>
          <text v-if="plan.generationMode" class="agent-note-mode">{{ generationModeLabel(plan.generationMode) }}</text>
        </view>
        <text class="agent-note-text">
          {{ hasPlanInsights ? plan.summary : '还没有生成摘要。你可以先点击上方按钮，让 AI 帮你出一版饮食计划。' }}
        </text>
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

      <view v-if="showTargetStrip" class="target-strip">
        <view v-if="plan.targetCalories" class="target-item">
          <text class="target-label">目标热量</text>
          <text class="target-value">{{ formatNumber(plan.targetCalories) }} kcal</text>
        </view>
        <view v-if="plan.targetProtein" class="target-item">
          <text class="target-label">目标蛋白</text>
          <text class="target-value">{{ formatNumber(plan.targetProtein, 1) }} g</text>
        </view>
        <view v-if="plan.calorieGap !== null && plan.calorieGap !== undefined" class="target-item">
          <text class="target-label">热量偏差</text>
          <text class="target-value">{{ signedNumber(plan.calorieGap) }} kcal</text>
        </view>
        <view v-if="plan.proteinGap !== null && plan.proteinGap !== undefined" class="target-item">
          <text class="target-label">蛋白偏差</text>
          <text class="target-value">{{ signedNumber(plan.proteinGap, 1) }} g</text>
        </view>
      </view>

      <view class="info-flow">
        <view v-if="plan.tips.length" class="info-card">
          <text class="bullet-title">执行建议</text>
          <view v-for="(tip, index) in plan.tips" :key="`tip-${index}`" class="bullet-item">
            <text class="bullet-index">{{ index + 1 }}</text>
            <text class="bullet-text">{{ tip }}</text>
          </view>
        </view>

        <view v-if="plan.warnings.length" class="info-card warning-card">
          <text class="bullet-title">提醒</text>
          <view v-for="(warning, index) in plan.warnings" :key="`warning-${index}`" class="bullet-item">
            <text class="bullet-index warning">{{ index + 1 }}</text>
            <text class="bullet-text">{{ warning }}</text>
          </view>
        </view>

        <view v-if="plan.references.length" class="info-card reference-card">
          <text class="bullet-title">规划依据</text>
          <text v-for="(reference, index) in plan.references" :key="`reference-${index}`" class="reference-text">
            {{ index + 1 }}. {{ reference }}
          </text>
        </view>
      </view>
    </view>

    <view class="panel">
      <view class="section-head">
        <view>
          <text class="section-title">当天执行</text>
          <text class="section-desc">按餐次查看执行内容，确认无误后可以保存草稿或一键应用到饮食记录。</text>
        </view>
        <view class="section-tag">{{ planItems.length }} 项</view>
      </view>

      <view v-if="!planItems.length" class="empty-card">
        <text class="empty-title">这一天还没有安排食物</text>
        <text class="empty-desc">先用上面的智能生成，或者展开下方手动补充区域添加食物。</text>
      </view>

      <view v-for="section in groupedPlanItems" :key="section.value" class="meal-section-card">
        <view class="meal-section-head">
          <view>
            <text class="meal-section-title">{{ section.label }}</text>
            <text class="meal-section-meta">{{ section.items.length }} 项 · {{ formatNumber(section.totalCalories) }} kcal</text>
          </view>
          <view class="plan-item-badge">{{ section.label }}</view>
        </view>

        <view class="meal-item-list">
          <view
            v-for="(item, index) in section.items"
            :key="`${item.foodId}-${section.value}-${index}`"
            class="plan-item-card"
          >
            <view class="plan-item-main">
              <text class="plan-item-name">{{ item.foodName }}</text>
              <text class="plan-item-meta">
                {{ formatNumber(item.quantity) }} g · {{ formatNumber(item.calories) }} kcal{{ item.note ? ` · ${item.note}` : '' }}
              </text>
            </view>
            <button class="danger-button small" @click="removePlanItem(item.originalIndex)">移除</button>
          </view>
        </view>
      </view>

      <view class="button-row">
        <button class="secondary-button" @click="savePlan">保存计划</button>
        <button class="primary-button" @click="applyPlan">一键应用到记录</button>
      </view>
    </view>

    <view class="panel editor-panel">
      <view class="section-head">
        <view>
          <text class="section-title">手动补充</text>
          <text class="section-desc">当你想修改 AI 方案时，再展开编辑，避免一进来信息太满。</text>
        </view>
        <button class="ghost-button mini" @click="editorExpanded = !editorExpanded">
          {{ editorExpanded ? '收起' : '展开编辑' }}
        </button>
      </view>

      <view v-if="editorExpanded">
        <view class="editor-note">
          <text class="editor-note-title">手动微调区</text>
          <text class="editor-note-text">这里适合修改标题、补充说明，或补上一些 AI 没安排到的食物。</text>
        </view>

        <input v-model="planForm.title" class="field" placeholder="计划标题，例如：训练日高蛋白计划" />
        <textarea
          v-model="planForm.notes"
          class="textarea-field"
          maxlength="200"
          placeholder="补充说明，例如：早餐少油、晚餐主食减量"
        ></textarea>

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
          <view class="field picker-field">{{ selectedFoodLabel }}</view>
        </picker>

        <view v-if="selectedFood" class="food-preview-card">
          <text class="food-preview-title">{{ selectedFood.name }}</text>
          <text class="food-preview-meta">
            {{ selectedFood.category || '未分类' }} · {{ selectedFood.unit || '100克' }} · {{ formatNumber(selectedFood.calories) }} kcal
          </text>
        </view>

        <view class="form-grid">
          <input v-model="quantity" class="field compact-field" type="digit" placeholder="数量(g)" />
          <input v-model="itemNote" class="field compact-field" placeholder="备注，例如：训练后、加餐" />
        </view>

        <view class="button-row">
          <button class="secondary-button" @click="addPlanItem">加入计划</button>
          <button class="ghost-button" @click="resetEditor">清空输入</button>
        </view>
      </view>
    </view>
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
const planForm = ref({
  title: '',
  notes: ''
})
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
  if (!foods.value.length) {
    return '先搜索食物，或者去食物库新增'
  }
  const food = foods.value[selectedFoodIndex.value]
  return food ? `${food.name} · ${food.unit || '100克'}` : '请选择食物'
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
    targetCalories: null,
    targetProtein: null,
    calorieGap: null,
    proteinGap: null,
    generationMode: '',
    summary: '',
    tips: [],
    warnings: [],
    references: [],
    items: []
  }
}

function normalizePlan(payload) {
  const next = payload || createEmptyPlan()
  const items = Array.isArray(next.items) ? next.items : []
  plan.value = {
    ...createEmptyPlan(),
    ...next,
    tips: Array.isArray(next.tips) ? next.tips : [],
    warnings: Array.isArray(next.warnings) ? next.warnings : [],
    references: Array.isArray(next.references) ? next.references : [],
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
  editorExpanded.value = !items.length
}

function applyGeneratedPlan(payload) {
  normalizePlan({
    status: 'GENERATED',
    ...payload
  })
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

async function generateDailyPlan() {
  if (!ensureLoggedIn()) {
    return
  }

  generatingDaily.value = true
  try {
    const response = await request.post('/meals/plans/generate/daily', {
      planDate: selectedDate.value,
      preference: generatePreference.value || null,
      saveDraft: false
    })
    applyGeneratedPlan(response)
    uni.showToast({
      title: '当天计划已生成',
      icon: 'success'
    })
  } catch (error) {
    console.log('generate daily plan failed', error)
  } finally {
    generatingDaily.value = false
  }
}

async function generateWeekPlan() {
  if (!ensureLoggedIn()) {
    return
  }

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
    if (currentDay) {
      applyGeneratedPlan({
        ...currentDay,
        status: 'READY'
      })
    }
    uni.showToast({
      title: '本周草案已生成',
      icon: 'success'
    })
  } catch (error) {
    console.log('generate week plan failed', error)
  } finally {
    generatingWeek.value = false
  }
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
    await loadPlan()
    uni.showToast({
      title: '计划已保存',
      icon: 'success'
    })
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
      content: '计划已经写入饮食记录，是否现在前往查看？',
      success: (result) => {
        if (result.confirm) {
          uni.navigateTo({
            url: '/pages/meals/index'
          })
        }
      }
    })
    await loadPlan()
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

function weekPlanMeta(item) {
  if (!item?.hasPlan) {
    return '未安排'
  }
  return `${item.itemCount || 0} 项`
}

function generationModeLabel(value) {
  if (value === 'AI_AGENT') {
    return 'AI 智能生成'
  }
  if (value === 'RULE_BASED') {
    return '规则兜底生成'
  }
  if (value === 'MIXED') {
    return '混合生成'
  }
  return value || '智能生成'
}

function planStatusLabel(value) {
  const map = {
    DRAFT: '草稿',
    GENERATED: '已生成',
    READY: '已就绪',
    APPLIED: '已应用'
  }
  return map[value] || value || '草稿'
}

function signedNumber(value, digits = 0) {
  const numeric = Number(value || 0)
  if (!Number.isFinite(numeric)) {
    return '0'
  }
  const fixed = numeric.toFixed(digits)
  return numeric > 0 ? `+${fixed}` : fixed
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
  padding: 32rpx 28rpx calc(96rpx + env(safe-area-inset-bottom));
}

.planner-card,
.summary-card,
.panel {
  margin-top: 24rpx;
  padding: 28rpx;
  border-radius: 30rpx;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: var(--nm-shadow);
}

.planner-card {
  background: linear-gradient(160deg, #f4faf5 0%, #fcfffd 100%);
  border: 1rpx solid rgba(107, 162, 123, 0.14);
}

.summary-card {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, #fdfefd 100%);
}

.section-head,
.summary-top,
.hero-note-top,
.search-row,
.button-row,
.meal-section-head,
.plan-item-card,
.planner-intro,
.week-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.planner-head {
  align-items: flex-start;
}

.section-title,
.summary-title {
  display: block;
  font-size: 36rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.section-desc,
.summary-desc,
.summary-label,
.summary-unit,
.food-preview-meta,
.empty-desc,
.plan-item-meta,
.meal-section-meta,
.section-mini-desc {
  font-size: 24rpx;
  color: var(--nm-muted);
}

.section-desc,
.summary-desc {
  display: block;
  margin-top: 8rpx;
  line-height: 1.7;
}

.date-pill,
.summary-badge,
.plan-item-badge,
.meal-type-chip,
.section-tag {
  padding: 14rpx 20rpx;
  border-radius: 999rpx;
}

.date-pill {
  background: var(--nm-primary-dark);
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 700;
}

.planner-intro {
  margin-top: 20rpx;
  align-items: flex-start;
  padding: 20rpx 22rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.76);
  border: 1rpx solid rgba(126, 168, 199, 0.18);
}

.planner-intro-badge {
  flex-shrink: 0;
  padding: 10rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(67, 113, 90, 0.1);
  font-size: 22rpx;
  font-weight: 700;
  color: var(--nm-primary-dark);
}

.planner-intro-text {
  flex: 1;
  font-size: 24rpx;
  line-height: 1.7;
  color: #4f6658;
}

.field,
.textarea-field,
.picker-field {
  width: 100%;
  box-sizing: border-box;
  margin-top: 18rpx;
  padding: 22rpx 24rpx;
  border-radius: 22rpx;
  background: #f7f6f1;
  border: 1rpx solid rgba(82, 117, 92, 0.08);
  font-size: 28rpx;
  color: var(--nm-text);
}

.textarea-field {
  min-height: 150rpx;
}

.compact-field {
  margin-top: 0;
}

.planner-actions {
  margin-top: 22rpx;
}

.week-strip {
  margin-top: 26rpx;
  align-items: flex-end;
}

.section-mini-title {
  display: block;
  font-size: 28rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.week-scroll {
  margin-top: 14rpx;
  white-space: nowrap;
}

.week-row {
  display: inline-flex;
  gap: 14rpx;
  padding-bottom: 6rpx;
}

.day-chip {
  min-width: 156rpx;
  padding: 20rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.84);
  border: 1rpx solid rgba(107, 162, 123, 0.12);
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
  color: var(--nm-text);
}

.day-chip-meta {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--nm-muted);
}

.day-chip.active .day-chip-date,
.day-chip.active .day-chip-meta {
  color: #ffffff;
}

.summary-badge {
  background: rgba(107, 162, 123, 0.14);
  color: var(--nm-primary-dark);
  font-size: 24rpx;
  font-weight: 700;
}

.hero-note-card {
  margin-top: 22rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  background: linear-gradient(145deg, #eef6ff 0%, #f7fbf7 100%);
}

.hero-note-card.empty {
  background: #f6f8f5;
}

.agent-note-title,
.bullet-title {
  display: block;
  font-size: 26rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.agent-note-mode {
  display: inline-flex;
  padding: 10rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(67, 113, 90, 0.12);
  font-size: 22rpx;
  font-weight: 700;
  color: var(--nm-primary-dark);
}

.agent-note-text,
.bullet-text,
.reference-text,
.editor-note-text {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.75;
  color: #4f5d52;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
  margin-top: 22rpx;
}

.summary-item {
  padding: 22rpx;
  border-radius: 22rpx;
  background: #f8faf7;
}

.summary-value {
  display: block;
  margin-top: 10rpx;
  font-size: 38rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.target-strip {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14rpx;
  margin-top: 20rpx;
}

.target-item {
  padding: 18rpx 20rpx;
  border-radius: 22rpx;
  background: #f7f5ef;
}

.target-label {
  display: block;
  font-size: 22rpx;
  color: var(--nm-muted);
}

.target-value {
  display: block;
  margin-top: 8rpx;
  font-size: 28rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.info-flow {
  margin-top: 20rpx;
}

.info-card {
  margin-top: 14rpx;
  padding: 22rpx;
  border-radius: 22rpx;
  background: #fbfcfa;
  border: 1rpx solid rgba(82, 117, 92, 0.08);
}

.warning-card {
  background: #fffaf2;
}

.reference-card {
  background: #f7fafb;
}

.bullet-item {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  margin-top: 14rpx;
}

.bullet-index {
  width: 34rpx;
  height: 34rpx;
  border-radius: 999rpx;
  background: rgba(107, 162, 123, 0.16);
  text-align: center;
  line-height: 34rpx;
  font-size: 22rpx;
  font-weight: 800;
  color: var(--nm-primary-dark);
}

.bullet-index.warning {
  background: rgba(208, 167, 99, 0.18);
  color: #9b6a2b;
}

.section-tag,
.plan-item-badge {
  background: rgba(107, 162, 123, 0.14);
  font-size: 22rpx;
  font-weight: 700;
  color: var(--nm-primary-dark);
}

.empty-card {
  margin-top: 20rpx;
  padding: 26rpx;
  border-radius: 24rpx;
  background: #f8f7f2;
}

.empty-title {
  font-size: 30rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.meal-section-card {
  margin-top: 20rpx;
  padding: 22rpx;
  border-radius: 24rpx;
  background: #f9fbf8;
  border: 1rpx solid rgba(82, 117, 92, 0.08);
}

.meal-section-head {
  align-items: flex-start;
}

.meal-section-title {
  display: block;
  font-size: 30rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.meal-item-list {
  margin-top: 14rpx;
}

.plan-item-card {
  margin-top: 12rpx;
  padding: 18rpx 20rpx;
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.92);
}

.plan-item-main {
  flex: 1;
  min-width: 0;
}

.plan-item-name,
.food-preview-title {
  font-size: 30rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.food-preview-card,
.editor-note {
  margin-top: 18rpx;
  padding: 22rpx;
  border-radius: 22rpx;
  background: #f7faf6;
}

.editor-note-title {
  display: block;
  font-size: 26rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.editor-panel {
  background: rgba(255, 255, 255, 0.9);
}

.meal-type-row,
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14rpx;
  margin-top: 18rpx;
}

.meal-type-chip {
  text-align: center;
}

.meal-type-chip.warm {
  background: #fff2dc;
}

.meal-type-chip.green {
  background: #eaf7ef;
}

.meal-type-chip.blue {
  background: #edf4fb;
}

.meal-type-chip.gold {
  background: #f8f1dc;
}

.meal-type-chip.active {
  background: var(--nm-primary);
}

.meal-type-text {
  font-size: 24rpx;
  font-weight: 700;
  color: #596451;
}

.meal-type-chip.active .meal-type-text {
  color: #ffffff;
}

.search-row {
  margin-top: 18rpx;
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
  border-radius: 22rpx;
  font-size: 26rpx;
  font-weight: 700;
}

.primary-button,
.search-button {
  background: var(--nm-primary-dark);
  color: #ffffff;
}

.secondary-button {
  background: #e7eff6;
  color: #385b76;
}

.ghost-button {
  background: #f1f4ef;
  color: var(--nm-text);
}

.ghost-button.mini {
  min-width: 172rpx;
  height: 72rpx;
  font-size: 24rpx;
}

.danger-button {
  background: #fbe8e1;
  color: #a05639;
}

.danger-button.small {
  min-width: 120rpx;
  height: 72rpx;
}

.button-row {
  margin-top: 22rpx;
}

.button-row button {
  flex: 1;
}
</style>
