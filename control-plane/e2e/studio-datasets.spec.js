import { expect, test } from '@playwright/test'
import path from 'node:path'
import { apiFailure, DATASETS, DATA_SOURCE, definitionResponse, installApi } from './ui-redesign.fixtures.js'

const output = path.resolve('node_modules/.cache/studio-t08')
const base = '/api/v1/data-sources/tushare_pro/datasets'
const records = api => api.requests.filter(request => request.path.endsWith('/records'))
const query = page => page.getByRole('button', { name: '查询', exact: true })
const input = (page, name) => page.locator(`[data-filter="${name}"] input`)

async function selectDataset(page, name) {
  const select = page.getByRole('combobox', { name: '数据集', exact: true })
  await select.fill(name)
  await page.getByRole('option').filter({ has: page.getByText(name, { exact: true }) }).click()
  await expect(page.locator('.el-select-dropdown:visible')).toHaveCount(0)
}

async function open(page, overrides = {}) {
  const api = await installApi(page, overrides)
  await page.goto('/datasets')
  await selectDataset(page, 'daily')
  await expect(query(page)).toBeVisible()
  return api
}

async function metrics(locator) {
  return locator.evaluate(element => {
    const rect = element.getBoundingClientRect()
    const style = getComputedStyle(element)
    return { x: rect.x, y: rect.y, height: rect.height, width: rect.width, fontSize: style.fontSize, color: style.color, background: style.backgroundColor }
  })
}

for (const width of [1024, 1280, 1440]) {
  test(`${width}px：横向查询栏、原生控件与 Demo 对照，真实查询支持 Enter`, async ({ page, context }) => {
    await page.setViewportSize({ width, height: 1050 })
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    const api = await open(page)
    const demo = await context.newPage()
    await demo.setViewportSize({ width, height: 1050 })
    await demo.goto('/ui-demos.html')
    await demo.getByRole('button', { name: '数据查看', exact: true }).click()
    const actualBrowser = await metrics(page.locator('.data-browser'))
    const demoBrowser = await metrics(demo.locator('.data-browser'))
    expect(actualBrowser.x).toBeCloseTo(demoBrowser.x, 0)
    expect(actualBrowser.y).toBeCloseTo(demoBrowser.y - (await demo.locator('.demo-banner').boundingBox()).height, 0)
    expect(actualBrowser.width).toBeCloseTo(demoBrowser.width, 0)
    for (const [actual, reference] of [
      [input(page, 'tsCode'), demo.getByRole('textbox', { name: '股票代码', exact: true })],
      [input(page, 'tradeDateFrom'), demo.locator('.query-form input[type="date"]').first()],
      [query(page), demo.getByRole('button', { name: '查询', exact: true })],
    ]) {
      const a = await metrics(actual)
      const b = await metrics(reference)
      for (const key of ['height', 'fontSize', 'color', 'background']) expect(a[key], key).toBe(b[key])
    }
    const fieldBoxes = await page.locator('.query-form input:not(.el-select__input)').evaluateAll(elements => elements.map(el => {
      const box = el.getBoundingClientRect()
      return { x: box.x, y: box.y, width: box.width, right: box.right }
    }))
    expect(new Set(fieldBoxes.map(box => box.y)).size).toBe(1)
    for (const box of fieldBoxes) expect(box.width).toBeGreaterThanOrEqual(140)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await input(page, 'tsCode').fill(' 000001.sz ')
    await input(page, 'tradeDateFrom').fill('2026-08-01')
    await input(page, 'tradeDateTo').fill('2026-08-07')
    await input(page, 'tsCode').press('Enter')
    await expect(page.getByRole('region', { name: '数据表格，可横向滚动' })).toBeVisible()
    expect(records(api).map(request => request.query)).toEqual([{ tsCode: '000001.SZ', tradeDateFrom: '2026-08-01', tradeDateTo: '2026-08-07', page: '1', pageSize: '50' }])
    await input(page, 'tsCode').fill('')
    await input(page, 'tradeDateFrom').fill('')
    await input(page, 'tradeDateTo').fill('')
    await query(page).click()
    await expect(page.getByRole('region', { name: '数据表格，可横向滚动' })).toBeVisible()
    await page.mouse.move(0, 0)
    await page.locator('h1').click()
    await page.screenshot({ path: `${output}/query-${width}.png`, fullPage: true })
    await demo.screenshot({ path: `${output}/demo-query-${width}.png`, fullPage: true })
    expect(api.unexpected).toEqual([])
    expect(errors).toEqual([])
    await demo.close()
  })
}

test('日期校验聚焦、修正范围和未完成日期，重置保留数据集且不自动查询', async ({ page }) => {
  const api = await open(page)
  const from = input(page, 'tradeDateFrom')
  const to = input(page, 'tradeDateTo')
  await from.fill('2026-09-04')
  await to.fill('2026-09-01')
  await query(page).click()
  await expect(from).toBeFocused()
  await expect(from).toHaveAttribute('aria-invalid', 'true')
  expect(records(api)).toEqual([])
  await to.fill('2026-09-05')
  await expect(page.getByText('开始日期不得晚于结束日期')).toHaveCount(0)
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await from.focus()
  await from.press('ArrowLeft')
  await from.press('ArrowLeft')
  await from.pressSequentially('02')
  expect(await from.evaluate(el => ({ value: el.value, badInput: el.validity.badInput }))).toEqual({ value: '', badInput: true })
  await query(page).click()
  await expect(from).toHaveAttribute('aria-invalid', 'true')
  await expect(from).toBeFocused()
  expect(records(api)).toEqual([])
  await page.screenshot({ path: `${output}/invalid-date.png`, fullPage: true })
  await page.getByRole('button', { name: '重置', exact: true }).click()
  expect(await from.evaluate(el => el.validity.badInput)).toBe(false)
  await expect(page.getByText('请选择有效日期', { exact: true })).toHaveCount(0)
  expect(records(api)).toEqual([])
  await query(page).click()
  await expect(page.getByRole('region', { name: '数据表格，可横向滚动' })).toBeVisible()
  expect(records(api)[0].query).toEqual({ page: '1', pageSize: '50' })
  expect(api.unexpected).toEqual([])
})

