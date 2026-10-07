import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  shiftBody,
  minutesLabel,
  secondsLabel,
  attendanceError,
} from '../../src/attendance/helpers.js'
import { apiRequest, ApiError } from '../../src/auth/api.js'
import { safeDestination } from '../../src/auth/navigation.js'
afterEach(() => vi.unstubAllGlobals())
const form = () => ({
  name: ' Standard ',
  intervals: [
    { period: 'MORNING', start: '08:00', end: '12:00' },
    { period: 'AFTERNOON', start: '13:30', end: '17:30' },
  ],
  checkInFrom: '06:00',
  checkOutUntil: '22:00',
})
describe('attendance UI contract', () => {
  it('sends the fixed schedule without client-supplied policy or hours', () => {
    expect(shiftBody({ ...form(), requiredMinutes: 999, mode: 'FLEXIBLE_DURATION' })).toEqual({
      ...form(),
      name: 'Standard',
      mode: 'FIXED_SHIFT',
      timezone: 'Asia/Ho_Chi_Minh',
    })
  })
  it('allows part-time and rejects overlapping or reversed intervals', () => {
    expect(
      shiftBody({ ...form(), intervals: [{ period: 'AFTERNOON', start: '13:00', end: '15:00' }] })
        .intervals,
    ).toHaveLength(1)
    for (const intervals of [
      [],
      [{ period: 'MORNING', start: '12:00', end: '08:00' }],
      [
        { period: 'MORNING', start: '08:00', end: '14:00' },
        { period: 'AFTERNOON', start: '13:30', end: '17:30' },
      ],
    ])
      expect(() => shiftBody({ ...form(), intervals })).toThrow()
  })
  it('does not display missing work as zero and keeps actual seconds', () => {
    expect(minutesLabel(null)).toBe('Chưa xác định')
    expect(minutesLabel(225)).toBe('3h 45p')
    expect(secondsLabel(13921)).toBe('3h 52p 1s')
    expect(attendanceError(new ApiError(412, 'changed'))).toContain('tải lại')
  })
  it('supports CSV downloads and preserves JSON errors', async () => {
    const blob = new Blob(['csv'], { type: 'text/csv' })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, blob: async () => blob }))
    expect(await apiRequest('/csv', { responseType: 'blob' })).toBe(blob)
    expect(fetch.mock.calls[0][1]).not.toHaveProperty('responseType')
    fetch.mockResolvedValue({
      ok: false,
      status: 403,
      json: async () => ({ detail: 'Forbidden' }),
      headers: new Headers(),
    })
    await expect(apiRequest('/csv', { responseType: 'blob' })).rejects.toMatchObject({
      status: 403,
    })
  })
  it('allows only the known attendance routes after login', () => {
    for (const path of [
      '/attendance',
      '/attendance/shifts',
      '/attendance/schedules',
      '/attendance/reports',
    ])
      expect(safeDestination(path)).toBe(path)
    expect(safeDestination('/attendance/../accounts/new')).toBe('/')
  })
})
