<script setup>
import { Download } from '@element-plus/icons-vue'

const props = defineProps({
  disabled: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false },
  recovering: { type: Boolean, default: false },
  mode: { type: String, default: 'SINGLE' },
})

const emit = defineEmits(['submit'])

function submit() {
  if (!props.disabled && !props.submitting) emit('submit')
}
</script>

<template>
  <button
    class="download-action"
    type="button"
    :disabled="disabled || submitting"
    :aria-busy="submitting"
    @click="submit"
  >
    <Download aria-hidden="true" />
    {{ submitting ? recovering ? '正在查找…' : '正在创建…' : mode === 'RANGE' ? '开始批量下载' : '开始下载' }}
  </button>
</template>

<style scoped>
.download-action { display: inline-flex; align-items: center; justify-content: center; gap: 0.7rem; width: 18rem; max-width: 100%; min-height: 4rem; padding: 0 1.7rem; border: 0; border-radius: 0.6rem; background: var(--tensor-accent); color: #fff; font: inherit; font-size: 1.4rem; font-weight: 500; cursor: pointer; }
.download-action svg { width: 1.5rem; height: 1.5rem; }
.download-action:disabled { opacity: .55; cursor: not-allowed; }
.download-action:focus-visible { outline: 0.2rem solid var(--tensor-accent); outline-offset: 0.2rem; }
.download-action:hover:not(:disabled) { background: color-mix(in srgb, var(--tensor-accent) 88%, var(--tensor-text)); }
.download-action:active:not(:disabled) { background: color-mix(in srgb, var(--tensor-accent) 76%, var(--tensor-text)); }
</style>
