import { flushPromises, mount } from '@vue/test-utils'
import { ElButton, ElDatePicker } from 'element-plus'
import { nextTick } from 'vue'

const api = vi.hoisted(() => ({
  listDataSources: vi.fn(),
  listApis: vi.fn(),
  downloadDataset: vi.fn(),
}))

vi.mock('../api/dataSources.js', () => ({
  listDataSources: api.listDataSources,
  listApis: api.listApis,
}))

vi.mock('../api/downloads.js', () => ({
  downloadDataset: api.downloadDataset,
}))

import { ClientError } from '../api/errors.js'
import AsyncStatePanel from '../components/common/AsyncStatePanel.vue'
import ApiDescription from '../components/download/ApiDescription.vue'
import ApiSelect from '../components/download/ApiSelect.vue'
import DataSourceSelect from '../components/download/DataSourceSelect.vue'
import DownloadAction from '../components/download/DownloadAction.vue'
import DownloadResult from '../components/download/DownloadResult.vue'
import DynamicParameterForm from '../components/download/DynamicParameterForm.vue'
import DownloadView from './DownloadView.vue'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function source(overrides = {}) {
  return {
    pluginId: 'fixture',
    displayName: 'Fixture',
    description: 'Fixture 数据源',
    enabled: true,
    credentialConfigured: true,
    downloadAvailable: true,
    unavailableReason: null,
    ...overrides,
  }
}

function parameter(overrides = {}) {
  return {
    name: 'trade_date',
    label: '交易日期',
    type: 'DATE',
    required: true,
    ...overrides,
  }
}

function descriptor(overrides = {}) {
  return {
    apiName: 'daily',
    displayName: '日线行情',
    category: '行情与估值',
    queryMode: 'trade_date',
    parameters: [parameter()],
    ...overrides,
  }
}

function response(overrides = {}) {
  return {
    requestId: 'request-1',
    outcome: 'SUCCESS',
    pluginId: 'fixture',
    apiName: 'daily',
    sourceRowCount: 12,
    insertedRows: 7,
    updatedRows: 5,
    message: '下载完成',
    ...overrides,
  }
}

function currentApis() {
  const categories = [
    ['basic_organization', 11],
    ['行情与估值', 7],
    ['交易与资金', 6],
    ['互联互通与转融通', 6],
    ['财务与披露', 9],
    ['公司行动', 3],
    ['股东与治理', 7],
  ]
  let index = 0
  return categories.flatMap(([category, count]) =>
    Array.from({ length: count }, () => {
      index += 1
      return descriptor({
        apiName: index === 1 ? 'daily' : `api_${index}`,
        displayName: index === 1 ? '日线行情' : `接口 ${index}`,
        category,
      })
    }),
  )
}

async function mountReady({
  sources = [source()],
  apis = [descriptor()],
  attachTo,
} = {}) {
  api.listDataSources.mockResolvedValueOnce(sources)
  api.listApis.mockResolvedValueOnce(apis)
  const wrapper = mount(DownloadView, { attachTo })
  await flushPromises()
  return wrapper
}

async function selectApi(wrapper, apiName = 'daily') {
  wrapper.getComponent(ApiSelect).vm.$emit('update:modelValue', apiName)
  await nextTick()
}

async function setFirstParameter(wrapper, value) {
  wrapper
    .getComponent(DynamicParameterForm)
    .getComponent(ElDatePicker)
    .vm.$emit('update:modelValue', value)
  await nextTick()
}

async function clickDownload(wrapper) {
  await wrapper.getComponent(DownloadAction).get('button').trigger('click')
  await flushPromises()
}

function expectBefore(first, second) {
  expect(
    first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING,
  ).toBeTruthy()
}

beforeEach(() => {
  vi.resetAllMocks()
})

