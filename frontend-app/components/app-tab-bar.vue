<template>
  <view class="tabbar-shell">
    <view class="tabbar-panel">
      <view
        v-for="item in tabs"
        :key="item.key"
        class="tab-item"
        :class="{ active: current === item.key, center: item.center }"
        @click="navigate(item)"
      >
        <view v-if="item.center" class="capture-fab" :class="{ active: current === item.key }">
          <view class="camera-icon">
            <view class="camera-top"></view>
            <view class="camera-body">
              <view class="camera-lens"></view>
            </view>
          </view>
        </view>

        <view v-else class="icon-box">
          <view v-if="item.key === 'home'" class="home-icon">
            <view class="home-roof"></view>
            <view class="home-body"></view>
          </view>

          <view v-else-if="item.key === 'advisor'" class="advisor-icon">
            <view class="advisor-bubble"></view>
            <view class="advisor-tail"></view>
            <view class="advisor-dot dot-left"></view>
            <view class="advisor-dot dot-center"></view>
            <view class="advisor-dot dot-right"></view>
          </view>

          <view v-else-if="item.key === 'community'" class="community-icon">
            <view class="community-head head-left"></view>
            <view class="community-head head-right"></view>
            <view class="community-body body-left"></view>
            <view class="community-body body-right"></view>
          </view>

          <view v-else class="profile-icon">
            <view class="profile-head"></view>
            <view class="profile-body"></view>
          </view>
        </view>

        <text v-if="!item.center" class="tab-label">{{ item.label }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  current: {
    type: String,
    default: ''
  }
})

const tabs = [
  { key: 'home', label: '首页', url: '/pages/index/index' },
  { key: 'advisor', label: '顾问', url: '/pages/advisor/index' },
  { key: 'capture', label: '', url: '/pages/capture/index', center: true },
  { key: 'community', label: '社区', url: '/pages/community/index' },
  { key: 'profile', label: '我的', url: '/pages/profile/index' }
]

function navigate(item) {
  if (props.current === item.key) {
    return
  }

  uni.reLaunch({
    url: item.url
  })
}
</script>

<style scoped>
.tabbar-shell {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 0 20rpx calc(20rpx + env(safe-area-inset-bottom));
  z-index: 90;
}

.tabbar-panel {
  position: relative;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  height: 164rpx;
  padding: 26rpx 18rpx 18rpx;
  border-top: 1rpx solid rgba(23, 22, 18, 0.06);
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(16rpx);
  box-shadow: 0 -8rpx 26rpx rgba(19, 18, 15, 0.04);
  border-radius: 34rpx 34rpx 0 0;
}

.tab-item {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 10rpx;
  height: 100%;
}

.tab-item.center {
  transform: translateY(-26rpx);
}

.icon-box {
  position: relative;
  width: 58rpx;
  height: 52rpx;
}

.tab-label {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--nm-muted);
}

.tab-item.active .tab-label {
  color: var(--nm-primary);
}

.capture-fab {
  width: 112rpx;
  height: 112rpx;
  border-radius: 32rpx;
  background: var(--nm-primary);
  box-shadow: 0 18rpx 30rpx rgba(14, 165, 109, 0.28);
  display: flex;
  align-items: center;
  justify-content: center;
}

.capture-fab.active {
  background: var(--nm-primary-dark);
  box-shadow: 0 18rpx 30rpx rgba(24, 23, 17, 0.22);
}

.camera-icon {
  position: relative;
  width: 52rpx;
  height: 40rpx;
}

.camera-top {
  position: absolute;
  top: 0;
  left: 8rpx;
  width: 16rpx;
  height: 8rpx;
  border-radius: 8rpx 8rpx 0 0;
  background: #ffffff;
}

.camera-body {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 32rpx;
  border: 4rpx solid #ffffff;
  border-radius: 12rpx;
}

.camera-lens {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 14rpx;
  height: 14rpx;
  margin-left: -7rpx;
  margin-top: -7rpx;
  border-radius: 50%;
  border: 4rpx solid #ffffff;
}

.home-roof {
  position: absolute;
  left: 12rpx;
  top: 4rpx;
  width: 34rpx;
  height: 22rpx;
  border-top: 4rpx solid var(--nm-muted);
  border-left: 4rpx solid var(--nm-muted);
  transform: rotate(45deg);
  border-top-left-radius: 6rpx;
}

.home-body {
  position: absolute;
  left: 12rpx;
  bottom: 4rpx;
  width: 32rpx;
  height: 24rpx;
  border: 4rpx solid var(--nm-muted);
  border-radius: 8rpx;
}

.tab-item.active .home-roof,
.tab-item.active .home-body {
  border-color: var(--nm-primary);
}

.advisor-icon {
  position: relative;
  width: 58rpx;
  height: 52rpx;
}

.advisor-bubble,
.advisor-tail {
  position: absolute;
  border: 4rpx solid var(--nm-muted);
  background: transparent;
}

.advisor-bubble {
  left: 4rpx;
  top: 4rpx;
  width: 46rpx;
  height: 30rpx;
  border-radius: 16rpx;
}

.advisor-tail {
  left: 14rpx;
  bottom: 2rpx;
  width: 14rpx;
  height: 14rpx;
  border-top: none;
  border-right: none;
  transform: rotate(-35deg);
  border-bottom-left-radius: 8rpx;
}

.advisor-dot {
  position: absolute;
  top: 18rpx;
  width: 6rpx;
  height: 6rpx;
  border-radius: 50%;
  background: var(--nm-muted);
}

.dot-left {
  left: 16rpx;
}

.dot-center {
  left: 26rpx;
}

.dot-right {
  left: 36rpx;
}

.tab-item.active .advisor-bubble,
.tab-item.active .advisor-tail {
  border-color: var(--nm-primary);
}

.tab-item.active .advisor-dot {
  background: var(--nm-primary);
}

.community-head,
.community-body {
  position: absolute;
  border: 4rpx solid var(--nm-muted);
}

.community-head {
  top: 4rpx;
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
}

.head-left {
  left: 10rpx;
}

.head-right {
  right: 10rpx;
}

.community-body {
  bottom: 6rpx;
  width: 20rpx;
  height: 12rpx;
  border-top-left-radius: 16rpx;
  border-top-right-radius: 16rpx;
  border-bottom: none;
}

.body-left {
  left: 6rpx;
}

.body-right {
  right: 6rpx;
}

.tab-item.active .community-head,
.tab-item.active .community-body {
  border-color: var(--nm-primary);
}

.profile-head,
.profile-body {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  border: 4rpx solid var(--nm-muted);
}

.profile-head {
  top: 2rpx;
  width: 18rpx;
  height: 18rpx;
  border-radius: 50%;
}

.profile-body {
  bottom: 4rpx;
  width: 34rpx;
  height: 20rpx;
  border-top-left-radius: 20rpx;
  border-top-right-radius: 20rpx;
  border-bottom: none;
}

.tab-item.active .profile-head,
.tab-item.active .profile-body {
  border-color: var(--nm-primary);
}
</style>
