import { afterEach, describe, expect, it, vi } from 'vitest'
import { requestBody } from '../../src/attendance/requests.js'
import { safeDestination } from '../../src/auth/navigation.js'

const shift = {
  date: '2030-01-07',
  shiftId: 'shift-id',
  shiftVersion: 2,
  definition: {
    intervals: [
      { period: 'MORNING', start: '08:00:00', end: '12:00:00' },
      { period: 'AFTERNOON', start: '13:30:00', end: '17:30:00' },
    ],
  },
}
const form = (overrides = {}) => ({
  requestType: 'LATE_ARRIVAL',
  workDate: '2030-01-07',
  period: 'MORNING',
  expectedTime: '08:17',
  reason: '  Lịch hẹn cá nhân  ',
  ...overrides,
})
afterEach(() => vi.useRealTimers())
describe('attendance requests with actual assigned schedule', () => {
  it('uses Vietnam dates and keeps requested minutes separate from rounded attendance', () => {
    vi.useFakeTimers().setSystemTime(new Date('2030-01-06T18:00:00Z'))
    const late = requestBody(form(), shift)
    expect(late).toMatchObject({
      workDate: '2030-01-07',
      requestedMinutes: 17,
      reason: 'Lịch hẹn cá nhân',
      shiftVersion: 2,
    })
    expect(late).not.toHaveProperty('roundedLateMinutes')
    expect(
      requestBody(
        form({ requestType: 'EARLY_DEPARTURE', period: 'AFTERNOON', expectedTime: '16:45' }),
        shift,
      ).requestedMinutes,
    ).toBe(45)
  })
  it('uses next-day times for requests inside an overnight shift', () => {
    vi.useFakeTimers().setSystemTime(new Date('2030-01-07T01:00:00Z'))
    const night = {
      ...shift,
      definition: {
        overnight: true,
        intervals: [{ period: 'AFTERNOON', start: '22:00:00', end: '06:00:00' }],
      },
    }
    expect(
      requestBody(form({ period: 'AFTERNOON', expectedTime: '00:30' }), night).requestedMinutes,
    ).toBe(150)
    expect(
      requestBody(
        form({ requestType: 'EARLY_DEPARTURE', period: 'AFTERNOON', expectedTime: '05:30' }),
        night,
      ).requestedMinutes,
    ).toBe(30)
    expect(() => requestBody(form({ period: 'AFTERNOON', expectedTime: '12:00' }), night)).toThrow()
  })
  it('rejects invalid dates, past dates, break times, complete-session absences and blank reasons', () => {
    vi.useFakeTimers().setSystemTime(new Date('2030-01-07T01:00:00Z'))
    for (const overrides of [
      { workDate: '2030-02-30' },
      { workDate: '2030-01-06' },
      { expectedTime: '08:00' },
      { expectedTime: '12:00' },
      { expectedTime: '12:30' },
      { expectedTime: '25:30' },
      { reason: '   ' },
      { reason: 'x'.repeat(1001) },
      { period: 'FULL_DAY' },
      { requestType: 'UNKNOWN' },
    ])
      expect(() => requestBody(form(overrides), shift)).toThrow()
  })
  it('rejects absent or stale schedule and allows only the known route', () => {
    vi.useFakeTimers().setSystemTime(new Date('2030-01-07T01:00:00Z'))
    expect(() => requestBody(form(), null)).toThrow()
    expect(() => requestBody(form(), { ...shift, date: '2030-01-08' })).toThrow()
    expect(safeDestination('/attendance/requests')).toBe('/attendance/requests')
    expect(safeDestination('/attendance/requests/../reports')).toBe('/')
  })
})
