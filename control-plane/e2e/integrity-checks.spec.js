import { expect, test } from '@playwright/test'
import path from 'node:path'
import { CHECK_ID, SECOND_CHECK_ID, RESULT_ID, PENDING_KEY, example, savedReport, installIntegrityApi, submission, summary } from './integrity-checks.fixtures.js'

const output = path.resolve('node_modules/.cache/integrity-t11')
const confirm = page => page.getByRole('checkbox', { name: '我已确认以上检查口径' })
const start = page => page.getByRole('button', { name: '开始检查', exact: true })
const history = page => page.locator('section').filter({ has: page.getByRole('heading', { name: '检查历史', exact: true }) }).last()
async function fillSelection(page) {
  await page.getByLabel('股票代码', { exact: true }).fill('999999.sz，999999.SZ\n600000.sh')
  await page.getByRole('button', { name: '添加', exact: true }).click()
  await page.getByLabel('开始日期', { exact: true }).fill('2024-02-01')
  await page.getByLabel('结束日期', { exact: true }).fill('2024-02-29')
}
async function noOverflow(page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
}

for (const width of [1440, 1024, 390]) {
  test(`${width}px：无Token的本地检查保留全范围，键盘提交一次并进入报告`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 })
    const state = await installIntegrityApi(page)
    state.history = [summary()]
    await page.goto('/integrity')
    await expect(page.getByRole('heading', { name: '数据完整性', exact: true })).toBeVisible()
    await expect(page.getByText('下载不可用，本地检查可用', { exact: false })).toBeVisible()
    await expect(start(page)).toBeDisabled()
    await fillSelection(page)
    await expect(page.getByText('当前快照不能证明所选历史窗口完整', { exact: false })).toBeVisible()
    await expect(page.getByText('不按股票逐只检查', { exact: false })).toBeVisible()
    await expect(page.getByText('缺少覆盖规则', { exact: false })).toBeVisible()
    await expect(page.getByText('核对参考交易日历', { exact: false })).toBeVisible()
    await expect(history(page).getByRole('link', { name: '查看报告结论' })).toBeVisible()
    await noOverflow(page)
    await confirm(page).focus()
    await page.keyboard.press('Space')
    await expect(confirm(page)).toBeChecked()
    await page.screenshot({ path: `${output}/create-${width}.png`, fullPage: true })
    let release
    state.postGate = new Promise(resolve => { release = resolve })
    await start(page).focus()
    await page.keyboard.press('Enter')
    await expect(start(page)).toBeDisabled()
    await expect.poll(() => state.posts.length).toBe(1)
    expect(state.posts[0]).toMatchObject({ pluginId: 'tushare_pro', symbols: ['999999.SZ', '600000.SH'], startDate: '2024-02-01', endDate: '2024-02-29', apiNames: ['daily', 'stock_basic', 'trade_cal', 'unimplemented'] })
    release()
    await expect(page).toHaveURL(`/integrity/checks/${CHECK_ID}`)
    await expect(page.getByText(CHECK_ID, { exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: '数据完整性', exact: true })).toHaveAttribute('aria-current', 'page')
    await page.getByRole('link', { name: '返回数据完整性' }).click()
    await expect(history(page).getByRole('link', { name: '查看报告结论' })).toHaveAttribute('href', `/integrity/checks/${CHECK_ID}`)
    expect(state.posts).toHaveLength(1)
    expect(state.requests.some(request => request.path === `/api/v1/integrity-checks/${CHECK_ID}`)).toBe(true)
  })
}

test('空接口禁提交；能力变化保留移除接口和原范围，重新确认才允许新请求', async ({ page }) => {
  const state = await installIntegrityApi(page)
  await page.goto('/integrity')
  await fillSelection(page)
  await page.getByRole('button', { name: '清空', exact: true }).click()
  await expect(start(page)).toBeDisabled()
  await page.getByRole('button', { name: '全选', exact: true }).click()
  await confirm(page).check()
  state.outcome = 'definition-changed'
  state.capability = { ...state.capability, capabilityHash: 'b'.repeat(64), apis: state.capability.apis.slice(0, 3) }
  await start(page).click()
  await expect(page.getByText('检查口径已更新，请重新确认', { exact: false })).toBeVisible()
  await expect(confirm(page)).not.toBeChecked()
  await expect(page.getByLabel('开始日期', { exact: true })).toHaveValue('2024-02-01')
  await expect(page.getByRole('checkbox', { name: /unimplemented/ })).toBeChecked()
  await expect(start(page)).toBeDisabled()
  await page.getByRole('checkbox', { name: /unimplemented/ }).click()
  await expect(page.getByRole('checkbox', { name: /unimplemented/ })).toHaveCount(0)
  await confirm(page).check()
  state.outcome = 'success'
  await start(page).click()
  await expect(page).toHaveURL(`/integrity/checks/${CHECK_ID}`)
  expect(state.posts).toHaveLength(2)
  expect(state.posts[1].submissionId).not.toBe(state.posts[0].submissionId)
  expect(state.posts[0].apiNames).toContain('unimplemented')
  expect(state.posts[1]).toMatchObject({ capabilityHash: 'b'.repeat(64), symbols: ['999999.SZ', '600000.SH'], apiNames: ['daily', 'stock_basic', 'trade_cal'] })
})

