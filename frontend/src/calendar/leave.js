import { leaveTypes, leavePeriods, leaveStatuses } from '../leave/helpers.js'

export function personalLeaveEvents(requests) {
  return requests
    .filter((request) => ['PENDING', 'APPROVED'].includes(request.status))
    .map((request) => ({
      id: `leave:${request.id}`,
      requestId: request.id,
      type: 'PERSONAL_LEAVE',
      title: `${leaveTypes[request.leaveType]} · ${leavePeriods[request.period]} · ${leaveStatuses[request.status]}`,
      allDay: true,
      startDate: request.startDate,
      endDate: request.endDate,
      status: request.status,
      period: request.period,
      description: 'Đơn nghỉ phép cá nhân của bạn.',
    }))
}
