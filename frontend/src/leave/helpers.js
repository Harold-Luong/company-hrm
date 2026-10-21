import { dateLimits } from '../config/validation.js'
import { fieldLimits } from '../config/validation.js'
import { leaveRules as copy } from '../content/leave.js'
import { today } from '../shared/date.js'
import { leaveTypes, leavePeriods } from './constants.js'

export { today, dateLabel, dateTimeLabel as timeLabel } from '../shared/date.js'
export {
  leaveReviewRoles,
  leaveTypes,
  leaveStatuses,
  leavePeriods,
  historyActions,
} from './constants.js'
export { leaveError } from './errors.js'

export function submission(form) {
  const days =
    (Date.parse(`${form.endDate}T00:00:00Z`) - Date.parse(`${form.startDate}T00:00:00Z`)) / 86400000
  if (!Object.hasOwn(leaveTypes, form.leaveType)) throw new Error(copy.invalidType)
  if (!leavePeriods[form.period]) throw new Error(copy.invalidDuration)
  if (
    !Number.isFinite(days) ||
    days < 0 ||
    days >= dateLimits.maxRangeDays ||
    form.startDate < today()
  )
    throw new Error(copy.invalidDateRange)
  if (!form.reason.trim() || form.reason.trim().length > fieldLimits.leaveReason)
    throw new Error(copy.invalidReason)
  if (form.period !== 'FULL_DAY' && form.startDate !== form.endDate)
    throw new Error(copy.halfDayRange)
  if (form.leaveType === 'ANNUAL' && form.startDate.slice(0, 4) !== form.endDate.slice(0, 4))
    throw new Error(copy.crossYearAnnualLeave)
  return {
    leaveType: form.leaveType,
    startDate: form.startDate,
    endDate: form.endDate,
    period: form.period,
    reason: form.reason.trim(),
  }
}
