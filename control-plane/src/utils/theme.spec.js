import { DEFAULT_ACCENT, contrastRatio, createTheme } from './theme.js'

const DEFAULT_COLORS = {
  bg: '#f7f9fb',
  surface: '#ffffff',
  raised: '#f1f4f8',
  nav: '#ffffff',
  line: '#e0e6ee',
  text: '#1f2d43',
  muted: '#52627a',
  accentBg: '#eaf0fa',
  accent: '#3565b6',
  success: '#28745a',
  error: '#b72d47',
}

const SURFACES = ['bg', 'surface', 'raised', 'nav', 'accentBg']

describe('createTheme', () => {
  it('returns the complete confirmed palette for the default accent', () => {
    expect(DEFAULT_ACCENT).toBe('#3565b6')
    expect(createTheme('#3565b6')).toEqual({
      requested: '#3565b6',
      applied: '#3565b6',
      colors: DEFAULT_COLORS,
    })
  })

  it('normalizes existing custom colors without tinting Studio surfaces', () => {
    expect(createTheme('#B52C63')).toEqual({
      requested: '#b52c63', applied: '#b52c63',
      colors: { ...DEFAULT_COLORS, accent: '#b52c63' },
    })
  })

  it.each(['#28745a', '#3565b6', '#745942', '#383d43'])(
    'preserves the Demo preset %s exactly', (color) => {
      expect(createTheme(color).applied).toBe(color)
    },
  )

  it.each([
    null,
    undefined,
    '',
    '#123',
    '#1234567',
    '2857b4',
    '#gggggg',
  ])('rejects malformed six-digit HEX input %s', (value) => {
    expect(createTheme(value)).toBeNull()
  })

  it.each(['#ffffff', '#ffff00', '#00ff00', '#000000', '#b52c63'])(
    'keeps the applied operation color readable for %s',
    (value) => {
      const theme = createTheme(value)

      for (const role of SURFACES) {
        expect(contrastRatio(theme.applied, theme.colors[role])).toBeGreaterThanOrEqual(4.5)
      }
      expect(contrastRatio(theme.applied, '#ffffff')).toBeGreaterThanOrEqual(4.5)
      expect(theme.colors.accent).toBe(theme.applied)
    },
  )

  it('darkens a bright requested color while preserving it as the requested value', () => {
    const theme = createTheme('#ffff00')

    expect(theme.requested).toBe('#ffff00')
    expect(theme.applied).not.toBe(theme.requested)
  })
})

describe('contrastRatio', () => {
  it('calculates WCAG contrast symmetrically at known boundaries', () => {
    expect(contrastRatio('#000000', '#ffffff')).toBeCloseTo(21, 10)
    expect(contrastRatio('#ffffff', '#ffffff')).toBe(1)
    expect(contrastRatio('#2857b4', '#ffffff')).toBeCloseTo(6.746861, 6)
    expect(contrastRatio('#ffffff', '#2857b4')).toBeCloseTo(6.746861, 6)
  })
})
