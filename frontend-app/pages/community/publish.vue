<template>
  <view class="page-xhs">
    <app-page-header
      :title="pageTitle"
      fallback-url="/pages/community/index"
    >
      <template #right>
        <button
          class="xhs-publish-btn"
          :class="{ 'is-active': canSubmit, 'is-loading': submitting }"
          :disabled="!canSubmit || submitting || loadingPost"
          @click="submitPost"
        >
          {{ submitButtonText }}
        </button>
      </template>
    </app-page-header>

    <view v-if="loadingPost" class="loading-state">
      <text class="loading-title">正在加载帖子内容</text>
      <text class="loading-desc">请稍候，我们正在为你准备编辑内容。</text>
    </view>

    <view v-else class="editor-body">
      <view class="text-section">
        <input
          v-model="form.title"
          class="xhs-title-input"
          placeholder="写一个更容易被看到的标题"
          placeholder-class="xhs-placeholder-title"
          maxlength="40"
        />

        <view class="content-wrapper">
          <textarea
            v-model="form.content"
            class="xhs-content-textarea"
            maxlength="600"
            :auto-height="true"
            placeholder="分享你的饮食记录、做法经验或健康心得"
            placeholder-class="xhs-placeholder-content"
          />
        </view>
      </view>

      <view class="media-section">
        <scroll-view scroll-x class="media-scroll" :show-scrollbar="false">
          <view class="media-list">
            <view
              v-for="(item, index) in selectedImages"
              :key="item.id"
              class="media-item"
            >
              <image
                class="media-img"
                :src="item.localPath"
                mode="aspectFill"
                @click="previewSelected(index)"
              />
              <view class="media-delete" @click.stop="removeImage(index)">
                <text class="delete-icon">×</text>
              </view>
              <view v-if="item.uploadedUrl" class="media-badge">
                <text class="media-badge-text">{{ item.isExisting ? '原图' : '新图' }}</text>
              </view>
            </view>

            <view v-if="selectedImages.length < 3" class="media-add-btn" @click="chooseImages">
              <text class="add-icon">+</text>
            </view>
          </view>
        </scroll-view>

        <view class="image-tip">
          <text class="tip-icon">📷</text>
          <text class="tip-text">
            {{ isEditMode ? '可保留原图，也可追加或删除图片，最多 3 张。' : '添加图片最多 3 张，让分享更完整。' }}
          </text>
        </view>
      </view>

      <view class="options-section">
        <view class="section-head">
          <text class="section-title">话题标签</text>
          <text class="section-subtitle">选择一个更适合当前内容的主题</text>
        </view>

        <view class="quick-tags">
          <view
            v-for="tag in tags"
            :key="tag"
            class="xhs-tag-chip"
            :class="{ 'is-selected': form.tag === tag }"
            @click="toggleTag(tag)"
          >
            <text class="hash-mark">#</text>
            <text class="tag-text">{{ tag }}</text>
          </view>
        </view>

        <view class="editor-tips">
          <text class="tips-title">{{ isEditMode ? '编辑说明' : '发布建议' }}</text>
          <text class="tips-line">
            {{ isEditMode ? '保存后会覆盖原帖子内容，但点赞、评论和收藏数据会继续保留。' : '标题可以为空，但建议补充关键信息，方便其他人理解内容。' }}
          </text>
          <text class="tips-line">支持上传 0 到 3 张图片；不选标签时会自动归类到“全部”。</text>
        </view>
      </view>
    </view>

    <view class="safe-bottom"></view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { ensureLoggedIn, getProfile } from '@/utils/auth.js'
import { getApiBaseUrl } from '@/utils/config.js'

const tags = ['健康饮食', '我的私藏菜谱', '打卡', '减脂餐分享', '神仙吃法']

const postId = ref('')
const loadingPost = ref(false)
const submitting = ref(false)

const form = ref({
  title: '',
  content: '',
  tag: ''
})

const selectedImages = ref([])

const isEditMode = computed(() => Boolean(postId.value))
const pageTitle = computed(() => (isEditMode.value ? '编辑帖子' : '发布图文'))
const submitButtonText = computed(() => {
  if (loadingPost.value) return '加载中...'
  if (submitting.value) return isEditMode.value ? '保存中...' : '发布中...'
  return isEditMode.value ? '保存' : '发布'
})

const canSubmit = computed(() => {
  return form.value.content.trim().length > 0 || selectedImages.value.length > 0
})

