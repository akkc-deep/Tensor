import { readFileSync } from 'node:fs'

import { flushPromises, mount } from '@vue/test-utils'
import { ElButton, ElDatePicker, ElInput } from 'element-plus'
import { nextTick } from 'vue'
import { createMemoryHistory } from 'vue-router'

const metadataApi = vi.hoisted(() => ({
  listDataSources: vi.fn(),
  listApis: vi.fn(),
}))
const downloadApi = vi.hoisted(() => ({ downloadDataset: vi.fn() }))
const downloadTaskApi = vi.hoisted(() => ({
  getDownloadCapabilities: vi.fn(),
  submitDownloadTask: vi.fn(),
  listDownloadTasks: vi.fn(),
  getDownloadTask: vi.fn(),
  listDownloadTaskBatches: vi.fn(),
  retryDownloadTask: vi.fn(),
  resumeDownloadTask: vi.fn(),
}))
const datasetApi = vi.hoisted(() => ({
  listDatasets: vi.fn(),
  getDataset: vi.fn(),
  queryDataset: vi.fn(),
}))

vi.mock('../api/dataSources.js', () => ({
  listDataSources: metadataApi.listDataSources,
  listApis: metadataApi.listApis,
}))
vi.mock('../api/downloads.js', () => ({
  downloadDataset: downloadApi.downloadDataset,
}))
vi.mock('../api/downloadTasks.js', () => ({
  getDownloadCapabilities: downloadTaskApi.getDownloadCapabilities,
  submitDownloadTask: downloadTaskApi.submitDownloadTask,
  listDownloadTasks: downloadTaskApi.listDownloadTasks,
  getDownloadTask: downloadTaskApi.getDownloadTask,
  listDownloadTaskBatches: downloadTaskApi.listDownloadTaskBatches,
  retryDownloadTask: downloadTaskApi.retryDownloadTask,
  resumeDownloadTask: downloadTaskApi.resumeDownloadTask,
}))
vi.mock('../api/datasets.js', () => ({
  listDatasets: datasetApi.listDatasets,
  getDataset: datasetApi.getDataset,
  queryDataset: datasetApi.queryDataset,
}))

import { ClientError } from '../api/errors.js'
import DownloadAction from '../components/download/DownloadAction.vue'
import ApiSelect from '../components/download/ApiSelect.vue'
import DownloadResult from '../components/download/DownloadResult.vue'
import DownloadTaskList from '../components/download/DownloadTaskList.vue'
import DynamicParameterForm from '../components/download/DynamicParameterForm.vue'
import DatasetPagination from '../components/dataset/DatasetPagination.vue'
import DatasetSelect from '../components/dataset/DatasetSelect.vue'
import DatasetTable from '../components/dataset/DatasetTable.vue'
import DynamicFilterForm from '../components/dataset/DynamicFilterForm.vue'
import { createThemeState, THEME_KEY } from '../composables/useTheme.js'
import { createAppRouter } from '../router/index.js'
import AppLayout from './AppLayout.vue'
import DownloadTaskView from '../views/DownloadTaskView.vue'
import examples from '../../../docs/contracts/download-task-examples.json'
import { parseDownloadTask } from '../api/downloadTaskDtos.js'

const styles = readFileSync('src/style.css', 'utf8')

let styleElement

beforeAll(() => {
  styleElement = document.createElement('style')
  styleElement.textContent = styles
  document.head.append(styleElement)
})

afterAll(() => styleElement.remove())

beforeEach(() => {
  vi.resetAllMocks()
  metadataApi.listDataSources.mockResolvedValue([])
  downloadTaskApi.getDownloadCapabilities.mockResolvedValue(capabilities())
  downloadTaskApi.listDownloadTasks.mockResolvedValue(taskPage())
  vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue(
    '33333333-3333-4333-8333-333333333333',
  )
  sessionStorage.clear()
  localStorage.clear()
  document.documentElement.removeAttribute('style')
})

