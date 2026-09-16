import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import LiveApp from './LiveApp.vue'
import * as sources from '../../api/dataSources.js'
import * as tasks from '../../api/downloadTasks.js'
import * as datasets from '../../api/datasets.js'
import { ClientError } from '../../api/errors.js'
import { parseDownloadTask } from '../../api/downloadTaskDtos.js'
import examples from '../../../../docs/contracts/download-task-examples.json'

vi.mock('../../api/dataSources.js', () => ({ listDataSources: vi.fn(), listApis: vi.fn() }))
vi.mock('../../api/downloadTasks.js', () => ({
  getDownloadCapabilities: vi.fn(), listDownloadTasks: vi.fn(), submitDownloadTask: vi.fn(),
  getDownloadTask: vi.fn(), listDownloadTaskBatches: vi.fn(), retryDownloadTask: vi.fn(), resumeDownloadTask: vi.fn(),
}))
vi.mock('../../api/datasets.js', () => ({ listDatasets: vi.fn(), getDataset: vi.fn(), queryDataset: vi.fn() }))

const source = { pluginId: 'tushare_pro', displayName: 'Tushare Pro', enabled: true, downloadAvailable: true }
const api = { apiName: 'daily', displayName: '日线行情', category: '行情' }
const parameters = [
  { name: 'ts_code', label: '股票代码', type: 'TS_CODE', required: true },
  { name: 'trade_date', label: '交易日期', type: 'DATE', required: true },
]
const capabilities = {
  single: { available: true, parameters },
  range: { availability: 'NEEDS_VERIFICATION', unavailableReason: '待验证', parameters: [], completenessRule: { kind: 'UNKNOWN' } },
}
const id = '11111111-1111-4111-8111-111111111111'
let wrapper
const button = label => wrapper.findAll('button').find(item => item.text().trim() === label)
beforeEach(() => {
  vi.resetAllMocks()
  localStorage.clear()
  sources.listDataSources.mockResolvedValue([source])
  sources.listApis.mockResolvedValue([api])
  tasks.getDownloadCapabilities.mockResolvedValue(capabilities)
  tasks.listDownloadTasks.mockResolvedValue({ page: 1, pageSize: 20, total: 0n, items: [] })
  Object.defineProperties(HTMLDialogElement.prototype, {
    showModal: { configurable: true, value() { this.open = true } },
    close: { configurable: true, value() { this.open = false } },
  })
})
afterEach(() => {
  wrapper?.unmount(); wrapper = null
  delete HTMLDialogElement.prototype.showModal
  delete HTMLDialogElement.prototype.close
})

it('loads the real catalog and submits normalized single parameters once', async () => {
  tasks.submitDownloadTask.mockResolvedValue({ taskId: id })
  wrapper = mount(LiveApp)
  await flushPromises()
  expect(sources.listApis).toHaveBeenCalledWith('tushare_pro')
  expect(tasks.getDownloadCapabilities).toHaveBeenCalledWith('tushare_pro', 'daily')
  expect(wrapper.text()).not.toContain('DL-0018')
  expect(button('批量下载待验证').attributes('disabled')).toBeDefined()
  await wrapper.get('[name="ts_code"]').setValue('000001.sz')
  await wrapper.get('[name="trade_date"]').setValue('2026-09-01')
  await wrapper.get('form.download-form').trigger('submit')
  await flushPromises()
  expect(tasks.submitDownloadTask).toHaveBeenCalledOnce()
  expect(tasks.submitDownloadTask.mock.calls[0][0]).toMatchObject({
    pluginId: 'tushare_pro', apiName: 'daily', mode: 'SINGLE', params: { ts_code: '000001.SZ', trade_date: '20260901' },
  })
  expect(wrapper.text()).toContain('任务已创建')
})

it('uses range parameters declared by the backend, with no demo planning limit', async () => {
  tasks.getDownloadCapabilities.mockResolvedValue({ ...capabilities, range: {
    availability: 'AVAILABLE', dateLabel: '报告期', startParameter: 'start_date', endParameter: 'end_date',
    parameters: [parameters[0],
      { name: 'start_date', label: '开始报告期', type: 'DATE_RANGE_MEMBER', required: true, relatedParameter: 'end_date' },
      { name: 'end_date', label: '结束报告期', type: 'DATE_RANGE_MEMBER', required: true, relatedParameter: 'start_date' },
    ], completenessRule: { kind: 'RESPONSE_ONLY' },
  } })
  tasks.submitDownloadTask.mockResolvedValue({ taskId: id })
  wrapper = mount(LiveApp)
  await flushPromises()
  await button('批量下载').trigger('click')
  await wrapper.get('[name="ts_code"]').setValue('000001.SZ')
  await wrapper.get('[name="start_date"]').setValue('2000-03-31')
  await wrapper.get('[name="end_date"]').setValue('2026-06-30')
  await wrapper.get('form.download-form').trigger('submit')
  await flushPromises()
  expect(tasks.submitDownloadTask.mock.calls[0][0]).toMatchObject({ mode: 'RANGE', params: {
    ts_code: '000001.SZ', start_date: '20000331', end_date: '20260630',
  } })
  expect(wrapper.text()).toContain('不保证区间数据完整')
})

