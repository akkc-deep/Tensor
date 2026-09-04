<script setup>
import { computed, onMounted, ref } from 'vue'

import AsyncStatePanel from '../components/common/AsyncStatePanel.vue'
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
  if (!parameterForm.value || !(await parameterForm.value.validate())) return
  await submit(parameterForm.value.normalizedValues())
}

onMounted(load)
</script>

<template>
  <section class="page" aria-labelledby="downloads-title">
    <h1 id="downloads-title">数据下载</h1>

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
    <ApiDescription :api="selectedApi" />
    <DynamicParameterForm
      v-if="selectedApi"
      ref="parameterForm"
      :parameters="selectedApi.parameters"
      :disabled="locked"
    />
    <DownloadAction
      :disabled="!canSubmit"
      :submitting="locked"
      @submit="handleSubmit"
    />

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
    >
      <template v-if="error.requestId || canRetry" #actions>
        <p v-if="error.requestId">请求 ID：{{ error.requestId }}</p>
        <el-button v-if="canRetry" native-type="button" @click="retry()">
          重新加载
        </el-button>
      </template>
    </AsyncStatePanel>
    <DownloadResult
      v-else-if="downloadResult"
      :state="state"
      :result="result"
      :error="error"
      :can-retry="canRetry"
      @retry="retry"
    />
    <AsyncStatePanel
      v-else-if="state !== 'SUBMITTING'"
      state="INITIAL"
      :title="selectedApi ? '填写参数并开始下载' : '请选择数据接口'"
      :message="
        selectedApi
          ? '提交后将在此显示本次下载结果。'
          : '选择接口后填写参数并开始下载。'
      "
    />
  </section>
</template>