function source() {
  return {
    pluginId: 'fixture',
    displayName: 'Fixture',
    description: 'Fixture 数据源',
    enabled: true,
    credentialConfigured: true,
    downloadAvailable: true,
    unavailableReason: null,
  }
}

function descriptor() {
  return {
    apiName: 'daily',
    displayName: '日线行情',
    category: '行情与估值',
    queryMode: 'trade_date',
    parameters: [
      {
        name: 'trade_date',
        label: '交易日期',
        type: 'DATE',
        required: true,
      },
    ],
  }
}

function capabilities() {
  return {
    single: { available: true, parameters: descriptor().parameters },
    range: {
      availability: 'UNSUPPORTED',
      unavailableReason: '仅支持单次请求',
      dateAxis: null,
      dateLabel: null,
      startParameter: null,
      endParameter: null,
      parameters: [],
      planningMode: null,
      splittable: false,
      policyVersion: null,
      completenessRule: null,
    },
  }
}

function taskPage(overrides = {}) {
  return Object.freeze({
    page: 1,
    pageSize: 20,
    total: 0n,
    items: Object.freeze([]),
    ...overrides,
  })
}

function taskReceipt() {
  return Object.freeze({
    requestId: '11111111-1111-4111-8111-111111111111',
    taskId: '22222222-2222-4222-8222-222222222222',
    status: 'QUEUED',
    version: 1n,
    createdAt: '2026-09-12T00:00:00Z',
  })
}

function datasetDescriptor() {
  return {
    pluginId: 'fixture',
    apiName: 'daily',
    displayName: '日线行情',
    category: '行情与估值',
    queryMode: 'trade_date',
    filters: [
      { field: 'ts_code', operator: 'EQ', controlType: 'TEXT' },
      { field: 'trade_date', operator: 'BETWEEN', controlType: 'DATE_RANGE' },
      { field: 'ann_date', operator: 'BETWEEN', controlType: 'DATE_RANGE' },
    ],
    fixedColumn: 'ts_code',
  }
}

function datasetDefinition() {
  return {
    ...datasetDescriptor(),
    columns: [
      {
        name: 'ts_code',
        label: '证券代码',
        logicalType: 'STRING',
        nullable: false,
        displayOrder: 0,
        length: 64,
      },
    ],
  }
}

