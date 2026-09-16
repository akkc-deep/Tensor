import { expect, test } from '@playwright/test'
import path from 'node:path'
import { TASK_ID, task, batch, rawJson } from './download-tasks.fixtures.js'
import { installApi } from './ui-redesign.fixtures.js'
import { selectDownloadApi } from './catalog-helpers.js'

const detailPath = `/api/v1/download-tasks/${TASK_ID}`
const output = path.resolve('node_modules/.cache/studio-t07')
const recent = page => page.locator('.download-task-list')
const dialog = page => page.getByRole('dialog')
const ok = body => ({ status: 200, body })
const failure = (requestId, code = 'QUERY_FAILED', message = '任务查询暂时失败，请重新查询。') => ({
  status: code === 'TASK_STATE_CONFLICT' ? 409 : 500,
  body: { requestId, code, message, retryable: code !== 'TASK_STATE_CONFLICT', fieldErrors: [] },
})
function deferred() {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}
async function setup(page, interrupted = false) {
  const state = {
    saved: task({ apiName: 'daily_basic', status: interrupted ? 'INTERRUPTED' : 'PARTIAL_FAILED',
      canRetry: !interrupted, canResume: interrupted,
      counts: { ...task().counts, succeededBatches: 2n, failedBatches: interrupted ? 0n : 1n, pendingBatches: interrupted ? 1n : 0n, sourceRows: 9007199254740995n },
      lastError: { code: interrupted ? 'EXECUTION_INTERRUPTED' : 'SOURCE_TIMEOUT', message: interrupted ? '执行已中断。' : '部分请求超时，请重试未完成工作。' },
    }),
    detailGate: null, postGate: null, failDetail: false, outcome: 'success', posts: [],
  }
  const batches = [batch(0, { sourceRows: 9007199254740993n }), batch(1, { sourceRows: 2n }), batch(2, { status: interrupted ? 'PENDING' : 'FAILED', sourceRows: 0n, attemptCount: interrupted ? 0 : 1 })]
  const api = await installApi(page, {
    'GET /api/v1/download-tasks': ({ query }) => {
      const inGroup = query.statusGroup !== 'ERROR' || ['FAILED', 'PARTIAL_FAILED', 'INTERRUPTED'].includes(state.saved.status)
      return ok({ page: Number(query.page), pageSize: Number(query.pageSize), total: inGroup ? 1n : 0n, items: inGroup ? [state.saved] : [] })
    },
    [`GET ${detailPath}`]: async ({ requestId }) => {
      if (state.detailGate) await state.detailGate.promise
      return state.failDetail ? failure(requestId) : ok(state.saved)
    },
    [`GET ${detailPath}/batches`]: () => ok({ page: 1, pageSize: 20, total: 3n, items: batches }),
  })
  await page.route(`**${detailPath}/*`, async route => {
    const request = route.request()
    if (request.method() !== 'POST') return route.fallback()
    state.posts.push({ action: new URL(request.url()).pathname.split('/').at(-1), body: request.postData() })
    const requestId = request.headers()['x-request-id']
    if (state.postGate) await state.postGate.promise
    const headers = { 'X-Request-Id': requestId, Location: detailPath }
    if (state.outcome === 'conflict') {
      state.saved = { ...state.saved, version: state.saved.version + 1n }
      return route.fulfill({ ...failure(requestId, 'TASK_STATE_CONFLICT', '任务状态或版本已变化。'), headers, contentType: 'application/json', body: rawJson(failure(requestId, 'TASK_STATE_CONFLICT', '任务状态或版本已变化。').body) })
    }
    if (state.outcome === 'reject') return route.fulfill({ status: 409, headers, contentType: 'application/json', body: rawJson({ requestId, code: 'TASK_DEFINITION_CHANGED', message: '任务定义已变化，请重新创建任务。', retryable: false, fieldErrors: [] }) })
    state.saved = { ...state.saved, status: 'SUCCEEDED', lastError: null, canRetry: false, canResume: false, version: state.saved.version + 1n,
      counts: { ...state.saved.counts, succeededBatches: 3n, failedBatches: 0n, pendingBatches: 0n, sourceRows: 9007199254740998n } }
    batches[2] = batch(2, { attemptCount: interrupted ? 1 : 2, sourceRows: 3n })
    if (state.outcome === 'network') return route.abort('failed')
    return route.fulfill({ status: 202, headers, contentType: 'application/json', body: rawJson({ requestId, taskId: TASK_ID, status: 'QUEUED', version: state.saved.version, createdAt: state.saved.createdAt }) })
  })
  return { state, api }
}
async function noOverflow(page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  for (const region of [recent(page), dialog(page)]) {
    if (await region.count()) expect(await region.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true)
  }
}

