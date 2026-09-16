<script setup>
import {
  computed,
  inject,
  onActivated,
  onDeactivated,
  onMounted,
  onUnmounted,
  ref,
} from 'vue'
import { RouterLink } from 'vue-router'

defineOptions({ name: 'DownloadView' })

import AsyncStatePanel from '../components/common/AsyncStatePanel.vue'
import PageHeading from '../components/common/PageHeading.vue'
import { Check, Grid } from '@element-plus/icons-vue'
import ApiSelect from '../components/download/ApiSelect.vue'
import DataSourceSelect from '../components/download/DataSourceSelect.vue'
import DownloadAction from '../components/download/DownloadAction.vue'
import DownloadTaskList from '../components/download/DownloadTaskList.vue'
import DynamicParameterForm from '../components/download/DynamicParameterForm.vue'
import { parseTaskJson } from '../api/downloadTaskDtos.js'
import { useDownloadFlow } from '../composables/useDownloadFlow.js'
import { useDownloadTaskList } from '../composables/useDownloadTaskList.js'
import { createDownloadTaskChannel, downloadTaskChannelKey, useDownloadTask } from '../composables/useDownloadTask.js'

const parameterForm = ref(null)
const recoveryUnavailable = ref(false)
const taskList = useDownloadTaskList()
const channel = inject(downloadTaskChannelKey, null) ?? createDownloadTaskChannel()
const quick = useDownloadTask({ channel, loadBatches: false, poll: false })
const quickTarget = ref(null), quickSelecting = ref(false)
const actionBusy = computed(() => quickSelecting.value || quick.loading.value || quick.operation.value !== null)
const action = computed(() => quickTarget.value && ({
  ...quickTarget.value,
  message: quick.operation.value
    ? quick.operation.value === 'retry' ? '正在提交重试…' : '正在提交恢复…'
    : quickSelecting.value ? '正在确认任务状态…' : quick.operationMessage.value,
  error: quick.taskError.value ?? quick.operationError.value,
}))
const stopTaskChanges = channel.subscribe(({ type }) => { if (!type) taskList.refresh() })

async function controlTask(row, type) {
  if (actionBusy.value || taskList.error.value || !['retry', 'resume'].includes(type) || channel.pending.has(row.taskId)) return
  quickSelecting.value = true
  quickTarget.value = { taskId: row.taskId, apiName: row.apiName }
  quick.operationMessage.value = ''
  quick.operationError.value = null
  try {
    const loaded = await quick.load(row.taskId)
    if (!loaded || !viewActive) return
    if (type === 'retry' ? quick.canRetry.value : quick.canResume.value) await quick[type]()
    else {
      quick.operationMessage.value = '任务状态已变化，当前操作不可用，请查看最新详情。'
      taskList.refresh()
    }
  } finally { quickSelecting.value = false }
}

const flow = useDownloadFlow({ onAccepted: taskList.onAccepted })
const {
  metadataState,
  sources,
  apis,
  selectedPluginId,
  selectedApiName,
  selectedSource,
  selectedApi,
  capabilities,
  mode,
  parameters,
  formKey,
  metadataError,
  submissionState,
  pendingSubmission,
  receipt,
  recoveredTask,
  submissionError,
  storageError,
  locked,
  canSubmit,
  load,
  selectSource,
  selectApi,
  selectMode,
  retryMetadata,
  submit,
  recoverSubmission,
  replaySubmission,
  retryStorage,
  clearCorruptSubmission,
  dispose: disposeFlow,
} = flow

const catalogLoading = computed(() => ['INITIAL', 'LOADING'].includes(metadataState.value) && !selectedApiName.value)
const catalogFailure = computed(() => metadataState.value === 'FAILURE' && !selectedApiName.value)
const queryModeLabels = { trade_date: '交易日', ann_date: '公告日', snapshot: '快照', date_range: '日期范围' }