test('响应丢失后按submissionId查回，找不到才显式原ID原载荷重发', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 900 })
  const state = await installIntegrityApi(page)
  state.outcome = 'lost-empty'
  await page.goto('/integrity')
  await fillSelection(page)
  await confirm(page).check()
  await start(page).click()
  const resend = page.getByRole('button', { name: '使用原请求重发' })
  await expect(resend).toBeEnabled()
  await expect(start(page)).toBeDisabled()
  await expect(page.getByLabel('股票代码', { exact: true })).toBeDisabled()
  expect(state.requests.some(request => request.query.submissionId === state.posts[0].submissionId)).toBe(true)
  state.recoveryFailure = true
  await page.getByRole('button', { name: '查询提交结果' }).click()
  await expect(page.getByRole('alert').filter({ hasText: '请求 ID' })).toHaveCount(2)
  await expect(resend).not.toBeEnabled()
  state.recoveryFailure = false
  await page.getByRole('button', { name: '查询提交结果' }).click()
  await expect(resend).toBeEnabled()
  await noOverflow(page)
  await page.screenshot({ path: `${output}/uncertain.png`, fullPage: true })
  state.outcome = 'success'
  await resend.focus()
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(`/integrity/checks/${CHECK_ID}`)
  expect(state.posts).toHaveLength(2)
  expect(state.posts[1]).toEqual(state.posts[0])
})

test('已受理但响应丢失和刷新后的缓存恢复均只GET查回', async ({ page }) => {
  const state = await installIntegrityApi(page)
  state.outcome = 'lost-accepted'
  await page.goto('/integrity')
  await fillSelection(page)
  await confirm(page).check()
  await start(page).click()
  await expect(page).toHaveURL(`/integrity/checks/${CHECK_ID}`)
  expect(state.posts).toHaveLength(1)
  const pending = state.posts[0]
  await page.evaluate(({ key, request }) => sessionStorage.setItem(key, JSON.stringify({ schemaVersion: 1, request })), { key: PENDING_KEY, request: pending })
  await page.goto('/integrity')
  await expect(page).toHaveURL(`/integrity/checks/${CHECK_ID}`)
  expect(state.posts).toHaveLength(1)
  expect(state.requests.filter(request => request.query.submissionId === pending.submissionId)).toHaveLength(2)
})

test('来源空态、失败可恢复；分类失败不会丢掉检查接口', async ({ page }) => {
  const state = await installIntegrityApi(page)
  state.sources = []
  await page.goto('/integrity')
  await expect(page.getByText('暂无数据源', { exact: true })).toBeVisible()
  await expect(history(page).getByRole('link', { name: '查看报告结论' })).toHaveCount(0)
  await noOverflow(page)
  await page.screenshot({ path: `${output}/empty.png`, fullPage: true })
  expect(state.requests.filter(request => request.path.endsWith('/integrity-capabilities'))).toHaveLength(0)
  state.sourceFailure = true
  await page.reload()
  await expect(page.getByRole('alert')).toContainText('请求 ID')
  state.sourceFailure = false
  state.sources = [{ pluginId: 'tushare_pro', displayName: 'Tushare Pro', enabled: true, downloadAvailable: false, credentialConfigured: false, unavailableReason: '缺Token' }]
  state.categoryFailure = true
  await page.getByRole('button', { name: /重新.*数据源|重新加载/ }).click()
  await expect(page.getByRole('checkbox', { name: /unimplemented/ })).toBeChecked()
  await fillSelection(page)
  await confirm(page).check()
  await expect(start(page)).toBeEnabled()
  state.categoryFailure = false
  await page.getByRole('button', { name: /重新.*分类/ }).click()
  await expect(page.getByText('行情数据', { exact: true })).toBeVisible()
  expect(state.posts).toHaveLength(0)
})

test('历史精确总数、分页筛选和错误重试不会推导数据结论或逐条查询详情', async ({ page }) => {
  const state = await installIntegrityApi(page)
  state.history = [summary()]
  state.total = '9007199254740993'
  await page.goto('/integrity')
  await expect(history(page)).toContainText('9007199254740993')
  await expect(history(page)).toContainText('450359962737050')
  await history(page).getByRole('button', { name: '下一页', exact: true }).click()
  await expect.poll(() => state.requests.filter(request => request.path === '/api/v1/integrity-checks').at(-1).query.page).toBe('2')
  await history(page).getByRole('button', { name: '上一页', exact: true }).click()
  await expect(history(page).getByRole('link', { name: '查看报告结论' })).toBeVisible()
  state.historyFailure = true
  await history(page).getByRole('button', { name: '刷新历史' }).click()
  await expect(history(page).getByRole('alert')).toContainText('请求 ID')
  await expect(history(page).getByRole('link', { name: '查看报告结论' })).toBeVisible()
  state.historyFailure = false
  state.total = null
  await history(page).getByRole('button', { name: /重新查询|重试/ }).click()
  await history(page).getByLabel('执行状态', { exact: true }).selectOption('FAILED')
  await expect.poll(() => state.requests.filter(request => request.path === '/api/v1/integrity-checks').at(-1).query).toMatchObject({ page: '1', status: 'FAILED' })
  await expect(history(page).getByRole('link', { name: '查看报告结论' })).toHaveCount(0)
  expect(state.requests.filter(request => request.path.includes('/integrity-checks/'))).toHaveLength(0)
  expect(state.posts).toHaveLength(0)
})

