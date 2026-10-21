import { pagination } from '../config/presentation.js'
import { requestJson } from '../api/client.js'
import { versionHeader, idempotencyHeader } from '../api/headers.js'
import { notifyRequestChange } from '../notifications/changes.js'
import { attendanceEndpoints as endpoints } from './endpoints.js'

export const attendance = {
    period: (month) => requestJson(endpoints.period(month)),
    checkPeriodSources: (month) => requestJson(endpoints.periodSources(month)),
    periodHistory: (month) => requestJson(endpoints.periodHistory(month)),
    changePeriod: (month, action, version, reason) =>
        requestJson(endpoints.periodAction(month, action), {
            method: 'POST',
            headers: versionHeader(version),
            body: { reason },
        }),
    corrections: (inbox = false, status = '', page = 0) =>
        requestJson(endpoints.corrections(inbox), {
            query: { status, page, size: pagination.pageSize },
        }),
    saveCorrection: (id, body, version, key) =>
        requestJson(endpoints.correction(id), {
            method: id ? 'PUT' : 'POST',
            headers: id ? versionHeader(version) : idempotencyHeader(key),
            body,
        }),
    cancelCorrection: (row) =>
        requestJson(endpoints.correctionAction(row.id, 'cancel'), {
            method: 'POST',
            headers: versionHeader(row.version),
        }),
    decideCorrection: (row, status, reviewNote) =>
        requestJson(endpoints.correctionAction(row.id, 'decision'), {
            method: 'POST',
            headers: versionHeader(row.version),
            body: { status, reviewNote },
        }),
    correctionHistory: (id, page = 0) =>
        requestJson(endpoints.correctionAction(id, 'history'), {
            query: { page, size: pagination.pageSize },
        }),
    overtime: (inbox = false, status = '', page = 0) =>
        requestJson(endpoints.overtime(inbox), { query: { status, page, size: pagination.pageSize } }),
    createOvertime: (body, key) =>
        requestJson(endpoints.overtime(), { method: 'POST', headers: idempotencyHeader(key), body }),
    decideOvertime: (row, status, reviewNote) =>
        requestJson(endpoints.overtimeAction(row.id, 'decision'), {
            method: 'POST',
            headers: versionHeader(row.version),
            body: { status, reviewNote },
        }),
    cancelOvertime: (row) =>
        requestJson(endpoints.overtimeAction(row.id, 'cancel'), {
            method: 'POST',
            headers: versionHeader(row.version),
        }),
    punchOvertime: (row, action, key) =>
        requestJson(endpoints.overtimeAction(row.id, action), {
            method: 'POST',
            headers: idempotencyHeader(key),
        }),
    requests: (inbox = false, status = '', page = 0) =>
        requestJson(endpoints.requests(inbox), { query: { status, page, size: pagination.pageSize } }),
    pendingRequestCount: () => requestJson(endpoints.pendingRequestCount),
    saveRequest: (id, body, version, key) => {
        const payload = { ...body }
        delete payload.requestedMinutes
        return requestJson(endpoints.request(id), {
            method: id ? 'PUT' : 'POST',
            headers: id ? versionHeader(version) : idempotencyHeader(key),
            body: payload,
        }).then(notifyRequestChange)
    },
    cancelRequest: (row) =>
        requestJson(endpoints.requestAction(row.id, 'cancel'), {
            method: 'POST',
            headers: versionHeader(row.version),
        }).then(notifyRequestChange),
    decideRequest: (row, status, reviewNote) =>
        requestJson(endpoints.requestAction(row.id, 'decision'), {
            method: 'POST',
            headers: versionHeader(row.version),
            body: { status, reviewNote },
        }).then(notifyRequestChange),
    requestHistory: (id) => requestJson(endpoints.requestAction(id, 'history')),
    shifts: (page = 0) =>
        requestJson(endpoints.shifts, { query: { page, size: pagination.pageSize } }),
    shift: (id) => requestJson(endpoints.shift(id)),
    shiftHistory: (id) => requestJson(endpoints.shiftHistory(id)),
    saveShift: (id, body, version) =>
        requestJson(endpoints.shift(id), {
            method: id ? 'PUT' : 'POST',
            ...(id ? { headers: versionHeader(version) } : {}),
            body,
        }),
    archiveShift: (id, version) =>
        requestJson(endpoints.shift(id), { method: 'DELETE', headers: versionHeader(version) }),
    preview: (body) => requestJson(endpoints.schedulePreview, { method: 'POST', body }),
    apply: (body, revision, key) =>
        requestJson(endpoints.scheduleApply, {
            method: 'POST',
            headers: { ...versionHeader(revision), ...idempotencyHeader(key) },
            body,
        }),
    scheduleHistory: (page = 0) =>
        requestJson(endpoints.scheduleHistory, { query: { page, size: pagination.pageSize } }),
    schedule: (from, until, employeeId = '') =>
        requestJson(endpoints.schedule(employeeId), { query: { from, until } }),
    mine: (from, until) => requestJson(endpoints.mine, { query: { from, until } }),
    punch: (action, key) =>
        requestJson(endpoints.punch(action), { method: 'POST', headers: idempotencyHeader(key) }),
    report: (filters) => requestJson(endpoints.report, { query: filters }),
    export: (filters) =>
        requestJson(endpoints.export, {
            query: filters,
            responseType: 'blob',
            headers: { Accept: 'text/csv' },
        }),
    refresh: (row) =>
        requestJson(endpoints.refresh(row.employeeId, row.workDate), {
            method: 'POST',
            headers: versionHeader(row.recordVersion),
        }),
}
