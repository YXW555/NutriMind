const API_BASE_KEY = 'nutrimind_admin_api_base'
const TOKEN_KEY = 'nutrimind_admin_token'
const DEFAULT_API_BASE = 'http://39.105.13.79:8080/api'

export function getApiBaseUrl() {
  const cached = localStorage.getItem(API_BASE_KEY) || ''
  const normalized = String(cached).trim().replace(/\/+$/, '')
  if (!normalized) {
    return DEFAULT_API_BASE
  }
  if (/localhost|127\.0\.0\.1/i.test(normalized)) {
    localStorage.setItem(API_BASE_KEY, DEFAULT_API_BASE)
    return DEFAULT_API_BASE
  }
  return normalized.endsWith('/api') ? normalized : `${normalized}/api`
}

export function setApiBaseUrl(value) {
  const normalized = String(value || '').trim().replace(/\/+$/, '')
  const finalValue = normalized.endsWith('/api') ? normalized : `${normalized}/api`
  localStorage.setItem(API_BASE_KEY, finalValue)
  return finalValue
}

export function getAdminToken() {
  return localStorage.getItem(TOKEN_KEY) || localStorage.getItem('nutrimind_token') || ''
}

export function setAdminToken(token) {
  localStorage.setItem(TOKEN_KEY, String(token || '').trim())
}

async function request(path, options = {}) {
  const url = `${getApiBaseUrl()}${path}`
  const token = getAdminToken()
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {})
    },
    ...options
  })

  const text = await response.text()
  const body = text ? JSON.parse(text) : null

  if (!response.ok) {
    throw new Error(body?.msg || `Request failed: ${response.status}`)
  }

  if (body && Object.prototype.hasOwnProperty.call(body, 'code')) {
    if (body.code !== 200) {
      throw new Error(body.msg || 'Business request failed')
    }
    return body.data
  }

  return body
}

export const adminApi = {
  get(path, params) {
    const search = params ? `?${new URLSearchParams(params).toString()}` : ''
    return request(`${path}${search}`)
  },
  post(path, payload) {
    return request(path, {
      method: 'POST',
      body: JSON.stringify(payload || {})
    })
  },
  put(path, payload) {
    return request(path, {
      method: 'PUT',
      body: JSON.stringify(payload || {})
    })
  },
  delete(path) {
    return request(path, {
      method: 'DELETE'
    })
  }
}
