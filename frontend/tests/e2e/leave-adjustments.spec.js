import { expect, test } from '@playwright/test'

const id = 'd38e31b7-0bba-420c-8eaf-50edbe5a61ae'
const changeId = 'aa8e31b7-0bba-420c-8eaf-50edbe5a61ae'
async function setup(page, role = 'EMPLOYEE', own = true, pending = false) {
  const state = {
    request: {
      id,
      employeeId: 'employee',
      requesterUserId: 'member',
      leaveType: 'ANNUAL',
      startDate: '2030-02-01',
      endDate: '2030-02-03',
      period: 'FULL_DAY',
      totalUnits: 6,
      reason: 'Việc gia đình',
      status: 'APPROVED',
      version: 1,
      adjustmentPending: pending,
    },
    entries: [],
    writes: [],
    conflict: false,
  }
  const adjustment = (body) => ({
    id: changeId,
    requestId: id,
    originalStart: '2030-02-01',
    originalEnd: '2030-02-03',
    originalPeriod: 'FULL_DAY',
    originalUnits: 6,
    totalUnits: body.cancelAll ? 0 : 1,
    createdAt: '2030-01-01T01:00:00Z',
    status: 'PENDING',
    ...body,
  })
  if (pending) state.entries.push(adjustment({ cancelAll: true, reason: 'Đi làm bình thường' }))
  await page.addInitScript(() =>
    localStorage.setItem(
      'company-hrm.session',
      JSON.stringify({ id: 'test', refreshToken: 'refresh' }),
    ),
  )
  await page.route('**/api/**', async (route) => {
    const req = route.request()
    const path = new URL(req.url()).pathname
    if (path.endsWith('/refresh'))
      return route.fulfill({ json: { accessToken: 'access', refreshToken: 'refresh' } })
    if (path.endsWith('/me'))
      return route.fulfill({
        json: {
          id: own ? 'member' : 'reviewer',
          employeeId: own ? 'employee' : 'hr',
          email: 'member@company.com',
          roles: [role],
          active: true,
        },
      })
    if (path.endsWith('/pending-count')) return route.fulfill({ json: { count: 1 } })
    if (path.endsWith('/history')) return route.fulfill({ json: [] })
    if (path.startsWith('/api/v1/employees/'))
      return route.fulfill({ json: { firstName: 'An', lastName: 'Nguyễn' } })
    if (req.method() === 'GET' && path.endsWith('/adjustments'))
      return route.fulfill({ json: state.entries })
    if (req.method() === 'GET' && path.endsWith('/inbox'))
      return route.fulfill({
        json: { content: [state.request], page: 0, totalElements: 1, totalPages: 1 },
      })
    if (req.method() === 'GET') return route.fulfill({ json: state.request })
    state.writes.push({ path, body: req.postDataJSON(), version: req.headers()['if-match'] })
    if (state.conflict) return route.fulfill({ status: 412, json: { detail: 'Changed' } })
    if (req.method() === 'POST') {
      state.entries.push(adjustment(req.postDataJSON()))
      state.request.adjustmentPending = true
    } else {
      state.entries[0].status = path.endsWith('/approve')
        ? 'APPROVED'
        : path.endsWith('/cancel')
          ? 'CANCELLED'
          : 'REJECTED'
      state.request.adjustmentPending = false
      if (path.endsWith('/approve')) state.request.status = 'CANCELLED'
    }
    state.request.version++
    return route.fulfill({ json: state.request })
  })
  return state
}

test('owner requests full cancellation and withdraws it without changing approved leave', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto(`/leave/requests/${id}`)
  await page.getByLabel('Lý do điều chỉnh').fill('Đi làm bình thường')
  await page.getByRole('button', { name: 'Gửi yêu cầu điều chỉnh' }).click()
  await expect(page.getByText('Chờ HR xác nhận', { exact: true })).toBeVisible()
  expect(state.writes[0]).toMatchObject({
    version: '"1"',
    body: {
      cancelAll: true,
      startDate: null,
      endDate: null,
      period: null,
      reason: 'Đi làm bình thường',
    },
  })
  expect(state.request.status).toBe('APPROVED')
  await page.getByRole('button', { name: 'Rút yêu cầu điều chỉnh' }).click()
  await expect(page.getByText('Đã rút yêu cầu', { exact: true })).toBeVisible()
  expect(state.writes[1].version).toBe('"2"')
})

test('owner chooses the remaining half day', async ({ page }) => {
  const state = await setup(page)
  await page.goto(`/leave/requests/${id}`)
  await page.getByLabel('Nội dung điều chỉnh').selectOption('false')
  await page.getByLabel('Còn nghỉ từ ngày').fill('2030-02-02')
  await page.getByLabel('Thời lượng còn nghỉ').selectOption('AFTERNOON')
  await page.getByLabel('Lý do điều chỉnh').fill('Chỉ nghỉ chiều')
  await page.getByRole('button', { name: 'Gửi yêu cầu điều chỉnh' }).click()
  await expect(page.getByText('Chờ HR xác nhận', { exact: true })).toBeVisible()
  expect(state.writes[0].body).toMatchObject({
    cancelAll: false,
    startDate: '2030-02-02',
    endDate: '2030-02-02',
    period: 'AFTERNOON',
  })
})

test('HR filters adjustments and approves with the current version', async ({ page }) => {
  const state = await setup(page, 'HR', false, true)
  await page.goto('/leave/inbox')
  await page.getByLabel('Trạng thái', { exact: true }).selectOption('ADJUSTMENT_PENDING')
  await expect(page.getByText('Chờ duyệt điều chỉnh')).toBeVisible()
  await page.getByRole('link', { name: 'Xem đơn' }).click()
  await page.getByRole('button', { name: 'Duyệt điều chỉnh', exact: true }).click()
  await expect(page.getByText('Đã duyệt điều chỉnh', { exact: true })).toBeVisible()
  expect(state.writes[0]).toMatchObject({
    path: `/api/v1/leave/requests/${id}/adjustments/${changeId}/approve`,
    version: '"1"',
  })
})

test('HR rejection requires a reason and a conflict requires reload', async ({ page }) => {
  const state = await setup(page, 'HR', false, true)
  await page.goto(`/leave/requests/${id}`)
  await page.getByRole('button', { name: 'Từ chối điều chỉnh', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Vui lòng nhập lý do từ chối')
  expect(state.writes).toHaveLength(0)
  await page.getByLabel('Phản hồi điều chỉnh').fill('Cần đối soát')
  state.conflict = true
  await page.getByRole('button', { name: 'Từ chối điều chỉnh', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Duyệt điều chỉnh', exact: true })).toBeDisabled()
  expect(state.writes).toHaveLength(1)
})

test('HR cannot review their own adjustment', async ({ page }) => {
  await setup(page, 'HR', true, true)
  await page.goto(`/leave/requests/${id}`)
  await expect(page.getByRole('button', { name: 'Rút yêu cầu điều chỉnh' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Duyệt điều chỉnh', exact: true })).toHaveCount(0)
})
