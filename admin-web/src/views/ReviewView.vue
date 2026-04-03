<script setup>
import { computed, onMounted, ref } from 'vue'
import { adminApi } from '@/lib/api'
import { moderationItems } from '@/lib/mock'

const loading = ref(false)
const message = ref('')
const statusFilter = ref('全部')
const riskFilter = ref('全部')
const items = ref(moderationItems.map((item) => ({ ...item })))

const statusOptions = ['全部', '待审核', '已通过', '已拦截']
const riskOptions = ['全部', '高', '中', '低']

const filteredItems = computed(() => items.value.filter((item) => {
  const matchesStatus = statusFilter.value === '全部' || item.status === statusFilter.value
  const matchesRisk = riskFilter.value === '全部' || item.risk === riskFilter.value
  return matchesStatus && matchesRisk
}))

async function loadItems() {
  loading.value = true
  message.value = ''

  try {
    const response = await adminApi.get('/admin/review/items')
    if (Array.isArray(response) && response.length) {
      items.value = response
    }
  } catch (error) {
    items.value = moderationItems.map((item) => ({ ...item }))
    message.value = `未连接到真实审核接口，当前显示演示数据：${error.message}`
  } finally {
    loading.value = false
  }
}

async function updateStatus(id, nextStatus) {
  const [typePrefix, rawTargetId] = String(id).split('-')
  const type = typePrefix === 'post' ? 'post' : 'comment'

  try {
    const updated = await adminApi.put(`/admin/review/${type}/${rawTargetId}?status=${encodeURIComponent(nextStatus)}`)
    items.value = items.value.map((item) => item.id === id ? updated : item)
    message.value = `审核状态已更新为${nextStatus}。`
  } catch (error) {
    items.value = items.value.map((item) => item.id === id ? { ...item, status: nextStatus } : item)
    message.value = `后端暂不可用，已在当前页面更新：${error.message}`
  }
}

onMounted(loadItems)
</script>

<template>
  <section class="page-stack">
    <div class="panel-card">
      <div class="panel-head">
        <div>
          <p class="panel-kicker">Review</p>
          <h3 class="panel-title">内容审核</h3>
        </div>
        <button class="secondary-button" @click="loadItems">刷新列表</button>
      </div>
      <p class="panel-desc">处理帖子和评论的审核状态。</p>
      <div class="toolbar-row top-gap">
        <select v-model="statusFilter" class="toolbar-input toolbar-select">
          <option v-for="item in statusOptions" :key="item" :value="item">{{ item }}</option>
        </select>
        <select v-model="riskFilter" class="toolbar-input toolbar-select">
          <option v-for="item in riskOptions" :key="item" :value="item">{{ item }}</option>
        </select>
        <span class="status-pill">{{ loading ? '加载中...' : `${filteredItems.length} 条结果` }}</span>
      </div>
      <p v-if="message" class="metric-hint top-gap">{{ message }}</p>
    </div>

    <div class="review-grid">
      <article v-for="item in filteredItems" :key="item.id" class="panel-card review-card">
        <div class="panel-head">
          <div>
            <p class="panel-kicker">{{ item.type }}</p>
            <h3 class="panel-title small">{{ item.id }}</h3>
          </div>
          <span class="status-pill" :class="item.risk === '高' ? 'danger' : item.risk === '中' ? 'warning' : 'online'">
            {{ item.risk }}风险
          </span>
        </div>
        <p class="review-author">发布者：{{ item.author }}</p>
        <p v-if="item.contentPreview" class="review-author">内容摘要：{{ item.contentPreview }}</p>
        <p class="review-reason">{{ item.reason }}</p>
        <p class="review-author">当前状态：{{ item.status }}</p>
        <div class="action-row">
          <button class="ghost-button" @click="updateStatus(item.id, '已通过')">通过</button>
          <button class="ghost-button danger-text" @click="updateStatus(item.id, '已拦截')">拦截</button>
        </div>
      </article>
    </div>
  </section>
</template>
