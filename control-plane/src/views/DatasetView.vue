<script setup>
import { computed, onMounted, ref, shallowRef } from 'vue'

import { listDataSources } from '../api/dataSources.js'
import { getDataset, listDatasets } from '../api/datasets.js'
import AsyncStatePanel from '../components/common/AsyncStatePanel.vue'
import DataSourceSelect from '../components/download/DataSourceSelect.vue'
import DatasetPagination from '../components/dataset/DatasetPagination.vue'
import DatasetSelect from '../components/dataset/DatasetSelect.vue'
import DatasetTable from '../components/dataset/DatasetTable.vue'
import DynamicFilterForm from '../components/dataset/DynamicFilterForm.vue'
import { useDatasetQuery } from '../composables/useDatasetQuery.js'

const sources = shallowRef([])
const datasets = shallowRef([])
const selectedPluginId = ref('')
const selectedApiName = ref('')
const definition = shallowRef(null)
const metadataLoading = ref(false)
const metadataOperation = ref(null)
const metadataError = shallowRef(null)
const filterForm = ref(null)
let metadataGeneration = 0
let failedMetadata = null

const {
  state: queryState,
  result,
  error: queryError,
  page,
  pageSize,
  loading: queryLoading,
  canRetry,
  query,
  changePage,
  changePageSize,
  retry,
  reset: resetQuery,
} = useDatasetQuery()

const metadataTitle = computed(() => ({
  SOURCES: '正在加载数据源',
  DATASETS: '正在加载数据集',
  DEFINITION: '正在加载数据集定义',
})[metadataOperation.value])

const metadataCanRetry = computed(() => {
  if (!failedMetadata || metadataError.value?.retryable !== true) return false
  if (failedMetadata.type === 'SOURCES') return true
  if (failedMetadata.pluginId !== selectedPluginId.value) return false
  return failedMetadata.type === 'DATASETS' ||
    failedMetadata.apiName === selectedApiName.value
})

function invalidateMetadata() {
  metadataGeneration += 1
  metadataLoading.value = false
  metadataOperation.value = null
  metadataError.value = null
  failedMetadata = null
}

function beginMetadata(operation) {
  const generation = ++metadataGeneration
  metadataLoading.value = true
  metadataOperation.value = operation
  metadataError.value = null
  failedMetadata = null
  return generation
}

function finishMetadata(generation) {
  if (generation !== metadataGeneration) return false
  metadataLoading.value = false
  metadataOperation.value = null
  return true
}

function failMetadata(generation, failure, failed) {
  if (!finishMetadata(generation)) return false
  metadataError.value = failure
  failedMetadata = failed
  return false
}

async function loadSources() {
  selectedPluginId.value = ''
  selectedApiName.value = ''
  sources.value = []
  datasets.value = []
  definition.value = null
  resetQuery()
  const generation = beginMetadata('SOURCES')

  try {
    const response = await listDataSources()
    if (!finishMetadata(generation)) return false
    sources.value = response
    return true
  } catch (failure) {
    return failMetadata(generation, failure, { type: 'SOURCES' })
  }
}

async function loadDatasetList(pluginId) {
  const generation = beginMetadata('DATASETS')
  try {
    const response = await listDatasets(pluginId)
    if (!finishMetadata(generation)) return false
    datasets.value = response
    return true
  } catch (failure) {
    return failMetadata(generation, failure, { type: 'DATASETS', pluginId })
  }
}

async function loadDefinition(pluginId, apiName) {
  const generation = beginMetadata('DEFINITION')
  try {
    const response = await getDataset(pluginId, apiName)
    if (!finishMetadata(generation)) return false
    definition.value = response
    return true
  } catch (failure) {
    return failMetadata(generation, failure, {
      type: 'DEFINITION',
      pluginId,
      apiName,
    })
  }
}

function selectSource(pluginId) {
  invalidateMetadata()
  selectedPluginId.value = pluginId
  selectedApiName.value = ''
  datasets.value = []
  definition.value = null
  resetQuery()
  return pluginId ? loadDatasetList(pluginId) : Promise.resolve(false)
}

function selectDataset(apiName) {
  invalidateMetadata()
  selectedApiName.value = apiName
  definition.value = null
  resetQuery()
  return apiName
    ? loadDefinition(selectedPluginId.value, apiName)
    : Promise.resolve(false)
}

