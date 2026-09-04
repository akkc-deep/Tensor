import { flushPromises, mount } from '@vue/test-utils'
import { ElOption, ElOptionGroup, ElSelect } from 'element-plus'
import { nextTick } from 'vue'

import DatasetSelect from './DatasetSelect.vue'

const CATEGORY_COUNTS = [
  ['basic_organization', 11],
  ['行情与估值', 7],
  ['交易与资金', 6],
  ['互联互通与转融通', 6],
  ['财务与披露', 9],
  ['公司行动', 3],
  ['股东与治理', 7],
]

function dataset(apiName, displayName, category = '行情与估值') {
  return { apiName, displayName, category, filters: [] }
}

function currentDatasets() {
  let index = 0
  return CATEGORY_COUNTS.flatMap(([category, count]) =>
    Array.from({ length: count }, () => {
      index += 1
      if (index === 1) return dataset('daily', '日线行情', category)
      if (index === 2) return dataset('weekly', '周线行情', category)
      return dataset(`dataset_${index}`, `数据集 ${index}`, category)
    }),
  )
}

function filter(wrapper, query) {
  wrapper.getComponent(ElSelect).props('filterMethod')(query)
  return nextTick()
}

describe('DatasetSelect', () => {
  it('groups all 49 current-source datasets in metadata order with apiName values', () => {
    const datasets = currentDatasets()
    const wrapper = mount(DatasetSelect, { props: { modelValue: '', datasets } })

    expect(wrapper.findAllComponents(ElOptionGroup).map((group) => group.props('label'))).toEqual(
      CATEGORY_COUNTS.map(([category]) => category),
    )
    const options = wrapper.findAllComponents(ElOption)
    expect(options).toHaveLength(49)
    expect(options.map((option) => option.props('value'))).toEqual(
      datasets.map(({ apiName }) => apiName),
    )
    expect(new Set(options.map((option) => option.props('value'))).size).toBe(49)
  })

  it('searches api and display names while preserving selection, source order, and descriptors', async () => {
    const datasets = currentDatasets()
    const original = structuredClone(datasets)
    const wrapper = mount(DatasetSelect, {
      props: { modelValue: 'daily', datasets },
    })

    await filter(wrapper, '  DAI  ')
    expect(wrapper.findAllComponents(ElOption).map((option) => option.props('value'))).toEqual(['daily'])
    await filter(wrapper, '周线')
    expect(wrapper.getComponent(ElOption).props('value')).toBe('weekly')
    await filter(wrapper, '不存在')
    expect(wrapper.findAllComponents(ElOption)).toHaveLength(0)
    expect(wrapper.getComponent(ElSelect).props('noMatchText')).toBe('无匹配数据集')
    await filter(wrapper, '')

    expect(wrapper.findAllComponents(ElOption).map((option) => option.props('value'))).toEqual(
      datasets.map(({ apiName }) => apiName),
    )
    expect(wrapper.getComponent(ElSelect).props('modelValue')).toBe('daily')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(datasets).toEqual(original)
  })

  it('shows distinct visible empty states for unavailable datasets and unmatched searches', async () => {
    const empty = mount(DatasetSelect, {
      attachTo: document.body,
      props: { modelValue: '', datasets: [] },
    })

    try {
      await empty.get('input[role="combobox"]').trigger('click')
      await flushPromises()
      expect(document.body.textContent).toContain('暂无数据集')
    } finally {
      empty.unmount()
    }

    const wrapper = mount(DatasetSelect, {
      attachTo: document.body,
      props: { modelValue: '', datasets: [dataset('daily', '日线行情')] },
    })

    try {
      await wrapper.get('input[role="combobox"]').trigger('click')
      await filter(wrapper, '不存在')
      await flushPromises()
      expect(document.body.textContent).toContain('无匹配数据集')
    } finally {
      wrapper.unmount()
    }
  })

  it('is controlled and emits exactly one apiName without writing its prop', async () => {
    const wrapper = mount(DatasetSelect, {
      props: { modelValue: 'daily', datasets: [dataset('daily', '日线行情'), dataset('weekly', '周线行情')] },
    })

    wrapper.getComponent(ElSelect).vm.$emit('update:modelValue', 'weekly')
    await nextTick()

    expect(wrapper.emitted('update:modelValue')).toEqual([['weekly']])
    expect(wrapper.getComponent(ElSelect).props('modelValue')).toBe('daily')
  })

  it('supports keyboard first-option selection and prevents new events while disabled', async () => {
    const wrapper = mount(DatasetSelect, {
      attachTo: document.body,
      props: { modelValue: '', datasets: [dataset('daily', '日线行情'), dataset('weekly', '周线行情')] },
    })

    try {
      const combobox = wrapper.get('input[role="combobox"]')
      combobox.element.focus()
      expect(document.activeElement).toBe(combobox.element)
      await combobox.trigger('keydown', { key: 'ArrowDown' })
      await flushPromises()
      await combobox.trigger('keydown', { key: 'Enter' })
      await flushPromises()
      expect(wrapper.emitted('update:modelValue')).toEqual([['daily']])

      await wrapper.setProps({ disabled: true })
      expect(wrapper.getComponent(ElSelect).props('disabled')).toBe(true)
      expect(wrapper.get('input[role="combobox"]').attributes('disabled')).toBe('')
      await combobox.trigger('keydown', { key: 'ArrowDown' })
      await combobox.trigger('keydown', { key: 'Enter' })
      await flushPromises()
      expect(wrapper.emitted('update:modelValue')).toEqual([['daily']])
    } finally {
      wrapper.unmount()
    }
  })
})
