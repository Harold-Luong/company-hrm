import { auth } from '@/auth/session.js'
import { notifyRequestChange } from '@/notifications/changes.js'

const base = '/api/v1/leave/requests'
export const leave = {
  list: (inbox, status = '', page = 0, size = 20) =>
    auth.request(`${base}/${inbox ? 'inbox' : 'mine'}?${new URLSearchParams({ status, page, size })}`),
  balance: (year = new Date().getFullYear()) =>
    auth.request(`${base}/balance?${new URLSearchParams({ year })}`),
  pendingCount: () => auth.request(`${base}/pending-count`),
  calendar: (year) => auth.request(`${base}/calendar?${new URLSearchParams({ year })}`),
  get: (id) => auth.request(`${base}/${encodeURIComponent(id)}`),
  history: (id) => auth.request(`${base}/${encodeURIComponent(id)}/history`),
  submit: (body) =>
    auth.request(base, { method: 'POST', body: JSON.stringify(body) }).then(notifyRequestChange),
  decide: (id, action, version, note = '') =>
    auth
      .request(`${base}/${encodeURIComponent(id)}/${action}`, {
        method: 'PATCH',
        headers: { 'If-Match': `"${version}"` },
        ...(action === 'cancel' ? {} : { body: JSON.stringify({ note }) }),
      })
      .then(notifyRequestChange),
}