const acceptedTask = computed(() => receipt.value ?? recoveredTask.value)
const apiDisabled = computed(
  () => selectedSource.value?.downloadAvailable !== true || locked.value,
)
const rangeAvailable = computed(
  () => capabilities.value?.range.availability === 'AVAILABLE',
)
const completenessNote = computed(() => {
  if (mode.value !== 'RANGE') return '单次请求，结果不代表完整历史。'
  const range = capabilities.value?.range
  const rule = range?.completenessRule
  if (rule?.kind === 'RESPONSE_ONLY') return '按所选日期区间采集本次接口返回的记录。数据完整性未确认，可能存在上游截断。'
  if (rule?.kind === 'CONFIRMED_ROW_LIMIT') return `单次返回上限为 ${rule.rowLimit} 行。${range.splittable ? '达到上限时按规则拆分日期区间。' : '完整性以任务执行结果为准。'}`
  if (rule?.kind === 'VERIFIED_RULE') return '采用已验证的完整性规则，执行结果以任务详情为准。'
  return ''
})
const pendingParameters = computed(() =>
  Object.entries(pendingSubmission.value?.params ?? {}).sort(([left], [right]) =>
    left.localeCompare(right),
  ),
)
const rejectionHelp = computed(() => {
  const code = submissionError.value?.code
  if (code === 'PARAM_REQUIRED' || code === 'PARAM_INVALID') return '请修改上述参数后重新提交。'
  if (code === 'TASK_QUEUE_FULL') return '任务队列已满，请稍后重新提交。'
  return '请重新选择接口以更新下载配置，再尝试提交。'
})

function safeErrorMessage(error) {
  if (
    error?.kind === 'INVALID_RESPONSE' &&
    typeof parseTaskJson('{"n":1}') === 'string'
  ) {
    return '当前浏览器无法保真处理任务数值，请更新浏览器后重试。'
  }
  return error?.message ?? ''
}

async function handleRecovery() {
  recoveryUnavailable.value = false
  const recovered = await recoverSubmission()
  if (
    mounted &&
    !recovered &&
    submissionState.value === 'UNCERTAIN' &&
    submissionError.value
  ) {
    recoveryUnavailable.value = true
  }
  return recovered
}

async function handleSubmit() {
  if (!selectedApi.value || !canSubmit.value) return
  if (parameters.value.length === 0) {
    await submit({})
    return
  }
  if (!parameterForm.value || !(await parameterForm.value.validate())) return
  await submit(parameterForm.value.normalizedValues())
}

let mounted = true
let viewActive = true
let recoveryComplete = false
let bootstrapped = false

async function startView() {
  if (!mounted || !viewActive || bootstrapped) return
  bootstrapped = true
  await Promise.allSettled([load(), taskList.start()])
}

onMounted(async () => {
  await handleRecovery()
  if (!mounted) return
  recoveryComplete = true
  await startView()
})

onActivated(() => {
  viewActive = true
  taskList.setActive(true)
  quick.setActive(true)
  if (recoveryComplete) startView()
})

onDeactivated(() => {
  viewActive = false
  taskList.setActive(false)
  quick.setActive(false)
})

onUnmounted(() => {
  mounted = false
  viewActive = false
  disposeFlow()
  taskList.dispose()
  quick.dispose()
  stopTaskChanges()
})
</script>

