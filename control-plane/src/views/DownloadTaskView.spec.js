import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import examples from '../../../docs/contracts/download-task-examples.json'
import { parseDownloadTask } from '../api/downloadTaskDtos.js'
import { ApiError, ClientError } from '../api/errors.js'
import * as api from '../api/downloadTasks.js'
import DownloadBatchTable from '../components/download/DownloadBatchTable.vue'
import DownloadTaskView from './DownloadTaskView.vue'

vi.mock('../api/downloadTasks.js', () => ({ getDownloadTask: vi.fn(), listDownloadTaskBatches: vi.fn(), retryDownloadTask: vi.fn(), resumeDownloadTask: vi.fn() }))
const ID = '22222222-2222-4222-8222-222222222222'
const OTHER = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
const REQUEST_ID = '11111111-1111-4111-8111-111111111111'
function task(name = 'partialFailedTask', overrides = {}) {
  return parseDownloadTask({ ...examples.examples.find((e) => e.name === name).value, ...overrides }, REQUEST_ID)
}
function deferred() { let resolve; const promise = new Promise((yes) => { resolve = yes }); return { promise, resolve } }
let wrapper
beforeEach(() => {
  vi.resetAllMocks()
  vi.useFakeTimers()
  api.getDownloadTask.mockResolvedValue(task())
  api.listDownloadTaskBatches.mockImplementation((id, c) => Promise.resolve({ ...c, total: 0n, items: [] }))
})
afterEach(() => { wrapper?.unmount(); wrapper = null; vi.clearAllTimers(); vi.useRealTimers() })
async function render(id = ID) {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/downloads/tasks/:taskId', name: 'download-task', component: DownloadTaskView },
    { path: '/downloads', name: 'downloads', component: { template: '<div />' } },
  ] })
  await router.push(`/downloads/tasks/${id}`)
  wrapper = mount(DownloadTaskView, { global: { plugins: [router] } })
  await flushPromises()
  return router
}

it('shows the URL task, all historical facts and dynamic counts with full bigint values', async () => {
  api.getDownloadTask.mockResolvedValue(task('partialFailedTask', { version: 9007199254740993n, deadlineAt: null,
    requestCount: 9223372036854775807n, runRequestCount: 9007199254740992n,
    counts: { ...task().counts, insertedRows: 9007199254740993n, updatedRows: 9223372036854775807n } }))
  await render()
  expect(wrapper.get('h1').text()).toBe('任务详情')
  expect(api.getDownloadTask).toHaveBeenCalledExactlyOnceWith(ID)
  expect(wrapper.get('a').attributes('href')).toBe('/downloads')
  expect(wrapper.text()).toContain(ID)
  expect(wrapper.text()).toContain('contract_fixture / daily')
  expect(wrapper.text()).toContain('end_date=20260805')
  expect(wrapper.text()).toContain('已结束 3 / 当前计划 3 批')
  expect(wrapper.text()).toContain('失败 1')
  expect(wrapper.text()).toContain('待执行 0')
  expect(wrapper.text()).toContain('9007199254740993')
  expect(wrapper.text()).toContain('9223372036854775807')
  expect(wrapper.text()).toContain('累计请求次数 9223372036854775807')
  expect(wrapper.text()).toContain('本轮请求次数 9007199254740992')
  expect(wrapper.text()).toContain('新增/更新为已提交写入操作次数，不是整段去重总量')
  expect(wrapper.find('progress').exists()).toBe(false)
  expect(wrapper.findAll('time')).toHaveLength(5)
  expect(wrapper.text()).toContain('尚无')
})

it.each([
  ['SINGLE', '本次请求已完成', '单次请求，结果不代表完整历史'],
  ['RANGE', '本次请求范围内的计划已完成', '日期区间'],
])('describes %s success within the request scope', async (mode, success, description) => {
  api.getDownloadTask.mockResolvedValue(task('succeededTask', { mode }))
  await render()
  expect(wrapper.text()).toContain(success)
  expect(wrapper.text()).toContain(description)
  expect(wrapper.text()).not.toContain('全部历史已完成')
})

it('does not infer success from zero unplanned batches and shows the stored planning error', async () => {
  api.getDownloadTask.mockResolvedValue(task('unplannedFailedTask'))
  await render()
  expect(wrapper.text()).toContain('尚未生成计划')
  expect(wrapper.text()).toContain(task('unplannedFailedTask').lastError.message)
  expect(wrapper.text()).not.toContain('本次请求已完成')
})

