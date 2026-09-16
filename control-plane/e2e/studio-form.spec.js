import { expect, test } from '@playwright/test'
import path from 'node:path'
import { EXPECTED, installApi, rangeCapability, successDownload } from './ui-redesign.fixtures.js'
import { selectDownloadApi } from './catalog-helpers.js'

const output = path.resolve('node_modules/.cache/studio-t03')
const capabilityPath = 'GET /api/v1/data-sources/tushare_pro/apis/daily/download-capabilities'
const capabilities = () => ({ single: { available: true, parameters: EXPECTED.get('daily').parameters }, range: rangeCapability('daily') })
const posts = (api) => api.requests.filter(({ method, path }) => method === 'POST' && path === '/api/v1/download-tasks')

async function open(page, overrides = {}) {
  const api = await installApi(page, overrides)
  await page.goto('/downloads')
  await selectDownloadApi(page, 'daily')
  await expect(page.locator('.mode-control')).toBeVisible()
  return api
}

async function metrics(locator) {
  return locator.evaluate(element => {
    const rect = element.getBoundingClientRect()
    const style = getComputedStyle(element)
    return { width: rect.width, height: rect.height, fontSize: style.fontSize, color: style.color, background: style.backgroundColor }
  })
}

for (const width of [1024, 1280, 1440]) {
  test(`${width}px：单次／批量表单与 Demo 对照`, async ({ page, context }) => {
    await page.setViewportSize({ width, height: 1100 })
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    const api = await open(page)
    await page.getByRole('searchbox', { name: '搜索接口' }).fill('')
    const demo = await context.newPage()
    await demo.setViewportSize({ width, height: 1100 })
    await demo.goto('/ui-demos.html')
    await page.addStyleTag({ content: '* { transition: none !important; animation: none !important; }' })
    await demo.addStyleTag({ content: '* { transition: none !important; animation: none !important; }' })
    for (const mode of ['SINGLE', 'RANGE']) {
      await page.locator(`[data-mode="${mode}"]`).click()
      await demo.locator(`[data-mode="${mode}"]`).click()
      await expect(page.locator(`[data-mode="${mode}"]`)).toHaveAttribute('aria-pressed', 'true')
      const selectors = ['.mode-control', '.mode-control button.selected']
      if (mode === 'RANGE') selectors.push('.range-fields', '.range-fields input')
      for (const selector of selectors) {
        const actual = await metrics(page.locator(selector).first())
        const reference = await metrics(demo.locator(selector).first())
        expect(actual.width, `${selector} width`).toBeCloseTo(reference.width, 0)
        expect(actual.height, `${selector} height`).toBeCloseTo(reference.height, 0)
        expect(actual.fontSize, `${selector} font`).toBe(reference.fontSize)
        expect(actual.color, `${selector} color`).toBe(reference.color)
        expect(actual.background, `${selector} background`).toBe(reference.background)
      }
      const stock = page.locator('[data-parameter="ts_code"] input')
      await stock.fill('000001.SZ')
      await page.locator(`[data-mode="${mode}"]`).click()
      await expect(stock).toHaveValue('000001.SZ')
      await stock.fill('')
      await page.locator(`[data-mode="${mode}"]`).focus()
      expect(await page.locator(`[data-mode="${mode}"]`).evaluate(el => getComputedStyle(el).outlineStyle)).toBe('solid')
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
      await expect(page.locator('.form-parameters')).not.toContainText('批次预览')
      await page.locator(`[data-mode="${mode}"]`).blur()
      await demo.locator(`[data-mode="${mode}"]`).blur()
      await page.mouse.move(0, 0)
      await demo.mouse.move(0, 0)
      // Capture equal, valid input states; Demo validates RANGE immediately.
      await stock.fill('000001.SZ')
      await demo.locator('.parameter-grid input[type="text"]').fill('000001.SZ')
      for (const surface of [page, demo]) {
        const dates = surface.locator('.studio-form input[type="date"]')
        for (let index = 0; index < await dates.count(); index++) {
          await dates.nth(index).fill(mode === 'RANGE' && index === 0 ? '2026-06-01' : '2026-08-07')
        }
        await dates.last().blur()
      }
      if (width === 1440 || width === 1024 && mode === 'RANGE') {
        await page.screenshot({ path: `${output}/form-${mode.toLowerCase()}-${width}.png`, fullPage: true })
        await demo.screenshot({ path: `${output}/demo-${mode.toLowerCase()}-${width}.png`, fullPage: true })
      }
    }
    expect(api.unexpected).toEqual([])
    expect(errors).toEqual([])
    await demo.close()
  })
}

