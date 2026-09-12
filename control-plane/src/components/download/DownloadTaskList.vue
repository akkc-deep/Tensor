<script setup>
import { computed, nextTick, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { parseTaskJson } from '../../api/downloadTaskDtos.js'
import AsyncStatePanel from '../common/AsyncStatePanel.vue'
import WorkbenchPanel from '../common/WorkbenchPanel.vue'

const PAGE_SIZES = [20, 50, 100]
const MAX_PAGE = 2_147_483_647n
const STATUS_LABELS = Object.freeze({
  QUEUED: '排队中',
  RUNNING: '运行中',
  SUCCEEDED: '已成功',
  PARTIAL_FAILED: '部分失败',
  FAILED: '失败',
  INTERRUPTED: '已中断',
})

const props = defineProps({
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  result: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: Object, default: null },
  lastUpdatedAt: { type: Date, default: null },
})

const emit = defineEmits(['refresh', 'update:page', 'update:pageSize'])

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
  return new Intl.DateTimeFormat(undefined, {
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
  <WorkbenchPanel
    class="download-task-list"
    heading-id="download-task-list-title"
    title="近期任务"
  >
    <div class="download-task-list__toolbar">
      <span data-total role="status" aria-live="polite" aria-atomic="true">
        共 {{ integer(total) }} 个任务，第 {{ page }} / {{ totalPages }} 页
      </span>
      <el-button
        data-refresh
        native-type="button"
        :aria-busy="loading && result !== null"
        @click="emit('refresh')"
      >
        刷新状态
      </el-button>
    </div>

    <div
      v-if="error && result"
      class="download-task-list__warning"
      role="status"
    >
      <strong>状态暂时无法更新</strong>
      <span>{{ errorMessage(error) }}</span>
      <span v-if="lastUpdatedAt">
        上次更新 {{ formatTime(lastUpdatedAt) }}
      </span>
    </div>

    <AsyncStatePanel
      v-if="loading && !result"
      state="LOADING"
      title="正在加载近期任务"
      message="请稍候。"
    />
    <AsyncStatePanel
      v-else-if="error && !result"
      state="FAILURE"
      title="近期任务加载失败"
      :message="errorMessage(error)"
      :request-id="error.requestId ?? ''"
      retry-label="重新加载"
      @retry="emit('refresh')"
    />
    <AsyncStatePanel
      v-else-if="!result"
      state="INITIAL"
      title="近期任务尚未加载"
      message="页面可见时将自动加载。"
    />

    <template v-else>
      <div
        v-if="rows.length"
        class="download-task-list__scroll"
        role="region"
        aria-label="近期任务表格"
        tabindex="0"
      >
        <table>
          <thead>
            <tr>
              <th scope="col">接口 / 参数</th>
              <th scope="col">模式 / 状态</th>
              <th scope="col">当前批次</th>
              <th scope="col">写入次数</th>
              <th scope="col">时间</th>
              <th scope="col">入口</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="task in rows" :key="task.taskId">
              <td>
                <strong>{{ task.pluginId }} / {{ task.apiName }}</strong>
                <code
                  v-for="parameter in parameters(task.params)"
                  :key="parameter"
                >
                  {{ parameter }}
                </code>
              </td>
              <td>
                <span>{{ task.mode === 'RANGE' ? '日期区间' : '单次请求' }}</span>
                <strong
                  data-task-status
                  class="download-task-list__status"
                  :class="`download-task-list__status--${task.status.toLowerCase()}`"
                >
                  {{ STATUS_LABELS[task.status] }}
                </strong>
              </td>
              <td>
                <template v-if="task.planReady">
                  <strong>
                    已结束 {{ integer(finished(task.counts)) }} / 当前计划
                    {{ integer(task.counts.totalBatches) }} 批
                  </strong>
                  <span>成功 {{ integer(task.counts.succeededBatches) }}</span>
                  <span>失败 {{ integer(task.counts.failedBatches) }}</span>
                  <span>待执行 {{ integer(task.counts.pendingBatches) }}</span>
                  <span>运行中 {{ integer(task.counts.runningBatches) }}</span>
                  <span>独立拆分 {{ integer(task.counts.splitBatches) }}</span>
                </template>
                <strong v-else>尚未生成计划</strong>
              </td>
              <td>
                <strong>
                  新增记录次数 {{ integer(task.counts.insertedRows) }}
                </strong>
                <strong>
                  更新记录次数 {{ integer(task.counts.updatedRows) }}
                </strong>
                <small>已提交写入操作次数，不是整段去重总量</small>
              </td>
              <td>
                <span>
                  创建
                  <time :datetime="task.createdAt" :title="task.createdAt">
                    {{ formatTime(task.createdAt) }}
                  </time>
                </span>
                <span>
                  更新
                  <time :datetime="task.updatedAt" :title="task.updatedAt">
                    {{ formatTime(task.updatedAt) }}
                  </time>
                </span>
              </td>
              <td>
                <RouterLink :to="`/downloads/tasks/${task.taskId}`">查看任务</RouterLink>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="download-task-list__empty">
        <p>{{ page === 1 ? '暂无近期任务' : '本页暂无任务' }}</p>
        <el-button
          v-if="page !== 1"
          native-type="button"
          @click="emit('update:page', 1)"
        >
          返回第一页
        </el-button>
      </div>

      <nav
        class="download-task-list__pagination"
        aria-label="近期任务分页"
        :aria-busy="loading"
      >
        <el-pagination
          :current-page="page"
          :page-size="pageSize"
          :page-count="totalPages"
          :page-sizes="PAGE_SIZES"
          :pager-count="5"
          layout="sizes, prev, pager, next"
          prev-text="上一页"
          next-text="下一页"
          :disabled="false"
          :hide-on-single-page="false"
          @update:current-page="updatePage"
          @update:page-size="updatePageSize"
        />
      </nav>
    </template>
  </WorkbenchPanel>
</template>

<style scoped>
.download-task-list {
  min-width: 0;
}

.download-task-list__toolbar,
.download-task-list__warning,
.download-task-list__empty,
.download-task-list__pagination {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 18px 24px;
}

.download-task-list__toolbar {
  justify-content: space-between;
  border-bottom: 1px solid var(--tensor-line);
}

.download-task-list__toolbar span,
.download-task-list__warning,
.download-task-list__empty,
.download-task-list__pagination {
  color: var(--tensor-muted);
  font-size: 12px;
  line-height: 1.8;
}

.download-task-list__warning {
  color: var(--tensor-text);
  background: var(--tensor-accent-bg);
}

.download-task-list__warning span:last-child {
  color: var(--tensor-muted);
}

.download-task-list__scroll {
  max-width: 100%;
  overflow-x: auto;
  outline-offset: -3px;
}

.download-task-list__scroll:focus-visible {
  outline: 3px solid var(--tensor-interactive-color);
}

table {
  width: 100%;
  min-width: 1120px;
  border-collapse: collapse;
  color: var(--tensor-text);
  font-size: 12px;
  line-height: 1.6;
}

th,
td {
  padding: 16px;
  text-align: left;
  vertical-align: top;
  border-bottom: 1px solid var(--tensor-line);
}

th {
  color: var(--tensor-muted);
  font-weight: 600;
  background: var(--tensor-raised);
}

td > strong,
td > span,
td > code,
td > small {
  display: block;
}

td > * + * {
  margin-top: 5px;
}

td code,
td time,
[data-total] {
  font-variant-numeric: tabular-nums;
}

td code,
td small {
  color: var(--tensor-muted);
}

.download-task-list__status {
  color: var(--tensor-muted);
}

.download-task-list__status--running,
.download-task-list__status--queued {
  color: var(--tensor-accent);
}

.download-task-list__status--succeeded {
  color: var(--tensor-success);
}

.download-task-list__status--partial_failed,
.download-task-list__status--failed,
.download-task-list__status--interrupted {
  color: var(--tensor-error);
}

.download-task-list__empty {
  min-height: 120px;
  justify-content: center;
}

.download-task-list__empty p {
  margin: 0;
}

.download-task-list__pagination {
  border-top: 1px solid var(--tensor-line);
}

.download-task-list__pagination :deep(.el-pagination) {
  display: flex;
  flex-wrap: wrap;
  min-width: 0;
}

@media (max-width: 680px) {
  .download-task-list__toolbar,
  .download-task-list__warning,
  .download-task-list__empty,
  .download-task-list__pagination {
    padding-right: 18px;
    padding-left: 18px;
  }

  .download-task-list__pagination :deep(.el-pagination__sizes) {
    flex: 1 0 100%;
    margin-right: 0;
  }
}
</style>
