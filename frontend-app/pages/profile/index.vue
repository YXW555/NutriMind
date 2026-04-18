<template>
  <view class="page-container">
    
    <view v-show="activePage === 'main'" class="main-dashboard">
      <view class="custom-nav" :style="mainNavSafeStyle">
        <view class="nav-left">
          <text class="user-id">ID: {{ overview.userId || '--' }}</text>
        </view>
      </view>

      <view class="user-header" @click="openPage('account')">
        <view class="avatar-wrap">
          <view class="avatar-circle">
            <image
              v-if="overview.avatarUrl"
              class="avatar-image"
              :src="overview.avatarUrl"
              mode="aspectFill"
            ></image>
            <text v-else class="avatar-text">{{ avatarText }}</text>
          </view>
        </view>
        <view class="user-info">
          <text class="user-name">{{ overview.nickname || overview.username || '--' }}</text>
          <view class="user-badges">
            <text class="badge">勋章 {{ rewardSummary.badgeCount }}</text>
            <text class="badge">积分 {{ rewardSummary.totalPoints }}</text>
            <text v-if="overview.role === 'ADMIN'" class="role-badge">管理员</text>
          </view>
        </view>
        <view class="header-right">
          <text class="profile-link">个人资料 ></text>
        </view>
      </view>

      <view class="vip-card" @click="openPage('goal')">
        <view class="vip-top">
          <text class="vip-title">💡 {{ goalTypeLabel }}方案</text>
          <view class="vip-btn">
            <text class="vip-btn-text">立即调整 ></text>
            <text class="vip-btn-sub">让计划更精准</text>
          </view>
        </view>
        <view class="vip-metrics">
          <view class="vip-metric-item">
            <text class="vm-title">🎯 目标热量</text>
            <text class="vm-desc">{{ goalCaloriesText }}</text>
          </view>
          <view class="vip-metric-item">
            <text class="vm-title">⚖️ 最新体重</text>
            <text class="vm-desc">{{ latestWeightText }}</text>
          </view>
        </view>
        <view class="vip-bottom">
          <text class="vip-tag">🥗 知食分子专属</text>
          <text class="vip-hint">坚持记录，遇见更好的自己</text>
        </view>
      </view>

      <view class="list-menu">
        <view class="list-item" @click="openPage('goal')">
          <view class="list-left">
            <text class="list-icon">💡</text>
            <text class="list-text">体重管理方案</text>
          </view>
          <view class="list-right">
            <text class="arrow">></text>
          </view>
        </view>
        <view class="list-item" @click="openPage('health')">
          <view class="list-left">
            <text class="list-icon">📝</text>
            <text class="list-text">健康档案</text>
          </view>
          <view class="list-right">
            <text class="arrow">></text>
          </view>
        </view>
        <view class="list-item" @click="openPage('weight')">
          <view class="list-left">
            <text class="list-icon">⚖️</text>
            <text class="list-text">数据统计（体重记录）</text>
          </view>
          <view class="list-right">
            <text class="arrow">></text>
          </view>
        </view>
        <view class="list-item" @click="goMyPosts">
          <view class="list-left">
            <text class="list-icon">📝</text>
            <text class="list-text">我的帖子管理</text>
          </view>
          <view class="list-right">
            <text class="arrow">></text>
          </view>
        </view>
        <view class="list-item" @click="openPage('reminders')">
          <view class="list-left">
            <text class="list-icon">🔔</text>
            <text class="list-text">智能提醒设置</text>
          </view>
          <view class="list-right">
            <text class="arrow">></text>
          </view>
        </view>
      </view>


      <view style="height: 180rpx;"></view> </view>


    <view v-if="activePage !== 'main'" class="sub-page">
      <view class="sub-nav" :style="subNavSafeStyle">
        <view class="back-btn" @click="closePage">
          <text class="back-arrow">←</text>
        </view>
        <text class="sub-nav-title">{{ activePage === 'reminders' ? '智能提醒' : subPageTitle }}</text>
        <view class="placeholder-box"></view>
      </view>

      <scroll-view scroll-y class="sub-scroll">
        <view class="panel sub-panel">
          
          <block v-if="activePage === 'account'">
            <view class="section-head">
              <view class="section-head-left">
                <text class="section-title">基本资料</text>
                <text class="section-desc">您的基础账号信息</text>
              </view>
              <text class="section-status" :class="{ editing: isEditingAccount }">
                {{ isEditingAccount ? '编辑中' : '已保存' }}
              </text>
            </view>

            <view class="avatar-upload-card">
              <view class="avatar-upload-preview">
                <view class="avatar-circle large">
                  <image v-if="overview.avatarUrl" class="avatar-image" :src="overview.avatarUrl" mode="aspectFill"></image>
                  <text v-else class="avatar-text">{{ avatarText }}</text>
                </view>
                <view class="avatar-upload-copy">
                  <text class="avatar-upload-title">头像</text>
                  <text class="avatar-upload-desc">支持 JPG、PNG、WEBP，大小不超过 3MB</text>
                </view>
              </view>
              <button class="secondary-button avatar-upload-button" :loading="savingAvatar" :disabled="savingAvatar" @click="chooseAvatar">
                {{ savingAvatar ? '上传中...' : '上传头像' }}
              </button>
            </view>

            <view v-if="!isEditingAccount" class="info-list">
              <view class="info-row"><text class="info-label">用户名</text><text class="info-value">{{ overview.username || '--' }}</text></view>
              <view class="info-row"><text class="info-label">用户编号</text><text class="info-value">{{ overview.userId || '--' }}</text></view>
              <view class="info-row"><text class="info-label">昵称</text><text class="info-value">{{ overview.nickname || '未填写' }}</text></view>
              <view class="info-row"><text class="info-label">邮箱</text><text class="info-value">{{ overview.email || '未填写' }}</text></view>
              <view class="info-row"><text class="info-label">手机号</text><text class="info-value">{{ overview.phone || '未填写' }}</text></view>
            </view>

            <view v-else>
              <view class="info-list" style="margin-bottom: 16rpx;">
                <view class="info-row"><text class="info-label">用户名</text><text class="info-value" style="color: #9ca3af;">{{ overview.username || '--' }}（不可修改）</text></view>
              </view>
              <input v-model="accountForm.nickname" class="field-input" placeholder="昵称，例如：健身小达人" confirm-type="next" adjust-position />
              <input v-model="accountForm.email" class="field-input" type="text" placeholder="邮箱地址" confirm-type="next" adjust-position />
              <input v-model="accountForm.phone" class="field-input" type="number" placeholder="手机号" confirm-type="done" adjust-position />
            </view>

            <button :class="isEditingAccount ? 'primary-button' : 'secondary-button'" :loading="savingAccount" :disabled="savingAccount" @click="handleAccountAction">
              {{ isEditingAccount ? (savingAccount ? '保存中...' : '保存资料') : '编辑基本资料' }}
            </button>
            
            <button v-if="!isEditingAccount" class="danger-button" style="margin-top: 30rpx;" @click="logout">
              退出登录
            </button>
          </block>

          <block v-if="activePage === 'health'">
            <view class="section-head">
              <view class="section-head-left">
                <text class="section-title">健康档案</text>
                <text class="section-desc">身高、活动水平和饮食偏好会影响计划建议。</text>
              </view>
              <text class="section-status" :class="{ editing: isEditingHealthProfile }">{{ healthProfileStatusText }}</text>
            </view>

            <view class="form-grid">
              <picker class="picker-wrap" :class="{ 'picker-wrap-disabled': !isEditingHealthProfile }" :range="genderOptions" range-key="label" :value="genderIndex" @change="handleGenderChange">
                <view class="picker-field" :class="{ readonly: !isEditingHealthProfile }">{{ genderLabel }}</view>
              </picker>
              <view class="picker-field date-trigger-field" :class="{ readonly: !isEditingHealthProfile }" @click="openDateSheet('birthDate')">
                <text>{{ healthForm.birthDate || (isEditingHealthProfile ? '选择出生日期' : '未填写生日') }}</text>
                <text v-if="isEditingHealthProfile" class="date-trigger-arrow">></text>
              </view>
              <input v-model="healthForm.heightCm" :class="['field-input', { readonly: !isEditingHealthProfile }]" type="digit" :placeholder="isEditingHealthProfile ? '身高(cm)' : '未填写身高'" confirm-type="next" cursor-spacing="24" :disabled="!isEditingHealthProfile" adjust-position />
              <picker class="picker-wrap" :class="{ 'picker-wrap-disabled': !isEditingHealthProfile }" :range="activityOptions" range-key="label" :value="activityIndex" @change="handleActivityChange">
                <view class="picker-field" :class="{ readonly: !isEditingHealthProfile }">{{ activityLabel }}</view>
              </picker>
            </view>

            <input v-model="healthForm.dietaryPreference" :class="['field-input', { readonly: !isEditingHealthProfile }]" :placeholder="isEditingHealthProfile ? '饮食偏好，例如低糖、素食' : '未填写饮食偏好'" confirm-type="next" cursor-spacing="24" :disabled="!isEditingHealthProfile" adjust-position />
            <input v-model="healthForm.allergies" :class="['field-input', { readonly: !isEditingHealthProfile }]" :placeholder="isEditingHealthProfile ? '过敏原，例如花生、海鲜' : '未填写过敏原'" confirm-type="next" cursor-spacing="24" :disabled="!isEditingHealthProfile" adjust-position />
            <textarea v-model="healthForm.medicalNotes" :class="['textarea-field', { readonly: !isEditingHealthProfile }]" maxlength="300" auto-height :disabled="!isEditingHealthProfile" :placeholder="isEditingHealthProfile ? '补充说明，例如胃部不适、健身周期、作息情况' : '未填写补充说明'"></textarea>

            <button :class="isEditingHealthProfile ? 'primary-button' : 'secondary-button'" :loading="savingHealthProfile" :disabled="savingHealthProfile" @click="handleHealthProfileAction">
              {{ healthProfileActionLabel }}
            </button>
          </block>

          <block v-if="activePage === 'goal'">
            <view class="section-head">
              <view class="section-head-left">
                <text class="section-title">健康目标</text>
                <text class="section-desc">把目标热量和宏量营养素定下来，后面更容易对照执行。</text>
              </view>
              <text class="section-status" :class="{ editing: isEditingHealthGoal }">{{ healthGoalStatusText }}</text>
            </view>

            <picker class="picker-wrap" :class="{ 'picker-wrap-disabled': !isEditingHealthGoal }" :range="goalOptions" range-key="label" :value="goalTypeIndex" @change="handleGoalTypeChange">
              <view class="picker-field" :class="{ readonly: !isEditingHealthGoal }">{{ goalTypePickerLabel }}</view>
            </picker>

            <view class="form-grid">
              <input v-model="goalForm.targetCalories" :class="['field-input', { readonly: !isEditingHealthGoal }]" type="digit" :placeholder="isEditingHealthGoal ? '目标热量(kcal)' : '未填写热量'" confirm-type="next" cursor-spacing="24" :disabled="!isEditingHealthGoal" adjust-position />
              <input v-model="goalForm.targetWeightKg" :class="['field-input', { readonly: !isEditingHealthGoal }]" type="digit" :placeholder="isEditingHealthGoal ? '目标体重(kg)' : '未填写目标体重'" confirm-type="next" cursor-spacing="24" :disabled="!isEditingHealthGoal" adjust-position />
              <input v-model="goalForm.targetProtein" :class="['field-input', { readonly: !isEditingHealthGoal }]" type="digit" :placeholder="isEditingHealthGoal ? '目标蛋白质(g)' : '未填写蛋白质'" confirm-type="next" cursor-spacing="24" :disabled="!isEditingHealthGoal" adjust-position />
              <input v-model="goalForm.targetCarbohydrate" :class="['field-input', { readonly: !isEditingHealthGoal }]" type="digit" :placeholder="isEditingHealthGoal ? '目标碳水(g)' : '未填写碳水'" confirm-type="next" cursor-spacing="24" :disabled="!isEditingHealthGoal" adjust-position />
              <input v-model="goalForm.targetFat" :class="['field-input', { readonly: !isEditingHealthGoal }]" type="digit" :placeholder="isEditingHealthGoal ? '目标脂肪(g)' : '未填写脂肪'" confirm-type="next" cursor-spacing="24" :disabled="!isEditingHealthGoal" adjust-position />
              <input v-model="goalForm.weeklyChangeKg" :class="['field-input', { readonly: !isEditingHealthGoal }]" type="digit" :placeholder="isEditingHealthGoal ? '每周变化(kg)' : '未填写每周变化'" confirm-type="next" cursor-spacing="24" :disabled="!isEditingHealthGoal" adjust-position />
            </view>

            <view class="form-grid">
              <view class="picker-field date-trigger-field" :class="{ readonly: !isEditingHealthGoal }" @click="openDateSheet('goalStartDate')">
                <text>{{ goalForm.startDate || (isEditingHealthGoal ? '开始日期' : '未填写开始日期') }}</text>
                <text v-if="isEditingHealthGoal" class="date-trigger-arrow">></text>
              </view>
              <view class="picker-field date-trigger-field" :class="{ readonly: !isEditingHealthGoal }" @click="openDateSheet('goalEndDate')">
                <text>{{ goalForm.endDate || (isEditingHealthGoal ? '结束日期' : '未填写结束日期') }}</text>
                <text v-if="isEditingHealthGoal" class="date-trigger-arrow">></text>
              </view>
            </view>

            <textarea v-model="goalForm.note" :class="['textarea-field', { readonly: !isEditingHealthGoal }]" maxlength="300" auto-height :disabled="!isEditingHealthGoal" :placeholder="isEditingHealthGoal ? '补充说明，例如想在 8 周减 4kg' : '未填写目标说明'"></textarea>

            <button :class="isEditingHealthGoal ? 'primary-button' : 'secondary-button'" :loading="savingHealthGoal" :disabled="savingHealthGoal" @click="handleHealthGoalAction">
              {{ healthGoalActionLabel }}
            </button>
          </block>

          <block v-if="activePage === 'weight'">
            <view class="section-head">
              <view class="section-head-left">
                <text class="section-title">体重记录</text>
                <text class="section-desc">建议固定时间记录，趋势会更稳定。</text>
              </view>
            </view>

            <view class="weight-form-row">
              <view class="picker-field weight-date-field date-trigger-field" @click="openDateSheet('weightRecordDate')">
                <text>{{ weightForm.recordDate }}</text>
                <text class="date-trigger-arrow">></text>
              </view>
              <input v-model="weightForm.weightKg" class="field-input weight-value-field" type="digit" placeholder="体重(kg)" confirm-type="done" cursor-spacing="24" adjust-position />
            </view>

            <input v-model="weightForm.note" class="field-input" placeholder="备注，例如晨起空腹、训练后" confirm-type="done" cursor-spacing="24" adjust-position />

            <view class="button-row">
              <button class="secondary-button" @click="resetWeightForm">重置</button>
              <button class="primary-button" :loading="savingWeightLog" :disabled="savingWeightLog" @click="saveWeightLog">
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
                <text class="weight-log-meta">{{ item.recordDate }}{{ item.note ? ` 路 ${item.note}` : '' }}</text>
              </view>
              <button class="danger-button small" @click="deleteWeightLog(item.id)">删除</button>
            </view>
          </block>

          <block v-if="activePage === 'reminders'">
            <view class="section-head">
              <view class="section-head-left">
                <text class="section-title">智能提醒</text>
                <text class="section-desc">系统会结合记录、目标和 GraphRAG 分析结果生成主动提醒。</text>
              </view>
            </view>

            <view class="reminder-hero-card">
              <text class="reminder-hero-title">当前策略</text>
              <text class="reminder-hero-desc">{{ reminderSummary }}</text>
            </view>

            <view class="reminder-setting-card">
              <view class="reminder-setting-row">
                <view class="reminder-setting-copy">
                  <text class="reminder-setting-title">开启智能提醒</text>
                  <text class="reminder-setting-desc">首页显示今日提醒卡，并驱动后续提醒动作。</text>
                </view>
                <switch color="#38D07D" :checked="reminderSettings.enabled" @change="event => handleReminderToggle('enabled', event)" />
              </view>

              <view class="reminder-setting-row">
                <view class="reminder-setting-copy">
                  <text class="reminder-setting-title">关键提醒弹窗</text>
                  <text class="reminder-setting-desc">高优先级提醒每天弹出一次，适合比赛演示和真实使用。</text>
                </view>
                <switch color="#38D07D" :checked="reminderSettings.popupEnabled" @change="event => handleReminderToggle('popupEnabled', event)" :disabled="!reminderSettings.enabled" />
              </view>

              <view class="reminder-setting-row">
                <view class="reminder-setting-copy">
                  <text class="reminder-setting-title">缺餐补记提醒</text>
                  <text class="reminder-setting-desc">提醒早餐、午餐和晚餐是否缺失，并引导补记。</text>
                </view>
                <switch color="#38D07D" :checked="reminderSettings.mealRecordReminder" @change="event => handleReminderToggle('mealRecordReminder', event)" :disabled="!reminderSettings.enabled" />
              </view>

              <view class="reminder-setting-row">
                <view class="reminder-setting-copy">
                  <text class="reminder-setting-title">营养偏差提醒</text>
                  <text class="reminder-setting-desc">自动关注热量、蛋白质等偏差，并给出修正方向。</text>
                </view>
                <switch color="#38D07D" :checked="reminderSettings.nutritionAnalysisReminder" @change="event => handleReminderToggle('nutritionAnalysisReminder', event)" :disabled="!reminderSettings.enabled" />
              </view>

              <view class="reminder-setting-row">
                <view class="reminder-setting-copy">
                  <text class="reminder-setting-title">执行建议提醒</text>
                  <text class="reminder-setting-desc">提供继续记录、生成方案和执行入口，推动用户真正行动。</text>
                </view>
                <switch color="#38D07D" :checked="reminderSettings.executionSuggestionReminder" @change="event => handleReminderToggle('executionSuggestionReminder', event)" :disabled="!reminderSettings.enabled" />
              </view>
            </view>
          </block>

        </view>
      </scroll-view>
    </view>

    <app-date-sheet
      :visible="dateSheetVisible"
      :title="dateSheetTitle"
      :value="dateSheetValue"
      :min-year="dateSheetMinYear"
      :max-year="dateSheetMaxYear"
      @close="closeDateSheet"
      @confirm="handleDateSheetConfirm"
    />

    <app-tab-bar v-show="activePage === 'main'" current="profile" />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { clearSession, formatToday, getToken, isLoggedIn, openAuthPage, saveSession } from '@/utils/auth.js'
