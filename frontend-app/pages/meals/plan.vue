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
            <text class="arrow-down">▼</text>
          </view>
        </picker>
      </view>

      <view class="prompt-box">
        <input
          v-model="generatePreference"
          class="prompt-input"
          placeholder="例如：减脂训练日、晚餐清淡、控糖优先"
          placeholder-class="prompt-placeholder"
        />
      </view>

      <view class="ai-action-row">
        <button class="btn-ai-primary" :loading="generatingDaily" @click="generateDailyPlan">生成今日计划</button>
        <button class="btn-ai-secondary" :loading="generatingWeek" @click="generateWeekPlan">生成本周草案</button>
      </view>

      <button class="btn-clear-plan" @click="clearCurrentPlan">清空当前计划</button>

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
          <text class="insight-icon">🍽️</text>
          <text class="insight-title">AI 洞察</text>
          <text class="insight-mode">{{ generationModeLabel(plan.generationMode) }}</text>
        </view>
        <text class="insight-content">{{ plan.summary }}</text>
        <view v-if="plan.references.length || hasExecutionSteps" class="insight-actions">
          <button v-if="plan.references.length" class="insight-action-btn ghost" @click="openReferenceModal">查看依据</button>
          <button v-if="hasExecutionSteps" class="insight-action-btn" @click="openProcessModal">查看多 Agent 过程</button>
        </view>
      </view>

      <view class="tips-container" v-if="plan.tips.length || plan.warnings.length">
        <view v-if="plan.tips.length" class="tip-group">
          <view v-for="(tip, index) in plan.tips" :key="`tip-${index}`" class="tip-row">
            <text class="tip-emoji">📌</text>
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
        <text class="empty-text">这一天还没有安排食物，让 AI 帮你生成吧</text>
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
                <text class="food-desc">{{ formatNumber(item.quantity) }}g 路 {{ formatNumber(item.calories) }}kcal <text v-if="item.note" class="food-note">| {{ item.note }}</text></text>
              </view>
              <view class="food-remove" @click="removePlanItem(item.originalIndex)">
                <text class="remove-icon">脳</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <view class="action-dock" v-if="planItems.length">
        <button class="btn-save" @click="savePlan">保存草稿</button>
        <button class="btn-apply" @click="applyPlan">保存为待执行计划</button>
      </view>
    </view>

    <view class="manual-edit-panel">
      <view class="edit-header" @click="editorExpanded = !editorExpanded">
        <view class="header-left">
          <text class="panel-main-title">手动补充与微调</text>
        </view>
        <text class="expand-icon" :class="{ 'is-open': editorExpanded }">⌄</text>
      </view>

      <view class="edit-body" :class="{ 'is-expanded': editorExpanded }">
        <input v-model="planForm.title" class="custom-input" placeholder="计划标题（选填）" />
        <textarea v-model="planForm.notes" class="custom-textarea" placeholder="补充说明（选填）" />

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
              <text class="picker-text" :class="{ 'has-val': foods.length > 0 }">{{ selectedFoodLabel }}</text>
              <text class="picker-arrow">▼</text>
            </view>
          </picker>

          <view class="input-grid">
            <input v-model="quantity" class="custom-input half" type="digit" placeholder="数量(g)" />
            <input v-model="itemNote" class="custom-input half" placeholder="备注（选填）" />
          </view>

          <button class="btn-add-food" @click="addPlanItem">+ 加入计划清单</button>
        </view>
      </view>
    </view>

    <view class="safe-area-bottom"></view>

    <view
      class="guide-modal-overlay"
      :class="{ 'is-visible': showReferenceModal || showProcessModal }"
      @tap="closeDetailModal"
    >
      <view
        v-if="showReferenceModal || showProcessModal"
        class="guide-modal-card plan-detail-modal"
        @tap.stop
      >
        <view class="guide-header">
          <view class="guide-header-main">
            <text class="guide-title">{{ showReferenceModal ? '生成依据' : '多 Agent 过程' }}</text>
            <text class="guide-subtitle">{{ showReferenceModal ? '这份计划为什么这样安排' : '本次计划的生成步骤与阶段输出' }}</text>
          </view>
          <view class="guide-close" @tap="closeDetailModal">
            <text class="guide-close-text">×</text>
          </view>
        </view>

        <scroll-view scroll-y class="guide-body plan-detail-body">
          <view class="plan-detail-scroll-content">
            <view v-if="showReferenceModal" class="detail-section">
              <view class="detail-hero-card">
                <text class="detail-hero-label">计划摘要</text>
                <text class="detail-hero-title">{{ plan.summary || '系统已根据你的目标、记录和知识依据生成本次饮食计划。' }}</text>
              </view>

              <text class="detail-section-title">GraphRAG 命中依据</text>
              <view class="detail-card-list">
                <view
                  v-for="(item, index) in referenceCards"
                  :key="`reference-${index}`"
                  class="detail-card-item reference-card"
                >
                  <view class="detail-card-head">
                    <text class="detail-card-index">{{ item.index }}</text>
                    <text class="detail-card-label">{{ item.title }}</text>
                  </view>
                  <text class="detail-card-text">{{ item.content }}</text>
                </view>
              </view>
            </view>

            <view v-if="showReferenceModal && plan.tips.length" class="detail-section">
              <text class="detail-section-title">关键执行建议</text>
              <view class="detail-card-list">
                <view
                  v-for="(item, index) in plan.tips"
                  :key="`tip-detail-${index}`"
                  class="detail-card-item"
                >
                  <text class="detail-card-text">{{ item }}</text>
                </view>
              </view>
            </view>

            <view v-if="showProcessModal && hasExecutionSteps" class="detail-section">
              <view class="process-summary-card">
                <text class="detail-hero-label">{{ executionSceneTitle }}</text>
                <text class="process-summary-title">{{ executionModeLabel }}</text>
                <text v-if="plan.executionDetail?.finalSummary" class="process-summary-text">{{ plan.executionDetail.finalSummary }}</text>
              </view>

              <text class="detail-section-title">协同步骤</text>
              <view class="detail-card-list">
                <view
                  v-for="step in executionSteps"
                  :key="`process-${step.stepOrder}`"
                  class="process-step-card"
                >
                  <view class="process-step-head">
                    <text class="process-step-order">#{{ step.stepOrder }}</text>
                    <text class="process-step-agent">{{ step.agentName || formatExecutionStage(step.stageName) }}</text>
                    <text class="process-step-stage">{{ formatExecutionStage(step.stageName) }}</text>
                  </view>
                  <text v-if="step.inputSummary" class="process-step-line">输入：{{ step.inputSummary }}</text>
                  <text v-if="step.outputSummary" class="process-step-line">输出：{{ step.outputSummary }}</text>
                  <text v-if="step.referenceSummary" class="process-step-line">依据：{{ step.referenceSummary }}</text>
                </view>
              </view>
            </view>
          </view>
        </scroll-view>

        <view class="guide-footer">
          <button class="btn-primary" @click="closeDetailModal">我知道了</button>
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
const planForm = ref({ title: '', notes: '' })
const planItems = ref([])
const generatePreference = ref('')
const generatingDaily = ref(false)
const generatingWeek = ref(false)
const editorExpanded = ref(false)
const showReferenceModal = ref(false)
const showProcessModal = ref(false)

