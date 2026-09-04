import { mount } from '@vue/test-utils'

import App from './App.vue'

describe('App', () => {
  it('renders the current root component', () => {
    const wrapper = mount(App, { attachTo: document.body })

    try {
      expect(wrapper.findAll('h1')).toHaveLength(1)
      expect(wrapper.get('h1').text()).toBe('Get started')
    } finally {
      wrapper.unmount()
    }
  })
})
