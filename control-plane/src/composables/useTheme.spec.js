import { isReadonly } from 'vue'

import { DEFAULT_ACCENT, createTheme } from '../utils/theme.js'
import { createThemeState } from './useTheme.js'

const STORAGE_KEY = 'tensor-issue004-accent'

function rootVariables() {
  return Object.fromEntries(
    [
      'bg',
      'surface',
      'raised',
      'nav',
      'line',
      'text',
      'muted',
      'accent-bg',
      'accent',
      'success',
      'error',
    ].map((name) => [
      name,
      document.documentElement.style.getPropertyValue(`--tensor-${name}`),
    ]),
  )
}

afterEach(() => {
  localStorage.clear()
  document.documentElement.removeAttribute('style')
})

describe('createThemeState', () => {
  it('restores a saved accent and applies all semantic variables before use', () => {
    localStorage.setItem(STORAGE_KEY, '#B52C63')

    const state = createThemeState()
    const expected = createTheme('#b52c63')

    expect(state.requested.value).toBe('#b52c63')
    expect(state.applied.value).toBe(expected.applied)
    expect(state.storageStatus.value).toBe('saved')
    expect(rootVariables()).toEqual({
      bg: expected.colors.bg,
      surface: expected.colors.surface,
      raised: expected.colors.raised,
      nav: expected.colors.nav,
      line: expected.colors.line,
      text: expected.colors.text,
      muted: expected.colors.muted,
      'accent-bg': expected.colors.accentBg,
      accent: expected.colors.accent,
      success: expected.colors.success,
      error: expected.colors.error,
    })
    expect(isReadonly(state.requested)).toBe(true)
    expect(isReadonly(state.applied)).toBe(true)
    expect(isReadonly(state.storageStatus)).toBe(true)
  })

  it.each([null, '#123', 'broken'])(
    'falls back to the complete default without rewriting missing or damaged storage: %s',
    (saved) => {
      if (saved !== null) localStorage.setItem(STORAGE_KEY, saved)
      const setItem = vi.spyOn(Storage.prototype, 'setItem')

      const state = createThemeState()

      expect(state.requested.value).toBe(DEFAULT_ACCENT)
      expect(state.applied.value).toBe(DEFAULT_ACCENT)
      expect(state.storageStatus.value).toBe('saved')
      expect(rootVariables().bg).toBe('#edf2f6')
      expect(rootVariables().accent).toBe(DEFAULT_ACCENT)
      expect(setItem).not.toHaveBeenCalled()
    },
  )

  it('keeps the default preview available when storage cannot be read', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('storage denied')
    })

    const state = createThemeState()

    expect(state.requested.value).toBe(DEFAULT_ACCENT)
    expect(state.applied.value).toBe(DEFAULT_ACCENT)
    expect(state.storageStatus.value).toBe('preview-only')
    expect(rootVariables().bg).toBe('#edf2f6')
  })

  it('persists only the normalized requested HEX after a successful apply', () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem')
    const state = createThemeState()

    expect(state.apply('#B52C63')).toBe(true)

    expect(state.requested.value).toBe('#b52c63')
    expect(state.applied.value).toBe('#a5285a')
    expect(state.storageStatus.value).toBe('saved')
    expect(setItem).toHaveBeenCalledTimes(1)
    expect(setItem).toHaveBeenCalledWith(STORAGE_KEY, '#b52c63')
    expect(rootVariables().accent).toBe('#a5285a')
    expect(
      document.documentElement.style.getPropertyValue('--tensor-interactive-color'),
    ).toBe('#a5285a')
  })

  it('applies a valid preview even when storage cannot be written', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('storage full')
    })
    const state = createThemeState()

    expect(state.apply('#ffff00')).toBe(true)

    expect(state.requested.value).toBe('#ffff00')
    expect(state.applied.value).not.toBe('#ffff00')
    expect(state.storageStatus.value).toBe('preview-only')
    expect(rootVariables().accent).toBe(state.applied.value)
  })

  it('rejects invalid input without changing state, variables, or storage', () => {
    const state = createThemeState()
    expect(state.apply('#b52c63')).toBe(true)
    const before = {
      requested: state.requested.value,
      applied: state.applied.value,
      status: state.storageStatus.value,
      variables: rootVariables(),
      stored: localStorage.getItem(STORAGE_KEY),
    }
    const setItem = vi.spyOn(Storage.prototype, 'setItem')

    expect(state.apply('#123')).toBe(false)

    expect(state.requested.value).toBe(before.requested)
    expect(state.applied.value).toBe(before.applied)
    expect(state.storageStatus.value).toBe(before.status)
    expect(rootVariables()).toEqual(before.variables)
    expect(localStorage.getItem(STORAGE_KEY)).toBe(before.stored)
    expect(setItem).not.toHaveBeenCalled()
  })

  it('resets the complete default palette and attempts to save the default accent', () => {
    const state = createThemeState()
    state.apply('#b52c63')
    const setItem = vi.spyOn(Storage.prototype, 'setItem')

    expect(state.reset()).toBe(true)

    expect(state.requested.value).toBe(DEFAULT_ACCENT)
    expect(state.applied.value).toBe(DEFAULT_ACCENT)
    expect(state.storageStatus.value).toBe('saved')
    expect(rootVariables().bg).toBe('#edf2f6')
    expect(rootVariables().accent).toBe(DEFAULT_ACCENT)
    expect(setItem).toHaveBeenCalledOnce()
    expect(setItem).toHaveBeenCalledWith(STORAGE_KEY, DEFAULT_ACCENT)
  })

  it('creates isolated state for each application instance', () => {
    const first = createThemeState()
    const second = createThemeState()

    first.apply('#b52c63')

    expect(first.requested.value).toBe('#b52c63')
    expect(second.requested.value).toBe(DEFAULT_ACCENT)
  })
})
