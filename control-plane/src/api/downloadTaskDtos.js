import { ClientError } from './errors.js'

const UUID = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/
const UTC_INSTANT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/
const LOCAL_DATE = /^\d{4}-\d{2}-\d{2}$/
const BATCH_KEY = /^[0-9]{6}(?:\/[01])*$/
const TASK_STATUSES = new Set([
  'QUEUED',
  'RUNNING',
  'SUCCEEDED',
  'PARTIAL_FAILED',
  'FAILED',
  'INTERRUPTED',
])
const MODES = new Set(['SINGLE', 'RANGE'])
const BATCH_STATUSES = new Set(['PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SPLIT'])
const IDENTIFIER = /^[a-z][a-z0-9_]{1,63}$/
const PARAMETER_TYPES = new Set([
  'DATE',
  'DATE_RANGE_MEMBER',
  'MONTH',
  'TS_CODE',
  'ENUM',
  'TEXT',
])
const ERROR_CODES = new Set([
  'PARAM_REQUIRED', 'PARAM_INVALID', 'PLUGIN_DISABLED',
  'DATASET_MISCONFIGURED', 'SOURCE_AUTH_FAILED',
  'SOURCE_PERMISSION_DENIED', 'SOURCE_RATE_LIMITED', 'SOURCE_UNAVAILABLE',
  'SOURCE_NETWORK_ERROR', 'SOURCE_TIMEOUT', 'SOURCE_PAYLOAD_INVALID',
  'ADAPTER_FIELD_MISSING', 'ADAPTER_TYPE_INVALID', 'PERSISTENCE_FAILED',
  'QUERY_FAILED', 'INTERNAL_ERROR', 'TASK_NOT_FOUND', 'SUBMISSION_CONFLICT',
  'TASK_STATE_CONFLICT', 'TASK_DEFINITION_CHANGED',
  'BATCH_DOWNLOAD_UNAVAILABLE', 'TASK_QUEUE_FULL',
  'BATCH_COMPLETENESS_UNCONFIRMED', 'SOURCE_RANGE_MISMATCH',
  'TASK_LIMIT_EXCEEDED', 'EXECUTION_INTERRUPTED',
])
const MAX_INT64 = 9223372036854775807n

/** Preserve every JSON integer token before JavaScript Number can round it. */
export function parseTaskJson(text) {
  if (typeof text !== 'string') return undefined
  let invalidNumber = false
  try {
    const value = JSON.parse(text, (key, parsed, context) => {
      if (typeof parsed !== 'number') return parsed
      const source = context?.source
      if (typeof source !== 'string' || !/^-?(?:0|[1-9]\d*)$/.test(source)) {
        invalidNumber = true
        return parsed
      }
      const exact = BigInt(source)
      return exact >= BigInt(Number.MIN_SAFE_INTEGER) &&
        exact <= BigInt(Number.MAX_SAFE_INTEGER)
        ? parsed
        : exact
    })
    return invalidNumber ? text : value
  } catch {
    return text
  }
}

function invalid(requestId) {
  throw new ClientError('INVALID_RESPONSE', requestId)
}

function exactObject(value, keys, requestId) {
  if (
    value === null ||
    typeof value !== 'object' ||
    Array.isArray(value) ||
    Object.keys(value).length !== keys.length ||
    !keys.every((key) => Object.hasOwn(value, key))
  ) {
    invalid(requestId)
  }
}

function validUtcInstant(value) {
  if (typeof value !== 'string' || !UTC_INSTANT.test(value)) return false
  const parsed = new Date(value)
  if (Number.isNaN(parsed.valueOf())) return false
  const [date] = value.split('T')
  return parsed.toISOString().slice(0, 10) === date
}

function validLocalDate(value) {
  if (typeof value !== 'string' || !LOCAL_DATE.test(value)) return false
  const parsed = new Date(`${value}T00:00:00Z`)
  return !Number.isNaN(parsed.valueOf()) && parsed.toISOString().slice(0, 10) === value
}

