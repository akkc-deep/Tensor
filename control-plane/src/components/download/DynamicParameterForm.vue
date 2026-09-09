<script setup>
import { computed } from 'vue'

import MetadataField from '../common/MetadataField.vue'
import { useFormValidation } from '../../composables/useFormValidation.js'
import { useParameterForm } from '../../composables/useParameterForm.js'
import { coveredMonths, inclusiveDateDays } from '../../utils/date.js'
import { dateLabels, isRangeMode } from '../../utils/downloadPolicy.js'

const props = defineProps({
  parameters: { type: Array, required: true },
  disabled: { type: Boolean, default: false },
  downloadPolicy: { type: Object, default: null },
})

const emit = defineEmits(['change'])
const rangeMode = computed(() => isRangeMode(props.downloadPolicy?.mode))
const renderedParameters = computed(() => {
  if (!rangeMode.value) return props.parameters
  const labels = dateLabels(props.downloadPolicy)
  return props.parameters.map((parameter) => {
    if (parameter.name !== 'start_date' && parameter.name !== 'end_date') return parameter
    const copy = { ...parameter, label: labels?.[parameter.name === 'start_date' ? 0 : 1] }
    delete copy.defaultValue
    return copy
  })
})
const maxRangeDays = computed(() => rangeMode.value ? props.downloadPolicy?.limits?.maxRangeDays : undefined)

const {
  values,
  errors,
  firstError,
  setValue,
  validateValues,
  normalizedValues,
  reset,
} = useParameterForm(renderedParameters, { maxRangeDays })
const { setControl, validate } = useFormValidation(validateValues, firstError)

function controlId(name) {
  return `download-parameter-${name}`
}

function updateValue(name, value) {
  if (!props.disabled) {
    setValue(name, value)
    emit('change')
  }
}

const rangeDays = computed(() => inclusiveDateDays(values.start_date, values.end_date))
const validSummary = computed(() => rangeMode.value && rangeDays.value !== null && rangeDays.value <= maxRangeDays.value)
const months = computed(() => validSummary.value && props.downloadPolicy?.mode === 'MONTH_RANGE' ? coveredMonths(values.start_date, values.end_date) : [])

defineExpose({ validate, normalizedValues, reset })
</script>

<template>
  <div class="dynamic-parameter-form">
    <MetadataField
      v-for="parameter in renderedParameters"
      :key="parameter.name"
      :id="controlId(parameter.name)"
      :ref="(control) => setControl(parameter.name, control)"
      :label="parameter.label"
      :type="parameter.type"
      :model-value="values[parameter.name]"
      :required="parameter.required"
      :description="parameter.description"
      :error="errors[parameter.name]"
      :disabled="disabled"
      :allowed-values="parameter.allowedValues"
      :data-parameter="parameter.name"
      @update:model-value="updateValue(parameter.name, $event)"
    />
    <div v-if="validSummary" class="range-summary">
      <p>所选范围：{{ values.start_date }} 至 {{ values.end_date }}，共 {{ rangeDays }} 个自然日</p>
      <p v-if="months.length">实际覆盖月份：{{ months.join('、') }}，共 {{ months.length }} 个月，按完整月份下载</p>
    </div>
  </div>
</template>

<style scoped>
.dynamic-parameter-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  row-gap: 22px;
  column-gap: 18px;
  min-width: 0;
}

.range-summary {
  grid-column: 1 / -1;
  color: var(--tensor-muted);
  font-size: 13px;
  line-height: 1.6;
}

.range-summary p { margin: 0; }

@media (max-width: 680px) {
  .dynamic-parameter-form {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
