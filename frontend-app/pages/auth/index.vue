<template>
  <view class="page">
    <view class="auth-shell">
      <view class="auth-card">
        <view class="auth-brand">
          <view class="brand-icon">
            <text class="brand-icon-text">知</text>
          </view>
          <view class="brand-copy">
            <text class="brand-title">知食分子</text>
          </view>
        </view>

        <view class="mode-switch">
          <view class="mode-chip" :class="{ active: mode === 'login' }" @click="switchMode('login')">登录</view>
          <view class="mode-chip" :class="{ active: mode === 'register' }" @click="switchMode('register')">注册</view>
        </view>

        <view class="field-list">
          <view class="field-block">
            <view class="field-head">
              <text class="field-label">{{ mode === 'login' ? '账号' : '用户名' }}</text>
              <text v-if="mode === 'register'" class="field-tip">4-20 位</text>
            </view>
            <input
              v-model="form.username"
              class="field-input"
              maxlength="32"
              :placeholder="mode === 'login' ? '请输入用户名、邮箱或手机号' : '例如 yxw_2026'"
              confirm-type="next"
              cursor-spacing="24"
              adjust-position
              @input="handleUsernameInput"
              @blur="handleUsernameBlur"
            />
            <text
              v-if="mode === 'register' && usernameStatus.message"
              class="feedback-text"
              :class="usernameStatus.available ? 'feedback-good' : 'feedback-warn'"
            >
              {{ usernameStatus.message }}
            </text>
          </view>

          <view v-if="mode === 'register'" class="field-block">
            <view class="field-head">
              <text class="field-label">昵称</text>
              <text class="field-tip">选填</text>
            </view>
            <input
              v-model="form.nickname"
              class="field-input"
              maxlength="20"
              placeholder="给自己起个更自然的称呼"
              confirm-type="next"
              cursor-spacing="24"
              adjust-position
            />
          </view>

          <view v-if="mode === 'register'" class="field-grid">
            <view class="field-block compact">
              <view class="field-head">
                <text class="field-label">邮箱</text>
                <text class="field-tip">选填</text>
              </view>
              <input
                v-model="form.email"
                class="field-input"
                maxlength="64"
                placeholder="name@example.com"
                confirm-type="next"
                cursor-spacing="24"
                adjust-position
              />
            </view>

            <view class="field-block compact">
              <view class="field-head">
                <text class="field-label">手机号</text>
                <text class="field-tip">选填</text>
              </view>
              <input
                v-model="form.phone"
                class="field-input"
                maxlength="20"
                placeholder="请输入常用手机号"
                confirm-type="next"
                cursor-spacing="24"
                adjust-position
              />
            </view>
          </view>

          <view class="field-block">
            <view class="field-head">
              <text class="field-label">密码</text>
              <text v-if="mode === 'register'" class="field-tip">6-32 位</text>
            </view>
            <view class="password-wrap">
              <input
                v-model="form.password"
                class="field-input password-input"
                :password="!showPassword"
                maxlength="32"
                :placeholder="mode === 'login' ? '请输入密码' : '设置登录密码'"
                confirm-type="next"
                cursor-spacing="24"
                adjust-position
                @confirm="mode === 'login' ? submitAuth() : undefined"
              />
              <text class="password-toggle" @click="showPassword = !showPassword">
                {{ showPassword ? '隐藏' : '显示' }}
              </text>
            </view>
          </view>

          <view v-if="mode === 'register'" class="field-block">
            <view class="field-head">
              <text class="field-label">确认密码</text>
            </view>
            <view class="password-wrap">
              <input
                v-model="form.confirmPassword"
                class="field-input password-input"
                :password="!showConfirmPassword"
                maxlength="32"
                placeholder="请再次输入密码"
                confirm-type="done"
                cursor-spacing="24"
                adjust-position
                @confirm="submitAuth"
              />
              <text class="password-toggle" @click="showConfirmPassword = !showConfirmPassword">
                {{ showConfirmPassword ? '隐藏' : '显示' }}
              </text>
            </view>
          </view>
        </view>

        <view v-if="mode === 'register'" class="strength-card">
          <view class="strength-head">
            <text class="strength-title">密码强度</text>
            <text class="strength-label" :class="`level-${passwordStrength.score}`">{{ passwordStrength.label }}</text>
          </view>
          <view class="strength-track">
            <view
              v-for="segment in 3"
              :key="segment"
              class="strength-segment"
              :class="{ active: passwordStrength.score >= segment }"
            ></view>
          </view>
          <text class="strength-desc">{{ passwordStrength.desc }}</text>
        </view>

        <button class="submit-button" :disabled="submitDisabled" @click="submitAuth">
          {{ submitLabel }}
        </button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request from '@/utils/request.js'
