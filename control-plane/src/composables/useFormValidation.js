import { nextTick } from 'vue'

export function useFormValidation(validateValues, firstError) {
  const controls = new Map()

  function setControl(name, control) {
    if (control) controls.set(name, control)
    else controls.delete(name)
  }

  async function validate() {
    const valid = validateValues()
    if (!valid) {
      await nextTick()
      controls.get(firstError.value)?.focus()
    }
    return valid
  }

  return { setControl, validate }
}
