<script setup>
import { computed, ref } from 'vue'

import { decimalSign, formatCell } from '../../utils/format.js'

const props = defineProps({
  columns: { type: Array, required: true },
  items: { type: Array, required: true },
  loading: { type: Boolean, default: false },
  pluginId: { type: String, default: '' },
  apiName: { type: String, default: '' },
})
const tableRegion = ref(null)

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

const MARKET_LABELS = {
  ts_code: '证券代码',
  trade_date: '交易日',
  open: '开盘价',
  high: '最高价',
  low: '最低价',
  close: '收盘价',
  pre_close: '前收盘价',
  change: '涨跌额',
  vol: '成交量',
  amount: '成交额',
  source_plugin: '来源插件',
  source_api: '来源接口',
  ingested_at: '入库时间',
}

function mappedLabel(column) {
  if (props.pluginId !== 'tushare_pro' || !['daily', 'weekly'].includes(props.apiName)) {
    return column.label
  }
  if (column.name === 'pct_chg') {
    return props.apiName === 'daily' ? '涨跌幅（%）' : '涨跌幅（比率）'
  }
  return MARKET_LABELS[column.name] ?? column.label
}

function hasMappedLabel(column) {
  if (props.pluginId !== 'tushare_pro' || !['daily', 'weekly'].includes(props.apiName)) {
    return false
  }
  return MARKET_LABELS[column.name] !== undefined || column.name === 'pct_chg'
}

function isNumeric(column) {
  return column.logicalType === 'LONG' || column.logicalType === 'DECIMAL'
}

function marketDirection(value, column) {
  if (!isNumeric(column) || !['change', 'pct_chg'].includes(column.name)) return 0
  return decimalSign(value)
}

function displayValue(value, column) {
  const formatted = formatCell(value, column)
  return marketDirection(value, column) === 1 && !String(formatted).startsWith('+')
    ? `+${formatted}`
    : formatted
}

function hasTooltip(value, column) {
  return column.longText === true && String(value ?? '').length > 30
}

function valueClasses(value, column) {
  const direction = marketDirection(value, column)
  return {
    'dataset-table__number': isNumeric(column),
    'dataset-table__change--up': direction === 1,
    'dataset-table__change--down': direction === -1,
  }
}

function stickyStyle(column, background) {
  if (column.property !== fixedColumn.value) return undefined
  return {
    position: 'sticky',
    left: '0',
    zIndex: 'calc(var(--el-table-index) + 1)',
    background,
  }
}

function cellStyle({ column }) {
  return stickyStyle(column, 'var(--tensor-surface)')
}

function headerCellStyle({ column }) {
  return stickyStyle(column, 'var(--tensor-raised)')
}

function scrollHorizontally(offset) {
  const scroller = tableRegion.value?.querySelector('.el-scrollbar__wrap')
  if (scroller) scroller.scrollLeft += offset
}
</script>

<template>
  <div
    ref="tableRegion"
    v-loading="loading"
    class="dataset-table"
    :aria-busy="loading"
    role="region"
    tabindex="0"
    aria-label="数据表格，可横向滚动"
    style="max-width: 100%; overflow-x: auto"
    @keydown.left.prevent="scrollHorizontally(-140)"
    @keydown.right.prevent="scrollHorizontally(140)"
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
        :label="mappedLabel(column)"
        :align="isNumeric(column) ? 'right' : undefined"
        :min-width="minWidth(column)"
        :show-overflow-tooltip="column.longText !== true"
      >
        <template #header>
          <span>{{ mappedLabel(column) }}</span>
          <code
            v-if="hasMappedLabel(column)"
            class="dataset-table__field-code"
          >{{ column.name }}</code>
        </template>
        <template #default="{ row }">
          <el-tooltip
            v-if="hasTooltip(row[column.name], column)"
            :content="String(row[column.name])"
            placement="top"
            :show-after="0"
          >
            <span
              class="dataset-table__value"
              :class="valueClasses(row[column.name], column)"
            >{{ displayValue(row[column.name], column) }}</span>
          </el-tooltip>
          <span
            v-else
            class="dataset-table__value"
            :class="valueClasses(row[column.name], column)"
          >{{ displayValue(row[column.name], column) }}</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.dataset-table {
  min-width: 0;
  max-width: 100%;
  overflow-x: auto;
  border-radius: 0 0 14px 14px;
}

.dataset-table:focus-visible {
  outline: 3px solid var(--tensor-interactive-color);
  outline-offset: -3px;
}

.dataset-table__field-code {
  display: block;
  margin-top: 3px;
  color: var(--tensor-muted);
  font-size: 11px;
  font-weight: 400;
  line-height: 1.4;
}

.dataset-table__number {
  font-variant-numeric: tabular-nums;
}

.dataset-table__change--up {
  color: var(--tensor-error);
}

.dataset-table__change--down {
  color: var(--tensor-success);
}

.dataset-table :deep(.el-table__body tr:hover > td.el-table__cell) {
  background: var(--tensor-raised) !important;
}
</style>
