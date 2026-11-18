import { ApiError } from '../api/errors.js'
import { today } from '../shared/date.js'
import { eventDateRange } from '../calendar/utils.js'
import { createSeed, dates, weekday, leaveUnits } from './seed.js'
import { demoPassword, demoStorageKey } from './config.js'

const fail = (status, message) => {
  throw new ApiError(status, message)
}
const now = () => new Date().toISOString()
const clone = (value) => JSON.parse(JSON.stringify(value))
export function readDemo() {
  try {
    const state = JSON.parse(localStorage.getItem(demoStorageKey))
    if (state?.schema === 1 && Array.isArray(state.employees)) return state
  } catch {
    /* A damaged or older demo starts from a fresh sample. */
  }
  return createSeed()
}
export function resetDemo() {
  localStorage.setItem(demoStorageKey, JSON.stringify(createSeed()))
  for (const key of ['company-hrm.demo.session', 'company-hrm.demo.signed-out'])
    localStorage.removeItem(key)
}
function page(rows, query) {
  const index = Math.max(0, Number(query.get('page')) || 0),
    size = Math.max(1, Number(query.get('size')) || 20)
  return {
    content: rows.slice(index * size, (index + 1) * size),
    page: index,
    size,
    totalElements: rows.length,
    totalPages: Math.ceil(rows.length / size),
  }
}
function filtered(rows, query) {
  return rows.filter(
    (row) =>
      ['status', 'type', 'employeeId'].every(
        (key) => !query.get(key) || row[key] === query.get(key),
      ) &&
      (!query.get('role') || row.roles?.includes(query.get('role'))) &&
      (!query.get('search') ||
        JSON.stringify(row).toLowerCase().includes(query.get('search').toLowerCase())),
  )
}
const find = (rows, id) =>
  rows.find((row) => String(row.id) === String(id)) || fail(404, 'Không tìm thấy dữ liệu demo')
