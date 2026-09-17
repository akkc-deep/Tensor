import { parseDownloadCapabilities } from './downloadTaskDtos.js'
import { ClientError } from './errors.js'

const UUID = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/
const IDENTIFIER = /^[a-z][a-z0-9_]{1,63}$/
const HASH = /^[0-9a-f]{64}$/
const LOCAL_DATE = /^[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}$/
const UTC_INSTANT = /^[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/
const COUNT = /^(0|[1-9][0-9]*)$/
const RULE_ID = /^[a-z][a-z0-9_.-]*$/
const RATE = /^(0\.[0-9]{6}|1\.000000)$/
const TASK_STATUSES = new Set(['QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'INTERRUPTED'])
const UNIT_STATUSES = new Set(['PENDING', 'RUNNING', 'COMPLETED', 'ERROR', 'NOT_RUN'])
const STATUSES = new Set(['PASS', 'FAIL', 'WARN', 'UNKNOWN', 'NOT_APPLICABLE'])
const ISSUE_TYPES = new Set([
  'MISSING', 'SUSPECTED_MISSING', 'EXTRA', 'REQUIRED_FIELD_MISSING',
  'BUSINESS_KEY_INVALID', 'SOURCE_IDENTITY_MISMATCH', 'REFERENCE_INCOMPLETE',
  'DATE_SCOPE_UNRESOLVED', 'RULE_EXECUTION_FAILED',
])

function invalid(requestId) {
  throw new ClientError('INVALID_RESPONSE', requestId)
}

function exact(value, keys, requestId) {
  if (value === null || typeof value !== 'object' || Array.isArray(value) ||
      Object.keys(value).length !== keys.length || !keys.every((key) => Object.hasOwn(value, key))) {
    invalid(requestId)
  }
}

function validDate(value) {
  if (typeof value !== 'string' || !LOCAL_DATE.test(value)) return false
  const parsed = new Date(`${value}T00:00:00Z`)
  return !Number.isNaN(parsed.valueOf()) && parsed.toISOString().slice(0, 10) === value
}

function validInstant(value) {
  if (typeof value !== 'string' || !UTC_INSTANT.test(value)) return false
  const parsed = new Date(value)
  return !Number.isNaN(parsed.valueOf()) && parsed.toISOString().slice(0, 10) === value.slice(0, 10)
}

const nonBlank = (value) => typeof value === 'string' && /\S/.test(value)
const validUuid = (value) => typeof value === 'string' && UUID.test(value)
const validIdentifier = (value) => typeof value === 'string' && IDENTIFIER.test(value)

function count(value, requestId, nullable = false) {
  if (nullable && value === null) return null
  if (typeof value !== 'string' || !COUNT.test(value)) invalid(requestId)
  return BigInt(value)
}

function positiveCount(value, requestId) {
  if (typeof value !== 'string' || !/^[1-9][0-9]*$/.test(value)) invalid(requestId)
  return BigInt(value)
}

function positiveInt(value, requestId) {
  if (!Number.isInteger(value) || value < 1 || value > 2147483647) invalid(requestId)
  return value
}

function array(value, parser, requestId, { min = 0, unique = false } = {}) {
  if (!Array.isArray(value) || value.length < min) invalid(requestId)
  const result = value.map((item) => parser(item, requestId))
  if (unique && new Set(result).size !== result.length) invalid(requestId)
  return Object.freeze(result)
}

function stringValue(value, requestId) {
  if (!nonBlank(value)) invalid(requestId)
  return value
}

function identifierValue(value, requestId) {
  if (!validIdentifier(value)) invalid(requestId)
  return value
}

function nullableInstant(value, requestId) {
  if (value !== null && !validInstant(value)) invalid(requestId)
  return value
}

function nullableString(value, requestId) {
  if (value !== null && !nonBlank(value)) invalid(requestId)
  return value
}

function datasetKey(value, requestId) {
  exact(value, ['pluginId', 'apiName'], requestId)
  if (!validIdentifier(value.pluginId) || !validIdentifier(value.apiName)) invalid(requestId)
  return Object.freeze({ pluginId: value.pluginId, apiName: value.apiName })
}

function dateRange(value, requestId) {
  exact(value, ['startDate', 'endDate'], requestId)
  if (!validDate(value.startDate) || !validDate(value.endDate) || value.startDate > value.endDate) invalid(requestId)
  return Object.freeze({ startDate: value.startDate, endDate: value.endDate })
}

function scope(value, requestId) {
  exact(value, ['datasetKey', 'symbol', 'startDate', 'endDate', 'acceptedAt', 'snapshotStartedAt'], requestId)
  if ((value.symbol !== null && !nonBlank(value.symbol)) || !validDate(value.startDate) ||
      !validDate(value.endDate) || value.startDate > value.endDate || !validInstant(value.acceptedAt)) invalid(requestId)
  return Object.freeze({
    datasetKey: datasetKey(value.datasetKey, requestId), symbol: value.symbol,
    startDate: value.startDate, endDate: value.endDate, acceptedAt: value.acceptedAt,
    snapshotStartedAt: nullableInstant(value.snapshotStartedAt, requestId),
  })
}

function taskScope(value, requestId) {
  exact(value, ['pluginId', 'symbols', 'startDate', 'endDate', 'apiNames', 'acceptedAt'], requestId)
  if (!validIdentifier(value.pluginId) || !validDate(value.startDate) || !validDate(value.endDate) ||
      value.startDate > value.endDate || !validInstant(value.acceptedAt)) invalid(requestId)
  return Object.freeze({
    pluginId: value.pluginId,
    symbols: array(value.symbols, stringValue, requestId, { min: 1 }),
    startDate: value.startDate, endDate: value.endDate,
    apiNames: array(value.apiNames, identifierValue, requestId, { min: 1 }),
    acceptedAt: value.acceptedAt,
  })
}

function dependency(value, requestId) {
  exact(value, ['datasetKey', 'columns', 'purpose'], requestId)
  if (!nonBlank(value.purpose)) invalid(requestId)
  return Object.freeze({
    datasetKey: datasetKey(value.datasetKey, requestId),
    columns: array(value.columns, identifierValue, requestId, { min: 1, unique: true }),
    purpose: value.purpose,
  })
}

function ruleDescriptor(value, requestId) {
  const keys = ['ruleId', 'version', 'displayName', 'dimension', 'requiredColumns', 'dependencies', 'description']
  exact(value, keys, requestId)
  if (typeof value.ruleId !== 'string' || !RULE_ID.test(value.ruleId) || !nonBlank(value.version) ||
      !nonBlank(value.displayName) || !['COVERAGE', 'KEY', 'FIELD'].includes(value.dimension) ||
      !nonBlank(value.description)) invalid(requestId)
  return Object.freeze({
    ruleId: value.ruleId, version: value.version, displayName: value.displayName,
    dimension: value.dimension,
    requiredColumns: array(value.requiredColumns, identifierValue, requestId, { unique: true }),
    dependencies: array(value.dependencies, dependency, requestId), description: value.description,
  })
}

function descriptor(value, requestId) {
  const keys = ['datasetKey', 'scopeKind', 'symbolField', 'dateField', 'dateLabel', 'marketZone',
    'capabilityVersion', 'dependencies', 'rules', 'limitations']
  exact(value, keys, requestId)
  if (!['STOCK_DATE', 'STOCK_SNAPSHOT', 'NON_STOCK'].includes(value.scopeKind) ||
      (value.symbolField !== null && !validIdentifier(value.symbolField)) ||
      (value.dateField !== null && !validIdentifier(value.dateField)) || !nonBlank(value.dateLabel) ||
      !nonBlank(value.marketZone) || !nonBlank(value.capabilityVersion)) invalid(requestId)
  return Object.freeze({
    datasetKey: datasetKey(value.datasetKey, requestId), scopeKind: value.scopeKind,
    symbolField: value.symbolField, dateField: value.dateField, dateLabel: value.dateLabel,
    marketZone: value.marketZone, capabilityVersion: value.capabilityVersion,
    dependencies: array(value.dependencies, dependency, requestId),
    rules: array(value.rules, ruleDescriptor, requestId),
    limitations: array(value.limitations, stringValue, requestId),
  })
}

const STATISTIC_KEYS = ['actualCount', 'expectedCount', 'matchedCount', 'missingCount',
  'suspectedMissingCount', 'extraCount', 'requiredFieldIssueCount', 'coverageRate']

function statistics(value, requestId) {
  exact(value, STATISTIC_KEYS, requestId)
  if (value.coverageRate !== null && (typeof value.coverageRate !== 'string' || !RATE.test(value.coverageRate))) invalid(requestId)
  const result = {}
  for (const key of STATISTIC_KEYS.slice(0, -1)) result[key] = count(value[key], requestId, true)
  result.coverageRate = value.coverageRate
  return Object.freeze(result)
}

function evidence(value, requestId) {
  exact(value, ['source', 'ruleVersion', 'range', 'readAt', 'summary'], requestId)
  if (!nonBlank(value.source) || !nonBlank(value.ruleVersion) || !validInstant(value.readAt) || !nonBlank(value.summary)) invalid(requestId)
  return Object.freeze({ source: value.source, ruleVersion: value.ruleVersion,
    range: dateRange(value.range, requestId), readAt: value.readAt, summary: value.summary })
}

function ruleResult(value, requestId) {
  exact(value, ['descriptor', 'status', 'reasonCode', 'message', 'statistics', 'evidence'], requestId)
  if (!STATUSES.has(value.status) || !nonBlank(value.reasonCode) || !nonBlank(value.message)) invalid(requestId)
  return Object.freeze({
    descriptor: ruleDescriptor(value.descriptor, requestId), status: value.status,
    reasonCode: value.reasonCode, message: value.message,
    statistics: statistics(value.statistics, requestId), evidence: array(value.evidence, evidence, requestId),
  })
}

function report(value, requestId) {
  const keys = ['scope', 'descriptor', 'definitionHash', 'publishedRange', 'unitStatus', 'coverageStatus',
    'keyStatus', 'fieldStatus', 'overallStatus', 'statistics', 'ruleResults', 'evidence', 'finishedAt',
    'incomplete', 'issuesComplete', 'reasonCode', 'message']
  exact(value, keys, requestId)
  if (typeof value.definitionHash !== 'string' || !HASH.test(value.definitionHash) || !UNIT_STATUSES.has(value.unitStatus) ||
      ![value.coverageStatus, value.keyStatus, value.fieldStatus, value.overallStatus].every((item) => STATUSES.has(item)) ||
      typeof value.incomplete !== 'boolean' || typeof value.issuesComplete !== 'boolean' ||
      !nonBlank(value.reasonCode) || !nonBlank(value.message)) invalid(requestId)
  return Object.freeze({
    scope: scope(value.scope, requestId), descriptor: value.descriptor === null ? null : descriptor(value.descriptor, requestId),
    definitionHash: value.definitionHash, publishedRange: value.publishedRange === null ? null : dateRange(value.publishedRange, requestId),
    unitStatus: value.unitStatus, coverageStatus: value.coverageStatus, keyStatus: value.keyStatus,
    fieldStatus: value.fieldStatus, overallStatus: value.overallStatus, statistics: statistics(value.statistics, requestId),
    ruleResults: array(value.ruleResults, ruleResult, requestId), evidence: array(value.evidence, evidence, requestId),
    finishedAt: nullableInstant(value.finishedAt, requestId), incomplete: value.incomplete,
    issuesComplete: value.issuesComplete, reasonCode: value.reasonCode, message: value.message,
  })
}

function requestSnapshot(value, requestId) {
  const keys = ['submissionId', 'pluginId', 'capabilityHash', 'symbols', 'startDate', 'endDate']
  if (Object.hasOwn(value ?? {}, 'apiNames')) keys.push('apiNames')
  exact(value, keys, requestId)
  if (!validUuid(value.submissionId) || !validIdentifier(value.pluginId) ||
      typeof value.capabilityHash !== 'string' || !HASH.test(value.capabilityHash) ||
      !validDate(value.startDate) || !validDate(value.endDate) || value.startDate > value.endDate) invalid(requestId)
  const result = {
    submissionId: value.submissionId, pluginId: value.pluginId, capabilityHash: value.capabilityHash,
    symbols: array(value.symbols, stringValue, requestId, { min: 1 }),
    startDate: value.startDate, endDate: value.endDate,
  }
  if (Object.hasOwn(value, 'apiNames')) result.apiNames = array(value.apiNames, identifierValue, requestId, { min: 1 })
  return Object.freeze(result)
}

const SUMMARY_KEYS = ['checkId', 'submissionId', 'pluginId', 'originalRequest', 'scope', 'capabilityHash',
  'status', 'plannedUnits', 'createdAt', 'updatedAt', 'startedAt', 'finishedAt', 'errorCode', 'errorMessage']

function summary(value, requestId, extra = []) {
  exact(value, [...SUMMARY_KEYS, ...extra], requestId)
  if (!validUuid(value.checkId) || !validUuid(value.submissionId) || !validIdentifier(value.pluginId) ||
      typeof value.capabilityHash !== 'string' || !HASH.test(value.capabilityHash) || !TASK_STATUSES.has(value.status) ||
      !validInstant(value.createdAt) || !validInstant(value.updatedAt)) invalid(requestId)
  return {
    checkId: value.checkId, submissionId: value.submissionId, pluginId: value.pluginId,
    originalRequest: requestSnapshot(value.originalRequest, requestId), scope: taskScope(value.scope, requestId),
    capabilityHash: value.capabilityHash, status: value.status, plannedUnits: positiveInt(value.plannedUnits, requestId),
    createdAt: value.createdAt, updatedAt: value.updatedAt,
    startedAt: nullableInstant(value.startedAt, requestId), finishedAt: nullableInstant(value.finishedAt, requestId),
    errorCode: nullableString(value.errorCode, requestId), errorMessage: nullableString(value.errorMessage, requestId),
  }
}

function limits(value, requestId) {
  const keys = ['maxSymbols', 'maxRangeDays', 'maxUnits', 'queueCapacity', 'workers', 'scanBatchSize',
    'maxIssuesPerUnit', 'maxScannedRowsPerUnit', 'unitTimeoutSeconds', 'taskTimeoutSeconds']
  exact(value, keys, requestId)
  const result = {}
  for (const key of keys.slice(0, 7)) result[key] = positiveInt(value[key], requestId)
  if (result.workers !== 1) invalid(requestId)
  for (const key of keys.slice(7)) result[key] = positiveCount(value[key], requestId)
  return Object.freeze(result)
}

function capabilityApi(value, requestId) {
  exact(value, ['apiName', 'displayName', 'descriptor', 'downloadAvailability'], requestId)
  if (!validIdentifier(value.apiName) || !nonBlank(value.displayName)) invalid(requestId)
  return Object.freeze({
    apiName: value.apiName, displayName: value.displayName,
    descriptor: value.descriptor === null ? null : descriptor(value.descriptor, requestId),
    downloadAvailability: parseDownloadCapabilities(value.downloadAvailability, requestId),
  })
}

export function parseIntegrityCapabilities(value, requestId) {
  exact(value, ['pluginId', 'localCheckAvailable', 'unavailableReason', 'capabilityHash', 'limits', 'apis'], requestId)
  if (!validIdentifier(value.pluginId) || typeof value.localCheckAvailable !== 'boolean' ||
      (value.unavailableReason !== null && !nonBlank(value.unavailableReason)) ||
      (value.capabilityHash !== null && (typeof value.capabilityHash !== 'string' || !HASH.test(value.capabilityHash)))) invalid(requestId)
  return Object.freeze({
    pluginId: value.pluginId, localCheckAvailable: value.localCheckAvailable,
    unavailableReason: value.unavailableReason, capabilityHash: value.capabilityHash,
    limits: limits(value.limits, requestId), apis: array(value.apis, capabilityApi, requestId),
  })
}

export function parseIntegrityReceipt(value, requestId) {
  const keys = ['requestId', 'checkId', 'submissionId', 'pluginId', 'status', 'plannedUnits', 'createdAt']
  exact(value, keys, requestId)
  if (!validUuid(value.requestId) || value.requestId !== requestId || !validUuid(value.checkId) ||
      !validUuid(value.submissionId) || !validIdentifier(value.pluginId) ||
      !TASK_STATUSES.has(value.status) || !validInstant(value.createdAt)) invalid(requestId)
  return Object.freeze({ requestId: value.requestId, checkId: value.checkId, submissionId: value.submissionId,
    pluginId: value.pluginId, status: value.status, plannedUnits: positiveInt(value.plannedUnits, requestId),
    createdAt: value.createdAt })
}

export function parseIntegrityTaskSummary(value, requestId) {
  return Object.freeze(summary(value, requestId))
}

export function parseIntegrityDetail(value, requestId) {
  const parsed = summary(value, requestId, ['overallStatus', 'completedUnits', 'errorUnits', 'notRunUnits', 'statusCounts'])
  if (!STATUSES.has(value.overallStatus)) invalid(requestId)
  const keys = ['PASS', 'FAIL', 'WARN', 'UNKNOWN', 'NOT_APPLICABLE']
  exact(value.statusCounts, keys, requestId)
  return Object.freeze({ ...parsed, overallStatus: value.overallStatus,
    completedUnits: count(value.completedUnits, requestId), errorUnits: count(value.errorUnits, requestId),
    notRunUnits: count(value.notRunUnits, requestId),
    statusCounts: Object.freeze(Object.fromEntries(keys.map((key) => [key, count(value.statusCounts[key], requestId)]))),
  })
}

export function parseIntegrityResult(value, requestId) {
  exact(value, ['resultId', 'checkId', 'report'], requestId)
  if (!validUuid(value.resultId) || !validUuid(value.checkId)) invalid(requestId)
  return Object.freeze({ resultId: value.resultId, checkId: value.checkId, report: report(value.report, requestId) })
}

function scalarMap(value, requestId) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) invalid(requestId)
  const result = {}
  for (const [key, item] of Object.entries(value)) {
    if (!validIdentifier(key) || (item !== null && typeof item !== 'string' && typeof item !== 'boolean' &&
        typeof item !== 'bigint' && !(typeof item === 'number' && Number.isSafeInteger(item)))) invalid(requestId)
    result[key] = item
  }
  return Object.freeze(result)
}

function dateMap(value, requestId) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) invalid(requestId)
  const result = {}
  for (const [key, item] of Object.entries(value)) {
    if (!validIdentifier(key) || !validDate(item)) invalid(requestId)
    result[key] = item
  }
  return Object.freeze(result)
}

