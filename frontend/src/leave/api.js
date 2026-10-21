import { pagination } from '../config/presentation.js'
import { requestJson } from '../api/client.js'
import { versionHeader } from '../api/headers.js'
import { notifyRequestChange } from '../notifications/changes.js'
import { leaveEndpoints as endpoints } from './endpoints.js'
import { today } from '../shared/date.js'

export const leave = {
  estimate: (from, until, period) =>
    requestJson(endpoints.estimate, { query: { from, until, period } }),
  list: (inbox, status = '', page = 0, size = pagination.pageSize) =>
    inbox && status === 'ADJUSTMENT_PENDING'
      ? requestJson(endpoints.adjustmentInbox, { query: { page, size } })
      : requestJson(endpoints.list(inbox), { query: { status, page, size } }),
  adjustments: (id) => requestJson(endpoints.adjustments(id)),
  adjust: (id, version, body) =>
    requestJson(endpoints.adjustments(id), {
      method: 'POST',
      headers: versionHeader(version),
      body,
    }).then(notifyRequestChange),
  decideAdjustment: (id, adjustmentId, action, version, note) =>
    requestJson(endpoints.adjustmentAction(id, adjustmentId, action), {
      method: 'PATCH',
      headers: versionHeader(version),
      body: { note },
    }).then(notifyRequestChange),
  balance: (year = Number(today().slice(0, 4))) =>
    requestJson(endpoints.balance, { query: { year } }),
  pendingCount: () => requestJson(endpoints.pendingCount),
  calendar: (year) => requestJson(endpoints.calendar, { query: { year } }),
  get: (id) => requestJson(endpoints.detail(id)),
  history: (id) => requestJson(endpoints.history(id)),
  submit: (body) =>
    requestJson(endpoints.requests, { method: 'POST', body }).then(notifyRequestChange),
  decide: (id, action, version, note = '') =>
    requestJson(endpoints.action(id, action), {
      method: 'PATCH',
      headers: versionHeader(version),
      ...(action === 'cancel' ? {} : { body: { note } }),
    }).then(notifyRequestChange),
}