test('长名称与多股票在390px换行；非法报告编号不发请求', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 900 })
  const state = await installIntegrityApi(page)
  state.capability.apis[0].displayName = '具有很长说明的本地历史行情接口'.repeat(8)
  state.capability.apis[0].descriptor.limitations = ['完整性未知原因'.repeat(60)]
  state.history = [summary({ ...submission(), symbols: Array.from({ length: 30 }, (_, i) => `${String(i).padStart(6, '0')}.SZ`) })]
  await page.goto('/integrity')
  await fillSelection(page)
  await expect(history(page).getByRole('link', { name: '查看报告结论' })).toBeVisible()
  await noOverflow(page)
  await page.screenshot({ path: `${output}/long-390.png`, fullPage: true })
  const previous = state.requests.length
  await page.goto('/integrity/checks/invalid')
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page.getByRole('link', { name: '返回数据完整性' })).toBeVisible()
  expect(state.requests).toHaveLength(previous)
})

test('本地能力不可用和请求失败均禁止新检查，手动重新获取只GET', async ({ page }) => {
  const state = await installIntegrityApi(page)
  state.capability.localCheckAvailable = false
  state.capability.unavailableReason = '该来源未提供本地检查能力'
  state.capability.capabilityHash = null
  await page.goto('/integrity')
  await expect(page.getByText('该来源未提供本地检查能力', { exact: false })).toBeVisible()
  await expect(start(page)).toBeDisabled()
  state.capabilityFailure = true
  await page.reload()
  await expect(page.getByRole('alert').filter({ hasText: '请求 ID' })).toBeVisible()
  await expect(start(page)).toBeDisabled()
  state.capabilityFailure = false
  state.capability.localCheckAvailable = true
  state.capability.unavailableReason = null
  state.capability.capabilityHash = 'a'.repeat(64)
  await page.getByRole('button', { name: '重新获取', exact: true }).click()
  await expect(page.getByRole('checkbox', { name: /daily/ })).toBeChecked()
  expect(state.posts).toHaveLength(0)
})

test('损坏的会话缓存明确显示错误，填写并确认前不会自动替代旧请求', async ({ page }) => {
  const state = await installIntegrityApi(page)
  await page.addInitScript(key => sessionStorage.setItem(key, '{broken'), PENDING_KEY)
  await page.goto('/integrity')
  await expect(page.getByRole('alert').filter({ hasText: '保存的检查请求无法读取' }).first()).toBeVisible()
  await expect(start(page)).toBeDisabled()
  expect(state.posts).toHaveLength(0)
  await fillSelection(page)
  await confirm(page).check()
  await start(page).click()
  await expect(page).toHaveURL(`/integrity/checks/${CHECK_ID}`)
  expect(state.posts).toHaveLength(1)
})

test('未点击添加的股票文本参与提交校验，会话保存失败时不会POST', async ({ page }) => {
  const state = await installIntegrityApi(page)
  await page.addInitScript(key => {
    window.integrityStorageBlocked = true
    const setItem = Storage.prototype.setItem
    Storage.prototype.setItem = function (name, value) {
      if (name === key && window.integrityStorageBlocked) throw new DOMException('Storage full', 'QuotaExceededError')
      return setItem.call(this, name, value)
    }
  }, PENDING_KEY)
  await page.goto('/integrity')
  await page.getByLabel('股票代码', { exact: true }).fill('999999.sz，999999.SZ 600000.sh')
  await page.getByLabel('开始日期', { exact: true }).fill('2024-02-01')
  await page.getByLabel('结束日期', { exact: true }).fill('2024-02-29')
  await confirm(page).check()
  await start(page).click()
  await expect(page.getByRole('alert').filter({ hasText: '无法保存检查请求' }).first()).toBeVisible()
  expect(state.posts).toHaveLength(0)
  await expect(page.getByLabel('股票代码', { exact: true })).toBeDisabled()
  await expect(page.getByLabel('开始日期', { exact: true })).toBeDisabled()
  await page.evaluate(() => { window.integrityStorageBlocked = false })
  await start(page).click()
  await expect(page).toHaveURL(`/integrity/checks/${CHECK_ID}`)
  expect(state.posts).toHaveLength(1)
  expect(state.posts[0].symbols).toEqual(['999999.SZ', '600000.SH'])
})

