import { mount } from '@vue/test-utils'
import ApiSelect from './ApiSelect.vue'

const apis = [
  { apiName: 'daily', displayName: '日线行情', category: '行情', queryMode: 'trade_date', parameters: [] },
  { apiName: 'weekly', displayName: '周线行情', category: '行情', queryMode: 'trade_date', parameters: [] },
  { apiName: 'stock_basic', displayName: '股票列表', category: '基础', queryMode: 'snapshot', parameters: [] },
]
const mountCatalog = (props = {}) => mount(ApiSelect, { props: { apis, sourceName: '测试来源', ...props } })
const codes = wrapper => wrapper.findAll('.catalog-list code').map(item => item.text())

it('combines name/code search with category and counts the visible results', async () => {
  const wrapper = mountCatalog()
  expect(codes(wrapper)).toEqual(['daily', 'weekly', 'stock_basic'])
  expect(wrapper.get('footer').text()).toBe('3 个接口 · 测试来源')
  expect(wrapper.findAll('option').map(option => option.text())).toEqual(['全部', '行情', '基础'])
  await wrapper.get('[aria-label="搜索接口"]').setValue('  DAI  ')
  expect(codes(wrapper)).toEqual(['daily'])
  expect(wrapper.get('footer').text()).toBe('1 个接口 · 测试来源')
  await wrapper.get('[aria-label="接口分类"]').setValue('基础')
  expect(codes(wrapper)).toEqual([])
  expect(wrapper.text()).toContain('没有匹配的接口')
  expect(wrapper.get('footer').text()).toBe('0 个接口 · 测试来源')
  await wrapper.get('[aria-label="接口分类"]').setValue('')
  await wrapper.get('[aria-label="搜索接口"]').setValue('周线')
  expect(codes(wrapper)).toEqual(['weekly'])
  await wrapper.get('[aria-label="搜索接口"]').setValue('')
  expect(codes(wrapper)).toEqual(['daily', 'weekly', 'stock_basic'])
  wrapper.unmount()
})

it('preserves selection through filtering and avoids resetting the selected API', async () => {
  const wrapper = mountCatalog({ modelValue: 'daily' })
  await wrapper.get('.catalog-list button[aria-pressed="true"]').trigger('click')
  expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  await wrapper.get('[aria-label="搜索接口"]').setValue('weekly')
  expect(wrapper.find('[aria-pressed="true"]').exists()).toBe(false)
  await wrapper.get('.catalog-list button').trigger('click')
  expect(wrapper.emitted('update:modelValue')).toEqual([['weekly']])
  await wrapper.get('[aria-label="搜索接口"]').setValue('')
  expect(wrapper.get('[aria-pressed="true"] code').text()).toBe('daily')
  wrapper.unmount()
})

it('disables all catalog controls and distinguishes an empty catalog from no matches', async () => {
  const wrapper = mountCatalog({ disabled: true })
  for (const control of wrapper.findAll('input, select, button')) expect(control.element.disabled).toBe(true)
  await wrapper.get('.catalog-list button').trigger('click')
  expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  await wrapper.setProps({ disabled: false, apis: [] })
  expect(wrapper.text()).toContain('此数据源暂无接口')
  expect(wrapper.get('footer').text()).toBe('0 个接口 · 测试来源')
  wrapper.unmount()
})
