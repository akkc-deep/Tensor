import { expect, test } from '@playwright/test'
import { installApi } from './ui-redesign.fixtures.js'
import { batch, task } from './download-tasks.fixtures.js'

const uuid = index => `${index.toString(16).padStart(8, '0')}-1111-4111-8111-111111111111`
const listPath = '/api/v1/download-tasks'
const records = Array.from({ length: 101 }, (_, index) => task({
  taskId: uuid(index + 1), submissionId: uuid(index + 1001), pluginId: 'retired_source',
}))
const parent = batch(0, { status: 'SPLIT' })
const batches = [parent, ...Array.from({ length: 101 }, (_, index) => batch(1, {
  batchId: uuid(index + 2001), parentBatchId: parent.batchId, batchKey: `000001/${index.toString(2).padStart(7, '0').split('').join('/')}`,
}))]

function paginate(items, query) {
  const page = Number(query.page), pageSize = Number(query.pageSize)
  return { status: 200, body: { page, pageSize, total: items.length, items: items.slice((page - 1) * pageSize, page * pageSize) } }
}

for (const width of [1280, 1440]) {
  test(`Studio task and batch server queries at ${width}px`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 1000 })
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    const batchPath = `${listPath}/${records[0].taskId}/batches`
    const api = await installApi(page, {
      [`GET ${listPath}`]: ({ query }) => paginate(records.filter(item =>
        ['pluginId', 'apiName', 'status', 'submissionId'].every(key => !query[key] || item[key] === query[key]),
      ), query),
      [`GET ${listPath}/${records[0].taskId}`]: { status: 200, body: records[0] },
      [`GET ${batchPath}`]: ({ query }) => paginate(batches.filter(item =>
        (!query.status || item.status === query.status) && (query.includeSplit === 'true' || item.status !== 'SPLIT'),
      ), query),
    })
    const lastQuery = path => api.requests.filter(request => request.path === path).at(-1)?.query
    await page.goto('/studio-live.html')
    const tasks = page.getByRole('region', { name: '下载任务', exact: true })
    await expect(tasks.locator('.task-row')).toHaveCount(20)
    await tasks.getByText('更多筛选', { exact: true }).click()
    await tasks.getByLabel('任务数据源', { exact: true }).fill('retired_source')
    await tasks.getByLabel('任务接口', { exact: true }).fill('daily')
    await tasks.getByRole('button', { name: '应用筛选' }).click()
    await tasks.getByLabel('任务状态', { exact: true }).selectOption('SUCCEEDED')
    await tasks.getByLabel('每页任务数').selectOption('100')
    await expect(tasks.locator('.task-row')).toHaveCount(100)
    await tasks.getByLabel('任务接口', { exact: true }).fill('weekly')
    await tasks.getByRole('button', { name: '任务下一页' }).click()
    await expect(tasks.locator('.task-row')).toHaveCount(1)
    await expect.poll(() => lastQuery(listPath)).toEqual({ page: '2', pageSize: '100', status: 'SUCCEEDED', pluginId: 'retired_source', apiName: 'daily' })
    await page.screenshot({ path: testInfo.outputPath('tasks.png'), fullPage: true })
    await tasks.getByRole('button', { name: '重置任务筛选' }).click()
    await expect.poll(() => lastQuery(listPath)).toEqual({ page: '1', pageSize: '100' })
    await tasks.getByLabel('提交标识', { exact: true }).fill(records[0].submissionId)
    await tasks.getByRole('button', { name: '应用筛选' }).click()
    await expect(tasks.locator('.task-row')).toHaveCount(1)
    await tasks.locator('.task-name').click()

    const dialog = page.getByRole('dialog')
    await expect(dialog.locator('.batch-preview li')).toHaveCount(20)
    await dialog.getByLabel('批次状态', { exact: true }).selectOption('SPLIT')
    await expect(dialog.getByText('没有匹配的批次。', { exact: true })).toBeVisible()
    await dialog.getByLabel('显示拆分父批次', { exact: true }).check()
    await expect(dialog.locator('.batch-preview li')).toHaveCount(1)
    await expect(dialog.getByText('此批次已拆分，执行结果见子批次。', { exact: true })).toBeVisible()
    await expect.poll(() => lastQuery(batchPath)).toEqual({ page: '1', pageSize: '20', includeSplit: 'true', status: 'SPLIT' })
    await dialog.getByLabel('批次状态', { exact: true }).selectOption('SUCCEEDED')
    await dialog.getByLabel('每页批次数').selectOption('50')
    await expect(dialog.locator('.batch-preview li')).toHaveCount(50)
    await dialog.getByRole('button', { name: '批次下一页' }).click()
    await expect.poll(() => lastQuery(batchPath)).toEqual({ page: '2', pageSize: '50', includeSplit: 'true', status: 'SUCCEEDED' })
    await expect(dialog.locator('.batch-preview li')).toHaveCount(50)
    await dialog.getByLabel('批次状态', { exact: true }).selectOption('SPLIT')
    await expect(dialog.locator('.batch-preview li')).toHaveCount(1)
    await dialog.getByText('批次标识与来源', { exact: true }).click()
    await page.screenshot({ path: testInfo.outputPath('batches.png'), fullPage: true })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await dialog.getByRole('button', { name: '关闭任务详情' }).click()
    await expect(dialog).toHaveCount(0)
    expect(errors).toEqual([])
    expect(api.unexpected).toEqual([])
    expect(api.requests.every(request => request.method === 'GET')).toBe(true)
  })
}
