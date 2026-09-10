import { expect, test } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

import {
  DATA_SOURCE,
  EXPECTED,
  EXPECTED_ROWS,
  apiFailure,
  installApi,
  successDownload,
  syntheticRecords,
} from './ui-redesign.fixtures.js'

const SOURCE_COLUMNS = ['source_plugin', 'source_api', 'ingested_at']
const SCREENSHOT_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../docs/verification/ISSUE-004-ui-redesign')
const MARKET_LABELS = {
  ts_code: '证券代码', trade_date: '交易日', open: '开盘价', high: '最高价',
  low: '最低价', close: '收盘价', pre_close: '前收盘价', change: '涨跌额',
  vol: '成交量', amount: '成交额', source_plugin: '来源插件', source_api: '来源接口',
  ingested_at: '入库时间',
}

test.describe.configure({ timeout: 60_000 })

function apiRequests(api, predicate = () => true) {
  return api.requests.filter(predicate)
}

function assertApiClean(api) {
  expect(api.unexpected).toEqual([])
  for (const request of api.requests) expect(request.requestId).not.toBe('')
}

function cssRgb(value) {
  const channels = value.match(/[\d.]+/g)?.slice(0, 3).map(Number)
  if (!channels || channels.length !== 3) throw new Error(`Unsupported computed color: ${value}`)
  return channels
}

function contrastRatio(first, second) {
  const luminance = (value) => cssRgb(value).map((channel) => {
    const normalized = channel / 255
    return normalized <= 0.04045 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4
  }).reduce((sum, channel, index) => sum + channel * [0.2126, 0.7152, 0.0722][index], 0)
  const left = luminance(first)
  const right = luminance(second)
  return (Math.max(left, right) + 0.05) / (Math.min(left, right) + 0.05)
}

async function boxes(locators) {
  return Promise.all(locators.map((locator) => locator.boundingBox()))
}

function expectBoxesStable(before, after) {
  expect(after).toHaveLength(before.length)
  before.forEach((rectangle, index) => {
    for (const key of ['x', 'y', 'width', 'height']) {
      expect(Math.abs(rectangle[key] - after[index][key]), `rectangle ${index} ${key}`).toBeLessThanOrEqual(0.5)
    }
  })
}

async function tabTo(page, target, { backwards = false, limit = 40 } = {}) {
  for (let step = 0; step < limit; step += 1) {
    await page.keyboard.press(backwards ? 'Shift+Tab' : 'Tab')
    if (await target.evaluate((element) => element === document.activeElement)) return
  }
  throw new Error(`Keyboard focus did not reach ${await target.evaluate((element) => element.outerHTML.slice(0, 160))}`)
}

async function expectFocusOutline(focused, outlined = focused) {
  await expect(focused).toBeFocused()
  expect(await focused.evaluate((element) => element.matches(':focus-visible'))).toBe(true)
  expect(await outlined.evaluate((element) => {
    const style = getComputedStyle(element)
    return style.outlineStyle !== 'none' && Number.parseFloat(style.outlineWidth) > 0
  })).toBe(true)
}

function pagedDatasetResponse(request, apiName) {
  const pageNumber = Number(request.query.page)
  const pageSize = Number(request.query.pageSize)
  const totalElements = 201
  const count = Math.min(pageSize, Math.max(0, totalElements - ((pageNumber - 1) * pageSize)))
  return {
    status: 200,
    body: {
      requestId: request.requestId,
      pluginId: 'tushare_pro',
      apiName,
      page: pageNumber,
      pageSize,
      totalElements,
      totalPages: Math.ceil(totalElements / pageSize),
      columns: [...EXPECTED.get(apiName).columns.map(({ name }) => name), ...SOURCE_COLUMNS],
      items: syntheticRecords(apiName, count),
    },
  }
}

async function selectCatalog(page, id, apiName) {
  const input = page.locator(`#${id}`)
  await expect(input).toBeEnabled()
  await input.click()
  await input.fill(apiName)
  const listboxId = await input.getAttribute('aria-controls')
  const option = page.locator(`#${listboxId}`).getByRole('option')
    .filter({ has: page.getByText(apiName, { exact: true }) })
  await expect(option).toHaveCount(1)
  await option.click()
  await expect(input).toHaveAttribute('aria-expanded', 'false')
}

async function openDownloads(page) {
  await page.goto('/downloads')
  await expect(page.getByRole('heading', { name: '数据下载', level: 1 })).toBeVisible()
  await expect(page.locator('#download-api')).toBeEnabled()
}

async function chooseDownload(page, apiName) {
  await selectCatalog(page, 'download-api', apiName)
  await expect(page.locator('.download-config-panel')).toContainText(apiName)
}

async function fillMetadataField(page, wrapper, parameter) {
  const input = wrapper.locator('input')
  if (parameter.type === 'ENUM') {
    await wrapper.locator('.el-select__wrapper').click()
    const listboxId = await input.getAttribute('aria-controls')
    await page.locator(`#${listboxId}`).getByRole('option', { name: parameter.allowedValues[0], exact: true }).click()
    return parameter.allowedValues[0]
  }
  const displayValue = ['DATE', 'DATE_RANGE_MEMBER'].includes(parameter.type)
    ? parameter.name === 'start_date' ? '2026-08-03' : '2026-08-07'
    : '000001.SZ'
  await input.fill(displayValue)
  await input.press('Enter')
  await input.blur()
  await expect(input).toHaveValue(displayValue)
  return ['DATE', 'DATE_RANGE_MEMBER'].includes(parameter.type)
    ? displayValue.replaceAll('-', '')
    : displayValue
}

