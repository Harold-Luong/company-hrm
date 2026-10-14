import { auth } from '@/auth/session.js'
import { notifyRequestChange } from '@/notifications/changes.js'

const base = '/api/v1/leave/requests'
export const leave = {
  estimate: (from, until, period) =>
    auth.request(`${base}/estimate?${new URLSearchParams({ from, until, period })}`),
  list: (inbox, status = '', page = 0, size = 20) =>
    auth.request(
      inbox && status === 'ADJUSTMENT_PENDING'
        ? `${base}/adjustments/inbox?${new URLSearchParams({ page, size })}`
        : `${base}/${inbox ? 'inbox' : 'mine'}?${new URLSearchParams({ status, page, size })}`,
    ),
  adjustments: (id) => auth.request(`${base}/${encodeURIComponent(id)}/adjustments`),
  adjust: (id, version, body) =>
    auth
      .request(`${base}/${encodeURIComponent(id)}/adjustments`, {
        method: 'POST',
        headers: { 'If-Match': `"${version}"` },
        body: JSON.stringify(body),
      })
      .then(notifyRequestChange),
  decideAdjustment: (id, adjustmentId, action, version, note) =>
    auth
      .request(
        `${base}/${encodeURIComponent(id)}/adjustments/${encodeURIComponent(adjustmentId)}/${action}`,
        {
          method: 'PATCH',
          headers: { 'If-Match': `"${version}"` },
          body: JSON.stringify({ note }),
        },
      )
      .then(notifyRequestChange),
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
