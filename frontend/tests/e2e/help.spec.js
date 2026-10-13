import { expect, test } from '@playwright/test'

async function session(page, role, loggedIn = true) {
  if (loggedIn)
    await page.addInitScript(() => {
      localStorage.setItem(
        'company-hrm.session',
        JSON.stringify({ id: 'help-test', refreshToken: 'refresh' }),
      )
    })
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/login'))
      return route.fulfill({ json: { data: { accessToken: 'access', refreshToken: 'refresh' } } })
    if (path.endsWith('/refresh'))
      return route.fulfill({ json: { accessToken: 'access', refreshToken: 'refresh' } })
    if (path.endsWith('/me'))
      return route.fulfill({
        json: { id: 'help-user', email: 'help@company.com', roles: [role], active: true },
      })
    if (path.endsWith('/pending-count')) return route.fulfill({ json: { count: 0 } })
    return route.fulfill({ json: {} })
  })
}

test('help supports discovery, search, formulas and mobile reading for employees', async ({
  page,
}, testInfo) => {
  await session(page, 'EMPLOYEE')
  await page.goto('/help/guide')
  await expect(page.getByRole('heading', { name: 'Hướng dẫn sử dụng', exact: true })).toBeVisible()
  await expect(
    page.getByRole('link', { name: 'Mở chức năng: Đọc & đối soát bảng công' }),
  ).toHaveCount(0)
  await page.getByText('Đã được duyệt đi trễ, vì sao vẫn trừ công?', { exact: true }).click()
  await expect(page.locator('details[open]')).toContainText('không cộng bù giờ làm')
  await page
    .getByRole('navigation', { name: 'Tài liệu sử dụng' })
    .getByRole('link', { name: 'Chức năng & công thức' })
    .click()
  await page.getByLabel('Nhóm chức năng', { exact: true }).selectOption('Nhân sự & lịch')
  await page.getByLabel('Tìm chức năng hoặc công thức').fill('phong ban')
  await expect(page.locator('.help-feature-list article')).toHaveCount(2)
  await expect(page.locator('.help-feature-list')).toContainText('Phòng ban')
  await expect(page.getByRole('link', { name: 'Mở chức năng: Phòng ban' })).toHaveCount(0)
  await page.getByLabel('Tìm chức năng hoặc công thức').fill('khongtimthay123')
  await expect(
    page.getByRole('heading', { name: 'Không tìm thấy chức năng phù hợp' }),
  ).toBeVisible()
  await page.getByRole('button', { name: 'Xóa bộ lọc' }).click()
  await expect(page.locator('.help-feature-list article')).toHaveCount(22)
  await page
    .getByRole('navigation', { name: 'Mục lục công thức' })
    .getByRole('link', { name: 'Giờ tính công đã xác định', exact: true })
    .click()
  await expect(page).toHaveURL(/#work-time$/)
  await expect(page.locator('#work-time')).toContainText('450 phút = 7 giờ 30 phút')
  await expect(page.locator('#work-time h3')).toBeInViewport()
  for (const path of ['/help/guide', '/help/features']) {
    await page.goto(path)
    for (const width of [1440, 390]) {
      await page.setViewportSize({ width, height: 900 })
      await expect(page.locator('h1')).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      )
      await page.screenshot({ path: testInfo.outputPath(`${path.split('/').at(-1)}-${width}.png`) })
    }
  }
  await page.getByRole('button', { name: 'Mở menu', exact: true }).click()
  await page
    .getByRole('navigation', { name: 'Điều hướng chính' })
    .getByRole('link', { name: 'Hướng dẫn sử dụng', exact: true })
    .click()
  await expect(page).toHaveURL(/\/help\/guide$/)
})

test('HR can open management from help and login returns to a help destination', async ({
  page,
}) => {
  await session(page, 'HR', false)
  await page.goto('/help/features')
  await expect(page).toHaveURL(/\/login\?redirect=/)
  await page.getByLabel('Email công việc', { exact: true }).fill('help@company.com')
  await page.getByLabel('Mật khẩu', { exact: true }).fill('Password123!')
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
  await expect(page).toHaveURL('http://127.0.0.1:5174/help/features')
  await expect(
    page.getByRole('link', { name: 'Mở chức năng: Bảng công & xuất CSV' }),
  ).toHaveAttribute('href', '/attendance/reports')
  await expect(
    page.getByRole('link', { name: 'Mở chức năng: Tạo & cấp tài khoản nhân viên' }),
  ).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Mở chức năng: Phòng ban' })).toHaveAttribute(
    'href',
    '/departments',
  )
})
