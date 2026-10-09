import { expect, test } from '@playwright/test'

const employeeId = '550e8400-e29b-41d4-a716-446655440000'
const profile = {
  id: employeeId,
  employeeCode: 'EMP001',
  firstName: 'An',
  lastName: 'Nguyễn Văn',
  personalEmail: 'an.nguyen@company.vn',
  gender: 'MALE',
  dateOfBirth: '1998-08-15',
  phone: '0901234567',
  address: 'TP. Hồ Chí Minh',
  status: 'ACTIVE',
  position: { name: 'Software Developer' },
  department: { name: 'IT Department' },
  hireDate: '2024-06-10',
  manager: null,
}

async function openProfile(page, employee = profile, linked = true) {
  const calls = { employeeRequests: 0 }
  await page.addInitScript(() => {
    localStorage.setItem(
      'company-hrm.session',
      JSON.stringify({ id: 'profile-test', refreshToken: 'refresh' }),
    )
  })
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/refresh'))
      return route.fulfill({ json: { accessToken: 'access', refreshToken: 'refresh' } })
    if (path.endsWith('/me'))
      return route.fulfill({
        json: {
          id: 42,
          active: true,
          employeeId: linked ? employeeId : null,
          email: 'login@company.vn',
          roles: ['EMPLOYEE'],
        },
      })
    if (path === `/api/v1/employees/${employeeId}`) {
      calls.employeeRequests++
      return employee
        ? route.fulfill({ json: employee })
        : route.fulfill({ status: 503, json: { message: 'Không tải được hồ sơ.' } })
    }
    return route.fulfill({ json: { count: 0, content: [] } })
  })
  await page.goto('/account')
  await expect(page.getByRole('heading', { name: 'Hồ sơ của tôi', exact: true })).toBeVisible()
  return calls
}

test('one profile tab bar defaults to personal and keeps Auth account data separate', async ({
  page,
}) => {
  const calls = await openProfile(page)
  const card = page.getByRole('region', { name: 'Hồ sơ của tôi' })
  await expect(card.getByRole('heading', { name: 'Nguyễn Văn An' })).toBeVisible()
  await expect(page.getByRole('tablist')).toHaveCount(1)
  await expect(page.getByRole('tab')).toHaveText([
    'Cá nhân',
    'Công việc',
    'Chấm công',
    'Nghỉ phép',
    'Tài khoản',
  ])
  await expect(page.getByRole('tab', { name: 'Cá nhân', exact: true })).toHaveAttribute(
    'aria-selected',
    'true',
  )
  expect(calls.employeeRequests).toBe(1)
  await expect(page).toHaveTitle('Hồ sơ của tôi | Company HRM')
  await page.getByRole('tab', { name: 'Tài khoản', exact: true }).click()
  const account = page.getByRole('tabpanel', { name: 'Tài khoản', exact: true })
  await expect(account.locator('dd').filter({ hasText: 'login@company.vn' })).toBeVisible()
  await expect(account.locator('.avatar')).toHaveCount(0)
  await expect(card.getByText(profile.personalEmail)).toHaveCount(0)
  await expect(card.getByText('Nguyễn Văn An')).toHaveCount(0)
  await expect(account.getByRole('button', { name: 'Đăng xuất tất cả phiên' })).toBeVisible()
  await page.screenshot({ path: '/tmp/my-profile-account.png', fullPage: true })
  await page.getByRole('tab', { name: 'Cá nhân', exact: true }).click()
  await expect(card.getByText(profile.personalEmail)).toBeVisible()
  expect(calls.employeeRequests).toBe(1)
})

test('shows personal data, changes profile tabs by keyboard', async ({ page }) => {
  await openProfile(page)
  const card = page.getByRole('region', { name: 'Hồ sơ của tôi' })
  await expect(card.getByRole('heading', { name: 'Nguyễn Văn An' })).toBeVisible()
  await expect(card.locator('header').getByText('Đang làm việc', { exact: true })).toBeVisible()
  const personal = card.getByRole('tabpanel', { name: 'Cá nhân', exact: true })
  await expect(personal.getByText('15/08/1998')).toBeVisible()
  await expect(personal.getByText('Nam', { exact: true })).toBeVisible()
  await expect(personal.getByText(profile.personalEmail)).toBeVisible()
  await expect(personal.getByText(profile.address)).toBeVisible()
  await expect(card.getByText('login@company.vn')).toHaveCount(0)
  await card.getByRole('tab', { name: 'Cá nhân', exact: true }).focus()
  await page.keyboard.press('ArrowRight')
  await expect(card.getByRole('tab', { name: 'Công việc' })).toBeFocused()
  await expect(
    card.getByRole('tabpanel', { name: 'Công việc' }).getByText('10/06/2024'),
  ).toBeVisible()
  await page.keyboard.press('Home')
  await expect(personal).toBeVisible()
  await expect(card.getByRole('tab', { name: 'Chấm công', exact: true })).toBeVisible()
  await expect(card.getByRole('tab', { name: 'Nghỉ phép', exact: true })).toBeVisible()
  await card.screenshot({ path: '/tmp/employee-personal-profile-desktop.png' })
})

