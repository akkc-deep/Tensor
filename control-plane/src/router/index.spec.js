import { createMemoryHistory } from 'vue-router'

import { createAppRouter } from './index.js'

describe('app router', () => {
  it('registers the named workspace routes', () => {
    const router = createAppRouter(createMemoryHistory())

    expect(router.resolve({ name: 'downloads' }).path).toBe('/downloads')
    expect(router.resolve({ name: 'datasets' }).path).toBe('/datasets')
    expect(router.resolve({ name: 'integrity' }).path).toBe('/integrity')
    expect(router.resolve({ name: 'integrity-check', params: { checkId: '33333333-3333-4333-8333-333333333333' } }).path).toBe('/integrity/checks/33333333-3333-4333-8333-333333333333')
    expect(router.resolve({ name: 'settings' }).path).toBe('/settings')
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

 it('resolves a persistent task URL before the catch-all and keeps the UUID parameter', async () => {
  const router = createAppRouter(createMemoryHistory())
  const taskId = '22222222-2222-4222-8222-222222222222'
  await router.push(`/downloads/tasks/${taskId}`)
  expect(router.currentRoute.value.name).toBe('download-task')
  expect(router.currentRoute.value.params).toEqual({ taskId })
  expect(router.resolve({ name: 'download-task', params: { taskId } }).path).toBe(`/downloads/tasks/${taskId}`)
})
