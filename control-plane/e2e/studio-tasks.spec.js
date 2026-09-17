import { expect, test } from '@playwright/test'
import path from 'node:path'
import { task } from './download-tasks.fixtures.js'
import { selectDownloadApi } from './catalog-helpers.js'
import { installApi } from './ui-redesign.fixtures.js'

const output = path.resolve('node_modules/.cache/studio-t05')
const list = page => page.locator('.download-task-list')
const group = (page, name) => list(page).getByRole('button', { name, exact: true })
const statusSets = { ACTIVE: ['QUEUED', 'RUNNING'], DONE: ['SUCCEEDED'], ERROR: ['PARTIAL_FAILED', 'FAILED', 'INTERRUPTED'] }
const statuses = ['QUEUED', 'SUCCEEDED', 'FAILED', 'RUNNING', 'PARTIAL_FAILED', 'INTERRUPTED']
function rows(count = 66) {
  return Array.from({ length: count }, (_, i) => task({
    taskId: `00000000-0000-4000-8000-${String(i + 1).padStart(12, '0')}`,
    apiName: ['daily', 'weekly', 'income', 'trade_cal', 'balancesheet', 'adj_factor'][i % 6],
    status: statuses[i % 6],
    counts: { ...task().counts, succeededBatches: i % 6 === 1 ? 3n : 1n, pendingBatches: i % 6 === 1 ? 0n : 2n },
    lastError: i % 6 === 2 ? { code: 'SOURCE_TIMEOUT', message: '数据源请求超时，请在详情中查看失败原因。' } : null,
    extraction: { policyVersion: 'saved-policy-v1', ruleKind: i % 6 === 4 ? 'RESPONSE_ONLY' : 'VERIFIED_RULE' },
  }))
}
function response(query, items) {
  const filtered = query.statusGroup ? items.filter(item => statusSets[query.statusGroup].includes(item.status)) : items
  const page = Number(query.page), pageSize = Number(query.pageSize)
  return { status: 200, body: { page, pageSize, total: BigInt(filtered.length), items: filtered.slice((page - 1) * pageSize, page * pageSize) } }
}

for (const width of [1024, 1280, 1440]) {
  test(`${width}px：Studio 卡片对照与键盘、长整数和完整性提示`, async ({ page, context }) => {
    await page.setViewportSize({ width, height: 1000 })
    const items = rows(6)
    items[0].counts.insertedRows = 9223372036854775807n
    const api = await installApi(page, { 'GET /api/v1/download-tasks': ({ query }) => response(query, items) })
    await page.goto('/downloads')
    await expect(list(page).locator('article')).toHaveCount(6)
    const demo = await context.newPage()
    await demo.setViewportSize({ width, height: 1000 })
    await demo.goto('/ui-demos.html')
    const actualHeading = list(page).locator('.section-heading h2')
    const demoHeading = demo.locator('.studio-tasks .section-heading h2')
    for (const prop of ['fontSize', 'fontWeight', 'color']) {
      expect(await actualHeading.evaluate((el, prop) => getComputedStyle(el)[prop], prop)).toBe(await demoHeading.evaluate((el, prop) => getComputedStyle(el)[prop], prop))
    }
    const a = await actualHeading.boundingBox(), b = await demoHeading.boundingBox()
    expect(a.x).toBeCloseTo(b.x, 0)
    expect(await list(page).locator('.task-filters').evaluate(el => getComputedStyle(el).gap)).toBe(await demo.locator('.studio-tasks .task-filters').evaluate(el => getComputedStyle(el).gap))
    await expect(list(page).locator('.task-notice')).toBeVisible()
    await expect(list(page).locator('.task-error')).toBeVisible()
    await expect(list(page).locator('table')).toHaveCount(0)
    await group(page, '进行中').focus()
    await page.keyboard.press('Enter')
    await expect(group(page, '进行中')).toHaveAttribute('aria-pressed', 'true')
    await expect(list(page).locator('article')).toHaveCount(2)
    await group(page, '全部').click()
    await expect(list(page).locator('article')).toHaveCount(6)
    await list(page).locator('summary').first().focus()
    await page.keyboard.press('Enter')
    await expect(list(page).getByText('新增记录次数 9223372036854775807')).toBeVisible()
    await page.keyboard.press('Enter')
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    expect(await list(page).evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true)
    await selectDownloadApi(page, 'daily')
    await page.getByRole('button', { name: '单次下载', exact: true }).click()
    await page.locator('[data-parameter="ts_code"] input').fill('000001.SZ')
    await page.locator('[data-parameter="trade_date"] input').fill('2026-08-07')
    await page.locator('h1').click()
    await page.screenshot({ path: `${output}/tasks-${width}.png`, fullPage: true })
    await demo.screenshot({ path: `${output}/demo-tasks-${width}.png`, fullPage: true })
    expect(api.unexpected).toEqual([])
    await demo.close()
  })
}

