<script setup>
import { computed, nextTick, watch } from 'vue'
import { Clock, RefreshRight } from '@element-plus/icons-vue'
import { RouterLink } from 'vue-router'

import { parseTaskJson } from '../../api/downloadTaskDtos.js'
import { taskExtractionNotice, taskStatusLabel } from '../../utils/downloadTaskText.js'
import AsyncStatePanel from '../common/AsyncStatePanel.vue'

const GROUPS = [{ id: '', label: '全部' }, { id: 'ACTIVE', label: '进行中' }, { id: 'DONE', label: '已完成' }, { id: 'ERROR', label: '需处理' }]
const PAGE_SIZES = [20, 50, 100]
const MAX_PAGE = 2_147_483_647n

const props = defineProps({
  statusGroup: { type: String, default: '' },
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  result: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: Object, default: null },
  lastUpdatedAt: { type: Date, default: null },
  action: { type: Object, default: null },
  actionBusy: { type: Boolean, default: false },
  pendingOperations: { type: Map, default: () => new Map() },
})

const emit = defineEmits(['refresh', 'update:page', 'update:pageSize', 'update:statusGroup', 'control', 'refresh-action'])

const groupLabel = computed(() => GROUPS.find(group => group.id === props.statusGroup)?.label ?? '全部')
const total = computed(() => props.result?.total ?? 0n)
const totalPages = computed(() => {
  if (total.value === 0n) return 0
  const pages =
    (total.value + BigInt(props.pageSize) - 1n) / BigInt(props.pageSize)
  return Number(pages > MAX_PAGE ? MAX_PAGE : pages)
})
const rows = computed(() => props.result?.items ?? [])
let ignoredClamp = null

watch(
  totalPages,
  (nextTotalPages) => {
    if (props.page <= nextTotalPages) return
    const clampTarget = Math.max(1, nextTotalPages)
    ignoredClamp = clampTarget
    nextTick(() => {
      if (ignoredClamp === clampTarget) ignoredClamp = null
    })
  },
  { flush: 'sync' },
)

function integer(value) {
  return value.toString()
}

function parameters(params) {
  return Object.entries(params)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([name, value]) => `${name}=${value}`)
}

function formatTime(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'medium',
  }).format(new Date(value))
}

function finished(counts) {
  return counts.succeededBatches + counts.failedBatches
}

function errorMessage(error) {
  if (
    error?.kind === 'INVALID_RESPONSE' &&
    typeof parseTaskJson('{"n":1}') === 'string'
  ) {
    return '当前浏览器无法保真处理任务数值，请更新浏览器后重试。'
  }
  return error.message
}

function updatePage(nextPage) {
  if (nextPage === ignoredClamp) {
    ignoredClamp = null
    return
  }
  if (nextPage !== props.page) emit('update:page', nextPage)
}

function updatePageSize(nextPageSize) {
  emit('update:pageSize', nextPageSize)
}
</script>