test('数据源加载失败不妨碍查回缓存中的原范围和原提交', async ({ page }) => {
  const state = await installIntegrityApi(page)
  const request = submission()
  state.sourceFailure = true
  state.recoveryFailure = true
  await page.addInitScript(({ key, request }) => sessionStorage.setItem(key, JSON.stringify({ schemaVersion: 1, request })), { key: PENDING_KEY, request })
  await page.goto('/integrity')
  await expect(page.getByText(request.submissionId, { exact: false })).toBeVisible()
  await expect(page.getByText('2024-02-01', { exact: false })).toBeVisible()
  await expect(page.getByRole('button', { name: '查询提交结果' })).toBeEnabled()
  state.recoveryFailure = false
  state.accepted = summary(request)
  await page.getByRole('button', { name: '查询提交结果' }).click()
  await expect(page).toHaveURL(`/integrity/checks/${CHECK_ID}`)
  expect(state.posts).toHaveLength(0)
  expect(state.requests.filter(item => item.path.endsWith('/integrity-capabilities'))).toHaveLength(0)
})

test('迟到分类不撤销用户清空；重新勾选按能力原顺序提交', async ({ page }) => {
  const state = await installIntegrityApi(page)
  state.sources.push({ ...state.sources[0], pluginId: 'disabled_source', displayName: '停用来源', enabled: false })
  let release
  state.categoryGate = new Promise(resolve => { release = resolve })
  await page.goto('/integrity')
  await page.getByLabel('数据源', { exact: true }).selectOption('tushare_pro')
  await expect(page.getByRole('checkbox', { name: /daily/ })).toBeChecked()
  await page.getByRole('button', { name: '清空', exact: true }).click()
  release()
  await expect(page.getByText('行情数据', { exact: true })).toBeVisible()
  await expect(page.getByRole('checkbox', { name: /daily/ })).not.toBeChecked()
  await expect(start(page)).toBeDisabled()
  await page.getByRole('button', { name: '全选', exact: true }).click()
  await page.getByRole('checkbox', { name: /daily/ }).uncheck()
  await page.getByRole('checkbox', { name: /daily/ }).check()
  await fillSelection(page)
  await confirm(page).check()
  await start(page).click()
  await expect(page).toHaveURL(`/integrity/checks/${CHECK_ID}`)
  expect(state.posts[0].apiNames).toEqual(['daily', 'stock_basic', 'trade_cal', 'unimplemented'])
})


test('报告深链接读取已存结论，计算完成仍显示有问题', async ({ page }) => {
  const state = await installIntegrityApi(page)
  await page.goto(`/integrity/checks/${CHECK_ID}`)
  await expect(page.getByText('计算已完成', { exact: true })).toBeVisible()
  await expect(page.getByRole('region', { name: '报告总览', exact: true }).getByText('数据结论：有问题', { exact: true })).toBeVisible()
  await expect(results(page)).toContainText('覆盖率 95%')
  expect(state.requests.filter(item => item.path.endsWith('integrity-capabilities'))).toHaveLength(0)
  expect(state.posts).toHaveLength(0)
})

const reportOutput = path.resolve('../docs/verification/data-integrity-t12')
const results = page => page.getByRole('region', { name: '检查结果', exact: true })
const issues = page => page.getByRole('region', { name: '问题明细', exact: true })
const lastQuery = (state, suffix) => state.requests.filter(item => item.path.endsWith(suffix)).at(-1)?.query
const recheck = page => page.getByRole('link', { name: '再次检查', exact: true }).or(page.getByRole('button', { name: '再次检查', exact: true }))

for (const width of [1440, 1024, 390]) {
  test(`${width}px报告：完整键、保存规则、未知日期筛选与重置可键盘操作`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 })
    const state = await installIntegrityApi(page)
    await page.goto(`/integrity/checks/${CHECK_ID}`)
    await expect(results(page)).toContainText('覆盖率 95%')
    await results(page).locator('summary').filter({ hasText: '保存的检查依据' }).click()
    await expect(results(page)).toContainText('保存的覆盖规则')
    await expect(results(page)).toContainText('保存时有20个预期键')
    await noOverflow(page)
    await results(page).getByRole('button', { name: '查看问题', exact: true }).focus()
    await page.keyboard.press('Enter')
    await expect(issues(page)).toBeVisible()
    await expect(issues(page)).toContainText('1.230000000000000000')
    await expect(issues(page)).toContainText('9223372036854775807')
    await expect(issues(page)).toContainText('false')
    await expect(issues(page)).toContainText('2023-12-31')
    await expect(issues(page)).toContainText('保存的覆盖规则')
    await expect(issues(page)).toContainText('未保存规则名称')
    await expect(issues(page)).toContainText('日期未知')
    expect(lastQuery(state, '/issues')).toMatchObject({ page: '1', pageSize: '20', resultId: RESULT_ID })
    await noOverflow(page)
    await page.locator('section .table-wrap').evaluateAll(elements => elements.forEach(element => { element.scrollLeft = 0 }))
    await page.screenshot({ path: `${reportOutput}/report-${width}.png`, fullPage: true })
    await issues(page).getByLabel('问题开始日期', { exact: true }).fill('2024-02-01')
    await expect(issues(page).getByText('日期筛选仅包含可定位日期的问题，已排除日期未知项。', { exact: true })).toHaveCount(0)
    await expect(issues(page)).toContainText('无法定位主日期，请保留整个检查范围。')
    await issues(page).getByRole('button', { name: '查询问题', exact: true }).click()
    await expect.poll(() => lastQuery(state, '/issues')).toMatchObject({ dateFrom: '2024-02-01', resultId: RESULT_ID, page: '1' })
    await expect(issues(page)).toContainText('已排除日期未知项')
    await expect(issues(page).getByText('无法定位主日期，请保留整个检查范围。', { exact: true })).toHaveCount(0)
    await issues(page).getByLabel('问题开始日期', { exact: true }).fill('')
    await expect(issues(page)).toContainText('已排除日期未知项')
    await issues(page).getByRole('button', { name: '重置问题筛选', exact: true }).click()
    await expect(issues(page)).toContainText('无法定位主日期，请保留整个检查范围。')
    expect(lastQuery(state, '/issues')).not.toHaveProperty('dateFrom')
    await issues(page).getByRole('button', { name: '关闭问题明细', exact: true }).click()
    await expect(issues(page)).toHaveCount(0)
    await expect(results(page).getByRole('button', { name: '查看问题', exact: true })).toBeFocused()
    expect(state.requests.filter(item => item.path.endsWith('integrity-capabilities'))).toHaveLength(0)
    expect(state.posts).toHaveLength(0)
  })
}

