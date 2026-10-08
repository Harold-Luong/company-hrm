import { serviceError } from '../auth/api.js'
export { today } from '../leave/helpers.js'
export const attendanceRoles = ['HR', 'ADMIN']
export const statuses = {
  NOT_STARTED: 'Chưa vào ca',
  OPEN: 'Đang làm việc',
  CLOSED: 'Đã kết thúc',
  NO_RECORD: 'Chưa có ghi nhận',
  MISSING_CHECK_OUT: 'Thiếu giờ ra',
  INVALID_RECORD: 'Giờ vào/ra chưa hợp lệ',
  SOURCE_CONFLICT: 'Cần đối soát phép và công',
  HOLIDAY: 'Ngày nghỉ chung',
  ON_LEAVE: 'Nghỉ phép',
  NO_SCHEDULE: 'Chưa có ca',
}
export const weekdays = [
  ['MONDAY', 'Thứ 2'],
  ['TUESDAY', 'Thứ 3'],
  ['WEDNESDAY', 'Thứ 4'],
  ['THURSDAY', 'Thứ 5'],
  ['FRIDAY', 'Thứ 6'],
  ['SATURDAY', 'Thứ 7'],
  ['SUNDAY', 'Chủ nhật'],
]
export const minutesLabel = (value) =>
  value == null ? 'Chưa xác định' : `${Math.floor(value / 60)}h ${value % 60}p`
export const secondsLabel = (value) =>
  value == null
    ? 'Chưa xác định'
    : `${minutesLabel(Math.floor(value / 60))}${value % 60 ? ` ${value % 60}s` : ''}`
export const timeLabel = (value) =>
  value
    ? new Intl.DateTimeFormat('vi-VN', {
        timeZone: 'Asia/Ho_Chi_Minh',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      }).format(new Date(value))
    : '—'
export const shiftLabel = (definition) =>
  definition?.intervals
    ?.map(
      (i) =>
        `${i.start.slice(0, 5)}–${i.end.slice(0, 5)}${definition.overnight ? ' (+1 ngày)' : ''}`,
    )
    .join(' · ') || 'Chưa có ca'
