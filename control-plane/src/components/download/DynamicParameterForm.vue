<script setup>
import { toRef } from 'vue'

import MetadataField from '../common/MetadataField.vue'
import { useFormValidation } from '../../composables/useFormValidation.js'
import { useParameterForm } from '../../composables/useParameterForm.js'

const props = defineProps({
  parameters: { type: Array, required: true },
  disabled: { type: Boolean, default: false },
})

const {
  values,
  errors,
  firstError,
  setValue,
  validateValues,
  normalizedValues,
  reset,
} = useParameterForm(toRef(props, 'parameters'))
const { setControl, validate } = useFormValidation(validateValues, firstError)

function controlId(name) {
  return `download-parameter-${name}`
}

function updateValue(name, value) {
  if (!props.disabled) setValue(name, value)
}

defineExpose({ validate, normalizedValues, reset })
</script>

<template>
  <div class="dynamic-parameter-form">
    <MetadataField
      v-for="parameter in parameters"
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

@media (max-width: 680px) {
  .dynamic-parameter-form {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
