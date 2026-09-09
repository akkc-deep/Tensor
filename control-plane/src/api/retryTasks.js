import { inclusiveDateDays, toApiDate } from '../utils/date.js'
import { isDownloadResult, isRecoveryScope } from './downloadResult.js'
import { ApiError, ClientError } from './errors.js'
import { http } from './http.js'

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const IDENTIFIER = /^[a-z][a-z0-9_]{1,63}$/
const UTC_MILLIS = /^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2}):(\d{2})\.\d{3}Z$/
const SUMMARY_KEYS = [
  'taskId', 'pluginId', 'apiName', 'pluginDisplayName', 'apiDisplayName',
  'originalDateRange', 'originalDateRangeStatus', 'failedItemCount', 'failedScopes',
  'createdAt', 'updatedAt',
]
const DETAIL_KEYS = [...SUMMARY_KEYS, 'requestId', 'taskParams', 'items', 'retrying', 'canExecute', 'executionBlocker']
const PAGE_KEYS = ['requestId', 'page', 'pageSize', 'totalElements', 'totalPages', 'items']
const ITEM_KEYS = ['targetType', 'targetValue', 'timeType', 'timeValue', 'errorCode', 'errorMessage', 'updatedAt']
const RANGE_KEYS = ['startDate', 'endDate']
const BLOCKER_KEYS = ['code', 'message']
const PAGE_SIZES = new Set([20, 50, 100])
const RANGE_STATUSES = new Set(['RECORDED', 'NOT_APPLICABLE', 'NOT_RECORDED', 'UNCONFIRMED'])
const FORBIDDEN_PARAMS = new Set(['offset', 'limit', 'cursor', 'page', 'page_size', 'trade_date', 'ann_date', 'month'])

function exactObject(value, keys) {
  return value !== null && typeof value === 'object' && !Array.isArray(value) &&
    Object.keys(value).length === keys.length && keys.every((key) => Object.hasOwn(value, key))
}

function nonBlank(value, maxLength = Infinity) {
  return typeof value === 'string' && value.trim() !== '' && value.length <= maxLength
}

function count(value) {
  return Number.isSafeInteger(value) && value >= 0
}

function header(headers, name) {
  const value = (typeof headers?.get === 'function' ? headers.get(name) : undefined) ??
    headers?.[name] ?? headers?.[name.toLowerCase()]
  return nonBlank(value) ? value : null
}

function outgoingRequestId(response) {
  return header(response.config?.headers, 'X-Request-Id')
}

function validTimestamp(value) {
  if (typeof value !== 'string') return false
  const match = UTC_MILLIS.exec(value)
  return match !== null && toApiDate(match[1]) !== null &&
    +match[2] <= 23 && +match[3] <= 59 && +match[4] <= 59
}

function compactDate(value) {
  if (typeof value !== 'string' || !/^\d{8}$/.test(value)) return null
  const iso = `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6)}`
  return toApiDate(iso) === value ? iso : null
}

function validOriginalRange(value) {
  if (!exactObject(value, RANGE_KEYS)) return false
  const start = compactDate(value.startDate)
  const end = compactDate(value.endDate)
  const days = start && end ? inclusiveDateDays(start, end) : 0
  return days >= 1 && days <= 31
}

function selectorKey(value) {
  return [value.targetType, value.targetValue, value.timeType, value.timeValue].join('\0')
}

function sortedUniqueScopes(scopes) {
  if (!Array.isArray(scopes) || scopes.length === 0 || !scopes.every(isRecoveryScope)) return false
  const keys = scopes.map(selectorKey)
  return new Set(keys).size === keys.length && keys.every((key, index) => index === 0 || keys[index - 1] < key)
}

function validSummary(value) {
  if (!exactObject(value, SUMMARY_KEYS) || typeof value.taskId !== 'string' || !UUID.test(value.taskId) ||
    typeof value.pluginId !== 'string' || !IDENTIFIER.test(value.pluginId) ||
    typeof value.apiName !== 'string' || !IDENTIFIER.test(value.apiName) ||
    !(value.pluginDisplayName === null || typeof value.pluginDisplayName === 'string') ||
    !(value.apiDisplayName === null || typeof value.apiDisplayName === 'string') ||
    !RANGE_STATUSES.has(value.originalDateRangeStatus) || !Number.isSafeInteger(value.failedItemCount) ||
    value.failedItemCount < 1 || !sortedUniqueScopes(value.failedScopes) ||
    value.failedItemCount !== value.failedScopes.length || !validTimestamp(value.createdAt) ||
    !validTimestamp(value.updatedAt)) {
    return false
  }
  return value.originalDateRangeStatus === 'RECORDED'
    ? validOriginalRange(value.originalDateRange)
    : value.originalDateRange === null
}

