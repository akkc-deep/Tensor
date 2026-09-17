import { parseIntegritySymbols, validateIntegritySelection } from './integrityForm.js'

const stockDate = { apiName: 'daily', descriptor: { scopeKind: 'STOCK_DATE' } }
const nonStock = { apiName: 'calendar', descriptor: { scopeKind: 'NON_STOCK' } }

it('keeps valid Tushare symbols without a local catalogue and de-duplicates normalized input', () => {
  expect(parseIntegritySymbols('999999.sz，999999.SZ 600000.sh', 'tushare_pro')).toEqual([
    '999999.SZ',
    '600000.SH',
  ])
})

it('allows the planned-unit limit exactly and rejects a range above it', () => {
  const selection = {
    pluginId: 'tushare_pro',
    symbols: ['999999.SZ', '600000.SH'],
    startDate: '2026-09-01',
    endDate: '2026-09-01',
    apiNames: ['daily', 'calendar'],
  }
  const capability = {
    apis: [stockDate, nonStock],
    limits: { maxSymbols: 2, maxRangeDays: 1, maxUnits: 3 },
  }

  expect(validateIntegritySelection(selection, capability, '2026-09-17')).toMatchObject({
    valid: true,
    plannedUnits: 3,
    rangeDays: 1,
  })
  expect(validateIntegritySelection(selection, {
    ...capability,
    limits: { ...capability.limits, maxUnits: 2 },
  }, '2026-09-17')).toMatchObject({
    valid: false,
    errors: { units: '计划检查单元数不能超过 2' },
    plannedUnits: 3,
  })
})

it('only normalizes Tushare symbols and preserves first-seen order for other plugins', () => {
  expect(parseIntegritySymbols(' abc,ABC\nabc ', 'fixture')).toEqual(['abc', 'ABC'])
  expect(parseIntegritySymbols(' 600000.sh  bad-code ', 'tushare_pro')).toEqual(['600000.SH', 'BAD-CODE'])
})

it.each([
  [{ symbols: [] }, 'symbols'],
  [{ symbols: ['600000.SH', 'bad'] }, 'symbols'],
  [{ apiNames: [] }, 'apiNames'],
  [{ startDate: '' }, 'startDate'],
  [{ startDate: '0999-12-31' }, 'startDate'],
  [{ startDate: '2026-02-29' }, 'startDate'],
  [{ startDate: '2026-09-02', endDate: '2026-09-01' }, 'range'],
  [{ endDate: '2026-09-18' }, 'endDate'],
  [{ apiNames: ['removed'] }, 'apiNames'],
])('rejects an invalid selection field %#', (change, errorKey) => {
  const selection = {
    pluginId: 'tushare_pro', symbols: ['600000.SH'], startDate: '2026-09-01',
    endDate: '2026-09-01', apiNames: ['daily'], ...change,
  }
  const result = validateIntegritySelection(selection, {
    apis: [stockDate], limits: { maxSymbols: 5, maxRangeDays: 30, maxUnits: 30 },
  }, '2026-09-17')
  expect(result.valid).toBe(false)
  expect(result.errors).toHaveProperty(errorKey)
})

it('counts a closed UTC date range and enforces symbol and day limits', () => {
  const selection = {
    pluginId: 'fixture', symbols: ['A', 'B'], startDate: '2024-02-28',
    endDate: '2024-03-01', apiNames: ['daily'],
  }
  const result = validateIntegritySelection(selection, {
    apis: [stockDate], limits: { maxSymbols: 1, maxRangeDays: 2, maxUnits: 10 },
  }, '2026-09-17')
  expect(result).toMatchObject({ rangeDays: 3, plannedUnits: 2, valid: false })
  expect(result.errors).toMatchObject({
    symbols: '股票数量不能超过 1',
    range: '日期范围不能超过 2 天',
  })
})

it('treats a missing descriptor as stock-scoped and NON_STOCK as one unit', () => {
  const result = validateIntegritySelection({
    pluginId: 'fixture', symbols: ['A', 'B'], startDate: '2026-09-01',
    endDate: '2026-09-01', apiNames: ['unknown-rules', 'calendar'],
  }, {
    apis: [{ apiName: 'unknown-rules', descriptor: null }, nonStock],
    limits: { maxSymbols: 2, maxRangeDays: 1, maxUnits: 3 },
  }, '2026-09-17')
  expect(result).toEqual({ valid: true, errors: {}, plannedUnits: 3, rangeDays: 1 })
})
