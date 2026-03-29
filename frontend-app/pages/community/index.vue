<template>
  <view class="page">
    <app-page-header
      title="食刻社区"
      subtitle="看看大家最近怎么吃，也把你的记录和心得发出来"
      fallback-url="/pages/index/index"
    >
      <template #right>
        <view class="publish-button" @click="goPublish">
          <text class="publish-button-text">发布</text>
        </view>
      </template>
    </app-page-header>

    <view class="search-card">
      <view class="search-row">
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索帖子、作者、关键词"
          confirm-type="search"
          @confirm="loadPosts"
        />
        <button class="search-button" @click="loadPosts">搜索</button>
      </view>

      <scroll-view scroll-x class="tag-scroll" show-scrollbar="false">
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
      <text class="empty-title">还没有找到相关内容</text>
      <text class="empty-desc">你可以先发布第一条饮食分享，或者换个标签看看。</text>
      <button class="empty-action" @click="goPublish">去发布内容</button>
    </view>

    <view v-for="post in posts" :key="post.id" class="post-card">
      <view class="post-main" @click="goDetail(post)">
        <view class="post-head">
          <view class="post-author-row">
            <view class="author-avatar">
              <text class="author-avatar-text">{{ (post.authorName || '知').slice(0, 1) }}</text>
            </view>
            <view class="author-main">
              <text class="author-name">{{ post.authorName || '知食用户' }}</text>
              <text class="author-time">{{ formatRelativeTime(post.createdAt) }}</text>
            </view>
          </view>

          <view class="top-tag">
            <text class="top-tag-text">{{ post.tag || '全部' }}</text>
          </view>
        </view>

        <text v-if="post.title" class="post-title">{{ post.title }}</text>
        <text class="post-content">{{ post.content }}</text>

        <view
          v-if="post.displayImageUrls.length"
          class="post-image-grid"
          :class="`count-${Math.min(post.displayImageUrls.length, 3)}`"
        >
          <image
            v-for="(url, index) in post.displayImageUrls"
            :key="`${post.id}-${index}`"
            class="post-image"
            mode="aspectFill"
            :src="url"
            @click.stop="previewImages(post, index)"
          />
        </view>

        <view v-else class="post-cover" :class="coverClass(post.tag)">
          <text class="post-cover-tag">{{ post.tag || '全部' }}</text>
          <text class="post-cover-title">{{ post.title || '今日饮食分享' }}</text>
        </view>
      </view>

      <view class="post-footer">
        <view class="footer-stat">{{ post.commentCount || 0 }} 条评论</view>
        <view class="post-actions">
          <button class="ghost-button" :class="{ active: post.liked }" @click.stop="toggleLike(post)">
            {{ post.liked ? '已赞' : '点赞' }} {{ post.likeCount || 0 }}
          </button>
          <button class="ghost-button" :class="{ favorite: post.favorited }" @click.stop="toggleFavorite(post)">
            {{ post.favorited ? '已藏' : '收藏' }} {{ post.favoriteCount || 0 }}
          </button>
        </view>
      </view>
    </view>

    <app-tab-bar current="community" />
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { getApiBaseUrl } from '@/utils/config.js'
import { ensureLoggedIn } from '@/utils/auth.js'
import { formatRelativeTime } from '@/utils/format.js'

const tags = ['全部', '减脂', '增肌', '素食', '快手菜']
const keyword = ref('')
const activeTag = ref('全部')
const posts = ref([])

function coverClass(tag) {
  const map = {
    减脂: 'cover-green',
    增肌: 'cover-blue',
    素食: 'cover-warm',
    快手菜: 'cover-gold'
  }
  return map[tag] || 'cover-green'
}

function goPublish() {
  uni.navigateTo({
    url: '/pages/community/publish'
  })
}

function goDetail(post) {
  uni.navigateTo({
    url: `/pages/community/detail?id=${post.id}`
  })
}

function changeTag(tag) {
  activeTag.value = tag
  loadPosts()
}

