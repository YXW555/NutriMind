<template>
  <view class="page">
    <app-page-header
      title="智能识别"
      subtitle="拍照识别食物并快速记录今天的摄入"
      fallback-url="/pages/index/index"
    />

    <view class="upload-card" @click="chooseImage">
      <image v-if="selectedImage" class="upload-image" :src="selectedImage" mode="aspectFill" />
      <view v-else class="upload-placeholder">
        <view class="camera-icon">
          <view class="camera-top"></view>
          <view class="camera-body">
            <view class="camera-lens"></view>
          </view>
        </view>
        <text class="upload-title">点击拍照 / 上传</text>
        <text class="upload-desc">上传后会自动识别食物，并给出可直接保存的候选结果</text>
      </view>
    </view>

    <view class="quick-row">
      <button class="quick-action" @click="focusManual">手动输入</button>
    </view>

    <view class="section-card">
      <text class="section-title">餐次选择</text>
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

      <text class="section-title search-title">{{ hasRecognitionResults ? '识别候选食物' : '手动确认食物' }}</text>
      <view class="search-row">
        <input
          id="manual-input"
          v-model="keyword"
          class="search-input"
          placeholder="搜索食物名称"
          confirm-type="search"
          @confirm="searchFoods"
        />
        <button class="search-button" @click="searchFoods">搜索</button>
      </view>

      <text v-if="recognizing" class="recognition-status">正在识别图片，请稍候...</text>
      <text v-else-if="hasRecognitionResults" class="recognition-status">
        已找到 {{ foods.length }} 个候选结果，当前识别方式：{{ recognitionModeLabel }}
      </text>

      <view v-if="hasRecognitionResults && selectedFood" class="recognition-summary">
        <text class="summary-title">当前推荐</text>
        <text class="summary-text">
          {{ selectedFood.name }}
          <text v-if="typeof selectedFood.confidence === 'number'"> · 置信度 {{ Math.round(selectedFood.confidence * 100) }}%</text>
        </text>
        <text v-if="selectedFood.matchReason" class="summary-desc">{{ selectedFood.matchReason }}</text>
      </view>

      <view class="suggestion-grid">
        <view
          v-for="food in foods"
          :key="food.id"
          class="food-chip"
          :class="{ active: selectedFood && selectedFood.id === food.id }"
          @click="selectFood(food)"
        >
          <text class="food-chip-name">{{ food.name }}</text>
          <text class="food-chip-meta">{{ foodMetaLabel(food) }}</text>
        </view>
      </view>

      <view v-if="!foods.length" class="empty-search-card">
        <text class="empty-search-title">当前还没有可选食物</text>
        <text class="empty-search-desc">你可以先手动搜索，也可以重启 food-service 让核心食物自动补齐。</text>
        <button class="empty-search-button" @click="goFoods">去食物库新增</button>
      </view>
    </view>

    <view v-if="selectedFood" class="selected-card">
      <view class="selected-head">
        <view class="selected-main">
          <text class="selected-name">{{ selectedFood.name }}</text>
          <text class="selected-meta">{{ selectedFood.category || '未分类' }} · {{ selectedFood.unit || '100克' }}</text>
        </view>
        <text class="selected-kcal">{{ formatNumber(selectedFood.calories) }} 千卡</text>
      </view>

      <view class="nutrition-row">
        <view class="nutrition-cell">
          <text class="nutrition-label">蛋白质</text>
          <text class="nutrition-value">{{ formatNumber(selectedFood.protein, 1) }}克</text>
        </view>
        <view class="nutrition-cell">
          <text class="nutrition-label">碳水</text>
          <text class="nutrition-value">{{ formatNumber(selectedFood.carbohydrate, 1) }}克</text>
        </view>
        <view class="nutrition-cell">
          <text class="nutrition-label">脂肪</text>
          <text class="nutrition-value">{{ formatNumber(selectedFood.fat, 1) }}克</text>
        </view>
      </view>

      <view class="quantity-block">
        <text class="quantity-label">食用量（克）</text>
        <input v-model="quantity" class="quantity-input" type="digit" />
      </view>

      <view class="quantity-row">
        <view
          v-for="item in quickQuantities"
          :key="item"
          class="quantity-chip"
          @click="quantity = `${item}`"
        >
          <text class="quantity-chip-text">{{ item }}克</text>
        </view>
      </view>

      <button class="save-button" @click="saveMeal">添加到今日记录</button>
    </view>

    <view class="section-head">
      <view>
        <text class="section-title">今天已记录</text>
        <text class="section-subtitle">{{ today }}</text>
      </view>
      <text class="section-link" @click="goMeals">全部</text>
    </view>

    <view v-if="!dailyRecord.details.length" class="empty-card">
      <text class="empty-title">还没有保存任何食物</text>
      <text class="empty-desc">选中食物并填写食用量后，就可以直接写入今天的饮食记录。</text>
    </view>

    <view v-for="detail in dailyRecord.details.slice(0, 4)" :key="detail.id" class="record-card">
      <view>
        <text class="record-name">{{ detail.foodName }}</text>
        <text class="record-meta">{{ mealTypeLabel(detail.mealType) }} · {{ formatTime(detail.createdAt) }}</text>
      </view>
      <view class="record-side">
        <text class="record-kcal">{{ formatNumber(detail.calories) }}</text>
        <text class="record-unit">千卡</text>
      </view>
    </view>

    <app-tab-bar current="capture" />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { ensureLoggedIn, formatToday } from '@/utils/auth.js'
