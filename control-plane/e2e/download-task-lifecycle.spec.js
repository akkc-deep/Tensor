import { expect, test } from '@playwright/test'

const LIVE = process.env.TENSOR_TASK_LIVE_E2E
const SCENARIO = process.env.TENSOR_TASK_LIFECYCLE_SCENARIO
const BASE_URL = process.env.PLAYWRIGHT_BASE_URL
const RESUME_TASK_ID = process.env.TENSOR_TASK_LIFECYCLE_TASK_ID
const CONTROL = '/__test/download-task-lifecycle'
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

if (LIVE !== '1') throw new Error('TENSOR_TASK_LIVE_E2E=1 is required')
if (!['flow', 'resume'].includes(SCENARIO)) throw new Error('TENSOR_TASK_LIFECYCLE_SCENARIO must be flow or resume')
if (!/^http:\/\/127\.0\.0\.1:\d+$/.test(BASE_URL ?? '')) throw new Error('PLAYWRIGHT_BASE_URL must be an explicit 127.0.0.1 URL')
if (SCENARIO === 'resume' && !UUID.test(RESUME_TASK_ID ?? '')) throw new Error('TENSOR_TASK_LIFECYCLE_TASK_ID must be a UUID for resume')

test.describe.configure({ mode: 'serial', retries: 0, timeout: 90_000 })

function observePageErrors(page, errors) {
  page.on('pageerror', error => errors.push(error.message))
  page.on('request', request => {
    if (new URL(request.url()).origin !== new URL(BASE_URL).origin) errors.push('external request')
  })
}

async function control(request, method, path, data) {
  const response = await request.fetch(`${CONTROL}${path}`, { method, data })
  expect(response.status(), `${method} ${path}: ${await response.text()}`).toBe(200)
  return response.json()
}

async function snapshot(request) {
  return control(request, 'GET', '/snapshot')
}

async function taskDetail(request, taskId) {
  const response = await request.get(`/api/v1/download-tasks/${taskId}`)
  expect(response.status()).toBe(200)
  return response.json()
}

async function batches(request, taskId) {
  const response = await request.get(`/api/v1/download-tasks/${taskId}/batches?page=1&pageSize=100&includeSplit=false`)
  expect(response.status()).toBe(200)
  return (await response.json()).items
}

async function release(request, date) {
  await control(request, 'POST', '/release', { date })
}

async function chooseLifecycleRange(page, start, end) {
  await page.goto('/downloads')
  await page.locator('.data-source-select .el-select').click()
  await page.getByRole('option', { name: 'HTTP lifecycle test' }).click()
  const api = page.locator('#download-api')
  await page.locator('.api-select .el-select').click()
  await api.fill('prices')
  const listboxId = await api.getAttribute('aria-controls')
  const option = page.locator(`#${listboxId}`).getByRole('option')
    .filter({ has: page.getByText('prices', { exact: true }) })
  await expect(option).toHaveCount(1)
  await option.click()
  await page.getByLabel('下载模式').getByText('日期区间', { exact: true }).click()
  for (const [name, value] of [['start_date', start], ['end_date', end]]) {
    const input = page.locator(`[data-parameter="${name}"] input`)
    await input.fill(value)
    await input.press('Enter')
    await input.blur()
    await expect(input).toHaveValue(value)
  }
}

async function submit(page) {
  let count = 0
  page.on('request', request => {
    if (request.method() === 'POST' && new URL(request.url()).pathname === '/api/v1/download-tasks') count++
  })
  const response = page.waitForResponse(response =>
    response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/v1/download-tasks')
  await page.getByRole('button', { name: '提交任务', exact: true }).click()
  const accepted = await response
  expect(accepted.status()).toBe(202)
  const receipt = await accepted.json()
  expect(receipt.taskId).toMatch(UUID)
  await expect(page.getByRole('heading', { name: '任务已接收' })).toBeVisible()
  return { taskId: receipt.taskId, submissions: () => count }
}

async function expectNoOverflow(page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true)
}