test('结果和问题精确分页，未应用筛选草稿不影响翻页，空页不推导通过', async ({ page }) => {
  const state = await installIntegrityApi(page)
  const data = state.reports[CHECK_ID]
  data.results = Array.from({ length: 21 }, (_, index) => {
    const result = structuredClone(data.results[0])
    result.resultId = `33333333-3333-4333-8333-${String(index + 1).padStart(12, '0')}`
    result.report.scope.symbol = index < 20 ? '000001.SZ' : '600000.SH'
    return result
  })
  data.issues.forEach(item => { item.resultId = data.results[0].resultId })
  data.detail.scope.symbols = ['000001.SZ', '600000.SH']
  data.resultsTotal = '9007199254740993'
  await page.goto(`/integrity/checks/${CHECK_ID}`)
  await expect(results(page)).toContainText('9007199254740993')
  await results(page).getByLabel('结果股票', { exact: true }).selectOption('600000.SH')
  await results(page).getByRole('button', { name: '下一页', exact: true }).click()
  await expect.poll(() => lastQuery(state, '/results')).toMatchObject({ page: '2' })
  expect(lastQuery(state, '/results')).not.toHaveProperty('symbol')
  await results(page).getByLabel('结果股票', { exact: true }).selectOption('600000.SH')
  await results(page).getByRole('button', { name: '查询结果', exact: true }).click()
  await expect.poll(() => lastQuery(state, '/results')).toMatchObject({ page: '1', symbol: '600000.SH' })
  await expect(results(page).getByRole('button', { name: '查看问题', exact: true })).toHaveCount(1)
  await results(page).getByLabel('数据结论', { exact: true }).selectOption('PASS')
  await results(page).getByRole('button', { name: '查询结果', exact: true }).click()
  await expect(results(page)).toContainText('没有符合条件的检查单元')
  await noOverflow(page)
  await page.screenshot({ path: `${reportOutput}/empty-results.png`, fullPage: true })
  await expect(page.getByRole('region', { name: '报告总览', exact: true }).getByText('数据结论：有问题', { exact: true })).toBeVisible()
  await results(page).getByRole('button', { name: '重置筛选', exact: true }).click()
  await results(page).getByRole('button', { name: '查看问题', exact: true }).first().click()
  await expect(issues(page)).toContainText('确认缺失2024-02-20的数据')
  data.issues = Array.from({ length: 21 }, (_, index) => ({ ...structuredClone(data.issues[0]), issueId: String(index + 1) }))
  await issues(page).getByRole('button', { name: '查询问题', exact: true }).click()
  await issues(page).getByRole('button', { name: '下一页', exact: true }).click()
  await expect.poll(() => lastQuery(state, '/issues')).toMatchObject({ page: '2', resultId: data.results[0].resultId })
  await issues(page).getByLabel('每页条数', { exact: true }).selectOption('50')
  await expect.poll(() => lastQuery(state, '/issues')).toMatchObject({ page: '1', pageSize: '50' })
  expect(state.posts).toHaveLength(0)
})

test('轮询遇错保留快照且停止，重新连接只GET，终态不再轮询', async ({ page }) => {
  await page.clock.install()
  const state = await installIntegrityApi(page)
  state.reports[CHECK_ID].detail.status = 'RUNNING'
  state.reports[CHECK_ID].detail.finishedAt = null
  await page.goto(`/integrity/checks/${CHECK_ID}`)
  await expect(results(page)).toContainText('覆盖率 95%')
  state.resultsFailure = true
  await page.clock.runFor(2100)
  await expect(page.getByText('连接已中断，以下为上次读取结果', { exact: false })).toBeVisible()
  await expect(results(page)).toContainText('请求 ID')
  await expect(results(page)).toContainText('覆盖率 95%')
  const before = state.requests.length
  await page.clock.runFor(6000)
  expect(state.requests).toHaveLength(before)
  state.resultsFailure = false
  state.reports[CHECK_ID].detail.status = 'COMPLETED'
  await page.getByRole('button', { name: '重新连接', exact: true }).click()
  await expect(page.getByText('计算已完成', { exact: true })).toBeVisible()
  const terminal = state.requests.length
  await page.clock.runFor(6000)
  expect(state.requests).toHaveLength(terminal)
  expect(state.posts).toHaveLength(0)
  await results(page).getByRole('button', { name: '查看问题', exact: true }).click()
  await expect(issues(page)).toContainText('确认缺失2024-02-20的数据')
  state.issuesFailure = true
  await issues(page).getByRole('button', { name: '查询问题', exact: true }).click()
  await expect(issues(page)).toContainText('请求 ID')
  await expect(issues(page)).toContainText('确认缺失2024-02-20的数据')
  state.issuesFailure = false
  await issues(page).getByRole('button', { name: '重新查询问题', exact: true }).click()
  await expect(issues(page).getByRole('alert').filter({ hasText: '请求 ID' })).toHaveCount(0)
  expect(state.posts).toHaveLength(0)
})

