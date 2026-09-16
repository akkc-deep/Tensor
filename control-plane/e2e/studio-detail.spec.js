import { expect, test } from '@playwright/test'
import path from 'node:path'
import { TASK_ID, task, batch } from './download-tasks.fixtures.js'
import { installApi } from './ui-redesign.fixtures.js'
import { selectDownloadApi } from './catalog-helpers.js'

const detailPath = `/api/v1/download-tasks/${TASK_ID}`
const output = path.resolve('node_modules/.cache/studio-t06')
const dialog = page => page.getByRole('dialog')
const ok = body => ({ status: 200, body })
const error = requestId => ({ status: 500, body: { requestId, code: 'QUERY_FAILED', message: '查询暂时不可用', retryable: true, fieldErrors: [] } })
function items(count = 23) {
  return Array.from({ length: count }, (_, i) => batch(0, {
    batchId: `44444444-4444-4444-8444-${String(i + 1).padStart(12, '0')}`,
    parentBatchId: i === 1 ? '55555555-5555-4555-8555-555555555555' : null,
    batchKey: String(i + 1).padStart(6, '0'),
    status: i === 1 ? 'FAILED' : 'SUCCEEDED',
    sourceRows: i === 0 ? 9007199254740993n : 5n,
    error: i === 1 ? { code: 'SOURCE_TIMEOUT', message: '上游请求超时，请稍后重试。' } : null,
  }))
}
async function setup(page, overrides = {}) {
  const saved = task({ status: 'PARTIAL_FAILED', canRetry: true,
    counts: { ...task().counts, totalBatches: 23n, succeededBatches: 22n, failedBatches: 1n, splitBatches: 1n } })
  return installApi(page, {
    'GET /api/v1/download-tasks': ({ query }) => ok({ page: Number(query.page), pageSize: Number(query.pageSize), total: 1n, items: [saved] }),
    [`GET ${detailPath}`]: () => ok(saved),
    [`GET ${detailPath}/batches`]: ({ query }) => {
      const page = Number(query.page), pageSize = Number(query.pageSize), all = items()
      return ok({ page, pageSize, total: BigInt(all.length), items: all.slice((page - 1) * pageSize, page * pageSize) })
    },
    ...overrides,
  })
}
async function noOverflow(page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  expect(await dialog(page).evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true)
}

for (const width of [1024, 1280, 1440]) {
  test(`${width}px：Studio 详情与 Demo 对照，真实批次和大整数可读`, async ({ page, context }) => {
    await page.setViewportSize({ width, height: 1000 })
    const saved = task({ apiName: 'daily_basic', status: 'PARTIAL_FAILED', canRetry: true,
      counts: { ...task().counts, succeededBatches: 2n, failedBatches: 1n, splitBatches: 1n, sourceRows: 9007199254741003n, insertedRows: 15n, updatedRows: 0n },
      params: { ts_code: '000001.SZ', start_date: '20260601', end_date: '20260807' } })
    const api = await setup(page, {
      'GET /api/v1/download-tasks': () => ok({ page: 1, pageSize: 20, total: 1n, items: [saved] }),
      [`GET ${detailPath}`]: () => ok(saved),
      [`GET ${detailPath}/batches`]: () => ok({ page: 1, pageSize: 20, total: 3n, items: items(3) }),
    })
    await page.goto('/downloads')
    await selectDownloadApi(page, 'daily_basic')
    await page.locator('.download-task-list a').first().click()
    await expect(dialog(page).getByRole('heading', { name: 'daily_basic', exact: true })).toBeVisible()
    await expect(dialog(page).getByRole('button', { name: '关闭任务详情' })).toBeFocused()
    await expect(dialog(page).getByLabel('已结束批次进度')).toHaveAttribute('value', '100')
    await expect(dialog(page)).toContainText('已结束包含失败批次')
    const rows = dialog(page).locator('ol > li')
    await expect(rows).toHaveCount(3)
    await expect(rows.first()).toContainText('9007199254740993')
    await expect(rows.nth(1)).toContainText('上游请求超时')
    await rows.nth(1).locator('summary').click()
    await expect(rows.nth(1).getByText('父批次 55555555-5555-4555-8555-555555555555')).toBeVisible()
    await rows.nth(1).locator('summary').click()
    await dialog(page).evaluate(el => { el.scrollTop = 0 })
    const demo = await context.newPage()
    await demo.setViewportSize({ width, height: 1000 })
    await demo.goto('/ui-demos.html')
    await demo.locator('.studio-tasks .task-name').nth(2).click()
    await expect(demo.getByRole('dialog')).toBeVisible()
    for (const prop of ['width', 'paddingTop', 'paddingLeft', 'backgroundColor']) {
      expect(await dialog(page).evaluate((el, prop) => getComputedStyle(el)[prop], prop)).toBe(await demo.getByRole('dialog').evaluate((el, prop) => getComputedStyle(el)[prop], prop))
    }
    expect((await dialog(page).boundingBox()).x).toBe(width - 430)
    expect(await dialog(page).locator('[data-task-status]').evaluate(el => getComputedStyle(el).color)).toBe(await demo.getByRole('dialog').locator('.status').first().evaluate(el => getComputedStyle(el).color))
    expect(await dialog(page).locator('.task-detail__stop-reason').evaluate(el => getComputedStyle(el).backgroundColor)).toBe(await demo.getByRole('dialog').locator('.task-error').evaluate(el => getComputedStyle(el).backgroundColor))
    expect(await dialog(page).locator('.confirmation > div').first().evaluate(el => getComputedStyle(el).borderBottomWidth)).toBe('1px')
    expect(await dialog(page).locator('[data-task-status]').evaluate(el => getComputedStyle(el).fontSize)).toBe('12px')
    await noOverflow(page)
    await page.screenshot({ path: `${output}/detail-${width}.png`, fullPage: true })
    await demo.screenshot({ path: `${output}/demo-detail-${width}.png`, fullPage: true })
    await dialog(page).evaluate(el => { el.scrollTop = el.querySelector('.download-batch-table').offsetTop - 30 })
    await rows.nth(1).locator('summary').click()
    await noOverflow(page)
    await page.screenshot({ path: `${output}/batches-${width}.png`, fullPage: true })
    await demo.close()
    expect(api.unexpected).toEqual([])
  })
}