async function submitDownload(page, definition) {
  const wrappers = page.locator('[data-parameter]')
  await expect(wrappers).toHaveCount(definition.parameters.length)
  const params = {}
  for (let index = 0; index < definition.parameters.length; index += 1) {
    const parameter = definition.parameters[index]
    const wrapper = wrappers.nth(index)
    await expect(wrapper).toHaveAttribute('data-parameter', parameter.name)
    await expect(wrapper.locator('label')).toContainText(parameter.label)
    await expect(wrapper.locator('input')).toHaveAttribute('aria-required', parameter.required ? 'true' : 'false')
    if (parameter.type === 'ENUM') await expect(wrapper.locator('.el-select')).toBeVisible()
    else if (['DATE', 'DATE_RANGE_MEMBER'].includes(parameter.type)) await expect(wrapper.locator('.el-date-editor')).toBeVisible()
    params[parameter.name] = await fillMetadataField(page, wrapper, parameter)
  }
  await page.getByRole('button', { name: '开始下载' }).click()
  await expect(page.getByRole('heading', { name: '下载成功', level: 2 })).toBeVisible()
  return params
}

async function openDataset(page, apiName) {
  await page.getByRole('link', { name: /数据查看/ }).click()
  await expect(page).toHaveURL(/\/datasets$/)
  await expect(page.locator('#dataset-select')).toBeEnabled()
  await selectCatalog(page, 'dataset-select', apiName)
  await expect(page.locator('.dataset-setup')).toBeVisible()
}

async function fillDatasetFilters(page, definition) {
  const expectedNames = definition.filters.flatMap(({ field }) => field === 'ts_code'
    ? ['tsCode']
    : field === 'trade_date'
      ? ['tradeDateFrom', 'tradeDateTo']
      : ['annDateFrom', 'annDateTo'])
  const wrappers = page.locator('[data-filter]')
  await expect(wrappers).toHaveCount(expectedNames.length)
  expect(await wrappers.evaluateAll((elements) => elements.map((element) => element.dataset.filter))).toEqual(expectedNames)
  const criteria = {}
  for (const name of expectedNames) {
    if (name.endsWith('To')) continue
    const input = page.locator(`[data-filter="${name}"] input`)
    const value = name === 'tsCode' ? '000001.SZ' : '2026-08-07'
    await input.fill(value)
    await input.press('Enter')
    await input.blur()
    criteria[name] = value
  }
  return criteria
}

function expectedHeaders(definition) {
  return [...definition.columns.map(({ name, label }) => {
    if (!['daily', 'weekly'].includes(definition.apiName)) return label
    if (name === 'pct_chg') return definition.apiName === 'daily' ? '涨跌幅（%）pct_chg' : '涨跌幅（比率）pct_chg'
    return MARKET_LABELS[name] ? `${MARKET_LABELS[name]}${name}` : label
  }), ...SOURCE_COLUMNS.map((name) => ['daily', 'weekly'].includes(definition.apiName) ? `${MARKET_LABELS[name]}${name}` : name)]
}

async function submitDataset(page, definition) {
  const criteria = await fillDatasetFilters(page, definition)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  const table = page.getByRole('region', { name: '数据表格，可横向滚动' })
  await expect(table).toBeVisible()
  const headers = await table.locator('.el-table__header-wrapper th .cell').allTextContents()
  expect(headers.map((value) => value.replaceAll(/\s+/g, ''))).toEqual(expectedHeaders(definition))
  return criteria
}

test.describe('40 项 UI 元数据矩阵', () => {
  for (const [apiName] of EXPECTED_ROWS) {
    test(`${apiName}：参数、筛选、原列顺序与一次请求`, async ({ page }) => {
      const definition = EXPECTED.get(apiName)
      const api = await installApi(page)
      await openDownloads(page)
      await chooseDownload(page, apiName)
      const expectedParams = await submitDownload(page, definition)
      const posts = apiRequests(api, ({ method, path: requestPath }) => method === 'POST' && requestPath === '/api/v1/downloads')
      expect(posts).toHaveLength(1)
      expect(posts[0].body).toEqual({ pluginId: 'tushare_pro', apiName, params: expectedParams })
      await openDataset(page, apiName)
      const expectedCriteria = await submitDataset(page, definition)
      const queries = apiRequests(api, ({ method, path: requestPath }) => method === 'GET' && requestPath.endsWith(`/${apiName}/records`))
      expect(queries).toHaveLength(1)
      expect(queries[0].query).toEqual({ ...expectedCriteria, page: '1', pageSize: '50' })
      assertApiClean(api)
    })
  }
})

