export const CALENDAR_TIMEZONE = 'Asia/Ho_Chi_Minh'

// Presentation labels for the calendar API enums; event data comes only from the API.
export const eventTypes = {
  HOLIDAY: 'Ngày nghỉ',
  COMPANY_MEAL: 'Ăn uống công ty',
  TEAM_BUILDING: 'Hoạt động gắn kết',
  TRAINING: 'Đào tạo',
  MEETING: 'Họp',
  COMPANY_EVENT: 'Sự kiện công ty',
  OTHER: 'Sự kiện khác',
}

export const holidayKinds = {
  PUBLIC_HOLIDAY: 'Nghỉ theo quy định',
  COMPANY_DAY_OFF: 'Công ty cho nghỉ',
  SUBSTITUTE_DAY_OFF: 'Nghỉ bù',
}

export const typeLabel = (type) => holidayKinds[type] || eventTypes[type] || type

export const calendarManagementRoles = ['HR', 'ADMIN']
export const eventStatuses = { DRAFT: 'Bản nháp', PUBLISHED: 'Đã công bố', CANCELLED: 'Đã hủy' }
