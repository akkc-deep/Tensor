import { flushPromises, mount as mountComponent } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ElButton, ElRadioGroup } from 'element-plus'
import { nextTick } from 'vue'

import { ApiError, ClientError } from '../api/errors.js'
import { PENDING_SUBMISSION_KEY } from '../utils/downloadTaskSubmission.js'
import AsyncStatePanel from '../components/common/AsyncStatePanel.vue'
import WorkbenchPanel from '../components/common/WorkbenchPanel.vue'
import ApiSelect from '../components/download/ApiSelect.vue'
import DataSourceSelect from '../components/download/DataSourceSelect.vue'
import DownloadAction from '../components/download/DownloadAction.vue'
import DownloadResult from '../components/download/DownloadResult.vue'
import DownloadTaskList from '../components/download/DownloadTaskList.vue'
import DynamicParameterForm from '../components/download/DynamicParameterForm.vue'
import MetadataField from '../components/common/MetadataField.vue'
import DownloadView from './DownloadView.vue'

const api = vi.hoisted(() => ({
  listDataSources: vi.fn(),
  listApis: vi.fn(),
  getDownloadCapabilities: vi.fn(),
  submitDownloadTask: vi.fn(),
  listDownloadTasks: vi.fn(),
}))

vi.mock('../api/dataSources.js', () => ({
  listDataSources: api.listDataSources,
  listApis: api.listApis,
}))
vi.mock('../api/downloadTasks.js', () => ({
  getDownloadCapabilities: api.getDownloadCapabilities,
  submitDownloadTask: api.submitDownloadTask,
  listDownloadTasks: api.listDownloadTasks,
}))

const SUBMISSION_ID = '33333333-3333-4333-8333-333333333333'
const TASK_ID = '22222222-2222-4222-8222-222222222222'

function mount(component, options = {}) {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/:pathMatch(.*)*', component: { template: '<div />' } },
  ] })
  return mountComponent(component, { ...options, global: { plugins: [router] } })
}

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
    pluginId: 'contract_fixture',
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

function capabilities(overrides = {}) {
  return {
    single: {
      available: true,
      parameters: [
        parameter({ name: 'ts_code', label: '股票代码', type: 'TS_CODE' }),
        parameter(),
      ],
    },
    range: {
      availability: 'AVAILABLE',
      unavailableReason: null,
      dateAxis: 'TRADE_DATE',
      dateLabel: '交易日期',
      startParameter: 'start_date',
      endParameter: 'end_date',
      parameters: [
        parameter({ name: 'ts_code', label: '股票代码', type: 'TS_CODE' }),
        parameter({ name: 'start_date', label: '开始日期', type: 'DATE_RANGE_MEMBER', relatedParameter: 'end_date' }),
        parameter({ name: 'end_date', label: '结束日期', type: 'DATE_RANGE_MEMBER', relatedParameter: 'start_date' }),
      ],
      planningMode: 'TRADING_DAYS',
      splittable: false,
      policyVersion: 'fixture-v1',
      completenessRule: { kind: 'VERIFIED_RULE', rowLimit: null, evidence: 'fixture' },
    },
    ...overrides,
  }
}

function receipt(overrides = {}) {
  return Object.freeze({
    requestId: '11111111-1111-4111-8111-111111111111',
    taskId: TASK_ID,
    status: 'QUEUED',
    version: 1n,
    createdAt: '2026-09-12T00:00:00Z',
    ...overrides,
  })
}

function emptyPage(overrides = {}) {
  return Object.freeze({ page: 1, pageSize: 20, total: 0n, items: Object.freeze([]), ...overrides })
}

function recoveredTask() {
  return Object.freeze({
    taskId: TASK_ID,
    submissionId: SUBMISSION_ID,
    pluginId: 'removed_source',
    apiName: 'removed_api',
    mode: 'SINGLE',
    params: Object.freeze({ trade_date: '20260912' }),
    status: 'QUEUED',
    version: 1n,
    planReady: false,
    counts: Object.freeze({
      totalBatches: 0n,
      pendingBatches: 0n,
      runningBatches: 0n,
      succeededBatches: 0n,
      failedBatches: 0n,
      splitBatches: 0n,
      sourceRows: 0n,
      insertedRows: 0n,
      updatedRows: 0n,
    }),
    lastError: null,
    canRetry: false,
    canResume: false,
    requestCount: 0n,
    runRequestCount: 0n,
    createdAt: '2026-09-12T00:00:00Z',
    updatedAt: '2026-09-12T00:00:00Z',
    queuedAt: '2026-09-12T00:00:00Z',
    startedAt: null,
    finishedAt: null,
    deadlineAt: '2026-09-12T01:00:00Z',
  })
}

