import { expect, test } from '@playwright/test'

import {
  EXPECTED,
  EXPECTED_ROWS,
  apiFailure,
  installApi,
  successDownload,
} from './ui-redesign.fixtures.js'

const STOCK_APIS = EXPECTED_ROWS.filter(([, , , , parameters]) => parameters.includes('ts_code'))
const UNCHANGED_APIS = EXPECTED_ROWS.filter(([, , , , parameters]) => !parameters.includes('ts_code'))

test.use({ baseURL: process.env.TENSOR_UI_BASE_URL || 'http://127.0.0.1:4173' })

test.describe.configure({ timeout: 120_000 })

function downloadPosts(api) {
  return api.requests.filter(({ method, path }) => method === 'POST' && path === '/api/v1/download-tasks')
}

async function selectApi(page, apiName) {
  const input = page.locator('#download-api')
  await input.click()
  await input.fill(apiName)
  const listboxId = await input.getAttribute('aria-controls')
  const option = page.locator(`#${listboxId}`).getByRole('option')
    .filter({ has: page.getByText(apiName, { exact: true }) })
  await expect(option).toHaveCount(1)
  await option.click()
}

async function fillParameter(page, parameter, rawStockCode = ' 000001.sz ') {
  const wrapper = page.locator(`[data-parameter="${parameter.name}"]`)
  const input = wrapper.locator('input')
  if (parameter.type === 'ENUM') {
    const value = parameter.name === 'exchange' ? 'SZSE' : parameter.allowedValues[0]
    await wrapper.locator('.el-select__wrapper').click()
    const listboxId = await input.getAttribute('aria-controls')
    const option = page.locator(`#${listboxId}`).getByRole('option', { name: value, exact: true })
    await option.scrollIntoViewIfNeeded()
    await expect(option).toBeInViewport({ ratio: 1 })
    await option.click()
    await expect(input).toHaveAttribute('aria-expanded', 'false')
    await expect(wrapper.locator('.el-select__selected-item').filter({ hasText: value })).toHaveText(value)
    return value
  }
  if (parameter.type === 'TS_CODE') {
    await input.fill(rawStockCode)
    return '000001.SZ'
  }
  await input.fill('2026-08-07')
  await input.press('Enter')
  return '20260807'
}

async function fillForm(page, definition, rawStockCode) {
  const params = {}
  for (const parameter of definition.parameters) {
    params[parameter.name] = await fillParameter(page, parameter, rawStockCode)
  }
  return params
}

async function openDownloads(page) {
  await page.goto('/downloads')
  await expect(page.locator('#download-api')).toBeEnabled()
}

test('34 stock forms block a blank stock and submit one normalized stock', async ({ page }) => {
  const api = await installApi(page)
  await openDownloads(page)

  for (const [apiName] of STOCK_APIS) {
    const definition = EXPECTED.get(apiName)
    await selectApi(page, apiName)
    const stock = page.locator('[data-parameter="ts_code"]')
    await expect(page.locator('[data-parameter]').first()).toHaveAttribute('data-parameter', 'ts_code')
    await expect(stock.locator('input')).toHaveAttribute('aria-required', 'true')
    const before = downloadPosts(api).length
    await page.getByRole('button', { name: '提交任务' }).click()
    await expect(stock.locator('input')).toHaveAttribute('aria-invalid', 'true')
    await expect(stock.locator('input')).toBeFocused()
    expect(downloadPosts(api)).toHaveLength(before)

    const params = await fillForm(page, definition)
    await page.getByRole('button', { name: '提交任务' }).click()
    await expect(page.getByRole('heading', { name: '任务已接收', level: 2 })).toBeVisible()
    expect(downloadPosts(api).at(-1).body).toEqual({ submissionId: expect.stringMatching(/^[0-9a-f-]{36}$/), mode: 'SINGLE', pluginId: 'tushare_pro', apiName, params })
  }

  expect(downloadPosts(api)).toHaveLength(34)
  expect(api.unexpected).toEqual([])
})

test('six unchanged forms submit their original parameters without a stock', async ({ page }) => {
  const api = await installApi(page)
  await openDownloads(page)

  expect(UNCHANGED_APIS.map(([apiName]) => apiName)).toEqual([
    'trade_cal', 'margin', 'slb_len', 'repurchase', 'new_share', 'index_classify',
  ])
  for (const [apiName] of UNCHANGED_APIS) {
    const definition = EXPECTED.get(apiName)
    await selectApi(page, apiName)
    await expect(page.locator('[data-parameter="ts_code"]')).toHaveCount(0)
    const params = await fillForm(page, definition)
    await page.getByRole('button', { name: '提交任务' }).click()
    await expect(page.getByRole('heading', { name: '任务已接收', level: 2 })).toBeVisible()
    expect(downloadPosts(api).at(-1).body).toEqual({ submissionId: expect.stringMatching(/^[0-9a-f-]{36}$/), mode: 'SINGLE', pluginId: 'tushare_pro', apiName, params })
  }

  expect(downloadPosts(api)).toHaveLength(6)
  expect(downloadPosts(api).find(({ body }) => body.apiName === 'slb_len').body.params)
    .toEqual({ trade_date: '20260807' })
  expect(downloadPosts(api).find(({ body }) => body.apiName === 'index_classify').body.params)
    .toEqual({})
  expect(api.unexpected).toEqual([])
})

test('manual submission confirmation preserves submissionId and normalized stock snapshot', async ({ page }) => {
  let attempt = 0
  const api = await installApi(page, {
    'POST /api/v1/download-tasks': (request) => {
      attempt += 1
      return attempt === 1
        ? apiFailure(request.requestId, 'SOURCE_TIMEOUT')
        : successDownload(request)
    },
  })
  await openDownloads(page)
  await selectApi(page, 'daily')
  await fillForm(page, EXPECTED.get('daily'))
  await page.getByRole('button', { name: '提交任务' }).click()
  await expect(page.getByRole('heading', { name: '提交结果尚未确认', level: 2 })).toBeVisible()

  await page.locator('[data-parameter="ts_code"] input').fill('000002.SZ')
  await page.locator('[data-parameter="trade_date"] input').fill('2026-08-08')
  await page.getByRole('button', { name: '使用原参数重新确认' }).click()
  await expect(page.getByRole('heading', { name: '任务已接收', level: 2 })).toBeVisible()

  expect(downloadPosts(api)[0].body.submissionId).toBe(downloadPosts(api)[1].body.submissionId)
  expect(downloadPosts(api).map(({ body }) => body)).toEqual([
    { submissionId: expect.stringMatching(/^[0-9a-f-]{36}$/), mode: 'SINGLE', pluginId: 'tushare_pro', apiName: 'daily', params: { ts_code: '000001.SZ', trade_date: '20260807' } },
    { submissionId: expect.stringMatching(/^[0-9a-f-]{36}$/), mode: 'SINGLE', pluginId: 'tushare_pro', apiName: 'daily', params: { ts_code: '000001.SZ', trade_date: '20260807' } },
  ])
  expect(api.unexpected).toEqual([])
})
