import { expect, test } from '@playwright/test'

async function setup(page, role = 'EMPLOYEE', { failOnce = false, empty = false } = {}) {
  await page.clock.setFixedTime(new Date('2026-01-15T05:00:00Z'))
  await page.addInitScript(() => sessionStorage.setItem('company-hrm.refresh-token', 'refresh'))
  const unexpected = []
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    let body
    if (path.endsWith('/refresh')) body = { accessToken: 'access', refreshToken: 'refresh' }
    else if (path.endsWith('/me'))
      body = { id: 1, email: 'member@company.com', roles: [role], active: true }
    else if (path === '/api/v1/calendar') {
      expect(route.request().headers().authorization).toBe('Bearer access')
      if (failOnce) {
        failOnce = false
        await route.fulfill({ status: 503, json: { detail: 'Calendar unavailable' } })
        return
      }
      const year = Number(url.searchParams.get('year'))
      body = {
        year,
        availableYears: [2026, 2027],
        events: empty
          ? []
          : [
              {
                id: 1,
                title: 'Ngày nghỉ công ty',
                type: 'HOLIDAY',
                holidayKind: 'COMPANY_DAY_OFF',
                allDay: true,
                startDate: `${year}-01-02`,
                endDate: `${year}-01-02`,
                description: 'Thông báo từ API',
                status: 'PUBLISHED',
              },
              {
                id: 2,
                title: 'Đào tạo nội bộ',
                type: 'TRAINING',
                holidayKind: null,
                allDay: false,
                startAt: `${year}-02-28T16:00:00Z`,
                endAt: `${year}-03-01T03:00:00Z`,
                description: 'Nội dung đào tạo',
                location: 'Phòng A',
                status: 'CANCELLED',
              },
              {
                id: 3,
                title: 'Nghỉ bù',
                type: 'HOLIDAY',
                holidayKind: 'SUBSTITUTE_DAY_OFF',
                allDay: true,
                startDate: `${year}-05-03`,
                endDate: `${year}-05-03`,
                description: 'Ghi chú nghỉ bù',
                status: 'PUBLISHED',
              },
            ].map((event) => ({ timezone: 'Asia/Ho_Chi_Minh', audienceType: 'ALL', ...event })),
      }
    } else {
      unexpected.push(path)
      body = {}
    }
    await route.fulfill({ status: 200, json: body })
  })
  return unexpected
}

for (const role of ['EMPLOYEE', 'MANAGER', 'HR', 'ADMIN']) {
  test(`${role} can read calendar data from the API`, async ({ page }) => {
    const unexpected = await setup(page, role)
    await page.goto('/calendar')
    await page.locator('[data-date="2026-01-02"]').click()
    await expect(page.locator('.calendar-detail')).toContainText('Thông báo từ API')
    await expect(
      page.locator('.calendar-detail').getByRole('button', { name: 'Cập nhật', exact: true }),
    ).toHaveCount(0)
    expect(unexpected).toEqual([])
    await expect(page.getByRole('link', { name: 'Lịch nghỉ & sự kiện', exact: true })).toBeVisible()
  })
}

test('filters API types, renders cross-month events and preserves cancellation and details', async ({
  page,
}) => {
  await setup(page)
  await page.goto('/calendar')
  await expect(page.locator('[data-date="2026-01-02"]')).toHaveClass(/kind-COMPANY_DAY_OFF/)
  await page.getByLabel('Loại sự kiện').selectOption('TRAINING')
  await expect(page.locator('.calendar-event-card')).toHaveCount(1)
  await page.locator('.calendar-event-card').click()
  await expect(page.getByLabel('Tháng', { exact: true })).toHaveValue('2')
  await expect(page.locator('.calendar-detail')).toContainText('Đã hủy')
  await expect(page.locator('.calendar-detail')).toContainText('Phòng A')
  await expect(page.locator('.calendar-detail')).toContainText('Nội dung đào tạo')
  await expect(page.locator('.calendar-detail-range')).toContainText('23:00')
  await page.getByRole('button', { name: 'Tháng sau' }).click()
  await expect(page.locator('[data-date="2026-03-01"]')).toContainText('Đào tạo nội bộ')
  await page.getByLabel('Năm', { exact: true }).selectOption('2027')
  await expect(page.locator('.calendar-banner h2')).toHaveText('Lịch nghỉ & sự kiện 2027')
  await page.getByLabel('Loại sự kiện').selectOption('HOLIDAY')
  await page.getByLabel('Tháng', { exact: true }).selectOption('5')
  await page.locator('[data-date="2027-05-03"]').click()
  await expect(page.locator('[data-date="2027-05-03"]')).toHaveClass(/kind-SUBSTITUTE_DAY_OFF/)
  await expect(page.locator('.calendar-detail')).toContainText('Ghi chú nghỉ bù')
  await page.getByLabel('Loại sự kiện').selectOption('MEETING')
  await expect(page.getByRole('status')).toContainText('Chưa có sự kiện thuộc loại này')
  await expect(page.locator('.calendar-event-card')).toHaveCount(0)
})

test('an empty API calendar still renders the selected year', async ({ page }) => {
  await setup(page, 'EMPLOYEE', { empty: true })
  await page.goto('/calendar')
  await expect(page.locator('.calendar-banner h2')).toHaveText('Lịch nghỉ & sự kiện 2026')
  await expect(page.locator('.calendar-day')).toHaveCount(31)
  await expect(page.locator('.calendar-event-card')).toHaveCount(0)
  await expect(page.getByRole('status')).toContainText('Chưa có sự kiện')
})

test('API failure shows an error and retry fetches the real data', async ({ page }) => {
  await setup(page, 'EMPLOYEE', { failOnce: true })
  await page.goto('/calendar')
  await expect(page.getByRole('alert')).toContainText('Không thể tải lịch')
  await expect(page.locator('.calendar-event-card')).toHaveCount(0)
  await page.getByRole('button', { name: 'Thử lại' }).click()
  await expect(page.locator('.calendar-event-card')).toHaveCount(3)
})

test('calendar remains usable on mobile without horizontal overflow', async ({ page }) => {
  await setup(page, 'HR')
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/calendar')
  await page.locator('[data-date="2026-01-02"]').click()
  await expect(page.locator('.calendar-detail')).toContainText('Thông báo từ API')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})
