<template>
  <view class="page">
    <app-page-header
      title="智能记录"
      subtitle="拍照秒识别，轻松记录饮食"
      fallback-url="/pages/index/index"
    >
      <template #right>
        <view class="nav-manual-btn" @click="openManualSearch">
          <text class="manual-icon">🔍</text>
          <text>手动输入</text>
        </view>
      </template>
    </app-page-header>

    <view class="upload-card" @click="chooseImage">
      <image v-if="selectedImage" class="upload-image" :src="selectedImage" mode="aspectFill" />
      <view v-else class="upload-placeholder">
        <view class="camera-icon-wrap">
          <text class="camera-emoji">📷</text>
        </view>
        <text class="upload-title">点击拍照 / 上传</text>
        <text class="upload-desc">AI 自动识别食物并估算营养</text>
      </view>
      
      <view v-if="recognizing" class="recognizing-mask">
        <view class="loading-spinner"></view>
        <text class="loading-text">AI 正在识别中...</text>
      </view>
    </view>

    <view class="section-container">
      <text class="section-title">这一餐是？</text>
      <scroll-view scroll-x class="meal-type-scroll" :show-scrollbar="false">
        <view class="meal-type-row">
          <view
            v-for="item in mealTypes"
            :key="item.value"
            class="meal-type-chip"
            :class="{ active: mealType === item.value }"
            @click="mealType = item.value"
          >
            <text class="meal-type-text">{{ item.label }}</text>
          </view>
        </view>
      </scroll-view>
    </view>

    <view class="section-container record-section">
      <view class="section-head">
        <view>
          <text class="section-title">今日已记录</text>
          <text class="section-subtitle">{{ today }}</text>
        </view>
        <text class="section-link" @click="goMeals">查看全部 ></text>
      </view>

      <view v-if="!dailyRecord.details.length" class="empty-card">
        <text class="empty-desc">今天还没有记录饮食，快拍张照记录你的第一餐吧。</text>
      </view>

      <view v-for="detail in dailyRecord.details.slice(0, 4)" :key="detail.id" class="record-card">
        <view class="record-left">
          <text class="record-name">{{ detail.foodName }}</text>
          <text class="record-meta">{{ mealTypeLabel(detail.mealType) }} 路 {{ formatTime(detail.createdAt) }}</text>
        </view>
        <view class="record-side">
          <text class="record-kcal">{{ formatNumber(detail.calories) }}</text>
          <text class="record-unit">千卡</text>
        </view>
      </view>
    </view>

    <view class="safe-padding"></view>
    <app-tab-bar current="capture" />

    <view class="popup-mask" :class="{ 'is-visible': showSelectorPopup }" @click="closeSelectorPopup"></view>
    <view class="bottom-sheet selector-sheet" :class="{ 'is-visible': showSelectorPopup }">
      <view class="sheet-header">
        <text class="sheet-title">{{ hasRecognitionResults ? 'AI 识别结果' : '查找食物' }}</text>
        <view class="close-btn" @click="closeSelectorPopup">×</view>
      </view>

      <view class="sheet-content">
        <view class="search-row">
          <view class="search-input-wrap">
            <text class="search-icon">🔍</text>
            <input
              v-model="keyword"
              class="search-input"
              placeholder="搜索食物名称"
              confirm-type="search"
              @confirm="searchFoods"
            />
          </view>
          <button class="search-button" @click="searchFoods">搜索</button>
        </view>
        
        <text v-if="hasRecognitionResults" class="recognition-mode">引擎: {{ recognitionModeLabel }}</text>

        <view v-if="recognizedConcept" class="concept-card">
          <view class="concept-head">
            <text class="concept-title">识别概念</text>
            <text v-if="recognizedConcept.confidence !== undefined && recognizedConcept.confidence !== null" class="concept-confidence">
              {{ Math.round(Number(recognizedConcept.confidence) * 100) }}%
            </text>

          </view>
          <text class="concept-name">{{ recognizedConcept.displayName || recognizedConcept.canonicalLabel || recognizedConcept.rawLabel }}</text>
          <text class="concept-desc">{{ conceptDescription }}</text>
          <view v-if="recognizedInsightChips.length" class="concept-insights">
            <text
              v-for="item in recognizedInsightChips"
              :key="item"
              class="concept-insight-chip"
            >
              {{ item }}
            </text>
          </view>
          <view v-if="recognizedConceptKeywords.length" class="concept-keywords">
            <text
              v-for="item in recognizedConceptKeywords"
              :key="item"
              class="concept-keyword"
            >
              {{ item }}
            </text>
          </view>
          <text v-if="directRecordCandidate && directRecordCandidate.estimated" class="concept-private-tip">
            当前识别结果已自动加入你的食物库，可直接记录并在后续重复使用。
          </text>
          <view v-if="directRecordCandidate" class="concept-actions">
            <button class="concept-primary-action" @click="openQuantityPopup(directRecordCandidate)">
              直接按“{{ directRecordCandidate.name }}”记录
            </button>
            <text class="concept-action-tip">如果下方候选里没有更合适的条目，就直接使用当前识别结果。</text>
          </view>
        </view>

        <scroll-view scroll-y class="selector-scroll-view">
          <view v-if="foods.length > 0" class="suggestion-grid">
            <view
              v-for="food in foods"
              :key="food.id"
              class="food-chip"
              @click="openQuantityPopup(food)"
            >
              <view class="food-chip-info">
                <text class="food-chip-name">{{ food.name }}</text>
                <view v-if="food.estimated" class="food-chip-badges">
                  <text class="food-chip-badge estimated">系统估算</text>
                  <text v-if="food.estimateSourceSummary" class="food-chip-badge source">{{ food.estimateSourceSummary }}</text>
                </view>
                <text v-if="food.estimated" class="food-private-note">已加入我的食物库</text>
                <text class="food-chip-meta">{{ foodMetaLabel(food) }}</text>
              </view>
              <view class="food-chip-add">+</view>
            </view>
          </view>

          <view v-if="!foods.length" class="empty-search-card">
            <text class="empty-emoji">🍽️</text>
            <text class="empty-search-title">暂未命中标准食物库</text>
            <text class="empty-search-desc">你可以换个关键词重试，或者继续使用当前识别结果，我们会优先自动估算营养值并加入你的食物库。</text>
            <button class="empty-search-button" @click="openManualSearch">重新搜索</button>
          </view>
        </scroll-view>
      </view>
    </view>

    <view class="popup-mask sub-mask" :class="{ 'is-visible': showQuantityPopup }" @click="closeQuantityPopup"></view>
    <view class="bottom-sheet quantity-sheet" :class="{ 'is-visible': showQuantityPopup }">
      <view class="sheet-header">
        <text class="sheet-title">确认食物分量</text>
        <view class="close-btn" @click="closeQuantityPopup">×</view>
      </view>

      <view v-if="selectedFood" class="sheet-content">
        <view class="selected-head">
          <view class="selected-main">
            <text class="selected-name">{{ selectedFood.name }}</text>
            <text class="selected-meta">{{ selectedFood.category || '未分类' }} · {{ selectedFood.unit || '100克' }}</text>
            <text v-if="selectedFood.estimated" class="selected-estimate-tip">
              该营养值已根据识别结果和相似食物自动估算，并同步加入你的食物库，确认后即可直接记录。
            </text>
          </view>
          <view class="selected-energy">
            <text class="selected-kcal">{{ formatNumber(selectedFood.calories) }}</text>
            <text class="selected-kcal-unit">千卡</text>
          </view>
        </view>

        <view class="nutrition-row">
          <view class="nutrition-cell">
            <text class="nutrition-label">蛋白质</text>
            <text class="nutrition-value">{{ formatNumber(selectedFood.protein, 1) }}g</text>
          </view>
          <view class="nutrition-cell">
            <text class="nutrition-label">碳水</text>
            <text class="nutrition-value">{{ formatNumber(selectedFood.carbohydrate, 1) }}g</text>
          </view>
          <view class="nutrition-cell">
            <text class="nutrition-label">脂肪</text>
            <text class="nutrition-value">{{ formatNumber(selectedFood.fat, 1) }}g</text>
          </view>
        </view>

        <view class="quantity-block">
          <text class="quantity-label">食用量（克 / 毫升）</text>
          <input v-model="quantity" class="quantity-input-large" type="digit" placeholder="输入分量" />
        </view>

        <view class="quantity-row">
          <view
            v-for="item in quickQuantities"
            :key="item"
            class="quantity-chip"
            :class="{ active: quantity === String(item) }"
            @click="quantity = String(item)"
          >
            <text class="quantity-chip-text">{{ item }}g</text>
          </view>
        </view>

        <button class="save-button" @click="saveMeal">记录到{{ currentMealLabel }}</button>
      </view>
    </view>

  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { ensureLoggedIn, formatToday } from '@/utils/auth.js'
