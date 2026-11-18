import { expect, test } from '@playwright/test'

async function enter(page, name = 'Quản trị viên') {
  await page.goto('/')
  await page.getByRole('button', { name, exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Tổng quan', exact: true })).toBeVisible()
}

test('all customer demo pages work without backend traffic', async ({ page }) => {
  const errors = [],
    apiCalls = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('request', (request) => {
    if (new URL(request.url()).pathname.startsWith('/api/')) apiCalls.push(request.url())
  })
  await enter(page)
  for (const path of [
    '/',
    '/account',
    '/employees',
    '/employees/new',
    '/departments',
    '/positions',
    '/leave',
    '/leave/inbox',
    '/calendar',
    '/calendar-events',
    '/calendar-events/new',
    '/attendance',
    '/attendance/requests',
    '/attendance/requests/inbox',
    '/attendance/corrections',
    '/attendance/overtime',
    '/attendance/shifts',
    '/attendance/schedules',
    '/attendance/reports',
    '/accounts',
    '/accounts/2',
    '/accounts/new',
    '/services',
    '/help/guide',
    '/help/features',
  ]) {
    await page.goto(`/#${path}`)
    await expect(page.locator('h1')).toBeVisible()
    await expect(page.locator('.loading-screen')).toHaveCount(0)
    await expect(page.locator('.alert-error')).toHaveCount(0)
    await expect(page).not.toHaveURL(/connection-error|forbidden/)
  }
  await page.goto('/#/employees')
  await expect(page.getByText('Trần Minh Khang', { exact: true })).toBeVisible()
  await expect(page.getByText('NV001', { exact: true })).toBeVisible()
  expect(errors).toEqual([])
  expect(apiCalls).toEqual([])
})

test('role switching, persistence, reset and mobile controls', async ({ page }) => {
  await enter(page)
  await page.goto('/#/departments/new')
  await page.getByRole('textbox', { name: 'Mã phòng ban' }).fill('DEMO')
  await page.getByRole('textbox', { name: 'Tên phòng ban' }).fill('Phòng demo khách hàng')
  await page.getByRole('button', { name: 'Tạo mới', exact: true }).click()
  await expect(page).toHaveURL(/departments\/[0-9a-f-]{36}/)
  await page.goto('/#/departments')
  await expect(page.getByText('Phòng demo khách hàng', { exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByText('Phòng demo khách hàng', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Nhân viên', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Tổng quan', exact: true })).toBeVisible()
  await page.goto('/#/employees')
  await expect(page).toHaveURL(/forbidden/)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('button', { name: 'Khôi phục dữ liệu mẫu', exact: true }).click()
  await page.getByRole('button', { name: 'Khôi phục', exact: true }).click()
  await expect(page).toHaveURL(/login/)
  await page.getByRole('button', { name: 'Quản trị viên', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Tổng quan', exact: true })).toBeVisible()
  await page.goto('/#/departments')
  await expect(page.getByText('Phòng demo khách hàng', { exact: true })).toHaveCount(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
})
