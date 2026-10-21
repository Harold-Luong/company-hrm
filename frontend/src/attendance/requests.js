import { fieldLimits } from '../config/validation.js'
import { attendanceRequestRules as copy } from '../content/attendance.js'
import { common } from '../content/common.js'
import { minuteOfDay } from '../shared/duration.js'
import { today } from '../shared/date.js'

export const requestTypes = {
    get LATE_ARRIVAL() {
        return copy.lateArrival
    },
    get EARLY_DEPARTURE() {
        return copy.earlyDeparture
    },
}
export const requestStatuses = {
    get PENDING() {
        return copy.pending
    },
    get APPROVED() {
        return copy.approved
    },
    get REJECTED() {
        return common.reject
    },
    get CANCELLED() {
        return copy.withdrawn
    },
}
export function requestBody(form, shift) {
    if (!Object.hasOwn(requestTypes, form.requestType)) throw new Error(copy.invalidType)
    if (!/^\d{4}-\d{2}-\d{2}$/.test(form.workDate)) throw new Error(copy.invalidDate)
    const date = new Date(`${form.workDate}T00:00:00Z`)
    if (
        !Number.isFinite(date.getTime()) ||
        date.toISOString().slice(0, 10) !== form.workDate ||
        form.workDate < today()
    )
        throw new Error(copy.pastDate)
    if (!shift?.definition || shift.date !== form.workDate) throw new Error(copy.noSchedule)
    const interval = shift.definition.intervals.find((value) => value.period === form.period)
    if (!interval) throw new Error(copy.invalidPeriod)
    const offset = (time) =>
        minuteOfDay(time) +
        (shift.definition.overnight && time.slice(0, 5) < interval.start.slice(0, 5) ? 1440 : 0)
    if (
        !/^([01]\d|2[0-3]):[0-5]\d$/.test(form.expectedTime) ||
        offset(form.expectedTime) <= offset(interval.start) ||
        offset(form.expectedTime) >= offset(interval.end)
    )
        throw new Error(copy.invalidExpectedTime({ start: interval.start, end: interval.end }))
    if (!form.reason.trim() || form.reason.trim().length > fieldLimits.attendanceReason)
        throw new Error(copy.invalidReason)
    return {
        workDate: form.workDate,
        shiftId: shift.shiftId,
        shiftVersion: shift.shiftVersion,
        requestType: form.requestType,
        period: form.period,
        expectedTime: form.expectedTime,
        requestedMinutes:
            form.requestType === 'LATE_ARRIVAL'
                ? offset(form.expectedTime) - offset(interval.start)
                : offset(interval.end) - offset(form.expectedTime),
        reason: form.reason.trim(),
    }
}