import { formatNumber, formatTime, mealTypeLabel } from '@/utils/format.js'

const today = formatToday()
const quickQuantities = [50, 100, 150, 200, 300, 500]
const mealTypes = [
  { label: '早餐', value: 'BREAKFAST' },
  { label: '午餐', value: 'LUNCH' },
  { label: '晚餐', value: 'DINNER' },
  { label: '加餐', value: 'SNACK' }
]

const keyword = ref('')
const quantity = ref('100')
const mealType = ref('BREAKFAST')
const selectedImage = ref('')
const selectedImageFile = ref(null)

const recognizing = ref(false)
const recognitionMode = ref('')
const hasRecognitionResults = ref(false)
const recognizedConcept = ref(null)
const foods = ref([])
const recognizedPrimaryCandidate = ref(null)
const selectedFood = ref(null)
const dailyRecord = ref({ details: [] })

// --- 寮圭獥鎺у埗鐘舵€?(鍙屽眰鍫嗗彔) ---
const showSelectorPopup = ref(false) // 绗竴灞傦細鎼滅储/鍊欓€夊垪琛?
const showQuantityPopup = ref(false) // 绗簩灞傦細纭鍒嗛噺

const currentMealLabel = computed(() => {
  const meal = mealTypes.find(m => m.value === mealType.value)
  return meal ? meal.label : '今日记录'
})

