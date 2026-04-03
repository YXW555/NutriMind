<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { adminApi } from '@/lib/api'

const overview = ref([
  { label: '知识条目', value: '-', hint: '正在加载' },
  { label: '食物条目', value: '-', hint: '正在加载' },
  { label: '待审核内容', value: '-', hint: '正在加载' },
  { label: 'RAG 状态', value: '-', hint: '正在加载' }
])

const loading = ref(false)
const statusText = ref('未同步')

const quickLinks = [
  { to: '/knowledge', title: '知识库管理', desc: '维护来源、状态和知识正文' },
  { to: '/foods', title: '食物库管理', desc: '维护营养数据与食物分类' },
  { to: '/review', title: '内容审核', desc: '处理帖子与评论审核' },
  { to: '/system', title: '系统状态', desc: '查看服务、模型与运行信息' }
]

const todoItems = ref([
  '等待同步系统总览数据',
  '等待同步待审核内容',
  '等待同步知识库状态'
])

const trendBars = [42, 55, 48, 72, 61, 67, 76]
const chartMonths = ['一月', '二月', '三月', '四月', '五月', '六月', '七月']

const hasLiveData = computed(() => !loading.value && statusText.value === '已同步真实数据')

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
      { label: '知识条目', value: String(knowledgeCount), hint: `已启用 ${enabledKnowledgeCount} 条` },
      { label: '食物条目', value: String(foodCount), hint: '已同步真实食物库数据' },
      { label: '待审核内容', value: String(pendingReviewCount), hint: '包含帖子与评论审核' },
      { label: 'RAG 状态', value: ragReady ? '已就绪' : '兜底中', hint: ragReady ? 'Milvus 与模型依赖可用' : '当前使用本地知识检索' }
    ]

    todoItems.value = [
      pendingReviewCount > 0 ? `当前有 ${pendingReviewCount} 条内容待审核` : '当前没有待审核内容',
      enabledKnowledgeCount < knowledgeCount ? `还有 ${knowledgeCount - enabledKnowledgeCount} 条知识未启用` : '知识条目均已启用',
      ragReady ? 'RAG 向量检索运行正常' : '请检查 Milvus 或外部模型依赖'
    ]

    statusText.value = '已同步真实数据'
  } catch (error) {
    statusText.value = '演示模式'
    todoItems.value = [
      '后端暂未连接，当前展示本地界面',
      '请先启动 meal-service 与 food-service',
      '确认网关地址和接口配置是否正确'
    ]
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
        <p class="panel-desc">集中查看系统状态、待处理事项和核心业务数据。</p>
      </div>
      <div class="inline-actions">
        <span class="status-pill" :class="hasLiveData ? 'online' : 'offline'">
          {{ loading ? '同步中...' : statusText }}
        </span>
        <button class="secondary-button" @click="loadOverview">刷新数据</button>
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
          <span class="status-pill">近 7 周</span>
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
              <h3 class="panel-title">待处理事项</h3>
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