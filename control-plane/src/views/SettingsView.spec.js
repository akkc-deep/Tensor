import { mount } from '@vue/test-utils'

import FieldError from '../components/common/FieldError.vue'
import { createThemeState, THEME_KEY } from '../composables/useTheme.js'
import SettingsView from './SettingsView.vue'

function mountSettings() {
  const theme = createThemeState()
  const wrapper = mount(SettingsView, {
    global: { provide: { [THEME_KEY]: theme } },
  })
  return { theme, wrapper }
}

beforeEach(() => {
  localStorage.clear()
  document.documentElement.removeAttribute('style')
})

describe('SettingsView', () => {
  it('shows the shared page heading and applies a valid color picker value immediately', async () => {
    const { theme, wrapper } = mountSettings()

    expect(wrapper.get('h1').text()).toBe('设置')
    expect(wrapper.text()).toContain('调整工作台外观，让每一次操作都更合心意。')
    expect(wrapper.text()).toContain('外观与主题')

    await wrapper.get('input[type="color"]').setValue('#b52c63')

    expect(theme.requested.value).toBe('#b52c63')
    expect(wrapper.get('input[type="text"]').element.value).toBe('#B52C63')
    expect(wrapper.text()).toContain(theme.applied.value.toUpperCase())
    expect(wrapper.text()).toContain('已保存')
  })

  it('rejects malformed HEX with an associated error and preserves the applied theme', async () => {
    const { theme, wrapper } = mountSettings()
    const applied = theme.applied.value
    const input = wrapper.get('input[type="text"]')

    await input.setValue('#123')
    await wrapper.get('form').trigger('submit')

    expect(theme.applied.value).toBe(applied)
    expect(wrapper.getComponent(FieldError).text()).toBe('请输入 6 位 HEX 颜色，例如 #2857B4')
    expect(input.attributes('aria-invalid')).toBe('true')
    expect(input.attributes('aria-describedby')).toContain('theme-error')
  })

  it('explains brightness correction, reports storage fallback, and resets to glacier white', async () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('storage denied')
    })
    const { theme, wrapper } = mountSettings()
    const input = wrapper.get('input[type="text"]')

    await input.setValue('#ffff00')
    await wrapper.get('form').trigger('submit')

    expect(theme.requested.value).toBe('#ffff00')
    expect(theme.applied.value).not.toBe('#ffff00')
    expect(wrapper.text()).toContain(theme.applied.value.toUpperCase())
    expect(wrapper.text()).toContain('为保证可读性，实际应用色已自动调整亮度。')
    expect(wrapper.text()).toContain('仅本次预览')

    await wrapper.get('button[type="button"]').trigger('click')

    expect(theme.requested.value).toBe('#2857b4')
    expect(theme.applied.value).toBe('#2857b4')
    expect(input.element.value).toBe('#2857B4')
    expect(wrapper.find('.field-error').exists()).toBe(false)
    expect(wrapper.get('button[type="button"]').text()).toBe('恢复冰川白')
  })
})
