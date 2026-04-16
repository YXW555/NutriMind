<template>
  <view class="page">
    <view class="nav-sticky" :style="navSafeStyle">
      <view class="search-bar">
        <view class="search-inner">
          <text class="search-icon">🔍</text>
          <input
            v-model="keyword"
            class="search-input"
            placeholder="搜经验、搜食谱、搜动态"
            confirm-type="search"
            @confirm="loadPosts"
          />
        </view>
      </view>

      <scroll-view scroll-x class="tag-scroll" :show-scrollbar="false">
        <view class="tag-row">
          <view
            v-for="item in tags"
            :key="item"
            class="tag-chip"
            :class="{ active: activeTag === item }"
            @click="changeTag(item)"
          >
            <text class="tag-chip-text">{{ item }}</text>
          </view>
        </view>
      </scroll-view>
    </view>

    <view v-if="!posts.length" class="empty-card">
      <text class="empty-title">还没有找到内容</text>
      <button class="btn-primary empty-btn" @click="goPublish">去发布</button>
    </view>

    <view class="waterfall-container" v-else>
      <view class="waterfall-column left-column">
        <view 
          v-for="(post, index) in leftPosts" 
          :key="post.id" 
          class="waterfall-item" 
          @click="goDetail(post)"
        >
          <image 
            v-if="post.displayImageUrls.length" 
            :src="post.displayImageUrls[0]" 
            mode="widthFix" 
            class="post-img"
          />
          <view 
            v-else 
            class="post-cover-placeholder" 
            :class="coverClass(post.tag)"
            :style="{ height: getPlaceholderHeight(index, 'left') }"
          >
            <text class="placeholder-text">{{ post.tag || '知食' }}</text>
          </view>
          
          <view class="post-info">
            <text class="post-title">{{ post.title || post.content }}</text>
            <view class="post-user-row">
              <view class="user-info">
                <view class="mini-avatar">
                  <text class="mini-avatar-text">{{ (post.authorName || '知').slice(0, 1) }}</text>
                </view>
                <text class="user-name">{{ post.authorName || '知食用户' }}</text>
              </view>
              <view class="like-box" @click.stop="toggleLike(post)">
                <text class="like-icon">{{ post.liked ? '❤️' : '🤍' }}</text>
                <text class="like-count">{{ post.likeCount || 0 }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <view class="waterfall-column right-column">
        <view 
          v-for="(post, index) in rightPosts" 
          :key="post.id" 
          class="waterfall-item" 
          @click="goDetail(post)"
        >
          <image 
            v-if="post.displayImageUrls.length" 
            :src="post.displayImageUrls[0]" 
            mode="widthFix" 
            class="post-img"
          />
          <view 
            v-else 
            class="post-cover-placeholder" 
            :class="coverClass(post.tag)"
            :style="{ height: getPlaceholderHeight(index, 'right') }"
          >
            <text class="placeholder-text">{{ post.tag || '知食' }}</text>
          </view>
          
          <view class="post-info">
            <text class="post-title">{{ post.title || post.content }}</text>
            <view class="post-user-row">
              <view class="user-info">
                <view class="mini-avatar">
                  <text class="mini-avatar-text">{{ (post.authorName || '知').slice(0, 1) }}</text>
                </view>
                <text class="user-name">{{ post.authorName || '知食用户' }}</text>
              </view>
              <view class="like-box" @click.stop="toggleLike(post)">
                <text class="like-icon">{{ post.liked ? '❤️' : '🤍' }}</text>
                <text class="like-count">{{ post.likeCount || 0 }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view class="fab-btn" @click="goPublish">
      <text class="fab-icon">+</text>
    </view>

    <app-tab-bar current="community" />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { getApiBaseUrl } from '@/utils/config.js'
import { ensureLoggedIn } from '@/utils/auth.js'
import { formatRelativeTime } from '@/utils/format.js'
import { createSafeAreaTopStyle } from '@/utils/layout.js'

const tags = ['关注', '广场', '减脂', '增肌', '素食', '快手菜']
const keyword = ref('')
const activeTag = ref('广场')
const posts = ref([])
const navSafeStyle = createSafeAreaTopStyle(12)

// 将帖子列表分成左右两列
const leftPosts = computed(() => posts.value.filter((_, i) => i % 2 === 0))
const rightPosts = computed(() => posts.value.filter((_, i) => i % 2 !== 0))

// 动态生成占位图的高低错落感
function getPlaceholderHeight(index, column) {
  // 预设四种高度，使得即使没有图片，卡片也是错落的
  const leftHeights = [460, 320, 500, 380];
  const rightHeights = [380, 500, 320, 460];
  const heights = column === 'left' ? leftHeights : rightHeights;
  return heights[index % 4] + 'rpx';
}

function coverClass(tag) {
  const map = { 减脂: 'cover-green', 增肌: 'cover-blue', 素食: 'cover-warm', 快手菜: 'cover-gold' }
  return map[tag] || 'cover-green'
}

function goPublish() { uni.navigateTo({ url: '/pages/community/publish' }) }
function goDetail(post) { uni.navigateTo({ url: `/pages/community/detail?id=${post.id}` }) }

function changeTag(tag) {
  activeTag.value = tag
  loadPosts()
}

function resolveAssetUrl(url) {
  if (!url) return ''
  if (/^https?:\/\//i.test(url)) return url
  const apiBase = getApiBaseUrl().replace(/\/+$/, '')
  const origin = apiBase.replace(/\/api$/i, '')
  return url.startsWith('/') ? `${origin}${url}` : `${origin}/${url}`
}

function normalizePost(post) {
  const imageUrls = Array.isArray(post?.imageUrls) ? post.imageUrls.filter(Boolean) : []
  return {
    ...post,
    imageUrls,
    displayImageUrls: imageUrls.map(resolveAssetUrl)
  }
}

async function loadPosts() {
  if (!ensureLoggedIn()) return
  try {
    const response = await request.get('/community/posts', {
      keyword: keyword.value,
      tag: (activeTag.value === '广场' || activeTag.value === '广场') ? '' : activeTag.value,
      current: 1,
      size: 50
    })
    const records = Array.isArray(response?.records) ? response.records : []
    posts.value = records.map(normalizePost)
  } catch (error) {
    console.log('load community posts failed', error)
  }
}

async function toggleLike(post) {
  try {
    const response = await request.post(`/community/posts/${post.id}/like`)
    posts.value = posts.value.map(item => (item.id === post.id ? normalizePost(response) : item))
  } catch (error) {
    console.log('toggle like failed', error)
  }
}

onShow(() => { loadPosts() })
</script>

<style scoped>
.page {
  min-height: 100vh;
  background-color: #F7F8FA; /* 浅灰底色 */
  padding: 0 20rpx 180rpx;
  box-sizing: border-box;
}

/* --- 1. 顶部搜索区样式 --- */
.nav-sticky {
  position: sticky;
  top: 0;
  z-index: 100;
  background-color: #F7F8FA;
  padding-top: calc(env(safe-area-inset-top) + 20rpx);
  padding-bottom: 10rpx;
}

.search-bar {
  display: flex;
  align-items: center;
  /* gap: 20rpx; */ /* 移除了 gap，因为只有一个子元素 */
  margin-bottom: 20rpx;
}

.search-inner {
  flex: 1;
  height: 80rpx;
  background: #FFFFFF;
  border-radius: 40rpx;
  display: flex;
  align-items: center;
  padding: 0 30rpx;
  box-shadow: 0 2rpx 10rpx rgba(0,0,0,0.02);
}

.search-icon { margin-right: 14rpx; font-size: 28rpx; }
.search-input { flex: 1; font-size: 28rpx; color: #262626; }

/* 移除了旧的 .msg-icon, .icon-text, .msg-dot 样式 */

.tag-scroll { white-space: nowrap; width: 100%; }
.tag-row { display: inline-flex; gap: 40rpx; padding: 10rpx 10rpx 20rpx; }
.tag-chip-text { font-size: 30rpx; color: #999999; font-weight: 500; }
.tag-chip.active .tag-chip-text { color: #262626; font-weight: 800; border-bottom: 6rpx solid #38D07D; padding-bottom: 6rpx; }

/* --- 2. 瀑布流布局样式 --- */
.waterfall-container {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-top: 10rpx;
}

.waterfall-column {
  width: 346rpx; /* (750 - 20*2 - 18) / 2 */
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

/* 强制让右边列往下挪一点，制造出天然的交错感 */
.right-column {
  margin-top: 50rpx; 
}

.waterfall-item {
  background: #FFFFFF;
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.02);
}

.post-img {
  width: 100%;
  display: block;
  background-color: #F0F0F0;
}

.post-cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  transition: height 0.3s;
}
.cover-green { background: #E8F3EE; }
.cover-blue { background: #EAF2FB; }
.cover-warm { background: #F8F1E5; }
.cover-gold { background: #FFF4E5; }
.placeholder-text { font-size: 44rpx; font-weight: 900; color: rgba(0,0,0,0.1); }

.post-info { padding: 20rpx 16rpx; }
.post-title {
  font-size: 28rpx;
  color: #262626;
  font-weight: bold;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2; /* 最多显示两行标题 */
  overflow: hidden;
  margin-bottom: 20rpx;
}

.post-user-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.user-info { display: flex; align-items: center; gap: 10rpx; }
.mini-avatar {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: #F0F0F0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.mini-avatar-text { font-size: 20rpx; color: #999999; font-weight: bold; }
.user-name { font-size: 22rpx; color: #999999; max-width: 120rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.like-box { display: flex; align-items: center; gap: 4rpx; }
.like-icon { font-size: 24rpx; }
.like-count { font-size: 22rpx; color: #999999; }

/* --- 3. 悬浮按钮样式 --- */
.fab-btn {
  position: fixed;
  right: 40rpx;
  bottom: 200rpx; /* 在 TabBar 上方 */
  width: 110rpx;
  height: 110rpx;
  background: #38D07D;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 20rpx rgba(56, 208, 125, 0.4);
  z-index: 99;
}
.fab-icon {
  font-size: 60rpx;
  color: #FFFFFF;
  font-weight: 300;
  margin-top: -6rpx;
}

/* 空状态 */
.empty-card { text-align: center; padding-top: 200rpx; }
.empty-title { display: block; font-size: 30rpx; color: #999999; margin-bottom: 30rpx; }
.btn-primary { 
  width: 240rpx; height: 80rpx; background: #38D07D; color: #fff; 
  border-radius: 40rpx; display: flex; align-items: center; justify-content: center; font-size: 28rpx;
}
.btn-primary::after { border: none; }
</style>