function validTaskParams(value) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) return false
  return Object.entries(value).every(([key, item]) =>
    /^[a-z][a-z0-9_]*$/.test(key) && !FORBIDDEN_PARAMS.has(key) &&
    !/(token|authorization|cookie|password|credential)/i.test(key) && typeof item === 'string')
}

function validItem(value) {
  return exactObject(value, ITEM_KEYS) &&
    isRecoveryScope(Object.fromEntries(ITEM_KEYS.slice(0, 4).map((key) => [key, value[key]]))) &&
    nonBlank(value.errorCode, 64) && nonBlank(value.errorMessage, 512) && validTimestamp(value.updatedAt)
}

function validBlocker(value) {
  return value === null || (exactObject(value, BLOCKER_KEYS) &&
    nonBlank(value.code, 64) && nonBlank(value.message, 512))
}

function validDetail(value, taskId) {
  if (!exactObject(value, DETAIL_KEYS) || !validSummary(Object.fromEntries(SUMMARY_KEYS.map((key) => [key, value[key]]))) ||
    value.taskId !== taskId || !nonBlank(value.requestId) || !validTaskParams(value.taskParams) ||
    !Array.isArray(value.items) || value.items.length !== value.failedItemCount || !value.items.every(validItem) ||
    typeof value.retrying !== 'boolean' || typeof value.canExecute !== 'boolean' || !validBlocker(value.executionBlocker) ||
    value.canExecute !== (value.executionBlocker === null) ||
    (value.originalDateRangeStatus === 'UNCONFIRMED' && value.canExecute) ||
    (value.retrying && (value.canExecute || value.executionBlocker?.code !== 'DOWNLOAD_BUSY'))) {
    return false
  }
  return value.items.every((item, index) => selectorKey(item) === selectorKey(value.failedScopes[index]))
}

function validPage(value, requestedPage, requestedSize) {
  if (!exactObject(value, PAGE_KEYS) || !nonBlank(value.requestId) || !Number.isSafeInteger(value.page) || value.page < 1 ||
    !PAGE_SIZES.has(value.pageSize) || value.pageSize !== requestedSize || !count(value.totalElements) ||
    !count(value.totalPages) || !Array.isArray(value.items) || !value.items.every(validSummary)) {
    return false
  }
  if (value.totalElements === 0) {
    return value.page === 1 && value.totalPages === 0 && value.items.length === 0
  }
  const totalPages = Math.ceil(value.totalElements / value.pageSize)
  const expectedPage = Math.min(requestedPage, totalPages)
  const expectedItems = expectedPage === totalPages
    ? value.totalElements - (totalPages - 1) * value.pageSize
    : value.pageSize
  if (value.totalPages !== totalPages || value.page !== expectedPage || value.items.length !== expectedItems) return false
  const ids = value.items.map((item) => item.taskId.toLowerCase())
  if (new Set(ids).size !== ids.length) return false
  return value.items.every((item, index) => index === 0 ||
    value.items[index - 1].updatedAt > item.updatedAt ||
    (value.items[index - 1].updatedAt === item.updatedAt && value.items[index - 1].taskId > item.taskId))
}

function normalizeTaskId(taskId) {
  if (typeof taskId !== 'string' || !UUID.test(taskId)) throw new ClientError('INVALID_RESPONSE')
  return taskId.toLowerCase()
}

function checked(response, valid) {
  const requestId = outgoingRequestId(response)
  if (!valid(response.data) || response.data.requestId !== header(response.headers, 'X-Request-Id')) {
    throw new ClientError('INVALID_RESPONSE', requestId)
  }
  return response.data
}

export async function listRetryTasks({ pluginId, apiName, page = 1, pageSize = 20 } = {}) {
  const params = { page, pageSize }
  if (typeof pluginId === 'string' && pluginId.trim() !== '') params.pluginId = pluginId
  if (typeof apiName === 'string' && apiName.trim() !== '') params.apiName = apiName
  const response = await http.get('/retry-tasks', { params })
  return checked(response, (data) => validPage(data, page, pageSize))
}

export async function getRetryTask(taskId) {
  const normalized = normalizeTaskId(taskId)
  const response = await http.get(`/retry-tasks/${encodeURIComponent(normalized)}`)
  return checked(response, (data) => validDetail(data, normalized))
}

export async function executeRetryTask(taskId) {
  const normalized = normalizeTaskId(taskId)
  try {
    const response = await http.post(`/retry-tasks/${encodeURIComponent(normalized)}/execute`)
    return checked(response, (data) => isDownloadResult(data) &&
      (data.taskId === null || data.taskId === normalized))
  } catch (failure) {
    const snapshot = failure instanceof ApiError ? failure.downloadResult : null
    if (snapshot && snapshot.taskId !== null && snapshot.taskId !== normalized) {
      throw new ClientError('INVALID_RESPONSE', failure.requestId)
    }
    throw failure
  }
}
