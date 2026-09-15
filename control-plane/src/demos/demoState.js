import { computed, reactive } from 'vue'
import catalog from './catalog.json'

export const demoKey = Symbol('tensor-ui-demo')
export const statusLabels = { QUEUED: '排队中', RUNNING: '下载中', SUCCEEDED: '已完成', FAILED: '失败', PARTIAL_FAILED: '部分失败', INTERRUPTED: '已中断' }

export function createDemo() {
  const timers = new Set()
  const state = reactive({
    selectedId: 'daily', page: 'downloads', params: {}, tasks: [], detail: null,
    notice: '', busy: false, settingsColor: '',
    catalog, selected: computed(() => catalog.find(item => item.id === state.selectedId)),
  })
  function later(callback, delay) {
    const timer = setTimeout(() => { timers.delete(timer); callback() }, delay)
    timers.add(timer)
  }
  function select(id) {
    state.selectedId = id
    state.params = Object.fromEntries(state.selected.parameters.map(p => [p.name,
      p.defaultValue ?? (p.type === 'TS_CODE' ? '000001.SZ' : p.type === 'ENUM' ? p.allowedValues[0] :
        p.type === 'MONTH' ? '2026-08' : p.type.includes('DATE') ? '2026-08-07' : ''),
    ]))
  }
  function notify(message) {
    state.notice = message
    later(() => { if (state.notice === message) state.notice = '' }, 4200)
  }
  function run(task) {
    task.status = 'QUEUED'
    task.rows = 0
    later(() => { task.status = 'RUNNING' }, 700)
    later(() => {
      task.status = 'SUCCEEDED'
      task.rows = task.apiName === 'stock_basic' ? 24 : 1
      notify(`${task.name}示例任务已完成`)
    }, 3200)
  }
  function submit() {
    if (state.busy) return
    state.busy = true
    const task = reactive({
      id: `DL-${String(19 + state.tasks.length).padStart(4, '0')}`,
      apiName: state.selectedId, name: state.selected.name, params: { ...state.params },
      status: 'QUEUED', rows: 0, time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    })
    state.tasks.unshift(task)
    run(task)
    notify('已创建示例任务，可在任务列表查看进度')
    later(() => { state.busy = false }, 800)
  }
  function retry(task) {
    if (!['FAILED', 'PARTIAL_FAILED', 'INTERRUPTED'].includes(task.status)) return
    run(task)
    notify('示例任务已重新加入队列')
  }
  select('daily')
  state.tasks = [
    { id: 'DL-0018', apiName: 'daily', name: '日线行情', status: 'SUCCEEDED', rows: 1, time: '14:32', params: { ts_code: '000001.SZ', trade_date: '2026-08-07' } },
    { id: 'DL-0017', apiName: 'stock_basic', name: '股票列表', status: 'SUCCEEDED', rows: 24, time: '14:28', params: { list_status: 'L' } },
    { id: 'DL-0016', apiName: 'daily_basic', name: '每日指标', status: 'FAILED', rows: 0, time: '14:21', params: { ts_code: '000001.SZ', trade_date: '2026-08-07' } },
    { id: 'DL-0015', apiName: 'weekly', name: '周线行情', status: 'SUCCEEDED', rows: 1, time: '13:56', params: { ts_code: '000001.SZ', trade_date: '2026-08-07' } },
    { id: 'DL-0014', apiName: 'adj_factor', name: '复权因子', status: 'INTERRUPTED', rows: 0, time: '13:42', params: { ts_code: '000001.SZ', trade_date: '2026-08-07' } },
  ]
  return Object.assign(state, { select, submit, retry, notify, dispose: () => timers.forEach(clearTimeout) })
}