test('下载状态、校验、往返缓存与原参数重试', async ({ page }) => {
  let releaseSources
  let releaseDownload
  let metadataAttempt = 0
  let downloadAttempt = 0
  const api = await installApi(page, {
    'GET /api/v1/data-sources': async (request) => {
      metadataAttempt += 1
      if (metadataAttempt === 1) {
        await new Promise((resolve) => { releaseSources = resolve })
        return apiFailure(request.requestId, 'SOURCE_TIMEOUT')
      }
      return { status: 200, body: [DATA_SOURCE] }
    },
    'POST /api/v1/downloads': async (request) => {
      downloadAttempt += 1
      if (downloadAttempt === 1) {
        await new Promise((resolve) => { releaseDownload = resolve })
        return { status: 200, body: successDownload(request.body.apiName, request.requestId) }
      }
      if (downloadAttempt === 2) return apiFailure(request.requestId, 'SOURCE_TIMEOUT')
      if (downloadAttempt === 4) return { status: 200, body: successDownload(request.body.apiName, request.requestId, 'EMPTY') }
      if (downloadAttempt === 5) return apiFailure(request.requestId, 'PARAM_INVALID')
      return { status: 200, body: successDownload(request.body.apiName, request.requestId) }
    },
  })
  await page.goto('/downloads')
  await expect(page.getByRole('heading', { name: '正在加载下载配置' })).toBeVisible()
  releaseSources()
  await expect(page.getByRole('heading', { name: '下载配置加载失败' })).toBeVisible()
  await expect(page.getByText(/请求 ID：/)).toBeVisible()
  await page.getByRole('button', { name: '重新加载' }).click()
  await chooseDownload(page, 'daily')
  await page.getByRole('button', { name: '开始下载' }).click()
  const required = page.locator('[data-parameter="trade_date"] input')
  await expect(required).toHaveAttribute('aria-invalid', 'true')
  await expect(required).toBeFocused()
  expect(apiRequests(api, ({ method }) => method === 'POST')).toHaveLength(0)
  await required.fill('2026-08-07')
  await required.press('Enter')
  await page.getByRole('button', { name: '开始下载' }).click()
  await expect(page.getByRole('heading', { name: '正在下载' })).toBeVisible()
  await expect(page.locator('#download-data-source')).toBeDisabled()
  await expect(page.locator('#download-api')).toBeDisabled()
  await expect(required).toBeDisabled()
  await expect(page.getByRole('button', { name: /开始下载/ })).toBeDisabled()
  await page.getByRole('link', { name: /设置/ }).click()
  releaseDownload()
  await page.getByRole('link', { name: /数据下载/ }).click()
  await expect(required).toHaveValue('2026-08-07')
  await expect(page.getByRole('heading', { name: '下载成功' })).toBeVisible()
  await expect(page.locator('.download-result__counts dd')).toHaveText(['12', '10', '2'])
  expect(apiRequests(api, ({ method }) => method === 'POST')).toHaveLength(1)

  await page.getByRole('button', { name: '开始下载' }).click()
  await expect(page.getByRole('heading', { name: '下载失败' })).toBeVisible()
  await required.fill('2026-08-08')
  await page.getByRole('link', { name: /设置/ }).click()
  await page.getByRole('link', { name: /数据下载/ }).click()
  await expect(page.getByRole('heading', { name: '下载失败' })).toBeVisible()
  await page.getByRole('button', { name: '使用原参数重试' }).click()
  await expect(page.getByRole('heading', { name: '下载成功' })).toBeVisible()
  const posts = apiRequests(api, ({ method }) => method === 'POST')
  expect(posts).toHaveLength(3)
  expect(posts[0].body.params).toEqual({ trade_date: '20260807' })
  expect(posts[1].body.params).toEqual({ trade_date: '20260807' })
  expect(posts[2].body.params).toEqual({ trade_date: '20260807' })

  await page.getByRole('button', { name: '开始下载' }).click()
  await expect(page.getByRole('heading', { name: '下载成功，0 条数据' })).toBeVisible()
  await page.getByRole('button', { name: '开始下载' }).click()
  await expect(page.getByRole('heading', { name: '下载失败' })).toBeVisible()
  await expect(page.getByRole('button', { name: '使用原参数重试' })).toHaveCount(0)

  await chooseDownload(page, 'new_share')
  const start = page.locator('[data-parameter="start_date"] input')
  const end = page.locator('[data-parameter="end_date"] input')
  await page.getByRole('button', { name: '开始下载' }).click()
  expect(apiRequests(api, ({ method }) => method === 'POST')).toHaveLength(5)
  await start.fill('2026-08-08')
  await start.press('Enter')
  await end.fill('2026-08-07')
  await end.press('Enter')
  await page.getByRole('button', { name: '开始下载' }).click()
  await expect(start).toHaveAttribute('aria-invalid', 'true')
  expect(apiRequests(api, ({ method }) => method === 'POST')).toHaveLength(5)
  await end.fill('2026-08-09')
  await end.press('Enter')
  await page.getByRole('button', { name: '开始下载' }).click()
  await expect(page.getByRole('heading', { name: '下载成功' })).toBeVisible()
  const finalPost = apiRequests(api, ({ method }) => method === 'POST').at(-1)
  expect(finalPost.body).toEqual({ pluginId: 'tushare_pro', apiName: 'new_share', params: { start_date: '20260808', end_date: '20260809' } })
  assertApiClean(api)
})

