import { createHash } from 'node:crypto'

const SAFE_ERROR = 'Invalid Tushare range evidence'
const RESPONSE_ONLY_DECISION = 'docs/issues/proposals/ISSUE-025-extraction-contracts.md#决策记录'
const RESPONSE_ONLY_APIS = new Set([
  'adj_factor', 'suspend_d', 'income', 'balancesheet', 'cashflow', 'fina_audit', 'express',
  'repurchase', 'stk_managers', 'top10_holders', 'top10_floatholders',
])

const matrix = [
  ['stock_basic', false, null, null, null, null, 25],
  ['stock_company', false, null, null, null, null, 112],
  ['income', true, 'STOCK', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 33],
  ['balancesheet', true, 'STOCK', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 36],
  ['cashflow', true, 'STOCK', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 44],
  ['fina_indicator', true, 'STOCK', 'NATIVE_RANGE', 'REPORT_PERIOD', 'end_date', 79],
  ['fina_audit', true, 'STOCK', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 80],
  ['fina_mainbz', true, 'STOCK', 'NATIVE_RANGE', 'REPORT_PERIOD', 'end_date', 81],
  ['stk_rewards', false, null, null, null, null, 194],
  ['stk_holdernumber', true, 'STOCK', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 166],
  ['trade_cal', true, 'EXCHANGE', 'NATIVE_RANGE', 'CALENDAR_DATE', 'cal_date', 26],
  ['margin', true, 'EXCHANGE_ID', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 58],
  ['daily', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 27],
  ['weekly', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 144],
  ['monthly', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 145],
  ['adj_factor', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 28],
  ['suspend_d', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 214],
  ['daily_basic', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 32],
  ['moneyflow', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 170],
  ['stk_limit', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 183],
  ['top_list', true, 'STOCK', 'TRADING_DAYS', 'TRADE_DATE', 'trade_date', 106],
  ['margin_detail', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 59],
  ['block_trade', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 161],
  ['slb_len', true, 'DATES', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 331],
  ['slb_sec', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 332],
  ['slb_sec_detail', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 333],
  ['forecast', true, 'STOCK', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 45],
  ['express', true, 'STOCK', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 46],
  ['dividend', true, 'STOCK', 'CALENDAR_DAYS', 'ANNOUNCEMENT_DATE', 'ann_date', 103],
  ['disclosure_date', true, 'STOCK', 'CALENDAR_DAYS', 'ANNOUNCEMENT_DATE', 'ann_date', 162],
  ['repurchase', true, 'DATES', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 124],
  ['stk_holdertrade', true, 'STOCK', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 175],
  ['top10_holders', true, 'STOCK', 'NATIVE_RANGE', 'REPORT_PERIOD', 'end_date', 61],
  ['top10_floatholders', true, 'STOCK', 'NATIVE_RANGE', 'REPORT_PERIOD', 'end_date', 62],
  ['new_share', true, 'DATES', 'NATIVE_RANGE', 'ISSUE_DATE', 'ipo_date', 123],
  ['stk_managers', true, 'STOCK', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 193],
  ['pledge_stat', false, null, null, null, null, 110],
  ['pledge_detail', true, 'STOCK', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 111],
  ['index_classify', false, null, null, null, null, 181],
  ['index_member_all', false, null, null, null, null, 335],
]

const singles = {
  stock_basic: { ts_code: '000001.SZ', list_status: 'L' },
  stock_company: { ts_code: '000001.SZ', exchange: 'SZSE' },
  income: { ts_code: '000001.SZ', ann_date: '20260807' },
  balancesheet: { ts_code: '000001.SZ', ann_date: '20260807' },
  cashflow: { ts_code: '000001.SZ', ann_date: '20260807' },
  fina_indicator: { ts_code: '000001.SZ', ann_date: '20260807' },
  fina_audit: { ts_code: '000001.SZ', ann_date: '20260807' },
  fina_mainbz: { ts_code: '000001.SZ' },
  stk_rewards: { ts_code: '000001.SZ' },
  stk_holdernumber: { ts_code: '000001.SZ' },
  trade_cal: { exchange: 'SSE', start_date: '20260807', end_date: '20260807' },
  margin: { exchange_id: 'SSE', trade_date: '20260807' },
  daily: { ts_code: '000001.SZ', trade_date: '20260807' },
  weekly: { ts_code: '000001.SZ', trade_date: '20260807' },
  monthly: { ts_code: '000001.SZ', trade_date: '20260807' },
  adj_factor: { ts_code: '000001.SZ', trade_date: '20260807' },
  suspend_d: { ts_code: '000001.SZ', trade_date: '20260807' },
  daily_basic: { ts_code: '000001.SZ', trade_date: '20260807' },
  moneyflow: { ts_code: '000001.SZ', trade_date: '20260807' },
  stk_limit: { ts_code: '000001.SZ', trade_date: '20260807' },
  top_list: { ts_code: '000001.SZ', trade_date: '20260807' },
  margin_detail: { ts_code: '000001.SZ', trade_date: '20260807' },
  block_trade: { ts_code: '000001.SZ', trade_date: '20260807' },
  slb_len: { trade_date: '20260807' },
  slb_sec: { ts_code: '000001.SZ', trade_date: '20260807' },
  slb_sec_detail: { ts_code: '000001.SZ', trade_date: '20260807' },
  forecast: { ts_code: '000001.SZ', ann_date: '20260807' },
  express: { ts_code: '000001.SZ', ann_date: '20260807' },
  dividend: { ts_code: '000001.SZ', ann_date: '20260807' },
  disclosure_date: { ts_code: '000001.SZ', ann_date: '20260807' },
  repurchase: { ann_date: '20260807' },
  stk_holdertrade: { ts_code: '000001.SZ', ann_date: '20260807' },
  top10_holders: { ts_code: '000001.SZ', ann_date: '20260807' },
  top10_floatholders: { ts_code: '000001.SZ', ann_date: '20260807' },
  new_share: { start_date: '20260807', end_date: '20260807' },
  stk_managers: { ts_code: '000001.SZ' },
  pledge_stat: { ts_code: '000001.SZ' },
  pledge_detail: { ts_code: '000001.SZ' },
  index_classify: {},
  index_member_all: { ts_code: '000001.SZ' },
}
const byApi = new Map(matrix.map((row) => [row[0], row]))
const keys = {
  index: ['schemaVersion', 'inputHashes', 'interfaces', 'runs'],
  hashes: ['manifestSha256', 'requestExamplesSha256'],
  interface: [
    'apiName', 'rangeTarget', 'shape', 'planningMode', 'dateAxis', 'outputDateColumn',
    'officialUrl', 'completeness', 'sourceStatus', 'taskStatus', 'disposition', 'policyVersion',
    'cases', 'unresolved', 'decisionRef',
  ],
  completeness: ['kind', 'rowLimit', 'evidenceRefs'],
  run: [
    'runId', 'startedAt', 'finishedAt', 'sourceDiffSha256', 'productionJarSha256',
    'acceptanceJarSha256', 'manifestSha256', 'requestExamplesSha256', 'commands', 'exitCode',
    'cleanup', 'cases',
  ],
  cleanup: ['status', 'completedAt', 'evidencePath'],
  case: [
    'caseId', 'apiName', 'phase', 'mode', 'params', 'dateAxis', 'expectedCoverage',
    'expectedCoverageSource', 'status', 'errorCode', 'taskId', 'submissionId', 'requestCount',
    'batchNodes', 'leafCount', 'succeededLeafCount', 'failedLeafCount', 'emptyLeafCount',
    'sourceRowCount', 'insertedRows', 'updatedRows', 'sqlBeforeKeyCount', 'sqlAfterKeyCount',
    'ownershipSummary', 'businessKeyDigest', 'reviewMethod', 'evidencePaths',
  ],
  batch: [
    'batchId', 'parentBatchId', 'status', 'start', 'end', 'sourceRowCount', 'insertedRows',
    'updatedRows', 'errorCode',
  ],
  plan: ['runId', 'cases'],
  planCase: ['caseId', 'apiName', 'mode', 'params', 'dateAxis', 'start', 'end', 'evidenceRefs'],
}
function fail() {
  throw new Error(SAFE_ERROR)
}

