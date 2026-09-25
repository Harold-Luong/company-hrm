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
const tokens = { accessToken: 'access-1', refreshToken: 'refresh-1' }
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
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})
async function login() {
  const { auth } = await import('../../src/auth/session.js')
  fetchMock.mockResolvedValueOnce(response({ data: tokens })).mockResolvedValueOnce(response(user))
  await auth.login(' admin@company.com ', 'password')
  return auth
}
describe('Auth contract and session lifecycle', () => {
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
    expect(sessionStorage.length).toBe(1)
    expect(sessionStorage.getItem('company-hrm.refresh-token')).toBe('refresh-1')
  })
  it('restores the tab session and stores the rotated refresh token', async () => {
    sessionStorage.setItem('company-hrm.refresh-token', 'old-refresh')
    const { auth } = await import('../../src/auth/session.js')
    fetchMock.mockResolvedValueOnce(response(tokens)).mockResolvedValueOnce(response(user))
    await Promise.all([auth.initialize(), auth.initialize()])
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      refreshToken: 'old-refresh',
    })
    expect(auth.state.user?.email).toBe(user.email)
    expect(sessionStorage.getItem('company-hrm.refresh-token')).toBe('refresh-1')
  })
  it('shares refresh for concurrent 401s and retries with the new access token', async () => {
    const auth = await login()
    let refreshCount = 0
    fetchMock.mockImplementation(async (url, init) => {
      if (String(url).endsWith('/refresh')) {
        refreshCount++
        await new Promise((resolve) => setTimeout(resolve, 10))
        return response({ accessToken: 'access-2', refreshToken: 'refresh-2' })
      }
      if (init?.headers?.Authorization === 'Bearer access-1') return response({}, 401)
      return response(user)
    })
    await Promise.all([auth.loadUser(), auth.loadUser(), auth.loadUser()])
    expect(refreshCount).toBe(1)
    expect(sessionStorage.getItem('company-hrm.refresh-token')).toBe('refresh-2')
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
    expect(sessionStorage.length).toBe(0)
  })
  it('clears a revoked session on startup', async () => {
    sessionStorage.setItem('company-hrm.refresh-token', 'revoked')
    const { auth } = await import('../../src/auth/session.js')
    fetchMock.mockResolvedValueOnce(response({}, 401))
    await auth.initialize()
    expect(auth.authenticated.value).toBe(false)
    expect(auth.state.initialized).toBe(true)
    expect(sessionStorage.length).toBe(0)
  })
  it('preserves a stored session during transient failures so startup can be retried', async () => {
    sessionStorage.setItem('company-hrm.refresh-token', 'existing')
    const { auth } = await import('../../src/auth/session.js')
    fetchMock.mockRejectedValueOnce(new TypeError('offline'))
    await expect(auth.initialize()).rejects.toMatchObject({ status: 0 })
    expect(sessionStorage.getItem('company-hrm.refresh-token')).toBe('existing')
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
    expect(sessionStorage.length).toBe(0)
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
    expect(sessionStorage.length).toBe(0)
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
    finish(response({ accessToken: 'access-2', refreshToken: 'refresh-2' }))
    await Promise.all([pending, logout])
    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/logout'))
    expect(JSON.parse(call[1].body)).toEqual({ refreshToken: 'refresh-2' })
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
      .mockResolvedValueOnce(
        response({ data: { accessToken: 'new-access', refreshToken: 'new-refresh' } }),
      )
      .mockResolvedValueOnce(response(nextUser))
    await auth.login(nextUser.email, 'password')
    finish(response(user))
    await expect(pending).rejects.toMatchObject({ status: 401 })
    expect(auth.state.user?.email).toBe(nextUser.email)
    expect(auth.hasRole(['ADMIN'])).toBe(false)
    expect(auth.hasRole(['HR'])).toBe(true)
    expect(sessionStorage.getItem('company-hrm.refresh-token')).toBe('new-refresh')
  })
})