const recognitionModeLabel = computed(() => {
  const mode = String(recognitionMode.value || '').toLowerCase()
  if (!mode) return '本地识别'
  if (mode.includes('onnx')) return '本地 ONNX 模型'
  if (mode.includes('clip')) return 'CLIP 视觉检索'
  if (mode.includes('python')) return '云端 AI 推理'
  return recognitionMode.value
})

const recognizedConceptKeywords = computed(() => {
  const keywords = Array.isArray(recognizedConcept.value?.searchKeywords)
    ? recognizedConcept.value.searchKeywords
    : []
  return keywords.slice(0, 4)
})

const recognizedInsightChips = computed(() => {
  if (!recognizedConcept.value) return []

  const exactWeight = recognizedConcept.value.estimatedWeightGrams
  const weightMin = recognizedConcept.value.estimatedWeightMinGrams
  const weightMax = recognizedConcept.value.estimatedWeightMaxGrams
  const weightText = exactWeight
    ? `估重 ${exactWeight}g`
    : (weightMin && weightMax
        ? `估重 ${weightMin}-${weightMax}g`
        : (weightMin ? `估重 ${weightMin}g 左右` : (weightMax ? `估重不超过 ${weightMax}g` : '')))

  return [
    recognizedConcept.value.cookingMethod ? `做法 ${recognizedConcept.value.cookingMethod}` : '',
    weightText,
    recognizedConcept.value.portionDescription ? `份量 ${recognizedConcept.value.portionDescription}` : ''
  ].filter(Boolean)
})

const conceptDescription = computed(() => {
  if (!recognizedConcept.value) return ''
  const conceptName = recognizedConcept.value.displayName
    || recognizedConcept.value.canonicalLabel
    || recognizedConcept.value.rawLabel
    || '当前概念'
  if (recognizedConcept.value.generic) {
    return `AI 先将图片归入“${conceptName}”方向，请再从下方候选食物中确认具体条目。`
  }
  return `AI 已先锁定“${conceptName}”方向，下方展示与该概念最接近的食物候选。`
})

const directRecordCandidate = computed(() => {
  if (recognizedPrimaryCandidate.value?.id) {
    return recognizedPrimaryCandidate.value
  }
  return pickDirectRecordCandidate(foods.value, recognizedConcept.value)
})

