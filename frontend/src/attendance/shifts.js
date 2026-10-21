import { fieldLimits } from '../config/validation.js'
import { shiftRules as copy } from '../content/attendance.js'
import { COMPANY_TIMEZONE } from '../shared/date.js'

export const shiftLabel = (definition) =>
    definition?.intervals
        ?.map(
            (i) =>
                `${i.start.slice(0, 5)}–${i.end.slice(0, 5)}${definition.overnight ? copy.nextDaySuffix : ''}`,
        )
        .join(' · ') || copy.noShift
export function shiftBody(form) {
    const intervals = form.intervals.map((i) => ({
        period: i.period,
        start: i.start.slice(0, 5),
        end: i.end.slice(0, 5),
    }))
    if (!form.name.trim() || form.name.trim().length > fieldLimits.shiftName)
        throw new Error(copy.invalidName)
    if (!intervals.length || intervals.length > 2) throw new Error(copy.invalidIntervalCount)
    const periods = new Set()
    for (const [index, i] of intervals.entries()) {
        if (
            !['MORNING', 'AFTERNOON'].includes(i.period) ||
            periods.has(i.period) ||
            !/^([01]\d|2[0-3]):[0-5]\d$/.test(i.start) ||
            !/^([01]\d|2[0-3]):[0-5]\d$/.test(i.end) ||
            (!form.overnight && i.start >= i.end) ||
            (index > 0 && i.start < intervals[index - 1].end)
        )
            throw new Error(copy.overlappingIntervals)
        periods.add(i.period)
    }
    if (
        form.overnight &&
        (intervals.length !== 1 ||
            intervals[0].end >= intervals[0].start ||
            form.checkInFrom <= intervals[0].end ||
            form.checkOutUntil >= form.checkInFrom)
    )
        throw new Error(copy.invalidOvernightShift)
    if (
        !form.checkInFrom ||
        !form.checkOutUntil ||
        form.checkInFrom > intervals[0].start ||
        form.checkOutUntil < intervals.at(-1).end
    )
        throw new Error(copy.invalidRecordingWindow)
    return {
        name: form.name.trim(),
        mode: 'FIXED_SHIFT',
        timezone: COMPANY_TIMEZONE,
        intervals,
        overnight: !!form.overnight,
        checkInFrom: form.checkInFrom,
        checkOutUntil: form.checkOutUntil,
    }
}
