<script setup>
import { computed, nextTick, toRef } from 'vue'

import { useDatasetFilters } from '../../composables/useDatasetFilters.js'
import FieldError from '../common/FieldError.vue'

const props = defineProps({
  filters: { type: Array, required: true },
  disabled: { type: Boolean, default: false },
})

const { values, errors, firstError, setValue, validateValues, criteria, reset } = useDatasetFilters(
  toRef(props, 'filters'),
)
const controls = new Map()

const fields = computed(() => props.filters.flatMap((filter) => {
  if (filter.field === 'ts_code' && filter.operator === 'EQ' && filter.controlType === 'TEXT') {
    return [{ key: 'tsCode', kind: 'text', label: '证券代码 (ts_code)' }]
  }
  if (filter.field === 'trade_date' && filter.operator === 'BETWEEN' && filter.controlType === 'DATE_RANGE') {
    return [
      { key: 'tradeDateFrom', kind: 'date', label: '交易日期开始 (trade_date)' },
      { key: 'tradeDateTo', kind: 'date', label: '交易日期结束 (trade_date)' },
    ]
  }
  if (filter.field === 'ann_date' && filter.operator === 'BETWEEN' && filter.controlType === 'DATE_RANGE') {
    return [
      { key: 'annDateFrom', kind: 'date', label: '公告日期开始 (ann_date)' },
      { key: 'annDateTo', kind: 'date', label: '公告日期结束 (ann_date)' },
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

function errorId(key) {
  return `${controlId(key)}-error`
}

function updateValue(key, value) {
  if (!props.disabled) setValue(key, value)
}

function setControl(key, control) {
  if (control) controls.set(key, control)
  else controls.delete(key)
}

async function validate() {
  const valid = validateValues()
  if (!valid) {
    await nextTick()
    controls.get(firstError.value)?.focus()
  }
  return valid
}

defineExpose({ validate, criteria, reset })
</script>

<template>
  <div class="dynamic-filter-form">
    <div
      v-for="field in fields"
      :key="field.key"
      v-input-a11y="{
        id: controlId(field.key),
        'aria-invalid': errors[field.key] ? 'true' : undefined,
        'aria-describedby': errors[field.key] ? errorId(field.key) : undefined,
      }"
      class="filter-field"
      :data-filter="field.key"
    >
      <label class="filter-field__label" :for="controlId(field.key)">
        {{ field.label }}
      </label>
      <el-date-picker
        v-if="field.kind === 'date'"
        :id="controlId(field.key)"
        :ref="(control) => setControl(field.key, control)"
        :model-value="values[field.key]"
        type="date"
        value-format="YYYY-MM-DD"
        :disabled="disabled"
        :aria-invalid="errors[field.key] ? 'true' : undefined"
        :aria-describedby="errors[field.key] ? errorId(field.key) : undefined"
        @update:model-value="updateValue(field.key, $event)"
      />
      <el-input
        v-else
        :id="controlId(field.key)"
        :ref="(control) => setControl(field.key, control)"
        :model-value="values[field.key]"
        :disabled="disabled"
        :aria-invalid="errors[field.key] ? 'true' : undefined"
        :aria-describedby="errors[field.key] ? errorId(field.key) : undefined"
        @update:model-value="updateValue(field.key, $event)"
      />
      <FieldError :id="errorId(field.key)" :message="errors[field.key] ?? ''" />
    </div>
  </div>
</template>

<style scoped>
.dynamic-filter-form,
.filter-field {
  display: grid;
  gap: 8px;
}

.filter-field__label {
  font-weight: 600;
}
</style>
