<template>
  <view class="xhs-detail-page">
    <app-page-header
      title="动态详情"
      fallback-url="/pages/community/index"
      :border="false"
      class="custom-header"
    />

    <view v-if="displayImageUrls.length" class="media-gallery">
      <swiper 
        class="gallery-swiper" 
        :indicator-dots="false" 
        :autoplay="false" 
        circular
        @change="onSwiperChange"
      >
        <swiper-item v-for="(url, index) in displayImageUrls" :key="`${post.id}-${index}`">
          <image 
            class="gallery-image" 
            mode="aspectFill" 
            :src="url" 
            @click="previewImages(index)" 
          />
        </swiper-item>
      </swiper>
      
      <view class="custom-dots" v-if="displayImageUrls.length > 1">
        <view 
          v-for="(_, index) in displayImageUrls" 
          :key="index" 
          class="dot" 
          :class="{ 'active': currentImageIndex === index }"
        ></view>
      </view>
    </view>

    <view class="content-body">
      <view class="author-row">
        <view class="author-info">
          <view class="avatar-circle">{{ (post.authorName || '知').charAt(0) }}</view>
          <text class="author-name">{{ post.authorName || '知食用户' }}</text>
        </view>
        <button class="xhs-follow-btn" :class="{ 'is-followed': isFollowed }" @click="isFollowed = !isFollowed">
          {{ isFollowed ? '已关注' : '关注' }}
        </button>
      </view>

      <view class="article-section">
        <text v-if="post.title" class="article-title">{{ post.title }}</text>
        
        <view class="article-content">
          <text class="text-paragraph">{{ post.content || '加载中...' }}</text>
          <text class="article-tag" v-if="post.tag" @click="searchTag">#{{ post.tag }}</text>
        </view>
        
        <view class="article-meta">
          <text class="meta-time">{{ formatRelativeTime(post.createdAt) }}</text>
        </view>
      </view>

      <view class="hairline-divider"></view>

      <view class="comment-section">
        <text class="comment-count-title">共 {{ comments.length }} 条评论</text>

        <view v-if="!comments.length" class="empty-comments">
          <view class="empty-avatar-placeholder"></view>
          <text class="empty-text">说点什么吧，万一火了呢...</text>
        </view>

        <view class="comment-list">
          <view v-for="item in comments" :key="item.id" class="comment-item">
            <view class="comment-avatar">{{ (item.authorName || '知').charAt(0) }}</view>
            
            <view class="comment-main">
              <text class="comment-author-name">{{ item.authorName || '知食用户' }}</text>
              <view class="comment-text-wrap">
                <text class="comment-text">{{ item.content }}</text>
              </view>
              
              <view class="comment-footer">
                <text class="comment-time">{{ formatRelativeTime(item.createdAt) }}</text>
                <text class="reply-btn" @click="focusInput('回复 @' + (item.authorName || '知食用户'))">回复</text>
                <text v-if="item.ownComment" class="delete-btn" @click="deleteComment(item.id)">删除</text>
              </view>
            </view>

            <view class="comment-like">
              <text class="like-heart">♡</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view class="xhs-bottom-bar" :style="{ paddingBottom: keyboardHeight > 0 ? keyboardHeight + 'px' : 'env(safe-area-inset-bottom)' }">
      <view class="bar-container">
        <view class="input-box" @click="isInputFocused = true">
          <text class="edit-icon">✎</text>
          <input 
            v-model="commentContent"
            class="real-input"
            :focus="isInputFocused"
            :placeholder="inputPlaceholder"
            placeholder-class="input-placeholder-style"
            confirm-type="send"
            :adjust-position="false"
            @focus="onInputFocus"
            @blur="onInputBlur"
            @confirm="submitComment"
          />
          <text v-if="commentContent.length > 0" class="send-text" @click.stop="submitComment">发送</text>
        </view>

        <view class="action-group" v-show="!isInputFocused && commentContent.length === 0">
          <view class="action-btn" :class="{ 'active': post.liked }" @click="toggleLike">
            <text class="action-icon">{{ post.liked ? '❤️' : '♡' }}</text>
            <text class="action-num">{{ post.likeCount || '赞' }}</text>
          </view>
          <view class="action-btn" :class="{ 'active': post.favorited }" @click="toggleFavorite">
            <text class="action-icon">{{ post.favorited ? '⭐' : '☆' }}</text>
            <text class="action-num">{{ post.favoriteCount || '收藏' }}</text>
          </view>
          <view class="action-btn" @click="scrollToComments">
            <text class="action-icon">💬</text>
            <text class="action-num">{{ comments.length || '评论' }}</text>
          </view>
        </view>
      </view>
    </view>

  </view>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { ensureLoggedIn } from '@/utils/auth.js'