test('查询分页、未提交草稿、设置往返与重试快照', async ({ page }) => {
  let queryAttempt = 0
  let releaseStale
  let releaseSelectionStale
  let releaseAcrossSettings
  const api = await installApi(page, {
    'GET /api/v1/data-sources/tushare_pro/datasets/daily/records': (request) => {
      queryAttempt += 1
      if (queryAttempt === 4) return apiFailure(request.requestId, 'QUERY_FAILED')
      const waitForRelease = queryAttempt === 6
        ? new Promise((resolve) => { releaseStale = resolve })
        : queryAttempt === 7
          ? new Promise((resolve) => { releaseSelectionStale = resolve })
          : queryAttempt === 8
          ? new Promise((resolve) => { releaseAcrossSettings = resolve })
          : Promise.resolve()
      const pageNumber = Number(request.query.page)
      const pageSize = Number(request.query.pageSize)
      const count = Math.min(pageSize, Math.max(0, 201 - ((pageNumber - 1) * pageSize)))
      return waitForRelease.then(() => ({ status: 200, body: {
        requestId: request.requestId, pluginId: 'tushare_pro', apiName: 'daily', page: pageNumber,
        pageSize, totalElements: 201, totalPages: Math.ceil(201 / pageSize),
        columns: [...EXPECTED.get('daily').columns.map(({ name }) => name), ...SOURCE_COLUMNS],
        items: syntheticRecords('daily', count),
      } }))
    },
  })
  await page.goto('/datasets')
  await expect(page.locator('#dataset-select')).toBeEnabled()
  await selectCatalog(page, 'dataset-select', 'daily')
  const from = page.locator('[data-filter="tradeDateFrom"] input')
  await from.fill('2026-08-07')
  await from.press('Enter')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: '共 201 条' })).toContainText('第 1 / 5 页')
  const pageSizeInput = page.locator('.el-pagination__sizes input')
  await page.locator('.el-pagination__sizes .el-select__wrapper').click()
  const pageSizeListbox = await pageSizeInput.getAttribute('aria-controls')
  await page.locator(`#${pageSizeListbox}`).getByRole('option').filter({ hasText: /^100/ }).click()
  await expect(page.getByRole('status').filter({ hasText: '共 201 条' })).toContainText('第 1 / 3 页')
  await page.getByRole('button', { name: /下一页/ }).click()
  await expect(page.getByRole('status').filter({ hasText: '共 201 条' })).toContainText('第 2 / 3 页')
  await from.fill('2026-08-08')
  await page.getByRole('link', { name: /设置/ }).click()
  await page.getByLabel('主题色 HEX').fill('#b52c63')
  await page.getByRole('button', { name: '应用' }).click()
  await page.getByRole('link', { name: /数据查看/ }).click()
  await expect(from).toHaveValue('2026-08-08')
  await expect(page.getByRole('status').filter({ hasText: '共 201 条' })).toContainText('第 2 / 3 页')
  expect(apiRequests(api, ({ path: requestPath }) => requestPath.endsWith('/daily/records'))).toHaveLength(3)
  await page.getByRole('button', { name: /下一页/ }).click()
  await expect(page.getByRole('heading', { name: '查询失败' })).toBeVisible()
  await page.getByRole('button', { name: '重新查询' }).click()
  await expect(page.getByRole('status').filter({ hasText: '共 201 条' })).toContainText('第 3 / 3 页')
  const queries = apiRequests(api, ({ path: requestPath }) => requestPath.endsWith('/daily/records'))
  expect(queries[3].query.tradeDateFrom).toBe('2026-08-07')
  expect(queries[4].query).toEqual(queries[3].query)
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect(page.getByRole('heading', { name: '设置筛选条件后查询' })).toBeVisible()
  await expect(page.locator('.dataset-select .el-select__selected-item:not(.el-select__input-wrapper)')).toContainText('daily')

  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('heading', { name: '正在查询数据' })).toBeVisible()
  const resetQueryRequest = apiRequests(api, ({ path: requestPath }) => requestPath.endsWith('/daily/records')).at(-1)
  expect(resetQueryRequest.query).toEqual({ page: '1', pageSize: '50' })
  await expect(from).toBeDisabled()
  await expect(page.getByRole('button', { name: '查询', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '重置', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '重置', exact: true }).click()
  releaseStale()
  await expect(page.getByRole('heading', { name: '设置筛选条件后查询' })).toBeVisible()
  await expect(page.getByRole('region', { name: '数据表格，可横向滚动' })).toHaveCount(0)
  await expect(page.locator('.dataset-select .el-select__selected-item:not(.el-select__input-wrapper)')).toContainText('daily')

  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('heading', { name: '正在查询数据' })).toBeVisible()
  await selectCatalog(page, 'dataset-select', 'trade_cal')
  releaseSelectionStale()
  await expect(page.locator('.dataset-select .el-select__selected-item:not(.el-select__input-wrapper)')).toContainText('trade_cal')
  await expect(page.getByRole('heading', { name: '设置筛选条件后查询' })).toBeVisible()
  await expect(page.getByRole('region', { name: '数据表格，可横向滚动' })).toHaveCount(0)

  await selectCatalog(page, 'dataset-select', 'income')
  const annDateTo = page.locator('[data-filter="annDateTo"] input')
  await annDateTo.fill('2026-08-07')
  await annDateTo.press('Enter')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('region', { name: '数据表格，可横向滚动' })).toBeVisible()
  const incomeQueries = apiRequests(api, ({ path: requestPath }) => requestPath.endsWith('/income/records'))
  expect(incomeQueries).toHaveLength(1)
  expect(incomeQueries[0].query).toEqual({ annDateTo: '2026-08-07', page: '1', pageSize: '50' })

  await selectCatalog(page, 'dataset-select', 'daily')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('heading', { name: '正在查询数据' })).toBeVisible()
  await page.getByRole('link', { name: /设置/ }).click()
  releaseAcrossSettings()
  await page.getByRole('link', { name: /数据查看/ }).click()
  await expect(page.getByRole('region', { name: '数据表格，可横向滚动' })).toBeVisible()
  expect(apiRequests(api, ({ path: requestPath }) => requestPath.endsWith('/daily/records'))).toHaveLength(8)
  assertApiClean(api)
})

