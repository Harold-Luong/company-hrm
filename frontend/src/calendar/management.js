import { ApiError, serviceError } from '../auth/api.js'
import { CALENDAR_TIMEZONE } from './constants.js'
import { calendarDate, eventDateRange } from './utils.js'

const localTimeFormatter = new Intl.DateTimeFormat('sv-SE', {
  timeZone: CALENDAR_TIMEZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
})

export function localEventTime(instant) {
  if (!instant) return ''
  const date = new Date(instant)
  const parts = Object.fromEntries(
    localTimeFormatter.formatToParts(date).map(({ type, value }) => [type, value]),
  )
  const milliseconds = date.getUTCMilliseconds()
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}:${parts.second}${milliseconds ? `.${String(milliseconds).padStart(3, '0')}` : ''}`
}

export function newEventForm() {
  const today = calendarDate()
  return {
    title: '',
    description: '',
    type: 'HOLIDAY',
    holidayKind: 'PUBLIC_HOLIDAY',
    allDay: true,
    startDate: today,
    endDate: today,
    startTime: '',
    endTime: '',
    location: '',
    reason: '',
  }
}

export function eventToForm(event) {
  return {
    title: event.title,
    description: event.description || '',
    type: event.type,
    holidayKind: event.holidayKind || '',
    allDay: event.allDay,
    startDate: event.startDate || '',
    endDate: event.endDate || '',
    startTime: localEventTime(event.startAt),
    endTime: localEventTime(event.endAt),
    location: event.location || '',
    reason: '',
  }
}

export function eventPayload(form) {
  if (!form.title.trim()) throw new Error('Vui lòng nhập tiêu đề sự kiện.')
  if (form.type === 'HOLIDAY' && (!form.allDay || !form.holidayKind))
    throw new Error('Ngày nghỉ cần là sự kiện cả ngày và có loại ngày nghỉ.')
  const body = {
    title: form.title.trim(),
    description: form.description.trim() || null,
    type: form.type,
    holidayKind: form.type === 'HOLIDAY' ? form.holidayKind : null,
    allDay: form.allDay,
    startDate: null,
    endDate: null,
    startAt: null,
    endAt: null,
    timezone: CALENDAR_TIMEZONE,
    location: form.location.trim() || null,
    audienceType: 'ALL',
  }
  if (form.allDay) {
    if (!form.startDate || !form.endDate || form.endDate < form.startDate)
      throw new Error('Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.')
    body.startDate = form.startDate
    body.endDate = form.endDate
  } else {
    // The form always edits Vietnam time, independent of the browser's time zone.
    const start = Date.parse(`${form.startTime}+07:00`)
    const end = Date.parse(`${form.endTime}+07:00`)
    if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start)
      throw new Error('Giờ kết thúc phải sau giờ bắt đầu.')
    body.startAt = new Date(start).toISOString()
    body.endAt = new Date(end).toISOString()
  }
  if (form.reason.trim()) body.reason = form.reason.trim()
  return body
}

export function eventActions(event, now = Date.now()) {
  if (!event) return { edit: true, publish: false, cancel: false, remove: false }
  const future =
    Date.parse(event.allDay ? `${event.startDate}T00:00:00+07:00` : event.startAt) > now
  const draft = event.status === 'DRAFT'
  return {
    edit: draft || (event.status === 'PUBLISHED' && future),
    publish: draft && (event.allDay ? event.startDate >= calendarDate(new Date(now)) : future),
    cancel: event.status === 'PUBLISHED' && future,
    remove: draft,
  }
}

export function calendarManagementError(error) {
  if (!(error instanceof ApiError)) return error.message || 'Không thể xử lý yêu cầu.'
  if (error.status === 412)
    return 'Sự kiện đã được người khác thay đổi. Tải lại dữ liệu trước khi tiếp tục.'
  if (error.status === 428) return 'Thiếu thông tin phiên bản. Tải lại sự kiện trước khi tiếp tục.'
  if (error.status === 409)
    return 'Thao tác không phù hợp với trạng thái hoặc thời gian của sự kiện. Tải lại để kiểm tra.'
  if (error.status === 403) return 'Bạn không có quyền quản lý lịch. Chỉ HR và ADMIN được thao tác.'
  if (error.status === 404) return 'Sự kiện không còn tồn tại hoặc không thể truy cập.'
  return serviceError(error)
}

// Match the API ordering while keeping its paginated response unchanged.
export function sortManagedEvents(events, today = calendarDate()) {
  const todayTime = Date.parse(`${today}T00:00:00Z`)
  return events
    .map((event) => ({
      event,
      priority: event.status === 'DRAFT' ? 0 : 1,
      distance: Math.abs(Date.parse(`${eventDateRange(event).startDay}T00:00:00Z`) - todayTime),
      start: Date.parse(event.allDay ? `${event.startDate}T00:00:00+07:00` : event.startAt),
    }))
    .sort(
      (a, b) =>
        a.priority - b.priority ||
        a.distance - b.distance ||
        a.start - b.start ||
        a.event.id - b.event.id,
    )
    .map(({ event }) => event)
}
