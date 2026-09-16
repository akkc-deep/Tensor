import { selectDownloadApi } from './catalog-helpers.js'
import { expect, test } from '@playwright/test'
import path from 'node:path'
import { installApi } from './ui-redesign.fixtures.js'

const output = path.resolve('node_modules/.cache/studio-t01')

async function geometry(page, selector, offset = 0) {
  return page.locator(selector).evaluate((element, offset) => {
    const box = element.getBoundingClientRect()
    const css = getComputedStyle(element)
    return {
      x: box.x, y: box.y - offset, width: box.width, height: box.height,
      fontSize: css.fontSize, lineHeight: css.lineHeight, color: css.color,
      background: css.backgroundColor, padding: css.padding,
      columns: css.gridTemplateColumns,
    }
  }, offset)
}

for (const width of [1280, 1440]) {
  test(`${width}px：正式外壳与 Demo 对照、设置持久化及历史导航`, async ({ page, context }) => {
    await page.setViewportSize({ width, height: 1000 })
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    const api = await installApi(page)
    const demo = await context.newPage()
    await demo.setViewportSize({ width, height: 1000 })
    await demo.goto('/ui-demos.html')
    await page.goto('/downloads')
    await expect(page.getByRole('heading', { name: '下载工作台', exact: true })).toBeVisible()
    await selectDownloadApi(page, 'daily')
    await page.getByRole('button', { name: '单次下载', exact: true }).click()
    await page.locator('[data-parameter="ts_code"] input').fill('000001.SZ')
    await page.locator('[data-parameter="trade_date"] input').fill('2026-08-07')
    await page.locator('[data-parameter="trade_date"] input').press('Enter')
    const banner = (await demo.locator('.demo-banner').boundingBox()).height
    for (const [actual, reference] of [
      ['.top-nav', '.top-nav'], ['.workspace-bar', '.workspace-topline'],
      ['.page-heading h1', '.page-heading h1'], ['.studio-layout', '.studio-layout'],
    ]) {
      const received = await geometry(page, actual)
      const expected = await geometry(demo, reference, banner)
      for (const key of ['x', 'y', 'width']) expect(received[key], `${actual}.${key}`).toBeCloseTo(expected[key], 0)
      if (actual !== '.studio-layout') expect(received.height, actual).toBeCloseTo(expected.height, 0)
      if (actual === '.studio-layout') expect(received.columns).toBe(expected.columns)
      expect(received.fontSize, actual).toBe(expected.fontSize)
      expect(received.color, actual).toBe(expected.color)
    }
    for (const item of [page, demo]) {
      await expect(item.locator('.studio-layout')).toBeVisible()
      expect(await item.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    }
    await page.screenshot({ path: `${output}/downloads-${width}.png`, fullPage: true })
    await demo.screenshot({ path: `${output}/demo-downloads-${width}.png`, fullPage: true })
    await page.getByRole('link', { name: '外观设置', exact: true }).click()
    await demo.getByRole('button', { name: '外观设置', exact: true }).click()
    for (const selector of ['.settings-intro', '.accent-setting', '.color-options']) {
      const received = await geometry(page, selector)
      const expected = await geometry(demo, selector, banner)
      for (const key of ['x', 'y', 'width', 'height']) expect(received[key], `${selector}.${key}`).toBeCloseTo(expected[key], 0)
    }
    await expect(page.getByRole('heading', {name: '外观设置', level: 1})).toBeVisible()
    await expect(page.getByText(/Demo|本地交互预览|示例数据/)).toHaveCount(0)
    await page.screenshot({ path: `${output}/settings-${width}.png`, fullPage: true })
    await demo.screenshot({ path: `${output}/demo-settings-${width}.png`, fullPage: true })
    await page.goBack()
    await expect(page.locator('[data-parameter="ts_code"] input')).toHaveValue('000001.SZ')
    await expect(page.locator('[data-parameter="trade_date"] input')).toHaveValue('2026-08-07')
    await page.goForward()
    await expect(page.getByRole('heading', { name: '外观设置', level: 1 })).toBeVisible()
    await page.getByRole('button', {name:'使用主题色 #28745a'}).click()
    await page.reload()
    await expect(page.getByRole('button', {name:'使用主题色 #28745a'})).toHaveAttribute('aria-pressed', 'true')
    expect(await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--tensor-accent').trim())).toBe('#28745a')
    await page.getByRole('button', {name:'恢复默认'}).click()
    await expect(page.getByRole('button', {name:'使用主题色 #3565b6'})).toHaveAttribute('aria-pressed', 'true')
    await page.getByRole('link', {name:'数据查看', exact:true}).click()
    await expect(page.getByRole('heading', {name:'数据查看', level:1})).toBeVisible()
    await page.goBack()
    await expect(page.getByRole('heading', {name:'外观设置', level:1})).toBeVisible()
    await page.goForward()
    await expect(page.getByRole('heading', {name:'数据查看', level:1})).toBeVisible()
    await page.reload()
    await expect(page.getByRole('link', {name:'数据查看', exact:true})).toHaveAttribute('aria-current','page')
    await page.keyboard.press('Tab')
    await expect(page.getByRole('link', {name:'跳转到工作区'})).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(page.locator('#workspace')).toBeFocused()
    expect(api.unexpected).toEqual([])
    expect(errors).toEqual([])
    await demo.close()
  })
}

test('1024px：大量任务的末页按钮在右栏内可见且可操作', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 1000 })
  const api = await installApi(page, {
    'GET /api/v1/download-tasks': ({ query }) => ({ status: 200, body: {
      page: Number(query.page), pageSize: Number(query.pageSize), total: 2_000_000_000n, items: [],
    } }),
  })
  await page.goto('/downloads')
  const last = page.locator('.studio-tasks .el-pager li.number').filter({ hasText: /^100000000$/ })
  await expect(last).toBeVisible()
  const column = await page.locator('.studio-tasks').boundingBox()
  const button = await last.boundingBox()
  expect(button.x + button.width).toBeLessThanOrEqual(column.x + column.width)
  await last.click()
  await expect(page.locator('.studio-tasks [data-total]')).toContainText('第 100000000 / 100000000 页')
  expect(api.requests.some(request => request.query.page === '100000000')).toBe(true)
  expect(api.unexpected).toEqual([])
})
