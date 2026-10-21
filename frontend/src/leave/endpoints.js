import { apiPath } from '../api/config.js'

const path = (...segments) => apiPath('leave', 'requests', ...segments)
export const leaveEndpoints = {
  requests: path(),
  list: (inbox) => path(inbox ? 'inbox' : 'mine'),
  adjustmentInbox: path('adjustments', 'inbox'),
  estimate: path('estimate'),
  adjustments: (id) => path(id, 'adjustments'),
  adjustmentAction: (id, adjustmentId, action) => path(id, 'adjustments', adjustmentId, action),
  balance: path('balance'),
  pendingCount: path('pending-count'),
  calendar: path('calendar'),
  detail: (id) => path(id),
  history: (id) => path(id, 'history'),
  action: (id, action) => path(id, action),
}
