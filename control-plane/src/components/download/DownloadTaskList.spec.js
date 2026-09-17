import { flushPromises, mount as mountComponent } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ElButton, ElPagination, ElSelect } from 'element-plus'
import { nextTick } from 'vue'

import { ClientError } from '../../api/errors.js'
import AsyncStatePanel from '../common/AsyncStatePanel.vue'
import DownloadTaskList from './DownloadTaskList.vue'

function mount(component, options = {}) {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/:pathMatch(.*)*', component: { template: '<div />' } },
  ] })
  return mountComponent(component, { ...options, global: { plugins: [router] } })
}

function task(overrides = {}) {
  return Object.freeze({
    taskId: '11111111-1111-4111-8111-111111111111',
    submissionId: '22222222-2222-4222-8222-222222222222',
    pluginId: 'tushare_pro',
    apiName: 'daily',
    mode: 'SINGLE',
    extraction: null,
    params: Object.freeze({ trade_date: '20260912', ts_code: '000001.SZ' }),
    status: 'QUEUED',
    version: 1n,
    planReady: true,
    counts: Object.freeze({
      totalBatches: 4n,
      pendingBatches: 1n,
      runningBatches: 1n,
      succeededBatches: 1n,
      failedBatches: 1n,
      splitBatches: 9007199254740993n,
      sourceRows: 0n,
      insertedRows: 9223372036854775807n,
      updatedRows: 9007199254740992n,
    }),
    lastError: null,
    canRetry: false,
    canResume: false,
    requestCount: 1n,
    runRequestCount: 1n,
    createdAt: '2026-09-12T00:00:00Z',
    updatedAt: '2026-09-12T01:02:03Z',
    queuedAt: '2026-09-12T00:00:00Z',
    startedAt: null,
    finishedAt: null,
    deadlineAt: '2026-09-12T02:00:00Z',
    ...overrides,
  })
}

function result(items = [], overrides = {}) {
  return Object.freeze({
    page: 1,
    pageSize: 20,
    total: BigInt(items.length),
    items: Object.freeze(items),
    ...overrides,
  })
}

