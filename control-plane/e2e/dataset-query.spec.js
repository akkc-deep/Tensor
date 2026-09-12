import { configurePackagedEnvironment } from './packaged-test-environment.js'
import { expect, test } from '@playwright/test'
import { spawn } from 'node:child_process'
import { createHash, randomBytes } from 'node:crypto'
import {
  lstat,
  mkdtemp,
  open,
  readFile,
  stat,
  writeFile,
} from 'node:fs/promises'
import { createServer } from 'node:http'
import { createConnection } from 'node:net'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { setTimeout as delay } from 'node:timers/promises'

const BASE_URL = 'http://127.0.0.1:8080'
const ACCEPTANCE_JAR_SHA = process.env.ISSUE_017_ACCEPTANCE_JAR_SHA256
const HEALTH_TIMEOUT_MS = 90_000
const STOP_TIMEOUT_MS = 150_000
const MYSQL_TIMEOUT_MS = 15_000
const INITIAL_DOWNLOAD_COUNT = 252
const TOTAL_DOWNLOAD_COUNT = 375
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
const LONG_TEXT = `M14_T03_TEXT_${'查询说明'.repeat(80)}`
const SOURCE_COLUMNS = ['source_plugin', 'source_api', 'ingested_at']
const DAILY_HEADERS = [
  '证券代码ts_code', '交易日trade_date', '开盘价open', '最高价high', '最低价low',
  '收盘价close', '前收盘价pre_close', '涨跌额change', '涨跌幅（%）pct_chg',
  '成交量vol', '成交额amount', '来源插件source_plugin', '来源接口source_api', '入库时间ingested_at',
]
const PAGE_KEYS = [
  'requestId',
  'pluginId',
  'apiName',
  'page',
  'pageSize',
  'totalElements',
  'totalPages',
  'columns',
  'items',
]
const DEFINITION_KEYS = [
  'pluginId',
  'apiName',
  'displayName',
  'category',
  'queryMode',
  'filters',
  'fixedColumn',
  'columns',
]
const DB_VARIABLES = [
  'TENSOR_DB_URL',
  'TENSOR_DB_USERNAME',
  'TENSOR_DB_PASSWORD',
]
const DATASETS = {
  daily: { option: /^日线行情daily$/, fields: 11 },
  stock_company: { option: /^上市公司基本信息stock_company$/, fields: 18 },
  index_classify: { option: /^行业指数分类index_classify$/, fields: 7 },
  balancesheet: { option: /^资产负债表balancesheet$/, fields: 152 },
  disclosure_date: { option: /^财报披露计划disclosure_date$/, fields: 5 },
}
const FILTER_LABELS = {
  tsCode: '证券代码 (ts_code)',
  tradeDateFrom: '交易日期开始 (trade_date)',
  tradeDateTo: '交易日期结束 (trade_date)',
  annDateFrom: '公告日期开始 (ann_date)',
  annDateTo: '公告日期结束 (ann_date)',
}

let application
let applicationLogPath
let mysqlConfig
let upstream
let upstreamToken
let fieldsByApi
let downloadPostCount = 0
let recordsResponseCount = 0
const requestIds = new Set()
const expectedEvents = []
const expectedTasks = []
const ingestionBaselines = new Map()
const evidence = {
  task: 'M14-T03',
  startedAt: undefined,
  finishedAt: undefined,
  environment: {},
  results: [],
  requests: [],
  events: [],
  screenshots: [],
  geometry: [],
  raceReleaseOrder: [],
  database: {},
  cleanup: {},
}

function safeCheck(passed, name) {
  if (!passed) throw new Error(`Safe check failed: ${name}`)
}

function exactKeys(value, keys, name) {
  safeCheck(
    value !== null && typeof value === 'object' && !Array.isArray(value),
    `${name} is an object`,
  )
  safeCheck(
    JSON.stringify(Object.keys(value).sort()) === JSON.stringify([...keys].sort()),
    `${name} fields are exact`,
  )
}

function forbiddenValues() {
  return [
    upstreamToken,
    process.env.TENSOR_DB_PASSWORD,
    process.env.TENSOR_DB_USERNAME,
    process.env.TENSOR_DB_URL,
    mysqlConfig?.password,
    mysqlConfig?.host,
    mysqlConfig?.schema,
  ].filter((value) => typeof value === 'string' && value.length > 0)
}

function assertPublicSurface(text, name) {
  safeCheck(typeof text === 'string', `${name} is text`)
  safeCheck(
    !/jdbc:mysql|\b(?:SELECT|CREATE|DROP|ALTER|INSERT|UPDATE|DELETE)\b[\s\S]*\b(?:FROM|TABLE|INTO|SET)\b/i.test(
      text,
    ),
    `${name} excludes connection and SQL markers`,
  )
  safeCheck(
    forbiddenValues().every((value) => !text.includes(value)),
    `${name} excludes private values`,
  )
}

function assertPrivateLog(text) {
  safeCheck(
    [upstreamToken, process.env.TENSOR_DB_PASSWORD, mysqlConfig?.password]
      .filter((value) => typeof value === 'string' && value.length > 0)
      .every((value) => !text.includes(value)),
    'private log excludes credentials',
  )
  safeCheck(!text.includes('"api_name"'), 'private log excludes upstream envelopes')
}

async function readPublicJson(response, name) {
  let text
  try {
    text = await response.text()
  } catch {
    throw new Error(`Safe check failed: ${name} body is readable`)
  }
  assertPublicSurface(text, name)
  try {
    return JSON.parse(text)
  } catch {
    throw new Error(`Safe check failed: ${name} is JSON`)
  }
}

async function assertPageSafety(page, name) {
  let text
  try {
    text = await page.locator('body').innerText()
  } catch {
    throw new Error(`Safe check failed: ${name} visible text is readable`)
  }
  assertPublicSurface(text, `${name} visible text`)
}

async function runWithCleanup(action, cleanup, message) {
  let primary
  let cleanupFailure
  try {
    await action()
  } catch (error) {
    primary = error
  }
  try {
    await cleanup()
  } catch (error) {
    cleanupFailure = error
  }
  if (primary && cleanupFailure) {
    throw new AggregateError([primary, cleanupFailure], message)
  }
  if (primary) throw primary
  if (cleanupFailure) throw cleanupFailure
}

async function withinDeadline(promise, timeout, name) {
  let timer
  const deadline = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error(`Safe check failed: ${name}`)), timeout)
  })
  try {
    return await Promise.race([promise, deadline])
  } finally {
    clearTimeout(timer)
  }
}

function watchRequestSettlement(page, predicate) {
  let active = true
  let finishedHandler
  let failedHandler
  const removeListeners = () => {
    page.off('requestfinished', finishedHandler)
    page.off('requestfailed', failedHandler)
  }
  const promise = new Promise((resolve) => {
    const settle = (type, request) => {
      if (!active || !predicate(request)) return
      active = false
      removeListeners()
      resolve({ type, request })
    }
    finishedHandler = (request) => settle('finished', request)
    failedHandler = (request) => settle('failed', request)
    page.on('requestfinished', finishedHandler)
    page.on('requestfailed', failedHandler)
  })
  return {
    promise,
    cancel() {
      if (!active) return
      active = false
      removeListeners()
    },
  }
}

function parseDefaultsText(text) {
  safeCheck(!text.startsWith('\ufeff'), 'MySQL defaults excludes BOM')
  safeCheck(!/\r(?!\n)/.test(text), 'MySQL defaults line endings are valid')
  const normalized = text.replace(/(?:\r\n|\n)$/, '')
  safeCheck(!/\n$/.test(normalized), 'MySQL defaults has at most one terminal newline')
  const lines = normalized.split(/\r?\n/)
  safeCheck(lines.length === 6, 'MySQL defaults has six lines')
  safeCheck(lines[0] === '[client]', 'MySQL defaults client group is exact')
  const values = new Map()
  for (const line of lines.slice(1)) {
    safeCheck(/^[a-z]+=.*$/.test(line), 'MySQL defaults entry syntax is valid')
    const separator = line.indexOf('=')
    const key = line.slice(0, separator)
    const value = line.slice(separator + 1)
    safeCheck(
      ['host', 'port', 'user', 'password', 'protocol'].includes(key),
      'MySQL defaults key is allowed',
    )
    safeCheck(!values.has(key), 'MySQL defaults keys are unique')
    values.set(key, value)
  }
  safeCheck(
    JSON.stringify([...values.keys()].sort()) ===
      JSON.stringify(['host', 'password', 'port', 'protocol', 'user']),
    'MySQL defaults keys are complete',
  )
  safeCheck(/^[A-Za-z0-9.-]+$/.test(values.get('host')), 'MySQL host is valid')
  safeCheck(/^\d+$/.test(values.get('port')), 'MySQL port is numeric')
  const port = Number(values.get('port'))
  safeCheck(port >= 1 && port <= 65_535, 'MySQL port is in range')
  safeCheck(values.get('protocol') === 'TCP', 'MySQL protocol is TCP')
  for (const key of ['user', 'password']) {
    safeCheck(
      /^[\x21-\x7e]+$/.test(values.get(key)) && !/[\s'"\\#;]/.test(values.get(key)),
      `MySQL ${key} is safe for defaults syntax`,
    )
  }
  return {
    host: values.get('host'),
    port: String(port),
    user: values.get('user'),
    password: values.get('password'),
  }
}

function validateJdbc(jdbc, schema, defaults) {
  safeCheck(jdbc.startsWith('jdbc:mysql://'), 'JDBC URL uses MySQL')
  let parsed
  try {
    parsed = new URL(jdbc.slice(5))
  } catch {
    throw new Error('Safe check failed: JDBC URL is valid')
  }
  safeCheck(parsed.username === '', 'JDBC URL excludes username')
  safeCheck(parsed.password === '', 'JDBC URL excludes password')
  safeCheck(parsed.hostname === defaults.host, 'JDBC and CLI hosts match')
  safeCheck(Number(parsed.port || '3306') === Number(defaults.port), 'JDBC and CLI ports match')
  safeCheck(parsed.pathname === `/${schema}`, 'JDBC and CLI schemas match')
  for (const name of parsed.searchParams.keys()) {
    safeCheck(
      !/^(?:user|username|password|token)$/i.test(name),
      'JDBC URL excludes credential parameters',
    )
  }
}

function processEnvironment(stubUrl, token) {
  const env = Object.fromEntries(
    Object.entries(process.env).filter(
      ([name]) => !/^(TENSOR_|SPRING_|SERVER_|MYSQL_|M14_)/.test(name),
    ),
  )
  for (const name of DB_VARIABLES) env[name] = process.env[name]
  env.TENSOR_TUSHARE_TOKEN = token
  env.TENSOR_TUSHARE_BASE_URL = stubUrl
  return env
}

async function syntheticProbes() {
  const sample = parseDefaultsText(
    '[client]\r\nhost=127.0.0.1\r\nport=3306\r\nuser=probe\r\npassword=probe-safe\r\nprotocol=TCP\r\n',
  )
  safeCheck(sample.port === '3306', 'CRLF defaults probe passes')

  const secret = 'probe invalid secret'
  let invalidMessage = ''
  try {
    parseDefaultsText(
      `[client]\nhost=127.0.0.1\nport=3306\nuser=probe\npassword=${secret}\nprotocol=TCP\n`,
    )
  } catch (error) {
    invalidMessage = error.message
  }
  safeCheck(
    invalidMessage === 'Safe check failed: MySQL password is safe for defaults syntax',
    'invalid defaults probe has fixed failure',
  )
  safeCheck(!invalidMessage.includes(secret), 'invalid defaults probe hides input')

  let jdbcMessage = ''
  try {
    validateJdbc('jdbc:mysql://probe:probe-safe@127.0.0.1:3306/probe', 'probe', sample)
  } catch (error) {
    jdbcMessage = error.message
  }
  safeCheck(
    jdbcMessage === 'Safe check failed: JDBC URL excludes username',
    'credential JDBC probe has fixed failure',
  )

  let keyMessage = ''
  try {
    exactKeys({ requestId: 'safe', extra: 'value' }, ['requestId'], 'probe response')
  } catch (error) {
    keyMessage = error.message
  }
  safeCheck(
    keyMessage === 'Safe check failed: probe response fields are exact',
    'extra public field probe fails safely',
  )

  const previousToken = upstreamToken
  upstreamToken = 'probe-page-secret'
  let pageMessage = ''
  try {
    assertPublicSurface(`visible ${upstreamToken}`, 'probe page')
  } catch (error) {
    pageMessage = error.message
  } finally {
    upstreamToken = previousToken
  }
  safeCheck(
    pageMessage === 'Safe check failed: probe page excludes private values',
    'page secret probe fails safely',
  )

  let aggregate
  try {
    await runWithCleanup(
      async () => { throw new Error('probe-primary') },
      async () => { throw new Error('probe-cleanup') },
      'probe aggregate',
    )
  } catch (error) {
    aggregate = error
  }
  safeCheck(aggregate instanceof AggregateError, 'dual failure probe is aggregate')
  safeCheck(aggregate.errors.length === 2, 'dual failure probe retains both errors')

  safeCheck(
    await withinDeadline(Promise.resolve('complete'), 1_000, 'resolved deadline probe') === 'complete',
    'resolved deadline probe passes',
  )
  let deadlineMessage = ''
  try {
    await withinDeadline(new Promise(() => {}), 1, 'synthetic deadline expires')
  } catch (error) {
    deadlineMessage = error.message
  }
  safeCheck(
    deadlineMessage === 'Safe check failed: synthetic deadline expires',
    'expired deadline probe has fixed failure',
  )

  const previousFields = fieldsByApi
  fieldsByApi = Object.fromEntries(Object.entries(DATASETS).map(([api]) => [api, ['probe']]))
  const malformedUpstream = createUpstream('probe-token')
  try {
    await new Promise((resolve, reject) => {
      malformedUpstream.server.once('error', reject)
      malformedUpstream.server.listen(0, '127.0.0.1', resolve)
    })
    const address = malformedUpstream.server.address()
    safeCheck(typeof address === 'object' && address !== null, 'malformed upstream probe address exists')
    const cases = [
      ['daily-main', null],
      ['daily-earlier', false],
      ['company', 0],
      ['index', ''],
      ['balance', {}],
    ]
    for (const [mode, value] of cases) {
      const stockCode = modeDefinition(mode).stockScoped ? '000001.SZ' : undefined
      malformedUpstream.setMode(mode, stockCode)
      const response = await fetch(`http://127.0.0.1:${address.port}/`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(value),
      })
      const body = await response.json()
      safeCheck(response.status === 500 && body.code === -1, 'malformed upstream envelope fails closed')
      for (const check of ['keys', 'api', 'token', 'params', 'fields']) {
        safeCheck(
          malformedUpstream.failures.includes(`${mode}:${stockCode ?? ''}:${check}`),
          'malformed upstream envelope records every field failure',
        )
      }
    }
  } finally {
    fieldsByApi = previousFields
    if (malformedUpstream.server.listening) {
      malformedUpstream.server.closeAllConnections()
      await new Promise((resolve, reject) => malformedUpstream.server.close((error) => {
        if (error) reject(error)
        else resolve()
      }))
    }
  }

  const probePage = () => {
    const listeners = new Map()
    return {
      on(name, handler) {
        if (!listeners.has(name)) listeners.set(name, new Set())
        listeners.get(name).add(handler)
      },
      off(name, handler) { listeners.get(name)?.delete(handler) },
      emit(name, value) {
        for (const handler of listeners.get(name) ?? []) handler(value)
      },
      locator() { return { innerText: async () => '' } },
    }
  }
  const probeRequest = (pathname) => ({
    method: () => 'GET',
    url: () => `${BASE_URL}${pathname}`,
  })
  const resourceResponsePage = probePage()
  const resourceResponseMonitor = monitorPage(resourceResponsePage)
  resourceResponsePage.emit('response', {
    status: () => 404,
    url: () => `${BASE_URL}/probe.css`,
  })
  let resourceResponseRejected = false
  try { await resourceResponseMonitor.assertClean() } catch { resourceResponseRejected = true }
  safeCheck(resourceResponseRejected, 'same-origin resource HTTP error probe is rejected')

  const resourceFailurePage = probePage()
  const resourceFailureMonitor = monitorPage(resourceFailurePage)
  resourceFailurePage.emit('requestfailed', probeRequest('/probe.css'))
  let resourceFailureRejected = false
  try { await resourceFailureMonitor.assertClean() } catch { resourceFailureRejected = true }
  safeCheck(resourceFailureRejected, 'same-origin resource failure probe is rejected')

  const abortPage = probePage()
  const abortMonitor = monitorPage(abortPage)
  const expectedAbort = probeRequest(`${recordsPath('daily')}?probe=expected`)
  abortMonitor.allowOneRecordsFailure((request) => request === expectedAbort)
  abortPage.emit('requestfailed', expectedAbort)
  await abortMonitor.assertClean()

  const unexpectedFailurePage = probePage()
  const unexpectedFailureMonitor = monitorPage(unexpectedFailurePage)
  unexpectedFailureMonitor.allowOneRecordsFailure((request) => request === expectedAbort)
  unexpectedFailurePage.emit('requestfailed', probeRequest(`${recordsPath('daily')}?probe=other`))
  let unexpectedFailureRejected = false
  try { await unexpectedFailureMonitor.assertClean() } catch { unexpectedFailureRejected = true }
  safeCheck(unexpectedFailureRejected, 'unregistered request failure probe is rejected')

  const pendingPage = probePage()
  const pendingMonitor = monitorPage(pendingPage)
  const pendingRequest = probeRequest('/api/v1/download-tasks?page=1&pageSize=20')
  pendingPage.emit('request', pendingRequest)
  let pendingDrainCompleted = false
  const pendingDrain = pendingMonitor.drainResponseScans('probe:pending-request')
    .then(() => { pendingDrainCompleted = true })
  await delay(0)
  safeCheck(!pendingDrainCompleted, 'response drain waits for request before response')
  pendingPage.emit('response', {
    status: () => 200,
    url: pendingRequest.url,
    request: () => pendingRequest,
    text: async () => '{}',
  })
  pendingPage.emit('requestfinished', pendingRequest)
  await pendingDrain
  await pendingMonitor.assertClean()

  const rejectedBodyPage = probePage()
  const rejectedBodyMonitor = monitorPage(rejectedBodyPage)
  const rejectedBodyRequest = probeRequest('/api/v1/download-tasks?page=1&pageSize=20')
  rejectedBodyPage.emit('response', {
    status: () => 200,
    url: rejectedBodyRequest.url,
    request: () => rejectedBodyRequest,
    text: async () => { throw new Error('No resource with given identifier found') },
  })
  let rejectedBodyDetected = false
  try { await rejectedBodyMonitor.assertClean() } catch { rejectedBodyDetected = true }
  safeCheck(rejectedBodyDetected, 'API response body rejection probe is rejected')

  const original = process.env.MYSQL_PWD
  process.env.MYSQL_PWD = 'probe-only'
  try {
    safeCheck(!Object.hasOwn(processEnvironment('http://127.0.0.1/', 'probe'), 'MYSQL_PWD'), 'JVM env excludes MYSQL variables')
  } finally {
    if (original === undefined) delete process.env.MYSQL_PWD
    else process.env.MYSQL_PWD = original
  }
}

