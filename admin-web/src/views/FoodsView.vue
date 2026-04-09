<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { adminApi } from '@/lib/api'
import { foodItems } from '@/lib/mock'

const loading = ref(false)
const metadataLoading = ref(false)
const keyword = ref('')
const categoryFilter = ref('全部')
const saveMessage = ref('')
const items = ref(foodItems.map(normalizeFood))
const categoryOptions = ref([])
const selectedFoodId = ref(foodItems[0]?.id || null)
const metadata = ref(createEmptyMetadata())
const recognitionLogs = ref([])
const form = ref(createEmptyForm())

function createEmptyForm() {
  return {
    name: '',
    category: '高蛋白',
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
    category: form.value.category.trim(),
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

function applySelection(foodId) {
  selectedFoodId.value = foodId
  const current = items.value.find((item) => item.id === foodId)
  if (current) {
    fillForm(current)
  } else {
    form.value = createEmptyForm()
  }
}

const visibleFoods = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return items.value.filter((item) => {
    const matchesKeyword = !query || [item.name, item.category].join(' ').toLowerCase().includes(query)
    const matchesCategory = categoryFilter.value === '全部' || item.category === categoryFilter.value
    return matchesKeyword && matchesCategory
  })
})

const filterCategories = computed(() => {
  const merged = new Set(['全部'])
  categoryOptions.value.forEach((item) => merged.add(item.name))
  items.value.forEach((item) => merged.add(item.category))
  return [...merged]
})

const selectedFood = computed(() => items.value.find((item) => item.id === selectedFoodId.value) || null)

function mockMetadataFor(food) {
  if (!food) {
    return createEmptyMetadata()
  }
  return {
    categoryId: food.id + 1000,
    categoryName: food.category,
    conceptId: food.id + 2000,
    conceptCode: `concept_${food.id}`,
    conceptName: food.name,
    conceptNameEn: '',
    aliases: [food.name],
    conceptAliases: [food.category, food.name],
    imageSamples: []
  }
}

function mockRecognitionLogsFor(food) {
  if (!food) {
    return []
  }
  return [
    {
      id: `${food.id}-1`,
      recognizedLabel: food.name,
      recognizedCanonicalLabel: food.name,
      matchedFoodName: food.name,
      confidence: 0.91,
      recognitionMode: 'mock_fallback',
      searchTerms: `${food.name}, ${food.category}`,
      manualConfirmationRequired: true,
      createdAt: '2026-04-09 10:20:00'
    },
    {
      id: `${food.id}-2`,
      recognizedLabel: food.category,
      recognizedCanonicalLabel: food.category,
      matchedFoodName: food.name,
      confidence: 0.74,
      recognitionMode: 'concept_recall',
      searchTerms: `${food.category}, ${food.name}`,
      manualConfirmationRequired: true,
      createdAt: '2026-04-08 18:15:00'
    }
  ]
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
    if (items.value.length && !items.value.find((item) => item.id === selectedFoodId.value)) {
      applySelection(items.value[0].id)
    }
  } catch (error) {
    items.value = foodItems.map(normalizeFood)
    if (items.value.length && !items.value.find((item) => item.id === selectedFoodId.value)) {
      applySelection(items.value[0].id)
    }
    saveMessage.value = `未连接到真实食物库，当前显示演示数据：${error.message}`
  } finally {
    loading.value = false
  }
}

async function loadFoodDetails(foodId) {
  const current = items.value.find((item) => item.id === foodId)
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
    metadata.value = mockMetadataFor(current)
    recognitionLogs.value = mockRecognitionLogsFor(current)
    saveMessage.value = `后端元数据暂不可用，当前显示演示信息：${error.message}`
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
    applySelection(normalized.id)
    await loadFoodDetails(normalized.id)
    saveMessage.value = editingId ? '食物条目已更新。' : '食物条目已创建。'
  } catch (error) {
    if (editingId) {
      items.value = items.value.map((item) => item.id === editingId ? { ...item, ...normalizeFood({ id: editingId, ...payload }) } : item)
      saveMessage.value = `后端暂不可用，已在当前页模拟更新：${error.message}`
      applySelection(editingId)
    } else {
      const localId = Date.now()
      items.value = [normalizeFood({ id: localId, ...payload }), ...items.value]
      applySelection(localId)
      saveMessage.value = `后端暂不可用，已在当前页模拟创建：${error.message}`
    }
  }
}

