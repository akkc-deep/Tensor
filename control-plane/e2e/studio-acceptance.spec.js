import { expect, test } from '@playwright/test'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { TASK_ID, task, batch } from './download-tasks.fixtures.js'
import { EXPECTED, installApi, successDownload, syntheticRecords } from './ui-redesign.fixtures.js'
import { selectDownloadApi } from './catalog-helpers.js'

const output = path.resolve('node_modules/.cache/studio-t10')
const taskPath = id => `/api/v1/download-tasks/${id}`
const recordsPath = '/api/v1/data-sources/tushare_pro/datasets/daily/records'
const resumeId = '22222222-2222-4222-8222-222222222223'
const recent = page => page.locator('.download-task-list')
const dialog = page => page.getByRole('dialog')
const table = page => page.getByRole('region', { name: '数据表格，可横向滚动' })
const pager = page => page.getByRole('navigation', { name: '数据集分页' })
const filter = (page, name) => page.locator(`[data-filter="${name}"] input`)
const posts = api => api.requests.filter(request => request.method === 'POST')
const queries = api => api.requests.filter(request => request.path === recordsPath)
const ok = body => ({ status: 200, body })
const exactValue = '12345678901234567890.123456789012345678'

function deferred() {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}

// These are HTTP response facts, not proof of database writes or upstream collection.
function recordRows() {
  return syntheticRecords('daily', 61).map((row, index) => ({ ...row,
    ts_code: '000001.SZ', trade_date: new Date(Date.UTC(2026, 5, 8 + index)).toISOString().slice(0, 10),
    amount: exactValue,
  }))
}

function rangeBatch(index, overrides = {}) {
  const start = ['2026-06-01', '2026-07-01', '2026-08-01'][index]
  const end = ['2026-06-30', '2026-07-31', '2026-08-07'][index]
  return batch(index, { rangeStart: start, rangeEnd: end,
    sourceParams: { ts_code: '000001.SZ', start_date: start.replaceAll('-', ''), end_date: end.replaceAll('-', '') }, ...overrides })
}

async function setup(page, width, overrides = {}) {
  await page.setViewportSize({ width, height: 1000 })
  const errors = [], demoRequests = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('request', request => { if (new URL(request.url()).pathname.startsWith('/src/demos/')) demoRequests.push(request.url()) })
  const api = await installApi(page, overrides)
  return { ...api, errors, demoRequests }
}

function clean(api) {
  expect(api.unexpected).toEqual([])
  expect(api.errors).toEqual([])
  expect(api.demoRequests).toEqual([])
  for (const request of api.requests) expect(request.requestId).toMatch(/^[\da-f-]{36}$/i)
}

function recordsResponse(request, rows) {
  const { tsCode, tradeDateFrom, tradeDateTo } = request.query
  const matching = rows.filter(row => (!tsCode || row.ts_code === tsCode)
    && (!tradeDateFrom || row.trade_date >= tradeDateFrom) && (!tradeDateTo || row.trade_date <= tradeDateTo))
  const page = Number(request.query.page), pageSize = Number(request.query.pageSize)
  return ok({ requestId: request.requestId, pluginId: 'tushare_pro', apiName: 'daily', page, pageSize,
    totalElements: matching.length, totalPages: Math.ceil(matching.length / pageSize),
    columns: [...EXPECTED.get('daily').columns.map(column => column.name), 'source_plugin', 'source_api', 'ingested_at'],
    items: matching.slice((page - 1) * pageSize, page * pageSize),
  })
}

async function fillDownload(page, mode) {
  await selectDownloadApi(page, 'daily')
  await page.locator(`[data-mode="${mode}"]`).click()
  await page.locator('[name="ts_code"]').fill(' 000001.sz ')
  const dates = mode === 'SINGLE' ? { trade_date: '2026-08-07' } : { start_date: '2026-06-01', end_date: '2026-08-07' }
  for (const [name, value] of Object.entries(dates)) await page.locator(`[name="${name}"]`).fill(value)
}

async function openDatasets(page) {
  await page.getByRole('link', { name: '数据查看', exact: true }).click()
  await page.getByRole('combobox', { name: '数据集', exact: true }).fill('daily')
  await page.getByRole('option').filter({ has: page.getByText('daily', { exact: true }) }).click()
  await expect(page.locator('.el-select-dropdown:visible')).toHaveCount(0)
}

