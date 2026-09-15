import { mount } from '@vue/test-utils'

import DownloadAction from './DownloadAction.vue'

describe('DownloadAction', () => {
  it('submits from a real task-labelled button', async () => {
    const wrapper = mount(DownloadAction)
    const button = wrapper.get('button')

    expect(button.text()).toBe('提交任务')
    expect(button.attributes('type')).toBe('button')
    expect(button.attributes('aria-busy')).toBe('false')
    await button.trigger('click')
    expect(wrapper.emitted('submit')).toEqual([[]])
  })

  it.each([
    { disabled: true, submitting: false },
    { disabled: false, submitting: true },
  ])('blocks submission for disabled task state %#', async (props) => {
    const wrapper = mount(DownloadAction, { props })
    const button = wrapper.get('button')

    expect(button.attributes('disabled')).toBeDefined()
    expect(button.attributes('aria-busy')).toBe(String(props.submitting))
    await button.trigger('click')
    expect(wrapper.emitted('submit')).toBeUndefined()
  })
})
