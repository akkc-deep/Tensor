import { ClientError } from './errors.js'
import { http } from './http.js'
import {
  parseDownloadBatchPage,
  parseDownloadCapabilities,
  parseDownloadTask,
  parseDownloadTaskPage,
  parseDownloadTaskReceipt,
  parseTaskJson,
} from './downloadTaskDtos.js'

const IDENTIFIER = /^[a-z][a-z0-9_]{1,63}$/
const UUID = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/
const STATUSES = new Set([
  'QUEUED', 'RUNNING', 'SUCCEEDED', 'PARTIAL_FAILED', 'FAILED', 'INTERRUPTED',
])
const CRITERIA_KEYS = new Set([
  'page', 'pageSize', 'pluginId', 'apiName', 'status', 'submissionId',
])
const BATCH_CRITERIA_KEYS = new Set(['page', 'pageSize'])
const MAX_INT64 = 9223372036854775807n

function header(headers, name) {
  return (
    (typeof headers?.get === 'function' ? headers.get(name) : undefined) ??
    headers?.[name] ??
    headers?.[name.toLowerCase()]
  )
}

function requestId(response) {
  return response.config.headers.get('X-Request-Id')
}

function invalid(response) {
  throw new ClientError('INVALID_RESPONSE', requestId(response))
}

function validateResponse(response, statuses) {
  const outgoingRequestId = requestId(response)
  if (
    !statuses.includes(response.status) ||
    header(response.headers, 'X-Request-Id') !== outgoingRequestId
  ) {
    invalid(response)
  }
  return outgoingRequestId
}

function taskOptions(signal) {
  return { responseType: 'text', transformResponse: [parseTaskJson], signal }
}

function validIdentifier(value) {
  return typeof value === 'string' && IDENTIFIER.test(value)
}

function taskPath(taskId) {
  if (typeof taskId !== 'string' || !UUID.test(taskId)) {
    throw new TypeError('Download taskId is invalid')
  }
  return `/download-tasks/${taskId.toLowerCase()}`
}

async function controlDownloadTask(taskId, expectedVersion, action, signal) {
  const path = taskPath(taskId)
  if (
    typeof expectedVersion !== 'bigint' ||
    expectedVersion < 1n ||
    expectedVersion > MAX_INT64
  ) {
    throw new TypeError('Download task expectedVersion is invalid')
  }
  const response = await http.post(
    `${path}/${action}`,
    `{"expectedVersion":${expectedVersion.toString()}}`,
    {
      ...taskOptions(signal),
      headers: { 'Content-Type': 'application/json' },
    },
  )
  const outgoingRequestId = validateResponse(response, [202])
  const receipt = parseDownloadTaskReceipt(response.data, outgoingRequestId)
  if (
    receipt.taskId.toLowerCase() !== taskId.toLowerCase() ||
    header(response.headers, 'Location') !==
      `/api/v1/download-tasks/${taskId.toLowerCase()}`
  ) {
    invalid(response)
  }
  return receipt
}

export function retryDownloadTask(taskId, expectedVersion, { signal } = {}) {
  return controlDownloadTask(taskId, expectedVersion, 'retry', signal)
}

export function resumeDownloadTask(taskId, expectedVersion, { signal } = {}) {
  return controlDownloadTask(taskId, expectedVersion, 'resume', signal)
}

/** Load one persisted task snapshot. */
export async function getDownloadTask(taskId, { signal } = {}) {
  const path = taskPath(taskId)
  const response = await http.get(path, taskOptions(signal))
  const task = parseDownloadTask(
    response.data,
    validateResponse(response, [200]),
  )
  if (task.taskId.toLowerCase() !== taskId.toLowerCase()) invalid(response)
  return task
}

function batchCriteria(criteria) {
  if (
    criteria === null ||
    typeof criteria !== 'object' ||
    Array.isArray(criteria) ||
    Object.keys(criteria).some((key) => !BATCH_CRITERIA_KEYS.has(key))
  ) {
    throw new TypeError('Download batch criteria are invalid')
  }
  const page = criteria.page === undefined ? 1 : criteria.page
  const pageSize = criteria.pageSize === undefined ? 20 : criteria.pageSize
  if (!Number.isInteger(page) || page < 1 || page > 2147483647) {
    throw new TypeError('Download batch page is invalid')
  }
  if (![20, 50, 100].includes(pageSize)) {
    throw new TypeError('Download batch pageSize is invalid')
  }
  return { page, pageSize }
}

