export function hasValue(value) {
  if (value === null || value === undefined) return false
  return typeof value !== 'string' || value.trim().length > 0
}

export function matchesPattern(value, pattern) {
  if (typeof value !== 'string' || typeof pattern !== 'string' || !pattern) {
    return false
  }

  try {
    return new RegExp(pattern).test(value)
  } catch {
    return false
  }
}

export function isRangeOrdered(start, end) {
  if (!hasValue(start) || !hasValue(end)) return true
  return typeof start === 'string' && typeof end === 'string' && start <= end
}
