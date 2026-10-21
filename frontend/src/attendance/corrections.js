import { fieldLimits } from '../config/validation.js'
import { correctionRules as copy } from '../content/attendance.js'
import { today } from '../shared/date.js'

// datetime-local values always represent Vietnam time, regardless of browser timezone.
export function localCorrectionTime(value) {
  return value ? new Date(new Date(value).getTime() + 7 * 3600000).toISOString().slice(0, 19) : ''
}
export function correctionBody(form, plan, row, now = new Date()) {
  if (!form.workDate || form.workDate > today()) throw new Error(copy.invalidDate)
  if (!plan?.definition || plan.date !== form.workDate) throw new Error(copy.noSchedule)
  if (row && row.workDate !== form.workDate) throw new Error(copy.reloadDay)
  const parse = (value) => {
    if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(value)) throw new Error(copy.missingTimes)
    const time = new Date(`${value}+07:00`)
    if (!Number.isFinite(time.getTime())) throw new Error(copy.invalidTimes)
    return time
  }
  const start = parse(form.proposedCheckIn),
    end = parse(form.proposedCheckOut)
  if (end <= start || end > now) throw new Error(copy.invalidTimeOrder)
  if (!form.reason.trim() || form.reason.trim().length > fieldLimits.attendanceReason)
    throw new Error(copy.invalidReason)
  return {
    workDate: form.workDate,
    shiftId: plan.shiftId,
    shiftVersion: plan.shiftVersion,
    recordVersion: row?.recordVersion ?? null,
    proposedCheckIn: start.toISOString().replace('.000Z', 'Z'),
    proposedCheckOut: end.toISOString().replace('.000Z', 'Z'),
    reason: form.reason.trim(),
  }
}
