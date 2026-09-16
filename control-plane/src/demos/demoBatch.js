// Local capability snapshot of TushareBatchPolicies.java, 2026-09-16.
const axes = Object.fromEntries([
  ['交易日期', 'daily weekly monthly adj_factor daily_basic stk_limit suspend_d moneyflow margin margin_detail block_trade slb_len slb_sec slb_sec_detail top_list'],
  ['公告日期', 'income balancesheet cashflow fina_audit forecast express repurchase stk_managers stk_holdernumber stk_holdertrade pledge_detail dividend'],
  ['报告期', 'fina_indicator fina_mainbz top10_holders top10_floatholders'],
  ['日历日期', 'trade_cal'], ['上网发行日期', 'new_share'], ['最新披露公告日', 'disclosure_date'],
].flatMap(([label, ids]) => ids.split(' ').map(id => [id, label])))
const excluded = new Set(['balancesheet', 'cashflow', 'repurchase', 'fina_indicator'])
const responseOnly = new Set(['adj_factor', 'suspend_d', 'income', 'fina_audit', 'express', 'stk_managers', 'top10_holders', 'top10_floatholders'])

export function rangeCapability(id) {
  return { availability: excluded.has(id) ? 'NEEDS_VERIFICATION' : axes[id] ? 'AVAILABLE' : 'UNSUPPORTED', dateLabel: axes[id], responseOnly: responseOnly.has(id) }
}

// Preview-only monthly slices; real task planning belongs to the backend.
export function planDemoBatches(start, end) {
  const valid = value => /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(`${value}T00:00:00Z`)) && new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value
  if (!valid(start) || !valid(end)) throw new Error('请填写有效的开始和结束日期。')
  if (start > end) throw new Error('开始日期不能晚于结束日期，请重新选择。')
  const batches = []
  let cursor = start
  while (cursor <= end) {
    if (batches.length === 120) throw new Error('本地演示最多支持 120 个按月划分的批次，请缩短日期范围。')
    const next = new Date(`${cursor}T00:00:00Z`)
    next.setUTCMonth(next.getUTCMonth() + 1, 1)
    const monthEnd = new Date(next.getTime() - 86400000).toISOString().slice(0, 10)
    batches.push({ id: `batch-${String(batches.length + 1).padStart(3, '0')}`, start: cursor, end: monthEnd < end ? monthEnd : end, status: 'QUEUED', rows: 0, attempts: 0 })
    if (batches.at(-1).end === end) break
    cursor = next.toISOString().slice(0, 10)
  }
  return batches
}
