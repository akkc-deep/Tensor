import { mount } from '@vue/test-utils'
import { ElButton } from 'element-plus'

import { ApiError, ClientError } from '../../api/errors.js'
import AsyncStatePanel from '../common/AsyncStatePanel.vue'
import DownloadAction from './DownloadAction.vue'
import DownloadResult from './DownloadResult.vue'

function response(overrides = {}) {
  return {
    requestId: 'request-1',
    outcome: 'SUCCESS',
    pluginId: 'fixture',
    apiName: 'daily',
    sourceRowCount: 12,
    insertedRows: 7,
    updatedRows: 5,
    message: '<strong>internal success detail</strong>',
    ...overrides,
  }
}

describe('DownloadAction', () => {
  it('emits one submit for each unlocked button click', async () => {
    const wrapper = mount(DownloadAction)
    const action = wrapper.getComponent(ElButton)

    expect(action.text()).toBe('开始下载')
    expect(action.props()).toMatchObject({
      type: 'primary',
      nativeType: 'button',
      disabled: false,
      loading: false,
    })
    expect(action.get('button').attributes('type')).toBe('button')

    await action.get('button').trigger('click')
    await action.get('button').trigger('click')
    expect(wrapper.emitted('submit')).toEqual([[], []])
  })

  it('blocks disabled and submitting actions without changing its label', async () => {
    const wrapper = mount(DownloadAction, { props: { disabled: true } })
    const action = wrapper.getComponent(ElButton)

    expect(action.props('disabled')).toBe(true)
    action.vm.$emit('click')
    expect(wrapper.emitted('submit')).toBeUndefined()

    await wrapper.setProps({ disabled: false, submitting: true })
    expect(action.props()).toMatchObject({ disabled: true, loading: true })
    expect(action.get('button').attributes('aria-busy')).toBe('true')
    expect(action.text()).toBe('开始下载')
    action.vm.$emit('click')
    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(wrapper.text()).not.toMatch(/下载中|适配中|入库中|进度|百分比/)
  })
})

describe('DownloadResult', () => {
  it('announces a successful response and shows its three counts', () => {
    const wrapper = mount(DownloadResult, {
      props: { state: 'SUCCESS', result: response() },
    })
    const status = wrapper.get('[role="status"]')

    expect(status.attributes('aria-live')).toBe('polite')
    expect(wrapper.get('h2').text()).toBe('下载成功')
    expect(wrapper.findAll('dt').map((item) => item.text())).toEqual([
      '上游返回数',
      '插入数',
      '更新数',
    ])
    expect(wrapper.findAll('dd').map((item) => item.text())).toEqual([
      '12',
      '7',
      '5',
    ])
    expect(wrapper.text()).not.toContain('internal success detail')
    expect(wrapper.find('strong').exists()).toBe(false)
    expect(wrapper.text()).not.toMatch(/预览|下载中|适配中|入库中/)
  })

  it('presents an empty response as a successful polite status', () => {
    const wrapper = mount(DownloadResult, {
      props: {
        state: 'EMPTY',
        result: response({
          outcome: 'EMPTY',
          sourceRowCount: 99,
          insertedRows: 99,
          updatedRows: 99,
        }),
      },
    })
    const panel = wrapper.getComponent(AsyncStatePanel)

    expect(panel.props()).toMatchObject({
      state: 'EMPTY',
      title: '下载成功，0 条数据',
      message: '本次请求没有可写入的数据。',
    })
    expect(wrapper.get('[role="status"]').attributes('aria-live')).toBe(
      'polite',
    )
    expect(wrapper.text()).not.toMatch(/上游返回数|插入数|更新数|下载失败|重试|占位/)
  })

  it('shows retryable failures as safe alert text and emits retry', () => {
    const error = new ApiError({
      requestId: 'request-unsafe',
      code: 'SOURCE_RATE_LIMITED',
      message: '<strong>请稍后重试</strong>',
      retryable: true,
      fieldErrors: [{ field: 'token', message: 'SECRET_TOKEN' }],
    })
    error.stack = 'SECRET_STACK'
    error.cause = 'SECRET_CAUSE'
    const wrapper = mount(DownloadResult, {
      props: { state: 'FAILURE', error, canRetry: true },
    })
    const panel = wrapper.getComponent(AsyncStatePanel)

    expect(panel.props()).toMatchObject({
      state: 'FAILURE',
      title: '下载失败',
      message: '<strong>请稍后重试</strong>',
    })
    expect(wrapper.get('[role="alert"]').attributes('aria-live')).toBeUndefined()
    expect(wrapper.text()).toContain('请求 ID：request-unsafe')
    expect(wrapper.find('strong').exists()).toBe(false)
    expect(wrapper.text()).not.toMatch(
      /SOURCE_RATE_LIMITED|SECRET_TOKEN|SECRET_STACK|SECRET_CAUSE/,
    )

    wrapper.getComponent(ElButton).vm.$emit('click')
    expect(wrapper.emitted('retry')).toEqual([[]])
  })

  it('omits retry and request ID for a non-retryable client failure', () => {
    const wrapper = mount(DownloadResult, {
      props: {
        state: 'FAILURE',
        result: response(),
        error: new ClientError('UNEXPECTED'),
        canRetry: false,
      },
    })

    expect(wrapper.get('[role="alert"]').text()).toContain('请求未能发送。')
    expect(wrapper.findComponent(ElButton).exists()).toBe(false)
    expect(wrapper.text()).not.toMatch(
      /请求 ID|internal success detail|fieldErrors|Token|进度|百分比/,
    )
  })
})
