import { expect, test } from '@playwright/test'
const baseUser = {
  id: 7,
  employeeId: '550e8400-e29b-41d4-a716-446655440000',
  email: 'admin@company.com',
  active: true,
  roles: ['ADMIN'],
  lastLoginAt: '2026-09-25T01:00:00Z',
  createdAt: '2026-09-01T01:00:00Z',
  updatedAt: '2026-09-25T01:00:00Z',
}
async function mockAuth(page, roles = ['ADMIN']) {
  const state = {
    roles,
    loginStatus: 200,
    refreshStatus: 200,
    meStatus: 200,
    registerStatus: 200,
    loginCount: 0,
    refreshCount: 0,
    registerBody: null,
    logoutAllCount: 0,
  }
  await page.route('**/api/v1/auth/**', async (route) => {
    const endpoint = new URL(route.request().url()).pathname.split('/').pop()
    let status = 200
    let body = { message: 'OK' }
    if (endpoint === 'login') {
      state.loginCount++
      status = state.loginStatus
      body =
        status === 200
          ? {
              message: 'Login successful',
              data: {
                tokenType: 'Bearer',
                accessToken: 'access-token',
                refreshToken: 'refresh-token',
                accessTokenExpiresIn: 900,
                refreshTokenExpiresIn: 604800,
              },
            }
          : {
              code: String(status),
              message:
                status === 403
                  ? 'User is inactive'
                  : status === 429
                    ? 'Too many login attempts'
                    : status === 503
                      ? 'Service unavailable'
                      : 'Invalid email or password',
            }
    }
    if (endpoint === 'me') {
      status = state.meStatus
      body = { ...baseUser, roles: state.roles }
    }
    if (endpoint === 'refresh') {
      state.refreshCount++
      status = state.refreshStatus
      body = { accessToken: 'rotated-access', refreshToken: 'rotated-refresh' }
    }
    if (endpoint === 'register') {
      state.registerBody = route.request().postDataJSON()
      status = state.registerStatus
      body = { message: status === 400 ? 'Email already exists' : 'User registered successfully!' }
    }
    if (endpoint === 'logout-all') state.logoutAllCount++
    await route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify(body),
      headers: status === 429 ? { 'Retry-After': '120' } : {},
    })
  })
  return state
}
async function login(page) {
  await page.getByLabel('Email công việc', { exact: true }).fill('admin@company.com')
  await page.getByLabel('Mật khẩu', { exact: true }).fill('test-password')
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
}
test('protected deep link returns to the page after login, restores on reload, then logs out', async ({
  page,
}) => {
  const state = await mockAuth(page)
  await page.goto('/accounts/new')
  await expect(page).toHaveURL(/\/login\?redirect=/)
  await login(page)
  await expect(page.getByRole('heading', { name: 'Tạo tài khoản', exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Tạo tài khoản', exact: true })).toBeVisible()
  expect(state.refreshCount).toBe(1)
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Đăng nhập workspace' })).toBeVisible()
  expect(await page.evaluate(() => sessionStorage.length)).toBe(0)
  await page.goto('/account')
  await expect(page).toHaveURL(/\/login/)
})
for (const role of ['EMPLOYEE', 'MANAGER']) {
  test(`${role} cannot access account creation even via direct URL`, async ({ page }) => {
    await mockAuth(page, [role])
    await page.goto('/login')
    await login(page)
    await expect(page.getByRole('heading', { name: 'Tổng quan', exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Tạo tài khoản', exact: true })).toHaveCount(0)
    await page.goto('/accounts/new')
    await expect(page.getByRole('heading', { name: 'Bạn chưa có quyền truy cập' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Tạo tài khoản', exact: true })).toHaveCount(0)
  })
}
for (const roles of [['HR'], ['ADMIN'], ['EMPLOYEE', 'HR']]) {
  test(`${roles.join('+')} can register through Auth with selected roles`, async ({ page }) => {
    const state = await mockAuth(page, roles)
    await page.goto('/accounts/new')
    await login(page)
    await page.getByLabel('Email công việc', { exact: false }).fill('new@company.com')
    await page.getByLabel('Mật khẩu ban đầu', { exact: false }).fill('new-password')
    await page.getByLabel('UUID liên kết', { exact: false }).fill(baseUser.employeeId)
    await page.getByRole('checkbox', { name: /Quản lý/ }).check()
    await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click()
    await expect(page.getByRole('status')).toContainText(
      'Đã tạo tài khoản new@company.com thành công.',
    )
    expect(state.registerBody).toEqual({
      email: 'new@company.com',
      password: 'new-password',
      employeeId: baseUser.employeeId,
      roles: ['EMPLOYEE', 'MANAGER'],
    })
    expect(state.loginCount).toBe(1)
    await expect(page.getByLabel('Mật khẩu ban đầu', { exact: false })).toBeEmpty()
  })
}
test('role revocation is applied on the next navigation', async ({ page }) => {
  const state = await mockAuth(page)
  await page.goto('/login')
  await login(page)
  await expect(page.getByRole('heading', { name: 'Tổng quan', exact: true })).toBeVisible()
  state.roles = ['EMPLOYEE']
  await page
    .getByRole('navigation')
    .getByRole('link', { name: 'Tạo tài khoản', exact: true })
    .click()
  await expect(page.getByRole('heading', { name: 'Bạn chưa có quyền truy cập' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Tạo tài khoản', exact: true })).toHaveCount(0)
})
test('invalid login, inactive account, throttling and service errors are readable', async ({
  page,
}) => {
  const state = await mockAuth(page)
  await page.goto('/login')
  for (const [status, message] of [
    [401, 'Email hoặc mật khẩu không chính xác.'],
    [403, 'Tài khoản đã bị vô hiệu hóa.'],
    [429, 'thử lại sau 2 phút'],
    [503, 'Không thể kết nối dịch vụ xác thực.'],
  ]) {
    state.loginStatus = status
    await login(page)
    await expect(page.getByRole('alert')).toContainText(message)
    await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeEnabled()
  }
})
test('expired refresh returns to login without protected content', async ({ page }) => {
  const state = await mockAuth(page)
  await page.goto('/login')
  await login(page)
  await expect(page.getByRole('heading', { name: 'Tổng quan', exact: true })).toBeVisible()
  state.refreshStatus = 401
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Đăng nhập workspace' })).toBeVisible()
  expect(await page.evaluate(() => sessionStorage.length)).toBe(0)
})
test('service outage on session restoration allows retry without discarding credentials', async ({
  page,
}) => {
  const state = await mockAuth(page)
  await page.goto('/login')
  await login(page)
  await expect(page.getByRole('heading', { name: 'Tổng quan', exact: true })).toBeVisible()
  state.refreshStatus = 503
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Chưa thể mở workspace' })).toBeVisible()
  state.refreshStatus = 200
  await page.getByRole('button', { name: 'Thử kết nối lại' }).click()
  await expect(page.getByRole('heading', { name: 'Tổng quan', exact: true })).toBeVisible()
})
test('logout-all requires confirmation and clears the session', async ({ page }) => {
  const state = await mockAuth(page)
  await page.goto('/account')
  await login(page)
  await page.getByRole('button', { name: 'Đăng xuất tất cả phiên' }).click()
  expect(state.logoutAllCount).toBe(0)
  await page.getByRole('button', { name: 'Xác nhận đăng xuất' }).click()
  await expect(page.getByRole('heading', { name: 'Đăng nhập workspace' })).toBeVisible()
  expect(state.logoutAllCount).toBe(1)
})
test('mobile layout has no horizontal overflow and provides working navigation', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockAuth(page)
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Đăng nhập workspace' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: 'test-results/login-mobile.png', fullPage: true })
  await login(page)
  await expect(page.getByRole('heading', { name: 'Tổng quan', exact: true })).toBeVisible()
  await expect(page.getByRole('navigation')).not.toBeVisible()
  await page.getByRole('button', { name: 'Mở menu' }).click()
  await page.getByRole('navigation').getByRole('link', { name: 'Tạo tài khoản' }).click()
  await expect(page.getByRole('heading', { name: 'Tạo tài khoản', exact: true })).toBeVisible()
  await expect(page.getByRole('navigation')).not.toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: 'test-results/create-mobile.png', fullPage: true })
})
test('desktop screens render without runtime errors', async ({ page }) => {
  const errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.setViewportSize({ width: 1440, height: 1000 })
  await mockAuth(page)
  await page.goto('/login')
  await page.screenshot({ path: 'test-results/login-desktop.png', fullPage: true })
  await login(page)
  await expect(page.getByRole('heading', { name: 'Tổng quan', exact: true })).toBeVisible()
  await page.screenshot({ path: 'test-results/dashboard-desktop.png', fullPage: true })
  await page.getByRole('navigation').getByRole('link', { name: 'Tài khoản của tôi' }).click()
  await expect(page.getByRole('heading', { name: 'Tài khoản của tôi', exact: true })).toBeVisible()
  await page.screenshot({ path: 'test-results/account-desktop.png', fullPage: true })
  expect(errors).toEqual([])
})
