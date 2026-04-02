<template>
  <view class="header-container">
    <view class="header-content">
      <view class="header-left">
        <view 
          v-if="displayBack" 
          class="back-btn-active" 
          @click="goBack"
          hover-class="btn-hover-effect"
          :hover-stay-time="100"
        >
          <view class="back-icon-refined"></view>
        </view>

        <view class="title-group">
          <text class="main-title">{{ title }}</text>
          <view v-if="subtitle" class="subtitle-badge">
            <text class="subtitle-text">{{ subtitle }}</text>
          </view>
        </view>
      </view>

      <view v-if="$slots.right" class="header-right">
        <slot name="right" />
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'

const props = defineProps({
  title: { type: String, default: '知食分子' },
  subtitle: { type: String, default: '' },
  showBack: { type: Boolean, default: true },
  fallbackUrl: { type: String, default: '/pages/index/index' }
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
    uni.navigateBack({ delta: 1 })
  } else {
    uni.reLaunch({ url: props.fallbackUrl })
  }
}

onMounted(() => {
  updateStackState()
})
</script>

<style scoped>
/* 容器：实现毛玻璃和安全区适配 */
.header-container {
  position: sticky;
  top: 0;
  z-index: 999;
  width: 100%;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px);
  padding-top: env(safe-area-inset-top);
  border-bottom: 1rpx solid rgba(0, 0, 0, 0.03);
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 110rpx;
  padding: 0 32rpx;
  gap: 20rpx;
}

.header-left {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 24rpx;
  min-width: 0;
}

/* 返回按钮：更有质感的卡片感 */
.back-btn-active {
  width: 72rpx;
  height: 72rpx;
  background: #ffffff;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.06);
  border: 1rpx solid rgba(0, 0, 0, 0.02);
  flex-shrink: 0;
  transition: all 0.2s ease;
}

/* 点击缩放反馈 */
.btn-hover-effect {
  transform: scale(0.92);
  background: #f8f8f8;
}

.back-icon-refined {
  width: 18rpx;
  height: 18rpx;
  border-left: 5rpx solid #243428;
  border-bottom: 5rpx solid #243428;
  transform: rotate(45deg);
  margin-left: 6rpx;
}

.title-group {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

/* 主标题：更粗更稳 */
.main-title {
  font-size: 40rpx;
  font-weight: 800;
  color: #1a261f;
  letter-spacing: -0.5rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.2;
}

/* 副标题：胶囊样式更高级 */
.subtitle-badge {
  margin-top: 6rpx;
  background: #eef6f0;
  padding: 2rpx 12rpx;
  border-radius: 8rpx;
  align-self: flex-start;
}

.subtitle-text {
  font-size: 20rpx;
  font-weight: 600;
  color: #6ba27b;
  line-height: 1.4;
}

.header-right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}
</style>