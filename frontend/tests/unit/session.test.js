import { beforeEach, describe, expect, it, vi } from 'vitest'
const user = {
  id: 7,
  email: 'admin@company.com',
  employeeId: '550e8400-e29b-41d4-a716-446655440000',
  active: true,
  roles: ['ADMIN'],
  lastLoginAt: '2026-09-25T01:00:00Z',
  createdAt: '2026-09-01T01:00:00Z',
  updatedAt: '2026-09-25T01:00:00Z',
}
const tokens = { accessToken: 'access-1' }
const storageKey = 'company-hrm.session'
const storedSession = () => JSON.parse(localStorage.getItem(storageKey))
const csrfMock = vi.fn()
const seedSession = (id = 'test-session') =>
  localStorage.setItem(storageKey, JSON.stringify({ id }))
const response = (data, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
const fetchMock = vi.fn()
const input = {
  email: 'new@company.com',
  password: 'test-only',
  employeeId: user.employeeId,
  roles: ['EMPLOYEE'],
}
beforeEach(() => {
  vi.resetModules()
  sessionStorage.clear()
  localStorage.clear()
  Object.defineProperty(navigator, 'locks', {
    configurable: true,
    value: { request: (_name, callback) => Promise.resolve().then(callback) },
  })
  fetchMock.mockReset()
  csrfMock
    .mockReset()
    .mockImplementation(async () =>
      response({ token: 'csrf-token', headerName: 'X-XSRF-TOKEN', sessionPresent: false }),
    )
  vi.stubGlobal('fetch', (url, init) =>
    url.endsWith('/csrf') ? csrfMock(url, init) : fetchMock(url, init),
  )
})
async function login() {
  const { auth } = await import('../../src/auth/session.js')
  fetchMock.mockResolvedValueOnce(response({ data: tokens })).mockResolvedValueOnce(response(user))
  await auth.login(' admin@company.com ', 'password')
  return auth
}
describe('Auth contract and session lifecycle', () => {
  it('removes legacy refresh tokens and restores using cookie credentials only', async () => {
    localStorage.setItem(storageKey, JSON.stringify({ id: 'legacy', refreshToken: 'secret' }))
    const { auth } = await import('../../src/auth/session.js')
    fetchMock.mockResolvedValueOnce(response(tokens)).mockResolvedValueOnce(response(user))
    await auth.initialize()
    expect(storedSession()).toEqual({ id: 'legacy' })
    expect(fetchMock.mock.calls[0][1]).toMatchObject({
      credentials: 'same-origin',
      headers: { 'X-XSRF-TOKEN': 'csrf-token' },
    })
    expect(fetchMock.mock.calls[0][1].body).toBeUndefined()
    expect(JSON.stringify(fetchMock.mock.calls)).not.toContain('secret')
  })
  it('restores an HttpOnly cookie even when local metadata was removed', async () => {
    csrfMock.mockImplementation(async () =>
      response({ token: 'csrf-token', headerName: 'X-XSRF-TOKEN', sessionPresent: true }),
    )
    const { auth } = await import('../../src/auth/session.js')
    fetchMock.mockResolvedValueOnce(response(tokens)).mockResolvedValueOnce(response(user))
    await auth.initialize()
    expect(auth.authenticated.value).toBe(true)
    expect(Object.keys(storedSession())).toEqual(['id'])
  })
  it('does not retry an uncertain refresh response', async () => {
    seedSession('existing')
    const { auth } = await import('../../src/auth/session.js')
    fetchMock.mockRejectedValueOnce(new TypeError('offline'))
    await expect(auth.initialize()).rejects.toMatchObject({ status: 0 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(storedSession()).toEqual({ id: 'existing' })
  })
  it('does not silently restore cookies after an offline logout and reload', async () => {
    const auth = await login()
    fetchMock.mockRejectedValueOnce(new TypeError('offline'))
    await expect(auth.logout()).rejects.toMatchObject({ status: 0 })
    expect(localStorage.getItem('company-hrm.signed-out')).toBe('1')
    vi.resetModules()
    csrfMock.mockClear()
    const { auth: reloaded } = await import('../../src/auth/session.js')
    await reloaded.initialize()
    expect(reloaded.authenticated.value).toBe(false)
    expect(csrfMock).not.toHaveBeenCalled()
  })
  it('never retries a rejected refresh with credentials from a different login', async () => {
    seedSession('old-login')
    const { auth } = await import('../../src/auth/session.js')
    fetchMock.mockImplementationOnce(async () => {
      localStorage.setItem(storageKey, JSON.stringify({ id: 'new-login' }))
      return response({}, 401)
    })
    await auth.initialize()
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(auth.state.user).toBeNull()
    expect(storedSession()).toEqual({ id: expect.any(String) })
  })
  it('clears a replaced login when checking the session', async () => {
    const auth = await login()
    fetchMock.mockResolvedValueOnce(response({}, 401)).mockResolvedValueOnce(response({}, 401))
    await expect(auth.checkSession()).rejects.toMatchObject({ status: 401 })
    expect(auth.authenticated.value).toBe(false)
    expect(auth.state.sessionEnded).toBe(true)
    expect(storedSession()).toBeNull()
  })
  it('keeps the login when Auth is unavailable during a session check', async () => {
    const auth = await login()
    fetchMock.mockResolvedValueOnce(response({}, 503))
    await expect(auth.checkSession()).rejects.toMatchObject({ status: 503 })
    expect(auth.authenticated.value).toBe(true)
    expect(auth.state.sessionEnded).toBe(false)
    expect(storedSession()).toEqual({ id: expect.any(String) })
  })
  it('accepts a current session without rotating refresh tokens', async () => {
    const auth = await login()
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))
    await auth.checkSession()
    expect(fetchMock.mock.calls.at(-1)[0]).toBe('/api/v1/auth/session')
    expect(storedSession()).toEqual({ id: expect.any(String) })
  })
  it('reauthenticates SSE with a Bearer header and preserves caller cancellation', async () => {
    const auth = await login()
    const controller = new AbortController()
    const streamResponse = new Response('event: notifications\ndata: {"revision":1}\n\n', {
      headers: { 'Content-Type': 'text/event-stream' },
    })
    fetchMock
      .mockResolvedValueOnce(response({}, 401))
      .mockResolvedValueOnce(response({ accessToken: 'access-2' }))
      .mockResolvedValueOnce(streamResponse)
    const body = await auth.request('/api/v1/notifications/stream', {
      responseType: 'stream',
      signal: controller.signal,
      headers: { Accept: 'text/event-stream' },
    })
    expect(body).toBe(streamResponse.body)
    const [url, options] = fetchMock.mock.calls.at(-1)
    expect(url).toBe('/api/v1/notifications/stream')
    expect(options.headers).toMatchObject({
      Authorization: 'Bearer access-2',
      Accept: 'text/event-stream',
    })
    expect(options.signal.aborted).toBe(false)
    controller.abort()
    expect(options.signal.aborted).toBe(true)
    await body.cancel()
  })
  it('rejects a successful JSON response when an SSE stream was requested', async () => {
    const auth = await login()
    fetchMock.mockResolvedValueOnce(response({ count: 1 }))
    await expect(
      auth.request('/api/v1/notifications/stream', { responseType: 'stream' }),
    ).rejects.toMatchObject({ status: 502, message: 'Invalid event stream' })
  })
  it('does not rotate without a cross-tab lock on unsupported browsers', async () => {
    seedSession('existing')
    Object.defineProperty(navigator, 'locks', { configurable: true, value: undefined })
    const { auth } = await import('../../src/auth/session.js')
    await expect(auth.initialize()).rejects.toMatchObject({ status: 503 })
    expect(fetchMock).not.toHaveBeenCalled()
    expect(storedSession()).toEqual({ id: expect.any(String) })
  })
  it('discards a late rejection from another login without deleting the new shared session', async () => {
    const auth = await login()
    let finish
    fetchMock.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    const pending = auth.loadUser()
    localStorage.setItem(storageKey, JSON.stringify({ id: 'another-login' }))
    // Do not dispatch storage: a background tab may not have received the event yet.
    finish(response({}, 401))
    await expect(pending).rejects.toMatchObject({ status: 401 })
    expect(auth.state.user).toBeNull()
    expect(storedSession()).toEqual({ id: expect.any(String) })
  })
  it('drops old per-tab tokens instead of restoring them after a shared logout', async () => {
    sessionStorage.setItem('company-hrm.refresh-token', 'legacy-token')
    const { auth } = await import('../../src/auth/session.js')
    await auth.initialize()
    expect(fetchMock).not.toHaveBeenCalled()
    expect(auth.authenticated.value).toBe(false)
    expect(sessionStorage.getItem('company-hrm.refresh-token')).toBeNull()
  })
  it('uses login.data tokens, sends Bearer auth, and gets current roles from /me', async () => {
    const auth = await login()
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      email: 'admin@company.com',
      password: 'password',
    })
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/auth/me')
    expect(fetchMock.mock.calls[1][1].headers).toMatchObject({ Authorization: 'Bearer access-1' })
    expect(auth.hasRole(['ADMIN'])).toBe(true)
    expect(auth.hasRole(['HR'])).toBe(false)
    expect(localStorage.length).toBe(1)
    expect(storedSession()).toEqual({ id: expect.any(String) })
  })
  it('restores the tab session without exposing the rotated refresh token', async () => {
    seedSession('old-refresh')
    const { auth } = await import('../../src/auth/session.js')
    fetchMock.mockResolvedValueOnce(response(tokens)).mockResolvedValueOnce(response(user))
    await Promise.all([auth.initialize(), auth.initialize()])
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[0][1].body).toBeUndefined()
    expect(auth.state.user?.email).toBe(user.email)
    expect(storedSession()).toEqual({ id: expect.any(String) })
  })
  it('shares refresh for concurrent 401s and retries with the new access token', async () => {
    const auth = await login()
    let refreshCount = 0
    fetchMock.mockImplementation(async (url, init) => {
      if (String(url).endsWith('/refresh')) {
        refreshCount++
        await new Promise((resolve) => setTimeout(resolve, 10))
        return response({ accessToken: 'access-2' })
      }
      if (init?.headers?.Authorization === 'Bearer access-1') return response({}, 401)
      return response(user)
    })
    await Promise.all([auth.loadUser(), auth.loadUser(), auth.loadUser()])
    expect(refreshCount).toBe(1)
    expect(storedSession()).toEqual({ id: expect.any(String) })
  })
  it('does not loop when an API still returns 401 after refresh', async () => {
    const auth = await login()
    fetchMock.mockClear()
    fetchMock
      .mockResolvedValueOnce(response({}, 401))
      .mockResolvedValueOnce(response(tokens))
      .mockResolvedValueOnce(response({}, 401))
    await expect(auth.loadUser()).rejects.toMatchObject({ status: 401 })
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(auth.state.user).toBeNull()
    expect(localStorage.getItem(storageKey)).toBeNull()
  })
  it('clears a revoked session on startup', async () => {
    seedSession('revoked')
    const { auth } = await import('../../src/auth/session.js')
    fetchMock.mockResolvedValueOnce(response({}, 401))
    await auth.initialize()
    expect(auth.authenticated.value).toBe(false)
    expect(auth.state.initialized).toBe(true)
    expect(localStorage.getItem(storageKey)).toBeNull()
  })
  it('preserves a stored session during transient failures so startup can be retried', async () => {
    seedSession('existing')
    const { auth } = await import('../../src/auth/session.js')
    fetchMock.mockRejectedValueOnce(new TypeError('offline'))
    await expect(auth.initialize()).rejects.toMatchObject({ status: 0 })
    expect(storedSession()).toEqual({ id: expect.any(String) })
    expect(auth.state.initialized).toBe(false)
    fetchMock.mockResolvedValueOnce(response(tokens)).mockResolvedValueOnce(response(user))
    await auth.initialize()
    expect(auth.authenticated.value).toBe(true)
  })
  it('removes stale roles when /me changes and rejects inactive accounts', async () => {
    const auth = await login()
    fetchMock.mockResolvedValueOnce(response({ ...user, roles: ['EMPLOYEE'] }))
    await auth.loadUser()
    expect(auth.hasRole(['ADMIN', 'HR'])).toBe(false)
    fetchMock.mockResolvedValueOnce(response({ ...user, active: false }))
    await expect(auth.loadUser()).rejects.toMatchObject({ status: 403 })
    expect(auth.authenticated.value).toBe(false)
  })
  it('does not refresh or erase the session for a forbidden register operation', async () => {
    const auth = await login()
    fetchMock.mockClear()
    fetchMock.mockResolvedValueOnce(response({}, 403))
    await expect(auth.register({ ...input, roles: [...input.roles] })).rejects.toMatchObject({
      status: 403,
    })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(auth.authenticated.value).toBe(true)
  })
  it('clears local credentials even if logout cannot reach the server', async () => {
    const auth = await login()
    fetchMock.mockRejectedValueOnce(new TypeError('offline'))
    await expect(auth.logout()).rejects.toMatchObject({ status: 0 })
    expect(auth.authenticated.value).toBe(false)
    expect(localStorage.getItem(storageKey)).toBeNull()
  })
  it('reports a CSRF rejection on logout instead of claiming server revocation', async () => {
    const auth = await login()
    fetchMock.mockResolvedValueOnce(response({}, 403))
    await expect(auth.logout()).rejects.toMatchObject({ status: 403 })
    expect(auth.authenticated.value).toBe(false)
    expect(localStorage.getItem('company-hrm.signed-out')).toBe('1')
  })
  it('does not restore the user from an in-flight response after logout', async () => {
    const auth = await login()
    let finish
    fetchMock.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    const pending = auth.loadUser()
    fetchMock.mockResolvedValueOnce(response({ message: 'Logged out successfully' }))
    await auth.logout()
    finish(response(user))
    await expect(pending).rejects.toMatchObject({ status: 401 })
    expect(auth.authenticated.value).toBe(false)
    expect(localStorage.getItem(storageKey)).toBeNull()
  })
  it('waits for refresh rotation then revokes the latest session on logout', async () => {
    const auth = await login()
    let finish
    fetchMock.mockResolvedValueOnce(response({}, 401))
    fetchMock.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    const pending = auth.loadUser().catch(() => undefined)
    await vi.waitFor(() => expect(finish).toBeTypeOf('function'))
    const logout = auth.logout()
    fetchMock.mockImplementation(async () => response(user))
    finish(response({ accessToken: 'access-2' }))
    await Promise.all([pending, logout])
    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/logout'))
    expect(call[1].body).toBeUndefined()
    expect(call[1].headers['X-XSRF-TOKEN']).toBe('csrf-token')
    expect(auth.authenticated.value).toBe(false)
  })
  it('keeps the current session if logout-all fails, clears it after success', async () => {
    const auth = await login()
    fetchMock.mockResolvedValueOnce(response({}, 503))
    await expect(auth.logout(true)).rejects.toMatchObject({ status: 503 })
    expect(auth.authenticated.value).toBe(true)
    fetchMock.mockResolvedValueOnce(response({ message: 'All sessions logged out successfully' }))
    await auth.logout(true)
    expect(auth.authenticated.value).toBe(false)
  })
  it('ignores an old account response after a different account signs in', async () => {
    const auth = await login()
    let finish
    fetchMock.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    const pending = auth.loadUser()
    const nextUser = { ...user, id: 8, email: 'hr@company.com', roles: ['HR'] }
    fetchMock
      .mockResolvedValueOnce(response({ data: { accessToken: 'new-access' } }))
      .mockResolvedValueOnce(response(nextUser))
    await auth.login(nextUser.email, 'password')
    finish(response(user))
    await expect(pending).rejects.toMatchObject({ status: 401 })
    expect(auth.state.user?.email).toBe(nextUser.email)
    expect(auth.hasRole(['ADMIN'])).toBe(false)
    expect(auth.hasRole(['HR'])).toBe(true)
    expect(storedSession()).toEqual({ id: expect.any(String) })
  })
})

