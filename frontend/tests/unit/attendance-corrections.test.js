import { describe, expect, it } from 'vitest'
import { correctionBody, localCorrectionTime } from '../../src/attendance/corrections.js'
import { safeDestination } from '../../src/auth/navigation.js'
const form = {
  workDate: '2025-01-07',
  proposedCheckIn: '2025-01-07T22:00:03',
  proposedCheckOut: '2025-01-08T06:00:04',
  reason: ' Quên ghi nhận ',
}
const plan = {
  date: form.workDate,
  shiftId: 'shift',
  shiftVersion: 3,
  definition: { overnight: true },
}
const now = new Date('2025-01-08T08:00:00+07:00')
describe('attendance corrections', () => {
  it('converts Vietnam wall times including overnight and preserves observed versions', () => {
    expect(correctionBody(form, plan, { workDate: form.workDate, recordVersion: 2 }, now)).toEqual({
      workDate: form.workDate,
      shiftId: 'shift',
      shiftVersion: 3,
      recordVersion: 2,
      proposedCheckIn: '2025-01-07T15:00:03Z',
      proposedCheckOut: '2025-01-07T23:00:04Z',
      reason: 'Quên ghi nhận',
    })
    expect(correctionBody(form, plan, null, now).recordVersion).toBeNull()
    expect(localCorrectionTime('2025-01-07T23:00:04Z')).toBe('2025-01-08T06:00:04')
  })
  it('rejects missing schedules, stale dates, reversed/future times and blank reasons', () => {
    expect(() => correctionBody(form, null, null, now)).toThrow()
    expect(() => correctionBody(form, { ...plan, date: '2025-01-06' }, null, now)).toThrow()
    for (const change of [
      { proposedCheckIn: '' },
      { proposedCheckOut: form.proposedCheckIn },
      { proposedCheckOut: '2025-01-08T10:00' },
      { reason: '  ' },
    ])
      expect(() => correctionBody({ ...form, ...change }, plan, null, now)).toThrow()
  })
  it('recognizes the new navigation route', () => {
    expect(safeDestination('/attendance/corrections')).toBe('/attendance/corrections')
  })
})
