import {
  formatIntegrityCount,
  formatIntegrityRate,
  formatIntegrityValue,
} from './integrityReport.js'

describe('integrity report formatting', () => {
  it('preserves exact counts and distinguishes unknown from zero', () => {
    expect(formatIntegrityCount(9223372036854775807n)).toBe('9223372036854775807')
    expect(formatIntegrityCount(null)).toBe('无法计算')
    expect(formatIntegrityCount(0n)).toBe('0')
  })

  it('moves the parsed decimal point without binary floating point rounding', () => {
    expect(formatIntegrityRate('0.950000')).toBe('95%')
    expect(formatIntegrityRate('0.999999')).toBe('99.9999%')
    expect(formatIntegrityRate(null)).toBe('无法计算')
  })

  it('formats every supported business-key scalar without losing precision', () => {
    expect(formatIntegrityValue('1234567890.123400')).toBe('1234567890.123400')
    expect(formatIntegrityValue(9223372036854775807n)).toBe('9223372036854775807')
    expect(formatIntegrityValue(42)).toBe('42')
    expect(formatIntegrityValue(true)).toBe('true')
    expect(formatIntegrityValue(null)).toBe('null')
  })
})
