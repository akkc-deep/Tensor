import { expect } from '@playwright/test'

import { TASK_ID, test } from './download-tasks.fixtures.js'

const DETAIL_PATH = `/api/v1/download-tasks/${TASK_ID}`

test.use({ baseURL: process.env.TENSOR_UI_BASE_URL || 'http://127.0.0.1:4173' })

test.describe.configure({ timeout: 30_000 })

async function selectDaily(page) {
  const input = page.locator('#download-api')
  await expect(input).toBeEnabled()
  await input.click()
  await input.fill('daily')
  const listboxId = await input.getAttribute('aria-controls')
  const option = page.locator(`#${listboxId}`).getByRole('option')
    .filter({ has: page.getByText('daily', { exact: true }) })
  await expect(option).toHaveCount(1)
  await option.click()
  await expect(page.getByText('交易日期范围。', { exact: true })).toBeVisible()
}

async function fillRange(page) {
  const values = {
    ts_code: '000001.sz',
    start_date: '2026-09-01',
    end_date: '2026-09-03',
  }
  for (const [name, value] of Object.entries(values)) {
    const input = page.locator(`[data-parameter="${name}"] input`)
    await input.fill(value)
    if (name !== 'ts_code') await input.press('Enter')
    await input.blur()
    await expect(input).toHaveValue(value)
  }
}

async function expectTask(page, status) {
  await expect(page.getByRole('heading', { name: '任务详情', level: 1 })).toBeVisible()
  await expect(page.locator('[data-task-status]')).toHaveText(status)
  await expect(page.getByText(`任务 ID ${TASK_ID}`, { exact: true })).toBeVisible()
}

async function expectNoPageOverflow(page) {
  const metrics = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }))
  expect(metrics.scrollWidth).toBeLessThanOrEqual(metrics.clientWidth)
}

function expectDynamicRequestIds(downloadTaskApi) {
  const ids = downloadTaskApi.state.requests.map(({ requestId }) => requestId)
  expect(ids.every((id) => /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(id))).toBe(true)
  expect(new Set(ids).size).toBe(ids.length)
}

test('RANGE receipt opens, reloads, and reopens from the server task list', async ({
  page,
  context,
  downloadTaskApi,
}) => {
  await page.setViewportSize({ width: 1440, height: 1080 })
  await page.goto('/downloads')
  await selectDaily(page)
  await fillRange(page)
  await page.getByRole('button', { name: '提交任务', exact: true }).click()

  const receipt = page.locator('.download-result-panel')
  await expect(receipt.getByRole('heading', { name: '任务已接收' })).toBeVisible()
  await expect(receipt).toContainText('服务已确认任务身份，进度以近期任务或详情为准。')
  await expect(receipt).toContainText(TASK_ID)
  await receipt.getByRole('link', { name: '查看任务' }).click()

  await expect(page).toHaveURL(`/downloads/tasks/${TASK_ID}`)
  await expectTask(page, '已成功')
  const parameters = page.getByLabel('规范化参数')
  await expect(parameters.getByText('ts_code=000001.SZ', { exact: true })).toBeVisible()
  await expect(parameters.getByText('start_date=20260901', { exact: true })).toBeVisible()
  await expect(parameters.getByText('end_date=20260903', { exact: true })).toBeVisible()
  await expect(page.locator('.task-detail__times time[datetime="2026-09-12T00:00:00Z"]')).toHaveCount(1)

  const beforeReload = downloadTaskApi.requests('GET', DETAIL_PATH).length
  await page.reload()
  await expectTask(page, '已成功')
  expect(downloadTaskApi.requests('GET', DETAIL_PATH).length).toBeGreaterThan(beforeReload)

  await page.close()
  const reopened = await context.newPage()
  await reopened.setViewportSize({ width: 1440, height: 1080 })
  await reopened.goto('/downloads')
  const recent = reopened.locator('.download-task-list')
  const recentLink = recent.getByRole('link', { name: '查看任务' })
  await expect(recentLink).toHaveAttribute('href', `/downloads/tasks/${TASK_ID}`)
  await recentLink.click()
  await expect(reopened).toHaveURL(`/downloads/tasks/${TASK_ID}`)
  await expectTask(reopened, '已成功')

  const submissions = downloadTaskApi.requests('POST', '/api/v1/download-tasks')
  expect(submissions).toHaveLength(1)
  expect(JSON.parse(submissions[0].rawBody)).toMatchObject({
    pluginId: 'tushare_pro',
    apiName: 'daily',
    mode: 'RANGE',
    params: {
      ts_code: '000001.SZ',
      start_date: '20260901',
      end_date: '20260903',
    },
  })
  expectDynamicRequestIds(downloadTaskApi)
  downloadTaskApi.assertClean()
})