import { formatRelativeTime } from '@/utils/format.js'
import { getApiBaseUrl } from '@/utils/config.js'

const postId = ref('')
const post = ref({})
const comments = ref([])
const commentContent = ref('')

// 交互状态
const currentImageIndex = ref(0)
const isFollowed = ref(false) // 模拟关注状态
const isInputFocused = ref(false)
const inputPlaceholder = ref('说点什么...')
const keyboardHeight = ref(0)

const displayImageUrls = computed(() => {
  const imageUrls = Array.isArray(post.value?.imageUrls) ? post.value.imageUrls : []
  return imageUrls.map(resolveAssetUrl)
})

function onSwiperChange(e) {
  currentImageIndex.value = e.detail.current
}

function resolveAssetUrl(url) {
  if (!url) return ''
  if (/^https?:\/\//i.test(url)) return url
  const apiBase = getApiBaseUrl().replace(/\/+$/, '')
  const origin = apiBase.replace(/\/api$/i, '')
  return url.startsWith('/') ? `${origin}${url}` : `${origin}/${url}`
}

function previewImages(index) {
  if (!displayImageUrls.value.length) return
  uni.previewImage({
    current: displayImageUrls.value[index],
    urls: displayImageUrls.value
  })
}

// 模拟小红书标签点击
function searchTag() {
  uni.showToast({ title: `搜索话题: ${post.value.tag}`, icon: 'none' })
}

// 唤起键盘并设置回复对象
function focusInput(placeholderText) {
  inputPlaceholder.value = placeholderText
  isInputFocused.value = true
}

// 监听键盘高度变化，确保底部输入框被准确推起
function onInputFocus(e) {
  keyboardHeight.value = e.detail.height || 0
  isInputFocused.value = true
}

function onInputBlur() {
  keyboardHeight.value = 0
  isInputFocused.value = false
  if (!commentContent.value) {
    inputPlaceholder.value = '说点什么...' // 失去焦点且无内容时重置
  }
}

function scrollToComments() {
  uni.pageScrollTo({ selector: '.comment-section', duration: 300 })
}

async function loadPostDetail() {
  if (!ensureLoggedIn() || !postId.value) return
  try {
    const [postResponse, commentResponse] = await Promise.all([
      request.get(`/community/posts/${postId.value}`),
      request.get(`/community/posts/${postId.value}/comments`)
    ])
    post.value = postResponse || {}
    comments.value = Array.isArray(commentResponse) ? commentResponse : []
  } catch (error) {
    console.log('load community detail failed', error)
  }
}

async function toggleLike() {
  if (!ensureLoggedIn()) return
  try {
    post.value = await request.post(`/community/posts/${postId.value}/like`)
  } catch (error) {}
}

async function toggleFavorite() {
  if (!ensureLoggedIn()) return
  try {
    post.value = await request.post(`/community/posts/${postId.value}/favorite`)
  } catch (error) {}
}

async function submitComment() {
  if (!ensureLoggedIn()) return
  if (!commentContent.value.trim()) {
    uni.showToast({ title: '评论不能为空哦', icon: 'none' })
    return
  }

  try {
    await request.post(`/community/posts/${postId.value}/comments`, {
      content: commentContent.value.trim()
    })
    uni.showToast({ title: '发送成功', icon: 'none' })
    commentContent.value = ''
    isInputFocused.value = false
    uni.hideKeyboard()
    loadPostDetail()
  } catch (error) {
    uni.showToast({ title: '发送失败', icon: 'none' })
  }
}

function deleteComment(commentId) {
  uni.showModal({
    title: '提示',
    content: '删除这条评论？',
    confirmColor: '#ff2442',
    success: async (result) => {
      if (!result.confirm) return
      try {
        await request.delete(`/community/comments/${commentId}`)
        loadPostDetail()
      } catch (error) {}
    }
  })
}

onLoad((query) => {
  postId.value = query?.id || ''
  // 监听键盘高度，部分平台适用
  uni.onKeyboardHeightChange((res) => {
    keyboardHeight.value = res.height
  })
})

onShow(() => {
  loadPostDetail()
})
</script>

<style scoped>
.xhs-detail-page {
  min-height: 100vh;
  background-color: #ffffff;
  /* 基础变量 */
  --xhs-red: #ff2442;
  --xhs-blue: #1352ac;
  --xhs-text-main: #333333;
  --xhs-text-sub: #999999;
  --xhs-bg-gray: #f5f5f5;
  --xhs-divider: rgba(0, 0, 0, 0.05);
  padding-bottom: calc(120rpx + env(safe-area-inset-bottom));
}

/* --- 1. 画廊区域 (4:3 比例) --- */
.media-gallery {
  position: relative;
  width: 100vw;
  height: 133.33vw; /* 小红书经典的 4:3 比例竖图展现 */
  background-color: #fafafa;
}

.gallery-swiper {
  width: 100%;
  height: 100%;
}

.gallery-image {
  width: 100%;
  height: 100%;
  display: block;
}

/* 剧中点状指示器 */
.custom-dots {
  position: absolute;
  bottom: 24rpx;
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12rpx;
}

.dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.5);
  transition: all 0.3s;
}

