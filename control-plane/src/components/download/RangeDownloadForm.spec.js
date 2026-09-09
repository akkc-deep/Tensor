import { mount } from '@vue/test-utils'
import { ElDatePicker } from 'element-plus'
import { nextTick } from 'vue'

import DynamicParameterForm from './DynamicParameterForm.vue'
import MetadataField from '../common/MetadataField.vue'
import fixture from '../../test/fixtures/range-apis.json'
import { downloadPolicyError } from '../../utils/downloadPolicy.js'

const rangeParameters = [
  { name: 'start_date', label: '旧开始', type: 'DATE_RANGE_MEMBER', required: true, relatedParameter: 'end_date', defaultValue: '20260901' },
  { name: 'end_date', label: '旧结束', type: 'DATE_RANGE_MEMBER', required: true, relatedParameter: 'start_date', defaultValue: '20260902' },
]

function policy(mode = 'TRADE_DATE_RANGE', maxRangeDays = 31) {
  return {
    mode,
    dateSemantic: 'TRADE_DATE',
    description: '服务端说明',
    calendarProfile: 'SSE_SZSE',
    limits: { maxRangeDays },
  }
}

async function setDate(wrapper, name, value) {
  wrapper.get(`[data-parameter="${name}"]`).getComponent(ElDatePicker).vm.$emit('update:modelValue', value)
  await nextTick()
}

