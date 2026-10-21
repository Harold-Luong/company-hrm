import { csrfResponse, isCsrfRequest } from './support/csrf.js'
import { expect, test } from '@playwright/test'

async function setup(page) {
  const state = { month: '2030-01', version: -1, closed: false }
  const writes = []
  const history = []
  await page.clock.setFixedTime(new Date('2030-02-02T05:00:00Z'))
  await page.addInitScript(() =>
    localStorage.setItem('company-hrm.session', JSON.stringify({ id: 'period-test' })),
  )
  await page.route('**/api/v1/**', async (route) => {
    if (isCsrfRequest(route)) return route.fulfill({ json: csrfResponse })
    const request = route.request(),
      path = new URL(request.url()).pathname
    if (path.endsWith('/refresh')) return route.fulfill({ json: { accessToken: 'access' } })
    if (path.endsWith('/auth/me'))
      return route.fulfill({
        json: {
          id: 'hr',
          email: 'hr@company.com',
          employeeId: '10000000-0000-0000-0000-000000000001',
          roles: ['HR'],
          active: true,
        },
      })
    if (path.endsWith('/pending-count')) return route.fulfill({ json: { count: 0 } })
    if (path.includes('/periods/')) {
      if (path.endsWith('/check-sources')) return route.fulfill({ json: { changed: true } })
      if (path.endsWith('/history')) return route.fulfill({ json: history })
      if (request.method() === 'POST') {
        writes.push({ path, match: request.headers()['if-match'], body: request.postDataJSON() })
        state.closed = path.endsWith('/close')
        state.version++
        history.unshift({
          ...state,
          reason: request.postDataJSON().reason,
          changedAt: '2030-02-02T05:00:00Z',
        })
      }
      return route.fulfill({ json: { ...state, month: path.split('/')[5] } })
    }
    if (path.endsWith('/reports')) return route.fulfill({ json: [] })
    if (path.endsWith('/stream')) return route.fulfill({ status: 503, json: {} })
    return route.fulfill({ json: { content: [], page: 0, totalPages: 0 } })
  })
  return { state, writes }
}

test('HR closes and reopens a whole-company month with reason, version and source check', async ({
  page,
}) => {
  const { state, writes } = await setup(page)
  await page.goto('/attendance/reports')
  await page.getByLabel('Tháng', { exact: true }).fill('2030-01')
  await page.getByRole('button', { name: 'Xem trạng thái kỳ' }).click()
  await expect(page.getByText('2030-01 · Đang mở')).toBeVisible()
  await expect(
    page.getByRole('button', { name: 'Chốt kỳ công toàn công ty', exact: true }),
  ).toBeDisabled()
  await page.getByLabel('Lý do chốt kỳ').fill('Đã đối soát toàn công ty')
  await page.getByRole('button', { name: 'Chốt kỳ công toàn công ty', exact: true }).click()
  await expect(page.getByText('2030-01 · Đã chốt')).toBeVisible()
  expect(writes[0]).toEqual({
    path: '/api/v1/attendance/periods/2030-01/close',
    match: '"-1"',
    body: { reason: 'Đã đối soát toàn công ty' },
  })
  await page.getByRole('button', { name: 'Kiểm tra thay đổi phép/ngày nghỉ' }).click()
  await expect(
    page.getByRole('region', { name: 'Chốt kỳ công' }).getByRole('status'),
  ).toContainText('Phép hoặc ngày nghỉ đã thay đổi sau chốt')
  await page.getByLabel('Lý do mở lại kỳ').fill('Cập nhật phép đã điều chỉnh')
  await page.getByRole('button', { name: 'Mở lại kỳ công toàn công ty', exact: true }).click()
  await expect(page.getByText('2030-01 · Đang mở')).toBeVisible()
  expect(writes[1].match).toBe('"0"')
  expect(state.closed).toBe(false)
  await page.getByText('Lịch sử chốt/mở kỳ').click()
  await expect(
    page.getByRole('listitem').filter({ hasText: 'Cập nhật phép đã điều chỉnh' }),
  ).toBeVisible()
})