function positiveInt64(value, requestId) {
  const exact =
    typeof value === 'bigint'
      ? value
      : Number.isSafeInteger(value)
        ? BigInt(value)
        : invalid(requestId)
  if (exact < 1n || exact > MAX_INT64) invalid(requestId)
  return exact
}

function nonNegativeInt64(value, requestId) {
  const exact =
    typeof value === 'bigint'
      ? value
      : Number.isSafeInteger(value)
        ? BigInt(value)
        : invalid(requestId)
  if (exact < 0n || exact > MAX_INT64) invalid(requestId)
  return exact
}

function nonBlank(value) {
  return typeof value === 'string' && value.trim() !== ''
}

function uuid(value) {
  return typeof value === 'string' && UUID.test(value)
}

function identifier(value) {
  return typeof value === 'string' && IDENTIFIER.test(value)
}

function stringMap(value, requestId) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    invalid(requestId)
  }
  const result = {}
  for (const [key, item] of Object.entries(value)) {
    if (!identifier(key) || typeof item !== 'string') invalid(requestId)
    result[key] = item
  }
  return Object.freeze(result)
}

function storedError(value, requestId) {
  if (value === null) return null
  exactObject(value, ['code', 'message'], requestId)
  if (!ERROR_CODES.has(value.code) || !nonBlank(value.message)) invalid(requestId)
  return Object.freeze({ code: value.code, message: value.message })
}

function nullableInstant(value, requestId) {
  if (value !== null && !validUtcInstant(value)) invalid(requestId)
  return value
}

/** @returns {Readonly<object>} */
export function parseDownloadTaskReceipt(value, requestId) {
  const keys = ['requestId', 'taskId', 'status', 'version', 'createdAt']
  exactObject(value, keys, requestId)
  if (
    !uuid(value.requestId) ||
    value.requestId !== requestId ||
    !uuid(value.taskId) ||
    !TASK_STATUSES.has(value.status) ||
    !validUtcInstant(value.createdAt)
  ) {
    invalid(requestId)
  }
  return Object.freeze({
    requestId: value.requestId,
    taskId: value.taskId,
    status: value.status,
    version: positiveInt64(value.version, requestId),
    createdAt: value.createdAt,
  })
}

const COUNT_KEYS = [
  'totalBatches',
  'pendingBatches',
  'runningBatches',
  'succeededBatches',
  'failedBatches',
  'splitBatches',
  'sourceRows',
  'insertedRows',
  'updatedRows',
]

function taskCounts(value, requestId) {
  exactObject(value, COUNT_KEYS, requestId)
  const result = Object.fromEntries(
    COUNT_KEYS.map((key) => [key, nonNegativeInt64(value[key], requestId)]),
  )
  if (
    result.totalBatches !==
    result.pendingBatches + result.runningBatches +
      result.succeededBatches + result.failedBatches
  ) {
    invalid(requestId)
  }
  return Object.freeze(result)
}

const TASK_KEYS = [
  'taskId', 'submissionId', 'pluginId', 'apiName', 'mode', 'params', 'status',
  'version', 'planReady', 'counts', 'lastError', 'canRetry', 'canResume',
  'requestCount', 'runRequestCount', 'createdAt', 'updatedAt', 'queuedAt',
  'startedAt', 'finishedAt', 'deadlineAt',
]

/** Parse one complete persisted task snapshot. */
export function parseDownloadTask(value, requestId) {
  exactObject(value, TASK_KEYS, requestId)
  if (
    !uuid(value.taskId) ||
    !uuid(value.submissionId) ||
    !identifier(value.pluginId) ||
    !identifier(value.apiName) ||
    !MODES.has(value.mode) ||
    !TASK_STATUSES.has(value.status) ||
    typeof value.planReady !== 'boolean' ||
    typeof value.canRetry !== 'boolean' ||
    typeof value.canResume !== 'boolean' ||
    !validUtcInstant(value.createdAt) ||
    !validUtcInstant(value.updatedAt) ||
    !validUtcInstant(value.queuedAt)
  ) {
    invalid(requestId)
  }
  return Object.freeze({
    taskId: value.taskId,
    submissionId: value.submissionId,
    pluginId: value.pluginId,
    apiName: value.apiName,
    mode: value.mode,
    params: stringMap(value.params, requestId),
    status: value.status,
    version: positiveInt64(value.version, requestId),
    planReady: value.planReady,
    counts: taskCounts(value.counts, requestId),
    lastError: storedError(value.lastError, requestId),
    canRetry: value.canRetry,
    canResume: value.canResume,
    requestCount: nonNegativeInt64(value.requestCount, requestId),
    runRequestCount: nonNegativeInt64(value.runRequestCount, requestId),
    createdAt: value.createdAt,
    updatedAt: value.updatedAt,
    queuedAt: value.queuedAt,
    startedAt: nullableInstant(value.startedAt, requestId),
    finishedAt: nullableInstant(value.finishedAt, requestId),
    deadlineAt: nullableInstant(value.deadlineAt, requestId),
  })
}

