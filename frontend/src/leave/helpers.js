import { ApiError, serviceError } from '../auth/api.js'

export const leaveReviewRoles = ['HR', 'ADMIN']
export const leaveTypes = {
    ANNUAL: 'Nghỉ phép năm',
    UNPAID: 'Nghỉ không lương',
}
export const leaveStatuses = {
    PENDING: 'Chờ duyệt',
    APPROVED: 'Đã duyệt',
    REJECTED: 'Từ chối',
    CANCELLED: 'Đã rút đơn',
}
export const leavePeriods = {
    FULL_DAY: 'Cả ngày',
    MORNING: 'Buổi sáng',
    AFTERNOON: 'Buổi chiều',
}
export const historyActions = {
    SUBMITTED: 'Gửi đơn',
    APPROVED: 'Duyệt đơn',
    REJECTED: 'Từ chối đơn',
    CANCELLED: 'Rút đơn',
}
export function today() {
    return new Intl.DateTimeFormat('en-CA', {
        timeZone: 'Asia/Ho_Chi_Minh',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
    }).format(new Date())
}
export function dateLabel(value) {
    if (!value) return '—'
    return value.split('-').reverse().join('/')
}
export function timeLabel(value) {
    return value ? new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' }) : '—'
}
export function submission(form) {
    const days =
        (Date.parse(`${form.endDate}T00:00:00Z`) - Date.parse(`${form.startDate}T00:00:00Z`)) / 86400000
    if (!Object.hasOwn(leaveTypes, form.leaveType)) throw new Error('Chọn loại nghỉ hợp lệ.')
    if (!leavePeriods[form.period]) throw new Error('Chọn thời lượng nghỉ hợp lệ.')
    if (!Number.isFinite(days) || days < 0 || days >= 366 || form.startDate < today())
        throw new Error(
            'Chọn ngày bắt đầu từ hôm nay, ngày kết thúc không trước ngày bắt đầu và tối đa 366 ngày.',
        )
    if (!form.reason.trim() || form.reason.trim().length > 2000)
        throw new Error('Nhập lý do nghỉ, tối đa 2.000 ký tự.')
    if (form.period !== 'FULL_DAY' && form.startDate !== form.endDate)
        throw new Error('Nghỉ buổi sáng hoặc buổi chiều chỉ áp dụng trong một ngày.')
    if (form.leaveType === 'ANNUAL' && form.startDate.slice(0, 4) !== form.endDate.slice(0, 4))
        throw new Error('Phép năm không được kéo dài qua hai năm. Hãy tách thành hai đơn.')
    return {
        leaveType: form.leaveType,
        startDate: form.startDate,
        endDate: form.endDate,
        period: form.period,
        reason: form.reason.trim(),
    }
}
export function leaveError(error) {
    if (!(error instanceof ApiError)) return 'Không thể xử lý yêu cầu. Vui lòng thử lại.'
    const messages = {
        'A pending or approved leave request overlaps these dates':
            'Bạn đã có đơn chờ duyệt hoặc đã duyệt trùng khoảng ngày này.',
        'You cannot review your own leave request':
            'Bạn không thể duyệt hoặc từ chối đơn của chính mình. Cần HR/ADMIN khác xử lý.',
        'A rejection reason is required': 'Vui lòng nhập lý do từ chối.',
        'Only pending requests can be processed': 'Đơn này đã được xử lý. Vui lòng tải lại.',
        'Dates must start today or later, in order, and span at most 366 calendar days':
            'Khoảng nghỉ phải bắt đầu từ hôm nay và không quá 366 ngày.',
    }
    if (messages[error.message]) return messages[error.message]
    if ([412, 428].includes(error.status))
        return 'Đơn đã thay đổi. Hãy tải lại và kiểm tra trước khi thao tác tiếp.'
    if (error.status === 0 || error.status >= 500)
        return 'Chưa xác nhận được kết quả từ dịch vụ nghỉ phép. Hãy tải lại danh sách trước khi gửi lại.'
    return serviceError(error)
}
