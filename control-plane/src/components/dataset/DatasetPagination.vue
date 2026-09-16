<script setup>
const PAGE_SIZES = [20, 50, 100]

const props = defineProps({
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 50 },
  totalElements: { type: Number, default: 0 },
  totalPages: { type: Number, default: 0 },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:page', 'update:pageSize'])

function updatePage(page) {
  if (!props.disabled && page !== props.page) emit('update:page', page)
}

function updatePageSize(pageSize) {
  if (!props.disabled) emit('update:pageSize', pageSize)
}
</script>

<template>
  <nav class="dataset-pagination" aria-label="数据集分页" :aria-disabled="disabled">
    <span role="status" aria-live="polite" aria-atomic="true">
      共 {{ totalElements }} 条，第 {{ page }} / {{ totalPages }} 页
    </span>
    <el-pagination
      :current-page="page"
      :page-size="pageSize"
      :page-count="totalPages"
      :page-sizes="PAGE_SIZES"
      :pager-count="5"
      layout="sizes, prev, pager, next"
      prev-text="上一页"
      next-text="下一页"
      :disabled="disabled"
      :hide-on-single-page="false"
      @update:current-page="updatePage"
      @update:page-size="updatePageSize"
    />
  </nav>
</template>

<style scoped>
.dataset-pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 2rem;
  min-width: 0;
  padding: 1.7rem 0;
  color: var(--tensor-muted);
  font-size: 1.2rem;
  font-variant-numeric: tabular-nums;
}

.dataset-pagination :deep(.el-pagination) {
  display: flex;
  flex-wrap: wrap;
  min-width: 0;
  gap: 0.8rem;
  --el-pagination-font-size: 1.2rem;
  --el-pagination-bg-color: transparent;
  --el-pagination-button-disabled-bg-color: transparent;
  --el-pagination-hover-color: var(--tensor-accent);
}

.dataset-pagination :deep(.el-select__wrapper) { min-height: 3.2rem; font-size: 1.2rem; border-radius: 0.6rem; }
.dataset-pagination :deep(.el-pagination__sizes) { margin: 0; }
.dataset-pagination :deep(.el-pager) { gap: 0.4rem; }
.dataset-pagination :deep(.el-pager li) { min-width: 3.2rem; width: auto; padding: 0 0.6rem; border-radius: 0.6rem; }
.dataset-pagination :deep(.el-pager li.is-active) { color: var(--tensor-accent); background: var(--tensor-accent-bg); }
.dataset-pagination :deep(button) { border-radius: 0.6rem; }
.dataset-pagination :deep(button:not(:disabled):hover),
.dataset-pagination :deep(.el-pager li:not(.is-disabled):hover) { background: var(--tensor-subtle); }
</style>
