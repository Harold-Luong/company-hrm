import { expect, test } from '@playwright/test'

const token = 'a'.repeat(43)
const password = 'My activation password 2026!'
const submitName = 'Đặt mật khẩu và kích hoạt'

async function fillPasswords(page, value = password, confirmation = value) {
  await page.getByLabel('Mật khẩu mới', { exact: true }).fill(value)
  await page.getByLabel('Nhập lại mật khẩu', { exact: true }).fill(confirmation)
}

test('public activation clears the URL, submits once and links to login after success', async ({
  page,
  context,
}) => {
  const requests = []
  await context.addCookies([
    { name: 'unrelated-session', value: 'cookie', url: 'http://127.0.0.1:5174' },
  ])
  await page.route('**/api/**', async (route) => {
    requests.push(route.request())
    await route.fulfill({ json: { message: 'Account activated successfully' } })
  })
  await page.goto(`/activate?token=${token}`)
  await expect(page).toHaveURL('/activate')
  await expect(page.getByRole('heading', { name: 'Kích hoạt tài khoản' })).toBeVisible()
  expect(requests).toHaveLength(0)
  await fillPasswords(page)
  await page.getByRole('button', { name: submitName }).click()
  await expect(page.getByRole('heading', { name: 'Kích hoạt thành công' })).toBeVisible()
  expect(requests).toHaveLength(1)
  expect(new URL(requests[0].url()).pathname).toBe('/api/v1/auth/activate')
  expect(requests[0].method()).toBe('POST')
  expect(requests[0].postDataJSON()).toEqual({ token, password })
  const headers = await requests[0].allHeaders()
  expect(headers.authorization).toBeUndefined()
  expect(headers.cookie).toBeUndefined()
  expect(headers.referer).toBeUndefined()
  expect(await page.evaluate(() => JSON.stringify([localStorage, sessionStorage]))).not.toContain(
    token,
  )
  await expect(page.locator('input')).toHaveCount(0)
  await page.getByRole('link', { name: 'Đi đến đăng nhập' }).click()
  await expect(page).toHaveURL('/login')
  await expect(page.getByRole('heading', { name: 'Đăng nhập workspace' })).toBeVisible()
})

test('activation does not initialize a stale login session', async ({ page }) => {
  const requests = []
  await page.addInitScript(() => {
    localStorage.setItem(
      'company-hrm.session',
      JSON.stringify({ id: 'old', refreshToken: 'expired' }),
    )
  })
  await page.route('**/api/**', async (route) => {
    requests.push(route.request().url())
    await route.fulfill({ status: 503, json: { message: 'Unavailable' } })
  })
  await page.goto(`/activate?token=${token}`)
  await expect(page.getByRole('button', { name: submitName })).toBeEnabled()
  expect(requests).toHaveLength(0)
})

for (const query of ['', '?token=invalid', `?token=${token}&token=${token}`]) {
  test(`missing or malformed token cannot be submitted: ${query || 'missing'}`, async ({
    page,
  }) => {
    await page.goto(`/activate${query}`)
    await expect(page.getByRole('alert')).toContainText('Liên kết kích hoạt không hợp lệ')
    await expect(page.getByRole('button', { name: submitName })).toHaveCount(0)
    await expect(page).toHaveURL('/activate')
  })
}

test('validates confirmation and UTF-8 length without submitting', async ({ page }) => {
  const requests = []
  await page.route('**/api/**', async (route) => {
    requests.push(route.request())
    await route.abort()
  })
  await page.goto(`/activate?token=${token}`)
  await fillPasswords(page, password, 'A different password')
  await page.getByRole('button', { name: submitName }).click()
  await expect(page.getByRole('alert')).toContainText('Mật khẩu nhập lại chưa khớp')
  await fillPasswords(page, 'đ'.repeat(40))
  await page.getByRole('button', { name: submitName }).click()
  await expect(page.getByRole('alert')).toContainText('72 byte UTF-8')
  expect(requests).toHaveLength(0)
})

test('expired or used invitation shows recovery guidance', async ({ page }) => {
  await page.route('**/api/v1/auth/activate', (route) =>
    route.fulfill({ status: 400, json: { message: 'Invalid or expired activation token' } }),
  )
  await page.goto(`/activate?token=${token}`)
  await fillPasswords(page)
  await page.getByRole('button', { name: submitName }).click()
  await expect(page.getByRole('alert')).toContainText(
    'Liên kết đã hết hạn, đã sử dụng hoặc bị thu hồi',
  )
  await expect(page.getByRole('heading', { name: 'Kích hoạt thành công' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Đi đến đăng nhập' })).toBeVisible()
})

test('network failure allows retry using the in-memory token', async ({ page }) => {
  let attempts = 0
  await page.route('**/api/v1/auth/activate', async (route) => {
    attempts++
    expect(route.request().postDataJSON()).toEqual({ token, password })
    if (attempts === 1) await route.abort()
    else await route.fulfill({ json: { message: 'Account activated successfully' } })
  })
  await page.goto(`/activate?token=${token}`)
  await fillPasswords(page)
  await page.getByRole('button', { name: submitName }).click()
  await expect(page.getByRole('alert')).toContainText('Chưa nhận được kết quả')
  await page.getByRole('button', { name: submitName }).click()
  await expect(page.getByRole('heading', { name: 'Kích hoạt thành công' })).toBeVisible()
  expect(attempts).toBe(2)
})

test('reload requires reopening the invitation instead of persisting the token', async ({
  page,
}) => {
  await page.goto(`/activate?token=${token}`)
  await expect(page).toHaveURL('/activate')
  await page.reload()
  await expect(page.getByRole('alert')).toContainText('Liên kết kích hoạt không hợp lệ')
})
