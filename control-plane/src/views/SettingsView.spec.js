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

    expect(wrapper.get('h1').text()).toBe('外观设置')
    expect(wrapper.text()).toContain('让工作台更合你的习惯。')
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
    expect(wrapper.getComponent(FieldError).text()).toBe('请输入 6 位 HEX 颜色，例如 #3565B6')
    expect(input.attributes('aria-invalid')).toBe('true')
    expect(input.attributes('aria-describedby')).toContain('theme-error')
  })

  it('explains brightness correction, reports storage fallback, and resets to Studio', async () => {
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
    expect(wrapper.text()).toContain('仅本次生效，无法保存到当前浏览器')

    await wrapper.get('.color-options .text-button').trigger('click')

    expect(theme.requested.value).toBe('#3565b6')
    expect(theme.applied.value).toBe('#3565b6')
    expect(input.element.value).toBe('#3565B6')
    expect(wrapper.find('.field-error').exists()).toBe(false)
    expect(wrapper.get('.color-options .text-button').text()).toBe('恢复默认')
  })
})


it('applies all four Demo presets, persists selection, and keeps custom color available', async () => {
  const { theme, wrapper } = mountSettings()
  const presets = wrapper.findAll('.color-options button[aria-pressed]')
  expect(presets).toHaveLength(4)
  expect(wrapper.get('details').attributes('open')).toBeUndefined()
  for (const [index, color] of ['#28745a', '#3565b6', '#745942', '#383d43'].entries()) {
    await presets[index].trigger('click')
    expect(theme.applied.value).toBe(color)
    expect(presets[index].attributes('aria-pressed')).toBe('true')
    expect(presets.filter(button => button.attributes('aria-pressed') === 'true')).toHaveLength(1)
    expect(localStorage.getItem('tensor-issue004-accent')).toBe(color)
    expect(wrapper.get('input[type="text"]').element.value).toBe(color.toUpperCase())
  }
  wrapper.unmount()
})