<template>
  <section class="page" aria-labelledby="downloads-title">
    <PageHeading
      id="downloads-title"
      title="下载工作台"
      description="接口、参数、任务，在同一视野。"
    >
      <span v-if="selectedSource" class="source-indicator">
        {{ selectedSource.displayName }} <span v-if="!catalogLoading && !catalogFailure">/ {{ apis.length }} 个接口</span>
      </span>
    </PageHeading>

    <div class="studio-layout">
      <section class="catalog-panel" aria-labelledby="catalog-title" :aria-busy="catalogLoading">
        <header class="panel-heading">
          <h2 id="catalog-title">接口目录 <span v-if="selectedSource && !catalogLoading && !catalogFailure" class="catalog-count">{{ apis.length }}</span></h2>
        </header>
        <DataSourceSelect
          v-if="sources.length"
          class="catalog-source"
          :model-value="selectedPluginId"
          :sources="sources"
          :disabled="locked"
          @update:model-value="selectSource"
        />
        <AsyncStatePanel
          v-if="catalogLoading"
          state="LOADING"
          :title="selectedPluginId ? '正在加载接口目录' : '正在加载下载配置'"
          message="请稍候。"
        />
        <AsyncStatePanel
          v-else-if="catalogFailure"
          state="FAILURE"
          :title="selectedPluginId ? '接口目录加载失败' : '下载配置加载失败'"
          :message="safeErrorMessage(metadataError)"
          :request-id="metadataError?.requestId ?? ''"
          :retry-label="selectedPluginId ? '重新加载目录' : '重新加载配置'"
          @retry="retryMetadata"
        />
        <p v-else-if="!sources.length" class="catalog-notice" role="status">暂无数据源。</p>
        <p v-else-if="!selectedSource" class="catalog-notice">请选择数据源以查看接口。</p>
        <ApiSelect
          v-else
          :key="selectedPluginId"
          :model-value="selectedApiName"
          :apis="apis"
          :source-name="selectedSource.displayName"
          :disabled="apiDisabled"
          @update:model-value="selectApi"
        />
      </section>
      <div class="studio-form">
        <template v-if="selectedApi">
          <div class="selected-api-heading">
            <span class="api-glyph" aria-hidden="true"><Grid /></span>
            <div><h2>{{ selectedApi.displayName }}</h2><code>{{ selectedApi.apiName }}</code></div>
          </div>
          <p class="selected-api-meta">{{ selectedApi.category }} · {{ queryModeLabels[selectedApi.queryMode] ?? selectedApi.queryMode }}</p>
        </template>
        <p v-else class="catalog-notice">请选择数据接口，从左侧目录开始。</p>
        <AsyncStatePanel
          v-if="selectedApi && metadataState === 'LOADING'"
          state="LOADING"
          title="正在加载接口能力"
          message="正在获取当前接口的下载模式与参数。"
        />
        <AsyncStatePanel
          v-else-if="selectedApi && metadataState === 'FAILURE'"
          state="FAILURE"
          title="接口能力加载失败"
          :message="safeErrorMessage(metadataError)"
          :request-id="metadataError?.requestId ?? ''"
          retry-label="重新加载能力"
          @retry="retryMetadata"
        />
        <div class="download-config-panel">
          <section v-if="selectedApi && capabilities" class="form-parameters" aria-labelledby="download-mode-label">
            <header class="mode-heading"><h3 id="download-mode-label">下载方式</h3><span>{{ mode === 'RANGE' ? '按日期范围' : '单次请求' }}</span></header>
            <div class="mode-control" role="group" aria-labelledby="download-mode-label">
              <button type="button" data-mode="SINGLE" :class="{ selected: mode === 'SINGLE' }" :aria-pressed="mode === 'SINGLE'" :disabled="locked || !capabilities.single.available" @click="selectMode('SINGLE')"><Check v-if="mode === 'SINGLE'" aria-hidden="true" />单次下载<small v-if="!capabilities.single.available">不支持</small></button>
              <button type="button" data-mode="RANGE" :class="{ selected: mode === 'RANGE' }" :aria-pressed="mode === 'RANGE'" :disabled="locked || !rangeAvailable" @click="selectMode('RANGE')"><Check v-if="mode === 'RANGE'" aria-hidden="true" />批量下载<small v-if="!rangeAvailable">{{ capabilities.range.availability === 'NEEDS_VERIFICATION' ? '待验证' : '不支持' }}</small></button>
            </div>
            <p v-if="!rangeAvailable" class="mode-note">{{ capabilities.range.unavailableReason }}</p>
            <DynamicParameterForm v-if="parameters.length" :key="formKey" ref="parameterForm" :parameters="parameters" :range="mode === 'RANGE' ? capabilities.range : null" :disabled="locked" />
            <p v-else class="parameter-empty">此模式无需填写请求参数。</p>
            <p v-if="completenessNote" class="mode-note completeness-note">{{ completenessNote }}</p>
          </section>
          <footer class="form-footer">
            <DownloadAction :mode="mode" :disabled="!canSubmit" :submitting="locked" :recovering="submissionState === 'RECOVERING'" @submit="handleSubmit" />
            <p class="form-footer__help">接收后可继续提交其他任务</p>
          </footer>
          <div v-if="submissionState !== 'IDLE' || storageError" class="download-feedback" :aria-busy="locked">
            <AsyncStatePanel
              v-if="submissionState === 'RECOVERING'"
              state="LOADING"
              title="正在找回原任务"
              message="正在使用原提交标识查询，请稍候。"
            />
            <AsyncStatePanel
              v-else-if="submissionState === 'SUBMITTING'"
              state="LOADING"
              title="正在提交任务"
              message="正在等待服务确认接收，请勿重复提交。"
            />
            <AsyncStatePanel
              v-else-if="storageError?.kind === 'CORRUPT'"
              state="FAILURE"
              title="本地提交记录无法恢复"
              :message="storageError.message"
            >
              <p class="local-record-note">
                仅清除此标签页记录，不会取消服务端任务。
              </p>
              <template #actions>
                <button class="feedback-button" type="button" @click="clearCorruptSubmission">
                  清除损坏的本地记录
                </button>
              </template>
            </AsyncStatePanel>
            <AsyncStatePanel
              v-else-if="storageError?.kind === 'READ'"
              state="FAILURE"
              title="无法读取本地提交记录"
              :message="storageError.message"
            >
              <template #actions>
                <button class="feedback-button" type="button" @click="retryStorage">重新读取</button>
              </template>
            </AsyncStatePanel>
            <AsyncStatePanel
              v-else-if="storageError?.kind === 'WRITE'"
              state="FAILURE"
              title="任务未接收"
              :message="storageError.message"
            >
              <p>请求尚未发送。请允许当前标签页使用本地存储后，点击上方下载按钮重试。</p>
            </AsyncStatePanel>
            <AsyncStatePanel
              v-else-if="submissionState === 'ACCEPTED'"
              state="SUCCESS"
              title="任务已接收"
              message="服务已确认任务身份，进度以近期任务或详情为准。"
            >
              <p class="task-identity">
                <span v-if="recoveredTask">
                  {{ recoveredTask.pluginId }} / {{ recoveredTask.apiName }}
                </span>
                <code>{{ acceptedTask.taskId }}</code>
                <RouterLink :to="`/downloads/tasks/${acceptedTask.taskId}`">查看任务</RouterLink>
              </p>
              <p v-if="submissionError">该提交标识已对应任务，请核对参数。</p>
              <template v-if="storageError?.kind === 'REMOVE'">
                <p>{{ storageError.message }}</p>
                <button class="feedback-button" type="button" @click="retryStorage">重新清除记录</button>
              </template>
            </AsyncStatePanel>
            <AsyncStatePanel
              v-else-if="submissionState === 'UNCERTAIN'"
              state="FAILURE"
              title="提交结果尚未确认"
              :message="safeErrorMessage(submissionError) || '请使用原提交标识找回任务。'"
              :request-id="submissionError?.requestId ?? ''"
            >
              <p v-if="pendingSubmission">请先确认原任务；重新确认始终使用以下原参数，不使用当前表单草稿。</p>
              <p v-if="recoveryUnavailable">状态暂时无法更新</p>
              <p v-if="storageError?.kind === 'REMOVE'">{{ storageError.message }}</p>
              <template #actions>
                <button class="feedback-button" type="button" :disabled="locked" @click="handleRecovery">
                  重新查找
                </button>
                <button class="feedback-button"
                  v-if="pendingSubmission"
                  type="button"
                  :disabled="locked"
                  @click="replaySubmission"
                >
                  使用原参数重新确认
                </button>
                <button class="feedback-button"
                  v-if="storageError?.kind === 'REMOVE'"
                  type="button"
                  @click="retryStorage"
                >
                  重新清除记录
                </button>
              </template>
            </AsyncStatePanel>
            <AsyncStatePanel
              v-else-if="submissionState === 'REJECTED'"
              state="FAILURE"
              title="任务未接收"
              :message="safeErrorMessage(submissionError)"
              :request-id="submissionError?.requestId ?? ''"
            >
              <ul v-if="submissionError?.fieldErrors?.length" class="submission-fields">
                <li v-for="fieldError in submissionError.fieldErrors" :key="fieldError.field">
                  <code>{{ fieldError.field }}</code>：{{ fieldError.message }}
                </li>
              </ul>
              <p>{{ rejectionHelp }}</p>
              <p v-if="storageError?.kind === 'REMOVE'">{{ storageError.message }}</p>
              <template v-if="storageError?.kind === 'REMOVE'" #actions>
                <button class="feedback-button" type="button" @click="retryStorage">重新清除记录</button>
              </template>
            </AsyncStatePanel>
            <div v-if="pendingSubmission" class="pending-submission" aria-label="原提交参数">
              <strong>{{ pendingSubmission.pluginId }} / {{ pendingSubmission.apiName }}</strong>
              <span>{{ pendingSubmission.mode === 'RANGE' ? '日期区间' : '单次请求' }}</span>
              <span>提交标识：<code>{{ pendingSubmission.submissionId }}</code></span>
              <code v-for="[name, value] in pendingParameters" :key="name">{{ name }}={{ value }}</code>
            </div>
          </div>
        </div>
      </div>
      <aside class="studio-tasks">
        <DownloadTaskList
          :action="action"
          :action-busy="actionBusy"
          :pending-operations="channel.pending"
          :status-group="taskList.statusGroup.value"
          :page="taskList.page.value"
          :page-size="taskList.pageSize.value"
          :result="taskList.result.value"
          :loading="taskList.loading.value"
          :error="taskList.error.value"
          :last-updated-at="taskList.lastUpdatedAt.value"
          @refresh="taskList.refresh"
          @control="controlTask"
          @refresh-action="quick.refresh"
          @update:status-group="taskList.changeStatusGroup"
          @update:page="taskList.changePage"
          @update:page-size="taskList.changePageSize"
        />
      </aside>
    </div>
  </section>
