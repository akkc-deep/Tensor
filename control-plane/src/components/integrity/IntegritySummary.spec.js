import { mount } from '@vue/test-utils'
import IntegritySummary from './IntegritySummary.vue'

const detail = (changes = {}) => ({
  checkId: '11111111-1111-4111-8111-111111111111',
  submissionId: '22222222-2222-4222-8222-222222222222', pluginId: 'fixture',
  capabilityHash: 'a'.repeat(64), status: 'COMPLETED', overallStatus: 'FAIL', plannedUnits: 5,
  completedUnits: 4n, errorUnits: 1n, notRunUnits: 1n,
  statusCounts: { PASS: 1n, FAIL: 1n, WARN: 0n, UNKNOWN: 2n, NOT_APPLICABLE: 1n },
  originalRequest: { symbols: ['000001.SZ'], startDate: '2026-09-01', endDate: '2026-09-02' },
  scope: { pluginId: 'fixture', symbols: ['000001.SZ', '600000.SH'], startDate: '2026-09-01', endDate: '2026-09-02', apiNames: ['daily', 'trade_cal'] },
  createdAt: '2026-09-17T00:00:00Z', startedAt: '2026-09-17T00:00:01Z', finishedAt: '2026-09-17T00:00:02Z',
  errorCode: null, errorMessage: null, ...changes,
})

it('keeps execution state, saved conclusion, and exact progress separate', () => {
  const wrapper = mount(IntegritySummary, { props: { detail: detail() } })
  expect(wrapper.text()).toContain('计算已完成')
  expect(wrapper.text()).toContain('有问题')
  expect(wrapper.text()).toContain('已处理 4 / 计划 5')
  expect(wrapper.text()).toContain('执行错误 1')
  expect(wrapper.text()).toContain('未运行 1')
  expect(wrapper.text()).toContain('N/A（不适用）')
  expect(wrapper.findAll('.counts dd').at(4).text()).toBe('1')
  expect(wrapper.text()).not.toContain('80%')
})

it('renders the accepted scope, request meaning, identifiers, and nullable times', () => {
  const wrapper = mount(IntegritySummary, { props: { detail: detail({ startedAt: null, finishedAt: null }) } })
  expect(wrapper.text()).toContain('受理时固定为以下接口')
  expect(wrapper.text()).toContain('000001.SZ、600000.SH')
  expect(wrapper.text()).toContain('daily、trade_cal')
  expect(wrapper.text()).toContain('尚无')
  expect(wrapper.get('time').attributes('datetime')).toBe('2026-09-17T00:00:00Z')
  expect(wrapper.text()).toContain('Asia/Shanghai')
})

it.each([
  ['RUNNING', 'UNKNOWN', '进行中，已有结论', '无法判定'],
  ['FAILED', 'NOT_APPLICABLE', '执行失败', 'N/A（不适用）'],
  ['INTERRUPTED', 'WARN', '已中断', '待核实'],
])('preserves %s and %s instead of inferring pass', (status, overallStatus, statusText, conclusion) => {
  const wrapper = mount(IntegritySummary, { props: { detail: detail({ status, overallStatus, errorMessage: 'worker stopped' }) } })
  expect(wrapper.text()).toContain(statusText)
  expect(wrapper.text()).toContain(conclusion)
  if (status !== 'RUNNING') expect(wrapper.text()).toContain('worker stopped')
})

it('marks a queued report as in progress with its current saved conclusion', () => {
  const wrapper = mount(IntegritySummary, { props: { detail: detail({ status: 'QUEUED', overallStatus: 'WARN' }) } })
  expect(wrapper.text()).toContain('排队中')
  expect(wrapper.text()).toContain('进行中，已有结论')
  expect(wrapper.text()).toContain('待核实')
})