describe('Employee API uses the shared authenticated session', () => {
  it('refreshes once across Auth and Employee and retains the idempotency key on retry', async () => {
    const auth = await login()
    let refreshCount = 0
    const writes = []
    fetchMock.mockImplementation(async (url, init) => {
      if (url.endsWith('/refresh')) {
        refreshCount++
        await new Promise((resolve) => setTimeout(resolve, 10))
        return response({ accessToken: 'access-2' })
      }
      if (url.includes('/account-requests')) writes.push(init)
      if (init.headers.Authorization === 'Bearer access-1') return response({}, 401)
      return response(
        url.endsWith('/me') ? user : { requestId: 'request', provisioningStatus: 'PENDING' },
      )
    })
    await Promise.all([
      auth.loadUser(),
      auth.request('/api/v1/employees/employee/account-requests', {
        method: 'POST',
        headers: { 'Idempotency-Key': 'same-key' },
        body: JSON.stringify({ email: 'employee@company.com' }),
      }),
    ])
    expect(refreshCount).toBe(1)
    expect(writes).toHaveLength(2)
    expect(writes[1].headers).toMatchObject({
      Authorization: 'Bearer access-2',
      'Idempotency-Key': 'same-key',
    })
    expect(writes[1].body).toBe(writes[0].body)
  })
  it('reads Employee problem details and does not retry writes on a network failure', async () => {
    const auth = await login()
    fetchMock.mockResolvedValueOnce(response({ detail: 'Department code already exists' }, 409))
    await expect(auth.request('/api/v1/departments')).rejects.toMatchObject({
      message: 'Department code already exists',
      status: 409,
    })
    fetchMock.mockClear()
    fetchMock.mockRejectedValueOnce(new TypeError('offline'))
    await expect(
      auth.request('/api/v1/employees', { method: 'POST', body: '{}' }),
    ).rejects.toMatchObject({ status: 0 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
