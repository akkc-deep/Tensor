<script setup>
import { computed } from 'vue'

import { formatCell } from '../../utils/format.js'

const props = defineProps({
  columns: { type: Array, required: true },
  items: { type: Array, required: true },
  loading: { type: Boolean, default: false },
})

const sourceColumns = [
  { name: 'source_plugin', label: 'source_plugin', logicalType: 'STRING' },
  { name: 'source_api', label: 'source_api', logicalType: 'STRING' },
  { name: 'ingested_at', label: 'ingested_at', logicalType: 'STRING' },
]

const displayColumns = computed(() => [...props.columns, ...sourceColumns])
const fixedColumn = computed(() => (
  props.columns.some(({ name }) => name === 'ts_code') ? 'ts_code' : props.columns[0]?.name
))

function minWidth(column) {
  if (column.name === 'ingested_at') return 180
  return column.longText === true ? 240 : 140
}

function stickyStyle(column, zIndex) {
  if (column.property !== fixedColumn.value) return undefined
  return { position: 'sticky', left: '0', zIndex, background: 'var(--el-bg-color, #fff)' }
}

function cellStyle({ column }) {
  return stickyStyle(column, 1)
}

function headerCellStyle({ column }) {
  return stickyStyle(column, 2)
}
</script>

<template>
  <div
    v-loading="loading"
    class="dataset-table"
    :aria-busy="loading"
    style="max-width: 100%; overflow-x: auto"
  >
    <el-table
      :data="items"
      :cell-style="cellStyle"
      :header-cell-style="headerCellStyle"
    >
      <el-table-column
        v-for="column in displayColumns"
        :key="column.name"
        :prop="column.name"
        :label="column.label"
        :min-width="minWidth(column)"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ formatCell(row[column.name], column) }}
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
