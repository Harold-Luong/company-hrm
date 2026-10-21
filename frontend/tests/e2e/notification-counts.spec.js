import { csrfResponse, isCsrfRequest } from './support/csrf.js'
import { expect, test } from '@playwright/test'

async function setup(page, role = 'HR', stream = true) {
  const state = { attendance: 1, leave: 2, failLeave: false, reads: [] }
  await page.addInitScript(
    ({ stream }) => {
      localStorage.setItem('company-hrm.session', JSON.stringify({ id: 'test' }))
      const originalFetch = window.fetch
      window.notificationConnections = 0
      window.fetch = (url, init) => {
        if (url !== '/api/v1/notifications/stream' || !stream) return originalFetch(url, init)
        window.notificationConnections++
        window.notificationAuthorization = init.headers.Authorization
        const body = new ReadableStream({
          start(controller) {
            window.notificationController = controller
            init.signal.addEventListener(
              'abort',
              () => {
                window.notificationAborted = true
                controller.error(new DOMException('Aborted', 'AbortError'))
              },
              { once: true },
            )
          },
        })
        return Promise.resolve(
          new Response(body, { headers: { 'Content-Type': 'text/event-stream' } }),
        )
      }
    },
    { stream },
  )
  await page.route('**/api/v1/**', async (route) => {
    if (isCsrfRequest(route)) return route.fulfill({ json: csrfResponse })
    const path = new URL(route.request().url()).pathname
    state.reads.push(path)
    if (path.endsWith('/refresh')) return route.fulfill({ json: { accessToken: 'access' } })
    if (path.endsWith('/me'))
      return route.fulfill({
        json: {
          id: 'reviewer',
          employeeId: '10000000-0000-0000-0000-000000000001',
          email: 'reviewer@company.com',
          active: true,
          roles: [role],
        },
      })
    if (path.endsWith('/logout')) return route.fulfill({ status: 204 })
    if (path.endsWith('/stream')) return route.fulfill({ status: 503, json: {} })
    if (path.endsWith('/pending-count')) {
      expect(route.request().headers().authorization).toBe('Bearer access')
      const leave = path.includes('/leave/')
      return route.fulfill({
        status: leave && state.failLeave ? 503 : 200,
        json: { count: leave ? state.leave : state.attendance },
      })
    }
    return route.fulfill({ json: { content: [], page: 0, totalPages: 0, totalElements: 0 } })
  })
  return state
}
const badge = (page, type) =>
  page.locator(
    `a[href="/${type === 'leave' ? 'leave' : 'attendance/requests'}/inbox"] .nav-notification`,
  )
async function emit(page, revision) {
  await page.evaluate((revision) => {
    // Include heartbeat and split CRLF/data across network chunks.
    const text = `: heartbeat\r\n\r\nevent: notifications\r\ndata: ${JSON.stringify({ revision, readCount: 0, unreadCount: 1 })}\r\n\r\n`
    const bytes = new TextEncoder().encode(text)
    window.notificationController.enqueue(bytes.slice(0, 17))
    window.notificationController.enqueue(bytes.slice(17))
  }, revision)
}

test('review badges update from SSE without navigation and reconnect after stream completion', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/account')
  await expect(badge(page, 'attendance')).toHaveText('1')
  await expect(badge(page, 'leave')).toHaveText('2')
  await expect.poll(() => page.evaluate(() => window.notificationConnections)).toBe(1)
  expect(await page.evaluate(() => window.notificationAuthorization)).toBe('Bearer access')
  state.attendance = 4
  state.leave = 3
  await emit(page, 1)
  await expect(badge(page, 'attendance')).toHaveText('4')
  await expect(badge(page, 'leave')).toHaveText('3')
  state.attendance = 0
  state.failLeave = true
  await emit(page, 2)
  await expect(badge(page, 'attendance')).toHaveCount(0)
  await expect(badge(page, 'leave')).toHaveText('3')
  await page.evaluate(() => window.notificationController.close())
  await expect.poll(() => page.evaluate(() => window.notificationConnections)).toBe(2)
  state.attendance = 125
  await emit(page, 3)
  await expect(badge(page, 'attendance')).toHaveText('99+')
  await expect(page).toHaveURL(/\/account$/)
  await page.getByRole('button', { name: 'Mở menu tài khoản' }).click()
  await page.getByRole('button', { name: /Đăng xuất/ }).click()
  await expect(page).toHaveURL(/\/login/)
  expect(await page.evaluate(() => window.notificationAborted)).toBe(true)
})

test('counts keep polling when notification service is unavailable', async ({ page }) => {
  const state = await setup(page, 'ADMIN', false)
  await page.clock.install()
  await page.goto('/account')
  await expect(badge(page, 'attendance')).toHaveText('1')
  state.attendance = 7
  await page.clock.fastForward(30_000)
  await expect(badge(page, 'attendance')).toHaveText('7')
})

test('employees do not connect to reviewer counters or show review badges', async ({ page }) => {
  const state = await setup(page, 'EMPLOYEE')
  await page.goto('/account')
  await expect(page.getByRole('heading', { name: 'Hồ sơ của tôi' })).toBeVisible()
  await expect(badge(page, 'attendance')).toHaveCount(0)
  expect(state.reads.some((path) => path.endsWith('/pending-count'))).toBe(false)
  expect(await page.evaluate(() => window.notificationConnections)).toBe(0)
})
