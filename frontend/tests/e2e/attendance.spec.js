import { expect, test } from '@playwright/test'
const employeeId = '10000000-0000-0000-0000-000000000001'
const shiftId = '20000000-0000-0000-0000-000000000001'
const date = '2030-01-07'
const definition = {
  name: 'Ca mặc định',
  mode: 'FIXED_SHIFT',
  timezone: 'Asia/Ho_Chi_Minh',
  intervals: [
    { period: 'MORNING', start: '08:00', end: '12:00' },
    { period: 'AFTERNOON', start: '13:30', end: '17:30' },
  ],
  checkInFrom: '06:00',
  checkOutUntil: '22:00',
}
async function setup(page, role = 'EMPLOYEE') {
  const state = {
    writes: [],
    overtime: [],
    requests: [],
    requestKeys: {},
    failRequest: false,
    noSchedule: false,
    failPunch: false,
    stale: false,
    shift: { id: shiftId, version: 0, active: true, definition, requiredMinutes: 480 },
    row: {
      employeeId,
      employeeCode: 'EMP001',
      employeeName: 'Nguyễn An',
      workDate: date,
      shiftId,
      shiftVersion: 0,
      checkIn: null,
      checkOut: null,
      remainingRequiredMinutes: 480,
      workMinutesCounted: null,
      workedActualSeconds: null,
      leaveDays: 0,
      roundedLateMinutes: null,
      roundedEarlyMinutes: null,
      pendingLeaveIds: [],
      status: 'NOT_STARTED',
      reportState: 'DRAFT',
    },
  }
  await page.clock.setFixedTime(new Date('2030-01-07T01:00:00Z'))
  await page.addInitScript(() =>
    localStorage.setItem(
      'company-hrm.session',
      JSON.stringify({ id: 'attendance-test', refreshToken: 'refresh' }),
    ),
  )
  await page.route('**/api/**', async (route) => {
    const req = route.request(),
      path = new URL(req.url()).pathname
    if (path.endsWith('/refresh'))
      return route.fulfill({ json: { accessToken: 'access', refreshToken: 'refresh' } })
    if (path === '/api/v1/auth/me')
      return route.fulfill({
        json: { id: 'user', employeeId, email: 'an@company.com', roles: [role], active: true },
      })
    if (path.endsWith('/pending-count')) return route.fulfill({ json: { count: 0 } })
    expect(req.headers().authorization).toBe('Bearer access')
    const person = { id: employeeId, employeeCode: 'EMP001', firstName: 'An', lastName: 'Nguyễn' }
    if (path === '/api/v1/employees')
      return route.fulfill({ json: { content: [person], page: 0, totalPages: 1 } })
    if (path.startsWith('/api/v1/employees/')) return route.fulfill({ json: person })
    if (path === '/api/v1/departments')
      return route.fulfill({ json: { content: [], page: 0, totalPages: 0 } })
    if (path.startsWith('/api/v1/attendance/overtime')) {
      const base = '/api/v1/attendance/overtime'
      const collection = state.overtime
      if (req.method() === 'GET')
        return route.fulfill({
          json: {
            content: collection,
            page: 0,
            totalPages: collection.length ? 1 : 0,
            totalElements: collection.length,
          },
        })
      const body = req.postData() ? req.postDataJSON() : null
      state.writes.push({
        path,
        body,
        key: req.headers()['idempotency-key'],
        version: req.headers()['if-match'],
      })
      if (path === base) {
        const row = {
          ...body,
          id: 'ot-1',
          version: 0,
          employeeId,
          employeeName: 'Nguyễn An',
          status: 'PENDING',
          approvedMinutes: 0,
          countedMinutes: 0,
        }
        collection.push(row)
        return route.fulfill({ status: 201, json: row })
      }
      const row = collection.find((r) => path.includes(r.id))
      if (path.endsWith('/decision')) {
        row.status = body.status
        row.reviewNote = body.reviewNote
        row.version++
        if (row.status === 'APPROVED') row.approvedMinutes = 120
      }
      if (path.endsWith('/cancel')) {
        row.status = 'CANCELLED'
        row.version++
      }
      if (path.endsWith('/check-in')) {
        row.checkIn = '2030-01-07T11:10:00Z'
        row.countedMinutes = null
      }
      if (path.endsWith('/check-out')) {
        row.checkOut = '2030-01-07T13:10:00Z'
        row.countedMinutes = 110
      }
      return route.fulfill({ json: row })
    }
    if (path.startsWith('/api/v1/attendance/requests')) {
      const parts = path.split('/'),
        id = parts[5],
        row = state.requests.find((r) => r.id === id)
      if (req.method() === 'GET') {
        if (path.endsWith('/history'))
          return route.fulfill({
            json: {
              content: [{ id: 'audit', action: 'SUBMITTED', occurredAt: '2030-01-07T01:00:00Z' }],
              page: 0,
              totalPages: 1,
              totalElements: 1,
            },
          })
        const status = new URL(req.url()).searchParams.get('status')
        const content = state.requests.filter((r) => !status || r.status === status)
        return route.fulfill({
          json: {
            content,
            page: 0,
            totalPages: content.length ? 1 : 0,
            totalElements: content.length,
          },
        })
      }
      const body = req.postData() ? req.postDataJSON() : null
      state.writes.push({
        path,
        body,
        key: req.headers()['idempotency-key'],
        version: req.headers()['if-match'],
      })
      if (req.method() === 'POST' && path === '/api/v1/attendance/requests') {
        const key = req.headers()['idempotency-key']
        if (state.requestKeys[key])
          return route.fulfill({ status: 201, json: state.requestKeys[key] })
        if (
          state.requests.some(
            (r) =>
              ['PENDING', 'APPROVED'].includes(r.status) &&
              r.workDate === body.workDate &&
              r.period === body.period &&
              r.requestType === body.requestType,
          )
        )
          return route.fulfill({
            status: 409,
            json: { detail: 'An active request already exists for this date, period and type' },
          })
        const minutes =
          body.requestType === 'LATE_ARRIVAL'
            ? Number(body.expectedTime.slice(3))
            : 17 * 60 +
              30 -
              (Number(body.expectedTime.slice(0, 2)) * 60 + Number(body.expectedTime.slice(3)))
        const result = {
          ...body,
          id: crypto.randomUUID(),
          employeeId,
          employeeCode: 'EMP001',
          employeeName: 'Nguyễn An',
          version: 0,
          status: 'PENDING',
          requestedMinutes: minutes,
          roundedRequestedMinutes: Math.ceil(minutes / 15) * 15,
        }
        state.requests.unshift(result)
        state.requestKeys[key] = result
        if (state.failRequest) {
          state.failRequest = false
          return route.abort()
        }
        return route.fulfill({ status: 201, json: result })
      }
      if (!row) return route.fulfill({ status: 404, json: { detail: 'Missing request' } })
      if (req.headers()['if-match'] !== `"${row.version}"`)
        return route.fulfill({ status: 412, json: { detail: 'Changed' } })
      if (req.method() === 'PUT')
        Object.assign(row, body, { requestedMinutes: Number(body.expectedTime.slice(3)) })
      if (path.endsWith('/cancel')) row.status = 'CANCELLED'
      if (path.endsWith('/decision'))
        Object.assign(row, { status: body.status, reviewNote: body.reviewNote })
      row.version++
      return route.fulfill({ json: row })
    }
    if (req.method() !== 'GET') {
      state.writes.push({
        path,
        body: req.postData() ? req.postDataJSON() : null,
        key: req.headers()['idempotency-key'],
        version: req.headers()['if-match'],
      })
      if (path.endsWith('/check-in')) {
        if (state.failPunch) {
          state.failPunch = false
          return route.abort()
        }
        Object.assign(state.row, {
          status: 'OPEN',
          checkIn: '2030-01-07T01:00:00Z',
          recordVersion: 0,
        })
        return route.fulfill({ json: state.row })
      }
      if (path.endsWith('/check-out')) {
        Object.assign(state.row, {
          status: 'CLOSED',
          checkOut: '2030-01-07T10:30:00Z',
          workMinutesCounted: 480,
          workedActualSeconds: 28800,
        })
        return route.fulfill({ json: state.row })
      }
      if (path.endsWith('/preview'))
        return route.fulfill({
          json: {
            scheduleRevision: 3,
            shift: state.shift,
            affectedOverrides: [],
            recordedDayConflicts: 0,
            approvedLeaveConflicts: 0,
          },
        })
      if (path.endsWith('/apply'))
        return route.fulfill({ json: { batchId: shiftId, scheduleRevision: 4 } })
      if (path.includes('/shifts')) {
        if (state.stale) {
          state.stale = false
          return route.fulfill({ status: 412, json: { detail: 'Changed' } })
        }
        return route.fulfill({
          status: req.method() === 'POST' ? 201 : 200,
          json: { ...state.shift, definition: req.postDataJSON() },
        })
      }
    }
    if (path.endsWith('/export.csv'))
      return route.fulfill({
        contentType: 'text/csv',
        body: '\uFEFFemployee_code,work_minutes\r\nEMP001,480\r\n',
      })
    if (path.endsWith('/history'))
      return route.fulfill({ json: { content: [], page: 0, totalPages: 0, totalElements: 0 } })
    if (path.endsWith('/shifts'))
      return route.fulfill({
        json: { content: [state.shift], page: 0, totalPages: 1, totalElements: 1 },
      })
    if (path.includes('/shifts/')) return route.fulfill({ json: state.shift })
    if (path.endsWith('/schedules/mine'))
      return route.fulfill({
        json: [
          {
            date: new URL(req.url()).searchParams.get('until'),
            shiftId,
            shiftVersion: 0,
            definition: state.noSchedule ? null : definition,
            requiredMinutes: 480,
          },
        ],
      })
    if (path.endsWith('/mine') || path.endsWith('/reports'))
      return route.fulfill({ json: [state.row] })
    return route.fulfill({ status: 404, json: { detail: path } })
  })
  return state
}
test('employee checks in and out, and retries an uncertain write with the same key', async ({
  page,
}) => {
  const state = await setup(page)
  state.failPunch = true
  await page.goto('/attendance')
  await expect(page.getByRole('heading', { name: 'Công của tôi' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Ca làm việc', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: 'Ghi nhận giờ vào' }).click()
  await expect(page.getByRole('alert')).toContainText('Kết nối gián đoạn')
  await page.getByRole('button', { name: 'Ghi nhận giờ vào' }).click()
  await expect(page.getByRole('status')).toContainText('Đã ghi nhận giờ vào')
  expect(state.writes[0].key).toBe(state.writes[1].key)
  await page.getByRole('button', { name: 'Ghi nhận giờ ra' }).click()
  await expect(page.getByRole('status')).toContainText('Đã ghi nhận giờ ra')
  await expect(page.getByRole('button', { name: 'Ghi nhận giờ vào' })).toBeDisabled()
})
test('HR creates a short fixed shift and stale edits require reload', async ({ page }) => {
  const state = await setup(page, 'HR')
  await page.goto('/attendance/shifts')
  await page.getByLabel('Tên ca', { exact: true }).fill('Ca chiều ngắn')
  await page.getByRole('button', { name: 'Bỏ khoảng 1' }).click()
  await page.getByLabel('Bắt đầu 1').fill('13:00')
  await page.getByLabel('Kết thúc 1').fill('15:00')
  await page.getByRole('button', { name: 'Lưu ca', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Đã lưu ca')
  expect(state.writes[0].body.intervals).toEqual([
    { period: 'AFTERNOON', start: '13:00', end: '15:00' },
  ])
  await page.getByRole('button', { name: 'Xem / Sửa' }).click()
  state.stale = true
  await page.getByLabel('Tên ca', { exact: true }).fill('Đổi tên')
  await page.getByRole('button', { name: 'Lưu ca', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Dữ liệu đã thay đổi')
  await expect(page.getByRole('button', { name: 'Lưu ca', exact: true })).toBeDisabled()
  expect(state.writes.at(-1).version).toBe('"0"')
})
test('HR previews a selected assignment and editing invalidates the preview', async ({ page }) => {
  const state = await setup(page, 'HR')
  await page.goto('/attendance/schedules')
  await page.getByLabel('Phạm vi áp dụng').selectOption('SELECTED_EMPLOYEES')
  await page.getByLabel('Chọn nhân viên', { exact: true }).selectOption(employeeId)
  await page.getByRole('button', { name: 'Thêm nhân viên' }).click()
  await page.getByLabel('Lý do áp dụng').fill('Lịch mới')
  await page.getByRole('button', { name: 'Xem trước phân công' }).click()
  await expect(page.getByRole('button', { name: 'Áp dụng phân công', exact: true })).toBeVisible()
  await page.getByLabel('Lý do áp dụng').fill('Lịch mới đã điều chỉnh')
  await expect(page.getByRole('button', { name: 'Áp dụng phân công', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: 'Xem trước phân công' }).click()
  await page.getByRole('button', { name: 'Áp dụng phân công', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Đã áp dụng')
  const write = state.writes.find((r) => r.path.endsWith('/apply'))
  expect(write.body.employeeIds).toEqual([employeeId])
  expect(write.version).toBe('"3"')
  expect(write.key).toBeTruthy()
})
test('HR exports CSV and employee cannot open reports', async ({ page }) => {
  await setup(page, 'HR')
  await page.goto('/attendance/reports')
  await expect(page.getByRole('heading', { name: 'Bảng công tạm tính' })).toBeVisible()
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Xuất CSV' }).click()
  expect((await download).suggestedFilename()).toContain('bang-cong-')
})
test('employee management routes are guarded', async ({ page }) => {
  await setup(page)
  for (const route of ['shifts', 'schedules', 'reports']) {
    await page.goto(`/attendance/${route}`)
    await expect(page).toHaveURL('/forbidden')
  }
})

test('report table filters employees and statuses, resets pagination, and clears empty results', async ({
  page,
}) => {
  const state = await setup(page, 'HR')
  const rows = Array.from({ length: 14 }, (_, index) => ({
    ...state.row,
    employeeId: `employee-${index}`,
    employeeName: index === 0 ? 'Nguyễn Mai' : `Nhân viên ${index}`,
    employeeCode: `NV${String(index).padStart(3, '0')}`,
    status: index < 12 ? 'CLOSED' : 'MISSING_CHECK_OUT',
    workMinutesCounted: index < 12 ? 480 : null,
    workedActualSeconds: index < 12 ? 28800 : null,
  }))
  await page.route('**/api/v1/attendance/reports?*', (route) => route.fulfill({ json: rows }))
  await page.goto('/attendance/reports')
  const table = page.getByRole('table')
  await expect(table.locator('tbody tr')).toHaveCount(10)
  await expect(page.getByLabel('Tổng hợp kỳ đang xem')).toContainText('96h 0p')
  await page.getByRole('button', { name: 'Trang sau' }).click()
  await expect(table.locator('tbody tr')).toHaveCount(4)
  await page.getByLabel('Trạng thái công').selectOption('MISSING_CHECK_OUT')
  await expect(table.locator('tbody tr')).toHaveCount(2)
  await expect(page.getByRole('button', { name: 'Trang trước' })).toBeDisabled()
  await page.getByLabel('Tìm nhân viên trong bảng').fill('Nguyễn Mai')
  await expect(table).toContainText('Không có bản ghi phù hợp')
  await page.getByRole('button', { name: 'Xóa bộ lọc bảng' }).click()
  await page.getByLabel('Tìm nhân viên trong bảng').fill('nv000')
  await expect(table.locator('tbody tr')).toHaveCount(1)
  await expect(table).toContainText('Nguyễn Mai')
})

test('attendance screens fit desktop and mobile without page overflow', async ({
  page,
}, testInfo) => {
  await setup(page, 'HR')
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 960 })
    for (const path of ['', '/shifts', '/schedules', '/reports', '/requests']) {
      await page.goto(`/attendance${path}`)
      await expect(page.locator('.attendance-page')).toBeVisible()
      await expect(page.getByRole('alert')).toHaveCount(0)
      await expect
        .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
        .toBe(true)
      await page.screenshot({
        path: testInfo.outputPath(`attendance${path.replace('/', '-') || '-mine'}-${width}.png`),
        fullPage: true,
      })
    }
  }
})

test('employee submits, edits and withdraws a persisted late request through the API', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/attendance/requests')
  await expect(
    page.getByRole('heading', { name: 'Xin đi trễ / về sớm', exact: true }),
  ).toBeVisible()
  await page.getByLabel('Giờ đến dự kiến', { exact: true }).fill('08:20')
  await page.getByLabel('Lý do xin phép').fill('Lịch hẹn cá nhân thử nghiệm')
  await page.getByRole('button', { name: 'Gửi đơn', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Đã gửi đơn')
  expect(state.writes[0].body).toMatchObject({ shiftId, shiftVersion: 0 })
  expect(state.writes[0].body).not.toHaveProperty('requestedMinutes')
  await page.reload()
  const requestRow = page.getByRole('row').filter({ hasText: 'Lịch hẹn cá nhân thử nghiệm' })
  await expect(requestRow).toContainText('0h 20p')
  await requestRow.getByRole('button', { name: 'Sửa đơn' }).click()
  await page.getByLabel('Giờ đến dự kiến', { exact: true }).fill('08:40')
  await page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click()
  await expect(requestRow).toContainText('0h 40p')
  expect(state.writes.at(-1).version).toBe('"0"')
  await requestRow.getByRole('button', { name: 'Rút đơn', exact: true }).click()
  await requestRow.getByRole('button', { name: 'Xác nhận rút' }).click()
  await expect(requestRow).toContainText('Đã rút')
  await requestRow.getByRole('button', { name: 'Lịch sử đơn' }).click()
  await expect(requestRow).toContainText('SUBMITTED')
})
test('unknown create response retries with the same key and no assigned schedule disables submit', async ({
  page,
}) => {
  const state = await setup(page)
  state.failRequest = true
  await page.goto('/attendance/requests')
  await page.getByLabel('Giờ đến dự kiến', { exact: true }).fill('08:08')
  await page.getByLabel('Lý do xin phép').fill('Lịch hẹn')
  await page.getByRole('button', { name: 'Gửi đơn', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Kết nối gián đoạn')
  await page.getByRole('button', { name: 'Gửi đơn', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Đã gửi đơn')
  expect(state.writes[0].key).toBe(state.writes[1].key)
  expect(state.requests).toHaveLength(1)
  state.noSchedule = true
  await page.getByLabel('Ngày xin phép').fill('2030-01-08')
  await expect(page.getByRole('alert')).toContainText('Chưa được phân ca')
  await expect(page.getByRole('button', { name: 'Gửi đơn', exact: true })).toBeDisabled()
})
test('HR reviews requests with If-Match and a rejection note', async ({ page }) => {
  const state = await setup(page, 'HR')
  state.requests.push({
    id: shiftId,
    employeeId: 'other-employee',
    employeeCode: 'EMP002',
    employeeName: 'Bình',
    version: 0,
    workDate: date,
    period: 'MORNING',
    requestType: 'LATE_ARRIVAL',
    expectedTime: '08:08',
    requestedMinutes: 8,
    status: 'PENDING',
    reason: 'Lịch hẹn',
  })
  await page.goto('/attendance/requests')
  await page.getByRole('button', { name: 'Hàng chờ duyệt', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Hàng chờ HR/Admin' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Từ chối', exact: true })).toBeDisabled()
  await page.getByLabel('Phản hồi (bắt buộc khi từ chối)').fill('Cần bàn giao trước')
  await page.getByRole('button', { name: 'Từ chối', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Đã từ chối đơn')
  expect(state.writes.at(-1)).toMatchObject({
    version: '"0"',
    body: { status: 'REJECTED', reviewNote: 'Cần bàn giao trước' },
  })
})
test('request UI validates session boundaries and duplicate requests', async ({ page }) => {
  await setup(page)
  await page.goto('/attendance/requests')
  await page.getByRole('radio', { name: 'Xin về sớm' }).check()
  await page.getByLabel('Buổi làm việc', { exact: true }).selectOption('AFTERNOON')
  await page.getByLabel('Giờ về dự kiến', { exact: true }).fill('12:30')
  await page.getByLabel('Lý do xin phép').fill('Bàn giao công việc')
  await page.getByRole('button', { name: 'Gửi đơn', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('sau 13:30 và trước 17:30')
  await page.getByLabel('Giờ về dự kiến', { exact: true }).fill('16:45')
  await page.getByRole('button', { name: 'Gửi đơn', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Đã gửi đơn')
  await page.getByRole('radio', { name: 'Xin về sớm' }).check()
  await page.getByLabel('Buổi làm việc', { exact: true }).selectOption('AFTERNOON')
  await page.getByLabel('Giờ về dự kiến', { exact: true }).fill('16:30')
  await page.getByLabel('Lý do xin phép').fill('Đơn trùng')
  await page.getByRole('button', { name: 'Gửi đơn', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Đã có đơn chờ duyệt hoặc đã duyệt')
})

test('employee registers OT and only sees time recording after approval', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/attendance/overtime')
  await page.getByLabel('Lý do tăng ca').fill('Triển khai hệ thống')
  await page.getByRole('button', { name: 'Gửi đăng ký OT' }).click()
  await expect(page.getByRole('status')).toContainText('Đã gửi đăng ký OT')
  await expect(page.getByRole('button', { name: 'Bắt đầu OT', exact: true })).toHaveCount(0)
  expect(state.writes[0].body).toEqual({
    start: '2030-01-07T18:00',
    end: '2030-01-07T20:00',
    reason: 'Triển khai hệ thống',
  })
  expect(state.writes[0].key).toBeTruthy()
  state.overtime[0].status = 'APPROVED'
  state.overtime[0].approvedMinutes = 120
  await page.getByRole('button', { name: 'Tải lại', exact: true }).click()
  await page.getByRole('button', { name: 'Bắt đầu OT', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Bắt đầu OT', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: 'Kết thúc OT', exact: true }).click()
  await expect(page.getByText('OT đã duyệt: 2h 0p · Đã ghi nhận: 1h 50p')).toBeVisible()
})

test('HR reviews an OT request with its current version', async ({ page }) => {
  const state = await setup(page, 'HR')
  state.overtime.push({
    id: 'ot-1',
    version: 3,
    employeeName: 'Nhân viên khác',
    start: '2030-01-07T18:00',
    end: '2030-01-07T20:00',
    reason: 'Bảo trì',
    status: 'PENDING',
    approvedMinutes: 0,
    countedMinutes: 0,
  })
  await page.goto('/attendance/overtime')
  await page.getByRole('button', { name: 'Hộp duyệt HR' }).click()
  await page.getByRole('button', { name: 'Từ chối', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Nhập lý do từ chối')
  await page.getByRole('button', { name: 'Duyệt OT', exact: true }).click()
  expect(state.writes.at(-1).version).toBe('"3"')
  expect(state.writes.at(-1).body.status).toBe('APPROVED')
  await expect(page.getByRole('heading', { name: 'Nhân viên khác · Đã duyệt' })).toBeVisible()
})

test('overnight attendance shows the previous work date for checkout after midnight', async ({
  page,
}) => {
  const state = await setup(page)
  await page.clock.setFixedTime(new Date('2030-01-08T06:00:00+07:00'))
  const night = {
    ...definition,
    name: 'Ca đêm',
    overnight: true,
    intervals: [{ period: 'AFTERNOON', start: '22:00', end: '06:00' }],
    checkInFrom: '21:00',
    checkOutUntil: '07:00',
  }
  state.row.checkIn = '2030-01-07T15:00:00Z'
  state.row.status = 'OPEN'
  await page.route('**/api/v1/attendance/schedules/mine?**', (route) =>
    route.fulfill({
      json: [
        { date, shiftId, shiftVersion: 0, definition: night, requiredMinutes: 480 },
        { date: '2030-01-08', shiftId, shiftVersion: 0, definition, requiredMinutes: 480 },
      ],
    }),
  )
  await page.goto('/attendance')
  await expect(page.getByText('Ca đêm · 22:00–06:00 (+1 ngày)')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Ghi nhận giờ ra' })).toBeEnabled()
  await expect(page.getByRole('button', { name: 'Ghi nhận giờ vào' })).toBeDisabled()
  await page.getByRole('button', { name: 'Ghi nhận giờ ra' }).click()
  expect(state.writes.at(-1).path).toBe('/api/v1/attendance/check-out')
  await expect(page.getByRole('status')).toContainText('Đã ghi nhận giờ ra')
})