async function query(page, from = '2026-08-07', to = '2026-08-07') {
  await filter(page, 'tsCode').fill('000001.SZ')
  await filter(page, 'tradeDateFrom').fill(from)
  await filter(page, 'tradeDateTo').fill(to)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(table(page)).toBeVisible()
}

async function noOverflow(page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  if (await dialog(page).count()) expect(await dialog(page).evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true)
}

async function scrollTable(page) {
  const scroll = table(page).locator('.el-scrollbar__wrap')
  await scroll.evaluate(el => { el.scrollLeft = el.scrollWidth })
  await expect.poll(() => scroll.evaluate(el => el.scrollLeft)).toBeGreaterThan(0)
  await expect(table(page).locator('.el-table__body tr').first().locator('td').last()).toBeInViewport()
  await expect(table(page)).toContainText('tushare_pro')
  await expect(table(page)).toContainText('2026-08-07 20:34:56')
  await noOverflow(page)
  await scroll.evaluate(el => { el.scrollLeft = 0 })
}

async function scrollDialog(page) {
  await dialog(page).evaluate(el => { el.scrollTop = el.scrollHeight })
  await expect.poll(() => dialog(page).evaluate(el => el.scrollTop)).toBeGreaterThan(0)
  await expect(dialog(page).locator('.download-batch-table')).toBeInViewport()
  await noOverflow(page)
  await dialog(page).evaluate(el => { el.scrollTop = 0 })
}