// --- 寮圭獥琛屼负鎺у埗 ---
function openManualSearch() {
  keyword.value = ''
  hasRecognitionResults.value = false
  recognizedConcept.value = null
  recognizedPrimaryCandidate.value = null
  foods.value = [] // 寮€鍚椂娓呯┖锛屾垨淇濈暀鍘嗗彶璁板綍
  showSelectorPopup.value = true
}

function closeSelectorPopup() {
  showSelectorPopup.value = false
}

function openQuantityPopup(food) {
  selectedFood.value = food
  quantity.value = '100' // 榛樿閲嶇疆涓?00g
  showQuantityPopup.value = true
}

function closeQuantityPopup() {
  showQuantityPopup.value = false
  // 寤惰繜娓呯┖閫変腑鐨勯鐗╋紝闃叉鍔ㄧ敾绌垮府
  setTimeout(() => {
    selectedFood.value = null
  }, 300)
}

// --- 涓氬姟閫昏緫 ---
async function chooseImage() {
  try {
    const result = await uni.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera']
    })
    const tempFiles = Array.isArray(result?.tempFiles) ? result.tempFiles : []
    const imagePaths = Array.isArray(result?.tempFilePaths)
      ? result.tempFilePaths
      : tempFiles.map((item) => item.path || item.tempFilePath).filter(Boolean)

    if (!imagePaths.length) return

    selectedImage.value = imagePaths[0]
    selectedImageFile.value = tempFiles[0]?.file || tempFiles[0] || null
    await recognizeImage()
  } catch (error) {
    const message = String(error?.errMsg || error?.message || '')
    if (!message.includes('cancel')) {
      uni.showToast({ title: '选图失败，请稍后重试', icon: 'none' })
    }
  }
}

async function recognizeImage() {
  if (!ensureLoggedIn() || !selectedImage.value) return

  recognizing.value = true
  hasRecognitionResults.value = false
  recognitionMode.value = ''
  recognizedConcept.value = null

  try {
    const response = await request.upload('/vision/recognize', {
      filePath: selectedImage.value,
      file: selectedImageFile.value,
      formData: { topK: 6 } 
    })

    const candidates = Array.isArray(response?.candidates) ? response.candidates : []
    recognizedConcept.value = response?.recognizedConcept || null
    recognizedPrimaryCandidate.value = pickDirectRecordCandidate(candidates, response?.recognizedConcept)
    foods.value = candidates
    hasRecognitionResults.value = candidates.length > 0 || !!recognizedConcept.value
    recognitionMode.value = response?.recognitionMode || ''

    if (recognizedConcept.value?.displayName) {
      keyword.value = recognizedConcept.value.displayName
    } else if (Array.isArray(recognizedConcept.value?.searchKeywords) && recognizedConcept.value.searchKeywords.length > 0) {
      keyword.value = recognizedConcept.value.searchKeywords[0]
    } else if (candidates.length > 0 && candidates[0].name) {
      keyword.value = candidates[0].name
    }

    // 识别完成后，自动弹起“选择食物”弹窗展示候选结果
    showSelectorPopup.value = true

  } catch (error) {
    foods.value = []
    hasRecognitionResults.value = false
    recognizedConcept.value = null
    recognizedPrimaryCandidate.value = null
    uni.showToast({ title: '识别超时或失败，请重试', icon: 'none' })
  } finally {
    recognizing.value = false
  }
}

async function searchFoods() {
  if (!ensureLoggedIn() || !keyword.value.trim()) return

  try {
    const response = await request.get('/foods', {
      keyword: keyword.value,
      current: 1,
      size: 12
    })
    const records = Array.isArray(response?.records) ? response.records : []
    foods.value = mergeSearchResultsWithDirectCandidate(records)
  } catch (error) {
    foods.value = []
    console.log('search foods failed', error)
  }
}

async function loadDailyRecord() {
  try {
    const response = await request.get('/meals/daily', { recordDate: today })
    dailyRecord.value = {
      details: [],
      ...(response || {}),
      details: Array.isArray(response?.details) ? response.details : []
    }
  } catch (error) {
    console.log('load daily record failed', error)
  }
}

