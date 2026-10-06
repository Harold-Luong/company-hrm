import { afterEach, describe, expect, it, vi } from 'vitest'
import { requestBody, requestPreview } from '../../src/attendance/requests.js'
import { safeDestination } from '../../src/auth/navigation.js'

const form = (overrides = {}) => ({
  requestType: 'LATE_ARRIVAL',
  workDate: '2030-01-07',
  period: 'MORNING',
  expectedTime: '08:17',
  reason: '  Lịch hẹn cá nhân  ',
  ...overrides,
})
afterEach(() => vi.useRealTimers())
describe('attendance request UI proposal', () => {
  it('uses Vietnam dates and keeps requested minutes separate from rounded attendance', () => {
    vi.useFakeTimers().setSystemTime(new Date('2030-01-06T18:00:00Z'))
    const late = requestBody(form())
    expect(late).toMatchObject({
      workDate: '2030-01-07',
      requestedMinutes: 17,
      reason: 'Lịch hẹn cá nhân',
      shiftVersion: 0,
    })
    expect(late).not.toHaveProperty('roundedLateMinutes')
    expect(
      requestBody(
        form({ requestType: 'EARLY_DEPARTURE', period: 'AFTERNOON', expectedTime: '16:45' }),
      ).requestedMinutes,
    ).toBe(45)
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
      expect(() => requestBody(form(overrides))).toThrow()
  })
  it('isolates preview data between accounts and accepts only the new known route', () => {
    const first = requestPreview('preview-user-1')
    first[0].reason = 'Private example'
    expect(requestPreview('preview-user-1')[0].reason).toBe('Private example')
    expect(requestPreview('preview-user-2')[0].reason).not.toBe('Private example')
    expect(safeDestination('/attendance/requests')).toBe('/attendance/requests')
    expect(safeDestination('/attendance/requests/../reports')).toBe('/')
  })
})