for (const [name, key, recovery] of [
  ['数据源', 'GET /api/v1/data-sources', [DATA_SOURCE]],
  ['数据集', `GET ${base}`, DATASETS],
  ['数据集定义', `GET ${base}/daily`, definitionResponse('daily')],
]) {
  test(`${name}元数据失败显示请求 ID 并重新加载当前资源`, async ({ page }) => {
    await page.setViewportSize({ width: 1024, height: 850 })
    let attempts = 0
    const api = await installApi(page, { [key]: request => ++attempts === 1 ? apiFailure(request.requestId) : { status: 200, body: recovery } })
    await page.goto('/datasets')
    if (name === '数据集定义') await selectDataset(page, 'daily')
    await expect(page.getByRole('alert')).toContainText('请求 ID：')
    if (name === '数据集定义') await page.screenshot({ path: `${output}/metadata-failure-1024.png`, fullPage: true })
    await page.getByRole('button', { name: '重新加载', exact: true }).click()
    if (name !== '数据集定义') await selectDataset(page, 'daily')
    await expect(query(page)).toBeVisible()
    await expect(page.getByRole('alert')).toHaveCount(0)
    expect(attempts).toBe(2)
    expect(records(api)).toEqual([])
    expect(api.unexpected).toEqual([])
  })
}

for (const [key, heading] of [
  ['GET /api/v1/data-sources', '暂无数据源'],
  [`GET ${base}`, '暂无可查询的数据集'],
]) {
  test(`${heading}有明确空态并禁用选择`, async ({ page }) => {
    const api = await installApi(page, { [key]: { status: 200, body: [] } })
    await page.goto('/datasets')
    await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible()
    await expect(page.getByRole('combobox', { name: '数据集', exact: true })).toBeDisabled()
    expect(records(api)).toEqual([])
  })
}

test('公告筛选只发送当前定义支持条件，无筛选数据集允许查询', async ({ page }) => {
  const api = await open(page)
  await input(page, 'tsCode').fill('000001.SZ')
  await input(page, 'tradeDateFrom').fill('2026-09-01')
  await selectDataset(page, 'income')
  await expect(input(page, 'tradeDateFrom')).toHaveCount(0)
  await expect(input(page, 'tsCode')).toHaveValue('')
  await input(page, 'annDateTo').fill('2026-09-04')
  await query(page).click()
  await expect(page.getByRole('region', { name: '数据表格，可横向滚动' })).toBeVisible()
  expect(records(api)[0].query).toEqual({ annDateTo: '2026-09-04', page: '1', pageSize: '50' })
  await selectDataset(page, 'index_classify')
  await expect(page.getByText('此数据集无需填写筛选条件。')).toBeVisible()
  await expect(page.locator('[data-filter]')).toHaveCount(0)
  await query(page).click()
  await expect(page.getByRole('region', { name: '数据表格，可横向滚动' })).toBeVisible()
  expect(records(api)[1].query).toEqual({ page: '1', pageSize: '50' })
  expect(api.unexpected).toEqual([])
})


test('键盘选择数据集、输入日期分段并通过 Enter 查询', async ({ page }) => {
  const api = await installApi(page)
  await page.goto('/datasets')
  const select = page.getByRole('combobox', { name: '数据集', exact: true })
  await select.focus()
  await page.keyboard.type('daily')
  await select.press('ArrowDown')
  await select.press('Enter')
  await select.press('Escape')
  const code = input(page, 'tsCode')
  for (let i = 0; i < 12 && !await code.evaluate(el => el === document.activeElement); i++) await page.keyboard.press('Tab')
  await expect(code).toBeFocused()
  await page.keyboard.type('000001.sz')
  for (const name of ['tradeDateFrom', 'tradeDateTo']) {
    const date = input(page, name)
    for (let i = 0; i < 12 && !await date.evaluate(el => el === document.activeElement); i++) await page.keyboard.press('Tab')
    await expect(date).toBeFocused()
    await page.keyboard.press('ArrowLeft')
    await page.keyboard.press('ArrowLeft')
    await page.keyboard.type('08072026')
    await expect(date).toHaveValue('2026-08-07')
  }
  expect(records(api)).toEqual([])
  await page.keyboard.press('Enter')
  await expect(page.getByRole('region', { name: '数据表格，可横向滚动' })).toBeVisible()
  expect(records(api).map(request => request.query)).toEqual([{ tsCode: '000001.SZ', tradeDateFrom: '2026-08-07', tradeDateTo: '2026-08-07', page: '1', pageSize: '50' }])
  expect(api.unexpected).toEqual([])
})