function resolveAssetUrl(url) {
  if (!url) return ''
  if (/^https?:\/\//i.test(url)) return url
  const apiBase = getApiBaseUrl().replace(/\/+$/, '')
  const origin = apiBase.replace(/\/api$/i, '')
  return url.startsWith('/') ? `${origin}${url}` : `${origin}/${url}`
}

function makeImageItem({ localPath, uploadedUrl = '', file = null, isExisting = false }) {
  return {
    id: `${Date.now()}-${Math.random().toString(16).slice(2, 10)}`,
    localPath,
    uploadedUrl,
    file,
    isExisting
  }
}

async function loadPostDetail() {
  if (!isEditMode.value || !ensureLoggedIn()) return
  loadingPost.value = true
  try {
    const response = await request.get(`/community/posts/${postId.value}`)
    form.value = {
      title: response?.title || '',
      content: response?.content || '',
      tag: response?.tag && response.tag !== '全部' ? response.tag : ''
    }
    selectedImages.value = Array.isArray(response?.imageUrls)
      ? response.imageUrls.map((url) =>
          makeImageItem({
            localPath: resolveAssetUrl(url),
            uploadedUrl: url,
            isExisting: true
          })
        )
      : []
  } catch (error) {
    console.log('load post detail for edit failed', error)
    uni.showToast({ title: '帖子加载失败，请稍后重试', icon: 'none' })
    setTimeout(() => {
      goBackToCommunity()
    }, 500)
  } finally {
    loadingPost.value = false
  }
}

async function chooseImages() {
  const remain = 3 - selectedImages.value.length
  if (remain <= 0) {
    uni.showToast({ title: '最多只能上传 3 张图片', icon: 'none' })
    return
  }

  try {
    const result = await uni.chooseImage({
      count: remain,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera']
    })

    const tempFiles = Array.isArray(result?.tempFiles) ? result.tempFiles : []
    const imagePaths = Array.isArray(result?.tempFilePaths)
      ? result.tempFilePaths
      : tempFiles.map((item) => item.path || item.tempFilePath).filter(Boolean)

    if (!imagePaths.length) return

    const nextImages = imagePaths.map((path, index) =>
      makeImageItem({
        localPath: path,
        file: tempFiles[index]?.file || tempFiles[index] || null,
        isExisting: false
      })
    )

    selectedImages.value = [...selectedImages.value, ...nextImages].slice(0, 3)
  } catch (error) {
    const message = String(error?.errMsg || error?.message || '')
    if (message.includes('cancel')) return
    uni.showToast({ title: '选择图片失败，请稍后重试', icon: 'none' })
  }
}

function removeImage(index) {
  selectedImages.value.splice(index, 1)
}

function previewSelected(index) {
  const urls = selectedImages.value.map((item) => item.localPath)
  if (!urls.length) return
  uni.previewImage({
    current: urls[index],
    urls
  })
}

function toggleTag(tag) {
  form.value.tag = form.value.tag === tag ? '' : tag
}

async function uploadSelectedImages() {
  if (!selectedImages.value.length) return []
  const uploadedUrls = []

  for (const item of selectedImages.value) {
    if (item.uploadedUrl) {
      uploadedUrls.push(item.uploadedUrl)
      continue
    }

    const response = await request.upload('/community/images', {
      name: 'file',
      filePath: item.localPath,
      file: item.file
    })

    if (response?.url) {
      item.uploadedUrl = response.url
      uploadedUrls.push(response.url)
    }
  }

  return uploadedUrls
}

function buildPayload(imageUrls) {
  const profile = getProfile() || {}
  return {
    title: form.value.title.trim(),
    content: form.value.content.trim(),
    tag: form.value.tag,
    authorName: profile.nickname || profile.username || '',
    imageUrls
  }
}

function goBackToCommunity() {
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  if (Array.isArray(pages) && pages.length > 1) {
    uni.navigateBack({ delta: 1 })
    return
  }
  uni.reLaunch({ url: '/pages/community/index' })
}

async function submitPost() {
  if (!ensureLoggedIn() || submitting.value || loadingPost.value || !canSubmit.value) return

  if (!form.value.content.trim() && !selectedImages.value.length) {
    uni.showToast({ title: '写点内容再发布吧', icon: 'none' })
    return
  }

  submitting.value = true
  try {
    const imageUrls = await uploadSelectedImages()
    const payload = buildPayload(imageUrls)

    if (isEditMode.value) {
      await request.put(`/community/posts/${postId.value}`, payload)
    } else {
      await request.post('/community/posts', payload)
    }

    uni.showToast({
      title: isEditMode.value ? '帖子已更新' : '发布成功',
      icon: 'success'
    })

    setTimeout(() => {
      goBackToCommunity()
    }, 600)
  } catch (error) {
    console.log('submit post failed', error)
    uni.showToast({
      title: isEditMode.value ? '保存失败，请稍后重试' : '发布失败，请稍后重试',
      icon: 'none'
    })
  } finally {
    submitting.value = false
  }
}

onLoad((query) => {
  postId.value = String(query?.id || '').trim()
  loadPostDetail()
})
</script>

<style scoped>
.page-xhs {
  --xhs-red: #ff2442;
  --xhs-red-light: rgba(255, 36, 66, 0.1);
  --xhs-bg: #ffffff;
  --xhs-text: #333333;
  --xhs-muted: #999999;
  --xhs-border: #f1f5f9;
  min-height: 100vh;
  background-color: var(--xhs-bg);
  padding: 0 0 60rpx;
}

.xhs-publish-btn {
  margin: 0;
  padding: 0 30rpx;
  height: 64rpx;
  line-height: 64rpx;
  border-radius: 32rpx;
  background-color: #f5f5f5;
  color: #c1c7d0;
  font-size: 28rpx;
  font-weight: 700;
  border: none;
}

.xhs-publish-btn::after {
  display: none;
}

.xhs-publish-btn.is-active {
  background-color: var(--xhs-red);
  color: #ffffff;
}

.xhs-publish-btn.is-loading {
  opacity: 0.72;
}

.loading-state {
  padding: 80rpx 40rpx;
}

.loading-title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: var(--xhs-text);
}