.dot.active {
  width: 12rpx;
  height: 12rpx;
  background-color: #ffffff;
}

.content-body {
  padding: 0 32rpx;
}

/* --- 2. 作者栏 --- */
.author-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx 0;
}

.author-info {
  display: flex;
  align-items: center;
}

.avatar-circle {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background: var(--xhs-bg-gray);
  color: var(--xhs-text-main);
  font-size: 32rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
}

.author-name {
  font-size: 30rpx;
  font-weight: 500;
  color: var(--xhs-text-main);
}

.xhs-follow-btn {
  margin: 0;
  padding: 0;
  width: 124rpx;
  height: 54rpx;
  line-height: 50rpx;
  border-radius: 27rpx;
  border: 2rpx solid var(--xhs-red);
  background: #ffffff;
  color: var(--xhs-red);
  font-size: 26rpx;
  font-weight: 600;
}

.xhs-follow-btn::after { display: none; }

.xhs-follow-btn.is-followed {
  border-color: #e6e6e6;
  color: var(--xhs-text-sub);
}

/* --- 3. 正文区域 --- */
.article-section {
  padding-bottom: 32rpx;
}

.article-title {
  display: block;
  font-size: 36rpx;
  font-weight: 600;
  color: var(--xhs-text-main);
  line-height: 1.4;
  margin-bottom: 24rpx;
}

