<script setup>
import { computed, onMounted, ref } from 'vue'
import { adminApi } from '@/lib/api'
import { knowledgeItems } from '@/lib/mock'

const keyword = ref('')
const authorityFilter = ref('全部')
const statusFilter = ref('全部')
const loading = ref(false)
const saveMessage = ref('')
const items = ref(knowledgeItems.map((item) => ({ ...item })))
const selectedId = ref(items.value[0]?.id || '')
const draft = ref(createDraft(items.value[0] || null))

const authorities = computed(() => ['全部', ...new Set(items.value.map((item) => item.authority).filter(Boolean))])
const statuses = ['全部', '已启用', '待复核', '草稿']

const filteredItems = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return items.value.filter((item) => {
    const matchesKeyword = !query || [item.title, item.authority, item.sourceName, item.tag, item.excerpt]
      .join(' ')
      .toLowerCase()
      .includes(query)
    const matchesAuthority = authorityFilter.value === '全部' || item.authority === authorityFilter.value
    const matchesStatus = statusFilter.value === '全部' || item.status === statusFilter.value
    return matchesKeyword && matchesAuthority && matchesStatus
  })
})

const selectedItem = computed(() => items.value.find((item) => item.id === selectedId.value) || null)

function createDraft(item) {
  return {
    id: item?.id || '',
    fileName: item?.fileName || '',
    title: item?.title || '',
    authority: item?.authority || '',
    sourceName: item?.sourceName || '',
    sourceUrl: item?.sourceUrl || '',
    tag: item?.tag || '',
    status: item?.status || '待复核',
    excerpt: item?.excerpt || '',
    content: item?.content || ''
  }
}

function buildContentFromDraft(currentDraft = draft.value) {
  return String(currentDraft?.content || '').trim()
    || `# ${currentDraft?.title || '新知识条目'}\n\n## 核心内容\n${currentDraft?.excerpt || '请填写知识正文。'}`
}

function selectItem(id) {
  selectedId.value = id
  const found = items.value.find((item) => item.id === id)
  draft.value = createDraft(found)
  draft.value.content = buildContentFromDraft(draft.value)
}

function createItem() {
  const nextId = `kb-${String(items.value.length + 1).padStart(3, '0')}`
  const nextItem = {
    id: nextId,
    fileName: `${nextId}.md`,
    title: '新知识条目',
    authority: '待补充',
    sourceName: '待补充来源',
    sourceUrl: '',
    tag: '待分类',
    status: '草稿',
    excerpt: '请填写知识摘要。',
    content: '# 新知识条目\n\n## 核心内容\n请填写知识正文。'
  }
  items.value = [nextItem, ...items.value]
  selectItem(nextItem.id)
  saveMessage.value = ''
}

async function loadDocuments() {
  loading.value = true
  saveMessage.value = ''
  try {
    const response = await adminApi.get('/admin/knowledge')
    if (Array.isArray(response) && response.length) {
      items.value = response.map((item) => ({
        ...item,
        content: buildContentFromDraft(item)
      }))
      selectItem(items.value[0].id)
    }
  } catch (error) {
    saveMessage.value = `未连接到后端知识库，当前显示演示数据：${error.message}`
  } finally {
    loading.value = false
  }
}

async function saveDocument() {
  saveMessage.value = ''
  const payload = {
    ...draft.value,
    content: buildContentFromDraft(draft.value)
  }

  try {
    const response = await adminApi.put('/admin/knowledge', payload)
    if (response) {
      const nextItem = {
        ...response,
        content: response.content || payload.content
      }
      const existingIndex = items.value.findIndex((item) => item.id === nextItem.id)
      if (existingIndex >= 0) {
        items.value[existingIndex] = nextItem
      } else {
        items.value.unshift(nextItem)
      }
      items.value = [...items.value]
      selectItem(nextItem.id)
      saveMessage.value = '知识条目已保存。'
    }
  } catch (error) {
    const fallbackItem = {
      ...payload,
      id: payload.id || `kb-${String(items.value.length + 1).padStart(3, '0')}`,
      fileName: payload.fileName || `${payload.id || 'draft'}.md`
    }
    const existingIndex = items.value.findIndex((item) => item.id === fallbackItem.id)
    if (existingIndex >= 0) {
      items.value[existingIndex] = fallbackItem
    } else {
      items.value.unshift(fallbackItem)
    }
    items.value = [...items.value]
    selectItem(fallbackItem.id)
    saveMessage.value = `后端暂不可用，已在当前页面保存：${error.message}`
  }
}