.loading-desc {
  display: block;
  margin-top: 16rpx;
  font-size: 28rpx;
  line-height: 1.7;
  color: var(--xhs-muted);
}

.editor-body {
  padding: 16rpx 0 0;
}

.text-section {
  padding: 0 32rpx;
}

.xhs-title-input {
  width: 100%;
  height: 110rpx;
  font-size: 36rpx;
  font-weight: 700;
  color: var(--xhs-text);
  border-bottom: 1rpx solid var(--xhs-border);
  margin-bottom: 24rpx;
}

.xhs-placeholder-title {
  color: #b6beca;
}

.content-wrapper {
  min-height: 320rpx;
}

.xhs-content-textarea {
  width: 100%;
  min-height: 320rpx;
  font-size: 30rpx;
  line-height: 1.8;
  color: var(--xhs-text);
}

.xhs-placeholder-content {
  color: #b6beca;
}

.media-section,
.options-section {
  margin-top: 36rpx;
  padding: 0 32rpx;
}

.media-scroll {
  width: 100%;
}

.media-list {
  display: inline-flex;
  align-items: center;
  gap: 18rpx;
  padding-bottom: 12rpx;
}

.media-item,
.media-add-btn {
  position: relative;
  width: 210rpx;
  height: 210rpx;
  border-radius: 24rpx;
  overflow: hidden;
  background: #f8fafc;
  border: 1rpx solid var(--xhs-border);
}

.media-add-btn {
  display: flex;
  align-items: center;
  justify-content: center;
}

.add-icon {
  font-size: 72rpx;
  color: #94a3b8;
  line-height: 1;
}

.media-img {
  width: 100%;
  height: 100%;
}

.media-delete {
  position: absolute;
  top: 12rpx;
  right: 12rpx;
  width: 46rpx;
  height: 46rpx;
  border-radius: 50%;
  background: rgba(15, 23, 42, 0.72);
  display: flex;
  align-items: center;
  justify-content: center;
}

.delete-icon {
  color: #ffffff;
  font-size: 30rpx;
  line-height: 1;
}

.media-badge {
  position: absolute;
  left: 12rpx;
  bottom: 12rpx;
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(15, 23, 42, 0.72);
}

.media-badge-text {
  color: #ffffff;
  font-size: 22rpx;
}

.image-tip {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 18rpx;
}

.tip-icon {
  font-size: 28rpx;
}

.tip-text {
  flex: 1;
  font-size: 24rpx;
  line-height: 1.6;
  color: #64748b;
}

.section-head {
  margin-bottom: 22rpx;
}

.section-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #111827;
}

.section-subtitle {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #64748b;
}

.quick-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 18rpx;
}

.xhs-tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  padding: 16rpx 22rpx;
  border-radius: 999rpx;
  background: #f8fafc;
  border: 1rpx solid #e2e8f0;
}

.xhs-tag-chip.is-selected {
  background: var(--xhs-red-light);
  border-color: rgba(255, 36, 66, 0.25);
}

.hash-mark,
.tag-text {
  font-size: 24rpx;
  color: #475569;
}

.xhs-tag-chip.is-selected .hash-mark,
.xhs-tag-chip.is-selected .tag-text {
  color: var(--xhs-red);
}

.editor-tips {
  margin-top: 26rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  background: #f8fafc;
  border: 1rpx solid #edf2f7;
}

.tips-title {
  display: block;
  font-size: 26rpx;
  font-weight: 700;
  color: #111827;
}

.tips-line {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #64748b;
}

.safe-bottom {
  height: calc(env(safe-area-inset-bottom) + 24rpx);
}
</style>