const foods = ref([])
const selectedFoodIndex = ref(0)
const foodKeyword = ref('')
const mealType = ref('BREAKFAST')
const quantity = ref('100')
const itemNote = ref('')

const selectedFood = computed(() => foods.value[selectedFoodIndex.value] || null)
const hasPlanInsights = computed(() => Boolean(plan.value.summary || plan.value.generationMode))
const hasExecutionSteps = computed(() => Array.isArray(plan.value.executionDetail?.steps) && plan.value.executionDetail.steps.length > 0)
const executionSteps = computed(() => Array.isArray(plan.value.executionDetail?.steps) ? plan.value.executionDetail.steps : [])
const executionModeLabel = computed(() => generationModeLabel(plan.value.executionDetail?.generationMode || plan.value.generationMode))
const executionSceneTitle = computed(() => formatSceneType(plan.value.executionDetail?.sceneType))
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
  if (!foods.value.length) return '搜索出食物后在此选择'
  const food = foods.value[selectedFoodIndex.value]
  return food ? `${food.name} · ${food.unit || '100g'}` : '请选择食物'
})

const referenceCards = computed(() =>
  (Array.isArray(plan.value.references) ? plan.value.references : []).map((item, index) => ({
    index: String(index + 1).padStart(2, '0'),
    title: index === 0 ? '核心依据' : `补充依据 ${index}`,
    content: item
  }))
)

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
    executionDetail: null,
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
    executionDetail: next.executionDetail || null,
    items
  }
  planForm.value = { title: next.title || '', notes: next.notes || '' }
  planItems.value = items.map((item, index) => ({ ...item, sortOrder: item.sortOrder ?? index }))
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

