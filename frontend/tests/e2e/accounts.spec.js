import { expect, test } from '@playwright/test'
import { csrfResponse } from './support/csrf.js'

async function setup(page, roles = ['ADMIN']) {
  const actor = {
    id: 1,
    employeeId: '11111111-1111-4111-8111-111111111111',
    email: 'admin@example.com',
    active: true,
    roles,
  }
  const state = {
    account: {
      id: 2,
      employeeId: '22222222-2222-4222-8222-222222222222',
      email: 'hr@example.com',
      status: 'ACTIVE',
      activationPending: false,
      roles: ['HR'],
      version: 0,
      lastLoginAt: null,
    },
    writes: [],
    queries: [],
    history: [],
    failure: null,
  }
  await page.addInitScript(() =>
    localStorage.setItem('company-hrm.session', JSON.stringify({ id: 'admin-session' })),
  )
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      path = url.pathname
    const json = (body, status = 200) => route.fulfill({ json: body, status })
    if (path.endsWith('/auth/csrf')) return json(csrfResponse)
    if (path.endsWith('/auth/refresh')) return json({ accessToken: 'access' })
    if (path.endsWith('/auth/me')) return json(actor)
    if (path.endsWith('/auth/session')) return route.fulfill({ status: 204 })
    if (path.endsWith('/pending-count')) return json({ count: 0 })
    if (path.includes('/notifications')) return json({}, 503)
    if (path === '/api/v1/auth/accounts') {
      state.queries.push(Object.fromEntries(url.searchParams))
      const current = Number(url.searchParams.get('page') ?? 0)
      const matching =
        (!url.searchParams.get('search') ||
          state.account.email.includes(url.searchParams.get('search'))) &&
        (!url.searchParams.get('status') ||
          state.account.status === url.searchParams.get('status')) &&
        (!url.searchParams.get('role') ||
          state.account.roles.includes(url.searchParams.get('role')))
      return json({
        content: matching ? [state.account] : [],
        page: current,
        totalPages: matching ? 2 : 0,
        totalElements: matching ? 21 : 0,
      })
    }
    if (path.endsWith('/accounts/1')) return json({ ...state.account, ...actor })
    if (path.endsWith('/accounts/2')) return json(state.account)
    if (/\/accounts\/\d+\/audit$/.test(path))
      return json({
        content: state.history,
        page: 0,
        totalPages: state.history.length ? 1 : 0,
        totalElements: state.history.length,
      })
    if (path.endsWith('/accounts/2/actions')) {
      expect(request.headers().authorization).toBe('Bearer access')
      const command = request.postDataJSON()
      state.writes.push(command)
      if (state.failure === 'network') return route.abort('failed')
      if (state.failure) return json({ message: 'ACCOUNT_VERSION_CONFLICT' }, state.failure)
      expect(command.version).toBe(state.account.version)
      const before = structuredClone(state.account)
      if (command.action === 'DISABLE') state.account.status = 'DISABLED'
      if (command.action === 'ENABLE') state.account.status = 'ACTIVE'
      if (command.action === 'CHANGE_ROLES') state.account.roles = command.roles
      state.account.version++
      state.history.unshift({
        id: state.history.length + 1,
        actorEmail: actor.email,
        action: command.action,
        reason: command.reason,
        beforeStatus: before.status,
        afterStatus: state.account.status,
        beforeRoles: before.roles.join(','),
        afterRoles: state.account.roles.join(','),
        createdAt: '2026-10-21T01:00:00Z',
      })
      return json(state.account)
    }
    return json({})
  })
  return state
}
async function confirm(page, reason = 'Reviewed access') {
  await page.getByLabel('Lý do thay đổi', { exact: true }).fill(reason)
  await page.getByRole('checkbox', { name: /Tôi đã kiểm tra/ }).check()
  await page.getByRole('button', { name: 'Xác nhận thực hiện', exact: true }).click()
}