function checkVersion(row, init) {
  const value = new Headers(init.headers).get('If-Match')
  if (value != null && Number(value.replaceAll('"', '')) !== row.version)
    fail(412, 'Dữ liệu đã thay đổi. Vui lòng tải lại.')
}
function history(state, key, action, user, extra = {}) {
  const item = {
    id: crypto.randomUUID(),
    action,
    actorUserId: user?.id,
    actorEmail: user?.email,
    occurredAt: now(),
    createdAt: now(),
    ...extra,
  }
  ;(state.histories[key] ||= []).unshift(item)
}
function employeeFields(state, user) {
  const e = find(state.employees, user.employeeId)
  return {
    employeeId: e.id,
    employeeName: `${e.lastName} ${e.firstName}`,
    employeeCode: e.employeeCode,
    requesterUserId: user.id,
  }
}
function plan(state, employeeId, date) {
  const day = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'][
    new Date(`${date}T00:00:00Z`).getUTCDay()
  ]
  const assignment = state.schedules.findLast(
    (s) =>
      s.from <= date &&
      (!s.until || date <= s.until) &&
      s.weekdays.includes(day) &&
      (s.scope === 'COMPANY_DEFAULT' || s.employeeIds.includes(employeeId)),
  )
  const shift = assignment ? find(state.shifts, assignment.shiftId) : state.shifts[0]
  const working = assignment || weekday(date)
  return {
    date,
    shiftId: working ? shift.id : null,
    shiftVersion: assignment?.shiftVersion ?? shift.version,
    definition: working ? assignment?.definition || shift.definition : null,
    requiredMinutes: working ? (assignment?.requiredMinutes ?? shift.requiredMinutes) : 0,
  }
}
function attendanceRow(state, employee, date) {
  const schedule = plan(state, employee.id, date),
    working = !!schedule.definition,
    past = date < today()
  const key = `${employee.id}:${date}`,
    saved = state.attendance[key]
  const leaves = state.leave.filter(
    (r) => r.employeeId === employee.id && r.startDate <= date && r.endDate >= date,
  )
  const approved = working ? leaves.filter((r) => r.status === 'APPROVED') : []
  const annual = approved
    .filter((r) => r.leaveType === 'ANNUAL')
    .reduce((n, r) => n + (r.period === 'FULL_DAY' ? 480 : 240), 0)
  const unpaid = approved
    .filter((r) => r.leaveType !== 'ANNUAL')
    .reduce((n, r) => n + (r.period === 'FULL_DAY' ? 480 : 240), 0)
  const minutes = Math.max(0, schedule.requiredMinutes - annual - unpaid)
  const late =
    past &&
    working &&
    Number(employee.employeeCode.slice(2)) % 5 === 0 &&
    Number(date.slice(-2)) % 4 === 0
      ? 15
      : 0
  const row = {
    employeeId: employee.id,
    employeeCode: employee.employeeCode,
    employeeName: `${employee.lastName} ${employee.firstName}`,
    departmentId: employee.department?.id,
    workDate: date,
    shiftId: schedule.shiftId,
    shiftVersion: schedule.shiftVersion,
    recordVersion: 0,
    checkIn: past && working && minutes ? `${date}T01:${late ? '15' : '00'}:00Z` : null,
    checkOut: past && working && minutes ? `${date}T10:30:00Z` : null,
    remainingRequiredMinutes: minutes,
    workMinutesCounted: past && working ? Math.max(0, minutes - late) : null,
    workedActualSeconds: past && working ? Math.max(0, minutes - late) * 60 : null,
    annualLeaveMinutes: annual,
    unpaidLeaveMinutes: unpaid,
    leaveDays: (annual + unpaid) / 480,
    roundedLateMinutes: past ? late : null,
    roundedEarlyMinutes: past ? 0 : null,
    pendingLeaveIds: leaves.filter((r) => r.status === 'PENDING').map((r) => r.id),
    status: !working ? 'NO_SCHEDULE' : !minutes ? 'ON_LEAVE' : past ? 'CLOSED' : 'NOT_STARTED',
    reportState: state.periods[date.slice(0, 7)]?.closed ? 'FINAL' : 'DRAFT',
    overtime: { approvedMinutes: 0, countedMinutes: 0, pendingIds: [] },
    ...saved,
  }
  // Coverage always follows approved requests, including after a saved punch/correction.
  Object.assign(row, {
    annualLeaveMinutes: annual,
    unpaidLeaveMinutes: unpaid,
    leaveDays: (annual + unpaid) / 480,
    remainingRequiredMinutes: minutes,
  })
  const ots = state.overtime.filter((r) => r.employeeId === employee.id && r.workDate === date)
  row.overtime = {
    approvedMinutes: ots
      .filter((r) => r.status === 'APPROVED')
      .reduce((n, r) => n + r.approvedMinutes, 0),
    countedMinutes: ots.reduce((n, r) => n + (r.countedMinutes || 0), 0),
    pendingIds: ots.filter((r) => r.status === 'PENDING').map((r) => r.id),
  }
  return row
}
function report(state, query, user, mine = false) {
  const from = query.get('from') || `${today().slice(0, 7)}-01`,
    until = query.get('until') || today()
  const people = state.employees.filter(
    (e) =>
      (!mine || e.id === user.employeeId) &&
      (!query.get('employeeId') || e.id === query.get('employeeId')) &&
      (!query.get('departmentId') || e.department?.id === query.get('departmentId')),
  )
  return people.flatMap((e) => dates(from, until).map((date) => attendanceRow(state, e, date)))
}
function route(state, path, query, init, body) {
  const method = init.method || 'GET',
    writing = method !== 'GET'
  const parts = path.split('/').filter(Boolean).slice(2)
  const [domain, id, action, child] = parts
  let user = state.accounts.find((a) => a.id === state.currentAccountId)
  const manage = () => {
    if (!user?.roles.some((r) => ['ADMIN', 'HR'].includes(r))) fail(403, 'Không có quyền quản lý')
  }
  const admin = () => {
    if (!user?.roles.includes('ADMIN')) fail(403, 'Chỉ dành cho quản trị viên')
  }
  if (domain === 'auth') {
    if (id === 'csrf')
      return { token: 'offline-demo', headerName: 'X-XSRF-TOKEN', sessionPresent: !!user }
    if (id === 'login') {
      user = state.accounts.find((a) => a.email.toLowerCase() === body.email?.toLowerCase())
      if (!user || body.password !== demoPassword || user.status !== 'ACTIVE')
        fail(401, 'Thông tin đăng nhập demo không hợp lệ')
      state.currentAccountId = user.id
      user.lastLoginAt = now()
      return { data: { accessToken: 'offline-demo-token' } }
    }
    if (id === 'logout' || id === 'logout-all') {
      state.currentAccountId = null
      return null
    }
  }
  if (!user || user.status !== 'ACTIVE') fail(401, 'Vui lòng đăng nhập demo')
  if (id === 'health-check') return { status: 'UP', database: 'UP', demo: true }
  if (domain === 'auth') {
    if (id === 'refresh') return { accessToken: 'offline-demo-token' }
    if (id === 'me') return { ...user, active: true }
    if (id === 'session') return null
    if (id === 'register') {
      admin()
      const e = find(state.employees, body.employeeId)
      if (state.accounts.some((a) => a.email === body.email || a.employeeId === body.employeeId))
        fail(409, 'Tài khoản đã tồn tại')
      const account = {
        id: Math.max(...state.accounts.map((a) => a.id)) + 1,
        employeeId: e.id,
        email: body.email,
        roles: body.roles,
        status: 'ACTIVE',
        active: true,
        version: 0,
        activationPending: false,
        createdAt: now(),
      }
      state.accounts.push(account)
      e.accountStatus = 'ACTIVE'
      return account
    }
    if (id === 'activation-invitations') {
      manage()
      const e = find(state.employees, action)
      return {
        employeeId: e.id,
        invitationId: e.id,
        status: 'PENDING',
        deliveryStatus: 'SENT',
        email: e.personalEmail,
        expiresAt: `${today()}T23:59:59Z`,
        createdAt: now(),
        resendAllowed: true,
      }
    }
    if (id === 'accounts') {
      admin()
      if (!action) return page(filtered(state.accounts, query), query)
      const account = find(state.accounts, action),
        key = `accounts:${action}`
      if (child === 'audit') return page(state.histories[key] || [], query)
      if (child === 'actions') {
        if (account.id === user.id) fail(409, 'Không thể tự thay đổi tài khoản')
        if (body.version !== account.version) fail(412, 'ACCOUNT_VERSION_CONFLICT')
        const before = clone(account)
        if (body.action === 'DISABLE') account.status = 'DISABLED'
        if (body.action === 'ENABLE') account.status = 'ACTIVE'
        if (body.action === 'CHANGE_ROLES') account.roles = body.roles
        account.version++
        history(state, key, body.action, user, {
          reason: body.reason,
          beforeStatus: before.status,
          afterStatus: account.status,
          beforeRoles: before.roles.join(','),
          afterRoles: account.roles.join(','),
        })
      }
      return account
    }
  }
  if (['employees', 'departments', 'positions'].includes(domain)) {
    if (!(domain === 'employees' && id === user.employeeId && !writing)) manage()
    const rows = state[domain]
    if (action === 'account-requests') {
      admin()
      const e = find(rows, id)
      if (writing) {
        e.accountStatus = 'PENDING_ACTIVATION'
        const accountId = Math.max(...state.accounts.map((a) => a.id)) + 1
        if (!state.accounts.some((a) => a.employeeId === id))
          state.accounts.push({
            id: accountId,
            employeeId: id,
            email: body.email,
            roles: ['EMPLOYEE'],
            status: 'PENDING_ACTIVATION',
            activationPending: true,
            version: 0,
          })
        const request = {
          requestId: crypto.randomUUID(),
          employeeId: id,
          provisioningStatus: 'SUCCEEDED',
          accountStatus: 'PENDING_ACTIVATION',
          createdAt: now(),
        }
        state.provisioning[request.requestId] = request
        return request
      }
      return state.provisioning[child] || fail(404, 'Không tìm thấy yêu cầu')
    }
    if (!writing) return id ? find(rows, id) : page(rows, query)
    const row = id
      ? find(rows, id)
      : {
          id: crypto.randomUUID(),
          createdAt: now(),
          ...(domain === 'employees'
            ? {
                employeeCode: `NV${String(rows.length + 1).padStart(3, '0')}`,
                status: 'PROBATION',
                accountStatus: 'NOT_CREATED',
              }
            : {}),
        }
    Object.assign(row, body, { updatedAt: now() })
    if (domain === 'employees' && action !== 'status') {
      for (const [key, resource] of [
        ['department', 'departments'],
        ['position', 'positions'],
        ['manager', 'employees'],
      ])
        row[key] = body[`${key}Id`] ? clone(find(state[resource], body[`${key}Id`])) : null
    }
    if (!id) rows.unshift(row)
    return row
  }
  if (domain === 'calendar') {
    const year = Number(query.get('year')) || Number(today().slice(0, 4))
    return {
      year,
      availableYears: [year - 1, year, year + 1],
      events: state.events.filter((event) => {
        if (event.status !== 'PUBLISHED') return false
        const { startDay, endDay } = eventDateRange(event)
        return startDay <= `${year}-12-31` && endDay >= `${year}-01-01`
      }),
    }
  }
  if (domain === 'calendar-events') {
    manage()
    if (!writing)
      return id
        ? find(state.events, id)
        : page(
            filtered(state.events, query).filter(
              (e) =>
                (!query.get('from') || (e.endDate || e.endAt) >= query.get('from')) &&
                (!query.get('to') || (e.startDate || e.startAt) <= `${query.get('to')}T23:59:59`),
            ),
            query,
          )
    const event = id
      ? find(state.events, id)
      : {
          id: Math.max(0, ...state.events.map((e) => e.id)) + 1,
          version: 0,
          status: 'DRAFT',
          createdAt: now(),
        }
    if (id) checkVersion(event, init)
    if (method === 'DELETE') {
      state.events = state.events.filter((e) => e.id !== event.id)
      return null
    }
    Object.assign(event, body, { updatedAt: now(), version: event.version + 1 })
    if (action === 'publish') event.status = 'PUBLISHED'
    if (action === 'cancel') event.status = 'CANCELLED'
    if (!id) state.events.push(event)
    return event
  }
  if (domain === 'leave' && id === 'requests')
    return leaveRoute(state, parts.slice(2), query, init, body, user, manage)
  if (domain === 'attendance')
    return attendanceRoute(state, parts.slice(1), query, init, body, user, manage)
  fail(501, `Demo chưa hỗ trợ: ${method} ${path}`)
}
function leaveRoute(state, parts, query, init, body, user, manage) {
  const [id, action, adjustmentId, operation] = parts,
    method = init.method || 'GET',
    writing = method !== 'GET'
  const own = state.leave.filter((r) => r.employeeId === user.employeeId)
  if (id === 'estimate') {
    const totalUnits = leaveUnits(query.get('from'), query.get('until'), query.get('period'))
    return { totalUnits, totalDays: totalUnits / 2, days: [] }
  }
  if (id === 'balance') {
    const year = Number(query.get('year')),
      usedDays = own
        .filter(
          (r) =>
            r.leaveType === 'ANNUAL' &&
            r.status === 'APPROVED' &&
            r.startDate.startsWith(String(year)),
        )
        .reduce((n, r) => n + r.totalUnits / 2, 0)
    return { year, entitledDays: 12, usedDays, remainingDays: 12 - usedDays }
  }
  if (id === 'pending-count') {
    manage()
    return {
      count: state.leave.filter((r) => r.status === 'PENDING' || r.adjustmentPending).length,
    }
  }
  if (id === 'calendar') return own.filter((r) => r.startDate.startsWith(query.get('year')))
  if (['mine', 'inbox', 'adjustments'].includes(id)) {
    if (id !== 'mine') manage()
    return page(
      filtered(
        id === 'mine'
          ? own
          : id === 'adjustments'
            ? state.leave.filter((r) => r.adjustmentPending)
            : state.leave,
        query,
      ),
      query,
    )
  }
  const row = id
    ? find(state.leave, id)
    : {
        id: crypto.randomUUID(),
        ...employeeFields(state, user),
        createdAt: now(),
        status: 'PENDING',
        version: 0,
        adjustmentPending: false,
      }
  if (row.employeeId !== user.employeeId) manage()
  if (action === 'history')
    return (
      state.histories[`leave:${id}`] || [
        {
          id: row.id,
          action: 'SUBMITTED',
          actorUserId: row.requesterUserId,
          occurredAt: row.createdAt,
        },
      ]
    )
  if (action === 'adjustments') {
    if (!writing) return state.adjustments.filter((r) => r.requestId === id)
    checkVersion(row, init)
    if (!adjustmentId) {
      if (row.employeeId !== user.employeeId || row.status !== 'APPROVED' || row.adjustmentPending)
        fail(409, 'Không thể điều chỉnh đơn này')
      const totalUnits = body.cancelAll ? 0 : leaveUnits(body.startDate, body.endDate, body.period)
      if (
        !body.cancelAll &&
        (body.startDate < row.startDate ||
          body.endDate > row.endDate ||
          totalUnits >= row.totalUnits ||
          totalUnits <= 0)
      )
        fail(400, 'Khoảng nghỉ còn lại không hợp lệ')
      state.adjustments.unshift({
        id: crypto.randomUUID(),
        requestId: id,
        ...body,
        originalStart: row.startDate,
        originalEnd: row.endDate,
        originalPeriod: row.period,
        originalUnits: row.totalUnits,
        totalUnits,
        status: 'PENDING',
        createdAt: now(),
      })
      row.adjustmentPending = true
    } else {
      const adjustment = find(state.adjustments, adjustmentId)
      if (adjustment.requestId !== id || adjustment.status !== 'PENDING')
        fail(409, 'Yêu cầu không còn chờ duyệt')
      if (operation !== 'cancel') {
        manage()
        if (row.employeeId === user.employeeId) fail(403, 'Không thể tự duyệt đơn')
      } else if (row.employeeId !== user.employeeId) fail(403, 'Chỉ người gửi được rút đơn')
      adjustment.status =
        { approve: 'APPROVED', reject: 'REJECTED', cancel: 'CANCELLED' }[operation] ||
        fail(400, 'Thao tác không hợp lệ')
      adjustment.reviewNote = body.note
      if (operation === 'approve') {
        if (adjustment.cancelAll) row.status = 'CANCELLED'
        else
          Object.assign(row, {
            startDate: adjustment.startDate,
            endDate: adjustment.endDate,
            period: adjustment.period,
            totalUnits: adjustment.totalUnits,
          })
      }
      row.adjustmentPending = false
    }
    row.version++
    history(state, `leave:${id}`, 'ADJUSTMENT_' + (operation || 'SUBMITTED').toUpperCase(), user)
    return row
  }
  if (!writing) return row
  if (!id) {
    Object.assign(row, body, { totalUnits: leaveUnits(body.startDate, body.endDate, body.period) })
    if (row.totalUnits <= 0) fail(400, 'Vui lòng chọn ngày làm việc')
    if (
      own.some(
        (r) =>
          ['PENDING', 'APPROVED'].includes(r.status) &&
          r.startDate <= row.endDate &&
          r.endDate >= row.startDate &&
          (r.period === 'FULL_DAY' || row.period === 'FULL_DAY' || r.period === row.period),
      )
    )
      fail(409, 'Khoảng nghỉ trùng đơn đã có')
    state.leave.unshift(row)
  } else {
    checkVersion(row, init)
    if (row.status !== 'PENDING') fail(409, 'Đơn không còn chờ duyệt')
    if (action !== 'cancel') {
      manage()
      if (row.employeeId === user.employeeId) fail(403, 'Không thể tự duyệt đơn')
    } else if (row.employeeId !== user.employeeId) fail(403, 'Chỉ người gửi được rút đơn')
    row.status =
      { approve: 'APPROVED', reject: 'REJECTED', cancel: 'CANCELLED' }[action] ||
      fail(400, 'Thao tác không hợp lệ')
    row.reviewNote = body.note
    row.reviewedBy = user.id
    row.reviewedAt = now()
    row.version++
  }
  history(state, `leave:${row.id}`, id ? row.status : 'SUBMITTED', user, { note: body.note })
  return row
}
function attendanceRoute(state, parts, query, init, body, user, manage) {
  const [resource, id, action] = parts,
    method = init.method || 'GET',
    writing = method !== 'GET'
  if (resource === 'mine') return report(state, query, user, true)
  if (resource === 'reports') {
    manage()
    const rows = report(state, query, user)
    if (id === 'export.csv') {
      const cell = (value) =>
        `"${String(value ?? '')
          .replace(/^[=+@-]/, "'$&")
          .replaceAll('"', '""')}"`
      const csv = [
        ['employee_code', 'employee_name', 'date', 'status', 'work_minutes'],
        ...rows.map((r) => [
          r.employeeCode,
          r.employeeName,
          r.workDate,
          r.status,
          r.workMinutesCounted,
        ]),
      ]
        .map((r) => r.map(cell).join(','))
        .join('\r\n')
      return new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8' })
    }
    return rows
  }
  if (resource === 'check-in' || resource === 'check-out') {
    const employee = find(state.employees, user.employeeId),
      date = today(),
      row = attendanceRow(state, employee, date)
    if (state.periods[date.slice(0, 7)]?.closed) fail(409, 'Kỳ công đã khóa')
    if (!row.shiftId) fail(409, 'Ngày này chưa có ca làm việc')
    if (resource === 'check-in') {
      if (row.checkIn) fail(409, 'Đã ghi giờ vào')
      row.checkIn = now()
      row.status = 'OPEN'
    } else {
      if (!row.checkIn || row.checkOut) fail(409, 'Cần ghi giờ vào trước giờ ra')
      row.checkOut = now()
      row.status = 'CLOSED'
      row.workedActualSeconds = Math.max(
        0,
        Math.floor((Date.parse(row.checkOut) - Date.parse(row.checkIn)) / 1000),
      )
      row.workMinutesCounted = Math.min(
        row.remainingRequiredMinutes,
        Math.floor(row.workedActualSeconds / 60),
      )
    }
    row.recordVersion++
    state.attendance[`${employee.id}:${date}`] = row
    return row
  }
  if (resource === 'employees' && parts[2] === 'days' && parts[4] === 'refresh-coverage') {
    manage()
    return attendanceRow(state, find(state.employees, id), parts[3])
  }
  if (resource === 'periods') {
    manage()
    const period = (state.periods[id] ||= { month: id, version: 0, closed: false })
    if (action === 'history') return state.histories[`period:${id}`] || []
    if (action === 'check-sources') return { changed: false }
    if (writing) {
      checkVersion(period, init)
      period.closed = action === 'close'
      period.version++
      history(state, `period:${id}`, period.closed ? 'CLOSED' : 'REOPENED', user, {
        reason: body.reason,
        version: period.version,
      })
    }
    return period
  }
  if (resource === 'shifts') {
    manage()
    if (action === 'history') return state.histories[`shifts:${id}`] || []
    if (!writing) return id ? find(state.shifts, id) : page(state.shifts, query)
    const shift = id
      ? find(state.shifts, id)
      : { id: crypto.randomUUID(), version: 0, active: true }
    if (id) checkVersion(shift, init)
    if (method === 'DELETE') shift.active = false
    else {
      shift.definition = body
      const minute = (v) => Number(v.slice(0, 2)) * 60 + Number(v.slice(3, 5))
      shift.requiredMinutes = body.intervals.reduce(
        (n, i) => n + ((minute(i.end) - minute(i.start) + 1440) % 1440),
        0,
      )
    }
    shift.version++
    if (!id) state.shifts.push(shift)
    history(
      state,
      `shifts:${shift.id}`,
      method === 'DELETE' ? 'ARCHIVED' : id ? 'UPDATED' : 'CREATED',
      user,
      { definition: shift.definition, version: shift.version },
    )
    return shift
  }
  if (resource === 'schedules') {
    if (id === 'mine' || id === 'employees') {
      if (id !== 'mine') manage()
      return dates(query.get('from'), query.get('until')).map((date) =>
        plan(state, id === 'mine' ? user.employeeId : action, date),
      )
    }
    manage()
    if (id === 'history') return page(state.histories.schedules || [], query)
    const shift = find(state.shifts, body.shiftId)
    if (id === 'preview')
      return {
        scheduleRevision: state.scheduleRevision,
        shift,
        affectedOverrides: [],
        recordedDayConflicts: 0,
        approvedLeaveConflicts: 0,
      }
    if (id === 'apply') {
      checkVersion({ version: state.scheduleRevision }, init)
      state.scheduleRevision++
      state.schedules.push({
        ...body,
        shiftVersion: shift.version,
        definition: clone(shift.definition),
        requiredMinutes: shift.requiredMinutes,
      })
      const batchId = crypto.randomUUID()
      history(state, 'schedules', 'APPLIED', user, {
        batchId,
        requestJson: JSON.stringify(body),
        scope: body.scope,
        reason: body.reason,
        scheduleRevision: state.scheduleRevision,
      })
      return { batchId, scheduleRevision: state.scheduleRevision }
    }
  }
  const mapping = { requests: 'permissions', corrections: 'corrections', overtime: 'overtime' }
  if (mapping[resource]) {
    const rows = state[mapping[resource]],
      key = `${resource}:${id}`
    if (id === 'pending-count') {
      manage()
      return { count: rows.filter((r) => r.status === 'PENDING').length }
    }
    if (!writing && (!id || id === 'inbox')) {
      if (id === 'inbox') manage()
      return page(
        filtered(
          id === 'inbox' ? rows : rows.filter((r) => r.employeeId === user.employeeId),
          query,
        ),
        query,
      )
    }
    const row = id
      ? find(rows, id)
      : {
          id: crypto.randomUUID(),
          ...employeeFields(state, user),
          createdAt: now(),
          version: 0,
          status: 'PENDING',
        }
    if (row.employeeId !== user.employeeId) manage()
    if (action === 'history') {
      const entries = state.histories[key] || []
      return resource === 'corrections' ? page(entries, query) : entries
    }
    if (!writing) return row
    if (id) checkVersion(row, init)
    if (action === 'decision') {
      manage()
      if (row.employeeId === user.employeeId) fail(403, 'Không thể tự duyệt đơn')
      if (row.status !== 'PENDING' || !['APPROVED', 'REJECTED'].includes(body.status))
        fail(409, 'Đơn không còn chờ duyệt')
      row.status = body.status
      row.reviewNote = body.reviewNote
      row.reviewedAt = now()
      if (resource === 'corrections' && row.status === 'APPROVED') {
        const work = attendanceRow(state, find(state.employees, row.employeeId), row.workDate)
        const seconds = Math.max(
          0,
          (Date.parse(row.proposedCheckOut) - Date.parse(row.proposedCheckIn)) / 1000,
        )
        Object.assign(work, {
          checkIn: row.proposedCheckIn,
          checkOut: row.proposedCheckOut,
          status: 'CLOSED',
          workedActualSeconds: seconds,
          workMinutesCounted: Math.min(work.remainingRequiredMinutes, seconds / 60),
          recordVersion: work.recordVersion + 1,
        })
        state.attendance[`${row.employeeId}:${row.workDate}`] = work
      }
    } else if (action === 'cancel') {
      if (row.employeeId !== user.employeeId || row.status !== 'PENDING')
        fail(409, 'Không thể rút đơn này')
      row.status = 'CANCELLED'
    } else if (action === 'check-in' || action === 'check-out') {
      if (row.employeeId !== user.employeeId || row.status !== 'APPROVED')
        fail(403, 'Chỉ chấm OT của mình đã được duyệt')
      if (action === 'check-in') {
        if (row.checkIn) fail(409, 'Đã ghi giờ vào')
        row.checkIn = now()
      } else {
        if (!row.checkIn || row.checkOut) fail(409, 'Cần ghi giờ vào trước giờ ra')
        row.checkOut = now()
        row.countedMinutes = Math.min(
          row.approvedMinutes,
          Math.max(0, Math.floor((Date.parse(row.checkOut) - Date.parse(row.checkIn)) / 60000)),
        )
      }
    } else {
      if (id && (row.employeeId !== user.employeeId || row.status !== 'PENDING'))
        fail(409, 'Không thể sửa đơn này')
      Object.assign(row, body)
      if (resource === 'overtime') {
        row.workDate = body.start.slice(0, 10)
        row.approvedStart = new Date(`${body.start}+07:00`).toISOString()
        row.approvedEnd = new Date(`${body.end}+07:00`).toISOString()
        row.approvedMinutes = Math.floor(
          (Date.parse(row.approvedEnd) - Date.parse(row.approvedStart)) / 60000,
        )
        row.countedMinutes = 0
      }
      if (resource === 'requests') {
        const schedule = plan(state, user.employeeId, row.workDate),
          interval = schedule.definition?.intervals.find((i) => i.period === row.period)
        if (!interval) fail(400, 'Không có ca phù hợp')
        const minute = (v) => Number(v.slice(0, 2)) * 60 + Number(v.slice(3, 5))
        row.requestedMinutes =
          row.requestType === 'LATE_ARRIVAL'
            ? minute(row.expectedTime) - minute(interval.start)
            : minute(interval.end) - minute(row.expectedTime)
      }
      if (!id) rows.unshift(row)
    }
    row.version++
    history(
      state,
      `${resource}:${row.id}`,
      action ? row.status : id ? 'UPDATED' : 'SUBMITTED',
      user,
      { reason: row.reason, reviewNote: body.reviewNote },
    )
    return row
  }
  fail(501, `Demo chưa hỗ trợ chấm công: ${parts.join('/')}`)
}

