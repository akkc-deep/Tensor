import { expect, test } from '@playwright/test'
import { spawn } from 'node:child_process'
import { createHash, randomBytes } from 'node:crypto'
import { open, lstat, mkdtemp, readFile, stat, writeFile } from 'node:fs/promises'
import { createServer } from 'node:http'
import { createConnection } from 'node:net'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { setTimeout as delay } from 'node:timers/promises'

const BASE_URL = 'http://127.0.0.1:8080'
const HEALTH_TIMEOUT_MS = 90_000
const STOP_TIMEOUT_MS = 150_000
const MYSQL_TIMEOUT_MS = 15_000
const TRIGGER = 'm14_t02_fixture_update_fail'
const UPSTREAM_CANARY = 'M14_T02_UPSTREAM_RAW_CANARY'
const SQL_CANARY = 'M14_T02_FAULT_SQL_CANARY'
const FIXTURE_API = /Fixture 日线.*fixture_daily/
const DAILY_OPTION = /^日线行情daily$/
const NEW_SHARE_API = /IPO 新股发行信息.*new_share$/
const DAILY_FIELDS = [
  'ts_code',
  'trade_date',
  'open',
  'high',
  'low',
  'close',
  'pre_close',
  'change',
  'pct_chg',
  'vol',
  'amount',
]
const DB_VARIABLES = [
  'TENSOR_DB_URL',
  'TENSOR_DB_USERNAME',
  'TENSOR_DB_PASSWORD',
]

let application
let applicationLogPath
let mysqlConfig
let stub
let upstreamToken
let fixtureBaseline
let dailyBaseline
let triggerOwned = false
let downloadPostCount = 0
let queryCount = 0
const requestIds = new Set()
const expectedEvents = []
const evidence = {
  startedAt: undefined,
  finishedAt: undefined,
  jarSha256: undefined,
  results: [],
  events: [],
  screenshots: [],
  counters: undefined,
  cleanup: {},
  fixtureUpsert: undefined,
  dailyRow: undefined,
}

function processEnvironment(stubUrl, token) {
  const env = Object.fromEntries(
    Object.entries(process.env).filter(
      ([name]) => !/^(TENSOR_|SPRING_|SERVER_|M14_|MYSQL_)/.test(name),
    ),
  )
  for (const name of DB_VARIABLES) env[name] = process.env[name]
  env.TENSOR_TUSHARE_TOKEN = token
  env.TENSOR_TUSHARE_BASE_URL = stubUrl
  return env
}

function safeCheck(passed, name) {
  if (!passed) throw new Error(`Safe check failed: ${name}`)
}

function assertExactKeys(value, expected, name) {
  safeCheck(
    value !== null && typeof value === 'object' && !Array.isArray(value),
    `${name} is an object`,
  )
  safeCheck(
    JSON.stringify(Object.keys(value).sort()) === JSON.stringify([...expected].sort()),
    `${name} fields are exact`,
  )
}

function forbiddenPublicValues() {
  return [
    upstreamToken,
    process.env.TENSOR_DB_PASSWORD,
    mysqlConfig?.cliPassword,
    process.env.TENSOR_DB_USERNAME,
    process.env.TENSOR_DB_URL,
    mysqlConfig?.host,
    mysqlConfig?.schema,
  ].filter((value) => typeof value === 'string' && value.length > 0)
}

function assertPublicSurface(text, name) {
  safeCheck(typeof text === 'string', `${name} is text`)
  safeCheck(
    !/M14_T02_(?:UPSTREAM_RAW|FAULT_SQL)_CANARY|jdbc:mysql|CREATE\s+TRIGGER|SIGNAL\s+SQLSTATE/i.test(
      text,
    ),
    `${name} excludes private markers`,
  )
  safeCheck(
    forbiddenPublicValues().every((value) => !text.includes(value)),
    `${name} excludes connection values`,
  )
}

function assertPrivateLogSafety(text) {
  safeCheck(
    [
      upstreamToken,
      process.env.TENSOR_DB_PASSWORD,
      mysqlConfig?.cliPassword,
    ]
      .filter((value) => typeof value === 'string' && value.length > 0)
      .every((value) => !text.includes(value)),
    'private log excludes credentials',
  )
  safeCheck(
    !/M14_T02_(?:UPSTREAM_RAW|FAULT_SQL)_CANARY|CREATE\s+TRIGGER|SIGNAL\s+SQLSTATE/i.test(
      text,
    ),
    'private log excludes raw payload and SQL',
  )
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
    throw new Error(`Safe check failed: ${name} is JSON`)
  }
}

async function assertPageSafety(page, name) {
  let text
  try {
    text = await page.locator('body').innerText()
  } catch {
    throw new Error(`Safe check failed: ${name} visible text readable`)
  }
  assertPublicSurface(text, `${name} visible text`)
}

async function runWithCleanup(action, cleanup, message) {
  let primaryError
  let cleanupError
  try {
    await action()
  } catch (error) {
    primaryError = error
  }
  try {
    await cleanup()
  } catch (error) {
    cleanupError = error
  }
  if (primaryError && cleanupError) {
    throw new AggregateError([primaryError, cleanupError], message)
  }
  if (primaryError) throw primaryError
  if (cleanupError) throw cleanupError
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

async function requireFreePort() {
  expect(await canConnectToPort(), 'port 8080 must be unused').toBe(false)
}

async function parseMysqlInputs() {
  const defaultsPath = process.env.M14_MYSQL_DEFAULTS_FILE ?? ''
  const schema = process.env.M14_DB_SCHEMA ?? ''
  safeCheck(path.isAbsolute(defaultsPath), 'MySQL defaults path is absolute')
  safeCheck(/^tensor_m14_t02_[a-f0-9]+$/.test(schema), 'schema is isolated')

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
    const value = values.get(key)
    safeCheck(
      /^[\x21-\x7e]+$/.test(value) && !/[\s'"\\#;]/.test(value),
      `MySQL ${key} is safe for defaults syntax`,
    )
  }

  const jdbc = process.env.TENSOR_DB_URL ?? ''
  safeCheck(jdbc.startsWith('jdbc:mysql://'), 'JDBC URL uses MySQL')
  let parsed
  try {
    parsed = new URL(jdbc.slice(5))
  } catch {
    throw new Error('Safe check failed: JDBC URL is valid')
  }
  safeCheck(parsed.username === '', 'JDBC URL excludes username')
  safeCheck(parsed.password === '', 'JDBC URL excludes password')
  safeCheck(parsed.hostname === values.get('host'), 'JDBC and CLI hosts match')
  safeCheck(Number(parsed.port || '3306') === port, 'JDBC and CLI ports match')
  safeCheck(parsed.pathname === `/${schema}`, 'JDBC and CLI schemas match')
  for (const name of parsed.searchParams.keys()) {
    safeCheck(
      !/^(?:user|username|password|token)$/i.test(name),
      'JDBC URL excludes credential parameters',
    )
  }

  return {
    defaultsPath,
    schema,
    host: values.get('host'),
    port: String(port),
    cliPassword: values.get('password'),
  }
}

function mysql(sql, step) {
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
        reject(
          new Error(
            `MySQL step failed: ${step} (exit=${code ?? 'none'}, signal=${signal ?? 'none'})`,
          ),
        )
        return
      }
      void stderr
      resolve(Buffer.concat(stdout).toString('utf8').replace(/\r?\n$/, ''))
    })
    child.stdin.end(sql)
  })
}