async function reloadKnowledgeBase() {
  saveMessage.value = ''
  try {
    await adminApi.post('/admin/knowledge/reload')
    saveMessage.value = '知识库已重载。'
    await loadDocuments()
  } catch (error) {
    saveMessage.value = `重载失败：${error.message}`
  }
}

onMounted(() => {
  draft.value.content = buildContentFromDraft(draft.value)
  loadDocuments()
})
</script>

<template>
  <section class="page-stack">
    <div class="panel-card">
      <div class="panel-head">
        <div>
          <p class="panel-kicker">Knowledge</p>
          <h3 class="panel-title">知识库管理</h3>
        </div>
        <div class="inline-actions">
          <button class="secondary-button" @click="reloadKnowledgeBase">重载知识库</button>
          <button class="secondary-button" @click="loadDocuments">刷新列表</button>
          <button class="primary-button" @click="createItem">新增条目</button>
        </div>
      </div>
      <p class="panel-desc">维护知识来源、标签、状态和正文内容。</p>

      <div class="toolbar-row">
        <input v-model="keyword" class="toolbar-input" placeholder="搜索标题、机构、来源" />
        <select v-model="authorityFilter" class="toolbar-input toolbar-select">
          <option v-for="item in authorities" :key="item" :value="item">{{ item }}</option>
        </select>
        <select v-model="statusFilter" class="toolbar-input toolbar-select">
          <option v-for="item in statuses" :key="item" :value="item">{{ item }}</option>
        </select>
        <span class="status-pill">{{ loading ? '加载中...' : `${filteredItems.length} 条结果` }}</span>
        <span v-if="saveMessage" class="status-pill online">{{ saveMessage }}</span>
      </div>
    </div>

    <div class="split-layout">
      <div class="table-card">
        <table class="data-table">
          <thead>
            <tr>
              <th>标题</th>
              <th>机构</th>
              <th>标签</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in filteredItems"
              :key="item.id"
              class="clickable-row"
              :class="{ selected: item.id === selectedId }"
              @click="selectItem(item.id)"
            >
              <td>
                <strong>{{ item.title }}</strong>
                <p class="cell-subtitle">{{ item.excerpt }}</p>
              </td>
              <td>{{ item.authority }}</td>
              <td>{{ item.tag }}</td>
              <td><span class="status-pill">{{ item.status }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="panel-card editor-card">
        <div class="panel-head">
          <div>
            <p class="panel-kicker">Editor</p>
            <h3 class="panel-title">条目编辑</h3>
          </div>
          <button class="primary-button" @click="saveDocument">保存</button>
        </div>

        <div class="form-grid">
          <label class="form-field">
            <span>标题</span>
            <input v-model="draft.title" class="toolbar-input" />
          </label>
          <label class="form-field">
            <span>来源机构</span>
            <input v-model="draft.authority" class="toolbar-input" />
          </label>
          <label class="form-field">
            <span>来源名称</span>
            <input v-model="draft.sourceName" class="toolbar-input" />
          </label>
          <label class="form-field">
            <span>来源链接</span>
            <input v-model="draft.sourceUrl" class="toolbar-input" />
          </label>
          <label class="form-field">
            <span>标签</span>
            <input v-model="draft.tag" class="toolbar-input" />
          </label>
          <label class="form-field">
            <span>状态</span>
            <select v-model="draft.status" class="toolbar-input">
              <option>已启用</option>
              <option>待复核</option>
              <option>草稿</option>
            </select>
          </label>
          <label class="form-field full">
            <span>摘要</span>
            <textarea v-model="draft.excerpt" class="editor-textarea"></textarea>
          </label>
          <label class="form-field full">
            <span>正文</span>
            <textarea v-model="draft.content" class="editor-textarea"></textarea>
          </label>
        </div>

        <div v-if="selectedItem" class="preview-box">
          <p class="panel-kicker">预览</p>
          <p class="preview-title">{{ draft.title }}</p>
          <p class="preview-meta">{{ draft.authority }} · {{ draft.sourceName }}</p>
          <p class="preview-text">{{ draft.excerpt }}</p>
        </div>
      </div>
    </div>
  </section>
</template>
