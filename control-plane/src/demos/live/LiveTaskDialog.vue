<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Close, RefreshRight, ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { useDownloadTask } from '../../composables/useDownloadTask.js'
import { taskExtractionNotice, taskStatusLabel } from '../../utils/downloadTaskText.js'
import { formatIngestedAt } from '../../utils/format.js'
import ErrorNotice from './ErrorNotice.vue'

const props = defineProps({ taskId: { type: String, required: true } })
const emit = defineEmits(['close', 'updated'])
const flow = reactive(useDownloadTask()), dialog = ref(null)
const task = computed(() => flow.task)
const progress = computed(() => task.value?.counts.totalBatches > 0n ? Number(task.value.counts.succeededBatches * 10000n / task.value.counts.totalBatches) / 100 : 0)
const nextPage = computed(() => flow.batches && BigInt(flow.page) * BigInt(flow.pageSize) < flow.batches.total)
const batchLabels = { PENDING: '待执行', RUNNING: '运行中', SUCCEEDED: '已成功', FAILED: '失败', SPLIT: '已拆分' }
let returnFocus
async function control(action) {
  await flow[action]()
  emit('updated')
}
onMounted(() => { returnFocus = document.activeElement; dialog.value.showModal(); flow.load(props.taskId) })
onBeforeUnmount(() => { flow.dispose(); dialog.value?.close(); if (returnFocus?.isConnected) returnFocus.focus() })
</script>

