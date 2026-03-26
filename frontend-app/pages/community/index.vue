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
  padding: 32rpx 28rpx 220rpx;
}

.search-card,
.empty-card,
.post-card {
  margin-top: 28rpx;
  border-radius: 34rpx;
  background: var(--nm-card);
  box-shadow: var(--nm-shadow);
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
  min-width: 120rpx;
  height: 72rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  background: rgba(14, 165, 109, 0.14);
  display: flex;
  align-items: center;
  justify-content: center;
}

.publish-button-text {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--nm-primary);
}

.search-input {
  flex: 1;
  height: 88rpx;
  padding: 0 24rpx;
  border-radius: 24rpx;
  background: #f5f4ef;
  font-size: 28rpx;
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
  background: var(--nm-primary-dark);
  color: #ffffff;
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
  background: #f1eee7;
}

.tag-chip.active {
  background: var(--nm-primary);
}

.tag-chip-text,
.top-tag-text {
  font-size: 24rpx;
  font-weight: 700;
  color: #685f50;
}

.tag-chip.active .tag-chip-text {
  color: #ffffff;
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
  background: #ece8df;
  display: flex;
  align-items: center;
  justify-content: center;
}

.author-avatar-text {
  font-size: 30rpx;
  font-weight: 700;
  color: #8b826f;
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
  color: #5f594d;
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
  border-radius: 24rpx;
  background: #ece8df;
}

.post-image-grid.count-1 .post-image {
  height: 360rpx;
}

.post-cover {
  height: 260rpx;
  margin-top: 22rpx;
  padding: 24rpx;
  border-radius: 30rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.cover-green {
  background: linear-gradient(135deg, #d4f3e4 0%, #89d4af 100%);
}

.cover-blue {
  background: linear-gradient(135deg, #dfeaff 0%, #91b7ff 100%);
}

.cover-warm {
  background: linear-gradient(135deg, #f6ead7 0%, #dfc4a1 100%);
}

.cover-gold {
  background: linear-gradient(135deg, #f8edc6 0%, #f1c168 100%);
}

.post-cover-tag {
  align-self: flex-start;
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.64);
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
  border-radius: 999rpx;
  background: #f4f1ea;
  color: #5f594d;
}

.ghost-button.active {
  background: rgba(14, 165, 109, 0.14);
  color: var(--nm-primary);
}

.ghost-button.favorite {
  background: rgba(245, 158, 11, 0.14);
  color: #b45309;
}
</style>
