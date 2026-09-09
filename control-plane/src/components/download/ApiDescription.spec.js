import { mount } from '@vue/test-utils'

import ApiDescription from './ApiDescription.vue'

function descriptor(mode = 'TRADE_DATE_RANGE') {
  return {
    apiName: 'daily',
    displayName: '<strong>日线行情</strong>',
    category: '<em>行情与估值</em>',
    queryMode: 'trade_date',
    parameters: [],
    downloadPolicy: {
      mode,
      dateSemantic: mode === 'NATIVE_RANGE' ? 'CALENDAR_DATE' : 'TRADE_DATE',
      description: '<strong>来源说明</strong>',
      calendarProfile: 'C-A',
      limits: mode === 'ORIGINAL_PARAMS' ? null : { maxRangeDays: 31 },
    },
  }
}

describe('ApiDescription', () => {
  it('renders no description without a selected API', () => {
    const wrapper = mount(ApiDescription, { props: { api: null } })

    expect(wrapper.find('section').exists()).toBe(false)
  })

  it('renders descriptor text and every download-mode label safely', async () => {
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

    expect(wrapper.text()).toContain('<strong>来源说明</strong>')
    expect(wrapper.find('.api-description__guidance strong').exists()).toBe(false)

    for (const [mode, label] of [
      ['TRADE_DATE_RANGE', '交易日期区间'],
      ['ANN_DATE_RANGE', '公告日期区间'],
      ['MONTH_RANGE', '覆盖月份'],
      ['NATIVE_RANGE', '原生日期范围'],
      ['ORIGINAL_PARAMS', '保留原条件'],
    ]) {
      await wrapper.setProps({ api: descriptor(mode) })
      expect(wrapper.get('.api-description__query-mode').text()).toBe(label)
    }
  })

  it.each([
    ['CALENDAR_DATE', 'trade_cal', '日历日期范围，包含开盘及休市记录'],
    ['IPO_DATE', 'new_share', '申购日期不等同于上市日期；实际筛选与完整性仍需来源核实'],
    ['ANN_DATE', 'namechange', '不将起止日期解释为名称有效期；实际筛选与完整性仍需来源核实'],
  ])('explains native %s from public policy', (dateSemantic, apiName, copy) => {
    const api = { ...descriptor('NATIVE_RANGE'), apiName }
    api.downloadPolicy = { ...api.downloadPolicy, dateSemantic }
    const wrapper = mount(ApiDescription, { props: { api } })
    expect(wrapper.text()).toContain(copy)
  })

  it('explains weekly/monthly granularity independently from covered-month mode', async () => {
    const wrapper = mount(ApiDescription, { props: { api: { ...descriptor(), apiName: 'weekly' } } })
    expect(wrapper.text()).toContain('结果保持周线粒度')
    expect(wrapper.text()).not.toContain('按完整月份下载')
    await wrapper.setProps({ api: { ...descriptor(), apiName: 'monthly' } })
    expect(wrapper.text()).toContain('结果保持月线粒度')
  })
})
