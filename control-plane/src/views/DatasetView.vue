<script setup>
import { computed, onMounted, ref, shallowRef } from 'vue'
import { Search } from '@element-plus/icons-vue'

defineOptions({ name: 'DatasetView' })

import { listDataSources } from '../api/dataSources.js'
import { getDataset, listDatasets } from '../api/datasets.js'
import AsyncStatePanel from '../components/common/AsyncStatePanel.vue'
import PageHeading from '../components/common/PageHeading.vue'
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
  if (queryLoading.value || !definition.value || !form || !(await form.validate())) return
  if (queryLoading.value || form !== filterForm.value || !definition.value) return
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
      description="找到需要的数据，让研究继续。"
    />

    <section class="data-browser" aria-labelledby="dataset-result-title">
      <form class="query-form" aria-label="数据查询" novalidate @submit.prevent="handleQuery">
        <DataSourceSelect
          :model-value="selectedPluginId"
          :sources="sources"
          :disabled="sources.length === 0"
          @update:model-value="selectSource"
        />
        <DatasetSelect
          :model-value="selectedApiName"
          :datasets="datasets"
          :disabled="!selectedPluginId || datasets.length === 0"
          @update:model-value="selectDataset"
        />
        <template v-if="definition">
          <DynamicFilterForm ref="filterForm" :filters="definition.filters" :disabled="queryLoading" />
          <div class="dataset-view__actions">
            <button class="button primary" type="submit" :disabled="queryLoading">
              <Search aria-hidden="true" />{{ queryLoading ? '查询中…' : '查询' }}
            </button>
            <button class="text-button" type="button" @click="handleReset">重置</button>
          </div>
        </template>
      </form>
      <p v-if="definition && definition.filters.length === 0" class="dataset-filter-empty">此数据集无需填写筛选条件。</p>
      <header class="data-caption">
        <h2 id="dataset-result-title">{{ definition ? definition.displayName : '查询结果' }}<code v-if="definition">{{ selectedApiName }}</code></h2>
        <span>只读<template v-if="result"> · {{ result.totalElements }} 条记录</template></span>
      </header>
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
          v-else-if="sources.length === 0"
          state="EMPTY"
          title="暂无数据源"
          message="当前没有可查询的数据源。"
        />
        <AsyncStatePanel
          v-else-if="selectedPluginId && datasets.length === 0"
          state="EMPTY"
          title="暂无可查询的数据集"
          message="请尝试其他数据源。"
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
    </section>
  </section>
</template>

<style scoped>
.data-browser { min-width: 0; border-top: 0.1rem solid var(--tensor-line); padding-top: 2.5rem; }
.query-form { display: flex; flex-wrap: wrap; gap: 1.7rem; align-items: flex-start; }
.query-form :deep(.field), .query-form > .data-source-select { flex: 1 1 14rem; min-width: 0; }
.query-form > .dataset-select { flex: 1.5 1 23rem; }
.query-form :deep(.data-source-select), .query-form :deep(.catalog-select) { gap: 0.9rem; }
.query-form :deep(.data-source-select__label), .query-form :deep(.catalog-select__label) { font-size: 1.4rem; font-weight: 500; }
.query-form :deep(.el-select__wrapper) { min-height: 4.3rem; border-radius: 0.6rem; padding: 0 1.1rem; background: var(--tensor-surface); }
.query-form :deep(.el-select__placeholder) { color: var(--tensor-text); }
.query-form :deep(.el-select__placeholder.is-transparent) { color: var(--tensor-muted); }
.query-form :deep(.data-source-select__reason) { overflow-wrap: anywhere; }
.dataset-view__actions { display: flex; align-items: center; gap: 1.7rem; align-self: flex-end; min-height: 4.3rem; }
.button.primary { min-height: 4.3rem; gap: 0.7rem; padding: 0 1.7rem; border: 0; border-radius: 0.6rem; background: var(--tensor-accent); color: #fff; font-size: 1.4rem; font-weight: 500; }
.button.primary svg { width: 1.5rem; height: 1.5rem; }
.button.primary:disabled { opacity: .55; cursor: not-allowed; }
.text-button { border: 0; padding: 0.4rem 0; background: transparent; color: var(--tensor-accent); font: inherit; font-size: 1.4rem; cursor: pointer; }
.text-button:hover { text-decoration: underline; text-underline-offset: 0.4rem; }
.text-button:focus-visible { outline: 0.2rem solid var(--tensor-accent); outline-offset: 0.2rem; }
.data-caption { display: flex; align-items: center; justify-content: space-between; padding: 3.1rem 0 1.7rem; gap: 2rem; }
.data-caption h2 { margin: 0; min-width: 0; font-size: 1.6rem; overflow-wrap: anywhere; }
.data-caption code { color: var(--tensor-muted); margin-left: 1.2rem; font-size: 1.2rem; }
.data-caption > span { font-size: 1.2rem; color: var(--tensor-muted); white-space: nowrap; }
.dataset-filter-empty { margin: 1.2rem 0 0; color: var(--tensor-muted); font-size: 1.2rem; }
.dataset-result-content { min-width: 0; }
.dataset-result-content :deep(.async-state-panel) { align-items: center; padding: 4.4rem 2.4rem; text-align: center; }
.dataset-result-content :deep(.async-state-panel__mark) { width: 3rem; height: 3rem; margin-bottom: 1.4rem; border: 0; border-radius: 0; background: transparent; }
.dataset-result-content :deep(.async-state-panel__title) { font-size: 1.4rem; font-weight: 600; letter-spacing: normal; }
.dataset-result-content :deep(.async-state-panel__message) { font-size: 1.4rem; }
.dataset-result-content :deep(.async-state-panel__actions .el-button) { min-height: 4rem; border-radius: 0.6rem; }
</style>