test.describe('主题、五视口与布局稳定性', () => {
  test.describe.configure({ timeout: 120_000 })
  for (const [width, height] of [[1440, 1080], [1024, 768], [768, 1024], [390, 844], [360, 800]]) {
    test(`${width}x${height}：宽表分页、导航位置与三页四主题布局稳定`, async ({ page }) => {
      await page.setViewportSize({ width, height })
      const api = await installApi(page, {
        'GET /api/v1/data-sources/tushare_pro/datasets/stock_company/records':
          (request) => pagedDatasetResponse(request, 'stock_company'),
      })
      await openDownloads(page)
      await chooseDownload(page, 'daily')
      const tradeDate = page.locator('[data-parameter="trade_date"] input')
      await tradeDate.fill('2026-08-07')
      await tradeDate.press('Enter')
      await page.getByRole('button', { name: '开始下载' }).click()
      await expect(page.getByRole('heading', { name: '下载成功' })).toBeVisible()
      const countColumns = await page.locator('.download-result__counts').evaluate((element) => getComputedStyle(element).gridTemplateColumns)
      expect(countColumns.split(' ')).toHaveLength(3)
      await page.evaluate(() => scrollTo(0, 0))
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
      const columns = await page.locator('.download-grid').evaluate((element) => getComputedStyle(element).gridTemplateColumns)
      expect(columns.split(' ').length).toBe(width <= 1000 ? 1 : 2)
      const downloadTargets = [
        page.locator('.app-nav__link.router-link-active'), page.locator('#download-api'),
        page.getByRole('button', { name: '开始下载' }), page.locator('.download-config-panel'),
        page.locator('.download-result-panel'),
      ]
      const downloadBaseline = await boxes(downloadTargets)

      await openDataset(page, 'stock_company')
      await page.getByRole('button', { name: '查询', exact: true }).click()
      const table = page.getByRole('region', { name: '数据表格，可横向滚动' })
      await expect(table).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
      const scroller = table.locator('.el-scrollbar__wrap')
      expect(await scroller.evaluate((element) => element.scrollWidth > element.clientWidth)).toBe(true)
      const fixedCell = table.locator('.el-table__body-wrapper td').first()
      expect(await fixedCell.evaluate((element) => getComputedStyle(element).position)).toBe('sticky')
      const status = page.getByRole('status').filter({ hasText: '共 201 条' })
      const previous = page.getByRole('button', { name: /上一页/ })
      const next = page.getByRole('button', { name: /下一页/ })
      await expect(status).toContainText('第 1 / 5 页')
      await expect(previous).toBeDisabled()
      await expect(next).toBeEnabled()
      await next.click()
      await expect(status).toContainText('第 2 / 5 页')
      await previous.click()
      await expect(status).toContainText('第 1 / 5 页')
      await page.locator('.el-pager li.number').filter({ hasText: /^2$/ }).click()
      await expect(status).toContainText('第 2 / 5 页')
      const pageSizeInput = page.locator('.el-pagination__sizes input')
      await page.locator('.el-pagination__sizes .el-select__wrapper').click()
      const pageSizeListbox = await pageSizeInput.getAttribute('aria-controls')
      const sizeOptions = page.locator(`#${pageSizeListbox}`).getByRole('option')
      await expect(sizeOptions).toHaveCount(3)
      expect((await sizeOptions.allTextContents()).map((value) => Number.parseInt(value, 10))).toEqual([20, 50, 100])
      await sizeOptions.filter({ hasText: /^100/ }).click()
      await expect(status).toContainText('第 1 / 3 页')
      await page.evaluate(() => scrollTo(0, 0))
      const datasetTargets = [
        page.locator('.app-nav__link.router-link-active'), page.locator('#dataset-select'),
        page.getByRole('button', { name: '查询', exact: true }), page.locator('.dataset-result-content'),
        page.locator('.dataset-pagination'), table,
      ]
      const datasetBaseline = await boxes(datasetTargets)

      await page.getByRole('link', { name: /设置/ }).click()
      await expect(page).toHaveURL(/\/settings$/)
      await expect(page.locator('.app-nav__link.router-link-active')).toContainText('设置')
      const settingsReset = page.getByRole('button', { name: '恢复冰川白' })
      const themeInput = page.getByLabel('主题色 HEX')
      const applyButton = page.getByRole('button', { name: '应用' })
      const geometryTargets = [page.locator('.app-nav__link.router-link-active'), themeInput, applyButton, page.locator('.settings-panel'), settingsReset]
      const settingsBaseline = await boxes(geometryTargets)
      for (const color of ['#2857b4', '#b52c63', '#ffff00', '#000000']) {
        await themeInput.fill(color)
        await applyButton.click()
        await page.evaluate(() => scrollTo(0, 0))
        expectBoxesStable(settingsBaseline, await boxes(geometryTargets))
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
        await page.getByRole('link', { name: /数据下载/ }).click()
        await page.evaluate(() => scrollTo(0, 0))
        expectBoxesStable(downloadBaseline, await boxes(downloadTargets))
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
        await page.getByRole('link', { name: /数据查看/ }).click()
        await page.evaluate(() => scrollTo(0, 0))
        expectBoxesStable(datasetBaseline, await boxes(datasetTargets))
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
        await page.getByRole('link', { name: /设置/ }).click()
      }
      const navBox = await page.locator('.app-nav').boundingBox()
      const workspaceBox = await page.locator('.workspace-shell').boundingBox()
      if (width < 680) expect(navBox.y + navBox.height).toBeLessThanOrEqual(workspaceBox.y + 0.5)
      else expect(navBox.x + navBox.width).toBeLessThanOrEqual(workspaceBox.x + 0.5)
      assertApiClean(api)
    })
  }
})

