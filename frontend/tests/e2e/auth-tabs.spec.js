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
  const state = { token: null, version: 0, expiredThrough: -1, used: [], logoutToken: null }
  const issue = () => {
    state.version++
    state.token = `refresh-${state.version}`
    return { accessToken: `access-${state.version}`, refreshToken: state.token }
  }
  await context.route('**/api/v1/auth/**', async (route) => {
    const request = route.request()
    const endpoint = new URL(request.url()).pathname.split('/').pop()
    if (endpoint === 'login') return route.fulfill({ json: { data: issue() } })
    if (endpoint === 'refresh') {
      const token = request.postDataJSON().refreshToken
      state.used.push(token)
      if (!state.token || token !== state.token)
        return route.fulfill({ status: 401, json: { message: 'Invalid refresh token' } })
      // Rotate before replying, like the real backend's single-use token contract.
      const tokens = issue()
      if (state.beforeRefreshReply) await state.beforeRefreshReply()
      return route.fulfill({ json: tokens })
    }
    if (endpoint === 'me') {
      const version = Number(request.headers().authorization?.replace('Bearer access-', ''))
      if (!version || version <= state.expiredThrough)
        return route.fulfill({ status: 401, json: { message: 'Expired access token' } })
      if (state.beforeMeReply) await state.beforeMeReply()
      return route.fulfill({ json: user })
    }
    if (endpoint === 'logout' || endpoint === 'logout-all') {
      if (endpoint === 'logout') state.logoutToken = request.postDataJSON().refreshToken
      state.token = null
      return route.fulfill({ json: { message: 'Logged out' } })
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
