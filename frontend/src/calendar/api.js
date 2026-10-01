import { apiRequest } from '../auth/api.js'
import { auth } from '../auth/session.js'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

export function getCalendar(year) {
  return auth.request(`${API_BASE_URL}/api/v1/calendar?year=${encodeURIComponent(year)}`)
}

export function getCalendarHealth() {
  return apiRequest(
    API_BASE_URL ? `${API_BASE_URL}/actuator/health` : '/api/v1/calendar/health-check',
  )
}

const eventsPath = `${API_BASE_URL}/api/v1/calendar-events`
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
