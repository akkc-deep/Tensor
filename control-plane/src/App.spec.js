import { flushPromises, mount } from '@vue/test-utils'
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
import { createAppRouter } from './router/index.js'

beforeEach(() => {
  vi.resetAllMocks()
  metadataApi.listDataSources.mockResolvedValue([])
})

describe('App', () => {
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
      expect(wrapper.findAll('header')).toHaveLength(1)
      expect(wrapper.findAll('nav[aria-label="主导航"]')).toHaveLength(1)
      expect(wrapper.findAll('main')).toHaveLength(1)
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