async function verifyEmptySchema() {
  const version = await mysql('SELECT VERSION();\n', 'read server version')
  expect(version).toMatch(/^8\.4\./)
  const schemaInfo = await mysql(
    `SELECT CONCAT(DEFAULT_CHARACTER_SET_NAME, '\\t', DEFAULT_COLLATION_NAME) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '${mysqlConfig.schema}';\n`,
    'read schema definition',
  )
  expect(schemaInfo).toBe('utf8mb4\tutf8mb4_0900_as_cs')
  const tableCount = await mysql(
    `SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '${mysqlConfig.schema}';\n`,
    'count initial tables',
  )
  expect(tableCount).toBe('0')
}

async function verifyMigratedSchema() {
  const migrations = await mysql(
    `SELECT GROUP_CONCAT(CONCAT(version, ':', success) ORDER BY installed_rank SEPARATOR ',') FROM \`${mysqlConfig.schema}\`.flyway_schema_history;\n`,
    'read migration history',
  )
  expect(migrations).toBe('1:1,2:1,3:1,4:1,5:1,6:1')
  const tables = await mysql(
    `SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '${mysqlConfig.schema}' AND TABLE_NAME <> 'flyway_schema_history';\n`,
    'count business tables',
  )
  expect(tables).toBe('50')
  const rows = await mysql(
    `SELECT (SELECT COUNT(*) FROM \`${mysqlConfig.schema}\`.\`fixture__fixture_daily\`), (SELECT COUNT(*) FROM \`${mysqlConfig.schema}\`.\`tushare_pro__daily\`);\n`,
    'count initial business rows',
  )
  expect(rows).toBe('0\t0')
  expect(await triggerCount()).toBe('0')
}

function triggerCount() {
  return mysql(
    `SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = '${mysqlConfig.schema}' AND TRIGGER_NAME = '${TRIGGER}';\n`,
    'check task trigger',
  )
}

async function createFailureTrigger() {
  expect(await triggerCount()).toBe('0')
  await mysql(
    `DELIMITER //\nCREATE TRIGGER \`${mysqlConfig.schema}\`.\`${TRIGGER}\`\nAFTER UPDATE ON \`${mysqlConfig.schema}\`.\`fixture__fixture_daily\`\nFOR EACH ROW\nBEGIN\n  IF NEW.note = 'PERSISTENCE_FAILURE' THEN\n    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '${SQL_CANARY}';\n  END IF;\nEND//\nDELIMITER ;\n`,
    'create task trigger',
  )
  triggerOwned = true
  const definition = await mysql(
    `SELECT CONCAT(EVENT_OBJECT_TABLE, '\\t', EVENT_MANIPULATION, '\\t', ACTION_TIMING, '\\t', ACTION_STATEMENT) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = '${mysqlConfig.schema}' AND TRIGGER_NAME = '${TRIGGER}';\n`,
    'verify task trigger',
  )
  expect(definition).toContain('fixture__fixture_daily\tUPDATE\tAFTER\t')
  expect(definition).toContain("NEW.note = 'PERSISTENCE_FAILURE'")
  expect(definition).toContain(SQL_CANARY)
}

async function dropFailureTrigger() {
  if (!triggerOwned) return
  await mysql(
    `DROP TRIGGER \`${mysqlConfig.schema}\`.\`${TRIGGER}\`;\n`,
    'drop owned task trigger',
  )
  triggerOwned = false
  expect(await triggerCount()).toBe('0')
}

function createUpstreamStub(token) {
  const sockets = new Set()
  const modeSockets = new Map()
  const failures = []
  const counts = new Map()
  let mode = 'unset'
  let received
  let resolveReceived

  const server = createServer((request, response) => {
    const currentMode = mode
    if (!modeSockets.has(currentMode)) modeSockets.set(currentMode, new Set())
    modeSockets.get(currentMode).add(request.socket)
    counts.set(currentMode, (counts.get(currentMode) ?? 0) + 1)
    resolveReceived?.()
    let size = 0
    const chunks = []
    request.on('data', (chunk) => {
      size += chunk.length
      if (size > 64 * 1024) request.destroy()
      else chunks.push(chunk)
    })
    request.on('end', () => {
      const checks = {
        method: request.method === 'POST',
        path: request.url === '/',
        contentLength: size <= 64 * 1024,
      }
      let body
      try {
        body = JSON.parse(Buffer.concat(chunks).toString('utf8'))
      } catch {
        checks.json = false
      }
      if (body) {
        checks.keys =
          JSON.stringify(Object.keys(body).sort()) ===
          JSON.stringify(['api_name', 'fields', 'params', 'token'])
        checks.api = body.api_name === 'daily'
        checks.token = body.token === token
        checks.params =
          JSON.stringify(body.params) === JSON.stringify({ trade_date: '20260807' })
        checks.fields = body.fields === DAILY_FIELDS.join(',')
      }
      for (const [name, passed] of Object.entries(checks)) {
        if (!passed) failures.push(`${currentMode}:${name}`)
      }

      const json = (status, value) => {
        response.writeHead(status, { 'Content-Type': 'application/json' })
        response.end(JSON.stringify(value))
      }
      if (currentMode === 'success') {
        json(200, {
          code: 0,
          msg: null,
          data: {
            fields: DAILY_FIELDS,
            items: [
              [
                '000001.SZ',
                '20260807',
                '11.23',
                '11.23',
                '11.23',
                '11.23',
                '11.23',
                '0',
                '0',
                '0',
                '0',
              ],
            ],
          },
        })
      } else if (currentMode === 'auth') {
        json(401, { code: -1, msg: UPSTREAM_CANARY, data: null })
      } else if (currentMode === 'permission') {
        json(403, { code: -1, msg: UPSTREAM_CANARY, data: null })
      } else if (currentMode === 'rate') {
        json(429, { code: -1, msg: UPSTREAM_CANARY, data: null })
      } else if (currentMode === 'unavailable') {
        json(503, { code: -1, msg: UPSTREAM_CANARY, data: null })
      } else if (currentMode === 'network') {
        response.writeHead(200, {
          'Content-Type': 'application/json',
          'Content-Length': '1000',
        })
        response.write('{', () => response.destroy())
      } else if (currentMode === 'timeout') {
        // The application, not the stub, owns the real 120 second read timeout.
      } else if (currentMode === 'payload') {
        json(200, {
          code: 0,
          msg: UPSTREAM_CANARY,
          data: { fields: ['trade_date', 'ts_code'], items: [] },
        })
      } else {
        failures.push(`${currentMode}:known-mode`)
        json(500, { code: -1, msg: 'unknown test mode', data: null })
      }
    })
  })
  server.on('connection', (socket) => {
    sockets.add(socket)
    socket.once('close', () => {
      sockets.delete(socket)
      for (const owned of modeSockets.values()) owned.delete(socket)
    })
  })

  return {
    server,
    sockets,
    failures,
    counts,
    setMode(next) {
      mode = next
      received = new Promise((resolve) => {
        resolveReceived = resolve
      })
      return received
    },
    assertMode(next) {
      expect(counts.get(next) ?? 0).toBe(1)
      expect(failures).toEqual([])
    },
    async closeMode(next) {
      const owned = modeSockets.get(next) ?? new Set()
      for (const socket of owned) socket.destroy()
      await expect.poll(() => owned.size, { timeout: 2_000 }).toBe(0)
    },
  }
}