test('再次检查刷新能力但保留原范围，新ID新报告并且旧报告可返回', async ({ page }) => {
  const state = await installIntegrityApi(page)
  const original = structuredClone(state.reports[CHECK_ID])
  state.capability.capabilityHash = 'b'.repeat(64)
  state.nextCheckId = SECOND_CHECK_ID
  await page.goto(`/integrity/checks/${CHECK_ID}`)
  await recheck(page).click()
  await expect(page).toHaveURL(`/integrity?fromCheckId=${CHECK_ID}`)
  await expect(page.getByRole('checkbox', { name: /daily/ })).toBeChecked()
  await expect(page.getByRole('checkbox', { name: /stock_basic/ })).not.toBeChecked()
  await expect(confirm(page)).not.toBeChecked()
  await expect(start(page)).toBeDisabled()
  await expect(page.getByLabel('开始日期', { exact: true })).toHaveValue('2024-02-01')
  expect(state.posts).toHaveLength(0)
  await confirm(page).check()
  await start(page).click()
  await expect(page).toHaveURL(`/integrity/checks/${SECOND_CHECK_ID}`)
  await expect(page.getByText('计算已完成', { exact: true })).toBeVisible()
  expect(state.posts).toHaveLength(1)
  expect(state.posts[0]).toMatchObject({ symbols: original.detail.scope.symbols, apiNames: ['daily'], capabilityHash: 'b'.repeat(64) })
  expect(state.posts[0].submissionId).not.toBe(original.detail.submissionId)
  await page.goto(`/integrity/checks/${CHECK_ID}`)
  await expect(results(page)).toContainText('覆盖率 95%')
  expect(state.reports[CHECK_ID]).toEqual(original)
})

test('复制条件失败保留可重试错误；未确认的原提交优先，不发新请求', async ({ page }) => {
  const state = await installIntegrityApi(page)
  state.detailFailure = true
  await page.goto(`/integrity?fromCheckId=${CHECK_ID}`)
  await expect(page.getByRole('alert').filter({ hasText: '请求 ID' }).first()).toBeVisible()
  await expect(start(page)).toBeDisabled()
  state.detailFailure = false
  await page.getByRole('button', { name: '重新读取原条件', exact: true }).click()
  await expect(page.getByRole('checkbox', { name: /daily/ })).toBeChecked()
  const pending = submission()
  state.recoveryFailure = true
  await page.evaluate(({ key, request }) => sessionStorage.setItem(key, JSON.stringify({ schemaVersion: 1, request })), { key: PENDING_KEY, request: pending })
  const before = state.requests.filter(item => item.path === `/api/v1/integrity-checks/${CHECK_ID}`).length
  await page.reload()
  await expect(page.getByText('请先确认上一次检查的提交结果，再从报告发起新检查', { exact: false })).toBeVisible()
  await expect(page.getByRole('button', { name: '查询提交结果' })).toBeVisible()
  expect(state.requests.filter(item => item.path === `/api/v1/integrity-checks/${CHECK_ID}`)).toHaveLength(before)
  expect(state.posts).toHaveLength(0)
})

