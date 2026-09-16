import { expect, test } from '@playwright/test'
import path from 'node:path'
import { installApi, successDownload } from './ui-redesign.fixtures.js'
import { selectDownloadApi } from './catalog-helpers.js'

const output = path.resolve('node_modules/.cache/studio-t04')
const storageKey = 'tensor.downloadTasks.pending.v1'
const submitPath = 'POST /api/v1/download-tasks'
const posts = api => api.requests.filter(request => request.method === 'POST')
const feedback = page => page.locator('.download-feedback')
const action = page => page.locator('.download-action')

async function fill(page, mode = 'RANGE') {
  await selectDownloadApi(page, 'daily')
  await page.locator(`[data-mode="${mode}"]`).click()
  await page.locator('[name="ts_code"]').fill(' 000001.sz ')
  const values = mode === 'RANGE'
    ? { start_date: '2026-09-01', end_date: '2026-09-02' }
    : { trade_date: '2026-09-01' }
  for (const [name, value] of Object.entries(values)) await page.locator(`[name="${name}"]`).fill(value)
}

async function noOverflow(page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  for (const button of await feedback(page).getByRole('button').all()) {
    expect(await button.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true)
  }
}

async function metrics(locator) {
  return locator.evaluate(el => {
    const rect = el.getBoundingClientRect()
    const css = getComputedStyle(el)
    return { width: rect.width, height: rect.height, fontSize: css.fontSize, fontWeight: css.fontWeight,
      color: css.color, background: css.backgroundColor, gap: css.gap, borderRadius: css.borderRadius }
  })
}

for (const width of [1024, 1280, 1440]) {
  test(`${width}px：Studio 提交按钮与 Demo 对照`, async ({ page, context }) => {
    await page.setViewportSize({ width, height: 1100 })
    const api = await installApi(page)
    await page.goto('/downloads')
    await fill(page, 'SINGLE')
    await page.getByRole('searchbox', { name: '搜索接口' }).fill('')
    const demo = await context.newPage()
    await demo.setViewportSize({ width, height: 1100 })
    await demo.goto('/ui-demos.html')
    await demo.locator('.parameter-grid input[type="text"]').fill('000001.SZ')
    await demo.locator('.parameter-grid input[type="date"]').fill('2026-09-01')
    for (const surface of [page, demo]) {
      await surface.addStyleTag({ content: '* { transition: none !important; animation: none !important; }' })
      await surface.locator('input[type="date"]').last().blur()
      await surface.mouse.move(0, 0)
    }
    for (const mode of ['SINGLE', 'RANGE']) {
      await page.locator(`[data-mode="${mode}"]`).click()
      await demo.locator(`[data-mode="${mode}"]`).click()
      if (mode === 'RANGE') {
        await demo.locator('[name="start_date"]').fill('2026-09-01')
        await demo.locator('[name="end_date"]').fill('2026-09-02')
      }
      await page.locator(`[data-mode="${mode}"]`).blur()
      await demo.locator('[data-mode="RANGE"]').blur()
      await demo.locator('input[type="date"]').last().blur()
      const actual = await metrics(action(page))
      const reference = await metrics(demo.locator('.form-actions .button'))
      expect(actual).toEqual(reference)
      await expect(action(page)).toHaveText(mode === 'RANGE' ? '开始批量下载' : '开始下载')
    }
    await expect(feedback(page)).toHaveCount(0)
    await page.keyboard.press('Tab')
    await action(page).focus()
    expect(await action(page).evaluate(el => getComputedStyle(el).outlineStyle)).toBe('solid')
    await action(page).blur()
    await noOverflow(page)
    if (width !== 1280) {
      for (const surface of [page, demo]) {
        await surface.locator('.studio-form input[type="text"]').fill('000001.SZ')
        await surface.locator('[name="start_date"]').fill('2026-09-01')
        await surface.locator('[name="end_date"]').fill('2026-09-02')
        await surface.locator('[name="end_date"]').blur()
      }
      await page.screenshot({ path: `${output}/submit-${width}.png`, fullPage: true })
      await demo.screenshot({ path: `${output}/demo-submit-${width}.png`, fullPage: true })
    }
    expect(api.unexpected).toEqual([])
    await demo.close()
  })
}

