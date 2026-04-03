const API_BASE_KEY = 'nutrimind_admin_api_base'
const DEFAULT_API_BASE = 'http://localhost:8080/api'

export function getApiBaseUrl() {
  return localStorage.getItem(API_BASE_KEY) || DEFAULT_API_BASE
}

export function setApiBaseUrl(value) {
  const normalized = String(value || '').trim().replace(/\/+$/, '')
  const finalValue = normalized.endsWith('/api') ? normalized : `${normalized}/api`
  localStorage.setItem(API_BASE_KEY, finalValue)
  return finalValue
}

async function request(path, options = {}) {
  const url = `${getApiBaseUrl()}${path}`
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
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
