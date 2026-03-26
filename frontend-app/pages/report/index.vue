<template>
  <view class="page">
    <app-page-header
      title="营养报告"
      subtitle="看清这一周或这一月的摄入趋势"
      fallback-url="/pages/index/index"
    >
      <template #right>
        <view class="range-switch">
          <view
            v-for="item in rangeOptions"
            :key="item.value"
            class="range-chip"
            :class="{ active: rangeType === item.value }"
            @click="changeRange(item.value)"
          >
            <text class="range-chip-text">{{ item.label }}</text>
          </view>
        </view>
      </template>
    </app-page-header>

    <view v-if="false" class="top-bar">
      <view>
        <text class="page-title">营养报告</text>
        <text class="page-subtitle">看清这一周或这一月的摄入趋势</text>
      </view>

      <view class="range-switch">
        <view
          v-for="item in rangeOptions"
          :key="item.value"
          class="range-chip"
          :class="{ active: rangeType === item.value }"
          @click="changeRange(item.value)"
        >
          <text class="range-chip-text">{{ item.label }}</text>
        </view>
      </view>
    </view>

    <view class="trend-card">
      <text class="card-title">{{ rangeType === 'week' ? '本周热量趋势' : '本月热量趋势' }}</text>

      <view class="trend-chart" :style="{ gridTemplateColumns: `repeat(${Math.max(chartItems.length, 1)}, minmax(48rpx, 1fr))` }">
        <view v-for="item in chartItems" :key="item.date" class="trend-column">
          <view class="trend-bar-wrap">
            <view class="trend-bar" :style="{ height: `${item.height}%` }"></view>
          </view>
          <text class="trend-label">{{ item.label }}</text>
        </view>
      </view>
    </view>

    <view class="summary-grid">
      <view class="info-card macro-card">
        <text class="card-title">宏量营养比例</text>
        <view class="macro-stack">
          <view class="macro-segment protein" :style="{ width: `${report.macroRatio?.proteinPercent || 0}%` }"></view>
          <view class="macro-segment carbohydrate" :style="{ width: `${report.macroRatio?.carbohydratePercent || 0}%` }"></view>
          <view class="macro-segment fat" :style="{ width: `${report.macroRatio?.fatPercent || 0}%` }"></view>
        </view>

        <view class="legend-list">
          <view class="legend-item">
            <view class="legend-dot protein"></view>
            <text class="legend-text">蛋白质 {{ report.macroRatio?.proteinPercent || 0 }}%</text>
          </view>
          <view class="legend-item">
            <view class="legend-dot carbohydrate"></view>
            <text class="legend-text">碳水 {{ report.macroRatio?.carbohydratePercent || 0 }}%</text>
          </view>
          <view class="legend-item">
            <view class="legend-dot fat"></view>
            <text class="legend-text">脂肪 {{ report.macroRatio?.fatPercent || 0 }}%</text>
          </view>
        </view>
      </view>

      <view class="info-card average-card">
        <text class="card-title">平均每日摄入</text>
        <text class="average-value">{{ formatNumber(report.averageCalories) }}</text>
        <text class="average-unit">千卡</text>
        <text class="average-desc">记录天数 {{ report.recordedDays || 0 }}</text>
        <view class="average-track">
          <view class="average-fill" :style="{ width: `${report.completionRate || 0}%` }"></view>
        </view>
        <text class="average-foot">达成度 {{ report.completionRate || 0 }}%</text>
      </view>
    </view>

    <view class="insight-card">
      <text class="card-title">{{ report.highlightTitle || '先开始记录今天的饮食' }}</text>
      <text class="insight-text">{{ report.highlightDesc || '有了真实数据后，这里会给你趋势和建议。' }}</text>
      <view class="metric-row">
        <view class="metric-chip">
          <text class="metric-label">蛋白质</text>
          <text class="metric-value">{{ formatNumber(report.averageProtein, 1) }}克</text>
        </view>
        <view class="metric-chip">
          <text class="metric-label">碳水</text>
          <text class="metric-value">{{ formatNumber(report.averageCarbohydrate, 1) }}克</text>
        </view>
        <view class="metric-chip">
          <text class="metric-label">脂肪</text>
          <text class="metric-value">{{ formatNumber(report.averageFat, 1) }}克</text>
        </view>
      </view>
    </view>

  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { ensureLoggedIn } from '@/utils/auth.js'
import { formatNumber } from '@/utils/format.js'

const rangeOptions = [
  { label: '周', value: 'week' },
  { label: '月', value: 'month' }
]

const rangeType = ref('week')
const report = ref({
  averageCalories: 0,
  averageProtein: 0,
  averageFat: 0,
  averageCarbohydrate: 0,
  completionRate: 0,
  recordedDays: 0,
  trend: [],
  macroRatio: {
    proteinPercent: 0,
    carbohydratePercent: 0,
    fatPercent: 0
  }
})

