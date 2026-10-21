import { beforeEach, describe, expect, it, vi } from 'vitest'
import { requestJson } from '../../src/api/client.js'
import { hrm, employeeStatuses, fullName, uuidPattern } from '../../src/hrm/api.js'

const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('../../src/auth/session.js', () => ({ auth: { request } }))

beforeEach(() => {
  request.mockReset()
})

describe('authenticated JSON client', () => {
  it('encodes filters, retains zero and false, and skips empty optional values', async () => {
    await requestJson('/api/v1/employees?size=20&page=9', {
      query: {
        page: 0,
        active: false,
        name: 'An & Bình',
        empty: '',
        absent: null,
        unset: undefined,
      },
    })
    const [path, options] = request.mock.calls[0]
    const url = new URL(path, 'https://example.test')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      size: '20',
      page: '0',
      active: 'false',
      name: 'An & Bình',
    })
    expect(options).toEqual({})
  })

  it('serializes JSON once and forwards cancellation, headers and response options', async () => {
    const signal = new AbortController().signal
    const body = { name: 'Nhân sự', description: null }
    const headers = { 'If-Match': '"3"' }
    const result = new Blob(['export'])
    request.mockResolvedValue(result)
    await expect(
      requestJson('/api/v1/export', {
        method: 'POST',
        body,
        headers,
        signal,
        responseType: 'blob',
      }),
    ).resolves.toBe(result)
    expect(request).toHaveBeenCalledWith('/api/v1/export', {
      method: 'POST',
      body: JSON.stringify(body),
      headers,
      signal,
      responseType: 'blob',
    })
    expect(body).toEqual({ name: 'Nhân sự', description: null })
  })

  it('propagates errors without retrying a failed write', async () => {
    const error = Object.assign(new Error('Service unavailable'), { status: 503 })
    request.mockRejectedValue(error)
    await expect(hrm.provision('employee', 'an@example.test', 'retry-key')).rejects.toBe(error)
    expect(request).toHaveBeenCalledTimes(1)
  })
})

describe('HRM API contract', () => {
  it.each(['employees', 'departments', 'positions'])(
    'preserves pagination and create/update requests for %s',
    async (resource) => {
      const body = { name: 'Nhân sự' }
      const result = { id: 'saved', ...body }
      request.mockResolvedValue(result)
      await hrm.list(resource)
      expect(request).toHaveBeenLastCalledWith(`/api/v1/${resource}?page=0&size=20`, {})
      await hrm.list(resource, 2, 100)
      expect(request).toHaveBeenLastCalledWith(`/api/v1/${resource}?page=2&size=100`, {})
      await expect(hrm.save(resource, '', body)).resolves.toBe(result)
      expect(request).toHaveBeenLastCalledWith(`/api/v1/${resource}`, {
        method: 'POST',
        body: JSON.stringify(body),
      })
      await hrm.save(resource, 'id/with?reserved#chars', body)
      expect(request).toHaveBeenLastCalledWith(`/api/v1/${resource}/id%2Fwith%3Freserved%23chars`, {
        method: 'PUT',
        body: JSON.stringify(body),
      })
      await hrm.get(resource, 'id/with?reserved#chars')
      expect(request).toHaveBeenLastCalledWith(
        `/api/v1/${resource}/id%2Fwith%3Freserved%23chars`,
        {},
      )
    },
  )

  it('encodes employee IDs in status and provisioning URLs and retains the idempotency key', async () => {
    await hrm.status('id/1', 'ACTIVE')
    expect(request).toHaveBeenLastCalledWith('/api/v1/employees/id%2F1/status', {
      method: 'PATCH',
      body: '{"status":"ACTIVE"}',
    })
    await hrm.provision('id/1', 'an@example.test', 'same-key-on-retry')
    expect(request).toHaveBeenLastCalledWith('/api/v1/employees/id%2F1/account-requests', {
      method: 'POST',
      headers: { 'Idempotency-Key': 'same-key-on-retry' },
      body: '{"email":"an@example.test"}',
    })
    await hrm.request('id/1', 'request/2')
    expect(request).toHaveBeenLastCalledWith(
      '/api/v1/employees/id%2F1/account-requests/request%2F2',
      {},
    )
  })

  it('uses the Auth service for invitations and does not add a body to resend', async () => {
    await hrm.activationInvitation('id/1')
    expect(request).toHaveBeenLastCalledWith('/api/v1/auth/activation-invitations/id%2F1', {})
    request.mockResolvedValue(null)
    await expect(hrm.resendActivationInvitation('id/1')).resolves.toBeNull()
    expect(request).toHaveBeenLastCalledWith('/api/v1/auth/activation-invitations/id%2F1', {
      method: 'POST',
    })
  })

  it.each(['unknown', '../auth', 'toString', '__proto__'])(
    'rejects unregistered resource %s before making a request',
    (resource) => {
      expect(() => hrm.list(resource)).toThrow('Unknown HRM resource')
      expect(() => hrm.get(resource, 'id')).toThrow('Unknown HRM resource')
      expect(() => hrm.save(resource, '', {})).toThrow('Unknown HRM resource')
      expect(request).not.toHaveBeenCalled()
    },
  )

  it('preserves UI helper imports', () => {
    expect(employeeStatuses.ACTIVE).toBe('Đang làm việc')
    expect(fullName({ lastName: 'Nguyễn Văn', firstName: 'An' })).toBe('Nguyễn Văn An')
    expect(uuidPattern.test('550e8400-e29b-41d4-a716-446655440000')).toBe(true)
  })
})
