import { mount } from '@vue/test-utils'
import IntegrityResultsTable from './IntegrityResultsTable.vue'

const stats = { actualCount: 9223372036854775807n, expectedCount: 20n, matchedCount: 19n,
  missingCount: 1n, suspectedMissingCount: 0n, extraCount: 1n,
  requiredFieldIssueCount: null, coverageRate: '0.999999' }
const result = (changes = {}) => ({
  resultId: '33333333-3333-4333-8333-333333333333', checkId: '11111111-1111-4111-8111-111111111111',
  report: {
    scope: { datasetKey: { pluginId: 'fixture', apiName: 'daily' }, symbol: '000001.SZ', startDate: '2026-09-01', endDate: '2026-09-02', snapshotStartedAt: '2026-09-17T00:00:01Z' },
    descriptor: { scopeKind: 'STOCK_DATE', dateLabel: '交易日期', dateField: 'trade_date', limitations: ['只适用于存档'], dependencies: [{ datasetKey: { pluginId: 'fixture', apiName: 'trade_cal' }, purpose: '交易日' }] },
    definitionHash: 'a'.repeat(64), publishedRange: { startDate: '2026-09-01', endDate: '2026-09-02' },
    unitStatus: 'ERROR', coverageStatus: 'FAIL', keyStatus: 'PASS', fieldStatus: 'UNKNOWN', overallStatus: 'FAIL', statistics: stats,
    ruleResults: [{ descriptor: { ruleId: 'fixture.coverage', version: '7', displayName: '存档覆盖规则', dimension: 'COVERAGE', description: '保存的依据', dependencies: [], requiredColumns: [] }, status: 'FAIL', reasonCode: 'MISSING_KEYS', message: '缺一日', statistics: stats, evidence: [] }],
    evidence: [{ source: 'fixture', ruleVersion: '7', range: { startDate: '2026-09-01', endDate: '2026-09-02' }, readAt: '2026-09-17T00:00:01Z', summary: 'saved evidence' }],
    finishedAt: null, incomplete: true, issuesComplete: false, reasonCode: 'READ_FAILED', message: 'partial result', ...changes,
  },
})

function mounted(item = result(), criteria = { page: 2, pageSize: 20, overallStatus: 'FAIL' }) {
  return mount(IntegrityResultsTable, { props: {
    page: { page: 2, pageSize: 20, total: 61n, items: [item] }, criteria,
    symbols: ['000001.SZ', '600000.SH'], apiNames: ['daily', 'trade_cal'], loading: false, error: null,
  } })
}

it('renders one saved result with exact numbers, independent statuses, and truncation warnings', () => {
  const wrapper = mounted()
  expect(wrapper.findAll('tbody tr:not(.basis-row)')).toHaveLength(1)
  expect(wrapper.text()).toContain('9223372036854775807')
  expect(wrapper.text()).toContain('99.9999%')
  expect(wrapper.text()).toContain('执行错误')
  expect(wrapper.text()).toContain('有问题')
  expect(wrapper.text()).toContain('统计未完成')
  expect(wrapper.text()).toContain('问题明细不完整')
  expect(wrapper.text()).toContain('无法计算')
})

it('emits only applied result filters and keeps draft filters out of paging', async () => {
  const wrapper = mounted()
  await wrapper.get('select[aria-label="结果股票"]').setValue('600000.SH')
  await wrapper.get('select[aria-label="结果接口"]').setValue('')
  await wrapper.get('button[aria-label="查询结果"]').trigger('click')
  expect(wrapper.emitted('query')[0][0]).toEqual({ page: 1, pageSize: 20, symbol: '600000.SH', overallStatus: 'FAIL' })
  await wrapper.get('select[aria-label="结果股票"]').setValue('000001.SZ')
  await wrapper.get('button[aria-label="下一页"]').trigger('click')
  expect(wrapper.emitted('update:page')).toEqual([[3]])
})

it('resets filters and exposes the selected saved result', async () => {
  const wrapper = mounted()
  await wrapper.get('button[aria-label="重置筛选"]').trigger('click')
  expect(wrapper.emitted('query')[0][0]).toEqual({ page: 1, pageSize: 20 })
  await wrapper.get('button[aria-label="查看问题"]').trigger('click')
  expect(wrapper.emitted('select-result')[0][0].resultId).toBe(result().resultId)
})

it('shows non-stock and missing-descriptor explanations plus saved basis', async () => {
  const wrapper = mounted(result({ scope: { ...result().report.scope, symbol: null }, descriptor: null, unitStatus: 'NOT_RUN' }))
  expect(wrapper.text()).toContain('范围说明，不按股票逐只检查')
  expect(wrapper.text()).toContain('覆盖规则未实现')
  expect(wrapper.text()).toContain('未运行')
  await wrapper.get('summary').trigger('click')
  expect(wrapper.text()).toContain('存档覆盖规则')
  expect(wrapper.text()).toContain('saved evidence')
})

it('shows a retained-page error and does not infer pass from an empty page', async () => {
  const wrapper = mounted()
  await wrapper.setProps({ error: Object.assign(new Error('offline'), { requestId: 'results-request' }) })
  expect(wrapper.text()).toContain('results-request')
  await wrapper.setProps({ page: { page: 1, pageSize: 20, total: 0n, items: [] }, error: null })
  expect(wrapper.text()).toContain('没有符合条件的检查单元')
  expect(wrapper.text()).not.toContain('数据通过')
})