test('mobile layout fits long values and shows contact details', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await openProfile(page, {
    ...profile,
    gender: null,
    dateOfBirth: null,
    personalEmail: `${'a'.repeat(80)}@company.vn`,
    contactRelative: 'Nguyễn Văn Bình',
    contactRelativePhone: '0912345678',
  })
  const card = page.getByRole('region', { name: 'Hồ sơ của tôi' })
  await expect(card.getByText('Nguyễn Văn Bình')).toBeVisible()
  await expect(card.getByText('0912345678')).toBeVisible()
  await expect(card.getByText('Chưa cập nhật', { exact: true })).toHaveCount(2)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
  await card.screenshot({ path: '/tmp/employee-personal-profile-mobile.png' })
})

test('handles a failed request without showing invented profile data', async ({ page }) => {
  await openProfile(page, null)
  await expect(page.getByRole('alert')).toContainText(
    'Không thể kết nối dịch vụ. Vui lòng thử lại sau.',
  )
  await expect(page.getByRole('heading', { name: 'Nguyễn Văn An' })).toHaveCount(0)
  await page.getByRole('tab', { name: 'Tài khoản', exact: true }).click()
  await expect(page.locator('dd').filter({ hasText: 'login@company.vn' })).toBeVisible()
  await expect(page.getByRole('alert')).toHaveCount(0)
})

test('handles accounts without an employee link', async ({ page }) => {
  await openProfile(page, null, false)
  await expect(page.getByText('Tài khoản chưa được liên kết với hồ sơ nhân viên.')).toBeVisible()
  await page.getByRole('tab', { name: 'Tài khoản', exact: true }).click()
  await expect(page.locator('dd').filter({ hasText: 'login@company.vn' })).toBeVisible()
})

test('attendance loads on demand, summarizes real values and changes month inside the profile', async ({
  page,
}) => {
  await page.clock.setFixedTime(new Date('2026-10-09T05:00:00Z'))
  await openProfile(page)
  const queries = []
  await page.route('**/api/v1/attendance/mine?**', (route) => {
    queries.push(Object.fromEntries(new URL(route.request().url()).searchParams))
    return route.fulfill({
      json: [
        { workMinutesCounted: 480, leaveDays: 0, overtime: { countedMinutes: 90 } },
        { workMinutesCounted: 240, leaveDays: 0.5, overtime: { countedMinutes: 30 } },
        { workMinutesCounted: null, leaveDays: 0, overtime: { countedMinutes: 0 } },
      ],
    })
  })
  expect(queries).toHaveLength(0)
  await page.getByRole('tab', { name: 'Chấm công', exact: true }).click()
  const panel = page.getByRole('tabpanel', { name: 'Chấm công', exact: true })
  await expect(panel.getByRole('heading', { name: 'Chấm công tháng 10/2026' })).toBeVisible()
  await expect(panel.getByText('12h 0p', { exact: true })).toBeVisible()
  await expect(panel.getByText('0,5', { exact: true })).toBeVisible()
  await expect(panel.getByText('2h 0p', { exact: true })).toBeVisible()
  expect(queries[0]).toEqual({ from: '2026-10-01', until: '2026-10-09' })
  await panel.getByLabel('Tháng chấm công').fill('2026-09')
  await expect.poll(() => queries.at(-1)).toEqual({ from: '2026-09-01', until: '2026-09-30' })
  await expect(panel.getByText('12h 0p', { exact: true })).toBeVisible()
  await expect(page).toHaveURL(/\/account$/)
  await page
    .getByRole('region', { name: 'Hồ sơ của tôi' })
    .screenshot({ path: '/tmp/employee-profile-attendance.png' })
})