test('关闭、Esc、遮罩和前进后退保留草稿、列表与焦点；深链接刷新可重开', async ({ page }) => {
  await setup(page)
  await page.goto('/downloads')
  await selectDownloadApi(page, 'daily')
  await page.locator('[data-parameter="ts_code"] input').fill('000001.SZ')
  const link = page.locator('.download-task-list a').first()
  await link.focus()
  await page.keyboard.press('Enter')
  await expect(dialog(page)).toBeVisible()
  // Native modal traps keyboard focus even when the background has many inputs.
  await page.keyboard.press('Shift+Tab')
  expect(await page.evaluate(() => document.querySelector('dialog').contains(document.activeElement))).toBe(true)
  await page.keyboard.press('Escape')
  await expect(page).toHaveURL('/downloads')
  await expect(link).toBeFocused()
  await expect(page.locator('[data-parameter="ts_code"] input')).toHaveValue('000001.SZ')
  await page.goForward()
  await expect(dialog(page)).toBeVisible()
  await page.goBack()
  await expect(dialog(page)).toHaveCount(0)
  await expect(link).toBeFocused()
  await link.click()
  await dialog(page).getByRole('button', { name: '关闭任务详情' }).click()
  await expect(link).toBeFocused()
  await link.click()
  await page.mouse.click(20, 300)
  await expect(page).toHaveURL('/downloads')
  await expect(link).toBeFocused()
  await page.goto(`/downloads/tasks/${TASK_ID}`)
  await expect(dialog(page)).toBeVisible()
  await page.reload()
  await expect(dialog(page).locator('[data-task-status]')).toHaveText('部分失败')
  await dialog(page).getByRole('button', { name: '关闭任务详情' }).click()
  await expect(page).toHaveURL('/downloads')
  await expect(page.locator('.download-task-list a').first()).toBeFocused()
})

test('批次分页与每页数量，零条和越尾页可恢复', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 900 })
  let all = items()
  const api = await setup(page, { [`GET ${detailPath}/batches`]: ({ query }) => {
    const page = Number(query.page), pageSize = Number(query.pageSize)
    return ok({ page, pageSize, total: BigInt(all.length), items: all.slice((page - 1) * pageSize, page * pageSize) })
  } })
  await page.goto(`/downloads/tasks/${TASK_ID}`)
  const batches = dialog(page).locator('.download-batch-table')
  await expect(batches.locator('ol > li')).toHaveCount(20)
  await batches.getByRole('button', { name: '下一页' }).click()
  await expect(batches.locator('ol > li')).toHaveCount(3)
  await expect(batches.locator('[data-total]')).toContainText('第 2 / 2 页')
  expect(api.requests.filter(r => r.path.endsWith('/batches')).at(-1).query).toEqual({ page: '2', pageSize: '20', includeSplit: 'false' })
  await batches.getByRole('navigation', { name: '批次分页' }).scrollIntoViewIfNeeded()
  await expect(batches.getByRole('navigation', { name: '批次分页' })).toBeInViewport({ ratio: 1 })
  await page.screenshot({ path: `${output}/pagination-1024.png`, fullPage: true })
  await batches.getByLabel('每页批次数').selectOption('50')
  await expect(batches.locator('ol > li')).toHaveCount(23)
  await expect(batches.locator('[data-total]')).toContainText('第 1 / 1 页')
  await batches.getByLabel('每页批次数').selectOption('20')
  await batches.getByRole('button', { name: '下一页' }).click()
  all = []
  await batches.getByRole('button', { name: '刷新批次' }).click()
  await expect(batches.getByText('本页暂无批次')).toBeVisible()
  await expect(batches.locator('[data-total]')).toHaveText('共 0 个叶子批次')
  await batches.getByRole('button', { name: '返回第一页' }).click()
  await expect(batches.getByText('暂无批次', { exact: true })).toBeVisible()
  await expect(batches.getByRole('button', { name: '下一页' })).toBeDisabled()
  expect(api.unexpected).toEqual([])
})