test('多页混合状态：四组查询、服务端总数、翻页和每页数量一致', async ({ page }) => {
  const api = await installApi(page, { 'GET /api/v1/download-tasks': ({ query }) => response(query, rows()) })
  await page.goto('/downloads')
  await expect(list(page).locator('[data-total]')).toContainText('全部 · 共 66 个任务，第 1 / 4 页')
  await list(page).getByRole('button', { name: '下一页', exact: true }).click()
  await expect(list(page).locator('[data-total]')).toContainText('第 2 / 4 页')
  for (const [name, value, total, pages, count] of [['进行中', 'ACTIVE', 22, 2, 20], ['已完成', 'DONE', 11, 1, 11], ['需处理', 'ERROR', 33, 2, 20]]) {
    await group(page, name).click()
    await expect(list(page).locator('[data-total]')).toContainText(`${name} · 共 ${total} 个任务，第 1 / ${pages} 页`)
    await expect(list(page).locator('article')).toHaveCount(count)
    expect(api.requests.at(-1).query).toEqual({ page: '1', pageSize: '20', statusGroup: value })
  }
  await list(page).getByRole('button', { name: '下一页', exact: true }).click()
  await expect(list(page).locator('[data-total]')).toContainText('第 2 / 2 页')
  await expect(list(page).locator('article')).toHaveCount(13)
  await list(page).locator('.el-select__wrapper').click()
  await page.getByRole('option', { name: '50条/页', exact: true }).click()
  await expect(list(page).locator('[data-total]')).toContainText('需处理 · 共 33 个任务，第 1 / 1 页')
  await expect(list(page).locator('article')).toHaveCount(33)
  expect(api.requests.at(-1).query).toEqual({ page: '1', pageSize: '50', statusGroup: 'ERROR' })
  expect(api.unexpected).toEqual([])
})

test('刷新失败保留当前页、切组失败清空旧结果，空分组和越尾页可恢复', async ({ page }) => {
  let failure = false, items = rows()
  const api = await installApi(page, { 'GET /api/v1/download-tasks': ({ query, requestId }) => failure
    ? { status: 500, body: { requestId, code: 'QUERY_FAILED', message: '任务查询暂时不可用', retryable: true, fieldErrors: [] } }
    : response(query, items) })
  await page.goto('/downloads')
  await expect(list(page).locator('article')).toHaveCount(20)
  failure = true
  await list(page).locator('[data-refresh]').click()
  await expect(list(page).getByText('状态暂时无法更新')).toBeVisible()
  await expect(list(page).locator('article')).toHaveCount(20)
  await expect(list(page).getByText(/上次更新/)).toBeVisible()
  await group(page, '进行中').click()
  await expect(list(page).getByText('近期任务加载失败')).toBeVisible()
  await expect(list(page).locator('article')).toHaveCount(0)
  failure = false
  await list(page).getByRole('button', { name: '重新加载' }).click()
  await expect(list(page).locator('article')).toHaveCount(20)
  await list(page).getByRole('button', { name: '下一页', exact: true }).click()
  await expect(list(page).locator('article')).toHaveCount(2)
  items = rows(6)
  await list(page).locator('[data-refresh]').click()
  await expect(list(page).getByText('本页暂无任务')).toBeVisible()
  await list(page).getByRole('button', { name: '返回第一页' }).click()
  await expect(list(page).locator('article')).toHaveCount(2)
  items = []
  await group(page, '已完成').click()
  await expect(list(page).getByText('暂无近期任务')).toBeVisible()
  await list(page).getByRole('button', { name: '查看全部任务' }).click()
  await expect(group(page, '全部')).toHaveAttribute('aria-pressed', 'true')
  expect(api.unexpected).toEqual([])
})

