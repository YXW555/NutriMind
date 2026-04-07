<template>
  <view class="page-xhs">
    <app-page-header
      title="发布图文"
      fallback-url="/pages/community/index"
      :border="false"
    >
      <template #right>
        <button 
          class="xhs-publish-btn" 
          :class="{ 'is-active': canPublish, 'is-loading': submitting }"
          :disabled="!canPublish || submitting"
          @click="submitPost"
        >
          {{ submitting ? '发布中...' : '发布' }}
        </button>
      </template>
    </app-page-header>

    <view class="editor-body">
      
      <view class="text-section">
        <input 
          v-model="form.title" 
          class="xhs-title-input" 
          placeholder="填写标题会有更多赞哦~" 
          placeholder-class="xhs-placeholder-title"
          maxlength="40" 
        />
        
        <view class="content-wrapper">
          <textarea
            v-model="form.content"
            class="xhs-content-textarea"
            maxlength="600"
            :auto-height="true"
            placeholder="添加正文"
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
              <image class="media-img" :src="item.localPath" mode="aspectFill" @click="previewSelected(index)" />
              <view class="media-delete" @click.stop="removeImage(index)">
                <text class="delete-icon">×</text>
              </view>
            </view>
            
            <view v-if="selectedImages.length < 3" class="media-add-btn" @click="chooseImages">
              <text class="add-icon">+</text>
            </view>
          </view>
        </scroll-view>
        <view v-if="selectedImages.length === 0" class="image-tip">
          <text class="xhs-tag-icon">🖼️</text>
          <text class="tip-text">添加图片 (最多3张)</text>
        </view>
      </view>

      <view class="options-section">
        
        <view class="quick-tags">
          <view 
            v-for="t in tags" 
            :key="t" 
            class="xhs-tag-chip" 
            :class="{ 'is-selected': form.tag === t }"
            @click="form.tag = (form.tag === t ? '' : t)"
          >
            <text class="hash-mark">#</text>
            <text class="tag-text">{{ t }}</text>
          </view>
        </view>

        <view class="xhs-cell">
          <view class="cell-left">
            <text class="cell-icon">📍</text>
            <text class="cell-title">发布分区</text>
          </view>
          </view>
        
        <view class="partition-chips">
          <view 
            v-for="p in partitions" 
            :key="p" 
            class="partition-chip" 
            :class="{ active: form.partition === p }"
            @click="form.partition = p"
          >
            {{ p }}
          </view>
        </view>

      </view>
    </view>
    
    <view class="safe-bottom"></view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import request from '@/utils/request.js'
import { ensureLoggedIn, getProfile } from '@/utils/auth.js'

// --- 数据配置 ---
const partitions = ['减脂', '增肌', '素食', '快手菜']
const tags = ['健康饮食', '我的私藏菜谱', '打卡', '减脂餐分享', '神仙吃法']

// --- 状态管理 ---
const form = ref({
  title: '',
  content: '',
  tag: '',
  partition: '减脂' // 默认选中分区
})
const selectedImages = ref([])
const submitting = ref(false)

// --- 计算属性 ---
const canPublish = computed(() => {
  return form.value.content.trim().length > 0 || selectedImages.value.length > 0
})

// --- 图片处理逻辑 ---
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
      : tempFiles.map(item => item.path || item.tempFilePath).filter(Boolean)

    if (!imagePaths.length) return

    const nextImages = imagePaths.map((path, index) => ({
      id: `${Date.now()}-${index}-${Math.random().toString(16).slice(2, 8)}`,
      localPath: path,
      file: tempFiles[index]?.file || tempFiles[index] || null
    }))

    selectedImages.value = [...selectedImages.value, ...nextImages].slice(0, 3)
  } catch (error) {
    const message = String(error?.errMsg || error?.message || '')
    if (message.includes('cancel')) return
    uni.showToast({ title: '选图取消', icon: 'none' })
  }
}

function removeImage(index) {
  selectedImages.value.splice(index, 1)
}

function previewSelected(index) {
  const urls = selectedImages.value.map(item => item.localPath)
  if (!urls.length) return
  uni.previewImage({ current: urls[index], urls })
}

async function uploadSelectedImages() {
  if (!selectedImages.value.length) return []

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
    uni.navigateBack({ delta: 1 })
    return
  }
  uni.reLaunch({ url: '/pages/community/index' })
}

