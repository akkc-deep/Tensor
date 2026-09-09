import { inclusiveDateDays, toApiDate, toApiMonth } from '../utils/date.js'

const RESULT_KEYS = [
  'requestId',
  'outcome',
  'pluginId',
  'apiName',
  'sourceRowCount',
  'insertedRows',
  'updatedRows',
  'message',
  'completedUnits',
  'failedUnits',
  'notStartedUnits',
  'skippedClosedDates',
  'taskId',
  'remainingFailedUnits',
  'failureRecordStatus',
  'failures',
  'notStartedScopes',
  'unconfirmedScopes',
]
const SCOPE_KEYS = ['targetType', 'targetValue', 'timeType', 'timeValue']
const FAILURE_KEYS = [...SCOPE_KEYS, 'errorCode', 'errorMessage']
const IDENTIFIER = /^[a-z][a-z0-9_]{1,63}$/
const STOCK = /^[A-Z0-9]+\.[A-Z0-9]+$/
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const OUTCOMES = new Set(['SUCCESS', 'EMPTY', 'NO_OPEN_DATES', 'PARTIAL', 'FAILED'])
const RECORD_STATUSES = new Set(['NOT_REQUIRED', 'CONFIRMED', 'UNCONFIRMED'])

function exactObject(value, keys) {
  return (
    value !== null &&
    typeof value === 'object' &&
    !Array.isArray(value) &&
    Object.keys(value).length === keys.length &&
    keys.every((key) => Object.hasOwn(value, key))
  )
}

function nonBlank(value, maxLength = Infinity) {
  return typeof value === 'string' && value.trim() !== '' && value.length <= maxLength
}

function count(value) {
  return Number.isSafeInteger(value) && value >= 0
}

function nullableCount(value) {
  return value === null || count(value)
}

function validSelector(value) {
  if (
    !['STOCK', 'REQUEST'].includes(value.targetType) ||
    typeof value.targetValue !== 'string' ||
    value.targetValue.length > 64 ||
    typeof value.timeValue !== 'string' ||
    value.timeValue.length > 64
  ) {
    return false
  }
  if (value.targetType === 'REQUEST' ? value.targetValue !== '' : !STOCK.test(value.targetValue)) {
    return false
  }

  if (value.timeType === 'DATE') return toApiDate(value.timeValue) !== null
  if (value.timeType === 'MONTH') return toApiMonth(value.timeValue) !== null
  if (value.timeType === 'NONE') return value.timeValue === ''
  if (value.timeType !== 'RANGE') return false

  const dates = value.timeValue.split('/')
  return dates.length === 2 && inclusiveDateDays(dates[0], dates[1]) > 1
}

export function isRecoveryScope(value) {
  return exactObject(value, SCOPE_KEYS) && validSelector(value)
}

function validFailure(value) {
  return (
    exactObject(value, FAILURE_KEYS) &&
    validSelector(value) &&
    nonBlank(value.errorCode, 64) &&
    nonBlank(value.errorMessage, 512)
  )
}

function validOutcomeCounts(value) {
  if (
    value.completedUnits === 0 &&
    (value.sourceRowCount !== 0 || value.insertedRows !== 0 || value.updatedRows !== 0)
  ) {
    return false
  }

  if (value.outcome === 'SUCCESS') {
    return value.completedUnits > 0 && value.failedUnits === 0 && value.sourceRowCount > 0
  }
  if (value.outcome === 'EMPTY') {
    return (
      value.completedUnits > 0 &&
      value.failedUnits === 0 &&
      value.sourceRowCount === 0 &&
      value.insertedRows === 0 &&
      value.updatedRows === 0
    )
  }
  if (value.outcome === 'NO_OPEN_DATES') {
    return (
      value.completedUnits === 0 &&
      value.failedUnits === 0 &&
      value.sourceRowCount === 0 &&
      value.insertedRows === 0 &&
      value.updatedRows === 0 &&
      value.skippedClosedDates > 0
    )
  }
  if (value.outcome === 'PARTIAL') {
    return value.completedUnits > 0 && value.failedUnits > 0
  }
  if (value.outcome === 'FAILED') {
    return value.completedUnits === 0 && value.failedUnits > 0
  }
  return value.failureRecordStatus === 'UNCONFIRMED'
}

function validHttpCompletion(value) {
  if (
    value.notStartedUnits !== 0 ||
    value.notStartedScopes.length !== 0 ||
    value.unconfirmedScopes.length !== 0 ||
    value.failureRecordStatus === 'UNCONFIRMED'
  ) {
    return false
  }

  if (value.outcome === 'PARTIAL' || value.outcome === 'FAILED') {
    return (
      value.taskId !== null &&
      value.remainingFailedUnits !== null &&
      value.remainingFailedUnits > 0 &&
      value.failureRecordStatus === 'CONFIRMED' &&
      value.failures.length > 0
    )
  }
  return value.taskId === null && value.remainingFailedUnits === 0 && value.failures.length === 0
}

/**
 * @param {unknown} value
 * @param {{allowUnconfirmed?: boolean}} [options]
 * @returns {boolean}
 */
export function isDownloadResult(value, { allowUnconfirmed = false } = {}) {
  if (!exactObject(value, RESULT_KEYS)) return false
  const unconfirmed = value.outcome === 'UNCONFIRMED'
  if (
    (!OUTCOMES.has(value.outcome) && !(allowUnconfirmed === true && unconfirmed)) ||
    !nonBlank(value.requestId) ||
    typeof value.pluginId !== 'string' ||
    !IDENTIFIER.test(value.pluginId) ||
    typeof value.apiName !== 'string' ||
    !IDENTIFIER.test(value.apiName) ||
    !nonBlank(value.message) ||
    !count(value.sourceRowCount) ||
    !count(value.insertedRows) ||
    !count(value.updatedRows) ||
    !count(value.completedUnits) ||
    !count(value.failedUnits) ||
    !nullableCount(value.notStartedUnits) ||
    !count(value.skippedClosedDates) ||
    !(value.taskId === null || (typeof value.taskId === 'string' && UUID.test(value.taskId))) ||
    !nullableCount(value.remainingFailedUnits) ||
    !RECORD_STATUSES.has(value.failureRecordStatus) ||
    !Array.isArray(value.failures) ||
    !value.failures.every(validFailure) ||
    !Array.isArray(value.notStartedScopes) ||
    !value.notStartedScopes.every(isRecoveryScope) ||
    !Array.isArray(value.unconfirmedScopes) ||
    !value.unconfirmedScopes.every(isRecoveryScope) ||
    !validOutcomeCounts(value)
  ) {
    return false
  }

  return unconfirmed ? value.failureRecordStatus === 'UNCONFIRMED' : validHttpCompletion(value)
}