test('隐藏和离开停止轮询，恢复和重新进入立即查询同一分组', async ({ page }) => {
  const now = new Date('2026-09-16T00:00:00Z')
  await page.clock.install({ time: now })
  await page.clock.pauseAt(now)
  const api = await installApi(page, { 'GET /api/v1/download-tasks': ({ query }) => response(query, rows()) })
  const requests = () => api.requests.filter(request => request.path === '/api/v1/download-tasks').length
  await page.goto('/downloads')
  await group(page, '需处理').click()
  await expect(list(page).locator('[data-total]')).toContainText('需处理 · 共 33')
  let before = requests()
  await page.clock.fastForward(5_000)
  await expect.poll(requests).toBe(before + 1)
  await expect(list(page).locator('[data-refresh]')).toHaveAttribute('aria-busy', 'false')
  await page.evaluate(() => { Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' }); document.dispatchEvent(new Event('visibilitychange')) })
  before = requests()
  await page.clock.fastForward(30_000)
  expect(requests()).toBe(before)
  await page.evaluate(() => { Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' }); document.dispatchEvent(new Event('visibilitychange')) })
  await expect.poll(requests).toBe(before + 1)
  await expect(list(page).locator('[data-refresh]')).toHaveAttribute('aria-busy', 'false')
  await page.getByRole('link', { name: '外观设置', exact: true }).click()
  await expect(page.getByRole('heading', { name: '外观设置', level: 1 })).toBeVisible()
  before = requests()
  await page.clock.fastForward(30_000)
  expect(requests()).toBe(before)
  await page.goBack()
  await expect.poll(requests).toBe(before + 1)
  await expect(group(page, '需处理')).toHaveAttribute('aria-pressed', 'true')
  expect(api.requests.filter(request => request.path === '/api/v1/download-tasks').at(-1).query.statusGroup).toBe('ERROR')
  expect(api.unexpected).toEqual([])
})

test('快速切组不回填旧响应', async ({ page }) => {
  let release
  const gate = new Promise(resolve => { release = resolve })
  const api = await installApi(page, { 'GET /api/v1/download-tasks': async ({ query }) => {
    if (query.statusGroup === 'ACTIVE') await gate
    return response(query, rows())
  } })
  await page.goto('/downloads')
  await expect(list(page).locator('article')).toHaveCount(20)
  await group(page, '进行中').click()
  await expect.poll(() => api.requests.at(-1).query.statusGroup).toBe('ACTIVE')
  await group(page, '需处理').click()
  release()
  await expect(list(page).locator('[data-total]')).toContainText('需处理 · 共 33')
  await expect(list(page).locator('[data-task-status]')).toHaveText(Array.from({ length: 20 }, (_, i) => ['失败', '部分失败', '已中断'][i % 3]))
  expect(api.unexpected).toEqual([])
})

test('1024px：最长标识、大整数、长错误和展开字段均在右栏内', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 1000 })
  const item = task({ pluginId: 'p'.repeat(64), apiName: 'a'.repeat(64), status: 'PARTIAL_FAILED',
    params: { note: 'long_parameter_'.repeat(20) },
    lastError: { code: 'SOURCE_TIMEOUT', message: 'timeout_'.repeat(35) },
    extraction: { ruleKind: 'RESPONSE_ONLY', policyVersion: 'saved_'.repeat(30) },
    counts: { ...task().counts, sourceRows: 9223372036854775807n, insertedRows: 9223372036854775807n },
  })
  await installApi(page, { 'GET /api/v1/download-tasks': ({ query }) => response(query, [item]) })
  await page.goto('/downloads')
  await list(page).locator('summary').click()
  await expect(list(page).getByText('新增记录次数 9223372036854775807')).toBeVisible()
  const overflow = await list(page).evaluate(root => [...root.querySelectorAll('*')].filter(el => el.getClientRects().length && el.scrollWidth > el.clientWidth + 1).map(el => el.className))
  expect(overflow).toEqual([])
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: `${output}/long-task-1024.png`, fullPage: true })
})