import { formatNumber, formatTime, mealTypeLabel } from '@/utils/format.js'

const today = formatToday()
const quickQuantities = [80, 100, 150, 200, 300]
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
const dailyRecord = ref({
  details: []
})

const recognitionModeLabel = computed(() => {
  const mode = String(recognitionMode.value || '').toLowerCase()
  if (!mode) {
    return '本地候选匹配'
  }
  if (mode.includes('onnx')) {
    return '本地 ONNX 分类模型'
  }
  if (mode.includes('clip')) {
    return 'CLIP 图文检索'
  }
  if (mode.includes('python')) {
    return 'Python 推理服务'
  }
  if (mode.includes('mock')) {
    return '模拟识别'
  }
  return recognitionMode.value
})

function selectFood(food) {
  selectedFood.value = food
}

function focusManual() {
  uni.pageScrollTo({
    scrollTop: 380,
    duration: 220
  })
}

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

    if (!imagePaths.length) {
      uni.showToast({
        title: '没有获取到图片，请重试',
        icon: 'none'
      })
      return
    }

    selectedImage.value = imagePaths[0]
    selectedImageFile.value = tempFiles[0]?.file || tempFiles[0] || null
    await recognizeImage()
  } catch (error) {
    const message = String(error?.errMsg || error?.message || '')
    if (message.includes('cancel')) {
      return
    }

    console.log('choose image failed', error)
    uni.showToast({
      title: '选图失败，请稍后重试',
      icon: 'none'
    })
  }
}

async function recognizeImage() {
  if (!ensureLoggedIn() || !selectedImage.value) {
    return
  }

  recognizing.value = true
  hasRecognitionResults.value = false
  recognitionMode.value = ''

  try {
    const response = await request.upload('/vision/recognize', {
      filePath: selectedImage.value,
      file: selectedImageFile.value,
      formData: {
        topK: 4
      }
    })

    const candidates = Array.isArray(response?.candidates) ? response.candidates : []
    foods.value = candidates
    selectedFood.value = candidates[0] || null
    hasRecognitionResults.value = candidates.length > 0
    recognitionMode.value = response?.recognitionMode || ''

    if (selectedFood.value?.name) {
      keyword.value = selectedFood.value.name
    }

    uni.showToast({
      title: candidates.length ? '识别完成，请确认结果' : '没有识别到候选，请手动搜索',
      icon: 'none'
    })
  } catch (error) {
    foods.value = []
    selectedFood.value = null
    hasRecognitionResults.value = false
    console.log('recognize image failed', error)
  } finally {
    recognizing.value = false
  }
}

