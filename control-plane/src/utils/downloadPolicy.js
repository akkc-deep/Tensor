const RANGE_MODES = new Set([
  'TRADE_DATE_RANGE',
  'ANN_DATE_RANGE',
  'MONTH_RANGE',
  'NATIVE_RANGE',
])

const MODES = new Set([...RANGE_MODES, 'ORIGINAL_PARAMS'])

const MODE_LABELS = {
  TRADE_DATE_RANGE: ['开始交易日期', '结束交易日期'],
  ANN_DATE_RANGE: ['开始公告日期', '结束公告日期'],
  MONTH_RANGE: ['开始日期', '结束日期'],
}

const NATIVE_LABELS = {
  CALENDAR_DATE: ['开始日历日期', '结束日历日期'],
  IPO_DATE: ['开始申购日期', '结束申购日期'],
  ANN_DATE: ['开始公告日期', '结束公告日期'],
}

export function isRangeMode(mode) {
  return RANGE_MODES.has(mode)
}

export function dateLabels(policy) {
  if (!policy) return null
  const labels = policy.mode === 'NATIVE_RANGE' ? NATIVE_LABELS : MODE_LABELS
  const key = policy.mode === 'NATIVE_RANGE' ? policy.dateSemantic : policy.mode
  return Object.hasOwn(labels, key) ? labels[key] : null
}

export function downloadPolicyError(api) {
  const policy = api?.downloadPolicy
  if (!policy || !MODES.has(policy.mode)) return '下载配置不完整，请重新加载页面。'
  const parameters = Array.isArray(api.parameters) ? api.parameters : []
  const endpoints = parameters.filter(({ name }) => name === 'start_date' || name === 'end_date')
  if (policy.mode === 'ORIGINAL_PARAMS') {
    return endpoints.length ? '下载配置不完整，请重新加载页面。' : null
  }
  if (!dateLabels(policy)) return '下载配置不完整，请重新加载页面。'
  if (!Number.isInteger(policy.limits?.maxRangeDays) || policy.limits.maxRangeDays <= 0) {
    return '下载配置不完整，请重新加载页面。'
  }
  if (endpoints.length !== 2) return '下载配置不完整，请重新加载页面。'
  const [start, end] = ['start_date', 'end_date'].map((name) => parameters.find((item) => item.name === name))
  if (
    start?.type !== 'DATE_RANGE_MEMBER' || end?.type !== 'DATE_RANGE_MEMBER' ||
    start.required !== true || end.required !== true ||
    start.relatedParameter !== 'end_date' || end.relatedParameter !== 'start_date'
  ) return '下载配置不完整，请重新加载页面。'
  if (parameters.some(({ name }) => ['trade_date', 'ann_date', 'month'].includes(name))) {
    return '下载配置不完整，请重新加载页面。'
  }
  return null
}
