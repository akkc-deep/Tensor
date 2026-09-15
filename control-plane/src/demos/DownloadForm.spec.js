import { mount } from '@vue/test-utils'
import { afterEach, expect, it, vi } from 'vitest'
import DownloadForm from './DownloadForm.vue'
import { createDemo, demoKey } from './demoState.js'

let wrapper, demo
afterEach(() => { wrapper?.unmount(); demo?.dispose(); vi.useRealTimers() })
function render() {
  vi.useFakeTimers()
  demo = createDemo()
  wrapper = mount(DownloadForm, { global: { provide: { [demoKey]: demo } }, attachTo: document.body })
  return wrapper
}

it('switches to a range and submits those dates with the selected stock', async () => {
  render()
  await wrapper.get('[data-mode="RANGE"]').trigger('click')
  await wrapper.get('input[name="start_date"]').setValue('2026-07-01')
  await wrapper.get('input[name="end_date"]').setValue('2026-08-07')
  await wrapper.get('form').trigger('submit')
  expect(demo.tasks[0]).toMatchObject({ mode: 'RANGE', params: { ts_code: '000001.SZ', start_date: '2026-07-01', end_date: '2026-08-07' } })
  expect(demo.tasks[0].batches).toHaveLength(2)
})

it('shows a reversed-range error and keeps invalid work out of the task list', async () => {
  render()
  await wrapper.get('[data-mode="RANGE"]').trigger('click')
  await wrapper.get('input[name="start_date"]').setValue('2026-09-01')
  expect(wrapper.get('[role="alert"]').text()).toContain('开始日期不能晚于结束日期')
  await wrapper.get('form').trigger('submit')
  expect(demo.tasks).toHaveLength(5)
})
