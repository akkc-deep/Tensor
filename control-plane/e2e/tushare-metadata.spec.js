import { expect, test } from '@playwright/test'
import { spawn, execFile } from 'node:child_process'
import { createHash, randomBytes } from 'node:crypto'
import { lstat, mkdtemp, open, readFile, stat, writeFile } from 'node:fs/promises'
import { readFileSync } from 'node:fs'
import { createServer } from 'node:http'
import { createConnection } from 'node:net'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { promisify } from 'node:util'
import { setTimeout as delay } from 'node:timers/promises'

const execFileAsync = promisify(execFile)
const BASE_URL = 'http://127.0.0.1:8080'
const MANIFEST_SHA = '37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2'
const ACCEPTANCE_JAR_SHA = 'a69874afa6ce783d4ef4e16a678ddb0ff457f2948b68f509a8e4a2c00440bcac'
const HEALTH_TIMEOUT_MS = 90_000
const STOP_TIMEOUT_MS = 150_000
const DB_VARIABLES = ['TENSOR_DB_URL', 'TENSOR_DB_USERNAME', 'TENSOR_DB_PASSWORD']
const API_KEYS = ['apiName', 'displayName', 'category', 'queryMode', 'parameters']
const SOURCE_KEYS = [
  'pluginId', 'displayName', 'description', 'enabled', 'credentialConfigured',
  'downloadAvailable', 'unavailableReason',
]
const SUMMARY_KEYS = [
  'pluginId', 'apiName', 'displayName', 'category', 'queryMode', 'filters', 'fixedColumn',
]
const DEFINITION_KEYS = [...SUMMARY_KEYS, 'columns']
const COLUMN_KEYS = [
  'name', 'label', 'logicalType', 'nullable', 'displayOrder', 'length', 'precision',
  'scale', 'allowedValues', 'longText',
]
const QUERY_MODE_LABELS = {
  trade_date: '交易日',
  ann_date: '公告日',
  snapshot: '快照',
  date_range: '日期范围',
}
const CATEGORY_COUNTS = {
  basic_organization: 11,
  行情与估值: 7,
  交易与资金: 6,
  互联互通与转融通: 6,
  财务与披露: 9,
  公司行动: 3,
  股东与治理: 7,
}
const DOWNLOAD_SCREENSHOTS = new Set([
  'stock_basic', 'trade_cal', 'broker_recommend', 'daily', 'income', 'stk_managers',
  'moneyflow_hsgt', 'pledge_detail',
])
const DATASET_SCREENSHOTS = new Set([
  'index_classify', 'stock_company', 'margin', 'daily', 'balancesheet',
])
const FILTER_LABELS = {
  ts_code: ['证券代码 (ts_code)'],
  trade_date: ['交易日期开始 (trade_date)', '交易日期结束 (trade_date)'],
  ann_date: ['公告日期开始 (ann_date)', '公告日期结束 (ann_date)'],
}

const PARAMETER = {
  list_status: { name: 'list_status', label: '上市状态', type: 'ENUM', required: true, allowedValues: ['L', 'P', 'D'] },
  exchange: { name: 'exchange', label: '交易所', type: 'ENUM', required: true, allowedValues: ['SSE', 'SZSE', 'BSE'] },
  exchange_id: { name: 'exchange_id', label: '交易所', type: 'ENUM', required: true, allowedValues: ['SSE', 'SZSE', 'BSE'] },
  hs_type: { name: 'hs_type', label: '沪深港通类型', type: 'ENUM', required: true, allowedValues: ['SH', 'SZ'] },
  start_date: { name: 'start_date', label: '开始日期', type: 'DATE_RANGE_MEMBER', required: true, relatedParameter: 'end_date' },
  end_date: { name: 'end_date', label: '结束日期', type: 'DATE_RANGE_MEMBER', required: true, relatedParameter: 'start_date' },
  month: { name: 'month', label: '月份', type: 'MONTH', required: true },
  trade_date: { name: 'trade_date', label: '交易日期', type: 'DATE', required: true },
  ann_date: { name: 'ann_date', label: '公告日期', type: 'DATE', required: true },
  ts_code: { name: 'ts_code', label: '股票代码', type: 'TS_CODE', required: true },
}

