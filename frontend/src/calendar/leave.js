import { calendarLeave as copy } from '../content/calendar.js'
import { leaveTypes, leavePeriods, leaveStatuses } from '../leave/constants.js'

export function personalLeaveEvents(requests) {
    return requests
        .filter((request) => ['PENDING', 'APPROVED'].includes(request.status))
        .map((request) => ({
            id: `leave:${request.id}`,
            requestId: request.id,
            type: 'PERSONAL_LEAVE',
            get title() {
                return `${leaveTypes[request.leaveType]} · ${leavePeriods[request.period]} · ${leaveStatuses[request.status]}`
            },
            allDay: true,
            startDate: request.startDate,
            endDate: request.endDate,
            status: request.status,
            period: request.period,
            get description() {
                return copy.personalLeaveDescription
            },
        }))
}
