import { computed, reactive } from 'vue'
import catalog from './catalog.json'
import { planDemoBatches, rangeCapability } from './demoBatch.js'

export const demoKey = Symbol('tensor-ui-demo')
export const statusLabels = { QUEUED: '排队中', RUNNING: '下载中', SUCCEEDED: '已完成', FAILED: '失败', PARTIAL_FAILED: '部分失败', INTERRUPTED: '已中断' }

export function createDemo() {
  const timers = new Set()
  const state = reactive({
    selectedId: 'daily', page: 'downloads', params: {}, tasks: [], detail: null,
    notice: '', busy: false, settingsColor: '',
    mode: 'SINGLE', range: { start: '2026-06-01', end: '2026-08-07' },
    catalog, selected: computed(() => catalog.find(item => item.id === state.selectedId)),
    rangeCapability: computed(() => rangeCapability(state.selectedId)),
    parameters: computed(() => state.mode === 'RANGE' ? state.selected.parameters.filter(p => !p.type.includes('DATE')).map(p => ({ ...p, required: true })) : state.selected.parameters),
  })
  function later(callback, delay) {
    const timer = setTimeout(() => { timers.delete(timer); callback() }, delay)
    timers.add(timer)
  }
  function select(id) {
    state.selectedId = id
    if (state.rangeCapability.availability !== 'AVAILABLE') state.mode = 'SINGLE'
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
    const pending = task.batches.filter(batch => batch.status !== 'SUCCEEDED')
    pending.forEach(batch => { batch.status = 'QUEUED'; batch.rows = 0 })
    task.rows = task.batches.reduce((sum, batch) => sum + batch.rows, 0)
    function next(index) {
      const batch = pending[index]
      if (!batch) { task.status = 'SUCCEEDED'; notify(`${task.name}示例任务已完成`); return }
      task.status = 'RUNNING'
      batch.status = 'RUNNING'
      batch.attempts++
      later(() => {
        batch.status = 'SUCCEEDED'
        batch.rows = task.apiName === 'stock_basic' ? 24 : 1
        task.rows += batch.rows
        next(index + 1)
      }, task.mode === 'RANGE' ? 1000 : 2500)
    }
    later(() => next(0), 700)
  }
  function submit() {
    if (state.busy) return
    if (state.mode === 'RANGE' && state.rangeCapability.availability !== 'AVAILABLE') throw new Error('当前接口暂不支持批量下载。')
    if (state.mode === 'RANGE' && state.selectedId === 'trade_cal' && state.params.exchange === 'BSE') throw new Error('北交所日历暂不支持批量下载，请选择 SSE 或 SZSE。')
    const params = Object.fromEntries(state.parameters.map(p => [p.name, String(state.params[p.name] ?? '').trim()]))
    for (const p of state.parameters) {
      if ((p.required && !params[p.name]) || (p.type === 'TS_CODE' && params[p.name] && !/^[0-9]{6}[.](SZ|SH|BJ)$/.test(params[p.name]))) throw new Error(`请填写有效的${p.label}。`)
    }
    const batches = state.mode === 'RANGE' ? planDemoBatches(state.range.start, state.range.end) : [{ id: 'batch-001', status: 'QUEUED', rows: 0, attempts: 0 }]
    if (state.mode === 'RANGE') Object.assign(params, { start_date: state.range.start, end_date: state.range.end })
    state.busy = true
    const task = reactive({
      id: `DL-${String(19 + state.tasks.length).padStart(4, '0')}`,
      apiName: state.selectedId, name: state.selected.name, params, batches, mode: state.mode,
      responseOnly: state.mode === 'RANGE' && state.rangeCapability.responseOnly,
      status: 'QUEUED', rows: 0, time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    })
    state.tasks.unshift(task)
    run(task)
    notify(state.mode === 'RANGE' ? `已创建批量示例任务，共 ${batches.length} 个批次` : '已创建示例任务，可在任务列表查看进度')
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
  ].map(task => ({ ...task, mode: 'SINGLE', batches: [{ id: 'batch-001', status: task.status, rows: task.rows, attempts: 1 }] }))
  for (const [index, statuses] of [[2, ['SUCCEEDED', 'FAILED', 'SUCCEEDED']], [4, ['SUCCEEDED', 'INTERRUPTED', 'QUEUED']]]) {
    const task = state.tasks[index]
    task.mode = 'RANGE'
    task.status = index === 2 ? 'PARTIAL_FAILED' : 'INTERRUPTED'
    task.params = { ts_code: task.params.ts_code, start_date: '2026-06-01', end_date: '2026-08-07' }
    task.responseOnly = rangeCapability(task.apiName).responseOnly
    task.batches = planDemoBatches(task.params.start_date, task.params.end_date).map((batch, i) => ({ ...batch, status: statuses[i], rows: statuses[i] === 'SUCCEEDED' ? 12 : 0, attempts: statuses[i] === 'QUEUED' ? 0 : 1 }))
    task.rows = task.batches.reduce((sum, batch) => sum + batch.rows, 0)
  }
  return Object.assign(state, { select, submit, retry, notify, dispose: () => timers.forEach(clearTimeout) })
}