/** Query one immutable server page of leaf batches. */
export async function listDownloadTaskBatches(
  taskId,
  criteria = {},
  { signal } = {},
) {
  const path = taskPath(taskId)
  const values = batchCriteria(criteria)
  const params = new URLSearchParams([
    ['page', String(values.page)],
    ['pageSize', String(values.pageSize)],
    ['includeSplit', 'false'],
  ])
  const response = await http.get(`${path}/batches`, {
    ...taskOptions(signal),
    params,
  })
  const page = parseDownloadBatchPage(
    response.data,
    validateResponse(response, [200]),
  )
  if (
    page.page !== values.page ||
    page.pageSize !== values.pageSize ||
    page.items.some(({ status }) => status === 'SPLIT')
  ) {
    invalid(response)
  }
  return page
}

/** Load current SINGLE/RANGE metadata for one dataset. */
export async function getDownloadCapabilities(pluginId, apiName, { signal } = {}) {
  if (!validIdentifier(pluginId) || !validIdentifier(apiName)) {
    throw new TypeError('Download capability identifiers are invalid')
  }
  const response = await http.get(
    `/data-sources/${encodeURIComponent(pluginId)}/apis/${encodeURIComponent(apiName)}/download-capabilities`,
    taskOptions(signal),
  )
  return parseDownloadCapabilities(
    response.data,
    validateResponse(response, [200]),
  )
}

export async function submitDownloadTask(request, { signal } = {}) {
  const response = await http.post(
    '/download-tasks',
    {
      submissionId: request.submissionId,
      pluginId: request.pluginId,
      apiName: request.apiName,
      mode: request.mode,
      params: request.params,
    },
    taskOptions(signal),
  )
  const outgoingRequestId = validateResponse(response, [200, 202])
  const receipt = parseDownloadTaskReceipt(response.data, outgoingRequestId)
  if (
    (response.status === 202 && receipt.status !== 'QUEUED') ||
    header(response.headers, 'Location') !==
    `/api/v1/download-tasks/${receipt.taskId}`
  ) {
    invalid(response)
  }
  return receipt
}

function listCriteria(criteria) {
  if (
    criteria === null ||
    typeof criteria !== 'object' ||
    Array.isArray(criteria) ||
    Object.keys(criteria).some((key) => !CRITERIA_KEYS.has(key))
  ) {
    throw new TypeError('Download task criteria are invalid')
  }
  const page = criteria.page === undefined ? 1 : criteria.page
  const pageSize = criteria.pageSize === undefined ? 20 : criteria.pageSize
  if (!Number.isInteger(page) || page < 1 || page > 2147483647) {
    throw new TypeError('Download task page is invalid')
  }
  if (![20, 50, 100].includes(pageSize)) {
    throw new TypeError('Download task pageSize is invalid')
  }
  for (const key of ['pluginId', 'apiName']) {
    if (criteria[key] !== undefined && !validIdentifier(criteria[key])) {
      throw new TypeError(`Download task ${key} is invalid`)
    }
  }
  if (criteria.status !== undefined && !STATUSES.has(criteria.status)) {
    throw new TypeError('Download task status is invalid')
  }
  if (
    criteria.submissionId !== undefined &&
    (typeof criteria.submissionId !== 'string' || !UUID.test(criteria.submissionId))
  ) {
    throw new TypeError('Download task submissionId is invalid')
  }
  const values = { page, pageSize }
  for (const key of ['pluginId', 'apiName', 'status', 'submissionId']) {
    if (criteria[key] !== undefined) values[key] = criteria[key]
  }
  return values
}

/** Query one immutable server page; no client-side filtering or insertion. */
export async function listDownloadTasks(criteria = {}, { signal } = {}) {
  const values = listCriteria(criteria)
  const params = new URLSearchParams(
    Object.entries(values).map(([key, value]) => [key, String(value)]),
  )
  const response = await http.get('/download-tasks', {
    ...taskOptions(signal),
    params,
  })
  const page = parseDownloadTaskPage(
    response.data,
    validateResponse(response, [200]),
  )
  if (page.page !== values.page || page.pageSize !== values.pageSize) {
    invalid(response)
  }
  if (values.submissionId !== undefined) {
    const found =
      page.total === 1n &&
      page.items.length === 1 &&
      page.items[0].submissionId.toLowerCase() === values.submissionId.toLowerCase()
    const absent = page.total === 0n && page.items.length === 0
    if (!found && !absent) invalid(response)
  }
  return page
}