it.each(['mode', 'api'])('keeps the draft when reselecting the current %s', async target => {
  wrapper = mount(LiveApp)
  await flushPromises()
  await wrapper.get('[name="ts_code"]').setValue('000001.SZ')
  await wrapper.get('[name="trade_date"]').setValue('2026-09-01')
  await (target === 'mode' ? button('单次下载') : wrapper.get('.catalog-list button.selected')).trigger('click')
  await flushPromises()
  expect(wrapper.get('[name="ts_code"]').element.value).toBe('000001.SZ')
  expect(wrapper.get('[name="trade_date"]').element.value).toBe('2026-09-01')
  expect(tasks.getDownloadCapabilities).toHaveBeenCalledOnce()
})

it('shows a recoverable backend failure without falling back to sample data', async () => {
  sources.listDataSources.mockRejectedValueOnce(new Error('连接失败'))
  wrapper = mount(LiveApp)
  await flushPromises()
  expect(wrapper.text()).toContain('连接失败')
  expect(sources.listApis).not.toHaveBeenCalled()
  await button('重新连接').trigger('click')
  await flushPromises()
  expect(tasks.getDownloadCapabilities).toHaveBeenCalledWith('tushare_pro', 'daily')
})

it('keeps the original submission ID when the response is uncertain and the user replays it', async () => {
  tasks.submitDownloadTask.mockRejectedValueOnce(new ClientError('NETWORK')).mockResolvedValueOnce({ taskId: id })
  wrapper = mount(LiveApp)
  await flushPromises()
  await wrapper.get('[name="ts_code"]').setValue('000001.SZ')
  await wrapper.get('[name="trade_date"]').setValue('2026-09-01')
  await wrapper.get('form.download-form').trigger('submit')
  await flushPromises()
  const original = tasks.submitDownloadTask.mock.calls[0][0]
  expect(wrapper.text()).toContain('正在确认上次提交结果')
  expect(button('开始下载').attributes('disabled')).toBeDefined()
  await button('重发原请求').trigger('click')
  await flushPromises()
  expect(tasks.submitDownloadTask).toHaveBeenLastCalledWith(original)
  expect(wrapper.text()).toContain('任务已创建')
})

it.each([
  ['FAILED', 'canRetry', 'retryDownloadTask', '重试失败任务'],
  ['INTERRUPTED', 'canResume', 'resumeDownloadTask', '恢复下载'],
])('opens %s details and uses the fresh server version for its control', async (status, permit, method, label) => {
  const snapshot = { taskId: id, apiName: 'daily', pluginId: 'tushare_pro', mode: 'RANGE', status,
    version: 9007199254740993n, params: {}, planReady: true, createdAt: '2026-09-16T00:00:00Z',
    extraction: { ruleKind: 'RESPONSE_ONLY' }, [permit]: true,
    counts: { totalBatches: 3n, succeededBatches: 2n, failedBatches: 1n, pendingBatches: 0n, runningBatches: 0n,
      splitBatches: 0n, sourceRows: 9007199254740995n, insertedRows: 2n, updatedRows: 0n } }
  tasks.listDownloadTasks.mockResolvedValue({ page: 1, pageSize: 20, total: 1n, items: [snapshot] })
  tasks.getDownloadTask.mockResolvedValue(snapshot)
  tasks.listDownloadTaskBatches.mockResolvedValue({ page: 1, pageSize: 20, total: 0n, items: [] })
  tasks[method].mockResolvedValue({ taskId: id })
  wrapper = mount(LiveApp)
  await flushPromises()
  await wrapper.get('.task-name').trigger('click')
  await flushPromises()
  expect(wrapper.get('dialog').text()).toContain('9007199254740995')
  expect(wrapper.get('dialog').text()).toContain('数据完整性未确认')
  await button(label).trigger('click')
  await flushPromises()
  expect(tasks[method]).toHaveBeenCalledExactlyOnceWith(id, 9007199254740993n)
  await wrapper.get('[aria-label="关闭任务详情"]').trigger('click')
  expect(wrapper.find('dialog').exists()).toBe(false)
})

it('queries full data and paginates with the last submitted filters', async () => {
  const definition = { ...api, filters: [{ field: 'ts_code', operator: 'EQ', controlType: 'TEXT' }],
    columns: [{ name: 'amount', label: '金额', logicalType: 'DECIMAL' }] }
  datasets.listDatasets.mockResolvedValue([api])
  datasets.getDataset.mockResolvedValue(definition)
  datasets.queryDataset.mockResolvedValue({ page: 1, pageSize: 50, totalElements: 51, totalPages: 2,
    columns: ['amount', 'source_plugin', 'source_api', 'ingested_at'], items: [{ amount: '9007199254740993.12' }] })
  wrapper = mount(LiveApp)
  await flushPromises()
  await button('数据查看').trigger('click')
  await flushPromises()
  await wrapper.get('[name="tsCode"]').setValue('000001.sz')
  await wrapper.get('form.query-form').trigger('submit')
  await flushPromises()
  expect(wrapper.text()).toContain('9007199254740993.12')
  await wrapper.get('[name="tsCode"]').setValue('600000.SH')
  await wrapper.get('button[aria-label="数据下一页"]').trigger('click')
  await flushPromises()
  expect(datasets.queryDataset).toHaveBeenLastCalledWith('tushare_pro', 'daily', {
    tsCode: '000001.SZ', page: 2, pageSize: 50,
  })
  await nextTick()
})

