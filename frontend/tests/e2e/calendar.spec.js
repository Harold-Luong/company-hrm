import { expect, test } from '@playwright/test'

async function setup(
  page,
  role = 'EMPLOYEE',
  { failOnce = false, empty = false, leaves = [] } = {},
) {
  await page.clock.setFixedTime(new Date('2026-01-15T05:00:00Z'))
  await page.addInitScript(() => {
    if (!localStorage.getItem('company-hrm.session'))
      localStorage.setItem(
        'company-hrm.session',
        JSON.stringify({ id: 'test', refreshToken: 'refresh' }),
      )
  })
  const unexpected = []
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    let body
    if (path.endsWith('/refresh')) body = { accessToken: 'access', refreshToken: 'refresh' }
    else if (path.endsWith('/me'))
      body = { id: 1, email: 'member@company.com', roles: [role], active: true }
    else if (path.endsWith('/pending-count')) body = { count: 0 }
    else if (path === '/api/v1/notifications/stream') {
      return route.fulfill({ status: 503, json: {} })
    } else if (path === '/api/v1/leave/requests/calendar') {
      expect([...url.searchParams.keys()]).toEqual(['year'])
      expect(route.request().headers().authorization).toBe('Bearer access')
      const year = Number(url.searchParams.get('year'))
      body = leaves.filter(
        (row) => row.startDate <= `${year}-12-31` && row.endDate >= `${year}-01-01`,
      )
    } else if (path === '/api/v1/calendar') {
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

test('personal leave coexists with company events, shows half days and links to the request', async ({
  page,
}) => {
  const leaves = [
    {
      id: 'own-morning',
      leaveType: 'ANNUAL',
      startDate: '2026-01-02',
      endDate: '2026-01-02',
      period: 'MORNING',
      status: 'PENDING',
    },
    {
      id: 'own-range',
      leaveType: 'UNPAID',
      startDate: '2025-12-31',
      endDate: '2026-01-03',
      period: 'FULL_DAY',
      status: 'APPROVED',
    },
    {
      id: 'withdrawn',
      leaveType: 'ANNUAL',
      startDate: '2026-01-05',
      endDate: '2026-01-05',
      period: 'AFTERNOON',
      status: 'CANCELLED',
    },
    {
      id: 'rejected',
      leaveType: 'ANNUAL',
      startDate: '2026-01-06',
      endDate: '2026-01-06',
      period: 'FULL_DAY',
      status: 'REJECTED',
    },
  ]
  await setup(page, 'EMPLOYEE', { leaves })
  await page.goto('/calendar')
  await expect(page.locator('[data-date="2026-01-02"] .calendar-day-event')).toHaveCount(3)
  await page.getByLabel('Loại sự kiện').selectOption('PERSONAL_LEAVE')
  await expect(page.locator('.calendar-event-card')).toHaveCount(2)
  await expect(page.locator('[data-date="2026-01-01"]')).toContainText('Đã duyệt')
  await expect(page.locator('[data-date="2026-01-04"] .calendar-day-event')).toHaveCount(0)
  await page.locator('.calendar-event-card').filter({ hasText: 'Buổi sáng' }).click()
  await expect(page.locator('.calendar-detail')).toContainText('Chờ duyệt')
  await expect(page.getByRole('link', { name: 'Xem đơn nghỉ phép' })).toHaveAttribute(
    'href',
    '/leave/requests/own-morning',
  )
  leaves[0].status = 'APPROVED'
  await page.evaluate(() => window.dispatchEvent(new Event('hrm:requests-changed')))
  await expect(page.locator('.calendar-detail h3')).toContainText('Đã duyệt')
  leaves[0].status = 'CANCELLED'
  await page.evaluate(() => window.dispatchEvent(new Event('hrm:requests-changed')))
  await expect(page.locator('.calendar-event-card')).toHaveCount(1)
  await page.getByLabel('Năm', { exact: true }).selectOption('2027')
  await expect(page.locator('.calendar-event-card')).toHaveCount(0)
})

test('personal calendar loads every request and keeps company calendar usable on Leave failure', async ({
  page,
}) => {
  const leaves = Array.from({ length: 25 }, (_, index) => ({
    id: `own-${index}`,
    leaveType: 'UNPAID',
    startDate: `2026-01-${String(index + 1).padStart(2, '0')}`,
    endDate: `2026-01-${String(index + 1).padStart(2, '0')}`,
    period: 'AFTERNOON',
    status: 'PENDING',
  }))
  await setup(page, 'HR', { leaves })
  let fail = true
  await page.route('**/api/v1/leave/requests/calendar?*', (route) =>
    fail ? route.fulfill({ status: 503, json: {} }) : route.fallback(),
  )
  await page.goto('/calendar')
  await expect(page.getByRole('alert')).toContainText('Không thể cập nhật nghỉ phép cá nhân')
  await expect(page.locator('[data-date="2026-01-02"]')).toContainText('Ngày nghỉ công ty')
  fail = false
  await page.getByRole('button', { name: 'Tải lại nghỉ phép' }).click()
  await expect(page.getByRole('alert')).toHaveCount(0)
  await page.getByLabel('Loại sự kiện').selectOption('PERSONAL_LEAVE')
  await expect(page.locator('.calendar-event-card')).toHaveCount(25)
  await page.setViewportSize({ width: 390, height: 844 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('a delayed response from the previous year cannot repopulate personal leave', async ({
  page,
}) => {
  await setup(page)
  let release
  const held = new Promise((resolve) => {
    release = resolve
  })
  await page.route('**/api/v1/leave/requests/calendar?year=2026', async (route) => {
    await held
    await route.fulfill({
      json: [
        {
          id: 'old',
          leaveType: 'ANNUAL',
          startDate: '2026-12-31',
          endDate: '2027-01-02',
          period: 'FULL_DAY',
          status: 'PENDING',
        },
      ],
    })
  })
  await page.goto('/calendar')
  await page.getByLabel('Năm', { exact: true }).selectOption('2027')
  await page.getByLabel('Loại sự kiện').selectOption('PERSONAL_LEAVE')
  const response = page.waitForResponse('**/api/v1/leave/requests/calendar?year=2026')
  release()
  await response
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