for (const width of [1024, 1280, 1440]) {
  test(`${width}px：单次提交到数据分页、主题和历史导航的连续旅程`, async ({ page }) => {
    const api = await setup(page, width, {
      'POST /api/v1/download-tasks': request => {
        const response = successDownload(request)
        response.task.counts = { ...response.task.counts, sourceRows: 1n, insertedRows: 1n, updatedRows: 0n }
        return response
      },
      [`GET ${recordsPath}`]: request => recordsResponse(request, recordRows()),
    })
    await page.goto('/downloads')
    await fillDownload(page, 'SINGLE')
    await page.getByRole('button', { name: '开始下载', exact: true }).click()
    await expect(page.locator('.download-feedback')).toContainText('任务已接收')
    expect(posts(api)).toHaveLength(1)
    expect(posts(api)[0].body).toEqual({ submissionId: expect.stringMatching(/^[\da-f-]{36}$/), pluginId: 'tushare_pro', apiName: 'daily', mode: 'SINGLE', params: { ts_code: '000001.SZ', trade_date: '20260807' } })
    const href = await page.locator('.download-feedback').getByRole('link', { name: '查看任务', exact: true }).getAttribute('href')
    const link = recent(page).locator(`a[href="${href}"]`)
    await expect(link).toBeVisible()
    await link.click()
    await expect(page).toHaveURL(href)
    await expect(dialog(page).locator('[data-task-status]')).toHaveText('已成功')
    await expect(dialog(page).getByLabel('规范化参数')).toContainText('trade_date=20260807')
    await scrollDialog(page)
    await dialog(page).getByRole('button', { name: '关闭任务详情' }).click()
    await page.goForward()
    await expect(page).toHaveURL(href)
    await expect(dialog(page)).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.locator('[name="ts_code"]')).toHaveValue(' 000001.sz ')
    await expect(page.locator('[name="trade_date"]')).toHaveValue('2026-08-07')
    await noOverflow(page)

    await openDatasets(page)
    await query(page)
    await expect(pager(page).getByRole('status')).toHaveText('共 1 条，第 1 / 1 页')
    await expect(table(page)).toContainText(exactValue)
    await expect(table(page)).toContainText('2026-08-07')
    await scrollTable(page)
    // Broaden the submitted date range before testing real multi-page results.
    await query(page, '2026-06-01')
    await expect(pager(page).getByRole('status')).toHaveText('共 61 条，第 1 / 2 页')
    await filter(page, 'tsCode').fill('000002.SZ')
    await filter(page, 'tradeDateFrom').fill('2026-07-01')
    await pager(page).getByRole('button', { name: /下一页/ }).click()
    await expect(pager(page).getByRole('status')).toHaveText('共 61 条，第 2 / 2 页')
    expect(queries(api).map(request => request.query)).toEqual([
      { tsCode: '000001.SZ', tradeDateFrom: '2026-08-07', tradeDateTo: '2026-08-07', page: '1', pageSize: '50' },
      { tsCode: '000001.SZ', tradeDateFrom: '2026-06-01', tradeDateTo: '2026-08-07', page: '1', pageSize: '50' },
      { tsCode: '000001.SZ', tradeDateFrom: '2026-06-01', tradeDateTo: '2026-08-07', page: '2', pageSize: '50' },
    ])
    const result = await table(page).innerText()
    await page.getByRole('link', { name: '外观设置', exact: true }).click()
    await page.getByRole('button', { name: '使用主题色 #28745a' }).click()
    await noOverflow(page)
    await page.getByRole('link', { name: '数据查看', exact: true }).click()
    await expect(filter(page, 'tsCode')).toHaveValue('000002.SZ')
    await expect(filter(page, 'tradeDateFrom')).toHaveValue('2026-07-01')
    await expect(pager(page).getByRole('status')).toHaveText('共 61 条，第 2 / 2 页')
    expect(await table(page).innerText()).toBe(result)
    await expect(table(page)).toContainText(exactValue)
    expect(queries(api)).toHaveLength(3)
    await page.reload()
    expect(await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--tensor-accent').trim())).toBe('#28745a')
    await page.getByRole('link', { name: '数据下载', exact: true }).click()
    await recent(page).locator(`a[href="${href}"]`).click()
    await expect(dialog(page).locator('[data-task-status]')).toHaveText('已成功')
    expect(posts(api)).toHaveLength(1)
    clean(api)
  })

  test(`${width}px：批量部分失败、跨入口去重、恢复到数据查询的连续旅程`, async ({ page }) => {
    const state = { created: null, postGate: deferred(), getGate: null, rows: [] }
    const completedCounts = { ...task().counts, insertedRows: 15n, updatedRows: 0n }
    const interrupted = task({ taskId: resumeId, status: 'INTERRUPTED', version: 9007199254740997n,
      params: { ts_code: '000001.SZ', start_date: '20260601', end_date: '20260807' },
      extraction: { policyVersion: 'saved-response-only-v1', ruleKind: 'RESPONSE_ONLY' }, canResume: true,
      counts: { ...task().counts, succeededBatches: 2n, pendingBatches: 1n, sourceRows: 10n, insertedRows: 10n, updatedRows: 0n },
      lastError: { code: 'EXECUTION_INTERRUPTED', message: '执行已中断' },
    })
    const batches = [rangeBatch(0), rangeBatch(1), rangeBatch(2, { status: 'FAILED', sourceRows: 0n, insertedRows: 0n, error: { code: 'SOURCE_TIMEOUT', message: '上游请求超时' } })]
    const api = await setup(page, width, {
      'POST /api/v1/download-tasks': request => {
        const response = successDownload(request)
        state.created = task({ taskId: TASK_ID, submissionId: request.body.submissionId, params: request.body.params,
          status: 'RUNNING', canRetry: false, finishedAt: null,
          counts: { ...task().counts, succeededBatches: 2n, runningBatches: 1n, sourceRows: 10n, insertedRows: 10n, updatedRows: 0n } })
        return { ...response, task: state.created, body: { ...response.body, taskId: TASK_ID }, headers: { Location: taskPath(TASK_ID) } }
      },
      'GET /api/v1/download-tasks': async ({ query }) => {
        if (state.getGate) await state.getGate.promise
        return ok({ page: Number(query.page), pageSize: Number(query.pageSize), total: state.created ? 2n : 1n, items: [...(state.created ? [state.created] : []), interrupted] })
      },
      [`GET ${taskPath(TASK_ID)}`]: async () => { if (state.getGate) await state.getGate.promise; return ok(state.created) },
      [`GET ${taskPath(TASK_ID)}/batches`]: () => ok({ page: 1, pageSize: 20, total: 3n, items: batches }),
      [`POST ${taskPath(TASK_ID)}/retry`]: async request => {
        expect(request.rawBody).toBe('{"expectedVersion":9007199254740993}')
        await state.postGate.promise
        return { status: 202, body: { requestId: request.requestId, taskId: TASK_ID, status: 'QUEUED', version: 9007199254740994n, createdAt: state.created.createdAt } }
      },
      [`GET ${taskPath(resumeId)}`]: () => ok(interrupted),
      [`GET ${taskPath(resumeId)}/batches`]: () => ok({ page: 1, pageSize: 20, total: 3n, items: [rangeBatch(0), rangeBatch(1), rangeBatch(2, { status: interrupted.status === 'SUCCEEDED' ? 'SUCCEEDED' : 'PENDING', attemptCount: interrupted.status === 'SUCCEEDED' ? 1 : 0, sourceRows: interrupted.status === 'SUCCEEDED' ? 5n : 0n, insertedRows: interrupted.status === 'SUCCEEDED' ? 5n : 0n })] }),
      [`POST ${taskPath(resumeId)}/resume`]: request => {
        expect(request.rawBody).toBe('{"expectedVersion":9007199254740997}')
        Object.assign(interrupted, { status: 'SUCCEEDED', version: 9007199254740998n, canResume: false, lastError: null, counts: completedCounts })
        state.rows = recordRows()
        return { status: 202, body: { requestId: request.requestId, taskId: resumeId, status: 'QUEUED', version: interrupted.version, createdAt: interrupted.createdAt } }
      },
      [`GET ${recordsPath}`]: request => recordsResponse(request, state.rows),
    })
    await page.goto('/downloads')
    await fillDownload(page, 'RANGE')
    await page.getByRole('button', { name: '开始批量下载', exact: true }).click()
    await expect(page.locator('.download-feedback')).toContainText('任务已接收')
    expect(posts(api)[0].body).toMatchObject({ pluginId: 'tushare_pro', apiName: 'daily', mode: 'RANGE', params: { ts_code: '000001.SZ', start_date: '20260601', end_date: '20260807' } })
    const createdRow = recent(page).locator('article').filter({ has: page.locator(`a[href="/downloads/tasks/${TASK_ID}"]`) })
    await createdRow.getByRole('link').click()
    await expect(dialog(page).locator('[data-task-status]')).toHaveText('运行中')
    Object.assign(state.created, { status: 'PARTIAL_FAILED', canRetry: true, finishedAt: task().finishedAt,
      counts: { ...state.created.counts, runningBatches: 0n, failedBatches: 1n }, lastError: { code: 'SOURCE_TIMEOUT', message: '上游请求超时' } })
    await dialog(page).getByRole('button', { name: '刷新状态' }).click()
    await expect(dialog(page).locator('[data-task-status]')).toHaveText('部分失败')
    await expect(dialog(page).locator('ol > li')).toHaveCount(3)
    await expect(dialog(page).locator('ol > li').last()).toContainText('上游请求超时')
    const successfulFacts = await Promise.all([0, 1].map(index => dialog(page).locator('ol > li').nth(index).locator('.batch-meta span').allTextContents()))
    expect(successfulFacts).toEqual([
      ['尝试次数 1', '来源行数 5', '新增记录次数 5', '更新记录次数 0'],
      ['尝试次数 1', '来源行数 5', '新增记录次数 5', '更新记录次数 0'],
    ])
    await scrollDialog(page)
    await dialog(page).getByRole('button', { name: '重试任务', exact: true }).click()
    await expect.poll(() => posts(api).length).toBe(2)
    await dialog(page).getByRole('button', { name: '关闭任务详情' }).click()
    await expect(createdRow.getByRole('button', { name: '重试 daily', exact: true })).toBeDisabled()
    await createdRow.getByRole('link').click()
    await expect(dialog(page).getByRole('button', { name: '重试任务', exact: true })).toBeDisabled()
    state.getGate = deferred()
    const refreshed = page.waitForRequest(request => request.method() === 'GET' && new URL(request.url()).pathname === taskPath(TASK_ID))
    const accepted = page.waitForResponse(response => response.request().method() === 'POST' && response.url().endsWith('/retry'))
    state.postGate.resolve()
    expect((await accepted).status()).toBe(202)
    await refreshed
    // An accepted POST is not the new task state; GET remains the authority.
    await expect(dialog(page).locator('[data-task-status]')).toHaveText('部分失败')
    Object.assign(state.created, { status: 'SUCCEEDED', version: 9007199254740994n, canRetry: false, lastError: null, counts: completedCounts })
    batches[2] = rangeBatch(2, { attemptCount: 2 })
    state.getGate.resolve()
    await expect(dialog(page).locator('[data-task-status]')).toHaveText('已成功')
    await dialog(page).getByRole('button', { name: '刷新状态' }).click()
    await expect(dialog(page).locator('ol > li').last()).toContainText('尝试次数 2')
    for (const [index, facts] of successfulFacts.entries()) {
      expect(await dialog(page).locator('ol > li').nth(index).locator('.batch-meta span').allTextContents()).toEqual(facts)
    }
    for (const row of await dialog(page).locator('ol > li').all()) await expect(row).toContainText('来源行数 5')
    await dialog(page).getByRole('button', { name: '关闭任务详情' }).click()
    await expect(createdRow.locator('[data-task-status]')).toHaveText('已成功')
    await recent(page).locator(`a[href="/downloads/tasks/${resumeId}"]`).click()
    await expect(dialog(page).locator('[data-task-status]')).toHaveText('已中断')
    await dialog(page).getByRole('button', { name: '恢复任务', exact: true }).click()
    await expect(dialog(page).locator('[data-task-status]')).toHaveText('返回记录已采集')
    await expect(dialog(page)).toContainText('数据完整性未确认，可能存在上游截断')
    await expect(dialog(page).getByLabel('已结束批次进度')).toHaveAttribute('value', '100')
    await dialog(page).getByRole('button', { name: '关闭任务详情' }).click()
    await openDatasets(page)
    await query(page)
    await expect(pager(page).getByRole('status')).toHaveText('共 1 条，第 1 / 1 页')
    await expect(table(page)).toContainText(exactValue)
    await scrollTable(page)
    await page.getByRole('link', { name: '外观设置', exact: true }).click()
    await page.getByRole('button', { name: '使用主题色 #745942' }).click()
    await page.getByRole('link', { name: '数据下载', exact: true }).click()
    await recent(page).locator(`a[href="/downloads/tasks/${resumeId}"]`).click()
    await expect(dialog(page).locator('[data-task-status]')).toHaveText('返回记录已采集')
    await expect(dialog(page)).toContainText('tushare_pro / daily')
    await expect(dialog(page)).toContainText('已结束 3 / 当前计划 3 批')
    await expect(dialog(page)).toContainText('数据完整性未确认')
    await dialog(page).getByText('任务记录', { exact: true }).click()
    await expect(dialog(page)).toContainText('saved-response-only-v1')
    await scrollDialog(page)
    expect(posts(api).map(request => request.path)).toEqual(['/api/v1/download-tasks', `${taskPath(TASK_ID)}/retry`, `${taskPath(resumeId)}/resume`])
    clean(api)
  })
}