async function saveMeal() {
  if (!ensureLoggedIn() || !selectedFood.value) return

const numericQuantity = Number(quantity.value)
if (Number.isNaN(numericQuantity) || numericQuantity <= 0) {
  uni.showToast({ title: '请输入正确的分量', icon: 'none' })
  return
}

try {
  await request.post('/meals', {
    recordDate: today,
    details: [
      {
        foodId: selectedFood.value.id,
        quantity: numericQuantity,
        mealType: mealType.value
      }
    ]
  })

  await submitRecognitionFeedback()
  
  uni.showToast({ title: '记录成功', icon: 'success' })
  
  // 一次性关闭所有弹窗并重置
  closeQuantityPopup()
  closeSelectorPopup()
  
  keyword.value = ''
  selectedImage.value = ''
  selectedImageFile.value = null
  foods.value = []
  hasRecognitionResults.value = false
  recognizedConcept.value = null
  recognizedPrimaryCandidate.value = null
  
  await loadDailyRecord()
} catch (error) {
  uni.showToast({ title: '保存失败，请检查网络', icon: 'none' })
}
}

async function submitRecognitionFeedback() {
  if (!selectedFood.value || !recognitionMode.value) return

  const recognizedLabel = selectedFood.value.recognizedLabel
    || selectedFood.value.recognizedCanonicalLabel
    || keyword.value
    || selectedFood.value.name

  const recognizedCanonicalLabel = selectedFood.value.recognizedCanonicalLabel
    || selectedFood.value.recognizedLabel
    || recognizedLabel

  const searchKeywords = Array.isArray(selectedFood.value.searchKeywords)
    ? selectedFood.value.searchKeywords
    : []

  try {
    await request.post('/foods/recognitions/feedback', {
      foodId: selectedFood.value.id,
      matchedFoodName: selectedFood.value.name,
      recognizedLabel,
      recognizedCanonicalLabel,
      confidence: selectedFood.value.confidence,
      recognitionMode: recognitionMode.value,
      searchTerms: searchKeywords,
      manualConfirmationRequired: true
    })
  } catch (error) {
    console.log('submit recognition feedback failed', error)
  }
}

function goMeals() {
  uni.navigateTo({ url: '/pages/meals/index' })
}

function goFoods() {
  uni.navigateTo({ url: '/pages/foods/index' })
}

function foodMetaLabel(food) {
  const parts = []
  if (food?.calories !== undefined && food?.calories !== null) {
    parts.push(`${formatNumber(food.calories)} kcal`)
  }
  if (typeof food?.confidence === 'number') {
    parts.push(`${Math.round(food.confidence * 100)}% 匹配`)
  }
  if (food?.estimated) {
    parts.push('已入我的食物库')
  }
  return parts.join(' · ')
}

function normalizeFoodName(value) {
  return String(value || '')
    .trim()
    .replace(/\s+/g, '')
    .toLowerCase()
}

function mergeSearchResultsWithDirectCandidate(records) {
  const direct = directRecordCandidate.value
  if (!direct?.id) return records

  const directName = normalizeFoodName(direct.name)
  const exists = records.some((item) => normalizeFoodName(item?.name) === directName)
  if (exists) return records

  return [direct, ...records]
}

function pickDirectRecordCandidate(candidates, concept) {
  const list = Array.isArray(candidates) ? candidates : []
  if (!list.length) return null

  const conceptName = normalizeFoodName(
    concept?.displayName
    || concept?.canonicalLabel
    || concept?.rawLabel
  )

  const exact = conceptName
    ? list.find((item) => normalizeFoodName(item?.name) === conceptName)
    : null
  if (exact?.id) return exact

  const estimated = list.find((item) => item?.estimated && item?.id)
  if (estimated?.id) return estimated

  return null
} // 这里补上缺失的 }

onShow(() => {
  if (!ensureLoggedIn()) return
  loadDailyRecord()
})
</script>

<style scoped>
.page {
  --nm-primary: #6B9E78; /* 茶绿色主色 */
  --nm-primary-dark: #588563;
  --nm-primary-light: rgba(107, 158, 120, 0.12);
  --nm-bg: #f5f7f9;
  --nm-card: #ffffff;
  --nm-text: #2d3132;
  --nm-muted: #959aa5;
  
  min-height: 100vh;
  background-color: var(--nm-bg);
  padding: 24rpx 28rpx 0;
}

