import { apiPath } from '../api/config.js'

export const calendarEndpoints = {
    calendar: apiPath('calendar'),
    health: apiPath('calendar', 'health-check'),
    events: apiPath('calendar-events'),
    event: (id) => apiPath('calendar-events', id),
    eventAction: (id, action) => apiPath('calendar-events', id, action),
}
