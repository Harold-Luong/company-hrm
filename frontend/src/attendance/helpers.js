// Compatibility exports; new code imports the owning module directly.
export { today, timeLabel } from '../shared/date.js'
export { minutesLabel, secondsLabel } from '../shared/duration.js'
export { attendanceRoles, statuses, weekdays } from './constants.js'
export { shiftBody, shiftLabel } from './shifts.js'
export { attendanceError } from './errors.js'
