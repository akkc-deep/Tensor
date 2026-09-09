import { flushPromises, mount } from '@vue/test-utils'
import { ElButton, ElDatePicker, ElPagination } from 'element-plus'
import { nextTick } from 'vue'
import { createMemoryHistory, routerKey, routeLocationKey } from 'vue-router'
import { createAppRouter } from '../router/index.js'

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

import { ApiError, ClientError } from '../api/errors.js'
import AsyncStatePanel from '../components/common/AsyncStatePanel.vue'
import WorkbenchPanel from '../components/common/WorkbenchPanel.vue'
import ApiDescription from '../components/download/ApiDescription.vue'
import ApiSelect from '../components/download/ApiSelect.vue'
import DataSourceSelect from '../components/download/DataSourceSelect.vue'
import DownloadAction from '../components/download/DownloadAction.vue'
import DownloadResult from '../components/download/DownloadResult.vue'
import DynamicParameterForm from '../components/download/DynamicParameterForm.vue'
import MetadataField from '../components/common/MetadataField.vue'
import DownloadView from './DownloadView.vue'
import results from '../test/fixtures/range-results.json'
import rangeApis from '../test/fixtures/range-apis.json'

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
    parameters: [
      parameter({ name: 'start_date', label: '开始日期', type: 'DATE_RANGE_MEMBER', relatedParameter: 'end_date' }),
      parameter({ name: 'end_date', label: '结束日期', type: 'DATE_RANGE_MEMBER', relatedParameter: 'start_date' }),
    ],
    downloadPolicy: {
      mode: 'TRADE_DATE_RANGE',
      dateSemantic: 'TRADE_DATE',
      description: '来源请求与完整性仍需核实。',
      calendarProfile: 'C-A',
      limits: { maxRangeDays: 31 },
    },
    ...overrides,
  }
}

function response(overrides = {}) {
  return {
    ...structuredClone(results[overrides.outcome ?? 'SUCCESS']),
    requestId: 'request-1',
    outcome: 'SUCCESS',
    pluginId: 'fixture',
    apiName: 'daily',
    sourceRowCount: 12,
    insertedRows: 7,
    updatedRows: 5,
    message: '下载完成',
    completedUnits: 2, failedUnits: 0, notStartedUnits: 0, skippedClosedDates: 0,
    taskId: null, remainingFailedUnits: 0, failureRecordStatus: 'NOT_REQUIRED',
    failures: [], notStartedScopes: [], unconfirmedScopes: [],
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
  router = { push: vi.fn() },
} = {}) {
  api.listDataSources.mockResolvedValueOnce(sources)
  api.listApis.mockResolvedValueOnce(apis)
  const wrapper = mount(DownloadView, { attachTo, global: router.currentRoute ? { plugins: [router] } : { provide: { [routerKey]: router, [routeLocationKey]: { name: 'downloads', query: {} } } } })
  await flushPromises()
  return wrapper
}

async function selectApi(wrapper, apiName = 'daily') {
  wrapper.getComponent(ApiSelect).vm.$emit('update:modelValue', apiName)
  await nextTick()
}

async function setFirstParameter(wrapper, value) {
  const pickers = wrapper.getComponent(DynamicParameterForm).findAllComponents(ElDatePicker)
  pickers[0].vm.$emit('update:modelValue', value)
  pickers[1].vm.$emit('update:modelValue', value)
  await nextTick()
}

async function setParameter(wrapper, name, value) {
  wrapper
    .getComponent(DynamicParameterForm)
    .get(`[data-parameter="${name}"]`)
    .getComponent(ElDatePicker)
    .vm.$emit('update:modelValue', value)
  await nextTick()
}

