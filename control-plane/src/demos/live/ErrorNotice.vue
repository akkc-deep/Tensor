<script setup>
defineProps({ error: Object, title: String, retryLabel: String, disabled: Boolean })
defineEmits(['retry'])
</script>

<template>
  <div v-if="error" class="live-notice error-notice" role="alert">
    <strong v-if="title">{{ title }}</strong>
    <p>{{ error.message }}</p>
    <ul v-if="error.fieldErrors?.length"><li v-for="item in error.fieldErrors" :key="item.field">{{ item.field }}：{{ item.message }}</li></ul>
    <small v-if="error.requestId">请求标识：{{ error.requestId }}</small>
    <button v-if="retryLabel" type="button" class="text-button" :disabled="disabled" @click="$emit('retry')">{{ retryLabel }}</button>
  </div>
</template>
