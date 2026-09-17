import { expect, test } from '@playwright/test'
import {
  createIntegrityFixtureEnvironment,
  evidenceFile,
  screenshotFile,
  sha256File,
  writeSafeEvidence,
} from './integrity-fixture.helpers.js'

const BASE_URL = 'http://127.0.0.1:8080'
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
const results = page => page.getByRole('region', { name: '检查结果', exact: true })
const issues = page => page.getByRole('region', { name: '问题明细', exact: true })
const summary = page => page.getByRole('region', { name: '报告总览', exact: true })
const confirm = page => page.getByRole('checkbox', { name: '我已确认以上检查口径' })
const start = page => page.getByRole('button', { name: '开始检查', exact: true })

let environment
const evidence = {
  version: 1,
  task: 'DATA-INTEGRITY-T13',
  startedAt: undefined,
  finishedAt: undefined,
  jarSha256: undefined,
  checks: [],
  sql: {},
  securities: [],
  screenshots: [],
  lifecycle: [],
  counters: {},
  cleanup: {},
}

function monitorPage(page) {
  const failures = []
  const posts = []
  page.on('pageerror', error => failures.push(`pageerror: ${error.message}`))
  page.on('request', request => {
    const url = new URL(request.url())
    if (url.origin !== BASE_URL) failures.push(`external request: ${url.origin}`)
    if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method())) {
      if (request.method() === 'POST' && url.pathname === '/api/v1/integrity-checks') {
        posts.push(request.postDataJSON())
      } else {
        failures.push(`unexpected write: ${request.method()} ${url.pathname}`)
      }
    }
  })
  page.on('requestfailed', request => {
    const url = new URL(request.url())
    if (url.pathname.startsWith('/api/v1/')) failures.push(`failed API request: ${url.pathname}`)
  })
  page.on('response', response => {
    const url = new URL(response.url())
    if (url.pathname.startsWith('/api/v1/') && response.status() >= 400) {
      failures.push(`business HTTP ${response.status()}: ${url.pathname}`)
    }
  })
  return {
    posts,
    assertClean(expectedPosts) {
      expect(posts).toHaveLength(expectedPosts)
      expect(failures).toEqual([])
    },
  }
}

async function noDocumentOverflow(page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
}

async function chooseFixture(page) {
  await page.goto('/integrity')
  await expect(page.getByRole('heading', { name: '数据完整性', exact: true })).toBeVisible()
  await page.getByLabel('数据源', { exact: true }).selectOption('fixture')
  await expect(page.getByRole('checkbox', { name: /fixture_daily/ })).toBeChecked()
}

async function waitForReport(page, conclusion) {
  await expect(page.getByText('计算已完成', { exact: true })).toBeVisible({ timeout: 60_000 })
  await expect(summary(page).getByText(`数据结论：${conclusion}`, { exact: true })).toBeVisible()
  await expect(results(page).getByRole('button', { name: '查看问题', exact: true })).toBeVisible()
}

async function submitCheck(page, symbol, startDate, endDate, conclusion, { copied = false } = {}) {
  if (!copied) {
    await chooseFixture(page)
    await page.getByLabel('股票代码', { exact: true }).fill(symbol)
    await page.getByRole('button', { name: '添加', exact: true }).click()
    await page.getByLabel('开始日期', { exact: true }).fill(startDate)
    await page.getByLabel('结束日期', { exact: true }).fill(endDate)
  }
  await expect(confirm(page)).not.toBeChecked()
  await expect(start(page)).toBeDisabled()
  const before = await environment.fixtureSnapshot()
  await confirm(page).focus()
  await page.keyboard.press('Space')
  await expect(confirm(page)).toBeChecked()
  const responsePromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return response.request().method() === 'POST' && url.pathname === '/api/v1/integrity-checks'
  })
  await start(page).focus()
  await page.keyboard.press('Enter')
  const response = await responsePromise
  expect(response.status()).toBe(202)
  const body = await response.request().postDataJSON()
  expect(body).toMatchObject({
    submissionId: expect.stringMatching(UUID),
    pluginId: 'fixture',
    symbols: [symbol],
    startDate,
    endDate,
    apiNames: ['fixture_daily'],
    capabilityHash: expect.stringMatching(/^[a-f0-9]{64}$/),
  })
  const receipt = await response.json()
  expect(receipt).toMatchObject({
    checkId: expect.stringMatching(UUID),
    submissionId: body.submissionId,
    pluginId: 'fixture',
    status: 'QUEUED',
    plannedUnits: 1,
  })
  expect(response.headers().location).toBe(`/api/v1/integrity-checks/${receipt.checkId}`)
  await expect(page).toHaveURL(`/integrity/checks/${receipt.checkId}`)
  await waitForReport(page, conclusion)
  const after = await environment.fixtureSnapshot()
  expect(after).toEqual(before)
  evidence.securities.push({ checkId: receipt.checkId, before, after })
  evidence.checks.push({
    symbol,
    startDate,
    endDate,
    checkId: receipt.checkId,
    submissionId: body.submissionId,
    capabilityHash: body.capabilityHash,
    conclusion,
  })
  return { ...receipt, request: body }
}

