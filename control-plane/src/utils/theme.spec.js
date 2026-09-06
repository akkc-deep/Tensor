import { DEFAULT_ACCENT, contrastRatio, createTheme } from './theme.js'

const DEFAULT_COLORS = {
  bg: '#edf2f6',
  surface: '#ffffff',
  raised: '#f3f6fa',
  nav: '#f9fbfd',
  line: '#c8d3e0',
  text: '#142a42',
  muted: '#52677d',
  accentBg: '#e8efff',
  accent: '#2857b4',
  success: '#14785e',
  error: '#b72d47',
}

const SURFACES = ['bg', 'surface', 'raised', 'nav', 'accentBg']

describe('createTheme', () => {
  it('returns the complete confirmed palette for the default accent', () => {
    expect(DEFAULT_ACCENT).toBe('#2857b4')
    expect(createTheme('#2857b4')).toEqual({
      requested: '#2857b4',
      applied: '#2857b4',
      colors: DEFAULT_COLORS,
    })
  })

  it('normalizes valid uppercase input and reproduces the confirmed mix ratios', () => {
    expect(createTheme('#B52C63')).toEqual({
      requested: '#b52c63',
      applied: '#a5285a',
      colors: {
        bg: '#f6e6ec',
        surface: '#fefbfc',
        raised: '#f9eef3',
        nav: '#faf0f4',
        line: '#e9c0d0',
        text: '#242a45',
        muted: '#5e607a',
        accentBg: '#f5e1e9',
        accent: '#a5285a',
        success: '#14785e',
        error: '#b72d47',
      },
    })
  })

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
        expect(contrastRatio(theme.applied, theme.colors[role])).toBeGreaterThanOrEqual(5.5)
      }
      expect(contrastRatio(theme.applied, '#ffffff')).toBeGreaterThanOrEqual(5.5)
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
