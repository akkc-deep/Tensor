import { spawn } from 'node:child_process'
import { createHash } from 'node:crypto'
import { lstat, mkdir, mkdtemp, open, readFile, rm, writeFile } from 'node:fs/promises'
import { createServer } from 'node:http'
import { createConnection } from 'node:net'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { setTimeout as delay } from 'node:timers/promises'

const BASE_URL = 'http://127.0.0.1:8080'
const HEALTH_TIMEOUT_MS = 90_000
const STOP_TIMEOUT_MS = 150_000
const MYSQL_TIMEOUT_MS = 20_000
const REQUIRED = [
  'ACCEPTANCE_JAR',
  'TENSOR_DB_URL',
  'TENSOR_DB_USERNAME',
  'TENSOR_DB_PASSWORD',
  'M14_DB_SCHEMA',
  'M14_MYSQL_DEFAULTS_FILE',
]
const ROOT = fileURLToPath(new URL('../..', import.meta.url))
const EVIDENCE_DIRECTORY = path.join(ROOT, 'docs', 'verification', 'data-integrity-t13', 'real')

function safeCheck(condition, message) {
  if (!condition) throw new Error(`Safe check failed: ${message}`)
}

function canConnectToPort() {
  return new Promise(resolve => {
    const socket = createConnection({ host: '127.0.0.1', port: 8080 })
    socket.setTimeout(2_000)
    socket.once('connect', () => { socket.destroy(); resolve(true) })
    socket.once('timeout', () => { socket.destroy(); resolve(false) })
    socket.once('error', () => resolve(false))
  })
}

function capture(command, args, step) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      shell: false,
      env: { PATH: process.env.PATH, LANG: 'C', LC_ALL: 'C' },
      stdio: ['ignore', 'pipe', 'pipe'],
    })
    const output = []
    child.stdout.on('data', chunk => output.push(chunk))
    child.stderr.on('data', chunk => output.push(chunk))
    child.once('error', error => reject(new Error(`${step} could not start (${error.code ?? 'error'})`)))
    child.once('close', code => {
      if (code !== 0) reject(new Error(`${step} failed (exit=${code ?? 'none'})`))
      else resolve(Buffer.concat(output).toString('utf8').trim())
    })
  })
}

