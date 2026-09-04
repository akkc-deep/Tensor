import { formatDate } from './date.js'

const DEFAULT_TIME_ZONE = 'Asia/Shanghai'

function formatter(timeZone) {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  })
}

function formatterFor(timeZone) {
  try {
    return formatter(timeZone)
  } catch {
    return formatter(DEFAULT_TIME_ZONE)
  }
}

export function formatIngestedAt(value, timeZone = DEFAULT_TIME_ZONE) {
  if (typeof value !== 'string') return value

  const instant = new Date(value)
  if (Number.isNaN(instant.getTime())) return value

  const parts = Object.fromEntries(
    formatterFor(timeZone)
      .formatToParts(instant)
      .filter(({ type }) => type !== 'literal')
      .map(({ type, value: part }) => [type, part]),
  )

  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`
}

export function formatCell(value, column, timeZone = DEFAULT_TIME_ZONE) {
  if (value === null || value === undefined) return '--'
  if (column?.name === 'ingested_at') {
    return formatIngestedAt(value, timeZone)
  }
  if (column?.logicalType === 'DATE') return formatDate(value)
  return value
}
