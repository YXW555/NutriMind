export function formatNumber(value, digits = 0) {
  const numeric = Number(value || 0)
  if (Number.isNaN(numeric)) {
    return '0'
  }
  if (digits === 0) {
    return `${Math.round(numeric)}`
  }
  return numeric.toFixed(digits).replace(/\.0+$/, '').replace(/(\.\d*[1-9])0+$/, '$1')
}

export function formatPercent(value) {
  const numeric = Number(value || 0)
  if (Number.isNaN(numeric)) {
    return 0
  }
  return Math.max(0, Math.min(100, Math.round(numeric)))
}

export function formatTime(value) {
  if (!value) {
    return '--:--'
  }
  const date = new Date(normalizeDateInput(value))
  if (Number.isNaN(date.getTime())) {
    return '--:--'
  }
  const hour = `${date.getHours()}`.padStart(2, '0')
  const minute = `${date.getMinutes()}`.padStart(2, '0')
  return `${hour}:${minute}`
}

export function formatRelativeTime(value) {
  if (!value) {
    return '刚刚'
  }
  const date = new Date(normalizeDateInput(value))
  if (Number.isNaN(date.getTime())) {
    return '刚刚'
  }
  const diff = Date.now() - date.getTime()
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff < hour) {
    return `${Math.max(1, Math.floor(diff / minute))} 分钟前`
  }
  if (diff < day) {
    return `${Math.max(1, Math.floor(diff / hour))} 小时前`
  }
  return `${Math.max(1, Math.floor(diff / day))} 天前`
}

export function mealTypeLabel(type) {
  const map = {
    BREAKFAST: '早餐',
    LUNCH: '午餐',
    DINNER: '晚餐',
    SNACK: '加餐'
  }
  return map[type] || '加餐'
}

export function normalizeDateInput(value) {
  return typeof value === 'string' ? value.replace(' ', 'T') : value
}
