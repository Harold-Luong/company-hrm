import { apiPath } from '../api/config.js'

const path = (...segments) => apiPath('attendance', ...segments)
const collection = (resource, inbox) => path(resource, ...(inbox ? ['inbox'] : []))
const item = (resource, id) => path(resource, ...(id ? [id] : []))

export const attendanceEndpoints = {
    period: (month) => path('periods', month),
    periodSources: (month) => path('periods', month, 'check-sources'),
    periodHistory: (month) => path('periods', month, 'history'),
    periodAction: (month, action) => path('periods', month, action),
    corrections: (inbox) => collection('corrections', inbox),
    correction: (id) => item('corrections', id),
    correctionAction: (id, action) => path('corrections', id, action),
    overtime: (inbox) => collection('overtime', inbox),
    overtimeAction: (id, action) => path('overtime', id, action),
    requests: (inbox) => collection('requests', inbox),
    request: (id) => item('requests', id),
    requestAction: (id, action) => path('requests', id, action),
    pendingRequestCount: path('requests', 'pending-count'),
    shifts: path('shifts'),
    shift: (id) => item('shifts', id),
    shiftHistory: (id) => path('shifts', id, 'history'),
    schedulePreview: path('schedules', 'preview'),
    scheduleApply: path('schedules', 'apply'),
    scheduleHistory: path('schedules', 'history'),
    schedule: (employeeId) =>
        path('schedules', ...(employeeId ? ['employees', employeeId] : ['mine'])),
    mine: path('mine'),
    punch: (action) => path(action),
    report: path('reports'),
    export: path('reports', 'export.csv'),
    refresh: (employeeId, date) => path('employees', employeeId, 'days', date, 'refresh-coverage'),
}