test('设置零 API、四主题真实颜色、持久化降级、非法值与完整默认 palette', async ({ page, browser }) => {
  test.setTimeout(180_000)
  const api = await installApi(page)
  await page.goto('/settings')
  expect(api.requests).toEqual([])
  const rootValues = async () => page.evaluate(() => Object.fromEntries([
    'bg', 'surface', 'raised', 'nav', 'line', 'text', 'muted', 'accent-bg', 'accent', 'success', 'error',
  ].map((name) => [name, getComputedStyle(document.documentElement).getPropertyValue(`--tensor-${name}`).trim()])))
  expect(await rootValues()).toEqual({ bg: '#edf2f6', surface: '#ffffff', raised: '#f3f6fa', nav: '#f9fbfd', line: '#c8d3e0', text: '#142a42', muted: '#52677d', 'accent-bg': '#e8efff', accent: '#2857b4', success: '#14785e', error: '#b72d47' })

  const colorPicker = page.getByLabel('全局主题色')
  await colorPicker.fill('#000000')
  expect((await rootValues()).accent).toBe('#000000')
  const hex = page.getByLabel('主题色 HEX')
  const apply = page.getByRole('button', { name: '应用' })
  for (const color of ['#2857b4', '#b52c63', '#ffff00', '#000000']) {
    await hex.fill(color)
    await apply.click()
    await page.mouse.move(0, 0)
    const style = (locator) => locator.evaluate((element) => {
      const computed = getComputedStyle(element)
      return { color: computed.color, background: computed.backgroundColor }
    })
    const buttonNormal = await style(apply)
    await apply.hover()
    const buttonHover = await style(apply)
    await page.mouse.down()
    const buttonActive = await style(apply)
    await page.mouse.up()
    const body = await style(page.locator('body'))
    const panel = await style(page.locator('.settings-panel'))
    const nav = await style(page.locator('.app-nav'))
    const selectedNav = await style(page.locator('.app-nav__link.router-link-active'))
    const text = await style(page.locator('.page-heading h1'))
    const muted = await style(page.locator('.settings-form__help'))
    const reset = page.getByRole('button', { name: '恢复冰川白' })
    await reset.hover()
    const raised = await style(reset)
    for (const button of [buttonNormal, buttonHover, buttonActive]) {
      expect(contrastRatio(button.color, button.background)).toBeGreaterThanOrEqual(5.5)
    }
    for (const surface of [body.background, panel.background, nav.background, selectedNav.background, raised.background]) {
      expect(contrastRatio(buttonNormal.background, surface)).toBeGreaterThanOrEqual(5.5)
    }
    expect(contrastRatio(text.color, body.background)).toBeGreaterThanOrEqual(4.5)
    expect(contrastRatio(muted.color, panel.background)).toBeGreaterThanOrEqual(4.5)
    expect(contrastRatio(selectedNav.color, selectedNav.background)).toBeGreaterThanOrEqual(5.5)
  }
  await page.getByLabel('主题色 HEX').fill('#b52c63')
  await page.getByRole('button', { name: '应用' }).click()
  await expect(page.getByRole('status')).toContainText('已保存')
  const applied = (await rootValues()).accent
  await page.reload()
  expect((await rootValues()).accent).toBe(applied)
  await page.getByLabel('主题色 HEX').fill('#nothex')
  await page.getByRole('button', { name: '应用' }).click()
  await expect(page.getByLabel('主题色 HEX')).toHaveAttribute('aria-invalid', 'true')
  expect((await rootValues()).accent).toBe(applied)
  await page.getByRole('button', { name: '恢复冰川白' }).click()
  expect((await rootValues()).accent).toBe('#2857b4')
  expect(api.requests).toEqual([])

  await page.evaluate(() => localStorage.setItem('tensor-issue004-accent', 'damaged'))
  await page.reload()
  expect((await rootValues()).accent).toBe('#2857b4')

  for (const mode of ['getItem', 'setItem']) {
    const context = await browser.newContext()
    await context.addInitScript((method) => {
      const original = Storage.prototype[method]
      Object.defineProperty(Storage.prototype, method, {
        configurable: true,
        value(...args) {
          if (args[0] === 'tensor-issue004-accent') throw new Error(`${method} unavailable`)
          return original.apply(this, args)
        },
      })
    }, mode)
    const degradedPage = await context.newPage()
    const degradedApi = await installApi(degradedPage)
    await degradedPage.goto('/settings')
    if (mode === 'setItem') {
      await degradedPage.getByLabel('主题色 HEX').fill('#b52c63')
      await degradedPage.getByRole('button', { name: '应用' }).click()
    }
    await expect(degradedPage.getByRole('status')).toContainText('仅本次预览')
    await degradedPage.getByLabel('主题色 HEX').fill('#000000')
    await degradedPage.getByRole('button', { name: '应用' }).click()
    await degradedPage.getByRole('button', { name: '恢复冰川白' }).click()
    expect(degradedApi.requests).toEqual([])
    assertApiClean(degradedApi)
    await context.close()
  }

  const businessContext = await browser.newContext({ viewport: { width: 1024, height: 768 } })
  const businessPage = await businessContext.newPage()
  const businessApi = await installApi(businessPage, {
    'GET /api/v1/data-sources/tushare_pro/datasets/daily/records':
      (request) => pagedDatasetResponse(request, 'daily'),
    'GET /api/v1/data-sources/tushare_pro/datasets/stock_company/records':
      (request) => pagedDatasetResponse(request, 'stock_company'),
  })
  const themeColor = (name) => businessPage.evaluate((role) => {
    const probe = document.createElement('span')
    probe.style.color = `var(--tensor-${role})`
    document.body.append(probe)
    const value = getComputedStyle(probe).color
    probe.remove()
    return value
  }, name)
  const background = (locator) => locator.evaluate((element) => getComputedStyle(element).backgroundColor)
  const foreground = (locator) => locator.evaluate((element) => getComputedStyle(element).color)

  await openDownloads(businessPage)
  await chooseDownload(businessPage, 'daily')
  const businessDate = businessPage.locator('[data-parameter="trade_date"] input')
  await businessDate.fill('2026-08-07')
  await businessDate.press('Enter')
  await businessPage.getByRole('button', { name: '开始下载' }).click()
  await openDataset(businessPage, 'daily')
  await businessPage.getByRole('button', { name: '查询', exact: true }).click()
  await expect(businessPage.getByRole('status').filter({ hasText: '共 201 条' })).toContainText('第 1 / 5 页')

  for (const color of ['#2857b4', '#b52c63', '#ffff00', '#000000']) {
    await businessPage.getByRole('link', { name: /设置/ }).click()
    await businessPage.getByLabel('主题色 HEX').fill(color)
    await businessPage.getByRole('button', { name: '应用' }).click()
    const [surface, raised, nav, textColor, accent] = await Promise.all(
      ['surface', 'raised', 'nav', 'text', 'accent'].map(themeColor),
    )

    await businessPage.getByRole('link', { name: /数据下载/ }).click()
    expect(await foreground(businessPage.locator('.async-state-panel--success .async-state-panel__mark'))).toBe('rgb(20, 120, 94)')
    await businessDate.click()
    const picker = businessPage.locator('.el-picker-panel:visible')
    await expect(picker).toBeVisible()
    expect(await background(picker)).toBe(nav)
    expect(await background(picker.locator('td.current span').first())).toBe(accent)
    await businessPage.keyboard.press('Escape')
    await businessPage.locator('#download-api').click()
    const apiListboxId = await businessPage.locator('#download-api').getAttribute('aria-controls')
    const apiDropdown = businessPage.locator(`#${apiListboxId}`).locator('xpath=ancestor::*[contains(@class, "el-popper")][1]')
    await expect(apiDropdown).toBeVisible()
    expect(await background(apiDropdown)).toBe(nav)
    await businessPage.keyboard.press('Escape')

    await businessPage.getByRole('link', { name: /数据查看/ }).click()
    const dailyTable = businessPage.getByRole('region', { name: '数据表格，可横向滚动' })
    expect(await background(dailyTable.locator('.el-table__body-wrapper td').first())).toBe(surface)
    expect(await background(dailyTable.locator('.el-table__header-wrapper th').first())).toBe(raised)
    expect(await foreground(businessPage.locator('.el-pager li.is-active'))).toBe(accent)
    expect(await foreground(dailyTable.locator('.dataset-table__change--up').first())).toBe('rgb(183, 45, 71)')
    expect(await foreground(dailyTable.locator('.dataset-table__change--down').first())).toBe('rgb(20, 120, 94)')

    await selectCatalog(businessPage, 'dataset-select', 'stock_company')
    await businessPage.getByRole('button', { name: '查询', exact: true }).click()
    const longCell = businessPage.getByText(/这是一段用于验证长文本提示/).first()
    await longCell.hover()
    const tooltip = businessPage.locator('.el-popper:visible').filter({ hasText: '<strong>' })
    await expect(tooltip).toBeVisible()
    expect(await background(tooltip)).toBe(textColor)
    await businessPage.mouse.move(0, 0)
    await selectCatalog(businessPage, 'dataset-select', 'daily')
    await businessPage.getByRole('button', { name: '查询', exact: true }).click()
  }
  assertApiClean(businessApi)
  await businessContext.close()
})

