import { selectDownloadApi } from './catalog-helpers.js'
import { expect, test } from '@playwright/test'
import { execFile, spawn } from 'node:child_process'
import { createHash, randomUUID } from 'node:crypto'
import {
  appendFile,
  chmod,
  lstat,
  mkdir,
  open,
  readFile,
  stat,
  writeFile,
} from 'node:fs/promises'
import { readFileSync, lstatSync } from 'node:fs'
import { createConnection } from 'node:net'
import path from 'node:path'
import { promisify } from 'node:util'
import { setTimeout as delay } from 'node:timers/promises'

import { selectTaskCases, validateTaskAcceptance, allowedTaskRead, summarizeTaskBatches, summarizeSqlRows, sqlMetadata, safeCaseEvidence, validateEvidenceCase, bindEvidenceInput, safeRecordsQueryFailure, safeTaskQueryFailure, responseOnlyExpectedCoverageSource, evidenceInteger, validateTaskRuntime } from './tushare-range-evidence.js'
import { parseDownloadTask, parseDownloadBatchPage, parseDownloadCapabilities } from '../src/api/downloadTaskDtos.js'

const execFileAsync = promisify(execFile)
const BASE_URL = 'http://127.0.0.1:8080'
const MANIFEST_SHA = '386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984'
const REQUESTS_SHA = '6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f'
const JAR_SHA = process.env.ISSUE_017_ACCEPTANCE_JAR_SHA256
const ERROR_KEYS = ['requestId', 'code', 'message', 'retryable', 'fieldErrors']
const FIELD_ERROR_KEYS = ['field', 'message']
const API_ERROR_CODES = new Set([
  'PARAM_REQUIRED', 'PARAM_INVALID', 'PLUGIN_DISABLED', 'DATASET_MISCONFIGURED',
  'SOURCE_AUTH_FAILED', 'SOURCE_PERMISSION_DENIED', 'SOURCE_RATE_LIMITED',
  'SOURCE_UNAVAILABLE', 'SOURCE_NETWORK_ERROR', 'SOURCE_TIMEOUT',
  'SOURCE_PAYLOAD_INVALID', 'ADAPTER_FIELD_MISSING', 'ADAPTER_TYPE_INVALID',
  'PERSISTENCE_FAILED', 'QUERY_FAILED', 'INTERNAL_ERROR',
])
const PAGE_KEYS = [
  'requestId', 'pluginId', 'apiName', 'columns', 'items', 'page', 'pageSize',
  'totalElements', 'totalPages',
]
const SOURCE_COLUMNS = ['source_plugin', 'source_api', 'ingested_at']
const FIXTURE_COLUMNS = [
  'ts_code', 'trade_date', 'amount', 'note', ...SOURCE_COLUMNS,
]
const FIXTURE_OPTION = /^Fixture 日线\s*fixture_daily$/
const MAX_LOG_LINE = 1024 * 1024
const ENV_ALLOWLIST = ['PATH', 'HOME', 'JAVA_HOME', 'TMPDIR', 'LANG', 'LC_ALL']
const DB_ENV = ['TENSOR_DB_URL', 'TENSOR_DB_USERNAME', 'TENSOR_DB_PASSWORD']
const SUPPORTED_API_NAMES = [
  'stock_basic', 'stock_company', 'income', 'balancesheet', 'cashflow',
  'fina_indicator', 'fina_audit', 'fina_mainbz', 'stk_rewards',
  'stk_holdernumber', 'trade_cal', 'margin', 'daily', 'weekly', 'monthly',
  'adj_factor', 'suspend_d', 'daily_basic', 'moneyflow', 'stk_limit',
  'top_list', 'margin_detail', 'block_trade', 'slb_len', 'slb_sec',
  'slb_sec_detail', 'forecast', 'express', 'dividend', 'disclosure_date',
  'repurchase', 'stk_holdertrade', 'top10_holders', 'top10_floatholders',
  'new_share', 'stk_managers', 'pledge_stat', 'pledge_detail',
  'index_classify', 'index_member_all',
]

const FILTER_LABELS = {
  ts_code: ['证券代码'],
  trade_date: ['交易开始日期', '交易结束日期'],
  ann_date: ['公告开始日期', '公告结束日期'],
}

function buildFilters() {
  const groups = [
    [[], 'trade_cal index_classify'],
    [['ts_code'], 'stock_basic stock_company new_share index_member_all fina_mainbz pledge_stat'],
    [['trade_date'], 'margin slb_len'],
    [['ts_code', 'trade_date'], 'daily weekly monthly adj_factor suspend_d daily_basic stk_limit moneyflow margin_detail top_list block_trade slb_sec slb_sec_detail'],
    [['ts_code', 'ann_date'], 'stk_managers income balancesheet cashflow fina_indicator fina_audit express forecast disclosure_date dividend repurchase stk_rewards stk_holdernumber stk_holdertrade top10_holders top10_floatholders pledge_detail'],
  ]
  const filters = new Map()
  for (const [fields, names] of groups) {
    for (const name of names.split(' ')) filters.set(name, fields)
  }
  return filters
}
const FILTERS = buildFilters()

const CONTRACT_ROWS = [
  ['stock_basic', '股票基础信息', ['ts_code', 'list_status'], 10],
  ['stock_company', '上市公司基本信息', ['ts_code', 'exchange'], 18],
  ['income', '利润表', ['ts_code', 'ann_date'], 85],
  ['balancesheet', '资产负债表', ['ts_code', 'ann_date'], 152],
  ['cashflow', '现金流量表', ['ts_code', 'ann_date'], 97],
  ['fina_indicator', '财务指标', ['ts_code', 'ann_date'], 108],
  ['fina_audit', '财务审计意见', ['ts_code', 'ann_date'], 7],
  ['fina_mainbz', '主营业务构成', ['ts_code'], 8],
  ['stk_rewards', '管理层薪酬与持股', ['ts_code'], 7],
  ['stk_holdernumber', '股东户数', ['ts_code'], 4],
  ['trade_cal', '交易日历', ['exchange', 'start_date', 'end_date'], 4],
  ['margin', '融资融券汇总', ['exchange_id', 'trade_date'], 9],
  ['daily', '日线行情', ['ts_code', 'trade_date'], 11],
  ['weekly', '周线行情', ['ts_code', 'trade_date'], 11],
  ['monthly', '月线行情', ['ts_code', 'trade_date'], 11],
  ['adj_factor', '复权因子', ['ts_code', 'trade_date'], 3],
  ['suspend_d', '每日停复牌信息', ['ts_code', 'trade_date'], 4],
  ['daily_basic', '每日估值与市场指标', ['ts_code', 'trade_date'], 18],
  ['moneyflow', '个股资金流向', ['ts_code', 'trade_date'], 20],
  ['stk_limit', '每日涨跌停价格', ['ts_code', 'trade_date'], 4],
  ['top_list', '龙虎榜每日明细', ['ts_code', 'trade_date'], 15],
  ['margin_detail', '融资融券交易明细', ['ts_code', 'trade_date'], 10],
  ['block_trade', '大宗交易', ['ts_code', 'trade_date'], 7],
  ['slb_len', '转融通期限与规模', ['trade_date'], 6],
  ['slb_sec', '转融通证券汇总', ['ts_code', 'trade_date'], 7],
  ['slb_sec_detail', '转融通证券明细', ['ts_code', 'trade_date'], 6],
  ['forecast', '业绩预告', ['ts_code', 'ann_date'], 13],
  ['express', '业绩快报', ['ts_code', 'ann_date'], 15],
  ['dividend', '分红送股', ['ts_code', 'ann_date'], 14],
  ['disclosure_date', '财报披露计划', ['ts_code', 'ann_date'], 5],
  ['repurchase', '股票回购', ['ann_date'], 9],
  ['stk_holdertrade', '股东增减持', ['ts_code', 'ann_date'], 11],
  ['top10_holders', '前十大股东', ['ts_code', 'ann_date'], 9],
  ['top10_floatholders', '前十大流通股东', ['ts_code', 'ann_date'], 9],
  ['new_share', 'IPO 新股发行信息', ['start_date', 'end_date'], 12],
  ['stk_managers', '上市公司管理层信息', ['ts_code'], 11],
  ['pledge_stat', '股权质押统计', ['ts_code'], 7],
  ['pledge_detail', '股权质押明细', ['ts_code'], 14],
  ['index_classify', '行业指数分类', [], 7],
  ['index_member_all', '行业分级与完整成分', ['ts_code'], 11],
]
const CONTRACTS = new Map(CONTRACT_ROWS.map(([apiName, displayName, parameters, columns]) => [
  apiName,
  { apiName, displayName, parameters, columns, filters: FILTERS.get(apiName) },
]))
safeCheck(FILTERS.size === 40, 'filter contract count')

function safeCheck(condition, name) {
  if (!condition) throw new Error(`Safe check failed: ${name}`)
}

function objectWithExactKeys(value, keys, name) {
  safeCheck(value !== null && typeof value === 'object' && !Array.isArray(value), `${name} object`)
  safeCheck(
    JSON.stringify(Object.keys(value).sort()) === JSON.stringify([...keys].sort()),
    `${name} exact keys`,
  )
}

function integer(value) {
  return Number.isSafeInteger(value) && value >= 0
}

export function validateManifest(bytes, expectedHash = MANIFEST_SHA) {
  safeCheck(createHash('sha256').update(bytes).digest('hex') === expectedHash, 'manifest hash')
  let manifest
  try {
    manifest = JSON.parse(bytes)
  } catch {
    throw new Error('Safe check failed: manifest JSON')
  }
  safeCheck(Array.isArray(manifest.interfaces) && manifest.interfaces.length === 40, 'manifest interface count')
  const names = new Set()
  let sampleCount = 0
  let okCount = 0
  for (const entry of manifest.interfaces) {
    objectWithExactKeys(
      entry,
      ['api_name', 'filename', 'query_mode', 'params', 'row_count', 'status'],
      'manifest interface',
    )
    safeCheck(/^[a-z][a-z0-9_]{1,63}$/.test(entry.api_name), 'manifest API name')
    safeCheck(!names.has(entry.api_name) && CONTRACTS.has(entry.api_name), 'manifest unique known API')
    names.add(entry.api_name)
    safeCheck(entry.filename === `${entry.api_name}.json`, 'manifest filename')
    safeCheck(entry.status === 'ok' || entry.status === 'empty', 'manifest status')
    safeCheck(Array.isArray(entry.params) && entry.params.length > 0, 'manifest params array')
    for (const sample of entry.params) {
      safeCheck(sample !== null && typeof sample === 'object' && !Array.isArray(sample), 'manifest params object')
      safeCheck(Object.values(sample).every((value) => typeof value === 'string'), 'manifest string values')
    }
    sampleCount += entry.params.length
    okCount += Number(entry.status === 'ok')
  }
  safeCheck(names.size === CONTRACTS.size, 'manifest API set')
  safeCheck(sampleCount === 48, 'manifest sample count')
  safeCheck(okCount === 28, 'manifest status counts')
  return { manifest, sampleCount, okCount, emptyCount: 40 - okCount }
}

export function validateRequests(bytes, interfaces, expectedHash = REQUESTS_SHA) {
  safeCheck(createHash('sha256').update(bytes).digest('hex') === expectedHash, 'request examples hash')
  let document
  try {
    document = JSON.parse(bytes)
  } catch {
    throw new Error('Safe check failed: request examples JSON')
  }
  objectWithExactKeys(document, ['requests'], 'request examples document')
  safeCheck(Array.isArray(document.requests) && document.requests.length === 40, 'request examples count')
  safeCheck(document.requests.filter(({ params }) => Object.hasOwn(params, 'ts_code')).length === 34, 'stock request count')
  document.requests.forEach((request, index) => {
    objectWithExactKeys(request, ['pluginId', 'apiName', 'params'], 'request example')
    safeCheck(request.pluginId === 'tushare_pro', 'request example plugin')
    safeCheck(request.apiName === interfaces[index].api_name, 'request example manifest order')
    const contract = CONTRACTS.get(request.apiName)
    safeCheck(contract, 'request example known API')
    objectWithExactKeys(request.params, contract.parameters, 'request example params')
    safeCheck(Object.values(request.params).every((value) => typeof value === 'string'), 'request example string values')
  })
  return document.requests
}