function issueBody(value, requestId) {
  const keys = ['type', 'status', 'symbol', 'apiName', 'dateField', 'date', 'businessKey', 'field',
    'relatedDates', 'reasonCode', 'message', 'evidence', 'incomplete']
  exact(value, keys, requestId)
  if (!ISSUE_TYPES.has(value.type) || !STATUSES.has(value.status) || (value.symbol !== null && !nonBlank(value.symbol)) ||
      !validIdentifier(value.apiName) || (value.dateField !== null && !validIdentifier(value.dateField)) ||
      (value.date !== null && !validDate(value.date)) || (value.field !== null && !validIdentifier(value.field)) ||
      !nonBlank(value.reasonCode) || !nonBlank(value.message) || typeof value.incomplete !== 'boolean') invalid(requestId)
  return Object.freeze({ type: value.type, status: value.status, symbol: value.symbol, apiName: value.apiName,
    dateField: value.dateField, date: value.date, businessKey: scalarMap(value.businessKey, requestId), field: value.field,
    relatedDates: dateMap(value.relatedDates, requestId), reasonCode: value.reasonCode, message: value.message,
    evidence: array(value.evidence, evidence, requestId), incomplete: value.incomplete })
}

export function parseIntegrityIssue(value, requestId) {
  exact(value, ['issueId', 'resultId', 'ruleId', 'ruleVersion', 'issue'], requestId)
  if (!validUuid(value.resultId) || !nonBlank(value.ruleId) || !nonBlank(value.ruleVersion)) invalid(requestId)
  return Object.freeze({ issueId: count(value.issueId, requestId), resultId: value.resultId,
    ruleId: value.ruleId, ruleVersion: value.ruleVersion, issue: issueBody(value.issue, requestId) })
}

