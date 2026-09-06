export const DEFAULT_ACCENT = '#2857b4'

const DEFAULT_PALETTE = {
  bg: '#edf2f6',
  surface: '#ffffff',
  raised: '#f3f6fa',
  nav: '#f9fbfd',
  line: '#c8d3e0',
  text: '#142a42',
  muted: '#52677d',
  accentBg: '#e8efff',
}

const SURFACE_ROLES = ['bg', 'surface', 'raised', 'nav', 'accentBg']

function rgb(hex) {
  return hex
    .slice(1)
    .match(/../g)
    .map((part) => Number.parseInt(part, 16))
}

function mix(first, second, weight) {
  const left = rgb(first)
  const right = rgb(second)
  return `#${left
    .map((channel, index) =>
      Math.round(channel * (1 - weight) + right[index] * weight)
        .toString(16)
        .padStart(2, '0'),
    )
    .join('')}`
}

function luminance(hex) {
  const channels = rgb(hex).map((channel) => {
    const value = channel / 255
    return value <= 0.04045
      ? value / 12.92
      : ((value + 0.055) / 1.055) ** 2.4
  })
  return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722
}

export function contrastRatio(first, second) {
  const left = luminance(first)
  const right = luminance(second)
  return (Math.max(left, right) + 0.05) / (Math.min(left, right) + 0.05)
}

export function createTheme(value) {
  if (typeof value !== 'string' || !/^#[0-9a-f]{6}$/i.test(value)) return null

  const requested = value.toLowerCase()
  const palette = requested === DEFAULT_ACCENT
    ? { ...DEFAULT_PALETTE }
    : {
        bg: mix(requested, '#ffffff', 0.88),
        surface: mix(requested, '#ffffff', 0.98),
        raised: mix(requested, '#ffffff', 0.92),
        nav: mix(requested, '#ffffff', 0.93),
        line: mix(requested, '#ffffff', 0.7),
        text: mix(requested, '#142a42', 0.9),
        muted: mix(requested, '#52677d', 0.88),
        accentBg: mix(requested, '#ffffff', 0.86),
      }

  let applied = requested
  const surfaces = SURFACE_ROLES.map((role) => palette[role])
  for (let amount = 0; amount <= 100; amount += 1) {
    applied = mix(requested, '#000000', amount / 100)
    if ([...surfaces, '#ffffff'].every((surface) => contrastRatio(applied, surface) >= 5.5)) {
      break
    }
  }

  return {
    requested,
    applied,
    colors: {
      ...palette,
      accent: applied,
      success: '#14785e',
      error: '#b72d47',
    },
  }
}