async function startStub() {
  const token = `m14-fake-${randomBytes(24).toString('hex')}`
  upstreamToken = token
  const current = createUpstreamStub(token)
  await new Promise((resolve, reject) => {
    current.server.once('error', reject)
    current.server.listen(0, '127.0.0.1', resolve)
  })
  const address = current.server.address()
  expect(typeof address).toBe('object')
  current.url = `http://127.0.0.1:${address.port}/`
  current.token = token
  return current
}

async function stopStub() {
  if (!stub) return
  const current = stub
  for (const socket of current.sockets) socket.destroy()
  await new Promise((resolve, reject) =>
    current.server.close((error) => (error ? reject(error) : resolve())),
  )
  stub = undefined
}

async function waitForHealth(current) {
  const deadline = Date.now() + HEALTH_TIMEOUT_MS
  let lastFailure = 'health did not return HTTP 200 with status UP'
  while (Date.now() < deadline) {
    if (current.closed) {
      throw new Error(
        `Owned JVM exited before readiness (pid=${current.child.pid ?? 'unknown'})`,
      )
    }
    try {
      const response = await fetch(`${BASE_URL}/actuator/health`, {
        signal: AbortSignal.timeout(2_000),
      })
      const body = await readPublicJson(response, 'health response')
      if (response.status === 200 && body?.status === 'UP') return
      lastFailure = `last health status was ${response.status}`
    } catch (error) {
      if (error instanceof Error && error.message.startsWith('Safe check failed:')) {
        throw error
      }
      lastFailure = 'health request failed'
    }
    await delay(250)
  }
  throw new Error(`Owned JVM was not ready within 90 seconds: ${lastFailure}`)
}

async function startApplication() {
  const runDirectory = await mkdtemp(path.join(tmpdir(), 'tensor-m14-t02-'))
  applicationLogPath = path.join(runDirectory, 'application.log')
  const log = await open(applicationLogPath, 'wx', 0o600)
  const child = spawn(
    'java',
    [
      '-jar',
      process.env.ACCEPTANCE_JAR,
      '--spring.profiles.active=acceptance',
      '--tensor.plugins.fixture.enabled=true',
      '--server.address=127.0.0.1',
      '--server.port=8080',
    ],
    {
      cwd: runDirectory,
      env: processEnvironment(stub.url, stub.token),
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
    if (!current.child.kill('SIGTERM')) {
      throw new Error(`Could not signal owned JVM pid=${current.child.pid}`)
    }
  }
  if (!current.closed) {
    const timeout = Symbol('stop-timeout')
    const controller = new AbortController()
    let result
    try {
      result = await Promise.race([
        current.closePromise,
        delay(STOP_TIMEOUT_MS, timeout, {
          ref: false,
          signal: controller.signal,
        }),
      ])
    } finally {
      controller.abort()
    }
    if (result === timeout) {
      throw new Error(
        `Owned JVM pid=${current.child.pid} did not exit within 150 seconds`,
      )
    }
  }
  application = undefined
  await expect.poll(canConnectToPort, { timeout: 2_000 }).toBe(false)
}

function monitorPage(page) {
  const failures = []
  const writes = []
  const allowedErrors = []
  const responseScans = []
  page.on('pageerror', () => failures.push('page error'))
  page.on('request', (request) => {
    const url = new URL(request.url())
    if (url.origin !== BASE_URL) failures.push('external request')
    if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method())) {
      writes.push(`${request.method()} ${url.pathname}`)
      if (request.method() === 'POST' && url.pathname === '/api/v1/downloads') {
        downloadPostCount += 1
      } else {
        failures.push('unexpected write')
      }
    }
  })
  page.on('requestfailed', (request) => {
    const url = new URL(request.url())
    if (url.pathname.startsWith('/api/v1/')) {
      failures.push('failed business request')
    }
  })
  page.on('response', (response) => {
    const url = new URL(response.url())
    if (url.pathname.startsWith('/api/v1/')) {
      responseScans.push(
        response
          .text()
          .then((body) => assertPublicSurface(body, 'API response'))
          .catch(() => {
            failures.push('API response safety scan')
          }),
      )
    }
    if (url.pathname.startsWith('/api/v1/') && response.status() >= 400) {
      const index = allowedErrors.findIndex(
        ({ path: allowedPath, status }) =>
          allowedPath === url.pathname && status === response.status(),
      )
      if (index >= 0) allowedErrors.splice(index, 1)
      else failures.push('unexpected business HTTP error')
    }
  })
  return {
    allowError(status, pathname = '/api/v1/downloads') {
      allowedErrors.push({ status, path: pathname })
    },
    async assertClean(expectedWrites) {
      await Promise.all(responseScans)
      await assertPageSafety(page, 'test boundary')
      expect(writes).toEqual(expectedWrites)
      expect(allowedErrors).toEqual([])
      expect(failures).toEqual([])
    },
  }
}

async function openRoute(page, route, heading) {
  const response = await page.goto(route)
  expect(response?.status()).toBe(200)
  await assertPageSafety(page, 'route')
  await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible()
}

async function selectOption(page, label, optionName) {
  const combobox = page.getByRole('combobox', { name: label, exact: true })
  await combobox.focus()
  await combobox.press('Enter')
  const option = page.getByRole('option', { name: optionName })
  await expect(option).toBeVisible()
  await option.click()
}

async function chooseFixtureDownload(page) {
  await selectOption(page, '数据源', 'Fixture')
  await selectOption(page, '数据接口', FIXTURE_API)
}

async function chooseTushareDownload(page, api = DAILY_OPTION) {
  await selectOption(page, '数据源', 'Tushare Pro')
  await selectOption(page, '数据接口', api)
}

async function chooseDataset(page, plugin, api) {
  const displayName = { fixture: 'Fixture', tushare_pro: 'Tushare Pro' }[plugin]
  expect(displayName, `known public display name for ${plugin}`).toBeTruthy()
  await selectOption(page, '数据源', displayName)
  await selectOption(page, '数据集', api)
}

