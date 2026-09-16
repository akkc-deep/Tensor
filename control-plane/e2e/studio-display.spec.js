import { expect, test } from '@playwright/test'
import { EXPECTED, installApi, syntheticRecords } from './ui-redesign.fixtures.js'
import { selectDownloadApi } from './catalog-helpers.js'
import { TASK_ID, task, batch } from './download-tasks.fixtures.js'

async function capture(page, name, width) {
  await page.mouse.move(0, 0)
  await page.evaluate(() => document.activeElement?.blur())
  await page.screenshot({ path: `node_modules/.cache/studio-display/${name}-${width}.png`, fullPage: true, animations: 'disabled' })
}

test('显示比例可保存、恢复自动适配，并在小窗口中保持设置可用', async ({ page }) => {
  await page.setViewportSize({ width: 2560, height: 1440 })
  await installApi(page)
  await page.goto('/settings')
  const scale = page.getByLabel('显示比例', { exact: true })
  await expect(scale).toHaveValue('auto')
  await expect(page.getByTestId('display-status')).toContainText('125%')
  await scale.selectOption('150')
  await page.reload()
  await expect(scale).toHaveValue('150')
  await expect(page.getByTestId('display-status')).toContainText('150%')
  await page.setViewportSize({ width: 1024, height: 900 })
  await expect(page.getByTestId('display-status')).toContainText('100%')
  await expect(scale).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(1024)
  await scale.selectOption('auto')
  await page.setViewportSize({ width: 3840, height: 2160 })
  await expect(page.getByTestId('display-status')).toContainText('200%')
})

test('输入框的键盘焦点与 Demo 一致', async ({ page, context }) => {
  await page.setViewportSize({ width: 1440, height: 1000 })
  await installApi(page)
  await page.goto('/downloads')
  await selectDownloadApi(page, 'daily')
  await page.locator('[data-mode="SINGLE"]').click()
  const demo = await context.newPage()
  await demo.goto('/ui-demos.html')
  const styles = []
  for (const [surface, selector] of [[page, '[name="ts_code"]'], [demo, '.parameter-grid input[type="text"]']]) {
    await surface.keyboard.press('Tab')
    await surface.locator(selector).focus()
    await surface.waitForTimeout(200)
    styles.push(await surface.locator(selector).evaluate(el => {
      const css = getComputedStyle(el)
      return { border: css.borderTopColor, shadow: css.boxShadow, outline: css.outlineStyle }
    }))
  }
  expect(styles[0]).toEqual(styles[1])
  await demo.close()
})

test('按钮悬停、按压、禁用和减少动态效果与 Demo 一致', async ({ page, context }) => {
  await page.setViewportSize({ width: 1440, height: 1000 })
  await installApi(page)
  await page.goto('/downloads')
  await selectDownloadApi(page, 'daily')
  await page.locator('[data-mode="SINGLE"]').click()
  await page.locator('[name="ts_code"]').fill('000001.SZ')
  await page.locator('[name="trade_date"]').fill('2026-08-07')
  const demo = await context.newPage()
  await demo.goto('/ui-demos.html')
  const properties = ['backgroundColor', 'color', 'borderRadius', 'fontSize', 'fontWeight', 'transform', 'transitionDuration']
  const style = locator => locator.evaluate((el, keys) => {
    const css = getComputedStyle(el)
    return Object.fromEntries(keys.map(key => [key, css[key]]))
  }, properties)
  async function compareButtons(actual, reference) {
    for (const state of ['hover', 'active']) {
      for (const button of [actual, reference]) {
        await button.hover()
        if (state === 'active') await button.page().mouse.down()
      }
      await page.waitForTimeout(200)
      expect(await style(actual)).toEqual(await style(reference))
      for (const button of [actual, reference]) {
        await button.page().mouse.move(0, 0)
        if (state === 'active') await button.page().mouse.up()
      }
    }
  }
  await compareButtons(page.locator('.download-action'), demo.locator('.form-actions .button'))
  await selectDownloadApi(page, 'stock_basic')
  await demo.locator('.catalog-list button').filter({ has: demo.getByText('stock_basic', { exact: true }) }).click()
  for (const surface of [page, demo]) {
    const disabled = surface.locator('[data-mode="RANGE"]')
    await expect(disabled).toBeDisabled()
    await expect(disabled).toHaveCSS('opacity', '0.55')
  }
  await page.getByRole('link', { name: '数据查看', exact: true }).click()
  await demo.getByRole('button', { name: '数据查看', exact: true }).click()
  await page.getByRole('combobox', { name: '数据集', exact: true }).fill('daily')
  await page.getByRole('option').filter({ has: page.getByText('daily', { exact: true }) }).click()
  await compareButtons(page.locator('.dataset-view__actions .button'), demo.locator('.query-form .button'))
  for (const surface of [page, demo]) await surface.emulateMedia({ reducedMotion: 'reduce' })
  await compareButtons(page.locator('.dataset-view__actions .button'), demo.locator('.query-form .button'))
  await demo.close()
})