test('partial failure retries once with the original bigint and advances through RUNNING', async ({
  page,
  downloadTaskApi,
}) => {
  downloadTaskApi.seedPartialFailure()
  await page.setViewportSize({ width: 1440, height: 1080 })
  await page.goto(`/downloads/tasks/${TASK_ID}`)

  await expectTask(page, '部分失败')
  await expect(page.getByText('版本 9007199254740993', { exact: false })).toBeVisible()
  await expect(page.getByText('累计请求次数 9223372036854775806', { exact: true })).toBeVisible()
  await expect(page.getByText('来源行数 9007199254740995', { exact: true })).toBeVisible()
  const rows = page.locator('.download-batch-table tbody tr')
  await expect(rows).toHaveCount(3)
  await expect(rows.nth(0)).toContainText('来源行数 9007199254740993')
  await expect(rows.nth(2)).toContainText('2026-09-03 至 2026-09-03')
  await expect(rows.nth(2)).toContainText('尝试次数 1')
  await expect(rows.nth(2)).toContainText('Source request timed out')

  await page.locator('[data-retry]').click({ clickCount: 2 })
  await expect(page.getByText('重试请求已接收', { exact: true })).toBeVisible()
  await expect(page.locator('[data-task-status]')).toHaveText('运行中')
  await expect(page.locator('[data-task-status]')).toHaveText('已成功', { timeout: 5_000 })

  const retries = downloadTaskApi.requests('POST', `${DETAIL_PATH}/retry`)
  expect(retries).toHaveLength(1)
  expect(retries[0]).toMatchObject({
    rawBody: '{"expectedVersion":9007199254740993}',
    contentType: expect.stringContaining('application/json'),
  })
  await expect(page.getByText('来源行数 9007199254740998', { exact: true })).toBeVisible()
  await expect(page.getByText('累计请求次数 9223372036854775807', { exact: true })).toBeVisible()
  await expect(rows.nth(0)).toContainText('来源行数 9007199254740993')
  await expect(rows.nth(2)).toContainText('尝试次数 2')
  await expect(rows.nth(2)).toContainText('来源行数 3')
  await expect(rows.nth(2)).toContainText('无')
  await expectNoPageOverflow(page)
  await page.screenshot({ path: '/tmp/issue018-t11-desktop.png', fullPage: true })

  expectDynamicRequestIds(downloadTaskApi)
  downloadTaskApi.assertClean()
})

test('INTERRUPTED conflict preserves status through a query 500 and resumes with the refreshed version', async ({
  page,
  downloadTaskApi,
}) => {
  downloadTaskApi.seedInterrupted()
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`/downloads/tasks/${TASK_ID}`)

  await expectTask(page, '已中断')
  await expect(page.locator('[data-resume]')).toBeVisible()
  await expect(page.locator('[data-retry]')).toHaveCount(0)
  await expect(page.getByText('版本 9007199254740993', { exact: false })).toBeVisible()
  await expect(page.getByText('尝试次数 0（尚未尝试）', { exact: true })).toBeVisible()

  const detailGets = downloadTaskApi.requests('GET', DETAIL_PATH).length
  await page.locator('[data-resume]').click()
  await expect(page.getByText('任务状态已变化，已重新查询', { exact: true })).toBeVisible()
  await expect(page.getByText('状态暂时无法更新', { exact: true })).toBeVisible()
  await expect(page.locator('[data-task-status]')).toHaveText('已中断')
  expect(downloadTaskApi.requests('GET', DETAIL_PATH)).toHaveLength(detailGets + 1)

  const table = page.getByRole('region', { name: '批次表格' })
  const tableMetrics = await table.evaluate((element) => ({
    scrollWidth: element.scrollWidth,
    clientWidth: element.clientWidth,
  }))
  expect(tableMetrics.scrollWidth).toBeGreaterThan(tableMetrics.clientWidth)
  await expectNoPageOverflow(page)
  await page.screenshot({ path: '/tmp/issue018-t11-mobile.png', fullPage: true })

  await page.locator('[data-refresh-task]').click()
  await expect(page.getByText('版本 9007199254740994', { exact: false })).toBeVisible()
  await expect(page.getByText('状态暂时无法更新', { exact: true })).toHaveCount(0)
  await expect(page.locator('[data-task-status]')).toHaveText('已中断')
  await page.locator('[data-resume]').click()
  await expect(page.getByText('恢复请求已接收', { exact: true })).toBeVisible()
  await expect(page.locator('[data-task-status]')).toHaveText('已成功')

  expect(downloadTaskApi.requests('POST', `${DETAIL_PATH}/resume`).map(({ rawBody }) => rawBody)).toEqual([
    '{"expectedVersion":9007199254740993}',
    '{"expectedVersion":9007199254740994}',
  ])
  await expectNoPageOverflow(page)
  expectDynamicRequestIds(downloadTaskApi)
  downloadTaskApi.assertClean()
})