function parsePage(value, requestId, parser, id) {
  exact(value, ['page', 'pageSize', 'total', 'items'], requestId)
  const page = positiveInt(value.page, requestId)
  if (!Number.isInteger(value.pageSize) || value.pageSize < 1 || value.pageSize > 100 || !Array.isArray(value.items)) invalid(requestId)
  const items = value.items.map((item) => parser(item, requestId))
  const total = count(value.total, requestId)
  if (items.length > value.pageSize || total < BigInt(items.length) ||
      new Set(items.map((item) => String(item[id]).toLowerCase())).size !== items.length) invalid(requestId)
  return Object.freeze({ page, pageSize: value.pageSize, total, items: Object.freeze(items) })
}

export const parseIntegrityTaskPage = (value, requestId) => parsePage(value, requestId, parseIntegrityTaskSummary, 'checkId')
export const parseIntegrityResultPage = (value, requestId) => parsePage(value, requestId, parseIntegrityResult, 'resultId')
export const parseIntegrityIssuePage = (value, requestId) => parsePage(value, requestId, parseIntegrityIssue, 'issueId')

export function validateIntegrityCheckId(value) {
  if (!validUuid(value)) throw new TypeError('Integrity checkId is invalid')
  return value.toLowerCase()
}

const CRITERIA_KEYS = Object.freeze({
  tasks: new Set(['page', 'pageSize', 'pluginId', 'status', 'submissionId']),
  results: new Set(['page', 'pageSize', 'symbol', 'apiName', 'overallStatus']),
  issues: new Set(['page', 'pageSize', 'resultId', 'symbol', 'apiName', 'type', 'status', 'dateFrom', 'dateTo']),
})