</template>

<style scoped>
.catalog-panel { display: flex; flex-direction: column; }
.catalog-panel h2 { display: flex; align-items: center; gap: 0.9rem; }
.catalog-count { display: inline-grid; place-items: center; min-width: 2rem; height: 1.9rem; padding: 0 0.5rem; border-radius: 0.4rem; background: var(--tensor-raised); font-weight: 400; }
.catalog-source { margin: 0 1.4rem 0.8rem; font-size: 1.2rem; }
.catalog-source :deep(.el-select__wrapper) { min-height: 3.6rem; padding: 0 1.2rem; }
.catalog-source :deep(.el-select__caret) { color: var(--tensor-muted); }
.catalog-notice { margin: 0; padding: 2.8rem; color: var(--tensor-muted); font-size: 1.3rem; }
.catalog-panel .catalog-notice { padding: 2rem 1.8rem; }
.catalog-panel :deep(.async-state-panel) { padding: 2rem 1.8rem; }
.catalog-panel :deep(.async-state-panel__title) { font-size: 1.6rem; }
.selected-api-heading { display: flex; align-items: center; gap: 1.3rem; padding: 2.8rem 2.8rem 0; }
.selected-api-heading > div { min-width: 0; overflow-wrap: anywhere; }
.selected-api-heading h2 { margin: 0; font-size: 2.1rem; font-weight: 600; line-height: 1.5; }
.selected-api-heading code { color: var(--tensor-muted); font: 1.2rem 'SFMono-Regular', Consolas, monospace; }
.api-glyph { display: grid; place-items: center; flex-shrink: 0; width: 4rem; height: 4rem; border: 0.1rem solid var(--tensor-line); border-radius: 0.8rem; color: var(--tensor-accent); }
.api-glyph svg { width: 1.8rem; height: 1.8rem; }
.selected-api-meta { margin: 1.6rem 2.8rem 0; color: var(--tensor-muted); font-size: 1.2rem; overflow-wrap: anywhere; }
@media (max-width: 1200px) {
  .selected-api-heading { padding-left: 2.2rem; padding-right: 2.2rem; }
  .selected-api-meta { margin-left: 2.2rem; margin-right: 2.2rem; }
}

