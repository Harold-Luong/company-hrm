import { defineContent } from '../i18n/content.js'
export { COMPANY_TIMEZONE as CALENDAR_TIMEZONE } from '../shared/date.js'
export const eventTypes = defineContent('eventTypes')
export const holidayKinds = defineContent('holidayKinds')
export const typeLabel = (type) => holidayKinds[type] || eventTypes[type] || type
export const calendarManagementRoles = ['HR', 'ADMIN']
export const eventStatuses = defineContent('eventStatuses')