describe('DownloadTaskList', () => {
  it('renders historical parameters, dynamic counts, full bigint writes, times, and the exact task path', () => {
    const row = task()
    const wrapper = mount(DownloadTaskList, {
      props: { page: 1, pageSize: 20, result: result([row]) },
    })

    expect(wrapper.get('h2').text()).toContain('最近任务')
    expect(wrapper.find('table').exists()).toBe(false)
    expect(wrapper.find('details').exists()).toBe(true)
    expect(wrapper.get('.task-name').text()).toContain('daily')
    expect(wrapper.get('.task-name > span').text()).toBe('tushare_pro')
    expect(wrapper.text()).toContain('trade_date=20260912')
    expect(wrapper.text()).toContain('ts_code=000001.SZ')
    expect(wrapper.text()).toContain('单次请求')
    expect(wrapper.text()).toContain('排队中')
    expect(wrapper.text()).toContain('已结束 2 / 当前计划 4 批')
    expect(wrapper.text()).toContain('成功 1')
    expect(wrapper.text()).toContain('失败 1')
    expect(wrapper.text()).toContain('待执行 1')
    expect(wrapper.text()).toContain('运行中 1')
    expect(wrapper.text()).toContain('独立拆分 9007199254740993')
    expect(wrapper.text()).toContain('新增记录次数 9223372036854775807')
    expect(wrapper.text()).toContain('更新记录次数 9007199254740992')
    expect(wrapper.text()).toContain('已提交写入操作次数，不是整段去重总量')
    expect(wrapper.get('time[datetime="2026-09-12T00:00:00Z"]')).toBeTruthy()
    expect(wrapper.get('time[datetime="2026-09-12T01:02:03Z"]')).toBeTruthy()
    expect(wrapper.get('a').attributes('href')).toBe(
      '/downloads/tasks/11111111-1111-4111-8111-111111111111',
    )
    expect(wrapper.get('a').attributes('aria-label')).toBe('查看任务 daily')
  })

  it('shows controlled group tabs and the server count without filtering current rows', async () => {
    const wrapper = mount(DownloadTaskList, { props: { statusGroup: 'ACTIVE', result: result([task()], { total: 42n }) } })
    const buttons = wrapper.findAll('.task-filters button')
    expect(buttons.map(button => button.text())).toEqual(['全部', '进行中', '已完成', '需处理'])
    expect(buttons[1].attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-total]').text()).toContain('进行中 · 共 42 个任务')
    expect(wrapper.findAll('article')).toHaveLength(1)
    await buttons[3].trigger('click')
    expect(wrapper.emitted('update:statusGroup')).toEqual([['ERROR']])
    expect(buttons[1].attributes('aria-pressed')).toBe('true')
  })

  it('keeps errors and completeness readable without opening technical details', () => {
    const wrapper = mount(DownloadTaskList, { props: { result: result([task({ status: 'FAILED',
      lastError: { code: 'SOURCE_TIMEOUT', message: '数据源请求超时' },
      extraction: { ruleKind: 'RESPONSE_ONLY', policyVersion: 'v1' },
    })]) } })
    expect(wrapper.get('.task-error').text()).toContain('数据源请求超时')
    expect(wrapper.get('.task-notice').text()).toContain('数据完整性未确认')
    expect(wrapper.get('.task-error').element.closest('details')).toBeNull()
  })

  it.each([
    ['QUEUED', '排队中'],
    ['RUNNING', '运行中'],
    ['SUCCEEDED', '已成功'],
    ['PARTIAL_FAILED', '部分失败'],
    ['FAILED', '失败'],
    ['INTERRUPTED', '已中断'],
  ])('keeps the %s status visible as text', (status, label) => {
    const wrapper = mount(DownloadTaskList, {
      props: {
        result: result([task({ status })]),
      },
    })

    expect(wrapper.get('[data-task-status]').text()).toBe(label)
  })

  it.each([['FAILED', '查看失败原因'], ['PARTIAL_FAILED', '查看失败原因'], ['INTERRUPTED', '执行已中断']])('does not imply execution continues for unplanned %s tasks', (status, hint) => {
    const wrapper = mount(DownloadTaskList, { props: { result: result([task({ status, planReady: false })]) } })
    expect(wrapper.get('.task-trailing').text()).toContain(hint)
    expect(wrapper.get('.task-trailing').text()).not.toContain('进度自动更新')
  })

  it('reports an unplanned zero-batch task without inferring completion', () => {
    const wrapper = mount(DownloadTaskList, {
      props: {
        result: result([
          task({
            status: 'RUNNING',
            planReady: false,
            counts: Object.freeze({
              ...task().counts,
              totalBatches: 0n,
              pendingBatches: 0n,
              runningBatches: 0n,
              succeededBatches: 0n,
              failedBatches: 0n,
            }),
          }),
        ]),
      },
    })

    expect(wrapper.text()).toContain('尚未生成计划')
    expect(wrapper.text()).not.toContain('已结束 0 / 当前计划 0 批')
  })

  it('derives the capped page count with bigint and emits controlled refresh and pagination events', async () => {
    const total = 9223372036854775807n
    const wrapper = mount(DownloadTaskList, {
      props: {
        page: 2,
        pageSize: 20,
        result: result([task()], { page: 2, total }),
        loading: false,
      },
    })
    const pagination = wrapper.getComponent(ElPagination)

    expect(wrapper.get('nav').attributes('aria-label')).toBe('近期任务分页')
    expect(wrapper.get('[data-total]').text()).toContain(total.toString())
    expect(pagination.props()).toMatchObject({
      currentPage: 2,
      pageSize: 20,
      pageCount: 2147483647,
      pageSizes: [20, 50, 100],
      prevText: '上一页',
      nextText: '下一页',
    })

    await wrapper.get('[data-refresh]').trigger('click')
    pagination.vm.$emit('update:current-page', 3)
    pagination.vm.$emit('update:page-size', 50)

    expect(wrapper.emitted('refresh')).toEqual([[]])
    expect(wrapper.emitted('update:page')).toEqual([[3]])
    expect(wrapper.emitted('update:pageSize')).toEqual([[50]])
  })

  it('keeps refresh and pagination operable during a background request', async () => {
    const wrapper = mount(DownloadTaskList, {
      props: {
        page: 2,
        result: result([task()], { page: 2, total: 60n }),
        loading: true,
      },
    })
    const pagination = wrapper.getComponent(ElPagination)

    expect(wrapper.get('[data-refresh]').attributes('disabled')).toBeUndefined()
    expect(wrapper.get('nav').attributes('aria-busy')).toBe('true')
    expect(pagination.props('disabled')).toBe(false)

    await wrapper.get('[data-refresh]').trigger('click')
    pagination.vm.$emit('update:current-page', 3)
    pagination.vm.$emit('update:page-size', 50)

    expect(wrapper.emitted('refresh')).toEqual([[]])
    expect(wrapper.emitted('update:page')).toEqual([[3]])
    expect(wrapper.emitted('update:pageSize')).toEqual([[50]])
  })

  it('keeps a successful page visible when refresh fails and exposes the last update', () => {
    const lastUpdatedAt = new Date('2026-09-12T02:03:04Z')
    const wrapper = mount(DownloadTaskList, {
      props: {
        result: result([task({ status: 'FAILED' })]),
        error: new ClientError('NETWORK', 'list-request'),
        lastUpdatedAt,
      },
    })

    expect(wrapper.find('article').exists()).toBe(true)
    expect(wrapper.text()).toContain('状态暂时无法更新')
    expect(wrapper.text()).toContain('上次更新')
    expect(wrapper.text()).toContain('失败')
    expect(wrapper.text()).not.toContain('任务状态：失败')
  })

  it('shows initial loading and failure panels with a working manual retry', async () => {
    const loading = mount(DownloadTaskList, { props: { loading: true } })
    expect(loading.getComponent(AsyncStatePanel).props()).toMatchObject({
      state: 'LOADING',
      title: '正在加载近期任务',
    })
    loading.unmount()

    const failed = mount(DownloadTaskList, {
      props: { error: new ClientError('NETWORK', 'list-request') },
    })
    expect(failed.getComponent(AsyncStatePanel).props()).toMatchObject({
      state: 'FAILURE',
      title: '近期任务加载失败',
      requestId: 'list-request',
    })
    const retry = failed
      .findAllComponents(ElButton)
      .find((button) => button.text() === '重新加载')
    await retry.get('button').trigger('click')
    expect(failed.emitted('refresh')).toEqual([[]])
  })

  it('explains the browser precision requirement when JSON number sources are unavailable', () => {
    const nativeParse = JSON.parse
    vi.spyOn(JSON, 'parse').mockImplementation((text, reviver) => {
      if (text === '{"n":1}' && reviver) return reviver('n', 1, undefined)
      return nativeParse(text, reviver)
    })

    const wrapper = mount(DownloadTaskList, {
      props: { error: new ClientError('INVALID_RESPONSE', 'list-request') },
    })

    expect(wrapper.text()).toContain('当前浏览器无法保真处理任务数值，请更新浏览器后重试。')
  })

  it('shows a true empty list and lets an over-tail empty page return to page one', async () => {
    const empty = mount(DownloadTaskList, {
      props: { page: 1, pageSize: 20, result: result([]) },
    })
    expect(empty.text()).toContain('暂无近期任务')
    expect(empty.get('[data-total]').text()).toBe('全部 · 共 0 个任务')
    expect(empty.getComponent(ElPagination).props('pageCount')).toBe(0)
    empty.unmount()

    const overTail = mount(DownloadTaskList, {
      props: {
        page: 7,
        pageSize: 20,
        result: result([], { page: 7, total: 3n }),
      },
    })
    expect(overTail.text()).toContain('本页暂无任务')
    await overTail.get('[data-first-page]').trigger('click')
    expect(overTail.emitted('update:page')).toEqual([[1]])
  })

  it.each([3n, 0n])(
    'suppresses Element Plus auto-clamping when an over-tail refresh reports total %s',
    async (total) => {
    const wrapper = mount(DownloadTaskList, {
      props: {
        page: 7,
        pageSize: 20,
        result: result([task()], { page: 7, total: 140n }),
      },
    })

    await wrapper.setProps({
      result: result([], { page: 7, total }),
    })
    await nextTick()

    expect(wrapper.text()).toContain('本页暂无任务')
    expect(wrapper.emitted('update:page')).toBeUndefined()

    await wrapper.get('[data-first-page]').trigger('click')
    expect(wrapper.emitted('update:page')).toEqual([[1]])
    },
  )

  it('still forwards real next-page and page-size interactions', async () => {
    const wrapper = mount(DownloadTaskList, {
      attachTo: document.body,
      props: {
        page: 2,
        pageSize: 20,
        result: result([task()], { page: 2, total: 60n }),
      },
    })

    try {
      await wrapper.get('.btn-next').trigger('click')
      expect(wrapper.emitted('update:page')).toEqual([[3]])

      const input = wrapper.get('input[role="combobox"]')
      await input.trigger('click')
      await flushPromises()
      const options = [...document.body.querySelectorAll('.el-select-dropdown__item')]
      await options.find((option) => option.textContent.trim() === '50条/页').click()
      await flushPromises()

      expect(wrapper.props('pageSize')).toBe(20)
      expect(wrapper.emitted('update:pageSize')).toEqual([[50]])
    } finally {
      wrapper.unmount()
    }
  })
})