.download-config-panel { padding: 0 2.8rem; }
.form-parameters, .download-config-panel .form-footer { max-width: 68rem; }
.form-parameters { margin-top: 2.6rem; padding-top: 2.4rem; border-top: 0.1rem solid var(--tensor-line); }
.mode-heading { display: flex; align-items: center; justify-content: space-between; gap: 1.2rem; margin-bottom: 1.2rem; }
.mode-heading h3 { margin: 0; font-size: 1.4rem; font-weight: 500; }
.mode-heading > span { font-size: 1.2rem; color: var(--tensor-muted); }
.mode-control { display: grid; grid-template-columns: 1fr 1fr; padding: 0.4rem; border: 0.1rem solid var(--tensor-line); border-radius: 0.7rem; gap: 0.4rem; margin-bottom: 2.4rem; background: var(--tensor-raised); }
.mode-control button { display: flex; align-items: center; justify-content: center; gap: 0.5rem; border: 0; border-radius: 0.4rem; height: 3.4rem; background: transparent; color: var(--tensor-text); font: inherit; font-size: 1.4rem; white-space: nowrap; cursor: pointer; }
.mode-control button.selected { background: var(--tensor-surface); color: var(--tensor-accent); box-shadow: 0 0.1rem 0.3rem #0000000a; }
.mode-control button:disabled { opacity: .55; cursor: not-allowed; }
.mode-control button:focus-visible { outline: 0.2rem solid var(--tensor-accent); outline-offset: 0.2rem; }
.mode-control svg { width: 1.3rem; height: 1.3rem; }
.mode-control small { font-size: 1.2rem; padding: 0.1rem 0.4rem; background: var(--tensor-line); border-radius: 0.3rem; }
.mode-note { margin: 1.2rem 0 2rem; font-size: 1.2rem; color: var(--tensor-muted); overflow-wrap: anywhere; }
.download-config-panel .form-footer { flex-direction: column; align-items: flex-start; gap: 1.3rem; padding: 2.3rem 0 2.8rem; margin-top: 2.9rem; border-top: 0.1rem solid var(--tensor-line); }
@media (max-width: 1200px) { .download-config-panel { padding: 0 2.2rem; } }

.download-feedback { min-width: 0; margin-bottom: 2.8rem; border-top: 0.1rem solid var(--tensor-line); }
.download-feedback :deep(.async-state-panel) { display: grid; grid-template-columns: 1.8rem minmax(0, 1fr); align-items: start; column-gap: 0.9rem; padding: 1.8rem 0 0; }
.download-feedback :deep(.async-state-panel > *) { grid-column: 2; min-width: 0; }
.download-feedback :deep(.async-state-panel__mark) { grid-column: 1; grid-row: 1; width: 1.8rem; height: 2rem; margin: 0; border: 0; border-radius: 0; background: transparent; }
.download-feedback :deep(.async-state-panel__mark svg) { width: 1.8rem; height: 1.8rem; }
.download-feedback :deep(.async-state-panel__title) { font-size: 1.4rem; font-weight: 600; line-height: 2rem; letter-spacing: normal; }
.download-feedback :deep(p) { margin: 0.8rem 0 0; color: var(--tensor-muted); font-size: 1.2rem; line-height: 1.8; overflow-wrap: anywhere; }
.download-feedback :deep(.async-state-panel__actions) { gap: 0.8rem; margin-top: 1.2rem; }
.feedback-button { min-height: 3.2rem; padding: 0.5rem 1rem; border: 0.1rem solid var(--tensor-line); border-radius: 0.6rem; background: var(--tensor-surface); color: var(--tensor-text); font: inherit; font-size: 1.2rem; cursor: pointer; }
.feedback-button:hover:not(:disabled) { background: var(--tensor-raised); }
.feedback-button:disabled { opacity: .55; cursor: not-allowed; }
.feedback-button:focus-visible { outline: 0.2rem solid var(--tensor-accent); outline-offset: 0.2rem; }

.task-identity,
.pending-submission,
.local-record-note,
.submission-fields {
  margin: 0;
  color: var(--tensor-muted);
  font-size: 1.2rem;
  line-height: 1.8;
}

.task-identity,
.pending-submission {
  display: grid;
  gap: 0.6rem;
  margin-top: 1.6rem;
}

.task-identity code,
.pending-submission code {
  color: var(--tensor-text);
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.task-identity a {
  width: fit-content;
  color: var(--tensor-interactive-color);
  text-underline-offset: 0.3rem;
}

.task-identity a:focus-visible { outline: 0.2rem solid var(--tensor-accent); outline-offset: 0.2rem; }

.pending-submission { padding: 1.2rem; border-radius: 0.6rem; background: var(--tensor-subtle); overflow-wrap: anywhere; }

.submission-fields {
  padding-left: 2rem;
}

</style>
