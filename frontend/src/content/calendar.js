import { defineContent } from '../i18n/content.js'
import { dateLimits } from '../config/validation.js'

export const calendarLeave = defineContent('calendarLeave')

export const calendarRules = defineContent('calendarRules')

export const calendarEventForm = defineContent('calendarEventForm')

export const calendarManagement = defineContent('calendarManagement', {
    parameters: {
        invalidRange: () => ({ maxRangeDays: dateLimits.maxRangeDays }),
        filterHint: () => ({ maxRangeDays: dateLimits.maxRangeDays }),
    },
})

export const eventCalendar = defineContent('eventCalendar', {
    methods: {
        yearTitle: true,
        yearCount: true,
        monthLabel: true,
        monthTitle: true,
        monthCount: true,
        yearPlanTitle: true,
        eventCount: true,
        emptyYear: true,
        monthHeading: true,
    },
})
