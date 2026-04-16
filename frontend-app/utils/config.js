const API_BASE_KEY = 'nutrimind_api_base'
const DEFAULT_API_BASE = 'http://10.100.233.124:8080/api'
const DEFAULT_NATIVE_PROXY_API_BASE = 'http://127.0.0.1:8080/api'
const DEFAULT_NATIVE_LAN_API_BASE = 'http://10.100.233.124:8080/api'
const DEFAULT_NATIVE_API_BASE = DEFAULT_NATIVE_PROXY_API_BASE

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

function getNativeCandidateApiBases() {
  return [DEFAULT_NATIVE_PROXY_API_BASE, DEFAULT_NATIVE_LAN_API_BASE]
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
  const normalized = normalizeApiBaseUrl(storedValue || getRuntimeDefaultApiBaseUrl())

  if (normalized === DEFAULT_NATIVE_LAN_API_BASE) {
    return DEFAULT_NATIVE_PROXY_API_BASE
  }

  return normalized
}

export function getApiBaseCandidates() {
  if (isH5Runtime()) {
    return [getRuntimeDefaultApiBaseUrl()]
  }

  const storedValue = uni.getStorageSync(getApiBaseStorageKey())
  const normalizedStoredValue = storedValue ? normalizeApiBaseUrl(storedValue) : ''
  const candidates = normalizedStoredValue
    ? [normalizedStoredValue, ...getNativeCandidateApiBases()]
    : getNativeCandidateApiBases()

  return [...new Set(candidates.map((item) => normalizeApiBaseUrl(item)))]
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

export function resolveApiAssetUrl(value) {
  const raw = String(value || '').trim()
  if (!raw) {
    return ''
  }

  if (/^https?:\/\//i.test(raw)) {
    return raw
  }

  const apiBase = getApiBaseUrl().replace(/\/+$/, '')
  const serviceBase = apiBase.replace(/\/api$/i, '')

  if (raw.startsWith('/')) {
    return `${serviceBase}${raw}`
  }

  return `${serviceBase}/${raw.replace(/^\/+/, '')}`
}

export function getApiBaseHint() {
  return '真机调试会优先尝试 USB 反向代理地址 127.0.0.1:8080，失败时再回退到局域网地址 10.100.233.124:8080。'
}