const chartItems = computed(() => {
  const trend = Array.isArray(report.value.trend) ? report.value.trend : []
  const maxCalories = trend.reduce((max, item) => Math.max(max, Number(item.calories || 0)), 0)
  return trend.map(item => ({
    ...item,
    height: maxCalories > 0 ? Math.max(14, Math.round((Number(item.calories || 0) / maxCalories) * 100)) : 14
  }))
})

async function loadReport() {
  if (!ensureLoggedIn()) {
    return
  }
  try {
    const response = await request.get('/reports/overview', {
      rangeType: rangeType.value
    })
    report.value = response || report.value
  } catch (error) {
    console.log('load report failed', error)
  }
}

function changeRange(value) {
  if (rangeType.value === value) {
    return
  }
  rangeType.value = value
  loadReport()
}

onShow(() => {
  loadReport()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 32rpx 28rpx 80rpx;
}

.top-bar,
.range-switch,
.summary-grid,
.legend-item,
.metric-row {
  display: flex;
}

.top-bar,
.legend-item {
  justify-content: space-between;
}

.top-bar {
  align-items: flex-start;
}

.page-title {
  display: block;
  font-size: 64rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.page-subtitle,
.trend-label,
.legend-text,
.average-desc,
.average-foot,
.insight-text,
.metric-label {
  font-size: 26rpx;
  color: var(--nm-muted);
}

.page-subtitle {
  margin-top: 12rpx;
}

.range-switch {
  gap: 10rpx;
  padding: 10rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.8);
  box-shadow: var(--nm-shadow);
}

.range-chip {
  min-width: 88rpx;
  padding: 18rpx 0;
  border-radius: 20rpx;
  text-align: center;
}

.range-chip.active {
  background: #ffffff;
}

.range-chip-text {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--nm-muted);
}

.range-chip.active .range-chip-text {
  color: var(--nm-text);
}

.trend-card,
.info-card,
.insight-card {
  margin-top: 30rpx;
  padding: 28rpx;
  border-radius: 34rpx;
  background: var(--nm-card);
  box-shadow: var(--nm-shadow);
}

.card-title {
  display: block;
  font-size: 38rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.trend-chart {
  display: grid;
  gap: 18rpx;
  align-items: end;
  margin-top: 30rpx;
  min-height: 400rpx;
  overflow-x: auto;
}

.trend-column {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  height: 100%;
}

.trend-bar-wrap {
  display: flex;
  align-items: flex-end;
  width: 100%;
  height: 300rpx;
}

.trend-bar {
  width: 100%;
  min-height: 24rpx;
  border-radius: 24rpx 24rpx 14rpx 14rpx;
  background: linear-gradient(180deg, rgba(14, 165, 109, 0.92) 0%, rgba(14, 165, 109, 0.26) 100%);
}

.trend-label {
  margin-top: 18rpx;
}

.summary-grid {
  gap: 18rpx;
}

.info-card {
  flex: 1;
}

.macro-stack {
  display: flex;
  overflow: hidden;
  height: 24rpx;
  margin-top: 26rpx;
  border-radius: 999rpx;
  background: #ece9e2;
}

.macro-segment.protein,
.legend-dot.protein {
  background: #14b67d;
}

.macro-segment.carbohydrate,
.legend-dot.carbohydrate {
  background: #4784f3;
}

.macro-segment.fat,
.legend-dot.fat {
  background: #f4a516;
}

.legend-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-top: 26rpx;
}

.legend-item {
  align-items: center;
  gap: 12rpx;
}

.legend-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  flex-shrink: 0;
}

.average-value {
  display: block;
  margin-top: 24rpx;
  font-size: 72rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.average-unit {
  display: block;
  margin-top: 6rpx;
  font-size: 28rpx;
  color: #8d8577;
}

.average-desc {
  margin-top: 18rpx;
}

.average-track {
  overflow: hidden;
  height: 14rpx;
  margin-top: 24rpx;
  border-radius: 999rpx;
  background: #ece9e2;
}

.average-fill {
  height: 100%;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #0ea56d 0%, #60c89e 100%);
}

.average-foot {
  display: block;
  margin-top: 14rpx;
}

.insight-text {
  display: block;
  margin-top: 18rpx;
  line-height: 1.7;
}

.metric-row {
  gap: 16rpx;
  margin-top: 24rpx;
}

.metric-chip {
  flex: 1;
  padding: 20rpx 18rpx;
  border-radius: 24rpx;
  background: #f5f4ef;
}

.metric-value {
  display: block;
  margin-top: 12rpx;
  font-size: 34rpx;
  font-weight: 800;
  color: var(--nm-text);
}
</style>