function currentApis() {
  const categories = [
    ['basic_organization', 7], ['行情与估值', 7], ['交易与资金', 5],
    ['互联互通与转融通', 3], ['财务与披露', 9], ['公司行动', 2], ['股东与治理', 7],
  ]
  let index = 0
  return categories.flatMap(([category, count]) => Array.from({ length: count }, () => {
    index += 1
    return descriptor({
      apiName: index === 1 ? 'daily' : `api_${index}`,
      displayName: index === 1 ? '日线行情' : `接口 ${index}`,
      category,
    })
  }))
}

const wrappers = []
let visibility = 'visible'

async function mountView({ sources = [source()], apis = [descriptor()], attachTo } = {}) {
  api.listDataSources.mockResolvedValueOnce(sources)
  api.listApis.mockResolvedValueOnce(apis)
  const wrapper = mount(DownloadView, { attachTo })
  wrappers.push(wrapper)
  await flushPromises()
  await flushPromises()
  return wrapper
}

async function selectApi(wrapper, apiName = 'daily') {
  wrapper.getComponent(ApiSelect).vm.$emit('update:modelValue', apiName)
  await flushPromises()
}

async function setParameter(wrapper, name, value) {
  const field = wrapper.getComponent(DynamicParameterForm).findAllComponents(MetadataField)
    .find((candidate) => candidate.attributes('data-parameter') === name)
  field.vm.$emit('update:modelValue', value)
  await nextTick()
}

async function submit(wrapper) {
  await wrapper.getComponent(DownloadAction).get('button').trigger('click')
  await flushPromises()
}

function expectBefore(first, second) {
  expect(first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
}

beforeEach(() => {
  vi.useFakeTimers()
  visibility = 'visible'
  vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibility)
  vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue(SUBMISSION_ID)
  sessionStorage.clear()
  api.listDownloadTasks.mockResolvedValue(emptyPage())
  api.getDownloadCapabilities.mockResolvedValue(capabilities())
})

afterEach(() => {
  while (wrappers.length) wrappers.pop().unmount()
  sessionStorage.clear()
  vi.clearAllTimers()
  vi.useRealTimers()
})

