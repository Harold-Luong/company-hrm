import database from '../assets/everrise_vn_event_calendar_mock_db.json'

// Read boundary for the future API. No mock update/delete operations or local persistence.
export const calendarSource = {
    async load() {
        return structuredClone(database)
    },
}

export const categories = {
    national_holiday: 'Lễ Việt Nam',
    japan_national_holiday: 'Lễ Nhật Bản',
    calendar_event: 'Sự kiện khác',
    salary_day: 'Ngày lương',
    official_party: 'Tiệc công ty',
    company_travel: 'Du lịch công ty',
}
export const detailTypes = {
    statutory_holiday: 'Nghỉ theo quy định',
    company_granted_holiday: 'Công ty cho thêm',
    substitute_holiday: 'Nghỉ bù',
    paid_leave_deduction: 'Trừ ngày phép',
}
export const typeLabel = (type) => detailTypes[type] || categories[type] || type
export const isoDay = (year, month, day) =>
    `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
export const coversDate = (event, date) => event.start_date <= date && date <= event.end_date
export function detailCoversDate(detail, date) {
    return (
        detail.date === date ||
        detail.dates?.includes(date) ||
        (detail.start_date && detail.end_date && coversDate(detail, date)) ||
        false
    )
}
export function dayEntries(events, date) {
    return events
        .filter((event) => coversDate(event, date))
        .map((event) => {
            const details = (event.details || []).filter((detail) => detailCoversDate(detail, date))
            return { event, details, type: details[0]?.type || event.category }
        })
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
export function displayRange(start, end) {
    return start === end ? displayDate(start) : `${displayDate(start)} – ${displayDate(end)}`
}
export function detailDates(detail) {
    if (detail.date) return displayDate(detail.date)
    if (detail.dates) return detail.dates.map(displayDate).join(', ')
    if (detail.start_date && detail.end_date) return displayRange(detail.start_date, detail.end_date)
    return ''
}
