import { csrfResponse, isCsrfRequest } from './support/csrf.js'
import { expect, test } from '@playwright/test'

const user = {
  id: 7,
  employeeId: '550e8400-e29b-41d4-a716-446655440000',
  email: 'member@company.com',
  active: true,
  roles: ['EMPLOYEE'],
}

async function setup(context, page) {
  await context.route(`**/api/v1/employees/${user.employeeId}`, (route) =>
    route.fulfill({
      json: {
        id: user.employeeId,
        employeeCode: 'EMP001',
        firstName: 'An',
        lastName: 'Nguyễn',
        personalEmail: 'personal@example.com',
        status: 'ACTIVE',
      },
    }),
  )
  const state = {
    token: null,
    version: 0,
    expiredThrough: -1,
    used: [],
    rejected: [],
    logoutToken: null,
  }
  const issue = () => {
    state.version++
    state.token = `refresh-${state.version}`
    return { accessToken: `access-${state.version}` }
  }
  await context.route('**/api/v1/auth/**', async (route) => {
    if (isCsrfRequest(route))
      return route.fulfill({
        json: { ...csrfResponse, sessionPresent: !!state.token },
        headers: { 'Set-Cookie': 'hrm-csrf-dev=test-csrf; HttpOnly; SameSite=Strict; Path=/' },
      })
    const request = route.request()
    const endpoint = new URL(request.url()).pathname.split('/').pop()
    const cookie = (value) =>
      `hrm-refresh-dev=${value}; HttpOnly; SameSite=Strict; Path=/${value ? '' : '; Max-Age=0'}`
    if (request.method() === 'POST') {
      expect(request.headers()['x-xsrf-token']).toBe('test-csrf')
      expect(await request.headerValue('cookie')).toContain('hrm-csrf-dev=test-csrf')
    }
    if (endpoint === 'login')
      return route.fulfill({
        json: { data: issue() },
        headers: { 'Set-Cookie': cookie(state.token) },
      })
    if (endpoint === 'refresh') {
      expect(request.postData()).toBeNull()
      const token = (await request.headerValue('cookie'))?.match(/hrm-refresh-dev=([^;]+)/)?.[1]
      if (!state.token || token !== state.token) {
        state.rejected.push(token)
        return route.fulfill({ status: 401, json: { message: 'Invalid refresh token' } })
      }
      state.used.push(token)
      // Rotate before replying, like the real backend's single-use token contract.
      const tokens = issue()
      if (state.beforeRefreshReply) await state.beforeRefreshReply()
      return route.fulfill({ json: tokens, headers: { 'Set-Cookie': cookie(state.token) } })
    }
    if (endpoint === 'me') {
      const version = Number(request.headers().authorization?.replace('Bearer access-', ''))
      if (!version || version <= state.expiredThrough)
        return route.fulfill({ status: 401, json: { message: 'Expired access token' } })
      if (state.beforeMeReply) await state.beforeMeReply()
      return route.fulfill({ json: user })
    }
    if (endpoint === 'logout' || endpoint === 'logout-all') {
      if (endpoint === 'logout')
        state.logoutToken = (await request.headerValue('cookie'))?.match(
          /hrm-refresh-dev=([^;]+)/,
        )?.[1]
      state.token = null
      return route.fulfill({
        json: { message: 'Logged out' },
        headers: { 'Set-Cookie': cookie('') },
      })
    }
    return route.fulfill({ json: { message: 'OK' } })
  })
  await page.goto('/account')
  await page.getByLabel('Email công việc', { exact: true }).fill(user.email)
  await page.getByLabel('Mật khẩu', { exact: true }).fill('password')
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Hồ sơ của tôi', exact: true })).toBeVisible()
  return state
}

const loadUser = (page) =>
  page.evaluate(async () => {
    const { auth } = await import('/src/auth/session.js')
    try {
      await auth.loadUser()
      return true
    } catch {
      return false
    }
  })