export function selectLiveInterfaces(interfaces, requests) {
  safeCheck(Array.isArray(interfaces) && interfaces.length === 40, 'live scope input count')
  const names = interfaces.map((entry) => entry?.api_name)
  safeCheck(names.every((name) => typeof name === 'string'), 'live scope API names')
  safeCheck(new Set(names).size === names.length, 'live scope unique API names')
  safeCheck(
    JSON.stringify(names) === JSON.stringify(SUPPORTED_API_NAMES),
    'live scope supported API order',
  )
  safeCheck(Array.isArray(requests) && requests.length === interfaces.length, 'live scope request count')
  const samples = selectTaskCases('single')
  safeCheck(requests.every((request) => JSON.stringify(samples.find((c) => c.apiName === request.apiName).params) === JSON.stringify(request.params)), 'single examples match canonical cases')
  return { interfaces: interfaces.map((entry) => ({ ...entry,
    cases: samples.filter((c) => c.apiName === entry.api_name),
    params: samples.filter((c) => c.apiName === entry.api_name).map((c) => c.params),
  })), sampleCount: samples.length, okCount: 28, emptyCount: 12 }
}

export function validateApiError(body) {
  objectWithExactKeys(body, ERROR_KEYS, 'API error')
  safeCheck(typeof body.requestId === 'string' && body.requestId.length > 0, 'API error request ID')
  safeCheck(API_ERROR_CODES.has(body.code), 'API error public code')
  safeCheck(typeof body.message === 'string' && body.message.length > 0, 'API error public message')
  safeCheck(typeof body.retryable === 'boolean' && Array.isArray(body.fieldErrors), 'API error shape')
  for (const fieldError of body.fieldErrors) {
    objectWithExactKeys(fieldError, FIELD_ERROR_KEYS, 'field error')
    safeCheck(typeof fieldError.field === 'string' && fieldError.field.length > 0, 'field error field')
    safeCheck(typeof fieldError.message === 'string' && fieldError.message.length > 0, 'field error message')
  }
  return body
}

export function validateBusinessRow(row, columns, pluginId, apiName, startedAt, finishedAt) {
  objectWithExactKeys(row, columns, 'business row')
  safeCheck(Object.values(row).every((value) => value === null || typeof value === 'string'), 'business row value types')
  safeCheck(row.source_plugin === pluginId && row.source_api === apiName, 'business row source')
  const instant = Date.parse(row.ingested_at)
  safeCheck(Number.isFinite(instant), 'business row ingestion instant')
  safeCheck(instant >= startedAt && instant <= finishedAt, 'business row ingestion window')
}

export class RequestLedger {
  constructor() {
    this.pending = new Set()
    this.requestIds = new Set()
    this.expectedDownload = undefined
    this.expectedQuery = undefined
  }

  begin(request) {
    this.pending.add(request)
  }

  finish(request) {
    this.pending.delete(request)
  }

  assertDrained() {
    safeCheck(this.pending.size === 0, 'pending requests drained')
  }

  rememberRequestId(requestId) {
    safeCheck(typeof requestId === 'string' && requestId.length > 0, 'request ID present')
    safeCheck(!this.requestIds.has(requestId), 'request ID unique')
    this.requestIds.add(requestId)
  }

  expectDownload(body) {
    safeCheck(!this.expectedDownload, 'one expected download')
    this.expectedDownload = structuredClone(body)
  }

  expectQuery(pathname) {
    safeCheck(!this.expectedQuery, 'one expected query')
    this.expectedQuery = pathname
  }

  observeWrite(method, pathname, body) {
    safeCheck(method === 'POST' && pathname === '/api/v1/download-tasks', 'only task POST')
    safeCheck(Boolean(this.expectedDownload), 'download POST registered')
    objectWithExactKeys(body, ['submissionId', 'pluginId', 'apiName', 'mode', 'params'], 'task request')
    safeCheck(/^[0-9a-f-]{36}$/i.test(body.submissionId), 'task submission UUID')
    const { submissionId, ...actual } = body
    safeCheck(actual.pluginId === this.expectedDownload.pluginId && actual.apiName === this.expectedDownload.apiName && actual.mode === this.expectedDownload.mode && JSON.stringify(Object.entries(actual.params).sort()) === JSON.stringify(Object.entries(this.expectedDownload.params).sort()), 'task POST body')
    this.expectedDownload = undefined
  }

  observeQuery(pathname) {
    safeCheck(pathname === this.expectedQuery, 'records GET registered')
    this.expectedQuery = undefined
  }

  assertExpectedTrafficConsumed() {
    safeCheck(!this.expectedDownload && !this.expectedQuery, 'expected traffic consumed')
  }
}

export class SafeLogSink {
  constructor(secrets, write = async () => {}) {
    this.secrets = secrets.flatMap((value) => {
      const escaped = JSON.stringify(value).slice(1, -1)
      return escaped === value ? [value] : [value, escaped]
    }).map((value) => Buffer.from(value, 'utf8'))
    this.overlap = Math.max(
      0,
      ...this.secrets.map(({ length }) => length - 1),
      Buffer.byteLength('"fields":') - 1,
      Buffer.byteLength('"items":') - 1,
      Buffer.byteLength('"token":') - 1,
    )
    this.write = write
    this.buffers = new Map([['stdout', Buffer.alloc(0)], ['stderr', Buffer.alloc(0)]])
    this.queue = Promise.resolve()
    this.failed = false
    this.onFailure = () => {}
  }

  scan(bytes) {
    safeCheck(this.secrets.every((secret) => bytes.indexOf(secret) < 0), 'application log secret scan')
    safeCheck(!/"(?:fields|items|token)"\s*:/i.test(bytes.toString('utf8')), 'application log envelope scan')
    let start = 0
    for (let newline = bytes.indexOf(0x0a, start); newline >= 0; newline = bytes.indexOf(0x0a, start)) {
      safeCheck(newline + 1 - start <= MAX_LOG_LINE, 'application log line length')
      start = newline + 1
    }
    safeCheck(bytes.length - start <= MAX_LOG_LINE, 'application log line length')
  }

  enqueue(text) {
    this.queue = this.queue
      .then(() => this.write(text))
      .catch(() => {
        this.failed = true
        this.onFailure()
      })
  }

  accept(channel, chunk) {
    if (this.failed) return
    let value = Buffer.concat([this.buffers.get(channel) ?? Buffer.alloc(0), Buffer.from(chunk)])
    try {
      this.scan(value)
      const safeLimit = Math.max(0, value.length - this.overlap)
      let writeEnd = -1
      for (let newline = value.indexOf(0x0a); newline >= 0 && newline < safeLimit; newline = value.indexOf(0x0a, newline + 1)) {
        writeEnd = newline + 1
      }
      if (writeEnd > 0) {
        this.enqueue(Buffer.from(value.subarray(0, writeEnd)))
        value = Buffer.from(value.subarray(writeEnd))
      }
      this.buffers.set(channel, value)
    } catch {
      this.failed = true
      this.buffers.set(channel, Buffer.alloc(0))
      this.onFailure()
    }
  }

  async end(channel) {
    if (this.failed) return
    const value = this.buffers.get(channel) ?? Buffer.alloc(0)
    try {
      this.scan(value)
      if (value.length) this.enqueue(Buffer.from(value))
      this.buffers.set(channel, Buffer.alloc(0))
      await this.queue
    } catch {
      this.failed = true
      this.onFailure()
    }
  }

  async idle() {
    await this.queue
  }

  pendingText() {
    safeCheck(!this.failed, 'application log safety')
    return [...this.buffers.values()]
      .map((bytes) => bytes.subarray(0, bytes.lastIndexOf(0x0a) + 1))
      .filter((bytes) => bytes.length > 0)
      .map((bytes) => bytes.toString('utf8'))
      .join('')
  }
}

export async function beforeDeadline(promise, deadlineAt, name, onTimeout = () => {}) {
  const remaining = Math.max(0, deadlineAt - Date.now())
  let timer
  const settled = Promise.resolve(promise).then(
    (value) => ({ type: 'value', value }),
    (error) => ({ type: 'error', error }),
  )
  const timeout = new Promise((resolve) => {
    timer = setTimeout(() => resolve({ type: 'timeout' }), remaining)
  })
  const result = await Promise.race([settled, timeout])
  clearTimeout(timer)
  if (result.type === 'timeout') {
    try { onTimeout() } catch { /* fixed failure below */ }
    throw new Error(`Safe check failed: ${name} timeout`)
  }
  if (result.type === 'error') {
    if (result.error instanceof Error && /^Safe (?:check failed|live blocker):/.test(result.error.message)) {
      throw result.error
    }
    throw new Error(`Safe check failed: ${name}`)
  }
  return result.value
}

export async function cleanupOwnedResources(owned, deadlineAt, limits = {}) {
  const failures = []
  const run = async (operation, milliseconds, name) => {
    const phaseDeadline = Math.min(deadlineAt, Date.now() + milliseconds)
    try { await beforeDeadline(operation(), phaseDeadline, name) } catch { failures.push(new Error(`Safe check failed: ${name}`)) }
  }
  owned.cancelled = true
  if (!owned.context && owned.contextPromise) {
    await run(async () => { owned.context = await owned.contextPromise }, limits.creation ?? 10_000, 'context ownership cleanup')
  }
  if (!owned.page && owned.pagePromise) {
    await run(async () => { owned.page = await owned.pagePromise }, limits.creation ?? 10_000, 'page ownership cleanup')
  }
  if (owned.monitor) await run(() => owned.monitor.drain(Math.min(deadlineAt, Date.now() + (limits.firstDrain ?? 60_000))), limits.firstDrain ?? 60_000, 'network drain')
  if (owned.page) await run(() => owned.page.close(), limits.pageClose ?? 10_000, 'page close')
  if (owned.monitor) await run(() => owned.monitor.drain(Math.min(deadlineAt, Date.now() + (limits.secondDrain ?? 60_000))), limits.secondDrain ?? 60_000, 'post-close network drain')
  if (owned.context) await run(() => owned.context.close(), limits.contextClose ?? 15_000, 'context close')
  if (owned.monitor) monitors.delete(owned.monitor)
  if (failures.length) throw new AggregateError(failures, 'Safe cleanup failed')
}

export class RunCounters {
  constructor() {
    this.attempted = new Set()
    this.failed = new Set()
    this.completed = new Set()
    this.traffic = {
      liveDownloadPosts: 0,
      fixtureDownloadPosts: 0,
      liveRecordsGets: 0,
      fixtureRecordsGets: 0,
    }
  }

  enter(apiName) {
    this.attempted.add(apiName)
  }

  fail(apiName) {
    safeCheck(this.attempted.has(apiName), 'failed case was attempted')
    this.failed.add(apiName)
  }

  complete(apiName) {
    safeCheck(this.attempted.has(apiName) && !this.failed.has(apiName), 'completed case state')
    this.completed.add(apiName)
  }

  observe(name) {
    safeCheck(Object.hasOwn(this.traffic, name), 'observed traffic counter')
    this.traffic[name] += 1
  }

  snapshot(totalCases) {
    return {
      attemptedCases: this.attempted.size,
      failedCases: this.failed.size,
      completedCases: this.completed.size,
      unexecutedCases: totalCases - this.attempted.size,
      ...this.traffic,
    }
  }
}

const manifestPath = new URL('../../docs/data-template/manifest.json', import.meta.url)
const manifestBytes = readFileSync(manifestPath)
const { manifest, sampleCount: manifestSampleCount } = validateManifest(manifestBytes)
const requestsPath = new URL('../../docs/contracts/download-request-examples.json', import.meta.url)
const requestBytes = readFileSync(requestsPath)
const requests = validateRequests(requestBytes, manifest.interfaces)
const phase = process.env.ISSUE018_T13_PHASE ?? 'single'
function readSafeInput(name, file = process.env[name]) {
  try {
    safeCheck(path.isAbsolute(file ?? ''), 'evidence input absolute')
    const state = lstatSync(file)
    safeCheck(state.isFile() && !state.isSymbolicLink() && state.uid === process.getuid(), 'evidence input ordinary owned file')
    if (name === 'ISSUE018_T13_CASES_FILE') {
      const parent = lstatSync(path.dirname(file))
      safeCheck((state.mode & 0o777) === 0o600 && parent.isDirectory() && !parent.isSymbolicLink() && (parent.mode & 0o777) === 0o700 && parent.uid === process.getuid(), 'case plan private')
    }
    return { ...bindEvidenceInput(readFileSync(file)), path: file }
  }
  catch { throw new Error('Safe check failed: task evidence input') }
}
const casePlanInput = phase === 'range' || process.env.ISSUE018_T13_CASES_FILE !== undefined
  ? readSafeInput('ISSUE018_T13_CASES_FILE') : undefined