export function responseOnlyExpectedCoverageSource(source) {
  if (!text(source?.runId) || !text(source?.caseId)) fail()
  return `${RESPONSE_ONLY_DECISION} docs/verification/ISSUE-018-range-acceptance.json#${source.runId}/${source.caseId}`
}

function object(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function exact(value, expected) {
  return object(value)
    && Object.keys(value).length === expected.length
    && expected.every((key) => Object.hasOwn(value, key))
}

function text(value) {
  return typeof value === 'string' && value.length > 0
}

function sha(value) {
  return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value)
}

function utc(value) {
  return typeof value === 'string' && value.endsWith('Z') && !Number.isNaN(Date.parse(value))
}

function integer(value) {
  return Number.isSafeInteger(value) && value >= 0
}

export function evidenceInteger(value, maximum = Number.MAX_SAFE_INTEGER) {
  const converted = typeof value === 'bigint' && value >= 0n && value <= BigInt(maximum)
    ? Number(value) : value
  if (!integer(converted) || converted > maximum) fail()
  return converted
}

function nullableInteger(value) {
  return value === null || integer(value)
}

function refs(value) {
  return Array.isArray(value) && value.every(text)
}

function same(left, right) {
  return Object.keys(left).length === Object.keys(right).length
    && Object.keys(left).every((key) => left[key] === right[key])
}

function date(value) {
  if (typeof value !== 'string' || !/^\d{8}$/.test(value)) return false
  const year = Number(value.slice(0, 4))
  const month = Number(value.slice(4, 6))
  const day = Number(value.slice(6))
  return year >= 2000 && year <= 2100 && month >= 1 && month <= 12
    && day >= 1 && day <= new Date(Date.UTC(year, month, 0)).getUTCDate()
}

function rangeParams(row, params, start, end) {
  const shape = row[2]
  const expected = shape === 'STOCK' ? ['ts_code', 'start_date', 'end_date']
    : shape === 'DATES' ? ['start_date', 'end_date']
      : shape === 'EXCHANGE' ? ['exchange', 'start_date', 'end_date']
        : ['exchange_id', 'start_date', 'end_date']
  if (!row[1] || !exact(params, expected) || !date(start) || !date(end)
    || start > end || params.start_date !== start || params.end_date !== end) return null
  if (shape === 'STOCK' && !/^[0-9]{6}\.(SZ|SH|BJ)$/.test(params.ts_code)) return null
  if (shape === 'EXCHANGE' && !['SSE', 'SZSE', 'BSE'].includes(params.exchange)) return null
  if (shape === 'EXCHANGE_ID' && !['SSE', 'SZSE', 'BSE'].includes(params.exchange_id)) return null
  return row[4]
}

function validatePlanCase(value) {
  if (!exact(value, keys.planCase) || !text(value.caseId) || !byApi.has(value.apiName)
    || !['SINGLE', 'RANGE'].includes(value.mode) || !object(value.params)
    || !refs(value.evidenceRefs)) fail()
  const row = byApi.get(value.apiName)
  if (value.mode === 'SINGLE') {
    if (value.dateAxis !== null || value.start !== null || value.end !== null
      || !singleParams(value.apiName, value.params)) fail()
  } else if (!text(value.dateAxis) || value.dateAxis !== rangeParams(row, value.params, value.start, value.end)
    || value.evidenceRefs.length === 0) fail()
}

