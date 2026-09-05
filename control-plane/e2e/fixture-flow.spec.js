import { expect, test } from '@playwright/test'
import { spawn } from 'node:child_process'
import { open, mkdtemp, stat, writeFile } from 'node:fs/promises'
import { createConnection } from 'node:net'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { setTimeout as delay } from 'node:timers/promises'

const BASE_URL = 'http://127.0.0.1:8080'
const HEALTH_TIMEOUT_MS = 90_000
const STOP_TIMEOUT_MS = 150_000
const FIXTURE_API = /Fixture 日线.*fixture_daily/
const DB_VARIABLES = [
  'TENSOR_DB_URL',
  'TENSOR_DB_USERNAME',
  'TENSOR_DB_PASSWORD',
]

let runtime
let tushareSummary
let savedRow
let downloadPostCount = 0

function processEnvironment() {
  const env = Object.fromEntries(
    Object.entries(process.env).filter(
      ([name]) => !/^(TENSOR_|SPRING_|SERVER_)/.test(name),
    ),
  )
  for (const name of DB_VARIABLES) env[name] = process.env[name]
  return env
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
      if (response.status === 200) {
        const body = await response.json()
        if (body?.status === 'UP') return
      }
      lastFailure = `last health status was ${response.status}`
    } catch (error) {
      lastFailure = error instanceof Error ? error.message : String(error)
    }
    await delay(250)
  }

  throw new Error(`Owned JVM was not ready within 90 seconds: ${lastFailure}`)
}

async function startApplication(fixtureEnabled) {
  const runDirectory = await mkdtemp(path.join(tmpdir(), 'tensor-m14-t01-'))
  const log = await open(path.join(runDirectory, 'application.log'), 'wx', 0o600)
  let child
  try {
    child = spawn(
      'java',
      [
        '-jar',
        process.env.ACCEPTANCE_JAR,
        '--spring.profiles.active=acceptance',
        `--tensor.plugins.fixture.enabled=${fixtureEnabled}`,
        '--server.address=127.0.0.1',
        '--server.port=8080',
      ],
      {
        cwd: runDirectory,
        env: processEnvironment(),
        shell: false,
        stdio: ['ignore', log.fd, log.fd],
      },
    )
  } catch (error) {
    await log.close()
    throw error
  }
  const current = {
    child,
    closed: false,
    signalled: false,
    closeResult: undefined,
  }
  current.closePromise = new Promise((resolve) => {
    child.once('error', (error) => {
      current.closed = true
      current.closeResult = { error }
      resolve(current.closeResult)
    })
    child.once('close', (code, signal) => {
      current.closed = true
      current.closeResult = { code, signal }
      resolve(current.closeResult)
    })
  })
  await log.close()
  runtime = current
  await waitForHealth(current)
  return current
}

async function stopApplication() {
  if (!runtime) return
  const current = runtime
  if (!current.closed && !current.signalled) {
    current.signalled = true
    if (!current.child.kill('SIGTERM')) {
      throw new Error(`Could not signal owned JVM pid=${current.child.pid}`)
    }
  }

  if (!current.closed) {
    const timeout = Symbol('stop-timeout')
    const timerController = new AbortController()
    let result
    try {
      result = await Promise.race([
        current.closePromise,
        delay(STOP_TIMEOUT_MS, timeout, {
          ref: false,
          signal: timerController.signal,
        }),
      ])
    } finally {
      timerController.abort()
    }
    if (result === timeout) {
      throw new Error(
        `Owned JVM pid=${current.child.pid} did not exit within 150 seconds`,
      )
    }
  }
  runtime = undefined
  await expect.poll(canConnectToPort, { timeout: 2_000 }).toBe(false)
}

async function readDataSources() {
  const response = await fetch(`${BASE_URL}/api/v1/data-sources`, {
    signal: AbortSignal.timeout(2_000),
  })
  expect(response.status).toBe(200)
  return response.json()
}

function monitorPage(page, { allowTushareUnavailable = false } = {}) {
  const failures = []
  const writes = []

  page.on('pageerror', (error) => failures.push(`pageerror: ${error.message}`))
  page.on('request', (request) => {
    const url = new URL(request.url())
    if (url.origin !== BASE_URL) failures.push(`external request: ${url.origin}`)
    if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method())) {
      writes.push(`${request.method()} ${url.pathname}`)
      if (request.method() === 'POST' && url.pathname === '/api/v1/downloads') {
        downloadPostCount += 1
      } else {
        failures.push(`unexpected write: ${request.method()} ${url.pathname}`)
      }
    }
  })
  page.on('requestfailed', (request) => {
    const url = new URL(request.url())
    if (url.pathname.startsWith('/api/v1/')) {
      failures.push(`failed business request: ${url.pathname}`)
    }
  })
  page.on('response', (response) => {
    const url = new URL(response.url())
    const allowed409 =
      allowTushareUnavailable &&
      response.status() === 409 &&
      url.pathname === '/api/v1/data-sources/tushare_pro/apis'
    if (
      url.pathname.startsWith('/api/v1/') &&
      response.status() >= 400 &&
      !allowed409
    ) {
      failures.push(`business HTTP ${response.status()}: ${url.pathname}`)
    }
  })

  return {
    assertClean(expectedWrites = []) {
      expect(writes).toEqual(expectedWrites)
      expect(failures).toEqual([])
    },
  }
}