const EXPECTED_ROWS = [
  ['stock_basic', '股票基础信息', 'basic_organization', 'snapshot', ['list_status'], 10],
  ['stock_company', '上市公司基本信息', 'basic_organization', 'snapshot', ['exchange'], 18],
  ['hs_const', '沪深港通标的范围', 'basic_organization', 'snapshot', ['hs_type'], 5],
  ['income', '利润表', '财务与披露', 'ann_date', ['ts_code', 'ann_date'], 85],
  ['balancesheet', '资产负债表', '财务与披露', 'ann_date', ['ts_code', 'ann_date'], 152],
  ['cashflow', '现金流量表', '财务与披露', 'ann_date', ['ts_code', 'ann_date'], 97],
  ['fina_indicator', '财务指标', '财务与披露', 'ann_date', ['ts_code', 'ann_date'], 108],
  ['fina_audit', '财务审计意见', '财务与披露', 'ann_date', ['ts_code', 'ann_date'], 7],
  ['fina_mainbz', '主营业务构成', '财务与披露', 'ann_date', ['ts_code', 'ann_date'], 8],
  ['stk_rewards', '管理层薪酬与持股', '股东与治理', 'snapshot', ['ts_code'], 7],
  ['stk_holdernumber', '股东户数', '股东与治理', 'snapshot', ['ts_code'], 4],
  ['broker_recommend', '券商月度推荐', 'basic_organization', 'snapshot', ['month'], 4],
  ['trade_cal', '交易日历', 'basic_organization', 'date_range', ['exchange', 'start_date', 'end_date'], 4],
  ['margin', '融资融券汇总', '交易与资金', 'trade_date', ['exchange_id', 'trade_date'], 9],
  ['daily', '日线行情', '行情与估值', 'trade_date', ['trade_date'], 11],
  ['weekly', '周线行情', '行情与估值', 'trade_date', ['trade_date'], 11],
  ['monthly', '月线行情', '行情与估值', 'trade_date', ['trade_date'], 11],
  ['adj_factor', '复权因子', '行情与估值', 'trade_date', ['trade_date'], 3],
  ['suspend_d', '每日停复牌信息', '行情与估值', 'trade_date', ['trade_date'], 4],
  ['daily_basic', '每日估值与市场指标', '行情与估值', 'trade_date', ['trade_date'], 18],
  ['moneyflow', '个股资金流向', '交易与资金', 'trade_date', ['trade_date'], 20],
  ['stk_limit', '每日涨跌停价格', '行情与估值', 'trade_date', ['trade_date'], 4],
  ['moneyflow_hsgt', '沪深港通资金流向', '互联互通与转融通', 'trade_date', ['trade_date'], 7],
  ['hsgt_top10', '沪深港通十大成交股', '互联互通与转融通', 'trade_date', ['trade_date'], 11],
  ['hk_hold', '沪深港股通持股明细', '互联互通与转融通', 'trade_date', ['trade_date'], 7],
  ['top_list', '龙虎榜每日明细', '交易与资金', 'trade_date', ['trade_date'], 15],
  ['top_inst', '龙虎榜机构明细', '交易与资金', 'trade_date', ['trade_date'], 10],
  ['margin_detail', '融资融券交易明细', '交易与资金', 'trade_date', ['trade_date'], 10],
  ['block_trade', '大宗交易', '交易与资金', 'trade_date', ['trade_date'], 7],
  ['slb_len', '转融通期限与规模', '互联互通与转融通', 'trade_date', ['trade_date'], 6],
  ['slb_sec', '转融通证券汇总', '互联互通与转融通', 'trade_date', ['trade_date'], 7],
  ['slb_sec_detail', '转融通证券明细', '互联互通与转融通', 'trade_date', ['trade_date'], 6],
  ['forecast', '业绩预告', '财务与披露', 'ann_date', ['ann_date'], 13],
  ['express', '业绩快报', '财务与披露', 'ann_date', ['ann_date'], 15],
  ['dividend', '分红送股', '公司行动', 'ann_date', ['ann_date'], 14],
  ['disclosure_date', '财报披露计划', '财务与披露', 'ann_date', ['ann_date'], 5],
  ['repurchase', '股票回购', '公司行动', 'ann_date', ['ann_date'], 9],
  ['share_float', '限售股解禁', '公司行动', 'ann_date', ['ann_date'], 7],
  ['stk_holdertrade', '股东增减持', '股东与治理', 'ann_date', ['ann_date'], 11],
  ['top10_holders', '前十大股东', '股东与治理', 'ann_date', ['ann_date'], 9],
  ['top10_floatholders', '前十大流通股东', '股东与治理', 'ann_date', ['ann_date'], 9],
  ['new_share', 'IPO 新股发行信息', 'basic_organization', 'date_range', ['start_date', 'end_date'], 12],
  ['namechange', '证券名称变更记录', 'basic_organization', 'date_range', ['start_date', 'end_date'], 6],
  ['stk_managers', '上市公司管理层信息', 'basic_organization', 'snapshot', [], 11],
  ['pledge_stat', '股权质押统计', '股东与治理', 'snapshot', [], 7],
  ['pledge_detail', '股权质押明细', '股东与治理', 'snapshot', [], 14],
  ['index_classify', '行业指数分类', 'basic_organization', 'snapshot', [], 7],
  ['index_member', '行业指数成分', 'basic_organization', 'snapshot', [], 5],
  ['index_member_all', '行业分级与完整成分', 'basic_organization', 'snapshot', [], 11],
]

function safeCheck(condition, name) {
  if (!condition) throw new Error(`Safe check failed: ${name}`)
}

function exactKeys(value, keys, name) {
  safeCheck(value !== null && typeof value === 'object' && !Array.isArray(value), `${name} object`)
  expect(Object.keys(value).sort()).toEqual([...keys].sort())
}

function filterDescriptor(field) {
  return field === 'ts_code'
    ? { field, operator: 'EQ', controlType: 'TEXT' }
    : { field, operator: 'BETWEEN', controlType: 'DATE_RANGE' }
}

