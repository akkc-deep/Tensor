import { readFileSync } from 'node:fs'

import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { createMemoryHistory } from 'vue-router'

const metadataApi = vi.hoisted(() => ({
  listDataSources: vi.fn(),
  listApis: vi.fn(),
}))

vi.mock('./api/dataSources.js', () => ({
  listDataSources: metadataApi.listDataSources,
  listApis: metadataApi.listApis,
}))

import App from './App.vue'
import DownloadAction from './components/download/DownloadAction.vue'
import { useTheme } from './composables/useTheme.js'
import { createAppRouter } from './router/index.js'

const styles = readFileSync('src/style.css', 'utf8')
let styleElement

beforeAll(() => {
  styleElement = document.createElement('style')
  styleElement.textContent = styles
  document.head.append(styleElement)
})

afterAll(() => styleElement.remove())

function rootDeclaration(property) {
  const rootRule = [...styleElement.sheet.cssRules].find(
    (rule) => rule.selectorText === ':root',
  )
  return rootRule.style.getPropertyValue(property).trim()
}

beforeEach(() => {
  vi.resetAllMocks()
  metadataApi.listDataSources.mockResolvedValue([])
  localStorage.clear()
  document.documentElement.removeAttribute('style')
})

describe('App', () => {
  it('maps the confirmed semantic palette across root and Element Plus states', () => {
    expect(rootDeclaration('--tensor-bg')).toBe('#edf2f6')
    expect(rootDeclaration('--tensor-surface')).toBe('#ffffff')
    expect(rootDeclaration('--tensor-raised')).toBe('#f3f6fa')
    expect(rootDeclaration('--tensor-nav')).toBe('#f9fbfd')
    expect(rootDeclaration('--tensor-line')).toBe('#c8d3e0')
    expect(rootDeclaration('--tensor-text')).toBe('#142a42')
    expect(rootDeclaration('--tensor-muted')).toBe('#52677d')
    expect(rootDeclaration('--tensor-accent-bg')).toBe('#e8efff')
    expect(rootDeclaration('--tensor-accent')).toBe('#2857b4')
    expect(rootDeclaration('--tensor-success')).toBe('#14785e')
    expect(rootDeclaration('--tensor-error')).toBe('#b72d47')

    expect(rootDeclaration('--el-bg-color-page')).toBe('var(--tensor-bg)')
    expect(rootDeclaration('--el-bg-color')).toBe('var(--tensor-surface)')
    expect(rootDeclaration('--el-bg-color-overlay')).toBe('var(--tensor-nav)')
    expect(rootDeclaration('--el-text-color-primary')).toBe('var(--tensor-text)')
    expect(rootDeclaration('--el-text-color-regular')).toBe('var(--tensor-muted)')
    expect(rootDeclaration('--el-border-color')).toBe('var(--tensor-line)')
    expect(rootDeclaration('--el-fill-color')).toBe('var(--tensor-raised)')
    expect(rootDeclaration('--el-color-primary')).toBe('var(--tensor-accent)')
    expect(rootDeclaration('--el-color-primary-light-3')).toBe('var(--tensor-accent)')
    expect(rootDeclaration('--el-color-primary-dark-2')).toBe('var(--tensor-accent)')
    expect(rootDeclaration('--el-color-primary-light-9')).toBe('var(--tensor-accent-bg)')
    expect(rootDeclaration('--el-color-success')).toBe('var(--tensor-success)')
    expect(rootDeclaration('--el-color-danger')).toBe('var(--tensor-error)')
    expect(rootDeclaration('--el-table-border-color')).toBe('var(--tensor-line)')
    expect(rootDeclaration('--el-table-header-bg-color')).toBe('var(--tensor-raised)')
  })

  it('initializes root theme variables and provides the shared state before children mount', () => {
    localStorage.setItem('tensor-issue004-accent', '#B52C63')
    let injectedTheme
    let accentAtSetup
    const ThemeConsumer = defineComponent({
      setup() {
        injectedTheme = useTheme()
        accentAtSetup = document.documentElement.style.getPropertyValue('--tensor-accent')
        return () => h('div')
      },
    })

    const wrapper = mount(App, {
      global: { stubs: { AppLayout: ThemeConsumer } },
    })

    try {
      expect(injectedTheme.requested.value).toBe('#b52c63')
      expect(injectedTheme.applied.value).toBe('#a5285a')
      expect(accentAtSetup).toBe('#a5285a')
      expect(document.documentElement.style.getPropertyValue('--tensor-bg')).toBe('#f6e6ec')
    } finally {
      wrapper.unmount()
    }
  })

  it('renders the routed Tensor application shell', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/')
    await router.isReady()
    const wrapper = mount(App, {
      attachTo: document.body,
      global: { plugins: [router] },
    })

    try {
      await flushPromises()
      expect(wrapper.findAll('aside.app-nav')).toHaveLength(1)
      expect(wrapper.findAll('nav[aria-label="工作区导航"]')).toHaveLength(1)
      expect(wrapper.findAll('main#workspace')).toHaveLength(1)
      expect(wrapper.findAll('h1')).toHaveLength(1)
      expect(wrapper.get('h1').text()).toBe('数据下载')
      expect(wrapper.getComponent(DownloadAction).props('disabled')).toBe(true)
      expect(wrapper.text()).toContain('请选择数据接口')
      expect(wrapper.text()).not.toContain('数据下载模块尚未完成')
    } finally {
      wrapper.unmount()
    }
  })
})
