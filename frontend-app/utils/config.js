const API_BASE_KEY = 'nutrimind_api_base'
const DEFAULT_API_BASE = 'http://localhost:8080/api'
const DEFAULT_NATIVE_API_BASE = 'http://10.100.233.124:8080/api'

function isH5Runtime() {
  return typeof window !== 'undefined' && typeof window.location !== 'undefined'
}

function isIpv4Host(hostname) {
  return /^\d{1,3}(\.\d{1,3}){3}$/.test(hostname)
}

function isPrivateNetworkHost(hostname) {
  if (!isIpv4Host(hostname)) {
    return false
  }

  if (hostname.startsWith('10.')) {
    return true
  }

  if (hostname.startsWith('192.168.')) {
    return true
  }

  const match = hostname.match(/^172\.(\d{1,2})\./)
  if (!match) {
    return false
  }

  const segment = Number(match[1])
  return segment >= 16 && segment <= 31
}

function getH5DefaultApiBaseUrl() {
  if (!isH5Runtime()) {
    return DEFAULT_API_BASE
  }

  const { hostname, origin } = window.location
  if (!hostname) {
    return DEFAULT_API_BASE
  }

  if (hostname === 'localhost' || hostname === '127.0.0.1' || isPrivateNetworkHost(hostname)) {
    return `http://${hostname}:8080/api`
  }

  return `${String(origin || '').replace(/\/+$/, '')}/api`
}

function getRuntimeDefaultApiBaseUrl() {
  return isH5Runtime() ? getH5DefaultApiBaseUrl() : DEFAULT_NATIVE_API_BASE
}

function getApiBaseStorageKey() {
  if (!isH5Runtime()) {
    return API_BASE_KEY
  }

  const hostname = window.location.hostname || 'localhost'
  return `${API_BASE_KEY}:h5:${hostname}`
}

export function normalizeApiBaseUrl(value) {
  let next = String(value || '').trim()
  if (!next) {
    return getRuntimeDefaultApiBaseUrl()
  }

  if (!/^https?:\/\//i.test(next)) {
    next = `http://${next}`
  }

  next = next.replace(/\/+$/, '')
  if (!/\/api$/i.test(next)) {
    next = `${next}/api`
  }

  return next
}

export function getApiBaseUrl() {
  if (isH5Runtime()) {
    return getRuntimeDefaultApiBaseUrl()
  }

  const storedValue = uni.getStorageSync(getApiBaseStorageKey())
  return normalizeApiBaseUrl(storedValue || DEFAULT_API_BASE)
}

export function setApiBaseUrl(value) {
  const normalized = normalizeApiBaseUrl(value)
  if (isH5Runtime()) {
    return normalized
  }
  uni.setStorageSync(getApiBaseStorageKey(), normalized)
  return normalized
}

export function resetApiBaseUrl() {
  if (isH5Runtime()) {
    return getRuntimeDefaultApiBaseUrl()
  }
  uni.removeStorageSync(getApiBaseStorageKey())
  return getRuntimeDefaultApiBaseUrl()
}

export function getDefaultApiBaseUrl() {
  return getRuntimeDefaultApiBaseUrl()
}

export function getApiBaseHint() {
  return '\u771f\u673a\u8c03\u8bd5\u65f6\uff0c\u8bf7\u4f7f\u7528\u4f60\u7535\u8111\u7684\u5c40\u57df\u7f51 IP\uff0c\u4f8b\u5982 10.100.233.124:8080\u3002\u5982\u679c\u66f4\u6362\u7f51\u7edc\uff0c\u9700\u540c\u6b65\u66f4\u65b0'
}