async function removeFood(foodId) {
  try {
    await adminApi.delete(`/foods/${foodId}`)
  } catch (error) {
    saveMessage.value = `后端删除失败，已在当前页模拟移除：${error.message}`
  }

  items.value = items.value.filter((item) => item.id !== foodId)
  if (selectedFoodId.value === foodId) {
    if (items.value.length) {
      applySelection(items.value[0].id)
      await loadFoodDetails(items.value[0].id)
    } else {
      selectedFoodId.value = null
      form.value = createEmptyForm()
      metadata.value = createEmptyMetadata()
      recognitionLogs.value = []
    }
  }
  if (!saveMessage.value) {
    saveMessage.value = '食物条目已删除。'
  }
}

function createFood() {
  selectedFoodId.value = null
  form.value = createEmptyForm()
  metadata.value = createEmptyMetadata()
  recognitionLogs.value = []
  saveMessage.value = ''
}

function formatConfidence(value) {
  const numeric = Number(value || 0)
  return `${Math.round(numeric * 100)}%`
}

function formatTime(value) {
  if (!value) {
    return '-'
  }
  return String(value).replace('T', ' ')
}

watch(selectedFoodId, async (foodId) => {
  if (!foodId) {
    return
  }
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
  } else if (items.value.length) {
    applySelection(items.value[0].id)
    await loadFoodDetails(items.value[0].id)
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
      <p class="panel-desc">维护食物基础营养信息，并查看概念层、概念别名、图片样本和最近识别日志。</p>

      <div class="toolbar-row top-gap">
        <input v-model="keyword" class="toolbar-input" placeholder="搜索食物名称或分类" />
        <select v-model="categoryFilter" class="toolbar-input toolbar-select">
          <option v-for="item in filterCategories" :key="item" :value="item">{{ item }}</option>
        </select>
        <span class="status-pill">{{ loading ? '同步中...' : `共 ${visibleFoods.length} 条` }}</span>
        <span v-if="metadataLoading" class="status-pill online">正在读取概念与日志</span>
      </div>
      <p v-if="saveMessage" class="metric-hint top-gap">{{ saveMessage }}</p>
    </div>

    <div class="split-grid-wide">
      <div class="panel-card">
        <div class="panel-head compact-head">
          <div>
            <p class="panel-kicker">Library</p>
            <h3 class="panel-title">食物条目</h3>
          </div>
          <p class="metric-hint">支持基础条目搜索与编辑</p>
        </div>
        <div class="table-shell top-gap">
          <table class="data-table">
            <thead>
              <tr>
                <th>食物名称</th>
                <th>分类</th>
                <th>热量</th>
                <th>蛋白质</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="food in visibleFoods"
                :key="food.id"
                class="clickable-row"
                :class="{ selected: food.id === selectedFoodId }"
                @click="applySelection(food.id)"
              >
                <td>
                  <p class="food-title">{{ food.name }}</p>
                  <p class="cell-subtitle">单位：{{ food.unit }}</p>
                </td>
                <td>{{ food.category }}</td>
                <td>{{ food.calories }} kcal</td>
                <td>{{ food.protein }} g</td>
                <td><span class="status-pill" :class="food.status === '启用' ? 'online' : 'warning'">{{ food.status }}</span></td>
                <td class="table-actions" @click.stop>
                  <button class="link-button" @click="applySelection(food.id)">查看</button>
                  <button class="link-button danger" @click="removeFood(food.id)">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
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
