import { mount } from '@vue/test-utils'
import IntegrityScopePreview from './IntegrityScopePreview.vue'

const descriptor = (scopeKind) => ({
  scopeKind, dateLabel: '交易日期', dateField: 'trade_date', limitations: ['仅验证本地数据'],
  datasetKey: { pluginId: 'fixture', apiName: 'daily' },
  dependencies: [{ datasetKey: { pluginId: 'fixture', apiName: 'calendar' }, purpose: '交易日依据' }],
  rules: [{ description: '以交易日历为准' }],
})

it('explains date, snapshot, non-stock and missing-rule scopes without inventing a total rate', () => {
  const wrapper = mount(IntegrityScopePreview, { props: {
    selection: { symbols: ['A', 'B'], startDate: '2026-09-01', endDate: '2026-09-03', apiNames: ['date', 'snapshot', 'calendar', 'unknown'] },
    apis: [
      { apiName: 'date', displayName: '日期接口', descriptor: descriptor('STOCK_DATE') },
      { apiName: 'snapshot', displayName: '快照接口', descriptor: descriptor('STOCK_SNAPSHOT') },
      { apiName: 'calendar', displayName: '日历', descriptor: descriptor('NON_STOCK') },
      { apiName: 'unknown', displayName: '未知', descriptor: null },
    ], validation: { plannedUnits: 7, rangeDays: 3, valid: true, errors: {} }, confirmed: false,
  } })
  expect(wrapper.text()).toContain('当前快照不能证明所选历史窗口完整')
  expect(wrapper.text()).toContain('不按股票逐只检查')
  expect(wrapper.text()).toContain('缺少覆盖规则，结论可能无法判定')
  expect(wrapper.text()).toContain('交易日依据')
  expect(wrapper.text()).not.toMatch(/总完整率|100%/)
})

it('emits confirmation and submission with accessible controls', async () => {
  const wrapper = mount(IntegrityScopePreview, { props: {
    selection: { symbols: [], startDate: '', endDate: '', apiNames: [] }, apis: [],
    validation: { plannedUnits: 0, rangeDays: 0, valid: false, errors: {} }, confirmed: false,
  } })
  await wrapper.get('[aria-label="我已确认以上检查口径"]').setValue(true)
  expect(wrapper.emitted('confirm')).toEqual([[true]])
  expect(wrapper.get('button[aria-label="开始检查"]').attributes('disabled')).toBeDefined()
})