async function parseMysqlInputs() {
  const defaultsPath = process.env.M14_MYSQL_DEFAULTS_FILE ?? ''
  const schema = process.env.M14_DB_SCHEMA ?? ''
  safeCheck(path.isAbsolute(defaultsPath), 'MySQL defaults path is absolute')
  safeCheck(/^tensor_m14_t03_[a-f0-9]+$/.test(schema), 'schema is isolated')
  const file = await lstat(defaultsPath)
  safeCheck(file.isFile(), 'MySQL defaults is a regular file')
  safeCheck(!file.isSymbolicLink(), 'MySQL defaults is not a symlink')
  safeCheck((file.mode & 0o777) === 0o600, 'MySQL defaults permissions are 0600')
  if (typeof process.getuid === 'function') {
    safeCheck(file.uid === process.getuid(), 'MySQL defaults owner is current user')
  }
  const bytes = await readFile(defaultsPath)
  safeCheck(
    !bytes.subarray(0, 3).equals(Buffer.from([0xef, 0xbb, 0xbf])),
    'MySQL defaults excludes BOM',
  )
  let text
  try {
    text = new TextDecoder('utf-8', { fatal: true }).decode(bytes)
  } catch {
    throw new Error('Safe check failed: MySQL defaults is UTF-8')
  }
  const parsed = parseDefaultsText(text)
  safeCheck(parsed.user === process.env.TENSOR_DB_USERNAME, 'application and CLI users match')
  safeCheck(parsed.password === process.env.TENSOR_DB_PASSWORD, 'application and CLI passwords match')
  validateJdbc(process.env.TENSOR_DB_URL ?? '', schema, parsed)
  return { defaultsPath, schema, ...parsed }
}

function spawnText(command, args, step) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      shell: false,
      env: { PATH: process.env.PATH, LANG: 'C', LC_ALL: 'C' },
      stdio: ['ignore', 'pipe', 'pipe'],
    })
    const stdout = []
    const stderr = []
    const timer = setTimeout(() => child.kill('SIGTERM'), MYSQL_TIMEOUT_MS)
    child.stdout.on('data', (chunk) => stdout.push(chunk))
    child.stderr.on('data', (chunk) => stderr.push(chunk))
    child.once('error', (error) => {
      clearTimeout(timer)
      reject(new Error(`Tool check could not start: ${step} (${error.code ?? 'error'})`))
    })
    child.once('close', (code) => {
      clearTimeout(timer)
      if (code !== 0) {
        reject(new Error(`Tool check failed: ${step} (exit=${code ?? 'none'})`))
        return
      }
      resolve(`${Buffer.concat(stdout)}${Buffer.concat(stderr)}`.trim())
    })
  })
}

function mysqlBatch(sql, step) {
  return new Promise((resolve, reject) => {
    const child = spawn(
      'mysql',
      [
        `--defaults-file=${mysqlConfig.defaultsPath}`,
        '--no-login-paths',
        `--host=${mysqlConfig.host}`,
        `--port=${mysqlConfig.port}`,
        '--protocol=TCP',
        '--batch',
        '--skip-column-names',
        '--raw',
        `--database=${mysqlConfig.schema}`,
      ],
      {
        shell: false,
        env: { PATH: process.env.PATH, LANG: 'C', LC_ALL: 'C' },
        stdio: ['pipe', 'pipe', 'pipe'],
      },
    )
    const stdout = []
    const stderr = []
    let settled = false
    const timer = setTimeout(() => {
      if (settled) return
      settled = true
      child.kill('SIGTERM')
      reject(new Error(`MySQL step timed out: ${step}`))
    }, MYSQL_TIMEOUT_MS)
    child.stdout.on('data', (chunk) => stdout.push(chunk))
    child.stderr.on('data', (chunk) => stderr.push(chunk))
    child.once('error', (error) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      reject(new Error(`MySQL step could not start: ${step} (${error.code ?? 'error'})`))
    })
    child.once('close', (code, signal) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      if (code !== 0) {
        reject(new Error(`MySQL step failed: ${step} (exit=${code ?? 'none'}, signal=${signal ?? 'none'})`))
        return
      }
      void stderr
      resolve(Buffer.concat(stdout).toString('utf8').replace(/\r?\n$/, ''))
    })
    child.stdin.end(sql)
  })
}

async function verifyInitialDatabase() {
  const schema = mysqlConfig.schema
  const output = await mysqlBatch(
    `SELECT CONCAT('server\\t', VERSION());\nSELECT CONCAT('schema\\t', DEFAULT_CHARACTER_SET_NAME, '\\t', DEFAULT_COLLATION_NAME) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '${schema}';\nSELECT CONCAT('tables\\t', COUNT(*)) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '${schema}';\n`,
    'initial read-only evidence',
  )
  const lines = output.split(/\r?\n/)
  expect(lines[0]).toMatch(/^server\t8\.4\./)
  expect(lines[1]).toBe('schema\tutf8mb4\tutf8mb4_0900_as_cs')
  expect(lines[2]).toBe('tables\t0')
  evidence.database.initial = { server: lines[0].slice(7), schema: 'empty' }
}

async function verifyMigratedDatabase() {
  const schema = mysqlConfig.schema
  const output = await mysqlBatch(
    `SELECT CONCAT('migrations\\t', GROUP_CONCAT(CONCAT(version, ':', success) ORDER BY installed_rank SEPARATOR ',')) FROM \`${schema}\`.flyway_schema_history;\nSELECT CONCAT('tables\\t', COUNT(*)) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '${schema}' AND TABLE_NAME <> 'flyway_schema_history';\nSELECT CONCAT('rows\\t', (SELECT COUNT(*) FROM \`${schema}\`.\`tushare_pro__daily\`), '\\t', (SELECT COUNT(*) FROM \`${schema}\`.\`tushare_pro__stock_company\`), '\\t', (SELECT COUNT(*) FROM \`${schema}\`.\`tushare_pro__index_classify\`), '\\t', (SELECT COUNT(*) FROM \`${schema}\`.\`tushare_pro__balancesheet\`), '\\t', (SELECT COUNT(*) FROM \`${schema}\`.\`tushare_pro__disclosure_date\`));\n`,
    'migrated read-only evidence',
  )
  expect(output.split(/\r?\n/)).toEqual([
    'migrations\t1:1,2:1,3:1,4:1,5:1,6:1,7:1,8:1',
    'tables\t52',
    'rows\t0\t0\t0\t0\t0',
  ])
  evidence.database.migrated = { migrations: 8, businessTables: 52, targetRows: [0, 0, 0, 0, 0] }
}

async function verifyFinalDatabase() {
  const schema = mysqlConfig.schema
  const output = await mysqlBatch(
    `SELECT CONCAT('migrations\\t', GROUP_CONCAT(CONCAT(version, ':', success) ORDER BY installed_rank SEPARATOR ',')) FROM \`${schema}\`.flyway_schema_history;\nSELECT CONCAT('tables\\t', COUNT(*)) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '${schema}' AND TABLE_NAME <> 'flyway_schema_history';\nSELECT CONCAT('rows\\t', (SELECT COUNT(*) FROM \`${schema}\`.\`tushare_pro__daily\`), '\\t', (SELECT COUNT(*) FROM \`${schema}\`.\`tushare_pro__stock_company\`), '\\t', (SELECT COUNT(*) FROM \`${schema}\`.\`tushare_pro__index_classify\`), '\\t', (SELECT COUNT(*) FROM \`${schema}\`.\`tushare_pro__balancesheet\`), '\\t', (SELECT COUNT(*) FROM \`${schema}\`.\`tushare_pro__disclosure_date\`), '\\t', (SELECT COUNT(*) FROM \`${schema}\`.\`tushare_pro__disclosure_date\` WHERE ann_date = '2026-08-07'), '\\t', (SELECT COUNT(*) FROM \`${schema}\`.\`tushare_pro__disclosure_date\` WHERE ann_date = '2026-08-08'));\n`,
    'final read-only evidence',
  )
  expect(output.split(/\r?\n/)).toEqual([
    'migrations\t1:1,2:1,3:1,4:1,5:1,6:1,7:1,8:1',
    'tables\t52',
    'rows\t126\t1\t1\t1\t123\t1\t122',
  ])
  evidence.database.final = {
    migrations: 8,
    businessTables: 52,
    daily: 126,
    company: 1,
    index: 1,
    balance: 1,
    disclosure: 123,
    disclosureAnnDates: { '2026-08-07': 1, '2026-08-08': 122 },
  }
}

function project(fields, values) {
  return fields.map((field) => values[field] ?? null)
}

function dailyItems(date, count) {
  return Array.from({ length: count }, (_, index) => {
    const ordinal = String(index + 1).padStart(6, '0')
    return project(fieldsByApi.daily, {
      ts_code: `${ordinal}.SZ`,
      trade_date: date,
      open: '11.23',
      high: '11.23',
      low: '11.23',
      close: '11.23',
      pre_close: '11.23',
      change: '0',
      pct_chg: '0',
      vol: '0',
      amount: '0',
    })
  })
}

function disclosureItems(corrected) {
  return Array.from({ length: 123 }, (_, index) => {
    const ordinal = index + 1
    return project(fieldsByApi.disclosure_date, {
      ts_code: `${900000 + ordinal}.SZ`,
      ann_date: corrected && ordinal > 1 ? '20260808' : '20260807',
      end_date: '20260630',
      pre_date: '20260820',
      actual_date: null,
    })
  })
}

function filterStockItems(fields, items, tsCode) {
  const stockIndex = fields.indexOf('ts_code')
  safeCheck(stockIndex >= 0, 'stock response has ts_code field')
  return items.filter((item) => item[stockIndex] === tsCode)
}

function modeDefinition(mode, tsCode) {
  const modes = {
    'daily-main': {
      api: 'daily',
      params: { trade_date: '20260807' },
      items: () => dailyItems('20260807', 123),
      stockScoped: true,
    },
    'daily-earlier': {
      api: 'daily',
      params: { trade_date: '20260806' },
      items: () => dailyItems('20260806', 3),
      stockScoped: true,
    },
    company: {
      api: 'stock_company',
      params: { exchange: 'SZSE' },
      items: () => [project(fieldsByApi.stock_company, {
        ts_code: '000001.SZ',
        com_name: 'M14 查询公司',
        exchange: 'SZSE',
        employees: '0',
        reg_capital: '9007199254740993.123456789012345678',
        introduction: LONG_TEXT,
        business_scope: '',
        main_business: null,
      })],
      stockScoped: true,
    },
    index: {
      api: 'index_classify',
      params: {},
      items: () => [project(fieldsByApi.index_classify, {
        index_code: '801001.SI',
        industry_name: 'M14 行业',
        level: 'L1',
        industry_code: 'M14',
        parent_code: '0',
        src: 'SW2021',
        is_pub: null,
      })],
    },
    balance: {
      api: 'balancesheet',
      params: { ann_date: '20260807' },
      items: () => [project(fieldsByApi.balancesheet, {
        ts_code: '000001.SZ',
        ann_date: '20260807',
        end_date: '20260630',
        report_type: '1',
        total_share: '9007199254740993.123456789012345678',
        cap_rese: '0',
      })],
      stockScoped: true,
    },
    'disclosure-initial': {
      api: 'disclosure_date',
      params: { ann_date: '20260807' },
      items: () => disclosureItems(false),
      stockScoped: true,
    },
    'disclosure-corrected': {
      api: 'disclosure_date',
      params: { ann_date: '20260807' },
      items: () => disclosureItems(true),
      stockScoped: true,
    },
  }
  const definition = modes[mode]
  if (!definition?.stockScoped || tsCode === undefined) return definition
  const items = definition.items
  return {
    ...definition,
    params: { ts_code: tsCode, ...definition.params },
    items: () => filterStockItems(fieldsByApi[definition.api], items(), tsCode),
  }
}

const STOCK_MODES = [
  'daily-main', 'daily-earlier', 'company', 'balance',
  'disclosure-initial', 'disclosure-corrected',
]
for (const mode of STOCK_MODES) {
  const definition = modeDefinition(mode, '000001.SZ')
  safeCheck(Object.keys(definition.params)[0] === 'ts_code', `${mode} requires stock input first`)
}
safeCheck(
  JSON.stringify(filterStockItems(['ts_code'], [['000001.SZ'], ['000002.SZ']], '000002.SZ')) ===
    JSON.stringify([['000002.SZ']]),
  'stock response filtering is exact',
)