test('详情和批次各自失败可重载，完整性使用历史快照，长内容不溢出', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 900 })
  let detailFails = true, batchesFail = false
  const saved = task({ apiName: 'a'.repeat(64), params: { note: 'x'.repeat(200) },
    extraction: { ruleKind: 'RESPONSE_ONLY', policyVersion: 'saved-rule' } })
  await setup(page, {
    [`GET ${detailPath}`]: ({ requestId }) => detailFails ? error(requestId) : ok(saved),
    [`GET ${detailPath}/batches`]: ({ query, requestId }) => batchesFail ? error(requestId) : ok({ page: Number(query.page), pageSize: Number(query.pageSize), total: 1n, items: [batch(0, { status: 'FAILED', error: { code: 'SOURCE_TIMEOUT', message: 'x'.repeat(300) } })] }),
  })
  await page.goto(`/downloads/tasks/${TASK_ID}`)
  await expect(dialog(page).getByText('任务加载失败')).toBeVisible()
  await expect(dialog(page).locator('ol > li')).toHaveCount(1)
  detailFails = false; batchesFail = true
  await dialog(page).getByRole('button', { name: '重新查询' }).click()
  await expect(dialog(page).locator('[data-task-status]')).toHaveText('返回记录已采集')
  await expect(dialog(page)).toContainText('数据完整性未确认，可能存在上游截断')
  await expect(dialog(page).locator('.download-batch-table')).toContainText('状态暂时无法更新')
  await expect(dialog(page).locator('ol > li')).toHaveCount(1)
  await noOverflow(page)
  await page.screenshot({ path: `${output}/long-detail-1024.png`, fullPage: true })
  await dialog(page).evaluate(el => { el.scrollTop = el.querySelector('.download-batch-table').offsetTop - 24 })
  await page.screenshot({ path: `${output}/batch-error-1024.png`, fullPage: true })
  batchesFail = false
  await dialog(page).getByRole('button', { name: '刷新批次' }).click()
  await expect(dialog(page).locator('.download-batch-table__warning')).toHaveCount(0)
})

test('运行详情轮询、隐藏暂停、关闭后停止，终态和计划未就绪准确呈现', async ({ page }) => {
  const now = new Date('2026-09-16T00:00:00Z')
  await page.clock.install({ time: now }); await page.clock.pauseAt(now)
  let current = task({ status: 'RUNNING' })
  const api = await setup(page, { [`GET ${detailPath}`]: () => ok(current) })
  const calls = () => api.requests.filter(r => r.path === detailPath).length
  await page.goto(`/downloads/tasks/${TASK_ID}`)
  await expect(dialog(page).locator('[data-task-status]')).toHaveText('运行中')
  const first = calls()
  await page.clock.fastForward(2000)
  await expect.poll(calls).toBe(first + 1)
  await page.evaluate(() => { Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' }); document.dispatchEvent(new Event('visibilitychange')) })
  await page.clock.fastForward(30_000)
  expect(calls()).toBe(first + 1)
  await page.evaluate(() => { Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' }); document.dispatchEvent(new Event('visibilitychange')) })
  await expect.poll(calls).toBe(first + 2)
  current = task({ status: 'FAILED', planReady: false, counts: { totalBatches: 0n, succeededBatches: 0n, failedBatches: 0n, pendingBatches: 0n, runningBatches: 0n, splitBatches: 0n, sourceRows: 0n, insertedRows: 0n, updatedRows: 0n }, lastError: { code: 'SOURCE_TIMEOUT', message: '规划请求超时' } })
  await page.clock.fastForward(2000)
  await expect(dialog(page)).toContainText('尚未生成计划')
  await expect(dialog(page).getByRole('progressbar')).toHaveCount(0)
  const terminal = calls()
  await page.clock.fastForward(30_000)
  expect(calls()).toBe(terminal)
  current = task({ status: 'RUNNING' })
  await dialog(page).getByRole('button', { name: '刷新状态' }).click()
  await expect(dialog(page).locator('[data-task-status]')).toHaveText('运行中')
  await page.keyboard.press('Escape')
  await expect(dialog(page)).toHaveCount(0)
  const closed = calls()
  await page.clock.fastForward(30_000)
  expect(calls()).toBe(closed)
})

test('返回下载页只导航一次，并可通过浏览器前进重开详情', async ({ page }) => {
  await setup(page)
  await page.goto('/downloads')
  await page.locator('.download-task-list a').first().click()
  await expect(dialog(page)).toBeVisible()
  const position = await page.evaluate(() => history.state.position)
  await dialog(page).getByRole('link', { name: '返回下载页' }).click()
  await expect(page).toHaveURL('/downloads')
  expect(await page.evaluate(() => history.state.position)).toBe(position - 1)
  await page.goForward()
  await expect(page).toHaveURL(`/downloads/tasks/${TASK_ID}`)
  await expect(dialog(page)).toBeVisible()
  expect(await page.evaluate(() => history.state.forward)).toBeNull()
})
