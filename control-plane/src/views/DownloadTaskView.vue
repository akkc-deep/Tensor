<script setup>
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'

import { parseTaskJson } from '../api/downloadTaskDtos.js'
import { taskExtractionNotice, taskStatusLabel } from '../utils/downloadTaskText.js'
import AsyncStatePanel from '../components/common/AsyncStatePanel.vue'
import DownloadBatchTable from '../components/download/DownloadBatchTable.vue'
import { downloadTaskChannelKey, useDownloadTask } from '../composables/useDownloadTask.js'

const route = useRoute()
const router = useRouter()
const dialog = ref(null)
const trigger = document.activeElement
const flow = useDownloadTask({ channel: inject(downloadTaskChannelKey, undefined) })
const {
  task, batches, page, pageSize, loading, taskError, batchesError,
  taskUpdatedAt, batchesUpdatedAt, operation, operationError, operationMessage,
  notFound, invalidTaskId, canRetry, canResume,
} = flow
watch(() => route.params.taskId, (id) => flow.load(id), { immediate: true })
onMounted(() => dialog.value.showModal())
onBeforeUnmount(() => {
  flow.dispose()
  dialog.value.close()
  const id = task.value?.taskId
  nextTick(() => {
    const fallback = id && document.querySelector(`.download-task-list a[href="/downloads/tasks/${id}"]`)
    const target = trigger?.isConnected && trigger !== document.body ? trigger : fallback || document.getElementById('workspace')
    target?.focus({ preventScroll: true })
  })
})
function close() {
  if (router.options.history.state.back === '/downloads') router.back()
  else router.replace({ name: 'downloads' })
}
function backdrop(event) {
  if (event.target !== dialog.value) return
  const { left, right, top, bottom } = dialog.value.getBoundingClientRect()
  if (event.clientX < left || event.clientX > right || event.clientY < top || event.clientY > bottom) close()
}