function expectedFilters() {
  const groups = [
    [[], 'trade_cal index_classify index_member'],
    [['ts_code'], 'stock_basic stock_company hs_const new_share broker_recommend index_member_all fina_mainbz pledge_stat'],
    [['trade_date'], 'margin moneyflow_hsgt slb_len'],
    [['ts_code', 'trade_date'], 'daily weekly monthly adj_factor suspend_d daily_basic stk_limit moneyflow margin_detail top_list top_inst block_trade hsgt_top10 hk_hold slb_sec slb_sec_detail'],
    [['ts_code', 'ann_date'], 'namechange stk_managers income balancesheet cashflow fina_indicator fina_audit express forecast disclosure_date dividend repurchase share_float stk_rewards stk_holdernumber stk_holdertrade top10_holders top10_floatholders pledge_detail'],
  ]
  const result = new Map()
  for (const [fields, names] of groups) {
    for (const name of names.split(' ')) {
      safeCheck(!result.has(name), 'filter expectation overlap')
      result.set(name, fields.map(filterDescriptor))
    }
  }
  return result
}

const FILTERS = expectedFilters()
const EXPECTED = new Map(EXPECTED_ROWS.map(([apiName, displayName, category, queryMode, parameterNames, columns]) => [
  apiName,
  {
    apiName,
    displayName,
    category,
    queryMode,
    parameters: parameterNames.map((name) => structuredClone(PARAMETER[name])),
    columns,
    filters: FILTERS.get(apiName),
  },
]))

const manifestPath = new URL('../../docs/data-template/manifest.json', import.meta.url)
const manifestBytes = readFileSync(manifestPath)
safeCheck(createHash('sha256').update(manifestBytes).digest('hex') === MANIFEST_SHA, 'manifest hash')
const manifest = JSON.parse(manifestBytes)
safeCheck(Array.isArray(manifest.interfaces) && manifest.interfaces.length === 49, 'manifest count')
const manifestNames = []
for (const entry of manifest.interfaces) {
  safeCheck(/^[a-z][a-z0-9_]{1,63}$/.test(entry.api_name), 'manifest API name')
  safeCheck(entry.filename === `${entry.api_name}.json`, 'manifest filename')
  const contract = EXPECTED.get(entry.api_name)
  safeCheck(Boolean(contract), 'manifest API set')
  const names = []
  for (const sample of entry.params ?? []) {
    safeCheck(sample !== null && typeof sample === 'object' && !Array.isArray(sample), 'manifest params object')
    for (const name of Object.keys(sample)) if (!names.includes(name)) names.push(name)
  }
  expect(names).toEqual(contract.parameters.map(({ name }) => name))
  expect(entry.query_mode === 'range' ? 'date_range' : entry.query_mode).toBe(contract.queryMode)
  manifestNames.push(entry.api_name)
}
safeCheck(new Set(manifestNames).size === 49 && EXPECTED.size === 49 && FILTERS.size === 49, 'independent coverage')
expect(new Set(manifestNames)).toEqual(new Set(EXPECTED.keys()))
const CONTRACTS = manifestNames.map((name) => EXPECTED.get(name))

let application
let applicationLogPath
let sentinel
let sentinelToken
let sentinelCalls = 0
let jarHashBefore
let evidencePath
let privateDbHost
let privateDbSchema
const evidence = {
  version: 1,
  task: 'M14-T04',
  startedAt: undefined,
  finishedAt: undefined,
  environment: {},
  manifest: { sha256: MANIFEST_SHA, count: 49 },
  results: [],
  screenshots: [],
  totals: {},
  cleanup: {},
}

function forbiddenValues() {
  return [
    sentinelToken,
    process.env.TENSOR_DB_PASSWORD,
    process.env.TENSOR_DB_USERNAME,
    process.env.TENSOR_DB_URL,
    privateDbHost,
    privateDbSchema,
  ].filter((value) => typeof value === 'string' && value.length > 0)
}

function assertPublicSurface(text, name) {
  safeCheck(typeof text === 'string', `${name} text`)
  safeCheck(!/jdbc:mysql|"(?:token|password|username|jdbcUrl)"\s*:/i.test(text), `${name} credential keys`)
  safeCheck(forbiddenValues().every((value) => !text.includes(value)), `${name} private values`)
}

async function readPublicJson(response, name) {
  let text
  try {
    text = await response.text()
  } catch {
    throw new Error(`Safe check failed: ${name} body readable`)
  }
  assertPublicSurface(text, name)
  try {
    return JSON.parse(text)
  } catch {
    throw new Error(`Safe check failed: ${name} JSON`)
  }
}

async function assertPageSafety(page, name) {
  const text = await page.locator('body').innerText()
  assertPublicSurface(text, `${name} visible text`)
}

async function sha256File(file) {
  return createHash('sha256').update(await readFile(file)).digest('hex')
}

