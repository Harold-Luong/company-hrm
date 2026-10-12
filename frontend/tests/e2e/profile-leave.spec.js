import { expect, test } from '@playwright/test'

const employeeId = '550e8400-e29b-41d4-a716-446655440000'
const request = {
  id: '550e8400-e29b-41d4-a716-446655440001',
  leaveType: 'ANNUAL',
  startDate: '2026-10-15',
  endDate: '2026-10-15',
  period: 'MORNING',
  totalUnits: 1,
  status: 'PENDING',
}
const balance = { year: 2026, entitledDays: 12, usedDays: 2.5, remainingDays: 9.5 }
const list = (content = [request], page = 0, totalElements = content.length) => ({
  content,
  page,
  size: 5,
  totalElements,
  totalPages: Math.ceil(totalElements / 5),
})

async function setup(page, { linked = true, balanceHandler, listHandler } = {}) {
  await page.clock.setFixedTime(new Date('2026-10-12T05:00:00Z'))
  await page.addInitScript(() =>
    localStorage.setItem(
      'company-hrm.session',
      JSON.stringify({
        id: 'profile-leave-test',
        refreshToken: 'refresh',
      }),
    ),
  )
  const calls = { balance: [], list: [] }
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (path.endsWith('/refresh'))
      return route.fulfill({ json: { accessToken: 'access', refreshToken: 'refresh' } })
    if (path.endsWith('/me'))
      return route.fulfill({
        json: {
          id: 42,
          active: true,
          employeeId: linked ? employeeId : null,
          email: 'employee@company.vn',
          roles: ['EMPLOYEE'],
        },
      })
    if (path === `/api/v1/employees/${employeeId}`)
      return route.fulfill({
        json: {
          id: employeeId,
          employeeCode: 'EMP001',
          firstName: 'An',
          lastName: 'Nguyễn Văn',
          status: 'ACTIVE',
        },
      })
    if (path.endsWith('/leave/requests/balance')) {
      calls.balance.push(Object.fromEntries(url.searchParams))
      return balanceHandler ? balanceHandler(route, url) : route.fulfill({ json: balance })
    }
    if (path.endsWith('/leave/requests/mine')) {
      calls.list.push(Object.fromEntries(url.searchParams))
      return listHandler ? listHandler(route, url) : route.fulfill({ json: list() })
    }
    return route.fulfill({ json: { count: 0, content: [] } })
  })
  await page.goto('/account')
  await expect(page.getByRole('tab', { name: 'Nghỉ phép', exact: true })).toBeVisible()
  return calls
}
const openLeave = async (page) => {
  await page.getByRole('tab', { name: 'Nghỉ phép', exact: true }).click()
  return page.getByRole('tabpanel', { name: 'Nghỉ phép', exact: true })
}

