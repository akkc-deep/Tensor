import { mount } from '@vue/test-utils'
import { h } from 'vue'

import AsyncStatePanel from './AsyncStatePanel.vue'
import FieldError from './FieldError.vue'

function mountPanel(state, options = {}) {
  return mount(AsyncStatePanel, {
    props: {
      state,
      title: `${state} title`,
      message: `${state} message`,
    },
    ...options,
  })
}

describe('AsyncStatePanel', () => {
  it('renders INITIAL guidance without a live region', () => {
    const wrapper = mountPanel('INITIAL')

    expect(wrapper.get('h2').text()).toBe('INITIAL title')
    expect(wrapper.get('p').text()).toBe('INITIAL message')
    expect(wrapper.get('section').attributes('role')).toBeUndefined()
    expect(wrapper.get('section').attributes('aria-live')).toBeUndefined()
    expect(wrapper.find('.async-state-panel__actions').exists()).toBe(false)
  })

  it('announces LOADING politely', () => {
    const panel = mountPanel('LOADING').get('section')

    expect(panel.attributes('role')).toBe('status')
    expect(panel.attributes('aria-live')).toBe('polite')
  })

  it('announces EMPTY politely', () => {
    const panel = mountPanel('EMPTY').get('section')

    expect(panel.attributes('role')).toBe('status')
    expect(panel.attributes('aria-live')).toBe('polite')
  })

  it('uses alert semantics for FAILURE and renders caller actions', () => {
    const wrapper = mountPanel('FAILURE', {
      slots: {
        actions: () => h('button', { type: 'button' }, '重试'),
      },
    })
    const panel = wrapper.get('section')

    expect(panel.attributes('role')).toBe('alert')
    expect(panel.attributes('aria-live')).toBeUndefined()
    expect(wrapper.get('.async-state-panel__actions button').text()).toBe('重试')
  })
})

describe('FieldError', () => {
  it('renders no error element for an empty message', () => {
    const wrapper = mount(FieldError, {
      props: { id: 'trade-date-error', message: '' },
    })

    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('renders a non-empty message as alert text with the supplied id', () => {
    const wrapper = mount(FieldError, {
      props: {
        id: 'trade-date-error',
        message: '<strong>日期无效</strong>',
      },
    })
    const error = wrapper.get('[role="alert"]')

    expect(error.attributes('id')).toBe('trade-date-error')
    expect(error.text()).toBe('<strong>日期无效</strong>')
    expect(error.find('strong').exists()).toBe(false)
  })
})