import { formatNumber } from '@/utils/format.js'
import { createSafeAreaTopStyle } from '@/utils/layout.js'
import { resolveApiAssetUrl } from '@/utils/config.js'
import { getReminderSettings, saveReminderSettings } from '@/utils/reminders.js'

// 页面状态控制
const activePage = ref('main') // 'main', 'account', 'health', 'goal', 'weight', 'reminders'
const mainNavSafeStyle = createSafeAreaTopStyle(16)
const subNavSafeStyle = createSafeAreaTopStyle(20)
const dateSheetVisible = ref(false)
const dateSheetTitle = ref('选择日期')
const dateSheetValue = ref(formatToday())
const dateSheetTarget = ref('')
const dateSheetMinYear = ref(1970)
const dateSheetMaxYear = ref(new Date().getFullYear() + 5)
const subPageTitle = computed(() => {
  const map = { account: '个人信息', health: '健康档案', goal: '体重管理方案', weight: '数据统计' }
  return map[activePage.value] || '详情'
})

function openPage(page) {
  activePage.value = page
}
function closePage() {
  activePage.value = 'main'
}
function goMyPosts() {
  uni.navigateTo({
    url: '/pages/community/manage'
  })
}

function handleReminderToggle(key, event) {
  const checked = Boolean(event?.detail?.value)
  reminderSettings.value = saveReminderSettings({
    ...reminderSettings.value,
    [key]: checked
  })

  if (key === 'enabled' && !checked) {
    reminderSettings.value = saveReminderSettings({
      ...reminderSettings.value,
      popupEnabled: false
    })
  }

  uni.showToast({
    title: '提醒设置已更新',
    icon: 'success'
  })
}

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
const rewardSummary = ref(createEmptyRewardSummary())
const weightLogs = ref([])

