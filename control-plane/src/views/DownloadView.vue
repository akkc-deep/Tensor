<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

defineOptions({ name: 'DownloadView' })

import AsyncStatePanel from '../components/common/AsyncStatePanel.vue'
import PageHeading from '../components/common/PageHeading.vue'
import WorkbenchPanel from '../components/common/WorkbenchPanel.vue'
import ApiDescription from '../components/download/ApiDescription.vue'
import ApiSelect from '../components/download/ApiSelect.vue'
import DataSourceSelect from '../components/download/DataSourceSelect.vue'
import DownloadAction from '../components/download/DownloadAction.vue'
import DownloadResult from '../components/download/DownloadResult.vue'
import DynamicParameterForm from '../components/download/DynamicParameterForm.vue'
import RetryTaskList from '../components/download/RetryTaskList.vue'
import RetryTaskDetail from '../components/download/RetryTaskDetail.vue'
import { useRetryTaskFlow } from '../composables/useRetryTaskFlow.js'
import { useDownloadFlow } from '../composables/useDownloadFlow.js'
import { downloadPolicyError } from '../utils/downloadPolicy.js'

const parameterForm = ref(null)
const router = useRouter()
const route = useRoute()
const {
  state,
  sources,
  apis,
  selectedPluginId,
  selectedApiName,
  result,
  error,
  executionContext,
  selectedSource,
  selectedApi,
  locked,
  canSubmit,
  canRetry,
  load,
  selectSource,
  selectApi,
  submit,
  retry,
  parametersChanged,
} = useDownloadFlow()
const {
  listState, listResult, listError, detailState, detail, detailError,
  selectedTaskId, page, pageSize, executionState, executionResult, executionError,
  executionContext: retryContext, locked: retryLocked, canExecute, needsRefresh, executionMessage,
  loadList, changePage, changePageSize, refreshList, selectTask, refreshDetail, execute,
} = useRetryTaskFlow({ isDownloadLocked: () => locked.value })
const combinedLocked = computed(() => locked.value || retryLocked.value)
const activeTab = ref('download')
const tasksMounted = ref(false)
watch(() => [route.name, route.query.tab, route.query.taskId], () => {
  if (route.name !== 'downloads') return
  activeTab.value = route.query.tab === 'retry-tasks' ? 'retry-tasks' : 'download'
  if (activeTab.value !== 'retry-tasks') return
  if (!tasksMounted.value) { tasksMounted.value = true; loadList() }
  const raw = route.query.taskId
  if (typeof raw === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(raw) && raw !== raw.toLowerCase()) {
    router.replace({ name: 'downloads', query: { tab: 'retry-tasks', taskId: raw.toLowerCase() } })
    return
  }
  selectTask(raw === undefined ? null : raw)
}, { immediate: true })
function switchTab(tab) {
  router.push({ name: 'downloads', query: tab === 'retry-tasks'
    ? { tab, ...(selectedTaskId.value ? { taskId: selectedTaskId.value } : {}) } : {} })
}
function selectTaskRoute(taskId) {
  if (!retryLocked.value) router.push({ name: 'downloads', query: { tab: 'retry-tasks', taskId } })
}
function viewRetryTask(taskId) {
  if (taskId && taskId === executionResult.value?.taskId) selectTaskRoute(taskId)
}
function handleSource(pluginId) { if (!combinedLocked.value) selectSource(pluginId) }
function handleApi(apiName) { if (!combinedLocked.value) selectApi(apiName) }
function handleParameters() { if (!combinedLocked.value) parametersChanged() }
function reloadMetadata() { if (!combinedLocked.value) retry() }
const policyError = computed(() => selectedApi.value ? downloadPolicyError(selectedApi.value) : null)

const apiDisabled = computed(
  () => selectedSource.value?.downloadAvailable !== true || combinedLocked.value,
)
const metadataFailure = computed(
  () => state.value === 'FAILURE' && selectedApiName.value === '',
)
const downloadResult = computed(
  () =>
    ['SUCCESS', 'EMPTY', 'NO_OPEN_DATES', 'PARTIAL', 'FAILED', 'UNCONFIRMED'].includes(state.value) ||
    (state.value === 'FAILURE' && selectedApiName.value !== ''),
)

function viewTask(taskId) {
  if (taskId && taskId === result.value?.taskId) {
    router.push({ name: 'downloads', query: { tab: 'retry-tasks', taskId } })
  }
}

async function handleSubmit() {
  if (combinedLocked.value || !selectedApi.value || policyError.value) return
  if (selectedApi.value.parameters.length === 0) {
    await submit({})
    return
  }
  if (!parameterForm.value || !(await parameterForm.value.validate()) || combinedLocked.value) return
  await submit(parameterForm.value.normalizedValues())
}

onMounted(load)
</script>