function criteriaError() {
  throw new TypeError('Integrity criteria are invalid')
}

export function validateIntegrityCriteria(kind, criteria) {
  const allowed = CRITERIA_KEYS[kind]
  if (!allowed || criteria === null || typeof criteria !== 'object' || Array.isArray(criteria) ||
      Object.keys(criteria).some((key) => !allowed.has(key) || criteria[key] === null)) criteriaError()
  const page = criteria.page === undefined ? 1 : criteria.page
  const pageSize = criteria.pageSize === undefined ? 20 : criteria.pageSize
  if (!Number.isInteger(page) || page < 1 || page > 2147483647 ||
      !Number.isInteger(pageSize) || pageSize < 1 || pageSize > 100) criteriaError()
  const result = { page, pageSize }
  for (const [key, value] of Object.entries(criteria)) {
    if (value === undefined || key === 'page' || key === 'pageSize') continue
    if ((key === 'pluginId' || key === 'apiName') && !validIdentifier(value)) criteriaError()
    if (key === 'submissionId' || key === 'resultId') {
      if (!validUuid(value)) criteriaError()
      result[key] = value.toLowerCase()
      continue
    }
    if (key === 'status' && !(kind === 'tasks' ? TASK_STATUSES : STATUSES).has(value)) criteriaError()
    if (key === 'overallStatus' && !STATUSES.has(value)) criteriaError()
    if (key === 'type' && !ISSUE_TYPES.has(value)) criteriaError()
    if ((key === 'dateFrom' || key === 'dateTo') && !validDate(value)) criteriaError()
    if (key === 'symbol' && (!nonBlank(value) || [...value].length > 255)) criteriaError()
    result[key] ??= value
  }
  if (result.dateFrom !== undefined && result.dateTo !== undefined && result.dateFrom > result.dateTo) criteriaError()
  if (kind === 'tasks' && result.submissionId !== undefined &&
      (result.page !== 1 || result.pageSize !== 20 || result.status !== undefined)) criteriaError()
  return Object.freeze(result)
}

