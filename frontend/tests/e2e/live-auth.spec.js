import { expect, test } from '@playwright/test'
// Live requests may carry real credentials; do not capture network traces.
test.use({ trace: 'off' })
test('live Auth: login, /me, restore with refresh, role-gated page and logout', async ({
  page,
}) => {
  test.skip(
    process.env.HRM_LIVE_AUTH !== '1',
    'Opt-in: requires a running Auth backend and a test account.',
  )
  const email = process.env.HRM_TEST_EMAIL
  const password = process.env.HRM_TEST_PASSWORD
  if (!email || !password)
    throw new Error('Set HRM_TEST_EMAIL and HRM_TEST_PASSWORD for the test account.')
  const endpoints = []
  page.on('response', (response) => {
    const path = new URL(response.url()).pathname
    if (path.startsWith('/api/v1/auth/')) endpoints.push(`${response.status()} ${path}`)
  })
  await page.goto('/login')
  await page.getByLabel('Email công việc', { exact: true }).fill(email)
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Tổng quan', exact: true })).toBeVisible()
  await page.getByRole('navigation').getByRole('link', { name: 'Tài khoản của tôi' }).click()
  await expect(page.getByRole('heading', { name: email, exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: email, exact: true })).toBeVisible()
  const createLink = page.getByRole('navigation').getByRole('link', { name: 'Tạo tài khoản' })
  if (await createLink.count()) {
    await createLink.click()
    await expect(page.getByRole('heading', { name: 'Tạo tài khoản', exact: true })).toBeVisible()
  } else {
    await page.goto('/accounts/new')
    await expect(page.getByRole('heading', { name: 'Bạn chưa có quyền truy cập' })).toBeVisible()
  }
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Đăng nhập workspace' })).toBeVisible()
  await expect(page.getByRole('status')).toContainText('Bạn đã đăng xuất thành công.')
  expect(endpoints).toContain('200 /api/v1/auth/login')
  expect(endpoints).toContain('200 /api/v1/auth/me')
  expect(endpoints).toContain('200 /api/v1/auth/refresh')
  expect(endpoints).toContain('200 /api/v1/auth/logout')
})