const evidenceIndexInput = phase === 'range' ? readSafeInput('ISSUE018_T13_EVIDENCE_INDEX_FILE') : undefined
const casePlan = casePlanInput?.value
const candidateIndex = evidenceIndexInput?.value
async function evidenceInputsUnchanged() {
  try {
    for (const [name, input] of [['ISSUE018_T13_CASES_FILE', casePlanInput],
      ['ISSUE018_T13_EVIDENCE_INDEX_FILE', evidenceIndexInput]]) {
      if (input && readSafeInput(name, input.path).sha256 !== input.sha256) return false
    }
    return true
  } catch { return false }
}
function initialTaskEvidence(candidate) {
  const source = sourceBindings.get(candidate.caseId)
  const responseOnly = candidateIndex?.interfaces.find(({ apiName }) =>
    apiName === candidate.apiName)?.completeness.kind === 'RESPONSE_ONLY'
  return safeCaseEvidence({
    ...candidate, phase: 'TASK', status: 'NOT_RUN', batchNodes: [], evidencePaths: [],
    expectedCoverage: candidate.mode === 'SINGLE' ? 'SINGLE_SAMPLE'
      : responseOnly ? 'RESPONSE_ONLY' : source.expectedCoverage,
    expectedCoverageSource: candidate.mode === 'SINGLE' ? 'current request examples'
      : responseOnly ? responseOnlyExpectedCoverageSource(source) : source.expectedCoverageSource,
  })
}
const taskRunId = casePlan?.runId ?? `issue018-t13-single-${randomUUID()}`
const sourceBindings = new Map()
const selectedCases = selectTaskCases(phase, casePlan, candidateIndex, sourceBindings).map((c) => phase === 'single' && !casePlanInput ? { ...c, caseId: `${taskRunId}-${c.caseId}` } : c)
const liveScope = phase === 'single' ? selectLiveInterfaces(manifest.interfaces, requests) : {
  interfaces: manifest.interfaces.filter((entry) => selectedCases.some((c) => c.apiName === entry.api_name))
    .map((entry) => ({ ...entry, cases: selectedCases.filter((c) => c.apiName === entry.api_name),
      params: selectedCases.filter((c) => c.apiName === entry.api_name).map((c) => c.params) })),
  sampleCount: selectedCases.length,
}
if (candidateIndex) safeCheck(candidateIndex.inputHashes.manifestSha256 === MANIFEST_SHA && candidateIndex.inputHashes.requestExamplesSha256 === REQUESTS_SHA, 'candidate input hashes')
const INTERFACES = liveScope.interfaces.map((entry) => ({ ...entry, cases: selectedCases.filter((c) => c.apiName === entry.api_name), contract: CONTRACTS.get(entry.api_name) }))

let application
let mysqlDefaultsPath
let runDeadline
let budgetTimer
let sourceRequests = 0
const downloadParameters = new WeakMap()
const ownedTaskIds = new Set()
const observedTasks = []
let logSink
let applicationLogPath
let artifactDirectory
let runDirectory
let intervalMs
let lastDownloadFinishedAt
let jarHashBefore
let runtimeFailure
let setupFailed = false
let fixturePassed = false
let artifactInitialized = false
let jarValidated = false
let evidenceWritten = false
const runCounters = new RunCounters()
const monitors = new Set()
const ledger = new RequestLedger()
const expectedEvents = new Map()
const correlatedEvents = new Map()
const evidence = {
  version: 2,
  task: 'ISSUE-018-T13',
  phase,
  runId: taskRunId,
  cases: selectedCases.map(initialTaskEvidence),
  sourceBindings: [...sourceBindings].map(([taskCaseId, source]) => ({ taskCaseId, sourceRunId: source.runId, sourceCaseId: source.caseId })),
  scope: {
    id: 'supported-apis',
    manifestCases: manifest.interfaces.length,
    manifestSamples: manifestSampleCount,
    selectedCases: INTERFACES.length,
    selectedSamples: liveScope.sampleCount,
    manifestStatuses: { ok: liveScope.okCount, empty: liveScope.emptyCount },
    interfaceStatuses: INTERFACES.map(({ api_name, status }) => ({
      apiName: api_name, manifestStatus: status,
    })),
  },
  startedAt: undefined,
  finishedAt: undefined,
  inputs: { manifestSha256: MANIFEST_SHA, requestExamplesSha256: REQUESTS_SHA, jarSha256: JAR_SHA },
  fixture: [],
  downloads: [],
  queries: [],
  totals: {},
  cleanup: {},
}

function publicEnvironment() {
  return Object.fromEntries(ENV_ALLOWLIST.flatMap((name) =>
    process.env[name] === undefined ? [] : [[name, process.env[name]]]))
}