<template>
  <section class="page" aria-labelledby="downloads-title">
    <PageHeading
      id="downloads-title"
      title="数据下载"
      description="选择数据接口，把市场数据接入你的研究。"
    />

    <el-tabs :model-value="activeTab" @update:model-value="switchTab">
      <el-tab-pane label="发起下载" name="download">
    <div class="download-grid">
      <WorkbenchPanel
        class="download-config-panel"
        heading-id="download-config-title"
        title="下载配置"
        :meta="selectedPluginId"
      >
        <div class="setup-body">
          <div class="workbench-selects">
            <DataSourceSelect
              :model-value="selectedPluginId"
              :sources="sources"
              :disabled="combinedLocked"
              @update:model-value="handleSource"
            />
            <ApiSelect
              :model-value="selectedApiName"
              :apis="apis"
              :disabled="apiDisabled"
              @update:model-value="handleApi"
            />
          </div>
          <ApiDescription :api="selectedApi" />
          <section
            v-if="selectedApi"
            class="parameter-group"
            aria-labelledby="download-parameters-title"
          >
            <header class="parameter-title">
              <h3 id="download-parameters-title">请求参数</h3>
              <span>按接口定义生成</span>
            </header>
            <DynamicParameterForm
              v-if="selectedApi.parameters.length"
              :key="`${selectedPluginId}:${selectedApiName}`"
              ref="parameterForm"
              :parameters="selectedApi.parameters"
              :download-policy="selectedApi.downloadPolicy"
              :disabled="combinedLocked"
              @change="handleParameters"
            />
            <p v-else class="parameter-empty">
              此接口无需填写请求参数。
            </p>
            <p v-if="policyError" class="parameter-error" role="alert">{{ policyError }}</p>
          </section>
        </div>
        <footer class="form-footer">
          <DownloadAction
            :disabled="combinedLocked || !canSubmit || Boolean(policyError)"
            :submitting="locked"
            @submit="handleSubmit"
          />
          <p class="form-footer__help">结果将在本页显示</p>
        </footer>
      </WorkbenchPanel>

      <WorkbenchPanel
        class="download-result-panel"
        heading-id="download-result-title"
        title="本次下载结果"
        :meta="selectedApiName"
      >
        <div class="download-feedback" :aria-busy="state === 'SUBMITTING'">
          <AsyncStatePanel
            v-if="state === 'METADATA_LOADING'"
            state="LOADING"
            title="正在加载下载配置"
            message="请稍候。"
          />
          <AsyncStatePanel
            v-else-if="metadataFailure"
            state="FAILURE"
            title="下载配置加载失败"
            :message="error.message"
            :request-id="error.requestId"
            :retry-label="canRetry ? '重新加载' : ''"
            @retry="reloadMetadata"
          />
          <AsyncStatePanel
            v-else-if="state === 'SUBMITTING'"
            state="LOADING"
            title="正在下载"
            :message="executionContext?.rangeMode ? '区间下载已开始，不可终止，请等待结果。' : '下载请求已提交，请等待结果。'"
          />
          <DownloadResult
            v-else-if="downloadResult"
            :state="state"
            :result="result"
            :error="error"
            :context="executionContext"
            @view-task="viewTask"
          />
          <AsyncStatePanel
            v-else
            state="INITIAL"
            :title="selectedApi ? '填写参数并开始下载' : '请选择数据接口'"
            :message="
              selectedApi
                ? '提交后将在此显示本次下载结果。'
                : '选择接口后填写参数并开始下载。'
            "
          />
        </div>
      </WorkbenchPanel>
    </div>
      </el-tab-pane>
      <el-tab-pane label="失败任务" name="retry-tasks">
        <div v-if="tasksMounted" class="retry-grid">
          <RetryTaskList :state="listState" :result="listResult" :error="listError" :selected-task-id="selectedTaskId"
            :page="page" :page-size="pageSize" :disabled="retryLocked" @filter="loadList" @page="changePage"
            @page-size="changePageSize" @refresh="refreshList" @select="selectTaskRoute" />
          <div class="retry-detail-column">
            <RetryTaskDetail :state="detailState" :detail="detail" :error="detailError" :disabled="retryLocked"
              :can-execute="canExecute" :needs-refresh="needsRefresh" :execution-message="executionMessage"
              @refresh="refreshDetail" @execute="execute" />
            <WorkbenchPanel v-if="executionState !== 'INITIAL'" heading-id="retry-result-title" title="本轮重试结果" :meta="retryContext?.taskId || ''">
              <AsyncStatePanel v-if="executionState === 'SUBMITTING'" state="LOADING" title="正在重试"
                :message="retryContext?.rangeMode ? '区间下载已开始，不可终止，请等待结果。' : '下载请求已提交，请等待结果。'" />
              <DownloadResult v-else :state="executionState" :result="executionResult" :error="executionError"
                :context="retryContext" @view-task="viewRetryTask" />
            </WorkbenchPanel>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<style scoped>
.retry-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr); gap: 24px; align-items: start; }
.retry-detail-column { display: grid; gap: 24px; min-width: 0; }
@media (max-width: 680px) { .retry-grid { grid-template-columns: minmax(0, 1fr); gap: 18px; } }
</style>
