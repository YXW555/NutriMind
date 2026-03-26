<template>
  <view class="page">
    <app-page-header
      title="食物库"
      subtitle="维护常见食物，录入每日饮食时会更省事"
      fallback-url="/pages/index/index"
    >
      <template #right>
        <view class="badge">
          <text class="badge-text">{{ foods.length }} 项</text>
        </view>
      </template>
    </app-page-header>

    <view v-if="false" class="header">
      <view>
        <text class="page-title">食物库</text>
        <text class="page-subtitle">维护常见食物，录入时会更省事</text>
      </view>
      <view class="badge">
        <text class="badge-text">{{ foods.length }} 项</text>
      </view>
    </view>

    <view class="panel">
      <text class="section-title">{{ editingId ? '编辑食物' : '新增食物' }}</text>
      <input v-model="form.name" class="field" placeholder="食物名称" />
      <input v-model="form.category" class="field" placeholder="分类，例如主食 / 蛋白质 / 水果" />
      <input v-model="form.unit" class="field" placeholder="单位，默认 100克" />

      <view class="grid">
        <input v-model="form.calories" class="field compact" type="digit" placeholder="热量" />
        <input v-model="form.protein" class="field compact" type="digit" placeholder="蛋白质" />
        <input v-model="form.carbohydrate" class="field compact" type="digit" placeholder="碳水" />
        <input v-model="form.fat" class="field compact" type="digit" placeholder="脂肪" />
      </view>

      <input v-model="form.fiber" class="field" type="digit" placeholder="膳食纤维" />

      <view class="button-row">
        <button class="primary-button" @click="submitFood">{{ editingId ? '保存修改' : '新增食物' }}</button>
        <button class="secondary-button" @click="resetForm">清空</button>
      </view>
    </view>

    <view class="panel">
      <text class="section-title">查找与筛选</text>
      <view class="search-row">
        <input v-model="keyword" class="field search-field" placeholder="按名称搜索" />
        <button class="search-button" @click="loadFoods">搜索</button>
      </view>

      <scroll-view scroll-x class="tag-scroll" show-scrollbar="false">
        <view class="tag-row">
          <view
            v-for="item in categoryOptions"
            :key="item.value || 'all'"
            class="tag-chip"
            :class="{ active: selectedCategory === item.value }"
            @click="applyCategory(item.value)"
          >
            <text class="tag-chip-text">{{ item.label }}</text>
          </view>
        </view>
      </scroll-view>
    </view>

    <view v-if="!foods.length" class="empty-card">
      <text class="empty-title">当前没有找到食物</text>
      <text class="empty-desc">服务启动后会自动注入一批常用食物，你也可以自己继续扩充。</text>
    </view>

    <view v-for="food in foods" :key="food.id" class="food-card">
      <view class="food-head">
        <view>
          <text class="food-name">{{ food.name }}</text>
          <text class="food-meta">{{ food.category || '未分类' }} · {{ food.unit || '100克' }}</text>
        </view>
        <text class="food-kcal">{{ formatNumber(food.calories) }} 千卡</text>
      </view>

      <view class="nutrition-grid">
        <view class="nutrition-cell">
          <text class="nutrition-label">蛋白质</text>
          <text class="nutrition-value">{{ formatNumber(food.protein, 1) }}克</text>
        </view>
        <view class="nutrition-cell">
          <text class="nutrition-label">碳水</text>
          <text class="nutrition-value">{{ formatNumber(food.carbohydrate, 1) }}克</text>
        </view>
        <view class="nutrition-cell">
          <text class="nutrition-label">脂肪</text>
          <text class="nutrition-value">{{ formatNumber(food.fat, 1) }}克</text>
        </view>
        <view class="nutrition-cell">
          <text class="nutrition-label">纤维</text>
          <text class="nutrition-value">{{ formatNumber(food.fiber, 1) }}克</text>
        </view>
      </view>

      <view class="button-row">
        <button class="secondary-button" @click="startEdit(food)">编辑</button>
        <button class="danger-button" @click="removeFood(food.id)">删除</button>
      </view>
    </view>

  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { ensureLoggedIn } from '@/utils/auth.js'
import { formatNumber } from '@/utils/format.js'

const defaultCategories = ['主食', '蛋白质', '蔬菜', '水果', '饮品', '零食']
const foods = ref([])
const keyword = ref('')
const selectedCategory = ref('')
const editingId = ref(null)
const form = ref(createEmptyForm())

const categoryOptions = computed(() => {
  const dynamic = foods.value
    .map(item => item.category)
    .filter(Boolean)
    .filter((item, index, array) => array.indexOf(item) === index)

  return [
    { label: '全部', value: '' },
    ...Array.from(new Set([...defaultCategories, ...dynamic])).map(item => ({ label: item, value: item }))
  ]
})

function createEmptyForm() {
  return {
    name: '',
    category: '',
    unit: '100克',
    calories: '',
    protein: '',
    carbohydrate: '',
    fat: '',
    fiber: ''
  }
}

function toNumber(value) {
  const numeric = Number(value)
  return Number.isNaN(numeric) ? 0 : numeric
}

function resetForm() {
  editingId.value = null
  form.value = createEmptyForm()
}

