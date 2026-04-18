<template>
  <view v-if="visible" class="date-sheet-mask" @click="handleMaskClick">
    <view class="date-sheet-card" @click.stop>
      <view class="date-sheet-header">
        <text class="date-sheet-title">{{ title }}</text>
        <text class="date-sheet-close" @click="emit('close')">×</text>
      </view>

      <picker-view class="date-picker-view" :value="pickerValue" indicator-style="height: 88rpx;" @change="handlePickerChange">
        <picker-view-column>
          <view v-for="year in years" :key="year" class="picker-item">{{ year }}年</view>
        </picker-view-column>
        <picker-view-column>
          <view v-for="month in months" :key="month" class="picker-item">{{ month }}月</view>
        </picker-view-column>
        <picker-view-column>
          <view v-for="day in days" :key="day" class="picker-item">{{ day }}日</view>
        </picker-view-column>
      </picker-view>

      <view class="date-sheet-footer">
        <button class="sheet-button sheet-button-secondary" @click="emit('close')">取消</button>
        <button class="sheet-button sheet-button-primary" @click="handleConfirm">确定</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '选择日期' },
  value: { type: String, default: '' },
  minYear: { type: Number, default: 1970 },
  maxYear: { type: Number, default: new Date().getFullYear() + 5 }
})

const emit = defineEmits(['close', 'confirm'])

const years = computed(() => {
  const result = []
  for (let year = props.minYear; year <= props.maxYear; year += 1) {
    result.push(year)
  }
  return result
})

const months = Array.from({ length: 12 }, (_, index) => index + 1)
const selectedYear = ref(props.minYear)
const selectedMonth = ref(1)
const selectedDay = ref(1)

const days = computed(() => {
  const totalDays = getDaysInMonth(selectedYear.value, selectedMonth.value)
  return Array.from({ length: totalDays }, (_, index) => index + 1)
})

const pickerValue = computed(() => {
  const yearIndex = Math.max(years.value.indexOf(selectedYear.value), 0)
  const monthIndex = Math.max(selectedMonth.value - 1, 0)
  const dayIndex = Math.max(Math.min(selectedDay.value - 1, days.value.length - 1), 0)
  return [yearIndex, monthIndex, dayIndex]
})

watch(
  () => [props.visible, props.value, props.minYear, props.maxYear],
  () => {
    syncFromValue(props.value)
  },
  { immediate: true }
)

watch(days, currentDays => {
  if (selectedDay.value > currentDays.length) {
    selectedDay.value = currentDays[currentDays.length - 1] || 1
  }
})

function syncFromValue(value) {
  const parsed = parseDate(value)
  const clampedYear = clamp(parsed.year, props.minYear, props.maxYear)
  selectedYear.value = clampedYear
  selectedMonth.value = clamp(parsed.month, 1, 12)
  selectedDay.value = clamp(parsed.day, 1, getDaysInMonth(clampedYear, selectedMonth.value))
}

function parseDate(value) {
  const today = new Date()
  if (!value || typeof value !== 'string') {
    return {
      year: today.getFullYear(),
      month: today.getMonth() + 1,
      day: today.getDate()
    }
  }

  const matched = value.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (!matched) {
    return {
      year: today.getFullYear(),
      month: today.getMonth() + 1,
      day: today.getDate()
    }
  }

  return {
    year: Number(matched[1]),
    month: Number(matched[2]),
    day: Number(matched[3])
  }
}

function getDaysInMonth(year, month) {
  return new Date(year, month, 0).getDate()
}

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max)
}

function handlePickerChange(event) {
  const [yearIndex = 0, monthIndex = 0, dayIndex = 0] = event.detail.value || []
  selectedYear.value = years.value[yearIndex] || props.minYear
  selectedMonth.value = months[monthIndex] || 1
  const maxDay = getDaysInMonth(selectedYear.value, selectedMonth.value)
  selectedDay.value = clamp((days.value[dayIndex] || dayIndex + 1 || 1), 1, maxDay)
}

function handleConfirm() {
  const value = `${selectedYear.value}-${pad2(selectedMonth.value)}-${pad2(selectedDay.value)}`
  emit('confirm', value)
  emit('close')
}

function pad2(value) {
  return String(value).padStart(2, '0')
}

function handleMaskClick() {
  emit('close')
}
</script>

<style scoped>
.date-sheet-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.48);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  z-index: 1300;
}

.date-sheet-card {
  width: 100%;
  background: #ffffff;
  border-top-left-radius: 28rpx;
  border-top-right-radius: 28rpx;
  padding: 28rpx 24rpx calc(env(safe-area-inset-bottom) + 24rpx);
  box-sizing: border-box;
}

.date-sheet-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.date-sheet-title {
  font-size: 32rpx;
  font-weight: 800;
  color: #111827;
}

.date-sheet-close {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: #f3f4f6;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
  line-height: 1;
  color: #4b5563;
}

.date-picker-view {
  width: 100%;
  height: 480rpx;
}

.picker-item {
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  font-size: 30rpx;
  color: #111827;
}

.date-sheet-footer {
  display: flex;
  gap: 20rpx;
  margin-top: 24rpx;
}

.sheet-button {
  flex: 1;
  height: 88rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  font-weight: 700;
}

.sheet-button::after {
  border: none;
}

.sheet-button-secondary {
  background: #f3f4f6;
  color: #374151;
}

.sheet-button-primary {
  background: #38d07d;
  color: #ffffff;
}
</style>
