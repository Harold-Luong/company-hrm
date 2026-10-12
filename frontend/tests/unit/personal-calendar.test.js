import { describe, expect, it, vi, afterEach } from 'vitest'
import { personalLeaveEvents } from '../../src/calendar/leave.js'
import { dayEntries } from '../../src/calendar/utils.js'

describe('personal calendar mapping', () => {
  it('keeps inclusive dates and distinguishes half days and decisions', () => {
    const events = personalLeaveEvents([
      {
        id: 'a',
        leaveType: 'UNPAID',
        startDate: '2026-12-31',
        endDate: '2027-01-02',
        period: 'FULL_DAY',
        status: 'APPROVED',
      },
      {
        id: 'b',
        leaveType: 'ANNUAL',
        startDate: '2027-01-03',
        endDate: '2027-01-03',
        period: 'MORNING',
        status: 'PENDING',
      },
      { id: 'c', status: 'REJECTED' },
      { id: 'd', status: 'CANCELLED' },
    ])
    expect(events).toHaveLength(2)
    expect(dayEntries(events, '2027-01-02')[0].event.requestId).toBe('a')
    expect(dayEntries(events, '2027-01-03')[0].event.title).toContain('Buổi sáng · Chờ duyệt')
    expect(dayEntries(events, '2027-01-04')).toEqual([])
    expect(events[0].id).toBe('leave:a')
  })
})

const request = vi.hoisted(() => vi.fn())
vi.mock('../../src/auth/session.js', () => ({ auth: { request } }))
afterEach(() => {
  vi.clearAllMocks()
})
it('shares one SSE connection across sidebar and calendar and closes it after both unsubscribe', async () => {
  const { subscribeNotifications } = await import('../../src/notifications/stream.js')
  let controller
  request.mockImplementation((_url, options) =>
    Promise.resolve(
      new ReadableStream({
        start(value) {
          controller = value
          options.signal.addEventListener('abort', () => value.error(new Error('aborted')), {
            once: true,
          })
        },
      }),
    ),
  )
  const sidebar = vi.fn(),
    calendar = vi.fn()
  const stopSidebar = subscribeNotifications(sidebar)
  const stopCalendar = subscribeNotifications(calendar)
  try {
    expect(request).toHaveBeenCalledTimes(1)
    controller.enqueue(new TextEncoder().encode('event: notifications\ndata: {"revision":1}\n\n'))
    await vi.waitFor(() => {
      expect(sidebar).toHaveBeenCalledTimes(1)
      expect(calendar).toHaveBeenCalledTimes(1)
    })
    stopCalendar()
    expect(request.mock.calls[0][1].signal.aborted).toBe(false)
    controller.enqueue(new TextEncoder().encode('event: notifications\ndata: {"revision":2}\n\n'))
    await vi.waitFor(() => expect(sidebar).toHaveBeenCalledTimes(2))
    expect(calendar).toHaveBeenCalledTimes(1)
  } finally {
    stopCalendar()
    stopSidebar()
  }
  expect(request.mock.calls[0][1].signal.aborted).toBe(true)
})
