import { afterEach, describe, expect, it, vi } from 'vitest'
import { effectScope, reactive, ref } from 'vue'
import { useAsyncResource } from '../../src/composables/useAsyncResource.js'
import { useIdempotencyKey } from '../../src/composables/useIdempotencyKey.js'

const scopes = []
function inScope(create) {
  const scope = effectScope()
  scopes.push(scope)
  return { scope, value: scope.run(create) }
}
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
afterEach(() => {
  scopes.splice(0).forEach((scope) => scope.stop())
  vi.restoreAllMocks()
})

describe('async resources', () => {
  it('keeps the latest page when older responses finish out of order', async () => {
    const old = deferred(),
      latest = deferred()
    const loader = vi.fn().mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise)
    const { value: resource } = inScope(() => useAsyncResource(loader, { initialValue: () => [] }))
    const firstLoad = resource.load(0)
    const secondLoad = resource.load(1)
    latest.resolve(['page two'])
    await secondLoad
    old.resolve(['page one'])
    await firstLoad
    expect(resource.data.value).toEqual(['page two'])
    expect(resource.loading.value).toBe(false)
    expect(loader.mock.calls).toEqual([[0], [1]])
  })
  it('does not let a stale error or finally clear the latest loading state', async () => {
    const old = deferred(),
      latest = deferred()
    const loader = vi.fn().mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise)
    const { value: resource } = inScope(() => useAsyncResource(loader))
    const firstLoad = resource.load()
    const secondLoad = resource.load()
    old.reject(new Error('stale failure'))
    await firstLoad
    expect(resource.error.value).toBe('')
    expect(resource.loading.value).toBe(true)
    latest.resolve('current')
    await secondLoad
    expect(resource.data.value).toBe('current')
  })
  it('invalidates in-flight work immediately when the owner is removed', async () => {
    const owner = ref('employee-one')
    const pending = deferred()
    const loader = vi.fn(() => pending.promise)
    const { value: resource } = inScope(() =>
      useAsyncResource(loader, {
        source: owner,
        enabled: () => !!owner.value,
      }),
    )
    owner.value = null
    pending.resolve({ private: 'old owner data' })
    await pending.promise
    expect(resource.data.value).toBeNull()
    expect(resource.loading.value).toBe(false)
    expect(loader).toHaveBeenCalledTimes(1)
  })
  it('ignores responses and prevents new loads after the component scope is disposed', async () => {
    const pending = deferred()
    const loader = vi.fn(() => pending.promise)
    const { value: resource, scope } = inScope(() => useAsyncResource(loader))
    const loading = resource.load()
    scope.stop()
    pending.resolve('late response')
    await loading
    await resource.load()
    expect(resource.data.value).toBeNull()
    expect(loader).toHaveBeenCalledTimes(1)
  })
  it('reports validation errors and clears them on a successful retry', async () => {
    const loader = vi
      .fn()
      .mockImplementationOnce(() => {
        throw new Error('Choose a valid month')
      })
      .mockResolvedValue([])
    const { value: resource } = inScope(() =>
      useAsyncResource(loader, {
        errorMessage: (error) => error.message,
      }),
    )
    await resource.load()
    expect(resource.error.value).toBe('Choose a valid month')
    expect(resource.loading.value).toBe(false)
    await resource.load()
    expect(resource.data.value).toEqual([])
    expect(resource.error.value).toBe('')
  })
  it('can retain previous list data during a failed reload', async () => {
    const loader = vi
      .fn()
      .mockResolvedValueOnce(['saved'])
      .mockRejectedValueOnce(new Error('offline'))
    const { value: resource } = inScope(() => useAsyncResource(loader, { resetOnLoad: false }))
    await resource.load()
    await resource.load()
    expect(resource.data.value).toEqual(['saved'])
    expect(resource.error.value).not.toBe('')
  })
})

describe('form idempotency', () => {
  it('reuses a key across retries and rotates immediately when nested payload data changes', () => {
    const uuid = vi
      .spyOn(crypto, 'randomUUID')
      .mockReturnValueOnce('first')
      .mockReturnValueOnce('second')
      .mockReturnValueOnce('third')
    const form = reactive({ reason: 'Same attempt', details: { time: '08:00' } })
    const { value: key } = inScope(() => useIdempotencyKey(form))
    expect(key.get()).toBe('first')
    expect(key.get()).toBe('first')
    form.details.time = '09:00'
    expect(key.get()).toBe('second')
    key.reset()
    expect(key.get()).toBe('third')
    expect(uuid).toHaveBeenCalledTimes(3)
  })
})