test('loads personal leave on demand, changes balance year and validates input', async ({
  page,
}) => {
  const calls = await setup(page, {
    balanceHandler: (route, url) =>
      route.fulfill({
        json: {
          ...balance,
          year: Number(url.searchParams.get('year')),
          remainingDays: url.searchParams.get('year') === '2025' ? 8 : 9.5,
        },
      }),
  })
  expect(calls).toEqual({ balance: [], list: [] })
  const panel = await openLeave(page)
  await expect(panel.getByText('9,5 ngày', { exact: true })).toBeVisible()
  await expect(panel.getByText('2,5 ngày', { exact: true })).toBeVisible()
  await expect(panel.getByRole('listitem')).toContainText(/Buổi sáng\s+· 0,5 ngày/)
  expect(calls.balance).toEqual([{ year: '2026' }])
  expect(calls.list).toEqual([{ status: '', page: '0', size: '5' }])
  await panel.getByLabel('Năm phép').fill('2025')
  await expect(panel.getByText('8 ngày', { exact: true })).toBeVisible()
  expect(calls.list).toHaveLength(1)
  await panel.getByLabel('Năm phép').fill('1999')
  await expect(panel.getByRole('alert')).toContainText('Chọn năm hợp lệ')
  await expect(panel.locator('.profile-module-stats')).toHaveCount(0)
  expect(calls.balance).toHaveLength(2)
  await panel.getByLabel('Năm phép').fill('2026')
  await expect(panel.getByText('9,5 ngày', { exact: true })).toBeVisible()
  await expect(panel.getByRole('link', { name: 'Tạo đơn xin nghỉ' })).toHaveAttribute(
    'href',
    '/leave',
  )
  await expect(panel.getByRole('link', { name: 'Xem đơn và lịch sử' })).toHaveAttribute(
    'href',
    `/leave/requests/${request.id}`,
  )
  await expect(page).toHaveURL(/\/account$/)
  await panel.screenshot({ path: test.info().outputPath('profile-leave-desktop.png') })
  await page.setViewportSize({ width: 390, height: 844 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await panel.screenshot({ path: test.info().outputPath('profile-leave-populated-mobile.png') })
})

test('pages through own history and resets the page when filtering status', async ({ page }) => {
  const calls = await setup(page, {
    listHandler: (route, url) => {
      const selected = url.searchParams.get('status')
      const index = Number(url.searchParams.get('page'))
      return route.fulfill({
        json: selected
          ? list([{ ...request, status: selected, leaveType: 'UNPAID' }])
          : list(
              [{ ...request, id: `request-${index}`, status: index ? 'APPROVED' : 'PENDING' }],
              index,
              6,
            ),
      })
    },
  })
  const panel = await openLeave(page)
  await expect(panel.getByText('6 bản ghi · Trang 1 / 2')).toBeVisible()
  await panel.getByRole('button', { name: 'Trang sau' }).click()
  await expect(panel.getByText('6 bản ghi · Trang 2 / 2')).toBeVisible()
  await expect(panel.getByRole('button', { name: 'Trang sau' })).toBeDisabled()
  await panel.getByLabel('Trạng thái đơn').selectOption('CANCELLED')
  await expect(
    panel.getByText('Đã rút đơn', { exact: true }).filter({ visible: true }),
  ).toBeVisible()
  await expect(panel.getByText('Nghỉ không lương', { exact: true })).toBeVisible()
  expect(calls.list.at(-1)).toEqual({ status: 'CANCELLED', page: '0', size: '5' })
  expect(calls.balance).toHaveLength(1)
  await expect(panel.getByRole('button', { name: 'Trang sau' })).toHaveCount(0)
})

test('balance and history fail independently, retry and reload refreshed data', async ({
  page,
}) => {
  let balanceFailed = true
  let listFailed = false
  const calls = await setup(page, {
    balanceHandler: (route) =>
      balanceFailed ? route.fulfill({ status: 503, json: {} }) : route.fulfill({ json: balance }),
    listHandler: (route) =>
      listFailed ? route.fulfill({ status: 503, json: {} }) : route.fulfill({ json: list() }),
  })
  const panel = await openLeave(page)
  const summary = panel.getByRole('region', { name: 'Số dư phép năm' })
  const history = panel.getByRole('region', { name: 'Lịch sử nghỉ phép' })
  await expect(summary.getByRole('alert')).toBeVisible()
  await expect(history.getByRole('link', { name: 'Xem đơn và lịch sử' })).toBeVisible()
  balanceFailed = false
  await summary.getByRole('button', { name: 'Thử lại' }).click()
  await expect(summary.getByText('9,5 ngày', { exact: true })).toBeVisible()
  expect(calls.list).toHaveLength(1)
  listFailed = true
  await panel.getByRole('button', { name: 'Tải lại', exact: true }).click()
  await expect(history.getByRole('alert')).toBeVisible()
  await expect(summary.getByText('9,5 ngày', { exact: true })).toBeVisible()
  await expect(history.getByText('Bạn chưa có đơn nghỉ phép.')).toHaveCount(0)
  listFailed = false
  await history.getByRole('button', { name: 'Thử lại' }).click()
  await expect(history.getByRole('link', { name: 'Xem đơn và lịch sử' })).toBeVisible()
})

test('ignores an old balance response after the selected year changes', async ({ page }) => {
  let resolveOld
  let oldRoute
  await setup(page, {
    balanceHandler: async (route, url) => {
      if (url.searchParams.get('year') === '2026') {
        oldRoute = route
        await new Promise((resolve) => {
          resolveOld = resolve
        })
      }
      await route.fulfill({
        json: {
          ...balance,
          year: Number(url.searchParams.get('year')),
          remainingDays: url.searchParams.get('year') === '2026' ? 9.5 : 7,
        },
      })
    },
  })
  const panel = await openLeave(page)
  await expect.poll(() => Boolean(oldRoute)).toBe(true)
  await expect(panel.getByRole('link', { name: 'Xem đơn và lịch sử' })).toBeVisible()
  await panel.getByLabel('Năm phép').fill('2025')
  await expect(panel.getByText('7 ngày', { exact: true })).toBeVisible()
  const response = page.waitForResponse('**/leave/requests/balance?year=2026')
  resolveOld()
  await (await response).finished()
  await expect(panel.getByText('7 ngày', { exact: true })).toBeVisible()
  await expect(panel.getByText('9,5 ngày', { exact: true })).toHaveCount(0)
})

test('unlinked accounts see an explanation without requesting leave data', async ({ page }) => {
  const calls = await setup(page, { linked: false })
  const panel = await openLeave(page)
  await expect(panel.getByRole('status')).toContainText('Liên hệ bộ phận nhân sự')
  await expect(panel.getByLabel('Năm phép')).toHaveCount(0)
  await expect(panel.getByRole('link', { name: 'Tạo đơn xin nghỉ' })).toHaveCount(0)
  expect(calls).toEqual({ balance: [], list: [] })
})

test('empty and filtered empty histories fit on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await setup(page, { listHandler: (route) => route.fulfill({ json: list([]) }) })
  const panel = await openLeave(page)
  await expect(panel.getByText('Bạn chưa có đơn nghỉ phép.')).toBeVisible()
  await panel.getByLabel('Trạng thái đơn').selectOption('REJECTED')
  await expect(panel.getByText('Không có đơn nghỉ phép ở trạng thái này.')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await panel.screenshot({ path: test.info().outputPath('profile-leave-mobile.png') })
})
