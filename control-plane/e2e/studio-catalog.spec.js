import { expect, test } from '@playwright/test'
import path from 'node:path'
import { APIS, DATA_SOURCE, installApi, apiFailure } from './ui-redesign.fixtures.js'
import { selectDownloadApi } from './catalog-helpers.js'

const output = path.resolve('node_modules/.cache/studio-t02')

async function metrics(locator) {
  return locator.evaluate(element => {
    const rect = element.getBoundingClientRect()
    const style = getComputedStyle(element)
    return { width: rect.width, height: rect.height, fontSize: style.fontSize, padding: style.padding, color: style.color, background: style.backgroundColor }
  })
}

for (const width of [1024, 1280, 1440]) {
  test(`${width}px：真实目录搜索分类、长列表键盘选择及 Demo 对照`, async ({ page, context }) => {
    await page.setViewportSize({ width, height: 1000 })
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    const api = await installApi(page)
    await page.goto('/downloads')
    const buttons = page.locator('.catalog-list button')
    await expect(buttons).toHaveCount(40)
    await expect(page.locator('.catalog-count')).toHaveText('40')
    await page.getByRole('combobox', { name: '接口分类' }).selectOption('行情与估值')
    await expect(buttons).toHaveCount(7)
    await page.getByRole('searchbox', { name: '搜索接口' }).fill('  DAI  ')
    await expect(buttons).toHaveCount(2)
    await expect(page.locator('.api-select footer')).toHaveText('2 个接口 · Tushare Pro')
    await page.getByRole('searchbox', { name: '搜索接口' }).fill('日线行情')
    await expect(buttons).toHaveCount(1)
    await buttons.first().focus()
    await page.keyboard.press('Enter')
    await expect(page.locator('.selected-api-heading h2')).toHaveText('日线行情')
    await expect(buttons.first()).toHaveAttribute('aria-pressed', 'true')
    await page.getByRole('searchbox', { name: '搜索接口' }).fill('不存在')
    await expect(page.locator('.catalog-empty')).toContainText('没有匹配')
    await expect(page.locator('.selected-api-heading code')).toHaveText('daily')
    await page.getByRole('combobox', { name: '接口分类' }).selectOption('')
    await page.getByRole('searchbox', { name: '搜索接口' }).fill('')
    await expect(buttons).toHaveCount(40)
    expect(await page.locator('.catalog-list').evaluate(el => el.scrollHeight > el.clientHeight)).toBe(true)
    const last = buttons.last()
    const lastCode = await last.locator('code').textContent()
    await last.focus()
    await page.keyboard.press('Enter')
    await expect(last).toBeInViewport()
    await expect(page.locator('.selected-api-heading code')).toHaveText(lastCode)
    expect(await page.locator('.catalog-list').evaluate(el => el.scrollTop)).toBeGreaterThan(0)
    await selectDownloadApi(page, 'daily')
    await page.getByRole('searchbox', { name: '搜索接口' }).fill('')
    await page.locator('.catalog-list button.selected').scrollIntoViewIfNeeded()
    await page.getByRole('searchbox', { name: '搜索接口' }).blur()
    await page.mouse.move(0, 0)
    await expect(page.locator('[data-parameter]').first()).toBeVisible()

    const demo = await context.newPage()
    await demo.setViewportSize({ width, height: 1000 })
    await demo.goto('/ui-demos.html')
    for (const selector of ['.catalog-search', '.category-select', '.catalog-list button.selected', '.selected-api-heading', '.api-glyph']) {
      const actual = await metrics(page.locator(selector))
      const reference = await metrics(demo.locator(selector))
      expect(actual.width, `${selector} width`).toBeCloseTo(reference.width, 0)
      expect(actual.height, `${selector} height`).toBeCloseTo(reference.height, 0)
      expect(actual.padding, `${selector} padding`).toBe(reference.padding)
      expect(actual.fontSize, `${selector} font`).toBe(reference.fontSize)
      expect(actual.color, `${selector} color`).toBe(reference.color)
      expect(actual.background, `${selector} background`).toBe(reference.background)
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: `${output}/catalog-${width}.png`, fullPage: true })
    await demo.screenshot({ path: `${output}/demo-catalog-${width}.png`, fullPage: true })
    expect(api.requests.some(request => request.path === '/api/v1/data-sources/tushare_pro/apis')).toBe(true)
    expect(api.unexpected).toEqual([])
    expect(errors).toEqual([])
    await demo.close()
  })
}

test('目录失败可重试，能力迟到失败不覆盖新选择', async ({ page }) => {
  let attempt = 0
  let release
  const old = new Promise(resolve => { release = resolve })
  const api = await installApi(page, {
    'GET /api/v1/data-sources/tushare_pro/apis': request => ++attempt === 1
      ? apiFailure(request.requestId, 'SOURCE_TIMEOUT') : { status: 200, body: APIS },
    'GET /api/v1/data-sources/tushare_pro/apis/daily/download-capabilities': async request => {
      await old
      return apiFailure(request.requestId, 'SOURCE_TIMEOUT')
    },
  })
  await page.goto('/downloads')
  await expect(page.getByRole('heading', { name: '接口目录加载失败' })).toBeVisible()
  await expect(page.getByText(/请求 ID：/)).toBeVisible()
  await page.getByRole('button', { name: '重新加载目录' }).click()
  await selectDownloadApi(page, 'daily')
  await expect(page.getByRole('heading', { name: '正在加载接口能力' })).toBeVisible()
  await expect(page.getByRole('button', { name: /^(开始(?:批量)?下载|正在创建…|正在查找…)$/ })).toBeDisabled()
  await selectDownloadApi(page, 'weekly')
  await expect(page.locator('[data-parameter]').first()).toBeVisible()
  const completed = page.waitForResponse('**/apis/daily/download-capabilities')
  release()
  await completed
  await expect(page.locator('.selected-api-heading code')).toHaveText('weekly')
  await expect(page.getByRole('heading', { name: '接口能力加载失败' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: /^(开始(?:批量)?下载|正在创建…|正在查找…)$/ })).toBeEnabled()
  expect(api.unexpected).toEqual([])
})

test('不可用来源显示原因并阻止目录选择', async ({ page }) => {
  const api = await installApi(page, {
    'GET /api/v1/data-sources': { status: 200, body: [{ ...DATA_SOURCE, downloadAvailable: false, unavailableReason: '尚未配置凭证' }] },
  })
  await page.goto('/downloads')
  await expect(page.locator('.catalog-list button')).toHaveCount(40)
  await expect(page.getByText('尚未配置凭证')).toBeVisible()
  await expect(page.getByRole('searchbox', { name: '搜索接口' })).toBeDisabled()
  for (const button of await page.locator('.catalog-list button').all()) await expect(button).toBeDisabled()
  await expect(page.getByRole('button', { name: /^(开始(?:批量)?下载|正在创建…|正在查找…)$/ })).toBeDisabled()
  expect(api.requests.some(request => request.path.endsWith('/download-capabilities'))).toBe(false)
})


test('目录选择辅助函数兼容既有业务测试的正则名称', async ({ page }) => {
  await installApi(page)
  await page.goto('/downloads')
  await selectDownloadApi(page, /^日线行情daily$/)
  await expect(page.locator('.selected-api-heading code')).toHaveText('daily')
})
