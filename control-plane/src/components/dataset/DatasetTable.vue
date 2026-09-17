<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import { decimalSign, formatCell } from '../../utils/format.js'
import { useDisplay } from '../../composables/useDisplay.js'

const props = defineProps({
  columns: { type: Array, required: true },
  items: { type: Array, required: true },
  loading: { type: Boolean, default: false },
  pluginId: { type: String, default: '' },
  apiName: { type: String, default: '' },
})
const tableRegion = ref(null)
const valueTooltips = ref([])
const display = useDisplay()
const displayScale = computed(() => display?.scale.value ?? 1)

const sourceColumns = [
  { name: 'source_plugin', label: '来源插件', logicalType: 'STRING' },
  { name: 'source_api', label: '来源接口', logicalType: 'STRING' },
  { name: 'ingested_at', label: '入库时间', logicalType: 'STRING' },
]

const displayColumns = computed(() => [...props.columns, ...sourceColumns])
const fixedColumn = computed(() => (
  props.columns.some(({ name }) => name === 'ts_code') ? 'ts_code' : props.columns[0]?.name
))

function minWidth(column) {
  return (column.name === 'ingested_at' ? 180 : column.longText === true ? 240 : 140) * displayScale.value
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
  return column.longText === true && String(value ?? '').length > 0
}

function closeTooltips(event) {
  if (event.key === 'Escape') valueTooltips.value.forEach(tooltip => tooltip.onClose())
}

function scrollTooltip(event) {
  const tooltip = document.getElementById(event.currentTarget.getAttribute('aria-describedby'))
  if (tooltip?.getAttribute('aria-hidden') !== 'false') return
  const offsets = { ArrowUp: -40, ArrowDown: 40, PageUp: -tooltip.clientHeight, PageDown: tooltip.clientHeight, Home: -tooltip.scrollHeight, End: tooltip.scrollHeight }
  if (!(event.key in offsets)) return
  event.preventDefault()
  event.stopPropagation()
  tooltip.scrollTop += offsets[event.key]
}

onMounted(() => document.addEventListener('keydown', closeTooltips))
onBeforeUnmount(() => document.removeEventListener('keydown', closeTooltips))

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
  return stickyStyle(column, 'var(--tensor-subtle)')
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
      :max-height="488 * displayScale"
      :scrollbar-tabindex="0"
      :cell-style="cellStyle"
      :header-cell-style="headerCellStyle"
    >
      <el-table-column
        v-for="column in displayColumns"
        :key="column.name"
        :prop="column.name"
        :label="column.label"
        align="center"
        :min-width="minWidth(column)"
        :show-overflow-tooltip="column.longText !== true"
      >
        <template #header>
          <span>{{ column.label }}</span>
          <code
            v-if="column.label !== column.name"
            class="dataset-table__field-code"
          >{{ column.name }}</code>
        </template>
        <template #default="{ row }">
          <el-tooltip
            v-if="hasTooltip(row[column.name], column)"
            ref="valueTooltips"
            :content="String(row[column.name])"
            placement="top"
            :trigger="['hover', 'focus']"
            :show-after="0"
            :popper-style="{ maxWidth: 'min(56rem, calc(100vw - 4.8rem))', maxHeight: '50vh', overflow: 'auto', overflowWrap: 'anywhere' }"
          >
            <span
              class="dataset-table__value dataset-table__long-text"
              :class="valueClasses(row[column.name], column)"
              tabindex="0"
              aria-description="使用上下键或 Page Up、Page Down 滚动全文，Home、End 跳至首尾，Esc 关闭。"
              @keydown="scrollTooltip"
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
  border: 0.1rem solid var(--tensor-line);
  border-radius: 0.7rem;
}

.dataset-table:focus-visible {
  outline: 0.3rem solid var(--tensor-interactive-color);
  outline-offset: -0.3rem;
}

.dataset-table__field-code {
  display: block;
  margin-top: 0.3rem;
  color: var(--tensor-muted);
  font-size: 1.1rem;
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

.dataset-table :deep(.el-table) {
  --el-table-text-color: var(--tensor-text);
  --el-table-header-text-color: var(--tensor-muted);
  --el-table-header-bg-color: var(--tensor-subtle);
  --el-table-row-hover-bg-color: var(--tensor-subtle);
  font-variant-numeric: tabular-nums;
}

.dataset-table :deep(.el-table__inner-wrapper::before) { display: none; }
.dataset-table :deep(.el-table__cell) { padding: 1.4rem 0; }
.dataset-table :deep(.cell) { padding: 0 1.5rem; line-height: 1.6; white-space: nowrap; }
/* Override Element Plus's narrower inline tooltip width to match the header. */
.dataset-table :deep(.cell.el-tooltip) { width: 100% !important; }
.dataset-table :deep(th.el-table__cell) { font-family: 'SFMono-Regular', Consolas, monospace; font-size: 1.2rem; font-weight: 400; }
.dataset-table :deep(td.el-table__cell) { font-size: 1.4rem; }
.dataset-table__long-text { display: block; overflow: hidden; text-overflow: ellipsis; }
.dataset-table__long-text:focus-visible { outline: 0.2rem solid var(--tensor-accent); outline-offset: -0.2rem; }

.dataset-table :deep(.el-table__body tr:hover > td.el-table__cell) {
  background: var(--tensor-subtle) !important;
}
</style>
