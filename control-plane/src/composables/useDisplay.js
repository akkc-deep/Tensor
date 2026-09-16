import { computed, inject, readonly, ref } from 'vue'

export const DISPLAY_KEY = Symbol('display')
export const DISPLAY_SCALES = ['100', '125', '150', '175', '200']
const STORAGE_KEY = 'tensor-display-scale'
const valid = value => value === 'auto' || DISPLAY_SCALES.includes(value)

export function createDisplayState() {
  const requested = ref('auto')
  const width = ref(window.innerWidth)
  const storageStatus = ref('saved')
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (valid(saved)) requested.value = saved
  } catch {
    storageStatus.value = 'preview-only'
  }
  const scale = computed(() => {
    const automatic = width.value >= 3520 ? 2 : width.value >= 2880 ? 1.5 : width.value >= 2240 ? 1.25 : 1
    const preferred = requested.value === 'auto' ? automatic : Number(requested.value) / 100
    // Keep the existing desktop workbench usable when a large-screen window is narrowed.
    const fitting = Math.max(1, Math.floor(width.value / 1024 * 4) / 4)
    return Math.min(preferred, fitting)
  })
  function render() {
    document.documentElement.style.setProperty('--tensor-display-scale', String(scale.value))
  }
  function resize() {
    width.value = window.innerWidth
    render()
  }
  function apply(value) {
    if (!valid(value)) return false
    requested.value = value
    render()
    try {
      localStorage.setItem(STORAGE_KEY, value)
      storageStatus.value = 'saved'
    } catch {
      storageStatus.value = 'preview-only'
    }
    return true
  }
  render()
  window.addEventListener('resize', resize)
  return {
    requested: readonly(requested), scale, storageStatus: readonly(storageStatus), apply,
    dispose() {
      window.removeEventListener('resize', resize)
      document.documentElement.style.removeProperty('--tensor-display-scale')
    },
  }
}

export const useDisplay = () => inject(DISPLAY_KEY, null)