it('displays controls independently and disables both during control and until refreshed', async () => {
  api.getDownloadTask.mockResolvedValue(task('partialFailedTask', { canResume: true }))
  await render()
  expect(wrapper.find('[data-resume]').exists()).toBe(true)
  const post = deferred(), detail = deferred()
  api.retryDownloadTask.mockReturnValueOnce(post.promise)
  api.getDownloadTask.mockReturnValueOnce(detail.promise)
  await wrapper.get('[data-retry]').trigger('click')
  expect(wrapper.get('[data-retry]').attributes('disabled')).toBeDefined()
  expect(wrapper.get('[data-resume]').attributes('disabled')).toBeDefined()
  post.resolve({ taskId: ID, status: 'SUCCEEDED' })
  await flushPromises()
  expect(wrapper.text()).toContain('重试请求已接收')
  expect(wrapper.get('[data-task-status]').text()).toBe('部分失败')
  expect(wrapper.get('[data-retry]').attributes('disabled')).toBeDefined()
  detail.resolve(task('succeededTask'))
  await flushPromises()
  expect(wrapper.find('[data-retry]').exists()).toBe(false)
})

it('keeps server status and controls visible but disabled on query failure with last update and request ID', async () => {
  await render()
  api.getDownloadTask.mockRejectedValueOnce(new ClientError('NETWORK', REQUEST_ID))
  await wrapper.get('[data-refresh-task]').trigger('click')
  await flushPromises()
  expect(wrapper.get('[data-task-status]').text()).toBe('部分失败')
  expect(wrapper.text()).toContain('状态暂时无法更新')
  expect(wrapper.text()).toContain('上次更新')
  expect(wrapper.text()).toContain(REQUEST_ID)
  expect(wrapper.get('[data-retry]').attributes('disabled')).toBeDefined()
  expect(wrapper.findComponent(DownloadBatchTable).exists()).toBe(true)
})

it.each(['invalid', 'not-found', 'failure'])('shows %s directly with safe recovery', async (state) => {
  if (state === 'not-found') api.getDownloadTask.mockRejectedValueOnce(new ApiError({ code: 'TASK_NOT_FOUND', message: 'Download task not found', requestId: REQUEST_ID, fieldErrors: [], retryable: false }))
  if (state === 'failure') api.getDownloadTask.mockRejectedValueOnce(new ClientError('NETWORK', REQUEST_ID))
  await render(state === 'invalid' ? 'wrong' : ID)
  expect(wrapper.text()).toContain({ invalid: '任务地址无效', 'not-found': '任务不存在', failure: '任务加载失败' }[state])
  expect(wrapper.findComponent(DownloadBatchTable).exists()).toBe(false)
  expect(wrapper.get('a').attributes('href')).toBe('/downloads')
  if (state === 'invalid') expect(api.getDownloadTask).not.toHaveBeenCalled()
})

it('watches same-name route IDs, ignores late A data, and disposes on unmount', async () => {
  const late = deferred()
  api.getDownloadTask.mockReturnValueOnce(late.promise)
  const router = await render()
  await router.push(`/downloads/tasks/${OTHER}`)
  api.getDownloadTask.mockResolvedValue(task('interruptedTask', { taskId: OTHER }))
  late.resolve(task())
  await flushPromises()
  expect(wrapper.text()).toContain(OTHER)
  expect(wrapper.text()).not.toContain(ID)
  expect(wrapper.find('[data-retry]').exists()).toBe(false)
  expect(wrapper.find('[data-resume]').exists()).toBe(true)
  expect(wrapper.text()).toContain('手动恢复')
  wrapper.unmount(); wrapper = null
  await vi.advanceTimersByTimeAsync(30_000)
  expect(api.getDownloadTask).toHaveBeenCalledTimes(2)
})

it('connects leaf pagination and refresh to the shared query cycle', async () => {
  await render()
  wrapper.getComponent(DownloadBatchTable).vm.$emit('update:page', 7)
  await flushPromises()
  expect(api.listDownloadTaskBatches).toHaveBeenLastCalledWith(ID, { page: 7, pageSize: 20 })
  wrapper.getComponent(DownloadBatchTable).vm.$emit('update:pageSize', 50)
  await flushPromises()
  expect(api.listDownloadTaskBatches).toHaveBeenLastCalledWith(ID, { page: 1, pageSize: 50 })
})
