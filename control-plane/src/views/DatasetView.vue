<script setup>
import { computed, onMounted, ref, shallowRef } from 'vue'

defineOptions({ name: 'DatasetView' })

import { listDataSources } from '../api/dataSources.js'
import { getDataset, listDatasets } from '../api/datasets.js'
import AsyncStatePanel from '../components/common/AsyncStatePanel.vue'
import PageHeading from '../components/common/PageHeading.vue'
import WorkbenchPanel from '../components/common/WorkbenchPanel.vue'
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
    <PageHeading
      id="datasets-title"
      title="数据查看"
      description="筛选、浏览与核验，找到你需要的市场数据。"
    />

    <div class="dataset-workbench">
      <WorkbenchPanel
        heading-id="dataset-config-title"
        title="查询配置"
        :meta="selectedPluginId"
      >
        <div class="setup-body dataset-setup">
          <div class="workbench-selects">
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
          </div>
          <template v-if="definition">
            <DynamicFilterForm
              ref="filterForm"
              :filters="definition.filters"
              :disabled="queryLoading"
            />
            <p
              v-if="definition.filters.length === 0"
              class="dataset-filter-empty"
            >
              此数据集无需填写筛选条件。
            </p>
            <div class="dataset-view__actions">
              <el-button
                type="primary"
                native-type="button"
                :disabled="queryLoading"
                @click="handleQuery"
              >
                查询
              </el-button>
              <el-button native-type="button" @click="handleReset">
                重置
              </el-button>
            </div>
          </template>
        </div>
      </WorkbenchPanel>

      <WorkbenchPanel
        heading-id="dataset-result-title"
        :title="definition ? definition.displayName : '查询结果'"
        :meta="selectedApiName"
      >
        <div class="dataset-result-content" :aria-busy="queryLoading">
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
            :request-id="metadataError.requestId"
            :retry-label="metadataCanRetry ? '重新加载' : ''"
            @retry="retryMetadata()"
          />
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
            :request-id="queryError.requestId"
            :retry-label="canRetry ? '重新查询' : ''"
            @retry="retry()"
          />
          <AsyncStatePanel
            v-else-if="queryState === 'EMPTY'"
            state="EMPTY"
            title="未找到符合条件的数据"
            message="请修改筛选条件后重新查询。"
          />
          <DatasetTable
            v-else-if="queryState === 'SUCCESS'"
            :columns="definition.columns"
            :items="result.items"
            :loading="queryLoading"
            :plugin-id="selectedPluginId"
            :api-name="selectedApiName"
          />
        </div>
        <DatasetPagination
          v-if="result && ['EMPTY', 'SUCCESS'].includes(queryState)"
          :page="page"
          :page-size="pageSize"
          :total-elements="result.totalElements"
          :total-pages="result.totalPages"
          :disabled="queryLoading"
          @update:page="changePage"
          @update:page-size="changePageSize"
        />
      </WorkbenchPanel>
    </div>
  </section>
</template>

<style scoped>
.dataset-workbench,
.dataset-setup,
.dataset-result-content {
  min-width: 0;
}

.dataset-workbench {
  display: grid;
  gap: 24px;
}

.dataset-setup {
  display: grid;
  gap: 24px;
  padding-bottom: 24px;
}

.dataset-view__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.dataset-filter-empty {
  margin: 0;
  color: var(--tensor-muted);
  font-size: 12px;
  line-height: 1.8;
}

@media (max-width: 1000px) {
  .dataset-workbench {
    gap: 18px;
  }
}

@media (max-width: 680px) {
  .dataset-setup {
    padding: 18px;
  }
}
</style>