// The adapter is imported lazily only in the demo build. No network fallback is allowed.
export async function demoRequest(path, init = {}) {
  if (init.signal?.aborted) throw init.signal.reason
  const url = new URL(path, 'http://demo.local'),
    state = readDemo()
  if (url.pathname === '/api/v1/notifications/stream') {
    return new ReadableStream({
      start(controller) {
        const emit = () =>
          controller.enqueue(
            new TextEncoder().encode(
              `event: notifications\ndata: ${JSON.stringify({ revision: Date.now() })}\n\n`,
            ),
          )
        emit()
        window.addEventListener('hrm-demo-change', emit)
        const stop = () => {
          window.removeEventListener('hrm-demo-change', emit)
          controller.close()
        }
        init.signal?.addEventListener('abort', stop, { once: true })
        this.cleanup = () => {
          window.removeEventListener('hrm-demo-change', emit)
          init.signal?.removeEventListener('abort', stop)
        }
      },
      cancel() {
        this.cleanup?.()
      },
    })
  }
  const body = init.body ? JSON.parse(init.body) : {}
  const key = new Headers(init.headers).get('Idempotency-Key')
  const cacheKey = key && `${state.currentAccountId}:${path}:${key}`
  if (cacheKey && state.idempotency[cacheKey]) return clone(state.idempotency[cacheKey])
  const result = route(state, url.pathname, url.searchParams, init, body)
  if (cacheKey) state.idempotency[cacheKey] = result
  // Persist before resolving so subsequent requests and page reloads see the mutation.
  try {
    localStorage.setItem(demoStorageKey, JSON.stringify(state))
  } catch {
    fail(507, 'Không thể lưu demo. Hãy cho phép bộ nhớ trình duyệt hoặc khôi phục dữ liệu mẫu.')
  }
  if (init.method && init.method !== 'GET') window.dispatchEvent(new Event('hrm-demo-change'))
  return result instanceof Blob || result == null ? result : clone(result)
}