test('administrator filters and pages accounts, then opens details', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/accounts')
  await expect(page.getByRole('heading', { name: 'Quản trị tài khoản', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Trang sau', exact: true }).click()
  await expect.poll(() => state.queries.at(-1)?.page).toBe('1')
  await page.getByLabel('Tìm theo email', { exact: true }).fill('hr@')
  await page.getByLabel('Trạng thái', { exact: true }).selectOption('ACTIVE')
  await page.getByLabel('Vai trò', { exact: true }).selectOption('HR')
  await page.getByRole('button', { name: 'Áp dụng bộ lọc', exact: true }).click()
  await expect
    .poll(() => state.queries.at(-1))
    .toMatchObject({ search: 'hr@', status: 'ACTIVE', role: 'HR', page: '0' })
  await page.getByRole('link', { name: 'hr@example.com', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Chi tiết tài khoản', exact: true })).toBeVisible()
})

test('lock, unlock, role change and force sign-out require confirmation and update audit', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/accounts/2')
  const submit = page.getByRole('button', { name: 'Xác nhận thực hiện', exact: true })
  await expect(submit).toBeDisabled()
  for (const action of ['DISABLE', 'ENABLE', 'CHANGE_ROLES', 'REVOKE_SESSIONS']) {
    await page.getByLabel('Thao tác', { exact: true }).selectOption(action)
    if (action === 'CHANGE_ROLES') {
      await page.getByRole('checkbox', { name: 'Nhân sự', exact: true }).uncheck()
      await page.getByRole('checkbox', { name: 'Nhân viên', exact: true }).check()
    }
    await confirm(page)
    await expect(
      page.getByRole('status').filter({ hasText: 'Đã cập nhật tài khoản' }),
    ).toBeVisible()
    await expect(submit).toBeDisabled()
  }
  expect(state.writes.map((command) => command.version)).toEqual([0, 1, 2, 3])
  expect(state.writes[2].roles).toEqual(['EMPLOYEE'])
  expect(state.history).toHaveLength(4)
  await expect(page.locator('.account-history li')).toHaveCount(4)
})

for (const failure of [412, 'network']) {
  test(`a ${failure} write failure requires reload and fresh confirmation, without automatic retry`, async ({
    page,
  }) => {
    const state = await setup(page)
    state.failure = failure
    await page.goto('/accounts/2')
    await confirm(page)
    await expect(page.getByRole('alert')).toBeVisible()
    expect(state.writes).toHaveLength(1)
    await expect(
      page.getByRole('button', { name: 'Xác nhận thực hiện', exact: true }),
    ).toBeDisabled()
    state.account.version = 1
    state.failure = null
    await page.getByRole('button', { name: 'Tải lại', exact: true }).first().click()
    await expect(page.getByLabel('Lý do thay đổi', { exact: true })).toBeEnabled()
    await expect(page.getByRole('checkbox', { name: /Tôi đã kiểm tra/ })).not.toBeChecked()
    await confirm(page)
    await expect.poll(() => state.writes.length).toBe(2)
    expect(state.writes[1].version).toBe(1)
  })
}

test('self account is read-only and HR cannot open account administration', async ({ page }) => {
  await setup(page)
  await page.goto('/accounts/1')
  await expect(page.getByText(/Bạn không thể khóa hoặc thay đổi quyền/)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Xác nhận thực hiện' })).toHaveCount(0)
  await page.unrouteAll({ behavior: 'wait' })
  await setup(page, ['HR'])
  await page.goto('/accounts')
  await expect(page).toHaveURL(/\/forbidden$/)
  await expect(page.getByRole('link', { name: 'Quản trị tài khoản', exact: true })).toHaveCount(0)
})

test('account administration renders in English on mobile without horizontal overflow', async ({
  page,
}) => {
  await setup(page)
  await page.addInitScript(() => localStorage.setItem('locale', 'en'))
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/accounts/2')
  await expect(page.getByRole('heading', { name: 'Account details', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Confirm action', exact: true })).toBeDisabled()
  await expect(page.getByLabel('Reason for change', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
})
