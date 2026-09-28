import { expect, test } from '@playwright/test'
const id = '550e8400-e29b-41d4-a716-446655440000'
const requestId = '650e8400-e29b-41d4-a716-446655440000'
const departmentId = '750e8400-e29b-41d4-a716-446655440000'
const employee = {
  id,
  employeeCode: 'NV001',
  lastName: 'Nguyễn Văn',
  firstName: 'An',
  email: 'an@company.com',
  phone: '0901234567',
  dateOfBirth: '1995-05-20',
  hireDate: '2026-01-01',
  status: 'PROBATION',
  accountStatus: 'NOT_CREATED',
  department: null,
  position: null,
  manager: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}
async function setup(page, roles = ['HR']) {
  const state = {
    employee: { ...employee },
    requests: [],
    saves: [],
    status: 'PENDING',
    provisioningError: '',
    loseResponse: false,
    listError: false,
  }
  await page.addInitScript(() => sessionStorage.setItem('company-hrm.refresh-token', 'refresh'))
  await page.route('**/api/v1/**', async (route) => {
    const req = route.request()
    const url = new URL(req.url())
    const path = url.pathname
    let body
    let status = 200
    if (path.endsWith('/refresh')) body = { accessToken: 'access', refreshToken: 'refresh' }
    else if (path.endsWith('/me')) body = { id: 1, email: 'hr@company.com', active: true, roles }
    else if (path.endsWith('/health-check')) body = { status: 'UP', database: 'UP' }
    else if (path.includes('/account-requests')) {
      if (req.method() === 'POST') {
        state.requests.push({ body: req.postDataJSON(), key: req.headers()['idempotency-key'] })
        if (state.loseResponse) {
          state.loseResponse = false
          await route.abort()
          return
        }
      }
      body = {
        requestId,
        employeeId: id,
        provisioningStatus: state.status,
        errorCode: state.provisioningError || null,
        accountStatus: state.status === 'SUCCEEDED' ? 'PENDING_ACTIVATION' : 'NOT_CREATED',
        createdAt: '2026-01-01T00:00:00Z',
      }
      if (req.method() === 'POST') status = 202
    } else if (path.endsWith('/status')) {
      state.employee.status = req.postDataJSON().status
      body = state.employee
    } else if (path === `/api/v1/employees/${id}`) {
      if (req.method() === 'PUT') {
        state.saves.push(req.postDataJSON())
        Object.assign(state.employee, req.postDataJSON())
      }
      body = state.employee
    } else if (path === '/api/v1/employees' && req.method() === 'POST') {
      state.saves.push(req.postDataJSON())
      Object.assign(state.employee, req.postDataJSON())
      body = state.employee
      status = 201
    } else if (path === `/api/v1/departments/${departmentId}`) {
      body = { id: departmentId, code: 'HR', name: 'Nhân sự', description: 'Đội ngũ HR' }
      if (req.method() === 'PUT') {
        state.saves.push(req.postDataJSON())
        body = { ...body, ...req.postDataJSON() }
      }
    } else if (path === '/api/v1/departments' && req.method() === 'POST') {
      state.saves.push(req.postDataJSON())
      body = { id: departmentId, ...req.postDataJSON() }
      status = 201
    } else {
      if (state.listError) {
        status = 503
        body = { detail: 'Service unavailable' }
      } else
        body = {
          content: path.endsWith('/employees')
            ? [state.employee]
            : path.endsWith('/departments')
              ? [{ id: departmentId, code: 'HR', name: 'Nhân sự', description: 'Đội ngũ HR' }]
              : [],
          page: Number(url.searchParams.get('page') || 0),
          size: 20,
          totalElements: path.endsWith('/employees') ? 21 : path.endsWith('/departments') ? 1 : 0,
          totalPages: path.endsWith('/employees') ? 2 : path.endsWith('/departments') ? 1 : 0,
        }
    }
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
  })
  return state
}
test('employee create, edit and status update follow backend payloads', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/employees/new')
  await page.getByLabel('Mã nhân viên').fill('NV002')
  await page.getByLabel('Email công việc').fill('new@company.com')
  await page.getByLabel('Họ và tên đệm').fill('Trần')
  await page.getByLabel('Tên *', { exact: true }).fill('Bình')
  await page.getByLabel('Ngày vào làm').fill('2026-09-01')
  await page.getByLabel('Phòng ban', { exact: true }).selectOption(departmentId)
  await page.getByRole('button', { name: 'Tạo nhân viên', exact: true }).click()
  await expect(page).toHaveURL(`/employees/${id}`)
  expect(state.saves[0]).toMatchObject({
    employeeCode: 'NV002',
    departmentId,
    positionId: null,
    managerId: null,
    dateOfBirth: null,
    phone: null,
  })
  await page.screenshot({ path: 'test-results/employee-desktop.png', fullPage: true })
  await page.getByLabel('Số điện thoại').fill('0912345678')
  await page.getByRole('button', { name: 'Lưu hồ sơ', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Đã lưu hồ sơ nhân viên.')
  await page.getByLabel('Trạng thái mới').selectOption('ACTIVE')
  await page.getByRole('button', { name: 'Cập nhật trạng thái', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Đã cập nhật trạng thái công việc.')
  expect(state.employee.status).toBe('ACTIVE')
})
test('catalog forms, empty state, pagination and list recovery work', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/departments/new')
  await page.getByLabel('Mã phòng ban').fill('HR')
  await page.getByLabel('Tên phòng ban').fill('Nhân sự')
  await page.getByRole('button', { name: 'Tạo mới', exact: true }).click()
  await expect(page).toHaveURL(`/departments/${departmentId}`)
  await page.getByLabel('Mô tả').fill('Cập nhật mô tả')
  await page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click()
  await expect(page.getByRole('status')).toHaveText('Đã lưu thay đổi.')
  expect(state.saves[1].description).toBe('Cập nhật mô tả')
  await page.goto('/positions')
  await expect(page.getByText('Chưa có chức danh.', { exact: false })).toBeVisible()
  state.listError = true
  await page.goto('/employees')
  await expect(page.getByRole('alert')).toContainText('Không thể kết nối dịch vụ.')
  state.listError = false
  await page.getByRole('button', { name: 'Tải lại', exact: true }).click()
  await expect(page.getByRole('link', { name: 'Nguyễn Văn An', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Trang sau', exact: true }).click()
  await expect(page.getByText('21 bản ghi · Trang 2 / 2')).toBeVisible()
})
test('Kafka retries reuse the idempotency key and pending request resumes after reload', async ({
  page,
}) => {
  const state = await setup(page)
  state.loseResponse = true
  await page.goto(`/employees/${id}`)
  await page.getByRole('button', { name: 'Gửi yêu cầu cấp tài khoản' }).click()
  await expect(page.getByRole('button', { name: 'Thử gửi lại' })).toBeVisible()
  await page.getByRole('button', { name: 'Thử gửi lại' }).click()
  await expect(page.getByText('Đang xử lý', { exact: true })).toBeVisible()
  expect(state.requests).toHaveLength(2)
  expect(state.requests[0]).toEqual(state.requests[1])
  expect(state.requests[0].body).toEqual({ email: 'an@company.com' })
  expect(state.requests[0].key).toMatch(/^[0-9a-f-]{36}$/)
  state.status = 'SUCCEEDED'
  await page.reload()
  await expect(page.getByText('Đã cấp tài khoản', { exact: true })).toBeVisible()
  await expect(page.getByText('Chờ kích hoạt', { exact: true }).first()).toBeVisible()
  expect(state.requests).toHaveLength(2)
})
test('Kafka failure explains conflict and permits a new request', async ({ page }) => {
  const state = await setup(page)
  state.status = 'FAILED'
  state.provisioningError = 'EMAIL_ALREADY_USED'
  await page.goto(`/employees/${id}`)
  await page.getByRole('button', { name: 'Gửi yêu cầu cấp tài khoản' }).click()
  await expect(page.getByText('Email này đã được dùng cho tài khoản khác.')).toBeVisible()
  await page.getByRole('button', { name: 'Chuẩn bị yêu cầu mới' }).click()
  await page.getByLabel('Email cấp tài khoản').fill('different@company.com')
  await page.getByRole('button', { name: 'Gửi yêu cầu cấp tài khoản' }).click()
  await expect(page.getByText('Cấp tài khoản thất bại', { exact: true })).toBeVisible()
  expect(state.requests[0].key).not.toBe(state.requests[1].key)
})
test('employee role sees personnel forms but cannot send provisioning requests', async ({
  page,
}) => {
  await setup(page, ['EMPLOYEE'])
  await page.goto(`/employees/${id}`)
  await expect(page.getByRole('heading', { name: 'Nguyễn Văn An' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Gửi yêu cầu cấp tài khoản' })).toHaveCount(0)
  await page.goto('/services')
  await expect(page.getByText('Hoạt động bình thường')).toHaveCount(2)
})
test('personnel pages fit a mobile viewport and render without runtime errors', async ({
  page,
}) => {
  const errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.setViewportSize({ width: 390, height: 844 })
  await setup(page)
  for (const path of ['/employees', `/employees/${id}`, '/departments/new', '/services']) {
    await page.goto(path)
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({
      path: `test-results/hrm-mobile-${path.replaceAll('/', '-')}.png`,
      fullPage: true,
    })
  }
  expect(errors).toEqual([])
})
