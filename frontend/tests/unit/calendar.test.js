import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
let getCalendar
let getCalendarHealth
import {
  dayEntries,
  eventDateRange,
  monthCells,
  displayEventRange,
} from '../../src/calendar/utils.js'

describe('calendar date handling', () => {
  it('includes both boundaries of all-day holidays without extending them', () => {
    const event = {
      id: 1,
      title: 'Nghỉ bù',
      type: 'HOLIDAY',
      holidayKind: 'SUBSTITUTE_DAY_OFF',
      allDay: true,
      startDate: '2026-04-27',
      endDate: '2026-04-28',
    }
    expect(dayEntries([event], '2026-04-26')).toEqual([])
    expect(dayEntries([event], '2026-04-27')[0].type).toBe('SUBSTITUTE_DAY_OFF')
    expect(dayEntries([event], '2026-04-28')).toHaveLength(1)
    expect(dayEntries([event], '2026-04-29')).toEqual([])
    expect(displayEventRange(event)).toBe('27/04/2026 – 28/04/2026')
  })
  it('uses Vietnam dates for timed events and excludes the ending midnight', () => {
    const event = {
      id: 2,
      title: 'Họp',
      type: 'MEETING',
      allDay: false,
      startDate: null,
      endDate: null,
      startAt: '2025-12-31T16:00:00Z',
      endAt: '2026-01-01T17:00:00Z',
    }
    expect(eventDateRange(event)).toEqual({ startDay: '2025-12-31', endDay: '2026-01-01' })
    expect(dayEntries([event], '2026-01-01')[0].type).toBe('MEETING')
    expect(dayEntries([event], '2026-01-02')).toEqual([])
    expect(event.startDate).toBeNull()
    expect(displayEventRange(event)).toContain('23:00')
    expect(displayEventRange(event)).toContain('00:00')
  })
  it('includes a timed end day when the event extends past midnight', () => {
    expect(
      eventDateRange({
        allDay: false,
        startAt: '2026-01-01T17:00:00Z',
        endAt: '2026-01-02T17:00:01Z',
      }),
    ).toEqual({ startDay: '2026-01-02', endDay: '2026-01-03' })
  })
  it('lays out Monday-first weeks and handles leap years independently of timezone', () => {
    const january = monthCells(2026, 1)
    expect(january).toHaveLength(42)
    expect(january.slice(0, 3)).toEqual([null, null, null])
    expect(january[3].date).toBe('2026-01-01')
    expect(january[5].weekend).toBe(true)
    expect(monthCells(2027, 2).filter(Boolean)).toHaveLength(28)
    expect(monthCells(2028, 2).filter(Boolean)).toHaveLength(29)
  })
})

describe('authenticated calendar API', () => {
  beforeEach(async () => {
    vi.resetModules()
    localStorage.clear()
    localStorage.setItem(
      'company-hrm.session',
      JSON.stringify({ id: 'test', refreshToken: 'refresh' }),
    )
    Object.defineProperty(navigator, 'locks', {
      configurable: true,
      value: { request: (_name, callback) => Promise.resolve().then(callback) },
    })
    ;({ getCalendar, getCalendarHealth } = await import('../../src/calendar/api.js'))
  })

  function mockFetch(response) {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ accessToken: 'access', refreshToken: 'rotated' })),
      )
      .mockResolvedValueOnce(response)
    vi.stubGlobal('fetch', fetchMock)
    return fetchMock
  }
  afterEach(() => vi.unstubAllGlobals())
  it('reads the API response without converting to the legacy mock schema', async () => {
    const response = {
      year: 2026,
      availableYears: [2026, 2027],
      events: [
        {
          id: 1,
          title: 'Đào tạo',
          type: 'TRAINING',
          description: 'Nội dung',
          location: 'Phòng A',
          status: 'CANCELLED',
          allDay: true,
          startDate: '2026-01-02',
          endDate: '2026-01-02',
          timezone: 'Asia/Ho_Chi_Minh',
          audienceType: 'ALL',
        },
      ],
    }
    const fetchMock = mockFetch(new Response(JSON.stringify(response)))
    expect(await getCalendar(2026)).toEqual(response)
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/calendar?year=2026',
      expect.objectContaining({
        headers: { Accept: 'application/json', Authorization: 'Bearer access' },
        cache: 'no-store',
      }),
    )
  })
  it('checks Calendar health through the gateway with the session access token', async () => {
    const fetchMock = mockFetch(new Response(JSON.stringify({ status: 'UP' })))
    expect(await getCalendarHealth()).toEqual({ status: 'UP' })
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/v1/calendar/health-check',
      expect.objectContaining({
        headers: { Accept: 'application/json', Authorization: 'Bearer access' },
      }),
    )
  })
  it('supports an empty year without adding sample events', async () => {
    const response = { year: 2026, availableYears: [2026], events: [] }
    mockFetch(new Response(JSON.stringify(response)))
    expect(await getCalendar(2026)).toEqual(response)
  })
  it('propagates HTTP failures instead of falling back to sample data', async () => {
    mockFetch(new Response(JSON.stringify({ detail: 'Calendar unavailable' }), { status: 503 }))
    await expect(getCalendar(2026)).rejects.toMatchObject({ status: 503 })
  })
})
