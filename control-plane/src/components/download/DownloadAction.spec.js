import { mount } from '@vue/test-utils'

import DownloadAction from './DownloadAction.vue'

describe('DownloadAction', () => {
  it('submits from a real task-labelled button', async () => {
    const wrapper = mount(DownloadAction)
    const button = wrapper.get('button')

    expect(button.text()).toBe('开始下载')
    expect(button.attributes('type')).toBe('button')
    expect(button.attributes('aria-busy')).toBe('false')
    await button.trigger('click')
    expect(wrapper.emitted('submit')).toEqual([[]])
  })

  it.each([
    [{ mode: 'RANGE' }, '开始批量下载'],
    [{ submitting: true }, '正在创建…'],
    [{ submitting: true, recovering: true }, '正在查找…'],
  ])('names the current operation %#', (props, label) => {
    const wrapper = mount(DownloadAction, { props })
    expect(wrapper.get('button').text()).toBe(label)
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
