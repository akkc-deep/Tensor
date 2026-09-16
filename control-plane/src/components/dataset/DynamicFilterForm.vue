<script setup>
import { computed, ref, toRef } from 'vue'

import { useDatasetFilters } from '../../composables/useDatasetFilters.js'
import { useFormValidation } from '../../composables/useFormValidation.js'
import FieldError from '../common/FieldError.vue'

const props = defineProps({
  filters: { type: Array, required: true },
  disabled: { type: Boolean, default: false },
})

const { values, errors, firstError, setValue, validateValues, criteria, reset: resetFilters } = useDatasetFilters(
  toRef(props, 'filters'),
)
const controls = ref(null)
const { setControl, validate } = useFormValidation(() => {
  // A partially typed native date may have no value and emit no input event.
  for (const control of controls.value.querySelectorAll('input')) {
    setValue(control.name, values[control.name], control.validity.badInput)
  }
  return validateValues()
}, firstError)

const fields = computed(() => props.filters.flatMap((filter) => {
  if (filter.field === 'ts_code' && filter.operator === 'EQ' && filter.controlType === 'TEXT') {
    return [{ key: 'tsCode', type: 'TEXT', label: '证券代码' }]
  }
  if (filter.field === 'trade_date' && filter.operator === 'BETWEEN' && filter.controlType === 'DATE_RANGE') {
    return [
      { key: 'tradeDateFrom', type: 'DATE', label: '交易开始日期' },
      { key: 'tradeDateTo', type: 'DATE', label: '交易结束日期' },
    ]
  }
  if (filter.field === 'ann_date' && filter.operator === 'BETWEEN' && filter.controlType === 'DATE_RANGE') {
    return [
      { key: 'annDateFrom', type: 'DATE', label: '公告开始日期' },
      { key: 'annDateTo', type: 'DATE', label: '公告结束日期' },
    ]
  }
  return []
}))

function controlId(key) {
  return `dataset-filter-${key}`
}

function updateValue(key, control) {
  if (!props.disabled) setValue(key, control.value, control.validity.badInput)
}

function reset() {
  resetFilters()
  for (const control of controls.value.querySelectorAll('input')) control.value = ''
}

defineExpose({ validate, criteria, reset })
</script>

<template>
  <div ref="controls" class="dynamic-filter-form">
    <div v-for="field in fields" :key="field.key" class="field" :data-filter="field.key">
      <label :for="controlId(field.key)">{{ field.label }}</label>
      <input
        :id="controlId(field.key)"
        :ref="(control) => setControl(field.key, control)"
        :name="field.key"
        :type="field.type === 'DATE' ? 'date' : 'text'"
        :value="values[field.key]"
        :placeholder="field.type === 'TEXT' ? '全部证券' : undefined"
        :disabled="disabled"
        :aria-invalid="errors[field.key] ? 'true' : undefined"
        :aria-describedby="errors[field.key] ? `${controlId(field.key)}-error` : undefined"
        autocomplete="off"
        @input="updateValue(field.key, $event.target)"
      />
      <FieldError :id="`${controlId(field.key)}-error`" :message="errors[field.key] ?? ''" />
    </div>
  </div>
</template>

<style scoped>
.dynamic-filter-form { display: contents; }
.field { display: flex; flex-direction: column; gap: 0.9rem; min-width: 0; }
.field label { font-size: 1.4rem; font-weight: 500; }
.field input { width: 100%; height: 4.3rem; min-width: 0; border: 0.1rem solid var(--tensor-line); border-radius: 0.6rem; padding: 0 1.1rem; background: var(--tensor-surface); color: var(--tensor-text); font: inherit; font-size: 1.4rem; caret-color: var(--tensor-accent); }
.field input::placeholder { color: var(--tensor-muted); }
.field input[aria-invalid="true"] { border-color: var(--tensor-error); }
.field input:disabled { opacity: .55; cursor: not-allowed; }
.field :deep(.field-error) { overflow-wrap: anywhere; }
</style>
