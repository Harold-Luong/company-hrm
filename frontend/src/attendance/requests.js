import { reactive } from 'vue'
import { today } from './helpers.js'

// UI proposal only: attendance-service has no request DTO or endpoint yet.
// Date, shift version and periods follow its existing schedule/shift responses.
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
export const previewShift = {
  shiftId: 'preview-office-shift',
  shiftVersion: 0,
  definition: {
    name: 'Ca hành chính (mẫu)',
    timezone: 'Asia/Ho_Chi_Minh',
    intervals: [
      { period: 'MORNING', start: '08:00', end: '12:00' },
      { period: 'AFTERNOON', start: '13:30', end: '17:30' },
    ],
  },
}
const minuteOfDay = (time) => {
  const [hour, minute] = time.split(':').map(Number)
  return hour * 60 + minute
}
export function requestBody(form, shift = previewShift) {
  if (!Object.hasOwn(requestTypes, form.requestType)) throw new Error('Chọn loại yêu cầu hợp lệ.')
  if (!/^\d{4}-\d{2}-\d{2}$/.test(form.workDate)) throw new Error('Chọn ngày làm việc hợp lệ.')
  const date = new Date(`${form.workDate}T00:00:00Z`)
  if (
    !Number.isFinite(date.getTime()) ||
    date.toISOString().slice(0, 10) !== form.workDate ||
    form.workDate < today()
  )
    throw new Error('Ngày xin phép phải từ hôm nay trở đi.')
  const interval = shift.definition.intervals.find((value) => value.period === form.period)
  if (!interval) throw new Error('Chọn buổi làm việc cần xin phép.')
  if (
    !/^([01]\d|2[0-3]):[0-5]\d$/.test(form.expectedTime) ||
    form.expectedTime <= interval.start ||
    form.expectedTime >= interval.end
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
        ? minuteOfDay(form.expectedTime) - minuteOfDay(interval.start)
        : minuteOfDay(interval.end) - minuteOfDay(form.expectedTime),
    reason: form.reason.trim(),
  }
}

const previews = new Map()
export function requestPreview(ownerId) {
  if (!previews.has(ownerId)) {
    const seed = [
      {
        requestType: 'LATE_ARRIVAL',
        period: 'MORNING',
        expectedTime: '08:30',
        reason: 'Đưa con đi khám theo lịch hẹn.',
        status: 'PENDING',
      },
      {
        requestType: 'EARLY_DEPARTURE',
        period: 'AFTERNOON',
        expectedTime: '16:30',
        reason: 'Có lịch hẹn giải quyết giấy tờ cá nhân.',
        status: 'APPROVED',
        reviewNote: 'Đã duyệt trong dữ liệu mẫu. Vui lòng bàn giao công việc trước khi về.',
      },
      {
        requestType: 'LATE_ARRIVAL',
        period: 'AFTERNOON',
        expectedTime: '14:00',
        reason: 'Có việc gia đình cần giải quyết.',
        status: 'REJECTED',
        reviewNote: 'Phản hồi mẫu: Cần sắp xếp lại thời gian để tham gia cuộc họp đầu giờ.',
      },
    ].map((row, index) => ({
      ...requestBody({
        ...row,
        workDate: new Date(Date.parse(`${today()}T00:00:00Z`) + (index + 1) * 86400000)
          .toISOString()
          .slice(0, 10),
      }),
      id: `sample-${index + 1}`,
      status: row.status,
      reviewNote: row.reviewNote || '',
      createdAt: new Date().toISOString(),
    }))
    previews.set(ownerId, reactive(seed))
  }
  return previews.get(ownerId)
}
