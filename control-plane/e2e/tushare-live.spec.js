import { expect, test } from '@playwright/test'
import { execFile, spawn } from 'node:child_process'
import { createHash } from 'node:crypto'
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
import { readFileSync } from 'node:fs'
import { createConnection } from 'node:net'
import path from 'node:path'
import { promisify } from 'node:util'
import { setTimeout as delay } from 'node:timers/promises'

const execFileAsync = promisify(execFile)
const BASE_URL = 'http://127.0.0.1:8080'
const MANIFEST_SHA = '386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984'
const REQUESTS_SHA = 'f9f147c605262ee1e4f04031dc8508470acd0b4a837927495a9aefecf98bf7af'
const JAR_SHA = process.env.ISSUE_017_ACCEPTANCE_JAR_SHA256
const DOWNLOAD_KEYS = [
  'requestId', 'outcome', 'pluginId', 'apiName', 'sourceRowCount', 'insertedRows',
  'updatedRows', 'message',
]
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

const PARAMETERS = {
  list_status: { label: '上市状态', type: 'ENUM' },
  exchange: { label: '交易所', type: 'ENUM' },
  exchange_id: { label: '交易所', type: 'ENUM' },
  start_date: { label: '开始日期', type: 'DATE_RANGE_MEMBER' },
  end_date: { label: '结束日期', type: 'DATE_RANGE_MEMBER' },
  trade_date: { label: '交易日期', type: 'DATE' },
  ann_date: { label: '公告日期', type: 'DATE' },
  ts_code: { label: '股票代码', type: 'TS_CODE' },
}
const FILTER_LABELS = {
  ts_code: ['证券代码 (ts_code)'],
  trade_date: ['交易日期开始 (trade_date)', '交易日期结束 (trade_date)'],
  ann_date: ['公告日期开始 (ann_date)', '公告日期结束 (ann_date)'],
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
  ['fina_mainbz', '主营业务构成', ['ts_code', 'ann_date'], 8],
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
  const sampleCount = requests.length
  const okCount = interfaces.filter(({ status }) => status === 'ok').length
  const emptyCount = interfaces.filter(({ status }) => status === 'empty').length
  safeCheck(sampleCount === 40, 'live scope sample count')
  safeCheck(okCount === 28 && emptyCount === 12, 'live scope status counts')
  const acceptanceInterfaces = interfaces.map((entry, index) => ({
    ...entry,
    params: [requests[index].params],
  }))
  return {
    interfaces: acceptanceInterfaces,
    sampleCount,
    okCount,
    emptyCount,
  }
}