for (const availability of ['UNSUPPORTED', 'NEEDS_VERIFICATION']) {
  test(`${availability}：禁用批量并显示真实原因`, async ({ page }) => {
    const capability = capabilities()
    capability.range = availability === 'UNSUPPORTED' ? rangeCapability('index_classify') : {
      ...capability.range, availability, unavailableReason: '当前来源的区间规则待验证',
      completenessRule: { kind: 'UNKNOWN', rowLimit: null, evidence: null },
    }
    const api = await open(page, { [capabilityPath]: { status: 200, body: capability } })
    await expect(page.locator('[data-mode="RANGE"]')).toBeDisabled()
    await expect(page.locator('[data-mode="RANGE"]')).toContainText(availability === 'UNSUPPORTED' ? '不支持' : '待验证')
    await expect(page.locator('.form-parameters')).toContainText(capability.range.unavailableReason)
    await expect(page.locator('[data-mode="SINGLE"]')).toHaveAttribute('aria-pressed', 'true')
    expect(posts(api)).toEqual([])
    expect(api.unexpected).toEqual([])
  })
}

for (const type of ['DATE', 'MONTH']) {
  test(`${type}：键盘输入不完整的选填日期阻止提交，清空后可省略`, async ({ page }) => {
    const capability = capabilities()
    capability.single.parameters = [{ name: 'optional_date', label: '选填时间', type, required: false }]
    const api = await open(page, { [capabilityPath]: { status: 200, body: capability } })
    await page.locator('[data-mode="SINGLE"]').click()
    const input = page.getByLabel('选填时间', { exact: true })
    await input.focus()
    await input.press('ArrowLeft')
    await input.press('ArrowLeft')
    await input.pressSequentially('02')
    expect(await input.evaluate(el => ({ value: el.value, badInput: el.validity.badInput }))).toEqual({ value: '', badInput: true })
    await page.getByRole('button', { name: /^(开始(?:批量)?下载|正在创建…|正在查找…)$/, exact: true }).click()
    await expect(input).toHaveAttribute('aria-invalid', 'true')
    await expect(input).toBeFocused()
    expect(posts(api)).toHaveLength(0)
    await input.fill(type === 'MONTH' ? '2026-09' : '2026-09-04')
    await input.fill('')
    await page.getByRole('button', { name: /^(开始(?:批量)?下载|正在创建…|正在查找…)$/, exact: true }).click()
    await expect(page.getByRole('heading', { name: '任务已接收', exact: true })).toBeVisible()
    expect(posts(api)[0].body.params).toEqual({})
    expect(api.unexpected).toEqual([])
  })
}

test('真实元数据的枚举、代码规则和倒序日期字段生成规范化 RANGE 请求', async ({ page }) => {
  const capability = capabilities()
  capability.range.parameters = [
    { name: 'until', label: '结束日期', type: 'DATE_RANGE_MEMBER', relatedParameter: 'since', required: true },
    { name: 'stock', label: '证券代码', type: 'TS_CODE', required: true, pattern: '^[0-9]{6}\\.(SZ|SH|BJ)$' },
    { name: 'exchange', label: '交易所', type: 'ENUM', required: false, allowedValues: ['SSE', 'SZSE'] },
    { name: 'since', label: '开始日期', type: 'DATE_RANGE_MEMBER', relatedParameter: 'until', required: true },
  ]
  capability.range.startParameter = 'since'
  capability.range.endParameter = 'until'
  const api = await open(page, {
    [capabilityPath]: { status: 200, body: capability },
    'POST /api/v1/download-tasks': request => successDownload(request),
  })
  await page.getByRole('button', { name: /^(开始(?:批量)?下载|正在创建…|正在查找…)$/, exact: true }).click()
  await expect(page.getByLabel('证券代码', { exact: true })).toBeFocused()
  await page.getByLabel('证券代码', { exact: true }).fill('ABC.US')
  await page.getByLabel('开始日期', { exact: true }).fill('2026-09-06')
  await page.getByLabel('结束日期', { exact: true }).fill('2026-09-04')
  await page.getByRole('button', { name: /^(开始(?:批量)?下载|正在创建…|正在查找…)$/, exact: true }).click()
  await expect(page.getByText('输入格式不正确', { exact: true })).toBeVisible()
  await expect(page.getByText('开始日期不得晚于结束日期', { exact: true })).toBeVisible()
  expect(posts(api)).toHaveLength(0)
  await page.getByLabel('证券代码', { exact: true }).fill(' 000001.sz ')
  await page.getByLabel('结束日期', { exact: true }).fill('2026-09-06')
  await page.getByLabel('交易所', { exact: true }).selectOption('SZSE')
  await page.getByLabel('交易所', { exact: true }).selectOption('')
  await page.getByRole('button', { name: /^(开始(?:批量)?下载|正在创建…|正在查找…)$/, exact: true }).click()
  await expect(page.getByRole('heading', { name: '任务已接收', exact: true })).toBeVisible()
  expect(posts(api)[0].body).toMatchObject({ mode: 'RANGE', params: { stock: '000001.SZ', since: '20260906', until: '20260906' } })
  expect(api.unexpected).toEqual([])
})