for (const mode of ['SINGLE', 'RANGE']) {
  test(`${mode}：慢提交锁定快照，双击只创建一次，接收后刷新列表`, async ({ page }) => {
    let release
    const gate = new Promise(resolve => { release = resolve })
    const api = await installApi(page, { [submitPath]: async request => { await gate; return successDownload(request) } })
    await page.setViewportSize({ width: 1440, height: 1100 })
    await page.goto('/downloads')
    await fill(page, mode)
    await action(page).click({ clickCount: 2 })
    try {
      await expect(feedback(page).getByRole('heading')).toHaveText('正在提交任务')
      await expect(action(page)).toHaveText('正在创建…')
      await expect(action(page)).toBeDisabled()
      await expect(page.locator('#download-data-source')).toBeDisabled()
      await expect(page.getByRole('searchbox', { name: '搜索接口' })).toBeDisabled()
      for (const control of await page.locator('.form-parameters input, [data-mode]').all()) await expect(control).toBeDisabled()
      await expect(page.getByLabel('原提交参数')).toContainText('ts_code=000001.SZ')
      expect(posts(api)).toHaveLength(1)
      const request = posts(api)[0].body
      expect(request).toMatchObject({ pluginId: 'tushare_pro', apiName: 'daily', mode,
        params: mode === 'RANGE' ? { ts_code: '000001.SZ', start_date: '20260901', end_date: '20260902' } : { ts_code: '000001.SZ', trade_date: '20260901' } })
      expect(await page.evaluate(key => JSON.parse(sessionStorage.getItem(key)).request, storageKey)).toEqual(request)
    } finally { release() }
    await expect(feedback(page).getByRole('heading')).toHaveText('任务已接收')
    await expect(page.locator('.download-task-list [data-task-status]')).toHaveCount(1)
    await expect(action(page)).toBeEnabled()
    await expect(feedback(page).getByRole('link', { name: '查看任务' })).toHaveAttribute('href', /\/downloads\/tasks\/[\da-f-]{36}$/)
    expect(await page.evaluate(key => sessionStorage.getItem(key), storageKey)).toBeNull()
    await noOverflow(page)
    if (mode === 'RANGE') {
      await action(page).blur()
      await page.mouse.move(0, 0)
      await page.screenshot({ path: `${output}/accepted-1440.png`, fullPage: true })
      await page.keyboard.press('Tab')
      const link = feedback(page).getByRole('link', { name: '查看任务' })
      await link.focus()
      await expect(link).toBeFocused()
      expect(await link.evaluate(el => getComputedStyle(el).outlineStyle)).toBe('solid')
      await page.screenshot({ path: `${output}/accepted-focus-1440.png`, fullPage: true })
    }
    expect(api.unexpected).toEqual([])
  })
}

for (const found of [true, false]) {
  test(`断网刷新后${found ? '找回原任务' : '空查询后使用原参数重新确认'}`, async ({ page }) => {
    let original
    let saved
    const api = await installApi(page, { [submitPath]: request => successDownload(request),
      ...(found ? { 'GET /api/v1/download-tasks': request => ({ status: 200, body: {
        page: 1, pageSize: 20, total: saved ? 1 : 0, items: saved ? [saved] : [],
      } }) } : {}),
    })
    await page.route('**/api/v1/download-tasks', async route => {
      if (route.request().method() === 'POST' && !original) {
        original = route.request().postDataJSON()
        saved = successDownload({ body: original, requestId: route.request().headers()['x-request-id'] }).task
        await route.abort('failed')
      } else await route.fallback()
    })
    await page.setViewportSize({ width: 1024, height: 1100 })
    await page.goto('/downloads')
    await fill(page)
    await action(page).click()
    await expect(feedback(page).getByRole('heading')).toHaveText('提交结果尚未确认')
    await expect(action(page)).toBeDisabled()
    await page.reload()
    if (found) {
      await expect(feedback(page).getByRole('heading')).toHaveText('任务已接收')
      await expect(feedback(page)).toContainText(saved.taskId)
      expect(posts(api)).toHaveLength(0)
      expect(await page.evaluate(key => sessionStorage.getItem(key), storageKey)).toBeNull()
    } else {
      await expect(feedback(page).getByRole('heading')).toHaveText('提交结果尚未确认')
      await fill(page, 'SINGLE')
      await page.locator('[name="ts_code"]').fill('000002.SZ')
      await expect(action(page)).toBeDisabled()
      await expect(page.getByLabel('原提交参数')).toContainText('ts_code=000001.SZ')
      await expect(page.getByLabel('原提交参数')).toContainText('start_date=20260901')
      expect(posts(api)).toHaveLength(0)
      await noOverflow(page)
      await page.screenshot({ path: `${output}/uncertain-1024.png`, fullPage: true })
      await page.getByRole('button', { name: '重新查找', exact: true }).click()
      await expect(page.getByRole('button', { name: '使用原参数重新确认' })).toBeEnabled()
      await page.getByRole('button', { name: '使用原参数重新确认' }).click()
      await expect(feedback(page).getByRole('heading')).toHaveText('任务已接收')
      expect(posts(api).map(request => request.body)).toEqual([original])
    }
    const lookups = api.requests.filter(request => request.query.submissionId)
    expect(lookups.length).toBeGreaterThan(0)
    expect(lookups.every(request => request.query.submissionId === original.submissionId)).toBe(true)
    expect(api.unexpected).toEqual([])
  })
}

