import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { useIntegrityCheck } from '../composables/useIntegrityCheck.js'
import { useIntegrityIssues } from '../composables/useIntegrityIssues.js'
import IntegrityCheckView from './IntegrityCheckView.vue'

const push = vi.fn()
vi.mock('vue-router', async (original) => ({ ...(await original()), useRouter: () => ({ push }) }))
vi.mock('../composables/useIntegrityCheck.js', () => ({ useIntegrityCheck: vi.fn() }))
vi.mock('../composables/useIntegrityIssues.js', () => ({ useIntegrityIssues: vi.fn() }))

const CHECK_A = '11111111-1111-4111-8111-111111111111'
const CHECK_B = '22222222-2222-4222-8222-222222222222'
const RESULT_ID = '33333333-3333-4333-8333-333333333333'
const detail = (checkId = CHECK_A) => ({
  checkId, submissionId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', pluginId: 'fixture', capabilityHash: 'a'.repeat(64),
  status: 'COMPLETED', overallStatus: 'FAIL', plannedUnits: 1, completedUnits: 1n, errorUnits: 0n, notRunUnits: 0n,
  statusCounts: { PASS: 0n, FAIL: 1n, WARN: 0n, UNKNOWN: 0n, NOT_APPLICABLE: 0n },
  originalRequest: { symbols: ['000001.SZ'], startDate: '2026-09-01', endDate: '2026-09-02', apiNames: ['daily'] },
  scope: { pluginId: 'fixture', symbols: ['000001.SZ'], startDate: '2026-09-01', endDate: '2026-09-02', apiNames: ['daily'] },
  createdAt: '2026-09-17T00:00:00Z', startedAt: '2026-09-17T00:00:01Z', finishedAt: '2026-09-17T00:00:02Z', errorCode: null, errorMessage: null,
})
const report = { scope: { datasetKey: { pluginId: 'fixture', apiName: 'daily' }, symbol: '000001.SZ', startDate: '2026-09-01', endDate: '2026-09-02', snapshotStartedAt: null }, descriptor: null, definitionHash: 'a'.repeat(64), publishedRange: null, unitStatus: 'COMPLETED', coverageStatus: 'FAIL', keyStatus: 'PASS', fieldStatus: 'PASS', overallStatus: 'FAIL', statistics: { actualCount: 1n, expectedCount: 2n, matchedCount: 1n, missingCount: 1n, suspectedMissingCount: 0n, extraCount: 0n, requiredFieldIssueCount: 0n, coverageRate: '0.500000' }, ruleResults: [], evidence: [], finishedAt: '2026-09-17T00:00:02Z', incomplete: false, issuesComplete: true, reasonCode: 'MISSING', message: 'missing' }
function flows() {
  const check = {
    detail: ref(detail()), results: ref({ page: 1, pageSize: 20, total: 1n, items: [{ resultId: RESULT_ID, checkId: CHECK_A, report }] }),
    resultsCriteria: ref({ page: 1, pageSize: 20 }), detailError: ref(null), resultsError: ref(null), loading: ref(false), connected: ref(true),
    load: vi.fn().mockResolvedValue(true), changeResults: vi.fn().mockResolvedValue(true), reconnect: vi.fn().mockResolvedValue(true), setActive: vi.fn(), dispose: vi.fn(),
  }
  const issues = { criteria: ref({ page: 1, pageSize: 20 }), page: ref(null), loading: ref(false), error: ref(null), load: vi.fn().mockResolvedValue(true), refresh: vi.fn(), reset: vi.fn(), dispose: vi.fn() }
  useIntegrityCheck.mockReturnValue(check); useIntegrityIssues.mockReturnValue(issues)
  return { check, issues }
}
function mounted(checkId = CHECK_A) {
  return mount(IntegrityCheckView, { props: { checkId }, attachTo: document.body, global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
}
beforeEach(() => { vi.resetAllMocks(); push.mockReset() })
afterEach(() => { document.body.innerHTML = '' })

it('loads a valid deep link and renders the saved report and result', async () => {
  const { check } = flows(), wrapper = mounted()
  await flushPromises()
  expect(check.load).toHaveBeenCalledWith(CHECK_A)
  expect(wrapper.text()).toContain('计算已完成')
  expect(wrapper.text()).toContain('有问题')
  expect(wrapper.text()).toContain('50%')
})

it('rejects an invalid id locally, stops old activity, and never loads it', async () => {
  const { check, issues } = flows(), wrapper = mounted('bad')
  await flushPromises()
  expect(wrapper.text()).toContain('检查编号无效')
  expect(check.load).not.toHaveBeenCalled()
  expect(check.setActive).toHaveBeenCalledWith(false)
  expect(issues.reset).toHaveBeenCalled()
})

it('applies result filters and paging without draft leakage', async () => {
  const { check } = flows(), wrapper = mounted(); await flushPromises()
  await wrapper.get('select[aria-label="结果股票"]').setValue('000001.SZ')
  await wrapper.get('button[aria-label="查询结果"]').trigger('click')
  expect(check.changeResults).toHaveBeenCalledWith({ page: 1, pageSize: 20, symbol: '000001.SZ' })
  check.resultsCriteria.value = { page: 1, pageSize: 20, symbol: '000001.SZ' }
  wrapper.findComponent({ name: 'IntegrityResultsTable' }).vm.$emit('update:pageSize', 50)
  expect(check.changeResults).toHaveBeenLastCalledWith({ page: 1, pageSize: 50, symbol: '000001.SZ' })
})

it('loads issues only for the selected result, focuses the region, and closes back to the trigger', async () => {
  const { issues } = flows(), wrapper = mounted(); await flushPromises()
  const button = wrapper.get('button[aria-label="查看问题"]')
  await button.trigger('click'); await flushPromises()
  expect(issues.load).toHaveBeenCalledWith(CHECK_A, { page: 1, pageSize: 20, resultId: RESULT_ID })
  expect(document.activeElement?.id).toBe('integrity-issues-title')
  await wrapper.get('button[aria-label="关闭问题明细"]').trigger('click'); await flushPromises()
  expect(issues.reset).toHaveBeenCalled()
  expect(document.activeElement).toBe(button.element)
})

it('uses GET reconnection and links a repeat check by reference only', async () => {
  const { check } = flows(), wrapper = mounted(); await flushPromises()
  await wrapper.get('button[aria-label="重新连接"]').trigger('click')
  await wrapper.get('button[aria-label="再次检查"]').trigger('click')
  expect(check.reconnect).toHaveBeenCalledTimes(1)
  expect(push).toHaveBeenCalledWith({ name: 'integrity', query: { fromCheckId: CHECK_A } })
})

it('clears issue state between task ids and disposes both flows on unmount', async () => {
  const { check, issues } = flows(), wrapper = mounted(); await flushPromises()
  await wrapper.setProps({ checkId: CHECK_B }); await flushPromises()
  expect(check.load).toHaveBeenLastCalledWith(CHECK_B)
  expect(issues.reset).toHaveBeenCalled()
  wrapper.unmount()
  expect(check.dispose).toHaveBeenCalled()
  expect(issues.dispose).toHaveBeenCalled()
})

it('keeps a saved snapshot visible when the connection is interrupted', async () => {
  const { check } = flows(); check.detailError.value = Object.assign(new Error('offline'), { requestId: 'detail-request' }); check.connected.value = false
  const wrapper = mounted(); await flushPromises()
  expect(wrapper.text()).toContain('连接已中断，以下为上次读取结果')
  expect(wrapper.text()).toContain('detail-request')
  expect(wrapper.text()).toContain('报告总览')
})
