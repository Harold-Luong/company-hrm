import { expect, test } from '@playwright/test'
const draft = {
  id: 1,
  title: 'Ngày nghỉ công ty',
  description: 'Thông báo',
  type: 'HOLIDAY',
  holidayKind: 'COMPANY_DAY_OFF',
  allDay: true,
  startDate: '2026-02-10',
  endDate: '2026-02-11',
  startAt: null,
  endAt: null,
  timezone: 'Asia/Ho_Chi_Minh',
  location: 'Văn phòng',
  audienceType: 'ALL',
  status: 'DRAFT',
  version: 0,
}
async function setup(page, role = 'HR', options = {}) {
  const state = {
    events: options.events || [{ ...draft }],
    writes: [],
    reads: [],
    conflict: false,
    failCreate: false,
  }
  await page.clock.setFixedTime(new Date('2026-01-15T05:00:00Z'))
  await page.addInitScript(() => {
    if (!localStorage.getItem('company-hrm.session'))
      localStorage.setItem(
        'company-hrm.session',
        JSON.stringify({ id: 'test', refreshToken: 'refresh' }),
      )
  })
  await page.route('**/api/**', async (route) => {
    const req = route.request(),
      url = new URL(req.url()),
      path = url.pathname,
      method = req.method()
    if (path === '/api/v1/leave/requests/pending-count')
      return route.fulfill({ json: { count: 0 } })
    if (path.endsWith('/refresh'))
      return route.fulfill({ json: { accessToken: 'access', refreshToken: 'refresh' } })
    if (path.endsWith('/me'))
      return route.fulfill({
        json: { id: 7, email: 'member@company.com', roles: [role], active: true },
      })
    if (path === '/api/v1/calendar')
      return route.fulfill({
        json: {
          year: 2026,
          availableYears: [2026],
          events: state.events.filter((e) => e.status !== 'DRAFT'),
        },
      })
    expect(path).toMatch(/^\/api\/v1\/calendar-events/)
    expect(req.headers().authorization).toBe('Bearer access')
    if (method === 'GET') {
      state.reads.push(url.search)
      if (path === '/api/v1/calendar-events') {
        const pageIndex = Number(url.searchParams.get('page')),
          size = Number(url.searchParams.get('size'))
        const events = state.events.filter(
          (e) =>
            (!url.searchParams.get('status') || e.status === url.searchParams.get('status')) &&
            (!url.searchParams.get('type') || e.type === url.searchParams.get('type')),
        )
        return route.fulfill({
          json: {
            content: events.slice(pageIndex * size, (pageIndex + 1) * size),
            page: pageIndex,
            size,
            totalElements: events.length,
            totalPages: Math.ceil(events.length / size),
          },
        })
      }
      const event = state.events.find((e) => String(e.id) === path.split('/').at(-1))
      return route.fulfill({ status: event ? 200 : 404, json: event || { detail: 'Not found' } })
    }
    const body = req.postData() ? req.postDataJSON() : undefined
    state.writes.push({ method, path, body, match: req.headers()['if-match'] })
    if (method === 'POST') {
      if (state.failCreate) return route.fulfill({ status: 503, json: { detail: 'Unavailable' } })
      const created = { ...body, id: 99, status: 'DRAFT', version: 0 }
      state.events.push(created)
      return route.fulfill({ status: 201, json: created })
    }
    const event = state.events.find((e) => String(e.id) === path.split('/')[4])
    if (state.conflict) {
      state.conflict = false
      event.version++
      event.title = 'Cập nhật từ đồng nghiệp'
      return route.fulfill({ status: 412, json: { detail: 'Event has changed' } })
    }
    expect(req.headers()['if-match']).toBe(`"${event.version}"`)
    if (method === 'DELETE') {
      state.events = state.events.filter((e) => e !== event)
      return route.fulfill({ status: 204 })
    }
    if (method === 'PUT') Object.assign(event, body)
    if (path.endsWith('/publish')) event.status = 'PUBLISHED'
    if (path.endsWith('/cancel')) event.status = 'CANCELLED'
    event.version++
    return route.fulfill({ json: event })
  })
  return state
}
for (const role of ['EMPLOYEE', 'MANAGER']) {
  test(`${role} cannot access calendar management`, async ({ page }) => {
    const state = await setup(page, role)
    await page.goto('/calendar')
    await expect(page.getByRole('link', { name: 'Quản lý lịch', exact: true })).toHaveCount(0)
    for (const path of ['/calendar-events', '/calendar-events/new', '/calendar-events/1']) {
      await page.goto(path)
      await expect(page).toHaveURL('/forbidden')
    }
    expect(state.reads).toEqual([])
    expect(state.writes).toEqual([])
  })
}
for (const role of ['HR', 'ADMIN']) {
  test(`${role} creates edits publishes and cancels with current versions`, async ({ page }) => {
    const state = await setup(page, role)
    await page.goto('/calendar-events')
    await page.getByRole('link', { name: 'Tạo sự kiện', exact: true }).click()
    await page.getByLabel('Tiêu đề *', { exact: true }).fill('Ngày nghỉ mới')
    await page.getByLabel('Ngày bắt đầu *', { exact: true }).fill('2026-03-01')
    await page.getByLabel('Ngày kết thúc *', { exact: true }).fill('2026-03-02')
    await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click()
    await expect(page).toHaveURL('/calendar-events/99')
    expect(state.writes[0].body).toMatchObject({
      title: 'Ngày nghỉ mới',
      type: 'HOLIDAY',
      allDay: true,
      startDate: '2026-03-01',
      endDate: '2026-03-02',
      startAt: null,
      endAt: null,
      audienceType: 'ALL',
    })
    expect(state.writes[0].body).not.toHaveProperty('status')
    expect(state.writes[0].body).not.toHaveProperty('version')
    await page.getByLabel('Tiêu đề *', { exact: true }).fill('Ngày nghỉ cập nhật')
    await expect(page.getByRole('button', { name: 'Công bố', exact: true })).toBeDisabled()
    await page.getByRole('button', { name: 'Lưu thay đổi' }).click()
    await expect(page.getByRole('status')).toContainText('Đã lưu')
    await page.getByRole('button', { name: 'Công bố', exact: true }).click()
    await page
      .getByRole('dialog')
      .getByRole('button', { name: 'Công bố sự kiện', exact: true })
      .click()
    await expect(page.locator('.page-heading .event-status')).toHaveText('Đã công bố')
    await expect(page.getByLabel('Loại sự kiện *', { exact: true })).toBeDisabled()
    await page.getByLabel('Mô tả', { exact: true }).fill('Nội dung mới')
    await page.getByLabel('Lý do thay đổi *', { exact: true }).fill('Bổ sung nội dung')
    await page.getByRole('button', { name: 'Lưu thay đổi' }).click()
    await expect(page.getByRole('button', { name: 'Hủy sự kiện', exact: true })).toBeEnabled()
    await page.getByRole('button', { name: 'Hủy sự kiện', exact: true }).click()
    await page.getByLabel('Lý do hủy *', { exact: true }).fill('   ')
    await page.getByRole('dialog').getByRole('button', { name: 'Hủy sự kiện', exact: true }).click()
    await expect(page.getByRole('dialog').getByRole('alert')).toContainText(
      'Vui lòng nhập lý do hủy',
    )
    expect(state.writes).toHaveLength(4)
    await page.getByLabel('Lý do hủy *', { exact: true }).fill('Đổi kế hoạch')
    await page.getByRole('dialog').getByRole('button', { name: 'Hủy sự kiện', exact: true }).click()
    await expect(page.locator('.page-heading .event-status')).toHaveText('Đã hủy')
    await expect(page.getByLabel('Tiêu đề *', { exact: true })).toBeDisabled()
    expect(state.writes.map((r) => r.match)).toEqual([undefined, '"0"', '"1"', '"2"', '"3"'])
    expect(state.writes.at(-1).body).toEqual({ reason: 'Đổi kế hoạch' })
  })
}
test('deletes a draft after confirmation and accepts 204', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/calendar-events/1')
  await page.getByRole('button', { name: 'Xóa bản nháp', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Quay lại' }).click()
  expect(state.writes).toEqual([])
  await page.getByRole('button', { name: 'Xóa bản nháp', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Xóa bản nháp', exact: true }).click()
  await expect(page).toHaveURL('/calendar-events')
  await expect(page.getByRole('status')).toContainText('Chưa có sự kiện')
  expect(state.events).toEqual([])
  expect(state.writes).toHaveLength(1)
})
test('412 preserves unsaved input and requires reload without automatic retry', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/calendar-events/1')
  await page.getByLabel('Tiêu đề *', { exact: true }).fill('Nội dung đang sửa')
  state.conflict = true
  await page.getByRole('button', { name: 'Lưu thay đổi' }).click()
  await expect(page.getByRole('alert')).toContainText('người khác thay đổi')
  await expect(page.getByLabel('Tiêu đề *', { exact: true })).toHaveValue('Nội dung đang sửa')
  await expect(page.getByRole('button', { name: 'Lưu thay đổi' })).toBeDisabled()
  expect(state.writes).toHaveLength(1)
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: 'Tải lại sự kiện', exact: true }).click()
  await expect(page.getByLabel('Tiêu đề *', { exact: true })).toHaveValue('Cập nhật từ đồng nghiệp')
  await page.getByLabel('Tiêu đề *', { exact: true }).fill('Đã đối chiếu')
  await page.getByRole('button', { name: 'Lưu thay đổi' }).click()
  await expect(page.getByRole('status')).toContainText('Đã lưu')
  expect(state.writes.at(-1).match).toBe('"1"')
})
test('list filters and pagination send selected parameters', async ({ page }) => {
  const state = await setup(page, 'HR', {
    events: Array.from({ length: 12 }, (_, i) => ({
      ...draft,
      id: i + 1,
      title: `Sự kiện ${i + 1}`,
    })),
  })
  await page.goto('/calendar-events')
  await page.getByLabel('Từ ngày', { exact: true }).fill('2026-02-01')
  await page.getByLabel('Đến ngày', { exact: true }).fill('2026-02-28')
  await page.getByLabel('Trạng thái', { exact: true }).selectOption('DRAFT')
  await page.getByLabel('Loại sự kiện', { exact: true }).selectOption('HOLIDAY')
  await page.getByLabel('Số dòng', { exact: true }).selectOption('10')
  await page.getByRole('button', { name: 'Áp dụng' }).click()
  await expect(page.locator('tbody tr')).toHaveCount(10)
  await page.getByRole('button', { name: 'Trang sau' }).click()
  await expect(page.locator('tbody tr')).toHaveCount(2)
  expect(Object.fromEntries(new URLSearchParams(state.reads.at(-1)))).toEqual({
    from: '2026-02-01',
    to: '2026-02-28',
    status: 'DRAFT',
    type: 'HOLIDAY',
    size: '10',
    page: '1',
  })
})
test('failed create is not repeated and points to the list', async ({ page }) => {
  const state = await setup(page)
  state.failCreate = true
  await page.goto('/calendar-events/new')
  await page.getByLabel('Tiêu đề *', { exact: true }).fill('Sự kiện mới')
  await page.getByRole('button', { name: 'Lưu bản nháp' }).click()
  await expect(page.getByRole('alert')).toContainText('Chưa xác định được kết quả')
  await expect(page.getByRole('button', { name: 'Lưu bản nháp' })).toBeDisabled()
  await expect(page.getByRole('link', { name: 'Kiểm tra danh sách sự kiện' })).toBeVisible()
  expect(state.writes).toHaveLength(1)
})
test('started events are read-only and past drafts cannot be published', async ({ page }) => {
  const state = await setup(page, 'HR', {
    events: [{ ...draft, status: 'PUBLISHED', startDate: '2026-01-15', endDate: '2026-01-15' }],
  })
  await page.goto('/calendar-events/1')
  await expect(page.getByLabel('Tiêu đề *', { exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Hủy sự kiện', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Xóa bản nháp', exact: true })).toHaveCount(0)
  state.events[0] = { ...draft, startDate: '2026-01-01', status: 'DRAFT' }
  await page.getByRole('button', { name: 'Tải lại dữ liệu' }).click()
  await expect(page.getByRole('button', { name: 'Công bố', exact: true })).toBeDisabled()
  await expect(page.getByLabel('Tiêu đề *', { exact: true })).toBeEnabled()
})
test.describe('time-zone independent editing', () => {
  test.use({ timezoneId: 'America/Los_Angeles' })
  test('creates a timed event in Vietnam time on mobile', async ({ page }) => {
    const state = await setup(page)
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/calendar-events/new')
    await page.getByLabel('Tiêu đề *', { exact: true }).fill('Họp qua ngày')
    await page.getByLabel('Loại sự kiện *', { exact: true }).selectOption('MEETING')
    await page.getByLabel('Cả ngày', { exact: true }).uncheck()
    await page.getByLabel('Bắt đầu lúc *', { exact: true }).fill('2026-02-01T23:00')
    await page.getByLabel('Kết thúc lúc *', { exact: true }).fill('2026-02-02T01:00')
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.getByRole('button', { name: 'Lưu bản nháp' }).click()
    await expect(page).toHaveURL('/calendar-events/99')
    expect(state.writes[0].body).toMatchObject({
      startAt: '2026-02-01T16:00:00.000Z',
      endAt: '2026-02-01T18:00:00.000Z',
      startDate: null,
      endDate: null,
      holidayKind: null,
      allDay: false,
    })
    await expect(page.getByLabel('Bắt đầu lúc *', { exact: true })).toHaveValue('2026-02-01T23:00')
    await page.getByRole('button', { name: 'Công bố', exact: true }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: 'test-results/calendar-management-mobile.png', fullPage: true })
  })
})

