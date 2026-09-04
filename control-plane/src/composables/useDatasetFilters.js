import { reactive, ref, watch } from 'vue'

import { toApiDate } from '../utils/date.js'
import { hasValue, isRangeOrdered } from '../utils/validation.js'

function clear(object) {
  for (const key of Object.keys(object)) delete object[key]
}

function fields(filters) {
  return filters.flatMap((filter) => {
    if (filter.field === 'ts_code' && filter.operator === 'EQ' && filter.controlType === 'TEXT') {
      return [{ name: 'tsCode', type: 'code' }]
    }
    if (filter.field === 'trade_date' && filter.operator === 'BETWEEN' && filter.controlType === 'DATE_RANGE') {
      return [
        { name: 'tradeDateFrom', type: 'date', pair: 'tradeDateTo' },
        { name: 'tradeDateTo', type: 'date' },
      ]
    }
    if (filter.field === 'ann_date' && filter.operator === 'BETWEEN' && filter.controlType === 'DATE_RANGE') {
      return [
        { name: 'annDateFrom', type: 'date', pair: 'annDateTo' },
        { name: 'annDateTo', type: 'date' },
      ]
    }
    return []
  })
}

export function useDatasetFilters(filters) {
  const values = reactive({})
  const errors = reactive(Object.create(null))
  const firstError = ref(null)
  let snapshot = null

  function reset() {
    clear(values)
    clear(errors)
    firstError.value = null
    snapshot = null
    for (const field of fields(filters.value)) values[field.name] = ''
  }

  function setValue(name, value) {
    values[name] = value
    delete errors[name]
    firstError.value = fields(filters.value).find(({ name: field }) => errors[field])?.name ?? null
    snapshot = null
  }

  function validateValues() {
    clear(errors)
    firstError.value = null
    snapshot = null
    const normalized = {}
    const declared = fields(filters.value)

    for (const field of declared) {
      const value = values[field.name]
      if (!hasValue(value)) continue

      if (field.type === 'code') {
        const normalizedValue = typeof value === 'string' ? value.trim().toUpperCase() : value
        if (typeof normalizedValue !== 'string' || !/^[A-Z0-9]+\.[A-Z0-9]+$/.test(normalizedValue)) {
          errors[field.name] = '请输入代码.市场格式，例如 000001.SZ'
        } else {
          normalized[field.name] = normalizedValue
        }
      } else if (!toApiDate(value)) {
        errors[field.name] = '请选择有效日期'
      } else {
        normalized[field.name] = value
      }
    }

    for (const field of declared) {
      if (field.pair && !errors[field.name] && !errors[field.pair] && !isRangeOrdered(normalized[field.name], normalized[field.pair])) {
        errors[field.name] = '开始日期不得晚于结束日期'
      }
    }

    firstError.value = declared.find(({ name }) => errors[name])?.name ?? null
    if (firstError.value) return false

    snapshot = normalized
    return true
  }

  function criteria() {
    return snapshot ? { ...snapshot } : {}
  }

  watch(filters, reset, { immediate: true, flush: 'sync' })

  return { values, errors, firstError, setValue, validateValues, criteria, reset }
}
