import { attendanceErrors as copy } from '../content/attendance.js'
import { serviceError } from '../api/errors.js'

export function attendanceError(error) {
    if (!(error?.status >= 0)) return error.message || copy.unexpected
    if (error.status === 412 && /correction/i.test(error.message))
        return copy.correctionSourcesChanged
    if (error.status === 412 || error.status === 428) return copy.staleVersion
    if (error.status === 0) return copy.interrupted
    if (error.status === 503) return copy.unavailable
    if (error.status === 422) return copy.rangeTooLarge
    const messages = {
        get 'Leave or holidays changed during closing; reload and retry'() {
            return copy.sourcesChangedDuringClosing
        },
        get 'Period must be closed before checking sources'() {
            return copy.periodNotClosed
        },

        get 'Attendance period is closed; HR/ADMIN must reopen it with a reason'() {
            return copy.periodClosed
        },
        get 'Schedule overlaps a closed attendance period'() {
            return copy.scheduleAffectsClosedPeriod
        },
        get 'Only completed months can be closed'() {
            return copy.incompleteMonth
        },
        get 'Resolve pending attendance, correction and OT requests before closing'() {
            return copy.pendingAttendanceRequests
        },
        get 'Complete approved OT recordings before closing'() {
            return copy.incompleteOvertime
        },
        get 'Resolve pending leave and leave adjustments before closing'() {
            return copy.pendingLeave
        },
        get 'Resolve missing punches and source conflicts before closing'() {
            return copy.missingPunches
        },
        get 'There are no employees to close'() {
            return copy.noEmployees
        },
        get 'Period is already closed'() {
            return copy.alreadyClosed
        },
        get 'Period is already open'() {
            return copy.alreadyOpen
        },

        get 'You cannot review your own attendance correction'() {
            return copy.selfReviewCorrection
        },
        get 'Only pending corrections can be reviewed'() {
            return copy.alreadyReviewed
        },
        get 'Only pending corrections can be edited or cancelled'() {
            return copy.pendingOnly
        },
        get 'Only the owner may edit or cancel a correction'() {
            return copy.ownerOnly
        },
        get 'A pending correction already exists for this date'() {
            return copy.correctionPending
        },
        get 'Correction date cannot be in the future'() {
            return copy.futureCorrection
        },
        get 'No assigned schedule for correction date'() {
            return copy.missingCorrectionSchedule
        },
        get 'Corrections can only be submitted after the shift ends'() {
            return copy.shiftNotEnded
        },
        get 'Corrected times must be ordered, not in the future and inside the recording window (whole seconds)'() {
            return copy.invalidCorrectedTimes
        },
        get 'Corrected times are unchanged'() {
            return copy.unchangedCorrectedTimes
        },
        get 'Employee is not eligible on the correction date'() {
            return copy.ineligibleCorrection
        },
        get 'Corrected times conflict with leave or company holidays'() {
            return copy.correctionLeaveConflict
        },
        get 'Attendance correction not found'() {
            return copy.correctionNotFound
        },

        get 'Schedule overlaps an adjacent assigned shift'() {
            return copy.overlappingShift
        },
        get 'Schedule affects approved OT; choose another date range'() {
            return copy.scheduleAffectsOvertime
        },
        get 'You cannot review your own OT request'() {
            return copy.selfReviewOvertime
        },
        get 'OT must be in the future, at minute precision, and shorter than 24 hours'() {
            return copy.invalidOvertimeInterval
        },
        get 'OT overlaps another pending or approved request'() {
            return copy.overlappingOvertime
        },
        get 'OT must be outside regular working intervals'() {
            return copy.regularHoursOvertime
        },
        get 'OT conflicts with leave'() {
            return copy.overtimeLeaveConflict
        },
        get 'OT must be approved before recording time'() {
            return copy.overtimeNotApproved
        },
        get 'Outside the approved OT interval'() {
            return copy.outsideOvertimeInterval
        },
        get 'Finish the previous overnight shift first'() {
            return copy.unfinishedOvernightShift
        },

        get 'No assigned schedule for request date'() {
            return copy.missingRequestSchedule
        },
        get 'An active request already exists for this date, period and type'() {
            return copy.duplicateRequest
        },
        get 'A leave request already covers this period'() {
            return copy.leaveCoversPeriod
        },
        get 'Cannot request time on a company holiday'() {
            return copy.holidayRequest
        },
        get 'Late and early requests cannot cover the entire work interval'() {
            return copy.fullIntervalRequest
        },
        get 'Only pending requests can be reviewed'() {
            return copy.alreadyReviewed
        },
        get 'Only pending requests can be edited or cancelled'() {
            return copy.pendingOnly
        },
        get 'You cannot review your own attendance request'() {
            return copy.selfReviewRequest
        },
        get 'Past requests cannot be edited'() {
            return copy.pastRequest
        },
        get 'A rejection reason is required'() {
            return copy.missingRejectionReason
        },
        get 'Schedule affects approved attendance requests; resolve conflicts first'() {
            return copy.scheduleAffectsRequests
        },

        get 'Check-in/out is only allowed from the company network'() {
            return copy.companyNetworkRequired
        },
        get 'A trusted single client IP is required'() {
            return copy.unverifiedNetwork
        },
        get 'Outside the assigned recording window'() {
            return copy.outsideRecordingWindow
        },
        get 'No assigned schedule for today'() {
            return copy.missingTodaySchedule
        },
        get 'Employee is not eligible to record attendance'() {
            return copy.ineligibleEmployee
        },
        get 'Attendance has already been recorded for this day'() {
            return copy.alreadyCheckedIn
        },
        get 'Already checked out'() {
            return copy.alreadyCheckedOut
        },
        get 'Check-in is required first'() {
            return copy.checkInRequired
        },
        get 'Schedule affects recorded attendance or approved leave; resolve conflicts first'() {
            return copy.scheduleAffectsRecords
        },
    }
    return messages[error.message] || serviceError(error)
}
