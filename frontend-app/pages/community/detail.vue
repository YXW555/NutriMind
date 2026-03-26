<template>
  <view class="page">
    <app-page-header
      title="帖子详情"
      subtitle="查看完整内容，参与评论、点赞和收藏"
      fallback-url="/pages/community/index"
    />

    <view class="post-card">
      <view class="post-head">
        <view>
          <text class="author-name">{{ post.authorName || '知食用户' }}</text>
          <text class="author-meta">{{ formatRelativeTime(post.createdAt) }} · {{ post.tag || '全部' }}</text>
        </view>
        <view class="post-tag">{{ post.tag || '全部' }}</view>
      </view>

      <text v-if="post.title" class="post-title">{{ post.title }}</text>
      <text class="post-content">{{ post.content || '正在加载内容...' }}</text>

      <view v-if="displayImageUrls.length" class="image-grid">
        <image
          v-for="(url, index) in displayImageUrls"
          :key="`${post.id}-${index}`"
          class="post-image"
          mode="aspectFill"
          :src="url"
          @click="previewImages(index)"
        />
      </view>

      <view class="action-row">
        <button class="ghost-button" :class="{ active: post.liked }" @click="toggleLike">
          {{ post.liked ? '已赞' : '点赞' }} {{ post.likeCount || 0 }}
        </button>
        <button class="ghost-button" :class="{ favorite: post.favorited }" @click="toggleFavorite">
          {{ post.favorited ? '已藏' : '收藏' }} {{ post.favoriteCount || 0 }}
        </button>
        <view class="comment-pill">{{ comments.length }} 条评论</view>
      </view>
    </view>

    <view class="comment-panel">
      <view class="section-head">
        <view>
          <text class="section-title">发表评论</text>
          <text class="section-desc">写下这条饮食内容给你的启发，或者补充你的做法。</text>
        </view>
      </view>

      <textarea
        v-model="commentContent"
        class="textarea-field"
        maxlength="300"
        placeholder="输入评论内容"
      ></textarea>

      <button class="primary-button" @click="submitComment">发布评论</button>
    </view>

    <view class="comment-panel">
      <view class="section-head">
        <view>
          <text class="section-title">评论区</text>
          <text class="section-desc">按时间顺序展示，支持删除你自己的评论。</text>
        </view>
      </view>

      <view v-if="!comments.length" class="empty-card">
        <text class="empty-title">还没有评论</text>
        <text class="empty-desc">抢先发第一条评论，和大家一起交流。</text>
      </view>

      <view v-for="item in comments" :key="item.id" class="comment-card">
        <view class="comment-main">
          <view class="comment-top">
            <text class="comment-author">{{ item.authorName || '知食用户' }}</text>
            <text class="comment-time">{{ formatRelativeTime(item.createdAt) }}</text>
          </view>
          <text class="comment-content">{{ item.content }}</text>
        </view>
        <button v-if="item.ownComment" class="danger-button small" @click="deleteComment(item.id)">删除</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { ensureLoggedIn } from '@/utils/auth.js'
import { formatRelativeTime } from '@/utils/format.js'
import { getApiBaseUrl } from '@/utils/config.js'

const postId = ref('')
const post = ref({})
const comments = ref([])
const commentContent = ref('')

const displayImageUrls = computed(() => {
  const imageUrls = Array.isArray(post.value?.imageUrls) ? post.value.imageUrls : []
  return imageUrls.map(resolveAssetUrl)
})

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

function previewImages(index) {
  if (!displayImageUrls.value.length) {
    return
  }
  uni.previewImage({
    current: displayImageUrls.value[index],
    urls: displayImageUrls.value
  })
}

async function loadPostDetail() {
  if (!ensureLoggedIn() || !postId.value) {
    return
  }

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
  try {
    post.value = await request.post(`/community/posts/${postId.value}/like`)
  } catch (error) {
    console.log('detail like failed', error)
  }
}

async function toggleFavorite() {
  try {
    post.value = await request.post(`/community/posts/${postId.value}/favorite`)
  } catch (error) {
    console.log('detail favorite failed', error)
  }
}

async function submitComment() {
  if (!commentContent.value.trim()) {
    uni.showToast({
      title: '请先输入评论内容',
      icon: 'none'
    })
    return
  }

  try {
    await request.post(`/community/posts/${postId.value}/comments`, {
      content: commentContent.value.trim()
    })
    commentContent.value = ''
    loadPostDetail()
  } catch (error) {
    console.log('submit comment failed', error)
  }
}

function deleteComment(commentId) {
  uni.showModal({
    title: '删除评论',
    content: '确认删除这条评论吗？',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      try {
        await request.delete(`/community/comments/${commentId}`)
        loadPostDetail()
      } catch (error) {
        console.log('delete comment failed', error)
      }
    }
  })
}

onLoad((query) => {
  postId.value = query?.id || ''
})

onShow(() => {
  loadPostDetail()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 32rpx 28rpx 80rpx;
}

.post-card,
.comment-panel {
  margin-top: 24rpx;
  padding: 28rpx;
  border-radius: 34rpx;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: var(--nm-shadow);
}

.post-head,
.action-row,
.section-head,
.comment-card,
.comment-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.action-row {
  flex-wrap: wrap;
}

.author-name,
.post-title,
.section-title,
.comment-author,
.empty-title {
  display: block;
  font-size: 34rpx;
  font-weight: 800;
  color: #111827;
}

.author-meta,
.post-content,
.section-desc,
.comment-time,
.comment-content,
.empty-desc {
  font-size: 26rpx;
  color: #64748b;
}

.author-meta,
.section-desc {
  display: block;
  margin-top: 8rpx;
}

.post-tag,
.comment-pill {
  padding: 12rpx 18rpx;
  border-radius: 999rpx;
  background: #f1eee7;
  font-size: 24rpx;
  color: #685f50;
}

.post-title {
  margin-top: 22rpx;
}

.post-content,
.comment-content,
.empty-desc {
  display: block;
  margin-top: 14rpx;
  line-height: 1.8;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12rpx;
  margin-top: 22rpx;
}

.post-image {
  width: 100%;
  height: 260rpx;
  border-radius: 24rpx;
  background: #ece8df;
}

.ghost-button,
.primary-button,
.danger-button {
  height: 78rpx;
  border-radius: 24rpx;
  font-size: 26rpx;
  font-weight: 700;
}

.ghost-button {
  min-width: 150rpx;
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

.textarea-field {
  width: 100%;
  box-sizing: border-box;
  min-height: 180rpx;
  margin-top: 18rpx;
  padding: 22rpx 24rpx;
  border-radius: 24rpx;
  background: #f5f4ef;
  font-size: 28rpx;
  color: #111827;
}

.primary-button {
  margin-top: 18rpx;
  background: var(--nm-primary-dark);
  color: #ffffff;
}

.empty-card,
.comment-card {
  margin-top: 18rpx;
  padding: 22rpx;
  border-radius: 24rpx;
  background: #f8fafc;
}

.comment-main {
  flex: 1;
  min-width: 0;
}

.danger-button {
  background: #fee2e2;
  color: #991b1b;
}

.danger-button.small {
  min-width: 120rpx;
  height: 72rpx;
  font-size: 24rpx;
}
</style>