async function searchFoods() {
  if (!ensureLoggedIn()) {
    return
  }

  try {
    hasRecognitionResults.value = false
    recognitionMode.value = ''
    const response = await request.get('/foods', {
      keyword: keyword.value,
      current: 1,
      size: 12
    })
    foods.value = Array.isArray(response?.records) ? response.records : []
    selectedFood.value = foods.value[0] || null
  } catch (error) {
    foods.value = []
    selectedFood.value = null
    console.log('search foods failed', error)
  }
}

async function loadDailyRecord() {
  try {
    const response = await request.get('/meals/daily', {
      recordDate: today
    })
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
  if (!ensureLoggedIn()) {
    return
  }
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
      title: '请输入正确的食用量',
      icon: 'none'
    })
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
    quantity.value = '100'
    selectedImage.value = ''
    selectedImageFile.value = null
    hasRecognitionResults.value = false
    recognitionMode.value = ''
    uni.showToast({
      title: '记录成功',
      icon: 'success'
    })
    await loadDailyRecord()
  } catch (error) {
    console.log('save meal failed', error)
  }
}

function goMeals() {
  uni.navigateTo({
    url: '/pages/meals/index'
  })
}

function goFoods() {
  uni.navigateTo({
    url: '/pages/foods/index'
  })
}

function foodMetaLabel(food) {
  const parts = []
  if (typeof food?.confidence === 'number') {
    parts.push(`${Math.round(food.confidence * 100)}% 匹配`)
  }
  if (food?.calories !== undefined && food?.calories !== null) {
    parts.push(`${formatNumber(food.calories)} 千卡`)
  }
  return parts.join(' · ')
}