export function validateDownloadSuccess(body, pluginId, apiName) {
  objectWithExactKeys(body, DOWNLOAD_KEYS, 'download response')
  safeCheck(typeof body.requestId === 'string' && body.requestId.length > 0, 'download request ID')
  safeCheck(body.pluginId === pluginId && body.apiName === apiName, 'download identity')
  safeCheck(['SUCCESS', 'EMPTY'].includes(body.outcome), 'download outcome')
  safeCheck(
    integer(body.sourceRowCount) && integer(body.insertedRows) && integer(body.updatedRows),
    'download safe counts',
  )
  safeCheck(typeof body.message === 'string', 'download public message')
  if (body.outcome === 'SUCCESS') {
    safeCheck(body.sourceRowCount > 0, 'SUCCESS source rows')
    safeCheck(
      body.insertedRows + body.updatedRows > 0 &&
        body.insertedRows + body.updatedRows <= body.sourceRowCount,
      'SUCCESS distinct-key counts',
    )
  } else {
    safeCheck(
      body.sourceRowCount === 0 && body.insertedRows === 0 && body.updatedRows === 0,
      'EMPTY zero counts',
    )
  }
  return body
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

export function validateInterfaceOutcomes(status, outcomes) {
  safeCheck(Array.isArray(outcomes) && outcomes.length > 0, 'interface outcomes present')
  if (status === 'ok') {
    safeCheck(outcomes.some(({ outcome }) => outcome === 'SUCCESS'), 'ok interface has SUCCESS')
    safeCheck(
      outcomes.reduce((sum, result) => sum + result.sourceRowCount, 0) > 0,
      'ok interface source rows',
    )
  } else {
    safeCheck(
      outcomes.every((result) =>
        result.outcome === 'EMPTY' && result.sourceRowCount === 0 &&
        result.insertedRows === 0 && result.updatedRows === 0),
      'empty interface stays empty',
    )
  }
}

export function validateBusinessRow(row, columns, pluginId, apiName, startedAt, finishedAt) {
  objectWithExactKeys(row, columns, 'business row')
  safeCheck(Object.values(row).every((value) => value === null || typeof value === 'string'), 'business row value types')
  safeCheck(row.source_plugin === pluginId && row.source_api === apiName, 'business row source')
  const instant = Date.parse(row.ingested_at)
  safeCheck(Number.isFinite(instant), 'business row ingestion instant')
  safeCheck(instant >= startedAt && instant <= finishedAt, 'business row ingestion window')
}

export function validateFinalDataset({
  outcomeStatus,
  insertedRows,
  body,
  definition,
  startedAt,
  finishedAt,
}) {
  objectWithExactKeys(body, PAGE_KEYS, 'page response')
  safeCheck(body.totalElements === insertedRows, 'final total equals inserted rows')
  const columns = [...definition.columns.map(({ name }) => name), ...SOURCE_COLUMNS]
  safeCheck(JSON.stringify(body.columns) === JSON.stringify(columns), 'final columns')
  if (outcomeStatus === 'empty') {
    safeCheck(body.totalElements === 0 && body.items.length === 0, 'empty interface has no rows')
    return
  }
  safeCheck(body.totalElements > 0 && body.items.length > 0, 'ok interface has visible rows')
  validateBusinessRow(
    body.items[0], columns, 'tushare_pro', body.apiName, startedAt, finishedAt,
  )
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
    safeCheck(method === 'POST' && pathname === '/api/v1/downloads', 'only download POST')
    safeCheck(Boolean(this.expectedDownload), 'download POST registered')
    safeCheck(JSON.stringify(body) === JSON.stringify(this.expectedDownload), 'download POST body')
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
const liveScope = selectLiveInterfaces(manifest.interfaces, requests)
const INTERFACES = liveScope.interfaces.map((entry) => ({ ...entry, contract: CONTRACTS.get(entry.api_name) }))

let application
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
  task: 'M14-T09',
  sourceTask: 'M14-T05',
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
          pathname === `/api/v1/data-sources/${pluginId}/datasets/${apiName}`
        const recordPath = `/api/v1/data-sources/${pluginId}/datasets/${apiName}/records`
        if (pathname === recordPath) {
          ledger.observeQuery(pathname)
          records += 1
          runCounters.observe(pluginId === 'fixture' ? 'fixtureRecordsGets' : 'liveRecordsGets')
        } else safeCheck(normal || metadata, 'browser request allowlist')
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
  const response = await page.goto(route)
  safeCheck(response?.status() === 200, 'page route status')
  await assertPageSafe(page, 'page route')
  await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible()
}

async function selectOption(page, label, name) {
  const combobox = page.getByRole('combobox', { name: label, exact: true })
  await combobox.focus()
  await combobox.press('Enter')
  const option = page.getByRole('option', { name, exact: typeof name === 'string' })
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
  await selectOption(page, '数据接口', pluginId === 'fixture' ? FIXTURE_OPTION : optionName(contract))
  await assertPageSafe(page, 'download selection')
  const region = page.getByRole('region', { name: '接口说明', exact: true })
  await expect(region.getByText(contract.apiName, { exact: true })).toBeVisible()
}

async function openDownloadFromDataset(page, pluginId, contract) {
  const sourcesPromise = page.waitForResponse((response) => isGet(response, '/api/v1/data-sources'))
  await page.getByRole('link', { name: '数据下载', exact: true }).click()
  await assertPageSafe(page, 'download navigation')
  await expect(page.getByRole('heading', { level: 1, name: '数据下载' })).toBeVisible()
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

async function openDataset(page, monitor, pluginId, contract, navigate = false) {
  const recordsBefore = monitor.records()
  const sourcesPromise = page.waitForResponse((response) => isGet(response, '/api/v1/data-sources'))
  if (navigate) {
    await page.getByRole('link', { name: '数据查看', exact: true }).click()
    await assertPageSafe(page, 'dataset navigation')
    await expect(page.getByRole('heading', { level: 1, name: '数据查看' })).toBeVisible()
  } else await openRoute(page, '/datasets', '数据查看')
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

function eventFieldSet(operation) {
  return operation === 'download'
    ? [
        'requestId', 'operation', 'pluginId', 'apiName', 'paramSummary', 'sourceRowCount',
        'insertedRows', 'updatedRows', 'durationMs', 'outcome', 'failureStage', 'errorCode',
      ]
    : [
        'requestId', 'operation', 'pluginId', 'apiName', 'filterNames', 'page', 'pageSize',
        'resultCount', 'totalElements', 'durationMs', 'outcome', 'failureStage', 'errorCode',
      ]
}

function parseCompletedEvent(line) {
  const marker = 'tensor.operation.completed'
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
  if (expected.outcome === 'failure') {
    safeCheck(event.failureStage !== 'none', 'failed completion stage')
    safeCheck(
      event.sourceRowCount === 'unavailable' && event.insertedRows === 'unavailable' &&
      event.updatedRows === 'unavailable',
      'failed completion unavailable counts',
    )
  }
  safeCheck(integer(Number(event.durationMs)), 'completion duration')
  correlatedEvents.set(expected.requestId, event)
  return Number(event.durationMs)
}

async function queryDataset(page, monitor, pluginId, contract, definition, { code } = {}) {
  const pathname = `/api/v1/data-sources/${pluginId}/datasets/${contract.apiName}/records`
  const priorRecords = monitor.records()
  safeCheck(priorRecords >= 0, 'records counter')
  if (code) await page.getByLabel('证券代码 (ts_code)', { exact: true }).fill(code)
  ledger.expectQuery(pathname)
  const responsePromise = page.waitForResponse((response) => isGet(response, pathname), { timeout: 30_000 })
  await page.getByRole('button', { name: '查询', exact: true }).click()
  const response = await responsePromise
  const body = await safeJson(response, 'records response')
  validatePageBody(response, body, pluginId, contract.apiName)
  safeCheck(
    JSON.stringify(body.columns) === JSON.stringify([
      ...definition.columns.map(({ name }) => name), ...SOURCE_COLUMNS,
    ]),
    'records columns match definition',
  )
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
    apiName: contract.apiName,
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
  for (const name of contract.parameters) {
    const parameter = PARAMETERS[name]
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

async function submitDownload(page, monitor, pluginId, contract, params, fixture = false) {
  if (!fixture) await rateLimit()
  const expectedBody = { pluginId, apiName: contract.apiName, params }
  ledger.expectDownload(expectedBody)
  const beforePosts = monitor.downloads()
  const responsePromise = page.waitForResponse(
    (response) => response.request().method() === 'POST' && responsePath(response) === '/api/v1/downloads',
    { timeout: fixture ? 30_000 : 135_000 },
  )
  const startedAt = Date.now()
  await page.getByRole('button', { name: '开始下载', exact: true }).click()
  const response = await responsePromise
  const body = await safeJson(response, 'download response')
  if (!fixture) lastDownloadFinishedAt = Date.now()
  safeCheck(monitor.downloads() === beforePosts + 1, 'one download POST')
  safeCheck(response.headers()['x-request-id'] === body.requestId, 'download header request ID')
  ledger.rememberRequestId(body.requestId)

  if (response.status() !== 200) {
    validateApiError(body)
    const projection = {
      apiName: contract.apiName,
      outcome: body.code,
      requestId: body.requestId,
    }
    evidence[fixture ? 'fixture' : 'downloads'].push(projection)
    await assertPageSafe(page, 'download result')
    const alert = page.getByRole('alert')
    await expect(alert.getByRole('heading', { name: '下载失败' })).toBeVisible()
    const alertText = await alert.innerText()
    assertSafeText(alertText, 'download failure alert')
    safeCheck(alertText.includes(body.message), 'download failure summary')
    await expect(page.getByRole('status')).toHaveCount(0)
    const durationMs = await correlateEvent({
      requestId: body.requestId,
      operation: 'download',
      pluginId,
      apiName: contract.apiName,
      outcome: 'failure',
      errorCode: body.code,
    })
    projection.durationMs = durationMs
    await monitor.drain()
    throw new Error(`Safe live blocker: ${body.code}`)
  }

  validateDownloadSuccess(body, pluginId, contract.apiName)
  const projection = {
    apiName: contract.apiName,
    outcome: body.outcome,
    sourceRowCount: body.sourceRowCount,
    insertedRows: body.insertedRows,
    updatedRows: body.updatedRows,
    requestId: body.requestId,
  }
  evidence[fixture ? 'fixture' : 'downloads'].push(projection)
  await assertPageSafe(page, 'download result')
  const panel = page.getByRole('status')
  if (body.outcome === 'EMPTY') {
    await expect(panel.getByRole('heading', { name: '下载成功，0 条数据' })).toBeVisible()
    await expect(panel.getByText('本次请求没有可写入的数据。')).toBeVisible()
    await expect(panel.getByRole('term')).toHaveCount(0)
  } else {
    await expect(panel.getByRole('heading', { name: '下载成功' })).toBeVisible()
    await expect(panel.getByRole('term')).toHaveText(['上游返回数', '插入数', '更新数'])
    await expect(panel.getByRole('definition')).toHaveText([
      String(body.sourceRowCount), String(body.insertedRows), String(body.updatedRows),
    ])
  }
  await expect(page.getByRole('button', { name: '开始下载', exact: true })).toBeEnabled()
  await expect(page.getByRole('combobox', { name: '数据源', exact: true })).toBeEnabled()
  await expect(page.getByRole('combobox', { name: '数据接口', exact: true })).toBeEnabled()
  for (const name of contract.parameters) {
    const parameter = PARAMETERS[name]
    const control = parameter?.type === 'ENUM'
      ? page.getByRole('combobox', { name: parameter.label, exact: true })
      : page.getByLabel(new RegExp(`^${escapeRegex(parameter?.label ?? '场景')}\\s*\\*?$`))
    await expect(control).toBeEnabled()
  }
  const durationMs = await correlateEvent({
    requestId: body.requestId,
    operation: 'download',
    pluginId,
    apiName: contract.apiName,
    sourceRowCount: body.sourceRowCount,
    insertedRows: body.insertedRows,
    updatedRows: body.updatedRows,
    outcome: body.outcome.toLowerCase(),
    failureStage: 'none',
    errorCode: 'none',
  })
  projection.durationMs = durationMs
  await monitor.drain()
  return { body, startedAt, finishedAt: Date.now() }
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
  const headerTexts = [...definition.columns.map(({ label }) => label), ...SOURCE_COLUMNS]
  safeCheck(
    JSON.stringify(await page.getByRole('columnheader').allTextContents()) === JSON.stringify(headerTexts),
    'visible column labels',
  )
  const rows = page.getByRole('row')
  safeCheck(await rows.count() >= 2, 'visible business row exists')
  const cells = rows.nth(1).getByRole('cell')
  const names = [...definition.columns.map(({ name }) => name), ...SOURCE_COLUMNS]
  const expected = names.map((name) => {
    if (name === 'ingested_at') return shanghaiTimestamp(row[name])
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
      let body = await queryDataset(page, monitor, 'fixture', contract, definition)
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

      const afterSuccessDefinition = await openDataset(page, monitor, 'fixture', contract, true)
      body = await queryDataset(page, monitor, 'fixture', contract, afterSuccessDefinition, { code: '000001.SZ' })
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

      const afterEmptyDefinition = await openDataset(page, monitor, 'fixture', contract, true)
      const afterEmpty = await queryDataset(page, monitor, 'fixture', contract, afterEmptyDefinition)
      safeCheck(afterEmpty.totalElements === 1 && afterEmpty.items.length === 1, 'fixture row retained')
      safeCheck(JSON.stringify(afterEmpty.items[0]) === JSON.stringify(row), 'fixture row unchanged')
      await assertVisibleRow(page, afterEmptyDefinition, row)
    })(), workDeadline, 'fixture preparation', () => cancelOwned(owned))
  } catch (error) {
    primary = error instanceof Error && error.message.startsWith('Safe')
      ? error
      : new Error('Safe check failed: fixture preparation')
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
  return 240_000 + sampleCount * (150_000 + intervalMs)
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
      const definition = await openDataset(page, monitor, 'tushare_pro', contract)
      await expect(page.getByRole('heading', { name: '设置筛选条件后查询' })).toBeVisible()
      const initial = await queryDataset(page, monitor, 'tushare_pro', contract, definition)
      safeCheck(initial.totalElements === 0 && initial.items.length === 0, 'live dataset initially empty')

      await openDownloadFromDataset(page, 'tushare_pro', contract)
      const results = []
      let firstDownloadAt
      let lastDownloadAt
      for (let index = 0; index < entry.params.length; index += 1) {
        if (index > 0) {
          await openDataset(page, monitor, 'tushare_pro', contract, true)
          await openDownloadFromDataset(page, 'tushare_pro', contract)
        }
        await fillSample(page, contract, entry.params[index])
        const result = await submitDownload(
          page, monitor, 'tushare_pro', contract, entry.params[index], false,
        )
        firstDownloadAt ??= result.startedAt
        lastDownloadAt = result.finishedAt
        results.push(result.body)
      }
      const outcomeStatus = results.some(({ outcome }) => outcome === 'SUCCESS') ? 'ok' : 'empty'
      validateInterfaceOutcomes(outcomeStatus, results)

      const finalDefinition = await openDataset(page, monitor, 'tushare_pro', contract, true)
      const finalBody = await queryDataset(page, monitor, 'tushare_pro', contract, finalDefinition)
      const insertedRows = results.reduce((sum, result) => sum + result.insertedRows, 0)
      validateFinalDataset({
        outcomeStatus,
        insertedRows,
        body: finalBody,
        definition: finalDefinition,
        startedAt: firstDownloadAt,
        finishedAt: lastDownloadAt,
      })
      if (outcomeStatus === 'ok') await assertVisibleRow(page, finalDefinition, finalBody.items[0])
      else {
        await expect(page.getByText('未找到符合条件的数据')).toBeVisible()
        safeCheck(await page.getByRole('row').count() <= 1, 'empty interface has no placeholder row')
      }
      await monitor.drain(Math.min(workDeadline, Date.now() + 135_000))
    })(), workDeadline, 'live interface work', () => cancelOwned(owned))
  } catch (error) {
    primary = error instanceof Error && /^Safe (?:check failed|live blocker):/.test(error.message)
      ? error
      : new Error('Safe check failed: live interface execution')
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

async function verifyAllEvents() {
  await logSink?.idle()
  safeCheck(Boolean(applicationLogPath), 'application log initialized')
  const text = `${await readFile(applicationLogPath, 'utf8')}\n${logSink?.pendingText() ?? ''}`
  assertSafeText(text, 'final application log')
  const completed = text.split(/\r?\n/).filter((line) => line.includes('tensor.operation.completed'))
  safeCheck(completed.length === expectedEvents.size, 'completion event total')
  safeCheck(correlatedEvents.size === expectedEvents.size, 'completion events correlated')
}

async function writeSafeEvidence() {
  if (evidenceWritten) return
  evidence.finishedAt = new Date().toISOString()
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
  }
  evidence.inputs.specSha256 = await sha256(new URL(import.meta.url))
  evidence.inputs.gitCommit = (await execFileAsync('git', ['rev-parse', 'HEAD'], {
    cwd: new URL('../..', import.meta.url), env: publicEnvironment(), timeout: 10_000,
  })).stdout.trim()
  evidence.command = 'npx playwright test e2e/tushare-live.spec.js --workers=1'
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
  console.info(`M14-T09 safe results: ${target}`)
}

async function cleanupAfterSetupFailure(error) {
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

  test.describe('M14-T09 live Tushare acceptance', () => {
    test.describe.configure({ mode: 'serial', retries: 0 })

    test.beforeAll(async ({ browser }, testInfo) => {
      test.setTimeout(600_000)
      evidence.startedAt = new Date().toISOString()
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
          safeCheck(evidence.queries.length === INTERFACES.length * 2, 'selected live queries completed')
          safeCheck(evidence.fixture.length === 5, '2 fixture downloads and 3 fixture queries completed')
          safeCheck(runCounters.traffic.liveDownloadPosts === liveScope.sampleCount, 'selected live download POSTs observed')
          safeCheck(runCounters.traffic.fixtureDownloadPosts === 2, '2 fixture download POSTs observed')
          safeCheck(runCounters.traffic.liveRecordsGets === INTERFACES.length * 2, 'selected live records GETs observed')
          safeCheck(runCounters.traffic.fixtureRecordsGets === 3, '3 fixture records GETs observed')
          safeCheck(runCounters.attempted.size === INTERFACES.length && runCounters.failed.size === 0, 'selected live cases attempted without failure')
        } catch { failures.push(new Error('Safe check failed: complete matrix totals')) }
      }
      let immutableInputs = false
      try {
        immutableInputs = Boolean(process.env.ACCEPTANCE_JAR) &&
          await sha256(process.env.ACCEPTANCE_JAR) === JAR_SHA &&
          createHash('sha256').update(await readFile(manifestPath)).digest('hex') === MANIFEST_SHA &&
          createHash('sha256').update(await readFile(requestsPath)).digest('hex') === REQUESTS_SHA
      } catch {
        immutableInputs = false
      }
      evidence.cleanup = {
        networkDrained: failures.every(({ message }) => !message.includes('network')),
        jvmStopped: !(await canConnectToPort()),
      }
      if (artifactInitialized) evidence.cleanup.logScanned = !logSink?.failed
      if (jarValidated) evidence.cleanup.immutableInputs = immutableInputs
      if (artifactInitialized) {
        try { await writeSafeEvidence() } catch { failures.push(new Error('Safe check failed: evidence write')) }
      }
      if (failures.length) throw new AggregateError(failures, 'Safe M14-T09 cleanup failure')
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