function processEnvironment() {
  const env = Object.fromEntries(Object.entries(process.env).filter(([name]) =>
    !/^(TENSOR_|SPRING_|SERVER_|MYSQL_|M14_|JAVA_TOOL_OPTIONS$|_JAVA_OPTIONS$|JDK_JAVA_OPTIONS$|MAVEN_OPTS$)/.test(name)))
  for (const name of DB_VARIABLES) env[name] = process.env[name]
  if (sentinelToken) env.TENSOR_TUSHARE_TOKEN = sentinelToken
  if (sentinel) env.TENSOR_TUSHARE_BASE_URL = sentinel.url
  return env
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

async function waitForHealth(current) {
  const deadline = Date.now() + HEALTH_TIMEOUT_MS
  while (Date.now() < deadline) {
    safeCheck(!current.closed, 'owned JVM remains running before readiness')
    try {
      const response = await fetch(`${BASE_URL}/actuator/health`, { signal: AbortSignal.timeout(2_000) })
      const body = await readPublicJson(response, 'health response')
      if (response.status === 200 && body?.status === 'UP') return
    } catch {
      // Readiness polling intentionally retries only this owned local process.
    }
    await delay(250)
  }
  throw new Error('Safe check failed: owned JVM readiness timeout')
}

async function startSentinel() {
  const sockets = new Set()
  const server = createServer((_request, response) => {
    sentinelCalls += 1
    response.writeHead(500, { 'Content-Type': 'application/json' })
    response.end('{"error":"sentinel"}')
  })
  server.on('connection', (socket) => {
    sockets.add(socket)
    socket.once('close', () => sockets.delete(socket))
  })
  await new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', resolve)
  })
  const address = server.address()
  safeCheck(address && typeof address === 'object', 'sentinel address')
  sentinel = { server, sockets, url: `http://127.0.0.1:${address.port}` }
}

async function stopSentinel() {
  if (!sentinel) return
  const current = sentinel
  sentinel = undefined
  for (const socket of current.sockets) socket.destroy()
  await new Promise((resolve, reject) => current.server.close((error) => error ? reject(error) : resolve()))
  evidence.cleanup.sentinel = true
}

async function startApplication() {
  const runDirectory = await mkdtemp(path.join(tmpdir(), 'tensor-m14-t04-'))
  applicationLogPath = path.join(runDirectory, 'application.log')
  evidencePath = path.join(runDirectory, 'metadata-evidence.json')
  const log = await open(applicationLogPath, 'wx', 0o600)
  let child
  try {
    child = spawn('java', [
      '-jar', process.env.ACCEPTANCE_JAR,
      '--spring.profiles.active=acceptance',
      '--tensor.plugins.fixture.enabled=true',
      '--server.address=127.0.0.1',
      '--server.port=8080',
    ], {
      cwd: runDirectory,
      env: processEnvironment(),
      shell: false,
      stdio: ['ignore', log.fd, log.fd],
    })
  } finally {
    await log.close()
  }
  const current = { child, closed: false, signalled: false, result: undefined }
  current.closePromise = new Promise((resolve) => {
    child.once('error', (error) => {
      current.closed = true
      current.result = { error }
      resolve(current.result)
    })
    child.once('close', (code, signal) => {
      current.closed = true
      current.result = { code, signal }
      resolve(current.result)
    })
  })
  application = current
  await waitForHealth(current)
}

async function stopApplication() {
  if (!application) return
  const current = application
  if (!current.closed && !current.signalled) {
    current.signalled = true
    safeCheck(current.child.kill('SIGTERM'), 'owned JVM accepts SIGTERM')
  }
  if (!current.closed) {
    const timeout = Symbol('timeout')
    const controller = new AbortController()
    let result
    try {
      result = await Promise.race([
        current.closePromise,
        delay(STOP_TIMEOUT_MS, timeout, { ref: false, signal: controller.signal }),
      ])
    } finally {
      controller.abort()
    }
    safeCheck(result !== timeout, 'owned JVM exits within 150 seconds')
  }
  application = undefined
  await expect.poll(canConnectToPort, { timeout: 2_000 }).toBe(false)
  evidence.cleanup.jvm = true
}