async function parseEnvironment() {
  for (const name of REQUIRED) safeCheck(Boolean(process.env[name]?.trim()), `${name} is present`)
  safeCheck(process.env.PLAYWRIGHT_BASE_URL === BASE_URL, 'Playwright base URL is exact loopback')
  safeCheck(path.isAbsolute(process.env.ACCEPTANCE_JAR), 'acceptance JAR path is absolute')
  const jarState = await lstat(process.env.ACCEPTANCE_JAR)
  safeCheck(jarState.isFile() && !jarState.isSymbolicLink(), 'acceptance JAR is an ordinary file')

  const schema = process.env.M14_DB_SCHEMA
  const defaultsPath = process.env.M14_MYSQL_DEFAULTS_FILE
  safeCheck(/^tensor_integrity_t13_[0-9a-f]+$/.test(schema), 'schema is isolated')
  safeCheck(path.isAbsolute(defaultsPath), 'MySQL defaults path is absolute')
  const state = await lstat(defaultsPath)
  safeCheck(state.isFile() && !state.isSymbolicLink(), 'MySQL defaults is an ordinary file')
  safeCheck((state.mode & 0o777) === 0o600, 'MySQL defaults permissions are 0600')
  if (typeof process.getuid === 'function') safeCheck(state.uid === process.getuid(), 'MySQL defaults owner is current user')
  const bytes = await readFile(defaultsPath)
  safeCheck(!bytes.subarray(0, 3).equals(Buffer.from([0xef, 0xbb, 0xbf])), 'MySQL defaults excludes BOM')
  let text
  try { text = new TextDecoder('utf-8', { fatal: true }).decode(bytes) }
  catch { throw new Error('Safe check failed: MySQL defaults is UTF-8') }
  safeCheck(!/\r(?!\n)/.test(text), 'MySQL defaults line endings are valid')
  const normalized = text.replace(/(?:\r\n|\n)$/, '')
  safeCheck(!/\n$/.test(normalized), 'MySQL defaults has at most one terminal newline')
  const lines = normalized.split(/\r?\n/)
  safeCheck(lines.length === 6 && lines[0] === '[client]', 'MySQL defaults shape is exact')
  const values = new Map()
  for (const line of lines.slice(1)) {
    safeCheck(/^[a-z]+=.*$/.test(line), 'MySQL defaults entry syntax is valid')
    const separator = line.indexOf('=')
    const key = line.slice(0, separator), value = line.slice(separator + 1)
    safeCheck(['host', 'port', 'user', 'password', 'protocol'].includes(key), 'MySQL defaults key is allowed')
    safeCheck(!values.has(key), 'MySQL defaults keys are unique')
    values.set(key, value)
  }
  safeCheck(JSON.stringify([...values.keys()].sort()) === JSON.stringify(['host', 'password', 'port', 'protocol', 'user']), 'MySQL defaults keys are complete')
  safeCheck(values.get('host') === '127.0.0.1', 'MySQL host is loopback')
  safeCheck(/^\d+$/.test(values.get('port')), 'MySQL port is numeric')
  const port = Number(values.get('port'))
  safeCheck(port >= 1 && port <= 65_535, 'MySQL port is in range')
  safeCheck(values.get('protocol') === 'TCP', 'MySQL protocol is TCP')
  for (const name of ['user', 'password']) {
    safeCheck(/^[\x21-\x7e]+$/.test(values.get(name)) && !/[\s'"\\#;]/.test(values.get(name)), `MySQL ${name} is safe`)
  }
  safeCheck(values.get('user') === process.env.TENSOR_DB_USERNAME, 'application and CLI users match')
  safeCheck(values.get('password') === process.env.TENSOR_DB_PASSWORD, 'application and CLI passwords match')

  const jdbc = /^jdbc:mysql:\/\/127\.0\.0\.1:([0-9]+)\/([^?]+)(?:\?(.*))?$/.exec(process.env.TENSOR_DB_URL)
  safeCheck(Boolean(jdbc), 'JDBC URL is loopback MySQL without authority credentials')
  safeCheck(Number(jdbc[1]) === port && jdbc[2] === schema, 'JDBC and CLI target the same schema')
  const query = new URLSearchParams(jdbc[3] ?? '')
  safeCheck(![...query.keys()].some(key => /^(?:user|username|password|token)$/i.test(key)), 'JDBC URL excludes credentials')

  const jarSha256 = await sha256File(process.env.ACCEPTANCE_JAR)
  if (process.env.ISSUE_017_ACCEPTANCE_JAR_SHA256 !== undefined) {
    safeCheck(process.env.ISSUE_017_ACCEPTANCE_JAR_SHA256 === jarSha256, 'acceptance JAR hash matches input')
  }
  const java = await capture('java', ['-version'], 'Java version check')
  const mysql = await capture('mysql', ['--version'], 'MySQL client version check')
  safeCheck(/version "21(?:\.|\")/.test(java), 'Java major version is 21')
  safeCheck(process.version === 'v24.15.0', 'Node version is 24.15.0')
  safeCheck(/Ver 8\.4\./.test(mysql), 'MySQL client major version is 8.4')
  safeCheck(!(await canConnectToPort()), 'port 8080 is unused')
  return { schema, defaultsPath, host: values.get('host'), port: String(port), jarSha256 }
}

function applicationEnvironment(receiverUrl) {
  const env = Object.fromEntries(Object.entries(process.env).filter(([name]) =>
    !/^(TENSOR_|SPRING_|SERVER_|MYSQL_|M14_|JAVA_TOOL_OPTIONS$|_JAVA_OPTIONS$|JDK_JAVA_OPTIONS$|MAVEN_OPTS$)/.test(name)))
  for (const name of ['TENSOR_DB_URL', 'TENSOR_DB_USERNAME', 'TENSOR_DB_PASSWORD']) env[name] = process.env[name]
  env.TENSOR_TUSHARE_BASE_URL = receiverUrl
  return env
}

class IntegrityFixtureEnvironment {
  constructor(config, receiver) {
    Object.assign(this, config)
    this.receiver = receiver
    this.application = null
    this.upstreamCalls = 0
    this.lifecycle = []
  }

  mysql(sql, step) {
    return new Promise((resolve, reject) => {
      const child = spawn('mysql', [
        `--defaults-file=${this.defaultsPath}`,
        '--no-login-paths',
        `--host=${this.host}`,
        `--port=${this.port}`,
        '--protocol=TCP',
        '--batch',
        '--skip-column-names',
        '--raw',
        `--database=${this.schema}`,
      ], {
        shell: false,
        env: { PATH: process.env.PATH, LANG: 'C', LC_ALL: 'C' },
        stdio: ['pipe', 'pipe', 'pipe'],
      })
      const stdout = []
      let settled = false
      const timer = setTimeout(() => {
        if (settled) return
        settled = true
        child.kill('SIGTERM')
        reject(new Error(`MySQL step timed out: ${step}`))
      }, MYSQL_TIMEOUT_MS)
      child.stdout.on('data', chunk => stdout.push(chunk))
      child.once('error', error => {
        if (settled) return
        settled = true
        clearTimeout(timer)
        reject(new Error(`MySQL step could not start: ${step} (${error.code ?? 'error'})`))
      })
      child.once('close', (code, signal) => {
        if (settled) return
        settled = true
        clearTimeout(timer)
        if (code !== 0) reject(new Error(`MySQL step failed: ${step} (exit=${code ?? 'none'}, signal=${signal ?? 'none'})`))
        else resolve(Buffer.concat(stdout).toString('utf8').replace(/\r?\n$/, ''))
      })
      child.stdin.end(sql)
    })
  }

  async verifyEmptySchema() {
    const output = await this.mysql(
      `SELECT VERSION();\nSELECT CONCAT(DEFAULT_CHARACTER_SET_NAME, '\\t', DEFAULT_COLLATION_NAME) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='${this.schema}';\nSELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${this.schema}';\n`,
      'verify empty T13 schema',
    )
    const lines = output.split('\n')
    safeCheck(/^8\.4\./.test(lines[0]), 'MySQL server version is 8.4')
    safeCheck(lines[1] === 'utf8mb4\tutf8mb4_0900_as_cs', 'schema charset and collation are exact')
    safeCheck(lines[2] === '0', 'T13 schema starts empty')
  }

  async verifyMigratedSchema() {
    const output = await this.mysql(
      `SELECT GROUP_CONCAT(CONCAT(version, ':', success) ORDER BY installed_rank SEPARATOR ',') FROM flyway_schema_history;\nSELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${this.schema}' AND TABLE_NAME <> 'flyway_schema_history';\n`,
      'verify V9 migrations',
    )
    safeCheck(output === '1:1,2:1,3:1,4:1,5:1,6:1,7:1,8:1,9:1\n55', 'V1 through V9 and 55 business tables are present')
  }

  async startApplication(version) {
    safeCheck(version === 2 || version === 3, 'fixture integrity version is supported')
    safeCheck(!this.application, 'only one owned JVM is active')
    safeCheck(!(await canConnectToPort()), 'port 8080 is unused before JVM start')
    const runDirectory = await mkdtemp(path.join(tmpdir(), `tensor-integrity-t13-v${version}-`))
    const logPath = path.join(runDirectory, 'application.log')
    const log = await open(logPath, 'wx', 0o600)
    const child = spawn('java', [
      '-jar', process.env.ACCEPTANCE_JAR,
      '--spring.profiles.active=acceptance',
      '--tensor.plugins.fixture.enabled=true',
      `--tensor.plugins.fixture.integrity-version=${version}`,
      '--tensor.plugins.tushare-pro.enabled=true',
      '--server.address=127.0.0.1',
      '--server.port=8080',
    ], {
      cwd: runDirectory,
      env: applicationEnvironment(this.receiver.url),
      shell: false,
      stdio: ['ignore', log.fd, log.fd],
    })
    const current = { child, version, runDirectory, logPath, closed: false, signalled: false, result: null }
    current.closePromise = new Promise(resolve => {
      child.once('error', error => { current.closed = true; current.result = { error }; resolve(current.result) })
      child.once('close', (code, signal) => { current.closed = true; current.result = { code, signal }; resolve(current.result) })
    })
    await log.close()
    this.application = current
    try { await this.waitForHealth(current) }
    catch (error) {
      try { await this.stopApplication() } catch (cleanupError) {
        throw new AggregateError([error, cleanupError], 'Owned JVM startup and cleanup failed')
      }
      throw error
    }
  }

  async waitForHealth(current) {
    const deadline = Date.now() + HEALTH_TIMEOUT_MS
    while (Date.now() < deadline) {
      if (current.closed) throw new Error(`Owned JVM v${current.version} exited before readiness`)
      try {
        const response = await fetch(`${BASE_URL}/actuator/health`, { signal: AbortSignal.timeout(2_000) })
        if (response.status === 200 && (await response.json())?.status === 'UP') return
      } catch { /* readiness retries this owned loopback process only */ }
      await delay(250)
    }
    throw new Error(`Owned JVM v${current.version} was not ready within 90 seconds`)
  }

  async stopApplication() {
    if (!this.application) return null
    const current = this.application
    let failure
    if (!current.closed) {
      current.signalled = true
      if (!current.child.kill('SIGTERM')) failure = new Error(`Could not signal owned JVM v${current.version}`)
    }
    if (!current.closed && !failure) {
      const marker = Symbol('stop-timeout')
      const controller = new AbortController()
      let result
      try {
        result = await Promise.race([current.closePromise, delay(STOP_TIMEOUT_MS, marker, { ref: false, signal: controller.signal })])
      } finally { controller.abort() }
      if (result === marker) {
        current.child.kill('SIGKILL')
        await current.closePromise
        failure = new Error(`Owned JVM v${current.version} did not exit within 150 seconds`)
      }
    }
    let logSafety = false
    try {
      const log = await readFile(current.logPath, 'utf8')
      const forbidden = [process.env.TENSOR_DB_PASSWORD, process.env.TENSOR_TUSHARE_TOKEN]
        .filter(value => typeof value === 'string' && value.length > 0)
      safeCheck(forbidden.every(value => !log.includes(value)), 'application log excludes passwords and tokens')
      logSafety = true
    } catch (error) { failure ??= error }
    this.application = null
    try { await rm(current.runDirectory, { recursive: true, force: true }) }
    catch (error) { failure ??= error }
    try { safeCheck(!(await canConnectToPort()), 'port 8080 is unused after JVM stop') }
    catch (error) { failure ??= error }
    const record = { version: current.version, signal: current.result?.signal ?? null, exitCode: current.result?.code ?? null, logSafety }
    this.lifecycle.push(record)
    if (current.result?.error) failure ??= new Error(`Owned JVM v${current.version} failed`)
    if (!current.signalled && current.closed) failure ??= new Error(`Owned JVM v${current.version} exited unexpectedly`)
    if (failure) throw failure
    return record
  }

  async seedProvenExtra() {
    const rows = []
    for (let day = 1; day <= 19; day += 1) rows.push(day)
    rows.push(21)
    const values = rows.map(day => `('PROVEN_EXTRA','2026-01-${String(day).padStart(2, '0')}',1.230000000000000000,NULL,'fixture','fixture_daily','2026-01-22 00:00:00.000')`)
    await this.mysql(`INSERT INTO fixture__fixture_daily(ts_code,trade_date,amount,note,source_plugin,source_api,ingested_at) VALUES\n${values.join(',\n')};\n`, 'seed synthetic fixture rows')
    safeCheck((await this.fixtureSnapshot()).rows.length === 20, 'fixture seed has 20 rows')
  }

  async fixtureSnapshot() {
    const output = await this.mysql(
      "SET time_zone='+00:00';\nSELECT CONCAT_WS('\\t',ts_code,DATE_FORMAT(trade_date,'%Y-%m-%d'),CAST(amount AS CHAR),IF(note IS NULL,'<NULL>',HEX(note)),source_plugin,source_api,DATE_FORMAT(ingested_at,'%Y-%m-%dT%H:%i:%s.%f')) FROM fixture__fixture_daily ORDER BY ts_code COLLATE utf8mb4_0900_as_cs,trade_date;\n",
      'snapshot fixture securities',
    )
    const rows = output ? output.split('\n') : []
    return { rows, sha256: createHash('sha256').update(`${output}\n`).digest('hex') }
  }

  async coverageSummary(symbol = 'PROVEN_EXTRA') {
    const endDate = { PROVEN_EXTRA: '2026-01-21', PROVEN: '2026-01-20' }[symbol]
    safeCheck(Boolean(endDate), 'coverage SQL symbol is controlled')
    const output = await this.mysql(`WITH RECURSIVE expected(trade_date) AS (
  SELECT DATE('2026-01-01') UNION ALL SELECT DATE_ADD(trade_date, INTERVAL 1 DAY)
  FROM expected WHERE trade_date < DATE('2026-01-20'))
SELECT CONCAT_WS('\\t','SUMMARY',COUNT(*),(SELECT COUNT(*) FROM fixture__fixture_daily WHERE ts_code='${symbol}' AND trade_date BETWEEN '2026-01-01' AND '${endDate}'),COUNT(a.trade_date),COUNT(*)-COUNT(a.trade_date),
  (SELECT COUNT(*) FROM fixture__fixture_daily a2 LEFT JOIN expected e2 ON a2.trade_date=e2.trade_date WHERE a2.ts_code='${symbol}' AND a2.trade_date BETWEEN '2026-01-01' AND '${endDate}' AND e2.trade_date IS NULL),CAST(COUNT(a.trade_date)/COUNT(*) AS DECIMAL(7,6)))
FROM expected e LEFT JOIN fixture__fixture_daily a ON a.ts_code='${symbol}' AND a.trade_date=e.trade_date;
WITH RECURSIVE expected(trade_date) AS (
  SELECT DATE('2026-01-01') UNION ALL SELECT DATE_ADD(trade_date, INTERVAL 1 DAY)
  FROM expected WHERE trade_date < DATE('2026-01-20'))
SELECT CONCAT_WS('\\t','MISSING','${symbol}',DATE_FORMAT(e.trade_date,'%Y-%m-%d')) FROM expected e LEFT JOIN fixture__fixture_daily a ON a.ts_code='${symbol}' AND a.trade_date=e.trade_date WHERE a.trade_date IS NULL ORDER BY e.trade_date;
WITH RECURSIVE expected(trade_date) AS (
  SELECT DATE('2026-01-01') UNION ALL SELECT DATE_ADD(trade_date, INTERVAL 1 DAY)
  FROM expected WHERE trade_date < DATE('2026-01-20'))
SELECT CONCAT_WS('\\t','EXTRA',a.ts_code,DATE_FORMAT(a.trade_date,'%Y-%m-%d')) FROM fixture__fixture_daily a LEFT JOIN expected e ON a.trade_date=e.trade_date WHERE a.ts_code='${symbol}' AND a.trade_date BETWEEN '2026-01-01' AND '${endDate}' AND e.trade_date IS NULL ORDER BY a.trade_date;
`, `recompute ${symbol} fixture coverage`)
    const lines = output.split('\n').filter(Boolean)
    const fields = lines.find(line => line.startsWith('SUMMARY\t'))?.split('\t')
    safeCheck(fields?.length === 7, 'coverage SQL summary is exact')
    return {
      expected: fields[1], actual: fields[2], matched: fields[3], missing: fields[4], extra: fields[5], rate: fields[6],
      missingKeys: lines.filter(line => line.startsWith('MISSING\t')).map(line => line.split('\t').slice(1).join('|')),
      extraKeys: lines.filter(line => line.startsWith('EXTRA\t')).map(line => line.split('\t').slice(1).join('|')),
    }
  }

  async moveExtraToMissing() {
    return this.mysql("UPDATE fixture__fixture_daily SET trade_date='2026-01-20' WHERE ts_code='PROVEN_EXTRA' AND trade_date='2026-01-21';\nSELECT ROW_COUNT();\n", 'move Jan21 key to Jan20')
  }

  async cleanup() {
    const failures = []
    try { await this.stopApplication() } catch (error) { failures.push(error) }
    if (this.receiver) {
      const current = this.receiver
      this.receiver = null
      for (const socket of current.sockets) socket.destroy()
      try { await new Promise((resolve, reject) => current.server.close(error => error ? reject(error) : resolve())) }
      catch (error) { failures.push(error) }
    }
    try { safeCheck(!(await canConnectToPort()), 'port 8080 is unused after cleanup') } catch (error) { failures.push(error) }
    if (failures.length) throw new AggregateError(failures, 'T13 owned runtime cleanup failed')
  }
}

async function startReceiver() {
  const sockets = new Set()
  let environment
  const server = createServer((_request, response) => {
    if (environment) environment.upstreamCalls += 1
    response.writeHead(500, { 'Content-Type': 'application/json' })
    response.end('{"error":"owned receiver"}')
  })
  server.on('connection', socket => { sockets.add(socket); socket.once('close', () => sockets.delete(socket)) })
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve) })
  const address = server.address()
  safeCheck(address && typeof address === 'object', 'owned receiver has a loopback address')
  return {
    receiver: { server, sockets, url: `http://127.0.0.1:${address.port}` },
    bind(value) { environment = value },
  }
}