.article-content {
  font-size: 30rpx;
  color: var(--xhs-text-main);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.text-paragraph {
  display: inline; /* 配合标签内联显示 */
}

.article-tag {
  display: inline;
  color: var(--xhs-blue);
  font-weight: 500;
  margin-left: 12rpx;
}

.article-meta {
  margin-top: 32rpx;
}

.meta-time {
  font-size: 24rpx;
  color: var(--xhs-text-sub);
}

/* 极细分割线 */
.hairline-divider {
  height: 1rpx;
  background-color: var(--xhs-divider);
  margin: 0 -32rpx; /* 撑满屏幕 */
}

/* --- 4. 评论区 --- */
.comment-section {
  padding: 32rpx 0;
}

.comment-count-title {
  font-size: 28rpx;
  color: var(--xhs-text-sub);
  margin-bottom: 32rpx;
  display: block;
}

.empty-comments {
  display: flex;
  align-items: center;
  padding: 40rpx 0;
}

.empty-avatar-placeholder {
  width: 60rpx;
  height: 60rpx;
  border-radius: 50%;
  background-color: var(--xhs-bg-gray);
  margin-right: 20rpx;
}

.empty-text {
  font-size: 28rpx;
  color: #cccccc;
}

.comment-list {
  display: flex;
  flex-direction: column;
}

.comment-item {
  display: flex;
  margin-bottom: 40rpx;
}

.comment-avatar {
  width: 68rpx;
  height: 68rpx;
  border-radius: 50%;
  background: var(--xhs-bg-gray);
  color: #666;
  font-size: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-right: 20rpx;
}

.comment-main {
  flex: 1;
}

.comment-author-name {
  font-size: 26rpx;
  color: var(--xhs-text-sub);
  margin-bottom: 8rpx;
  display: block;
}

.comment-text-wrap {
  margin-bottom: 16rpx;
}

.comment-text {
  font-size: 28rpx;
  color: var(--xhs-text-main);
  line-height: 1.5;
}

.comment-footer {
  display: flex;
  align-items: center;
}

.comment-time {
  font-size: 24rpx;
  color: var(--xhs-text-sub);
  margin-right: 32rpx;
}

.reply-btn, .delete-btn {
  font-size: 24rpx;
  color: var(--xhs-text-main);
  font-weight: 600;
  margin-right: 32rpx;
}

.delete-btn {
  color: #7b92ab; /* 柔和的删除色 */
}

.comment-like {
  padding-left: 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.like-heart {
  font-size: 32rpx;
  color: var(--xhs-text-sub);
}

/* --- 5. 底部吸底操作栏 --- */
.xhs-bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: #ffffff;
  border-top: 1rpx solid var(--xhs-divider);
  z-index: 999;
  transition: padding-bottom 0.2s ease-out; /* 键盘弹出的过渡动画 */
}

.bar-container {
  display: flex;
  align-items: center;
  height: 100rpx;
  padding: 0 32rpx;
}

.input-box {
  flex: 1;
  height: 72rpx;
  background-color: var(--xhs-bg-gray);
  border-radius: 36rpx;
  display: flex;
  align-items: center;
  padding: 0 28rpx;
  transition: all 0.3s;
}

.edit-icon {
  font-size: 32rpx;
  color: #b3b3b3;
  margin-right: 12rpx;
}

.real-input {
  flex: 1;
  height: 100%;
  font-size: 28rpx;
  color: var(--xhs-text-main);
}

.input-placeholder-style {
  color: #b3b3b3;
}

.send-text {
  font-size: 28rpx;
  color: var(--xhs-blue);
  font-weight: 600;
  padding-left: 20rpx;
}

/* 右侧图标互动区 */
.action-group {
  display: flex;
  align-items: center;
  margin-left: 40rpx;
  gap: 36rpx;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.action-icon {
  font-size: 44rpx;
  line-height: 1;
  color: var(--xhs-text-main);
}

.action-num {
  font-size: 26rpx;
  color: var(--xhs-text-main);
  font-weight: 500;
}

.action-btn.active .action-icon {
  color: var(--xhs-red);
}
.action-btn.active:nth-child(2) .action-icon {
  color: #ffb800; /* 收藏状态黄色 */
}
</style>