describe('range download form', () => {
  it('covers all 49 published descriptors and nine parameter shapes', () => {
    expect(fixture.apis).toHaveLength(49)
    expect(new Set(fixture.apis.map(({ apiName }) => apiName)).size).toBe(49)
    expect(Object.fromEntries(['TRADE_DATE_RANGE', 'ANN_DATE_RANGE', 'MONTH_RANGE', 'NATIVE_RANGE', 'ORIGINAL_PARAMS'].map(mode => [mode, fixture.apis.filter(api => api.downloadPolicy.mode === mode).length]))).toEqual({ TRADE_DATE_RANGE: 19, ANN_DATE_RANGE: 15, MONTH_RANGE: 1, NATIVE_RANGE: 3, ORIGINAL_PARAMS: 11 })
    expect(new Set(fixture.apis.map(api => api.parameters.map(({ name }) => name).join(','))).size).toBe(9)
    expect(fixture.apis.every(api => downloadPolicyError(api) === null)).toBe(true)
  })

  it('mounts, fills, validates and normalizes every published descriptor', async () => {
    for (const api of fixture.apis) {
      const original = structuredClone(api)
      const wrapper = mount(DynamicParameterForm, { props: { parameters: api.parameters, downloadPolicy: api.downloadPolicy } })
      for (const field of wrapper.findAllComponents(MetadataField)) {
        const parameter = api.parameters.find(({ name }) => name === field.attributes('data-parameter'))
        let value = parameter.defaultValue ?? ''
        if (!value) {
          if (parameter.name === 'start_date') value = '2026-09-01'
          else if (parameter.name === 'end_date') value = '2026-09-02'
          else if (parameter.type === 'DATE') value = '2026-09-01'
          else if (parameter.type === 'MONTH') value = '2026-09'
          else if (parameter.type === 'TS_CODE') value = ' 000001.sz '
          else if (parameter.type === 'ENUM') value = parameter.allowedValues[0]
          else value = 'value'
        }
        field.vm.$emit('update:modelValue', value)
      }
      await nextTick()
      expect(await wrapper.vm.validate(), api.apiName).toBe(true)
      expect(Object.keys(wrapper.vm.normalizedValues()), api.apiName).toEqual(api.parameters.map(({ name }) => name))
      expect(api, api.apiName).toEqual(original)
      wrapper.unmount()
    }
  })

  it('uses a changed server limit instead of a built-in range default', async () => {
    const wrapper = mount(DynamicParameterForm, { props: { parameters: rangeParameters, downloadPolicy: policy('TRADE_DATE_RANGE', 2) } })
    await setDate(wrapper, 'start_date', '2026-09-01')
    await setDate(wrapper, 'end_date', '2026-09-02')
    expect(await wrapper.vm.validate()).toBe(true)
    await setDate(wrapper, 'end_date', '2026-09-03')
    expect(await wrapper.vm.validate()).toBe(false)
    expect(wrapper.text()).toContain('单次最多支持 2 个自然日')
  })

  it('clears only stale opt-in range errors when either endpoint is corrected', async () => {
    const wrapper = mount(DynamicParameterForm, { props: { parameters: rangeParameters, downloadPolicy: policy() } })
    await setDate(wrapper, 'start_date', '2026-09-03')
    await setDate(wrapper, 'end_date', '2026-09-01')
    expect(await wrapper.vm.validate()).toBe(false)
    expect(wrapper.text()).toContain('开始日期不得晚于结束日期')
    await setDate(wrapper, 'end_date', '2026-09-04')
    expect(wrapper.text()).not.toContain('开始日期不得晚于结束日期')
    expect(await wrapper.vm.validate()).toBe(true)

    await setDate(wrapper, 'end_date', '')
    expect(await wrapper.vm.validate()).toBe(false)
    expect(wrapper.text()).toContain('此项为必填项')
    await setDate(wrapper, 'start_date', '2026-09-02')
    expect(wrapper.text()).toContain('此项为必填项')

    const generic = mount(DynamicParameterForm, { props: { parameters: rangeParameters } })
    await setDate(generic, 'start_date', '2026-09-03')
    await setDate(generic, 'end_date', '2026-09-01')
    expect(await generic.vm.validate()).toBe(false)
    await setDate(generic, 'end_date', '2026-09-04')
    expect(generic.text()).toContain('开始日期不得晚于结束日期')
  })
  it('starts empty and submits only normalized range endpoints', async () => {
    const wrapper = mount(DynamicParameterForm, { props: { parameters: rangeParameters, downloadPolicy: policy() } })
    expect(wrapper.text()).toContain('开始交易日期')
    expect(wrapper.text()).toContain('结束交易日期')
    expect(wrapper.getComponent(ElDatePicker).props('modelValue')).toBe('')
    await setDate(wrapper, 'start_date', '2026-09-01')
    await setDate(wrapper, 'end_date', '2026-09-02')
    expect(await wrapper.vm.validate()).toBe(true)
    expect(wrapper.vm.normalizedValues()).toEqual({ start_date: '20260901', end_date: '20260902' })
  })

  it('drops range endpoint defaults while preserving and resetting non-date defaults', async () => {
    const parameters = [{ name: 'ts_code', label: '股票', type: 'TS_CODE', required: true, defaultValue: '000001.sz' }, ...rangeParameters]
    const wrapper = mount(DynamicParameterForm, { props: { parameters, downloadPolicy: policy('ANN_DATE_RANGE') } })
    expect(wrapper.get('[data-parameter="ts_code"]').getComponent(MetadataField).props('modelValue')).toBe('000001.sz')
    expect(wrapper.findAllComponents(ElDatePicker).map(item => item.props('modelValue'))).toEqual(['', ''])
    await setDate(wrapper, 'start_date', '2026-09-01')
    wrapper.vm.reset()
    await nextTick()
    expect(wrapper.get('[data-parameter="ts_code"]').getComponent(MetadataField).props('modelValue')).toBe('000001.sz')
    expect(wrapper.findAllComponents(ElDatePicker).map(item => item.props('modelValue'))).toEqual(['', ''])
  })

  it.each([
    ['CALENDAR_DATE', '开始日历日期', '结束日历日期'],
    ['IPO_DATE', '开始申购日期', '结束申购日期'],
    ['ANN_DATE', '开始公告日期', '结束公告日期'],
  ])('uses public native %s endpoint labels', (dateSemantic, startLabel, endLabel) => {
    const downloadPolicy = { ...policy('NATIVE_RANGE'), dateSemantic }
    const wrapper = mount(DynamicParameterForm, { props: { parameters: rangeParameters, downloadPolicy } })
    expect(wrapper.text()).toContain(startLabel)
    expect(wrapper.text()).toContain(endLabel)
  })

  it('summarizes a 31-day three-month range and rejects 32 inclusive days', async () => {
    const wrapper = mount(DynamicParameterForm, { props: { parameters: rangeParameters, downloadPolicy: policy('MONTH_RANGE') } })
    await setDate(wrapper, 'start_date', '2026-01-31')
    await setDate(wrapper, 'end_date', '2026-03-02')
    expect(wrapper.text()).toContain('共 31 个自然日')
    expect(wrapper.text()).toContain('实际覆盖月份：2026-01、2026-02、2026-03，共 3 个月，按完整月份下载')
    expect(await wrapper.vm.validate()).toBe(true)

    await setDate(wrapper, 'end_date', '2026-03-03')
    expect(wrapper.text()).not.toContain('实际覆盖月份')
    expect(await wrapper.vm.validate()).toBe(false)
    expect(wrapper.text()).toContain('单次最多支持 31 个自然日（含起止日期），请缩小区间')
    expect(wrapper.vm.normalizedValues()).toEqual({})
  })
})