test('leave tab gets its balance and requests from Leave without leaving the profile', async ({
  page,
}) => {
  await openProfile(page)
  const calls = []
  await page.route('**/api/v1/leave/requests/**', (route) => {
    const path = new URL(route.request().url()).pathname
    calls.push(path)
    if (path.endsWith('/balance'))
      return route.fulfill({
        json: { year: 2026, entitledDays: 12, usedDays: 3, remainingDays: 9 },
      })
    return route.fulfill({
      json: {
        content: [
          {
            id: 'leave-1',
            leaveType: 'ANNUAL',
            startDate: '2026-09-15',
            endDate: '2026-09-15',
            period: 'FULL_DAY',
            status: 'APPROVED',
          },
        ],
      },
    })
  })
  expect(calls).toHaveLength(0)
  await page.getByRole('tab', { name: 'Nghỉ phép', exact: true }).click()
  const panel = page.getByRole('tabpanel', { name: 'Nghỉ phép', exact: true })
  await expect(panel.getByText('9 ngày', { exact: true })).toBeVisible()
  await expect(panel.getByText('Đã duyệt', { exact: true })).toBeVisible()
  await expect(panel.getByRole('link', { name: 'Xem đơn và lịch sử' })).toHaveAttribute(
    'href',
    '/leave/requests/leave-1',
  )
  expect(calls).toEqual(
    expect.arrayContaining(['/api/v1/leave/requests/balance', '/api/v1/leave/requests/mine']),
  )
  await expect(page).toHaveURL(/\/account$/)
  await page
    .getByRole('region', { name: 'Hồ sơ của tôi' })
    .screenshot({ path: '/tmp/employee-profile-leave.png' })
})

test('module failures and empty data stay within their tabs and can be retried', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await openProfile(page)
  let failed = true
  await page.route('**/api/v1/attendance/mine?**', (route) =>
    failed
      ? route.fulfill({ status: 503, json: { message: 'Service unavailable' } })
      : route.fulfill({ json: [] }),
  )
  await page.getByRole('tab', { name: 'Chấm công', exact: true }).click()
  const attendance = page.getByRole('tabpanel', { name: 'Chấm công', exact: true })
  await expect(attendance.getByRole('alert')).toBeVisible()
  await expect(attendance.locator('.profile-module-stats')).toHaveCount(0)
  failed = false
  await attendance.getByRole('button', { name: 'Thử lại' }).click()
  await expect(attendance.getByText('Chưa có dữ liệu chấm công trong tháng này.')).toBeVisible()
  await page.route('**/api/v1/leave/requests/balance?**', (route) =>
    route.fulfill({ status: 503, json: {} }),
  )
  await page.route('**/api/v1/leave/requests/mine?**', (route) =>
    route.fulfill({ json: { content: [] } }),
  )
  await page.getByRole('tab', { name: 'Nghỉ phép', exact: true }).click()
  const leave = page.getByRole('tabpanel', { name: 'Nghỉ phép', exact: true })
  await expect(leave.getByRole('alert')).toContainText('Chưa tải được số dư phép năm')
  await expect(leave.getByText('Bạn chưa có đơn nghỉ phép.')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Nguyễn Văn An' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
  await page.getByRole('tab', { name: 'Cá nhân', exact: true }).click()
  await expect(
    page.getByRole('tabpanel', { name: 'Cá nhân', exact: true }).getByText(profile.personalEmail),
  ).toBeVisible()
})

for (const width of [1280, 390]) {
  test(`avatar account dropdown opens, closes and supports keyboard at ${width}px`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 900 })
    await openProfile(page)
    const trigger = page.getByRole('button', { name: 'Mở menu tài khoản', exact: true })
    const dropdown = page.getByRole('navigation', { name: 'Tài khoản', exact: true })
    await expect(
      page
        .getByRole('navigation', { name: 'Điều hướng chính' })
        .getByRole('link', { name: 'Hồ sơ của tôi' }),
    ).toHaveCount(0)
    await expect(trigger).toHaveAttribute('aria-expanded', 'false')
    await trigger.click()
    await expect(dropdown.getByRole('link', { name: 'Hồ sơ của tôi' })).toBeVisible()
    await expect(trigger).toHaveAttribute('aria-expanded', 'true')
    await page.keyboard.press('Tab')
    await expect(dropdown.getByRole('link')).toBeFocused()
    await page.keyboard.press('Escape')
    await expect(dropdown).toHaveCount(0)
    await expect(trigger).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(dropdown).toBeVisible()
    await page.getByRole('heading', { name: 'Hồ sơ của tôi', exact: true }).click()
    await expect(dropdown).toHaveCount(0)
    await trigger.click()
    await dropdown.getByRole('link', { name: 'Hồ sơ của tôi' }).click()
    await expect(dropdown).toHaveCount(0)
    await expect(page).toHaveURL(/\/account$/)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  })
}