test('精确宽表、符号、单位、大整数、空值与纯文本 tooltip', async ({ page }) => {
  const api = await installApi(page)
  await page.goto('/datasets')
  await expect(page.locator('#dataset-select')).toBeEnabled()
  for (const apiName of ['balancesheet', 'daily', 'weekly', 'stk_holdernumber', 'stock_company']) {
    await selectCatalog(page, 'dataset-select', apiName)
    await page.getByRole('button', { name: '查询', exact: true }).click()
    const table = page.getByRole('region', { name: '数据表格，可横向滚动' })
    await expect(table).toBeVisible()
    if (apiName === 'balancesheet') {
      await expect(table.locator('.el-table__header-wrapper th')).toHaveCount(155)
      await expect(table).toContainText('12345678901234567890.123456789012345678')
      const scroller = table.locator('.el-scrollbar__wrap')
      expect(await scroller.evaluate((element) => element.scrollWidth > element.clientWidth)).toBe(true)
      const fixedCell = table.locator('.el-table__body-wrapper td').first()
      expect(await fixedCell.evaluate((element) => getComputedStyle(element).position)).toBe('sticky')
      expect(await fixedCell.evaluate((element) => getComputedStyle(element).backgroundColor)).toBe('rgb(255, 255, 255)')
      await scroller.evaluate((element) => { element.scrollLeft = element.scrollWidth })
      await expect(table.locator('.el-table__header-wrapper th').last()).toContainText('ingested_at')
    }
    if (apiName === 'daily') {
      await expect(table).toContainText('+0.0100')
      await expect(table).toContainText('11.2700')
      await expect(table.getByText('-0.0200', { exact: true })).toHaveClass(/dataset-table__change--down/)
      await expect(table.getByText('-0.0000', { exact: true }).first()).not.toHaveClass(/dataset-table__change--down/)
      await expect(table.locator('th').filter({ hasText: '涨跌幅（%）' })).toBeVisible()
      const number = table.locator('.dataset-table__number').first()
      expect(await number.evaluate((element) => getComputedStyle(element).fontVariantNumeric)).toContain('tabular-nums')
      expect(await number.evaluate((element) => getComputedStyle(element.closest('td')).textAlign)).toBe('right')
    }
    if (apiName === 'weekly') {
      await expect(table).toContainText('-0.0378')
      await expect(table.locator('th').filter({ hasText: '涨跌幅（比率）' })).toBeVisible()
    }
    if (apiName === 'stk_holdernumber') await expect(table).toContainText('9223372036854775807')
    if (apiName === 'stock_company') {
      const cell = table.getByText(/这是一段用于验证长文本提示/)
      await page.mouse.move(0, 0)
      await cell.hover()
      const tooltip = page.locator('.el-popper:visible').filter({ hasText: '<strong>' })
      await expect(tooltip).toBeVisible()
      await expect(tooltip.locator('strong')).toHaveCount(0)
    }
    await expect(table).toContainText('--')
    await expect(table).toContainText('2026-08-07 20:34:56')
  }
  assertApiClean(api)
})

