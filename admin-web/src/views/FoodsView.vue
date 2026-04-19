<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { adminApi } from '@/lib/api'

const loading = ref(false)
const metadataLoading = ref(false)
const keyword = ref('')
const categoryFilter = ref('全部')
const saveMessage = ref('')
const items = ref([])
const categoryOptions = ref([])
const selectedFoodId = ref(null)
const recognitionLogs = ref([])
const metadata = ref(createEmptyMetadata())
const form = ref(createEmptyForm())
const currentPage = ref(1)
const pageSize = 10

function createEmptyForm() {
  return {
    name: '',
    category: '',
    unit: '100g',
    calories: '',
    protein: '',
    fat: '',
    carbohydrate: '',
    fiber: '',
    status: '启用'
  }
}

function createEmptyMetadata() {
  return {
    categoryId: null,
    categoryName: '',
    conceptId: null,
    conceptCode: '',
    conceptName: '',
    conceptNameEn: '',
    aliases: [],
    conceptAliases: [],
    imageSamples: []
  }
}

function normalizeFood(item) {
  return {
    id: item.id,
    name: item.name || '',
    category: item.category || '未分类',
    unit: item.unit || '100g',
    calories: Number(item.calories || 0),
    protein: Number(item.protein || 0),
    fat: Number(item.fat || 0),
    carbohydrate: Number(item.carbohydrate || 0),
    fiber: Number(item.fiber || 0),
    status: Number(item.status) === 1 || item.status === '启用' ? '启用' : '待校对'
  }
}

function toPayload() {
  return {
    name: form.value.name.trim(),
    category: form.value.category.trim() || '未分类',
    unit: form.value.unit.trim() || '100g',
    calories: Number(form.value.calories || 0),
    protein: Number(form.value.protein || 0),
    fat: Number(form.value.fat || 0),
    carbohydrate: Number(form.value.carbohydrate || 0),
    fiber: Number(form.value.fiber || 0),
    status: form.value.status === '启用' ? 1 : 0
  }
}

function fillForm(food) {
  form.value = {
    name: food.name,
    category: food.category,
    unit: food.unit || '100g',
    calories: String(food.calories ?? ''),
    protein: String(food.protein ?? ''),
    fat: String(food.fat ?? ''),
    carbohydrate: String(food.carbohydrate ?? ''),
    fiber: String(food.fiber ?? ''),
    status: food.status || '启用'
  }
}

const filterCategories = computed(() => {
  const merged = new Set(['全部'])
  categoryOptions.value.forEach((item) => merged.add(item.name))
  items.value.forEach((item) => merged.add(item.category))
  return [...merged]
})

const visibleFoods = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return items.value.filter((item) => {
    const matchesKeyword = !query || [item.name, item.category].join(' ').toLowerCase().includes(query)
    const matchesCategory = categoryFilter.value === '全部' || item.category === categoryFilter.value
    return matchesKeyword && matchesCategory
  })
})

const totalPages = computed(() => Math.max(1, Math.ceil(visibleFoods.value.length / pageSize)))

const pagedFoods = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return visibleFoods.value.slice(start, start + pageSize)
})

const selectedFood = computed(() => items.value.find((item) => item.id === selectedFoodId.value) || null)

function selectFood(foodId) {
  selectedFoodId.value = foodId
  const current = items.value.find((item) => item.id === foodId)
  if (current) {
    fillForm(current)
  } else {
    form.value = createEmptyForm()
  }
}

function createFood() {
  selectedFoodId.value = null
  form.value = createEmptyForm()
  metadata.value = createEmptyMetadata()
  recognitionLogs.value = []
  saveMessage.value = ''
}

async function loadCategories() {
  try {
    const data = await adminApi.get('/foods/categories')
    categoryOptions.value = Array.isArray(data) ? data : []
  } catch {
    categoryOptions.value = []
  }
}

async function loadFoods() {
  loading.value = true
  saveMessage.value = ''
  try {
    const data = await adminApi.get('/foods', { current: 1, size: 200 })
    items.value = Array.isArray(data?.records) ? data.records.map(normalizeFood) : []
    if (items.value.length) {
      if (!items.value.find((item) => item.id === selectedFoodId.value)) {
        selectFood(items.value[0].id)
      }
    } else {
      createFood()
    }
  } catch (error) {
    items.value = []
    createFood()
    saveMessage.value = `食物库连接失败：${error.message}`
  } finally {
    loading.value = false
  }
}