// 表单数据
const accountForm = ref({ nickname: '', email: '', phone: '' })
const healthForm = ref(createEmptyHealthForm())
const goalForm = ref(createEmptyGoalForm())
const weightForm = ref(createEmptyWeightForm())

// 编辑状态控制
const isEditingAccount = ref(false)
const isEditingHealthProfile = ref(true)
const isEditingHealthGoal = ref(true)

// 保存 loading 状态
const savingAccount = ref(false)
const savingAvatar = ref(false)
const savingHealthProfile = ref(false)
const savingHealthGoal = ref(false)
const savingWeightLog = ref(false)
const reminderSettings = ref(getReminderSettings())

const avatarText = computed(() => {
  const source = overview.value.nickname || overview.value.username || '我'
  return String(source).slice(0, 1)
})

const latestWeightText = computed(() => {
  if (!overview.value.latestWeightKg) return '待记录'
  return `${formatNumber(overview.value.latestWeightKg, 1)} kg`
})

const goalTypeLabel = computed(() => {
  const current = goalOptions.find(item => item.value === goalForm.value.goalType)
  return current ? current.label : '未设置'
})

const goalCaloriesText = computed(() => {
  if (!goalForm.value.targetCalories) return '--'
  return `${formatNumber(goalForm.value.targetCalories)} kcal`
})