for (const [width, height, scale] of [[1920, 1080, 1], [2560, 1440, 1.25], [3840, 2160, 2]]) {
  test(`${width}px 自动适配：控件、下拉定位、详情和数据查询可用`, async ({ page }) => {
    await page.setViewportSize({ width, height })
    const api = await installApi(page, {
      'GET /api/v1/download-tasks': () => ({ status: 200, body: { page: 1, pageSize: 20, total: 1n, items: [task()] } }),
      [`GET /api/v1/download-tasks/${TASK_ID}`]: () => ({ status: 200, body: task() }),
      [`GET /api/v1/download-tasks/${TASK_ID}/batches`]: () => ({ status: 200, body: { page: 1, pageSize: 20, total: 3n, items: [batch(0), batch(1), batch(2)] } }),
    })
    await page.goto('/downloads')
    await selectDownloadApi(page, 'daily')
    await page.locator('[data-mode="SINGLE"]').click()
    const input = page.locator('[name="ts_code"]')
    await expect(input).toBeVisible()
    expect((await input.boundingBox()).height).toBeCloseTo(43 * scale, 0)
    const source = page.locator('.catalog-source .el-select__wrapper')
    expect(await source.evaluate(el => parseFloat(getComputedStyle(el).fontSize))).toBe(14 * scale)
    await source.click()
    const option = page.getByRole('option', { name: 'Tushare Pro', exact: true })
    await expect(option).toBeVisible()
    const anchor = await source.boundingBox(), popup = await option.boundingBox()
    expect(Math.abs(anchor.x - popup.x)).toBeLessThan(30 * scale)
    expect(popup.y).toBeGreaterThanOrEqual(anchor.y + anchor.height)
    expect(popup.y - anchor.y - anchor.height).toBeLessThan(60 * scale)
    await option.click()
    await expect(option).not.toBeVisible()
    await page.getByRole('searchbox', { name: '搜索接口' }).fill('')
    await capture(page, 'downloads', width)
    await page.locator(`a[href="/downloads/tasks/${TASK_ID}"]`).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    await expect(dialog.getByRole('heading', { name: 'daily', exact: true })).toBeVisible()
    await expect(dialog.locator('.download-batch-table ol > li')).toHaveCount(3)
    await expect(dialog.getByLabel('已结束批次进度')).toHaveAttribute('value', '100')
    const box = await dialog.boundingBox()
    expect(box.width).toBeCloseTo(430 * scale, 0)
    expect(box.height).toBe(height)
    expect(box.x + box.width).toBeCloseTo(width, 0)
    expect((await dialog.getByRole('button', { name: '关闭任务详情' }).boundingBox()).width).toBeCloseTo(34 * scale, 0)
    expect(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true)
    await capture(page, 'detail', width)
    await dialog.getByRole('button', { name: '关闭任务详情' }).click()
    await page.getByRole('link', { name: '数据查看', exact: true }).click()
    await page.getByRole('combobox', { name: '数据集', exact: true }).fill('daily')
    await page.getByRole('option').filter({ has: page.getByText('daily', { exact: true }) }).click()
    await page.getByRole('button', { name: '查询', exact: true }).click()
    await expect(page.getByRole('region', { name: '数据表格，可横向滚动' })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width)
    await capture(page, 'datasets', width)
    await page.getByRole('link', { name: '外观设置', exact: true }).click()
    await expect(page.getByTestId('display-status')).toContainText(`${scale * 100}%`)
    await capture(page, 'settings', width)
    expect(api.unexpected).toEqual([])
  })
}

test('2K 切换到 4K 后表格列宽和多行全文提示同步适配', async ({ page }) => {
  await page.setViewportSize({ width: 2560, height: 1440 })
  const items = syntheticRecords('stock_company', 1)
  items[0].introduction = items[0].introduction.repeat(10)
  const api = await installApi(page, {
    'GET /api/v1/data-sources/tushare_pro/datasets/stock_company/records': ({ requestId }) => ({ status: 200, body: {
      requestId, pluginId: 'tushare_pro', apiName: 'stock_company', page: 1, pageSize: 50, totalElements: 1, totalPages: 1,
      columns: [...EXPECTED.get('stock_company').columns.map(column => column.name), 'source_plugin', 'source_api', 'ingested_at'], items,
    } }),
  })
  await page.goto('/datasets')
  await page.getByRole('combobox', { name: '数据集', exact: true }).fill('stock_company')
  await page.getByRole('option').filter({ has: page.getByText('stock_company', { exact: true }) }).click()
  await page.getByRole('button', { name: '查询', exact: true }).click()
  const table = page.getByRole('region', { name: '数据表格，可横向滚动' })
  const text = table.getByText(/这是一段用于验证长文本提示/)
  for (const [width, height, scale] of [[2560, 1440, 1.25], [3840, 2160, 2]]) {
    await page.setViewportSize({ width, height })
    await expect(table.locator('td').first()).toHaveCSS('font-size', `${14 * scale}px`)
    expect((await table.locator('th').first().boundingBox()).width).toBeGreaterThanOrEqual(140 * scale)
    await text.scrollIntoViewIfNeeded()
    await text.focus()
    const tooltip = page.getByRole('tooltip').filter({ hasText: '<strong>' })
    await expect(tooltip).toBeVisible()
    await expect(tooltip).toHaveCSS('opacity', '1')
    await expect(tooltip).toHaveCSS('font-size', `${12 * scale}px`)
    await expect(tooltip).toHaveCSS('line-height', `${20 * scale}px`)
    const box = await tooltip.boundingBox()
    expect(box.height).toBeGreaterThan(40 * scale)
    expect(box.width).toBeLessThanOrEqual(560 * scale)
    expect(box.x).toBeGreaterThanOrEqual(0)
    expect(box.x + box.width).toBeLessThanOrEqual(width)
    expect(box.y).toBeGreaterThanOrEqual(0)
    expect(box.y + box.height).toBeLessThanOrEqual(height)
    await page.screenshot({ path: `node_modules/.cache/studio-display/tooltip-${width}.png`, fullPage: true })
    await page.keyboard.press('Escape')
    await expect(tooltip).not.toBeVisible()
    await text.blur()
  }
  expect(api.unexpected).toEqual([])
})
