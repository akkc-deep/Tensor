import { mount } from '@vue/test-utils'

import WorkbenchPanel from './WorkbenchPanel.vue'

describe('WorkbenchPanel', () => {
  it('associates its heading and renders caller content with optional metadata', () => {
    const wrapper = mount(WorkbenchPanel, {
      props: {
        headingId: 'panel-title',
        title: '工作面板',
        meta: 'daily',
      },
      slots: { default: '<p>面板内容</p>' },
    })

    expect(wrapper.get('section').attributes('aria-labelledby')).toBe(
      'panel-title',
    )
    expect(wrapper.get('h2').attributes('id')).toBe('panel-title')
    expect(wrapper.get('h2').text()).toBe('工作面板')
    expect(wrapper.get('code').text()).toBe('daily')
    expect(wrapper.get('p').text()).toBe('面板内容')
  })

  it('omits empty metadata', () => {
    const wrapper = mount(WorkbenchPanel, {
      props: { headingId: 'empty-meta-title', title: '无元数据' },
    })

    expect(wrapper.find('code').exists()).toBe(false)
  })
})
