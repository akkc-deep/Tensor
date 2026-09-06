<script setup>
import { computed, toRef } from 'vue'

import { useDatasetFilters } from '../../composables/useDatasetFilters.js'
import { useFormValidation } from '../../composables/useFormValidation.js'
import MetadataField from '../common/MetadataField.vue'

const props = defineProps({
  filters: { type: Array, required: true },
  disabled: { type: Boolean, default: false },
})

const { values, errors, firstError, setValue, validateValues, criteria, reset } = useDatasetFilters(
  toRef(props, 'filters'),
)
const { setControl, validate } = useFormValidation(validateValues, firstError)

const fields = computed(() => props.filters.flatMap((filter) => {
  if (filter.field === 'ts_code' && filter.operator === 'EQ' && filter.controlType === 'TEXT') {
    return [{ key: 'tsCode', type: 'TEXT', label: '证券代码 (ts_code)' }]
  }
  if (filter.field === 'trade_date' && filter.operator === 'BETWEEN' && filter.controlType === 'DATE_RANGE') {
    return [
      { key: 'tradeDateFrom', type: 'DATE', label: '交易日期开始 (trade_date)' },
      { key: 'tradeDateTo', type: 'DATE', label: '交易日期结束 (trade_date)' },
    ]
  }
  if (filter.field === 'ann_date' && filter.operator === 'BETWEEN' && filter.controlType === 'DATE_RANGE') {
    return [
      { key: 'annDateFrom', type: 'DATE', label: '公告日期开始 (ann_date)' },
      { key: 'annDateTo', type: 'DATE', label: '公告日期结束 (ann_date)' },
    ]
  }
  return []
}))

function syncInputAttributes(element, binding) {
  const input = element.querySelector('input')
  if (!input) return
  for (const [name, value] of Object.entries(binding.value)) {
    if (value === undefined) input.removeAttribute(name)
    else input.setAttribute(name, value)
  }
}

const vInputA11y = { mounted: syncInputAttributes, updated: syncInputAttributes }

function controlId(key) {
  return `dataset-filter-${key}`
}

function updateValue(key, value) {
  if (!props.disabled) setValue(key, value)
}

defineExpose({ validate, criteria, reset })
</script>

<template>
  <div class="dynamic-filter-form">
    <MetadataField
      v-for="field in fields"
      :key="field.key"
      :id="controlId(field.key)"
      :ref="(control) => setControl(field.key, control)"
      :label="field.label"
      :type="field.type"
      :model-value="values[field.key]"
      :error="errors[field.key]"
      :disabled="disabled"
      :data-filter="field.key"
      @update:model-value="updateValue(field.key, $event)"
    />
  </div>
</template>

<style scoped>
.dynamic-filter-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  row-gap: 22px;
  column-gap: 18px;
  min-width: 0;
}

@media (max-width: 680px) {
  .dynamic-filter-form {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
