import { describe, expect, it } from 'vitest'
import {
    attendanceOverview,
    reviewReasons,
    summarizeAttendance,
} from '../../src/attendance/report.js'
const row = (overrides = {}) => ({
    employeeId: 'a',
    employeeCode: 'NV01',
    employeeName: 'Nguyễn An',
    workDate: '2026-10-01',
    status: 'CLOSED',
    workMinutesCounted: 450,
    annualLeaveMinutes: 0,
    unpaidLeaveMinutes: 0,
    roundedLateMinutes: 15,
    roundedEarlyMinutes: 15,
    overtime: { approvedMinutes: 120, countedMinutes: 90, pendingIds: [] },
    ...overrides,
})
describe('HR attendance summaries', () => {
    it('groups by employee, uses the latest identity and sums server minutes without day conversion', () => {
        const rows = [
            row({
                workDate: '2026-10-02',
                employeeName: 'Tên mới',
                workMinutesCounted: 105,
                annualLeaveMinutes: 120,
                unpaidLeaveMinutes: 60,
            }),
            row(),
            row({ employeeId: 'b', workMinutesCounted: 240 }),
        ]
        const copy = structuredClone(rows)
        const people = summarizeAttendance(rows)
        expect(people).toHaveLength(2)
        expect(people[0]).toMatchObject({
            employeeName: 'Tên mới',
            workMinutes: 555,
            annualMinutes: 120,
            unpaidMinutes: 60,
            lateMinutes: 30,
            earlyMinutes: 30,
            lateEarlyDays: 2,
            otCountedMinutes: 180,
            otApprovedMinutes: 240,
        })
        expect(attendanceOverview(people)).toMatchObject({
            employees: 2,
            workMinutes: 795,
            otCountedMinutes: 270,
            otApprovedMinutes: 360,
        })
        expect(rows).toEqual(copy)
    })
    it('keeps missing totals unknown while preserving genuine zero and excluding days without a shift', () => {
        const rows = [
            row({ status: 'MISSING_CHECK_OUT', workMinutesCounted: null }),
            row({ workDate: '2026-10-02', status: 'NO_SCHEDULE', workMinutesCounted: null }),
        ]
        const [person] = summarizeAttendance(rows)
        expect(person).toMatchObject({
            workMinutes: null,
            incompleteDays: 1,
            noScheduleDays: 1,
            reviewDays: 1,
        })
        expect(attendanceOverview([person]).workMinutes).toBeNull()
        const [withLeave] = summarizeAttendance([
            ...rows,
            row({ workDate: '2026-10-03', status: 'ON_LEAVE', workMinutesCounted: 0 }),
        ])
        expect(withLeave.workMinutes).toBe(0)
        expect(withLeave.incompleteDays).toBe(1)
    })
    it('keeps late and early totals unknown when no daily value has been calculated', () => {
        const [person] = summarizeAttendance([
            row({
                status: 'OPEN',
                workMinutesCounted: null,
                roundedLateMinutes: null,
                roundedEarlyMinutes: null,
            }),
        ])
        expect(person).toMatchObject({ lateMinutes: null, earlyMinutes: null, lateEarlyDays: 0 })
    })
    it('deduplicates pending requests across days and counts a day with multiple warnings only once', () => {
        const rows = [
            row({
                status: 'SOURCE_CONFLICT',
                workMinutesCounted: null,
                pendingLeaveIds: ['leave-1'],
                permissionCoverage: {
                    pendingRequestIds: ['permission-1'],
                    conflictingRequestIds: ['conflict-1'],
                },
                overtime: { pendingIds: ['ot-1'] },
            }),
            row({ workDate: '2026-10-02', pendingLeaveIds: ['leave-1'] }),
        ]
        const [person] = summarizeAttendance(rows)
        expect(person).toMatchObject({
            reviewDays: 2,
            pendingRequests: 3,
            issues: { record: 0, conflict: 1, pending: 2 },
        })
        expect(attendanceOverview([person])).toMatchObject({
            reviewEmployees: 1,
            reviewDays: 2,
            pendingRequests: 3,
            incompleteDays: 1,
        })
    })
    it('does not label an ongoing shift or a day without a schedule as a missing punch', () => {
        for (const status of ['NOT_STARTED', 'OPEN', 'NO_SCHEDULE', 'HOLIDAY', 'ON_LEAVE'])
            expect(reviewReasons(row({ status }))).toEqual([])
        const [person] = summarizeAttendance([
            row({ status: 'OPEN', workMinutesCounted: null }),
            row({ workDate: '2026-10-02', status: 'NOT_STARTED', workMinutesCounted: null }),
        ])
        expect(person).toMatchObject({ ongoingDays: 2, incompleteDays: 2, reviewDays: 0 })
    })
    it('recalculates warnings and totals after a day is reconciled', () => {
        const rows = [
            row(),
            row({ workDate: '2026-10-02', status: 'NO_RECORD', workMinutesCounted: null }),
        ]
        expect(attendanceOverview(summarizeAttendance(rows))).toMatchObject({
            workMinutes: 450,
            reviewEmployees: 1,
        })
        rows[1] = row({ workDate: '2026-10-02', workMinutesCounted: 240 })
        expect(attendanceOverview(summarizeAttendance(rows))).toMatchObject({
            workMinutes: 690,
            reviewEmployees: 0,
            incompleteDays: 0,
        })
        expect(attendanceOverview([])).toMatchObject({
            employees: 0,
            workMinutes: null,
            reviewEmployees: 0,
        })
    })
})
