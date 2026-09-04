import { config } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, vi } from 'vitest'

config.global.plugins = [ElementPlus]

afterEach(() => {
  document.body.innerHTML = ''
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})
