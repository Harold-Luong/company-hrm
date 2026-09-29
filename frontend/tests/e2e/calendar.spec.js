import { expect, test } from '@playwright/test'

async function setup(page, role = 'EMPLOYEE') {
  await page.clock.setFixedTime(new Date('2026-01-15T05:00:00Z'))
  await page.addInitScript(() => sessionStorage.setItem('company-hrm.refresh-token', 'refresh'))
  const unexpected = []
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    let body
    if (path.endsWith('/refresh')) body = { accessToken: 'access', refreshToken: 'refresh' }
    else if (path.endsWith('/me'))
      body = { id: 1, email: 'member@company.com', roles: [role], active: true }
    else {
      unexpected.push(path)
      body = {}
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(body),
    })
  })
  return unexpected
}

for (const role of ['EMPLOYEE', 'MANAGER', 'HR', 'ADMIN']) {
  test(`${role} can view the calendar; only HR/Admin see unavailable management actions`, async ({
    page,
  }) => {
    const unexpected = await setup(page, role)
    await page.goto('/calendar')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Lịch nghỉ & sự kiện')
    await page.locator('[data-date="2026-01-02"]').click()
    const detail = page.locator('.calendar-detail')
    await expect(detail).toContainText('trừ vào ngày phép')
    if (['HR', 'ADMIN'].includes(role)) {
      await expect(detail.getByRole('button', { name: 'Cập nhật', exact: true })).toBeDisabled()
      await expect(detail.getByRole('button', { name: 'Xóa', exact: true })).toBeDisabled()
    } else {
      await expect(detail.getByRole('button', { name: 'Cập nhật', exact: true })).toHaveCount(0)
      await expect(detail.getByRole('button', { name: 'Xóa', exact: true })).toHaveCount(0)
    }
    expect(unexpected).toEqual([])
    await expect(page.getByRole('link', { name: 'Lịch nghỉ & sự kiện', exact: true })).toBeVisible()
  })
}

test('year/month filters render precise holiday details, cross-month events and empty categories', async ({
  page,
}) => {
  await setup(page)
  await page.goto('/calendar')
  await expect(page.locator('[data-date="2026-01-02"]')).toHaveClass(/kind-paid_leave_deduction/)
  await page.getByLabel('Tháng', { exact: true }).selectOption('2')
  await expect(page.locator('[data-date="2026-02-13"]')).toHaveClass(/kind-company_granted_holiday/)
  await expect(page.locator('[data-date="2026-02-16"]')).toHaveClass(/kind-statutory_holiday/)
  await page.locator('[data-date="2026-02-16"]').click()
  await expect(page.locator('.calendar-detail')).toContainText('5 ngày nghỉ Tết theo quy định')
  await expect(page.locator('.calendar-notes')).toContainText(
    'Ngày nghỉ du lịch cty chỉ mang tính chất tham khảo',
  )
  await page.screenshot({ path: 'test-results/calendar-desktop.png', fullPage: true })
  await page.getByLabel('Năm', { exact: true }).selectOption('2027')
  await expect(page.locator('.calendar-notes')).toHaveCount(0)
  await page.getByLabel('Tháng', { exact: true }).selectOption('5')
  await page.locator('[data-date="2027-05-03"]').click()
  await expect(page.locator('[data-date="2027-05-03"]')).toHaveClass(/kind-substitute_holiday/)
  await expect(page.locator('.calendar-detail')).toContainText('nghỉ bù cho ngày 01/05')
  await page.getByLabel('Loại sự kiện').selectOption('japan_national_holiday')
  await expect(page.locator('.calendar-event-card')).toHaveCount(2)
  await expect(page.locator('.calendar-day.has-events')).toHaveCount(0)
  await page.locator('.calendar-event-card').filter({ hasText: 'Sports Day' }).click()
  await expect(page.getByLabel('Tháng', { exact: true })).toHaveValue('10')
  await expect(page.locator('.calendar-detail')).toContainText('スポーツの日')
  await page.getByLabel('Loại sự kiện').selectOption('salary_day')
  await expect(page.getByRole('status')).toContainText('Chưa có sự kiện thuộc loại này')
  await expect(page.locator('.calendar-event-card')).toHaveCount(0)
})

test('calendar remains usable on mobile without horizontal overflow', async ({ page }) => {
  await setup(page, 'HR')
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/calendar')
  await page.getByLabel('Tháng', { exact: true }).selectOption('2')
  await page.locator('[data-date="2026-02-13"]').click()
  await expect(page.locator('.calendar-detail')).toContainText('3 ngày nghỉ cty cho thêm')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: 'test-results/calendar-mobile.png', fullPage: true })
})
