<script setup>
import { computed, onMounted, ref } from 'vue'
import { adminApi } from '@/lib/api'

const loading = ref(false)
const sceneType = ref('全部')
const executions = ref([])
const activeExecutionId = ref(null)
const executionDetail = ref(null)

const sceneOptions = ['全部', 'ADVISOR_CHAT', 'MEAL_PLAN_DAILY', 'MEAL_PLAN_WEEK']

const activeExecution = computed(() => executions.value.find((item) => item.id === activeExecutionId.value) || null)

function formatScene(value) {
  switch (value) {
    case 'ADVISOR_CHAT':
      return '营养问答'
    case 'MEAL_PLAN_DAILY':
      return '单日计划'
    case 'MEAL_PLAN_WEEK':
      return '周计划'
    default:
      return value || '-'
  }
}

function formatStatus(value) {
  switch (value) {
    case 'SUCCESS':
      return '成功'
    case 'FAILED':
      return '失败'
    case 'RUNNING':
      return '进行中'
    case 'SKIPPED':
      return '跳过'
    default:
      return value || '-'
  }
}

function resolveStatusClass(value) {
  if (value === 'FAILED') return 'warning'
  if (value === 'SUCCESS') return 'online'
  return ''
}

async function loadExecutions() {
  loading.value = true
  try {
    const params = { size: 12 }
    if (sceneType.value !== '全部') {
      params.sceneType = sceneType.value
    }
    executions.value = await adminApi.get('/admin/agent-executions', params)
    if (executions.value.length) {
      const targetId = executions.value.some((item) => item.id === activeExecutionId.value)
        ? activeExecutionId.value
        : executions.value[0].id
      await loadExecutionDetail(targetId)
    } else {
      activeExecutionId.value = null
      executionDetail.value = null
    }
  } catch {
    executions.value = []
    activeExecutionId.value = null
    executionDetail.value = null
  } finally {
    loading.value = false
  }
}

async function loadExecutionDetail(id) {
  activeExecutionId.value = id
  try {
    executionDetail.value = await adminApi.get(`/admin/agent-executions/${id}`)
  } catch {
    executionDetail.value = null
  }
}

onMounted(loadExecutions)
</script>

<template>
  <section class="page-stack">
    <div class="panel-card">
      <div class="panel-head">
        <div>
          <p class="panel-kicker">Multi-Agent</p>
          <h3 class="panel-title">执行链路</h3>
        </div>
        <div class="inline-actions">
          <select v-model="sceneType" class="toolbar-input toolbar-select" @change="loadExecutions">
            <option v-for="item in sceneOptions" :key="item" :value="item">{{ item }}</option>
          </select>
          <button class="secondary-button" @click="loadExecutions">刷新</button>
        </div>
      </div>

      <div class="metrics-grid top-gap">
        <article class="metric-card">
          <p class="metric-label">最近执行</p>
          <p class="metric-value small">{{ activeExecution ? formatScene(activeExecution.sceneType) : '-' }}</p>
          <p class="metric-hint">{{ activeExecution?.generationMode || '暂无' }}</p>
        </article>
        <article class="metric-card">
          <p class="metric-label">执行状态</p>
          <p class="metric-value small">{{ activeExecution ? formatStatus(activeExecution.finalStatus) : '-' }}</p>
          <p class="metric-hint">{{ activeExecution?.stepCount ?? 0 }} 个阶段</p>
        </article>
        <article class="metric-card">
          <p class="metric-label">最近摘要</p>
          <p class="metric-value small">{{ executions.length }}</p>
          <p class="metric-hint">已记录执行链路</p>
        </article>
      </div>
    </div>

    <div class="split-layout">
      <div class="table-card">
        <table class="data-table">
          <thead>
            <tr>
              <th>场景</th>
              <th>模式</th>
              <th>状态</th>
              <th>阶段</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in executions"
              :key="item.id"
              class="clickable-row"
              :class="{ selected: item.id === activeExecutionId }"
              @click="loadExecutionDetail(item.id)"
            >
              <td>
                <strong>{{ formatScene(item.sceneType) }}</strong>
                <p class="cell-subtitle">{{ item.requestSummary || '暂无摘要' }}</p>
              </td>
              <td>{{ item.generationMode || '-' }}</td>
              <td>
                <span class="status-pill" :class="resolveStatusClass(item.finalStatus)">
                  {{ formatStatus(item.finalStatus) }}
                </span>
              </td>
              <td>{{ item.stepCount || 0 }}</td>
            </tr>
            <tr v-if="!executions.length">
              <td colspan="4" class="empty-cell">{{ loading ? '加载中...' : '暂无执行记录' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="panel-card detail-panel">
        <div class="panel-head compact-head">
          <div>
            <p class="panel-kicker">Execution Detail</p>
            <h3 class="panel-title">链路明细</h3>
          </div>
          <span v-if="executionDetail" class="status-pill" :class="resolveStatusClass(executionDetail.finalStatus)">
            {{ formatStatus(executionDetail.finalStatus) }}
          </span>
        </div>

        <div v-if="executionDetail" class="page-stack top-gap">
          <div class="detail-block">
            <p class="detail-label">请求摘要</p>
            <p class="detail-value">{{ executionDetail.requestSummary || '暂无' }}</p>
          </div>
          <div class="detail-block">
            <p class="detail-label">最终结论</p>
            <p class="detail-value">{{ executionDetail.finalSummary || '暂无' }}</p>
          </div>

          <div class="graph-panel">
            <h4 class="graph-subtitle">阶段步骤</h4>
            <div class="graph-relation-list">
              <article
                v-for="step in executionDetail.steps || []"
                :key="`${executionDetail.id}-${step.stepOrder}`"
                class="agent-step-card"
              >
                <div class="agent-step-head">
                  <strong>{{ step.stepOrder }}. {{ step.agentName }}</strong>
                  <span class="status-pill" :class="resolveStatusClass(step.status)">{{ formatStatus(step.status) }}</span>
                </div>
                <p class="cell-subtitle">{{ step.stageName }} · {{ step.durationMs ?? 0 }} ms</p>
                <p class="detail-value">{{ step.outputSummary || step.inputSummary || '暂无输出摘要' }}</p>
                <p v-if="step.referenceSummary" class="metric-hint">依据：{{ step.referenceSummary }}</p>
              </article>
            </div>
          </div>
        </div>

        <p v-else class="metric-hint top-gap">选择左侧记录查看执行链路。</p>
      </div>
    </div>
  </section>
</template>
