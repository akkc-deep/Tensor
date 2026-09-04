<script setup>
defineProps({
  id: { type: String, required: true },
  message: { type: String, required: true },
})
</script>

<template>
  <p v-if="message" :id="id" class="field-error" role="alert">
    {{ message }}
  </p>
</template>

<style scoped>
.field-error {
  margin: 4px 0 0;
  color: var(--el-color-danger, #f56c6c);
  line-height: 1.5;
}
</style>
