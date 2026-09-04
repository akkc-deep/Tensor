import { formatDate, toApiDate, toApiMonth } from './date.js'
import { formatCell, formatIngestedAt } from './format.js'
import { hasValue, isRangeOrdered, matchesPattern } from './validation.js'

describe('date utilities', () => {
  it('converts strict calendar dates to compact download values', () => {
    expect(toApiDate('2026-09-04')).toBe('20260904')
    expect(toApiDate('2024-02-29')).toBe('20240229')
  })

  it('rejects empty, non-string, loose, and nonexistent dates', () => {
    for (const value of [
      null,
      undefined,
      new Date('2026-09-04T00:00:00Z'),
      '',
      ' 2026-09-04 ',
      '2026-9-04',
      '2026-02-29',
      '2026-13-01',
    ]) {
      expect(toApiDate(value)).toBeNull()
    }
  })

  it('converts only strict valid months', () => {
    expect(toApiMonth('2026-09')).toBe('202609')

    for (const value of [null, undefined, new Date(), '', '2026-9', '2026-00', '2026-13']) {
      expect(toApiMonth(value)).toBeNull()
    }
  })

  it('keeps valid display dates and preserves invalid values', () => {
    expect(formatDate('2026-09-04')).toBe('2026-09-04')
    expect(formatDate('2026-02-29')).toBe('2026-02-29')
    expect(formatDate(20260904)).toBe(20260904)
  })
})

describe('display utilities', () => {
  it('formats ingestion time in Asia/Shanghai to whole seconds by default', () => {
    expect(formatIngestedAt('2026-08-25T02:30:15.123Z')).toBe(
      '2026-08-25 10:30:15',
    )
  })

  it('supports an explicit zone, falls back from a bad zone, and preserves bad time values', () => {
    expect(formatIngestedAt('2026-08-25T02:30:15.123Z', 'UTC')).toBe(
      '2026-08-25 02:30:15',
    )
    expect(
      formatIngestedAt('2026-08-25T02:30:15.123Z', 'Not/A_Zone'),
    ).toBe('2026-08-25 10:30:15')
    expect(formatIngestedAt('not-a-time')).toBe('not-a-time')

    const nonString = { value: '2026-08-25T02:30:15.123Z' }
    expect(formatIngestedAt(nonString)).toBe(nonString)
  })

  it('maps only nullish cells to the placeholder', () => {
    expect(formatCell(null, {})).toBe('--')
    expect(formatCell(undefined, {})).toBe('--')
    expect(formatCell(0, {})).toBe(0)
    expect(formatCell('', {})).toBe('')
  })

  it('preserves precise numeric strings and dispatches date and ingestion columns', () => {
    const decimal = '12345678901234567890.123456789012345678'
    const long = '9223372036854775807'

    expect(formatCell(decimal, { logicalType: 'DECIMAL' })).toBe(decimal)
    expect(formatCell(long, { logicalType: 'LONG' })).toBe(long)
    expect(formatCell('2026-09-04', { logicalType: 'DATE' })).toBe(
      '2026-09-04',
    )
    expect(
      formatCell(
        '2026-08-25T02:30:15.123Z',
        { name: 'ingested_at', logicalType: 'DATE' },
        'UTC',
      ),
    ).toBe('2026-08-25 02:30:15')
  })
})

describe('validation utilities', () => {
  it('handles required values, metadata patterns, and ordered optional ranges', () => {
    expect(hasValue(null)).toBe(false)
    expect(hasValue(undefined)).toBe(false)
    expect(hasValue('')).toBe(false)
    expect(hasValue('   ')).toBe(false)
    expect(hasValue(0)).toBe(true)
    expect(hasValue(false)).toBe(true)

    expect(matchesPattern('000001.SZ', '^[0-9]{6}\\.(SZ|SH)$')).toBe(true)
    expect(matchesPattern('000001', '^[0-9]{6}\\.(SZ|SH)$')).toBe(false)
    expect(matchesPattern('anything', '[')).toBe(false)
    expect(matchesPattern('anything', '')).toBe(false)
    expect(matchesPattern(1, '^[0-9]+$')).toBe(false)

    expect(isRangeOrdered('2026-09-01', '2026-09-04')).toBe(true)
    expect(isRangeOrdered('2026-09-04', '2026-09-01')).toBe(false)
    expect(isRangeOrdered('', '2026-09-04')).toBe(true)
    expect(isRangeOrdered('2026-09-01', '')).toBe(true)
    expect(isRangeOrdered(1, 2)).toBe(false)
  })
})
