<template>
  <view class="page">
    <app-page-header
      title="智能记录"
      subtitle="拍照秒识别，轻松记饮食"
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
          <text class="camera-emoji">📸</text>
        </view>
        <text class="upload-title">点击拍照 / 上传</text>
        <text class="upload-desc">AI 自动识别食物并计算热量</text>
      </view>
      
      <view v-if="recognizing" class="recognizing-mask">
        <view class="loading-spinner"></view>
        <text class="loading-text">AI 努力识别中...</text>
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
          <text class="section-title">今天已记录</text>
          <text class="section-subtitle">{{ today }}</text>
        </view>
        <text class="section-link" @click="goMeals">查看全部 ></text>
      </view>

      <view v-if="!dailyRecord.details.length" class="empty-card">
        <text class="empty-desc">今天还没有记录饮食，快拍张照记录你的第一餐吧！</text>
      </view>

      <view v-for="detail in dailyRecord.details.slice(0, 4)" :key="detail.id" class="record-card">
        <view class="record-left">
          <text class="record-name">{{ detail.foodName }}</text>
          <text class="record-meta">{{ mealTypeLabel(detail.mealType) }} · {{ formatTime(detail.createdAt) }}</text>
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
                <text class="food-chip-meta">{{ foodMetaLabel(food) }}</text>
              </view>
              <view class="food-chip-add">+</view>
            </view>
          </view>

          <view v-if="!foods.length" class="empty-search-card">
            <text class="empty-emoji">🍽️</text>
            <text class="empty-search-title">未找到相关食物</text>
            <text class="empty-search-desc">换个词搜索，或者去食物库手动添加吧</text>
            <button class="empty-search-button" @click="goFoods">去食物库新增</button>
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
          <text class="quantity-label">食用量 (克/毫升)</text>
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

        <button class="save-button" @click="saveMeal">记录到 {{ currentMealLabel }}</button>
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
const foods = ref([])
const selectedFood = ref(null)
const dailyRecord = ref({ details: [] })

// --- 弹窗控制状态 (双层堆叠) ---
const showSelectorPopup = ref(false) // 第一层：搜索/候选列表
const showQuantityPopup = ref(false) // 第二层：确认分量

const currentMealLabel = computed(() => {
  const meal = mealTypes.find(m => m.value === mealType.value)
  return meal ? meal.label : '今日记录'
})

const recognitionModeLabel = computed(() => {
  const mode = String(recognitionMode.value || '').toLowerCase()
  if (!mode) return '本地检索'
  if (mode.includes('onnx')) return '本地 ONNX 模型'
  if (mode.includes('clip')) return 'CLIP 视觉检索'
  if (mode.includes('python')) return '云端 AI 推理'
  return recognitionMode.value
})

// --- 弹窗行为控制 ---
function openManualSearch() {
  keyword.value = ''
  hasRecognitionResults.value = false
  foods.value = [] // 开启时清空，或保留历史记录
  showSelectorPopup.value = true
}

function closeSelectorPopup() {
  showSelectorPopup.value = false
}

function openQuantityPopup(food) {
  selectedFood.value = food
  quantity.value = '100' // 默认重置为100g
  showQuantityPopup.value = true
}

function closeQuantityPopup() {
  showQuantityPopup.value = false
  // 延迟清空选中的食物，防止动画穿帮
  setTimeout(() => {
    selectedFood.value = null
  }, 300)
}

// --- 业务逻辑 ---
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

  try {
    const response = await request.upload('/vision/recognize', {
      filePath: selectedImage.value,
      file: selectedImageFile.value,
      formData: { topK: 6 } 
    })

    const candidates = Array.isArray(response?.candidates) ? response.candidates : []
    foods.value = candidates
    hasRecognitionResults.value = candidates.length > 0
    recognitionMode.value = response?.recognitionMode || ''

    if (candidates.length > 0 && candidates[0].name) {
      keyword.value = candidates[0].name
    }

    // 识别完成后，自动弹起“选择食物”弹窗展示候选结果
    showSelectorPopup.value = true

  } catch (error) {
    foods.value = []
    hasRecognitionResults.value = false
    uni.showToast({ title: '识别超时或失败，请重试', icon: 'none' })
  } finally {
    recognizing.value = false
  }
}

async function searchFoods() {
  if (!ensureLoggedIn() || !keyword.value.trim()) return

  try {
    hasRecognitionResults.value = false
    recognitionMode.value = ''
    const response = await request.get('/foods', {
      keyword: keyword.value,
      current: 1,
      size: 12
    })
    foods.value = Array.isArray(response?.records) ? response.records : []
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
    
    uni.showToast({ title: '记录成功', icon: 'success' })
    
    // 一次性关掉所有弹窗并重置
    closeQuantityPopup()
    closeSelectorPopup()
    
    keyword.value = ''
    selectedImage.value = ''
    selectedImageFile.value = null
    foods.value = []
    hasRecognitionResults.value = false
    
    await loadDailyRecord()
  } catch (error) {
    uni.showToast({ title: '保存失败，请检查网络', icon: 'none' })
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
    parts.push(`${Math.round(food.confidence * 100)}%匹配`)
  }
  return parts.join(' · ')
}

onShow(() => {
  if (!ensureLoggedIn()) return
  loadDailyRecord()
})
</script>

<style scoped>
.page {
  --nm-primary: #6B9E78; /* 抹茶绿主色 */
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

/* --- 角落里的手动输入按钮 --- */
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

/* --- 通用容器 --- */
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

/* --- 1. 核心上传区 --- */
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

/* --- 2. 餐次选择 --- */
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

/* --- 3. 记录概览 --- */
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


/* ================= 双层底部弹窗 (Bottom Sheets) ================= */

/* 统一的遮罩层 */
.popup-mask {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(2px);
  z-index: 900; /* 第一层级 */
  opacity: 0;
  visibility: hidden;
  transition: all 0.3s ease;
}

.popup-mask.is-visible {
  opacity: 1;
  visibility: visible;
}

.popup-mask.sub-mask {
  z-index: 902; /* 第二层级遮罩，覆盖在第一层sheet之上 */
  background: rgba(0, 0, 0, 0.2); /* 更柔和一点 */
}

/* 统一的 Sheet 底座 */
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

/* 层级控制 */
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

/* --- 第一层 Sheet 内容 (搜索与候选) --- */
.selector-scroll-view {
  max-height: 55vh; /* 限制最高高度，保证可滑动 */
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

.food-chip-meta {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--nm-muted);
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

/* --- 第二层 Sheet 内容 (分量确认) --- */
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
.save-button::after { display: none; }
.save-button:active { transform: scale(0.98); }
</style>