export function createIntegritySubmission(selection, submissionId = globalThis.crypto.randomUUID()) {
  if (selection === null || typeof selection !== 'object' || Array.isArray(selection)) throw new TypeError('Integrity submission is invalid')
  const hasId = Object.hasOwn(selection, 'submissionId')
  const allowed = new Set(['pluginId', 'capabilityHash', 'symbols', 'startDate', 'endDate', 'apiNames'])
  if (hasId) allowed.add('submissionId')
  if (Object.keys(selection).some((key) => !allowed.has(key)) || !validUuid(submissionId) ||
      (hasId && (arguments.length < 2 || selection.submissionId !== submissionId)) || !validIdentifier(selection.pluginId) ||
      typeof selection.capabilityHash !== 'string' || !HASH.test(selection.capabilityHash) ||
      !validDate(selection.startDate) || !validDate(selection.endDate) || selection.startDate > selection.endDate ||
      !Array.isArray(selection.symbols) || selection.symbols.length === 0 || !selection.symbols.every(nonBlank) ||
      (selection.apiNames !== undefined && (!Array.isArray(selection.apiNames) || selection.apiNames.length === 0 ||
        !selection.apiNames.every(validIdentifier)))) throw new TypeError('Integrity submission is invalid')
  const result = { submissionId, pluginId: selection.pluginId,
    capabilityHash: selection.capabilityHash, symbols: Object.freeze([...selection.symbols]),
    startDate: selection.startDate, endDate: selection.endDate }
  if (selection.apiNames !== undefined) result.apiNames = Object.freeze([...selection.apiNames])
  return Object.freeze(result)
}
