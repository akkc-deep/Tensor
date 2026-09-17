import { mount } from '@vue/test-utils'
import IntegrityCheckForm from './IntegrityCheckForm.vue'

const sources = [
  { pluginId: 'tushare_pro', displayName: 'Tushare', enabled: true, downloadAvailable: false },
  { pluginId: 'disabled', displayName: '停用源', enabled: false, downloadAvailable: true },
]
const capability = {
  apis: [
    { apiName: 'daily', displayName: '日线', descriptor: { scopeKind: 'STOCK_DATE', dateLabel: '交易日期', limitations: [] } },
    { apiName: 'mystery', displayName: '未知接口', descriptor: null },
  ],
}
const selection = () => ({ pluginId: 'tushare_pro', symbols: ['600000.SH'], startDate: '', endDate: '', apiNames: ['daily'] })

it('uses enabled state only for sources and keeps every capability API visible', () => {
  const wrapper = mount(IntegrityCheckForm, { props: { sources, capability, categories: { daily: '行情' }, modelValue: selection() } })
  const options = wrapper.findAll('select option')
  expect(options.find((item) => item.attributes('value') === 'tushare_pro').attributes('disabled')).toBeUndefined()
  expect(options.find((item) => item.attributes('value') === 'disabled').attributes('disabled')).toBeDefined()
  expect(wrapper.text()).toContain('未分类')
  expect(wrapper.text()).toContain('未知接口')
})

it('adds pasted symbols in first-seen order and removes individual entries', async () => {
  const wrapper = mount(IntegrityCheckForm, { props: { sources, capability, categories: {}, modelValue: selection() } })
  await wrapper.get('[aria-label="股票代码"]').setValue('999999.sz，600000.SH 000001.sz')
  await wrapper.get('button[aria-label="添加"]').trigger('click')
  expect(wrapper.emitted('update:modelValue').at(-1)[0].symbols).toEqual(['600000.SH', '999999.SZ', '000001.SZ'])
  await wrapper.get('button[aria-label="移除 600000.SH"]').trigger('click')
  expect(wrapper.emitted('update:modelValue').at(-1)[0].symbols).toEqual([])
})

it('exposes pending symbol commit and explicit API all/clear controls', async () => {
  const wrapper = mount(IntegrityCheckForm, { props: { sources, capability, categories: {}, modelValue: selection() } })
  await wrapper.get('[aria-label="股票代码"]').setValue('000001.sz')
  const committed = wrapper.vm.commitPendingSymbols()
  expect(committed.symbols).toEqual(['600000.SH', '000001.SZ'])
  await wrapper.get('button[aria-label="全选"]').trigger('click')
  expect(wrapper.emitted('update:modelValue').at(-1)[0].apiNames).toEqual(['daily', 'mystery'])
  await wrapper.get('button[aria-label="清空"]').trigger('click')
  expect(wrapper.emitted('update:modelValue').at(-1)[0].apiNames).toEqual([])
})

it('re-normalizes uncommitted symbols when the selected plugin changes', async () => {
  const initial = { ...selection(), pluginId: 'fixture' }
  const wrapper = mount(IntegrityCheckForm, { props: { sources, capability, categories: {}, modelValue: initial } })
  await wrapper.get('[aria-label="股票代码"]').setValue('600000.sh')
  expect(wrapper.emitted('draft-change').at(-1)[0]).toEqual(['600000.sh'])
  await wrapper.setProps({ modelValue: { ...initial, pluginId: 'tushare_pro' } })
  expect(wrapper.emitted('draft-change').at(-1)[0]).toEqual(['600000.SH'])
})

it('restores a rechecked API to capability order', async () => {
  const initial = { ...selection(), apiNames: ['daily', 'mystery'] }
  const wrapper = mount(IntegrityCheckForm, { props: { sources, capability, categories: {}, modelValue: initial } })
  const daily = () => wrapper.findAll('label.api-option').find((item) => item.text().includes('daily')).get('input')
  await daily().setValue(false)
  const withoutDaily = wrapper.emitted('update:modelValue').at(-1)[0]
  expect(withoutDaily.apiNames).toEqual(['mystery'])
  await wrapper.setProps({ modelValue: withoutDaily })
  await daily().setValue(true)
  expect(wrapper.emitted('update:modelValue').at(-1)[0].apiNames).toEqual(['daily', 'mystery'])
})
