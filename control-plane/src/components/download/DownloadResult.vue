<script setup>
import { computed } from 'vue'

import AsyncStatePanel from '../common/AsyncStatePanel.vue'

const props = defineProps({
  state: {
    type: String,
    required: true,
    validator: (value) => ['SUCCESS', 'EMPTY', 'NO_OPEN_DATES', 'PARTIAL', 'FAILED', 'UNCONFIRMED', 'FAILURE'].includes(value),
  },
  result: { type: Object, default: null },
  error: { type: Object, default: null },
  context: { type: Object, default: null },
})
const emit = defineEmits(['view-task'])
const titles = {
  SUCCESS: '下载完成', EMPTY: '下载完成，0 条数据', NO_OPEN_DATES: '所选范围均为休市日期',
  PARTIAL: '部分完成', FAILED: '本轮下载失败', UNCONFIRMED: '结果未确认',
}
const panelState = computed(() => props.state === 'SUCCESS' ? 'SUCCESS'
  : ['EMPTY', 'NO_OPEN_DATES'].includes(props.state) ? 'EMPTY' : 'FAILURE')
const title = computed(() => titles[props.state] ?? ({
  CALENDAR_UNCONFIRMED: '未开始下载：日历未确认', DOWNLOAD_BUSY: '已有下载正在执行',
}[props.error?.code] ?? '请求未完成'))
const explanation = computed(() => {
  if (props.state === 'UNCONFIRMED') return props.result
    ? '本轮结果或失败记录未完全确认，已确认提交的单元不会因此撤销。'
    : '未收到可确认的下载结果，服务端可能仍在执行。请查看实际保存的失败记录；本提示不代表已终止、已回滚或已成功。'
  if (props.error?.code === 'CALENDAR_UNCONFIRMED') return props.context?.operation === 'RETRY'
    ? '未确认适用日历，本次未开始业务重试，原失败记录保留。'
    : '未确认适用日历，本次未开始业务下载，也未新增失败任务。'
  return {
    SUCCESS: '本轮数据已完成写入。', EMPTY: '本次请求没有可写入的数据。',
    NO_OPEN_DATES: '已按适用日历确认跳过休市日期，不属于来源返回空数据。',
    PARTIAL: '本轮已完成部分恢复单元，仍有明确失败项。',
    FAILED: '本轮尝试的恢复单元均未完成，请查看明确失败范围。',
  }[props.state] ?? ''
})
const counts = [
  ['completedUnits', '已完成项'], ['failedUnits', '明确失败项'], ['notStartedUnits', '未开始项'],
  ['skippedClosedDates', '跳过休市日期'], ['sourceRowCount', '已确认返回行数'],
  ['insertedRows', '已确认新增行数'], ['updatedRows', '已确认更新行数'],
]
const recordLabels = {
  NOT_REQUIRED: '无需保留失败记录', CONFIRMED: '失败记录已确认', UNCONFIRMED: '失败记录保存或删除结果未确认',
}
const scopes = computed(() => props.result ? [
  ['本轮明确失败范围', props.result.failures],
  ['本轮未开始范围', props.result.notStartedScopes],
  ['本轮结果未确认范围', props.result.unconfirmedScopes],
].filter(([, items]) => items.length) : [])
const hasRequestScope = computed(() => scopes.value.some(([, items]) => items.some((item) => item.targetType === 'REQUEST')))
const conditions = computed(() => Object.entries(props.context?.params ?? {}).filter(([name]) => !['start_date', 'end_date'].includes(name)))
function count(value) { return value === null ? '未确认' : value }
function time(scope) {
  if (scope.timeType === 'NONE') return '原条件请求'
  if (scope.timeType === 'MONTH') return `${scope.timeValue}（完整月份）`
  return scope.timeValue.replace('/', ' 至 ')
}
</script>

<template>
  <AsyncStatePanel
    class="download-result"
    :state="panelState"
    :title="title"
    :message="explanation || error?.message || result?.message || ''"
    :request-id="error?.requestId || result?.requestId || ''"
  >
    <p v-if="explanation && (error?.message || result?.message)" class="download-result__note">{{ error?.message || result?.message }}</p>
    <p v-if="state === 'UNCONFIRMED'" class="download-result__note">{{ context?.operation === 'RETRY' ? '可刷新当前失败记录；再次手动重试只处理服务端当时仍保存的明细，不会恢复已删除项。' : '重新提交当前条件属于新的首次下载，不是原任务重试。' }}</p>
    <template v-if="result">
      <p v-if="state === 'UNCONFIRMED'" class="download-result__note">以下为本轮已确认小计，不代表完整结果。</p>
      <dl class="download-result__counts">
        <div v-for="[key, label] in counts" :key="key">
          <dt>{{ label }}</dt><dd>{{ count(result[key]) }}</dd>
        </div>
      </dl>
      <dl class="download-result__record">
        <div><dt>当前剩余失败项</dt><dd>{{ count(result.remainingFailedUnits) }}</dd></div>
        <div><dt>失败记录状态</dt><dd>{{ recordLabels[result.failureRecordStatus] }}</dd></div>
      </dl>
      <div v-if="hasRequestScope" class="download-result__conditions">
        <h3>原请求附加条件</h3>
        <ul v-if="conditions.length"><li v-for="[name, value] in conditions" :key="name">{{ name }}：{{ value }}</li></ul>
        <p v-else>无附加条件</p>
      </div>
      <section v-for="[heading, items] in scopes" :key="heading" class="download-result__scopes" :aria-label="heading">
        <h3>{{ heading }}</h3>
        <ul>
          <li v-for="(scope, index) in items" :key="index">
            <span>{{ scope.targetType === 'STOCK' ? scope.targetValue : '原请求条件' }} · {{ time(scope) }}</span>
            <p v-if="scope.errorCode">{{ scope.errorMessage }}（{{ scope.errorCode }}）</p>
          </li>
        </ul>
      </section>
    </template>
    <template v-if="result?.taskId" #actions>
      <el-button native-type="button" @click="emit('view-task', result.taskId)">查看重试任务</el-button>
    </template>
  </AsyncStatePanel>
</template>

<style scoped>
.download-result__counts {
  display: grid;
  width: 100%;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px 14px;
  margin: 24px 0 0;
  padding-top: 24px;
  border-top: 1px solid var(--tensor-line);
}
.download-result__counts dt,
.download-result__record dt {
  color: var(--tensor-muted);
  font-size: 12px;
  line-height: 1.5;
}
.download-result__counts dd {
  margin: 8px 0 0;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace;
  font-size: clamp(19px, 2vw, 28px);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.04em;
}
.download-result__note,
.download-result__record,
.download-result__conditions,
.download-result__scopes {
  width: 100%;
  margin: 16px 0 0;
  font-size: 14px;
  line-height: 1.7;
}
.download-result__note { color: var(--tensor-muted); }
.download-result__record dd { margin: 4px 0 12px; }
.download-result__scopes h3,
.download-result__conditions h3 { margin: 8px 0; font-size: 14px; }
.download-result__scopes ul,
.download-result__conditions ul { margin: 0; padding-inline-start: 20px; }
.download-result__scopes li + li { margin-top: 12px; }
.download-result__scopes p,
.download-result__conditions p { margin: 4px 0 0; }
.download-result { overflow-wrap: anywhere; }
@media (max-width: 680px) {
  .download-result__counts { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
