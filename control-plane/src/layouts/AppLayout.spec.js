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
vi.mock('../api/datasets.js', () => ({
  listDatasets: datasetApi.listDatasets,
  getDataset: datasetApi.getDataset,
  queryDataset: datasetApi.queryDataset,
}))

import { ClientError } from '../api/errors.js'
import DownloadAction from '../components/download/DownloadAction.vue'
import ApiSelect from '../components/download/ApiSelect.vue'
import DownloadResult from '../components/download/DownloadResult.vue'
import DynamicParameterForm from '../components/download/DynamicParameterForm.vue'
import DatasetPagination from '../components/dataset/DatasetPagination.vue'
import DatasetSelect from '../components/dataset/DatasetSelect.vue'
import DatasetTable from '../components/dataset/DatasetTable.vue'
import DynamicFilterForm from '../components/dataset/DynamicFilterForm.vue'
import { createThemeState, THEME_KEY } from '../composables/useTheme.js'
import { createAppRouter } from '../router/index.js'
import AppLayout from './AppLayout.vue'

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
      expect(wrapper.get('main h2').text()).toBe('请选择数据接口')
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
      expect(wrapper.get('main h2').text()).toBe('请选择数据源')
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
      await nextTick()
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

  it('keeps a pending download active while settings is open and shows its single response on return', async () => {
    const pending = deferred()
    metadataApi.listDataSources.mockResolvedValueOnce([source()])
    metadataApi.listApis.mockResolvedValueOnce([descriptor()])
    downloadApi.downloadDataset.mockReturnValueOnce(pending.promise)
    const { router, wrapper } = await mountAt('/downloads')

    try {
      wrapper.getComponent(ApiSelect).vm.$emit('update:modelValue', 'daily')
      await nextTick()
      wrapper
        .getComponent(DynamicParameterForm)
        .getComponent(ElDatePicker)
        .vm.$emit('update:modelValue', '2026-09-04')
      await nextTick()
      await wrapper.getComponent(DownloadAction).get('button').trigger('click')
      await nextTick()

      expect(downloadApi.downloadDataset).toHaveBeenCalledOnce()
      await router.push('/settings')
      await flushPromises()
      pending.resolve({
        requestId: 'download-request',
        outcome: 'SUCCESS',
        pluginId: 'fixture',
        apiName: 'daily',
        sourceRowCount: 12,
        insertedRows: 7,
        updatedRows: 5,
        message: '下载完成',
      })
      await flushPromises()

      await router.push('/downloads')
      await flushPromises()

      expect(wrapper.getComponent(DownloadResult).props('state')).toBe(
        'SUCCESS',
      )
      expect(
        wrapper.getComponent(DownloadResult).props('result').requestId,
      ).toBe('download-request')
      expect(downloadApi.downloadDataset).toHaveBeenCalledOnce()
      expect(metadataApi.listDataSources).toHaveBeenCalledOnce()
      expect(metadataApi.listApis).toHaveBeenCalledOnce()
    } finally {
      wrapper.unmount()
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
