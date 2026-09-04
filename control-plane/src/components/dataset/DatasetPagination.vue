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
  if (!props.disabled) emit('update:page', page)
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
  gap: 12px;
}
</style>
