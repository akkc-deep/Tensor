import { flushPromises, mount } from '@vue/test-utils'
import { ElDatePicker, ElInput, ElOption, ElSelect } from 'element-plus'
import { nextTick } from 'vue'

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

function inputComponent(wrapper, name) {
  const current = field(wrapper, name)
  for (const component of [ElDatePicker, ElInput, ElSelect]) {
    const match = current.findComponent(component)
    if (match.exists()) return match
  }
  throw new Error(`No input component for ${name}`)
}

async function setValue(wrapper, name, value) {
  inputComponent(wrapper, name).vm.$emit('update:modelValue', value)
  await nextTick()
}

describe('DynamicParameterForm', () => {
  it('renders all metadata types in order with visible accessible labels', () => {
    const wrapper = mount(DynamicParameterForm, {
      props: { parameters: allTypes() },
    })

    expect(
      wrapper.findAll('[data-parameter]').map((item) =>
        item.attributes('data-parameter'),
      ),
    ).toEqual(allTypes().map(({ name }) => name))
    expect(
      wrapper.findAllComponents(ElDatePicker).map((picker) =>
        picker.props('type'),
      ),
    ).toEqual(['date', 'date', 'date', 'month'])
    expect(field(wrapper, 'ts_code').getComponent(ElInput).exists()).toBe(true)
    expect(field(wrapper, 'keyword').getComponent(ElInput).exists()).toBe(true)
    expect(wrapper.findAllComponents(ElSelect)).toHaveLength(1)
    expect(
      wrapper.findAllComponents(ElOption).map((option) =>
        option.props('value'),
      ),
    ).toEqual(['SSE', 'SZSE', 'BSE'])
    expect(
      wrapper.findAll('.parameter-field__label').map((label) => label.text()),
    ).toEqual(allTypes().map(({ label }) => `${label}*`))

    const dateControl = field(wrapper, 'trade_date').get('input')
    expect(dateControl.attributes()).toMatchObject({
      id: 'download-parameter-trade_date',
      'aria-required': 'true',
      'aria-describedby': 'download-parameter-trade_date-description',
    })
    expect(field(wrapper, 'trade_date').get('.parameter-field__description').text()).toBe(
      '<strong>交易日</strong>',
    )
    expect(field(wrapper, 'trade_date').find('strong').exists()).toBe(false)
    expect(wrapper.props()).not.toHaveProperty('apiName')
  })

  it('loads typed defaults and resets on demand or parameter replacement', async () => {
    const parameters = [
      parameter({ defaultValue: '20260904' }),
      parameter({ name: 'month', label: '月份', type: 'MONTH', defaultValue: '202609' }),
      parameter({ name: 'ts_code', label: '证券代码', type: 'TS_CODE', defaultValue: '000001.sz' }),
      parameter({ name: 'exchange', label: '交易所', type: 'ENUM', allowedValues: ['SSE'], defaultValue: 'SSE' }),
      parameter({ name: 'keyword', label: '关键词', type: 'TEXT', defaultValue: ' initial ' }),
    ]
    const wrapper = mount(DynamicParameterForm, { props: { parameters } })

    expect(inputComponent(wrapper, 'trade_date').props('modelValue')).toBe('2026-09-04')
    expect(inputComponent(wrapper, 'month').props('modelValue')).toBe('2026-09')
    expect(inputComponent(wrapper, 'ts_code').props('modelValue')).toBe('000001.sz')
    expect(inputComponent(wrapper, 'exchange').props('modelValue')).toBe('SSE')
    expect(inputComponent(wrapper, 'keyword').props('modelValue')).toBe(' initial ')

    await setValue(wrapper, 'trade_date', '2026-09-05')
    expect(await wrapper.vm.validate()).toBe(true)
    expect(wrapper.vm.normalizedValues()).not.toEqual({})
    wrapper.vm.reset()
    await nextTick()
    expect(inputComponent(wrapper, 'trade_date').props('modelValue')).toBe('2026-09-04')
    expect(wrapper.vm.normalizedValues()).toEqual({})

    await wrapper.setProps({
      parameters: [
        parameter({ name: 'query_text', label: '查询文本', type: 'TEXT', required: false, defaultValue: 'new' }),
      ],
    })
    expect(wrapper.findAll('[data-parameter]')).toHaveLength(1)
    expect(inputComponent(wrapper, 'query_text').props('modelValue')).toBe('new')
    expect(wrapper.vm.normalizedValues()).toEqual({})
  })

  it('normalizes valid values into fresh descriptor-ordered snapshots', async () => {
    const parameters = allTypes()
    const original = structuredClone(parameters)
    const wrapper = mount(DynamicParameterForm, { props: { parameters } })

    for (const [name, value] of [
      ['trade_date', '2026-09-04'],
      ['start_date', '2026-09-01'],
      ['end_date', '2026-09-04'],
      ['month', '2026-09'],
      ['ts_code', ' 000001.sz '],
      ['exchange', 'SZSE'],
      ['keyword', '  年报 查询  '],
    ]) {
      await setValue(wrapper, name, value)
    }

    expect(await wrapper.vm.validate()).toBe(true)
    const first = wrapper.vm.normalizedValues()
    const second = wrapper.vm.normalizedValues()
    expect(first).toEqual({
      trade_date: '20260904',
      start_date: '20260901',
      end_date: '20260904',
      month: '202609',
      ts_code: '000001.SZ',
      exchange: 'SZSE',
      keyword: '年报 查询',
    })
    expect(Object.keys(first)).toEqual(parameters.map(({ name }) => name))
    expect(second).toEqual(first)
    expect(second).not.toBe(first)
    expect(parameters).toEqual(original)
  })

  it('accepts an empty parameter list and returns fresh empty snapshots', async () => {
    const wrapper = mount(DynamicParameterForm, { props: { parameters: [] } })

    expect(wrapper.find('[data-parameter]').exists()).toBe(false)
    expect(await wrapper.vm.validate()).toBe(true)
    const first = wrapper.vm.normalizedValues()
    const second = wrapper.vm.normalizedValues()
    expect(first).toEqual({})
    expect(second).toEqual({})
    expect(second).not.toBe(first)
    wrapper.vm.reset()
    expect(wrapper.vm.normalizedValues()).toEqual({})
  })

  it('shows required errors as text and focuses only the first invalid control', async () => {
    const parameters = [
      parameter({ name: 'ts_code', label: '证券代码', type: 'TS_CODE', description: '代码说明' }),
      parameter({ name: 'keyword', label: '关键词', type: 'TEXT' }),
    ]
    const wrapper = mount(DynamicParameterForm, {
      attachTo: document.body,
      props: { parameters },
    })

    try {
      expect(await wrapper.vm.validate()).toBe(false)
      await flushPromises()
      expect(wrapper.findAllComponents({ name: 'FieldError' }).map((error) => error.text())).toEqual([
        '此项为必填项',
        '此项为必填项',
      ])
      expect(field(wrapper, 'ts_code').get('input').attributes()).toMatchObject({
        'aria-invalid': 'true',
        'aria-describedby':
          'download-parameter-ts_code-description download-parameter-ts_code-error',
      })
      expect(field(wrapper, 'keyword').get('input').attributes('aria-describedby')).toBe(
        'download-parameter-keyword-error',
      )
      expect(document.activeElement).toBe(
        field(wrapper, 'ts_code').get('input').element,
      )
    } finally {
      wrapper.unmount()
    }
  })

  it('rejects invalid types and safe pattern failures without exposing inputs', async () => {
    const parameters = [
      parameter(),
      parameter({ name: 'month', label: '月份', type: 'MONTH' }),
      parameter({ name: 'ts_code', label: '证券代码', type: 'TS_CODE' }),
      parameter({ name: 'exchange', label: '交易所', type: 'ENUM', allowedValues: ['SSE'] }),
      parameter({ name: 'matched_text', label: '匹配文本', type: 'TEXT', pattern: '^safe$' }),
      parameter({ name: 'broken_text', label: '损坏规则文本', type: 'TEXT', pattern: '[' }),
    ]
    const wrapper = mount(DynamicParameterForm, { props: { parameters } })

    for (const [name, value] of [
      ['trade_date', '2026-02-30'],
      ['month', '2026-13'],
      ['ts_code', '<b>000001</b>'],
      ['exchange', '<i>UNKNOWN</i>'],
      ['matched_text', '<script>unsafe</script>'],
      ['broken_text', 'secret'],
    ]) {
      await setValue(wrapper, name, value)
    }

    expect(await wrapper.vm.validate()).toBe(false)
    expect(wrapper.vm.normalizedValues()).toEqual({})
    expect(wrapper.findAllComponents({ name: 'FieldError' }).map((error) => error.text())).toEqual([
      '请选择有效日期',
      '请选择有效月份',
      '请输入代码.市场格式，例如 000001.SZ',
      '请选择有效选项',
      '输入格式不正确',
      '输入格式不正确',
    ])
    const errors = wrapper.findAll('.field-error').map((error) => error.text()).join(' ')
    expect(errors).not.toContain('unsafe')
    expect(errors).not.toContain('UNKNOWN')
    expect(errors).not.toContain('^safe$')
    expect(errors).not.toContain('secret')
  })

  it('omits optional blanks and invalidates old errors and successful snapshots on change', async () => {
    const wrapper = mount(DynamicParameterForm, {
      props: {
        parameters: [
          parameter({ name: 'optional_text', label: '可选文本', type: 'TEXT', required: false }),
          parameter({ name: 'required_text', label: '必填文本', type: 'TEXT' }),
        ],
      },
    })

    await setValue(wrapper, 'optional_text', '   ')
    await setValue(wrapper, 'required_text', '  valid value  ')
    expect(await wrapper.vm.validate()).toBe(true)
    expect(wrapper.vm.normalizedValues()).toEqual({ required_text: 'valid value' })

    await setValue(wrapper, 'required_text', '   ')
    expect(wrapper.vm.normalizedValues()).toEqual({})
    expect(await wrapper.vm.validate()).toBe(false)
    expect(field(wrapper, 'required_text').get('.field-error').text()).toBe('此项为必填项')

    await setValue(wrapper, 'required_text', 'fixed')
    expect(field(wrapper, 'required_text').find('.field-error').exists()).toBe(false)
    expect(wrapper.vm.normalizedValues()).toEqual({})
  })

  it('validates mutual date ranges generically and marks only the first member', async () => {
    for (const [startName, endName] of [
      ['start_date', 'end_date'],
      ['begin_date', 'finish_date'],
      ['lower_date', 'upper_date'],
    ]) {
      const wrapper = mount(DynamicParameterForm, {
        attachTo: document.body,
        props: {
          parameters: [
            parameter({ name: startName, label: '下界', type: 'DATE_RANGE_MEMBER', relatedParameter: endName }),
            parameter({ name: endName, label: '上界', type: 'DATE_RANGE_MEMBER', relatedParameter: startName }),
          ],
        },
      })

      try {
        await setValue(wrapper, startName, '2026-09-04')
        await setValue(wrapper, endName, '2026-09-04')
        expect(await wrapper.vm.validate()).toBe(true)

        await setValue(wrapper, startName, '2026-09-01')
        expect(await wrapper.vm.validate()).toBe(true)

        await setValue(wrapper, startName, '2026-09-05')
        expect(await wrapper.vm.validate()).toBe(false)
        await flushPromises()
        expect(field(wrapper, startName).get('.field-error').text()).toBe(
          '开始日期不得晚于结束日期',
        )
        expect(field(wrapper, endName).find('.field-error').exists()).toBe(false)
        expect(document.activeElement).toBe(field(wrapper, startName).get('input').element)
      } finally {
        wrapper.unmount()
      }
    }
  })

  it('locks every control while disabled without changing values or creating snapshots', async () => {
    const parameters = allTypes().map((item) => ({
      ...item,
      defaultValue:
        item.type === 'DATE' || item.type === 'DATE_RANGE_MEMBER'
          ? '20260904'
          : item.type === 'MONTH'
            ? '202609'
            : item.type === 'ENUM'
              ? 'SSE'
              : 'initial',
    }))
    const wrapper = mount(DynamicParameterForm, {
      props: { parameters, disabled: true },
    })

    expect(wrapper.findAllComponents(ElDatePicker).every((item) => item.props('disabled'))).toBe(true)
    expect(wrapper.findAllComponents(ElInput).every((item) => item.props('disabled'))).toBe(true)
    expect(wrapper.findAllComponents(ElSelect).every((item) => item.props('disabled'))).toBe(true)
    expect(wrapper.findAll('input').every((item) => item.attributes('disabled') === '')).toBe(true)

    inputComponent(wrapper, 'trade_date').vm.$emit('update:modelValue', '2026-09-05')
    await nextTick()
    expect(inputComponent(wrapper, 'trade_date').props('modelValue')).toBe('2026-09-04')
    expect(wrapper.vm.normalizedValues()).toEqual({})
    wrapper.vm.reset()
    await nextTick()
    expect(inputComponent(wrapper, 'trade_date').props('modelValue')).toBe('2026-09-04')
    expect(wrapper.vm.normalizedValues()).toEqual({})
  })
})
