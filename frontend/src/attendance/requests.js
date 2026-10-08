import { today } from './helpers.js'

export const requestTypes = {
  LATE_ARRIVAL: 'Xin đi trễ',
  EARLY_DEPARTURE: 'Xin về sớm',
}
export const requestStatuses = {
  PENDING: 'Chờ duyệt',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Từ chối',
  CANCELLED: 'Đã rút',
}
const minuteOfDay = (time) => {
  const [hour, minute] = time.split(':').map(Number)
  return hour * 60 + minute
}
export function requestBody(form, shift) {
  if (!Object.hasOwn(requestTypes, form.requestType)) throw new Error('Chọn loại yêu cầu hợp lệ.')
  if (!/^\d{4}-\d{2}-\d{2}$/.test(form.workDate)) throw new Error('Chọn ngày làm việc hợp lệ.')
  const date = new Date(`${form.workDate}T00:00:00Z`)
  if (
    !Number.isFinite(date.getTime()) ||
    date.toISOString().slice(0, 10) !== form.workDate ||
    form.workDate < today()
  )
    throw new Error('Ngày xin phép phải từ hôm nay trở đi.')
  if (!shift?.definition || shift.date !== form.workDate)
    throw new Error('Chưa có ca thực tế cho ngày đã chọn. Hãy tải lại lịch.')
  const interval = shift.definition.intervals.find((value) => value.period === form.period)
  if (!interval) throw new Error('Chọn buổi làm việc cần xin phép.')
  const offset = (time) =>
    minuteOfDay(time) +
    (shift.definition.overnight && time.slice(0, 5) < interval.start.slice(0, 5) ? 1440 : 0)
  if (
    !/^([01]\d|2[0-3]):[0-5]\d$/.test(form.expectedTime) ||
    offset(form.expectedTime) <= offset(interval.start) ||
    offset(form.expectedTime) >= offset(interval.end)
  )
    throw new Error(
      `Giờ dự kiến phải sau ${interval.start} và trước ${interval.end}. Nếu nghỉ cả buổi, hãy tạo đơn nghỉ phép.`,
    )
  if (!form.reason.trim() || form.reason.trim().length > 1000)
    throw new Error('Nhập lý do xin phép, tối đa 1.000 ký tự.')
  return {
    workDate: form.workDate,
    shiftId: shift.shiftId,
    shiftVersion: shift.shiftVersion,
    requestType: form.requestType,
    period: form.period,
    expectedTime: form.expectedTime,
    requestedMinutes:
      form.requestType === 'LATE_ARRIVAL'
        ? offset(form.expectedTime) - offset(interval.start)
        : offset(interval.end) - offset(form.expectedTime),
    reason: form.reason.trim(),
  }
}
