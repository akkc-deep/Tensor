<script setup>
import { reactive } from 'vue'
import AsyncStatePanel from '../common/AsyncStatePanel.vue'
import WorkbenchPanel from '../common/WorkbenchPanel.vue'
import { formatIngestedAt } from '../../utils/format.js'

const props = defineProps({
  state: { type: String, required: true }, result: { type: Object, default: null },
  error: { type: Object, default: null }, selectedTaskId: { type: String, default: null },
  page: { type: Number, default: 1 }, pageSize: { type: Number, default: 20 },
  disabled: { type: Boolean, default: false },
})
const emit = defineEmits(['filter', 'page', 'page-size', 'refresh', 'select'])
const filters = reactive({ pluginId: '', apiName: '' })
const errors = reactive({ pluginId: '', apiName: '' })
const fields = [['pluginId', '插件标识'], ['apiName', '接口标识']]
const inputs = {}
function inputRef(key, input) { inputs[key] = input }
function filter() {
  if (props.disabled) return
  const values = {}
  for (const [key] of fields) {
    const value = filters[key].trim()
    errors[key] = value && !/^[a-z][a-z0-9_]{1,63}$/.test(value) ? '请输入有效标识（小写字母开头，2～64 位字母、数字或下划线）。' : ''
    if (value) values[key] = value
  }
  const firstInvalid = fields.find(([key]) => errors[key])
  if (firstInvalid) inputs[firstInvalid[0]]?.focus()
  else emit('filter', values)
}
function action(event, value) { if (!props.disabled) emit(event, value) }
function date(value) { return `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6)}` }
function original(task) {
  return task.originalDateRangeStatus === 'RECORDED'
    ? `${date(task.originalDateRange.startDate)} 至 ${date(task.originalDateRange.endDate)}`
    : { NOT_APPLICABLE: '不适用', NOT_RECORDED: '未记录', UNCONFIRMED: '原始区间未确认' }[task.originalDateRangeStatus]
}
function time(scope) {
  if (scope.timeType === 'NONE') return '原条件请求'
  if (scope.timeType === 'MONTH') return `${scope.timeValue}（完整月份）`
  return scope.timeValue.replace('/', ' 至 ')
}
function scopeKey(scope) { return JSON.stringify([scope.targetType, scope.targetValue, scope.timeType, scope.timeValue]) }
</script>

