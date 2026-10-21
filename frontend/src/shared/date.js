import { localizedDateFormatter } from './intl.js'
import { getDisplayLocale } from '../i18n/index.js'
import { common } from '../content/common.js'
import { COMPANY_TIMEZONE } from '../config/presentation.js'

const dayFormatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: COMPANY_TIMEZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
})
export const calendarDate = (date = new Date()) => dayFormatter.format(date)
export const today = () => calendarDate()
export const dateLabel = (value) =>
    value
        ? new Intl.DateTimeFormat(getDisplayLocale(), {
            timeZone: 'UTC',
            day: '2-digit',
            month: '2-digit',
            year: 'numeric',
        }).format(new Date(`${value}T00:00:00Z`))
        : common.missingValue
export const dateTimeLabel = (value, fallback = common.missingValue) =>
    value
        ? new Date(value).toLocaleString(getDisplayLocale(), { timeZone: COMPANY_TIMEZONE })
        : fallback

const timeFormatter = localizedDateFormatter({
    timeZone: COMPANY_TIMEZONE,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
})
export const timeLabel = (value) =>
    value ? timeFormatter.format(new Date(value)) : common.missingValue

export function formatDate(value) {
    if (!value) return common.noInformation
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return common.noInformation
    return localizedDateFormatter({
        dateStyle: 'medium',
        timeStyle: 'short',
        timeZone: COMPANY_TIMEZONE,
    }).format(date)
}
export { COMPANY_TIMEZONE }