const reminderSummary = computed(() => {
  if (!reminderSettings.value.enabled) {
    return '当前已关闭全部主动提醒，首页不会再展示智能提醒卡。'
  }

  const parts = []
  if (reminderSettings.value.mealRecordReminder) parts.push('缺餐补记')
  if (reminderSettings.value.nutritionAnalysisReminder) parts.push('营养偏差')
  if (reminderSettings.value.executionSuggestionReminder) parts.push('执行建议')
  const popupText = reminderSettings.value.popupEnabled ? '高优先级提醒会弹窗提示。' : '当前只在页面内展示，不做弹窗打扰。'

  return `系统当前会关注${parts.join('、') || '基础状态'}。${popupText}`
})

const hasSavedHealthProfile = computed(() => hasHealthProfileContent(overview.value?.healthProfile))
const hasSavedHealthGoal = computed(() => hasHealthGoalContent(overview.value?.healthGoal))

const healthProfileActionLabel = computed(() => {
  if (savingHealthProfile.value) return '保存中...'
  return isEditingHealthProfile.value ? '保存健康档案' : '编辑健康档案'
})

const healthGoalActionLabel = computed(() => {
  if (savingHealthGoal.value) return '保存中...'
  return isEditingHealthGoal.value ? '保存目标' : '编辑目标'
})