async function loadFoodDetails(foodId) {
  if (!foodId) return
  metadataLoading.value = true
  try {
    const [detail, logs] = await Promise.all([
      adminApi.get(`/foods/${foodId}/metadata`),
      adminApi.get('/foods/recognitions/logs', { foodId, size: 12 })
    ])
    metadata.value = {
      ...createEmptyMetadata(),
      ...(detail || {}),
      aliases: Array.isArray(detail?.aliases) ? detail.aliases : [],
      conceptAliases: Array.isArray(detail?.conceptAliases) ? detail.conceptAliases : [],
      imageSamples: Array.isArray(detail?.imageSamples) ? detail.imageSamples : []
    }
    recognitionLogs.value = Array.isArray(logs) ? logs : []
  } catch (error) {
    metadata.value = createEmptyMetadata()
    recognitionLogs.value = []
    saveMessage.value = `读取食物详情失败：${error.message}`
  } finally {
    metadataLoading.value = false
  }
}

async function saveFood() {
  if (!form.value.name.trim()) {
    saveMessage.value = '请先填写食物名称。'
    return
  }

  const payload = toPayload()
  const editingId = selectedFoodId.value

  try {
    const saved = editingId
      ? await adminApi.put(`/foods/${editingId}`, payload)
      : await adminApi.post('/foods', payload)

    const normalized = normalizeFood(saved)
    const nextItems = items.value.filter((item) => item.id !== normalized.id)
    items.value = [normalized, ...nextItems]
    selectFood(normalized.id)
    await loadFoodDetails(normalized.id)
    saveMessage.value = editingId ? '食物条目已更新。' : '食物条目已创建。'
  } catch (error) {
    saveMessage.value = `保存失败：${error.message}`
  }
}

async function removeFood(foodId) {
  try {
    await adminApi.delete(`/foods/${foodId}`)
    items.value = items.value.filter((item) => item.id !== foodId)
    saveMessage.value = '食物条目已删除。'
    if (selectedFoodId.value === foodId) {
      if (items.value.length) {
        const next = items.value[0]
        selectFood(next.id)
        await loadFoodDetails(next.id)
      } else {
        createFood()
      }
    }
  } catch (error) {
    saveMessage.value = `删除失败：${error.message}`
  }
}

function changePage(nextPage) {
  if (nextPage < 1 || nextPage > totalPages.value) return
  currentPage.value = nextPage
}

function formatConfidence(value) {
  const numeric = Number(value || 0)
  return `${Math.round(numeric * 100)}%`
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ')
}

watch([keyword, categoryFilter], () => {
  currentPage.value = 1
})

watch(visibleFoods, (list) => {
  if (currentPage.value > totalPages.value) {
    currentPage.value = totalPages.value
  }
  if (!selectedFoodId.value && list.length) {
    selectFood(list[0].id)
  }
})

watch(selectedFoodId, async (foodId) => {
  if (!foodId) return
  const current = items.value.find((item) => item.id === foodId)
  if (current) {
    fillForm(current)
  }
  await loadFoodDetails(foodId)
})

onMounted(async () => {
  await Promise.all([loadCategories(), loadFoods()])
  if (selectedFoodId.value) {
    await loadFoodDetails(selectedFoodId.value)
  }
})
</script>