for (const width of [1024, 1280, 1440]) {
  test(`${width}px：快捷重试先查询精确版本，筛选结果和详情保留成功数据`, async ({ page, context }) => {
    await page.setViewportSize({ width, height: 1000 })
    const { state, api } = await setup(page)
    await page.goto('/downloads')
    await selectDownloadApi(page, 'daily_basic')
    await recent(page).getByRole('button', { name: '需处理', exact: true }).click()
    const retry = recent(page).getByRole('button', { name: '重试 daily_basic', exact: true })
    await expect(retry).toBeVisible()
    await retry.focus()
    await expect(retry).toBeFocused()
    await noOverflow(page)
    await page.screenshot({ path: `${output}/list-${width}.png`, fullPage: true })
    const demo = await context.newPage()
    await demo.setViewportSize({ width, height: 1000 })
    await demo.goto('/ui-demos.html')
    await demo.locator('.studio-tasks').getByRole('button', { name: '需处理', exact: true }).click()
    const demoAction = demo.locator('.studio-tasks .task-trailing .text-button').first()
    for (const prop of ['fontSize', 'color']) expect(await retry.evaluate((el, prop) => getComputedStyle(el)[prop], prop)).toBe(await demoAction.evaluate((el, prop) => getComputedStyle(el)[prop], prop))
    await demo.screenshot({ path: `${output}/demo-list-${width}.png`, fullPage: true })
    await demo.close()
    state.saved.version = 9007199254740997n
    state.detailGate = deferred()
    await retry.click({ clickCount: 2 })
    await expect(retry).toBeDisabled()
    expect(state.posts).toHaveLength(0)
    state.detailGate.resolve()
    await expect(recent(page).locator('.task-action-feedback')).toContainText('重试请求已接收')
    await expect(recent(page).locator('[data-total]')).toHaveText('需处理 · 共 0 个任务')
    expect(state.posts).toEqual([{ action: 'retry', body: '{"expectedVersion":9007199254740997}' }])
    expect(api.requests.filter(r => r.path.endsWith('/batches'))).toHaveLength(0)
    await recent(page).getByRole('link', { name: '查看任务详情' }).click()
    await expect(dialog(page).locator('[data-task-status]')).toHaveText('已成功')
    await expect(dialog(page)).toContainText('9007199254740998')
    const rows = dialog(page).locator('ol > li')
    await expect(rows.first()).toContainText('来源行数 9007199254740993')
    await expect(rows.first()).toContainText('尝试次数 1')
    await expect(rows.nth(2)).toContainText('尝试次数 2')
    await noOverflow(page)
    expect(api.unexpected).toEqual([])
  })
}

test('快捷恢复遇到冲突和查询失败时保留反馈，重新查询后用新版恢复', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 1000 })
  const { state } = await setup(page, true)
  state.outcome = 'conflict'
  state.postGate = deferred()
  await page.goto('/downloads')
  await recent(page).getByRole('button', { name: '恢复 daily_basic' }).click()
  await expect.poll(() => state.posts.length).toBe(1)
  state.failDetail = true
  state.postGate.resolve()
  const feedback = recent(page).locator('.task-action-feedback')
  await expect(feedback).toContainText('任务状态已变化')
  await expect(feedback).toContainText('任务查询暂时失败')
  await expect(feedback).toContainText('请求 ID：')
  await noOverflow(page)
  await page.screenshot({ path: `${output}/conflict-1024.png`, fullPage: true })
  state.failDetail = false
  state.outcome = 'success'
  await feedback.getByRole('button', { name: '重新查询' }).click()
  await expect(feedback).not.toContainText('任务查询暂时失败')
  await recent(page).getByRole('button', { name: '恢复 daily_basic' }).click()
  await expect(feedback).toContainText('恢复请求已接收')
  await expect(recent(page).locator('[data-task-status]')).toHaveText('已成功')
  expect(state.posts).toEqual([
    { action: 'resume', body: '{"expectedVersion":9007199254740993}' },
    { action: 'resume', body: '{"expectedVersion":9007199254740994}' },
  ])
})

