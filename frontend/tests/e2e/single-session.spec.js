import { csrfResponse, isCsrfRequest } from './support/csrf.js'
import { expect, test } from '@playwright/test'

test('a new browser login replaces the old session while an Auth outage preserves it', async ({
  browser,
  baseURL,
}) => {
  const first = await browser.newContext({ baseURL })
  const second = await browser.newContext({ baseURL })
  const state = { sid: 0, unavailable: false }
  const user = {
    id: 7,
    employeeId: null,
    email: 'member@example.com',
    active: true,
    roles: ['EMPLOYEE'],
  }
  const tokens = () => ({
    accessToken: `access-${state.sid}`,
    refreshToken: `refresh-${state.sid}`,
  })
  try {
    for (const context of [first, second]) {
      await context.route('**/api/v1/auth/**', async (route) => {
        if (isCsrfRequest(route)) return route.fulfill({ json: csrfResponse })
        const request = route.request()
        const endpoint = new URL(request.url()).pathname.split('/').pop()
        if (endpoint === 'login') {
          state.sid++
          return route.fulfill({
            json: { data: { accessToken: tokens().accessToken } },
            headers: {
              'Set-Cookie': `hrm-refresh-dev=${tokens().refreshToken}; HttpOnly; SameSite=Strict; Path=/`,
            },
          })
        }
        if (state.unavailable) return route.fulfill({ status: 503, json: {} })
        const valid =
          endpoint === 'refresh'
            ? (await request.headerValue('cookie'))?.includes(
                `hrm-refresh-dev=${tokens().refreshToken}`,
              )
            : request.headers().authorization === `Bearer ${tokens().accessToken}`
        if (!valid)
          return route.fulfill({ status: 401, json: { message: 'Session replaced or signed out' } })
        if (endpoint === 'session') return route.fulfill({ status: 204 })
        return route.fulfill({
          json: endpoint === 'refresh' ? { accessToken: tokens().accessToken } : user,
        })
      })
    }
    const oldPage = await first.newPage()
    const newPage = await second.newPage()
    const login = async (page) => {
      await page.goto('/login?redirect=/account')
      await page.getByLabel('Email công việc', { exact: true }).fill(user.email)
      await page.getByLabel('Mật khẩu', { exact: true }).fill('password')
      await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
      await expect(page).toHaveURL((url) => url.pathname === '/account')
    }
    await login(oldPage)
    await oldPage.bringToFront()
    state.unavailable = true
    const outageResponse = oldPage.waitForResponse(
      (response) => response.url().endsWith('/api/v1/auth/session') && response.status() === 503,
    )
    await oldPage.evaluate(() => window.dispatchEvent(new Event('focus')))
    await (await outageResponse).finished()
    await expect(oldPage).toHaveURL((url) => url.pathname === '/account')
    expect(await oldPage.evaluate(() => localStorage.getItem('company-hrm.session'))).not.toBeNull()
    state.unavailable = false
    await login(newPage)
    await oldPage.bringToFront()
    await oldPage.evaluate(() => window.dispatchEvent(new Event('focus')))
    await expect(oldPage).toHaveURL(/\/login/)
    await expect(oldPage.getByRole('status')).toContainText('tài khoản đã đăng nhập ở nơi khác')
    expect(await oldPage.evaluate(() => localStorage.getItem('company-hrm.session'))).toBeNull()
    await newPage.reload()
    await expect(newPage).toHaveURL((url) => url.pathname === '/account')
  } finally {
    await first.close()
    await second.close()
  }
})
