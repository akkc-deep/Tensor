import { flushPromises, mount } from '@vue/test-utils'
import { ElButton, ElInput } from 'element-plus'
import { nextTick } from 'vue'

const api = vi.hoisted(() => ({
  listDataSources: vi.fn(),
  listDatasets: vi.fn(),
  getDataset: vi.fn(),
  queryDataset: vi.fn(),
}))

vi.mock('../api/dataSources.js', () => ({
  listDataSources: api.listDataSources,
}))

vi.mock('../api/datasets.js', () => ({
  listDatasets: api.listDatasets,
  getDataset: api.getDataset,
  queryDataset: api.queryDataset,
}))

import { ClientError } from '../api/errors.js'
import AsyncStatePanel from '../components/common/AsyncStatePanel.vue'
import DataSourceSelect from '../components/download/DataSourceSelect.vue'
import DatasetPagination from '../components/dataset/DatasetPagination.vue'
import DatasetSelect from '../components/dataset/DatasetSelect.vue'
import DatasetTable from '../components/dataset/DatasetTable.vue'
import DynamicFilterForm from '../components/dataset/DynamicFilterForm.vue'
import DatasetView from './DatasetView.vue'

const codeFilter = { field: 'ts_code', operator: 'EQ', controlType: 'TEXT' }
const tradeFilter = {
  field: 'trade_date',
  operator: 'BETWEEN',
  controlType: 'DATE_RANGE',
}
const annFilter = {
  field: 'ann_date',
  operator: 'BETWEEN',
  controlType: 'DATE_RANGE',
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

function dataset(overrides = {}) {
  return {
    pluginId: 'fixture',
    apiName: 'daily',
    displayName: '日线行情',
    category: '行情与估值',
    queryMode: 'trade_date',
    filters: [codeFilter, tradeFilter],
    fixedColumn: 'ts_code',
    ...overrides,
  }
}

function column(name, label = name, logicalType = 'STRING') {
  return {
    name,
    label,
    logicalType,
    nullable: false,
    displayOrder: 0,
    ...(logicalType === 'STRING' ? { length: 64 } : {}),
    ...(logicalType === 'DECIMAL' ? { precision: 38, scale: 18 } : {}),
  }
}

function definition(overrides = {}) {
  return {
    ...dataset(),
    columns: [
      column('ts_code', '证券代码'),
      column('trade_date', '交易日期', 'DATE'),
      column('close', '收盘价', 'DECIMAL'),
    ],
    ...overrides,
  }
}

function pageResponse(overrides = {}) {
  return {
    requestId: 'query-request',
    pluginId: 'fixture',
    apiName: 'daily',
    page: 1,
    pageSize: 50,
    totalElements: 1,
    totalPages: 1,
    columns: [
      'ts_code',
      'trade_date',
      'close',
      'source_plugin',
      'source_api',
      'ingested_at',
    ],
    items: [
      {
        ts_code: '000001.SZ',
        trade_date: '2026-09-04',
        close: '12.340000000000000000',
        source_plugin: 'fixture',
        source_api: 'daily',
        ingested_at: '2026-09-05T00:00:00Z',
      },
    ],
    ...overrides,
  }
}

function button(wrapper, text) {
  const result = wrapper
    .findAllComponents(ElButton)
    .find((candidate) => candidate.text() === text)
  if (!result) throw new Error(`Missing button: ${text}`)
  return result.get('button')
}

function expectBefore(first, second) {
  expect(
    first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING,
  ).toBeTruthy()
}

async function selectSource(wrapper, pluginId) {
  wrapper
    .getComponent(DataSourceSelect)
    .vm.$emit('update:modelValue', pluginId)
  await nextTick()
}

async function selectDataset(wrapper, apiName) {
  wrapper.getComponent(DatasetSelect).vm.$emit('update:modelValue', apiName)
  await nextTick()
}

async function setCode(wrapper, value) {
  wrapper
    .getComponent(DynamicFilterForm)
    .getComponent(ElInput)
    .vm.$emit('update:modelValue', value)
  await nextTick()
}

async function mountWithDefinition({
  currentSource = source(),
  currentDataset = dataset(),
  currentDefinition = definition(),
  attachTo,
} = {}) {
  api.listDataSources.mockResolvedValueOnce([currentSource])
  api.listDatasets.mockResolvedValueOnce([currentDataset])
  api.getDataset.mockResolvedValueOnce(currentDefinition)
  const wrapper = mount(DatasetView, { attachTo })
  await flushPromises()
  await selectDataset(wrapper, currentDataset.apiName)
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.resetAllMocks()
})

