import { mount } from '@vue/test-utils'
import { ElPagination } from 'element-plus'
import RetryTaskList from './RetryTaskList.vue'
import tasks from '../../test/fixtures/retry-tasks.json'

function render(props = {}) { return mount(RetryTaskList, { props: { state: 'SUCCESS', result: tasks.page, ...props } }) }
function button(wrapper, label) { return wrapper.findAll('button').find(item => item.text() === label) }

describe('RetryTaskList', () => {
  it('keeps original range and sparse current scopes distinct with saved identity and Beijing times', () => {
    const wrapper = render()
    expect(wrapper.text()).toContain('原始下载区间')
    expect(wrapper.text()).toContain('2026-09-01 至 2026-09-10')
    expect(wrapper.text()).toContain('当前失败范围')
    expect(wrapper.findAll('.retry-task-list__scope').map(item => item.text())).toEqual(['原请求条件 · 2026-09-03', '原请求条件 · 2026-09-07'])
    expect(wrapper.text()).toContain('2026-09-09 10:00:00')
    expect(wrapper.text()).toContain('北京时间')
    expect(wrapper.text()).not.toContain('已完成项')
    expect(wrapper.getComponent(ElPagination).props()).toMatchObject({ pageSize: 20, pageSizes: [20, 50, 100] })
  })
  it.each([['pluginId', 'offline_plugin'], ['apiName', 'offline_api']])('applies independent %s and trims blanks', async (name, value) => {
    const wrapper = render()
    await wrapper.get(`[name="${name}"]`).setValue(` ${value} `)
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('filter')).toEqual([[{ [name]: value }]])
  })
  it('rejects invalid identifiers inline and does not emit a GET action', async () => {
    const wrapper = render()
    await wrapper.get('[name="pluginId"]').setValue('bad/value')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('[role="alert"]').text()).toContain('标识')
    expect(wrapper.emitted('filter')).toBeUndefined()
    expect(wrapper.get('[name="pluginId"]').attributes('aria-invalid')).toBe('true')
  })
  it('focuses the first invalid submitted identifier and disables native spellcheck', async () => {
    const wrapper = mount(RetryTaskList, {
      attachTo: document.body,
      props: { state: 'SUCCESS', result: tasks.page },
    })
    const pluginInput = wrapper.get('[name="pluginId"]')
    const apiInput = wrapper.get('[name="apiName"]')
    expect(pluginInput.attributes('spellcheck')).toBe('false')
    expect(apiInput.attributes('spellcheck')).toBe('false')
    await pluginInput.setValue('bad/plugin')
    await apiInput.setValue('bad/api')
    await button(wrapper, '筛选').trigger('click')
    expect(document.activeElement).toBe(pluginInput.element)
    wrapper.unmount()
  })
  it('emits only deliberate selection, refresh and pagination and guards disabled handlers', async () => {
    const wrapper = render()
    await button(wrapper, '查看详情').trigger('click')
    expect(wrapper.emitted('select')).toEqual([[tasks.detail.taskId]])
    await button(wrapper, '刷新列表').trigger('click')
    expect(wrapper.emitted('refresh')).toHaveLength(1)
    wrapper.getComponent(ElPagination).vm.$emit('update:page-size', 50)
    wrapper.getComponent(ElPagination).vm.$emit('update:current-page', 2)
    expect(wrapper.emitted('page-size')).toEqual([[50]])
    expect(wrapper.emitted('page')).toEqual([[2]])
    await wrapper.setProps({ disabled: true })
    await wrapper.get('form').trigger('submit')
    wrapper.getComponent(ElPagination).vm.$emit('update:page-size', 100)
    expect(wrapper.emitted('page-size')).toHaveLength(1)
    expect(wrapper.emitted('filter')).toBeUndefined()
  })
  it('does not turn temporary missing results into page-one navigation', async () => {
    const wrapper = render({ page: 2, result: { ...tasks.page, page: 2, totalElements: 41, totalPages: 3 } })
    await wrapper.setProps({ state: 'LOADING', result: null })
    expect(wrapper.getComponent(ElPagination).props()).toMatchObject({ currentPage: 2, pageCount: 2, disabled: true })
    expect(wrapper.emitted('page')).toBeUndefined()
  })
  it('shows actual empty and failed reads separately with a GET reload', async () => {
    const wrapper = render({ state: 'EMPTY', result: tasks.empty })
    expect(wrapper.text()).toContain('暂无失败任务')
    await wrapper.setProps({ state: 'FAILURE', error: { message: '读取失败', requestId: 'read-id' } })
    expect(wrapper.text()).not.toContain('暂无失败任务')
    await button(wrapper, '重新加载').trigger('click')
    expect(wrapper.emitted('refresh')).toHaveLength(1)
  })
})