test('键盘、焦点、移动端弹层与 reduced motion', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 800 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  const api = await installApi(page, {
    'GET /api/v1/data-sources/tushare_pro/datasets/stock_company/records':
      (request) => pagedDatasetResponse(request, 'stock_company'),
  })
  await openDownloads(page)
  const skipLink = page.getByRole('link', { name: '跳转到工作区' })
  const downloadsLink = page.getByRole('link', { name: /数据下载/ })
  const datasetsLink = page.getByRole('link', { name: /数据查看/ })
  await tabTo(page, skipLink)
  await expectFocusOutline(skipLink)
  await tabTo(page, downloadsLink)
  await expectFocusOutline(downloadsLink)
  await tabTo(page, datasetsLink)
  await expectFocusOutline(datasetsLink)
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/datasets$/)
  await expectFocusOutline(datasetsLink)
  await tabTo(page, downloadsLink, { backwards: true })
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/downloads$/)

  const downloadApi = page.locator('#download-api')
  await tabTo(page, downloadApi)
  await expectFocusOutline(downloadApi, downloadApi.locator('xpath=ancestor::*[contains(@class, "el-select__wrapper")][1]'))
  await page.keyboard.press('ControlOrMeta+A')
  await page.keyboard.type('daily')
  await page.keyboard.press('ArrowDown')
  await page.keyboard.press('Enter')
  await page.keyboard.press('Escape')
  await expect(page.locator('.api-select .el-select__selected-item:not(.el-select__input-wrapper)')).toContainText('daily')

  const date = page.locator('[data-parameter="trade_date"] input')
  await tabTo(page, date)
  await expectFocusOutline(date, date.locator('xpath=ancestor::*[contains(@class, "el-input__wrapper")][1]'))
  const picker = page.locator('.el-picker-panel:visible')
  await expect(picker).toBeVisible()
  const pickerBox = await picker.boundingBox()
  expect(pickerBox.x).toBeGreaterThanOrEqual(0)
  expect(pickerBox.x + pickerBox.width).toBeLessThanOrEqual(360)
  await page.keyboard.press('Escape')
  await page.keyboard.press('ControlOrMeta+A')
  await page.keyboard.type('2026-08-07')
  await page.keyboard.press('Enter')
  await expect(date).toHaveValue('2026-08-07')
  const downloadButton = page.getByRole('button', { name: '开始下载' })
  await tabTo(page, downloadButton)
  await expectFocusOutline(downloadButton)
  await page.keyboard.press('Enter')
  await expect(page.getByRole('heading', { name: '下载成功' })).toBeVisible()

  await tabTo(page, datasetsLink)
  await expectFocusOutline(datasetsLink)
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/datasets$/)
  const datasetSelect = page.locator('#dataset-select')
  await expect(datasetSelect).toBeEnabled()
  await tabTo(page, datasetSelect)
  await expectFocusOutline(datasetSelect, datasetSelect.locator('xpath=ancestor::*[contains(@class, "el-select__wrapper")][1]'))
  await page.keyboard.type('stock_company')
  const listboxId = await datasetSelect.getAttribute('aria-controls')
  const dropdown = page.locator(`#${listboxId}`).locator('xpath=ancestor::*[contains(@class, "el-popper")][1]')
  await expect(dropdown).toBeVisible()
  const dropdownBox = await dropdown.boundingBox()
  expect(dropdownBox.x).toBeGreaterThanOrEqual(0)
  expect(dropdownBox.x + dropdownBox.width).toBeLessThanOrEqual(360)
  await datasetSelect.press('ArrowDown')
  await datasetSelect.press('Enter')
  await datasetSelect.press('Escape')
  await expect(page.locator('.dataset-select .el-select__selected-item:not(.el-select__input-wrapper)')).toContainText('stock_company')
  const queryButton = page.getByRole('button', { name: '查询', exact: true })
  await tabTo(page, queryButton)
  await expectFocusOutline(queryButton)
  await page.keyboard.press('Enter')
  const status = page.getByRole('status').filter({ hasText: '共 201 条' })
  await expect(status).toContainText('第 1 / 5 页')

  const table = page.getByRole('region', { name: '数据表格，可横向滚动' })
  await tabTo(page, table)
  await expectFocusOutline(table)

  const pageSizeInput = page.locator('.el-pagination__sizes input')
  await tabTo(page, pageSizeInput)
  await expectFocusOutline(pageSizeInput, pageSizeInput.locator('xpath=ancestor::*[contains(@class, "el-select__wrapper")][1]'))
  await page.keyboard.press('Enter')
  await page.keyboard.press('ArrowDown')
  await page.keyboard.press('Enter')
  await page.keyboard.press('Escape')
  await expect(status).toContainText('第 1 / 3 页')
  const next = page.getByRole('button', { name: /下一页/ })
  await tabTo(page, next)
  await expectFocusOutline(next)
  await page.keyboard.press('Enter')
  await expect(status).toContainText('第 2 / 3 页')
  const previous = page.getByRole('button', { name: /上一页/ })
  await tabTo(page, previous)
  await expectFocusOutline(previous)
  await page.keyboard.press('Enter')
  await expect(status).toContainText('第 1 / 3 页')
  const secondPage = page.locator('.el-pager li.number').filter({ hasText: /^2$/ })
  await tabTo(page, secondPage, { limit: 10 })
  await expectFocusOutline(secondPage)
  await page.keyboard.press('Enter')
  await expect(status).toContainText('第 2 / 3 页')
  await tabTo(page, table)
  await expectFocusOutline(table)
  await page.keyboard.press('ArrowRight')
  expect(await table.locator('.el-scrollbar__wrap').evaluate((element) => element.scrollLeft)).toBeGreaterThan(0)
  assertApiClean(api)
})

test('生成八张可复现的正式验收截图', async ({ page }) => {
  test.setTimeout(120_000)
  const api = await installApi(page)
  const shot = async (name, popup = false) => {
    if (popup) {
      await expect(page.locator('.el-popper:visible')).toBeVisible()
      await expect.poll(() => page.locator('.el-popper:visible').evaluate((element) => getComputedStyle(element).opacity)).toBe('1')
    } else {
      await expect(page.locator('.el-popper:visible')).toHaveCount(0)
    }
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, name), fullPage: true, animations: 'disabled' })
  }
  await page.setViewportSize({ width: 1440, height: 1080 })
  await openDownloads(page)
  await chooseDownload(page, 'daily')
  const date = page.locator('[data-parameter="trade_date"] input')
  await date.fill('2026-08-07')
  await date.press('Enter')
  await page.getByRole('button', { name: '开始下载' }).click()
  await expect(page.getByRole('heading', { name: '下载成功' })).toBeVisible()
  await shot('downloads-desktop.png')
  await openDataset(page, 'daily')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('region', { name: '数据表格，可横向滚动' })).toBeVisible()
  await shot('datasets-desktop.png')
  await page.getByRole('link', { name: /设置/ }).click()
  await shot('settings-desktop.png')
  await page.getByRole('link', { name: /数据下载/ }).click()
  await page.setViewportSize({ width: 390, height: 844 })
  await shot('downloads-mobile.png')
  await page.getByRole('link', { name: /数据查看/ }).click()
  await shot('datasets-mobile.png')
  await page.getByRole('link', { name: /设置/ }).click()
  await shot('settings-mobile.png')
  await page.setViewportSize({ width: 360, height: 800 })
  await page.getByRole('link', { name: /数据下载/ }).click()
  await date.click()
  await expect(page.locator('.el-picker-panel:visible')).toBeVisible()
  await shot('datepicker-mobile.png', true)
  await page.keyboard.press('Escape')
  await page.unroute('**/api/**')
  const errorApi = await installApi(page, {
    'POST /api/v1/downloads': (request) => {
      const failure = apiFailure(request.requestId, 'SOURCE_TIMEOUT')
      failure.body.message = '上游数据源响应超时，当前请求未完成。请检查网络与凭证后稍后使用原参数重试；字面标记 <strong> 必须作为纯文本显示。'
      return failure
    },
  })
  await page.reload()
  await chooseDownload(page, 'daily')
  await page.locator('[data-parameter="trade_date"] input').fill('2026-08-07')
  await page.locator('[data-parameter="trade_date"] input').press('Enter')
  await page.getByRole('button', { name: '开始下载' }).click()
  await expect(page.getByRole('heading', { name: '下载失败' })).toBeVisible()
  await shot('download-error-mobile.png')
  assertApiClean(api)
  assertApiClean(errorApi)
})