const TIMES = [
  ['createdAt', '创建时间'], ['updatedAt', '更新时间'], ['queuedAt', '排队时间'],
  ['startedAt', '开始时间'], ['finishedAt', '结束时间'], ['deadlineAt', '本轮截止时间'],
]
const parameters = computed(() => Object.entries(task.value?.params ?? {}).sort(([a], [b]) => a.localeCompare(b)))
const finished = computed(() => task.value ? task.value.counts.succeededBatches + task.value.counts.failedBatches : 0n)
const progress = computed(() => {
  const total = task.value?.counts.totalBatches ?? 0n
  return total > 0n ? Number(finished.value * 10000n / total) / 100 : 0
})
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
  <dialog ref="dialog" class="task-detail" aria-labelledby="download-task-title" @cancel.prevent="close" @click="backdrop">
    <header class="task-detail__header">
      <div><span class="muted">任务详情</span><h2 id="download-task-title">{{ task?.apiName || '任务详情' }}</h2></div>
      <button class="task-detail__close" type="button" aria-label="关闭任务详情" autofocus @click="close"><Close aria-hidden="true" /></button>
    </header>
    <div class="task-detail__toolbar">
      <a class="text-button" href="/downloads" @click.prevent="close">返回下载页</a>
      <button data-refresh-task class="text-button" type="button" :disabled="loading || invalidTaskId" :aria-busy="loading" @click="flow.refresh">{{ loading ? '刷新中…' : '刷新状态' }}</button>
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
      <span data-task-status class="task-detail__status" :class="`task-detail__status--${task.status.toLowerCase()}`">{{ taskStatusLabel(task) }}</span>
      <dl class="confirmation">
        <div><dt>数据源 / 接口</dt><dd>{{ task.pluginId }} / {{ task.apiName }}</dd></div>
        <div><dt>下载方式</dt><dd>{{ task.mode === 'SINGLE' ? '单次请求' : '批量下载 · 日期区间' }}</dd></div>
        <div><dt>来源行数</dt><dd>{{ task.counts.sourceRows.toString() }}</dd></div>
      </dl>
      <div class="task-detail__parameters" aria-label="规范化参数">
        <code v-for="[name, value] in parameters" :key="name">{{ name }}={{ value }}</code>
        <span v-if="!parameters.length">无业务参数</span>
      </div>
      <p v-if="task.mode === 'SINGLE'" class="task-detail__help">单次请求，结果不代表完整历史。</p>
      <p v-if="taskExtractionNotice(task)" class="task-detail__notice">{{ taskExtractionNotice(task) }}</p>
      <p v-if="task.status === 'SUCCEEDED' && task.extraction?.ruleKind !== 'RESPONSE_ONLY'" class="task-detail__help">{{ task.mode === 'SINGLE' ? '本次请求已完成' : '本次请求范围内的计划已完成' }}。</p>
      <p v-else-if="['QUEUED', 'RUNNING'].includes(task.status)" class="task-detail__help">任务由后台执行，可稍后通过此地址继续查看。</p>
      <div class="detail-progress">
        <strong v-if="task.planReady">已结束 {{ finished.toString() }} / 当前计划 {{ task.counts.totalBatches.toString() }} 批</strong>
        <strong v-else>尚未生成计划</strong>
        <progress v-if="task.planReady && task.counts.totalBatches > 0n" :value="progress" max="100" aria-label="已结束批次进度" />
        <div class="task-detail__count-items">
          <span>成功 {{ task.counts.succeededBatches.toString() }}</span><span>失败 {{ task.counts.failedBatches.toString() }}</span>
          <span>待执行 {{ task.counts.pendingBatches.toString() }}</span><span>运行中 {{ task.counts.runningBatches.toString() }}</span>
          <span>独立拆分 {{ task.counts.splitBatches.toString() }}</span>
        </div>
        <p class="task-detail__help">已结束包含失败批次；当前计划及区间可能随拆分变化。</p>
      </div>
      <div v-if="task.lastError || ['FAILED', 'PARTIAL_FAILED', 'INTERRUPTED'].includes(task.status) || task.canRetry || task.canResume" class="task-detail__stop-reason">
        <h3>{{ task.status === 'INTERRUPTED' ? '任务执行已中断' : '任务需要处理' }}</h3>
        <p v-if="task.status === 'INTERRUPTED'">任务已中断，需要手动恢复。已成功的结果保留。</p>
        <template v-if="task.lastError"><strong>{{ task.lastError.code }}</strong><p>{{ task.lastError.message }}</p></template>
        <div class="task-detail__actions">
          <button v-if="task.canRetry" data-retry class="button button--primary" type="button" :disabled="!canRetry" :aria-busy="operation === 'retry'" @click="flow.retry">{{ operation === 'retry' ? '正在重试…' : '重试任务' }}</button>
          <button v-if="task.canResume" data-resume class="button button--primary" type="button" :disabled="!canResume" :aria-busy="operation === 'resume'" @click="flow.resume">{{ operation === 'resume' ? '正在恢复…' : '恢复任务' }}</button>
        </div>
        <p v-if="(task.canRetry || task.canResume) && !canRetry && !canResume && !operation" class="task-detail__help">正在确认最新任务状态；查询成功后可继续操作。</p>
        <p v-if="!task.canRetry && !task.canResume" class="task-detail__help">当前任务暂不可重试或恢复，请刷新状态或查看失败原因。</p>
        <p v-if="task.canRetry" class="task-detail__help">重试处理失败及未完成工作，保留成功结果。</p>
        <p v-if="task.canResume" class="task-detail__help">恢复继续中断工作，普通失败可能仍需之后重试。</p>
      </div>
      <details class="task-detail__record">
        <summary>任务记录</summary>
        <p>任务 ID <code>{{ task.taskId }}</code></p><p>提交标识 <code>{{ task.submissionId }}</code></p>
        <p>版本 {{ task.version.toString() }}</p>
        <p>新增记录次数 {{ task.counts.insertedRows.toString() }}</p><p>更新记录次数 {{ task.counts.updatedRows.toString() }}</p>
        <p v-if="task.extraction">采集规则 {{ task.extraction.ruleKind }} · 策略版本 {{ task.extraction.policyVersion }}</p>
        <p class="task-detail__help">新增/更新为已提交写入操作次数，不是整段去重总量。</p>
        <p>累计请求次数 {{ task.requestCount.toString() }}</p><p>本轮请求次数 {{ task.runRequestCount.toString() }}</p>
        <dl class="confirmation task-detail__times"><div v-for="[key, label] in TIMES" :key="key"><dt>{{ label }}</dt><dd><time v-if="task[key]" :datetime="task[key]" :title="task[key]">{{ formatTime(task[key]) }}</time><span v-else>尚无</span></dd></div></dl>
      </details>
    </template>
    <DownloadBatchTable v-if="!invalidTaskId && !notFound" :page="page" :page-size="pageSize" :result="batches" :loading="loading"
      :error="batchesError" :last-updated-at="batchesUpdatedAt" @refresh="flow.refresh"
      @update:page="flow.changePage" @update:page-size="flow.changePageSize" />
  </dialog>
</template>