<template>
  <section class="download-task-list" aria-labelledby="download-task-list-title">
    <header class="section-heading">
      <h2 id="download-task-list-title">最近任务 <span v-if="result" class="count" :aria-label="`${groupLabel}任务共 ${total} 个`">{{ total }}</span></h2>
      <button data-refresh class="text-button" type="button" :aria-busy="loading" @click="emit('refresh')">
        <RefreshRight aria-hidden="true" />{{ loading ? '刷新中…' : '刷新状态' }}
      </button>
    </header>
    <div class="task-filters" role="group" aria-label="任务状态筛选">
      <button v-for="group in GROUPS" :key="group.id" type="button" :class="{ active: statusGroup === group.id }" :aria-pressed="statusGroup === group.id" @click="emit('update:statusGroup', group.id)">{{ group.label }}</button>
    </div>

    <div v-if="action" class="download-task-list__warning task-action-feedback" role="status" aria-live="polite">
      <strong>{{ action.apiName }} · {{ action.message || '请重新查询任务状态。' }}</strong>
      <span v-if="action.error">{{ errorMessage(action.error) }}</span>
      <span v-if="action.error?.requestId">请求 ID：{{ action.error.requestId }}</span>
      <div class="task-action-links">
        <button v-if="action.error" class="text-button" type="button" :disabled="actionBusy" @click="emit('refresh-action')">重新查询</button>
        <RouterLink class="text-button" :to="`/downloads/tasks/${action.taskId}`">查看任务详情</RouterLink>
      </div>
    </div>
    <div v-if="error && result" class="download-task-list__warning" role="status">
      <strong>状态暂时无法更新</strong>
      <span>{{ errorMessage(error) }}</span>
      <span v-if="error.requestId">请求 ID：{{ error.requestId }}</span>
      <span v-if="lastUpdatedAt">上次更新 {{ formatTime(lastUpdatedAt) }}</span>
      <button class="text-button" type="button" @click="emit('refresh')">重试刷新</button>
    </div>
    <AsyncStatePanel v-if="loading && !result" state="LOADING" title="正在加载近期任务" message="请稍候。" />
    <AsyncStatePanel v-else-if="error && !result" state="FAILURE" title="近期任务加载失败" :message="errorMessage(error)" :request-id="error.requestId ?? ''" retry-label="重新加载" @retry="emit('refresh')" />
    <AsyncStatePanel v-else-if="!result" state="INITIAL" title="近期任务尚未加载" message="页面可见时将自动加载。" />
    <template v-else>
      <div v-if="rows.length" class="task-items" role="region" aria-label="最近任务列表" tabindex="0">
        <article v-for="task in rows" :key="task.taskId" class="task-row">
          <div class="task-overview">
            <RouterLink class="task-name" :to="`/downloads/tasks/${task.taskId}`" :aria-label="`查看任务 ${task.apiName}`">
              {{ task.apiName }}<span>{{ task.pluginId }}</span>
              <small class="task-progress">{{ task.mode === 'RANGE' ? '批量' : '单次请求' }} · <template v-if="task.planReady">{{ task.counts.succeededBatches }} / {{ task.counts.totalBatches }} 批成功</template><template v-else>尚未生成计划</template></small>
            </RouterLink>
            <div class="task-trailing">
              <span data-task-status class="download-task-list__status" :class="`download-task-list__status--${task.status.toLowerCase()}`">{{ taskStatusLabel(task) }}</span>
              <template v-if="task.canRetry || task.canResume">
                <button v-if="task.canRetry" data-quick-retry class="text-button" type="button" :aria-label="`重试 ${task.apiName}`" :disabled="actionBusy || !!error || pendingOperations.has(task.taskId)" :aria-busy="pendingOperations.get(task.taskId) === 'retry'" @click="emit('control', task, 'retry')"><RefreshRight aria-hidden="true" />{{ pendingOperations.get(task.taskId) === 'retry' ? '重试中…' : '重试' }}</button>
                <button v-if="task.canResume" data-quick-resume class="text-button" type="button" :aria-label="`恢复 ${task.apiName}`" :disabled="actionBusy || !!error || pendingOperations.has(task.taskId)" :aria-busy="pendingOperations.get(task.taskId) === 'resume'" @click="emit('control', task, 'resume')"><RefreshRight aria-hidden="true" />{{ pendingOperations.get(task.taskId) === 'resume' ? '恢复中…' : '恢复' }}</button>
              </template>
              <small v-else-if="task.status === 'SUCCEEDED'">{{ task.counts.sourceRows }} 行已采集</small>
              <small v-else-if="task.planReady && task.counts.failedBatches > 0n">{{ task.counts.failedBatches }} 批失败</small>
              <small v-else-if="task.status === 'INTERRUPTED'">执行已中断</small>
              <small v-else-if="['FAILED', 'PARTIAL_FAILED'].includes(task.status)">查看失败原因</small>
              <small v-else>{{ task.status === 'QUEUED' ? '等待执行' : '进度自动更新' }}</small>
            </div>
          </div>
          <p v-if="task.lastError" class="task-error">{{ task.lastError.message }} <code>{{ task.lastError.code }}</code></p>
          <p v-if="taskExtractionNotice(task)" class="task-notice">{{ taskExtractionNotice(task) }}</p>
          <details class="task-facts">
            <summary>参数与进度</summary>
            <div class="task-facts__body">
              <code v-for="parameter in parameters(task.params)" :key="parameter">{{ parameter }}</code>
              <span v-if="task.extraction">采集规则 {{ task.extraction.ruleKind }} · 策略版本 {{ task.extraction.policyVersion }}</span>
              <template v-if="task.planReady">
                <strong>已结束 {{ integer(finished(task.counts)) }} / 当前计划 {{ integer(task.counts.totalBatches) }} 批</strong>
                <span>成功 {{ task.counts.succeededBatches }} · 失败 {{ task.counts.failedBatches }} · 待执行 {{ task.counts.pendingBatches }} · 运行中 {{ task.counts.runningBatches }}</span>
                <span>独立拆分 {{ task.counts.splitBatches }}</span>
              </template>
              <span>已采集 {{ task.counts.sourceRows }} 行</span>
              <span>新增记录次数 {{ task.counts.insertedRows }}</span>
              <span>更新记录次数 {{ task.counts.updatedRows }}</span>
              <span>已提交写入操作次数，不是整段去重总量</span>
              <span>创建 <time :datetime="task.createdAt" :title="task.createdAt">{{ formatTime(task.createdAt) }}</time></span>
              <span>更新 <time :datetime="task.updatedAt" :title="task.updatedAt">{{ formatTime(task.updatedAt) }}</time></span>
            </div>
          </details>
        </article>
      </div>
      <div v-else class="download-task-list__empty" role="status">
        <Clock aria-hidden="true" />
        <h3>{{ page === 1 ? '暂无近期任务' : '本页暂无任务' }}</h3>
        <p>{{ page === 1 ? '新建一个下载，或切换其他状态查看。' : '任务状态可能已变化，请返回第一页查看。' }}</p>
        <button v-if="page !== 1" data-first-page class="text-button" type="button" @click="emit('update:page', 1)">返回第一页</button>
        <button v-else-if="statusGroup" class="text-button" type="button" @click="emit('update:statusGroup', '')">查看全部任务</button>
      </div>
      <footer class="task-footer">
        <span data-total role="status" aria-live="polite" aria-atomic="true">{{ groupLabel }} · 共 {{ integer(total) }} 个任务<template v-if="totalPages > 0">，第 {{ page }} / {{ totalPages }} 页</template></span>
        <nav class="download-task-list__pagination" aria-label="近期任务分页" :aria-busy="loading">
          <el-pagination :current-page="page" :page-size="pageSize" :page-count="totalPages" :page-sizes="PAGE_SIZES" :pager-count="5" :layout="totalPages > 1 ? 'sizes, prev, next, pager' : 'sizes, prev, next'" prev-text="上一页" next-text="下一页" :disabled="false" :hide-on-single-page="false" @update:current-page="updatePage" @update:page-size="updatePageSize" />
        </nav>
        <div class="task-footer__help">
          <span>点击名称查看详情</span>
          <span v-if="lastUpdatedAt && !error" class="task-updated">更新于 {{ formatTime(lastUpdatedAt) }}</span>
        </div>
      </footer>
    </template>
  </section>
