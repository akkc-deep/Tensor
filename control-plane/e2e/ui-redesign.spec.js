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
  const displayValue = parameter.type === 'MONTH'
    ? '2026-08'
    : ['DATE', 'DATE_RANGE_MEMBER'].includes(parameter.type)
      ? parameter.name === 'start_date' ? '2026-08-03' : '2026-08-07'
      : '000001.SZ'
  await input.fill(displayValue)
  await input.press('Enter')
  await input.blur()
  await expect(input).toHaveValue(displayValue)
  return parameter.type === 'MONTH'
    ? '202608'
    : ['DATE', 'DATE_RANGE_MEMBER'].includes(parameter.type)
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
    else if (['DATE', 'DATE_RANGE_MEMBER', 'MONTH'].includes(parameter.type)) await expect(wrapper.locator('.el-date-editor')).toBeVisible()
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

test.describe('49 项 UI 元数据矩阵', () => {
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
        return apiFailure(request.requestId, 'SOURCE_TIMEOUT')
      }
      if (downloadAttempt === 3) return { status: 200, body: successDownload(request.body.apiName, request.requestId, 'EMPTY') }
      if (downloadAttempt === 4) return apiFailure(request.requestId, 'PARAM_INVALID')
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
  await expect(page.getByRole('heading', { name: '下载失败' })).toBeVisible()
  await required.fill('2026-08-08')
  await page.getByRole('link', { name: /设置/ }).click()
  await page.getByRole('link', { name: /数据下载/ }).click()
  await expect(page.getByRole('heading', { name: '下载失败' })).toBeVisible()
  await page.getByRole('button', { name: '使用原参数重试' }).click()
  await expect(page.getByRole('heading', { name: '下载成功' })).toBeVisible()
  const posts = apiRequests(api, ({ method }) => method === 'POST')
  expect(posts).toHaveLength(2)
  expect(posts[0].body.params).toEqual({ trade_date: '20260807' })
  expect(posts[1].body.params).toEqual({ trade_date: '20260807' })

  await page.getByRole('button', { name: '开始下载' }).click()
  await expect(page.getByRole('heading', { name: '下载成功，0 条数据' })).toBeVisible()
  await page.getByRole('button', { name: '开始下载' }).click()
  await expect(page.getByRole('heading', { name: '下载失败' })).toBeVisible()
  await expect(page.getByRole('button', { name: '使用原参数重试' })).toHaveCount(0)

  await chooseDownload(page, 'new_share')
  const start = page.locator('[data-parameter="start_date"] input')
  const end = page.locator('[data-parameter="end_date"] input')
  await page.getByRole('button', { name: '开始下载' }).click()
  expect(apiRequests(api, ({ method }) => method === 'POST')).toHaveLength(4)
  await start.fill('2026-08-08')
  await start.press('Enter')
  await end.fill('2026-08-07')
  await end.press('Enter')
  await page.getByRole('button', { name: '开始下载' }).click()
  await expect(start).toHaveAttribute('aria-invalid', 'true')
  expect(apiRequests(api, ({ method }) => method === 'POST')).toHaveLength(4)
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
  let releaseAcrossSettings
  const api = await installApi(page, {
    'GET /api/v1/data-sources/tushare_pro/datasets/daily/records': (request) => {
      queryAttempt += 1
      if (queryAttempt === 4) return apiFailure(request.requestId, 'QUERY_FAILED')
      const waitForRelease = queryAttempt === 6
        ? new Promise((resolve) => { releaseStale = resolve })
        : queryAttempt === 7
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

  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('heading', { name: '正在查询数据' })).toBeVisible()
  await expect(from).toBeDisabled()
  await expect(page.getByRole('button', { name: '查询', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '重置', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '重置', exact: true }).click()
  releaseStale()
  await expect(page.getByRole('heading', { name: '设置筛选条件后查询' })).toBeVisible()
  await expect(page.getByRole('region', { name: '数据表格，可横向滚动' })).toHaveCount(0)

  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('heading', { name: '正在查询数据' })).toBeVisible()
  await page.getByRole('link', { name: /设置/ }).click()
  releaseAcrossSettings()
  await page.getByRole('link', { name: /数据查看/ }).click()
  await expect(page.getByRole('region', { name: '数据表格，可横向滚动' })).toBeVisible()
  expect(apiRequests(api, ({ path: requestPath }) => requestPath.endsWith('/daily/records'))).toHaveLength(7)
  assertApiClean(api)
})

test.describe('主题、五视口与布局稳定性', () => {
  test.describe.configure({ timeout: 120_000 })
  for (const [width, height] of [[1440, 1080], [1024, 768], [768, 1024], [390, 844], [360, 800]]) {
    test(`${width}x${height}：三页无页面横向溢出、主题换色不挪动布局`, async ({ page }) => {
      await page.setViewportSize({ width, height })
      const api = await installApi(page)
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
      const baseline = await page.locator('.download-config-panel').boundingBox()
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
      const columns = await page.locator('.download-grid').evaluate((element) => getComputedStyle(element).gridTemplateColumns)
      expect(columns.split(' ').length).toBe(width <= 1000 ? 1 : 2)
      await page.getByRole('link', { name: /设置/ }).click()
      await expect(page).toHaveURL(/\/settings$/)
      await expect(page.locator('.app-nav__link.router-link-active')).toContainText('设置')
      const settingsReset = page.getByRole('button', { name: '恢复冰川白' })
      const themeInput = page.getByLabel('主题色 HEX')
      const applyButton = page.getByRole('button', { name: '应用' })
      const geometryTargets = [page.locator('.app-nav__link.router-link-active'), themeInput, applyButton, page.locator('.settings-panel'), settingsReset]
      const settingsBaseline = await boxes(geometryTargets)
      for (const color of ['#b52c63', '#ffff00', '#000000']) {
        await themeInput.fill(color)
        await applyButton.click()
        expectBoxesStable(settingsBaseline, await boxes(geometryTargets))
      }
      await page.getByRole('link', { name: /数据下载/ }).click()
      await expect(page).toHaveURL(/\/downloads$/)
      await expect(page.getByRole('heading', { name: '数据下载', level: 1 })).toBeVisible()
      await page.evaluate(() => scrollTo(0, 0))
      const themed = await page.locator('.download-config-panel').boundingBox()
      expectBoxesStable([baseline], [themed])
      await page.getByRole('link', { name: /数据查看/ }).click()
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
      await page.getByRole('link', { name: /设置/ }).click()
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
      assertApiClean(api)
    })
  }
})

test('设置零 API、四主题真实颜色、持久化降级、非法值与完整默认 palette', async ({ page, browser }) => {
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
  const api = await installApi(page)
  await openDownloads(page)
  await page.keyboard.press('Tab')
  await expect(page.getByRole('link', { name: '跳转到工作区' })).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page.locator('#workspace')).toBeFocused()
  await chooseDownload(page, 'daily')
  const date = page.locator('[data-parameter="trade_date"] input')
  await date.fill('2026-08-07')
  await date.press('Enter')
  await date.click()
  const picker = page.locator('.el-picker-panel:visible')
  await expect(picker).toBeVisible()
  const pickerBox = await picker.boundingBox()
  expect(pickerBox.x).toBeGreaterThanOrEqual(0)
  expect(pickerBox.x + pickerBox.width).toBeLessThanOrEqual(360)
  const selectedDate = picker.locator('td.current span').first()
  await expect(selectedDate).toBeVisible()
  const selectedDateStyle = await selectedDate.evaluate((element) => {
    const computed = getComputedStyle(element)
    return { color: computed.color, background: computed.backgroundColor }
  })
  expect(contrastRatio(selectedDateStyle.color, selectedDateStyle.background)).toBeGreaterThanOrEqual(5.5)
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '开始下载' }).focus()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('heading', { name: '下载成功' })).toBeVisible()
  await page.getByRole('link', { name: /数据查看/ }).click()
  const datasetSelect = page.locator('#dataset-select')
  await expect(datasetSelect).toBeEnabled()
  await datasetSelect.click()
  await datasetSelect.fill('balancesheet')
  const listboxId = await datasetSelect.getAttribute('aria-controls')
  const dropdown = page.locator(`#${listboxId}`).locator('xpath=ancestor::*[contains(@class, "el-popper")][1]')
  await expect(dropdown).toBeVisible()
  const dropdownBox = await dropdown.boundingBox()
  expect(dropdownBox.x).toBeGreaterThanOrEqual(0)
  expect(dropdownBox.x + dropdownBox.width).toBeLessThanOrEqual(360)
  await datasetSelect.press('ArrowDown')
  await datasetSelect.press('Enter')
  await datasetSelect.press('Escape')
  await expect(page.locator('.dataset-setup')).toContainText('公告日期')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  const table = page.getByRole('region', { name: '数据表格，可横向滚动' })
  await page.getByRole('button', { name: '重置', exact: true }).focus()
  await page.keyboard.press('Tab')
  await expect(table).toBeFocused()
  expect(await table.evaluate((element) => getComputedStyle(element).outlineStyle)).not.toBe('none')
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