if (SCENARIO === 'flow') {
  test('liveDisconnectReloadAndReopen', async ({ browser, request }) => {
    await control(request, 'POST', '/scenario', { scenario: 'disconnect' })
    const pageErrors = []
    const firstContext = await browser.newContext({ viewport: { width: 1440, height: 1080 } })
    const page = await firstContext.newPage()
    observePageErrors(page, pageErrors)
    await chooseLifecycleRange(page, '2026-09-01', '2026-09-03')
    const submitted = await submit(page)
    await page.getByLabel('任务接收').getByRole('link', { name: '查看任务', exact: true }).click()
    await expect(page).toHaveURL(`/downloads/tasks/${submitted.taskId}`)
    await page.reload()
    await expect.poll(async () => (await snapshot(request)).calls['2026-09-01']).toBe(1)
    expect((await snapshot(request)).committedKeys).toEqual([])

    await firstContext.close()
    expect(firstContext.pages()).toHaveLength(0)
    await release(request, '2026-09-01')
    await expect.poll(async () => (await snapshot(request)).calls['2026-09-02']).toBe(1)
    await expect.poll(async () => (await snapshot(request)).committedKeys).toEqual(['20260901'])
    await release(request, '2026-09-02')
    await expect.poll(async () => (await snapshot(request)).calls['2026-09-03']).toBe(1)
    await release(request, '2026-09-03')
    await expect.poll(async () => (await taskDetail(request, submitted.taskId)).status).toBe('SUCCEEDED')

    const secondContext = await browser.newContext({ viewport: { width: 1440, height: 1080 } })
    const reopened = await secondContext.newPage()
    observePageErrors(reopened, pageErrors)
    await reopened.goto('/downloads')
    const recentLink = reopened.locator(`.download-task-list a[href="/downloads/tasks/${submitted.taskId}"]`)
    await expect(recentLink).toBeVisible()
    const row = recentLink.locator('xpath=ancestor::tr')
    await expect(row).toContainText('已成功')
    await recentLink.click()
    await expect(reopened).toHaveURL(`/downloads/tasks/${submitted.taskId}`)
    await expect(reopened.locator('[data-task-status]')).toHaveText('已成功')
    await reopened.reload()
    await expect(reopened.locator('[data-task-status]')).toHaveText('已成功')
    const finalRows = reopened.locator('.download-batch-table tbody tr')
    await expect(finalRows).toHaveCount(3)
    for (const date of ['2026-09-01', '2026-09-02', '2026-09-03']) {
      const batchRow = finalRows.filter({
        has: reopened.getByText(`${date} 至 ${date}`, { exact: true }),
      })
      await expect(batchRow).toHaveCount(1)
      await expect(batchRow.getByText('已成功', { exact: true })).toBeVisible()
    }
    expect(await snapshot(request)).toMatchObject({
      calls: { '2026-09-01': 1, '2026-09-02': 1, '2026-09-03': 1 },
      committedKeys: ['20260901', '20260902', '20260903'],
      rowCount: 3,
      inFlight: false,
    })
    expect(submitted.submissions()).toBe(1)
    await expectNoOverflow(reopened)
    await reopened.screenshot({ path: '/tmp/issue018-t12-lifecycle-desktop.png', fullPage: true })
    await secondContext.close()
    expect(pageErrors).toEqual([])
  })

  test('livePartialFailureRetriesOnlyTheMiddleBatch', async ({ page, request }) => {
    await control(request, 'POST', '/scenario', { scenario: 'partial' })
    const pageErrors = []
    observePageErrors(page, pageErrors)
    await page.setViewportSize({ width: 390, height: 844 })
    await chooseLifecycleRange(page, '2026-09-10', '2026-09-12')
    const submitted = await submit(page)
    await page.getByLabel('任务接收').getByRole('link', { name: '查看任务', exact: true }).click()
    await expect(page).toHaveURL(`/downloads/tasks/${submitted.taskId}`)
    await expect(page.locator('[data-task-status]')).toHaveText('部分失败', { timeout: 15_000 })
    const failedRow = page.locator('.download-batch-table tbody tr').filter({
      has: page.getByText('2026-09-11 至 2026-09-11', { exact: true }),
    })
    await expect(failedRow).toHaveCount(1)
    await expect(failedRow.getByText('失败', { exact: true })).toBeVisible()
    await expect(failedRow.getByText('尝试次数 1', { exact: true })).toBeVisible()
    await expect(failedRow.getByText('SOURCE_TIMEOUT', { exact: true })).toBeVisible()
    await expect(failedRow.getByText('Source request timed out', { exact: true })).toBeVisible()
    let leaves = await batches(request, submitted.taskId)
    expect(leaves.map(batch => [batch.status, batch.attemptCount])).toEqual([
      ['SUCCEEDED', 1], ['FAILED', 1], ['SUCCEEDED', 1],
    ])
    expect(leaves[1].error).toEqual({ code: 'SOURCE_TIMEOUT', message: 'Source request timed out' })
    expect(await snapshot(request)).toMatchObject({
      calls: { '2026-09-10': 1, '2026-09-11': 1, '2026-09-12': 1 },
      committedKeys: ['20260910', '20260912'], rowCount: 2, inFlight: false,
    })

    const version = (await taskDetail(request, submitted.taskId)).version
    const retryPosts = []
    page.on('request', request => {
      if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/retry')) retryPosts.push(request.postDataJSON())
    })
    await page.locator('[data-retry]').click({ clickCount: 2 })
    await expect(page.locator('[data-task-status]')).toHaveText('已成功', { timeout: 15_000 })
    expect(retryPosts).toEqual([{ expectedVersion: version }])
    const done = await taskDetail(request, submitted.taskId)
    expect(done).toMatchObject({ status: 'SUCCEEDED', requestCount: 4, counts: {
      sourceRows: 3, insertedRows: 3, updatedRows: 0,
    } })
    expect(done.version).toBeGreaterThan(version)
    leaves = await batches(request, submitted.taskId)
    expect(leaves.map(batch => [batch.attemptCount, batch.sourceRows, batch.insertedRows, batch.updatedRows])).toEqual([
      [1, 1, 1, 0], [2, 1, 1, 0], [1, 1, 1, 0],
    ])
    expect(await snapshot(request)).toMatchObject({
      calls: { '2026-09-10': 1, '2026-09-11': 2, '2026-09-12': 1 },
      committedKeys: ['20260910', '20260911', '20260912'], rowCount: 3, inFlight: false,
    })
    expect(submitted.submissions()).toBe(1)
    await expectNoOverflow(page)
    await page.screenshot({ path: '/tmp/issue018-t12-lifecycle-mobile.png', fullPage: true })
    expect(pageErrors).toEqual([])
  })
}

