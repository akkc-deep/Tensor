import { createMemoryHistory } from 'vue-router'

import { createAppRouter } from './index.js'

describe('app router', () => {
  it('registers the named business routes', () => {
    const router = createAppRouter(createMemoryHistory())

    expect(router.resolve({ name: 'downloads' }).path).toBe('/downloads')
    expect(router.resolve({ name: 'datasets' }).path).toBe('/datasets')
  })

  it('redirects the root route to downloads', async () => {
    const router = createAppRouter(createMemoryHistory())

    await router.push('/')

    expect(router.currentRoute.value.name).toBe('downloads')
    expect(router.currentRoute.value.fullPath).toBe('/downloads')
  })

  it('keeps an unknown path on the not-found route', async () => {
    const router = createAppRouter(createMemoryHistory())

    await router.push('/missing')

    expect(router.currentRoute.value.name).toBe('not-found')
    expect(router.currentRoute.value.fullPath).toBe('/missing')
  })
})