function resolveAssetUrl(url) {
  if (!url) {
    return ''
  }
  if (/^https?:\/\//i.test(url)) {
    return url
  }
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

function previewImages(post, index) {
  if (!post.displayImageUrls.length) {
    return
  }
  uni.previewImage({
    current: post.displayImageUrls[index],
    urls: post.displayImageUrls
  })
}

async function loadPosts() {
  if (!ensureLoggedIn()) {
    return
  }

  try {
    const response = await request.get('/community/posts', {
      keyword: keyword.value,
      tag: activeTag.value === '全部' ? '' : activeTag.value,
      current: 1,
      size: 30
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

async function toggleFavorite(post) {
  try {
    const response = await request.post(`/community/posts/${post.id}/favorite`)
    posts.value = posts.value.map(item => (item.id === post.id ? normalizePost(response) : item))
  } catch (error) {
    console.log('toggle favorite failed', error)
  }
}

onShow(() => {
  loadPosts()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 32rpx 24rpx 176rpx;
}

.search-card,
.empty-card,
.post-card {
  margin-top: 28rpx;
  border-radius: 22rpx;
  background: var(--nm-card);
  box-shadow: var(--nm-shadow);
  border: 1rpx solid var(--nm-line);
}

.search-card,
.empty-card {
  padding: 28rpx;
}

.search-row,
.post-head,
.post-author-row,
.post-footer,
.post-actions {
  display: flex;
  align-items: center;
}

.search-row,
.post-head,
.post-footer {
  justify-content: space-between;
}

.publish-button {
  min-width: 96rpx;
  height: 64rpx;
  padding: 0 24rpx;
  border-radius: 16rpx;
  background: var(--nm-card-soft);
  border: 1rpx solid var(--nm-line);
  display: flex;
  align-items: center;
  justify-content: center;
}

.publish-button-text {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--nm-primary);
}

.search-input {
  flex: 1;
  height: 88rpx;
  padding: 0 24rpx;
  border-radius: 18rpx;
  background: #f1f8f3;
  font-size: 28rpx;
  color: var(--nm-text);
}

.search-button,
.empty-action,
.ghost-button {
  font-size: 26rpx;
  font-weight: 700;
}

.search-button,
.empty-action {
  height: 88rpx;
  background: var(--nm-primary);
  color: #ffffff;
  border-radius: 18rpx;
}

.search-button {
  width: 148rpx;
}

.tag-scroll {
  margin-top: 22rpx;
  white-space: nowrap;
}

.tag-row {
  display: inline-flex;
  gap: 14rpx;
}

.tag-chip,
.top-tag {
  padding: 14rpx 22rpx;
  border-radius: 999rpx;
  background: var(--nm-card-soft);
}

.tag-chip.active {
  background: rgba(47, 125, 107, 0.12);
}

.tag-chip-text,
.top-tag-text {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--nm-muted);
}

.tag-chip.active .tag-chip-text {
  color: var(--nm-primary);
}

.empty-title,
.author-name,
.post-title {
  display: block;
  font-size: 34rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.empty-desc,
.author-time,
.post-content,
.footer-stat {
  font-size: 26rpx;
  color: var(--nm-muted);
}

.empty-desc {
  display: block;
  margin-top: 12rpx;
  line-height: 1.7;
}

.empty-action {
  width: 100%;
  margin-top: 22rpx;
}

.post-card {
  overflow: hidden;
  padding: 24rpx;
}

.post-author-row {
  gap: 16rpx;
}

.author-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: var(--nm-card-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}

.author-avatar-text {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--nm-muted);
}

.author-main {
  min-width: 0;
}

.author-time {
  display: block;
  margin-top: 6rpx;
}

.post-title {
  margin-top: 22rpx;
}

.post-content {
  display: block;
  margin-top: 14rpx;
  line-height: 1.8;
  color: var(--nm-text);
}

.post-image-grid {
  display: grid;
  gap: 12rpx;
  margin-top: 22rpx;
}

.post-image-grid.count-1 {
  grid-template-columns: 1fr;
}

.post-image-grid.count-2,
.post-image-grid.count-3 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.post-image {
  width: 100%;
  height: 240rpx;
  border-radius: 18rpx;
  background: var(--nm-card-soft);
}

.post-image-grid.count-1 .post-image {
  height: 360rpx;
}

.post-cover {
  height: 260rpx;
  margin-top: 22rpx;
  padding: 24rpx;
  border-radius: 18rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.cover-green {
  background: #e7f4ea;
}

.cover-blue {
  background: #ebf3fb;
}

.cover-warm {
  background: #f8f1e5;
}

.cover-gold {
  background: #f6efd9;
}

.post-cover-tag {
  align-self: flex-start;
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.72);
  font-size: 24rpx;
  font-weight: 700;
  color: var(--nm-text);
}

.post-cover-title {
  font-size: 40rpx;
  line-height: 1.35;
  font-weight: 800;
  color: rgba(24, 23, 17, 0.9);
}

.post-footer {
  margin-top: 20rpx;
}

.post-actions {
  gap: 12rpx;
}

.ghost-button {
  min-width: 140rpx;
  height: 72rpx;
  padding: 0 22rpx;
  border-radius: 16rpx;
  background: var(--nm-card-soft);
  color: var(--nm-text);
}

.ghost-button.active {
  background: rgba(47, 125, 107, 0.12);
  color: var(--nm-primary);
}

.ghost-button.favorite {
  background: rgba(183, 121, 31, 0.12);
  color: var(--nm-orange);
}
</style>