.nav-manual-btn {
  display: flex;
  align-items: center;
  background: #f0f2f5;
  padding: 10rpx 24rpx;
  border-radius: 999rpx;
  font-size: 26rpx;
  font-weight: 600;
  color: #5c6366;
  transition: all 0.2s;
}

.nav-manual-btn:active {
  background: #e2e6ea;
  transform: scale(0.95);
}

.manual-icon {
  font-size: 28rpx;
  margin-right: 8rpx;
}


.section-container {
  margin-top: 24rpx;
  padding: 32rpx;
  border-radius: 32rpx;
  background: var(--nm-card);
  box-shadow: 0 4rpx 24rpx rgba(0, 0, 0, 0.02);
}

.section-title {
  font-size: 32rpx;
  font-weight: 800;
  color: var(--nm-text);
  margin-bottom: 24rpx;
  display: block;
}

/* --- 1. 鏍稿績涓婁紶鍖?--- */
.upload-card {
  position: relative;
  overflow: hidden;
  height: 480rpx;
  border-radius: 36rpx;
  background: linear-gradient(135deg, #eaf2eb 0%, var(--nm-card) 100%);
  box-shadow: 0 10rpx 30rpx rgba(107, 158, 120, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
}

.upload-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.camera-icon-wrap {
  width: 120rpx;
  height: 120rpx;
  background: #ffffff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(107, 158, 120, 0.15);
}

.camera-emoji {
  font-size: 56rpx;
}

.upload-title {
  font-size: 34rpx;
  font-weight: 800;
  color: var(--nm-text);
  margin-bottom: 12rpx;
}

.upload-desc {
  font-size: 26rpx;
  color: var(--nm-primary);
}

.recognizing-mask {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(4px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.loading-spinner {
  width: 50rpx;
  height: 50rpx;
  border: 6rpx solid var(--nm-primary-light);
  border-top-color: var(--nm-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 16rpx;
}
@keyframes spin { 100% { transform: rotate(360deg); } }

.loading-text {
  font-size: 28rpx;
  color: var(--nm-primary);
  font-weight: 600;
}

/* --- 2. 椁愭閫夋嫨 --- */
.meal-type-scroll {
  width: 100%;
  white-space: nowrap;
}

.meal-type-row {
  display: inline-flex;
  gap: 16rpx;
  padding: 4rpx 0;
}

.meal-type-chip {
  padding: 16rpx 40rpx;
  border-radius: 999rpx;
  background: #f0f2f5;
  transition: all 0.2s ease;
}

.meal-type-chip.active {
  background: var(--nm-primary);
  box-shadow: 0 6rpx 16rpx var(--nm-primary-light);
}

.meal-type-text {
  font-size: 28rpx;
  font-weight: 600;
  color: #5c6366;
}

.meal-type-chip.active .meal-type-text {
  color: #ffffff;
}

/* --- 3. 璁板綍姒傝 --- */
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 24rpx;
}

.section-subtitle {
  font-size: 26rpx;
  color: var(--nm-muted);
  margin-left: 16rpx;
}

.section-link {
  font-size: 26rpx;
  color: var(--nm-primary);
  font-weight: 600;
}

.record-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f0f2f5;
}

.record-card:last-child {
  border-bottom: none;
}

.record-name {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: var(--nm-text);
}

.record-meta {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--nm-muted);
}

.record-side {
  text-align: right;
}

.record-kcal {
  font-size: 36rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.record-unit {
  font-size: 22rpx;
  color: var(--nm-muted);
  margin-left: 4rpx;
}

.safe-padding {
  height: calc(180rpx + env(safe-area-inset-bottom));
}


/* ================= 鍙屽眰搴曢儴寮圭獥 (Bottom Sheets) ================= */

/* 缁熶竴鐨勯伄缃╁眰 */
.popup-mask {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(2px);
  z-index: 900; /* 绗竴灞傜骇 */
  opacity: 0;
  visibility: hidden;
  transition: all 0.3s ease;
}

.popup-mask.is-visible {
  opacity: 1;
  visibility: visible;
}

.popup-mask.sub-mask {
  z-index: 902; /* 绗簩灞傜骇閬僵锛岃鐩栧湪绗竴灞俿heet涔嬩笂 */
  background: rgba(0, 0, 0, 0.2); /* 鏇存煍鍜屼竴鐐?*/
}

/* 缁熶竴鐨?Sheet 搴曞骇 */
.bottom-sheet {
  position: fixed;
  left: 0; right: 0; bottom: 0;
  background: #ffffff;
  border-radius: 40rpx 40rpx 0 0;
  transform: translateY(100%);
  transition: transform 0.35s cubic-bezier(0.2, 0.8, 0.2, 1);
  padding-bottom: env(safe-area-inset-bottom);
}

.bottom-sheet.is-visible {
  transform: translateY(0);
}

/* 灞傜骇鎺у埗 */
.selector-sheet {
  z-index: 901;
}

.quantity-sheet {
  z-index: 903;
}

.sheet-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 32rpx 40rpx 16rpx;
}

.sheet-title {
  font-size: 32rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.close-btn {
  font-size: 48rpx;
  color: #999;
  line-height: 1;
  padding: 0 10rpx;
}

.sheet-content {
  padding: 0 40rpx 40rpx;
}

/* --- 绗竴灞?Sheet 鍐呭 (鎼滅储涓庡€欓€? --- */
.selector-scroll-view {
  max-height: 55vh; /* 闄愬埗鏈€楂橀珮搴︼紝淇濊瘉鍙粦鍔?*/
  width: 100%;
}

.search-row {
  display: flex;
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.search-input-wrap {
  flex: 1;
  height: 88rpx;
  background: #f5f7f9;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  padding: 0 24rpx;
}

.search-icon {
  font-size: 32rpx;
  margin-right: 12rpx;
}

.search-input {
  flex: 1;
  height: 100%;
  font-size: 28rpx;
  color: var(--nm-text);
}

.search-button {
  width: 140rpx;
  height: 88rpx;
  line-height: 88rpx;
  background: var(--nm-primary);
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 700;
  border-radius: 20rpx;
  margin: 0;
}

.recognition-mode {
  display: inline-block;
  font-size: 22rpx;
  color: var(--nm-primary);
  margin-bottom: 20rpx;
  background: var(--nm-primary-light);
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}

.concept-card {
  margin-bottom: 20rpx;
  padding: 20rpx 24rpx;
  border-radius: 20rpx;
  background: #f6faf7;
  border: 1rpx solid rgba(107, 158, 120, 0.16);
}

.concept-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.concept-title {
  font-size: 24rpx;
  color: var(--nm-primary);
  font-weight: 700;
}

.concept-confidence {
  font-size: 22rpx;
  color: var(--nm-muted);
}

.concept-name {
  display: block;
  margin-top: 10rpx;
  font-size: 30rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.concept-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: var(--nm-muted);
}

.concept-keywords {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
}

.concept-insights {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
}

.concept-insight-chip {
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  color: #4c6152;
  background: rgba(76, 97, 82, 0.08);
}

.concept-keyword {
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  color: var(--nm-primary);
  background: rgba(107, 158, 120, 0.12);
}

.concept-actions {
  margin-top: 22rpx;
}

.concept-primary-action {
  height: 82rpx;
  line-height: 82rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, var(--nm-primary) 0%, var(--nm-primary-dark) 100%);
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 700;
  box-shadow: 0 12rpx 24rpx rgba(107, 158, 120, 0.18);
}

.concept-primary-action::after {
  display: none;
}

.concept-action-tip {
  display: block;
  margin-top: 14rpx;
  font-size: 22rpx;
  line-height: 1.5;
  color: var(--nm-muted);
}

.concept-private-tip {
  display: block;
  margin-top: 18rpx;
  padding: 14rpx 18rpx;
  border-radius: 18rpx;
  background: rgba(107, 158, 120, 0.1);
  color: var(--nm-primary-dark);
  font-size: 22rpx;
  line-height: 1.5;
}

.suggestion-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
  padding-bottom: 20rpx;
}

.food-chip {
  background: #f8f9fa;
  padding: 24rpx;
  border-radius: 24rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border: 2rpx solid transparent;
  transition: all 0.2s;
}

.food-chip:active {
  background: var(--nm-primary-light);
  border-color: rgba(107, 158, 120, 0.3);
}

.food-chip-info {
  flex: 1;
  min-width: 0;
}

.food-chip-name {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: var(--nm-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.food-chip-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 10rpx;
}

.food-chip-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  font-size: 20rpx;
  font-weight: 700;
}

.food-chip-badge.estimated {
  background: rgba(107, 158, 120, 0.12);
  color: var(--nm-primary-dark);
}

.food-chip-badge.source {
  background: #eef2f5;
  color: #66707a;
}

.food-chip-meta {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--nm-muted);
}

.food-private-note {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--nm-primary-dark);
  font-weight: 600;
}

