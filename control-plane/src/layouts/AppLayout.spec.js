import { readFileSync } from 'node:fs'

import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'

const metadataApi = vi.hoisted(() => ({
  listDataSources: vi.fn(),
  listApis: vi.fn(),
}))

vi.mock('../api/dataSources.js', () => ({
  listDataSources: metadataApi.listDataSources,
  listApis: metadataApi.listApis,
}))

import DownloadAction from '../components/download/DownloadAction.vue'
import { createAppRouter } from '../router/index.js'
import AppLayout from './AppLayout.vue'

const styles = readFileSync('src/style.css', 'utf8')

let styleElement

beforeAll(() => {
  styleElement = document.createElement('style')
  styleElement.textContent = styles
  document.head.append(styleElement)
})

afterAll(() => styleElement.remove())

beforeEach(() => {
  vi.resetAllMocks()
  metadataApi.listDataSources.mockResolvedValue([])
})

function declaration(selector, property) {
  const rule = [...styleElement.sheet.cssRules].find((candidate) =>
    candidate.selectorText
      ?.split(',')
      .map((part) => part.trim())
      .includes(selector),
  )

  return rule.style.getPropertyValue(property).trim()
}

function resolveColor(value) {
  const variable = value.match(/var\((--[\w-]+)(?:,\s*(#[\da-f]{6}))?\)/i)
  if (variable) return declaration(':root', variable[1]) || variable[2]

  return value.match(/#[\da-f]{6}/i)[0]
}

function luminance(hex) {
  const channels = hex
    .match(/[\da-f]{2}/gi)
    .map((channel) => Number.parseInt(channel, 16) / 255)
    .map((channel) =>
      channel <= 0.04045
        ? channel / 12.92
        : ((channel + 0.055) / 1.055) ** 2.4,
    )

  return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
}

function contrastRatio(foreground, background) {
  const values = [luminance(foreground), luminance(background)].sort(
    (left, right) => right - left,
  )
  return (values[0] + 0.05) / (values[1] + 0.05)
}

async function mountAt(path) {
  const router = createAppRouter(createMemoryHistory())
  await router.push(path)
  await router.isReady()

  const result = {
    router,
    wrapper: mount(AppLayout, {
      attachTo: document.body,
      global: { plugins: [router] },
    }),
  }
  await flushPromises()
  return result
}

describe('AppLayout', () => {
  it('renders the two-item semantic navigation and active download view', async () => {
    const { wrapper } = await mountAt('/downloads')

    try {
      const nav = wrapper.get('nav[aria-label="主导航"]')
      const links = nav.findAll('a')
      expect(links.map((link) => link.text())).toEqual(['数据下载', '数据查看'])
      expect(links.map((link) => link.attributes('href'))).toEqual([
        '/downloads',
        '/datasets',
      ])
      expect(links[0].attributes('aria-current')).toBe('page')
      expect(links[1].attributes('aria-current')).toBeUndefined()
      const activeColor = resolveColor(
        declaration('.tensor-nav a.router-link-active', 'color'),
      )
      expect(contrastRatio(activeColor, '#ffffff')).toBeGreaterThanOrEqual(4.5)
      expect(contrastRatio(activeColor, '#ecf5ff')).toBeGreaterThanOrEqual(4.5)
      const focusColor = resolveColor(
        declaration('.tensor-nav a:focus-visible', 'outline'),
      )
      expect(contrastRatio(focusColor, '#ffffff')).toBeGreaterThanOrEqual(3)
      expect(wrapper.get('main h1').text()).toBe('数据下载')
      expect(wrapper.get('main h2').text()).toBe('请选择数据接口')
      expect(wrapper.getComponent(DownloadAction).props('disabled')).toBe(
        true,
      )
      expect(wrapper.text()).not.toContain('数据下载模块尚未完成')
    } finally {
      wrapper.unmount()
    }
  })

  it('keeps dataset navigation focusable and switches the active view', async () => {
    const { router, wrapper } = await mountAt('/downloads')

    try {
      const links = wrapper.get('nav[aria-label="主导航"]').findAll('a')
      links[1].element.focus()
      expect(document.activeElement).toBe(links[1].element)

      await links[1].trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.name).toBe('datasets')
      expect(links[0].attributes('aria-current')).toBeUndefined()
      expect(links[1].attributes('aria-current')).toBe('page')
      expect(wrapper.get('main h1').text()).toBe('数据查看')
      expect(wrapper.get('main h2').text()).toBe('请选择数据源')
      expect(wrapper.get('main p').text()).toBe(
        '选择数据源后加载可查询的数据集。',
      )
      expect(wrapper.text()).not.toContain('数据查看模块尚未完成')
    } finally {
      wrapper.unmount()
    }
  })

  it('renders a recoverable not-found view for an unknown path', async () => {
    const { wrapper } = await mountAt('/missing')

    try {
      expect(wrapper.get('main h1').text()).toBe('页面不存在')
      expect(wrapper.get('main p').text()).toBe('当前地址不存在。')
      const returnLink = wrapper.get('main a')
      expect(returnLink.text()).toBe('返回数据下载')
      expect(returnLink.attributes('href')).toBe('/downloads')
      const actionColor = resolveColor(declaration('.page__action', 'color'))
      expect(contrastRatio(actionColor, '#ffffff')).toBeGreaterThanOrEqual(4.5)
    } finally {
      wrapper.unmount()
    }
  })
})