function clearCurrentPlan() {
  uni.showModal({
    title: '清空计划',
    content: '确认清空当前日期的饮食计划吗？清空后可以重新生成新的计划。',
    success: async (result) => {
      if (!result.confirm) return
      try {
        if (plan.value.id) {
          await request.put('/meals/plans/daily', {
            planDate: selectedDate.value,
            title: null,
            notes: null,
            items: []
          })
        }
        normalizePlan({ ...createEmptyPlan(), planDate: selectedDate.value })
        planForm.value = { title: '', notes: '' }
        planItems.value = []
        await loadPlan()
        uni.showToast({ title: '已清空', icon: 'success' })
      } catch (error) {}
    }
  })
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
    uni.showToast({ title: '请输入正确数量', icon: 'none' })
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
    const response = await persistPlan()
    normalizePlan({
      ...response,
      status: response?.status || 'READY'
    })
    uni.showModal({
      title: '计划已保存',
      content: '当前只会保存为待执行计划，不会自动计入当日饮食记录。实际进食后，请再到饮食记录页手动记录。',
      showCancel: false
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
  const map = { 'AI_AGENT': 'AI 智能生成', 'RULE_BASED': '规则生成', 'MIXED': '混合生成' }
  return map[value] || '智能生成'
}

function planStatusLabel(value) {
  const map = { DRAFT: '草稿阶段', GENERATED: 'AI 已生成', READY: '待执行', APPLIED: '已应用' }
  return map[value] || '规划中'
}

function formatExecutionStage(value) {
  const map = {
    PERCEPTION: '感知',
    GRAPH_RETRIEVAL: '图谱检索',
    DOCUMENT_RETRIEVAL: '知识检索',
    PLAN_GENERATION: '计划生成',
    PLAN_VALIDATION: '结果校验',
    RESPONSE_GENERATION: '回答生成'
  }
  return map[String(value || '').toUpperCase()] || String(value || '处理中')
}

function formatSceneType(value) {
  const map = {
    MEAL_PLAN_DAILY: '今日计划执行链路',
    MEAL_PLAN_WEEK: '本周计划执行链路',
    ADVISOR_CHAT: '营养顾问链路'
  }
  return map[String(value || '').toUpperCase()] || '智能执行链路'
}

function openReferenceModal() {
  showProcessModal.value = false
  showReferenceModal.value = true
}

function openProcessModal() {
  showReferenceModal.value = false
  showProcessModal.value = true
}

function closeDetailModal() {
  showReferenceModal.value = false
  showProcessModal.value = false
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
/* 鐧界豢娓呯埥涓婚鍙橀噺 */
.page-modern {
  --app-bg: #ffffff; /* 绾櫧澶ц儗鏅?*/
  --card-bg: #ffffff;
  --primary: #059669; /* 椴滄槑鐜颁唬鐨勬牳蹇冪豢鑹?*/
  --primary-dark: #047857; /* 娣辩豢鑹诧紝鐢ㄤ簬鎮诞鎴栧己璋?*/
  --primary-light: #d1fae5; /* 鏋佹祬缁匡紝鐢ㄤ簬杈呭姪鑳屾櫙 */
  --text-main: #111827; /* 娣辩伆/榛戞枃鏈?*/
  --text-sub: #6b7280; /* 涓伆鑹茶緟鍔╂枃鏈?*/
  --border-light: #e5e7eb; /* 娴呰壊鍒嗗壊绾垮拰杈规 */
  --warn-color: #d97706; /* 姗欒壊鐢ㄤ簬璀﹀憡鏇夸唬 */
  --warn-bg: #fef3c7; /* 娴呮鑹茶儗鏅?*/

  min-height: 100vh;
  background-color: var(--app-bg);
  padding: 24rpx;
}

/* --- 1. AI 鏅鸿兘瑙勫垝鍖?--- */
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

/* 瀵硅瘽妗嗗紡杈撳叆 */
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

.btn-clear-plan {
  margin-top: 16rpx;
  height: 76rpx;
  border-radius: 20rpx;
  background: #ffffff;
  border: 1rpx dashed var(--border-light);
  color: var(--text-sub);
  font-size: 26rpx;
  font-weight: 600;
}

.btn-clear-plan::after { border: none; }

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
  background: #9ca3af;
}

.day-bubble.active .bubble-dot.has-plan {
  background: #ffffff;
}

/* --- 2. 璁″垝鎽樿浠〃鐩?--- */
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

/* 缃戞牸浠〃 */
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

/* AI 娲炲療 */
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
.insight-mode {
  font-size: 22rpx;
  color: var(--primary);
  font-weight: 600;
  background: var(--primary-light);
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}

.insight-content {
  font-size: 26rpx;
  color: #374151;
  line-height: 1.6;
}

.insight-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 20rpx;
}

.insight-action-btn {
  flex: 1;
  height: 72rpx;
  border-radius: 18rpx;
  background: var(--primary);
  color: #ffffff;
  font-size: 26rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.insight-action-btn.ghost {
  background: #ffffff;
  border: 1rpx solid var(--border-light);
  color: var(--text-main);
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

.guide-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.48);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.2s ease;
  z-index: 120;
}
.guide-modal-overlay.is-visible {
  opacity: 1;
  pointer-events: auto;
}
.guide-modal-card {
  width: 88%;
  max-height: 78vh;
  background: #ffffff;
  border-radius: 32rpx;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 24rpx 72rpx rgba(15, 23, 42, 0.18);
}
.plan-detail-modal {
  height: 78vh;
}
.plan-detail-modal .guide-body {
  flex: none;
  height: calc(78vh - 220rpx);
}
.guide-header {
  padding: 28rpx 28rpx 20rpx;
  border-bottom: 1rpx solid var(--border-light);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20rpx;
}
.guide-header-main {
  flex: 1;
}
.guide-title {
  display: block;
  font-size: 34rpx;
  font-weight: 800;
  color: var(--text-main);
}
.guide-subtitle {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: var(--text-sub);
}
.guide-close {
  width: 56rpx;
  height: 56rpx;
  border-radius: 999rpx;
  background: #f3f4f6;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.guide-close-text {
  font-size: 34rpx;
  line-height: 1;
  color: #6b7280;
}
.guide-body {
  flex: 1;
  height: 0;
  min-height: 0;
  padding: 24rpx 28rpx;
  box-sizing: border-box;
}
.guide-footer {
  padding: 18rpx 28rpx 24rpx;
  border-top: 1rpx solid var(--border-light);
  background: #ffffff;
  flex-shrink: 0;
  box-sizing: border-box;
}
.plan-detail-body {
  display: flex;
  flex-direction: column;
  gap: 22rpx;
  box-sizing: border-box;
}
.plan-detail-scroll-content {
  display: flex;
  flex-direction: column;
  gap: 22rpx;
  padding-bottom: 220rpx;
  box-sizing: border-box;
}
.detail-section {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}
.detail-section-title {
  font-size: 28rpx;
  font-weight: 800;
  color: var(--text-main);
}
.detail-card-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}
.detail-card-item,
.process-summary-card,
.process-step-card {
  padding: 22rpx 24rpx;
  border-radius: 18rpx;
  background: #f8fafc;
  border: 1rpx solid var(--border-light);
}
.detail-hero-card {
  padding: 24rpx;
  border-radius: 20rpx;
  background: linear-gradient(135deg, #ecfdf5 0%, #f0fdf4 100%);
  border: 1rpx solid #d1fae5;
}
.detail-hero-label {
  display: block;
  font-size: 22rpx;
  font-weight: 700;
  color: var(--primary);
  letter-spacing: 1rpx;
}
.detail-hero-title {
  display: block;
  margin-top: 10rpx;
  font-size: 28rpx;
  line-height: 1.6;
  font-weight: 700;
  color: var(--text-main);
}
.detail-card-head {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 10rpx;
}
.detail-card-index {
  min-width: 44rpx;
  height: 44rpx;
  padding: 0 10rpx;
  border-radius: 999rpx;
  background: #dcfce7;
  color: var(--primary-dark);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20rpx;
  font-weight: 800;
}
.detail-card-label {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--text-main);
}
.detail-card-text,
.process-summary-text,
.process-step-line {
  font-size: 24rpx;
  line-height: 1.6;
  color: #64748b;
}
.process-summary-title {
  display: block;
  font-size: 26rpx;
  font-weight: 800;
  color: var(--text-main);
}
.process-summary-text {
  display: block;
  margin-top: 8rpx;
}
.process-step-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-bottom: 8rpx;
}
.process-step-order {
  font-size: 22rpx;
  font-weight: 800;
  color: var(--primary);
}
.process-step-agent {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--text-main);
}
.process-step-stage {
  font-size: 22rpx;
  color: var(--text-sub);
}

/* --- 3. 鎵ц娓呭崟鍖?--- */
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

/* --- 4. 鎵嬪姩寰皟鍖?--- */
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


