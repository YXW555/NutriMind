<script setup>
import { computed, onMounted, ref } from 'vue'
import { adminApi, getApiBaseUrl, setApiBaseUrl, getAdminToken, setAdminToken } from '@/lib/api'

const apiBase = ref(getApiBaseUrl())
const adminToken = ref(getAdminToken())
const loading = ref(false)
const cards = ref([])
const logs = ref([])
const settings = ref({
  ragEnabled: true,
  agentFallbackEnabled: true,
  visionEngine: 'python'
})

const runtimeSummary = computed(() => [
  `RAG：${settings.value.ragEnabled ? '启用' : '停用'}`,
  `Agent 兜底：${settings.value.agentFallbackEnabled ? '启用' : '停用'}`,
  `图像识别引擎：${settings.value.visionEngine}`
])

function saveApiBase() {
  apiBase.value = setApiBaseUrl(apiBase.value)
}

function saveAdminToken() {
  setAdminToken(adminToken.value)
}

async function loadSystemOverview() {
  loading.value = true
  try {
    const [overview, foods] = await Promise.all([
      adminApi.get('/admin/system/overview'),
      adminApi.get('/foods', { current: 1, size: 1 })
    ])

    cards.value = [
      { label: 'Gateway', status: '正常', detail: apiBase.value },
      { label: 'RAG', status: overview?.ragEnabled ? '正常' : '停用', detail: `知识条目 ${Number(overview?.knowledgeDocumentCount || 0)}` },
      { label: 'Milvus', status: overview?.milvusReady ? '正常' : '待检查', detail: overview?.milvusUri || '-' },
      { label: 'Agent', status: '正常', detail: overview?.chatModel || '-' },
      { label: '图像识别', status: settings.value.visionEngine === 'python' ? '正常' : '待检查', detail: settings.value.visionEngine },
      { label: '食物库', status: '正常', detail: `条目 ${Number(foods?.total || 0)}` }
    ]

    logs.value = [
      { id: 'log-1', level: 'INFO', message: `知识条目 ${Number(overview?.knowledgeDocumentCount || 0)}，已启用 ${Number(overview?.enabledKnowledgeCount || 0)}` },
      { id: 'log-2', level: 'INFO', message: `食物条目 ${Number(foods?.total || 0)}` },
      { id: 'log-3', level: overview?.milvusReady ? 'INFO' : 'WARN', message: overview?.milvusReady ? 'Milvus 已就绪' : '当前使用本地检索兜底' }
    ]
  } catch {
    cards.value = [
      { label: 'Gateway', status: '-', detail: '-' },
      { label: 'RAG', status: '-', detail: '-' },
      { label: 'Milvus', status: '-', detail: '-' },
      { label: 'Agent', status: '-', detail: '-' },
      { label: '图像识别', status: '-', detail: '-' },
      { label: '食物库', status: '-', detail: '-' }
    ]
    logs.value = []
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
        <button class="secondary-button" @click="loadSystemOverview">刷新</button>
      </div>

      <div class="form-grid top-gap">
        <label class="form-field form-field-wide">
          <span>接口地址</span>
          <div class="toolbar-row">
            <input v-model="apiBase" class="toolbar-input" placeholder="http://localhost:8080/api" />
            <button class="secondary-button" @click="saveApiBase">保存</button>
          </div>
        </label>
        <label class="form-field form-field-wide">
          <span>后台 Token</span>
          <div class="toolbar-row">
            <input v-model="adminToken" class="toolbar-input" placeholder="如需手动指定可在此填写" />
            <button class="secondary-button" @click="saveAdminToken">保存</button>
          </div>
        </label>
        <label class="form-field">
          <span>RAG</span>
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
            <option value="python">python</option>
            <option value="mock">mock</option>
          </select>
        </label>
      </div>
    </div>

    <div class="metrics-grid">
      <article v-for="item in cards" :key="item.label" class="metric-card">
        <p class="metric-label">{{ item.label }}</p>
        <p class="metric-value small">{{ loading ? '...' : item.status }}</p>
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
        <div v-if="logs.length" class="log-list">
          <article v-for="item in logs" :key="item.id" class="log-item">
            <span class="status-pill" :class="item.level === 'WARN' ? 'warning' : 'online'">{{ item.level }}</span>
            <div class="log-body">
              <p class="timeline-title">{{ item.message }}</p>
            </div>
          </article>
        </div>
        <p v-else class="metric-hint">暂无日志摘要。</p>
      </div>
    </div>
  </section>
</template>
