<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { adminApi } from '@/lib/api'

const loading = ref(false)
const connected = ref(true)
const overview = ref([
  { label: '知识条目', value: '-', hint: '加载中' },
  { label: '食物条目', value: '-', hint: '加载中' },
  { label: '待审核', value: '-', hint: '加载中' },
  { label: 'RAG', value: '-', hint: '加载中' }
])

const quickLinks = [
  { to: '/knowledge', title: '知识库', desc: '维护知识条目' },
  { to: '/foods', title: '食物库', desc: '维护食物与概念' },
  { to: '/review', title: '审核', desc: '处理帖子与评论' },
  { to: '/system', title: '系统', desc: '查看运行状态' }
]

const todoItems = ref([])
const trendBars = [42, 55, 48, 72, 61, 67, 76]
const chartMonths = ['一月', '二月', '三月', '四月', '五月', '六月', '七月']

const statusText = computed(() => {
  if (loading.value) return '同步中'
  return connected.value ? '已连接' : '未连接'
})

async function loadOverview() {
  loading.value = true
  try {
    const [systemOverview, foods] = await Promise.all([
      adminApi.get('/admin/system/overview'),
      adminApi.get('/foods', { current: 1, size: 1 })
    ])

    const knowledgeCount = Number(systemOverview?.knowledgeDocumentCount || 0)
    const enabledKnowledgeCount = Number(systemOverview?.enabledKnowledgeCount || 0)
    const pendingReviewCount = Number(systemOverview?.pendingReviewCount || 0)
    const foodCount = Number(foods?.total || 0)
    const ragReady = Boolean(systemOverview?.milvusReady)

    overview.value = [
      { label: '知识条目', value: String(knowledgeCount), hint: `已启用 ${enabledKnowledgeCount}` },
      { label: '食物条目', value: String(foodCount), hint: '真实数据' },
      { label: '待审核', value: String(pendingReviewCount), hint: '帖子与评论' },
      { label: 'RAG', value: ragReady ? '就绪' : '兜底', hint: ragReady ? 'Milvus 可用' : '本地检索' }
    ]

    todoItems.value = [
      pendingReviewCount > 0 ? `待审核 ${pendingReviewCount} 条` : '当前没有待审核内容',
      enabledKnowledgeCount < knowledgeCount ? `未启用知识 ${knowledgeCount - enabledKnowledgeCount} 条` : '知识条目已全部启用',
      ragReady ? 'RAG 服务运行正常' : '当前使用本地检索兜底'
    ]
    connected.value = true
  } catch {
    overview.value = [
      { label: '知识条目', value: '-', hint: '暂无数据' },
      { label: '食物条目', value: '-', hint: '暂无数据' },
      { label: '待审核', value: '-', hint: '暂无数据' },
      { label: 'RAG', value: '-', hint: '暂无数据' }
    ]
    todoItems.value = ['后端未连接']
    connected.value = false
  } finally {
    loading.value = false
  }
}

onMounted(loadOverview)
</script>

<template>
  <section class="page-stack dashboard-shell">
    <div class="dashboard-head panel-card">
      <div>
        <p class="panel-kicker">Overview</p>
        <h3 class="panel-title">管理总览</h3>
      </div>
      <div class="inline-actions">
        <span class="status-pill" :class="connected ? 'online' : 'offline'">{{ statusText }}</span>
        <button class="secondary-button" @click="loadOverview">刷新</button>
      </div>
    </div>

    <div class="metrics-grid dashboard-metrics">
      <article v-for="(item, index) in overview" :key="item.label" class="metric-card metric-card-rich">
        <div class="metric-head-row">
          <p class="metric-label">{{ item.label }}</p>
          <span class="metric-dot" :class="`tone-${index + 1}`"></span>
        </div>
        <p class="metric-value">{{ item.value }}</p>
        <p class="metric-hint">{{ item.hint }}</p>
        <div class="mini-bars">
          <span v-for="bar in 4" :key="bar" :style="{ height: `${24 + ((bar + index) % 4) * 10}px` }"></span>
        </div>
      </article>
    </div>

    <div class="dashboard-grid">
      <section class="panel-card trend-card">
        <div class="panel-head compact-head">
          <div>
            <p class="panel-kicker">Trend</p>
            <h3 class="panel-title">近期趋势</h3>
          </div>
          <span class="status-pill">近 7 期</span>
        </div>
        <div class="chart-stage top-gap">
          <div class="chart-lines">
            <span></span>
            <span></span>
            <span></span>
            <span></span>
          </div>
          <div class="chart-bars">
            <div v-for="(item, index) in trendBars" :key="chartMonths[index]" class="chart-column">
              <span :style="{ height: `${item}%` }"></span>
              <small>{{ chartMonths[index] }}</small>
            </div>
          </div>
        </div>
      </section>

      <div class="dashboard-side">
        <section class="panel-card quick-panel">
          <div class="panel-head compact-head">
            <div>
              <p class="panel-kicker">Quick Access</p>
              <h3 class="panel-title">常用入口</h3>
            </div>
          </div>
          <div class="simple-list top-gap">
            <RouterLink v-for="item in quickLinks" :key="item.to" :to="item.to" class="list-row link-row card-link-row">
              <div>
                <p class="list-title">{{ item.title }}</p>
                <p class="list-desc">{{ item.desc }}</p>
              </div>
              <span class="list-arrow">进入</span>
            </RouterLink>
          </div>
        </section>

        <section class="panel-card todo-panel">
          <div class="panel-head compact-head">
            <div>
              <p class="panel-kicker">Tasks</p>
              <h3 class="panel-title">待处理</h3>
            </div>
          </div>
          <div class="simple-list top-gap">
            <div v-for="item in todoItems" :key="item" class="list-row task-row">
              <span class="task-bullet"></span>
              <p class="list-title">{{ item }}</p>
            </div>
          </div>
        </section>
      </div>
    </div>
  </section>
</template>
