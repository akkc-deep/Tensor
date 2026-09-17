const DAY = 86_400_000

function validDate(value) {
  if (typeof value !== 'string' || !/^(?:[1-9]\d{3})-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])$/.test(value)) return false
  const [year, month, day] = value.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day))
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
}

export function parseIntegritySymbols(text, pluginId) {
  const symbols = String(text ?? '').split(/[\s,，]+/).filter(Boolean)
    .map((value) => pluginId === 'tushare_pro' ? value.toUpperCase() : value)
  return [...new Set(symbols)]
}

export function validateIntegritySelection(selection = {}, capability, today) {
  const errors = {}
  const symbols = Array.isArray(selection.symbols) ? selection.symbols : []
  const apiNames = Array.isArray(selection.apiNames) ? selection.apiNames : []
  const apis = Array.isArray(capability?.apis) ? capability.apis : []
  const limits = capability?.limits ?? {}

  if (!selection.pluginId) errors.pluginId = '请选择数据源'
  if (!symbols.length) errors.symbols = '请至少添加一个股票代码'
  else if (symbols.some((value) => typeof value !== 'string' || !value.trim())) errors.symbols = '股票代码不能为空'
  else if (selection.pluginId === 'tushare_pro' && symbols.some((value) => !/^\d{6}\.(?:SH|SZ|BJ)$/.test(value))) {
    errors.symbols = '股票代码应为六位数字加 SH、SZ 或 BJ'
  } else if (Number.isInteger(limits.maxSymbols) && symbols.length > limits.maxSymbols) {
    errors.symbols = `股票数量不能超过 ${limits.maxSymbols}`
  }

  if (!selection.startDate) errors.startDate = '请选择开始日期'
  else if (!validDate(selection.startDate)) errors.startDate = '开始日期格式无效'
  if (!selection.endDate) errors.endDate = '请选择结束日期'
  else if (!validDate(selection.endDate)) errors.endDate = '结束日期格式无效'

  let rangeDays = 0
  if (validDate(selection.startDate) && validDate(selection.endDate)) {
    rangeDays = (Date.parse(`${selection.endDate}T00:00:00Z`) - Date.parse(`${selection.startDate}T00:00:00Z`)) / DAY + 1
    if (rangeDays < 1) errors.range = '结束日期不能早于开始日期'
    else if (Number.isInteger(limits.maxRangeDays) && rangeDays > limits.maxRangeDays) {
      errors.range = `日期范围不能超过 ${limits.maxRangeDays} 天`
    }
    if (selection.pluginId === 'tushare_pro' && validDate(today) && selection.endDate > today) {
      errors.endDate = '结束日期不能晚于上海当日'
    }
  }

  if (!apiNames.length) errors.apiNames = '请至少选择一个接口'
  const byName = new Map(apis.map((api) => [api.apiName, api]))
  const missing = apiNames.filter((name) => !byName.has(name))
  if (missing.length) errors.apiNames = `所选接口已失效：${missing.join('、')}`
  const plannedUnits = apiNames.reduce((total, name) => {
    const api = byName.get(name)
    if (!api) return total
    return total + (api.descriptor?.scopeKind === 'NON_STOCK' ? 1 : symbols.length)
  }, 0)
  if (Number.isInteger(limits.maxUnits) && plannedUnits > limits.maxUnits) {
    errors.units = `计划检查单元数不能超过 ${limits.maxUnits}`
  }
  return { valid: Object.keys(errors).length === 0, errors, plannedUnits, rangeDays }
}