.food-chip-add {
  width: 48rpx;
  height: 48rpx;
  background: #ffffff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  color: var(--nm-primary);
  font-weight: 300;
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.05);
}

.empty-search-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60rpx 0 20rpx;
}

.empty-emoji {
  font-size: 64rpx;
  margin-bottom: 16rpx;
}

.empty-search-title {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--nm-text);
  margin-bottom: 12rpx;
}

.empty-search-desc {
  font-size: 26rpx;
  color: var(--nm-muted);
  text-align: center;
  margin-bottom: 24rpx;
}

.empty-search-button {
  background: var(--nm-primary-light);
  color: var(--nm-primary);
  font-size: 28rpx;
  font-weight: 600;
  height: 72rpx;
  line-height: 72rpx;
  border-radius: 36rpx;
  padding: 0 40rpx;
}
.empty-search-button::after { display: none; }

/* --- 绗簩灞?Sheet 鍐呭 (鍒嗛噺纭) --- */
.selected-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32rpx;
  padding-bottom: 24rpx;
  border-bottom: 2rpx solid #f0f2f5;
}

.selected-name {
  font-size: 40rpx;
  font-weight: 800;
  color: var(--nm-text);
  display: block;
}

.selected-meta {
  font-size: 26rpx;
  color: var(--nm-muted);
  margin-top: 8rpx;
  display: block;
}