// --- 发布逻辑 ---
async function submitPost() {
  if (!ensureLoggedIn() || submitting.value || !canPublish.value) return

  if (!form.value.content.trim() && !selectedImages.value.length) {
    uni.showToast({ title: '写点内容再发布吧', icon: 'none' })
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
      partition: form.value.partition,
      authorName: profile.nickname || profile.username || '',
      imageUrls
    })

    uni.showToast({ title: '发布成功', icon: 'success' })

    setTimeout(() => {
      goBackToCommunity()
    }, 1500)
  } catch (error) {
    uni.showToast({ title: '发布失败，请重试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.page-xhs {
  /* 小红书风格的色彩变量 */
  --xhs-red: #ff2442;
  --xhs-red-light: rgba(255, 36, 66, 0.1);
  --xhs-bg: #ffffff;
  --xhs-text: #333333;
  --xhs-muted: #999999;
  --xhs-border: #f5f5f5;
  
  min-height: 100vh;
  background-color: var(--xhs-bg);
}

/* 顶部导航栏的药丸发布按钮 */
.xhs-publish-btn {
  margin: 0;
  padding: 0 32rpx;
  height: 64rpx;
  line-height: 64rpx;
  border-radius: 32rpx;
  background-color: #f5f5f5;
  color: #cccccc;
  font-size: 28rpx;
  font-weight: 600;
  border: none;
  transition: all 0.3s ease;
}

.xhs-publish-btn::after {
  display: none;
}

.xhs-publish-btn.is-active {
  background-color: var(--xhs-red);
  color: #ffffff;
}

.xhs-publish-btn.is-loading {
  opacity: 0.7;
}

.editor-body {
  padding: 0; 
}

/* --- 文本输入区 (移至上方) --- */
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
  color: #c4c4c4;
  font-weight: 500;
}

.content-wrapper {
  position: relative;
}

.xhs-content-textarea {
  width: 100%;
  min-height: 260rpx;
  font-size: 30rpx;
  line-height: 1.6;
  color: var(--xhs-text);
  padding-bottom: 30rpx; /* 留出一点空间给下方的图片区域 */
}

.xhs-placeholder-content {
  color: #c4c4c4;
}

/* --- 图片横向滚动区 (现在位于正文下方) --- */
.media-section {
  padding: 10rpx 32rpx 30rpx; /* 调整间距 */
  border-bottom: 1rpx solid var(--xhs-border); /* 在图片下方加一条分割线 */
}

.media-scroll {
  width: 100%;
  white-space: nowrap;
}

.media-list {
  display: inline-flex;
  gap: 16rpx;
}

.media-item, .media-add-btn {
  width: 210rpx; /* 稍微缩小一点，更精致 */
  height: 210rpx;
  border-radius: 12rpx;
  flex-shrink: 0;
  position: relative;
  overflow: hidden;
}

.media-img {
  width: 100%;
  height: 100%;
  background-color: #f8f8f8;
}

.media-delete {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  width: 36rpx;
  height: 36rpx;
  background-color: rgba(0, 0, 0, 0.4);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
}

.delete-icon {
  color: #ffffff;
  font-size: 28rpx;
  line-height: 1;
}

.media-add-btn {
  background-color: #f8f8f8;
  border: 2rpx dashed #e0e0e0; /* 加个虚线框 */
  display: flex;
  align-items: center;
  justify-content: center;
}

.add-icon {
  font-size: 50rpx;
  color: #cccccc;
  font-weight: 300;
}

/* 未选图片时的提示语 */
.image-tip {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  gap: 10rpx;
}

.xhs-tag-icon {
  font-size: 32rpx;
}

.tip-text {
  font-size: 28rpx;
  color: var(--xhs-muted);
}

/* --- 功能选项区 (话题、分区) --- */
.options-section {
  padding: 0 32rpx;
  margin-top: 30rpx; /* 调整间距 */
}

/* 话题标签 */
.quick-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 40rpx;
}

.xhs-tag-chip {
  display: inline-flex;
  align-items: center;
  height: 56rpx;
  padding: 0 24rpx;
  border-radius: 28rpx;
  background-color: #f5f5f5;
  transition: all 0.2s;
}

.xhs-tag-chip.is-selected {
  background-color: var(--xhs-red-light);
}

.hash-mark {
  color: var(--xhs-red);
  font-weight: bold;
  font-size: 26rpx;
  margin-right: 6rpx;
}

.tag-text {
  font-size: 26rpx;
  color: #555555;
}

.xhs-tag-chip.is-selected .tag-text {
  color: var(--xhs-red);
  font-weight: 500;
}

/* 列表选项 (分区) */
.xhs-cell {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10rpx 0; /* 调整间距 */
}

.cell-left {
  display: flex;
  align-items: center;
}

.cell-icon {
  font-size: 32rpx;
  margin-right: 12rpx;
}

.cell-title {
  font-size: 30rpx;
  color: var(--xhs-text);
  font-weight: 500;
}

.partition-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx;
  padding-top: 10rpx;
  padding-bottom: 40rpx;
}

.partition-chip {
  padding: 14rpx 36rpx;
  background-color: #f8f8f8;
  border-radius: 12rpx;
  font-size: 28rpx;
  color: #666666;
  border: 2rpx solid transparent;
  transition: all 0.2s;
}

.partition-chip.active {
  background-color: #ffffff;
  color: var(--xhs-red);
  border-color: var(--xhs-red);
  font-weight: 600;
}

.safe-bottom {
  height: calc(40rpx + env(safe-area-inset-bottom));
}
</style>