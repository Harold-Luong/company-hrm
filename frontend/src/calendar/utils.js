import { localizedDateFormatter } from '../shared/intl.js'
import { dateLabel } from '../shared/date.js'
import { calendarDate } from '../shared/date.js'
import { CALENDAR_TIMEZONE } from './constants.js'

const dateTimeFormatter = localizedDateFormatter({
    timeZone: CALENDAR_TIMEZONE,
    dateStyle: 'short',
    timeStyle: 'short',
})

export { calendarDate } from '../shared/date.js'
export const isoDay = (year, month, day) =>
    `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`

// Derived grid boundaries, keeping the API's original dates and instants intact.
export function eventDateRange(event) {
    return {
        startDay: event.allDay ? event.startDate : calendarDate(new Date(event.startAt)),
        // All-day end dates are inclusive; timed end instants are exclusive.
        endDay: event.allDay ? event.endDate : calendarDate(new Date(Date.parse(event.endAt) - 1)),
    }
}

export function dayEntries(events, date) {
    return events
        .filter((event) => {
            const { startDay, endDay } = eventDateRange(event)
            return startDay <= date && date <= endDay
        })
        .map((event) => ({ event, type: event.holidayKind || event.type }))
}

export function monthCells(year, month) {
    const offset = (new Date(Date.UTC(year, month - 1, 1)).getUTCDay() + 6) % 7
    const length = new Date(Date.UTC(year, month, 0)).getUTCDate()
    return Array.from({ length: 42 }, (_, index) => {
        const day = index - offset + 1
        return day > 0 && day <= length
            ? { date: isoDay(year, month, day), day, weekend: index % 7 >= 5 }
            : null
    })
}

export const displayDate = dateLabel

export function displayEventRange(event) {
    if (!event.allDay) {
        return `${dateTimeFormatter.format(new Date(event.startAt))} – ${dateTimeFormatter.format(new Date(event.endAt))}`
    }
    return event.startDate === event.endDate
        ? displayDate(event.startDate)
        : `${displayDate(event.startDate)} – ${displayDate(event.endDate)}`
}
