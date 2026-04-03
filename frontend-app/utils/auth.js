const TOKEN_KEY = 'nutrimind_token'
const PROFILE_KEY = 'nutrimind_profile'
// 新增：首次登录标记的本地缓存 Key
const FIRST_LOGIN_KEY = 'nutrimind_first_login' 

const AUTH_PAGE_URL = '/pages/auth/index'
const HOME_PAGE_URL = '/pages/index/index'

export function getToken() {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

export function setToken(token) {
  uni.setStorageSync(TOKEN_KEY, token || '')
}

export function getProfile() {
  return uni.getStorageSync(PROFILE_KEY) || null
}

export function setProfile(profile) {
  uni.setStorageSync(PROFILE_KEY, profile || null)
}

// === 新增：首次登录标记管理 ===
export function setFirstLoginFlag(flag) {
  uni.setStorageSync(FIRST_LOGIN_KEY, Boolean(flag))
}

export function getFirstLoginFlag() {
  return uni.getStorageSync(FIRST_LOGIN_KEY) || false
}

export function clearFirstLoginFlag() {
  uni.removeStorageSync(FIRST_LOGIN_KEY)
}
// =============================

export function saveSession(token, profile) {
  setToken(token)
  setProfile(profile)
}

export function clearSession() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(PROFILE_KEY)
  uni.removeStorageSync(FIRST_LOGIN_KEY) // 登出时顺便清理掉标记
}

export function isLoggedIn() {
  return Boolean(getToken())
}

export function openAuthPage() {
  uni.reLaunch({
    url: AUTH_PAGE_URL
  })
}

export function openHomePage() {
  uni.reLaunch({
    url: HOME_PAGE_URL
  })
}

export function ensureLoggedIn() {
  if (isLoggedIn()) {
    return true
  }

  uni.showToast({
    title: '请先登录',
    icon: 'none'
  })

  setTimeout(() => {
    openAuthPage()
  }, 250)

  return false
}

export function formatToday() {
  const now = new Date()
  const year = now.getFullYear()
  const month = `${now.getMonth() + 1}`.padStart(2, '0')
  const day = `${now.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${day}`
}