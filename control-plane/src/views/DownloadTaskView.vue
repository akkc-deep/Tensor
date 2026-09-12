<script setup>
import { computed, onUnmounted, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { parseTaskJson } from '../api/downloadTaskDtos.js'
import AsyncStatePanel from '../components/common/AsyncStatePanel.vue'
import PageHeading from '../components/common/PageHeading.vue'
import WorkbenchPanel from '../components/common/WorkbenchPanel.vue'
import DownloadBatchTable from '../components/download/DownloadBatchTable.vue'
import { useDownloadTask } from '../composables/useDownloadTask.js'

const route = useRoute()
const flow = useDownloadTask()
const {
  task, batches, page, pageSize, loading, taskError, batchesError,
  taskUpdatedAt, batchesUpdatedAt, operation, operationError, operationMessage,
  notFound, invalidTaskId, canRetry, canResume,
} = flow
watch(() => route.params.taskId, (id) => flow.load(id), { immediate: true })
onUnmounted(flow.dispose)

const STATUS_LABELS = {
  QUEUED: '排队中', RUNNING: '运行中', SUCCEEDED: '已成功',
  PARTIAL_FAILED: '部分失败', FAILED: '失败', INTERRUPTED: '已中断',
}
const TIMES = [
  ['createdAt', '创建时间'], ['updatedAt', '更新时间'], ['queuedAt', '排队时间'],
  ['startedAt', '开始时间'], ['finishedAt', '结束时间'], ['deadlineAt', '本轮截止时间'],
]
const parameters = computed(() => Object.entries(task.value?.params ?? {}).sort(([a], [b]) => a.localeCompare(b)))
const finished = computed(() => task.value ? task.value.counts.succeededBatches + task.value.counts.failedBatches : 0n)
function formatTime(value) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(value))
}
function errorMessage(error) {
  if (error?.kind === 'INVALID_RESPONSE' && typeof parseTaskJson('{"n":1}') === 'string') {
    return '当前浏览器无法保真处理任务数值，请更新浏览器后重试。'
  }
  return error.message
}
</script>

<template>
  <section class="page task-detail" aria-labelledby="download-task-title">
    <PageHeading id="download-task-title" title="任务详情" description="查看任务进度、批次结果，继续未完成的下载。" />
    <div class="task-detail__toolbar">
      <RouterLink class="page__action" to="/downloads">返回下载页</RouterLink>
      <el-button data-refresh-task native-type="button" :aria-busy="loading" @click="flow.refresh">刷新状态</el-button>
    </div>
    <div v-if="operationMessage" class="task-detail__notice" role="status" aria-live="polite">
      <strong>{{ operationMessage }}</strong>
      <span v-if="operationError && operationError.message !== operationMessage">{{ errorMessage(operationError) }}</span>
      <span v-if="operationError?.requestId">请求 ID：{{ operationError.requestId }}</span>
    </div>
    <AsyncStatePanel v-if="invalidTaskId" state="FAILURE" title="任务地址无效" message="请核对完整任务地址，或返回下载页查找任务。" />
    <AsyncStatePanel v-else-if="notFound" state="FAILURE" title="任务不存在" message="未找到该任务，请核对地址或返回近期任务列表。"
      :request-id="taskError.requestId ?? ''" retry-label="重新查询" @retry="flow.refresh" />
    <AsyncStatePanel v-else-if="!task && taskError" state="FAILURE" title="任务加载失败" :message="errorMessage(taskError)"
      :request-id="taskError.requestId ?? ''" retry-label="重新查询" @retry="flow.refresh" />
    <AsyncStatePanel v-else-if="!task" state="LOADING" title="正在加载任务" message="查询任务详情与当前批次。" />
    <template v-else>
      <div v-if="taskError" class="task-detail__notice" role="status">
        <strong>状态暂时无法更新</strong><span>{{ errorMessage(taskError) }}</span>
        <span v-if="taskError.requestId">请求 ID：{{ taskError.requestId }}</span>
        <span v-if="taskUpdatedAt">上次更新 {{ formatTime(taskUpdatedAt) }}</span>
      </div>
      <WorkbenchPanel heading-id="task-overview-title" title="任务概览">
        <div class="task-detail__overview">
          <div class="task-detail__identity">
            <strong class="task-detail__dataset">{{ task.pluginId }} / {{ task.apiName }}</strong>
            <span>任务 ID <code>{{ task.taskId }}</code></span>
            <span>{{ task.mode === 'SINGLE' ? '单次请求' : '日期区间' }} · 版本 {{ task.version.toString() }}</span>
            <div class="task-detail__parameters" aria-label="规范化参数">
              <code v-for="[name, value] in parameters" :key="name">{{ name }}={{ value }}</code>
              <span v-if="!parameters.length">无业务参数</span>
            </div>
            <p v-if="task.mode === 'SINGLE'">单次请求，结果不代表完整历史。</p>
          </div>
          <div class="task-detail__execution">
            <strong data-task-status class="task-detail__status" :class="`task-detail__status--${task.status.toLowerCase()}`">{{ STATUS_LABELS[task.status] }}</strong>
            <p v-if="task.status === 'SUCCEEDED'">{{ task.mode === 'SINGLE' ? '本次请求已完成' : '本次请求范围内的计划已完成' }}。</p>
            <p v-else-if="task.status === 'INTERRUPTED'">任务已中断，需要手动恢复。已成功的结果保留。</p>
            <p v-else-if="['FAILED', 'PARTIAL_FAILED'].includes(task.status)">
              失败 {{ task.counts.failedBatches.toString() }} 批，待执行 {{ task.counts.pendingBatches.toString() }} 批。
              <a class="page__action" href="#download-batches-title">查看批次明细</a>
            </p>
            <p v-else>任务由后台执行，可稍后通过此地址继续查看。</p>
            <div class="task-detail__actions">
              <el-button v-if="task.canRetry" data-retry type="primary" native-type="button" :disabled="!canRetry"
                :aria-busy="operation === 'retry'" @click="flow.retry">重试任务</el-button>
              <el-button v-if="task.canResume" data-resume type="primary" native-type="button" :disabled="!canResume"
                :aria-busy="operation === 'resume'" @click="flow.resume">恢复任务</el-button>
            </div>
            <p v-if="task.canRetry" class="task-detail__help">重试处理失败及未完成工作，保留成功结果。</p>
            <p v-if="task.canResume" class="task-detail__help">恢复继续中断工作，普通失败可能仍需之后重试。</p>
          </div>
        </div>
        <div class="task-detail__counts">
          <strong v-if="task.planReady">已结束 {{ finished.toString() }} / 当前计划 {{ task.counts.totalBatches.toString() }} 批</strong>
          <strong v-else>尚未生成计划</strong>
          <div class="task-detail__count-items">
            <span>成功 {{ task.counts.succeededBatches.toString() }}</span>
            <span>失败 {{ task.counts.failedBatches.toString() }}</span>
            <span>待执行 {{ task.counts.pendingBatches.toString() }}</span>
            <span>运行中 {{ task.counts.runningBatches.toString() }}</span>
            <span>独立拆分 {{ task.counts.splitBatches.toString() }}</span>
          </div>
          <div class="task-detail__count-items">
            <span>来源行数 {{ task.counts.sourceRows.toString() }}</span>
            <span>新增记录次数 {{ task.counts.insertedRows.toString() }}</span>
            <span>更新记录次数 {{ task.counts.updatedRows.toString() }}</span>
          </div>
          <div class="task-detail__count-items">
            <span>累计请求次数 {{ task.requestCount.toString() }}</span>
            <span>本轮请求次数 {{ task.runRequestCount.toString() }}</span>
          </div>
          <p class="task-detail__help">新增/更新为已提交写入操作次数，不是整段去重总量。</p>
        </div>
        <div v-if="task.lastError" class="task-detail__stop-reason">
          <strong>任务停止 / 规划原因：{{ task.lastError.code }}</strong>
          <p>{{ task.lastError.message }}</p>
        </div>
        <dl class="task-detail__times">
          <div v-for="[key, label] in TIMES" :key="key">
            <dt>{{ label }}</dt>
            <dd><time v-if="task[key]" :datetime="task[key]" :title="task[key]">{{ formatTime(task[key]) }}</time><span v-else>尚无</span></dd>
          </div>
        </dl>
      </WorkbenchPanel>
      <DownloadBatchTable :page="page" :page-size="pageSize" :result="batches" :loading="loading"
        :error="batchesError" :last-updated-at="batchesUpdatedAt" @refresh="flow.refresh"
        @update:page="flow.changePage" @update:page-size="flow.changePageSize" />
    </template>
  </section>