it.each([
  ['QUEUED', 0n, '排队中'], ['RUNNING', 2n, '运行中'],
  ['SUCCEEDED', 2n, '返回记录已采集'], ['SUCCEEDED', 0n, '本次请求未返回记录'],
  ['FAILED', 2n, '失败'], ['PARTIAL_FAILED', 2n, '部分失败'], ['INTERRUPTED', 2n, '已中断'],
])('keeps saved response-only caveat on list %s with %s rows', (status, sourceRows, label) => {
  const row = task({ mode: 'RANGE', status, extraction: { policyVersion: 'saved-v1', ruleKind: 'RESPONSE_ONLY' },
    counts: { ...task().counts, sourceRows } })
  const wrapper = mount(DownloadTaskList, { props: { result: result([row]) } })
  expect(wrapper.text()).toContain('数据完整性未确认，可能存在上游截断')
  expect(wrapper.text()).toContain('saved-v1')
  expect(wrapper.text()).toContain('RESPONSE_ONLY')
  expect(wrapper.get('.download-task-list__status').text()).toBe(label)
  if (status !== 'SUCCEEDED') {
    expect(wrapper.text()).not.toContain('返回记录已采集')
    expect(wrapper.text()).not.toContain('本次请求未返回记录')
  }
  wrapper.unmount()
})