function downloadResponse(response) {
  const url = new URL(response.url())
  return (
    response.request().method() === 'POST' &&
    url.pathname === '/api/v1/downloads'
  )
}

function recordsResponse(response, plugin, api) {
  const url = new URL(response.url())
  return (
    response.request().method() === 'GET' &&
    url.pathname === `/api/v1/data-sources/${plugin}/datasets/${api}/records`
  )
}

function rememberRequest(response, body) {
  expect(body.requestId).toBeTruthy()
  expect(response.headers()['x-request-id']).toBe(body.requestId)
  expect(requestIds.has(body.requestId)).toBe(false)
  requestIds.add(body.requestId)
  return body.requestId
}

function parameterSummary(requestBody) {
  return `[${Object.keys(requestBody.params).join(', ')}]`
}

async function assertNoExtraFeatures(page) {
  await expect(page.getByRole('progressbar')).toHaveCount(0)
  await expect(page.getByRole('button', { name: /取消|历史/ })).toHaveCount(0)
  await expect(page.getByRole('link', { name: /取消|历史/ })).toHaveCount(0)
  await expect(page.getByText(/下载中|适配中|入库中|百分比|进度/)).toHaveCount(0)
}

async function assertNoSuccessResult(page) {
  await expect(page.getByRole('status')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: /^下载成功/ })).toHaveCount(0)
  await expect(page.getByText('本次请求没有可写入的数据。')).toHaveCount(0)
}

async function submitDownload(
  page,
  { title, requestBody, status, success, error, timeout = 15_000 },
) {
  await assertNoExtraFeatures(page)
  const startedAt = Date.now()
  const responsePromise = page.waitForResponse(downloadResponse, { timeout })
  await page.getByRole('button', { name: '开始下载', exact: true }).click()
  const response = await responsePromise
  const body = await readPublicJson(response, 'download response')
  expect(response.status()).toBe(status)
  expect(await response.request().postDataJSON()).toEqual(requestBody)
  const requestId = rememberRequest(response, body)
  await assertPageSafety(page, 'download result')
  if (success) {
    expect(body).toEqual({ requestId, ...success })
    const panel = page.getByRole('status')
    if (success.outcome === 'EMPTY') {
      await expect(panel.getByRole('heading', { name: '下载成功，0 条数据' })).toBeVisible()
      await expect(panel.getByText('本次请求没有可写入的数据。')).toBeVisible()
      await expect(panel.getByRole('term')).toHaveCount(0)
    } else {
      await expect(panel.getByRole('heading', { name: '下载成功' })).toBeVisible()
      await expect(panel.getByRole('term')).toHaveText([
        '上游返回数',
        '插入数',
        '更新数',
      ])
      await expect(panel.getByRole('definition')).toHaveText([
        String(success.sourceRowCount),
        String(success.insertedRows),
        String(success.updatedRows),
      ])
    }
    expectedEvents.push({
      requestId,
      operation: 'download',
      pluginId: requestBody.pluginId,
      apiName: requestBody.apiName,
      paramSummary: parameterSummary(requestBody),
      outcome: success.outcome.toLowerCase(),
      failureStage: 'none',
      errorCode: 'none',
      sourceRowCount: String(success.sourceRowCount),
      insertedRows: String(success.insertedRows),
      updatedRows: String(success.updatedRows),
    })
  } else {
    const { failureStage, ...publicError } = error
    expect(Object.keys(body).sort()).toEqual([
      'code',
      'fieldErrors',
      'message',
      'requestId',
      'retryable',
    ])
    expect(body).toEqual({ requestId, ...publicError, fieldErrors: [] })
    const alert = page.getByRole('alert')
    await expect(alert.getByRole('heading', { name: '下载失败' })).toBeVisible()
    await expect(alert).toContainText(error.message)
    await expect(alert).toContainText(`请求 ID：${requestId}`)
    await expect(alert).not.toContainText(error.code)
    await expect(alert).not.toContainText(UPSTREAM_CANARY)
    await expect(alert).not.toContainText(SQL_CANARY)
    await expect(alert).not.toContainText('not-a-decimal')
    const retry = alert.getByRole('button', { name: '使用原参数重试' })
    await expect(retry).toHaveCount(error.retryable ? 1 : 0)
    await assertNoSuccessResult(page)
    expectedEvents.push({
      requestId,
      operation: 'download',
      pluginId: requestBody.pluginId,
      apiName: requestBody.apiName,
      paramSummary: parameterSummary(requestBody),
      outcome: 'failure',
      failureStage,
      errorCode: error.code,
      sourceRowCount: 'unavailable',
      insertedRows: 'unavailable',
      updatedRows: 'unavailable',
    })
  }
  await assertNoExtraFeatures(page)
  const durationMs = Date.now() - startedAt
  evidence.results.push({
    title,
    status,
    requestId,
    durationMs,
    ...success,
    ...error,
  })
  return { response, body, requestId, durationMs }
}

async function queryDataset(page, { plugin, api, option, code, navigate = false }) {
  if (navigate) {
    await page.getByRole('link', { name: '数据查看', exact: true }).click()
    await expect(page.getByRole('heading', { level: 1, name: '数据查看' })).toBeVisible()
  } else {
    await openRoute(page, '/datasets', '数据查看')
  }
  await chooseDataset(page, plugin, option)
  if (code) await page.getByLabel('证券代码 (ts_code)', { exact: true }).fill(code)
  const responsePromise = page.waitForResponse((response) =>
    recordsResponse(response, plugin, api),
  )
  await page.getByRole('button', { name: '查询', exact: true }).click()
  const response = await responsePromise
  const body = await readPublicJson(response, 'query response')
  expect(response.status()).toBe(200)
  const url = new URL(response.url())
  expect(url.searchParams.get('page') ?? '1').toBe('1')
  expect(url.searchParams.get('pageSize') ?? '50').toBe('50')
  if (code) expect(url.searchParams.get('tsCode')).toBe(code)
  assertExactKeys(
    body,
    [
      'apiName',
      'columns',
      'items',
      'page',
      'pageSize',
      'pluginId',
      'requestId',
      'totalElements',
      'totalPages',
    ],
    'query response',
  )
  safeCheck(body.pluginId === plugin, 'query response plugin matches route')
  safeCheck(body.apiName === api, 'query response API matches route')
  const requestId = rememberRequest(response, body)
  queryCount += 1
  expectedEvents.push({
    requestId,
    operation: 'query',
    pluginId: plugin,
    apiName: api,
    outcome: 'success',
    failureStage: 'none',
    errorCode: 'none',
    filterNames: code ? '[ts_code]' : '[]',
    page: '1',
    pageSize: '50',
    resultCount: String(body.items.length),
    totalElements: String(body.totalElements),
  })
  return body
}