const healthProfileStatusText = computed(() => {
  if (isEditingHealthProfile.value) return hasSavedHealthProfile.value ? '编辑中' : '待填写'
  return '已保存'
})

const healthGoalStatusText = computed(() => {
  if (isEditingHealthGoal.value) return hasSavedHealthGoal.value ? '编辑中' : '待填写'
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

const genderLabel = computed(() => genderOptions[genderIndex.value]?.label || (isEditingHealthProfile.value ? '选择性别' : '未设置性别'))
const activityLabel = computed(() => activityOptions[activityIndex.value]?.label || (isEditingHealthProfile.value ? '选择活动水平' : '未设置活动水平'))
const goalTypePickerLabel = computed(() => goalOptions[goalTypeIndex.value]?.label || (isEditingHealthGoal.value ? '选择目标' : '未设置目标'))

function createEmptyHealthForm() {
  return { gender: '', birthDate: '', heightCm: '', activityLevel: '', dietaryPreference: '', allergies: '', medicalNotes: '' }
}

function createEmptyGoalForm() {
  return { goalType: 'BALANCE', targetCalories: '', targetProtein: '', targetFat: '', targetCarbohydrate: '', targetWeightKg: '', weeklyChangeKg: '', startDate: '', endDate: '', note: '' }
}

function createEmptyWeightForm() {
  return { weightKg: '', recordDate: formatToday(), note: '' }
}

function createEmptyRewardSummary() {
  return {
    totalPoints: 0,
    badgeCount: 0,
    currentStreak: 0,
    badges: []
  }
}

function normalizeOptionalText(value) {
  const normalized = String(value ?? '').trim()
  return normalized || null
}

function normalizeOptionalNumber(value) {
  const normalized = String(value ?? '').trim()
  if (!normalized) return null
  const parsed = Number(normalized)
  return Number.isFinite(parsed) ? parsed : Number.NaN
}

function showInlineToast(title, icon = 'none') {
  uni.showToast({ title, icon })
}

function showSavingIndicator(title = '保存中') {
  if (typeof uni.hideKeyboard === 'function') uni.hideKeyboard()
  uni.showLoading({ title, mask: true })
}

function hasHealthProfileContent(profile) {
  const current = profile || {}
  return Boolean(current.gender || current.birthDate || current.heightCm || current.activityLevel || current.dietaryPreference || current.allergies || current.medicalNotes)
}

function hasHealthGoalContent(goal) {
  const current = goal || {}
  return Boolean(current.id || current.targetCalories || current.targetProtein || current.targetFat || current.targetCarbohydrate || current.targetWeightKg || current.weeklyChangeKg || current.startDate || current.endDate || current.note || (current.goalType && current.goalType !== 'BALANCE'))
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
  isEditingAccount.value = false 
}

async function loadOverview() {
  if (!isLoggedIn()) {
    openAuthPage()
    return
  }

  try {
    const [data, reward] = await Promise.all([
      request.get('/profile/overview'),
      request.get('/meals/rewards/summary')
    ])
    overview.value = {
      ...(data || {}),
      avatarUrl: resolveApiAssetUrl(data?.avatarUrl)
    }
    rewardSummary.value = {
      ...createEmptyRewardSummary(),
      ...(reward || {})
    }
    saveSession(getToken(), {
      userId: data?.userId,
      username: data?.username,
      nickname: data?.nickname,
      avatarUrl: resolveApiAssetUrl(data?.avatarUrl),
      email: data?.email,
      phone: data?.phone,
      role: data?.role,
      totalPoints: reward?.totalPoints || 0,
      badgeCount: reward?.badgeCount || 0,
      currentStreak: reward?.currentStreak || 0
    })
    assignForms(overview.value)
  } catch (error) {
    console.log('load profile overview failed', error)
  }
}

// ---- 鍩烘湰璧勬枡鎿嶄綔 ----
async function handleAccountAction() {
  if (!isEditingAccount.value) {
    accountForm.value = {
      nickname: overview.value.nickname || '',
      email: overview.value.email || '',
      phone: overview.value.phone || ''
    }
    isEditingAccount.value = true
  } else {
    await saveAccountInfo()
  }
}

async function saveAccountInfo() {
  if (savingAccount.value) return
  savingAccount.value = true
  showSavingIndicator()

  try {
    await request.put('/profile/info', {
      nickname: normalizeOptionalText(accountForm.value.nickname),
      email: normalizeOptionalText(accountForm.value.email),
      phone: normalizeOptionalText(accountForm.value.phone)
    })
    isEditingAccount.value = false
    await loadOverview()
    showInlineToast('基本资料已保存', 'success')
  } catch (error) {
    console.log('save account info failed', error)
  } finally {
    savingAccount.value = false
    uni.hideLoading()
  }
}

function chooseAvatar() {
  if (savingAvatar.value) return
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (result) => {
      const filePath = result?.tempFilePaths?.[0]
      if (!filePath) {
        showInlineToast('请选择图片')
        return
      }
      savingAvatar.value = true
      showSavingIndicator('上传中')
      try {
        await request.upload('/profile/avatar', {
          filePath,
          name: 'file'
        })
        await loadOverview()
        showInlineToast('头像已更新', 'success')
      } catch (error) {
        console.log('upload avatar failed', error)
      } finally {
        savingAvatar.value = false
        uni.hideLoading()
      }
    }
  })
}

