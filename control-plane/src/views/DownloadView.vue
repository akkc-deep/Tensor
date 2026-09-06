<script setup>
import { computed, onMounted, ref } from 'vue'

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
import { useDownloadFlow } from '../composables/useDownloadFlow.js'

const parameterForm = ref(null)
const {
  state,
  sources,
  apis,
  selectedPluginId,
  selectedApiName,
  result,
  error,
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
} = useDownloadFlow()

const apiDisabled = computed(
  () => selectedSource.value?.downloadAvailable !== true || locked.value,
)
const metadataFailure = computed(
  () => state.value === 'FAILURE' && selectedApiName.value === '',
)
const downloadResult = computed(
  () =>
    state.value === 'SUCCESS' ||
    state.value === 'EMPTY' ||
    (state.value === 'FAILURE' && selectedApiName.value !== ''),
)

async function handleSubmit() {
  if (!selectedApi.value) return
  if (selectedApi.value.parameters.length === 0) {
    await submit({})
    return
  }
  if (!parameterForm.value || !(await parameterForm.value.validate())) return
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
              ref="parameterForm"
              :parameters="selectedApi.parameters"
              :disabled="locked"
            />
            <p v-else class="parameter-empty">
              此接口无需填写请求参数。
            </p>
          </section>
        </div>
        <footer class="form-footer">
          <DownloadAction
            :disabled="!canSubmit"
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
            @retry="retry()"
          />
          <AsyncStatePanel
            v-else-if="state === 'SUBMITTING'"
            state="LOADING"
            title="正在下载"
            message="请求已提交，请稍候。"
          />
          <DownloadResult
            v-else-if="downloadResult"
            :state="state"
            :result="result"
            :error="error"
            :can-retry="canRetry"
            @retry="retry"
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
  </section>
</template>