async function cleanupRuntime() {
  const failures = []
  for (const cleanup of [stopApplication, stopSentinel]) {
    try { await cleanup() } catch (error) { failures.push(error) }
  }
  if (failures.length) throw new AggregateError(failures, 'M14-T04 runtime cleanup failed')
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function optionName(contract) {
  return new RegExp(`^${escapeRegex(contract.displayName)}\\s*${escapeRegex(contract.apiName)}$`)
}

async function selectFrom(combobox, name) {
  await combobox.focus()
  await combobox.press('Enter')
  const option = combobox.page().getByRole('option', { name, exact: typeof name === 'string' })
  await expect(option).toBeVisible()
  await option.click()
  await expect(combobox).toHaveAttribute('aria-expanded', 'false')
  await expect(combobox.page().getByRole('option')).toHaveCount(0)
  await doubleAnimationFrame(combobox.page())
}

async function selectOption(page, label, name) {
  await selectFrom(page.getByRole('combobox', { name: label, exact: true }), name)
}

function isResponse(response, pathname) {
  const url = new URL(response.url())
  return response.request().method() === 'GET' && url.origin === BASE_URL && url.pathname === pathname
}

async function doubleAnimationFrame(page) {
  await page.evaluate(() => new Promise((resolve) =>
    requestAnimationFrame(() => requestAnimationFrame(resolve))))
}

function monitorPage(page) {
  const failures = []
  const scans = []
  const started = new WeakMap()
  const metadata = []
  let downloadPosts = 0
  let recordsGets = 0
  page.on('pageerror', () => failures.push('page-error'))
  page.on('request', (request) => {
    started.set(request, Date.now())
    const url = new URL(request.url())
    if (url.origin !== BASE_URL) failures.push('external-request')
    if (request.method() === 'POST' && url.pathname === '/api/v1/downloads') downloadPosts += 1
    else if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method())) failures.push('write-request')
    if (request.method() === 'GET' && url.pathname.endsWith('/records')) recordsGets += 1
  })
  page.on('requestfailed', () => failures.push('request-failed'))
  page.on('response', (response) => {
    const url = new URL(response.url())
    if (url.pathname.startsWith('/api/v1/')) {
      metadata.push({
        path: url.pathname,
        status: response.status(),
        durationMs: Math.max(0, Date.now() - (started.get(response.request()) ?? Date.now())),
      })
      scans.push(response.text()
        .then((text) => assertPublicSurface(text, 'API response'))
        .catch(() => failures.push('response-safety')))
    }
    if (response.status() >= 400) failures.push('http-error')
  })
  return {
    downloadPosts: () => downloadPosts,
    recordsGets: () => recordsGets,
    metadata: () => structuredClone(metadata),
    async assertClean() {
      await Promise.all(scans)
      await assertPageSafety(page, 'test boundary')
      expect({ downloadPosts, recordsGets }).toEqual({ downloadPosts: 0, recordsGets: 0 })
      expect(failures).toEqual([])
    },
  }
}

function uniqueByApi(items, name) {
  safeCheck(Array.isArray(items) && items.length === 49, `${name} count`)
  const result = new Map()
  for (const item of items) {
    safeCheck(!result.has(item.apiName), `${name} unique API`)
    result.set(item.apiName, item)
  }
  expect(new Set(result.keys())).toEqual(new Set(manifestNames))
  return result
}

function expectedApi(contract) {
  return {
    apiName: contract.apiName,
    displayName: contract.displayName,
    category: contract.category,
    queryMode: contract.queryMode,
    parameters: contract.parameters,
  }
}

function expectedDataset(contract) {
  return {
    pluginId: 'tushare_pro',
    apiName: contract.apiName,
    displayName: contract.displayName,
    category: contract.category,
    queryMode: contract.queryMode,
    filters: contract.filters,
  }
}

async function validateSources(response) {
  expect(response.status()).toBe(200)
  const sources = await readPublicJson(response, 'data sources')
  safeCheck(Array.isArray(sources), 'data sources array')
  for (const source of sources) exactKeys(source, SOURCE_KEYS, 'data source')
  const fixture = sources.find(({ pluginId }) => pluginId === 'fixture')
  const tushare = sources.find(({ pluginId }) => pluginId === 'tushare_pro')
  safeCheck(Boolean(fixture), 'fixture source present')
  expect(tushare).toEqual({
    pluginId: 'tushare_pro',
    displayName: 'Tushare Pro',
    description: 'Tushare Pro 证券数据源',
    enabled: true,
    credentialConfigured: true,
    downloadAvailable: true,
    unavailableReason: null,
  })
}

async function validateApis(response) {
  expect(response.status()).toBe(200)
  const apis = uniqueByApi(await readPublicJson(response, 'API descriptors'), 'API descriptors')
  for (const contract of CONTRACTS) {
    exactKeys(apis.get(contract.apiName), API_KEYS, 'API descriptor')
    expect(apis.get(contract.apiName)).toEqual(expectedApi(contract))
  }
}

async function validateDatasets(response) {
  expect(response.status()).toBe(200)
  const summaries = uniqueByApi(await readPublicJson(response, 'dataset summaries'), 'dataset summaries')
  for (const contract of CONTRACTS) {
    const summary = summaries.get(contract.apiName)
    exactKeys(summary, SUMMARY_KEYS, 'dataset summary')
    const { fixedColumn, ...actual } = summary
    expect(actual).toEqual(expectedDataset(contract))
    expect(fixedColumn).toMatch(/^[a-z][a-z0-9_]{1,63}$/)
  }
  return summaries
}

async function validateDefinition(response, contract, summary) {
  expect(response.status()).toBe(200)
  const definition = await readPublicJson(response, 'dataset definition')
  exactKeys(definition, DEFINITION_KEYS, 'dataset definition')
  const { columns, fixedColumn, ...actual } = definition
  expect(actual).toEqual(expectedDataset(contract))
  expect(fixedColumn).toBe(summary.fixedColumn)
  expect(columns).toHaveLength(contract.columns)
  expect(new Set(columns.map(({ name }) => name)).size).toBe(contract.columns)
  expect(columns.map(({ displayOrder }) => displayOrder)).toEqual(
    Array.from({ length: contract.columns }, (_, index) => index),
  )
  for (const column of columns) {
    safeCheck(Object.keys(column).every((key) => COLUMN_KEYS.includes(key)), 'column response fields')
    safeCheck(!['business_key', 'source_plugin', 'source_api', 'ingested_at'].includes(column.name), 'business columns only')
  }
  expect(columns.some(({ name }) => name === fixedColumn)).toBe(true)
  return definition
}

