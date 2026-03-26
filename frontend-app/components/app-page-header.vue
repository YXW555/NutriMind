<template>
  <view class="page-header">
    <view class="header-left">
      <view v-if="displayBack" class="back-button" @click="goBack">
        <view class="back-icon"></view>
      </view>

      <view class="title-block">
        <text class="page-title">{{ title }}</text>
        <text v-if="subtitle" class="page-subtitle">{{ subtitle }}</text>
      </view>
    </view>

    <view v-if="$slots.right" class="header-right">
      <slot name="right" />
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'

const props = defineProps({
  title: {
    type: String,
    default: ''
  },
  subtitle: {
    type: String,
    default: ''
  },
  showBack: {
    type: Boolean,
    default: true
  },
  fallbackUrl: {
    type: String,
    default: '/pages/index/index'
  }
})

const canGoBack = ref(false)

function updateStackState() {
  const pages = getCurrentPages()
  canGoBack.value = Array.isArray(pages) && pages.length > 1
}

const displayBack = computed(() => props.showBack && canGoBack.value)

function goBack() {
  const pages = getCurrentPages()
  if (Array.isArray(pages) && pages.length > 1) {
    uni.navigateBack({
      delta: 1
    })
    return
  }

  uni.reLaunch({
    url: props.fallbackUrl
  })
}

onMounted(() => {
  updateStackState()
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20rpx;
  padding-top: calc(env(safe-area-inset-top) + 12rpx);
}

.header-left {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: flex-start;
  gap: 18rpx;
}

.back-button {
  width: 72rpx;
  height: 72rpx;
  border-radius: 22rpx;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--nm-shadow);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.back-icon {
  width: 20rpx;
  height: 20rpx;
  border-left: 4rpx solid var(--nm-text);
  border-bottom: 4rpx solid var(--nm-text);
  transform: rotate(45deg);
}

.title-block {
  flex: 1;
  min-width: 0;
}

.page-title {
  display: block;
  font-size: 56rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.page-subtitle {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  line-height: 1.7;
  color: var(--nm-muted);
}

.header-right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}
</style>