test('new tabs share login and simultaneous refreshes use distinct current tokens', async ({
  context,
  page,
}) => {
  const state = await setup(context, page)
  const second = await context.newPage()
  const third = await context.newPage()
  await Promise.all([second.goto('/account'), third.goto('/account')])
  for (const tab of [second, third])
    await expect(tab.getByRole('heading', { name: 'Hồ sơ của tôi', exact: true })).toBeVisible()
  state.expiredThrough = state.version
  expect(await Promise.all([loadUser(page), loadUser(second), loadUser(third)])).toEqual([
    true,
    true,
    true,
  ])
  expect(new Set(state.used).size).toBe(state.used.length)
  expect(state.used).toHaveLength(5)
  expect(state.rejected).toEqual([])
  expect(await page.evaluate(() => JSON.stringify([localStorage, sessionStorage]))).not.toContain(
    'refresh-',
  )
  expect(await page.evaluate(() => document.cookie)).not.toContain('hrm-refresh-dev')
  expect(
    (await context.cookies()).find((cookie) => cookie.name === 'hrm-refresh-dev')?.httpOnly,
  ).toBe(true)
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Hồ sơ của tôi', exact: true })).toBeVisible()
})

for (const allSessions of [false, true]) {
  test(`logout${allSessions ? '-all' : ''} signs out every open tab`, async ({ context, page }) => {
    await setup(context, page)
    const second = await context.newPage()
    await second.goto('/account')
    await expect(second.getByRole('heading', { name: 'Hồ sơ của tôi', exact: true })).toBeVisible()
    await page.evaluate(async (all) => {
      const { auth } = await import('/src/auth/session.js')
      await auth.logout(all)
    }, allSessions)
    for (const tab of [page, second]) {
      await expect(tab).toHaveURL(/\/login/)
      expect(await tab.evaluate(() => localStorage.getItem('company-hrm.session'))).toBeNull()
    }
  })
}

test('logout waits for another tab to rotate and revokes its latest token', async ({
  context,
  page,
}) => {
  const state = await setup(context, page)
  const second = await context.newPage()
  await second.goto('/account')
  await expect(second.getByRole('heading', { name: 'Hồ sơ của tôi', exact: true })).toBeVisible()
  let release
  state.beforeRefreshReply = () =>
    new Promise((resolve) => {
      release = resolve
    })
  state.expiredThrough = state.version
  const refresh = loadUser(second)
  await expect.poll(() => typeof release).toBe('function')
  const logout = page.evaluate(async () => {
    const { auth } = await import('/src/auth/session.js')
    await auth.logout()
  })
  release()
  await Promise.all([refresh, logout])
  expect(state.logoutToken).toBe(`refresh-${state.version}`)
  await expect(page).toHaveURL(/\/login/)
  await expect(second).toHaveURL(/\/login/)
})

test('an old profile response cannot restore a tab after logout elsewhere', async ({
  context,
  page,
}) => {
  const state = await setup(context, page)
  const second = await context.newPage()
  await second.goto('/account')
  await expect(second.getByRole('heading', { name: 'Hồ sơ của tôi', exact: true })).toBeVisible()
  let release
  state.beforeMeReply = () =>
    new Promise((resolve) => {
      release = resolve
    })
  const pending = loadUser(second)
  await expect.poll(() => typeof release).toBe('function')
  await page.getByRole('button', { name: 'Mở menu tài khoản' }).click()
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click()
  await expect
    .poll(() => page.evaluate(() => localStorage.getItem('company-hrm.session')))
    .toBeNull()
  state.beforeMeReply = null
  release()
  expect(await pending).toBe(false)
  await expect(second).toHaveURL(/\/login/)
  expect(await second.evaluate(() => localStorage.getItem('company-hrm.session'))).toBeNull()
})

test('cookie session restores after storage is cleared; offline logout remains signed out on reload', async ({
  context,
  page,
}) => {
  const state = await setup(context, page)
  await page.evaluate(() => localStorage.clear())
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Hồ sơ của tôi', exact: true })).toBeVisible()
  await page.route('**/api/v1/auth/logout', (route) => route.abort())
  await page.evaluate(async () => {
    const { auth } = await import('/src/auth/session.js')
    try {
      await auth.logout()
    } catch {
      /* offline */
    }
  })
  expect(state.token).not.toBeNull()
  await page.reload()
  await expect(page).toHaveURL(/\/login/)
  await expect(page.getByRole('heading', { name: 'Đăng nhập workspace' })).toBeVisible()
})