import { clearSession, isLoggedIn, openHomePage, saveSession, setToken } from '@/utils/auth.js'

const USERNAME_PATTERN = /^[A-Za-z0-9_]{4,20}$/
const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/
const PHONE_PATTERN = /^\+?[0-9-]{7,20}$/

const mode = ref('login')
const submitting = ref(false)
const checkingUsername = ref(false)
const showPassword = ref(false)
const showConfirmPassword = ref(false)
const form = ref(createEmptyForm())
const usernameStatus = ref(createUsernameStatus())
const submitLabel = computed(() => {
  if (submitting.value) {
    return mode.value === 'login' ? '登录中...' : '注册中...'
  }
  return mode.value === 'login' ? '登录' : '注册'
})
const submitDisabled = computed(() => submitting.value || (mode.value === 'register' && checkingUsername.value))
const passwordStrength = computed(() => evaluatePasswordStrength(form.value.password))

function createEmptyForm() {
  return {
    username: '',
    password: '',
    confirmPassword: '',
    nickname: '',
    email: '',
    phone: ''
  }
}

function createUsernameStatus() {
  return {
    checked: false,
    available: false,
    message: ''
  }
}

function resetForm() {
  form.value = createEmptyForm()
  usernameStatus.value = createUsernameStatus()
  showPassword.value = false
  showConfirmPassword.value = false
}

function switchMode(nextMode) {
  if (mode.value === nextMode) {
    return
  }
  const preservedIdentifier = form.value.username.trim()
  mode.value = nextMode
  resetForm()
  form.value.username = preservedIdentifier
}

function handleUsernameInput() {
  if (mode.value !== 'register') {
    return
  }
  usernameStatus.value = createUsernameStatus()
}

async function handleUsernameBlur() {
  if (mode.value !== 'register') {
    return
  }
  await ensureUsernameAvailable()
}

async function ensureUsernameAvailable() {
  const username = form.value.username.trim()
  if (!username) {
    usernameStatus.value = createUsernameStatus()
    return false
  }
  if (!USERNAME_PATTERN.test(username)) {
    usernameStatus.value = {
      checked: false,
      available: false,
      message: '用户名需为 4-20 位字母、数字或下划线'
    }
    return false
  }
  if (usernameStatus.value.checked && usernameStatus.value.available) {
    return true
  }

  checkingUsername.value = true
  try {
    const response = await request.get('/auth/check-username', { username })
    usernameStatus.value = {
      checked: true,
      available: Boolean(response?.available),
      message: response?.available ? '用户名可用，可以继续注册。' : '这个用户名已被占用，换一个会更稳。'
    }
    return Boolean(response?.available)
  } catch (error) {
    usernameStatus.value = {
      checked: false,
      available: false,
      message: '用户名检查失败，请稍后再试。'
    }
    console.log('check username failed', error)
    return false
  } finally {
    checkingUsername.value = false
  }
}

function evaluatePasswordStrength(password) {
  const value = String(password || '')
  if (!value) {
    return {
      score: 0,
      label: '待设置',
      desc: '建议至少 6 位，并尽量混合字母、数字或符号。'
    }
  }

  let score = 0
  if (value.length >= 6) {
    score += 1
  }
  if (/[A-Za-z]/.test(value) && /\d/.test(value)) {
    score += 1
  }
  if ((/[A-Z]/.test(value) && /[a-z]/.test(value)) || /[^A-Za-z0-9_]/.test(value)) {
    score += 1
  }

  if (score <= 1) {
    return {
      score: 1,
      label: '偏弱',
      desc: '可以继续补数字、大小写字母或符号，让密码更稳一些。'
    }
  }
  if (score === 2) {
    return {
      score: 2,
      label: '中等',
      desc: '已经够用，再加一点复杂度会更好。'
    }
  }
  return {
    score: 3,
    label: '较强',
    desc: '当前密码组合比较完整，安全性会更好。'
  }
}