describe('DatasetView', () => {
  it('loads sources once and retries a safe source failure', async () => {
    const pending = deferred()
    const failure = new ClientError('NETWORK', 'source-request')
    api.listDataSources
      .mockReturnValueOnce(pending.promise)
      .mockResolvedValueOnce([])
    const wrapper = mount(DatasetView)
    await nextTick()

    expect(wrapper.findAll('h1')).toHaveLength(1)
    expect(wrapper.get('h1').text()).toBe('数据查看')
    expect(api.listDataSources).toHaveBeenCalledTimes(1)
    expect(wrapper.getComponent(AsyncStatePanel).props()).toMatchObject({
      state: 'LOADING',
      title: '正在加载数据源',
      message: '请稍候。',
    })
    expect(wrapper.text()).not.toContain('数据查看模块尚未完成')

    pending.reject(failure)
    await flushPromises()
    expect(wrapper.getComponent(AsyncStatePanel).props()).toMatchObject({
      state: 'FAILURE',
      title: '数据查看配置加载失败',
      message: '无法连接服务，请检查网络后重试。',
    })
    expect(wrapper.get('[role="alert"]').text()).toContain(
      '请求 ID：source-request',
    )

    await button(wrapper, '重新加载').trigger('click')
    await flushPromises()
    expect(api.listDataSources).toHaveBeenCalledTimes(2)
    expect(wrapper.getComponent(AsyncStatePanel).props()).toMatchObject({
      state: 'INITIAL',
      title: '请选择数据源',
      message: '选择数据源后加载可查询的数据集。',
    })
  })

  it('defaults one source and ignores stale dataset-list successes and failures', async () => {
    const sources = [
      source({ pluginId: 'first', displayName: 'First' }),
      source({ pluginId: 'second', displayName: 'Second' }),
    ]
    api.listDataSources.mockResolvedValueOnce(sources)
    const wrapper = mount(DatasetView)
    await flushPromises()
    expect(api.listDatasets).not.toHaveBeenCalled()

    const staleSuccess = deferred()
    const currentSuccess = deferred()
    api.listDatasets
      .mockReturnValueOnce(staleSuccess.promise)
      .mockReturnValueOnce(currentSuccess.promise)
    await selectSource(wrapper, 'first')
    await selectSource(wrapper, 'second')
    const secondDatasets = [
      dataset({ pluginId: 'second', apiName: 'weekly', displayName: '周线行情' }),
    ]
    currentSuccess.resolve(secondDatasets)
    await flushPromises()
    staleSuccess.resolve([dataset({ pluginId: 'first' })])
    await flushPromises()

    expect(wrapper.getComponent(DataSourceSelect).props('modelValue')).toBe(
      'second',
    )
    expect(wrapper.getComponent(DatasetSelect).props('datasets')).toBe(
      secondDatasets,
    )
    expect(wrapper.getComponent(AsyncStatePanel).props('title')).toBe(
      '请选择数据集',
    )

    const staleFailure = deferred()
    const newestSuccess = deferred()
    api.listDatasets
      .mockReturnValueOnce(staleFailure.promise)
      .mockReturnValueOnce(newestSuccess.promise)
    await selectSource(wrapper, 'first')
    await selectSource(wrapper, 'second')
    const newestDatasets = [
      dataset({ pluginId: 'second', apiName: 'monthly', displayName: '月线行情' }),
    ]
    newestSuccess.resolve(newestDatasets)
    await flushPromises()
    staleFailure.reject(new ClientError('NETWORK', 'stale-list'))
    await flushPromises()

    expect(api.listDatasets.mock.calls).toEqual([
      ['first'],
      ['second'],
      ['first'],
      ['second'],
    ])
    expect(wrapper.getComponent(DatasetSelect).props('datasets')).toBe(
      newestDatasets,
    )
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)

    const listFailure = new ClientError('NETWORK', 'dataset-list-request')
    const recoveredDatasets = [dataset({ pluginId: 'first' })]
    api.listDatasets
      .mockRejectedValueOnce(listFailure)
      .mockResolvedValueOnce(recoveredDatasets)
    await selectSource(wrapper, 'first')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain(
      '请求 ID：dataset-list-request',
    )
    await button(wrapper, '重新加载').trigger('click')
    await flushPromises()
    expect(api.listDatasets.mock.calls.slice(-2)).toEqual([
      ['first'],
      ['first'],
    ])
    expect(wrapper.getComponent(DatasetSelect).props('datasets')).toBe(
      recoveredDatasets,
    )

    api.listDataSources.mockResolvedValueOnce([source()])
    api.listDatasets.mockResolvedValueOnce([])
    const defaulted = mount(DatasetView)
    await flushPromises()
    expect(api.listDatasets).toHaveBeenLastCalledWith('fixture')
    expect(defaulted.getComponent(DataSourceSelect).props('modelValue')).toBe(
      'fixture',
    )
    defaulted.unmount()
  })

  it('loads the selected definition and ignores stale definition outcomes without querying', async () => {
    const datasets = [
      dataset(),
      dataset({
        apiName: 'weekly',
        displayName: '周线行情',
        filters: [annFilter],
      }),
    ]
    api.listDataSources.mockResolvedValueOnce([source()])
    api.listDatasets.mockResolvedValueOnce(datasets)
    const wrapper = mount(DatasetView)
    await flushPromises()

    const staleSuccess = deferred()
    const currentSuccess = deferred()
    api.getDataset
      .mockReturnValueOnce(staleSuccess.promise)
      .mockReturnValueOnce(currentSuccess.promise)
    await selectDataset(wrapper, 'daily')
    await selectDataset(wrapper, 'weekly')
    const weeklyDefinition = definition({
      apiName: 'weekly',
      displayName: '周线行情',
      filters: [annFilter],
    })
    currentSuccess.resolve(weeklyDefinition)
    await flushPromises()
    staleSuccess.resolve(definition())
    await flushPromises()

    expect(wrapper.getComponent(DatasetSelect).props('modelValue')).toBe(
      'weekly',
    )
    expect(wrapper.getComponent(DynamicFilterForm).props('filters')).toBe(
      weeklyDefinition.filters,
    )
    expect(api.queryDataset).not.toHaveBeenCalled()

    const staleFailure = deferred()
    const newestSuccess = deferred()
    api.getDataset
      .mockReturnValueOnce(staleFailure.promise)
      .mockReturnValueOnce(newestSuccess.promise)
    await selectDataset(wrapper, 'daily')
    await selectDataset(wrapper, 'weekly')
    newestSuccess.resolve(weeklyDefinition)
    await flushPromises()
    staleFailure.reject(new ClientError('NETWORK', 'stale-definition'))
    await flushPromises()

    expect(api.getDataset.mock.calls).toEqual([
      ['fixture', 'daily'],
      ['fixture', 'weekly'],
      ['fixture', 'daily'],
      ['fixture', 'weekly'],
    ])
    expect(wrapper.getComponent(DynamicFilterForm).props('filters')).toBe(
      weeklyDefinition.filters,
    )
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(api.queryDataset).not.toHaveBeenCalled()

    const definitionFailure = new ClientError(
      'NETWORK',
      'definition-request',
    )
    api.getDataset
      .mockRejectedValueOnce(definitionFailure)
      .mockResolvedValueOnce(weeklyDefinition)
    await selectDataset(wrapper, 'weekly')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain(
      '请求 ID：definition-request',
    )
    await button(wrapper, '重新加载').trigger('click')
    await flushPromises()
    expect(api.getDataset.mock.calls.slice(-2)).toEqual([
      ['fixture', 'weekly'],
      ['fixture', 'weekly'],
    ])
    expect(wrapper.getComponent(DynamicFilterForm).props('filters')).toBe(
      weeklyDefinition.filters,
    )
  })

  it('renders controls in order and validates before submitting one fresh criteria snapshot', async () => {
    const wrapper = await mountWithDefinition({ attachTo: document.body })

    try {
      const order = [
        wrapper.getComponent(DataSourceSelect).element,
        wrapper.getComponent(DatasetSelect).element,
        wrapper.getComponent(DynamicFilterForm).element,
        wrapper.get('.dataset-view__actions').element,
        wrapper.getComponent(AsyncStatePanel).element,
      ]
      order.slice(0, -1).forEach((element, index) =>
        expectBefore(element, order[index + 1]),
      )

      await setCode(wrapper, 'bad')
      await button(wrapper, '查询').trigger('click')
      await flushPromises()
      expect(api.queryDataset).not.toHaveBeenCalled()
      expect(wrapper.get('.field-error').text()).toBe(
        '请输入代码.市场格式，例如 000001.SZ',
      )
      expect(document.activeElement).toBe(
        wrapper.get('[data-filter="tsCode"] input').element,
      )

      const completed = pageResponse()
      api.queryDataset.mockResolvedValueOnce(completed)
      await setCode(wrapper, ' 000001.sz ')
      await button(wrapper, '查询').trigger('click')
      await flushPromises()
      expect(api.queryDataset).toHaveBeenCalledWith('fixture', 'daily', {
        tsCode: '000001.SZ',
        page: 1,
        pageSize: 50,
      })
      expect(wrapper.getComponent(DatasetTable).props('items')).toBe(
        completed.items,
      )
    } finally {
      wrapper.unmount()
    }
  })

  it('hides old records while loading and reset invalidates the request but keeps selection', async () => {
    const wrapper = await mountWithDefinition()
    api.queryDataset.mockResolvedValueOnce(pageResponse())
    await setCode(wrapper, '000001.SZ')
    await button(wrapper, '查询').trigger('click')
    await flushPromises()
    expect(wrapper.findComponent(DatasetTable).exists()).toBe(true)
    expect(wrapper.findComponent(DatasetPagination).exists()).toBe(true)

    const pending = deferred()
    api.queryDataset.mockReturnValueOnce(pending.promise)
    await setCode(wrapper, '000002.SZ')
    await button(wrapper, '查询').trigger('click')
    await nextTick()

    expect(wrapper.findComponent(DatasetTable).exists()).toBe(false)
    expect(wrapper.findComponent(DatasetPagination).exists()).toBe(false)
    expect(wrapper.getComponent(DynamicFilterForm).props('disabled')).toBe(
      true,
    )
    expect(button(wrapper, '查询').attributes('disabled')).toBeDefined()
    expect(button(wrapper, '重置').attributes('disabled')).toBeUndefined()
    expect(wrapper.getComponent(AsyncStatePanel).props('title')).toBe(
      '正在查询数据',
    )

    await button(wrapper, '重置').trigger('click')
    await nextTick()
    expect(wrapper.getComponent(DataSourceSelect).props('modelValue')).toBe(
      'fixture',
    )
    expect(wrapper.getComponent(DatasetSelect).props('modelValue')).toBe(
      'daily',
    )
    expect(wrapper.getComponent(ElInput).props('modelValue')).toBe('')
    expect(wrapper.getComponent(AsyncStatePanel).props('title')).toBe(
      '设置筛选条件后查询',
    )

    pending.resolve(pageResponse({ requestId: 'stale-reset' }))
    await flushPromises()
    expect(wrapper.findComponent(DatasetTable).exists()).toBe(false)
    expect(wrapper.getComponent(AsyncStatePanel).props('title')).toBe(
      '设置筛选条件后查询',
    )
  })

  it('renders successful records and delegates page and page-size changes with server facts', async () => {
    const currentDefinition = definition()
    const wrapper = await mountWithDefinition({ currentDefinition })
    const first = pageResponse({ totalElements: 80, totalPages: 2 })
    api.queryDataset.mockResolvedValueOnce(first)
    await setCode(wrapper, '000001.SZ')
    await button(wrapper, '查询').trigger('click')
    await flushPromises()

    expect(wrapper.getComponent(DatasetTable).props()).toMatchObject({
      columns: currentDefinition.columns,
      items: first.items,
    })
    expect(wrapper.getComponent(DatasetPagination).props()).toMatchObject({
      page: 1,
      pageSize: 50,
      totalElements: 80,
      totalPages: 2,
      disabled: false,
    })

    const normalized = pageResponse({
      requestId: 'page-request',
      page: 2,
      totalElements: 80,
      totalPages: 2,
      items: [{ ...first.items[0], ts_code: '000002.SZ' }],
    })
    api.queryDataset.mockResolvedValueOnce(normalized)
    wrapper.getComponent(DatasetPagination).vm.$emit('update:page', 3)
    await flushPromises()
    expect(api.queryDataset).toHaveBeenLastCalledWith('fixture', 'daily', {
      tsCode: '000001.SZ',
      page: 3,
      pageSize: 50,
    })
    expect(wrapper.getComponent(DatasetPagination).props('page')).toBe(2)
    expect(wrapper.getComponent(DatasetTable).props('items')).toBe(
      normalized.items,
    )

    const resized = pageResponse({
      requestId: 'size-request',
      page: 1,
      pageSize: 20,
      totalElements: 80,
      totalPages: 4,
    })
    api.queryDataset.mockResolvedValueOnce(resized)
    wrapper.getComponent(DatasetPagination).vm.$emit('update:pageSize', 20)
    await flushPromises()
    expect(api.queryDataset).toHaveBeenLastCalledWith('fixture', 'daily', {
      tsCode: '000001.SZ',
      page: 1,
      pageSize: 20,
    })
    expect(wrapper.getComponent(DatasetPagination).props()).toMatchObject({
      page: 1,
      pageSize: 20,
      totalElements: 80,
      totalPages: 4,
    })
  })

  it('keeps pagination for empty results and retries only retryable record failures', async () => {
    const wrapper = await mountWithDefinition()
    const empty = pageResponse({
      totalElements: 0,
      totalPages: 0,
      items: [],
    })
    api.queryDataset.mockResolvedValueOnce(empty)
    await button(wrapper, '查询').trigger('click')
    await flushPromises()

    expect(wrapper.getComponent(AsyncStatePanel).props()).toMatchObject({
      state: 'EMPTY',
      title: '未找到符合条件的数据',
      message: '请修改筛选条件后重新查询。',
    })
    expect(wrapper.findComponent(DatasetTable).exists()).toBe(false)
    expect(wrapper.getComponent(DatasetPagination).props()).toMatchObject({
      page: 1,
      pageSize: 50,
      totalElements: 0,
      totalPages: 0,
      disabled: false,
    })

    const retryable = new ClientError('NETWORK', 'records-request')
    api.queryDataset.mockRejectedValueOnce(retryable)
    wrapper.getComponent(DatasetPagination).vm.$emit('update:pageSize', 20)
    await flushPromises()
    expect(wrapper.getComponent(AsyncStatePanel).props()).toMatchObject({
      state: 'FAILURE',
      title: '查询失败',
      message: '无法连接服务，请检查网络后重试。',
    })
    expect(wrapper.get('[role="alert"]').text()).toContain(
      '请求 ID：records-request',
    )
    expect(wrapper.findComponent(DatasetTable).exists()).toBe(false)

    const recovered = pageResponse({
      pageSize: 20,
      totalElements: 0,
      totalPages: 0,
      items: [],
    })
    api.queryDataset.mockResolvedValueOnce(recovered)
    await button(wrapper, '重新查询').trigger('click')
    await flushPromises()
    expect(api.queryDataset.mock.calls.slice(-2)).toEqual([
      ['fixture', 'daily', { page: 1, pageSize: 20 }],
      ['fixture', 'daily', { page: 1, pageSize: 20 }],
    ])
    expect(wrapper.getComponent(AsyncStatePanel).props('state')).toBe('EMPTY')

    api.queryDataset.mockRejectedValueOnce(
      new ClientError('UNEXPECTED', 'final-request'),
    )
    await button(wrapper, '查询').trigger('click')
    await flushPromises()
    expect(wrapper.getComponent(AsyncStatePanel).props('state')).toBe(
      'FAILURE',
    )
    expect(
      wrapper
        .findAllComponents(ElButton)
        .some((candidate) => candidate.text() === '重新查询'),
    ).toBe(false)
  })

  it('clears query context on selection changes and keeps the read-only controls focusable in order', async () => {
    const sources = [
      source({ pluginId: 'first', displayName: 'First' }),
      source({ pluginId: 'second', displayName: 'Second' }),
    ]
    const daily = dataset({ pluginId: 'first' })
    const weekly = dataset({
      pluginId: 'first',
      apiName: 'weekly',
      displayName: '周线行情',
      filters: [annFilter],
    })
    const monthly = dataset({
      pluginId: 'second',
      apiName: 'monthly',
      displayName: '月线行情',
      filters: [codeFilter],
    })
    api.listDataSources.mockResolvedValueOnce(sources)
    api.listDatasets
      .mockResolvedValueOnce([daily, weekly])
      .mockResolvedValueOnce([monthly])
    api.getDataset
      .mockResolvedValueOnce(definition({ pluginId: 'first' }))
      .mockResolvedValueOnce(
        definition({
          pluginId: 'first',
          apiName: 'weekly',
          displayName: '周线行情',
          filters: [annFilter],
        }),
      )
      .mockResolvedValueOnce(
        definition({
          pluginId: 'second',
          apiName: 'monthly',
          displayName: '月线行情',
          filters: [codeFilter],
        }),
      )
    const wrapper = mount(DatasetView, { attachTo: document.body })

    try {
      await flushPromises()
      await selectSource(wrapper, 'first')
      await flushPromises()
      await selectDataset(wrapper, 'daily')
      await flushPromises()
      await setCode(wrapper, '000001.SZ')
      api.queryDataset.mockResolvedValueOnce(pageResponse({ pluginId: 'first' }))
      await button(wrapper, '查询').trigger('click')
      await flushPromises()
      expect(wrapper.findComponent(DatasetTable).exists()).toBe(true)

      api.queryDataset.mockRejectedValueOnce(
        new ClientError('NETWORK', 'page-request'),
      )
      wrapper.getComponent(DatasetPagination).vm.$emit('update:page', 2)
      await flushPromises()
      expect(button(wrapper, '重新查询').exists()).toBe(true)

      await selectDataset(wrapper, 'weekly')
      await flushPromises()
      expect(wrapper.findComponent(DatasetTable).exists()).toBe(false)
      expect(wrapper.findComponent(DatasetPagination).exists()).toBe(false)
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(wrapper.getComponent(DynamicFilterForm).props('filters')).toEqual([
        annFilter,
      ])
      expect(wrapper.getComponent(AsyncStatePanel).props('title')).toBe(
        '设置筛选条件后查询',
      )

      await selectSource(wrapper, 'second')
      await flushPromises()
      expect(wrapper.getComponent(DatasetSelect).props('modelValue')).toBe('')
      expect(wrapper.findComponent(DynamicFilterForm).exists()).toBe(false)
      expect(wrapper.getComponent(AsyncStatePanel).props('title')).toBe(
        '请选择数据集',
      )

      await selectDataset(wrapper, 'monthly')
      await flushPromises()
      const sourceInput = wrapper
        .getComponent(DataSourceSelect)
        .get('input[role="combobox"]')
      const datasetInput = wrapper
        .getComponent(DatasetSelect)
        .get('input[role="combobox"]')
      const filterInput = wrapper
        .getComponent(DynamicFilterForm)
        .get('[data-filter="tsCode"] input')
      const queryButton = button(wrapper, '查询')
      const resetButton = button(wrapper, '重置')
      for (const control of [
        sourceInput,
        datasetInput,
        filterInput,
        queryButton,
        resetButton,
      ]) {
        control.element.focus()
        expect(document.activeElement).toBe(control.element)
      }
      expectBefore(sourceInput.element, datasetInput.element)
      expectBefore(datasetInput.element, filterInput.element)
      expectBefore(filterInput.element, queryButton.element)
      expectBefore(queryButton.element, resetButton.element)
      expect(queryButton.attributes('type')).toBe('button')
      expect(resetButton.attributes('type')).toBe('button')
      const buttonTexts = wrapper.findAll('button').map((item) => item.text())
      for (const forbidden of ['排序', '新增', '编辑', '删除', '导出']) {
        expect(buttonTexts).not.toContain(forbidden)
      }
    } finally {
      wrapper.unmount()
    }
  })
})