export function validateCasePlan(plan) {
  assertSafeStrings(plan)
  if (!exact(plan, keys.plan) || !text(plan.runId) || !Array.isArray(plan.cases)
    || plan.cases.length === 0) fail()
  const ids = new Set()
  for (const value of plan.cases) {
    validatePlanCase(value)
    if (ids.has(value.caseId)) fail()
    ids.add(value.caseId)
  }
  return plan
}
const errorCodes = new Set([
  'PARAM_REQUIRED', 'PARAM_INVALID', 'PLUGIN_DISABLED', 'DATASET_MISCONFIGURED',
  'SOURCE_AUTH_FAILED', 'SOURCE_PERMISSION_DENIED', 'SOURCE_RATE_LIMITED',
  'SOURCE_UNAVAILABLE', 'SOURCE_NETWORK_ERROR', 'SOURCE_TIMEOUT', 'SOURCE_PAYLOAD_INVALID',
  'ADAPTER_FIELD_MISSING', 'ADAPTER_TYPE_INVALID', 'PERSISTENCE_FAILED', 'QUERY_FAILED',
  'INTERNAL_ERROR', 'TASK_NOT_FOUND', 'SUBMISSION_CONFLICT', 'TASK_STATE_CONFLICT',
  'TASK_DEFINITION_CHANGED', 'BATCH_DOWNLOAD_UNAVAILABLE', 'TASK_QUEUE_FULL',
  'BATCH_COMPLETENESS_UNCONFIRMED', 'SOURCE_RANGE_MISMATCH', 'TASK_LIMIT_EXCEEDED',
  'EXECUTION_INTERRUPTED',
])
const caseCounts = [
  'requestCount', 'leafCount', 'succeededLeafCount', 'failedLeafCount', 'emptyLeafCount',
  'sourceRowCount', 'insertedRows', 'updatedRows', 'sqlBeforeKeyCount', 'sqlAfterKeyCount',
]
const sqlFields = ['insertedRows', 'updatedRows', 'sqlBeforeKeyCount', 'sqlAfterKeyCount',
  'ownershipSummary', 'businessKeyDigest']
const rowCounts = ['sourceRowCount', 'insertedRows', 'updatedRows']
const succeeded = (node) => ['SUCCESS', 'SUCCEEDED'].includes(node.status)
const validError = (value) => value === null || errorCodes.has(value)

