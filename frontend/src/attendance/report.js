// Summarize the server's calculated values; never infer an absence or an eight-hour day.
export function reviewReasons(row) {
  const reasons = []
  if (['NO_RECORD', 'MISSING_CHECK_OUT', 'INVALID_RECORD'].includes(row.status))
    reasons.push('record')
  if (row.status === 'SOURCE_CONFLICT' || row.permissionCoverage?.conflictingRequestIds?.length)
    reasons.push('conflict')
  if (
    row.pendingLeaveIds?.length ||
    row.permissionCoverage?.pendingRequestIds?.length ||
    row.overtime?.pendingIds?.length
  )
    reasons.push('pending')
  return reasons
}

export function summarizeAttendance(rows) {
  const groups = new Map()
  for (const row of rows) {
    if (!groups.has(row.employeeId)) groups.set(row.employeeId, [])
    groups.get(row.employeeId).push(row)
  }
  return [...groups.entries()].map(([employeeId, days]) => {
    const latest = days.reduce((a, b) => (a.workDate > b.workDate ? a : b))
    const known = days.filter((day) => day.workMinutesCounted != null)
    const sum = (field) => days.reduce((total, day) => total + (day[field] ?? 0), 0)
    const issues = { record: 0, conflict: 0, pending: 0 }
    const pending = new Set()
    let reviewDays = 0
    for (const day of days) {
      const reasons = reviewReasons(day)
      if (reasons.length) reviewDays++
      for (const reason of reasons) issues[reason]++
      for (const [type, ids] of [
        ['leave', day.pendingLeaveIds],
        ['permission', day.permissionCoverage?.pendingRequestIds],
        ['overtime', day.overtime?.pendingIds],
      ])
        for (const id of ids ?? []) pending.add(`${type}:${id}`)
    }
    return {
      employeeId,
      employeeCode: latest.employeeCode,
      employeeName: latest.employeeName,
      days,
      workMinutes: known.length ? sum('workMinutesCounted') : null,
      incompleteDays: days.filter(
        (day) => day.workMinutesCounted == null && day.status !== 'NO_SCHEDULE',
      ).length,
      noScheduleDays: days.filter((day) => day.status === 'NO_SCHEDULE').length,
      ongoingDays: days.filter((day) => ['OPEN', 'NOT_STARTED'].includes(day.status)).length,
      annualMinutes: sum('annualLeaveMinutes'),
      unpaidMinutes: sum('unpaidLeaveMinutes'),
      lateMinutes: days.some((day) => day.roundedLateMinutes != null)
        ? sum('roundedLateMinutes')
        : null,
      earlyMinutes: days.some((day) => day.roundedEarlyMinutes != null)
        ? sum('roundedEarlyMinutes')
        : null,
      lateEarlyDays: days.filter((day) => day.roundedLateMinutes > 0 || day.roundedEarlyMinutes > 0)
        .length,
      otCountedMinutes: days.reduce((total, day) => total + (day.overtime?.countedMinutes ?? 0), 0),
      otApprovedMinutes: days.reduce(
        (total, day) => total + (day.overtime?.approvedMinutes ?? 0),
        0,
      ),
      reviewDays,
      issues,
      pendingRequests: pending.size,
    }
  })
}

export function attendanceOverview(employees) {
  const sum = (field) => employees.reduce((total, person) => total + (person[field] ?? 0), 0)
  return {
    employees: employees.length,
    reviewEmployees: employees.filter((person) => person.reviewDays > 0).length,
    reviewDays: sum('reviewDays'),
    pendingRequests: sum('pendingRequests'),
    workMinutes: employees.some((person) => person.workMinutes != null) ? sum('workMinutes') : null,
    incompleteDays: sum('incompleteDays'),
    noScheduleDays: sum('noScheduleDays'),
    otCountedMinutes: sum('otCountedMinutes'),
    otApprovedMinutes: sum('otApprovedMinutes'),
  }
}
