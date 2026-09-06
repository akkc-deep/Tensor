import { inject, readonly, ref } from 'vue'

import { DEFAULT_ACCENT, createTheme } from '../utils/theme.js'

export const THEME_KEY = Symbol('theme')

const STORAGE_KEY = 'tensor-issue004-accent'

const CSS_ROLES = {
  bg: 'bg',
  surface: 'surface',
  raised: 'raised',
  nav: 'nav',
  line: 'line',
  text: 'text',
  muted: 'muted',
  accentBg: 'accent-bg',
  accent: 'accent',
  success: 'success',
  error: 'error',
}

export function createThemeState() {
  const requested = ref(DEFAULT_ACCENT)
  const applied = ref(DEFAULT_ACCENT)
  const storageStatus = ref('saved')

  function commit(theme) {
    requested.value = theme.requested
    applied.value = theme.applied
    const style = document.documentElement.style
    for (const [role, name] of Object.entries(CSS_ROLES)) {
      style.setProperty(`--tensor-${name}`, theme.colors[role])
    }
    style.setProperty('--tensor-interactive-color', theme.applied)
  }

  function apply(value) {
    const theme = createTheme(value)
    if (theme === null) return false

    commit(theme)
    try {
      localStorage.setItem(STORAGE_KEY, theme.requested)
      storageStatus.value = 'saved'
    } catch {
      storageStatus.value = 'preview-only'
    }
    return true
  }

  function reset() {
    return apply(DEFAULT_ACCENT)
  }

  let initial = DEFAULT_ACCENT
  try {
    initial = localStorage.getItem(STORAGE_KEY) || DEFAULT_ACCENT
  } catch {
    storageStatus.value = 'preview-only'
  }
  commit(createTheme(initial) || createTheme(DEFAULT_ACCENT))

  return {
    requested: readonly(requested),
    applied: readonly(applied),
    storageStatus: readonly(storageStatus),
    apply,
    reset,
  }
}

export function useTheme() {
  return inject(THEME_KEY)
}
