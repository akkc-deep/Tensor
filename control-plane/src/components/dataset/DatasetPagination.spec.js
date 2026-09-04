import { flushPromises, mount } from '@vue/test-utils'
import { ElPagination, ElSelect } from 'element-plus'
import { nextTick } from 'vue'

import DatasetPagination from './DatasetPagination.vue'

function pagination(wrapper) {
  return wrapper.getComponent(ElPagination)
}

function pageSizeInput(wrapper) {
  return wrapper.get('input[role="combobox"]')
}

describe('DatasetPagination', () => {
  it('uses the approved defaults and fixed server pagination controls', () => {
    const wrapper = mount(DatasetPagination)
    const control = pagination(wrapper)

    expect(wrapper.get('nav').attributes('aria-label')).toBe('数据集分页')
    expect(control.props()).toMatchObject({
      currentPage: 1,
      pageSize: 50,
      pageCount: 0,
      pageSizes: [20, 50, 100],
      layout: 'sizes, prev, pager, next',
      prevText: '上一页',
      nextText: '下一页',
      hideOnSinglePage: false,
    })
    expect(control.props('pageSizes')).toEqual([20, 50, 100])
    expect(wrapper.text()).toContain('上一页')
    expect(wrapper.text()).toContain('下一页')
  })

  it('announces server totals without deriving page count from the record total', async () => {
    const wrapper = mount(DatasetPagination, {
      props: { page: 2, pageSize: 50, totalElements: 123, totalPages: 7 },
    })
    const summary = wrapper.get('[role="status"]')

    expect(summary.text()).toBe('共 123 条，第 2 / 7 页')
    expect(summary.attributes()).toMatchObject({ 'aria-live': 'polite', 'aria-atomic': 'true' })
    expect(pagination(wrapper).props('pageCount')).toBe(7)

    await wrapper.setProps({ page: 3, totalElements: 99, totalPages: 9 })

    expect(summary.text()).toBe('共 99 条，第 3 / 9 页')
    expect(pagination(wrapper).props('pageCount')).toBe(9)
  })

  it('emits a next page from the real labelled button while remaining controlled', async () => {
    const wrapper = mount(DatasetPagination, {
      attachTo: document.body,
      props: { page: 2, pageSize: 50, totalElements: 123, totalPages: 7 },
    })

    try {
      const next = wrapper.get('.btn-next')
      expect(next.attributes('type')).toBe('button')
      expect(next.text()).toContain('下一页')
      expect(next.attributes('aria-label')).toBeTruthy()
      next.element.focus()
      expect(document.activeElement).toBe(next.element)
      await next.trigger('click')

      expect(wrapper.emitted('update:page')).toEqual([[3]])
      expect(wrapper.emitted('update:pageSize')).toBeUndefined()
      expect(pagination(wrapper).props('currentPage')).toBe(2)
    } finally {
      wrapper.unmount()
    }
  })

  it('offers only approved page sizes and emits a size change without resetting the page', async () => {
    const wrapper = mount(DatasetPagination, {
      attachTo: document.body,
      props: { page: 2, pageSize: 50, totalElements: 123, totalPages: 7 },
    })

    try {
      const input = pageSizeInput(wrapper)
      expect(wrapper.getComponent(ElSelect).props('modelValue')).toBe(50)
      expect(pagination(wrapper).props('pageSizes')).toEqual([20, 50, 100])
      await input.trigger('click')
      await flushPromises()
      const options = [...document.body.querySelectorAll('.el-select-dropdown__item')]
      expect(options.map((option) => option.textContent.trim())).toEqual(['20/page', '50/page', '100/page'])
      await options[2].click()
      await flushPromises()

      expect(wrapper.emitted('update:pageSize')).toEqual([[100]])
      expect(wrapper.emitted('update:page')).toBeUndefined()
      expect(pagination(wrapper).props('pageSize')).toBe(50)
    } finally {
      wrapper.unmount()
    }
  })

  it('disables the whole control and guards forwarded events until re-enabled', async () => {
    const wrapper = mount(DatasetPagination, {
      props: { page: 2, pageSize: 50, totalElements: 123, totalPages: 7, disabled: true },
    })
    const control = pagination(wrapper)

    expect(wrapper.get('nav').attributes('aria-disabled')).toBe('true')
    expect(control.props('disabled')).toBe(true)
    expect(wrapper.get('.btn-next').attributes('disabled')).toBeDefined()
    expect(pageSizeInput(wrapper).attributes('disabled')).toBeDefined()
    control.vm.$emit('update:current-page', 3)
    control.vm.$emit('update:page-size', 100)
    await nextTick()
    expect(wrapper.emitted()).toEqual({})

    await wrapper.setProps({ disabled: false })

    expect(wrapper.get('nav').attributes('aria-disabled')).toBe('false')
    expect(control.props('disabled')).toBe(false)
    expect(wrapper.get('.btn-next').attributes('disabled')).toBeUndefined()
    expect(pageSizeInput(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('keeps page sizes available for a zero-page server result', async () => {
    const wrapper = mount(DatasetPagination, {
      attachTo: document.body,
      props: { page: 1, pageSize: 50, totalElements: 0, totalPages: 0, disabled: false },
    })

    try {
      expect(wrapper.get('[role="status"]').text()).toBe('共 0 条，第 1 / 0 页')
      expect(wrapper.get('nav').attributes('aria-disabled')).toBe('false')
      expect(pagination(wrapper).props('disabled')).toBe(false)
      expect(wrapper.get('.btn-prev').attributes('disabled')).toBeDefined()
      expect(wrapper.get('.btn-next').attributes('disabled')).toBeDefined()
      const input = pageSizeInput(wrapper)
      input.element.focus()
      expect(document.activeElement).toBe(input.element)
      pagination(wrapper).vm.$emit('update:page-size', 20)
      await nextTick()
      expect(wrapper.emitted('update:pageSize')).toEqual([[20]])
    } finally {
      wrapper.unmount()
    }
  })
})
