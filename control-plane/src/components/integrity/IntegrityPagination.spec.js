import { mount } from '@vue/test-utils'
import IntegrityPagination from './IntegrityPagination.vue'

it('renders exact totals and bounds next with BigInt arithmetic', async () => {
  const wrapper = mount(IntegrityPagination, { props: {
    page: 2, pageSize: 20, total: 9223372036854775807n, disabled: false,
  } })
  expect(wrapper.text()).toContain('共 9223372036854775807 条')
  expect(wrapper.text()).toContain('第 2 页')
  await wrapper.get('button[aria-label="下一页"]').trigger('click')
  expect(wrapper.emitted('update:page')).toEqual([[3]])
  await wrapper.setProps({ page: 1, total: 20n })
  expect(wrapper.get('button[aria-label="下一页"]').attributes('disabled')).toBeDefined()
})

it('returns to page one when page size changes and respects disabled state', async () => {
  const wrapper = mount(IntegrityPagination, { props: {
    page: 3, pageSize: 20, total: 80n, disabled: false,
  } })
  await wrapper.get('select[aria-label="每页条数"]').setValue('50')
  expect(wrapper.emitted('update:pageSize')).toEqual([[50]])
  expect(wrapper.emitted('update:page')).toBeUndefined()
  await wrapper.setProps({ disabled: true })
  expect(wrapper.get('select').attributes('disabled')).toBeDefined()
  expect(wrapper.findAll('button').every((button) => button.attributes('disabled') !== undefined)).toBe(true)
})
