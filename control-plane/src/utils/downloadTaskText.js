const STATUS_LABELS = {
  QUEUED: '排队中', RUNNING: '运行中', SUCCEEDED: '已成功',
  PARTIAL_FAILED: '部分失败', FAILED: '失败', INTERRUPTED: '已中断',
}

export function taskStatusLabel(task) {
  if (task.extraction?.ruleKind === 'RESPONSE_ONLY' && task.status === 'SUCCEEDED') {
    return task.counts.sourceRows > 0n ? '返回记录已采集' : '本次请求未返回记录'
  }
  return STATUS_LABELS[task.status]
}

export function taskExtractionNotice(task) {
  if (task.extraction?.ruleKind === 'RESPONSE_ONLY') return '数据完整性未确认，可能存在上游截断'
  if (task.extraction?.ruleKind === 'UNKNOWN') return '数据完整性未确认'
  return ''
}