<style scoped>
.task-detail { position: fixed; inset: 0 0 0 auto; box-sizing: border-box; width: 43rem; max-width: 100%; height: 100dvh; max-height: 100dvh; margin: 0; padding: 3.1rem; border: 0; border-left: 0.1rem solid var(--tensor-line); background: var(--tensor-surface); color: var(--tensor-text); overflow: auto; overscroll-behavior: contain; overflow-wrap: anywhere; font-size: 1.4rem; line-height: 1.6; }
.task-detail::backdrop { background: #121b1840; }
.task-detail__header, .task-detail__toolbar, .task-detail__actions { display: flex; align-items: flex-start; justify-content: space-between; gap: 1.2rem; }
.task-detail__header { margin-bottom: 2.2rem; }
.task-detail__header > div { min-width: 0; }
.task-detail__header h2 { margin: 0.5rem 0 0; font-size: 2.3rem; line-height: 1.35; }
.muted, .task-detail__help { color: var(--tensor-muted); font-size: 1.2rem; }
.task-detail__close { display: grid; place-items: center; flex: 0 0 3.4rem; width: 3.4rem; height: 3.4rem; padding: 0.6rem; border: 0; border-radius: 0.6rem; color: var(--tensor-text); background: transparent; cursor: pointer; }
.task-detail__close:hover { background: var(--tensor-raised); color: var(--tensor-text); }
.task-detail__close svg { width: 1.8rem; height: 1.8rem; }
.task-detail__toolbar { margin-bottom: 1.8rem; }
.task-detail__toolbar a { text-decoration: none; }
.task-detail__status { display: inline-flex; align-items: center; gap: 0.5rem; color: var(--tensor-accent); font-size: 1.2rem; }
.task-detail__status::before { content: ''; width: 0.5rem; height: 0.5rem; flex-shrink: 0; border-radius: 50%; background: currentColor; }
.task-detail__status--succeeded { color: var(--tensor-success); }
.task-detail__status--failed, .task-detail__status--partial_failed, .task-detail__status--interrupted { color: var(--tensor-warning); }
.confirmation { margin: 2.2rem 0 1.2rem; }
.confirmation > div { display: grid; grid-template-columns: 10.4rem minmax(0, 1fr); gap: 1.8rem; padding: 1.2rem 0; border-bottom: 0.1rem solid var(--tensor-line); }
.confirmation dt { color: var(--tensor-muted); }
.confirmation dd { margin: 0; text-align: right; font-variant-numeric: tabular-nums; }
.task-detail__parameters { display: grid; gap: 0.5rem; font-size: 1.2rem; }
.task-detail__help { margin: 1rem 0; }
.detail-progress { margin: 2.4rem 0; font-size: 1.2rem; font-variant-numeric: tabular-nums; }
.detail-progress > strong { font-weight: 500; }
.detail-progress progress { display: block; width: 100%; height: 0.5rem; margin: 1.2rem 0; border: 0; border-radius: 0.3rem; overflow: hidden; appearance: none; background: var(--tensor-raised); accent-color: var(--tensor-accent); }
.detail-progress progress::-webkit-progress-bar { background: var(--tensor-raised); }
.detail-progress progress::-webkit-progress-value { background: var(--tensor-accent); }
.detail-progress progress::-moz-progress-bar { background: var(--tensor-accent); }
.task-detail__count-items { display: flex; flex-wrap: wrap; gap: 0.6rem 1.4rem; color: var(--tensor-muted); }
.task-detail__notice { display: flex; flex-direction: column; gap: 0.4rem; padding: 1.2rem; margin: 1.6rem 0; background: var(--tensor-accent-bg); border-radius: 0.7rem; font-size: 1.2rem; }
.task-detail__stop-reason { padding: 1.8rem; margin: 2.4rem 0; background: var(--tensor-warning-bg); border-radius: 0.7rem; }
.task-detail__stop-reason h3 { margin: 0 0 0.8rem; color: var(--tensor-warning); font-size: 1.4rem; }
.task-detail__stop-reason > strong { font-size: 1.2rem; color: var(--tensor-warning); }
.task-detail__stop-reason p { margin: 0.8rem 0 1.6rem; }
.task-detail__actions { flex-wrap: wrap; justify-content: flex-start; }
.task-detail__record { margin: 2.4rem 0; padding: 1.2rem 0; border-top: 0.1rem solid var(--tensor-line); border-bottom: 0.1rem solid var(--tensor-line); font-size: 1.2rem; }
.task-detail__record summary { cursor: pointer; color: var(--tensor-muted); }
.task-detail__record .confirmation { margin: 1.2rem 0 0; }
.task-detail :is(button, a, summary):focus-visible { outline: 0.2rem solid var(--tensor-interactive-color); outline-offset: 0.2rem; }
.task-detail button:disabled { cursor: not-allowed; opacity: .55; }
</style>