export function shiftBody(form) {
  const intervals = form.intervals.map((i) => ({
    period: i.period,
    start: i.start.slice(0, 5),
    end: i.end.slice(0, 5),
  }))
  if (!form.name.trim() || form.name.trim().length > 100)
    throw new Error('Nhập tên ca, tối đa 100 ký tự.')
  if (!intervals.length || intervals.length > 2)
    throw new Error('Ca cần một hoặc hai khoảng làm việc.')
  const periods = new Set()
  for (const [index, i] of intervals.entries()) {
    if (
      !['MORNING', 'AFTERNOON'].includes(i.period) ||
      periods.has(i.period) ||
      !/^([01]\d|2[0-3]):[0-5]\d$/.test(i.start) ||
      !/^([01]\d|2[0-3]):[0-5]\d$/.test(i.end) ||
      (!form.overnight && i.start >= i.end) ||
      (index > 0 && i.start < intervals[index - 1].end)
    )
      throw new Error('Các khoảng giờ phải đúng thứ tự, không trùng nhau và không trùng buổi.')
    periods.add(i.period)
  }
  if (
    form.overnight &&
    (intervals.length !== 1 ||
      intervals[0].end >= intervals[0].start ||
      form.checkInFrom <= intervals[0].end ||
      form.checkOutUntil >= form.checkInFrom)
  )
    throw new Error(
      'Ca qua đêm cần một khoảng giờ kết thúc vào ngày sau, cửa sổ chấm công dưới 24 giờ.',
    )
  if (
    !form.checkInFrom ||
    !form.checkOutUntil ||
    form.checkInFrom > intervals[0].start ||
    form.checkOutUntil < intervals.at(-1).end
  )
    throw new Error('Khoảng cho phép chấm công phải bao phủ toàn bộ ca.')
  return {
    name: form.name.trim(),
    mode: 'FIXED_SHIFT',
    timezone: 'Asia/Ho_Chi_Minh',
    intervals,
    overnight: !!form.overnight,
    checkInFrom: form.checkInFrom,
    checkOutUntil: form.checkOutUntil,
  }
}
export function attendanceError(error) {
  if (!(error?.status >= 0)) return error.message || 'Không thể xử lý yêu cầu.'
  if (error.status === 412 || error.status === 428)
    return 'Dữ liệu đã thay đổi hoặc thiếu phiên bản. Hãy tải lại và xem trước trước khi lưu.'
  if (error.status === 0)
    return 'Kết nối gián đoạn. Tải lại để kiểm tra kết quả; thử lại thao tác sẽ dùng cùng mã gửi.'
  if (error.status === 503)
    return 'Dịch vụ chấm công hoặc nguồn Nhân viên/Phép/Lịch chưa sẵn sàng. Hãy thử tải lại.'
  if (error.status === 422)
    return 'Phạm vi dữ liệu quá lớn. Hãy chọn ít nhân viên hoặc rút ngắn khoảng ngày.'
  const messages = {
    'Schedule overlaps an adjacent assigned shift':
      'Lịch mới chồng giờ với ca liền kề đang được phân công.',
    'Schedule affects approved OT; choose another date range':
      'Lịch mới ảnh hưởng OT đã duyệt. Hãy chọn phạm vi ngày khác.',
    'You cannot review your own OT request': 'Bạn không được tự duyệt OT của mình.',
    'OT must be in the future, at minute precision, and shorter than 24 hours':
      'OT cần được đăng ký và duyệt trước giờ bắt đầu, có thời lượng dưới 24 giờ.',
    'OT overlaps another pending or approved request':
      'Khoảng OT trùng với đơn đang chờ duyệt hoặc đã duyệt.',
    'OT must be outside regular working intervals':
      'Giờ OT phải nằm ngoài giờ làm việc thông thường.',
    'OT conflicts with leave': 'Giờ OT trùng với khoảng nghỉ phép.',
    'OT must be approved before recording time': 'OT cần được duyệt trước khi chấm giờ.',
    'Outside the approved OT interval': 'Chỉ được bắt đầu trong khoảng OT đã duyệt.',
    'Finish the previous overnight shift first':
      'Hãy kết thúc ca đêm đang mở trước khi vào ca mới.',

    'No assigned schedule for request date': 'Chưa được phân ca cho ngày xin phép. Liên hệ HR.',
    'An active request already exists for this date, period and type':
      'Đã có đơn chờ duyệt hoặc đã duyệt cùng ngày, buổi và loại yêu cầu.',
    'A leave request already covers this period':
      'Buổi này đã có đơn nghỉ phép chờ duyệt hoặc được duyệt.',
    'Cannot request time on a company holiday': 'Ngày nghỉ chung không cần đơn đi trễ/về sớm.',
    'Late and early requests cannot cover the entire work interval':
      'Hai đơn bao phủ cả buổi làm việc. Hãy dùng đơn nghỉ phép.',
    'Only pending requests can be reviewed': 'Đơn đã được xử lý. Hãy tải lại danh sách.',
    'Only pending requests can be edited or cancelled': 'Chỉ được sửa hoặc rút đơn đang chờ duyệt.',
    'You cannot review your own attendance request':
      'Bạn không được tự duyệt hoặc từ chối đơn của mình.',
    'Past requests cannot be edited': 'Không thể sửa đơn của ngày đã qua.',
    'A rejection reason is required': 'Nhập lý do từ chối đơn.',
    'Schedule affects approved attendance requests; resolve conflicts first':
      'Phân công ảnh hưởng đơn đi trễ/về sớm đã duyệt. Hãy chọn phạm vi khác hoặc đối soát trước.',

    'Check-in/out is only allowed from the company network':
      'Bạn cần kết nối mạng công ty để chấm công. Nếu đang ở công ty, liên hệ quản trị viên kiểm tra cấu hình mạng.',
    'A trusted single client IP is required':
      'Chưa xác minh được mạng truy cập. Vui lòng liên hệ quản trị viên.',
    'Outside the assigned recording window':
      'Hiện chưa nằm trong khoảng thời gian được phép chấm công của ca.',
    'No assigned schedule for today': 'Hôm nay chưa được phân ca. Vui lòng liên hệ HR.',
    'Employee is not eligible to record attendance':
      'Hồ sơ nhân viên hiện chưa đủ điều kiện chấm công.',
    'Attendance has already been recorded for this day':
      'Hôm nay đã có giờ vào. Hãy tải lại bảng công.',
    'Already checked out': 'Ca hôm nay đã kết thúc. Hãy tải lại bảng công.',
    'Check-in is required first': 'Bạn cần ghi nhận giờ vào trước khi ghi nhận giờ ra.',
    'Schedule affects recorded attendance or approved leave; resolve conflicts first':
      'Phân công ảnh hưởng ngày đã chấm công hoặc phép đã duyệt. Hãy chọn ngày hiệu lực khác hoặc xử lý đối soát trước.',
  }
  return messages[error.message] || serviceError(error)
}