for (const [stateName, exampleName, status] of [
  ['全UNKNOWN', 'unknownResult', 'UNKNOWN'], ['全N/A', 'notApplicableResult', 'NOT_APPLICABLE'], ['中断与长值', 'incompleteResult', 'FAIL'],
]) {
  for (const width of [1440, 1024, 390]) test(`${stateName}在${width}px不溢出且不冒充通过`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    const state = await installIntegrityApi(page)
    const data = state.reports[CHECK_ID]
    const result = example(exampleName)
    const apiName = status === 'NOT_APPLICABLE' ? 'trade_cal' : 'daily'
    data.detail.scope.apiNames = [apiName]
    result.report.scope.datasetKey = { pluginId: data.detail.pluginId, apiName }
    result.report.descriptor.datasetKey = { ...result.report.scope.datasetKey }
    if (status === 'NOT_APPLICABLE') result.report.descriptor.dateLabel = '不适用'
    data.results = [result]
    data.detail.overallStatus = status
    data.detail.statusCounts = { PASS: '0', FAIL: '0', WARN: '0', UNKNOWN: '0', NOT_APPLICABLE: '0', [status]: '1' }
    if (stateName === '中断与长值') {
      data.detail.status = 'INTERRUPTED'
      data.detail.errorMessage = '执行预算到期，未完成范围仍保留'
      data.detail.notRunUnits = '1'
      result.report.overallStatus = 'FAIL'
      result.report.statistics.missingCount = '9223372036854775807'
      result.report.descriptor.rules[0].displayName = '已保存的很长规则名称'.repeat(20)
      result.report.message = '统计未完成的具体原因'.repeat(25)
    }
    await page.goto(`/integrity/checks/${CHECK_ID}`)
    await expect(results(page).getByRole('button', { name: '查看问题', exact: true })).toBeVisible()
    if (status === 'NOT_APPLICABLE') await expect(results(page)).toContainText('范围说明，不按股票逐只检查')
    if (stateName === '中断与长值') {
      await expect(page.getByText('已中断', { exact: true })).toBeVisible()
      await expect(results(page)).toContainText('统计未完成')
      await expect(results(page)).toContainText('问题明细不完整')
      await expect(results(page)).toContainText('9223372036854775807')
    }
    await results(page).locator('summary').filter({ hasText: '保存的检查依据' }).click()
    await noOverflow(page)
    await page.screenshot({ path: `${reportOutput}/${stateName.replaceAll('/', '-')}-${width}.png`, fullPage: true })
    expect(state.posts).toHaveLength(0)
  })
}

test('重查时失效接口与当前限额不能静默裁剪原范围', async ({ page }) => {
  const state = await installIntegrityApi(page)
  state.capability.apis = state.capability.apis.filter(api => api.apiName !== 'daily')
  await page.goto(`/integrity?fromCheckId=${CHECK_ID}`)
  await expect(page.getByRole('checkbox', { name: /daily/ })).toBeChecked()
  await expect(page.getByText('所选接口已失效：daily', { exact: false }).first()).toBeVisible()
  await expect(start(page)).toBeDisabled()
  expect(state.posts).toHaveLength(0)
  state.capability.apis.unshift({ ...example('availableCapability').apis[0], apiName: 'daily' })
  state.capability.limits.maxRangeDays = 1
  await page.reload()
  await expect(page.getByLabel('开始日期', { exact: true })).toHaveValue('2024-02-01')
  await expect(page.getByLabel('结束日期', { exact: true })).toHaveValue('2024-02-29')
  await expect(page.getByText('日期范围不能超过 1 天', { exact: false }).first()).toBeVisible()
  await expect(start(page)).toBeDisabled()
  expect(state.posts).toHaveLength(0)
})

test('问题日期反向范围就地拒绝，不请求或把关联日期当作主日期', async ({ page }) => {
  const state = await installIntegrityApi(page)
  await page.goto(`/integrity/checks/${CHECK_ID}`)
  await results(page).getByRole('button', { name: '查看问题', exact: true }).click()
  await expect(issues(page)).toContainText('确认缺失2024-02-20的数据')
  await issues(page).getByLabel('问题开始日期', { exact: true }).fill('2024-02-28')
  await issues(page).getByLabel('问题结束日期', { exact: true }).fill('2024-02-01')
  const before = state.requests.filter(item => item.path.endsWith('/issues')).length
  await issues(page).getByRole('button', { name: '查询问题', exact: true }).click()
  await expect(issues(page).getByRole('alert')).toContainText('日期格式无效或起止顺序错误')
  expect(state.requests.filter(item => item.path.endsWith('/issues'))).toHaveLength(before)
  await issues(page).getByLabel('问题结束日期', { exact: true }).fill('2024-02-29')
  await issues(page).getByRole('button', { name: '查询问题', exact: true }).click()
  await expect(issues(page)).toContainText('当前条件下没有问题记录')
  expect(lastQuery(state, '/issues')).toMatchObject({ dateFrom: '2024-02-28', dateTo: '2024-02-29' })
  await expect(page.getByRole('region', { name: '报告总览', exact: true }).getByText('数据结论：有问题', { exact: true })).toBeVisible()
})

test('报告不存在给出请求ID和GET重试，恢复后可独立刷新', async ({ page }) => {
  const state = await installIntegrityApi(page)
  const saved = state.reports[CHECK_ID]
  delete state.reports[CHECK_ID]
  await page.goto(`/integrity/checks/${CHECK_ID}`)
  await expect(page.getByRole('alert').first()).toContainText('请求 ID')
  await expect(page.getByRole('link', { name: '返回数据完整性' })).toBeVisible()
  state.reports[CHECK_ID] = saved
  await page.getByRole('button', { name: '重新连接', exact: true }).click()
  await expect(results(page)).toContainText('覆盖率 95%')
  await page.reload()
  await expect(results(page)).toContainText('覆盖率 95%')
  expect(state.posts).toHaveLength(0)
})

test('含大写十六进制的合法报告编号可复制，不会一直加载', async ({ page }) => {
  const state = await installIntegrityApi(page)
  const id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
  state.reports[id] = savedReport(undefined, id)
  await page.goto(`/integrity?fromCheckId=${id.toUpperCase()}`)
  await expect(page.getByRole('checkbox', { name: /daily/ })).toBeChecked()
  await expect(page.getByText('正在读取原报告条件…', { exact: true })).toHaveCount(0)
  await expect(confirm(page)).not.toBeChecked()
  expect(state.requests.some(item => item.path === `/api/v1/integrity-checks/${id}`)).toBe(true)
  expect(state.posts).toHaveLength(0)
})

