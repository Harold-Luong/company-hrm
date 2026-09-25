// Transcribed from the EVERRISE VIETNAM CO.,LTD calendar supplied by the user.
// This is a display-only company schedule, not a statutory holiday rules engine.
export const calendarYear = 2026
export const companyName = 'EVERRISE VIETNAM CO.,LTD'
export const weekdays = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']
export const eventTypes = {
  holiday: { label: 'Nghỉ lễ', short: 'Lễ', color: 'blue' },
  annual: { label: 'Nghỉ trừ phép năm', short: 'Phép', color: 'purple' },
  extra: { label: 'Nghỉ thêm dự kiến', short: 'Dự kiến', color: 'orange' },
  salary: { label: 'Ngày lương', short: 'Lương', color: 'red' },
  party: { label: 'Tiệc công ty', short: 'Tiệc', color: 'amber' },
  travel: { label: 'Du lịch công ty', short: 'Du lịch', color: 'green' },
}

export const holidayGroups = [
  {
    id: 'new-year', title: 'Tết Dương lịch', english: 'New Year’s Day', country: 'VN',
    start: '2026-01-01', end: '2026-01-02',
    notes: ['Ngày 02/01 nghỉ và trừ vào ngày phép năm.'],
    segments: [
      { start: '2026-01-01', end: '2026-01-01', type: 'holiday', label: 'Nghỉ Tết Dương lịch' },
      { start: '2026-01-02', end: '2026-01-02', type: 'annual', label: 'Nghỉ đầu năm · Trừ phép năm' },
    ],
  },
  {
    id: 'tet', title: 'Tết Nguyên đán', english: 'Lunar New Year', country: 'VN',
    start: '2026-02-13', end: '2026-02-24',
    notes: [
      '16–20/02: 5 ngày nghỉ Tết theo quy định ghi trong lịch.',
      '13/02 và 23–24/02: 3 ngày công ty cho nghỉ thêm, dự kiến.',
      'Ngày nghỉ du lịch chỉ mang tính chất tham khảo. Tùy vào tình hình thực tế sẽ quyết định sau.',
    ],
    segments: [
      { start: '2026-02-13', end: '2026-02-13', type: 'extra', label: 'Nghỉ thêm dịp Tết · Dự kiến' },
      { start: '2026-02-16', end: '2026-02-20', type: 'holiday', label: 'Nghỉ Tết Nguyên đán' },
      { start: '2026-02-23', end: '2026-02-24', type: 'extra', label: 'Nghỉ thêm dịp Tết · Dự kiến' },
    ],
  },
  {
    id: 'hung-kings', title: 'Giỗ Tổ Hùng Vương', english: 'Hung Kings Festival', country: 'VN',
    start: '2026-04-27', end: '2026-04-27',
    notes: ['Ngày 27/04 nghỉ bù cho ngày 26/04 (Chủ nhật).'],
    segments: [{ start: '2026-04-27', end: '2026-04-27', type: 'holiday', label: 'Giỗ Tổ Hùng Vương · Nghỉ bù' }],
  },
  {
    id: 'liberation-labour', title: 'Ngày Giải phóng & Quốc tế Lao động', english: 'Liberation Day & May Day', country: 'VN',
    start: '2026-04-30', end: '2026-05-01', notes: [],
    segments: [{ start: '2026-04-30', end: '2026-05-01', type: 'holiday', label: 'Nghỉ lễ 30/04 – 01/05' }],
  },
  {
    id: 'independence', title: 'Quốc khánh', english: 'Independence Day', country: 'VN',
    start: '2026-09-02', end: '2026-09-03', notes: [],
    segments: [{ start: '2026-09-02', end: '2026-09-03', type: 'holiday', label: 'Nghỉ Quốc khánh' }],
  },
  {
    id: 'sports', title: 'Ngày Thể thao', english: 'スポーツの日', country: 'JP',
    start: '2026-10-12', end: '2026-10-12', notes: [],
    segments: [{ start: '2026-10-12', end: '2026-10-12', type: 'holiday', label: 'Nhật Bản · Ngày Thể thao' }],
  },
  {
    id: 'culture', title: 'Ngày Văn hóa', english: '文化の日', country: 'JP',
    start: '2026-11-03', end: '2026-11-03', notes: [],
    segments: [{ start: '2026-11-03', end: '2026-11-03', type: 'holiday', label: 'Nhật Bản · Ngày Văn hóa' }],
  },
  {
    id: 'labour-thanksgiving', title: 'Ngày Cảm tạ Lao động', english: '勤労感謝の日', country: 'JP',
    start: '2026-11-23', end: '2026-11-23', notes: [],
    segments: [{ start: '2026-11-23', end: '2026-11-23', type: 'holiday', label: 'Nhật Bản · Ngày Cảm tạ Lao động' }],
  },
]

// Red dates in the supplied calendar. Keep exact dates; do not infer a payroll rule.
export const salaryDates = [
  '2026-01-09', '2026-02-10', '2026-03-10', '2026-04-10',
  '2026-05-08', '2026-06-10', '2026-07-10', '2026-08-10',
  '2026-09-10', '2026-10-09', '2026-11-10', '2026-12-10',
]

// The legend is readable, but event dates need confirmation from the source image.
export const pendingActivities = [
  { type: 'party', label: 'Official party · Tiệc công ty' },
  { type: 'travel', label: 'Company Travel · Du lịch công ty' },
]

export function formatCalendarDate(date) {
  return date.split('-').reverse().join('/')
}

export function formatPeriod(start, end) {
  return start === end ? formatCalendarDate(start) : `${formatCalendarDate(start)} – ${formatCalendarDate(end)}`
}

export function dayEvents(date) {
  const events = holidayGroups.flatMap(group => group.segments
    .filter(segment => date >= segment.start && date <= segment.end)
    .map(segment => ({ ...segment, country: group.country, groupId: group.id, notes: group.notes })))
  if (salaryDates.includes(date)) events.push({ type: 'salary', label: 'Ngày lương', notes: ['Ngày lương được đánh dấu trong lịch công ty.'] })
  return events
}

export function monthDays(month) {
  const offset = (new Date(Date.UTC(calendarYear, month - 1, 1)).getUTCDay() + 6) % 7
  const count = new Date(Date.UTC(calendarYear, month, 0)).getUTCDate()
  return Array.from({ length: 42 }, (_, index) => {
    const number = index - offset + 1
    if (number < 1 || number > count) return null
    const date = `${calendarYear}-${String(month).padStart(2, '0')}-${String(number).padStart(2, '0')}`
    return { number, date, weekday: index % 7, events: dayEvents(date) }
  })
}
