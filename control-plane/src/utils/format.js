import { formatDate, toApiDate } from './date.js'

const DEFAULT_TIME_ZONE = 'Asia/Shanghai'
const INSTANT_PATTERN = /^(\d{4}-\d{2}-\d{2})T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?(?:Z|[+-](?:[01]\d|2[0-3]):[0-5]\d)$/

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

function parseInstant(value) {
  if (typeof value !== 'string') return null

  const match = INSTANT_PATTERN.exec(value)
  if (!match || toApiDate(match[1]) === null) return null

  const instant = new Date(value)
  return Number.isNaN(instant.getTime()) ? null : instant
}

export function formatIngestedAt(value, timeZone = DEFAULT_TIME_ZONE) {
  const instant = parseInstant(value)
  if (instant === null) return value

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
