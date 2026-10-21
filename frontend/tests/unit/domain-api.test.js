import { beforeEach, describe, expect, it, vi } from 'vitest'
import { attendance } from '../../src/attendance/api.js'
import { leave } from '../../src/leave/api.js'
import { calendarManagement } from '../../src/calendar/api.js'
import { openNotificationStream } from '../../src/notifications/api.js'
import { requestChangedEvent } from '../../src/notifications/changes.js'
import { apiPath } from '../../src/api/config.js'

const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('../../src/auth/session.js', () => ({ auth: { request } }))
beforeEach(() => {
  request.mockReset().mockResolvedValue({ id: 'result' })
})

describe('domain API contracts', () => {
  it('encodes dynamic segments without allowing them to alter routing', () => {
    expect(apiPath('employees', 'a/b?x=1#fragment')).toBe(
      '/api/v1/employees/a%2Fb%3Fx%3D1%23fragment',
    )
  })
  it('excludes computed minutes, keeps the input intact and notifies only on success', async () => {
    const listener = vi.fn()
    window.addEventListener(requestChangedEvent, listener)
    try {
      const body = { workDate: '2026-10-20', requestedMinutes: 30, reason: 'Visit' }
      await attendance.saveRequest('', body, undefined, 'submission-key')
      expect(request).toHaveBeenLastCalledWith('/api/v1/attendance/requests', {
        method: 'POST',
        headers: { 'Idempotency-Key': 'submission-key' },
        body: JSON.stringify({ workDate: '2026-10-20', reason: 'Visit' }),
      })
      expect(body.requestedMinutes).toBe(30)
      expect(listener).toHaveBeenCalledTimes(1)
      request.mockRejectedValueOnce(new Error('offline'))
      await expect(attendance.cancelRequest({ id: 'one', version: 2 })).rejects.toThrow('offline')
      expect(listener).toHaveBeenCalledTimes(1)
    } finally {
      window.removeEventListener(requestChangedEvent, listener)
    }
  })
  it('uses optimistic concurrency for edits and both headers for schedule application', async () => {
    await attendance.saveCorrection('id/1', { reason: 'Fix' }, 7, 'unused')
    expect(request).toHaveBeenLastCalledWith('/api/v1/attendance/corrections/id%2F1', {
      method: 'PUT',
      headers: { 'If-Match': '"7"' },
      body: '{"reason":"Fix"}',
    })
    await attendance.apply({ employeeIds: ['one'] }, 4, 'same-key')
    expect(request).toHaveBeenLastCalledWith('/api/v1/attendance/schedules/apply', {
      method: 'POST',
      headers: { 'If-Match': '"4"', 'Idempotency-Key': 'same-key' },
      body: '{"employeeIds":["one"]}',
    })
  })
  it('retains CSV response settings and encodes report filters', async () => {
    const blob = new Blob(['csv'])
    request.mockResolvedValue(blob)
    expect(await attendance.export({ employeeId: 'a&b', detail: false, page: 0, empty: '' })).toBe(
      blob,
    )
    expect(request).toHaveBeenCalledWith(
      '/api/v1/attendance/reports/export.csv?employeeId=a%26b&detail=false&page=0',
      {
        responseType: 'blob',
        headers: { Accept: 'text/csv' },
      },
    )
  })
  it('routes leave adjustment inbox separately and retains cancellation without a body', async () => {
    await leave.list(true, 'ADJUSTMENT_PENDING', 2, 5)
    expect(request).toHaveBeenLastCalledWith(
      '/api/v1/leave/requests/adjustments/inbox?page=2&size=5',
      {},
    )
    await leave.decide('id/1', 'cancel', 3)
    expect(request).toHaveBeenLastCalledWith('/api/v1/leave/requests/id%2F1/cancel', {
      method: 'PATCH',
      headers: { 'If-Match': '"3"' },
    })
    await leave.decideAdjustment('id/1', 'adjustment/2', 'reject', 4, 'Reason')
    expect(request).toHaveBeenLastCalledWith(
      '/api/v1/leave/requests/id%2F1/adjustments/adjustment%2F2/reject',
      {
        method: 'PATCH',
        headers: { 'If-Match': '"4"' },
        body: '{"note":"Reason"}',
      },
    )
  })
  it('keeps calendar lifecycle methods, versions and JSON payloads', async () => {
    await calendarManagement.publish(0, 0)
    expect(request).toHaveBeenLastCalledWith('/api/v1/calendar-events/0/publish', {
      method: 'PATCH',
      headers: { 'If-Match': '"0"' },
    })
    await calendarManagement.cancel('id/1', 8, 'Rescheduled')
    expect(request).toHaveBeenLastCalledWith('/api/v1/calendar-events/id%2F1/cancel', {
      method: 'PATCH',
      headers: { 'If-Match': '"8"' },
      body: '{"reason":"Rescheduled"}',
    })
  })
  it('keeps stream cancellation and a zero cursor through the common client', async () => {
    const signal = new AbortController().signal
    await openNotificationStream(0, signal)
    expect(request).toHaveBeenLastCalledWith('/api/v1/notifications/stream?accountAfter=0', {
      responseType: 'stream',
      headers: { Accept: 'text/event-stream' },
      signal,
    })
  })
})
