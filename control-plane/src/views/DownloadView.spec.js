import { flushPromises, mount as mountComponent } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ElButton } from 'element-plus'
import { nextTick } from 'vue'

import { ApiError, ClientError } from '../api/errors.js'
import { PENDING_SUBMISSION_KEY } from '../utils/downloadTaskSubmission.js'
import AsyncStatePanel from '../components/common/AsyncStatePanel.vue'
import ApiSelect from '../components/download/ApiSelect.vue'
import DataSourceSelect from '../components/download/DataSourceSelect.vue'
import DownloadAction from '../components/download/DownloadAction.vue'
import DownloadTaskList from '../components/download/DownloadTaskList.vue'
import DynamicParameterForm from '../components/download/DynamicParameterForm.vue'
import DownloadView from './DownloadView.vue'

const api = vi.hoisted(() => ({
  listDataSources: vi.fn(),
  listApis: vi.fn(),
  getDownloadCapabilities: vi.fn(),
  submitDownloadTask: vi.fn(),
  listDownloadTasks: vi.fn(),
  getDownloadTask: vi.fn(),
  retryDownloadTask: vi.fn(),
  resumeDownloadTask: vi.fn(),
}))

vi.mock('../api/dataSources.js', () => ({
  listDataSources: api.listDataSources,
  listApis: api.listApis,
}))
vi.mock('../api/downloadTasks.js', () => ({
  getDownloadCapabilities: api.getDownloadCapabilities,
  submitDownloadTask: api.submitDownloadTask,
  listDownloadTasks: api.listDownloadTasks,
  getDownloadTask: api.getDownloadTask,
  retryDownloadTask: api.retryDownloadTask,
  resumeDownloadTask: api.resumeDownloadTask,
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
    extraction: null,
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
  if (sources.length) api.listApis.mockResolvedValueOnce(apis)
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
  await wrapper.getComponent(DynamicParameterForm).get(`[data-parameter="${name}"] input, [data-parameter="${name}"] select`).setValue(value)
}

async function submit(wrapper) {
  await wrapper.getComponent(DownloadAction).get('button').trigger('click')
  await flushPromises()
}

function expectBefore(first, second) {
  expect(first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
}

beforeEach(() => {
  vi.resetAllMocks()
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
  it('preserves input when the selected Studio mode is clicked again', async () => {
    const wrapper = await mountView()
    await selectApi(wrapper)
    const button = wrapper.get('[data-mode="RANGE"]')
    expect(button.attributes('aria-pressed')).toBe('true')
    await setParameter(wrapper, 'ts_code', '000001.SZ')
    await button.trigger('click')
    expect(wrapper.get('[data-parameter="ts_code"] input').element.value).toBe('000001.SZ')
  })

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
    expect(wrapper.get('.pending-submission').text()).toContain('trade_date=20260912')
    expect(wrapper.getComponent(DownloadAction).text()).toBe('正在查找…')
    expect(wrapper.get('.catalog-panel').text()).not.toContain('暂无数据源')

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
    expect(wrapper.findComponent(DownloadAction).exists()).toBe(false)
    expect(wrapper.get('.download-empty').text()).toContain('选择接口，开始下载')
    expect(wrapper.getComponent(DownloadTaskList).get('h2').text()).toContain('最近任务')
    expect(wrapper.find('.download-feedback').exists()).toBe(false)

    const failure = new ClientError('NETWORK', 'capability-request')
    api.getDownloadCapabilities.mockRejectedValueOnce(failure)
    await selectApi(wrapper)
    expect(wrapper.get('.studio-form').text()).toContain('接口能力加载失败')
    expect(wrapper.text()).toContain('请求 ID：capability-request')

    api.getDownloadCapabilities.mockResolvedValueOnce(capabilities())
    await wrapper.findAllComponents(ElButton)
      .find((button) => button.text() === '重新加载能力').get('button').trigger('click')
    await flushPromises()
    expect(wrapper.getComponent(DynamicParameterForm).exists()).toBe(true)
  })

  it('updates the heading immediately, shows capability loading, and ignores a late failure', async () => {
    const pending = deferred()
    api.getDownloadCapabilities.mockReturnValueOnce(pending.promise)
    const wrapper = await mountView({ apis: [descriptor(), descriptor({ apiName: 'weekly', displayName: '周线行情' })] })
    await wrapper.get('.catalog-list button').trigger('click')
    expect(wrapper.get('.selected-api-heading').text()).toContain('日线行情')
    expect(wrapper.get('.studio-form').text()).toContain('正在加载接口能力')
    expect(wrapper.findComponent(DynamicParameterForm).exists()).toBe(false)
    await wrapper.findAll('.catalog-list button')[1].trigger('click')
    await flushPromises()
    pending.reject(new ClientError('NETWORK', 'old-capability'))
    await flushPromises()
    expect(wrapper.get('.selected-api-heading').text()).toContain('周线行情')
    expect(wrapper.findComponent(DynamicParameterForm).exists()).toBe(true)
    expect(wrapper.text()).not.toContain('old-capability')
    expect(wrapper.getComponent(DownloadAction).props('disabled')).toBe(false)
  })

  it('keeps parameters when filtering or reselecting and resets filters on source changes', async () => {
    const wrapper = await mountView({ sources: [source(), source({ pluginId: 'other', displayName: 'Other' })] })
    expect(wrapper.text()).toContain('请选择数据源')
    wrapper.getComponent(DataSourceSelect).vm.$emit('update:modelValue', 'contract_fixture')
    await flushPromises()
    await wrapper.get('.catalog-list button').trigger('click')
    await flushPromises()
    await setParameter(wrapper, 'ts_code', '000001.SZ')
    await wrapper.get('.catalog-list button').trigger('click')
    await wrapper.get('[aria-label="搜索接口"]').setValue('missing')
    expect(wrapper.getComponent(DynamicParameterForm).get('input').element.value).toBe('000001.SZ')
    const loading = deferred()
    api.listApis.mockReturnValueOnce(loading.promise)
    wrapper.getComponent(DataSourceSelect).vm.$emit('update:modelValue', 'other')
    await nextTick()
    expect(wrapper.get('.catalog-panel').text()).toContain('正在加载接口目录')
    expect(wrapper.find('.selected-api-heading').exists()).toBe(false)
    expect(wrapper.findComponent(DynamicParameterForm).exists()).toBe(false)
    loading.resolve([descriptor({ apiName: 'other_api', displayName: '其他接口', category: '其他' })])
    await flushPromises()
    expect(wrapper.get('[aria-label="搜索接口"]').element.value).toBe('')
    expect(wrapper.get('[aria-label="接口分类"]').element.value).toBe('')
    expect(wrapper.get('.catalog-list').text()).toContain('其他接口')
  })

  it('separates empty sources, unavailable sources and empty catalogs', async () => {
    const empty = await mountView({ sources: [] })
    expect(empty.get('.catalog-panel').text()).toContain('暂无数据源')
    const unavailable = await mountView({ sources: [source({ downloadAvailable: false, unavailableReason: '尚未配置凭证' })] })
    expect(unavailable.get('.catalog-panel').text()).toContain('尚未配置凭证')
    expect(unavailable.get('.catalog-list button').element.disabled).toBe(true)
    expect(unavailable.findComponent(DownloadAction).exists()).toBe(false)
    const noApis = await mountView({ apis: [] })
    expect(noApis.get('.catalog-panel').text()).toContain('此数据源暂无接口')
  })

  it('retries a failed catalog without presenting it as empty or losing its source', async () => {
    api.listDataSources.mockResolvedValueOnce([source()])
    api.listApis.mockRejectedValueOnce(new ClientError('NETWORK', 'catalog-request'))
    const wrapper = mount(DownloadView)
    wrappers.push(wrapper)
    await flushPromises()
    expect(wrapper.get('.catalog-panel').text()).toContain('接口目录加载失败')
    expect(wrapper.get('.catalog-panel').text()).toContain('catalog-request')
    expect(wrapper.text()).not.toContain('此数据源暂无接口')
    api.listApis.mockResolvedValueOnce([descriptor()])
    await wrapper.findAllComponents(ElButton).find(button => button.text() === '重新加载目录').trigger('click')
    await flushPromises()
    expect(wrapper.get('.catalog-list').text()).toContain('日线行情')
  })

  it('uses capability modes and remounts a clean parameter form on every switch', async () => {
    const wrapper = await mountView()
    await selectApi(wrapper)
    const rangeButton = wrapper.get('[data-mode="RANGE"]')
    expect(rangeButton.attributes('aria-pressed')).toBe('true')
    expect(wrapper.text()).toContain('交易日期')
    expect(wrapper.getComponent(DynamicParameterForm).props('parameters').map(({ name }) => name)).toEqual([
      'ts_code', 'start_date', 'end_date',
    ])
    await setParameter(wrapper, 'ts_code', '000001.SZ')
    await wrapper.get('[data-mode="SINGLE"]').trigger('click')
    await nextTick()
    expect(wrapper.getComponent(DynamicParameterForm).props('parameters').map(({ name }) => name)).toEqual([
      'ts_code', 'trade_date',
    ])
    expect(wrapper.getComponent(DynamicParameterForm).get('input').element.value).toBe('')
    expect(wrapper.text()).toContain('单次请求，结果不代表完整历史')
  })

  it.each([
    ['VERIFIED_RULE', null, false, '采用已验证的完整性规则'],
    ['CONFIRMED_ROW_LIMIT', 9223372036854775807n, true, '单次返回上限为 9223372036854775807 行。达到上限时按规则拆分日期区间。'],
    ['CONFIRMED_ROW_LIMIT', 6000n, false, '单次返回上限为 6000 行。完整性以任务执行结果为准。'],
  ])('explains %s capability without promising completion or previewing batches', async (kind, rowLimit, splittable, text) => {
    api.getDownloadCapabilities.mockResolvedValueOnce(capabilities({ range: {
      ...capabilities().range, planningMode: 'NATIVE_RANGE', splittable,
      completenessRule: { kind, rowLimit, evidence: '服务端规则' },
    } }))
    const wrapper = await mountView()
    await selectApi(wrapper)
    expect(wrapper.get('.completeness-note').text()).toContain(text)
    expect(wrapper.get('.form-parameters').text()).not.toContain('批次预览')
    await wrapper.get('[data-mode="SINGLE"]').trigger('click')
    expect(wrapper.get('.completeness-note').text()).toBe('单次请求，结果不代表完整历史。')
  })

  it('explains response-only before submission and removes the caveat for SINGLE', async () => {
    api.getDownloadCapabilities.mockResolvedValueOnce(capabilities({ range: {
      ...capabilities().range, planningMode: 'NATIVE_RANGE', splittable: false,
      completenessRule: { kind: 'RESPONSE_ONLY', rowLimit: null, evidence: '受控采集合同' },
    } }))
    const wrapper = await mountView()
    await selectApi(wrapper)
    expect(wrapper.get('.completeness-note').text()).toContain('按所选日期区间采集本次接口返回的记录。数据完整性未确认，可能存在上游截断。')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(wrapper.find('input[type="checkbox"]').exists()).toBe(false)
    await wrapper.get('[data-mode="SINGLE"]').trigger('click')
    await nextTick()
    expect(wrapper.text()).not.toContain('可能存在上游截断')
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
    expect(wrapper.get('[data-mode="SINGLE"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-mode="RANGE"]').element.disabled).toBe(true)
    expect(wrapper.text()).toContain('范围完整性尚待验证')
    expect(wrapper.getComponent(DownloadAction).props('disabled')).toBe(true)
  })

  it('blocks an invalid form, focuses its first field, and preserves keyboard order', async () => {
    const wrapper = await mountView({ attachTo: document.body })
    try {
      await selectApi(wrapper)
      const sourceInput = wrapper.getComponent(DataSourceSelect).get('input[role="combobox"]')
      const apiInput = wrapper.getComponent(ApiSelect).get('[aria-label="搜索接口"]')
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
    expect(wrapper.getComponent(DownloadAction).props('submitting')).toBe(false)
    expect(api.listDownloadTasks.mock.calls.filter(([criteria]) => criteria.page === 1)).toHaveLength(2)
  })

  it('locks every request input during a slow POST and displays the frozen request only once', async () => {
    const pending = deferred()
    api.submitDownloadTask.mockReturnValueOnce(pending.promise)
    const wrapper = await mountView()
    await selectApi(wrapper)
    await setParameter(wrapper, 'ts_code', ' 000001.sz ')
    await setParameter(wrapper, 'start_date', '2026-09-01')
    await setParameter(wrapper, 'end_date', '2026-09-02')
    const button = wrapper.getComponent(DownloadAction).get('button')
    await Promise.all([button.trigger('click'), button.trigger('click')])
    await flushPromises()

    expect(api.submitDownloadTask).toHaveBeenCalledOnce()
    expect(wrapper.get('.pending-submission').text()).toContain('ts_code=000001.SZ')
    expect(wrapper.get('.pending-submission').text()).toContain(SUBMISSION_ID)
    expect(wrapper.get('.download-feedback').attributes('aria-busy')).toBe('true')
    expect(button.text()).toBe('正在创建…')
    expect(wrapper.getComponent(DataSourceSelect).props('disabled')).toBe(true)
    expect(wrapper.getComponent(ApiSelect).props('disabled')).toBe(true)
    expect(wrapper.getComponent(DynamicParameterForm).props('disabled')).toBe(true)
    expect(wrapper.findAll('[data-mode]').every(mode => mode.element.disabled)).toBe(true)
    await button.trigger('click')
    expect(api.submitDownloadTask).toHaveBeenCalledOnce()

    pending.resolve(receipt())
    await flushPromises()
    expect(wrapper.get('.download-feedback [role="status"]').text()).toContain('任务已接收')
    expect(wrapper.find('.pending-submission').exists()).toBe(false)
    expect(button.element.disabled).toBe(false)
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
    await wrapper.findAll('button').find(button => button.text() === '重新查找').trigger('click')
    await flushPromises()
    expect(api.listDownloadTasks).toHaveBeenCalledWith({ submissionId: SUBMISSION_ID })

    api.submitDownloadTask.mockResolvedValueOnce(receipt())
    await wrapper.findAll('button').find(button => button.text() === '使用原参数重新确认').trigger('click')
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


it('preflights a quick action, uses the fresh bigint and suppresses rapid repeats', async () => {
  const row = { ...recoveredTask(), status: 'FAILED', canRetry: true }
  api.listDownloadTasks.mockResolvedValue(emptyPage({ total: 1n, items: [row] }))
  const fresh = deferred(), post = deferred()
  api.getDownloadTask.mockReturnValueOnce(fresh.promise).mockResolvedValue({ ...row, status: 'RUNNING', canRetry: false })
  api.retryDownloadTask.mockReturnValueOnce(post.promise)
  const wrapper = await mountView()
  const list = wrapper.getComponent(DownloadTaskList)
  list.vm.$emit('control', row, 'retry')
  list.vm.$emit('control', row, 'retry')
  await nextTick()
  expect(list.get('[data-quick-retry]').element.disabled).toBe(true)
  expect(api.retryDownloadTask).not.toHaveBeenCalled()
  fresh.resolve({ ...row, version: 9007199254740993n })
  await flushPromises()
  expect(api.retryDownloadTask).toHaveBeenCalledExactlyOnceWith(TASK_ID, 9007199254740993n)
  expect(list.find('.task-action-feedback').text()).toContain('正在提交重试')
  post.resolve({ taskId: TASK_ID })
  await flushPromises()
  expect(list.text()).toContain('重试请求已接收')
  expect(api.listDownloadTasks).toHaveBeenCalledTimes(2)
  expect(api.getDownloadTask).toHaveBeenCalledTimes(2)
})

it('does not use list permissions when a fresh task forbids the operation or the lookup fails', async () => {
  const row = { ...recoveredTask(), status: 'FAILED', canRetry: true }
  api.listDownloadTasks.mockResolvedValue(emptyPage({ total: 1n, items: [row] }))
  api.getDownloadTask.mockResolvedValueOnce({ ...row, canRetry: false })
  const wrapper = await mountView()
  const list = wrapper.getComponent(DownloadTaskList)
  list.vm.$emit('control', row, 'retry')
  await flushPromises()
  expect(list.text()).toContain('当前操作不可用')
  expect(api.retryDownloadTask).not.toHaveBeenCalled()
  api.getDownloadTask.mockRejectedValueOnce(new ClientError('NETWORK'))
  list.vm.$emit('control', row, 'retry')
  await flushPromises()
  expect(list.find('.task-action-feedback').text()).not.toContain('当前操作不可用')
  expect(list.find('.task-action-feedback').text()).toContain('重新查询')
  expect(api.retryDownloadTask).not.toHaveBeenCalled()
})
