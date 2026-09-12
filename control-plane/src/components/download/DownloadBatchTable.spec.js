import { flushPromises, mount } from '@vue/test-utils'
import { ElButton, ElPagination } from 'element-plus'
import { nextTick } from 'vue'
import examples from '../../../../docs/contracts/download-task-examples.json'
import { ClientError } from '../../api/errors.js'
import DownloadBatchTable from './DownloadBatchTable.vue'

function batch(overrides = {}) {
  const value = examples.examples.find((e) => e.name === 'partialFailedBatches').value.items[1]
  return Object.freeze({ ...value, sourceRows: 0n, insertedRows: 0n, updatedRows: 0n, ...overrides })
}
function result(items = [batch()], overrides = {}) {
  return { page: 1, pageSize: 20, total: BigInt(items.length), items, ...overrides }
}
const wrappers = []
function render(props = {}) {
  const wrapper = mount(DownloadBatchTable, { attachTo: document.body, props })
  wrappers.push(wrapper)
  return wrapper
}
afterEach(() => wrappers.splice(0).forEach((w) => w.unmount()))

it('shows failed and unexecuted intervals, immutable source params, attempts, full counts and times', () => {
  const wrapper = render({ result: result([
    batch({ insertedRows: 9007199254740993n, updatedRows: 9223372036854775807n }),
    batch({ batchId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', batchKey: '000003', status: 'PENDING', attemptCount: 0, error: null }),
  ]) })
  const rows = wrapper.findAll('tbody tr')
  expect(rows[0].text()).toContain('失败')
  expect(rows[0].text()).toContain('2026-08-04')
  expect(rows[0].text()).toContain('SOURCE_TIMEOUT')
  expect(rows[0].text()).toContain('9007199254740993')
  expect(rows[0].text()).toContain('9223372036854775807')
  expect(rows[1].text()).toContain('待执行')
  expect(rows[1].text()).toContain('0（尚未尝试）')
  expect(wrapper.text()).toContain('批次按服务端计划排序，拆分可能改变总批数')
  expect(wrapper.get('time').attributes('datetime')).toBeTruthy()
  expect(wrapper.get('[aria-label="批次表格"]').attributes('tabindex')).toBe('0')
  expect(wrapper.find('table[aria-live]').exists()).toBe(false)
})

it('keeps a pending batch with past attempts pending, and identifies single requests and split parents', () => {
  const wrapper = render({ result: result([batch({ status: 'PENDING', attemptCount: 2, error: null,
    rangeStart: null, rangeEnd: null, parentBatchId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb' })]) })
  expect(wrapper.text()).toContain('单次请求')
  expect(wrapper.text()).toContain('待执行')
  expect(wrapper.text()).not.toContain('本轮已执行')
  expect(wrapper.text()).toContain('父批次 bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb')
})

it('keeps same-page rows when querying fails, with an independent safe error and refresh', async () => {
  const wrapper = render({ result: result(), error: new ClientError('NETWORK', 'batch-request'), lastUpdatedAt: new Date() })
  expect(wrapper.findAll('tbody tr')).toHaveLength(1)
  expect(wrapper.text()).toContain('状态暂时无法更新')
  expect(wrapper.text()).toContain('上次更新')
  expect(wrapper.text()).toContain('batch-request')
  await wrapper.get('[data-refresh]').trigger('click')
  expect(wrapper.emitted('refresh')).toEqual([[]])
})

it('shows initial loading and failure separately, with a working reload', async () => {
  const wrapper = render({ loading: true })
  expect(wrapper.text()).toContain('正在加载批次')
  await wrapper.setProps({ loading: false, error: new ClientError('NETWORK', 'failed-query') })
  expect(wrapper.get('[role="alert"]').text()).toContain('批次加载失败')
  await wrapper.findAllComponents(ElButton).find((b) => b.text() === '重新加载').trigger('click')
  expect(wrapper.emitted('refresh')).toEqual([[]])
})

it.each([3n, 0n])('keeps page 7 when the real pager clamps a shrinking total %s', async (total) => {
  const wrapper = render({ page: 7, result: result([batch()], { page: 7, total: 140n }) })
  await wrapper.setProps({ result: result([], { page: 7, total }) })
  await nextTick()
  expect(wrapper.text()).toContain('本页暂无批次')
  expect(wrapper.emitted('update:page')).toBeUndefined()
  if (total === 0n) {
    expect(wrapper.getComponent(ElPagination).props('pageCount')).toBe(0)
    expect(wrapper.get('.btn-next').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.btn-prev').attributes('disabled')).toBeDefined()
  }
  await wrapper.findAllComponents(ElButton).find((b) => b.text() === '返回第一页').trigger('click')
  expect(wrapper.emitted('update:page')).toEqual([[1]])
})

it('keeps bigint total and caps accessible pages without losing the count', () => {
  const wrapper = render({ result: result([], { total: 9223372036854775807n }) })
  expect(wrapper.text()).toContain('9223372036854775807')
  expect(wrapper.getComponent(ElPagination).props('pageCount')).toBe(2147483647)
})

it('forwards actual next-page and page-size interactions', async () => {
  const wrapper = render({ page: 2, result: result([batch()], { page: 2, total: 60n }) })
  await wrapper.get('.btn-next').trigger('click')
  expect(wrapper.emitted('update:page')).toEqual([[3]])
  await wrapper.get('input[role="combobox"]').trigger('click')
  await flushPromises()
  const option = [...document.body.querySelectorAll('.el-select-dropdown__item')].find((o) => o.textContent.trim() === '50/page')
  option.click()
  await flushPromises()
  expect(wrapper.emitted('update:pageSize')).toEqual([[50]])
})
