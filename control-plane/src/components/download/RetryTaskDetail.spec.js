import { mount } from '@vue/test-utils'
import RetryTaskDetail from './RetryTaskDetail.vue'
import tasks from '../../test/fixtures/retry-tasks.json'

function render(detail = tasks.detail, props = {}) { return mount(RetryTaskDetail, { props: { state: 'SUCCESS', detail, canExecute: true, ...props } }) }
function button(wrapper, label) { return wrapper.findAll('button').find(item => item.text() === label) }
function executionLines(wrapper) { return wrapper.findAll('.retry-task-detail__execution p').map(item => item.text()) }

describe('RetryTaskDetail', () => {
  it('shows current reasons separately and preserves the original interval after partial retry', async () => {
    const wrapper = render()
    expect(wrapper.findAll('.retry-task-detail__item')).toHaveLength(2)
    expect(wrapper.text()).toContain('SOURCE_TIMEOUT')
    expect(wrapper.text()).toContain('2026-09-01 至 2026-09-10')
    expect(wrapper.text()).toContain('无已记录的公共条件')
    await wrapper.setProps({ detail: tasks.remaining })
    expect(wrapper.findAll('.retry-task-detail__item')).toHaveLength(1)
    expect(wrapper.text()).not.toContain('2026-09-03')
    expect(wrapper.text()).toContain('2026-09-01 至 2026-09-10')
    expect(wrapper.text()).not.toContain('已完成项')
  })
  it('keeps both same-day stocks and safely renders long reasons and missing display names', () => {
    const detail = structuredClone(tasks.stocks)
    detail.pluginDisplayName = null; detail.apiDisplayName = ''
    detail.items[0].errorMessage = '<img src=x onerror=alert(1)>'
    const wrapper = render(detail)
    expect(wrapper.findAll('.retry-task-detail__item').map(item => item.text())).toEqual([expect.stringContaining('000001.SZ'), expect.stringContaining('600000.SH')])
    expect(wrapper.text()).toContain('tushare_pro')
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
    expect(wrapper.find('img').exists()).toBe(false)
  })
  it.each([['original', '不适用'], ['legacy', '未记录'], ['blocked', '原始区间未确认']])('displays %s original status without guessing', (key, label) => {
    const wrapper = render(tasks[key], { canExecute: tasks[key].canExecute })
    expect(wrapper.get('.retry-task-detail__original').text()).toContain(label)
    expect(wrapper.get('.retry-task-detail__original').text()).not.toContain('2026-09-03')
  })
  it('shows REQUEST conditions, month, native range and original time semantics', () => {
    const detail = structuredClone(tasks.original)
    detail.items = [
      { ...detail.items[0], timeType: 'MONTH', timeValue: '2026-09' },
      { ...detail.items[0], timeType: 'RANGE', timeValue: '2026-09-01/2026-09-10' },
      detail.items[0],
    ]
    const wrapper = render(detail)
    expect(wrapper.text()).toContain('list_status：L')
    expect(wrapper.text()).toContain('2026-09（完整月份）')
    expect(wrapper.text()).toContain('2026-09-01 至 2026-09-10')
    expect(wrapper.text()).toContain('原条件请求')
  })
  it('executes directly, explains range permanence and guards both local and server permission', async () => {
    const wrapper = render()
    expect(wrapper.text()).toContain('区间下载开始后不可终止。')
    await button(wrapper, '重试一次').trigger('click')
    expect(wrapper.emitted('execute')).toEqual([[]])
    await wrapper.setProps({ canExecute: false, needsRefresh: true, executionMessage: '记录可能已变化，请刷新详情' })
    expect(button(wrapper, '重试一次').attributes('disabled')).toBeDefined()
    await button(wrapper, '刷新详情').trigger('click')
    expect(wrapper.emitted('refresh')).toEqual([[]])
    expect(wrapper.text()).toContain('记录可能已变化')
    await wrapper.setProps({ disabled: true })
    expect(button(wrapper, '刷新详情').attributes('disabled')).toBeDefined()
  })
  it('shows supplied executable-range guidance exactly once', () => {
    const message = '区间下载开始后不可终止。'
    const wrapper = render(tasks.detail, { executionMessage: message })
    expect(executionLines(wrapper)).toEqual([message])
    expect(button(wrapper, '重试一次').attributes('disabled')).toBeUndefined()
    expect(button(wrapper, '刷新详情').attributes('disabled')).toBeUndefined()
  })
  it('shows blocker guidance once with its code and keeps refresh available', () => {
    const { message, code } = tasks.blocked.executionBlocker
    const wrapper = render(tasks.blocked, { canExecute: false, executionMessage: message })
    expect(executionLines(wrapper)).toEqual([`${message}（${code}）`])
    expect(button(wrapper, '重试一次').attributes('disabled')).toBeDefined()
    expect(button(wrapper, '刷新详情').attributes('disabled')).toBeUndefined()
  })
  it.each([
    ['server retrying', { detail: { ...tasks.detail, retrying: true, canExecute: false, executionBlocker: { code: 'DOWNLOAD_BUSY', message: '已有下载正在执行。' } }, canExecute: false, executionMessage: '已有下载正在执行。' }, ['已有下载正在执行。（DOWNLOAD_BUSY）', '当前任务正在重试。'], false],
    ['refresh required', { needsRefresh: true, canExecute: false, executionMessage: '记录可能已变化，请刷新详情。' }, ['记录可能已变化，请刷新详情。'], false],
    ['first download busy', { canExecute: false, executionMessage: '已有下载正在执行。' }, ['已有下载正在执行。'], false],
  ])('prioritizes %s guidance without generic range permanence', (name, props, expected, refreshDisabled) => {
    const { detail = tasks.detail, ...componentProps } = props
    const wrapper = render(detail, componentProps)
    expect(executionLines(wrapper)).toEqual(expected)
    expect(wrapper.text()).not.toContain('区间下载开始后不可终止。')
    expect(button(wrapper, '重试一次').attributes('disabled')).toBeDefined()
    expect(button(wrapper, '刷新详情').attributes('disabled') !== undefined).toBe(refreshDisabled)
  })
  it('shows all blocked records with the server cause and no execution', () => {
    const wrapper = render(tasks.blocked, { canExecute: false })
    expect(wrapper.findAll('.retry-task-detail__item')).toHaveLength(2)
    expect(wrapper.text()).toContain('RETRY_TASK_INVALID')
    expect(button(wrapper, '重试一次').attributes('disabled')).toBeDefined()
  })
  it('announces invalid, loading, missing and read failure independently', async () => {
    const wrapper = render(null, { state: 'INITIAL', canExecute: false })
    expect(wrapper.text()).toContain('选择失败任务查看详情')
    await wrapper.setProps({ state: 'LOADING' })
    expect(wrapper.get('[role="status"]').text()).toContain('正在加载')
    await wrapper.setProps({ state: 'NOT_FOUND', error: { message: '任务不存在；查不到任务不代表某次无响应下载成功。' } })
    expect(wrapper.text()).toContain('查不到任务不代表')
    expect(button(wrapper, '重试一次')).toBeUndefined()
    await wrapper.setProps({ state: 'FAILURE', error: { message: '任务标识无效' } })
    expect(wrapper.get('[role="alert"]').text()).toContain('任务标识无效')
  })
})