if (SCENARIO === 'resume') {
  test('liveResumeAfterRestart', async ({ page, request }) => {
    const pageErrors = []
    observePageErrors(page, pageErrors)
    await page.goto(`/downloads/tasks/${RESUME_TASK_ID}`)
    await expect(page.locator('[data-task-status]')).toHaveText('已中断')
    const before = await taskDetail(request, RESUME_TASK_ID)
    const oldLeaves = await batches(request, RESUME_TASK_ID)
    expect(oldLeaves.map(batch => [batch.status, batch.attemptCount])).toEqual([
      ['SUCCEEDED', 1], ['FAILED', 1], ['FAILED', 1],
    ])
    expect(oldLeaves[1].error.code).toBe('SOURCE_TIMEOUT')
    expect(oldLeaves[2].error.code).toBe('EXECUTION_INTERRUPTED')
    expect(await snapshot(request)).toMatchObject({ calls: {}, committedKeys: ['20260920'], rowCount: 1, inFlight: false })

    const resumePosts = []
    page.on('request', request => {
      if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/resume')) resumePosts.push(request.postDataJSON())
    })
    await page.locator('[data-resume]').click({ clickCount: 2 })
    await expect.poll(async () => (await snapshot(request)).calls['2026-09-22']).toBe(1)
    expect(await snapshot(request)).toMatchObject({ committedKeys: ['20260920'], rowCount: 1, inFlight: true })
    await release(request, '2026-09-22')
    await expect(page.locator('[data-task-status]')).toHaveText('部分失败', { timeout: 15_000 })
    expect(resumePosts).toEqual([{ expectedVersion: before.version }])
    const done = await taskDetail(request, RESUME_TASK_ID)
    expect(done.status).toBe('PARTIAL_FAILED')
    expect(done.version).toBeGreaterThan(before.version)
    expect(done.counts).toMatchObject({ sourceRows: 2, insertedRows: 2, updatedRows: 0, succeededBatches: 2, failedBatches: 1 })
    expect(done.requestCount).toBe(4)
    const leaves = await batches(request, RESUME_TASK_ID)
    expect(leaves.map(batch => [batch.status, batch.attemptCount])).toEqual([
      ['SUCCEEDED', 1], ['FAILED', 1], ['SUCCEEDED', 2],
    ])
    expect(await snapshot(request)).toMatchObject({
      calls: { '2026-09-22': 1 }, committedKeys: ['20260920', '20260922'], rowCount: 2, inFlight: false,
    })
    await expectNoOverflow(page)
    expect(pageErrors).toEqual([])
  })
}
