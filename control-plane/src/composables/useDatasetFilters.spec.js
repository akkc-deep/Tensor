import { ref } from 'vue'

import { useDatasetFilters } from './useDatasetFilters.js'

const codeFilter = { field: 'ts_code', operator: 'EQ', controlType: 'TEXT' }
const tradeFilter = { field: 'trade_date', operator: 'BETWEEN', controlType: 'DATE_RANGE' }
const annFilter = { field: 'ann_date', operator: 'BETWEEN', controlType: 'DATE_RANGE' }

describe('useDatasetFilters', () => {
  it('validates empty metadata and all optional blanks into fresh empty snapshots', () => {
    const empty = useDatasetFilters(ref([]))
    expect(empty.validateValues()).toBe(true)
    expect(empty.criteria()).toEqual({})

    const optional = useDatasetFilters(ref([codeFilter, tradeFilter]))
    optional.setValue('tsCode', '   ')
    optional.setValue('tradeDateFrom', null)
    expect(optional.validateValues()).toBe(true)
    const first = optional.criteria()
    const second = optional.criteria()
    expect(first).toEqual({})
    expect(second).toEqual({})
    expect(second).not.toBe(first)
  })

  it('maps ts_code only to tsCode, normalizes valid values, and keeps unsafe input out of errors', () => {
    const state = useDatasetFilters(ref([codeFilter]))
    state.setValue('tsCode', ' 000001.sz ')
    expect(state.validateValues()).toBe(true)
    expect(state.criteria()).toEqual({ tsCode: '000001.SZ' })
    expect(state.values).not.toHaveProperty('ts_code')

    state.setValue('tsCode', '<script>bad</script>')
    expect(state.validateValues()).toBe(false)
    expect(state.errors.tsCode).toBe('请输入代码.市场格式，例如 000001.SZ')
    expect(state.errors.tsCode).not.toContain('bad')
    expect(state.criteria()).toEqual({})
  })

  it('keeps valid ISO dates, accepts one-sided ranges, and rejects nonexistent dates', () => {
    const state = useDatasetFilters(ref([tradeFilter]))
    state.setValue('tradeDateFrom', '2026-02-28')
    expect(state.validateValues()).toBe(true)
    expect(state.criteria()).toEqual({ tradeDateFrom: '2026-02-28' })
    state.setValue('tradeDateTo', '2026-02-30')
    expect(state.validateValues()).toBe(false)
    expect(state.errors.tradeDateTo).toBe('请选择有效日期')
    expect(state.criteria()).toEqual({})
  })

  it('creates the five fixed camelCase keys in metadata order without changing descriptors', () => {
    const filters = [codeFilter, tradeFilter, annFilter]
    const original = structuredClone(filters)
    const state = useDatasetFilters(ref(filters))
    for (const [name, value] of [
      ['tsCode', '000001.SZ'], ['tradeDateFrom', '2026-09-01'], ['tradeDateTo', '2026-09-02'],
      ['annDateFrom', '2026-08-01'], ['annDateTo', '2026-08-02'],
    ]) state.setValue(name, value)
    expect(state.validateValues()).toBe(true)
    expect(Object.keys(state.criteria())).toEqual([
      'tsCode', 'tradeDateFrom', 'tradeDateTo', 'annDateFrom', 'annDateTo',
    ])
    expect(filters).toEqual(original)
  })

  it('validates ranges separately, orders first errors by visible fields, and never saves partial criteria', () => {
    const state = useDatasetFilters(ref([codeFilter, tradeFilter, annFilter]))
    state.setValue('tsCode', 'bad')
    state.setValue('tradeDateFrom', '2026-09-03')
    state.setValue('tradeDateTo', '2026-09-02')
    state.setValue('annDateFrom', '2026-08-03')
    state.setValue('annDateTo', '2026-08-02')
    expect(state.validateValues()).toBe(false)
    expect(state.errors).toMatchObject({
      tsCode: '请输入代码.市场格式，例如 000001.SZ',
      tradeDateFrom: '开始日期不得晚于结束日期',
      annDateFrom: '开始日期不得晚于结束日期',
    })
    expect(state.firstError.value).toBe('tsCode')
    expect(state.criteria()).toEqual({})
  })

  it('clears field errors and snapshots on edits, reset, and filter-reference replacement', () => {
    const filters = ref([codeFilter, tradeFilter])
    const state = useDatasetFilters(filters)
    state.setValue('tsCode', '000001.sz')
    expect(state.validateValues()).toBe(true)
    state.setValue('tsCode', 'bad')
    expect(state.criteria()).toEqual({})
    expect(state.validateValues()).toBe(false)
    state.setValue('tsCode', '000001.SZ')
    expect(state.errors.tsCode).toBeUndefined()
    expect(state.firstError.value).toBeNull()
    state.reset()
    expect(state.values).toEqual({ tsCode: '', tradeDateFrom: '', tradeDateTo: '' })
    expect(state.errors).toEqual({})
    expect(state.firstError.value).toBeNull()
    filters.value = [annFilter]
    expect(state.values).toEqual({ annDateFrom: '', annDateTo: '' })
    expect(state.errors).toEqual({})
    expect(state.criteria()).toEqual({})
  })
})
