import { expect, test } from '@playwright/test'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { EXPECTED, installApi, syntheticRecords } from './ui-redesign.fixtures.js'

const output = path.resolve('node_modules/.cache/studio-t09')
const recordPath = name => `GET /api/v1/data-sources/tushare_pro/datasets/${name}/records`
const table = page => page.getByRole('region', { name: '数据表格，可横向滚动' })
const pager = page => page.getByRole('navigation', { name: '数据集分页' })
const requests = api => api.requests.filter(request => request.path.endsWith('/records'))

function response(request, name, total = 101, items, size = Number(request.query.pageSize)) {
  const page = Number(request.query.page)
  const start = (page - 1) * size
  const count = Math.min(size, Math.max(0, total - start))
  return { status: 200, body: {
    requestId: request.requestId, pluginId: 'tushare_pro', apiName: name,
    page, pageSize: size, totalElements: total, totalPages: Math.ceil(total / size),
    columns: [...EXPECTED.get(name).columns.map(column => column.name), 'source_plugin', 'source_api', 'ingested_at'],
    items: items ?? syntheticRecords(name, count).map((row, index) => ({ ...row, ts_code: String(start + index + 1).padStart(6, '0') + '.SZ' })),
  } }
}

async function open(page, name, overrides = {}) {
  const api = await installApi(page, overrides)
  await page.goto('/datasets')
  await page.getByRole('combobox', { name: '数据集', exact: true }).fill(name)
  await page.getByRole('option').filter({ has: page.getByText(name, { exact: true }) }).click()
  await expect(page.locator('.el-select-dropdown:visible')).toHaveCount(0)
  return api
}

async function query(page) {
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(table(page)).toBeVisible()
}

async function style(locator, properties) {
  return locator.evaluate((element, properties) => {
    const css = getComputedStyle(element)
    return Object.fromEntries(properties.map(key => [key, css[key]]))
  }, properties)
}

for (const width of [1024, 1280, 1440]) {
  test(`${width}px：完整日线表格和分页与 Studio Demo 对照`, async ({ page, context }) => {
    await page.setViewportSize({ width, height: 1050 })
    // Same records as the pinned Demo; production still receives every real field.
    const sample = JSON.parse(readFileSync(new URL('../../docs/data-template/daily.json', import.meta.url), 'utf8')).data.slice(0, 20)
    const items = sample.map(row => ({ ...Object.fromEntries(Object.entries(row).map(([key, value]) => [key, value === null ? null : String(value)])),
      trade_date: row.trade_date.replace(/^(\d{4})(\d{2})(\d{2})$/, '$1-$2-$3'),
      source_plugin: 'tushare_pro', source_api: 'daily', ingested_at: '2026-08-07T12:34:56Z',
    }))
    const api = await open(page, 'daily', { [recordPath('daily')]: request => response(request, 'daily', 24, items, 20) })
    await query(page)
    await expect(table(page).locator('.el-table__header th')).toHaveCount(14)
    await expect(table(page).locator('.el-table__body tbody tr')).toHaveCount(20)
    const demo = await context.newPage()
    await demo.setViewportSize({ width, height: 1050 })
    await demo.goto('/ui-demos.html')
    await demo.getByRole('button', { name: '数据查看', exact: true }).click()
    const actualBox = await table(page).boundingBox()
    const referenceBox = await demo.locator('.data-scroll').boundingBox()
    expect(actualBox.x).toBe(referenceBox.x)
    expect(actualBox.width).toBe(referenceBox.width)
    expect(actualBox.height).toBeCloseTo(referenceBox.height, 0)
    for (const [actual, reference, properties] of [
      [table(page), demo.locator('.data-scroll'), ['borderRadius', 'borderTopColor', 'borderTopWidth']],
      [table(page).locator('th').first(), demo.locator('.records-table th').first(), ['backgroundColor', 'fontFamily', 'fontSize', 'fontWeight', 'color', 'paddingTop']],
      [table(page).locator('.el-table__body td .cell').first(), demo.locator('.records-table td').first(), ['fontSize', 'color', 'lineHeight', 'paddingLeft']],
      [pager(page), demo.locator('.pagination'), ['paddingTop', 'paddingBottom', 'fontSize', 'color']],
    ]) expect(await style(actual, properties)).toEqual(await style(reference, properties))
    await expect(table(page).locator('.el-table__body td').first()).toHaveText(sample[0].ts_code)
    await expect(pager(page).getByRole('status')).toHaveText('共 24 条，第 1 / 2 页')
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    const scroll = table(page).locator('.el-scrollbar__wrap')
    const headerY = (await table(page).locator('th').first().boundingBox()).y
    await scroll.evaluate(element => { element.scrollTop = element.scrollHeight })
    expect((await table(page).locator('th').first().boundingBox()).y).toBe(headerY)
    await scroll.evaluate(element => { element.scrollTop = 0 })
    await table(page).focus()
    await page.keyboard.press('ArrowRight')
    await expect.poll(() => scroll.evaluate(element => element.scrollLeft)).toBeGreaterThan(0)
    await page.keyboard.press('ArrowLeft')
    await expect.poll(() => scroll.evaluate(element => element.scrollLeft)).toBe(0)
    await page.keyboard.press('Tab')
    await expect(scroll).toBeFocused()
    await page.keyboard.press('PageDown')
    await expect.poll(() => scroll.evaluate(element => element.scrollTop)).toBeGreaterThan(0)
    await page.keyboard.press('PageUp')
    await expect.poll(() => scroll.evaluate(element => element.scrollTop)).toBe(0)
    await page.locator('h1').click()
    await page.mouse.move(0, 0)
    await page.screenshot({ path: `${output}/table-${width}.png`, fullPage: true, animations: 'disabled' })
    await demo.screenshot({ path: `${output}/demo-table-${width}.png`, fullPage: true, animations: 'disabled' })
    expect(api.unexpected).toEqual([])
    await demo.close()
  })
}