async function openDownloads(page, contract) {
  const sourcesPromise = page.waitForResponse((response) => isResponse(response, '/api/v1/data-sources'))
  const navigation = await page.goto('/downloads')
  expect(navigation?.status()).toBe(200)
  await expect(page.getByRole('heading', { level: 1, name: '数据下载' })).toBeVisible()
  await validateSources(await sourcesPromise)
  const apisPromise = page.waitForResponse((response) =>
    isResponse(response, '/api/v1/data-sources/tushare_pro/apis'))
  await selectOption(page, '数据源', 'Tushare Pro')
  await validateApis(await apisPromise)

  const combobox = page.getByRole('combobox', { name: '数据接口', exact: true })
  await combobox.focus()
  await combobox.press('Enter')
  await expect(page.getByRole('option')).toHaveCount(49)
  for (const expected of CONTRACTS) {
    await expect(page.getByRole('option', { name: optionName(expected) })).toHaveCount(1)
  }
  if (contract.apiName === CONTRACTS[0].apiName) {
    for (const category of Object.keys(CATEGORY_COUNTS)) {
      await expect(page.getByText(category, { exact: true }).last()).toBeVisible()
    }
  }
  await page.getByRole('option', { name: optionName(contract) }).click()
  await expect(combobox).toHaveAttribute('aria-expanded', 'false')
  await expect(page.getByRole('option')).toHaveCount(0)
  await doubleAnimationFrame(page)

  const description = page.getByRole('region', { name: '接口说明', exact: true })
  await expect(description).toBeVisible()
  for (const text of [contract.displayName, contract.apiName, contract.category, QUERY_MODE_LABELS[contract.queryMode]]) {
    await expect(description.getByText(text, { exact: true })).toBeVisible()
  }
}

function parameterControl(page, parameter) {
  return parameter.type === 'ENUM'
    ? page.getByRole('combobox', { name: parameter.label, exact: true })
    : page.getByLabel(new RegExp(`^${escapeRegex(parameter.label)}\\s*\\*?$`))
}

async function screenshot(page, testInfo, name) {
  await assertPageSafety(page, 'screenshot')
  await doubleAnimationFrame(page)
  const output = testInfo.outputPath(name)
  await page.screenshot({ path: output, fullPage: true, animations: 'disabled' })
  evidence.screenshots.push({ name, path: output, sha256: await sha256File(output), manuallyReviewed: false })
}

async function validateParameters(page, contract, testInfo) {
  const controls = contract.parameters.map((parameter) => parameterControl(page, parameter))
  for (let index = 0; index < contract.parameters.length; index += 1) {
    const parameter = contract.parameters[index]
    const control = controls[index]
    await expect(page.getByText(new RegExp(`^${escapeRegex(parameter.label)}\\s*\\*$`))).toBeVisible()
    await expect(control).toBeVisible()
    await expect(control).toHaveAttribute('aria-required', 'true')
    if (index) {
      const ordered = await controls[index - 1].evaluate((previous, current) =>
        Boolean(previous.compareDocumentPosition(current) & Node.DOCUMENT_POSITION_FOLLOWING),
      await control.elementHandle())
      expect(ordered).toBe(true)
    }
    if (parameter.type === 'ENUM') {
      await control.focus()
      await control.press('Enter')
      await expect(page.getByRole('option')).toHaveText(parameter.allowedValues)
      await control.press('Escape')
    }
  }

  const button = page.getByRole('button', { name: '开始下载', exact: true })
  await expect(button).toBeEnabled()
  if (!contract.parameters.length) {
    await expect(page.getByText('此项为必填项', { exact: true })).toHaveCount(0)
    await expect(page.getByRole('combobox')).toHaveCount(2)
    await expect(page.getByRole('textbox')).toHaveCount(0)
    if (DOWNLOAD_SCREENSHOTS.has(contract.apiName)) await screenshot(page, testInfo, `download-${contract.apiName}.png`)
    return { requiredBlocked: false, parameterless: true }
  }

  await button.click()
  await expect(page.getByText('此项为必填项', { exact: true })).toHaveCount(contract.parameters.length)
  for (const control of controls) {
    await expect(control).toHaveAttribute('aria-invalid', 'true')
    const errorId = await control.getAttribute('aria-describedby')
    safeCheck(Boolean(errorId), 'required error described by')
    const ids = errorId.split(/\s+/)
    const matches = await Promise.all(ids.map(async (id) => (await page.locator(`#${id}`).textContent())?.trim()))
    expect(matches).toContain('此项为必填项')
  }
  await expect(controls[0]).toBeFocused()
  await doubleAnimationFrame(page)

  if (contract.apiName === 'trade_cal') await screenshot(page, testInfo, 'download-trade_cal.png')
  if (contract.apiName === 'broker_recommend') {
    await controls[0].click()
    await expect(controls[0]).toHaveAttribute('aria-expanded', 'true')
    await doubleAnimationFrame(page)
    await screenshot(page, testInfo, 'download-broker_recommend.png')
    await controls[0].press('Escape')
  }

  for (let index = 0; index < contract.parameters.length; index += 1) {
    const parameter = contract.parameters[index]
    const control = controls[index]
    if (parameter.type === 'ENUM') {
      await selectFrom(control, parameter.allowedValues[0])
    } else {
      const values = {
        trade_date: '2026-08-07', ann_date: '2026-08-07', start_date: '2026-08-01',
        end_date: '2026-08-07', month: '2026-08', ts_code: '000001.SZ',
      }
      if (['DATE', 'DATE_RANGE_MEMBER', 'MONTH'].includes(parameter.type)) {
        await control.click()
        await control.press('Escape')
      }
      await control.fill(values[parameter.name])
      await control.press('Tab')
      if (['DATE', 'DATE_RANGE_MEMBER', 'MONTH'].includes(parameter.type)) await page.keyboard.press('Escape')
      await expect(control).toHaveValue(values[parameter.name])
    }
    await expect(control).not.toHaveAttribute('aria-invalid', 'true')
    await expect(page.getByText('此项为必填项', { exact: true })).toHaveCount(contract.parameters.length - index - 1)
  }
  if (DOWNLOAD_SCREENSHOTS.has(contract.apiName) && !['trade_cal', 'broker_recommend'].includes(contract.apiName)) {
    await screenshot(page, testInfo, `download-${contract.apiName}.png`)
  }
  return { requiredBlocked: true, parameterless: false }
}

