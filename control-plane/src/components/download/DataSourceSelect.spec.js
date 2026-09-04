import { mount } from '@vue/test-utils'
import { ElOption, ElSelect } from 'element-plus'

import DataSourceSelect from './DataSourceSelect.vue'

function source(overrides = {}) {
  return {
    pluginId: 'tushare_pro',
    displayName: 'Tushare Pro',
    description: 'Tushare Pro 证券数据源',
    enabled: true,
    credentialConfigured: true,
    downloadAvailable: true,
    unavailableReason: null,
    ...overrides,
  }
}

describe('DataSourceSelect', () => {
  it('shows a visible label and defaults an empty single source', () => {
    const wrapper = mount(DataSourceSelect, {
      props: { modelValue: '', sources: [source()] },
    })

    expect(wrapper.get('label[for="download-data-source"]').text()).toBe(
      '数据源',
    )
    expect(wrapper.getComponent(ElOption).props()).toMatchObject({
      label: 'Tushare Pro',
      value: 'tushare_pro',
      disabled: false,
    })
    expect(wrapper.emitted('update:modelValue')).toEqual([['tushare_pro']])
  })

  it('does not default multiple sources or overwrite an existing value', async () => {
    const sources = [
      source({ pluginId: 'fixture', displayName: 'Fixture' }),
      source(),
    ]
    const wrapper = mount(DataSourceSelect, {
      props: { modelValue: '', sources },
    })

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()

    await wrapper.setProps({ modelValue: 'fixture' })
    expect(wrapper.getComponent(ElSelect).props('modelValue')).toBe('fixture')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('disables unavailable interaction and renders its reason as text', () => {
    const unavailable = source({
      credentialConfigured: false,
      downloadAvailable: false,
      unavailableReason: '<strong>Credentials missing</strong>',
    })
    const wrapper = mount(DataSourceSelect, {
      props: { modelValue: '', sources: [unavailable], disabled: true },
    })

    expect(wrapper.getComponent(ElSelect).props('disabled')).toBe(true)
    expect(wrapper.getComponent(ElOption).props('disabled')).toBe(true)
    const reason = wrapper.get('[role="status"]')
    expect(reason.text()).toBe('<strong>Credentials missing</strong>')
    expect(reason.find('strong').exists()).toBe(false)
  })
})