it('applies task filters and page size on the server without sending unfinished drafts', async () => {
  tasks.listDownloadTasks.mockImplementation(criteria => Promise.resolve({ ...criteria, total: 201n, items: [] }))
  wrapper = mount(LiveApp)
  await flushPromises()
  await wrapper.get('[name="task-pluginId"]').setValue(' retired_source ')
  await wrapper.get('[name="task-apiName"]').setValue('daily')
  await wrapper.get('[name="task-submissionId"]').setValue(id)
  await wrapper.get('.live-task-filters').trigger('submit')
  await flushPromises()
  const filters = { pluginId: 'retired_source', apiName: 'daily', submissionId: id }
  expect(tasks.listDownloadTasks).toHaveBeenLastCalledWith({ page: 1, pageSize: 20, ...filters })
  await wrapper.get('[aria-label="任务状态"]').setValue('FAILED')
  await flushPromises()
  await wrapper.get('[aria-label="每页任务数"]').setValue('100')
  await flushPromises()
  await wrapper.get('[name="task-apiName"]').setValue('weekly')
  await wrapper.get('[aria-label="任务下一页"]').trigger('click')
  await flushPromises()
  expect(tasks.listDownloadTasks).toHaveBeenLastCalledWith({ page: 2, pageSize: 100, status: 'FAILED', ...filters })
  await wrapper.get('[name="task-submissionId"]').setValue('bad-id')
  const calls = tasks.listDownloadTasks.mock.calls.length
  await wrapper.get('.live-task-filters').trigger('submit')
  await flushPromises()
  expect(tasks.listDownloadTasks).toHaveBeenCalledTimes(calls)
  expect(wrapper.get('[name="task-submissionId"]').attributes('aria-invalid')).toBe('true')
  await button('重置任务筛选').trigger('click')
  await flushPromises()
  expect(tasks.listDownloadTasks).toHaveBeenLastCalledWith({ page: 1, pageSize: 100 })
})

it('filters batches, includes split parents and changes page size from the task dialog', async () => {
  const snapshot = parseDownloadTask(examples.examples.find(item => item.name === 'succeededTask').value, id)
  tasks.listDownloadTasks.mockResolvedValue({ page: 1, pageSize: 20, total: 1n, items: [snapshot] })
  tasks.getDownloadTask.mockResolvedValue(snapshot)
  tasks.listDownloadTaskBatches.mockImplementation((_taskId, criteria) => Promise.resolve({
    page: criteria.page, pageSize: criteria.pageSize, total: criteria.includeSplit ? 101n : 0n,
    items: criteria.includeSplit ? [{ batchId: id, parentBatchId: null, batchKey: 'parent-001', status: 'SPLIT',
      rangeStart: '2026-09-01', rangeEnd: '2026-09-16', attemptCount: 1n, sourceRows: 0n, insertedRows: 0n, updatedRows: 0n }] : [],
  }))
  wrapper = mount(LiveApp)
  await flushPromises()
  await wrapper.get('.task-name').trigger('click')
  await flushPromises()
  await wrapper.get('[aria-label="批次状态"]').setValue('SPLIT')
  await flushPromises()
  expect(tasks.listDownloadTaskBatches).toHaveBeenLastCalledWith(snapshot.taskId, { page: 1, pageSize: 20, status: 'SPLIT' })
  expect(wrapper.get('dialog').text()).toContain('显示拆分父批次')
  await wrapper.get('[aria-label="显示拆分父批次"]').setValue(true)
  await flushPromises()
  expect(wrapper.get('dialog').text()).toContain('parent-001')
  expect(wrapper.get('dialog').text()).toContain('已拆分')
  await wrapper.get('[aria-label="每页批次数"]').setValue('50')
  await flushPromises()
  await wrapper.get('[aria-label="批次下一页"]').trigger('click')
  await flushPromises()
  expect(tasks.listDownloadTaskBatches).toHaveBeenLastCalledWith(snapshot.taskId, { page: 2, pageSize: 50, status: 'SPLIT', includeSplit: true })
  await wrapper.get('[aria-label="显示拆分父批次"]').setValue(false)
  await flushPromises()
  expect(tasks.listDownloadTaskBatches).toHaveBeenLastCalledWith(snapshot.taskId, { page: 1, pageSize: 50, status: 'SPLIT' })
  expect(wrapper.get('dialog').text()).not.toContain('parent-001')
})