/** Parse a one-based server page without changing membership or ordering. */
export function parseDownloadTaskPage(value, requestId) {
  exactObject(value, ['page', 'pageSize', 'total', 'items'], requestId)
  if (
    !Number.isInteger(value.page) ||
    value.page < 1 ||
    value.page > 2147483647 ||
    ![20, 50, 100].includes(value.pageSize) ||
    !Array.isArray(value.items) ||
    value.items.length > value.pageSize
  ) {
    invalid(requestId)
  }
  const items = value.items.map((item) => parseDownloadTask(item, requestId))
  const total = nonNegativeInt64(value.total, requestId)
  if (
    total < BigInt(items.length) ||
    new Set(items.map(({ taskId }) => taskId.toLowerCase())).size !== items.length
  ) {
    invalid(requestId)
  }
  return Object.freeze({
    page: value.page,
    pageSize: value.pageSize,
    total,
    items: Object.freeze(items),
  })
}

const BATCH_KEYS = [
  'batchId', 'parentBatchId', 'batchKey', 'rangeStart', 'rangeEnd',
  'sourceParams', 'status', 'attemptCount', 'sourceRows', 'insertedRows',
  'updatedRows', 'error', 'startedAt', 'finishedAt', 'createdAt', 'updatedAt',
]

/** Parse one complete persisted batch snapshot. */
export function parseDownloadBatch(value, requestId) {
  exactObject(value, BATCH_KEYS, requestId)
  const ranged = value.rangeStart !== null || value.rangeEnd !== null
  if (
    !uuid(value.batchId) ||
    (value.parentBatchId !== null && !uuid(value.parentBatchId)) ||
    typeof value.batchKey !== 'string' ||
    value.batchKey.length > 128 ||
    !BATCH_KEY.test(value.batchKey) ||
    (ranged && (
      !validLocalDate(value.rangeStart) ||
      !validLocalDate(value.rangeEnd) ||
      value.rangeStart > value.rangeEnd
    )) ||
    !BATCH_STATUSES.has(value.status) ||
    !Number.isInteger(value.attemptCount) ||
    value.attemptCount < 0 ||
    value.attemptCount > 2147483647 ||
    !validUtcInstant(value.createdAt) ||
    !validUtcInstant(value.updatedAt)
  ) {
    invalid(requestId)
  }
  return Object.freeze({
    batchId: value.batchId,
    parentBatchId: value.parentBatchId,
    batchKey: value.batchKey,
    rangeStart: value.rangeStart,
    rangeEnd: value.rangeEnd,
    sourceParams: stringMap(value.sourceParams, requestId),
    status: value.status,
    attemptCount: value.attemptCount,
    sourceRows: nonNegativeInt64(value.sourceRows, requestId),
    insertedRows: nonNegativeInt64(value.insertedRows, requestId),
    updatedRows: nonNegativeInt64(value.updatedRows, requestId),
    error: storedError(value.error, requestId),
    startedAt: nullableInstant(value.startedAt, requestId),
    finishedAt: nullableInstant(value.finishedAt, requestId),
    createdAt: value.createdAt,
    updatedAt: value.updatedAt,
  })
}