// Equivalent to the pinned Demo's initial task states; the app still uses HTTP.
function comparisonTasks() {
  return [['daily', 'SUCCEEDED', 1n], ['stock_basic', 'SUCCEEDED', 24n], ['daily_basic', 'PARTIAL_FAILED', 24n],
    ['weekly', 'SUCCEEDED', 1n], ['adj_factor', 'INTERRUPTED', 12n]].map(([apiName, status, rows], index) => {
    const range = ['PARTIAL_FAILED', 'INTERRUPTED'].includes(status)
    return task({ taskId: `22222222-2222-4222-8222-${String(index + 1).padStart(12, '0')}`, apiName, status,
      mode: range ? 'RANGE' : 'SINGLE', extraction: range ? { policyVersion: 'comparison-v1', ruleKind: apiName === 'adj_factor' ? 'RESPONSE_ONLY' : 'CONFIRMED_ROW_LIMIT' } : null,
      params: range ? { ts_code: '000001.SZ', start_date: '20260601', end_date: '20260807' }
        : apiName === 'stock_basic' ? { list_status: 'L' } : { ts_code: '000001.SZ', trade_date: '20260807' },
      canRetry: status === 'PARTIAL_FAILED', canResume: status === 'INTERRUPTED',
      counts: { ...task().counts, totalBatches: range ? 3n : 1n, succeededBatches: range ? status === 'INTERRUPTED' ? 1n : 2n : 1n,
        failedBatches: status === 'PARTIAL_FAILED' ? 1n : 0n, pendingBatches: status === 'INTERRUPTED' ? 2n : 0n,
        sourceRows: rows, insertedRows: rows, updatedRows: 0n },
    })
  })
}