describe('DownloadView', () => {
  it('loads sources on mount and retries a safe metadata failure', async () => {
    const pending = deferred()
    const failure = new ClientError('NETWORK', 'source-request')
    api.listDataSources
      .mockReturnValueOnce(pending.promise)
      .mockResolvedValueOnce([])
    const wrapper = mount(DownloadView)
    await nextTick()

    expect(wrapper.findAll('h1')).toHaveLength(1)
    expect(wrapper.get('h1').text()).toBe('数据下载')
    expect(api.listDataSources).toHaveBeenCalledTimes(1)
    expect(wrapper.getComponent(AsyncStatePanel).props()).toMatchObject({
      state: 'LOADING',
      title: '正在加载下载配置',
      message: '请稍候。',
    })
    expect(wrapper.get('[role="status"]').attributes('aria-live')).toBe(
      'polite',
    )
    expect(wrapper.text()).not.toContain('数据下载模块尚未完成')

    pending.reject(failure)
    await flushPromises()
    expect(wrapper.getComponent(AsyncStatePanel).props()).toMatchObject({
      state: 'FAILURE',
      title: '下载配置加载失败',
      message: '无法连接服务，请检查网络后重试。',
    })
    expect(wrapper.get('[role="alert"]').text()).toContain(
      '请求 ID：source-request',
    )
    const reload = wrapper
      .findAllComponents(ElButton)
      .find((button) => button.text() === '重新加载')
    expect(reload).toBeDefined()

    await reload.get('button').trigger('click')
    await flushPromises()
    expect(api.listDataSources).toHaveBeenCalledTimes(2)
    expect(wrapper.getComponent(AsyncStatePanel).props()).toMatchObject({
      state: 'INITIAL',
      title: '请选择数据接口',
      message: '选择接口后填写参数并开始下载。',
    })
    expect(wrapper.findComponent(DownloadResult).exists()).toBe(false)
  })

  it('defaults one available source and passes all 49 descriptors to the API selector', async () => {
    const sources = [source()]
    const apis = currentApis()
    const wrapper = await mountReady({ sources, apis })

    expect(wrapper.getComponent(DataSourceSelect).props()).toMatchObject({
      modelValue: 'fixture',
      sources,
      disabled: false,
    })
    expect(api.listApis.mock.calls).toEqual([['fixture']])
    expect(wrapper.getComponent(ApiSelect).props('apis')).toEqual(apis)
    expect(wrapper.getComponent(ApiSelect).props('modelValue')).toBe('')
    expect(wrapper.getComponent(DownloadAction).props('disabled')).toBe(true)
    expect(wrapper.getComponent(AsyncStatePanel).props('title')).toBe(
      '请选择数据接口',
    )
  })

  it('renders the selected API in order and blocks an invalid form submission', async () => {
    const daily = descriptor()
    const wrapper = await mountReady({ apis: [daily], attachTo: document.body })

    try {
      await selectApi(wrapper)
      expect(wrapper.getComponent(ApiDescription).props('api')).toBe(daily)
      expect(wrapper.getComponent(DynamicParameterForm).props()).toMatchObject({
        parameters: daily.parameters,
        disabled: false,
      })
      expect(wrapper.getComponent(DownloadAction).props('disabled')).toBe(
        false,
      )

      const order = [
        wrapper.getComponent(DataSourceSelect).element,
        wrapper.getComponent(ApiSelect).element,
        wrapper.getComponent(ApiDescription).element,
        wrapper.getComponent(DynamicParameterForm).element,
        wrapper.getComponent(DownloadAction).element,
        wrapper.getComponent(AsyncStatePanel).element,
      ]
      order.slice(0, -1).forEach((element, index) =>
        expectBefore(element, order[index + 1]),
      )

      await clickDownload(wrapper)
      expect(api.downloadDataset).not.toHaveBeenCalled()
      expect(wrapper.get('.field-error').text()).toBe('此项为必填项')
      expect(document.activeElement).toBe(
        wrapper
          .getComponent(DynamicParameterForm)
          .get('[data-parameter="trade_date"] input').element,
      )
    } finally {
      wrapper.unmount()
    }
  })

  it('submits one normalized snapshot, locks every control, and shows success', async () => {
    const pending = deferred()
    const completed = response()
    api.downloadDataset.mockReturnValueOnce(pending.promise)
    const wrapper = await mountReady()
    await selectApi(wrapper)
    await setFirstParameter(wrapper, '2026-09-04')

    await wrapper.getComponent(DownloadAction).get('button').trigger('click')
    await flushPromises()
    expect(api.downloadDataset).toHaveBeenCalledWith({
      pluginId: 'fixture',
      apiName: 'daily',
      params: { trade_date: '20260904' },
    })
    expect(wrapper.getComponent(DataSourceSelect).props('disabled')).toBe(true)
    expect(wrapper.getComponent(ApiSelect).props('disabled')).toBe(true)
    expect(wrapper.getComponent(DynamicParameterForm).props('disabled')).toBe(
      true,
    )
    expect(wrapper.getComponent(DownloadAction).props()).toMatchObject({
      disabled: true,
      submitting: true,
    })
    expect(
      wrapper.getComponent(DownloadAction).get('button').attributes('aria-busy'),
    ).toBe('true')
    expect(wrapper.getComponent(DownloadAction).text()).toBe('开始下载')
    expect(wrapper.findComponent(AsyncStatePanel).exists()).toBe(false)
    expect(wrapper.findComponent(DownloadResult).exists()).toBe(false)
    expect(wrapper.text()).not.toMatch(/下载中|适配中|入库中|进度|百分比/)

    pending.resolve(completed)
    await flushPromises()
    expect(wrapper.getComponent(DownloadResult).props()).toMatchObject({
      state: 'SUCCESS',
      result: completed,
      canRetry: false,
    })
    expect(wrapper.text()).toContain('上游返回数12')
    expect(wrapper.text()).toContain('插入数7')
    expect(wrapper.text()).toContain('更新数5')
    expect(wrapper.getComponent(DataSourceSelect).props('disabled')).toBe(false)
  })

  it('preserves an EMPTY outcome without counts, failure, or placeholders', async () => {
    api.downloadDataset.mockResolvedValueOnce(
      response({
        outcome: 'EMPTY',
        sourceRowCount: 99,
        insertedRows: 99,
        updatedRows: 99,
      }),
    )
    const wrapper = await mountReady()
    await selectApi(wrapper)
    await setFirstParameter(wrapper, '2026-09-04')
    await clickDownload(wrapper)

    expect(wrapper.getComponent(DownloadResult).props('state')).toBe('EMPTY')
    expect(wrapper.text()).toContain('下载成功，0 条数据')
    expect(wrapper.text()).toContain('本次请求没有可写入的数据。')
    expect(wrapper.text()).not.toMatch(
      /上游返回数|插入数|更新数|下载失败|使用原参数重试|占位记录/,
    )
  })

  it('shows a safe download failure and retries its frozen parameters', async () => {
    const failure = new ClientError('NETWORK', 'download-request')
    api.downloadDataset
      .mockRejectedValueOnce(failure)
      .mockResolvedValueOnce(response())
    const wrapper = await mountReady()
    await selectApi(wrapper)
    await setFirstParameter(wrapper, '2026-09-04')
    await clickDownload(wrapper)

    expect(wrapper.getComponent(DownloadResult).props()).toMatchObject({
      state: 'FAILURE',
      error: failure,
      canRetry: true,
    })
    expect(wrapper.get('[role="alert"]').text()).toContain(
      '无法连接服务，请检查网络后重试。',
    )
    expect(wrapper.text()).toContain('请求 ID：download-request')

    await setFirstParameter(wrapper, '2026-09-05')
    const retry = wrapper
      .findAllComponents(ElButton)
      .find((button) => button.text() === '使用原参数重试')
    await retry.get('button').trigger('click')
    await flushPromises()

    expect(api.downloadDataset.mock.calls).toEqual([
      [
        {
          pluginId: 'fixture',
          apiName: 'daily',
          params: { trade_date: '20260904' },
        },
      ],
      [
        {
          pluginId: 'fixture',
          apiName: 'daily',
          params: { trade_date: '20260904' },
        },
      ],
    ])
    expect(wrapper.getComponent(DownloadResult).props('state')).toBe(
      'SUCCESS',
    )
  })

  it('clears results and parameters when the API or source changes', async () => {
    const sources = [
      source({ pluginId: 'first', displayName: 'First' }),
      source({ pluginId: 'second', displayName: 'Second' }),
    ]
    const daily = descriptor()
    const weekly = descriptor({
      apiName: 'weekly',
      displayName: '周线行情',
      parameters: [
        parameter({ name: 'keyword', label: '关键词', type: 'TEXT' }),
      ],
    })
    const secondApi = descriptor({
      apiName: 'monthly',
      displayName: '月线行情',
    })
    api.listDataSources.mockResolvedValueOnce(sources)
    api.listApis.mockImplementation((pluginId) =>
      Promise.resolve(pluginId === 'first' ? [daily, weekly] : [secondApi]),
    )
    api.downloadDataset.mockResolvedValueOnce(
      response({ pluginId: 'first' }),
    )
    const wrapper = mount(DownloadView)
    await flushPromises()

    wrapper
      .getComponent(DataSourceSelect)
      .vm.$emit('update:modelValue', 'first')
    await flushPromises()
    await selectApi(wrapper, 'daily')
    await setFirstParameter(wrapper, '2026-09-04')
    await clickDownload(wrapper)
    expect(wrapper.getComponent(DownloadResult).props('state')).toBe(
      'SUCCESS',
    )

    await selectApi(wrapper, 'weekly')
    expect(wrapper.findComponent(DownloadResult).exists()).toBe(false)
    expect(
      wrapper
        .getComponent(DynamicParameterForm)
        .get('[data-parameter]')
        .attributes('data-parameter'),
    ).toBe('keyword')
    expect(
      wrapper.getComponent(DynamicParameterForm).get('input').element.value,
    ).toBe('')

    wrapper
      .getComponent(DataSourceSelect)
      .vm.$emit('update:modelValue', 'second')
    await flushPromises()
    expect(wrapper.getComponent(ApiSelect).props('modelValue')).toBe('')
    expect(wrapper.getComponent(ApiSelect).props('apis')).toEqual([secondApi])
    expect(wrapper.findComponent(DynamicParameterForm).exists()).toBe(false)
    expect(wrapper.findComponent(DownloadResult).exists()).toBe(false)
    expect(api.listApis.mock.calls).toEqual([['first'], ['second']])
  })

  it('keeps the real controls keyboard-focusable in page order', async () => {
    const wrapper = await mountReady({ attachTo: document.body })

    try {
      const sourceInput = wrapper
        .getComponent(DataSourceSelect)
        .get('input[role="combobox"]')
      const apiInput = wrapper
        .getComponent(ApiSelect)
        .get('input[role="combobox"]')
      sourceInput.element.focus()
      expect(document.activeElement).toBe(sourceInput.element)
      apiInput.element.focus()
      expect(document.activeElement).toBe(apiInput.element)

      await apiInput.trigger('keydown', { key: 'ArrowDown' })
      await flushPromises()
      await apiInput.trigger('keydown', { key: 'Enter' })
      await flushPromises()
      expect(wrapper.getComponent(ApiSelect).props('modelValue')).toBe('daily')

      const parameterInput = wrapper
        .getComponent(DynamicParameterForm)
        .get('input')
      const button = wrapper.getComponent(DownloadAction).get('button')
      parameterInput.element.focus()
      expect(document.activeElement).toBe(parameterInput.element)
      button.element.focus()
      expect(document.activeElement).toBe(button.element)
      expect(button.attributes('type')).toBe('button')
      expectBefore(sourceInput.element, apiInput.element)
      expectBefore(apiInput.element, parameterInput.element)
      expectBefore(parameterInput.element, button.element)
    } finally {
      wrapper.unmount()
    }
  })
})
