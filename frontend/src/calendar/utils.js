import { CALENDAR_TIMEZONE } from './constants.js'

const dateFormatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: CALENDAR_TIMEZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
})
const dateTimeFormatter = new Intl.DateTimeFormat('vi-VN', {
    timeZone: CALENDAR_TIMEZONE,
    dateStyle: 'short',
    timeStyle: 'short',
})

export const calendarDate = (date = new Date()) => dateFormatter.format(date)
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

export function displayDate(value) {
    const [year, month, day] = value.split('-')
    return `${day}/${month}/${year}`
}

export function displayEventRange(event) {
    if (!event.allDay) {
        return `${dateTimeFormatter.format(new Date(event.startAt))} – ${dateTimeFormatter.format(new Date(event.endAt))}`
    }
    return event.startDate === event.endDate
        ? displayDate(event.startDate)
        : `${displayDate(event.startDate)} – ${displayDate(event.endDate)}`
}