</template>

<style scoped>
.download-task-list { min-width: 0; padding: 2.5rem 2.1rem; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 1.2rem; padding-bottom: 1.9rem; }
.section-heading h2 { display: flex; align-items: center; flex-wrap: wrap; gap: 0.9rem; min-width: 0; margin: 0; font-size: 1.6rem; font-weight: 600; }
.count { padding: 0 0.5rem; border-radius: 0.4rem; background: var(--tensor-raised); color: var(--tensor-muted); font-size: 1.2rem; font-weight: 400; overflow-wrap: anywhere; }
.text-button { display: inline-flex; align-items: center; gap: 0.5rem; padding: 0; border: 0; background: transparent; color: var(--tensor-accent); font: inherit; font-size: 1.2rem; cursor: pointer; }
.section-heading > button { flex-shrink: 0; }
.text-button svg { width: 1.4rem; height: 1.4rem; }
.text-button:disabled { opacity: .55; cursor: not-allowed; }
.task-action-links { display: flex; flex-wrap: wrap; gap: 1.2rem; }
.task-action-links a { color: var(--tensor-interactive-color); text-underline-offset: 0.3rem; }
.task-filters { display: flex; gap: 2rem; border-bottom: 0.1rem solid var(--tensor-line); }
.task-filters button { padding: 0 0 1rem; border: 0; border-bottom: 0.2rem solid transparent; background: transparent; color: var(--tensor-muted); font: inherit; font-size: 1.4rem; white-space: nowrap; cursor: pointer; }
.task-filters button.active { border-bottom-color: var(--tensor-accent); color: var(--tensor-text); font-weight: 600; }
.task-filters button:hover, .task-name:hover, summary:hover { color: var(--tensor-accent); }
.task-items { max-height: 62rem; overflow-y: auto; outline-offset: -0.3rem; }
.task-row { padding: 1rem 0; border-bottom: 0.1rem solid var(--tensor-line); overflow-wrap: anywhere; }
.task-row:last-child { border-bottom: 0; }
.task-overview { display: flex; align-items: center; gap: 1.1rem; min-height: 5.8rem; }
.task-name { min-width: 0; padding: 0.2rem 0; color: var(--tensor-text); font-size: 1.4rem; font-weight: 500; text-decoration: none; }
.task-name > span { display: block; margin-top: 0.3rem; color: var(--tensor-muted); font: 1.2rem/1.6 'SFMono-Regular', Consolas, monospace; }
.task-progress { display: block; margin-top: 0.3rem; color: var(--tensor-muted); font-size: 1.2rem; font-weight: 400; }
.task-trailing { display: flex; flex-direction: column; align-items: flex-end; gap: 0.5rem; max-width: 48%; margin-left: auto; text-align: right; flex-shrink: 0; }
.task-trailing small { color: var(--tensor-muted); font-size: 1.2rem; }
.task-trailing .text-button { font-size: 1.4rem; }
.download-task-list__status { color: var(--tensor-muted); font-size: 1.2rem; }
.download-task-list__status--running, .download-task-list__status--queued { color: var(--tensor-accent); }
.download-task-list__status--succeeded { color: var(--tensor-success); }
.download-task-list__status--partial_failed, .download-task-list__status--failed, .download-task-list__status--interrupted { color: var(--tensor-warning); }
.task-error { color: var(--tensor-error); }
.task-notice, .task-error { margin: 0.5rem 0; font-size: 1.2rem; line-height: 1.6; }
.task-notice { color: var(--tensor-muted); }
.task-facts { margin-top: 0.4rem; color: var(--tensor-muted); font-size: 1.2rem; }
summary { width: fit-content; cursor: pointer; }
.task-facts__body { display: grid; gap: 0.5rem; padding-top: 1rem; line-height: 1.6; }
.task-facts__body > * { min-width: 0; }
.task-footer { display: grid; gap: 1.2rem; padding-top: 1.2rem; border-top: 0.1rem solid var(--tensor-line); color: var(--tensor-muted); font-size: 1.2rem; overflow-wrap: anywhere; }
.task-footer__help { display: grid; gap: 0.4rem; }
.download-task-list__pagination :deep(.el-pagination) { display: flex; flex-wrap: wrap; min-width: 0; row-gap: 0.8rem; --el-pagination-bg-color: transparent; --el-pagination-button-disabled-bg-color: transparent; }
.download-task-list__pagination :deep(.el-pagination__sizes) { margin: 0 auto 0 0; }
.download-task-list__pagination :deep(.el-select__wrapper) { min-height: 3.4rem; }
.download-task-list__pagination :deep(.el-pager) { flex-basis: 100%; justify-content: center; margin: 0; }
.download-task-list__warning { display: grid; gap: 0.6rem; margin-top: 1.6rem; padding: 1.2rem; background: var(--tensor-accent-bg); color: var(--tensor-text); font-size: 1.2rem; overflow-wrap: anywhere; }
.download-task-list__empty { display: flex; flex-direction: column; align-items: center; padding: 4.5rem 0; color: var(--tensor-muted); text-align: center; }
.download-task-list__empty svg { width: 2.8rem; height: 2.8rem; }
.download-task-list__empty h3 { margin: 1.5rem 0 0; color: var(--tensor-text); font-size: 1.4rem; }
.download-task-list__empty p { margin: 0.9rem 0 1.5rem; font-size: 1.2rem; line-height: 1.6; }
.download-task-list :deep(.async-state-panel) { padding: 2.4rem 0; }
.download-task-list :deep(.async-state-panel__title) { font-size: 1.6rem; }
.download-task-list :deep(.async-state-panel__mark) { width: 3rem; height: 3rem; margin-bottom: 1.2rem; border: 0; background: transparent; }
.download-task-list :deep(.async-state-panel__mark svg) { width: 2.2rem; height: 2.2rem; }
button:focus-visible, a:focus-visible, summary:focus-visible, .task-items:focus-visible { outline: 0.2rem solid var(--tensor-interactive-color); outline-offset: 0.2rem; }
.task-items:focus-visible { outline-offset: -0.2rem; }
code, time, .count, [data-total], .task-progress { font-variant-numeric: tabular-nums; }
@media (max-width: 1200px) { .download-task-list { padding: 2.4rem 1.6rem; } .task-filters { gap: 1.2rem; } }
</style>
