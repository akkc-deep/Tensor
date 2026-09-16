import { mount } from '@vue/test-utils'
import { effectScope, nextTick, ref } from 'vue'
import { useParameterForm } from '../../composables/useParameterForm.js'

import DynamicParameterForm from './DynamicParameterForm.vue'

function parameter(overrides = {}) {
  return {
    name: 'trade_date',
    label: '交易日期',
    type: 'DATE',
    required: true,
    ...overrides,
  }
}

function allTypes() {
  return [
    parameter({ description: '<strong>交易日</strong>' }),
    parameter({
      name: 'start_date',
      label: '开始日期',
      type: 'DATE_RANGE_MEMBER',
      relatedParameter: 'end_date',
    }),
    parameter({
      name: 'end_date',
      label: '结束日期',
      type: 'DATE_RANGE_MEMBER',
      relatedParameter: 'start_date',
    }),
    parameter({ name: 'month', label: '月份', type: 'MONTH' }),
    parameter({ name: 'ts_code', label: '证券代码', type: 'TS_CODE' }),
    parameter({
      name: 'exchange',
      label: '交易所',
      type: 'ENUM',
      allowedValues: ['SSE', 'SZSE', 'BSE'],
    }),
    parameter({ name: 'keyword', label: '关键词', type: 'TEXT' }),
  ]
}

function field(wrapper, name) {
  return wrapper.get(`[data-parameter="${name}"]`)
}

async function setValue(wrapper, name, value) {
  await field(wrapper, name).get('input, select').setValue(value)
}