test('旧来源已消失时保留原范围并说明不可提交原因', async ({ page }) => {
  const state = await installIntegrityApi(page)
  state.sources = []
  await page.goto(`/integrity?fromCheckId=${CHECK_ID}`)
  await expect(page.getByText('暂无数据源', { exact: true })).toBeVisible()
  await expect(page.getByText('tushare_pro', { exact: false }).first()).toBeVisible()
  await expect(page.getByText('2024-02-01', { exact: false }).first()).toBeVisible()
  expect(state.posts).toHaveLength(0)
})


test('复制的来源停用后可明确选择新来源并经确认提交', async ({ page }) => {
  const state = await installIntegrityApi(page)
  state.sources[0].enabled = false
  state.sources.push({ ...state.sources[0], pluginId: 'fixture', displayName: 'Fixture', enabled: true })
  state.nextCheckId = SECOND_CHECK_ID
  await page.goto(`/integrity?fromCheckId=${CHECK_ID}`)
  await expect(page.getByText('原数据源目前停用或不存在', { exact: false })).toBeVisible()
  await expect(start(page)).toBeDisabled()
  state.capability = JSON.parse(JSON.stringify(state.capability).replaceAll('tushare_pro', 'fixture'))
  await page.getByLabel('数据源', { exact: true }).selectOption('fixture')
  await expect(page.getByRole('checkbox', { name: /daily/ })).toBeChecked()
  await expect(confirm(page)).not.toBeChecked()
  await confirm(page).check()
  await expect(start(page)).toBeEnabled()
  await start(page).click()
  await expect(page).toHaveURL(`/integrity/checks/${SECOND_CHECK_ID}`)
  expect(state.posts).toHaveLength(1)
  expect(state.posts[0]).toMatchObject({ pluginId: 'fixture', symbols: ['000001.SZ'], startDate: '2024-02-01', endDate: '2024-02-29' })
})

test('明确拒绝后读取报告条件期间及失败时阻止旧范围提交，重试可恢复', async ({ page }) => {
  const state = await installIntegrityApi(page)
  await page.addInitScript(({ key, request }) => sessionStorage.setItem(key, JSON.stringify({ schemaVersion: 1, request })), { key: PENDING_KEY, request: submission() })
  await page.goto(`/integrity?fromCheckId=${CHECK_ID}`)
  state.outcome = 'rejected'
  await page.getByRole('button', { name: '使用原请求重发', exact: true }).click()
  const read = page.getByRole('button', { name: '读取这份报告的条件', exact: true })
  await expect(read).toBeVisible()
  let release
  state.detailGate = new Promise(resolve => { release = resolve })
  state.detailFailure = true
  await read.click()
  await expect(page.getByText('正在读取原报告条件…', { exact: true })).toBeVisible()
  await expect(start(page)).toBeDisabled()
  expect(state.posts).toHaveLength(1)
  release()
  const error = page.getByRole('alert').filter({ hasText: '原报告条件读取失败' })
  await expect(error).toContainText('请求 ID')
  await expect(start(page)).toBeDisabled()
  state.detailGate = null
  state.detailFailure = false
  await page.getByRole('button', { name: '重新读取原条件', exact: true }).click()
  await expect(page.getByText('已复制原报告的固定执行范围', { exact: true })).toBeVisible()
  await expect(confirm(page)).not.toBeChecked()
  state.outcome = 'success'
  state.nextCheckId = SECOND_CHECK_ID
  await confirm(page).check()
  await start(page).click()
  await expect(page).toHaveURL(`/integrity/checks/${SECOND_CHECK_ID}`)
  expect(state.posts).toHaveLength(2)
  expect(state.posts[1].submissionId).not.toBe(state.posts[0].submissionId)
})

test('被拒绝的原请求遇到非法复制编号也清空旧范围并阻止提交', async ({ page }) => {
  const state = await installIntegrityApi(page)
  await page.addInitScript(({ key, request }) => sessionStorage.setItem(key, JSON.stringify({ schemaVersion: 1, request })), { key: PENDING_KEY, request: submission() })
  await page.goto('/integrity?fromCheckId=invalid')
  state.outcome = 'rejected'
  await page.getByRole('button', { name: '使用原请求重发', exact: true }).click()
  await expect(page.getByLabel('开始日期', { exact: true })).toHaveValue('2024-02-01')
  await page.getByRole('button', { name: '读取这份报告的条件', exact: true }).click()
  await expect(page.getByRole('alert').filter({ hasText: '原报告条件读取失败' })).toBeVisible()
  await expect(page.getByLabel('开始日期', { exact: true })).toHaveValue('')
  await expect(confirm(page)).not.toBeChecked()
  await expect(start(page)).toBeDisabled()
  expect(state.requests.filter(item => item.path === '/api/v1/integrity-checks/invalid')).toHaveLength(0)
  expect(state.posts).toHaveLength(1)
})
