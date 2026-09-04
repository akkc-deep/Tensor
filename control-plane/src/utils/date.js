const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/
const MONTH_PATTERN = /^(\d{4})-(\d{2})$/

function isLeapYear(year) {
  return year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
}

function parseDate(value) {
  if (typeof value !== 'string') return null

  const match = DATE_PATTERN.exec(value)
  if (!match) return null

  const year = +match[1]
  const month = +match[2]
  const day = +match[3]
  const days = [31, isLeapYear(year) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]

  if (year === 0 || month < 1 || month > 12) return null
  if (day < 1 || day > days[month - 1]) return null

  return match
}

export function toApiDate(value) {
  const match = parseDate(value)
  return match ? `${match[1]}${match[2]}${match[3]}` : null
}

export function toApiMonth(value) {
  if (typeof value !== 'string') return null

  const match = MONTH_PATTERN.exec(value)
  if (!match) return null

  const year = +match[1]
  const month = +match[2]
  return year > 0 && month >= 1 && month <= 12 ? `${match[1]}${match[2]}` : null
}

export function formatDate(value) {
  const compact = toApiDate(value)
  return compact
    ? `${compact.slice(0, 4)}-${compact.slice(4, 6)}-${compact.slice(6)}`
    : value
}