<template>
  <section class="page-stack">
    <div class="panel-card">
      <div class="panel-head">
        <div>
          <p class="panel-kicker">Foods</p>
          <h3 class="panel-title">食物库管理</h3>
        </div>
        <div class="inline-actions">
          <button class="secondary-button" @click="loadFoods">刷新列表</button>
          <button class="secondary-button" @click="createFood">新建条目</button>
          <button class="primary-button" @click="saveFood">{{ selectedFoodId ? '保存修改' : '创建食物' }}</button>
        </div>
      </div>
      <p class="panel-desc">连接云端食物库，维护基础营养信息，并查看概念、样本和识别日志。</p>

      <div class="toolbar-row top-gap">
        <input v-model="keyword" class="toolbar-input" placeholder="搜索食物名称或分类" />
        <select v-model="categoryFilter" class="toolbar-input toolbar-select">
          <option v-for="item in filterCategories" :key="item" :value="item">{{ item }}</option>
        </select>
        <span class="status-pill">{{ loading ? '同步中...' : `共 ${visibleFoods.length} 条` }}</span>
        <span v-if="metadataLoading" class="status-pill online">正在读取详情</span>
      </div>
      <p v-if="saveMessage" class="metric-hint top-gap">{{ saveMessage }}</p>
    </div>

    <div class="food-admin-grid">
      <div class="panel-card list-panel">
        <div class="panel-head compact-head">
          <div>
            <p class="panel-kicker">Library</p>
            <h3 class="panel-title">食物条目</h3>
          </div>
          <p class="metric-hint">每页 {{ pageSize }} 条</p>
        </div>

        <div class="food-list top-gap">
          <button
            v-for="food in pagedFoods"
            :key="food.id"
            class="food-list-item"
            :class="{ active: food.id === selectedFoodId }"
            @click="selectFood(food.id)"
          >
            <div class="food-list-copy">
              <p class="food-list-name">{{ food.name }}</p>
              <p class="cell-subtitle">{{ food.category }} · {{ food.unit }}</p>
            </div>
            <div class="food-list-side">
              <span class="status-pill" :class="food.status === '启用' ? 'online' : 'warning'">{{ food.status }}</span>
              <strong>{{ food.calories }} kcal</strong>
            </div>
          </button>

          <div v-if="!pagedFoods.length" class="list-empty">
            <p class="food-list-name">暂无食物条目</p>
            <p class="cell-subtitle">请检查云端接口或尝试新建食物。</p>
          </div>
        </div>

        <div class="list-footer">
          <button class="secondary-button" :disabled="currentPage === 1" @click="changePage(currentPage - 1)">上一页</button>
          <span class="status-pill">第 {{ currentPage }} / {{ totalPages }} 页</span>
          <button class="secondary-button" :disabled="currentPage >= totalPages" @click="changePage(currentPage + 1)">下一页</button>
        </div>
      </div>

      <div class="page-stack">
        <div class="panel-card">
          <div class="panel-head compact-head">
            <div>
              <p class="panel-kicker">Editor</p>
              <h3 class="panel-title">基础信息编辑</h3>
            </div>
            <p class="metric-hint">{{ selectedFoodId ? `当前食物 #${selectedFoodId}` : '正在新建食物' }}</p>
          </div>

          <div class="form-grid top-gap">
            <label class="form-field">
              <span>食物名称</span>
              <input v-model="form.name" class="toolbar-input" placeholder="例如：鸡腿肉" />
            </label>
            <label class="form-field">
              <span>食物分类</span>
              <input v-model="form.category" class="toolbar-input" placeholder="例如：高蛋白" />
            </label>
            <label class="form-field">
              <span>计量单位</span>
              <input v-model="form.unit" class="toolbar-input" placeholder="默认 100g" />
            </label>
            <label class="form-field">
              <span>状态</span>
              <select v-model="form.status" class="toolbar-input">
                <option>启用</option>
                <option>待校对</option>
              </select>
            </label>
            <label class="form-field">
              <span>热量</span>
              <input v-model="form.calories" class="toolbar-input" placeholder="kcal / 100g" />
            </label>
            <label class="form-field">
              <span>蛋白质</span>
              <input v-model="form.protein" class="toolbar-input" placeholder="g / 100g" />
            </label>
            <label class="form-field">
              <span>脂肪</span>
              <input v-model="form.fat" class="toolbar-input" placeholder="g / 100g" />
            </label>
            <label class="form-field">
              <span>碳水化合物</span>
              <input v-model="form.carbohydrate" class="toolbar-input" placeholder="g / 100g" />
            </label>
            <label class="form-field form-field-wide">
              <span>膳食纤维</span>
              <input v-model="form.fiber" class="toolbar-input" placeholder="g / 100g" />
            </label>
          </div>
        </div>

        <div class="content-grid">
          <div class="panel-card">
            <div class="panel-head compact-head">
              <div>
                <p class="panel-kicker">Concept</p>
                <h3 class="panel-title small">食物概念</h3>
              </div>
              <span class="status-pill">{{ metadata.conceptCode || '未绑定概念' }}</span>
            </div>
            <div class="simple-list">
              <div class="list-row">
                <div>
                  <p class="list-title">{{ metadata.conceptName || '暂无概念名称' }}</p>
                  <p class="list-desc">{{ metadata.conceptNameEn || '暂无英文概念' }}</p>
                </div>
              </div>
              <div class="list-row">
                <div>
                  <p class="list-title">所属分类</p>
                  <p class="list-desc">{{ metadata.categoryName || selectedFood?.category || '未分类' }}</p>
                </div>
              </div>
              <div class="list-row">
                <div>
                  <p class="list-title">概念别名</p>
                  <p class="list-desc">{{ metadata.conceptAliases?.length ? metadata.conceptAliases.join(' / ') : '暂无概念别名' }}</p>
                </div>
              </div>
              <div class="list-row">
                <div>
                  <p class="list-title">食物别名</p>
                  <p class="list-desc">{{ metadata.aliases?.length ? metadata.aliases.join(' / ') : '暂无食物别名' }}</p>
                </div>
              </div>
            </div>
          </div>

          <div class="panel-card">
            <div class="panel-head compact-head">
              <div>
                <p class="panel-kicker">Samples</p>
                <h3 class="panel-title small">图片样本</h3>
              </div>
              <span class="status-pill">{{ metadata.imageSamples?.length || 0 }} 张</span>
            </div>
            <div v-if="metadata.imageSamples?.length" class="simple-list">
              <div v-for="sample in metadata.imageSamples" :key="sample.id" class="list-row">
                <div class="log-body">
                  <p class="list-title">{{ sample.description || '样本图片' }}</p>
                  <p class="list-desc">{{ sample.source || '未标注来源' }}</p>
                  <a class="cell-link" :href="sample.imageUrl" target="_blank" rel="noreferrer">{{ sample.imageUrl }}</a>
                </div>
              </div>
            </div>
            <p v-else class="metric-hint top-gap">当前食物还没有配置图片样本。</p>
          </div>
        </div>

        <div class="panel-card">
          <div class="panel-head compact-head">
            <div>
              <p class="panel-kicker">Logs</p>
              <h3 class="panel-title small">近期识别日志</h3>
            </div>
            <span class="status-pill">{{ recognitionLogs.length }} 条</span>
          </div>
          <div v-if="recognitionLogs.length" class="table-shell top-gap">
            <table class="data-table">
              <thead>
                <tr>
                  <th>原始标签</th>
                  <th>标准标签</th>
                  <th>确认结果</th>
                  <th>置信度</th>
                  <th>模式</th>
                  <th>时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="log in recognitionLogs" :key="log.id">
                  <td>
                    <p class="food-title">{{ log.recognizedLabel || '-' }}</p>
                    <p class="cell-subtitle">{{ log.searchTerms || '无检索词' }}</p>
                  </td>
                  <td>{{ log.recognizedCanonicalLabel || '-' }}</td>
                  <td>{{ log.matchedFoodName || '-' }}</td>
                  <td>{{ formatConfidence(log.confidence) }}</td>
                  <td>{{ log.recognitionMode || '-' }}</td>
                  <td>{{ formatTime(log.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else class="metric-hint top-gap">当前食物还没有识别反馈数据，用户确认后会自动回流到这里。</p>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.food-admin-grid {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 18px;
}

.list-panel {
  display: flex;
  flex-direction: column;
  min-height: 760px;
}

.food-list {
  display: grid;
  gap: 10px;
  max-height: 640px;
  overflow: auto;
  padding-right: 4px;
}

.food-list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  width: 100%;
  padding: 14px 16px;
  border: 1px solid var(--line);
  background: #fff;
  color: var(--text);
  text-align: left;
  cursor: pointer;
}

.food-list-item.active {
  border-color: #ccefdc;
  background: linear-gradient(180deg, #f4fcf8 0%, #ecf9f1 100%);
}

.food-list-copy {
  min-width: 0;
}

.food-list-name,
.food-title {
  margin: 0;
  font-weight: 700;
  color: #111827;
}

.food-list-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  flex-shrink: 0;
}

.list-empty {
  padding: 32px 18px;
  border: 1px dashed var(--line);
  background: var(--panel-soft);
}

.list-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: auto;
  padding-top: 14px;
}

@media (max-width: 1180px) {
  .food-admin-grid {
    grid-template-columns: 1fr;
  }

  .list-panel {
    min-height: auto;
  }

  .food-list {
    max-height: 420px;
  }
}
</style>
