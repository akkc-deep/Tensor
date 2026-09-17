import { flushPromises, mount } from '@vue/test-utils'
import * as api from '../../api/integrityChecks.js'
import IntegrityCheckHistory from './IntegrityCheckHistory.vue'

vi.mock('../../api/integrityChecks.js', () => ({ listIntegrityChecks: vi.fn() }))

const task = { checkId: '33333333-3333-4333-8333-333333333333', pluginId: 'fixture', status: 'COMPLETED', createdAt: '2026-09-17T00:00:00Z', scope: { symbols: ['A', 'B', 'C', 'D'], startDate: '2026-09-01', endDate: '2026-09-02', apiNames: ['daily'] } }
beforeEach(() => api.listIntegrityChecks.mockResolvedValue({ page: 1, pageSize: 20, total: 9007199254740993n, items: [task] }))

it('renders task facts without deriving a report conclusion and paginates with BigInt', async () => {
  const wrapper = mount(IntegrityCheckHistory, { props: { sources: [{ pluginId: 'fixture', displayName: 'Fixture' }] } })
  await flushPromises()
  expect(wrapper.text()).toContain('450359962737050')
  expect(wrapper.text()).toContain('查看报告结论')
  expect(wrapper.text()).not.toMatch(/PASS|UNKNOWN/)
  await wrapper.get('button[aria-label="下一页"]').trigger('click')
  await flushPromises()
  expect(api.listIntegrityChecks).toHaveBeenLastCalledWith({ page: 2, pageSize: 20 }, { signal: expect.any(AbortSignal) })
})

it('resets filters to page one and does not show rows from the previous criteria after failure', async () => {
  const wrapper = mount(IntegrityCheckHistory, { props: { sources: [{ pluginId: 'fixture', displayName: 'Fixture' }] } })
  await flushPromises()
  api.listIntegrityChecks.mockRejectedValueOnce(Object.assign(new Error('failed'), { requestId: 'history-request' }))
  await wrapper.get('[aria-label="执行状态"]').setValue('FAILED')
  await flushPromises()
  expect(api.listIntegrityChecks).toHaveBeenLastCalledWith({ page: 1, pageSize: 20, status: 'FAILED' }, { signal: expect.any(AbortSignal) })
  expect(wrapper.text()).not.toContain('查看报告结论')
  expect(wrapper.text()).toContain('history-request')
})

it('preserves the same criteria snapshot when a manual refresh fails', async () => {
  const wrapper = mount(IntegrityCheckHistory, { props: { sources: [{ pluginId: 'fixture', displayName: 'Fixture' }] } })
  await flushPromises()
  api.listIntegrityChecks.mockRejectedValueOnce(Object.assign(new Error('failed'), { requestId: 'refresh-request' }))
  await wrapper.get('button[aria-label="刷新历史"]').trigger('click')
  await flushPromises()
  expect(wrapper.text()).toContain('查看报告结论')
  expect(wrapper.text()).toContain('refresh-request')
})