it('updates the list explanation from the next saved task snapshot', async () => {
  const wrapper = mount(DownloadTaskList, { props: { result: result([task({ mode: 'RANGE',
    extraction: { policyVersion: 'old-v1', ruleKind: 'UNKNOWN' } })]) } })
  expect(wrapper.text()).toContain('数据完整性未确认')
  expect(wrapper.text()).not.toContain('可能存在上游截断')
  await wrapper.setProps({ result: result([task({ mode: 'RANGE',
    extraction: { policyVersion: 'old-v2', ruleKind: 'CONFIRMED_ROW_LIMIT' } })]) })
  expect(wrapper.text()).toContain('old-v2')
  expect(wrapper.text()).not.toContain('数据完整性未确认')
  wrapper.unmount()
})

it('offers independent server permissions, emits the task and blocks stale or busy shortcuts', async () => {
  const row = task({ status: 'PARTIAL_FAILED', canRetry: true, canResume: true })
  const wrapper = mount(DownloadTaskList, { props: { result: result([row]) } })
  const retry = wrapper.get('[data-quick-retry]')
  const resume = wrapper.get('[data-quick-resume]')
  await retry.trigger('click')
  expect(wrapper.emitted('control')).toEqual([[row, 'retry']])
  await wrapper.setProps({ error: new ClientError('NETWORK') })
  expect(retry.element.disabled).toBe(true)
  expect(resume.element.disabled).toBe(true)
  await wrapper.setProps({ error: null, actionBusy: true })
  expect(retry.element.disabled).toBe(true)
  await wrapper.setProps({ actionBusy: false, result: result([task({ status: 'FAILED' })]) })
  expect(wrapper.find('[data-quick-retry]').exists()).toBe(false)
  wrapper.unmount()
})
