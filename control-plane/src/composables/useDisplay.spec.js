import { createDisplayState } from './useDisplay.js'

let display
function resize(width) {
  vi.stubGlobal('innerWidth', width)
  window.dispatchEvent(new Event('resize'))
}
beforeEach(() => { localStorage.clear(); resize(1920) })
afterEach(() => display?.dispose())

it('adapts to CSS viewport width without multiplying the operating-system pixel ratio', () => {
  vi.stubGlobal('devicePixelRatio', 2)
  display = createDisplayState()
  expect(display.scale.value).toBe(1)
  resize(2560)
  expect(display.scale.value).toBe(1.25)
  resize(3840)
  expect(display.scale.value).toBe(2)
  expect(document.documentElement.style.getPropertyValue('--tensor-display-scale')).toBe('2')
})

it('restores a manual choice and retains it while fitting a narrow window', () => {
  localStorage.setItem('tensor-display-scale', '175')
  resize(2560)
  display = createDisplayState()
  expect(display.scale.value).toBe(1.75)
  resize(1024)
  expect(display.scale.value).toBe(1)
  expect(display.requested.value).toBe('175')
  resize(2560)
  expect(display.scale.value).toBe(1.75)
})

it('falls back safely for corrupted storage and rejects arbitrary values', () => {
  localStorage.setItem('tensor-display-scale', '900')
  display = createDisplayState()
  expect(display.requested.value).toBe('auto')
  for (const value of ['0', '900', '125px', null]) expect(display.apply(value)).toBe(false)
  expect(display.scale.value).toBe(1)
})

it('persists manual choices and a return to automatic mode', () => {
  resize(2560)
  display = createDisplayState()
  expect(display.apply('150')).toBe(true)
  expect(display.scale.value).toBe(1.5)
  expect(localStorage.getItem('tensor-display-scale')).toBe('150')
  display.apply('auto')
  expect(localStorage.getItem('tensor-display-scale')).toBe('auto')
  expect(display.scale.value).toBe(1.25)
})

it('keeps settings usable and reports when browser storage is unavailable', () => {
  vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('denied') })
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('denied') })
  resize(2560)
  display = createDisplayState()
  expect(display.storageStatus.value).toBe('preview-only')
  expect(display.apply('150')).toBe(true)
  expect(display.scale.value).toBe(1.5)
  expect(display.storageStatus.value).toBe('preview-only')
})

it('stops changing the document after disposal', () => {
  display = createDisplayState()
  display.dispose()
  resize(3840)
  expect(document.documentElement.style.getPropertyValue('--tensor-display-scale')).toBe('')
})