function createUpstream(token) {
  const sockets = new Set()
  const failures = []
  const counts = new Map()
  let mode = 'unset'
  let stockCode
  let received
  let resolveReceived
  const server = createServer((request, response) => {
    const currentMode = mode
    const currentStockCode = stockCode
    const requestKey = `${currentMode}:${currentStockCode ?? ''}`
    counts.set(requestKey, (counts.get(requestKey) ?? 0) + 1)
    resolveReceived?.()
    let size = 0
    const chunks = []
    request.on('data', (chunk) => {
      size += chunk.length
      if (size > 64 * 1024) request.destroy()
      else chunks.push(chunk)
    })
    request.on('end', () => {
      const definition = modeDefinition(currentMode, currentStockCode)
      const checks = {
        mode: Boolean(definition),
        count: counts.get(requestKey) === 1,
        method: request.method === 'POST',
        path: request.url === '/',
        size: size <= 64 * 1024,
      }
      let body
      try {
        body = JSON.parse(Buffer.concat(chunks).toString('utf8'))
      } catch {
        checks.json = false
      }
      const objectBody =
        body !== null &&
        typeof body === 'object' &&
        !Array.isArray(body) &&
        Object.getPrototypeOf(body) === Object.prototype
      checks.keys = objectBody &&
        JSON.stringify(Object.keys(body).sort()) ===
          JSON.stringify(['api_name', 'fields', 'params', 'token'])
      checks.api = objectBody && Boolean(definition) && body.api_name === definition.api
      checks.token = objectBody && body.token === token
      checks.params =
        objectBody &&
        Boolean(definition) &&
        body.params !== null &&
        typeof body.params === 'object' &&
        !Array.isArray(body.params) &&
        JSON.stringify(Object.keys(body.params).sort()) ===
          JSON.stringify(Object.keys(definition.params).sort()) &&
        Object.entries(definition.params).every(([key, value]) => body.params[key] === value)
      checks.fields = objectBody &&
        Boolean(definition) &&
        body.fields === fieldsByApi[definition.api].join(',')
      for (const [name, passed] of Object.entries(checks)) {
        if (!passed) failures.push(`${requestKey}:${name}`)
      }
      const valid = Boolean(definition) && Object.values(checks).every(Boolean)
      response.writeHead(valid ? 200 : 500, { 'Content-Type': 'application/json' })
      response.end(JSON.stringify(valid ? {
        code: 0,
        msg: null,
        data: { fields: fieldsByApi[definition.api], items: definition.items() },
      } : { code: -1, msg: 'unknown test mode', data: null }))
    })
  })
  server.on('connection', (socket) => {
    sockets.add(socket)
    socket.once('close', () => sockets.delete(socket))
  })
  return {
    server,
    sockets,
    failures,
    counts,
    setMode(next, nextStockCode) {
      const template = modeDefinition(next)
      safeCheck(Boolean(template), 'upstream mode is declared')
      safeCheck(!template.stockScoped || typeof nextStockCode === 'string', 'stock-scoped mode has stock input')
      mode = next
      stockCode = nextStockCode
      received = new Promise((resolve) => { resolveReceived = resolve })
      return received
    },
    assertMode(next, expectedStockCode) {
      expect(counts.get(`${next}:${expectedStockCode ?? ''}`) ?? 0).toBe(1)
      expect(failures).toEqual([])
    },
  }
}

async function startUpstream() {
  upstreamToken = `m14-fake-${randomBytes(24).toString('hex')}`
  const current = createUpstream(upstreamToken)
  await new Promise((resolve, reject) => {
    current.server.once('error', reject)
    current.server.listen(0, '127.0.0.1', resolve)
  })
  const address = current.server.address()
  safeCheck(typeof address === 'object' && address !== null, 'upstream address is assigned')
  current.url = `http://127.0.0.1:${address.port}/`
  return current
}

async function stopUpstream() {
  if (!upstream) return
  const current = upstream
  for (const socket of current.sockets) socket.destroy()
  await new Promise((resolve, reject) => {
    current.server.close((error) => (error ? reject(error) : resolve()))
  })
  upstream = undefined
}

function canConnectToPort() {
  return new Promise((resolve) => {
    const socket = createConnection({ host: '127.0.0.1', port: 8080 })
    socket.setTimeout(2_000)
    socket.once('connect', () => {
      socket.destroy()
      resolve(true)
    })
    socket.once('timeout', () => {
      socket.destroy()
      resolve(false)
    })
    socket.once('error', () => resolve(false))
  })
}

async function waitForHealth(current) {
  const deadline = Date.now() + HEALTH_TIMEOUT_MS
  while (Date.now() < deadline) {
    if (current.closed) throw new Error('Owned JVM exited before readiness')
    try {
      const response = await fetch(`${BASE_URL}/actuator/health`, {
        signal: AbortSignal.timeout(2_000),
      })
      const body = await readPublicJson(response, 'health response')
      if (response.status === 200 && body?.status === 'UP') return
    } catch (error) {
      if (error instanceof Error && error.message.startsWith('Safe check failed:')) throw error
    }
    await delay(250)
  }
  throw new Error('Owned JVM was not ready within 90 seconds')
}

async function startApplication() {
  const runDirectory = await mkdtemp(path.join(tmpdir(), 'tensor-m14-t03-'))
  applicationLogPath = path.join(runDirectory, 'application.log')
  const log = await open(applicationLogPath, 'wx', 0o600)
  const child = spawn(
    'java',
    [
      '-jar',
      process.env.ACCEPTANCE_JAR,
      '--spring.profiles.active=acceptance',
      '--tensor.plugins.fixture.enabled=true',
      // The owned stub makes all 375 fixture calls local and deterministic.
      '--tensor.plugins.tushare-pro.min-request-interval=0ms',
      '--server.address=127.0.0.1',
      '--server.port=8080',
    ],
    {
      cwd: runDirectory,
      env: processEnvironment(upstream.url, upstreamToken),
      shell: false,
      stdio: ['ignore', log.fd, log.fd],
    },
  )
  const current = { child, closed: false, signalled: false }
  current.closePromise = new Promise((resolve) => {
    child.once('error', (error) => {
      current.closed = true
      resolve({ error })
    })
    child.once('close', (code, signal) => {
      current.closed = true
      resolve({ code, signal })
    })
  })
  await log.close()
  application = current
  await waitForHealth(current)
}

async function stopApplication() {
  if (!application) return
  const current = application
  if (!current.closed && !current.signalled) {
    current.signalled = true
    if (!current.child.kill('SIGTERM')) throw new Error('Could not signal owned JVM')
  }
  if (!current.closed) {
    const timeout = Symbol('stop-timeout')
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
    if (result === timeout) throw new Error('Owned JVM did not exit within 150 seconds')
  }
  application = undefined
  await expect.poll(canConnectToPort, { timeout: 2_000 }).toBe(false)
}

async function loadTemplateFields() {
  const entries = await Promise.all(Object.entries(DATASETS).map(async ([api, contract]) => {
    const file = new URL(`../../docs/data-template/${api}.json`, import.meta.url)
    const parsed = JSON.parse(await readFile(file, 'utf8'))
    expect(parsed.api_name).toBe(api)
    expect(parsed.fields).toHaveLength(contract.fields)
    expect(new Set(parsed.fields).size).toBe(contract.fields)
    return [api, parsed.fields]
  }))
  return Object.fromEntries(entries)
}

function allowedApiGet(url) {
  const path = url.pathname
  if (path === '/api/v1/data-sources') return url.search === ''
  if (path === '/api/v1/data-sources/fixture/datasets') return url.search === ''
  if (path === '/api/v1/download-tasks') {
    return url.searchParams.toString() === 'page=1&pageSize=20'
  }
  if (/^\/api\/v1\/download-tasks\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(path)) return url.search === ''
  if (/^\/api\/v1\/download-tasks\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\/batches$/.test(path)) {
    return url.searchParams.toString() === 'page=1&pageSize=20&includeSplit=false'
  }
  const match = path.match(/^\/api\/v1\/data-sources\/tushare_pro\/(apis|datasets)(?:\/([a-z0-9_]+)(\/download-capabilities|\/records)?)?$/)
  if (!match) return false
  const [, kind, apiName, suffix] = match
  if (!apiName) return url.search === ''
  if (!Object.hasOwn(DATASETS, apiName)) return false
  if (kind === 'apis') return suffix === '/download-capabilities' && url.search === ''
  return suffix === '/records' || (!suffix && url.search === '')
}

function monitorPage(page) {
  const failures = []
  const writes = []
  const responseScans = []
  const pendingApiRequests = new Map()
  let recordsRequests = 0
  let allowedFailure
  page.on('pageerror', () => failures.push('page error'))
  page.on('request', (request) => {
    const url = new URL(request.url())
    if (url.origin !== BASE_URL) failures.push('external request')
    if (url.pathname.startsWith('/api/v1/')) {
      let complete
      const done = new Promise((resolve) => { complete = resolve })
      pendingApiRequests.set(request, { complete, done })
    }
    if (request.method() === 'GET' && url.pathname.startsWith('/api/v1/') && !allowedApiGet(url)) {
      failures.push('unexpected API route')
    }
    if (request.method() === 'GET' && url.pathname.endsWith('/records')) {
      recordsRequests += 1
    }
    if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method())) {
      writes.push(`${request.method()} ${url.pathname}`)
      if (request.method() === 'POST' && url.pathname === '/api/v1/download-tasks') {
        downloadPostCount += 1
      } else {
        failures.push('unexpected write')
      }
    }
  })
  const finishApiRequest = (request) => {
    pendingApiRequests.get(request)?.complete()
    pendingApiRequests.delete(request)
  }
  page.on('requestfinished', finishApiRequest)
  page.on('requestfailed', (request) => {
    if (allowedFailure?.(request)) allowedFailure = undefined
    else failures.push('failed request')
    finishApiRequest(request)
  })
  page.on('response', (response) => {
    const url = new URL(response.url())
    if (url.pathname.startsWith('/api/v1/')) {
      const method = response.request().method()
      const allowedRoute = method === 'GET' && allowedApiGet(url)
        ? `${method} ${url.pathname.replace(/[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}/g, ':taskId')}`
        : method === 'POST' && url.pathname === '/api/v1/download-tasks'
          ? 'POST /api/v1/download-tasks'
          : undefined
      const scan = {
        route: allowedRoute,
        settled: false,
        bodyReadFailed: false,
      }
      scan.promise = response.text().then(
          (text) => {
            try { assertPublicSurface(text, 'API response') }
            catch (error) { failures.push(error.message) }
          },
          (error) => {
            scan.bodyReadFailed = true
            const reason = /No resource with given identifier found/.test(error.message)
              ? 'resource unavailable' : /closed|disposed/i.test(error.message) ? 'page closed' : 'read failed'
            const route = allowedApiGet(url) ? url.pathname.replace(/[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}/g, ':taskId') : 'unexpected route'
            failures.push(`API response body ${reason}: ${route}`)
          },
        ).finally(() => { scan.settled = true })
      responseScans.push(scan)
    }
    if (response.status() >= 400) failures.push('unexpected HTTP error')
  })
  const reportResponseScans = (phase) => {
    const pending = responseScans.filter(({ settled }) => !settled)
    const bodyReadFailed = responseScans.filter((scan) => scan.bodyReadFailed).length
    const routes = Object.fromEntries([...new Set(
      pending.map(({ route }) => route).filter(Boolean),
    )].sort().map((route) => [route, pending.filter((scan) => scan.route === route).length]))
    console.info(`M14-T03 response scans ${JSON.stringify({
      phase,
      total: responseScans.length,
      settled: responseScans.length - pending.length,
      pending: pending.length,
      pendingRequests: pendingApiRequests.size,
      bodyReadFailed,
      failureCount: failures.length,
      routes,
    })}`)
  }
  const drainResponseScans = async (phase) => {
    let stableScanCount = -1
    while (true) {
      const scans = responseScans.filter(({ settled }) => !settled)
      const requests = [...pendingApiRequests.values()]
      if (scans.length === 0 && requests.length === 0) {
        if (stableScanCount === responseScans.length) break
        stableScanCount = responseScans.length
        await delay(0)
        continue
      }
      stableScanCount = -1
      await Promise.all([
        ...scans.map(({ promise }) => promise),
        ...requests.map(({ done }) => done),
      ])
    }
    reportResponseScans(phase)
  }
  return {
    recordsRequests: () => recordsRequests,
    reportResponseScans,
    drainResponseScans,
    allowOneRecordsFailure(predicate) {
      safeCheck(allowedFailure === undefined, 'only one request failure exemption is registered')
      safeCheck(typeof predicate === 'function', 'request failure exemption is explicit')
      allowedFailure = predicate
    },
    async assertClean(expectedWrites = []) {
      reportResponseScans('assert-clean:before-drain')
      await drainResponseScans('assert-clean:after-drain')
      await assertPageSafety(page, 'test boundary')
      expect(writes).toEqual(expectedWrites)
      expect(allowedFailure).toBeUndefined()
      expect(failures).toEqual([])
    },
  }
}

async function openRoute(page, route, heading) {
  const response = await page.goto(route)
  expect(response?.status()).toBe(200)
  await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible()
  await assertPageSafety(page, 'route')
}

async function selectFrom(combobox, optionName) {
  await combobox.focus()
  await combobox.press('Enter')
  const option = combobox.page().getByRole('option', { name: optionName, exact: typeof optionName === 'string' })
  await expect(option).toBeVisible()
  await option.click()
}

async function selectOption(page, label, optionName) {
  await selectFrom(page.getByRole('combobox', { name: label, exact: true }), optionName)
}

async function assertSelectedOption(combobox, optionName) {
  await combobox.focus()
  await combobox.press('Enter')
  await expect(combobox.page().getByRole('option', {
    name: optionName,
    exact: typeof optionName === 'string',
    selected: true,
  })).toBeVisible()
  await combobox.press('Escape')
}

async function assertNoSelectedOption(combobox) {
  await combobox.focus()
  await combobox.press('Enter')
  await expect(combobox.page().getByRole('option', { selected: true })).toHaveCount(0)
  await combobox.press('Escape')
}

async function chooseTushareDownload(page, api) {
  await selectOption(page, '数据源', 'Tushare Pro')
  await selectOption(page, '数据接口', DATASETS[api].option)
}

function definitionResponse(response, api) {
  const url = new URL(response.url())
  return response.request().method() === 'GET' &&
    url.pathname === `/api/v1/data-sources/tushare_pro/datasets/${api}`
}

async function chooseDataset(page, api, { source = true } = {}) {
  if (source) await selectOption(page, '数据源', 'Tushare Pro')
  const responsePromise = page.waitForResponse((response) => definitionResponse(response, api))
  await selectOption(page, '数据集', DATASETS[api].option)
  const response = await responsePromise
  expect(response.status()).toBe(200)
  const definition = await readPublicJson(response, 'dataset definition')
  exactKeys(definition, DEFINITION_KEYS, 'dataset definition')
  expect(definition).toMatchObject({ pluginId: 'tushare_pro', apiName: api })
  expect(definition.columns.map(({ name }) => name)).toEqual(fieldsByApi[api])
  expect(definition.fixedColumn).toBe(fieldsByApi[api].includes('ts_code') ? 'ts_code' : fieldsByApi[api][0])
  return definition
}

function recordsPath(api) {
  return `/api/v1/data-sources/tushare_pro/datasets/${api}/records`
}

function isRecordsResponse(response, api) {
  const url = new URL(response.url())
  return response.request().method() === 'GET' && url.pathname === recordsPath(api)
}

function rememberRequest(response, body) {
  safeCheck(typeof body.requestId === 'string' && body.requestId.length > 0, 'request ID is present')
  safeCheck(response.headers()['x-request-id'] === body.requestId, 'request ID header matches body')
  safeCheck(!requestIds.has(body.requestId), 'request ID is unique')
  requestIds.add(body.requestId)
  return body.requestId
}

function filterNames(params) {
  const names = []
  if (params.tsCode) names.push('ts_code')
  if (params.tradeDateFrom || params.tradeDateTo) names.push('trade_date')
  if (params.annDateFrom || params.annDateTo) names.push('ann_date')
  return `[${names.join(', ')}]`
}

async function captureQueryResponse(response, api, expectedRequest, { record = true } = {}) {
  expect(response.status()).toBe(200)
  const url = new URL(response.url())
  expect(url.pathname).toBe(recordsPath(api))
  expect(Object.fromEntries(url.searchParams)).toEqual(expectedRequest)
  const body = await readPublicJson(response, 'records response')
  exactKeys(body, PAGE_KEYS, 'records response')
  expect(body.pluginId).toBe('tushare_pro')
  expect(body.apiName).toBe(api)
  expect(body.columns).toEqual([...fieldsByApi[api], ...SOURCE_COLUMNS])
  expect(body.items.every((item) => Object.keys(item).length === body.columns.length)).toBe(true)
  expect(body.items.every((item) =>
    JSON.stringify(Object.keys(item).sort()) === JSON.stringify([...body.columns].sort()),
  )).toBe(true)
  const requestId = rememberRequest(response, body)
  recordsResponseCount += 1
  const expectedEvent = {
    requestId,
    operation: 'query',
    pluginId: 'tushare_pro',
    apiName: api,
    filterNames: filterNames(expectedRequest),
    page: String(body.page),
    pageSize: String(body.pageSize),
    resultCount: String(body.items.length),
    totalElements: String(body.totalElements),
    outcome: 'success',
    failureStage: 'none',
    errorCode: 'none',
  }
  expectedEvents.push(expectedEvent)
  if (record) evidence.requests.push({
    api,
    request: expectedRequest,
    response: {
      requestId,
      page: body.page,
      pageSize: body.pageSize,
      totalElements: body.totalElements,
      totalPages: body.totalPages,
      itemCount: body.items.length,
    },
  })
  return body
}