async function openAndRefresh(page, route, heading) {
  const navigation = await page.goto(route)
  expect(navigation?.status()).toBe(200)
  await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible()
  const refresh = await page.reload()
  expect(refresh?.status()).toBe(200)
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

async function chooseFixtureDataset(page) {
  await selectOption(page, '数据源', 'Fixture')
  await selectOption(page, '数据集', FIXTURE_API)
}

async function chooseFixtureDownload(page) {
  await selectOption(page, '数据源', 'Fixture')
  await selectOption(page, '数据接口', FIXTURE_API)
}

function recordsResponse(response) {
  const url = new URL(response.url())
  return (
    response.request().method() === 'GET' &&
    url.pathname === '/api/v1/data-sources/fixture/datasets/fixture_daily/records'
  )
}

async function queryByCode(page) {
  await page.getByLabel('证券代码 (ts_code)').fill('000001.SZ')
  const responsePromise = page.waitForResponse(recordsResponse)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  const response = await responsePromise
  expect(response.status()).toBe(200)
  const url = new URL(response.url())
  expect(url.searchParams.get('tsCode')).toBe('000001.SZ')
  expect(url.searchParams.get('page') ?? '1').toBe('1')
  expect(url.searchParams.get('pageSize') ?? '50').toBe('50')
  return response.json()
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

async function assertFixtureRow(page, body) {
  expect(body).toMatchObject({
    pluginId: 'fixture',
    apiName: 'fixture_daily',
    page: 1,
    pageSize: 50,
    totalElements: 1,
    totalPages: 1,
  })
  expect(body.columns).toEqual([
    'ts_code',
    'trade_date',
    'amount',
    'note',
    'source_plugin',
    'source_api',
    'ingested_at',
  ])
  expect(body.items).toHaveLength(1)
  const rowData = body.items[0]
  expect(rowData).toEqual({
    ts_code: '000001.SZ',
    trade_date: '2026-08-07',
    amount: '11.230000000000000000',
    note: null,
    source_plugin: 'fixture',
    source_api: 'fixture_daily',
    ingested_at: rowData.ingested_at,
  })
  const displayedTimestamp = shanghaiTimestamp(rowData.ingested_at)

  await expect(page.getByRole('columnheader')).toHaveText(body.columns)
  const row = page
    .getByRole('row')
    .filter({ has: page.getByRole('cell', { name: '000001.SZ', exact: true }) })
  await expect(row).toHaveCount(1)
  await expect(row.getByRole('cell')).toHaveText([
    '000001.SZ',
    '2026-08-07',
    '11.230000000000000000',
    '--',
    'fixture',
    'fixture_daily',
    displayedTimestamp,
  ])
  await row.getByRole('cell', { name: displayedTimestamp, exact: true }).scrollIntoViewIfNeeded()
  return { rowData, row }
}

async function assertDisabledPage(page, route, heading, screenshotPath) {
  await openAndRefresh(page, route, heading)
  const source = page.getByRole('combobox', { name: '数据源', exact: true })
  await source.focus()
  await source.press('Enter')
  await expect(page.getByRole('option')).toHaveText(['Tushare Pro'])
  await expect(page.getByRole('option', { name: 'Fixture' })).toHaveCount(0)
  await page.screenshot({ path: screenshotPath, fullPage: true })
  await source.press('Escape')
}

test.describe('fixture page flow', () => {
  test.describe.configure({ mode: 'serial', retries: 0, timeout: 180_000 })
  test.use({ viewport: { width: 1440, height: 1000 } })

  test.beforeAll(async () => {
    test.setTimeout(180_000)
    expect(path.isAbsolute(process.env.ACCEPTANCE_JAR ?? '')).toBe(true)
    expect((await stat(process.env.ACCEPTANCE_JAR)).isFile()).toBe(true)
    for (const name of DB_VARIABLES) expect(process.env[name]?.length).toBeGreaterThan(0)
    expect(process.env.PLAYWRIGHT_BASE_URL ?? BASE_URL).toBe(BASE_URL)
    await requireFreePort()
    try {
      await startApplication(true)
      const sources = await readDataSources()
      tushareSummary = sources.find(({ pluginId }) => pluginId === 'tushare_pro')
      expect(tushareSummary).toBeDefined()
    } catch (error) {
      await stopApplication()
      throw error
    }
  })

  test.afterAll(async () => {
    test.setTimeout(180_000)
    await stopApplication()
  })

  test('downloadsSuccessAndQueriesFixtureFromPages', async ({ page }, testInfo) => {
    const monitor = monitorPage(page)
    await openAndRefresh(page, '/downloads', '数据下载')
    await openAndRefresh(page, '/datasets', '数据查看')

    await chooseFixtureDataset(page)
    const emptyBody = await queryByCode(page)
    expect(emptyBody).toMatchObject({
      totalElements: 0,
      totalPages: 0,
      items: [],
    })
    await expect(
      page.getByRole('heading', { name: '未找到符合条件的数据' }),
    ).toBeVisible()

    await page.getByRole('link', { name: '数据下载', exact: true }).click()
    await expect(page.getByRole('heading', { level: 1, name: '数据下载' })).toBeVisible()
    await chooseFixtureDownload(page)
    const scenario = page.getByRole('combobox', { name: /场景/ })
    await scenario.focus()
    await scenario.press('Enter')
    await expect(
      page.getByRole('option', { name: 'SUCCESS', exact: true, selected: true }),
    ).toBeVisible()
    await scenario.press('Escape')

    const responsePromise = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return response.request().method() === 'POST' && url.pathname === '/api/v1/downloads'
    })
    await page.getByRole('button', { name: '开始下载', exact: true }).click()
    const response = await responsePromise
    expect(response.status()).toBe(200)
    expect(await response.request().postDataJSON()).toEqual({
      pluginId: 'fixture',
      apiName: 'fixture_daily',
      params: { scenario: 'SUCCESS' },
    })
    const result = await response.json()
    expect(result).toMatchObject({
      outcome: 'SUCCESS',
      pluginId: 'fixture',
      apiName: 'fixture_daily',
      sourceRowCount: 1,
      insertedRows: 1,
      updatedRows: 0,
    })
    expect(result.requestId).toBeTruthy()
    expect(response.headers()['x-request-id']).toBe(result.requestId)
    const status = page.getByRole('status')
    await expect(status.getByRole('heading', { name: '下载成功' })).toBeVisible()
    await expect(status.getByRole('term')).toHaveText(['上游返回数', '插入数', '更新数'])
    await expect(status.getByRole('definition')).toHaveText(['1', '1', '0'])
    await status.screenshot({ path: testInfo.outputPath('success-counts.png') })

    await page.getByRole('link', { name: '数据查看', exact: true }).click()
    await expect(page.getByRole('heading', { level: 1, name: '数据查看' })).toBeVisible()
    await chooseFixtureDataset(page)
    const pageBody = await queryByCode(page)
    const { rowData, row } = await assertFixtureRow(page, pageBody)
    savedRow = structuredClone(rowData)
    await row.screenshot({ path: testInfo.outputPath('success-row.png') })
    monitor.assertClean(['POST /api/v1/downloads'])
    expect(downloadPostCount).toBe(1)
  })

  test('showsEmptyDownloadWithoutAddingRows', async ({ page }, testInfo) => {
    const monitor = monitorPage(page)
    await openAndRefresh(page, '/downloads', '数据下载')
    await chooseFixtureDownload(page)
    await selectOption(page, /场景/, 'EMPTY')

    const responsePromise = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return response.request().method() === 'POST' && url.pathname === '/api/v1/downloads'
    })
    await page.getByRole('button', { name: '开始下载', exact: true }).click()
    const response = await responsePromise
    expect(response.status()).toBe(200)
    expect(await response.request().postDataJSON()).toEqual({
      pluginId: 'fixture',
      apiName: 'fixture_daily',
      params: { scenario: 'EMPTY' },
    })
    expect(await response.json()).toMatchObject({
      outcome: 'EMPTY',
      pluginId: 'fixture',
      apiName: 'fixture_daily',
      sourceRowCount: 0,
      insertedRows: 0,
      updatedRows: 0,
    })
    await expect(page.getByRole('heading', { name: '下载成功，0 条数据' })).toBeVisible()
    await expect(page.getByText('本次请求没有可写入的数据。')).toBeVisible()
    await expect(page.getByText('下载失败')).toHaveCount(0)
    await page.getByRole('status').screenshot({ path: testInfo.outputPath('empty-result.png') })

    await page.getByRole('link', { name: '数据查看', exact: true }).click()
    await chooseFixtureDataset(page)
    const pageBody = await queryByCode(page)
    const { rowData } = await assertFixtureRow(page, pageBody)
    expect(rowData).toEqual(savedRow)
    monitor.assertClean(['POST /api/v1/downloads'])
    expect(downloadPostCount).toBe(2)
  })

  test('hidesDisabledFixtureOnBothPagesAfterRestart', async ({ page }, testInfo) => {
    test.setTimeout(360_000)
    await stopApplication()
    await requireFreePort()
    await startApplication(false)

    const disabledSources = await readDataSources()
    expect(disabledSources).toEqual([tushareSummary])
    await writeFile(
      testInfo.outputPath('flow-evidence.json'),
      `${JSON.stringify({
        savedRow,
        enabledTushareSummary: tushareSummary,
        disabledSources,
      }, null, 2)}\n`,
      { encoding: 'utf8', flag: 'wx', mode: 0o600 },
    )
    const monitor = monitorPage(page, { allowTushareUnavailable: true })
    await assertDisabledPage(
      page,
      '/downloads',
      '数据下载',
      testInfo.outputPath('disabled-downloads.png'),
    )
    await assertDisabledPage(
      page,
      '/datasets',
      '数据查看',
      testInfo.outputPath('disabled-datasets.png'),
    )
    monitor.assertClean([])
    expect(downloadPostCount).toBe(2)
  })
})
