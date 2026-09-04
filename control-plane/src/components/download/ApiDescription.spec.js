import { mount } from '@vue/test-utils'

import ApiDescription from './ApiDescription.vue'

function descriptor(queryMode = 'trade_date') {
  return {
    apiName: 'daily',
    displayName: '<strong>日线行情</strong>',
    category: '<em>行情与估值</em>',
    queryMode,
    parameters: [],
  }
}

describe('ApiDescription', () => {
  it('renders no description without a selected API', () => {
    const wrapper = mount(ApiDescription, { props: { api: null } })

    expect(wrapper.find('section').exists()).toBe(false)
  })

  it('renders descriptor text and every query-mode label safely', async () => {
    const wrapper = mount(ApiDescription, {
      props: { api: descriptor() },
    })

    expect(wrapper.get('h2').text()).toBe('接口说明')
    expect(wrapper.get('.api-description__display-name').text()).toBe(
      '<strong>日线行情</strong>',
    )
    expect(wrapper.get('.api-description__api-name').text()).toBe('daily')
    expect(wrapper.get('.api-description__category').text()).toBe(
      '<em>行情与估值</em>',
    )
    expect(wrapper.find('.api-description strong').exists()).toBe(false)
    expect(wrapper.find('.api-description em').exists()).toBe(false)

    for (const [queryMode, label] of [
      ['trade_date', '交易日'],
      ['ann_date', '公告日'],
      ['snapshot', '快照'],
      ['date_range', '日期范围'],
      ['future_mode', 'future_mode'],
    ]) {
      await wrapper.setProps({ api: descriptor(queryMode) })
      expect(wrapper.get('.api-description__query-mode').text()).toBe(label)
    }
  })
})
