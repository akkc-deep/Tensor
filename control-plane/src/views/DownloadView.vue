<script setup>
import {
  computed,
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
import WorkbenchPanel from '../components/common/WorkbenchPanel.vue'
import ApiDescription from '../components/download/ApiDescription.vue'
import ApiSelect from '../components/download/ApiSelect.vue'
import DataSourceSelect from '../components/download/DataSourceSelect.vue'
import DownloadAction from '../components/download/DownloadAction.vue'
import DownloadTaskList from '../components/download/DownloadTaskList.vue'
import DynamicParameterForm from '../components/download/DynamicParameterForm.vue'
import { parseTaskJson } from '../api/downloadTaskDtos.js'
import { useDownloadFlow } from '../composables/useDownloadFlow.js'
import { useDownloadTaskList } from '../composables/useDownloadTaskList.js'

const parameterForm = ref(null)
const recoveryUnavailable = ref(false)
const taskList = useDownloadTaskList()
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

const acceptedTask = computed(() => receipt.value ?? recoveredTask.value)
const apiDisabled = computed(
  () => selectedSource.value?.downloadAvailable !== true || locked.value,
)
const rangeAvailable = computed(
  () => capabilities.value?.range.availability === 'AVAILABLE',
)
const pendingParameters = computed(() =>
  Object.entries(pendingSubmission.value?.params ?? {}).sort(([left], [right]) =>
    left.localeCompare(right),
  ),
)
const submissionMeta = computed(() => {
  if (submissionState.value === 'UNCERTAIN' || submissionState.value === 'SUBMITTING') {
    return pendingSubmission.value?.apiName ?? ''
  }
  if (submissionState.value === 'ACCEPTED') {
    return recoveredTask.value?.apiName ?? ''
  }
  return submissionState.value === 'IDLE' ? selectedApiName.value : ''
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
  if (recoveryComplete) startView()
})

onDeactivated(() => {
  viewActive = false
  taskList.setActive(false)
})

onUnmounted(() => {
  mounted = false
  viewActive = false
  disposeFlow()
  taskList.dispose()
})
</script>

<template>
  <section class="page" aria-labelledby="downloads-title">
    <PageHeading
      id="downloads-title"
      title="数据下载"
      description="选择数据接口，把市场数据接入你的研究。"
    />

    <div class="download-grid">
      <WorkbenchPanel
        class="download-config-panel"
        heading-id="download-config-title"
        title="下载配置"
        :meta="selectedPluginId"
      >
        <div class="setup-body" :aria-busy="metadataState === 'LOADING'">
          <AsyncStatePanel
            v-if="metadataState === 'LOADING' && sources.length === 0"
            state="LOADING"
            title="正在加载下载配置"
            message="请稍候。"
          />
          <AsyncStatePanel
            v-else-if="metadataState === 'FAILURE' && sources.length === 0"
            state="FAILURE"
            title="下载配置加载失败"
            :message="safeErrorMessage(metadataError)"
            :request-id="metadataError.requestId ?? ''"
            retry-label="重新加载配置"
            @retry="retryMetadata"
          />
          <template v-else>
            <div class="workbench-selects">
              <DataSourceSelect
                :model-value="selectedPluginId"
                :sources="sources"
                :disabled="locked"
                @update:model-value="selectSource"
              />
              <ApiSelect
                :model-value="selectedApiName"
                :apis="apis"
                :disabled="apiDisabled"
                @update:model-value="selectApi"
              />
            </div>
            <ApiDescription :api="selectedApi" />

            <AsyncStatePanel
              v-if="metadataState === 'FAILURE'"
              state="FAILURE"
              title="下载配置加载失败"
              :message="safeErrorMessage(metadataError)"
              :request-id="metadataError.requestId ?? ''"
              retry-label="重新加载配置"
              @retry="retryMetadata"
            />

            <section
              v-if="selectedApi && capabilities"
              class="parameter-group"
              aria-labelledby="download-parameters-title"
            >
              <header class="parameter-title">
                <h3 id="download-parameters-title">请求参数</h3>
                <span>按能力与模式生成</span>
              </header>
              <div class="download-mode">
                <span id="download-mode-label">下载模式</span>
                <el-radio-group
                  :model-value="mode"
                  aria-labelledby="download-mode-label"
                  :disabled="locked"
                  @update:model-value="selectMode"
                >
                  <el-radio-button
                    value="SINGLE"
                    :disabled="!capabilities.single.available"
                  >
                    单次请求
                  </el-radio-button>
                  <el-radio-button value="RANGE" :disabled="!rangeAvailable">
                    日期区间
                  </el-radio-button>
                </el-radio-group>
                <p v-if="mode === 'SINGLE'">单次请求，结果不代表完整历史。</p>
                <p v-else>{{ capabilities.range.dateLabel }}范围。</p>
                <p v-if="!rangeAvailable && capabilities.range.unavailableReason">
                  {{ capabilities.range.unavailableReason }}
                </p>
              </div>
              <DynamicParameterForm
                v-if="parameters.length"
                :key="formKey"
                ref="parameterForm"
                :parameters="parameters"
                :disabled="locked"
              />
              <p v-else class="parameter-empty">此模式无需填写请求参数。</p>
            </section>
          </template>
        </div>
        <footer class="form-footer">
          <DownloadAction
            :disabled="!canSubmit"
            :submitting="locked"
            @submit="handleSubmit"
          />
          <p class="form-footer__help">接收后可继续提交其他任务</p>
        </footer>
      </WorkbenchPanel>

      <WorkbenchPanel
        class="download-result-panel"
        heading-id="download-result-title"
        title="任务接收"
        :meta="submissionMeta"
      >
        <div class="download-feedback" :aria-busy="locked">
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
            message="正在等待服务确认持久化接收。"
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
              <el-button native-type="button" @click="clearCorruptSubmission">
                清除损坏的本地记录
              </el-button>
            </template>
          </AsyncStatePanel>
          <AsyncStatePanel
            v-else-if="storageError?.kind === 'READ'"
            state="FAILURE"
            title="无法读取本地提交记录"
            :message="storageError.message"
          >
            <template #actions>
              <el-button native-type="button" @click="retryStorage">重新读取</el-button>
            </template>
          </AsyncStatePanel>
          <AsyncStatePanel
            v-else-if="storageError?.kind === 'WRITE'"
            state="FAILURE"
            title="任务未接收"
            :message="storageError.message"
          />
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
              <el-button native-type="button" @click="retryStorage">重新清除记录</el-button>
            </template>
          </AsyncStatePanel>
          <AsyncStatePanel
            v-else-if="submissionState === 'UNCERTAIN'"
            state="FAILURE"
            title="提交结果尚未确认"
            :message="safeErrorMessage(submissionError) || '请使用原提交标识找回任务。'"
            :request-id="submissionError?.requestId ?? ''"
          >
            <div v-if="pendingSubmission" class="pending-submission">
              <strong>{{ pendingSubmission.pluginId }} / {{ pendingSubmission.apiName }}</strong>
              <span>{{ pendingSubmission.mode === 'RANGE' ? '日期区间' : '单次请求' }}</span>
              <code>{{ pendingSubmission.submissionId }}</code>
              <code v-for="[name, value] in pendingParameters" :key="name">
                {{ name }}={{ value }}
              </code>
            </div>
            <p v-if="recoveryUnavailable">状态暂时无法更新</p>
            <p v-if="storageError?.kind === 'REMOVE'">{{ storageError.message }}</p>
            <template #actions>
              <el-button native-type="button" :disabled="locked" @click="handleRecovery">
                重新查找
              </el-button>
              <el-button
                v-if="pendingSubmission"
                native-type="button"
                :disabled="locked"
                @click="replaySubmission"
              >
                使用原参数重新确认
              </el-button>
              <el-button
                v-if="storageError?.kind === 'REMOVE'"
                native-type="button"
                @click="retryStorage"
              >
                重新清除记录
              </el-button>
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
            <p v-if="storageError?.kind === 'REMOVE'">{{ storageError.message }}</p>
            <template v-if="storageError?.kind === 'REMOVE'" #actions>
              <el-button native-type="button" @click="retryStorage">重新清除记录</el-button>
            </template>
          </AsyncStatePanel>
          <AsyncStatePanel
            v-else
            state="INITIAL"
            title="等待提交任务"
            message="选择模式并填写参数后提交。"
          />
        </div>
      </WorkbenchPanel>
    </div>

    <DownloadTaskList
      class="download-task-list-section"
      :page="taskList.page.value"
      :page-size="taskList.pageSize.value"
      :result="taskList.result.value"
      :loading="taskList.loading.value"
      :error="taskList.error.value"
      :last-updated-at="taskList.lastUpdatedAt.value"
      @refresh="taskList.refresh"
      @update:page="taskList.changePage"
      @update:page-size="taskList.changePageSize"
    />
  </section>
</template>

<style scoped>
.download-mode {
  display: grid;
  gap: 10px;
  margin-bottom: 20px;
}

.download-mode > span {
  font-size: 13px;
  font-weight: 600;
}

.download-mode p,
.task-identity,
.pending-submission,
.local-record-note,
.submission-fields {
  margin: 0;
  color: var(--tensor-muted);
  font-size: 12px;
  line-height: 1.8;
}

.task-identity,
.pending-submission {
  display: grid;
  gap: 6px;
  margin-top: 16px;
}

.task-identity code,
.pending-submission code {
  color: var(--tensor-text);
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.task-identity a {
  width: fit-content;
}

.submission-fields {
  padding-left: 20px;
}

.download-task-list-section {
  margin-top: 24px;
}
</style>