function forbiddenValues() {
  const jdbc = process.env.TENSOR_DB_URL ?? ''
  const match = /^jdbc:mysql:\/\/([^/?#@]+)\/(tensor_m14_t05_[a-f0-9]+)(?:\?[^#]*)?$/.exec(jdbc)
  return [
    process.env.TENSOR_TUSHARE_TOKEN,
    process.env.TENSOR_DB_PASSWORD,
    process.env.TENSOR_DB_USERNAME,
    jdbc,
    match?.[1],
    match?.[2],
  ].filter((value) => typeof value === 'string' && value.length > 0)
}

function assertSafeText(text, name) {
  safeCheck(typeof text === 'string', `${name} text`)
  safeCheck(!/jdbc:mysql|"(?:token|password|username|jdbcUrl)"\s*:/i.test(text), `${name} credential keys`)
  const variants = forbiddenValues().flatMap((value) => [value, JSON.stringify(value).slice(1, -1)])
  safeCheck(variants.every((value) => !text.includes(value)), `${name} private values`)
}

async function safeJson(response, name) {
  let text
  try {
    text = await response.text()
  } catch {
    throw new Error(`Safe check failed: ${name} readable`)
  }
  assertSafeText(text, name)
  try {
    return JSON.parse(text)
  } catch {
    throw new Error(`Safe check failed: ${name} JSON`)
  }
}

async function assertPageSafe(page, name) {
  let text
  try {
    text = await page.locator('body').innerText({ timeout: 5_000 })
  } catch {
    throw new Error(`Safe check failed: ${name} visible text readable`)
  }
  assertSafeText(text, `${name} visible text`)
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function optionName(contract) {
  return new RegExp(`^${escapeRegex(contract.displayName)}\\s*${escapeRegex(contract.apiName)}$`)
}

async function sha256(file) {
  return createHash('sha256').update(await readFile(file)).digest('hex')
}

async function bounded(promise, timeout, name) {
  const timeoutValue = Symbol(name)
  const controller = new AbortController()
  try {
    const result = await Promise.race([
      promise,
      delay(timeout, timeoutValue, { signal: controller.signal, ref: false }),
    ])
    safeCheck(result !== timeoutValue, `${name} timeout`)
    return result
  } finally {
    controller.abort()
  }
}

function canConnectToPort() {
  return new Promise((resolve) => {
    const socket = createConnection({ host: '127.0.0.1', port: 8080 })
    socket.setTimeout(2_000)
    socket.once('connect', () => { socket.destroy(); resolve(true) })
    socket.once('timeout', () => { socket.destroy(); resolve(false) })
    socket.once('error', () => resolve(false))
  })
}

async function validateSqlInputs() {
  mysqlDefaultsPath = process.env.ISSUE018_T13_MYSQL_DEFAULTS_FILE
  safeCheck(path.isAbsolute(mysqlDefaultsPath ?? ''), 'SQL defaults absolute')
  const state = await lstat(mysqlDefaultsPath)
  safeCheck(state.isFile() && !state.isSymbolicLink() && (state.mode & 0o777) === 0o600 && state.uid === process.getuid(), 'SQL defaults private')
  const lines = (await readFile(mysqlDefaultsPath, 'utf8')).trim().split(/\r?\n/)
  safeCheck([7, 8].includes(lines.length) && lines.shift() === '[client]', 'SQL defaults shape')
  const values = Object.fromEntries(lines.map((line) => {
    const split = line.indexOf('=')
    safeCheck(split > 0, 'SQL defaults syntax')
    return [line.slice(0, split), line.slice(split + 1)]
  }))
  const keys = ['host', 'port', 'user', 'password', 'database', 'protocol']
  if (lines.length === 7) keys.push('default-character-set')
  objectWithExactKeys(values, keys, 'SQL defaults')
  safeCheck(lines.length === 6 || values['default-character-set'] === 'utf8mb4', 'SQL defaults charset')
  const jdbc = new URL(process.env.TENSOR_DB_URL.slice(5))
  safeCheck(values.host === jdbc.hostname && Number(values.port) === Number(jdbc.port || 3306) && `/${values.database}` === jdbc.pathname && values.user === process.env.TENSOR_DB_USERNAME && values.password === process.env.TENSOR_DB_PASSWORD && values.protocol === 'TCP', 'SQL matches application account and schema')
}

async function mysql(sql) {
  safeCheck(/^SELECT\s/i.test(sql) && !/;/.test(sql), 'read-only SQL statement')
  return new Promise((resolve, reject) => {
    const child = spawn('mysql', [`--defaults-file=${mysqlDefaultsPath}`, '--no-login-paths', '--default-character-set=utf8mb4', '--batch', '--skip-column-names', '--raw'], {
      shell: false, env: { PATH: process.env.PATH, LANG: 'C', LC_ALL: 'C' }, stdio: ['pipe', 'pipe', 'pipe'],
    })
    const chunks = []
    let size = 0, failed = false
    const rejectSafe = () => { failed = true; child.kill('SIGTERM'); reject(new Error('Safe check failed: read-only SQL')) }
    const timer = setTimeout(rejectSafe, 15_000)
    child.stdout.on('data', (chunk) => { size += chunk.length; if (size > 16 * 1024 * 1024) rejectSafe(); else chunks.push(chunk) })
    child.stderr.on('data', () => { failed = true })
    child.once('error', () => { clearTimeout(timer); rejectSafe() })
    child.once('close', (code) => {
      clearTimeout(timer)
      if (failed || code !== 0) reject(new Error('Safe check failed: read-only SQL'))
      else resolve(Buffer.concat(chunks).toString('utf8').trimEnd())
    })
    child.stdin.on('error', rejectSafe)
    child.stdin.end(`${sql};\n`)
  })
}

async function sqlSnapshot(contract, definition, stock) {
  const yaml = await readFile(new URL(`../../data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/${contract.apiName}.yaml`, import.meta.url), 'utf8')
  const metadata = sqlMetadata(yaml)
  const columns = [...definition.columns.map((c) => c.name), ...SOURCE_COLUMNS]
  safeCheck(columns.every((c) => /^[a-z][a-z0-9_]+$/.test(c)) && metadata.keys.every((c) => columns.includes(c)), 'SQL trusted column metadata')
  const quote = (name) => `\`${name}\``
  // HEX keeps tabs/newlines, NULL and decimal text exact without GROUP_CONCAT limits.
  const expressions = columns.map((name) => `IF(${quote(name)} IS NULL, 'N', CONCAT('V', HEX(CAST(${quote(name)} AS CHAR))))`)
  const rows = []
  for (let offset = 0; ; offset += 500) {
    safeCheck(Date.now() < runDeadline && offset <= 1_000_000, 'bounded SQL snapshot')
    const output = await mysql(`SELECT ${expressions.join(',')} FROM ${quote(metadata.table)} ORDER BY ${metadata.keys.map(quote).join(',')} LIMIT 500 OFFSET ${offset}`)
    const page = output ? output.split('\n').map((line) => line.split('\t').map((value) => {
      safeCheck(value === 'N' || /^V(?:[0-9A-F]{2})*$/.test(value), 'SQL canonical cell')
      return value === 'N' ? null : Buffer.from(value.slice(1), 'hex').toString('utf8')
    })) : []
    safeCheck(page.every((row) => row.length === columns.length && row[columns.indexOf('source_plugin')] === 'tushare_pro' && row[columns.indexOf('source_api')] === contract.apiName), 'SQL row source identity')
    rows.push(...page)
    if (page.length < 500) break
  }
  return summarizeSqlRows(rows, metadata.keys.map((key) => columns.indexOf(key)), columns.indexOf('ts_code'), stock)
}

async function validatePreconditions(testInfo) {
  safeCheck(path.isAbsolute(process.env.M14_T05_ARTIFACT_DIR ?? ''), 'artifact directory absolute')
  artifactDirectory = process.env.M14_T05_ARTIFACT_DIR
  const artifactState = await lstat(artifactDirectory)
  safeCheck(artifactState.isDirectory() && !artifactState.isSymbolicLink(), 'artifact directory ordinary')
  safeCheck((artifactState.mode & 0o777) === 0o700, 'artifact directory mode 0700')
  if (typeof process.getuid === 'function') safeCheck(artifactState.uid === process.getuid(), 'artifact directory owner')
  runDirectory = path.join(artifactDirectory, 'run')
  await mkdir(runDirectory, { mode: 0o700 })
  await chmod(runDirectory, 0o700)
  applicationLogPath = path.join(runDirectory, 'application.log')
  const handle = await open(applicationLogPath, 'wx', 0o600)
  await handle.close()
  artifactInitialized = true

  safeCheck(testInfo.config.workers === 1, 'single Playwright worker')
  safeCheck(testInfo.retry === 0 && testInfo.project.use.trace === 'off' && testInfo.project.use.screenshot === 'off' && testInfo.project.use.video === 'off', 'live artifacts disabled')
  safeCheck(!testInfo.config.grepInvert && String(testInfo.config.grep) === '/.*/', 'exact selected cases without grep')
  safeCheck((process.env.PLAYWRIGHT_BASE_URL ?? BASE_URL) === BASE_URL, 'isolated base URL')
  safeCheck(typeof process.env.TENSOR_TUSHARE_TOKEN === 'string' && process.env.TENSOR_TUSHARE_TOKEN.length > 0, 'live token supplied')
  for (const name of DB_ENV) safeCheck(typeof process.env[name] === 'string' && process.env[name].length > 0, `${name} supplied`)
  const jdbc = process.env.TENSOR_DB_URL
  const match = /^jdbc:mysql:\/\/([^/?#@]+)\/(tensor_m14_t05_[a-f0-9]+)(?:\?([^#]*))?$/.exec(jdbc)
  safeCheck(Boolean(match), 'dedicated JDBC schema')
  const query = new URLSearchParams(match[3] ?? '')
  safeCheck(![...query.keys()].some((key) => /^(?:user|username|password)$/i.test(key)), 'JDBC excludes credentials')
  safeCheck(!match[1].includes('@'), 'JDBC excludes embedded credentials')

  safeCheck(/^\d+$/.test(process.env.M14_T05_CALL_INTERVAL_MS ?? ''), 'call interval integer')
  intervalMs = Number(process.env.M14_T05_CALL_INTERVAL_MS)
  safeCheck(Number.isSafeInteger(intervalMs) && intervalMs >= 2_000 && intervalMs <= 3_600_000, 'call interval range')

  safeCheck(path.isAbsolute(process.env.ACCEPTANCE_JAR ?? ''), 'acceptance JAR absolute path')
  safeCheck(/^[a-f0-9]{64}$/.test(JAR_SHA ?? ''), 'expected acceptance JAR hash')
  const jarState = await lstat(process.env.ACCEPTANCE_JAR)
  safeCheck(jarState.isFile() && !jarState.isSymbolicLink(), 'acceptance JAR ordinary file')
  jarHashBefore = await sha256(process.env.ACCEPTANCE_JAR)
  safeCheck(jarHashBefore === JAR_SHA, 'acceptance JAR hash')
  jarValidated = true

  const java = await execFileAsync('java', ['-version'], { env: publicEnvironment(), timeout: 10_000 })
  const javaVersion = `${java.stdout}\n${java.stderr}`
  safeCheck(/(?:java|openjdk) version "21(?:[.\s])/.test(javaVersion), 'Java 21')
  safeCheck(!(await canConnectToPort()), 'port 8080 unused')
  await validateSqlInputs()
  safeCheck((await mysql('SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()')) === '0', 'fresh empty SQL schema')

}

function applicationEnvironment() {
  const env = publicEnvironment()
  for (const name of DB_ENV) env[name] = process.env[name]
  env.TENSOR_TUSHARE_TOKEN = process.env.TENSOR_TUSHARE_TOKEN
  env.TENSOR_TUSHARE_BASE_URL = 'https://api.tushare.pro'
  env.LOGGING_LEVEL_ORG_FLYWAYDB = 'WARN'
  return env
}

async function waitForHealth(current) {
  const deadline = Date.now() + 90_000
  while (Date.now() < deadline) {
    safeCheck(!runtimeFailure, 'application log safety')
    safeCheck(!current.closed, 'owned JVM stays alive before readiness')
    try {
      const response = await fetch(`${BASE_URL}/actuator/health`, { signal: AbortSignal.timeout(2_000) })
      const body = await safeJson(response, 'health response')
      if (response.status === 200 && body?.status === 'UP') return
    } catch (error) {
      if (error instanceof Error && error.message.startsWith('Safe check failed:')) throw error
    }
    await delay(250)
  }
  throw new Error('Safe check failed: owned JVM health timeout')
}

async function startApplication() {
  const child = spawn(
    'java',
    [
      '-jar', process.env.ACCEPTANCE_JAR,
      '--spring.profiles.active=acceptance',
      '--tensor.plugins.fixture.enabled=true',
      `--tensor.plugins.tushare-pro.min-request-interval=${intervalMs}ms`,
      '--tensor.download-tasks.max-requests-per-run=5000',
      '--tensor.download-tasks.max-run-duration=30m',
      '--server.address=127.0.0.1',
      '--server.port=8080',
    ],
    {
      cwd: runDirectory,
      env: applicationEnvironment(),
      shell: false,
      stdio: ['ignore', 'pipe', 'pipe'],
    },
  )
  const current = { child, closed: false, signalled: false }
  current.closePromise = new Promise((resolve) => {
    child.once('error', () => { current.closed = true; resolve({ error: true }) })
    child.once('close', (code, signal) => { current.closed = true; resolve({ code, signal }) })
  })
  application = current
  logSink = new SafeLogSink(forbiddenValues(), (line) => appendFile(applicationLogPath, line, { mode: 0o600 }))
  logSink.onFailure = () => {
    runtimeFailure = new Error('Safe check failed: application log safety')
    if (!current.closed && !current.signalled) {
      current.signalled = true
      current.child.kill('SIGTERM')
    }
  }
  child.stdout.on('data', (chunk) => logSink.accept('stdout', chunk))
  child.stderr.on('data', (chunk) => logSink.accept('stderr', chunk))
  child.stdout.once('end', () => { void logSink.end('stdout') })
  child.stderr.once('end', () => { void logSink.end('stderr') })
  await waitForHealth(current)
  safeCheck(await mysql("SELECT CONCAT(version, ':', success) FROM flyway_schema_history ORDER BY installed_rank") === '1:1\n2:1\n3:1\n4:1\n5:1\n6:1\n7:1\n8:1', 'eight migrations')
  safeCheck(await mysql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'") === '52', '52 business and task tables')
}

function signalOwnedApplication() {
  if (application && !application.closed && !application.signalled) {
    application.signalled = true
    application.child.kill('SIGTERM')
  }
}

async function stopApplication() {
  if (!application) return
  const current = application
  if (!current.closed && !current.signalled) {
    current.signalled = true
    safeCheck(current.child.kill('SIGTERM'), 'owned JVM SIGTERM')
  }
  if (!current.closed) await bounded(current.closePromise, 150_000, 'owned JVM stop')
  await logSink?.end('stdout')
  await logSink?.end('stderr')
  await logSink?.idle()
  safeCheck(!logSink?.failed && !runtimeFailure, 'application log safety')
  application = undefined
  safeCheck(!(await canConnectToPort()), 'port 8080 released')
}

function responsePath(response) {
  return new URL(response.url()).pathname
}

function createMonitor(page, pluginId, apiName) {
  const failures = []
  const scans = []
  const pending = new Map()
  const completions = new WeakMap()
  const downloadRequests = new WeakSet()
  let records = 0
  let downloads = 0
  page.on('pageerror', () => failures.push('page-error'))
  page.on('request', (request) => {
    pending.set(request, new Promise((resolve) => { completions.set(request, resolve) }))
    ledger.begin(request)
    try {
      const url = new URL(request.url())
      safeCheck(url.origin === BASE_URL, 'browser same origin')
      const method = request.method()
      const pathname = url.pathname
      if (method === 'POST') {
        assertSafeText(request.postData() ?? '', 'download request')
        ledger.observeWrite(method, pathname, request.postDataJSON())
        downloadRequests.add(request)
        downloads += 1
        runCounters.observe(pluginId === 'fixture' ? 'fixtureDownloadPosts' : 'liveDownloadPosts')
      } else {
        safeCheck(method === 'GET', 'browser read method')
        const normal = pathname === '/' || pathname === '/downloads' || pathname === '/datasets' ||
          pathname === '/favicon.ico' || pathname === '/vite.svg' || pathname.startsWith('/assets/')
        const metadata = pathname === '/api/v1/data-sources' ||
          pathname === `/api/v1/data-sources/${pluginId}/apis` ||
          pathname === `/api/v1/data-sources/${pluginId}/datasets` ||
          pathname === `/api/v1/data-sources/${pluginId}/datasets/${apiName}` ||
          pathname === `/api/v1/data-sources/${pluginId}/apis/${apiName}/download-capabilities`
        const recordPath = `/api/v1/data-sources/${pluginId}/datasets/${apiName}/records`
        if (pathname === recordPath) {
          ledger.observeQuery(pathname)
          records += 1
          runCounters.observe(pluginId === 'fixture' ? 'fixtureRecordsGets' : 'liveRecordsGets')
        } else safeCheck(((normal || metadata) && url.search === '') || allowedTaskRead(url, ownedTaskIds), 'browser request allowlist')
      }
    } catch {
      failures.push('request-contract')
    }
  })
  const finish = (request, failed) => {
    if (failed) failures.push('request-failed')
    completions.get(request)?.()
    pending.delete(request)
    ledger.finish(request)
  }
  page.on('requestfinished', (request) => finish(request, false))
  page.on('requestfailed', (request) => finish(request, true))
  page.on('response', (response) => {
    const pathname = responsePath(response)
    if (pathname.startsWith('/api/v1/')) {
      scans.push(response.text().then((text) => assertSafeText(text, 'API response')).catch(() => failures.push('response-safety')))
    }
    if (response.status() >= 400 && !downloadRequests.has(response.request())) failures.push('http-error')
  })
  const current = {
    records: () => records,
    downloads: () => downloads,
    async drain(deadlineAt = Date.now() + 135_000) {
      await beforeDeadline((async () => {
        while (pending.size || scans.length) {
          const currentScans = scans.splice(0)
          await Promise.all([...pending.values(), ...currentScans])
        }
      })(), deadlineAt, 'network drain')
      ledger.assertDrained()
      ledger.assertExpectedTrafficConsumed()
      safeCheck(failures.length === 0, 'browser network monitor')
    },
  }
  monitors.add(current)
  return current
}

function cancelOwned(owned) {
  owned.cancelled = true
  if (owned.context) void owned.context.close().catch(() => {})
  else if (owned.contextPromise) {
    void owned.contextPromise.then((context) => context.close()).catch(() => {})
  }
}

async function initializeOwnedPage(browser, owned, deadlineAt, pluginId, apiName) {
  owned.contextPromise = browser.newContext({ viewport: { width: 1440, height: 1000 } })
  void owned.contextPromise.then((context) => {
    owned.context = context
    if (owned.cancelled) return context.close()
  }).catch(() => {})
  owned.context = await beforeDeadline(
    owned.contextPromise, deadlineAt, 'browser context creation', () => cancelOwned(owned),
  )
  owned.pagePromise = owned.context.newPage()
  void owned.pagePromise.then((page) => {
    owned.page = page
    if (owned.cancelled) return page.close()
  }).catch(() => {})
  owned.page = await beforeDeadline(
    owned.pagePromise, deadlineAt, 'browser page creation', () => cancelOwned(owned),
  )
  owned.monitor = createMonitor(owned.page, pluginId, apiName)
}

async function drainAllMonitors() {
  const failures = []
  for (const monitor of monitors) {
    try { await monitor.drain() } catch { failures.push(new Error('Safe check failed: remaining network drain')) }
  }
  if (failures.length) throw new AggregateError(failures, 'Safe network cleanup failed')
}

async function openRoute(page, route, heading) {
  await drainAllMonitors()
  const response = await page.goto(route)
  safeCheck(response?.status() === 200, 'page route status')
  await assertPageSafe(page, 'page route')
  await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible()
}

async function selectOption(page, label, name) {
  if (label === '数据接口') return selectDownloadApi(page, name)
  const combobox = page.getByRole('combobox', { name: label, exact: true })
  if (await combobox.evaluate(el => el.tagName === 'SELECT')) return combobox.selectOption(name)
  await combobox.focus()
  await combobox.press('Enter')
  const option = page.getByRole('option', { name, exact: typeof name === 'string' })
  await option.scrollIntoViewIfNeeded()
  await expect(option).toBeVisible()
  await option.click()
  await expect(combobox).toHaveAttribute('aria-expanded', 'false')
}

function isGet(response, pathname) {
  return response.request().method() === 'GET' && responsePath(response) === pathname
}

async function chooseDownload(page, pluginId, contract) {
  const display = pluginId === 'fixture' ? 'Fixture' : 'Tushare Pro'
  const apisPath = `/api/v1/data-sources/${pluginId}/apis`
  const apisPromise = page.waitForResponse((response) => isGet(response, apisPath))
  await selectOption(page, '数据源', display)
  const apisResponse = await apisPromise
  safeCheck(apisResponse.status() === 200, 'API descriptor status')
  const apis = await safeJson(apisResponse, 'API descriptors')
  safeCheck(Array.isArray(apis), 'API descriptors array')
  if (pluginId === 'tushare_pro') {
    expect(apis.map(({ apiName }) => apiName).sort()).toEqual([...SUPPORTED_API_NAMES].sort())
  }
  const descriptor = apis.find(({ apiName }) => apiName === contract.apiName)
  safeCheck(Boolean(descriptor), 'selected API descriptor')
  safeCheck(
    JSON.stringify(descriptor.parameters.map(({ name }) => name)) === JSON.stringify(contract.parameters),
    'selected API parameter order',
  )
  const capabilityPath = `/api/v1/data-sources/${pluginId}/apis/${contract.apiName}/download-capabilities`
  const capabilityPromise = page.waitForResponse((response) => isGet(response, capabilityPath))
  await selectOption(page, '数据接口', pluginId === 'fixture' ? FIXTURE_OPTION : optionName(contract))
  const capabilityResponse = await capabilityPromise
  const wireCapability = await safeJson(capabilityResponse, 'capabilities')
  safeCheck(capabilityResponse.status() === 200, 'capability status')
  const capability = parseDownloadCapabilities(
    wireCapability, capabilityResponse.headers()['x-request-id'])
  const mode = pluginId === 'fixture' ? 'SINGLE' : phase.toUpperCase()
  if (mode === 'RANGE') {
    const candidate = candidateIndex.interfaces.find((c) => c.apiName === contract.apiName)
    const expectedRule = { ROW_LIMIT: 'CONFIRMED_ROW_LIMIT', CALENDAR_COVERAGE: 'VERIFIED_RULE',
      RESPONSE_ONLY: 'RESPONSE_ONLY' }[candidate.completeness.kind]
    const rowLimit = capability.range.completenessRule.rowLimit
    safeCheck(capability.range.availability === 'AVAILABLE'
      && capability.range.dateAxis === candidate.dateAxis
      && capability.range.policyVersion === candidate.policyVersion
      && capability.range.planningMode === candidate.planningMode
      && capability.range.completenessRule.kind === expectedRule
      && (typeof rowLimit === 'bigint' ? rowLimit === BigInt(candidate.completeness.rowLimit)
        : rowLimit === candidate.completeness.rowLimit)
      && (expectedRule !== 'RESPONSE_ONLY' || !capability.range.splittable),
    'runtime RANGE matches candidate')
  } else safeCheck(capability.single.available, 'runtime SINGLE available')
  const modeLabel = mode === 'RANGE' ? '批量下载' : '单次下载'
  await page.getByRole('button', { name: modeLabel, exact: true }).click()
  await expect(page.getByRole('button', { name: modeLabel, exact: true })).toHaveAttribute('aria-pressed', 'true')
  downloadParameters.set(page, capability[mode.toLowerCase()].parameters)
  await assertPageSafe(page, 'download selection')
  const region = page.locator('.selected-api-heading')
  await expect(region.getByText(contract.apiName, { exact: true })).toBeVisible()
}

async function openDownloadFromDataset(page, pluginId, contract) {
  await drainAllMonitors()
  const sourcesPromise = page.waitForResponse((response) => isGet(response, '/api/v1/data-sources'))
  // Navigation links include indices, and KeepAlive reactivation does not refresh metadata.
  await openRoute(page, '/downloads', '数据下载')
  const sourcesResponse = await sourcesPromise
  safeCheck(sourcesResponse.status() === 200, 'data source status')
  const sources = await safeJson(sourcesResponse, 'data sources')
  safeCheck(Array.isArray(sources), 'data sources array')
  const source = sources.find(({ pluginId: id }) => id === pluginId)
  safeCheck(Boolean(source), 'selected data source')
  if (pluginId === 'tushare_pro') {
    safeCheck(source.credentialConfigured === true && source.downloadAvailable === true, 'Tushare configured for download')
  }
  await chooseDownload(page, pluginId, contract)
}

async function doubleAnimationFrame(page) {
  await page.evaluate(() => new Promise((resolve) =>
    requestAnimationFrame(() => requestAnimationFrame(resolve))))
}

async function openDataset(page, monitor, pluginId, contract) {
  await monitor.drain()
  const recordsBefore = monitor.records()
  const sourcesPromise = page.waitForResponse((response) => isGet(response, '/api/v1/data-sources'))
  await openRoute(page, '/datasets', '数据查看')
  const sourcesResponse = await sourcesPromise
  safeCheck(sourcesResponse.status() === 200, 'data source status')
  await safeJson(sourcesResponse, 'data sources')

  const datasetsPath = `/api/v1/data-sources/${pluginId}/datasets`
  const datasetsPromise = page.waitForResponse((response) => isGet(response, datasetsPath))
  await selectOption(page, '数据源', pluginId === 'fixture' ? 'Fixture' : 'Tushare Pro')
  const datasetsResponse = await datasetsPromise
  safeCheck(datasetsResponse.status() === 200, 'dataset summaries status')
  const datasets = await safeJson(datasetsResponse, 'dataset summaries')
  safeCheck(Array.isArray(datasets) && datasets.some(({ apiName }) => apiName === contract.apiName), 'selected dataset summary')
  if (pluginId === 'tushare_pro') {
    expect(datasets.map(({ apiName }) => apiName).sort()).toEqual([...SUPPORTED_API_NAMES].sort())
  }

  const definitionPath = `${datasetsPath}/${contract.apiName}`
  const definitionPromise = page.waitForResponse((response) => isGet(response, definitionPath))
  await selectOption(page, '数据集', pluginId === 'fixture' ? FIXTURE_OPTION : optionName(contract))
  const definitionResponse = await definitionPromise
  safeCheck(definitionResponse.status() === 200, 'dataset definition status')
  const definition = await safeJson(definitionResponse, 'dataset definition')
  safeCheck(definition.pluginId === pluginId && definition.apiName === contract.apiName, 'dataset definition identity')
  safeCheck(Array.isArray(definition.columns) && definition.columns.length === contract.columns, 'dataset business column count')
  safeCheck(new Set(definition.columns.map(({ name }) => name)).size === contract.columns, 'dataset business columns unique')
  safeCheck(definition.columns.every(({ name }) => !SOURCE_COLUMNS.includes(name)), 'dataset definition business columns only')
  await assertPageSafe(page, 'dataset selection')
  await expect(page.getByRole('heading', { name: '设置筛选条件后查询' })).toBeVisible()
  const filterFields = pluginId === 'fixture' ? ['ts_code'] : contract.filters
  for (const label of filterFields.flatMap((field) => FILTER_LABELS[field])) {
    safeCheck(await page.getByLabel(label, { exact: true }).inputValue() === '', 'dataset filter initially empty')
  }
  await doubleAnimationFrame(page)
  safeCheck(monitor.records() === recordsBefore, 'no automatic records request')
  return definition
}

function validatePageBody(response, body, pluginId, apiName, requestId) {
  safeCheck(response.status() === 200, 'records HTTP status')
  objectWithExactKeys(body, PAGE_KEYS, 'page response')
  safeCheck(response.headers()['x-request-id'] === body.requestId, 'records header request ID')
  ledger.rememberRequestId(body.requestId)
  safeCheck(body.requestId !== requestId, 'records request ID fresh')
  safeCheck(body.pluginId === pluginId && body.apiName === apiName, 'records identity')
  safeCheck(
    integer(body.page) && integer(body.pageSize) && integer(body.totalElements) && integer(body.totalPages),
    'records page counts',
  )
  safeCheck(body.page === 1 && body.pageSize === 50, 'records page request')
  safeCheck(Array.isArray(body.columns) && Array.isArray(body.items), 'records arrays')
  safeCheck(body.totalPages === Math.ceil(body.totalElements / body.pageSize), 'records total pages')
}

function eventFieldSet() {
  return ['requestId', 'operation', 'pluginId', 'apiName', 'filterNames', 'page', 'pageSize',
    'resultCount', 'totalElements', 'durationMs', 'outcome', 'failureStage', 'errorCode']
}

function parseCompletedEvent(line, marker = 'tensor.operation.completed') {
  const index = line.indexOf(marker)
  safeCheck(index >= 0, 'completion event marker')
  const text = line.slice(index + marker.length).trim()
  const fields = {}
  const pattern = /([A-Za-z]+)=(\[[^\]]*\]|\S+)/g
  let cursor = 0
  for (const match of text.matchAll(pattern)) {
    safeCheck(text.slice(cursor, match.index).trim() === '', 'completion event tokens')
    safeCheck(!Object.hasOwn(fields, match[1]), 'completion event unique fields')
    fields[match[1]] = match[2]
    cursor = match.index + match[0].length
  }
  safeCheck(text.slice(cursor).trim() === '', 'completion event trailing text')
  return fields
}

async function correlateEvent(expected) {
  expectedEvents.set(expected.requestId, expected)
  let matching = []
  await expect.poll(async () => {
    await logSink.idle()
    safeCheck(!logSink.failed && !runtimeFailure, 'application log safety')
    const text = `${await readFile(applicationLogPath, 'utf8')}\n${logSink.pendingText()}`
    assertSafeText(text, 'application log')
    matching = text.split(/\r?\n/).filter((line) =>
      line.includes('tensor.operation.completed') &&
      new RegExp(`requestId=${escapeRegex(expected.requestId)}(?:\\s|$)`).test(line))
    return matching.length
  }, { timeout: 5_000, message: 'safe completion event poll' }).toBe(1)
  const event = parseCompletedEvent(matching[0])
  safeCheck(
    JSON.stringify(Object.keys(event).sort()) === JSON.stringify(eventFieldSet(expected.operation).sort()),
    'completion event exact fields',
  )
  for (const [key, value] of Object.entries(expected)) {
    safeCheck(event[key] === String(value), `completion event ${key}`)
  }
  safeCheck(integer(Number(event.durationMs)), 'completion duration')
  correlatedEvents.set(expected.requestId, event)
  return Number(event.durationMs)
}

async function queryDataset(page, monitor, pluginId, contract, definition, { code, caseId, position } = {}) {
  const pathname = `/api/v1/data-sources/${pluginId}/datasets/${contract.apiName}/records`
  const priorRecords = monitor.records()
  safeCheck(priorRecords >= 0, 'records counter')
  if (code) await page.getByLabel('证券代码', { exact: true }).fill(code)
  ledger.expectQuery(pathname)
  const responsePromise = page.waitForResponse((response) => isGet(response, pathname), { timeout: 30_000 })
  await page.getByRole('button', { name: '查询', exact: true }).click()
  const response = await responsePromise
  const body = await safeJson(response, 'records response')
  if (response.status() !== 200) {
    validateApiError(body)
    const failure = safeRecordsQueryFailure(body, { pluginId, apiName: contract.apiName, caseId, position,
      httpStatus: response.status(), requestId: response.headers()['x-request-id'] })
    ledger.rememberRequestId(body.requestId)
    evidence.queryFailures ??= []
    evidence.queryFailures.push(failure)
    throw new Error('Safe check failed: records query failed')
  }
  validatePageBody(response, body, pluginId, contract.apiName)
  safeCheck(
    JSON.stringify(body.columns) === JSON.stringify([
      ...definition.columns.map(({ name }) => name), ...SOURCE_COLUMNS,
    ]),
    'records columns match definition',
  )
  for (const row of body.items) validateBusinessRow(row, body.columns, pluginId, contract.apiName, 0, Date.now())
  const url = new URL(response.url())
  const expectedQuery = code
    ? [['page', '1'], ['pageSize', '50'], ['tsCode', code]]
    : [['page', '1'], ['pageSize', '50']]
  safeCheck(
    JSON.stringify([...url.searchParams.entries()].sort()) === JSON.stringify(expectedQuery.sort()),
    'records query string',
  )
  safeCheck(monitor.records() === priorRecords + 1, 'one records request')
  const projection = {
    apiName: contract.apiName, caseId, position,
    outcome: 'SUCCESS',
    totalElements: body.totalElements,
    resultCount: body.items.length,
    requestId: body.requestId,
  }
  evidence[pluginId === 'fixture' ? 'fixture' : 'queries'].push(projection)
  const durationMs = await correlateEvent({
    requestId: body.requestId,
    operation: 'query',
    pluginId,
    apiName: contract.apiName,
    filterNames: code ? '[ts_code]' : '[]',
    page: 1,
    pageSize: 50,
    resultCount: body.items.length,
    totalElements: body.totalElements,
    outcome: 'success',
    failureStage: 'none',
    errorCode: 'none',
  })
  projection.durationMs = durationMs
  return body
}

function compactDisplayValue(type, value) {
  if (type === 'DATE' || type === 'DATE_RANGE_MEMBER') {
    return `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6, 8)}`
  }
  return value
}

async function fillSample(page, contract, sample) {
  const descriptors = downloadParameters.get(page)
  safeCheck(Array.isArray(descriptors), 'selected capability parameters')
  for (const name of Object.keys(sample)) {
    const parameter = descriptors.find((descriptor) => descriptor.name === name)
    safeCheck(Boolean(parameter), 'fixed sample parameter in selected capability')
    const value = sample[name]
    if (parameter.type === 'ENUM') {
      await selectOption(page, parameter.label, value)
      continue
    }
    const control = page.getByLabel(new RegExp(`^${escapeRegex(parameter.label)}\\s*\\*?$`))
    const displayed = compactDisplayValue(parameter.type, value)
    await control.fill(displayed)
    await control.press('Tab')
    if (['DATE', 'DATE_RANGE_MEMBER'].includes(parameter.type)) await page.keyboard.press('Escape')
    safeCheck(await control.inputValue() === displayed, 'parameter displayed value')
  }
}

async function rateLimit() {
  if (lastDownloadFinishedAt === undefined) return
  const remaining = intervalMs - (Date.now() - lastDownloadFinishedAt)
  if (remaining > 0) await delay(remaining)
  safeCheck(Date.now() - lastDownloadFinishedAt >= intervalMs, 'live call interval')
}

async function readTaskApi(page, pathname, parser, context) {
  safeCheck(context && ownedTaskIds.has(context.taskId)
    && pathname.startsWith(`/api/v1/download-tasks/${context.taskId}`)
    && allowedTaskRead(new URL(pathname, BASE_URL), ownedTaskIds), 'owned task query')
  const result = await page.evaluate(async (route) => {
    const requestId = crypto.randomUUID()
    const response = await fetch(route, { headers: { 'X-Request-Id': requestId } })
    return { status: response.status, requestId, header: response.headers.get('X-Request-Id'), text: await response.text() }
  }, pathname)
  assertSafeText(result.text, 'task API response')
  if (result.status !== 200) {
    let failure = { operation: 'TASK_QUERY', httpStatus: result.status, requestId: null, errorCode: null }
    try {
      failure = safeTaskQueryFailure(JSON.parse(result.text), { ...context, pathname,
        httpStatus: result.status, requestId: result.requestId, responseRequestId: result.header })
    } catch {}
    evidence.queryFailures ??= []
    evidence.queryFailures.push(failure)
  }
  safeCheck(result.status === 200 && result.header === result.requestId, 'task query response')
  ledger.rememberRequestId(result.requestId)
  let body
  try { body = parser(JSON.parse(result.text), result.requestId) }
  catch { throw new Error('Safe check failed: task query contract') }
  return body
}

async function assertResponseOnlyPage(page, sourceRows) {
  const detail = page.locator('.task-detail')
  const outcome = sourceRows > 0n ? '返回记录已采集' : '本次请求未返回记录'
  await expect(detail.getByText(outcome, { exact: true })).toBeVisible()
  await expect(detail.getByText('数据完整性未确认，可能存在上游截断', { exact: true })).toBeVisible()
}

async function submitDownload(page, monitor, pluginId, contract, params, fixture = false, evidenceCase) {
  if (!fixture) await rateLimit()
  safeCheck(Date.now() < runDeadline && sourceRequests < 5000, 'shared task run budget')
  const expectedBody = { pluginId, apiName: contract.apiName, mode: fixture ? 'SINGLE' : phase.toUpperCase(), params }
  ledger.expectDownload(expectedBody)
  const beforePosts = monitor.downloads()
  const responsePromise = page.waitForResponse((response) => response.request().method() === 'POST' && responsePath(response) === '/api/v1/download-tasks', { timeout: 30_000 })
  const startedAt = Date.now()
  await page.getByRole('button', { name: /^(开始(?:批量)?下载|正在创建…|正在查找…)$/, exact: true }).click()
  const response = await responsePromise
  const receipt = validateTaskAcceptance(response.request().postDataJSON(), expectedBody, {
    status: response.status(), location: response.headers().location,
    requestId: response.headers()['x-request-id'], body: await safeJson(response, 'task receipt'),
  })
  safeCheck(response.request().headers()['x-request-id'] === receipt.requestId, 'task outgoing request ID')
  ledger.rememberRequestId(receipt.requestId)
  safeCheck(!ownedTaskIds.has(receipt.taskId), 'new unique task')
  ownedTaskIds.add(receipt.taskId)
  safeCheck(monitor.downloads() === beforePosts + 1, 'one task POST')
  const submissionId = response.request().postDataJSON().submissionId
  if (evidenceCase) Object.assign(evidenceCase, { taskId: receipt.taskId, submissionId })
  const queryContext = { pluginId, apiName: contract.apiName, taskId: receipt.taskId, caseId: evidenceCase?.caseId ?? null }
  const observed = { ...expectedBody, submissionId, requestId: receipt.requestId, taskId: receipt.taskId, task: null, batches: [] }
  observedTasks.push(observed)
  const panel = page.locator('.download-feedback')
  await expect(panel.getByRole('heading', { name: '任务已接收' })).toBeVisible()
  await expect(page.getByRole('combobox', { name: '数据源', exact: true })).toBeEnabled()
  await expect(page.getByRole('searchbox', { name: '搜索接口' })).toBeEnabled()
  await monitor.drain()
  await panel.getByRole('link', { name: '查看任务', exact: true }).click()
  await expect(page).toHaveURL(`/downloads/tasks/${receipt.taskId}`)
  const candidate = !fixture && phase === 'range'
    ? candidateIndex.interfaces.find(({ apiName }) => apiName === contract.apiName) : null
  let task
  do {
    safeCheck(Date.now() < runDeadline && !runtimeFailure, 'shared 30 minute task deadline')
    task = await readTaskApi(page, `/api/v1/download-tasks/${receipt.taskId}`, parseDownloadTask, queryContext)
    observed.task = task
    observed.observedAt = new Date().toISOString()
    const requestCount = evidenceInteger(task.requestCount, 5000)
    if (evidenceCase) evidenceCase.requestCount = requestCount
    safeCheck(task.taskId === receipt.taskId && task.submissionId === submissionId && task.pluginId === pluginId && task.apiName === contract.apiName && task.mode === expectedBody.mode && JSON.stringify(Object.entries(task.params).sort()) === JSON.stringify(Object.entries(params).sort()), 'task identity')
    if (sourceRequests + requestCount >= 5000) {
      runtimeFailure = new Error('Safe check failed: shared source request budget')
      signalOwnedApplication()
      throw runtimeFailure
    }
    if (['QUEUED', 'RUNNING'].includes(task.status)) await delay(1000)
  } while (['QUEUED', 'RUNNING'].includes(task.status))
  const requestCount = evidenceInteger(task.requestCount, 5000)
  if (sourceRequests + requestCount >= 5000) {
    runtimeFailure = new Error('Safe check failed: shared source budget exhausted')
    signalOwnedApplication()
  }
  sourceRequests += requestCount
  const batches = observed.batches
  let total
  for (let number = 1; ; number += 1) {
    const batchPage = await readTaskApi(page, `/api/v1/download-tasks/${receipt.taskId}/batches?page=${number}&pageSize=100&includeSplit=true`, parseDownloadBatchPage, queryContext)
    const pageTotal = evidenceInteger(batchPage.total, 5000)
    total ??= pageTotal
    safeCheck(batchPage.page === number && batchPage.pageSize === 100 && pageTotal === total
      && batchPage.items.length === Math.min(100, total - batches.length),
    'complete stable batch pages')
    batches.push(...batchPage.items)
    if (batches.length === total) break
  }
  const summary = summarizeTaskBatches(task, batches)
  if (expectedBody.mode === 'SINGLE' && task.status === 'SUCCEEDED') safeCheck(requestCount === 1 && summary.leafCount === 1 && batches[0].attemptCount === 1, 'one SINGLE source attempt')
  const body = { apiName: contract.apiName, outcome: task.status === 'SUCCEEDED'
    ? summary.sourceRowCount === 0 ? 'EMPTY' : 'SUCCESS' : task.status,
    taskId: receipt.taskId, submissionId, requestId: receipt.requestId, requestCount,
    ...summary, status: task.status, errorCode: task.lastError?.code ?? null }
  evidence[fixture ? 'fixture' : 'downloads'].push(body)
  if (evidenceCase && task.status === 'FAILED') Object.assign(evidenceCase,
    safeCaseEvidence({ ...evidenceCase, ...body, status: 'FAILED' }))
  if (candidate) validateTaskRuntime(candidate, params, task, batches)
  if (!fixture) lastDownloadFinishedAt = Date.now()
  await assertPageSafe(page, 'task result')
  if (task.status === 'SUCCEEDED') {
    safeCheck(task.planReady && task.lastError === null, 'successful planned task')
    const statusLabel = candidate?.completeness.kind === 'RESPONSE_ONLY'
      ? task.counts.sourceRows > 0n ? '返回记录已采集' : '本次请求未返回记录'
      : '已成功'
    await expect(page.locator('.task-detail [data-task-status]')).toHaveText(statusLabel)
    safeCheck(!task.canRetry && !task.canResume, 'successful task cannot be replayed')
    const progress = page.locator('.task-detail')
    await expect(progress.locator(':scope > .confirmation dd').last()).toHaveText(task.counts.sourceRows.toString())
    await progress.locator('.task-detail__record summary').click()
    await expect(progress.getByText(`新增记录次数 ${task.counts.insertedRows}`, { exact: true })).toBeVisible()
    await expect(progress.getByText(`更新记录次数 ${task.counts.updatedRows}`, { exact: true })).toBeVisible()
    if (candidate?.completeness.kind === 'RESPONSE_ONLY') {
      await assertResponseOnlyPage(page, task.counts.sourceRows)
    }
  }
  await monitor.drain()
  return { body, task, startedAt, finishedAt: Date.now() }
}

function shanghaiTimestamp(value) {
  const date = new Date(value)
  safeCheck(!Number.isNaN(date.valueOf()) && /(?:Z|[+-]\d{2}:\d{2})$/i.test(value), 'timestamp with offset')
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(date).map(({ type, value: part }) => [type, part]))
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`
}

async function assertVisibleRow(page, definition, row) {
  await assertPageSafe(page, 'visible business row')
  const sourceLabels = { source_plugin: '来源插件', source_api: '来源接口', ingested_at: '入库时间' }
  const headerTexts = [...definition.columns, ...SOURCE_COLUMNS.map((name) => ({ name, label: sourceLabels[name] }))]
    .map(({ name, label }) => `${label}${name}`)
  safeCheck(
    JSON.stringify((await page.getByRole('columnheader').allTextContents()).map((v) => v.replace(/\s+/g, ''))) === JSON.stringify(headerTexts.map((v) => v.replace(/\s+/g, ''))),
    'visible column labels',
  )
  const rows = page.getByRole('row')
  safeCheck(await rows.count() >= 2, 'visible business row exists')
  const cells = rows.nth(1).getByRole('cell')
  const names = [...definition.columns.map(({ name }) => name), ...SOURCE_COLUMNS]
  const expected = names.map((name) => {
    if (name === 'ingested_at') return shanghaiTimestamp(row[name])
    if (['change', 'pct_chg'].includes(name) && row[name] !== null && !String(row[name]).startsWith('-') && /[1-9]/.test(row[name])) return `+${row[name]}`
    return row[name] === null ? '--' : row[name]
  })
  const actual = await cells.allTextContents()
  safeCheck(JSON.stringify(actual) === JSON.stringify(expected), 'visible business row values')
  await cells.nth((await cells.count()) - 1).scrollIntoViewIfNeeded()
}

async function runFixture(browser) {
  const contract = { apiName: 'fixture_daily', displayName: 'Fixture 日线', parameters: ['scenario'], columns: 4 }
  const owned = { cancelled: false }
  const cleanupDeadline = Date.now() + 120_000
  const workDeadline = cleanupDeadline - 45_000
  let primary
  try {
    await beforeDeadline((async () => {
      await initializeOwnedPage(browser, owned, workDeadline, 'fixture', contract.apiName)
      const { page, monitor } = owned
      const definition = await openDataset(page, monitor, 'fixture', contract)
      let body = await queryDataset(page, monitor, 'fixture', contract, definition, { caseId: 'fixture-success', position: 'BEFORE' })
      safeCheck(body.totalElements === 0 && body.items.length === 0, 'fixture initially empty')

      await openDownloadFromDataset(page, 'fixture', contract)
      const scenario = page.getByRole('combobox', { name: /场景/ })
      await scenario.focus()
      await scenario.press('Enter')
      await expect(page.getByRole('option', { name: 'SUCCESS', exact: true, selected: true })).toBeVisible()
      await scenario.press('Escape')
      const success = await submitDownload(page, monitor, 'fixture', contract, { scenario: 'SUCCESS' }, true)
      safeCheck(
        success.body.outcome === 'SUCCESS' && success.body.sourceRowCount === 1 &&
        success.body.insertedRows === 1 && success.body.updatedRows === 0,
        'fixture SUCCESS counts',
      )

      const afterSuccessDefinition = await openDataset(page, monitor, 'fixture', contract)
      body = await queryDataset(page, monitor, 'fixture', contract, afterSuccessDefinition, { code: '000001.SZ', caseId: 'fixture-success', position: 'AFTER' })
      safeCheck(JSON.stringify(body.columns) === JSON.stringify(FIXTURE_COLUMNS), 'fixture columns')
      safeCheck(body.totalElements === 1 && body.items.length === 1, 'fixture SUCCESS row')
      const row = body.items[0]
      objectWithExactKeys(row, FIXTURE_COLUMNS, 'fixture row')
      safeCheck(
        row.ts_code === '000001.SZ' && row.trade_date === '2026-08-07' &&
        row.amount === '11.230000000000000000' && row.note === null &&
        row.source_plugin === 'fixture' && row.source_api === 'fixture_daily',
        'fixture row contract',
      )
      validateBusinessRow(
        row, FIXTURE_COLUMNS, 'fixture', 'fixture_daily', success.startedAt, success.finishedAt,
      )
      await assertVisibleRow(page, afterSuccessDefinition, row)

      await openDownloadFromDataset(page, 'fixture', contract)
      await selectOption(page, /场景/, 'EMPTY')
      const empty = await submitDownload(page, monitor, 'fixture', contract, { scenario: 'EMPTY' }, true)
      safeCheck(empty.body.outcome === 'EMPTY', 'fixture EMPTY outcome')

      const afterEmptyDefinition = await openDataset(page, monitor, 'fixture', contract)
      const afterEmpty = await queryDataset(page, monitor, 'fixture', contract, afterEmptyDefinition, { caseId: 'fixture-empty', position: 'AFTER' })
      safeCheck(afterEmpty.totalElements === 1 && afterEmpty.items.length === 1, 'fixture row retained')
      safeCheck(JSON.stringify(afterEmpty.items[0]) === JSON.stringify(row), 'fixture row unchanged')
      await assertVisibleRow(page, afterEmptyDefinition, row)
    })(), workDeadline, 'fixture preparation', () => cancelOwned(owned))
  } catch (error) {
    primary = error instanceof Error && error.message.startsWith('Safe')
      ? error
      : new Error('Safe check failed: fixture preparation')
    signalOwnedApplication()
  }
  let cleanupFailure
  try {
    await cleanupOwnedResources(owned, cleanupDeadline, {
      creation: 5_000, firstDrain: 15_000, pageClose: 5_000,
      secondDrain: 15_000, contextClose: 5_000,
    })
  } catch {
    cleanupFailure = new Error('Safe check failed: fixture cleanup')
  }
  if (primary && cleanupFailure) throw new AggregateError([primary, cleanupFailure], 'Safe fixture failure and cleanup failure')
  if (primary) throw primary
  if (cleanupFailure) throw cleanupFailure
  fixturePassed = true
}

function liveCaseTimeoutMs(sampleCount) {
  return phase === 'range' ? 1_980_000 : 240_000 + sampleCount * (150_000 + (intervalMs ?? 2000))
}

async function runLiveInterface(browser, entry) {
  const contract = entry.contract
  runCounters.enter(contract.apiName)
  const outerTimeout = liveCaseTimeoutMs(entry.params.length)
  const cleanupDeadline = Date.now() + outerTimeout - 5_000
  const workDeadline = cleanupDeadline - 150_000
  const owned = { cancelled: false }
  let primary
  try {
    await beforeDeadline((async () => {
      await initializeOwnedPage(browser, owned, workDeadline, 'tushare_pro', contract.apiName)
      const { page, monitor } = owned
      for (let index = 0; index < entry.cases.length; index += 1) {
        const sample = entry.cases[index]
        const evidenceCase = evidence.cases.find((c) => c.caseId === sample.caseId)
        const candidate = typeof candidateIndex === 'undefined' ? null
          : candidateIndex.interfaces.find(({ apiName }) => apiName === contract.apiName)
        const responseOnly = candidate?.completeness.kind === 'RESPONSE_ONLY'
        const binding = responseOnly ? sourceBindings.get(sample.caseId) : null
        const boundSource = responseOnly ? candidateIndex.runs
          .find(({ runId }) => runId === binding?.runId)?.cases
          .find(({ caseId }) => caseId === binding?.caseId) : null
        if (responseOnly) safeCheck(boundSource?.phase === 'SOURCE'
          && boundSource.status === 'PASS' && boundSource.apiName === sample.apiName
          && JSON.stringify(Object.entries(boundSource.params).sort())
            === JSON.stringify(Object.entries(sample.params).sort()),
        'bound RESPONSE_ONLY source')
        const definition = await openDataset(page, monitor, 'tushare_pro', contract)
        const initial = await queryDataset(page, monitor, 'tushare_pro', contract, definition, { code: sample.params.ts_code, caseId: sample.caseId, position: 'BEFORE' })
        const before = await sqlSnapshot(contract, definition, sample.params.ts_code)
        evidence.sql ??= []
        const sqlEvidence = { caseId: sample.caseId, before, after: null }
        evidence.sql.push(sqlEvidence)
        safeCheck(initial.totalElements === before.selectedCount, 'initial browser and SQL selected count')
        await openDownloadFromDataset(page, 'tushare_pro', contract)
        await fillSample(page, contract, sample.params)
        Object.assign(evidenceCase, { status: 'EVIDENCE_MISSING', reviewMethod: 'task API plus read-only SQL', evidencePaths: ['safe-results.json'] })
        const result = await submitDownload(page, monitor, 'tushare_pro', contract, sample.params, false, evidenceCase)
        const after = await sqlSnapshot(contract, definition, sample.params.ts_code)
        Object.assign(evidenceCase, safeCaseEvidence({ ...evidenceCase, ...result.body,
          status: result.task.lastError ? 'FAILED' : 'EVIDENCE_MISSING',
          sqlBeforeKeyCount: before.keyCount, sqlAfterKeyCount: after.keyCount,
          ownershipSummary: after.ownershipSummary, businessKeyDigest: after.keyDigest,
          reviewMethod: responseOnly
            ? 'verified persisted RESPONSE_ONLY policy/rule, one full-range request and successful leaf, source/write counts, read-only SQL, page outcome, and unconfirmed-completeness notice'
            : evidenceCase.reviewMethod,
        }))
        sqlEvidence.after = after
        safeCheck(after.otherStockDigest === before.otherStockDigest, 'other-stock history unchanged')
        safeCheck(after.keyCount === before.keyCount + result.body.insertedRows, 'SQL key growth matches inserted facts')
        if (sample.params.ts_code) safeCheck(after.selectedCount === before.selectedCount + result.body.insertedRows, 'all inserts belong to selected stock')
        const finalDefinition = await openDataset(page, monitor, 'tushare_pro', contract)
        const finalBody = await queryDataset(page, monitor, 'tushare_pro', contract, finalDefinition, { code: sample.params.ts_code, caseId: sample.caseId, position: 'AFTER' })
        safeCheck(finalBody.totalElements === after.selectedCount, 'final browser and SQL selected count')
        safeCheck(!sample.params.ts_code || finalBody.items.every((row) => row.ts_code === sample.params.ts_code), 'visible rows belong to selected stock')
        if (finalBody.items.length) await assertVisibleRow(page, finalDefinition, finalBody.items[0])
        else await expect(page.getByText('未找到符合条件的数据')).toBeVisible()
        safeCheck(result.task.status === 'SUCCEEDED', 'task terminal success')
        const completed = { ...evidenceCase,
          status: responseOnly && boundSource.sourceRowCount > 0
            && result.body.sourceRowCount === 0 ? 'EVIDENCE_MISSING' : 'PASS' }
        validateEvidenceCase(completed)
        Object.assign(evidenceCase, completed)
      }
      await monitor.drain(Math.min(workDeadline, Date.now() + 135_000))
    })(), workDeadline, 'live interface work', () => cancelOwned(owned))
  } catch (error) {
    primary = error instanceof Error && /^Safe (?:check failed|live blocker):/.test(error.message)
      ? error
      : new Error('Safe check failed: live interface execution')
    signalOwnedApplication()
  }
  let cleanupFailure
  try {
    await cleanupOwnedResources(owned, cleanupDeadline)
  } catch {
    cleanupFailure = new Error('Safe check failed: live interface cleanup')
  }
  if (primary || cleanupFailure) runCounters.fail(contract.apiName)
  if (primary && cleanupFailure) throw new AggregateError([primary, cleanupFailure], 'Safe live failure and cleanup failure')
  if (primary) throw primary
  if (cleanupFailure) throw cleanupFailure
  runCounters.complete(contract.apiName)
}

async function verifyTaskEvents(text) {
  const events = (marker) => text.split(/\r?\n/).filter((line) => line.includes(marker)).map((line) => parseCompletedEvent(line, marker))
  const accepted = events('tensor.download_task.accepted')
  const started = events('tensor.download_task.started')
  const finished = events('tensor.download_task.finished')
  const batches = events('tensor.download_batch.finished')
  safeCheck(accepted.length === observedTasks.length && started.length === observedTasks.length, 'one acceptance and start per task')
  const equalFields = (event, expected, keys) => {
    objectWithExactKeys(event, keys.split(' '), 'task event')
    for (const [key, value] of Object.entries(expected)) safeCheck(event[key] === String(value), 'task event fact')
  }
  for (const item of observedTasks) {
    const identity = { taskId: item.taskId, pluginId: item.pluginId, apiName: item.apiName }
    const one = (rows) => {
      const found = rows.filter((row) => row.taskId === item.taskId)
      safeCheck(found.length === 1, 'unique task event')
      return found[0]
    }
    equalFields(one(accepted), { ...identity, requestId: item.requestId, status: 'QUEUED', version: 1, kind: 'CREATED', outcome: 'accepted' }, 'requestId taskId pluginId apiName status version kind outcome durationMs')
    equalFields(one(started), { ...identity, runGeneration: 1 }, 'taskId pluginId apiName runGeneration')
    if (!item.task || ['QUEUED', 'RUNNING'].includes(item.task.status)) continue
    equalFields(one(finished), { ...identity, runGeneration: 1, status: item.task.status, recovered: false,
      requestCount: item.task.requestCount, runRequestCount: item.task.runRequestCount,
      ...item.task.counts, errorCode: item.task.lastError?.code ?? 'none',
    }, 'taskId pluginId apiName runGeneration status recovered durationMs requestCount runRequestCount totalBatches pendingBatches runningBatches succeededBatches failedBatches splitBatches sourceRows insertedRows updatedRows errorCode')
    const taskBatches = batches.filter((row) => row.taskId === item.taskId)
    const executed = item.batches.filter((batch) => batch.attemptCount > 0 && !['RUNNING', 'PENDING'].includes(batch.status))
    safeCheck(taskBatches.length === executed.length, 'batch events match actual executed nodes')
    for (const batch of executed) {
      const matches = taskBatches.filter((row) => row.batchId === batch.batchId)
      safeCheck(matches.length === 1, 'unique batch event')
      equalFields(matches[0], { ...identity, batchId: batch.batchId, runGeneration: 1, status: batch.status,
        attemptCount: batch.attemptCount, sourceRows: batch.sourceRows, insertedRows: batch.insertedRows,
        updatedRows: batch.updatedRows, errorCode: batch.error?.code ?? 'none',
      }, 'taskId batchId pluginId apiName runGeneration status attemptCount durationMs sourceRows insertedRows updatedRows errorCode')
    }
  }
  safeCheck(finished.every((event) => ownedTaskIds.has(event.taskId)) && batches.every((event) => ownedTaskIds.has(event.taskId)), 'only owned task events')
  evidence.taskEvents = { accepted: accepted.length, started: started.length, finished: finished.length, batches: batches.length }
}

async function verifyAllEvents() {
  await logSink?.idle()
  safeCheck(Boolean(applicationLogPath), 'application log initialized')
  const text = `${await readFile(applicationLogPath, 'utf8')}\n${logSink?.pendingText() ?? ''}`
  assertSafeText(text, 'final application log')
  const completed = text.split(/\r?\n/).filter((line) => line.includes('tensor.operation.completed'))
  safeCheck(completed.length === expectedEvents.size, 'completion event total')
  safeCheck(correlatedEvents.size === expectedEvents.size, 'completion events correlated')
  safeCheck(completed.every((line) => parseCompletedEvent(line).operation === 'query'), 'no synchronous download completion')
  await verifyTaskEvents(text)
}

async function writeSafeEvidence() {
  if (evidenceWritten) return
  evidence.finishedAt = new Date().toISOString()
  evidence.taskObservations = observedTasks.map((item) => ({ taskId: item.taskId, submissionId: item.submissionId,
    apiName: item.apiName, pluginId: item.pluginId, requestId: item.requestId,
    status: item.task?.status ?? null, observedAt: item.observedAt ?? null,
    requestCount: item.task ? evidenceInteger(item.task.requestCount, 5000) : null,
    counts: item.task ? Object.fromEntries(Object.entries(item.task.counts)
      .map(([key, value]) => [key, evidenceInteger(value)])) : null,
    errorCode: item.task?.lastError?.code ?? null,
    batchNodes: item.batches.map((node) => ({ batchId: node.batchId, parentBatchId: node.parentBatchId,
      status: node.status, start: node.rangeStart?.replaceAll('-', '') ?? null, end: node.rangeEnd?.replaceAll('-', '') ?? null,
      attemptCount: node.attemptCount, sourceRowCount: evidenceInteger(node.sourceRows),
      insertedRows: evidenceInteger(node.insertedRows), updatedRows: evidenceInteger(node.updatedRows),
      errorCode: node.error?.code ?? null })),
  }))
  const counters = runCounters.snapshot(INTERFACES.length)
  evidence.totals = {
    registeredCases: INTERFACES.length,
    attemptedCases: counters.attemptedCases,
    failedCases: counters.failedCases,
    completedCases: counters.completedCases,
    unexecutedCases: counters.unexecutedCases,
    manifestSamples: manifestSampleCount,
    selectedSamples: liveScope.sampleCount,
    liveDownloadPostsObserved: counters.liveDownloadPosts,
    fixtureDownloadPostsObserved: counters.fixtureDownloadPosts,
    liveRecordsGetsObserved: counters.liveRecordsGets,
    fixtureRecordsGetsObserved: counters.fixtureRecordsGets,
    liveDownloadResultsRecorded: evidence.downloads.length,
    liveQueryResultsRecorded: evidence.queries.length,
    callIntervalMs: intervalMs,
    completedSourceRequests: sourceRequests,
    sourceRequestsObserved: observedTasks.reduce((sum, item) =>
      sum + (item.task ? evidenceInteger(item.task.requestCount, 5000) : 0), 0),
    liveSourceRequestsObserved: observedTasks.filter((item) => item.pluginId === 'tushare_pro')
      .reduce((sum, item) => sum + (item.task ? evidenceInteger(item.task.requestCount, 5000) : 0), 0),
    effectiveRetries: 0,
    workers: 1,
  }
  evidence.inputs.specSha256 = await sha256(new URL(import.meta.url))
  evidence.inputs.gitCommit = (await execFileAsync('git', ['rev-parse', 'HEAD'], {
    cwd: new URL('../..', import.meta.url), env: publicEnvironment(), timeout: 10_000,
  })).stdout.trim()
  evidence.command = `ISSUE018_T13_PHASE=${phase} TENSOR_TUSHARE_LIVE_E2E=1 npx playwright test e2e/tushare-live.spec.js --workers=1`
  evidence.inputs.casePlanSha256 = casePlanInput?.sha256 ?? null
  evidence.inputs.evidenceIndexSha256 = evidenceIndexInput?.sha256 ?? null
  const serialized = `${JSON.stringify(evidence, null, 2)}\n`
  assertSafeText(serialized, 'safe evidence')
  const target = path.join(runDirectory, 'safe-results.json')
  try {
    await writeFile(target, serialized, { encoding: 'utf8', flag: 'wx', mode: 0o600 })
  } catch (error) {
    if (error?.code !== 'EEXIST') throw error
    const existingState = await lstat(target)
    safeCheck(existingState.isFile() && !existingState.isSymbolicLink(), 'existing safe evidence ordinary file')
    safeCheck((existingState.mode & 0o777) === 0o600, 'existing safe evidence mode')
    assertSafeText(await readFile(target, 'utf8'), 'existing safe evidence')
    evidenceWritten = true
    return
  }
  await chmod(target, 0o600)
  evidenceWritten = true
  console.info(`ISSUE-018-T13 safe results: ${target}`)
}

async function cleanupAfterSetupFailure(error) {
  signalOwnedApplication()
  const failures = [error]
  if (monitors.size) {
    try { await drainAllMonitors() } catch (cleanupError) { failures.push(cleanupError) }
  }
  if (application) {
    try { await stopApplication() } catch (cleanupError) { failures.push(cleanupError) }
  }
  throw failures.length === 1 ? error : new AggregateError(failures, 'Safe setup and cleanup failure')
}

function registerTests() {
  test.use({
    viewport: { width: 1440, height: 1000 },
    trace: 'off',
    video: 'off',
    screenshot: 'off',
    launchOptions: { env: publicEnvironment() },
  })

  test.describe(`ISSUE-018-T13 ${phase} live Tushare acceptance`, () => {
    test.describe.configure({ mode: 'serial', retries: 0 })

    test.beforeAll(async ({ browser }, testInfo) => {
      test.setTimeout(600_000)
      evidence.startedAt = new Date().toISOString()
      runDeadline = Date.now() + 30 * 60_000
      budgetTimer = setTimeout(() => {
        runtimeFailure = new Error('Safe check failed: shared 30 minute deadline')
        signalOwnedApplication()
      }, 30 * 60_000)
      try {
        await bounded(validatePreconditions(testInfo), 30_000, 'preconditions')
        await startApplication()
        await runFixture(browser)
      } catch (error) {
        setupFailed = true
        await cleanupAfterSetupFailure(
          error instanceof Error && error.message.startsWith('Safe')
            ? error
            : new Error('Safe check failed: setup'),
        )
      }
    })

    test.afterAll(async () => {
      test.setTimeout(330_000)
      const failures = []
      try { await bounded(drainAllMonitors(), 135_000, 'final network drain') } catch { failures.push(new Error('Safe check failed: final network drain')) }
      try { await stopApplication() } catch { failures.push(new Error('Safe check failed: JVM cleanup')) }
      clearTimeout(budgetTimer)
      if (jarValidated) {
        try {
          safeCheck(await sha256(process.env.ACCEPTANCE_JAR) === jarHashBefore, 'acceptance JAR unchanged')
          safeCheck(createHash('sha256').update(await readFile(manifestPath)).digest('hex') === MANIFEST_SHA, 'manifest unchanged')
          safeCheck(createHash('sha256').update(await readFile(requestsPath)).digest('hex') === REQUESTS_SHA, 'request examples unchanged')
        } catch { failures.push(new Error('Safe check failed: immutable input verification')) }
      }
      if (artifactInitialized) {
        try { await verifyAllEvents() } catch { failures.push(new Error('Safe check failed: final log correlation')) }
      }
      if (!setupFailed && runCounters.completed.size === INTERFACES.length) {
        try {
          safeCheck(fixturePassed, 'fixture preparation completed')
          safeCheck(evidence.downloads.length === liveScope.sampleCount, 'selected live downloads completed')
          safeCheck(evidence.queries.length === liveScope.sampleCount * 2, 'selected live queries completed')
          safeCheck(evidence.fixture.length === 5, '2 fixture downloads and 3 fixture queries completed')
          safeCheck(runCounters.traffic.liveDownloadPosts === liveScope.sampleCount, 'selected live download POSTs observed')
          safeCheck(runCounters.traffic.fixtureDownloadPosts === 2, '2 fixture download POSTs observed')
          safeCheck(runCounters.traffic.liveRecordsGets === liveScope.sampleCount * 2, 'selected live records GETs observed')
          safeCheck(runCounters.traffic.fixtureRecordsGets === 3, '3 fixture records GETs observed')
          safeCheck(runCounters.attempted.size === INTERFACES.length && runCounters.failed.size === 0, 'selected live cases attempted without failure')
        } catch { failures.push(new Error('Safe check failed: complete matrix totals')) }
      }
      let immutableInputs = false
      try {
        immutableInputs = Boolean(process.env.ACCEPTANCE_JAR) &&
          await sha256(process.env.ACCEPTANCE_JAR) === JAR_SHA &&
          createHash('sha256').update(await readFile(manifestPath)).digest('hex') === MANIFEST_SHA &&
          createHash('sha256').update(await readFile(requestsPath)).digest('hex') === REQUESTS_SHA &&
          await evidenceInputsUnchanged()
      } catch {
        immutableInputs = false
      }
      if (!immutableInputs && jarValidated) failures.push(new Error('Safe check failed: immutable input verification'))
      evidence.cleanup = {
        networkDrained: failures.every(({ message }) => !message.includes('network')),
        logCorrelated: failures.every(({ message }) => !message.includes('log correlation')),
        budgetExpired: Boolean(runtimeFailure),
        jvmStopped: !(await canConnectToPort()),
      }
      if (artifactInitialized) evidence.cleanup.logScanned = !logSink?.failed
      if (jarValidated) evidence.cleanup.immutableInputs = immutableInputs
      if (artifactInitialized) {
        try { await writeSafeEvidence() } catch { failures.push(new Error('Safe check failed: evidence write')) }
      }
      if (failures.length) throw new AggregateError(failures, 'Safe ISSUE-018-T13 cleanup failure')
    })

    for (const entry of INTERFACES) {
      test(`liveTushare:${entry.api_name}`, async ({ browser }) => {
        test.setTimeout(liveCaseTimeoutMs(entry.params.length))
        await runLiveInterface(browser, entry)
      })
    }
  })
}

registerTests()
