export function getStatusBarHeight() {
  try {
    const systemInfo = typeof uni !== 'undefined' && typeof uni.getSystemInfoSync === 'function'
      ? uni.getSystemInfoSync()
      : null
    const rawHeight = Number(systemInfo && systemInfo.statusBarHeight ? systemInfo.statusBarHeight : 0)
    return Number.isFinite(rawHeight) && rawHeight > 0 ? rawHeight : 0
  } catch (error) {
    return 0
  }
}

export function createSafeAreaTopStyle(extraPx = 0) {
  const top = Math.max(0, getStatusBarHeight() + extraPx)
  return top > 0
    ? { paddingTop: `${top}px` }
    : {}
}
