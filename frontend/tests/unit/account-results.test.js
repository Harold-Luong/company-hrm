import { afterEach, beforeEach, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  request: vi.fn(),
  subscribe: vi.fn(),
  auth: { state: { user: { id: 'hr-1' } } },
}))
vi.mock('../../src/auth/session.js', () => ({ auth: mocks.auth }))
vi.mock('../../src/hrm/api.js', () => ({
  hrm: { request: mocks.request },
  uuidPattern: /^[0-9a-f-]{36}$/,
}))
vi.mock('../../src/notifications/stream.js', () => ({ subscribeAccountEvents: mocks.subscribe }))
const employeeId = '550e8400-e29b-41d4-a716-446655440000'
const requestId = '650e8400-e29b-41d4-a716-446655440000'
const pending = {
  requestId,
  employeeId,
  provisioningStatus: 'PENDING',
  accountStatus: 'NOT_CREATED',
}
const success = { ...pending, provisioningStatus: 'SUCCEEDED', accountStatus: 'PENDING_ACTIVATION' }
let stop
let emit
beforeEach(() => {
  vi.resetModules()
  vi.useFakeTimers()
  sessionStorage.clear()
  mocks.auth.state.user = { id: 'hr-1' }
  mocks.request.mockReset()
  mocks.subscribe.mockImplementation((callback) => {
    emit = callback
    return vi.fn()
  })
})
afterEach(() => {
  stop?.()
  vi.useRealTimers()
})

it('waits for Employee to consume the result, then notifies once across SSE replay and polling', async () => {
  const module = await import('../../src/notifications/accountResults.js')
  mocks.request.mockResolvedValueOnce(pending).mockResolvedValue(success)
  stop = module.startAccountNotifications()
  emit([{ requestId, employeeId, provisioningStatus: 'SUCCEEDED' }])
  await vi.advanceTimersByTimeAsync(0)
  expect(module.accountToasts.value).toHaveLength(0)
  await vi.advanceTimersByTimeAsync(3000)
  expect(module.accountToasts.value).toHaveLength(1)
  emit([{ requestId, employeeId }])
  module.trackAccountRequest(employeeId, success)
  expect(module.accountToasts.value).toHaveLength(1)
  expect(mocks.request).toHaveBeenCalledTimes(2)
  module.dismissAccountToast(requestId)
  stop()
  stop = module.startAccountNotifications()
  emit([{ requestId, employeeId }])
  expect(module.accountToasts.value).toHaveLength(0)
})

it('keeps polling tracked requests without SSE and recovers from temporary errors', async () => {
  const module = await import('../../src/notifications/accountResults.js')
  mocks.request.mockRejectedValueOnce(new Error('offline')).mockResolvedValue(success)
  module.trackAccountRequest(employeeId, pending)
  stop = module.startAccountNotifications()
  await vi.advanceTimersByTimeAsync(6000)
  expect(module.accountToasts.value).toHaveLength(1)
})

it('restores pending requests after reload and does not toast completed requests again', async () => {
  let module = await import('../../src/notifications/accountResults.js')
  module.trackAccountRequest(employeeId, pending)
  vi.resetModules()
  module = await import('../../src/notifications/accountResults.js')
  mocks.request.mockResolvedValue(success)
  stop = module.startAccountNotifications()
  await vi.advanceTimersByTimeAsync(0)
  expect(module.accountToasts.value).toHaveLength(1)
  stop()
  vi.resetModules()
  module = await import('../../src/notifications/accountResults.js')
  stop = module.startAccountNotifications()
  emit([{ employeeId, requestId }])
  await vi.advanceTimersByTimeAsync(3000)
  expect(module.accountToasts.value).toHaveLength(0)
  expect(mocks.request).toHaveBeenCalledTimes(1)
})

it('does not show results from an in-flight request after logout or another user starts a session', async () => {
  const module = await import('../../src/notifications/accountResults.js')
  let resolve
  mocks.request.mockImplementation(
    () =>
      new Promise((done) => {
        resolve = done
      }),
  )
  module.trackAccountRequest(employeeId, pending)
  stop = module.startAccountNotifications()
  stop()
  mocks.auth.state.user = { id: 'hr-2' }
  stop = module.startAccountNotifications()
  resolve(success)
  await vi.advanceTimersByTimeAsync(0)
  expect(module.accountToasts.value).toHaveLength(0)
  expect(mocks.request).toHaveBeenCalledTimes(1)
})
