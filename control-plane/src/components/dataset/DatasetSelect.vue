<script setup>
import CatalogSelect from '../common/CatalogSelect.vue'

defineProps({
  modelValue: { type: String, default: '' },
  datasets: { type: Array, required: true },
  disabled: { type: Boolean, default: false },
})

defineEmits(['update:modelValue'])
</script>

<template>
  <CatalogSelect
    class="dataset-select"
    id="dataset-select"
    label="数据集"
    :items="datasets"
    :model-value="modelValue"
    :disabled="disabled"
    empty-text="暂无数据集"
    no-match-text="无匹配数据集"
    @update:model-value="$emit('update:modelValue', $event)"
  />
</template>
