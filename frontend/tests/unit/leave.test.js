import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { submission, today, leaveError } from '../../src/leave/helpers.js'
import { ApiError } from '../../src/auth/api.js'
import { safeDestination } from '../../src/auth/navigation.js'

describe('leave forms', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2030-01-01T18:00:00Z'))
  })
  afterEach(() => vi.useRealTimers())
  const form = () => ({
    leaveType: 'ANNUAL',
    startDate: '2030-01-02',
    endDate: '2030-01-03',
    period: 'FULL_DAY',
    reason: ' Family matters ',
  })
  it('uses the Vietnam business date', () => expect(today()).toBe('2030-01-02'))
  it('sends only allowed fields and trims the reason', () => {
    expect(submission({ ...form(), employeeId: 'forged', status: 'APPROVED' })).toEqual({
      ...form(),
      reason: 'Family matters',
    })
  })
  it.each([
    { startDate: '2030-01-01' },
    { endDate: '2030-01-01' },
    { endDate: '' },
    { reason: '  ' },
    { reason: 'x'.repeat(2001) },
    { leaveType: 'INVALID' },
    { leaveType: 'SICK' },
    { leaveType: 'OTHER' },
    { leaveType: 'toString' },
    { period: 'INVALID' },
    { endDate: '2031-01-03' },
  ])('rejects invalid input %j', (change) =>
    expect(() => submission({ ...form(), ...change })).toThrow(),
  )
  it('accepts a half day only on one date', () => {
    const halfDay = { ...form(), endDate: '2030-01-02', period: 'MORNING' }
    expect(submission(halfDay).period).toBe('MORNING')
    expect(() => submission({ ...form(), period: 'AFTERNOON' })).toThrow('chỉ áp dụng')
  })
  it.each(['ANNUAL', 'UNPAID'])('keeps a personal reason separate from %s', (leaveType) => {
    expect(submission({ ...form(), leaveType, reason: ' Khám bệnh ' })).toMatchObject({
      leaveType,
      reason: 'Khám bệnh',
    })
  })
  it('asks for reload after a stale version or unknown write result', () => {
    expect(leaveError(new ApiError(412, 'changed'))).toContain('tải lại')
    expect(leaveError(new ApiError(0, 'offline'))).toContain('trước khi gửi lại')
  })
  it.each(['/leave', '/leave/inbox', '/leave/requests/d38e31b7-0bba-420c-8eaf-50edbe5a61ae'])(
    'accepts safe leave destinations %s',
    (path) => {
      expect(safeDestination(path)).toBe(path)
    },
  )
  it.each([
    '/leave/../accounts/new',
    '/leave?next=https://example.com',
    '/leave/requests/not-an-id',
  ])('rejects invalid leave destinations %s', (path) => {
    expect(safeDestination(path)).toBe('/')
  })
})
