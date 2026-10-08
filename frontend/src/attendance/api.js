import { auth } from '@/auth/session.js'

const base = '/api/v1/attendance'
const query = (values) =>
  new URLSearchParams(Object.entries(values).filter(([, value]) => value !== '' && value != null))
export const attendance = {
  overtime: (inbox = false, status = '', page = 0) =>
    auth.request(`${base}/overtime${inbox ? '/inbox' : ''}?${query({ status, page, size: 20 })}`),
  createOvertime: (body, key) =>
    auth.request(`${base}/overtime`, {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
      body: JSON.stringify(body),
    }),
  decideOvertime: (row, status, reviewNote) =>
    auth.request(`${base}/overtime/${row.id}/decision`, {
      method: 'POST',
      headers: { 'If-Match': `"${row.version}"` },
      body: JSON.stringify({ status, reviewNote }),
    }),
  cancelOvertime: (row) =>
    auth.request(`${base}/overtime/${row.id}/cancel`, {
      method: 'POST',
      headers: { 'If-Match': `"${row.version}"` },
    }),
  punchOvertime: (row, action, key) =>
    auth.request(`${base}/overtime/${row.id}/${action}`, {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
    }),
  requests: (inbox = false, status = '', page = 0) =>
    auth.request(`${base}/requests${inbox ? '/inbox' : ''}?${query({ status, page, size: 20 })}`),
  saveRequest: (id, body, version, key) => {
    const payload = { ...body }
    delete payload.requestedMinutes
    return auth.request(`${base}/requests${id ? `/${encodeURIComponent(id)}` : ''}`, {
      method: id ? 'PUT' : 'POST',
      headers: id ? { 'If-Match': `"${version}"` } : { 'Idempotency-Key': key },
      body: JSON.stringify(payload),
    })
  },
  cancelRequest: (row) =>
    auth.request(`${base}/requests/${encodeURIComponent(row.id)}/cancel`, {
      method: 'POST',
      headers: { 'If-Match': `"${row.version}"` },
    }),
  decideRequest: (row, status, reviewNote) =>
    auth.request(`${base}/requests/${encodeURIComponent(row.id)}/decision`, {
      method: 'POST',
      headers: { 'If-Match': `"${row.version}"` },
      body: JSON.stringify({ status, reviewNote }),
    }),
  requestHistory: (id) => auth.request(`${base}/requests/${encodeURIComponent(id)}/history`),
  shifts: (page = 0) => auth.request(`${base}/shifts?page=${page}&size=20`),
  shift: (id) => auth.request(`${base}/shifts/${encodeURIComponent(id)}`),
  shiftHistory: (id) => auth.request(`${base}/shifts/${encodeURIComponent(id)}/history`),
  saveShift: (id, body, version) =>
    auth.request(`${base}/shifts${id ? `/${encodeURIComponent(id)}` : ''}`, {
      method: id ? 'PUT' : 'POST',
      ...(id ? { headers: { 'If-Match': `"${version}"` } } : {}),
      body: JSON.stringify(body),
    }),
  archiveShift: (id, version) =>
    auth.request(`${base}/shifts/${encodeURIComponent(id)}`, {
      method: 'DELETE',
      headers: { 'If-Match': `"${version}"` },
    }),
  preview: (body) =>
    auth.request(`${base}/schedules/preview`, { method: 'POST', body: JSON.stringify(body) }),
  apply: (body, revision, key) =>
    auth.request(`${base}/schedules/apply`, {
      method: 'POST',
      headers: { 'If-Match': `"${revision}"`, 'Idempotency-Key': key },
      body: JSON.stringify(body),
    }),
  scheduleHistory: (page = 0) => auth.request(`${base}/schedules/history?page=${page}&size=20`),
  schedule: (from, until, employeeId = '') =>
    auth.request(
      `${base}/schedules/${employeeId ? `employees/${encodeURIComponent(employeeId)}` : 'mine'}?${query({ from, until })}`,
    ),
  mine: (from, until) => auth.request(`${base}/mine?${query({ from, until })}`),
  punch: (action, key) =>
    auth.request(`${base}/${action}`, { method: 'POST', headers: { 'Idempotency-Key': key } }),
  report: (filters) => auth.request(`${base}/reports?${query(filters)}`),
  export: (filters) =>
    auth.request(`${base}/reports/export.csv?${query(filters)}`, {
      responseType: 'blob',
      headers: { Accept: 'text/csv' },
    }),
  refresh: (row) =>
    auth.request(
      `${base}/employees/${encodeURIComponent(row.employeeId)}/days/${row.workDate}/refresh-coverage`,
      {
        method: 'POST',
        headers: { 'If-Match': `"${row.recordVersion}"` },
      },
    ),
}