function validateLoginForm() {
  const identifier = form.value.username.trim()
  const password = form.value.password.trim()
  if (!identifier || !password) {
    uni.showToast({
      title: '请输入账号和密码',
      icon: 'none'
    })
    return null
  }
  return {
    username: identifier,
    password
  }
}

function validateRegisterForm() {
  const username = form.value.username.trim()
  const nickname = form.value.nickname.trim()
  const email = form.value.email.trim()
  const phone = form.value.phone.trim()
  const password = form.value.password.trim()
  const confirmPassword = form.value.confirmPassword.trim()

  if (!USERNAME_PATTERN.test(username)) {
    uni.showToast({
      title: '用户名需为 4-20 位字母、数字或下划线',
      icon: 'none'
    })
    return null
  }
  if (nickname && nickname.length > 20) {
    uni.showToast({
      title: '昵称最多 20 个字符',
      icon: 'none'
    })
    return null
  }
  if (email && !EMAIL_PATTERN.test(email)) {
    uni.showToast({
      title: '邮箱格式不正确',
      icon: 'none'
    })
    return null
  }
  if (phone && !PHONE_PATTERN.test(phone)) {
    uni.showToast({
      title: '手机号格式不正确',
      icon: 'none'
    })
    return null
  }
  if (password.length < 6 || password.length > 32) {
    uni.showToast({
      title: '密码长度需在 6-32 位之间',
      icon: 'none'
    })
    return null
  }
  if (password !== confirmPassword) {
    uni.showToast({
      title: '两次输入的密码不一致',
      icon: 'none'
    })
    return null
  }

  return {
    username,
    password,
    confirmPassword,
    nickname,
    email,
    phone
  }
}

async function submitAuth() {
  if (submitting.value) {
    return
  }

  const payload = mode.value === 'login'
    ? validateLoginForm()
    : validateRegisterForm()
  if (!payload) {
    return
  }

  if (mode.value === 'register') {
    const available = await ensureUsernameAvailable()
    if (!available) {
      uni.showToast({
        title: usernameStatus.value.message || '当前用户名不可用',
        icon: 'none'
      })
      return
    }
  }

  submitting.value = true
  try {
    const authResponse = mode.value === 'login'
      ? await request.post('/auth/login', payload)
      : await request.post('/auth/register', payload)

    setToken(authResponse.token)
    const profile = await request.get('/auth/me')
    saveSession(authResponse.token, profile)
    resetForm()
    uni.showToast({
      title: mode.value === 'login' ? '登录成功' : '注册成功',
      icon: 'success'
    })

    setTimeout(() => {
      openHomePage()
    }, 250)
  } catch (error) {
    clearSession()
    console.log('submit auth failed', error)
  } finally {
    submitting.value = false
  }
}

