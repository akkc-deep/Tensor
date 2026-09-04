import { reactive, ref, watch } from 'vue'

import { toApiDate, toApiMonth } from '../utils/date.js'
import {
  hasValue,
  isRangeOrdered,
  matchesPattern,
} from '../utils/validation.js'

function clear(object) {
  for (const key of Object.keys(object)) delete object[key]
}

function displayValue(parameter) {
  const value = parameter.defaultValue ?? ''
  if (
    (parameter.type === 'DATE' || parameter.type === 'DATE_RANGE_MEMBER') &&
    /^\d{8}$/.test(value)
  ) {
    return `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6)}`
  }
  if (parameter.type === 'MONTH' && /^\d{6}$/.test(value)) {
    return `${value.slice(0, 4)}-${value.slice(4)}`
  }
  return value
}

function normalizedValue(parameter, value) {
  switch (parameter.type) {
    case 'DATE':
    case 'DATE_RANGE_MEMBER':
      return toApiDate(value)
    case 'MONTH':
      return toApiMonth(value)
    case 'TS_CODE':
      return value.trim().toUpperCase()
    case 'TEXT':
      return value.trim()
    default:
      return value
  }
}

function typeError(parameter, value) {
  switch (parameter.type) {
    case 'DATE':
    case 'DATE_RANGE_MEMBER':
      return value === null ? '请选择有效日期' : ''
    case 'MONTH':
      return value === null ? '请选择有效月份' : ''
    case 'TS_CODE':
      return /^[A-Z0-9]+\.[A-Z0-9]+$/.test(value)
        ? ''
        : '请输入代码.市场格式，例如 000001.SZ'
    case 'ENUM':
      return parameter.allowedValues.includes(value) ? '' : '请选择有效选项'
    default:
      return ''
  }
}

export function useParameterForm(parameters) {
  const values = reactive({})
  const errors = reactive(Object.create(null))
  const firstError = ref(null)
  let snapshot = null

  function reset() {
    clear(values)
    clear(errors)
    firstError.value = null
    snapshot = null
    for (const parameter of parameters.value) {
      values[parameter.name] = displayValue(parameter)
    }
  }

  function setValue(name, value) {
    values[name] = value
    delete errors[name]
    firstError.value =
      parameters.value.find(({ name: field }) => errors[field])?.name ?? null
    snapshot = null
  }

  function validateValues() {
    clear(errors)
    firstError.value = null
    snapshot = null
    const normalized = {}

    for (const parameter of parameters.value) {
      const value = values[parameter.name]
      if (!hasValue(value)) {
        if (parameter.required) errors[parameter.name] = '此项为必填项'
        continue
      }

      const result = normalizedValue(parameter, value)
      const error = typeError(parameter, result)
      if (error) {
        errors[parameter.name] = error
        continue
      }
      if (parameter.pattern && !matchesPattern(result, parameter.pattern)) {
        errors[parameter.name] = '输入格式不正确'
        continue
      }
      normalized[parameter.name] = result
    }

    parameters.value.forEach((parameter, index) => {
      if (parameter.type !== 'DATE_RANGE_MEMBER' || errors[parameter.name]) return
      const relatedIndex = parameters.value.findIndex(
        ({ name }) => name === parameter.relatedParameter,
      )
      const related = parameters.value[relatedIndex]
      if (
        relatedIndex <= index ||
        related?.type !== 'DATE_RANGE_MEMBER' ||
        related.relatedParameter !== parameter.name ||
        errors[related.name]
      ) {
        return
      }
      if (
        !isRangeOrdered(
          normalized[parameter.name],
          normalized[related.name],
        )
      ) {
        errors[parameter.name] = '开始日期不得晚于结束日期'
      }
    })

    firstError.value =
      parameters.value.find(({ name }) => errors[name])?.name ?? null
    if (firstError.value) return false

    snapshot = normalized
    return true
  }

  function normalizedValues() {
    return snapshot ? { ...snapshot } : {}
  }

  watch(parameters, reset, { immediate: true })

  return {
    values,
    errors,
    firstError,
    setValue,
    validateValues,
    normalizedValues,
    reset,
  }
}