function handleGenderChange(event) {
  if (!isEditingHealthProfile.value) return
  const selected = genderOptions[Number(event.detail.value || 0)]
  healthForm.value.gender = selected ? selected.value : ''
}

function handleActivityChange(event) {
  if (!isEditingHealthProfile.value) return
  const selected = activityOptions[Number(event.detail.value || 0)]
  healthForm.value.activityLevel = selected ? selected.value : ''
}

function handleGoalTypeChange(event) {
  if (!isEditingHealthGoal.value) return
  const selected = goalOptions[Number(event.detail.value || 0)]
  goalForm.value.goalType = selected ? selected.value : 'BALANCE'
}

function handleBirthDateChange(event) {
  if (!isEditingHealthProfile.value) return
  healthForm.value.birthDate = event.detail.value
}

function openDateSheet(target) {
  const currentYear = new Date().getFullYear()
  const configMap = {
    birthDate: {
      enabled: isEditingHealthProfile.value,
      title: '选择出生日期',
      value: healthForm.value.birthDate,
      minYear: 1960,
      maxYear: currentYear
    },
    goalStartDate: {
      enabled: isEditingHealthGoal.value,
      title: '选择开始日期',
      value: goalForm.value.startDate,
      minYear: currentYear - 5,
      maxYear: currentYear + 10
    },
    goalEndDate: {
      enabled: isEditingHealthGoal.value,
      title: '选择结束日期',
      value: goalForm.value.endDate || goalForm.value.startDate,
      minYear: currentYear - 5,
      maxYear: currentYear + 10
    },
    weightRecordDate: {
      enabled: true,
      title: '选择记录日期',
      value: weightForm.value.recordDate,
      minYear: currentYear - 5,
      maxYear: currentYear + 1
    }
  }

  const config = configMap[target]
  if (!config || !config.enabled) {
    return
  }

  dateSheetTarget.value = target
  dateSheetTitle.value = config.title
  dateSheetValue.value = config.value || formatToday()
  dateSheetMinYear.value = config.minYear
  dateSheetMaxYear.value = config.maxYear
  dateSheetVisible.value = true
}

function closeDateSheet() {
  dateSheetVisible.value = false
}