/** Parse a one-based server page without changing batch membership or ordering. */
export function parseDownloadBatchPage(value, requestId) {
  exactObject(value, ['page', 'pageSize', 'total', 'items'], requestId)
  if (
    !Number.isInteger(value.page) ||
    value.page < 1 ||
    value.page > 2147483647 ||
    ![20, 50, 100].includes(value.pageSize) ||
    !Array.isArray(value.items) ||
    value.items.length > value.pageSize
  ) {
    invalid(requestId)
  }
  const items = value.items.map((item) => parseDownloadBatch(item, requestId))
  const total = nonNegativeInt64(value.total, requestId)
  if (
    total < BigInt(items.length) ||
    new Set(items.map(({ batchId }) => batchId.toLowerCase())).size !== items.length
  ) {
    invalid(requestId)
  }
  return Object.freeze({
    page: value.page,
    pageSize: value.pageSize,
    total,
    items: Object.freeze(items),
  })
}

const PARAMETER_REQUIRED_KEYS = ['name', 'label', 'type', 'required']
const PARAMETER_KEYS = new Set([
  ...PARAMETER_REQUIRED_KEYS,
  'description', 'defaultValue', 'allowedValues', 'pattern', 'relatedParameter',
])

function parameters(value, requestId) {
  if (!Array.isArray(value)) invalid(requestId)
  const result = value.map((parameter) => {
    if (
      parameter === null ||
      typeof parameter !== 'object' ||
      Array.isArray(parameter) ||
      !PARAMETER_REQUIRED_KEYS.every((key) => Object.hasOwn(parameter, key)) ||
      Object.keys(parameter).some((key) => !PARAMETER_KEYS.has(key)) ||
      !identifier(parameter.name) ||
      !nonBlank(parameter.label) ||
      !PARAMETER_TYPES.has(parameter.type) ||
      typeof parameter.required !== 'boolean'
    ) {
      invalid(requestId)
    }
    if (Object.hasOwn(parameter, 'description') && !nonBlank(parameter.description)) {
      invalid(requestId)
    }
    if (Object.hasOwn(parameter, 'defaultValue') && typeof parameter.defaultValue !== 'string') {
      invalid(requestId)
    }
    if (Object.hasOwn(parameter, 'pattern') && typeof parameter.pattern !== 'string') {
      invalid(requestId)
    }
    if (Object.hasOwn(parameter, 'relatedParameter') && !identifier(parameter.relatedParameter)) {
      invalid(requestId)
    }
    if (Object.hasOwn(parameter, 'allowedValues')) {
      if (
        !Array.isArray(parameter.allowedValues) ||
        parameter.allowedValues.length === 0 ||
        !parameter.allowedValues.every((item) => typeof item === 'string') ||
        new Set(parameter.allowedValues).size !== parameter.allowedValues.length
      ) {
        invalid(requestId)
      }
    }
    if (parameter.type === 'ENUM' && !Object.hasOwn(parameter, 'allowedValues')) {
      invalid(requestId)
    }
    if (parameter.type === 'DATE_RANGE_MEMBER' && !Object.hasOwn(parameter, 'relatedParameter')) {
      invalid(requestId)
    }
    if (parameter.relatedParameter === parameter.name) invalid(requestId)
    const copy = { ...parameter }
    if (copy.allowedValues) copy.allowedValues = Object.freeze([...copy.allowedValues])
    return Object.freeze(copy)
  })
  if (new Set(result.map(({ name }) => name)).size !== result.length) invalid(requestId)
  const byName = new Map(result.map((parameter) => [parameter.name, parameter]))
  for (const parameter of result) {
    if (parameter.type !== 'DATE_RANGE_MEMBER') continue
    const related = byName.get(parameter.relatedParameter)
    if (
      related?.type !== 'DATE_RANGE_MEMBER' ||
      related.relatedParameter !== parameter.name
    ) {
      invalid(requestId)
    }
  }
  return Object.freeze(result)
}

