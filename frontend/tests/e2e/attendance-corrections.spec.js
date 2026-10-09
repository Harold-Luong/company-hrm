import { expect, test } from '@playwright/test'
const employeeId = '10000000-0000-0000-0000-000000000001',
  otherId = '10000000-0000-0000-0000-000000000002'
const date = '2030-01-07'
const pending = {
  id: 'correction-1',
  version: 0,
  employeeId,
  employeeName: 'Nguyễn An',
  workDate: date,
  beforeCheckIn: null,
  beforeCheckOut: null,
  proposedCheckIn: '2030-01-07T06:00:00Z',
  proposedCheckOut: '2030-01-07T08:00:00Z',
  reason: 'Quên ghi nhận',
  status: 'PENDING',
}
async function setup(page, role = 'EMPLOYEE') {
  const state = { rows: [], writes: [], fail: false, stale: false }
  await page.clock.setFixedTime(new Date('2030-01-07T18:00:00+07:00'))
  await page.addInitScript(() =>
    localStorage.setItem(
      'company-hrm.session',
      JSON.stringify({ id: 'corrections-test', refreshToken: 'refresh' }),
    ),
  )
  await page.route('**/api/**', async (route) => {
    const req = route.request(),
      path = new URL(req.url()).pathname
    if (path.endsWith('/refresh'))
      return route.fulfill({ json: { accessToken: 'access', refreshToken: 'refresh' } })
    if (path === '/api/v1/auth/me')
      return route.fulfill({
        json: {
          id: 'user',
          email: 'an@company.test',
          employeeId: role === 'HR' ? otherId : employeeId,
          roles: [role],
          active: true,
        },
      })
    if (path.endsWith('/pending-count')) return route.fulfill({ json: { count: 0 } })
    if (path.endsWith('/schedules/mine'))
      return route.fulfill({
        json: [
          {
            date,
            shiftId: 'shift',
            shiftVersion: 0,
            definition: {
              name: 'Ca chiều',
              intervals: [{ period: 'AFTERNOON', start: '13:00', end: '15:00' }],
            },
          },
        ],
      })
    if (path === '/api/v1/attendance/mine')
      return route.fulfill({
        json: [{ workDate: date, checkIn: null, checkOut: null, recordVersion: null }],
      })
    if (path.includes('/corrections')) {
      if (req.method() === 'GET') {
        const content = path.endsWith('/history')
          ? [
              {
                id: 'history-1',
                action: 'SUBMITTED',
                occurredAt: '2030-01-07T11:00:00Z',
                actorUserId: 'user',
                snapshot: JSON.stringify(pending),
              },
            ]
          : state.rows
        return route.fulfill({
          json: { content, page: 0, totalPages: 1, totalElements: content.length },
        })
      }
      const body = req.postData() ? req.postDataJSON() : null
      state.writes.push({
        path,
        body,
        key: req.headers()['idempotency-key'],
        version: req.headers()['if-match'],
      })
      if (state.fail) {
        state.fail = false
        return route.abort('failed')
      }
      if (state.stale) {
        state.stale = false
        return route.fulfill({
          status: 412,
          json: { detail: 'Attendance changed; update the correction before approval' },
        })
      }
      if (path.endsWith('/decision')) {
        Object.assign(state.rows[0], {
          status: body.status,
          reviewNote: body.reviewNote,
          version: 1,
        })
        return route.fulfill({ json: state.rows[0] })
      }
      if (path.endsWith('/cancel')) {
        state.rows[0].status = 'CANCELLED'
        return route.fulfill({ json: state.rows[0] })
      }
      const row = { ...pending, ...body, version: req.method() === 'PUT' ? 1 : 0 }
      state.rows = [row]
      return route.fulfill({ status: req.method() === 'PUT' ? 200 : 201, json: row })
    }
    return route.fulfill({ json: {} })
  })
  return state
}
test('employee retries, edits, reads history and cancels a correction', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/attendance/corrections')
  await page.getByLabel('Giờ vào đề nghị').fill(`${date}T13:00`)
  await page.getByLabel('Giờ ra đề nghị').fill(`${date}T15:00`)
  await page.getByLabel('Lý do bổ sung / điều chỉnh').fill('Quên ghi nhận')
  state.fail = true
  await page.getByRole('button', { name: 'Gửi đơn', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Kết nối gián đoạn')
  await page.getByRole('button', { name: 'Gửi đơn', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Đã lưu đơn')
  expect(state.writes[0].key).toBe(state.writes[1].key)
  expect(state.writes[1].body).toMatchObject({
    recordVersion: null,
    proposedCheckIn: '2030-01-07T06:00:00Z',
    proposedCheckOut: '2030-01-07T08:00:00Z',
  })
  await page.getByRole('button', { name: 'Sửa đơn', exact: true }).click()
  await expect(page.getByLabel('Lý do bổ sung / điều chỉnh')).toHaveValue('Quên ghi nhận')
  await page.getByLabel('Lý do bổ sung / điều chỉnh').fill('Bổ sung lý do')
  await page.getByRole('button', { name: 'Lưu thay đổi' }).click()
  await expect(page.getByText('Lý do: Bổ sung lý do', { exact: true })).toBeVisible()
  expect(state.writes.at(-1).version).toBe('"0"')
  await page.getByRole('button', { name: 'Lịch sử đơn' }).click()
  await expect(page.getByText(/Gửi đơn ·/)).toBeVisible()
  await page.getByRole('button', { name: 'Rút đơn', exact: true }).click()
  await page.getByRole('button', { name: 'Xác nhận rút' }).click()
  await expect(page.getByRole('status')).toContainText('Đã rút đơn')
  expect(state.writes.at(-1).version).toBe('"1"')
})
test('reviewer handles stale data and requires rejection reason', async ({ page }) => {
  const state = await setup(page, 'HR')
  state.rows = [{ ...pending }]
  await page.goto('/attendance/corrections')
  await page.getByRole('button', { name: 'Xét duyệt', exact: true }).click()
  await page.getByRole('button', { name: 'Từ chối', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('Nhập lý do từ chối đơn.')
  expect(state.writes).toHaveLength(0)
  state.stale = true
  await page.getByRole('button', { name: 'Duyệt và cập nhật công' }).click()
  await expect(page.getByRole('alert')).toContainText('Người gửi cần sửa đơn')
  await page.getByLabel('Ghi chú xét duyệt (bắt buộc khi từ chối)').fill('Cần xác minh giờ ra')
  await page.getByRole('button', { name: 'Từ chối', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Đã từ chối đơn')
  expect(state.writes.at(-1).body).toEqual({
    status: 'REJECTED',
    reviewNote: 'Cần xác minh giờ ra',
  })
})
test('reviewer approves another employee but not own request', async ({ page }) => {
  const state = await setup(page, 'HR')
  state.rows = [{ ...pending }, { ...pending, id: 'own', employeeId: otherId }]
  await page.goto('/attendance/corrections')
  await page.getByRole('button', { name: 'Xét duyệt', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Duyệt và cập nhật công' })).toHaveCount(1)
  await page.getByRole('button', { name: 'Duyệt và cập nhật công' }).click()
  await expect(page.getByRole('status')).toContainText('Đã duyệt và cập nhật bảng công')
  expect(state.writes[0].version).toBe('"0"')
  expect(state.writes[0].body.status).toBe('APPROVED')
})