onShow(() => {
  if (!ensureLoggedIn()) {
    return
  }
  searchFoods()
  loadDailyRecord()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 32rpx 28rpx 220rpx;
}

.upload-card,
.section-card,
.selected-card,
.empty-card,
.record-card {
  margin-top: 28rpx;
  border-radius: 36rpx;
  background: var(--nm-card);
  box-shadow: var(--nm-shadow);
}

.upload-card {
  position: relative;
  overflow: hidden;
  min-height: 560rpx;
  padding: 24rpx;
  background:
    radial-gradient(circle at center, rgba(14, 165, 109, 0.12) 0%, rgba(14, 165, 109, 0.04) 38%, transparent 72%),
    var(--nm-card);
}

.upload-image {
  width: 100%;
  height: 512rpx;
  border-radius: 28rpx;
}

.upload-placeholder {
  min-height: 512rpx;
  border: 4rpx dashed rgba(23, 22, 18, 0.08);
  border-radius: 28rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.camera-icon {
  position: relative;
  width: 84rpx;
  height: 62rpx;
  margin-bottom: 26rpx;
}

.camera-top {
  position: absolute;
  top: 0;
  left: 14rpx;
  width: 26rpx;
  height: 12rpx;
  border-radius: 10rpx 10rpx 0 0;
  background: #c9c5bd;
}

.camera-body {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 50rpx;
  border: 5rpx solid #c9c5bd;
  border-radius: 18rpx;
}

.camera-lens {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 22rpx;
  height: 22rpx;
  margin-left: -11rpx;
  margin-top: -11rpx;
  border-radius: 50%;
  border: 5rpx solid #c9c5bd;
}

.upload-title {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
  color: #b3aa9a;
}

.upload-desc,
.section-subtitle,
.record-meta,
.empty-desc,
.selected-meta,
.nutrition-label,
.summary-desc {
  font-size: 26rpx;
  color: var(--nm-muted);
}

.upload-desc {
  margin-top: 16rpx;
  text-align: center;
  line-height: 1.7;
}

.quick-row,
.meal-type-row,
.search-row,
.nutrition-row,
.quantity-row,
.section-head,
.record-card {
  display: flex;
}

.quick-row,
.meal-type-row,
.quantity-row,
.nutrition-row {
  gap: 16rpx;
}

.quick-row {
  margin-top: 24rpx;
}

.quick-action {
  flex: 1;
  height: 88rpx;
  background: rgba(255, 255, 255, 0.8);
  color: #544d40;
  font-size: 30rpx;
  font-weight: 700;
}

.section-card,
.selected-card,
.empty-card,
.record-card {
  padding: 28rpx;
}

.section-title {
  display: block;
  font-size: 34rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.search-title {
  margin-top: 28rpx;
}

.recognition-status {
  display: block;
  margin-top: 18rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: var(--nm-primary);
}

.recognition-summary {
  margin-top: 18rpx;
  padding: 22rpx 24rpx;
  border-radius: 24rpx;
  background: rgba(14, 165, 109, 0.08);
}

.summary-title {
  display: block;
  font-size: 24rpx;
  font-weight: 700;
  color: var(--nm-primary);
}

.summary-text {
  display: block;
  margin-top: 10rpx;
  font-size: 30rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.summary-desc {
  display: block;
  margin-top: 10rpx;
  line-height: 1.6;
}

.meal-type-row {
  margin-top: 18rpx;
  flex-wrap: wrap;
}

.meal-type-chip {
  padding: 16rpx 24rpx;
  border-radius: 999rpx;
  background: #f0eee7;
}

.meal-type-chip.active {
  background: var(--nm-primary);
}

.meal-type-text {
  font-size: 26rpx;
  font-weight: 700;
  color: #665f51;
}

.meal-type-chip.active .meal-type-text {
  color: #ffffff;
}

.search-row {
  gap: 14rpx;
  margin-top: 18rpx;
}

.search-input,
.quantity-input {
  flex: 1;
  height: 88rpx;
  padding: 0 24rpx;
  border-radius: 24rpx;
  background: #f5f4ef;
  font-size: 28rpx;
}

.search-button,
.save-button {
  font-size: 28rpx;
  font-weight: 700;
}

.search-button {
  width: 148rpx;
  background: var(--nm-primary-dark);
  color: #ffffff;
}

.suggestion-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14rpx;
  margin-top: 22rpx;
}

.food-chip {
  padding: 20rpx;
  border-radius: 24rpx;
  background: #f5f4ef;
}

.food-chip.active {
  background: rgba(14, 165, 109, 0.12);
}

.food-chip-name {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: var(--nm-text);
}

.food-chip-meta {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: var(--nm-muted);
}

.empty-search-card {
  margin-top: 22rpx;
  padding: 24rpx;
  border-radius: 28rpx;
  background: #f7f5ef;
}

.empty-search-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: var(--nm-text);
}

.empty-search-desc {
  display: block;
  margin-top: 12rpx;
  line-height: 1.7;
}

.empty-search-button {
  width: 100%;
  height: 84rpx;
  margin-top: 20rpx;
  background: rgba(14, 165, 109, 0.12);
  color: var(--nm-primary);
  font-size: 28rpx;
  font-weight: 700;
}

.selected-head,
.section-head,
.record-card {
  justify-content: space-between;
  align-items: center;
}

.selected-main {
  flex: 1;
  min-width: 0;
}

.selected-name,
.record-name,
.empty-title {
  display: block;
  font-size: 34rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.selected-kcal {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--nm-primary);
}

.nutrition-row {
  margin-top: 24rpx;
}

.nutrition-cell {
  flex: 1;
  padding: 20rpx;
  border-radius: 22rpx;
  background: #f5f4ef;
}

.nutrition-value {
  display: block;
  margin-top: 10rpx;
  font-size: 28rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.quantity-block {
  margin-top: 24rpx;
}

.quantity-label {
  display: block;
  margin-bottom: 14rpx;
  font-size: 28rpx;
  font-weight: 700;
  color: var(--nm-text);
}

.quantity-row {
  margin-top: 18rpx;
  flex-wrap: wrap;
}

.quantity-chip {
  padding: 14rpx 22rpx;
  border-radius: 999rpx;
  background: #edf6f1;
}

.quantity-chip-text {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--nm-primary);
}

.save-button {
  width: 100%;
  height: 92rpx;
  margin-top: 26rpx;
  background: var(--nm-primary);
  color: #ffffff;
}

.section-head {
  margin-top: 36rpx;
}

.section-link {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--nm-primary);
}

.record-side {
  text-align: right;
}

.record-kcal {
  display: block;
  font-size: 46rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.record-unit {
  font-size: 24rpx;
  color: #b0a99b;
}
</style>
