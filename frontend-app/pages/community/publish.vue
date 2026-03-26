<template>
  <view class="page">
    <app-page-header
      title="发布动态"
      subtitle="支持最多 3 张图片，把今天的饮食灵感分享出来"
      fallback-url="/pages/community/index"
    >
      <template #right>
        <view class="count-badge">
          <text class="count-badge-text">{{ selectedImages.length }}/3 图</text>
        </view>
      </template>
    </app-page-header>

    <view class="intro-card">
      <text class="intro-title">发一条更完整的分享</text>
      <text class="intro-desc">适合晒一餐、分享做法、记录减脂餐搭配，带图会更直观。</text>
    </view>

    <view class="form-card">
      <text class="field-label">标题</text>
      <input v-model="form.title" class="field" placeholder="一句话概括这次分享（可选）" maxlength="40" />

      <text class="field-label">正文</text>
      <textarea
        v-model="form.content"
        class="textarea"
        maxlength="600"
        placeholder="写下今天吃了什么、怎么做的、为什么想分享..."
      />

      <view class="length-row">
        <text class="helper-text">内容越具体，越容易被大家看到</text>
        <text class="length-text">{{ form.content.length }}/600</text>
      </view>

      <text class="field-label">标签</text>
      <view class="tag-row">
        <view
          v-for="item in tags"
          :key="item"
          class="tag-chip"
          :class="{ active: form.tag === item }"
          @click="form.tag = item"
        >
          <text class="tag-chip-text">{{ item }}</text>
        </view>
      </view>

      <view class="image-head">
        <text class="field-label no-gap">配图</text>
        <text class="helper-text">最多 3 张</text>
      </view>

      <view class="image-grid">
        <view v-for="(item, index) in selectedImages" :key="item.id" class="picked-image-card">
          <image class="picked-image" :src="item.localPath" mode="aspectFill" @click="previewSelected(index)" />
          <view class="remove-badge" @click.stop="removeImage(index)">
            <text class="remove-badge-text">×</text>
          </view>
        </view>

        <view v-if="selectedImages.length < 3" class="picker-card" @click="chooseImages">
          <view class="picker-icon">+</view>
          <text class="picker-title">添加图片</text>
          <text class="picker-desc">相册 / 拍照</text>
        </view>
      </view>
    </view>

    <view class="submit-panel">
      <button class="ghost-button" @click="chooseImages">继续加图</button>
      <button class="publish-button" :disabled="submitting" @click="submitPost">发布内容</button>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import request from '@/utils/request.js'
import { ensureLoggedIn, getProfile } from '@/utils/auth.js'

const tags = ['减脂', '增肌', '素食', '快手菜']

const form = ref(createEmptyForm())
const selectedImages = ref([])
const submitting = ref(false)

function createEmptyForm() {
  return {
    title: '',
    content: '',
    tag: '减脂'
  }
}

async function chooseImages() {
  const remain = 3 - selectedImages.value.length
  if (remain <= 0) {
    uni.showToast({
      title: '最多只能上传 3 张图片',
      icon: 'none'
    })
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
      : tempFiles.map(item => item.path || item.tempFilePath).filter(Boolean)

    if (!imagePaths.length) {
      return
    }

    const nextImages = imagePaths.map((path, index) => ({
      id: `${Date.now()}-${index}-${Math.random().toString(16).slice(2, 8)}`,
      localPath: path,
      file: tempFiles[index]?.file || tempFiles[index] || null
    }))

    selectedImages.value = [...selectedImages.value, ...nextImages].slice(0, 3)
  } catch (error) {
    const message = String(error?.errMsg || error?.message || '')
    if (message.includes('cancel')) {
      return
    }
    console.log('choose community images failed', error)
    uni.showToast({
      title: '选图失败，请稍后重试',
      icon: 'none'
    })
  }
}

function removeImage(index) {
  selectedImages.value.splice(index, 1)
}

function previewSelected(index) {
  const urls = selectedImages.value.map(item => item.localPath)
  if (!urls.length) {
    return
  }
  uni.previewImage({
    current: urls[index],
    urls
  })
}

async function uploadSelectedImages() {
  if (!selectedImages.value.length) {
    return []
  }

  const uploadedUrls = []
  for (const item of selectedImages.value) {
    const response = await request.upload('/community/images', {
      name: 'file',
      filePath: item.localPath,
      file: item.file
    })
    if (response?.url) {
      uploadedUrls.push(response.url)
    }
  }
  return uploadedUrls
}

