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
            return route.fulfill({ json: [{ definition, requiredMinutes: 480 }] })
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

test('employee previews, edits and withdraws a late request without calling attendance APIs', async ({ page }) => {
  await setup(page)
  const attendanceCalls = []
  page.on('request', (request) => { if (request.url().includes('/api/v1/attendance')) attendanceCalls.push(request.url()) })
  await page.goto('/attendance/requests')
  await expect(page.getByRole('heading', { name: 'Xin đi trễ / về sớm', exact: true })).toBeVisible()
  await expect(page.getByText('Bản xem trước · Dữ liệu mô phỏng')).toBeVisible()
  await page.getByLabel('Giờ đến dự kiến', { exact: true }).fill('08:20')
  await page.getByLabel('Lý do xin phép').fill('Lịch hẹn cá nhân thử nghiệm')
  await page.getByRole('button', { name: 'Gửi thử đơn', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Đã tạo đơn mô phỏng')
  const requestRow = page.getByRole('row').filter({ hasText: 'Lịch hẹn cá nhân thử nghiệm' })
  await expect(requestRow).toContainText('0h 20p')
  await requestRow.getByRole('button', { name: 'Sửa đơn' }).click()
  await page.getByLabel('Giờ đến dự kiến', { exact: true }).fill('08:40')
  await page.getByRole('button', { name: 'Lưu thay đổi thử' }).click()
  await expect(requestRow).toContainText('0h 40p')
  await requestRow.getByRole('button', { name: 'Rút đơn', exact: true }).click()
  await requestRow.getByRole('button', { name: 'Giữ đơn' }).click()
  await expect(requestRow).toContainText('Chờ duyệt')
  await requestRow.getByRole('button', { name: 'Rút đơn', exact: true }).click()
  await requestRow.getByRole('button', { name: 'Xác nhận rút' }).click()
  await page.getByLabel('Trạng thái đơn').selectOption('CANCELLED')
  await expect(page.getByRole('table').locator('tbody tr')).toHaveCount(1)
  await expect(requestRow).toContainText('Đã rút')
  await expect(requestRow.getByRole('button', { name: 'Sửa đơn' })).toHaveCount(0)
  expect(attendanceCalls).toEqual([])
})

test('early departure UI validates session boundaries and duplicate requests', async ({ page }) => {
  await setup(page)
  await page.goto('/attendance/requests')
  await page.getByRole('radio', { name: 'Xin về sớm' }).check()
  await page.getByLabel('Buổi làm việc', { exact: true }).selectOption('AFTERNOON')
  await page.getByLabel('Giờ về dự kiến', { exact: true }).fill('12:30')
  await page.getByLabel('Lý do xin phép').fill('Bàn giao công việc trước khi về')
  await page.getByRole('button', { name: 'Gửi thử đơn', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('sau 13:30 và trước 17:30')
  await page.getByLabel('Giờ về dự kiến', { exact: true }).fill('16:45')
  await page.getByRole('button', { name: 'Gửi thử đơn', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Đã tạo đơn mô phỏng')
  await expect(page.getByRole('table')).toContainText('0h 45p')
  await page.getByRole('radio', { name: 'Xin về sớm' }).check()
  await page.getByLabel('Buổi làm việc', { exact: true }).selectOption('AFTERNOON')
  await page.getByLabel('Giờ về dự kiến', { exact: true }).fill('16:30')
  await page.getByLabel('Lý do xin phép').fill('Đơn trùng')
  await page.getByRole('button', { name: 'Gửi thử đơn', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Đã có đơn chờ duyệt hoặc đã duyệt')
})