export async function createIntegrityFixtureEnvironment() {
  const config = await parseEnvironment()
  await mkdir(EVIDENCE_DIRECTORY, { recursive: true })
  const started = await startReceiver()
  const environment = new IntegrityFixtureEnvironment(config, started.receiver)
  started.bind(environment)
  try { await environment.verifyEmptySchema() }
  catch (error) {
    try { await environment.cleanup() } catch (cleanupError) {
      throw new AggregateError([error, cleanupError], 'T13 environment validation and cleanup failed')
    }
    throw error
  }
  return environment
}

export function evidenceFile() { return path.join(EVIDENCE_DIRECTORY, 'browser-evidence.json') }
export function screenshotFile(name) { return path.join(EVIDENCE_DIRECTORY, name) }

export async function sha256File(file) {
  return createHash('sha256').update(await readFile(file)).digest('hex')
}

export async function writeSafeEvidence(file, value) {
  await mkdir(EVIDENCE_DIRECTORY, { recursive: true })
  const text = `${JSON.stringify(value, null, 2)}\n`
  const forbidden = [process.env.TENSOR_DB_PASSWORD, process.env.TENSOR_DB_USERNAME,
    process.env.TENSOR_DB_URL, process.env.M14_DB_SCHEMA, process.env.M14_MYSQL_DEFAULTS_FILE]
    .filter(item => typeof item === 'string' && item.length > 0)
  safeCheck(!/jdbc:mysql/i.test(text) && forbidden.every(item => !text.includes(item)), 'evidence excludes database connection values')
  await writeFile(file, text, { encoding: 'utf8', mode: 0o600 })
}
