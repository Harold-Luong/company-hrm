import { expect, test } from '@playwright/test'

test('renders the authenticated API without company metadata, including events crossing years', async ({
  page,
}) => {
  const renderErrors = []
  page.on('pageerror', (error) => renderErrors.push(error.message))
  await page.clock.setFixedTime(new Date('2026-01-15T05:00:00Z'))
  await page.addInitScript(() => {
    if (!localStorage.getItem('company-hrm.session'))
      localStorage.setItem(
        'company-hrm.session',
        JSON.stringify({ id: 'test', refreshToken: 'refresh' }),
      )
  })
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    let body
    if (url.pathname.endsWith('/refresh')) body = { accessToken: 'access', refreshToken: 'refresh' }
    else if (url.pathname.endsWith('/me'))
      body = { id: 1, email: 'member@company.com', roles: ['EMPLOYEE'], active: true }
    else if (url.pathname === '/api/v1/calendar') {
      expect(route.request().headers().authorization).toBe('Bearer access')
      const year = Number(url.searchParams.get('year'))
      body = {
        year,
        availableYears: [2026, 2027],
        events:
          year === 2026
            ? [
                {
                  id: 1,
                  title: 'Nghỉ Tết',
                  type: 'HOLIDAY',
                  holidayKind: 'PUBLIC_HOLIDAY',
                  allDay: true,
                  startDate: '2026-01-01',
                  endDate: '2026-01-02',
                },
                {
                  id: 2,
                  title: 'Họp qua năm',
                  type: 'MEETING',
                  allDay: false,
                  startDate: null,
                  endDate: null,
                  startAt: '2025-12-31T16:00:00Z',
                  endAt: '2026-01-01T17:00:00Z',
                },
              ]
            : [],
      }
    } else throw new Error(`Unexpected API request: ${url.pathname}`)
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(body),
    })
  })
  await page.goto('/calendar')
  await expect(page.locator('.calendar-banner h2')).toHaveText('Lịch nghỉ & sự kiện 2026')
  await expect(page.locator('.calendar-event-card')).toHaveCount(2)
  await page.locator('.calendar-event-card').filter({ hasText: 'Họp qua năm' }).click()
  await expect(page.getByLabel('Tháng', { exact: true })).toHaveValue('1')
  await expect(page.locator('.calendar-detail h3')).toHaveText('Họp qua năm')
  await expect(page.locator('[data-date="2026-01-02"]')).not.toContainText('Họp qua năm')
  await page.locator('[data-date="2026-01-02"]').click()
  await expect(page.locator('.calendar-detail h3')).toHaveText('Nghỉ Tết')
  await expect(page.locator('.calendar-notes')).toHaveCount(0)
  await page.getByLabel('Năm', { exact: true }).selectOption('2027')
  await expect(page.locator('.calendar-banner h2')).toHaveText('Lịch nghỉ & sự kiện 2027')
  await expect(page.locator('.calendar-event-card')).toHaveCount(0)
  await expect(page.getByRole('status')).toContainText('Chưa có sự kiện')
  expect(renderErrors).toEqual([])
})