test('event table renders drafts first then start dates nearest today', async ({ page }) => {
  const entries = [
    {
      ...draft,
      id: 1,
      title: 'Đã công bố hôm nay',
      status: 'PUBLISHED',
      startDate: '2026-01-15',
      endDate: '2026-01-15',
    },
    { ...draft, id: 2, title: 'Nháp xa', startDate: '2026-02-15', endDate: '2026-02-15' },
    { ...draft, id: 3, title: 'Nháp hôm qua', startDate: '2026-01-14', endDate: '2026-01-14' },
    { ...draft, id: 4, title: 'Nháp hôm nay', startDate: '2026-01-15', endDate: '2026-01-15' },
    {
      ...draft,
      id: 5,
      title: 'Đã công bố xa',
      status: 'PUBLISHED',
      startDate: '2026-03-15',
      endDate: '2026-03-15',
    },
  ]
  await setup(page, 'HR', { events: entries })
  await page.goto('/calendar-events')
  await expect(page.locator('tbody .record-link')).toHaveText([
    'Nháp hôm nay',
    'Nháp hôm qua',
    'Nháp xa',
    'Đã công bố hôm nay',
    'Đã công bố xa',
  ])
  await page.getByLabel('Trạng thái', { exact: true }).selectOption('PUBLISHED')
  await page.getByRole('button', { name: 'Áp dụng' }).click()
  await expect(page.locator('tbody .record-link')).toHaveText([
    'Đã công bố hôm nay',
    'Đã công bố xa',
  ])
})