</template>

<style scoped>
.task-detail { min-width: 0; }
.task-detail__toolbar, .task-detail__actions, .task-detail__count-items {
  display: flex; flex-wrap: wrap; align-items: center; gap: 12px 24px;
}
.task-detail__toolbar { justify-content: space-between; margin-bottom: 20px; }
.task-detail__toolbar .page__action { margin: 0; }
.task-detail__overview { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr); gap: 32px; padding: 24px; }
.task-detail__identity, .task-detail__execution { min-width: 0; }
.task-detail__identity > span, .task-detail__parameters code { display: block; margin-top: 8px; }
.task-detail__dataset { font-size: 16px; }
.task-detail__status { font-size: 22px; color: var(--tensor-accent); }
.task-detail__status--succeeded { color: var(--tensor-success); }
.task-detail__status--failed, .task-detail__status--partial_failed, .task-detail__status--interrupted { color: var(--tensor-error); }
.task-detail__identity, .task-detail__execution p, .task-detail__counts, .task-detail__times, .task-detail__notice, .task-detail__stop-reason {
  font-size: 12px; line-height: 1.8; overflow-wrap: anywhere;
}
.task-detail__parameters { margin-top: 18px; color: var(--tensor-muted); }
.task-detail__counts, .task-detail__times, .task-detail__stop-reason { padding: 20px 24px; border-top: 1px solid var(--tensor-line); }
.task-detail__count-items { margin-top: 8px; font-variant-numeric: tabular-nums; }
.task-detail__help, dt { color: var(--tensor-muted); }
.task-detail__help { margin: 12px 0 0; }
.task-detail__times { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px 24px; margin: 0; }
.task-detail__times dd { margin: 4px 0 0; font-variant-numeric: tabular-nums; }
.task-detail__notice { display: flex; flex-direction: column; gap: 4px; padding: 16px 24px; margin-bottom: 20px; background: var(--tensor-accent-bg); color: var(--tensor-text); border-radius: 8px; }
.task-detail__stop-reason { color: var(--tensor-error); }
.task-detail__stop-reason p { margin: 6px 0 0; }
.task-detail > .workbench-panel + .workbench-panel { margin-top: 24px; }
@media (max-width: 680px) {
  .task-detail__overview { grid-template-columns: minmax(0, 1fr); gap: 24px; padding: 18px; }
  .task-detail__counts, .task-detail__times, .task-detail__stop-reason, .task-detail__notice { padding: 18px; }
  .task-detail__times { grid-template-columns: minmax(0, 1fr); }
}
</style>
