import { flushPromises, mount } from '@vue/test-utils'
import { ElDatePicker, ElInput } from 'element-plus'
import { nextTick } from 'vue'

import DynamicFilterForm from './DynamicFilterForm.vue'

const codeFilter = { field: 'ts_code', operator: 'EQ', controlType: 'TEXT' }
const tradeFilter = { field: 'trade_date', operator: 'BETWEEN', controlType: 'DATE_RANGE' }
const annFilter = { field: 'ann_date', operator: 'BETWEEN', controlType: 'DATE_RANGE' }

function field(wrapper, key) {
  return wrapper.get(`[data-filter="${key}"]`)
}

function control(wrapper, key) {
  const current = field(wrapper, key)
  const component = current.findComponent(ElDatePicker).exists()
    ? current.findComponent(ElDatePicker)
    : current.findComponent(ElInput)
  if (!component.exists()) throw new Error(`Missing control ${key}`)
  return component
}

async function setValue(wrapper, key, value) {
  control(wrapper, key).vm.$emit('update:modelValue', value)
  await nextTick()
}

describe('DynamicFilterForm', () => {
  it('accepts no filters and exposes fresh successful empty criteria', async () => {
    const wrapper = mount(DynamicFilterForm, { props: { filters: [] } })

    expect(wrapper.find('[data-filter]').exists()).toBe(false)
    expect(await wrapper.vm.validate()).toBe(true)
    const first = wrapper.vm.criteria()
    const second = wrapper.vm.criteria()
    expect(first).toEqual({})
    expect(second).toEqual({})
    expect(second).not.toBe(first)
  })

  it('renders only supported metadata controls in descriptor order with labels, ids, and ISO dates', () => {
    const wrapper = mount(DynamicFilterForm, {
      props: { filters: [codeFilter, tradeFilter, annFilter] },
    })

    expect(wrapper.findAll('[data-filter]').map((item) => item.attributes('data-filter'))).toEqual([
      'tsCode', 'tradeDateFrom', 'tradeDateTo', 'annDateFrom', 'annDateTo',
    ])
    expect(wrapper.findAll('.filter-field__label').map((label) => label.text())).toEqual([
      '证券代码 (ts_code)', '交易日期开始 (trade_date)', '交易日期结束 (trade_date)',
      '公告日期开始 (ann_date)', '公告日期结束 (ann_date)',
    ])
    expect(wrapper.findAllComponents(ElDatePicker).map((picker) => ({
      type: picker.props('type'), valueFormat: picker.props('valueFormat'),
    }))).toEqual(Array(4).fill({ type: 'date', valueFormat: 'YYYY-MM-DD' }))
    for (const key of ['tsCode', 'tradeDateFrom', 'tradeDateTo', 'annDateFrom', 'annDateTo']) {
      expect(field(wrapper, key).get('input').attributes('id')).toBe(`dataset-filter-${key}`)
    }
    expect(wrapper.props()).not.toHaveProperty('apiName')
    expect(wrapper.props()).not.toHaveProperty('columns')
  })

  it('builds one normalized AND snapshot from real component updates', async () => {
    const wrapper = mount(DynamicFilterForm, {
      props: { filters: [codeFilter, tradeFilter, annFilter] },
    })

    await setValue(wrapper, 'tsCode', ' 000001.sz ')
    await setValue(wrapper, 'tradeDateFrom', '2026-09-01')
    await setValue(wrapper, 'tradeDateTo', '2026-09-04')
    await setValue(wrapper, 'annDateFrom', '2026-08-01')
    await setValue(wrapper, 'annDateTo', '2026-08-31')
    expect(await wrapper.vm.validate()).toBe(true)
    expect(wrapper.vm.criteria()).toEqual({
      tsCode: '000001.SZ',
      tradeDateFrom: '2026-09-01',
      tradeDateTo: '2026-09-04',
      annDateFrom: '2026-08-01',
      annDateTo: '2026-08-31',
    })
  })

  it('shows safe errors, links ARIA, and focuses the first invalid control', async () => {
    const wrapper = mount(DynamicFilterForm, {
      attachTo: document.body,
      props: { filters: [codeFilter, tradeFilter, annFilter] },
    })

    try {
      await setValue(wrapper, 'tsCode', '<b>broken</b>')
      await setValue(wrapper, 'tradeDateFrom', '2026-02-30')
      await setValue(wrapper, 'annDateFrom', '2026-09-02')
      await setValue(wrapper, 'annDateTo', '2026-09-01')
      expect(await wrapper.vm.validate()).toBe(false)
      await flushPromises()
      expect(field(wrapper, 'tsCode').get('.field-error').text()).toBe('请输入代码.市场格式，例如 000001.SZ')
      expect(field(wrapper, 'tradeDateFrom').get('.field-error').text()).toBe('请选择有效日期')
      expect(field(wrapper, 'annDateFrom').get('.field-error').text()).toBe('开始日期不得晚于结束日期')
      expect(field(wrapper, 'tsCode').get('.field-error').text()).not.toContain('broken')
      expect(field(wrapper, 'tsCode').get('input').attributes()).toMatchObject({
        'aria-invalid': 'true', 'aria-describedby': 'dataset-filter-tsCode-error',
      })
      expect(document.activeElement).toBe(field(wrapper, 'tsCode').get('input').element)
      expect(wrapper.vm.criteria()).toEqual({})
    } finally {
      wrapper.unmount()
    }
  })

  it('resets on demand or metadata replacement and disabled controls ignore updates', async () => {
    const wrapper = mount(DynamicFilterForm, { props: { filters: [codeFilter, tradeFilter] } })
    await setValue(wrapper, 'tsCode', '000001.sz')
    expect(await wrapper.vm.validate()).toBe(true)
    wrapper.vm.reset()
    await nextTick()
    expect(wrapper.vm.criteria()).toEqual({})
    expect(control(wrapper, 'tsCode').props('modelValue')).toBe('')

    await wrapper.setProps({ filters: [annFilter] })
    expect(wrapper.findAll('[data-filter]').map((item) => item.attributes('data-filter'))).toEqual([
      'annDateFrom', 'annDateTo',
    ])
    expect(wrapper.vm.criteria()).toEqual({})
    await wrapper.setProps({ disabled: true })
    expect(control(wrapper, 'annDateFrom').props('disabled')).toBe(true)
    expect(field(wrapper, 'annDateFrom').get('input').attributes('disabled')).toBe('')
    await setValue(wrapper, 'annDateFrom', '2026-09-01')
    expect(control(wrapper, 'annDateFrom').props('modelValue')).toBe('')
  })
})