for (const failure of ['READ', 'WRITE', 'REMOVE', 'CORRUPT']) {
  test(`本地记录 ${failure}：保留提交事实并提供可操作恢复`, async ({ page }) => {
    const api = await installApi(page, { [submitPath]: request => successDownload(request) })
    await page.addInitScript(({ key, failure }) => {
      if (failure === 'CORRUPT') sessionStorage.setItem(key, '{broken')
      sessionStorage.setItem('keep.me', 'yes')
      window.storageFailure = failure === 'CORRUPT' ? null : failure
      for (const [method, kind] of [['getItem', 'READ'], ['setItem', 'WRITE'], ['removeItem', 'REMOVE']]) {
        const original = Storage.prototype[method]
        Storage.prototype[method] = function(name, ...args) {
          if (this === sessionStorage && name === key && window.storageFailure === kind) throw new DOMException('blocked', 'SecurityError')
          return original.call(this, name, ...args)
        }
      }
    }, { key: storageKey, failure })
    await page.setViewportSize({ width: 1024, height: 1100 })
    await page.goto('/downloads')
    await fill(page, 'SINGLE')
    if (failure === 'READ' || failure === 'CORRUPT') {
      await expect(action(page)).toBeDisabled()
      expect(posts(api)).toHaveLength(0)
      if (failure === 'CORRUPT') {
        await expect(feedback(page)).toContainText('不会取消服务端任务')
        await page.evaluate(() => { window.storageFailure = 'REMOVE' })
        await page.getByRole('button', { name: '清除损坏的本地记录' }).click()
        await expect(page.getByRole('button', { name: '重新清除记录' })).toBeVisible()
        await expect(action(page)).toBeDisabled()
      }
      await page.evaluate(() => { window.storageFailure = null })
      await page.getByRole('button', { name: failure === 'READ' ? '重新读取' : '重新清除记录' }).click()
      await expect(feedback(page)).toHaveCount(0)
      await expect(action(page)).toBeEnabled()
    } else {
      await action(page).click()
      await expect(feedback(page)).toContainText(failure === 'WRITE' ? '请求尚未发送' : '任务已接收')
      expect(posts(api)).toHaveLength(failure === 'WRITE' ? 0 : 1)
      await noOverflow(page)
      await page.evaluate(() => { window.storageFailure = null })
      if (failure === 'WRITE') {
        await action(page).click()
        await expect(feedback(page).getByRole('heading')).toHaveText('任务已接收')
      } else {
        await page.getByRole('button', { name: '重新清除记录' }).click()
        await expect(feedback(page)).not.toContainText('无法清除')
        await expect(feedback(page).getByRole('link', { name: '查看任务' })).toBeVisible()
      }
      expect(posts(api)).toHaveLength(1)
    }
    expect(await page.evaluate(key => sessionStorage.getItem(key), storageKey)).toBeNull()
    expect(await page.evaluate(() => sessionStorage.getItem('keep.me'))).toBe('yes')
    expect(api.unexpected).toEqual([])
  })
}

test('明确拒绝展示字段和请求 ID，修改后可创建新提交', async ({ page }) => {
  let rejected = false
  const api = await installApi(page, { [submitPath]: request => {
    if (rejected) return successDownload(request)
    rejected = true
    return { status: 400, body: { requestId: request.requestId, code: 'PARAM_INVALID', message: '请求参数无效',
      retryable: false, fieldErrors: [{ field: 'ts_code', message: '请核对证券代码' }] } }
  } })
  await page.goto('/downloads')
  await fill(page, 'SINGLE')
  await action(page).click()
  await expect(feedback(page).getByRole('alert')).toContainText('ts_code：请核对证券代码')
  await expect(feedback(page)).toContainText(posts(api)[0].requestId)
  await expect(feedback(page)).toContainText('请修改上述参数后重新提交')
  await page.locator('[name="ts_code"]').fill('000002.SZ')
  await action(page).click()
  await expect(feedback(page).getByRole('heading')).toHaveText('任务已接收')
  expect(posts(api)).toHaveLength(2)
  expect(posts(api)[1].body.submissionId).not.toBe(posts(api)[0].body.submissionId)
  expect(posts(api)[1].body.params.ts_code).toBe('000002.SZ')
  expect(api.unexpected).toEqual([])
})


test('1024px：长来源、接口与原参数完整换行，不越出反馈区域', async ({ page }) => {
  const api = await installApi(page)
  await page.addInitScript(key => {
    sessionStorage.setItem(key, JSON.stringify({ schemaVersion: 1, request: {
      submissionId: '33333333-3333-4333-8333-333333333333',
      pluginId: 'p'.repeat(64), apiName: 'a'.repeat(64), mode: 'SINGLE',
      params: { query: 'v'.repeat(400) },
    } }))
  }, storageKey)
  await page.setViewportSize({ width: 1024, height: 1100 })
  await page.goto('/downloads')
  await expect(feedback(page).getByRole('heading')).toHaveText('提交结果尚未确认')
  for (const selector of ['.pending-submission', '.download-feedback', '.studio-form']) {
    expect(await page.locator(selector).evaluate(el => el.scrollWidth <= el.clientWidth), selector).toBe(true)
  }
  expect(api.unexpected).toEqual([])
})
