import { beforeEach, describe, expect, it, vi } from 'vitest'
import { demoRequest, resetDemo, readDemo } from '../../src/demo/api.js'
import { demoPassword, demoStorageKey } from '../../src/demo/config.js'
const request = (path, body, method = 'POST', headers = {}) =>
  demoRequest(`/api/v1/${path}`, {
    method,
    headers,
    ...(body ? { body: JSON.stringify(body) } : {}),
  })
const get = (path) => demoRequest(`/api/v1/${path}`)
const login = (role) =>
  request('auth/login', { email: `${role}@demo.example`, password: demoPassword })
beforeEach(() => {
  localStorage.clear()
  resetDemo()
})
describe('offline demo workflows', () => {
  it('logs in with isolated credentials and never falls back to network', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch')
    await expect(get('employees')).rejects.toMatchObject({ status: 401 })
    await expect(
      request('auth/login', { email: 'admin@demo.example', password: 'wrong' }),
    ).rejects.toMatchObject({ status: 401 })
    await login('admin')
    expect((await get('auth/me')).roles).toEqual(['ADMIN'])
    expect((await get('employees')).totalElements).toBe(24)
    expect((await get('employees?page=1')).content).toHaveLength(4)
    await expect(get('unsupported')).rejects.toMatchObject({ status: 501 })
    expect(fetch).not.toHaveBeenCalled()
    fetch.mockRestore()
  })
  it('persists HR edits and resets only demo data', async () => {
    localStorage.setItem('company-hrm.session', 'real-session')
    await login('hr')
    const created = await request('departments', { code: 'DEMO', name: 'Phòng demo' })
    expect((await get(`departments/${created.id}`)).name).toBe('Phòng demo')
    expect(readDemo().departments).toHaveLength(7)
    resetDemo()
    expect(readDemo().departments).toHaveLength(6)
    expect(localStorage.getItem('company-hrm.session')).toBe('real-session')
  })
  it('submits as employee, approves as HR, updates balance and calendar and rejects stale actions', async () => {
    await login('employee')
    const before = await get('leave/requests/balance?year=2031')
    const row = await request('leave/requests', {
      leaveType: 'ANNUAL',
      startDate: '2031-01-06',
      endDate: '2031-01-06',
      period: 'FULL_DAY',
      reason: 'Demo approval',
    })
    await expect(
      request(`leave/requests/${row.id}/approve`, {}, 'PATCH', { 'If-Match': '"0"' }),
    ).rejects.toMatchObject({ status: 403 })
    await login('hr')
    const approved = await request(
      `leave/requests/${row.id}/approve`,
      { note: 'Đồng ý' },
      'PATCH',
      { 'If-Match': '"0"' },
    )
    expect(approved.status).toBe('APPROVED')
    await expect(
      request(`leave/requests/${row.id}/reject`, {}, 'PATCH', { 'If-Match': '"0"' }),
    ).rejects.toMatchObject({ status: 412 })
    await login('employee')
    expect((await get('leave/requests/balance?year=2031')).usedDays).toBe(before.usedDays + 1)
    expect(
      (await get('leave/requests/calendar?year=2031')).find((r) => r.id === row.id).status,
    ).toBe('APPROVED')
    await request(
      `leave/requests/${row.id}/adjustments`,
      { cancelAll: true, reason: 'Đi làm lại' },
      'POST',
      { 'If-Match': '"1"' },
    )
    const [adjustment] = await get(`leave/requests/${row.id}/adjustments`)
    await login('hr')
    await request(`leave/requests/${row.id}/adjustments/${adjustment.id}/approve`, {}, 'PATCH', {
      'If-Match': '"2"',
    })
    await login('employee')
    expect((await get('leave/requests/balance?year=2031')).usedDays).toBe(before.usedDays)
  })
  it('publishes events and exports real CSV content', async () => {
    await login('admin')
    const event = await request('calendar-events', {
      title: 'Demo workshop',
      allDay: true,
      startDate: '2031-03-10',
      endDate: '2031-03-10',
      type: 'EVENT',
    })
    expect((await get('calendar?year=2031')).events).toHaveLength(0)
    await request(`calendar-events/${event.id}/publish`, {}, 'PATCH', {
      'If-Match': String(event.version),
    })
    expect((await get('calendar?year=2031')).events[0].title).toBe('Demo workshop')
    const csv = await get('attendance/reports/export.csv?from=2031-03-10&until=2031-03-10')
    expect(csv.type).toContain('text/csv')
    expect(csv.size).toBeGreaterThan(100)
  })
  it('applies schedules and protects permissions', async () => {
    await login('hr')
    const [shift] = (await get('attendance/shifts')).content
    const body = {
      shiftId: shift.id,
      from: '2031-01-04',
      until: '2031-01-04',
      scope: 'COMPANY_DEFAULT',
      employeeIds: [],
      weekdays: ['SATURDAY'],
      reason: 'Demo',
    }
    const preview = await request('attendance/schedules/preview', body)
    await request('attendance/schedules/apply', body, 'POST', {
      'If-Match': String(preview.scheduleRevision),
    })
    expect(
      (await get('attendance/schedules/mine?from=2031-01-04&until=2031-01-04'))[0].definition.name,
    ).toBe(shift.definition.name)
    await login('employee')
    await expect(get('auth/accounts')).rejects.toMatchObject({ status: 403 })
    await expect(request('departments', { name: 'Forbidden' })).rejects.toMatchObject({
      status: 403,
    })
    expect(localStorage.getItem(demoStorageKey)).toBeTruthy()
  })
  it('includes events overlapping the selected year using Vietnam dates', async () => {
    await login('admin')
    const bodies = [
      { allDay: true, startDate: '2030-12-31', endDate: '2031-01-02' },
      { allDay: false, startAt: '2030-12-31T18:00:00Z', endAt: '2030-12-31T19:00:00Z' },
      { allDay: false, startAt: '2030-12-31T16:00:00Z', endAt: '2030-12-31T17:00:00Z' },
    ]
    const events = []
    for (const body of bodies) {
      const event = await request('calendar-events', { title: 'New year', type: 'EVENT', ...body })
      await request(`calendar-events/${event.id}/publish`, {}, 'PATCH', {
        'If-Match': String(event.version),
      })
      events.push(event)
    }
    expect((await get('calendar?year=2031')).events.map((event) => event.id)).toEqual([
      events[0].id,
      events[1].id,
    ])
  })
  it('keeps assigned shift hours and version after the shift template changes', async () => {
    await login('hr')
    const [shift] = (await get('attendance/shifts')).content
    const body = {
      shiftId: shift.id,
      shiftVersion: shift.version,
      from: '2031-01-06',
      until: '2031-01-06',
      scope: 'COMPANY_DEFAULT',
      employeeIds: [],
      weekdays: ['MONDAY'],
      reason: 'Demo snapshot',
    }
    const preview = await request('attendance/schedules/preview', body)
    await request('attendance/schedules/apply', body, 'POST', {
      'If-Match': String(preview.scheduleRevision),
    })
    const updated = await request(
      `attendance/shifts/${shift.id}`,
      {
        ...shift.definition,
        intervals: [{ period: 'MORNING', start: '08:00', end: '12:00' }],
      },
      'PUT',
      { 'If-Match': String(shift.version) },
    )
    expect(updated.requiredMinutes).toBe(240)
    const [assigned] = await get('attendance/schedules/mine?from=2031-01-06&until=2031-01-06')
    expect(assigned).toMatchObject({
      shiftVersion: shift.version,
      definition: shift.definition,
      requiredMinutes: shift.requiredMinutes,
    })
  })
})
