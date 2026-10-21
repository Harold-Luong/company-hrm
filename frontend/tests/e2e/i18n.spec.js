import { csrfResponse, isCsrfRequest } from './support/csrf.js'
import { expect, test } from '@playwright/test'
import vi from '../../src/locales/vi.json' with { type: 'json' }
test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/auth/csrf', (route) => route.fulfill({ json: csrfResponse }))
})

import en from '../../src/locales/en.json' with { type: 'json' }

async function mockSession(page) {
  await page.addInitScript(() => {
    if (!localStorage.getItem('company-hrm.session'))
      localStorage.setItem('company-hrm.session', JSON.stringify({ id: 'i18n-test' }))
  })
  await page.route('**/api/v1/**', (route) => {
    if (isCsrfRequest(route)) return route.fulfill({ json: csrfResponse })
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/refresh')) return route.fulfill({ json: { accessToken: 'access' } })
    if (path.endsWith('/me'))
      return route.fulfill({
        json: { id: 'i18n-user', email: 'user@company.com', roles: ['EMPLOYEE'], active: true },
      })
    if (path.endsWith('/requests/calendar')) return route.fulfill({ json: [] })
    if (path === '/api/v1/calendar')
      return route.fulfill({
        json: {
          year: 2026,
          availableYears: [2026],
          events: [
            {
              id: 1,
              title: 'Thông báo từ công ty',
              type: 'MEETING',
              allDay: true,
              startDate: '2026-01-15',
              endDate: '2026-01-15',
              status: 'PUBLISHED',
            },
          ],
        },
      })
    return route.fulfill({ json: {} })
  })
}
async function switchWorkspace(page, source, target) {
  await page.getByRole('button', { name: source.workspaceLayout.openAccountMenu }).click()
  await page
    .getByRole('button', {
      name: source.workspaceLayout[target === en ? 'switchToEnglish' : 'switchToVietnamese'],
    })
    .click()
  await page.getByRole('button', { name: target.workspaceLayout.openAccountMenu }).click()
}

test('login switches language without clearing inputs and persists after reload', async ({
  page,
}) => {
  await page.goto('/login')
  await page.getByLabel(vi.common.workEmail, { exact: true }).fill('user@company.com')
  await page.getByRole('button', { name: vi.workspaceLayout.switchToEnglish }).click()
  await expect(page.locator('h2')).toHaveText(en.login.title)
  await expect(page.getByLabel(en.common.workEmail, { exact: true })).toHaveValue(
    'user@company.com',
  )
  await expect(page.locator('html')).toHaveAttribute('lang', 'en')
  await expect(page).toHaveTitle(`${en.routeTitles.login} | ${en.common.appName}`)
  await page.reload()
  await expect(page.locator('h2')).toHaveText(en.login.title)
  await page.getByRole('button', { name: en.workspaceLayout.switchToVietnamese }).click()
  await expect(page.locator('h2')).toHaveText(vi.login.title)
})

test('activation translates visible validation and busy state without losing the token', async ({
  page,
}) => {
  let release
  const response = new Promise((resolve) => {
    release = resolve
  })
  let body
  await page.route('**/api/v1/auth/activate', async (route) => {
    body = route.request().postDataJSON()
    await response
    await route.fulfill({ json: { message: 'Account activated successfully' } })
  })
  const token = 'a'.repeat(43)
  await page.goto(`/activate?token=${token}`)
  await page.getByLabel(vi.activation.newPassword, { exact: true }).fill('Password123!')
  await page.getByLabel(vi.activation.confirmation, { exact: true }).fill('Different123!')
  await page.getByRole('button', { name: vi.activation.actions.submit }).click()
  await expect(page.getByRole('alert')).toHaveText(vi.activation.passwordMismatch)
  await page.getByRole('button', { name: vi.workspaceLayout.switchToEnglish }).click()
  await expect(page.getByRole('alert')).toHaveText(en.activation.passwordMismatch)
  await page.getByLabel(en.activation.confirmation, { exact: true }).fill('Password123!')
  await page.getByRole('button', { name: en.activation.actions.submit }).click()
  await expect(page.getByRole('button', { name: en.activation.actions.submitting })).toBeDisabled()
  await page.getByRole('button', { name: en.workspaceLayout.switchToVietnamese }).click()
  await expect(page.getByRole('button', { name: vi.activation.actions.submitting })).toBeDisabled()
  release()
  await expect(page.getByRole('heading', { name: vi.activation.successTitle })).toBeVisible()
  expect(body).toEqual({ token, password: 'Password123!' })
})

test('workspace, help filters, calendar labels and titles react in place', async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-01-15T05:00:00Z'))
  await mockSession(page)
  await page.goto('/help/features')
  await page.getByLabel(vi.featureReference.groupLabel, { exact: true }).selectOption('people')
  const count = await page.locator('.help-feature-list article').count()
  await switchWorkspace(page, vi, en)
  await expect(page.locator('h1')).toHaveText(en.featureReference.title)
  await expect(page.getByLabel(en.featureReference.groupLabel, { exact: true })).toHaveValue(
    'people',
  )
  await expect(page.locator('.help-feature-list article')).toHaveCount(count)
  await expect(page).toHaveTitle(`${en.routeTitles.featureReference} | ${en.common.appName}`)
  await page.goto('/calendar')
  await expect(page.locator('.calendar-weekdays')).toContainText('Mon')
  await expect(page.locator('option').filter({ hasText: en.eventTypes.MEETING })).toHaveCount(1)
  await expect(page.getByText('Thông báo từ công ty').first()).toBeVisible()
  await switchWorkspace(page, en, vi)
  await expect(page.locator('option').filter({ hasText: vi.eventTypes.MEETING })).toHaveCount(1)
  await expect(page).toHaveTitle(`${vi.routeTitles.calendar} | ${vi.common.appName}`)
})

test('unsupported saved locale falls back to Vietnamese', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('locale', 'unknown'))
  await page.goto('/login')
  await expect(page.locator('html')).toHaveAttribute('lang', 'vi')
  await expect(page.locator('h2')).toHaveText(vi.login.title)
})
