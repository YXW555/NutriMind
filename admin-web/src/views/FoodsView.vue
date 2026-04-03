<script setup>
import { computed, onMounted, ref } from 'vue'
import { adminApi } from '@/lib/api'
import { foodItems } from '@/lib/mock'

const keyword = ref('')
const category = ref('全部')
const loading = ref(false)
const saveMessage = ref('')
const items = ref(foodItems.map(normalizeFood))
const editingId = ref(null)
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

const visibleFoods = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return items.value.filter((item) => {
    const matchesKeyword = !query || [item.name, item.category].join(' ').toLowerCase().includes(query)
    const matchesCategory = category.value === '全部' || item.category === category.value
    return matchesKeyword && matchesCategory
  })
})

const categories = computed(() => ['全部', ...new Set(items.value.map((item) => item.category).filter(Boolean))])

function fillForm(item) {
  editingId.value = item.id
  form.value = {
    name: item.name,
    category: item.category,
    unit: item.unit || '100g',
    calories: String(item.calories ?? ''),
    protein: String(item.protein ?? ''),
    fat: String(item.fat ?? ''),
    carbohydrate: String(item.carbohydrate ?? ''),
    fiber: String(item.fiber ?? ''),
    status: item.status || '启用'
  }
  saveMessage.value = ''
}

function resetForm(options = {}) {
  const { keepMessage = false } = options
  editingId.value = null
  form.value = createEmptyForm()
  if (!keepMessage) {
    saveMessage.value = ''
  }
}

function buildPayload() {
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

async function loadFoods() {
  loading.value = true
  saveMessage.value = ''

  try {
    const data = await adminApi.get('/foods', { current: 1, size: 100 })
    items.value = Array.isArray(data?.records) ? data.records.map(normalizeFood) : []
  } catch (error) {
    items.value = foodItems.map(normalizeFood)
    saveMessage.value = `未连接到真实食物库，当前显示演示数据：${error.message}`
  } finally {
    loading.value = false
  }
}

async function saveFood() {
  if (!form.value.name.trim()) {
    saveMessage.value = '请先填写食物名称。'
    return
  }

  const payload = buildPayload()

  try {
    const saved = editingId.value
      ? await adminApi.put(`/foods/${editingId.value}`, payload)
      : await adminApi.post('/foods', payload)

    const normalized = normalizeFood(saved)
    const nextItems = items.value.filter((item) => item.id !== normalized.id)
    items.value = [normalized, ...nextItems]
    saveMessage.value = editingId.value ? '食物条目已更新。' : '食物条目已创建。'
    resetForm({ keepMessage: true })
  } catch (error) {
    if (editingId.value) {
      items.value = items.value.map((item) => item.id === editingId.value ? { ...item, ...normalizeFood({ id: editingId.value, ...payload }) } : item)
      saveMessage.value = `后端暂不可用，已在当前页面更新：${error.message}`
    } else {
      const localId = Date.now()
      items.value = [normalizeFood({ id: localId, ...payload }), ...items.value]
      saveMessage.value = `后端暂不可用，已在当前页面创建：${error.message}`
    }
    resetForm({ keepMessage: true })
  }
}

async function removeFood(foodId) {
  try {
    await adminApi.delete(`/foods/${foodId}`)
    items.value = items.value.filter((item) => item.id !== foodId)
    if (editingId.value === foodId) {
      resetForm({ keepMessage: true })
    }
    saveMessage.value = '食物条目已删除。'
  } catch (error) {
    items.value = items.value.filter((item) => item.id !== foodId)
    if (editingId.value === foodId) {
      resetForm({ keepMessage: true })
    }
    saveMessage.value = `后端暂不可用，已在当前页面删除：${error.message}`
  }
}

onMounted(loadFoods)
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
          <button class="primary-button" @click="saveFood">{{ editingId ? '保存修改' : '新增食物' }}</button>
        </div>
      </div>
      <p class="panel-desc">维护食物分类、基础营养数据和启用状态。</p>

      <div class="toolbar-row top-gap">
        <input v-model="keyword" class="toolbar-input" placeholder="搜索食物名称或分类" />
        <select v-model="category" class="toolbar-input toolbar-select">
          <option v-for="item in categories" :key="item" :value="item">{{ item }}</option>
        </select>
        <button class="secondary-button" @click="resetForm">重置表单</button>
      </div>
      <p v-if="loading" class="metric-hint top-gap">正在同步食物库数据...</p>
      <p v-else-if="saveMessage" class="metric-hint top-gap">{{ saveMessage }}</p>
    </div>

    <div class="split-grid split-grid-wide">
      <div class="panel-card">
        <div class="panel-head compact-head">
          <div>
            <p class="panel-kicker">Entries</p>
            <h3 class="panel-title">食物条目</h3>
          </div>
          <p class="metric-hint">共 {{ visibleFoods.length }} 条</p>
        </div>
        <div class="table-shell top-gap">
          <table class="data-table">
            <thead>
              <tr>
                <th>名称</th>
                <th>分类</th>
                <th>热量</th>
                <th>蛋白质</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="food in visibleFoods" :key="food.id">
                <td>{{ food.name }}</td>
                <td>{{ food.category }}</td>
                <td>{{ food.calories }} kcal</td>
                <td>{{ food.protein }} g</td>
                <td>{{ food.status }}</td>
                <td class="table-actions">
                  <button class="link-button" @click="fillForm(food)">编辑</button>
                  <button class="link-button danger" @click="removeFood(food.id)">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="panel-card">
        <div class="panel-head compact-head">
          <div>
            <p class="panel-kicker">Editor</p>
            <h3 class="panel-title">条目编辑</h3>
          </div>
          <p class="metric-hint">{{ editingId ? `正在编辑 #${editingId}` : '新建食物条目' }}</p>
        </div>

        <div class="form-grid top-gap">
          <label class="form-field">
            <span>食物名称</span>
            <input v-model="form.name" class="toolbar-input" placeholder="例如：鸡胸肉" />
          </label>
          <label class="form-field">
            <span>分类</span>
            <input v-model="form.category" class="toolbar-input" placeholder="例如：高蛋白" />
          </label>
          <label class="form-field">
            <span>单位</span>
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
            <span>碳水</span>
            <input v-model="form.carbohydrate" class="toolbar-input" placeholder="g / 100g" />
          </label>
          <label class="form-field form-field-wide">
            <span>膳食纤维</span>
            <input v-model="form.fiber" class="toolbar-input" placeholder="g / 100g" />
          </label>
        </div>
      </div>
    </div>
  </section>
</template>