function completenessRule(value, requestId) {
  exactObject(value, ['kind', 'rowLimit', 'evidence'], requestId)
  if (!['UNKNOWN', 'CONFIRMED_ROW_LIMIT', 'VERIFIED_RULE'].includes(value.kind)) {
    invalid(requestId)
  }
  let rowLimit = null
  if (value.kind === 'UNKNOWN') {
    if (value.rowLimit !== null || value.evidence !== null) invalid(requestId)
  } else if (value.kind === 'CONFIRMED_ROW_LIMIT') {
    if (!nonBlank(value.evidence)) invalid(requestId)
    rowLimit = positiveInt64(value.rowLimit, requestId)
  } else {
    if (value.rowLimit !== null || !nonBlank(value.evidence)) invalid(requestId)
  }
  return Object.freeze({ kind: value.kind, rowLimit, evidence: value.evidence })
}

function singleCapability(value, requestId) {
  exactObject(value, ['available', 'parameters'], requestId)
  if (typeof value.available !== 'boolean') invalid(requestId)
  return Object.freeze({
    available: value.available,
    parameters: parameters(value.parameters, requestId),
  })
}

const RANGE_KEYS = [
  'availability', 'unavailableReason', 'dateAxis', 'dateLabel',
  'startParameter', 'endParameter', 'parameters', 'planningMode', 'splittable',
  'policyVersion', 'completenessRule',
]

function rangeCapability(value, requestId) {
  exactObject(value, RANGE_KEYS, requestId)
  const availability = value.availability
  const supported = availability !== 'UNSUPPORTED'
  if (
    !['AVAILABLE', 'NEEDS_VERIFICATION', 'UNSUPPORTED'].includes(availability) ||
    typeof value.splittable !== 'boolean' ||
    !nonBlank(value.policyVersion) ||
    (availability === 'AVAILABLE' ? value.unavailableReason !== null : !nonBlank(value.unavailableReason))
  ) {
    invalid(requestId)
  }
  const parsedParameters = parameters(value.parameters, requestId)
  const parsedCompleteness = completenessRule(value.completenessRule, requestId)
  if (supported) {
    if (
      !['TRADE_DATE', 'ANNOUNCEMENT_DATE', 'REPORT_PERIOD', 'CALENDAR_DATE', 'ISSUE_DATE'].includes(value.dateAxis) ||
      !nonBlank(value.dateLabel) ||
      !identifier(value.startParameter) ||
      !identifier(value.endParameter) ||
      value.startParameter === value.endParameter ||
      !['NATIVE_RANGE', 'CALENDAR_DAYS', 'TRADING_DAYS'].includes(value.planningMode) ||
      parsedParameters.length < 2
    ) {
      invalid(requestId)
    }
    const start = parsedParameters.find(({ name }) => name === value.startParameter)
    const end = parsedParameters.find(({ name }) => name === value.endParameter)
    if (
      start?.type !== 'DATE_RANGE_MEMBER' ||
      end?.type !== 'DATE_RANGE_MEMBER' ||
      start.relatedParameter !== end.name ||
      end.relatedParameter !== start.name
    ) {
      invalid(requestId)
    }
  } else if (
    value.dateAxis !== null || value.dateLabel !== null ||
    value.startParameter !== null || value.endParameter !== null ||
    value.planningMode !== null || parsedParameters.length !== 0 ||
    value.splittable || parsedCompleteness.kind !== 'UNKNOWN'
  ) {
    invalid(requestId)
  }
  if (value.splittable && value.planningMode !== 'NATIVE_RANGE') invalid(requestId)
  if (availability === 'AVAILABLE' && parsedCompleteness.kind === 'UNKNOWN') {
    invalid(requestId)
  }
  return Object.freeze({
    availability,
    unavailableReason: value.unavailableReason,
    dateAxis: value.dateAxis,
    dateLabel: value.dateLabel,
    startParameter: value.startParameter,
    endParameter: value.endParameter,
    parameters: parsedParameters,
    planningMode: value.planningMode,
    splittable: value.splittable,
    policyVersion: value.policyVersion,
    completenessRule: parsedCompleteness,
  })
}

/** Parse capability metadata without retaining the wire object. */
export function parseDownloadCapabilities(value, requestId) {
  exactObject(value, ['single', 'range'], requestId)
  return Object.freeze({
    single: singleCapability(value.single, requestId),
    range: rangeCapability(value.range, requestId),
  })
}
