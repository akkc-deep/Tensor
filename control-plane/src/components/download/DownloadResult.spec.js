import { mount } from '@vue/test-utils'
import { ElButton } from 'element-plus'

import { ApiError, ClientError } from '../../api/errors.js'
import AsyncStatePanel from '../common/AsyncStatePanel.vue'
import DownloadAction from './DownloadAction.vue'
import DownloadResult from './DownloadResult.vue'

import results from '../../test/fixtures/range-results.json'

describe('DownloadAction', () => {
  it('emits one submit for each unlocked button click', async () => {
    const wrapper = mount(DownloadAction)
    const action = wrapper.getComponent(ElButton)

    expect(action.text()).toBe('开始下载')
    expect(action.props()).toMatchObject({
      type: 'primary',
      nativeType: 'button',
      disabled: false,
      loading: false,
    })
    expect(action.get('button').attributes('type')).toBe('button')

    await action.get('button').trigger('click')
    await action.get('button').trigger('click')
    expect(wrapper.emitted('submit')).toEqual([[], []])
  })

  it('blocks disabled and submitting actions without changing its label', async () => {
    const wrapper = mount(DownloadAction, { props: { disabled: true } })
    const action = wrapper.getComponent(ElButton)

    expect(action.props('disabled')).toBe(true)
    action.vm.$emit('click')
    expect(wrapper.emitted('submit')).toBeUndefined()

    await wrapper.setProps({ disabled: false, submitting: true })
    expect(action.props()).toMatchObject({ disabled: true, loading: true })
    expect(action.get('button').attributes('aria-busy')).toBe('true')
    expect(action.text()).toBe('开始下载')
    action.vm.$emit('click')
    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(wrapper.text()).not.toMatch(/下载中|适配中|入库中|进度|百分比/)
  })
})

function counts(wrapper) {
  return Object.fromEntries(wrapper.findAll('dl > div').map((item) => [item.get('dt').text(), item.get('dd').text()]))
}

function snapshot(outcome, overrides = {}) {
  return { ...structuredClone(results[outcome]), ...overrides }
}

function apiError(code, downloadResult) {
  return new ApiError({ requestId: 'error-request', code, message: '安全错误说明。', retryable: false, fieldErrors: [], ...(downloadResult ? { downloadResult } : {}) })
}