function goBackToCommunity() {
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  if (Array.isArray(pages) && pages.length > 1) {
    uni.navigateBack({
      delta: 1
    })
    return
  }
  uni.reLaunch({
    url: '/pages/community/index'
  })
}

async function submitPost() {
  if (!ensureLoggedIn() || submitting.value) {
    return
  }

  if (!form.value.content.trim()) {
    uni.showToast({
      title: '内容不能为空',
      icon: 'none'
    })
    return
  }

  submitting.value = true

  try {
    const profile = getProfile() || {}
    const imageUrls = await uploadSelectedImages()

    await request.post('/community/posts', {
      title: form.value.title.trim(),
      content: form.value.content.trim(),
      tag: form.value.tag,
      authorName: profile.nickname || profile.username || '',
      imageUrls
    })

    uni.showToast({
      title: '发布成功',
      icon: 'success'
    })

    setTimeout(() => {
      goBackToCommunity()
    }, 500)
  } catch (error) {
    console.log('submit community post failed', error)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 32rpx 28rpx 80rpx;
}

.count-badge,
.image-head,
.length-row,
.submit-panel {
  display: flex;
  align-items: center;
}

.image-head,
.length-row,
.submit-panel {
  justify-content: space-between;
}

.count-badge {
  min-width: 120rpx;
  height: 72rpx;
  padding: 0 22rpx;
  border-radius: 999rpx;
  background: rgba(14, 165, 109, 0.12);
  justify-content: center;
}

.count-badge-text {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--nm-primary);
}

.intro-card,
.form-card {
  margin-top: 28rpx;
  padding: 28rpx;
  border-radius: 34rpx;
  background: var(--nm-card);
  box-shadow: var(--nm-shadow);
}

.intro-card {
  background: linear-gradient(160deg, #edf9f3 0%, #ffffff 100%);
}

.intro-title,
.field-label {
  display: block;
  font-size: 32rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.intro-desc,
.helper-text,
.length-text,
.picker-desc {
  font-size: 24rpx;
  color: var(--nm-muted);
}

.intro-desc {
  display: block;
  margin-top: 10rpx;
  line-height: 1.72;
}

.field-label {
  margin-top: 24rpx;
}

.field-label.no-gap {
  margin-top: 0;
}

.field,
.textarea {
  width: 100%;
  margin-top: 14rpx;
  border-radius: 24rpx;
  background: #f5f4ef;
  font-size: 28rpx;
  color: var(--nm-text);
}

.field {
  height: 88rpx;
  padding: 0 24rpx;
}

.textarea {
  min-height: 260rpx;
  padding: 24rpx;
}

.length-row {
  margin-top: 12rpx;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
  margin-top: 14rpx;
}

.tag-chip {
  padding: 16rpx 24rpx;
  border-radius: 999rpx;
  background: #f1eee7;
}

.tag-chip.active {
  background: var(--nm-primary);
}

.tag-chip-text {
  font-size: 24rpx;
  font-weight: 700;
  color: #685f50;
}

.tag-chip.active .tag-chip-text {
  color: #ffffff;
}

.image-head {
  margin-top: 28rpx;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14rpx;
  margin-top: 16rpx;
}

.picked-image-card,
.picker-card {
  position: relative;
  height: 200rpx;
  border-radius: 26rpx;
  overflow: hidden;
}

.picked-image {
  width: 100%;
  height: 100%;
  background: #ece8df;
}

.remove-badge {
  position: absolute;
  top: 10rpx;
  right: 10rpx;
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background: rgba(17, 16, 14, 0.72);
  display: flex;
  align-items: center;
  justify-content: center;
}

.remove-badge-text {
  font-size: 28rpx;
  line-height: 1;
  color: #ffffff;
}

.picker-card {
  border: 2rpx dashed rgba(14, 165, 109, 0.2);
  background: linear-gradient(160deg, #f3fbf7 0%, #ffffff 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.picker-icon {
  font-size: 42rpx;
  line-height: 1;
  color: var(--nm-primary);
}

.picker-title {
  margin-top: 10rpx;
  font-size: 26rpx;
  font-weight: 700;
  color: var(--nm-text);
}

.picker-desc {
  margin-top: 8rpx;
}

.submit-panel {
  gap: 16rpx;
  margin-top: 28rpx;
}

.ghost-button,
.publish-button {
  flex: 1;
  height: 92rpx;
  font-size: 28rpx;
  font-weight: 700;
}

.ghost-button {
  background: rgba(255, 255, 255, 0.88);
  color: var(--nm-text);
}

.publish-button {
  background: var(--nm-primary);
  color: #ffffff;
}

.publish-button[disabled] {
  opacity: 0.6;
}
</style>