async function openDataset(page, contract) {
  const sourcesPromise = page.waitForResponse((response) => isResponse(response, '/api/v1/data-sources'))
  await page.getByRole('link', { name: '数据查看', exact: true }).click()
  await expect(page).toHaveURL(`${BASE_URL}/datasets`)
  await expect(page.getByRole('heading', { level: 1, name: '数据查看' })).toBeVisible()
  await validateSources(await sourcesPromise)

  const summariesPromise = page.waitForResponse((response) =>
    isResponse(response, '/api/v1/data-sources/tushare_pro/datasets'))
  await selectOption(page, '数据源', 'Tushare Pro')
  const summaries = await validateDatasets(await summariesPromise)
  const definitionPromise = page.waitForResponse((response) =>
    isResponse(response, `/api/v1/data-sources/tushare_pro/datasets/${contract.apiName}`))
  await selectOption(page, '数据集', optionName(contract))
  const definition = await validateDefinition(await definitionPromise, contract, summaries.get(contract.apiName))
  return definition
}

async function validateFilterControls(page, contract, testInfo) {
  const expectedLabels = contract.filters.flatMap(({ field }) => FILTER_LABELS[field])
  const orderedControls = expectedLabels.map((label) => page.getByLabel(label, { exact: true }))
  for (const labels of Object.values(FILTER_LABELS)) {
    for (const label of labels) {
      const control = page.getByLabel(label, { exact: true })
      if (expectedLabels.includes(label)) {
        await expect(control).toBeVisible()
        await expect(control).toHaveValue('')
        await expect(control).not.toHaveAttribute('aria-required', 'true')
        await expect(control).not.toHaveAttribute('aria-invalid', 'true')
      } else {
        await expect(control).toHaveCount(0)
      }
    }
  }
  for (let index = 1; index < orderedControls.length; index += 1) {
    const ordered = await orderedControls[index - 1].evaluate((previous, current) =>
      Boolean(previous.compareDocumentPosition(current) & Node.DOCUMENT_POSITION_FOLLOWING),
    await orderedControls[index].elementHandle())
    expect(ordered).toBe(true)
  }
  await expect(page.getByRole('heading', { name: '设置筛选条件后查询' })).toBeVisible()
  await expect(page.getByRole('columnheader')).toHaveCount(0)
  await expect(page.getByRole('navigation', { name: '数据集分页', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '查询', exact: true })).toBeEnabled()
  await expect(page.getByRole('button', { name: '重置', exact: true })).toBeEnabled()
  await expect(page.getByRole('alert')).toHaveCount(0)
  await doubleAnimationFrame(page)
  if (DATASET_SCREENSHOTS.has(contract.apiName)) await screenshot(page, testInfo, `dataset-${contract.apiName}.png`)
}

test.use({
  viewport: { width: 1440, height: 1000 },
  trace: 'off',
  video: 'off',
  screenshot: 'off',
})

