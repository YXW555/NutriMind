<template>
  <view class="page">
    <app-page-header
      title="我的"
      subtitle="管理健康档案、目标和体重记录"
      :show-back="false"
    />

    <view class="hero-card">
      <view class="hero-main">
        <view>
          <text class="hero-name">{{ overview.nickname || overview.username || '未命名用户' }}</text>
          <text class="hero-role">{{ roleLabel }}</text>
        </view>

        <view class="avatar-wrap">
          <view class="avatar-circle avatar-action" @click="toggleAccountInfo">
            <text class="avatar-text">{{ avatarText }}</text>
          </view>
          <text class="avatar-tip">{{ showAccountInfo ? '点击收起账号信息' : '点击头像查看账号信息' }}</text>
        </view>
      </view>

      <view class="hero-metrics">
        <view class="metric-pill">
          <text class="metric-label">最新体重</text>
          <text class="metric-value">{{ latestWeightText }}</text>
        </view>
        <view class="metric-pill">
          <text class="metric-label">当前目标</text>
          <text class="metric-value">{{ goalTypeLabel }}</text>
        </view>
        <view class="metric-pill">
          <text class="metric-label">目标热量</text>
          <text class="metric-value">{{ goalCaloriesText }}</text>
        </view>
      </view>

      <view v-if="showAccountInfo" class="account-sheet">
        <view class="account-sheet-head">
          <view>
            <text class="account-sheet-title">账号信息</text>
            <text class="account-sheet-desc">这里只保留查看，不在页面里平铺展示。</text>
          </view>
          <text class="account-sheet-link" @click="toggleAccountInfo">收起</text>
        </view>

        <view class="info-list">
          <view class="info-row">
            <text class="info-label">用户名</text>
            <text class="info-value">{{ overview.username || '--' }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">用户编号</text>
            <text class="info-value">{{ overview.userId || '--' }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">昵称</text>
            <text class="info-value">{{ overview.nickname || '暂未填写' }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">邮箱</text>
            <text class="info-value">{{ overview.email || '暂未填写' }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">手机号</text>
            <text class="info-value">{{ overview.phone || '暂未填写' }}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="panel">
      <view class="section-head">
        <view>
          <text class="section-title">健康档案</text>
          <text class="section-desc">身高、活动水平和饮食偏好会影响计划建议。</text>
        </view>
        <text class="section-status" :class="{ editing: isEditingHealthProfile }">
          {{ healthProfileStatusText }}
        </text>
      </view>

      <view class="form-grid">
        <picker
          class="picker-wrap"
          :class="{ 'picker-wrap-disabled': !isEditingHealthProfile }"
          :range="genderOptions"
          range-key="label"
          :value="genderIndex"
          @change="handleGenderChange"
        >
          <view class="picker-field" :class="{ readonly: !isEditingHealthProfile }">{{ genderLabel }}</view>
        </picker>
        <picker
          mode="date"
          :disabled="!isEditingHealthProfile"
          :value="healthForm.birthDate"
          @change="handleBirthDateChange"
        >
          <view class="picker-field" :class="{ readonly: !isEditingHealthProfile }">{{ healthForm.birthDate || '选择出生日期' }}</view>
        </picker>
        <input
          v-model="healthForm.heightCm"
          :class="['field-input', { readonly: !isEditingHealthProfile }]"
          type="digit"
          placeholder="身高(cm)"
          confirm-type="next"
          cursor-spacing="24"
          :disabled="!isEditingHealthProfile"
          adjust-position
        />
        <picker
          class="picker-wrap"
          :class="{ 'picker-wrap-disabled': !isEditingHealthProfile }"
          :range="activityOptions"
          range-key="label"
          :value="activityIndex"
          @change="handleActivityChange"
        >
          <view class="picker-field" :class="{ readonly: !isEditingHealthProfile }">{{ activityLabel }}</view>
        </picker>
      </view>

      <input
        v-model="healthForm.dietaryPreference"
        :class="['field-input', { readonly: !isEditingHealthProfile }]"
        placeholder="饮食偏好，例如低糖、素食"
        confirm-type="next"
        cursor-spacing="24"
        :disabled="!isEditingHealthProfile"
        adjust-position
      />
      <input
        v-model="healthForm.allergies"
        :class="['field-input', { readonly: !isEditingHealthProfile }]"
        placeholder="过敏原，例如花生、海鲜"
        confirm-type="next"
        cursor-spacing="24"
        :disabled="!isEditingHealthProfile"
        adjust-position
      />
      <textarea
        v-model="healthForm.medicalNotes"
        :class="['textarea-field', { readonly: !isEditingHealthProfile }]"
        maxlength="300"
        auto-height
        :disabled="!isEditingHealthProfile"
        placeholder="补充说明，例如胃部不适、健身周期、作息情况"
      ></textarea>

      <button
        :class="isEditingHealthProfile ? 'primary-button' : 'secondary-button'"
        :loading="savingHealthProfile"
        :disabled="savingHealthProfile"
        @click="handleHealthProfileAction"
      >
        {{ healthProfileActionLabel }}
      </button>
    </view>

    <view class="panel">
      <view class="section-head">
        <view>
          <text class="section-title">健康目标</text>
          <text class="section-desc">把目标热量和宏量营养素定下来，后面更容易对照执行。</text>
        </view>
        <text class="section-status" :class="{ editing: isEditingHealthGoal }">
          {{ healthGoalStatusText }}
        </text>
      </view>

      <picker
        class="picker-wrap"
        :class="{ 'picker-wrap-disabled': !isEditingHealthGoal }"
        :range="goalOptions"
        range-key="label"
        :value="goalTypeIndex"
        @change="handleGoalTypeChange"
      >
        <view class="picker-field" :class="{ readonly: !isEditingHealthGoal }">{{ goalTypePickerLabel }}</view>
      </picker>

      <view class="form-grid">
        <input v-model="goalForm.targetCalories" :class="['field-input', { readonly: !isEditingHealthGoal }]" type="digit" placeholder="目标热量(kcal)" confirm-type="next" cursor-spacing="24" :disabled="!isEditingHealthGoal" adjust-position />
        <input v-model="goalForm.targetWeightKg" :class="['field-input', { readonly: !isEditingHealthGoal }]" type="digit" placeholder="目标体重(kg)" confirm-type="next" cursor-spacing="24" :disabled="!isEditingHealthGoal" adjust-position />
        <input v-model="goalForm.targetProtein" :class="['field-input', { readonly: !isEditingHealthGoal }]" type="digit" placeholder="目标蛋白质(g)" confirm-type="next" cursor-spacing="24" :disabled="!isEditingHealthGoal" adjust-position />
        <input v-model="goalForm.targetCarbohydrate" :class="['field-input', { readonly: !isEditingHealthGoal }]" type="digit" placeholder="目标碳水(g)" confirm-type="next" cursor-spacing="24" :disabled="!isEditingHealthGoal" adjust-position />
        <input v-model="goalForm.targetFat" :class="['field-input', { readonly: !isEditingHealthGoal }]" type="digit" placeholder="目标脂肪(g)" confirm-type="next" cursor-spacing="24" :disabled="!isEditingHealthGoal" adjust-position />
        <input v-model="goalForm.weeklyChangeKg" :class="['field-input', { readonly: !isEditingHealthGoal }]" type="digit" placeholder="每周变化(kg)" confirm-type="next" cursor-spacing="24" :disabled="!isEditingHealthGoal" adjust-position />
      </view>

      <view class="form-grid">
        <picker mode="date" :disabled="!isEditingHealthGoal" :value="goalForm.startDate" @change="event => (goalForm.startDate = event.detail.value)">
          <view class="picker-field" :class="{ readonly: !isEditingHealthGoal }">{{ goalForm.startDate || '开始日期' }}</view>
        </picker>
        <picker mode="date" :disabled="!isEditingHealthGoal" :value="goalForm.endDate" @change="event => (goalForm.endDate = event.detail.value)">
          <view class="picker-field" :class="{ readonly: !isEditingHealthGoal }">{{ goalForm.endDate || '结束日期' }}</view>
        </picker>
      </view>

      <textarea
        v-model="goalForm.note"
        :class="['textarea-field', { readonly: !isEditingHealthGoal }]"
        maxlength="300"
        auto-height
        :disabled="!isEditingHealthGoal"
        placeholder="补充目标说明，例如想在 8 周内减脂 4kg"
      ></textarea>

      <button
        :class="isEditingHealthGoal ? 'primary-button' : 'secondary-button'"
        :loading="savingHealthGoal"
        :disabled="savingHealthGoal"
        @click="handleHealthGoalAction"
      >
        {{ healthGoalActionLabel }}
      </button>
    </view>

    <view class="panel">
      <view class="section-head">
        <view>
          <text class="section-title">体重记录</text>
          <text class="section-desc">建议固定时间记录，趋势会更稳定。</text>
        </view>
      </view>

      <view class="weight-form-row">
        <picker mode="date" :value="weightForm.recordDate" @change="event => (weightForm.recordDate = event.detail.value)">
          <view class="picker-field weight-date-field">{{ weightForm.recordDate }}</view>
        </picker>
        <input
          v-model="weightForm.weightKg"
          class="field-input weight-value-field"
          type="digit"
          placeholder="体重(kg)"
          confirm-type="done"
          cursor-spacing="24"
          adjust-position
        />
      </view>

      <input
        v-model="weightForm.note"
        class="field-input"
        placeholder="备注，例如晨起空腹、训练后"
        confirm-type="done"
        cursor-spacing="24"
        adjust-position
      />

      <view class="button-row">
        <button class="secondary-button" @click="resetWeightForm">重置</button>
        <button
          class="primary-button"
          :loading="savingWeightLog"
          :disabled="savingWeightLog"
          @click="saveWeightLog"
        >
          {{ savingWeightLog ? '保存中...' : '保存体重' }}
        </button>
      </view>

      <view v-if="!weightLogs.length" class="empty-inline-card">
        <text class="empty-inline-title">还没有体重记录</text>
        <text class="empty-inline-desc">先记下今天的体重，后面就能逐步看到趋势。</text>
      </view>

      <view v-for="item in weightLogs" :key="item.id" class="weight-log-card">
        <view class="weight-log-main">
          <text class="weight-log-value">{{ formatNumber(item.weightKg, 1) }} kg</text>
          <text class="weight-log-meta">{{ item.recordDate }}{{ item.note ? ` · ${item.note}` : '' }}</text>
        </view>
        <button class="danger-button small" @click="deleteWeightLog(item.id)">删除</button>
      </view>
    </view>

    <view class="action-row">
      <button class="secondary-button" @click="loadOverview">刷新资料</button>
      <button class="danger-button" @click="logout">退出登录</button>
    </view>

    <app-tab-bar current="profile" />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { clearSession, formatToday, getToken, isLoggedIn, openAuthPage, saveSession } from '@/utils/auth.js'
import { formatNumber } from '@/utils/format.js'

const genderOptions = [
  { label: '性别未设置', value: '' },
  { label: '男', value: 'MALE' },
  { label: '女', value: 'FEMALE' },
  { label: '其他', value: 'OTHER' }
]

const activityOptions = [
  { label: '活动水平未设置', value: '' },
  { label: '低活动', value: 'LOW' },
  { label: '中等活动', value: 'MEDIUM' },
  { label: '高活动', value: 'HIGH' }
]

const goalOptions = [
  { label: '均衡饮食', value: 'BALANCE' },
  { label: '减脂', value: 'FAT_LOSS' },
  { label: '增肌', value: 'MUSCLE_GAIN' },
  { label: '维持', value: 'MAINTAIN' }
]

const overview = ref({})
const weightLogs = ref([])
const healthForm = ref(createEmptyHealthForm())
const goalForm = ref(createEmptyGoalForm())
const weightForm = ref(createEmptyWeightForm())
const showAccountInfo = ref(false)
const isEditingHealthProfile = ref(true)
const isEditingHealthGoal = ref(true)
const savingHealthProfile = ref(false)
const savingHealthGoal = ref(false)
const savingWeightLog = ref(false)

const avatarText = computed(() => {
  const source = overview.value.nickname || overview.value.username || '我'
  return String(source).slice(0, 1)
})

const roleLabel = computed(() => {
  if (overview.value.role === 'ADMIN') {
    return '管理员'
  }
  if (overview.value.role === 'USER') {
    return '普通用户'
  }
  return '用户'
})

const latestWeightText = computed(() => {
  if (!overview.value.latestWeightKg) {
    return '待记录'
  }
  return `${formatNumber(overview.value.latestWeightKg, 1)} kg`
})

const goalTypeLabel = computed(() => {
  const current = goalOptions.find(item => item.value === goalForm.value.goalType)
  return current ? current.label : '未设置'
})

const goalCaloriesText = computed(() => {
  if (!goalForm.value.targetCalories) {
    return '未设置'
  }
  return `${formatNumber(goalForm.value.targetCalories)} kcal`
})

const hasSavedHealthProfile = computed(() => hasHealthProfileContent(overview.value?.healthProfile))
const hasSavedHealthGoal = computed(() => hasHealthGoalContent(overview.value?.healthGoal))

const healthProfileActionLabel = computed(() => {
  if (savingHealthProfile.value) {
    return '保存中...'
  }
  return isEditingHealthProfile.value ? '保存健康档案' : '编辑健康档案'
})

const healthGoalActionLabel = computed(() => {
  if (savingHealthGoal.value) {
    return '保存中...'
  }
  return isEditingHealthGoal.value ? '保存目标' : '编辑目标'
})

const healthProfileStatusText = computed(() => {
  if (isEditingHealthProfile.value) {
    return hasSavedHealthProfile.value ? '编辑中' : '待填写'
  }
  return '已保存'
})

const healthGoalStatusText = computed(() => {
  if (isEditingHealthGoal.value) {
    return hasSavedHealthGoal.value ? '编辑中' : '待填写'
  }
  return '已保存'
})

const genderIndex = computed(() => {
  const index = genderOptions.findIndex(item => item.value === healthForm.value.gender)
  return index >= 0 ? index : 0
})

const activityIndex = computed(() => {
  const index = activityOptions.findIndex(item => item.value === healthForm.value.activityLevel)
  return index >= 0 ? index : 0
})

const goalTypeIndex = computed(() => {
  const index = goalOptions.findIndex(item => item.value === goalForm.value.goalType)
  return index >= 0 ? index : 0
})

const genderLabel = computed(() => genderOptions[genderIndex.value]?.label || '选择性别')
const activityLabel = computed(() => activityOptions[activityIndex.value]?.label || '选择活动水平')
const goalTypePickerLabel = computed(() => goalOptions[goalTypeIndex.value]?.label || '选择目标')

function createEmptyHealthForm() {
  return {
    gender: '',
    birthDate: '',
    heightCm: '',
    activityLevel: '',
    dietaryPreference: '',
    allergies: '',
    medicalNotes: ''
  }
}

function createEmptyGoalForm() {
  return {
    goalType: 'BALANCE',
    targetCalories: '',
    targetProtein: '',
    targetFat: '',
    targetCarbohydrate: '',
    targetWeightKg: '',
    weeklyChangeKg: '',
    startDate: '',
    endDate: '',
    note: ''
  }
}

function createEmptyWeightForm() {
  return {
    weightKg: '',
    recordDate: formatToday(),
    note: ''
  }
}

function normalizeOptionalText(value) {
  const normalized = String(value ?? '').trim()
  return normalized || null
}

function normalizeOptionalNumber(value) {
  const normalized = String(value ?? '').trim()
  if (!normalized) {
    return null
  }

  const parsed = Number(normalized)
  return Number.isFinite(parsed) ? parsed : Number.NaN
}

function showInlineToast(title, icon = 'none') {
  uni.showToast({
    title,
    icon
  })
}

function showSavingIndicator(title = '保存中') {
  if (typeof uni.hideKeyboard === 'function') {
    uni.hideKeyboard()
  }
  uni.showLoading({
    title,
    mask: true
  })
}

function hasHealthProfileContent(profile) {
  const current = profile || {}
  return Boolean(
    current.gender
    || current.birthDate
    || current.heightCm
    || current.activityLevel
    || current.dietaryPreference
    || current.allergies
    || current.medicalNotes
  )
}

function hasHealthGoalContent(goal) {
  const current = goal || {}
  return Boolean(
    current.id
    || current.targetCalories
    || current.targetProtein
    || current.targetFat
    || current.targetCarbohydrate
    || current.targetWeightKg
    || current.weeklyChangeKg
    || current.startDate
    || current.endDate
    || current.note
    || (current.goalType && current.goalType !== 'BALANCE')
  )
}

function assignForms(data) {
  const profile = data?.healthProfile || {}
  const goal = data?.healthGoal || {}
  healthForm.value = {
    gender: profile.gender || '',
    birthDate: profile.birthDate || '',
    heightCm: profile.heightCm ? `${profile.heightCm}` : '',
    activityLevel: profile.activityLevel || '',
    dietaryPreference: profile.dietaryPreference || '',
    allergies: profile.allergies || '',
    medicalNotes: profile.medicalNotes || ''
  }
  goalForm.value = {
    goalType: goal.goalType || 'BALANCE',
    targetCalories: goal.targetCalories ? `${goal.targetCalories}` : '',
    targetProtein: goal.targetProtein ? `${goal.targetProtein}` : '',
    targetFat: goal.targetFat ? `${goal.targetFat}` : '',
    targetCarbohydrate: goal.targetCarbohydrate ? `${goal.targetCarbohydrate}` : '',
    targetWeightKg: goal.targetWeightKg ? `${goal.targetWeightKg}` : '',
    weeklyChangeKg: goal.weeklyChangeKg ? `${goal.weeklyChangeKg}` : '',
    startDate: goal.startDate || '',
    endDate: goal.endDate || '',
    note: goal.note || ''
  }
  weightLogs.value = Array.isArray(data?.recentWeightLogs) ? data.recentWeightLogs : []
  isEditingHealthProfile.value = !hasHealthProfileContent(profile)
  isEditingHealthGoal.value = !hasHealthGoalContent(goal)
}

async function loadOverview() {
  if (!isLoggedIn()) {
    openAuthPage()
    return
  }

  try {
    const data = await request.get('/profile/overview')
    overview.value = data || {}
    saveSession(getToken(), {
      userId: data?.userId,
      username: data?.username,
      nickname: data?.nickname,
      email: data?.email,
      phone: data?.phone,
      role: data?.role
    })
    assignForms(data)
  } catch (error) {
    console.log('load profile overview failed', error)
  }
}

function handleGenderChange(event) {
  if (!isEditingHealthProfile.value) {
    return
  }
  const selected = genderOptions[Number(event.detail.value || 0)]
  healthForm.value.gender = selected ? selected.value : ''
}

function handleActivityChange(event) {
  if (!isEditingHealthProfile.value) {
    return
  }
  const selected = activityOptions[Number(event.detail.value || 0)]
  healthForm.value.activityLevel = selected ? selected.value : ''
}

function handleGoalTypeChange(event) {
  if (!isEditingHealthGoal.value) {
    return
  }
  const selected = goalOptions[Number(event.detail.value || 0)]
  goalForm.value.goalType = selected ? selected.value : 'BALANCE'
}

function handleBirthDateChange(event) {
  if (!isEditingHealthProfile.value) {
    return
  }
  healthForm.value.birthDate = event.detail.value
}

async function handleHealthProfileAction() {
  if (!isEditingHealthProfile.value && hasSavedHealthProfile.value) {
    isEditingHealthProfile.value = true
    return
  }
  await saveHealthProfile()
}

async function handleHealthGoalAction() {
  if (!isEditingHealthGoal.value && hasSavedHealthGoal.value) {
    isEditingHealthGoal.value = true
    return
  }
  await saveHealthGoal()
}

async function saveHealthProfile() {
  if (savingHealthProfile.value) {
    return
  }

  const heightCm = normalizeOptionalNumber(healthForm.value.heightCm)
  if (Number.isNaN(heightCm)) {
    showInlineToast('请输入正确身高')
    return
  }
  if (heightCm !== null && (heightCm < 50 || heightCm > 260)) {
    showInlineToast('身高需在50-260cm')
    return
  }

  savingHealthProfile.value = true
  showSavingIndicator()
  try {
    await request.put('/profile/health', {
      gender: healthForm.value.gender || null,
      birthDate: healthForm.value.birthDate || null,
      heightCm,
      activityLevel: healthForm.value.activityLevel || null,
      dietaryPreference: normalizeOptionalText(healthForm.value.dietaryPreference),
      allergies: normalizeOptionalText(healthForm.value.allergies),
      medicalNotes: normalizeOptionalText(healthForm.value.medicalNotes)
    })
    isEditingHealthProfile.value = false
    await loadOverview()
    showInlineToast('档案已保存', 'success')
  } catch (error) {
    console.log('save health profile failed', error)
  } finally {
    savingHealthProfile.value = false
    uni.hideLoading()
  }
}

async function saveHealthGoal() {
  if (savingHealthGoal.value) {
    return
  }

  const targetCalories = normalizeOptionalNumber(goalForm.value.targetCalories)
  const targetProtein = normalizeOptionalNumber(goalForm.value.targetProtein)
  const targetFat = normalizeOptionalNumber(goalForm.value.targetFat)
  const targetCarbohydrate = normalizeOptionalNumber(goalForm.value.targetCarbohydrate)
  const targetWeightKg = normalizeOptionalNumber(goalForm.value.targetWeightKg)
  const weeklyChangeKg = normalizeOptionalNumber(goalForm.value.weeklyChangeKg)
  const nonNegativeFields = [
    { value: targetCalories, label: '目标热量' },
    { value: targetProtein, label: '目标蛋白质' },
    { value: targetFat, label: '目标脂肪' },
    { value: targetCarbohydrate, label: '目标碳水' },
    { value: targetWeightKg, label: '目标体重' }
  ]

  for (const field of nonNegativeFields) {
    if (Number.isNaN(field.value)) {
      showInlineToast(`${field.label}格式不正确`)
      return
    }
    if (field.value !== null && field.value < 0) {
      showInlineToast(`${field.label}不能小于0`)
      return
    }
  }

  if (Number.isNaN(weeklyChangeKg)) {
    showInlineToast('每周变化格式不正确')
    return
  }
  if (goalForm.value.startDate && goalForm.value.endDate && goalForm.value.endDate < goalForm.value.startDate) {
    showInlineToast('结束日期不能早于开始日期')
    return
  }

  savingHealthGoal.value = true
  showSavingIndicator()
  try {
    await request.put('/profile/goal', {
      goalType: goalForm.value.goalType || 'BALANCE',
      targetCalories,
      targetProtein,
      targetFat,
      targetCarbohydrate,
      targetWeightKg,
      weeklyChangeKg,
      startDate: goalForm.value.startDate || null,
      endDate: goalForm.value.endDate || null,
      note: normalizeOptionalText(goalForm.value.note)
    })
    isEditingHealthGoal.value = false
    await loadOverview()
    showInlineToast('目标已保存', 'success')
  } catch (error) {
    console.log('save health goal failed', error)
  } finally {
    savingHealthGoal.value = false
    uni.hideLoading()
  }
}

async function saveWeightLog() {
  if (savingWeightLog.value) {
    return
  }

  const weightKg = normalizeOptionalNumber(weightForm.value.weightKg)
  if (weightKg === null) {
    showInlineToast('请先填写体重')
    return
  }
  if (Number.isNaN(weightKg) || weightKg <= 0) {
    showInlineToast('请输入正确体重')
    return
  }

  savingWeightLog.value = true
  showSavingIndicator()
  try {
    await request.post('/profile/weights', {
      weightKg,
      recordDate: weightForm.value.recordDate,
      note: normalizeOptionalText(weightForm.value.note)
    })
    resetWeightForm()
    await loadOverview()
    showInlineToast('体重已保存', 'success')
  } catch (error) {
    console.log('save weight log failed', error)
  } finally {
    savingWeightLog.value = false
    uni.hideLoading()
  }
}

function deleteWeightLog(logId) {
  uni.showModal({
    title: '删除记录',
    content: '确认删除这条体重记录吗？',
    success: async (result) => {
      if (!result.confirm) {
        return
      }
      try {
        await request.delete(`/profile/weights/${logId}`)
        loadOverview()
      } catch (error) {
        console.log('delete weight log failed', error)
      }
    }
  })
}

function resetWeightForm() {
  weightForm.value = createEmptyWeightForm()
}

function toggleAccountInfo() {
  showAccountInfo.value = !showAccountInfo.value
}

function logout() {
  clearSession()
  uni.showToast({
    title: '已退出登录',
    icon: 'success'
  })

  setTimeout(() => {
    openAuthPage()
  }, 250)
}

onShow(() => {
  if (!isLoggedIn()) {
    openAuthPage()
    return
  }
  loadOverview()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 32rpx 28rpx 220rpx;
}

.hero-card,
.panel {
  margin-top: 24rpx;
  padding: 30rpx 28rpx;
  border-radius: 34rpx;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: var(--nm-shadow);
}

.hero-card {
  background: linear-gradient(145deg, #fff3d7 0%, #fffaf0 100%);
}

.hero-main,
.section-head,
.info-row,
.action-row,
.button-row,
.weight-form-row,
.weight-log-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.hero-name {
  display: block;
  font-size: 52rpx;
  font-weight: 800;
  color: #111827;
}

.hero-role {
  display: inline-flex;
  margin-top: 12rpx;
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.82);
  font-size: 24rpx;
  color: #9a3412;
}

.avatar-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
}

.avatar-circle {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.avatar-action {
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.avatar-text {
  font-size: 42rpx;
  font-weight: 800;
  color: #c2410c;
}

.avatar-tip {
  font-size: 22rpx;
  color: #7c5b33;
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14rpx;
  margin-top: 24rpx;
}

.account-sheet {
  margin-top: 24rpx;
  padding: 24rpx;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.82);
  border: 1rpx solid rgba(194, 132, 12, 0.08);
}

.account-sheet-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
}

.account-sheet-title {
  display: block;
  font-size: 30rpx;
  font-weight: 800;
  color: #111827;
}

.account-sheet-desc,
.account-sheet-link {
  font-size: 24rpx;
  color: #7c5b33;
}

.account-sheet-desc {
  display: block;
  margin-top: 8rpx;
  line-height: 1.6;
}

.account-sheet-link {
  flex-shrink: 0;
  font-weight: 700;
}

.metric-pill {
  padding: 20rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.88);
}

.metric-label,
.section-desc,
.info-label,
.shortcut-desc,
.empty-inline-desc,
.weight-log-meta {
  font-size: 24rpx;
  color: #64748b;
}

.metric-value {
  display: block;
  margin-top: 10rpx;
  font-size: 28rpx;
  font-weight: 800;
  color: #111827;
}

.section-title {
  display: block;
  font-size: 36rpx;
  font-weight: 800;
  color: #111827;
}

.section-desc {
  display: block;
  margin-top: 10rpx;
  line-height: 1.7;
}

.section-status {
  flex-shrink: 0;
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(14, 165, 109, 0.12);
  font-size: 24rpx;
  font-weight: 700;
  color: var(--nm-primary-dark);
}

.section-status.editing {
  background: rgba(59, 130, 246, 0.12);
  color: #1d4ed8;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 18rpx;
  margin-top: 22rpx;
}

.info-list {
  margin-top: 20rpx;
}

.info-row {
  padding: 18rpx 0;
  border-bottom: 1rpx solid rgba(15, 23, 42, 0.06);
}

.info-row:last-child {
  border-bottom: none;
}

.info-value {
  flex: 1;
  text-align: right;
  font-size: 26rpx;
  color: #111827;
  word-break: break-all;
}

.picker-wrap,
.field-input,
.picker-field,
.textarea-field {
  width: 100%;
}

.picker-wrap {
  display: block;
}

.picker-wrap-disabled {
  pointer-events: none;
}

.field-input,
.picker-field,
.textarea-field {
  box-sizing: border-box;
  margin-top: 16rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  background: #f5f4ef;
  font-size: 28rpx;
  color: #111827;
  border: 1rpx solid rgba(15, 23, 42, 0.04);
}

.field-input,
.picker-field {
  min-height: 92rpx;
}

.field-input {
  display: block;
  line-height: 44rpx;
}

.picker-field {
  display: flex;
  align-items: center;
}

.field-input.readonly,
.picker-field.readonly,
.textarea-field.readonly {
  background: #eef2f7;
  color: #64748b;
  border-color: rgba(100, 116, 139, 0.12);
}

.form-grid .field-input,
.form-grid .picker-field {
  margin-top: 0;
}

.textarea-field {
  min-height: 180rpx;
  line-height: 1.65;
}

.weight-form-row .field-input,
.weight-form-row .picker-field {
  margin-top: 16rpx;
}

.weight-date-field,
.weight-value-field {
  margin-top: 0;
}

.weight-date-field {
  flex: 1.1;
}

.weight-value-field {
  flex: 1;
}

.primary-button,
.secondary-button,
.danger-button {
  margin-top: 20rpx;
  height: 88rpx;
  border-radius: 24rpx;
  font-size: 28rpx;
  font-weight: 700;
}

.primary-button {
  background: var(--nm-primary-dark);
  color: #ffffff;
}

.primary-button[disabled],
.secondary-button[disabled],
.danger-button[disabled] {
  opacity: 0.72;
}

.secondary-button {
  background: #dbeafe;
  color: #1d4ed8;
}

.danger-button {
  background: #fee2e2;
  color: #991b1b;
}

.danger-button.small {
  margin-top: 0;
  min-width: 120rpx;
  height: 72rpx;
  font-size: 24rpx;
}

.button-row button,
.action-row button {
  flex: 1;
}

.empty-inline-card {
  margin-top: 20rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  background: #f7f5ef;
}

.empty-inline-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #111827;
}

.weight-log-card {
  margin-top: 18rpx;
  padding: 22rpx;
  border-radius: 24rpx;
  background: #f8fafc;
}

.weight-log-main {
  flex: 1;
  min-width: 0;
}

.weight-log-value {
  display: block;
  font-size: 32rpx;
  font-weight: 800;
  color: #111827;
}
</style>