async function queryByButton(page, api, expectedRequest) {
  const responsePromise = page.waitForResponse((response) => isRecordsResponse(response, api))
  await page.getByRole('button', { name: '查询', exact: true }).click()
  return captureQueryResponse(await responsePromise, api, expectedRequest)
}

async function queryByAction(page, api, expectedRequest, action) {
  const responsePromise = page.waitForResponse((response) => isRecordsResponse(response, api))
  await action()
  return captureQueryResponse(await responsePromise, api, expectedRequest)
}

function displayTimestamp(value) {
  const parsed = new Date(value)
  expect(Number.isNaN(parsed.valueOf())).toBe(false)
  expect(value).toMatch(/(?:Z|[+-]\d{2}:\d{2})$/i)
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(parsed).reduce((result, part) => ({ ...result, [part.type]: part.value }), {})
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`
}

function dailyExpectedRows() {
  const result = []
  for (let ordinal = 1; ordinal <= 123; ordinal += 1) {
    const code = `${String(ordinal).padStart(6, '0')}.SZ`
    if (ordinal <= 3) result.push(dailyExpected(code, '2026-08-06'))
    result.push(dailyExpected(code, '2026-08-07'))
  }
  return result
}

function dailyExpected(tsCode, tradeDate) {
  const decimal = '11.230000000000000000'
  const zero = '0.000000000000000000'
  return {
    ts_code: tsCode,
    trade_date: tradeDate,
    open: decimal,
    high: decimal,
    low: decimal,
    close: decimal,
    pre_close: decimal,
    change: zero,
    pct_chg: zero,
    vol: zero,
    amount: zero,
  }
}

function disclosureExpected(ordinal, annDate = ordinal === 1 ? '2026-08-07' : '2026-08-08') {
  return {
    ts_code: `${900000 + ordinal}.SZ`,
    ann_date: annDate,
    end_date: '2026-06-30',
    pre_date: '2026-08-20',
    actual_date: null,
  }
}

function assertBusinessRows(body, expectedRows) {
  expect(body.items).toHaveLength(expectedRows.length)
  body.items.forEach((row, index) => {
    const business = Object.fromEntries(fieldsByApi[body.apiName].map((name) => [name, row[name]]))
    expect(business).toEqual(expectedRows[index])
    expect(row.source_plugin).toBe('tushare_pro')
    expect(row.source_api).toBe(body.apiName)
    displayTimestamp(row.ingested_at)
    const key = `${body.apiName}:${fieldsByApi[body.apiName].map((name) => row[name] ?? '<null>').join('|')}`
    if (ingestionBaselines.has(key)) expect(row.ingested_at).toBe(ingestionBaselines.get(key))
    else ingestionBaselines.set(key, row.ingested_at)
  })
}

function displayValue(name, value) {
  if (value === null || value === undefined) return '--'
  if (name === 'ingested_at') return displayTimestamp(value)
  return String(value)
}

async function assertTable(page, definition, body) {
  const headers = body.apiName === 'daily' ? DAILY_HEADERS
    : [...definition.columns.map(({ label }) => label), ...SOURCE_COLUMNS]
  await expect(page.getByRole('columnheader')).toHaveText(headers)
  await expect(page.getByRole('row')).toHaveCount(body.items.length + 1)
  const rows = page.getByRole('row').filter({ has: page.getByRole('cell') })
  await expect(rows).toHaveCount(body.items.length)
  for (let index = 0; index < body.items.length; index += 1) {
    const values = body.columns.map((name) => displayValue(name, body.items[index][name]))
    await expect(rows.nth(index).getByRole('cell')).toHaveText(values)
  }
}

async function assertSummary(page, total, current, pages) {
  const summary = page.getByRole('status').filter({ hasText: `共 ${total} 条` })
  await expect(summary).toHaveText(`共 ${total} 条，第 ${current} / ${pages} 页`)
  await expect(summary).toHaveAttribute('aria-live', 'polite')
  await expect(summary).toHaveAttribute('aria-atomic', 'true')
}

function pagination(page) {
  return page.getByRole('navigation', { name: '数据集分页', exact: true })
}

async function selectPageSize(page, size) {
  await selectFrom(pagination(page).getByRole('combobox'), `${size}/page`)
}

async function doubleAnimationFrame(page) {
  await page.evaluate(() => new Promise((resolve) => {
    requestAnimationFrame(() => requestAnimationFrame(resolve))
  }))
}

async function geometryWithinViewport(page, locator, name) {
  await expect(locator).toBeVisible()
  await expect(locator).toBeInViewport()
  const [geometry, viewport] = await Promise.all([
    locator.evaluate((element) => new Promise((resolve) => {
      const observer = new IntersectionObserver(([entry]) => {
        observer.disconnect()
        const box = entry.boundingClientRect
        const intersection = entry.intersectionRect
        resolve({
          box: { x: box.x, y: box.y, width: box.width, height: box.height },
          intersection: {
            x: intersection.x,
            y: intersection.y,
            width: intersection.width,
            height: intersection.height,
          },
        })
      })
      observer.observe(element)
    })),
    page.viewportSize(),
  ])
  safeCheck(Boolean(geometry && viewport), `${name} viewport geometry exists`)
  const { box, intersection } = geometry
  safeCheck(
    box.x >= -1 && box.y >= -1 &&
      box.x + box.width <= viewport.width + 1 &&
      box.y + box.height <= viewport.height + 1,
    `${name} is within one CSS pixel of the viewport`,
  )
  safeCheck(
    box.width - intersection.width <= 1 &&
      box.height - intersection.height <= 1,
    `${name} is visible within one CSS pixel`,
  )
  return geometry
}

async function recordScreenshot(locator, testInfo, name) {
  await assertPageSafety(locator.page(), 'screenshot page')
  const text = await locator.innerText()
  assertPublicSurface(text, 'screenshot text')
  const screenshotPath = testInfo.outputPath(name)
  await locator.screenshot({ path: screenshotPath })
  const sha256 = createHash('sha256').update(await readFile(screenshotPath)).digest('hex')
  evidence.screenshots.push({ name, path: screenshotPath, sha256, manuallyReviewed: false })
}

async function hoverTextTooltip(
  page,
  cell,
  text,
  name,
  { cellTrigger = false, layout = 'clipped' } = {},
) {
  await page.mouse.move(8, 8)
  await cell.scrollIntoViewIfNeeded()
  await doubleAnimationFrame(page)
  await expect(cell).toBeVisible()
  const target = cell.getByText(text, { exact: true })
  await expect(target).toBeVisible()
  const geometry = await target.evaluate((element) => {
    const box = element.getBoundingClientRect()
    const range = document.createRange()
    range.selectNodeContents(element)
    const textWidth = range.getBoundingClientRect().width
    const lineCount = range.getClientRects().length
    let current = element
    let clippedByAncestor = false
    while (current) {
      const style = getComputedStyle(current)
      if (
        ['auto', 'clip', 'hidden', 'scroll'].includes(style.overflowX) &&
        textWidth > current.clientWidth + 1
      ) {
        clippedByAncestor = true
        break
      }
      current = current.parentElement
    }
    return {
      x: box.x,
      y: box.y,
      width: box.width,
      height: box.height,
      clientWidth: element.clientWidth,
      scrollWidth: element.scrollWidth,
      textWidth,
      lineCount,
      clippedByAncestor,
      overflow: getComputedStyle(element).overflow,
      textOverflow: getComputedStyle(element).textOverflow,
    }
  })
  safeCheck(
    geometry.x >= 0 && geometry.y >= 0 &&
      geometry.x + geometry.width <= 1440 && geometry.y + geometry.height <= 1000,
    `${name} hover target is inside viewport`,
  )
  if (layout === 'clipped') {
    safeCheck(
      geometry.scrollWidth > geometry.clientWidth ||
        geometry.textWidth > geometry.width || geometry.clippedByAncestor,
      `${name} text is clipped`,
    )
  } else {
    safeCheck(layout === 'wrapped', `${name} layout contract is known`)
    safeCheck(geometry.lineCount > 1, `${name} text wraps across lines`)
  }
  evidence.geometry.push({ name, ...geometry })
  await (cellTrigger ? cell : target).hover()
}

async function horizontalScrollState(cell) {
  return cell.evaluate((element) => {
    let current = element.parentElement
    while (current) {
      if (current.scrollWidth > current.clientWidth + 1) {
        return {
          clientWidth: current.clientWidth,
          scrollWidth: current.scrollWidth,
          scrollLeft: current.scrollLeft,
        }
      }
      current = current.parentElement
    }
    return null
  })
}

async function assertReadOnly(page) {
  for (const name of [/新增/, /编辑/, /删除/, /导出/, /排序/, /行选择/, /列配置/]) {
    await expect(page.getByRole('button', { name })).toHaveCount(0)
    await expect(page.getByRole('link', { name })).toHaveCount(0)
  }
  await expect(page.getByRole('checkbox')).toHaveCount(0)
}

function downloadRequest(response) {
  const url = new URL(response.url())
  return response.request().method() === 'POST' && url.pathname === '/api/v1/download-tasks'
}

async function readTaskApi(page, pathname) {
  const result = await page.evaluate(async (path) => {
    const requestId = crypto.randomUUID()
    const response = await fetch(path, { headers: { 'X-Request-Id': requestId } })
    return {
      status: response.status,
      requestId,
      responseRequestId: response.headers.get('X-Request-Id'),
      body: await response.json(),
    }
  }, pathname)
  expect(result.status).toBe(200)
  expect(result.responseRequestId).toBe(result.requestId)
  return result.body
}

async function waitForTask(page, taskId) {
  let task
  await expect.poll(async () => {
    task = await readTaskApi(page, `/api/v1/download-tasks/${taskId}`)
    return task.status
  }, { timeout: 15_000 }).toBe('SUCCEEDED')
  const batches = await readTaskApi(
    page,
    `/api/v1/download-tasks/${taskId}/batches?page=1&pageSize=20&includeSplit=false`,
  )
  expect(batches).toMatchObject({ page: 1, pageSize: 20, total: 1 })
  expect(batches.items).toHaveLength(1)
  return { task, batch: batches.items[0] }
}

async function fillDownloadParameters(page, api, params) {
  if (params.trade_date) {
    const input = page.getByLabel('交易日期', { exact: false })
    await input.fill(`${params.trade_date.slice(0, 4)}-${params.trade_date.slice(4, 6)}-${params.trade_date.slice(6)}`)
    await input.press('Tab')
  }
  if (params.ann_date) {
    const input = page.getByLabel('公告日期', { exact: false })
    await input.fill(`${params.ann_date.slice(0, 4)}-${params.ann_date.slice(4, 6)}-${params.ann_date.slice(6)}`)
    await input.press('Tab')
  }
  if (params.ts_code) {
    await page.getByLabel('股票代码', { exact: false }).fill(params.ts_code)
  }
  if (params.exchange) {
    await selectOption(page, '交易所', params.exchange)
  }
  void api
}

async function performDownload(page, monitor, mode, expectedCounts, tsCode, reuseForm = false) {
  const definition = modeDefinition(mode, tsCode)
  if (!reuseForm) {
    monitor.reportResponseScans(`${mode}:before-goto`)
    await monitor.drainResponseScans(`${mode}:before-goto:drained`)
    await openRoute(page, '/downloads', '数据下载')
    monitor.reportResponseScans(`${mode}:after-goto`)
    await chooseTushareDownload(page, definition.api)
  }
  await fillDownloadParameters(page, definition.api, definition.params)
  const received = upstream.setMode(mode, tsCode)
  const responsePromise = page.waitForResponse(downloadRequest)
  await page.getByRole('button', { name: '提交任务', exact: true }).click()
  const response = await responsePromise
  await received
  expect(response.status()).toBe(202)
  const request = await response.request().postDataJSON()
  expect(request).toEqual({
    submissionId: expect.stringMatching(UUID),
    pluginId: 'tushare_pro',
    apiName: definition.api,
    mode: 'SINGLE',
    params: definition.params,
  })
  const receipt = await readPublicJson(response, 'download task receipt')
  exactKeys(receipt, ['requestId', 'taskId', 'status', 'version', 'createdAt'], 'download task receipt')
  const requestId = rememberRequest(response, receipt)
  expect(receipt).toMatchObject({
    requestId,
    taskId: expect.stringMatching(UUID),
    status: 'QUEUED',
    version: 1,
  })
  expect(response.headers().location).toBe(`/api/v1/download-tasks/${receipt.taskId}`)
  await expect(page.locator('.download-result-panel').getByRole('heading', { name: '任务已接收' })).toBeVisible()
  const { task, batch } = await waitForTask(page, receipt.taskId)
  expect(task).toMatchObject({
    taskId: receipt.taskId,
    submissionId: request.submissionId,
    pluginId: 'tushare_pro',
    apiName: definition.api,
    mode: 'SINGLE',
    params: definition.params,
    status: 'SUCCEEDED',
    planReady: true,
    counts: {
      totalBatches: 1,
      pendingBatches: 0,
      runningBatches: 0,
      succeededBatches: 1,
      failedBatches: 0,
      splitBatches: 0,
      sourceRows: expectedCounts[0],
      insertedRows: expectedCounts[1],
      updatedRows: expectedCounts[2],
    },
    lastError: null,
    requestCount: 1,
    runRequestCount: 1,
  })
  expect(batch).toMatchObject({
    status: 'SUCCEEDED',
    attemptCount: 1,
    sourceRows: expectedCounts[0],
    insertedRows: expectedCounts[1],
    updatedRows: expectedCounts[2],
    error: null,
  })
  upstream.assertMode(mode, tsCode)
  expectedTasks.push({
    requestId, taskId: receipt.taskId,
    pluginId: 'tushare_pro',
    apiName: definition.api,
    status: 'SUCCEEDED',
    sourceRows: String(expectedCounts[0]),
    insertedRows: String(expectedCounts[1]),
    updatedRows: String(expectedCounts[2]),
  })
  evidence.requests.push({ api: definition.api, operation: 'download', mode, tsCode, counts: expectedCounts, requestId })
  return {
    sourceRowCount: task.counts.sourceRows,
    insertedRows: task.counts.insertedRows,
    updatedRows: task.counts.updatedRows,
  }
}

function modeStockCodes(mode) {
  const definition = modeDefinition(mode)
  if (!definition.stockScoped) return [undefined]
  const fields = fieldsByApi[definition.api]
  const stockIndex = fields.indexOf('ts_code')
  safeCheck(stockIndex >= 0, `${mode} stock field`)
  const items = definition.items()
  const stockCodes = [...new Set(items.map((item) => item[stockIndex]))]
  safeCheck(
    stockCodes.length === items.length && stockCodes.every((value) => typeof value === 'string'),
    `${mode} unique stock rows`,
  )
  return stockCodes
}

async function performModeDownloads(page, monitor, mode, expectedTotals) {
  const stockCodes = modeStockCodes(mode)
  const totals = [0, 0, 0]
  for (let index = 0; index < stockCodes.length; index += 1) {
    const expectedCounts = mode === 'disclosure-corrected' ? [1, 0, 1] : [1, 1, 0]
    const body = await performDownload(page, monitor, mode, expectedCounts, stockCodes[index], index > 0)
    totals[0] += body.sourceRowCount
    totals[1] += body.insertedRows
    totals[2] += body.updatedRows
  }
  expect(totals).toEqual(expectedTotals)
  return stockCodes.length
}

function parseLogEvent(line, marker) {
  const start = line.indexOf(marker)
  safeCheck(start >= 0, 'completion event marker is present')
  const text = line.slice(start + marker.length).trim()
  const fields = {}
  const pattern = /([A-Za-z]+)=(\[[^\]]*\]|\S+)/g
  let cursor = 0
  for (const match of text.matchAll(pattern)) {
    safeCheck(text.slice(cursor, match.index).trim() === '', 'completion event tokens are parsed')
    safeCheck(!Object.hasOwn(fields, match[1]), 'completion event fields are unique')
    fields[match[1]] = match[2]
    cursor = match.index + match[0].length
  }
  safeCheck(text.slice(cursor).trim() === '', 'completion event has no trailing token')
  return fields
}

async function verifyEventsAndLog() {
  const log = await readFile(applicationLogPath, 'utf8')
  assertPrivateLog(log)
  const lines = log.split(/\r?\n/).filter((line) => line.includes('tensor.operation.completed'))
  safeCheck(lines.length === expectedEvents.length, 'private log completion event count matches')
  const byRequest = new Map()
  for (const line of lines) {
    const parsed = parseLogEvent(line, 'tensor.operation.completed')
    safeCheck(!byRequest.has(parsed.requestId), 'completion request ID is unique')
    byRequest.set(parsed.requestId, parsed)
  }
  for (const expected of expectedEvents) {
    const actual = byRequest.get(expected.requestId)
    safeCheck(Boolean(actual), 'expected completion event exists')
    const keys = expected.operation === 'download'
      ? ['requestId', 'operation', 'pluginId', 'apiName', 'paramSummary', 'sourceRowCount', 'insertedRows', 'updatedRows', 'durationMs', 'outcome', 'failureStage', 'errorCode']
      : ['requestId', 'operation', 'pluginId', 'apiName', 'filterNames', 'page', 'pageSize', 'resultCount', 'totalElements', 'durationMs', 'outcome', 'failureStage', 'errorCode']
    safeCheck(JSON.stringify(Object.keys(actual).sort()) === JSON.stringify(keys.sort()), 'completion event fields are exact')
    for (const [name, value] of Object.entries(expected)) {
      safeCheck(actual[name] === String(value), `completion event ${name} matches`)
    }
    safeCheck(/^\d+$/.test(actual.durationMs), 'completion event duration is nonnegative')
    assertPublicSurface(lines.find((line) => line.includes(`requestId=${expected.requestId}`)), 'completion event')
    evidence.events.push(Object.fromEntries(keys.map((key) => [key, actual[key]])))
  }
  safeCheck(
    [...byRequest.values()].every(({ operation }) => operation === 'query'),
    'task submission does not emit synchronous download completion events',
  )

  const accepted = log.split(/\r?\n/)
    .filter((line) => line.includes('tensor.download_task.accepted'))
    .map((line) => parseLogEvent(line, 'tensor.download_task.accepted'))
  const finished = log.split(/\r?\n/)
    .filter((line) => line.includes('tensor.download_task.finished'))
    .map((line) => parseLogEvent(line, 'tensor.download_task.finished'))
  const batches = log.split(/\r?\n/)
    .filter((line) => line.includes('tensor.download_batch.finished'))
    .map((line) => parseLogEvent(line, 'tensor.download_batch.finished'))
  expect(accepted).toHaveLength(expectedTasks.length)
  expect(finished).toHaveLength(expectedTasks.length)
  expect(batches).toHaveLength(expectedTasks.length)
  for (const expected of expectedTasks) {
    const acceptedEvent = accepted.find(({ taskId }) => taskId === expected.taskId)
    const finishedEvent = finished.find(({ taskId }) => taskId === expected.taskId)
    const batchEvent = batches.find(({ taskId }) => taskId === expected.taskId)
    expect(acceptedEvent).toMatchObject({
      requestId: expected.requestId,
      taskId: expected.taskId,
      pluginId: expected.pluginId,
      apiName: expected.apiName,
      status: 'QUEUED',
      version: '1',
      kind: 'CREATED',
      outcome: 'accepted',
    })
    expect(finishedEvent).toMatchObject({
      taskId: expected.taskId,
      pluginId: expected.pluginId,
      apiName: expected.apiName,
      status: expected.status,
      totalBatches: '1',
      succeededBatches: '1',
      failedBatches: '0',
      sourceRows: expected.sourceRows,
      insertedRows: expected.insertedRows,
      updatedRows: expected.updatedRows,
      errorCode: 'none',
    })
    expect(batchEvent).toMatchObject({
      taskId: expected.taskId,
      pluginId: expected.pluginId,
      apiName: expected.apiName,
      status: 'SUCCEEDED',
      attemptCount: '1',
      sourceRows: expected.sourceRows,
      insertedRows: expected.insertedRows,
      updatedRows: expected.updatedRows,
      errorCode: 'none',
    })
  }
  evidence.taskEvents = { accepted: accepted.length, batches: batches.length, finished: finished.length }
}

async function focusByTab(page, locator, { backwards = false } = {}) {
  for (let count = 0; count < 80; count += 1) {
    const focused = await locator.evaluate((element) => document.activeElement === element).catch(() => false)
    if (focused) return
    await page.keyboard.press(backwards ? 'Shift+Tab' : 'Tab')
  }
  throw new Error('Keyboard focus target was not reached within 80 Tab steps')
}

async function keyboardSelect(page, combobox, optionName, { search, direction = 'ArrowDown' } = {}) {
  await expect(combobox).toBeFocused()
  await page.keyboard.press('ArrowDown')
  await expect(combobox).toHaveAttribute('aria-expanded', 'true')
  if (search) await page.keyboard.type(search)
  const option = page.getByRole('option', { name: optionName, exact: typeof optionName === 'string' })
  await expect(option).toBeVisible()
  let active
  for (let count = 0; count < 80; count += 1) {
    const activeId = await combobox.getAttribute('aria-activedescendant')
    if (activeId) {
      const candidate = page.locator(`[id="${activeId}"]`)
      if (await candidate.count() === 1 && await candidate.getAttribute('role') === 'option') {
        const text = (await candidate.innerText()).trim()
        const matches = typeof optionName === 'string' ? text === optionName : optionName.test(text)
        if (matches) {
          active = candidate
          break
        }
      }
    }
    await page.keyboard.press(direction)
  }
  safeCheck(Boolean(active), 'keyboard navigation reaches exact active option')
  await expect(active).toHaveRole('option')
  await expect(active).toHaveText(optionName)
  await page.keyboard.press('Enter')
  await expect(combobox).toHaveAttribute('aria-expanded', 'false')
  await page.keyboard.press('Escape')
}

test.use({
  viewport: { width: 1440, height: 1000 },
  trace: 'off',
  video: 'off',
  screenshot: 'off',
})

test.describe('dataset query UX', () => {
  test.describe.configure({ mode: 'serial', retries: 0, timeout: 180_000 })

  test.beforeAll(async () => {
    configurePackagedEnvironment('dataset-query')
    test.setTimeout(300_000)
    evidence.startedAt = new Date().toISOString()
    await syntheticProbes()
    for (const name of DB_VARIABLES) safeCheck((process.env[name]?.length ?? 0) > 0, `${name} is present`)
    safeCheck(path.isAbsolute(process.env.ACCEPTANCE_JAR ?? ''), 'acceptance JAR path is absolute')
    safeCheck(/^[a-f0-9]{64}$/.test(ACCEPTANCE_JAR_SHA ?? ''), 'expected acceptance JAR hash')
    safeCheck((await stat(process.env.ACCEPTANCE_JAR)).isFile(), 'acceptance JAR is a file')
    safeCheck((process.env.PLAYWRIGHT_BASE_URL ?? BASE_URL) === BASE_URL, 'Playwright base URL is isolated')
    mysqlConfig = await parseMysqlInputs()
    fieldsByApi = await loadTemplateFields()
    const java = await spawnText('java', ['-version'], 'Java version')
    const npm = await spawnText('npm', ['--version'], 'npm version')
    const mysql = await spawnText('mysql', ['--version'], 'MySQL client version')
    safeCheck(/version "21\./.test(java), 'Java major version is 21')
    safeCheck(/^v24\./.test(process.version), 'Node major version is 24')
    safeCheck(/^\d+\.\d+\.\d+/.test(npm), 'npm version is available')
    safeCheck(/Ver 8\.4\./.test(mysql), 'MySQL client major version is 8.4')
    safeCheck(!(await canConnectToPort()), 'port 8080 is unused')
    await verifyInitialDatabase()
    evidence.environment = {
      java: java.match(/version "([^"]+)"/)?.[1],
      node: process.version,
      npm,
      mysqlClient: mysql.match(/Ver ([^ ]+)/)?.[1],
      jarSha256: createHash('sha256').update(await readFile(process.env.ACCEPTANCE_JAR)).digest('hex'),
    }
    safeCheck(
      evidence.environment.jarSha256 === ACCEPTANCE_JAR_SHA,
      'acceptance JAR hash matches approved artifact',
    )
    try {
      upstream = await startUpstream()
      await startApplication()
      await verifyMigratedDatabase()
    } catch (error) {
      const failures = [error]
      try { await stopApplication() } catch (cleanupError) { failures.push(cleanupError) }
      try { await stopUpstream() } catch (cleanupError) { failures.push(cleanupError) }
      if (failures.length === 1) throw error
      throw new AggregateError(failures, 'M14-T03 setup and cleanup failed')
    }
  })

  test.afterEach(async ({ page }, testInfo) => {
    evidence.results.push({ title: testInfo.title, status: testInfo.status })
    await assertPageSafety(page, 'case boundary')
  })

  test.afterAll(async () => {
    test.setTimeout(300_000)
    const failures = []
    try {
      await stopApplication()
      evidence.cleanup.jvmStopped = !(await canConnectToPort())
    } catch (error) { failures.push(error) }
    try { await verifyFinalDatabase() } catch (error) { failures.push(error) }
    try { await verifyEventsAndLog() } catch (error) { failures.push(error) }
    try {
      expect(downloadPostCount).toBe(TOTAL_DOWNLOAD_COUNT)
      expect([...upstream.counts.values()].reduce((sum, count) => sum + count, 0)).toBe(TOTAL_DOWNLOAD_COUNT)
      expect(upstream.failures).toEqual([])
      evidence.counters = {
        downloads: downloadPostCount,
        upstreamCalls: TOTAL_DOWNLOAD_COUNT,
        recordsResponses: recordsResponseCount,
      }
    } catch (error) { failures.push(error) }
    try {
      await stopUpstream()
      evidence.cleanup.upstreamStopped = true
    } catch (error) { failures.push(error) }
    evidence.finishedAt = new Date().toISOString()
    const evidencePath = path.join(path.dirname(applicationLogPath), 'evidence.json')
    try {
      const text = JSON.stringify(evidence, null, 2)
      assertPublicSurface(text, 'shared evidence')
      await writeFile(evidencePath, `${text}\n`, { encoding: 'utf8', flag: 'wx', mode: 0o600 })
      console.info(`M14-T03 safe evidence: ${evidencePath}`)
    } catch (error) { failures.push(error) }
    if (failures.length) throw new AggregateError(failures, 'M14-T03 cleanup failed')
  })

  test('seedsQueryDatasetsThroughDownloadPages', async ({ page }) => {
    test.setTimeout(600_000)
    const monitor = monitorPage(page)
    await openRoute(page, '/downloads', '数据下载')
    await page.getByRole('link', { name: /^数据查看\s*02$/ }).click()
    await expect(page.getByRole('heading', { level: 1, name: '数据查看' })).toBeVisible()
    await page.getByRole('link', { name: /^数据下载\s*01$/ }).click()
    await expect(page.getByRole('heading', { level: 1, name: '数据下载' })).toBeVisible()

    let seedDownloads = 0
    for (const [mode, counts] of [
      ['daily-main', [123, 123, 0]],
      ['daily-earlier', [3, 3, 0]],
      ['company', [1, 1, 0]],
      ['index', [1, 1, 0]],
      ['balance', [1, 1, 0]],
      ['disclosure-initial', [123, 123, 0]],
    ]) {
      seedDownloads += await performModeDownloads(page, monitor, mode, counts)
    }
    expect(seedDownloads).toBe(INITIAL_DOWNLOAD_COUNT)
    expect(downloadPostCount).toBe(INITIAL_DOWNLOAD_COUNT)
    expect([...upstream.counts.values()].reduce((sum, count) => sum + count, 0)).toBe(INITIAL_DOWNLOAD_COUNT)
    await monitor.assertClean(Array(INITIAL_DOWNLOAD_COUNT).fill('POST /api/v1/download-tasks'))
  })

  test('showsOnlyDeclaredFiltersWithoutAutoQuery', async ({ page }) => {
    const monitor = monitorPage(page)
    await openRoute(page, '/datasets', '数据查看')
    const cases = [
      ['daily', ['tsCode', 'tradeDateFrom', 'tradeDateTo']],
      ['stock_company', ['tsCode']],
      ['index_classify', []],
      ['balancesheet', ['tsCode', 'annDateFrom', 'annDateTo']],
      ['disclosure_date', ['tsCode', 'annDateFrom', 'annDateTo']],
    ]
    for (const [api, filters] of cases) {
      const before = monitor.recordsRequests()
      const definition = await chooseDataset(page, api)
      expect(definition.filters.map(({ field }) => field)).toEqual(
        filters.map((name) => ({
          tsCode: 'ts_code',
          tradeDateFrom: 'trade_date',
          tradeDateTo: 'trade_date',
          annDateFrom: 'ann_date',
          annDateTo: 'ann_date',
        })[name]).filter((name, index, values) => values.indexOf(name) === index),
      )
      await doubleAnimationFrame(page)
      expect(monitor.recordsRequests()).toBe(before)
      for (const [name, label] of Object.entries(FILTER_LABELS)) {
        await expect(page.getByLabel(label, { exact: true })).toHaveCount(filters.includes(name) ? 1 : 0)
      }
      await expect(page.getByRole('heading', { name: '设置筛选条件后查询' })).toBeVisible()
      await expect(page.getByRole('columnheader')).toHaveCount(0)
      await expect(pagination(page)).toHaveCount(0)
      await expect(page.getByRole('alert')).toHaveCount(0)
    }
    await selectOption(page, '数据源', 'Fixture')
    await doubleAnimationFrame(page)
    expect(monitor.recordsRequests()).toBe(0)
    for (const label of Object.values(FILTER_LABELS)) {
      await expect(page.getByLabel(label, { exact: true })).toHaveCount(0)
    }
    await expect(page.getByText(/balancesheet|disclosure_date|资产负债表|财报披露计划/)).toHaveCount(0)
    await selectOption(page, '数据源', 'Tushare Pro')
    await doubleAnimationFrame(page)
    expect(monitor.recordsRequests()).toBe(0)
    await expect(page.getByRole('heading', { name: '请选择数据集' })).toBeVisible()
    await assertNoSelectedOption(page.getByRole('combobox', { name: '数据集', exact: true }))
    await monitor.assertClean([])
  })

  test('paginatesAllRowsWithServerTotals', async ({ page }, testInfo) => {
    const monitor = monitorPage(page)
    const allRows = dailyExpectedRows()
    await openRoute(page, '/datasets', '数据查看')
    const definition = await chooseDataset(page, 'daily')

    let body = await queryByButton(page, 'daily', { page: '1', pageSize: '50' })
    expect(body).toMatchObject({ page: 1, pageSize: 50, totalElements: 126, totalPages: 3 })
    assertBusinessRows(body, allRows.slice(0, 50))
    await assertTable(page, definition, body)
    await assertSummary(page, 126, 1, 3)

    body = await queryByAction(page, 'daily', { page: '2', pageSize: '50' }, () =>
      pagination(page).getByRole('button', { name: /下一页/ }).click())
    expect(body).toMatchObject({ page: 2, pageSize: 50, totalElements: 126, totalPages: 3 })
    assertBusinessRows(body, allRows.slice(50, 100))
    await assertTable(page, definition, body)
    await assertSummary(page, 126, 2, 3)
    await recordScreenshot(page.locator('main'), testInfo, 'daily-page-2-of-3.png')

    body = await queryByAction(page, 'daily', { page: '3', pageSize: '50' }, () =>
      pagination(page).getByRole('button', { name: /下一页/ }).click())
    expect(body).toMatchObject({ page: 3, pageSize: 50, totalElements: 126, totalPages: 3 })
    assertBusinessRows(body, allRows.slice(100))
    await assertTable(page, definition, body)
    await assertSummary(page, 126, 3, 3)
    await recordScreenshot(page.locator('main'), testInfo, 'daily-last-page-50.png')

    body = await queryByAction(page, 'daily', { page: '1', pageSize: '20' }, () => selectPageSize(page, 20))
    expect(body).toMatchObject({ page: 1, pageSize: 20, totalElements: 126, totalPages: 7 })
    assertBusinessRows(body, allRows.slice(0, 20))
    await assertTable(page, definition, body)
    await assertSummary(page, 126, 1, 7)
    body = await queryByAction(page, 'daily', { page: '2', pageSize: '20' }, () =>
      pagination(page).getByRole('button', { name: /下一页/ }).click())
    expect(body).toMatchObject({ page: 2, pageSize: 20, totalElements: 126, totalPages: 7 })
    assertBusinessRows(body, allRows.slice(20, 40))
    await assertTable(page, definition, body)
    await assertSummary(page, 126, 2, 7)

    body = await queryByAction(page, 'daily', { page: '1', pageSize: '100' }, () => selectPageSize(page, 100))
    expect(body).toMatchObject({ page: 1, pageSize: 100, totalElements: 126, totalPages: 2 })
    assertBusinessRows(body, allRows.slice(0, 100))
    await assertTable(page, definition, body)
    await assertSummary(page, 126, 1, 2)
    body = await queryByAction(page, 'daily', { page: '2', pageSize: '100' }, () =>
      pagination(page).getByRole('button', { name: /下一页/ }).click())
    expect(body).toMatchObject({ page: 2, pageSize: 100, totalElements: 126, totalPages: 2 })
    assertBusinessRows(body, allRows.slice(100))
    await assertTable(page, definition, body)
    await assertSummary(page, 126, 2, 2)
    await monitor.assertClean([])
  })

  test('combinesTradeDateFiltersAndKeepsEmptyPaging', async ({ page }) => {
    const monitor = monitorPage(page)
    await openRoute(page, '/datasets', '数据查看')
    const definition = await chooseDataset(page, 'daily')
    const code = page.getByLabel(FILTER_LABELS.tsCode, { exact: true })
    const from = page.getByLabel(FILTER_LABELS.tradeDateFrom, { exact: true })
    const to = page.getByLabel(FILTER_LABELS.tradeDateTo, { exact: true })
    await code.fill(' 000001.sz ')
    await from.fill('2026-08-07')
    await from.press('Tab')
    await to.fill('2026-08-07')
    await to.press('Tab')
    let body = await queryByButton(page, 'daily', {
      tsCode: '000001.SZ', tradeDateFrom: '2026-08-07', tradeDateTo: '2026-08-07', page: '1', pageSize: '50',
    })
    expect(body).toMatchObject({ totalElements: 1, totalPages: 1 })
    assertBusinessRows(body, [dailyExpected('000001.SZ', '2026-08-07')])
    await assertTable(page, definition, body)
    await assertSummary(page, 1, 1, 1)

    await code.fill('')
    await to.fill('')
    await to.press('Tab')
    body = await queryByButton(page, 'daily', { tradeDateFrom: '2026-08-07', page: '1', pageSize: '50' })
    expect(body).toMatchObject({ totalElements: 123, totalPages: 3 })
    assertBusinessRows(body, dailyExpectedRows().filter(({ trade_date }) => trade_date === '2026-08-07').slice(0, 50))
    await assertTable(page, definition, body)
    await assertSummary(page, 123, 1, 3)

    await from.fill('')
    await from.press('Tab')
    await to.fill('2026-08-06')
    await to.press('Tab')
    body = await queryByButton(page, 'daily', { tradeDateTo: '2026-08-06', page: '1', pageSize: '50' })
    expect(body).toMatchObject({ totalElements: 3, totalPages: 1 })
    assertBusinessRows(body, [1, 2, 3].map((ordinal) => dailyExpected(`${String(ordinal).padStart(6, '0')}.SZ`, '2026-08-06')))
    await assertTable(page, definition, body)
    await assertSummary(page, 3, 1, 1)

    await code.fill('000001.SZ')
    await from.fill('2026-08-05')
    await from.press('Tab')
    await to.fill('2026-08-05')
    await to.press('Tab')
    body = await queryByButton(page, 'daily', {
      tsCode: '000001.SZ', tradeDateFrom: '2026-08-05', tradeDateTo: '2026-08-05', page: '1', pageSize: '50',
    })
    expect(body).toMatchObject({ page: 1, pageSize: 50, totalElements: 0, totalPages: 0, items: [] })
    await expect(page.getByRole('heading', { name: '未找到符合条件的数据' })).toBeVisible()
    await expect(page.getByRole('status').filter({ hasText: '未找到符合条件的数据' })).toHaveAttribute('aria-live', 'polite')
    await expect(page.getByRole('columnheader')).toHaveCount(0)
    await assertSummary(page, 0, 1, 0)
    await expect(code).toHaveValue('000001.SZ')
    await expect(from).toHaveValue('2026-08-05')
    await expect(to).toHaveValue('2026-08-05')

    body = await queryByAction(page, 'daily', {
      tsCode: '000001.SZ', tradeDateFrom: '2026-08-05', tradeDateTo: '2026-08-05', page: '1', pageSize: '20',
    }, () => selectPageSize(page, 20))
    expect(body).toMatchObject({ page: 1, pageSize: 20, totalElements: 0, totalPages: 0, items: [] })
    await assertSummary(page, 0, 1, 0)
    await monitor.assertClean([])
  })

  test('resetsSelectionStateAndRejectsInvalidRanges', async ({ page }) => {
    const monitor = monitorPage(page)
    await openRoute(page, '/datasets', '数据查看')
    const definition = await chooseDataset(page, 'daily')
    const code = page.getByLabel(FILTER_LABELS.tsCode, { exact: true })
    const from = page.getByLabel(FILTER_LABELS.tradeDateFrom, { exact: true })
    const to = page.getByLabel(FILTER_LABELS.tradeDateTo, { exact: true })
    await from.fill('2026-08-07')
    await from.press('Tab')
    await page.keyboard.press('Escape')
    let body = await queryByButton(page, 'daily', { tradeDateFrom: '2026-08-07', page: '1', pageSize: '50' })
    assertBusinessRows(body, dailyExpectedRows().filter(({ trade_date }) => trade_date === '2026-08-07').slice(0, 50))
    await assertTable(page, definition, body)
    await assertSummary(page, 123, 1, 3)
    body = await queryByAction(page, 'daily', { tradeDateFrom: '2026-08-07', page: '1', pageSize: '20' }, () => selectPageSize(page, 20))
    assertBusinessRows(body, dailyExpectedRows().filter(({ trade_date }) => trade_date === '2026-08-07').slice(0, 20))
    await assertTable(page, definition, body)
    await assertSummary(page, 123, 1, 7)
    body = await queryByAction(page, 'daily', { tradeDateFrom: '2026-08-07', page: '2', pageSize: '20' }, () =>
      pagination(page).getByRole('button', { name: /下一页/ }).click())
    assertBusinessRows(body, dailyExpectedRows().filter(({ trade_date }) => trade_date === '2026-08-07').slice(20, 40))
    await assertTable(page, definition, body)
    await assertSummary(page, 123, 2, 7)
    const beforeReset = monitor.recordsRequests()
    await page.getByRole('button', { name: '重置', exact: true }).click()
    await doubleAnimationFrame(page)
    expect(monitor.recordsRequests()).toBe(beforeReset)
    await assertSelectedOption(page.getByRole('combobox', { name: '数据源', exact: true }), 'Tushare Pro')
    await assertSelectedOption(page.getByRole('combobox', { name: '数据集', exact: true }), DATASETS.daily.option)
    await expect(from).toHaveValue('')
    await expect(page.getByRole('heading', { name: '设置筛选条件后查询' })).toBeVisible()
    await expect(page.getByRole('columnheader')).toHaveCount(0)
    await expect(pagination(page)).toHaveCount(0)

    body = await queryByButton(page, 'daily', { page: '1', pageSize: '50' })
    expect(body).toMatchObject({ page: 1, pageSize: 50, totalElements: 126, totalPages: 3 })
    assertBusinessRows(body, dailyExpectedRows().slice(0, 50))
    await assertTable(page, definition, body)
    await assertSummary(page, 126, 1, 3)

    let beforeValidation = monitor.recordsRequests()
    await code.fill('invalid-code')
    await page.getByRole('button', { name: '查询', exact: true }).click()
    await expect(page.getByText('请输入代码.市场格式，例如 000001.SZ', { exact: true })).toBeVisible()
    await expect(code).toHaveAttribute('aria-invalid', 'true')
    await expect(code).toHaveAttribute('aria-describedby', /.+/)
    await expect(code).toBeFocused()
    expect(monitor.recordsRequests()).toBe(beforeValidation)

    await code.fill('')
    await from.fill('2026-08-08')
    await from.press('Tab')
    await to.fill('2026-08-07')
    await to.press('Tab')
    beforeValidation = monitor.recordsRequests()
    await page.getByRole('button', { name: '查询', exact: true }).click()
    await expect(page.getByText('开始日期不得晚于结束日期', { exact: true })).toBeVisible()
    await expect(from).toHaveAttribute('aria-invalid', 'true')
    await expect(from).toHaveAttribute('aria-describedby', /.+/)
    await expect(from).toBeFocused()
    expect(monitor.recordsRequests()).toBe(beforeValidation)

    await chooseDataset(page, 'disclosure_date', { source: false })
    const annFrom = page.getByLabel(FILTER_LABELS.annDateFrom, { exact: true })
    const annTo = page.getByLabel(FILTER_LABELS.annDateTo, { exact: true })
    await annFrom.fill('2026-08-08')
    await annFrom.press('Tab')
    await annTo.fill('2026-08-07')
    await annTo.press('Tab')
    beforeValidation = monitor.recordsRequests()
    await page.getByRole('button', { name: '查询', exact: true }).click()
    await expect(page.getByText('开始日期不得晚于结束日期', { exact: true })).toBeVisible()
    await expect(annFrom).toHaveAttribute('aria-invalid', 'true')
    await expect(annFrom).toBeFocused()
    expect(monitor.recordsRequests()).toBe(beforeValidation)
    await page.getByRole('button', { name: '重置', exact: true }).click()
    await expect(page.getByText('开始日期不得晚于结束日期', { exact: true })).toHaveCount(0)
    await expect(annFrom).not.toHaveAttribute('aria-invalid', 'true')
    await monitor.assertClean([])
  })

  test('normalizesLastPageAfterAnnDateCorrection', async ({ page, context }, testInfo) => {
    test.setTimeout(600_000)
    const monitor = monitorPage(page)
    await openRoute(page, '/datasets', '数据查看')
    const definition = await chooseDataset(page, 'disclosure_date')
    const code = page.getByLabel(FILTER_LABELS.tsCode, { exact: true })
    const from = page.getByLabel(FILTER_LABELS.annDateFrom, { exact: true })
    const to = page.getByLabel(FILTER_LABELS.annDateTo, { exact: true })
    await from.fill('2026-08-07')
    await from.press('Tab')
    await to.fill('2026-08-07')
    await to.press('Tab')
    let body = await queryByButton(page, 'disclosure_date', {
      annDateFrom: '2026-08-07', annDateTo: '2026-08-07', page: '1', pageSize: '50',
    })
    expect(body).toMatchObject({ page: 1, pageSize: 50, totalElements: 123, totalPages: 3 })
    assertBusinessRows(body, Array.from({ length: 50 }, (_, index) => disclosureExpected(index + 1, '2026-08-07')))
    await assertTable(page, definition, body)
    await assertSummary(page, 123, 1, 3)

    const updater = await context.newPage()
    const updaterMonitor = monitorPage(updater)
    await runWithCleanup(
      async () => {
        const updateDownloads = await performModeDownloads(
          updater,
          updaterMonitor,
          'disclosure-corrected',
          [123, 0, 123],
        )
        expect(updateDownloads).toBe(123)
        await updaterMonitor.assertClean(Array(updateDownloads).fill('POST /api/v1/download-tasks'))
      },
      () => updater.close(),
      'disclosure update and page cleanup failed',
    )
    for (const key of [...ingestionBaselines.keys()]) {
      if (key.startsWith('disclosure_date:')) ingestionBaselines.delete(key)
    }
    await assertSummary(page, 123, 1, 3)

    body = await queryByAction(page, 'disclosure_date', {
      annDateFrom: '2026-08-07', annDateTo: '2026-08-07', page: '3', pageSize: '50',
    }, () => pagination(page).getByText('3', { exact: true }).click())
    expect(body).toMatchObject({ page: 1, pageSize: 50, totalElements: 1, totalPages: 1 })
    assertBusinessRows(body, [disclosureExpected(1)])
    await assertTable(page, definition, body)
    await assertSummary(page, 1, 1, 1)
    await recordScreenshot(page.locator('main'), testInfo, 'disclosure-normalized-page.png')

    body = await queryByAction(page, 'disclosure_date', {
      annDateFrom: '2026-08-07', annDateTo: '2026-08-07', page: '1', pageSize: '20',
    }, () => selectPageSize(page, 20))
    expect(body).toMatchObject({ page: 1, pageSize: 20, totalElements: 1, totalPages: 1 })
    assertBusinessRows(body, [disclosureExpected(1)])
    await assertTable(page, definition, body)
    await assertSummary(page, 1, 1, 1)

    await code.fill('900002.SZ')
    body = await queryByButton(page, 'disclosure_date', {
      tsCode: '900002.SZ', annDateFrom: '2026-08-07', annDateTo: '2026-08-07', page: '1', pageSize: '20',
    })
    expect(body).toMatchObject({ totalElements: 0, totalPages: 0, items: [] })
    await expect(page.getByRole('heading', { name: '未找到符合条件的数据' })).toBeVisible()
    await expect(page.getByRole('columnheader')).toHaveCount(0)
    await assertSummary(page, 0, 1, 0)
    await from.fill('2026-08-08')
    await from.press('Tab')
    await to.fill('2026-08-08')
    await to.press('Tab')
    body = await queryByButton(page, 'disclosure_date', {
      tsCode: '900002.SZ', annDateFrom: '2026-08-08', annDateTo: '2026-08-08', page: '1', pageSize: '20',
    })
    expect(body).toMatchObject({ totalElements: 1, totalPages: 1 })
    assertBusinessRows(body, [disclosureExpected(2)])
    await assertTable(page, definition, body)
    await assertSummary(page, 1, 1, 1)

    await code.fill('')
    await to.fill('')
    await to.press('Tab')
    body = await queryByButton(page, 'disclosure_date', {
      annDateFrom: '2026-08-08', page: '1', pageSize: '20',
    })
    expect(body).toMatchObject({ page: 1, pageSize: 20, totalElements: 122, totalPages: 7 })
    assertBusinessRows(body, Array.from({ length: 20 }, (_, index) => disclosureExpected(index + 2)))
    expect(body.items[0].ts_code).toBe('900002.SZ')
    await assertTable(page, definition, body)
    await assertSummary(page, 122, 1, 7)

    await from.fill('')
    await from.press('Tab')
    await to.fill('2026-08-07')
    await to.press('Tab')
    body = await queryByButton(page, 'disclosure_date', {
      annDateTo: '2026-08-07', page: '1', pageSize: '20',
    })
    expect(body).toMatchObject({ page: 1, pageSize: 20, totalElements: 1, totalPages: 1 })
    assertBusinessRows(body, [disclosureExpected(1)])
    await assertTable(page, definition, body)
    await assertSummary(page, 1, 1, 1)
    await monitor.assertClean([])
  })

  test('rendersWideColumnsAndExactTextValues', async ({ page }, testInfo) => {
    const monitor = monitorPage(page)
    await openRoute(page, '/datasets', '数据查看')
    let definition = await chooseDataset(page, 'balancesheet')
    let body = await queryByButton(page, 'balancesheet', { page: '1', pageSize: '50' })
    expect(definition.columns).toHaveLength(152)
    expect(body.columns).toHaveLength(155)
    const balanceExpected = Object.fromEntries(fieldsByApi.balancesheet.map((name) => [name, null]))
    Object.assign(balanceExpected, {
      ts_code: '000001.SZ',
      ann_date: '2026-08-07',
      end_date: '2026-06-30',
      report_type: '1',
      total_share: '9007199254740993.123456789012345678',
      cap_rese: '0.000000000000000000',
    })
    assertBusinessRows(body, [balanceExpected])
    await assertTable(page, definition, body)
    await assertSummary(page, 1, 1, 1)
    await expect(page.getByRole('columnheader')).toHaveCount(155)
    await expect(page.getByRole('cell')).toHaveCount(155)
    await expect(page.getByText('business_key', { exact: true })).toHaveCount(0)
    const tsLabel = definition.columns.find(({ name }) => name === 'ts_code').label
    const annLabel = definition.columns.find(({ name }) => name === 'ann_date').label
    const fixedHeader = page.getByRole('columnheader', { name: tsLabel, exact: true })
    const normalHeader = page.getByRole('columnheader', { name: annLabel, exact: true })
    const fixedCell = page.getByRole('cell', { name: '000001.SZ', exact: true })
    const initialFixedHeader = await fixedHeader.boundingBox()
    const initialFixedCell = await fixedCell.boundingBox()
    const initialNormal = await normalHeader.boundingBox()
    safeCheck(Boolean(initialFixedHeader && initialFixedCell && initialNormal), 'balance initial geometry exists')
    const overflow = await fixedCell.evaluate((element) => {
      let current = element.parentElement
      while (current) {
        if (current.scrollWidth > current.clientWidth + 1) {
          return { scrollWidth: current.scrollWidth, clientWidth: current.clientWidth }
        }
        current = current.parentElement
      }
      return null
    })
    safeCheck(Boolean(overflow && overflow.scrollWidth > overflow.clientWidth), 'balance table has horizontal overflow')
    await recordScreenshot(page.locator('main'), testInfo, 'balancesheet-left.png')
    const ingested = page.getByRole('cell', { name: displayTimestamp(body.items[0].ingested_at), exact: true })
    await ingested.scrollIntoViewIfNeeded()
    const box = await ingested.boundingBox()
    safeCheck(Boolean(box), 'balance last column geometry exists')
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
    await page.mouse.wheel(2500, 0)
    const finalFixedHeader = await fixedHeader.boundingBox()
    const finalFixedCell = await fixedCell.boundingBox()
    const finalNormal = await normalHeader.boundingBox()
    safeCheck(Boolean(finalFixedHeader && finalFixedCell && finalNormal), 'balance final geometry exists')
    expect(Math.abs(finalFixedHeader.x - initialFixedHeader.x)).toBeLessThanOrEqual(2)
    expect(Math.abs(finalFixedCell.x - initialFixedCell.x)).toBeLessThanOrEqual(2)
    expect(Math.abs(finalNormal.x - initialNormal.x)).toBeGreaterThan(2)
    await expect(ingested).toBeVisible()
    await recordScreenshot(page.locator('main'), testInfo, 'balancesheet-right.png')
    const precise = page.getByRole('cell', { name: '9007199254740993.123456789012345678', exact: true })
    await page.mouse.move(8, 8)
    await precise.scrollIntoViewIfNeeded()
    await doubleAnimationFrame(page)
    await recordScreenshot(page.locator('body'), testInfo, 'balancesheet-precision-target.png')
    await hoverTextTooltip(
      page,
      precise,
      '9007199254740993.123456789012345678',
      'balance precision',
      { cellTrigger: true },
    )
    const preciseTooltip = page.getByRole('tooltip').filter({ hasText: '9007199254740993.123456789012345678' })
    await expect(preciseTooltip).toHaveText('9007199254740993.123456789012345678')
    await expect(preciseTooltip.locator('strong, em, script, style')).toHaveCount(0)
    await recordScreenshot(page.locator('body'), testInfo, 'balancesheet-precision-tooltip.png')

    definition = await chooseDataset(page, 'stock_company', { source: false })
    body = await queryByButton(page, 'stock_company', { page: '1', pageSize: '50' })
    const companyExpected = Object.fromEntries(fieldsByApi.stock_company.map((name) => [name, null]))
    Object.assign(companyExpected, {
      ts_code: '000001.SZ',
      com_name: 'M14 查询公司',
      exchange: 'SZSE',
      employees: '0',
      reg_capital: '9007199254740993.123456789012345678',
      introduction: LONG_TEXT,
      business_scope: '',
      main_business: null,
    })
    assertBusinessRows(body, [companyExpected])
    await assertTable(page, definition, body)
    await assertSummary(page, 1, 1, 1)
    const companyCells = page.getByRole('row').filter({ has: page.getByRole('cell') }).getByRole('cell')
    const businessScopeCell = companyCells.nth(fieldsByApi.stock_company.indexOf('business_scope'))
    const mainBusinessCell = companyCells.nth(fieldsByApi.stock_company.indexOf('main_business'))
    const employeesCell = companyCells.nth(fieldsByApi.stock_company.indexOf('employees'))
    expect(await businessScopeCell.textContent()).toBe('')
    await expect(mainBusinessCell).toHaveText('--')
    await expect(employeesCell).toHaveText('0')
    await expect(companyCells.nth(fieldsByApi.stock_company.indexOf('reg_capital'))).toHaveText('9007199254740993.123456789012345678')
    const longCell = companyCells.nth(fieldsByApi.stock_company.indexOf('introduction'))
    await longCell.scrollIntoViewIfNeeded()
    await doubleAnimationFrame(page)
    await recordScreenshot(page.locator('body'), testInfo, 'company-long-text-target.png')
    await hoverTextTooltip(
      page,
      longCell,
      LONG_TEXT,
      'company long text',
      { layout: 'wrapped' },
    )
    const longTooltip = page.getByRole('tooltip').filter({ hasText: LONG_TEXT })
    await expect(longTooltip).toHaveText(LONG_TEXT)
    expect(await longTooltip.textContent()).toBe(LONG_TEXT)
    await expect(longTooltip.locator('strong, em, script, style')).toHaveCount(0)
    await recordScreenshot(page.locator('body'), testInfo, 'company-empty-null-zero-tooltip.png')
    await page.mouse.move(8, 8)
    await mainBusinessCell.scrollIntoViewIfNeeded()
    await doubleAnimationFrame(page)
    const [businessScopeBox, mainBusinessBox, employeesBox] = await Promise.all([
      geometryWithinViewport(page, businessScopeCell, 'company empty value'),
      geometryWithinViewport(page, mainBusinessCell, 'company null value'),
      geometryWithinViewport(page, employeesCell, 'company zero value'),
    ])
    evidence.geometry.push({
      name: 'company empty/null/zero viewport',
      tolerance: 1,
      businessScopeBox,
      mainBusinessBox,
      employeesBox,
    })
    await recordScreenshot(page.locator('body'), testInfo, 'company-empty-null-zero.png')

    definition = await chooseDataset(page, 'index_classify', { source: false })
    body = await queryByButton(page, 'index_classify', { page: '1', pageSize: '50' })
    const indexExpected = {
      index_code: '801001.SI', industry_name: 'M14 行业', level: 'L1', industry_code: 'M14',
      is_pub: null, parent_code: '0', src: 'SW2021',
    }
    assertBusinessRows(body, [indexExpected])
    await assertTable(page, definition, body)
    await assertSummary(page, 1, 1, 1)
    const indexLabel = definition.columns[0].label
    const industryLabel = definition.columns[1].label
    const indexHeader = page.getByRole('columnheader', { name: indexLabel, exact: true })
    const industryHeader = page.getByRole('columnheader', { name: industryLabel, exact: true })
    const indexCell = page.getByRole('cell', { name: '801001.SI', exact: true })
    const industryCell = page.getByRole('cell', { name: 'M14 行业', exact: true })
    const industryBox = await industryCell.boundingBox()
    safeCheck(Boolean(industryBox), 'index scroll target geometry exists')
    await page.mouse.move(industryBox.x + industryBox.width / 2, industryBox.y + industryBox.height / 2)
    await page.mouse.wheel(-2500, 0)
    await expect.poll(async () => (await horizontalScrollState(indexCell))?.scrollLeft).toBeLessThanOrEqual(2)
    const leftState = await horizontalScrollState(indexCell)
    safeCheck(Boolean(leftState && leftState.scrollWidth > leftState.clientWidth), 'index table has horizontal overflow')
    const indexBefore = await indexHeader.boundingBox()
    const industryBefore = await industryHeader.boundingBox()
    await recordScreenshot(page.locator('main'), testInfo, 'index-left.png')
    const indexLast = page.getByRole('cell', { name: displayTimestamp(body.items[0].ingested_at), exact: true })
    await indexLast.scrollIntoViewIfNeeded()
    const indexLastBox = await indexLast.boundingBox()
    safeCheck(Boolean(indexLastBox), 'index last column geometry exists')
    await page.mouse.move(indexLastBox.x + indexLastBox.width / 2, indexLastBox.y + indexLastBox.height / 2)
    await page.mouse.wheel(2500, 0)
    await expect.poll(async () => (await horizontalScrollState(indexCell))?.scrollLeft).toBeGreaterThan(leftState.scrollLeft + 2)
    const rightState = await horizontalScrollState(indexCell)
    const indexAfter = await indexHeader.boundingBox()
    const industryAfter = await industryHeader.boundingBox()
    safeCheck(Boolean(indexBefore && indexAfter && industryBefore && industryAfter), 'index geometry exists')
    expect(Math.abs(indexAfter.x - indexBefore.x)).toBeLessThanOrEqual(2)
    expect(Math.abs(industryAfter.x - industryBefore.x)).toBeGreaterThan(2)
    expect(await indexHeader.evaluate((element) => getComputedStyle(element).position)).toBe('sticky')
    expect(await indexCell.evaluate((element) => getComputedStyle(element).position)).toBe('sticky')
    expect(await industryHeader.evaluate((element) => getComputedStyle(element).position)).not.toBe('sticky')
    expect(await industryCell.evaluate((element) => getComputedStyle(element).position)).not.toBe('sticky')
    evidence.geometry.push({ name: 'index horizontal scroll', leftState, rightState })
    await recordScreenshot(page.locator('main'), testInfo, 'index-right.png')
    await monitor.assertClean([])
  })

  test('ignoresReleasedResponseFromPreviousDataset', async ({ page }, testInfo) => {
    const monitor = monitorPage(page)
    await openRoute(page, '/datasets', '数据查看')
    const dailyDefinition = await chooseDataset(page, 'daily')
    const first = await queryByButton(page, 'daily', { page: '1', pageSize: '50' })
    expect(first).toMatchObject({ totalElements: 126, totalPages: 3 })
    assertBusinessRows(first, dailyExpectedRows().slice(0, 50))
    await assertTable(page, dailyDefinition, first)
    await assertSummary(page, 126, 1, 3)

    let release
    let held = false
    let heldResolve
    let handlerDoneResolve
    let handlerError
    const heldPromise = new Promise((resolve) => { heldResolve = resolve })
    const handlerDonePromise = new Promise((resolve) => { handlerDoneResolve = resolve })
    const releasePromise = new Promise((resolve) => { release = resolve })
    const handler = async (route) => {
      const url = new URL(route.request().url())
      if (!held && url.searchParams.get('page') === '1' && url.searchParams.get('pageSize') === '50') {
        held = true
        evidence.raceReleaseOrder.push('dataset-switch:daily-held')
        heldResolve()
        let holdError
        try {
          await withinDeadline(releasePromise, 30_000, 'dataset race hold is released within 30 seconds')
        } catch (error) {
          holdError = error
        }
        try {
          await route.continue()
          evidence.raceReleaseOrder.push('dataset-switch:daily-continued')
        } catch (error) {
          handlerError = holdError
            ? new AggregateError([holdError, error], 'dataset hold deadline and continue failed')
            : error
        } finally {
          handlerDoneResolve()
        }
        if (holdError && !handlerError) handlerError = holdError
      } else {
        await route.continue()
      }
    }
    await page.route(`**${recordsPath('daily')}*`, handler)
    const oldResponsePromise = page.waitForResponse((response) => isRecordsResponse(response, 'daily'))
    const oldSettlement = watchRequestSettlement(page, (request) => {
      const url = new URL(request.url())
      return url.pathname === recordsPath('daily')
        && url.searchParams.get('page') === '1'
        && url.searchParams.get('pageSize') === '50'
    })
    let settledRequest
    let mainError
    let cleanupError
    try {
      await page.getByRole('button', { name: '查询', exact: true }).click()
      await withinDeadline(heldPromise, 10_000, 'dataset race request is intercepted within 10 seconds')
      await expect(page.getByRole('heading', { name: '正在查询数据' })).toBeVisible()
      await expect(page.getByRole('columnheader')).toHaveCount(0)
      await expect(pagination(page)).toHaveCount(0)
      await expect(page.getByLabel(FILTER_LABELS.tsCode, { exact: true })).toBeDisabled()
      await expect(page.getByRole('button', { name: '查询', exact: true })).toBeDisabled()
      await expect(page.getByRole('button', { name: '重置', exact: true })).toBeEnabled()

      const indexDefinition = await chooseDataset(page, 'index_classify', { source: false })
      const index = await queryByButton(page, 'index_classify', { page: '1', pageSize: '50' })
      expect(index).toMatchObject({ totalElements: 1, totalPages: 1 })
      assertBusinessRows(index, [{
        index_code: '801001.SI', industry_name: 'M14 行业', level: 'L1', industry_code: 'M14',
        is_pub: null, parent_code: '0', src: 'SW2021',
      }])
      await assertTable(page, indexDefinition, index)
      await assertSummary(page, 1, 1, 1)
      evidence.raceReleaseOrder.push('dataset-switch:index-visible')
      release()
      const old = await captureQueryResponse(await oldResponsePromise, 'daily', { page: '1', pageSize: '50' })
      settledRequest = await withinDeadline(oldSettlement.promise, 30_000, 'dataset race request settles after release')
      safeCheck(settledRequest.type === 'finished', 'dataset race request finishes successfully')
      expect(old).toMatchObject({ page: 1, pageSize: 50, totalElements: 126, totalPages: 3 })
      await doubleAnimationFrame(page)
      await expect(page.getByRole('columnheader')).toHaveText([
        ...indexDefinition.columns.map(({ label }) => label), ...SOURCE_COLUMNS,
      ])
      await expect(page.getByRole('cell', { name: '801001.SI', exact: true })).toBeVisible()
      await expect(page.getByRole('cell', { name: '000001.SZ', exact: true })).toHaveCount(0)
      await assertSummary(page, 1, 1, 1)
      evidence.raceReleaseOrder.push('dataset-switch:stale-ignored')
      await recordScreenshot(page.locator('main'), testInfo, 'stale-daily-after-index.png')
    } catch (error) { mainError = error }
    try {
      await runWithCleanup(
        async () => {
          release?.()
          if (held) {
            await runWithCleanup(
              () => withinDeadline(handlerDonePromise, 35_000, 'dataset race handler completes during cleanup'),
              async () => {
                settledRequest ??= await withinDeadline(
                  oldSettlement.promise, 35_000, 'dataset race request settles during cleanup',
                )
                safeCheck(['finished', 'failed'].includes(settledRequest.type), 'dataset race cleanup observes request settlement')
              },
              'dataset race handler and request settlement failed',
            )
          }
          if (handlerError) throw handlerError
        },
        async () => {
          oldSettlement.cancel()
          await page.unroute(`**${recordsPath('daily')}*`, handler)
        },
        'dataset race release and unroute failed',
      )
    } catch (error) { cleanupError = error }
    if (mainError && cleanupError) throw new AggregateError([mainError, cleanupError], 'dataset race and cleanup failed')
    if (mainError) throw mainError
    if (cleanupError) throw cleanupError
    await monitor.assertClean([])
  })

  test('keepsResetStateAfterPendingQueryCompletes', async ({ page }) => {
    const monitor = monitorPage(page)
    await openRoute(page, '/datasets', '数据查看')
    const definition = await chooseDataset(page, 'daily')
    const from = page.getByLabel(FILTER_LABELS.tradeDateFrom, { exact: true })
    await from.fill('2026-08-07')
    await from.press('Tab')
    await page.keyboard.press('Escape')
    let body = await queryByButton(page, 'daily', { tradeDateFrom: '2026-08-07', page: '1', pageSize: '50' })
    assertBusinessRows(body, dailyExpectedRows().filter(({ trade_date }) => trade_date === '2026-08-07').slice(0, 50))
    await assertTable(page, definition, body)
    await assertSummary(page, 123, 1, 3)
    body = await queryByAction(page, 'daily', { tradeDateFrom: '2026-08-07', page: '1', pageSize: '20' }, () => selectPageSize(page, 20))
    assertBusinessRows(body, dailyExpectedRows().filter(({ trade_date }) => trade_date === '2026-08-07').slice(0, 20))
    await assertTable(page, definition, body)
    await assertSummary(page, 123, 1, 7)

    let release
    let held = false
    let heldResolve
    let handlerDoneResolve
    let handlerError
    const heldPromise = new Promise((resolve) => { heldResolve = resolve })
    const handlerDonePromise = new Promise((resolve) => { handlerDoneResolve = resolve })
    const releasePromise = new Promise((resolve) => { release = resolve })
    const handler = async (route) => {
      const url = new URL(route.request().url())
      if (!held && url.searchParams.get('page') === '2' && url.searchParams.get('pageSize') === '20') {
        held = true
        evidence.raceReleaseOrder.push('reset:page2-held')
        heldResolve()
        let holdError
        try {
          await withinDeadline(releasePromise, 30_000, 'reset race hold is released within 30 seconds')
        } catch (error) {
          holdError = error
        }
        try {
          await route.continue()
          evidence.raceReleaseOrder.push('reset:page2-continued')
        } catch (error) {
          handlerError = holdError
            ? new AggregateError([holdError, error], 'reset hold deadline and continue failed')
            : error
        } finally {
          handlerDoneResolve()
        }
        if (holdError && !handlerError) handlerError = holdError
      } else await route.continue()
    }
    await page.route(`**${recordsPath('daily')}*`, handler)
    const oldResponsePromise = page.waitForResponse((response) => isRecordsResponse(response, 'daily'))
    const oldSettlement = watchRequestSettlement(page, (request) => {
      const url = new URL(request.url())
      return url.pathname === recordsPath('daily')
        && url.searchParams.get('page') === '2'
        && url.searchParams.get('pageSize') === '20'
    })
    let settledRequest
    let mainError
    let cleanupError
    try {
      await pagination(page).getByRole('button', { name: /下一页/ }).click()
      await withinDeadline(heldPromise, 10_000, 'reset race request is intercepted within 10 seconds')
      await expect(page.getByRole('heading', { name: '正在查询数据' })).toBeVisible()
      await page.getByRole('button', { name: '重置', exact: true }).click()
      evidence.raceReleaseOrder.push('reset:state-visible')
      await expect(from).toHaveValue('')
      await expect(page.getByRole('heading', { name: '设置筛选条件后查询' })).toBeVisible()
      await expect(page.getByRole('columnheader')).toHaveCount(0)
      await expect(pagination(page)).toHaveCount(0)
      await assertSelectedOption(page.getByRole('combobox', { name: '数据源', exact: true }), 'Tushare Pro')
      await assertSelectedOption(page.getByRole('combobox', { name: '数据集', exact: true }), DATASETS.daily.option)
      release()
      const old = await captureQueryResponse(await oldResponsePromise, 'daily', {
        tradeDateFrom: '2026-08-07', page: '2', pageSize: '20',
      })
      settledRequest = await withinDeadline(oldSettlement.promise, 30_000, 'reset race request settles after release')
      safeCheck(settledRequest.type === 'finished', 'reset race request finishes successfully')
      expect(old).toMatchObject({ page: 2, pageSize: 20, totalElements: 123, totalPages: 7 })
      await doubleAnimationFrame(page)
      await expect(page.getByRole('heading', { name: '设置筛选条件后查询' })).toBeVisible()
      await expect(page.getByRole('columnheader')).toHaveCount(0)
      evidence.raceReleaseOrder.push('reset:stale-ignored')
      const fresh = await queryByButton(page, 'daily', { page: '1', pageSize: '50' })
      expect(fresh).toMatchObject({ page: 1, pageSize: 50, totalElements: 126, totalPages: 3 })
      assertBusinessRows(fresh, dailyExpectedRows().slice(0, 50))
      await assertTable(page, definition, fresh)
      await assertSummary(page, 126, 1, 3)
    } catch (error) { mainError = error }
    try {
      await runWithCleanup(
        async () => {
          release?.()
          if (held) {
            await runWithCleanup(
              () => withinDeadline(handlerDonePromise, 35_000, 'reset race handler completes during cleanup'),
              async () => {
                settledRequest ??= await withinDeadline(
                  oldSettlement.promise, 35_000, 'reset race request settles during cleanup',
                )
                safeCheck(['finished', 'failed'].includes(settledRequest.type), 'reset race cleanup observes request settlement')
              },
              'reset race handler and request settlement failed',
            )
          }
          if (handlerError) throw handlerError
        },
        async () => {
          oldSettlement.cancel()
          await page.unroute(`**${recordsPath('daily')}*`, handler)
        },
        'reset race release and unroute failed',
      )
    } catch (error) { cleanupError = error }
    if (mainError && cleanupError) throw new AggregateError([mainError, cleanupError], 'reset race and cleanup failed')
    if (mainError) throw mainError
    if (cleanupError) throw cleanupError
    await monitor.assertClean([])
  })

  test('recoversFromQueryNetworkFailureWithoutOldRows', async ({ page }) => {
    const monitor = monitorPage(page)
    await openRoute(page, '/datasets', '数据查看')
    const definition = await chooseDataset(page, 'daily')
    const from = page.getByLabel(FILTER_LABELS.tradeDateFrom, { exact: true })
    await from.fill('2026-08-07')
    await from.press('Tab')
    await page.keyboard.press('Escape')
    const initial = await queryByButton(page, 'daily', { tradeDateFrom: '2026-08-07', page: '1', pageSize: '50' })
    assertBusinessRows(initial, dailyExpectedRows().filter(({ trade_date }) => trade_date === '2026-08-07').slice(0, 50))
    await assertTable(page, definition, initial)
    await assertSummary(page, 123, 1, 3)

    let aborted = false
    const handler = async (route) => {
      const url = new URL(route.request().url())
      if (!aborted && url.searchParams.get('page') === '2' && url.searchParams.get('pageSize') === '50') {
        aborted = true
        await route.abort('failed')
      } else await route.continue()
    }
    await page.route(`**${recordsPath('daily')}*`, handler)
    const expectedAbortParams = { tradeDateFrom: '2026-08-07', page: '2', pageSize: '50' }
    const isExpectedAbort = (request) => {
      const url = new URL(request.url())
      return request.method() === 'GET' &&
        url.origin === BASE_URL &&
        url.pathname === recordsPath('daily') &&
        [...url.searchParams].length === Object.keys(expectedAbortParams).length &&
        Object.entries(expectedAbortParams).every(([name, value]) => url.searchParams.get(name) === value)
    }
    monitor.allowOneRecordsFailure(isExpectedAbort)
    const failedPromise = page.waitForEvent('requestfailed', {
      predicate: isExpectedAbort,
    })
    let mainError
    let cleanupError
    try {
      await pagination(page).getByRole('button', { name: /下一页/ }).click()
      const failedRequest = await failedPromise
      const failedUrl = new URL(failedRequest.url())
      expect(Object.fromEntries(failedUrl.searchParams)).toEqual({
        tradeDateFrom: '2026-08-07', page: '2', pageSize: '50',
      })
      const abortedRequestId = failedRequest.headers()['x-request-id']
      safeCheck(typeof abortedRequestId === 'string' && abortedRequestId.length > 0, 'aborted query has outbound request ID')
      safeCheck(!requestIds.has(abortedRequestId), 'aborted query ID has no response identity')
      await expect(page.getByRole('alert')).toContainText('无法连接服务，请检查网络后重试。')
      await expect(page.getByRole('alert').getByRole('heading', { name: '查询失败' })).toBeVisible()
      await expect(page.getByRole('button', { name: '重新查询', exact: true })).toBeVisible()
      await expect(page.getByRole('alert')).toContainText(`请求 ID：${abortedRequestId}`)
      evidence.requests.push({
        api: 'daily',
        operation: 'query',
        request: { tradeDateFrom: '2026-08-07', page: '2', pageSize: '50' },
        outcome: 'network-aborted',
        requestId: abortedRequestId,
        response: null,
        completionEvent: false,
      })
      await expect(page.getByRole('columnheader')).toHaveCount(0)
      await expect(pagination(page)).toHaveCount(0)
      await page.unroute(`**${recordsPath('daily')}*`, handler)
      const recovered = await queryByAction(page, 'daily', {
        tradeDateFrom: '2026-08-07', page: '2', pageSize: '50',
      }, () => page.getByRole('button', { name: '重新查询', exact: true }).click())
      expect(recovered).toMatchObject({ page: 2, pageSize: 50, totalElements: 123, totalPages: 3 })
      assertBusinessRows(recovered, dailyExpectedRows().filter(({ trade_date }) => trade_date === '2026-08-07').slice(50, 100))
      await assertTable(page, definition, recovered)
      await assertSummary(page, 123, 2, 3)
    } catch (error) { mainError = error }
    try { await page.unroute(`**${recordsPath('daily')}*`, handler) } catch (error) { cleanupError = error }
    if (mainError && cleanupError) throw new AggregateError([mainError, cleanupError], 'network failure and cleanup failed')
    if (mainError) throw mainError
    if (cleanupError) throw cleanupError
    expect(aborted).toBe(true)
    await monitor.assertClean([])
  })

  test('queriesAndPaginatesUsingKeyboard', async ({ page }, testInfo) => {
    const monitor = monitorPage(page)
    await openRoute(page, '/downloads', '数据下载')
    const datasetsLink = page.getByRole('link', { name: /^数据查看\s*02$/ })
    await focusByTab(page, datasetsLink)
    await expect(datasetsLink).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(page.getByRole('heading', { level: 1, name: '数据查看' })).toBeVisible()

    const source = page.getByRole('combobox', { name: '数据源', exact: true })
    await focusByTab(page, source)
    await keyboardSelect(page, source, 'Tushare Pro')
    const dataset = page.getByRole('combobox', { name: '数据集', exact: true })
    await focusByTab(page, dataset)
    const definitionPromise = page.waitForResponse((response) => definitionResponse(response, 'daily'))
    await keyboardSelect(page, dataset, DATASETS.daily.option, { search: 'daily' })
    const definition = await readPublicJson(await definitionPromise, 'keyboard dataset definition')
    exactKeys(definition, DEFINITION_KEYS, 'keyboard dataset definition')
    expect(definition.columns.map(({ name }) => name)).toEqual(fieldsByApi.daily)

    const code = page.getByLabel(FILTER_LABELS.tsCode, { exact: true })
    const from = page.getByLabel(FILTER_LABELS.tradeDateFrom, { exact: true })
    const to = page.getByLabel(FILTER_LABELS.tradeDateTo, { exact: true })
    const query = page.getByRole('button', { name: '查询', exact: true })
    for (const [locator, label] of [
      [code, FILTER_LABELS.tsCode], [from, FILTER_LABELS.tradeDateFrom], [to, FILTER_LABELS.tradeDateTo],
    ]) {
      const id = await locator.getAttribute('id')
      safeCheck(Boolean(id), 'keyboard field has an ID')
      await expect(page.locator(`label[for="${id}"]`)).toBeVisible()
      await expect(page.locator(`label[for="${id}"]`)).toHaveText(label)
    }

    await focusByTab(page, code)
    await page.keyboard.type('invalid-code')
    await focusByTab(page, query)
    const beforeInvalid = monitor.recordsRequests()
    await page.keyboard.press('Enter')
    await expect(page.getByText('请输入代码.市场格式，例如 000001.SZ', { exact: true })).toBeVisible()
    await expect(code).toHaveAttribute('aria-invalid', 'true')
    const errorId = await code.getAttribute('aria-describedby')
    safeCheck(Boolean(errorId), 'keyboard error is described')
    await expect(page.locator(`[id="${errorId}"]`)).toHaveText('请输入代码.市场格式，例如 000001.SZ')
    await expect(code).toBeFocused()
    expect(monitor.recordsRequests()).toBe(beforeInvalid)
    await recordScreenshot(page.locator('main'), testInfo, 'keyboard-invalid-focus.png')

    await page.keyboard.press(process.platform === 'darwin' ? 'Meta+A' : 'Control+A')
    await page.keyboard.type('000001.SZ')
    await page.keyboard.press('Tab')
    await expect(from).toBeFocused()
    await page.keyboard.type('2026-08-07')
    await page.keyboard.press('Tab')
    await expect(to).toBeFocused()
    await page.keyboard.type('2026-08-07')
    await focusByTab(page, query)
    let responsePromise = page.waitForResponse((response) => isRecordsResponse(response, 'daily'))
    await page.keyboard.press('Enter')
    let body = await captureQueryResponse(await responsePromise, 'daily', {
      tsCode: '000001.SZ', tradeDateFrom: '2026-08-07', tradeDateTo: '2026-08-07', page: '1', pageSize: '50',
    })
    expect(body).toMatchObject({ totalElements: 1, totalPages: 1 })
    assertBusinessRows(body, [dailyExpected('000001.SZ', '2026-08-07')])
    await assertTable(page, definition, body)
    await assertSummary(page, 1, 1, 1)

    await focusByTab(page, code, { backwards: true })
    await page.keyboard.press(process.platform === 'darwin' ? 'Meta+A' : 'Control+A')
    await page.keyboard.press('Backspace')
    await focusByTab(page, query)
    responsePromise = page.waitForResponse((response) => isRecordsResponse(response, 'daily'))
    await page.keyboard.press('Enter')
    body = await captureQueryResponse(await responsePromise, 'daily', {
      tradeDateFrom: '2026-08-07', tradeDateTo: '2026-08-07', page: '1', pageSize: '50',
    })
    expect(body).toMatchObject({ page: 1, pageSize: 50, totalElements: 123, totalPages: 3 })
    assertBusinessRows(body, dailyExpectedRows().filter(({ trade_date }) => trade_date === '2026-08-07').slice(0, 50))
    await assertTable(page, definition, body)
    await assertSummary(page, 123, 1, 3)

    const size = pagination(page).getByRole('combobox')
    await focusByTab(page, size)
    responsePromise = page.waitForResponse((response) => isRecordsResponse(response, 'daily'))
    await keyboardSelect(page, size, '20/page', { direction: 'ArrowUp' })
    body = await captureQueryResponse(await responsePromise, 'daily', {
      tradeDateFrom: '2026-08-07', tradeDateTo: '2026-08-07', page: '1', pageSize: '20',
    })
    expect(body).toMatchObject({ page: 1, pageSize: 20, totalElements: 123, totalPages: 7 })
    assertBusinessRows(body, dailyExpectedRows().filter(({ trade_date }) => trade_date === '2026-08-07').slice(0, 20))
    await assertTable(page, definition, body)
    await assertSummary(page, 123, 1, 7)

    const next = pagination(page).getByRole('button', { name: /下一页/ })
    await focusByTab(page, next)
    await expect(next).toBeFocused()
    responsePromise = page.waitForResponse((response) => isRecordsResponse(response, 'daily'))
    await page.keyboard.press('Enter')
    body = await captureQueryResponse(await responsePromise, 'daily', {
      tradeDateFrom: '2026-08-07', tradeDateTo: '2026-08-07', page: '2', pageSize: '20',
    })
    expect(body).toMatchObject({ page: 2, pageSize: 20, totalElements: 123, totalPages: 7 })
    assertBusinessRows(body, dailyExpectedRows().filter(({ trade_date }) => trade_date === '2026-08-07').slice(20, 40))
    await assertTable(page, definition, body)
    await assertSummary(page, 123, 2, 7)
    const nextAfterRender = pagination(page).getByRole('button', { name: /下一页/ })
    await focusByTab(page, nextAfterRender)
    await expect(nextAfterRender).toBeFocused()
    evidence.keyboardFocus = { nextBeforeEnter: true, nextAfterRenderReachedByTab: true }
    await recordScreenshot(page.locator('main'), testInfo, 'keyboard-page-2-focus.png')
    await assertReadOnly(page)
    await monitor.assertClean([])
  })
})
