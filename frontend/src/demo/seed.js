import { today } from '../shared/date.js'
import { demoPersonas } from './config.js'

export const uuid = (group, index) =>
  `${group}0000000-0000-4000-8000-${String(index).padStart(12, '0')}`
export function dates(from, until) {
  if (!from || !until || from > until) return []
  const result = []
  for (
    const day = new Date(`${from}T00:00:00Z`);
    day.toISOString().slice(0, 10) <= until && result.length < 366;
    day.setUTCDate(day.getUTCDate() + 1)
  )
    result.push(day.toISOString().slice(0, 10))
  return result
}
export const weekday = (date) => ![0, 6].includes(new Date(`${date}T00:00:00Z`).getUTCDay())
export function dayOffset(date, offset) {
  const day = new Date(`${date}T00:00:00Z`)
  day.setUTCDate(day.getUTCDate() + offset)
  return day.toISOString().slice(0, 10)
}
export function leaveUnits(start, end, period = 'FULL_DAY') {
  return dates(start, end).filter(weekday).length * (period === 'FULL_DAY' ? 2 : 1)
}
export function createSeed() {
  const date = today(),
    year = Number(date.slice(0, 4)),
    month = date.slice(0, 7)
  const stamp = `${date}T01:00:00Z`
  const departments = [
    'Ban điều hành',
    'Nhân sự',
    'Công nghệ',
    'Kinh doanh',
    'Marketing',
    'Tài chính',
  ].map((name, i) => ({
    id: uuid(2, i + 1),
    code: ['BOD', 'HR', 'TECH', 'SALES', 'MKT', 'FIN'][i],
    name,
    description: `Phòng ${name}`,
    createdAt: stamp,
    updatedAt: stamp,
  }))
  const positions = [
    'Giám đốc',
    'Trưởng phòng',
    'Chuyên viên nhân sự',
    'Kỹ sư phần mềm',
    'Chuyên viên kinh doanh',
    'Chuyên viên marketing',
    'Kế toán',
  ].map((name, i) => ({
    id: uuid(3, i + 1),
    code: `POS${i + 1}`,
    name,
    description: name,
    createdAt: stamp,
    updatedAt: stamp,
  }))
  const names = [
    ['Trần Minh', 'Khang'],
    ['Nguyễn Thu', 'Hà'],
    ['Lê Hoàng', 'An'],
    ['Phạm Ngọc', 'Linh'],
    ['Đỗ Minh', 'Quân'],
    ['Vũ Thảo', 'Vy'],
    ['Bùi Quốc', 'Bảo'],
    ['Nguyễn Hải', 'Yến'],
    ['Trần Đức', 'Huy'],
    ['Lê Thanh', 'Trúc'],
    ['Phan Anh', 'Tuấn'],
    ['Hoàng Gia', 'Hân'],
    ['Đặng Minh', 'Châu'],
    ['Võ Nhật', 'Nam'],
    ['Ngô Bảo', 'Ngọc'],
    ['Dương Khánh', 'Duy'],
    ['Đinh Phương', 'Thảo'],
    ['Mai Quang', 'Vinh'],
    ['Lý Ngọc', 'Mai'],
    ['Trịnh Minh', 'Đức'],
    ['Hồ Thanh', 'Tâm'],
    ['Cao Tuấn', 'Kiệt'],
    ['Đoàn Mỹ', 'Dung'],
    ['Tạ Hoài', 'Phương'],
  ]
  const employees = names.map(([lastName, firstName], i) => ({
    id: uuid(1, i + 1),
    employeeCode: `NV${String(i + 1).padStart(3, '0')}`,
    lastName,
    firstName,
    personalEmail: demoPersonas[i]?.email || `nv${i + 1}@demo.example`,
    phone: `090${String(1000000 + i)}`,
    gender: i % 2 ? 'FEMALE' : 'MALE',
    dateOfBirth: `${1990 + (i % 9)}-05-15`,
    hireDate: `${year - 2}-03-01`,
    address: `${12 + i} Nguyễn Thị Minh Khai, TP. Hồ Chí Minh`,
    contactRelative: 'Người thân (mẫu)',
    contactRelativePhone: '0900000000',
    status: i > 20 ? 'PROBATION' : 'ACTIVE',
    accountStatus: i > 18 ? 'NOT_CREATED' : 'ACTIVE',
    department: departments[i < 3 ? i : i % departments.length],
    position: positions[i < 3 ? i : 3 + (i % 4)],
    manager: i
      ? { id: uuid(1, 1), employeeCode: 'NV001', firstName: 'Khang', lastName: 'Trần Minh' }
      : null,
    createdAt: stamp,
    updatedAt: stamp,
  }))
  const accounts = employees.slice(0, 19).map((e, i) => ({
    id: i + 1,
    employeeId: e.id,
    email: e.personalEmail,
    roles: [demoPersonas[i]?.role || 'EMPLOYEE'],
    status: 'ACTIVE',
    active: true,
    activationPending: false,
    version: 0,
    lastLoginAt: stamp,
    createdAt: stamp,
  }))
  const definition = {
    name: 'Ca hành chính',
    mode: 'FIXED_SHIFT',
    timezone: 'Asia/Ho_Chi_Minh',
    intervals: [
      { period: 'MORNING', start: '08:00', end: '12:00' },
      { period: 'AFTERNOON', start: '13:30', end: '17:30' },
    ],
    overnight: false,
    checkInFrom: '00:00',
    checkOutUntil: '23:59',
  }
  const shifts = [
    { id: uuid(4, 1), version: 0, active: true, definition, requiredMinutes: 480 },
    {
      id: uuid(4, 2),
      version: 0,
      active: true,
      definition: {
        ...definition,
        name: 'Ca sáng',
        intervals: [definition.intervals[0]],
        checkOutUntil: '14:00',
      },
      requiredMinutes: 240,
    },
  ]
  const requests = (group, extra) =>
    employees.slice(0, 9).map((e, i) => ({
      id: uuid(group, i + 1),
      employeeId: e.id,
      employeeName: `${e.lastName} ${e.firstName}`,
      employeeCode: e.employeeCode,
      requesterUserId: i + 1,
      status: ['PENDING', 'APPROVED', 'PENDING', 'REJECTED'][i % 4],
      version: 0,
      reason: ['Sắp xếp việc gia đình', 'Lịch hẹn cá nhân', 'Hoàn thành kế hoạch dự án'][i % 3],
      createdAt: stamp,
      updatedAt: stamp,
      ...extra(i),
    }))
  let future = dayOffset(date, 3)
  while (!weekday(future)) future = dayOffset(future, 1)
  const leave = requests(5, () => ({
    leaveType: 'ANNUAL',
    startDate: future,
    endDate: future,
    period: 'FULL_DAY',
    totalUnits: 2,
    adjustmentPending: false,
  }))
  // Include past approved leave for balances and personal history.
  leave.push(
    ...employees.slice(0, 3).map((e, i) => ({
      ...leave[i],
      id: uuid(5, i + 20),
      startDate: `${year}-01-05`,
      endDate: `${year}-01-06`,
      totalUnits: leaveUnits(`${year}-01-05`, `${year}-01-06`),
      status: 'APPROVED',
    })),
  )
  const permissions = requests(6, () => ({
    workDate: future,
    shiftId: shifts[0].id,
    shiftVersion: 0,
    requestType: 'LATE_ARRIVAL',
    period: 'MORNING',
    expectedTime: '09:00',
    requestedMinutes: 60,
  }))
  const corrections = requests(7, () => ({
    workDate: dayOffset(date, -1),
    shiftId: shifts[0].id,
    shiftVersion: 0,
    recordVersion: 0,
    proposedCheckIn: `${dayOffset(date, -1)}T01:00:00Z`,
    proposedCheckOut: `${dayOffset(date, -1)}T10:30:00Z`,
  }))
  const overtime = requests(8, () => ({
    workDate: date,
    start: `${date}T18:00`,
    end: `${date}T20:00`,
    approvedStart: `${date}T11:00:00Z`,
    approvedEnd: `${date}T13:00:00Z`,
    approvedMinutes: 120,
    countedMinutes: 0,
    checkIn: null,
    checkOut: null,
  }))
  const events = [
    ...[1, 5, 12, 19, 25].map((day, i) => ({
      id: i + 1,
      title: [
        'Họp toàn công ty',
        'Chào đón nhân viên mới',
        'Chia sẻ kỹ năng làm việc',
        'Workshop sản phẩm',
        'Sinh nhật tháng',
      ][i],
      description: 'Hoạt động nội bộ — dữ liệu minh họa.',
      type: 'EVENT',
      holidayKind: null,
      allDay: true,
      startDate: `${month}-${String(day).padStart(2, '0')}`,
      endDate: `${month}-${String(day).padStart(2, '0')}`,
      startAt: null,
      endAt: null,
      timezone: 'Asia/Ho_Chi_Minh',
      location: 'Phòng họp Lotus',
      audienceType: 'ALL',
      status: i === 4 ? 'DRAFT' : 'PUBLISHED',
      version: 0,
      createdAt: stamp,
    })),
    ...[
      ['01-01', 'Tết Dương lịch'],
      ['04-30', 'Ngày Giải phóng miền Nam'],
      ['05-01', 'Quốc tế Lao động'],
      ['09-02', 'Quốc khánh'],
    ].map(([day, title], i) => ({
      id: i + 10,
      title,
      description: 'Lịch nghỉ lễ mẫu',
      type: 'HOLIDAY',
      holidayKind: 'PUBLIC_HOLIDAY',
      allDay: true,
      startDate: `${year}-${day}`,
      endDate: `${year}-${day}`,
      timezone: 'Asia/Ho_Chi_Minh',
      audienceType: 'ALL',
      status: 'PUBLISHED',
      version: 0,
    })),
  ]
  return {
    schema: 1,
    seededAt: date,
    currentAccountId: null,
    employees,
    departments,
    positions,
    accounts,
    shifts,
    leave,
    permissions,
    corrections,
    overtime,
    events,
    adjustments: [],
    attendance: {},
    periods: {},
    histories: {},
    schedules: [],
    scheduleRevision: 0,
    provisioning: {},
    idempotency: {},
  }
}