function shanghaiTimestamp(value) {
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
  })
    .formatToParts(parsed)
    .reduce((result, part) => ({ ...result, [part.type]: part.value }), {})
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`
}

async function assertSingleRow(page, body, expectedColumns, expectedRow) {
  await assertPageSafety(page, 'query result')
  expect(body).toMatchObject({
    page: 1,
    pageSize: 50,
    totalElements: 1,
    totalPages: 1,
    columns: expectedColumns,
  })
  expect(body.items).toHaveLength(1)
  expect(body.items[0]).toEqual(expectedRow)
  await expect(page.getByRole('columnheader')).toHaveText(expectedColumns)
  const row = page
    .getByRole('row')
    .filter({ has: page.getByRole('cell', { name: expectedRow.ts_code, exact: true }) })
  await expect(row).toHaveCount(1)
  return row
}

function fixtureRow(body) {
  const row = body.items[0]
  expect(row).toEqual({
    ts_code: '000001.SZ',
    trade_date: '2026-08-07',
    amount: '11.230000000000000000',
    note: null,
    source_plugin: 'fixture',
    source_api: 'fixture_daily',
    ingested_at: row.ingested_at,
  })
  return row
}

async function assertFixtureBody(page, body, expected = fixtureRow(body)) {
  const columns = [
    'ts_code',
    'trade_date',
    'amount',
    'note',
    'source_plugin',
    'source_api',
    'ingested_at',
  ]
  const row = await assertSingleRow(page, body, columns, expected)
  await expect(row.getByRole('cell')).toHaveText([
    '000001.SZ',
    '2026-08-07',
    '11.230000000000000000',
    '--',
    'fixture',
    'fixture_daily',
    shanghaiTimestamp(expected.ingested_at),
  ])
  return row
}

function dailyRow(body) {
  const row = body.items[0]
  const decimal = '11.230000000000000000'
  const zero = '0.000000000000000000'
  expect(row).toEqual({
    ts_code: '000001.SZ',
    trade_date: '2026-08-07',
    open: decimal,
    high: decimal,
    low: decimal,
    close: decimal,
    pre_close: decimal,
    change: zero,
    pct_chg: zero,
    vol: zero,
    amount: zero,
    source_plugin: 'tushare_pro',
    source_api: 'daily',
    ingested_at: row.ingested_at,
  })
  return row
}

async function assertDailyBody(page, body, expected = dailyRow(body)) {
  const columns = [...DAILY_FIELDS, 'source_plugin', 'source_api', 'ingested_at']
  const row = await assertSingleRow(page, body, columns, expected)
  await expect(row.getByRole('cell')).toHaveText([
    '000001.SZ',
    '2026-08-07',
    ...Array(5).fill('11.230000000000000000'),
    ...Array(4).fill('0.000000000000000000'),
    'tushare_pro',
    'daily',
    shanghaiTimestamp(expected.ingested_at),
  ])
  return row
}

async function assertFixtureUnchanged(page) {
  const body = await queryDataset(page, {
    plugin: 'fixture',
    api: 'fixture_daily',
    option: FIXTURE_API,
  })
  expect(body.items[0]).toEqual(fixtureBaseline)
  await assertFixtureBody(page, body, fixtureBaseline)
  return body
}

async function assertDailyUnchanged(page) {
  const body = await queryDataset(page, {
    plugin: 'tushare_pro',
    api: 'daily',
    option: DAILY_OPTION,
  })
  expect(body.items[0]).toEqual(dailyBaseline)
  await assertDailyBody(page, body, dailyBaseline)
  return body
}

function parseCompletedEvent(line) {
  const marker = 'tensor.operation.completed'
  const start = line.indexOf(marker)
  if (start < 0) throw new Error('Completion event marker missing')
  const text = line.slice(start + marker.length).trim()
  const fields = {}
  const pattern = /([A-Za-z]+)=(\[[^\]]*\]|\S+)/g
  let cursor = 0
  for (const match of text.matchAll(pattern)) {
    if (text.slice(cursor, match.index).trim() !== '') {
      throw new Error('Completion event contains an unparsed token')
    }
    const [, key, value] = match
    if (Object.hasOwn(fields, key)) {
      throw new Error('Completion event repeats a field')
    }
    fields[key] = value
    cursor = match.index + match[0].length
  }
  if (text.slice(cursor).trim() !== '') {
    throw new Error('Completion event contains trailing unparsed text')
  }
  return fields
}

async function readCompletedEvent(expected) {
  let matches = []
  await expect
    .poll(
      async () => {
        const log = await readFile(applicationLogPath, 'utf8')
        assertPrivateLogSafety(log)
        matches = log
          .split(/\r?\n/)
          .filter(
            (line) =>
              line.includes('tensor.operation.completed') &&
              new RegExp(
                `requestId=${expected.requestId.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?:\\s|$)`,
              ).test(line),
          )
        return matches.length
      },
      { timeout: 5_000 },
    )
    .toBe(1)
  const line = matches[0]
  assertPublicSurface(line, 'completion event')
  const allowed =
    expected.operation === 'download'
      ? new Set([
          'requestId',
          'operation',
          'pluginId',
          'apiName',
          'paramSummary',
          'sourceRowCount',
          'insertedRows',
          'updatedRows',
          'durationMs',
          'outcome',
          'failureStage',
          'errorCode',
        ])
      : new Set([
          'requestId',
          'operation',
          'pluginId',
          'apiName',
          'filterNames',
          'page',
          'pageSize',
          'resultCount',
          'totalElements',
          'durationMs',
          'outcome',
          'failureStage',
          'errorCode',
        ])
  const actual = parseCompletedEvent(line)
  safeCheck(
    JSON.stringify(Object.keys(actual).sort()) === JSON.stringify([...allowed].sort()),
    'completion event fields are exact',
  )
  for (const [key, value] of Object.entries(expected)) {
    safeCheck(actual[key] === String(value), `completion event ${key} matches`)
  }
  safeCheck(!Object.hasOwn(actual, 'message'), 'completion event excludes message')
  safeCheck(!Object.hasOwn(actual, 'cause'), 'completion event excludes cause')
  safeCheck(!Object.hasOwn(actual, 'stack'), 'completion event excludes stack')
  safeCheck(!Object.hasOwn(actual, 'throwable'), 'completion event excludes throwable')
  evidence.events.push(
    Object.fromEntries([...allowed].map((key) => [key, actual[key]])),
  )
}

async function verifyEventsAndSafety() {
  evidence.events = []
  for (const expected of expectedEvents) await readCompletedEvent(expected)
  const log = await readFile(applicationLogPath, 'utf8')
  assertPrivateLogSafety(log)
  const completed = log
    .split(/\r?\n/)
    .filter((line) => line.includes('tensor.operation.completed'))
  safeCheck(
    completed.length === expectedEvents.length,
    'private log completion event count matches',
  )
}

async function recordScreenshot(locator, testInfo, name) {
  const screenshotPath = testInfo.outputPath(name)
  await assertPageSafety(locator.page(), 'screenshot page')
  let text
  try {
    text = await locator.innerText()
  } catch {
    throw new Error('Safe check failed: screenshot text readable')
  }
  assertPublicSurface(text, 'screenshot')
  await locator.screenshot({ path: screenshotPath })
  evidence.screenshots.push({ name, path: screenshotPath })
}

test.use({
  viewport: { width: 1440, height: 1000 },
  trace: 'off',
  video: 'off',
  screenshot: 'off',
})

test.describe('download outcome matrix', () => {
  test.describe.configure({ mode: 'serial', retries: 0, timeout: 180_000 })

  test.beforeAll(async () => {
    test.setTimeout(300_000)
    evidence.startedAt = new Date().toISOString()
    expect(path.isAbsolute(process.env.ACCEPTANCE_JAR ?? '')).toBe(true)
    expect((await stat(process.env.ACCEPTANCE_JAR)).isFile()).toBe(true)
    for (const name of DB_VARIABLES) {
      safeCheck((process.env[name]?.length ?? 0) > 0, `${name} is present`)
    }
    safeCheck(
      (process.env.PLAYWRIGHT_BASE_URL ?? BASE_URL) === BASE_URL,
      'Playwright base URL is isolated',
    )
    mysqlConfig = await parseMysqlInputs()
    await requireFreePort()
    await verifyEmptySchema()
    evidence.jarSha256 = createHash('sha256')
      .update(await readFile(process.env.ACCEPTANCE_JAR))
      .digest('hex')
    try {
      stub = await startStub()
      await startApplication()
      await verifyMigratedSchema()
    } catch (error) {
      const failures = [error]
      try {
        await stopApplication()
      } catch (cleanupError) {
        failures.push(cleanupError)
      }
      try {
        await stopStub()
      } catch (cleanupError) {
        failures.push(cleanupError)
      }
      if (failures.length === 1) throw error
      throw new AggregateError(failures, 'M14-T02 setup and cleanup failed')
    }
  })

  test.afterAll(async () => {
    test.setTimeout(180_000)
    const failures = []
    try {
      await dropFailureTrigger()
      evidence.cleanup.triggerAbsent = (await triggerCount()) === '0'
    } catch (error) {
      failures.push(error)
    }
    try {
      await stopApplication()
      evidence.cleanup.jvmStopped = !(await canConnectToPort())
    } catch (error) {
      failures.push(error)
    }
    try {
      await verifyEventsAndSafety()
    } catch (error) {
      failures.push(error)
    }
    let stubCalls = 0
    try {
      stubCalls = [...stub.counts.values()].reduce((sum, value) => sum + value, 0)
      evidence.counters = {
        downloadPosts: downloadPostCount,
        queries: queryCount,
        stubCalls,
      }
      expect(evidence.counters.downloadPosts).toBe(14)
      expect(evidence.counters.stubCalls).toBe(8)
      expect(stub.failures).toEqual([])
    } catch (error) {
      failures.push(error)
    }
    try {
      await stopStub()
      evidence.cleanup.stubStopped = true
    } catch (error) {
      failures.push(error)
    }
    evidence.finishedAt = new Date().toISOString()
    const evidencePath = path.join(path.dirname(applicationLogPath), 'evidence.json')
    try {
      const shared = JSON.stringify(evidence, null, 2)
      assertPublicSurface(shared, 'shared evidence')
      await writeFile(evidencePath, `${shared}\n`, {
        encoding: 'utf8',
        flag: 'wx',
        mode: 0o600,
      })
      console.info(`M14-T02 safe evidence: ${evidencePath}`)
    } catch (error) {
      failures.push(error)
    }
    if (failures.length) throw new AggregateError(failures, 'M14-T02 cleanup failed')
  })

  test('blocksMissingRequiredDate', async ({ page }) => {
    const monitor = monitorPage(page)
    const posts = downloadPostCount
    const calls = [...stub.counts.values()].reduce((sum, value) => sum + value, 0)
    await openRoute(page, '/downloads', '数据下载')
    await chooseTushareDownload(page)
    const date = page.getByLabel('交易日期', { exact: false })
    await expect(page.getByRole('button', { name: '开始下载', exact: true })).toBeEnabled()
    await page.getByRole('button', { name: '开始下载', exact: true }).click()
    await expect(date).toHaveAttribute('aria-invalid', 'true')
    await expect(date).toBeFocused()
    const ids = (await date.getAttribute('aria-describedby')).split(' ')
    expect(ids.length).toBeGreaterThan(0)
    const fieldError = page.locator(`#${ids.at(-1)}`)
    await expect(fieldError).toHaveAttribute('role', 'alert')
    await expect(fieldError).toHaveText('此项为必填项')
    await expect(page.getByRole('button', { name: '开始下载', exact: true })).toBeEnabled()
    await expect(page.getByRole('status')).toHaveCount(0)
    await expect(page.getByRole('heading', { name: '下载失败' })).toHaveCount(0)
    await expect(page.getByText('下载成功，0 条数据')).toHaveCount(0)
    await assertNoExtraFeatures(page)
    expect(downloadPostCount).toBe(posts)
    expect([...stub.counts.values()].reduce((sum, value) => sum + value, 0)).toBe(calls)
    await monitor.assertClean([])
  })

  test('blocksReversedDateRange', async ({ page }) => {
    const monitor = monitorPage(page)
    const posts = downloadPostCount
    const calls = [...stub.counts.values()].reduce((sum, value) => sum + value, 0)
    await openRoute(page, '/downloads', '数据下载')
    await chooseTushareDownload(page, NEW_SHARE_API)
    const start = page.getByLabel('开始日期', { exact: false })
    const end = page.getByLabel('结束日期', { exact: false })
    await start.fill('2026-08-08')
    await start.press('Tab')
    await end.fill('2026-08-07')
    await end.press('Tab')
    await expect(start).toHaveValue('2026-08-08')
    await expect(end).toHaveValue('2026-08-07')
    await page.getByRole('button', { name: '开始下载', exact: true }).click()
    await expect(start).toHaveAttribute('aria-invalid', 'true')
    await expect(start).toBeFocused()
    await expect(end).not.toHaveAttribute('aria-invalid', 'true')
    const ids = (await start.getAttribute('aria-describedby')).split(' ')
    await expect(page.locator(`#${ids.at(-1)}`)).toHaveText(
      '开始日期不得晚于结束日期',
    )
    await expect(page.getByText('开始日期不得晚于结束日期')).toHaveCount(1)
    await assertNoExtraFeatures(page)
    expect(downloadPostCount).toBe(posts)
    expect([...stub.counts.values()].reduce((sum, value) => sum + value, 0)).toBe(calls)
    await monitor.assertClean([])
  })

  test('upsertsDuplicateFixtureSuccess', async ({ page }, testInfo) => {
    const monitor = monitorPage(page)
    let body = await queryDataset(page, {
      plugin: 'fixture',
      api: 'fixture_daily',
      option: FIXTURE_API,
    })
    expect(body).toMatchObject({ totalElements: 0, totalPages: 0, items: [] })
    await openRoute(page, '/downloads', '数据下载')
    await chooseFixtureDownload(page)
    const scenario = page.getByRole('combobox', { name: /场景/ })
    await scenario.focus()
    await scenario.press('Enter')
    await expect(
      page.getByRole('option', { name: 'SUCCESS', exact: true, selected: true }),
    ).toBeVisible()
    await scenario.press('Escape')
    const successBase = {
      outcome: 'SUCCESS',
      pluginId: 'fixture',
      apiName: 'fixture_daily',
      sourceRowCount: 1,
    }
    await submitDownload(page, {
      title: 'upsertsDuplicateFixtureSuccess:first',
      requestBody: {
        pluginId: 'fixture',
        apiName: 'fixture_daily',
        params: { scenario: 'SUCCESS' },
      },
      status: 200,
      success: { ...successBase, insertedRows: 1, updatedRows: 0, message: '下载成功' },
    })
    body = await queryDataset(page, {
      plugin: 'fixture',
      api: 'fixture_daily',
      option: FIXTURE_API,
      code: '000001.SZ',
      navigate: true,
    })
    const first = structuredClone(fixtureRow(body))
    await assertFixtureBody(page, body, first)
    await expect
      .poll(() => Date.now(), { timeout: 5_000 })
      .toBeGreaterThan(new Date(first.ingested_at).getTime() + 1_000)

    await openRoute(page, '/downloads', '数据下载')
    await chooseFixtureDownload(page)
    await submitDownload(page, {
      title: 'upsertsDuplicateFixtureSuccess:second',
      requestBody: {
        pluginId: 'fixture',
        apiName: 'fixture_daily',
        params: { scenario: 'SUCCESS' },
      },
      status: 200,
      success: { ...successBase, insertedRows: 0, updatedRows: 1, message: '下载成功' },
    })
    body = await queryDataset(page, {
      plugin: 'fixture',
      api: 'fixture_daily',
      option: FIXTURE_API,
      code: '000001.SZ',
    })
    const second = structuredClone(fixtureRow(body))
    await assertFixtureBody(page, body, second)
    expect({ ...second, ingested_at: first.ingested_at }).toEqual(first)
    expect(new Date(second.ingested_at).getTime()).toBeGreaterThan(
      new Date(first.ingested_at).getTime(),
    )
    evidence.fixtureUpsert = { first, second }
    fixtureBaseline = second
    await recordScreenshot(
      page.getByRole('row').filter({ hasText: '000001.SZ' }),
      testInfo,
      'fixture-upsert-row.png',
    )
    await monitor.assertClean(['POST /api/v1/downloads', 'POST /api/v1/downloads'])
  })

  test('keepsRowsOnFixtureEmpty', async ({ page }) => {
    const monitor = monitorPage(page)
    await openRoute(page, '/downloads', '数据下载')
    await chooseFixtureDownload(page)
    await selectOption(page, /场景/, 'EMPTY')
    await submitDownload(page, {
      title: 'keepsRowsOnFixtureEmpty',
      requestBody: {
        pluginId: 'fixture',
        apiName: 'fixture_daily',
        params: { scenario: 'EMPTY' },
      },
      status: 200,
      success: {
        outcome: 'EMPTY',
        pluginId: 'fixture',
        apiName: 'fixture_daily',
        sourceRowCount: 0,
        insertedRows: 0,
        updatedRows: 0,
        message: '下载成功，0 条数据',
      },
    })
    await assertFixtureUnchanged(page)
    await monitor.assertClean(['POST /api/v1/downloads'])
  })

  test('showsFixtureSourceFailure', async ({ page }) => {
    const monitor = monitorPage(page)
    await openRoute(page, '/downloads', '数据下载')
    await chooseFixtureDownload(page)
    await selectOption(page, /场景/, 'SOURCE_FAILURE')
    monitor.allowError(502)
    await submitDownload(page, {
      title: 'showsFixtureSourceFailure',
      requestBody: {
        pluginId: 'fixture',
        apiName: 'fixture_daily',
        params: { scenario: 'SOURCE_FAILURE' },
      },
      status: 502,
      error: {
        code: 'SOURCE_UNAVAILABLE',
        message: 'Source is unavailable',
        retryable: true,
        failureStage: 'source',
      },
    })
    await assertFixtureUnchanged(page)
    await monitor.assertClean(['POST /api/v1/downloads'])
  })

  test('rejectsFixtureTypeFailure', async ({ page }) => {
    const monitor = monitorPage(page)
    await openRoute(page, '/downloads', '数据下载')
    await chooseFixtureDownload(page)
    await selectOption(page, /场景/, 'TYPE_FAILURE')
    monitor.allowError(422)
    const { body } = await submitDownload(page, {
      title: 'rejectsFixtureTypeFailure',
      requestBody: {
        pluginId: 'fixture',
        apiName: 'fixture_daily',
        params: { scenario: 'TYPE_FAILURE' },
      },
      status: 422,
      error: {
        code: 'ADAPTER_TYPE_INVALID',
        message: 'Source data contains an invalid value',
        retryable: false,
        failureStage: 'adapter',
      },
    })
    safeCheck(
      !/not-a-decimal|field=|row=/.test(JSON.stringify(body)),
      'adapter response excludes raw field details',
    )
    await assertFixtureUnchanged(page)
    await monitor.assertClean(['POST /api/v1/downloads'])
  })

  test('rollsBackFixturePersistenceFailure', async ({ page }, testInfo) => {
    const monitor = monitorPage(page)
    await createFailureTrigger()
    await runWithCleanup(
      async () => {
        await assertFixtureUnchanged(page)
        await openRoute(page, '/downloads', '数据下载')
        await chooseFixtureDownload(page)
        await selectOption(page, /场景/, 'PERSISTENCE_FAILURE')
        monitor.allowError(500)
        const { body } = await submitDownload(page, {
          title: 'rollsBackFixturePersistenceFailure',
          requestBody: {
            pluginId: 'fixture',
            apiName: 'fixture_daily',
            params: { scenario: 'PERSISTENCE_FAILURE' },
          },
          status: 500,
          error: {
            code: 'PERSISTENCE_FAILED',
            message: 'Persistence failed',
            retryable: true,
            failureStage: 'persistence',
          },
        })
        safeCheck(
          !/PERSISTENCE_FAILURE|M14_T02_FAULT_SQL_CANARY|SQLSTATE|UPDATE/i.test(
            JSON.stringify(body),
          ),
          'persistence response excludes SQL details',
        )
        await assertFixtureUnchanged(page)
        await recordScreenshot(
          page.getByRole('row').filter({ hasText: '000001.SZ' }),
          testInfo,
          'fixture-rollback-row.png',
        )
      },
      dropFailureTrigger,
      'Persistence assertion and trigger cleanup failed',
    )
    await monitor.assertClean(['POST /api/v1/downloads'])
  })

  test('downloadsDailyFromLocalUpstream', async ({ page }, testInfo) => {
    const monitor = monitorPage(page)
    let body = await queryDataset(page, {
      plugin: 'tushare_pro',
      api: 'daily',
      option: DAILY_OPTION,
    })
    expect(body).toMatchObject({ totalElements: 0, totalPages: 0, items: [] })
    await openRoute(page, '/downloads', '数据下载')
    await chooseTushareDownload(page)
    const tradeDate = page.getByLabel('交易日期', { exact: false })
    await tradeDate.fill('2026-08-07')
    await tradeDate.press('Tab')
    await expect(tradeDate).toHaveValue('2026-08-07')
    stub.setMode('success')
    await submitDownload(page, {
      title: 'downloadsDailyFromLocalUpstream',
      requestBody: {
        pluginId: 'tushare_pro',
        apiName: 'daily',
        params: { trade_date: '20260807' },
      },
      status: 200,
      success: {
        outcome: 'SUCCESS',
        pluginId: 'tushare_pro',
        apiName: 'daily',
        sourceRowCount: 1,
        insertedRows: 1,
        updatedRows: 0,
        message: '下载成功',
      },
    })
    stub.assertMode('success')
    body = await queryDataset(page, {
      plugin: 'tushare_pro',
      api: 'daily',
      option: DAILY_OPTION,
      code: '000001.SZ',
    })
    dailyBaseline = structuredClone(dailyRow(body))
    evidence.dailyRow = dailyBaseline
    const row = await assertDailyBody(page, body, dailyBaseline)
    await recordScreenshot(row, testInfo, 'daily-success-row.png')
    await monitor.assertClean(['POST /api/v1/downloads'])
  })

  for (const scenario of [
    {
      title: 'showsSourceAuthFailure',
      mode: 'auth',
      status: 502,
      code: 'SOURCE_AUTH_FAILED',
      message: 'Source authentication failed',
      retryable: false,
    },
    {
      title: 'showsSourcePermissionFailure',
      mode: 'permission',
      status: 502,
      code: 'SOURCE_PERMISSION_DENIED',
      message: 'Source permission denied',
      retryable: false,
    },
    {
      title: 'showsSourceRateLimitFailure',
      mode: 'rate',
      status: 502,
      code: 'SOURCE_RATE_LIMITED',
      message: 'Source rate limit exceeded',
      retryable: true,
    },
    {
      title: 'showsSourceUnavailableFailure',
      mode: 'unavailable',
      status: 502,
      code: 'SOURCE_UNAVAILABLE',
      message: 'Source is unavailable',
      retryable: true,
    },
    {
      title: 'showsSourceNetworkFailure',
      mode: 'network',
      status: 502,
      code: 'SOURCE_NETWORK_ERROR',
      message: 'Source network request failed',
      retryable: true,
    },
    {
      title: 'showsSourceTimeoutFailure',
      mode: 'timeout',
      status: 504,
      code: 'SOURCE_TIMEOUT',
      message: 'Source request timed out',
      retryable: true,
    },
    {
      title: 'showsSourcePayloadFailure',
      mode: 'payload',
      status: 502,
      code: 'SOURCE_PAYLOAD_INVALID',
      message: 'Source returned an invalid payload',
      retryable: true,
    },
  ]) {
    test(scenario.title, async ({ page }, testInfo) => {
      const monitor = monitorPage(page)
      await openRoute(page, '/downloads', '数据下载')
      await chooseTushareDownload(page)
      const tradeDate = page.getByLabel('交易日期', { exact: false })
      await tradeDate.fill('2026-08-07')
      await tradeDate.press('Tab')
      await expect(tradeDate).toHaveValue('2026-08-07')
      const received = stub.setMode(scenario.mode)
      monitor.allowError(scenario.status)
      const startedAt = Date.now()
      const responsePromise = page.waitForResponse(downloadResponse, {
        timeout: scenario.mode === 'timeout' ? 135_000 : 15_000,
      })
      await assertNoExtraFeatures(page)
      await page.getByRole('button', { name: '开始下载', exact: true }).click()
      if (scenario.mode === 'timeout') {
        await received
        const button = page.getByRole('button', { name: '开始下载', exact: true })
        await expect(button).toBeDisabled()
        await expect(button).toHaveAttribute('aria-busy', 'true')
        await expect(page.getByRole('combobox', { name: '数据源', exact: true })).toBeDisabled()
        await expect(page.getByRole('combobox', { name: '数据接口', exact: true })).toBeDisabled()
        await expect(tradeDate).toBeDisabled()
        await assertNoExtraFeatures(page)
      }
      const response = await responsePromise
      const body = await readPublicJson(response, 'download response')
      expect(response.status()).toBe(scenario.status)
      expect(await response.request().postDataJSON()).toEqual({
        pluginId: 'tushare_pro',
        apiName: 'daily',
        params: { trade_date: '20260807' },
      })
      const requestId = rememberRequest(response, body)
      await assertPageSafety(page, 'download result')
      expect(body).toEqual({
        requestId,
        code: scenario.code,
        message: scenario.message,
        retryable: scenario.retryable,
        fieldErrors: [],
      })
      const alert = page.getByRole('alert')
      await expect(alert.getByRole('heading', { name: '下载失败' })).toBeVisible()
      await expect(alert).toContainText(scenario.message)
      await expect(alert).toContainText(`请求 ID：${requestId}`)
      await expect(alert).not.toContainText(scenario.code)
      await expect(alert).not.toContainText(UPSTREAM_CANARY)
      await expect(alert.getByRole('button', { name: '使用原参数重试' })).toHaveCount(
        scenario.retryable ? 1 : 0,
      )
      await assertNoSuccessResult(page)
      expectedEvents.push({
        requestId,
        operation: 'download',
        pluginId: 'tushare_pro',
        apiName: 'daily',
        paramSummary: '[trade_date]',
        outcome: 'failure',
        failureStage: 'source',
        errorCode: scenario.code,
        sourceRowCount: 'unavailable',
        insertedRows: 'unavailable',
        updatedRows: 'unavailable',
      })
      const durationMs = Date.now() - startedAt
      if (scenario.mode === 'timeout') {
        expect(durationMs).toBeGreaterThanOrEqual(120_000)
        expect(durationMs).toBeLessThan(130_000)
        await expect(page.getByRole('button', { name: '开始下载', exact: true })).toBeEnabled()
        await stub.closeMode('timeout')
      }
      evidence.results.push({
        title: scenario.title,
        status: scenario.status,
        requestId,
        code: scenario.code,
        retryable: scenario.retryable,
        durationMs,
      })
      stub.assertMode(scenario.mode)
      if (scenario.mode === 'payload') {
        await recordScreenshot(alert, testInfo, 'source-payload-error.png')
      }
      await assertDailyUnchanged(page)
      await assertNoExtraFeatures(page)
      await monitor.assertClean(['POST /api/v1/downloads'])
    })
  }
})