async function screenshot(page, name) {
  await page.addStyleTag({ content: '* { transition: none !important; animation: none !important; }' })
  await expect(page.locator('.el-select-dropdown:visible')).toHaveCount(0)
  await page.mouse.move(0, 0)
  await noOverflow(page)
  await page.screenshot({ path: `${output}/${name}.png`, fullPage: true, animations: 'disabled' })
}

async function compareStyle(actual, reference, properties) {
  const styles = locator => locator.evaluate((el, properties) => {
    const css = getComputedStyle(el)
    return Object.fromEntries(properties.map(key => [key, css[key]]))
  }, properties)
  expect(await styles(actual)).toEqual(await styles(reference))
}

for (const width of [1024, 1280, 1440]) {
  test(`${width}px：三主页面和详情与固定 Studio Demo 的最终对照`, async ({ page, context }) => {
    const tasks = comparisonTasks()
    const sample = JSON.parse(readFileSync(new URL('../../docs/data-template/daily.json', import.meta.url), 'utf8')).data.slice(0, 24)
    const rows = sample.map(row => ({ ...Object.fromEntries(Object.entries(row).map(([key, value]) => [key, value === null ? null : String(value)])),
      trade_date: row.trade_date.replace(/^(\d{4})(\d{2})(\d{2})$/, '$1-$2-$3'), source_plugin: 'tushare_pro', source_api: 'daily', ingested_at: '2026-08-07T12:34:56Z' }))
    const api = await setup(page, width, {
      'GET /api/v1/download-tasks': () => ok({ page: 1, pageSize: 20, total: 5n, items: tasks }),
      [`GET ${taskPath(tasks[0].taskId)}`]: () => ok(tasks[0]),
      [`GET ${taskPath(tasks[0].taskId)}/batches`]: () => ok({ page: 1, pageSize: 20, total: 1n, items: [batch(0, {
        rangeStart: null, rangeEnd: null, sourceParams: tasks[0].params, sourceRows: 1n, insertedRows: 1n,
      })] }),
      [`GET ${recordsPath}`]: request => recordsResponse(request, rows),
    })
    const demo = await context.newPage()
    await demo.setViewportSize({ width, height: 1000 })
    await demo.goto('/ui-demos.html')
    await page.goto('/downloads')
    await fillDownload(page, 'SINGLE')
    await page.locator('[name="ts_code"]').fill('000001.SZ')
    await page.getByRole('searchbox', { name: '搜索接口' }).fill('')
    await expect(recent(page).locator('article')).toHaveCount(5)
    for (const selector of ['.top-nav', '.studio-layout', '.catalog-panel']) {
      await compareStyle(page.locator(selector), demo.locator(selector), ['width', 'fontSize', 'color'])
    }
    await screenshot(page, `downloads-${width}`)
    await screenshot(demo, `demo-downloads-${width}`)

    await recent(page).locator(`a[href="/downloads/tasks/${tasks[0].taskId}"]`).click()
    await expect(dialog(page).locator('[data-task-status]')).toHaveText('已成功')
    await expect(dialog(page).locator('ol > li')).toHaveCount(1)
    await demo.locator('.studio-tasks .task-name').first().click()
    await compareStyle(dialog(page), dialog(demo), ['width', 'paddingTop', 'paddingLeft', 'backgroundColor'])
    await screenshot(page, `detail-${width}`)
    await screenshot(demo, `demo-detail-${width}`)
    await scrollDialog(page)
    for (const surface of [page, demo]) await dialog(surface).getByRole('button', { name: '关闭任务详情' }).click()

    await openDatasets(page)
    await page.getByRole('button', { name: '查询', exact: true }).click()
    await expect(table(page).locator('.el-table__body tbody tr')).toHaveCount(24)
    await pager(page).getByRole('combobox').focus()
    await page.keyboard.press('ArrowDown')
    await page.keyboard.press('Home')
    await page.keyboard.press('Enter')
    await expect(table(page).locator('.el-table__body tbody tr')).toHaveCount(20)
    await demo.getByRole('button', { name: '数据查看', exact: true }).click()
    expect(await table(page).locator('.el-table__body tr').first().locator('td').first().innerText()).toBe(sample[0].ts_code)
    await compareStyle(table(page), demo.locator('.data-scroll'), ['width', 'borderRadius', 'borderTopColor'])
    await screenshot(page, `datasets-${width}`)
    await screenshot(demo, `demo-datasets-${width}`)
    await scrollTable(page)

    await page.getByRole('link', { name: '外观设置', exact: true }).click()
    await demo.getByRole('button', { name: '外观设置', exact: true }).click()
    for (const selector of ['.settings-intro', '.accent-setting', '.color-options']) {
      await compareStyle(page.locator(selector), demo.locator(selector), ['width', 'fontSize', 'color'])
    }
    await screenshot(page, `settings-${width}`)
    await screenshot(demo, `demo-settings-${width}`)
    clean(api)
    await demo.close()
  })
}
