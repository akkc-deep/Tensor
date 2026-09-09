<script setup>
import { computed } from 'vue'
import AsyncStatePanel from '../common/AsyncStatePanel.vue'
import WorkbenchPanel from '../common/WorkbenchPanel.vue'
import { formatIngestedAt } from '../../utils/format.js'

const props = defineProps({
  state: { type: String, required: true }, detail: { type: Object, default: null },
  error: { type: Object, default: null }, disabled: { type: Boolean, default: false },
  canExecute: { type: Boolean, default: false }, needsRefresh: { type: Boolean, default: false },
  executionMessage: { type: String, default: '' },
})
const emit = defineEmits(['refresh', 'execute'])
const conditions = computed(() => Object.entries(props.detail?.taskParams ?? {}).filter(([name]) => !['start_date', 'end_date'].includes(name)))
const rangeMode = computed(() => ['RECORDED', 'NOT_RECORDED'].includes(props.detail?.originalDateRangeStatus))
const mainGuidance = computed(() => props.executionMessage || (props.needsRefresh
  ? '记录可能已变化，请刷新详情。'
  : props.detail?.executionBlocker?.message || (rangeMode.value && props.canExecute ? '区间下载开始后不可终止。' : '')))
function date(value) { return `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6)}` }
const original = computed(() => props.detail?.originalDateRangeStatus === 'RECORDED'
  ? `${date(props.detail.originalDateRange.startDate)} 至 ${date(props.detail.originalDateRange.endDate)}`
  : { NOT_APPLICABLE: '不适用', NOT_RECORDED: '未记录', UNCONFIRMED: '原始区间未确认' }[props.detail?.originalDateRangeStatus])
function time(scope) {
  if (scope.timeType === 'NONE') return '原条件请求'
  if (scope.timeType === 'MONTH') return `${scope.timeValue}（完整月份）`
  return scope.timeValue.replace('/', ' 至 ')
}
function scopeKey(scope) { return JSON.stringify([scope.targetType, scope.targetValue, scope.timeType, scope.timeValue]) }
function refresh() { if (!props.disabled) emit('refresh') }
function execute() { if (!props.disabled && props.canExecute && !props.needsRefresh && props.state === 'SUCCESS' && !props.detail?.retrying) emit('execute') }
</script>

<template>
  <WorkbenchPanel class="retry-task-detail" heading-id="retry-detail-title" title="任务详情">
    <div v-if="state === 'SUCCESS' && detail" class="retry-task-detail__body">
      <h3>{{ detail.apiDisplayName?.trim() || detail.apiName }}</h3>
      <p>{{ detail.pluginDisplayName?.trim() || detail.pluginId }} · {{ detail.pluginId }} / {{ detail.apiName }}</p>
      <p>任务 ID：{{ detail.taskId }}</p>
      <dl>
        <div class="retry-task-detail__original"><dt>原始下载区间</dt><dd>{{ original }}</dd></div>
        <div><dt>公共条件</dt><dd>
          <ul v-if="conditions.length"><li v-for="[name, value] in conditions" :key="name">{{ name }}：{{ value }}</li></ul>
          <span v-else>无已记录的公共条件</span>
        </dd></div>
        <div><dt>当前失败项</dt><dd>{{ detail.failedItemCount }}</dd></div>
        <div><dt>创建时间（北京时间）</dt><dd>{{ formatIngestedAt(detail.createdAt) }}</dd></div>
        <div><dt>更新时间（北京时间）</dt><dd>{{ formatIngestedAt(detail.updatedAt) }}</dd></div>
      </dl>
      <section aria-labelledby="retry-scopes-title">
        <h3 id="retry-scopes-title">当前失败范围</h3>
        <ul class="retry-task-detail__items">
          <li v-for="item in detail.items" :key="scopeKey(item)" class="retry-task-detail__item">
            <p>{{ item.targetType === 'STOCK' ? item.targetValue : '原请求条件' }} · {{ time(item) }}</p>
            <p>{{ item.errorMessage }}（{{ item.errorCode }}）</p>
            <p class="retry-task-detail__muted">更新时间（北京时间）：{{ formatIngestedAt(item.updatedAt) }}</p>
          </li>
        </ul>
      </section>
      <div class="retry-task-detail__execution" role="status" aria-live="polite">
        <p v-if="mainGuidance">{{ mainGuidance }}<template v-if="detail.executionBlocker?.message === mainGuidance">（{{ detail.executionBlocker.code }}）</template></p>
        <p v-if="detail.executionBlocker && detail.executionBlocker.message !== mainGuidance">{{ detail.executionBlocker.message }}（{{ detail.executionBlocker.code }}）</p>
        <p v-if="detail.retrying && mainGuidance !== '当前任务正在重试。'">当前任务正在重试。</p>
      </div>
      <div class="retry-task-detail__actions">
        <el-button native-type="button" :disabled="disabled" @click="refresh">刷新详情</el-button>
        <el-button type="primary" native-type="button" :disabled="disabled || !canExecute || needsRefresh || detail.retrying" @click="execute">重试一次</el-button>
      </div>
    </div>
    <AsyncStatePanel v-else-if="state === 'LOADING'" state="LOADING" title="正在加载任务详情" message="请稍候。" />
    <AsyncStatePanel v-else-if="state === 'NOT_FOUND'" state="EMPTY" title="任务不存在或已无剩余失败记录"
      message="任务不存在或已无剩余失败记录；查不到任务不代表某次无响应下载成功。">
      <p v-if="executionMessage">{{ executionMessage }}</p>
    </AsyncStatePanel>
    <AsyncStatePanel v-else-if="state === 'FAILURE'" state="FAILURE" title="任务详情加载失败"
      :message="error?.message || '未能读取当前记录。'" :request-id="error?.requestId || ''"
      :retry-label="disabled ? '' : '重新加载'" @retry="refresh" />
    <AsyncStatePanel v-else state="INITIAL" title="选择失败任务查看详情" message="详情只展示当前保存的失败记录。" />
  </WorkbenchPanel>
</template>

<style scoped>
.retry-task-detail { min-width: 0; overflow-wrap: anywhere; }
.retry-task-detail__body { padding: 24px; font-size: 13px; line-height: 1.7; }
.retry-task-detail h3 { margin: 0 0 10px; font-size: 16px; font-weight: 550; }
.retry-task-detail p { margin: 6px 0; }
.retry-task-detail dl { display: grid; gap: 14px; margin: 24px 0; }
.retry-task-detail dt, .retry-task-detail__muted { color: var(--tensor-muted); }
.retry-task-detail dd { margin: 4px 0 0; font-variant-numeric: tabular-nums; }
.retry-task-detail dd ul { margin: 0; padding-left: 18px; }
.retry-task-detail__items { list-style: none; margin: 0; padding: 0; }
.retry-task-detail__item { padding: 14px 0; border-top: 1px solid var(--tensor-line); }
.retry-task-detail__execution { margin-top: 20px; }
.retry-task-detail__actions { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 16px; }
.retry-task-detail__actions :deep(.el-button + .el-button) { margin-left: 0; }
.retry-task-detail :deep(.el-button:focus-visible) { outline: 3px solid var(--tensor-interactive-color); outline-offset: 3px; }
@media (max-width: 680px) { .retry-task-detail__body { padding: 18px; } }
</style>
