export function formatIntegrityCount(value) {
  return value === null ? '无法计算' : value.toString()
}

export function formatIntegrityRate(value) {
  if (value === null) return '无法计算'
  const [whole, fraction] = value.split('.')
  const digits = `${whole}${fraction}`.padStart(7, '0')
  const integer = digits.slice(0, -4).replace(/^0+(?=\d)/, '')
  const decimal = digits.slice(-4).replace(/0+$/, '')
  return `${integer}${decimal ? `.${decimal}` : ''}%`
}

export function formatIntegrityValue(value) {
  return value === null ? 'null' : String(value)
}