test('152 个业务列、50 行完整返回，精确小数与来源列可横向查看', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 1050 })
  const api = await open(page, 'balancesheet', { [recordPath('balancesheet')]: request => response(request, 'balancesheet') })
  await query(page)
  await expect(table(page).locator('th')).toHaveCount(155)
  await expect(table(page).locator('.el-table__body tbody tr')).toHaveCount(50)
  await expect(table(page)).toContainText('12345678901234567890.123456789012345678')
  const scroll = table(page).locator('.el-scrollbar__wrap')
  const fixed = table(page).locator('.el-table__body td').first()
  const x = (await fixed.boundingBox()).x
  await scroll.evaluate(element => { element.scrollLeft = element.scrollWidth })
  await expect.poll(async () => (await fixed.boundingBox()).x).toBeCloseTo(x, 0)
  const last = table(page).locator('.el-table__body tr').first().locator('td').last()
  await expect(last).toBeInViewport()
  await expect(last).toHaveText('2026-08-07 20:34:56')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: `${output}/wide-table-1024.png`, fullPage: true, animations: 'disabled' })
  expect(api.unexpected).toEqual([])
})

test('长文本单行省略，键盘焦点和悬停均显示安全全文且可按 Escape 关闭', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 1050 })
  const api = await open(page, 'stock_company')
  await query(page)
  const text = table(page).getByText(/这是一段用于验证长文本提示/)
  await text.scrollIntoViewIfNeeded()
  const dimensions = await text.evaluate(el => ({ height: el.clientHeight, lineHeight: parseFloat(getComputedStyle(el).lineHeight), clipped: el.scrollWidth > el.clientWidth }))
  expect(dimensions.height).toBeLessThanOrEqual(Math.ceil(dimensions.lineHeight))
  expect(dimensions.clipped).toBe(true)
  await table(page).focus()
  await page.keyboard.press('Tab')
  await expect(table(page).locator('.el-scrollbar__wrap')).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(text).toBeFocused()
  const tooltip = page.getByRole('tooltip').filter({ hasText: '<strong>' })
  await expect(tooltip).toBeVisible()
  await expect(tooltip).toHaveText(await text.textContent())
  await expect(tooltip.locator('strong')).toHaveCount(0)
  const box = await tooltip.boundingBox()
  expect(box.width).toBeLessThanOrEqual(560)
  expect(box.x).toBeGreaterThanOrEqual(0)
  expect(box.x + box.width).toBeLessThanOrEqual(1024)
  await page.screenshot({ path: `${output}/long-text-1024.png`, fullPage: true, animations: 'disabled' })
  await page.keyboard.press('Escape')
  await expect(tooltip).not.toBeVisible()
  await page.locator('h1').click()
  await text.hover()
  await expect(tooltip).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(tooltip).not.toBeVisible()
  expect(api.unexpected).toEqual([])
})