async function assertInitialReport(page) {
  await expect(results(page)).toContainText('实际 20')
  await expect(results(page)).toContainText('预期 20')
  await expect(results(page)).toContainText('命中 19')
  await expect(results(page)).toContainText('覆盖率 95%')
  await expect(results(page)).toContainText('确认缺失 1')
  await expect(results(page)).toContainText('额外 1')
  await results(page).getByRole('button', { name: '查看问题', exact: true }).click()
  const rows = issues(page).locator('tbody tr')
  await expect(rows).toHaveCount(2)
  await assertIssueRow(page, rows, 'MISSING', '2026-01-20')
  await assertIssueRow(page, rows, 'EXTRA', '2026-01-21')
  await issues(page).getByLabel('问题开始日期', { exact: true }).fill('2026-01-20')
  await issues(page).getByLabel('问题结束日期', { exact: true }).fill('2026-01-20')
  await issues(page).getByRole('button', { name: '查询问题', exact: true }).click()
  await expect(rows).toHaveCount(1)
  await assertIssueRow(page, rows, 'MISSING', '2026-01-20')
  await issues(page).getByRole('button', { name: '关闭问题明细', exact: true }).click()
}

async function assertIssueRow(page, rows, type, tradeDate) {
  const row = rows.filter({ has: page.getByText(type, { exact: true }) })
  await expect(row).toHaveCount(1)
  await expect(row.locator('td').nth(0).getByText(type, { exact: true })).toBeVisible()
  await expect(row.locator('td').nth(1).getByText(tradeDate, { exact: true })).toBeVisible()
  const key = name => row.locator('.business-key div').filter({ has: page.getByText(name, { exact: true }) })
  await expect(key('ts_code').locator('dt')).toHaveText('ts_code')
  await expect(key('ts_code').locator('dd')).toHaveText('PROVEN_EXTRA')
  await expect(key('trade_date').locator('dt')).toHaveText('trade_date')
  await expect(key('trade_date').locator('dd')).toHaveText(tradeDate)
}

async function recordResponsiveReport(page, checkId, width) {
  await page.setViewportSize({ width, height: 1000 })
  await page.goto(`/integrity/checks/${checkId}`)
  await page.reload()
  await waitForReport(page, '有问题')
  await expect(results(page)).toContainText('覆盖率 95%')
  const open = results(page).getByRole('button', { name: '查看问题', exact: true })
  await open.focus()
  await page.keyboard.press('Enter')
  await expect(issues(page)).toBeVisible()
  await expect(issues(page).getByRole('heading', { name: '问题明细', exact: true })).toBeFocused()
  await expect(issues(page)).toContainText('2026-01-20')
  await noDocumentOverflow(page)
  const file = screenshotFile(`report-${width}.png`)
  await page.screenshot({ path: file, fullPage: true })
  evidence.screenshots.push({ width, path: `docs/verification/data-integrity-t13/real/report-${width}.png`, sha256: await sha256File(file) })
}

test.use({ viewport: { width: 1440, height: 1000 }, trace: 'off', video: 'off', screenshot: 'off' })

