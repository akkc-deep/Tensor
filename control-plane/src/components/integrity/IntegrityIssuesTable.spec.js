import { mount } from '@vue/test-utils'
import IntegrityIssuesTable from './IntegrityIssuesTable.vue'

const RESULT_ID = '33333333-3333-4333-8333-333333333333'
const selected = (changes = {}) => ({
  resultId: RESULT_ID,
  report: {
    scope: { symbol: '000001.SZ', datasetKey: { pluginId: 'fixture', apiName: 'daily' } },
    issuesComplete: false,
    ruleResults: [{ descriptor: { ruleId: 'fixture.coverage', version: '7', displayName: '存档覆盖规则' } }],
    ...changes,
  },
})
const issue = (changes = {}) => ({
  issueId: 9223372036854775807n, resultId: RESULT_ID, ruleId: 'fixture.coverage', ruleVersion: '7',
  issue: {
    type: 'MISSING', status: 'FAIL', symbol: '000001.SZ', apiName: 'daily', dateField: 'trade_date', date: '2026-09-01',
    businessKey: { ts_code: '000001.SZ', amount: '1234567890.123400', serial: 9223372036854775807n, active: true, note: null },
    field: null, relatedDates: { ann_date: '2026-09-02', end_date: '2026-06-30' }, reasonCode: 'MISSING_KEY', message: '缺少记录',
    evidence: [{ source: 'fixture', ruleVersion: '7', range: { startDate: '2026-09-01', endDate: '2026-09-02' }, readAt: '2026-09-17T00:00:00Z', summary: '完整键证据' }], incomplete: false,
    ...changes,
  },
})
function mounted(item = issue(), criteria = { page: 1, pageSize: 20, resultId: RESULT_ID }) {
  return mount(IntegrityIssuesTable, { props: {
    result: selected(), criteria, page: { page: criteria.page, pageSize: criteria.pageSize, total: 1n, items: [item] }, loading: false, error: null,
  } })
}

it('renders full precise business keys, related dates, saved rule name, and evidence', () => {
  const wrapper = mounted()
  expect(wrapper.text()).toContain('9223372036854775807')
  expect(wrapper.text()).toContain('1234567890.123400')
  expect(wrapper.text()).toContain('active')
  expect(wrapper.text()).toContain('true')
  expect(wrapper.text()).toContain('null')
  expect(wrapper.text()).toContain('ann_date')
  expect(wrapper.text()).toContain('2026-09-02')
  expect(wrapper.text()).toContain('存档覆盖规则')
  expect(wrapper.text()).toContain('fixture.coverage@7')
  expect(wrapper.text()).toContain('完整键证据')
  expect(wrapper.text()).toContain('问题明细不完整')
})

it('shows unknown primary dates and a truthful fallback for missing saved names', () => {
  const item = issue({ date: null, businessKey: {} })
  item.ruleId = 'missing.rule'; item.ruleVersion = '2'
  const wrapper = mounted(item)
  expect(wrapper.text()).toContain('日期未知 / 整个检查范围')
  expect(wrapper.text()).toContain('未提供业务键')
  expect(wrapper.text()).toContain('missing.rule@2')
  expect(wrapper.text()).toContain('未保存规则名称')
})

it('queries by primary date and warns that unknown-date issues are excluded', async () => {
  const wrapper = mounted()
  await wrapper.get('select[aria-label="问题类型"]').setValue('SUSPECTED_MISSING')
  await wrapper.get('input[aria-label="问题开始日期"]').setValue('2026-09-01')
  await wrapper.get('button[aria-label="查询问题"]').trigger('click')
  const applied = { page: 1, pageSize: 20, resultId: RESULT_ID, type: 'SUSPECTED_MISSING', dateFrom: '2026-09-01' }
  expect(wrapper.emitted('query')[0][0]).toEqual(applied)
  await wrapper.setProps({ criteria: applied })
  expect(wrapper.text()).toContain('日期筛选仅包含可定位日期的问题，已排除日期未知项')
})

it('describes only applied date filters while the draft changes', async () => {
  const wrapper = mounted()
  await wrapper.get('input[aria-label="问题开始日期"]').setValue('2026-09-01')
  expect(wrapper.text()).not.toContain('日期筛选仅包含可定位日期的问题，已排除日期未知项')
  await wrapper.setProps({ criteria: { page: 1, pageSize: 20, resultId: RESULT_ID, dateFrom: '2026-09-01' } })
  expect(wrapper.text()).toContain('日期筛选仅包含可定位日期的问题，已排除日期未知项')
  await wrapper.get('input[aria-label="问题开始日期"]').setValue('')
  expect(wrapper.text()).toContain('日期筛选仅包含可定位日期的问题，已排除日期未知项')
})

it('rejects invalid dates locally and reset preserves only resultId', async () => {
  const wrapper = mounted()
  await wrapper.get('input[aria-label="问题开始日期"]').setValue('2026-09-02')
  await wrapper.get('input[aria-label="问题结束日期"]').setValue('2026-09-01')
  await wrapper.get('button[aria-label="查询问题"]').trigger('click')
  expect(wrapper.emitted('query')).toBeUndefined()
  expect(wrapper.text()).toContain('日期格式无效或起止顺序错误')
  await wrapper.get('button[aria-label="重置问题筛选"]').trigger('click')
  expect(wrapper.emitted('query')[0][0]).toEqual({ page: 1, pageSize: 20, resultId: RESULT_ID })
})

it('exposes retry, close, exact pagination, and the empty state without inferring pass', async () => {
  const wrapper = mounted()
  await wrapper.setProps({ error: Object.assign(new Error('offline'), { requestId: 'issues-request' }) })
  await wrapper.get('button[aria-label="重新查询问题"]').trigger('click')
  await wrapper.get('button[aria-label="关闭问题明细"]').trigger('click')
  expect(wrapper.emitted('retry')).toHaveLength(1)
  expect(wrapper.emitted('close')).toHaveLength(1)
  await wrapper.setProps({ page: { page: 1, pageSize: 20, total: 0n, items: [] }, error: null })
  expect(wrapper.text()).toContain('当前条件下没有问题记录')
  expect(wrapper.text()).not.toContain('因此通过')
})