<template>
  <dialog ref="dialog" class="detail-dialog live-detail" aria-labelledby="live-detail-title" @cancel.prevent="$emit('close')" @click="event => { if (event.target === dialog) $emit('close') }">
    <header><div><h2 id="live-detail-title">{{ task?.apiName || '任务详情' }}</h2><p class="muted live-task-id">{{ taskId }}</p></div><button class="icon-button" aria-label="关闭任务详情" autofocus @click="$emit('close')"><Close /></button></header>
    <p v-if="flow.invalidTaskId" class="live-notice" role="alert">任务标识无效。</p>
    <p v-if="flow.loading && !task" class="loading-state" role="status">正在加载任务…</p>
    <ErrorNotice :error="flow.taskError" title="任务查询失败" retry-label="重新查询任务" :disabled="flow.loading" @retry="flow.refresh" />
    <p v-if="flow.operationMessage" class="live-notice" role="status">{{ flow.operationMessage }}</p>
    <ErrorNotice :error="flow.operationError" title="任务操作反馈" />
    <template v-if="task">
      <span class="status" :class="task.status">{{ taskStatusLabel(task) }}</span>
      <dl class="confirmation">
        <div><dt>数据源</dt><dd>{{ task.pluginId }}</dd></div>
        <div><dt>提交标识</dt><dd class="live-task-id">{{ task.submissionId }}</dd></div>
        <div><dt>下载方式</dt><dd>{{ task.mode === 'RANGE' ? '批量下载 · 日期范围' : '单次下载' }}</dd></div>
        <div v-for="(value, key) in task.params" :key="key"><dt>{{ key }}</dt><dd>{{ value }}</dd></div>
        <div><dt>创建时间</dt><dd>{{ formatIngestedAt(task.createdAt) }}</dd></div>
        <div><dt>返回记录</dt><dd>{{ task.counts.sourceRows }} 行</dd></div>
        <div><dt>新增 / 更新次数</dt><dd>{{ task.counts.insertedRows }} / {{ task.counts.updatedRows }}</dd></div>
      </dl>
      <div class="detail-progress"><div><span>批次进度</span><strong>{{ task.planReady ? `${task.counts.succeededBatches} / ${task.counts.totalBatches} 批成功` : '正在规划批次' }}</strong></div><progress :value="progress" max="100" aria-label="成功批次进度"></progress><p class="muted">失败 {{ task.counts.failedBatches }} · 待执行 {{ task.counts.pendingBatches }} · 运行中 {{ task.counts.runningBatches }} · 已拆分 {{ task.counts.splitBatches }}</p></div>
      <p v-if="taskExtractionNotice(task)" class="live-notice">{{ taskExtractionNotice(task) }}。任务完成仅表示返回记录已处理。</p>
      <div v-if="task.lastError || task.canRetry || task.canResume" class="task-error"><h3>{{ task.status === 'INTERRUPTED' ? '任务执行已中断' : '任务需要处理' }}</h3><p v-if="task.lastError">{{ task.lastError.message }}</p><p>已成功的批次会保留，继续处理范围由任务当前状态决定。</p><div class="live-actions"><button v-if="task.canRetry" class="button primary" :disabled="!flow.canRetry" @click="control('retry')"><RefreshRight />{{ flow.operation === 'retry' ? '正在重试…' : '重试失败任务' }}</button><button v-if="task.canResume" class="button primary" :disabled="!flow.canResume" @click="control('resume')"><RefreshRight />{{ flow.operation === 'resume' ? '正在恢复…' : '恢复下载' }}</button></div></div>
      <section class="batch-preview" aria-label="批次明细">
        <h3>批次明细 <span v-if="flow.batches" class="count">{{ flow.batches.total }}</span></h3>
        <div class="live-batch-filters">
          <label class="field"><span>批次状态</span><select :value="flow.batchStatus" aria-label="批次状态" @change="flow.changeBatchFilters({ status: $event.target.value })"><option value="">全部状态</option><option v-for="(label, value) in batchLabels" :key="value" :value="value">{{ label }}</option></select></label>
          <label class="live-checkbox"><input type="checkbox" :checked="flow.includeSplit" aria-label="显示拆分父批次" @change="flow.changeBatchFilters({ includeSplit: $event.target.checked })" />显示拆分父批次</label>
        </div>
        <p v-if="flow.batchStatus === 'SPLIT' && !flow.includeSplit" class="mode-note">查看已拆分批次，请勾选“显示拆分父批次”。</p>
        <ErrorNotice :error="flow.batchesError" title="批次查询失败" retry-label="重新加载批次" :disabled="flow.loading" @retry="flow.refresh" />
        <p v-if="flow.loading && !flow.batches" class="muted" role="status">正在加载批次…</p>
        <p v-if="flow.batches && !flow.batches.items.length" class="muted">{{ flow.batchStatus ? '没有匹配的批次。' : '暂无已规划批次。' }}</p>
        <ol v-if="flow.batches"><li v-for="batch in flow.batches.items" :key="batch.batchId"><div><code>{{ batch.batchKey }}</code><span class="status" :class="batch.status">{{ batchLabels[batch.status] }}</span></div><p v-if="batch.rangeStart">{{ batch.rangeStart }} — {{ batch.rangeEnd }}</p><div class="batch-meta"><span>已尝试 {{ batch.attemptCount }} 次</span><span>{{ batch.sourceRows }} 行返回</span></div><p class="muted">新增 {{ batch.insertedRows }} · 更新 {{ batch.updatedRows }}</p><p v-if="batch.status === 'SPLIT'" class="muted">此批次已拆分，执行结果见子批次。</p><details class="live-batch-lineage"><summary>批次标识与来源</summary><p>批次：<code>{{ batch.batchId }}</code></p><p v-if="batch.parentBatchId">父批次：<code>{{ batch.parentBatchId }}</code></p></details><p v-if="batch.error" class="batch-error">{{ batch.error.message }}</p></li></ol>
        <footer class="pagination live-batch-pagination">
          <label>每页 <select :value="flow.pageSize" aria-label="每页批次数" @change="flow.changePageSize(Number($event.target.value))"><option v-for="size in [20, 50, 100]" :key="size" :value="size">{{ size }}</option></select> 条</label>
          <div><span>第 {{ flow.page }} 页</span><button class="icon-button" aria-label="批次上一页" :disabled="flow.page <= 1 || flow.loading" @click="flow.changePage(flow.page - 1)"><ArrowLeft /></button><button class="icon-button" aria-label="批次下一页" :disabled="!nextPage || flow.loading" @click="flow.changePage(flow.page + 1)"><ArrowRight /></button></div>
        </footer>
      </section>
      <p class="detail-footnote">{{ flow.includeSplit ? '当前包含拆分父批次，列表数量可能大于执行批次数。' : '当前展示最终执行批次。' }}区间和批次数可能随任务规划与拆分变化。</p>
    </template>
  </dialog>
</template>