describe('DownloadResult', () => {
  it.each([
    ['SUCCESS', '下载完成', 'status', ['2', '0', '0', '0', '10', '7', '3']],
    ['EMPTY', '下载完成，0 条数据', 'status', ['2', '0', '0', '0', '0', '0', '0']],
    ['NO_OPEN_DATES', '所选范围均为休市日期', 'status', ['0', '0', '0', '3', '0', '0', '0']],
    ['PARTIAL', '部分完成', 'alert', ['2', '1', '0', '0', '10', '7', '3']],
    ['FAILED', '本轮下载失败', 'alert', ['0', '2', '0', '0', '0', '0', '0']],
    ['UNCONFIRMED', '结果未确认', 'alert', ['2', '1', '未确认', '0', '10', '7', '3']],
  ])('announces %s and preserves all seven confirmed counters', (state, title, role, values) => {
    const wrapper = mount(DownloadResult, { props: { state, result: snapshot(state) } })
    expect(wrapper.get('h2').text()).toBe(title)
    expect(wrapper.get(`[role="${role}"]`).attributes('aria-live')).toBe(role === 'status' ? 'polite' : undefined)
    expect(wrapper.findAll('.download-result__counts dt').map((item) => item.text())).toEqual([
      '已完成项', '明确失败项', '未开始项', '跳过休市日期', '已确认返回行数', '已确认新增行数', '已确认更新行数',
    ])
    expect(wrapper.findAll('.download-result__counts dd').map((item) => item.text())).toEqual(values)
    expect(wrapper.text()).toContain(results[state].requestId)
    expect(wrapper.findAll('button').map((button) => button.text())).toEqual(results[state].taskId ? ['查看重试任务'] : [])
    if (state === 'NO_OPEN_DATES') expect(wrapper.text()).toContain('不属于来源返回空数据')
    wrapper.unmount()
  })

  it('keeps empty committed units plus a failure PARTIAL and distinguishes F from remaining', () => {
    const wrapper = mount(DownloadResult, { props: { state: 'PARTIAL', result: snapshot('PARTIAL', {
      completedUnits: 1, sourceRowCount: 0, insertedRows: 0, updatedRows: 0, remainingFailedUnits: 2,
    }) } })
    expect(wrapper.get('h2').text()).toBe('部分完成')
    expect(counts(wrapper)).toMatchObject({ 已完成项: '1', 明确失败项: '1', 当前剩余失败项: '2', 已确认返回行数: '0', 失败记录状态: '失败记录已确认' })
  })

  it('retains confirmed task navigation when the latest save is unknown and removes a deleted task', async () => {
    const wrapper = mount(DownloadResult, { props: { state: 'UNCONFIRMED', result: snapshot('UNCONFIRMED') }, attachTo: document.body })
    expect(counts(wrapper)).toMatchObject({ 未开始项: '未确认', 当前剩余失败项: '未确认', 失败记录状态: '失败记录保存或删除结果未确认' })
    expect(wrapper.text()).toContain('以下为本轮已确认小计，不代表完整结果。')
    const button = wrapper.get('button')
    button.element.focus()
    expect(document.activeElement).toBe(button.element)
    await button.trigger('click')
    expect(wrapper.emitted('view-task')).toEqual([[results.UNCONFIRMED.taskId]])
    await wrapper.setProps({ result: snapshot('UNCONFIRMED', { taskId: null, remainingFailedUnits: 0, notStartedUnits: 0 }) })
    expect(wrapper.find('button').exists()).toBe(false)
    expect(counts(wrapper)).toMatchObject({ 当前剩余失败项: '0', 未开始项: '0' })
    wrapper.unmount()
  })

  it('shows separate same-day stocks and safe historical failure codes without leaking diagnostics', () => {
    const result = snapshot('FAILED')
    result.failures[1].errorMessage = '<script>alert("text")</script>'
    const error = apiError('INTERNAL_ERROR')
    error.stack = 'SECRET_STACK'; error.cause = 'SECRET_CAUSE'
    const wrapper = mount(DownloadResult, { props: { state: 'FAILED', result, error } })
    const items = wrapper.get('[aria-label="本轮明确失败范围"]').findAll('li')
    expect(items).toHaveLength(2)
    expect(items[0].text()).toContain('000001.SZ · 2026-09-03')
    expect(items[1].text()).toContain('000002.SZ · 2026-09-03')
    expect(items[1].text()).toContain('HISTORICAL_SOURCE_CODE')
    expect(items[1].text()).toContain('<script>')
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.text()).not.toMatch(/SECRET_STACK|SECRET_CAUSE/)
  })

  it('keeps sparse REQUEST dates and each RANGE, MONTH and NONE boundary without inferring N', () => {
    const scopes = [
      { targetType: 'REQUEST', targetValue: '', timeType: 'DATE', timeValue: '2026-09-03' },
      { targetType: 'REQUEST', targetValue: '', timeType: 'DATE', timeValue: '2026-09-07' },
      { targetType: 'REQUEST', targetValue: '', timeType: 'RANGE', timeValue: '2026-09-08/2026-09-10' },
      { targetType: 'REQUEST', targetValue: '', timeType: 'MONTH', timeValue: '2026-08' },
      { targetType: 'REQUEST', targetValue: '', timeType: 'NONE', timeValue: '' },
    ]
    const wrapper = mount(DownloadResult, { props: {
      state: 'UNCONFIRMED', result: snapshot('UNCONFIRMED', { notStartedScopes: scopes }),
      context: { params: { ts_code: '000001.SZ', start_date: '20260901', end_date: '20260910', exchange: '<b>SSE</b>' } },
    } })
    expect(wrapper.findAll('.download-result__scopes h3').map((item) => item.text())).toEqual(['本轮明确失败范围', '本轮未开始范围', '本轮结果未确认范围'])
    expect(wrapper.get('[aria-label="本轮未开始范围"]').findAll('li').map((item) => item.text())).toEqual([
      '原请求条件 · 2026-09-03', '原请求条件 · 2026-09-07', '原请求条件 · 2026-09-08 至 2026-09-10',
      '原请求条件 · 2026-08（完整月份）', '原请求条件 · 原条件请求',
    ])
    expect(wrapper.get('.download-result__conditions').text()).toBe('原请求附加条件ts_code：000001.SZexchange：<b>SSE</b>')
    expect(wrapper.find('b').exists()).toBe(false)
    expect(counts(wrapper).未开始项).toBe('未确认')
  })

  it('shows no additional conditions for a REQUEST snapshot without extra inputs', () => {
    const wrapper = mount(DownloadResult, { props: { state: 'UNCONFIRMED', result: snapshot('UNCONFIRMED'), context: { params: { start_date: '20260901', end_date: '20260910' } } } })
    expect(wrapper.get('.download-result__conditions').text()).toContain('无附加条件')
  })

  it.each(['TIMEOUT', 'NETWORK', 'INVALID_RESPONSE', 'UNEXPECTED'])('never invents counts, scopes or a task for %s', (kind) => {
    const wrapper = mount(DownloadResult, { props: { state: 'UNCONFIRMED', error: new ClientError(kind, 'lost-request') } })
    expect(wrapper.get('[role="alert"]').text()).toContain('未收到可确认的下载结果，服务端可能仍在执行')
    expect(wrapper.text()).toContain('本提示不代表已终止、已回滚或已成功')
    expect(wrapper.text()).toContain('重新提交当前条件属于新的首次下载，不是原任务重试')
    expect(wrapper.text()).toContain('lost-request')
    expect(wrapper.find('dl').exists()).toBe(false)
    expect(wrapper.find('ul').exists()).toBe(false)
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it.each([
    ['CALENDAR_UNCONFIRMED', '未开始下载：日历未确认'],
    ['DOWNLOAD_BUSY', '已有下载正在执行'],
    ['SOURCE_REQUEST_UNCONFIRMED', '请求未完成'],
  ])('shows %s as a safe rejection without claiming a result', (code, title) => {
    const wrapper = mount(DownloadResult, { props: { state: 'FAILURE', error: apiError(code) } })
    expect(wrapper.get('h2').text()).toBe(title)
    expect(wrapper.text()).toContain('安全错误说明。')
    expect(wrapper.find('dl').exists()).toBe(false)
    expect(wrapper.find('button').exists()).toBe(false)
    expect(wrapper.text().includes('本次未开始业务下载')).toBe(code === 'CALENDAR_UNCONFIRMED')
  })
})

it('keeps retry uncertainty about the saved task instead of suggesting a new first download', () => {
  const wrapper = mount(DownloadResult, { props: { state: 'UNCONFIRMED', context: { operation: 'RETRY' } } })
  expect(wrapper.text()).toContain('再次手动重试只处理服务端当时仍保存的明细')
  expect(wrapper.text()).not.toContain('重新提交当前条件属于新的首次下载')
})
it('explains a preflight calendar retry rejection without claiming a new task', () => {
  const wrapper = mount(DownloadResult, { props: { state: 'FAILURE', context: { operation: 'RETRY' }, error: { code: 'CALENDAR_UNCONFIRMED', message: '日历未确认', requestId: 'retry-id' } } })
  expect(wrapper.text()).toContain('本次未开始业务重试，原失败记录保留')
  expect(wrapper.text()).not.toContain('也未新增失败任务')
})
