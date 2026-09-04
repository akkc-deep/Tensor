<script setup>
const props = defineProps({
  disabled: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false },
})

const emit = defineEmits(['submit'])

function submit() {
  if (!props.disabled && !props.submitting) emit('submit')
}
</script>

<template>
  <el-button
    type="primary"
    native-type="button"
    :disabled="disabled || submitting"
    :loading="submitting"
    :aria-busy="submitting"
    @click="submit"
  >
    开始下载
  </el-button>
</template>