for (const outcome of ['network', 'reject']) {
  test(`详情 ${outcome} 后重查，并立即更新列表且不自动重放`, async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 1000 })
    const { state, api } = await setup(page)
    state.outcome = outcome
    await page.goto(`/downloads/tasks/${TASK_ID}`)
    await dialog(page).getByRole('button', { name: '重试任务', exact: true }).click()
    await expect(dialog(page)).toContainText(outcome === 'network' ? '操作结果尚未确认' : '任务定义已变化，请重新创建任务。')
    await expect(dialog(page).locator('[data-task-status]')).toHaveText(outcome === 'network' ? '已成功' : '部分失败')
    await expect(recent(page).locator('[data-task-status]')).toHaveText(outcome === 'network' ? '已成功' : '部分失败')
    await expect.poll(() => api.requests.filter(r => r.path === '/api/v1/download-tasks').length).toBeGreaterThanOrEqual(2)
    expect(state.posts).toHaveLength(1)
    if (outcome === 'network') await page.screenshot({ path: `${output}/uncertain-detail.png`, fullPage: true })
  })
}

test('列表操作在途时打开详情仍禁用，关闭详情后操作完成刷新列表', async ({ page }) => {
  const { state } = await setup(page)
  state.postGate = deferred()
  await page.goto('/downloads')
  await recent(page).getByRole('button', { name: '重试 daily_basic' }).click()
  await expect.poll(() => state.posts.length).toBe(1)
  await recent(page).getByRole('link', { name: '查看任务 daily_basic', exact: true }).click()
  await expect(dialog(page).getByRole('button', { name: '重试任务', exact: true })).toBeDisabled()
  await dialog(page).getByRole('button', { name: '关闭任务详情' }).click()
  state.postGate.resolve()
  await expect(recent(page).locator('[data-task-status]')).toHaveText('已成功')
  expect(state.posts).toHaveLength(1)
})

test('详情操作关闭并重开期间不会重复发送，迟到受理同步列表和新详情', async ({ page }) => {
  const { state } = await setup(page, true)
  state.postGate = deferred()
  await page.goto(`/downloads/tasks/${TASK_ID}`)
  await dialog(page).getByRole('button', { name: '恢复任务', exact: true }).click()
  await expect.poll(() => state.posts.length).toBe(1)
  await dialog(page).getByRole('button', { name: '关闭任务详情' }).click()
  await expect(recent(page).getByRole('button', { name: '恢复 daily_basic' })).toBeDisabled()
  await recent(page).getByRole('link', { name: '查看任务 daily_basic', exact: true }).click()
  await expect(dialog(page).getByRole('button', { name: '恢复任务', exact: true })).toBeDisabled()
  state.postGate.resolve()
  await expect(dialog(page).locator('[data-task-status]')).toHaveText('已成功')
  await expect(recent(page).locator('[data-task-status]')).toHaveText('已成功')
  expect(state.posts).toHaveLength(1)
})

test('陈旧列表的许可被新详情撤销或预查询失败时不发操作', async ({ page }) => {
  const { state } = await setup(page)
  await page.goto('/downloads')
  const retry = recent(page).getByRole('button', { name: '重试 daily_basic' })
  await expect(retry).toBeEnabled()
  state.failDetail = true
  await retry.click()
  await expect(recent(page).locator('.task-action-feedback')).toContainText('任务查询暂时失败')
  expect(state.posts).toHaveLength(0)
  state.failDetail = false
  state.saved = { ...state.saved, canRetry: false }
  await retry.click()
  await expect(recent(page).locator('.task-action-feedback')).toContainText('当前操作不可用')
  expect(state.posts).toHaveLength(0)
  await expect(retry).toHaveCount(0)
})
