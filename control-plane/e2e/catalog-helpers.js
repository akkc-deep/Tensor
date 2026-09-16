import { expect } from '@playwright/test'

export async function selectDownloadApi(page, name) {
  const query = typeof name === 'string' ? name.match(/\(([^)]+)\)$/)?.[1] ?? name : ''
  const search = page.getByRole('searchbox', { name: '搜索接口' })
  await expect(search).toBeEnabled()
  await page.getByRole('combobox', { name: '接口分类' }).selectOption('')
  await search.fill(query)
  const button = page.getByRole('group', { name: '数据接口', exact: true }).getByRole('button')
    .filter(query ? { has: page.getByText(query, { exact: true }) } : { hasText: name })
  await expect(button).toHaveCount(1)
  await button.click()
  await expect(button).toHaveAttribute('aria-pressed', 'true')
}
