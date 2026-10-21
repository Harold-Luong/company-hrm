import { requestJson } from '../api/client.js'
import { versionHeader } from '../api/headers.js'
import { calendarEndpoints as endpoints } from './endpoints.js'

export function getCalendar(year) {
    return requestJson(endpoints.calendar, { query: { year } })
}

export function getCalendarHealth() {
    return requestJson(endpoints.health)
}

export const calendarManagement = {
    list: (filters, page = 0) =>
        requestJson(endpoints.events, {
            query: {
                from: filters.from,
                to: filters.to,
                page,
                size: filters.size,
                type: filters.type,
                status: filters.status,
            },
        }),
    get: (id) => requestJson(endpoints.event(id)),
    create: (body) => requestJson(endpoints.events, { method: 'POST', body }),
    update: (id, version, body) =>
        requestJson(endpoints.event(id), {
            method: 'PUT',
            headers: versionHeader(version),
            body,
        }),
    publish: (id, version) =>
        requestJson(endpoints.eventAction(id, 'publish'), {
            method: 'PATCH',
            headers: versionHeader(version),
        }),
    cancel: (id, version, reason) =>
        requestJson(endpoints.eventAction(id, 'cancel'), {
            method: 'PATCH',
            headers: versionHeader(version),
            body: { reason },
        }),
    remove: (id, version) =>
        requestJson(endpoints.event(id), {
            method: 'DELETE',
            headers: versionHeader(version),
        }),
}