test.describe('real fixture integrity closure', () => {
  test.describe.configure({ mode: 'serial', retries: 0, timeout: 900_000 })

  test.beforeAll(async () => {
    evidence.startedAt = new Date().toISOString()
    environment = await createIntegrityFixtureEnvironment()
    evidence.jarSha256 = environment.jarSha256
    try {
      await environment.startApplication(2)
      await environment.verifyMigratedSchema()
      await environment.seedProvenExtra()
    } catch (error) {
      try { await environment.cleanup() } catch (cleanupError) {
        throw new AggregateError([error, cleanupError], 'T13 setup and cleanup failed')
      }
      throw error
    }
  })

  test.afterAll(async () => {
    const failures = []
    if (environment) {
      try {
        await environment.cleanup()
        evidence.cleanup = { jvmStopped: true, receiverStopped: true, port8080Free: true }
      } catch (error) { failures.push(error) }
      finally {
        try { evidence.lifecycle.push(...environment.lifecycle) } catch (error) { failures.push(error) }
      }
      evidence.counters.upstreamCalls = environment.upstreamCalls
      try { expect(environment.upstreamCalls).toBe(0) } catch (error) { failures.push(error) }
    }
    evidence.finishedAt = new Date().toISOString()
    try { await writeSafeEvidence(evidenceFile(), evidence) } catch (error) { failures.push(error) }
    if (failures.length) throw new AggregateError(failures, 'T13 final verification failed')
  })

  test('proves exact sets, immutable history, version upgrade, and responsive report', async ({ page }) => {
    const monitor = monitorPage(page)
    const checkA = await submitCheck(page, 'PROVEN_EXTRA', '2026-01-01', '2026-01-21', '有问题')
    await assertInitialReport(page)
    evidence.sql.beforeUpdate = await environment.coverageSummary()
    expect(evidence.sql.beforeUpdate).toEqual({
      expected: '20', actual: '20', matched: '19', missing: '1', extra: '1',
      rate: '0.950000', missingKeys: ['PROVEN_EXTRA|2026-01-20'], extraKeys: ['PROVEN_EXTRA|2026-01-21'],
    })

    await page.reload()
    await waitForReport(page, '有问题')
    await expect(results(page)).toContainText('覆盖率 95%')
    await page.getByRole('link', { name: '返回数据完整性', exact: true }).click()
    const history = page.getByRole('region', { name: '检查历史', exact: true })
    await history.getByRole('button', { name: '刷新历史', exact: true }).click()
    await expect(history.locator(`a[href="/integrity/checks/${checkA.checkId}"]`)).toBeVisible()

    expect(await environment.moveExtraToMissing()).toBe('1')
    evidence.sql.afterUpdate = await environment.coverageSummary()
    expect(evidence.sql.afterUpdate).toEqual({
      expected: '20', actual: '20', matched: '20', missing: '0', extra: '0',
      rate: '1.000000', missingKeys: [], extraKeys: [],
    })
    await page.goto(`/integrity/checks/${checkA.checkId}`)
    await page.getByRole('button', { name: '再次检查', exact: true }).click()
    await expect(page.getByText('已复制原报告的固定执行范围', { exact: true })).toBeVisible()
    const checkB = await submitCheck(page, 'PROVEN_EXTRA', '2026-01-01', '2026-01-21', '通过', { copied: true })
    expect(checkB.checkId).not.toBe(checkA.checkId)
    expect(checkB.submissionId).not.toBe(checkA.submissionId)
    await expect(results(page)).toContainText('实际 20')
    await expect(results(page)).toContainText('预期 20')
    await expect(results(page)).toContainText('命中 20')
    await expect(results(page)).toContainText('覆盖率 100%')
    await page.goto(`/integrity/checks/${checkA.checkId}`)
    await waitForReport(page, '有问题')
    await expect(results(page)).toContainText('覆盖率 95%')

    const proven = await submitCheck(page, 'PROVEN', '2026-01-01', '2026-01-20', '有问题')
    await expect(results(page)).toContainText('实际 0')
    await expect(results(page)).toContainText('预期 20')
    await expect(results(page)).toContainText('确认缺失 20')
    await expect(results(page)).toContainText('覆盖率 0%')
    const provenMissingKeys = Array.from({ length: 20 }, (_, index) =>
      `PROVEN|2026-01-${String(index + 1).padStart(2, '0')}`)
    evidence.sql.proven = await environment.coverageSummary('PROVEN')
    expect(evidence.sql.proven).toEqual({
      expected: '20', actual: '0', matched: '0', missing: '20', extra: '0',
      rate: '0.000000', missingKeys: provenMissingKeys, extraKeys: [],
    })
    const empty = await submitCheck(page, 'PROVEN_EMPTY', '2026-01-01', '2026-01-21', '通过')
    await expect(results(page)).toContainText('实际 0')
    await expect(results(page)).toContainText('预期 0')
    await expect(results(page)).toContainText('覆盖率 无法计算')
    const unknown = await submitCheck(page, 'UNCONFIRMED', '2026-01-01', '2026-01-20', '无法判定')
    await expect(results(page)).toContainText('预期 无法计算')
    await expect(results(page)).toContainText('命中 无法计算')
    await expect(results(page)).toContainText('疑似缺口 20')
    await expect(results(page)).toContainText('覆盖率 无法计算')
    evidence.scenarios = { proven: proven.checkId, provenEmpty: empty.checkId, unconfirmed: unknown.checkId }

    await environment.stopApplication()
    expect(environment.upstreamCalls).toBe(0)
    await environment.startApplication(3)
    await environment.verifyMigratedSchema()
    await page.goto(`/integrity/checks/${checkA.checkId}`)
    await waitForReport(page, '有问题')
    await expect(results(page)).toContainText('覆盖率 95%')
    await results(page).locator('summary').filter({ hasText: '保存的检查依据' }).click()
    await expect(results(page)).toContainText('fixture.coverage.fixture_daily@2')
    await expect(results(page)).not.toContainText('fixture.acceptance.extension')
    await page.getByRole('button', { name: '再次检查', exact: true }).click()
    const checkC = await submitCheck(page, 'PROVEN_EXTRA', '2026-01-01', '2026-01-21', '通过', { copied: true })
    expect(checkC.request.capabilityHash).not.toBe(checkA.request.capabilityHash)
    await expect(results(page)).toContainText('覆盖率 100%')
    await results(page).locator('summary').filter({ hasText: '保存的检查依据' }).click()
    await expect(results(page)).toContainText('fixture.coverage.fixture_daily@3')
    await expect(results(page)).toContainText('Fixture 验收扩展')
    await expect(results(page)).toContainText('fixture.acceptance.extension@1')
    await expect(results(page)).toContainText('FIXTURE_EXTENSION_VERIFIED')
    await expect(results(page)).toContainText('验收扩展规则已执行')

    for (const width of [1440, 1024, 390]) await recordResponsiveReport(page, checkA.checkId, width)
    expect(environment.upstreamCalls).toBe(0)
    monitor.assertClean(6)
  })
})