onShow(() => {
  if (isLoggedIn()) {
    openHomePage()
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 44rpx 28rpx 56rpx;
  background: linear-gradient(180deg, #fbfefc 0%, #eef7f1 100%);
}

.auth-shell {
  min-height: calc(100vh - 100rpx);
  display: flex;
  align-items: flex-start;
  justify-content: center;
}

.auth-card {
  border-radius: 24rpx;
  box-shadow: var(--nm-shadow);
}

.auth-brand,
.field-head,
.password-wrap,
.strength-head {
  display: flex;
}

.auth-brand,
.password-wrap,
.strength-head {
  align-items: center;
}

.brand-icon {
  width: 84rpx;
  height: 84rpx;
  border-radius: 20rpx;
  background: var(--nm-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.brand-icon-text {
  font-size: 42rpx;
  font-weight: 800;
  color: #ffffff;
}

.brand-copy {
  flex: 1;
  min-width: 0;
}

.auth-brand {
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.brand-title {
  display: block;
  font-size: 44rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.field-tip,
.feedback-text,
.strength-desc {
  font-size: 25rpx;
  line-height: 1.7;
  color: var(--nm-muted);
}

.auth-card {
  width: 100%;
  max-width: 760rpx;
  padding: 32rpx 28rpx 28rpx;
  background: rgba(255, 255, 255, 0.96);
  border: 1rpx solid var(--nm-line);
}

.mode-switch {
  display: flex;
  gap: 8rpx;
  padding: 8rpx;
  border-radius: 18rpx;
  background: var(--nm-surface);
}

.mode-chip {
  flex: 1;
  padding: 16rpx 0;
  border-radius: 14rpx;
  text-align: center;
  font-size: 28rpx;
  font-weight: 700;
  color: var(--nm-muted);
  transition: all 0.2s ease;
}

.mode-chip.active {
  background: var(--nm-primary);
  color: #ffffff;
  box-shadow: none;
}

.field-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
  margin-top: 26rpx;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18rpx;
}

.field-block {
  display: flex;
  flex-direction: column;
}

.field-head {
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  margin-bottom: 12rpx;
}

.field-label {
  font-size: 27rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.field-tip {
  font-size: 22rpx;
  line-height: 1.4;
  text-align: right;
}

.field-input {
  width: 100%;
  min-height: 94rpx;
  padding: 24rpx 22rpx;
  border-radius: 18rpx;
  background: #f1f8f3;
  border: 1rpx solid var(--nm-line);
  box-sizing: border-box;
  font-size: 28rpx;
  color: var(--nm-text);
}

.field-input:focus {
  border-color: rgba(47, 125, 107, 0.35);
  box-shadow: 0 0 0 4rpx rgba(47, 125, 107, 0.08);
}

.password-wrap {
  position: relative;
}

.password-input {
  padding-right: 112rpx;
}

.password-toggle {
  position: absolute;
  top: 50%;
  right: 24rpx;
  transform: translateY(-50%);
  font-size: 24rpx;
  font-weight: 700;
  color: var(--nm-primary);
}

.feedback-text {
  margin-top: 10rpx;
}

.feedback-good {
  color: var(--nm-primary);
}

.feedback-warn {
  color: var(--nm-danger);
}

.strength-card {
  margin-top: 24rpx;
  padding: 24rpx;
  border-radius: 18rpx;
  background: #f4faf5;
  border: 1rpx solid var(--nm-line);
}

.strength-head {
  justify-content: space-between;
  gap: 14rpx;
}

.strength-title {
  font-size: 28rpx;
  font-weight: 800;
  color: var(--nm-text);
}

.strength-label {
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  font-size: 23rpx;
  font-weight: 800;
}

.strength-label.level-0,
.strength-label.level-1 {
  background: rgba(180, 83, 9, 0.12);
  color: var(--nm-danger);
}

.strength-label.level-2 {
  background: rgba(183, 121, 31, 0.12);
  color: var(--nm-orange);
}

.strength-label.level-3 {
  background: rgba(47, 125, 107, 0.12);
  color: var(--nm-primary);
}

.strength-track {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10rpx;
  margin-top: 18rpx;
}

.strength-segment {
  height: 12rpx;
  border-radius: 999rpx;
  background: #dbe3ea;
}

.strength-segment.active {
  background: var(--nm-primary);
}

.strength-desc {
  display: block;
  margin-top: 14rpx;
}

.submit-button {
  width: 100%;
  height: 94rpx;
  margin-top: 26rpx;
  border-radius: 18rpx;
  background: var(--nm-primary);
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 800;
}

.submit-button::after {
  border: none;
}

.submit-button[disabled] {
  opacity: 0.72;
}

@media screen and (max-width: 720px) {
  .field-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media screen and (min-width: 960px) {
  .page {
    padding: 72rpx 40rpx 80rpx;
  }

  .auth-shell {
    max-width: 560px;
    margin: 0 auto;
  }
}
</style>
