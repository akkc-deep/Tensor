import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createDemo } from './demoState.js'
import { planDemoBatches, rangeCapability } from './demoBatch.js'

describe('demo batch downloads', () => {
  let demo
  beforeEach(() => { vi.useFakeTimers(); demo = createDemo() })
  afterEach(() => { demo.dispose(); vi.useRealTimers() })

  it('keeps the current 30 available, four excluded and six single-only interfaces', () => {
    const available = demo.catalog.filter(item => rangeCapability(item.id).availability === 'AVAILABLE')
    expect(available).toHaveLength(30)
    expect(['balancesheet', 'cashflow', 'repurchase', 'fina_indicator'].map(id => rangeCapability(id).availability)).toEqual(Array(4).fill('NEEDS_VERIFICATION'))
    expect(['stock_basic', 'stock_company', 'index_classify', 'index_member_all', 'pledge_stat', 'stk_rewards'].map(id => rangeCapability(id).availability)).toEqual(Array(6).fill('UNSUPPORTED'))
    expect(rangeCapability('top10_holders')).toMatchObject({ dateLabel: '报告期', responseOnly: true })
    expect(rangeCapability('disclosure_date').dateLabel).toBe('最新披露公告日')
  })

  it('splits the inclusive demo range at month boundaries without gaps, including leap days', () => {
    expect(planDemoBatches('2024-01-31', '2024-03-01').map(({ start, end }) => [start, end])).toEqual([
      ['2024-01-31', '2024-01-31'], ['2024-02-01', '2024-02-29'], ['2024-03-01', '2024-03-01'],
    ])
    expect(planDemoBatches('2026-12-31', '2027-01-01').map(({ start, end }) => [start, end])).toEqual([
      ['2026-12-31', '2026-12-31'], ['2027-01-01', '2027-01-01'],
    ])
    expect(planDemoBatches('9999-12-01', '9999-12-31')).toHaveLength(1)
  })

  it.each([['', '2026-08-01'], ['2026-02-30', '2026-03-01'], ['2026-09-01', '2026-08-01'], ['2000-01-01', '2026-01-01']])('rejects invalid or oversized ranges %s–%s', (start, end) => {
    expect(() => planDemoBatches(start, end)).toThrow()
  })

  it('snapshots batch parameters and progresses each batch before completing the task', () => {
    demo.mode = 'RANGE'
    demo.range = { start: '2026-06-15', end: '2026-08-07' }
    demo.submit()
    const task = demo.tasks[0]
    expect(task).toMatchObject({ mode: 'RANGE', status: 'QUEUED', params: { ts_code: '000001.SZ', start_date: '2026-06-15', end_date: '2026-08-07' } })
    expect(task.params).not.toHaveProperty('trade_date')
    expect(task.batches).toHaveLength(3)
    demo.params.ts_code = '600000.SH'
    demo.range.start = '2026-01-01'
    vi.advanceTimersByTime(1800)
    expect(task.status).toBe('RUNNING')
    expect(task.batches.some(batch => batch.status === 'SUCCEEDED')).toBe(true)
    vi.runAllTimers()
    expect(task.status).toBe('SUCCEEDED')
    expect(task.params.ts_code).toBe('000001.SZ')
    expect(task.batches.every(batch => batch.status === 'SUCCEEDED' && batch.attempts === 1)).toBe(true)
    expect(task.rows).toBe(task.batches.reduce((sum, batch) => sum + batch.rows, 0))
  })

  it('retries failed batches and resumes interrupted ones without re-running successful batches', () => {
    for (const status of ['PARTIAL_FAILED', 'INTERRUPTED']) {
      const task = demo.tasks.find(task => task.status === status)
      const succeeded = task.batches.find(batch => batch.status === 'SUCCEEDED')
      const before = { ...succeeded }
      demo.retry(task)
      demo.retry(task)
      vi.runAllTimers()
      expect(task.status).toBe('SUCCEEDED')
      expect(succeeded).toEqual(before)
      expect(task.batches.every(batch => batch.status === 'SUCCEEDED')).toBe(true)
      expect(task.rows).toBe(task.batches.reduce((sum, batch) => sum + batch.rows, 0))
    }
  })

  it('preserves single downloads and resets range mode when selecting an unsupported interface', () => {
    demo.mode = 'RANGE'
    demo.select('stock_basic')
    expect(demo.mode).toBe('SINGLE')
    demo.submit()
    demo.submit()
    expect(demo.tasks).toHaveLength(6)
    expect(demo.tasks[0].batches).toHaveLength(1)
    vi.runAllTimers()
    expect(demo.tasks[0]).toMatchObject({ mode: 'SINGLE', status: 'SUCCEEDED', rows: 24 })
  })

  it('blocks excluded ranges and invalid parameters without creating a task', () => {
    demo.select('balancesheet')
    demo.mode = 'RANGE'
    expect(() => demo.submit()).toThrow()
    demo.select('daily')
    demo.mode = 'RANGE'
    demo.params.ts_code = ''
    expect(() => demo.submit()).toThrow()
    expect(demo.tasks).toHaveLength(5)
    expect(demo.busy).toBe(false)
  })

  it('stops pending batch updates on disposal', () => {
    demo.mode = 'RANGE'
    demo.submit()
    const task = demo.tasks[0]
    demo.dispose()
    vi.runAllTimers()
    expect(task.status).toBe('QUEUED')
  })

  it('keeps BSE calendar ranges unavailable while preserving single calendar requests', () => {
    demo.select('trade_cal')
    demo.params.exchange = 'BSE'
    demo.mode = 'RANGE'
    expect(() => demo.submit()).toThrow('北交所日历暂不支持批量下载')
    expect(demo.tasks).toHaveLength(5)
    demo.mode = 'SINGLE'
    demo.submit()
    expect(demo.tasks[0].params.exchange).toBe('BSE')
  })
})