describe('DynamicParameterForm', () => {
  it('uses native controls and groups explicit range endpoints in chronological order', async () => {
    const wrapper = mount(DynamicParameterForm, { props: {
      parameters: [
        parameter({ name: 'finish', label: '结束日期', type: 'DATE_RANGE_MEMBER', relatedParameter: 'begin' }),
        parameter({ name: 'code', label: '代码', type: 'TEXT', required: false }),
        parameter({ name: 'begin', label: '开始日期', type: 'DATE_RANGE_MEMBER', relatedParameter: 'finish' }),
      ],
      range: { startParameter: 'begin', endParameter: 'finish', dateLabel: '公告日期' },
    } })
    expect(wrapper.get('.range-configuration h3').text()).toBe('公告日期范围')
    expect(wrapper.findAll('[data-parameter]').map((item) => item.attributes('data-parameter'))).toEqual(['code', 'begin', 'finish'])
    await wrapper.get('[name="begin"]').setValue('2026-09-01')
    await wrapper.get('[name="finish"]').setValue('2026-09-04')
    expect(await wrapper.vm.validate()).toBe(true)
    expect(wrapper.vm.normalizedValues()).toEqual({ begin: '20260901', finish: '20260904' })
    await wrapper.get('[name="finish"]').setValue('2026-08-31')
    expect(await wrapper.vm.validate()).toBe(false)
    await wrapper.get('[name="finish"]').setValue('2026-09-04')
    expect(wrapper.find('.field-error').exists()).toBe(false)
    expect(wrapper.vm.normalizedValues()).toEqual({})
    expect(await wrapper.vm.validate()).toBe(true)
  })

  it('lets an optional enum return to an omitted empty value', async () => {
    const wrapper = mount(DynamicParameterForm, { props: { parameters: [parameter({
      name: 'exchange', type: 'ENUM', required: false, allowedValues: ['SSE', 'SZSE'],
    })] } })
    const select = wrapper.get('select[name="exchange"]')
    await select.setValue('SSE')
    expect(await wrapper.vm.validate()).toBe(true)
    expect(wrapper.vm.normalizedValues()).toEqual({ exchange: 'SSE' })
    await select.setValue('')
    expect(await wrapper.vm.validate()).toBe(true)
    expect(wrapper.vm.normalizedValues()).toEqual({})
  })

  it('renders metadata controls with associated labels and plain text descriptions', () => {
    const wrapper = mount(DynamicParameterForm, { props: { parameters: allTypes() } })
    expect(wrapper.findAll('[data-parameter]').map((item) => item.attributes('data-parameter'))).toEqual(allTypes().map(({ name }) => name))
    expect(wrapper.findAll('input').map((input) => input.attributes('type'))).toEqual(['date', 'date', 'date', 'month', 'text', 'text'])
    expect(wrapper.findAll('select option').map((option) => option.element.value)).toEqual(['', 'SSE', 'SZSE', 'BSE'])
    for (const parameter of allTypes()) {
      expect(field(wrapper, parameter.name).get('label').attributes('for')).toBe(`download-parameter-${parameter.name}`)
      expect(field(wrapper, parameter.name).get('input, select').attributes('aria-required')).toBe('true')
      expect(field(wrapper, parameter.name).get('.field-label').text()).toContain('必填')
    }
    expect(field(wrapper, 'trade_date').get('.field-help').text()).toBe('<strong>交易日</strong>')
    expect(field(wrapper, 'trade_date').find('strong').exists()).toBe(false)
  })

  it('normalizes inputs into fresh snapshots, omits blanks and uses metadata code patterns', async () => {
    const parameters = [...allTypes(), parameter({ name: 'constructor', type: 'TEXT', required: false })]
    parameters.find(({ name }) => name === 'ts_code').pattern = '^[0-9]{6}\\.(SZ|SH|BJ)$'
    const original = structuredClone(parameters)
    const wrapper = mount(DynamicParameterForm, { props: { parameters } })
    for (const [name, value] of Object.entries({ trade_date: '2026-09-04', start_date: '2026-09-01', end_date: '2026-09-04', month: '2026-09', ts_code: ' 000001.sz ', exchange: 'SZSE', keyword: '  年报 查询  ', constructor: '   ' })) await setValue(wrapper, name, value)
    expect(await wrapper.vm.validate()).toBe(true)
    const snapshot = wrapper.vm.normalizedValues()
    expect(snapshot).toEqual({ trade_date: '20260904', start_date: '20260901', end_date: '20260904', month: '202609', ts_code: '000001.SZ', exchange: 'SZSE', keyword: '年报 查询' })
    expect(wrapper.vm.normalizedValues()).not.toBe(snapshot)
    expect(parameters).toEqual(original)
    await setValue(wrapper, 'ts_code', 'ABC.NY')
    expect(wrapper.vm.normalizedValues()).toEqual({})
    expect(await wrapper.vm.validate()).toBe(false)
    expect(field(wrapper, 'ts_code').get('.field-error').text()).toBe('输入格式不正确')
    await setValue(wrapper, 'ts_code', '000001.SZ')
    expect(field(wrapper, 'ts_code').find('.field-error').exists()).toBe(false)
  })

  it('focuses the first required field and associates errors with its control', async () => {
    const wrapper = mount(DynamicParameterForm, { attachTo: document.body, props: { parameters: allTypes() } })
    try {
      expect(await wrapper.vm.validate()).toBe(false)
      expect(wrapper.findAll('.field-error')).toHaveLength(7)
      expect(document.activeElement).toBe(field(wrapper, 'trade_date').get('input').element)
      expect(field(wrapper, 'trade_date').get('input').attributes()).toMatchObject({
        'aria-invalid': 'true', 'aria-describedby': 'download-parameter-trade_date-description download-parameter-trade_date-error',
      })
      await setValue(wrapper, 'trade_date', '2026-09-04')
      expect(field(wrapper, 'trade_date').get('input').attributes('aria-invalid')).toBeUndefined()
    } finally { wrapper.unmount() }
  })

  it('loads typed defaults, resets on demand and discards old values on metadata replacement', async () => {
    const wrapper = mount(DynamicParameterForm, { props: { parameters: [
      parameter({ defaultValue: '20260904' }),
      parameter({ name: 'month', type: 'MONTH', defaultValue: '202609' }),
    ] } })
    expect(field(wrapper, 'trade_date').get('input').element.value).toBe('2026-09-04')
    expect(field(wrapper, 'month').get('input').element.value).toBe('2026-09')
    await setValue(wrapper, 'trade_date', '2026-09-05')
    expect(await wrapper.vm.validate()).toBe(true)
    wrapper.vm.reset()
    await nextTick()
    expect(field(wrapper, 'trade_date').get('input').element.value).toBe('2026-09-04')
    expect(wrapper.vm.normalizedValues()).toEqual({})
    await wrapper.setProps({ parameters: [parameter({ name: 'new_text', type: 'TEXT', defaultValue: 'fresh' })] })
    expect(wrapper.findAll('[data-parameter]')).toHaveLength(1)
    expect(field(wrapper, 'new_text').get('input').element.value).toBe('fresh')
    expect(wrapper.vm.normalizedValues()).toEqual({})
  })

  it('validates generic SINGLE date pairs and clears stale ordering errors when either date changes', async () => {
    const wrapper = mount(DynamicParameterForm, { props: { parameters: allTypes().slice(1, 3) } })
    await setValue(wrapper, 'start_date', '2026-09-05')
    await setValue(wrapper, 'end_date', '2026-09-04')
    expect(await wrapper.vm.validate()).toBe(false)
    expect(field(wrapper, 'start_date').get('.field-error').text()).toBe('开始日期不得晚于结束日期')
    await setValue(wrapper, 'end_date', '2026-09-05')
    expect(wrapper.find('.field-error').exists()).toBe(false)
    expect(await wrapper.vm.validate()).toBe(true)
  })

  it('locks every native control and ignores input events while disabled', async () => {
    const wrapper = mount(DynamicParameterForm, { props: { parameters: allTypes(), disabled: true } })
    expect(wrapper.findAll('input, select').every((control) => control.element.disabled)).toBe(true)
    await setValue(wrapper, 'ts_code', '000001.SZ')
    await wrapper.setProps({ disabled: false })
    expect(await wrapper.vm.validate()).toBe(false)
    expect(field(wrapper, 'ts_code').get('.field-error').text()).toBe('此项为必填项')
    expect(wrapper.vm.normalizedValues()).toEqual({})
  })

  it('rejects an incomplete optional native date instead of silently dropping the filter', async () => {
    const wrapper = mount(DynamicParameterForm, { props: { parameters: [parameter({ required: false })] } })
    const input = wrapper.get('input')
    const validity = vi.spyOn(input.element, 'validity', 'get').mockReturnValue({ badInput: true })
    // Partial native date segments may not emit an input event.
    expect(await wrapper.vm.validate()).toBe(false)
    expect(wrapper.get('.field-error').text()).toBe('请选择有效日期')
    validity.mockRestore()
    await input.setValue('2026-09-04')
    expect(await wrapper.vm.validate()).toBe(true)
    expect(wrapper.vm.normalizedValues()).toEqual({ trade_date: '20260904' })
    await input.setValue('')
    expect(await wrapper.vm.validate()).toBe(true)
    expect(wrapper.vm.normalizedValues()).toEqual({})
  })

  it('accepts empty metadata and returns fresh empty snapshots', async () => {
    const wrapper = mount(DynamicParameterForm, { props: { parameters: [] } })
    expect(await wrapper.vm.validate()).toBe(true)
    expect(wrapper.vm.normalizedValues()).toEqual({})
    expect(wrapper.vm.normalizedValues()).not.toBe(wrapper.vm.normalizedValues())
  })

  it('rejects malformed dates, months, enums, codes and broken patterns before snapshot creation', () => {
    const scope = effectScope()
    const form = scope.run(() => useParameterForm(ref([
      parameter(), parameter({ name: 'month', type: 'MONTH' }),
      parameter({ name: 'code', type: 'TS_CODE' }),
      parameter({ name: 'exchange', type: 'ENUM', allowedValues: ['SSE'] }),
      parameter({ name: 'text', type: 'TEXT', pattern: '[' }),
    ])))
    try {
      for (const [name, value] of Object.entries({ trade_date: '2026-02-30', month: '2026-13', code: '<b>secret</b>', exchange: 'unknown', text: 'secret' })) form.setValue(name, value)
      expect(form.validateValues()).toBe(false)
      expect(Object.values(form.errors)).toEqual(['请选择有效日期', '请选择有效月份', '请输入代码.市场格式，例如 000001.SZ', '请选择有效选项', '输入格式不正确'])
      expect(form.normalizedValues()).toEqual({})
      expect(JSON.stringify(form.errors)).not.toContain('secret')
    } finally { scope.stop() }
  })
})