function applyCategory(category) {
  selectedCategory.value = category
  loadFoods()
}

function startEdit(food) {
  editingId.value = food.id
  form.value = {
    name: food.name || '',
    category: food.category || '',
    unit: food.unit || '100克',
    calories: `${food.calories ?? ''}`,
    protein: `${food.protein ?? ''}`,
    carbohydrate: `${food.carbohydrate ?? ''}`,
    fat: `${food.fat ?? ''}`,
    fiber: `${food.fiber ?? ''}`
  }
}

async function loadFoods() {
  if (!ensureLoggedIn()) {
    return
  }
  try {
    const response = await request.get('/foods', {
      keyword: keyword.value,
      category: selectedCategory.value,
      current: 1,
      size: 50
    })
    foods.value = Array.isArray(response?.records) ? response.records : []
  } catch (error) {
    console.log('load foods failed', error)
  }
}

async function submitFood() {
  if (!ensureLoggedIn()) {
    return
  }
  if (!form.value.name.trim()) {
    uni.showToast({
      title: '食物名称不能为空',
      icon: 'none'
    })
    return
  }

  const payload = {
    name: form.value.name.trim(),
    category: form.value.category.trim(),
    unit: form.value.unit.trim() || '100克',
    calories: toNumber(form.value.calories),
    protein: toNumber(form.value.protein),
    carbohydrate: toNumber(form.value.carbohydrate),
    fat: toNumber(form.value.fat),
    fiber: toNumber(form.value.fiber)
  }

  try {
    if (editingId.value) {
      await request.put(`/foods/${editingId.value}`, payload)
    } else {
      await request.post('/foods', payload)
    }
    uni.showToast({
      title: editingId.value ? '保存成功' : '新增成功',
      icon: 'success'
    })
    resetForm()
    loadFoods()
  } catch (error) {
    console.log('submit food failed', error)
  }
}

function removeFood(id) {
  uni.showModal({
    title: '删除食物',
    content: '确认删除这条食物吗？',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      try {
        await request.delete(`/foods/${id}`)
        uni.showToast({
          title: '删除成功',
          icon: 'success'
        })
        loadFoods()
      } catch (error) {
        console.log('delete food failed', error)
      }
    }
  })
}

onShow(() => {
  loadFoods()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 32rpx 28rpx 80rpx;
}

.header,
.food-head,
.button-row,
.search-row {
  display: flex;
}

.header,
.food-head,
.button-row,
.search-row {
  justify-content: space-between;
}

.header {
  align-items: center;
}

.page-title {
  display: block;
  font-size: 58rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.page-subtitle,
.food-meta,
.empty-desc,
.nutrition-label {
  font-size: 26rpx;
  color: var(--nm-muted);
}

.page-subtitle {
  margin-top: 12rpx;
}

.badge {
  padding: 16rpx 22rpx;
  border-radius: 999rpx;
  background: rgba(14, 165, 109, 0.14);
}

.badge-text {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--nm-primary);
}

.panel,
.empty-card,
.food-card {
  margin-top: 26rpx;
  padding: 28rpx;
  border-radius: 34rpx;
  background: var(--nm-card);
  box-shadow: var(--nm-shadow);
}

.section-title,
.empty-title,
.food-name {
  display: block;
  font-size: 34rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.field {
  width: 100%;
  height: 88rpx;
  margin-top: 16rpx;
  padding: 0 24rpx;
  border-radius: 24rpx;
  background: #f5f4ef;
  font-size: 28rpx;
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14rpx;
}

.compact {
  margin-top: 0;
}

.button-row {
  gap: 16rpx;
  margin-top: 22rpx;
}

.primary-button,
.secondary-button,
.danger-button,
.search-button {
  flex: 1;
  height: 88rpx;
  font-size: 28rpx;
  font-weight: 700;
}

.primary-button,
.search-button {
  background: var(--nm-primary-dark);
  color: #ffffff;
}

.secondary-button {
  background: #eceae2;
  color: var(--nm-text);
}

.danger-button {
  background: #f8dfdf;
  color: #a63f3f;
}

.search-field {
  margin-top: 0;
  flex: 1;
}

.search-row {
  gap: 14rpx;
  margin-top: 18rpx;
}

.search-button {
  max-width: 160rpx;
}

.tag-scroll {
  margin-top: 18rpx;
  white-space: nowrap;
}

.tag-row {
  display: inline-flex;
  gap: 12rpx;
}

.tag-chip {
  padding: 14rpx 22rpx;
  border-radius: 999rpx;
  background: #f1eee7;
}

.tag-chip.active {
  background: var(--nm-primary);
}

.tag-chip-text {
  font-size: 24rpx;
  font-weight: 700;
  color: #6b6252;
}

.tag-chip.active .tag-chip-text {
  color: #ffffff;
}

.food-kcal {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--nm-primary);
}

.nutrition-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14rpx;
  margin-top: 22rpx;
}

.nutrition-cell {
  padding: 20rpx;
  border-radius: 24rpx;
  background: #f5f4ef;
}

.nutrition-value {
  display: block;
  margin-top: 10rpx;
  font-size: 28rpx;
  font-weight: 800;
  color: var(--nm-text);
}
</style>