describe('DownloadView', () => {
  it('recovers first, then starts metadata and recent-list requests independently', async () => {
    const recovery = deferred()
    const metadata = deferred()
    const recent = deferred()
    sessionStorage.setItem(PENDING_SUBMISSION_KEY, JSON.stringify({
      schemaVersion: 1,
      request: {
        submissionId: SUBMISSION_ID,
        pluginId: 'removed_source',
        apiName: 'removed_api',
        mode: 'SINGLE',
        params: { trade_date: '20260912' },
      },
    }))
    api.listDownloadTasks.mockImplementation((criteria) =>
      criteria.submissionId ? recovery.promise : recent.promise,
    )
    api.listDataSources.mockReturnValueOnce(metadata.promise)
    const wrapper = mount(DownloadView)
    wrappers.push(wrapper)
    await nextTick()

    expect(api.listDownloadTasks).toHaveBeenCalledWith({ submissionId: SUBMISSION_ID })
    expect(api.listDataSources).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('正在找回原任务')

    recovery.resolve(emptyPage())
    await flushPromises()
    expect(api.listDataSources).toHaveBeenCalledOnce()
    expect(api.listDownloadTasks).toHaveBeenCalledWith({ page: 1, pageSize: 20 })
    expect(wrapper.getComponent(DownloadTaskList).props('loading')).toBe(true)

    recent.resolve(emptyPage())
    metadata.resolve([])
    await flushPromises()
    expect(wrapper.getComponent(DownloadTaskList).props('result')).toEqual(emptyPage())
  })

  it('loads one source and all 40 descriptors, with safe metadata retry', async () => {
    const apis = currentApis()
    const wrapper = await mountView({ apis })
    expect(api.listApis).toHaveBeenCalledWith('contract_fixture')
    expect(wrapper.getComponent(DataSourceSelect).props('sources')).toEqual([source()])
    expect(wrapper.getComponent(ApiSelect).props('apis')).toEqual(apis)
    expect(wrapper.getComponent(DownloadAction).props('disabled')).toBe(true)
    expect(wrapper.findAllComponents(WorkbenchPanel).map((panel) => panel.props('title'))).toEqual([
      '下载配置', '任务接收', '近期任务',
    ])

    const failure = new ClientError('NETWORK', 'capability-request')
    api.getDownloadCapabilities.mockRejectedValueOnce(failure)
    await selectApi(wrapper)
    expect(wrapper.text()).toContain('下载配置加载失败')
    expect(wrapper.text()).toContain('请求 ID：capability-request')

    api.getDownloadCapabilities.mockResolvedValueOnce(capabilities())
    await wrapper.findAllComponents(ElButton)
      .find((button) => button.text() === '重新加载配置').get('button').trigger('click')
    await flushPromises()
    expect(wrapper.getComponent(DynamicParameterForm).exists()).toBe(true)
  })

  it('uses capability modes and remounts a clean parameter form on every switch', async () => {
    const wrapper = await mountView()
    await selectApi(wrapper)
    const radios = wrapper.getComponent(ElRadioGroup)
    expect(radios.props('modelValue')).toBe('RANGE')
    expect(wrapper.text()).toContain('交易日期')
    expect(wrapper.getComponent(DynamicParameterForm).props('parameters').map(({ name }) => name)).toEqual([
      'ts_code', 'start_date', 'end_date',
    ])
    await setParameter(wrapper, 'ts_code', '000001.SZ')
    radios.vm.$emit('update:modelValue', 'SINGLE')
    await nextTick()
    expect(wrapper.getComponent(DynamicParameterForm).props('parameters').map(({ name }) => name)).toEqual([
      'ts_code', 'trade_date',
    ])
    expect(wrapper.getComponent(DynamicParameterForm).get('input').element.value).toBe('')
    expect(wrapper.text()).toContain('单次请求，结果不代表完整历史')
  })

  it('keeps unavailable range visible with its reason and blocks unavailable single mode', async () => {
    api.getDownloadCapabilities.mockResolvedValueOnce(capabilities({
      single: { available: false, parameters: [] },
      range: {
        ...capabilities().range,
        availability: 'NEEDS_VERIFICATION',
        unavailableReason: '范围完整性尚待验证',
        dateAxis: null,
        dateLabel: null,
        startParameter: null,
        endParameter: null,
        parameters: [],
        planningMode: null,
        policyVersion: null,
        completenessRule: null,
      },
    }))
    const wrapper = await mountView()
    await selectApi(wrapper)
    expect(wrapper.getComponent(ElRadioGroup).props('modelValue')).toBe('SINGLE')
    expect(wrapper.text()).toContain('范围完整性尚待验证')
    expect(wrapper.getComponent(DownloadAction).props('disabled')).toBe(true)
  })

  it('blocks an invalid form, focuses its first field, and preserves keyboard order', async () => {
    const wrapper = await mountView({ attachTo: document.body })
    try {
      await selectApi(wrapper)
      const sourceInput = wrapper.getComponent(DataSourceSelect).get('input[role="combobox"]')
      const apiInput = wrapper.getComponent(ApiSelect).get('input[role="combobox"]')
      const firstParameter = wrapper.getComponent(DynamicParameterForm)
        .get('[data-parameter="ts_code"] input')
      const button = wrapper.getComponent(DownloadAction).get('button')
      await submit(wrapper)
      expect(api.submitDownloadTask).not.toHaveBeenCalled()
      expect(wrapper.findAll('.field-error')).toHaveLength(3)
      expect(document.activeElement).toBe(firstParameter.element)
      for (const control of [sourceInput, apiInput, firstParameter, button]) {
        control.element.focus()
        expect(document.activeElement).toBe(control.element)
      }
      expectBefore(sourceInput.element, apiInput.element)
      expectBefore(apiInput.element, firstParameter.element)
      expectBefore(firstParameter.element, button.element)
      expect(button.attributes('type')).toBe('button')
    } finally {
      wrapper.unmount()
      wrappers.splice(wrappers.indexOf(wrapper), 1)
    }
  })

  it('submits one normalized RANGE snapshot and presents 202 as accepted identity only', async () => {
    api.submitDownloadTask.mockResolvedValueOnce(receipt())
    const wrapper = await mountView()
    await selectApi(wrapper)
    await setParameter(wrapper, 'ts_code', '000001.sz')
    await setParameter(wrapper, 'start_date', '2026-09-01')
    await setParameter(wrapper, 'end_date', '2026-09-02')
    await submit(wrapper)

    expect(api.submitDownloadTask).toHaveBeenCalledOnce()
    expect(api.submitDownloadTask.mock.calls[0][0]).toEqual({
      submissionId: SUBMISSION_ID,
      pluginId: 'contract_fixture',
      apiName: 'daily',
      mode: 'RANGE',
      params: { ts_code: '000001.SZ', start_date: '20260901', end_date: '20260902' },
    })
    expect(wrapper.text()).toContain('任务已接收')
    expect(wrapper.text()).toContain(TASK_ID)
    expect(wrapper.get('.download-feedback a').attributes('href')).toBe(`/downloads/tasks/${TASK_ID}`)
    expect(wrapper.text()).toContain('接收后可继续提交其他任务')
    expect(wrapper.text()).not.toMatch(/下载成功|插入数|更新数|EMPTY/)
    expect(wrapper.findComponent(DownloadResult).exists()).toBe(false)
    expect(wrapper.getComponent(DownloadAction).props('submitting')).toBe(false)
    expect(api.listDownloadTasks.mock.calls.filter(([criteria]) => criteria.page === 1)).toHaveLength(2)
  })

  it('keeps range validation and submits a parameterless SINGLE task as an empty object', async () => {
    const wrapper = await mountView()
    await selectApi(wrapper)
    await setParameter(wrapper, 'ts_code', '000001.SZ')
    await setParameter(wrapper, 'start_date', '2026-09-07')
    await setParameter(wrapper, 'end_date', '2026-09-03')
    await submit(wrapper)
    expect(api.submitDownloadTask).not.toHaveBeenCalled()

    api.getDownloadCapabilities.mockResolvedValueOnce({
      single: { available: true, parameters: [] },
      range: {
        availability: 'UNSUPPORTED', unavailableReason: '仅支持单次请求',
        dateAxis: null, dateLabel: null, startParameter: null, endParameter: null,
        parameters: [], planningMode: null, splittable: false, policyVersion: null,
        completenessRule: null,
      },
    })
    api.submitDownloadTask.mockResolvedValueOnce(receipt())
    await selectApi(wrapper)
    expect(wrapper.text()).toContain('此模式无需填写请求参数')
    await submit(wrapper)
    expect(api.submitDownloadTask.mock.calls[0][0].params).toEqual({})
  })

  it('keeps an immutable pending summary and reuses its identity for recovery and replay', async () => {
    api.submitDownloadTask.mockRejectedValueOnce(new ClientError('NETWORK', 'submit-request'))
    const wrapper = await mountView()
    await selectApi(wrapper)
    await setParameter(wrapper, 'ts_code', '000001.SZ')
    await setParameter(wrapper, 'start_date', '2026-09-01')
    await setParameter(wrapper, 'end_date', '2026-09-02')
    await submit(wrapper)
    expect(wrapper.text()).toContain('提交结果尚未确认')
    expect(wrapper.text()).toContain('start_date=20260901')
    await setParameter(wrapper, 'start_date', '2026-09-09')
    expect(wrapper.text()).toContain('start_date=20260901')
    expect(wrapper.text()).not.toContain('start_date=20260909')

    api.listDownloadTasks.mockResolvedValueOnce(emptyPage())
    await wrapper.findAllComponents(ElButton)
      .find((button) => button.text() === '重新查找').get('button').trigger('click')
    await flushPromises()
    expect(api.listDownloadTasks).toHaveBeenCalledWith({ submissionId: SUBMISSION_ID })

    api.submitDownloadTask.mockResolvedValueOnce(receipt())
    await wrapper.findAllComponents(ElButton)
      .find((button) => button.text() === '使用原参数重新确认').get('button').trigger('click')
    await flushPromises()
    expect(api.submitDownloadTask.mock.calls[1][0]).toEqual(api.submitDownloadTask.mock.calls[0][0])
  })

  it('shows safe rejected fields and does not let a later storage failure hide the problem', async () => {
    const rejected = new ApiError({
      requestId: '11111111-1111-4111-8111-111111111111',
      code: 'PARAM_INVALID',
      message: '请求参数无效',
      retryable: false,
      fieldErrors: [{ field: 'start_date', message: '开始日期不可用' }],
    })
    api.submitDownloadTask.mockRejectedValueOnce(rejected)
    const wrapper = await mountView()
    await selectApi(wrapper)
    await setParameter(wrapper, 'ts_code', '000001.SZ')
    await setParameter(wrapper, 'start_date', '2026-09-01')
    await setParameter(wrapper, 'end_date', '2026-09-02')
    await submit(wrapper)

    expect(wrapper.text()).toContain('任务未接收')
    expect(wrapper.text()).toContain('start_date：开始日期不可用')

    api.submitDownloadTask.mockResolvedValueOnce(receipt())
    await submit(wrapper)
    expect(wrapper.text()).toContain('任务已接收')

    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('blocked')
    })
    await submit(wrapper)
    expect(wrapper.text()).toContain('无法保存提交记录，暂不能提交。')
    expect(wrapper.text()).not.toContain('服务已确认任务身份，进度以近期任务或详情为准。')
  })

  it('explains failed recovery and corrupt-record cleanup without implying server cancellation', async () => {
    sessionStorage.setItem(PENDING_SUBMISSION_KEY, JSON.stringify({
      schemaVersion: 1,
      request: {
        submissionId: SUBMISSION_ID, pluginId: 'removed_source', apiName: 'removed_api',
        mode: 'SINGLE', params: { trade_date: '20260912' },
      },
    }))
    api.listDownloadTasks.mockRejectedValueOnce(new ClientError('NETWORK', 'recover-request'))
    const failed = await mountView({ sources: [] })
    expect(failed.text()).toContain('状态暂时无法更新')
    expect(failed.text()).toContain('重新查找')
    failed.unmount()
    wrappers.splice(wrappers.indexOf(failed), 1)

    sessionStorage.setItem(PENDING_SUBMISSION_KEY, '{broken')
    const corrupt = await mountView({ sources: [] })
    expect(corrupt.text()).toContain('仅清除此标签页记录，不会取消服务端任务')
  })

  it('explains the browser precision requirement for an uncertain task response', async () => {
    const nativeParse = JSON.parse
    vi.spyOn(JSON, 'parse').mockImplementation((text, reviver) => {
      if (text === '{"n":1}' && reviver) return reviver('n', 1, undefined)
      return nativeParse(text, reviver)
    })
    api.submitDownloadTask.mockRejectedValueOnce(
      new ClientError('INVALID_RESPONSE', 'submit-request'),
    )
    const wrapper = await mountView()
    await selectApi(wrapper)
    await setParameter(wrapper, 'ts_code', '000001.SZ')
    await setParameter(wrapper, 'start_date', '2026-09-01')
    await setParameter(wrapper, 'end_date', '2026-09-02')
    await submit(wrapper)

    expect(wrapper.text()).toContain('当前浏览器无法保真处理任务数值，请更新浏览器后重试。')
  })

  it('recovers a task removed from current metadata and links its real identity', async () => {
    sessionStorage.setItem(PENDING_SUBMISSION_KEY, JSON.stringify({
      schemaVersion: 1,
      request: {
        submissionId: SUBMISSION_ID, pluginId: 'removed_source', apiName: 'removed_api',
        mode: 'SINGLE', params: { trade_date: '20260912' },
      },
    }))
    api.listDownloadTasks.mockImplementation((criteria) => Promise.resolve(
      criteria.submissionId
        ? emptyPage({ total: 1n, items: Object.freeze([recoveredTask()]) })
        : emptyPage(),
    ))
    const wrapper = await mountView({ sources: [] })
    expect(wrapper.text()).toContain('任务已接收')
    expect(wrapper.text()).toContain('removed_source / removed_api')
    expect(wrapper.get('.download-feedback a').attributes('href')).toBe(`/downloads/tasks/${TASK_ID}`)
    expect(sessionStorage.getItem(PENDING_SUBMISSION_KEY)).toBeNull()
  })

  it('does not continue metadata or list startup after unmount during recovery', async () => {
    const recovery = deferred()
    sessionStorage.setItem(PENDING_SUBMISSION_KEY, JSON.stringify({
      schemaVersion: 1,
      request: {
        submissionId: SUBMISSION_ID, pluginId: 'removed_source', apiName: 'removed_api',
        mode: 'SINGLE', params: { trade_date: '20260912' },
      },
    }))
    api.listDownloadTasks.mockReturnValueOnce(recovery.promise)
    const wrapper = mount(DownloadView)
    await nextTick()
    wrapper.unmount()
    recovery.resolve(emptyPage())
    await flushPromises()
    expect(api.listDataSources).not.toHaveBeenCalled()
    expect(api.listDownloadTasks).toHaveBeenCalledOnce()
    await vi.advanceTimersByTimeAsync(30_000)
    expect(api.listDownloadTasks).toHaveBeenCalledOnce()
  })
})