<template>
  <WorkbenchPanel class="retry-task-list" heading-id="retry-list-title" title="失败任务">
    <form class="retry-task-list__filters" @submit.prevent="filter">
      <div v-for="[key, label] in fields" :key="key">
        <label :for="`retry-filter-${key}`">{{ label }}</label>
        <el-input :id="`retry-filter-${key}`" :ref="input => inputRef(key, input)" v-model="filters[key]" :name="key" :disabled="disabled" :spellcheck="false"
          :aria-invalid="Boolean(errors[key])" :aria-describedby="errors[key] ? `retry-error-${key}` : undefined" />
        <p v-if="errors[key]" :id="`retry-error-${key}`" class="retry-task-list__error" role="alert">{{ errors[key] }}</p>
      </div>
      <div class="retry-task-list__actions">
        <el-button native-type="submit" :disabled="disabled">筛选</el-button>
        <el-button native-type="button" :disabled="disabled" @click="action('refresh')">刷新列表</el-button>
      </div>
    </form>
    <ul v-if="state === 'SUCCESS'" class="retry-task-list__items" aria-label="当前失败任务">
      <li v-for="task in result.items" :key="task.taskId" :aria-current="selectedTaskId === task.taskId ? 'true' : undefined">
        <h3>{{ task.apiDisplayName?.trim() || task.apiName }}</h3>
        <p>{{ task.pluginDisplayName?.trim() || task.pluginId }} · {{ task.pluginId }} / {{ task.apiName }}</p>
        <p class="retry-task-list__id">任务 ID：{{ task.taskId }}</p>
        <dl>
          <div><dt>原始下载区间</dt><dd>{{ original(task) }}</dd></div>
          <div><dt>当前失败范围</dt><dd><ul>
            <li v-for="scope in task.failedScopes" :key="scopeKey(scope)" class="retry-task-list__scope">{{ scope.targetType === 'STOCK' ? scope.targetValue : '原请求条件' }} · {{ time(scope) }}</li>
          </ul></dd></div>
          <div><dt>当前失败项</dt><dd>{{ task.failedItemCount }}</dd></div>
          <div><dt>创建时间（北京时间）</dt><dd>{{ formatIngestedAt(task.createdAt) }}</dd></div>
          <div><dt>更新时间（北京时间）</dt><dd>{{ formatIngestedAt(task.updatedAt) }}</dd></div>
        </dl>
        <el-button native-type="button" :disabled="disabled" @click="action('select', task.taskId)">查看详情</el-button>
      </li>
    </ul>
    <AsyncStatePanel v-else-if="state === 'LOADING'" state="LOADING" title="正在加载失败任务" message="请稍候。" />
    <AsyncStatePanel v-else-if="state === 'FAILURE'" state="FAILURE" title="失败任务加载失败"
      :message="error?.message || '未能读取当前记录。'" :request-id="error?.requestId || ''"
      :retry-label="disabled ? '' : '重新加载'" @retry="action('refresh')" />
    <AsyncStatePanel v-else-if="state === 'EMPTY'" state="EMPTY" title="暂无失败任务" message="当前筛选条件下没有已保存的失败记录。" />
    <AsyncStatePanel v-else state="INITIAL" title="查看当前失败任务" message="读取实际保存的失败记录。" />
    <nav class="retry-task-list__pagination" aria-label="失败任务分页" :aria-disabled="disabled">
      <span role="status" aria-live="polite">共 {{ result?.totalElements ?? 0 }} 项，第 {{ page }} / {{ result?.totalPages ?? 0 }} 页</span>
      <el-pagination :current-page="page" :page-size="pageSize" :page-count="result?.totalPages ?? page" :page-sizes="[20, 50, 100]"
        :pager-count="5" layout="sizes, prev, pager, next" prev-text="上一页" next-text="下一页" :disabled="disabled || !result"
        @update:current-page="value => value !== page && action('page', value)" @update:page-size="value => action('page-size', value)" />
    </nav>
  </WorkbenchPanel>
</template>

<style scoped>
.retry-task-list { min-width: 0; overflow-wrap: anywhere; }
.retry-task-list__filters { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; padding: 24px; }
.retry-task-list__filters label { display: block; margin-bottom: 8px; font-size: 13px; }
.retry-task-list__actions { grid-column: 1 / -1; display: flex; flex-wrap: wrap; gap: 12px; }
.retry-task-list__actions :deep(.el-button + .el-button) { margin-left: 0; }
.retry-task-list__error { color: var(--tensor-error); font-size: 12px; line-height: 1.6; }
.retry-task-list__items { list-style: none; margin: 0; padding: 0; }
.retry-task-list__items > li { padding: 24px; border-top: 1px solid var(--tensor-line); }
.retry-task-list__items > li[aria-current] { background: var(--tensor-raised); }
.retry-task-list h3 { margin: 0 0 8px; font-size: 16px; font-weight: 550; }
.retry-task-list p { font-size: 13px; line-height: 1.7; margin: 6px 0; }
.retry-task-list__id, .retry-task-list dt { color: var(--tensor-muted); }
.retry-task-list dl { display: grid; gap: 14px; margin: 20px 0; font-size: 13px; line-height: 1.6; }
.retry-task-list dd { margin: 4px 0 0; font-variant-numeric: tabular-nums; }
.retry-task-list dd ul { padding-left: 18px; }
.retry-task-list__scope + .retry-task-list__scope { margin-top: 6px; }
.retry-task-list__pagination { display: flex; flex-wrap: wrap; gap: 12px; padding: 24px; border-top: 1px solid var(--tensor-line); font-size: 12px; }
.retry-task-list__pagination :deep(.el-pagination) { display: flex; flex-wrap: wrap; min-width: 0; }
.retry-task-list :deep(.el-button:focus-visible) { outline: 3px solid var(--tensor-interactive-color); outline-offset: 3px; }
@media (max-width: 680px) {
  .retry-task-list__filters, .retry-task-list__items > li, .retry-task-list__pagination { padding: 18px; }
  .retry-task-list__filters { grid-template-columns: minmax(0, 1fr); }
  .retry-task-list__pagination :deep(.el-pagination__sizes) { flex: 1 0 100%; margin-right: 0; }
}
</style>
