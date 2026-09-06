<script setup>
import CatalogSelect from '../common/CatalogSelect.vue'

defineProps({
  modelValue: { type: String, default: '' },
  apis: { type: Array, required: true },
  disabled: { type: Boolean, default: false },
})

defineEmits(['update:modelValue'])
</script>

<template>
  <CatalogSelect
    class="api-select"
    id="download-api"
    label="数据接口"
    :items="apis"
    :model-value="modelValue"
    :disabled="disabled"
    empty-text="暂无接口"
    no-match-text="无匹配接口"
    @update:model-value="$emit('update:modelValue', $event)"
  />
</template>
