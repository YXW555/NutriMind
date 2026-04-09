<template>
  <view class="page">
    <app-page-header
      title="我的帖子"
      subtitle="查看、编辑和删除你自己发布的内容"
      fallback-url="/pages/profile/index"
    >
      <template #right>
        <button class="header-button" @click="goPublish">新建</button>
      </template>
    </app-page-header>

    <view v-if="!posts.length" class="empty-card">
      <text class="empty-title">你还没有发布过帖子</text>
      <text class="empty-desc">先分享一顿饮食、一个做法，或者一段健康管理心得。</text>
      <button class="primary-button empty-button" @click="goPublish">去发布</button>
    </view>

    <view v-else>
      <view v-for="post in posts" :key="post.id" class="post-card">
        <view class="post-top">
          <view class="post-main" @click="goDetail(post.id)">
            <text class="post-title">{{ post.title || post.content }}</text>
            <text class="post-meta">
              {{ post.tag || '全部' }} · {{ formatRelativeTime(post.updatedAt || post.createdAt) }} · {{ statusLabel(post.moderationStatus) }}
            </text>
          </view>
          <view class="post-actions">
            <button class="ghost-button small-button" @click="editPost(post.id)">编辑</button>
            <button class="danger-button small-button" @click="deletePost(post.id)">删除</button>
          </view>
        </view>

        <text class="post-desc">{{ post.content }}</text>

        <view v-if="Array.isArray(post.imageUrls) && post.imageUrls.length" class="image-row">
          <image
            v-for="(url, index) in post.imageUrls.slice(0, 3)"
            :key="`${post.id}-${index}`"
            class="thumb"
            mode="aspectFill"
            :src="resolveAssetUrl(url)"
          />
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { ensureLoggedIn } from '@/utils/auth.js'
import { formatRelativeTime } from '@/utils/format.js'
import { getApiBaseUrl } from '@/utils/config.js'

const posts = ref([])

function resolveAssetUrl(url) {
  if (!url) return ''
  if (/^https?:\/\//i.test(url)) return url
  const apiBase = getApiBaseUrl().replace(/\/+$/, '')
  const origin = apiBase.replace(/\/api$/i, '')
  return url.startsWith('/') ? `${origin}${url}` : `${origin}/${url}`
}

function statusLabel(status) {
  if (!status) return '待处理'
  if (status === 'APPROVED') return '已发布'
  if (status === 'REJECTED') return '已驳回'
  return '待审核'
}

async function loadPosts() {
  if (!ensureLoggedIn()) return
  try {
    const response = await request.get('/community/posts/mine')
    posts.value = Array.isArray(response) ? response : []
  } catch (error) {
    console.log('load my posts failed', error)
  }
}

function goPublish() {
  uni.navigateTo({ url: '/pages/community/publish' })
}

function editPost(id) {
  uni.navigateTo({ url: `/pages/community/publish?id=${id}` })
}

function goDetail(id) {
  uni.navigateTo({ url: `/pages/community/detail?id=${id}` })
}

function deletePost(id) {
  uni.showModal({
    title: '删除帖子',
    content: '确认删除这条帖子吗？删除后不可恢复。',
    success: async (result) => {
      if (!result.confirm) return
      try {
        await request.delete(`/community/posts/${id}`)
        posts.value = posts.value.filter(item => item.id !== id)
      } catch (error) {
        console.log('delete my post failed', error)
      }
    }
  })
}

onShow(() => {
  loadPosts()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 32rpx 28rpx 80rpx;
}

.header-button {
  min-width: 120rpx;
  height: 72rpx;
  border-radius: 999rpx;
  background: rgba(14, 165, 109, 0.12);
  color: var(--nm-primary);
  font-size: 24rpx;
  font-weight: 700;
}

.empty-card,
.post-card {
  margin-top: 24rpx;
  padding: 28rpx;
  border-radius: 32rpx;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: var(--nm-shadow);
}

.empty-title,
.post-title {
  display: block;
  font-size: 34rpx;
  font-weight: 800;
  color: #111827;
}

.empty-desc,
.post-meta,
.post-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 1.7;
  color: #64748b;
}

.empty-button {
  margin-top: 24rpx;
}

.post-top {
  display: flex;
  justify-content: space-between;
  gap: 18rpx;
}

.post-main {
  flex: 1;
  min-width: 0;
}

.post-actions {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.small-button {
  width: 132rpx;
  height: 68rpx;
  font-size: 24rpx;
}

.image-row {
  display: flex;
  gap: 12rpx;
  margin-top: 18rpx;
}

.thumb {
  width: 160rpx;
  height: 160rpx;
  border-radius: 20rpx;
  background: #ece8df;
}
</style>