test.describe('Tushare 49 metadata contracts', () => {
  test.describe.configure({ mode: 'serial', retries: 0, timeout: 120_000 })

  test.beforeAll(async () => {
    test.setTimeout(180_000)
    evidence.startedAt = new Date().toISOString()
    safeCheck(path.isAbsolute(process.env.ACCEPTANCE_JAR ?? ''), 'acceptance JAR absolute path')
    const jarState = await lstat(process.env.ACCEPTANCE_JAR)
    safeCheck(jarState.isFile() && !jarState.isSymbolicLink(), 'acceptance JAR ordinary file')
    for (const name of DB_VARIABLES) safeCheck(Boolean(process.env[name]), `${name} supplied`)
    const jdbc = process.env.TENSOR_DB_URL
    const match = /^jdbc:mysql:\/\/([^/?#@]+)\/(tensor_m14_t04_[a-f0-9]+)(?:\?([^#]*))?$/.exec(jdbc)
    safeCheck(Boolean(match), 'dedicated JDBC schema')
    privateDbHost = match[1]
    privateDbSchema = match[2]
    const query = new URLSearchParams(match[3] ?? '')
    safeCheck(![...query.keys()].some((key) => /^(?:user|username|password)$/i.test(key)), 'JDBC excludes credentials')
    safeCheck((process.env.PLAYWRIGHT_BASE_URL ?? BASE_URL) === BASE_URL, 'Playwright base URL')
    safeCheck(!(await canConnectToPort()), 'port 8080 unused')
    const java = await execFileAsync('java', ['-version'], { env: processEnvironment() })
    safeCheck(/version "21(?:\.|\")/.test(`${java.stdout}${java.stderr}`), 'Java 21')
    jarHashBefore = await sha256File(process.env.ACCEPTANCE_JAR)
    expect(jarHashBefore).toBe(ACCEPTANCE_JAR_SHA)
    const playwrightPackage = JSON.parse(await readFile(new URL('../node_modules/@playwright/test/package.json', import.meta.url), 'utf8'))
    evidence.environment = {
      node: process.version,
      java: 21,
      playwright: playwrightPackage.version,
      acceptanceJarSha256: jarHashBefore,
    }
    sentinelToken = `m14t04_${randomBytes(24).toString('hex')}`
    try {
      await startSentinel()
      await startApplication()
    } catch (error) {
      await cleanupRuntime()
      throw error
    }
  })

  test.afterAll(async () => {
    test.setTimeout(180_000)
    const failures = []
    try { await cleanupRuntime() } catch (error) { failures.push(error) }
    if (!jarHashBefore || !applicationLogPath || !evidencePath) {
      if (failures.length) throw new AggregateError(failures, 'M14-T04 partial startup cleanup failed')
      return
    }
    try {
      const jarHashAfter = await sha256File(process.env.ACCEPTANCE_JAR)
      expect(jarHashAfter).toBe(jarHashBefore)
      evidence.environment.acceptanceJarSha256After = jarHashAfter
      const privateLog = await readFile(applicationLogPath, 'utf8')
      safeCheck(!privateLog.includes(sentinelToken), 'private log excludes fake token')
      safeCheck(!privateLog.includes(process.env.TENSOR_DB_PASSWORD), 'private log excludes DB password')
      evidence.cleanup.privateLogScanned = true
      evidence.finishedAt = new Date().toISOString()
      evidence.totals = {
        cases: evidence.results.length,
        apiPassed: evidence.results.filter(({ apiPassed }) => apiPassed).length,
        datasetsPassed: evidence.results.filter(({ datasetPassed }) => datasetPassed).length,
        requiredBlocked: evidence.results.filter(({ requiredBlocked }) => requiredBlocked).length,
        parameterless: evidence.results.filter(({ parameterless }) => parameterless).length,
        downloadPosts: evidence.results.reduce((sum, result) => sum + result.downloadPosts, 0),
        recordsGets: evidence.results.reduce((sum, result) => sum + result.recordsGets, 0),
        upstreamCalls: sentinelCalls,
        screenshots: evidence.screenshots.length,
      }
      const expectedTotals = {
        cases: 49, apiPassed: 49, datasetsPassed: 49, requiredBlocked: 43,
        parameterless: 6, downloadPosts: 0, recordsGets: 0, upstreamCalls: 0, screenshots: 13,
      }
      if (evidence.results.length === 49) expect(evidence.totals).toEqual(expectedTotals)
      else expect(sentinelCalls, 'failed run must still make zero upstream calls').toBe(0)
      const serialized = `${JSON.stringify(evidence, null, 2)}\n`
      assertPublicSurface(serialized, 'evidence JSON')
      await writeFile(evidencePath, serialized, { encoding: 'utf8', flag: 'wx', mode: 0o600 })
      console.log(`M14-T04 private evidence: ${evidencePath}`)
    } catch (error) {
      failures.push(error)
    }
    if (failures.length) throw new AggregateError(failures, 'M14-T04 final verification failed')
  })

  for (const contract of CONTRACTS) {
    test(`metadataContract:${contract.apiName}`, async ({ page, browser }, testInfo) => {
      const startedAt = Date.now()
      if (!evidence.environment.chromium) evidence.environment.chromium = browser.version()
      const monitor = monitorPage(page)
      await openDownloads(page, contract)
      const form = await validateParameters(page, contract, testInfo)
      expect(monitor.downloadPosts()).toBe(0)
      const definition = await openDataset(page, contract)
      expect(definition.filters).toEqual(contract.filters)
      await validateFilterControls(page, contract, testInfo)
      expect(monitor.recordsGets()).toBe(0)
      await monitor.assertClean()
      evidence.results.push({
        api: contract.apiName,
        category: contract.category,
        queryMode: contract.queryMode,
        parameters: contract.parameters.map(({ name, label, type, required, allowedValues }) => ({
          name, label, type, required, ...(allowedValues ? { allowedValues } : {}),
        })),
        filters: contract.filters,
        apiPassed: true,
        datasetPassed: true,
        ...form,
        downloadPosts: monitor.downloadPosts(),
        recordsGets: monitor.recordsGets(),
        metadataRequests: monitor.metadata(),
        durationMs: Date.now() - startedAt,
      })
    })
  }
})