function handleDateSheetConfirm(value) {
  if (dateSheetTarget.value === 'birthDate') {
    healthForm.value.birthDate = value
    return
  }
  if (dateSheetTarget.value === 'goalStartDate') {
    goalForm.value.startDate = value
    if (goalForm.value.endDate && goalForm.value.endDate < value) {
      goalForm.value.endDate = value
    }
    return
  }
  if (dateSheetTarget.value === 'goalEndDate') {
    goalForm.value.endDate = value
    return
  }
  if (dateSheetTarget.value === 'weightRecordDate') {
    weightForm.value.recordDate = value
  }
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
  if (savingHealthProfile.value) return

  const heightCm = normalizeOptionalNumber(healthForm.value.heightCm)
  if (Number.isNaN(heightCm)) {
    showInlineToast('请输入正确身高')
    return
  }
  if (heightCm !== null && (heightCm < 50 || heightCm > 260)) {
    showInlineToast('身高需在 50-260cm')
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
  if (savingHealthGoal.value) return

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
      showInlineToast(`${field.label}涓嶈兘灏忎簬0`)
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
  if (savingWeightLog.value) return

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
      if (!result.confirm) return
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

function logout() {
  clearSession()
  uni.showToast({ title: '已退出登录', icon: 'success' })
  setTimeout(() => { openAuthPage() }, 250)
}

onLoad((query) => {
  if (query?.tab) {
    activePage.value = query.tab
  }
})

onShow(() => {
  if (!isLoggedIn()) {
    openAuthPage()
    return
  }
  reminderSettings.value = getReminderSettings()
  loadOverview()
})
</script>

<style scoped>
/* =========================================================
   鍏ㄥ眬椤甸潰鏍峰紡
========================================================= */
.page-container {
  min-height: 100vh;
  background-color: #F7F8FA;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  position: relative;
}

/* =========================================================
   1. 涓讳华琛ㄧ洏鏍峰紡 (Main Dashboard)
========================================================= */
.main-dashboard {
  padding-bottom: 20rpx;
}

/* 鑷畾涔夐《閮ㄥ鑸?*/
.custom-nav {
  padding: calc(env(safe-area-inset-top) + 24rpx) 40rpx 20rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: transparent;
}
.user-id { font-size: 24rpx; color: #999; }

/* 鐢ㄦ埛澶撮儴淇℃伅 */
.user-header {
  padding: 20rpx 40rpx 40rpx;
  display: flex;
  align-items: center;
  gap: 30rpx;
}
.avatar-circle {
  width: 130rpx; height: 130rpx; border-radius: 50%;
  background: #E8F3EE; display: flex; align-items: center; justify-content: center;
  border: 4rpx solid #fff; box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.05);
  overflow: hidden;
}
.avatar-circle.large { width: 144rpx; height: 144rpx; }
.avatar-image { width: 100%; height: 100%; display: block; }
.avatar-text { font-size: 50rpx; font-weight: 900; color: #38D07D; }
.user-info { flex: 1; }
.user-name { font-size: 40rpx; font-weight: 900; color: #262626; display: block; margin-bottom: 12rpx;}
.user-badges { display: flex; gap: 12rpx; align-items: center;}
.badge { background: #F0F0F0; color: #666; font-size: 22rpx; padding: 4rpx 16rpx; border-radius: 100rpx; }
.role-badge { background: #FFF4E5; color: #D97706; font-size: 22rpx; padding: 4rpx 16rpx; border-radius: 100rpx; }
.header-right { padding-left: 20rpx; }
.profile-link { font-size: 24rpx; color: #999; }

/* VIP / 鐩爣鏁版嵁鍗＄墖 */
.vip-card {
  margin: 0 30rpx;
  background: linear-gradient(135deg, #2E3238 0%, #1A1D21 100%);
  border-radius: 32rpx;
  padding: 30rpx;
  color: #fff;
  box-shadow: 0 10rpx 30rpx rgba(0,0,0,0.1);
  position: relative;
  overflow: hidden;
}
.vip-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 30rpx; }
.vip-title { font-size: 32rpx; font-weight: bold; color: #E8F3EE; }
.vip-btn { background: linear-gradient(90deg, #E8F3EE, #C4E4D3); padding: 10rpx 20rpx; border-radius: 16rpx; text-align: center; }
.vip-btn-text { display: block; font-size: 24rpx; color: #1A1D21; font-weight: bold; }
.vip-btn-sub { display: block; font-size: 18rpx; color: #4A5D53; margin-top: 4rpx; }
.vip-metrics { display: flex; gap: 40rpx; margin-bottom: 24rpx; }
.vip-metric-item { flex: 1; background: rgba(255,255,255,0.06); padding: 20rpx; border-radius: 20rpx; }
.vm-title { display: block; font-size: 24rpx; color: #999; margin-bottom: 8rpx; }
.vm-desc { display: block; font-size: 32rpx; font-weight: bold; color: #38D07D; }
.vip-bottom { display: flex; align-items: center; gap: 16rpx; }
.vip-tag { font-size: 22rpx; background: linear-gradient(90deg, #E8F3EE, #fff); color: #2E3238; padding: 4rpx 16rpx; border-radius: 100rpx; font-weight: bold; }
.vip-hint { font-size: 22rpx; color: #666; }

/* 鍒楄〃鑿滃崟 (宸茬Щ闄や笂鏂瑰叓瀹牸闂磋窛锛岃皟鏁?margin) */
.list-menu {
  background: #fff; margin: 40rpx 30rpx 30rpx; border-radius: 32rpx;
  padding: 10rpx 30rpx; box-shadow: 0 4rpx 20rpx rgba(0,0,0,0.02);
}
.list-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 34rpx 0; border-bottom: 1rpx solid #F0F0F0;
}
.reminder-entry-card {
  margin: 0 30rpx 20rpx;
  padding: 30rpx;
  border-radius: 28rpx;
  background: #F5FBF8;
  border: 1rpx solid #D4F2E1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}
.reminder-entry-copy { flex: 1; min-width: 0; }
.reminder-entry-title {
  display: block;
  font-size: 30rpx;
  font-weight: 800;
  color: #1F2937;
}
.reminder-entry-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: #6B7280;
}
.border-none { border-bottom: none; }
.list-left { display: flex; align-items: center; gap: 20rpx; }
.list-icon { font-size: 40rpx; }
.list-text { font-size: 30rpx; color: #262626; font-weight: bold; }
.text-danger { color: #FF4D4F; }
.arrow { color: #CCC; font-size: 30rpx; font-weight: bold; }

/* =========================================================
   2. 瀛愰〉闈㈡牱寮?(Sub Page for Forms)
========================================================= */
.sub-page {
  position: fixed;
  inset: 0;
  background: #F7F8FA;
  z-index: 999;
  display: flex;
  flex-direction: column;
  animation: slideIn 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}
@keyframes slideIn {
  from { transform: translateX(100%); }
  to { transform: translateX(0); }
}

.sub-nav {
  padding: calc(env(safe-area-inset-top) + 24rpx) 30rpx 30rpx;
  background: #fff;
  display: flex; justify-content: space-between; align-items: center;
  box-shadow: 0 2rpx 10rpx rgba(0,0,0,0.02);
  flex-shrink: 0;
}
.back-btn { width: 60rpx; height: 60rpx; display: flex; align-items: center; }
.back-arrow { font-size: 60rpx; color: #262626; margin-top: -10rpx; font-weight: 300; }
.sub-nav-title { font-size: 34rpx; font-weight: bold; color: #262626; }
.placeholder-box { width: 60rpx; } /* 鍗犱綅绗︿繚鎸佹爣棰樺眳涓?*/

.sub-scroll {
  flex: 1;
  padding: 30rpx;
  box-sizing: border-box;
}
.sub-panel {
  background: #fff; border-radius: 32rpx; padding: 40rpx 30rpx;
  box-shadow: 0 4rpx 20rpx rgba(0,0,0,0.02); margin-bottom: 60rpx;
}

/* 琛ㄥ崟鍐呴儴鏍峰紡 */
.section-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 30rpx;}
.section-head-left { flex: 1; min-width: 0; padding-right: 16rpx; }
.section-title { display: block; font-size: 36rpx; font-weight: 800; color: #262626; }
.section-desc { display: block; margin-top: 10rpx; font-size: 24rpx; color: #999; line-height: 1.4; }

/* 鍏抽敭淇锛氬姞鍏?flex-shrink 鍜?white-space 淇濊瘉鏍囩涓嶈鎸ゅ帇鎹㈣ */
.section-status { 
  flex-shrink: 0;
  white-space: nowrap;
  padding: 8rpx 16rpx; 
  border-radius: 100rpx; 
  background: #E8F3EE; 
  font-size: 22rpx; 
  font-weight: bold; 
  color: #38D07D; 
}
.section-status.editing { background: #EAF2FB; color: #2563EB; }

.avatar-upload-card {
  margin-top: 20rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  background: #F9FAFB;
  border: 1rpx solid #F0F0F0;
}
.avatar-upload-preview {
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.avatar-upload-copy {
  flex: 1;
  min-width: 0;
}
.avatar-upload-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #262626;
}
.avatar-upload-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  line-height: 1.5;
  color: #8B8B8B;
}
.avatar-upload-button {
  margin-top: 24rpx;
}
.info-list { margin-top: 20rpx; }
.info-row { padding: 24rpx 0; border-bottom: 1rpx solid #F0F0F0; display: flex; justify-content: space-between; }
.info-row:last-child { border-bottom: none; }
.info-label { font-size: 28rpx; color: #666; }
.info-value { font-size: 28rpx; color: #262626; font-weight: bold;}

.field-input, .picker-field, .textarea-field {
  box-sizing: border-box; margin-top: 20rpx; padding: 28rpx;
  border-radius: 20rpx; background: #F9FAFB; font-size: 28rpx; color: #262626; border: 1rpx solid #F0F0F0;
}
.field-input, .picker-field { min-height: 100rpx; display: flex; align-items: center; }
.field-input.readonly, .picker-field.readonly, .textarea-field.readonly { background: #F5F6F8; color: #999; border-color: transparent; }
.date-trigger-field {
  justify-content: space-between;
  gap: 16rpx;
}
.date-trigger-arrow {
  flex-shrink: 0;
  font-size: 28rpx;
  color: #9CA3AF;
}
.form-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 20rpx; margin-top: 20rpx; }
.form-grid .field-input, .form-grid .picker-field { margin-top: 0; }
.textarea-field { min-height: 180rpx; line-height: 1.6; }
.weight-form-row { display: flex; gap: 20rpx; margin-top: 20rpx;}
.weight-date-field { flex: 1.2; margin-top: 0;}
.weight-value-field { flex: 1; margin-top: 0;}

.primary-button, .secondary-button, .danger-button {
  margin-top: 40rpx; height: 96rpx; border-radius: 100rpx; font-size: 32rpx; font-weight: bold; display: flex; align-items: center; justify-content: center;
}
.primary-button { background: #38D07D; color: #ffffff; }
.primary-button::after, .secondary-button::after, .danger-button::after { border: none; }
.secondary-button { background: #E8F3EE; color: #38D07D; }
.danger-button { background: #FEF2F2; color: #FF4D4F; }
.danger-button.small { margin-top: 0; width: 140rpx; height: 72rpx; font-size: 26rpx; border-radius: 16rpx;}
.button-row { display: flex; gap: 20rpx; }
.button-row button { flex: 1; }

.empty-inline-card { margin-top: 30rpx; padding: 40rpx 20rpx; border-radius: 24rpx; background: #F9FAFB; text-align: center;}
.empty-inline-title { display: block; font-size: 28rpx; font-weight: bold; color: #262626; }
.empty-inline-desc { font-size: 24rpx; color: #999; margin-top: 10rpx;}
.weight-log-card { margin-top: 20rpx; padding: 28rpx; border-radius: 24rpx; background: #F9FAFB; display: flex; justify-content: space-between; align-items: center;}
.weight-log-main { flex: 1; }
.weight-log-value { display: block; font-size: 34rpx; font-weight: bold; color: #262626; margin-bottom: 8rpx;}
.reminder-hero-card {
  padding: 30rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #1F2937 0%, #111827 100%);
  margin-bottom: 24rpx;
}
.reminder-hero-title {
  display: block;
  font-size: 28rpx;
  font-weight: 800;
  color: #D1FAE5;
}
.reminder-hero-desc {
  display: block;
  margin-top: 14rpx;
  font-size: 26rpx;
  line-height: 1.7;
  color: #F9FAFB;
}
.reminder-setting-card {
  border-radius: 24rpx;
  background: #FFFFFF;
  border: 1rpx solid #E5E7EB;
  overflow: hidden;
}
.reminder-setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
  padding: 28rpx 24rpx;
  border-bottom: 1rpx solid #F3F4F6;
}
.reminder-setting-row:last-child { border-bottom: none; }
.reminder-setting-copy {
  flex: 1;
  min-width: 0;
}
.reminder-setting-title {
  display: block;
  font-size: 28rpx;
  font-weight: 800;
  color: #1F2937;
}
.reminder-setting-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: #6B7280;
}
</style>
