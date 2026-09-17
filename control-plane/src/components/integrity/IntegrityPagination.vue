<script setup>
import { computed } from 'vue'

const props = defineProps({
  page: { type: Number, required: true }, pageSize: { type: Number, required: true },
  total: { type: BigInt, required: true }, disabled: Boolean,
})
const emit = defineEmits(['update:page', 'update:pageSize'])
const pages = computed(() => props.total === 0n ? 0n :
  (props.total + BigInt(props.pageSize) - 1n) / BigInt(props.pageSize))
const canNext = computed(() => !props.disabled && BigInt(props.page) < pages.value && props.page < 2147483647)
function changeSize(event) {
  emit('update:pageSize', Number(event.target.value))
}
</script>

<template>
  <nav class="pagination" aria-label="分页">
    <label>每页条数
      <select aria-label="每页条数" :value="pageSize" :disabled="disabled" @change="changeSize">
        <option :value="20">20</option><option :value="50">50</option><option :value="100">100</option>
      </select>
    </label>
    <span>共 {{ total.toString() }} 条</span>
    <span>第 {{ total === 0n ? 0 : page }} 页 / 共 {{ pages.toString() }} 页</span>
    <button type="button" aria-label="上一页" :disabled="disabled || page <= 1" @click="emit('update:page', page - 1)">上一页</button>
    <button type="button" aria-label="下一页" :disabled="!canNext" @click="emit('update:page', page + 1)">下一页</button>
  </nav>
</template>

<style scoped>
.pagination{display:flex;align-items:center;justify-content:flex-end;flex-wrap:wrap;gap:10px;margin-top:16px;font-size:12px}.pagination label{display:flex;align-items:center;gap:7px;color:var(--tensor-muted)}select,button{padding:7px 10px;border:1px solid var(--tensor-line);border-radius:4px;background:var(--tensor-raised);color:var(--tensor-text)}:disabled{cursor:not-allowed;opacity:.55}@media(max-width:680px){.pagination{justify-content:flex-start}}
</style>
