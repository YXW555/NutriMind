<template>
  <view class="page">
    <view class="auth-shell">
      <view class="auth-card">
        <view class="auth-brand">
          <image class="brand-icon-image" src="/static/logo.png" mode="aspectFit"></image>
          
          <view class="brand-copy">
            <text class="brand-title">知食分子</text>
            <text class="brand-subtitle">你的智能营养顾问</text>
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
              :placeholder="mode === 'login' ? '请输入用户名、邮箱或手机号' : '请输入用户名'"
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
                placeholder="请输入邮箱地址"
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

          <view v-if="mode === 'register'" class="field-block">
            <view class="field-head">
              <text class="field-label">验证码</text>
              <text class="field-tip">如有填手机/邮箱</text>
            </view>
            <view class="verify-wrap">
              <input
                v-model="form.verifyCode"
                class="field-input verify-input"
                maxlength="6"
                type="number"
                placeholder="请输入6位验证码"
                confirm-type="next"
                cursor-spacing="24"
                adjust-position
              />
              <text 
                class="verify-action" 
                :class="{ disabled: countdown > 0 }" 
                @click="sendVerifyCode"
              >
                {{ countdown > 0 ? `${countdown}s 后重新获取` : '获取验证码' }}
              </text>
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
            
            <view v-if="mode === 'login'" class="action-row">
              <text class="forgot-link" @click="handleForgotPwd">忘记密码？</text>
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

        <view class="agreement-wrap">
          <view class="checkbox-box" @click="agreedToTerms = !agreedToTerms">
            <view class="checkbox-icon" :class="{ 'is-checked': agreedToTerms }">
              <text v-if="agreedToTerms" class="check-mark">✓</text>
            </view>
          </view>
          <view class="agreement-text">
            我已阅读并同意
            <text class="link" @click.stop="openAgreement('terms')">《用户协议》</text>
            与
            <text class="link" @click.stop="openAgreement('privacy')">《隐私政策》</text>
          </view>
        </view>

        <button class="submit-button" :disabled="submitDisabled" @click="submitAuth">
          {{ submitLabel }}
        </button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, onUnmounted } from 'vue'
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

// 状态：协议勾选与倒计时
const agreedToTerms = ref(false)
const countdown = ref(0)
let timer = null

const submitLabel = computed(() => {
  if (submitting.value) {
    return mode.value === 'login' ? '登录中...' : '注册中...'
  }
  return mode.value === 'login' ? '登录' : '注册'
})
const submitDisabled = computed(() => submitting.value || (mode.value === 'register' && checkingUsername.value))

function createEmptyForm() {
  return {
    username: '',
    password: '',
    confirmPassword: '',
    email: '',
    phone: '',
    verifyCode: ''
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
  // 重置模式时不重置协议勾选，体验更好
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

// 发送验证码逻辑
function sendVerifyCode() {
  if (countdown.value > 0) return
  
  const email = form.value.email.trim()
  const phone = form.value.phone.trim()
  
  if (!email && !phone) {
    uni.showToast({ title: '请先填写手机号或邮箱', icon: 'none' })
    return
  }

  // 此处可调用后端发送验证码接口：await request.post('/auth/send-code', { email, phone })
  uni.showToast({ title: '验证码已发送，请注意查收', icon: 'none' })
  
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(timer)
    }
  }, 1000)
}

// 忘记密码点击跳转
function handleForgotPwd() {
  uni.showToast({ title: '忘记密码功能开发中', icon: 'none' })
  // uni.navigateTo({ url: '/pages/auth/forgot' })
}

