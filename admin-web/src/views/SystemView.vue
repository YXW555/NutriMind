<script setup>
import { computed, onMounted, ref } from 'vue'
import { adminApi, getApiBaseUrl, setApiBaseUrl } from '@/lib/api'
import { systemCards as mockCards, systemLogs as mockLogs, systemSettings } from '@/lib/mock'

const apiBase = ref(getApiBaseUrl())
const loading = ref(false)
const statusMessage = ref('')
const cards = ref(mockCards.map((item) => ({ ...item })))
const logs = ref(mockLogs.map((item) => ({ ...item })))
const settings = ref({
  ragEnabled: systemSettings.ragEnabled,
  agentFallbackEnabled: systemSettings.agentFallbackEnabled,
  visionEngine: systemSettings.visionEngine
})

const runtimeSummary = computed(() => [
  `RAG 检索：${settings.value.ragEnabled ? '启用' : '停用'}`,
  `Agent 兜底：${settings.value.agentFallbackEnabled ? '启用' : '停用'}`,
  `图像识别引擎：${settings.value.visionEngine}`
])

function saveApiBase() {
  apiBase.value = setApiBaseUrl(apiBase.value)
  statusMessage.value = '接口地址已更新。'
}

async function loadSystemOverview() {
  loading.value = true
  statusMessage.value = ''

  try {
    const [overview, foods] = await Promise.all([
      adminApi.get('/admin/system/overview'),
      adminApi.get('/foods', { current: 1, size: 1 })
    ])

    cards.value = [
      { label: 'Gateway', status: '正常', detail: apiBase.value },
      { label: 'RAG 检索', status: overview?.ragEnabled ? '正常' : '已停用', detail: `知识条目 ${Number(overview?.knowledgeDocumentCount || 0)} 个` },
      { label: 'Milvus', status: overview?.milvusReady ? '正常' : '待检查', detail: overview?.milvusUri || '未配置' },
      { label: 'Agent 服务', status: settings.value.agentFallbackEnabled ? '正常' : '待检查', detail: `对话模型：${overview?.chatModel || '未配置'}` },
      { label: '图像识别', status: settings.value.visionEngine === 'python' ? '正常' : '待检查', detail: `当前引擎：${settings.value.visionEngine}` },
      { label: '食物库', status: '正常', detail: `食物条目 ${Number(foods?.total || 0)} 个` }
    ]

    logs.value = [
      { id: 'live-1', level: 'INFO', message: `已同步知识库条目 ${Number(overview?.knowledgeDocumentCount || 0)} 个，其中启用 ${Number(overview?.enabledKnowledgeCount || 0)} 个。`, time: new Date().toLocaleTimeString('zh-CN', { hour12: false }) },
      { id: 'live-2', level: 'INFO', message: `已同步食物库条目 ${Number(foods?.total || 0)} 个。`, time: new Date().toLocaleTimeString('zh-CN', { hour12: false }) },
      { id: 'live-3', level: overview?.milvusReady ? 'INFO' : 'WARN', message: overview?.milvusReady ? `Milvus 已就绪，嵌入模型为 ${overview?.embeddingModel || '未配置'}。` : 'Milvus 或外部依赖未就绪，当前使用本地检索兜底。', time: new Date().toLocaleTimeString('zh-CN', { hour12: false }) }
    ]

    statusMessage.value = '系统状态已同步。'
  } catch (error) {
    cards.value = mockCards.map((item) => ({ ...item }))
    logs.value = mockLogs.map((item) => ({ ...item }))
    statusMessage.value = `未连接到真实后端，当前显示演示数据：${error.message}`
  } finally {
    loading.value = false
  }
}

onMounted(loadSystemOverview)
</script>

<template>
  <section class="page-stack">
    <div class="panel-card">
      <div class="panel-head">
        <div>
          <p class="panel-kicker">System</p>
          <h3 class="panel-title">系统状态</h3>
        </div>
        <div class="inline-actions">
          <button class="secondary-button" @click="loadSystemOverview">刷新状态</button>
          <button class="primary-button" @click="saveApiBase">保存接口地址</button>
        </div>
      </div>
      <p class="panel-desc">查看服务状态、基础配置和运行摘要。</p>

      <div class="toolbar-row top-gap">
        <input v-model="apiBase" class="toolbar-input" placeholder="http://localhost:8080/api" />
        <span class="status-pill" :class="loading ? 'warning' : 'online'">{{ loading ? '同步中...' : '配置已加载' }}</span>
      </div>
      <p v-if="statusMessage" class="metric-hint top-gap">{{ statusMessage }}</p>

      <div class="form-grid top-gap">
        <label class="form-field">
          <span>RAG 检索</span>
          <select v-model="settings.ragEnabled" class="toolbar-input">
            <option :value="true">启用</option>
            <option :value="false">停用</option>
          </select>
        </label>
        <label class="form-field">
          <span>Agent 兜底</span>
          <select v-model="settings.agentFallbackEnabled" class="toolbar-input">
            <option :value="true">启用</option>
            <option :value="false">停用</option>
          </select>
        </label>
        <label class="form-field">
          <span>图像识别引擎</span>
          <select v-model="settings.visionEngine" class="toolbar-input">
            <option value="mock">mock</option>
            <option value="python">python</option>
          </select>
        </label>
      </div>
    </div>

    <div class="metrics-grid">
      <article v-for="item in cards" :key="item.label" class="metric-card">
        <p class="metric-label">{{ item.label }}</p>
        <p class="metric-value small">{{ item.status }}</p>
        <p class="metric-hint">{{ item.detail }}</p>
      </article>
    </div>

    <div class="content-grid">
      <div class="panel-card">
        <div class="panel-head compact-head">
          <div>
            <p class="panel-kicker">Summary</p>
            <h3 class="panel-title">配置摘要</h3>
          </div>
        </div>
        <ul class="bullet-list">
          <li v-for="item in runtimeSummary" :key="item">{{ item }}</li>
        </ul>
      </div>

      <div class="panel-card">
        <div class="panel-head compact-head">
          <div>
            <p class="panel-kicker">Logs</p>
            <h3 class="panel-title">日志摘要</h3>
          </div>
        </div>
        <div class="log-list">
          <article v-for="item in logs" :key="item.id" class="log-item">
            <span class="status-pill" :class="item.level === 'WARN' ? 'warning' : 'online'">{{ item.level }}</span>
            <div class="log-body">
              <p class="timeline-title">{{ item.message }}</p>
              <p class="timeline-meta">{{ item.time }}</p>
            </div>
          </article>
        </div>
      </div>
    </div>
  </section>
</template>