test('短中文溢出也可查看全文，超长提示能用键盘读到末尾', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 1050 })
  const items = syntheticRecords('stock_company', 2)
  items[0].introduction = '中'.repeat(25)
  items[1].introduction = '连续长文本'.repeat(1200) + '结束标记'
  const api = await open(page, 'stock_company', { [recordPath('stock_company')]: request => response(request, 'stock_company', 2, items) })
  await query(page)
  const short = table(page).getByText(items[0].introduction, { exact: true })
  await expect(short).toHaveAttribute('tabindex', '0')
  await short.focus()
  await expect(page.getByRole('tooltip').filter({ hasText: items[0].introduction })).toBeVisible()
  await page.keyboard.press('Escape')
  const long = table(page).getByText(items[1].introduction, { exact: true })
  await long.focus()
  const tooltip = page.getByRole('tooltip').filter({ hasText: '结束标记' })
  await expect(tooltip).toBeVisible()
  const scrollTop = () => tooltip.evaluate(element => element.scrollTop)
  await page.keyboard.press('PageDown')
  await expect.poll(scrollTop).toBeGreaterThan(0)
  await page.keyboard.press('End')
  expect(await tooltip.evaluate(element => Math.abs(element.scrollHeight - element.clientHeight - element.scrollTop))).toBeLessThanOrEqual(1)
  await expect(long).toBeFocused()
  await page.keyboard.press('Home')
  await expect.poll(scrollTop).toBe(0)
  await page.keyboard.press('Escape')
  await expect(tooltip).not.toBeVisible()
  expect(api.unexpected).toEqual([])
})

test('分页和页大小使用已提交条件，键盘操作首尾页且不裁切服务端行数', async ({ page }) => {
  const api = await open(page, 'daily', { [recordPath('daily')]: request => response(request, 'daily') })
  const code = page.locator('[data-filter="tsCode"] input')
  await code.fill('000001.SZ')
  await query(page)
  await expect(table(page).locator('.el-table__body tbody tr')).toHaveCount(50)
  await code.fill('000002.SZ')
  const next = pager(page).getByRole('button', { name: /下一页/ })
  await next.focus()
  await page.keyboard.press('Enter')
  await expect(pager(page).getByRole('status')).toContainText('第 2 / 3 页')
  await expect(table(page).locator('.el-table__body td').first()).toHaveText('000051.SZ')
  await next.click()
  await expect(pager(page).getByRole('status')).toContainText('第 3 / 3 页')
  await expect(next).toBeDisabled()
  await expect(table(page).locator('.el-table__body tbody tr')).toHaveCount(1)
  const size = pager(page).getByRole('combobox')
  await size.focus()
  await page.keyboard.press('ArrowDown')
  await page.keyboard.press('End')
  await page.keyboard.press('Enter')
  await expect(pager(page).getByRole('status')).toContainText('第 1 / 2 页')
  await expect(table(page).locator('.el-table__body tbody tr')).toHaveCount(100)
  expect(requests(api).map(request => request.query)).toEqual([
    { tsCode: '000001.SZ', page: '1', pageSize: '50' },
    { tsCode: '000001.SZ', page: '2', pageSize: '50' },
    { tsCode: '000001.SZ', page: '3', pageSize: '50' },
    { tsCode: '000001.SZ', page: '1', pageSize: '100' },
  ])
  expect(api.unexpected).toEqual([])
})

test('空结果保留条数选择，大页码完整可见且不撑宽页面', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 1050 })
  let total = 0
  const api = await open(page, 'daily', { [recordPath('daily')]: request => response(request, 'daily', total) })
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('heading', { name: '未找到符合条件的数据' })).toBeVisible()
  await expect(pager(page).getByRole('status')).toHaveText('共 0 条，第 1 / 0 页')
  await expect(pager(page).getByRole('button', { name: /上一页/ })).toBeDisabled()
  await expect(pager(page).getByRole('button', { name: /下一页/ })).toBeDisabled()
  await expect(pager(page).getByRole('combobox')).toBeEnabled()
  await page.screenshot({ path: `${output}/empty-1024.png`, fullPage: true, animations: 'disabled' })
  total = 500000001
  await query(page)
  const lastPage = pager(page).locator('.el-pager .number').last()
  await expect(lastPage).toHaveText('10000001')
  await lastPage.click()
  await expect(pager(page).getByRole('status')).toHaveText('共 500000001 条，第 10000001 / 10000001 页')
  await expect(pager(page).getByRole('button', { name: /下一页/ })).toBeDisabled()
  expect(await pager(page).locator('.el-pager li').evaluateAll(elements => elements.every(element => element.scrollWidth <= element.clientWidth))).toBe(true)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: `${output}/large-page-1024.png`, fullPage: true, animations: 'disabled' })
  expect(api.unexpected).toEqual([])
})
