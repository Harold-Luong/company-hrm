import { defineContent } from '../i18n/content.js'
import { fieldLimits } from '../config/validation.js'
import { getDisplayLocale } from '../i18n/index.js'

export const correctionRules = defineContent('correctionRules', {
    parameters: {
        invalidReason: () => ({
            attendanceReason: fieldLimits.attendanceReason.toLocaleString(getDisplayLocale()),
        }),
    },
})

export const attendanceErrors = defineContent('attendanceErrors')

export const attendanceRequestRules = defineContent('attendanceRequestRules', {
    methods: { invalidExpectedTime: true },
    parameters: {
        invalidReason: () => ({
            attendanceReason: fieldLimits.attendanceReason.toLocaleString(getDisplayLocale()),
        }),
    },
})

export const shiftRules = defineContent('shiftRules', {
    parameters: { invalidName: () => ({ shiftName: fieldLimits.shiftName }) },
})

export const attendancePeriod = defineContent('attendancePeriod', {
    methods: { reasonLabel: true },
})

export const attendanceReportSummary = defineContent('attendanceReportSummary', {
    methods: {
        employeeCount: true,
        detailsLabel: true,
        employeeDays: true,
        incompleteDays: true,
        noScheduleDays: true,
        annualLeaveDays: true,
        unpaidLeaveDays: true,
        lateEarlyDays: true,
        approvedOvertime: true,
        reviewDays: true,
        invalidRecordDays: true,
        conflictDays: true,
        pendingRequests: true,
        ongoingShifts: true,
        visibleCount: true,
    },
})

export const attendanceRequestScope = defineContent('attendanceRequestScope')

export const attendanceStats = defineContent('attendanceStats')

export const attendanceTable = defineContent('attendanceTable', {
    methods: {
        resultCount: true,
        correctedTimes: true,
        approvedOvertime: true,
        leaveDays: true,
        annualLeave: true,
        unpaidLeave: true,
        roundedMinutes: true,
        pendingCorrections: true,
        pendingOvertime: true,
        approvedRequests: true,
        pendingRequests: true,
        unapprovedMinutes: true,
        sourcesAt: true,
    },
})

export const profileAttendance = defineContent('profileAttendance', {
    methods: { monthTitle: true },
})

export const quickAttendance = defineContent('quickAttendance', { methods: { workDate: true } })

export const attendanceCorrections = defineContent('attendanceCorrections', {
    methods: {
        currentTimes: true,
        originalTimes: true,
        requestedTimes: true,
        reasonSummary: true,
        reviewedAt: true,
        reviewNote: true,
    },
})

export const attendanceOvertime = defineContent('attendanceOvertime', {
    methods: { durationSummary: true, recordedTimes: true, reviewedAt: true },
})

export const attendanceReports = defineContent('attendanceReports', {
    methods: {
        reviewSummary: true,
        incompleteHours: true,
        approvedOvertime: true,
        sourcesUpdated: true,
        employeeCountSuffix: true,
        detailEyebrow: true,
        detailPeriod: true,
        reviewDays: true,
        incompleteDays: true,
        lateEarlyDays: true,
        ongoingSummary: true,
    },
})

export const attendanceRequests = defineContent('attendanceRequests', {
    methods: {
        periodHint: true,
        reasonLength: (input) => ({
            ...input,
            attendanceReason: fieldLimits.attendanceReason.toLocaleString(getDisplayLocale()),
        }),
        pendingOnPage: true,
        count: true,
    },
})

export const attendanceSchedules = defineContent('attendanceSchedules', {
    methods: {
        dailyDuration: true,
        removeEmployee: true,
        previewSummary: true,
        affectedSchedules: true,
        conflicts: true,
    },
})

export const attendanceShifts = defineContent('attendanceShifts', {
    methods: {
        deactivateConfirmation: true,
        intervalLabel: true,
        intervalStart: true,
        intervalEnd: true,
        removeInterval: true,
        history: true,
        historyVersion: true,
    },
})

export const attendance = defineContent('attendance', { methods: { recordingWindow: true } })
