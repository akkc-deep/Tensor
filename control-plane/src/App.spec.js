import { mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'

import App from './App.vue'
import { createAppRouter } from './router/index.js'

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
      expect(wrapper.findAll('header')).toHaveLength(1)
      expect(wrapper.findAll('nav[aria-label="主导航"]')).toHaveLength(1)
      expect(wrapper.findAll('main')).toHaveLength(1)
      expect(wrapper.findAll('h1')).toHaveLength(1)
      expect(wrapper.get('h1').text()).toBe('数据下载')
    } finally {
      wrapper.unmount()
    }
  })
})
