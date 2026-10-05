import { auth } from '../auth/session.js'

export function getCalendar(year) {
  return auth.request(`/api/v1/calendar?year=${encodeURIComponent(year)}`)
}

export function getCalendarHealth() {
  return auth.request('/api/v1/calendar/health-check')
}

const eventsPath = '/api/v1/calendar-events'
const versionHeader = (version) => ({ 'If-Match': `"${version}"` })

export const calendarManagement = {
  list(filters, page = 0) {
    const query = new URLSearchParams({
      from: filters.from,
      to: filters.to,
      page,
      size: filters.size,
    })
    if (filters.type) query.set('type', filters.type)
    if (filters.status) query.set('status', filters.status)
    return auth.request(`${eventsPath}?${query}`)
  },
  get: (id) => auth.request(`${eventsPath}/${encodeURIComponent(id)}`),
  create: (body) => auth.request(eventsPath, { method: 'POST', body: JSON.stringify(body) }),
  update: (id, version, body) =>
    auth.request(`${eventsPath}/${encodeURIComponent(id)}`, {
      method: 'PUT',
      headers: versionHeader(version),
      body: JSON.stringify(body),
    }),
  publish: (id, version) =>
    auth.request(`${eventsPath}/${encodeURIComponent(id)}/publish`, {
      method: 'PATCH',
      headers: versionHeader(version),
    }),
  cancel: (id, version, reason) =>
    auth.request(`${eventsPath}/${encodeURIComponent(id)}/cancel`, {
      method: 'PATCH',
      headers: versionHeader(version),
      body: JSON.stringify({ reason }),
    }),
  remove: (id, version) =>
    auth.request(`${eventsPath}/${encodeURIComponent(id)}`, {
      method: 'DELETE',
      headers: versionHeader(version),
    }),
}