// 打开协议
function openAgreement(type) {
  const title = type === 'terms' ? '用户协议' : '隐私政策'
  uni.showToast({ title: `查看${title}`, icon: 'none' })
  // 实际可跳转webview或富文本页面
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
  const email = form.value.email.trim()
  const phone = form.value.phone.trim()
  const password = form.value.password.trim()
  const confirmPassword = form.value.confirmPassword.trim()
  const verifyCode = form.value.verifyCode.trim()

  if (!USERNAME_PATTERN.test(username)) {
    uni.showToast({
      title: '用户名需为 4-20 位字母、数字或下划线',
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
    email,
    phone,
    verifyCode
  }
}

async function submitAuth() {
  if (submitting.value) {
    return
  }

  // 提交前必须勾选协议拦截
  if (!agreedToTerms.value) {
    uni.showToast({
      title: '请先阅读并同意用户协议与隐私政策',
      icon: 'none'
    })
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

// 组件卸载时清理定时器
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 44rpx 28rpx 56rpx;
  background: linear-gradient(180deg, #fbfefc 0%, #eef7f1 100%);
  display: flex;
  align-items: center; /* 让内容在整个屏幕垂直居中更好看 */
}

.auth-shell {
  width: 100%;
  display: flex;
  align-items: flex-start;
  justify-content: center;
}

.auth-card {
  width: 100%;
  max-width: 760rpx;
  padding: 60rpx 40rpx 40rpx; 
  background: rgba(255, 255, 255, 0.98);
  border-radius: 32rpx; 
  box-shadow: 0 16rpx 48rpx rgba(47, 125, 107, 0.08); 
  border: 1rpx solid rgba(47, 125, 107, 0.05);
}

.field-head,
.password-wrap {
  display: flex;
}

.password-wrap {
  align-items: center;
}

.auth-brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 24rpx;
  margin-bottom: 56rpx;
}

/* 【修改这里】移除了原来的 .brand-icon 相关代码，改用图片样式 */
.brand-icon-image {
  width: 160rpx; /* 控制图片宽度 */
  height: 160rpx; /* 控制图片高度 */
  border-radius: 36rpx; /* 给图片加一点圆角，如果你的图本来就是圆的或者透明的，这个属性不影响 */
  box-shadow: 0 8rpx 24rpx rgba(47, 125, 107, 0.15); /* 加一点阴影让Logo更立体，不需要可以删掉 */
}

.brand-copy {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.brand-title {
  display: block;
  font-size: 52rpx;
  font-weight: 800;
  color: var(--nm-text);
  letter-spacing: 2rpx;
}

.brand-subtitle {
  font-size: 26rpx;
  color: var(--nm-muted);
  letter-spacing: 4rpx;
}

.field-tip,
.feedback-text {
  font-size: 25rpx;
  line-height: 1.7;
  color: var(--nm-muted);
}

.mode-switch {
  display: flex;
  gap: 8rpx;
  padding: 8rpx;
  border-radius: 18rpx;
  background: var(--nm-surface);
  margin-bottom: 12rpx; 
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

/* 验证码输入框及按钮样式 */
.verify-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.verify-input {
  padding-right: 220rpx;
}

.verify-action {
  position: absolute;
  right: 20rpx;
  font-size: 26rpx;
  font-weight: 700;
  color: var(--nm-primary);
  padding: 10rpx;
  z-index: 2;
}

.verify-action.disabled {
  color: var(--nm-muted);
  font-weight: 400;
}

/* 忘记密码行样式 */
.action-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16rpx;
}

.forgot-link {
  font-size: 25rpx;
  color: var(--nm-muted);
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

/* 协议勾选框区域样式 */
.agreement-wrap {
  display: flex;
  align-items: flex-start;
  margin-top: 36rpx;
  padding: 0 8rpx;
}

.checkbox-box {
  padding-top: 4rpx;
  margin-right: 12rpx;
}

.checkbox-icon {
  width: 32rpx;
  height: 32rpx;
  border: 2rpx solid var(--nm-muted);
  border-radius: 6rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.checkbox-icon.is-checked {
  background-color: var(--nm-primary);
  border-color: var(--nm-primary);
}

.check-mark {
  color: #fff;
  font-size: 24rpx;
  font-weight: bold;
}

.agreement-text {
  flex: 1;
  font-size: 24rpx;
  color: var(--nm-muted);
  line-height: 1.5;
}

.agreement-text .link {
  color: var(--nm-primary);
}

.submit-button {
  width: 100%;
  height: 94rpx;
  margin-top: 32rpx;
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