// Scan persisted values, including strings in approved fields. Never read account secrets.
function assertSafeStrings(value) {
  if (typeof value === 'string') {
    const forbidden = /jdbc:|\b(?:token|password|passwd|secret|api[_-]?key|authorization|username|jdbcUrl)["']?\s*[:=]|\bBearer\s+\S+|\braw[ _-]*(?:response|error|cause|body|payload)\b|\bcaused by\s*:|\bat\s+[\w.$]+\([^)]*:\d+\)|https?:\/\/[^/\s]+@|\bsk-(?:proj-|svcacct-)?[a-z0-9_-]{16,}|-----BEGIN .*PRIVATE KEY-----|[\[{]\s*["'{\[]/i
    const credentials = /(?:^|[^a-z0-9])(?:[a-z0-9]+_)*(?:token|password|passwd|secret|api[_-]?key)["']?\s*[:=]|--(?:token|password|secret|api-key)\s+\S+|\bgh[pousr]_[a-z0-9]{20,}|\bgithub_pat_[a-z0-9_]{20,}|\beyJ[a-z0-9_-]+\.[a-z0-9_-]+\.[a-z0-9_-]+/i
    if (forbidden.test(value) || credentials.test(value)) fail()
  } else if (Array.isArray(value)) {
    value.forEach(assertSafeStrings)
  } else if (object(value)) {
    Object.values(value).forEach(assertSafeStrings)
  }
}

function singleParams(apiName, params) {
  if (!exact(params, Object.keys(singles[apiName]))) return false
  for (const [key, value] of Object.entries(params)) {
    if (key === 'ts_code' && !/^[0-9]{6}\.(SZ|SH|BJ)$/.test(value)) return false
    if (key.includes('date') && !date(value)) return false
    if (['exchange', 'exchange_id'].includes(key) && !['SSE', 'SZSE', 'BSE'].includes(value)) return false
    if (key === 'list_status' && !['L', 'P', 'D'].includes(value)) return false
  }
  if (params.start_date && params.start_date > params.end_date) return false
  if (apiName === 'stock_company'
    && params.exchange !== { SH: 'SSE', SZ: 'SZSE', BJ: 'BSE' }[params.ts_code.slice(-2)]) return false
  return true
}

function validateBatch(node, evidence) {
  if (!exact(node, keys.batch)) fail()
  const validRange = evidence.mode === 'SINGLE'
    ? (node.start === null && node.end === null)
      || (date(node.start) && date(node.end) && node.start <= node.end)
    : date(node.start) && date(node.end) && node.start <= node.end
      && node.start >= evidence.params.start_date && node.end <= evidence.params.end_date
  if (!exact(node, keys.batch) || !text(node.batchId)
    || !(node.parentBatchId === null || text(node.parentBatchId)) || !validRange
    || !['SUCCESS', 'SUCCEEDED', 'FAILED', 'NOT_RUN', 'PENDING', 'RUNNING', 'SPLIT'].includes(node.status)
    || !rowCounts.every((key) => nullableInteger(node[key])) || !validError(node.errorCode)
    || (evidence.phase === 'SOURCE' && (node.insertedRows !== null || node.updatedRows !== null))) fail()
  if (node.status === 'FAILED' ? node.errorCode === null : node.errorCode !== null) fail()
  if (succeeded(node) && (node.sourceRowCount === null
    || (evidence.phase === 'TASK' && (node.insertedRows === null || node.updatedRows === null)))) fail()
  if (node.status === 'NOT_RUN' && rowCounts.some((key) => node[key] !== null)) fail()
  if (['SPLIT', 'PENDING', 'RUNNING'].includes(node.status)
    && rowCounts.some((key) => node[key] !== null && node[key] !== 0)) fail()
}

function nextDate(value) {
  const time = new Date(Date.UTC(Number(value.slice(0, 4)), Number(value.slice(4, 6)) - 1,
    Number(value.slice(6)) + 1))
  return time.toISOString().slice(0, 10).replaceAll('-', '')
}

function validateRootCoverage(evidence) {
  if (evidence.status !== 'PASS' || evidence.mode !== 'RANGE') return
  const planningMode = byApi.get(evidence.apiName)[3]
  // Trading dates require the cited calendar; do not infer an open-day sequence.
  if (planningMode === 'TRADING_DAYS') return
  const roots = evidence.batchNodes.filter((node) => node.parentBatchId === null)
    .sort((a, b) => a.start.localeCompare(b.start))
  let expectedStart = evidence.params.start_date
  for (const root of roots) {
    if (root.start !== expectedStart
      || (planningMode === 'CALENDAR_DAYS' && root.start !== root.end)) fail()
    expectedStart = nextDate(root.end)
  }
  if (roots.length === 0 || roots.at(-1).end !== evidence.params.end_date) fail()
}

function validateTree(evidence) {
  const nodes = new Map()
  for (const node of evidence.batchNodes) {
    validateBatch(node, evidence)
    if (nodes.has(node.batchId)) fail()
    nodes.set(node.batchId, node)
  }
  const children = new Map()
  for (const node of nodes.values()) {
    if (node.parentBatchId === null) continue
    const parent = nodes.get(node.parentBatchId)
    if (!parent || parent.status !== 'SPLIT' || node.batchId === node.parentBatchId) fail()
    const siblings = children.get(parent.batchId) ?? []
    siblings.push(node)
    children.set(parent.batchId, siblings)
    const ancestors = new Set([node.batchId])
    let ancestor = parent
    while (ancestor) {
      if (ancestors.has(ancestor.batchId)) fail()
      ancestors.add(ancestor.batchId)
      ancestor = nodes.get(ancestor.parentBatchId)
    }
  }
  for (const node of nodes.values()) {
    if (node.status !== 'SPLIT') continue
    const halves = children.get(node.batchId)
    if (!halves || halves.length !== 2 || evidence.mode !== 'RANGE') fail()
    halves.sort((a, b) => a.start.localeCompare(b.start))
    if (halves[0].start !== node.start || halves[1].end !== node.end
      || nextDate(halves[0].end) !== halves[1].start) fail()
  }
  const leaves = [...nodes.values()].filter((node) => node.status !== 'SPLIT')
  const successful = leaves.filter(succeeded)
  const counts = {
    leafCount: leaves.length,
    succeededLeafCount: successful.length,
    failedLeafCount: leaves.filter((node) => node.status === 'FAILED').length,
    emptyLeafCount: successful.filter((node) => node.sourceRowCount === 0).length,
  }
  for (const [key, count] of Object.entries(counts)) {
    if (evidence[key] !== null && evidence[key] !== count) fail()
  }
  for (const key of rowCounts) {
    if (evidence[key] !== null
      && evidence[key] !== successful.reduce((total, node) => total + (node[key] ?? 0), 0)) fail()
  }
  const executed = [...nodes.values()].filter((node) =>
    !['NOT_RUN', 'PENDING'].includes(node.status)).length
  if (evidence.status === 'PASS' && evidence.requestCount < executed) fail()
  validateRootCoverage(evidence)
  return leaves
}

export function validateEvidenceCase(value) {
  const validParams = byApi.has(value?.apiName) && (value.mode === 'RANGE'
    ? text(value.dateAxis) && value.dateAxis === rangeParams(byApi.get(value.apiName), value.params,
      value.params?.start_date, value.params?.end_date)
    : value.mode === 'SINGLE' && value.dateAxis === null && singleParams(value.apiName, value.params))
  if (!exact(value, keys.case) || !text(value.caseId)
    || !['SOURCE', 'TASK'].includes(value.phase) || !validParams
    || !text(value.expectedCoverage) || !text(value.expectedCoverageSource)
    || !['PASS', 'FAILED', 'NOT_RUN', 'EVIDENCE_MISSING'].includes(value.status)
    || !validError(value.errorCode) || !caseCounts.every((key) => nullableInteger(value[key]))
    || !Array.isArray(value.batchNodes)
    || !['taskId', 'submissionId', 'ownershipSummary', 'reviewMethod'].every((key) =>
      value[key] === null || text(value[key]))
    || !(value.businessKeyDigest === null || sha(value.businessKeyDigest))
    || !refs(value.evidencePaths)) fail()
  if (value.phase === 'SOURCE' && ['taskId', 'submissionId', ...sqlFields].some((key) =>
    value[key] !== null)) fail()
  if (value.status === 'NOT_RUN') {
    if ([...caseCounts, 'errorCode', 'taskId', 'submissionId', 'ownershipSummary',
      'businessKeyDigest', 'reviewMethod'].some((key) => value[key] !== null)
      || value.batchNodes.length !== 0 || value.evidencePaths.length !== 0) fail()
    return
  }
  if (value.status === 'FAILED' && value.errorCode === null) fail()
  const leaves = validateTree(value)
  if (value.status !== 'PASS') return
  if (value.errorCode !== null || value.requestCount === null || value.requestCount < 1
    || value.leafCount === null || value.succeededLeafCount !== value.leafCount
    || value.failedLeafCount !== 0 || value.emptyLeafCount === null || value.sourceRowCount === null
    || !leaves.every(succeeded) || value.reviewMethod === null || value.evidencePaths.length === 0
    || (value.phase === 'TASK' && (!text(value.taskId) || !text(value.submissionId)
      || sqlFields.some((key) => value[key] === null)))) fail()
  if (leaves.length === 0 && (value.apiName !== 'top_list' || value.mode !== 'RANGE'
    || value.expectedCoverage !== 'COMPLETE_CLOSED_CALENDAR'
    || value.sourceRowCount !== 0 || (value.phase === 'TASK'
      && (value.insertedRows !== 0 || value.updatedRows !== 0
        || value.sqlBeforeKeyCount !== value.sqlAfterKeyCount)))) fail()
}

function validateRun(value, inputHashes) {
  if (!exact(value, keys.run) || !text(value.runId) || !utc(value.startedAt) || !utc(value.finishedAt)
    || value.startedAt > value.finishedAt || !sha(value.sourceDiffSha256)
    || !sha(value.productionJarSha256) || !sha(value.acceptanceJarSha256)
    || keys.hashes.some((key) => value[key] !== inputHashes[key])
    || !refs(value.commands) || value.commands.length === 0 || !Number.isInteger(value.exitCode)
    || !exact(value.cleanup, keys.cleanup) || !['PASS', 'FAILED'].includes(value.cleanup.status)
    || !utc(value.cleanup.completedAt) || !text(value.cleanup.evidencePath)
    || !Array.isArray(value.cases)) fail()
  value.cases.forEach(validateEvidenceCase)
}

function validateCompleteness(value, schemaVersion, entry) {
  if (!exact(value, keys.completeness)
    || !['UNKNOWN', 'ROW_LIMIT', 'CALENDAR_COVERAGE', 'RESPONSE_ONLY'].includes(value.kind)
    || !refs(value.evidenceRefs)) fail()
  if ((value.kind === 'UNKNOWN' && (value.rowLimit !== null || value.evidenceRefs.length !== 0))
    || (value.kind === 'ROW_LIMIT' && (!integer(value.rowLimit) || value.rowLimit === 0
      || value.evidenceRefs.length === 0))
    || (value.kind === 'CALENDAR_COVERAGE' && (value.rowLimit !== null
      || value.evidenceRefs.length === 0))
    || (value.kind === 'RESPONSE_ONLY' && (schemaVersion !== 2
      || !RESPONSE_ONLY_APIS.has(entry.apiName) || entry.planningMode !== 'NATIVE_RANGE'
      || value.rowLimit !== null || entry.decisionRef !== RESPONSE_ONLY_DECISION
      || !value.evidenceRefs.includes(RESPONSE_ONLY_DECISION)
      || !value.evidenceRefs.includes(`docs/verification/ISSUE-018-range-acceptance.md#${entry.apiName}`)
      || !value.evidenceRefs.includes(entry.officialUrl)))) fail()
}

function phaseStatus(found, phase) {
  const statuses = found.filter(({ evidence }) => evidence.phase === phase)
    .map(({ evidence }) => evidence.status)
  if (statuses.includes('FAILED')) return 'FAILED'
  if (statuses.length === 0 || statuses.every((status) => status === 'NOT_RUN')) return 'NOT_RUN'
  if (statuses.every((status) => status === 'PASS')) return 'PASS'
  return 'EVIDENCE_MISSING'
}

function validateAvailable(value, found) {
  if (value.completeness.kind === 'UNKNOWN' || value.sourceStatus !== 'PASS'
    || value.taskStatus !== 'PASS' || Number(value.policyVersion.slice('tushare-range-v'.length)) < 2
    || value.unresolved.length !== 0 || value.decisionRef === null) fail()
  const ranges = found.filter(({ evidence }) => evidence.mode === 'RANGE')
  if (!ranges.some(({ evidence }) => evidence.phase === 'SOURCE')
    || !ranges.some(({ evidence }) => evidence.phase === 'TASK')) fail()
  if (found.some(({ run }) => run.exitCode !== 0 || run.cleanup.status !== 'PASS')) fail()
  for (const sample of ranges) {
    if (!ranges.some((other) => sample.evidence.phase !== other.evidence.phase
      && sample.run.runId !== other.run.runId
      && sample.evidence.dateAxis === other.evidence.dateAxis
      && same(sample.evidence.params, other.evidence.params))) fail()
  }
  const sourceRuns = new Set(ranges.filter(({ evidence }) => evidence.phase === 'SOURCE')
    .map(({ run }) => run.runId))
  if (ranges.some(({ evidence, run }) => evidence.phase === 'TASK' && sourceRuns.has(run.runId))) fail()
}

function validateResponseOnlyTasks(found) {
  const sources = found.filter(({ evidence, run }) => evidence.phase === 'SOURCE'
    && evidence.status === 'PASS' && run.exitCode === 0 && run.cleanup.status === 'PASS')
  for (const { evidence: task } of found.filter(({ evidence }) =>
    evidence.phase === 'TASK' && evidence.mode === 'RANGE')) {
    if (task.expectedCoverage !== 'RESPONSE_ONLY') fail()
    const matches = sources.filter(({ run, evidence }) =>
      task.expectedCoverageSource === responseOnlyExpectedCoverageSource({
        runId: run.runId, caseId: evidence.caseId,
      }) && evidence.dateAxis === task.dateAxis && same(evidence.params, task.params))
    if (matches.length !== 1) fail()
    if (task.status === 'PASS' && (task.requestCount !== 1 || task.batchNodes.length !== 1
      || task.leafCount !== 1 || task.succeededLeafCount !== 1 || task.failedLeafCount !== 0
      || task.batchNodes[0].parentBatchId !== null || !succeeded(task.batchNodes[0])
      || task.batchNodes[0].start !== task.params.start_date
      || task.batchNodes[0].end !== task.params.end_date)) fail()
  }
}

function validateGlobalResponseOnlyTask(item, cases) {
  const { evidence: task, run: taskRun } = item
  if (task.phase !== 'TASK' || task.mode !== 'RANGE'
    || !RESPONSE_ONLY_APIS.has(task.apiName)) fail()
  const prefix = `${RESPONSE_ONLY_DECISION} docs/verification/ISSUE-018-range-acceptance.json#`
  if (!task.expectedCoverageSource.startsWith(prefix)) fail()
  const identity = task.expectedCoverageSource.slice(prefix.length).split('/')
  if (identity.length !== 2 || identity.some((value) => !text(value))) fail()
  const source = cases.get(identity[1])
  if (!source || source.run.runId !== identity[0] || source.run.runId === taskRun.runId
    || source.run.exitCode !== 0 || source.run.cleanup.status !== 'PASS'
    || source.evidence.phase !== 'SOURCE' || source.evidence.status !== 'PASS'
    || source.evidence.apiName !== task.apiName || source.evidence.mode !== 'RANGE'
    || source.evidence.dateAxis !== task.dateAxis
    || !same(source.evidence.params, task.params)) fail()
  if (task.status === 'PASS' && (task.requestCount !== 1 || task.batchNodes.length !== 1
    || task.leafCount !== 1 || task.succeededLeafCount !== 1 || task.failedLeafCount !== 0
    || task.batchNodes[0].parentBatchId !== null || !succeeded(task.batchNodes[0])
    || task.batchNodes[0].start !== task.params.start_date
    || task.batchNodes[0].end !== task.params.end_date)) fail()
}

function validateInterface(value, row, cases, schemaVersion) {
  const [apiName, rangeTarget, shape, planningMode, dateAxis, outputDateColumn, docId] = row
  if (!exact(value, keys.interface) || value.apiName !== apiName || value.rangeTarget !== rangeTarget
    || value.shape !== shape || value.planningMode !== planningMode || value.dateAxis !== dateAxis
    || value.outputDateColumn !== outputDateColumn
    || value.officialUrl !== `https://tushare.pro/document/2?doc_id=${docId}`
    || !['NOT_RUN', 'PASS', 'FAILED', 'EVIDENCE_MISSING'].includes(value.sourceStatus)
    || !['NOT_RUN', 'PASS', 'FAILED', 'EVIDENCE_MISSING'].includes(value.taskStatus)
    || !refs(value.cases) || new Set(value.cases).size !== value.cases.length
    || !refs(value.unresolved) || !(value.decisionRef === null || text(value.decisionRef))) fail()
  validateCompleteness(value.completeness, schemaVersion, value)
  const found = value.cases.map((id) => cases.get(id))
  if (found.some((item) => !item || item.evidence.apiName !== apiName)
    || value.sourceStatus !== phaseStatus(found, 'SOURCE')
    || value.taskStatus !== phaseStatus(found, 'TASK')) fail()
  if (value.completeness.kind === 'RESPONSE_ONLY') validateResponseOnlyTasks(found)
  if (!rangeTarget) {
    if (value.disposition !== 'SINGLE_ONLY' || value.policyVersion !== null
      || found.some(({ evidence }) => evidence.mode !== 'SINGLE')) fail()
    return
  }
  if (!/^tushare-range-v[1-9]\d*$/.test(value.policyVersion)) fail()
  if (value.disposition === 'AVAILABLE') validateAvailable(value, found)
  else if (value.disposition === 'EXCLUDED') {
    if (value.decisionRef === null) fail()
  } else if (value.disposition !== 'NEEDS_VERIFICATION') fail()
}

export function validateEvidence(index) {
  assertSafeStrings(index)
  if (!exact(index, keys.index) || ![1, 2].includes(index.schemaVersion)
    || !exact(index.inputHashes, keys.hashes) || !sha(index.inputHashes.manifestSha256)
    || !sha(index.inputHashes.requestExamplesSha256) || !Array.isArray(index.interfaces)
    || index.interfaces.length !== matrix.length || !Array.isArray(index.runs)) fail()
  const cases = new Map()
  const runIds = new Set()
  for (const run of index.runs) {
    validateRun(run, index.inputHashes)
    if (runIds.has(run.runId)) fail()
    runIds.add(run.runId)
    for (const evidence of run.cases) {
      if (cases.has(evidence.caseId)) fail()
      cases.set(evidence.caseId, { evidence, run })
    }
  }
  for (const item of cases.values()) {
    if (item.evidence.expectedCoverage !== 'RESPONSE_ONLY') continue
    if (index.schemaVersion === 1) fail()
    validateGlobalResponseOnlyTask(item, cases)
  }
  matrix.forEach((row, i) => validateInterface(index.interfaces[i], row, cases, index.schemaVersion))
  return index
}

export function safeCaseEvidence(input) {
  const pick = (source, wanted) => Object.fromEntries(wanted.map((key) => [key, source?.[key] ?? null]))
  const row = byApi.get(input?.apiName)
  const paramKeys = input?.mode === 'RANGE' && row
    ? row[2] === 'STOCK' ? ['ts_code', 'start_date', 'end_date']
      : row[2] === 'DATES' ? ['start_date', 'end_date']
        : row[2] === 'EXCHANGE' ? ['exchange', 'start_date', 'end_date']
          : ['exchange_id', 'start_date', 'end_date']
    : Object.keys(singles[input?.apiName] ?? {})
  const output = pick(input, keys.case)
  output.params = pick(input?.params, paramKeys)
  output.batchNodes = Array.isArray(input?.batchNodes)
    ? input.batchNodes.map((node) => pick(node, keys.batch)) : null
  const scalar = (value) => value === null || ['string', 'number'].includes(typeof value)
  if (keys.case.filter((key) => !['params', 'batchNodes', 'evidencePaths'].includes(key))
    .some((key) => !scalar(output[key])) || !Object.values(output.params).every(scalar)
    || (output.evidencePaths !== null && !refs(output.evidencePaths))
    || output.batchNodes?.some((node) => !Object.values(node).every(scalar))) fail()
  assertSafeStrings(output)
  if (!validError(output.errorCode)
    || output.batchNodes?.some((node) => !validError(node.errorCode))) fail()
  return output
}

// Test harness contracts. These do not change production registration or availability.
export function selectTaskCases(phase, plan, index, sourceBindings = new Map()) {
  if (phase === 'single') {
    if (index) fail()
    const canonical = matrix.flatMap(([apiName]) => {
      const first = structuredClone(singles[apiName])
      const samples = [first]
      if (first.ts_code) samples.push({ ...first, ts_code: '600000.SH',
        ...(apiName === 'stock_company' ? { exchange: 'SSE' } : {}) })
      return samples.map((params, i) => ({ caseId: `${apiName}-single-${i + 1}`, apiName,
        mode: 'SINGLE', params, dateAxis: null, start: null, end: null, evidenceRefs: [] }))
    })
    if (plan === undefined) return canonical
    validateCasePlan(plan)
    if (plan.cases.length !== canonical.length) fail()
    return canonical.map((sample) => {
      const matches = plan.cases.filter((candidate) => candidate.apiName === sample.apiName
        && candidate.mode === sample.mode && same(candidate.params, sample.params))
      if (matches.length !== 1) fail()
      return matches[0]
    })
  }
  if (phase !== 'range') fail()
  validateCasePlan(plan)
  validateEvidence(index)
  if (index.runs.some((run) => run.runId === plan.runId)) fail()
  for (const candidate of plan.cases) {
    const entry = index.interfaces.find((row) => row.apiName === candidate.apiName)
    if (candidate.mode !== 'RANGE' || !entry.rangeTarget || entry.disposition === 'EXCLUDED'
      || entry.completeness.kind === 'UNKNOWN'
      || !entry.completeness.evidenceRefs.every((ref) => candidate.evidenceRefs.includes(ref))
      || index.runs.some((run) => run.cases.some((c) => c.caseId === candidate.caseId))) fail()
    const sources = index.runs.flatMap((run) => run.cases.map((evidence) => ({ run, evidence })))
      .filter(({ run, evidence }) => run.exitCode === 0 && run.cleanup.status === 'PASS'
        && entry.cases.includes(evidence.caseId) && evidence.phase === 'SOURCE'
        && evidence.status === 'PASS' && evidence.apiName === candidate.apiName
        && evidence.mode === 'RANGE' && evidence.dateAxis === candidate.dateAxis
        && same(evidence.params, candidate.params))
    const fullRefs = candidate.evidenceRefs.map((ref) =>
      /^docs\/verification\/ISSUE-018-range-acceptance\.json#([^/]+)\/([^/]+)$/.exec(ref))
      .filter(Boolean)
    const legacyRefs = candidate.evidenceRefs.map((ref) =>
      /^docs\/verification\/ISSUE-018-range-acceptance\.json#([^/]+)$/.exec(ref))
      .filter(Boolean)
    if (entry.completeness.kind === 'RESPONSE_ONLY'
      && (fullRefs.length !== 1 || legacyRefs.length !== 0)) fail()
    if (fullRefs.length > 1 || legacyRefs.length > 1 || (fullRefs.length && legacyRefs.length)) fail()
    const matches = fullRefs.length
      ? sources.filter(({ run, evidence }) => run.runId === fullRefs[0][1]
        && evidence.caseId === fullRefs[0][2])
      : legacyRefs.length
        ? sources.filter(({ evidence }) => evidence.caseId === legacyRefs[0][1])
        : sources
    if (matches.length !== 1) fail()
    const { run, evidence } = matches[0]
    const source = { runId: run.runId, caseId: evidence.caseId,
      expectedCoverage: evidence.expectedCoverage, expectedCoverageSource: evidence.expectedCoverageSource }
    sourceBindings.set(candidate.caseId, source)
  }
  return plan.cases
}

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
export function validateTaskAcceptance(request, expected, response) {
  const body = response?.body
  if (!exact(request, ['submissionId', 'pluginId', 'apiName', 'mode', 'params'])
    || !uuidPattern.test(request.submissionId) || request.pluginId !== expected.pluginId
    || request.apiName !== expected.apiName || request.mode !== expected.mode
    || !same(request.params, expected.params) || response.status !== 202
    || !exact(body, ['requestId', 'taskId', 'status', 'version', 'createdAt'])
    || !text(body.requestId) || body.requestId !== response.requestId
    || !uuidPattern.test(body.taskId) || body.status !== 'QUEUED' || body.version !== 1
    || !utc(body.createdAt) || response.location !== `/api/v1/download-tasks/${body.taskId}`) fail()
  assertSafeStrings(body)
  return body
}

export function summarizeSqlRows(rows, keyIndexes, stockIndex, selectedStock) {
  if (!Array.isArray(rows) || !Array.isArray(keyIndexes) || keyIndexes.length === 0
    || !keyIndexes.every(integer) || !rows.every((row) => Array.isArray(row)
      && row.every((v) => v === null || typeof v === 'string')
      && keyIndexes.every((i) => i < row.length))) fail()
  const tuples = rows.map((row) => JSON.stringify(row))
  const businessKeys = rows.map((row) => JSON.stringify(keyIndexes.map((i) => row[i])))
  if (new Set(businessKeys).size !== rows.length) fail()
  const digest = (values) => createHash('sha256').update(JSON.stringify(values.sort())).digest('hex')
  const selected = stockIndex >= 0 && selectedStock
    ? rows.filter((row) => row[stockIndex] === selectedStock) : rows
  const other = stockIndex >= 0 && selectedStock
    ? rows.filter((row) => row[stockIndex] !== selectedStock) : []
  const owners = stockIndex < 0 ? [] : [...new Set(rows.map((row) => row[stockIndex]))].sort()
  return { keyCount: businessKeys.length, selectedCount: selected.length,
    keyDigest: digest(businessKeys), rowDigest: digest(tuples),
    otherStockDigest: digest(other.map((row) => JSON.stringify(row))),
    ownershipSummary: owners.map((owner) => `${owner}:${rows.filter((row) => row[stockIndex] === owner).length}`).join(',') || 'non-stock dataset' }
}

export function allowedTaskRead(url, taskIds) {
  const route = url.pathname
  if (route === '/api/v1/download-tasks') return url.search === '?page=1&pageSize=20'
  for (const id of taskIds) {
    if (!uuidPattern.test(id)) return false
    if ([`/downloads/tasks/${id}`, `/api/v1/download-tasks/${id}`].includes(route)) return url.search === ''
    if (route === `/api/v1/download-tasks/${id}/batches`) return /^\?page=[1-9]\d*&pageSize=(?:20&includeSplit=false|100&includeSplit=true)$/.test(url.search)
  }
  return false
}

export function summarizeTaskBatches(task, nodes) {
  if (!Array.isArray(nodes) || !object(task?.counts)) fail()
  const batchNodes = nodes.map((node) => ({ batchId: node.batchId, parentBatchId: node.parentBatchId,
    status: node.status, start: node.rangeStart?.replaceAll('-', '') ?? null,
    end: node.rangeEnd?.replaceAll('-', '') ?? null,
    sourceRowCount: evidenceInteger(node.sourceRows), insertedRows: evidenceInteger(node.insertedRows),
    updatedRows: evidenceInteger(node.updatedRows), errorCode: node.error?.code ?? null }))
  const ids = new Map(batchNodes.map((node) => [node.batchId, node]))
  if (ids.size !== nodes.length) fail()
  for (const node of batchNodes) {
    if (node.parentBatchId !== null && ids.get(node.parentBatchId)?.status !== 'SPLIT') fail()
    if (node.status === 'SPLIT' && batchNodes.filter((c) => c.parentBatchId === node.batchId).length !== 2) fail()
  }
  const leaves = batchNodes.filter((node) => node.status !== 'SPLIT')
  const success = leaves.filter(succeeded)
  const result = { batchNodes, leafCount: leaves.length, succeededLeafCount: success.length,
    failedLeafCount: leaves.filter((node) => node.status === 'FAILED').length,
    emptyLeafCount: success.filter((node) => node.sourceRowCount === 0).length,
    ...Object.fromEntries(rowCounts.map((key) => [key, success.reduce((sum, node) => sum + node[key], 0)])) }
  const expected = { totalBatches: leaves.length, pendingBatches: leaves.filter((n) => n.status === 'PENDING').length,
    runningBatches: leaves.filter((n) => n.status === 'RUNNING').length, succeededBatches: success.length,
    failedBatches: result.failedLeafCount, splitBatches: nodes.length - leaves.length,
    sourceRows: result.sourceRowCount, insertedRows: result.insertedRows, updatedRows: result.updatedRows }
  if (Object.entries(expected).some(([key, value]) => !integer(value)
    || evidenceInteger(task.counts[key]) !== value)) fail()
  if (task.status === 'SUCCEEDED' && success.length !== leaves.length) fail()
  return result
}

export function validateTaskRuntime(candidate, params, task, nodes) {
  const ruleKind = { ROW_LIMIT: 'CONFIRMED_ROW_LIMIT', CALENDAR_COVERAGE: 'VERIFIED_RULE',
    RESPONSE_ONLY: 'RESPONSE_ONLY' }[candidate?.completeness?.kind]
  if (!ruleKind || task?.mode !== 'RANGE' || !exact(task.extraction, ['policyVersion', 'ruleKind'])
    || task.extraction.policyVersion !== candidate.policyVersion
    || task.extraction.ruleKind !== ruleKind || !Array.isArray(nodes)) fail()
  if (ruleKind !== 'RESPONSE_ONLY') return true
  const leaf = nodes[0]
  if (candidate.planningMode !== 'NATIVE_RANGE' || candidate.completeness.rowLimit !== null
    || task.status !== 'SUCCEEDED' || evidenceInteger(task.requestCount, 5000) !== 1
    || nodes.length !== 1 || leaf.parentBatchId !== null || leaf.status !== 'SUCCEEDED'
    || leaf.attemptCount !== 1 || leaf.rangeStart?.replaceAll('-', '') !== params.start_date
    || leaf.rangeEnd?.replaceAll('-', '') !== params.end_date) fail()
  return true
}

export function sqlMetadata(yaml) {
  const tables = [...yaml.matchAll(/^tableName: ([a-z][a-z0-9_]+)$/gm)]
  const business = [...yaml.matchAll(/^businessKey: \{ mode: (?:COMPOSITE|FINGERPRINT), fields: \[([a-z0-9_, ]+)\] \}$/gm)]
  if (tables.length !== 1 || business.length !== 1) fail()
  const keys = business[0][1].split(',').map((v) => v.trim())
  if (!keys.length || new Set(keys).size !== keys.length || keys.some((v) => !/^[a-z][a-z0-9_]+$/.test(v))) fail()
  return { table: tables[0][1], keys }
}


export function bindEvidenceInput(bytes) {
  let value
  try { value = JSON.parse(bytes) } catch { fail() }
  assertSafeStrings(value)
  return { value, sha256: createHash('sha256').update(bytes).digest('hex') }
}

export function safeRecordsQueryFailure(body, context) {
  const { pluginId, apiName, caseId, position, httpStatus, requestId } = context
  if (!text(pluginId) || !text(apiName) || !text(caseId) || !['BEFORE', 'AFTER'].includes(position)
    || !Number.isInteger(httpStatus) || httpStatus < 400 || httpStatus > 599
    || !text(requestId) || body?.requestId !== requestId || !errorCodes.has(body?.code)) fail()
  const result = { operation: 'RECORDS_QUERY', pluginId, apiName, caseId, position,
    httpStatus, requestId, errorCode: body.code }
  assertSafeStrings(result)
  return result
}

export function safeTaskQueryFailure(body, context) {
  const { pluginId, apiName, caseId, taskId, pathname, httpStatus, requestId, responseRequestId } = context
  if (!exact(body, ['requestId', 'code', 'message', 'retryable', 'fieldErrors'])
    || !errorCodes.has(body.code) || !text(body.message) || !body.message.trim()
    || typeof body.retryable !== 'boolean' || !Array.isArray(body.fieldErrors)
    || body.fieldErrors.some((entry) => !exact(entry, ['field', 'message'])
      || !text(entry.field) || !entry.field.trim() || !text(entry.message) || !entry.message.trim())
    || !uuidPattern.test(requestId) || requestId !== responseRequestId || requestId !== body.requestId
    || !Number.isInteger(httpStatus) || httpStatus < 400 || httpStatus > 599
    || !uuidPattern.test(taskId) || typeof pathname !== 'string'
    || !pathname.startsWith(`/api/v1/download-tasks/${taskId}`)
    || !allowedTaskRead(new URL(pathname, 'http://localhost'), new Set([taskId]))
    || !(pluginId === 'tushare_pro' && byApi.has(apiName) && text(caseId)
      || pluginId === 'fixture' && apiName === 'fixture_daily' && caseId === null)) fail()
  assertSafeStrings(body)
  const result = { operation: 'TASK_QUERY', pluginId, apiName, caseId, taskId, pathname,
    httpStatus, requestId, errorCode: body.code }
  assertSafeStrings(result)
  return result
}
