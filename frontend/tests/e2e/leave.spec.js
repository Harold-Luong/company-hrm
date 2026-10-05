import { expect, test } from '@playwright/test'

const id = 'd38e31b7-0bba-420c-8eaf-50edbe5a61ae'
const employeeId = 'ba8e31b7-0bba-420c-8eaf-50edbe5a61ae'
const hrId = 'aa8e31b7-0bba-420c-8eaf-50edbe5a61ae'
const sample = {
  id,
  employeeId,
  requesterUserId: 'member',
  leaveType: 'ANNUAL',
  startDate: '2030-02-01',
  endDate: '2030-02-03',
  period: 'FULL_DAY',
  reason: 'Việc gia đình',
  status: 'PENDING',
  version: 0,
  createdAt: '2030-01-01T01:00:00Z',
}
async function setup(page, role = 'EMPLOYEE', own = false) {
  const state = { request: { ...sample }, writes: [], conflict: false }
  await page.clock.setFixedTime(new Date('2030-01-01T05:00:00Z'))
  await page.addInitScript(() => {
    if (!localStorage.getItem('company-hrm.session'))
      localStorage.setItem(
        'company-hrm.session',
        JSON.stringify({ id: 'test', refreshToken: 'refresh' }),
      )
  })
  await page.route('**/api/**', async (route) => {
    const req = route.request(),
      url = new URL(req.url()),
      path = url.pathname
    if (path.endsWith('/refresh'))
      return route.fulfill({ json: { accessToken: 'access', refreshToken: 'refresh' } })
    if (path.endsWith('/me'))
      return route.fulfill({
        json: {
          id: role === 'EMPLOYEE' || own ? 'member' : 'reviewer',
          employeeId: role === 'EMPLOYEE' || own ? employeeId : hrId,
          email: 'member@company.com',
          roles: [role],
          active: true,
        },
      })
    if (path.startsWith('/api/v1/employees/'))
      return route.fulfill({
        json: { firstName: 'An', lastName: 'Nguyễn', employeeCode: 'EMP001' },
      })
    expect(req.headers().authorization).toBe('Bearer access')
    if (path.endsWith('/pending-count')) return route.fulfill({ json: { count: 1 } })
    if (path.endsWith('/history'))
      return route.fulfill({
        json: [{ id: 1, action: 'SUBMITTED', actorUserId: 'member', occurredAt: sample.createdAt }],
      })
    if (path.endsWith('/mine') || path.endsWith('/inbox'))
      return route.fulfill({
        json: { content: [state.request], page: 0, totalElements: 1, totalPages: 1 },
      })
    if (req.method() === 'GET') return route.fulfill({ json: state.request })
    state.writes.push({
      path,
      body: req.postData() ? req.postDataJSON() : null,
      version: req.headers()['if-match'],
    })
    if (req.method() === 'POST') {
      Object.assign(state.request, req.postDataJSON())
      return route.fulfill({ status: 201, json: state.request })
    }
    if (state.conflict) {
      state.conflict = false
      state.request.version++
      return route.fulfill({ status: 412, json: { detail: 'Changed' } })
    }
    Object.assign(state.request, {
      status: { approve: 'APPROVED', reject: 'REJECTED', cancel: 'CANCELLED' }[
        path.split('/').at(-1)
      ],
      version: state.request.version + 1,
      reviewNote: req.postDataJSON()?.note,
    })
    return route.fulfill({ json: state.request })
  })
  return state
}

test('employee submits form, views status and withdraws pending request', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/leave')
  await page.getByLabel('Từ ngày').fill('2030-02-01')
  await page.getByLabel('Đến ngày').fill('2030-02-03')
  await page.getByLabel('Lý do nghỉ', { exact: true }).fill('Việc gia đình')
  await page.getByRole('button', { name: 'Gửi đơn đến HR' }).click()
  await expect(page).toHaveURL(`/leave/requests/${id}`)
  await expect(page.getByText('Chờ duyệt', { exact: true })).toBeVisible()
  expect(state.writes[0].body).toEqual({
    leaveType: 'ANNUAL',
    startDate: '2030-02-01',
    endDate: '2030-02-03',
    period: 'FULL_DAY',
    reason: 'Việc gia đình',
  })
  await page.getByRole('button', { name: 'Rút đơn', exact: true }).click()
  await expect(page.getByText('Đã rút đơn', { exact: true })).toBeVisible()
  expect(state.writes[1].version).toBe('"0"')
})
test('HR opens inbox and approves with the current version', async ({ page }) => {
  const state = await setup(page, 'HR')
  await page.goto('/leave/inbox')
  await expect(page.getByRole('navigation').getByLabel('1 đơn đang chờ duyệt')).toBeVisible()
  await expect(page.getByText('Nguyễn An · EMP001')).toBeVisible()
  await page.getByRole('link', { name: 'Xem đơn' }).click()
  await page.getByLabel('Phản hồi (bắt buộc khi từ chối)').fill('Đồng ý')
  await page.getByRole('button', { name: 'Duyệt đơn', exact: true }).click()
  await expect(page.getByText('Đã duyệt', { exact: true })).toBeVisible()
  expect(state.writes[0]).toMatchObject({ version: '"0"', body: { note: 'Đồng ý' } })
})
test('HR must provide a rejection reason', async ({ page }) => {
  const state = await setup(page, 'HR')
  await page.goto(`/leave/requests/${id}`)
  await page.getByRole('button', { name: 'Từ chối', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('nhập lý do')
  expect(state.writes).toHaveLength(0)
  await page.getByLabel('Phản hồi (bắt buộc khi từ chối)').fill('Cần đổi lịch')
  await page.getByRole('button', { name: 'Từ chối', exact: true }).click()
  await expect(page.getByText('Từ chối', { exact: true })).toBeVisible()
  await expect(page.getByText('Cần đổi lịch', { exact: true })).toBeVisible()
})
test('conflicting decisions require reload before retry', async ({ page }) => {
  const state = await setup(page, 'HR')
  state.conflict = true
  await page.goto(`/leave/requests/${id}`)
  await page.getByRole('button', { name: 'Duyệt đơn', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Đơn đã thay đổi')
  await expect(page.getByRole('button', { name: 'Duyệt đơn', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: 'Tải lại' }).click()
  await page.getByRole('button', { name: 'Duyệt đơn', exact: true }).click()
  await expect(page.getByText('Đã duyệt', { exact: true })).toBeVisible()
  expect(state.writes[1].version).toBe('"1"')
})
test('employee cannot open HR inbox', async ({ page }) => {
  await setup(page)
  await page.goto('/leave/inbox')
  await expect(page).toHaveURL('/forbidden')
})
test('HR cannot review their own request', async ({ page }) => {
  await setup(page, 'HR', true)
  await page.goto(`/leave/requests/${id}`)
  await expect(page.getByText('Đơn của bạn cần HR/ADMIN khác xử lý.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Duyệt đơn', exact: true })).toHaveCount(0)
})