function retryMetadata() {
  const failed = failedMetadata ? { ...failedMetadata } : null
  if (!failed) return Promise.resolve(false)
  if (failed.type === 'SOURCES') return loadSources()
  if (failed.pluginId !== selectedPluginId.value) {
    return Promise.resolve(false)
  }
  if (failed.type === 'DATASETS') return loadDatasetList(failed.pluginId)
  if (failed.apiName !== selectedApiName.value) {
    return Promise.resolve(false)
  }
  return loadDefinition(failed.pluginId, failed.apiName)
}

async function handleQuery() {
  const form = filterForm.value
  if (!definition.value || !form || !(await form.validate())) return
  if (form !== filterForm.value || !definition.value) return
  await query(
    selectedPluginId.value,
    selectedApiName.value,
    form.criteria(),
  )
}

function handleReset() {
  filterForm.value?.reset()
  resetQuery()
}

onMounted(loadSources)
</script>

<template>
  <section class="page" aria-labelledby="datasets-title">
    <h1 id="datasets-title">数据查看</h1>

    <DataSourceSelect
      :model-value="selectedPluginId"
      :sources="sources"
      @update:model-value="selectSource"
    />
    <DatasetSelect
      :model-value="selectedApiName"
      :datasets="datasets"
      :disabled="!selectedPluginId || datasets.length === 0"
      @update:model-value="selectDataset"
    />
    <DynamicFilterForm
      v-if="definition"
      ref="filterForm"
      :filters="definition.filters"
      :disabled="queryLoading"
    />
    <div v-if="definition" class="dataset-view__actions">
      <el-button
        type="primary"
        native-type="button"
        :disabled="queryLoading"
        @click="handleQuery"
      >
        查询
      </el-button>
      <el-button native-type="button" @click="handleReset">重置</el-button>
    </div>

    <AsyncStatePanel
      v-if="metadataLoading"
      state="LOADING"
      :title="metadataTitle"
      message="请稍候。"
    />
    <AsyncStatePanel
      v-else-if="metadataError"
      state="FAILURE"
      title="数据查看配置加载失败"
      :message="metadataError.message"
    >
      <template v-if="metadataError.requestId || metadataCanRetry" #actions>
        <p v-if="metadataError.requestId">
          请求 ID：{{ metadataError.requestId }}
        </p>
        <el-button
          v-if="metadataCanRetry"
          native-type="button"
          @click="retryMetadata()"
        >
          重新加载
        </el-button>
      </template>
    </AsyncStatePanel>
    <AsyncStatePanel
      v-else-if="!selectedPluginId"
      state="INITIAL"
      title="请选择数据源"
      message="选择数据源后加载可查询的数据集。"
    />
    <AsyncStatePanel
      v-else-if="!selectedApiName || !definition"
      state="INITIAL"
      title="请选择数据集"
      message="选择数据集后设置筛选条件。"
    />
    <AsyncStatePanel
      v-else-if="queryState === 'UNQUERIED'"
      state="INITIAL"
      title="设置筛选条件后查询"
      message="筛选条件可留空，结果将由服务端分页返回。"
    />
    <AsyncStatePanel
      v-else-if="queryState === 'LOADING'"
      state="LOADING"
      title="正在查询数据"
      message="请稍候。"
    />
    <AsyncStatePanel
      v-else-if="queryState === 'FAILURE'"
      state="FAILURE"
      title="查询失败"
      :message="queryError.message"
    >
      <template v-if="queryError.requestId || canRetry" #actions>
        <p v-if="queryError.requestId">请求 ID：{{ queryError.requestId }}</p>
        <el-button
          v-if="canRetry"
          native-type="button"
          @click="retry()"
        >
          重新查询
        </el-button>
      </template>
    </AsyncStatePanel>
    <template v-else-if="queryState === 'EMPTY'">
      <AsyncStatePanel
        state="EMPTY"
        title="未找到符合条件的数据"
        message="请修改筛选条件后重新查询。"
      />
      <DatasetPagination
        :page="page"
        :page-size="pageSize"
        :total-elements="result.totalElements"
        :total-pages="result.totalPages"
        :disabled="queryLoading"
        @update:page="changePage"
        @update:page-size="changePageSize"
      />
    </template>
    <template v-else-if="queryState === 'SUCCESS'">
      <DatasetTable
        :columns="definition.columns"
        :items="result.items"
        :loading="queryLoading"
      />
      <DatasetPagination
        :page="page"
        :page-size="pageSize"
        :total-elements="result.totalElements"
        :total-pages="result.totalPages"
        :disabled="queryLoading"
        @update:page="changePage"
        @update:page-size="changePageSize"
      />
    </template>
  </section>
</template>

<style scoped>
.dataset-view__actions {
  display: flex;
  gap: 12px;
}
</style>