async function setRawParameter(wrapper, name, value) {
  wrapper.getComponent(DynamicParameterForm).get(`[data-parameter="${name}"]`).getComponent(MetadataField).vm.$emit('update:modelValue', value)
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
  it('shows PARTIAL completed and failed units with confirmed row counts on the real page', async () => {
    api.downloadDataset.mockResolvedValueOnce(response({
      outcome: 'PARTIAL', completedUnits: 2, failedUnits: 1,
      sourceRowCount: 10, insertedRows: 7, updatedRows: 3,
      notStartedUnits: 0, skippedClosedDates: 0,
      taskId: '11111111-1111-4111-8111-111111111111', remainingFailedUnits: 1,
      failureRecordStatus: 'CONFIRMED',
      failures: [{ targetType: 'STOCK', targetValue: '000002.SZ', timeType: 'DATE', timeValue: '2026-09-03', errorCode: 'ADAPTER_TYPE_INVALID', errorMessage: '字段类型不符合要求。' }],
      notStartedScopes: [], unconfirmedScopes: [],
    }))
    const wrapper = await mountReady()
    await selectApi(wrapper)
    await setFirstParameter(wrapper, '2026-09-03')
    await clickDownload(wrapper)
    expect(wrapper.text()).toContain('部分完成')
    const counts = Object.fromEntries(wrapper.findAll('dl > div').map((item) => [item.get('dt').text(), item.get('dd').text()]))
    expect(counts).toMatchObject({ 已完成项: '2', 明确失败项: '1', 已确认返回行数: '10', 已确认新增行数: '7', 已确认更新行数: '3' })
    expect(wrapper.text()).toContain('000002.SZ')
    expect(api.downloadDataset).toHaveBeenCalledOnce()
    wrapper.unmount()
  })

  it('shows a timeout as unconfirmed without replay or invented counts', async () => {
    api.downloadDataset.mockRejectedValueOnce(new ClientError('TIMEOUT', 'timeout-request'))
    const wrapper = await mountReady()
    await selectApi(wrapper)
    await setFirstParameter(wrapper, '2026-09-03')
    await clickDownload(wrapper)
    expect(wrapper.text()).toContain('结果未确认')
    expect(wrapper.text()).toContain('服务端可能仍在执行')
    expect(wrapper.text()).not.toContain('使用原参数重试')
    expect(wrapper.getComponent(DownloadResult).find('dl').exists()).toBe(false)
    expect(api.downloadDataset).toHaveBeenCalledOnce()
    expect(wrapper.getComponent(DownloadAction).props('disabled')).toBe(false)
    wrapper.unmount()
  })

  it.each(['NETWORK', 'INVALID_RESPONSE', 'UNEXPECTED'])('renders %s without counts or a replay control', async (kind) => {
    api.downloadDataset.mockRejectedValueOnce(new ClientError(kind, 'lost-request'))
    const wrapper = await mountReady()
    await selectApi(wrapper)
    await setFirstParameter(wrapper, '2026-09-03')
    await clickDownload(wrapper)
    const result = wrapper.getComponent(DownloadResult)
    expect(result.props('state')).toBe('UNCONFIRMED')
    expect(result.find('dl').exists()).toBe(false)
    expect(result.find('button').exists()).toBe(false)
    expect(result.text()).toContain('服务端可能仍在执行')
    expect(api.downloadDataset).toHaveBeenCalledOnce()
    wrapper.unmount()
  })

  it('routes only the confirmed task ID and starts a new download only on explicit submit', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/downloads?stale=discard')
    await router.isReady()
    const partial = { ...results.PARTIAL, pluginId: 'fixture' }
    const previousAdapter = http.defaults.adapter
    http.defaults.adapter = async config => taskResponse(config, config.url === '/retry-tasks' ? tasks.page : { ...tasks.detail, taskId: partial.taskId })
    api.downloadDataset.mockResolvedValueOnce(partial).mockResolvedValueOnce(response({ requestId: 'new-request' }))
    const wrapper = await mountReady({ router, attachTo: document.body })
    await selectApi(wrapper)
    await setFirstParameter(wrapper, '2026-09-03')
    await clickDownload(wrapper)
    const result = wrapper.getComponent(DownloadResult)
    result.vm.$emit('view-task', '22222222-2222-4222-8222-222222222222')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ stale: 'discard' })
    const taskButton = result.get('button')
    taskButton.element.focus()
    expect(document.activeElement).toBe(taskButton.element)
    await taskButton.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('downloads')
    expect(router.currentRoute.value.query).toEqual({ tab: 'retry-tasks', taskId: partial.taskId })
    expect(api.downloadDataset).toHaveBeenCalledOnce()
    await clickDownload(wrapper)
    expect(api.downloadDataset).toHaveBeenCalledTimes(2)
    expect(wrapper.getComponent(DownloadResult).props('result').requestId).toBe('new-request')
    expect(wrapper.getComponent(DownloadResult).find('button').exists()).toBe(false)
    wrapper.unmount()
    http.defaults.adapter = previousAdapter
  })

  it('preserves an error snapshot with unknown N and remaining counts on the page', async () => {
    const downloadResult = { ...results.UNCONFIRMED, pluginId: 'fixture' }
    api.downloadDataset.mockRejectedValueOnce(new ApiError({ requestId: downloadResult.requestId, code: 'COMMIT_UNCONFIRMED', message: '提交结果未确认', retryable: false, fieldErrors: [], downloadResult }))
    const wrapper = await mountReady()
    await selectApi(wrapper)
    await setFirstParameter(wrapper, '2026-09-03')
    await clickDownload(wrapper)
    const result = wrapper.getComponent(DownloadResult)
    expect(result.props('state')).toBe('UNCONFIRMED')
    expect(result.props('result')).toMatchObject({ notStartedUnits: null, remainingFailedUnits: null, sourceRowCount: 10, insertedRows: 7, updatedRows: 3 })
    expect(result.text()).toContain('本轮已确认小计')
    expect(result.text()).toContain('2026-09-04 至 2026-09-07')
    expect(result.get('button').text()).toBe('查看重试任务')
    expect(api.downloadDataset).toHaveBeenCalledOnce()
    wrapper.unmount()
  })

  it.each(['CALENDAR_UNCONFIRMED', 'DOWNLOAD_BUSY'])('shows the actual %s rejection without a result', async (code) => {
    api.downloadDataset.mockRejectedValueOnce(new ApiError({ requestId: 'rejected-request', code, message: '当前请求未执行。', retryable: true, fieldErrors: [] }))
    const wrapper = await mountReady()
    await selectApi(wrapper)
    await setFirstParameter(wrapper, '2026-09-03')
    await clickDownload(wrapper)
    expect(wrapper.getComponent(DownloadResult).props()).toMatchObject({ state: 'FAILURE', result: null })
    expect(wrapper.getComponent(DownloadResult).find('button').exists()).toBe(false)
    expect(wrapper.text()).toContain(code === 'DOWNLOAD_BUSY' ? '已有下载正在执行' : '未开始下载：日历未确认')
    expect(api.downloadDataset).toHaveBeenCalledOnce()
    wrapper.unmount()
  })

  it('uses the original-condition waiting message and never retries on remount', async () => {
    const pending = deferred()
    api.downloadDataset.mockReturnValueOnce(pending.promise)
    const apis = [descriptor({ apiName: 'calendar', parameters: [], downloadPolicy: { mode: 'ORIGINAL_PARAMS', dateSemantic: null, description: '原条件', limits: null, calendarProfile: null } })]
    const wrapper = await mountReady({ apis })
    await selectApi(wrapper, 'calendar')
    await clickDownload(wrapper)
    expect(wrapper.text()).toContain('下载请求已提交，请等待结果。')
    expect(wrapper.text()).not.toContain('区间下载已开始')
    wrapper.unmount()
    const fresh = await mountReady({ apis })
    pending.resolve(response({ apiName: 'calendar' }))
    await flushPromises()
    expect(fresh.findComponent(DownloadResult).exists()).toBe(false)
    expect(api.downloadDataset).toHaveBeenCalledOnce()
    expect(api.listDataSources).toHaveBeenCalledTimes(2)
    fresh.unmount()
  })

  it.each([['income', 'ts_code', '000002.SZ'], ['margin', 'exchange_id', 'SZSE']])('clears a %s result and task context when %s changes', async (apiName, field, changed) => {
    const selected = rangeApis.apis.find((item) => item.apiName === apiName)
    api.downloadDataset.mockResolvedValueOnce({ ...results.PARTIAL, pluginId: 'fixture', apiName })
    const wrapper = await mountReady({ apis: [selected] })
    await selectApi(wrapper, apiName)
    await setRawParameter(wrapper, field, apiName === 'income' ? '000001.SZ' : 'SSE')
    await setFirstParameter(wrapper, '2026-09-03')
    await clickDownload(wrapper)
    expect(wrapper.getComponent(DownloadResult).props('context').apiName).toBe(apiName)
    await setRawParameter(wrapper, field, changed)
    expect(wrapper.findComponent(DownloadResult).exists()).toBe(false)
    expect(wrapper.text()).not.toContain('查看重试任务')
    expect(api.downloadDataset).toHaveBeenCalledOnce()
    wrapper.unmount()
  })

  it('loads sources on mount and retries a safe metadata failure', async () => {
    const pending = deferred()
    const failure = new ClientError('NETWORK', 'source-request')
    api.listDataSources
      .mockReturnValueOnce(pending.promise)
      .mockResolvedValueOnce([])
    const wrapper = mount(DownloadView, { global: { provide: { [routerKey]: { push: vi.fn() }, [routeLocationKey]: { name: 'downloads', query: {} } } } })
    await nextTick()

    expect(wrapper.findAll('h1')).toHaveLength(1)
    expect(wrapper.get('h1').text()).toBe('数据下载')
    expect(wrapper.text()).toContain('选择数据接口，把市场数据接入你的研究。')
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
    const panels = wrapper.findAllComponents(WorkbenchPanel)
    expect(panels).toHaveLength(2)
    expect(panels.map((panel) => panel.props('title'))).toEqual([
      '下载配置',
      '本次下载结果',
    ])
    expect(panels[0].props('meta')).toBe('fixture')
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
          .get('[data-parameter="start_date"] input').element,
      )
    } finally {
      wrapper.unmount()
    }
  })

  it('submits one normalized snapshot, locks every control, and shows success', async () => {
    const pending = deferred()
    const completed = response({ pluginId: 'tushare_pro' })
    api.downloadDataset.mockReturnValueOnce(pending.promise)
    const wrapper = await mountReady({
      sources: [source({ pluginId: 'tushare_pro' })],
    })
    await selectApi(wrapper)
    await setFirstParameter(wrapper, '2026-08-07')

    await wrapper.getComponent(DownloadAction).get('button').trigger('click')
    await flushPromises()
    expect(api.downloadDataset).toHaveBeenCalledTimes(1)
    expect(api.downloadDataset).toHaveBeenCalledWith({
      pluginId: 'tushare_pro',
      apiName: 'daily',
      params: { start_date: '20260807', end_date: '20260807' },
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
    const status = wrapper.get('[role="status"]')
    expect(status.attributes('aria-live')).toBe('polite')
    expect(status.text()).toContain('正在下载')
    expect(status.text()).toContain('区间下载已开始，不可终止，请等待结果。')
    expect(wrapper.findComponent(DownloadResult).exists()).toBe(false)
    expect(wrapper.text()).not.toMatch(/适配中|入库中|进度|百分比/)

    expect(wrapper.getComponent(DynamicParameterForm).findAll('input').every((input) => input.element.disabled)).toBe(true)
    await wrapper.getComponent(DownloadAction).get('button').trigger('click')
    await flushPromises()
    expect(api.downloadDataset).toHaveBeenCalledOnce()
    expect(wrapper.findAll('button').some((button) => /取消|暂停|停止|终止/.test(button.text()))).toBe(false)

    pending.resolve(completed)
    await flushPromises()
    expect(wrapper.getComponent(DownloadResult).props()).toMatchObject({
      state: 'SUCCESS',
      result: completed,
    })
    expect(wrapper.text()).toContain('已确认返回行数12')
    expect(wrapper.text()).toContain('已确认新增行数7')
    expect(wrapper.text()).toContain('已确认更新行数5')
    expect(wrapper.getComponent(DataSourceSelect).props('disabled')).toBe(false)
  })

  it('blocks incomplete or reversed new_share ranges and submits the two original parameters once', async () => {
    api.downloadDataset.mockResolvedValueOnce(response({ apiName: 'new_share' }))
    const newShare = descriptor({
      apiName: 'new_share',
      parameters: [
        parameter({
          name: 'start_date',
          label: '开始日期',
          type: 'DATE_RANGE_MEMBER',
          relatedParameter: 'end_date',
        }),
        parameter({
          name: 'end_date',
          label: '结束日期',
          type: 'DATE_RANGE_MEMBER',
          relatedParameter: 'start_date',
        }),
      ],
      downloadPolicy: {
        mode: 'NATIVE_RANGE',
        dateSemantic: 'IPO_DATE',
        description: '实际筛选与完整性仍需来源核实。',
        calendarProfile: null,
        limits: { maxRangeDays: 31 },
      },
    })
    const wrapper = await mountReady({
      sources: [source({ pluginId: 'tushare_pro' })],
      apis: [newShare],
    })
    await selectApi(wrapper, 'new_share')

    await setParameter(wrapper, 'start_date', '2026-08-07')
    await clickDownload(wrapper)
    expect(api.downloadDataset).not.toHaveBeenCalled()

    await setParameter(wrapper, 'end_date', '2026-08-03')
    await clickDownload(wrapper)
    expect(api.downloadDataset).not.toHaveBeenCalled()

    await setParameter(wrapper, 'start_date', '2026-08-03')
    await setParameter(wrapper, 'end_date', '2026-08-07')
    await clickDownload(wrapper)
    expect(api.downloadDataset).toHaveBeenCalledTimes(1)
    expect(api.downloadDataset).toHaveBeenCalledWith({
      pluginId: 'tushare_pro',
      apiName: 'new_share',
      params: { start_date: '20260803', end_date: '20260807' },
    })
  })

  it('submits daily endpoints once without its retired trade_date field and rejects 32 days', async () => {
    const range = [
      parameter({ name: 'start_date', label: '开始日期', type: 'DATE_RANGE_MEMBER', relatedParameter: 'end_date' }),
      parameter({ name: 'end_date', label: '结束日期', type: 'DATE_RANGE_MEMBER', relatedParameter: 'start_date' }),
    ]
    const daily = descriptor({
      parameters: range,
      downloadPolicy: {
        mode: 'TRADE_DATE_RANGE', dateSemantic: 'TRADE_DATE', description: '来源仍需核实。',
        calendarProfile: 'C-A', limits: { maxRangeDays: 31 },
      },
    })
    api.downloadDataset.mockResolvedValueOnce(response())
    const wrapper = await mountReady({ apis: [daily] })
    await selectApi(wrapper)
    await setParameter(wrapper, 'start_date', '2026-09-01')
    await setParameter(wrapper, 'end_date', '2026-09-02')
    await clickDownload(wrapper)
    expect(api.downloadDataset).toHaveBeenCalledOnce()
    expect(api.downloadDataset).toHaveBeenCalledWith({ pluginId: 'fixture', apiName: 'daily', params: { start_date: '20260901', end_date: '20260902' } })

    await setParameter(wrapper, 'start_date', '2026-01-31')
    await setParameter(wrapper, 'end_date', '2026-03-03')
    await clickDownload(wrapper)
    expect(api.downloadDataset).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('单次最多支持 31 个自然日（含起止日期），请缩小区间')
  })

  it.each([
    ['income', 'ANN_DATE_RANGE', 'ANN_DATE', [parameter({ name: 'ts_code', type: 'TS_CODE' }), parameter({ name: 'start_date', type: 'DATE_RANGE_MEMBER', relatedParameter: 'end_date' }), parameter({ name: 'end_date', type: 'DATE_RANGE_MEMBER', relatedParameter: 'start_date' })], { ts_code: ' 000001.sz ', start_date: '2026-09-01', end_date: '2026-09-02' }, { ts_code: '000001.SZ', start_date: '20260901', end_date: '20260902' }],
    ['margin', 'TRADE_DATE_RANGE', 'TRADE_DATE', [parameter({ name: 'exchange_id', type: 'ENUM', allowedValues: ['SSE'] }), parameter({ name: 'start_date', type: 'DATE_RANGE_MEMBER', relatedParameter: 'end_date' }), parameter({ name: 'end_date', type: 'DATE_RANGE_MEMBER', relatedParameter: 'start_date' })], { exchange_id: 'SSE', start_date: '2026-09-01', end_date: '2026-09-01' }, { exchange_id: 'SSE', start_date: '20260901', end_date: '20260901' }],
    ['broker_recommend', 'MONTH_RANGE', 'COVERED_MONTH', [parameter({ name: 'start_date', type: 'DATE_RANGE_MEMBER', relatedParameter: 'end_date' }), parameter({ name: 'end_date', type: 'DATE_RANGE_MEMBER', relatedParameter: 'start_date' })], { start_date: '2026-01-31', end_date: '2026-03-02' }, { start_date: '20260131', end_date: '20260302' }],
    ['trade_cal', 'NATIVE_RANGE', 'CALENDAR_DATE', [parameter({ name: 'exchange', type: 'ENUM', allowedValues: ['SSE'] }), parameter({ name: 'start_date', type: 'DATE_RANGE_MEMBER', relatedParameter: 'end_date' }), parameter({ name: 'end_date', type: 'DATE_RANGE_MEMBER', relatedParameter: 'start_date' })], { exchange: 'SSE', start_date: '2026-09-01', end_date: '2026-09-02' }, { exchange: 'SSE', start_date: '20260901', end_date: '20260902' }],
    ['new_share', 'NATIVE_RANGE', 'IPO_DATE', descriptor().parameters, { start_date: '2026-09-01', end_date: '2026-09-02' }, { start_date: '20260901', end_date: '20260902' }],
    ['namechange', 'NATIVE_RANGE', 'ANN_DATE', descriptor().parameters, { start_date: '2026-09-01', end_date: '2026-09-02' }, { start_date: '20260901', end_date: '20260902' }],
  ])('submits exact %s parameters once', async (apiName, mode, dateSemantic, parameters, values, expected) => {
    api.downloadDataset.mockResolvedValueOnce(response({ apiName }))
    const item = descriptor({ apiName, parameters, downloadPolicy: { mode, dateSemantic, description: '来源仍需核实。', calendarProfile: null, limits: { maxRangeDays: 31 } } })
    const wrapper = await mountReady({ apis: [item] })
    await selectApi(wrapper, apiName)
    for (const [name, value] of Object.entries(values)) await setRawParameter(wrapper, name, value)
    await clickDownload(wrapper)
    expect(api.downloadDataset).toHaveBeenCalledOnce()
    expect(api.downloadDataset).toHaveBeenCalledWith({ pluginId: 'fixture', apiName, params: expected })
  })

  it.each([
    ['stock_basic', [parameter({ name: 'list_status', type: 'ENUM', allowedValues: ['L'], defaultValue: 'L' })], { list_status: 'L' }],
    ['index_classify', [], {}],
  ])('submits exact retained %s parameters once', async (apiName, parameters, expected) => {
    api.downloadDataset.mockResolvedValueOnce(response({ apiName }))
    const item = descriptor({ apiName, parameters, downloadPolicy: { mode: 'ORIGINAL_PARAMS', dateSemantic: null, description: '保留原条件。', calendarProfile: null, limits: null } })
    const wrapper = await mountReady({ apis: [item] })
    await selectApi(wrapper, apiName)
    await clickDownload(wrapper)
    expect(api.downloadDataset).toHaveBeenCalledOnce()
    expect(api.downloadDataset).toHaveBeenCalledWith({ pluginId: 'fixture', apiName, params: expected })
  })

  it('preserves an EMPTY outcome with confirmed zero row counts', async () => {
    api.downloadDataset.mockResolvedValueOnce(
      response({
        outcome: 'EMPTY',
        sourceRowCount: 0,
        insertedRows: 0,
        updatedRows: 0,
      }),
    )
    const wrapper = await mountReady()
    await selectApi(wrapper)
    await setFirstParameter(wrapper, '2026-09-04')
    await clickDownload(wrapper)

    expect(wrapper.getComponent(DownloadResult).props('state')).toBe('EMPTY')
    expect(wrapper.text()).toContain('下载完成，0 条数据')
    expect(wrapper.text()).toContain('本次请求没有可写入的数据。')
    expect(wrapper.getComponent(DownloadResult).text()).not.toMatch(
      /上游返回数|插入数|更新数|下载失败|使用原参数重试|占位记录/,
    )
  })

  it('submits an API with no request parameters as an empty object', async () => {
    api.downloadDataset.mockResolvedValueOnce(response({ apiName: 'calendar' }))
    const wrapper = await mountReady({
      apis: [descriptor({ apiName: 'calendar', parameters: [], downloadPolicy: { mode: 'ORIGINAL_PARAMS', dateSemantic: null, description: '保留原条件。', calendarProfile: null, limits: null } })],
    })
    await selectApi(wrapper, 'calendar')

    expect(wrapper.findComponent(DynamicParameterForm).exists()).toBe(false)
    expect(wrapper.text()).toContain('此接口无需填写请求参数。')
    await clickDownload(wrapper)

    expect(api.downloadDataset).toHaveBeenCalledOnce()
    expect(api.downloadDataset).toHaveBeenCalledWith({
      pluginId: 'fixture',
      apiName: 'calendar',
      params: {},
    })
  })

  it.each([
    ['missing policy', undefined],
    ['unknown mode', { mode: 'UNKNOWN', dateSemantic: null, description: '', calendarProfile: null, limits: null }],
    ['missing limit', { mode: 'TRADE_DATE_RANGE', dateSemantic: 'TRADE_DATE', description: '', calendarProfile: 'C-A', limits: null }],
    ['zero limit', { mode: 'TRADE_DATE_RANGE', dateSemantic: 'TRADE_DATE', description: '', calendarProfile: 'C-A', limits: { maxRangeDays: 0 } }],
    ['negative limit', { mode: 'TRADE_DATE_RANGE', dateSemantic: 'TRADE_DATE', description: '', calendarProfile: 'C-A', limits: { maxRangeDays: -1 } }],
    ['fractional limit', { mode: 'TRADE_DATE_RANGE', dateSemantic: 'TRADE_DATE', description: '', calendarProfile: 'C-A', limits: { maxRangeDays: 1.5 } }],
    ['unknown native semantic', { mode: 'NATIVE_RANGE', dateSemantic: 'TRADE_DATE_RANGE', description: '', calendarProfile: null, limits: { maxRangeDays: 31 } }],
    ['prototype native semantic', { mode: 'NATIVE_RANGE', dateSemantic: 'toString', description: '', calendarProfile: null, limits: { maxRangeDays: 31 } }],
  ])('blocks %s before any download, including the zero-parameter path', async (_name, downloadPolicy) => {
    const parameters = ['TRADE_DATE_RANGE', 'NATIVE_RANGE'].includes(downloadPolicy?.mode)
      ? [
          parameter({ name: 'start_date', type: 'DATE_RANGE_MEMBER', relatedParameter: 'end_date' }),
          parameter({ name: 'end_date', type: 'DATE_RANGE_MEMBER', relatedParameter: 'start_date' }),
        ]
      : []
    const wrapper = await mountReady({ apis: [descriptor({ parameters, downloadPolicy })] })
    await selectApi(wrapper)
    expect(wrapper.get('[role="alert"]').text()).toBe('下载配置不完整，请重新加载页面。')
    expect(wrapper.getComponent(DownloadAction).props('disabled')).toBe(true)
    await wrapper.getComponent(DownloadAction).vm.$emit('submit')
    await flushPromises()
    expect(api.downloadDataset).not.toHaveBeenCalled()
  })

  it.each([
    ['missing start', [parameter({ name: 'end_date', type: 'DATE_RANGE_MEMBER', relatedParameter: 'start_date' })], descriptor().downloadPolicy],
    ['duplicate start', [...descriptor().parameters, parameter({ name: 'start_date', type: 'DATE_RANGE_MEMBER', relatedParameter: 'end_date' })], descriptor().downloadPolicy],
    ['wrong endpoint type', [parameter({ name: 'start_date', type: 'DATE', relatedParameter: 'end_date' }), descriptor().parameters[1]], descriptor().downloadPolicy],
    ['optional endpoint', [parameter({ name: 'start_date', type: 'DATE_RANGE_MEMBER', required: false, relatedParameter: 'end_date' }), descriptor().parameters[1]], descriptor().downloadPolicy],
    ['non-reciprocal endpoints', [descriptor().parameters[0], parameter({ name: 'end_date', type: 'DATE_RANGE_MEMBER', relatedParameter: 'other' })], descriptor().downloadPolicy],
    ['retired trade_date', [...descriptor().parameters, parameter({ name: 'trade_date' })], descriptor().downloadPolicy],
    ['retired ann_date', [...descriptor().parameters, parameter({ name: 'ann_date' })], descriptor().downloadPolicy],
    ['retired month', [...descriptor().parameters, parameter({ name: 'month', type: 'MONTH' })], descriptor().downloadPolicy],
    ['original start endpoint', [parameter({ name: 'start_date', type: 'DATE_RANGE_MEMBER', relatedParameter: 'end_date' })], { mode: 'ORIGINAL_PARAMS', dateSemantic: null, description: '', calendarProfile: null, limits: null }],
    ['original end endpoint', [parameter({ name: 'end_date', type: 'DATE_RANGE_MEMBER', relatedParameter: 'start_date' })], { mode: 'ORIGINAL_PARAMS', dateSemantic: null, description: '', calendarProfile: null, limits: null }],
  ])('fails closed for malformed descriptor: %s', async (_name, parameters, downloadPolicy) => {
    const wrapper = await mountReady({ apis: [descriptor({ parameters, downloadPolicy })] })
    await selectApi(wrapper)
    expect(wrapper.get('[role="alert"]').text()).toBe('下载配置不完整，请重新加载页面。')
    await wrapper.getComponent(DownloadAction).vm.$emit('submit')
    await flushPromises()
    expect(api.downloadDataset).not.toHaveBeenCalled()
  })

  it('clears an unconfirmed download and its execution context after editing parameters', async () => {
    const failure = new ClientError('NETWORK', 'download-request')
    api.downloadDataset
      .mockRejectedValueOnce(failure)
      .mockResolvedValueOnce(response())
    const wrapper = await mountReady()
    await selectApi(wrapper)
    await setFirstParameter(wrapper, '2026-09-04')
    await clickDownload(wrapper)

    expect(wrapper.getComponent(DownloadResult).props()).toMatchObject({
      state: 'UNCONFIRMED',
      error: failure,
    })
    expect(wrapper.get('[role="alert"]').text()).toContain(
      '无法连接服务，请检查网络后重试。',
    )
    expect(wrapper.text()).toContain('请求 ID：download-request')

    await setFirstParameter(wrapper, '2026-09-05')
    expect(wrapper.findComponent(DownloadResult).exists()).toBe(false)
    expect(wrapper.text()).not.toContain('使用原参数重试')
    expect(api.downloadDataset).toHaveBeenCalledTimes(1)
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
      downloadPolicy: { mode: 'ORIGINAL_PARAMS', dateSemantic: null, description: '保留原条件。', calendarProfile: null, limits: null },
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
    const wrapper = mount(DownloadView, { global: { provide: { [routerKey]: { push: vi.fn() }, [routeLocationKey]: { name: 'downloads', query: {} } } } })
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

  it('remounts empty endpoints across same-shape and range-original-range switches', async () => {
    const daily = descriptor()
    const newShare = descriptor({ apiName: 'new_share', downloadPolicy: { ...descriptor().downloadPolicy, mode: 'NATIVE_RANGE', dateSemantic: 'IPO_DATE', calendarProfile: null } })
    const original = descriptor({ apiName: 'index_classify', parameters: [], downloadPolicy: { mode: 'ORIGINAL_PARAMS', dateSemantic: null, description: '保留原条件。', calendarProfile: null, limits: null } })
    const wrapper = await mountReady({ apis: [daily, newShare, original] })
    await selectApi(wrapper)
    await setFirstParameter(wrapper, '2026-09-01')
    await selectApi(wrapper, 'new_share')
    expect(wrapper.getComponent(DynamicParameterForm).findAllComponents(ElDatePicker).map(item => item.props('modelValue'))).toEqual(['', ''])
    await selectApi(wrapper, 'index_classify')
    expect(wrapper.findComponent(DynamicParameterForm).exists()).toBe(false)
    await selectApi(wrapper)
    expect(wrapper.getComponent(DynamicParameterForm).findAllComponents(ElDatePicker).map(item => item.props('modelValue'))).toEqual(['', ''])
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

// Real task API, Axios interceptors, flow, router and rendered controls.
import { AxiosError } from 'axios'
import { http } from '../api/http.js'
import tasks from '../test/fixtures/retry-tasks.json'

const taskId = tasks.detail.taskId
function taskResponse(config, data) {
  return { config, status: 200, statusText: 'OK', headers: { 'X-Request-Id': config.headers.get('X-Request-Id') }, data: { ...structuredClone(data), requestId: config.headers.get('X-Request-Id') } }
}
function taskPage(page) {
  const offset = (page - 1) * 20
  const count = page === 3 ? 1 : 20
  return {
    ...structuredClone(tasks.page), page, totalElements: 41, totalPages: 3,
    items: Array.from({ length: count }, (_, index) => ({
      ...structuredClone(tasks.page.items[0]),
      taskId: `7a5d2e8e-7bc2-4d16-8942-${String(offset + index + 1).padStart(12, '0')}`,
      updatedAt: `2026-09-09T02:00:${String(59 - offset - index).padStart(2, '0')}.000Z`,
    })),
  }
}
function taskNotFound(config) {
  const response = taskResponse(config, { code: 'RETRY_TASK_NOT_FOUND', message: '失败任务不存在。', retryable: false, fieldErrors: [] })
  response.status = 404
  return new AxiosError('HTTP 404', 'ERR_BAD_REQUEST', config, null, response)
}
function byButton(wrapper, text) {
  const button = wrapper.findAll('button').find(button => button.text() === text)
  expect(button, `button ${text}`).toBeDefined()
  return button
}
async function mountTasks(query = { tab: 'retry-tasks', taskId }) {
  api.listDataSources.mockResolvedValue([])
  const router = createAppRouter(createMemoryHistory())
  await router.push({ name: 'downloads', query })
  await router.isReady()
  const wrapper = mount(DownloadView, { attachTo: document.body, global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('DownloadView retry task HTTP integration', () => {
  const adapter = http.defaults.adapter
  afterEach(() => { http.defaults.adapter = adapter })

  it('opens the T16 task entrance with one list GET and one detail GET', async () => {
    const requests = []
    http.defaults.adapter = async config => {
      requests.push([config.method, config.url])
      return taskResponse(config, config.url === '/retry-tasks' ? tasks.page : tasks.detail)
    }
    const router = createAppRouter(createMemoryHistory())
    await router.push('/downloads')
    await router.isReady()
    api.downloadDataset.mockResolvedValueOnce({ ...results.PARTIAL, pluginId: 'fixture', taskId })
    const wrapper = await mountReady({ router, attachTo: document.body })
    await selectApi(wrapper)
    await setFirstParameter(wrapper, '2026-09-01')
    await clickDownload(wrapper)
    await byButton(wrapper, '查看重试任务').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('[role="tab"]').map(tab => tab.text())).toEqual(['发起下载', '失败任务'])
    expect(wrapper.get('#tab-retry-tasks').attributes('aria-selected')).toBe('true')
    expect(wrapper.get('.retry-task-detail').text()).toContain('2026-09-01 至 2026-09-10')
    expect(wrapper.get('.retry-task-detail').text()).toContain('2026-09-03')
    expect(wrapper.get('.retry-task-detail').text()).toContain('2026-09-07')
    expect(requests).toEqual([['get', '/retry-tasks'], ['get', `/retry-tasks/${taskId}`]])
    expect(api.downloadDataset).toHaveBeenCalledOnce()
    wrapper.unmount()
  })

  it('sends one zero-data POST and reads only day 7 with the original range unchanged', async () => {
    let executed = false
    const requests = []
    http.defaults.adapter = async config => {
      requests.push({ method: config.method, url: config.url, data: config.data, params: config.params })
      if (config.method === 'post') {
        executed = true
        return taskResponse(config, { ...results.PARTIAL, completedUnits: 1, taskId, failures: tasks.remaining.items.map(({ updatedAt, ...item }) => item), remainingFailedUnits: 1 })
      }
      if (config.url === '/retry-tasks') return taskResponse(config, executed ? tasks.remainingPage : tasks.page)
      return taskResponse(config, executed ? tasks.remaining : tasks.detail)
    }
    const { wrapper } = await mountTasks()
    expect(wrapper.find('.retry-task-detail').exists()).toBe(true)
    await byButton(wrapper, '重试一次').trigger('click')
    await flushPromises()
    expect(wrapper.get('.retry-task-detail').text()).toContain('2026-09-01 至 2026-09-10')
    expect(wrapper.get('.retry-task-detail').text()).not.toContain('2026-09-03')
    expect(wrapper.get('.retry-task-detail').text()).toContain('2026-09-07')
    const summary = wrapper.get('.retry-task-list__items > li')
    expect(summary.findAll('.retry-task-list__scope').map(item => item.text())).toEqual(['原请求条件 · 2026-09-07'])
    expect(Object.fromEntries(summary.findAll('dl > div').map(item => [item.get('dt').text(), item.get('dd').text()]))['当前失败项']).toBe('1')
    expect(wrapper.text()).toContain('本轮重试结果')
    expect(requests.filter(r => r.method === 'post')).toEqual([{ method: 'post', url: `/retry-tasks/${taskId}/execute`, data: undefined, params: undefined }])
    expect(requests.map(r => r.method)).toEqual(['get', 'get', 'post', 'get', 'get'])
    wrapper.unmount()
  })

  it('keeps timeout unknown without replay and refreshes the empty list after detail 404', async () => {
    let timedOut = false
    const requests = []
    http.defaults.adapter = async config => {
      requests.push([config.method, config.url])
      if (config.method === 'post') { timedOut = true; throw new AxiosError('timeout sentinel', 'ECONNABORTED', config) }
      if (config.url === '/retry-tasks') return taskResponse(config, timedOut ? tasks.empty : tasks.page)
      if (timedOut) throw taskNotFound(config)
      return taskResponse(config, tasks.detail)
    }
    const { wrapper } = await mountTasks()
    expect(wrapper.find('.retry-task-detail').exists()).toBe(true)
    await byButton(wrapper, '重试一次').trigger('click')
    await flushPromises()
    expect(wrapper.getComponent(DownloadResult).props()).toMatchObject({ state: 'UNCONFIRMED', result: null })
    expect(wrapper.getComponent(DownloadResult).find('dl').exists()).toBe(false)
    expect(byButton(wrapper, '重试一次').attributes('disabled')).toBeDefined()
    await byButton(wrapper, '刷新详情').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('暂无失败任务')
    expect(wrapper.text()).toContain('查不到任务不代表某次无响应下载成功')
    expect(wrapper.getComponent(DownloadResult).props('state')).toBe('UNCONFIRMED')
    expect(requests.filter(([method]) => method === 'post')).toHaveLength(1)
    expect(requests.map(([method]) => method)).toEqual(['get', 'get', 'post', 'get', 'get'])
    wrapper.unmount()
  })
})

describe('DownloadView retry task pagination integration', () => {
  const adapter = http.defaults.adapter
  afterEach(() => { http.defaults.adapter = adapter })

  it('keeps delayed refresh, navigation and rejected reread on the intended later page', async () => {
    const pages = []
    let delayed = null
    http.defaults.adapter = async config => {
      if (config.url !== '/retry-tasks') return taskResponse(config, tasks.detail)
      const requestedPage = config.params.page
      pages.push(requestedPage)
      const pending = delayed?.page === requestedPage ? delayed : null
      if (pending) {
        delayed = null
        await pending.request.promise
        if (pending.reject) throw new AxiosError('delayed list failure', 'ERR_NETWORK', config)
      }
      return taskResponse(config, taskPage(requestedPage))
    }
    const { wrapper } = await mountTasks({ tab: 'retry-tasks' })
    await wrapper.get('.retry-task-list .btn-next').trigger('click'); await flushPromises()
    expect(wrapper.getComponent(ElPagination).props('currentPage')).toBe(2)

    const refresh = { page: 2, request: deferred() }
    delayed = refresh
    await byButton(wrapper, '刷新列表').trigger('click'); await nextTick(); await flushPromises()
    expect(pages).toEqual([1, 2, 2])
    refresh.request.resolve(); await flushPromises()
    expect(wrapper.getComponent(ElPagination).props('currentPage')).toBe(2)

    const navigation = { page: 3, request: deferred() }
    delayed = navigation
    await wrapper.get('.retry-task-list .btn-next').trigger('click'); await nextTick(); await flushPromises()
    expect(pages).toEqual([1, 2, 2, 3])
    navigation.request.resolve(); await flushPromises()
    expect(wrapper.getComponent(ElPagination).props('currentPage')).toBe(3)

    const rejected = { page: 3, request: deferred(), reject: true }
    delayed = rejected
    await byButton(wrapper, '刷新列表').trigger('click'); await nextTick(); await flushPromises()
    expect(pages).toEqual([1, 2, 2, 3, 3])
    rejected.request.resolve(); await flushPromises()
    expect(wrapper.text()).toContain('失败任务加载失败')
    expect(wrapper.text()).not.toContain('暂无失败任务')
    expect(wrapper.getComponent(ElPagination).props('currentPage')).toBe(3)
    await byButton(wrapper, '重新加载').trigger('click'); await flushPromises()
    expect(pages).toEqual([1, 2, 2, 3, 3, 3])
    expect(wrapper.getComponent(ElPagination).props('currentPage')).toBe(3)
    wrapper.unmount()
  })

  it('keeps the automatic post-execute list refresh on page two', async () => {
    const requests = []
    let executed = false
    let delayed = null
    http.defaults.adapter = async config => {
      requests.push({ method: config.method, url: config.url, page: config.params?.page })
      if (config.method === 'post') {
        executed = true
        return taskResponse(config, { ...results.PARTIAL, completedUnits: 1, taskId, failures: tasks.remaining.items.map(({ updatedAt, ...item }) => item), remainingFailedUnits: 1 })
      }
      if (config.url !== '/retry-tasks') return taskResponse(config, executed ? tasks.remaining : tasks.detail)
      const requestedPage = config.params.page
      const pending = delayed?.page === requestedPage ? delayed : null
      if (pending) { delayed = null; await pending.request.promise }
      return taskResponse(config, taskPage(requestedPage))
    }
    const { wrapper } = await mountTasks()
    await wrapper.get('.retry-task-list .btn-next').trigger('click'); await flushPromises()
    const automatic = { page: 2, request: deferred() }
    delayed = automatic
    await byButton(wrapper, '重试一次').trigger('click'); await nextTick(); await flushPromises()
    expect(requests.filter(request => request.url === '/retry-tasks').map(request => request.page)).toEqual([1, 2, 2])
    automatic.request.resolve(); await flushPromises()
    expect(wrapper.getComponent(ElPagination).props('currentPage')).toBe(2)
    expect(requests.filter(request => request.url === '/retry-tasks').map(request => request.page)).toEqual([1, 2, 2])
    wrapper.unmount()
  })
})

describe('DownloadView retry routes and local locks', () => {
  const adapter = http.defaults.adapter
  afterEach(() => { http.defaults.adapter = adapter })
  it.each([[''], ['short-id'], [['7a5d2e8e-7bc2-4d16-8942-cb9e45d5d1f8', 'other']]])('rejects invalid query %j without a detail request', async invalid => {
    const requests = []
    http.defaults.adapter = async config => { requests.push(config.url); return taskResponse(config, tasks.page) }
    const { wrapper } = await mountTasks({ tab: 'retry-tasks', taskId: invalid })
    expect(wrapper.text()).toContain('任务标识无效')
    expect(requests).toEqual(['/retry-tasks'])
    wrapper.unmount()
  })
  it('ignores an array-valued retry tab query', async () => {
    const requests = []
    http.defaults.adapter = async config => { requests.push(config.url); return taskResponse(config, tasks.page) }
    const { wrapper } = await mountTasks({ tab: ['retry-tasks'], taskId })
    expect(wrapper.get('#tab-download').attributes('aria-selected')).toBe('true')
    expect(requests).toEqual([])
    wrapper.unmount()
  })
  it('canonicalizes an uppercase task ID with one list and one detail GET', async () => {
    const requests = []
    http.defaults.adapter = async config => {
      requests.push(config.url)
      return taskResponse(config, config.url === '/retry-tasks' ? tasks.page : tasks.detail)
    }
    const { wrapper, router } = await mountTasks({ tab: 'retry-tasks', taskId: taskId.toUpperCase() })
    expect(router.currentRoute.value.query).toEqual({ tab: 'retry-tasks', taskId })
    expect(requests).toEqual(['/retry-tasks', `/retry-tasks/${taskId}`])
    wrapper.unmount()
  })
  it('ignores retry-looking query parameters on a non-download route', async () => {
    const requests = []
    http.defaults.adapter = async config => { requests.push(config.url); return taskResponse(config, tasks.page) }
    api.listDataSources.mockResolvedValue([])
    const router = createAppRouter(createMemoryHistory())
    await router.push({ name: 'datasets', query: { tab: 'retry-tasks', taskId } })
    await router.isReady()
    const wrapper = mount(DownloadView, { attachTo: document.body, global: { plugins: [router] } })
    await flushPromises()
    expect(wrapper.get('#tab-download').attributes('aria-selected')).toBe('true')
    expect(requests).toEqual([])
    wrapper.unmount()
  })
  it('loads selection only from route, preserves loaded tabs, and clears explicit no-selection queries', async () => {
    const requests = []
    http.defaults.adapter = async config => { requests.push(config.url); return taskResponse(config, config.url === '/retry-tasks' ? tasks.page : tasks.detail) }
    const { wrapper, router } = await mountTasks({ tab: 'retry-tasks' })
    expect(wrapper.text()).toContain('选择失败任务查看详情')
    await byButton(wrapper, '查看详情').trigger('click'); await flushPromises()
    expect(requests).toEqual(['/retry-tasks', `/retry-tasks/${taskId}`])
    await wrapper.get('#tab-download').trigger('click'); await flushPromises()
    expect(router.currentRoute.value.query).toEqual({})
    await wrapper.get('#tab-retry-tasks').trigger('click'); await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ tab: 'retry-tasks', taskId })
    expect(requests).toHaveLength(2)
    await router.push({ name: 'downloads', query: { tab: 'retry-tasks' } }); await flushPromises()
    expect(wrapper.get('.retry-task-detail').text()).toContain('选择失败任务查看详情')
    wrapper.unmount()
  })
  it('requires refresh after busy and removes the final task only after explicit success plus list GET', async () => {
    let executeCount = 0
    let resolved = false
    const requests = []
    http.defaults.adapter = async config => {
      requests.push([config.method, config.url])
      if (config.method === 'post') {
        executeCount++
        if (executeCount === 1) {
          const response = taskResponse(config, { code: 'DOWNLOAD_BUSY', message: '已有下载正在执行。', retryable: true, fieldErrors: [] })
          response.status = 409
          throw new AxiosError('busy', 'ERR_BAD_REQUEST', config, null, response)
        }
        resolved = true
        return taskResponse(config, results.SUCCESS)
      }
      return taskResponse(config, config.url === '/retry-tasks' ? resolved ? tasks.empty : tasks.page : tasks.detail)
    }
    const { wrapper } = await mountTasks()
    await byButton(wrapper, '重试一次').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('已有下载正在执行')
    expect(byButton(wrapper, '重试一次').attributes('disabled')).toBeDefined()
    expect(requests).toHaveLength(3)
    await byButton(wrapper, '刷新详情').trigger('click'); await flushPromises()
    expect(byButton(wrapper, '重试一次').attributes('disabled')).toBeUndefined()
    await byButton(wrapper, '重试一次').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('暂无失败任务')
    expect(wrapper.text()).toContain('本轮响应确认当前任务已无剩余失败项')
    expect(wrapper.getComponent(DownloadResult).props('result').taskId).toBeNull()
    expect(requests.map(([method]) => method)).toEqual(['get', 'get', 'post', 'get', 'post', 'get'])
    wrapper.unmount()
  })
  it('guards configuration events and double clicks while retry is pending and consumes only the latest route target', async () => {
    const pending = deferred()
    const requests = []
    http.defaults.adapter = async config => {
      requests.push([config.method, config.url])
      if (config.method === 'post') return taskResponse(config, await pending.promise)
      if (config.url === '/retry-tasks') return taskResponse(config, tasks.page)
      return taskResponse(config, config.url.endsWith(tasks.stocks.taskId) ? tasks.stocks : tasks.detail)
    }
    const router = createAppRouter(createMemoryHistory())
    await router.push('/downloads'); await router.isReady()
    const wrapper = await mountReady({ router })
    await selectApi(wrapper); await setFirstParameter(wrapper, '2026-09-01')
    await router.push({ name: 'downloads', query: { tab: 'retry-tasks', taskId } }); await flushPromises()
    await byButton(wrapper, '重试一次').trigger('click'); await flushPromises()
    await byButton(wrapper, '重试一次').trigger('click')
    wrapper.getComponent(DataSourceSelect).vm.$emit('update:modelValue', 'other_source')
    wrapper.getComponent(ApiSelect).vm.$emit('update:modelValue', 'other_api')
    wrapper.getComponent(DynamicParameterForm).vm.$emit('change')
    wrapper.getComponent(DownloadAction).vm.$emit('submit'); await flushPromises()
    expect(wrapper.getComponent(ApiSelect).props('modelValue')).toBe('daily')
    expect(wrapper.getComponent(DataSourceSelect).props('modelValue')).toBe('fixture')
    expect(wrapper.getComponent(DynamicParameterForm).props('disabled')).toBe(true)
    expect(api.downloadDataset).not.toHaveBeenCalled()
    await router.push({ name: 'downloads', query: { tab: 'retry-tasks', taskId: tasks.stocks.taskId } }); await flushPromises()
    expect(requests).toHaveLength(3)
    expect(wrapper.text()).toContain('区间下载已开始，不可终止，请等待结果。')
    pending.resolve({ ...results.PARTIAL, taskId })
    await flushPromises()
    expect(wrapper.get('.retry-task-detail').text()).toContain(tasks.stocks.taskId)
    expect(wrapper.get('.retry-task-detail').text()).toContain('000001.SZ')
    expect(wrapper.findComponent(DownloadResult).exists()).toBe(false)
    expect(requests.filter(([method]) => method === 'post')).toHaveLength(1)
    wrapper.unmount()
  })
  it('keeps retry read-only during a first download, including direct execute events', async () => {
    const pending = deferred()
    const requests = []
    http.defaults.adapter = async config => { requests.push(config.method); return taskResponse(config, config.url === '/retry-tasks' ? tasks.page : tasks.detail) }
    const router = createAppRouter(createMemoryHistory())
    await router.push('/downloads'); await router.isReady()
    api.downloadDataset.mockReturnValueOnce(pending.promise)
    const wrapper = await mountReady({ router })
    await selectApi(wrapper); await setFirstParameter(wrapper, '2026-09-01'); await clickDownload(wrapper)
    await router.push({ name: 'downloads', query: { tab: 'retry-tasks', taskId } }); await flushPromises()
    expect(wrapper.get('.retry-task-detail').text()).toContain('已有下载正在执行')
    expect(byButton(wrapper, '重试一次').attributes('disabled')).toBeDefined()
    wrapper.findComponent({ name: 'RetryTaskDetail' }).vm.$emit('execute'); await flushPromises()
    await byButton(wrapper, '刷新详情').trigger('click'); await flushPromises()
    expect(requests).toEqual(['get', 'get', 'get'])
    pending.resolve(response()); await flushPromises()
    wrapper.unmount()
  })
  it('rechecks the combined lock after asynchronous initial-form validation', async () => {
    const validation = deferred()
    const retryResponse = deferred()
    const requests = []
    http.defaults.adapter = async config => {
      requests.push(config.method)
      return taskResponse(config, config.method === 'post' ? await retryResponse.promise : config.url === '/retry-tasks' ? tasks.page : tasks.detail)
    }
    const router = createAppRouter(createMemoryHistory())
    await router.push('/downloads'); await router.isReady()
    const wrapper = await mountReady({ router })
    await selectApi(wrapper); await setFirstParameter(wrapper, '2026-09-01')
    const validate = vi.spyOn(wrapper.getComponent(DynamicParameterForm).vm.$.exposed, 'validate').mockReturnValueOnce(validation.promise)
    wrapper.getComponent(DownloadAction).vm.$emit('submit'); await flushPromises()
    expect(validate).toHaveBeenCalledOnce()
    expect(api.downloadDataset).not.toHaveBeenCalled()
    await router.push({ name: 'downloads', query: { tab: 'retry-tasks', taskId } }); await flushPromises()
    await byButton(wrapper, '重试一次').trigger('click'); await flushPromises()
    validation.resolve(true); await flushPromises()
    expect(api.downloadDataset).not.toHaveBeenCalled()
    expect(requests.filter(method => method === 'post')).toHaveLength(1)
    retryResponse.resolve({ ...results.PARTIAL, taskId }); await flushPromises()
    wrapper.unmount()
  })
  it('keeps independent saved-identifier filters separate from the initial download form', async () => {
    const requests = []
    http.defaults.adapter = async config => {
      requests.push(config.params)
      return taskResponse(config, config.url === '/retry-tasks' ? tasks.page : tasks.detail)
    }
    const router = createAppRouter(createMemoryHistory())
    await router.push('/downloads'); await router.isReady()
    const wrapper = await mountReady({ router })
    await selectApi(wrapper); await setFirstParameter(wrapper, '2026-09-01')
    await router.push({ name: 'downloads', query: { tab: 'retry-tasks', taskId } }); await flushPromises()
    await wrapper.get('[name="pluginId"]').setValue('offline_plugin')
    await wrapper.get('.retry-task-list form').trigger('submit'); await flushPromises()
    expect(requests.at(-1)).toEqual({ pluginId: 'offline_plugin', page: 1, pageSize: 20 })
    expect(wrapper.getComponent(DataSourceSelect).props('modelValue')).toBe('fixture')
    expect(wrapper.getComponent(ApiSelect).props('modelValue')).toBe('daily')
    expect(wrapper.getComponent(DynamicParameterForm).findAllComponents(ElDatePicker).map(item => item.props('modelValue'))).toEqual(['2026-09-01', '2026-09-01'])
    expect(wrapper.get('.retry-task-detail').text()).toContain(taskId)
    wrapper.unmount()
  })

})

describe('retry execution result boundary on the real page', () => {
  const adapter = http.defaults.adapter
  afterEach(() => { http.defaults.adapter = adapter })
  it.each(['SUCCESS', 'EMPTY', 'NO_OPEN_DATES', 'PARTIAL', 'FAILED'])('preserves the %s outcome through Axios without deriving historical totals', async outcome => {
    const result = {
      ...structuredClone(results[outcome]),
      ...(outcome === 'PARTIAL' ? { completedUnits: 1, failures: tasks.remaining.items.map(({ updatedAt, ...item }) => item) } : {}),
      ...(outcome === 'FAILED' ? { failures: tasks.detail.items.map(({ updatedAt, ...item }) => item) } : {}),
    }
    if (result.taskId) result.taskId = taskId
    let executed = false
    const requests = []
    http.defaults.adapter = async config => {
      requests.push(config.method)
      if (config.method === 'post') { executed = true; return taskResponse(config, result) }
      if (!executed) return taskResponse(config, config.url === '/retry-tasks' ? tasks.page : tasks.detail)
      if (config.url === '/retry-tasks') return taskResponse(config, result.taskId === null ? tasks.empty : outcome === 'PARTIAL' ? tasks.remainingPage : tasks.page)
      return taskResponse(config, outcome === 'PARTIAL' ? tasks.remaining : tasks.detail)
    }
    const { wrapper } = await mountTasks()
    await byButton(wrapper, '重试一次').trigger('click'); await flushPromises()
    const component = wrapper.getComponent(DownloadResult)
    expect(component.props('state')).toBe(outcome)
    expect(component.props('result')).toMatchObject(result.requestId ? { ...result, requestId: expect.any(String) } : result)
    expect(component.findAll('.download-result__counts > div')).toHaveLength(7)
    const retained = {
      PARTIAL: { counts: [1, 1], selectors: [['REQUEST', '', 'DATE', '2026-09-07']] },
      FAILED: { counts: [0, 2], selectors: [['REQUEST', '', 'DATE', '2026-09-03'], ['REQUEST', '', 'DATE', '2026-09-07']] },
    }[outcome]
    if (retained) {
      expect([result.completedUnits, result.failedUnits]).toEqual(retained.counts)
      expect(result.failures.map(item => [item.targetType, item.targetValue, item.timeType, item.timeValue])).toEqual(retained.selectors)
      const labels = retained.selectors.map(item => `原请求条件 · ${item[3]}`)
      expect(wrapper.get('.retry-task-list').findAll('.retry-task-list__scope').map(item => item.text())).toEqual(labels)
      expect(wrapper.get('.retry-task-detail').findAll('.retry-task-detail__item').map(item => item.get('p').text())).toEqual(labels)
      const summary = wrapper.get('.retry-task-list__items > li')
      expect(Object.fromEntries(summary.findAll('dl > div').map(item => [item.get('dt').text(), item.get('dd').text()]))['当前失败项']).toBe(String(retained.selectors.length))
    }
    expect(wrapper.get('.retry-task-detail').text()).not.toContain('已完成项')
    expect(requests).toEqual(result.taskId ? ['get', 'get', 'post', 'get', 'get'] : ['get', 'get', 'post', 'get'])
    wrapper.unmount()
  })
  it.each([
    ['TASK_RECORD_SAVE_UNCONFIRMED', false, true], ['COMMIT_UNCONFIRMED', false, false],
    ['PERSISTENCE_FAILED', true, true], ['INTERNAL_ERROR', false, false],
  ])('retains %s confirmed subtotals and its actual nullable task ID through Axios', async (code, retryable, hasTaskId) => {
    const requests = []
    http.defaults.adapter = async config => {
      requests.push(config.method)
      if (config.method === 'post') {
        const response = taskResponse(config, { code, message: '本轮结果未确认。', retryable, fieldErrors: [], downloadResult: { ...results.UNCONFIRMED, taskId: hasTaskId ? taskId : null, requestId: config.headers.get('X-Request-Id') } })
        response.status = 500
        throw new AxiosError('raw secret', 'ERR_BAD_RESPONSE', config, null, response)
      }
      return taskResponse(config, config.url === '/retry-tasks' ? tasks.page : tasks.detail)
    }
    const { wrapper } = await mountTasks()
    await byButton(wrapper, '重试一次').trigger('click'); await flushPromises()
    const component = wrapper.getComponent(DownloadResult)
    expect(component.props('state')).toBe('UNCONFIRMED')
    expect(component.props('error')).toMatchObject({ code, retryable })
    expect(component.props('result')).toMatchObject({ taskId: hasTaskId ? taskId : null, notStartedUnits: null, remainingFailedUnits: null, sourceRowCount: 10, insertedRows: 7, updatedRows: 3 })
    expect(component.text()).toContain('本轮已确认小计')
    expect(component.text()).toContain('再次手动重试只处理服务端当时仍保存的明细')
    expect(component.text()).not.toContain('raw secret')
    expect(component.findAll('button')).toHaveLength(hasTaskId ? 1 : 0)
    expect(byButton(wrapper, '重试一次').attributes('disabled')).toBeDefined()
    expect(requests).toEqual(['get', 'get', 'post'])
    wrapper.unmount()
  })
  it.each([['ECONNABORTED', 'TIMEOUT'], ['ERR_NETWORK', 'NETWORK'], ['ERR_BAD_RESPONSE', 'INVALID_RESPONSE'], ['ERR_OTHER', 'UNEXPECTED']])('keeps %s unknown until an explicit detail GET', async (code, kind) => {
    const requests = []
    http.defaults.adapter = async config => {
      requests.push(config.method)
      if (config.method === 'post') {
        const response = kind === 'INVALID_RESPONSE' ? { ...taskResponse(config, { sentinel: 'raw secret' }), status: 500 } : undefined
        throw new AxiosError('raw secret', code, config, null, response)
      }
      return taskResponse(config, config.url === '/retry-tasks' ? tasks.page : tasks.detail)
    }
    const { wrapper } = await mountTasks()
    await byButton(wrapper, '重试一次').trigger('click'); await flushPromises()
    const component = wrapper.getComponent(DownloadResult)
    expect(component.props()).toMatchObject({ state: 'UNCONFIRMED', result: null, error: { kind } })
    expect(component.find('dl').exists()).toBe(false)
    expect(component.text()).toContain('服务端可能仍在执行')
    expect(requests).toEqual(['get', 'get', 'post'])
    await byButton(wrapper, '刷新详情').trigger('click'); await flushPromises()
    expect(byButton(wrapper, '重试一次').attributes('disabled')).toBeUndefined()
    expect(component.props('state')).toBe('UNCONFIRMED')
    expect(requests).toEqual(['get', 'get', 'post', 'get'])
    wrapper.unmount()
  })
})