.selected-estimate-tip {
  display: block;
  margin-top: 12rpx;
  font-size: 22rpx;
  line-height: 1.5;
  color: var(--nm-primary-dark);
  background: rgba(107, 158, 120, 0.1);
  padding: 12rpx 16rpx;
  border-radius: 18rpx;
}

.selected-energy {
  text-align: right;
  color: var(--nm-primary);
}

.selected-kcal {
  font-size: 48rpx;
  font-weight: 800;
}

.selected-kcal-unit {
  font-size: 24rpx;
  margin-left: 4rpx;
  font-weight: 600;
}

.nutrition-row {
  display: flex;
  gap: 16rpx;
  margin-bottom: 32rpx;
}

.nutrition-cell {
  flex: 1;
  background: #f8f9fa;
  padding: 20rpx;
  border-radius: 20rpx;
  text-align: center;
}

.nutrition-label {
  display: block;
  font-size: 24rpx;
  color: var(--nm-muted);
  margin-bottom: 8rpx;
}

.nutrition-value {
  font-size: 30rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.quantity-block {
  margin-bottom: 24rpx;
}

.quantity-label {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--nm-text);
  margin-bottom: 16rpx;
  display: block;
}

.quantity-input-large {
  width: 100%;
  height: 100rpx;
  background: #f5f7f9;
  border-radius: 24rpx;
  font-size: 40rpx;
  font-weight: 700;
  color: var(--nm-text);
  padding: 0 32rpx;
  box-sizing: border-box;
}

.quantity-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 40rpx;
}

.quantity-chip {
  padding: 16rpx 32rpx;
  border-radius: 999rpx;
  background: #f0f2f5;
  transition: all 0.2s;
}

.quantity-chip.active {
  background: var(--nm-primary);
  box-shadow: 0 6rpx 16rpx var(--nm-primary-light);
}

.quantity-chip-text {
  font-size: 26rpx;
  font-weight: 600;
  color: #666;
}

.quantity-chip.active .quantity-chip-text {
  color: #ffffff;
}

.save-button {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  background: var(--nm-primary);
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 800;
  border-radius: 48rpx;
  box-shadow: 0 12rpx 24rpx var(--nm-primary-light);
}
.save-button::after {
  display: none;
}
.save-button:active {
  transform: scale(0.98);
}
</style>