function pageResponse(overrides = {}) {
  return {
    requestId: 'query-request',
    pluginId: 'fixture',
    apiName: 'daily',
    page: 1,
    pageSize: 50,
    totalElements: 220,
    totalPages: 5,
    columns: ['ts_code', 'source_plugin', 'source_api', 'ingested_at'],
    items: [
      {
        ts_code: '000001.SZ',
        source_plugin: 'fixture',
        source_api: 'daily',
        ingested_at: '2026-09-05T00:00:00Z',
      },
    ],
    ...overrides,
  }
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

async function readyDataset(wrapper) {
  wrapper.getComponent(DatasetSelect).vm.$emit('update:modelValue', 'daily')
  await flushPromises()
}

async function setFilter(wrapper, key, value) {
  const field = wrapper
    .getComponent(DynamicFilterForm)
    .get(`[data-filter="${key}"]`)
  const control = key === 'tsCode'
    ? field.getComponent(ElInput)
    : field.getComponent(ElDatePicker)
  control.vm.$emit('update:modelValue', value)
  await nextTick()
}

function action(wrapper, label) {
  const component = wrapper
    .findAllComponents(ElButton)
    .find((candidate) => candidate.text() === label)
  if (!component) throw new Error(`Missing button: ${label}`)
  return component.get('button')
}

function declaration(selector, property) {
  const rule = [...styleElement.sheet.cssRules].find((candidate) =>
    candidate.selectorText
      ?.split(',')
      .map((part) => part.trim())
      .includes(selector),
  )

  return rule.style.getPropertyValue(property).trim()
}

function resolveColor(value) {
  const variable = value.match(/var\((--[\w-]+)(?:,\s*(#[\da-f]{6}))?\)/i)
  if (variable) return declaration(':root', variable[1]) || variable[2]

  return value.match(/#[\da-f]{6}/i)[0]
}

function luminance(hex) {
  const channels = hex
    .match(/[\da-f]{2}/gi)
    .map((channel) => Number.parseInt(channel, 16) / 255)
    .map((channel) =>
      channel <= 0.04045
        ? channel / 12.92
        : ((channel + 0.055) / 1.055) ** 2.4,
    )

  return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
}

function contrastRatio(foreground, background) {
  const values = [luminance(foreground), luminance(background)].sort(
    (left, right) => right - left,
  )
  return (values[0] + 0.05) / (values[1] + 0.05)
}

async function mountAt(path) {
  const router = createAppRouter(createMemoryHistory())
  await router.push(path)
  await router.isReady()

  const result = {
    router,
    wrapper: mount(AppLayout, {
      attachTo: document.body,
      global: {
        plugins: [router],
        provide: { [THEME_KEY]: createThemeState() },
      },
    }),
  }
  await flushPromises()
  return result
}

describe('AppLayout', () => {
  it('renders the three-item semantic navigation, skip link, and active download view', async () => {
    const { wrapper } = await mountAt('/downloads')

    try {
      const nav = wrapper.get('nav[aria-label="工作区导航"]')
      const links = nav.findAll('a')
      expect(links.map((link) => link.text())).toEqual([
        '数据下载01',
        '数据查看02',
        '设置03',
      ])
      expect(links.map((link) => link.attributes('href'))).toEqual([
        '/downloads',
        '/datasets',
        '/settings',
      ])
      expect(links[0].attributes('aria-current')).toBe('page')
      expect(links[1].attributes('aria-current')).toBeUndefined()
      expect(links[2].attributes('aria-current')).toBeUndefined()
      const activeColor = resolveColor(
        declaration('.app-nav__link.router-link-active', 'color'),
      )
      expect(contrastRatio(activeColor, '#ffffff')).toBeGreaterThanOrEqual(4.5)
      expect(contrastRatio(activeColor, '#ecf5ff')).toBeGreaterThanOrEqual(4.5)
      const focusColor = resolveColor(
        declaration('.app-nav__link:focus-visible', 'outline'),
      )
      expect(contrastRatio(focusColor, '#ffffff')).toBeGreaterThanOrEqual(3)
      const skip = wrapper.get('a[href="#workspace"]')
      expect(skip.text()).toBe('跳转到工作区')
      expect(wrapper.get('main#workspace').attributes('tabindex')).toBe('-1')
      expect(wrapper.get('main h1').text()).toBe('数据下载')
      expect(
        wrapper.findAll('main h2').map((heading) => heading.text()),
      ).toEqual(['下载配置', '任务接收', '等待提交任务', '近期任务'])
      expect(wrapper.find('input[type="color"]').exists()).toBe(false)
      expect(wrapper.getComponent(DownloadAction).props('disabled')).toBe(
        true,
      )
      expect(wrapper.text()).not.toContain('数据下载模块尚未完成')
    } finally {
      wrapper.unmount()
    }
  })

  it('keeps dataset navigation focusable and switches the active view', async () => {
    const { router, wrapper } = await mountAt('/downloads')

    try {
      const links = wrapper.get('nav[aria-label="工作区导航"]').findAll('a')
      links[1].element.focus()
      expect(document.activeElement).toBe(links[1].element)

      await links[1].trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.name).toBe('datasets')
      expect(links[0].attributes('aria-current')).toBeUndefined()
      expect(links[1].attributes('aria-current')).toBe('page')
      expect(wrapper.get('main h1').text()).toBe('数据查看')
      expect(wrapper.get('main .async-state-panel h2').text()).toBe(
        '请选择数据源',
      )
      expect(wrapper.text()).toContain('选择数据源后加载可查询的数据集。')
      expect(wrapper.text()).not.toContain('数据查看模块尚未完成')
    } finally {
      wrapper.unmount()
    }
  })

  it('opens settings without loading API metadata', async () => {
    const { wrapper } = await mountAt('/settings')

    try {
      expect(wrapper.get('main h1').text()).toBe('设置')
      expect(wrapper.get('input[type="color"]').element.value).toBe('#2857b4')
      expect(metadataApi.listDataSources).not.toHaveBeenCalled()
      expect(metadataApi.listApis).not.toHaveBeenCalled()
      expect(downloadApi.downloadDataset).not.toHaveBeenCalled()
      expect(downloadTaskApi.getDownloadCapabilities).not.toHaveBeenCalled()
      expect(downloadTaskApi.submitDownloadTask).not.toHaveBeenCalled()
      expect(downloadTaskApi.listDownloadTasks).not.toHaveBeenCalled()
      expect(datasetApi.listDatasets).not.toHaveBeenCalled()
      expect(datasetApi.getDataset).not.toHaveBeenCalled()
      expect(datasetApi.queryDataset).not.toHaveBeenCalled()
    } finally {
      wrapper.unmount()
    }
  })

  it('keeps the download form instance and metadata when visiting settings', async () => {
    metadataApi.listDataSources.mockResolvedValueOnce([source()])
    metadataApi.listApis.mockResolvedValueOnce([descriptor()])
    const { router, wrapper } = await mountAt('/downloads')

    try {
      wrapper.getComponent(ApiSelect).vm.$emit('update:modelValue', 'daily')
      await flushPromises()
      wrapper
        .getComponent(DynamicParameterForm)
        .getComponent(ElDatePicker)
        .vm.$emit('update:modelValue', '2026-09-04')
      await nextTick()

      await router.push('/settings')
      await flushPromises()
      expect(wrapper.get('main h1').text()).toBe('设置')

      await router.push('/downloads')
      await flushPromises()

      expect(
        wrapper
          .getComponent(DynamicParameterForm)
          .getComponent(ElDatePicker)
          .props('modelValue'),
      ).toBe('2026-09-04')
      expect(metadataApi.listDataSources).toHaveBeenCalledTimes(1)
      expect(metadataApi.listApis).toHaveBeenCalledTimes(1)
    } finally {
      wrapper.unmount()
    }
  })

  it('pauses the kept-alive task list while settings is open and accepts a pending task on return', async () => {
    vi.useFakeTimers()
    const pending = deferred()
    const lateList = deferred()
    const resumedList = deferred()
    metadataApi.listDataSources.mockResolvedValueOnce([source()])
    metadataApi.listApis.mockResolvedValueOnce([descriptor()])
    downloadTaskApi.listDownloadTasks
      .mockResolvedValueOnce(taskPage())
      .mockReturnValueOnce(lateList.promise)
      .mockReturnValueOnce(resumedList.promise)
    downloadTaskApi.submitDownloadTask.mockReturnValueOnce(pending.promise)
    const { router, wrapper } = await mountAt('/downloads')

    try {
      wrapper.getComponent(ApiSelect).vm.$emit('update:modelValue', 'daily')
      await flushPromises()
      wrapper
        .getComponent(DynamicParameterForm)
        .getComponent(ElDatePicker)
        .vm.$emit('update:modelValue', '2026-09-04')
      await nextTick()
      await wrapper.getComponent(DownloadAction).get('button').trigger('click')
      await nextTick()

      expect(downloadTaskApi.submitDownloadTask).toHaveBeenCalledOnce()
      await vi.advanceTimersByTimeAsync(5_000)
      expect(downloadTaskApi.listDownloadTasks).toHaveBeenCalledTimes(2)

      await router.push('/settings')
      await flushPromises()
      await vi.advanceTimersByTimeAsync(30_000)
      expect(downloadTaskApi.listDownloadTasks).toHaveBeenCalledTimes(2)

      lateList.resolve(taskPage({ total: 9n }))
      pending.resolve(taskReceipt())
      await flushPromises()
      expect(downloadTaskApi.listDownloadTasks).toHaveBeenCalledTimes(2)

      await router.push('/downloads')
      await flushPromises()

      expect(downloadTaskApi.listDownloadTasks).toHaveBeenCalledTimes(3)
      expect(wrapper.getComponent(DownloadTaskList).props('result').total).toBe(0n)
      resumedList.resolve(taskPage({ total: 2n }))
      await flushPromises()

      expect(
        wrapper
          .getComponent(DynamicParameterForm)
          .getComponent(ElDatePicker)
          .props('modelValue'),
      ).toBe('2026-09-04')
      expect(wrapper.text()).toContain('任务已接收')
      expect(wrapper.text()).not.toContain('下载成功')
      expect(wrapper.findComponent(DownloadResult).exists()).toBe(false)
      expect(wrapper.getComponent(DownloadTaskList).props('result').total).toBe(2n)
      expect(downloadTaskApi.submitDownloadTask).toHaveBeenCalledOnce()
      expect(metadataApi.listDataSources).toHaveBeenCalledOnce()
      expect(metadataApi.listApis).toHaveBeenCalledOnce()
    } finally {
      wrapper.unmount()
      vi.clearAllTimers()
      vi.useRealTimers()
    }
  })

  it('keeps dataset filters, second page, page size, and rows when visiting settings', async () => {
    metadataApi.listDataSources.mockResolvedValueOnce([source()])
    datasetApi.listDatasets.mockResolvedValueOnce([datasetDescriptor()])
    datasetApi.getDataset.mockResolvedValueOnce(datasetDefinition())
    datasetApi.queryDataset
      .mockResolvedValueOnce(pageResponse())
      .mockResolvedValueOnce(pageResponse({ pageSize: 100, totalPages: 3 }))
      .mockResolvedValueOnce(pageResponse({
        page: 2,
        pageSize: 100,
        totalPages: 3,
        items: [{ ...pageResponse().items[0], ts_code: '000002.SZ' }],
      }))
    const { router, wrapper } = await mountAt('/datasets')

    try {
      await readyDataset(wrapper)
      await setFilter(wrapper, 'tsCode', '000001.SZ')
      await setFilter(wrapper, 'tradeDateFrom', '2026-08-01')
      await setFilter(wrapper, 'annDateTo', '2026-08-31')
      await action(wrapper, '查询').trigger('click')
      await flushPromises()
      wrapper.getComponent(DatasetPagination).vm.$emit('update:pageSize', 100)
      await flushPromises()
      wrapper.getComponent(DatasetPagination).vm.$emit('update:page', 2)
      await flushPromises()
      await setFilter(wrapper, 'tsCode', '000003.SZ')

      await router.push('/settings')
      await flushPromises()
      await router.push('/datasets')
      await flushPromises()

      expect(
        wrapper
          .getComponent(DynamicFilterForm)
          .get('[data-filter="tsCode"]')
          .getComponent(ElInput)
          .props('modelValue'),
      ).toBe('000003.SZ')
      expect(wrapper.getComponent(DatasetPagination).props()).toMatchObject({
        page: 2,
        pageSize: 100,
        totalElements: 220,
        totalPages: 3,
      })
      expect(
        wrapper.getComponent(DatasetTable).props('items')[0].ts_code,
      ).toBe('000002.SZ')
      expect(datasetApi.queryDataset).toHaveBeenCalledTimes(3)
      expect(datasetApi.queryDataset.mock.calls[2]).toEqual([
        'fixture',
        'daily',
        {
          tsCode: '000001.SZ',
          tradeDateFrom: '2026-08-01',
          annDateTo: '2026-08-31',
          page: 2,
          pageSize: 100,
        },
      ])
      expect(metadataApi.listDataSources).toHaveBeenCalledOnce()
      expect(datasetApi.listDatasets).toHaveBeenCalledOnce()
      expect(datasetApi.getDataset).toHaveBeenCalledOnce()
    } finally {
      wrapper.unmount()
    }
  })

  it('accepts a pending dataset response while settings is open without querying again', async () => {
    const pending = deferred()
    metadataApi.listDataSources.mockResolvedValueOnce([source()])
    datasetApi.listDatasets.mockResolvedValueOnce([datasetDescriptor()])
    datasetApi.getDataset.mockResolvedValueOnce(datasetDefinition())
    datasetApi.queryDataset.mockReturnValueOnce(pending.promise)
    const { router, wrapper } = await mountAt('/datasets')

    try {
      await readyDataset(wrapper)
      await setFilter(wrapper, 'tsCode', '000001.SZ')
      await action(wrapper, '查询').trigger('click')
      await nextTick()
      await router.push('/settings')
      await flushPromises()

      pending.resolve(pageResponse())
      await flushPromises()
      await router.push('/datasets')
      await flushPromises()

      expect(
        wrapper.getComponent(DatasetTable).props('items')[0].ts_code,
      ).toBe('000001.SZ')
      expect(datasetApi.queryDataset).toHaveBeenCalledOnce()
      expect(metadataApi.listDataSources).toHaveBeenCalledOnce()
    } finally {
      wrapper.unmount()
    }
  })

  it('keeps a failed dataset snapshot across settings and retries its original filters', async () => {
    metadataApi.listDataSources.mockResolvedValueOnce([source()])
    datasetApi.listDatasets.mockResolvedValueOnce([datasetDescriptor()])
    datasetApi.getDataset.mockResolvedValueOnce(datasetDefinition())
    datasetApi.queryDataset
      .mockRejectedValueOnce(new ClientError('NETWORK', 'query-failed'))
      .mockResolvedValueOnce(pageResponse())
    const { router, wrapper } = await mountAt('/datasets')

    try {
      await readyDataset(wrapper)
      await setFilter(wrapper, 'tsCode', '000001.SZ')
      await setFilter(wrapper, 'tradeDateFrom', '2026-08-01')
      await action(wrapper, '查询').trigger('click')
      await flushPromises()
      await setFilter(wrapper, 'tsCode', '000099.SZ')

      await router.push('/settings')
      await flushPromises()
      await router.push('/datasets')
      await flushPromises()

      expect(wrapper.get('[role="alert"]').text()).toContain('query-failed')
      await action(wrapper, '重新查询').trigger('click')
      await flushPromises()

      expect(datasetApi.queryDataset.mock.calls).toEqual([
        [
          'fixture',
          'daily',
          {
            tsCode: '000001.SZ',
            tradeDateFrom: '2026-08-01',
            page: 1,
            pageSize: 50,
          },
        ],
        [
          'fixture',
          'daily',
          {
            tsCode: '000001.SZ',
            tradeDateFrom: '2026-08-01',
            page: 1,
            pageSize: 50,
          },
        ],
      ])
      expect(
        wrapper
          .getComponent(DynamicFilterForm)
          .get('[data-filter="tsCode"]')
          .getComponent(ElInput)
          .props('modelValue'),
      ).toBe('000099.SZ')
      expect(wrapper.getComponent(DatasetTable).exists()).toBe(true)
    } finally {
      wrapper.unmount()
    }
  })

  it('renders a recoverable not-found view for an unknown path', async () => {
    const { wrapper } = await mountAt('/missing')

    try {
      expect(wrapper.get('main h1').text()).toBe('页面不存在')
      expect(wrapper.get('main p').text()).toBe('当前地址不存在。')
      const returnLink = wrapper.get('main a')
      expect(returnLink.text()).toBe('返回数据下载')
      expect(returnLink.attributes('href')).toBe('/downloads')
      const actionColor = resolveColor(declaration('.page__action', 'color'))
      expect(contrastRatio(actionColor, '#ffffff')).toBeGreaterThanOrEqual(4.5)
    } finally {
      wrapper.unmount()
    }
  })
})

function detailTask(overrides = {}) {
  return parseDownloadTask({ ...examples.examples.find((e) => e.name === 'partialFailedTask').value, ...overrides }, '11111111-1111-4111-8111-111111111111')
}

it('opens details through the list RouterLink, pauses the cached list, and returns to the same form', async () => {
  vi.useFakeTimers()
  metadataApi.listDataSources.mockResolvedValueOnce([source()])
  metadataApi.listApis.mockResolvedValueOnce([descriptor()])
  downloadTaskApi.listDownloadTasks.mockResolvedValue(taskPage({ total: 1n, items: [detailTask()] }))
  downloadTaskApi.getDownloadTask.mockResolvedValue(detailTask())
  downloadTaskApi.listDownloadTaskBatches.mockResolvedValue(taskPage())
  const { router, wrapper } = await mountAt('/downloads')
  try {
    wrapper.getComponent(ApiSelect).vm.$emit('update:modelValue', 'daily')
    await flushPromises()
    const originalForm = wrapper.getComponent(DynamicParameterForm).element
    wrapper.getComponent(DynamicParameterForm).getComponent(ElDatePicker).vm.$emit('update:modelValue', '2026-09-04')
    await nextTick()
    await wrapper.get('.download-task-list a').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('download-task')
    expect(wrapper.get('h1').text()).toBe('任务详情')
    expect(wrapper.get('.workspace-bar b').text()).toBe('任务详情')
    expect(wrapper.get('nav[aria-label="工作区导航"] a').classes()).toContain('router-link-active')
    expect(downloadTaskApi.getDownloadTask).toHaveBeenCalledExactlyOnceWith(detailTask().taskId)
    await vi.advanceTimersByTimeAsync(6000)
    expect(downloadTaskApi.listDownloadTasks).toHaveBeenCalledTimes(1)
    await wrapper.get('.task-detail__toolbar a').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('downloads')
    expect(wrapper.getComponent(DynamicParameterForm).element).toBe(originalForm)
    expect(wrapper.getComponent(DynamicParameterForm).getComponent(ElDatePicker).props('modelValue')).toBe('2026-09-04')
    expect(downloadTaskApi.listDownloadTasks).toHaveBeenCalledTimes(2)
  } finally { wrapper.unmount(); vi.clearAllTimers(); vi.useRealTimers() }
})

it('reuses a same-name detail route while discarding the old task response', async () => {
  const late = deferred()
  downloadTaskApi.getDownloadTask.mockReturnValueOnce(late.promise)
  downloadTaskApi.listDownloadTaskBatches.mockResolvedValue(taskPage())
  const { router, wrapper } = await mountAt(`/downloads/tasks/${detailTask().taskId}`)
  try {
    const view = wrapper.getComponent(DownloadTaskView).element
    const other = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
    await router.push(`/downloads/tasks/${other}`)
    downloadTaskApi.getDownloadTask.mockResolvedValue(detailTask({ taskId: other, canRetry: false }))
    late.resolve(detailTask())
    await flushPromises()
    expect(wrapper.getComponent(DownloadTaskView).element).toBe(view)
    expect(wrapper.text()).toContain(other)
    expect(wrapper.text()).not.toContain(detailTask().taskId)
    expect(downloadTaskApi.getDownloadTask).toHaveBeenCalledTimes(2)
    expect(metadataApi.listDataSources).not.toHaveBeenCalled()
  } finally { wrapper.unmount() }
})
