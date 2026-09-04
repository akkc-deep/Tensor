import { flushPromises, mount } from '@vue/test-utils'
import { ElOption, ElOptionGroup, ElSelect } from 'element-plus'
import { nextTick } from 'vue'

import ApiSelect from './ApiSelect.vue'

const CATEGORY_COUNTS = [
  ['basic_organization', 11],
  ['行情与估值', 7],
  ['交易与资金', 6],
  ['互联互通与转融通', 6],
  ['财务与披露', 9],
  ['公司行动', 3],
  ['股东与治理', 7],
]

function descriptor(apiName, displayName, category = '行情与估值') {
  return {
    apiName,
    displayName,
    category,
    queryMode: 'trade_date',
    parameters: [],
  }
}

function currentApis() {
  let index = 0
  return CATEGORY_COUNTS.flatMap(([category, count]) =>
    Array.from({ length: count }, () => {
      index += 1
      if (index === 1) return descriptor('daily', '日线行情', category)
      if (index === 2) return descriptor('weekly', '周线行情', category)
      return descriptor(`api_${index}`, `接口 ${index}`, category)
    }),
  )
}

function filter(wrapper, query) {
  wrapper.getComponent(ElSelect).props('filterMethod')(query)
  return nextTick()
}

describe('ApiSelect', () => {
  it('groups all 49 options by the current seven metadata categories', () => {
    const apis = currentApis()
    const wrapper = mount(ApiSelect, {
      props: { modelValue: '', apis },
    })

    expect(
      wrapper.findAllComponents(ElOptionGroup).map((group) =>
        group.props('label'),
      ),
    ).toEqual(CATEGORY_COUNTS.map(([category]) => category))
    const options = wrapper.findAllComponents(ElOption)
    expect(options).toHaveLength(49)
    expect(options.map((option) => option.props('value'))).toEqual(
      apis.map(({ apiName }) => apiName),
    )
    expect(new Set(options.map((option) => option.props('value'))).size).toBe(
      49,
    )
  })

  it('searches API names case-insensitively', async () => {
    const wrapper = mount(ApiSelect, {
      props: { modelValue: '', apis: currentApis() },
    })

    await filter(wrapper, '  DAI  ')

    expect(
      wrapper.findAllComponents(ElOption).map((option) => option.props('value')),
    ).toEqual(['daily'])
  })

  it('searches display names and exposes the fixed no-match text', async () => {
    const wrapper = mount(ApiSelect, {
      props: { modelValue: '', apis: currentApis() },
    })

    await filter(wrapper, '周线')
    expect(wrapper.findAllComponents(ElOption)).toHaveLength(1)
    expect(wrapper.getComponent(ElOption).props('value')).toBe('weekly')

    await filter(wrapper, '不存在')
    expect(wrapper.findAllComponents(ElOption)).toHaveLength(0)
    expect(wrapper.getComponent(ElSelect).props('noMatchText')).toBe(
      '无匹配接口',
    )
  })

  it('restores original options without changing selection or descriptors', async () => {
    const apis = currentApis()
    const snapshot = structuredClone(apis)
    const wrapper = mount(ApiSelect, {
      props: { modelValue: 'daily', apis },
    })

    await filter(wrapper, 'weekly')
    await filter(wrapper, '')

    expect(wrapper.findAllComponents(ElOption)).toHaveLength(49)
    expect(wrapper.getComponent(ElSelect).props('modelValue')).toBe('daily')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(apis).toEqual(snapshot)
  })

  it('supports keyboard selection and locks interaction when disabled', async () => {
    const wrapper = mount(ApiSelect, {
      attachTo: document.body,
      props: {
        modelValue: '',
        apis: [
          descriptor('daily', '日线行情'),
          descriptor('weekly', '周线行情'),
        ],
      },
    })

    try {
      const combobox = wrapper.get('input[role="combobox"]')
      combobox.element.focus()
      expect(document.activeElement).toBe(combobox.element)

      await combobox.trigger('keydown', { key: 'ArrowDown' })
      await flushPromises()
      expect(wrapper.emitted('update:modelValue')).toBeUndefined()

      await combobox.trigger('keydown', { key: 'Enter' })
      await flushPromises()
      expect(wrapper.emitted('update:modelValue')).toEqual([['daily']])

      await wrapper.setProps({ disabled: true })
      expect(wrapper.getComponent(ElSelect).props('disabled')).toBe(true)
      expect(wrapper.get('input[role="combobox"]').attributes('disabled')).toBe(
        '',
      )
      await combobox.trigger('keydown', { key: 'ArrowDown' })
      await combobox.trigger('keydown', { key: 'Enter' })
      await flushPromises()
      expect(wrapper.emitted('update:modelValue')).toEqual([['daily']])
    } finally {
      wrapper.unmount()
    }
  })
})
