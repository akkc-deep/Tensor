import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'

import { createAppRouter } from '../router/index.js'
import AppLayout from './AppLayout.vue'

async function mountAt(path) {
  const router = createAppRouter(createMemoryHistory())
  await router.push(path)
  await router.isReady()

  return {
    router,
    wrapper: mount(AppLayout, {
      attachTo: document.body,
      global: { plugins: [router] },
    }),
  }
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
      expect(wrapper.get('main h1').text()).toBe('数据下载')
      expect(wrapper.get('main p').text()).toBe(
        '数据下载模块尚未完成，后续任务将提供数据源、接口、参数和下载结果。',
      )
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
      expect(wrapper.get('main p').text()).toBe(
        '数据查看模块尚未完成，后续任务将提供数据集筛选、表格和分页。',
      )
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
    } finally {
      wrapper.unmount()
    }
  })
})
